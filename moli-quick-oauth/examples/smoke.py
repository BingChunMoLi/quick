"""Build the three example jars first; run a local, process-isolated OAuth/OIDC smoke test."""
from pathlib import Path
from urllib.request import Request, build_opener, HTTPCookieProcessor, HTTPRedirectHandler, ProxyHandler
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlsplit, parse_qs
from html.parser import HTMLParser
import base64
import hashlib
import http.cookiejar
import json
import os
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / 'target'
OUTPUT.mkdir(exist_ok=True)
SECRETS = {name: f'local-{name}-secret' for name in ('web', 'machine', 'reference')}
USER_PASSWORD = 'local-user-password'  # Only the example's explicit local profile uses this value.
AUTH, RESOURCE, CLIENT, OPAQUE = [f'http://127.0.0.1:{port}' for port in (9010, 9011, 9012, 9013)]
processes, files, checks = [], [], []


def check(condition, description):
    if not condition:
        raise AssertionError(description)
    checks.append(description)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


class Inputs(HTMLParser):
    def __init__(self, document):
        super().__init__()
        self.values = []
        self.feed(document)

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if tag == 'input' and attributes.get('name') and attributes.get('value') is not None:
            self.values.append((attributes['name'], attributes['value']))


def request(opener, url, fields=None, headers=None):
    data = None if fields is None else urlencode(fields).encode()
    request_headers = dict(headers or {})
    if data is not None:
        request_headers['Content-Type'] = 'application/x-www-form-urlencoded'
    try:
        response = opener.open(Request(url, data=data, headers=request_headers), timeout=12)
    except HTTPError as response:
        return response.code, response.read().decode(), response.headers, response.url
    with response:
        return response.status, response.read().decode(), response.headers, response.url


def launch(app, readiness, profiles='local'):
    artifact = ROOT / app / 'target' / f'quick-oauth-{app}-example-0.0.1-SNAPSHOT.jar'
    if not artifact.is_file():
        raise RuntimeError(f'Build the example first: {app}')
    log = (OUTPUT / f'{app}-{profiles}.log').open('w', encoding='utf-8')
    files.append(log)
    environment = {key: value for key, value in os.environ.items()
                   if not key.upper().startswith(('SPRING_', 'MOLI_', 'SERVER_', 'EXAMPLE_', 'OAUTH_'))
                   and key.upper() not in ('AUTH_ISSUER', 'RESOURCE_BASE')}
    environment.update(OAUTH_CLIENT_SECRET=SECRETS['web'], OAUTH_INTROSPECTION_SECRET=SECRETS['machine'],
                       AUTH_ISSUER=AUTH, RESOURCE_BASE=RESOURCE, OAUTH_REDIRECT_URI=CLIENT + '/login/oauth2/code/local')
    port = {'authorization-server': 9010, 'resource-server': 9013 if profiles == 'opaque' else 9011, 'client': 9012}[app]
    process = subprocess.Popen(['java', '-jar', str(artifact), f'--spring.profiles.active={profiles}',
                                '--server.address=127.0.0.1', f'--server.port={port}',
                                '--spring.main.banner-mode=off', '--logging.level.root=WARN'],
                               cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    processes.append(process)
    for attempt in range(120):
        if process.poll() is not None:
            raise RuntimeError(f'{app} exited; inspect {log.name}')
        try:
            status, *_ = request(build_opener(ProxyHandler({})), readiness)
            if status in (200, 401):
                return
        except (URLError, TimeoutError, OSError):
            pass
        time.sleep(0.5)
    raise RuntimeError(f'{app} did not become ready; inspect {log.name}')


def basic(client):
    return {'Authorization': 'Basic ' + base64.b64encode(f'{client}:{SECRETS[client]}'.encode()).decode()}


def bearer(token):
    return {'Authorization': 'Bearer ' + token}


try:
    for port in (9010, 9011, 9012, 9013):
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', port)) == 0:
                raise RuntimeError(f'Port {port} is occupied; the smoke test will not reuse or stop an existing service')
    launch('authorization-server', AUTH + '/.well-known/openid-configuration')
    launch('resource-server', RESOURCE + '/api/me')
    launch('resource-server', OPAQUE + '/api/me', 'opaque')
    launch('client', CLIENT + '/oauth2/authorization/local')

    cookies = http.cookiejar.CookieJar()
    browser = build_opener(ProxyHandler({}), HTTPCookieProcessor(cookies))
    status, page, _, url = request(browser, CLIENT + '/me')
    check(status == 200 and url.startswith(AUTH + '/login'), 'Client redirects to authorization-server login')
    csrf = dict(Inputs(page).values).get('_csrf')
    check(bool(csrf), 'Host form login keeps CSRF protection')
    status, page, _, url = request(browser, AUTH + '/login', [('username', 'alice'), ('password', USER_PASSWORD), ('_csrf', csrf)])
    if '/oauth2/authorize' in url:
        fields = [(name, value) for name, value in Inputs(page).values if name in ('client_id', 'state', 'scope', '_csrf')]
        status, page, _, url = request(browser, AUTH + '/oauth2/authorize', fields)
    check(status == 200 and url == CLIENT + '/me', 'OIDC callback establishes the host business Session')
    check('alice' in json.loads(page)['account'], 'Host business account uses stable OAuth identity')
    status, body, _, _ = request(browser, CLIENT + '/api')
    check(status == 200 and json.loads(body)['subject'] == 'alice', 'Client Starter calls the protected resource API')

    plain = build_opener(ProxyHandler({}))
    status, body, headers, _ = request(plain, RESOURCE + '/api/me')
    check(status == 401 and headers.get('WWW-Authenticate', '').startswith('Bearer'), 'Missing Bearer token receives standard 401')
    status, body, _, _ = request(plain, AUTH + '/oauth2/token', [('grant_type', 'client_credentials'), ('scope', 'api.read')], basic('machine'))
    check(status == 200, 'client_credentials issues a JWT access token')
    machine = json.loads(body)['access_token']
    check(request(plain, RESOURCE + '/api/me', headers=bearer(machine))[0] == 200, 'JWT signature issuer audience and scope authorize the API')
    check(request(plain, RESOURCE + '/api/admin', headers=bearer(machine))[0] == 403, 'Insufficient scope receives 403')

    verifier = base64.urlsafe_b64encode(os.urandom(48)).decode().rstrip('=')
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    no_redirect = build_opener(ProxyHandler({}), HTTPCookieProcessor(cookies), NoRedirect())
    authorize = AUTH + '/oauth2/authorize?' + urlencode({'client_id': 'web', 'response_type': 'code',
        'redirect_uri': CLIENT + '/login/oauth2/code/local', 'scope': 'openid profile api.read',
        'state': 'process-smoke-' + os.urandom(16).hex(), 'nonce': os.urandom(16).hex(),
        'code_challenge': challenge, 'code_challenge_method': 'S256'})
    status, page, headers, _ = request(no_redirect, authorize)
    if status == 200:
        fields = [(name, value) for name, value in Inputs(page).values if name in ('client_id', 'state', 'scope', '_csrf')]
        status, page, headers, _ = request(no_redirect, AUTH + '/oauth2/authorize', fields)
    check(status == 302, 'Authenticated authorization request returns a single-use code')
    code = parse_qs(urlsplit(headers['Location']).query)['code'][0]
    status, body, _, _ = request(plain, AUTH + '/oauth2/token', [('grant_type', 'authorization_code'), ('code', code),
        ('redirect_uri', CLIENT + '/login/oauth2/code/local'), ('code_verifier', verifier)], basic('web'))
    check(status == 200, 'S256 PKCE authorization code exchange succeeds')
    tokens = json.loads(body)
    check(bool(tokens.get('id_token') and tokens.get('refresh_token')), 'OIDC and confidential refresh tokens are issued')
    status, body, _, _ = request(plain, AUTH + '/oauth2/token', [('grant_type', 'refresh_token'), ('refresh_token', tokens['refresh_token'])], basic('web'))
    check(status == 200, 'Refresh grant succeeds across standalone processes')
    refreshed = json.loads(body)
    check(refreshed['refresh_token'] != tokens['refresh_token'], 'Refresh token rotates')
    check(request(plain, AUTH + '/oauth2/token', [('grant_type', 'refresh_token'), ('refresh_token', tokens['refresh_token'])], basic('web'))[0] == 400,
          'Reusing the old refresh token is rejected')

    status, body, _, _ = request(plain, AUTH + '/oauth2/token', [('grant_type', 'client_credentials'), ('scope', 'api.read')], basic('reference'))
    check(status == 200, 'Reference access token is issued')
    opaque = json.loads(body)['access_token']
    check(request(plain, OPAQUE + '/api/me', headers=bearer(opaque))[0] == 200, 'Opaque resource validates introspection')
    check(request(plain, AUTH + '/oauth2/revoke', [('token', opaque)], basic('reference'))[0] == 200, 'Reference token is revoked')
    check(request(plain, OPAQUE + '/api/me', headers=bearer(opaque))[0] == 401, 'Opaque revocation takes effect on the next request')
    check(request(plain, AUTH + '/oauth2/revoke', [('token', machine)], basic('machine'))[0] == 200, 'JWT authorization is revoked at the issuer')
    check(request(plain, RESOURCE + '/api/me', headers=bearer(machine))[0] == 200, 'Local JWT validation retains its documented expiry semantics')
    check(request(plain, OPAQUE + '/api/me', headers=bearer(machine))[0] == 401, 'Introspection rejects the revoked JWT authorization')

    result = {'passed': True, 'checks': checks, 'count': len(checks)}
    (OUTPUT / 'smoke-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))
finally:
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
    for log in files:
        log.close()

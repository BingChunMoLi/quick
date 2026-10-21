import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import http from 'node:http'
import { generateKeyPairSync, sign, verify } from 'node:crypto'
import { withRequestSignature } from '../src/util/signed-request.mjs'

// Load Axios only after supplying XMLHttpRequest: adapter availability is captured at import.
// This shim forwards the real XHR adapter's final headers/data unchanged to local HTTP.
const originalXHR = globalThis.XMLHttpRequest
globalThis.XMLHttpRequest = class {
  constructor() { this.onloadend = null; this.headers = {} }
  open(method, url) { this.method = method; this.url = url }
  setRequestHeader(name, value) { this.headers[name] = value }
  getAllResponseHeaders() {
    return Object.entries(this.responseHeaders).map(([name, value]) => `${name}: ${value}`).join('\r\n')
  }
  abort() { this.activeRequest?.destroy(); this.onabort?.() }
  send(body) {
    this.activeRequest = http.request(this.url, { method: this.method, headers: this.headers }, response => {
      const chunks = []
      response.on('data', chunk => chunks.push(chunk))
      response.on('end', () => {
        this.status = response.statusCode
        this.statusText = response.statusMessage
        this.responseHeaders = response.headers
        this.responseText = Buffer.concat(chunks).toString('utf8')
        this.onloadend()
      })
    })
    this.activeRequest.on('error', () => this.onerror?.())
    this.activeRequest.end(body ?? undefined)
  }
}
const { default: axios } = await import('axios')
const { default: xhr } = await import('axios/lib/adapters/xhr.js')
assert.equal(typeof xhr, 'function', 'the real XHR adapter must be loaded, never silently fall back to Node HTTP')

const keys = generateKeyPairSync('rsa', { modulusLength: 2048 })
const observed = []
let server
let base

before(async () => {
  server = http.createServer(async (request, response) => {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const body = Buffer.concat(chunks).toString('utf8')
    const json = (request.headers['content-type'] ?? '').split(';')[0] === 'application/json'
    const values = new Map([['authorization', request.headers.authorization ?? ''], ['t', request.headers.t], ['nonce', request.headers.nonce]])
    if (json) values.set('body', body.replace(/[\r\n]/g, ''))
    const encode = text => text.replaceAll('%', '%25').replaceAll('&', '%26').replaceAll('=', '%3D')
      .replaceAll('\r', '%0D').replaceAll('\n', '%0A')
    const input = `${request.method.toLowerCase()} /echo\n` + [...values.keys()].sort()
      .map(name => `${encode(name)}=${encode(values.get(name))}`).join('&')
    const valid = verify('RSA-SHA256', Buffer.from(input), keys.publicKey, Buffer.from(request.headers.sign, 'base64'))
    observed.push({ method: request.method, contentType: request.headers['content-type'], body, input, valid })
    response.writeHead(valid ? 200 : 401, { 'Content-Type': 'application/json', 'X-Signature-Valid': String(valid) })
    response.end(JSON.stringify({ valid }))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  base = `http://127.0.0.1:${server.address().port}`
})

after(async () => {
  try { await new Promise(resolve => server.close(resolve)) }
  finally {
    if (originalXHR === undefined) delete globalThis.XMLHttpRequest
    else globalThis.XMLHttpRequest = originalXHR
  }
})

function client(adapter = xhr) {
  const service = axios.create({ baseURL: base, adapter, timeout: 3000 })
  service.interceptors.request.use(config => withRequestSignature(config, request => service.getUri(request),
    input => sign('RSA-SHA256', Buffer.from(input), keys.privateKey).toString('base64')))
  return service
}

for (const method of ['get', 'head', 'post', 'put', 'patch', 'delete']) {
  test(`XHR preserves the signed Content-Type for an undefined JSON body: ${method}`, async () => {
    const response = await client().request({ method, url: '/echo', headers: { 'Content-Type': 'application/json' } })
    assert.equal(response.status, 200)
    assert.equal(response.headers['x-signature-valid'], 'true')
    assert.equal(observed.at(-1).contentType, 'application/json')
    assert.equal(observed.at(-1).body, '')
    assert.match(observed.at(-1).input, /body=&/)
  })
}

test('a global JSON default header works for a bodyless browser request', async () => {
  const service = client()
  service.defaults.headers.common['Content-Type'] = 'application/json'
  assert.equal((await service.get('/echo')).status, 200)
  assert.equal(observed.at(-1).contentType, 'application/json')
})

for (const [name, data, body] of [['object', { amount: 1 }, '{"amount":1}'], ['false', false, 'false'],
  ['zero', 0, '0'], ['null', null, 'null']]) {
  test(`XHR keeps the existing serialized JSON representation: ${name}`, async () => {
    const response = await client().post('/echo', data, { headers: { 'Content-Type': 'application/json' } })
    assert.equal(response.status, 200)
    assert.equal(observed.at(-1).body, body)
    assert.equal(observed.at(-1).contentType, 'application/json')
  })
}

test('a bodyless request without JSON keeps omitting the body signature field', async () => {
  assert.equal((await client().get('/echo')).status, 200)
  assert.equal(observed.at(-1).contentType, undefined)
  assert(!observed.at(-1).input.includes('body='))
})

test('the JSON empty-body representation agrees between Node HTTP and XHR', async () => {
  const config = { method: 'post', url: '/echo', headers: { 'Content-Type': 'application/json' } }
  assert.equal((await client('http').request(config)).status, 200)
  const nodeWire = observed.at(-1)
  assert.equal((await client().request(config)).status, 200)
  const xhrWire = observed.at(-1)
  assert.equal(nodeWire.body, xhrWire.body)
  assert.equal(nodeWire.contentType, xhrWire.contentType)
})

for (const method of ['get', 'post']) {
  test(`reusing an empty JSON request config preserves its body and installs one signature transform: ${method}`, async () => {
    const service = client()
    const first = await service.request({ method, url: '/echo', headers: { 'Content-Type': 'application/json' } })
    const transformCount = first.config.transformRequest.length
    const second = await service.request(first.config)
    assert.equal(second.status, 200)
    assert.equal(observed.at(-1).body, '')
    assert.equal(second.config.transformRequest.length, transformCount)
    assert.equal(observed.at(-1).contentType, 'application/json')
  })
}

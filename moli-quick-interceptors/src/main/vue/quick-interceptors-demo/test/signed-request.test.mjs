import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'node:http'
import { generateKeyPairSync, sign, verify } from 'node:crypto'
import axios from 'axios'
import { withRequestSignature } from '../src/util/signed-request.mjs'

const keys = generateKeyPairSync('rsa', { modulusLength: 2048 })
let base
let server
let requests = 0

// Independently reconstruct the Servlet-side signature input from the actual HTTP request.
function serverInput(request, body) {
  const url = new URL(request.url, base)
  const values = new Map()
  const type = String(request.headers['content-type'] ?? '').split(';')[0].toLowerCase()
  const add = (name, value) => {
    assert(!values.has(name), `duplicate wire parameter ${name}`)
    values.set(name, value)
  }
  for (const [name, value] of url.searchParams) add(name, value)
  if (type === 'application/x-www-form-urlencoded') {
    for (const [name, value] of new URLSearchParams(body)) add(name, value)
  }
  values.delete('file')
  if (type === 'application/json' || type.endsWith('+json')) values.set('body', body.replace(/[\r\n]/g, ''))
  values.set('authorization', request.headers.authorization ?? '')
  values.set('t', request.headers.t)
  values.set('nonce', request.headers.nonce)
  const encode = text => text.replaceAll('%', '%25').replaceAll('&', '%26').replaceAll('=', '%3D')
    .replaceAll('\r', '%0D').replaceAll('\n', '%0A')
  return `${request.method.toLowerCase()} ${url.pathname}\n` + [...values.keys()].sort()
    .map(name => `${encode(name)}=${encode(values.get(name))}`).join('&')
}

before(async () => {
  server = createServer(async (request, response) => {
    requests++
    try {
      const chunks = []
      for await (const chunk of request) chunks.push(chunk)
      const body = Buffer.concat(chunks).toString('utf8')
      const input = serverInput(request, body)
      const valid = verify('RSA-SHA256', Buffer.from(input), keys.publicKey, Buffer.from(request.headers.sign, 'base64'))
      response.writeHead(valid ? 200 : 401, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ url: request.url, body, input, valid }))
    } catch (error) {
      response.writeHead(400, { 'Content-Type': 'text/plain' })
      response.end(error.message)
    }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  base = `http://127.0.0.1:${server.address().port}`
})

after(async () => { await new Promise(resolve => server.close(resolve)) })

function client(prefix = '') {
  const service = axios.create({ baseURL: base + prefix, timeout: 3000 })
  service.interceptors.request.use(config => withRequestSignature(config, request => service.getUri(request),
    input => sign('RSA-SHA256', Buffer.from(input), keys.privateKey).toString('base64')))
  return service
}

for (const [name, data, headers, expected] of [
  ['object', { amount: 1 }, undefined, '{"amount":1}'],
  ['serialized JSON', '{"amount":1}', { 'Content-Type': 'application/json' }, '{"amount":1}'],
  ['untyped serialized JSON', '{"amount":1}', undefined, '{"amount":1}'],
  ['formatted JSON with original number spelling', ' {\n "amount":1.00\n} ', { 'Content-Type': 'application/json' }, '{\n "amount":1.00\n}'],
  ['false', false, undefined, 'false'],
  ['zero', 0, undefined, '0'],
  ['null', null, undefined, 'null'],
  ['typed false', false, { 'Content-Type': 'application/json' }, 'false'],
  ['typed zero', 0, { 'Content-Type': 'application/json' }, '0'],
  ['JSON string value', '"hello"', undefined, '"hello"'],
  ['JSON array', [1, 999], undefined, '[1,999]'],
  ['UTF-8 and signature delimiters', { title: '中文&=\n' }, undefined, '{"title":"中文&=\\n"}'],
  ['empty typed body', undefined, { 'Content-Type': 'application/json' }, '']
]) {
  test(`${name} signs exactly the body Axios sends`, async () => {
    const response = await client().post('/echo', data, { headers })
    assert.equal(response.data.body, expected)
    assert.equal(response.data.valid, true)
  })
}

test('baseURL pathname is included in the signature', async () => {
  const response = await client('/api').post('/echo', { amount: 1 })
  assert.equal(response.data.url, '/api/echo')
  assert.match(response.data.input, /^post \/api\/echo\n/)
})

test('URL query and config.params are signed as decoded parameter values', async () => {
  const response = await client('/api').post('/echo?title=invoice%26transfer%3Dtrue', { amount: 1 }, { params: { tag: '中文%26' } })
  assert.match(response.data.input, /title=invoice%26transfer%3Dtrue/)
  assert.match(response.data.input, /tag=中文%2526/)
  assert.match(response.data.input, /^post \/api\/echo\n/)
})

test('absolute URL, encoded pathname and fragment match the actual transport', async () => {
  const response = await client('/unused').get(`${base}/%E4%B8%AD%E6%96%87?q=a+b#local`)
  assert.equal(response.data.url, '/%E4%B8%AD%E6%96%87?q=a+b')
  assert.match(response.data.input, /^get \/%E4%B8%AD%E6%96%87\n/)
  assert.match(response.data.input, /q=a b/)
})

test('a custom query serializer runs once before the signed URL is frozen', async () => {
  let serializations = 0
  const response = await client().get('/echo', { params: { ignored: true }, paramsSerializer: {
    serialize() { serializations++; return `value=${serializations}` }
  } })
  assert.equal(serializations, 1)
  assert.equal(response.data.url, '/echo?value=1')
})

test('URLSearchParams query input is included', async () => {
  const response = await client().get('/echo', { params: new URLSearchParams({ amount: '1' }) })
  assert.equal(response.data.url, '/echo?amount=1')
})

test('default query parameters remain frozen when a request config is reused', async () => {
  const service = client()
  service.defaults.params = { tenant: 'one' }
  const first = await service.get('/echo', { params: { amount: 1 } })
  const second = await service.request(first.config)
  assert.equal(first.data.url, '/echo?tenant=one&amount=1')
  assert.equal(second.data.url, first.data.url)
})

test('POST URLSearchParams and URL query share the signed parameter map', async () => {
  const response = await client().post('/form?tag=one', new URLSearchParams({ amount: '1', body: 'business-data' }))
  assert.match(response.data.input, /amount=1/)
  assert.match(response.data.input, /body=business-data/)
  assert.match(response.data.input, /tag=one/)
})

test('Axios form object transformation is signed after serialization', async () => {
  const response = await client().post('/form', { amount: 1 }, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
  assert.equal(response.data.body, 'amount=1')
})

test('custom transforms execute before signing without a second transformation', async () => {
  let transformations = 0
  const response = await client().post('/before', { ignored: true }, { transformRequest: [function (_, headers) {
    transformations++
    this.url = '/after'
    this.params = { changed: 'true' }
    headers.setContentType('application/json')
    return '{"amount":2}'
  }] })
  assert.equal(transformations, 1)
  assert.equal(response.data.url, '/after?changed=true')
  assert.equal(response.data.body, '{"amount":2}')
})

test('default ignored fields match the backend while remaining on the wire', async () => {
  const response = await client().get('/echo?file=business-field&amount=1')
  assert(!response.data.input.includes('file='))
  assert(response.data.url.includes('file='))
})

for (const url of ['/echo?t=999', '/echo?Nonce=999', '/echo?sign=x', '/echo?Authorization=x',
  '/echo?body=x', '/echo?id=1&id=999', '/echo?file=one&file=two']) {
  test(`rejects conflicting or duplicate parameters before transport: ${url}`, async () => {
    const before = requests
    await assert.rejects(client().post(url, { amount: 1 }), /signature parameter|one value per parameter/)
    assert.equal(requests, before)
  })
}

test('rejects duplicates across inline query and config.params', async () => {
  const before = requests
  await assert.rejects(client().get('/echo?id=1', { params: { id: 999 } }), /one value per parameter/)
  assert.equal(requests, before)
})

test('rejects duplicates across query and POST form fields', async () => {
  const before = requests
  await assert.rejects(client().post('/form?id=1', new URLSearchParams({ id: '999' })), /one value per parameter/)
  assert.equal(requests, before)
})

test('rejects unsupported bodies before making an unsigned request', async () => {
  const before = requests
  await assert.rejects(client().post('/echo', 'raw text', { headers: { 'Content-Type': 'text/plain' } }), /Signed bodies require/)
  await assert.rejects(client().get('/echo', { data: { amount: 1 } }), /cannot carry a body/)
  assert.equal(requests, before)
})

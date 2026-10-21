import { canonicalizeSignature } from './signature-canonicalization.mjs'

const signatureTransforms = new WeakMap()

function addParameter(parameters, name, value, json) {
  if (parameters.has(name)) throw new Error(`Signed requests require one value per parameter: ${name}`)
  if (['authorization', 'sign', 't', 'nonce'].includes(name.toLowerCase()) || (json && name.toLowerCase() === 'body')) {
    throw new Error(`Reserved signature parameter: ${name}`)
  }
  parameters.set(name, value)
}

/** Sign after Axios transforms data, then freeze the URL and the exact serialized body sent by its adapter. */
export function withRequestSignature(config, getUri, signer) {
  const transforms = config.transformRequest == null ? []
    : Array.isArray(config.transformRequest) ? config.transformRequest : [config.transformRequest]
  for (const transform of transforms) {
    if (signatureTransforms.get(transform)?.emptyJson && config.data === '') config.data = undefined
  }
  const state = { emptyJson: false }
  const signatureTransform = function (data, headers) {
    const method = (this.method ?? 'get').toLowerCase()
    const uri = getUri(this)
    const url = typeof window === 'undefined' ? new URL(uri) : new URL(uri, window.location.href)
    if (this.auth || url.username || url.password) throw new Error('Signed requests cannot use transport Basic authentication')
    url.hash = ''

    let type = String(headers.getContentType() || '').split(';', 1)[0].trim().toLowerCase()
    // Axios leaves bare JSON strings and scalar values untyped. Give them one explicit wire representation.
    if (!type && data !== undefined) {
      if (typeof data === 'string') JSON.parse(data)
      else if (data === null || typeof data === 'boolean' || typeof data === 'number') data = JSON.stringify(data)
      else throw new Error('Signed bodies require JSON or POST URL-encoded form fields')
      headers.setContentType('application/json')
      type = 'application/json'
    }
    const json = type === 'application/json' || (type.startsWith('application/') && type.endsWith('+json'))
    const form = type === 'application/x-www-form-urlencoded' && method === 'post'
    if (data !== undefined && data !== null && (method === 'get' || method === 'head')) {
      throw new Error('Signed GET and HEAD requests cannot carry a body')
    }
    if (data !== undefined && data !== null && (!json && !form || typeof data !== 'string')) {
      throw new Error('Signed bodies require serialized JSON or POST URL-encoded form fields')
    }
    if (json && data != null && data !== '') JSON.parse(data)
    // XHR deletes Content-Type for undefined data. Keep the signed JSON body= representation
    // and the JSON header together, including on bodyless GET/HEAD requests.
    if (json && data === undefined) {
      state.emptyJson = true
      data = ''
    }

    const parameters = new Map()
    for (const [name, value] of url.searchParams) addParameter(parameters, name, value, json)
    if (form && data != null) {
      for (const [name, value] of new URLSearchParams(data)) addParameter(parameters, name, value, false)
    }
    // Match the demo backend's default ignore-parameters-list, after validating duplicates.
    parameters.delete('file')
    if (json) parameters.set('body', String(data ?? '').replace(/[\r\n]/g, ''))
    parameters.set('authorization', '')
    const timestamp = String(Date.now())
    const nonce = Math.random().toString(10).slice(-8)
    parameters.set('t', timestamp)
    parameters.set('nonce', nonce)
    headers.set('Authorization', '')
    headers.set('t', timestamp)
    headers.set('nonce', nonce)
    headers.set('sign', signer(canonicalizeSignature(method, url.pathname, parameters)))

    this.url = url.href
    this.baseURL = undefined
    this.params = null
    return data
  }
  signatureTransforms.set(signatureTransform, state)
  config.transformRequest = [...transforms.filter(transform => !signatureTransforms.has(transform)), signatureTransform]
  return config
}

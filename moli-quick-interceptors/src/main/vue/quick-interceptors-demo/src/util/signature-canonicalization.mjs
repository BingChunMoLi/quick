export function encodeSignatureComponent(value) {
  const escapes = { '%': '%25', '&': '%26', '=': '%3D', '\r': '%0D', '\n': '%0A' }
  return value.replace(/[%&=\r\n]/g, (character) => escapes[character])
}

export function canonicalizeSignature(method, path, parameters) {
  const entries = [...parameters].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
  const query = entries.map(([key, value]) => `${encodeSignatureComponent(key)}=${encodeSignatureComponent(value)}`).join('&')
  return `${method.toLowerCase()} ${path}${entries.length ? '\n' + query : ''}`
}

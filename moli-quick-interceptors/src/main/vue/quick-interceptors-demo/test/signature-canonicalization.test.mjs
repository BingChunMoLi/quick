import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import test from 'node:test'
import { canonicalizeSignature, encodeSignatureComponent } from '../src/util/signature-canonicalization.mjs'

const vectors = JSON.parse(readFileSync(new URL('../../../../test/resources/signature-canonicalization.json', import.meta.url), 'utf8'))
for (const vector of vectors) {
  test(vector.name, () => {
    const parameters = new Map([['authorization', ''], ...Object.entries(vector.parameters)])
    assert.equal(canonicalizeSignature(vector.method, vector.path, parameters), vector.expected)
  })
}
test('a literal escape never aliases the delimiter it describes', () => {
  assert.notEqual(encodeSignatureComponent('%26'), encodeSignatureComponent('&'))
  assert.notEqual(encodeSignatureComponent('%3D'), encodeSignatureComponent('='))
  assert.notEqual(encodeSignatureComponent('%0A'), encodeSignatureComponent('\n'))
})

import {KEYUTIL, KJUR} from 'jsrsasign'

const privateKey =
  '-----BEGIN PRIVATE KEY-----MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAMS/oUut0jArVutj3JclUyD3kds9MVvzasILtQcQUFIatwHQdRUFgSzpYosKr++qcDHD0ed5um9E16rU3i5ukitJLeogQqrFnPWU8hLHJnTICQkB95W7nuIMDt8StsQU1qVDx5UeH2CVoB2Kw1eTK1soyZlMGP7KwUxXjUhFhymtAgMBAAECgYAY7rPQ9FELe6KDJ99/Y+J57gWUw9dqcJlZx8QEBiQ3k10XpMQBivYIq/ap6gvLkkyvccI61Ck1CoNOCbWyKp7pFnW8pLZcaMClUoJtejhXsvjryiiU8xu3coXgoMY7RPoOnG4rejl7NftCFabvhcV+1J6xlUGU2e6DulMXw/C/4QJBAOKzZgkkNiCicbeKYu0JhJHDcnCpclO6cwLXDxAJli+pREpdO/re/onxcQeJcd5/GestMEp14qwGyXXzt/Eg1vUCQQDeLTwphXAEaRTUQf0UOS0klxDBMg229933k4L9cMrJfIyzHF1BjbWBQ7Wzf0dOXWLtuk3SCeBdoXYQJp2FjKTZAkBb1+BzvabsK8O0Xy1a6Os7fppDiHKX90aXFPe2QJwG7SWMlx8wsN0gl596NTXbtVST0bfpwr61gP5nefjAiNRtAkBTti2F434aW1F6zEZD+WsV70lj0a0zNF9Q6iafwYDWu/PzhItqqK8rvkxXDmzajPs6YUjzC7+a57wpR7NwX1a5AkEA0i5fr75XDDlvSJmG1lYynEYZILXlBB9tzugZQlxzm0jJYBGb3MH+/jnG954qdFe/ysqAMhYDOLuMB+cnE2OB5g==-----END PRIVATE KEY-----'
const sig = new KJUR.crypto.Signature({ alg: 'SHA256withRSA' })
sig.init(KEYUTIL.getKey(privateKey))
export function sign(str: string): string {
  sig.updateString(str)
  return sig.sign()
}

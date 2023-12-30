import {KEYUTIL, KJUR, hextob64} from 'jsrsasign'

const privateKey =
  '-----BEGIN PRIVATE KEY-----MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJ4TePZWwZTngoS0LSWmUzdUpPPHFxoLVRMrsz9T6dmOzzf0E8d7SDuuciWCPTfNVqsq6WdI34OtaBpVOOclyK/+ScOGiReWU3M4tDVEx7KXfWuqVwTBlKBxX89UvVqD6KKJEvnQywLdl8MxqOfayq3Ty0vWna2IxV8IA5DyJV1jAgMBAAECgYATuWdLCZk/DMIm79qAS6gvi8LHXky+JbLYYQgNO1m/GUg2+aAsMHBbbhF1J7Kez2Lkmcak6h1lr1IBkye2A+tuBAs2g6+cER7it/wlFXiVAqDC8MbkZl7tSJ8be8RtyZn8E1EX+hU8bp+8rB2EZC0+Vh+y0G4mvAYSb4PigjyszQJBANjdCKI7Ffcyo9OkIMKpohkC8bNddjLQQC5pE1HQ3hud1KKMnLklGlngYD8xLisJwySY69fqqe2N36rx5u5DzF8CQQC6mn8qDJ34IYUAY43IJHeLfjMajwTeRcmqVnKKoq6Gwm6w+L6SZ9RDqQIPv3olaSbPgLv/W5asdNt+HhNyrk19AkAZAMLyEWXi7k+9Ivsz9mLeB1zelTP/f8smjq7PjAlPnc7aPuQC5Kg5HIl8P6tXAvUgzxDCACg/X1AeYW2CowZ5AkAV9s11A5ssc7WWACHWJwSzNJ2LSL5H82rzU82Tp+ZnEke5KO41iWcgt6n4dI9KDAB3eC2otv0bO34CcEPVwdJ5AkEAqwpeFFiI8zwhxh00kpLbX2pOtPmxdPIh5R3tvHS7SHrTxl/fDfwiGD3xgCf2hHBS8LBb/jePoYsU5pC2qjPtFQ==-----END PRIVATE KEY-----'
export function sign(str: string): string {
  const sig = new KJUR.crypto.Signature({ alg: 'SHA256withRSA' })
  sig.init(KEYUTIL.getKey(privateKey))
  sig.updateString(str)
  return hextob64(sig.sign())
}

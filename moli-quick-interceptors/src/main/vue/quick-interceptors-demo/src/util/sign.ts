import {KEYUTIL, KJUR} from 'jsrsasign'

const privateKey =
    `-----BEGIN PRIVATE KEY-----
    MIICdgIBADANBgkqhkiG9w0BAQEF
    AASCAmAwggJcAgEAAoGBAMS/oUut
    0jArVutj3JclUyD3kds9MVvzasIL
    tQcQUFIatwHQdRUFgSzpYosKr++q
    cDHD0ed5um9E16rU3i5ukitJLeog
    QqrFnPWU8hLHJnTICQkB95W7nuIM
    Dt8StsQU1qVDx5UeH2CVoB2Kw1eT
    K1soyZlMGP7KwUxXjUhFhymtAgMB
    AAECgYAY7rPQ9FELe6KDJ99/Y+J5
    7gWUw9dqcJlZx8QEBiQ3k10XpMQB
    ivYIq/ap6gvLkkyvccI61Ck1CoNO
    CbWyKp7pFnW8pLZcaMClUoJtejhX
    svjryiiU8xu3coXgoMY7RPoOnG4r
    ejl7NftCFabvhcV+1J6xlUGU2e6D
    ulMXw/C/4QJBAOKzZgkkNiCicbeK
    Yu0JhJHDcnCpclO6cwLXDxAJli+p
    REpdO/re/onxcQeJcd5/GestMEp1
    4qwGyXXzt/Eg1vUCQQDeLTwphXAE
    aRTUQf0UOS0klxDBMg229933k4L9
    cMrJfIyzHF1BjbWBQ7Wzf0dOXWLt
    uk3SCeBdoXYQJp2FjKTZAkBb1+BzvabsK8O0Xy1a6Os7fppDiHKX90aXFPe2QJwG7SWMlx8wsN0gl596NTXbtVST0bfpwr61gP5nefjAiNRtAkBTti2F434aW1F6zEZD+WsV70lj0a0zNF9Q6iafwYDWu/PzhItqqK8rvkxXDmzajPs6YUjzC7+a57wpR7NwX1a5AkEA0i5fr75XDDlvSJmG1lYynEYZILXlBB9tzugZQlxzm0jJYBGb3MH+/jnG954qdFe/ysqAMhYDOLuMB+cnE2OB5g==-----BEGIN END KEY-----`
// const sign = new KJUR.crypto.Signature({"alg": "SHA1withRSA"});
const sig = new KJUR.crypto.Signature({"alg": "SHA1withRSA"});
sig.init(KEYUTIL.getKey(privateKey))

export function sign(str: string): string {
    sig.updateString(str);
    return sig.sign();

//   const rsa = new RSAKey();
//   rsa.readPrivateKeyFromPEMString(privateKey);
//   const hashAlg = "sha256";
//   const hSig = rsa.sign(str, hashAlg);
// //   document.form1.siggenerated.value = linebrk(hSig, 64);
//   console.log(hSig);
//   return hSig;
}

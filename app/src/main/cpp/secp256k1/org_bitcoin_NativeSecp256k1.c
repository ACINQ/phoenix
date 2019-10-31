#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include "org_bitcoin_NativeSecp256k1.h"
#include "include/secp256k1.h"
#include "include/secp256k1_ecdh.h"
#include "include/secp256k1_recovery.h"


SECP256K1_API jlong JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ctx_1clone
  (JNIEnv* env, jclass classObject, jlong ctx_l)
{
  const secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;

  jlong ctx_clone_l = (uintptr_t) secp256k1_context_clone(ctx);

  (void)classObject;(void)env;

  return ctx_clone_l;

}

SECP256K1_API jint JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1context_1randomize
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;

  const unsigned char* seed = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);

  (void)classObject;

  return secp256k1_context_randomize(ctx, seed);

}

SECP256K1_API void JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1destroy_1context
  (JNIEnv* env, jclass classObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;

  secp256k1_context_destroy(ctx);

  (void)classObject;(void)env;
}

SECP256K1_API jint JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ecdsa_1verify
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint siglen, jint publen)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;

  unsigned char* data = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);
  const unsigned char* sigdata = {  (unsigned char*) (data + 32) };
  const unsigned char* pubdata = { (unsigned char*) (data + siglen + 32) };

  secp256k1_ecdsa_signature sig;
  secp256k1_pubkey pubkey;

  int ret = 0;
  if (siglen == 64) {
      ret = secp256k1_ecdsa_signature_parse_compact(ctx, &sig, sigdata);
  } else {
      ret = secp256k1_ecdsa_signature_parse_der(ctx, &sig, sigdata, siglen);
  }

  if( ret ) {
    ret = secp256k1_ec_pubkey_parse(ctx, &pubkey, pubdata, publen);

    if( ret ) {
      ret = secp256k1_ecdsa_verify(ctx, &sig, data, &pubkey);
    }
  }

  (void)classObject;

  return ret;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ecdsa_1sign
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  unsigned char* data = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);
  unsigned char* secKey = (unsigned char*) (data + 32);

  jobjectArray retArray;
  jbyteArray sigArray, intsByteArray;
  unsigned char intsarray[2];

  secp256k1_ecdsa_signature sig;

  int ret = secp256k1_ecdsa_sign(ctx, &sig, data, secKey, NULL, NULL);

  unsigned char outputSer[72];
  size_t outputLen = 72;

  if( ret ) {
    int ret2 = secp256k1_ecdsa_signature_serialize_der(ctx,outputSer, &outputLen, &sig ); (void)ret2;
  }

  intsarray[0] = outputLen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  sigArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, sigArray, 0, outputLen, (jbyte*)outputSer);
  (*env)->SetObjectArrayElement(env, retArray, 0, sigArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

JNIEXPORT jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ecdsa_1sign_1compact
(JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  unsigned char* data = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);
  unsigned char* secKey = (unsigned char*) (data + 32);

  jobjectArray retArray;
  jbyteArray sigArray, intsByteArray;
  unsigned char intsarray[2];

  secp256k1_ecdsa_signature sig[72];

  int ret = secp256k1_ecdsa_sign(ctx, sig, data, secKey, NULL, NULL);

   unsigned char outputSer[64];
   size_t outputLen = 64;

  if( ret ) {
    int ret2 = secp256k1_ecdsa_signature_serialize_compact(ctx,outputSer, sig ); (void)ret2;
  }

  intsarray[0] = outputLen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  sigArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, sigArray, 0, outputLen, (jbyte*)outputSer);
  (*env)->SetObjectArrayElement(env, retArray, 0, sigArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}


SECP256K1_API jint JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ec_1seckey_1verify
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  unsigned char* secKey = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);

  (void)classObject;

  return secp256k1_ec_seckey_verify(ctx, secKey);
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ec_1pubkey_1create
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  const unsigned char* secKey = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);

  secp256k1_pubkey pubkey;

  jobjectArray retArray;
  jbyteArray pubkeyArray, intsByteArray;
  unsigned char intsarray[2];

  int ret = secp256k1_ec_pubkey_create(ctx, &pubkey, secKey);

  unsigned char outputSer[65];
  size_t outputLen = 65;

  if( ret ) {
    int ret2 = secp256k1_ec_pubkey_serialize(ctx,outputSer, &outputLen, &pubkey,SECP256K1_EC_UNCOMPRESSED );(void)ret2;
  }

  intsarray[0] = outputLen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  pubkeyArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, pubkeyArray, 0, outputLen, (jbyte*)outputSer);
  (*env)->SetObjectArrayElement(env, retArray, 0, pubkeyArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ec_1pubkey_1parse
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint inputlen)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  const unsigned char* pubkeydata = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);

  secp256k1_pubkey pubkey;

  jobjectArray retArray;
  jbyteArray pubkeyArray, intsByteArray;
  unsigned char intsarray[2];

  int ret = secp256k1_ec_pubkey_parse(ctx, &pubkey, pubkeydata, inputlen);

  unsigned char outputSer[65];
  size_t outputLen = 65;

  if( ret ) {
    int ret2 = secp256k1_ec_pubkey_serialize(ctx,outputSer, &outputLen, &pubkey, SECP256K1_EC_UNCOMPRESSED );(void)ret2;
  }

  intsarray[0] = outputLen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  pubkeyArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, pubkeyArray, 0, outputLen, (jbyte*)outputSer);
  (*env)->SetObjectArrayElement(env, retArray, 0, pubkeyArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1privkey_1negate
(JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
    secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
    unsigned char* privkey = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);

    jobjectArray retArray;
    jbyteArray privArray, intsByteArray;
    unsigned char intsarray[2];

    int privkeylen = 32;

    int ret = secp256k1_ec_privkey_negate(ctx, privkey);

    intsarray[0] = privkeylen;
    intsarray[1] = ret;

    retArray = (*env)->NewObjectArray(env, 2,
      (*env)->FindClass(env, "[B"),
      (*env)->NewByteArray(env, 1));

    privArray = (*env)->NewByteArray(env, privkeylen);
    (*env)->SetByteArrayRegion(env, privArray, 0, privkeylen, (jbyte*)privkey);
    (*env)->SetObjectArrayElement(env, retArray, 0, privArray);

    intsByteArray = (*env)->NewByteArray(env, 2);
    (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
    (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

    (void)classObject;

    return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1privkey_1tweak_1add
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  unsigned char* privkey = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);
  const unsigned char* tweak = (unsigned char*) (privkey + 32);

  jobjectArray retArray;
  jbyteArray privArray, intsByteArray;
  unsigned char intsarray[2];

  int privkeylen = 32;

  int ret = secp256k1_ec_privkey_tweak_add(ctx, privkey, tweak);

  intsarray[0] = privkeylen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  privArray = (*env)->NewByteArray(env, privkeylen);
  (*env)->SetByteArrayRegion(env, privArray, 0, privkeylen, (jbyte*)privkey);
  (*env)->SetObjectArrayElement(env, retArray, 0, privArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1privkey_1tweak_1mul
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  unsigned char* privkey = (unsigned char*) (*env)->GetDirectBufferAddress(env, byteBufferObject);
  const unsigned char* tweak = (unsigned char*) (privkey + 32);

  jobjectArray retArray;
  jbyteArray privArray, intsByteArray;
  unsigned char intsarray[2];

  int privkeylen = 32;

  int ret = secp256k1_ec_privkey_tweak_mul(ctx, privkey, tweak);

  intsarray[0] = privkeylen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  privArray = (*env)->NewByteArray(env, privkeylen);
  (*env)->SetByteArrayRegion(env, privArray, 0, privkeylen, (jbyte*)privkey);
  (*env)->SetObjectArrayElement(env, retArray, 0, privArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

JNIEXPORT jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1pubkey_1negate
(JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint publen)
{
    secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
    unsigned char* pkey = (*env)->GetDirectBufferAddress(env, byteBufferObject);

    jobjectArray retArray;
    jbyteArray pubArray, intsByteArray;
    unsigned char intsarray[2];
    unsigned char outputSer[65];
    size_t outputLen = publen;

    secp256k1_pubkey pubkey;
    int ret = secp256k1_ec_pubkey_parse(ctx, &pubkey, pkey, publen);

    if( ret ) {
      ret = secp256k1_ec_pubkey_negate(ctx, &pubkey);
    }

    if( ret ) {
      int ret2 = secp256k1_ec_pubkey_serialize(ctx,outputSer, &outputLen, &pubkey, publen == 33 ?  SECP256K1_EC_COMPRESSED : SECP256K1_EC_UNCOMPRESSED );(void)ret2;
    }

    intsarray[0] = outputLen;
    intsarray[1] = ret;

    retArray = (*env)->NewObjectArray(env, 2,
      (*env)->FindClass(env, "[B"),
      (*env)->NewByteArray(env, 1));

    pubArray = (*env)->NewByteArray(env, outputLen);
    (*env)->SetByteArrayRegion(env, pubArray, 0, outputLen, (jbyte*)outputSer);
    (*env)->SetObjectArrayElement(env, retArray, 0, pubArray);

    intsByteArray = (*env)->NewByteArray(env, 2);
    (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
    (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

    (void)classObject;

    return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1pubkey_1tweak_1add
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint publen)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
/*  secp256k1_pubkey* pubkey = (secp256k1_pubkey*) (*env)->GetDirectBufferAddress(env, byteBufferObject);*/
  unsigned char* pkey = (*env)->GetDirectBufferAddress(env, byteBufferObject);
  const unsigned char* tweak = (unsigned char*) (pkey + publen);

  jobjectArray retArray;
  jbyteArray pubArray, intsByteArray;
  unsigned char intsarray[2];
  unsigned char outputSer[65];
  size_t outputLen = 65;

  secp256k1_pubkey pubkey;
  int ret = secp256k1_ec_pubkey_parse(ctx, &pubkey, pkey, publen);

  if( ret ) {
    ret = secp256k1_ec_pubkey_tweak_add(ctx, &pubkey, tweak);
  }

  if( ret ) {
    int ret2 = secp256k1_ec_pubkey_serialize(ctx,outputSer, &outputLen, &pubkey,SECP256K1_EC_UNCOMPRESSED );(void)ret2;
  }

  intsarray[0] = outputLen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  pubArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, pubArray, 0, outputLen, (jbyte*)outputSer);
  (*env)->SetObjectArrayElement(env, retArray, 0, pubArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1pubkey_1tweak_1mul
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint publen)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  unsigned char* pkey = (*env)->GetDirectBufferAddress(env, byteBufferObject);
  const unsigned char* tweak = (unsigned char*) (pkey + publen);

  jobjectArray retArray;
  jbyteArray pubArray, intsByteArray;
  unsigned char intsarray[2];
  unsigned char outputSer[65];
  size_t outputLen = 65;

  secp256k1_pubkey pubkey;
  int ret = secp256k1_ec_pubkey_parse(ctx, &pubkey, pkey, publen);

  if ( ret ) {
    ret = secp256k1_ec_pubkey_tweak_mul(ctx, &pubkey, tweak);
  }

  if( ret ) {
    int ret2 = secp256k1_ec_pubkey_serialize(ctx,outputSer, &outputLen, &pubkey,SECP256K1_EC_UNCOMPRESSED );(void)ret2;
  }

  intsarray[0] = outputLen;
  intsarray[1] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  pubArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, pubArray, 0, outputLen, (jbyte*)outputSer);
  (*env)->SetObjectArrayElement(env, retArray, 0, pubArray);

  intsByteArray = (*env)->NewByteArray(env, 2);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

JNIEXPORT jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ec_1pubkey_1add
(JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint publen1, jint publen2)
{
    secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
    const unsigned char* pubdata1 = (*env)->GetDirectBufferAddress(env, byteBufferObject);
    const unsigned char* pubdata2 = (const unsigned char*) (pubdata1 + publen1);
    jobjectArray retArray;
    jbyteArray pubArray, intsByteArray;
    unsigned char intsarray[2];
    secp256k1_pubkey pubkey1, pubkey2;
    const secp256k1_pubkey *pubkeys[2];
    unsigned char outputSer[65];
    size_t outputLen = 65;

    int ret = secp256k1_ec_pubkey_parse(ctx, &pubkey1, pubdata1, publen1);
    if (ret) {
        ret = secp256k1_ec_pubkey_parse(ctx, &pubkey2, pubdata2, publen2);
    }
    secp256k1_pubkey result;
    if (ret) {
        pubkeys[0] = &pubkey1;
        pubkeys[1] = &pubkey2;
        ret = secp256k1_ec_pubkey_combine(ctx, &result, pubkeys, 2);
    }
    if (ret) {
        ret = secp256k1_ec_pubkey_serialize(ctx, outputSer, &outputLen, &result, SECP256K1_EC_UNCOMPRESSED );
    }
    intsarray[0] = outputLen;
    intsarray[1] = ret;

    retArray = (*env)->NewObjectArray(env, 2,
      (*env)->FindClass(env, "[B"),
      (*env)->NewByteArray(env, 1));

    pubArray = (*env)->NewByteArray(env, outputLen);
    (*env)->SetByteArrayRegion(env, pubArray, 0, outputLen, (jbyte*)outputSer);
    (*env)->SetObjectArrayElement(env, retArray, 0, pubArray);

    intsByteArray = (*env)->NewByteArray(env, 2);
    (*env)->SetByteArrayRegion(env, intsByteArray, 0, 2, (jbyte*)intsarray);
    (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

    (void)classObject;

    return retArray;
}

SECP256K1_API jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ecdh
  (JNIEnv* env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint publen)
{
  secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
  const unsigned char* secdata = (*env)->GetDirectBufferAddress(env, byteBufferObject);
  const unsigned char* pubdata = (const unsigned char*) (secdata + 32);

  jobjectArray retArray;
  jbyteArray outArray, intsByteArray;
  unsigned char intsarray[1];
  secp256k1_pubkey pubkey;
  unsigned char nonce_res[32];
  size_t outputLen = 32;

  int ret = secp256k1_ec_pubkey_parse(ctx, &pubkey, pubdata, publen);

  if (ret) {
    ret = secp256k1_ecdh(
      ctx,
      nonce_res,
      &pubkey,
      secdata,
      NULL,
      NULL
    );
  }

  intsarray[0] = ret;

  retArray = (*env)->NewObjectArray(env, 2,
    (*env)->FindClass(env, "[B"),
    (*env)->NewByteArray(env, 1));

  outArray = (*env)->NewByteArray(env, outputLen);
  (*env)->SetByteArrayRegion(env, outArray, 0, 32, (jbyte*)nonce_res);
  (*env)->SetObjectArrayElement(env, retArray, 0, outArray);

  intsByteArray = (*env)->NewByteArray(env, 1);
  (*env)->SetByteArrayRegion(env, intsByteArray, 0, 1, (jbyte*)intsarray);
  (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

  (void)classObject;

  return retArray;
}

/*
 * Class:     org_bitcoin_NativeSecp256k1
 * Method:    secp256k1_ecdsa_recover
 * Signature: (Ljava/nio/ByteBuffer;JI)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_org_bitcoin_NativeSecp256k1_secp256k1_1ecdsa_1recover
  (JNIEnv *env, jclass classObject, jobject byteBufferObject, jlong ctx_l, jint recid)
{
    secp256k1_context *ctx = (secp256k1_context*)(uintptr_t)ctx_l;
    const unsigned char* sigdata = (*env)->GetDirectBufferAddress(env, byteBufferObject);
    const unsigned char* msgdata = (const unsigned char*)(sigdata + 64);
    secp256k1_ecdsa_recoverable_signature sig;
    secp256k1_pubkey pub;
    unsigned char outputSer[65];
    size_t outputLen = 65;
    jobjectArray retArray;
    jbyteArray pubArray, intsByteArray;
    unsigned char intsarray[1];

    int ret = secp256k1_ecdsa_recoverable_signature_parse_compact(ctx, &sig, sigdata, recid);
    if (ret) {
        ret = secp256k1_ecdsa_recover(ctx, &pub, &sig, msgdata);
        if (ret) {
            ret = secp256k1_ec_pubkey_serialize(ctx, outputSer, &outputLen, &pub, SECP256K1_EC_UNCOMPRESSED );
        }
    }

    intsarray[0] = ret;

    retArray = (*env)->NewObjectArray(env, 2,
      (*env)->FindClass(env, "[B"),
      (*env)->NewByteArray(env, 1));

    pubArray = (*env)->NewByteArray(env, outputLen);
    (*env)->SetByteArrayRegion(env, pubArray, 0, outputLen, (jbyte*)outputSer);
    (*env)->SetObjectArrayElement(env, retArray, 0, pubArray);

    intsByteArray = (*env)->NewByteArray(env, 1);
    (*env)->SetByteArrayRegion(env, intsByteArray, 0, 1, (jbyte*)intsarray);
    (*env)->SetObjectArrayElement(env, retArray, 1, intsByteArray);

    (void)classObject;

    return retArray;
}


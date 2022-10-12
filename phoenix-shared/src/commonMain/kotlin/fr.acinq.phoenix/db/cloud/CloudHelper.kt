package fr.acinq.phoenix.db.cloud

import io.ktor.util.*

// Kotlin wants to encode a ByteArray like this: {
//   "fail": [123,34,112,97,121,109,101,110,116,82,101,113,117]
// }
//
// Lol. If we don't use Cbor, then we should at least use Base64.

fun ByteArray.b64Encode(): String {
    return this.encodeBase64() // io.ktor.util
}

fun String.b64Decode(): ByteArray {
    return this.decodeBase64Bytes() // io.ktor.util
}

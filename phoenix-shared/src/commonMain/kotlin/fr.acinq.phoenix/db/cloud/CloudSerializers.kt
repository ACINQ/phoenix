package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.utils.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborBuilder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

// Notes from the field:
//
// Consider the following JSON:
// {
//   "preimage":"JuO9VOOW/5pzCKsaCO7a9E/ETS7Bef5yyWVRBJBYmOQ=",
//   "origin":{
//     "type":"INVOICE_V0",
//     "blob":"eyJwYXltZW50UmVxdWVzdCI6ImxudGIxMDB1MXBzMDNmc3VwcDVnbmh4NmR4NTA4cnM3OG1kcnB3Y3U3cWgwNGZja3hjbmRhdXdueXp1NjRuM3V5dzRqZHRzZHE1ZmFjeDJtM3F2ZDV4em1ud3Y0a3FjcXBqc3A1aHIyZDg1cDdkNm5xcGtyZXVtNGo5czZycjBwaG1weGhzdzVqYXJyNTI5ZGxsYXY5MGZ3cTlxdHpxcXFxcXF5c2dxeHF5anc1cXJ6anF3Zm4zcDkyNzh0dHp6cGUwZTAwdWh5eGhuZWQzajVkOWFjcWFrNWVtd2ZwZmxwOHoyY25mbGNmemNhODA1NmsweXFxcXFsZ3FxcXFxZXFxanFmOTN4M3YyM3IwZTg1a3p5cXJlaDY4ZGhxbGQwY2w3and4OTdwZDZwemF4Y2N1Y3l3NzhxZGc1ZzJsdzhrZnlmdWQzMnN4d2NtNDlhY2wwNXdxd3phajA4djdyeHQ5cWZ4Z2E3MDNzcGhrcmZhdCJ9"
//   },
//   "received":{
//     "ts":1626908236089,
//     "type":"MULTIPARTS_V0",
//     "blob":"W3sidHlwZSI6ImZyLmFjaW5xLnBob2VuaXguZGIucGF5bWVudHMuSW5jb21pbmdSZWNlaXZlZFdpdGhEYXRhLlBhcnQuTmV3Q2hhbm5lbC5WMCIsImFtb3VudCI6eyJtc2F0IjoxMDAwMDAwMH0sImZlZXMiOnsibXNhdCI6MzAwMDAwMH0sImNoYW5uZWxJZCI6bnVsbH1d"
//   },
//   "createdAt":1626908189069
// }
//
// Now there are 4 different ways in which we can encode this data.
//
// 1. Use JSON serialization, and encode the data as Base64.
//    The output looks exactly like the above.
//    ```
//    @Serializable(with = ByteVector32JsonSerializer::class)
//    val preimage: ByteVector32
//    ```
//
// 2. Use CBOR serialization, and encode the data as Base64.
//    ```
//    @Serializable(with = ByteVector32JsonSerializer::class)
//    val preimage: ByteVector32
//    ```
//
// 3. Use CBOR serialization, and encode the data as ByteArray.
//    Since CBOR supports raw data, this should encode smaller.
//    ```
//    val preimage: ByteArray
//    ```
//
// 4. Use CBOR serialization, and encode the data as ByteArray w/ByteString.
//    The docs mention that we can opt-in to use CBOR major type 2.
//    ```
//    @ByteString
//    val preimage: ByteArray
//    ```
//
// After attempting all the above options, here are the results:
// 1.   915 bytes
// 2.   883 bytes
// 3. 1,256 bytes (huh?)
// 4.   690 bytes !
//
// The winner is CBOR with @ByteString.

object ByteVectorJsonSerializer : KSerializer<ByteVector> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ByteVector", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteVector) {
        return encoder.encodeString(value.toByteArray().b64Encode())
    }

    override fun deserialize(decoder: Decoder): ByteVector {
        return ByteVector(decoder.decodeString().b64Decode())
    }
}

object ByteVector32JsonSerializer : KSerializer<ByteVector32> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ByteVector32", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteVector32) {
        return encoder.encodeString(value.toByteArray().b64Encode())
    }

    override fun deserialize(decoder: Decoder): ByteVector32 {
        return ByteVector32(decoder.decodeString().b64Decode())
    }
}

// A UUID is serialized as: {
//   "mostSignificantBits":-1321539888342873580,
//   "leastSignificantBits":-7509590717981962141
// }
//
// But we can decrease the size.
//

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        return encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

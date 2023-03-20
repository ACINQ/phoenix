package fr.acinq.phoenix.db.serializers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.secp256k1.Hex

fun OutPoint.serializeForDb(): String {
    return "${hash.toHex()}:${this.index}"
}

fun OutPoint.Companion.deserializeFromDb(data: String): OutPoint {
    return data.split(":").let {
        OutPoint(ByteVector32.fromValidHex(it[0]), it[1].toLong())
    }
}

package fr.acinq.phoenix.utils

import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytesNoCopy
import platform.posix.memcpy


class PinnedData(private val pinned: Pinned<ByteArray>) {
    @OptIn(ExperimentalUnsignedTypes::class)
    fun toData(): NSData {
        return NSData.dataWithBytesNoCopy(pinned.addressOf(0), pinned.get().size.toULong(), false)
    }

    fun unpin() { pinned.unpin() }
}

fun ByteArray.kotlinPin() = PinnedData(pin())

@OptIn(ExperimentalUnsignedTypes::class)
fun NSData.toByteArray() =
    ByteArray(length.toInt()).apply {
        if (length.toInt() > 0) {
            usePinned {
                memcpy(it.addressOf(0), bytes, length)
            }
        }
    }

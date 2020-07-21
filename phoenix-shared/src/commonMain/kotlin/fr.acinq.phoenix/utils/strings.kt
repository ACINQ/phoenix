package fr.acinq.phoenix.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow


@OptIn(ExperimentalUnsignedTypes::class)
fun utf8ByteCount(firstCodePoint: Byte) =
    when (firstCodePoint.toUByte().toInt()) {
        in 0..0x7F -> 1
        in 0xC0..0xDF -> 2
        in 0xE0..0xEF -> 3
        in 0xF0..0xF7 -> 4
        else -> error("Malformed UTF-8 character (bad first codepoint 0x${firstCodePoint.toUByte().toString(16).padStart(2, '0')})")
    }

fun Flow<ByteArray>.decodeToString(): Flow<String> =
    flow {
        val splitBytes = ByteArray(3)
        var splitBytesSize = 0
        collect { receivedBytes ->
            val bytes = splitBytes.subArray(splitBytesSize) concat receivedBytes

            var correctSize = 0
            while (correctSize < bytes.size) {
                val count = utf8ByteCount(bytes[correctSize])
                if (correctSize + count > bytes.size) break
                correctSize += count
            }

            if (correctSize < bytes.size) {
                bytes.copyInto(splitBytes, 0, correctSize, bytes.size)
            }
            splitBytesSize = bytes.size - correctSize

            emit(bytes.subArray(correctSize).decodeToString())
        }
        if (splitBytesSize > 0) error("Flow ended with a malformed UTF-8 character")
    }

fun Flow<String>.splitByLines(): Flow<String> =
    flow {
        var buffer = ""
        val lineEnding = Regex("\\R")

        collect {
            buffer += it
            val match = lineEnding.find(buffer)
            if (match != null) {
                emit(buffer.substring(0, match.range.first))
                buffer = buffer.substring(match.range.last + 1)
            }
        }
        if (buffer.isNotEmpty()) emit(buffer)
    }

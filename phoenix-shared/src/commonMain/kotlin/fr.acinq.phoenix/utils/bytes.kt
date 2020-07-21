package fr.acinq.phoenix.utils


private val emptyByteArray = ByteArray(0)
fun ByteArray.subArray(newSize: Int): ByteArray {
    require(size >= 0)
    if (size == 0) return emptyByteArray
    require(newSize <= size)
    if (newSize == size) return this
    return copyOf(newSize)
}

infix fun ByteArray.concat(append: ByteArray): ByteArray {
    if (this.isEmpty()) return append
    if (append.isEmpty()) return this
    return this + append
}

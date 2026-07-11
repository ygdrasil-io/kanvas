package org.graphiks.kanvas.codec.jpeg

/** A JPEG canonical Huffman table constructed from its DHT code-length counts. */
internal class HuffmanTable(lengths: IntArray, symbols: IntArray) {
    private val symbolsByCode: Map<Int, Int>

    init {
        if (lengths.size != 16) fail()
        val entries = HashMap<Int, Int>(symbols.size)
        var code = 0
        var symbolIndex = 0
        for (length in 1..16) {
            val count = lengths[length - 1]
            if (count < 0 || code + count > (1 shl length)) fail()
            for (unused in 0 until count) {
                if (symbolIndex >= symbols.size) fail()
                entries[(length shl 16) or code] = symbols[symbolIndex++]
                code++
            }
            code = code shl 1
        }
        if (symbolIndex != symbols.size) fail()
        symbolsByCode = entries
    }

    fun decode(reader: EntropyBitReader): Int {
        var code = 0
        for (length in 1..16) {
            code = (code shl 1) or reader.readBit()
            symbolsByCode[(length shl 16) or code]?.let { return it }
        }
        fail()
    }
}

/** Reads entropy-coded JPEG bits, unstuffing 0xFF00 and consuming RST markers explicitly. */
internal class EntropyBitReader(private val bytes: ByteArray) {
    private var offset = 0
    private var current = 0
    private var remaining = 0

    fun readBit(): Int {
        if (remaining == 0) {
            if (offset >= bytes.size) fail()
            current = bytes[offset++].toInt() and 0xFF
            if (current == 0xFF) {
                if (offset >= bytes.size || bytes[offset] != 0x00.toByte()) fail()
                offset++
            }
            remaining = 8
        }
        remaining--
        return (current ushr remaining) and 1
    }

    fun readBits(count: Int): Int {
        var value = 0
        for (unused in 0 until count) value = (value shl 1) or readBit()
        return value
    }

    fun consumeRestart(expected: Int) {
        remaining = 0
        if (offset >= bytes.size || bytes[offset] != 0xFF.toByte()) fail()
        while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) offset++
        if (offset >= bytes.size || (bytes[offset++].toInt() and 0xFF) != 0xD0 + expected) fail()
    }
}

internal fun receiveAndExtend(reader: EntropyBitReader, size: Int): Int {
    if (size == 0) return 0
    val value = reader.readBits(size)
    val threshold = 1 shl (size - 1)
    return if (value < threshold) value - ((1 shl size) - 1) else value
}

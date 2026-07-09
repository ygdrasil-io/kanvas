package org.graphiks.kanvas.color.icc

/** A four-byte ICC signature stored in big-endian byte order. */
@JvmInline
public value class IccSignature(public val value: Int) {
    override fun toString(): String = buildString(4) {
        append((value ushr 24).toChar())
        append((value ushr 16 and 0xff).toChar())
        append((value ushr 8 and 0xff).toChar())
        append((value and 0xff).toChar())
    }

    public companion object {
        public val ACSP: IccSignature = IccSignature(0x61637370)
        public val INPUT_CLASS: IccSignature = IccSignature(0x73636e72)
        public val DISPLAY_CLASS: IccSignature = IccSignature(0x6d6e7472)
        public val OUTPUT_CLASS: IccSignature = IccSignature(0x70727472)

        public val RGB: IccSignature = IccSignature(0x52474220)
        public val GRAY: IccSignature = IccSignature(0x47524159)
        public val XYZ: IccSignature = IccSignature(0x58595a20)

        public val XYZ_TYPE: IccSignature = XYZ
        public val CURVE_TYPE: IccSignature = IccSignature(0x63757276)
        public val PARAMETRIC_CURVE_TYPE: IccSignature = IccSignature(0x70617261)

        public val R_XYZ: IccSignature = IccSignature(0x7258595a)
        public val G_XYZ: IccSignature = IccSignature(0x6758595a)
        public val B_XYZ: IccSignature = IccSignature(0x6258595a)
        public val R_TRC: IccSignature = IccSignature(0x72545243)
        public val G_TRC: IccSignature = IccSignature(0x67545243)
        public val B_TRC: IccSignature = IccSignature(0x62545243)
        public val K_TRC: IccSignature = IccSignature(0x6b545243)
        public val DESCRIPTION: IccSignature = IccSignature(0x64657363)
    }
}

internal class IccBigEndianReader(
    private val bytes: ByteArray,
    private val limit: Int,
) {
    fun hasRange(offset: Long, size: Long): Boolean =
        offset >= 0L && size >= 0L && offset <= limit.toLong() - size

    fun u16(offset: Int): Int {
        check(hasRange(offset.toLong(), 2L))
        return ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)
    }

    fun u32(offset: Int): Long {
        check(hasRange(offset.toLong(), 4L))
        return ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
    }

    fun signature(offset: Int): IccSignature = IccSignature(u32(offset).toInt())

    fun s15Fixed16(offset: Int): Float = u32(offset).toInt() / 65536f
}

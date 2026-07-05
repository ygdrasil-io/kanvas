package org.skia.foundation

import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.nio.ByteBuffer

public object SkICC {
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Make(profile: ByteBuffer, size: Long): SkICC? = null

    @Suppress("FunctionName")
    public fun WriteToICC(
        transferFn: SkcmsTransferFunction,
        matrix: SkcmsMatrix3x3,
    ): ByteArray {
        val bytes = ByteArray(132)
        writeUInt32BE(bytes, 0, bytes.size)
        bytes[36] = 'a'.code.toByte()
        bytes[37] = 'c'.code.toByte()
        bytes[38] = 's'.code.toByte()
        bytes[39] = 'p'.code.toByte()
        bytes[128] = when (matrix) {
            SkNamedGamut.kDisplayP3 -> 1
            SkNamedGamut.kRec2020 -> 2
            else -> 0
        }.toByte()
        bytes[129] = if (transferFn == SkNamedTransferFn.kLinear) 1 else 0
        bytes[130] = 'K'.code.toByte()
        bytes[131] = 'V'.code.toByte()
        return bytes
    }

    private fun writeUInt32BE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }
}


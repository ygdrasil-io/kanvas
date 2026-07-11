package org.graphiks.kanvas.codec.jpeg

import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorType
import kotlin.math.roundToInt

class JpegProgressiveDecodeTest {

    @Test
    fun `decodes three component progressive DC and AC scans`() {
        val codec = JpegCodec.Decoder.make(progressiveColorJpeg())
        assertNotNull(codec)

        val (bitmap, result) = codec!!.getImage()

        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(yCbCrToArgb(160, 96, 192), bitmap!!.getPixel(0, 0))
        assertEquals(yCbCrToArgb(160, 96, 192), bitmap.getPixel(15, 7))
    }

    @Test
    fun `applies multicomponent DC and AC refinement including EOB runs`() {
        val initial = progressiveSamples(progressiveColorJpeg(dcApproxLow = 1, includeAcCoefficients = true))
        val refined = progressiveSamples(progressiveColorJpeg(dcApproxLow = 1, includeAcCoefficients = true, refine = true))

        assertEquals(3, initial.planes.size)
        for (component in initial.planes.indices) {
            assertTrue(
                initial.planes[component].indices.any { initial.planes[component][it] != refined.planes[component][it] },
                "component=$component",
            )
        }
    }

    @Test
    fun `decodes three component progressive scans across restart intervals`() {
        val codec = JpegCodec.Decoder.make(progressiveColorJpeg(restartInterval = 1))
        assertNotNull(codec)

        val (bitmap, result) = codec!!.getImage()

        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(yCbCrToArgb(160, 96, 192), bitmap!!.getPixel(0, 0))
        assertEquals(yCbCrToArgb(160, 96, 192), bitmap.getPixel(15, 7))
    }

    @Test
    fun `reports stable diagnostics for duplicate incomplete and illegal progressive scans`() {
        assertProgressiveDiagnostic(ProgressiveFault.DUPLICATE_DC, "jpeg.progressive.scan.duplicate")
        assertProgressiveDiagnostic(ProgressiveFault.REFINEMENT_BEFORE_INITIAL, "jpeg.progressive.scan.refinement-order")
        assertProgressiveDiagnostic(ProgressiveFault.AC_BEFORE_DC, "jpeg.progressive.scan.order")
        assertProgressiveDiagnostic(ProgressiveFault.MISSING_COMPONENT_DC, "jpeg.progressive.scan.incomplete")
    }

    private fun assertProgressiveDiagnostic(fault: ProgressiveFault, expected: String) {
        val document = JpegDocument.open(progressiveColorJpeg(fault = fault)).document!!
        val result = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))

        assertEquals(expected, result.diagnostic?.code, fault.name)
    }

    private fun progressiveSamples(data: ByteArray): DecodedJpegSamples {
        val document = JpegDocument.open(data).document!!
        return decodeProgressiveDct(parseJpeg(data, document.metadata)!!)
    }

    private fun progressiveColorJpeg(
        dcApproxLow: Int = 0,
        includeAcCoefficients: Boolean = false,
        refine: Boolean = false,
        restartInterval: Int = 0,
        fault: ProgressiveFault? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(8) }
        }
        out.writeSegment(0xC2) {
            write(8)
            writeU16BE(8)
            writeU16BE(16)
            write(3)
            for (id in 1..3) {
                write(id)
                write(0x11)
                write(0)
            }
        }
        out.writeSegment(0xC4) {
            write(0x00)
            repeat(3) { write(0) }
            write(12)
            repeat(12) { write(0) }
            for (symbol in 0..11) write(symbol)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(0)
            write(4)
            repeat(14) { write(0) }
            write(0)
            write(1)
            write(0x10)
            write(0x11)
        }
        if (restartInterval > 0) out.writeSegment(0xDD) { writeU16BE(restartInterval) }

        when (fault) {
            ProgressiveFault.REFINEMENT_BEFORE_INITIAL -> {
                writeDcScan(out, listOf(1, 2, 3), successiveApprox = 0x10, bits = listOf("111", "111"), restartInterval)
            }
            else -> {
                if (fault == ProgressiveFault.AC_BEFORE_DC) {
                    writeAcScan(out, 1, successiveApprox = 0, bits = listOf("00", "00"), restartInterval = 0)
                }
                val components = if (fault == ProgressiveFault.MISSING_COMPONENT_DC) listOf(1, 2) else listOf(1, 2, 3)
                writeDcScan(
                    out,
                    components,
                    dcApproxLow,
                    dcBits(components, dcApproxLow, restartInterval),
                    restartInterval,
                )
                if (fault == ProgressiveFault.DUPLICATE_DC) {
                    writeDcScan(
                        out,
                        components,
                        dcApproxLow,
                        dcBits(components, dcApproxLow, restartInterval),
                        restartInterval,
                    )
                }
                if (fault == null) {
                    if (refine) {
                        writeDcScan(out, listOf(1, 2, 3), dcApproxLow shl 4 or (dcApproxLow - 1), listOf("111", "111"), 0)
                    }
                    for (id in 1..3) {
                        writeAcScan(
                            out,
                            id,
                            successiveApprox = if (includeAcCoefficients) 1 else 0,
                            bits = if (includeAcCoefficients) listOf("11100", "11100") else listOf("00", "00"),
                            restartInterval = restartInterval,
                        )
                        if (refine) {
                            writeAcScan(out, id, 0x10, listOf("10011"), restartInterval = 0)
                        }
                    }
                }
            }
        }
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun writeDcScan(
        out: ByteArrayOutputStream,
        components: List<Int>,
        successiveApprox: Int,
        bits: List<String>,
        restartInterval: Int,
    ) {
        out.writeSegment(0xDA) {
            write(components.size)
            for (id in components) {
                write(id)
                write(0)
            }
            write(0)
            write(0)
            write(successiveApprox)
        }
        out.write(entropyMcuBits(bits, restartInterval))
    }

    private fun writeAcScan(
        out: ByteArrayOutputStream,
        component: Int,
        successiveApprox: Int,
        bits: List<String>,
        restartInterval: Int,
    ) {
        out.writeSegment(0xDA) {
            write(1)
            write(component)
            write(0)
            write(1)
            write(63)
            write(successiveApprox)
        }
        out.write(entropyMcuBits(bits, restartInterval))
    }

    private fun dcBits(components: List<Int>, low: Int, restartInterval: Int): List<String> {
        val target = intArrayOf(32, -32, 64)
        val previous = IntArray(3)
        return List(2) { mcu ->
            buildString {
                for (component in components) {
                    val value = target[component - 1] shr low
                    val difference = value - previous[component - 1]
                    previous[component - 1] = value
                    append(dcBits(difference))
                }
            }.also {
                if (restartInterval > 0 && mcu + 1 < 2 && (mcu + 1) % restartInterval == 0) previous.fill(0)
            }
        }
    }

    private fun dcBits(value: Int): String {
        val category = if (value == 0) 0 else 32 - Integer.numberOfLeadingZeros(kotlin.math.abs(value))
        val amplitude = if (value >= 0) value else value + (1 shl category) - 1
        return category.toString(2).padStart(4, '0') + amplitude.toString(2).padStart(category, '0')
    }

    private fun entropyMcuBits(mcuBits: List<String>, restartInterval: Int): ByteArray {
        if (restartInterval == 0) return entropyBits(mcuBits.joinToString(separator = ""))
        return ByteArrayOutputStream().apply {
            for (start in mcuBits.indices step restartInterval) {
                val end = (start + restartInterval).coerceAtMost(mcuBits.size)
                write(entropyBits(mcuBits.subList(start, end).joinToString(separator = "")))
                if (end < mcuBits.size) writeMarker(0xD0 + (start / restartInterval and 7))
            }
        }.toByteArray()
    }

    private enum class ProgressiveFault {
        DUPLICATE_DC,
        REFINEMENT_BEFORE_INITIAL,
        AC_BEFORE_DC,
        MISSING_COMPONENT_DC,
    }

    private fun yCbCrToArgb(y: Int, cb: Int, cr: Int): Int {
        val r = (y + 1.402 * (cr - 128)).roundToInt().coerceIn(0, 255)
        val g = (y - 0.344136 * (cb - 128) - 0.714136 * (cr - 128)).roundToInt().coerceIn(0, 255)
        val b = (y + 1.772 * (cb - 128)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteArrayOutputStream.writeSegment(marker: Int, writePayload: ByteArrayOutputStream.() -> Unit) {
        val payload = ByteArrayOutputStream().apply(writePayload).toByteArray()
        writeMarker(marker)
        writeU16BE(payload.size + 2)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU16BE(value: Int) {
        write(value ushr 8)
        write(value and 0xFF)
    }

    private fun entropyBits(bits: String): ByteArray {
        val out = ByteArray((bits.length + 7) / 8)
        for (index in bits.indices) {
            if (bits[index] == '1') out[index / 8] = (out[index / 8].toInt() or (1 shl (7 - (index and 7)))).toByte()
        }
        for (index in bits.length until out.size * 8) {
            out[index / 8] = (out[index / 8].toInt() or (1 shl (7 - (index and 7)))).toByte()
        }
        return ByteArrayOutputStream().apply {
            for (value in out) {
                write(value.toInt() and 0xFF)
                if (value == 0xFF.toByte()) write(0)
            }
        }.toByteArray()
    }
}

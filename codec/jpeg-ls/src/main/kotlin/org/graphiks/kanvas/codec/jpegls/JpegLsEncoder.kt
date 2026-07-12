package org.graphiks.kanvas.codec.jpegls

import java.io.ByteArrayOutputStream
import org.skia.foundation.SkBitmap

/** Interleave modes exposed for the RGB JPEG-LS encoder. */
public enum class JpegLsRgbInterleaveMode {
    /** One R, one G, and one B scan in component order. */
    NonInterleaved,

    /** One component line after another; the established encoder default. */
    Line,

    /** One RGB triplet after another. */
    Sample,
}

/**
 * Static JPEG-LS encoder for verified 8-bit LOCO-I data. It emits grayscale
 * scans with `ILV=0`, or RGB scans with `ILV=0` (non-interleaved), `ILV=1`
 * (line), or `ILV=2` (sample) interleave, preserving opaque source channels
 * without a colour transform.
 */
public object JpegLsEncoder {
    public data class Options(
        val nearLossless: Int = 0,
        val rgbInterleaveMode: JpegLsRgbInterleaveMode = JpegLsRgbInterleaveMode.Line,
    ) {
        init {
            require(nearLossless in 0..JpegLsCodingParameters.maximumNearLossless) {
                "nearLossless must be in 0..${JpegLsCodingParameters.maximumNearLossless} for 8-bit JPEG-LS"
            }
        }
    }

    public fun encode(source: SkBitmap, options: Options = Options()): ByteArray? {
        if (source.width !in 1..0xFFFF || source.height !in 1..0xFFFF) return null
        var grayscale = true
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getPixel(x, y)
                val a = argb ushr 24 and 0xFF
                val r = argb ushr 16 and 0xFF
                val g = argb ushr 8 and 0xFF
                val b = argb and 0xFF
                if (a != 0xFF) return null
                if (r != g || g != b) grayscale = false
            }
        }
        val components = if (grayscale) listOf(JpegLsComponent(1)) else listOf(
            JpegLsComponent(1),
            JpegLsComponent(2),
            JpegLsComponent(3),
        )
        if (
            components.size == 3 &&
            options.rgbInterleaveMode != JpegLsRgbInterleaveMode.Line &&
            options.nearLossless != 0
        ) {
            return null
        }
        val samples = IntArray(source.width * source.height * components.size)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getPixel(x, y)
                val base = (y * source.width + x) * components.size
                samples[base] = argb ushr 16 and 0xFF
                if (components.size == 3) {
                    samples[base + 1] = argb ushr 8 and 0xFF
                    samples[base + 2] = argb and 0xFF
                }
            }
        }
        val frame = JpegLsFrame(
            source.width,
            source.height,
            components,
            options.nearLossless,
            JpegLsCodingParameters.defaults(options.nearLossless),
            if (components.size == 1) 0 else options.rgbInterleaveMode.markerValue,
        )
        val entropyScans = JpegLsEntropy.encodeScans(source.width, source.height, samples, frame)
        return ByteArrayOutputStream(entropyScans.sumOf(ByteArray::size) + 32 + components.size * 10).also { output ->
            output.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            output.write(byteArrayOf(0xFF.toByte(), 0xF7.toByte()))
            writeU16(output, 8 + components.size * 3)
            output.write(0x08)
            writeU16(output, source.height)
            writeU16(output, source.width)
            output.write(components.size)
            components.forEach { component ->
                output.write(component.id)
                output.write(0x11)
                output.write(0x00)
            }
            if (frame.interleaveMode == 0 && components.size == 3) {
                components.forEachIndexed { index, component ->
                    writeSos(output, listOf(component), frame)
                    output.write(entropyScans[index])
                }
            } else {
                writeSos(output, components, frame)
                output.write(entropyScans.single())
            }
            output.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        }.toByteArray()
    }

    private fun writeSos(output: ByteArrayOutputStream, components: List<JpegLsComponent>, frame: JpegLsFrame) {
        output.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte()))
        writeU16(output, 6 + components.size * 2)
        output.write(components.size)
        components.forEach { component ->
            output.write(component.id)
            output.write(0x00)
        }
        output.write(frame.nearLossless)
        output.write(frame.interleaveMode)
        output.write(0x00)
    }

    private fun writeU16(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 8)
        output.write(value and 0xFF)
    }

    private val JpegLsRgbInterleaveMode.markerValue: Int
        get() = when (this) {
            JpegLsRgbInterleaveMode.NonInterleaved -> 0
            JpegLsRgbInterleaveMode.Line -> 1
            JpegLsRgbInterleaveMode.Sample -> 2
        }
}

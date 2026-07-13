package org.graphiks.kanvas.codec.jpeg

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorType
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureNanoTime

/**
 * Reproducible measurement evidence, deliberately without pass/fail timing
 * thresholds.  The fixtures cover a photograph, a graphic, CMYK, a 12-bit
 * arithmetic stream and a generated large image; the report is a trend input,
 * not a benchmark gate tied to one host.
 */
class JpegPerformanceEvidenceTest {

    @Test
    fun `writes warm performance evidence for documented JPEG fixture classes`() {
        val cases = listOf(
            PerformanceCase("photo", resource("/codec-real-images/jpeg/dog.jpg")),
            PerformanceCase("graphic", resource("/codec-real-images/jpeg/color_wheel_420.jpg")),
            PerformanceCase("cmyk", cmykFixture()),
            PerformanceCase("12-bit", resource("/jpeg-arithmetic/gray-12bit-sequential-sof9.jpg")),
            PerformanceCase(
                "large-generated",
                requireNotNull(
                    JpegEncoder.encode(
                        JpegConformanceFixtures.color(512, 384),
                        JpegEncoder.Options(
                            quality = 90,
                            process = JpegEncodeProcess.SequentialHuffman,
                            colorModel = JpegEncodeColorModel.YCbCr,
                            sampling = JpegSampling.S420,
                        ),
                    ),
                ),
            ),
        )

        val evidence = cases.map { case -> measure(case) }
        val destination = Path.of(requireNotNull(System.getProperty("kanvas.jpeg.performance.report")))
        Files.createDirectories(requireNotNull(destination.parent))
        Files.writeString(destination, report(evidence))
        assertTrue(Files.size(destination) > 0)
    }

    private fun measure(case: PerformanceCase): PerformanceEvidence {
        val document = requireNotNull(JpegDocument.open(case.bytes).document) { case.name }
        fun decodeOnce(): Pair<Int, Int> {
            val decoded = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))
            assertNull(decoded.diagnostic, case.name)
            val bitmap = requireNotNull(decoded.bitmap) { case.name }
            return bitmap.width to bitmap.height
        }

        decodeOnce() // warm-up; no wall-clock expectation is asserted.
        val durations = LongArray(MEASURED_ITERATIONS) {
            measureNanoTime { decodeOnce() }
        }.also { it.sort() }
        val (width, height) = decodeOnce()
        return PerformanceEvidence(
            name = case.name,
            encodedBytes = case.bytes.size,
            width = width,
            height = height,
            peakDecodedBytesEstimate = width.toLong() * height * RGBA_8888_BYTES_PER_PIXEL,
            medianNanoseconds = durations[durations.size / 2],
        )
    }

    private fun report(evidence: List<PerformanceEvidence>): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"warmupIterations\": 1,")
        appendLine("  \"measuredIterations\": $MEASURED_ITERATIONS,")
        appendLine("  \"cases\": [")
        evidence.forEachIndexed { index, item ->
            append("    {\"name\":\"").append(item.name).append("\"")
            append(",\"encodedBytes\":").append(item.encodedBytes)
            append(",\"width\":").append(item.width)
            append(",\"height\":").append(item.height)
            append(",\"peakDecodedBytesEstimate\":").append(item.peakDecodedBytesEstimate)
            append(",\"medianNanoseconds\":").append(item.medianNanoseconds).append('}')
            appendLine(if (index == evidence.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    /** Small deterministic Adobe CMYK SOF0 stream: four zero-valued DCT blocks. */
    private fun cmykFixture(): ByteArray = ByteArrayOutputStream().apply {
        marker(0xD8)
        segment(0xEE, byteArrayOf(
            'A'.code.toByte(), 'd'.code.toByte(), 'o'.code.toByte(), 'b'.code.toByte(), 'e'.code.toByte(),
            0, 100, 0, 0, 0, 0, 0,
        ))
        segment(0xDB, byteArrayOf(0) + ByteArray(64) { 1 })
        segment(0xC0, byteArrayOf(
            8, 0, 8, 0, 8, 4,
            1, 0x11, 0,
            2, 0x11, 0,
            3, 0x11, 0,
            4, 0x11, 0,
        ))
        segment(0xC4, byteArrayOf(0) + byteArrayOf(1) + ByteArray(15) + byteArrayOf(0))
        segment(0xC4, byteArrayOf(0x10) + byteArrayOf(1) + ByteArray(15) + byteArrayOf(0))
        segment(0xDA, byteArrayOf(4, 1, 0, 2, 0, 3, 0, 4, 0, 0, 63, 0))
        write(0) // DC+EOB for four components; padded with one bits by the entropy reader.
        marker(0xD9)
    }.toByteArray()

    private fun ByteArrayOutputStream.marker(value: Int) {
        write(0xFF)
        write(value)
    }

    private fun ByteArrayOutputStream.segment(marker: Int, payload: ByteArray) {
        marker(marker)
        write((payload.size + 2) ushr 8)
        write((payload.size + 2) and 0xFF)
        write(payload)
    }

    private fun resource(path: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(path)) { path }.readBytes()

    private data class PerformanceCase(val name: String, val bytes: ByteArray)
    private data class PerformanceEvidence(
        val name: String,
        val encodedBytes: Int,
        val width: Int,
        val height: Int,
        val peakDecodedBytesEstimate: Long,
        val medianNanoseconds: Long,
    )

    private companion object {
        const val MEASURED_ITERATIONS: Int = 10
        const val RGBA_8888_BYTES_PER_PIXEL: Int = 4
    }
}

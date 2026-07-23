package org.graphiks.kanvas.codec.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Optional interoperability check.  It never discovers an executable through
 * PATH: callers must supply a directory containing the desired `djpeg` via
 * `-PjpegOracleDir=/absolute/path`.  Normal CI remains Kotlin-only.
 */
class JpegOracleTest {

    @Test
    fun `explicit djpeg oracle reads generated arithmetic sequential and progressive streams`() {
        val configuredDirectory = System.getProperty("kanvas.jpeg.oracle.dir").orEmpty()
        assumeTrue(configuredDirectory.isNotBlank(), "Set -PjpegOracleDir=/absolute/path/to/djpeg-directory to enable")
        val oracle = Path.of(configuredDirectory).resolve("djpeg")
        assumeTrue(Files.isExecutable(oracle), "djpeg is not executable: $oracle")

        val source = JpegConformanceFixtures.grayscale(16, 12)
        val cases = listOf(
            "SOF9" to JpegEncoder.Options(
                process = JpegEncodeProcess.SequentialArithmetic,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                restartInterval = 1,
            ),
            "SOF10" to JpegEncoder.Options(
                process = JpegEncodeProcess.ProgressiveArithmetic,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                restartInterval = 1,
                progressiveScans = listOf(
                    JpegProgressiveScan(listOf(1), 0, 0),
                    JpegProgressiveScan(listOf(1), 1, 63),
                ),
            ),
        )

        for ((name, options) in cases) {
            val encoded = requireNotNull(JpegEncoder.encode(source, options)) { name }
            val output = ProcessBuilder(oracle.toString(), "-pnm")
                .redirectErrorStream(true)
                .start()
                .also { process -> process.outputStream.use { it.write(encoded) } }
                .let { process ->
                    val bytes = process.inputStream.use { it.readBytes() }
                    assertEquals(0, process.waitFor(), "$name oracle output=${bytes.decodeToString()}")
                    bytes
                }

            val header = output.decodeToString(endIndex = output.indexOfFirst { it == '\n'.code.toByte() }.coerceAtLeast(0))
            assertTrue(header == "P5" || header == "P6", "$name PNM header=$header")
            assertTrue(output.size > 16, "$name PNM output must contain pixel data")
        }
    }
}

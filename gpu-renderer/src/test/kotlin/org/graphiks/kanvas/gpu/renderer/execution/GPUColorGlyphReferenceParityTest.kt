package org.graphiks.kanvas.gpu.renderer.execution

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureBuilder
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUColorGlyphReferenceParityTest {

    data class LayerInput(
        val atlasRect: AtlasRegion,
        val premultipliedRgba: FloatArray,
    )

    @Test
    fun `GPU color glyph output matches CPU pixel-exact reference within tolerance`() {
        val atlasResult = GlyphAtlasTextureBuilder().build("AB", fontSize = 48f)
        assumeTrue(
            atlasResult is GlyphAtlasTextureResult.Built,
            "glyph atlas unavailable in current environment: ${(atlasResult as? GlyphAtlasTextureResult.Refused)?.reason}",
        )
        val built = assertIs<GlyphAtlasTextureResult.Built>(atlasResult)
        val atlas = built.atlas
        val placements = atlas.placements
        assertTrue(placements.size >= 2, "need at least 2 glyph placements for 'A' and 'B'")

        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val layers = listOf(
            LayerInput(
                atlasRect = placements[0].region,
                premultipliedRgba = floatArrayOf(1f, 0f, 0f, 1f),
            ),
            LayerInput(
                atlasRect = placements[1].region,
                premultipliedRgba = floatArrayOf(0f, 0f, 1f, 1f),
            ),
        )

        val cpuReference = computeCpuColorGlyphReference(
            atlasA8Bytes = atlas.a8Bytes,
            atlasWidth = atlas.width,
            atlasHeight = atlas.height,
            layers = layers,
            targetW = TARGET_W,
            targetH = TARGET_H,
        )

        val uniformBytes = buildUniformBytes(TARGET_W, TARGET_H, layers, atlas.width, atlas.height)
        val vertexData = floatArrayOf(
            0f, 0f, 0f, 0f,
            64f, 0f, 1f, 0f,
            64f, 64f, 1f, 1f,
            0f, 64f, 0f, 1f,
        )
        val indexData = intArrayOf(0, 1, 2, 0, 2, 3)

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = TARGET_W, height = TARGET_H, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawColorGlyphPass(
                        atlasRgba = atlas.a8Bytes,
                        atlasWidth = atlas.width,
                        atlasHeight = atlas.height,
                        atlasFormat = "r8unorm",
                        vertexData = vertexData,
                        indexData = indexData,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = uniformBytes,
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = TARGET_W,
                                scissorHeight = TARGET_H,
                            ),
                        ),
                    )
                }

                val gpuRgba = target.readRgba()

                val comparison = comparePixels(gpuRgba, cpuReference, tolerance = TOLERANCE)
                val adapterName = session.adapterInfo?.summary ?: "unknown-adapter"

                val reportsDir = resolveReportsDir()
                reportsDir.mkdirs()

                writePng(cpuReference, TARGET_W, TARGET_H, File(reportsDir, "reference.png"))

                if (comparison.similarity < 1.0) {
                    val diffPixels = generateDiffPixels(gpuRgba, cpuReference, TARGET_W, TARGET_H)
                    writePng(diffPixels, TARGET_W, TARGET_H, File(reportsDir, "diff.png"))
                }

                val parityText = buildString {
                    appendLine("COLRv0 color glyph parity report")
                    appendLine("adapter=$adapterName")
                    appendLine("similarity=${"%.4f".format(comparison.similarity)}")
                    appendLine("matchingPixels=${comparison.matchingPixels}/${comparison.totalPixels}")
                    appendLine("tolerancePerChannel=$TOLERANCE")
                    appendLine("maxDeltaR=${comparison.maxR}")
                    appendLine("maxDeltaG=${comparison.maxG}")
                    appendLine("maxDeltaB=${comparison.maxB}")
                    appendLine("maxDeltaA=${comparison.maxA}")
                    appendLine("meanDeltaR=${"%.4f".format(comparison.meanR)}")
                    appendLine("meanDeltaG=${"%.4f".format(comparison.meanG)}")
                    appendLine("meanDeltaB=${"%.4f".format(comparison.meanB)}")
                    appendLine("meanDeltaA=${"%.4f".format(comparison.meanA)}")
                    appendLine("targetSize=${TARGET_W}x${TARGET_H}")
                    appendLine("atlasVersion=AB-48px-${atlas.width}x${atlas.height}")
                }
                File(reportsDir, "parity.txt").writeText(parityText)

                println("CPU vs GPU parity: similarity=${"%.2f".format(comparison.similarity * 100)}% " +
                    "matching=${comparison.matchingPixels}/${comparison.totalPixels} " +
                    "maxDelta=(R=${comparison.maxR},G=${comparison.maxG},B=${comparison.maxB},A=${comparison.maxA}) " +
                    "meanDelta=(R=${"%.2f".format(comparison.meanR)},G=${"%.2f".format(comparison.meanG)}," +
                    "B=${"%.2f".format(comparison.meanB)},A=${"%.2f".format(comparison.meanA)}) " +
                    "adapter=$adapterName")

                assertTrue(
                    comparison.similarity >= REQUIRED_SIMILARITY,
                    "GPU-CPU parity below threshold: similarity=${"%.4f".format(comparison.similarity)} " +
                        "(required >= $REQUIRED_SIMILARITY), " +
                        "matching=${comparison.matchingPixels}/${comparison.totalPixels}, " +
                        "maxDelta=(R=${comparison.maxR},G=${comparison.maxG},B=${comparison.maxB},A=${comparison.maxA})"
                )
            }
        }
    }

    private fun resolveReportsDir(): File {
        val explicit = System.getProperty("kanvas.parity.reportsDir")
        if (explicit != null) return File(explicit)

        val cwd = File(System.getProperty("user.dir"))
        val fromHere = File(cwd, "../reports/gpu-renderer-scenes/offscreen/colr-v0-color-glyph")
        if (fromHere.parentFile.exists() || fromHere.exists()) return fromHere

        val fromCwd = File(cwd, "reports/gpu-renderer-scenes/offscreen/colr-v0-color-glyph")
        if (fromCwd.parentFile.exists() || fromCwd.exists()) return fromCwd

        return fromHere
    }

    private fun buildUniformBytes(
        targetW: Int,
        targetH: Int,
        layers: List<LayerInput>,
        atlasW: Int,
        atlasH: Int,
    ): ByteArray {
        val buf = ByteBuffer.allocate(528).order(ByteOrder.LITTLE_ENDIAN)

        buf.putFloat(targetW.toFloat())
        buf.putFloat(targetH.toFloat())
        buf.putInt(layers.size)
        buf.putInt(0)

        for (i in 0 until 16) {
            if (i < layers.size) {
                val c = layers[i].premultipliedRgba
                buf.putFloat(c[0])
                buf.putFloat(c[1])
                buf.putFloat(c[2])
                buf.putFloat(c[3])
            } else {
                buf.putFloat(0f)
                buf.putFloat(0f)
                buf.putFloat(0f)
                buf.putFloat(0f)
            }
        }

        val aw = atlasW.toFloat()
        val ah = atlasH.toFloat()
        for (i in 0 until 16) {
            if (i < layers.size) {
                val r = layers[i].atlasRect
                buf.putFloat(r.x / aw)
                buf.putFloat(r.y / ah)
                buf.putFloat(r.width / aw)
                buf.putFloat(r.height / ah)
            } else {
                buf.putFloat(0f)
                buf.putFloat(0f)
                buf.putFloat(0f)
                buf.putFloat(0f)
            }
        }

        return buf.array()
    }

    companion object {
        private const val TARGET_W = 64
        private const val TARGET_H = 64
        private const val BYTES_PER_PIXEL = 4
        private const val TOLERANCE = 2
        private const val REQUIRED_SIMILARITY = 0.95

        fun computeCpuColorGlyphReference(
            atlasA8Bytes: ByteArray,
            atlasWidth: Int,
            atlasHeight: Int,
            layers: List<LayerInput>,
            targetW: Int,
            targetH: Int,
        ): ByteArray {
            val pixels = ByteArray(targetW * targetH * BYTES_PER_PIXEL)

            for (y in 0 until targetH) {
                for (x in 0 until targetW) {
                    val quadUvX = (x + 0.5f) / targetW
                    val quadUvY = (y + 0.5f) / targetH

                    var accumR = 0f
                    var accumG = 0f
                    var accumB = 0f
                    var accumA = 0f

                    for (layer in layers) {
                        val r = layer.atlasRect
                        val texelXF = r.x + quadUvX * r.width
                        val texelYF = r.y + quadUvY * r.height

                        val tx = texelXF.toInt().coerceIn(0, atlasWidth - 1)
                        val ty = texelYF.toInt().coerceIn(0, atlasHeight - 1)

                        val coverage = ((atlasA8Bytes[ty * atlasWidth + tx].toInt() and 0xFF) / 255.0f)
                            .coerceIn(0f, 1f)

                        val srcR = layer.premultipliedRgba[0] * coverage
                        val srcG = layer.premultipliedRgba[1] * coverage
                        val srcB = layer.premultipliedRgba[2] * coverage
                        val srcA = layer.premultipliedRgba[3] * coverage

                        val invSrcA = 1f - srcA
                        accumR = srcR + accumR * invSrcA
                        accumG = srcG + accumG * invSrcA
                        accumB = srcB + accumB * invSrcA
                        accumA = srcA + accumA * invSrcA
                    }

                    val finalA = accumA + (1f - accumA)

                    val i = (y * targetW + x) * BYTES_PER_PIXEL
                    pixels[i] = (accumR * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
                    pixels[i + 1] = (accumG * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
                    pixels[i + 2] = (accumB * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
                    pixels[i + 3] = (finalA * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
                }
            }

            return pixels
        }

        fun comparePixels(
            a: ByteArray,
            b: ByteArray,
            tolerance: Int = 0,
        ): BitmapComparison {
            require(a.size == b.size) { "Buffer sizes differ: ${a.size} vs ${b.size}" }
            val total = a.size / BYTES_PER_PIXEL
            var matching = 0
            var maxR = 0; var maxG = 0; var maxB = 0; var maxA = 0
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var sumA = 0L
            var mismatchCount = 0

            for (i in 0 until total) {
                val base = i * BYTES_PER_PIXEL
                val ra = a[base].toInt() and 0xFF
                val rb = b[base].toInt() and 0xFF
                val ga = a[base + 1].toInt() and 0xFF
                val gb = b[base + 1].toInt() and 0xFF
                val ba = a[base + 2].toInt() and 0xFF
                val bb = b[base + 2].toInt() and 0xFF
                val aa = a[base + 3].toInt() and 0xFF
                val ab = b[base + 3].toInt() and 0xFF

                val dr = kotlin.math.abs(ra - rb)
                val dg = kotlin.math.abs(ga - gb)
                val db = kotlin.math.abs(ba - bb)
                val da = kotlin.math.abs(aa - ab)

                if (maxOf(dr, dg, db, da) <= tolerance) {
                    matching++
                } else {
                    maxR = maxOf(maxR, dr)
                    maxG = maxOf(maxG, dg)
                    maxB = maxOf(maxB, db)
                    maxA = maxOf(maxA, da)
                    sumR += dr; sumG += dg; sumB += db; sumA += da
                    mismatchCount++
                }
            }

            val similarity = if (total > 0) matching.toDouble() / total else 1.0
            return BitmapComparison(
                similarity = similarity,
                totalPixels = total,
                matchingPixels = matching,
                maxR = maxR, maxG = maxG, maxB = maxB, maxA = maxA,
                meanR = if (mismatchCount > 0) sumR.toDouble() / mismatchCount else 0.0,
                meanG = if (mismatchCount > 0) sumG.toDouble() / mismatchCount else 0.0,
                meanB = if (mismatchCount > 0) sumB.toDouble() / mismatchCount else 0.0,
                meanA = if (mismatchCount > 0) sumA.toDouble() / mismatchCount else 0.0,
            )
        }

        data class BitmapComparison(
            val similarity: Double,
            val totalPixels: Int,
            val matchingPixels: Int,
            val maxR: Int, val maxG: Int, val maxB: Int, val maxA: Int,
            val meanR: Double, val meanG: Double, val meanB: Double, val meanA: Double,
        )

        fun generateDiffPixels(
            actual: ByteArray,
            reference: ByteArray,
            width: Int,
            height: Int,
        ): ByteArray {
            val diff = ByteArray(actual.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = (y * width + x) * BYTES_PER_PIXEL
                    var dr = (actual[i].toInt() and 0xFF) - (reference[i].toInt() and 0xFF)
                    var dg = (actual[i + 1].toInt() and 0xFF) - (reference[i + 1].toInt() and 0xFF)
                    var db = (actual[i + 2].toInt() and 0xFF) - (reference[i + 2].toInt() and 0xFF)

                    val amp = 4
                    dr = (dr * amp).coerceIn(-128, 127)
                    dg = (dg * amp).coerceIn(-128, 127)
                    db = (db * amp).coerceIn(-128, 127)

                    diff[i] = (128 + dr).toByte()
                    diff[i + 1] = (128 + dg).toByte()
                    diff[i + 2] = (128 + db).toByte()
                    diff[i + 3] = 255.toByte()
                }
            }
            return diff
        }

        fun writePng(pixels: ByteArray, width: Int, height: Int, file: File) {
            require(pixels.size == width * height * BYTES_PER_PIXEL) {
                "RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${pixels.size}"
            }

            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

            val ihdrData = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(width)
                putInt(height)
                put(8.toByte())
                put(6.toByte())
                put(0.toByte())
                put(0.toByte())
                put(0.toByte())
            }
            out.write(makePngChunk("IHDR", ihdrData.array()))

            val rawStride = 1 + width * BYTES_PER_PIXEL
            val raw = ByteArray(height * rawStride)
            for (y in 0 until height) {
                val rowStart = y * rawStride
                raw[rowStart] = 0.toByte()
                System.arraycopy(pixels, y * width * BYTES_PER_PIXEL, raw, rowStart + 1, width * BYTES_PER_PIXEL)
            }

            val deflater = Deflater()
            deflater.setInput(raw)
            deflater.finish()
            val compressed = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                compressed.write(buf, 0, n)
            }
            deflater.end()
            out.write(makePngChunk("IDAT", compressed.toByteArray()))

            out.write(makePngChunk("IEND", ByteArray(0)))

            FileOutputStream(file).use { fos -> fos.write(out.toByteArray()) }
        }

        private fun makePngChunk(type: String, data: ByteArray): ByteArray {
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            val len = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
            val crcInput = typeBytes + data
            val crc = CRC32().apply { update(crcInput) }
            val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt()).array()
            return len + typeBytes + data + crcBytes
        }
    }
}

package org.graphiks.kanvas.surface.gpu

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.PreparedTextOutline
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Task 10 evidence for the shipped Liberation Sans font only.  This is not a
 * general text or font-support claim: each row is a bounded Latin glyph run
 * derived from one of the blocked GM families.
 */
class GPUDeliveredFontGlyphRunEvidenceTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `shipped Liberation Sans glyph runs match the complete CPU A8 oracle and headless WebGPU`() {
        val typeface = shippedLiberationTypeface()
        val rows = listOf(
            EvidenceRow(
                id = "gradtext.glyph-run.linear-clamp.v1",
                text = "Skia",
                size = 24f,
                paint = Paint.fill(ColorARGB.White).copy(
                    shader = Shader.LinearGradient(
                        start = Point2F32(0f, 0f),
                        end = Point2F32(64f, 0f),
                        stops = listOf(
                            GradientStop(0f, ColorARGB.Red),
                            GradientStop(1f, ColorARGB.Blue),
                        ),
                        tileMode = TileMode.CLAMP,
                    ),
                ),
                transform = Matrix3x3F32.Identity,
                requiresOpaqueCpuOracle = false,
                expectedDiff = PixelDiffSnapshot(
                    cpuSha256 = "9666e292ad51f7754d2fb0ccebb6c557988dc36f0edb89a32d0c2897e106c354",
                    gpuSha256 = "3e82d101c0a894f7a06da71e24a397841903ddca083053aff58e50ffc21db24a",
                    differentBytes = 9,
                    differentPixels = 9,
                    maxChannelDelta = 1,
                    totalAbsoluteDelta = 9,
                ),
            ),
            EvidenceRow(
                id = "text-scale-skew.glyph-run.affine.v1",
                text = "Skia",
                size = 24f,
                paint = Paint.fill(ColorARGB.White),
                transform = Matrix3x3F32(sx = 1.15f, kx = 0.18f, tx = 0f, ky = 0f, sy = 1f, ty = 0f),
                requiresOpaqueCpuOracle = true,
                expectedDiff = PixelDiffSnapshot(
                    cpuSha256 = "6ea03c02678f30606226c83d7f263404d82e31c49c856935775c916b977cb0ae",
                    gpuSha256 = "6ea03c02678f30606226c83d7f263404d82e31c49c856935775c916b977cb0ae",
                    differentBytes = 0,
                    differentPixels = 0,
                    maxChannelDelta = 0,
                    totalAbsoluteDelta = 0,
                ),
            ),
            EvidenceRow(
                id = "fontscaler.glyph-run.size-18.v1",
                text = "Aa",
                size = 18f,
                paint = Paint.fill(ColorARGB.White),
                transform = Matrix3x3F32.Identity,
                requiresOpaqueCpuOracle = true,
                expectedDiff = PixelDiffSnapshot(
                    cpuSha256 = "16cf0ecf39f03cb3d0f8eaf22387d350cfd26f649c2fd7b1deefd2075e9de7d6",
                    gpuSha256 = "16cf0ecf39f03cb3d0f8eaf22387d350cfd26f649c2fd7b1deefd2075e9de7d6",
                    differentBytes = 0,
                    differentPixels = 0,
                    maxChannelDelta = 0,
                    totalAbsoluteDelta = 0,
                ),
            ),
        )

        rows.forEach { row ->
            val blob = Font(typeface, size = row.size).toTextBlob(row.text, 0f, 0f)
            val glyphRun = blob.glyphRuns.single()
            assertEquals(row.text.codePointCount(0, row.text.length), glyphRun.glyphs.size, row.id)
            assertTrue(glyphRun.glyphs.all { glyph -> glyph.toInt() != 0 }, "${row.id}: missing glyph mapping")
            assertTrue(
                glyphRun.glyphs.all { glyph -> typeface.getGlyphPath(glyph.toInt(), row.size) != null },
                "${row.id}: CPU getGlyphPath oracle is unavailable",
            )
            assertTrue(
                glyphRun.glyphs.all { glyph ->
                    typeface.preparedTextOutline(glyph.toInt(), row.size) is PreparedTextOutline.ProvenNonEmpty
                },
                "${row.id}: preparedTextOutline cannot supply the GPU TextA8 route",
            )

            val result = execute(
                operations = listOf(
                    DisplayOp.DrawText(
                        blob = blob,
                        x = 8f,
                        y = 28f,
                        paint = row.paint,
                        transform = row.transform,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                width = 96,
                height = 48,
            )
            val payloads = preparedTextPayloads(
                operations = listOf(
                    DisplayOp.DrawText(
                        blob = blob,
                        x = 8f,
                        y = 28f,
                        paint = row.paint,
                        transform = row.transform,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                identity = row.id,
            )

            assertEquals(0, result.evidence.textCounters.pathStrokeDraws, row.id)
            assertEquals(glyphRun.glyphs.size, result.evidence.textCounters.a8Instances, row.id)
            assertEquals(1L, result.evidence.submits, row.id)
            assertEquals(1L, result.evidence.readbackCopies, row.id)
            assertEquals(glyphRun.glyphs.size, payloads.sumOf { payload -> payload.instances.size }, row.id)
            assertEquals(96 * 48 * 4, result.rgba.size, "${row.id}: unexpected WebGPU readback size")
            assertTrue(result.rgba.any { byte -> byte.toInt() != 0 }, "${row.id}: blank GPU readback")
            val cpuOracle = renderCpuA8Oracle(row, payloads, width = 96, height = 48)
            val diff = PixelDiff.compare(cpuOracle, result.rgba)
            assertEquals(row.expectedDiff, diff.snapshot(), "${row.id}: ${diff.describe()}")
            println("task10.text.diff id=${row.id} ${diff.describe()}")
            if (row.requiresOpaqueCpuOracle) {
                val expectedOpaque = GPUPreparedTextPixelOracle.a8SourceOver(
                    material = GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
                    paintAlpha = 1f,
                    coverage = 255,
                ).bytes()
                assertTrue(
                    result.rgba.asList().windowed(expectedOpaque.size, expectedOpaque.size)
                        .any { pixel -> pixel.toByteArray().contentEquals(expectedOpaque) },
                    "${row.id}: GPU output contains no CPU-oracle opaque A8 texel",
                )
            }
            println(
                "task10.text id=${row.id} glyphs=${glyphRun.glyphs.size} " +
                    "a8Instances=${result.evidence.textCounters.a8Instances} " +
                    "submits=${result.evidence.submits} readbacks=${result.evidence.readbackCopies}",
            )
        }
    }

    private fun execute(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
    ): GPUPreparedSurfaceExecutionResult.Succeeded {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return assertIs(
            GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
                GPUPreparedSurfaceExecutionRequest(
                    candidate = GPUPreparedSurfaceEligibility.Candidate(
                        operations = operations,
                        config = RenderConfig.DEFAULT,
                        color = color,
                    ),
                    width = width,
                    height = height,
                    output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
                ),
            ),
        )
    }

    private fun preparedTextPayloads(
        operations: List<DisplayOp>,
        identity: String,
    ): List<GPUDrawSemanticPayload.TextA8> {
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT),
        )
        val buildResult = GPUPreparedSurfaceFrameBuilder.build(
            GPUPreparedSurfaceFrameBuildRequest(
                candidate = candidate,
                targetFacts = GPUTargetFacts(96, 48, "rgba8unorm-srgb"),
                targetBounds = GPUPixelBounds(0, 0, 96, 48),
                capabilities = evidenceCapabilities(),
                deviceGeneration = GPUDeviceGenerationID(1),
                target = GPUFrameTargetRef("$identity-target"),
                recordingId = GPURecordingID("$identity-recording"),
                frameId = GPUFrameID(1),
                readbackRequestId = GPUReadbackRequestID("$identity-readback"),
            ),
        )
        val built = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(buildResult, buildResult.toString())
        return built.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull { packet -> packet.semanticPayload as? GPUDrawSemanticPayload.TextA8 }
    }

    private fun evidenceCapabilities(): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts,
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:delivered-font-evidence",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
        )
    }

    /**
     * Test-only CPU A8 interpreter for the sealed TextA8 payload. It samples the
     * immutable CPU-prepared atlas with the payload's device quads and compares
     * every encoded RGBA8 byte after the admitted paint is composed source-over
     * in linear premultiplied space and encoded as sRGB.
     */
    private fun renderCpuA8Oracle(
        row: EvidenceRow,
        payloads: List<GPUDrawSemanticPayload.TextA8>,
        width: Int,
        height: Int,
    ): ByteArray {
        val linearPremul = FloatArray(width * height * 4)
        payloads.forEach { payload ->
            val atlas = payload.atlas.tightBytesForUpload()
            payload.instances.forEach { instance ->
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val sample = sampleA8(
                            instance = instance,
                            atlas = atlas,
                            atlasWidth = payload.atlas.width,
                            atlasHeight = payload.atlas.height,
                            atlasRowBytes = payload.atlas.rowBytes,
                            deviceX = x + 0.5f,
                            deviceY = y + 0.5f,
                        ) ?: continue
                        if (sample.coverage == 0f) continue
                        val material = materialAt(
                            row = row,
                            payload = payload,
                            deviceX = x + 0.5f,
                            deviceY = y + 0.5f,
                        )
                        val alpha = material.alpha * payload.material.paintAlpha * sample.coverage
                        val pixel = (y * width + x) * 4
                        val inverseAlpha = 1f - alpha
                        linearPremul[pixel] =
                            material.red * payload.material.paintAlpha * sample.coverage +
                                linearPremul[pixel] * inverseAlpha
                        linearPremul[pixel + 1] =
                            material.green * payload.material.paintAlpha * sample.coverage +
                                linearPremul[pixel + 1] * inverseAlpha
                        linearPremul[pixel + 2] =
                            material.blue * payload.material.paintAlpha * sample.coverage +
                                linearPremul[pixel + 2] * inverseAlpha
                        linearPremul[pixel + 3] = alpha + linearPremul[pixel + 3] * inverseAlpha
                    }
                }
            }
        }
        return ByteArray(linearPremul.size) { index ->
            val encoded = if (index % 4 == 3) linearPremul[index] else encodeSrgb(linearPremul[index])
            (encoded.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }
    }

    private fun sampleA8(
        instance: org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance,
        atlas: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasRowBytes: Int,
        deviceX: Float,
        deviceY: Float,
    ): A8Sample? {
        val q = instance.deviceQuad
        val originX = q[0]
        val originY = q[1]
        val axisUX = q[2] - originX
        val axisUY = q[3] - originY
        val axisVX = q[6] - originX
        val axisVY = q[7] - originY
        val determinant = axisUX * axisVY - axisUY * axisVX
        if (abs(determinant) <= 1.0e-6f) return null
        val relativeX = deviceX - originX
        val relativeY = deviceY - originY
        val u = (relativeX * axisVY - relativeY * axisVX) / determinant
        val v = (axisUX * relativeY - axisUY * relativeX) / determinant
        if (u < 0f || u >= 1f || v < 0f || v >= 1f) return null
        val uv = instance.uvRect
        val textureX = floor((uv.left + u * (uv.right - uv.left)) * atlasWidth).toInt()
            .coerceIn(0, atlasWidth - 1)
        val textureY = floor((uv.top + v * (uv.bottom - uv.top)) * atlasHeight).toInt()
            .coerceIn(0, atlasHeight - 1)
        return A8Sample((atlas[textureY * atlasRowBytes + textureX].toInt() and 0xff) / 255f)
    }

    private fun materialAt(
        row: EvidenceRow,
        payload: GPUDrawSemanticPayload.TextA8,
        deviceX: Float,
        deviceY: Float,
    ): LinearPremul = when (val shader = row.paint.shader) {
        null -> row.paint.color.toLinearPremul()
        is Shader.LinearGradient -> {
            assertEquals(TileMode.CLAMP, shader.tileMode, "Task 10 gradient oracle is CLAMP-only")
            assertEquals(2, shader.stops.size, "Task 10 gradient oracle is two-stop-only")
            val inverse = payload.deviceToLocal
            val localX = inverse.m00 * deviceX + inverse.m01 * deviceY + inverse.m02
            val localY = inverse.m10 * deviceX + inverse.m11 * deviceY + inverse.m12
            payload.sampleLinearGradientMaterial(localX, localY)
        }
        else -> error("Task 10 CPU oracle admits only solid or CLAMP linear-gradient paint")
    }

    private fun GPUDrawSemanticPayload.TextA8.sampleLinearGradientMaterial(
        localX: Float,
        localY: Float,
    ): LinearPremul {
        val uniform = material.uniformBytes
        val startX = uniform.floatAt(0)
        val startY = uniform.floatAt(4)
        val endX = uniform.floatAt(8)
        val endY = uniform.floatAt(12)
        val matrixLocalX = uniform.floatAt(16) * localX + uniform.floatAt(20) * localY + uniform.floatAt(24)
        val matrixLocalY = uniform.floatAt(32) * localX + uniform.floatAt(36) * localY + uniform.floatAt(40)
        val axisX = endX - startX
        val axisY = endY - startY
        val axisLengthSquared = axisX * axisX + axisY * axisY
        require(axisLengthSquared > 0f)
        val t = (((matrixLocalX - startX) * axisX + (matrixLocalY - startY) * axisY) / axisLengthSquared)
            .coerceIn(0f, 1f)
        val stopCount = uniform.intAt(48)
        require(stopCount == 2) { "Task 10 gradient oracle expects two packed stops, got $stopCount" }
        return LinearPremul(
            red = uniform.floatAt(80) * (1f - t) + uniform.floatAt(112) * t,
            green = uniform.floatAt(84) * (1f - t) + uniform.floatAt(116) * t,
            blue = uniform.floatAt(88) * (1f - t) + uniform.floatAt(120) * t,
            alpha = uniform.floatAt(92) * (1f - t) + uniform.floatAt(124) * t,
        )
    }

    private fun List<Int>.floatAt(offset: Int): Float = Float.fromBits(intAt(offset))

    private fun List<Int>.intAt(offset: Int): Int {
        require(offset >= 0 && offset + 4 <= size)
        return this[offset] or (this[offset + 1] shl 8) or (this[offset + 2] shl 16) or (this[offset + 3] shl 24)
    }

    private fun ColorARGB.toLinearPremul(): LinearPremul = LinearPremul(
        red = decodeSrgb(redNormalized) * alphaNormalized,
        green = decodeSrgb(greenNormalized) * alphaNormalized,
        blue = decodeSrgb(blueNormalized) * alphaNormalized,
        alpha = alphaNormalized,
    )

    private fun decodeSrgb(encoded: Float): Float =
        if (encoded <= 0.04045f) encoded / 12.92f
        else (((encoded + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()

    private fun encodeSrgb(linear: Float): Float =
        if (linear <= 0.0031308f) linear * 12.92f
        else (1.055 * linear.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()

    private fun shippedLiberationTypeface(): FontTypeface = FontTypeface(
        requireNotNull(
            javaClass.classLoader.getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf"),
        ).use { stream -> stream.readBytes() },
        "LiberationSans-Regular.ttf",
    )

    private data class EvidenceRow(
        val id: String,
        val text: String,
        val size: Float,
        val paint: Paint,
        val transform: Matrix3x3F32,
        val requiresOpaqueCpuOracle: Boolean,
        val expectedDiff: PixelDiffSnapshot,
    )

    private data class A8Sample(val coverage: Float)

    private data class LinearPremul(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    )

    private data class PixelDiffSnapshot(
        val cpuSha256: String,
        val gpuSha256: String,
        val differentBytes: Int,
        val differentPixels: Int,
        val maxChannelDelta: Int,
        val totalAbsoluteDelta: Long,
    )

    private data class PixelDiff(
        val expectedSha256: String,
        val actualSha256: String,
        val differentBytes: Int,
        val differentPixels: Int,
        val maxChannelDelta: Int,
        val totalAbsoluteDelta: Long,
        val firstDifferences: List<String>,
    ) {
        fun describe(): String =
            "cpuSha256=$expectedSha256 gpuSha256=$actualSha256 " +
                "differentBytes=$differentBytes differentPixels=$differentPixels " +
                "maxChannelDelta=$maxChannelDelta totalAbsoluteDelta=$totalAbsoluteDelta " +
                "firstDifferences=" +
                firstDifferences.joinToString(";")

        fun snapshot(): PixelDiffSnapshot = PixelDiffSnapshot(
            cpuSha256 = expectedSha256,
            gpuSha256 = actualSha256,
            differentBytes = differentBytes,
            differentPixels = differentPixels,
            maxChannelDelta = maxChannelDelta,
            totalAbsoluteDelta = totalAbsoluteDelta,
        )

        companion object {
            fun compare(expected: ByteArray, actual: ByteArray): PixelDiff {
                assertEquals(expected.size, actual.size, "CPU/GPU byte sizes differ")
                var differentBytes = 0
                var maxChannelDelta = 0
                var totalAbsoluteDelta = 0L
                val firstDifferences = ArrayList<String>()
                expected.indices.forEach { index ->
                    val delta = abs((expected[index].toInt() and 0xff) - (actual[index].toInt() and 0xff))
                    if (delta != 0) {
                        differentBytes++
                        if (firstDifferences.size < 8) {
                            firstDifferences += "index=$index cpu=${expected[index].toInt() and 0xff} gpu=${actual[index].toInt() and 0xff}"
                        }
                    }
                    maxChannelDelta = maxOf(maxChannelDelta, delta)
                    totalAbsoluteDelta += delta
                }
                val recomputedDifferentPixels = expected.indices
                    .filter { index -> index % 4 == 0 }
                    .count { index ->
                        expected[index] != actual[index] ||
                            expected[index + 1] != actual[index + 1] ||
                            expected[index + 2] != actual[index + 2] ||
                            expected[index + 3] != actual[index + 3]
                    }
                return PixelDiff(
                    expectedSha256 = expected.sha256(),
                    actualSha256 = actual.sha256(),
                    differentBytes = differentBytes,
                    differentPixels = recomputedDifferentPixels,
                    maxChannelDelta = maxChannelDelta,
                    totalAbsoluteDelta = totalAbsoluteDelta,
                    firstDifferences = firstDifferences,
                )
            }

            private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
                .digest(this)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }
}

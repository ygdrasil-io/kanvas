package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.materials.BlendPremulColor
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendBindingTopology
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendCoverageKind
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendCpuOracle
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibrary
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.wgsl.parser.parseWgslResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUBackendRuntimeNativeWgslValidationTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `native pipelines for every shader formula id and coverage topology match the independent RGBA oracle`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")

        val source = premulBytes(64, 32, 96, 128)
        val lcdCoverage = floatArrayOf(38f / 255f, 140f / 255f, 230f / 255f)
        val destinations = listOf(
            "transparent" to premulBytes(0, 0, 0, 0),
            "translucent" to premulBytes(20, 80, 40, 160),
            "opaque" to premulBytes(51, 153, 89, 255),
        )
        val validationCases = buildList {
            destinations.forEach { (label, destination) ->
                add(BlendValidationCase("$label-full", GPUBlendCoverageKind.Full, destination))
                listOf(0f, .25f, .5f, 1f).forEach { coverage ->
                    add(BlendValidationCase("$label-scalar-$coverage", GPUBlendCoverageKind.Scalar, destination, coverage))
                }
                add(BlendValidationCase("$label-lcd", GPUBlendCoverageKind.LCD, destination, lcdCoverage = lcdCoverage))
            }
        }
        session!!.use { runtime ->
            GPUBlendMode.entries.filterNot { it == GPUBlendMode.DST }.forEach { blendMode ->
                validationCases.forEach { validationCase ->
                    val coverageKind = validationCase.coverageKind
                    val destination = validationCase.destination
                    runtime.createOffscreenTarget(GPUOffscreenTargetRequest(1, 1)).use { target ->
                        val srcTexture = target.createOffscreenTexture(
                            GPUBackendOffscreenTexture("blend-validation-src-$blendMode-${validationCase.label}", 1, 1, "rgba8unorm"),
                        )
                        val dstTexture = target.createOffscreenTexture(
                            GPUBackendOffscreenTexture("blend-validation-dst-$blendMode-${validationCase.label}", 1, 1, "rgba8unorm"),
                        )
                        target.encodeOffscreenTexture(srcTexture, source.toClearColor()) {}
                        target.encodeOffscreenTexture(dstTexture, destination.toClearColor()) {}

                        val formula = requireNotNull(
                            GPUBlendFormulaLibrary.formulaFor(blendMode, coverageKind),
                        )
                        val coverageTexture = when (formula.bindingTopology) {
                            GPUBlendBindingTopology.SourceDestination -> null
                            GPUBlendBindingTopology.SourceDestinationCoverage -> target.createOffscreenTexture(
                                GPUBackendOffscreenTexture(
                                    "blend-validation-coverage-$blendMode-${validationCase.label}",
                                    1,
                                    1,
                                    "rgba8unorm",
                                ),
                            ).also { texture ->
                                target.encodeOffscreenTexture(texture, validationCase.coverageColor()) {}
                            }
                        }
                        val module = GPUBlendFormulaLibrary.assembleValidationModule(formula)
                        val draw = GPUBackendRawUniformDraw(ByteArray(16), 0, 0, 1, 1)
                        target.encode(GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                            when (formula.bindingTopology) {
                                GPUBlendBindingTopology.SourceDestination -> drawTwoTexturePass(
                                    module,
                                    "rgba8unorm",
                                    srcTexture,
                                    dstTexture,
                                    listOf(draw),
                                )
                                GPUBlendBindingTopology.SourceDestinationCoverage -> drawThreeTexturePass(
                                    module,
                                    "rgba8unorm",
                                    srcTexture,
                                    dstTexture,
                                    requireNotNull(coverageTexture),
                                    listOf(draw),
                                )
                            }
                        }

                        val expected = when (coverageKind) {
                            GPUBlendCoverageKind.Full -> GPUBlendCpuOracle.blendAtFullCoverage(
                                blendMode,
                                source,
                                destination,
                            )
                            GPUBlendCoverageKind.Scalar -> GPUBlendCpuOracle.blend(
                                blendMode,
                                source,
                                destination,
                                validationCase.scalarCoverage,
                            )
                            GPUBlendCoverageKind.LCD -> GPUBlendCpuOracle.blendLcd(
                                blendMode,
                                source,
                                destination,
                                validationCase.lcdCoverage,
                            )
                        }
                        assertRgbaNear(target.readRgba(), expected, blendMode, coverageKind)
                    }
                }
            }
        }
    }

    @Test
    fun `executed A8 text atlas shader exposes source planes and parses through wgsl4k`() {
        val source = nativeTextAtlasA8WgslSource()

        assertContains(source, "sourcePlane: u32")
        assertContains(source, "uniforms.sourcePlane == 1u")
        assertContains(source, "uniforms.sourcePlane == 2u")

        val parsed = parseWgslResult(source)
        assertTrue(
            parsed.isSuccess,
            "wgsl4k rejected the executed A8 text atlas shader: ${parsed.errors.joinToString { it.message }}",
        )
    }

    private fun premulBytes(r: Int, g: Int, b: Int, a: Int): BlendPremulColor =
        BlendPremulColor(r / 255f, g / 255f, b / 255f, a / 255f)

    private data class BlendValidationCase(
        val label: String,
        val coverageKind: GPUBlendCoverageKind,
        val destination: BlendPremulColor,
        val scalarCoverage: Float = 1f,
        val lcdCoverage: FloatArray = floatArrayOf(1f, 1f, 1f),
    ) {
        fun coverageColor(): GPUClearColor = when (coverageKind) {
            GPUBlendCoverageKind.Full -> error("Full coverage has no coverage binding")
            GPUBlendCoverageKind.Scalar -> GPUClearColor(0.0, 0.0, 0.0, scalarCoverage.toDouble())
            GPUBlendCoverageKind.LCD -> GPUClearColor(
                lcdCoverage[0].toDouble(),
                lcdCoverage[1].toDouble(),
                lcdCoverage[2].toDouble(),
                1.0,
            )
        }
    }

    private fun BlendPremulColor.toClearColor(): GPUClearColor = GPUClearColor(
        r.toDouble(),
        g.toDouble(),
        b.toDouble(),
        a.toDouble(),
    )

    private fun assertRgbaNear(
        actual: ByteArray,
        expected: BlendPremulColor,
        blendMode: GPUBlendMode,
        coverageKind: GPUBlendCoverageKind,
    ) {
        val expectedBytes = expected.toArray().map { channel -> (channel.coerceIn(0f, 1f) * 255f).toInt() }
        expectedBytes.indices.forEach { channel ->
            val actualByte = actual[channel].toInt() and 0xff
            assertTrue(
                kotlin.math.abs(actualByte - expectedBytes[channel]) <= 2,
                "$blendMode/$coverageKind channel=$channel actual=$actualByte expected=${expectedBytes[channel]}",
            )
        }
    }
}

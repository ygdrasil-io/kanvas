package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class GPUBlendFormulaLibraryTest {
    @Test
    fun `every canonical shader formula id resolves while Dst has no shader route`() {
        val planner = GPUBlendPlanner()
        val coverages = listOf(GPUCoverageConsumption.FullOrScissor, GPUCoverageConsumption.ScalarCoverage, GPUCoverageConsumption.LCDCoverage)

        GPUBlendMode.entries.forEach { mode ->
            coverages.forEach { coverage ->
                val plan = planner.plan(request(mode, coverage))
                when (plan) {
                    is GPUBlendPlan.ShaderBlendNoDstRead,
                    is GPUBlendPlan.ShaderBlendWithDstRead,
                    -> assertNotNull(GPUBlendFormulaLibrary.resolve(plan), "$mode/$coverage must resolve")
                    else -> assertNull(GPUBlendFormulaLibrary.resolve(plan), "$mode/$coverage must not invent a shader route")
                }
            }
        }

        assertNull(GPUBlendFormulaLibrary.formulaFor(GPUBlendMode.DST, GPUBlendCoverageKind.Full))
    }

    @Test
    fun `all formula ids and coverage topologies are stable and the production registry has no CPU evaluator`() {
        GPUBlendMode.entries.forEach { mode ->
            GPUBlendCoverageKind.entries.forEach { coverageKind ->
                val formula = GPUBlendFormulaLibrary.formulaFor(mode, coverageKind)
                if (mode == GPUBlendMode.DST) {
                    assertNull(formula, "Dst/$coverageKind must stay a no-op route")
                } else {
                    assertNotNull(formula)
                    val expectedId = when (coverageKind) {
                        GPUBlendCoverageKind.LCD -> "lcd.${mode.gpuLabel}@v1"
                        GPUBlendCoverageKind.Full,
                        GPUBlendCoverageKind.Scalar,
                        -> if (mode == GPUBlendMode.PLUS) "plus_exact@v1" else "${mode.gpuLabel}@v1"
                    }
                    assertEquals(expectedId, formula.formulaId)
                    assertEquals(coverageKind, formula.coverageKind)
                    assertEquals(mode, formula.mode)
                    assertEquals(
                        if (coverageKind == GPUBlendCoverageKind.Full) {
                            GPUBlendBindingTopology.SourceDestination
                        } else {
                            GPUBlendBindingTopology.SourceDestinationCoverage
                        },
                        formula.bindingTopology,
                    )
                }
            }
        }

        assertTrue(
            GPUBlendFormulaLibrary::class.java.declaredMethods.none { method ->
                method.name.contains("evaluate", ignoreCase = true) ||
                    method.returnType.simpleName.contains("Color", ignoreCase = true)
            },
            "production registry must expose WGSL descriptions, never CPU expected-color logic",
        )
    }

    @Test
    fun `all 29 premultiplied modes obey scalar coverage interpolation for destination alpha classes`() {
        val source = BlendPremulColor(.18f, .07f, .31f, .55f)
        val destinations = listOf(
            BlendPremulColor(0f, 0f, 0f, 0f),
            BlendPremulColor(.08f, .24f, .16f, .4f),
            BlendPremulColor(.2f, .6f, .35f, 1f),
        )
        val coverages = listOf(0f, .25f, .5f, 1f)

        GPUBlendMode.entries.forEach { mode ->
            destinations.forEach { destination ->
                val full = GPUBlendCpuOracle.blendAtFullCoverage(mode, source, destination)
                coverages.forEach { coverage ->
                    val actual = GPUBlendCpuOracle.blend(mode, source, destination, coverage)
                    val expected = BlendPremulColor(
                        destination.r + coverage * (full.r - destination.r),
                        destination.g + coverage * (full.g - destination.g),
                        destination.b + coverage * (full.b - destination.b),
                        destination.a + coverage * (full.a - destination.a),
                    )
                    assertColorNear(expected, actual, "$mode coverage=$coverage destination=$destination")
                }
            }
        }
    }

    @Test
    fun `LCD coverage stays vector-valued and alpha is the maximum interpolated component alpha`() {
        val source = BlendPremulColor(.18f, .07f, .31f, .55f)
        val destination = BlendPremulColor(.08f, .24f, .16f, .4f)
        val coverage = floatArrayOf(.15f, .55f, .9f)

        GPUBlendMode.entries.forEach { mode ->
            val full = GPUBlendCpuOracle.blendAtFullCoverage(mode, source, destination)
            val actual = GPUBlendCpuOracle.blendLcd(mode, source, destination, coverage)
            assertEquals(destination.r + coverage[0] * (full.r - destination.r), actual.r, 1e-6f, mode.name)
            assertEquals(destination.g + coverage[1] * (full.g - destination.g), actual.g, 1e-6f, mode.name)
            assertEquals(destination.b + coverage[2] * (full.b - destination.b), actual.b, 1e-6f, mode.name)
            assertEquals(
                coverage.maxOf { component -> destination.a + component * (full.a - destination.a) },
                actual.a,
                1e-6f,
                mode.name,
            )
        }

        val lcdPlan = GPUBlendPlanner().plan(request(GPUBlendMode.SRC_OVER, GPUCoverageConsumption.LCDCoverage))
        assertEquals(GPUSourceCoverageEncoding.LCDCoverageInShader, lcdPlan.sourceCoverageEncoding)
        assertTrue(lcdPlan.sourceCoverageEncoding != GPUSourceCoverageEncoding.ScalarCoverageInShader)
    }

    @Test
    fun `golden zero-alpha division saturation and nonseparable boundaries stay finite`() {
        val transparentColor = BlendPremulColor(0f, 0f, 0f, 0f)
        val opaqueBlack = BlendPremulColor(0f, 0f, 0f, 1f)
        val opaqueWhite = BlendPremulColor(1f, 1f, 1f, 1f)
        assertColorNear(opaqueBlack, GPUBlendCpuOracle.blendAtFullCoverage(GPUBlendMode.COLOR_DODGE, opaqueWhite, opaqueBlack), "dodge black")
        assertColorNear(opaqueWhite, GPUBlendCpuOracle.blendAtFullCoverage(GPUBlendMode.COLOR_BURN, opaqueBlack, opaqueWhite), "burn white")
        assertColorNear(opaqueWhite, GPUBlendCpuOracle.blendAtFullCoverage(GPUBlendMode.PLUS, opaqueWhite, opaqueWhite), "plus saturation")
        assertColorNear(opaqueWhite, GPUBlendCpuOracle.blendAtFullCoverage(GPUBlendMode.HUE, transparentColor, opaqueWhite), "zero source alpha")

        listOf(GPUBlendMode.HUE, GPUBlendMode.SATURATION, GPUBlendMode.COLOR, GPUBlendMode.LUMINOSITY).forEach { mode ->
            val result = GPUBlendCpuOracle.blendAtFullCoverage(
                mode,
                BlendPremulColor(.5f, .05f, .2f, .5f),
                BlendPremulColor(.1f, .7f, .3f, 1f),
            )
            assertTrue(result.toArray().all(Float::isFinite), "$mode must stay finite")
        }
    }

    @Test
    fun `every shader formula id and topology assembles a complete wgsl4k-parseable module`() {
        val formulas = GPUBlendMode.entries
            .filterNot { mode -> mode == GPUBlendMode.DST }
            .flatMap { mode ->
                GPUBlendCoverageKind.entries.map { coverageKind ->
                    assertNotNull(GPUBlendFormulaLibrary.formulaFor(mode, coverageKind))
                }
            }

        formulas.forEach { formula ->
            val source = GPUBlendFormulaLibrary.assembleValidationModule(formula)
            val parsed = parseWgslResult(source)
            assertTrue(parsed.isSuccess, "${formula.formulaId}: ${parsed.errors.joinToString { it.message }}")
            val lowered = Lowerer().lower(parsed.translationUnit)
            assertEquals(setOf("vs_main", "fs_main"), lowered.entryPoints.map { it.name }.toSet())
            assertTrue(source.contains("@group(1) @binding(1) var srcTexture"))
            assertTrue(source.contains("@group(1) @binding(3) var dstTexture"))
            when (formula.coverageKind) {
                GPUBlendCoverageKind.Full -> assertTrue(!source.contains("coverageTexture"))
                GPUBlendCoverageKind.Scalar,
                GPUBlendCoverageKind.LCD,
                -> assertTrue(source.contains("coverageTexture"))
            }
        }
    }

    private fun request(mode: GPUBlendMode, coverage: GPUCoverageConsumption) = GPUBlendSpecializationRequest(
        mode = mode,
        coverage = coverage,
        sourceAlpha = GPUSourceAlphaClassification.Translucent,
        target = GPUTargetBlendFacts("rgba8unorm", clampsNormalizedColorWrites = true, premultipliedAlpha = true),
        samplePlan = GPUSamplePlan.SingleSampleFrame,
    )

    private fun assertColorNear(expected: BlendPremulColor, actual: BlendPremulColor, label: String) {
        expected.toArray().indices.forEach { channel ->
            assertTrue(abs(expected[channel] - actual[channel]) <= 1e-6f, "$label channel=$channel expected=$expected actual=$actual")
        }
    }
}

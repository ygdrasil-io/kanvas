package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendCoverageKind
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibrary
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

/** Verifies generic WGSL render module assembly, ABI dumps, reflection fixtures, and rejection diagnostics. */
class WGSLModuleAbiTest {
    @Test
    fun `blend formula ABI validator rejects a reflected sample type mismatch`() {
        val coverageKind = GPUBlendCoverageKind.Scalar
        val formula = requireNotNull(
            GPUBlendFormulaLibrary.formulaFor(GPUBlendMode.SRC_OVER, coverageKind),
        )
        val parsed = parseWgslResult(GPUBlendFormulaLibrary.assembleValidationModule(formula))
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })
        val reflected = Lowerer().lower(parsed.translationUnit).reflectWgslModule(formula.formulaId)
        val declared = GPUBlendFormulaModuleAbi.declaredFor(coverageKind)
        val mismatched = declared.copy(
            bindings = declared.bindings.map { binding ->
                if (binding.group == 1 && binding.binding == 1) {
                    binding.copy(sampleType = "sint")
                } else {
                    binding
                }
            },
        )

        val result = validateWgslModuleAbi(mismatched, reflected)

        val rejected = assertIs<WgslModuleAbiValidationResult.Mismatch>(result)
        assertContains(rejected.diagnostics.joinToString(), "sampleType")
        assertContains(rejected.diagnostics.joinToString(), "group=1,binding=1")
    }

    @Test
    fun `blend formula modules match complete declared full scalar and LCD ABI`() {
        GPUBlendCoverageKind.entries.forEach { coverageKind ->
            val formula = requireNotNull(
                GPUBlendFormulaLibrary.formulaFor(GPUBlendMode.SRC_OVER, coverageKind),
            )
            val parsed = parseWgslResult(GPUBlendFormulaLibrary.assembleValidationModule(formula))
            assertTrue(parsed.isSuccess, "${formula.formulaId}: ${parsed.errors.joinToString { it.message }}")
            val reflected = Lowerer().lower(parsed.translationUnit).reflectWgslModule(formula.formulaId)
            val result = validateWgslModuleAbi(GPUBlendFormulaModuleAbi.declaredFor(coverageKind), reflected)

            assertIs<WgslModuleAbiValidationResult.Match>(
                result,
                (result as? WgslModuleAbiValidationResult.Mismatch)?.diagnostics?.joinToString("\n"),
            )
        }
    }

    /** The first ABI fixture exposes group zero draw data and group one solid material data deterministically. */
    @Test
    fun `solid render module fixture exposes group zero and group one ABI`() {
        val result = WGSLModuleAssembler.assembleRenderModule(solidModuleInput())
        val module = (result as? WGSLModuleAssemblyResult.Accepted)?.module
            ?: fail("Expected solid module fixture to assemble")

        assertEquals("solid-rect-render", module.moduleLabel)
        assertEquals("vs_main", module.vertexEntryPoint)
        assertEquals("fs_main", module.fragmentEntryPoint)
        assertTrue(module.parserState.parserBacked, "Solid module must be parser-backed when the WGSL parser is available")
        assertTrue(module.parserState.toolName == "wgsl4k", "Parser tool must be wgsl4k")

        assertEquals(
            listOf(
                "0:0:uniform-buffer:frame",
                "0:1:uniform-buffer:render-step",
                "0:2:uniform-buffer:intrinsic-draw",
                "1:0:uniform-buffer:material-solid",
            ),
            module.bindings.map { "${it.group}:${it.binding}:${it.resourceKind}:${it.layoutRole}" },
        )

        val solidLayout = module.uniformLayouts.single { it.layoutHash == "layout:solid-material-block:v1" }
        assertEquals(16L, solidLayout.sizeBytes)
        assertEquals(16, solidLayout.alignment)
        assertEquals(
            listOf(WGSLUniformFieldLayout(name = "color", type = "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16)),
            solidLayout.fieldLayouts,
        )

        val solidPacking = module.packingPlans.single { it.layoutHash == solidLayout.layoutHash }
        assertEquals(listOf("color"), solidPacking.fieldOrder)
        assertEquals(mapOf("color" to 0L), solidPacking.offsets)
        assertContains(module.source, "struct SolidMaterialBlock")
        assertContains(module.source, "color: vec4<f32>")
        assertContains(module.source, "@group(0) @binding(0) var<uniform> frame")
        assertContains(module.source, "@group(1) @binding(0) var<uniform> solidMaterial")
        assertContains(module.source, "return solidMaterial.color")
        assertContains(module.abiDump().lines(), "moduleHash=${module.moduleHash.value}")

        val dump = module.abiDump()
        assertContains(dump, "module=solid-rect-render")
        assertContains(dump, "entryPoints=vertex:vs_main,fragment:fs_main")
        assertContains(dump, "parser=parser-backed:wgsl4k")
        assertContains(dump, "binding=0/0 frame uniform-buffer min=64")
        assertContains(dump, "binding=1/0 material-solid uniform-buffer min=16")
        assertContains(dump, "uniform=layout:solid-material-block:v1")
        assertContains(dump, "color:vec4<f32>@0/16")
        assertContains(dump, "packing=pack:solid-material-block:v1 layout=layout:solid-material-block:v1 fields=color@0 padding=0")
        assertContains(dump, "reflection=wgsl4k-parsed")
    }

    /** Module assembly rejects a fragment set that does not expose the requested complete entry points. */
    @Test
    fun `module assembly rejects missing entry point`() {
        val result = WGSLModuleAssembler.assembleRenderModule(
            solidModuleInput().copy(fragmentEntryPoint = "missing_fs"),
        )

        val rejected = result as? WGSLModuleAssemblyResult.Rejected
            ?: fail("Expected missing entry point to reject module assembly")

        assertContains(rejected.diagnostics.map { it.code }, "unsupported.wgsl.missing_entry_point")
    }

    /** Module assembly rejects duplicate group and binding pairs before source emission. */
    @Test
    fun `module assembly rejects binding collision`() {
        val input = solidModuleInput()
        val result = WGSLModuleAssembler.assembleRenderModule(
            input.copy(bindings = input.bindings + input.bindings.first().copy(layoutRole = "frame-alias")),
        )

        val rejected = result as? WGSLModuleAssemblyResult.Rejected
            ?: fail("Expected duplicate binding to reject module assembly")

        assertContains(rejected.diagnostics.map { it.code }, "unsupported.wgsl.binding_collision")
        assertContains(rejected.diagnostics.single { it.code == "unsupported.wgsl.binding_collision" }.fieldOrBinding.orEmpty(), "group=0,binding=0")
    }

    /** Module assembly rejects Kotlin packing plans whose offsets disagree with declared WGSL layout. */
    @Test
    fun `module assembly rejects Kotlin WGSL layout mismatch`() {
        val input = solidModuleInput()
        val badPacking = input.packingPlans.map { plan ->
            if (plan.planHash == "pack:solid-material-block:v1") {
                plan.copy(offsets = mapOf("color" to 4L))
            } else {
                plan
            }
        }

        val result = WGSLModuleAssembler.assembleRenderModule(input.copy(packingPlans = badPacking))
        val rejected = result as? WGSLModuleAssemblyResult.Rejected
            ?: fail("Expected packing mismatch to reject module assembly")

        assertContains(rejected.diagnostics.map { it.code }, "unsupported.wgsl.uniform_alignment_mismatch")
        assertContains(rejected.diagnostics.single { it.code == "unsupported.wgsl.uniform_alignment_mismatch" }.fieldOrBinding.orEmpty(), "color")
    }

    /** Module assembly rejects features or limits that the current facade fixture cannot represent. */
    @Test
    fun `module assembly rejects unsupported facade feature`() {
        val input = solidModuleInput()
        val result = WGSLModuleAssembler.assembleRenderModule(
            input.copy(
                fragments = input.fragments.map { fragment ->
                    fragment.copy(requiredFeatures = fragment.requiredFeatures + "shader-f16")
                },
                capabilities = WGSLFacadeCapabilities(supportedFeatures = emptySet()),
            ),
        )

        val rejected = result as? WGSLModuleAssemblyResult.Rejected
            ?: fail("Expected unsupported feature to reject module assembly")

        assertContains(rejected.diagnostics.map { it.code }, "unsupported.wgsl.feature_unrepresented_by_parser")
        assertContains(rejected.diagnostics.single().fieldOrBinding.orEmpty(), "shader-f16")
    }

    /** Module assembly rejects binding slots that exceed facade fixture limits. */
    @Test
    fun `module assembly rejects unsupported facade binding limit`() {
        val input = solidModuleInput()
        val result = WGSLModuleAssembler.assembleRenderModule(
            input.copy(
                bindings = input.bindings + uniformBinding(group = 4, binding = 0, role = "too-many-groups", size = 16),
                capabilities = WGSLFacadeCapabilities(supportedFeatures = emptySet(), maxBindGroup = 3, maxBinding = 15),
            ),
        )

        val rejected = result as? WGSLModuleAssemblyResult.Rejected
            ?: fail("Expected facade bind-group limit to reject module assembly")

        assertContains(rejected.diagnostics.map { it.code }, "unsupported.wgsl.facade_limit")
        assertContains(rejected.diagnostics.single { it.code == "unsupported.wgsl.facade_limit" }.fieldOrBinding.orEmpty(), "group=4,binding=0")
    }

    /** Creates the generic first-slice module assembly input used by WGSL ABI tests. */
    private fun solidModuleInput(): WGSLModuleAssemblyInput {
        val bindings = listOf(
            uniformBinding(group = 0, binding = 0, role = "frame", size = 64),
            uniformBinding(group = 0, binding = 1, role = "render-step", size = 64),
            uniformBinding(group = 0, binding = 2, role = "intrinsic-draw", size = 64),
            uniformBinding(group = 1, binding = 0, role = "material-solid", size = 16),
        )
        val layouts = listOf(
            uniformLayout(
                hash = "layout:frame-block:v1",
                fields = listOf(
                    WGSLUniformFieldLayout("frameIndex", "u32", offset = 0L, sizeBytes = 4L, alignment = 4),
                    WGSLUniformFieldLayout("targetSize", "vec2<f32>", offset = 16L, sizeBytes = 8L, alignment = 8),
                ),
                size = 64,
            ),
            uniformLayout(
                hash = "layout:render-step-block:v1",
                fields = listOf(
                    WGSLUniformFieldLayout("coverageMode", "u32", offset = 0L, sizeBytes = 4L, alignment = 4),
                    WGSLUniformFieldLayout("blendMode", "u32", offset = 4L, sizeBytes = 4L, alignment = 4),
                ),
                size = 64,
            ),
            uniformLayout(
                hash = "layout:intrinsic-draw-block:v1",
                fields = listOf(
                    WGSLUniformFieldLayout("localToDevice", "mat3x3<f32>", offset = 0L, sizeBytes = 48L, alignment = 16),
                ),
                size = 64,
            ),
            uniformLayout(
                hash = "layout:solid-material-block:v1",
                fields = listOf(
                    WGSLUniformFieldLayout("color", "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16),
                ),
                size = 16,
            ),
        )

        return WGSLModuleAssemblyInput(
            moduleLabel = "solid-rect-render",
            moduleSalt = "kanvas-gpu-renderer:solid-material:v1",
            vertexEntryPoint = "vs_main",
            fragmentEntryPoint = "fs_main",
            fragments = listOf(
                WGSLFragment(
                    fragmentId = "fixture.solid.material",
                    stage = "fragment",
                    sourceHash = "fragment:solid-material:v1",
                    entryPoints = listOf("vs_main", "fs_main"),
                    bindingLayouts = emptyList(),
                    uniformLayouts = emptyList(),
                    storageLayouts = emptyList(),
                    requiredFeatures = emptyList(),
                    diagnosticLabel = "solid material fixture",
                ),
            ),
            bindings = bindings,
            uniformLayouts = layouts,
            packingPlans = listOf(
                packingPlan("pack:frame-block:v1", "layout:frame-block:v1", mapOf("frameIndex" to 0L, "targetSize" to 16L), padding = 44),
                packingPlan("pack:render-step-block:v1", "layout:render-step-block:v1", mapOf("coverageMode" to 0L, "blendMode" to 4L), padding = 56),
                packingPlan("pack:intrinsic-draw-block:v1", "layout:intrinsic-draw-block:v1", mapOf("localToDevice" to 0L), padding = 16),
                packingPlan("pack:solid-material-block:v1", "layout:solid-material-block:v1", mapOf("color" to 0L), padding = 0),
            ),
            parserState = WGSLParserState.unavailable("wgsl4k", "WGSL parser unavailable in :gpu-renderer"),
            capabilities = WGSLFacadeCapabilities(supportedFeatures = emptySet()),
        )
    }

    /** Creates a uniform binding layout fixture. */
    private fun uniformBinding(group: Int, binding: Int, role: String, size: Long): WGSLBindingLayout =
        WGSLBindingLayout(
            group = group,
            binding = binding,
            visibility = setOf("vertex", "fragment"),
            resourceKind = "uniform-buffer",
            access = "read",
            minBindingSize = size,
            dynamicOffset = false,
            layoutRole = role,
            diagnosticLabel = role,
        )

    /** Creates a uniform layout fixture from typed fields. */
    private fun uniformLayout(hash: String, fields: List<WGSLUniformFieldLayout>, size: Long): WGSLUniformLayout =
        WGSLUniformLayout(
            layoutHash = hash,
            fields = fields.map { it.name },
            fieldLayouts = fields,
            sizeBytes = size,
            alignment = 16,
            numericRepresentation = "f32/u32",
        )

    /** Creates a packing plan fixture with deterministic field order. */
    private fun packingPlan(
        hash: String,
        layoutHash: String,
        offsets: Map<String, Long>,
        padding: Long,
    ): WGSLPackingPlan =
        WGSLPackingPlan(
            planHash = hash,
            layoutHash = layoutHash,
            fieldOrder = offsets.keys.toList(),
            offsets = offsets,
            paddingBytes = padding,
            dynamicOffsetAlignment = 256,
        )
}

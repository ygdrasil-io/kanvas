package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/** Verifies generic WGSL render module assembly, ABI dumps, reflection fixtures, and rejection diagnostics. */
class WGSLModuleAbiTest {
    /** The first ABI fixture exposes group zero draw data and group one solid material data deterministically. */
    @Test
    fun `solid render module fixture exposes group zero and group one ABI`() {
        val result = WGSLModuleAssembler.assembleRenderModule(solidModuleInput())
        val module = (result as? WGSLModuleAssemblyResult.Accepted)?.module
            ?: fail("Expected solid module fixture to assemble")

        assertEquals("solid-rect-render", module.moduleLabel)
        assertEquals("vs_main", module.vertexEntryPoint)
        assertEquals("fs_main", module.fragmentEntryPoint)
        assertEquals(WGSLParserState.unavailable("wgsl4k", "wgsl4k dependency unavailable in :gpu-renderer"), module.parserState)
        assertFalse(module.parserState.parserBacked, "Fixture reflection must not claim parser-backed support")

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

        assertEquals(
            """
            module=solid-rect-render
            entryPoints=vertex:vs_main,fragment:fs_main
            parser=unavailable:wgsl4k
            binding=0/0 frame uniform-buffer min=64
            binding=0/1 render-step uniform-buffer min=64
            binding=0/2 intrinsic-draw uniform-buffer min=64
            binding=1/0 material-solid uniform-buffer min=16
            uniform=layout:frame-block:v1 size=64 align=16 fields=frameIndex:u32@0/4, targetSize:vec2<f32>@16/8
            uniform=layout:render-step-block:v1 size=64 align=16 fields=coverageMode:u32@0/4, blendMode:u32@4/4
            uniform=layout:intrinsic-draw-block:v1 size=64 align=16 fields=localToDevice:mat3x3<f32>@0/48
            uniform=layout:solid-material-block:v1 size=16 align=16 fields=color:vec4<f32>@0/16
            packing=pack:frame-block:v1 layout=layout:frame-block:v1 fields=frameIndex@0,targetSize@16 padding=44
            packing=pack:render-step-block:v1 layout=layout:render-step-block:v1 fields=coverageMode@0,blendMode@4 padding=56
            packing=pack:intrinsic-draw-block:v1 layout=layout:intrinsic-draw-block:v1 fields=localToDevice@0 padding=16
            packing=pack:solid-material-block:v1 layout=layout:solid-material-block:v1 fields=color@0 padding=0
            reflection=fixture-declared
            """.trimIndent(),
            module.abiDump(),
        )
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

        assertContains(rejected.diagnostics.map { it.code }, "unsupported.wgsl.feature_unrepresented_by_wgsl4k")
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
            parserState = WGSLParserState.unavailable("wgsl4k", "wgsl4k dependency unavailable in :gpu-renderer"),
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

package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WGSLParserBackedReflectionTest {
    @Test
    fun `assembler produces parser-backed reflection for solid rect WGSL when parser available`() {
        val result = WGSLModuleAssembler.assembleRenderModule(solidModuleInput())
        val module = (result as WGSLModuleAssemblyResult.Accepted).module

        assertTrue(module.parserState.parserBacked, "Solid rect module must be parser-backed when wgsl4k is available")
        val reflection = assertIs<WGSLReflectionResult.Accepted>(module.reflection)
        assertTrue(reflection.reflectionSource != "fixture-declared", "Reflection source must not be fixture-declared")
        assertTrue(reflection.reflectionSource.contains("wgsl4k"), "Reflection source must reference wgsl4k")
        assertFalse(reflection.diagnostics.any { it.terminal }, "Parser-backed reflection must have no terminal diagnostics")
    }

    @Test
    fun `ABI validator rejects Kotlin packing that mismatches WGSL reflection layout`() {
        val layout = uniformLayout(
            hash = "layout:test-block:v1",
            fields = listOf(
                WGSLUniformFieldLayout("testField", "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16),
            ),
            size = 16,
        )
        val packing = WGSLPackingPlan(
            planHash = "pack:test-block:v1",
            layoutHash = layout.layoutHash,
            fieldOrder = listOf("testField"),
            offsets = mapOf("testField" to 4L),
            paddingBytes = 12,
            dynamicOffsetAlignment = 256,
        )

        val result = WGSLAbiValidator.validate(layout, packing)

        assertIs<WGSLAbiValidationResult.Mismatch>(result)
        assertTrue(result.diagnostic.contains("testField"), "Mismatch diagnostic must reference the field name")
        assertTrue(result.diagnostic.contains("4"), "Mismatch diagnostic must reference the actual offset")
        assertTrue(result.diagnostic.contains("0"), "Mismatch diagnostic must reference the expected offset")
    }

    @Test
    fun `ABI validator accepts matching layouts`() {
        val layout = uniformLayout(
            hash = "layout:test-block:v1",
            fields = listOf(
                WGSLUniformFieldLayout("color", "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16),
                WGSLUniformFieldLayout("alpha", "f32", offset = 16L, sizeBytes = 4L, alignment = 4),
            ),
            size = 20,
        )
        val packing = WGSLPackingPlan(
            planHash = "pack:test-block:v1",
            layoutHash = layout.layoutHash,
            fieldOrder = listOf("color", "alpha"),
            offsets = mapOf("color" to 0L, "alpha" to 16L),
            paddingBytes = 12,
            dynamicOffsetAlignment = 256,
        )

        val result = WGSLAbiValidator.validate(layout, packing)

        assertIs<WGSLAbiValidationResult.Match>(result)
    }

    @Test
    fun `evolution gate fails when any WGSL module uses fixture-declared reflection`() {
        val fixtureModule = createModuleWithFixtureDeclaredReflection()
        val parserModule = createModuleWithParserBackedReflection()

        val result = evaluateWgsl4kEvolutionGate(listOf(fixtureModule, parserModule))

        assertIs<Wgsl4kEvolutionGate.NotPassed>(result)
    }

    @Test
    fun `evolution gate passes when all modules use parser-backed reflection`() {
        val modules = listOf(
            createModuleWithParserBackedReflection(),
            createModuleWithParserBackedReflection(),
        )

        val result = evaluateWgsl4kEvolutionGate(modules)

        assertIs<Wgsl4kEvolutionGate.Passed>(result)
    }

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

    private fun createModuleWithFixtureDeclaredReflection(): WGSLModule {
        val moduleHash = WGSLModuleHash("wgsl-module:fixture")
        return WGSLModule(
            moduleHash = moduleHash,
            entryPoint = "fs_main",
            fragments = emptyList(),
            bindings = emptyList(),
            uniformLayouts = emptyList(),
            storageLayouts = emptyList(),
            reflection = WGSLReflectionResult.Accepted(
                moduleHash = moduleHash,
                bindings = emptyList(),
                uniforms = emptyList(),
                storage = emptyList(),
                parserState = WGSLParserState.unavailable("wgsl4k", "parser not available"),
                reflectionSource = "fixture-declared",
            ),
            rendererVersionSalt = "v1",
        )
    }

    private fun createModuleWithParserBackedReflection(): WGSLModule {
        val moduleHash = WGSLModuleHash("wgsl-module:parser")
        val parserState = WGSLParserState(status = "parser-backed", toolName = "wgsl4k", message = "parser-backed via wgsl4k")
        return WGSLModule(
            moduleHash = moduleHash,
            entryPoint = "fs_main",
            fragments = emptyList(),
            bindings = emptyList(),
            uniformLayouts = emptyList(),
            storageLayouts = emptyList(),
            reflection = WGSLReflectionResult.Accepted(
                moduleHash = moduleHash,
                bindings = emptyList(),
                uniforms = emptyList(),
                storage = emptyList(),
                parserState = parserState,
                reflectionSource = "wgsl4k-parsed",
            ),
            rendererVersionSalt = "v1",
        )
    }

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

    private fun uniformLayout(hash: String, fields: List<WGSLUniformFieldLayout>, size: Long): WGSLUniformLayout =
        WGSLUniformLayout(
            layoutHash = hash,
            fields = fields.map { it.name },
            fieldLayouts = fields,
            sizeBytes = size,
            alignment = 16,
            numericRepresentation = "f32/u32",
        )

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

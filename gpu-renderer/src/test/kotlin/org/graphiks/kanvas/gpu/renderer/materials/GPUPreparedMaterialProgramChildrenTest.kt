package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedBlenderChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedColorFilterChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgramAdmission
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.SimpleRTDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTBindingPlanHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTReflectionHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformSchemaHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl

class GPUPreparedMaterialProgramChildrenTest {
    @Test
    fun `prepared runtime material compiles exact shader matrix and mode children once`() {
        val ready = compileReady(
            descriptor = exactDescriptor(),
            parent = parentProgram(exactSlots()),
        )

        assertEquals(listOf("source", "filter", "blender"), ready.childPrograms.map { it.name })
        assertEquals(
            listOf(
                GPUPreparedRuntimeEffectChildRole.Shader,
                GPUPreparedRuntimeEffectChildRole.ColorFilter,
                GPUPreparedRuntimeEffectChildRole.Blender,
            ),
            ready.childPrograms.map { it.role },
        )
        assertEquals(16, ready.childPrograms[0].uniformBytes.size)
        assertEquals(80, ready.childPrograms[1].uniformBytes.size)
        assertTrue(ready.childPrograms[2].uniformBytes.isEmpty())
        assertTrue(ready.childPrograms.all { child -> child.programKey.isNotBlank() })
        assertTrue(ready.childPrograms.all { child -> child.abiHash.startsWith("sha256:") })
    }

    @Test
    fun `prepared runtime material refuses missing extra reordered and wrong role children`() {
        val exact = exactDescriptor().childDescriptors
        val cases = listOf(
            GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
                effectId = SimpleRTDescriptor.effectId.value,
                uniforms = uniforms(),
                childDescriptors = linkedMapOf(
                    "source" to exact.getValue("source"),
                    "filter" to exact.getValue("filter"),
                ),
            ) to "unsupported.material.runtime_effect.child_missing",
            GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
                effectId = SimpleRTDescriptor.effectId.value,
                uniforms = uniforms(),
                childDescriptors = LinkedHashMap(exact).apply {
                    put("extra", GPURuntimeEffectChildDescriptor.Shader(solid()))
                },
            ) to "unsupported.material.runtime_effect.child_extra",
            GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
                effectId = SimpleRTDescriptor.effectId.value,
                uniforms = uniforms(),
                childDescriptors = linkedMapOf(
                    "filter" to exact.getValue("filter"),
                    "source" to exact.getValue("source"),
                    "blender" to exact.getValue("blender"),
                ),
            ) to "unsupported.material.runtime_effect.child_order",
            GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
                effectId = SimpleRTDescriptor.effectId.value,
                uniforms = uniforms(),
                childDescriptors = linkedMapOf(
                    "source" to exact.getValue("blender"),
                    "filter" to exact.getValue("filter"),
                    "blender" to exact.getValue("blender"),
                ),
            ) to "unsupported.material.runtime_effect.child_role",
        )

        cases.forEach { (descriptor, expectedCode) ->
            val refused = compileRefused(descriptor, parentProgram(exactSlots()))
            assertEquals(expectedCode, refused.code, descriptor.childDescriptors.keys.toString())
        }
    }

    @Test
    fun `prepared runtime material refuses a child program ABI mismatch`() {
        val slots = exactSlots().toMutableList()
        slots[0] = slots[0].copy(abiHash = "sha256:${"f".repeat(64)}")

        val refused = compileRefused(exactDescriptor(), parentProgram(slots))

        assertEquals("unsupported.material.runtime_effect.child_abi", refused.code)
    }

    @Test
    fun `prepared runtime material refuses unregistered arithmetic and runtime color filter programs`() {
        val arithmetic = descriptorWithSingleChild(
            "blender",
            GPURuntimeEffectChildDescriptor.Blender(
                GPUPreparedBlenderChildDescriptor.Arithmetic(1f, 0f, 0f, 0f),
            ),
        )
        val registeredFilter = descriptorWithSingleChild(
            "filter",
            GPURuntimeEffectChildDescriptor.ColorFilter(
                GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect(
                    GPUMaterialDescriptor.RuntimeEffect(
                        effectId = SimpleRTDescriptor.effectId.value,
                        uniforms = uniforms(),
                    ),
                ),
            ),
        )

        assertEquals(
            "unsupported.material.runtime_effect.child_program",
            compileRefused(
                arithmetic,
                parentProgram(listOf(slot("blender", GPUPreparedRuntimeEffectChildRole.Blender))),
            ).code,
        )
        assertEquals(
            "unsupported.material.runtime_effect.child_program",
            compileRefused(
                registeredFilter,
                parentProgram(listOf(slot("filter", GPUPreparedRuntimeEffectChildRole.ColorFilter))),
            ).code,
        )
    }

    @Test
    fun `child payload and resources change material key without changing invocation ABI`() {
        val uniformA = descriptorWithSingleChild(
            "source",
            GPURuntimeEffectChildDescriptor.Shader(solid(r = 0.1f)),
        )
        val uniformB = descriptorWithSingleChild(
            "source",
            GPURuntimeEffectChildDescriptor.Shader(solid(r = 0.9f)),
        )
        val imageA = descriptorWithSingleChild(
            "source",
            GPURuntimeEffectChildDescriptor.Shader(image(byteArrayOf(1, 2, 3, 4))),
        )
        val imageB = descriptorWithSingleChild(
            "source",
            GPURuntimeEffectChildDescriptor.Shader(image(byteArrayOf(4, 3, 2, 1))),
        )
        val parent = parentProgram(
            listOf(slot("source", GPUPreparedRuntimeEffectChildRole.Shader)),
        )

        val uniformProgramA = compileReady(uniformA, parent)
        val uniformProgramB = compileReady(uniformB, parent)
        val imageProgramA = compileReady(imageA, parent)
        val imageProgramB = compileReady(imageB, parent)

        assertNotEquals(uniformProgramA.materialKey, uniformProgramB.materialKey)
        assertEquals(uniformProgramA.abiHash, uniformProgramB.abiHash)
        assertNotEquals(imageProgramA.materialKey, imageProgramB.materialKey)
        assertEquals(imageProgramA.abiHash, imageProgramB.abiHash)
        assertNotEquals(
            imageProgramA.childPrograms.single().resourceFacts,
            imageProgramB.childPrograms.single().resourceFacts,
        )
        assertTrue(
            imageProgramA.childPrograms.single().resourceFacts.none { fact ->
                "handle" in fact.lowercase() || "offset" in fact.lowercase()
            },
        )
    }

    @Test
    fun `child source and compiled ABI change common material and ABI identity`() {
        val solidDescriptor = descriptorWithSingleChild(
            "source",
            GPURuntimeEffectChildDescriptor.Shader(solid()),
        )
        val imageDescriptor = descriptorWithSingleChild(
            "source",
            GPURuntimeEffectChildDescriptor.Shader(image(byteArrayOf(1, 2, 3, 4))),
        )
        val parent = parentProgram(
            listOf(slot("source", GPUPreparedRuntimeEffectChildRole.Shader)),
        )

        val solidProgram = compileReady(solidDescriptor, parent)
        val imageProgram = compileReady(imageDescriptor, parent)
        val compiledSolidChild = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(solid(), 1f, context(parent)),
        ).program

        assertEquals(compiledSolidChild.abiHash, solidProgram.childPrograms.single().abiHash)
        assertNotEquals(solidProgram.childPrograms.single().abiHash, imageProgram.childPrograms.single().abiHash)
        assertNotEquals(solidProgram.materialKey, imageProgram.materialKey)
        assertNotEquals(solidProgram.abiHash, imageProgram.abiHash)
    }

    @Test
    fun `child names order and roles change common material and ABI identity`() {
        val exact = compileReady(exactDescriptor(), parentProgram(exactSlots()))
        val renamedDescriptor = GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = SimpleRTDescriptor.effectId.value,
            uniforms = uniforms(),
            childDescriptors = linkedMapOf(
                "renamed" to GPURuntimeEffectChildDescriptor.Shader(solid()),
                "filter" to matrixChild(),
                "blender" to modeChild(),
            ),
        )
        val renamedSlots = listOf(
            slot("renamed", GPUPreparedRuntimeEffectChildRole.Shader),
            slot("filter", GPUPreparedRuntimeEffectChildRole.ColorFilter),
            slot("blender", GPUPreparedRuntimeEffectChildRole.Blender),
        )
        val reorderedDescriptor = GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = SimpleRTDescriptor.effectId.value,
            uniforms = uniforms(),
            childDescriptors = linkedMapOf(
                "filter" to matrixChild(),
                "source" to GPURuntimeEffectChildDescriptor.Shader(solid()),
                "blender" to modeChild(),
            ),
        )
        val reorderedSlots = listOf(
            slot("filter", GPUPreparedRuntimeEffectChildRole.ColorFilter),
            slot("source", GPUPreparedRuntimeEffectChildRole.Shader),
            slot("blender", GPUPreparedRuntimeEffectChildRole.Blender),
        )

        listOf(
            compileReady(renamedDescriptor, parentProgram(renamedSlots)),
            compileReady(reorderedDescriptor, parentProgram(reorderedSlots)),
        ).forEach { changed ->
            assertNotEquals(exact.materialKey, changed.materialKey)
            assertNotEquals(exact.abiHash, changed.abiHash)
        }
    }

    @Test
    fun `authenticated child program substitution is refused`() {
        val program = compileReady(exactDescriptor(), parentProgram(exactSlots()))
        val admissionField = GPUPreparedMaterialProgram::class.java.getDeclaredField("admission")
        admissionField.isAccessible = true
        val admission = admissionField.get(program) as GPUPreparedMaterialProgramAdmission

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedMaterialProgram.createAuthenticated(
                wgslSource = program.wgslSource,
                entryPoint = program.entryPoint,
                uniformBytes = program.uniformBytes,
                sampledResources = program.sampledResources,
                childPrograms = program.childPrograms.dropLast(1),
                paintAlpha = program.paintAlpha,
                sourceKind = program.sourceKind,
                preCoverageSourceAlpha = program.preCoverageSourceAlpha,
                admission = admission,
            )
        }
    }

    @Test
    fun `descriptor graph refuses a recursive child beyond the existing depth budget`() {
        var nested: GPUMaterialDescriptor = solid()
        assertFailsWith<IllegalArgumentException> {
            repeat(65) {
                nested = descriptorWithSingleChild(
                    "source",
                    GPURuntimeEffectChildDescriptor.Shader(nested),
                )
            }
        }
    }

    private fun compileReady(
        descriptor: GPUMaterialDescriptor.RuntimeEffect,
        parent: GPUPreparedRuntimeEffectProgram,
    ): GPUPreparedMaterialProgram = assertIs<GPUPreparedMaterialProgramResult.Ready>(
        GPUPreparedMaterialProgramCompiler.compile(descriptor, 1f, context(parent)),
    ).program

    private fun compileRefused(
        descriptor: GPUMaterialDescriptor.RuntimeEffect,
        parent: GPUPreparedRuntimeEffectProgram,
    ): GPUPreparedMaterialProgramResult.Refused =
        assertIs<GPUPreparedMaterialProgramResult.Refused>(
            GPUPreparedMaterialProgramCompiler.compile(descriptor, 1f, context(parent)),
        )

    private fun context(parent: GPUPreparedRuntimeEffectProgram) = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:child-test:v1",
        runtimeEffectResolver = GPUPreparedRuntimeEffectResolver {
                effectId,
                descriptorVersion,
            ->
            if (effectId == parent.effectId && descriptorVersion == parent.descriptorVersion) {
                GPUPreparedRuntimeEffectResolution.Ready(parent)
            } else {
                GPUPreparedRuntimeEffectResolution.DescriptorUnavailable("not registered")
            }
        },
    )

    private fun exactDescriptor(): GPUMaterialDescriptor.RuntimeEffect =
        GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = SimpleRTDescriptor.effectId.value,
            uniforms = uniforms(),
            childDescriptors = linkedMapOf(
                "source" to GPURuntimeEffectChildDescriptor.Shader(solid()),
                "filter" to matrixChild(),
                "blender" to modeChild(),
            ),
        )

    private fun descriptorWithSingleChild(
        name: String,
        child: GPURuntimeEffectChildDescriptor,
    ): GPUMaterialDescriptor.RuntimeEffect =
        GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = SimpleRTDescriptor.effectId.value,
            uniforms = uniforms(),
            childDescriptors = linkedMapOf(name to child),
        )

    private fun exactSlots(): List<GPUPreparedRuntimeEffectChildSlot> = listOf(
        slot("source", GPUPreparedRuntimeEffectChildRole.Shader),
        slot("filter", GPUPreparedRuntimeEffectChildRole.ColorFilter),
        slot("blender", GPUPreparedRuntimeEffectChildRole.Blender),
    )

    private fun slot(
        name: String,
        role: GPUPreparedRuntimeEffectChildRole,
    ): GPUPreparedRuntimeEffectChildSlot = GPUPreparedRuntimeEffectChildSlot(
        name = name,
        role = role,
        bindingIndex = null,
        abiHash = preparedRuntimeEffectChildAbiHash(role),
    )

    private fun parentProgram(
        slots: List<GPUPreparedRuntimeEffectChildSlot>,
    ): GPUPreparedRuntimeEffectProgram = GPUPreparedRuntimeEffectProgram(
        effectId = SimpleRTDescriptor.effectId.value,
        descriptorVersion = SimpleRTDescriptor.descriptorVersion.value,
        wgslSource = SimpleRTWgsl,
        sourceFunction = SimpleRTEntryPoint,
        sourceColorContract = GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba,
        sourceHash = SimpleRTSourceHash,
        moduleHash = "sha256:${"1".repeat(64)}",
        reflectionHash = "sha256:${"2".repeat(64)}",
        uniformSchemaHash = SimpleRTUniformSchemaHash,
        uniformBlockSizeBytes = 16,
        uniformFields = listOf(
            GPUPreparedRuntimeEffectUniformField(
                name = "gColor",
                type = GPUPreparedRuntimeEffectUniformType.Float4,
                offsetBytes = 0,
                sizeBytes = 16,
                alignmentBytes = 16,
            ),
        ),
        bindings = listOf(
            GPUPreparedRuntimeEffectBinding(
                group = 1,
                binding = 0,
                resourceKind = "uniformBuffer",
                minBindingSizeBytes = 16,
            ),
        ),
        childSlots = slots,
        bindingPlanHash = SimpleRTBindingPlanHash,
        routeContractHash = "sha256:${"3".repeat(64)}",
    )

    private fun matrixChild(): GPURuntimeEffectChildDescriptor.ColorFilter =
        GPURuntimeEffectChildDescriptor.ColorFilter(
            GPUPreparedColorFilterChildDescriptor.Matrix(
                listOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

    private fun modeChild(): GPURuntimeEffectChildDescriptor.Blender =
        GPURuntimeEffectChildDescriptor.Blender(
            GPUPreparedBlenderChildDescriptor.Mode(GPUBlendMode.SRC_OVER),
        )

    private fun uniforms(): Map<String, GPURuntimeEffectUniformValue> = mapOf(
        "gColor" to GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
    )

    private fun solid(r: Float = 0.25f) =
        GPUMaterialDescriptor.SolidColor(r, 0.5f, 0.75f, 0.8f)

    private fun image(pixels: ByteArray) = GPUMaterialDescriptor.ImageDraw(
        imageSourceId = "child-image",
        imageWidth = 1,
        imageHeight = 1,
        rgbaPixels = pixels,
        samplingFilterMode = "nearest",
    )
}

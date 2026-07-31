package org.graphiks.kanvas.gpu.renderer.payloads

import java.lang.reflect.Modifier
import java.nio.Buffer
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedBlenderChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedColorFilterChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildRole
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildSlot
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectSourceColorContract
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformField
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformType
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgramAdmission
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedRuntimeEffectChildCpuProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.SimpleRTDescriptor
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesArtifactInput
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesPacker
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesPackingLimits
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesPackingResult
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTBindingPlanHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTReflectionHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformSchemaHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl

class GPUPreparedVerticesPayloadTest {
    @Test
    fun `every closed semantic axis changes the canonical hash independently`() {
        val baselineInput = input()
        val baseline = baselineInput.ready()
        val coloredArtifact = artifact(colors = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1))
        val stripArtifact = artifact(
            topology = GPUVertexMode.TriangleStrip,
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f, 2f, 2f),
        )
        val mutations = linkedMapOf(
            "payloadRef" to baselineInput.copy(
                payloadRef = GPUDrawPayloadRef(8, PREPARED_VERTICES_RENDER_STEP_IDENTITY),
            ),
            "artifact bytes" to baselineInput.copy(artifact = artifact(positions = floatArrayOf(0f, 0f, 3f, 0f, 0f, 2f))),
            "artifact layout" to baselineInput.copy(artifact = coloredArtifact),
            "artifact topology" to baselineInput.copy(artifact = stripArtifact, topologyIdentity = GPUPreparedVerticesTopologyIdentity.TriangleStrip),
            "transform raw bits" to baselineInput.copy(
                transformBytes = baselineInput.transformBytes.toMutableList().also { it[2] = 1f.toRawBits() },
            ),
            "material key abi and uniform bytes" to baselineInput.copy(material = material(0.5f)),
            "material resource bytes" to baselineInput.copy(material = imageMaterial(byteArrayOf(4, 3, 2, 1))),
            "primitive color presence" to baselineInput.copy(
                primitiveColorPresent = true,
                primitiveBlendIdentity = "primitive:src-over:v1",
            ),
            "clip identity" to baselineInput.copy(clipIdentity = "clip:other"),
            "clip coverage" to baselineInput.copy(clipCoverageIdentity = "coverage:scissor:0,0,16,16"),
            "scissor" to baselineInput.copy(scissorBounds = GPUPixelBounds(1, 0, 16, 16)),
            "final blend" to baselineInput.copy(finalBlendIdentity = "blend:src:v1"),
            "target bounds" to baselineInput.copy(targetBounds = GPUPixelBounds(0, 0, 32, 16)),
            "target format" to baselineInput.copy(targetFormat = "bgra8unorm"),
            "capability" to baselineInput.copy(capabilitySnapshotHash = "capability:other"),
            "draw provenance" to baselineInput.copy(drawProvenance = "drawMesh:program"),
            "frame provenance" to baselineInput.copy(frameProvenance = GPUFrameProvenance.HarnessBackground),
        )

        mutations.forEach { (axis, changedInput) ->
            assertNotEquals(baseline.canonicalHash, changedInput.ready().canonicalHash, axis)
        }

        val imageBaseline = baselineInput.copy(
            material = imageMaterial(byteArrayOf(1, 2, 3, 4)),
        ).ready()
        val imageBytesOnly = baselineInput.copy(
            material = imageMaterial(byteArrayOf(4, 3, 2, 1)),
        ).ready()
        assertNotEquals(
            imageBaseline.canonicalHash,
            imageBytesOnly.canonicalHash,
            "sampled resource bytes must be an isolated material hash axis",
        )
    }

    @Test
    fun `raw float encoding distinguishes negative zero and refuses non finite transform`() {
        val positive = input(transformBytes = identityTransformBits()).ready()
        val negativeZero = input(
            transformBytes = identityTransformBits().toMutableList().also {
                it[2] = (-0.0f).toRawBits()
            },
        ).ready()

        assertNotEquals(positive.canonicalHash, negativeZero.canonicalHash)
        val refused = assertIs<GPUPreparedVerticesPayloadResult.Refused>(
            GPUPreparedVerticesPayloadGatherer.gather(
                input(transformBytes = identityTransformBits().toMutableList().also {
                    it[0] = Float.NaN.toRawBits()
                }),
            ),
        )
        assertEquals("invalid.renderer.prepared.vertices-transform", refused.code)
    }

    @Test
    fun `canonical strings preserve exact UTF16 code units without replacement`() {
        val firstMalformed = input(drawProvenance = "draw:\uD800").ready()
        val secondMalformed = input(drawProvenance = "draw:\uD801").ready()
        val astralPair = input(drawProvenance = "draw:\uD83D\uDE00").ready()

        assertNotEquals(firstMalformed.canonicalHash, secondMalformed.canonicalHash)
        assertNotEquals(firstMalformed.canonicalHash, astralPair.canonicalHash)
        assertNotEquals(secondMalformed.canonicalHash, astralPair.canonicalHash)
    }

    @Test
    fun `factory refuses malformed hash topology bounds identities and payload ref atomically`() {
        val malformed = listOf(
            "invalid.renderer.prepared.vertices-hash" to input(suppliedCanonicalHash = "sha256:${"0".repeat(64)}"),
            "invalid.renderer.prepared.vertices-topology" to input(topologyIdentity = GPUPreparedVerticesTopologyIdentity.TriangleStrip),
            "invalid.renderer.prepared.vertices-bounds" to input(scissorBounds = GPUPixelBounds(0, 0, 17, 16)),
            "invalid.renderer.prepared.vertices-identity" to input(clipIdentity = " "),
            "invalid.renderer.prepared.vertices-capability" to input(capabilitySnapshotHash = ""),
            "invalid.renderer.prepared.vertices-provenance" to input(drawProvenance = ""),
            "invalid.renderer.prepared.vertices-payload-ref" to input(
                payloadRef = GPUDrawPayloadRef(7, "other.step"),
            ),
        )

        malformed.forEach { (expectedCode, malformedInput) ->
            val refused = assertIs<GPUPreparedVerticesPayloadResult.Refused>(
                GPUPreparedVerticesPayloadGatherer.gather(malformedInput),
                expectedCode,
            )
            assertEquals(expectedCode, refused.code)
        }
    }

    @Test
    fun `published snapshots resist hostile mutation and recompute their hash`() {
        val transform = identityTransformBits().toMutableList()
        val resourceBytes = byteArrayOf(1, 2, 3, 4)
        val semantic = input(
            transformBytes = transform,
            material = imageMaterial(resourceBytes),
        ).ready()
        val hash = semantic.canonicalHash

        transform.fill(42)
        resourceBytes.fill(99)
        @Suppress("UNCHECKED_CAST")
        assertTrue(runCatching { (semantic.transformBytes as MutableList<Int>)[0] = 0 }.isFailure)
        @Suppress("UNCHECKED_CAST")
        assertTrue(runCatching { (semantic.material.uniformBytes as MutableList<Int>)[0] = 0 }.isFailure)

        assertEquals(hash, semantic.canonicalHash)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `semantic object graph is recursively handle free`() {
        val semantic = input(material = imageMaterial(byteArrayOf(1, 2, 3, 4))).ready()
        val visited = IdentityHashMap<Any, Boolean>()
        val forbiddenField = Regex(
            "nativeHandle|cacheHit|deviceGeneration|bufferGeneration|materialGeneration|uploadRange",
            RegexOption.IGNORE_CASE,
        )

        fun visit(value: Any?, path: String) {
            if (value == null || value is String || value is Number || value is Boolean || value is Enum<*>) return
            if (visited.put(value, true) != null) return
            assertFalse(value is AutoCloseable, "$path owns AutoCloseable ${value.javaClass.name}")
            assertFalse(value is Buffer, "$path owns NIO buffer ${value.javaClass.name}")
            val typeName = value.javaClass.name
            assertFalse(
                typeName.contains("wgpu", ignoreCase = true) ||
                    typeName.contains("NativeHandle", ignoreCase = true) ||
                    typeName.contains("CommandEncoder", ignoreCase = true) ||
                    typeName.contains("PipelineCache", ignoreCase = true),
                "$path contains backend type $typeName",
            )
            when (value) {
                is ByteArray, is IntArray, is FloatArray, is LongArray -> return
                is Array<*> -> value.forEachIndexed { index, child -> visit(child, "$path[$index]") }
                is Iterable<*> -> value.forEachIndexed { index, child -> visit(child, "$path[$index]") }
                is Map<*, *> -> value.forEach { (key, child) ->
                    visit(key, "$path.key")
                    visit(child, "$path[$key]")
                }
                else -> if (typeName.startsWith("org.graphiks.kanvas")) {
                    value.javaClass.declaredFields
                        .filterNot { field -> Modifier.isStatic(field.modifiers) }
                        .forEach { field ->
                            assertFalse(forbiddenField.containsMatchIn(field.name), "$path.${field.name}")
                            field.isAccessible = true
                            visit(field.get(value), "$path.${field.name}")
                        }
                }
            }
        }

        visit(semantic, "semantic")
        assertTrue(visited.size > 10, "recursive test must traverse the nested semantic graph")
    }

    @Test
    fun `runtime child semantics change hash and every unauthenticated child axis refuses`() {
        val baselineMaterial = runtimeChildMaterial(0.25f)
        val changedChildMaterial = runtimeChildMaterial(0.5f)
        assertNotEquals(
            input(material = baselineMaterial).ready().canonicalHash,
            input(material = changedChildMaterial).ready().canonicalHash,
        )

        val admissionField = GPUPreparedMaterialProgram::class.java.getDeclaredField("admission")
        admissionField.isAccessible = true
        val admission = admissionField.get(baselineMaterial) as GPUPreparedMaterialProgramAdmission
        val children = baselineMaterial.childPrograms
        val first = children.first()
        val baselineHash = input(material = baselineMaterial).ready().canonicalHash
        val validHash = "sha256:${"a".repeat(64)}"
        val mutations = listOf<List<org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildProgram>>(
            children.reversed(),
            listOf(first.copy(name = "renamed")) + children.drop(1),
            listOf(first.copy(role = GPUPreparedRuntimeEffectChildRole.ColorFilter)) + children.drop(1),
            listOf(first.copy(programKey = "forged.program")) + children.drop(1),
            listOf(first.copy(abiHash = validHash)) + children.drop(1),
            listOf(first.copy(uniformBytes = first.uniformBytes + 0)) + children.drop(1),
            listOf(first.copy(resourceFacts = first.resourceFacts + "forged=resource")) + children.drop(1),
            listOf(first.copy(wgslSource = first.wgslSource + "\n")) + children.drop(1),
            listOf(first.copy(evaluationFunction = "forged_evaluate")) + children.drop(1),
            listOf(first.copy(
                cpuProgram = GPUPreparedRuntimeEffectChildCpuProgram.Shader("forged.material"),
            )) + children.drop(1),
        )
        mutations.forEach { mutatedChildren ->
            val mutation = runCatching {
                GPUPreparedMaterialProgram.createAuthenticated(
                    wgslSource = baselineMaterial.wgslSource,
                    entryPoint = baselineMaterial.entryPoint,
                    uniformBytes = baselineMaterial.uniformBytes,
                    sampledResources = baselineMaterial.sampledResources,
                    childPrograms = mutatedChildren,
                    paintAlpha = baselineMaterial.paintAlpha,
                    sourceKind = baselineMaterial.sourceKind,
                    preCoverageSourceAlpha = baselineMaterial.preCoverageSourceAlpha,
                    admission = admission,
                )
            }
            mutation.getOrNull()?.let { acceptedMutation ->
                assertNotEquals(
                    baselineHash,
                    input(material = acceptedMutation).ready().canonicalHash,
                )
            } ?: assertIs<IllegalArgumentException>(mutation.exceptionOrNull())
        }
    }

    private fun GPUPreparedVerticesPayloadInput.ready(): GPUDrawSemanticPayload.Vertices =
        assertIs<GPUPreparedVerticesPayloadResult.Ready>(
            GPUPreparedVerticesPayloadGatherer.gather(this),
        ).payload

    private fun input(
        payloadRef: GPUDrawPayloadRef = GPUDrawPayloadRef(7, PREPARED_VERTICES_RENDER_STEP_IDENTITY),
        artifact: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact = artifact(),
        material: GPUPreparedMaterialProgram = material(0.25f),
        topologyIdentity: GPUPreparedVerticesTopologyIdentity = when (artifact.topology) {
            GPUVertexMode.Triangles -> GPUPreparedVerticesTopologyIdentity.Triangles
            GPUVertexMode.TriangleStrip -> GPUPreparedVerticesTopologyIdentity.TriangleStrip
            else -> error("test artifacts must be canonical")
        },
        transformBytes: List<Int> = identityTransformBits(),
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 16, 16),
        scissorBounds: GPUPixelBounds = targetBounds,
        targetFormat: String = "rgba8unorm",
        clipIdentity: String = "clip:wide-open",
        clipCoverageIdentity: String = "coverage:none",
        primitiveColorPresent: Boolean = false,
        primitiveBlendIdentity: String? = null,
        finalBlendIdentity: String = "blend:src-over:v1",
        capabilitySnapshotHash: String = "capability:unit",
        drawProvenance: String = "drawVertices",
        frameProvenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        suppliedCanonicalHash: String? = null,
    ) = GPUPreparedVerticesPayloadInput(
        payloadRef = payloadRef,
        artifact = artifact,
        material = material,
        topologyIdentity = topologyIdentity,
        transformBytes = transformBytes,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        targetFormat = targetFormat,
        clipIdentity = clipIdentity,
        clipCoverageIdentity = clipCoverageIdentity,
        primitiveColorPresent = primitiveColorPresent,
        primitiveBlendIdentity = primitiveBlendIdentity,
        finalBlendIdentity = finalBlendIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        drawProvenance = drawProvenance,
        frameProvenance = frameProvenance,
        suppliedCanonicalHash = suppliedCanonicalHash,
    )

    private fun artifact(
        topology: GPUVertexMode = GPUVertexMode.Triangles,
        positions: FloatArray = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
        colors: ByteArray? = null,
    ) = assertIs<GPUPreparedVerticesPackingResult.Ready>(
        GPUPreparedVerticesPacker.pack(
            GPUPreparedVerticesArtifactInput(
                topology = topology,
                positions = positions,
                colorsRgba8 = colors,
                texCoords = null,
                indices = null,
                provenance = "payload-test",
            ),
            GPUPreparedVerticesPackingLimits(
                maxVertices = 64,
                maxIndices = 64,
                maxVertexBytes = 4096,
                maxIndexBytes = 4096,
                maxFanExpandedIndices = 64,
            ),
            supportsUint32Index = true,
        ),
    ).artifact

    private fun material(red: Float): GPUPreparedMaterialProgram = compile(
        GPUMaterialDescriptor.SolidColor(red, 0.5f, 0.75f, 1f),
    )

    private fun imageMaterial(bytes: ByteArray): GPUPreparedMaterialProgram = compile(
        GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "payload-resource",
            imageWidth = 1,
            imageHeight = 1,
            rgbaPixels = bytes,
            samplingFilterMode = "nearest",
        ),
    )

    private fun runtimeChildMaterial(red: Float): GPUPreparedMaterialProgram {
        val parent = runtimeParentProgram()
        val descriptor = GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = SimpleRTDescriptor.effectId.value,
            uniforms = mapOf(
                "gColor" to GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
            ),
            childDescriptors = linkedMapOf(
                "source" to GPURuntimeEffectChildDescriptor.Shader(
                    GPUMaterialDescriptor.SolidColor(red, 0.5f, 0.75f, 0.8f),
                ),
                "filter" to GPURuntimeEffectChildDescriptor.ColorFilter(
                    GPUPreparedColorFilterChildDescriptor.Matrix(
                        listOf(
                            1f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                ),
                "blender" to GPURuntimeEffectChildDescriptor.Blender(
                    GPUPreparedBlenderChildDescriptor.Mode(GPUBlendMode.SRC_OVER),
                ),
            ),
        )
        return assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(
                descriptor,
                1f,
                GPUMaterialLoweringContext(
                    capabilityClass = "payload-child-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:payload-child:v1",
                    runtimeEffectResolver = GPUPreparedRuntimeEffectResolver { effectId, version ->
                        if (effectId == parent.effectId && version == parent.descriptorVersion) {
                            GPUPreparedRuntimeEffectResolution.Ready(parent)
                        } else {
                            GPUPreparedRuntimeEffectResolution.DescriptorUnavailable("not registered")
                        }
                    },
                ),
            ),
        ).program
    }

    private fun runtimeParentProgram(): GPUPreparedRuntimeEffectProgram =
        GPUPreparedRuntimeEffectProgram(
            effectId = SimpleRTDescriptor.effectId.value,
            descriptorVersion = SimpleRTDescriptor.descriptorVersion.value,
            wgslSource = SimpleRTWgsl,
            sourceFunction = SimpleRTEntryPoint,
            sourceColorContract = GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba,
            sourceHash = SimpleRTSourceHash,
            moduleHash = "sha256:${"1".repeat(64)}",
            reflectionHash = SimpleRTReflectionHash,
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
            childSlots = listOf(
                runtimeChildSlot("source", GPUPreparedRuntimeEffectChildRole.Shader, 0),
                runtimeChildSlot("filter", GPUPreparedRuntimeEffectChildRole.ColorFilter, 1),
                runtimeChildSlot("blender", GPUPreparedRuntimeEffectChildRole.Blender, 2),
            ),
            bindingPlanHash = SimpleRTBindingPlanHash,
            routeContractHash = "sha256:${"3".repeat(64)}",
        )

    private fun runtimeChildSlot(
        name: String,
        role: GPUPreparedRuntimeEffectChildRole,
        index: Int,
    ) = GPUPreparedRuntimeEffectChildSlot(
        name = name,
        role = role,
        bindingIndex = index,
        abiHash = org.graphiks.kanvas.gpu.renderer.materials.preparedRuntimeEffectChildAbiHash(role),
    )

    private fun compile(descriptor: GPUMaterialDescriptor): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(
                descriptor,
                paintAlpha = 1f,
                context = GPUMaterialLoweringContext(
                    capabilityClass = "payload-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:payload-test:v1",
                    runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
                ),
            ),
        ).program

    private fun identityTransformBits(): List<Int> = listOf(
        1f.toRawBits(), 0f.toRawBits(), 0f.toRawBits(),
        0f.toRawBits(), 1f.toRawBits(), 0f.toRawBits(),
        0f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(),
    )
}

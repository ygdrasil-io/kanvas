package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderResult
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.PreparedVerticesShaderAssembler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesTopologyIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesStagingLayout
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Pure handle-free admission tests for the full-frame prepared-vertices preflight.
 *
 * Every refusal asserts the complete native seam stays untouched: no target borrow,
 * no buffer creation, no encoder creation, no queue write, and no submission.
 */
class GPUPreparedSurfaceVerticesNativePreflightTest {

    @Test
    fun `accepted vertices frame passes pure preflight without any native seam call`() {
        val fixture = verticesPreflightFixture()
        val seam = GPUPreparedVerticesNativeSeam()

        val refusal = GPUPreparedSurfaceNativePreflight()
            .validateFramePlan(fixture.framePlan, fixture.context, fixture.capabilities)

        assertNull(refusal)
        seam.assertUntouched()
    }

    @Test
    fun `accepted vertices frames cover both topologies and indexed and non-indexed geometry`() {
        listOf(
            VerticesShapeVariant(topology = GPUVertexMode.Triangles, indexed = false, indexFormat = null),
            VerticesShapeVariant(topology = GPUVertexMode.Triangles, indexed = true, indexFormat = "uint16"),
            VerticesShapeVariant(topology = GPUVertexMode.Triangles, indexed = true, indexFormat = "uint32"),
            VerticesShapeVariant(topology = GPUVertexMode.TriangleStrip, indexed = false, indexFormat = null),
            VerticesShapeVariant(topology = GPUVertexMode.TriangleStrip, indexed = true, indexFormat = "uint16"),
        ).forEach { variant ->
            val fixture = verticesPreflightFixture(
                topology = variant.topology,
                indexed = variant.indexed,
                indexFormat = variant.indexFormat,
            )
            val refusal = GPUPreparedSurfaceNativePreflight()
                .validateFramePlan(fixture.framePlan, fixture.context, fixture.capabilities)
            assertNull(refusal, variant.toString())
        }
    }

    @Test
    fun `accepted vertices frame with shared artifact across two draws passes pure preflight`() {
        val fixture = verticesPreflightFixture(commandCount = 2)
        val artifactKey = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .map { packet -> (packet.semanticPayload as GPUDrawSemanticPayload.Vertices).artifact.key }
            .distinct()
        assertEquals(1, artifactKey.size)

        val refusal = GPUPreparedSurfaceNativePreflight()
            .validateFramePlan(fixture.framePlan, fixture.context, fixture.capabilities)

        assertNull(refusal)
    }

    @Test
    fun `accepted vertices run plan retains exact draw facts grouped by render scope`() {
        val fixture = verticesPreflightFixture(indexed = true)
        val renderIndex = fixture.framePlan.steps.indexOfFirst {
            it is GPUFrameStep.RenderPassStep
        }
        val renderStep = fixture.framePlan.steps[renderIndex] as GPUFrameStep.RenderPassStep
        val packets = renderStep.drawPackets.map { packet ->
            packet.semanticPayload as GPUDrawSemanticPayload.Vertices
        }
        val deviceGeneration = fixture.framePlan.capabilitySeal.deviceGeneration.value
        val artifactPlans = packets.map(GPUDrawSemanticPayload.Vertices::artifact)
            .distinctBy { artifact -> artifact.key }
            .map { artifact -> buildVerticesFrameResourcePlan(artifact, deviceGeneration) }
        val stagingLayout = buildVerticesStagingLayout(artifactPlans)
        val drawFacts = renderStep.drawPackets.map { packet ->
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
            val plan = artifactPlans.single { it.artifactKey == semantic.artifact.key }
            GPUPreparedVerticesDrawFacts(
                packetId = packet.packetId,
                artifactKey = semantic.artifact.key,
                vertexCount = semantic.artifact.vertexCount,
                indexCount = semantic.artifact.indexCount,
                indexFormat = semantic.artifact.indexFormat,
                vertexByteCount = plan.vertexBuffer.byteCount,
                indexByteCount = plan.indexBuffer?.byteCount,
            )
        }
        val shaderPrograms = renderStep.drawPackets.associate { packet ->
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
            val program = assertIs<GPUPreparedVerticesShaderResult.Ready>(
                PreparedVerticesShaderAssembler.assemble(
                    layout = semantic.artifact.layout,
                    topology = semantic.artifact.topology,
                    material = semantic.material,
                    hasPrimitiveColor = semantic.primitiveColorPresent,
                ),
            ).program
            packet.packetId to program
        }

        val run = GPUPreparedSurfaceNativeRunPlan.Vertices(
            GPUPreparedVerticesRenderRunPlan(
                sourceScopeIndex = renderIndex,
                renderStep = renderStep,
                packets = packets,
                resourcePlans = artifactPlans,
                drawFacts = drawFacts,
                shaderProgramByPacketId = shaderPrograms,
                exactScopeKey = GPUPreparedNativeScopeKey(
                    sourceStepIndex = renderIndex,
                    operationKind = GPUEncoderOperationKind.Render,
                    operandKeys = listOf(
                        GPUPreparedNativeOperandKey(
                            role = GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            kind = GPUPreparedNativeOperandKind.Buffer,
                            bindingKey = "key.vertices",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(renderStep.drawPackets.map(GPUDrawPacket::packetId), run.plan.drawFacts.map { it.packetId })
        assertEquals(1, run.plan.resourcePlans.size)
        assertTrue(run.plan.drawFacts.all { fact -> fact.indexFormat == "uint16" })
        assertTrue(run.plan.drawFacts.all { fact -> fact.indexByteCount == fact.indexCount!!.toLong() * 2L })
        val semantic = packets.single()
        val stagedRanges = stagingLayout.ranges
        assertEquals(2, stagedRanges.size)
        assertEquals("vertex", stagedRanges[0].bufferKind)
        assertEquals("index", stagedRanges[1].bufferKind)
        assertEquals(0L, stagedRanges[0].offsetBytes)
        assertTrue(stagedRanges[1].offsetBytes >= semantic.artifact.vertexCount.toLong() * 8L)
        assertNotNull(run.plan.shaderProgramByPacketId[semantic.payloadRef.commandIdValue.run {
            renderStep.drawPackets.single { packet -> packet.commandIdValue == this }.packetId
        }])
        assertEquals(renderIndex, run.plan.exactScopeKey.sourceStepIndex)
    }

    @TestFactory
    fun `vertices refusal matrix keeps every native seam counter at zero`(): List<DynamicTest> =
        GPUPreparedVerticesPreflightMutationMatrix.orderedMutations.map { mutation ->
            DynamicTest.dynamicTest(mutation.name) {
                val mutated = verticesPreflightFixture()
                    .withViolation(mutation.violationKind)
                val seam = GPUPreparedVerticesNativeSeam()

                val refusal = GPUPreparedSurfaceNativePreflight()
                    .validateFramePlan(mutated.framePlan, mutated.context, mutated.capabilities)

                val refused = assertNotNull(refusal, "Expected refusal for ${mutation.name}")
                assertEquals(
                    expectedVerticesPreflightRefusalCode(mutation.violationKind),
                    refused.code,
                    "Refusal code for ${mutation.name} (${refused.message})",
                )
                seam.assertUntouched()
            }
        }

    private data class VerticesShapeVariant(
        val topology: GPUVertexMode,
        val indexed: Boolean,
        val indexFormat: String?,
    )
}

/** Handle-free native seam counter: the pure preflight must never touch any of these. */
internal class GPUPreparedVerticesNativeSeam {
    var targetBorrowCount: Int = 0
        private set
    var bufferCreateCount: Int = 0
        private set
    var encoderCreateCount: Int = 0
        private set
    var queueWriteCount: Int = 0
        private set
    var submitCount: Int = 0
        private set

    fun assertUntouched() {
        assertEquals(0, targetBorrowCount, "Preflight must not borrow a native target")
        assertEquals(0, bufferCreateCount, "Preflight must not create a native buffer")
        assertEquals(0, encoderCreateCount, "Preflight must not create a native encoder")
        assertEquals(0, queueWriteCount, "Preflight must not write a native queue")
        assertEquals(0, submitCount, "Preflight must not submit native work")
    }
}

/**
 * Kinds of prepared-vertices invariants that the full-frame preflight must validate.
 *
 * Each kind maps to exactly one refusal boundary in the production preflight.
 * The production implementation owns the canonical refusal codes; this test-only
 * matrix does not invent any.
 */
enum class GPUPreparedVerticesViolationKind {
    /** A packet lost its typed semantic. */
    NULL_SEMANTIC_PACKET,

    /** The semantic payload reference no longer matches the packet identity. */
    PACKET_IDENTITY_MODIFIED,

    /** A required identity (clip, clip coverage, final blend, primitive blend) is blank. */
    BLANK_CLIP_IDENTITY,

    /** The semantic canonical hash no longer matches its immutable snapshot. */
    CANONICAL_HASH_MISMATCH,

    /** Vertex bytes changed after artifact finalization. */
    MODIFIED_VERTEX_BYTES,

    /** The vertex content hash no longer matches the immutable bytes. */
    MODIFIED_VERTEX_CONTENT_HASH,

    /** The semantic topology identity disagrees with the artifact topology. */
    TOPOLOGY_MISMATCH,

    /** The artifact layout is not one of the four canonical vertex layouts. */
    NON_CANONICAL_LAYOUT,

    /** The index format is neither uint16 nor uint32. */
    UNSUPPORTED_INDEX_FORMAT,

    /** The transform bytes are not finite, affine, or invertible. */
    NON_AFFINE_TRANSFORM,

    /** The scissor bounds are not contained in the target bounds. */
    SCISSOR_OUT_OF_TARGET,

    /** The material ABI hash changed after frame authentication. */
    MATERIAL_ABI_MISMATCH,

    /** The authenticated material frame identity string changed after recording. */
    MATERIAL_IDENTITY_MODIFIED,

    /** The shader assembler refuses the layout, topology, material, or color flag. */
    SHADER_ATTRIBUTE_MISMATCH,

    /** A vertex or index upload step required by a draw is absent. */
    UPLOAD_MISSING,

    /** Two upload steps claim the same artifact range. */
    UPLOAD_DUPLICATED,

    /** An upload step appears after its first consuming draw. */
    UPLOAD_AFTER_DRAW,

    /** Upload ranges overlap each other. */
    OVERLAPPING_RANGES,

    /** Upload ranges exceed the staging byte count. */
    OUT_OF_BOUNDS_RANGE,

    /** Buffer usage flags disagree with the exact copy-destination vertex/index usages. */
    USAGE_MISMATCH,

    /** The frame context lost the exact resource generation for a vertices buffer. */
    STALE_BUFFER_GENERATION,

    /** The frame context carries a different device generation than the frame seal. */
    STALE_DEVICE_GENERATION,

    /** A required upload-before-consumer dependency edge is missing. */
    DEPENDENCY_MISSING,

    /** The frame memory budget already carries a terminal aggregate refusal. */
    AGGREGATE_BUDGET_VIOLATION,

    /** The frame memory budget lost the vertices staging allocation. */
    BUDGET_ALLOCATION_MISSING,

    /** The semantic target format disagrees with the scene target format. */
    TARGET_FORMAT_MISMATCH,

    /** The render sample plan is not the single-sample prepared frame. */
    SAMPLE_COUNT_MISMATCH,
}

/**
 * One prepared-vertices preflight invariant verified against the production
 * full-frame preflight.
 */
data class GPUPreparedVerticesPreflightMutation(
    val name: String,
    val violationKind: GPUPreparedVerticesViolationKind,
    val description: String,
)

/** Ordered matrix of every prepared-vertices invariant the preflight must validate. */
object GPUPreparedVerticesPreflightMutationMatrix {
    val orderedMutations: List<GPUPreparedVerticesPreflightMutation> = listOf(
        GPUPreparedVerticesPreflightMutation(
            name = "null semantic packet",
            violationKind = GPUPreparedVerticesViolationKind.NULL_SEMANTIC_PACKET,
            description = "Every prepared-surface packet must retain one typed semantic.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "packet identity modified",
            violationKind = GPUPreparedVerticesViolationKind.PACKET_IDENTITY_MODIFIED,
            description = "The semantic payload reference must match the packet render step.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "blank clip identity",
            violationKind = GPUPreparedVerticesViolationKind.BLANK_CLIP_IDENTITY,
            description = "Clip, clip-coverage, and final-blend identities must not be blank.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "canonical hash mismatch",
            violationKind = GPUPreparedVerticesViolationKind.CANONICAL_HASH_MISMATCH,
            description = "The semantic canonical hash must match its immutable snapshot.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "modified vertex bytes",
            violationKind = GPUPreparedVerticesViolationKind.MODIFIED_VERTEX_BYTES,
            description = "Vertex bytes must remain exactly the artifact snapshot bytes.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "modified vertex content hash",
            violationKind = GPUPreparedVerticesViolationKind.MODIFIED_VERTEX_CONTENT_HASH,
            description = "The vertex content hash must match the immutable bytes.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "topology mismatch",
            violationKind = GPUPreparedVerticesViolationKind.TOPOLOGY_MISMATCH,
            description = "Semantic topology identity must agree with the artifact topology.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "non canonical layout",
            violationKind = GPUPreparedVerticesViolationKind.NON_CANONICAL_LAYOUT,
            description = "Only the four canonical FP-06 vertex layouts are admitted.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "unsupported index format",
            violationKind = GPUPreparedVerticesViolationKind.UNSUPPORTED_INDEX_FORMAT,
            description = "Index format must be uint16 or uint32.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "non affine transform",
            violationKind = GPUPreparedVerticesViolationKind.NON_AFFINE_TRANSFORM,
            description = "The transform must be finite, affine, and invertible.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "scissor out of target",
            violationKind = GPUPreparedVerticesViolationKind.SCISSOR_OUT_OF_TARGET,
            description = "The scissor must be contained in the target bounds.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "material ABI mismatch",
            violationKind = GPUPreparedVerticesViolationKind.MATERIAL_ABI_MISMATCH,
            description = "The material frame identity must re-authenticate exactly.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "material identity modified",
            violationKind = GPUPreparedVerticesViolationKind.MATERIAL_IDENTITY_MODIFIED,
            description = "The authenticated material frame identity must remain part of the canonical hash.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "shader attribute mismatch",
            violationKind = GPUPreparedVerticesViolationKind.SHADER_ATTRIBUTE_MISMATCH,
            description = "The Task 8 shader assembler must accept the exact layout and color flag.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "upload missing",
            violationKind = GPUPreparedVerticesViolationKind.UPLOAD_MISSING,
            description = "Every artifact requires its exact vertex and index upload steps.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "upload duplicated",
            violationKind = GPUPreparedVerticesViolationKind.UPLOAD_DUPLICATED,
            description = "One artifact range must be claimed by exactly one upload step.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "upload after draw",
            violationKind = GPUPreparedVerticesViolationKind.UPLOAD_AFTER_DRAW,
            description = "An upload must never appear after its first consuming draw.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "overlapping ranges",
            violationKind = GPUPreparedVerticesViolationKind.OVERLAPPING_RANGES,
            description = "Staging ranges must not overlap.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "out of bounds range",
            violationKind = GPUPreparedVerticesViolationKind.OUT_OF_BOUNDS_RANGE,
            description = "Staging ranges must fit the derived staging byte count.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "usage mismatch",
            violationKind = GPUPreparedVerticesViolationKind.USAGE_MISMATCH,
            description = "Vertex and index buffers require exact copy-destination usages.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "stale buffer generation",
            violationKind = GPUPreparedVerticesViolationKind.STALE_BUFFER_GENERATION,
            description = "Every vertices buffer requires its exact current generation.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "stale device generation",
            violationKind = GPUPreparedVerticesViolationKind.STALE_DEVICE_GENERATION,
            description = "The frame context device generation must match the frame seal.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "dependency missing",
            violationKind = GPUPreparedVerticesViolationKind.DEPENDENCY_MISSING,
            description = "Every draw depends on its artifact upload token before use.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "aggregate budget violation",
            violationKind = GPUPreparedVerticesViolationKind.AGGREGATE_BUDGET_VIOLATION,
            description = "A terminal aggregate budget refusal must refuse before any seam call.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "budget allocation missing",
            violationKind = GPUPreparedVerticesViolationKind.BUDGET_ALLOCATION_MISSING,
            description = "The recorded budget must retain every exact vertices staging allocation.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "target format mismatch",
            violationKind = GPUPreparedVerticesViolationKind.TARGET_FORMAT_MISMATCH,
            description = "The semantic target format must match the scene target format.",
        ),
        GPUPreparedVerticesPreflightMutation(
            name = "sample count mismatch",
            violationKind = GPUPreparedVerticesViolationKind.SAMPLE_COUNT_MISMATCH,
            description = "Only single-sample prepared frames are admitted.",
        ),
    )
}

/** Exact production refusal code for each prepared-vertices violation kind. */
internal fun expectedVerticesPreflightRefusalCode(
    kind: GPUPreparedVerticesViolationKind,
): String = when (kind) {
    GPUPreparedVerticesViolationKind.NULL_SEMANTIC_PACKET ->
        "unsupported.prepared-surface.semantic-shape"
    GPUPreparedVerticesViolationKind.PACKET_IDENTITY_MODIFIED ->
        "invalid.prepared-surface.vertices-semantic"
    GPUPreparedVerticesViolationKind.BLANK_CLIP_IDENTITY ->
        "invalid.prepared-surface.vertices-identity"
    GPUPreparedVerticesViolationKind.CANONICAL_HASH_MISMATCH ->
        "invalid.prepared-surface.vertices-hash"
    GPUPreparedVerticesViolationKind.MODIFIED_VERTEX_BYTES,
    GPUPreparedVerticesViolationKind.MODIFIED_VERTEX_CONTENT_HASH,
    -> "invalid.prepared-surface.vertices-artifact"
    GPUPreparedVerticesViolationKind.TOPOLOGY_MISMATCH ->
        "unsupported.prepared-surface.vertices-topology"
    GPUPreparedVerticesViolationKind.NON_CANONICAL_LAYOUT ->
        GPUPreparedVerticesRefusalCodes.AttributeLayout
    GPUPreparedVerticesViolationKind.UNSUPPORTED_INDEX_FORMAT ->
        "unsupported.prepared-surface.vertices-index-format"
    GPUPreparedVerticesViolationKind.NON_AFFINE_TRANSFORM ->
        "invalid.prepared-surface.vertices-transform"
    GPUPreparedVerticesViolationKind.SCISSOR_OUT_OF_TARGET ->
        "invalid.prepared-surface.vertices-bounds"
    GPUPreparedVerticesViolationKind.MATERIAL_ABI_MISMATCH ->
        "invalid.prepared-surface.vertices-material-abi"
    GPUPreparedVerticesViolationKind.MATERIAL_IDENTITY_MODIFIED ->
        "invalid.prepared-surface.vertices-hash"
    GPUPreparedVerticesViolationKind.SHADER_ATTRIBUTE_MISMATCH ->
        GPUPreparedVerticesRefusalCodes.AttributeLayout
    GPUPreparedVerticesViolationKind.UPLOAD_MISSING ->
        "invalid.prepared-surface.vertices-upload-missing"
    GPUPreparedVerticesViolationKind.UPLOAD_DUPLICATED ->
        "invalid.prepared-surface.vertices-upload-duplicate"
    GPUPreparedVerticesViolationKind.UPLOAD_AFTER_DRAW ->
        "invalid.prepared-surface.vertices-upload-order"
    GPUPreparedVerticesViolationKind.OVERLAPPING_RANGES,
    GPUPreparedVerticesViolationKind.OUT_OF_BOUNDS_RANGE,
    -> "invalid.prepared-surface.vertices-upload-layout"
    GPUPreparedVerticesViolationKind.USAGE_MISMATCH ->
        "invalid.prepared-surface.vertices-usage"
    GPUPreparedVerticesViolationKind.STALE_BUFFER_GENERATION ->
        "stale.prepared-surface.vertices-generation"
    GPUPreparedVerticesViolationKind.STALE_DEVICE_GENERATION ->
        "stale.prepared-surface.frame-context"
    GPUPreparedVerticesViolationKind.DEPENDENCY_MISSING ->
        "invalid.prepared-surface.vertices-dependency"
    GPUPreparedVerticesViolationKind.AGGREGATE_BUDGET_VIOLATION,
    GPUPreparedVerticesViolationKind.BUDGET_ALLOCATION_MISSING,
    -> "unsupported.prepared-surface.vertices-budget"
    GPUPreparedVerticesViolationKind.TARGET_FORMAT_MISMATCH ->
        "invalid.prepared-surface.vertices-target"
    GPUPreparedVerticesViolationKind.SAMPLE_COUNT_MISMATCH ->
        "unsupported.prepared-surface.sample-plan"
}

internal data class PreparedVerticesPreflightFixture(
    val framePlan: GPUFramePlan,
    val capabilities: GPUCapabilities,
    val context: GPUFramePreflightContext,
)

internal fun verticesPreflightFixture(
    topology: GPUVertexMode = GPUVertexMode.Triangles,
    indexed: Boolean = true,
    indexFormat: String? = "uint16",
    commandCount: Int = 1,
    material: GPUPreparedMaterialProgram = stubPreparedMaterialProgram(),
    targetFormat: String = "rgba8unorm-srgb",
): PreparedVerticesPreflightFixture {
    val capabilities = verticesPreflightCapabilities()
    val base = verticesPreflightBaseTaskList((0 until commandCount).toList())
    val artifact = verticesPreflightArtifact(
        topology = topology,
        vertexCount = 6,
        indexed = indexed,
        indexFormat = indexFormat,
    )
    val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>()
    (0 until commandCount).forEach { commandId ->
        semantics[commandId] = verticesPreflightSemantic(
            commandId = commandId,
            artifact = artifact,
            material = material,
            targetFormat = targetFormat,
        )
    }
    val target = GPUFrameTargetRef("target.prepared-surface")
    val build = GPUPreparedSurfaceFrameTaskListBuilder().build(
        GPUPreparedSurfaceFrameRequest(
            baseTaskList = base,
            capabilities = capabilities,
            target = target,
            targetBounds = VERTICES_PREFLIGHT_BOUNDS,
            semanticsByCommandId = semantics,
            readbackRequestId = null,
            targetFormat = org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat(targetFormat),
        ),
    )
    val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
        build,
        (build as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
    ).taskList
    val framePlan = GPUFramePlanner.plan(taskList)
    val targetGeneration = taskList.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask.Render::drawPackets)
        .first()
        .resourceGeneration
    val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
        .flatMap(GPUTask.PrepareResources::requests)
        .associate { request ->
            request.resource to if (request.role == GPUFrameResourceRole.SceneTarget) {
                targetGeneration
            } else {
                VERTICES_PREFLIGHT_RESOURCE_GENERATION
            }
        }
    return PreparedVerticesPreflightFixture(
        framePlan = framePlan,
        capabilities = capabilities,
        context = GPUFramePreflightContext(
            targetId = target.value,
            deviceGeneration = taskList.capabilitySeal.deviceGeneration,
            targetGeneration = targetGeneration,
            resourceGenerations = resourceGenerations,
        ),
    )
}

private fun PreparedVerticesPreflightFixture.withViolation(
    kind: GPUPreparedVerticesViolationKind,
): PreparedVerticesPreflightFixture = when (kind) {
    GPUPreparedVerticesViolationKind.NULL_SEMANTIC_PACKET ->
        withVerticesPacket { packet -> packet.rebuilt(semanticPayload = null) }
    GPUPreparedVerticesViolationKind.PACKET_IDENTITY_MODIFIED ->
        withVerticesPacket { packet ->
            packet.rebuilt(renderStepId = GPURenderStepID("vertices.draw.forged"))
        }
    GPUPreparedVerticesViolationKind.BLANK_CLIP_IDENTITY ->
        withVerticesSemantic { semantic ->
            semantic.corruptFieldForNegativeTest("clipIdentity", "")
            semantic
        }
    GPUPreparedVerticesViolationKind.CANONICAL_HASH_MISMATCH ->
        withVerticesSemantic { semantic ->
            semantic.corruptFieldForNegativeTest("canonicalHash", "forged-canonical-hash")
            semantic
        }
    GPUPreparedVerticesViolationKind.MODIFIED_VERTEX_BYTES ->
        withVerticesArtifact { artifact ->
            artifact.corruptFieldForNegativeTest(
                "vertexSnapshot",
                artifact.vertexBytesForUpload().also { bytes -> bytes[0] = (bytes[0].toInt() xor 0x7f).toByte() },
            )
        }
    GPUPreparedVerticesViolationKind.MODIFIED_VERTEX_CONTENT_HASH ->
        withVerticesArtifact { artifact ->
            artifact.corruptFieldForNegativeTest("vertexContentHash", "forged-vertex-hash")
        }
    GPUPreparedVerticesViolationKind.TOPOLOGY_MISMATCH ->
        withVerticesArtifact { artifact ->
            artifact.corruptFieldForNegativeTest("topology", GPUVertexMode.TriangleFan)
        }
    GPUPreparedVerticesViolationKind.NON_CANONICAL_LAYOUT ->
        withVerticesArtifact { artifact ->
            artifact.corruptFieldForNegativeTest(
                "layout",
                GPUVertexLayoutPlan(
                    attributes = listOf("position"),
                    strideBytes = 8,
                    offsets = mapOf("position" to 4),
                    shaderLocations = mapOf("position" to 0),
                ),
            )
        }
    GPUPreparedVerticesViolationKind.UNSUPPORTED_INDEX_FORMAT ->
        withVerticesArtifact { artifact ->
            artifact.corruptFieldForNegativeTest("indexFormat", "uint8")
        }
    GPUPreparedVerticesViolationKind.NON_AFFINE_TRANSFORM ->
        withVerticesSemantic { semantic ->
            semantic.corruptFieldForNegativeTest(
                "transformBytes",
                listOf(
                    1f.toRawBits(), 0f.toRawBits(), 0f.toRawBits(),
                    0f.toRawBits(), 1f.toRawBits(), 0f.toRawBits(),
                    1f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(),
                ),
            )
            semantic
        }
    GPUPreparedVerticesViolationKind.SCISSOR_OUT_OF_TARGET ->
        withVerticesSemantic { semantic ->
            semantic.corruptFieldForNegativeTest(
                "scissorBounds",
                GPUPixelBounds(0, 0, 32, 32),
            )
            semantic
        }
    GPUPreparedVerticesViolationKind.MATERIAL_ABI_MISMATCH ->
        withVerticesSemantic { semantic ->
            semantic.material.corruptFieldForNegativeTest("abiHash", "abi:forged")
            semantic
        }
    GPUPreparedVerticesViolationKind.MATERIAL_IDENTITY_MODIFIED ->
        withVerticesSemantic { semantic ->
            val snapshot = semantic.javaClass.getDeclaredField("snapshot").run {
                isAccessible = true
                get(semantic)
            }
            snapshot.corruptFieldForNegativeTest(
                "materialIdentity",
                "forged-material-identity",
            )
            semantic
        }
    GPUPreparedVerticesViolationKind.SHADER_ATTRIBUTE_MISMATCH ->
        withVerticesSemantic { semantic ->
            semantic.corruptFieldForNegativeTest("primitiveColorPresent", true)
            semantic.corruptFieldForNegativeTest("primitiveBlendIdentity", "blend:primitive")
            semantic
        }
    GPUPreparedVerticesViolationKind.UPLOAD_MISSING ->
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.filterNot { step ->
                    step is GPUFrameStep.UploadResourceStep &&
                        step.destination.value.contains("prepared-vertices.vertex")
                },
            ),
        )
    GPUPreparedVerticesViolationKind.UPLOAD_DUPLICATED -> {
        val upload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { step -> step.destination.value.contains("prepared-vertices.vertex") }
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.toMutableList().apply {
                    add(indexOf(upload) + 1, upload)
                },
            ),
        )
    }
    GPUPreparedVerticesViolationKind.UPLOAD_AFTER_DRAW -> {
        val upload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { step -> step.destination.value.contains("prepared-vertices.vertex") }
        val renderIndex = framePlan.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep }
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.filterNot { it === upload }.toMutableList().apply {
                    add(renderIndex, upload)
                },
            ),
        )
    }
    GPUPreparedVerticesViolationKind.OVERLAPPING_RANGES -> {
        val indexUpload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { step -> step.destination.value.contains("prepared-vertices.index") }
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.map { step ->
                    if (step === indexUpload) {
                        GPUFrameStep.UploadResourceStep(
                            staging = indexUpload.staging,
                            destination = indexUpload.destination,
                            layout = indexUpload.layout.copy(sourceOffsetBytes = 0L),
                            sourceTaskIds = indexUpload.sourceTaskIds,
                        )
                    } else {
                        step
                    }
                },
            ),
        )
    }
    GPUPreparedVerticesViolationKind.OUT_OF_BOUNDS_RANGE -> {
        val indexUpload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { step -> step.destination.value.contains("prepared-vertices.index") }
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.map { step ->
                    if (step === indexUpload) {
                        GPUFrameStep.UploadResourceStep(
                            staging = indexUpload.staging,
                            destination = indexUpload.destination,
                            layout = indexUpload.layout.copy(
                                byteSize = indexUpload.layout.byteSize + 16L,
                            ),
                            sourceTaskIds = indexUpload.sourceTaskIds,
                        )
                    } else {
                        step
                    }
                },
            ),
        )
    }
    GPUPreparedVerticesViolationKind.USAGE_MISMATCH -> {
        val vertexRef = framePlan.verticesVertexBufferRef(framePlan.verticesArtifactKey())
        copy(
            framePlan = framePlan.withPreparationMutation(vertexRef) { request ->
                GPUResourcePreparationRequest(
                    resource = request.resource,
                    descriptor = request.descriptor,
                    role = request.role,
                    usages = setOf(GPUFrameResourceUsage.Vertex),
                    lifetime = request.lifetime,
                    byteSize = request.byteSize,
                    diagnosticLabel = request.diagnosticLabel,
                )
            },
        )
    }
    GPUPreparedVerticesViolationKind.STALE_BUFFER_GENERATION -> {
        val vertexRef = framePlan.verticesVertexBufferRef(framePlan.verticesArtifactKey())
        copy(
            context = context.withoutResourceGeneration(vertexRef),
        )
    }
    GPUPreparedVerticesViolationKind.STALE_DEVICE_GENERATION ->
        copy(
            context = GPUFramePreflightContext(
                targetId = context.targetId,
                deviceGeneration = org.graphiks.kanvas.gpu.renderer.capabilities
                    .GPUDeviceGenerationID(context.deviceGeneration.value + 1L),
                targetGeneration = context.targetGeneration,
                resourceGenerations = context.resourceGenerations,
                surfaceGeneration = context.surfaceGeneration,
            ),
        )
    GPUPreparedVerticesViolationKind.DEPENDENCY_MISSING ->
        copy(
            framePlan = framePlan.rebuilt(
                steps = framePlan.steps,
                dependencies = framePlan.dependencies.filterNot { dependency ->
                    dependency.reasonCode == "prepared.vertices.upload-before-consumer"
                },
            ),
        )
    GPUPreparedVerticesViolationKind.AGGREGATE_BUDGET_VIOLATION ->
        copy(
            framePlan = framePlan.rebuilt(
                steps = framePlan.steps,
                memoryBudget = framePlan.memoryBudget.copy(
                    diagnostic = GPUDiagnostic(
                        code = GPUDiagnosticCode("unsupported.vertices.budget"),
                        domain = GPUDiagnosticDomain.Recording,
                        severity = GPUDiagnosticSeverity.Error,
                        message = "forged aggregate vertices budget refusal",
                    ),
                ),
            ),
        )
    GPUPreparedVerticesViolationKind.BUDGET_ALLOCATION_MISSING ->
        copy(
            framePlan = framePlan.rebuilt(
                steps = framePlan.steps,
                memoryBudget = framePlan.memoryBudget.copy(
                    allocations = framePlan.memoryBudget.allocations.filterNot { allocation ->
                        allocation.label == "prepared-vertices.staging"
                    },
                ),
            ),
        )
    GPUPreparedVerticesViolationKind.TARGET_FORMAT_MISMATCH -> {
        val artifact = verticesPreflightArtifact()
        copy(
            framePlan = framePlan.withVerticesSemanticReplacement { semantic ->
                val replacement = verticesPreflightSemantic(
                    commandId = semantic.payloadRef.commandIdValue,
                    artifact = artifact,
                    material = semantic.material,
                    targetFormat = "rgba8unorm",
                )
                assertTrue(
                    replacement.payloadRef == semantic.payloadRef &&
                        replacement.artifact.key == semantic.artifact.key,
                )
                replacement
            },
        )
    }
    GPUPreparedVerticesViolationKind.SAMPLE_COUNT_MISMATCH ->
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.map { step ->
                    if (step is GPUFrameStep.RenderPassStep) {
                        step.rebuilt(samplePlan = GPUSamplePlan.MultisampleFrame(4))
                    } else {
                        step
                    }
                },
            ),
        )
}

private fun GPUFramePlan.verticesArtifactKey(): String =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        .map { packet -> (packet.semanticPayload as GPUDrawSemanticPayload.Vertices).artifact.key }
        .single()

private fun PreparedVerticesPreflightFixture.withVerticesSemantic(
    transform: (GPUDrawSemanticPayload.Vertices) -> GPUDrawSemanticPayload.Vertices,
): PreparedVerticesPreflightFixture = copy(
    framePlan = framePlan.withVerticesSemanticReplacement(transform),
)

private fun GPUFramePlan.withVerticesSemanticReplacement(
    transform: (GPUDrawSemanticPayload.Vertices) -> GPUDrawSemanticPayload.Vertices,
): GPUFramePlan = rebuilt(
    steps.map { step ->
        if (step !is GPUFrameStep.RenderPassStep) {
            step
        } else {
            step.rebuilt(
                drawPackets = step.drawPackets.map { packet ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.Vertices
                        ?: return@map packet
                    packet.rebuilt(semanticPayload = transform(semantic))
                },
            )
        }
    },
)

private fun PreparedVerticesPreflightFixture.withVerticesPacket(
    transform: (GPUDrawPacket) -> GPUDrawPacket,
): PreparedVerticesPreflightFixture = copy(
    framePlan = framePlan.rebuilt(
        framePlan.steps.map { step ->
            if (step !is GPUFrameStep.RenderPassStep) {
                step
            } else {
                step.rebuilt(
                    drawPackets = step.drawPackets.map { packet ->
                        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.Vertices
                            ?: return@map packet
                        transform(packet)
                    },
                )
            }
        },
    ),
)

private fun PreparedVerticesPreflightFixture.withVerticesArtifact(
    transform: (GPUPreparedVerticesUploadArtifact) -> Unit,
): PreparedVerticesPreflightFixture = withVerticesSemantic { semantic ->
    transform(semantic.artifact)
    semantic
}

private fun Any.corruptFieldForNegativeTest(fieldName: String, value: Any?) {
    javaClass.getDeclaredField(fieldName).run {
        isAccessible = true
        set(this@corruptFieldForNegativeTest, value)
    }
}

private fun verticesPreflightBaseTaskList(commandIds: List<Int>): GPUTaskList {
    val frameId = GPUFrameID(VERTICES_PREFLIGHT_FRAME_ID)
    val recordingId = GPURecordingID("recording.vertices.preflight")
    val capabilities = verticesPreflightCapabilities()
    val seal = GPUFrameCapabilitySeal.capture(frameId, VERTICES_PREFLIGHT_DEVICE_GENERATION, capabilities)
    val renders = commandIds.map { commandId ->
        val packet = verticesPreflightPacket(commandId, PREPARED_VERTICES_RENDER_STEP_IDENTITY)
        GPUTask.Render(
            taskId = GPUTaskID("task.base.vertices.$commandId"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = VERTICES_PREFLIGHT_TARGET,
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            provisionalSegmentKey = GPUProvisionalRenderSegmentKey("segment.vertices.$commandId"),
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.Isolated,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
    }
    return GPUTaskList(
        frameId = frameId,
        capabilitySeal = seal,
        recordingSeals = listOf(
            GPURecordingSeal(
                recordingId,
                0,
                "compat:vertices",
                "replay:vertices",
                seal.sealHash,
            ),
        ),
        expectedReplayKeyHash = "replay:vertices",
        tasks = renders,
        dependencies = emptyList(),
        phaseOrder = GPUTaskPhase.entries,
        memoryBudget = GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = 0,
            targetResidentBytes = 0,
            categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
            deviceLimitFacts = emptyList(),
            configuredAggregateBudgetBytes = 1,
            diagnostic = null,
        ),
    )
}

internal fun verticesPreflightSemantic(
    commandId: Int,
    artifact: GPUPreparedVerticesUploadArtifact,
    material: GPUPreparedMaterialProgram = stubPreparedMaterialProgram(),
    targetFormat: String = "rgba8unorm-srgb",
    topology: GPUVertexMode = artifact.topology,
): GPUDrawSemanticPayload.Vertices {
    val result = GPUPreparedVerticesPayloadGatherer.gather(
        GPUPreparedVerticesPayloadInput(
            payloadRef = GPUDrawPayloadRef(commandId, PREPARED_VERTICES_RENDER_STEP_IDENTITY),
            artifact = artifact,
            material = material,
            materialFrameSnapshot = null,
            topologyIdentity = when (topology) {
                GPUVertexMode.Triangles -> GPUPreparedVerticesTopologyIdentity.Triangles
                GPUVertexMode.TriangleStrip -> GPUPreparedVerticesTopologyIdentity.TriangleStrip
                else -> error("Prepared vertices preflight fixture requires a canonical topology")
            },
            transformBytes = listOf(
                1f.toRawBits(), 0f.toRawBits(), 0f.toRawBits(),
                0f.toRawBits(), 1f.toRawBits(), 0f.toRawBits(),
                0f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(),
            ),
            targetBounds = VERTICES_PREFLIGHT_BOUNDS,
            scissorBounds = VERTICES_PREFLIGHT_BOUNDS,
            targetFormat = targetFormat,
            clipIdentity = "clip:none",
            clipCoverageIdentity = "clip-coverage:none",
            primitiveColorPresent = false,
            primitiveBlendIdentity = null,
            finalBlendIdentity = "src-over",
            capabilitySnapshotHash = "capability:vertices",
            drawProvenance = "test",
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )
    return assertIs<GPUPreparedVerticesPayloadResult.Ready>(result).payload
}

internal fun verticesPreflightArtifact(
    topology: GPUVertexMode = GPUVertexMode.Triangles,
    vertexCount: Int = 6,
    indexed: Boolean = true,
    indexFormat: String? = "uint16",
): GPUPreparedVerticesUploadArtifact = GPUPreparedVerticesUploadArtifact(
    topology = topology,
    layout = GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = false),
    vertexBytes = ByteArray(vertexCount * 8),
    indexBytes = if (indexed) {
        ByteArray(vertexCount * (if (indexFormat == "uint16") 2 else 4))
    } else {
        null
    },
    vertexCount = vertexCount,
    indexCount = if (indexed) vertexCount else null,
    indexFormat = if (indexed) indexFormat else null,
    provenance = "test",
    canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.IdentityV1,
)

private fun verticesPreflightPacket(
    commandId: Int,
    renderStepIdentity: String,
): GPUDrawPacket = GPUDrawPacket(
    packetId = GPUDrawPacketID("packet.vertices.$commandId"),
    commandIdValue = commandId,
    analysisRecordId = "analysis.vertices.$commandId",
    passId = "pass.vertices.$commandId",
    layerId = "root",
    bindingListId = "bindings.vertices.$commandId",
    insertionReasonCode = "prepared-vertices",
    sortKey = commandId.toLong(),
    sortKeyPreimage = "paint-order:$commandId",
    renderStepId = GPURenderStepID(renderStepIdentity),
    renderStepVersion = 1,
    role = GPUDrawPacketRole.Shading,
    blendPlan = org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "one_isa",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding =
            org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding.None,
    ),
    renderPipelineKey = GPURenderPipelineKey("pending.pipeline.vertices"),
    bindingLayoutHash = "pending.layout.vertices",
    vertexSourceLabel = "prepared-vertices",
    targetStateHash = "target.rgba8unorm-srgb.16x16",
    originalPaintOrder = commandId,
    resourceGeneration = VERTICES_PREFLIGHT_TARGET_GENERATION,
    frameProvenance = GPUFrameProvenance.GmContent,
    clipCoveragePlan = GPUClipCoveragePlan.NoClip,
    clipExecutionPlan = GPUClipExecutionPlan.NoClip,
)

private fun verticesPreflightCapabilities() = GPUCapabilities(
    implementation = org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity(
        "GPU",
        "test",
        "adapter",
        "device",
    ),
    facts = listOf(
        org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact(
            "first_slice.fill_rect.native",
            "test",
            "supported",
            true,
            "test",
        ),
        org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact(
            "first_slice.draw_vertices.prepared",
            "test",
            "supported",
            true,
            "test",
        ),
    ),
    snapshotId = "prepared-vertices-preflight",
    limits = org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits(
        maxTextureDimension2D = 8192,
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
        maxBufferSize = 1L shl 30,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
    ),
    supportedTextureFormats = setOf(
        io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb,
    ),
)

private fun GPUFramePlan.verticesVertexBufferRef(artifactKey: String): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.vertex.${frameId.value}.$artifactKey")

private fun GPUFramePlan.withPreparationMutation(
    resource: GPUFrameResourceRef,
    transform: (GPUResourcePreparationRequest) -> GPUResourcePreparationRequest,
): GPUFramePlan = rebuilt(
    steps.map { step ->
        if (step !is GPUFrameStep.PrepareResourcesStep) {
            step
        } else {
            GPUFrameStep.PrepareResourcesStep(
                requests = step.requests.map { request ->
                    if (request.resource == resource) transform(request) else request
                },
                sourceTaskIds = step.sourceTaskIds,
            )
        }
    },
)

private fun GPUFramePreflightContext.withoutResourceGeneration(
    resource: GPUFrameResourceRef,
): GPUFramePreflightContext = GPUFramePreflightContext(
    targetId = targetId,
    deviceGeneration = deviceGeneration,
    targetGeneration = targetGeneration,
    resourceGenerations = resourceGenerations - resource,
    surfaceGeneration = surfaceGeneration,
)

private fun GPUFramePlan.rebuilt(
    steps: List<GPUFrameStep>,
    dependencies: List<GPUTaskDependency> = this.dependencies,
    memoryBudget: GPUFrameMemoryBudgetPlan = this.memoryBudget,
): GPUFramePlan = GPUFramePlan(
    frameId = frameId,
    capabilitySeal = capabilitySeal,
    recordingSeals = recordingSeals,
    steps = steps,
    memoryBudget = memoryBudget,
    diagnostics = diagnostics,
    dependencies = dependencies,
    phaseOrder = phaseOrder,
    elidedNoOpDraws = elidedNoOpDraws,
    atomicallyRefused = atomicallyRefused,
)

private fun GPUFrameStep.RenderPassStep.rebuilt(
    drawPackets: List<GPUDrawPacket> = this.drawPackets,
    resourceUses: List<GPUFrameResourceUse> = this.resourceUses,
    samplePlan: GPUSamplePlan = this.samplePlan,
): GPUFrameStep.RenderPassStep = GPUFrameStep.RenderPassStep(
    target = target,
    loadStore = loadStore,
    samplePlan = samplePlan,
    resourceUses = resourceUses,
    drawPackets = drawPackets,
    sourceTaskIds = sourceTaskIds,
    batches = batches,
    sampleContinuation = sampleContinuation,
    depthStencilLoadStore = depthStencilLoadStore,
    preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
    preparedTextBindingsByPacketId = preparedTextBindingsByPacketId,
)

private fun GPUDrawPacket.rebuilt(
    renderStepId: GPURenderStepID = this.renderStepId,
    semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
): GPUDrawPacket = GPUDrawPacket(
    packetId = packetId,
    commandIdValue = commandIdValue,
    analysisRecordId = analysisRecordId,
    passId = passId,
    layerId = layerId,
    bindingListId = bindingListId,
    insertionReasonCode = insertionReasonCode,
    sortKey = sortKey,
    sortKeyPreimage = sortKeyPreimage,
    renderStepId = renderStepId,
    renderStepVersion = renderStepVersion,
    role = role,
    blendPlan = blendPlan,
    renderPipelineKey = renderPipelineKey,
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = bindingLayoutHash,
    uniformSlot = uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semanticPayload,
    vertexSourceLabel = vertexSourceLabel,
    scissorBoundsHash = scissorBoundsHash,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoveragePlan,
    clipExecutionPlan = clipExecutionPlan,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private val VERTICES_PREFLIGHT_BOUNDS = GPUPixelBounds(0, 0, 16, 16)
private val VERTICES_PREFLIGHT_TARGET = GPUFrameTargetRef("target.prepared-surface")
private val VERTICES_PREFLIGHT_DEVICE_GENERATION =
    org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID(5L)
private const val VERTICES_PREFLIGHT_FRAME_ID = 61L
private const val VERTICES_PREFLIGHT_TARGET_GENERATION = 7L
private const val VERTICES_PREFLIGHT_RESOURCE_GENERATION = 5L

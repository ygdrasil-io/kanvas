package org.graphiks.kanvas.gpu.renderer.payloads

import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.collections.ExactUtf16CanonicalIdentityDigestEncoder
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFrameIdentityAuthority
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFrameSnapshot
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

const val PREPARED_VERTICES_RENDER_STEP_IDENTITY: String = "vertices.draw.prepared"

/** Exact packet identities accepted by the prepared-vertices native route. */
internal val PREPARED_VERTICES_RENDER_PIPELINE_KEY =
    org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey(
        "pipeline.prepared-vertices.rgba8unorm.src-over",
    )

internal const val PREPARED_VERTICES_BINDING_LAYOUT_HASH =
    "layout.prepared-vertices.group0.draw-material"

internal const val PREPARED_VERTICES_VERTEX_SOURCE_LABEL = "prepared-vertices"

/** Closed canonical topologies admitted into prepared vertices semantics. */
enum class GPUPreparedVerticesTopologyIdentity(val sourceLabel: String) {
    Triangles("Triangles"),
    TriangleStrip("TriangleStrip"),
}

/** Mutable-boundary input for one closed, handle-free prepared vertices semantic. */
data class GPUPreparedVerticesPayloadInput(
    val payloadRef: GPUDrawPayloadRef,
    val artifact: GPUPreparedVerticesUploadArtifact,
    val material: GPUPreparedMaterialProgram,
    val materialFrameSnapshot: GPUPreparedMaterialFrameSnapshot? = null,
    val topologyIdentity: GPUPreparedVerticesTopologyIdentity,
    val transformBytes: List<Int>,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val targetFormat: String,
    val clipIdentity: String,
    val clipCoverageIdentity: String,
    val primitiveColorPresent: Boolean,
    val primitiveBlendIdentity: String?,
    val finalBlendIdentity: String,
    val capabilitySnapshotHash: String,
    val drawProvenance: String,
    val frameProvenance: GPUFrameProvenance,
    val suppliedCanonicalHash: String? = null,
)

sealed interface GPUPreparedVerticesPayloadResult {
    data class Ready(val payload: GPUDrawSemanticPayload.Vertices) :
        GPUPreparedVerticesPayloadResult

    class Refused internal constructor(
        val code: String,
        facts: Map<String, String>,
    ) : GPUPreparedVerticesPayloadResult {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

internal class GPUPreparedVerticesPayloadSnapshot(
    input: GPUPreparedVerticesPayloadInput,
) {
    val payloadRef = GPUDrawPayloadRef(
        commandIdValue = input.payloadRef.commandIdValue,
        renderStepIdentity = input.payloadRef.renderStepIdentity,
    )
    val artifact = input.artifact
    val authenticatedMaterial = input.materialFrameSnapshot?.also { snapshot ->
        require(GPUPreparedMaterialFrameIdentityAuthority.authenticates(snapshot)) {
            "Prepared vertices material frame snapshot identity must be authenticated"
        }
        require(GPUPreparedMaterialFrameIdentityAuthority.exactlyMatches(snapshot.program, input.material)) {
            "Prepared vertices material must match the supplied authenticated frame snapshot"
        }
    } ?: GPUPreparedMaterialFrameIdentityAuthority.authenticate(input.material)
    val material = authenticatedMaterial.program
    val materialIdentity = authenticatedMaterial.identity.bucketKey
    val topologyIdentity = input.topologyIdentity
    val transformBytes = immutableList(input.transformBytes)
    val targetBounds = input.targetBounds.copy()
    val scissorBounds = input.scissorBounds.copy()
    val targetFormat = input.targetFormat
    val clipIdentity = input.clipIdentity
    val clipCoverageIdentity = input.clipCoverageIdentity
    val primitiveColorPresent = input.primitiveColorPresent
    val primitiveBlendIdentity = input.primitiveBlendIdentity
    val finalBlendIdentity = input.finalBlendIdentity
    val capabilitySnapshotHash = input.capabilitySnapshotHash
    val drawProvenance = input.drawProvenance
    val frameProvenance = input.frameProvenance
    val canonicalHash = canonicalHash()

    fun canonicalHash(): String {
        val layout = artifact.layout
        val encoder = ExactUtf16CanonicalIdentityDigestEncoder(
            "prepared-vertices-semantic-v2-utf16-code-units",
        )
            .int("payloadRef.commandIdValue", payloadRef.commandIdValue)
            .text("payloadRef.renderStepIdentity", payloadRef.renderStepIdentity)
            .text("artifact.key", artifact.key)
            .text("artifact.topology", artifact.topology.sourceLabel)
            .text("artifact.canonicalization", artifact.canonicalizationIdentity.stableIdentity)
            .int("artifact.vertexCount", artifact.vertexCount)
            .boolean("artifact.hasIndices", artifact.indexCount != null)
            .int("artifact.indexCount", artifact.indexCount ?: 0)
            .text("artifact.indexFormat", artifact.indexFormat ?: "none")
            .int("artifact.layout.strideBytes", layout.strideBytes)
            .texts("artifact.layout.attributes", layout.attributes)
            .texts("artifact.layout.formats", layout.attributeFormats)
            .texts(
                "artifact.layout.offsets",
                layout.offsets.toSortedMap().map { (name, value) -> "$name=$value" },
            )
            .texts(
                "artifact.layout.shaderLocations",
                layout.shaderLocations.toSortedMap().map { (name, value) -> "$name=$value" },
            )
            .bytes("artifact.vertexBytes", artifact.vertexBytesForUpload())
            .bytes("artifact.indexBytes", artifact.indexBytesForUpload() ?: byteArrayOf())
            .text("material.authenticatedIdentity", materialIdentity)
            .text("topology", topologyIdentity.sourceLabel)
            .int("transform.count", transformBytes.size)
        transformBytes.forEachIndexed { index, bits ->
            encoder.int("transform.rawBits[$index]", bits)
        }
        return encoder
            .int("target.left", targetBounds.left)
            .int("target.top", targetBounds.top)
            .int("target.right", targetBounds.right)
            .int("target.bottom", targetBounds.bottom)
            .text("target.format", targetFormat)
            .int("scissor.left", scissorBounds.left)
            .int("scissor.top", scissorBounds.top)
            .int("scissor.right", scissorBounds.right)
            .int("scissor.bottom", scissorBounds.bottom)
            .text("clip.identity", clipIdentity)
            .text("clip.coverageIdentity", clipCoverageIdentity)
            .boolean("primitiveColor.present", primitiveColorPresent)
            .boolean("primitiveBlend.present", primitiveBlendIdentity != null)
            .text("primitiveBlend.identity", primitiveBlendIdentity ?: "none")
            .text("finalBlend.identity", finalBlendIdentity)
            .text("capability.snapshotHash", capabilitySnapshotHash)
            .text("draw.provenance", drawProvenance)
            .text("frame.provenance", frameProvenance.annotationValue)
            .digestIdentity()
    }
}

/** Total factory: all malformed inputs refuse before a semantic instance exists. */
object GPUPreparedVerticesPayloadGatherer {
    fun gather(input: GPUPreparedVerticesPayloadInput): GPUPreparedVerticesPayloadResult {
        validatePayloadRef(input)?.let { return it }
        validateArtifact(input)?.let { return it }
        validateTransform(input.transformBytes)?.let { return it }
        validateBounds(input.targetBounds, input.scissorBounds)?.let { return it }
        validateIdentities(input)?.let { return it }

        val snapshot = try {
            GPUPreparedVerticesPayloadSnapshot(input)
        } catch (failure: IllegalArgumentException) {
            return refused(
                code = "invalid.renderer.prepared.vertices-material",
                reason = "material_authentication_failed",
                extra = mapOf("message" to (failure.message ?: "invalid")),
            )
        }
        input.suppliedCanonicalHash?.let { supplied ->
            if (supplied != snapshot.canonicalHash) {
                return refused(
                    code = "invalid.renderer.prepared.vertices-hash",
                    reason = "canonical_hash_mismatch",
                )
            }
        }
        return GPUPreparedVerticesPayloadResult.Ready(
            GPUDrawSemanticPayload.Vertices(snapshot),
        )
    }

    private fun validatePayloadRef(
        input: GPUPreparedVerticesPayloadInput,
    ): GPUPreparedVerticesPayloadResult.Refused? {
        val ref = input.payloadRef
        return if (
            ref.commandIdValue < 0 ||
            ref.renderStepIdentity != PREPARED_VERTICES_RENDER_STEP_IDENTITY ||
            ref.uniformSlot != null || ref.resourceSlot != null || ref.gradientStore != null ||
            ref.uniformBlock != null || ref.resourceBlock != null
        ) {
            refused("invalid.renderer.prepared.vertices-payload-ref", "noncanonical_payload_ref")
        } else {
            null
        }
    }

    private fun validateArtifact(
        input: GPUPreparedVerticesPayloadInput,
    ): GPUPreparedVerticesPayloadResult.Refused? {
        val artifact = input.artifact
        if (input.topologyIdentity.sourceLabel != artifact.topology.sourceLabel) {
            return refused("invalid.renderer.prepared.vertices-topology", "topology_mismatch")
        }
        val vertexBytes = try {
            Math.multiplyExact(artifact.vertexCount, artifact.layout.strideBytes)
        } catch (_: ArithmeticException) {
            return refused("invalid.renderer.prepared.vertices-artifact", "vertex_size_overflow")
        }
        if (vertexBytes != artifact.vertexBytesForUpload().size) {
            return refused("invalid.renderer.prepared.vertices-artifact", "vertex_size_mismatch")
        }
        val expectedIndexBytes = artifact.indexCount?.let { count ->
            try {
                Math.multiplyExact(count, if (artifact.indexFormat == "uint16") 2 else 4)
            } catch (_: ArithmeticException) {
                return refused("invalid.renderer.prepared.vertices-artifact", "index_size_overflow")
            }
        }
        if (expectedIndexBytes != artifact.indexBytesForUpload()?.size) {
            return refused("invalid.renderer.prepared.vertices-artifact", "index_size_mismatch")
        }
        return null
    }

    private fun validateTransform(
        bytes: List<Int>,
    ): GPUPreparedVerticesPayloadResult.Refused? {
        if (bytes.size != 9) {
            return refused("invalid.renderer.prepared.vertices-transform", "transform_size")
        }
        val values = bytes.map(Float::fromBits)
        if (values.any { !it.isFinite() } ||
            values[6] != 0f || values[7] != 0f || values[8] != 1f
        ) {
            return refused("invalid.renderer.prepared.vertices-transform", "non_affine_or_nonfinite")
        }
        val determinant = values[0] * values[4] - values[1] * values[3]
        return if (!determinant.isFinite() || determinant == 0f) {
            refused("invalid.renderer.prepared.vertices-transform", "singular_transform")
        } else {
            null
        }
    }

    private fun validateBounds(
        target: GPUPixelBounds,
        scissor: GPUPixelBounds,
    ): GPUPreparedVerticesPayloadResult.Refused? =
        if (target.isEmpty || scissor.isEmpty || scissor.left < target.left ||
            scissor.top < target.top || scissor.right > target.right ||
            scissor.bottom > target.bottom
        ) {
            refused("invalid.renderer.prepared.vertices-bounds", "invalid_target_or_scissor")
        } else {
            null
        }

    private fun validateIdentities(
        input: GPUPreparedVerticesPayloadInput,
    ): GPUPreparedVerticesPayloadResult.Refused? {
        if (input.targetFormat.isBlank() || input.clipIdentity.isBlank() ||
            input.clipCoverageIdentity.isBlank() || input.finalBlendIdentity.isBlank()
        ) {
            return refused("invalid.renderer.prepared.vertices-identity", "blank_identity")
        }
        if (input.primitiveColorPresent != (input.primitiveBlendIdentity != null) ||
            input.primitiveBlendIdentity?.isBlank() == true
        ) {
            return refused("invalid.renderer.prepared.vertices-identity", "primitive_blend_mismatch")
        }
        if (input.capabilitySnapshotHash.isBlank()) {
            return refused("invalid.renderer.prepared.vertices-capability", "blank_capability")
        }
        if (input.drawProvenance.isBlank()) {
            return refused("invalid.renderer.prepared.vertices-provenance", "blank_provenance")
        }
        return null
    }

    private fun refused(
        code: String,
        reason: String,
        extra: Map<String, String> = emptyMap(),
    ) = GPUPreparedVerticesPayloadResult.Refused(
        code,
        linkedMapOf(
            "authority" to "GPUPreparedVerticesPayloadGatherer",
            "reason" to reason,
        ).apply { putAll(extra) },
    )
}

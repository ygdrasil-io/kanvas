package org.graphiks.kanvas.gpu.renderer.artifacts

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

/** Versioned identity of the topology canonicalization embodied by an artifact. */
enum class GPUPreparedVerticesCanonicalizationIdentity(val stableIdentity: String) {
    IdentityV1("identity-v1"),
    TriangleFanToTriangleListV1("triangle-fan-to-triangle-list-v1"),
}

/**
 * Immutable frame-owned vertex/index payload. Its identity intentionally excludes
 * command state and native-resource state.
 */
class GPUPreparedVerticesUploadArtifact internal constructor(
    topology: GPUVertexMode,
    layout: GPUVertexLayoutPlan,
    vertexBytes: ByteArray,
    indexBytes: ByteArray?,
    val vertexCount: Int,
    val indexCount: Int?,
    val indexFormat: String?,
    val provenance: String,
    val canonicalizationIdentity: GPUPreparedVerticesCanonicalizationIdentity,
) {
    private val vertexSnapshot = vertexBytes.copyOf()
    private val indexSnapshot = indexBytes?.copyOf()

    val topology: GPUVertexMode = topology
    val layout: GPUVertexLayoutPlan = layout.deepSnapshot()

    init {
        validate(
            topology = topology,
            layout = this.layout,
            vertexBytes = vertexSnapshot,
            indexBytes = indexSnapshot,
            vertexCount = vertexCount,
            indexCount = indexCount,
            indexFormat = indexFormat,
            provenance = provenance,
            canonicalizationIdentity = canonicalizationIdentity,
        )
    }

    val vertexContentHash: String = vertexSnapshot.sha256Hex()
    val indexContentHash: String? = indexSnapshot?.sha256Hex()
    val key: String = CanonicalIdentityEncoder("prepared-vertices-artifact-v1")
        .text("topology", topology.canonicalKeyLabel())
        .int("vertexCount", vertexCount)
        .boolean("hasIndices", indexSnapshot != null)
        .int("indexCount", indexCount ?: 0)
        .text("canonicalizationIdentity", canonicalizationIdentity.stableIdentity)
        .int("strideBytes", this.layout.strideBytes)
        .texts("attributes", this.layout.attributes)
        .texts(
            "offsets",
            this.layout.offsets.entries.map { (name, offset) -> "$name=$offset" },
        )
        .texts(
            "locations",
            this.layout.shaderLocations.entries.map { (name, location) -> "$name=$location" },
        )
        .text("vertexHash", vertexContentHash)
        .apply {
            if (indexSnapshot != null) {
                text("indexFormat", requireNotNull(indexFormat))
                text("indexHash", requireNotNull(indexContentHash))
            }
        }
        .digestIdentity()

    fun vertexBytesForUpload(): ByteArray = vertexSnapshot.copyOf()

    fun indexBytesForUpload(): ByteArray? = indexSnapshot?.copyOf()

    private companion object {
        fun validate(
            topology: GPUVertexMode,
            layout: GPUVertexLayoutPlan,
            vertexBytes: ByteArray,
            indexBytes: ByteArray?,
            vertexCount: Int,
            indexCount: Int?,
            indexFormat: String?,
            provenance: String,
            canonicalizationIdentity: GPUPreparedVerticesCanonicalizationIdentity,
        ) {
            require(provenance.isNotBlank()) { "Prepared vertices provenance must not be blank" }
            require(topology == GPUVertexMode.Triangles || topology == GPUVertexMode.TriangleStrip) {
                "Prepared vertices topology must be canonical"
            }
            require(vertexCount > 0) { "Prepared vertices vertexCount must be positive" }
            validateLayout(layout)
            require(vertexBytes.size == checkedProduct(vertexCount, layout.strideBytes, "vertex bytes")) {
                "Prepared vertices bytes must exactly match vertexCount * strideBytes"
            }
            validateIndices(indexBytes, indexCount, indexFormat)
            validateCanonicalization(topology, indexBytes, canonicalizationIdentity)
        }

        fun validateLayout(layout: GPUVertexLayoutPlan) {
            require(GPUPreparedVerticesLayoutAuthority.isCanonical(layout)) {
                "Prepared vertices layout must exactly match the canonical FP-06 layout authority"
            }
        }

        fun validateCanonicalization(
            topology: GPUVertexMode,
            indexBytes: ByteArray?,
            canonicalizationIdentity: GPUPreparedVerticesCanonicalizationIdentity,
        ) {
            if (canonicalizationIdentity ==
                GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1
            ) {
                require(topology == GPUVertexMode.Triangles && indexBytes != null) {
                    "Triangle-fan canonicalization requires triangle-list topology and an index payload"
                }
            }
        }

        fun validateIndices(
            indexBytes: ByteArray?,
            indexCount: Int?,
            indexFormat: String?,
        ) {
            if (indexBytes == null) {
                require(indexCount == null && indexFormat == null) {
                    "Prepared vertices absent indices must not carry count or format"
                }
                return
            }

            require(indexCount != null && indexCount > 0) {
                "Prepared vertices indices must carry a positive count"
            }
            require(indexFormat == "uint16" || indexFormat == "uint32") {
                "Prepared vertices index format must be uint16 or uint32"
            }
            val elementBytes = if (indexFormat == "uint16") 2 else 4
            require(indexBytes.size == checkedProduct(indexCount, elementBytes, "index bytes")) {
                "Prepared vertices index bytes must exactly match count and format"
            }
        }

        fun checkedProduct(left: Int, right: Int, label: String): Int =
            try {
                Math.multiplyExact(left, right)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("Prepared vertices $label overflow", error)
            }
    }
}

private fun GPUVertexLayoutPlan.deepSnapshot(): GPUVertexLayoutPlan =
    GPUVertexLayoutPlan(
        attributes = Collections.unmodifiableList(attributes.toList()),
        strideBytes = strideBytes,
        offsets = Collections.unmodifiableMap(offsets.toSortedMap()),
        shaderLocations = Collections.unmodifiableMap(shaderLocations.toSortedMap()),
    )

private fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun GPUVertexMode.canonicalKeyLabel(): String =
    when (this) {
        GPUVertexMode.Triangles -> "triangle-list"
        GPUVertexMode.TriangleStrip -> "triangle-strip"
        GPUVertexMode.TriangleFan,
        is GPUVertexMode.Unsupported,
        -> error("Prepared vertices topology must be canonical before key encoding")
    }

package org.graphiks.kanvas.gpu.renderer.artifacts

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

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
        )
    }

    val vertexContentHash: String = vertexSnapshot.sha256Hex()
    val indexContentHash: String? = indexSnapshot?.sha256Hex()
    val key: String = CanonicalIdentityEncoder("prepared-vertices-artifact-v1")
        .text("topology", topology.canonicalKeyLabel())
        .int("vertexCount", vertexCount)
        .boolean("hasIndices", indexSnapshot != null)
        .int("indexCount", indexCount ?: 0)
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
        val canonicalLayouts = setOf(
            listOf("position"),
            listOf("position", "color"),
            listOf("position", "texcoord"),
            listOf("position", "color", "texcoord"),
        )
        val attributeByteSizes = mapOf(
            "position" to 8,
            "color" to 4,
            "texcoord" to 8,
        )

        fun validate(
            topology: GPUVertexMode,
            layout: GPUVertexLayoutPlan,
            vertexBytes: ByteArray,
            indexBytes: ByteArray?,
            vertexCount: Int,
            indexCount: Int?,
            indexFormat: String?,
            provenance: String,
        ) {
            require(provenance.isNotBlank()) { "Prepared vertices provenance must not be blank" }
            require(topology == GPUVertexMode.Triangles || topology == GPUVertexMode.TriangleStrip) {
                "Prepared vertices topology must be canonical"
            }
            require(vertexCount > 0) { "Prepared vertices vertexCount must be positive" }
            require(layout.strideBytes > 0) { "Prepared vertices layout stride must be positive" }
            require(vertexBytes.size == checkedProduct(vertexCount, layout.strideBytes, "vertex bytes")) {
                "Prepared vertices bytes must exactly match vertexCount * strideBytes"
            }
            validateLayout(layout)
            validateIndices(indexBytes, indexCount, indexFormat)
        }

        fun validateLayout(layout: GPUVertexLayoutPlan) {
            require(layout.attributes in canonicalLayouts) {
                "Prepared vertices layout attributes must be canonical"
            }
            val attributes = layout.attributes
            require(layout.offsets.keys == attributes.toSet()) {
                "Prepared vertices layout offsets must exactly match attributes"
            }
            require(layout.shaderLocations.keys == attributes.toSet()) {
                "Prepared vertices layout shader locations must exactly match attributes"
            }
            require(layout.offsets.values.all { offset -> offset >= 0 && offset < layout.strideBytes }) {
                "Prepared vertices layout offsets must be within stride"
            }
            require(layout.shaderLocations.values.all { location -> location >= 0 }) {
                "Prepared vertices layout shader locations must be non-negative"
            }
            require(layout.offsets.values.toSet().size == attributes.size) {
                "Prepared vertices layout offsets must be unique"
            }
            require(layout.shaderLocations.values.toSet().size == attributes.size) {
                "Prepared vertices layout shader locations must be unique"
            }
            attributes.forEach { attribute ->
                require(layout.offsets.getValue(attribute) + attributeByteSizes.getValue(attribute) <= layout.strideBytes) {
                    "Prepared vertices attribute must fit within stride"
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

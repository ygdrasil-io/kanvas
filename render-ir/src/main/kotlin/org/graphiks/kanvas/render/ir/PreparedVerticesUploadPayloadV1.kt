package org.graphiks.kanvas.render.ir

import java.security.MessageDigest
import org.graphiks.math.geometry.TriangleMeshF32
import org.graphiks.math.geometry.TriangleTopologyI32

/** Neutral, immutable V/I upload bytes sealed before a graph is published. */
public class PreparedVerticesUploadPayloadV1 private constructor(
    public val topology: Topology,
    public val vertexCountI32: Int,
    public val indexCountI32: Int?,
    public val vertexStrideBytesI32: Int,
    public val indexElementBytesI32: Int?,
    public val hasColors: Boolean,
    public val hasTexCoords: Boolean,
    public val canonicalization: Canonicalization,
    vertexBytes: ByteArray,
    indexBytes: ByteArray?,
) {
    public enum class Topology { TriangleList, TriangleStrip }

    public enum class Canonicalization(public val stableIdentity: String) {
        IdentityV1("identity-v1"),
        TriangleFanToTriangleListV1("triangle-fan-to-triangle-list-v1"),
    }

    private val vertexSnapshot: ByteArray = vertexBytes.copyOf()
    private val indexSnapshot: ByteArray? = indexBytes?.copyOf()

    public val vertexBytesI64: Long get() = vertexSnapshot.size.toLong()
    public val indexBytesI64: Long get() = indexSnapshot?.size?.toLong() ?: 0L
    public val canonicalIdentity: String = canonicalIdentity(
        topology, vertexCountI32, indexCountI32, vertexStrideBytesI32, indexElementBytesI32,
        hasColors, hasTexCoords, canonicalization, vertexSnapshot, indexSnapshot,
    )

    init {
        require(vertexCountI32 > 0 && vertexStrideBytesI32 >= 8)
        require(vertexSnapshot.size == Math.multiplyExact(vertexCountI32, vertexStrideBytesI32))
        if (indexSnapshot == null) {
            require(indexCountI32 == null && indexElementBytesI32 == null)
        } else {
            require(indexCountI32 != null && indexCountI32 > 0)
            require(indexElementBytesI32 == 2 || indexElementBytesI32 == 4)
            require(indexSnapshot.size == Math.multiplyExact(indexCountI32, indexElementBytesI32))
        }
        if (canonicalization == Canonicalization.TriangleFanToTriangleListV1) {
            require(topology == Topology.TriangleList && indexSnapshot != null)
        }
    }

    public fun copyVertexBytes(): ByteArray = vertexSnapshot.copyOf()
    public fun copyIndexBytes(): ByteArray? = indexSnapshot?.copyOf()

    public companion object {
        /**
         * This is the sole V/I canonicalizer shared by graph planning and every renderer
         * consumer.  Inputs are already immutable `TriangleMeshF32` snapshots.
         */
        public fun seal(geometry: TriangleMeshF32, colorsRgba8: ByteArray?): PreparedVerticesUploadPayloadV1 {
            val positions = geometry.copyPositionsF32()
            val coordinates = geometry.copyCoordinatesF32()
            val vertexCount = geometry.vertexCountI32
            require(colorsRgba8 == null || colorsRgba8.size == Math.multiplyExact(vertexCount, 4))
            val stride = 8 + (if (colorsRgba8 == null) 0 else 4) + (if (coordinates == null) 0 else 8)
            val vertexBytes = ByteArray(Math.multiplyExact(vertexCount, stride))
            var offset = 0
            repeat(vertexCount) { index ->
                vertexBytes.putI32LE(offset, positions[index * 2].toRawBits()); offset += 4
                vertexBytes.putI32LE(offset, positions[index * 2 + 1].toRawBits()); offset += 4
                colorsRgba8?.let { colors ->
                    val colorOffset = index * 4
                    val alpha = colors[colorOffset + 3].toInt() and 0xff
                    vertexBytes[offset++] = premultiply(colors[colorOffset], alpha)
                    vertexBytes[offset++] = premultiply(colors[colorOffset + 1], alpha)
                    vertexBytes[offset++] = premultiply(colors[colorOffset + 2], alpha)
                    vertexBytes[offset++] = alpha.toByte()
                }
                coordinates?.let { texCoords ->
                    vertexBytes.putI32LE(offset, texCoords[index * 2].toRawBits()); offset += 4
                    vertexBytes.putI32LE(offset, texCoords[index * 2 + 1].toRawBits()); offset += 4
                }
            }
            require(offset == vertexBytes.size)
            val indices = geometry.copyIndicesI32()
            val indexElementBytes = indices?.maxOrNull()?.let { if (it <= 65_535) 2 else 4 }
            val indexBytes = indices?.let { values -> ByteArray(Math.multiplyExact(values.size, requireNotNull(indexElementBytes))).also { bytes ->
                var indexOffset = 0
                values.forEach { value ->
                    if (indexElementBytes == 2) {
                        bytes[indexOffset++] = value.toByte()
                        bytes[indexOffset++] = (value ushr 8).toByte()
                    } else {
                        bytes.putI32LE(indexOffset, value)
                        indexOffset += 4
                    }
                }
                require(indexOffset == bytes.size)
            } }
            return PreparedVerticesUploadPayloadV1(
                topology = if (geometry.topologyI32 == TriangleTopologyI32.Strip) Topology.TriangleStrip else Topology.TriangleList,
                vertexCountI32 = vertexCount,
                indexCountI32 = indices?.size,
                vertexStrideBytesI32 = stride,
                indexElementBytesI32 = indexElementBytes,
                hasColors = colorsRgba8 != null,
                hasTexCoords = coordinates != null,
                canonicalization = if (geometry.fanExpanded) Canonicalization.TriangleFanToTriangleListV1 else Canonicalization.IdentityV1,
                vertexBytes = vertexBytes,
                indexBytes = indexBytes,
            )
        }
    }
}

private fun ByteArray.putI32LE(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
    this[offset + 2] = (value ushr 16).toByte()
    this[offset + 3] = (value ushr 24).toByte()
}

private fun premultiply(component: Byte, alpha: Int): Byte =
    ((((component.toInt() and 0xff) * alpha + 127) / 255)).toByte()

private fun canonicalIdentity(
    topology: PreparedVerticesUploadPayloadV1.Topology,
    vertexCountI32: Int,
    indexCountI32: Int?,
    vertexStrideBytesI32: Int,
    indexElementBytesI32: Int?,
    hasColors: Boolean,
    hasTexCoords: Boolean,
    canonicalization: PreparedVerticesUploadPayloadV1.Canonicalization,
    vertexBytes: ByteArray,
    indexBytes: ByteArray?,
): String = CanonicalHashBytesV1("kanvas-prepared-vertices-upload-v1")
    .text(topology.name).i32(vertexCountI32).option(indexCountI32) { i32(it) }
    .i32(vertexStrideBytesI32).option(indexElementBytesI32) { i32(it) }
    .u8(if (hasColors) 1 else 0).u8(if (hasTexCoords) 1 else 0).text(canonicalization.stableIdentity)
    .text(vertexBytes.sha256Hex()).option(indexBytes) { text(it.sha256Hex()) }.sha256Hex()

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") {
    (it.toInt() and 0xff).toString(16).padStart(2, '0')
}

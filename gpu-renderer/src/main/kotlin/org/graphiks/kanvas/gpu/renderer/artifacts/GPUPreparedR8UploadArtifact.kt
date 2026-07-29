package org.graphiks.kanvas.gpu.renderer.artifacts

import java.security.MessageDigest
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact

/** Immutable row-strided R8 upload artifact shared by A8 and color-glyph coverage. */
class GPUPreparedR8UploadArtifact internal constructor(
    val key: String,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
    val generation: Long,
    val contentHash: String,
    bytes: ByteArray,
) {
    private val snapshot = validateAndSnapshot(
        key = key,
        width = width,
        height = height,
        rowBytes = rowBytes,
        generation = generation,
        contentHash = contentHash,
        bytes = bytes,
    )

    val byteSize: Int
        get() = snapshot.size

    fun tightBytesForUpload(): ByteArray = snapshot.copyOf()

    private companion object {
        fun validateAndSnapshot(
            key: String,
            width: Int,
            height: Int,
            rowBytes: Int,
            generation: Long,
            contentHash: String,
            bytes: ByteArray,
        ): ByteArray {
            require(key.isNotBlank()) { "R8 artifact key must not be blank" }
            require(width > 0 && height > 0) {
                "R8 artifact dimensions must be positive"
            }
            require(rowBytes >= width) {
                "R8 artifact rowBytes must contain every meaningful texel"
            }
            require(generation >= 0L) {
                "R8 artifact generation must be non-negative"
            }
            require(contentHash.matches(Regex("[0-9a-f]{64}"))) {
                "R8 artifact contentHash must be a lowercase SHA-256 value"
            }
            val byteSize = Math.multiplyExact(rowBytes.toLong(), height.toLong())
            require(byteSize <= Int.MAX_VALUE) {
                "R8 artifact payload exceeds JVM byte-array capacity"
            }
            require(bytes.size.toLong() == byteSize) {
                "R8 artifact bytes must contain exactly rowBytes * height samples"
            }
            require(bytes.sha256() == contentHash) {
                "R8 artifact contentHash must match the exact source bytes"
            }
            return bytes.copyOf()
        }
    }
}

/**
 * Adapts one already-finalized text atlas page without deriving a second
 * identity or content hash authority.
 */
fun GPUTextA8AtlasPageArtifact.toPreparedR8UploadArtifact(): GPUPreparedR8UploadArtifact =
    GPUPreparedR8UploadArtifact(
        key = "prepared-text-a8-page:v1:" +
            "artifactID=${artifactKey.artifactID.value}:" +
            "pageIndex=$pageIndex:" +
            "contentFingerprint=${artifactKey.contentFingerprint}",
        width = width,
        height = height,
        rowBytes = rowBytes,
        generation = artifactKey.generation.value.toLong(),
        contentHash = contentSha256,
        bytes = ByteArray(bytes.size) { index -> bytes[index].toByte() },
    )

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

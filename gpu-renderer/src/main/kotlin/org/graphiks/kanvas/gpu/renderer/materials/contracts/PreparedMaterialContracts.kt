package org.graphiks.kanvas.gpu.renderer.materials.contracts

import java.security.MessageDigest

/**
 * Passive, handle-free snapshot of one sampled image binding admitted by the
 * prepared-material compiler.
 *
 * The compiler remains the sole authority for [resourceKey]. This contract only
 * freezes and validates the exact already-expanded RGBA facts that it receives.
 */
class GPUPreparedMaterialSampledResource internal constructor(
    val width: Int,
    val height: Int,
    val samplingFilterMode: String,
    val alphaOnly: Boolean,
    rgba8Bytes: ByteArray,
    val resourceKey: String,
) {
    private val rgba8Snapshot = rgba8Bytes.copyOf()

    val contentHash: String = sha256Hex(rgba8Snapshot)

    init {
        require(width > 0 && height > 0) {
            "Prepared material sampled resource dimensions must be positive"
        }
        val expectedBytes = exactRgbaByteCount(width, height)
        require(expectedBytes != null && expectedBytes == rgba8Snapshot.size.toLong()) {
            "Prepared material sampled resource must contain width * height * 4 bytes"
        }
        require(samplingFilterMode == "nearest" || samplingFilterMode == "linear") {
            "Prepared material sampled resource requires nearest or linear sampling"
        }
        require(resourceKey.isNotBlank()) {
            "Prepared material sampled resource key must not be blank"
        }
    }

    fun rgba8Bytes(): ByteArray = rgba8Snapshot.copyOf()

    internal fun identityFacts(): List<String> = listOf(
        "key=$resourceKey",
        "content=$contentHash",
        "dimensions=${width}x$height",
        "sampling=$samplingFilterMode",
        "alphaOnly=$alphaOnly",
    )
}

/** Passive source-family fact retained by a prepared material program. */
enum class GPUMaterialSourceKind {
    SolidColor,
    Gradient,
    ImageShader,
    RuntimeEffect,
    ShaderBlend,
    Unsupported,
}

/**
 * Immutable, handle-free result of prepared-material compilation.
 *
 * This DTO owns no lowering, routing, shader generation, or resource decision.
 */
data class GPUPreparedMaterialProgram(
    val materialKey: String,
    val wgslSource: String,
    val entryPoint: String,
    val uniformBytes: List<Int>,
    val sampledResources: List<GPUPreparedMaterialSampledResource>,
    val paintAlpha: Float,
    val sourceKind: GPUMaterialSourceKind,
    val abiHash: String,
)

private fun exactRgbaByteCount(width: Int, height: Int): Long? {
    if (width <= 0 || height <= 0) return null
    return try {
        Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), 4L)
    } catch (_: ArithmeticException) {
        null
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

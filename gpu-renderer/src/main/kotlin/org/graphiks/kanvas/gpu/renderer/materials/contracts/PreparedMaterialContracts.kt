package org.graphiks.kanvas.gpu.renderer.materials.contracts

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList

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
class GPUPreparedMaterialProgram internal constructor(
    val materialKey: String,
    val wgslSource: String,
    val entryPoint: String,
    val composableFragment: GPUPreparedMaterialFragment,
    uniformBytes: List<Int>,
    sampledResources: List<GPUPreparedMaterialSampledResource>,
    val paintAlpha: Float,
    val sourceKind: GPUMaterialSourceKind,
    val abiHash: String,
) {
    val uniformBytes: List<Int> = immutableList(uniformBytes)
    val sampledResources: List<GPUPreparedMaterialSampledResource> =
        immutableList(sampledResources)

    init {
        require(materialKey.isNotBlank()) { "Prepared material key must not be blank" }
        require(wgslSource.isNotBlank()) { "Prepared material WGSL source must not be blank" }
        require(entryPoint.isNotBlank()) { "Prepared material entry point must not be blank" }
        require(this.uniformBytes.all { byte -> byte in 0..255 }) {
            "Prepared material uniforms must be unsigned bytes"
        }
        require(paintAlpha.isFinite() && paintAlpha in 0f..1f) {
            "Prepared material paint alpha must be finite and normalized"
        }
        require(abiHash.isNotBlank()) { "Prepared material ABI hash must not be blank" }

        val uniformBinding = composableFragment.uniformBinding
        require((uniformBinding == null) == this.uniformBytes.isEmpty()) {
            "Prepared material fragment uniform topology must match its payload"
        }
        uniformBinding?.let { binding ->
            require(binding.group == 1 && binding.binding == 0) {
                "Prepared material uniform binding must use canonical group 1 binding 0"
            }
            require(binding.minBindingSizeBytes == this.uniformBytes.size) {
                "Prepared material fragment uniform size must match its payload"
            }
        }
        require(composableFragment.sampledBindings.size == this.sampledResources.size) {
            "Prepared material fragment sampled topology must match its resources"
        }
    }
}

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

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
    private val expectedFragmentIdentity: GPUPreparedMaterialFragmentIdentity,
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
        require(composableFragment.authenticatedIdentity == expectedFragmentIdentity) {
            "Prepared material fragment identity must match its authenticated program"
        }

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

    internal fun authenticatedSnapshot(): GPUPreparedMaterialProgram =
        GPUPreparedMaterialProgram(
            materialKey = materialKey,
            wgslSource = wgslSource,
            entryPoint = entryPoint,
            composableFragment = composableFragment,
            uniformBytes = uniformBytes,
            sampledResources = sampledResources,
            paintAlpha = paintAlpha,
            sourceKind = sourceKind,
            abiHash = abiHash,
            expectedFragmentIdentity = expectedFragmentIdentity,
        )

    operator fun component1(): String = materialKey

    operator fun component2(): String = wgslSource

    operator fun component3(): String = entryPoint

    operator fun component4(): GPUPreparedMaterialFragment = composableFragment

    operator fun component5(): List<Int> = uniformBytes

    operator fun component6(): List<GPUPreparedMaterialSampledResource> = sampledResources

    operator fun component7(): Float = paintAlpha

    operator fun component8(): GPUMaterialSourceKind = sourceKind

    operator fun component9(): String = abiHash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUPreparedMaterialProgram) return false

        return materialKey == other.materialKey &&
            wgslSource == other.wgslSource &&
            entryPoint == other.entryPoint &&
            composableFragment == other.composableFragment &&
            uniformBytes == other.uniformBytes &&
            sampledResources == other.sampledResources &&
            paintAlpha.compareTo(other.paintAlpha) == 0 &&
            sourceKind == other.sourceKind &&
            abiHash == other.abiHash
    }

    override fun hashCode(): Int {
        var result = materialKey.hashCode()
        result = 31 * result + wgslSource.hashCode()
        result = 31 * result + entryPoint.hashCode()
        result = 31 * result + composableFragment.hashCode()
        result = 31 * result + uniformBytes.hashCode()
        result = 31 * result + sampledResources.hashCode()
        result = 31 * result + paintAlpha.hashCode()
        result = 31 * result + sourceKind.hashCode()
        result = 31 * result + abiHash.hashCode()
        return result
    }

    override fun toString(): String =
        "GPUPreparedMaterialProgram(" +
            "materialKey=$materialKey, " +
            "wgslSource=$wgslSource, " +
            "entryPoint=$entryPoint, " +
            "composableFragment=$composableFragment, " +
            "uniformBytes=$uniformBytes, " +
            "sampledResources=$sampledResources, " +
            "paintAlpha=$paintAlpha, " +
            "sourceKind=$sourceKind, " +
            "abiHash=$abiHash)"
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

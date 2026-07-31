package org.graphiks.kanvas.gpu.renderer.materials.contracts

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.state.GPUSourceAlphaClassification

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

/** Exact callable role exposed by one registered prepared runtime-effect child slot. */
enum class GPUPreparedRuntimeEffectChildRole {
    Shader,
    ColorFilter,
    Blender,
}

/** Handle-free compiled payload for one exact registered runtime-effect child. */
@ConsistentCopyVisibility
data class GPUPreparedRuntimeEffectChildProgram internal constructor(
    val name: String,
    val role: GPUPreparedRuntimeEffectChildRole,
    val programKey: String,
    val abiHash: String,
    val uniformBytes: List<Int>,
    val resourceFacts: List<String>,
    val wgslSource: String,
    val evaluationFunction: String,
    internal val cpuProgram: GPUPreparedRuntimeEffectChildCpuProgram,
) {
    init {
        require(name.isNotBlank()) { "Prepared runtime-effect child name must not be blank" }
        require(programKey.isNotBlank()) { "Prepared runtime-effect child program key must not be blank" }
        require(abiHash.matches(Regex("sha256:[0-9a-f]{64}"))) {
            "Prepared runtime-effect child ABI hash must be canonical"
        }
        require(uniformBytes.all { byte -> byte in 0..255 }) {
            "Prepared runtime-effect child uniforms must be unsigned bytes"
        }
        require(resourceFacts.all(String::isNotBlank)) {
            "Prepared runtime-effect child resource facts must not be blank"
        }
        require(wgslSource.isNotBlank()) { "Prepared runtime-effect child WGSL must not be blank" }
        require(evaluationFunction.isNotBlank()) {
            "Prepared runtime-effect child evaluation function must not be blank"
        }
    }
}

/** Immutable CPU semantic payload paired with one prepared child WGSL program. */
internal sealed interface GPUPreparedRuntimeEffectChildCpuProgram {
    data class Shader(
        val materialKey: String,
    ) : GPUPreparedRuntimeEffectChildCpuProgram

    data class Matrix(
        val values: List<Float>,
    ) : GPUPreparedRuntimeEffectChildCpuProgram

    data class BlendConstant(
        val sourcePremul: List<Float>,
        val modeLabel: String,
    ) : GPUPreparedRuntimeEffectChildCpuProgram

    data class Compose(
        val inner: GPUPreparedRuntimeEffectChildCpuProgram,
        val outer: GPUPreparedRuntimeEffectChildCpuProgram,
    ) : GPUPreparedRuntimeEffectChildCpuProgram

    data class ModeBlender(
        val modeLabel: String,
    ) : GPUPreparedRuntimeEffectChildCpuProgram
}

/**
 * Immutable, handle-free result of prepared-material compilation.
 *
 * This DTO owns no lowering, routing, shader generation, or resource decision.
 */
class GPUPreparedMaterialProgram private constructor(
    val materialKey: String,
    val wgslSource: String,
    val entryPoint: String,
    val composableFragment: GPUPreparedMaterialFragment,
    uniformBytes: List<Int>,
    sampledResources: List<GPUPreparedMaterialSampledResource>,
    childPrograms: List<GPUPreparedRuntimeEffectChildProgram>,
    val paintAlpha: Float,
    val sourceKind: GPUMaterialSourceKind,
    val preCoverageSourceAlpha: GPUSourceAlphaClassification,
    val abiHash: String,
    private val admission: GPUPreparedMaterialProgramAdmission,
) {
    val uniformBytes: List<Int> = immutableList(uniformBytes)
    val sampledResources: List<GPUPreparedMaterialSampledResource> =
        immutableList(sampledResources)
    val childPrograms: List<GPUPreparedRuntimeEffectChildProgram> =
        immutableList(childPrograms)

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
        require(abiHash.matches(Regex("sha256:[0-9a-f]{64}"))) {
            "Prepared material ABI hash must be canonical"
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
        require(this.childPrograms.map { child -> child.name }.distinct().size == this.childPrograms.size) {
            "Prepared runtime-effect child program names must be unique"
        }
    }

    @JvmSynthetic
    internal fun authenticatedSnapshot(): GPUPreparedMaterialProgram =
        createAuthenticatedCore(
            materialKey = materialKey,
            wgslSource = wgslSource,
            entryPoint = entryPoint,
            uniformBytes = uniformBytes,
            sampledResources = sampledResources,
            childPrograms = childPrograms,
            paintAlpha = paintAlpha,
            sourceKind = sourceKind,
            preCoverageSourceAlpha = preCoverageSourceAlpha,
            admission = admission,
            retainedFragment = composableFragment,
            retainedAbiHash = abiHash,
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

    operator fun component10(): GPUSourceAlphaClassification = preCoverageSourceAlpha

    operator fun component11(): List<GPUPreparedRuntimeEffectChildProgram> = childPrograms

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUPreparedMaterialProgram) return false

        return materialKey == other.materialKey &&
            wgslSource == other.wgslSource &&
            entryPoint == other.entryPoint &&
            composableFragment == other.composableFragment &&
            uniformBytes == other.uniformBytes &&
            sampledResources == other.sampledResources &&
            childPrograms == other.childPrograms &&
            paintAlpha.compareTo(other.paintAlpha) == 0 &&
            sourceKind == other.sourceKind &&
            preCoverageSourceAlpha == other.preCoverageSourceAlpha &&
            abiHash == other.abiHash
    }

    override fun hashCode(): Int {
        var result = materialKey.hashCode()
        result = 31 * result + wgslSource.hashCode()
        result = 31 * result + entryPoint.hashCode()
        result = 31 * result + composableFragment.hashCode()
        result = 31 * result + uniformBytes.hashCode()
        result = 31 * result + sampledResources.hashCode()
        result = 31 * result + childPrograms.hashCode()
        result = 31 * result + paintAlpha.hashCode()
        result = 31 * result + sourceKind.hashCode()
        result = 31 * result + preCoverageSourceAlpha.hashCode()
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
            "childPrograms=$childPrograms, " +
            "paintAlpha=$paintAlpha, " +
            "sourceKind=$sourceKind, " +
            "preCoverageSourceAlpha=$preCoverageSourceAlpha, " +
            "abiHash=$abiHash)"

    companion object {
        @JvmSynthetic
        internal fun createAuthenticated(
            wgslSource: String,
            entryPoint: String,
            uniformBytes: List<Int>,
            sampledResources: List<GPUPreparedMaterialSampledResource>,
            paintAlpha: Float,
            sourceKind: GPUMaterialSourceKind,
            preCoverageSourceAlpha: GPUSourceAlphaClassification,
            admission: GPUPreparedMaterialProgramAdmission,
            childPrograms: List<GPUPreparedRuntimeEffectChildProgram> = emptyList(),
        ): GPUPreparedMaterialProgram =
            createAuthenticatedCore(
                materialKey = admission.materialKey(),
                wgslSource = wgslSource,
                entryPoint = entryPoint,
                uniformBytes = uniformBytes,
                sampledResources = sampledResources,
                childPrograms = childPrograms,
                paintAlpha = paintAlpha,
                sourceKind = sourceKind,
                preCoverageSourceAlpha = preCoverageSourceAlpha,
                admission = admission,
                retainedFragment = null,
                retainedAbiHash = null,
            )

        private fun createAuthenticatedCore(
            materialKey: String,
            wgslSource: String,
            entryPoint: String,
            uniformBytes: List<Int>,
            sampledResources: List<GPUPreparedMaterialSampledResource>,
            childPrograms: List<GPUPreparedRuntimeEffectChildProgram>,
            paintAlpha: Float,
            sourceKind: GPUMaterialSourceKind,
            preCoverageSourceAlpha: GPUSourceAlphaClassification,
            admission: GPUPreparedMaterialProgramAdmission,
            retainedFragment: GPUPreparedMaterialFragment?,
            retainedAbiHash: String?,
        ): GPUPreparedMaterialProgram {
            admission.requireMatches(
                materialKey = materialKey,
                wgslSource = wgslSource,
                entryPoint = entryPoint,
                sourceKind = sourceKind,
                uniformBytes = uniformBytes,
                sampledResources = sampledResources,
                childPrograms = childPrograms,
                paintAlpha = paintAlpha,
                preCoverageSourceAlpha = preCoverageSourceAlpha,
            )
            val authoritativeFragment = GPUPreparedMaterialFragment.createAuthenticated(
                admission.fragmentAdmission,
            )
            retainedFragment?.let { retained ->
                require(retained.declarationsWgsl == authoritativeFragment.declarationsWgsl)
                require(
                    retained.evaluationFunctionWgsl ==
                        authoritativeFragment.evaluationFunctionWgsl,
                )
                require(retained.evaluationFunction == authoritativeFragment.evaluationFunction)
                require(retained.uniformBinding == authoritativeFragment.uniformBinding)
                require(retained.sampledBindings == authoritativeFragment.sampledBindings)
                require(retained.colorContract == authoritativeFragment.colorContract)
                require(retained.coordinateContract == authoritativeFragment.coordinateContract)
                require(retained.fragmentHash == authoritativeFragment.fragmentHash)
                require(retained.abiHash == authoritativeFragment.abiHash)
            }
            val fragment = retainedFragment ?: authoritativeFragment
            val abiHash = admission.programAbiHash(
                fragmentHash = authoritativeFragment.fragmentHash,
                fragmentAbiHash = authoritativeFragment.abiHash,
            )
            retainedAbiHash?.let { retained ->
                require(retained == abiHash) {
                    "Prepared material ABI must match its admitted program facts"
                }
            }
            return GPUPreparedMaterialProgram(
                materialKey = materialKey,
                wgslSource = wgslSource,
                entryPoint = entryPoint,
                composableFragment = fragment,
                uniformBytes = uniformBytes,
                sampledResources = sampledResources,
                childPrograms = childPrograms,
                paintAlpha = paintAlpha,
                sourceKind = sourceKind,
                preCoverageSourceAlpha = preCoverageSourceAlpha,
                abiHash = abiHash,
                admission = admission,
            )
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

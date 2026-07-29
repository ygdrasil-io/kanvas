package org.graphiks.kanvas.gpu.renderer.filters

import java.security.MessageDigest
import java.util.Objects

/** Stable filter node identity independent of object addresses. */
@JvmInline
value class GPUPreparedFilterNodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPreparedFilterNodeId.value must not be blank" }
    }
}

/** Canonical public image filter kinds (22 total). */
enum class GPUPreparedFilterKind {
    Crop,
    Blur,
    DropShadow,
    ColorFilter,
    Compose,
    Blend,
    Dilate,
    Erode,
    DistantLitDiffuse,
    PointLitDiffuse,
    SpotLitDiffuse,
    DistantLitSpecular,
    PointLitSpecular,
    SpotLitSpecular,
    Offset,
    Tile,
    Merge,
    DisplacementMap,
    Picture,
    Magnifier,
    MatrixConvolution,
    RuntimeEffect,
}

/** Filter graph input reference. */
sealed interface GPUPreparedFilterInputRef {
    fun identityFragment(): String

    data object ImplicitSource : GPUPreparedFilterInputRef {
        override fun identityFragment() = "implicit_source"
    }

    data object TransparentBlack : GPUPreparedFilterInputRef {
        override fun identityFragment() = "transparent_black"
    }

    data class Node(val id: GPUPreparedFilterNodeId) : GPUPreparedFilterInputRef {
        override fun identityFragment() = "node:${id.value}"
    }

    data class Picture(val pictureIdentity: String) : GPUPreparedFilterInputRef {
        override fun identityFragment() = "picture:$pictureIdentity"
    }

    data class Backdrop(val destinationPlanIdentity: String) : GPUPreparedFilterInputRef {
        override fun identityFragment() = "backdrop:$destinationPlanIdentity"
    }
}

/** Typed filter parameters sealed hierarchy. */
sealed interface GPUPreparedFilterParameters {
    fun canonicalIdentity(): String
}

//
// Parameter classes with ONLY primitive/scalar types → data classes
//

data class BlurParams(
    val sigmaX: Float,
    val sigmaY: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "blur:${sigmaX.toRawBits()}:${sigmaY.toRawBits()}"
}

data class CropParams(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "crop:${x.toRawBits()}:${y.toRawBits()}:${width.toRawBits()}:${height.toRawBits()}"
}

data class OffsetParams(
    val dx: Float,
    val dy: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "offset:${dx.toRawBits()}:${dy.toRawBits()}"
}

data class BlendParams(
    val mode: String,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String = "blend:$mode"
}

data class ComposeParams(
    val inner: GPUPreparedFilterInputRef,
    val outer: GPUPreparedFilterInputRef,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "compose:inner=${inner.identityFragment()}:outer=${outer.identityFragment()}"
}

data class DilateParams(
    val radiusX: Float,
    val radiusY: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "dilate:${radiusX.toRawBits()}:${radiusY.toRawBits()}"
}

data class ErodeParams(
    val radiusX: Float,
    val radiusY: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "erode:${radiusX.toRawBits()}:${radiusY.toRawBits()}"
}

data class TileParams(
    val srcX: Float,
    val srcY: Float,
    val srcWidth: Float,
    val srcHeight: Float,
    val tileMode: String,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "tile:${srcX.toRawBits()}:${srcY.toRawBits()}:${srcWidth.toRawBits()}:${srcHeight.toRawBits()}:$tileMode"
}

data class MergeParams(
    val inputs: List<GPUPreparedFilterInputRef>,
) : GPUPreparedFilterParameters {
    init {
        require(inputs.isNotEmpty()) { "Merge must have at least one input" }
    }

    override fun canonicalIdentity(): String =
        "merge:${inputs.joinToString(":") { it.identityFragment() }}"
}

data class DisplacementMapParams(
    val xChannel: String,
    val yChannel: String,
    val scale: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "displacement:${xChannel}:${yChannel}:${scale.toRawBits()}"
}

data class PictureParams(
    val pictureIdentity: String,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String = "picture:$pictureIdentity"
}

data class MagnifierParams(
    val srcX: Float,
    val srcY: Float,
    val srcWidth: Float,
    val srcHeight: Float,
    val lensX: Float,
    val lensY: Float,
    val lensWidth: Float,
    val lensHeight: Float,
    val zoom: Float,
    val inset: Float,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        "magnifier:${srcX.toRawBits()}:${srcY.toRawBits()}:${srcWidth.toRawBits()}" +
            ":${srcHeight.toRawBits()}:${lensX.toRawBits()}:${lensY.toRawBits()}" +
            ":${lensWidth.toRawBits()}:${lensHeight.toRawBits()}:${zoom.toRawBits()}:${inset.toRawBits()}"
}

//
// Parameter classes with FloatArray → regular classes with defensive copy
//

class ColorFilterParams(matrix: FloatArray) : GPUPreparedFilterParameters {

    val matrix: FloatArray = matrix.copyOf()

    init {
        require(this.matrix.size == 20) { "Color matrix must have exactly 20 entries" }
    }

    override fun canonicalIdentity(): String =
        "color_filter:${matrix.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorFilterParams) return false
        return matrix.contentEquals(other.matrix)
    }

    override fun hashCode(): Int = matrix.contentHashCode()

    override fun toString(): String = "ColorFilterParams(matrix=${matrix.contentToString()})"
}

class DropShadowParams(
    val dx: Float,
    val dy: Float,
    val sigmaX: Float,
    val sigmaY: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 4) { "Drop shadow color must have exactly 4 entries" }
    }

    override fun canonicalIdentity(): String =
        "drop_shadow:${dx.toRawBits()}:${dy.toRawBits()}:${sigmaX.toRawBits()}:${sigmaY.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropShadowParams) return false
        return dx == other.dx && dy == other.dy &&
            sigmaX == other.sigmaX && sigmaY == other.sigmaY &&
            color.contentEquals(other.color)
    }

    override fun hashCode(): Int = Objects.hash(dx, dy, sigmaX, sigmaY, color.contentHashCode())

    override fun toString(): String =
        "DropShadowParams(dx=$dx, dy=$dy, sigmaX=$sigmaX, sigmaY=$sigmaY, color=${color.contentToString()})"
}

class DistantLitDiffuseParams(
    val dx: Float,
    val dy: Float,
    val dz: Float,
    val surfaceScale: Float,
    val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }

    override fun canonicalIdentity(): String =
        "distant_lit_diffuse:${dx.toRawBits()}:${dy.toRawBits()}:${dz.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${kd.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistantLitDiffuseParams) return false
        return dx == other.dx && dy == other.dy && dz == other.dz &&
            surfaceScale == other.surfaceScale && kd == other.kd &&
            color.contentEquals(other.color)
    }

    override fun hashCode(): Int =
        Objects.hash(dx, dy, dz, surfaceScale, kd, color.contentHashCode())

    override fun toString(): String =
        "DistantLitDiffuseParams(dx=$dx, dy=$dy, dz=$dz, surfaceScale=$surfaceScale, kd=$kd)"
}

class PointLitDiffuseParams(
    val px: Float,
    val py: Float,
    val pz: Float,
    val surfaceScale: Float,
    val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }

    override fun canonicalIdentity(): String =
        "point_lit_diffuse:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${kd.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointLitDiffuseParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            surfaceScale == other.surfaceScale && kd == other.kd &&
            color.contentEquals(other.color)
    }

    override fun hashCode(): Int =
        Objects.hash(px, py, pz, surfaceScale, kd, color.contentHashCode())

    override fun toString(): String =
        "PointLitDiffuseParams(px=$px, py=$py, pz=$pz, surfaceScale=$surfaceScale, kd=$kd)"
}

class SpotLitDiffuseParams(
    val px: Float,
    val py: Float,
    val pz: Float,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val cutoff: Float,
    val exponent: Float,
    val surfaceScale: Float,
    val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }

    override fun canonicalIdentity(): String =
        "spot_lit_diffuse:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}" +
            ":${tx.toRawBits()}:${ty.toRawBits()}:${tz.toRawBits()}" +
            ":${cutoff.toRawBits()}:${exponent.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${kd.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpotLitDiffuseParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            tx == other.tx && ty == other.ty && tz == other.tz &&
            cutoff == other.cutoff && exponent == other.exponent &&
            surfaceScale == other.surfaceScale && kd == other.kd &&
            color.contentEquals(other.color)
    }

    override fun hashCode(): Int =
        Objects.hash(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, kd, color.contentHashCode())

    override fun toString(): String =
        "SpotLitDiffuseParams(px=$px, py=$py, pz=$pz, kd=$kd)"
}

class DistantLitSpecularParams(
    val dx: Float,
    val dy: Float,
    val dz: Float,
    val surfaceScale: Float,
    val ks: Float,
    val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }

    override fun canonicalIdentity(): String =
        "distant_lit_specular:${dx.toRawBits()}:${dy.toRawBits()}:${dz.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistantLitSpecularParams) return false
        return dx == other.dx && dy == other.dy && dz == other.dz &&
            surfaceScale == other.surfaceScale && ks == other.ks &&
            shininess == other.shininess && color.contentEquals(other.color)
    }

    override fun hashCode(): Int =
        Objects.hash(dx, dy, dz, surfaceScale, ks, shininess, color.contentHashCode())

    override fun toString(): String =
        "DistantLitSpecularParams(dx=$dx, dy=$dy, dz=$dz, ks=$ks, shininess=$shininess)"
}

class PointLitSpecularParams(
    val px: Float,
    val py: Float,
    val pz: Float,
    val surfaceScale: Float,
    val ks: Float,
    val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }

    override fun canonicalIdentity(): String =
        "point_lit_specular:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointLitSpecularParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            surfaceScale == other.surfaceScale && ks == other.ks &&
            shininess == other.shininess && color.contentEquals(other.color)
    }

    override fun hashCode(): Int =
        Objects.hash(px, py, pz, surfaceScale, ks, shininess, color.contentHashCode())

    override fun toString(): String =
        "PointLitSpecularParams(px=$px, py=$py, pz=$pz, ks=$ks, shininess=$shininess)"
}

class SpotLitSpecularParams(
    val px: Float,
    val py: Float,
    val pz: Float,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val cutoff: Float,
    val exponent: Float,
    val surfaceScale: Float,
    val ks: Float,
    val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {

    val color: FloatArray = color.copyOf()

    init {
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }

    override fun canonicalIdentity(): String =
        "spot_lit_specular:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}" +
            ":${tx.toRawBits()}:${ty.toRawBits()}:${tz.toRawBits()}" +
            ":${cutoff.toRawBits()}:${exponent.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}" +
            ":${color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpotLitSpecularParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            tx == other.tx && ty == other.ty && tz == other.tz &&
            cutoff == other.cutoff && exponent == other.exponent &&
            surfaceScale == other.surfaceScale && ks == other.ks &&
            shininess == other.shininess && color.contentEquals(other.color)
    }

    override fun hashCode(): Int =
        Objects.hash(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, ks, shininess, color.contentHashCode())

    override fun toString(): String =
        "SpotLitSpecularParams(px=$px, py=$py, pz=$pz, ks=$ks, shininess=$shininess)"
}

class MatrixConvolutionParams(
    kernel: FloatArray,
    val gain: Float,
    val bias: Float,
    val kernelOffsetX: Int,
    val kernelOffsetY: Int,
    val convolveAlpha: Boolean,
    val tileMode: String,
) : GPUPreparedFilterParameters {

    val kernel: FloatArray = kernel.copyOf()

    val kernelHash: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        this.kernel.forEach { f ->
            val bits = f.toRawBits()
            digest.update((bits shr 24).toByte())
            digest.update((bits shr 16).toByte())
            digest.update((bits shr 8).toByte())
            digest.update(bits.toByte())
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun canonicalIdentity(): String =
        "matrix_convolution:${gain.toRawBits()}:${bias.toRawBits()}" +
            ":${kernelOffsetX}:${kernelOffsetY}:${convolveAlpha}:${tileMode}:kernel=$kernelHash"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MatrixConvolutionParams) return false
        return gain == other.gain && bias == other.bias &&
            kernelOffsetX == other.kernelOffsetX && kernelOffsetY == other.kernelOffsetY &&
            convolveAlpha == other.convolveAlpha && tileMode == other.tileMode &&
            kernel.contentEquals(other.kernel)
    }

    override fun hashCode(): Int =
        Objects.hash(gain, bias, kernelOffsetX, kernelOffsetY, convolveAlpha, tileMode, kernel.contentHashCode())

    override fun toString(): String =
        "MatrixConvolutionParams(gain=$gain, bias=$bias, kernelOffset=($kernelOffsetX,$kernelOffsetY), " +
            "convolveAlpha=$convolveAlpha, tileMode=$tileMode)"
}

class RuntimeEffectParams(
    val effectId: String,
    val effectVersion: Int,
    uniforms: Map<String, FloatArray>,
    val children: Map<String, GPUPreparedFilterInputRef>,
) : GPUPreparedFilterParameters {

    val uniforms: Map<String, FloatArray> = uniforms.mapValues { (_, v) -> v.copyOf() }

    override fun canonicalIdentity(): String {
        val uniformPart = uniforms.entries
            .sortedBy { it.key }
            .joinToString(";") { (k, v) ->
                "$k=${v.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
            }
        val childPart = children.entries
            .sortedBy { it.key }
            .joinToString(";") { (k, v) -> "$k=${v.identityFragment()}" }
        return "runtime:$effectId:v$effectVersion:uniforms=[$uniformPart]:children=[$childPart]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeEffectParams) return false
        if (effectId != other.effectId || effectVersion != other.effectVersion) return false
        if (uniforms.size != other.uniforms.size) return false
        for ((k, v) in uniforms) {
            val ov = other.uniforms[k] ?: return false
            if (!v.contentEquals(ov)) return false
        }
        return children == other.children
    }

    override fun hashCode(): Int {
        var result = effectId.hashCode()
        result = 31 * result + effectVersion
        for ((k, v) in uniforms.entries.sortedBy { it.key }) {
            result = 31 * result + k.hashCode()
            result = 31 * result + v.contentHashCode()
        }
        result = 31 * result + children.hashCode()
        return result
    }

    override fun toString(): String =
        "RuntimeEffectParams(effectId=$effectId, version=$effectVersion, uniformKeys=${uniforms.keys}, childKeys=${children.keys})"
}

/** Typed, immutable filter graph node. */
data class GPUPreparedFilterNode(
    val id: GPUPreparedFilterNodeId,
    val kind: GPUPreparedFilterKind,
    val inputs: List<GPUPreparedFilterInputRef>,
    val parameters: GPUPreparedFilterParameters,
    val provenance: String,
) {
    fun canonicalIdentity(): String =
        "node:${id.value}:kind=$kind:inputs=[${inputs.joinToString(";") { it.identityFragment() }}]:" +
            "params=(${parameters.canonicalIdentity()}):provenance=$provenance"

    fun parameterMap(): Map<String, String> {
        val combined = mutableMapOf<String, String>()
        combined["kind"] = kind.name
        combined["provenance"] = provenance
        combined["inputCount"] = inputs.size.toString()
        combined["params"] = parameters.canonicalIdentity()
        inputs.withIndex().associateTo(combined) { (i, ref) -> "input[$i]" to ref.identityFragment() }
        return java.util.Collections.unmodifiableMap(combined)
    }
}

/** Typed, immutable filter DAG. */
class GPUPreparedFilterGraph(
    nodes: List<GPUPreparedFilterNode>,
    val output: GPUPreparedFilterInputRef,
    val identity: String,
) {
    val nodes: List<GPUPreparedFilterNode> = java.util.Collections.unmodifiableList(ArrayList(nodes))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUPreparedFilterGraph) return false
        return nodes == other.nodes && output == other.output && identity == other.identity
    }

    override fun hashCode(): Int = Objects.hash(nodes, output, identity)

    override fun toString(): String =
        "GPUPreparedFilterGraph(nodes=${nodes.size}, output=${output.identityFragment()}, identity=$identity)"
}

/** Proof of a normalization rewrite. */
data class GPUPreparedFilterRewriteProof(
    val rule: String,
    val sourceNodeIds: List<GPUPreparedFilterNodeId>,
    val resultNodeIds: List<GPUPreparedFilterNodeId>,
    val removedIntermediateCount: Int,
    val inputBoundsIdentity: String,
    val outputBoundsIdentity: String,
)

/** Normalized filter graph with rewrite proofs. */
data class GPUPreparedFilterNormalization(
    val graph: GPUPreparedFilterGraph,
    val rewrites: List<GPUPreparedFilterRewriteProof>,
    val materializationNodeIds: Set<GPUPreparedFilterNodeId>,
)

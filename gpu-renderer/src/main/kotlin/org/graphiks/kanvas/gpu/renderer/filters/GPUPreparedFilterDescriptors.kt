package org.graphiks.kanvas.gpu.renderer.filters

import java.security.MessageDigest
import java.util.Collections
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

private fun requireFinite(vararg values: Float, label: String) {
    for (v in values) {
        require(v.isFinite()) { "$label: non-finite value $v" }
    }
}

//
// Parameter classes
//

data class BlurParams(
    val sigmaX: Float,
    val sigmaY: Float,
    val tileMode: String = "clamp",
) : GPUPreparedFilterParameters {
    init {
        requireFinite(sigmaX, sigmaY, label = "BlurParams")
        require(sigmaX >= 0f && sigmaY >= 0f) { "BlurParams: sigma must be >= 0" }
    }

    override fun canonicalIdentity(): String =
        "blur:${sigmaX.toRawBits()}:${sigmaY.toRawBits()}:$tileMode"
}

data class CropParams(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val tileMode: String = "decal",
) : GPUPreparedFilterParameters {
    init {
        requireFinite(x, y, width, height, label = "CropParams")
    }

    override fun canonicalIdentity(): String =
        "crop:${x.toRawBits()}:${y.toRawBits()}:${width.toRawBits()}:${height.toRawBits()}:$tileMode"
}

data class OffsetParams(
    val dx: Float,
    val dy: Float,
) : GPUPreparedFilterParameters {
    init { requireFinite(dx, dy, label = "OffsetParams") }

    override fun canonicalIdentity(): String =
        "offset:${dx.toRawBits()}:${dy.toRawBits()}"
}

class ColorFilterParams(matrix: FloatArray) : GPUPreparedFilterParameters {

    private val _matrix: FloatArray = matrix.copyOf()

    val matrix: FloatArray get() = _matrix.copyOf()

    init {
        require(this._matrix.size == 20) { "Color matrix must have exactly 20 entries" }
    }

    override fun canonicalIdentity(): String =
        "color_filter:${_matrix.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorFilterParams) return false
        return _matrix.contentEquals(other._matrix)
    }

    override fun hashCode(): Int = _matrix.contentHashCode()

    override fun toString(): String = "ColorFilterParams(matrix=${_matrix.contentToString()})"
}

data class BlendParams(val mode: String) : GPUPreparedFilterParameters {
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
    init { requireFinite(radiusX, radiusY, label = "DilateParams") }

    override fun canonicalIdentity(): String =
        "dilate:${radiusX.toRawBits()}:${radiusY.toRawBits()}"
}

data class ErodeParams(
    val radiusX: Float,
    val radiusY: Float,
) : GPUPreparedFilterParameters {
    init { requireFinite(radiusX, radiusY, label = "ErodeParams") }

    override fun canonicalIdentity(): String =
        "erode:${radiusX.toRawBits()}:${radiusY.toRawBits()}"
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
        requireFinite(dx, dy, sigmaX, sigmaY, label = "DropShadowParams")
        require(this.color.size == 4) { "Drop shadow color must have exactly 4 entries" }
    }

    override fun canonicalIdentity(): String =
        "drop_shadow:${dx.toRawBits()}:${dy.toRawBits()}:${sigmaX.toRawBits()}:${sigmaY.toRawBits()}" +
            ":${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropShadowParams) return false
        return dx == other.dx && dy == other.dy &&
            sigmaX == other.sigmaX && sigmaY == other.sigmaY &&
            color.contentEquals(other.color)
    }

    override fun hashCode(): Int = Objects.hash(dx, dy, sigmaX, sigmaY, color.contentHashCode())

    override fun toString(): String =
        "DropShadowParams(dx=$dx, dy=$dy, sigmaX=$sigmaX, sigmaY=$sigmaY)"
}

data class TileParams(
    val srcLeft: Float,
    val srcTop: Float,
    val srcRight: Float,
    val srcBottom: Float,
    val dstX: Float,
    val dstY: Float,
    val dstRight: Float,
    val dstBottom: Float,
) : GPUPreparedFilterParameters {
    init {
        requireFinite(srcLeft, srcTop, srcRight, srcBottom, dstX, dstY, dstRight, dstBottom, label = "TileParams")
    }

    override fun canonicalIdentity(): String =
        "tile:src=${srcLeft.toRawBits()}:${srcTop.toRawBits()}:${srcRight.toRawBits()}:${srcBottom.toRawBits()}" +
            ":dst=${dstX.toRawBits()}:${dstY.toRawBits()}:${dstRight.toRawBits()}:${dstBottom.toRawBits()}"
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
    init { requireFinite(scale, label = "DisplacementMapParams") }

    override fun canonicalIdentity(): String =
        "displacement:${xChannel}:${yChannel}:${scale.toRawBits()}"
}

class PictureParams(
    val pictureIdentity: String,
    val srcX: Float = -1f,
    val srcY: Float = -1f,
    val srcW: Float = -1f,
    val srcH: Float = -1f,
) : GPUPreparedFilterParameters {

    val hasExplicitSrc: Boolean get() = srcX >= 0f

    override fun canonicalIdentity(): String =
        if (hasExplicitSrc)
            "picture:$pictureIdentity:src=${srcX.toRawBits()}:${srcY.toRawBits()}:${srcW.toRawBits()}:${srcH.toRawBits()}"
        else "picture:$pictureIdentity"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PictureParams) return false
        return pictureIdentity == other.pictureIdentity &&
            srcX == other.srcX && srcY == other.srcY && srcW == other.srcW && srcH == other.srcH
    }

    override fun hashCode(): Int = Objects.hash(pictureIdentity, srcX, srcY, srcW, srcH)

    override fun toString(): String =
        "PictureParams(id=$pictureIdentity, hasSrc=$hasExplicitSrc)"
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
    init {
        requireFinite(srcX, srcY, srcWidth, srcHeight, lensX, lensY, lensWidth, lensHeight, zoom, inset, label = "MagnifierParams")
    }

    override fun canonicalIdentity(): String =
        "magnifier:${srcX.toRawBits()}:${srcY.toRawBits()}:${srcWidth.toRawBits()}" +
            ":${srcHeight.toRawBits()}:${lensX.toRawBits()}:${lensY.toRawBits()}" +
            ":${lensWidth.toRawBits()}:${lensHeight.toRawBits()}:${zoom.toRawBits()}:${inset.toRawBits()}"
}

class MatrixConvolutionParams(
    kernel: FloatArray,
    val kernelSizeX: Int,
    val kernelSizeY: Int,
    val gain: Float,
    val bias: Float,
    val kernelOffsetX: Int,
    val kernelOffsetY: Int,
    val convolveAlpha: Boolean,
    val tileMode: String,
) : GPUPreparedFilterParameters {

    private val _kernel: FloatArray = kernel.copyOf()

    val kernel: FloatArray get() = _kernel.copyOf()

    init {
        requireFinite(gain, bias, label = "MatrixConvolutionParams")
        require(kernelSizeX > 0 && kernelSizeY > 0) { "kernelSize must be positive" }
        require(this._kernel.size == kernelSizeX * kernelSizeY) {
            "kernel size ${this._kernel.size} != $kernelSizeX * $kernelSizeY"
        }
        for ((i, v) in this._kernel.withIndex()) {
            require(v.isFinite()) { "MatrixConvolutionParams kernel[$i]: non-finite value $v" }
        }
    }

    val kernelHash: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        this._kernel.forEach { f ->
            val bits = f.toRawBits()
            digest.update((bits shr 24).toByte())
            digest.update((bits shr 16).toByte())
            digest.update((bits shr 8).toByte())
            digest.update(bits.toByte())
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun canonicalIdentity(): String =
        "matrix_convolution:${kernelSizeX}x${kernelSizeY}:${gain.toRawBits()}:${bias.toRawBits()}" +
            ":${kernelOffsetX}:${kernelOffsetY}:${convolveAlpha}:${tileMode}:kernel=$kernelHash"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MatrixConvolutionParams) return false
        return kernelSizeX == other.kernelSizeX && kernelSizeY == other.kernelSizeY &&
            gain == other.gain && bias == other.bias &&
            kernelOffsetX == other.kernelOffsetX && kernelOffsetY == other.kernelOffsetY &&
            convolveAlpha == other.convolveAlpha && tileMode == other.tileMode &&
            kernel.contentEquals(other.kernel)
    }

    override fun hashCode(): Int =
        Objects.hash(kernelSizeX, kernelSizeY, gain, bias, kernelOffsetX, kernelOffsetY,
            convolveAlpha, tileMode, kernel.contentHashCode())

    override fun toString(): String =
        "MatrixConvolutionParams(size=${kernelSizeX}x${kernelSizeY}, gain=$gain, tileMode=$tileMode)"
}

class RuntimeEffectParams(
    val effectId: String,
    val effectVersion: Int,
    uniforms: Map<String, FloatArray>,
    val children: Map<String, GPUPreparedFilterInputRef>,
    val childShaderName: String = "src",
) : GPUPreparedFilterParameters {

    val uniforms: Map<String, FloatArray> =
        Collections.unmodifiableMap(uniforms.mapValues { (_, v) -> v.copyOf() })

    override fun canonicalIdentity(): String {
        val uniformPart = uniforms.entries
            .sortedBy { it.key }
            .joinToString(";") { (k, v) ->
                "$k=${v.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
            }
        val childPart = children.entries
            .sortedBy { it.key }
            .joinToString(";") { (k, v) -> "$k=${v.identityFragment()}" }
        return "runtime:$effectId:v$effectVersion:uniforms=[$uniformPart]:children=[$childPart]:shader=$childShaderName"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeEffectParams) return false
        if (effectId != other.effectId || effectVersion != other.effectVersion) return false
        if (childShaderName != other.childShaderName) return false
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
        result = 31 * result + childShaderName.hashCode()
        for ((k, v) in uniforms.entries.sortedBy { it.key }) {
            result = 31 * result + k.hashCode()
            result = 31 * result + v.contentHashCode()
        }
        result = 31 * result + children.hashCode()
        return result
    }

    override fun toString(): String =
        "RuntimeEffectParams(effectId=$effectId, version=$effectVersion, shader=$childShaderName)"
}

private fun requireFinite(lightColor: String, color: FloatArray) {
    for (f in color) require(f.isFinite()) { "$lightColor: non-finite component $f" }
}

class DistantLitDiffuseParams(
    val dx: Float, val dy: Float, val dz: Float,
    val surfaceScale: Float, val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    val color: FloatArray = color.copyOf()
    init {
        requireFinite(dx, dy, dz, surfaceScale, kd, label = "DistantLitDiffuseParams")
        requireFinite("DistantLitDiffuseParams", color)
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "distant_lit_diffuse:${dx.toRawBits()}:${dy.toRawBits()}:${dz.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${kd.toRawBits()}:${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistantLitDiffuseParams) return false
        return dx == other.dx && dy == other.dy && dz == other.dz &&
            surfaceScale == other.surfaceScale && kd == other.kd && this.color.contentEquals(other.color)
    }
    override fun hashCode(): Int = Objects.hash(dx, dy, dz, surfaceScale, kd, color.contentHashCode())
}

class PointLitDiffuseParams(
    val px: Float, val py: Float, val pz: Float,
    val surfaceScale: Float, val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    val color: FloatArray = color.copyOf()
    init {
        requireFinite(px, py, pz, surfaceScale, kd, label = "PointLitDiffuseParams")
        requireFinite("PointLitDiffuseParams", color)
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "point_lit_diffuse:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${surfaceScale.toRawBits()}:${kd.toRawBits()}:${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointLitDiffuseParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            surfaceScale == other.surfaceScale && kd == other.kd && this.color.contentEquals(other.color)
    }
    override fun hashCode(): Int = Objects.hash(px, py, pz, surfaceScale, kd, color.contentHashCode())
}

class SpotLitDiffuseParams(
    val px: Float, val py: Float, val pz: Float,
    val tx: Float, val ty: Float, val tz: Float,
    val cutoff: Float, val exponent: Float,
    val surfaceScale: Float, val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    val color: FloatArray = color.copyOf()
    init {
        requireFinite(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, kd, label = "SpotLitDiffuseParams")
        requireFinite("SpotLitDiffuseParams", color)
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "spot_lit_diffuse:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${tx.toRawBits()}:${ty.toRawBits()}:${tz.toRawBits()}:${cutoff.toRawBits()}:${exponent.toRawBits()}:${surfaceScale.toRawBits()}:${kd.toRawBits()}:${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpotLitDiffuseParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            tx == other.tx && ty == other.ty && tz == other.tz &&
            cutoff == other.cutoff && exponent == other.exponent &&
            surfaceScale == other.surfaceScale && kd == other.kd && this.color.contentEquals(other.color)
    }
    override fun hashCode(): Int = Objects.hash(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, kd, color.contentHashCode())
}

class DistantLitSpecularParams(
    val dx: Float, val dy: Float, val dz: Float,
    val surfaceScale: Float, val ks: Float, val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    val color: FloatArray = color.copyOf()
    init {
        requireFinite(dx, dy, dz, surfaceScale, ks, shininess, label = "DistantLitSpecularParams")
        requireFinite("DistantLitSpecularParams", color)
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "distant_lit_specular:${dx.toRawBits()}:${dy.toRawBits()}:${dz.toRawBits()}:${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}:${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistantLitSpecularParams) return false
        return dx == other.dx && dy == other.dy && dz == other.dz &&
            surfaceScale == other.surfaceScale && ks == other.ks && shininess == other.shininess &&
            this.color.contentEquals(other.color)
    }
    override fun hashCode(): Int = Objects.hash(dx, dy, dz, surfaceScale, ks, shininess, color.contentHashCode())
}

class PointLitSpecularParams(
    val px: Float, val py: Float, val pz: Float,
    val surfaceScale: Float, val ks: Float, val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    val color: FloatArray = color.copyOf()
    init {
        requireFinite(px, py, pz, surfaceScale, ks, shininess, label = "PointLitSpecularParams")
        requireFinite("PointLitSpecularParams", color)
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "point_lit_specular:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}:${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointLitSpecularParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            surfaceScale == other.surfaceScale && ks == other.ks && shininess == other.shininess &&
            this.color.contentEquals(other.color)
    }
    override fun hashCode(): Int = Objects.hash(px, py, pz, surfaceScale, ks, shininess, color.contentHashCode())
}

class SpotLitSpecularParams(
    val px: Float, val py: Float, val pz: Float,
    val tx: Float, val ty: Float, val tz: Float,
    val cutoff: Float, val exponent: Float,
    val surfaceScale: Float, val ks: Float, val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    val color: FloatArray = color.copyOf()
    init {
        requireFinite(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, ks, shininess, label = "SpotLitSpecularParams")
        requireFinite("SpotLitSpecularParams", color)
        require(this.color.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "spot_lit_specular:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${tx.toRawBits()}:${ty.toRawBits()}:${tz.toRawBits()}:${cutoff.toRawBits()}:${exponent.toRawBits()}:${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}:${this.color.fold(0L) { acc, f -> acc * 31 + f.toRawBits().toLong() }}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpotLitSpecularParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            tx == other.tx && ty == other.ty && tz == other.tz &&
            cutoff == other.cutoff && exponent == other.exponent &&
            surfaceScale == other.surfaceScale && ks == other.ks && shininess == other.shininess &&
            this.color.contentEquals(other.color)
    }
    override fun hashCode(): Int = Objects.hash(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, ks, shininess, color.contentHashCode())
}

/** Typed, immutable filter graph node. */
data class GPUPreparedFilterNode(
    val id: GPUPreparedFilterNodeId,
    val kind: GPUPreparedFilterKind,
    val inputs: List<GPUPreparedFilterInputRef>,
    val parameters: GPUPreparedFilterParameters,
    val provenance: String,
) {
    init {
        validateKindParams()
        validateInputArity()
    }

    private fun validateKindParams() {
        val valid = when (kind) {
            GPUPreparedFilterKind.Blur -> parameters is BlurParams
            GPUPreparedFilterKind.Crop -> parameters is CropParams
            GPUPreparedFilterKind.Offset -> parameters is OffsetParams
            GPUPreparedFilterKind.ColorFilter -> parameters is ColorFilterParams
            GPUPreparedFilterKind.DropShadow -> parameters is DropShadowParams
            GPUPreparedFilterKind.Compose -> parameters is ComposeParams
            GPUPreparedFilterKind.Blend -> parameters is BlendParams
            GPUPreparedFilterKind.Dilate -> parameters is DilateParams
            GPUPreparedFilterKind.Erode -> parameters is ErodeParams
            GPUPreparedFilterKind.DistantLitDiffuse -> parameters is DistantLitDiffuseParams
            GPUPreparedFilterKind.PointLitDiffuse -> parameters is PointLitDiffuseParams
            GPUPreparedFilterKind.SpotLitDiffuse -> parameters is SpotLitDiffuseParams
            GPUPreparedFilterKind.DistantLitSpecular -> parameters is DistantLitSpecularParams
            GPUPreparedFilterKind.PointLitSpecular -> parameters is PointLitSpecularParams
            GPUPreparedFilterKind.SpotLitSpecular -> parameters is SpotLitSpecularParams
            GPUPreparedFilterKind.Tile -> parameters is TileParams
            GPUPreparedFilterKind.Merge -> parameters is MergeParams
            GPUPreparedFilterKind.DisplacementMap -> parameters is DisplacementMapParams
            GPUPreparedFilterKind.Picture -> parameters is PictureParams
            GPUPreparedFilterKind.Magnifier -> parameters is MagnifierParams
            GPUPreparedFilterKind.MatrixConvolution -> parameters is MatrixConvolutionParams
            GPUPreparedFilterKind.RuntimeEffect -> parameters is RuntimeEffectParams
        }
        require(valid) { "Node ${id.value}: kind $kind requires matching parameter type, got ${parameters::class.simpleName}" }
    }

    private fun validateInputArity() {
        val expectedRange = when (kind) {
            GPUPreparedFilterKind.Blur -> 1..1
            GPUPreparedFilterKind.Crop -> 1..1
            GPUPreparedFilterKind.Offset -> 1..1
            GPUPreparedFilterKind.ColorFilter -> 1..1
            GPUPreparedFilterKind.DropShadow -> 1..1
            GPUPreparedFilterKind.Dilate -> 1..1
            GPUPreparedFilterKind.Erode -> 1..1
            GPUPreparedFilterKind.DistantLitDiffuse -> 1..1
            GPUPreparedFilterKind.PointLitDiffuse -> 1..1
            GPUPreparedFilterKind.SpotLitDiffuse -> 1..1
            GPUPreparedFilterKind.DistantLitSpecular -> 1..1
            GPUPreparedFilterKind.PointLitSpecular -> 1..1
            GPUPreparedFilterKind.SpotLitSpecular -> 1..1
            GPUPreparedFilterKind.Tile -> 1..1
            GPUPreparedFilterKind.Picture -> 1..1
            GPUPreparedFilterKind.Magnifier -> 1..1
            GPUPreparedFilterKind.MatrixConvolution -> 1..1
            GPUPreparedFilterKind.RuntimeEffect -> 1..1
            GPUPreparedFilterKind.Compose -> 2..2
            GPUPreparedFilterKind.Blend -> 2..2
            GPUPreparedFilterKind.DisplacementMap -> 2..2
            GPUPreparedFilterKind.Merge -> 1..Int.MAX_VALUE
        }
        val count = inputs.size
        require(count in expectedRange) {
            "Node ${id.value}: kind $kind expects ${expectedRange} inputs, got $count"
        }
    }

    fun canonicalIdentity(): String =
        "node:${id.value}:kind=$kind:inputs=[${inputs.joinToString(";") { it.identityFragment() }}]:" +
            "params=(${parameters.canonicalIdentity()}):provenance=$provenance"
}

/** Typed, immutable filter DAG with validated identity. */
class GPUPreparedFilterGraph(
    nodes: List<GPUPreparedFilterNode>,
    val output: GPUPreparedFilterInputRef,
    internal val deprecatedIdentity: String = "",
) {
    val nodes: List<GPUPreparedFilterNode> = Collections.unmodifiableList(ArrayList(nodes))

    val identity: String

    init {
        validateNodeIds(this.nodes)
        validateOutputReference(this.nodes, output)
        validateNoCycles(this.nodes)
        val computed = computeIdentity(this.nodes, output)
        this.identity = computed
        if (deprecatedIdentity.isNotEmpty() && deprecatedIdentity != computed) {
            error("Provided graph identity '$deprecatedIdentity' does not match computed identity '$computed'")
        }
    }

    private fun validateNoCycles(nodes: List<GPUPreparedFilterNode>) {
        val nodeById = nodes.associateBy { it.id.value }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun dfs(id: String): Boolean {
            if (id in visiting) return true
            if (id in visited) return false
            val node = nodeById[id] ?: return false
            visiting.add(id)
            for (input in node.inputs) {
                if (input is GPUPreparedFilterInputRef.Node) {
                    if (dfs(input.id.value)) return true
                }
            }
            visiting.remove(id)
            visited.add(id)
            return false
        }
        for (node in nodes) {
            require(!dfs(node.id.value)) { "Graph contains a cycle detected from node ${node.id.value}" }
        }
    }

    private fun validateNodeIds(nodes: List<GPUPreparedFilterNode>) {
        val ids = nodes.map { it.id.value }
        val dupes = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        require(dupes.isEmpty()) { "Duplicate node IDs: $dupes" }
        val nodeIdSet = ids.toSet()
        for (node in nodes) {
            for (input in node.inputs) {
                if (input is GPUPreparedFilterInputRef.Node) {
                    require(input.id.value in nodeIdSet) {
                        "Node ${node.id.value} references missing node ${input.id.value}"
                    }
                }
            }
        }
    }

    private fun validateOutputReference(
        nodes: List<GPUPreparedFilterNode>,
        output: GPUPreparedFilterInputRef,
    ) {
        if (output is GPUPreparedFilterInputRef.Node) {
            val nodeIds = nodes.map { it.id.value }.toSet()
            require(output.id.value in nodeIds) {
                "Output node ${output.id.value} does not exist in graph"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUPreparedFilterGraph) return false
        return nodes == other.nodes && output == other.output && identity == other.identity
    }

    override fun hashCode(): Int = Objects.hash(nodes, output, identity)

    override fun toString(): String =
        "GPUPreparedFilterGraph(nodes=${nodes.size}, output=${output.identityFragment()}, identity=$identity)"

    companion object {
        fun computeIdentity(
            nodes: List<GPUPreparedFilterNode>,
            output: GPUPreparedFilterInputRef,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (node in nodes.sortedBy { it.id.value }) {
                digest.update(node.canonicalIdentity().toByteArray())
            }
            digest.update(output.identityFragment().toByteArray())
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
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

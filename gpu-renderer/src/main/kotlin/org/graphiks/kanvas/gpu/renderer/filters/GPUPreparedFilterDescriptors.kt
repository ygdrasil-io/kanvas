package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import java.nio.ByteBuffer
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
        init {
            require(pictureIdentity.isNotBlank()) { "pictureIdentity must not be blank" }
        }

        override fun identityFragment() = "picture:$pictureIdentity"
    }

    data class Backdrop(val destinationPlanIdentity: String) : GPUPreparedFilterInputRef {
        init {
            require(destinationPlanIdentity.isNotBlank()) {
                "destinationPlanIdentity must not be blank"
            }
        }

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

private fun canonicalHash(vararg parts: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun FloatArray.canonicalBitsIdentity(): String =
    canonicalHash(
        *buildList {
            add(size.toString())
            this@canonicalBitsIdentity.forEach { add(it.toRawBits().toString()) }
        }.toTypedArray(),
    )

private fun String.toTileMode(): GPUTileMode = when (lowercase()) {
    "clamp" -> GPUTileMode.Clamp
    "repeat" -> GPUTileMode.Repeat
    "mirror" -> GPUTileMode.Mirror
    "decal" -> GPUTileMode.Decal
    else -> throw IllegalArgumentException("Unknown tile mode '$this'")
}

private fun String.toBlendMode(): GPUBlendMode {
    val normalized = replace(Regex("([a-z])([A-Z])"), "\$1_\$2")
        .replace('-', '_')
        .uppercase()
    return GPUBlendMode.entries.firstOrNull {
        it.name == normalized || it.gpuLabel == lowercase()
    } ?: throw IllegalArgumentException("Unknown blend mode '$this'")
}

private fun String.toColorChannel(): GPUColorChannel =
    GPUColorChannel.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown color channel '$this'")

//
// Parameter classes
//

data class BlurParams(
    val sigmaX: Float,
    val sigmaY: Float,
    val tileMode: GPUTileMode = GPUTileMode.Clamp,
) : GPUPreparedFilterParameters {
    constructor(sigmaX: Float, sigmaY: Float, tileMode: String) :
        this(sigmaX, sigmaY, tileMode.toTileMode())

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
    val tileMode: GPUTileMode = GPUTileMode.Clamp,
) : GPUPreparedFilterParameters {
    constructor(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tileMode: String,
    ) : this(x, y, width, height, tileMode.toTileMode())

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
        requireFinite("ColorFilterParams", _matrix)
    }

    override fun canonicalIdentity(): String =
        "color_filter:${_matrix.canonicalBitsIdentity()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorFilterParams) return false
        return _matrix.contentEquals(other._matrix)
    }

    override fun hashCode(): Int = _matrix.contentHashCode()

    override fun toString(): String = "ColorFilterParams(matrix=${_matrix.contentToString()})"
}

data class BlendParams(val mode: GPUBlendMode) : GPUPreparedFilterParameters {
    constructor(mode: String) : this(mode.toBlendMode())

    override fun canonicalIdentity(): String = "blend:$mode"
}

data class ComposeParams(
    val inner: GPUPreparedFilterInputRef,
    val outer: GPUPreparedFilterInputRef,
) : GPUPreparedFilterParameters {
    override fun canonicalIdentity(): String =
        canonicalHash("compose", inner.identityFragment(), outer.identityFragment())
}

data class DilateParams(
    val radiusX: Float,
    val radiusY: Float,
) : GPUPreparedFilterParameters {
    init {
        requireFinite(radiusX, radiusY, label = "DilateParams")
        require(radiusX >= 0f && radiusY >= 0f) {
            "DilateParams: radii must be >= 0"
        }
    }

    override fun canonicalIdentity(): String =
        "dilate:${radiusX.toRawBits()}:${radiusY.toRawBits()}"
}

data class ErodeParams(
    val radiusX: Float,
    val radiusY: Float,
) : GPUPreparedFilterParameters {
    init {
        requireFinite(radiusX, radiusY, label = "ErodeParams")
        require(radiusX >= 0f && radiusY >= 0f) {
            "ErodeParams: radii must be >= 0"
        }
    }

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

    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()

    init {
        requireFinite(dx, dy, sigmaX, sigmaY, label = "DropShadowParams")
        require(sigmaX >= 0f && sigmaY >= 0f) {
            "DropShadowParams: sigma must be >= 0"
        }
        require(colorSnapshot.size == 4) { "Drop shadow color must have exactly 4 entries" }
        requireFinite("DropShadowParams", colorSnapshot)
    }

    override fun canonicalIdentity(): String =
        "drop_shadow:${dx.toRawBits()}:${dy.toRawBits()}:${sigmaX.toRawBits()}:${sigmaY.toRawBits()}" +
            ":${colorSnapshot.canonicalBitsIdentity()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropShadowParams) return false
        return dx == other.dx && dy == other.dy &&
            sigmaX == other.sigmaX && sigmaY == other.sigmaY &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }

    override fun hashCode(): Int =
        Objects.hash(dx, dy, sigmaX, sigmaY, colorSnapshot.contentHashCode())

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

class MergeParams(
    inputs: List<GPUPreparedFilterInputRef>,
) : GPUPreparedFilterParameters {
    val inputs: List<GPUPreparedFilterInputRef> =
        Collections.unmodifiableList(inputs.toList())

    init {
        require(inputs.isNotEmpty()) { "Merge must have at least one input" }
    }

    override fun canonicalIdentity(): String =
        canonicalHash(
            *buildList {
                add("merge")
                add(inputs.size.toString())
                inputs.forEach { add(it.identityFragment()) }
            }.toTypedArray(),
        )

    override fun equals(other: Any?): Boolean =
        this === other || other is MergeParams && inputs == other.inputs

    override fun hashCode(): Int = inputs.hashCode()

    override fun toString(): String = "MergeParams(inputs=$inputs)"
}

data class DisplacementMapParams(
    val xChannel: GPUColorChannel,
    val yChannel: GPUColorChannel,
    val scale: Float,
) : GPUPreparedFilterParameters {
    constructor(xChannel: String, yChannel: String, scale: Float) :
        this(xChannel.toColorChannel(), yChannel.toColorChannel(), scale)

    init { requireFinite(scale, label = "DisplacementMapParams") }

    override fun canonicalIdentity(): String =
        "displacement:${xChannel}:${yChannel}:${scale.toRawBits()}"
}

data class GPUPreparedFilterRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    init {
        requireFinite(x, y, width, height, label = "GPUPreparedFilterRect")
        require(width >= 0f && height >= 0f) {
            "GPUPreparedFilterRect: width and height must be >= 0"
        }
    }

    fun canonicalIdentity(): String = canonicalHash(
        "rect",
        x.toRawBits().toString(),
        y.toRawBits().toString(),
        width.toRawBits().toString(),
        height.toRawBits().toString(),
    )
}

class PictureParams(
    val pictureIdentity: String,
    val sourceRect: GPUPreparedFilterRect? = null,
) : GPUPreparedFilterParameters {
    constructor(
        pictureIdentity: String,
        srcX: Float,
        srcY: Float,
        srcW: Float,
        srcH: Float,
    ) : this(pictureIdentity, GPUPreparedFilterRect(srcX, srcY, srcW, srcH))

    init {
        require(pictureIdentity.isNotBlank()) { "Picture identity must not be blank" }
    }

    val hasExplicitSrc: Boolean get() = sourceRect != null
    val srcX: Float? get() = sourceRect?.x
    val srcY: Float? get() = sourceRect?.y
    val srcW: Float? get() = sourceRect?.width
    val srcH: Float? get() = sourceRect?.height

    override fun canonicalIdentity(): String =
        canonicalHash("picture", pictureIdentity, sourceRect?.canonicalIdentity() ?: "no-source-rect")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PictureParams) return false
        return pictureIdentity == other.pictureIdentity && sourceRect == other.sourceRect
    }

    override fun hashCode(): Int = Objects.hash(pictureIdentity, sourceRect)

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
    val tileMode: GPUTileMode,
) : GPUPreparedFilterParameters {
    constructor(
        kernel: FloatArray,
        kernelSizeX: Int,
        kernelSizeY: Int,
        gain: Float,
        bias: Float,
        kernelOffsetX: Int,
        kernelOffsetY: Int,
        convolveAlpha: Boolean,
        tileMode: String,
    ) : this(
        kernel,
        kernelSizeX,
        kernelSizeY,
        gain,
        bias,
        kernelOffsetX,
        kernelOffsetY,
        convolveAlpha,
        tileMode.toTileMode(),
    )

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
    children: Map<String, GPUPreparedFilterInputRef>,
    val childShaderName: String = "src",
) : GPUPreparedFilterParameters {

    private val uniformSnapshots: Map<String, FloatArray> =
        Collections.unmodifiableMap(uniforms.mapValues { (_, value) -> value.copyOf() })
    val uniforms: Map<String, FloatArray>
        get() = Collections.unmodifiableMap(
            uniformSnapshots.mapValues { (_, value) -> value.copyOf() },
        )
    val children: Map<String, GPUPreparedFilterInputRef> =
        Collections.unmodifiableMap(LinkedHashMap(children))

    init {
        require(effectId.isNotBlank()) { "Runtime effect id must not be blank" }
        require(effectVersion >= 0) { "Runtime effect version must be >= 0" }
        require(childShaderName.isNotBlank()) {
            "Runtime effect child shader name must not be blank"
        }
        uniformSnapshots.forEach { (name, values) ->
            require(name.isNotBlank()) { "Runtime uniform name must not be blank" }
            requireFinite("RuntimeEffectParams uniform '$name'", values)
        }
        this.children.keys.forEach { name ->
            require(name.isNotBlank()) { "Runtime child name must not be blank" }
        }
    }

    override fun canonicalIdentity(): String {
        return canonicalHash(
            *buildList {
                add("runtime-effect")
                add(effectId)
                add(effectVersion.toString())
                add(childShaderName)
                val sortedUniforms = uniformSnapshots.entries.sortedBy { it.key }
                add(sortedUniforms.size.toString())
                sortedUniforms.forEach { (name, values) ->
                    add(name)
                    add(values.canonicalBitsIdentity())
                }
                val sortedChildren = children.entries.sortedBy { it.key }
                add(sortedChildren.size.toString())
                sortedChildren.forEach { (name, input) ->
                    add(name)
                    add(input.identityFragment())
                }
            }.toTypedArray(),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeEffectParams) return false
        if (effectId != other.effectId || effectVersion != other.effectVersion) return false
        if (childShaderName != other.childShaderName) return false
        if (uniformSnapshots.size != other.uniformSnapshots.size) return false
        for ((k, v) in uniformSnapshots) {
            val ov = other.uniformSnapshots[k] ?: return false
            if (!v.contentEquals(ov)) return false
        }
        return children == other.children
    }

    override fun hashCode(): Int {
        var result = effectId.hashCode()
        result = 31 * result + effectVersion
        result = 31 * result + childShaderName.hashCode()
        for ((k, v) in uniformSnapshots.entries.sortedBy { it.key }) {
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
    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()
    init {
        requireFinite(dx, dy, dz, surfaceScale, kd, label = "DistantLitDiffuseParams")
        requireFinite("DistantLitDiffuseParams", color)
        require(colorSnapshot.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "distant_lit_diffuse:${dx.toRawBits()}:${dy.toRawBits()}:${dz.toRawBits()}" +
            ":${surfaceScale.toRawBits()}:${kd.toRawBits()}:${colorSnapshot.canonicalBitsIdentity()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistantLitDiffuseParams) return false
        return dx == other.dx && dy == other.dy && dz == other.dz &&
            surfaceScale == other.surfaceScale && kd == other.kd &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }
    override fun hashCode(): Int =
        Objects.hash(dx, dy, dz, surfaceScale, kd, colorSnapshot.contentHashCode())
}

class PointLitDiffuseParams(
    val px: Float, val py: Float, val pz: Float,
    val surfaceScale: Float, val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()
    init {
        requireFinite(px, py, pz, surfaceScale, kd, label = "PointLitDiffuseParams")
        requireFinite("PointLitDiffuseParams", color)
        require(colorSnapshot.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "point_lit_diffuse:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${surfaceScale.toRawBits()}:${kd.toRawBits()}:${colorSnapshot.canonicalBitsIdentity()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointLitDiffuseParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            surfaceScale == other.surfaceScale && kd == other.kd &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }
    override fun hashCode(): Int =
        Objects.hash(px, py, pz, surfaceScale, kd, colorSnapshot.contentHashCode())
}

class SpotLitDiffuseParams(
    val px: Float, val py: Float, val pz: Float,
    val tx: Float, val ty: Float, val tz: Float,
    val cutoff: Float, val exponent: Float,
    val surfaceScale: Float, val kd: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()
    init {
        requireFinite(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, kd, label = "SpotLitDiffuseParams")
        requireFinite("SpotLitDiffuseParams", color)
        require(colorSnapshot.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "spot_lit_diffuse:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${tx.toRawBits()}:${ty.toRawBits()}:${tz.toRawBits()}:${cutoff.toRawBits()}:${exponent.toRawBits()}:${surfaceScale.toRawBits()}:${kd.toRawBits()}:${colorSnapshot.canonicalBitsIdentity()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpotLitDiffuseParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            tx == other.tx && ty == other.ty && tz == other.tz &&
            cutoff == other.cutoff && exponent == other.exponent &&
            surfaceScale == other.surfaceScale && kd == other.kd &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }
    override fun hashCode(): Int =
        Objects.hash(
            px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, kd,
            colorSnapshot.contentHashCode(),
        )
}

class DistantLitSpecularParams(
    val dx: Float, val dy: Float, val dz: Float,
    val surfaceScale: Float, val ks: Float, val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()
    init {
        requireFinite(dx, dy, dz, surfaceScale, ks, shininess, label = "DistantLitSpecularParams")
        requireFinite("DistantLitSpecularParams", color)
        require(colorSnapshot.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "distant_lit_specular:${dx.toRawBits()}:${dy.toRawBits()}:${dz.toRawBits()}:${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}:${colorSnapshot.canonicalBitsIdentity()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistantLitSpecularParams) return false
        return dx == other.dx && dy == other.dy && dz == other.dz &&
            surfaceScale == other.surfaceScale && ks == other.ks && shininess == other.shininess &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }
    override fun hashCode(): Int =
        Objects.hash(dx, dy, dz, surfaceScale, ks, shininess, colorSnapshot.contentHashCode())
}

class PointLitSpecularParams(
    val px: Float, val py: Float, val pz: Float,
    val surfaceScale: Float, val ks: Float, val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()
    init {
        requireFinite(px, py, pz, surfaceScale, ks, shininess, label = "PointLitSpecularParams")
        requireFinite("PointLitSpecularParams", color)
        require(colorSnapshot.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "point_lit_specular:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}:${colorSnapshot.canonicalBitsIdentity()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointLitSpecularParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            surfaceScale == other.surfaceScale && ks == other.ks && shininess == other.shininess &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }
    override fun hashCode(): Int =
        Objects.hash(px, py, pz, surfaceScale, ks, shininess, colorSnapshot.contentHashCode())
}

class SpotLitSpecularParams(
    val px: Float, val py: Float, val pz: Float,
    val tx: Float, val ty: Float, val tz: Float,
    val cutoff: Float, val exponent: Float,
    val surfaceScale: Float, val ks: Float, val shininess: Float,
    color: FloatArray,
) : GPUPreparedFilterParameters {
    private val colorSnapshot: FloatArray = color.copyOf()
    val color: FloatArray get() = colorSnapshot.copyOf()
    init {
        requireFinite(px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, ks, shininess, label = "SpotLitSpecularParams")
        requireFinite("SpotLitSpecularParams", color)
        require(colorSnapshot.size == 3) { "Lighting color must have exactly 3 entries" }
    }
    override fun canonicalIdentity(): String =
        "spot_lit_specular:${px.toRawBits()}:${py.toRawBits()}:${pz.toRawBits()}:${tx.toRawBits()}:${ty.toRawBits()}:${tz.toRawBits()}:${cutoff.toRawBits()}:${exponent.toRawBits()}:${surfaceScale.toRawBits()}:${ks.toRawBits()}:${shininess.toRawBits()}:${colorSnapshot.canonicalBitsIdentity()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpotLitSpecularParams) return false
        return px == other.px && py == other.py && pz == other.pz &&
            tx == other.tx && ty == other.ty && tz == other.tz &&
            cutoff == other.cutoff && exponent == other.exponent &&
            surfaceScale == other.surfaceScale && ks == other.ks && shininess == other.shininess &&
            colorSnapshot.contentEquals(other.colorSnapshot)
    }
    override fun hashCode(): Int =
        Objects.hash(
            px, py, pz, tx, ty, tz, cutoff, exponent, surfaceScale, ks, shininess,
            colorSnapshot.contentHashCode(),
        )
}

/** Typed, deeply immutable filter graph node. */
class GPUPreparedFilterNode(
    val id: GPUPreparedFilterNodeId,
    val kind: GPUPreparedFilterKind,
    inputs: List<GPUPreparedFilterInputRef>,
    val parameters: GPUPreparedFilterParameters,
    val provenance: String,
) {
    val inputs: List<GPUPreparedFilterInputRef> =
        Collections.unmodifiableList(inputs.toList())

    init {
        validateKindParams()
        validateInputArity()
        validateParameterInputs()
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

    private fun validateParameterInputs() {
        when (val params = parameters) {
            is ComposeParams -> require(inputs == listOf(params.inner, params.outer)) {
                "Node ${id.value}: Compose parameter inputs must match node inputs exactly"
            }
            is MergeParams -> require(inputs == params.inputs) {
                "Node ${id.value}: Merge parameter inputs must match node inputs exactly"
            }
            else -> Unit
        }
    }

    fun canonicalIdentity(): String = canonicalHash(
        *buildList {
            add("node")
            add(id.value)
            add(kind.name)
            add(inputs.size.toString())
            inputs.forEach { add(it.identityFragment()) }
            add(parameters.canonicalIdentity())
            add(provenance)
        }.toTypedArray(),
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is GPUPreparedFilterNode &&
            id == other.id &&
            kind == other.kind &&
            inputs == other.inputs &&
            parameters == other.parameters &&
            provenance == other.provenance

    override fun hashCode(): Int = Objects.hash(id, kind, inputs, parameters, provenance)

    override fun toString(): String =
        "GPUPreparedFilterNode(id=$id, kind=$kind, inputs=$inputs, provenance=$provenance)"
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
        ): String = canonicalHash(
            *buildList {
                add("filter-graph:v2")
                val sortedNodes = nodes.sortedBy { it.id.value }
                add(sortedNodes.size.toString())
                sortedNodes.forEach { add(it.canonicalIdentity()) }
                add(output.identityFragment())
            }.toTypedArray(),
        )
    }
}

/** Deeply immutable proof of a normalization rewrite. */
class GPUPreparedFilterRewriteProof(
    val rule: String,
    sourceNodeIds: List<GPUPreparedFilterNodeId>,
    resultNodeIds: List<GPUPreparedFilterNodeId>,
    val removedIntermediateCount: Int,
    val inputBoundsIdentity: String,
    val outputBoundsIdentity: String,
) {
    val sourceNodeIds: List<GPUPreparedFilterNodeId> =
        Collections.unmodifiableList(sourceNodeIds.toList())
    val resultNodeIds: List<GPUPreparedFilterNodeId> =
        Collections.unmodifiableList(resultNodeIds.toList())
}

/** Normalized filter graph with immutable rewrite proofs. */
class GPUPreparedFilterNormalization(
    val graph: GPUPreparedFilterGraph,
    rewrites: List<GPUPreparedFilterRewriteProof>,
    materializationNodeIds: Set<GPUPreparedFilterNodeId>,
) {
    val rewrites: List<GPUPreparedFilterRewriteProof> =
        Collections.unmodifiableList(rewrites.toList())
    val materializationNodeIds: Set<GPUPreparedFilterNodeId> =
        Collections.unmodifiableSet(LinkedHashSet(materializationNodeIds))
}

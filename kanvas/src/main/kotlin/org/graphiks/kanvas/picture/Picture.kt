@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.CornerRadiiF32

import org.graphiks.math.color.ColorMatrixF32

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.canvas.GeometrySnapshotContext
import org.graphiks.kanvas.canvas.snapshotGeometry
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.Gamut
import org.graphiks.kanvas.color.TransferFunction
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneArchiveCodec
import org.graphiks.kanvas.render.ir.SceneArchiveDecodeResult
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneDisplayOpAdapter
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathCommand
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.math.matrix.Matrix3x3F32
import kotlin.math.ceil
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.MeshChild
import org.graphiks.kanvas.paint.ShaderChild
import org.graphiks.kanvas.paint.ColorFilterChild
import org.graphiks.kanvas.paint.BlenderChild
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.pipeline.*
import org.graphiks.kanvas.surface.ImageEncoder
import org.graphiks.kanvas.surface.ImageEncoderRegistry
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.geometry.SizeF32
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * An immutable snapshot of recorded drawing commands.
 *
 * Created by [PictureRecorder] and can be drawn onto any [Canvas]
 * via [Canvas.drawPicture] or replayed in full via [playback].
 */
class Picture internal constructor(
    cullRect: RectF32,
    ops: List<DisplayOp>,
) {
    private val recordedCullRect = RectF32(cullRect.left, cullRect.top, cullRect.right, cullRect.bottom)

    /** A fresh mutable compatibility value for the immutable recorded cull bounds. */
    val cullRect: RectF32
        get() = RectF32(recordedCullRect.left, recordedCullRect.top, recordedCullRect.right, recordedCullRect.bottom)

    internal val ops: List<DisplayOp> = ops.snapshotGeometry()

    /** Unique identifier for this picture instance. */
    val uniqueID: Int = nextId()

    /** Number of top-level display operations in this picture. */
    val opCount: Int get() = ops.size

    /** Total operation count including nested pictures. */
    val totalOpCount: Int get() = approximateOpCount(nested = true)

    /**
     * Walks every top-level [DisplayOp.DrawImage], [DisplayOp.DrawImageNine],
     * [DisplayOp.DrawImageLattice], and [DisplayOp.DrawAtlas] in this picture,
     * invoking [action] for each embedded [Image]. Does not recurse into
     * nested pictures.
     */
    fun walkImages(action: (Image) -> Unit) {
        for (op in ops) {
            when (op) {
                is DisplayOp.DrawImage -> action(op.image)
                is DisplayOp.DrawImageNine -> action(op.image)
                is DisplayOp.DrawImageLattice -> action(op.image)
                is DisplayOp.DrawAtlas -> action(op.atlas)
                else -> {}
            }
        }
    }

    /**
     * Walks every top-level [DisplayOp.DrawPicture] in this picture, invoking
     * [action] for each nested [Picture]. Does not recurse.
     */
    fun walkNestedPictures(action: (Picture) -> Unit) {
        for (op in ops) {
            if (op is DisplayOp.DrawPicture) action(op.picture)
        }
    }

    /**
     * Walks every top-level [DisplayOp.DrawText] in this picture, invoking
     * [action] once per distinct [TextBlob] (deduplicated by reference
     * identity). Does not recurse into nested pictures.
     */
    fun walkTextBlobs(action: (TextBlob) -> Unit) {
        val seen = java.util.IdentityHashMap<TextBlob, Boolean>()
        for (op in ops) {
            if (op is DisplayOp.DrawText && seen.put(op.blob, true) == null) {
                action(op.blob)
            }
        }
    }

    /**
     * Iterates every display operation in insertion order.
     *
     * @param nested if `true`, recurses into [DisplayOp.DrawPicture]
     * @param action invoked for each [DisplayOp] encountered
     */
    fun forEachOp(nested: Boolean = false, action: (DisplayOp) -> Unit) {
        forEachOp(nested, action, GeometrySnapshotContext())
    }

    private fun forEachOp(
        nested: Boolean,
        action: (DisplayOp) -> Unit,
        context: GeometrySnapshotContext,
    ) {
        for (op in ops) {
            val snapshot = op.snapshotGeometry(context)
            action(snapshot)
            if (nested && snapshot is DisplayOp.DrawPicture) {
                snapshot.picture.forEachOp(nested = true, action = action, context = context)
            }
        }
    }

    /**
     * Renders this picture into a transient surface and wraps the result
     * as a tiled [Shader.Image], equivalent to Skia's `SkPicture.makeShader`.
     *
     * The picture is rasterised once into a snapshot sized to [tile]
     * (defaults to [cullRect]), then promoted to an image shader for
     * unlimited reuse on any canvas.
     *
     * @param tileX    tile mode along the local-x axis
     * @param tileY    tile mode along the local-y axis
     * @param sampling sampling filter; defaults to [SamplingOptions.NEAREST]
     * @param tile     sub-rectangle of the picture to use as the tile source
     * @param matrix   optional shader-local transform applied before sampling;
     *                 when non-null, wraps with [Shader.WithLocalMatrix]
     */
    fun asShader(
        tileX: TileMode = TileMode.CLAMP,
        tileY: TileMode = TileMode.CLAMP,
        sampling: SamplingOptions = SamplingOptions.NEAREST,
        tile: RectF32 = cullRect,
        matrix: Matrix3x3F32? = null,
    ): Shader {
        val w = maxOf(1, ceil(tile.width()).toInt())
        val h = maxOf(1, ceil(tile.height()).toInt())
        val surface = Surface(w, h)
        val c = surface.canvas()
        c.clear(ColorARGB.Transparent)
        if (tile.left != 0f || tile.top != 0f) {
            c.translate(-tile.left, -tile.top)
        }
        playback(c)
        val image = surface.makeImageSnapshot()
        val base = Shader.Image(image, tileX, tileY, sampling)
        return if (matrix != null) Shader.WithLocalMatrix(base, matrix) else base
    }

    /**
     * Renders this picture into an [Image] of the given dimensions.
     *
     * This is an explicit snapshot alternative to [asShader] — useful for
     * generating textures, thumbnails, or GPU uploads without wrapping in
     * a shader.
     *
     * @param width     output image width in pixels
     * @param height    output image height in pixels
     */
    fun rasterize(
        width: Int,
        height: Int,
    ): Image {
        val surface = Surface(width, height)
        val c = surface.canvas()
        c.clear(ColorARGB.Transparent)
        playback(c)
        return surface.makeImageSnapshot()
    }

    /**
     * Replay this picture's drawing commands onto [canvas].
     *
     * The canvas's save/restore balance is preserved — each call
     * is wrapped in a save/restore pair.
     */
    fun playback(canvas: Canvas) {
        canvas.save()
        try {
            for (op in ops) {
                when (op) {
                    is DisplayOp.DrawRect -> canvas.drawRect(op.rect, op.paint)
                    is DisplayOp.DrawRRect -> canvas.drawRRect(op.rrect, op.paint)
                    is DisplayOp.DrawDRRect -> canvas.drawDRRect(op.outer, op.inner, op.paint)
                    is DisplayOp.DrawPath -> canvas.drawPath(
                        op.path,
                        op.paint,
                        DrawPathSourceOperation.fromStableName(op.sourceOperation)
                            ?: DrawPathSourceOperation.DRAW_PATH,
                    )
                    is DisplayOp.DrawPoint -> canvas.drawPoint(op.x, op.y, op.paint)
                    is DisplayOp.DrawPoints -> canvas.drawPoints(op.mode, op.points, op.paint)
                    is DisplayOp.DrawImage -> canvas.drawImage(op.image, op.dst, op.paint)
                    is DisplayOp.DrawImageNine -> canvas.drawImageNine(op.image, op.center, op.dst, op.paint)
                    is DisplayOp.DrawImageLattice -> canvas.drawImageLattice(
                        op.image,
                        op.lattice,
                        op.dst,
                        op.paint,
                        op.sampling,
                    )
                    is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
                    is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint)
                    is DisplayOp.DrawVertices -> canvas.drawVertices(op.vertices, op.paint)
                    is DisplayOp.DrawMesh -> canvas.drawMesh(op.mesh, op.paint, op.blendMode)
                    is DisplayOp.DrawAtlas -> canvas.drawAtlas(op.atlas, op.transforms, op.texRects, op.colors, op.blendMode, op.paint)
                    is DisplayOp.DrawColor -> canvas.drawColor(op.color, op.mode)
                    is DisplayOp.Clear -> canvas.clear(op.color)
                    is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
                    is DisplayOp.SetClip -> { /* clip is baked into draw ops; state tracked during recording */ }
                    is DisplayOp.BeginLayer -> canvas.saveLayer(op.rec)
                    is DisplayOp.EndLayer -> canvas.restore()
                    is DisplayOp.Annotation -> { /* no visual output */ }
                    is DisplayOp.FlushAndSnapshot -> { /* no visual output; readback is render-backend-specific */ }
                }
            }
        } finally {
            canvas.restore()
        }
    }

    /**
     * Approximate number of display operations in this picture.
     *
     * @param nested if true, recursively count ops in nested pictures
     */
    fun approximateOpCount(nested: Boolean = false): Int {
        if (!nested) return ops.size
        return ops.sumOf { op ->
            if (op is DisplayOp.DrawPicture) 1 + op.picture.approximateOpCount(true) else 1
        }
    }

    /**
     * Approximate memory footprint of this picture in bytes.
     * Does not include the memory of referenced objects owned externally.
     */
    fun approximateBytesUsed(): Int = ops.size * 128

    /** Serialize this picture to a compact binary representation. */
    fun toByteArray(): ByteArray {
        val capture = DisplayOpSceneAdapter.capture(
            operations = ops,
            extent = SceneExtent(cullExtent(recordedCullRect.width(), "cull width"), cullExtent(recordedCullRect.height(), "cull height")),
            colorSpace = ColorSpace.SRGB,
        )
        val scene = when (capture) {
            is SceneCaptureResult.Captured -> capture.scene
            is SceneCaptureResult.Invalid -> throw IllegalStateException(
                capture.diagnostics.joinToString(",") { it.code.value },
            )
        }
        return SceneArchiveCodec.encodePicture(scene, recordedCullRect)
    }

    companion object {
        /** Deserialize a Picture from its binary representation. */
        fun fromByteArray(data: ByteArray): Picture? {
            return decodePicture(data)
        }

        private var globalId = 0
        private fun nextId(): Int = synchronized(this) { ++globalId }
    }
}

// ---- Binary serialization helpers ------------------------------------------

private val MAGIC = byteArrayOf(0x4B, 0x50, 0x49, 0x43)
private const val FORMAT_VERSION = 8
private const val STABLE_WIRE_VERSION = 8

// type discriminators
private const val OP_DRAW_RECT: Byte = 0
private const val OP_DRAW_R_RECT: Byte = 1
private const val OP_DRAW_D_R_RECT: Byte = 2
private const val OP_DRAW_PATH: Byte = 3
private const val OP_DRAW_POINT: Byte = 4
private const val OP_DRAW_POINTS: Byte = 5
private const val OP_DRAW_IMAGE: Byte = 6
private const val OP_DRAW_IMAGE_NINE: Byte = 7
private const val OP_DRAW_IMAGE_LATTICE: Byte = 8
private const val OP_DRAW_TEXT: Byte = 9
private const val OP_DRAW_PICTURE: Byte = 10
private const val OP_DRAW_VERTICES: Byte = 11
private const val OP_DRAW_ATLAS: Byte = 12
private const val OP_DRAW_COLOR: Byte = 13
private const val OP_CLEAR: Byte = 14
private const val OP_SET_TRANSFORM: Byte = 15
private const val OP_SET_CLIP: Byte = 16
private const val OP_BEGIN_LAYER: Byte = 17
private const val OP_END_LAYER: Byte = 18
private const val OP_ANNOTATION: Byte = 19
private const val OP_FLUSH_AND_SNAPSHOT: Byte = 20
private const val OP_DRAW_MESH: Byte = 21

private class Reader(private val data: ByteArray) {
    var formatVersion: Int = 1
    private val bais = ByteArrayInputStream(data)
    val dis = DataInputStream(bais)
    var valid = true
        private set

    private fun guard(block: () -> Unit) {
        if (!valid) return
        try { block() } catch (_: IOException) { valid = false }
    }

    fun byte(): Byte { var v = 0.toByte(); guard { v = dis.readByte() }; return v }
    fun int(): Int { var v = 0; guard { v = dis.readInt() }; return v }
    fun float(): Float { var v = 0f; guard { v = dis.readFloat() }; return v }
    fun bool(): Boolean { var v = false; guard { v = dis.readBoolean() }; return v }
    fun string(): String { var v = ""; guard { v = dis.readUTF() }; return v }
    fun bytes(len: Int): ByteArray { val v = ByteArray(len); guard { if (valid) dis.readFully(v) }; return v }
    fun atEnd(): Boolean = bais.available() == 0

    private fun <T> discriminator(
        legacy: List<T>,
        stable: (Byte) -> T?,
        default: T,
    ): T {
        val id = byte()
        val value = if (formatVersion == STABLE_WIRE_VERSION) stable(id) else legacy.getOrNull(id.toInt())
        if (value == null) {
            valid = false
            return default
        }
        return value
    }

    private fun fillType(): FillType = discriminator(FillType.entries, ::stableFillTypeFromId, FillType.WINDING)
    private fun pathVerb(): PathVerb = discriminator(PathVerb.entries, ::stablePathVerbFromId, PathVerb.MOVE)
    private fun colorType(): ColorType = discriminator(ColorType.entries, ::stableColorTypeFromId, ColorType.UNKNOWN)
    private fun alphaType(): AlphaType = discriminator(AlphaType.entries, ::stableAlphaTypeFromId, AlphaType.UNKNOWN)
    private fun transferFunction(): TransferFunction = discriminator(TransferFunction.entries, ::stableTransferFunctionFromId, TransferFunction.SRGB)
    private fun gamut(): Gamut = discriminator(Gamut.entries, ::stableGamutFromId, Gamut.SRGB)
    private fun paintStyle(): PaintStyle = discriminator(PaintStyle.entries, ::stablePaintStyleFromId, PaintStyle.FILL)
    private fun strokeCap(): StrokeCap = discriminator(StrokeCap.entries, ::stableStrokeCapFromId, StrokeCap.BUTT)
    private fun strokeJoin(): StrokeJoin = discriminator(StrokeJoin.entries, ::stableStrokeJoinFromId, StrokeJoin.MITER)
    private fun uniformType(): UniformType = discriminator(UniformType.entries, ::stableUniformTypeFromId, UniformType.FLOAT)
    private fun vertexFormat(): VertexFormat = discriminator(VertexFormat.entries, ::stableVertexFormatFromId, VertexFormat.FLOAT32)
    private fun childType(): ChildType = discriminator(ChildType.entries, ::stableChildTypeFromId, ChildType.SHADER)
    private fun vertexStepMode(): VertexStepMode = discriminator(
        VertexStepMode.entries,
        ::stableVertexStepModeFromId,
        VertexStepMode.VERTEX,
    )
    private fun path1DStyle(): Path1DStyle = discriminator(Path1DStyle.entries, ::stablePath1DStyleFromId, Path1DStyle.TRANSLATE)

    fun rect(): RectF32 = RectF32(float(), float(), float(), float())
    fun point2(): Point2F32 = Point2F32(float(), float())
    fun vector2(): Vector2F32 = Vector2F32(float(), float())
    fun size(): SizeF32 = SizeF32.of(float(), float())
    fun color(): ColorARGB = ColorARGB.fromPackedUInt(int().toUInt())
    fun samplingOptions(): SamplingOptions = when (byte().toInt()) {
        0 -> SamplingOptions.NEAREST
        1 -> SamplingOptions.LINEAR
        2 -> SamplingOptions.Cubic(float(), float())
        else -> { valid = false; SamplingOptions.LINEAR }
    }
    fun cornerRadii(): CornerRadiiF32 = CornerRadiiF32.of(float(), float())

    fun matrix33(): Matrix3x3F32 {
        val floats = FloatArray(9) { float() }
        if (!valid) return Matrix3x3F32.Identity
        return Matrix3x3F32.of(
            floats[0], floats[1], floats[2],
            floats[3], floats[4], floats[5],
            floats[6], floats[7], floats[8],
        )
    }

    fun rrect(): RRectF32 {
        return RRectF32.of(rect(), cornerRadii(), cornerRadii(), cornerRadii(), cornerRadii())
    }

    fun path(): Path {
        val fillType = fillType()
        val verbCount = int()
        val verbs = List(verbCount) { pathVerb() }
        val ptCount = int()
        val values = FloatArray(ptCount * 2) { float() }
        val p = Path()
        p.fillType = fillType
        var pointIndex = 0
        fun nextPair(): Pair<Float, Float> {
            if (pointIndex + 1 >= values.size) {
                valid = false
                return 0f to 0f
            }
            val pair = values[pointIndex] to values[pointIndex + 1]
            pointIndex += 2
            return pair
        }
        for (verb in verbs) {
            when (verb) {
                PathVerb.MOVE -> nextPair().let { (x, y) -> p.moveTo(x, y) }
                PathVerb.LINE -> nextPair().let { (x, y) -> p.lineTo(x, y) }
                PathVerb.QUAD -> {
                    val (controlX, controlY) = nextPair()
                    val (endX, endY) = nextPair()
                    p.quadTo(controlX, controlY, endX, endY)
                }
                PathVerb.CUBIC -> {
                    val (control1X, control1Y) = nextPair()
                    val (control2X, control2Y) = nextPair()
                    val (endX, endY) = nextPair()
                    p.cubicTo(control1X, control1Y, control2X, control2Y, endX, endY)
                }
                PathVerb.ARC_TO -> {
                    val (radiusX, radiusY) = nextPair()
                    val (rotation, largeArcValue) = nextPair()
                    val (sweepValue, _) = nextPair()
                    val (endX, endY) = nextPair()
                    p.arcTo(radiusX, radiusY, rotation, largeArcValue > 0f, sweepValue > 0f, endX, endY)
                }
                PathVerb.CLOSE -> p.close()
            }
            if (!valid) return p
        }
        if (pointIndex != values.size) valid = false
        return p
    }

    fun image(): Image {
        val w = int(); val h = int()
        val ct = colorType()
        val srcId = string()
        val hasPixels = bool()
        val px = if (hasPixels) { val len = int(); bytes(len) } else null
        val cs = readColorSpace()
        val alphaType = if (formatVersion >= 5) alphaType() else AlphaType.UNPREMUL
        return Image(w, h, ct, srcId, px, cs, alphaType)
    }

    fun readColorSpace(): ColorSpace {
        val name = string()
        val tf = transferFunction()
        val g = gamut()
        return ColorSpace(name, tf, g)
    }

    fun paint(): Paint {
        val c = color()
        val s = shader()
        val bm = blendMode()
        val cf = colorFilter()
        val mf = maskFilter()
        val pe = pathEffect()
        val imf = imageFilter()
        val bl = blender()
        val style = paintStyle()
        val sw = float()
        val cap = strokeCap()
        val join = strokeJoin()
        val sm = float()
        val aa = bool()
        return Paint(c, s, bm, cf, mf, pe, imf, bl, style, sw, cap, join, sm, aa)
    }

    fun shader(): Shader? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> Shader.SolidColor(color())
            1 -> Shader.LinearGradient(point2(), point2(), gradientStops(), tileMode(), colorSpaceInterpolation())
            2 -> Shader.RadialGradient(point2(), float(), gradientStops(), tileMode(), colorSpaceInterpolation())
            3 -> Shader.SweepGradient(point2(), float(), float(), gradientStops(), tileMode(), colorSpaceInterpolation())
            4 -> Shader.ConicalGradient(point2(), float(), point2(), float(), gradientStops(), tileMode(), colorSpaceInterpolation())
            5 -> Shader.Image(image(), tileMode(), tileMode())
            6 -> Shader.Blend(blendMode(), shader()!!, shader()!!)
            7 -> readRuntimeEffect()?.let { re -> readUniformBlock()?.let { ub -> Shader.RuntimeEffect(re, ub, readShaderMap()) } }
                ?: run { valid = false; null }
            8 -> Shader.WithLocalMatrix(shader()!!, matrix33())
            9 -> Shader.WithColorFilter(shader()!!, colorFilter()!!)
            10 -> Shader.PerlinNoise(float(), float(), int(), int(), readSizeOrNull())
            11 -> Shader.FractalNoise(float(), float(), int(), int(), readSizeOrNull())
            12 -> Shader.WithWorkingColorSpace(shader()!!, colorSpaceInterpolation())
            13 -> Shader.CoordClamp(shader()!!, rect())
            else -> { valid = false; null }
        }
    }

    private fun gradientStops(): List<GradientStop> {
        val n = int(); return List(n) { GradientStop(float(), color()) }
    }

    private fun readSizeOrNull(): SizeF32? = if (bool()) size() else null

    private fun readShaderMap(): Map<String, Shader> {
        val n = int()
        return buildMap { for (i in 0 until n) { val key = string(); put(key, shader()!!) } }
    }

    private fun readColorFilterMap(): Map<String, ColorFilter> {
        val n = int()
        return buildMap { for (i in 0 until n) { val key = string(); put(key, colorFilter()!!) } }
    }

    private fun readImageFilterMap(): Map<String, ImageFilter?> {
        val n = int()
        return buildMap { for (i in 0 until n) { val key = string(); put(key, imageFilter()) } }
    }

    private fun readStringOrNull(): String? = if (bool()) string() else null

    fun readRuntimeEffect(): RuntimeEffect? {
        val id = string()
        val module = readShaderModule()
        val layout = readUniformLayout()
        val children = readChildSlots()
        val result = createRuntimeEffect(id, module, layout, children)
        if (result == null) valid = false
        return result
    }

    private fun readShaderModule(): ShaderModule {
        val source = string()
        val entry = string()
        val uniformCount = int()
        val uniforms = List(uniformCount) { UniformSlot(string(), int(), uniformType(), int()) }
        val textureCount = int()
        val textures = List(textureCount) { TextureSlot(string(), int()) }
        val attrCount = int()
        val attrs = List(attrCount) { VertexAttribute(vertexFormat(), int(), int()) }
        val stride = int()
        val stepMode = if (formatVersion == STABLE_WIRE_VERSION) {
            vertexStepMode()
        } else {
            VertexStepMode.entries[byte().toInt()]
        }
        if (formatVersion != STABLE_WIRE_VERSION) {
            // Versions 1–7 could not reconstruct private ShaderModule state.
            if (uniforms.isNotEmpty() || textures.isNotEmpty() || attrs.isNotEmpty()) valid = false
            return ShaderModule.fromSource(source, entry)
        }
        return createShaderModule(source, entry, uniforms, textures, VertexLayout(attrs, stride, stepMode))
            ?: ShaderModule.fromSource(source, entry).also { valid = false }
    }

    private fun readUniformLayout(): UniformLayout {
        val n = int()
        val slots = List(n) {
            UniformSlot(string(), int(), uniformType(), int())
        }
        return UniformLayout(slots)
    }

    private fun readChildSlots(): List<ChildSlot> {
        val n = int()
        return List(n) { ChildSlot(string(), childType()) }
    }

    fun readUniformBlock(): UniformBlock? {
        // UniformBlock has no public constructor that accepts entries map
        // We'll create it via the DSL
        val n = int()
        val entries = mutableMapOf<String, UniformValue>()
        for (i in 0 until n) {
            val name = string()
            val type = byte().toInt()
            val value = when (type) {
                0 -> UniformValue.F1(float())
                1 -> UniformValue.F2(float(), float())
                2 -> UniformValue.F3(float(), float(), float())
                3 -> UniformValue.F4(float(), float(), float(), float())
                4 -> UniformValue.M3(matrix33())
                5 -> { val len = int(); UniformValue.M4(FloatArray(len) { float() }) }
                6 -> UniformValue.I1(int())
                else -> { valid = false; UniformValue.F1(0f) }
            }
            entries[name] = value
        }
        val result = createUniformBlock(entries)
        if (result == null) valid = false
        return result
    }

    fun colorFilter(): ColorFilter? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(int()) { float() }))
            1 -> ColorFilter.Blend(color(), blendMode())
            2 -> ColorFilter.Compose(colorFilter()!!, colorFilter()!!)
            3 -> ColorFilter.Table(UByteArray(int()) { byte().toUByte() })
            4 -> ColorFilter.Lighting(color(), color())
            5 -> ColorFilter.SRGBToLinear
            6 -> ColorFilter.LinearToSRGB
            7 -> ColorFilter.HSLAMatrix(FloatArray(int()) { float() })
            8 -> ColorFilter.Lerp(float(), colorFilter()!!, colorFilter()!!)
            9 -> ColorFilter.HighContrast
            10 -> ColorFilter.Luma
            11 -> ColorFilter.Overdraw
            12 -> readRuntimeEffect()?.let { re -> readUniformBlock()?.let { ub -> ColorFilter.RuntimeEffect(re, ub, readColorFilterMap()) } }
                ?: run { valid = false; null }
            else -> { valid = false; null }
        }
    }

    fun maskFilter(): MaskFilter? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> MaskFilter.Blur(blurStyle(), float())
            1 -> MaskFilter.Shader(shader()!!)
            2 -> MaskFilter.Table(UByteArray(int()) { byte().toUByte() })
            else -> { valid = false; null }
        }
    }

    fun pathEffect(): PathEffect? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> PathEffect.Dash(FloatArray(int()) { float() }, float())
            1 -> PathEffect.Corner(float())
            2 -> PathEffect.Discrete(float(), float())
            3 -> PathEffect.Path1D(path(), float(), float(), path1DStyle())
            4 -> PathEffect.Path2D(matrix33(), path())
            5 -> PathEffect.Trim(float(), float())
            else -> { valid = false; null }
        }
    }

    fun imageFilter(): ImageFilter? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> ImageFilter.Blur(float(), float(), tileMode(), imageFilter())
            21 -> ImageFilter.Crop(rect(), tileMode(), imageFilter())
            1 -> ImageFilter.DropShadow(float(), float(), float(), float(), color(), imageFilter())
            2 -> ImageFilter.ColorFilter(colorFilter()!!, imageFilter())
            3 -> ImageFilter.Compose(imageFilter()!!, imageFilter()!!)
            4 -> ImageFilter.Blend(blendMode(), imageFilter()!!, imageFilter()!!)
            5 -> ImageFilter.Dilate(float(), float(), imageFilter())
            6 -> ImageFilter.Erode(float(), float(), imageFilter())
            7 -> ImageFilter.DistantLitDiffuse(vector2(), color(), float(), float(), imageFilter())
            8 -> ImageFilter.PointLitDiffuse(point2(), color(), float(), float(), imageFilter())
            9 -> ImageFilter.SpotLitDiffuse(point2(), point2(), float(), float(), color(), float(), float(), imageFilter())
            10 -> ImageFilter.DistantLitSpecular(vector2(), color(), float(), float(), float(), imageFilter())
            11 -> ImageFilter.PointLitSpecular(point2(), color(), float(), float(), float(), imageFilter())
            12 -> ImageFilter.SpotLitSpecular(point2(), point2(), float(), float(), color(), float(), float(), float(), imageFilter())
            13 -> ImageFilter.Offset(float(), float(), imageFilter())
            14 -> ImageFilter.Tile(rect(), rect(), imageFilter())
            15 -> ImageFilter.Merge(List(int()) { imageFilter()!! })
            16 -> ImageFilter.DisplacementMap(colorChannel(), colorChannel(), float(), imageFilter()!!, imageFilter())
            17 -> ImageFilter.Magnifier(rect(), float(), float(), imageFilter())
            18 -> ImageFilter.MatrixConvolution(size(), FloatArray(int()) { float() }, float(), float(), vector2(), tileMode(), bool(), imageFilter())
            19 -> {
                val nestedLen = int()
                val nestedData = bytes(nestedLen)
                val pic = decodePicture(nestedData)
                val src = if (bool()) rect() else null
                if (pic != null) ImageFilter.Picture(pic, src) else { valid = false; null }
            }
            20 -> readRuntimeEffect()?.let { re -> readUniformBlock()?.let { ub -> ImageFilter.RuntimeEffect(re, ub, readStringOrNull(), readImageFilterMap()) } }
                ?: run { valid = false; null }
            else -> { valid = false; null }
        }
    }

    fun blender(): Blender? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> Blender.Mode(blendMode())
            1 -> Blender.Arithmetic(float(), float(), float(), float())
            else -> { valid = false; null }
        }
    }

    fun blendMode(): BlendMode = discriminator(BlendMode.entries, ::stableBlendModeFromId, BlendMode.SRC_OVER)
    fun tileMode(): TileMode = discriminator(TileMode.entries, ::stableTileModeFromId, TileMode.CLAMP)
    fun blurStyle(): BlurStyle = discriminator(BlurStyle.entries, ::stableBlurStyleFromId, BlurStyle.NORMAL)
    fun colorChannel(): ColorChannel = discriminator(ColorChannel.entries, ::stableColorChannelFromId, ColorChannel.R)
    fun colorSpaceInterpolation(): ColorSpaceInterpolation = discriminator(
        ColorSpaceInterpolation.entries,
        ::stableColorSpaceInterpolationFromId,
        ColorSpaceInterpolation.SRGB,
    )
    fun pointMode(): PointMode = discriminator(PointMode.entries, ::stablePointModeFromId, PointMode.POINTS)
    fun vertexMode(): VertexMode = discriminator(VertexMode.entries, ::stableVertexModeFromId, VertexMode.TRIANGLES)
    fun latticeFlags(): LatticeFlags = discriminator(LatticeFlags.entries, ::stableLatticeFlagsFromId, LatticeFlags.DEFAULT)
    fun clipOp(): ClipOp = discriminator(ClipOp.entries, ::stableClipOpFromId, ClipOp.INTERSECT)

    fun textBlob(): TextBlob {
        val runs = List(int()) {
            val glyphs = List<UShort>(int()) { int().toUShort() }
            val positions = List(int()) { point2() }
            KanvasGlyphRun(glyphs, positions)
        }
        val typeface = if (bool()) KanvasTypeface(string()) else null
        val fontSize = float()
        return TextBlob(runs, typeface, fontSize)
    }

    fun vertices(): Vertices {
        val mode = vertexMode()
        val positions = List(int()) { point2() }
        val texCoords = if (bool()) List(int()) { point2() } else null
        val colors = if (bool()) List(int()) { color() } else null
        val indices = if (bool()) List(int()) { int() } else null
        return Vertices(mode, positions, texCoords, colors, indices)
    }

    fun lattice(): Lattice {
        val xDivs = List(int()) { int() }
        val yDivs = List(int()) { int() }
        val rects = if (bool()) List(int()) { rect() } else null
        val colors = if (bool()) List(int()) { color() } else null
        val flags = if (bool()) List(int()) { latticeFlags() } else null
        return Lattice(xDivs, yDivs, rects, colors, flags)
    }

    fun clipStack(): ClipStack {
        return when (byte().toInt()) {
            0 -> ClipStack.WideOpen
            1 -> ClipStack.DeviceRect(rect(), bool())
            2 -> ClipStack.Complex(List(int()) { clipStackOp() })
            else -> { valid = false; ClipStack.WideOpen }
        }
    }

    private fun clipStackOp(): ClipStackOp {
        val aa = bool()
        return when (byte().toInt()) {
            0 -> ClipStackOp.RectOp(rect(), clipOp(), aa)
            1 -> ClipStackOp.RRectOp(rrect(), clipOp(), aa)
            2 -> ClipStackOp.PathOp(
                path(),
                clipOp(),
                aa,
                transformClass = if (formatVersion >= 7) string() else "identity",
            )
            else -> { valid = false; ClipStackOp.RectOp(RectF32.Empty, ClipOp.INTERSECT, aa) }
        }
    }

    fun readMeshProgram(): MeshProgram? {
        if (!bool()) return null
        val effect = readRuntimeEffect() ?: return null.also { valid = false }
        val uniforms = readUniformBlock() ?: return null.also { valid = false }
        val entryCount = int()
        val entries = mutableListOf<MeshChildren.Entry>()
        for (i in 0 until entryCount) {
            val name = string()
            val child = readMeshChild() ?: return null.also { valid = false }
            entries.add(MeshChildren.Entry(name, child))
        }
        return MeshProgram(effect, uniforms, MeshChildren(entries))
    }

    fun readMeshChild(): MeshChild? {
        return when (byte().toInt()) {
            0 -> ShaderChild(shader()!!)
            1 -> ColorFilterChild(colorFilter()!!)
            2 -> BlenderChild(blender()!!)
            else -> { valid = false; null }
        }
    }

    fun displayOp(): DisplayOp? {
        val disc = byte()
        return when (disc.toInt()) {
            OP_DRAW_RECT.toInt() -> DisplayOp.DrawRect(rect(), paint(), matrix33(), clipStack())
            OP_DRAW_R_RECT.toInt() -> DisplayOp.DrawRRect(rrect(), paint(), matrix33(), clipStack())
            OP_DRAW_D_R_RECT.toInt() -> DisplayOp.DrawDRRect(rrect(), rrect(), paint(), matrix33(), clipStack())
            OP_DRAW_PATH.toInt() -> {
                val path = path()
                val paint = paint()
                val transform = matrix33()
                val clip = clipStack()
                val sourceOperation = if (formatVersion >= 6) {
                    DrawPathSourceOperation.fromStableName(string())
                } else {
                    DrawPathSourceOperation.DRAW_PATH
                }
                if (sourceOperation == null) {
                    valid = false
                    null
                } else {
                    DisplayOp.DrawPath.withSourceOperation(
                        path = path,
                        paint = paint,
                        transform = transform,
                        clip = clip,
                        sourceOperation = sourceOperation,
                    )
                }
            }
            OP_DRAW_POINT.toInt() -> DisplayOp.DrawPoint(float(), float(), paint(), matrix33(), clipStack())
            OP_DRAW_POINTS.toInt() -> {
                val mode = pointMode()
                val pts = List(int()) { point2() }
                DisplayOp.DrawPoints(mode, pts, paint(), matrix33(), clipStack())
            }
            OP_DRAW_IMAGE.toInt() -> {
                val img = image(); val src = rect(); val dst = rect()
                val p = if (bool()) paint() else null
                DisplayOp.DrawImage(img, src, dst, p, matrix33(), clipStack())
            }
            OP_DRAW_IMAGE_NINE.toInt() -> {
                val img = image(); val center = rect(); val dst = rect()
                val p = if (bool()) paint() else null
                DisplayOp.DrawImageNine(img, center, dst, p, matrix33(), clipStack())
            }
            OP_DRAW_IMAGE_LATTICE.toInt() -> {
                val img = image(); val lat = lattice(); val dst = rect()
                val p = if (bool()) paint() else null
                val transform = matrix33()
                val clip = clipStack()
                val sampling = if (formatVersion >= 4) samplingOptions() else SamplingOptions.LINEAR
                DisplayOp.DrawImageLattice(img, lat, dst, p, transform, clip, sampling)
            }
            OP_DRAW_TEXT.toInt() -> DisplayOp.DrawText(textBlob(), float(), float(), paint(), matrix33(), clipStack())
            OP_DRAW_PICTURE.toInt() -> {
                val nestedLen = int(); val nestedData = bytes(nestedLen)
                val nestedPic = decodePicture(nestedData)
                val p = if (bool()) paint() else null
                if (nestedPic == null) { valid = false; return null }
                DisplayOp.DrawPicture(nestedPic, p, matrix33(), clipStack())
            }
            OP_DRAW_VERTICES.toInt() -> DisplayOp.DrawVertices(vertices(), paint(), matrix33(), clipStack())
            OP_DRAW_MESH.toInt() -> {
                val v = vertices()
                val p = paint()
                val bm = if (bool()) blendMode() else null
                val mp = readMeshProgram()
                val bounds = rect()
                DisplayOp.DrawMesh(Mesh(v, mp, bounds), p, bm, matrix33(), clipStack())
            }
            OP_DRAW_ATLAS.toInt() -> {
                val atlas = image()
                val txCount = int()
                val transforms = List(txCount) { matrix33() }
                val texRects = List(txCount) { rect() }
                val colors = if (bool()) List(txCount) { color() } else null
                val bm = blendMode()
                val p = if (bool()) paint() else null
                DisplayOp.DrawAtlas(atlas, transforms, texRects, colors, bm, p, matrix33(), clipStack())
            }
            OP_DRAW_COLOR.toInt() -> DisplayOp.DrawColor(color(), blendMode(), matrix33(), clipStack())
            OP_CLEAR.toInt() -> DisplayOp.Clear(color())
            OP_SET_TRANSFORM.toInt() -> DisplayOp.SetTransform(matrix33())
            OP_SET_CLIP.toInt() -> DisplayOp.SetClip(clipStack())
            OP_BEGIN_LAYER.toInt() -> {
                val bounds = if (bool()) rect() else null
                val p = if (bool()) paint() else null
                val backdrop = if (formatVersion >= 2) imageFilter() else null
                val compositeClip = if (formatVersion >= 3 && bool()) clipStack() else null
                DisplayOp.BeginLayer(SaveLayerRec(bounds, p, backdrop, compositeClip))
            }
            OP_END_LAYER.toInt() -> DisplayOp.EndLayer
            OP_ANNOTATION.toInt() -> DisplayOp.Annotation(rect(), string(), string())
            OP_FLUSH_AND_SNAPSHOT.toInt() -> DisplayOp.FlushAndSnapshot(rect())
            else -> { valid = false; null }
        }
    }
}

private fun decodePicture(data: ByteArray): Picture? {
    if (data.size < 4) return null
    if (data[0] != 0x4B.toByte() || data[1] != 0x50.toByte() ||
        data[2] != 0x49.toByte() || data[3] != 0x43.toByte()) return null
    val r = Reader(data)
    r.bytes(4) // skip magic
    val version = r.int()
    if (!r.valid) return null
    return when (version) {
        in 1..7 -> decodeLegacyPicture(data, version)
        STABLE_WIRE_VERSION -> when (val decoded = SceneArchiveCodec.decodePicture(data)) {
            is SceneArchiveDecodeResult.Decoded -> try {
                Picture(decoded.copyCullRect(), SceneDisplayOpAdapter.toDisplayOps(decoded.scene))
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            } catch (_: ClassCastException) {
                null
            }
            SceneArchiveDecodeResult.LegacyV8 -> decodeHistoricalPictureV8(data)
            is SceneArchiveDecodeResult.Invalid -> null
        }
        else -> null
    }
}

private fun decodeLegacyPicture(data: ByteArray, version: Int): Picture? =
    decodePictureWithVersion(data, version, requireEnd = false)

/**
 * Compatibility reader for v8 data written before SceneArchiveCodec owned the
 * writer.  It is intentionally read-only; all new v8 output is IR-tagged.
 */
private fun decodeHistoricalPictureV8(data: ByteArray): Picture? =
    decodePictureWithVersion(data, STABLE_WIRE_VERSION, requireEnd = true)

private fun decodePictureWithVersion(data: ByteArray, version: Int, requireEnd: Boolean): Picture? {
    val r = Reader(data)
    r.bytes(4) // skip magic
    if (r.int() != version || !r.valid) return null
    r.formatVersion = version
    val cullRect = r.rect()
    val opCount = r.int()
    if (opCount !in 0..MAX_LEGACY_OPS || !r.valid) return null
    val ops = mutableListOf<DisplayOp>()
    for (i in 0 until opCount) {
        val op = r.displayOp()
        if (op == null || !r.valid) return null
        ops.add(op)
    }
    if (requireEnd && !r.atEnd()) return null
    return Picture(cullRect, ops)
}

private const val MAX_LEGACY_OPS = 1_000_000

private fun cullExtent(value: Float, field: String): Int {
    require(value.isFinite()) { "$field must be finite" }
    require(value <= Int.MAX_VALUE.toFloat()) { "$field exceeds the scene extent range" }
    return maxOf(1, value.toInt())
}

private fun createUniformBlock(entries: Map<String, UniformValue>): UniformBlock? = try {
    val constructor = UniformBlock::class.java.getDeclaredConstructor(Map::class.java)
    constructor.isAccessible = true
    constructor.newInstance(entries)
} catch (_: Exception) { null }

private fun createRuntimeEffect(id: String, module: ShaderModule, uniformLayout: UniformLayout, children: List<ChildSlot>): RuntimeEffect? = try {
    val constructor = RuntimeEffect::class.java.getDeclaredConstructor(
        String::class.java, ShaderModule::class.java, UniformLayout::class.java, List::class.java
    )
    constructor.isAccessible = true
    constructor.newInstance(id, module, uniformLayout, children)
} catch (_: Exception) { null }

private fun createShaderModule(
    source: String,
    entryPoint: String,
    uniforms: List<UniformSlot>,
    textures: List<TextureSlot>,
    vertexLayout: VertexLayout,
): ShaderModule? = try {
    val constructor = ShaderModule::class.java.getDeclaredConstructor(
        String::class.java,
        String::class.java,
        List::class.java,
        List::class.java,
        VertexLayout::class.java,
    )
    constructor.isAccessible = true
    constructor.newInstance(source, entryPoint, uniforms, textures, vertexLayout)
} catch (_: Exception) { null }

private val PathCommand.serializedPairCount: Int
    get() = when (this) {
        is PathCommand.Move, is PathCommand.Line -> 1
        is PathCommand.Quad -> 2
        is PathCommand.Cubic -> 3
        is PathCommand.ArcTo -> 4
        PathCommand.Close -> 0
    }

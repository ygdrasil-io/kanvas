@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.pipeline.*
import org.graphiks.kanvas.surface.ImageEncoder
import org.graphiks.kanvas.surface.ImageEncoderRegistry
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.*
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
    val cullRect: Rect,
    internal val ops: List<DisplayOp>,
) {
    /** Unique identifier for this picture instance. */
    val uniqueID: Int = nextId()

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
                    is DisplayOp.DrawPath -> canvas.drawPath(op.path, op.paint)
                    is DisplayOp.DrawPoint -> canvas.drawPoint(op.x, op.y, op.paint)
                    is DisplayOp.DrawPoints -> canvas.drawPoints(op.mode, op.points, op.paint)
                    is DisplayOp.DrawImage -> canvas.drawImage(op.image, op.dst, op.paint)
                    is DisplayOp.DrawImageNine -> canvas.drawImageNine(op.image, op.center, op.dst, op.paint)
                    is DisplayOp.DrawImageLattice -> canvas.drawImageLattice(op.image, op.lattice, op.dst, op.paint)
                    is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
                    is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint)
                    is DisplayOp.DrawVertices -> canvas.drawVertices(op.vertices, op.paint)
                    is DisplayOp.DrawAtlas -> canvas.drawAtlas(op.atlas, op.transforms, op.texRects, op.colors, op.blendMode, op.paint)
                    is DisplayOp.DrawColor -> canvas.drawColor(op.color, op.mode)
                    is DisplayOp.Clear -> canvas.clear(op.color)
                    is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
                    is DisplayOp.SetClip -> { /* clip is baked into draw ops; state tracked during recording */ }
                    is DisplayOp.BeginLayer -> canvas.saveLayer(op.bounds, op.paint)
                    is DisplayOp.EndLayer -> canvas.restore()
                    is DisplayOp.Annotation -> { /* no visual output */ }
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
        return encodePicture(this)
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
private const val FORMAT_VERSION = 1

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

private class Writer {
    private val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)

    fun byte(v: Byte) { dos.writeByte(v.toInt()) }
    fun int(v: Int) { dos.writeInt(v) }
    fun float(v: Float) { dos.writeFloat(v) }
    fun bool(v: Boolean) { dos.writeBoolean(v) }
    fun string(v: String) { dos.writeUTF(v) }
    fun bytes(v: ByteArray) { dos.write(v) }
    fun result(): ByteArray = baos.toByteArray()

    fun rect(r: Rect) { float(r.left); float(r.top); float(r.right); float(r.bottom) }
    fun point(p: Point) { float(p.x); float(p.y) }
    fun size(s: Size) { float(s.width); float(s.height) }
    fun color(c: Color) { int(c.packed.toInt()) }
    fun cornerRadii(c: CornerRadii) { float(c.x); float(c.y) }

    fun matrix33(m: Matrix33) {
        float(m.scaleX); float(m.skewX); float(m.transX)
        float(m.skewY); float(m.scaleY); float(m.transY)
        float(m.persp0); float(m.persp1); float(m.persp2)
    }

    fun rrect(r: RRect) {
        rect(r.rect); cornerRadii(r.topLeft); cornerRadii(r.topRight)
        cornerRadii(r.bottomRight); cornerRadii(r.bottomLeft)
    }

    fun path(p: Path) {
        byte(p.fillType.ordinal.toByte())
        val verbs = p.verbs()
        int(verbs.size)
        for (v in verbs) byte(v.ordinal.toByte())
        val pts = p.points()
        int(pts.size)
        for (pt in pts) point(pt)
    }

    fun image(img: Image) {
        int(img.width); int(img.height)
        byte(img.colorType.ordinal.toByte())
        string(img.sourceId)
        val px = img.pixels
        if (px != null) {
            bool(true)
            int(px.size); bytes(px)
        } else {
            bool(false)
        }
        colorSpace(img.colorSpace)
    }

    fun colorSpace(cs: ColorSpace) {
        string(cs.name)
        byte(cs.transferFunction.ordinal.toByte())
        byte(cs.gamut.ordinal.toByte())
    }

    fun paint(p: Paint) {
        color(p.color)
        shader(p.shader)
        blendMode(p.blendMode)
        colorFilter(p.colorFilter)
        maskFilter(p.maskFilter)
        pathEffect(p.pathEffect)
        imageFilter(p.imageFilter)
        blender(p.blender)
        byte(p.style.ordinal.toByte())
        float(p.strokeWidth)
        byte(p.strokeCap.ordinal.toByte())
        byte(p.strokeJoin.ordinal.toByte())
        float(p.strokeMiter)
        bool(p.antiAlias)
    }

    fun shader(s: Shader?) {
        if (s == null) { byte(0xFF.toByte()); return }
        when (s) {
            is Shader.SolidColor -> { byte(0); color(s.color) }
            is Shader.LinearGradient -> {
                byte(1); point(s.start); point(s.end)
                gradientStops(s.stops); tileMode(s.tileMode); colorSpaceInterpolation(s.interpolation)
            }
            is Shader.RadialGradient -> {
                byte(2); point(s.center); float(s.radius)
                gradientStops(s.stops); tileMode(s.tileMode); colorSpaceInterpolation(s.interpolation)
            }
            is Shader.SweepGradient -> {
                byte(3); point(s.center); float(s.startAngle); float(s.endAngle)
                gradientStops(s.stops); tileMode(s.tileMode); colorSpaceInterpolation(s.interpolation)
            }
            is Shader.ConicalGradient -> {
                byte(4); point(s.start); float(s.startRadius); point(s.end); float(s.endRadius)
                gradientStops(s.stops); tileMode(s.tileMode); colorSpaceInterpolation(s.interpolation)
            }
            is Shader.Image -> { byte(5); image(s.image); tileMode(s.tileModeX); tileMode(s.tileModeY) }
            is Shader.Blend -> { byte(6); blendMode(s.mode); shader(s.dst); shader(s.src) }
            is Shader.RuntimeEffect -> {
                byte(7)
                runtimeEffect(s.effect)
                uniformBlock(s.uniforms)
            }
            is Shader.WithLocalMatrix -> { byte(8); shader(s.shader); matrix33(s.matrix) }
            is Shader.WithColorFilter -> { byte(9); shader(s.shader); colorFilter(s.filter) }
            is Shader.PerlinNoise -> { byte(10); float(s.baseX); float(s.baseY); int(s.numOctaves); int(s.seed); sizeOrNull(s.tileSize) }
            is Shader.FractalNoise -> { byte(11); float(s.baseX); float(s.baseY); int(s.numOctaves); int(s.seed); sizeOrNull(s.tileSize) }
            is Shader.WithWorkingColorSpace -> { byte(12); shader(s.shader); colorSpaceInterpolation(s.interpolation) }
            is Shader.CoordClamp -> { byte(13); shader(s.shader); rect(s.subset) }
        }
    }

    private fun gradientStops(stops: List<GradientStop>) {
        int(stops.size)
        for (st in stops) { float(st.position); color(st.color) }
    }

    private fun sizeOrNull(s: Size?) {
        if (s == null) { bool(false); return }
        bool(true); size(s)
    }

    fun runtimeEffect(e: RuntimeEffect) {
        string(e.id)
        shaderModule(e.module)
        writeUniformLayout(e.uniformLayout)
        childSlots(e.children)
    }

    private fun shaderModule(m: ShaderModule) {
        string(m.source); string(m.entryPoint)
        uniformSlots(m.uniforms); textureSlots(m.textures); writeVertexLayout(m.vertexLayout)
    }

    private fun uniformSlots(slots: List<UniformSlot>) {
        int(slots.size)
        for (s in slots) { string(s.name); int(s.binding); byte(s.type.ordinal.toByte()); int(s.size) }
    }

    private fun textureSlots(slots: List<TextureSlot>) {
        int(slots.size)
        for (s in slots) { string(s.name); int(s.binding) }
    }

    private fun writeVertexLayout(vl: VertexLayout) {
        vertexAttribs(vl.attributes)
        int(vl.stride)
    }

    private fun vertexAttribs(attrs: List<VertexAttribute>) {
        int(attrs.size)
        for (a in attrs) {
            int(a.shaderLocation); byte(a.format.ordinal.toByte())
            int(a.offset)
        }
    }

    private fun writeUniformLayout(ul: UniformLayout) {
        uniformSlots(ul.slots)
    }

    private fun childSlots(slots: List<ChildSlot>) {
        int(slots.size)
        for (s in slots) { string(s.name); byte(s.type.ordinal.toByte()) }
    }

    fun uniformBlock(ub: UniformBlock) {
        val entries = ub.entries
        int(entries.size)
        for ((name, value) in entries) {
            string(name)
            when (value) {
                is UniformValue.F1 -> { byte(0); float(value.v) }
                is UniformValue.F2 -> { byte(1); float(value.x); float(value.y) }
                is UniformValue.F3 -> { byte(2); float(value.x); float(value.y); float(value.z) }
                is UniformValue.F4 -> { byte(3); float(value.x); float(value.y); float(value.z); float(value.w) }
                is UniformValue.M3 -> { byte(4); matrix33(value.m) }
                is UniformValue.M4 -> { byte(5); int(value.values.size); for (f in value.values) float(f) }
            }
        }
    }

    fun colorFilter(cf: ColorFilter?) {
        if (cf == null) { byte(0xFF.toByte()); return }
        when (cf) {
            is ColorFilter.Matrix -> { byte(0); int(cf.values.size); for (f in cf.values) float(f) }
            is ColorFilter.Blend -> { byte(1); color(cf.color); blendMode(cf.mode) }
            is ColorFilter.Compose -> { byte(2); colorFilter(cf.outer); colorFilter(cf.inner) }
            is ColorFilter.Table -> { byte(3); int(cf.table.size); for (b in cf.table) byte(b.toByte()) }
            is ColorFilter.Lighting -> { byte(4); color(cf.mul); color(cf.add) }
            ColorFilter.SRGBToLinear -> byte(5)
            ColorFilter.LinearToSRGB -> byte(6)
            is ColorFilter.HSLAMatrix -> { byte(7); int(cf.values.size); for (f in cf.values) float(f) }
            is ColorFilter.Lerp -> { byte(8); float(cf.t); colorFilter(cf.dst); colorFilter(cf.src) }
            ColorFilter.HighContrast -> byte(9)
            ColorFilter.Luma -> byte(10)
            ColorFilter.Overdraw -> byte(11)
        }
    }

    fun maskFilter(mf: MaskFilter?) {
        if (mf == null) { byte(0xFF.toByte()); return }
        when (mf) {
            is MaskFilter.Blur -> { byte(0); blurStyle(mf.style); float(mf.sigma) }
            is MaskFilter.Shader -> { byte(1); shader(mf.shader) }
            is MaskFilter.Table -> { byte(2); int(mf.table.size); for (b in mf.table) byte(b.toByte()) }
        }
    }

    fun pathEffect(pe: PathEffect?) {
        if (pe == null) { byte(0xFF.toByte()); return }
        when (pe) {
            is PathEffect.Dash -> { byte(0); int(pe.intervals.size); for (f in pe.intervals) float(f); float(pe.phase) }
            is PathEffect.Corner -> { byte(1); float(pe.radius) }
            is PathEffect.Discrete -> { byte(2); float(pe.segmentLength); float(pe.deviation) }
            is PathEffect.Path1D -> { byte(3); path(pe.path); float(pe.advance); float(pe.phase); byte(pe.style.ordinal.toByte()) }
            is PathEffect.Path2D -> { byte(4); matrix33(pe.matrix); path(pe.path) }
            is PathEffect.Trim -> { byte(5); float(pe.start); float(pe.stop) }
        }
    }

    fun imageFilter(imageFilter: ImageFilter?) {
        if (imageFilter == null) { byte(0xFF.toByte()); return }
        when (imageFilter) {
            is ImageFilter.Blur -> { byte(0); float(imageFilter.sigmaX); float(imageFilter.sigmaY); tileMode(imageFilter.tileMode); imageFilter(imageFilter.input) }
            is ImageFilter.DropShadow -> { byte(1); float(imageFilter.dx); float(imageFilter.dy); float(imageFilter.sigmaX); float(imageFilter.sigmaY); color(imageFilter.color); imageFilter(imageFilter.input) }
            is ImageFilter.ColorFilter -> { byte(2); colorFilter(imageFilter.filter); imageFilter(imageFilter.input) }
            is ImageFilter.Compose -> { byte(3); imageFilter(imageFilter.outer); imageFilter(imageFilter.inner) }
            is ImageFilter.Blend -> { byte(4); blendMode(imageFilter.mode); imageFilter(imageFilter.background); imageFilter(imageFilter.foreground) }
            is ImageFilter.Dilate -> { byte(5); float(imageFilter.radiusX); float(imageFilter.radiusY); imageFilter(imageFilter.input) }
            is ImageFilter.Erode -> { byte(6); float(imageFilter.radiusX); float(imageFilter.radiusY); imageFilter(imageFilter.input) }
            is ImageFilter.DistantLitDiffuse -> { byte(7); point(imageFilter.direction); color(imageFilter.lightColor); float(imageFilter.surfaceScale); float(imageFilter.kd); imageFilter(imageFilter.input) }
            is ImageFilter.PointLitDiffuse -> { byte(8); point(imageFilter.location); color(imageFilter.lightColor); float(imageFilter.surfaceScale); float(imageFilter.kd); imageFilter(imageFilter.input) }
            is ImageFilter.SpotLitDiffuse -> { byte(9); point(imageFilter.location); point(imageFilter.target); float(imageFilter.specularExponent); float(imageFilter.cutoffAngle); color(imageFilter.lightColor); float(imageFilter.surfaceScale); float(imageFilter.kd); imageFilter(imageFilter.input) }
            is ImageFilter.DistantLitSpecular -> { byte(10); point(imageFilter.direction); color(imageFilter.lightColor); float(imageFilter.surfaceScale); float(imageFilter.ks); float(imageFilter.shininess); imageFilter(imageFilter.input) }
            is ImageFilter.PointLitSpecular -> { byte(11); point(imageFilter.location); color(imageFilter.lightColor); float(imageFilter.surfaceScale); float(imageFilter.ks); float(imageFilter.shininess); imageFilter(imageFilter.input) }
            is ImageFilter.SpotLitSpecular -> { byte(12); point(imageFilter.location); point(imageFilter.target); float(imageFilter.specularExponent); float(imageFilter.cutoffAngle); color(imageFilter.lightColor); float(imageFilter.surfaceScale); float(imageFilter.ks); float(imageFilter.shininess); imageFilter(imageFilter.input) }
            is ImageFilter.Offset -> { byte(13); float(imageFilter.dx); float(imageFilter.dy); imageFilter(imageFilter.input) }
            is ImageFilter.Tile -> { byte(14); rect(imageFilter.src); rect(imageFilter.dst); imageFilter(imageFilter.input) }
            is ImageFilter.Merge -> { byte(15); int(imageFilter.inputs.size); for (f in imageFilter.inputs) imageFilter(f) }
            is ImageFilter.DisplacementMap -> { byte(16); colorChannel(imageFilter.xChannelSelector); colorChannel(imageFilter.yChannelSelector); float(imageFilter.scale); imageFilter(imageFilter.displacement); imageFilter(imageFilter.input) }
            is ImageFilter.Magnifier -> { byte(17); rect(imageFilter.src); float(imageFilter.zoom); float(imageFilter.inset); imageFilter(imageFilter.input) }
            is ImageFilter.MatrixConvolution -> {
                byte(18); size(imageFilter.kernelSize); int(imageFilter.kernel.size)
                for (f in imageFilter.kernel) float(f)
                float(imageFilter.gain); float(imageFilter.bias)
                point(imageFilter.kernelOffset); tileMode(imageFilter.tileMode)
                bool(imageFilter.convolveAlpha); imageFilter(imageFilter.input)
            }
        }
    }

    fun blender(b: Blender?) {
        if (b == null) { byte(0xFF.toByte()); return }
        when (b) {
            is Blender.Mode -> { byte(0); blendMode(b.mode) }
            is Blender.Arithmetic -> { byte(1); float(b.k1); float(b.k2); float(b.k3); float(b.k4) }
        }
    }

    fun blendMode(m: BlendMode) { byte(m.ordinal.toByte()) }
    fun tileMode(m: TileMode) { byte(m.ordinal.toByte()) }
    fun blurStyle(s: BlurStyle) { byte(s.ordinal.toByte()) }
    fun colorChannel(c: ColorChannel) { byte(c.ordinal.toByte()) }
    fun colorSpaceInterpolation(c: ColorSpaceInterpolation) { byte(c.ordinal.toByte()) }
    fun pointMode(m: PointMode) { byte(m.ordinal.toByte()) }
    fun vertexMode(m: VertexMode) { byte(m.ordinal.toByte()) }
    fun latticeFlags(f: LatticeFlags) { byte(f.ordinal.toByte()) }
    fun clipOp(op: ClipOp) { byte(op.ordinal.toByte()) }

    fun textBlob(blob: TextBlob) {
        int(blob.glyphRuns.size)
        for (run in blob.glyphRuns) {
            int(run.glyphs.size)
            for (g in run.glyphs) int(g.toInt())
            int(run.positions.size)
            for (p in run.positions) point(p)
        }
        if (blob.typeface != null) { bool(true); string(blob.typeface.resourcePath) } else bool(false)
        float(blob.fontSize)
    }

    fun vertices(v: Vertices) {
        vertexMode(v.mode)
        int(v.positions.size); for (p in v.positions) point(p)
        if (v.texCoords != null) { bool(true); int(v.texCoords.size); for (p in v.texCoords) point(p) } else bool(false)
        if (v.colors != null) { bool(true); int(v.colors.size); for (c in v.colors) color(c) } else bool(false)
        if (v.indices != null) { bool(true); int(v.indices.size); for (i in v.indices) int(i) } else bool(false)
    }

    fun lattice(l: Lattice) {
        int(l.xDivs.size); for (d in l.xDivs) int(d)
        int(l.yDivs.size); for (d in l.yDivs) int(d)
        if (l.rects != null) { bool(true); int(l.rects.size); for (r in l.rects) rect(r) } else bool(false)
        if (l.colors != null) { bool(true); int(l.colors.size); for (c in l.colors) color(c) } else bool(false)
        if (l.flags != null) { bool(true); int(l.flags.size); for (f in l.flags) latticeFlags(f) } else bool(false)
    }

    fun clipStack(cs: ClipStack) {
        when (cs) {
            ClipStack.WideOpen -> byte(0)
            is ClipStack.DeviceRect -> { byte(1); rect(cs.rect); bool(cs.antiAlias) }
            is ClipStack.Complex -> { byte(2); int(cs.ops.size); for (op in cs.ops) clipStackOp(op) }
        }
    }

    private fun clipStackOp(op: ClipStackOp) {
        bool(op.antiAlias)
        when (op) {
            is ClipStackOp.RectOp -> { byte(0); rect(op.rect); clipOp(op.op) }
            is ClipStackOp.RRectOp -> { byte(1); rrect(op.rrect); clipOp(op.op) }
            is ClipStackOp.PathOp -> { byte(2); path(op.path); clipOp(op.op) }
        }
    }

    fun displayOp(op: DisplayOp) {
        when (op) {
            is DisplayOp.DrawRect -> {
                byte(OP_DRAW_RECT); rect(op.rect); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawRRect -> {
                byte(OP_DRAW_R_RECT); rrect(op.rrect); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawDRRect -> {
                byte(OP_DRAW_D_R_RECT); rrect(op.outer); rrect(op.inner); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawPath -> {
                byte(OP_DRAW_PATH); path(op.path); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawPoint -> {
                byte(OP_DRAW_POINT); float(op.x); float(op.y); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawPoints -> {
                byte(OP_DRAW_POINTS); pointMode(op.mode); int(op.points.size)
                for (p in op.points) point(p); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawImage -> {
                byte(OP_DRAW_IMAGE); image(op.image); rect(op.src); rect(op.dst)
                if (op.paint != null) { bool(true); paint(op.paint) } else bool(false)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawImageNine -> {
                byte(OP_DRAW_IMAGE_NINE); image(op.image); rect(op.center); rect(op.dst)
                if (op.paint != null) { bool(true); paint(op.paint) } else bool(false)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawImageLattice -> {
                byte(OP_DRAW_IMAGE_LATTICE); image(op.image); lattice(op.lattice); rect(op.dst)
                if (op.paint != null) { bool(true); paint(op.paint) } else bool(false)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawText -> {
                byte(OP_DRAW_TEXT); textBlob(op.blob); float(op.x); float(op.y); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawPicture -> {
                byte(OP_DRAW_PICTURE); picture(op.picture)
                if (op.paint != null) { bool(true); paint(op.paint) } else bool(false)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawVertices -> {
                byte(OP_DRAW_VERTICES); vertices(op.vertices); paint(op.paint)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawAtlas -> {
                byte(OP_DRAW_ATLAS); image(op.atlas); int(op.transforms.size)
                for (m in op.transforms) matrix33(m)
                for (r in op.texRects) rect(r)
                if (op.colors != null) { bool(true); for (c in op.colors) color(c) } else bool(false)
                blendMode(op.blendMode)
                if (op.paint != null) { bool(true); paint(op.paint) } else bool(false)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.DrawColor -> {
                byte(OP_DRAW_COLOR); color(op.color); blendMode(op.mode)
                matrix33(op.transform); clipStack(op.clip)
            }
            is DisplayOp.Clear -> { byte(OP_CLEAR); color(op.color) }
            is DisplayOp.SetTransform -> { byte(OP_SET_TRANSFORM); matrix33(op.matrix) }
            is DisplayOp.SetClip -> { byte(OP_SET_CLIP); clipStack(op.clip) }
            is DisplayOp.BeginLayer -> {
                byte(OP_BEGIN_LAYER)
                if (op.bounds != null) { bool(true); rect(op.bounds) } else bool(false)
                if (op.paint != null) { bool(true); paint(op.paint) } else bool(false)
            }
            DisplayOp.EndLayer -> byte(OP_END_LAYER)
            is DisplayOp.Annotation -> { byte(OP_ANNOTATION); rect(op.rect); string(op.key); string(op.value) }
        }
    }

    fun picture(p: Picture) {
        // recursively serialize a nested Picture
        val nested = encodePicture(p)
        int(nested.size); bytes(nested)
    }
}

private class Reader(private val data: ByteArray) {
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

    fun rect(): Rect = Rect(float(), float(), float(), float())
    fun point(): Point = Point(float(), float())
    fun size(): Size = Size(float(), float())
    fun color(): Color = Color(int().toUInt())
    fun cornerRadii(): CornerRadii = CornerRadii(float(), float())

    fun matrix33(): Matrix33 {
        val floats = FloatArray(9) { float() }
        return createMatrix33(floats) ?: run { valid = false; Matrix33.identity() }
    }

    fun rrect(): RRect {
        return RRect(rect(), cornerRadii(), cornerRadii(), cornerRadii(), cornerRadii())
    }

    fun path(): Path {
        val fillType = FillType.entries[byte().toInt()]
        val verbCount = int()
        val verbs = List(verbCount) { PathVerb.entries[byte().toInt()] }
        val ptCount = int()
        val pts = List(ptCount) { point() }
        val p = Path()
        p.fillType = fillType
        // Add verbs and points via internal methods
        for (v in verbs) if (!p.addVerb(v)) { valid = false; return p }
        for (pt in pts) if (!p.addPoint(pt)) { valid = false; return p }
        return p
    }

    fun image(): Image {
        val w = int(); val h = int()
        val ct = ColorType.entries[byte().toInt()]
        val srcId = string()
        val hasPixels = bool()
        val px = if (hasPixels) { val len = int(); bytes(len) } else null
        val cs = readColorSpace()
        return Image(w, h, ct, srcId, px, cs)
    }

    fun readColorSpace(): ColorSpace {
        val name = string()
        val tf = TransferFunction.entries[byte().toInt()]
        val g = Gamut.entries[byte().toInt()]
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
        val style = PaintStyle.entries[byte().toInt()]
        val sw = float()
        val cap = StrokeCap.entries[byte().toInt()]
        val join = StrokeJoin.entries[byte().toInt()]
        val sm = float()
        val aa = bool()
        return Paint(c, s, bm, cf, mf, pe, imf, bl, style, sw, cap, join, sm, aa)
    }

    fun shader(): Shader? {
        val disc = byte()
        if (disc == 0xFF.toByte()) return null
        return when (disc.toInt()) {
            0 -> Shader.SolidColor(color())
            1 -> Shader.LinearGradient(point(), point(), gradientStops(), tileMode(), colorSpaceInterpolation())
            2 -> Shader.RadialGradient(point(), float(), gradientStops(), tileMode(), colorSpaceInterpolation())
            3 -> Shader.SweepGradient(point(), float(), float(), gradientStops(), tileMode(), colorSpaceInterpolation())
            4 -> Shader.ConicalGradient(point(), float(), point(), float(), gradientStops(), tileMode(), colorSpaceInterpolation())
            5 -> Shader.Image(image(), tileMode(), tileMode())
            6 -> Shader.Blend(blendMode(), shader()!!, shader()!!)
            7 -> readRuntimeEffect()?.let { re -> readUniformBlock()?.let { ub -> Shader.RuntimeEffect(re, ub) } }
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

    private fun readSizeOrNull(): Size? = if (bool()) size() else null

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
        val uniforms = List(uniformCount) { UniformSlot(string(), int(), UniformType.entries[byte().toInt()], int()) }
        val textureCount = int()
        val textures = List(textureCount) { TextureSlot(string(), int()) }
        val attrCount = int()
        val attrs = List(attrCount) { VertexAttribute(VertexFormat.entries[byte().toInt()], int(), int()) }
        val stride = int()
        val stepMode = VertexStepMode.entries[byte().toInt()]
        // ShaderModule constructor is private; uniforms/textures/vertexLayout cannot be
        // injected into the reconstructed module. If the source had bindings, mark invalid.
        if (uniforms.isNotEmpty() || textures.isNotEmpty() || attrs.isNotEmpty()) {
            valid = false
        }
        return ShaderModule.fromSource(source, entry)
    }

    private fun readUniformLayout(): UniformLayout {
        val n = int()
        val slots = List(n) {
            UniformSlot(string(), int(), UniformType.entries[byte().toInt()], int())
        }
        return UniformLayout(slots)
    }

    private fun readChildSlots(): List<ChildSlot> {
        val n = int()
        return List(n) { ChildSlot(string(), ChildType.entries[byte().toInt()]) }
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
            0 -> ColorFilter.Matrix(FloatArray(int()) { float() })
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
            3 -> PathEffect.Path1D(path(), float(), float(), Path1DStyle.entries[byte().toInt()])
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
            1 -> ImageFilter.DropShadow(float(), float(), float(), float(), color(), imageFilter())
            2 -> ImageFilter.ColorFilter(colorFilter()!!, imageFilter())
            3 -> ImageFilter.Compose(imageFilter()!!, imageFilter()!!)
            4 -> ImageFilter.Blend(blendMode(), imageFilter()!!, imageFilter()!!)
            5 -> ImageFilter.Dilate(float(), float(), imageFilter())
            6 -> ImageFilter.Erode(float(), float(), imageFilter())
            7 -> ImageFilter.DistantLitDiffuse(point(), color(), float(), float(), imageFilter())
            8 -> ImageFilter.PointLitDiffuse(point(), color(), float(), float(), imageFilter())
            9 -> ImageFilter.SpotLitDiffuse(point(), point(), float(), float(), color(), float(), float(), imageFilter())
            10 -> ImageFilter.DistantLitSpecular(point(), color(), float(), float(), float(), imageFilter())
            11 -> ImageFilter.PointLitSpecular(point(), color(), float(), float(), float(), imageFilter())
            12 -> ImageFilter.SpotLitSpecular(point(), point(), float(), float(), color(), float(), float(), float(), imageFilter())
            13 -> ImageFilter.Offset(float(), float(), imageFilter())
            14 -> ImageFilter.Tile(rect(), rect(), imageFilter())
            15 -> ImageFilter.Merge(List(int()) { imageFilter()!! })
            16 -> ImageFilter.DisplacementMap(colorChannel(), colorChannel(), float(), imageFilter()!!, imageFilter())
            17 -> ImageFilter.Magnifier(rect(), float(), float(), imageFilter())
            18 -> ImageFilter.MatrixConvolution(size(), FloatArray(int()) { float() }, float(), float(), point(), tileMode(), bool(), imageFilter())
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

    fun blendMode(): BlendMode = BlendMode.entries[byte().toInt()]
    fun tileMode(): TileMode = TileMode.entries[byte().toInt()]
    fun blurStyle(): BlurStyle = BlurStyle.entries[byte().toInt()]
    fun colorChannel(): ColorChannel = ColorChannel.entries[byte().toInt()]
    fun colorSpaceInterpolation(): ColorSpaceInterpolation = ColorSpaceInterpolation.entries[byte().toInt()]
    fun pointMode(): PointMode = PointMode.entries[byte().toInt()]
    fun vertexMode(): VertexMode = VertexMode.entries[byte().toInt()]
    fun latticeFlags(): LatticeFlags = LatticeFlags.entries[byte().toInt()]
    fun clipOp(): ClipOp = ClipOp.entries[byte().toInt()]

    fun textBlob(): TextBlob {
        val runs = List(int()) {
            val glyphs = List<UShort>(int()) { int().toUShort() }
            val positions = List(int()) { point() }
            KanvasGlyphRun(glyphs, positions)
        }
        val typeface = if (bool()) KanvasTypeface(string()) else null
        val fontSize = float()
        return TextBlob(runs, typeface, fontSize)
    }

    fun vertices(): Vertices {
        val mode = vertexMode()
        val positions = List(int()) { point() }
        val texCoords = if (bool()) List(int()) { point() } else null
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
            2 -> ClipStackOp.PathOp(path(), clipOp(), aa)
            else -> { valid = false; ClipStackOp.RectOp(Rect.EMPTY, ClipOp.INTERSECT, aa) }
        }
    }

    fun displayOp(): DisplayOp? {
        val disc = byte()
        return when (disc.toInt()) {
            OP_DRAW_RECT.toInt() -> DisplayOp.DrawRect(rect(), paint(), matrix33(), clipStack())
            OP_DRAW_R_RECT.toInt() -> DisplayOp.DrawRRect(rrect(), paint(), matrix33(), clipStack())
            OP_DRAW_D_R_RECT.toInt() -> DisplayOp.DrawDRRect(rrect(), rrect(), paint(), matrix33(), clipStack())
            OP_DRAW_PATH.toInt() -> DisplayOp.DrawPath(path(), paint(), matrix33(), clipStack())
            OP_DRAW_POINT.toInt() -> DisplayOp.DrawPoint(float(), float(), paint(), matrix33(), clipStack())
            OP_DRAW_POINTS.toInt() -> {
                val mode = pointMode()
                val pts = List(int()) { point() }
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
                DisplayOp.DrawImageLattice(img, lat, dst, p, matrix33(), clipStack())
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
                DisplayOp.BeginLayer(bounds, p)
            }
            OP_END_LAYER.toInt() -> DisplayOp.EndLayer
            OP_ANNOTATION.toInt() -> DisplayOp.Annotation(rect(), string(), string())
            else -> { valid = false; null }
        }
    }
}

private fun encodePicture(picture: Picture): ByteArray {
    val w = Writer()
    w.bytes(MAGIC)
    w.int(FORMAT_VERSION)
    w.rect(picture.cullRect)
    w.int(picture.ops.size)
    for (op in picture.ops) w.displayOp(op)
    return w.result()
}

private fun decodePicture(data: ByteArray): Picture? {
    if (data.size < 4) return null
    if (data[0] != 0x4B.toByte() || data[1] != 0x50.toByte() ||
        data[2] != 0x49.toByte() || data[3] != 0x43.toByte()) return null
    val r = Reader(data)
    r.bytes(4) // skip magic
    val version = r.int()
    if (version != 1 || !r.valid) return null
    val cullRect = r.rect()
    val opCount = r.int()
    if (opCount < 0 || !r.valid) return null
    val ops = mutableListOf<DisplayOp>()
    for (i in 0 until opCount) {
        val op = r.displayOp()
        if (op == null || !r.valid) return null
        ops.add(op)
    }
    return Picture(cullRect, ops)
}

// ---- Workarounds for types with private constructors -----------------------
// These reflection accesses are guarded: if any fail (e.g., due to JDK module
// restrictions or constructor changes), deserialization returns null rather
// than throwing at runtime.

private fun createMatrix33(values: FloatArray): Matrix33? = try {
    val constructor = Matrix33::class.java.getDeclaredConstructor(FloatArray::class.java)
    constructor.isAccessible = true
    constructor.newInstance(values)
} catch (_: Exception) { null }

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

// Add missing methods to Path for serialization construction
internal fun Path.addVerb(verb: PathVerb): Boolean = try {
    val field = Path::class.java.getDeclaredField("verbs")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as MutableList<PathVerb>).add(verb)
    true
} catch (_: Exception) { false }

internal fun Path.addPoint(pt: Point): Boolean = try {
    val field = Path::class.java.getDeclaredField("points")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as MutableList<Point>).add(pt)
    true
} catch (_: Exception) { false }

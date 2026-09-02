@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.render.ir

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.Gamut
import org.graphiks.kanvas.color.TransferFunction
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32

/**
 * The owner of the version-8 Picture payload.
 *
 * A v8 archive starts with the public `KPIC` magic, its v8 integer and the
 * cull rectangle.  The following negative marker occupies the old v8
 * `opCount` slot: it can therefore never be mistaken for a valid historical
 * v8 op count.  Historical Task 8 v8 streams deliberately return [LegacyV8]
 * so the Kanvas facade can read them without introducing a dependency cycle.
 */
public object SceneArchiveCodec {
    private val magic: ByteArray = byteArrayOf(0x4b, 0x50, 0x49, 0x43)
    private const val pictureVersion: Int = 8
    private const val irMarker: Int = -1_391_019_346
    private const val schemaVersion: Int = 1

    /** Encodes a deeply immutable Scene IR as the sole v8 Picture writer. */
    public fun encodePicture(scene: SceneSnapshot, cullRect: RectF32): ByteArray {
        requireSemanticValidity(scene)
        val writer = ArchiveWriter()
        writer.bytes(magic)
        writer.i32(pictureVersion)
        writer.rect(cullRect)
        writer.i32(irMarker)
        writer.i32(schemaVersion)
        writer.scene(scene)
        return writer.finish()
    }

    /** Decodes a v8 IR Picture without allocating any backend object. */
    public fun decodePicture(data: ByteArray): SceneArchiveDecodeResult {
        val reader = ArchiveReader(data)
        return try {
            if (!reader.bytesEqual(magic)) return SceneArchiveDecodeResult.Invalid("invalid-magic", "Picture magic is not KPIC")
            if (reader.i32() != pictureVersion) return SceneArchiveDecodeResult.Invalid("unknown-version", "Picture version is not 8")
            val cull = reader.rect()
            val markerOrLegacyOpCount = reader.i32()
            if (markerOrLegacyOpCount != irMarker) {
                return if (markerOrLegacyOpCount >= 0) {
                    SceneArchiveDecodeResult.LegacyV8
                } else {
                    SceneArchiveDecodeResult.Invalid("invalid-marker", "Picture archive marker is not recognized")
                }
            }
            if (reader.i32() != schemaVersion) return SceneArchiveDecodeResult.Invalid("unknown-schema", "Scene archive schema is not supported")
            val scene = reader.scene()
            reader.requireEnd()
            when (val validation = SceneSemanticValidator.validate(scene)) {
                SceneSemanticValidationResult.Valid -> SceneArchiveDecodeResult.Decoded(scene, cull)
                is SceneSemanticValidationResult.Invalid -> SceneArchiveDecodeResult.Invalid(validation.code, validation.message)
            }
        } catch (failure: ArchiveFailure) {
            SceneArchiveDecodeResult.Invalid(failure.code, failure.message)
        } catch (_: IllegalArgumentException) {
            SceneArchiveDecodeResult.Invalid("invalid-value", "Archive contains an invalid Scene IR value")
        } catch (_: ArithmeticException) {
            SceneArchiveDecodeResult.Invalid("invalid-value", "Archive contains an overflowing Scene IR value")
        }
    }

    private fun requireSemanticValidity(scene: SceneSnapshot) {
        when (val validation = SceneSemanticValidator.validate(scene)) {
            SceneSemanticValidationResult.Valid -> Unit
            is SceneSemanticValidationResult.Invalid -> throw IllegalArgumentException(
                "Scene archive semantic validation failed: ${validation.code}",
            )
        }
    }
}

/** Typed, fail-closed outcome of [SceneArchiveCodec.decodePicture]. */
public sealed interface SceneArchiveDecodeResult {
    public class Decoded internal constructor(
        public val scene: SceneSnapshot,
        cullRect: RectF32,
    ) : SceneArchiveDecodeResult {
        private val storedCullRect: RectF32 = cullRect.copy()
        public fun copyCullRect(): RectF32 = storedCullRect.copy()
    }

    /** The old non-negative v8 operation-count slot is present; the facade must use its legacy reader. */
    public data object LegacyV8 : SceneArchiveDecodeResult

    public data class Invalid(public val code: String, public val message: String) : SceneArchiveDecodeResult
}

private const val MAX_COLLECTION_SIZE = 1_000_000
private const val MAX_BINARY_SIZE = 64 * 1024 * 1024
/*
 * Wire frames are more granular than semantic graph edges: a Picture scene
 * crosses scene/command/draw/geometry frames before reaching its child scene.
 * Eight wire frames per semantic edge bounds every current recursive writer
 * path while preserving the public GraphLimits depth of 64.
 */
private const val MAX_NESTING = 512

private class ArchiveFailure(val code: String, override val message: String) : RuntimeException(message)

private class ArchiveWriter {
    private val output = ByteArrayOutputStream()
    private val stream = DataOutputStream(output)
    private var depth = 0

    fun finish(): ByteArray = output.toByteArray()
    fun bytes(value: ByteArray) = stream.write(value)
    fun i32(value: Int) = stream.writeInt(value)
    fun bool(value: Boolean) = stream.writeBoolean(value)
    fun f32(value: Float) {
        require(value.isFinite()) { "Scene archives reject non-finite floats" }
        stream.writeFloat(value)
    }
    fun text(value: String) {
        val bytes = try {
            val encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
            ByteArray(encoded.remaining()).also(encoded::get)
        } catch (failure: CharacterCodingException) {
            throw IllegalArgumentException("Scene archive text is not valid UTF-8", failure)
        }
        require(bytes.size <= MAX_BINARY_SIZE) { "String exceeds archive limit" }
        i32(bytes.size); bytes(bytes)
    }
    fun <T> optional(value: T?, write: (T) -> Unit) { bool(value != null); if (value != null) write(value) }
    fun color(value: ColorARGB) = i32(value.value.toInt())
    fun colorF32(value: ColorF32) { f32(value.red); f32(value.green); f32(value.blue); f32(value.alpha) }
    fun point(value: Point2F32) { f32(value.x); f32(value.y) }
    fun vector(value: Vector2F32) { f32(value.x); f32(value.y) }
    fun size(value: SizeF32) { f32(value.width); f32(value.height) }
    fun rect(value: RectF32) { f32(value.left); f32(value.top); f32(value.right); f32(value.bottom) }
    fun rrect(value: RRectF32) {
        rect(value.rect)
        corner(value.topLeft); corner(value.topRight); corner(value.bottomRight); corner(value.bottomLeft)
    }
    fun corner(value: CornerRadiiF32) { f32(value.x); f32(value.y) }
    fun matrix(value: Matrix3x3F32) {
        f32(value.sx); f32(value.kx); f32(value.tx); f32(value.ky); f32(value.sy); f32(value.ty)
        f32(value.persp0); f32(value.persp1); f32(value.persp2)
    }
    fun colorSpace(value: ColorSpace) { text(value.name); enum(value.transferFunction); enum(value.gamut) }
    fun <T : Enum<T>> enum(value: T) = text(value.name)
    fun <T> list(values: Collection<T>, write: (T) -> Unit) {
        require(values.size <= MAX_COLLECTION_SIZE) { "Collection exceeds archive limit" }
        i32(values.size); values.forEach(write)
    }
    fun ints(values: IntArray) { require(values.size <= MAX_COLLECTION_SIZE); i32(values.size); values.forEach(::i32) }
    fun floats(values: FloatArray) { require(values.size <= MAX_COLLECTION_SIZE); i32(values.size); values.forEach(::f32) }
    fun ubytes(values: UByteArray) { require(values.size <= MAX_BINARY_SIZE); i32(values.size); values.forEach { stream.writeByte(it.toInt()) } }
    fun byteArray(values: ByteArray) { require(values.size <= MAX_BINARY_SIZE); i32(values.size); bytes(values) }
    fun stringFloatMap(values: Map<String, Float>) { list(values.entries) { (name, value) -> text(name); f32(value) } }
    fun uniforms(values: Map<String, RuntimeUniformValue>) { list(values.entries) { (name, value) -> text(name); uniform(value) } }

    private inline fun nested(block: () -> Unit) {
        require(depth < MAX_NESTING) { "Scene archive graph exceeds nesting limit" }
        depth += 1
        try { block() } finally { depth -= 1 }
    }

    fun scene(scene: SceneSnapshot): Unit = nested {
        i32(scene.extent.width); i32(scene.extent.height); colorSpace(scene.colorSpace)
        list(scene.toList()) { command(it) }
    }

    fun command(value: SceneCommand): Unit = nested {
        when (value) {
            is SceneCommand.Draw -> { i32(1); draw(value.node) }
            is SceneCommand.Clear -> { i32(2); colorF32(value.color) }
            is SceneCommand.DrawColor -> { i32(3); color(value.color); enum(value.mode); matrix(value.transform); clip(value.clip) }
            is SceneCommand.SetTransform -> { i32(4); matrix(value.matrix) }
            is SceneCommand.SetClip -> { i32(5); clip(value.clip) }
            is SceneCommand.BeginLayer -> { i32(6); layer(value.descriptor) }
            SceneCommand.EndLayer -> i32(7)
            is SceneCommand.State -> { i32(8); text(value.name); list(value.entries().entries) { (key, entry) -> text(key); text(entry) } }
            is SceneCommand.Annotation -> { i32(9); rect(value.copyBounds()); text(value.key); text(value.value) }
            is SceneCommand.Readback -> { i32(10); text(value.request.label); rect(value.request.copyBounds()) }
        }
    }

    fun draw(value: DrawNode): Unit = nested {
        geometry(value.geometry); material(value.material); enum(value.coverage); clip(value.clip); blend(value.blend); effects(value.effects)
        matrix(value.transform); enum(value.origin); optional(value.paint, ::paint)
        optional(value.resource, ::image)
        optional(value.operationBlendMode) { enum(it) }
    }

    fun layer(value: LayerDescriptor): Unit = nested {
        optional(value.label, ::text); optional(value.copyBounds(), ::rect)
        optional(value.material, ::material); optional(value.paint, ::paint)
        blend(value.blend); clip(value.clip); optional(value.compositeClip, ::clip)
        effects(value.backdrop); effects(value.effects); matrix(value.transform)
    }

    fun paint(value: PaintNode): Unit = nested {
        color(value.color); optional(value.shader, ::material); enum(value.blendMode)
        optional(value.blender, ::blender); optional(value.colorFilter, ::colorFilter)
        optional(value.maskFilter, ::maskFilter); optional(value.pathEffect, ::pathEffect)
        optional(value.imageFilter, ::imageFilter); enum(value.style); f32(value.strokeWidth)
        enum(value.strokeCap); enum(value.strokeJoin); f32(value.strokeMiter); bool(value.antiAlias)
    }

    fun geometry(value: GeometryNode): Unit = nested {
        when (value) {
            is GeometryNode.Rect -> { i32(1); rect(value.copyBounds()) }
            is GeometryNode.RRect -> { i32(2); rrect(value.copyShape()) }
            is GeometryNode.DoubleRRect -> { i32(3); rrect(value.copyOuter()); rrect(value.copyInner()) }
            is GeometryNode.Path -> { i32(4); path(value.path) }
            is GeometryNode.Points -> { i32(5); enum(value.mode); list(value.toList()) { point(it) } }
            is GeometryNode.IndexedMesh -> {
                i32(6); enum(value.primitiveMode); list((0 until value.vertexCount).map(value::vertexAt)) { point(it) }
                optional(value.copyTexCoords()) { list(it) { point(it) } }
                optional(value.copyColors()) { list(it) { color(it) } }
                optional(value.copyIndices(), ::ints); optional(value.copyBounds(), ::rect)
                optional(value.program, ::resourceRef); optional(value.meshProgram, ::meshProgram)
            }
            is GeometryNode.ImagePatch -> { i32(7); resourceRef(value.image); rect(value.copySource()); rect(value.copyDestination()) }
            is GeometryNode.ImageLattice -> {
                i32(8); resourceRef(value.image); ints(value.copyXDivs()); ints(value.copyYDivs())
                optional(value.copyCellRects()) { list(it) { rect(it) } }; optional(value.copyColors()) { list(it) { color(it) } }
                optional(value.copyFlags()) { list(it) { flag -> enum(flag) } }; rect(value.copyDestination()); sampling(value.sampling)
            }
            is GeometryNode.Atlas -> { i32(9); resourceRef(value.image); list(value.toList()) { atlasEntry(it) } }
            is GeometryNode.GlyphRun -> {
                i32(10); ints(value.copyGlyphIds()); list((0 until value.glyphCount).map(value::positionAt)) { point(it) }; f32(value.fontSize)
                stringFloatMap(value.variations()); optional(value.typeface, ::typeface)
            }
            is GeometryNode.TextBlob -> {
                i32(11); list(value.toList()) { glyphRun(it) }; f32(value.x); f32(value.y); optional(value.typeface, ::typeface)
                f32(value.fontSize); stringFloatMap(value.variationCoordinates())
            }
            is GeometryNode.Picture -> { i32(12); scene(value.scene); rect(value.copyCullRect()) }
        }
    }

    fun atlasEntry(value: GeometryNode.AtlasEntry) { matrix(value.transform); rect(value.copySource()); optional(value.color, ::color) }
    fun glyphRun(value: GeometryNode.GlyphRun) { ints(value.copyGlyphIds()); list((0 until value.glyphCount).map(value::positionAt)) { point(it) }; f32(value.fontSize); stringFloatMap(value.variations()); optional(value.typeface, ::typeface) }
    fun typeface(value: TypefaceReference) = text(value.id.value)
    fun resourceRef(value: ResourceReference) = text(value.id.value)
    fun sampling(value: ImageSampling): Unit = when (value) {
        ImageSampling.Nearest -> i32(1); ImageSampling.Linear -> i32(2); is ImageSampling.Cubic -> { i32(3); f32(value.b); f32(value.c) }
    }

    fun path(value: PathF32): Unit = nested {
        enum(value.fillRule); list(value.toList()) { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> { i32(1); point(segment.point) }
                is PathSegmentF32.LineTo -> { i32(2); point(segment.point) }
                is PathSegmentF32.QuadTo -> { i32(3); point(segment.control); point(segment.point) }
                is PathSegmentF32.CubicTo -> { i32(4); point(segment.control1); point(segment.control2); point(segment.point) }
                is PathSegmentF32.ArcTo -> { i32(5); vector(segment.radius); f32(segment.xAxisRotation); bool(segment.largeArc); bool(segment.sweep); point(segment.point) }
                PathSegmentF32.Close -> i32(6)
            }
        }
    }

    fun material(value: MaterialNode): Unit = nested {
        when (value) {
            MaterialNode.Transparent -> i32(1)
            is MaterialNode.Solid -> { i32(2); color(value.color) }
            is MaterialNode.LinearGradient -> { i32(3); point(value.start); point(value.end); stops(value.stops()); enum(value.tileMode); enum(value.interpolation) }
            is MaterialNode.RadialGradient -> { i32(4); point(value.center); f32(value.radius); stops(value.stops()); enum(value.tileMode); enum(value.interpolation) }
            is MaterialNode.SweepGradient -> { i32(5); point(value.center); f32(value.startAngle); f32(value.endAngle); stops(value.stops()); enum(value.tileMode); enum(value.interpolation) }
            is MaterialNode.ConicalGradient -> { i32(6); point(value.start); f32(value.startRadius); point(value.end); f32(value.endRadius); stops(value.stops()); enum(value.tileMode); enum(value.interpolation) }
            is MaterialNode.ImageSample -> { i32(7); image(value.image); enum(value.tileModeX); enum(value.tileModeY); sampling(value.sampling) }
            is MaterialNode.Blend -> { i32(8); enum(value.mode); material(value.dst); material(value.src) }
            is MaterialNode.RuntimeEffect -> { i32(9); descriptor(value.descriptor); uniforms(value.uniforms()); list(value.toList()) { text(it.name); material(it.material) } }
            is MaterialNode.WithLocalMatrix -> { i32(10); material(value.material); matrix(value.matrix) }
            is MaterialNode.WithColorFilter -> { i32(11); material(value.material); colorFilter(value.filter) }
            is MaterialNode.Opacity -> { i32(12); material(value.material); f32(value.alpha) }
            is MaterialNode.PerlinNoise -> { i32(13); f32(value.baseX); f32(value.baseY); i32(value.numOctaves); i32(value.seed); optional(value.tileSize, ::size) }
            is MaterialNode.FractalNoise -> { i32(14); f32(value.baseX); f32(value.baseY); i32(value.numOctaves); i32(value.seed); optional(value.tileSize, ::size) }
            is MaterialNode.WithWorkingColorSpace -> { i32(15); material(value.material); enum(value.interpolation) }
            is MaterialNode.CoordClamp -> { i32(16); material(value.material); rect(value.copySubset()) }
        }
    }
    fun stops(values: List<GradientStop>): Unit = list(values) { f32(it.position); color(it.color) }

    fun image(value: ImageResourceSnapshot): Unit = nested {
        when (value) {
            is ImageResourceSnapshot.Pixels -> { i32(1); imageMetadata(value); i32(value.rowBytes); byteArray(value.copyPixels()) }
            is ExternalImageReference -> { i32(2); imageMetadata(value) }
        }
    }
    fun imageMetadata(value: ImageResourceSnapshot) { text(value.sourceId); i32(value.width); i32(value.height); enum(value.pixelFormat); enum(value.alphaType); colorSpace(value.colorSpace) }

    fun descriptor(value: RuntimeEffectDescriptor): Unit = nested {
        text(value.id.value); enum(value.abi); list(value.uniformLayout.toList()) { uniformSlot(it) }
        list(value.toList()) { text(it.name); enum(it.type) }
        optional(value.vertexLayout, ::vertexLayout)
        optional(value.module, ::module)
    }
    fun uniformSlot(value: RuntimeUniformSlot) { text(value.name); i32(value.binding); enum(value.type); i32(value.size) }
    fun vertexLayout(value: RuntimeVertexLayout) { i32(value.stride); enum(value.stepMode); list(value.toList()) { enum(it.format); i32(it.offset); i32(it.shaderLocation) } }
    fun module(value: ShaderModuleDescriptor) { text(value.source); text(value.entryPoint); list(value.uniforms()) { uniformSlot(it) }; list(value.textures()) { text(it.name); i32(it.binding) } }
    fun uniform(value: RuntimeUniformValue): Unit = when (value) {
        is RuntimeUniformValue.F1 -> { i32(1); f32(value.value) }; is RuntimeUniformValue.F2 -> { i32(2); f32(value.x); f32(value.y) }
        is RuntimeUniformValue.F3 -> { i32(3); f32(value.x); f32(value.y); f32(value.z) }; is RuntimeUniformValue.F4 -> { i32(4); f32(value.x); f32(value.y); f32(value.z); f32(value.w) }
        is RuntimeUniformValue.I1 -> { i32(5); i32(value.value) }; is RuntimeUniformValue.M3 -> { i32(6); matrix(value.value) }
        is RuntimeUniformValue.M4 -> { i32(7); floats(value.copyValues()) }
    }
    fun meshProgram(value: MeshProgramNode): Unit = nested { descriptor(value.descriptor); uniforms(value.uniforms()); list(value.toList()) { text(it.name); when (it) { is MeshProgramChild.Shader -> { i32(1); material(it.material) }; is MeshProgramChild.ColorFilter -> { i32(2); colorFilter(it.filter) }; is MeshProgramChild.Blender -> { i32(3); blender(it.blender) } } } }

    fun blend(value: BlendNode): Unit = nested { when (value) { BlendNode.SrcOver -> i32(1); is BlendNode.Mode -> { i32(2); enum(value.mode) }; is BlendNode.Custom -> { i32(3); blender(value.blender) }; is BlendNode.Paint -> { i32(4); enum(value.mode); optional(value.blender, ::blender) } } }
    fun blender(value: BlenderNode): Unit = when (value) { is BlenderNode.Mode -> { i32(1); enum(value.mode) }; is BlenderNode.Arithmetic -> { i32(2); f32(value.k1); f32(value.k2); f32(value.k3); f32(value.k4) } }
    fun clip(value: ClipStackNode): Unit = nested { when (value) { ClipStackNode.Empty -> i32(1); is ClipStackNode.DeviceRect -> { i32(2); rect(value.copyBounds()); bool(value.antiAlias) }; is ClipStackNode.Operations -> { i32(3); list(value.toList()) { geometry(it.geometry); enum(it.operation); bool(it.antiAlias); bool(it.perspectiveCaptureRefusal); text(it.transformClass) } } } }
    fun effects(value: EffectStack): Unit = when (value) { EffectStack.Empty -> i32(1); is EffectStack.Entries -> { i32(2); list(value.toList()) { effect(it) } } }
    fun effect(value: EffectNode): Unit = when (value) { is ColorFilterNode -> { i32(1); colorFilter(value) }; is MaskFilterNode -> { i32(2); maskFilter(value) }; is PathEffectNode -> { i32(3); pathEffect(value) }; is ImageFilterNode -> { i32(4); imageFilter(value) } }
    fun colorFilter(value: ColorFilterNode): Unit = nested { when (value) {
        is ColorFilterNode.Matrix -> { i32(1); floats(value.values.copyToFloatArray()) }; is ColorFilterNode.Blend -> { i32(2); color(value.color); enum(value.mode) }
        is ColorFilterNode.Compose -> { i32(3); colorFilter(value.outer); colorFilter(value.inner) }; is ColorFilterNode.Table -> { i32(4); ubytes(value.table.copyToUByteArray()) }
        is ColorFilterNode.Lighting -> { i32(5); color(value.mul); color(value.add) }; ColorFilterNode.SRGBToLinear -> i32(6); ColorFilterNode.LinearToSRGB -> i32(7)
        is ColorFilterNode.HSLAMatrix -> { i32(8); floats(value.values.copyToFloatArray()) }; is ColorFilterNode.Lerp -> { i32(9); f32(value.t); colorFilter(value.dst); colorFilter(value.src) }
        ColorFilterNode.HighContrast -> i32(10); ColorFilterNode.Luma -> i32(11); ColorFilterNode.Overdraw -> i32(12)
        is ColorFilterNode.RuntimeEffect -> { i32(13); descriptor(value.descriptor); uniforms(value.uniforms()); list(value.toList()) { text(it.name); colorFilter(it.filter) } }
    } }
    fun maskFilter(value: MaskFilterNode): Unit = nested { when (value) { is MaskFilterNode.Blur -> { i32(1); enum(value.style); f32(value.sigma) }; is MaskFilterNode.Shader -> { i32(2); material(value.material) }; is MaskFilterNode.Table -> { i32(3); ubytes(value.table.copyToUByteArray()) } } }
    fun pathEffect(value: PathEffectNode): Unit = nested { when (value) { is PathEffectNode.Dash -> { i32(1); floats(value.intervals.copyToFloatArray()); f32(value.phase) }; is PathEffectNode.Corner -> { i32(2); f32(value.radius) }; is PathEffectNode.Discrete -> { i32(3); f32(value.segmentLength); f32(value.deviation) }; is PathEffectNode.Path1D -> { i32(4); path(value.path); f32(value.advance); f32(value.phase); enum(value.style) }; is PathEffectNode.Path2D -> { i32(5); matrix(value.matrix); path(value.path) }; is PathEffectNode.Trim -> { i32(6); f32(value.start); f32(value.stop) } } }
    fun imageFilter(value: ImageFilterNode): Unit = nested { when (value) {
        is ImageFilterNode.Crop -> { i32(1); rect(value.copyCrop()); enum(value.tileMode); optional(value.input, ::imageFilter) }
        is ImageFilterNode.Blur -> { i32(2); f32(value.sigmaX); f32(value.sigmaY); enum(value.tileMode); optional(value.input, ::imageFilter) }
        is ImageFilterNode.DropShadow -> { i32(3); f32(value.dx); f32(value.dy); f32(value.sigmaX); f32(value.sigmaY); color(value.color); optional(value.input, ::imageFilter) }
        is ImageFilterNode.ColorFilter -> { i32(4); colorFilter(value.filter); optional(value.input, ::imageFilter) }
        is ImageFilterNode.Compose -> { i32(5); imageFilter(value.outer); imageFilter(value.inner) }; is ImageFilterNode.Blend -> { i32(6); enum(value.mode); imageFilter(value.background); imageFilter(value.foreground) }
        is ImageFilterNode.Dilate -> { i32(7); f32(value.radiusX); f32(value.radiusY); optional(value.input, ::imageFilter) }; is ImageFilterNode.Erode -> { i32(8); f32(value.radiusX); f32(value.radiusY); optional(value.input, ::imageFilter) }
        is ImageFilterNode.DistantLitDiffuse -> { i32(9); f32(value.directionX); f32(value.directionY); color(value.lightColor); f32(value.surfaceScale); f32(value.kd); optional(value.input, ::imageFilter) }
        is ImageFilterNode.PointLitDiffuse -> { i32(10); point(value.location); color(value.lightColor); f32(value.surfaceScale); f32(value.kd); optional(value.input, ::imageFilter) }
        is ImageFilterNode.SpotLitDiffuse -> { i32(11); point(value.location); point(value.target); f32(value.specularExponent); f32(value.cutoffAngle); color(value.lightColor); f32(value.surfaceScale); f32(value.kd); optional(value.input, ::imageFilter) }
        is ImageFilterNode.DistantLitSpecular -> { i32(12); f32(value.directionX); f32(value.directionY); color(value.lightColor); f32(value.surfaceScale); f32(value.ks); f32(value.shininess); optional(value.input, ::imageFilter) }
        is ImageFilterNode.PointLitSpecular -> { i32(13); point(value.location); color(value.lightColor); f32(value.surfaceScale); f32(value.ks); f32(value.shininess); optional(value.input, ::imageFilter) }
        is ImageFilterNode.SpotLitSpecular -> { i32(14); point(value.location); point(value.target); f32(value.specularExponent); f32(value.cutoffAngle); color(value.lightColor); f32(value.surfaceScale); f32(value.ks); f32(value.shininess); optional(value.input, ::imageFilter) }
        is ImageFilterNode.Offset -> { i32(15); f32(value.dx); f32(value.dy); optional(value.input, ::imageFilter) }
        is ImageFilterNode.Tile -> { i32(16); rect(value.copySource()); rect(value.copyDestination()); optional(value.input, ::imageFilter) }
        is ImageFilterNode.Merge -> { i32(17); list(value.toList()) { imageFilter(it) } }
        is ImageFilterNode.DisplacementMap -> { i32(18); enum(value.xChannelSelector); enum(value.yChannelSelector); f32(value.scale); imageFilter(value.displacement); optional(value.input, ::imageFilter) }
        is ImageFilterNode.Picture -> { i32(19); scene(value.scene); rect(value.copyCullRect()); optional(value.copySource(), ::rect) }
        is ImageFilterNode.Magnifier -> { i32(20); rect(value.copySource()); f32(value.zoom); f32(value.inset); optional(value.input, ::imageFilter) }
        is ImageFilterNode.MatrixConvolution -> { i32(21); size(value.kernelSize); floats(value.kernel.copyToFloatArray()); f32(value.gain); f32(value.bias); vector(value.kernelOffset); enum(value.tileMode); bool(value.convolveAlpha); optional(value.input, ::imageFilter) }
        is ImageFilterNode.RuntimeEffect -> { i32(22); descriptor(value.descriptor); uniforms(value.uniforms()); optional(value.childShaderName, ::text); list(value.toList()) { text(it.name); optional(it.filter, ::imageFilter) } }
    } }
}

private class ArchiveReader(private val data: ByteArray) {
    private var offset: Int = 0
    private var depth = 0

    fun bytesEqual(expected: ByteArray): Boolean {
        if (data.size - offset < expected.size) throw ArchiveFailure("truncated", "Archive ended before its magic")
        return expected.indices.all { data[offset + it] == expected[it] }.also { offset += expected.size }
    }
    fun requireEnd() { if (offset != data.size) throw ArchiveFailure("trailing-data", "Archive contains trailing bytes") }
    fun i32(): Int { requireBytes(4); val value = (data[offset].toInt() shl 24) or ((data[offset + 1].toInt() and 255) shl 16) or ((data[offset + 2].toInt() and 255) shl 8) or (data[offset + 3].toInt() and 255); offset += 4; return value }
    fun bool(): Boolean = when (val value = byte()) { 0 -> false; 1 -> true; else -> throw ArchiveFailure("invalid-boolean", "Boolean tag $value is invalid") }
    fun byte(): Int { requireBytes(1); return data[offset++].toInt() and 255 }
    fun f32(): Float = Float.fromBits(i32()).also { if (!it.isFinite()) throw ArchiveFailure("non-finite", "Archive contains a non-finite float") }
    fun text(): String {
        val length = length(MAX_BINARY_SIZE, "string")
        requireBytes(length)
        val start = offset
        offset += length
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, start, length))
                .toString()
        } catch (_: CharacterCodingException) {
            throw ArchiveFailure("invalid-utf8", "Archive contains malformed UTF-8 text")
        }
    }
    fun color(): ColorARGB = ColorARGB.fromPackedInt(i32())
    fun colorF32(): ColorF32 = ColorF32.of(f32(), f32(), f32(), f32())
    fun point(): Point2F32 = Point2F32(f32(), f32())
    fun vector(): Vector2F32 = Vector2F32(f32(), f32())
    fun size(): SizeF32 = SizeF32(f32(), f32())
    fun rect(): RectF32 = RectF32(f32(), f32(), f32(), f32())
    fun rrect(): RRectF32 = RRectF32(rect(), corner(), corner(), corner(), corner())
    fun corner(): CornerRadiiF32 = CornerRadiiF32(f32(), f32())
    fun matrix(): Matrix3x3F32 = Matrix3x3F32.of(f32(), f32(), f32(), f32(), f32(), f32(), f32(), f32(), f32())
    inline fun <reified T : Enum<T>> enum(): T = try { enumValueOf<T>(text()) } catch (_: IllegalArgumentException) { throw ArchiveFailure("invalid-enum", "Unknown ${T::class.simpleName} value") }
    fun colorSpace(): ColorSpace = ColorSpace(text(), enum<TransferFunction>(), enum<Gamut>())
    fun <T> list(read: () -> T): List<T> { val count = length(MAX_COLLECTION_SIZE, "collection"); return List(count) { read() } }
    fun ints(): IntArray { val count = length(MAX_COLLECTION_SIZE, "int array"); return IntArray(count) { i32() } }
    fun floats(): FloatArray { val count = length(MAX_COLLECTION_SIZE, "float array"); return FloatArray(count) { f32() } }
    fun ubytes(): UByteArray { val count = length(MAX_BINARY_SIZE, "byte array"); return UByteArray(count) { byte().toUByte() } }
    fun byteArray(): ByteArray { val count = length(MAX_BINARY_SIZE, "byte array"); requireBytes(count); return data.copyOfRange(offset, offset + count).also { offset += count } }
    fun stringFloatMap(): Map<String, Float> = list { text() to f32() }.also(::requireDistinctKeys).toMap()
    fun uniforms(): Map<String, RuntimeUniformValue> = list { text() to uniform() }.also(::requireDistinctKeys).toMap()

    private inline fun <T> nested(block: () -> T): T {
        if (depth >= MAX_NESTING) throw ArchiveFailure("nesting-limit", "Scene archive graph exceeds nesting limit")
        depth += 1
        return try { block() } finally { depth -= 1 }
    }
    private fun requireBytes(count: Int) { if (count < 0 || data.size - offset < count) throw ArchiveFailure("truncated", "Archive ended unexpectedly") }
    private fun length(max: Int, name: String): Int { val value = i32(); if (value < 0 || value > max || value > data.size - offset) throw ArchiveFailure("invalid-length", "$name length is invalid"); return value }
    private fun <T> requireDistinctKeys(values: List<Pair<String, T>>) { if (values.map { it.first }.toSet().size != values.size) throw ArchiveFailure("duplicate-key", "Archive contains duplicate map keys") }

    fun scene(): SceneSnapshot = nested {
        val extent = SceneExtent(i32().positive("extent width"), i32().positive("extent height"))
        SceneSnapshot.of(extent, colorSpace(), list(::command))
    }
    fun command(): SceneCommand = nested { when (i32()) {
        1 -> SceneCommand.Draw(draw()); 2 -> SceneCommand.Clear(colorF32()); 3 -> SceneCommand.DrawColor(color(), enum(), matrix(), clip())
        4 -> SceneCommand.SetTransform(matrix()); 5 -> SceneCommand.SetClip(clip()); 6 -> SceneCommand.BeginLayer(layer()); 7 -> SceneCommand.EndLayer
        8 -> SceneCommand.State.of(text(), list { text() to text() }.also(::requireDistinctKeys).toMap())
        9 -> SceneCommand.Annotation.of(rect(), text(), text()); 10 -> SceneCommand.Readback(ReadbackRequest.of(text(), rect()))
        else -> failTag("command")
    } }
    fun draw(): DrawNode = nested {
        DrawNode(geometry(), material(), enum(), clip(), blend(), effects(), matrix(), enum(), optional(::paint), optional(::image), optional { enum<BlendMode>() })
    }
    fun layer(): LayerDescriptor = nested { LayerDescriptor.of(optional(::text), optional(::rect), optional(::material), optional(::paint), blend(), clip(), optional(::clip), effects(), effects(), matrix()) }
    fun paint(): PaintNode = nested { PaintNode(color(), optional(::material), enum(), optional(::blender), optional(::colorFilter), optional(::maskFilter), optional(::pathEffect), optional(::imageFilter), enum(), f32(), enum(), enum(), f32(), bool()) }
    fun <T> optional(read: () -> T): T? = if (bool()) read() else null

    fun geometry(): GeometryNode = nested { when (i32()) {
        1 -> GeometryNode.Rect.of(rect()); 2 -> GeometryNode.RRect.of(rrect()); 3 -> GeometryNode.DoubleRRect.of(rrect(), rrect()); 4 -> GeometryNode.Path(path())
        5 -> GeometryNode.Points.of(enum(), list(::point))
        6 -> GeometryNode.IndexedMesh.of(enum(), list(::point), optional { list(::point) }, optional { list(::color) }, optional(::ints), optional(::rect), optional(::resourceRef), optional(::meshProgram))
        7 -> GeometryNode.ImagePatch.of(resourceRef(), rect(), rect())
        8 -> GeometryNode.ImageLattice.of(resourceRef(), ints(), ints(), optional { list(::rect) }, optional { list(::color) }, optional { list { enum<LatticeCellFlag>() } }, rect(), sampling())
        9 -> GeometryNode.Atlas.of(resourceRef(), list(::atlasEntry))
        10 -> glyphRun()
        11 -> GeometryNode.TextBlob.of(list(::glyphRun), f32(), f32(), optional(::typeface), f32(), stringFloatMap())
        12 -> GeometryNode.Picture.of(scene(), rect())
        else -> failTag("geometry")
    } }
    fun atlasEntry(): GeometryNode.AtlasEntry = GeometryNode.AtlasEntry.of(matrix(), rect(), optional(::color))
    fun glyphRun(): GeometryNode.GlyphRun = GeometryNode.GlyphRun.of(ints(), list(::point), f32(), stringFloatMap(), optional(::typeface))
    fun typeface(): TypefaceReference = TypefaceReference(TypefaceId(text()))
    fun resourceRef(): ResourceReference = ResourceReference(ResourceId(text()))
    fun sampling(): ImageSampling = when (i32()) { 1 -> ImageSampling.Nearest; 2 -> ImageSampling.Linear; 3 -> ImageSampling.Cubic(f32(), f32()); else -> failTag("sampling") }
    fun path(): PathF32 = nested { val builder = PathBuilder(enum<FillRule>()); list {
        when (i32()) {
            1 -> { val point = point(); builder.moveTo(point.x, point.y) }
            2 -> { val point = point(); builder.lineTo(point.x, point.y) }
            3 -> { val c = point(); val p = point(); builder.quadTo(c.x, c.y, p.x, p.y) }
            4 -> { val c1 = point(); val c2 = point(); val p = point(); builder.cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y) }
            5 -> {
                val radius = vector()
                val rotation = f32()
                val largeArc = bool()
                val sweep = bool()
                val endpoint = point()
                builder.arcTo(radius.x, radius.y, rotation, largeArc, sweep, endpoint.x, endpoint.y)
            }
            6 -> builder.close(); else -> failTag("path segment")
        }
    }; builder.build() }
    fun material(): MaterialNode = nested { when (i32()) {
        1 -> MaterialNode.Transparent; 2 -> MaterialNode.Solid(color()); 3 -> MaterialNode.LinearGradient.of(point(), point(), stops(), enum(), enum())
        4 -> MaterialNode.RadialGradient.of(point(), f32(), stops(), enum(), enum()); 5 -> MaterialNode.SweepGradient.of(point(), f32(), f32(), stops(), enum(), enum())
        6 -> MaterialNode.ConicalGradient.of(point(), f32(), point(), f32(), stops(), enum(), enum()); 7 -> MaterialNode.ImageSample(image(), enum(), enum(), sampling())
        8 -> MaterialNode.Blend(enum(), material(), material()); 9 -> MaterialNode.RuntimeEffect.of(descriptor(), uniforms(), list { RuntimeMaterialChild(text(), material()) })
        10 -> MaterialNode.WithLocalMatrix(material(), matrix()); 11 -> MaterialNode.WithColorFilter(material(), colorFilter()); 12 -> MaterialNode.Opacity(material(), f32())
        13 -> MaterialNode.PerlinNoise(f32(), f32(), i32(), i32(), optional(::size)); 14 -> MaterialNode.FractalNoise(f32(), f32(), i32(), i32(), optional(::size))
        15 -> MaterialNode.WithWorkingColorSpace(material(), enum()); 16 -> MaterialNode.CoordClamp(material(), rect()); else -> failTag("material")
    } }
    fun stops(): List<GradientStop> = list { GradientStop(f32(), color()) }
    fun image(): ImageResourceSnapshot = nested { when (i32()) { 1 -> { val meta = imageMetadata(); ImageResourceSnapshot.fromPixels(meta.sourceId, meta.width, meta.height, meta.format, meta.alpha, meta.colorSpace, i32(), byteArray()) }; 2 -> { val meta = imageMetadata(); ExternalImageReference.of(meta.sourceId, meta.width, meta.height, meta.format, meta.alpha, meta.colorSpace) }; else -> failTag("image") } }
    private data class ImageMeta(val sourceId: String, val width: Int, val height: Int, val format: ImagePixelFormat, val alpha: ImageAlphaType, val colorSpace: ColorSpace)
    private fun imageMetadata(): ImageMeta = ImageMeta(text(), i32().nonNegative("image width"), i32().nonNegative("image height"), enum(), enum(), colorSpace())
    fun descriptor(): RuntimeEffectDescriptor = nested { RuntimeEffectDescriptor.of(RuntimeEffectId(text()), enum(), RuntimeUniformLayout.of(list(::uniformSlot)), list { RuntimeChildSlot(text(), enum()) }, optional(::vertexLayout), optional(::module)) }
    fun uniformSlot(): RuntimeUniformSlot = RuntimeUniformSlot(text(), i32().nonNegative("uniform binding"), enum(), i32().nonNegative("uniform size"))
    fun vertexLayout(): RuntimeVertexLayout {
        val stride = i32().nonNegative("vertex stride")
        val stepMode = enum<RuntimeVertexStepMode>()
        val attributes = list { RuntimeVertexAttribute(enum(), i32().nonNegative("vertex offset"), i32().nonNegative("shader location")) }
        return RuntimeVertexLayout.of(stride, attributes, stepMode)
    }
    fun module(): ShaderModuleDescriptor = ShaderModuleDescriptor.of(text(), text(), list(::uniformSlot), list { RuntimeTextureSlot(text(), i32().nonNegative("texture binding")) })
    fun uniform(): RuntimeUniformValue = when (i32()) { 1 -> RuntimeUniformValue.F1(f32()); 2 -> RuntimeUniformValue.F2(f32(), f32()); 3 -> RuntimeUniformValue.F3(f32(), f32(), f32()); 4 -> RuntimeUniformValue.F4(f32(), f32(), f32(), f32()); 5 -> RuntimeUniformValue.I1(i32()); 6 -> RuntimeUniformValue.M3(matrix()); 7 -> RuntimeUniformValue.M4(floats()); else -> failTag("uniform") }
    fun meshProgram(): MeshProgramNode = nested { MeshProgramNode.of(descriptor(), uniforms(), list { val name = text(); when (i32()) { 1 -> MeshProgramChild.Shader(name, material()); 2 -> MeshProgramChild.ColorFilter(name, colorFilter()); 3 -> MeshProgramChild.Blender(name, blender()); else -> failTag("mesh child") } }) }
    fun blend(): BlendNode = nested { when (i32()) { 1 -> BlendNode.SrcOver; 2 -> BlendNode.Mode(enum()); 3 -> BlendNode.Custom(blender()); 4 -> BlendNode.Paint(enum(), optional(::blender)); else -> failTag("blend") } }
    fun blender(): BlenderNode = when (i32()) { 1 -> BlenderNode.Mode(enum()); 2 -> BlenderNode.Arithmetic(f32(), f32(), f32(), f32()); else -> failTag("blender") }
    fun clip(): ClipStackNode = nested { when (i32()) { 1 -> ClipStackNode.Empty; 2 -> ClipStackNode.DeviceRect.of(rect(), bool()); 3 -> ClipStackNode.Operations.of(list { ClipEntry(geometry(), enum(), bool(), bool(), text()) }); else -> failTag("clip") } }
    fun effects(): EffectStack = when (i32()) { 1 -> EffectStack.Empty; 2 -> EffectStack.of(list(::effect)); else -> failTag("effect stack") }
    fun effect(): EffectNode = when (i32()) { 1 -> colorFilter(); 2 -> maskFilter(); 3 -> pathEffect(); 4 -> imageFilter(); else -> failTag("effect") }
    fun colorFilter(): ColorFilterNode = nested { when (i32()) {
        1 -> ColorFilterNode.Matrix(ImmutableFloats.copyOf(floats())); 2 -> ColorFilterNode.Blend(color(), enum()); 3 -> ColorFilterNode.Compose(colorFilter(), colorFilter()); 4 -> ColorFilterNode.Table(ImmutableUBytes.copyOf(ubytes())); 5 -> ColorFilterNode.Lighting(color(), color()); 6 -> ColorFilterNode.SRGBToLinear; 7 -> ColorFilterNode.LinearToSRGB; 8 -> ColorFilterNode.HSLAMatrix(ImmutableFloats.copyOf(floats())); 9 -> ColorFilterNode.Lerp(f32(), colorFilter(), colorFilter()); 10 -> ColorFilterNode.HighContrast; 11 -> ColorFilterNode.Luma; 12 -> ColorFilterNode.Overdraw; 13 -> ColorFilterNode.RuntimeEffect.of(descriptor(), uniforms(), list { RuntimeColorFilterChild(text(), colorFilter()) }); else -> failTag("color filter")
    } }
    fun maskFilter(): MaskFilterNode = nested { when (i32()) { 1 -> MaskFilterNode.Blur(enum(), f32()); 2 -> MaskFilterNode.Shader(material()); 3 -> MaskFilterNode.Table(ImmutableUBytes.copyOf(ubytes())); else -> failTag("mask filter") } }
    fun pathEffect(): PathEffectNode = nested { when (i32()) { 1 -> PathEffectNode.Dash(ImmutableFloats.copyOf(floats()), f32()); 2 -> PathEffectNode.Corner(f32()); 3 -> PathEffectNode.Discrete(f32(), f32()); 4 -> PathEffectNode.Path1D(path(), f32(), f32(), enum()); 5 -> PathEffectNode.Path2D(matrix(), path()); 6 -> PathEffectNode.Trim(f32(), f32()); else -> failTag("path effect") } }
    fun imageFilter(): ImageFilterNode = nested { when (i32()) {
        1 -> ImageFilterNode.Crop.of(rect(), enum(), optional(::imageFilter)); 2 -> ImageFilterNode.Blur(f32(), f32(), enum(), optional(::imageFilter)); 3 -> ImageFilterNode.DropShadow(f32(), f32(), f32(), f32(), color(), optional(::imageFilter)); 4 -> ImageFilterNode.ColorFilter(colorFilter(), optional(::imageFilter)); 5 -> ImageFilterNode.Compose(imageFilter(), imageFilter()); 6 -> ImageFilterNode.Blend(enum(), imageFilter(), imageFilter()); 7 -> ImageFilterNode.Dilate(f32(), f32(), optional(::imageFilter)); 8 -> ImageFilterNode.Erode(f32(), f32(), optional(::imageFilter)); 9 -> ImageFilterNode.DistantLitDiffuse(f32(), f32(), color(), f32(), f32(), optional(::imageFilter)); 10 -> ImageFilterNode.PointLitDiffuse(point(), color(), f32(), f32(), optional(::imageFilter)); 11 -> ImageFilterNode.SpotLitDiffuse(point(), point(), f32(), f32(), color(), f32(), f32(), optional(::imageFilter)); 12 -> ImageFilterNode.DistantLitSpecular(f32(), f32(), color(), f32(), f32(), f32(), optional(::imageFilter)); 13 -> ImageFilterNode.PointLitSpecular(point(), color(), f32(), f32(), f32(), optional(::imageFilter)); 14 -> ImageFilterNode.SpotLitSpecular(point(), point(), f32(), f32(), color(), f32(), f32(), f32(), optional(::imageFilter)); 15 -> ImageFilterNode.Offset(f32(), f32(), optional(::imageFilter)); 16 -> ImageFilterNode.Tile.of(rect(), rect(), optional(::imageFilter)); 17 -> ImageFilterNode.Merge.of(list(::imageFilter)); 18 -> ImageFilterNode.DisplacementMap(enum(), enum(), f32(), imageFilter(), optional(::imageFilter)); 19 -> ImageFilterNode.Picture.of(scene(), rect(), optional(::rect)); 20 -> ImageFilterNode.Magnifier.of(rect(), f32(), f32(), optional(::imageFilter)); 21 -> ImageFilterNode.MatrixConvolution.of(size(), ImmutableFloats.copyOf(floats()), f32(), f32(), vector(), enum(), bool(), optional(::imageFilter)); 22 -> ImageFilterNode.RuntimeEffect.of(descriptor(), uniforms(), optional(::text), list { RuntimeImageFilterChild(text(), optional(::imageFilter)) }); else -> failTag("image filter")
    } }
    private fun Int.positive(name: String): Int = if (this > 0) this else throw ArchiveFailure("invalid-value", "$name must be positive")
    private fun Int.nonNegative(name: String): Int = if (this >= 0) this else throw ArchiveFailure("invalid-value", "$name must be non-negative")
    private fun <T> failTag(type: String): T = throw ArchiveFailure("unknown-$type", "Archive contains an unknown $type tag")
}

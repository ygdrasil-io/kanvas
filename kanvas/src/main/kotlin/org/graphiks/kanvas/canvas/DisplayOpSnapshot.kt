@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.toCompatibilityPath
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.ShaderChild
import org.graphiks.kanvas.paint.ColorFilterChild
import org.graphiks.kanvas.paint.BlenderChild
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Vertices
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.color.ColorMatrixF32
import java.util.Collections
import java.util.IdentityHashMap

/** Returns a recursive defensive copy of every mutable geometric value in this operation. */
internal fun DisplayOp.snapshotGeometry(): DisplayOp = GeometrySnapshotContext().snapshot(this)

/** Returns a defensive copy that preserves aliases between operations in this snapshot. */
internal fun List<DisplayOp>.snapshotGeometry(): List<DisplayOp> {
    val context = GeometrySnapshotContext()
    return map(context::snapshot)
}

/** Snapshot state that must be shared for one append or complete operation copy. */
internal class GeometrySnapshotContext {
    private val textBlobs = IdentityHashMap<TextBlob, TextBlob>()
    private val images = IdentityHashMap<Image, Image>()
    private val shaders = IdentityHashMap<Shader, Shader>()
    private val colorFilters = IdentityHashMap<ColorFilter, ColorFilter>()
    private val maskFilters = IdentityHashMap<MaskFilter, MaskFilter>()
    private val imageFilters = IdentityHashMap<ImageFilter, ImageFilter>()
    private val activeEffectSnapshots = IdentityHashMap<Any, Unit>()
    private var effectSnapshotNodes = 0

    fun snapshot(operation: DisplayOp): DisplayOp {
        check(activeEffectSnapshots.isEmpty()) { "Display operation snapshot leaked an active effect traversal" }
        effectSnapshotNodes = 0
        images.clear()
        shaders.clear()
        colorFilters.clear()
        maskFilters.clear()
        imageFilters.clear()
        return operation.snapshotGeometry(this)
    }

    fun snapshot(blob: TextBlob): TextBlob {
        val previous = textBlobs[blob]
        if (previous != null && blob == previous) return previous

        return blob.snapshotGeometry().also {
            textBlobs[blob] = it
        }
    }

    fun snapshot(image: Image): Image = images[image] ?: image.copy(
        pixels = image.pixels?.copyOf(),
    ).also { images[image] = it }

    fun snapshot(filter: MaskFilter): MaskFilter = maskFilters[filter] ?: when (filter) {
        is MaskFilter.Shader -> filter.copy(shader = snapshot(filter.shader))
        is MaskFilter.Blur -> filter
        is MaskFilter.Table -> filter.copy(table = filter.table.copyOf())
    }.also { maskFilters[filter] = it }

    fun snapshot(shader: Shader): Shader {
        shaders[shader]?.let { return it }
        enterEffectSnapshot(shader)
        return try {
            when (shader) {
            is Shader.RuntimeEffect -> {
                val children = linkedMapOf<String, Shader>()
                Shader.RuntimeEffect(shader.effect, shader.uniforms, Collections.unmodifiableMap(children)).also { copy ->
                    shaders[shader] = copy
                    shader.children.forEach { (name, child) -> children[name] = snapshot(child) }
                }
            }
            is Shader.Blend -> shader.copy(dst = snapshot(shader.dst), src = snapshot(shader.src))
            is Shader.WithLocalMatrix -> shader.copy(shader = snapshot(shader.shader))
            is Shader.WithColorFilter -> shader.copy(shader = snapshot(shader.shader), filter = snapshot(shader.filter))
            is Shader.CoordClamp -> shader.copy(shader = snapshot(shader.shader), subset = shader.subset.snapshotGeometry())
            is Shader.WithWorkingColorSpace -> shader.copy(shader = snapshot(shader.shader))
            is Shader.Image -> shader.copy(image = snapshot(shader.image))
            is Shader.LinearGradient -> shader.copy(stops = shader.stops.toList())
            is Shader.RadialGradient -> shader.copy(stops = shader.stops.toList())
            is Shader.SweepGradient -> shader.copy(stops = shader.stops.toList())
            is Shader.ConicalGradient -> shader.copy(stops = shader.stops.toList())
            is Shader.SolidColor,
            is Shader.PerlinNoise,
            is Shader.FractalNoise,
            -> shader
            }.also { shaders[shader] = it }
        } finally {
            leaveEffectSnapshot(shader)
        }
    }

    fun snapshot(filter: ColorFilter): ColorFilter {
        colorFilters[filter]?.let { return it }
        enterEffectSnapshot(filter)
        return try {
            when (filter) {
            is ColorFilter.RuntimeEffect -> {
                val children = linkedMapOf<String, ColorFilter>()
                ColorFilter.RuntimeEffect(filter.effect, filter.uniforms, Collections.unmodifiableMap(children)).also { copy ->
                    colorFilters[filter] = copy
                    filter.children.forEach { (name, child) -> children[name] = snapshot(child) }
                }
            }
            is ColorFilter.Compose -> filter.copy(outer = snapshot(filter.outer), inner = snapshot(filter.inner))
            is ColorFilter.Lerp -> filter.copy(dst = snapshot(filter.dst), src = snapshot(filter.src))
            is ColorFilter.Matrix -> filter.copy(matrix = ColorMatrixF32.of(filter.matrix.toFloatArray()))
            is ColorFilter.Table -> filter.copy(table = filter.table.copyOf())
            is ColorFilter.HSLAMatrix -> filter.copy(values = filter.values.copyOf())
            is ColorFilter.Blend,
            is ColorFilter.Lighting,
            ColorFilter.SRGBToLinear,
            ColorFilter.LinearToSRGB,
            ColorFilter.HighContrast,
            ColorFilter.Luma,
            ColorFilter.Overdraw,
            -> filter
            }.also { colorFilters[filter] = it }
        } finally {
            leaveEffectSnapshot(filter)
        }
    }

    fun snapshot(filter: ImageFilter): ImageFilter {
        imageFilters[filter]?.let { return it }
        enterEffectSnapshot(filter)
        return try {
            when (filter) {
            is ImageFilter.Merge -> {
                val inputs = mutableListOf<ImageFilter>()
                ImageFilter.Merge(Collections.unmodifiableList(inputs)).also { copy ->
                    imageFilters[filter] = copy
                    filter.inputs.forEach { input -> inputs += snapshot(input) }
                }
            }
            is ImageFilter.RuntimeEffect -> {
                val children = linkedMapOf<String, ImageFilter?>()
                ImageFilter.RuntimeEffect(filter.effect, filter.uniforms, filter.childShaderName, Collections.unmodifiableMap(children)).also { copy ->
                    imageFilters[filter] = copy
                    filter.childImageFilters.forEach { (name, child) -> children[name] = child?.let(::snapshot) }
                }
            }
            is ImageFilter.Crop -> filter.copy(crop = filter.crop.snapshotGeometry(), input = filter.input?.let(::snapshot))
            is ImageFilter.Blur -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.DropShadow -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.ColorFilter -> filter.copy(filter = snapshot(filter.filter), input = filter.input?.let(::snapshot))
            is ImageFilter.Compose -> filter.copy(outer = snapshot(filter.outer), inner = snapshot(filter.inner))
            is ImageFilter.Blend -> filter.copy(background = snapshot(filter.background), foreground = snapshot(filter.foreground))
            is ImageFilter.Dilate -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.Erode -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.DistantLitDiffuse -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.PointLitDiffuse -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.SpotLitDiffuse -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.DistantLitSpecular -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.PointLitSpecular -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.SpotLitSpecular -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.Offset -> filter.copy(input = filter.input?.let(::snapshot))
            is ImageFilter.Tile -> filter.copy(src = filter.src.snapshotGeometry(), dst = filter.dst.snapshotGeometry(), input = filter.input?.let(::snapshot))
            is ImageFilter.DisplacementMap -> filter.copy(displacement = snapshot(filter.displacement), input = filter.input?.let(::snapshot))
            is ImageFilter.Picture -> filter.copy(src = filter.src?.snapshotGeometry())
            is ImageFilter.Magnifier -> filter.copy(src = filter.src.snapshotGeometry(), input = filter.input?.let(::snapshot))
            is ImageFilter.MatrixConvolution -> filter.copy(kernel = filter.kernel.copyOf(), input = filter.input?.let(::snapshot))
            }.also { imageFilters[filter] = it }
        } finally {
            leaveEffectSnapshot(filter)
        }
    }

    private fun enterEffectSnapshot(value: Any) {
        check(activeEffectSnapshots.size < MAX_EFFECT_SNAPSHOT_DEPTH) {
            "Display operation effect graph exceeds immutable snapshot depth $MAX_EFFECT_SNAPSHOT_DEPTH"
        }
        check(effectSnapshotNodes < MAX_EFFECT_SNAPSHOT_NODES) {
            "Display operation effect graph exceeds immutable snapshot node budget $MAX_EFFECT_SNAPSHOT_NODES"
        }
        activeEffectSnapshots[value] = Unit
        effectSnapshotNodes += 1
    }

    private fun leaveEffectSnapshot(value: Any) {
        activeEffectSnapshots.remove(value)
    }

    private companion object {
        const val MAX_EFFECT_SNAPSHOT_DEPTH = 64
        const val MAX_EFFECT_SNAPSHOT_NODES = 4_096
    }
}

internal fun DisplayOp.snapshotGeometry(context: GeometrySnapshotContext): DisplayOp = when (this) {
    is DisplayOp.DrawRect -> copy(rect = rect.snapshotGeometry(), paint = paint.snapshotGeometry(context), clip = clip.snapshotGeometry())
    is DisplayOp.DrawRRect -> copy(rrect = rrect.snapshotGeometry(), paint = paint.snapshotGeometry(context), clip = clip.snapshotGeometry())
    is DisplayOp.DrawPath -> copyPreservingSourceOperation(
        path = path.snapshotGeometry(),
        paint = paint.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawImage -> copy(
        src = src.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        image = context.snapshot(image),
        paint = paint?.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawText -> copy(
        blob = context.snapshot(blob),
        paint = paint.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.SetTransform -> this
    is DisplayOp.SetClip -> copy(clip = clip.snapshotGeometry())
    is DisplayOp.BeginLayer -> copy(rec = rec.snapshotGeometry(context))
    DisplayOp.EndLayer -> this
    is DisplayOp.DrawColor -> copy(clip = clip.snapshotGeometry())
    is DisplayOp.Clear -> this
    is DisplayOp.DrawPoint -> copy(paint = paint.snapshotGeometry(context), clip = clip.snapshotGeometry())
    is DisplayOp.DrawPoints -> copy(points = points.toList(), paint = paint.snapshotGeometry(context), clip = clip.snapshotGeometry())
    is DisplayOp.DrawDRRect -> copy(
        outer = outer.snapshotGeometry(),
        inner = inner.snapshotGeometry(),
        paint = paint.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawImageNine -> copy(
        center = center.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        image = context.snapshot(image),
        paint = paint?.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawImageLattice -> copy(
        lattice = lattice.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        image = context.snapshot(image),
        paint = paint?.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawPicture -> copy(
        picture = picture,
        paint = paint?.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawVertices -> copy(
        vertices = vertices.snapshotGeometry(),
        paint = paint.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawMesh -> copy(
        mesh = mesh.snapshotGeometry(context),
        paint = paint.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawAtlas -> copy(
        transforms = transforms.toList(),
        texRects = texRects.map(RectF32::snapshotGeometry),
        colors = colors?.toList(),
        atlas = context.snapshot(atlas),
        paint = paint?.snapshotGeometry(context),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.Annotation -> copy(rect = rect.snapshotGeometry())
    is DisplayOp.FlushAndSnapshot -> copy(bounds = bounds.snapshotGeometry())
}

internal fun ClipStack.snapshotGeometry(): ClipStack = when (this) {
    ClipStack.WideOpen -> this
    is ClipStack.DeviceRect -> copy(rect = rect.snapshotGeometry())
    is ClipStack.Complex -> copy(ops = ops.map(ClipStackOp::snapshotGeometry))
}

private fun ClipStackOp.snapshotGeometry(): ClipStackOp = when (this) {
    is ClipStackOp.RectOp -> copy(rect = rect.snapshotGeometry())
    is ClipStackOp.RRectOp -> copy(rrect = rrect.snapshotGeometry())
    is ClipStackOp.PathOp -> copy(path = path.snapshotGeometry())
}

internal fun Paint.snapshotGeometry(context: GeometrySnapshotContext): Paint = copy(
    shader = shader?.let(context::snapshot),
    colorFilter = colorFilter?.let(context::snapshot),
    maskFilter = maskFilter?.let(context::snapshot),
    pathEffect = pathEffect?.snapshotGeometry(),
    imageFilter = imageFilter?.let(context::snapshot),
)

private fun PathEffect.snapshotGeometry(): PathEffect = when (this) {
    is PathEffect.Dash -> copy(intervals = intervals.copyOf())
    is PathEffect.Path1D -> copy(path = path.snapshotGeometry())
    is PathEffect.Path2D -> copy(path = path.snapshotGeometry())
    is PathEffect.Corner,
    is PathEffect.Discrete,
    is PathEffect.Trim,
    -> this
}

private fun SaveLayerRec.snapshotGeometry(context: GeometrySnapshotContext): SaveLayerRec = copy(
    bounds = bounds?.snapshotGeometry(),
    paint = paint?.snapshotGeometry(context),
    backdrop = backdrop?.let(context::snapshot),
    compositeClip = compositeClip?.snapshotGeometry(),
)

private fun Lattice.snapshotGeometry(): Lattice = copy(
    xDivs = xDivs.toList(),
    yDivs = yDivs.toList(),
    rects = rects?.map(RectF32::snapshotGeometry),
    colors = colors?.toList(),
    flags = flags?.toList(),
)

private fun Vertices.snapshotGeometry(): Vertices = copy(
    positions = positions.toList(),
    texCoords = texCoords?.toList(),
    colors = colors?.toList(),
    indices = indices?.toList(),
)

private fun Mesh.snapshotGeometry(context: GeometrySnapshotContext): Mesh = copy(
    vertices = vertices.snapshotGeometry(),
    program = program?.snapshotGeometry(context),
    bounds = bounds.snapshotGeometry(),
)

private fun MeshProgram.snapshotGeometry(context: GeometrySnapshotContext): MeshProgram = copy(children = children.snapshotGeometry(context))

private fun MeshChildren.snapshotGeometry(context: GeometrySnapshotContext): MeshChildren = copy(
    entries = entries.map { entry ->
        entry.copy(
            child = when (val child = entry.child) {
                is ShaderChild -> child.copy(shader = context.snapshot(child.shader))
                is ColorFilterChild -> child.copy(filter = context.snapshot(child.filter))
                is BlenderChild -> child
            },
        )
    },
)

private fun TextBlob.snapshotGeometry(): TextBlob = TextBlob(
    glyphRuns = glyphRuns.map { run ->
        KanvasGlyphRun(
            glyphs = run.glyphs.toList(),
            positions = run.positions.toList(),
            fontSize = run.fontSize,
        )
    },
    typeface = typeface,
    fontSize = fontSize,
    variationCoordinates = variationCoordinates.toMap(),
)

private fun org.graphiks.kanvas.geometry.Path.snapshotGeometry() =
    toPathF32().toCompatibilityPath()

private fun RectF32.snapshotGeometry(): RectF32 = RectF32(left, top, right, bottom)

private fun RRectF32.snapshotGeometry(): RRectF32 = RRectF32(
    rect = rect.snapshotGeometry(),
    topLeft = topLeft,
    topRight = topRight,
    bottomRight = bottomRight,
    bottomLeft = bottomLeft,
)

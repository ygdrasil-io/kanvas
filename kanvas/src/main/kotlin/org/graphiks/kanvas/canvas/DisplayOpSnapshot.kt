package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.toCompatibilityPath
import org.graphiks.kanvas.geometry.toPathF32
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
import java.util.IdentityHashMap

/** Returns a recursive defensive copy of every mutable geometric value in this operation. */
internal fun DisplayOp.snapshotGeometry(): DisplayOp = snapshotGeometry(GeometrySnapshotContext())

/** Returns a defensive copy that preserves aliases between operations in this snapshot. */
internal fun List<DisplayOp>.snapshotGeometry(): List<DisplayOp> {
    val context = GeometrySnapshotContext()
    return map { it.snapshotGeometry(context) }
}

/** Snapshot state that must be shared for one append or complete operation copy. */
internal class GeometrySnapshotContext {
    private val textBlobs = IdentityHashMap<TextBlob, TextBlob>()

    fun snapshot(blob: TextBlob): TextBlob {
        val previous = textBlobs[blob]
        if (previous != null && blob == previous) return previous

        return blob.snapshotGeometry().also {
            textBlobs[blob] = it
        }
    }
}

internal fun DisplayOp.snapshotGeometry(context: GeometrySnapshotContext): DisplayOp = when (this) {
    is DisplayOp.DrawRect -> copy(rect = rect.snapshotGeometry(), paint = paint.snapshotGeometry(), clip = clip.snapshotGeometry())
    is DisplayOp.DrawRRect -> copy(rrect = rrect.snapshotGeometry(), paint = paint.snapshotGeometry(), clip = clip.snapshotGeometry())
    is DisplayOp.DrawPath -> copyPreservingSourceOperation(
        path = path.snapshotGeometry(),
        paint = paint.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawImage -> copy(
        src = src.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        paint = paint?.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawText -> copy(
        blob = context.snapshot(blob),
        paint = paint.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.SetTransform -> this
    is DisplayOp.SetClip -> copy(clip = clip.snapshotGeometry())
    is DisplayOp.BeginLayer -> copy(rec = rec.snapshotGeometry())
    DisplayOp.EndLayer -> this
    is DisplayOp.DrawColor -> copy(clip = clip.snapshotGeometry())
    is DisplayOp.Clear -> this
    is DisplayOp.DrawPoint -> copy(paint = paint.snapshotGeometry(), clip = clip.snapshotGeometry())
    is DisplayOp.DrawPoints -> copy(points = points.toList(), paint = paint.snapshotGeometry(), clip = clip.snapshotGeometry())
    is DisplayOp.DrawDRRect -> copy(
        outer = outer.snapshotGeometry(),
        inner = inner.snapshotGeometry(),
        paint = paint.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawImageNine -> copy(
        center = center.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        paint = paint?.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawImageLattice -> copy(
        lattice = lattice.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        paint = paint?.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawPicture -> copy(
        picture = picture,
        paint = paint?.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawVertices -> copy(
        vertices = vertices.snapshotGeometry(),
        paint = paint.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawMesh -> copy(
        mesh = mesh.snapshotGeometry(),
        paint = paint.snapshotGeometry(),
        clip = clip.snapshotGeometry(),
    )
    is DisplayOp.DrawAtlas -> copy(
        transforms = transforms.toList(),
        texRects = texRects.map(RectF32::snapshotGeometry),
        colors = colors?.toList(),
        paint = paint?.snapshotGeometry(),
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

internal fun Paint.snapshotGeometry(): Paint = copy(
    shader = shader?.snapshotGeometry(),
    maskFilter = maskFilter.snapshotGeometry(),
    pathEffect = pathEffect?.snapshotGeometry(),
    imageFilter = imageFilter?.snapshotGeometry(),
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

private fun MaskFilter?.snapshotGeometry(): MaskFilter? = when (this) {
    null -> null
    is MaskFilter.Shader -> copy(shader = shader.snapshotGeometry())
    is MaskFilter.Blur,
    is MaskFilter.Table,
    -> this
}

private fun Shader.snapshotGeometry(): Shader = when (this) {
    is Shader.LinearGradient -> copy(stops = stops.toList())
    is Shader.RadialGradient -> copy(stops = stops.toList())
    is Shader.SweepGradient -> copy(stops = stops.toList())
    is Shader.ConicalGradient -> copy(stops = stops.toList())
    is Shader.Blend -> copy(dst = dst.snapshotGeometry(), src = src.snapshotGeometry())
    is Shader.RuntimeEffect -> copy(children = children.mapValues { (_, child) -> child.snapshotGeometry() })
    is Shader.WithLocalMatrix -> copy(shader = shader.snapshotGeometry())
    is Shader.WithColorFilter -> copy(shader = shader.snapshotGeometry())
    is Shader.CoordClamp -> copy(shader = shader.snapshotGeometry(), subset = subset.snapshotGeometry())
    is Shader.WithWorkingColorSpace -> copy(shader = shader.snapshotGeometry())
    is Shader.SolidColor,
    is Shader.Image,
    is Shader.PerlinNoise,
    is Shader.FractalNoise,
    -> this
}

private fun ImageFilter.snapshotGeometry(): ImageFilter = when (this) {
    is ImageFilter.Crop -> copy(crop = crop.snapshotGeometry(), input = input?.snapshotGeometry())
    is ImageFilter.Blur -> copy(input = input?.snapshotGeometry())
    is ImageFilter.DropShadow -> copy(input = input?.snapshotGeometry())
    is ImageFilter.ColorFilter -> copy(input = input?.snapshotGeometry())
    is ImageFilter.Compose -> copy(outer = outer.snapshotGeometry(), inner = inner.snapshotGeometry())
    is ImageFilter.Blend -> copy(background = background.snapshotGeometry(), foreground = foreground.snapshotGeometry())
    is ImageFilter.Dilate -> copy(input = input?.snapshotGeometry())
    is ImageFilter.Erode -> copy(input = input?.snapshotGeometry())
    is ImageFilter.DistantLitDiffuse -> copy(input = input?.snapshotGeometry())
    is ImageFilter.PointLitDiffuse -> copy(input = input?.snapshotGeometry())
    is ImageFilter.SpotLitDiffuse -> copy(input = input?.snapshotGeometry())
    is ImageFilter.DistantLitSpecular -> copy(input = input?.snapshotGeometry())
    is ImageFilter.PointLitSpecular -> copy(input = input?.snapshotGeometry())
    is ImageFilter.SpotLitSpecular -> copy(input = input?.snapshotGeometry())
    is ImageFilter.Offset -> copy(input = input?.snapshotGeometry())
    is ImageFilter.Tile -> copy(
        src = src.snapshotGeometry(),
        dst = dst.snapshotGeometry(),
        input = input?.snapshotGeometry(),
    )
    is ImageFilter.Merge -> copy(inputs = inputs.map(ImageFilter::snapshotGeometry))
    is ImageFilter.DisplacementMap -> copy(
        displacement = displacement.snapshotGeometry(),
        input = input?.snapshotGeometry(),
    )
    is ImageFilter.Picture -> copy(picture = picture, src = src?.snapshotGeometry())
    is ImageFilter.Magnifier -> copy(src = src.snapshotGeometry(), input = input?.snapshotGeometry())
    is ImageFilter.MatrixConvolution -> copy(kernel = kernel.copyOf(), input = input?.snapshotGeometry())
    is ImageFilter.RuntimeEffect -> copy(
        childImageFilters = childImageFilters.mapValues { (_, child) -> child?.snapshotGeometry() },
    )
}

private fun SaveLayerRec.snapshotGeometry(): SaveLayerRec = copy(
    bounds = bounds?.snapshotGeometry(),
    paint = paint?.snapshotGeometry(),
    backdrop = backdrop?.snapshotGeometry(),
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

private fun Mesh.snapshotGeometry(): Mesh = copy(
    vertices = vertices.snapshotGeometry(),
    program = program?.snapshotGeometry(),
    bounds = bounds.snapshotGeometry(),
)

private fun MeshProgram.snapshotGeometry(): MeshProgram = copy(children = children.snapshotGeometry())

private fun MeshChildren.snapshotGeometry(): MeshChildren = copy(
    entries = entries.map { entry ->
        entry.copy(
            child = when (val child = entry.child) {
                is ShaderChild -> child.copy(shader = child.shader.snapshotGeometry())
                is ColorFilterChild,
                is BlenderChild,
                -> child
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

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
import java.util.ArrayDeque
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
    private val shaderRuntimeChildren = IdentityHashMap<Shader.RuntimeEffect, MutableMap<String, Shader>>()
    private val colorRuntimeChildren = IdentityHashMap<ColorFilter.RuntimeEffect, MutableMap<String, ColorFilter>>()
    private val imageRuntimeChildren = IdentityHashMap<ImageFilter.RuntimeEffect, MutableMap<String, ImageFilter?>>()
    private val mergeInputs = IdentityHashMap<ImageFilter.Merge, MutableList<ImageFilter>>()

    fun snapshot(operation: DisplayOp): DisplayOp {
        images.clear()
        shaders.clear()
        colorFilters.clear()
        maskFilters.clear()
        imageFilters.clear()
        shaderRuntimeChildren.clear()
        colorRuntimeChildren.clear()
        imageRuntimeChildren.clear()
        mergeInputs.clear()
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
        data class ShaderFrame(val value: Shader, val leaving: Boolean)
        val pending = ArrayDeque<ShaderFrame>()
        pending += ShaderFrame(shader, false)
        while (pending.isNotEmpty()) {
            val frame = pending.removeLast()
            val value = frame.value
            if (frame.leaving) {
                when (value) {
                    is Shader.RuntimeEffect -> shaderRuntimeChildren.getValue(value).apply {
                        value.children.forEach { (name, child) -> put(name, shaders.getValue(child)) }
                    }
                    is Shader.Blend -> shaders[value] = value.copy(dst = shaders.getValue(value.dst), src = shaders.getValue(value.src))
                    is Shader.WithLocalMatrix -> shaders[value] = value.copy(shader = shaders.getValue(value.shader))
                    is Shader.WithColorFilter -> shaders[value] = value.copy(shader = shaders.getValue(value.shader), filter = snapshot(value.filter))
                    is Shader.CoordClamp -> shaders[value] = value.copy(shader = shaders.getValue(value.shader), subset = value.subset.snapshotGeometry())
                    is Shader.WithWorkingColorSpace -> shaders[value] = value.copy(shader = shaders.getValue(value.shader))
                    is Shader.Image -> shaders[value] = value.copy(image = snapshot(value.image))
                    is Shader.LinearGradient -> shaders[value] = value.copy(stops = value.stops.toList())
                    is Shader.RadialGradient -> shaders[value] = value.copy(stops = value.stops.toList())
                    is Shader.SweepGradient -> shaders[value] = value.copy(stops = value.stops.toList())
                    is Shader.ConicalGradient -> shaders[value] = value.copy(stops = value.stops.toList())
                    is Shader.SolidColor,
                    is Shader.PerlinNoise,
                    is Shader.FractalNoise,
                    -> shaders[value] = value
                }
                continue
            }
            if (shaders.containsKey(value)) continue
            when (value) {
                is Shader.RuntimeEffect -> {
                    val children = linkedMapOf<String, Shader>()
                    shaderRuntimeChildren[value] = children
                    shaders[value] = Shader.RuntimeEffect(value.effect, value.uniforms, Collections.unmodifiableMap(children))
                    pending += ShaderFrame(value, true)
                    value.children.values.reversed().forEach { pending += ShaderFrame(it, false) }
                }
                else -> {
                    pending += ShaderFrame(value, true)
                    shaderChildren(value).asReversed().forEach { pending += ShaderFrame(it, false) }
                }
            }
        }
        return shaders.getValue(shader)
    }

    fun snapshot(filter: ColorFilter): ColorFilter {
        colorFilters[filter]?.let { return it }
        data class ColorFilterFrame(val value: ColorFilter, val leaving: Boolean)
        val pending = ArrayDeque<ColorFilterFrame>()
        pending += ColorFilterFrame(filter, false)
        while (pending.isNotEmpty()) {
            val frame = pending.removeLast()
            val value = frame.value
            if (frame.leaving) {
                when (value) {
                    is ColorFilter.RuntimeEffect -> colorRuntimeChildren.getValue(value).apply {
                        value.children.forEach { (name, child) -> put(name, colorFilters.getValue(child)) }
                    }
                    is ColorFilter.Compose -> colorFilters[value] = value.copy(outer = colorFilters.getValue(value.outer), inner = colorFilters.getValue(value.inner))
                    is ColorFilter.Lerp -> colorFilters[value] = value.copy(dst = colorFilters.getValue(value.dst), src = colorFilters.getValue(value.src))
                    is ColorFilter.Matrix -> colorFilters[value] = value.copy(matrix = ColorMatrixF32.of(value.matrix.toFloatArray()))
                    is ColorFilter.Table -> colorFilters[value] = value.copy(table = value.table.copyOf())
                    is ColorFilter.HSLAMatrix -> colorFilters[value] = value.copy(values = value.values.copyOf())
                    is ColorFilter.Blend,
                    is ColorFilter.Lighting,
                    ColorFilter.SRGBToLinear,
                    ColorFilter.LinearToSRGB,
                    ColorFilter.HighContrast,
                    ColorFilter.Luma,
                    ColorFilter.Overdraw,
                    -> colorFilters[value] = value
                }
                continue
            }
            if (colorFilters.containsKey(value)) continue
            when (value) {
                is ColorFilter.RuntimeEffect -> {
                    val children = linkedMapOf<String, ColorFilter>()
                    colorRuntimeChildren[value] = children
                    colorFilters[value] = ColorFilter.RuntimeEffect(value.effect, value.uniforms, Collections.unmodifiableMap(children))
                    pending += ColorFilterFrame(value, true)
                    value.children.values.reversed().forEach { pending += ColorFilterFrame(it, false) }
                }
                else -> {
                    pending += ColorFilterFrame(value, true)
                    colorFilterChildren(value).asReversed().forEach { pending += ColorFilterFrame(it, false) }
                }
            }
        }
        return colorFilters.getValue(filter)
    }

    fun snapshot(filter: ImageFilter): ImageFilter {
        imageFilters[filter]?.let { return it }
        data class ImageFilterFrame(val value: ImageFilter, val leaving: Boolean)
        val pending = ArrayDeque<ImageFilterFrame>()
        pending += ImageFilterFrame(filter, false)
        while (pending.isNotEmpty()) {
            val frame = pending.removeLast()
            val value = frame.value
            if (frame.leaving) {
                imageFilters[value] = when (value) {
                    is ImageFilter.Merge -> imageFilters.getValue(value).also {
                        mergeInputs.getValue(value).addAll(value.inputs.map(imageFilters::getValue))
                    }
                    is ImageFilter.RuntimeEffect -> imageFilters.getValue(value).also {
                        imageRuntimeChildren.getValue(value).apply {
                            value.childImageFilters.forEach { (name, child) -> put(name, child?.let(imageFilters::getValue)) }
                        }
                    }
                    is ImageFilter.Crop -> value.copy(crop = value.crop.snapshotGeometry(), input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.Blur -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.DropShadow -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.ColorFilter -> value.copy(filter = snapshot(value.filter), input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.Compose -> value.copy(outer = imageFilters.getValue(value.outer), inner = imageFilters.getValue(value.inner))
                    is ImageFilter.Blend -> value.copy(background = imageFilters.getValue(value.background), foreground = imageFilters.getValue(value.foreground))
                    is ImageFilter.Dilate -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.Erode -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.DistantLitDiffuse -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.PointLitDiffuse -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.SpotLitDiffuse -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.DistantLitSpecular -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.PointLitSpecular -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.SpotLitSpecular -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.Offset -> value.copy(input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.Tile -> value.copy(src = value.src.snapshotGeometry(), dst = value.dst.snapshotGeometry(), input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.DisplacementMap -> value.copy(displacement = imageFilters.getValue(value.displacement), input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.Picture -> value.copy(src = value.src?.snapshotGeometry())
                    is ImageFilter.Magnifier -> value.copy(src = value.src.snapshotGeometry(), input = value.input?.let(imageFilters::getValue))
                    is ImageFilter.MatrixConvolution -> value.copy(kernel = value.kernel.copyOf(), input = value.input?.let(imageFilters::getValue))
                }
                continue
            }
            if (imageFilters.containsKey(value)) continue
            when (value) {
                is ImageFilter.Merge -> {
                    val inputs = mutableListOf<ImageFilter>()
                    mergeInputs[value] = inputs
                    imageFilters[value] = ImageFilter.Merge(Collections.unmodifiableList(inputs))
                    pending += ImageFilterFrame(value, true)
                    value.inputs.reversed().forEach { pending += ImageFilterFrame(it, false) }
                }
                is ImageFilter.RuntimeEffect -> {
                    val children = linkedMapOf<String, ImageFilter?>()
                    imageRuntimeChildren[value] = children
                    imageFilters[value] = ImageFilter.RuntimeEffect(value.effect, value.uniforms, value.childShaderName, Collections.unmodifiableMap(children))
                    pending += ImageFilterFrame(value, true)
                    value.childImageFilters.values.filterNotNull().toList().asReversed().forEach { pending += ImageFilterFrame(it, false) }
                }
                else -> {
                    pending += ImageFilterFrame(value, true)
                    imageFilterChildren(value).asReversed().forEach { pending += ImageFilterFrame(it, false) }
                }
            }
        }
        return imageFilters.getValue(filter)
    }
}

private fun shaderChildren(value: Shader): List<Shader> = when (value) {
    is Shader.Blend -> listOf(value.dst, value.src)
    is Shader.WithLocalMatrix -> listOf(value.shader)
    is Shader.WithColorFilter -> listOf(value.shader)
    is Shader.CoordClamp -> listOf(value.shader)
    is Shader.WithWorkingColorSpace -> listOf(value.shader)
    else -> emptyList()
}

private fun colorFilterChildren(value: ColorFilter): List<ColorFilter> = when (value) {
    is ColorFilter.Compose -> listOf(value.outer, value.inner)
    is ColorFilter.Lerp -> listOf(value.dst, value.src)
    else -> emptyList()
}

private fun imageFilterChildren(value: ImageFilter): List<ImageFilter> = when (value) {
    is ImageFilter.Crop -> listOfNotNull(value.input)
    is ImageFilter.Blur -> listOfNotNull(value.input)
    is ImageFilter.DropShadow -> listOfNotNull(value.input)
    is ImageFilter.ColorFilter -> listOfNotNull(value.input)
    is ImageFilter.Compose -> listOf(value.outer, value.inner)
    is ImageFilter.Blend -> listOf(value.background, value.foreground)
    is ImageFilter.Dilate -> listOfNotNull(value.input)
    is ImageFilter.Erode -> listOfNotNull(value.input)
    is ImageFilter.DistantLitDiffuse -> listOfNotNull(value.input)
    is ImageFilter.PointLitDiffuse -> listOfNotNull(value.input)
    is ImageFilter.SpotLitDiffuse -> listOfNotNull(value.input)
    is ImageFilter.DistantLitSpecular -> listOfNotNull(value.input)
    is ImageFilter.PointLitSpecular -> listOfNotNull(value.input)
    is ImageFilter.SpotLitSpecular -> listOfNotNull(value.input)
    is ImageFilter.Offset -> listOfNotNull(value.input)
    is ImageFilter.Tile -> listOfNotNull(value.input)
    is ImageFilter.DisplacementMap -> listOfNotNull(value.displacement, value.input)
    is ImageFilter.Magnifier -> listOfNotNull(value.input)
    is ImageFilter.MatrixConvolution -> listOfNotNull(value.input)
    else -> emptyList()
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

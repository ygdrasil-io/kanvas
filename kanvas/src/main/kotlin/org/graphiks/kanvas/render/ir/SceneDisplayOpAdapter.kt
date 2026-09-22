package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.toCompatibilityPath
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typeface
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

/** Reconstructs public display operations from their typed captured scene representation. */
public object SceneDisplayOpAdapter {
    public fun toDisplayOps(scene: SceneSnapshot): List<DisplayOp> {
        val filters = FilterRestoreContext(scene.filterTable)
        return scene.map { command -> toDisplayOp(command, filters) }.toList()
    }

    private fun toDisplayOp(
        command: SceneCommand,
        filters: FilterRestoreContext,
    ): DisplayOp = when (command) {
        is SceneCommand.Draw -> draw(command.node, filters)
        is SceneCommand.DrawColor -> DisplayOp.DrawColor(
            command.color,
            org.graphiks.kanvas.paint.BlendMode.valueOf(command.mode.name),
            command.transform,
            command.clip.toClip(),
        )
        is SceneCommand.Clear -> DisplayOp.Clear(command.color.toColorARGB())
        is SceneCommand.SetTransform -> DisplayOp.SetTransform(command.matrix)
        is SceneCommand.SetClip -> DisplayOp.SetClip(command.clip.toClip())
        is SceneCommand.BeginLayer -> DisplayOp.BeginLayer(
            SaveLayerRec(
                bounds = command.descriptor.copyBounds(),
                paint = command.descriptor.paint?.let { restorePaint(it, filters) },
                backdrop = command.descriptor.backdrop.singleImageFilterOrNull()?.let { restoreFilter(it, filters) },
                compositeClip = command.descriptor.compositeClip?.toClip(),
                initWithPrevious = command.descriptor.initWithPrevious,
            ),
            command.descriptor.transform,
        )
        SceneCommand.EndLayer -> DisplayOp.EndLayer
        is SceneCommand.Annotation -> DisplayOp.Annotation(command.copyBounds(), command.key, command.value)
        is SceneCommand.Readback -> DisplayOp.FlushAndSnapshot(command.request.copyBounds())
        is SceneCommand.State -> throw IllegalArgumentException("Opaque state commands are not public DisplayOps")
    }

    private fun draw(
        node: DrawNode,
        filters: FilterRestoreContext,
    ): DisplayOp {
        val clip = node.clip.toClip()
        val paint = node.paint?.let { restorePaint(it, filters) }
        fun requiredPaint(): Paint = requireNotNull(paint) { "Captured public draw is missing its Paint" }
        fun image(): org.graphiks.kanvas.image.Image = ResourceSceneAdapter.toImage(
            requireNotNull(node.resource) { "Captured image draw is missing its image resource" },
        )
        return when (node.origin) {
            DrawOrigin.RECT -> DisplayOp.DrawRect((node.geometry as GeometryNode.Rect).copyBounds(), requiredPaint(), node.transform, clip)
            DrawOrigin.RRECT -> DisplayOp.DrawRRect((node.geometry as GeometryNode.RRect).copyShape(), requiredPaint(), node.transform, clip)
            DrawOrigin.DOUBLE_RRECT -> {
                val geometry = node.geometry as GeometryNode.DoubleRRect
                DisplayOp.DrawDRRect(geometry.copyOuter(), geometry.copyInner(), requiredPaint(), node.transform, clip)
            }
            DrawOrigin.PATH,
            DrawOrigin.TEXT_EXPANDED_PATH,
            -> {
                val geometry = node.geometry as GeometryNode.Path
                val path = geometry.path.toCompatibilityPath()
                if (node.origin == DrawOrigin.TEXT_EXPANDED_PATH) {
                    DisplayOp.DrawPath.withSourceOperation(path, requiredPaint(), node.transform, clip, DrawPathSourceOperation.TEXT_EXPANDED)
                } else {
                    DisplayOp.DrawPath(path, requiredPaint(), node.transform, clip)
                }
            }
            DrawOrigin.POINT -> {
                val point = (node.geometry as GeometryNode.Points).pointAt(0)
                DisplayOp.DrawPoint(point.x, point.y, requiredPaint(), node.transform, clip)
            }
            DrawOrigin.POINTS -> {
                val geometry = node.geometry as GeometryNode.Points
                DisplayOp.DrawPoints(PointMode.valueOf(geometry.mode.name), geometry.toList(), requiredPaint(), node.transform, clip)
            }
            DrawOrigin.IMAGE -> {
                val geometry = node.geometry as GeometryNode.ImagePatch
                DisplayOp.DrawImage(
                    image(), geometry.copySource(), geometry.copyDestination(), paint, node.transform, clip,
                    geometry.sampling.toSamplingOptions(),
                )
            }
            DrawOrigin.IMAGE_NINE -> {
                val geometry = node.geometry as GeometryNode.ImageNine
                DisplayOp.DrawImageNine(image(), geometry.copyCenter(), geometry.copyDestination(), paint, node.transform, clip)
            }
            DrawOrigin.IMAGE_LATTICE -> {
                val geometry = node.geometry as GeometryNode.ImageLattice
                DisplayOp.DrawImageLattice(
                    image(),
                    Lattice(
                        xDivs = geometry.copyXDivs().toList(),
                        yDivs = geometry.copyYDivs().toList(),
                        rects = geometry.copyCellRects(),
                        colors = geometry.copyColors(),
                        flags = geometry.copyFlags()?.map { LatticeFlags.valueOf(it.name) },
                    ),
                    geometry.copyDestination(),
                    paint,
                    node.transform,
                    clip,
                    geometry.sampling.toSamplingOptions(),
                )
            }
            DrawOrigin.ATLAS -> {
                val geometry = node.geometry as GeometryNode.Atlas
                val entries = geometry.toList()
                DisplayOp.DrawAtlas(
                    image(),
                    transforms = entries.map { it.transform },
                    texRects = entries.map(GeometryNode.AtlasEntry::copySource),
                    colors = entries.takeIf { entries.any { it.color != null } }?.map { entry ->
                        requireNotNull(entry.color) { "Captured atlas color table is incomplete" }
                    },
                    blendMode = org.graphiks.kanvas.paint.BlendMode.valueOf(requireNotNull(node.operationBlendMode).name),
                    paint = paint,
                    transform = node.transform,
                    clip = clip,
                )
            }
            DrawOrigin.VERTICES -> DisplayOp.DrawVertices((node.geometry as GeometryNode.IndexedMesh).toVertices(), requiredPaint(), node.transform, clip)
            DrawOrigin.MESH -> {
                val geometry = node.geometry as GeometryNode.IndexedMesh
                DisplayOp.DrawMesh(
                    Mesh(
                        geometry.toVertices(),
                        program = geometry.meshProgram?.let(PaintSceneAdapter::restoreMeshProgram),
                        bounds = requireNotNull(geometry.copyBounds()) { "Captured Mesh bounds are missing" },
                    ),
                    requiredPaint(),
                    node.operationBlendMode?.let { org.graphiks.kanvas.paint.BlendMode.valueOf(it.name) },
                    node.transform,
                    clip,
                )
            }
            DrawOrigin.TEXT -> {
                val geometry = node.geometry as GeometryNode.TextBlob
                DisplayOp.DrawText(
                    TextBlob(
                        glyphRuns = geometry.map(::toKanvasGlyphRun),
                        typeface = geometry.typeface?.toPublicTypeface(),
                        fontSize = geometry.fontSize,
                        variationCoordinates = geometry.variationCoordinates(),
                    ),
                    geometry.x,
                    geometry.y,
                    requiredPaint(),
                    node.transform,
                    clip,
                )
            }
            DrawOrigin.PICTURE -> {
                val geometry = node.geometry as GeometryNode.Picture
                DisplayOp.DrawPicture(Picture(geometry.copyCullRect(), toDisplayOps(geometry.scene)), paint, node.transform, clip)
            }
        }
    }

    private fun restorePaint(
        node: PaintNode,
        filters: FilterRestoreContext,
    ): Paint = PaintSceneAdapter.restore(node).let { restored ->
        node.imageFilter?.let { restored.copy(imageFilter = restoreFilter(it, filters)) } ?: restored
    }

    private fun restoreFilter(
        node: CapturedFilterRootV1,
        filters: FilterRestoreContext,
    ): org.graphiks.kanvas.paint.ImageFilter = filters.restore(node)

    private fun GeometryNode.IndexedMesh.toVertices(): Vertices = Vertices(
        mode = VertexMode.valueOf(primitiveMode.name),
        positions = (0 until vertexCount).map(::vertexAt),
        texCoords = copyTexCoords(),
        colors = copyColors(),
        indices = copyIndices()?.toList(),
    )

    private fun toKanvasGlyphRun(run: GeometryNode.GlyphRun): KanvasGlyphRun = KanvasGlyphRun(
        glyphs = run.copyGlyphIds().map(Int::toUShort),
        positions = (0 until run.glyphCount).map(run::positionAt),
        fontSize = run.fontSize,
    )

    private fun TypefaceReference.toPublicTypeface(): Typeface {
        val prefix = "kanvas-resource:"
        require(id.value.startsWith(prefix)) {
            "Typeface identity is preserved in scene IR but is not reconstructible without font data"
        }
        return KanvasTypeface(id.value.removePrefix(prefix))
    }

    private fun ImageSampling.toSamplingOptions(): SamplingOptions = when (this) {
        ImageSampling.Nearest -> SamplingOptions.NEAREST
        ImageSampling.Linear -> SamplingOptions.LINEAR
        is ImageSampling.Cubic -> SamplingOptions.Cubic(b, c)
    }

    private fun ClipStackNode.toClip(): ClipStack = when (this) {
        ClipStackNode.Empty -> ClipStack.WideOpen
        is ClipStackNode.DeviceRect -> ClipStack.DeviceRect(copyBounds(), antiAlias)
        is ClipStackNode.Operations -> ClipStack.Complex(map { entry ->
            val op = ClipOp.valueOf(entry.operation.name)
            when (val geometry = entry.geometry) {
                is GeometryNode.Rect -> ClipStackOp.RectOp(geometry.copyBounds(), op, entry.antiAlias, entry.transform)
                is GeometryNode.RRect -> ClipStackOp.RRectOp(geometry.copyShape(), op, entry.antiAlias, entry.transform)
                is GeometryNode.Path -> ClipStackOp.PathOp(geometry.path.toCompatibilityPath(), op, entry.antiAlias, entry.transform)
                else -> throw IllegalArgumentException("Clip geometry is not a public clip shape")
            }
        })
    }
}

private fun EffectStack.singleImageFilterOrNull(): CapturedFilterRootV1? = when (this) {
    EffectStack.Empty -> null
    is EffectStack.Entries -> {
        require(effectCount == 1 && effectAt(0) is CapturedFilterRootV1) { "Layer backdrop is not a single ImageFilter" }
        effectAt(0) as CapturedFilterRootV1
    }
}

private class FilterRestoreContext(private val table: CapturedFilterTableV1) {
    private val filters = mutableMapOf<CapturedFilterNodeId, org.graphiks.kanvas.paint.ImageFilter>()

    fun restore(root: CapturedFilterRootV1): org.graphiks.kanvas.paint.ImageFilter = node(root.id)

    private fun input(value: CapturedFilterInputV1): org.graphiks.kanvas.paint.ImageFilter? = when (value) {
        CapturedFilterInputV1.ImplicitSource -> null
        is CapturedFilterInputV1.Node -> node(value.id)
        CapturedFilterInputV1.TransparentBlack,
        is CapturedFilterInputV1.Picture,
        is CapturedFilterInputV1.Backdrop,
        -> throw IllegalArgumentException("Captured filter input is not public-replayable in W6b")
    }

    private fun node(id: CapturedFilterNodeId): org.graphiks.kanvas.paint.ImageFilter = filters[id] ?: when (val value = table.nodeAt(id)) {
        is CapturedFilterNodeV1.Crop -> org.graphiks.kanvas.paint.ImageFilter.Crop(value.crop.copy(), org.graphiks.kanvas.paint.TileMode.valueOf(value.tileMode.name), input(value.input))
        is CapturedFilterNodeV1.Blur -> org.graphiks.kanvas.paint.ImageFilter.Blur(value.sigmaX, value.sigmaY, org.graphiks.kanvas.paint.TileMode.valueOf(value.tileMode.name), input(value.input))
        is CapturedFilterNodeV1.DropShadow -> org.graphiks.kanvas.paint.ImageFilter.DropShadow(value.dx, value.dy, value.sigmaX, value.sigmaY, value.color, input(value.input), org.graphiks.kanvas.paint.DropShadowMode.valueOf(value.mode.name))
        is CapturedFilterNodeV1.ColorFilter -> org.graphiks.kanvas.paint.ImageFilter.ColorFilter(PaintSceneAdapter.restoreColorFilter(value.filter), input(value.input))
        is CapturedFilterNodeV1.Compose -> org.graphiks.kanvas.paint.ImageFilter.Compose(requireNotNull(input(value.outer)), requireNotNull(input(value.inner)))
        is CapturedFilterNodeV1.Blend -> org.graphiks.kanvas.paint.ImageFilter.Blend(org.graphiks.kanvas.paint.BlendMode.valueOf(value.mode.name), requireNotNull(input(value.background)), requireNotNull(input(value.foreground)))
        is CapturedFilterNodeV1.Dilate -> org.graphiks.kanvas.paint.ImageFilter.Dilate(value.radiusX, value.radiusY, input(value.input))
        is CapturedFilterNodeV1.Erode -> org.graphiks.kanvas.paint.ImageFilter.Erode(value.radiusX, value.radiusY, input(value.input))
        is CapturedFilterNodeV1.DistantLitDiffuse -> org.graphiks.kanvas.paint.ImageFilter.DistantLitDiffuse(org.graphiks.math.vector.Vector2F32(value.directionX, value.directionY), value.lightColor, value.surfaceScale, value.kd, input(value.input))
        is CapturedFilterNodeV1.PointLitDiffuse -> org.graphiks.kanvas.paint.ImageFilter.PointLitDiffuse(value.location, value.lightColor, value.surfaceScale, value.kd, input(value.input))
        is CapturedFilterNodeV1.SpotLitDiffuse -> org.graphiks.kanvas.paint.ImageFilter.SpotLitDiffuse(value.location, value.target, value.specularExponent, value.cutoffAngle, value.lightColor, value.surfaceScale, value.kd, input(value.input))
        is CapturedFilterNodeV1.DistantLitSpecular -> org.graphiks.kanvas.paint.ImageFilter.DistantLitSpecular(org.graphiks.math.vector.Vector2F32(value.directionX, value.directionY), value.lightColor, value.surfaceScale, value.ks, value.shininess, input(value.input))
        is CapturedFilterNodeV1.PointLitSpecular -> org.graphiks.kanvas.paint.ImageFilter.PointLitSpecular(value.location, value.lightColor, value.surfaceScale, value.ks, value.shininess, input(value.input))
        is CapturedFilterNodeV1.SpotLitSpecular -> org.graphiks.kanvas.paint.ImageFilter.SpotLitSpecular(value.location, value.target, value.specularExponent, value.cutoffAngle, value.lightColor, value.surfaceScale, value.ks, value.shininess, input(value.input))
        is CapturedFilterNodeV1.Offset -> org.graphiks.kanvas.paint.ImageFilter.Offset(value.dx, value.dy, input(value.input))
        is CapturedFilterNodeV1.Tile -> org.graphiks.kanvas.paint.ImageFilter.Tile(value.src.copy(), value.dst.copy(), input(value.input))
        is CapturedFilterNodeV1.Merge -> org.graphiks.kanvas.paint.ImageFilter.Merge(value.map { requireNotNull(input(it)) })
        is CapturedFilterNodeV1.DisplacementMap -> org.graphiks.kanvas.paint.ImageFilter.DisplacementMap(org.graphiks.kanvas.paint.ColorChannel.valueOf(value.xChannelSelector.name.first().toString()), org.graphiks.kanvas.paint.ColorChannel.valueOf(value.yChannelSelector.name.first().toString()), value.scale, requireNotNull(input(value.displacement)), input(value.input))
        is CapturedFilterNodeV1.Picture -> org.graphiks.kanvas.paint.ImageFilter.Picture(Picture(value.cullRect.copy(), SceneDisplayOpAdapter.toDisplayOps(value.scene)), value.src?.copy())
        is CapturedFilterNodeV1.Magnifier -> org.graphiks.kanvas.paint.ImageFilter.Magnifier(value.src.copy(), value.zoom, value.inset, input(value.input))
        is CapturedFilterNodeV1.MatrixConvolution -> org.graphiks.kanvas.paint.ImageFilter.MatrixConvolution(value.kernelSize, value.kernel.copyToFloatArray(), value.gain, value.bias, value.kernelOffset, org.graphiks.kanvas.paint.TileMode.valueOf(value.tileMode.name), value.convolveAlpha, input(value.input))
        is CapturedFilterNodeV1.RuntimeEffect -> PaintSceneAdapter.restoreRuntimeImageFilter(
            value,
            value.associate { child -> child.name to input(child.input) },
        )
    }.also { filters[id] = it }
}

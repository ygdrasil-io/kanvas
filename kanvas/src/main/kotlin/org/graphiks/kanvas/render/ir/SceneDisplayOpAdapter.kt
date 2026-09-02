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
    public fun toDisplayOps(scene: SceneSnapshot): List<DisplayOp> = scene.map(::toDisplayOp).toList()

    private fun toDisplayOp(command: SceneCommand): DisplayOp = when (command) {
        is SceneCommand.Draw -> draw(command.node)
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
                paint = command.descriptor.paint?.let(PaintSceneAdapter::restore),
                backdrop = command.descriptor.backdrop.singleImageFilterOrNull()?.let(PaintSceneAdapter::restoreImageFilter),
                compositeClip = command.descriptor.compositeClip?.toClip(),
            ),
            command.descriptor.transform,
        )
        SceneCommand.EndLayer -> DisplayOp.EndLayer
        is SceneCommand.Annotation -> DisplayOp.Annotation(command.copyBounds(), command.key, command.value)
        is SceneCommand.Readback -> DisplayOp.FlushAndSnapshot(command.request.copyBounds())
        is SceneCommand.State -> throw IllegalArgumentException("Opaque state commands are not public DisplayOps")
    }

    private fun draw(node: DrawNode): DisplayOp {
        val clip = node.clip.toClip()
        val paint = node.paint?.let(PaintSceneAdapter::restore)
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
            DrawOrigin.IMAGE,
            DrawOrigin.IMAGE_NINE,
            -> {
                val geometry = node.geometry as GeometryNode.ImagePatch
                if (node.origin == DrawOrigin.IMAGE) {
                    DisplayOp.DrawImage(image(), geometry.copySource(), geometry.copyDestination(), paint, node.transform, clip)
                } else {
                    DisplayOp.DrawImageNine(image(), geometry.copySource(), geometry.copyDestination(), paint, node.transform, clip)
                }
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
                is GeometryNode.Rect -> ClipStackOp.RectOp(geometry.copyBounds(), op, entry.antiAlias, entry.perspectiveCaptureRefusal)
                is GeometryNode.RRect -> ClipStackOp.RRectOp(geometry.copyShape(), op, entry.antiAlias, entry.perspectiveCaptureRefusal, entry.transformClass)
                is GeometryNode.Path -> ClipStackOp.PathOp(geometry.path.toCompatibilityPath(), op, entry.antiAlias, entry.perspectiveCaptureRefusal, entry.transformClass)
                else -> throw IllegalArgumentException("Clip geometry is not a public clip shape")
            }
        })
    }
}

private fun EffectStack.singleImageFilterOrNull(): ImageFilterNode? = when (this) {
    EffectStack.Empty -> null
    is EffectStack.Entries -> {
        require(effectCount == 1 && effectAt(0) is ImageFilterNode) { "Layer backdrop is not a single ImageFilter" }
        effectAt(0) as ImageFilterNode
    }
}

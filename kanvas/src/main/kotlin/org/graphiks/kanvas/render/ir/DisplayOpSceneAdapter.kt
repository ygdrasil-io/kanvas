package org.graphiks.kanvas.render.ir

import java.util.Collections
import java.util.ArrayDeque
import java.util.IdentityHashMap
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.Typeface
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.color.ColorF32
import org.graphiks.math.vector.Vector2F32

/** Immutable capture budgets, checked before any renderer is asked to plan the scene. */
public data class SceneCaptureLimits(
    public val graphLimits: GraphLimits = GraphLimits(),
    public val maxDepth: Int = 64,
    public val maxNodes: Int = 4_096,
    public val maxResources: Int = 1_024,
) {
    init {
        require(maxDepth > 0) { "SceneCaptureLimits.maxDepth must be positive" }
        require(maxNodes > 0) { "SceneCaptureLimits.maxNodes must be positive" }
        require(maxResources > 0) { "SceneCaptureLimits.maxResources must be positive" }
    }

    public companion object {
        public val DEFAULT: SceneCaptureLimits = SceneCaptureLimits()
    }
}

/** Typed outcome of DisplayOp capture; neither backend handles nor exceptions are exposed. */
public sealed interface SceneCaptureResult {
    public data class Captured(public val scene: SceneSnapshot) : SceneCaptureResult

    public class Invalid(diagnostics: List<RenderDiagnostic>) : SceneCaptureResult {
        public val diagnostics: List<RenderDiagnostic> = Collections.unmodifiableList(diagnostics.toList())
    }
}

/** Converts recorded Canvas operations into the handle-free Scene IR. */
public object DisplayOpSceneAdapter {
    public fun capture(
        operations: List<DisplayOp>,
        extent: SceneExtent,
        colorSpace: ColorSpace,
        limits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT,
    ): SceneCaptureResult {
        val diagnostics = mutableListOf<RenderDiagnostic>()
        return try {
            val context = CaptureContext(limits)
            SceneCaptureResult.Captured(SceneSnapshot.of(extent, colorSpace, captureOperations(operations, limits, context)))
        } catch (failure: CaptureFailure) {
            invalid(diagnostics, failure.code, failure.message)
        } catch (_: IllegalArgumentException) {
            invalid(diagnostics, "scene-capture-invalid", "DisplayOp capture contains an invalid semantic value")
        } catch (_: ArithmeticException) {
            invalid(diagnostics, "scene-capture-invalid", "DisplayOp capture overflows a semantic resource bound")
        }
    }

    private fun captureOperations(
        operations: List<DisplayOp>,
        limits: SceneCaptureLimits,
        context: CaptureContext,
    ): List<SceneCommand> = operations.mapIndexed { index, operation ->
        context.countNode()
        captureOperation(operation, index, limits, context).also { context.validate(it) }
    }

    private fun captureOperation(
        operation: DisplayOp,
        index: Int,
        limits: SceneCaptureLimits,
        context: CaptureContext,
    ): SceneCommand = when (operation) {
        is DisplayOp.DrawRect -> draw(
            geometry = GeometryNode.Rect.of(operation.rect.checked("draw[$index].rect")),
            operation = operation,
            origin = DrawOrigin.RECT,
            limits = limits,
            context = context,
        )
        is DisplayOp.DrawPoint -> draw(
            geometry = GeometryNode.Points.of(
                PointMode.POINTS,
                listOf(Point2F32(operation.x.checked("draw[$index].x"), operation.y.checked("draw[$index].y"))),
            ),
            operation = operation,
            origin = DrawOrigin.POINT,
            limits = limits,
            context = context,
        )
        is DisplayOp.DrawRRect -> draw(GeometryNode.RRect.of(operation.rrect.checked("draw[$index].rrect")), operation.paint, operation.transform, operation.clip, DrawOrigin.RRECT, limits, context = context)
        is DisplayOp.DrawDRRect -> draw(GeometryNode.DoubleRRect.of(operation.outer.checked("draw[$index].outer"), operation.inner.checked("draw[$index].inner")), operation.paint, operation.transform, operation.clip, DrawOrigin.DOUBLE_RRECT, limits, context = context)
        is DisplayOp.DrawPath -> draw(GeometryNode.Path(operation.path.toPathF32().checked("draw[$index].path")), operation.paint, operation.transform, operation.clip, if (operation.sourceOperation == "text-expanded") DrawOrigin.TEXT_EXPANDED_PATH else DrawOrigin.PATH, limits, context = context)
        is DisplayOp.DrawPoints -> draw(GeometryNode.Points.of(PointMode.valueOf(operation.mode.name), operation.points.map { point -> Point2F32(point.x.checked("draw[$index].point.x"), point.y.checked("draw[$index].point.y")) }), operation.paint, operation.transform, operation.clip, DrawOrigin.POINTS, limits, context = context)
        is DisplayOp.DrawImage -> imageDraw(GeometryNode.ImagePatch.of(ResourceReference(ResourceId(operation.image.sourceId)), operation.src.checked("draw[$index].src"), operation.dst.checked("draw[$index].dst")), operation.paint, operation.transform, operation.clip, DrawOrigin.IMAGE, context.captureImage(operation.image), limits, context = context)
        is DisplayOp.DrawImageNine -> imageDraw(GeometryNode.ImagePatch.of(ResourceReference(ResourceId(operation.image.sourceId)), operation.center.checked("draw[$index].center"), operation.dst.checked("draw[$index].dst")), operation.paint, operation.transform, operation.clip, DrawOrigin.IMAGE_NINE, context.captureImage(operation.image), limits, context = context)
        is DisplayOp.DrawImageLattice -> imageDraw(
            GeometryNode.ImageLattice.of(ResourceReference(ResourceId(operation.image.sourceId)), operation.lattice.xDivs.toIntArray(), operation.lattice.yDivs.toIntArray(), operation.lattice.rects?.map { it.checked("draw[$index].lattice-rect") }, operation.lattice.colors, operation.lattice.flags?.map { LatticeCellFlag.valueOf(it.name) }, operation.dst.checked("draw[$index].dst"), operation.sampling.toImageSampling()),
            operation.paint, operation.transform, operation.clip, DrawOrigin.IMAGE_LATTICE, context.captureImage(operation.image), limits, context = context,
        )
        is DisplayOp.DrawAtlas -> {
            if (operation.transforms.size != operation.texRects.size || operation.colors?.size?.let { it != operation.transforms.size } == true) {
                throw CaptureFailure("atlas-cardinality", "Atlas transforms, texture rectangles, and colors must have identical cardinalities")
            }
            imageDraw(
                GeometryNode.Atlas.of(ResourceReference(ResourceId(operation.atlas.sourceId)), operation.transforms.indices.map { i -> GeometryNode.AtlasEntry.of(operation.transforms[i].checked("draw[$index].atlas-transform"), operation.texRects[i].checked("draw[$index].atlas-rect"), operation.colors?.get(i)) }),
                operation.paint, operation.transform, operation.clip, DrawOrigin.ATLAS, context.captureImage(operation.atlas), limits, BlendMode.valueOf(operation.blendMode.name), context,
            )
        }
        is DisplayOp.DrawVertices -> draw(operation.vertices.toGeometry(null), operation.paint, operation.transform, operation.clip, DrawOrigin.VERTICES, limits, context = context)
        is DisplayOp.DrawMesh -> draw(
            operation.mesh.vertices.toGeometry(
                operation.mesh.bounds,
                operation.mesh.program?.let {
                    context.preflightMeshProgram(it)
                    PaintSceneAdapter.captureMeshProgram(it, context::captureImage)
                },
            ),
            operation.paint, operation.transform, operation.clip, DrawOrigin.MESH, limits,
            operation.blendMode?.let { BlendMode.valueOf(it.name) }, context,
        )
        is DisplayOp.DrawText -> draw(
            GeometryNode.TextBlob.of(
                runs = operation.blob.glyphRuns.map(::captureGlyphRun),
                x = operation.x.checked("text.x"),
                y = operation.y.checked("text.y"),
                typeface = operation.blob.typeface?.toTypefaceReference(),
                fontSize = operation.blob.fontSize.checked("text.blob.font-size"),
                variationCoordinates = operation.blob.variationCoordinates.mapValues { (_, value) -> value.checked("text.blob.variation") },
            ),
            operation.paint,
            operation.transform,
            operation.clip,
            DrawOrigin.TEXT,
            limits,
            context = context,
        )
        is DisplayOp.DrawPicture -> imageDraw(GeometryNode.Picture.of(capturePicture(operation.picture, limits, context), operation.picture.cullRect.checked("draw[$index].picture-cull")), operation.paint, operation.transform, operation.clip, DrawOrigin.PICTURE, null, limits, context = context)
        is DisplayOp.SetTransform -> SceneCommand.SetTransform(operation.matrix.checked("set-transform"))
        is DisplayOp.SetClip -> SceneCommand.SetClip(captureClip(operation.clip))
        is DisplayOp.Annotation -> SceneCommand.Annotation.of(operation.rect.checked("annotation"), operation.key, operation.value)
        is DisplayOp.FlushAndSnapshot -> SceneCommand.Readback(ReadbackRequest.of("readback-$index", operation.bounds.checked("readback")))
        is DisplayOp.DrawColor -> SceneCommand.DrawColor(
            color = operation.color,
            mode = BlendMode.valueOf(operation.mode.name),
            transform = operation.transform.checked("draw[$index].transform"),
            clip = captureClip(operation.clip),
        )
        is DisplayOp.Clear -> SceneCommand.Clear(ColorF32.fromColorARGB(operation.color))
        is DisplayOp.BeginLayer -> {
            val paint = operation.rec.paint?.let { capturePaint(it, limits, context) }
            val backdrop = operation.rec.backdrop?.let { filter ->
                requireNotNull(capturePaint(org.graphiks.kanvas.paint.Paint(imageFilter = filter), limits, context, defaultMaterial = false).imageFilter)
            }
            SceneCommand.BeginLayer(
                LayerDescriptor.of(
                    bounds = operation.rec.bounds?.checked("layer[$index].bounds"),
                    material = paint?.shader ?: paint?.let { MaterialNode.Solid(it.color) },
                    paint = paint,
                    blend = paint?.toBlendNode() ?: BlendNode.SrcOver,
                    compositeClip = operation.rec.compositeClip?.let(::captureClip),
                    backdrop = backdrop?.let { EffectStack.of(listOf(it)) } ?: EffectStack.Empty,
                    effects = paint?.toEffectStack() ?: EffectStack.Empty,
                    transform = operation.transform.checked("layer[$index].transform"),
                ),
            )
        }
        DisplayOp.EndLayer -> SceneCommand.EndLayer
        else -> throw CaptureFailure("unsupported-display-op", "DisplayOp variant is not capturable by this scene adapter")
    }

    private fun draw(
        geometry: GeometryNode,
        operation: DisplayOp.DrawRect,
        origin: DrawOrigin,
        limits: SceneCaptureLimits,
        context: CaptureContext,
    ): SceneCommand.Draw {
        return draw(geometry, operation.paint, operation.transform, operation.clip, origin, limits, context = context)
    }

    private fun draw(
        geometry: GeometryNode,
        operation: DisplayOp.DrawPoint,
        origin: DrawOrigin,
        limits: SceneCaptureLimits,
        context: CaptureContext,
    ): SceneCommand.Draw {
        return draw(geometry, operation.paint, operation.transform, operation.clip, origin, limits, context = context)
    }

    private fun draw(
        geometry: GeometryNode,
        sourcePaint: org.graphiks.kanvas.paint.Paint,
        transform: Matrix3x3F32,
        clip: ClipStack,
        origin: DrawOrigin,
        limits: SceneCaptureLimits,
        operationBlendMode: BlendMode? = null,
        context: CaptureContext,
    ): SceneCommand.Draw {
        val paint = capturePaint(sourcePaint, limits, context)
        return SceneCommand.Draw(
            DrawNode(
                geometry = geometry,
                material = paint.shader ?: MaterialNode.Solid(paint.color),
                coverage = if (paint.antiAlias) CoverageRequest.ANTIALIASED else CoverageRequest.HARD_EDGE,
                clip = captureClip(clip),
                blend = paint.toBlendNode(),
                effects = paint.toEffectStack(),
                transform = transform.checked("draw.transform"),
                origin = origin,
                paint = paint,
                operationBlendMode = operationBlendMode,
            ),
        )
    }

    private fun imageDraw(
        geometry: GeometryNode,
        sourcePaint: org.graphiks.kanvas.paint.Paint?,
        transform: Matrix3x3F32,
        clip: ClipStack,
        origin: DrawOrigin,
        resource: ImageResourceSnapshot?,
        limits: SceneCaptureLimits,
        operationBlendMode: BlendMode? = null,
        context: CaptureContext,
    ): SceneCommand.Draw {
        val paint = sourcePaint?.let { capturePaint(it, limits, context, defaultMaterial = resource == null) }
        if (resource != null) context.countGraphLeaf()
        if (resource == null && sourcePaint == null) context.countGraphLeaf()
        return SceneCommand.Draw(DrawNode(
            geometry = geometry,
            material = resource?.let(MaterialNode::ImageSample) ?: paint?.shader ?: paint?.let { MaterialNode.Solid(it.color) } ?: MaterialNode.Transparent,
            coverage = paint?.let { if (it.antiAlias) CoverageRequest.ANTIALIASED else CoverageRequest.HARD_EDGE } ?: CoverageRequest.DEFAULT,
            clip = captureClip(clip), blend = paint?.toBlendNode() ?: BlendNode.SrcOver,
            effects = paint?.toEffectStack() ?: EffectStack.Empty, transform = transform.checked("draw.transform"), origin = origin, paint = paint,
            resource = resource, operationBlendMode = operationBlendMode,
        ))
    }

    internal fun captureClip(clip: ClipStack): ClipStackNode = when (clip) {
        ClipStack.WideOpen -> ClipStackNode.Empty
        is ClipStack.DeviceRect -> ClipStackNode.DeviceRect.of(clip.rect.checked("clip.device-rect"), clip.antiAlias)
        is ClipStack.Complex -> ClipStackNode.Operations.of(clip.ops.map { entry ->
            when (entry) {
                is org.graphiks.kanvas.canvas.ClipStackOp.RectOp -> ClipEntry(GeometryNode.Rect.of(entry.rect.checked("clip.rect")), ClipOperation.valueOf(entry.op.name), entry.antiAlias, entry.perspectiveCaptureRefusal, "identity")
                is org.graphiks.kanvas.canvas.ClipStackOp.RRectOp -> ClipEntry(GeometryNode.RRect.of(entry.rrect.checked("clip.rrect")), ClipOperation.valueOf(entry.op.name), entry.antiAlias, entry.perspectiveCaptureRefusal, entry.transformClass)
                is org.graphiks.kanvas.canvas.ClipStackOp.PathOp -> ClipEntry(GeometryNode.Path(entry.path.toPathF32().checked("clip.path")), ClipOperation.valueOf(entry.op.name), entry.antiAlias, entry.perspectiveCaptureRefusal, entry.transformClass)
            }
        })
    }

    private fun captureGlyphRun(run: KanvasGlyphRun): GeometryNode.GlyphRun = GeometryNode.GlyphRun.of(
        run.glyphs.map(UShort::toInt).toIntArray(),
        run.positions.map { it.checked("text.glyph-position") },
        run.fontSize.checked("text.glyph-font-size"),
    )

    private fun capturePaint(
        paint: org.graphiks.kanvas.paint.Paint,
        limits: SceneCaptureLimits,
        context: CaptureContext?,
        defaultMaterial: Boolean = true,
    ): PaintNode = PaintSceneAdapter.capture(
        paint = paint.also { context?.preflightPaint(it, defaultMaterial) },
        limits = limits,
        captureImage = context?.let { it::captureImage } ?: ResourceSceneAdapter::captureImage,
        capturePicture = { picture ->
            val current = requireNotNull(context) { "Nested Picture effect requires scene capture context" }
            capturePicture(picture, limits, current)
        },
    )

    private fun capturePicture(
        picture: org.graphiks.kanvas.picture.Picture,
        limits: SceneCaptureLimits,
        context: CaptureContext,
    ): SceneSnapshot {
        context.enterPicture(picture)
        val cull = picture.cullRect.checked("picture.cull")
        return try {
            SceneSnapshot.of(
                SceneExtent(cull.width().pictureExtent("picture.cull.width"), cull.height().pictureExtent("picture.cull.height")),
                ColorSpace.SRGB,
                captureOperations(picture.ops, limits, context),
            )
        } finally {
            context.leavePicture(picture)
        }
    }

    private fun invalid(
        diagnostics: MutableList<RenderDiagnostic>,
        code: String,
        message: String,
    ): SceneCaptureResult.Invalid {
        diagnostics += RenderDiagnostic(
            RenderDiagnosticCode(code),
            RenderDiagnosticDomain.SCENE,
            RenderDiagnosticSeverity.ERROR,
            message,
        )
        return SceneCaptureResult.Invalid(diagnostics)
    }
}

private class CaptureContext(private val limits: SceneCaptureLimits) {
    private val activePictures = IdentityHashMap<org.graphiks.kanvas.picture.Picture, Unit>()
    private val images = IdentityHashMap<org.graphiks.kanvas.image.Image, Unit>()
    private var nodes: Int = 0
    private var graphNodes: Int = 0

    fun countNode() {
        nodes += 1
        if (nodes > limits.maxNodes) {
            throw CaptureFailure("scene-node-limit", "Capture has more than ${limits.maxNodes} nodes")
        }
    }

    fun captureImage(image: org.graphiks.kanvas.image.Image): ImageResourceSnapshot {
        if (images.put(image, Unit) == null && images.size > limits.maxResources) {
            throw CaptureFailure("scene-resource-limit", "Capture has more than ${limits.maxResources} image resources")
        }
        return ResourceSceneAdapter.captureImage(image)
    }

    fun enterPicture(picture: org.graphiks.kanvas.picture.Picture) {
        if (activePictures.put(picture, Unit) != null) {
            throw CaptureFailure("cyclic-picture", "Nested Picture graph contains a cycle")
        }
        if (activePictures.size > limits.maxDepth) {
            throw CaptureFailure("scene-depth-limit", "Nested Picture graph exceeds depth ${limits.maxDepth}")
        }
    }

    fun leavePicture(picture: org.graphiks.kanvas.picture.Picture) { activePictures.remove(picture) }

    /**
     * Walk public paint graphs before their recursive IR conversion.  This makes
     * cycles and oversized (but otherwise valid) public graphs a typed capture
     * failure rather than a stack overflow or a backend concern.
     */
    fun preflightPaint(paint: org.graphiks.kanvas.paint.Paint, defaultMaterial: Boolean) {
        val roots = buildList<Any> {
            paint.shader?.let(::add)
            paint.colorFilter?.let(::add)
            paint.maskFilter?.let(::add)
            paint.pathEffect?.let(::add)
            paint.imageFilter?.let(::add)
            paint.blender?.let(::add)
        }
        if (paint.shader == null && defaultMaterial) countGraphLeaf()
        walkGraph(roots)
    }

    fun preflightMeshProgram(program: MeshProgram) {
        walkGraph(buildList {
            add(program.effect)
            program.children.entries.forEach { entry ->
                when (val child = entry.child) {
                    is org.graphiks.kanvas.paint.ShaderChild -> add(child.shader)
                    is org.graphiks.kanvas.paint.ColorFilterChild -> add(child.filter)
                    is org.graphiks.kanvas.paint.BlenderChild -> add(child.blender)
                }
            }
        })
    }

    fun countGraphLeaf() {
        graphNodes += 1
        if (graphNodes > limits.graphLimits.maxNodes) {
            throw CaptureFailure("graph-node-limit", "Capture graph budget exceeds ${limits.graphLimits.maxNodes} nodes")
        }
    }

    private fun walkGraph(roots: List<Any>) {
        data class Visit(val value: Any, val depth: Int, val leaving: Boolean)
        val active = IdentityHashMap<Any, Unit>()
        val pending = ArrayDeque<Visit>()
        roots.asReversed().forEach { pending.addLast(Visit(it, 1, false)) }
        while (pending.isNotEmpty()) {
            val visit = pending.removeLast()
            if (visit.leaving) {
                active.remove(visit.value)
                continue
            }
            if (active.put(visit.value, Unit) != null) {
                throw CaptureFailure("cyclic-effect-graph", "Paint, effect, or material graph contains an identity cycle")
            }
            if (visit.depth > minOf(limits.maxDepth, limits.graphLimits.maxDepth)) {
                throw CaptureFailure("graph-depth-limit", "Paint, effect, or material graph exceeds configured depth")
            }
            countGraphLeaf()
            pending.addLast(Visit(visit.value, visit.depth, true))
            graphChildren(visit.value).asReversed().forEach { child ->
                pending.addLast(Visit(child, visit.depth + 1, false))
            }
        }
    }

    private fun graphChildren(value: Any): List<Any> = when (value) {
        is Shader.Blend -> listOf(value.dst, value.src)
        is Shader.RuntimeEffect -> value.children.values.toList()
        is Shader.WithLocalMatrix -> listOf(value.shader)
        is Shader.WithColorFilter -> listOf(value.shader, value.filter)
        is Shader.WithWorkingColorSpace -> listOf(value.shader)
        is Shader.CoordClamp -> listOf(value.shader)
        is ColorFilter.Compose -> listOf(value.outer, value.inner)
        is ColorFilter.Lerp -> listOf(value.dst, value.src)
        is ColorFilter.RuntimeEffect -> value.children.values.toList()
        is MaskFilter.Shader -> listOf(value.shader)
        is ImageFilter.Crop -> listOfNotNull(value.input)
        is ImageFilter.Blur -> listOfNotNull(value.input)
        is ImageFilter.DropShadow -> listOfNotNull(value.input)
        is ImageFilter.ColorFilter -> listOfNotNull(value.filter, value.input)
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
        is ImageFilter.Merge -> value.inputs.toList()
        is ImageFilter.DisplacementMap -> listOfNotNull(value.displacement, value.input)
        is ImageFilter.Magnifier -> listOfNotNull(value.input)
        is ImageFilter.MatrixConvolution -> listOfNotNull(value.input)
        is ImageFilter.RuntimeEffect -> value.childImageFilters.values.filterNotNull()
        else -> emptyList()
    }

    fun validate(command: SceneCommand) {
        if (command is SceneCommand.Draw) {
            validateMaterial(command.node.material)
            command.node.paint?.let { paint ->
                paint.shader?.let(::validateMaterial)
                paint.colorFilter?.let(::validateEffect)
                paint.maskFilter?.let(::validateEffect)
                paint.pathEffect?.let(::validateEffect)
                paint.imageFilter?.let(::validateEffect)
            }
        }
        if (command is SceneCommand.BeginLayer) {
            command.descriptor.material?.let(::validateMaterial)
            command.descriptor.paint?.let { paint ->
                paint.shader?.let(::validateMaterial)
                paint.colorFilter?.let(::validateEffect)
                paint.maskFilter?.let(::validateEffect)
                paint.pathEffect?.let(::validateEffect)
                paint.imageFilter?.let(::validateEffect)
            }
        }
    }

    private fun validateMaterial(value: MaterialNode) = when (val result = MaterialGraph.validate(value, limits.graphLimits)) {
        GraphValidationResult.Valid -> Unit
        is GraphValidationResult.DepthLimitExceeded -> throw CaptureFailure("graph-depth-limit", "Material graph exceeds depth ${result.maxDepth}")
        is GraphValidationResult.NodeLimitExceeded -> throw CaptureFailure("graph-node-limit", "Material graph exceeds ${result.maxNodes} nodes")
    }

    private fun validateEffect(value: EffectNode) = when (val result = EffectGraph.validate(value, limits.graphLimits)) {
        GraphValidationResult.Valid -> Unit
        is GraphValidationResult.DepthLimitExceeded -> throw CaptureFailure("graph-depth-limit", "Effect graph exceeds depth ${result.maxDepth}")
        is GraphValidationResult.NodeLimitExceeded -> throw CaptureFailure("graph-node-limit", "Effect graph exceeds ${result.maxNodes} nodes")
    }
}

internal class CaptureFailure(val code: String, override val message: String) : RuntimeException(message)

internal fun Float.checked(field: String): Float = takeIf(Float::isFinite)
    ?: throw CaptureFailure("non-finite-value", "$field must be finite")

internal fun RectF32.checked(field: String): RectF32 {
    left.checked("$field.left")
    top.checked("$field.top")
    right.checked("$field.right")
    bottom.checked("$field.bottom")
    return RectF32(left, top, right, bottom)
}

internal fun RRectF32.checked(field: String): RRectF32 {
    rect.checked("$field.rect")
    listOf(topLeft, topRight, bottomRight, bottomLeft).forEachIndexed { index, radius ->
        radius.x.checked("$field.radius[$index].x")
        radius.y.checked("$field.radius[$index].y")
    }
    return copy(rect = rect.copy())
}

internal fun Point2F32.checked(field: String): Point2F32 = Point2F32(
    x.checked("$field.x"),
    y.checked("$field.y"),
)

internal fun SizeF32.checked(field: String): SizeF32 = SizeF32(
    width.checked("$field.width"),
    height.checked("$field.height"),
)

internal fun Vector2F32.checked(field: String): Vector2F32 = Vector2F32(
    x.checked("$field.x"),
    y.checked("$field.y"),
)

internal fun PathF32.checked(field: String): PathF32 {
    forEachIndexed { index, segment ->
        fun point(name: String, value: Point2F32) {
            value.x.checked("$field[$index].$name.x")
            value.y.checked("$field[$index].$name.y")
        }
        when (segment) {
            is PathSegmentF32.MoveTo -> point("point", segment.point)
            is PathSegmentF32.LineTo -> point("point", segment.point)
            is PathSegmentF32.QuadTo -> { point("control", segment.control); point("point", segment.point) }
            is PathSegmentF32.CubicTo -> { point("control1", segment.control1); point("control2", segment.control2); point("point", segment.point) }
            is PathSegmentF32.ArcTo -> {
                segment.radius.x.checked("$field[$index].radius.x")
                segment.radius.y.checked("$field[$index].radius.y")
                segment.xAxisRotation.checked("$field[$index].rotation")
                point("point", segment.point)
            }
            PathSegmentF32.Close -> Unit
        }
    }
    return this
}

internal fun Matrix3x3F32.checked(field: String): Matrix3x3F32 {
    sx.checked("$field.sx")
    kx.checked("$field.kx")
    tx.checked("$field.tx")
    ky.checked("$field.ky")
    sy.checked("$field.sy")
    ty.checked("$field.ty")
    persp0.checked("$field.persp0")
    persp1.checked("$field.persp1")
    persp2.checked("$field.persp2")
    return this
}

private fun SamplingOptions.toImageSampling(): ImageSampling = when (this) {
    SamplingOptions.NEAREST -> ImageSampling.Nearest
    SamplingOptions.LINEAR -> ImageSampling.Linear
    is SamplingOptions.Cubic -> ImageSampling.Cubic(B.checked("sampling.b"), C.checked("sampling.c"))
}

private fun Float.pictureExtent(field: String): Int {
    checked(field)
    if (this > Int.MAX_VALUE.toFloat()) {
        throw CaptureFailure("scene-extent-overflow", "$field exceeds the scene extent range")
    }
    return maxOf(1, toInt())
}

private fun PaintNode.toBlendNode(): BlendNode = BlendNode.Paint(blendMode, blender)

private fun PaintNode.toEffectStack(): EffectStack = EffectStack.of(
    listOfNotNull(colorFilter, maskFilter, pathEffect, imageFilter),
)

private fun Vertices.toGeometry(bounds: RectF32?, meshProgram: MeshProgramNode? = null): GeometryNode.IndexedMesh = GeometryNode.IndexedMesh.of(
    primitiveMode = MeshPrimitiveMode.valueOf(mode.name),
    vertices = positions.map { point -> Point2F32(point.x.checked("vertices.position.x"), point.y.checked("vertices.position.y")) },
    texCoords = texCoords?.map { point -> Point2F32(point.x.checked("vertices.texcoord.x"), point.y.checked("vertices.texcoord.y")) },
    colors = colors,
    indices = indices?.toIntArray(),
    bounds = bounds?.checked("vertices.bounds"),
    meshProgram = meshProgram,
)

private fun Typeface.toTypefaceReference(): TypefaceReference = when (this) {
    is KanvasTypeface -> TypefaceReference(TypefaceId("kanvas-resource:$resourcePath"))
    else -> TypefaceReference(TypefaceId("font-name:$fontName"))
}

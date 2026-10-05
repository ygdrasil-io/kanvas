package org.graphiks.kanvas.gpu.plan

import java.util.Collections
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.mapPathFillInputF64

internal enum class W4cOriginalFrameModeV1 {
    PathOnly,
    HardPathRoot,
}

/** Non-public, issuer-bound input for a single preparation of an original scene. */
internal class W4cOriginalFrameAdmissionV1 internal constructor(
    internal val owner: W4cPathFillPlanCompiler,
    internal val scene: SceneSnapshot,
    internal val target: RenderTargetDescriptor,
    internal val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
    internal val mode: W4cOriginalFrameModeV1,
    issuerToken: Any,
) {
    private val issuerToken: Any = issuerToken

    internal fun wasIssuedBy(expectedToken: Any): Boolean = issuerToken === expectedToken
}

internal sealed interface W4cFramePreparationResultV1 {
    class MaterialRefused(refusals: List<EffectiveMaterialPlanner.Result.Refused>) : W4cFramePreparationResultV1 {
        val refusals: List<EffectiveMaterialPlanner.Result.Refused> = Collections.unmodifiableList(refusals.toList())
    }

    class Accepted(
        draws: List<W4cSealedDrawV1>,
        val materialPlanTable: MaterialPlanTable?,
        val capabilityId: String,
        val sourceTable: MaterialSourceConstructionTableV4,
        val hardPathRootFrame: W7HardPathRootFrameV1? = null,
    ) : W4cFramePreparationResultV1 {
        val draws: List<W4cSealedDrawV1> = Collections.unmodifiableList(
            draws.map { draw ->
                draw.copy(
                    pathF32 = W4cFramePreparationV1.snapshotPath(draw.pathF32),
                    transform = draw.transform.copy(),
                    scissorI32 = draw.scissorI32.copy(),
                )
            },
        )
    }

    data class Gap(val message: String) : W4cFramePreparationResultV1
    data class Invalid(val message: String) : W4cFramePreparationResultV1
    data class ResourceLimit(val message: String) : W4cFramePreparationResultV1
}

internal class W4cSealedDrawV1(
    val commandIndex: Int,
    val pathF32: PathF32,
    val transform: Matrix3x3F32,
    val material: MaterialPlanRef,
    val coordinates: MaterialCoordinatePlanV1?,
    val coordinatesV2: MaterialCoordinatePlanV2?,
    val coordinatesV4: SourceCoordinatesV4?,
    val geometryF32: PathFillGeometryF32,
    val strategy: PathFillStrategy,
    scissorI32: RectI32,
    val blend: BlendPlan,
) {
    private val scissorI32Storage: RectI32 = scissorI32.copy()
    val scissorI32: RectI32 get() = scissorI32Storage.copy()

    fun copy(
        commandIndex: Int = this.commandIndex,
        pathF32: PathF32 = this.pathF32,
        transform: Matrix3x3F32 = this.transform,
        material: MaterialPlanRef = this.material,
        coordinates: MaterialCoordinatePlanV1? = this.coordinates,
        coordinatesV2: MaterialCoordinatePlanV2? = this.coordinatesV2,
        coordinatesV4: SourceCoordinatesV4? = this.coordinatesV4,
        geometryF32: PathFillGeometryF32 = this.geometryF32,
        strategy: PathFillStrategy = this.strategy,
        scissorI32: RectI32 = this.scissorI32,
        blend: BlendPlan = this.blend,
    ): W4cSealedDrawV1 = W4cSealedDrawV1(
        commandIndex, pathF32, transform, material, coordinates, coordinatesV2, coordinatesV4,
        geometryF32, strategy, scissorI32, blend,
    )
}

/** Shared single pass over original scene commands for W4c frame recognition and preparation. */
internal object W4cFramePreparationV1 {
    private sealed interface DrawRecognition {
        data class NoOp(val frameAttemptedEdgesAfterI32: Int) : DrawRecognition
        data class MaterialRefused(
            val refusal: EffectiveMaterialPlanner.Result.Refused,
            val frameAttemptedEdgesAfterI32: Int,
        ) : DrawRecognition
        data class Accepted(
            val draw: W4cSealedDrawV1,
            val frameAttemptedEdgesAfterI32: Int,
        ) : DrawRecognition
        data class Gap(val message: String) : DrawRecognition
        data class Invalid(val message: String) : DrawRecognition
        data class ResourceLimit(val message: String) : DrawRecognition
    }

    private sealed interface ClipRecognition {
        data class Accepted(val bounds: RectI32?) : ClipRecognition
        data class Gap(val message: String) : ClipRecognition
        data class Invalid(val message: String) : ClipRecognition
    }

    internal fun prepareFrame(admission: W4cOriginalFrameAdmissionV1): W4cFramePreparationResultV1 {
        check(admission.owner.authenticates(admission)) { "W4c frame admission was not issued by its owner" }
        val hardPathRoot = admission.mode == W4cOriginalFrameModeV1.HardPathRoot
        val scene = admission.scene
        if (scene.extent != admission.target.extent || scene.colorSpace != admission.target.colorSpace) {
            return W4cFramePreparationResultV1.Invalid("Scene and target descriptors disagree")
        }
        if (scene.colorSpace != ColorSpace.SRGB) return W4cFramePreparationResultV1.Gap("W4c supports only sRGB scenes")
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val draws = mutableListOf<W4cSealedDrawV1>()
        val materialEntries = mutableListOf<MaterialPlanEntry>()
        val sources = mutableListOf<MaterialSourceConstructionV4>()
        val materialRefusals = mutableListOf<EffectiveMaterialPlanner.Result.Refused>()
        val hardPathSlots = mutableListOf<W7HardPathSlotV1>()
        val hardPathColors = mutableListOf<SolidRectDraw>()
        var frameAttemptedEdgesBeforeI32 = 0
        var elidedNoOpsI32 = 0
        var pathSourceOccurrenceI32 = 0
        if (hardPathRoot && scene.commandCount > W4cPathFillPlanCompiler.MAX_DRAWS) {
            return W4cFramePreparationResultV1.Gap("HardPath root accepts at most 512 original commands")
        }
        for ((commandIndex, command) in scene.withIndex()) {
            when (command) {
                is SceneCommand.Draw -> {
                    if (draws.size + materialRefusals.size + elidedNoOpsI32 == W4cPathFillPlanCompiler.MAX_DRAWS) {
                        return W4cFramePreparationResultV1.Gap("W4c accepts at most 512 visual path draws")
                    }
                    when (
                        val draw = recognizeDraw(
                            node = command.node,
                            commandIndex = commandIndex,
                            targetBounds = targetBounds,
                            frameAttemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
                            materialEntries = materialEntries,
                            sources = sources,
                            runtimeCatalog = admission.runtimeCatalog,
                        )
                    ) {
                        is DrawRecognition.NoOp -> {
                            elidedNoOpsI32++
                            if (hardPathRoot) hardPathSlots += W7HardPathSlotV1(
                                commandIndex, command.canonicalId, W7HardPathSlotKindV1.NoOp,
                                attemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
                                attemptedEdgesAfterI32 = draw.frameAttemptedEdgesAfterI32,
                            )
                            frameAttemptedEdgesBeforeI32 = draw.frameAttemptedEdgesAfterI32
                        }
                        is DrawRecognition.MaterialRefused -> {
                            materialRefusals += draw.refusal
                            if (hardPathRoot) hardPathSlots += W7HardPathSlotV1(
                                commandIndex, command.canonicalId, W7HardPathSlotKindV1.NoOp,
                                attemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
                                attemptedEdgesAfterI32 = draw.frameAttemptedEdgesAfterI32,
                            )
                            frameAttemptedEdgesBeforeI32 = draw.frameAttemptedEdgesAfterI32
                        }
                        is DrawRecognition.Accepted -> {
                            draws += draw.draw
                            if (hardPathRoot) {
                                hardPathSlots += W7HardPathSlotV1(
                                    commandIndex, command.canonicalId, W7HardPathSlotKindV1.Path,
                                    path = draw.draw,
                                    attemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
                                    attemptedEdgesAfterI32 = draw.frameAttemptedEdgesAfterI32,
                                    pathSourceOccurrenceI32 = pathSourceOccurrenceI32++,
                                )
                            }
                            frameAttemptedEdgesBeforeI32 = draw.frameAttemptedEdgesAfterI32
                        }
                        is DrawRecognition.Gap -> return W4cFramePreparationResultV1.Gap(draw.message)
                        is DrawRecognition.Invalid -> return W4cFramePreparationResultV1.Invalid(draw.message)
                        is DrawRecognition.ResourceLimit -> return W4cFramePreparationResultV1.ResourceLimit(draw.message)
                    }
                }
                is SceneCommand.DrawColor -> {
                    if (!hardPathRoot) return W4cFramePreparationResultV1.Gap("Scene command is outside W4c")
                    when (val draw = W3DrawColorAdmissionV1.recognize(
                        command, commandIndex, targetBounds, CompositionDomain.LINEAR,
                    )) {
                        is W3DrawColorAdmissionResultV1.Accepted -> {
                            hardPathColors += draw.draw
                            hardPathSlots += W7HardPathSlotV1(
                                commandIndex, command.canonicalId, W7HardPathSlotKindV1.DrawColor,
                                color = draw.draw,
                                attemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
                                attemptedEdgesAfterI32 = frameAttemptedEdgesBeforeI32,
                            )
                        }
                        is W3DrawColorAdmissionResultV1.Gap -> return W4cFramePreparationResultV1.Gap(draw.diagnostic.message)
                        is W3DrawColorAdmissionResultV1.Invalid -> return W4cFramePreparationResultV1.Invalid(draw.diagnostic.message)
                    }
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) {
                    return W4cFramePreparationResultV1.Invalid("Transform metadata is non-finite")
                }
                is SceneCommand.SetClip -> if (!finiteMetadataClip(command.clip)) {
                    return W4cFramePreparationResultV1.Invalid("Clip metadata is non-finite")
                }
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) {
                    return W4cFramePreparationResultV1.Invalid("Annotation bounds are non-finite")
                }
                else -> return W4cFramePreparationResultV1.Gap("Scene command is outside W4c")
            }
        }
        if (materialRefusals.isNotEmpty()) return W4cFramePreparationResultV1.MaterialRefused(materialRefusals)
        if (draws.isEmpty() && elidedNoOpsI32 == 0) {
            return W4cFramePreparationResultV1.Gap("W4c requires at least one visual path draw")
        }
        if (hardPathRoot && (hardPathColors.isEmpty() || draws.none { it.blend is BlendPlan.DestinationReadV1 })) {
            return W4cFramePreparationResultV1.Gap("HardPath root requires a retained DrawColor and DARKEN path")
        }
        val pending = sources.any { it.pending }
        val selectedDraws = if (pending) draws.mapIndexed { ordinal, draw -> draw.copy(material = MaterialPlanRef(ordinal)) } else draws
        val materialPlanTable = materialEntries.takeIf { it.isNotEmpty() && !pending }?.let(MaterialPlanTable::of)
        val capabilityId = if (
            elidedNoOpsI32 > 0 || pending || materialEntries.any { it.stopSlab != null } ||
            selectedDraws.any { it.blend != BlendPlan.LegacySrcOverV1 }
        ) W4cPathFillPlanCompiler.W5B_CAPABILITY_ID else W4cPathFillPlanCompiler.CAPABILITY_ID
        val sourceTable = (MaterialSourceConstructionTableV4.of(sources) as SourceConstructionResultV4.Built).value
        if (hardPathRoot && (pending || materialPlanTable == null)) {
            return W4cFramePreparationResultV1.Gap("HardPath root requires resolved solid path materials")
        }
        val rootFrame = if (hardPathRoot) W7HardPathRootFrameV1(
            admission,
            hardPathSlots,
            requireNotNull(materialPlanTable),
            sourceTable,
            frameAttemptedEdgesBeforeI32,
        ) else null
        return W4cFramePreparationResultV1.Accepted(selectedDraws, materialPlanTable, capabilityId, sourceTable, rootFrame)
    }

    /** Whole-frame structural preclassification; geometry preparation starts only after this accepts. */
    internal fun qualifiesHardPathRoot(scene: SceneSnapshot, target: RenderTargetDescriptor): Boolean {
        if (scene.extent != target.extent || scene.colorSpace != ColorSpace.SRGB ||
            target.colorSpace != ColorSpace.SRGB || target.compositionDomain != CompositionDomain.LINEAR ||
            scene.commandCount > W4cPathFillPlanCompiler.MAX_DRAWS) return false
        var hasColor = false
        var hasDarkenPath = false
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        for (command in scene) {
            when (command) {
                is SceneCommand.DrawColor -> {
                    if (!W3DrawColorAdmissionV1.isFinite(command.transform) || !command.transform.isIdentity ||
                        command.mode != BlendMode.SRC_OVER) return false
                    val clip = when (val recognized = W3DrawColorAdmissionV1.recognizeClip(command.clip)) {
                        is W3DrawColorAdmissionV1.ClipRecognition.Accepted -> recognized.bounds
                        else -> return false
                    }
                    if (clip != null && W3DrawColorAdmissionV1.intersect(targetBounds, clip) == null) return false
                    hasColor = true
                }
                is SceneCommand.Draw -> {
                    val node = command.node
                    val path = (node.geometry as? GeometryNode.Path)?.path ?: return false
                    if (!finite(path) || !finite(node.transform) || node.origin != DrawOrigin.PATH ||
                        path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD) ||
                        node.coverage != CoverageRequest.HARD_EDGE ||
                        !(node.transform.isIdentity || node.transform.isScaleTranslate()) ||
                        recognizeClip(node.clip) !is ClipRecognition.Accepted || !supportsSolidFill(node)) return false
                    val paint = node.paint ?: return false
                    val solidMaterial = node.material as? MaterialNode.Solid ?: return false
                    if (paint.shader != null || paint.colorFilter != null || paint.maskFilter != null ||
                        paint.pathEffect != null || paint.imageFilter != null || paint.blender != null ||
                        paint.style != PaintStyleNode.FILL || node.resource != null || node.operationBlendMode != null ||
                        node.effects != org.graphiks.kanvas.render.ir.EffectStack.Empty ||
                        solidMaterial.color != paint.color || !materialMatchesPaintAuthority(node)) return false
                    val blendMode = when (val blend = node.blend) {
                        BlendNode.SrcOver -> BlendMode.SRC_OVER
                        is BlendNode.Mode -> blend.mode
                        is BlendNode.Paint -> if (blend.blender == null) blend.mode else return false
                        is BlendNode.Custom -> return false
                    }
                    if (blendMode !in setOf(BlendMode.SRC_OVER, BlendMode.DARKEN, BlendMode.DST) ||
                        paint.blendMode != blendMode) return false
                    if (blendMode == BlendMode.DARKEN) hasDarkenPath = true
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return false
                is SceneCommand.SetClip -> if (!finiteMetadataClip(command.clip)) return false
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return false
                else -> return false
            }
        }
        return hasColor && hasDarkenPath
    }

    private fun recognizeDraw(
        node: DrawNode,
        commandIndex: Int,
        targetBounds: RectI32,
        frameAttemptedEdgesBeforeI32: Int,
        materialEntries: MutableList<MaterialPlanEntry>,
        sources: MutableList<MaterialSourceConstructionV4>,
        runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
    ): DrawRecognition {
        val geometry = node.geometry as? GeometryNode.Path
            ?: return DrawRecognition.Gap("Draw geometry is outside W4c")
        if (!finite(geometry.path)) return DrawRecognition.Invalid("Path geometry is non-finite")
        if (!finite(node.transform)) return DrawRecognition.Invalid("Draw transform is non-finite")
        val clip = when (val recognized = recognizeClip(node.clip)) {
            is ClipRecognition.Accepted -> recognized.bounds
            is ClipRecognition.Gap -> return DrawRecognition.Gap(recognized.message)
            is ClipRecognition.Invalid -> return DrawRecognition.Invalid(recognized.message)
        }
        if (node.origin != DrawOrigin.PATH) return DrawRecognition.Gap("Path provenance is outside W4c")
        if (geometry.path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD)) {
            return DrawRecognition.Gap("Inverse path fills are outside W4c")
        }
        if (node.coverage != CoverageRequest.HARD_EDGE) return DrawRecognition.Gap("Antialiased path coverage is outside W4c")
        if (!(node.transform.isIdentity || node.transform.isScaleTranslate())) {
            return DrawRecognition.Gap("Transform is outside W4c")
        }
        if (!supportsSolidFill(node)) return DrawRecognition.Gap("Draw material, paint, blend, or effect is outside W4c")

        val pathSnapshot = snapshotPath(geometry.path)
        val input = try {
            node.transform.mapPathFillInputF64(pathSnapshot)
        } catch (_: IllegalArgumentException) {
            return DrawRecognition.Invalid("Path transform could not produce finite device geometry")
        }
        return when (
            val prepared = preparePathFillGeometryF32(
                inputF64 = input,
                frameAttemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
            )
        ) {
            is PathFillPreparationResult.InvalidScene ->
                DrawRecognition.Invalid("Path preparation found non-finite device geometry")
            is PathFillPreparationResult.ResourceLimitExceeded ->
                DrawRecognition.ResourceLimit("Path preparation exceeded a W4c resource limit")
            is PathFillPreparationResult.Empty -> DrawRecognition.Gap("Path preparation retained no fill contour")
            is PathFillPreparationResult.Ready -> {
                val geometryF32 = prepared.geometryF32
                val strategy = when {
                    geometryF32.copyDirectTriangleF32OrNull() != null -> PathFillStrategy.DirectTriangle
                    geometryF32.copyStencilEdgeFanF32OrNull() != null -> {
                        if (
                            geometryF32.fillRule == FillRule.WINDING &&
                            geometryF32.emittedNonZeroClosedEdgeCountI32 > W4cPathFillPlanCompiler.MAX_WINDING_STENCIL_EDGES
                        ) {
                            return DrawRecognition.ResourceLimit("Winding path exceeds the W4c stencil edge limit")
                        }
                        PathFillStrategy.StencilCover
                    }
                    else -> return DrawRecognition.ResourceLimit("Path preparation returned no renderable payload")
                }
                val targetScissor = intersect(geometryF32.copyConservativeScissorI32(), targetBounds)
                    ?: return DrawRecognition.Gap("Path is outside the target")
                val scissor = clip?.let { intersect(targetScissor, it) } ?: targetScissor
                if (scissor.isEmpty) return DrawRecognition.Gap("Path is fully clipped out")
                val attemptedAfter = try {
                    Math.addExact(frameAttemptedEdgesBeforeI32, prepared.attemptedEdgeCountI32)
                } catch (_: ArithmeticException) {
                    return DrawRecognition.ResourceLimit("Frame attempted-edge count overflowed")
                }
                val source = when (
                    val planned = EffectiveMaterialPlanner.normalizeSourcesV4(
                        node,
                        W4cPathFillPlanCompiler.FORMAT.blendTargetClampV1(),
                        scissor,
                        runtimeCatalog = runtimeCatalog,
                    )
                ) {
                    EffectiveMaterialPlanner.SourceNormalizationV4.NoOp -> return DrawRecognition.NoOp(attemptedAfter)
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Refused -> return DrawRecognition.MaterialRefused(
                        EffectiveMaterialPlanner.Result.Refused(planned.diagnosticCode), attemptedAfter,
                    )
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Source -> planned.captured
                }
                sources += source
                val resolved = source.resolvedSource
                DrawRecognition.Accepted(
                    W4cSealedDrawV1(
                        commandIndex = commandIndex,
                        pathF32 = pathSnapshot,
                        transform = node.transform.copy(),
                        material = if (resolved == null) MaterialPlanRef(sources.lastIndex)
                            else appendMaterialPlan(materialEntries, resolved.table, resolved.root),
                        coordinates = MaterialCoordinatePlanV1.fromCtm(node.transform),
                        coordinatesV2 = resolved?.table?.coordinatesV2(resolved.root),
                        coordinatesV4 = if (source.pending) source.coordinates else resolved?.table?.coordinatesV4(resolved.root),
                        geometryF32 = geometryF32,
                        strategy = strategy,
                        scissorI32 = scissor.copy(),
                        blend = if (source.blend is BlendPlan.FixedFunctionV1 && source.blend.mode == BlendMode.SRC_OVER)
                            BlendPlan.LegacySrcOverV1 else source.blend,
                    ),
                    attemptedAfter,
                )
            }
        }
    }

    private fun supportsSolidFill(node: DrawNode): Boolean {
        if (
            !colorFilterEffectsMatchPaint(node) ||
            node.resource != null ||
            node.operationBlendMode != null ||
            !w4cBlend(node.blend)
        ) return false
        val paint = node.paint ?: return false
        return finite(paint) && paint.blender == null && paint.maskFilter == null && paint.pathEffect == null &&
            paint.imageFilter == null && paint.style == PaintStyleNode.FILL && materialMatchesPaintAuthority(node)
    }

    private fun recognizeClip(clip: ClipStackNode): ClipRecognition = when (clip) {
        ClipStackNode.Empty -> ClipRecognition.Accepted(null)
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            when {
                !finite(bounds) -> ClipRecognition.Invalid("Clip bounds are non-finite")
                clip.antiAlias -> ClipRecognition.Gap("Antialiased clip is outside W4c")
                else -> integralRect(bounds)?.let(ClipRecognition::Accepted)
                    ?: ClipRecognition.Gap("Clip is not a non-empty I32 device rectangle")
            }
        }
        is ClipStackNode.Operations -> if (!finiteMetadataClip(clip)) {
            ClipRecognition.Invalid("Complex clip metadata is non-finite")
        } else ClipRecognition.Gap("Complex clips are outside W4c")
    }

    internal fun snapshotPath(path: PathF32): PathF32 = PathBuilder(path.fillRule).addPath(path).build()

    private fun integralRect(bounds: RectF32): RectI32? {
        if (!finite(bounds)) return null
        val values = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val converted = values.map { value ->
            val long = value.toLong()
            if (long.toFloat() != value || long !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            long.toInt()
        }
        return RectI32(converted[0], converted[1], converted[2], converted[3]).takeUnless { it.isEmpty64() }
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }

    private fun finiteMetadataClip(clip: ClipStackNode): Boolean = when (clip) {
        ClipStackNode.Empty -> true
        is ClipStackNode.DeviceRect -> finite(clip.copyBounds())
        is ClipStackNode.Operations -> clip.all { finiteMetadataClipGeometry(it.geometry) }
    }

    private fun finiteMetadataClipGeometry(geometry: GeometryNode): Boolean = when (geometry) {
        is GeometryNode.Rect -> finite(geometry.copyBounds())
        is GeometryNode.RRect -> finite(geometry.copyShape())
        is GeometryNode.Path -> finite(geometry.path)
        else -> false
    }

    private fun finite(shape: RRectF32): Boolean = finite(shape.rect) && listOf(
        shape.topLeft.x, shape.topLeft.y, shape.topRight.x, shape.topRight.y,
        shape.bottomRight.x, shape.bottomRight.y, shape.bottomLeft.x, shape.bottomLeft.y,
    ).all(Float::isFinite)

    private fun finite(path: PathF32): Boolean = path.all { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> finite(segment.point)
            is PathSegmentF32.LineTo -> finite(segment.point)
            is PathSegmentF32.QuadTo -> finite(segment.control) && finite(segment.point)
            is PathSegmentF32.CubicTo -> finite(segment.control1) && finite(segment.control2) && finite(segment.point)
            is PathSegmentF32.ArcTo -> finite(segment.point) && segment.radius.x.isFinite() &&
                segment.radius.y.isFinite() && segment.xAxisRotation.isFinite()
            PathSegmentF32.Close -> true
        }
    }

    private fun finite(paint: PaintNode): Boolean = paint.strokeWidth.isFinite() && paint.strokeMiter.isFinite()
    private fun finite(point: Point2F32): Boolean = point.x.isFinite() && point.y.isFinite()
    private fun finite(bounds: RectF32): Boolean = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)
    private fun finite(matrix: Matrix3x3F32): Boolean = listOf(
        matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty,
        matrix.persp0, matrix.persp1, matrix.persp2,
    ).all(Float::isFinite)

    private fun w4cBlend(blend: BlendNode): Boolean = when (blend) {
        BlendNode.SrcOver -> true
        is BlendNode.Mode -> true
        is BlendNode.Paint -> blend.blender == null
        is BlendNode.Custom -> false
    }

    private fun appendMaterialPlan(
        entries: MutableList<MaterialPlanEntry>,
        incoming: MaterialPlanTable,
        root: MaterialPlanRef,
    ): MaterialPlanRef {
        val offset = entries.size
        incoming.entries().forEach { entry -> entries += entry }
        return MaterialPlanRef(offset + root.indexI32)
    }

    private fun materialMatchesPaintAuthority(node: DrawNode): Boolean {
        val paint = node.paint ?: return false
        val paintMaterial = paint.shader ?: MaterialNode.Solid(paint.color)
        return node.material.canonicalId == paintMaterial.canonicalId
    }
}

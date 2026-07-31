package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedMaskFilterLowerer
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedClipSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeEntry
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScope
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeId
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeState
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMaskFilterLowering
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMaskFilterPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMatrixSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedPaintSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedPaintStyle
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedRectSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedStrokeCap
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedStrokeJoin
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Collections

data class GPUPreparedCompositeCaptureLimits(
    val maxRecursionDepth: Int = 10,
    val maxNestingDepth: Int = 10,
    val maxExpandedOps: Int = 10000,
) {
    init {
        require(maxRecursionDepth >= 0)
        require(maxNestingDepth >= 0)
        require(maxExpandedOps >= 0)
    }
}

enum class GPUPreparedPathVerbSnapshot {
    Move,
    Line,
    Quad,
    Cubic,
    ArcTo,
    Close,
}

enum class GPUPreparedPathFillSnapshot {
    Winding,
    EvenOdd,
    InverseWinding,
    InverseEvenOdd,
}

data class GPUPreparedPointSnapshot(
    val xBits: Int,
    val yBits: Int,
)

sealed interface GPUPreparedGeometrySnapshot {
    fun identityFragment(): String

    data class RectGeometry(
        val rect: GPUPreparedRectSnapshot,
    ) : GPUPreparedGeometrySnapshot {
        override fun identityFragment(): String = canonicalHash("rect", rect.identityFragment())
    }

    data class RRectGeometry(
        val rect: GPUPreparedRectSnapshot,
        val topLeft: GPUPreparedPointSnapshot,
        val topRight: GPUPreparedPointSnapshot,
        val bottomRight: GPUPreparedPointSnapshot,
        val bottomLeft: GPUPreparedPointSnapshot,
    ) : GPUPreparedGeometrySnapshot {
        override fun identityFragment(): String = canonicalHash(
            "rrect",
            rect.identityFragment(),
            topLeft.identityFragment(),
            topRight.identityFragment(),
            bottomRight.identityFragment(),
            bottomLeft.identityFragment(),
        )
    }

    class PathGeometry(
        val fill: GPUPreparedPathFillSnapshot,
        verbs: List<GPUPreparedPathVerbSnapshot>,
        points: List<GPUPreparedPointSnapshot>,
    ) : GPUPreparedGeometrySnapshot {
        val verbs: List<GPUPreparedPathVerbSnapshot> =
            Collections.unmodifiableList(verbs.toList())
        val points: List<GPUPreparedPointSnapshot> =
            Collections.unmodifiableList(points.toList())

        override fun identityFragment(): String = canonicalHash(
            buildList {
                add("path")
                add(fill.name)
                add(verbs.size.toString())
                verbs.forEach { add(it.name) }
                add(points.size.toString())
                points.forEach { add(it.identityFragment()) }
            },
        )
    }
}

sealed interface GPUPreparedOperationSnapshot {
    fun identityFragment(): String

    data class Draw(
        val operationIndex: Int,
        val provenance: String,
        val geometry: GPUPreparedGeometrySnapshot,
        val paint: GPUPreparedPaintSnapshot,
        val transform: GPUPreparedMatrixSnapshot,
        val clip: GPUPreparedClipSnapshot,
        val maskFilterPlan: GPUPreparedMaskFilterPlan? = null,
    ) : GPUPreparedOperationSnapshot {
        override fun identityFragment(): String = canonicalHash(
            "draw",
            operationIndex.toString(),
            provenance,
            geometry.identityFragment(),
            paint.identityFragment(),
            maskFilterPlan?.identityFragment() ?: "no-mask-filter",
            transform.identityFragment(),
            clip.identityFragment(),
        )
    }

    data class SetTransform(
        val operationIndex: Int,
        val provenance: String,
        val matrix: GPUPreparedMatrixSnapshot,
    ) : GPUPreparedOperationSnapshot {
        override fun identityFragment(): String = canonicalHash(
            "set-transform",
            operationIndex.toString(),
            provenance,
            matrix.identityFragment(),
        )
    }

    data class SetClip(
        val operationIndex: Int,
        val provenance: String,
        val clip: GPUPreparedClipSnapshot,
    ) : GPUPreparedOperationSnapshot {
        override fun identityFragment(): String = canonicalHash(
            "set-clip",
            operationIndex.toString(),
            provenance,
            clip.identityFragment(),
        )
    }
}

/** Captured operation with typed, immutable semantics. */
data class GPUPreparedCapturedOperation(
    val sourceOperationIndex: Int,
    val snapshot: GPUPreparedOperationSnapshot,
    val identity: String,
) {
    init {
        require(sourceOperationIndex >= 0) {
            "sourceOperationIndex must be >= 0"
        }
        check(identity == snapshot.identityFragment()) {
            "Captured operation identity does not match its semantic snapshot"
        }
    }
}

/** Immutable composite capture result. */
class GPUPreparedCompositeCapture(
    val rootScopeId: GPUPreparedCompositeScopeId,
    scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
    expandedOperations: List<GPUPreparedCapturedOperation>,
    val identity: String,
) {
    val scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope> =
        Collections.unmodifiableMap(LinkedHashMap(scopes))
    val expandedOperations: List<GPUPreparedCapturedOperation> =
        Collections.unmodifiableList(expandedOperations.toList())

    init {
        val root = this.scopes[rootScopeId]
            ?: throw IllegalArgumentException("Root scope ${rootScopeId.value} is missing")
        require(root.parentId == null) { "Root scope must not have a parent" }
        this.scopes.forEach { (key, scope) ->
            require(key == scope.id) {
                "Scope map key ${key.value} does not match scope id ${scope.id.value}"
            }
            scope.parentId?.let { parentId ->
                require(parentId in this.scopes) {
                    "Scope ${scope.id.value} references missing parent ${parentId.value}"
                }
            }
            scope.entries.forEach { entry ->
                when (entry) {
                    is GPUPreparedCompositeEntry.Draw -> require(
                        entry.operationIndex in this.expandedOperations.indices,
                    ) {
                        "Scope ${scope.id.value} references missing operation ${entry.operationIndex}"
                    }
                    is GPUPreparedCompositeEntry.Scope -> {
                        val child = this.scopes[entry.id]
                            ?: throw IllegalArgumentException(
                                "Scope ${scope.id.value} references missing child ${entry.id.value}",
                            )
                        require(child.parentId == scope.id) {
                            "Scope ${child.id.value} parent does not match containing scope ${scope.id.value}"
                        }
                    }
                }
            }
        }
        check(
            identity == computeCaptureIdentity(
                rootScopeId = rootScopeId,
                operations = this.expandedOperations,
                scopes = this.scopes,
            ),
        ) {
            "Composite capture identity does not match its contents"
        }
    }
}

sealed interface GPUPreparedCompositeCaptureResult {
    data class Ready(val capture: GPUPreparedCompositeCapture) :
        GPUPreparedCompositeCaptureResult

    class Refused(
        val code: String,
        val operationIndex: Int?,
        facts: Map<String, String>,
    ) : GPUPreparedCompositeCaptureResult {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

internal object GPUPreparedCompositeCapturer {

    fun capture(
        operations: List<DisplayOp>,
        limits: GPUPreparedCompositeCaptureLimits,
    ): GPUPreparedCompositeCaptureResult {
        val context = CaptureContext(limits)
        return try {
            val root = context.processTopLevel(operations)
            val scopes = context.finalizeScopes()
            val processedOperations = context.finalizeOperations()
            GPUPreparedCompositeCaptureResult.Ready(
                GPUPreparedCompositeCapture(
                    rootScopeId = root.id,
                    scopes = scopes,
                    expandedOperations = processedOperations,
                    identity = computeCaptureIdentity(root.id, processedOperations, scopes),
                ),
            )
        } catch (refusal: CaptureRefusedException) {
            GPUPreparedCompositeCaptureResult.Refused(
                code = refusal.code,
                operationIndex = refusal.operationIndex,
                facts = Collections.unmodifiableMap(LinkedHashMap(refusal.facts)),
            )
        }
    }

    private class CaptureContext(
        private val limits: GPUPreparedCompositeCaptureLimits,
    ) {
        private val scopes =
            linkedMapOf<GPUPreparedCompositeScopeId, MutableCaptureScope>()
        private val expandedOperations = mutableListOf<GPUPreparedCapturedOperation>()
        private val activePictureIds = mutableSetOf<Int>()
        private val provenancePath = mutableListOf("root")
        private var scopeIdCounter = 0
        private var visitedOperationCount = 0

        fun processTopLevel(operations: List<DisplayOp>): MutableCaptureScope {
            val root = MutableCaptureScope(
                id = nextScopeId(),
                parentId = null,
                sourceKind = GPUPreparedCompositeScopeKind.Root,
                provenance = "root",
                state = GPUPreparedCompositeScopeState(
                    bounds = null,
                    paint = null,
                    transform = Matrix33.identity().toSnapshot(-1),
                    clip = GPUPreparedClipSnapshot.WideOpen,
                ),
            )
            scopes[root.id] = root
            processOperations(operations, root)
            return root
        }

        private fun processOperations(
            operations: List<DisplayOp>,
            parentScope: MutableCaptureScope,
            pictureReplay: PictureReplayState? = null,
        ): Int {
            var operationIndex = 0
            while (operationIndex < operations.size) {
                visitOperation(operationIndex)
                val sourceOperation = operations[operationIndex]
                val operation = pictureReplay?.let { replay ->
                    sourceOperation.withPictureReplayState(
                        outerTransform = replay.transform,
                        enclosingClip = replay.enclosingClip,
                    )
                } ?: sourceOperation
                when (operation) {
                    is DisplayOp.DrawPicture ->
                        processPicture(operation, operationIndex, parentScope)

                    is DisplayOp.BeginLayer -> {
                        val layerDepth = layerDepth(parentScope) + 1
                        if (layerDepth > limits.maxNestingDepth) {
                            refuse(
                                GPUPreparedCompositeRefusalCodes.LAYER_BUDGET,
                                operationIndex,
                                mapOf(
                                    "depth" to layerDepth.toString(),
                                    "limit" to limits.maxNestingDepth.toString(),
                                ),
                            )
                        }
                        val state = snapshotLayerState(operation, operationIndex)
                        val layer = MutableCaptureScope(
                            id = nextScopeId(),
                            parentId = parentScope.id,
                            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
                            provenance = currentProvenance("layer", operationIndex),
                            saveOperationIndex = operationIndex,
                            state = state,
                        )
                        scopes[layer.id] = layer
                        parentScope.entries += GPUPreparedCompositeEntry.Scope(layer.id)
                        provenancePath += "layer[$operationIndex]"
                        val endIndex = try {
                            processOperations(
                                operations = operations.subList(operationIndex + 1, operations.size),
                                parentScope = layer,
                                pictureReplay = pictureReplay?.copy(
                                    enclosingClip = ClipStack.WideOpen,
                                ),
                            )
                        } finally {
                            provenancePath.removeLast()
                        }
                        val absoluteEndIndex = operationIndex + 1 + endIndex
                        if (absoluteEndIndex >= operations.size ||
                            operations[absoluteEndIndex] !is DisplayOp.EndLayer
                        ) {
                            refuse(
                                GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED,
                                operationIndex,
                                mapOf("reason" to "unclosed BeginLayer"),
                            )
                        }
                        layer.restoreOperationIndex = absoluteEndIndex
                        operationIndex = absoluteEndIndex
                    }

                    DisplayOp.EndLayer -> {
                        if (parentScope.sourceKind != GPUPreparedCompositeScopeKind.SaveLayer) {
                            refuse(
                                GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED,
                                operationIndex,
                                mapOf("reason" to "orphan EndLayer outside a saveLayer scope"),
                            )
                        }
                        return operationIndex
                    }

                    else -> appendOperation(operationIndex, operation, parentScope)
                }
                operationIndex++
            }
            return operationIndex
        }

        private fun processPicture(
            operation: DisplayOp.DrawPicture,
            operationIndex: Int,
            parentScope: MutableCaptureScope,
        ) {
            val pictureId = operation.picture.uniqueID
            if (pictureId in activePictureIds) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.PICTURE_CYCLE,
                    operationIndex,
                    mapOf("depth" to activePictureIds.size.toString()),
                )
            }
            if (activePictureIds.size >= limits.maxRecursionDepth) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.PICTURE_BUDGET,
                    operationIndex,
                    mapOf(
                        "depth" to activePictureIds.size.toString(),
                        "limit" to limits.maxRecursionDepth.toString(),
                    ),
                )
            }

            provenancePath += "picture[$operationIndex]"
            activePictureIds += pictureId
            try {
                if (operation.paint == null) {
                    processOperations(
                        operations = operation.picture.ops,
                        parentScope = parentScope,
                        pictureReplay = PictureReplayState(
                            transform = operation.transform,
                            enclosingClip = operation.clip,
                        ),
                    )
                } else {
                    val scope = MutableCaptureScope(
                        id = nextScopeId(),
                        parentId = parentScope.id,
                        sourceKind = GPUPreparedCompositeScopeKind.PaintedPicture,
                        provenance = currentProvenance("scope", operationIndex),
                        saveOperationIndex = operationIndex,
                        restoreOperationIndex = operationIndex,
                        state = GPUPreparedCompositeScopeState(
                            bounds = operation.picture.cullRect.toSnapshot(
                                GPUPreparedCompositeRefusalCodes.OPERATION,
                                operationIndex,
                            ),
                            paint = operation.paint.toSnapshot(operationIndex),
                            transform = operation.transform.toSnapshot(operationIndex),
                            clip = operation.clip.toSnapshot(operationIndex),
                        ),
                    )
                    scopes[scope.id] = scope
                    parentScope.entries += GPUPreparedCompositeEntry.Scope(scope.id)
                    processOperations(operation.picture.ops, scope)
                }
            } finally {
                activePictureIds -= pictureId
                provenancePath.removeLast()
            }
        }

        private fun snapshotLayerState(
            operation: DisplayOp.BeginLayer,
            operationIndex: Int,
        ): GPUPreparedCompositeScopeState {
            return GPUPreparedCompositeScopeState(
                bounds = operation.rec.bounds?.toSnapshot(
                    GPUPreparedCompositeRefusalCodes.LAYER_BOUNDS,
                    operationIndex,
                ),
                paint = operation.rec.paint?.toSnapshot(operationIndex),
                transform = operation.transform.toSnapshot(operationIndex),
                clip = (operation.rec.compositeClip ?: ClipStack.WideOpen)
                    .toSnapshot(operationIndex),
                backdropRequired = operation.rec.backdrop != null,
            )
        }

        private fun appendOperation(
            operationIndex: Int,
            operation: DisplayOp,
            scope: MutableCaptureScope,
        ) {
            val provenance = currentProvenance("op", operationIndex)
            val snapshot = when (operation) {
                is DisplayOp.DrawRect -> GPUPreparedOperationSnapshot.Draw(
                    operationIndex = operationIndex,
                    provenance = provenance,
                    geometry = GPUPreparedGeometrySnapshot.RectGeometry(
                        operation.rect.toSnapshot(
                            GPUPreparedCompositeRefusalCodes.OPERATION,
                            operationIndex,
                        ),
                    ),
                    paint = operation.paint.toSnapshot(operationIndex),
                    transform = operation.transform.toSnapshot(operationIndex),
                    clip = operation.clip.toSnapshot(operationIndex),
                    maskFilterPlan = processMaskFilter(operation.paint, operationIndex),
                )

                is DisplayOp.DrawRRect -> GPUPreparedOperationSnapshot.Draw(
                    operationIndex = operationIndex,
                    provenance = provenance,
                    geometry = operation.rrect.toSnapshot(operationIndex),
                    paint = operation.paint.toSnapshot(operationIndex),
                    transform = operation.transform.toSnapshot(operationIndex),
                    clip = operation.clip.toSnapshot(operationIndex),
                    maskFilterPlan = processMaskFilter(operation.paint, operationIndex),
                )

                is DisplayOp.DrawPath -> GPUPreparedOperationSnapshot.Draw(
                    operationIndex = operationIndex,
                    provenance = provenance,
                    geometry = operation.path.toSnapshot(operationIndex),
                    paint = operation.paint.toSnapshot(operationIndex),
                    transform = operation.transform.toSnapshot(operationIndex),
                    clip = operation.clip.toSnapshot(operationIndex),
                    maskFilterPlan = processMaskFilter(operation.paint, operationIndex),
                )

                is DisplayOp.SetTransform -> GPUPreparedOperationSnapshot.SetTransform(
                    operationIndex = operationIndex,
                    provenance = provenance,
                    matrix = operation.matrix.toSnapshot(operationIndex),
                )

                is DisplayOp.SetClip -> GPUPreparedOperationSnapshot.SetClip(
                    operationIndex = operationIndex,
                    provenance = provenance,
                    clip = operation.clip.toSnapshot(operationIndex),
                )

                else -> refuse(
                    GPUPreparedCompositeRefusalCodes.OPERATION,
                    operationIndex,
                    mapOf("operation" to operation::class.qualifiedName.orEmpty()),
                )
            }
            val expandedIndex = expandedOperations.size
            val identity = snapshot.identityFragment()
            expandedOperations += GPUPreparedCapturedOperation(
                sourceOperationIndex = operationIndex,
                snapshot = snapshot,
                identity = identity,
            )
            scope.entries += GPUPreparedCompositeEntry.Draw(expandedIndex)
        }

        private fun visitOperation(operationIndex: Int) {
            visitedOperationCount++
            if (visitedOperationCount > limits.maxExpandedOps) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.PICTURE_BUDGET,
                    operationIndex,
                    mapOf(
                        "total" to visitedOperationCount.toString(),
                        "limit" to limits.maxExpandedOps.toString(),
                    ),
                )
            }
        }

        private fun layerDepth(scope: MutableCaptureScope): Int {
            var depth = 0
            var current: MutableCaptureScope? = scope
            while (current != null) {
                if (current.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer) {
                    depth++
                }
                current = current.parentId?.let(scopes::get)
            }
            return depth
        }

        private fun currentProvenance(kind: String, operationIndex: Int): String =
            (provenancePath + "$kind[$operationIndex]").joinToString("/")

        fun finalizeOperations(): List<GPUPreparedCapturedOperation> =
            Collections.unmodifiableList(expandedOperations.toList())

        fun finalizeScopes(): Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope> {
            val result = linkedMapOf<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>()
            scopes.forEach { (id, mutable) ->
                result[id] = GPUPreparedCompositeScope(
                    id = mutable.id,
                    parentId = mutable.parentId,
                    saveOperationIndex = mutable.saveOperationIndex,
                    restoreOperationIndex = mutable.restoreOperationIndex,
                    entries = Collections.unmodifiableList(mutable.entries.toList()),
                    sourceKind = mutable.sourceKind,
                    provenance = mutable.provenance,
                    state = mutable.state,
                )
            }
            return Collections.unmodifiableMap(result)
        }

        private fun nextScopeId(): GPUPreparedCompositeScopeId =
            GPUPreparedCompositeScopeId("scope_${++scopeIdCounter}")

        private fun Rect.toSnapshot(code: String, operationIndex: Int): GPUPreparedRectSnapshot {
            if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
                refuse(code, operationIndex, mapOf("reason" to "non-finite rectangle"))
            }
            return GPUPreparedRectSnapshot(
                leftBits = left.toRawBits(),
                topBits = top.toRawBits(),
                rightBits = right.toRawBits(),
                bottomBits = bottom.toRawBits(),
            )
        }

        private fun RRect.toSnapshot(operationIndex: Int): GPUPreparedGeometrySnapshot.RRectGeometry =
            GPUPreparedGeometrySnapshot.RRectGeometry(
                rect = rect.toSnapshot(GPUPreparedCompositeRefusalCodes.OPERATION, operationIndex),
                topLeft = topLeft.toPointSnapshot(operationIndex),
                topRight = topRight.toPointSnapshot(operationIndex),
                bottomRight = bottomRight.toPointSnapshot(operationIndex),
                bottomLeft = bottomLeft.toPointSnapshot(operationIndex),
            )

        private fun org.graphiks.kanvas.types.CornerRadii.toPointSnapshot(
            operationIndex: Int,
        ): GPUPreparedPointSnapshot {
            if (!x.isFinite() || !y.isFinite()) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.OPERATION,
                    operationIndex,
                    mapOf("reason" to "non-finite corner radius"),
                )
            }
            return GPUPreparedPointSnapshot(x.toRawBits(), y.toRawBits())
        }

        private fun Path.toSnapshot(operationIndex: Int): GPUPreparedGeometrySnapshot.PathGeometry {
            val pointSnapshots = points().map { it.toSnapshot(operationIndex) }
            return GPUPreparedGeometrySnapshot.PathGeometry(
                fill = when (fillType) {
                    FillType.WINDING -> GPUPreparedPathFillSnapshot.Winding
                    FillType.EVEN_ODD -> GPUPreparedPathFillSnapshot.EvenOdd
                    FillType.INVERSE_WINDING -> GPUPreparedPathFillSnapshot.InverseWinding
                    FillType.INVERSE_EVEN_ODD -> GPUPreparedPathFillSnapshot.InverseEvenOdd
                },
                verbs = verbs().map {
                    when (it) {
                        PathVerb.MOVE -> GPUPreparedPathVerbSnapshot.Move
                        PathVerb.LINE -> GPUPreparedPathVerbSnapshot.Line
                        PathVerb.QUAD -> GPUPreparedPathVerbSnapshot.Quad
                        PathVerb.CUBIC -> GPUPreparedPathVerbSnapshot.Cubic
                        PathVerb.ARC_TO -> GPUPreparedPathVerbSnapshot.ArcTo
                        PathVerb.CLOSE -> GPUPreparedPathVerbSnapshot.Close
                    }
                },
                points = pointSnapshots,
            )
        }

        private fun Point.toSnapshot(operationIndex: Int): GPUPreparedPointSnapshot {
            if (!x.isFinite() || !y.isFinite()) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.OPERATION,
                    operationIndex,
                    mapOf("reason" to "non-finite path point"),
                )
            }
            return GPUPreparedPointSnapshot(x.toRawBits(), y.toRawBits())
        }

        private fun Matrix33.toSnapshot(operationIndex: Int): GPUPreparedMatrixSnapshot {
            val values = listOf(
                scaleX,
                skewX,
                transX,
                skewY,
                scaleY,
                transY,
                persp0,
                persp1,
                persp2,
            )
            if (values.any { !it.isFinite() }) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.OPERATION,
                    operationIndex,
                    mapOf("reason" to "non-finite transform"),
                )
            }
            return GPUPreparedMatrixSnapshot(
                scaleX.toRawBits(),
                skewX.toRawBits(),
                transX.toRawBits(),
                skewY.toRawBits(),
                scaleY.toRawBits(),
                transY.toRawBits(),
                persp0.toRawBits(),
                persp1.toRawBits(),
                persp2.toRawBits(),
            )
        }

        private fun ClipStack.toSnapshot(operationIndex: Int): GPUPreparedClipSnapshot =
            when (this) {
                ClipStack.WideOpen -> GPUPreparedClipSnapshot.WideOpen
                is ClipStack.DeviceRect -> GPUPreparedClipSnapshot.DeviceRect(
                    rect = rect.toSnapshot(GPUPreparedCompositeRefusalCodes.CLIP, operationIndex),
                    antiAlias = antiAlias,
                )
                is ClipStack.Complex -> refuse(
                    GPUPreparedCompositeRefusalCodes.CLIP,
                    operationIndex,
                    mapOf("reason" to "complex clip is outside the bounded scaffold"),
                )
            }

        private fun Paint.toSnapshot(operationIndex: Int): GPUPreparedPaintSnapshot {
            if (shader != null || colorFilter != null ||
                pathEffect != null || imageFilter != null || blender != null
            ) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.PAINT,
                    operationIndex,
                    mapOf("reason" to "paint contains an unsupported effect"),
                )
            }
            if (!strokeWidth.isFinite() || !strokeMiter.isFinite()) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.PAINT,
                    operationIndex,
                    mapOf("reason" to "paint contains a non-finite stroke value"),
                )
            }
            return GPUPreparedPaintSnapshot(
                colorArgb = color.packed,
                blendMode = GPUBlendMode.valueOf(blendMode.name),
                style = when (style) {
                    PaintStyle.FILL -> GPUPreparedPaintStyle.Fill
                    PaintStyle.STROKE -> GPUPreparedPaintStyle.Stroke
                    PaintStyle.STROKE_AND_FILL -> GPUPreparedPaintStyle.StrokeAndFill
                },
                strokeWidthBits = strokeWidth.toRawBits(),
                strokeCap = when (strokeCap) {
                    StrokeCap.BUTT -> GPUPreparedStrokeCap.Butt
                    StrokeCap.ROUND -> GPUPreparedStrokeCap.Round
                    StrokeCap.SQUARE -> GPUPreparedStrokeCap.Square
                },
                strokeJoin = when (strokeJoin) {
                    StrokeJoin.MITER -> GPUPreparedStrokeJoin.Miter
                    StrokeJoin.ROUND -> GPUPreparedStrokeJoin.Round
                    StrokeJoin.BEVEL -> GPUPreparedStrokeJoin.Bevel
                },
                strokeMiterBits = strokeMiter.toRawBits(),
                antiAlias = antiAlias,
            )
        }

        private fun processMaskFilter(
            paint: Paint,
            operationIndex: Int,
        ): GPUPreparedMaskFilterPlan? {
            val rawMaskFilter = paint.maskFilter ?: return null
            val normalized = rawMaskFilter.toNormalizedMaskFilter()
                ?: refuse(
                    GPUPreparedCompositeRefusalCodes.PAINT,
                    operationIndex,
                    mapOf("reason" to "unsupported mask filter type"),
                )
            return when (val lowering = GPUPreparedMaskFilterLowerer.lower(normalized)) {
                is GPUPreparedMaskFilterLowering.Ready -> lowering.plan
                is GPUPreparedMaskFilterLowering.Refused -> refuse(
                    lowering.code,
                    operationIndex,
                    lowering.facts,
                )
            }
        }

        private fun refuse(
            code: String,
            operationIndex: Int,
            facts: Map<String, String>,
        ): Nothing = throw CaptureRefusedException(code, operationIndex, facts)
    }

    private class MutableCaptureScope(
        val id: GPUPreparedCompositeScopeId,
        val parentId: GPUPreparedCompositeScopeId?,
        val sourceKind: GPUPreparedCompositeScopeKind,
        val provenance: String,
        val state: GPUPreparedCompositeScopeState,
        val saveOperationIndex: Int? = null,
        var restoreOperationIndex: Int? = null,
        val entries: MutableList<GPUPreparedCompositeEntry> = mutableListOf(),
    )

    private data class PictureReplayState(
        val transform: Matrix33,
        val enclosingClip: ClipStack,
    )
}

private fun GPUPreparedRectSnapshot.identityFragment(): String = canonicalHash(
    "rect-bits",
    leftBits.toString(),
    topBits.toString(),
    rightBits.toString(),
    bottomBits.toString(),
)

private fun GPUPreparedPointSnapshot.identityFragment(): String =
    canonicalHash("point-bits", xBits.toString(), yBits.toString())

private fun GPUPreparedMatrixSnapshot.identityFragment(): String = canonicalHash(
    "matrix-bits",
    scaleXBits.toString(),
    skewXBits.toString(),
    transXBits.toString(),
    skewYBits.toString(),
    scaleYBits.toString(),
    transYBits.toString(),
    persp0Bits.toString(),
    persp1Bits.toString(),
    persp2Bits.toString(),
)

private fun GPUPreparedClipSnapshot.identityFragment(): String = when (this) {
    GPUPreparedClipSnapshot.WideOpen -> canonicalHash("clip-wide-open")
    is GPUPreparedClipSnapshot.DeviceRect -> canonicalHash(
        "clip-device-rect",
        rect.identityFragment(),
        antiAlias.toString(),
    )
}

private fun GPUPreparedPaintSnapshot.identityFragment(): String = canonicalHash(
    "paint",
    colorArgb.toString(),
    blendMode.name,
    style.name,
    strokeWidthBits.toString(),
    strokeCap.name,
    strokeJoin.name,
    strokeMiterBits.toString(),
    antiAlias.toString(),
)

private fun GPUPreparedMaskFilterPlan.identityFragment(): String = canonicalHash(
    "mask-filter-plan",
    kind.name,
    coverageFormat.name,
    executionIdentity,
)
private fun GPUPreparedCompositeScopeState.identityFragment(): String = canonicalHash(
    "scope-state",
    bounds?.identityFragment() ?: "no-bounds",
    paint?.identityFragment() ?: "no-paint",
    transform.identityFragment(),
    clip.identityFragment(),
    backdropRequired.toString(),
)

private fun GPUPreparedCompositeScope.identityFragment(): String = canonicalHash(
    buildList {
        add("scope")
        add(id.value)
        add(parentId?.value ?: "no-parent")
        add(saveOperationIndex?.toString() ?: "no-save")
        add(restoreOperationIndex?.toString() ?: "no-restore")
        add(sourceKind.name)
        add(provenance)
        add(state?.identityFragment() ?: "no-state")
        add(entries.size.toString())
        entries.forEach { entry ->
            add(
                when (entry) {
                    is GPUPreparedCompositeEntry.Draw -> "draw:${entry.operationIndex}"
                    is GPUPreparedCompositeEntry.Scope -> "scope:${entry.id.value}"
                },
            )
        }
    },
)

private fun computeCaptureIdentity(
    rootScopeId: GPUPreparedCompositeScopeId,
    operations: List<GPUPreparedCapturedOperation>,
    scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
): String = canonicalHash(
    buildList {
        add("capture:v2")
        add(rootScopeId.value)
        add(operations.size.toString())
        operations.forEach { add(it.identity) }
        add(scopes.size.toString())
        scopes.values.forEach { add(it.identityFragment()) }
    },
)

private fun canonicalHash(vararg parts: String): String = canonicalHash(parts.asList())

private fun canonicalHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private class CaptureRefusedException(
    val code: String,
    val operationIndex: Int?,
    val facts: Map<String, String>,
) : RuntimeException()

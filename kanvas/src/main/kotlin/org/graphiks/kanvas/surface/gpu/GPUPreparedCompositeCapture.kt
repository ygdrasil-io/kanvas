package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScope
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeId
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeEntry
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import java.security.MessageDigest

data class GPUPreparedCompositeCaptureLimits(
    val maxRecursionDepth: Int = 10,
    val maxNestingDepth: Int = 10,
    val maxExpandedOps: Int = 10000,
)

sealed interface GPUPreparedOperationSnapshot {
    fun identityFragment(): String
    data class DrawOp(
        val opType: String,
        val operationIndex: Int,
        val provenance: String,
        val geometryFacts: String,
        val paintFacts: String,
        val transformFacts: String,
        val clipFacts: String,
    ) : GPUPreparedOperationSnapshot {
        override fun identityFragment(): String =
            "draw:$opType:$geometryFacts:$paintFacts:$transformFacts:$clipFacts:$provenance"
    }
}

/** Captured operation with typed snapshot. */
data class GPUPreparedCapturedOperation(
    val sourceOperationIndex: Int,
    val snapshot: GPUPreparedOperationSnapshot,
    val identity: String,
)

/** Immutable composite capture result. */
data class GPUPreparedCompositeCapture(
    val rootScopeId: GPUPreparedCompositeScopeId,
    val scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
    val expandedOperations: List<GPUPreparedCapturedOperation>,
    val identity: String,
)

sealed interface GPUPreparedCompositeCaptureResult {
    data class Ready(val capture: GPUPreparedCompositeCapture) :
        GPUPreparedCompositeCaptureResult

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedCompositeCaptureResult
}

internal object GPUPreparedCompositeCapturer {

    fun capture(
        operations: List<DisplayOp>,
        limits: GPUPreparedCompositeCaptureLimits,
    ): GPUPreparedCompositeCaptureResult {
        val ctx = CaptureContext(limits)
        return try {
            val totalOps = countTotalOps(operations)
            ctx.checkBudget(totalOps)
            val (processedOps, root) = ctx.processTopLevel(operations)
            val scopes = ctx.finalizeScopes(root)
            GPUPreparedCompositeCaptureResult.Ready(
                GPUPreparedCompositeCapture(
                    rootScopeId = root.id,
                    scopes = scopes,
                    expandedOperations = processedOps,
                    identity = computeIdentity(processedOps, scopes),
                )
            )
        } catch (refusal: CaptureRefusedException) {
            GPUPreparedCompositeCaptureResult.Refused(
                code = refusal.code,
                operationIndex = refusal.operationIndex,
                facts = refusal.facts,
            )
        }
    }

    private fun countTotalOps(ops: List<DisplayOp>): Int {
        var count = ops.size
        for (op in ops) {
            if (op is DisplayOp.DrawPicture) {
                count += countTotalOps(op.picture.ops)
            }
        }
        return count
    }

    private fun computeIdentity(
        ops: List<GPUPreparedCapturedOperation>,
        scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("capture:v1:${ops.size}:${scopes.size}".toByteArray())
        for (op in ops) {
            digest.update(op.identity.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    private class CaptureContext(val limits: GPUPreparedCompositeCaptureLimits) {
        private val scopes = mutableMapOf<GPUPreparedCompositeScopeId, MutableCaptureScope>()
        private val expandedOps = mutableListOf<GPUPreparedCapturedOperation>()
        private var scopeIdCounter = 0
        private var capturedOpCount = 0
        private val activePicturesOnStack = mutableSetOf<Int>()
        private var provenancePath = mutableListOf<String>()

        fun checkBudget(totalOps: Int) {
            if (totalOps > limits.maxExpandedOps) {
                refuse(
                    GPUPreparedCompositeRefusalCodes.PICTURE_BUDGET, -1,
                    mapOf("total" to totalOps.toString(), "limit" to limits.maxExpandedOps.toString()),
                )
            }
        }

        fun processTopLevel(
            operations: List<DisplayOp>,
        ): Pair<List<GPUPreparedCapturedOperation>, MutableCaptureScope> {
            val rootId = nextScopeId()
            val root = MutableCaptureScope(
                id = rootId, parentId = null,
                sourceKind = GPUPreparedCompositeScopeKind.Root, provenance = "root",
            )
            scopes[root.id] = root
            provenancePath.add("root")
            processOperations(operations, 0, root)
            provenancePath.removeLast()
            checkUnclosedLayers(root, -1)
            return expandedOps.toList() to root
        }

        private fun processOperations(
            operations: List<DisplayOp>,
            startIndex: Int,
            parentScope: MutableCaptureScope,
        ): Int {
            var idx = startIndex
            while (idx < operations.size) {
                val op = operations[idx]
                when {
                    op is DisplayOp.DrawPicture -> {
                        idx = processPicture(op, idx, parentScope, operations)
                    }
                    op is DisplayOp.BeginLayer -> {
                        val currentDepth = computeScopeDepth(parentScope)
                        val effectiveDepth = if (parentScope.sourceKind == GPUPreparedCompositeScopeKind.Root)
                            currentDepth else currentDepth + 1
                        if (effectiveDepth > limits.maxNestingDepth) {
                            refuse(
                                GPUPreparedCompositeRefusalCodes.LAYER_BUDGET, idx,
                                mapOf("depth" to effectiveDepth.toString(), "limit" to limits.maxNestingDepth.toString()),
                            )
                        }
                        val layerId = nextScopeId()
                        val layerScope = MutableCaptureScope(
                            id = layerId, parentId = parentScope.id,
                            sourceKind = GPUPreparedCompositeScopeKind.SaveLayer,
                            provenance = "saveLayer($idx)", saveOperationIndex = idx,
                            saveLayerRec = op.rec,
                        )
                        scopes[layerId] = layerScope
                        parentScope.entries.add(GPUPreparedCompositeEntry.Scope(layerId))
                        provenancePath.add("layer_${layerId.value}")
                        val resultIdx = processOperations(operations, idx + 1, layerScope)
                        provenancePath.removeLast()
                        if (resultIdx >= operations.size || operations[resultIdx] !is DisplayOp.EndLayer) {
                            refuse(
                                GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED,
                                layerScope.saveOperationIndex ?: resultIdx,
                                mapOf("reason" to "unclosed BeginLayer"),
                            )
                        }
                        layerScope.restoreOperationIndex = resultIdx
                        idx = resultIdx
                    }
                    op is DisplayOp.EndLayer -> {
                        if (parentScope.sourceKind == GPUPreparedCompositeScopeKind.Root) {
                            refuse(GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED, idx,
                                mapOf("reason" to "orphan EndLayer in root scope"))
                        }
                        return idx
                    }
                    else -> {
                        appendDraw(idx, op, parentScope)
                    }
                }
                idx++
            }
            return idx
        }

        private fun processPicture(
            op: DisplayOp.DrawPicture,
            opIdx: Int,
            parentScope: MutableCaptureScope,
            operations: List<DisplayOp>,
        ): Int {
            val pictureId = op.picture.uniqueID
            if (pictureId in activePicturesOnStack) {
                refuse(GPUPreparedCompositeRefusalCodes.PICTURE_CYCLE, opIdx,
                    mapOf("pictureId" to pictureId.toString()))
            }
            val innerOps = op.picture.ops
            val nestedStackDepth = activePicturesOnStack.size
            if (nestedStackDepth >= limits.maxRecursionDepth) {
                refuse(GPUPreparedCompositeRefusalCodes.PICTURE_BUDGET, opIdx,
                    mapOf("depth" to nestedStackDepth.toString(), "limit" to limits.maxRecursionDepth.toString()))
            }
            if (op.paint == null) {
                provenancePath.add("pic_${pictureId}")
                activePicturesOnStack.add(pictureId)
                try {
                    processOperations(innerOps, 0, parentScope)
                } finally {
                    activePicturesOnStack.remove(pictureId)
                    provenancePath.removeLast()
                }
                return opIdx
            } else {
                val syntheticId = nextScopeId()
                val syntheticScope = MutableCaptureScope(
                    id = syntheticId, parentId = parentScope.id,
                    sourceKind = GPUPreparedCompositeScopeKind.PaintedPicture,
                    provenance = "picture(${pictureId})",
                    saveOperationIndex = opIdx,
                    picturePaint = op.paint,
                    pictureTransform = op.transform,
                    pictureClip = op.clip,
                )
                scopes[syntheticId] = syntheticScope
                parentScope.entries.add(GPUPreparedCompositeEntry.Scope(syntheticId))
                provenancePath.add("picpainted_${pictureId}")
                activePicturesOnStack.add(pictureId)
                try {
                    processOperations(innerOps, 0, syntheticScope)
                } finally {
                    activePicturesOnStack.remove(pictureId)
                    provenancePath.removeLast()
                }
                syntheticScope.restoreOperationIndex = opIdx
                return opIdx
            }
        }

        private fun computeScopeDepth(scope: MutableCaptureScope): Int {
            var depth = 0
            var current: MutableCaptureScope = scope
            while (current.parentId != null) {
                val parent = scopes[current.parentId]
                if (parent != null) { depth++; current = parent } else break
            }
            return depth
        }

        private fun appendDraw(opIdx: Int, op: DisplayOp, scope: MutableCaptureScope) {
            val idx = expandedOps.size
            capturedOpCount++
            val opClassName = op.javaClass.simpleName
            val geomFacts = extractGeometryFacts(op)
            val paintFacts = extractPaintFacts(op)
            val transformFacts = extractTransformFacts(op)
            val clipFacts = extractClipFacts(op)
            val prov = provenancePath.joinToString("/")
            val opIdentity = computeOpIdentity(opType = opClassName, geom = geomFacts,
                paint = paintFacts, transform = transformFacts, clip = clipFacts, provenance = prov)
            expandedOps.add(
                GPUPreparedCapturedOperation(
                    sourceOperationIndex = opIdx,
                    snapshot = GPUPreparedOperationSnapshot.DrawOp(
                        opType = opClassName, operationIndex = opIdx, provenance = prov,
                        geometryFacts = geomFacts, paintFacts = paintFacts,
                        transformFacts = transformFacts, clipFacts = clipFacts,
                    ),
                    identity = opIdentity,
                )
            )
            scope.entries.add(GPUPreparedCompositeEntry.Draw(idx))
        }

        private fun computeOpIdentity(
            opType: String, geom: String, paint: String,
            transform: String, clip: String, provenance: String,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("op:v1:$opType:$geom:$paint:$transform:$clip:$provenance".toByteArray())
            return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
        }

        private fun extractGeometryFacts(op: DisplayOp): String {
            return when (op) {
                is DisplayOp.DrawRect -> "rect:${op.rect.left.toRawBits()}:${op.rect.top.toRawBits()}:${op.rect.right.toRawBits()}:${op.rect.bottom.toRawBits()}"
                is DisplayOp.DrawRRect -> "rrect:${op.rrect.rect.left.toRawBits()}:${op.rrect.rect.top.toRawBits()}:${op.rrect.rect.right.toRawBits()}:${op.rrect.rect.bottom.toRawBits()}"
                is DisplayOp.DrawPath -> "path:hash"
                else -> "no_geom"
            }
        }

        private fun extractPaintFacts(op: DisplayOp): String {
            return when (op) {
                is DisplayOp.DrawRect -> paintFacts(op.paint)
                is DisplayOp.DrawRRect -> paintFacts(op.paint)
                is DisplayOp.DrawPath -> paintFacts(op.paint)
                is DisplayOp.DrawPicture -> if (op.paint != null) paintFacts(op.paint) else "nopaint"
                is DisplayOp.DrawImage -> "image"
                is DisplayOp.DrawText -> "text"
                is DisplayOp.BeginLayer -> "layer"
                is DisplayOp.EndLayer -> "end"
                else -> "generic"
            }
        }

        private fun paintFacts(p: Paint): String {
            return "paint:${p.color.packed}:${p.blendMode}:${p.style}:${p.strokeWidth.toRawBits()}:${p.antiAlias}"
        }

        private fun extractTransformFacts(op: DisplayOp): String {
            return when (op) {
                is DisplayOp.DrawRect -> "t:ok"
                is DisplayOp.DrawRRect -> "t:ok"
                is DisplayOp.DrawPicture -> if (op.transform !== Matrix33.identity()) "t:t" else "t:id"
                else -> "t:id"
            }
        }

        private fun extractClipFacts(op: DisplayOp): String {
            return when (op) {
                is DisplayOp.DrawRect -> if (op.clip is ClipStack.WideOpen) "c:wo" else "c:cl"
                else -> "c:wo"
            }
        }

        private fun checkUnclosedLayers(scope: MutableCaptureScope, opIdx: Int) {
            for ((id, child) in scopes) {
                if (child.parentId == scope.id &&
                    child.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer &&
                    child.restoreOperationIndex == null
                ) {
                    refuse(
                        GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED,
                        child.saveOperationIndex ?: opIdx,
                        mapOf("reason" to "unclosed layer scope ${child.id.value}"),
                    )
                }
            }
        }

        fun finalizeScopes(root: MutableCaptureScope): Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope> {
            val result = mutableMapOf<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>()
            for ((id, mut) in scopes) {
                result[id] = GPUPreparedCompositeScope(
                    id = mut.id, parentId = mut.parentId,
                    saveOperationIndex = mut.saveOperationIndex,
                    restoreOperationIndex = mut.restoreOperationIndex,
                    entries = mut.entries.toList(),
                    sourceKind = mut.sourceKind, provenance = mut.provenance,
                )
            }
            return result.toMap()
        }

        private fun nextScopeId(): GPUPreparedCompositeScopeId {
            scopeIdCounter++
            return GPUPreparedCompositeScopeId("scope_$scopeIdCounter")
        }

        private fun refuse(code: String, opIdx: Int, facts: Map<String, String>): Nothing {
            throw CaptureRefusedException(code, opIdx, facts)
        }
    }

    private class MutableCaptureScope(
        val id: GPUPreparedCompositeScopeId,
        var parentId: GPUPreparedCompositeScopeId?,
        val sourceKind: GPUPreparedCompositeScopeKind,
        val provenance: String,
        var saveOperationIndex: Int? = null,
        var restoreOperationIndex: Int? = null,
        val entries: MutableList<GPUPreparedCompositeEntry> = mutableListOf(),
        val saveLayerRec: SaveLayerRec? = null,
        val picturePaint: Paint? = null,
        val pictureTransform: Matrix33? = null,
        val pictureClip: ClipStack? = null,
    )
}

private class CaptureRefusedException(
    val code: String,
    val operationIndex: Int?,
    val facts: Map<String, String>,
) : RuntimeException()

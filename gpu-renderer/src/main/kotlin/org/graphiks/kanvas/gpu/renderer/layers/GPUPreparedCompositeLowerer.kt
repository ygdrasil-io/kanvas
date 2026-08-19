package org.graphiks.kanvas.gpu.renderer.layers

object GPUPreparedCompositeLowerer {

    fun lower(
        scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
        rootScopeId: GPUPreparedCompositeScopeId,
        identity: String,
        deviceGeneration: Long = 0L,
        sceneTargetLabel: String = "root-target",
    ): GPUPreparedCompositeLowering {
        val layerPlans = mutableListOf<GPULayerPlan>()
        val gatePlans = mutableMapOf<String, GPUSaveLayerIsolatedTargetGatePlan>()
        val scopesByValue = scopes.entries.associate { (id, scope) -> id.value to scope }

        for (scope in scopes.values) {
            if (scope.sourceKind != GPUPreparedCompositeScopeKind.SaveLayer) continue

            val state = scope.state ?: continue

            val saveRecord = buildSaveRecord(scope, state)
            val bounds = buildBounds(state)
            val parentTargetLabel = parentTargetLabel(scope, scopes, sceneTargetLabel)

            val request = GPUSaveLayerIsolatedTargetRequest(
                saveRecord = saveRecord,
                bounds = bounds,
                parentTargetLabel = parentTargetLabel,
                deviceGeneration = deviceGeneration,
            )

            val gatePlan = GPUSaveLayerIsolatedTargetPlanner().plan(request)

            val refusalCode = gatePlan.diagnostics.firstOrNull { it.terminal }?.code
            if (refusalCode != null) {
                return GPUPreparedCompositeLowering.Refused(
                    code = refusalCode,
                    operationIndex = scope.saveOperationIndex,
                    facts = mapOf("scopeId" to scope.id.value),
                )
            }

            layerPlans.add(gatePlan.layerPlan)
            gatePlans[gatePlan.layerPlan.saveRecord.scopeId.value] = gatePlan
        }

        // Render order: innermost layers first (their targets must exist before a parent
        // composites them), then siblings by lexicographic scopeId for a deterministic
        // tie-break. Insertion order alone would emit the parent's CompositeLayer before
        // the child target is ever rendered.
        val depthByScopeValue = scopesByValue.mapValues { (value, scope) ->
            scopeDepth(scope, scopesByValue)
        }
        layerPlans.sortWith(
            compareByDescending<GPULayerPlan> { plan -> depthByScopeValue[plan.saveRecord.scopeId.value] ?: 0 }
                .thenBy { plan -> plan.saveRecord.scopeId.value },
        )

        return GPUPreparedCompositeLowering.Ready(
            GPUPreparedCompositePlan(
                captureIdentity = identity,
                rootScopeId = rootScopeId,
                layers = layerPlans,
                normalizedFilters = emptyMap(),
                identity = identity,
                gatePlans = gatePlans,
            ),
        )
    }

    private fun buildSaveRecord(scope: GPUPreparedCompositeScope, state: GPUPreparedCompositeScopeState): GPULayerSaveRecord {
        val childIds = scope.entries.mapNotNull { entry ->
            when (entry) {
                is GPUPreparedCompositeEntry.Draw -> "draw:${entry.operationIndex}"
                is GPUPreparedCompositeEntry.Scope -> "scope:${entry.id.value}"
            }
        }
        return GPULayerSaveRecord(
            scopeId = GPULayerScopeID(scope.id.value),
            boundsLabel = "scope-bounds:${scope.id.value}",
            backdropRequired = state.backdropRequired,
            parentScopeId = scope.parentId?.let { GPULayerScopeID(it.value) },
            childCommandIds = childIds,
            restoreBlendMode = state.paint?.blendMode?.restoreLabel ?: "srcOver",
            alpha = state.paint.layerAlpha(),
            clipLabel = state.clip.clipLabel(),
        )
    }

/** Layer opacity derived from the captured paint color alpha byte. */
private fun GPUPreparedPaintSnapshot?.layerAlpha(): Float =
    this?.let { paint ->
        ((paint.colorArgb shr 24) and 0xFFu).toFloat() / 255f
    } ?: 1f

/**
 * Parent render-target label for a saveLayer scope's composite draw.
 *
 * A saveLayer whose parent is the root scope composites onto the scene target, so its
 * parent label must be the real scene target ref (the one the frame preflight declares),
 * not a scope-id label. A saveLayer nested under another saveLayer composites onto the
 * parent layer's isolated target (`layer-target:<parentScopeId>`, matching
 * [GPUSaveLayerIsolatedTargetPlanner]'s target label convention) — the preflight refuses
 * such frames loudly with `unsupported.prepared-surface.layer-nesting` until nested
 * materialization lands.
 */
private fun parentTargetLabel(
    scope: GPUPreparedCompositeScope,
    scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
    sceneTargetLabel: String,
): String {
    val parentId = scope.parentId ?: return sceneTargetLabel
    val parent = scopes[parentId] ?: return sceneTargetLabel
    return if (parent.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer) {
        "layer-target:${parentId.value}"
    } else {
        sceneTargetLabel
    }
}

    /** Faithful lossless label for an accepted clip snapshot. */
    private fun GPUPreparedClipSnapshot.clipLabel(): String? =
        when (this) {
            GPUPreparedClipSnapshot.WideOpen -> null
            is GPUPreparedClipSnapshot.DeviceRect ->
                "device-rect:l=${rect.leftBits},t=${rect.topBits},r=${rect.rightBits}," +
                    "b=${rect.bottomBits},aa=$antiAlias"
        }

    private fun buildBounds(state: GPUPreparedCompositeScopeState): GPULayerBoundsPlan {
        val bounds = state.bounds
        return if (bounds != null) {
            val left = Float.fromBits(bounds.leftBits)
            val top = Float.fromBits(bounds.topBits)
            val right = Float.fromBits(bounds.rightBits)
            val bottom = Float.fromBits(bounds.bottomBits)
            val w = if (right > left) (right - left).toInt() else 0
            val h = if (bottom > top) (bottom - top).toInt() else 0
            val ox = left.toInt()
            val oy = top.toInt()
            GPULayerBoundsPlan(
                requestedBoundsLabel = "scope-bounds:${left}x${top}-${right}x${bottom}",
                deviceBoundsLabel = "$ox,$oy,$w,$h",
                conservative = false,
                finite = true,
                originX = ox,
                originY = oy,
                width = w,
                height = h,
            )
        } else {
            GPULayerBoundsPlan(
                requestedBoundsLabel = "unbounded",
                deviceBoundsLabel = "0,0,0,0",
                conservative = false,
                finite = false,
            )
        }
    }
}

/** Returns the saveLayer nesting depth of a scope (0 for root children). */
private fun scopeDepth(
    scope: GPUPreparedCompositeScope,
    scopesByValue: Map<String, GPUPreparedCompositeScope>,
): Int {
    var depth = 0
    var parentId = scope.parentId?.value
    while (parentId != null) {
        depth += 1
        parentId = scopesByValue[parentId]?.parentId?.value
    }
    return depth
}

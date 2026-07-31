package org.graphiks.kanvas.gpu.renderer.layers

object GPUPreparedCompositeLowerer {

    fun lower(
        scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
        rootScopeId: GPUPreparedCompositeScopeId,
        identity: String,
    ): GPUPreparedCompositeLowering {
        val layerPlans = mutableListOf<GPULayerPlan>()

        for (scope in scopes.values) {
            if (scope.sourceKind != GPUPreparedCompositeScopeKind.SaveLayer) continue

            val state = scope.state ?: continue

            val saveRecord = buildSaveRecord(scope)
            val bounds = buildBounds(state)
            val parentTargetLabel = scope.parentId?.value ?: "root-target"

            val request = GPUSaveLayerIsolatedTargetRequest(
                saveRecord = saveRecord,
                bounds = bounds,
                parentTargetLabel = parentTargetLabel,
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
        }

        return GPUPreparedCompositeLowering.Ready(
            GPUPreparedCompositePlan(
                captureIdentity = identity,
                rootScopeId = rootScopeId,
                layers = layerPlans,
                normalizedFilters = emptyMap(),
                identity = identity,
            ),
        )
    }

    private fun buildSaveRecord(scope: GPUPreparedCompositeScope): GPULayerSaveRecord {
        val childIds = scope.entries.mapNotNull { entry ->
            when (entry) {
                is GPUPreparedCompositeEntry.Draw -> "draw:${entry.operationIndex}"
                is GPUPreparedCompositeEntry.Scope -> "scope:${entry.id.value}"
            }
        }
        return GPULayerSaveRecord(
            scopeId = GPULayerScopeID(scope.id.value),
            boundsLabel = "scope-bounds:${scope.id.value}",
            backdropRequired = false,
            parentScopeId = scope.parentId?.let { GPULayerScopeID(it.value) },
            childCommandIds = childIds,
        )
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

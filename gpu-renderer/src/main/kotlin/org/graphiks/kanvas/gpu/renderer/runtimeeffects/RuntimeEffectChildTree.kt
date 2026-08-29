package org.graphiks.kanvas.gpu.renderer.runtimeeffects

enum class GPURuntimeEffectChildKind(val sourceKind: String) { Shader("shader"), ColorFilter("color-filter"), Blender("blender") }

class GPURuntimeEffectChildTree(
    val descriptorId: GPURuntimeEffectID,
    children: List<Child> = emptyList(),
    val uniformSchemaHash: String,
    val usesLocalCoordinates: Boolean = false,
    val nestedDepth: Int = 1,
) {
    val children: List<Child> = children.toList()

    data class Child(val slot: String, val kind: GPURuntimeEffectChildKind, val present: Boolean = true)

    init {
        require(uniformSchemaHash.isNotBlank()) { "runtime-effect child tree uniform schema hash must not be blank" }
        require(nestedDepth >= 1) { "runtime-effect child tree depth must be positive" }
        children.forEach { child ->
            require(child.slot.isNotBlank()) { "runtime-effect child slot must not be blank" }
        }
    }

    fun validate(snapshot: GPURuntimeEffectRegistrySnapshot): GPURuntimeEffectChildTreeResult {
        if (nestedDepth > MAX_DEPTH) return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.child_depth_exceeded")
        val descriptor = snapshot.lookup(descriptorId)
            ?: return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.unregistered_descriptor")
        if (uniformSchemaHash != descriptor.uniformSchema.schemaHash)
            return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.uniform_schema_mismatch")
        val slots = descriptor.childSlots
        children.forEach { child ->
            val slot = slots.singleOrNull { it.slotName == child.slot }
                ?: return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.child_extra")
            if (!child.present && slot.required)
                return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.child_missing")
            if (child.present && child.kind.sourceKind !in slot.acceptedSourceKinds)
                return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.child_kind_mismatch")
        }
        val suppliedNames = children.map { it.slot }.toSet()
        if (slots.any { it.required && it.slotName !in suppliedNames })
            return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.child_missing")
        if (children.map { it.slot } != slots.map { it.slotName })
            return GPURuntimeEffectChildTreeResult.Refused("unsupported.runtime_effect.child_order")
        return GPURuntimeEffectChildTreeResult.Accepted(
            GPURuntimeEffectChildTreePlan(descriptorId, children, uniformSchemaHash, usesLocalCoordinates),
        )
    }
    companion object { const val MAX_DEPTH: Int = 64 }
}

sealed interface GPURuntimeEffectChildTreeResult {
    data class Accepted(val plan: GPURuntimeEffectChildTreePlan) : GPURuntimeEffectChildTreeResult
    data class Refused(val code: String) : GPURuntimeEffectChildTreeResult
}

class GPURuntimeEffectChildTreePlan(
    val descriptorId: GPURuntimeEffectID,
    orderedChildren: List<GPURuntimeEffectChildTree.Child>,
    val uniformSchemaHash: String,
    val usesLocalCoordinates: Boolean,
) {
    val orderedChildren: List<GPURuntimeEffectChildTree.Child> = orderedChildren.toList()
    val cpuChildren: List<GPURuntimeEffectChildTree.Child> = orderedChildren.toList()
    val gpuChildren: List<GPURuntimeEffectChildTree.Child> = orderedChildren.toList()
}

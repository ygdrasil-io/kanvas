package org.graphiks.kanvas.gpu.plan

/**
 * Versioned graph contract for W5a path successors.  Legacy v1 graphs intentionally retain
 * their table-free [PlanDrawMaterialAuthority.LegacyColorV1] representation.
 */
@JvmSynthetic
public fun RenderGraph.hasW5aMaterialPathContract(): Boolean {
    val table = materialPlanTableOrNull() ?: return false
    val paths = passes().filterIsInstance<PlanPass.PathRenderPass>()
    return paths.isNotEmpty() && paths.all { pass ->
        val authority = pass.draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1
            ?: return@all false
        runCatching { table.entry(authority.ref) }.isSuccess
    }
}

@JvmSynthetic
public fun RenderGraph.hasLegacyPathColorContract(): Boolean =
    materialPlanTableOrNull() == null &&
        passes().filterIsInstance<PlanPass.PathRenderPass>().all { pass ->
            pass.draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1
        }

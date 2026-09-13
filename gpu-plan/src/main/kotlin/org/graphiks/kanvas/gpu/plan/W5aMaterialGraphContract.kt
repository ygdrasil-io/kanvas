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
        val ref = when (val authority = pass.draw.materialAuthority) {
            is PlanDrawMaterialAuthority.MaterialV3 -> return@all false
            is PlanDrawMaterialAuthority.MaterialV1 -> authority.ref
            is PlanDrawMaterialAuthority.MaterialV2 -> authority.ref
            is PlanDrawMaterialAuthority.LegacyColorV1 -> return@all false
        }
        runCatching { table.entry(ref) }.isSuccess
    }
}

@JvmSynthetic
public fun RenderGraph.hasLegacyPathColorContract(): Boolean =
    materialPlanTableOrNull() == null &&
        passes().filterIsInstance<PlanPass.PathRenderPass>().all { pass ->
            pass.draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1
        }

/** Versioned material contract for the historical W4c/W4d pass families. */
@JvmSynthetic
public fun RenderGraph.hasW5aPathDrawMaterialContract(): Boolean {
    val table = materialPlanTableOrNull() ?: return false
    val draws = passes().flatMap { pass -> when (pass) {
        is PlanPass.RenderPass -> pass.draws().filterIsInstance<PathDraw>()
        is PlanPass.StencilProducer -> listOf(pass.draw)
        is PlanPass.StencilCover -> listOf(pass.draw)
        else -> emptyList()
    } }
    return draws.isNotEmpty() && draws.all { draw ->
        val authority = draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1
            ?: return@all false
        runCatching { table.entry(authority.ref) }.isSuccess
    }
}

@JvmSynthetic
public fun RenderGraph.hasLegacyPathDrawColorContract(): Boolean =
    materialPlanTableOrNull() == null &&
        passes().flatMap { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws().filterIsInstance<PathDraw>()
            is PlanPass.StencilProducer -> listOf(pass.draw)
            is PlanPass.StencilCover -> listOf(pass.draw)
            else -> emptyList()
        } }.all { draw -> draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1 }

/** Exact W5a material capability discriminator for W4dGeneral and W4e path payloads. */
@JvmSynthetic
public fun RenderGraph.hasW5aMaterialPathCapabilityV2(): Boolean =
    W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(capabilityId) ||
        W4eClipPlanCompiler.isW5aMaterialCapabilityId(capabilityId)

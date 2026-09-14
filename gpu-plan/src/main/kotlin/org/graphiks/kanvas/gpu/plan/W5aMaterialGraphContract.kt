package org.graphiks.kanvas.gpu.plan

/**
 * Versioned graph contract for W5a path successors.  Legacy v1 graphs intentionally retain
 * their table-free [PlanDrawMaterialAuthority.LegacyColorV1] representation.
 */
@JvmSynthetic
public fun RenderGraph.hasW5aMaterialPathContract(): Boolean = canonicalConstruction().hasW5aMaterialPathContract()

internal fun RenderGraphConstruction.hasW5aMaterialPathContract(): Boolean {
    val table = materialPlanTableOrNull() ?: return false
    val paths = passes().filterIsInstance<PlanPass.PathRenderPass>()
    return paths.isNotEmpty() && paths.all { pass ->
        val ref = when (val authority = pass.draw.materialAuthority) {
            is PlanDrawMaterialAuthority.MaterialV4 -> {
                if (pass.draw !is GeneralPathDraw || !(pass.draw.copyPathGeometry() is PathDrawGeometry.Fill ||
                    pass.draw.copyPathGeometry() is PathDrawGeometry.Stroke && table.isUnfilteredGradientV4(authority.ref))) return@all false
                if (!table.colorSourceProofV4(authority.ref).authenticates(table,authority.ref,authority.coordinates)) return@all false
                authority.ref
            }
            is PlanDrawMaterialAuthority.MaterialV3 -> return@all false
            is PlanDrawMaterialAuthority.MaterialV1 -> authority.ref
            is PlanDrawMaterialAuthority.MaterialV2 -> authority.ref
            is PlanDrawMaterialAuthority.LegacyColorV1 -> return@all false
        }
        runCatching { table.entry(ref) }.isSuccess
    }
}

@JvmSynthetic
public fun RenderGraph.hasLegacyPathColorContract(): Boolean = canonicalConstruction().hasLegacyPathColorContract()

internal fun RenderGraphConstruction.hasLegacyPathColorContract(): Boolean =
    materialPlanTableOrNull() == null &&
        passes().filterIsInstance<PlanPass.PathRenderPass>().all { pass ->
            pass.draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1
        }

/** Versioned material contract for the historical W4c/W4d pass families. */
@JvmSynthetic
public fun RenderGraph.hasW5aPathDrawMaterialContract(): Boolean = canonicalConstruction().hasW5aPathDrawMaterialContract()

internal fun RenderGraphConstruction.hasW5aPathDrawMaterialContract(): Boolean {
    val table = materialPlanTableOrNull() ?: return false
    val draws = passes().flatMap { pass -> when (pass) {
        is PlanPass.RenderPass -> pass.draws().filterIsInstance<PathDraw>()
        is PlanPass.StencilProducer -> listOf(pass.draw)
        is PlanPass.StencilCover -> listOf(pass.draw)
        else -> emptyList()
    } }
    return draws.isNotEmpty() && draws.all { draw ->
        val ref = when (val authority = draw.materialAuthority) {
            is PlanDrawMaterialAuthority.MaterialV1 -> authority.ref
            is PlanDrawMaterialAuthority.MaterialV4 -> {
                if (draw !is PathFillDraw || !table.colorSourceProofV4(authority.ref).authenticates(table,authority.ref,authority.coordinates))
                    return@all false
                authority.ref
            }
            else -> return@all false
        }
        runCatching { table.entry(ref) }.isSuccess
    }
}

@JvmSynthetic
public fun RenderGraph.hasLegacyPathDrawColorContract(): Boolean = canonicalConstruction().hasLegacyPathDrawColorContract()

internal fun RenderGraphConstruction.hasLegacyPathDrawColorContract(): Boolean =
    materialPlanTableOrNull() == null &&
        passes().flatMap { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws().filterIsInstance<PathDraw>()
            is PlanPass.StencilProducer -> listOf(pass.draw)
            is PlanPass.StencilCover -> listOf(pass.draw)
            else -> emptyList()
        } }.all { draw -> draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1 }

/** Exact W5a material capability discriminator for W4dGeneral and W4e path payloads. */
@JvmSynthetic
public fun RenderGraph.hasW5aMaterialPathCapabilityV2(): Boolean = canonicalConstruction().hasW5aMaterialPathCapabilityV2()

internal fun RenderGraphConstruction.hasW5aMaterialPathCapabilityV2(): Boolean =
    W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(capabilityId) ||
        W4eClipPlanCompiler.isW5aMaterialCapabilityId(capabilityId)

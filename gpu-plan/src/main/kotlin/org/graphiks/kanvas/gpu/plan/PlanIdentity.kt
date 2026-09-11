package org.graphiks.kanvas.gpu.plan

@JvmInline
public value class PlanId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan ID must not be blank" } }
}

@JvmInline
public value class PlanResourceId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan resource ID must not be blank" } }
}

@JvmInline
public value class PlanPassId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan pass ID must not be blank" } }
}

@JvmInline
public value class PlanAtomicGroupId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan atomic group ID must not be blank" } }
}

internal fun planResourceId(role: PlanResourceRole, ordinal: Int): PlanResourceId =
    PlanResourceId("${role.name}:$ordinal")

internal fun planPassId(role: PlanPassRole, ordinal: Int): PlanPassId =
    PlanPassId("${role.name}:$ordinal")

internal fun canonicalPathAtomicGroup(draw: PathDraw): PlanAtomicGroupId = when (draw) {
    is PathFillDraw -> PlanAtomicGroupId("w4c:${draw.commandIndex}")
    is PathStrokeDraw -> PlanAtomicGroupId("w4d:${draw.commandIndex}")
    is GeneralPathDraw -> canonicalGeneralPathAtomicGroup(draw)
}

internal fun canonicalGeneralPathAtomicGroup(draw: GeneralPathDraw): PlanAtomicGroupId =
    PlanAtomicGroupId("w4d.2:${draw.commandIndex}")

/** Canonical capability facts that affect texture allocation or resolve availability. */
internal fun planCapabilityIdentityFacts(capabilities: PlanCapabilitySnapshot): List<String> = buildList {
    add("texture-sample-supports-v1")
    capabilities.supportedTextureSampleSupports()
        .map { support ->
            "${textureFormatIdentity(support.format)}:${support.sampleCountI32}:${support.usages().map { it.name }.sorted().joinToString(",")}"
        }
        .sorted()
        .forEach(::add)
    add("texture-resolve-supports-v1")
    capabilities.supportedTextureResolveSupports()
        .map { support ->
            "${textureFormatIdentity(support.format)}:${support.sourceSampleCountI32}:${support.destinationSampleCountI32}"
        }
        .sorted()
        .forEach(::add)
}

private fun textureFormatIdentity(format: PlanTextureFormat): String = when (format) {
    is PlanTextureFormat.Color -> "color:${format.value.name}"
    is PlanTextureFormat.DepthStencil -> "depth-stencil:${format.value.name}"
    PlanTextureFormat.CoverageMask -> "coverage-mask:${PlanTextureFormat.CoverageMask.value.name}"
}

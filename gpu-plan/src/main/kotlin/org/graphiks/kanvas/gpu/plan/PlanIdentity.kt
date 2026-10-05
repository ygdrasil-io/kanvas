package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor

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
    is W5bW4ePathDraw -> draw.nativeColorPass.atomicGroup ?: if (draw.hasW4eInverseMaskStencilPair()) {
        PlanAtomicGroupId("w4e.inverse-mask:${draw.commandIndex}")
    } else error("W5b W4e paths require a sealed stencil atomic group")
    is GeneralPathDraw -> canonicalGeneralPathAtomicGroup(draw)
}

internal fun canonicalGeneralPathAtomicGroup(draw: GeneralPathDraw): PlanAtomicGroupId =
    PlanAtomicGroupId("w4d.2:${draw.commandIndex}")

/**
 * Identity is target-relative: a LINEAR plan keeps its historical capability
 * sequence, while encoded plans authenticate the physical encoded capability.
 * Planning, validation and native seals continue to use the complete snapshot.
 */
internal fun PlanCapabilitySnapshot.identitySupportedFormats(target: RenderTargetDescriptor): Set<PlanLogicalColorFormat> =
    if (target.compositionDomain == CompositionDomain.LINEAR)
        supportedFormats() - PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL
    else supportedFormats()

/** Canonical target-relative capability facts that affect identity allocation or resolve availability. */
internal fun planCapabilityIdentityFacts(
    capabilities: PlanCapabilitySnapshot,
    target: RenderTargetDescriptor,
): List<String> = buildList {
    add("texture-sample-supports-v1")
    capabilities.supportedTextureSampleSupports()
        .filter { support -> target.compositionDomain != CompositionDomain.LINEAR ||
            (support.format as? PlanTextureFormat.Color)?.value != PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL }
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
    is PlanTextureFormat.ImageV1 -> "image-v1:${format.value.name}"
    is PlanTextureFormat.Color -> "color:${format.value.name}"
    is PlanTextureFormat.DepthStencil -> "depth-stencil:${format.value.name}"
    PlanTextureFormat.CoverageMask -> "coverage-mask:${PlanTextureFormat.CoverageMask.value.name}"
}

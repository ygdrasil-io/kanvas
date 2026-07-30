package org.graphiks.kanvas.surface.gpu

import kotlin.math.roundToInt
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedColorGlyphPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextA8PayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextDeviceToLocalAffine
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadGatherer
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.Matrix33

internal sealed interface GPUPreparedTextSemanticGatherResult {
    data class Gathered(
        val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    ) : GPUPreparedTextSemanticGatherResult

    data class Refused(
        val code: String,
        val commandId: Int?,
        val message: String,
    ) : GPUPreparedTextSemanticGatherResult
}

/** Gathers every text semantic transactionally before recording publishes any frame state. */
internal object GPUPreparedTextSemanticBuilder {
    fun gather(
        visualCommands: List<GPUFramePathVisualCommand>,
        inventory: PreparedTextFrameInventory,
        targetBounds: GPUPixelBounds,
        gatherA8: (GPUPreparedTextA8PayloadInput) -> GPUDrawSemanticPayload.TextA8 =
            GPUPreparedTextPayloadGatherer()::gather,
    ): GPUPreparedTextSemanticGatherResult {
        val textVisuals = visualCommands.filter { visual -> visual.preparedText != null }
        val expectedSubRuns = inventory.subRunsByOperationIndex.values.flatten()
        if (textVisuals.mapNotNull(GPUFramePathVisualCommand::preparedText) != expectedSubRuns) {
            return GPUPreparedTextSemanticGatherResult.Refused(
                code = "invalid.surface.prepared.text-command-bijection",
                commandId = null,
                message = "Prepared text visuals must preserve the exact frame inventory order.",
            )
        }
        if (
            inventory.pages.map(GPUTextA8AtlasPageArtifact::pageIndex).toSet().size !=
            inventory.pages.size ||
            inventory.pages.any { page ->
                page.artifactKey.generation != inventory.generation
            }
        ) {
            return GPUPreparedTextSemanticGatherResult.Refused(
                code = "invalid.surface.prepared.text-page",
                commandId = null,
                message = "Prepared text pages must have unique indexes and the exact frame generation.",
            )
        }
        val pageFactsByIndex = try {
            inventory.pages.associate { page ->
                page.pageIndex to PreparedTextPageSemanticFacts(
                    page = page,
                    atlas = page.toPreparedR8UploadArtifact(),
                    placementBounds = page.placements.mapTo(linkedSetOf()) { placement ->
                        placement.contentRect.let { rect ->
                            GPUPixelBounds(rect.left, rect.top, rect.right, rect.bottom)
                        }
                    },
                )
            }
        } catch (failure: IllegalArgumentException) {
            return GPUPreparedTextSemanticGatherResult.Refused(
                code = "invalid.surface.prepared.text-page",
                commandId = null,
                message = failure.message ?: "Prepared text page validation failed.",
            )
        }
        val colorGatherer = GPUColorGlyphPayloadGatherer()
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>()
        for (visual in textVisuals) {
            val commandId = visual.normalized.commandId.value
            val subRun = requireNotNull(visual.preparedText)
            val normalized = visual.normalized as? NormalizedDrawCommand.DrawTextRun
            val expectedBounds = subRun.instances.preparedTextBounds(
                GPUTargetFacts(
                    width = targetBounds.width,
                    height = targetBounds.height,
                    colorFormat = subRun.draw.targetColorFormat,
                ),
            )
            if (expectedBounds == null ||
                expectedBounds != visual.targetSpaceBounds ||
                normalized == null ||
                normalized.bounds != expectedBounds ||
                normalized.material != null ||
                normalized.preparedMaterial !== subRun.draw.material
            ) {
                return GPUPreparedTextSemanticGatherResult.Refused(
                    code = "invalid.surface.prepared.text-command-facts",
                    commandId = commandId,
                    message = "Prepared text command bounds or material diverged from the exact sub-run.",
                )
            }
            val pageIndex = subRun.pageIndex
                ?: return GPUPreparedTextSemanticGatherResult.Refused(
                    "invalid.surface.prepared.text-page",
                    commandId,
                    "Prepared text sub-run has no atlas page.",
                )
            val pageFacts = pageFactsByIndex[pageIndex]
                ?: return GPUPreparedTextSemanticGatherResult.Refused(
                    "invalid.surface.prepared.text-page",
                    commandId,
                    "Prepared text sub-run page does not belong to the frame inventory.",
                )
            val page = pageFacts.page
            val scissor = visual.clipCoverage.toPreparedScissorBounds(
                targetBounds = targetBounds,
                nonScissorClipRetainedSeparately = true,
            )
                ?: return GPUPreparedTextSemanticGatherResult.Refused(
                    "invalid.surface.prepared.text-scissor",
                    commandId,
                    "Prepared text clip has no valid canonical scissor bounds.",
                )
            val semantic = try {
                when (subRun.representation) {
                    GPUPreparedTextRepresentation.A8_MASK -> {
                        val deviceToLocal = subRun.draw.transform
                            .preparedTextDeviceToLocal()
                            ?: return GPUPreparedTextSemanticGatherResult.Refused(
                                code = GPUTextRefusalCodes.TRANSFORM_SINGULAR,
                                commandId = commandId,
                                message =
                                    "Prepared text Surface transform lost its exact affine inverse.",
                            )
                        gatherA8(
                            GPUPreparedTextA8PayloadInput(
                                commandIdValue = commandId,
                                atlas = pageFacts.atlas,
                                atlasGeneration = inventory.generation,
                                pageIndex = page.pageIndex,
                                instances = subRun.instances,
                                material = subRun.draw.material,
                                deviceToLocal = deviceToLocal,
                                targetBounds = targetBounds,
                                scissorBounds = scissor,
                                clipIdentity = subRun.draw.clipContentKey,
                                blendPlanIdentity =
                                    subRun.draw.blendPlan.canonicalIdentity(),
                                capabilitySnapshotHash =
                                    subRun.draw.capabilitySnapshotHash,
                                frameProvenance = visual.provenance,
                            ),
                        )
                    }
                    GPUPreparedTextRepresentation.COLRV0 -> {
                        val plan = subRun.colorGlyphLayerPlan
                            ?: return GPUPreparedTextSemanticGatherResult.Refused(
                                "invalid.surface.prepared.color-glyph-plan",
                                commandId,
                                "Prepared COLRv0 sub-run lost its exact layer plan.",
                            )
                        val layers = subRun.preparedColorLayers(pageFacts, targetBounds)
                            ?: return GPUPreparedTextSemanticGatherResult.Refused(
                                "invalid.surface.prepared.color-glyph-layers",
                                commandId,
                                "Prepared COLRv0 instances no longer match exact layer and atlas facts.",
                            )
                        colorGatherer.gatherPreparedSemantic(
                            GPUPreparedColorGlyphPayloadInput(
                                commandIdValue = commandId,
                                planArtifactKey = plan.artifactKey,
                                atlasArtifactKey = page.artifactKey,
                                atlas = pageFacts.atlas,
                                instances = subRun.instances,
                                layers = layers,
                                material = subRun.draw.material,
                                globalPaintAlpha = subRun.draw.foregroundColor.a,
                                targetBounds = targetBounds,
                                scissorBounds = scissor,
                                clipIdentity = subRun.draw.clipContentKey,
                                blendPlanIdentity = subRun.draw.blendPlan.canonicalIdentity(),
                                capabilitySnapshotHash = subRun.draw.capabilitySnapshotHash,
                                frameProvenance = visual.provenance,
                            ),
                        )
                    }
                }
            } catch (failure: IllegalArgumentException) {
                return GPUPreparedTextSemanticGatherResult.Refused(
                    code = "invalid.surface.prepared.text-semantic",
                    commandId = commandId,
                    message = failure.message ?: "Prepared text semantic validation failed.",
                )
            }
            semantics[commandId] = semantic
        }
        return GPUPreparedTextSemanticGatherResult.Gathered(semantics)
    }
}

/**
 * Inverts the exact Surface-command affine once at the semantic boundary.
 *
 * Device quads and atlas coordinates are deliberately absent: neither is a
 * coordinate-transform authority.
 */
private fun Matrix33.preparedTextDeviceToLocal(): GPUPreparedTextDeviceToLocalAffine? {
    if (
        persp0 != 0f ||
        persp1 != 0f ||
        persp2 != 1f ||
        listOf(scaleX, skewX, transX, skewY, scaleY, transY).any { !it.isFinite() }
    ) {
        return null
    }
    val determinant = scaleX * scaleY - skewX * skewY
    if (!determinant.isFinite() || determinant == 0f) return null
    val coefficients = floatArrayOf(
        scaleY / determinant,
        -skewX / determinant,
        (skewX * transY - scaleY * transX) / determinant,
        -skewY / determinant,
        scaleX / determinant,
        (skewY * transX - scaleX * transY) / determinant,
    )
    if (coefficients.any { !it.isFinite() }) return null
    return GPUPreparedTextDeviceToLocalAffine(
        m00 = coefficients[0],
        m01 = coefficients[1],
        m02 = coefficients[2],
        m10 = coefficients[3],
        m11 = coefficients[4],
        m12 = coefficients[5],
    )
}

private data class PreparedTextPageSemanticFacts(
    val page: GPUTextA8AtlasPageArtifact,
    val atlas: GPUPreparedR8UploadArtifact,
    val placementBounds: Set<GPUPixelBounds>,
)

private fun GPUPreparedTextSubRun.preparedColorLayers(
    pageFacts: PreparedTextPageSemanticFacts,
    targetBounds: GPUPixelBounds,
): List<GPUColorGlyphLayerPayloadInput>? {
    val plan = colorGlyphLayerPlan ?: return null
    val page = pageFacts.page
    val foreground = draw.foregroundColor
    val foregroundUnmodulated = floatArrayOf(
        foreground.r,
        foreground.g,
        foreground.b,
        1f,
    )
    return instances.map { instance ->
        val sourceGlyph = draw.glyphs.getOrNull(instance.sourceGlyphIndex.value) ?: return null
        val layerIndex = instance.colorLayerIndex ?: return null
        val layer = plan.layers.getOrNull(layerIndex) ?: return null
        if (sourceGlyph.glyphId != plan.baseGlyphID.toInt() ||
            instance.glyphId != layer.layerGlyphID.toInt() ||
            instance.pageIndex != page.pageIndex
        ) {
            return null
        }
        val atlasBounds = GPUPixelBounds(
            left = (instance.uvRect.left * page.width).roundToInt(),
            top = (instance.uvRect.top * page.height).roundToInt(),
            right = (instance.uvRect.right * page.width).roundToInt(),
            bottom = (instance.uvRect.bottom * page.height).roundToInt(),
        )
        if (atlasBounds !in pageFacts.placementBounds) {
            return null
        }
        val deviceBounds = instance.preparedTextPixelBounds(targetBounds) ?: return null
        val color = if (layer.useForeground) {
            foregroundUnmodulated
        } else {
            requireNotNull(layer.resolvedColorArgb).toLinearPremultipliedRgba()
        }
        GPUColorGlyphLayerPayloadInput(
            planArtifactKey = plan.artifactKey,
            layerGlyphID = layer.layerGlyphID,
            paletteIndex = layer.paletteIndex,
            atlasBounds = atlasBounds,
            deviceBounds = deviceBounds,
            premultipliedRgba = color,
            useForeground = layer.useForeground,
            foregroundResolved = true,
            placementProof = GPUColorGlyphAtlasPlacementProofInput(
                atlasArtifactKey = page.artifactKey,
                strikeGlyphId = layer.layerGlyphID.toInt(),
                strikeSize = sourceGlyph.strikeKey.sizePx,
                strikeSubpixelX = sourceGlyph.strikeKey.subpixelX.toRawBits(),
                strikeSubpixelY = sourceGlyph.strikeKey.subpixelY.toRawBits(),
                atlasBounds = atlasBounds,
            ),
            colorLayerIndex = layerIndex,
        )
    }
}

private fun Int.toLinearPremultipliedRgba(): FloatArray {
    val alpha = ((this ushr 24) and 0xff) / 255f
    return floatArrayOf(
        srgbToLinear(((this ushr 16) and 0xff) / 255f) * alpha,
        srgbToLinear(((this ushr 8) and 0xff) / 255f) * alpha,
        srgbToLinear((this and 0xff) / 255f) * alpha,
        alpha,
    )
}

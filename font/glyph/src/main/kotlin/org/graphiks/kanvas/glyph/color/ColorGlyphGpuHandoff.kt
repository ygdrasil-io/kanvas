package org.graphiks.kanvas.glyph.color

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayer
import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey

/**
 * Bridges a resolved COLRv0 [ColorGlyphPlan] (rich, font-side) into the
 * GPU-facing [GPUColorGlyphLayerPlan], carrying each layer's coverage glyph and
 * resolved solid color across the font -> renderer boundary.
 *
 * Identity and generation are assigned by the caller (the planning/renderer
 * integration layer); the content fingerprint is taken from the plan's stable
 * [ColorGlyphPlan.dumpSha256] so content changes invalidate the GPU plan. Layers
 * are ordered bottom -> top by [COLRV0LayerPlan.layerIndex].
 *
 * @param artifactID stable GPU artifact identity assigned by the caller.
 * @param generation GPU artifact generation assigned by the caller.
 * @throws IllegalArgumentException if the plan has no layers, or a layer violates
 * the GPUColorGlyphLayer invariant (resolvedColor non-null iff not foreground).
 *   Resolved COLRv0 plans always satisfy this; the guard catches malformed input.
 */
fun ColorGlyphPlan.toGPUColorGlyphLayerPlan(
    artifactID: GPUTextArtifactID,
    generation: GPUTextArtifactGeneration,
): GPUColorGlyphLayerPlan = GPUColorGlyphLayerPlan(
    artifactKey = GPUTextArtifactKey(
        artifactID = artifactID,
        generation = generation,
        contentFingerprint = dumpSha256,
    ),
    baseGlyphID = glyphId.toUInt(),
    layers = layers
        .sortedBy { layer -> layer.layerIndex }
        .map { layer ->
            GPUColorGlyphLayer(
                layerGlyphID = layer.glyphId.toUInt(),
                paletteIndex = layer.paletteIndex,
                resolvedColorArgb = layer.resolvedColor,
                useForeground = layer.usesForegroundColor,
            )
        },
)

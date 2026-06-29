package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.gpu.renderer.text.ColorGlyphRefusalKind
import org.graphiks.kanvas.gpu.renderer.text.GPUColorGlyphRouteDecision
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes
import org.graphiks.kanvas.gpu.renderer.text.GPUTextRoute

/**
 * Classifies a resolved COLRv0 color glyph plan into an accepted color route or a
 * stable refusal. COLRv0 plans within the layer budget are accepted; plans over
 * the budget are refused with a layer-count diagnostic. Non-COLRv0 color formats
 * (COLRv1, SVG OpenType, emoji sequences) are refused via [refuseUnsupportedColorFormat].
 */
class GPUColorGlyphRoutePlanner {

    /** Accepts a COLRv0 plan within the layer budget, else refuses with a layer-count diagnostic. */
    fun planColorGlyphRoute(plan: GPUColorGlyphLayerPlan): GPUColorGlyphRouteDecision {
        if (plan.layerCount > MAX_COLOR_LAYERS) {
            return GPUColorGlyphRouteDecision.Refused(
                diagnostic = GPUTextDiagnostic(
                    code = GPUTextDiagnosticCodes.COLOR_FONT_LAYER_COUNT_EXCEEDED,
                    message = "COLRv0 glyph ${plan.baseGlyphID} has ${plan.layerCount} " +
                        "layers (max $MAX_COLOR_LAYERS).",
                    terminal = true,
                ),
                refusalKind = ColorGlyphRefusalKind.LAYER_COUNT_EXCEEDED,
            )
        }
        return GPUColorGlyphRouteDecision.Accepted(
            route = GPUTextRoute.ColorGlyph(plan = plan),
        )
    }

    /** Refuses a non-COLRv0 color format (COLRv1, SVG, emoji) with a stable format diagnostic. */
    fun refuseUnsupportedColorFormat(formatLabel: String): GPUColorGlyphRouteDecision.Refused =
        GPUColorGlyphRouteDecision.Refused(
            diagnostic = GPUTextDiagnostic(
                code = GPUTextDiagnosticCodes.COLOR_FONT_FORMAT_UNAVAILABLE,
                message = "Color font format '$formatLabel' is not supported on the GPU color route.",
                terminal = true,
            ),
            refusalKind = ColorGlyphRefusalKind.FORMAT_UNAVAILABLE,
        )

    companion object {
        /** Maximum COLRv0 layers accepted on the single-pass color route. */
        const val MAX_COLOR_LAYERS: Int = 16
    }
}

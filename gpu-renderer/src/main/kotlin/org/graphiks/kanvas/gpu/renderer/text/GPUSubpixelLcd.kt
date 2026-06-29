package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.gpu.renderer.geometry.GPUAtlasEntryRef

enum class GPUPixelGeometry {
    RGBHorizontal,
    BGRHorizontal,
    VRGBVertical,
    VBGRVertical,
}

data class GPUSubpixelCoverageMask(
    val atlasEntry: GPUAtlasEntryRef,
    val rComponent: Float,
    val gComponent: Float,
    val bComponent: Float,
) {
    init {
        require(rComponent in 0f..1f) { "rComponent must be in [0,1]: $rComponent" }
        require(gComponent in 0f..1f) { "gComponent must be in [0,1]: $gComponent" }
        require(bComponent in 0f..1f) { "bComponent must be in [0,1]: $bComponent" }
    }
}

data class GPUSubpixelLCDRenderStep(
    val modulation: GPUPerComponentAlphaModulation,
    val wgslModule: GPUSubpixelLCDWGSL,
)

data class GPUSubpixelLCDPlan(
    val pixelGeometry: GPUPixelGeometry,
    val perComponentMask: GPUSubpixelCoverageMask,
    val renderStep: GPUSubpixelLCDRenderStep,
) {
    companion object {
        fun create(
            pixelGeometry: GPUPixelGeometry,
            r: Float,
            g: Float,
            b: Float,
            atlasEntry: String = "glyph_0",
        ): GPUSubpixelLCDPlan {
            val mask = GPUSubpixelCoverageMask(
                atlasEntry = GPUAtlasEntryRef(atlasEntry),
                rComponent = r,
                gComponent = g,
                bComponent = b,
            )
            val modulationSuffix = when (pixelGeometry) {
                GPUPixelGeometry.RGBHorizontal -> "rgb"
                GPUPixelGeometry.BGRHorizontal -> "bgr"
                GPUPixelGeometry.VRGBVertical -> "vrgb"
                GPUPixelGeometry.VBGRVertical -> "vbgr"
            }
            return GPUSubpixelLCDPlan(
                pixelGeometry = pixelGeometry,
                perComponentMask = mask,
                renderStep = GPUSubpixelLCDRenderStep(
                    modulation = GPUPerComponentAlphaModulation("subpixel_lcd_$modulationSuffix"),
                    wgslModule = GPUSubpixelLCDWGSL(
                        moduleId = "subpixel_lcd_$modulationSuffix",
                        entryPoint = "subpixel_lcd_main",
                    ),
                ),
            )
        }
    }
}

sealed interface GPUSubpixelLCDRouteDecision {
    data class Accepted(val plan: GPUSubpixelLCDPlan) : GPUSubpixelLCDRouteDecision

    data class Refused(val diagnostic: GPUTextDiagnostic) : GPUSubpixelLCDRouteDecision
}

data class GPUSubpixelLCDRouteContext(
    val targetFormat: String,
    val pixelGeometryKnown: Boolean,
    val destinationOpaque: Boolean,
    val destinationReadAvailable: Boolean,
) {
    companion object {
        fun decide(
            ctx: GPUSubpixelLCDRouteContext,
            plan: GPUSubpixelLCDPlan,
        ): GPUSubpixelLCDRouteDecision {
            if (!ctx.pixelGeometryKnown) {
                return GPUSubpixelLCDRouteDecision.Refused(
                    GPUTextDiagnostic(
                        code = GPUTextDiagnosticCodes.SUBPIXEL_PIXEL_GEOMETRY,
                        message = "Adapter does not report pixel geometry",
                        terminal = true,
                    ),
                )
            }
            if (ctx.targetFormat != "rgba8unorm") {
                return GPUSubpixelLCDRouteDecision.Refused(
                    GPUTextDiagnostic(
                        code = GPUTextDiagnosticCodes.SUBPIXEL_TARGET_FORMAT,
                        message = "Target format ${ctx.targetFormat} is not subpixel-compatible (requires rgba8unorm)",
                        terminal = true,
                    ),
                )
            }
            if (!ctx.destinationOpaque && !ctx.destinationReadAvailable) {
                return GPUSubpixelLCDRouteDecision.Refused(
                    GPUTextDiagnostic(
                        code = GPUTextDiagnosticCodes.DESTINATION_READ_UNACCEPTED,
                        message = "Translucent destination without destination-read is not supported for subpixel LCD",
                        terminal = true,
                    ),
                )
            }
            return GPUSubpixelLCDRouteDecision.Accepted(plan)
        }
    }
}

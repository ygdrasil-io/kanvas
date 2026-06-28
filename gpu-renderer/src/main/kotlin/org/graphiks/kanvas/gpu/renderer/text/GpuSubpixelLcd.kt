package org.graphiks.kanvas.gpu.renderer.text

import kotlin.math.max
import kotlin.math.min

enum class GpuPixelGeometry {
    RGBHorizontal,
    BGRHorizontal,
    VRGBVertical,
    VBGRVertical,
}

data class GpuSubpixelCoverageMask(
    val atlasEntry: String,
    var rComponent: Float,
    var gComponent: Float,
    var bComponent: Float,
) {
    init {
        rComponent = min(max(rComponent, 0f), 1f)
        gComponent = min(max(gComponent, 0f), 1f)
        bComponent = min(max(bComponent, 0f), 1f)
    }
}

data class GpuSubpixelLcdRenderStep(
    val modulation: String,
)

data class GpuSubpixelLcdPlan(
    val pixelGeometry: GpuPixelGeometry,
    val coverageMask: GpuSubpixelCoverageMask,
    val renderStep: GpuSubpixelLcdRenderStep,
) {
    companion object {
        fun create(
            pixelGeometry: GpuPixelGeometry,
            r: Float,
            g: Float,
            b: Float,
            atlasEntry: String = "glyph_0",
        ): GpuSubpixelLcdPlan {
            val mask = GpuSubpixelCoverageMask(
                atlasEntry = atlasEntry,
                rComponent = r,
                gComponent = g,
                bComponent = b,
            )
            val modulationName = "subpixel_lcd_" + when (pixelGeometry) {
                GpuPixelGeometry.RGBHorizontal -> "rgb"
                GpuPixelGeometry.BGRHorizontal -> "bgr"
                GpuPixelGeometry.VRGBVertical -> "vrgb"
                GpuPixelGeometry.VBGRVertical -> "vbgr"
            }
            return GpuSubpixelLcdPlan(
                pixelGeometry = pixelGeometry,
                coverageMask = mask,
                renderStep = GpuSubpixelLcdRenderStep(modulation = modulationName),
            )
        }
    }
}

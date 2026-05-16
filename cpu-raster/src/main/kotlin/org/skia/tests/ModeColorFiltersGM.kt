package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/modecolorfilters.cpp::ModeColorFilterGM`
 * (registered name `"modecolorfilters"`, 512 × 1024).
 *
 * Renders a grid of small (`20 × 20`) test rects that exercise
 * [SkColorFilters.Blend] across the 14 Porter-Duff-coefficient blend
 * modes (`kClear`..`kModulate`), 5 source colours, and 4 shader
 * variants (`null` + three "solid"-via-gradient shaders). When the
 * paint has a shader, the *paint colour* loops over a smaller set of
 * 2 alphas instead.
 *
 * Each cell is drawn inside a `saveLayer(rect, null)` so the colour
 * filter composites against a checker `bgPaint` (drawn first inside
 * the layer, then the geometry on top), giving us a transparency
 * preview around each test cell. The layer is sized to the cell rect
 * so neighbouring cells stay independent.
 *
 * **Adaptations vs upstream**:
 *  - Upstream uses a degenerate linear gradient with two coincident
 *    points + identical colours to fabricate a "solid" shader (the GPU
 *    path lacks a pure colour shader). kanvas-skia has the same
 *    constraint and uses the same trick — see [makeColorShader].
 *  - The checker background bitmap is built via [ToolUtils.colorTo565]
 *    to mirror the 565-quantised brown / navy that upstream produces.
 */
public class ModeColorFiltersGM : GM() {

    init { setBGColor(0xFF303030.toInt()) }

    override fun getName(): String = "modecolorfilters"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private var bmpShader: SkShader? = null

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        if (bmpShader == null) {
            bmpShader = makeBgShader(K_CHECK_SIZE)
        }
        val bgPaint = SkPaint().apply {
            shader = bmpShader
            blendMode = SkBlendMode.kSrc
        }

        // null shader = use paint colour; non-null = solid + transparent + trans-black.
        val shaders: Array<SkShader?> = arrayOf(
            null,
            makeColorShader(SkColorSetARGB(0xFF, 0x42, 0x82, 0x21)),
            makeColorShader(SkColorSetARGB(0x80, 0x10, 0x70, 0x20)),
            makeColorShader(0x0),
        )

        // Used without shader (5 paint colours).
        val colors = intArrayOf(
            SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF),
            SkColorSetARGB(0xFF, 0x00, 0x00, 0x00),
            SkColorSetARGB(0x00, 0x00, 0x00, 0x00),
            SkColorSetARGB(0xFF, 0x10, 0x20, 0x42),
            SkColorSetARGB(0xA0, 0x20, 0x30, 0x90),
        )

        // Used with shaders (2 alpha multipliers applied as paint colour).
        val alphas = intArrayOf(0xFFFFFFFF.toInt(), 0x80808080.toInt())

        val modes = arrayOf(
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kDst,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
            SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kXor,
            SkBlendMode.kPlus,
            SkBlendMode.kModulate,
        )

        val paint = SkPaint()
        var idx = 0
        val rectsPerRow = (WIDTH / K_RECT_WIDTH).coerceAtLeast(1)
        for (cfm in modes.indices) {
            for (cfc in colors.indices) {
                paint.colorFilter = SkColorFilters.Blend(colors[cfc], modes[cfm])
                for (s in shaders.indices) {
                    paint.shader = shaders[s]
                    val hasShader = paint.shader == null   // mirrors upstream's quirky condition
                    val paintColors: IntArray = if (hasShader) alphas else colors
                    val paintColorCnt = paintColors.size
                    for (pc in 0 until paintColorCnt) {
                        paint.color = paintColors[pc]
                        val x = (idx % rectsPerRow).toFloat()
                        val y = (idx / rectsPerRow).toFloat()
                        val rect = SkRect.MakeXYWH(
                            x * K_RECT_WIDTH, y * K_RECT_HEIGHT,
                            K_RECT_WIDTH.toFloat(), K_RECT_HEIGHT.toFloat(),
                        )
                        c.saveLayer(rect, null)
                        c.drawRect(rect, bgPaint)
                        c.drawRect(rect, paint)
                        c.restore()
                        ++idx
                    }
                }
            }
        }
    }

    /** `make_color_shader` — a degenerate linear gradient masquerading as a solid colour. */
    private fun makeColorShader(color: SkColor): SkShader = SkLinearGradient.Make(
        SkPoint(0f, 0f),
        SkPoint(1f, 1f),
        intArrayOf(color, color),
        null,
        SkTileMode.kClamp,
    )

    /** `make_bg_shader(checkSize)` — 2×2 checker (brown / navy) tiled across the layer. */
    private fun makeBgShader(checkSize: Int): SkShader {
        val bmp = org.skia.foundation.SkBitmap.Make(2 * checkSize, 2 * checkSize)
        // canvas.clear(brown) is just a fill on the empty bitmap.
        bmp.eraseColor(ToolUtils.colorTo565(0xFF800000.toInt()))
        val navy = ToolUtils.colorTo565(0xFF000080.toInt())
        for (yy in 0 until checkSize) {
            for (xx in 0 until checkSize) {
                bmp.setPixel(xx, yy, navy)                                // rect0
                bmp.setPixel(xx + checkSize, yy + checkSize, navy)        // rect1
            }
        }
        return bmp.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat)
    }

    private companion object {
        const val WIDTH = 512
        const val HEIGHT = 1024
        const val K_RECT_WIDTH = 20
        const val K_RECT_HEIGHT = 20
        const val K_CHECK_SIZE = 10
    }
}

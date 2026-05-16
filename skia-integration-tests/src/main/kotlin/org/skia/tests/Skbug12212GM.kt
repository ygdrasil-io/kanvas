package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorCYAN
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's
 * [`gm/skbug_12212.cpp`](https://github.com/google/skia/blob/main/gm/skbug_12212.cpp)
 * — registered as `DEF_SIMPLE_GM_BG(skbug_12212, canvas, 400, 400, SK_ColorCYAN)`.
 *
 * Validates the Phase G4a `kAlpha_8` drawing path : creates an Alpha_8
 * offscreen surface, draws subpixel-antialiased text into it with
 * `BlendMode::kSrc` and `paint.alpha = 0x80`, then blits the result back
 * onto the cyan-cleared main canvas.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_BG(skbug_12212, canvas, 400, 400, SK_ColorCYAN) {
 *     auto imageInfo = SkImageInfo::Make(400, 400, kAlpha_8_SkColorType,
 *                                        kPremul_SkAlphaType);
 *     SkSurfaceProps props(0, kRGB_H_SkPixelGeometry);
 *     sk_sp<SkSurface> surface;
 *     // ... GPU branch elided — raster fallback below
 *     surface = SkSurfaces::Raster(imageInfo, &props);
 *
 *     SkPaint p;
 *     p.setAntiAlias(true);
 *     p.setBlendMode(SkBlendMode::kSrc);
 *     p.setAlpha(0x80);
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setSize(170);
 *     font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *     auto textBlob = SkTextBlob::MakeFromText("text", 4, font);
 *     surface->getCanvas()->drawTextBlob(textBlob, 50, 350, p);
 *
 *     surface->draw(canvas, 0, 0);
 * }
 * ```
 *
 * `:kanvas-skia` doesn't carry `SkSurfaceProps` (the `kRGB_H_SkPixelGeometry`
 * hint only matters for LCD subpixel layout choice — irrelevant on our
 * full-coverage AA glyph path). `SkFont.Edging.kSubpixelAntiAlias` is
 * accepted but `SkCanvas` downgrades it to `kAntiAlias` for our raster
 * pipeline (see [SkCanvas] KDoc). The structural shape — large
 * half-transparent text on cyan, glyphs hugged by red fringes from
 * LCD-RGB ordering — is what the reference captures ; we render the
 * full-coverage AA equivalent, so a chunk of the per-pixel residual
 * is the fringe difference. Tolerance accordingly loose.
 */
public class Skbug12212GM : GM() {

    init {
        setBGColor(SK_ColorCYAN)
    }

    override fun getName(): String = "skbug_12212"

    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Build the Alpha_8 offscreen surface (400 × 400). On `:kanvas-skia`
        // the raster surface path is the only branch — no GPU fallback.
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeA8(400, 400))

        // Paint: AA, kSrc blend, half-alpha. The kSrc blend mode plus
        // alpha = 0x80 means the surface alpha byte ends up at exactly
        // 0x80 wherever a glyph covers a pixel.
        val paint = SkPaint().apply {
            isAntiAlias = true
            blendMode = SkBlendMode.kSrc
            alpha = 0x80
        }

        // 170pt portable font, subpixel-AA edging. We don't carry an
        // `SkTextBlob.MakeFromText` factory ; reproduce its single-run
        // build via the shared ToolUtils helper.
        val font = ToolUtils.DefaultPortableFont().apply {
            size = 170f
            edging = SkFont.Edging.kSubpixelAntiAlias
        }

        val builder = SkTextBlobBuilder()
        ToolUtils.addToTextBlob(builder, "text", font, 0f, 0f)
        val blob: SkTextBlob = builder.make() ?: return

        surface.canvas.drawTextBlob(blob, 50f, 350f, paint)

        // Blit the Alpha_8 snapshot back onto the cyan main canvas.
        // `surface.draw` snapshots to an SkImage and routes through
        // drawImage with the default sampler.
        surface.draw(c, 0f, 0f)
    }
}

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTextEncoding
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/crbug_478659067.cpp` — regression test that exercises
 * the Graphite glyph-atlas page overflow path by drawing skewed text with
 * `kUseDeviceIndependentFonts_Flag` enabled.
 *
 * **Classification: INTRACTABLE.GRAPHITE_ONLY** — the entire GM is guarded
 * by `#if defined(SK_GRAPHITE)` and explicitly returns
 * `DrawResult::kSkip` with errorMsg "Graphite only" on every other backend.
 * Porting it to the CPU raster path is not meaningful because:
 *
 *  1. `canvas->recorder()` (Graphite recorder) is unavailable on raster.
 *     → [TODO("STUB.GRAPHITE_RECORDER")]
 *  2. `SkSurfaces::RenderTarget(recorder, info, …)` requires a Graphite
 *     context. → [TODO("STUB.GRAPHITE_RENDER_TARGET")]
 *  3. `recorder->priv().caps()->glyphsAsPathsFontSize()` reads Graphite
 *     capability data that has no raster equivalent.
 *     → [TODO("STUB.GRAPHITE_CAPS_GLYPHS_AS_PATHS_FONT_SIZE")]
 *  4. `canvas->getBaseLayerSize()` is not yet exposed in Kotlin [SkCanvas].
 *     → [TODO("STUB.CANVAS_GET_BASE_LAYER_SIZE")]
 *  5. `canvas->imageInfo().refColorSpace()` is not yet exposed in Kotlin
 *     [SkCanvas]. → [TODO("STUB.CANVAS_IMAGE_INFO")]
 *  6. `graphiteCanvas->getSurface()->draw(canvas, 0, 0)` is the Graphite
 *     surface blit back to the parent canvas.
 *     → [TODO("STUB.GRAPHITE_SURFACE_DRAW")]
 *
 * The non-Graphite fast-exit ("Graphite only") mirrors upstream's
 * `DrawResult::kSkip` branch and is the only path reachable by this port.
 *
 * C++ original:
 * ```cpp
 * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *     SkISize size = this->getISize();
 *     if (!canvas->getBaseLayerSize().isEmpty()) {
 *         size = canvas->getBaseLayerSize();
 *     }
 *     SkImageInfo info = SkImageInfo::MakeN32(size.width(), size.height(), kPremul_SkAlphaType,
 *                                             canvas->imageInfo().refColorSpace());
 *     SkSurfaceProps inputProps;
 *     canvas->getProps(&inputProps);
 *     SkSurfaceProps props(SkSurfaceProps::kUseDeviceIndependentFonts_Flag | inputProps.flags(),
 *                          inputProps.pixelGeometry());
 *     sk_sp<SkSurface> surface;
 *
 *     SkScalar dfSize = 162;
 * #if defined(SK_GRAPHITE)
 *     if (auto recorder = canvas->recorder()) {
 *         surface = SkSurfaces::RenderTarget(recorder, info, skgpu::Mipmapped::kNo, &props);
 *         dfSize = recorder->priv().caps()->glyphsAsPathsFontSize();
 *     }
 * #endif
 *     // Effectively make this test graphite only
 *     if (!surface) {
 *         *errorMsg = "Graphite only";
 *         return DrawResult::kSkip;
 *     }
 *
 *     // Create a new canvas with the DeviceIndepdentFonts flag enabled
 *     SkCanvas* graphiteCanvas = surface->getCanvas();
 *     graphiteCanvas->clear(0xffffffff);
 *
 *     const char* dfText = "TheQuickBrownFoxJumpsOverTheLazyDog_0123456789";
 *     const size_t dfLen = strlen(dfText);
 *
 *     SkScalar scale = graphiteCanvas->getLocalToDeviceAs3x3().getMaxScale();
 *     if (scale <= 0.0f) {
 *         scale = 1.0f;
 *     }
 *
 *     SkFont font(ToolUtils::CreatePortableTypeface("serif", SkFontStyle()));
 *     font.setSize((dfSize - 64) / scale);
 *     font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *     font.setSubpixel(true);
 *
 *     SkPaint paint;
 *     paint.setColor(SK_ColorBLACK);
 *
 *     SkScalar lineSpacing = font.getSize();
 *     for (int i = 1; i <= 6; ++i) {
 *         SkAutoCanvasRestore acr(graphiteCanvas, true);
 *         graphiteCanvas->translate(SkIntToScalar(10), (i * lineSpacing) - 10);
 *         font.setSkewX(i * 0.05f);
 *         graphiteCanvas->drawSimpleText(dfText, dfLen, SkTextEncoding::kUTF8, 0, 0, font, paint);
 *     }
 *
 *     graphiteCanvas->getSurface()->draw(canvas, 0, 0);
 *     return DrawResult::kOk;
 * }
 * ```
 */
public class CrBug478659067GM : GM() {

    init {
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String = "crbug_478659067"
    override fun getISize(): SkISize = SkISize.Make(1024, 1280)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ── Graphite recorder check ────────────────────────────────────────
        // Upstream: `if (auto recorder = canvas->recorder())` — only proceeds
        // on a Graphite canvas. On CPU raster this is always null and the GM
        // skips (mirrors DrawResult::kSkip "Graphite only").
        //
        // TODO("STUB.GRAPHITE_RECORDER") — SkCanvas.recorder() is not yet
        // exposed in the Kotlin raster backend. When Graphite support lands,
        // replace this early return with the full draw path below.
        //
        // TODO("STUB.GRAPHITE_RENDER_TARGET") — SkSurfaces.RenderTarget(
        //     recorder, info, Mipmapped.kNo, props) requires Graphite context.
        //
        // TODO("STUB.GRAPHITE_CAPS_GLYPHS_AS_PATHS_FONT_SIZE") —
        //     recorder.priv().caps().glyphsAsPathsFontSize() reads Graphite
        //     capability data unavailable on the raster backend.
        //
        // TODO("STUB.CANVAS_GET_BASE_LAYER_SIZE") — SkCanvas.getBaseLayerSize()
        //     is not yet exposed in the Kotlin SkCanvas wrapper.
        //
        // TODO("STUB.CANVAS_IMAGE_INFO") — SkCanvas.imageInfo().refColorSpace()
        //     is not yet exposed in the Kotlin SkCanvas wrapper.
        //
        // TODO("STUB.GRAPHITE_SURFACE_DRAW") — SkSurface.draw(canvas, 0, 0)
        //     blits a Graphite render-target back to the parent canvas.
        //
        // Non-Graphite fast-exit — mirrors upstream's "Graphite only" kSkip.
        return

        // ── Full Graphite draw path (unreachable on raster) ───────────────
        // Kept here as documentation of what would run on Graphite once all
        // stubs above are implemented.
        @Suppress("UNREACHABLE_CODE")
        run {
            val dfText = "TheQuickBrownFoxJumpsOverTheLazyDog_0123456789"
            // dfSize would come from recorder.priv().caps().glyphsAsPathsFontSize()
            val dfSize = 162f

            // The Graphite surface would be created via:
            //   SkSurfaces.RenderTarget(recorder, info, Mipmapped.kNo, props)
            // where props includes kUseDeviceIndependentFonts_Flag.
            // On raster we cannot proceed, so this block is dead code.

            val inputProps = c.surfaceProps()
            @Suppress("UNUSED_VARIABLE")
            val props = SkSurfaceProps(
                flags = SkSurfaceProps.kUseDeviceIndependentFonts_Flag or inputProps.flags,
                pixelGeometry = inputProps.pixelGeometry,
            )

            // scale = graphiteCanvas.getLocalToDeviceAs3x3().getMaxScale()
            val scale = c.getLocalToDeviceAsMatrix()?.getMaxScale()?.takeIf { it > 0f } ?: 1f

            val font = SkFont(ToolUtils.CreatePortableTypeface("serif", SkFontStyle())).apply {
                size = (dfSize - 64f) / scale
                edging = SkFont.Edging.kSubpixelAntiAlias
                isSubpixel = true
            }
            val paint = SkPaint().apply { color = SK_ColorBLACK }
            val lineSpacing = font.size

            for (i in 1..6) {
                c.withSave {
                    translate(10f, i * lineSpacing - 10f)
                    font.skewX = i * 0.05f
                    drawSimpleText(dfText, dfText.length, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
                }
            }
        }
    }
}

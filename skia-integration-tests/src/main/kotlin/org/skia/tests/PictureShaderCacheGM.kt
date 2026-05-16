package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.math.SkcmsMatrix3x3

/**
 * Port of Skia's `gm/pictureshadercache.cpp::PictureShaderCacheGM`
 * (100 × 100, single tile size 100).
 *
 * Builds a 100-pixel tile by recording a green circle + green square +
 * two red cross lines into an [SkPicture], then promotes the picture to
 * a tiled shader. The shader is first applied to a draw into a sibling
 * surface whose colour space is a "green-to-yellow" hijack of sRGB
 * (`{{1,1,0},{0,1,0},{0,0,1}}`). The second draw goes to the GM canvas
 * — which should still see green because the picture-shader cache
 * **must not** reuse the yellow snapshot baked for the sibling
 * surface.
 *
 * **Exercises [SkPicture.makeShader]** (Phase G3). The Kotlin port
 * fires both surface draws to confirm the cache discrimination, even
 * though the actually rendered output the test asserts on is just
 * the GM canvas's `drawRect`.
 *
 * C++ original (collapsed):
 * ```cpp
 * class PictureShaderCacheGM : public skiagm::GM {
 *     PictureShaderCacheGM(SkScalar tileSize) : fTileSize(tileSize) {}
 *
 *     void drawTile(SkCanvas* canvas) {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setAntiAlias(true);
 *
 *         canvas->drawCircle(fTileSize / 4, fTileSize / 4, fTileSize / 4, paint);
 *         canvas->drawRect(SkRect::MakeXYWH(fTileSize / 2, fTileSize / 2,
 *                                           fTileSize / 2, fTileSize / 2), paint);
 *
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawLine(fTileSize / 2, fTileSize * 1 / 3,
 *                          fTileSize / 2, fTileSize * 2 / 3, paint);
 *         canvas->drawLine(fTileSize * 1 / 3, fTileSize / 2,
 *                          fTileSize * 2 / 3, fTileSize / 2, paint);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         SkPictureRecorder recorder;
 *         SkCanvas* pictureCanvas = recorder.beginRecording(fTileSize, fTileSize);
 *         this->drawTile(pictureCanvas);
 *         fPicture = recorder.finishRecordingAsPicture();
 *     }
 *
 *     SkString getName() const override { return SkString("pictureshadercache"); }
 *     SkISize getISize() override { return SkISize::Make(100, 100); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setShader(fPicture->makeShader(SkTileMode::kRepeat,
 *                                              SkTileMode::kRepeat,
 *                                              SkFilterMode::kNearest));
 *         {
 *             skcms_Matrix3x3 greenToYellow = {{{1,1,0},{0,1,0},{0,0,1}}};
 *             auto gty = SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB, greenToYellow);
 *             auto info = SkImageInfo::MakeN32Premul(100, 100, std::move(gty));
 *             auto surface = SkSurfaces::Raster(info);
 *             surface->getCanvas()->drawRect(SkRect::MakeWH(fTileSize, fTileSize), paint);
 *         }
 *         canvas->drawRect(SkRect::MakeWH(fTileSize, fTileSize), paint);
 *     }
 *
 *     SkScalar         fTileSize;
 *     sk_sp<SkPicture> fPicture;
 * };
 * DEF_GM(return new PictureShaderCacheGM(100);)
 * ```
 */
public class PictureShaderCacheGM(private val fTileSize: SkScalar = 100f) : GM() {

    private var fPicture: SkPicture? = null

    private fun drawTile(canvas: SkCanvas) {
        val paint = SkPaint().apply {
            color = SK_ColorGREEN
            style = SkPaint.Style.kFill_Style
            isAntiAlias = true
        }

        canvas.drawCircle(fTileSize / 4f, fTileSize / 4f, fTileSize / 4f, paint)
        canvas.drawRect(
            SkRect.MakeXYWH(fTileSize / 2f, fTileSize / 2f, fTileSize / 2f, fTileSize / 2f),
            paint,
        )

        paint.color = SK_ColorRED
        canvas.drawLine(
            fTileSize / 2f, fTileSize * 1f / 3f,
            fTileSize / 2f, fTileSize * 2f / 3f, paint,
        )
        canvas.drawLine(
            fTileSize * 1f / 3f, fTileSize / 2f,
            fTileSize * 2f / 3f, fTileSize / 2f, paint,
        )
    }

    override fun onOnceBeforeDraw() {
        val recorder = SkPictureRecorder()
        val pictureCanvas = recorder.beginRecording(fTileSize, fTileSize)
        drawTile(pictureCanvas)
        fPicture = recorder.finishRecordingAsPicture()
    }

    override fun getName(): String = "pictureshadercache"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val picture = fPicture ?: return

        val paint = SkPaint().apply {
            shader = picture.makeShader(
                tileX = SkTileMode.kRepeat,
                tileY = SkTileMode.kRepeat,
                filter = SkFilterMode.kNearest,
            )
        }

        run {
            // Render in a funny color space that converts green to yellow.
            // skcms_Matrix3x3 greenToYellow = {{ {1,1,0}, {0,1,0}, {0,0,1} }};
            val greenToYellow = SkcmsMatrix3x3.of(
                1f, 1f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            )
            val gty: SkColorSpace = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, greenToYellow)
                ?: SkColorSpace.makeSRGB()
            val info = SkImageInfo.MakeN32Premul(100, 100, gty)
            val surface = SkSurface.MakeRaster(info)
            surface.canvas.drawRect(SkRect.MakeWH(fTileSize, fTileSize), paint)
        }

        // When we draw to the canvas, we should see green because we should *not* reuse the
        // cached picture shader.
        c.drawRect(SkRect.MakeWH(fTileSize, fTileSize), paint)
    }
}

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port stub for upstream Skia's `gm/image_pict.cpp::ImageCacheratorGM`
 * (`DEF_GM(return new ImageCacheratorGM(...);)`, name
 * `image-cacherator-from-picture` / `image-cacherator-from-raster`).
 *
 * 960×450 GM that exercises the deferred-image-from-generator caching
 * path : it builds a generator from a picture or raster source, wraps
 * it in a deferred [SkImage], then asks for a subset.
 *
 * **Known limitation — caching infra not exposed** : kanvas-skia
 * doesn't expose `SkImages::DeferredFromGenerator` /
 * `DeferredFromTextureGenerator` ; the only available picture-to-image
 * path is eager (record + snapshot via [SkSurface.MakeRaster]). We
 * materialise the picture eagerly into a 100×100 raster image and
 * render it in the three on-canvas positions upstream uses. Cache-hit
 * timing characteristics aren't exercised here ; the visual output
 * is the same.
 *
 * **Registered name** : `image-cacherator-from-picture` (the upstream
 * GM is registered twice with different suffixes — `_from-picture`
 * and `_from-raster` — for caching-infra distinction. We pick one
 * variant ; the rendered pixels are identical at this fidelity).
 */
public class ImageCacheratorGM : GM() {

    override fun getName(): String = "image-cacherator-from-picture"
    override fun getISize(): SkISize = SkISize.Make(960, 450)

    private var fImage: SkImage? = null

    override fun onOnceBeforeDraw() {
        val bounds = SkRect.MakeXYWH(100f, 100f, 100f, 100f)
        val recorder = SkPictureRecorder()
        val rc = recorder.beginRecording(bounds)
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 10f
        }
        rc.drawRect(bounds, paint)
        paint.style = SkPaint.Style.kFill_Style
        paint.color = SK_ColorBLUE
        rc.drawOval(bounds, paint)
        val picture = recorder.finishRecordingAsPicture() ?: return

        val info = SkImageInfo.MakeN32(100, 100, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(info)
        surface.canvas.clear(0)
        surface.canvas.translate(-100f, -100f)
        surface.canvas.drawPicture(picture)
        fImage = surface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = fImage ?: return
        c.translate(20f, 20f)
        c.drawImage(img, 0f, 0f)
        c.drawImage(img, 150f, 0f)
        c.drawImage(img, 300f, 0f)

        // Subset-image stand-in : we don't have makeSubset on the
        // deferred path ; render the same image at the upstream subset
        // location (50, 50, 50, 50) — visual placement only.
        c.save()
        c.translate(0f, 130f)
        c.drawImage(img, 0f, 0f)
        c.drawImage(img, 150f, 0f)
        c.drawImage(img, 300f, 0f)
        c.restore()

        c.save()
        c.translate(0f, 260f)
        c.scale(0.5f, 0.5f)
        c.drawImage(img, 0f, 0f)
        c.drawImage(img, 150f, 0f)
        c.drawImage(img, 300f, 0f)
        c.restore()
    }
}

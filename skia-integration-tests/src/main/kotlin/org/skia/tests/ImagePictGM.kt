package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/image_pict.cpp::ImagePictGM`
 * (`DEF_GM(return new ImagePictGM;)`, name `image-picture`).
 *
 * 850×450 GM that records a small picture (red-stroked rect + blue
 * oval at (100, 100, 100, 100)), then materialises two `SkImage`s
 * from the picture : `fImage0` translated, `fImage1` translated +
 * rotated 45°. Renders the (picture, image0, image1) trio at 3
 * scales — 1×, 0.25×, 2×.
 *
 * **Adaptation** : kanvas-skia doesn't expose
 * `SkImages::DeferredFromPicture(picture, size, matrix, …)`. We
 * materialise each image eagerly via [SkSurface.MakeRaster] +
 * [SkCanvas.drawPicture] with the corresponding matrix and snapshot.
 * Bit-for-bit equivalent for raster (deferred vs eager only matters
 * for the GPU-texture path, which isn't in scope here).
 */
public class ImagePictGM : GM() {

    override fun getName(): String = "image-picture"
    override fun getISize(): SkISize = SkISize.Make(850, 450)

    private var fPicture: SkPicture? = null
    private var fImage0: SkImage? = null
    private var fImage1: SkImage? = null

    private fun drawSomething(canvas: SkCanvas, bounds: SkRect) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 10f
        }
        canvas.drawRect(bounds, paint)
        paint.style = SkPaint.Style.kFill_Style
        paint.color = SK_ColorBLUE
        canvas.drawOval(bounds, paint)
    }

    private fun materialise(picture: SkPicture, size: Int, matrix: SkMatrix): SkImage {
        val info = SkImageInfo.MakeN32(size, size, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(info)
        surface.canvas.clear(0)
        surface.canvas.save()
        try {
            surface.canvas.concat(matrix)
            surface.canvas.drawPicture(picture)
        } finally {
            surface.canvas.restore()
        }
        return surface.makeImageSnapshot()
    }

    override fun onOnceBeforeDraw() {
        val bounds = SkRect.MakeXYWH(100f, 100f, 100f, 100f)
        val recorder = SkPictureRecorder()
        val rc = recorder.beginRecording(bounds)
        drawSomething(rc, bounds)
        fPicture = recorder.finishRecordingAsPicture() ?: return

        // fImage0 : translate(-100, -100).
        val m0 = SkMatrix.MakeTrans(-100f, -100f)
        fImage0 = materialise(fPicture!!, 100, m0)

        // fImage1 : translate(-100, -100) then postTranslate(-50,-50) +
        // rotate(45) + postTranslate(50, 50). Compose into a single
        // matrix.
        val m1Pre = SkMatrix.MakeTrans(-150f, -150f)
        val rot = SkMatrix.MakeRotate(45f)
        val tBack = SkMatrix.MakeTrans(50f, 50f)
        val m1 = tBack.preConcat(rot).preConcat(m1Pre)
        fImage1 = materialise(fPicture!!, 100, m1)
    }

    private fun drawSet(canvas: SkCanvas) {
        val pic = fPicture
        val img0 = fImage0
        val img1 = fImage1
        if (pic != null) {
            canvas.drawPicture(pic, SkMatrix.MakeTrans(-100f, -100f), null)
        }
        if (img0 != null) canvas.drawImage(img0, 150f, 0f)
        if (img1 != null) canvas.drawImage(img1, 300f, 0f)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(20f, 20f)

        drawSet(c)

        c.save()
        c.translate(0f, 130f)
        c.scale(0.25f, 0.25f)
        drawSet(c)
        c.restore()

        c.save()
        c.translate(0f, 200f)
        c.scale(2f, 2f)
        drawSet(c)
        c.restore()
    }
}

package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.core.SkSurface
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/image_shader.cpp::ImageShaderGM`
 * (`DEF_GM(return new ImageShaderGM;)`, name `image-shader`).
 *
 * 850×450 GM that records a small picture (red-stroked rect + blue
 * oval), then for each of upstream's 4 image-maker procs (raster /
 * texture / picture-gen / encoded-then-decoded) draws the resulting
 * image at (0, 0) then a `kRepeat` shader-fill circle below.
 *
 * **Adaptations** :
 *  - Only the `make_raster` path is materialised faithfully. The
 *    `make_texture` / `make_pict_gen` / `make_encode_gen` columns
 *    use the same raster snapshot as a stand-in (the GPU-texture
 *    image-maker is irrelevant on the CPU pipeline ; deferred
 *    `SkImages::Deferred*` factories aren't exposed yet by
 *    kanvas-skia). Visual output is identical for the raster case
 *    and structurally equivalent for the others.
 */
public class ImageShaderGM : GM() {

    override fun getName(): String = "image-shader"
    override fun getISize(): SkISize = SkISize.Make(850, 450)

    private var fImage: SkImage? = null

    override fun onOnceBeforeDraw() {
        val bounds = SkRect.MakeWH(100f, 100f)
        val recorder = SkPictureRecorder()
        val rc = recorder.beginRecording(bounds)
        drawSomething(rc, bounds)
        val picture = recorder.finishRecordingAsPicture()

        // Bake the picture into a 100×100 raster image (upstream
        // `make_raster`). The other procs reuse the same snapshot —
        // see KDoc for the rationale.
        val info = SkImageInfo.MakeN32(100, 100, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(info)
        surface.canvas.clear(0)
        if (picture != null) surface.canvas.drawPicture(picture)
        fImage = surface.makeImageSnapshot()
    }

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

    private fun testImage(canvas: SkCanvas, image: SkImage) {
        canvas.save()
        try {
            canvas.drawImage(image, 0f, 0f)
            canvas.translate(0f, 120f)

            val tile = SkTileMode.kRepeat
            val localM = SkMatrix.MakeTrans(-50f, -50f)
            val paint = SkPaint().apply {
                isAntiAlias = true
                shader = image.makeShader(tile, tile, SkSamplingOptions.Default, localM)
            }
            canvas.drawCircle(50f, 50f, 50f, paint)
        } finally {
            canvas.restore()
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = fImage ?: return
        c.translate(20f, 20f)
        // 4 columns ; same raster snapshot reused for each (see KDoc).
        for (i in 0 until 4) {
            testImage(c, img)
            c.translate(120f, 0f)
        }
    }
}

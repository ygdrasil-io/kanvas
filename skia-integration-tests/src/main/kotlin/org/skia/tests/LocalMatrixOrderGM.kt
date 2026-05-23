package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/localmatrixshader.cpp::LocalMatrixOrder`](https://github.com/google/skia/blob/main/gm/localmatrixshader.cpp).
 *
 * Verifies that `makeWithLocalMatrix` post-concatenates to the shader's local
 * matrix in the correct order. Two images are composited together (mandrill
 * and example_5), both rotated 45° about the canvas centre and blended via
 * `kModulate`. The blended shader is then further rotated by `fAngle` (driven
 * by `onAnimate`; `0` for static renders) to demonstrate that the rotation
 * accumulates as expected.
 *
 * **imageInfo / canvas centre** — upstream obtains the canvas centre via
 * `canvas->imageInfo().bounds().center()`. Since `SkCanvas::imageInfo()` is
 * not yet in kanvas-skia's surface, we derive the same centre from
 * [getISize]: `(fWidth / 2, fHeight / 2)`. For static GM renders (identity
 * CTM) this is identical.  The `ictm.invert()` viewer-DPI-scaling path is
 * replicated using [SkCanvas.getTotalMatrix].
 *
 * C++ original (full source in `gm/localmatrixshader.cpp`).
 */
public class LocalMatrixOrderGM : GM() {

    override fun getName(): String = "localmatrix_order"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onOnceBeforeDraw() {
        val mandrill = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return
        val example5 = ToolUtils.GetResourceAsImage("images/example_5.png") ?: return

        // mandrill shader: rotate 45° about its centre (128, 128), kRepeat tiling.
        val mShader = mandrill.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            sampling = SkSamplingOptions(SkFilterMode.kNearest),
            localMatrix = SkMatrix.MakeRotate(45f, 128f, 128f),
        )

        // example_5 shader: scale 2× (so it matches mandrill 256×256 footprint) …
        var eShader: SkShader = example5.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            sampling = SkSamplingOptions(SkFilterMode.kNearest),
            localMatrix = SkMatrix.MakeScale(2f, 2f),
        )
        // … then rotate 45° about centre (128, 128) via makeWithLocalMatrix.
        eShader = eShader.makeWithLocalMatrix(SkMatrix.MakeRotate(45f, 128f, 128f))

        // blend the two aligned images via kModulate.
        fShader = SkShaders.Blend(SkBlendMode.kModulate, mShader, eShader)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val shader = fShader ?: return

        // Determine the device-space centre of the canvas.
        // Upstream: `auto center = SkRect::Make(canvas->imageInfo().bounds()).center()`
        // We replicate this using the GM size, which matches the raster surface size.
        var center = SkPoint(getISize().width / 2f, getISize().height / 2f)

        // Upstream applies viewer-inserted DPI scaling guard:
        // `if (auto ictm = canvas->getTotalMatrix(); ictm.invert(&ictm)) { center = ictm.mapPoint(center) }`
        val ictm = c.getTotalMatrix().invert()
        if (ictm != null) {
            center = ictm.mapXY(center)
        }

        // Rotate fShader about the canvas centre by the animated angle.
        val rotated = shader.makeWithLocalMatrix(SkMatrix.MakeRotate(fAngle, center.fX, center.fY))

        val paint = SkPaint().apply { this.shader = rotated }
        c.drawPaint(paint)
    }

    override fun onAnimate(nanos: Double): Boolean {
        // TimeUtils::NanosToSeconds(nanos) * 5.f
        fAngle = (nanos / 1_000_000_000.0 * 5.0).toFloat()
        return true
    }

    private var fShader: SkShader? = null
    private var fAngle: Float = 0f
}

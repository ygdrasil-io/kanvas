package org.skia.tests

import org.skia.core.SaveLayerFlags
import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(savelayer_initfromprev,
 * canvas, 256, 256)`.
 *
 * Exercises the `kInitWithPrevious_SaveLayerFlag` path. The GM:
 *   1. Draws the `mandrill_256.png` resource at `(0, 0)`.
 *   2. Opens a layer with `kInitWithPrevious` + a `kPlus` blend paint.
 *   3. Punches a transparent hole in the layer with `kClear` + a 96-radius
 *      circle at `(128, 128)`.
 *   4. Restores ; the `kInitWithPrevious` seed means the un-punched
 *      pixels are double-bright (mandrill compounded with mandrill via
 *      `kPlus`), while the punched-out hole shows the original mandrill
 *      from below.
 *
 * **Status note** : `:kanvas-skia` ignores the
 * `kInitWithPrevious_SaveLayerFlag` bit by design (see
 * [BackdropImagefilterCroprectGM]) — it always allocates a fresh
 * transparent layer. This means the post-restore image will lack the
 * "double-bright" pass and the GM is expected to diverge from the
 * upstream reference outside the punched hole. The reference image is
 * still loaded for the harness ratchet.
 */
public class SaveLayerGM : GM() {

    override fun getName(): String = "savelayer_initfromprev"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val mandrill = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return
        c.drawImage(mandrill, 0f, 0f)

        val layerPaint = SkPaint().apply {
            blendMode = SkBlendMode.kPlus
        }
        c.saveLayer(SaveLayerRec(
            bounds = null,
            paint = layerPaint,
            backdrop = null,
            flags = INIT_WITH_PREVIOUS_FLAG,
        ))

        val clearPaint = SkPaint().apply {
            blendMode = SkBlendMode.kClear
        }
        c.drawCircle(128f, 128f, 96f, clearPaint)
        c.restore()
    }
}

private const val INIT_WITH_PREVIOUS_FLAG: SaveLayerFlags = 1 shl 1

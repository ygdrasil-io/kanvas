package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/colorwheel.cpp::DEF_SIMPLE_GM(colorwheel, canvas, 384, 256)`.
 *
 * Tests whether image decoders properly decode each colour channel. The GM
 * draws a checkerboard background and overlays up to four decoded images of
 * `images/color_wheel.*` (PNG top-left, GIF top-middle, WEBP bottom-left,
 * JPEG bottom-middle) and optionally an AVIF (top-right) when the codec is
 * available.
 *
 * **Adaptation for `:kanvas-skia`** : only PNG and JPEG decoders are bundled
 * in the raster facade (GIF support exists but only `box.gif` / animated GIF
 * are in the test-resource tree, not `color_wheel.gif`; WEBP and AVIF are
 * not registered). Missing images produce a silent no-op (the checkerboard
 * remains visible in their slot), exactly as upstream's `draw_image` does
 * when the resource is absent.
 *
 * Default checkerboard colours (`0xFF999999` / `0xFF666666`, size 8) match
 * upstream's `ToolUtils::draw_checkerboard(canvas)` no-args overload
 * (`tools/ToolUtils.h:92`).
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(colorwheel, canvas, 384, 256) {
 *     ToolUtils::draw_checkerboard(canvas);
 *     draw_image(canvas, "images/color_wheel.png",  0,   0);    // top left
 *     draw_image(canvas, "images/color_wheel.gif",  128, 0);    // top middle
 *     draw_image(canvas, "images/color_wheel.webp", 0,   128);  // bottom left
 *     draw_image(canvas, "images/color_wheel.jpg",  128, 128);  // bottom middle
 * #if defined(SK_CODEC_DECODES_AVIF)
 *     draw_image(canvas, "images/color_wheel.avif", 256, 0);    // top right
 * #endif
 * }
 * ```
 */
public class ColorWheelGM : GM() {

    override fun getName(): String = "colorwheel"
    override fun getISize(): SkISize = SkISize.Make(384, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Default checkerboard: 0xFF999999 / 0xFF666666, 8-pixel tiles.
        ToolUtils.draw_checkerboard(c, 0xFF999999.toInt(), 0xFF666666.toInt(), 8)

        drawImage(c, "images/color_wheel.png",  0,   0)    // top left
        drawImage(c, "images/color_wheel.gif",  128, 0)    // top middle (may be absent)
        drawImage(c, "images/color_wheel.webp", 0,   128)  // bottom left (may be absent)
        drawImage(c, "images/color_wheel.jpg",  128, 128)  // bottom middle
        // AVIF: SK_CODEC_DECODES_AVIF — not available in :kanvas-skia, skip.
    }

    /**
     * Mirrors upstream's file-local `draw_image(canvas, resource, x, y)`.
     * Loads the resource via [ToolUtils.GetResourceAsImage] and draws it at
     * `(x, y)` using integer-to-float coercion. Silently no-ops when the
     * resource is missing or the codec does not recognise the format —
     * identical to upstream's `if (image) { canvas->drawImage(…); }` branch.
     */
    private fun drawImage(canvas: SkCanvas, resource: String, x: Int, y: Int) {
        val image = ToolUtils.GetResourceAsImage(resource) ?: return
        canvas.drawImage(image, x.toFloat(), y.toFloat())
    }
}

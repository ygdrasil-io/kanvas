package org.skia.dm

import org.skia.svg.SkSVGCanvas
import org.skia.tests.GM
import java.io.StringWriter

/**
 * Vector sink that drives a GM through [SkSVGCanvas] and returns the
 * resulting SVG document as bytes. Slice **B2.5** of the SVG mini
 * plan ([MIGRATION_PLAN_SVG.md](../../../../../../../MIGRATION_PLAN_SVG.md))
 * — closes the SVG chantier by wiring the canvas into the DM matrix.
 *
 * Mirrors upstream Skia's `dm::SVGSink` ; the only configuration knob
 * is a no-op (upstream accepts a "stylesheet" url but our canvas
 * doesn't use one). The sink is stateless and reusable across draws.
 *
 * **Output shape** :
 *  - Returns [Sink.Result.Bytes] on success ; the bytes are UTF-8
 *    encoded SVG ready to be written to a `.svg` file or served
 *    over HTTP under `image/svg+xml`.
 *  - Returns [Sink.Result.Error] on any exception during the GM's
 *    `onDraw` — the message survives in [Runner]'s failure list.
 *
 * **DM matrix integration** :
 *  - [tag] = `"svg"` ; [fileExtension] = `"svg"`. [DmCli] registers
 *    the tag in `KNOWN_CONFIGS` so a `--config svg` flag resolves to
 *    a fresh [SvgSink].
 *  - [Runner] picks up [Sink.Result.Bytes] via the new variant and
 *    builds the per-record `md5` directly over the byte payload — no
 *    PNG re-encode (vector formats are their own canonical form).
 */
public class SvgSink : Sink {

    override val tag: String = TAG

    override val fileExtension: String = "svg"

    override fun draw(src: GM): Sink.Result {
        val sw = StringWriter()
        val size = src.size()
        return try {
            val canvas = SkSVGCanvas(sw, size.width.toFloat(), size.height.toFloat())
            // The GM contract documents that bgColor() is the canvas
            // background. Raster sinks erase the bitmap to bgColor()
            // before drawing ; for SVG we'd emit a full-viewport rect
            // — but bgColor defaults to white and the SVG default
            // background is transparent, so emitting a white rect for
            // every GM that uses the default would just be noise.
            // Instead, emit the bg only when the GM has overridden it.
            val bg = src.bgColor()
            if (bg != org.skia.foundation.SK_ColorWHITE) {
                val paint = org.skia.foundation.SkPaint(bg)
                canvas.drawRect(
                    org.skia.math.SkRect.MakeWH(size.width.toFloat(), size.height.toFloat()),
                    paint,
                )
            }
            src.draw(canvas)
            canvas.flush()
            Sink.Result.Bytes(sw.toString().toByteArray(Charsets.UTF_8), MIME_TYPE)
        } catch (e: Throwable) {
            Sink.Result.Error("SvgSink[${src.name()}]: ${e.message ?: e::class.simpleName}")
        }
    }

    public companion object {
        /** DM tag matching upstream's `--config svg`. */
        public const val TAG: String = "svg"

        /** Standard MIME type for SVG documents per RFC 7996 / W3C SVG 2. */
        public const val MIME_TYPE: String = "image/svg+xml"
    }
}

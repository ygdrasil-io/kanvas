package org.skia.tests

import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor4f
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1177833.cpp` (`DEF_SIMPLE_GM(crbug_1177833,
 * canvas, 400, 400)`).
 *
 * Reproduces three "bad quads" dumped from SkiaRenderer in
 * crbug.com/1178833 — each one used to expose a 3D inset / outset
 * collapse bug in the GPU AA-quad shader. On CPU raster they should
 * all render as really thin lines.
 *
 * Skia source verbatim, including the `SkBits2Float` hex-encoded
 * matrix / rect / clip / colour constants (we use [Float.fromBits] for
 * bit-exact parity). The `SkBlendMode(0x00000003)` literal is
 * `SkBlendMode.kSrcOver` (zero-indexed `kClear, kSrc, kDst, kSrcOver,
 * ...`). The aaFlags `0x00000002` = `kTop_QuadAAFlag` and
 * `0x00000004` = `kRight_QuadAAFlag`.
 *
 * **CPU AA semantics.** Upstream's `SkDevice::drawEdgeAAQuad` only
 * applies AA when *all* edges are flagged (`aa == kAll_QuadAAFlags`).
 * The three quads here use single-edge masks (`kTop`, `kTop`,
 * `kRight`) so AA is disabled on the CPU back-end — the rendered
 * lines are hard-edged. Reference image `crbug_1177833.png` is the
 * upstream CPU render, so the parity test exercises the
 * non-AA-quad-collapse path.
 */
public class Crbug1177833GM : GM() {
    override fun getName(): String = "crbug_1177833"
    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.clear(SK_ColorBLACK)
        c.translate(-700f, -700f)

        // ── First quad (top-edge AA). ──────────────────────────────────
        run {
            val ctm = SkMatrix.MakeAll(
                Float.fromBits(0xbf79250e.toInt()),
                Float.fromBits(0x3e9da860),
                Float.fromBits(0x44914c8a),
                Float.fromBits(0xbf982962.toInt()),
                Float.fromBits(0xbf280002.toInt()),
                Float.fromBits(0x44c3116e),
                Float.fromBits(0xba9bfe62.toInt()),
                Float.fromBits(0x39d10455),
                Float.fromBits(0x3fc9b377),
            )
            val rect = SkRect.MakeLTRB(
                Float.fromBits(0x00000000),
                Float.fromBits(0x00000000),
                Float.fromBits(0x40a00000),
                Float.fromBits(0x43560000),
            )
            val clip = arrayOf(
                SkPoint(Float.fromBits(0x409fff57), Float.fromBits(0x40c86a18)),
                SkPoint(Float.fromBits(0x409fff57), Float.fromBits(0x4314dc8c)),
                SkPoint(Float.fromBits(0x407f6b0d), Float.fromBits(0x43157fff)),
                SkPoint(Float.fromBits(0x4040859c), Float.fromBits(0x43140374)),
            )
            val aaFlags = 0x00000002 // kTop_QuadAAFlag
            val color4f = SkColor4f(
                Float.fromBits(0x3f6eeef0),
                Float.fromBits(0x3f6eeef0),
                Float.fromBits(0x3f6eeef0),
                Float.fromBits(0x3f800000),
            )
            val mode = SkBlendMode.kSrcOver // index 3
            c.save()
            c.concat(ctm)
            c.experimental_DrawEdgeAAQuad(rect, clip, aaFlags, color4f.toSkColor(), mode)
            c.restore()
        }

        // ── Second quad (also single-edge AA, drawn 300 px to the left). ─
        c.save()
        c.translate(-300f, 0f)
        run {
            val ctm = SkMatrix.MakeAll(
                Float.fromBits(0x3f54dd8a),
                Float.fromBits(0xbf9096a4.toInt()),
                Float.fromBits(0x447eae34),
                Float.fromBits(0x3f3f6905),
                Float.fromBits(0xbe5208ba.toInt()),
                Float.fromBits(0x4418118b),
                Float.fromBits(0x3aa134a1),
                Float.fromBits(0xb93ef249.toInt()),
                Float.fromBits(0x3f580bd4),
            )
            val rect = SkRect.MakeLTRB(
                Float.fromBits(0x00000000),
                Float.fromBits(0x00000000),
                Float.fromBits(0x40a00000),
                Float.fromBits(0x43560000),
            )
            val clip = arrayOf(
                SkPoint(Float.fromBits(0x40a0000e), Float.fromBits(0x40c86b5a)),
                SkPoint(Float.fromBits(0x40a0001e), Float.fromBits(0x4314dd5f)),
                SkPoint(Float.fromBits(0x407f76eb), Float.fromBits(0x431580c2)),
                SkPoint(Float.fromBits(0x404092e7), Float.fromBits(0x43140445)),
            )
            val aaFlags = 0x00000002 // kTop_QuadAAFlag
            val color4f = SkColor4f(
                Float.fromBits(0x3f6eeef0),
                Float.fromBits(0x3f6eeef0),
                Float.fromBits(0x3f6eeef0),
                Float.fromBits(0x3f800000),
            )
            val mode = SkBlendMode.kSrcOver
            c.save()
            c.concat(ctm)
            c.experimental_DrawEdgeAAQuad(rect, clip, aaFlags, color4f.toSkColor(), mode)
            c.restore()
        }
        c.restore()

        // ── Third quad (right-edge AA, white). ───────────────────────
        run {
            val ctm = SkMatrix.MakeAll(
                Float.fromBits(0x3f54b255),
                Float.fromBits(0x3eb5a94d),
                Float.fromBits(0x443d7419),
                Float.fromBits(0x3f885d66),
                Float.fromBits(0x3f5a6b9c),
                Float.fromBits(0x443c7334),
                Float.fromBits(0x3aa95ea5),
                Float.fromBits(0xb8a1391e.toInt()),
                Float.fromBits(0x3f84dde5),
            )
            val rect = SkRect.MakeLTRB(
                Float.fromBits(0x00000000),
                Float.fromBits(0x00000000),
                Float.fromBits(0x40a00000),
                Float.fromBits(0x43100000),
            )
            val clip = arrayOf(
                SkPoint(Float.fromBits(0x405a654c), Float.fromBits(0x42e8c790)),
                SkPoint(Float.fromBits(0x3728c61b), Float.fromBits(0x42e7df31)),
                SkPoint(Float.fromBits(0xb678ecc5.toInt()), Float.fromBits(0x412db4e0)),
                SkPoint(Float.fromBits(0x4024b2ad), Float.fromBits(0x413ab3ed)),
            )
            val aaFlags = 0x00000004 // kRight_QuadAAFlag
            val color4f = SkColor4f(
                Float.fromBits(0x3f800000),
                Float.fromBits(0x3f800000),
                Float.fromBits(0x3f800000),
                Float.fromBits(0x3f800000),
            )
            val mode = SkBlendMode.kSrcOver
            c.save()
            c.concat(ctm)
            c.experimental_DrawEdgeAAQuad(rect, clip, aaFlags, color4f.toSkColor(), mode)
            c.restore()
        }
    }
}

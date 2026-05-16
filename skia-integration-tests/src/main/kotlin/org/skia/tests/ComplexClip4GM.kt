package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/complexclip4.cpp::ComplexClip4GM` (970 × 780).
 *
 * Exercises the Android-Framework `androidFramework_setDeviceClipRestriction`
 * +`SkCanvasPriv::ResetClip` combo : a device clip restriction is applied
 * for a "green fill" pass, then [ResetClip] discards the device clip and
 * the canvas is re-clipped via [clipRect] / [clipPath] / [clipRRect] for
 * the yellow target rect. The `aa` / `bw` variants drive `doAntiAlias`
 * on every clip *and* the yellow paint.
 *
 * `:kanvas-skia` has no `androidFramework_setDeviceClipRestriction` /
 * `ResetClip` plumbing, so the GM is approximated by wrapping the green
 * fill in `save()` → `clipRect(restrictRect)` → `drawColor(green)` →
 * `restore()`, which leaves the parent clip identical to a hypothetical
 * "post-ResetClip" state. The yellow target rect is then drawn through
 * the appropriate replacement clip in the outer scope.
 *
 * The last sub-test uses an outer rotation + translation followed by an
 * "emulated device restriction" + `drawColor(yellow)`. Upstream uses
 * device-space restriction so it ignores the rotation ; our approximation
 * applies the rectangle in local coordinates, so this fourth cell will
 * differ visibly from upstream. That divergence is documented and called
 * out in the similarity floor.
 */
public open class ComplexClip4GM(
    private val doAAClip: Boolean = false,
) : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDE, 0xDF, 0xDE))
    }

    override fun getName(): String = "complexclip4_${if (doAAClip) "aa" else "bw"}"
    override fun getISize(): SkISize = SkISize.Make(970, 780)

    private fun greenIn(c: SkCanvas, restrict: SkIRect) {
        c.save()
        c.clipRect(
            SkRect.MakeLTRB(
                restrict.left.toFloat(), restrict.top.toFloat(),
                restrict.right.toFloat(), restrict.bottom.toFloat(),
            ),
            doAAClip,
        )
        c.drawColor(SK_ColorGREEN)
        c.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val yellow = SkPaint().apply {
            isAntiAlias = doAAClip
            color = SK_ColorYELLOW
        }

        c.save()

        // 1) yellow rect through a rect-replace clip
        c.save()
        greenIn(c, SkIRect.MakeLTRB(100, 100, 300, 300))
        c.clipRect(SkRect.MakeLTRB(100f, 200f, 400f, 500f), doAAClip)
        c.drawRect(SkRect.MakeLTRB(100f, 200f, 400f, 500f), yellow)
        c.restore()

        // 2) yellow rect through a diamond path-replace clip
        c.save()
        greenIn(c, SkIRect.MakeLTRB(500, 100, 800, 300))
        val pathClip = SkPath.Polygon(
            arrayOf(
                650f to 200f,
                900f to 300f,
                650f to 400f,
                650f to 300f,
            ),
            isClosed = true,
        )
        c.clipPath(pathClip, doAAClip)
        c.drawRect(SkRect.MakeLTRB(500f, 200f, 900f, 500f), yellow)
        c.restore()

        // 3) yellow rect through a round-rect-replace clip (full oval)
        c.save()
        greenIn(c, SkIRect.MakeLTRB(500, 500, 800, 700))
        val rrect = SkRRect.MakeOval(SkRect.MakeLTRB(500f, 600f, 900f, 750f))
        c.clipRRect(rrect, doAAClip)
        c.drawRect(SkRect.MakeLTRB(500f, 600f, 900f, 750f), yellow)
        c.restore()

        // 4) fill clip with yellow ; upstream uses a device-space replace,
        //    here we just rely on the parent clipRect + a translated/rotated
        //    drawColor sequence (best-effort — pixel-accurate match needs
        //    device-space clip restriction).
        c.save()
        c.clipRect(SkRect.MakeLTRB(100f, 400f, 300f, 750f), doAAClip)
        c.drawColor(SK_ColorGREEN)
        c.rotate(20f)
        c.translate(50f, 50f)
        // Local-space approximation of the upstream device-space rect.
        c.save()
        c.clipRect(SkRect.MakeLTRB(150f, 450f, 250f, 700f), doAAClip)
        c.drawColor(SK_ColorYELLOW)
        c.restore()
        c.restore()

        c.restore()
    }
}

/** AA variant — same drawing, AA toggled on every clip / paint. */
public class ComplexClip4AaGM : ComplexClip4GM(doAAClip = true)

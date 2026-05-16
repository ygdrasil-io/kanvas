package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/arcto.cpp` (`DEF_SIMPLE_GM(arcto, …, 500, 600)`).
 *
 * Exercises the SVG-style `SkPathBuilder.arcTo(rx, ry, xAxisRotate,
 * ArcSize, sweep, x, y)` in three sections, each rendered with a
 * 2-pixel stroke (the round-cap section uses a 5-pixel stroke):
 *
 *  1. **Loop section** (8 dark-red arcs, top half): two outer rotations
 *     (`angle = 0°, 45°`) × two ellipse heights (`oHeight = 2, 1`) ×
 *     two arc shapes (small CW from oval TL to oval BR, then large CCW
 *     from oval TL+(100,100) to oval BR+(0,100)). The oval origin
 *     marches by `(50, 0)` per inner iteration.
 *
 *  2. **Four-coloured chord section** (4 stroke-width-5 arcs): the same
 *     `(250,400) → (250,500)` chord on a `rx=120, ry=80` ellipse,
 *     rendered four times with each `(largeArc, sweep)` permutation
 *     (red / dark-green / purple / blue). Skia's API takes
 *     `SkPathDirection`, where SVG `sweep=1` maps to `kCCW` and
 *     `sweep=0` to `kCW` (per the comment in
 *     `src/utils/SkParsePath.cpp`).
 *
 *  3. **Round-cap zero-length section** (2 round-cap stroked arcs):
 *     verifies that arcs with `rx=0, ry=0` and arcs whose start equals
 *     end still produce a visible round-cap dot — both fall through to
 *     the `lineTo(endPt)` degenerate branch.
 *
 * **Reference image**: `arcto.png`, 500 × 600, white background.
 */
public class ArcToGM : GM() {

    override fun getName(): String = "arcto"
    override fun getISize(): SkISize = SkISize.Make(500, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
            color = 0xFF660000.toInt()
        }
        var oval = SkRect.MakeXYWH(100f, 100f, 100f, 100f)

        // Section 1: loop — angle ∈ {0, 45}, oHeight ∈ {2, 1}, two
        // arcs per inner step (small CW + large CCW), oval marches +50px.
        var angle = 0f
        while (angle <= 45f) {
            for (oHeight in intArrayOf(2, 1)) {
                val ovalHeight = oval.height() / oHeight
                val ovalWidthHalf = oval.width() * 0.5f

                val small = SkPathBuilder()
                    .moveTo(oval.left, oval.top)
                    .arcTo(
                        ovalWidthHalf, ovalHeight, angle,
                        SkPathBuilder.ArcSize.kSmall_ArcSize,
                        SkPathDirection.kCW,
                        oval.right, oval.bottom,
                    )
                    .detach()
                c.drawPath(small, paint)

                val large = SkPathBuilder()
                    .moveTo(oval.left + 100f, oval.top + 100f)
                    .arcTo(
                        ovalWidthHalf, ovalHeight, angle,
                        SkPathBuilder.ArcSize.kLarge_ArcSize,
                        SkPathDirection.kCCW,
                        oval.right, oval.bottom + 100f,
                    )
                    .detach()
                c.drawPath(large, paint)

                oval = SkRect.MakeXYWH(oval.left + 50f, oval.top, oval.width(), oval.height())
            }
            angle += 45f
        }

        // Section 2: four-coloured chord. The chord is fixed at
        // (250, 400) → (250, 500); each variant picks a different
        // (largeArc, sweep) → arc combination.
        paint.strokeWidth = 5f
        val purple = 0xFF800080.toInt()
        val darkgreen = 0xFF008000.toInt()
        // SVG: "M250,400 A120,80 0 L,S 250,500", L/S ∈ {0,1}.
        // Mapping: largeArc 1 → kLarge_ArcSize, 0 → kSmall_ArcSize.
        //          sweep 1 → kCCW (per Skia convention), 0 → kCW.
        data class Chord(
            val color: Int,
            val arc: SkPathBuilder.ArcSize,
            val sweep: SkPathDirection,
        )
        val chords = listOf(
            Chord(SK_ColorRED,  SkPathBuilder.ArcSize.kSmall_ArcSize, SkPathDirection.kCW),  // "0,0"
            Chord(darkgreen,    SkPathBuilder.ArcSize.kLarge_ArcSize, SkPathDirection.kCCW), // "1,1"
            Chord(purple,       SkPathBuilder.ArcSize.kLarge_ArcSize, SkPathDirection.kCW),  // "1,0"
            Chord(SK_ColorBLUE, SkPathBuilder.ArcSize.kSmall_ArcSize, SkPathDirection.kCCW), // "0,1"
        )
        for (chord in chords) {
            paint.color = chord.color
            val p = SkPathBuilder()
                .moveTo(250f, 400f)
                .arcTo(120f, 80f, 0f, chord.arc, chord.sweep, 250f, 500f)
                .detach()
            c.drawPath(p, paint)
        }

        // Section 3: zero-length round-cap arcs.
        paint.strokeCap = SkPaint.Cap.kRound_Cap
        // rx=ry=0 → degenerate to lineTo(endPt). With round caps + the
        // matching start/end, Skia emits a round dot at each end of the
        // (200, 200)-to-(200, 200) "line".
        val zeroRadii = SkPathBuilder()
            .moveTo(100f, 100f)
            .arcTo(0f, 0f, 0f, SkPathBuilder.ArcSize.kLarge_ArcSize, SkPathDirection.kCW, 200f, 200f)
            .detach()
        c.drawPath(zeroRadii, paint)

        // Start equals end → degenerate to lineTo (no arc). Skia emits a
        // round-cap dot at (200, 100).
        val zeroChord = SkPathBuilder()
            .moveTo(200f, 100f)
            .arcTo(80f, 80f, 0f, SkPathBuilder.ArcSize.kLarge_ArcSize, SkPathDirection.kCW, 200f, 100f)
            .detach()
        c.drawPath(zeroChord, paint)
    }
}

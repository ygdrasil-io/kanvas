package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/arcto.cpp` (`DEF_SIMPLE_GM(arcto, …, 500, 600)`).
 * Exercises the SVG-style Path.arcTo(rx, ry, xAxisRotate,
 * ArcSize, sweep, x, y) in three sections, each rendered with a
 * 2-pixel stroke (the round-cap section uses a 5-pixel stroke):
 *
 * 1. Loop section (8 dark-red arcs, top half): two outer rotations
 *    (angle = 0°, 45°) × two ellipse heights (oHeight = 2, 1) ×
 *    two arc shapes (small CW from oval TL to oval BR, then large CCW
 *    from oval TL+(100,100) to oval BR+(0,100)). The oval origin
 *    marches by (50, 0) per inner iteration.
 *
 * 2. Four-coloured chord section (4 stroke-width-5 arcs): the same
 *    (250,400) → (250,500) chord on a rx=120, ry=80 ellipse,
 *    rendered four times with each (largeArc, sweep) permutation
 *    (red / dark-green / purple / blue).
 *
 * 3. Round-cap zero-length section (2 round-cap stroked arcs):
 *    verifies that arcs with rx=0, ry=0 and arcs whose start equals
 *    end still produce a visible round-cap dot — both fall through to
 *    the lineTo(endPt) degenerate branch.
 *
 * Reference image: arcto.png, 500 × 600, white background.
 * @see https://github.com/google/skia/blob/main/gm/arcto.cpp
 */
class ArcToGm : SkiaGm {
    override val name = "arcto"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
            color = Color.fromRGBA(0x66 / 255f, 0x00 / 255f, 0x00 / 255f, 1f)
        )
        var oval = Rect.fromXYWH(100f, 100f, 100f, 100f)

        // Section 1: loop — angle ∈ {0, 45}, oHeight ∈ {2, 1}, two
        // arcs per inner step (small CW + large CCW), oval marches +50px.
        var angle = 0f
        while (angle <= 45f) {
            for (oHeight in intArrayOf(2, 1)) {
                val ovalHeight = oval.height / oHeight
                val ovalWidthHalf = oval.width * 0.5f

                val small = Path {
                    moveTo(oval.left, oval.top)
                    arcTo(
                        ovalWidthHalf, ovalHeight, angle,
                        largeArc = false,
                        sweep = false,
                        oval.right, oval.bottom,
                    )
                }
                canvas.drawPath(small, paint)

                val large = Path {
                    moveTo(oval.left + 100f, oval.top + 100f)
                    arcTo(
                        ovalWidthHalf, ovalHeight, angle,
                        largeArc = true,
                        sweep = true,
                        oval.right, oval.bottom + 100f,
                    )
                }
                canvas.drawPath(large, paint)

                oval = Rect.fromXYWH(oval.left + 50f, oval.top, oval.width, oval.height)
            }
            angle += 45f
        }

        // Section 2: four-coloured chord. The chord is fixed at
        // (250, 400) → (250, 500); each variant picks a different
        // (largeArc, sweep) → arc combination.
        paint = paint.copy(strokeWidth = 5f)
        val purple = Color.fromRGBA(0x80 / 255f, 0x00 / 255f, 0x80 / 255f, 1f)
        val darkgreen = Color.fromRGBA(0x00 / 255f, 0x80 / 255f, 0x00 / 255f, 1f)
        val blue = Color.BLUE
        val red = Color.RED
        // SVG: "M250,400 A120,80 0 L,S 250,500", L/S ∈ {0,1}.
        // Mapping: largeArc 1 → true, 0 → false.
        //          sweep 1 → true (CCW), 0 → false (CW).
        data class Chord(
            val color: Color,
            val largeArc: Boolean,
            val sweep: Boolean,
        )
        val chords = listOf(
            Chord(red, false, false),      // "0,0"
            Chord(darkgreen, true, true),   // "1,1"
            Chord(purple, true, false),     // "1,0"
            Chord(blue, false, true),       // "0,1"
        )
        for (chord in chords) {
            canvas.drawPath(
                Path {
                    moveTo(250f, 400f)
                    arcTo(120f, 80f, 0f, chord.largeArc, chord.sweep, 250f, 500f)
                },
                paint.copy(color = chord.color)
            )
        }

        // Section 3: zero-length round-cap arcs.
        paint = paint.copy(strokeCap = StrokeCap.ROUND)
        // rx=ry=0 → degenerate to lineTo(endPt). With round caps + the
        // matching start/end, emits a round dot at each end of the
        // (200, 200)-to-(200, 200) "line".
        canvas.drawPath(
            Path {
                moveTo(100f, 100f)
                arcTo(0f, 0f, 0f, true, false, 200f, 200f)
            },
            paint
        )

        // Start equals end → degenerate to lineTo (no arc). Emits a
        // round-cap dot at (200, 100).
        canvas.drawPath(
            Path {
                moveTo(200f, 100f)
                arcTo(80f, 80f, 0f, true, false, 200f, 100f)
            },
            paint
        )
    }
}

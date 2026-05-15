package org.skia.pathops

import org.junit.jupiter.api.Test
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType

/**
 * Debug harness for `cubicOp25i` — PathOps pixel-divergent regression
 * (D1.4 follow-up). **Handoff** : root cause identified, fix deferred.
 *
 * Upstream (`tests/PathOpsOpTest.cpp`) :
 * ```
 * static void cubicOp25i(skiatest::Reporter*, const char*) {
 *     path .setFillType(kWinding);
 *     path .moveTo(0,1);  path .cubicTo(2,4, 5,0, 3,2);  path .close();
 *     pathB.setFillType(kWinding);
 *     pathB.moveTo(0,5);  pathB.cubicTo(2,3, 1,0, 4,2);  pathB.close();
 *     testPathOp(reporter, ..., kIntersect_SkPathOp, ...);
 * }
 * ```
 *
 * ## Geometry — why this fixture is hard
 *
 * Cubic A `(0,1)–(2,4)–(5,0)–(3,2)` is an S-shape that **self-loops**
 * (its own y-extent dips below 1.42 and back up to 2). After
 * `close()`, the implicit line back to `(0,1)` *intersects cubic A
 * itself* near `(2.844, 1.948)` (at `cubicA.t ≈ 0.467`, `lineA.t ≈
 * 0.052`). Cubic A thus has a **self-intersection between its curve
 * portion and its close-line**, producing a small "tail" loop near
 * the cubic's t=1 endpoint.
 *
 * Path B `(0,5)–(2,3)–(1,0)–(4,2)` has a similar S-shape but doesn't
 * self-intersect.
 *
 * Cubic A × Cubic B has **three intersection points** :
 *  - `(1.555, 2.135)` at `tA≈0.244, tB≈0.470`
 *  - `(3.393, 1.642)` at `tA≈0.603, tB≈0.926`
 *  - `(3.391, 1.625)` at `tA≈0.919, tB≈0.924`
 *
 * Two of those (the second / third) sit on top of each other near
 * `(3.39, 1.63)` — that's where cubic A loops through, crossing B's
 * curve twice in a short t-interval.
 *
 * ## Divergence point — what the engine does wrong
 *
 * Logging `bridgeOp` with `-Dkanvas.pathops.debug.bridgeOp=1` (a flag
 * that was wired during debug then removed — re-add to the top of
 * `bridgeOp` if iterating) shows the walker emits :
 *
 * 1. Cubic B from `t=0.598 → 0.471` (down to A×B int #1).
 * 2. Cubic A from `t=0.245 → 0.467` (across to A's self-intersection).
 * 3. **Line A from `t=0.052 → 0.000`** ← wrong turn at the A-self-int.
 * 4. Cubic A from `t=1.000 → 0.920` (reverse, away from (3,2)).
 * 5. Cubic B from `t=0.924 → 0.598` (reverse, back to start).
 *
 * Step 3 is the bug : at the `cubicA × lineA` self-intersection point
 * `(2.844, 1.948)` the angle-sort picks **line A backward** as the
 * next active edge, when the geometrically-correct continuation along
 * the boundary of `A ∩ B` is **cubic A onward** (toward A×B int #2
 * at `(3.39, 1.64)`). The wrong turn pulls the contour out toward
 * `(3, 2)` (A's cubic endpoint), then doubles back via the upper
 * cubic-A loop — producing an extra "spike" region that doesn't
 * belong to the intersection.
 *
 * The walker then re-enters with a second contour
 * (`Move(3.40,1.64) → Cubic B (0.605→0.467) → Line back`) that
 * duplicates a slice of cubic B and a slice of line A, glued with
 * stitching chords from `SkPathWriter.assemble` — visible in the
 * output as the second `Move`-rooted contour.
 *
 * **Net pixel diff** : 20 2×2-block errors above the lens, in rows
 * y∈[18, 28] of the 64×64 oracle bitmap (the AAA region in the diff
 * dump). The engine's result rasterises ~12% wider than the oracle's
 * expected lens.
 *
 * ## Sibling fixtures with the same fingerprint
 *
 * The 13 PIXEL_DIVERGE fixtures are listed in
 * `PathOpsRegressionRunner` kdoc. The six `loops23i / loops26i /
 * loops33i / loops47i / loops63i` and `loop3` fixtures are all
 * "cubic-A vs cubic-B" pairs where pathB's cubic is a **circular
 * shift** of pathA's cubic — same self-loop geometry, same A×B
 * triple-intersection, same A-self-intersection with the close-line.
 * The four `cubicOp25i / 32d / 33i / 48d / 61d / 63d / 95u` fixtures
 * are smaller-integer-coord versions of the same loop-rich topology.
 * They very likely share the same root cause and should clear in a
 * single fix.
 *
 * ## Where to look next
 *
 * Three candidate sites, in decreasing confidence :
 *
 *  1. **`SkOpAngle.orderable` / sector logic** for a 4-way junction
 *     where one pair of edges is collinear (cubic-A tangent at the
 *     self-int is `≈(-23.4°, 156.6°)`, the close-line A is at
 *     `≈(-161.6°, 18.4°)` — not collinear, but the sort is sensitive
 *     to how curve-vs-line angles get classified into the same sector).
 *     `SkOpAngle.kt` ~1000 lines ; start with the sector-assignment
 *     code path (`setSector` / `alignmentSameSide`).
 *  2. **`findNextOp` activeCount tie-breaker** at line 1287-1291 of
 *     `SkOpSegment.kt` :
 *       ```kotlin
 *       if (foundAngle == null || (foundDone && (activeCount and 1) == 1)) {
 *       ```
 *     This handles the "previous winner was done and the activeCount
 *     parity flipped" tie-break. If the angle-sort is right but the
 *     parity flip happens to pick line A here, this is where to patch.
 *  3. **`HandleCoincidence` for near-coincident intersections** at
 *     `(3.39, 1.63)` (two A×B intersections within 0.017 of each
 *     other). If the coincidence resolver collapses these into one
 *     and breaks the topology, the walker downstream has nothing
 *     legal to choose. `SkOpCoincidence.kt` ~2000 lines ;
 *     `addIfMissing` and `mark` are the likely points.
 *
 * The `[PixelOracleDebugDump]` helper (companion file) ASCII-dumps
 * the expected vs actual bitmap so a fix's effect is visible
 * immediately ; pixel-parity `Op(A, B, kIntersect)` should match
 * the oracle within `MAX_2X2_ERRORS = 8`.
 *
 * ## Bail-out
 *
 * This test stays as a starting point for the next debug pass. The
 * production `bridgeOp` / `findNextOp` / `SkOpAngle` are unchanged
 * from `master` — no half-fix landed.
 */
class CubicOp25iDebugTest {

    private fun mkA(): SkPath = SkPathBuilder().apply {
        setFillType(SkPathFillType.kWinding)
        moveTo(0f, 1f)
        cubicTo(2f, 4f, 5f, 0f, 3f, 2f)
        close()
    }.detach()

    private fun mkB(): SkPath = SkPathBuilder().apply {
        setFillType(SkPathFillType.kWinding)
        moveTo(0f, 5f)
        cubicTo(2f, 3f, 1f, 0f, 4f, 2f)
        close()
    }.detach()

    private fun verbsToString(p: SkPath): String {
        val sb = StringBuilder()
        sb.append("fill=").append(p.fillType).append(" ")
        var ci = 0
        for (v in p.verbs) {
            sb.append(v.name.removePrefix("k"))
            sb.append('(')
            repeat(v.pointCount) { k ->
                if (k > 0) sb.append(", ")
                sb.append("%.4f".format(p.coords[ci++]))
                sb.append(',')
                sb.append("%.4f".format(p.coords[ci++]))
            }
            sb.append(") ")
        }
        return sb.toString()
    }

    @Test
    fun `dump engine vs oracle for cubicOp25i`() {
        val a = mkA()
        val b = mkB()
        val op = SkPathOp.kIntersect
        val engine = SkPathOps.Op(a, b, op)
        println("== cubicOp25i ==")
        println("pathA: ${verbsToString(a)}")
        println("pathB: ${verbsToString(b)}")
        println("engine result: ${engine?.let(::verbsToString) ?: "NULL"}")
        println("engine isFinite=${engine?.isFinite()} verbCount=${engine?.verbs?.size}")
        val outcome = engine?.let { PathOpsPixelOracle.compare(a, b, op, it) }
        println("oracle outcome: $outcome")
        if (engine != null) dumpMaps(a, b, op, engine)
    }

    private fun dumpMaps(a: SkPath, b: SkPath, op: SkPathOp, result: SkPath) {
        val dump = PathOpsPixelOracleDebugDump.dump(a, b, op, result)
        println("== Expected (pixel-oracle) ==")
        println(dump.expectedAscii)
        println("== Actual (engine result) ==")
        println(dump.actualAscii)
        println("== Diff (E=expected only, A=actual only, .=match, #=both) ==")
        println(dump.diffAscii)
        println("Block-diff errors: ${dump.errors}")
    }
}

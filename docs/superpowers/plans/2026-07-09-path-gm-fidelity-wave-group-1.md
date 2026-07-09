# Path GM Fidelity Wave Group 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve high-impact `PATH` GM scores by fixing GM port parity and deterministic Skia-style random streams, without modifying reference images.

**Architecture:** Start with the proven `convexpaths` background mismatch, then add a test-local Skia-compatible random helper used only by integration-test GMs. Keep renderer behavior unchanged in this group unless evidence proves a renderer root cause.

**Tech Stack:** Kotlin/JUnit 5, Kanvas integration-test GM sources, Gradle Skia render tasks, `jq`, generated PNG/dashboard artifacts.

## Global Constraints

- Branch is `codex/path-gm-fidelity-wave`, created from `origin/master`.
- Do not modify upstream/reference PNGs to make scores pass.
- Do not lower thresholds to claim progress.
- Do not hide `noReference`, `renderFailed`, or unsupported rows.
- Do not claim broad Skia path parity from selected GM improvements.
- Do not port Ganesh, Graphite, or SkSL compiler behavior.
- Commit GM source fixes separately from generated render/score artifact updates when practical.

---

## File Structure

- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGm.kt`: add the missing black background and later switch color random generation to the Skia-compatible helper if evidence supports it.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGmTest.kt`: verify the GM records the black background before path draws.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandom.kt`: test-local deterministic random helper matching Skia `SkRandom`.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandomTest.kt`: golden tests for helper values.
- Modify selected randomized GM files only after `SkiaRandomTest` passes and targeted score evidence justifies migration:
  - `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGm.kt`
  - `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/PolygonsGm.kt`
  - `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/StrokesGm.kt`
  - `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/WideButtCapsGm.kt`
- Generated artifacts are limited to targeted `integration-tests/skia/src/test/resources/generated-renders/path/*.png` and `integration-tests/skia/test-similarity-scores.properties` when score persistence is required.

### Task 1: Fix ConvexPaths Background Parity

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGm.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGmTest.kt`

**Interfaces:**
- Consumes: `ConvexPathsGm.draw(canvas, width, height)` and `GmCanvas.drawColor(r, g, b, a)`.
- Produces: `ConvexPathsGm` records a full-canvas black `DisplayOp.DrawRect` before translating/scaling path content.

- [ ] **Step 1: Write the failing test**

Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGmTest.kt`:

```kotlin
package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConvexPathsGmTest {
    @Test
    fun `draws black background before convex paths`() {
        val gm = ConvexPathsGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        val ops = surface.snapshotOps()
        val firstRect = ops.firstOrNull() as? DisplayOp.DrawRect
        requireNotNull(firstRect) { "expected first op to be a DrawRect background" }
        assertEquals(Color.BLACK, firstRect.paint.color)
        assertEquals(0f, firstRect.rect.left)
        assertEquals(0f, firstRect.rect.top)
        assertEquals(gm.width.toFloat(), firstRect.rect.right)
        assertEquals(gm.height.toFloat(), firstRect.rect.bottom)
        assertTrue(ops.drop(1).filterIsInstance<DisplayOp.DrawPath>().isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.path.ConvexPathsGmTest
```

Expected: FAIL because the first recorded op is currently a path draw, not a black background rect.

- [ ] **Step 3: Implement the minimal GM fix**

In `ConvexPathsGm.draw`, add `drawColor` before the existing translation:

```kotlin
override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    val rand = Random(0)
    canvas.drawColor(0f, 0f, 0f, 1f)
    canvas.translate(20f, 20f)
    canvas.scale(2f / 3, 2f / 3)
    for (i in paths.indices) {
        canvas.save()
        canvas.translate(200f * (i % 5) + 1f / 10, 200f * (i / 5) + 9f / 10)
        val raw = rand.nextInt()
        val ci = raw or (0xFF000000.toInt())
        val a = ((ci ushr 24) and 0xFF) / 255f
        val r = ((ci ushr 16) and 0xFF) / 255f
        val g = ((ci ushr 8) and 0xFF) / 255f
        val b = (ci and 0xFF) / 255f
        canvas.drawPath(paths[i], Paint(color = Color.fromRGBA(r, g, b, a), antiAlias = true))
        canvas.restore()
    }
}
```

- [ ] **Step 4: Run the test and targeted render**

Run:

```bash
./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.path.ConvexPathsGmTest
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=convexpaths -Pgm.includeBlocking=true
```

Expected: the unit test passes. The render command produces `integration-tests/skia/src/test/resources/generated-renders/path/convexpaths.png`.

- [ ] **Step 5: Check the score from dashboard data or generate dashboard if needed**

If `data/gms.json` is stale, run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
jq -r '.gms[] | select(.name=="convexpaths") | [.name,.similarity,.matchingPixels,.totalPixels] | @tsv' integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json
```

Expected: `convexpaths` improves materially from `0.0%`; if it does not, stop and inspect generated/reference/diff images before changing anything else.

- [ ] **Step 6: Commit source and generated convexpaths render**

Run:

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGm.kt \
        integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGmTest.kt \
        integration-tests/skia/src/test/resources/generated-renders/path/convexpaths.png
git commit -m "Fix convexpaths GM background parity"
```

### Task 2: Add Test-Local SkiaRandom Helper

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandom.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandomTest.kt`

**Interfaces:**
- Produces: `class SkiaRandom(seed: UInt = 0u)` with methods:
  - `fun nextU(): UInt`
  - `fun nextS(): Int`
  - `fun nextF(): Float`

- [ ] **Step 1: Write golden tests**

Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandomTest.kt`:

```kotlin
package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkiaRandomTest {
    @Test
    fun `matches SkRandom nextU golden stream for seed zero`() {
        val random = SkiaRandom(0u)

        assertArrayEquals(
            arrayOf(0x4f9643a0u, 0x018cb5ecu, 0x79ea6f5cu, 0xbdc9934eu, 0x2fcfce7bu),
            Array(5) { random.nextU() },
        )
    }

    @Test
    fun `matches SkRandom signed and float golden values`() {
        val signed = SkiaRandom(42u)
        assertEquals(-836240769, signed.nextS())
        assertEquals(-685989928, signed.nextS())
        assertEquals(-175912196, signed.nextS())

        val floats = SkiaRandom(1u)
        assertEquals(0.015994906f, floats.nextF(), 0.0000005f)
        assertEquals(0.41842508f, floats.nextF(), 0.0000005f)
        assertEquals(0.7300186f, floats.nextF(), 0.0000005f)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaRandomTest
```

Expected: FAIL because `SkiaRandom` does not exist.

- [ ] **Step 3: Implement SkiaRandom**

Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandom.kt`:

```kotlin
package org.graphiks.kanvas.skia

/**
 * Test-local port of Skia's SkRandom stream.
 *
 * Source model: Skia `include/utils/SkRandom.h`, Marsaglia multiply-with-carry
 * generator after a one-step LCG seed expansion.
 */
class SkiaRandom(seed: UInt = 0u) {
    private var k: UInt = nextLCG(seed).let { if (it == 0u) nextLCG(it) else it }
    private var j: UInt = nextLCG(k).let { if (it == 0u) nextLCG(it) else it }

    fun nextU(): UInt {
        k = K_MUL * (k and 0xffffu) + (k shr 16)
        j = J_MUL * (j and 0xffffu) + (j shr 16)
        return ((k shl 16) or (k shr 16)) + j
    }

    fun nextS(): Int = nextU().toInt()

    fun nextF(): Float {
        val bits = 0x3f800000u or (nextU() shr 9)
        return Float.fromBits(bits.toInt()) - 1f
    }

    fun nextRangeU(minInclusive: UInt, maxExclusive: UInt): UInt {
        require(maxExclusive > minInclusive) { "maxExclusive must be greater than minInclusive" }
        return minInclusive + nextU() % (maxExclusive - minInclusive)
    }

    fun nextRangeF(minInclusive: Float, maxExclusive: Float): Float {
        require(maxExclusive >= minInclusive) { "maxExclusive must be greater than or equal to minInclusive" }
        return minInclusive + nextF() * (maxExclusive - minInclusive)
    }

    private fun nextLCG(seed: UInt): UInt = LCG_MUL * seed + LCG_ADD

    private companion object {
        private const val LCG_MUL: UInt = 1664525u
        private const val LCG_ADD: UInt = 1013904223u
        private const val K_MUL: UInt = 30345u
        private const val J_MUL: UInt = 18000u
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run:

```bash
./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaRandomTest
```

Expected: PASS.

- [ ] **Step 5: Commit helper and tests**

Run:

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandom.kt \
        integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandomTest.kt
git commit -m "Add Skia-compatible random helper for GM tests"
```

### Task 3: Migrate One Randomized GM And Measure

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGm.kt`
- Modify after render: `integration-tests/skia/src/test/resources/generated-renders/path/manycircles.png`

**Interfaces:**
- Consumes: `SkiaRandom.nextF()` from Task 2.
- Produces: `ManyCirclesGm` uses the Skia-compatible stream for position, size, hue, saturation, and value generation.

- [ ] **Step 1: Write an operation-level test for deterministic draw count**

Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGmTest.kt`:

```kotlin
package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManyCirclesGmTest {
    @Test
    fun `draws ten thousand oval path operations`() {
        val gm = ManyCirclesGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        assertEquals(10_000, surface.snapshotOps().filterIsInstance<DisplayOp.DrawPath>().size)
    }
}
```

- [ ] **Step 2: Run the test before changing random**

Run:

```bash
./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.path.ManyCirclesGmTest
```

Expected: PASS. This is a non-regression guard for GM structure, not the score fix.

- [ ] **Step 3: Switch ManyCirclesGm to SkiaRandom**

Edit `ManyCirclesGm.kt`:

```kotlin
import org.graphiks.kanvas.skia.SkiaRandom
```

Replace the random setup and calls:

```kotlin
override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    val rand = SkiaRandom(1u)
    val paint = Paint(antiAlias = true)
    var total = 10_000
    while (total-- > 0) {
        val x = rand.nextF() * kWidth - 100f
        val y = rand.nextF() * kHeight - 100f
        val w = rand.nextF() * 200f
        val circle = Rect.fromXYWH(x, y, w, w)
        canvas.drawOval(circle, paint.copy(color = genColor(rand)))
    }
}

private fun genColor(rand: SkiaRandom): Color {
    val hue = rand.nextF() * 360f
    val sat = 0.5f + rand.nextF() * 0.5f
    val value = 0.5f + rand.nextF() * 0.5f
    val c = value * sat
    val hp = hue / 60f
    val xVal = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, xVal, 0f)
        hp < 2f -> Triple(xVal, c, 0f)
        hp < 3f -> Triple(0f, c, xVal)
        hp < 4f -> Triple(0f, xVal, c)
        hp < 5f -> Triple(xVal, 0f, c)
        else -> Triple(c, 0f, xVal)
    }
    val m = value - c
    return Color.fromRGBA(r1 + m, g1 + m, b1 + m)
}
```

Remove `import kotlin.random.Random`.

- [ ] **Step 4: Run tests and targeted render**

Run:

```bash
./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaRandomTest --tests org.graphiks.kanvas.skia.gm.path.ManyCirclesGmTest
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=manycircles -Pgm.includeBlocking=true
```

Expected: tests pass and `generated-renders/path/manycircles.png` changes.

- [ ] **Step 5: Measure score movement before migrating other GMs**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
jq -r '.gms[] | select(.name=="manycircles") | [.name,.similarity,.matchingPixels,.totalPixels] | @tsv' integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json
```

Expected: if `manycircles` remains near `0.0%`, stop and inspect color-generation parity before migrating more files. If it improves materially, continue to Task 4.

- [ ] **Step 6: Commit ManyCircles migration if score improves**

Run:

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGm.kt \
        integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ManyCirclesGmTest.kt \
        integration-tests/skia/src/test/resources/generated-renders/path/manycircles.png
git commit -m "Use Skia random stream for manycircles GM"
```

### Task 4: Apply SkiaRandom To Remaining Randomized Group 1 GMs

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/PolygonsGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/StrokesGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/WideButtCapsGm.kt`
- Modify generated renders for the same GM names if score movement justifies it.

**Interfaces:**
- Consumes: `SkiaRandom.nextS()` and `SkiaRandom.nextF()` from Task 2.
- Produces: Randomized Group 1 GMs use Skia's deterministic stream where their upstream reference depends on `SkRandom`.

- [ ] **Step 1: Migrate ConvexPaths color stream**

In `ConvexPathsGm.kt`, replace `Random(0)` with `SkiaRandom(0u)` and `rand.nextInt()` with `rand.nextS()`.

Required import change:

```kotlin
import org.graphiks.kanvas.skia.SkiaRandom
```

Remove:

```kotlin
import kotlin.random.Random
```

The local line should become:

```kotlin
val rand = SkiaRandom(0u)
```

Color raw generation should become:

```kotlin
val raw = rand.nextS()
```

- [ ] **Step 2: Migrate Polygons color stream**

In `PolygonsGm.kt`, replace `Random(42)` with `SkiaRandom(42u)` and `rand.nextInt()` with `rand.nextS()`.

Required import:

```kotlin
import org.graphiks.kanvas.skia.SkiaRandom
```

The `makePaint` signature should become:

```kotlin
private fun makePaint(rand: SkiaRandom, join: StrokeJoin, width: Float, style: PaintStyle): Paint {
```

The raw value should become:

```kotlin
val raw = rand.nextS() or 0xFF000000.toInt()
```

- [ ] **Step 3: Migrate StrokesGm shape and color stream**

In `StrokesGm.kt`, replace `Random(0)` with `SkiaRandom(0u)`. Update `rndRect`:

```kotlin
private fun rndRect(rand: SkiaRandom): Pair<Rect, Color> {
    val x = rand.nextF() * W
    val y = rand.nextF() * H
    val w = rand.nextF() * (W shr 2)
    val h = rand.nextF() * (H shr 2)
    val hoffset = rand.nextF() * 2f - 1f
    val woffset = rand.nextF() * 2f - 1f

    val dx = -w / 2f + woffset
    val dy = -h / 2f + hoffset
    val r = Rect.fromLTRB(x + dx, y + dy, x + w + dx, y + h + dy)

    val c32 = rand.nextS()
    val color = Color.fromRGBA(
        ((c32 ushr 16) and 0xFF) / 255f,
        ((c32 ushr 8) and 0xFF) / 255f,
        (c32 and 0xFF) / 255f,
        1f,
    )
    return Pair(r, color)
}
```

- [ ] **Step 4: Migrate WideButtCaps color stream**

In `WideButtCapsGm.kt`, replace `Random(0)` with `SkiaRandom(0u)`. Update `nextColor`:

```kotlin
private fun nextColor(rand: SkiaRandom): Color {
    val raw = rand.nextS() or 0xFF808080.toInt()
    val r = ((raw ushr 16) and 0xFF) / 255f
    val g = ((raw ushr 8) and 0xFF) / 255f
    val b = (raw and 0xFF) / 255f
    return Color.fromRGBA(r, g, b, 1f)
}
```

- [ ] **Step 5: Run targeted tests**

Run:

```bash
./gradlew :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaRandomTest \
  --tests org.graphiks.kanvas.skia.gm.path.ConvexPathsGmTest \
  --tests org.graphiks.kanvas.skia.gm.path.ManyCirclesGmTest
```

Expected: PASS.

- [ ] **Step 6: Generate targeted renders**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=convexpaths -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=polygons -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=strokes_round -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=widebuttcaps -Pgm.includeBlocking=true
```

Expected: all four render commands finish without render failures.

- [ ] **Step 7: Measure targeted scores**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
jq -r '.gms[] | select(.name=="convexpaths" or .name=="polygons" or .name=="strokes_round" or .name=="widebuttcaps" or .name=="manycircles") | [.name,.similarity,.matchingPixels,.totalPixels] | @tsv' integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json
```

Expected: keep only migrations that improve or preserve their targeted GM score. If one GM regresses materially, revert only that GM migration and keep the helper plus improving migrations.

- [ ] **Step 8: Commit accepted migrations**

Run:

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ConvexPathsGm.kt \
        integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/PolygonsGm.kt \
        integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/StrokesGm.kt \
        integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/WideButtCapsGm.kt \
        integration-tests/skia/src/test/resources/generated-renders/path/convexpaths.png \
        integration-tests/skia/src/test/resources/generated-renders/path/polygons.png \
        integration-tests/skia/src/test/resources/generated-renders/path/strokes_round.png \
        integration-tests/skia/src/test/resources/generated-renders/path/widebuttcaps.png
git commit -m "Use Skia random stream for path GM parity"
```

### Task 5: Persist Scores And Produce Milestone Evidence

**Files:**
- Modify: `integration-tests/skia/test-similarity-scores.properties`
- Modify: accepted `integration-tests/skia/src/test/resources/generated-renders/path/*.png`

**Interfaces:**
- Consumes: generated renders and dashboard data from Tasks 1-4.
- Produces: persisted score file and final dashboard evidence for Group 1.

- [ ] **Step 1: Run full dashboard**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
```

Expected: `BUILD SUCCESSFUL`. Record the final summary line, including total/pass/fail/no-score/average similarity.

- [ ] **Step 2: Run score persistence**

Run:

```bash
./gradlew :integration-tests:skia:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`. Blocking GMs may remain skipped by the test task; do not claim blocking score persistence if the output says they were skipped. Use dashboard `gms.json` for blocking-row evidence when necessary.

- [ ] **Step 3: Extract Group 1 scores**

Run:

```bash
jq -r '.gms[] | select(.name=="convexpaths" or .name=="manycircles" or .name=="polygons" or .name=="strokes_round" or .name=="widebuttcaps") | [.name,.similarity,.minSimilarity,.matchingPixels,.totalPixels] | @tsv' integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json
```

Expected: output contains all five target rows. Paste the score table into the PR body or PR comment later.

- [ ] **Step 4: Check reference guardrail and whitespace**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
git diff --check
```

Expected: first command prints nothing. Second command prints nothing.

- [ ] **Step 5: Commit score/render artifacts**

Run:

```bash
git add integration-tests/skia/test-similarity-scores.properties \
        integration-tests/skia/src/test/resources/generated-renders/path
git commit -m "Regenerate path GM parity renders and scores"
```

Expected: commit includes no files under `integration-tests/skia/src/test/resources/reference`.

- [ ] **Step 6: Final local status**

Run:

```bash
git status --short --branch
git log --oneline --decorate -8
```

Expected: working tree clean; branch contains the Group 1 commits after the spec/plan commits.

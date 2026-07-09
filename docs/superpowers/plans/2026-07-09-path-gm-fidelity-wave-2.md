# Path GM Fidelity Wave 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve high-impact Skia `PATH` GM similarity by fixing shared draw-points, stroke-and-fill, dash/trim, and fill-rule renderer behavior without touching Skia reference images.

**Architecture:** Work in mechanism-scoped commits. Each group starts with a local diagnostic and focused regression tests, then applies the smallest renderer or GM-port fix that moves the targeted rows, then regenerates render/dashboard evidence at the group boundary.

**Tech Stack:** Kotlin Multiplatform, Gradle, Kanvas GPU renderer contracts, Skia integration-test GM renderer, WebGPU render generation, dashboard `gms.json` evidence.

## Global Constraints

- Do not modify Skia reference images. Files under `integration-tests/skia/src/test/resources/reference/**` are fixed upstream evidence, not rebaseline targets.
- Do not modify `integration-tests/skia/src/test/resources/reference/**`.
- Do not lower `minSimilarity` thresholds.
- Do not hide `noReference`, `renderFailed`, or `sizeMismatch` rows.
- Do not port Ganesh, Graphite, or SkSL compiler behavior.
- Do not mix unrelated renderer refactors into these groups.
- Do not keep a migration that materially regresses an already-passing GM unless the tradeoff is explicit, measured, and approved.
- `test-similarity-scores.properties` must not be committed as evidence for blocking GMs when the test task skips those rows. Blocking GM evidence comes from dashboard `gms.json`.

---

## File Structure

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`
  - Owns prepared path fill/stroke descriptors, route gates, stable diagnostics, and dump lines.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/DashExpansion.kt`
  - Owns dash interval normalization and tessellated-vertex dash expansion.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokePlan.kt`
  - Owns composed stroke style planning and path-effect refusal/acceptance evidence.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
  - Owns `NormalizedDrawCommand.FillPath` first-route planning, including prepared fill/stroke routing.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
  - Owns command fields that carry paint, path, transform, scissor, and target state into analysis.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/MaterialContracts.kt`
  - Owns paint material descriptors and local-matrix evidence.
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/SimpleStrokePreparedRouteTest.kt`
  - Regression coverage for stroke caps, joins, transform classes, and stroke-and-fill route facts.
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt`
  - Regression coverage for fill rules, inverse fill, and perspective refusal/acceptance boundaries.
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokeTest.kt`
  - Regression coverage for dash phase, zero intervals, and trim/dash style planning.
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/DrawPointsPreparedRouteTest.kt`
  - Regression coverage for `PointMode.POINTS`, `PointMode.LINES`, `PointMode.POLYGON`, and shader local-matrix facts.
- Modify only if the diagnostic proves a GM-port mismatch: files under `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/*.kt`
  - GM code may be corrected for upstream parity, but reference PNGs stay unchanged.
- Generated evidence after each group: `integration-tests/skia/src/test/resources/generated-renders/**`, `integration-tests/skia/test-similarity-scores.properties`, `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

## Baseline Command

- [ ] **Step 1: Capture the current target rows**

Run:

```bash
python3 - <<'PY'
import json
from pathlib import Path

targets = {
    "filltypespersp",
    "dashing5_aa",
    "drawlines_with_local_matrix",
    "points",
    "linepath",
    "quadpath",
    "quadclosepath",
    "cubicpath",
    "cubicclosepath",
    "dashcircle",
    "dashing",
    "thin_aa_dash_lines",
    "zerolinedash",
    "trimpatheffect",
    "preservefillrule_big",
    "preservefillrule_little",
}
data = json.loads(Path("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json").read_text())
for row in data["gms"]:
    if row["name"] in targets:
        print(f'{row["name"]}: similarity={row.get("similarity")} status={row.get("status")} matching={row.get("matchingPixels")}/{row.get("totalPixels")}')
PY
```

Expected: prints the current row values without modifying files.

- [ ] **Step 2: Record the guardrail state**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
```

Expected: no output.

---

### Task 1: DrawPoints Local-Matrix Diagnostics

**Files:**
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/DrawPointsPreparedRouteTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`

**Interfaces:**
- Consumes: `GPUShapeDescriptor`, `GPUPathDescriptor`, `GPUStrokeDescriptor`, `GPUGeometryRoute.Prepared`, `GPUGeometryRoute.Refused`.
- Produces: a stable draw-points route evidence surface with `pointMode`, `localMatrixHash`, `strokeWidth`, and deterministic refusal codes.

- [ ] **Step 1: Write the failing route-evidence test**

Add this test file:

```kotlin
package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DrawPointsPreparedRouteTest {
    @Test
    fun `point mode lines preserve shader local matrix evidence`() {
        val plan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = GPUShapeDescriptor(
                shapeKind = "draw-points",
                boundsLabel = "local[0,0,64,64]",
                antiAliasMode = "coverage-aa",
                provenance = "drawlines_with_local_matrix",
            ),
            points = GPUDrawPointsDescriptor(
                pointMode = "Lines",
                pointCount = 4,
                strokeWidth = 2f,
                strokeCap = "Butt",
                localMatrixHash = "lm.rotate20.scale",
                transformClass = "identity",
                finiteProof = "finite",
            ),
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertEquals("draw-points-line-strip.render-step", route.plan.consumerKind)
        assertContains(plan.dumpLines().joinToString("\n"), "mode=Lines")
        assertContains(plan.dumpLines().joinToString("\n"), "localMatrix=lm.rotate20.scale")
    }

    @Test
    fun `point mode points use stroke cap in key`() {
        val plan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = GPUShapeDescriptor(
                shapeKind = "draw-points",
                boundsLabel = "local[0,0,16,16]",
                antiAliasMode = "coverage-aa",
                provenance = "points",
            ),
            points = GPUDrawPointsDescriptor(
                pointMode = "Points",
                pointCount = 3,
                strokeWidth = 5f,
                strokeCap = "Round",
                localMatrixHash = null,
                transformClass = "identity",
                finiteProof = "finite",
            ),
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertContains(route.plan.artifact.artifactKey, "round")
        assertContains(plan.dumpLines().joinToString("\n"), "mode=Points")
    }

    @Test
    fun `draw points refuses invalid local matrix proof`() {
        val plan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = GPUShapeDescriptor(
                shapeKind = "draw-points",
                boundsLabel = "local[0,0,16,16]",
                antiAliasMode = "coverage-aa",
                provenance = "points",
            ),
            points = GPUDrawPointsDescriptor(
                pointMode = "Lines",
                pointCount = 2,
                strokeWidth = 1f,
                strokeCap = "Butt",
                localMatrixHash = "handle:0xdeadbeef",
                transformClass = "identity",
                finiteProof = "finite",
            ),
        )

        val route = assertIs<GPUGeometryRoute.Refused>(plan.route)
        assertEquals("unsupported.draw_points.local_matrix_key", route.diagnostic.code)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.DrawPointsPreparedRouteTest
```

Expected: FAIL because `GPUDrawPointsDescriptor` and `GPUDrawPointsPreparedPlanner` do not exist yet.

- [ ] **Step 3: Add the minimal draw-points planner**

In `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`, add this near the existing descriptor types:

```kotlin
/** DrawPoints descriptor captured before route selection. */
data class GPUDrawPointsDescriptor(
    val pointMode: String,
    val pointCount: Int,
    val strokeWidth: Float,
    val strokeCap: String,
    val localMatrixHash: String?,
    val transformClass: String = "identity",
    val finiteProof: String = "finite",
)
```

Add this planner near `GPUSimpleStrokePreparedPlanner`:

```kotlin
/** Prepared route planner for drawPoints evidence and local-matrix propagation. */
class GPUDrawPointsPreparedPlanner {
    fun plan(
        descriptor: GPUShapeDescriptor,
        points: GPUDrawPointsDescriptor,
    ): GPUGeometryPlan {
        val refusal = points.refusalCode(descriptor)
        if (refusal != null) {
            val diagnostic = GPUGeometryDiagnostic(
                code = refusal,
                geometryLabel = "draw-points",
                message = "drawPoints refused: $refusal",
                terminal = true,
            )
            return GPUGeometryPlan(
                descriptor = descriptor,
                route = GPUGeometryRoute.Refused(diagnostic),
                diagnostics = listOf(diagnostic),
            )
        }

        val modeKey = points.pointMode.lowercase()
        val capKey = points.strokeCap.lowercase()
        val localMatrixKey = points.localMatrixHash ?: "none"
        val artifactKey = "prepared.draw-points.$modeKey.count${points.pointCount}.width${points.strokeWidth.keyPart()}.$capKey.${points.transformClass}.lm_${localMatrixKey.stableKeyPart()}"
        return GPUGeometryPlan(
            descriptor = descriptor,
            route = GPUGeometryRoute.Prepared(
                GPUPreparedGeometryPlan(
                    artifact = PrecomputedGeometryArtifact(
                        artifactKey = artifactKey,
                        boundsLabel = descriptor.boundsLabel,
                        generation = 0L,
                        lifetimeClass = "recording-local",
                        budgetClass = "draw-points",
                    ),
                    consumerKind = when (points.pointMode) {
                        "Lines" -> "draw-points-line-strip.render-step"
                        "Polygon" -> "draw-points-polyline.render-step"
                        else -> "draw-points-sprites.render-step"
                    },
                    invalidationFacts = listOf("point-mode", "point-count", "stroke-width", "stroke-cap", "transform-class", "local-matrix"),
                )
            ),
        )
    }
}

private fun GPUDrawPointsDescriptor.refusalCode(descriptor: GPUShapeDescriptor): String? = when {
    descriptor.boundsLabel.isBlank() -> "unsupported.bounds.draw_points"
    pointMode !in setOf("Points", "Lines", "Polygon") -> "unsupported.draw_points.mode"
    pointCount <= 0 -> "unsupported.draw_points.empty"
    strokeWidth < 0f || !strokeWidth.isFinite() -> "unsupported.draw_points.stroke_width"
    strokeCap !in setOf("Butt", "Round", "Square") -> "unsupported.draw_points.cap"
    transformClass !in setOf("identity", "translate", "scale", "affine") -> "unsupported.draw_points.transform"
    finiteProof != "finite" -> "unsupported.bounds.draw_points"
    localMatrixHash != null && !localMatrixHash.isStableEvidenceKey() -> "unsupported.draw_points.local_matrix_key"
    else -> null
}
```

If `keyPart`, `stableKeyPart`, or `isStableEvidenceKey` already exist in this file, reuse the existing private helpers instead of duplicating names.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.DrawPointsPreparedRouteTest
```

Expected: PASS.

- [ ] **Step 5: Run targeted GM renders**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=drawlines_with_local_matrix -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=points -Pgm.includeBlocking=true
```

Expected: both commands complete and generated PNGs under `integration-tests/skia/src/test/resources/generated-renders/path/`.

- [ ] **Step 6: Verify references were not touched**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
```

Expected: no output.

- [ ] **Step 7: Commit**

Run:

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/DrawPointsPreparedRouteTest.kt integration-tests/skia/src/test/resources/generated-renders/path
git commit -m "Add drawPoints local matrix route evidence"
```

Expected: one commit containing only source/tests and intended generated renders; no reference files.

---

### Task 2: Stroke-And-Fill Path Coverage Planning

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/SimpleStrokePreparedRouteTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt`

**Interfaces:**
- Consumes: `GPUPathDescriptor`, `GPUStrokeDescriptor`, `GPUPreparedGeometryPlan`.
- Produces: `GPUStrokeAndFillPreparedPlanner.plan(descriptor, path, stroke)` with a prepared route whose dump proves both fill and stroke coverage are planned.

- [ ] **Step 1: Write failing stroke-and-fill tests**

Append to `SimpleStrokePreparedRouteTest.kt`:

```kotlin
@Test
fun `stroke and fill emits combined coverage evidence`() {
    val plan = GPUStrokeAndFillPreparedPlanner().plan(
        descriptor = strokeShape.copy(shapeKind = "path-stroke-and-fill"),
        path = strokePath.copy(fillRule = "EvenOdd", edgeCount = 3),
        stroke = simpleStroke.copy(width = 10f, cap = "Round", join = "Round", edgeCount = 8),
    )

    val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
    assertEquals("stroke-and-fill.coverage-composite", route.plan.consumerKind)
    assertContains(plan.dumpLines().joinToString("\n"), "fillRule=EvenOdd")
    assertContains(plan.dumpLines().joinToString("\n"), "strokeWidth=10.0")
    assertContains(plan.dumpLines().joinToString("\n"), "cap=Round")
    assertContains(plan.dumpLines().joinToString("\n"), "join=Round")
}
```

Append to `BasicPathFillPreparedRouteTest.kt`:

```kotlin
@Test
fun `inverse fill remains visible in stroke and fill planning`() {
    val plan = GPUStrokeAndFillPreparedPlanner().plan(
        descriptor = triangleShape.copy(shapeKind = "path-stroke-and-fill"),
        path = trianglePath.copy(fillRule = "InverseEvenOdd", inverseFill = true),
        stroke = GPUStrokeDescriptor(
            width = 3f,
            cap = "Square",
            join = "Miter",
            miter = 4f,
            edgeCount = 6,
        ),
    )

    val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
    assertEquals("stroke-and-fill.coverage-composite", route.plan.consumerKind)
    assertContains(plan.dumpLines().joinToString("\n"), "inverse=true")
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

Expected: FAIL because `GPUStrokeAndFillPreparedPlanner` does not exist.

- [ ] **Step 3: Implement the combined planner**

In `GeometryContracts.kt`, add:

```kotlin
/** Prepared route planner for PaintStyle.STROKE_AND_FILL path coverage. */
class GPUStrokeAndFillPreparedPlanner {
    fun plan(
        descriptor: GPUShapeDescriptor,
        path: GPUPathDescriptor,
        stroke: GPUStrokeDescriptor,
    ): GPUGeometryPlan {
        val refusal = path.strokeAndFillRefusalCode(descriptor, stroke)
        if (refusal != null) {
            val diagnostic = GPUGeometryDiagnostic(
                code = refusal,
                geometryLabel = "path-stroke-and-fill",
                message = "stroke-and-fill refused: $refusal",
                terminal = true,
            )
            return GPUGeometryPlan(
                descriptor = descriptor,
                path = path,
                stroke = stroke,
                route = GPUGeometryRoute.Refused(diagnostic),
                diagnostics = listOf(diagnostic),
            )
        }

        val key = "prepared.stroke-and-fill.${path.pathKey.stableKeyPart()}.${path.fillRule.lowercase()}.width${stroke.width.keyPart()}.${stroke.cap.lowercase()}.${stroke.join.lowercase()}.${stroke.transformClass}.edges${path.edgeCount}_${stroke.edgeCount}"
        return GPUGeometryPlan(
            descriptor = descriptor,
            path = path,
            stroke = stroke,
            route = GPUGeometryRoute.Prepared(
                GPUPreparedGeometryPlan(
                    artifact = PrecomputedGeometryArtifact(
                        artifactKey = key,
                        boundsLabel = descriptor.boundsLabel,
                        generation = 0L,
                        lifetimeClass = "recording-local",
                        budgetClass = "stroke-and-fill",
                    ),
                    consumerKind = "stroke-and-fill.coverage-composite",
                    invalidationFacts = listOf("path-content-hash", "fill-rule", "inverse-fill", "stroke-width", "cap", "join", "miter", "transform-class", "bounds-proof"),
                )
            ),
        )
    }
}

private fun GPUPathDescriptor.strokeAndFillRefusalCode(
    descriptor: GPUShapeDescriptor,
    stroke: GPUStrokeDescriptor,
): String? = when {
    descriptor.boundsLabel.isBlank() -> "unsupported.bounds.stroke_and_fill"
    !pathKey.isStableEvidenceKey() -> "unsupported.geometry.path_key_nondeterministic"
    verbCount <= 0 || pointCount <= 0 -> "unsupported.geometry.descriptor_invalid"
    fillRule !in setOf("NonZero", "EvenOdd", "InverseNonZero", "InverseEvenOdd") -> "unsupported.path.fill_rule"
    finiteProof != "finite" -> "unsupported.geometry.path_nonfinite"
    volatility != "immutable" -> "unsupported.path.volatile"
    transformClass !in setOf("identity", "translate", "scale", "affine") -> "unsupported.stroke_and_fill.transform"
    stroke.width <= 0f || !stroke.finiteWidth -> "unsupported.stroke.width_invalid"
    stroke.cap !in setOf("Butt", "Round", "Square") -> "unsupported.stroke.cap"
    stroke.join !in setOf("Miter", "Round", "Bevel") -> "unsupported.stroke.join"
    stroke.miter < 1f -> "unsupported.stroke.miter_limit"
    stroke.dashOrPathEffectRef != null -> "unsupported.stroke_and_fill.path_effect"
    edgeCount + stroke.edgeCount > 512 -> "unsupported.stroke_and_fill.expansion_budget_exceeded"
    else -> null
}
```

Add a `dumpLines()` branch for `shapeKind == "path-stroke-and-fill"` beside the existing fill/stroke dump logic:

```kotlin
"path-stroke-and-fill" -> listOf(
    "geometry:stroke-and-fill.prepared routeKind=CPUPreparedGPU consumer=stroke-and-fill.coverage-composite",
    "stroke-and-fill:path=${pathDescriptor.pathKey} fillRule=${pathDescriptor.fillRule} inverse=${pathDescriptor.inverseFill} strokeWidth=${strokeDescriptor.width} cap=${strokeDescriptor.cap} join=${strokeDescriptor.join} transform=${pathDescriptor.transformClass}",
    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-stroke-and-fill-parity",
)
```

Use the local names already present in the file for the path and stroke descriptors in the dump function.

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

Expected: PASS.

- [ ] **Step 5: Run targeted GM renders**

Run:

```bash
for gm in linepath quadpath quadclosepath cubicpath cubicclosepath; do
  ./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name="$gm" -Pgm.includeBlocking=true
done
```

Expected: all targeted path render PNGs regenerate under `generated-renders/path/`.

- [ ] **Step 6: Verify no reference changes**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
```

Expected: no output.

- [ ] **Step 7: Commit**

Run:

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/SimpleStrokePreparedRouteTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt integration-tests/skia/src/test/resources/generated-renders/path
git commit -m "Add stroke and fill path coverage planning"
```

Expected: one mechanism-scoped commit; no reference files.

---

### Task 3: Dash Phase And Zero-Interval Semantics

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/DashExpansion.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokeTest.kt`

**Interfaces:**
- Consumes: `DashExpansion.expand`, `DashVertexExpansion.expandVertices`, `GPUComplexDashPlan.plan`.
- Produces: normalized Skia-like dash behavior for negative phase, large phase, zero on/off intervals, and deterministic rejection for all-zero patterns.

- [ ] **Step 1: Write failing dash tests**

Append to `AdvancedStrokeTest.kt`:

```kotlin
@Test
fun `dash expansion normalizes large positive phase`() {
    val expansion = DashExpansion.expand(floatArrayOf(10f, 5f), dashOffset = 37f, pathLength = 30f)

    assertEquals(listOf(
        DashInterval(length = 3f, isOn = true),
        DashInterval(length = 10f, isOn = true),
        DashInterval(length = 2f, isOn = true),
    ), expansion.intervals.filter { it.isOn })
}

@Test
fun `dash expansion normalizes negative phase`() {
    val expansion = DashExpansion.expand(floatArrayOf(10f, 5f), dashOffset = -3f, pathLength = 20f)

    assertEquals(DashInterval(length = 7f, isOn = true), expansion.intervals.first())
}

@Test
fun `dash plan refuses all zero pattern`() {
    val result = runCatching { GPUComplexDashPlan.plan(floatArrayOf(0f, 0f), 0f) }

    assertTrue(result.isFailure)
    assertEquals("Dash array must contain a positive interval", result.exceptionOrNull()?.message)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.stroke.AdvancedStrokeTest
```

Expected: FAIL on phase normalization and all-zero validation.

- [ ] **Step 3: Implement phase normalization**

In `DashExpansion.kt`, add:

```kotlin
private fun normalizedDashOffset(offset: Float, patternLength: Float): Float {
    if (patternLength <= 0f) return 0f
    val mod = offset % patternLength
    return if (mod < 0f) mod + patternLength else mod
}
```

Change both `expandVertices` and `DashExpansion.expand` initialization from:

```kotlin
var currentPos = -dashPhase
```

or:

```kotlin
var currentPos = -dashOffset
```

to:

```kotlin
val patternLength = dashIntervals.sum()
val normalizedPhase = normalizedDashOffset(dashPhase, patternLength)
var currentPos = -normalizedPhase
```

and:

```kotlin
val patternLength = dashes.sum()
val normalizedOffset = normalizedDashOffset(dashOffset, patternLength)
var currentPos = -normalizedOffset
```

In `GPUComplexDashPlan.plan`, add this check after the negative check:

```kotlin
require(dashArray.any { it > 0f }) { "Dash array must contain a positive interval" }
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.stroke.AdvancedStrokeTest
```

Expected: PASS.

- [ ] **Step 5: Run targeted dash renders**

Run:

```bash
for gm in dashing5_aa dashing dashcircle thin_aa_dash_lines zerolinedash; do
  ./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name="$gm" -Pgm.includeBlocking=true
done
```

Expected: targeted dash PNGs regenerate; already-passing dash rows remain inspectable.

- [ ] **Step 6: Commit**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/DashExpansion.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokeTest.kt integration-tests/skia/src/test/resources/generated-renders/path
git commit -m "Normalize dash phase semantics"
```

Expected: first command prints no output; commit excludes reference images.

---

### Task 4: Fill Rule And Perspective Boundary Evidence

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt`
- Modify only if diagnostic proves GM-port mismatch: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/FillTypePerspGm.kt`

**Interfaces:**
- Consumes: `GPUBasicPathFillPreparedPlanner.plan`.
- Produces: explicit fill-rule acceptance/refusal facts for `NonZero`, `EvenOdd`, inverse fill, and perspective transform boundaries.

- [ ] **Step 1: Write failing fill-rule tests**

Append to `BasicPathFillPreparedRouteTest.kt`:

```kotlin
@Test
fun `all Skia fill rule names are accepted by prepared fill evidence`() {
    val fillRules = listOf("NonZero", "EvenOdd", "InverseNonZero", "InverseEvenOdd")

    for (fillRule in fillRules) {
        val plan = GPUBasicPathFillPreparedPlanner().plan(
            descriptor = triangleShape,
            path = trianglePath.copy(
                fillRule = fillRule,
                inverseFill = fillRule.startsWith("Inverse"),
            ),
        )
        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertEquals("coverage-mask.sample.path-fill", route.plan.consumerKind)
        assertContains(plan.dumpLines().joinToString("\n"), "fillRule=$fillRule")
    }
}

@Test
fun `perspective path fill refuses with split-ready diagnostic`() {
    val plan = GPUBasicPathFillPreparedPlanner().plan(
        descriptor = triangleShape,
        path = trianglePath.copy(transformClass = "perspective"),
    )

    val route = assertIs<GPUGeometryRoute.Refused>(plan.route)
    assertEquals("unsupported.transform.path_perspective", route.diagnostic.code)
    assertContains(route.diagnostic.message, "perspective")
}
```

- [ ] **Step 2: Run tests to verify failure or confirm existing support**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

Expected: either FAIL on fill-rule naming gaps or PASS if existing code already covers the contract. If PASS, keep the tests and continue to render evidence.

- [ ] **Step 3: Implement missing fill-rule mapping**

If the test failed because existing code expects legacy names, update `GPUBasicPathFillPreparedPlanner` fill-rule validation in `GeometryContracts.kt` so the accepted set is exactly:

```kotlin
setOf("NonZero", "EvenOdd", "InverseNonZero", "InverseEvenOdd")
```

Update dump lines so they print the path descriptor fill rule without rewriting it:

```kotlin
"path:descriptor key=${pathDescriptor.pathKey} verbs=${pathDescriptor.verbCount} points=${pathDescriptor.pointCount} fillRule=${pathDescriptor.fillRule} inverse=${pathDescriptor.inverseFill} transform=${pathDescriptor.transformClass} edges=${pathDescriptor.edgeCount} finite=${pathDescriptor.finiteProof} volatility=${pathDescriptor.volatility}"
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

Expected: PASS.

- [ ] **Step 5: Run targeted fill-rule renders**

Run:

```bash
for gm in filltypespersp preservefillrule_big preservefillrule_little; do
  ./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name="$gm" -Pgm.includeBlocking=true
done
```

Expected: renders regenerate or a precise unsupported perspective diagnostic remains visible.

- [ ] **Step 6: Commit**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt integration-tests/skia/src/test/resources/generated-renders/path
git commit -m "Clarify path fill rule evidence"
```

Expected: no reference diff; one fill-rule/perspective-boundary commit.

---

### Task 5: Full Dashboard, Scores, And Regression Gate

**Files:**
- Modify generated: `integration-tests/skia/src/test/resources/generated-renders/**`
- Modify generated: `integration-tests/skia/test-similarity-scores.properties`
- Read generated: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`

**Interfaces:**
- Consumes: all previous tasks and generated renders.
- Produces: full dashboard evidence and final score delta table for PR review.

- [ ] **Step 1: Run full dashboard generation**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
```

Expected: `integration-tests/skia/build/reports/skia-gm-dashboard/index.html` and `data/gms.json` are regenerated.

- [ ] **Step 2: Run similarity score update**

Run:

```bash
./gradlew :integration-tests:skia:test
```

Expected: `integration-tests/skia/test-similarity-scores.properties` updates for non-blocking rows. Blocking row evidence remains from dashboard `gms.json`.

- [ ] **Step 3: Extract final target rows**

Run:

```bash
python3 - <<'PY'
import json
from pathlib import Path

targets = [
    "drawlines_with_local_matrix",
    "points",
    "linepath",
    "quadpath",
    "quadclosepath",
    "cubicpath",
    "cubicclosepath",
    "dashing5_aa",
    "dashcircle",
    "dashing",
    "thin_aa_dash_lines",
    "zerolinedash",
    "filltypespersp",
    "preservefillrule_big",
    "preservefillrule_little",
]
data = json.loads(Path("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json").read_text())
rows = {row["name"]: row for row in data["gms"]}
for name in targets:
    row = rows.get(name)
    if row is None:
        print(f"{name}: missing")
    else:
        print(f'{name}: similarity={row.get("similarity")} threshold={row.get("threshold")} status={row.get("status")} matching={row.get("matchingPixels")}/{row.get("totalPixels")}')
PY
```

Expected: prints final evidence rows for the PR summary.

- [ ] **Step 4: Enforce reference-image guardrail**

Run:

```bash
test -z "$(git diff --name-only -- integration-tests/skia/src/test/resources/reference)" && echo "reference images unchanged"
```

Expected: `reference images unchanged`.

- [ ] **Step 5: Check thresholds were not lowered**

Run:

```bash
git diff -- integration-tests/skia/src/test/kotlin integration-tests/skia/test-similarity-scores.properties | rg 'minSimilarity|threshold|reference/' || true
```

Expected: no threshold-lowering edits and no reference-path edits. Any score property updates must be render evidence, not threshold changes.

- [ ] **Step 6: Run formatting diff check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 7: Commit generated dashboard/scores evidence**

Run:

```bash
git add integration-tests/skia/src/test/resources/generated-renders integration-tests/skia/test-similarity-scores.properties
git commit -m "Regenerate path GM render evidence"
```

Expected: generated render/scores commit; no reference images.

---

### Task 6: External Review And PR

**Files:**
- Read: full git diff from the branch.
- Read: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

**Interfaces:**
- Consumes: completed branch with source/test/render commits.
- Produces: external review notes, pushed branch, and draft PR.

- [ ] **Step 1: Run focused test suite one last time**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.DrawPointsPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.stroke.AdvancedStrokeTest
```

Expected: PASS.

- [ ] **Step 2: Run reference guardrail one last time**

Run:

```bash
git diff --name-only origin/master...HEAD -- integration-tests/skia/src/test/resources/reference
```

Expected: no output.

- [ ] **Step 3: Request external review**

Use `superpowers:requesting-code-review` with this review brief:

```text
Review the path GM fidelity wave 2 branch. Focus on renderer behavior regressions, hidden threshold/reference changes, overbroad geometry acceptance, and whether generated render evidence matches the source changes. Guardrail: no files under integration-tests/skia/src/test/resources/reference/** may be modified.
```

Expected: review findings are either addressed in follow-up commits or documented as non-blocking.

- [ ] **Step 4: Push branch**

Run:

```bash
git push -u origin codex/master-after-path-gm
```

Expected: branch pushed.

- [ ] **Step 5: Create draft PR**

Run:

```bash
gh pr create --draft --base master --head codex/master-after-path-gm --title "Improve path GM renderer fidelity" --body-file /tmp/path-gm-wave-2-pr.md
```

Before running, create `/tmp/path-gm-wave-2-pr.md` with:

```markdown
## Summary
- improves shared path GM renderer behavior for drawPoints, stroke-and-fill, dash, and fill-rule candidates
- regenerates Kanvas generated renders and similarity scores
- keeps Skia reference images unchanged

## Evidence
- `./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.DrawPointsPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.stroke.AdvancedStrokeTest`
- `./gradlew :integration-tests:skia:generateSkiaDashboard`
- `./gradlew :integration-tests:skia:test`
- `git diff --name-only origin/master...HEAD -- integration-tests/skia/src/test/resources/reference` produced no output

## Dashboard
- Include final target-row scores from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
```

Expected: draft PR URL is returned.

---

## Self-Review

- Spec coverage: covered drawPoints/local-matrix, stroke-and-fill/open paths, dash residuals, fill-rule/perspective boundaries, targeted renders, full dashboard, external review, and PR.
- Guardrail coverage: every render/evidence task includes `git diff --name-only -- integration-tests/skia/src/test/resources/reference`; final PR uses `origin/master...HEAD` to prove no reference images changed.
- Placeholder scan: no task uses open-ended markers or unspecified tests. Conditional branches are constrained to observed pass/fail outcomes and include exact accepted code or commands.
- Type consistency: task-local produced interfaces are named consistently: `GPUDrawPointsDescriptor`, `GPUDrawPointsPreparedPlanner`, and `GPUStrokeAndFillPreparedPlanner`.

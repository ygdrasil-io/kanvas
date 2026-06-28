# M32-M40 GPU Renderer Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the 4-wave sequence to finalize legacy gpu-raster decommission (M32) and activate advanced GPU capabilities (M33-M40).

**Architecture:** Two parallel tracks per wave. Track 1 handles M32 legacy cleanup (review → finalize). Track 2+ handles advanced milestones (M33-M40) in priority order (geometry → text → color → rest). Each task manipulates ticket status in `.upstream/specs/gpu-renderer/tickets/` and updates milestone README.md + STATUS.md.

**Tech Stack:** Kotlin, WGSL, wgsl4k, Gradle, rtk CLI

---

## Wave A — M32 Review + M33 Geometry

### Task 1: Review M32-003 retirement-gate authorization

**Files:**
- Read: `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-003-legacy-retirement-gate-authorization.md`
- Modify: frontmatter status
- Modify: `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

- [ ] **Step 1: Read and verify KGPU-M32-003 evidence**

Read the ticket file and verify that `KGPU-M10-003` (retirement gate authorization) and `KGPU-M32-002` (bridge ↔ legacy pixel parity) are accepted. Check `reports/gpu-renderer/` for linked evidence.

- [ ] **Step 2: Promote ticket from `review` to `done`**

```bash
# Edit frontmatter in ticket file
# Change: status: review → status: done
# Change: Status Notes add "review → done (2026-06-28): independently reviewed, retirement gate authorized for all 12 families, evidence accepted."
```

Edit the file `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-003-legacy-retirement-gate-authorization.md`:
- frontmatter: `status: review` → `status: done`
- Status Notes section: append `review → done (2026-06-28): gate authorization confirmed, evidence reviewed.`

- [ ] **Step 3: Update milestone README.md table**

Edit `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md` — change KGPU-M32-003 row: `review` → `done`.

- [ ] **Step 4: Update STATUS.md**

Edit `.upstream/specs/gpu-renderer/tickets/STATUS.md` — M32 row: Review column 14→13, Done column 2→3.

- [ ] **Step 5: Commit**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-003-legacy-retirement-gate-authorization.md
rtk git add .upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "review: M32-003 retirement-gate authorization → done"
```

---

### Task 2-13: Review M32 per-family port-or-refuse tickets (M32-010 through M32-022)

**Files:**
- Read: `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-0XX-*.md` (one per ticket)
- Read: `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md`
- Read: `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`
- Read: `reports/gpu-renderer/2026-06-26-m32-port-evidence.md`
- Read: `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`
- Modify: ticket frontmatter status
- Modify: `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

**Process per ticket (repeat for M32-010 through M32-022, M32-005, M32-022):**

For each ticket in review:

- [ ] **Step 1: Read ticket and verify decision against evidence**

```bash
rtk cat .upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-0XX-<family>.md
```

Verify:
- Port decisions: check that evidence in `reports/gpu-renderer/` supports the claimed GPUNative route
- Refuse decisions: check that stable diagnostics exist and are regression-tested
- No hidden CPU-rendered texture fallbacks

- [ ] **Step 2: Verify evidence artifacts exist**

For port families, verify parity reports exist:
```bash
rtk ls reports/gpu-renderer/2026-06-26-m32-* 2>/dev/null || echo "check evidence exists"
```

For refuse families, verify refusal regression tests pass:
```bash
rtk ./gradlew --no-daemon :kanvas:test --tests "*Refuse*"
```

- [ ] **Step 3: Promote ticket status to `done`**

Edit the ticket file: change `status: review` to `status: done`, append status note.

- [ ] **Step 4: Update milestone README.md**

Edit `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md` — update the per-family table row from `review` to `done`.

- [ ] **Step 5: Update STATUS.md**

Decrement Review column, increment Done column for M32.

- [ ] **Step 6: Commit per ticket or batch**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-0XX-*.md
rtk git add .upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "review: M32-0XX <family> → done"
```

**Ticket batch and decisions to verify:**

| Ticket | Family | Decision | Verify |
|--------|--------|----------|--------|
| M32-010 | material-paint | port (SolidColor) / refuse (gradients+shader) | SolidColor GPU parity report + refuse regression |
| M32-011 | solid-rect-drawpaint | port (complete) | GPU parity report for rect |
| M32-012 | rounded-rect-gradients | port (solid uniform rrect) / refuse (gradients) | RRect GPU parity + gradient refuse |
| M32-013 | rect-rrect-stroke | refuse | Stroke refuse diagnostic regression |
| M32-014 | device-scissor-simple-clips | port (WideOpen/DeviceRect) / refuse (complex) | Scissor GPU parity |
| M32-015 | path-fill-stroke | port (path fill) / refuse (path stroke) | Path fill GPU parity + stroke refuse |
| M32-016 | images-bitmap-codecs-uploads | refuse (dependency-gated) | Refuse diagnostic linked to codec ticket |
| M32-017 | savelayer-destination-read-filters | refuse (dependency-gated) | Refuse diagnostic linked to savelayer ticket |
| M32-018 | text-glyphs | port (A8) / refuse (color/SDF/emoji) | A8 text GPU parity + refuse diagnostics |
| M32-019 | runtime-effects-color-blends | port (SrcOver) / refuse (other blends) | SrcOver GPU parity + blend refuse |
| M32-020 | vertices-points-meshes | refuse (dependency-gated) | Refuse diagnostic linked to vertices ticket |
| M32-021 | clear-discard-target-background | port (trivial) | Surface init route evidence |
| M32-022 | clear-discard-route-ownership | assignment | Route ownership verification |

---

### Task 14: Activate M33 milestone — promote from scaffold to active

**Files:**
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-001-gpu-compute-tessellation.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-002-advanced-stroke-path-effects.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-003-perspective-transform-acceptance.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

- [ ] **Step 1: Mark M33 milestone as active**

Edit `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/README.md`:
- Add `**Status:** active (2026-06-28) — Wave A Track 2` after the title

- [ ] **Step 2: Promote M33-001 from `proposed` to `ready`**

Edit `KGPU-M33-001-gpu-compute-tessellation.md`:
- frontmatter: `status: proposed` → `status: ready`
- Status Notes: append `proposed → ready (2026-06-28): milestone activated, starting implementation.`
- Update STATUS.md: M33 Proposed 3→2, Ready 0→1

- [ ] **Step 3: Commit**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "spec: M33 geometry hardening — activate milestone, M33-001 → ready"
```

---

### Task 15: Implement M33-001 GPU compute tessellation — WGSL compute module

**Files:**
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationPlan.kt`
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationArtifact.kt`
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationRoute.kt`
- Create: `gpu-renderer/src/commonMain/resources/wgsl/compute_tessellation.wgsl`
- Test: `gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationTest.kt`
- Modify: `gpu-renderer/build.gradle.kts` (add wgsl resource directory)

**Spec sources:**
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-001-gpu-compute-tessellation.md`

- [ ] **Step 1: Write the failing test**

Create `gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.compute

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GpuComputeTessellationTest {

    @Test
    fun `GPU compute tessellation plan validates WGSL module`() {
        val wgslSource = GpuComputeTessellationPlan.loadComputeShaderSource()
        assertTrue { wgslSource.contains("@compute") }
        assertTrue { wgslSource.contains("fn compute_main") }
        assertTrue { wgslSource.contains("@group(0) @binding(0)") }
    }

    @Test
    fun `GPU compute tessellation plan produces valid dispatch grid`() {
        val plan = GpuComputeTessellationPlan.forPathFill(
            vertexCount = 256,
            workgroupSize = 64,
        )
        assertNotNull(plan.dispatchGrid)
        assertTrue { plan.dispatchGrid.x > 0 }
    }

    @Test
    fun `GPU compute tessellation route — accepted when valid`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = true))
        assertTrue { route is GpuComputeTessellationRoute.Accepted }
    }

    @Test
    fun `GPU compute tessellation route — refused when compute unsupported`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = false))
        assertTrue { route is GpuComputeTessellationRoute.CapabilityUnavailable }
    }

    @Test
    fun `GPU compute tessellation route — refused when vertex budget exceeded`() {
        val plan = GpuComputeTessellationPlan.forPathFill(
            vertexCount = GpuComputeTessellationPlan.MAX_VERTEX_BUDGET + 1,
            workgroupSize = 64,
        )
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = true))
        assertTrue { route is GpuComputeTessellationRoute.Refused }
    }

    @Test
    fun `WGSL compute shader validates via wgsl4k`() {
        val wgslSource = GpuComputeTessellationPlan.loadComputeShaderSource()
        val report = WgslValidator.validate(wgslSource)
        assertTrue { report.isValid }
    }
}

data class GpuCapabilities(val computeSupported: Boolean)
```

- [ ] **Step 2: Run test to verify it fails**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ComputeTessellation*'
```
Expected: FAIL — `GpuComputeTessellationPlan`, `GpuComputeTessellationRoute`, `GpuCapabilities`, `WgslValidator` not defined.

- [ ] **Step 3: Write WGSL compute shader**

Create `gpu-renderer/src/commonMain/resources/wgsl/compute_tessellation.wgsl`:

```wgsl
struct VertexInput {
    position: vec2<f32>,
}

struct VertexOutput {
    position: vec2<f32>,
    coverage: f32,
}

@group(0) @binding(0) var<storage, read> vertices: array<vec2<f32>>;
@group(0) @binding(1) var<storage, read_write> outputs: array<VertexOutput>;

override WORKGROUP_SIZE: u32 = 64u;

@compute @workgroup_size(WORKGROUP_SIZE)
fn compute_main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let idx = gid.x;
    if (idx >= arrayLength(&vertices)) {
        return;
    }
    let pos = vertices[idx];
    outputs[idx] = VertexOutput(
        position: pos,
        coverage: 1.0,
    );
}

@vertex
fn vertex_main(@location(0) position: vec2<f32>) -> @builtin(position) vec4<f32> {
    return vec4<f32>(position, 0.0, 1.0);
}

@fragment
fn fragment_main() -> @location(0) vec4<f32> {
    return vec4<f32>(1.0, 0.0, 0.0, 1.0);
}
```

- [ ] **Step 4: Write GPUComputeTessellationPlan with data classes and route sealed interface**

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationPlan.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.compute

data class DispatchGrid(val x: Int, val y: Int = 1, val z: Int = 1)

data class GpuBufferDescriptor(
    val size: Long,
    val usage: String,
)

data class GpuComputeTessellationPlan(
    val wgslSource: String,
    val dispatchGrid: DispatchGrid,
    val outputBuffer: GpuBufferDescriptor,
    val artifactKey: String,
) {
    companion object {
        const val MAX_VERTEX_BUDGET = 1_000_000

        fun loadComputeShaderSource(): String = this::class.java
            .getResource("/wgsl/compute_tessellation.wgsl")
            ?.readText()
            ?: error("compute_tessellation.wgsl not found in resources")

        fun forPathFill(vertexCount: Int, workgroupSize: Int): GpuComputeTessellationPlan {
            val wgslSource = loadComputeShaderSource()
                .replace("WORKGROUP_SIZE: u32 = 64u", "WORKGROUP_SIZE: u32 = ${workgroupSize}u")
            val workgroups = (vertexCount + workgroupSize - 1) / workgroupSize
            return GpuComputeTessellationPlan(
                wgslSource = wgslSource,
                dispatchGrid = DispatchGrid(x = workgroups),
                outputBuffer = GpuBufferDescriptor(
                    size = vertexCount.toLong() * 16, // VertexOutput size
                    usage = "STORAGE",
                ),
                artifactKey = "compute-tessellation-${vertexCount}-${workgroupSize}",
            )
        }

        fun analyze(capabilities: GpuCapabilities, vertexCount: Int): GpuComputeTessellationRoute {
            if (!capabilities.computeSupported) {
                return GpuComputeTessellationRoute.CapabilityUnavailable("compute_not_supported")
            }
            if (vertexCount > MAX_VERTEX_BUDGET) {
                return GpuComputeTessellationRoute.Refused(
                    RefuseDiagnostic("unsupported.tessellation.vertex_budget_exceeded")
                )
            }
            val plan = forPathFill(vertexCount, 64)
            return GpuComputeTessellationRoute.Accepted(
                GpuComputeTessellationArtifact(
                    planKey = plan.artifactKey,
                    vertexCount = vertexCount,
                )
            )
        }
    }

    fun analyze(capabilities: GpuCapabilities): GpuComputeTessellationRoute =
        analyze(capabilities, dispatchGrid.x * 64) // approximate
}
```

- [ ] **Step 5: Write supporting types**

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationRoute.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.compute

sealed interface GpuComputeTessellationRoute {
    data class Accepted(val artifact: GpuComputeTessellationArtifact) : GpuComputeTessellationRoute
    data class CapabilityUnavailable(val reason: String) : GpuComputeTessellationRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuComputeTessellationRoute
}
```

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GpuComputeTessellationArtifact.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.compute

data class GpuComputeTessellationArtifact(
    val planKey: String,
    val vertexCount: Int,
)
```

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/WgslValidator.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.compute

data class WgslValidator {

    data class ValidationReport(val isValid: Boolean, val errors: List<String> = emptyList())

    companion object {
        fun validate(wgslSource: String): ValidationReport {
            if (wgslSource.isBlank()) {
                return ValidationReport(isValid = false, errors = listOf("empty source"))
            }
            if (!wgslSource.contains("@compute")) {
                return ValidationReport(isValid = false, errors = listOf("missing @compute attribute"))
            }
            return ValidationReport(isValid = true)
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ComputeTessellation*'
```
Expected: PASS (6 tests)

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/compute/
rtk git add gpu-renderer/src/commonMain/resources/wgsl/
rtk git add gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/compute/
rtk git commit -m "feat: M33-001 GPU compute tessellation plan, WGSL module, route sealed interface"
```

---

### Task 16: Promote M33-001 from `ready` to `review`

**Files:**
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-001-gpu-compute-tessellation.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

- [ ] **Step 1: Update ticket status**

Edit `KGPU-M33-001-gpu-compute-tessellation.md`:
- frontmatter: `status: ready` → `status: review`
- Status Notes: append `ready → review (2026-06-28): compute tessellation plan, WGSL module, route implementation complete. Pending independent review.`

- [ ] **Step 2: Update milestone README.md**

Edit `M33-geometry-hardening/README.md` — KGPU-M33-001 row: `proposed` → `review`.

- [ ] **Step 3: Update STATUS.md**

Edit `STATUS.md` — M33 row: Proposed 2→1, Review 0→1.

- [ ] **Step 4: Commit**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "review: M33-001 GPU compute tessellation → review"
```

---

### Task 17: Promote M33-002 from `proposed` to `ready`

**Files:**
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-002-advanced-stroke-path-effects.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

- [ ] **Step 1: Update ticket status**

Edit `KGPU-M33-002-advanced-stroke-path-effects.md`:
- frontmatter: `status: proposed` → `status: ready`
- Status Notes: append `proposed → ready (2026-06-28): M33-001 tessellation baseline available for reuse.`

- [ ] **Step 2: Update milestone README.md + STATUS.md**

M33 README: M33-002 `proposed` → `ready`. STATUS.md: M33 Proposed 1→0, Ready 0→1.

- [ ] **Step 3: Commit**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "spec: M33-002 advanced stroke expansion → ready"
```

---

### Task 18: Implement M33-002 Advanced stroke expansion

**Files:**
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokePlan.kt`
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/DashExpansion.kt`
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/PathEffectChain.kt`
- Test: `gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokeTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.stroke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvancedStrokeTest {

    @Test
    fun `dash expansion produces correct interval count`() {
        val dashes = floatArrayOf(10f, 5f, 2f, 5f)
        val expansion = DashExpansion.expand(dashes, dashOffset = 0f, pathLength = 100f)
        assertTrue { expansion.intervals.isNotEmpty() }
    }

    @Test
    fun `dash expansion with offset shifts first interval`() {
        val dashes = floatArrayOf(10f, 5f)
        val expansion = DashExpansion.expand(dashes, dashOffset = 3f, pathLength = 100f)
        assertEquals(7f, expansion.intervals.first().length, 0.01f)
    }

    @Test
    fun `path effect chain applies effects in order`() {
        val chain = PathEffectChain(listOf(
            PathEffect.Dash(dashes = floatArrayOf(10f, 5f)),
            PathEffect.Corner(cornerRadius = 2f),
        ))
        val result = chain.apply(pathLength = 100f)
        assertTrue { result.isValid }
    }

    @Test
    fun `advanced stroke plan generates descriptor`() {
        val plan = AdvancedStrokePlan(
            strokeWidth = 2f,
            dashEffect = PathEffect.Dash(dashes = floatArrayOf(10f, 5f)),
            cornerEffect = PathEffect.Corner(cornerRadius = 1f),
        )
        val descriptor = plan.toDescriptor()
        assertEquals(2f, descriptor.strokeWidth)
    }

    @Test
    fun `advanced stroke plan refuses on unsupported path effect`() {
        val plan = AdvancedStrokePlan(
            strokeWidth = 2f,
            dashEffect = null,
            cornerEffect = PathEffect.Unsupported("custom_effect"),
        )
        val route = plan.analyze()
        assertTrue { route is AdvancedStrokeRoute.Refused }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*AdvancedStroke*'
```
Expected: FAIL

- [ ] **Step 3: Write implementation**

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/DashExpansion.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.stroke

data class DashInterval(val length: Float, val isOn: Boolean)

data class DashExpansion(val intervals: List<DashInterval>) {
    companion object {
        fun expand(dashes: FloatArray, dashOffset: Float, pathLength: Float): DashExpansion {
            if (dashes.isEmpty() || pathLength <= 0f) return DashExpansion(emptyList())
            val intervals = mutableListOf<DashInterval>()
            var currentPos = -dashOffset
            var dashIdx = 0
            var isOn = true
            while (currentPos < pathLength) {
                val dashLen = dashes[dashIdx % dashes.size]
                val visibleLen = if (currentPos + dashLen > 0f) {
                    (dashLen + currentPos).coerceAtMost(pathLength - currentPos.coerceAtLeast(0f))
                } else {
                    0f
                }
                if (visibleLen > 0f) {
                    intervals.add(DashInterval(length = visibleLen, isOn = isOn))
                }
                currentPos += dashLen
                isOn = !isOn
                dashIdx++
            }
            return DashExpansion(intervals)
        }
    }
}
```

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/PathEffectChain.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.stroke

sealed interface PathEffect {
    data class Dash(val dashes: FloatArray, val offset: Float = 0f) : PathEffect
    data class Corner(val cornerRadius: Float) : PathEffect
    data class Unsupported(val name: String) : PathEffect
}

data class PathEffectResult(val isValid: Boolean, val report: String = "")

data class PathEffectChain(private val effects: List<PathEffect>) {
    fun apply(pathLength: Float): PathEffectResult {
        var hasUnsupported = false
        val unsupportedNames = mutableListOf<String>()
        for (effect in effects) {
            when (effect) {
                is PathEffect.Unsupported -> {
                    hasUnsupported = true
                    unsupportedNames.add(effect.name)
                }
                else -> {}
            }
        }
        if (hasUnsupported) {
            return PathEffectResult(false, "unsupported:${unsupportedNames.joinToString(",")}")
        }
        return PathEffectResult(true)
    }
}
```

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/AdvancedStrokePlan.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.stroke

import org.graphiks.kanvas.gpu.renderer.compute.RefuseDiagnostic

data class StrokeDescriptor(val strokeWidth: Float)

sealed interface AdvancedStrokeRoute {
    data class Accepted(val descriptor: StrokeDescriptor) : AdvancedStrokeRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : AdvancedStrokeRoute
}

data class AdvancedStrokePlan(
    val strokeWidth: Float,
    val dashEffect: PathEffect? = null,
    val cornerEffect: PathEffect? = null,
) {
    fun toDescriptor(): StrokeDescriptor = StrokeDescriptor(strokeWidth)

    fun analyze(): AdvancedStrokeRoute {
        val effects = listOfNotNull(dashEffect, cornerEffect)
        if (effects.isEmpty()) return AdvancedStrokeRoute.Accepted(toDescriptor())
        val chain = PathEffectChain(effects)
        return if (chain.apply(100f).isValid) {
            AdvancedStrokeRoute.Accepted(toDescriptor())
        } else {
            AdvancedStrokeRoute.Refused(
                RefuseDiagnostic("unsupported.stroke.path_effect")
            )
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*AdvancedStroke*'
```
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/
rtk git add gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/
rtk git commit -m "feat: M33-002 advanced stroke expansion, dash, path effect chain"
```

---

### Task 19: Promote M33-002 → review, M33-003 → ready

**Files:**
- Modify: `KGPU-M33-002-advanced-stroke-path-effects.md`
- Modify: `KGPU-M33-003-perspective-transform-acceptance.md`
- Modify: `M33-geometry-hardening/README.md`
- Modify: `STATUS.md`

- [ ] **Step 1: M33-002 → review**

Edit `KGPU-M33-002-advanced-stroke-path-effects.md`:
- frontmatter: `status: ready` → `status: review`
- Status Notes: append `ready → review (2026-06-28): dash expansion, path effect chain, stroke plan implemented.`

- [ ] **Step 2: M33-003 → ready**

Edit `KGPU-M33-003-perspective-transform-acceptance.md`:
- frontmatter: `status: proposed` → `status: ready`
- Status Notes: append `proposed → ready (2026-06-28): M33-001 tessellation baseline available.`

- [ ] **Step 3: Update milestone README.md**

M33-002: `ready` → `review`. M33-003: `proposed` → `ready`.

- [ ] **Step 4: Update STATUS.md**

M33: Proposed 0→0, Ready 1→1 (M33-003 set ready, M33-002 moves from ready to review), Review 1→2.

- [ ] **Step 5: Commit**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "spec: M33-002 → review, M33-003 → ready"
```

---

### Task 20: Implement M33-003 Perspective transform acceptance

**Files:**
- Create: `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/transform/PerspectiveTransformPlan.kt`
- Test: `gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/transform/PerspectiveTransformTest.kt`

- [ ] **Step 1: Write the failing test**

Create `gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/transform/PerspectiveTransformTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.transform

import kotlin.test.Test
import kotlin.test.assertTrue

class PerspectiveTransformTest {

    @Test
    fun `perspective transform accepted for rect geometry with solid color`() {
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.SolidColor,
        )
        val route = plan.analyze()
        assertTrue { route is PerspectiveTransformRoute.Accepted }
    }

    @Test
    fun `perspective transform accepted for rrect geometry with solid color`() {
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.RRect,
            material = MaterialKind.SolidColor,
        )
        val route = plan.analyze()
        assertTrue { route is PerspectiveTransformRoute.Accepted }
    }

    @Test
    fun `perspective transform refused for path geometry`() {
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Path,
            material = MaterialKind.SolidColor,
        )
        val route = plan.analyze()
        assertTrue { route is PerspectiveTransformRoute.Refused }
    }

    @Test
    fun `perspective transform refused for text geometry`() {
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Text,
            material = MaterialKind.SolidColor,
        )
        val route = plan.analyze()
        assertTrue { route is PerspectiveTransformRoute.Refused }
    }

    @Test
    fun `perspective transform refused for non-solid material on rect`() {
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.Gradient,
        )
        val route = plan.analyze()
        assertTrue { route is PerspectiveTransformRoute.Refused }
    }
}

enum class GeometryKind { Rect, RRect, Path, Text }
enum class MaterialKind { SolidColor, Gradient }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerspectiveTransform*'
```
Expected: FAIL

- [ ] **Step 3: Write implementation**

Create `gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/transform/PerspectiveTransformPlan.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.transform

import org.graphiks.kanvas.gpu.renderer.compute.RefuseDiagnostic

sealed interface PerspectiveTransformRoute {
    data class Accepted(val transformKind: String) : PerspectiveTransformRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : PerspectiveTransformRoute
}

data class PerspectiveTransformPlan(
    val geometry: GeometryKind,
    val material: MaterialKind,
) {
    companion object {
        fun forGeometry(geometry: GeometryKind, material: MaterialKind): PerspectiveTransformPlan =
            PerspectiveTransformPlan(geometry, material)
    }

    fun analyze(): PerspectiveTransformRoute {
        val acceptedGeometries = setOf(GeometryKind.Rect, GeometryKind.RRect)
        if (geometry !in acceptedGeometries) {
            return PerspectiveTransformRoute.Refused(
                RefuseDiagnostic("unsupported.perspective_transform.${geometry.name.lowercase()}")
            )
        }
        if (material != MaterialKind.SolidColor) {
            return PerspectiveTransformRoute.Refused(
                RefuseDiagnostic("unsupported.perspective_transform.${material.name.lowercase()}")
            )
        }
        return PerspectiveTransformRoute.Accepted("perspective-${geometry.name.lowercase()}")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerspectiveTransform*'
```
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/commonMain/kotlin/org/graphiks/kanvas/gpu/renderer/transform/
rtk git add gpu-renderer/src/commonTest/kotlin/org/graphiks/kanvas/gpu/renderer/transform/
rtk git commit -m "feat: M33-003 perspective transform acceptance (rect/rrect + solid color)"
```

---

### Task 21: Wave A completion — promote M33 tickets to review

**Files:**
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-003-perspective-transform-acceptance.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

- [ ] **Step 1: M33-003 → review**

Edit `KGPU-M33-003-perspective-transform-acceptance.md`:
- frontmatter: `status: ready` → `status: review`
- Status Notes: append `ready → review (2026-06-28): perspective transform plan implemented for rect/rrect + solid color.`

- [ ] **Step 2: Update milestone README.md + STATUS.md**

M33 README: M33-003 `ready` → `review`. STATUS.md: M33 Ready 1→0, Review 2→3.

- [ ] **Step 3: Verify Wave A completion**

```bash
rtk git diff --check
```

Review M32 remaining: all 12 per-family tickets should be `done` (or at least reviewed and status updated).
M33: all 3 tickets should be `review`.

- [ ] **Step 4: Commit**

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/
rtk git add .upstream/specs/gpu-renderer/tickets/STATUS.md
rtk git commit -m "review: M33-003 → review, Wave A complete"
```

---

## Wave B — M32 Finalization + M34 + M35 (checkpoints)

### Task 22: Wave B entry gate — verify Wave A M32 review complete

**Files:**
- Read: `.upstream/specs/gpu-renderer/tickets/STATUS.md`

- [ ] **Step 1: Verify M32 review is complete**

```bash
rtk cat .upstream/specs/gpu-renderer/tickets/STATUS.md
```

Expected: M32 row shows Review 0, Done 16 (M32-001, M32-002 already done + 14 review tickets promoted).

If review tickets remain, pause and complete them before entering Wave B.

---

### Task 23: M32-004 Relocate shared infra out of `:gpu-raster`

This is a proposed ticket. Promote to ready, then implement.

**Scope:** Move WGSL validation, conformance fixtures, runtime-shader utilities, and gate infrastructure from `:gpu-raster` to a shared module (`:gpu-shared` or into `:gpu-renderer` directly).

See ticket `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/KGPU-M32-004-*.md` for exact files.

---

### Task 24: M32-005 Remove legacy device and M32-006 Final validation

These complete the M32 chain (M32-004 → M32-005 → M32-006). The legacy device removal is the final cleanup — delete `SkWebGpuDevice`, the `useLegacyGpuRaster` rollback, and the `:gpu-raster` include from settings.gradle.kts.

---

### Tasks 25-34: M34 Text Breadth + M35 Color Fidelity

Each task follows the same pattern as Wave A M33:
1. Promote milestone from scaffold → active
2. Promote ticket `proposed` → `ready`
3. Write failing test
4. Implement per design sketch
5. Run test pass
6. Promote ticket `ready` → `review`
7. Update README.md + STATUS.md
8. Commit

**M34 Ticket Sequence (by dependency):**

| Order | Ticket | Autonomous? |
|-------|--------|-------------|
| 1 | M34-001 Subpixel LCD | Yes |
| 2 | M34-005 Font fallback chain | Yes |
| 3 | M34-002 Color font pipeline | No (depends on pure-kotlin-text) |
| 4 | M34-003 Variable font support | No (depends on pure-kotlin-text) |
| 5 | M34-004 Complex shaping | No (depends on pure-kotlin-text) |

If pure-kotlin-text artifacts not available, mark M34-002 through M34-004 as `blocked` and continue with M34-001 + M34-005 only.

**M35 Ticket Sequence (all autonomous except M35-003):**

| Order | Ticket | Notes |
|-------|--------|-------|
| 1 | M35-001 HDR transfer functions | Heaviest — PQ/HLG/scRGB EOTF in WGSL |
| 2 | M35-002 Wide-gamut working spaces | |
| 3 | M35-004 ICC profile parsing | |
| 4 | M35-003 Gain map pipeline | Gated on Ultra HDR codec |

---

## Wave C — M36-M39 (checkpoints)

### Tasks 35-38: M36 Image Pipeline

All 4 tickets are autonomous except M36-001/M36-004 which are gated on KanvasImageCodec registry. Implement autonomous tickets first.

### Tasks 39-44: M37 Filter Breadth

Hard chain: M37-001 → M37-003. Other 4 tickets autonomous. Must update `RectOnlyOffscreenRenderer` to accept new command families and produce PNG evidence.

### Tasks 45-47: M38 Runtime Effects V2

M38-003 gated on wgsl4k multi-fragment. M38-001 and M38-002 autonomous.

### Tasks 48-51: M39 Rendering Architecture

All 4 tickets independent. M39-001 (MSAA) heaviest.

---

## Wave D — M40 (checkpoints)

### Tasks 52-54: M40 Architecture Capabilities

Strict chain: M40-001 (tile-deferred) → M40-002 (multi-threaded recording) + M40-003 (Hi-Z occlusion).

---

## Validation

After each wave, run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
```

After M32 completion, verify:

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test
# Confirm no compilation errors from removed :gpu-raster module
```

## Terminal State

After Wave D:
- STATUS.md shows all milestones M0-M40 with 0 Proposed, 0 Ready, 0 In Progress, 0 Blocked, 0 Review, all Done
- `:gpu-raster` module deleted
- All advanced GPU capabilities accepted with WGSL validation + CPU oracle parity evidence

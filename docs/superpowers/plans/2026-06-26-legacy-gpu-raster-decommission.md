# Legacy `gpu-raster` Decommission + Missing-Family Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This repo's evidence/ticket discipline (root `AGENTS.md`) OVERRIDES generic plan habits: never claim "done" without reference + CPU/GPU evidence or explicit refusal, diff/stat artifacts, route diagnostics, and a stable fallback policy.

**Goal:** Remove the legacy `:gpu-raster` GPU device (`SkWebGpuDevice` + rollback path) after every legacy route family is either ported to the Kanvas-native bridge with real pixel parity or formally refused with a stable diagnostic, and after the shared release-blocking infra hosted in `:gpu-raster` is relocated.

**Architecture:** Production rendering already routes `SkCanvas → KanvasSkiaBridge → SkiaKanvasSurface → kanvas Surface.renderToRgba() → GPU`. The legacy `gpu-raster` path (`SkWebGpuDevice`) is deprecated/frozen (KGPU-M30-004) and reachable only via `-Dkanvas.rollback.legacy-gpu-raster=true`. Decommissioning = (1) prove bridge↔legacy parity or refuse per family, (2) authorize each of the 12 `GpuRendererLegacyRouteFamily` rows through `GpuRendererLegacyRetirementGate` (KGPU-M10-003), (3) move shared WGSL/conformance/runtime-shader/gate assets out of `:gpu-raster`, (4) delete the legacy device + rollback branch + module include.

**Tech Stack:** Kotlin/JVM, Gradle (`rtk ./gradlew --no-daemon …`), WebGPU via wgpu4k, WGSL (wgsl4k), JUnit 5. GPU evidence runs via `JavaExec` tasks (`-XstartOnFirstThread` on macOS), not the JUnit JVM.

---

## Operating Rules (read before every task)

1. **Run gradle through `rtk`**: `rtk ./gradlew --no-daemon <task>`. Never start a daemon.
2. **Ticket discipline**: work is tracked as `.upstream/specs/gpu-renderer/tickets/` tickets using the section order in `tickets/README.md` (PM Note, Problem, Scope, Non-Goals, Spec Sources, Graphite Algorithm References, Design Sketch, Acceptance Criteria, Required Evidence, Fallback/Refusal Behavior, Dashboard Impact, Validation, Status Notes, Linear Labels). New tickets start `status: proposed`/`ready`, never `done`.
3. **No over-claim**: a family is "ported" only with committed pixel-diff evidence (similarity %, max channel delta, diff artifact) under `reports/gpu-renderer/`. Otherwise it is **refused** with a stable `refuse:<diagnosticName>:<reason>` diagnostic. Dependency-gated families (codecs, color/SDF text, fonts) per `AGENTS.md` are refused + linked to their dependency ticket; do NOT add short-lived substitutes.
4. **No silent fallback**: the bridge already refuses loudly (`Surface.kt` `refuse(...)`, `SkiaKanvasSurface.emitRefusedDiagnostics`). Preserve this.
5. **Each phase ends with a real commit**, `rtk git diff --check` clean, and an evidence report committed alongside code.
6. **Independent review gate**: tickets move `review → done` only after the per-ticket validation commands pass AND an independent reviewer accepts the evidence (use superpowers:requesting-code-review at phase boundaries).

---

## Grounded Current State (verify, do not re-derive)

- Module still included: `settings.gradle.kts:100` → `include(":gpu-raster")`.
- Legacy device + deps: `gpu-raster/build.gradle.kts:8` (`:gpu-raster depends on :kanvas-skia`); legacy classes in `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/` — `SkWebGpuDevice.kt`, `WebGpuContext.kt`, `HeadlessTarget.kt`, `WebGpuCoveragePlanSelector.kt`, `SkWebGpuGlyphAtlas.kt`.
- Rollback flag: `kanvas-skia-bridge/.../RollbackConfig.kt:4` (`kanvas.rollback.legacy-gpu-raster`); branch consumed in `SkiaKanvasSurface.kt:25` (`isKanvasRendererEnabled`) + `wrapIfEnabled` (`SkiaKanvasSurface.kt:156`).
- Bridge dispatch + refuse: `kanvas/.../Surface.kt` `renderToRgba()` (`Surface.kt:127`), `dispatchFillRect`/`dispatchFillRRect`/`dispatchFillPath`/`dispatchDrawTextRun` (~`Surface.kt:152,432`), refuse pattern `refuse("unsupported_material:…"|"unsupported_blend:…")`.
- **Text A8 already ported** (post-M31-005 report, #1890): `kanvas/.../TextRunDispatch.kt`, `kanvas/.../TextBlob.kt`, `kanvas/src/test/.../TextGpuEvidenceMain.kt` ("PASS real GPU A8 text pixels with CPU parity"). The M31-005 evidence marking DrawTextRun "refuse" is **stale**.
- Retirement gate (lives INSIDE the module to be deleted): `gpu-raster/.../GpuRendererLegacyRetirementGates.kt` + `GpuRendererShadowParityGates.kt` (defines the 12-value `GpuRendererLegacyRouteFamily` enum). Test: `gpu-raster/.../GpuRendererLegacyRetirementGateTest.kt`.
- Shared release-blocking infra hosted in `:gpu-raster` (must relocate before delete): `:gpu-raster:wgslValidateStrict`, `:gpu-raster:wgslValidateAll`, `:gpu-raster:pipelineConformanceTest` (root `build.gradle.kts:849-851`), generated WGSL tools (`tools/GeneratedSolidRectWgsl.kt`, `tools/GeneratedLinearGradientWgsl.kt`, `tools/Wgsl*ValidationReport.kt`), runtime-effect shaders `gpu-raster/src/main/resources/shaders/runtime_simple_rt.wgsl` (consumed by `kadre-runtime/build.gradle.kts:298`), `text_glyph_atlas.wgsl`, GPU inventory (`tools/GpuInventoryFailureReport.kt`).
- Parity harnesses: `kanvas-skia-bridge:compareBridgeVsSkiaRaster` (`kanvas-skia-bridge/build.gradle.kts:39`, main `CompareBridgeVsSkiaRasterKt`) compares bridge vs **Skia software raster**, NOT vs legacy `SkWebGpuDevice`. `gpu-renderer-scenes:compareKanvasSurfaceOffscreen` / `renderKanvasSurfaceOffscreen` (`gpu-renderer-scenes/build.gradle.kts:63,86`) are the per-family GPU evidence path.

### Family → capability matrix (port `P` vs refuse `R`)

| `familyId` | Default replacement ticket | Current Kanvas state | Decommission action |
|---|---|---|---|
| `solid-rect-drawpaint` | KGPU-M1-004 | FillRect 100% | retire-ready after Phase 1 parity |
| `material-paint` | KGPU-M11-009 | SolidColor only | `P` blends/material or `R` |
| `rounded-rect-gradients` | KGPU-M2-002 | FillRRect SolidColor 99.84%; gradients not dispatched | `P` rrect-gradient or `R` |
| `rect-rrect-stroke` | KGPU-M3-003 | stroke not dispatched | `P` stroke or `R` |
| `path-fill-stroke` | KGPU-M11-007 | FillPath fill 100%; path stroke not dispatched | `P` path-stroke or `R` |
| `device-scissor-simple-clips` | KGPU-M2-003 | WideOpen/DeviceRect only | `P` clip or `R` |
| `images-bitmap-codecs-uploads` | KGPU-M11-004 | DrawImage refused | `P` texture or `R` (codecs dependency-gated) |
| `savelayer-destination-read-filters` | KGPU-M11-006 | not dispatched | `R` (dependency-gated) |
| `text-glyphs` | KGPU-M6-002 | A8 done; color/SDF/emoji gaps | `P` bridge-wire A8 + parity; `R` color/SDF |
| `runtime-effects-color-blends` | KGPU-M11-008 | non-SRC_OVER refused | `P` blend modes or `R` |
| `vertices-points-meshes` | KGPU-M8-003 | not dispatched | `R` |
| `clear-discard-target-background` | (route-specific) | clear covered by surface init | verify `P` or `R` |

> Decision rule: prefer `R` (formal refusal) for any family whose port is dependency-gated or lacks accepted spec/evidence. Refusal is a valid decommission outcome — the legacy path is removed; the family becomes an explicit `refuse:` route on the bridge until a future accepted ticket ports it.

---

## Phase 0 — Scaffold M32 milestone + decision matrix

### Task 0.1: Create the M32 milestone directory and README

**Files:**
- Create: `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md`
- Modify: `.upstream/specs/gpu-renderer/tickets/README.md:123` (milestone table — add M32 row)

- [ ] **Step 1:** Read `tickets/templates/milestone-template.md` and `tickets/M31-production-activation/README.md` to copy the exact table schema (columns: ticket, status, priority, claim_impact, route_kind, product_activation, release_blocking, owner_area, depends_on, legacy_gate).
- [ ] **Step 2:** Create `M32-legacy-gpu-raster-decommission/README.md` with the milestone purpose ("Remove the legacy gpu-raster device after per-family port-or-refuse, retirement-gate authorization, and shared-infra relocation") and a ticket table seeded with the tickets defined in Phases 1–5 (`KGPU-M32-001 … KGPU-M32-0NN`), all `status: proposed`.
- [ ] **Step 3:** Add to `tickets/README.md` milestone table:

```markdown
| M32 | [M32-legacy-gpu-raster-decommission](M32-legacy-gpu-raster-decommission/README.md) | <count> | Remove legacy `gpu-raster` after per-family port-or-refuse, retirement-gate authorization, and shared-infra relocation. |
```

- [ ] **Step 4:** Commit.

```bash
rtk git add .upstream/specs/gpu-renderer/tickets/
rtk git commit -m "docs(gpu-renderer): add M32 legacy gpu-raster decommission milestone scaffold"
```

### Task 0.2: Produce the per-family decommission decision matrix report

**Files:**
- Create: `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md`

- [ ] **Step 1:** For each of the 12 `GpuRendererLegacyRouteFamily` values (`gpu-raster/.../GpuRendererShadowParityGates.kt:9-68`), record: current Kanvas dispatch status (grep `Surface.kt` for the corresponding `dispatchX`), decision (`port` or `refuse`), replacement ticket (`defaultReplacementTicket`), and required evidence. Use the matrix above as the starting point but verify each row against current code.
- [ ] **Step 2:** Commit the report. This report is the authority for which Phase-2 tickets are `port` vs `refuse`.

```bash
rtk git add reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md
rtk git commit -m "docs(gpu-renderer): M32-001 per-family decommission decision matrix"
```

---

## Phase 1 — Close KGPU-M31-005 (real bridge↔legacy parity or formal refusal) and unblock M30-003/M31-003/M31-001

This phase removes the over-claim flagged in `KGPU-M31-005` and replaces the "deferred legacy comparison" with either a real comparison or a documented refusal.

### Task 1.1: Add a real bridge↔legacy (`SkWebGpuDevice`) pixel comparison for the proven fill families

**Files:**
- Modify: `kanvas-skia-bridge/src/main/kotlin/org/skia/kanvas/CompareBridgeVsSkiaRaster*.kt` (the `CompareBridgeVsSkiaRasterKt` main) — add a legacy-device branch, OR create `kanvas-skia-bridge/src/main/kotlin/org/skia/kanvas/CompareBridgeVsLegacyGpuRaster.kt`
- Modify: `kanvas-skia-bridge/build.gradle.kts:39` — register a sibling `compareBridgeVsLegacyGpuRaster` `JavaExec` task (copy the `compareBridgeVsSkiaRaster` block, change `mainClass`)
- Create: `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`

- [ ] **Step 1:** Read `CompareBridgeVsSkiaRasterKt` (the existing main behind `compareBridgeVsSkiaRaster`) to reuse its scene + `TestUtils.compareBitmapsDetailed` pattern.
- [ ] **Step 2:** Implement the legacy reference: render the SAME scene through `SkWebGpuDevice` (set `-Dkanvas.rollback.legacy-gpu-raster=true` path / instantiate the legacy device directly from `:gpu-raster`) and decode to RGBA. Compare against the bridge output with the documented tolerance already used (rect tol=0; rrect AA threshold ≥99.5%).
- [ ] **Step 3:** Register the gradle task; run it on a GPU-capable runner:

```bash
rtk ./gradlew --no-daemon :kanvas-skia-bridge:compareBridgeVsLegacyGpuRaster
# Expected: PASS lines per family (Rect, RRect, Path) with similarity % + maxDiff
```

- [ ] **Step 4:** If GPU is unavailable in CI, mark the task GPU-gated (`assumeTrue`-style guard) and capture the local run transcript in the report. Commit report + code.

> If the real legacy comparison is infeasible (legacy device deprecated/unbuildable), STOP and instead execute Task 1.2's refusal path: document that bridge↔legacy parity is replaced by bridge↔Skia-raster + bridge↔independent-CPU evidence, and record the explicit decision in the M31-005 evidence. Do not leave the "deferred" wording.

### Task 1.2: Correct the over-claimed wording and mark unported families refused

**Files:**
- Modify: `reports/gpu-renderer/2026-06-25-M31-003-evidence.md` (replace "Task-level parity verified"/"parity verified for all 5 families" with "structural/task-level coverage + pixel parity for 3 fill families; image/blend refused")
- Modify: `.upstream/specs/gpu-renderer/tickets/M31-production-activation/KGPU-M31-005-bridge-pixel-parity.md` Status Notes (record real-parity-or-refusal outcome; update the stale "DrawTextRun remains refuse" line — A8 text now dispatched, see Phase 2 Task 2.7)

- [ ] **Step 1:** Update both files; ensure the family table reflects actual dispatch state verified against `Surface.kt`.
- [ ] **Step 2:** `rtk git diff --check` clean; commit.

### Task 1.3: Promote KGPU-M31-005 → done and unblock dependents

**Files:**
- Modify: `KGPU-M31-005-bridge-pixel-parity.md:4` (`status: in-progress` → `review`, then `done` after review)
- Modify: `M30-skia-wrapper-legacy-retirement/KGPU-M30-003-regression-parity-tests.md` and `M31-production-activation/KGPU-M31-003-pm-evidence-bundle.md`, `KGPU-M31-001-production-activation.md` (`review` → `done`)
- Modify: corresponding rows in `M30…/README.md` and `M31…/README.md` tables

- [ ] **Step 1:** Run validation:

```bash
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test
rtk ./gradlew --no-daemon :kanvas:test :gpu-renderer:test
```

- [ ] **Step 2:** Request independent review (superpowers:requesting-code-review). Only after acceptance flip statuses to `done`.
- [ ] **Step 3:** Commit.

---

## Phase 2 — Port-or-refuse each remaining family

Create one `KGPU-M32-0NN` ticket per family marked `port` or `refuse` in the Task 0.2 matrix. Each ticket follows the same shape. **Codeable refusals** (Tasks marked `R`) are small and concrete; **ports** (`P`) follow the existing `dispatchX` pattern in `Surface.kt` and require committed pixel-diff evidence.

### Pattern A — Formal refusal (use for every dependency-gated / unported family)

For a family `F` whose decision is `refuse`:

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/Surface.kt` (ensure the family's command branch in the `when` at `Surface.kt:152` emits `refuse("unsupported_<reason>")` — most already do)
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/<F>RefuseTest.kt`

- [ ] **Step 1: Write the failing hermetic test** asserting the refuse diagnostic:

```kotlin
@Test
fun `<F> command emits a stable refuse diagnostic`() {
    val surface = Surface(width = 64, height = 64)
    val canvas = Canvas(surface)
    // record an <F> command via canvas.draw...(...)
    val result = surface.renderToRgba()
    assertTrue(result.diagnostics.any { it.startsWith("refuse:") && it.contains("<expected_reason>") },
        "expected refuse diagnostic, got ${result.diagnostics}")
    assertEquals(0, result.dispatched)
}
```

- [ ] **Step 2:** `rtk ./gradlew --no-daemon :kanvas:test --tests "*<F>RefuseTest*"` → FAIL (or PASS if already refused; if PASS, skip to Step 4 and only add the regression test).
- [ ] **Step 3:** If not yet refused, add the `refuse("unsupported_<reason>")` branch following `Surface.kt:183/199` (`unsupported_material` / `unsupported_blend`).
- [ ] **Step 4:** `rtk ./gradlew --no-daemon :kanvas:test --tests "*<F>RefuseTest*"` → PASS.
- [ ] **Step 5:** Create `reports/gpu-renderer/2026-06-26-m32-<F>-refusal.md` recording: the diagnostic code, the dependency/spec ticket gating a future port, and the statement "legacy route removed; family served as refuse until <ticket>". Commit.

### Pattern B — Port (use for image texture, rrect-gradient, strokes, blend modes, clip — where a route is feasible now)

For a family `F` with decision `port`:

**Files:**
- Modify: `kanvas/.../Surface.kt` — add `dispatch<F>(cmd, dispatched, diagnostics)` following the `dispatchFillRRect` SDF pattern (`Surface.kt` ~rrect) or `dispatchFillPath` stencil-cover pattern; branch it in the `when` at `Surface.kt:152`
- Modify: `kanvas/.../NormalizedDrawCommand.kt` (`gpu-renderer/.../commands/NormalizedDrawCommand.kt`) if a new material/command kind is needed (mirror `GPUMaterialDescriptor.ImageDraw`)
- Modify: `gpu-renderer-scenes/.../RenderKanvasSurfaceOffscreenMain.kt` + `CompareKanvasSurfaceOffscreenMain.kt` — add the `<F>` scene + independent CPU reference
- Create: `reports/gpu-renderer/2026-06-26-m32-<F>-parity.md`

- [ ] **Step 1: Write the failing parity/dispatch unit test** in `kanvas/src/test/.../` asserting `result.dispatched == 1` and `result.refusedCount == 0` for the `<F>` scene (constraints documented).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement `dispatch<F>` (WGSL + uniform/vertex data path following the existing rrect/path dispatch). Keep unsupported sub-cases (e.g. non-uniform radii, non-SRC_OVER) refused.
- [ ] **Step 4:** Run unit test → PASS.
- [ ] **Step 5: GPU evidence** (GPU-capable runner):

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderKanvasSurfaceOffscreen -PsceneName=<F>-scene -PsceneOutput=reports/kanvas-surface-offscreen/<F>
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=<F>-scene -PsceneOutput=reports/kanvas-surface-offscreen/<F>/final
# Expected: PASS similarity ≥ documented threshold vs INDEPENDENT CPU reference (not a WGSL port of the same shader)
```

- [ ] **Step 6:** Commit code + `reports/gpu-renderer/2026-06-26-m32-<F>-parity.md` (similarity %, max channel delta, diff artifact path, tolerance rationale).

### Task 2.7 — Text family: wire bridge `drawTextBlob` to the existing A8 dispatch + parity

A8 text already lands in `kanvas` (`TextRunDispatch.kt`, proven by `TextGpuEvidenceMain.kt`). The remaining gap is the **bridge** route + parity; color/SDF/emoji are refused (dependency-gated).

**Files:**
- Modify: `kanvas-skia-bridge/.../KanvasSkiaBridge.kt` `drawTextBlob` — translate `SkTextBlob` → `Canvas.drawTextRun` with a real typeface/atlas (mirror `TextBlob.kt` lowering); refuse color/SDF runs with `refuse:text:unsupported_color_or_sdf`
- Modify: `gpu-renderer-scenes` scene mains — add a bridge text scene
- Create: `reports/gpu-renderer/2026-06-26-m32-text-bridge-parity.md`

- [ ] **Step 1:** Failing bridge test: a Latin text blob through the bridge yields `dispatched ≥ 1`, `refusedCount == 0`.
- [ ] **Step 2:** Run → FAIL. Implement the bridge translation. Run → PASS.
- [ ] **Step 3:** GPU parity vs independent CPU oracle (reuse the `TextRunDispatch` CPU oracle). Commit code + report.

> Output of Phase 2: every family is either (a) dispatched with committed independent pixel parity, or (b) refused with a stable diagnostic + a dependency/port ticket. `oldPathUsageCount` for each family on the production-default path is now **0** (no traffic reaches `SkWebGpuDevice`). Record per-family `oldPathUsageEvidenceId` proof (grep production routing for legacy device references = none) — required by the retirement gate.

---

## Phase 3 — Authorize each family through `GpuRendererLegacyRetirementGate` (KGPU-M10-003)

The gate (`gpu-raster/.../GpuRendererLegacyRetirementGates.kt:106`) requires, per family, a `GpuRendererLegacyRetirementEvidence` row that passes every diagnostic in `diagnosticsFor` (`:198-270`).

### Task 3.1: Build the 12-family retirement evidence set and prove `gatePassed = true`

**Files:**
- Modify/Create: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GpuRendererLegacyRetirementGateTest.kt` — add a test that feeds one accepted row per family and asserts `report.gatePassed` and `report.acceptedFamilyCount == GpuRendererLegacyRouteFamily.values().size`
- Create: `reports/gpu-renderer/2026-06-26-m32-003-legacy-retirement-authorization.md` (dump `report.dumpLines()`)

- [ ] **Step 1:** For each family, construct evidence satisfying ALL checks in `diagnosticsFor`:
  - `acceptedReplacementTicket == family.defaultReplacementTicket` and `replacementAccepted = true`
  - `activationDecisionId` non-blank, contains `:${family.familyId}:`, `activationDecisionAccepted = true`, unique across families
  - `shadowParityAccepted = true` (from KGPU-M10-002 gate)
  - `rollbackEvidenceId` family-scoped + unique; `rollbackValidationHash` starts `sha256:` (real hash of the rollback validation transcript)
  - `pmEvidenceRowId == "gpu-renderer.legacy-retirement.${family.familyId}"`
  - `oldPathUsageEvidenceId` family-scoped + unique; `oldPathUsageCount == 0`
  - `scopeLabel == "legacy.${family.familyId}.retirement"`
  - all of `archivedEvidenceOnly`, `genericMigrationGate`, `broadDeletion`, `productRouteActivated`, `releaseBlocking` = `false`; `readinessDelta == 0.0`
- [ ] **Step 2: Write the failing test** asserting `gatePassed`:

```kotlin
@Test
fun `all legacy families are retirement-authorized with family-scoped evidence`() {
    val report = GpuRendererLegacyRetirementGate.evaluate(allFamilyRetirementEvidence())
    assertEquals(
        GpuRendererLegacyRouteFamily.values().size,
        report.acceptedFamilyCount,
        report.dumpLines().joinToString("\n"),
    )
    assertTrue(report.gatePassed, report.dumpLines().joinToString("\n"))
}
```

- [ ] **Step 3:** Run → FAIL; fill evidence until green:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererLegacyRetirementGateTest
```

- [ ] **Step 4:** Capture `report.dumpLines()` into the report. Commit. This authorization is the precondition for Phase 5 deletion. (Note: the gate's `init` requires `legacyRouteActive = true` — authorization does NOT itself remove routes; Phase 5 does.)

---

## Phase 4 — Relocate shared release-blocking infra out of `:gpu-raster`

`:gpu-raster` cannot be deleted while it hosts release-gated WGSL/conformance/runtime-shader assets and the retirement/shadow gates. Move them to a neutral module (recommended: `:gpu-renderer`, which is the live engine and already a dependency of the bridge).

### Task 4.1: Move WGSL validation + generated WGSL + inventory tooling

**Files:**
- Move: `gpu-raster/.../tools/{WgslStrictValidationReport,WgslValidationReport,GeneratedSolidRectWgsl,GeneratedLinearGradientWgsl,RuntimeEffectsLayoutV2Report,GpuInventoryFailureReport,WgslParserSmokeMain}.kt` → `gpu-renderer/src/main/kotlin/.../tools/`
- Move: `gpu-raster/src/test/resources/wgsl-diagnostics-allowlist.txt` → `gpu-renderer/src/test/resources/`
- Modify: root `build.gradle.kts` — repoint `:gpu-raster:wgslValidateStrict|wgslValidateAll|pipelineConformanceTest` registrations and all `gpu-raster/...` `inputs.file(...)` paths (lines ~131-199, 408-409, 469-593, 691-892, 5148-5959, 8892-8897) to the new module
- Modify: `build.gradle.kts:3284,3443-3500` (`checkGpuRasterImageToolingNoAwt`, `projectsToCheck`)

- [ ] **Step 1:** Move files; update package declarations + imports.
- [ ] **Step 2:** Repoint every `gpu-raster/...` reference in root `build.gradle.kts` (grep `gpu-raster` in `*.kts` returned ~80 hits — fix each). Repoint the conformance registration `project(":gpu-raster").registerPipelineConformanceTest(` (`build.gradle.kts:610`).
- [ ] **Step 3:** Validate the relocated gates still run:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:wgslValidateStrict :gpu-renderer:wgslValidateAll :gpu-renderer:pipelineConformanceTest
```

- [ ] **Step 4:** Commit.

### Task 4.2: Move runtime-effect + text/glyph atlas shaders

**Files:**
- Move: `gpu-raster/src/main/resources/shaders/{runtime_simple_rt.wgsl,runtime_spiral_rt.wgsl,text_glyph_atlas.wgsl}` → `gpu-renderer/src/main/resources/shaders/`
- Modify: `kadre-runtime/build.gradle.kts:298` and root `build.gradle.kts:767-768,5718` shader input paths

- [ ] **Step 1:** Move shaders; repoint consumers. **Step 2:** `rtk ./gradlew --no-daemon :kadre-runtime:check` and the runtime-effect conformance tasks. **Step 3:** Commit.

### Task 4.3: Move the retirement + shadow-parity gates

**Files:**
- Move: `gpu-raster/.../GpuRendererLegacyRetirementGates.kt`, `GpuRendererShadowParityGates.kt`, `GpuRendererLegacyRetirementGateTest.kt`, and the M10-002 shadow gate test → `gpu-renderer/src/main/kotlin/.../legacy/` + `src/test/...`
- Modify: imports/package; KGPU-M10-002/003 ticket Validation commands (`:gpu-raster:test` → `:gpu-renderer:test`)

- [ ] **Step 1:** Move; fix `internal` visibility (gates are `internal` — keep within the new module or relax to the test source set). **Step 2:**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GpuRenderer*Gate*'
```

- [ ] **Step 3:** Commit.

### Task 4.4: Migrate/triage remaining `:gpu-raster` GM/conformance tests

**Files:** `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/**` (~140 tests; see `glob gpu-raster/**/*.kt`)

- [ ] **Step 1:** Classify each test: (a) validates the legacy device → delete with the device in Phase 5; (b) validates WGSL/coverage-selector/shared behavior → move to `:gpu-renderer`. Record the classification in `reports/gpu-renderer/2026-06-26-m32-004-test-migration.md`.
- [ ] **Step 2:** Move category (b); leave category (a) for Phase 5. **Step 3:** `rtk ./gradlew --no-daemon :gpu-renderer:test`. **Step 4:** Commit + report.

---

## Phase 5 — Remove the legacy device, rollback branch, and module

Only after Phases 1–4 are green and the retirement gate report shows `gatePassed = true`.

### Task 5.1: Remove the rollback-to-legacy branch (keep the bridge default-on)

**Files:**
- Modify: `kanvas-skia-bridge/.../RollbackConfig.kt` — remove `useLegacyGpuRaster` + `SYSTEM_PROPERTY`
- Modify: `kanvas-skia-bridge/.../SkiaKanvasSurface.kt:25-26,156-161` — `isKanvasRendererEnabled()` becomes unconditional `true`; `wrapIfEnabled` always wraps (or inline)
- Modify: `kanvas-skia-bridge/.../KanvasSkiaBridgeTest.kt:222-253,417-419` — remove the legacy-rollback tests
- Modify: `KGPU-M31-002-rollback-flag.md` — add a `superseded`/closeout note (rollback target changed: no legacy device to roll back to)

- [ ] **Step 1: Update the test first** — delete the rollback tests, then make `isKanvasRendererEnabled` unconditional. **Step 2:**

```bash
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test
```

- [ ] **Step 3:** Commit.

### Task 5.2: Delete the legacy device source + its device-only tests

**Files:**
- Delete: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/{SkWebGpuDevice,WebGpuContext,HeadlessTarget,WebGpuCoveragePlanSelector,SkWebGpuGlyphAtlas,GpuRendererFirstRoute*,GpuRendererShadowAdapter}.kt` (everything not relocated in Phase 4)
- Delete: `gpu-raster/src/test/...` category-(a) tests from Task 4.4
- Delete: `gpu-raster/build.gradle.kts`, `gpu-raster/module.md`, `gpu-raster/README.md`, `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/module.md`
- Modify: `settings.gradle.kts:95-100` — remove the comment block + `include(":gpu-raster")`

- [ ] **Step 1:** Grep for any remaining consumer of the deleted classes before deletion:

```bash
rtk rg -n "SkWebGpuDevice|WebGpuContext\\b|HeadlessTarget|WebGpuCoveragePlanSelector|org\\.skia\\.gpu\\.webgpu" --glob '!gpu-raster/**' --glob '*.kt' --glob '*.kts'
# Expected after Phase 4: only references inside :gpu-raster itself (about to be deleted)
```

- [ ] **Step 2:** Delete files + remove the `include`. **Step 3:** Full build:

```bash
rtk ./gradlew --no-daemon build
```

Expected: configuration succeeds with no `:gpu-raster` project; all relocated gates/tasks resolve.

- [ ] **Step 4:** Commit.

### Task 5.3: Update specs, routing policy, docs, and dashboard

**Files:**
- Modify: `.upstream/specs/gpu-renderer/05-routing-policy.md` — remove the FROZEN `gpu-raster-legacy-path` row (route no longer exists)
- Modify: `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md` — mark retirement complete
- Modify: `README.md:517,521` (deprecation row + activation instructions referencing legacy gpu-raster)
- Modify: `docs/superpowers/specs/2026-06-25-kanvas-native-api-design.md:39,48-49` (legacy-path references)
- Modify: `.upstream/specs/gpu-renderer/tickets/STATUS.md` + M30/M31/M32 README tables

- [ ] **Step 1:** Update each doc; ensure no doc claims a live `gpu-raster` route. **Step 2:** `rtk git diff --check`. **Step 3:** Commit.

---

## Phase 6 — Final validation, evidence bundle, and PR

### Task 6.1: Full release-gate validation

- [ ] **Step 1:** Run the aggregate gates that previously depended on `:gpu-raster` (now relocated):

```bash
rtk ./gradlew --no-daemon :gpu-renderer:wgslValidateStrict :gpu-renderer:wgslValidateAll :gpu-renderer:pipelineConformanceTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GpuRenderer*Gate*'
rtk ./gradlew --no-daemon :kanvas:test :gpu-renderer:test :kanvas-skia-bridge:test :kadre-runtime:check
rtk ./gradlew --no-daemon build
rtk git diff --check
```

- [ ] **Step 2:** GPU evidence runs (GPU-capable runner) for every ported family + the bridge↔legacy/Skia parity from Phase 1.

### Task 6.2: Decommission evidence bundle + ticket closeout

**Files:**
- Create: `reports/gpu-renderer/2026-06-26-m32-decommission-evidence-bundle.md` (links all per-phase reports, the retirement-gate dump, the no-remaining-consumer grep, the full-build transcript)
- Modify: M32 ticket statuses `review → done` (after independent review only)

- [ ] **Step 1:** Assemble the bundle. **Step 2:** Request independent review (superpowers:requesting-code-review). **Step 3:** Flip statuses on acceptance. **Step 4:** Commit.

### Task 6.3: Open the PR

- [ ] **Step 1:** Use the gh-pr-creator skill. PR body must include: the decision matrix, per-family port/refuse outcome, the retirement-gate `gatePassed=true` dump, the relocation summary, and the full-build transcript. Link M32 tickets.

---

## Self-Review Checklist (run before handing off)

1. **Spec coverage:** every `GpuRendererLegacyRouteFamily` (12) has a Phase-2 port-or-refuse task and a Phase-3 retirement-evidence row; every `:gpu-raster` shared asset (WGSL validation, generated WGSL, runtime/text shaders, inventory, gates) has a Phase-4 relocation task; legacy device + rollback + include have Phase-5 deletion tasks.
2. **Placeholder scan:** feature ports use the real `dispatchX`/`refuse(...)` pattern and committed pixel-diff evidence — no fabricated shader code is asserted as "done"; dependency-gated families are explicit refusals, not substitutes (per `AGENTS.md`).
3. **Consistency:** retirement evidence field names match `GpuRendererLegacyRetirementEvidence` (`gpu-raster/.../GpuRendererLegacyRetirementGates.kt:4-23`); `pmEvidenceRowId`/`scopeLabel`/`oldPathUsageCount==0`/`sha256:` exactly match `diagnosticsFor` (`:198-270`).

## Known Hard Gates / Decision Points

- **Real legacy comparison feasibility (Task 1.1):** if `SkWebGpuDevice` can no longer be instantiated for comparison, Phase 1 downgrades to documented refusal of the bridge↔legacy comparison (bridge↔Skia-raster + independent CPU stay as the parity evidence). Decide explicitly; do not leave "deferred".
- **Dependency-gated families:** `images-bitmap-codecs-uploads` (codecs), `savelayer-destination-read-filters`, color/SDF/emoji `text-glyphs`, `vertices-points-meshes` are likely `refuse` now. Removing the legacy device for these means they become bridge `refuse:` routes — acceptable per the decommission goal, but the PM/dashboard must show them as refused, not "supported".

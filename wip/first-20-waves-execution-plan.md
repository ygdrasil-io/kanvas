# First 20 Skia GM renderer waves implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` to execute one task at a time.
> Each task is one stacked PR and uses checkbox (`- [ ]`) tracking.

**Goal:** Implement and prove the first 20 waves of the non-font Skia GM
renderer roadmap, preserving stable refusals and dependency gates.

**Architecture:** Work only through Kanvas public `Surface`, with CPU oracle
and headless WebGPU evidence. Each task owns one route, one PR, one report and
one promotion set; the next task starts from the previous task's branch.

**Tech Stack:** Kotlin Multiplatform, Gradle, WebGPU/wgpu4k, WGSL, PipelineIR,
Skia GM tests and GPU evidence v2.

**Spec:** `wip/complete-renderer-feature-plan.md` and the architecture targets
listed there.

## Global constraints

- Code, executed tests and verified artifacts are the source of truth; WIP
  Markdown never proves support.
- Reports and evidence go under `reports/gpu-renderer/evidence/`.
- Do not touch `gpu-renderer-scenes`, Ganesh, Graphite, dynamic SkSL or native
  windowing.
- Use WebGPU production routes and public `Surface`, never harness-only layers.
- No silent CPU fallback; invalid or unsupported inputs refuse before partial
  submission with stable diagnostics.
- Fonts and missing codecs remain dependency-gated.
- Every supported route needs CPU oracle or reference, GPU capture, diff/stats,
  route diagnostics and a negative contract test.
- One task equals one PR. Do not merge these PRs during this run.

## Task map

### Task 1: W00 — GM truth inventory

**Files:** `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistry.kt`, `SkiaGmRunner.kt`, `SkiaGmRenderer.kt`, `integration-tests/skia/src/test/resources/test-similarity-scores.properties`, `reports/gpu-renderer/evidence/gm-inventory/`.

**Deliverable:** Generate a source-derived machine-readable inventory mapping
every registered GM to family, reference/render availability, score,
operation count and first route/terminal diagnostic; reject duplicate or
orphan score rows; add deterministic regeneration tests.

**Tests:** `:integration-tests:skia:test` plus the new inventory test.

### Task 2: W01 — evidence catalogue convergence

**Files:** `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt`, `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/KanvasSurfaceProgram.kt`, `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/KanvasSurfaceEvidenceExecutor.kt`, `integration-tests/gpu-evidence/src/test/`, `reports/gpu-renderer/evidence/`.

**Deliverable:** Reconcile standalone evidence with catalogue v2; enforce unique
  IDs, public Surface route, oracle/refusal consistency, bundle hashes and
  exact SHA/adapter metadata.

**Tests:** `:integration-tests:gpu-evidence:test` and
`:integration-tests:gpu-evidence:verifyPromotedGpuEvidence`.

### Task 3: W10 — Canvas state

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `ClipStack.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Prove save/restore/restoreToCount, queries, clip bounds,
  quickReject and state isolation with post-restore sentinels; preserve stable
  refusals for invalid stack operations.

**Tests:** `:kanvas:test`, `:gpu-renderer:test`, targeted CanvasState evidence.

### Task 4: W11 — affine transforms

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support translation, uniform/non-uniform scale, rotation,
  skew, concat, set/reset matrix for bounded primitives and clips; reject
  non-finite, singular and general perspective matrices before submission.

**Tests:** `:kanvas:test`, `:gpu-renderer:test`, targeted Transform evidence.

### Task 5: W12 — basic primitives

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Prove drawColor, clear, points, rect, RRect, DRRect,
  annotation and snapshot for valid/empty/out-of-bounds/alpha inputs with
  independent CPU oracles.

**Tests:** `:kanvas:test`, `:gpu-renderer:test`, targeted Rect/Primitive evidence.

### Task 6: W20 — path curves

**Files:** `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/`, `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/`, `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/`.

**Deliverable:** Implement bounded quadratic, cubic, conic, oval and circle
  fill lowering with deterministic segment/fan/memory budgets and refusal over
  budget.

**Tests:** `:gpu-renderer:test`, path oracle tests, targeted Surface path GMs.

### Task 7: W21 — path topology

**Files:** `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support multiple contours, winding/even-odd/inverse,
  bounded self-intersections and reflected transforms with exact topology;
  refuse non-deterministic or over-budget topology.

**Tests:** geometry topology tests, independent path oracle tests and Surface GM
  evidence.

### Task 8: W22 — anti-aliased coverage

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt`, `GPUClipCoveragePlanner.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/`, `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/`.

**Deliverable:** Add deterministic fractional-edge coverage for rect, RRect,
  path, small primitives and overlaps under affine transform/clip; refuse
  unsupported sample/format combinations.

**Tests:** coverage math tests, targeted AA evidence and regression refusals.

### Task 9: W23 — clip shapes

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipMapper.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support transformed clipRRect/clipPath consumed by rect, RRect
  and path routes, with scissor/analytic/stencil/intermediate diagnostics and
  exact bounds lifetime.

**Tests:** clip contract, clip oracle and Surface clip GMs.

### Task 10: W24 — clip composition

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/ClipStack.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoveragePlanner.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support intersect, difference, inverse and nested clips with
  deterministic depth/edge/intermediate budgets; refuse before draw on budget
  overflow or invalid composition.

**Tests:** clip stack/unit tests, nested Surface evidence and refusal tests.

### Task 11: W25 — stroke geometry

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUStroke.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/StrokeSnippet.kt`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support rect/RRect/path strokes with butt/round/square caps,
  miter/round/bevel joins, valid widths, hairline policy, affine transforms,
  clip, gradient and AA; refuse expansion over budget.

**Tests:** stroke lowerer tests, Surface stroke oracle/evidence and negative
  width/cap/join tests.

### Task 12: W26 — path effects

**Files:** `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/stroke/PathEffectChain.kt`, `AdvancedStrokePlan.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/PathEffect.kt`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Implement bounded Dash, Corner and Trim; implement or expose
  stable refusals for Discrete, Path1D and Path2D with phase/style validation.

**Tests:** effect chain tests, stroke/path Surface evidence and invalid-parameter
  refusal tests.

### Task 13: W30 — gradient stops and tile modes

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/LinearGradientMaterialLowering.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GradientTileSnippet.kt`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support 1/2/3/4/8 stops, hard stops and all tile modes with
  validation for positions, non-finite values and budget; preserve stable
  refusal when exactness is not available.

**Tests:** gradient oracle and WGSL tests, Surface gradient evidence and refusal
  tests.

### Task 14: W31 — gradient families

**Files:** `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/LinearGradientMaterialLowering.kt`, `RadialGradientMaterialLowering.kt`, `SweepGradientMaterialLowering.kt`, `ConicalGradientMaterialLowering.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Close linear, radial, sweep and conical gradients including
  degenerate centers/radii/angles and interpolation/premultiplication rules.

**Tests:** material/WGSL tests, gradient CPU oracle and representative GMs.

### Task 15: W32 — transformed gradients

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support CTM/local matrix combinations, color interpolation
  spaces SRGB/LINEAR/OKLAB/HSL/OKLCH, gradients under clip and stroke;
  refuse singular matrices and unsupported wrapper combinations.

**Tests:** transform/color-space oracle tests and Surface gradient interactions.

### Task 16: W33 — blend modes

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/BlendMode.kt`, `Blender.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support Porter-Duff then advanced modes with premultiplied
  alpha, destination-read/MSAA/layer policies and arithmetic blender validation.

**Tests:** blend CPU oracle, WGSL tests, destination-read route evidence and
  incompatible-format refusals.

### Task 17: W34 — color filters

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUGradientColorFilter.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support Matrix, Blend, Compose, Table, Lighting, transfer
  functions, HSLAMatrix, Lerp, HighContrast, Luma and Overdraw with exact
  premul/color-space semantics and invalid-value refusals.

**Tests:** filter oracle, WGSL tests and filter-on-solid/gradient/image/layer
  evidence.

### Task 18: W35 — shader composition

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support Blend, local-matrix, color-filter, working-space and
  coord-clamp wrappers; add deterministic Perlin/Fractal noise or stable
  refusals for unsupported parameters and cycles.

**Tests:** composition oracle, shader ABI/WGSL tests and wrapper interaction GMs.

### Task 19: W40 — image sampling

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/SamplingOptions.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLowering.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/BitmapShaderSnippet.kt`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Support NEAREST, LINEAR and bounded Cubic sampling at centers,
  half-pixels, edges, crop, scale, rotation and clip; define mipmap and missing
  sampler refusals.

**Tests:** image CPU oracle, sampler/WGSL tests and representative image GMs.

### Task 20: W41 — image formats and uploads

**Files:** `kanvas/src/main/kotlin/org/graphiks/kanvas/image/`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/`, `integration-tests/gpu-evidence/src/test/`.

**Deliverable:** Validate and support every raw materializable format, row
  stride, padding, sub-rect upload, alpha/premul/color conversion and resource
  identity. Keep unavailable codec formats dependency-gated.

**Tests:** image upload contract, format/oracle tests, evidence for each raw
  format and codec gate tests.

## Per-task completion

- [ ] Implementer writes a report containing changed files, tests, output and
      concerns under `.superpowers/sdd/first-20-waves-execution-plan/`.
- [ ] A separate reviewer checks spec compliance and code quality.
- [ ] All Critical/Important findings are fixed and re-reviewed before the next
      task.
- [ ] The controller records the commit range and PR base/head in the ledger.

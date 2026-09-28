# W7 standalone rect/path stroke routing implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Render bounded mixed solid rect/path frames, including AA and zero-width hairlines, through the existing W4d geometry and graph authority.

**Architecture:** Project rectangle geometry to an exact math path inside W4dGeneralPathPlanCompiler while preserving the original DrawNode and canonical identity. Add a bounded standalone admission reason for AA, stroked rectangles and hairlines; retain historical routes and the existing math stroke and hard/AA graph builders. Do not adapt DrawOrigin or bypass legacy guards.

**Tech Stack:** Kotlin, :math, semantic IR, GPU plan, native WebGPU/Metal public Surface tests.

**Spec:** This document's architecture and constraints, under the delegated pilotage in [pilotage.md](pilotage.md). Astra independently reviewed this strategy before implementation.

## Global constraints

- Fonts, codecs and quarantined jpg-color-cube remain outside the current corpus scope.
- No changes to references, thresholds, GM ports, budgets or infrastructure tests.
- Geometry authority remains :math; new numeric geometry types, if any, use I/F32/64 nomenclature.
- Extend only standalone solid SrcOver sRGB rect/path frames, FILL/STROKE, no shader, filter or path effect, and empty or integral hard rectangular clip.
- Existing transformed/material/source routes retain their contracts; W5a/W6 and legacy GPUOpMapper are not widened.
- Keep original material, transform, clip, command index and identity. Do not synthesize PATH provenance for RECT.

## Review focus

- Hairline width stays one device pixel under scaling: public scaled ring witness.
- AA does not silently become hard-edge: fractional-edge witness with partial coverage.
- Hard and AA draws preserve ordering and alpha: independent literal overlap witness.
- New route must not absorb unsupported shader/filter or clip cases: existing public boundary regressions plus one scoped refusal if not covered.
- Existing native source consumers must not accidentally inherit rectangle admission: limit geometry projection to the standalone extension and run W6/W7 layer regressions.

### Task 1: Admit bounded standalone rect/path frames

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt` (explicit standalone opt-in; default compiler/source consumers unchanged).
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` (recognize the sealed W4d resolve-only logical target and already-owned physical attachments).
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUW5aGeometryHostTemplateV1.kt` (reuse the existing authenticated W4d mask-consumer 4x mapping, preserving W5h validation).
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt` and a focused helper if needed (validate the existing W4d MSAA contract separately from the generic resolve-per-pass contract).
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt` and `passes/GPUPlanW4dGeneralPreparedAuthority.kt` (white hard-mask coverage, independent of material color; reuse the existing D24-compatible direct-color pipeline when the graph attaches D24).
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7StrokeRoutingSurfacePixelTest.kt`.
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt` (replace the two stale W4d AA capability-refusal expectations with positive pixels on a capable backend and exact capability handling otherwise).

**Interfaces:** Existing `select(SceneSnapshot, RenderTargetDescriptor)`, `classifyDrawScope(DrawNode)`, and `preparePathStrokeGeometryF32`. A named public compiler factory is required across the plan/renderer module boundary; no new public Surface API or source contract.

- [x] Write public Surface witnesses using independent expected pixels declared before Surface: 8x8 blue background; red stroked rectangle/path with centerline (2.5,2.5)..(5.5,5.5), width 0/1, AA false/true. The device ring occupies [2,6) square minus [3,5) square. Test hairline with 2x CTM using local coordinates halved. Add fractional-edge AA and ordered alpha overlap witnesses.
- [x] Run the new suite before production changes; preserve exact failing diagnostics. Use static AfterAll GPU disposal as existing W7 suite does.
- [x] Implement exact Rect-to-path geometry projection without modifying DrawNode; admit the bounded full-frame domain only when AA, rect stroke or hairline requires it. Keep existing transformed admissions and W6/W4e source scopes unchanged. Validate finite rectangle coordinates before constructing the path.
- [x] Fix the causally exposed native compatibility guard: W4d AA resolves into the logical scene target without rendering directly to it. Recognize only the exact common sealed W4d authority, ordered pass/target bindings and logical readback/resolve identity. Keep physical target refs, downstream preflight and single-owner native allocations; do not duplicate transient preparations or bypass the general guard.
- [x] Reuse the W4d-specific mask-consumer mapping in its authenticated material template. Validate W4d's physical MSAA attachment, retained passes and final-only canonical resolve against existing sealed semantic/native facts, preserving the generic contract and D24 validation. Include a public direct-triangle AA witness because its conservative D24 attachment has no stencil access.
- [x] Run the new suite and relevant legacy refusal/public W6/W7 regressions sequentially on GPU. Expected: literal pixels and native Render/Readback evidence, no process exit 133.
- [x] Independent code review, correct findings, rerun affected tests, commit (`718445e6ef366dbc63ab213b0f4eb301c574af12`).

**Observed RED chain:** The first public witnesses refused `unsupported.stroke.rect_subpixel_first_slice`, `unsupported.stroke.rect_transform` and `unsupported.stroke.rect_anti_alias`. Once admitted, the root AA graph exposed resolve-only target rejection, missing 4x mask-consumer mapping and generic per-pass resolve validation. Actual pixels then exposed transparent hard-mask producers, and direct AA exposed a native D24 attachment/pipeline mismatch. AA PATH strokes also selected the deferred W5b hard-only source topology: only the already-proven standalone AA solid domain now uses direct normalization with retained original source authority. No public pixel oracle was relaxed to accommodate these renderer defects.

**Regression exception:** The expanded 69-test run passed 68 tests and failed the existing `W4d negative dash phase matches an independent source-arclength oracle` with `w5b.geometry.incompatible-plan: Required value was null.` A controlled rerun with the root compiler restored to its historical default constructor produces the identical error before native execution. Dash is excluded from the new standalone domain; the failure remains tracked, not disabled or reclassified as a passing test.

The static diagnosis identifies `PathStrokeStyleF64.snapshot()` recreating an immutable `PathStrokeDashF64` without structural equality; the W5b scratch seal then compares the copied styles using data-class equality. The dash object identity differs, the seal fails, and `requireNotNull` surfaces the observed diagnostic. Those files are unchanged from the parent commit. This A/B is evidence for the historical route, not a claimed full rebuild of the historical commit; a separate public dash repair is needed.

**Final targeted validation:** After restoring the standalone factory, the three complete W6/W7 suites (40 + 9 + 6 tests) plus `GPUPlanSurfacePixelTest.W4dGeneral*` and `GPUPlanSurfacePixelTest.W4e public hard*` (14 tests) pass: **69/69, Gradle exit 0**. This selector set differs from the expanded run above and does not include its failing dash test. Independent Sol review found no confirmed production defect; no global test-suite success is claimed.

### Task 2: Measure and publish

**Files:** Existing `SkiaGmParityCheckpoint.kt` and `summarize-parity.mjs` are used unchanged; update `pilotage.md`/`status.md` and add a measured corpus snapshot.

**Interfaces:** Same 631 registered identities, 443 current eligible identities, 30-second per-GM timeout; compare against `baseline-d661f10c3.json`.

- [x] Run the complete checkpoint with a fresh directory and committed renderer SHA. Resume after timed-out GM; no exclusions added.
- [x] Compare render/refusal/timeout states and image hashes of all 123 previously rendered GMs; separately report new renders and scores, regressions and known port-fidelity limits.
- [x] Document results and remaining gap, push branch and create a stacked PR on `codex/w7-parity-pilot`; attach it to the chat: [draft #2412](https://github.com/ygdrasil-io/kanvas/pull/2412).

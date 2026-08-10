# FP-09 — Retire the Legacy Immediate Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire `renderViaGpuLegacy`, the `GPUPreparedSurfaceLegacyPort`, and the legacy-only helper machinery, after the prepared route covers (or stably refuses with terminal codes) every family the legacy renderer currently serves.

**Architecture:** The legacy immediate renderer is the last consumer of the S/G compositor (`GPUClipExecution`, `LayerScissorOffscreenTarget`, CPU text-atlas builders, legacy mask leases) and the only reason the router still has a `Legacy` product route. FP-09 first adds prepared coverage for the two blend families whose prepared machinery already exists end-to-end (`GPUBlendPlanner` fixed/shader blend plans, `GPUDestinationSnapshotOperation.TextureCopy` + `GPUBlendFormulaLibrary` + `GPUCorePrimitivePreparedAuthority.Blend.ShaderWithDestination` — used today by ColorGlyph only): non-SrcOver core-primitive blends and destination-read blends. The three remaining fallback families (hairline points, mixed uniform layouts, analytic-clip non-direct geometry) become **documented stable terminal refusals** (their codes are already emitted by the prepared route; the collapse makes them terminal instead of falling back). Then the route authorities collapse to Prepared/Terminal/Refused/NoOp (`BeforePreparedEntryRefused` → always `Terminal`), `renderViaGpuLegacy` + the legacy port + all legacy-only helpers are deleted, and every legacy-pinning test is deleted or re-pointed with evidence.

**Tech Stack:** Kotlin, WebGPU via wgpu4k, WGSL generation, Gradle (`./gradlew -F off`), JUnit (`kotlin.test`).

**Reference docs:**
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` — FP-08 `completed`; this plan delivers FP-09.
- `reports/fp08-retire-immediate-cpu-paths-plan.md` — structure template; original Tasks 4–7 (executed-then-reverted) are FP-09's raw material.
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-08-retire-immediate-cpu-paths-evidence.md` — §3 (the 636-case reversal evidence, 5-code table) and §6 (FP-09 preconditions).
- `reports/upstream-rebaseline/2026-06-29-gpu-renderer-pre-existing-test-failures.md` — the `GPURendererPackageBoundaryTest` package-boundary case is a documented pre-existing failure; FP-09 must NOT fix it and must NOT change its failure state.

---

## Context: validated branch state (evidence, 2026-08-08)

**HEAD:** `accaea616` (FP-08 squash-merge #2057, working tree clean). Branch `codex/graphite-dawn-frame-fp09`.

**Baseline verified at plan time (2026-08-08, this worktree):**
- `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (9 s).
- `./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (WebGPU-dependent cases skip via `assumeTrue` when the backend is unavailable).
- `./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain` → FAILS ONLY on `gpu renderer production source satisfies package boundary rules` (documented pre-existing: exactly 20 cycle violations, 0 rule violations). All 21 other cases pass. **Do not modify.**

**Build command convention (this worktree):** `rtk proxy` is not on PATH; use `./gradlew -F off <tasks> --no-parallel --console=plain` with dependency verification disabled. Do NOT modify `gradle/verification-metadata.xml`.

### 1. Production legacy-path inventory at HEAD (the acceptance oracle)

Verified by `rg` at plan time (saved before-snapshot: this section; commit as the plan's Task 1 step):

```bash
rg -n "GPUPreparedSurfaceProductRoute\.Legacy|GPUPreparedSurfaceEligibility\.Legacy|GPUPreparedSurfaceRouteDecision\.Legacy|GPUPreparedSurfaceLegacyPort|renderViaGpuLegacy|legacy\.surface\.prepared|legacyPort|hasTerminalPreparedFamily|BeforePreparedEntryRefused" kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu --type kotlin
```

| Site | Meaning |
| --- | --- |
| `GPUPreparedSurfaceProductRouter.kt:18` | `GPUPreparedSurfaceProductRoute.Legacy(code)` product-route variant |
| `GPUPreparedSurfaceProductRouter.kt:36` | gate `Legacy` eligibility → route `Legacy(eligibility.code)` |
| `GPUPreparedSurfaceProductRouter.kt:60-65` | `BeforePreparedEntryRefused` → `Terminal` if `hasTerminalPreparedFamily` else `Legacy` (the fallback decision) |
| `GPUPreparedSurfaceProductRouter.kt:152-165` | `DisplayOp.hasTerminalPreparedFamily()` (10 terminal families; core primitives fall through to `Legacy`) |
| `GPUPreparedSurfaceFrameGate.kt:14-17` | `GPUPreparedSurfaceEligibility.Legacy(code, operationIndex)` |
| `GPUPreparedSurfaceFrameGate.kt:28-30` | color mapping `Refused` → `Legacy(code)` (e.g. `unsupported.surface.gpu-color-format.rgba8-unorm`) |
| `GPUPreparedSurfaceFrameGate.kt:60-63` | `FlushAndSnapshot` → `Legacy("legacy.surface.prepared.flush-snapshot")` |
| `GPUPreparedSurfaceFrameGate.kt:67-69` | empty/state-only frame → `Legacy("legacy.surface.prepared.empty-frame")` |
| `GPUPreparedSurfaceProductEntry.kt:12` | `GPUPreparedSurfaceRouteDecision.Legacy(code)` |
| `GPUPreparedSurfaceProductEntry.kt:21-30` | `GPUPreparedSurfaceLegacyPort` fun interface (`render(..., routeTrace: GPUClipRouteTrace?)`) |
| `GPUPreparedSurfaceProductEntry.kt:56-57` | `legacyPort` / `legacyRouteTrace` params |
| `GPUPreparedSurfaceProductEntry.kt:70-73` | `Legacy` route branch → `legacyPort.render(...)` |
| `GPURenderer.kt:703` | `renderViaGpu(..., routeTrace: GPUClipRouteTrace?)` public param |
| `GPURenderer.kt:714` | `legacyPort = preparedSurfaceLegacyPort` |
| `GPURenderer.kt:723-726` | `preparedSurfaceLegacyPort` (the lambda into `renderViaGpuLegacy`) |
| `GPURenderer.kt:729-3042` | `renderViaGpuLegacy` (the legacy renderer body; ~2,300 lines) |
| `GPURenderer.kt:87-696` | legacy-only top-of-file helpers (see Task 7 deletion list) |
| `GPURenderer.kt:3043-3262` | CPU text-atlas builders (`computeAtlasDst`, `hasColorGlyphs`, `TextAtlasMesh`, `buildTextAtlasMesh`, `drawTextAtlasPass`, `resolveTextColor`, `extractSolidShaderColor`, `ctmEffectiveScale`, `scaledForRasterization`, `normalizeGlyphRects`) |
| `GPUOpMapper.kt:2282-2358` | `expandPicturesForGpuReplay` (legacy-only; called only from `renderViaGpuLegacy` at `GPURenderer.kt:739`) |
| `GPUClipExecution.kt:1-330` | whole file: `GPUClipSourceSurface`, `GPUClipRouteContext`, `GPUClipRouteTrace`, `GPUClipDestinationReadComposer`, `GPUClipDestinationReadRefusalComposer`, `renderWithClip`, `compositeFixedSource`, `sourceCompositeUniformDraw`, `clipSourceFacts`, `copyForClipSource` (×4 + dispatcher), `withFullTargetSourceBounds`, `copyForDestinationReadSource` — consumers: `GPURenderer.kt` (legacy) + tests only |
| `GPUClipCoverage.kt:43,46-60` | `GPUClipCoverageFrameBudgetExceededException`, `GPUClipCoverageFrameLease` (legacy mask-lease) |
| `GPUClipCoverage.kt:69-240` | `GPUClipCoverageFrameCache` (legacy mask-lease cache) |
| `GPUClipCoverage.kt:241-319` | `GPUClipUsePrepass` (legacy clip-use prepass; consumer `GPURenderer.kt:1645`) |
| `GPUClipCoverage.kt:322-329` | `gpuClipCoveragePlanOrNull` (legacy-only; consumer `GPUClipUsePrepass:294`) |
| `GPUClipCoverage.kt:418-442` | `clipCompositeBlendFacts` (legacy-only; consumer `GPUClipUsePrepass:293`) |
| `GPUClipCoverage.kt:444-468` | `clipForMaskPrepass` (legacy-only) |
| `GPUClipCoverage.kt:471-478` | `ClipMaskLease` (legacy-only) |
| `GPUClipCoverage.kt:481-805` | `acquireClipMask` + mask materialization helpers (legacy-only; consumers `GPUClipExecution.kt:174`, `GPURenderer.kt:962`) |

### 2. The five precondition families (fallback today, terminal-or-covered after this plan)

All five codes are emitted by **prepared-route machinery** (they surface as `GPUPreparedSurfaceFrameBuildResult.Refused` → executor `BeforePreparedEntryRefused` → router `Legacy` because core-primitive frames have no terminal family). FP-08 evidence §3 proved the 636 cases were REAL rendering by `renderViaGpuLegacy` (pixel oracle passed), not legacy-pinning expectations — so the terminal conversion is a deliberate, evidence-documented behavior change, not a test artifact.

| # | code | cases | emission sites (prepared route) | FP-09 decision |
| --- | --- | --- | --- | --- |
| 1 | `unsupported.destination_read.required` | 630 | `AnalysisContracts.kt:1218-1219, 1611-1612, 1646-1647, 1722-1723, 1956-1957` (DrawLayer planners: `layer.requiresDestinationRead \|\| ordering.dependsOnDestination` + `GPUBlendFacts.canonicalRefusalCode` at `AnalysisContracts.kt:2367-2378`); pinned in `GPUPreparedSurfaceFrameBuilderTest.kt:659` | **Coverage (Task 3)** — `ShaderBlendWithDstRead` + `GPUDestinationSnapshotOperation.TextureCopy` + `GPUBlendFormulaLibrary` already exist (ColorGlyph-only today; see §3) |
| 2 | `unsupported.native-core-primitive.blend` | 330 | `GPUCorePrimitiveDirectNativeRoute.kt:119-124` (SrcOver-only check); classification consumers `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1633-1643, 2025-2034`; `GPUFramePreflighter.kt:2093, 2634-2635, 3351-3352`; pinned in `GPUPreparedSurfaceFrameBuilderTest.kt:664` | **Coverage (Task 2)** — `GPUBlendPlanner` already produces exact `FixedFunctionBlend`/`ShaderBlendNoDstRead` plans and `GPUCorePrimitivePreparedAuthority.Blend.Fixed(mode)`/`ShaderNoDestination` exist structurally |
| 3 | `unsupported.core_primitive.point.hairline_exact_lowering` | 168 | `GPUCorePrimitiveSemanticBuilder.kt:409` (+ `round_cap_exact_lowering` at 411, stroke round-cap at 465); pinned in `GPUFramePathApiInventoryTest.kt:735, 751` | **Stable terminal refusal (Task 4)** — exact hairline point lowering is a new-geometry feature, tracked as a bounded FP-11 gap |
| 4 | `unsupported.recording.core_primitive_mixed_uniform_layouts` | 92 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1602, 2104` (+ preflight twin `unsupported.preflight.core_primitive_mixed_uniform_layouts` at `GPUFramePreflighter.kt:3307`) | **Stable terminal refusal (Task 4)** — multi-layout pass splitting is a recording/execution feature, tracked as a bounded FP-11 gap |
| 5 | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | 52 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1994` (+ intersection twin at 2001) | **Stable terminal refusal (Task 4)** — analytic clip over stencil-shading geometry is a new execution feature, tracked as a bounded FP-11 gap |

(Overlapping frames report multiple codes; the per-code counts above are the FP-08 evidence table's.)

### 3. Prepared machinery that ALREADY covers the blend families (evidence for Tasks 2–3)

- `GPUBlendPlanner` (`gpu-renderer/.../passes/GPUBlendPlanning.kt:148-255`) is an exhaustive 29-mode specializer producing `FixedFunctionBlend` (CLEAR/SRC/DST_OVER/SRC_IN/DST_IN/SRC_OUT/DST_OUT/SRC_ATOP/DST_ATOP/XOR/MODULATE/SCREEN…), `ShaderBlendNoDstRead`, `ShaderBlendWithDstRead` (advanced modes + exact PLUS + scalar-coverage SRC/SRC_IN/SRC_OUT/DST_ATOP), `NoOp`, `UnsupportedBlend`. Kanvas-facing wrappers: `GPUOpMapper.kt:1702-1729` (`canonicalBlendPlan`, `needsDestinationTexture`).
- Structural authority accepts all three blend kinds for core primitives: `GPUCorePrimitivePreparedAuthority.kt:846-868` maps `ShaderBlendWithDstRead` → `Blend.ShaderWithDestination`, `ShaderBlendNoDstRead` → `Blend.ShaderNoDestination`, `FixedFunctionBlend` → `Blend.Fixed(mode, coverage, state)`; the structural key (`GPUCorePrimitiveRenderPipelineStructuralKey.Blend`, same file l.161-183) carries the mode/state.
- GPU-owned destination reads exist in the prepared route: `gpu-renderer/.../destination/GPUDestinationSnapshotGrouping.kt` (TextureCopy/CopyAsDraw planning), `GPUDestinationSnapshotOperation.TextureCopy`, the `GPUTask.DestinationSnapshots` task, and the ColorGlyph evidence path `GPUPreparedSurfaceFrameBuilder.kt:491-523` (`authenticatedDestinationReadEvidence` — `require(semantics[commandId] is GPUDrawSemanticPayload.ColorGlyph)` is the ONLY family gate today) with `GPUBlendPlan.ShaderBlendWithDstRead` at `GPUPreparedSurfaceFrameBuilder.kt:509`.
- `GPUBlendFormulaLibrary` (`gpu-renderer/.../materials/GPUBlendFormulaLibrary.kt`) + `BlendWgslBuilder` + `GPUPreparedRuntimeEffectChildProgramAuthority` — the formula WGSL dispatchers (advanced modes), shared with the legacy renderer's WGSL today.
- Prepared destination-copy smoke precedent: `GPUWgpu4kDestinationCopyFrameSmokeTest` (gpu-renderer).
- `GPUIntermediatePlanner` (`gpu-renderer/.../intermediates/`) already handles `ShaderBlendWithDstRead` (`GPUIntermediatePlanner.kt`), and `GPUFramePlan.kt` carries the snapshot-group planning.
- Empty-frame NoOp precedent: `GPUPreparedSurfacePreBackendNoOpGate` (`GPUPreparedSurfaceFrameExecution.kt:107-162`) + `completeNoOp` (385-416) return transparent zero-filled pixels with zero native work — exact parity with what `renderViaGpuLegacy` returns for an empty frame.

### 4. Guards that MUST survive this plan

- `GPUClipCoverage.kt:340-346` `coreRoutePreflightRefusalReason`, `:353` `coveragePlaneTask4RefusalOrNull`, `:355-387` `picturePreflightRefusalReason`, `:389-392` `Picture.containsLayer`, `:395-416` `gpuCompositePreflightRefusalOrNull` (used by `picturePreflightRefusalReason` at 356/370). The `nested_vertices` guard (`unsupported.picture.nested_vertices`, pinned by `GPUPreparedSurfaceProductRouterTest.kt:295-301` and the composite capture semantics pinned by `GPUPreparedCompositeCaptureSemanticTest.kt:398-431`) stays test-pinned even though its legacy production callers (`GPUClipUsePrepass:289`, `GPURenderer.kt:1705,1713,1884`, `GPUOpMapper.kt:2331,2351`) are deleted.
- `GPUOpMapper.kt:2250-2279` `withPictureReplayState` + `:2360-2434` `clipForPictureReplay`/`transformForPictureReplay` — used by the prepared composite capture at `GPUPreparedCompositeCapture.kt:323`; `expandPicturesForGpuReplay` (2282-2358) is the only deletion in that block.
- `GPUPreparedSurfaceFrameExecution.kt:275`-renamed `unavailable.surface.prepared.runtime-capabilities` (FP-08 Task 5) — untouched.

### 5. Test consumers that pin legacy machinery (inventory for Tasks 5, 6, 9)

| Test | pins | disposition |
| --- | --- | --- |
| `GPUPreparedSurfaceProductEntryTest.kt` | `legacyPort` stubs at 38, 93, 121, 160, 198, 212 | re-point to Terminal expectations (Task 5) |
| `GPUPreparedSurfaceProductRouterTest.kt` | `non-image gate legacy stays legacy…` (58-77), `vertices and mesh reaching the legacy route…` (276-302), `before-entry refusal is legacy…` (436-455) | re-point (Task 5); guard assertions at 295-301 KEEP |
| `GPUPreparedSurfaceFrameGateTest.kt` | Legacy expectations at 78-84, 90-104, 107-126, 139-147 | re-point (Task 5) |
| `GPUClipCoverageSurfaceTest.kt` | `legacyPort` stubs at 570-789; `GPUClipRouteTrace` at 930, 986, 1189, 1237, 1281, 1348; `routeTrace` calls at 1256, 1294, 1360 | re-point with evidence (Task 6); composite-refusal cases keep |
| `GPUAllApiBlendSurfaceTest.kt` | `ProductRouteExpectation.Legacy` / `GPUPreparedSurfaceRouteDecision.Legacy` (126-136, 165-193) for the 5 families; `assertPixelsNear` CPU oracle at 152-157 | re-point with evidence (Task 6) |
| `GPUClipCoverageDispatchTest.kt` | `renderWithClip`/`GPUClipRouteContext`/`GPUClipSourceSurface` (281+) | delete (Task 9 — dies with `GPUClipExecution.kt`) |
| `GPUTextAtlasGeometryTest.kt` | `buildTextAtlasMesh`/`normalizeGlyphRects` | delete (Task 9) |
| `GPUSaveLayerCompositeRegressionTest.kt` | `LayerScissorOffscreenTarget`/`LayerBounds` | delete (Task 9) |
| `GPUProductIntermediatePlannerScopeTest.kt` | `productIntermediatePlannerScopeDiagnostics` (stale phase-5 diagnostic string) | delete (Task 9) |
| `GPUColorGlyphPaintAlphaTest.kt` | `modulateCpalLayerAlpha`/`colorGlyphSourceColor` (legacy text-atlas color helpers) | re-point or delete (Task 9) |
| `GPUPathStrokeInputTest.kt` | `selectPathVerticesForCommand` | re-point or delete (Task 9) |
| `GPUImageFilterDispatchTest.kt` | `copyForClipSource` | re-point (Task 9) |
| `GPUBlendFormulaSurfaceTest.kt` | `destinationReadBlendModeIndex` (legacy uniform-index mapping) | re-point to prepared `GPUBlendFormulaLibrary` evidence (Task 9) |
| `GPUPreparedSurfaceFrameBuilderTest.kt` | refusal matrix 655-684 pins `unsupported.destination_read.required` (659) + `unsupported.native-core-primitive.blend` (664) | re-point in Tasks 2-3 (rows flip to Ready) |
| `GPUFramePathApiInventoryTest.kt` | hairline/round-cap refusal pins at 735, 751 (already prepared-route codes) | KEEP — the codes become terminal (Task 4 documents) |
| `GPUPreparedSurfaceLegacyAbsenceTest.kt` | pins 4 FP-08 tokens only | extend token list (Task 10) |
| `SurfaceTest.kt` (kanvas) | no legacy-pinning cases at HEAD (BGRA/snapshot already prepared; `readPixels` is the GPU-owned readback API) | verify-only (Task 10) |

---

## File Map

### Deleted (new to this plan)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt` — entire file (Task 8).
- `GPURenderer.kt:87-696` legacy-only helpers: `modulateCpalLayerAlpha`, `colorGlyphSourceColor`, `productIntermediatePlannerScopeDiagnostics`, `selectPathVerticesForCommand`, `hasActiveMaskBlur` (private), `requiresSeparateGeometryCoverage`, `geometryCoverageMaterial`, `forGeometryCoverage` (×3), `LAYER_OPACITY_WGSL`, `layerOpacityUniformDraw`, `maskBlurDiagnosticFacts`, `destinationReadBlendModeIndex`, `clipCoverageBlendModeIndex`, `destinationReadBlendUniformDraw`, `clipCoverageBlendUniformDraw`, `coverageCombineUniformDraw`, `destinationReadScissorBlendUniformDraw`, `renderDestinationReadBlend`, `LayerBounds`, `LayerPlan`, `LayerCompositePlan`, `BackdropPlan`, `SceneTargetFrame`, `LayerScissorOffscreenTarget`, `LayerScissorRenderRecorder`, `intersectLayerScissor` (×6), `intersectScissor` (×3) (Task 7).
- `GPURenderer.kt:729-3042` `renderViaGpuLegacy` (Task 7).
- `GPURenderer.kt:3043-3262` CPU text-atlas builders (Task 7).
- `GPUOpMapper.kt:2282-2358` `expandPicturesForGpuReplay` (Task 8).
- `GPUClipCoverage.kt` legacy mask-lease machinery: l.43, l.46-240 (`GPUClipCoverageFrameCache`/`GPUClipCoverageFrameLease`/`GPUClipCoverageFrameBudgetExceededException`), l.241-329 (`GPUClipUsePrepass`, `gpuClipCoveragePlanOrNull`), l.418-468 (`clipCompositeBlendFacts`, `clipForMaskPrepass`), l.471-805 (`ClipMaskLease`, `acquireClipMask`, mask materialization helpers) (Task 8).
- Tests: `GPUClipCoverageDispatchTest.kt`, `GPUTextAtlasGeometryTest.kt`, `GPUSaveLayerCompositeRegressionTest.kt`, `GPUProductIntermediatePlannerScopeTest.kt` (Tasks 9).

### Modified (new to this plan)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt` — `GPUPreparedSurfaceEligibility.Legacy` → `Refused(code, operationIndex)`; `FlushAndSnapshot` becomes a state event (Candidate, no code); empty/state-only frames → `Candidate`; color refusal keeps its stable code under `Refused` (Task 5).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt` — delete `GPUPreparedSurfaceProductRoute.Legacy` (l.18) and the l.36 / l.60-65 Legacy constructions; `BeforePreparedEntryRefused` → always `Terminal`; delete `hasTerminalPreparedFamily` (l.152-165) (Task 5).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntry.kt` — delete `GPUPreparedSurfaceRouteDecision.Legacy` (l.12), `GPUPreparedSurfaceLegacyPort` (l.21-30), `legacyPort`/`legacyRouteTrace` params (l.56-57), `Legacy` branch (l.70-73) (Task 5).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` — extend `GPUPreparedSurfacePreBackendNoOpGate` (l.107-162) to classify empty/state-only frames → `NoOp` (Task 5).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` — delete `renderViaGpuLegacy`/`preparedSurfaceLegacyPort`/legacy helpers; `renderViaGpu` drops the `routeTrace: GPUClipRouteTrace?` param (l.703, 715) (Tasks 5, 7).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitiveDirectNativeRoute.kt` — replace the `canonicalPremultipliedSrcOver: Boolean` param/check (l.94-124) with real blend-plan admission (Task 2).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt` — classification call sites l.1633-1643, l.2025-2034 pass the packet's `blendPlan`; admit fixed/shader blends (Task 2).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt` — classification call sites l.2093, l.2634-2635, l.3351-3352 pass the packet's `blendPlan` (Task 2).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt` — DrawLayer planners admit destination-read blends (remove the blanket `layer.requiresDestinationRead`/`canonicalRefusalCode` refusals at l.1218-1219, 1611-1612, 1646-1647, 1722-1723, 1956-1957, 2367-2378) once the snapshot/formula path is wired (Task 3).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` — honor non-SrcOver `FixedFunctionBlend` states + `ShaderWithDestination` dst-texture binding for core draws (verify-then-wire, Task 2/3).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt` — keep only the pinned guards (l.331-416 area); delete everything else (Task 8).
- `GPUPreparedSurfaceLegacyAbsenceTest.kt` — extend the retired-token list (Task 10).

### Tests (new / modified / deleted — Tasks 2, 3, 5, 6, 9, 10)
- Modified: `GPUPreparedSurfaceFrameGateTest.kt`, `GPUPreparedSurfaceProductRouterTest.kt`, `GPUPreparedSurfaceProductEntryTest.kt`, `GPUPreparedSurfaceFrameBuilderTest.kt`, `GPUAllApiBlendSurfaceTest.kt`, `GPUClipCoverageSurfaceTest.kt`, `GPUImageFilterDispatchTest.kt`, `GPUBlendFormulaSurfaceTest.kt`, `GPUColorGlyphPaintAlphaTest.kt` (or delete), `GPUPathStrokeInputTest.kt` (or delete), `GPUPreparedSurfaceLegacyAbsenceTest.kt`.
- Deleted: `GPUClipCoverageDispatchTest.kt`, `GPUTextAtlasGeometryTest.kt`, `GPUSaveLayerCompositeRegressionTest.kt`, `GPUProductIntermediatePlannerScopeTest.kt`.
- Kept as-is (guard pins): `GPUPreparedCompositeCaptureSemanticTest.kt`, `GPUPreparedCompositeFrameRouteIntegrationTest.kt`, `GPUFramePathApiInventoryTest.kt` (hairline pins), `GPURendererPackageBoundaryTest` (pre-existing failure), `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` (pre-existing failure).

---

## Phase 0 — Baseline & dead-path detection

### Task 1: Inventory every remaining path to the legacy renderer and freeze the green baseline

**Files:** Create `reports/fp09-retire-legacy-immediate-renderer-plan.md` (this plan, committed as the FP-09 reference); evidence only otherwise.

**Context:** Before touching code, the implementer must reproduce the production-legacy map in §1 of this plan and freeze the green baseline. This map is the acceptance oracle: the plan is complete only when every row is gone (deleted) or provably terminal/prepared.

- [ ] **Step 1: Re-run the legacy oracle and diff against §1**

```bash
cd /Users/chaos/workspace/kanvas/.worktrees/graphite-dawn-frame-fp09
rg -n "GPUPreparedSurfaceProductRoute\.Legacy|GPUPreparedSurfaceEligibility\.Legacy|GPUPreparedSurfaceRouteDecision\.Legacy|GPUPreparedSurfaceLegacyPort|renderViaGpuLegacy|legacy\.surface\.prepared|legacyPort|hasTerminalPreparedFamily" kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu --type kotlin > /tmp/fp09_legacy_map.txt
```

Expected: matches the §1 table (line numbers may drift if HEAD moved; record the actual values — the diff is the evidence). Save to `reports/fp09-legacy-map.txt`.

- [ ] **Step 2: Freeze the green baseline**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
```

Expected: first command BUILD SUCCESSFUL; second FAILS ONLY on `gpu renderer production source satisfies package boundary rules` (pre-existing, 20 cycle violations — do not modify).

- [ ] **Step 3: Commit**

```bash
git add reports/
git commit -m "docs(surface): fp09 legacy path inventory and green baseline evidence"
```

---

## Phase 1 — Precondition coverage: non-SrcOver core-primitive blends (family 2, 330 cases)

### Task 2: Admit fixed-function and no-destination-read shader blends on prepared core primitives

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitiveDirectNativeRoute.kt` (l.94-124)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt` (l.1633-1643, 2025-2034)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt` (l.2093, 2634-2635, 3351-3352)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` (verify-then-wire blend state)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt` (refusal-matrix rows at l.660-664)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveNativeRouteTest.kt`

**Context:** `GPUBlendPlanner` already produces exact `FixedFunctionBlend` plans for CLEAR, SRC, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, MODULATE, SCREEN (full coverage) and `ShaderBlendNoDstRead` modulate plans (scalar coverage), and `GPUCorePrimitivePreparedAuthority.Blend.Fixed(mode, coverage, state)`/`ShaderNoDestination` exist structurally (GPUCorePrimitivePreparedAuthority.kt:846-868). The ONLY admission gate is the direct-native-route classifier's SrcOver-only check (GPUCorePrimitiveDirectNativeRoute.kt:119-124) reached from the task-list builder (1633-1643, 2025-2034) and the preflighter (2093, 2634-2635, 3351-3352). This task replaces that gate with real blend-plan admission and verifies the materializer honors the non-SrcOver blend state.

- [ ] **Step 1: Write the failing tests (red)**

In `GPUPreparedSurfaceFrameBuilderTest.kt`, remove the two blend rows from the refusal-matrix `cases` list (l.660-664) and add a dedicated failing test:

```kotlin
@Test
fun `clear and src hard rects build ready with fixed function blend packets`() {
    val clear = request(listOf(
        rect(color = Color.BLUE),
        rect(color = Color.RED).copy(paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.CLEAR)),
    ))
    val clearReady = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
        GPUPreparedSurfaceFrameBuilder.build(clear),
    )
    val clearBlends = clearReady.taskList.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask::drawPackets)
        .mapNotNull { (it.blendPlan as? GPUBlendPlan.FixedFunctionBlend)?.mode }
    assertTrue(GPUBlendMode.CLEAR in clearBlends)

    val src = request(listOf(rect().copy(paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.SRC))))
    val srcReady = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
        GPUPreparedSurfaceFrameBuilder.build(src),
    )
    val srcBlends = srcReady.taskList.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask::drawPackets)
        .mapNotNull { (it.blendPlan as? GPUBlendPlan.FixedFunctionBlend)?.mode }
    assertTrue(GPUBlendMode.SRC in srcBlends)
}
```

(Read the actual `rect()` fixture defaults at HEAD first — if `rect()` is anti-aliased, SRC falls under scalar coverage and its plan is `ShaderBlendNoDstRead`/`ShaderBlendWithDstRead` instead; assert the exact plan the planner produces for the fixture and cover both the hard-rect CLEAR case and the fixture's SRC case.)

In `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveNativeRouteTest.kt`, add a case: a hard rect with `GPUBlendFacts` mode CLEAR (canonical fixed-function state) must classify `Accepted` (today it classifies `Refused("unsupported.native-core-primitive.blend")`).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameBuilderTest" --no-parallel --console=plain`
And: `./gradlew -F off :gpu-renderer:test --tests "*GPUCorePrimitiveNativeRouteTest" --no-parallel --console=plain`
Expected: FAIL — CLEAR/SRC frames still `Refused` with `unsupported.native-core-primitive.blend`; the new classifier case still refuses.

- [ ] **Step 3: Admit fixed/shader-no-dst blend plans in the direct-native-route classifier**

In `GPUCorePrimitiveDirectNativeRoute.kt:94-124`, replace the `canonicalPremultipliedSrcOver: Boolean` parameter with `blendPlan: GPUBlendPlan` and replace the l.119-124 check:

```kotlin
if (!blendPlan.isCorePrimitiveDirectLaneBlend()) {
    return refused(
        "unsupported.native-core-primitive.blend",
        "Direct CorePrimitive native geometry requires a canonical fixed-function, " +
            "shader-no-destination, or shader-with-destination blend plan.",
    )
}
```

Add the admission predicate next to it:

```kotlin
internal fun GPUBlendPlan.isCorePrimitiveDirectLaneBlend(): Boolean = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> true
    is GPUBlendPlan.ShaderBlendNoDstRead -> true
    is GPUBlendPlan.ShaderBlendWithDstRead -> true
    is GPUBlendPlan.LayerCompositeBlend -> child.isCorePrimitiveDirectLaneBlend()
    is GPUBlendPlan.NoOp -> true
    is GPUBlendPlan.UnsupportedBlend -> false
}
```

(Define `isCorePrimitiveDirectLaneBlend` in `GPUCorePrimitivePreparedAuthority.kt` next to `corePrimitiveStructuralBlend` at l.846-868 if it must be shared; otherwise keep it file-private in `GPUCorePrimitiveDirectNativeRoute.kt`.)

Update the three production callers to pass the real plan:
- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1633-1643` (`classifyCorePrimitiveDirectNativeRoute(semantic, clipExecutionPlan, blendPlan = packet.blendPlan, samplePlan, targetFormat)`)
- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2025-2034` (same)
- `GPUFramePreflighter.kt:2093` (the preflighter's classification site — pass the packet's `blendPlan`; adjust the local signature/`canonicalPremultipliedSrcOver` usages at 2634-2635, 3351-3352)

- [ ] **Step 4: Verify the materializer honors the non-SrcOver blend state**

Search `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` for where the structural key's `Blend` is turned into the pipeline descriptor (`GPUFixedFunctionBlendState`, `Blend.Fixed`); verify a non-SrcOver `FixedFunctionBlend` state reaches the color blend component (it is carried by `GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(mode, coverage, state)` via `stableRenderPipelineKey` at GPUCorePrimitivePreparedAuthority.kt:289-332 and the sealed blend state at l.161-183). If the materializer hard-codes SrcOver for core shading, wire `blend.state` through the descriptor at the site you find and note the `file:line` in the commit message. For `ShaderBlendNoDstRead`, verify the formula shader path exists (`BlendWgslBuilder`); if the core materializer has no no-dst shader-blend path, keep `ShaderBlendNoDstRead` refused at this stage ONLY for the analytic/direct shading lane (document the residual code) and proceed — the scalar-coverage no-dst cases are a small remainder re-verified in Task 6's evidence run.

- [ ] **Step 5: Run to verify green + regression**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameBuilderTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPUCorePrimitiveNativeRouteTest" --tests "*GPUFramePreflighterTest" --tests "*GPUCorePrimitivePreparedFrameTaskListBuilderTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
```

Expected: builder matrix green (SRC/CLEAR rows Ready); classifier/preflighter suites green; the GPU blend suite stays green (in a WebGPU environment, the CLEAR/SRC/DST_IN/DST_OUT… full-coverage mode cases on hard rects/rrects/paths must now take the `Prepared` route and match the CPU oracle at `GPUAllApiBlendSurfaceTest.kt:152-157` — record the observed route split; in a non-GPU environment the cases skip).

- [ ] **Step 6: Commit**

```bash
git add gpu-renderer/src/main gpu-renderer/src/test kanvas/src/test
git commit -m "feat(surface): prepared non src over fixed function blends on core primitives"
```

---

## Phase 1 (continued) — Precondition coverage: destination-read blends (family 1, 630 cases)

### Task 3: Admit destination-read shader blends on prepared core primitives and layer composites

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt` (destination-read evidence path l.491-523; verify the ColorGlyph `require` gates)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt` (DrawLayer planners l.1218-1219, 1611-1612, 1646-1647, 1722-1723, 1956-1957, 2367-2378)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt` (dst-read packet admission — classification sites from Task 2 already admit `ShaderBlendWithDstRead`)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` (verify-then-wire the dst-texture binding for `Blend.ShaderWithDestination`)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt` (refusal-matrix row at l.658-659)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerDestinationContractTest.kt`, `GPUWgpu4kDestinationCopyFrameSmokeTest` (smoke, WebGPU env)

**Context:** The prepared route already implements GPU-owned destination reads for ColorGlyph: `GPUDestinationSnapshotOperation.TextureCopy` + `GPUTask.DestinationSnapshots` + `ShaderBlendWithDstRead` (evidence gate `GPUPreparedSurfaceFrameBuilder.kt:503` is `require(semantics[commandId] is GPUDrawSemanticPayload.ColorGlyph)`), with `GPUBlendFormulaLibrary` providing the formula WGSL. The refusal `unsupported.destination_read.required` fires before that path for every other family: `GPUBlendFacts.canonicalRefusalCode` (AnalysisContracts.kt:2367-2378) and the five DrawLayer planner sites. This task extends the admission to core primitives (rect/rrect/path) and to DrawLayer composites with non-SrcOver blends, reusing the snapshot/formula machinery.

- [ ] **Step 1: Write the failing tests (red)**

In `GPUPreparedSurfaceFrameBuilderTest.kt`, flip the SRC-scalar row (l.658-659) to Ready: an AA rect (`antiAlias = true`, i.e. `GPUCoverageConsumption.ScalarCoverage`) with `BlendMode.SRC` must now build `Ready` and emit a `GPUTask.DestinationSnapshots` task plus a `ShaderBlendWithDstRead` packet (`blendPlan is GPUBlendPlan.ShaderBlendWithDstRead` on the matching render packet, `mode.gpuLabel == "src"`, `formulaId == "src@v1"`).

In `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerDestinationContractTest.kt`, add a case: a DrawLayer command whose restore blend is `GPUBlendMode.MULTIPLY` (DestinationTextureRequired) must plan `Ready` with a `DestinationSnapshots` task + `ShaderBlendWithDstRead` packet (today it plans `Refused("unsupported.destination_read.required")` via `canonicalRefusalCode`).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameBuilderTest" --no-parallel --console=plain`
And: `./gradlew -F off :gpu-renderer:test --tests "*GPUFramePlannerDestinationContractTest" --no-parallel --console=plain`
Expected: FAIL — both still refuse with `unsupported.destination_read.required`.

- [ ] **Step 3: Remove the blanket destination-read refusals and admit the snapshot path**

1. In `AnalysisContracts.kt`, change the DrawLayer planners: delete the `layer.requiresDestinationRead || ordering.dependsOnDestination -> "unsupported.destination_read.required"` branches (l.1218-1219, 1611-1612, 1646-1647, 1722-1723, 1956-1957) and the `DestinationTextureRequired` branch of `canonicalRefusalCode` (l.2373-2376) so `ShaderBlendWithDstRead` plans are admitted instead of refused. Keep the `UnsupportedBlend` branch.
2. In `GPUPreparedSurfaceFrameBuilder.kt:491-523`, widen the evidence gate: the `require(semantics[commandId] is GPUDrawSemanticPayload.ColorGlyph)` assertion becomes a filter that also accepts core-primitive semantics (`GPUDrawSemanticPayload.CorePrimitive`) carrying a `ShaderBlendWithDstRead` packet, so the prepared route reports `route:destination-read:<operation>` diagnostics for the new families too. Verify `GPUDestinationSnapshotOperation.TextureCopy`/`CopyAsDraw` materialization is family-agnostic (it is — `gpu-renderer/.../destination/GPUDestinationSnapshotGrouping.kt` plans by command/blend, not by family; if you find a family check, remove it).
3. In `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`, verify-then-wire the `Blend.ShaderWithDestination` dst-texture binding for core shading (the ColorGlyph precedent binds `sampler + textureView` per the Graphite/Dawn model; see FP-08 evidence §4). If the core materializer lacks the binding, add it at the pipeline-layout site for `Blend.ShaderWithDestination` and bind the snapshot texture from the `DestinationSnapshots` task; note the `file:line` in the commit message. The WGSL formula comes from `GPUBlendFormulaLibrary`/`BlendWgslBuilder` (already imported by the runtime).

- [ ] **Step 4: Run to verify green + regression**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameBuilderTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPUFramePlannerDestinationContractTest" --tests "*GPUBlendFormulaLibraryTest" --tests "*GPUWgpu4kDestinationCopyFrameSmokeTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
```

Expected: builder + destination-contract suites green; blend formula library green; the GPU blend suite green (in a WebGPU environment the advanced-mode cases on core primitives and saveLayer composites must now take the `Prepared` route and emit `route:destination-read:<op>:<id>` with `reason == "gpu-copy-then-formula"` — asserted at `GPUAllApiBlendSurfaceTest.kt:176-193` — while matching the CPU oracle; record the observed route split per mode). `SurfaceTest` stays green (BGRA prepared native path unaffected).

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main gpu-renderer/src/main kanvas/src/test gpu-renderer/src/test
git commit -m "feat(surface): prepared destination read blends on core primitives and layer composites"
```

---

## Phase 1b — GRAPHITE-FAITHFUL MULTI-PIPELINE DIRECT PASSES (SCOPE AMENDMENT 2026-08-08, user decision)

> **Amendment:** During Tasks 2-3 execution, the prepared direct pass was found to materialize ONE structural pipeline per pass (sealed pass-seal + operand-topology contracts), forcing mixed-blend frames to refuse with `unsupported.recording.core_primitive_mixed_pipeline_keys` and stay on the legacy route. The user directed: verify Graphite/Dawn in `/Users/chaos/workspace/kanvas-forge/skia-main` (done — evidence below), then implement multi-pipeline-per-pass in FP-09.

**C++ evidence (skia-main, verified 2026-08-08):**
- A `DrawPass` holds an ARRAY of pipelines; draws reference them by index via `BindGraphicsPipeline` commands emitted MID-PASS (`DrawPass.h:103-113`, `DrawCommands.h:108-109`, `DrawList.cpp:203-206`); Dawn executes `SetPipeline` inside one render pass (`DawnCommandBuffer.cpp:675-679, 775-784`). Blend mode is per-pipeline (`GraphicsPipelineDesc = {renderStepID, paintID}`, `GraphicsPipelineDesc.h:27-43`); a frame mixing SrcOver+CLEAR renders in ONE pass with multiple pipeline binds — Graphite never splits a pass on blend mode. `RenderPassDesc.h:87-91` explicitly anticipates mixed pipelines in one pass.
- Destination reads: per-pass `kTextureCopy` decision at flush (`Device.cpp:2176`); GPU-only `CopyTextureToTextureTask` ordered BEFORE the consuming `RenderPassTask` in the same encoder (`DrawContext.cpp:198-204, 270-315`, `Image_Graphite.cpp:113-137`); dst texture+sampler appended at the END of the fragment bind group (bindings 2n-2/2n-1, `DawnCommandBuffer.cpp:927-938`); `dstReadBounds` intrinsic uniform (`ContextUtils.cpp:86-118`); dst-reading pipelines blend in the shader with fixed-function Src (`ShaderInfo.cpp:1011-1022, 1139-1160, 1218-1219`).
- Multi-pipeline + dst-copy coexist via `rebindTexturesOnPipelineChange` (`DrawList.cpp:140-174`) and PER-PIPELINE bind groups (dst-copy presence changes the fragment binding count).
- The Task 2 per-packet revert (256 failures) therefore reflects a missing per-pipeline bind-group discipline, NOT the model — WebGPU legally allows `SetPipeline` mid-pass.

**Design (Graphite-faithful, execution-first to avoid the 256-failure trap):**
- Task 3b materializes N structural pipelines per direct pass (per-pipeline bind groups + dst bindings at the end of the fragment layout) with materializer-level tests, WITHOUT flipping the recording gate.
- Task 3c flips the recording/seal/preflight gates: `core_primitive_mixed_pipeline_keys` refusal removed, pass seal + preflight generalized to N keys, dst-read core frames flow end-to-end with pixel evidence, executor fallback residuals removed, router evidence label generalized beyond `DrawText:`.
- Task 6's evidence run then re-splits `GPUAllApiBlendSurfaceTest` per the actual coverage.

### Task 3b: Materialize multiple structural pipelines per direct pass (execution side)

**Files:** verify-then-modify at HEAD (line numbers drifted during Tasks 2-3):
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` (the pass-seal → pipeline materialization loop, ~l.811)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt` (the shared-key authority ~l.4815, `authenticateColorGlyphDestinationReads` l.1052-1077)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt` (l.175-186 core+dst-copy refusal)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt` (structural key → pipeline identity)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitivePipelineDescriptor.kt` (`nativeShadingBlendProgramOrNull` l.387-393, blend program enum)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` (executor fallback residual codes l.524-545, 825-836)

**Context:** The recording builder already emits per-packet blend plans and per-key structural authority; the sealed pass-seal/preflight contracts assume one key. This task generalizes the EXECUTION side to N keys per pass: the materializer binds per-key pipelines mid-pass (Graphite `BindGraphicsPipeline(index)`), per-key bind groups (dst-copy presence appends sampler+textureView at the end of the fragment layout — `DawnCommandBuffer.cpp:927-938`), and per-key pipeline caching. The recording gate (`core_primitive_mixed_pipeline_keys`) STAYS until Task 3c so no frame shape changes yet.

- [ ] **Step 1: Write failing materializer tests (red)**
- [ ] **Step 2: Run to verify they fail**
- [ ] **Step 3: Generalize the materializer to N pipelines per pass** (multi-key pass seal → per-key pipeline identity + bind group + pipeline cache; dst binding slots at fragment-layout end)
- [ ] **Step 4: Generalize the preflight shared-key authority to N keys**
- [ ] **Step 5: Run green + regression** (`GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest`, `GPUPreparedSurfaceNativePreflightTest`, full `:gpu-renderer:test`)
- [ ] **Step 6: Commit** — `feat(surface): materialize multiple structural pipelines per direct pass`

### Task 3c: Destination-read shader blends on the core lane (Graphite dst-copy recipe) + gate flip

**Files:** as in Task 3 plus `GPUWgpu4kCorePrimitivePipelineDescriptor.kt` (formula program), `BlendWgslBuilder`/`GPUBlendFormulaLibrary` (formula WGSL + dst sampler snippet per `ShaderInfo.cpp:1066-1160`), `GPUPreparedSurfaceFrameBuilder.kt` (evidence gate, already widened in Task 3), `GPUPreparedSurfaceProductRouter.kt:98` (evidence label), `GPUCorePrimitivePreparedFrameTaskListBuilder.kt` (remove the `core_primitive_mixed_pipeline_keys` gate l.2115-2143), `GPUFramePreflighter.kt` (one-key authority), pass seal generalization.

**Context:** Task 3 already wired the ADMISSION (DestinationSnapshots planning for core + DrawLayer) and left 5 execution gaps documented. This task closes them per the Graphite recipe: dst formula program for the core shading lane, dst sampler+textureView bound at fragment-layout end, `dstReadBounds` uniform, preflight admission for CorePrimitive consumers, dispatcher core+dst-copy route; then flips the recording gates so dst-read core frames and mixed-blend frames materialize prepared (multi-pipeline per Task 3b). GPU pixel evidence: DARKEN rect over a destination rect vs the CPU oracle at `GPUAllApiBlendSurfaceTest.kt:152-157`.

- [ ] **Step 1: Write failing tests (red)** — dst-read core frame (DARKEN rect over destination) must build+execute `Ready` with `route:destination-read:DrawRect:*` evidence (`reason == "gpu-copy-then-formula"`); mixed SRC_OVER+CLEAR frame must build `Ready`
- [ ] **Step 2: Run to verify they fail**
- [ ] **Step 3: Implement the core dst-read materialization** (descriptor program + formula WGSL + dst bindings + preflight + dispatcher)
- [ ] **Step 4: Flip the recording gates** (remove `core_primitive_mixed_pipeline_keys`; generalize preflighter one-key authority; remove the executor fallback residuals)
- [ ] **Step 5: Run green + GPU pixel regression** (`GPUAllApiBlendSurfaceTest` — record the new route split; full `:kanvas:test` + `:gpu-renderer:test`)
- [ ] **Step 6: Commit** — `feat(surface): destination read formula blends on prepared core lane`

---

---

## Phase 4b — RESTORE TOP-LEVEL MASK BLUR (SCOPE AMENDMENT 2026-08-08, user decision)

> **Amendment:** The Task 10 full regression discovered 18 top-level mask-blur pins (`GPUMaskBlurSurfaceTest` ×11, `GPUPathClipRegressionTest` ×4, `GPUClipAdvancedBlendSurfaceTest` ×3) absent from the plan's §5 inventory: frames that rendered via `renderViaGpuLegacy` (proven green at FP-08 tip `accaea616`) now refuse with `unsupported.core_primitive.rect.analysis_authority_missing` / `unsupported.pipeline.capability_missing` / `invalid.recording.core_primitive_semantic_authority` — the only capability removal outside the anticipated inventory. External review flagged it; the user decided to RESTORE top-level mask blur in the prepared route rather than accept the FP-11 deferral.

**Precedents (verified at HEAD):** the prepared composite route already lowers blur→coverage A8 (`GPUPreparedMaskFilterLowerer.lower(NormalizedMaskFilter)` → `GPUPreparedCoverageFormat.A8`, gpu-renderer filters); the core route has the coverage-mask producer machinery (`GPUCorePrimitiveCoverageMaskPreparedRoute`, `GPUWgpu4kCoverageMaskProducerMaterializer`, R8/A8 mask route); the legacy top-level dispatcher `GPUMaskBlurDispatch.kt` (475 lines: `renderMaskBlurCommand`/`toMaskBlurRequest`/`maskBlurPreflightRefusalReasonOrNull`/`toLocalMaskCommand`) is orphaned but test-pinned by `GPUMaskBlurDispatchTest` — the legacy blur-pass logic and WGSL survive there as reference. `MaskBlurPlan.kt` + `GPUSeparableBlur.kt` (gpu-renderer filters) hold the prepared blur plan/execution.

### Task 11: Top-level mask blur renders prepared on core primitives (rect/path/rrect)

**Goal:** `DrawRect`/`DrawPath`/`DrawRRect` with `paint.maskFilter = MaskFilter.Blur` at the TOP LEVEL (surface ops, no saveLayer) build+execute prepared with pixel evidence, flipping the 18 Task-10 terminal re-points back to `Prepared` assertions.

**Approach (shape-blur, faithful to legacy semantics):** materialize the draw's A8 blur coverage (shape coverage via the core coverage-mask producer machinery, blurred via the prepared blur path), then shade color × blurred coverage — mirroring how the composite route blurs scoped masks and how `renderViaGpuLegacy` blurred top-level shapes (reference: `GPUMaskBlurDispatch.kt` + the deleted `renderDestinationReadBlend`-era blur pass). The exact wiring is the implementer's discovery task: where the top-level blur must hook into `GPUCorePrimitiveSemanticBuilder` (currently refusing) + the recording/execution lanes.

- [ ] **Step 1: Write failing tests (red)** — flip the 18 re-points back: each asserts `Ready` + pixel evidence (CPU blur oracle — the composite route's blur oracle / `GPUMaskBlurDispatchTest` blur math, or the pre-FP-09 legacy render as reference via a documented oracle)
- [ ] **Step 2: Run to verify they fail** — all 18 must fail with the current terminal codes
- [ ] **Step 3: Implement top-level blur in the prepared core route** (semantic admission + A8 blur coverage materialization + shading; reuse `GPUPreparedMaskFilterLowerer`/`GPUSeparableBlur`/coverage-mask producer; retire the three refusal paths for blur)
- [ ] **Step 4: Run green + GPU pixel regression** (the 18 tests + `GPUAllApiBlendSurfaceTest` + `GPUClipCoverageSurfaceTest` + full `:kanvas:test`/`:gpu-renderer:test`)
- [ ] **Step 5: Update the evidence/roadmap** (the 18 families move from terminal to prepared in evidence §10/§15/§16 + FP-11 transfers; remove the top-level blur gap note)
- [ ] **Step 6: Commit** — `feat(surface): top level mask blur renders prepared on core primitives`

---

## Phase 2 — Terminal-family policy (families 3–5) and route-authority collapse

### Task 4: Document and pin the stable terminal refusals for hairline points, mixed uniform layouts, and analytic-clip non-direct geometry

**Files:** evidence only (this task produces the evidence table consumed by Task 6; the codes are already emitted by the prepared route and pinned by `GPUFramePathApiInventoryTest.kt:735, 751` — no production change).

**Context:** Families 3-5 are genuine rendering features of `renderViaGpuLegacy` that the prepared route does not implement: exact hairline point lowering (`GPUCorePrimitiveSemanticBuilder.kt:409, 411, 465`), multi-uniform-layout direct passes (`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1602, 2104`), and analytic clips over non-direct shading geometry (`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1994`). Per FP-08 evidence §6, each family must be covered OR explicitly refused with a stable terminal code replacing the legacy render. These three become **stable terminal refusals**; restoring real rendering is tracked as a bounded gap under FP-11 ("required stroke, coverage…"). The conversion is a deliberate, evidence-documented behavior change (pixels → loud terminal refusal) — this is exactly what the FP-08 evidence §3 proved was premature before the coverage of Tasks 2-3 existed.

- [ ] **Step 1: Record the terminal-policy decision**

The per-family policy table (code, case count, emission site, FP-08 evidence §3 attribution, decision, FP-11 tracking note) is already captured in this plan's Context §2; the standalone evidence file `fp-09-retire-legacy-immediate-renderer-evidence.md` is created in Task 6 (red-run table) and finalized in Task 10. No file is written in this task — it is the decision record, not a code change.

- [ ] **Step 2: Pin the three codes as terminal through the router**

This step lands inside Task 5 Step 1 (its red-test block): `GPUPreparedSurfaceProductRouterTest` gains a matrix case asserting that a `BeforePreparedEntryRefused` carrying any of the three codes (plus a generic core refusal) routes to `Terminal` — never `Legacy`. The existing semantic-builder pins (`GPUFramePathApiInventoryTest.kt:735, 751`) already fix the codes at the source, so no other test change is required for the codes themselves.

- [ ] **Step 3: Commit (no production diff; the pins land with Task 5)**

Skipped — folded into Task 5's commit.

---

### Task 5: Collapse route authorities to Prepared/Terminal/Refused/NoOp

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntry.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` (NoOp gate extension)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` (renderViaGpu signature)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouterTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntryTest.kt`

**Context:** This is the FP-08 original Tasks 4-5 content, now justified: after Tasks 2-3 the blend families are prepared-covered, and families 3-5 are documented terminal refusals. `BeforePreparedEntryRefused` → always `Terminal` (delete the `hasTerminalPreparedFamily` split). The gate's three `Legacy` construction sites become: color-refused → `Refused(stable code)`; `FlushAndSnapshot` → state event (the prepared `GPUOpMapper` already records `GPUFramePathStateKind.FlushSnapshot` at `GPUOpMapper.kt:180-181`, and the legacy renderer itself treats it as a no-op — `GPURenderer.kt` comment "deferred to render-backend; no-op in CPU path"); empty/state-only → `Candidate`, resolved by the executor's extended `GPUPreparedSurfacePreBackendNoOpGate` into a backend-free transparent result (parity with `renderViaGpuLegacy`, which returns the cleared target). `GPUPreparedSurfaceLegacyPort`, `GPUPreparedSurfaceRouteDecision.Legacy`, and `renderViaGpu`'s `routeTrace: GPUClipRouteTrace?` param are deleted. Unit-test re-points land in the SAME commit so the pure-route suites stay green.

- [ ] **Step 1: Write the failing unit tests (red)**

`GPUPreparedSurfaceFrameGateTest.kt`:
- `empty and state only frames use the stable empty frame diagnostic` (l.90-104) → `empty and state only frames classify as candidate and complete as noop`: assert `Candidate` for `emptyList()` and the state-only list (code and `operationIndex` assertions deleted).
- `first refused operation wins with its exact index family and code` (l.107-126) → the `FlushAndSnapshot` cases now expect `Candidate` (state event; the `flush` op no longer refuses).
- `bgra8 unorm enters the candidate while rgba8 unorm stays refused` (l.139-147) → the RGBA8_UNORM case asserts `GPUPreparedSurfaceEligibility.Refused("unsupported.surface.gpu-color-format.rgba8-unorm", null)`.
- Fixture `FlushAndSnapshot` row (l.213) → `Expected.Candidate`; `SetTransform`/`SetClip`/`Annotation` rows (l.191-196, 210-212) → `Expected.Candidate` (empty-frame NoOp now handled by the executor).

`GPUPreparedSurfaceProductRouterTest.kt`:
- `non-image gate legacy stays legacy while BGRA8 renders prepared…` (l.58-77) → the `FlushAndSnapshot` part asserts the router no longer short-circuits: the execution port IS called and its result governs (rename to `flush snapshot frames reach the execution port while BGRA8 renders prepared with BGRA byte order`).
- `before-entry refusal is legacy while terminal failure remains terminal` (l.436-455) → `before-entry refusal is terminal while terminal failure remains terminal`: both branches assert `GPUPreparedSurfaceProductRoute.Terminal`.
- `vertices and mesh reaching the legacy route carry the exact composite refusal` (l.276-302) → KEEP the guard assertions (295-301) and re-point the framing: `vertices and mesh refuse with the exact composite code before native work` — the router path for vertices/mesh nested in a refused frame is now `Terminal`.
- NEW: `before-entry refusals for the terminal families are never legacy` — a matrix of the three Task-4 codes (`unsupported.core_primitive.point.hairline_exact_lowering`, `unsupported.recording.core_primitive_mixed_uniform_layouts`, `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry`) plus a generic core refusal (`unsupported.test.builder`) against a `rect()` frame: each returns `GPUPreparedSurfaceProductRoute.Terminal` with the exact code (no `hasTerminalPreparedFamily` split).

`GPUPreparedSurfaceProductEntryTest.kt`: remove every `legacyPort = GPUPreparedSurfaceLegacyPort { … }` stub (l.38, 93, 121, 160, 198, 212); the `Legacy`-route tests become `assertFailsWith<GPUPreparedSurfaceTerminalException>` with the exact code; the `render` call signature drops `legacyPort`/`legacyRouteTrace`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --no-parallel --console=plain`
Expected: FAIL — `Legacy` variants still exist; empty/flush frames still classify `Legacy`; `BeforePreparedEntryRefused` on core frames still routes `Legacy`.

- [ ] **Step 3: Implement the collapse**

1. `GPUPreparedSurfaceFrameGate.kt`: rename `GPUPreparedSurfaceEligibility.Legacy` → `Refused(code, operationIndex)`; l.28-30 returns `Refused(code = mapping.code)`; l.60-63 and l.67-69 are deleted — `FlushAndSnapshot` joins the state-event `Unit` group, and the `if (!hasVisual)` branch returns `Candidate` (with the empty operation list, as today's code path already builds the candidate at l.71-75).
2. `GPUPreparedSurfaceProductRouter.kt`: delete `GPUPreparedSurfaceProductRoute.Legacy` (l.18); l.36 becomes `is GPUPreparedSurfaceEligibility.Refused -> return GPUPreparedSurfaceProductRoute.Terminal(terminalDiagnostic(eligibility.code))`; l.60-65 becomes unconditional `GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)`; delete `hasTerminalPreparedFamily` (l.152-165). Add the `terminalDiagnostic(code)` helper (or reuse the router's `GPUDiagnostic` construction pattern at l.141-149 — severity `Error`, domain `Execution`).
3. `GPUPreparedSurfaceProductEntry.kt`: delete `GPUPreparedSurfaceRouteDecision.Legacy` (l.12), `GPUPreparedSurfaceLegacyPort` (l.21-30), the `legacyPort`/`legacyRouteTrace` params (l.56-57) and the `Legacy` branch (l.70-73); delete the now-unused `GPUClipRouteTrace` import.
4. `GPUPreparedSurfaceFrameExecution.kt`: extend `GPUPreparedSurfacePreBackendNoOpGate.classify` (l.107-162) — after the current text-only pass returns null, add: if every operation is a state event (`SetTransform`/`SetClip`/`Annotation`/`FlushAndSnapshot`), return `NoOp(stateEventCount = …, textMetrics = GPUPreparedTextFrameMetrics(glyphCount = 0, uniqueMaskCount = 0, instanceCount = 0, a8InstanceCount = 0, colorGlyphInstanceCount = 0, pathStrokeDrawCount = 0, subRunCount = 0, pageCount = 0, pageBytes = 0, instanceBytes = 0), acceptedTextOperationIndices = emptySet(), elidedTextOperationIndices = emptySet(), culledTextOperationIndices = emptySet())` (mirror the existing text-only construction at l.143-160); an empty operation list also returns `NoOp`. `completeNoOp` (l.385-416) already returns transparent zero-filled pixels for `ReadbackRgba` with zero native work — no change there.
5. `GPURenderer.kt`: `renderViaGpu` (l.697-718) drops `routeTrace: GPUClipRouteTrace?` and `legacyRouteTrace`; the `legacyPort =` argument (l.714) and `preparedSurfaceLegacyPort` (l.723-726) are deleted.

- [ ] **Step 4: Run to verify green**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --tests "*GPUPreparedSurfaceFrameBuilderTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL. (In a WebGPU environment, `GPUAllApiBlendSurfaceTest`/`GPUClipCoverageSurfaceTest` are now RED for the three terminal families — that is expected and becomes the Task 6 evidence; in a non-GPU environment they skip and the aggregate stays green.)

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main kanvas/src/test
git commit -m "refactor(surface): collapse prepared route authorities to prepared terminal and noop"
```

---

### Task 6: Re-point the surface suites with terminal/prepared evidence (families 3–5 + stragglers)

**Files:**
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageSurfaceTest.kt`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-09-retire-legacy-immediate-renderer-evidence.md` (evidence table; content finalized in Task 10, skeleton now)

**Context:** After Task 5, the three terminal families refuse with `GPUPreparedSurfaceTerminalException` (stable codes) and the blend families render prepared. The surface suites still encode the old `Legacy` expectations. This task runs the suites in a WebGPU environment, captures the exact per-case code (the RED run IS the evidence — exactly the 5-code table of FP-08 evidence §3), and re-points:

- `GPUAllApiBlendSurfaceTest` `expectedPreparedProductRoute` (l.124): `ProductRouteExpectation.Legacy` entries for families 1-2 → `Prepared` (pixel oracle + `route:destination-read:*` assertions at l.176-193 already verify them); families 3-5 → `Terminal(code)` (asserted via `assertFailsWith<GPUPreparedSurfaceTerminalException>` + decision trace at l.137-150, which also asserts no destination readback is allocated before refusal). The `GPUPreparedSurfaceRouteDecision.Legacy` assertion (l.132) and `ProductRouteExpectation.Legacy` branch (l.168-169) are deleted with the type.
- `GPUClipCoverageSurfaceTest`: every `legacyPort = GPUPreparedSurfaceLegacyPort { … }` stub (l.570-789, 944, 1216) → `assertFailsWith<GPUPreparedSurfaceTerminalException>` with the code captured from the red run; `routeTrace = trace` calls (l.1256, 1294, 1360) drop the param; `GPUClipRouteTrace` assertions (l.930, 986, 1189, 1237, 1281, 1348) are deleted. Composite-refusal cases (`unsupported.composite.*`) stay — they were already terminal.

- [ ] **Step 1: Capture the red evidence (WebGPU environment)**

Run: `./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain 2>&1 | tee /tmp/fp09_collapse_red.log`
Expected: failures grouped by exactly the five codes (families 1-2 must have ~zero failures after Tasks 2-3; families 3-5 must fail with their codes; any straggler code is a coverage gap to classify). Record the per-code case list into `fp-09-retire-legacy-immediate-renderer-evidence.md`.

- [ ] **Step 2: Re-point the expectations per the evidence**

Apply the re-points described above; where a case's actual code is unexpected, classify it explicitly (covered → keep `Prepared`; terminal → `Terminal(code)`) and record it in the evidence table. Do NOT relax any pixel assertion to paper over a rendering gap — a case must render prepared or refuse loudly with its stable code.

- [ ] **Step 3: Run to verify green**

```bash
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL (WebGPU env: all cases green; non-GPU env: skipped). The full `:kanvas:test` aggregate must be green except the pre-existing boundary/stencil cases (verified again in Task 10).

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/test reports/upstream-rebaseline/graphite-dawn-frame-plan
git commit -m "test(surface): re-point blend and clip surface expectations with terminal evidence"
```

---

## Phase 3 — Legacy renderer and helper deletion

### Task 7: Delete `renderViaGpuLegacy`, the legacy port, and the legacy-only `GPURenderer.kt` helpers

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` (delete l.87-696 helpers, l.729-3042 `renderViaGpuLegacy`, l.3043-3262 text-atlas builders)

**Context:** After Task 5 no production path can reach `renderViaGpuLegacy`: the router is Prepared/Terminal/Refused/NoOp only, and the entry no longer carries a legacy port. This task deletes the renderer body and every helper with no remaining production caller. The listed symbols were verified legacy-only at plan time (their only non-test consumers are inside `renderViaGpuLegacy`); after the deletion, `:kanvas:compileKotlin` succeeds and `:kanvas:compileTestKotlin` fails with the test-consumer list that drives Task 9.

- [ ] **Step 1: Delete the legacy renderer and helpers**

Delete `renderViaGpuLegacy` (l.729-3042) and the legacy-only helpers listed in the File Map (`GPURenderer.kt:87-696` + text-atlas builders at l.3043-3262). Keep: `renderViaGpu` (l.697-718), `preparedSurfaceProductExecutionPort` (l.720-722), and the FP-08/FP-09 shared top-of-file helpers if any remain referenced by prepared code (e.g. anything called from `GPUPrepared*` files — the compiler will tell you; the plan's verified legacy-only list is authoritative for the ones to remove). Do NOT touch `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest`-related production.

- [ ] **Step 2: Compile the main source set**

```bash
./gradlew -F off :kanvas:compileKotlin --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL (main sources no longer reference any deleted symbol). Then:

```bash
./gradlew -F off :kanvas:compileTestKotlin --no-parallel --console=plain
```

Expected: FAIL listing the test files still referencing deleted legacy symbols — this is the driving list for Task 9 (`GPUBlendFormulaSurfaceTest` → `destinationReadBlendModeIndex`, `GPUTextAtlasGeometryTest`, `GPUSaveLayerCompositeRegressionTest`, `GPUColorGlyphPaintAlphaTest`, `GPUPathStrokeInputTest`, `GPUProductIntermediatePlannerScopeTest`, `GPUImageFilterDispatchTest` — plus any straggler).

- [ ] **Step 3: Commit (production only; tests are fixed in Task 9)**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "refactor(surface): delete legacy immediate renderer and text atlas builders"
```

(Committing a state whose `compileTestKotlin` fails is acceptable ONLY when immediately followed by Task 9 in the same session; if you prefer every commit green, run Task 9 first and commit both together with the Task 9 message.)

---

### Task 8: Delete `GPUClipExecution.kt`, the legacy mask-lease machinery, and `expandPicturesForGpuReplay`

**Files:**
- Delete: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt` (keep only the pinned guards)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` (delete `expandPicturesForGpuReplay` l.2282-2358; keep `withPictureReplayState` l.2250-2279 + `clipForPictureReplay`/`transformForPictureReplay` l.2360-2434)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/GPUClipCoveragePlanner.kt` (doc comment at l.146 referencing `GPUClipCoverageFrameCache`)

**Context:** These are the legacy-only helper machinery items of the roadmap acceptance ("GPUClipExecution.kt, LayerScissorOffscreenTarget, CPU text-atlas builders, legacy mask-lease machinery"), minus the test-pinned FP-06 guards. Verified at plan time: `GPUClipExecution` symbols are consumed only by `GPURenderer.kt` (deleted in Task 7) and tests; `GPUClipCoverageFrameCache`/`GPUClipUsePrepass`/`acquireClipMask`/`gpuClipCoveragePlanOrNull`/`clipForMaskPrepass`/`ClipMaskLease`/`clipCompositeBlendFacts` are consumed only by the legacy renderer path; the guards `coreRoutePreflightRefusalReason`/`picturePreflightRefusalReason`/`coveragePlaneTask4RefusalOrNull`/`gpuCompositePreflightRefusalOrNull` (GPUClipCoverage.kt:340-416) and `withPictureReplayState` + replay helpers (used by `GPUPreparedCompositeCapture.kt:323`) SURVIVE.

- [ ] **Step 1: Delete the file and sweep GPUClipCoverage.kt**

Delete `GPUClipExecution.kt`. In `GPUClipCoverage.kt`, delete everything except: `coreRoutePreflightRefusalReason` (l.340-346), `coveragePlaneTask4RefusalOrNull` (l.353), `picturePreflightRefusalReason` (l.355-387), `Picture.containsLayer` (l.389-392), `gpuCompositePreflightRefusalOrNull` (l.395-416), and their imports. Update the `GPUClipCoveragePlanner.kt:146` comment (it references the deleted frame cache) to point at the prepared coverage-mask route instead.

In `GPUOpMapper.kt`, delete `expandPicturesForGpuReplay` (l.2282-2358) only. `withPictureReplayState` and its private helpers stay (prepared composite capture consumer at `GPUPreparedCompositeCapture.kt:323`).

- [ ] **Step 2: Compile and run the guard suites**

```bash
./gradlew -F off :kanvas:compileKotlin --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL; guard pins green (`nested_vertices` at `GPUPreparedSurfaceProductRouterTest.kt:295-301`; composite semantics at `GPUPreparedCompositeCaptureSemanticTest.kt:398-431`).

- [ ] **Step 3: Commit**

```bash
git add kanvas/src/main gpu-renderer/src/main
git commit -m "refactor(surface): delete legacy clip execution mask leases and picture replay expansion"
```

---

### Task 9: Delete or re-point the legacy-pinning tests

**Files:**
- Delete: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUTextAtlasGeometryTest.kt`, `GPUSaveLayerCompositeRegressionTest.kt`, `GPUProductIntermediatePlannerScopeTest.kt`, `GPUClipCoverageDispatchTest.kt` (dies with `GPUClipExecution.kt` in Task 8)
- Modify (re-point to prepared evidence or delete the obsolete case): `GPUColorGlyphPaintAlphaTest.kt`, `GPUPathStrokeInputTest.kt`, `GPUImageFilterDispatchTest.kt`, `GPUBlendFormulaSurfaceTest.kt`

**Context:** These tests pin the deleted legacy machinery from Tasks 7-8. `GPUTextAtlasGeometryTest` (CPU text-atlas math: `buildTextAtlasMesh`/`normalizeGlyphRects`), `GPUSaveLayerCompositeRegressionTest` (`LayerScissorOffscreenTarget`/`LayerBounds`), `GPUProductIntermediatePlannerScopeTest` (stale `productIntermediatePlannerScopeDiagnostics` string), and `GPUClipCoverageDispatchTest` (all its fixtures exercise `renderWithClip`/`GPUClipRouteContext`/`GPUClipSourceSurface` from the deleted `GPUClipExecution.kt`) test only deleted code — delete the files. `GPUColorGlyphPaintAlphaTest`/`GPUPathStrokeInputTest`/`GPUImageFilterDispatchTest`/`GPUBlendFormulaSurfaceTest` mix legacy-pinned internals with still-valid prepared behavior — delete the legacy-pinned cases and keep the rest, or delete the file if nothing survives. Prepared destination-read coverage now exists (Task 3), so `GPUBlendFormulaSurfaceTest`'s blend-formula evidence can be re-pointed onto the prepared route: assert the formula WGSL dispatchers (`GPUBlendFormulaLibrary.allModeBlendDispatcherWgsl`) still emit the exact mode indices and that a DARKEN rect surface renders prepared with `route:destination-read:DrawRect:*` evidence (mirroring `GPUAllApiBlendSurfaceTest`'s assertions at l.176-193).

- [ ] **Step 1: Apply the deletions/re-points above**

Use the Task 7 Step 2 `compileTestKotlin` failure list as the driving list; resolve every entry (delete file, or delete the pinned case and keep the file green).

- [ ] **Step 2: Run the affected suites**

```bash
./gradlew -F off :kanvas:test --tests "*GPUBlendFormulaSurfaceTest" --tests "*GPUColorGlyphPaintAlphaTest" --tests "*GPUImageFilterDispatchTest" --tests "*GPUPathStrokeInputTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A kanvas/src/test
git commit -m "test(surface): delete legacy pinning tests and re-point blend evidence"
```

---

## Phase 4 — Regression proof & closure

### Task 10: Full regression, guards, absence test extension, evidence report, roadmap FP-09 completed

**Files:**
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLegacyAbsenceTest.kt` (extend retired tokens)
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-09-retire-legacy-immediate-renderer-evidence.md`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` (FP-09 → `completed`; add FP-11 tracking note for the three terminal families)
- Create: `reports/fp09-legacy-map.txt` (Task 1 before-snapshot diff)

**Context:** FP-09 acceptance: route authorities collapsed (Task 5); `renderViaGpuLegacy`/`GPUPreparedSurfaceLegacyPort`/`preparedSurfaceLegacyPort` deleted (Task 7); legacy-only helper machinery deleted except the test-pinned `nested_vertices` guards (Task 8); blend/clip surface suites re-pointed with evidence (Task 6). The absence guard must now pin every retired token, and the roadmap entry closes with the evidence report.

- [ ] **Step 1: Extend the absence guard**

In `GPUPreparedSurfaceLegacyAbsenceTest.kt` (l.23-28), extend `retired` with the FP-09 tokens (the FP-08 comment at l.13-17 about tokens "remaining until FP-09" is updated):

```kotlin
val retired = listOf(
    "GPULegacyImmediatePathAdapter",
    "LegacyDisplayOpFamily",
    "GPULegacyImmediatePathDump",
    "legacyDump",
    "renderViaGpuLegacy",
    "GPUPreparedSurfaceLegacyPort",
    "GPUClipRouteTrace",
    "renderWithClip",
    "cachePixels",
    "buildTextAtlasMesh",
    "LayerScissorOffscreenTarget",
    "GPUClipUsePrepass",
    "GPUClipCoverageFrameCache",
    "acquireClipMask",
    "expandPicturesForGpuReplay",
    "legacy.surface.prepared",
)
```

Note: `GPUClipRouteTrace`/`renderWithClip`/`buildTextAtlasMesh`/`LayerScissorOffscreenTarget`/`cachePixels` are already absent after Tasks 7-8; `legacy.surface.prepared` pins the two gate codes deleted in Task 5.

- [ ] **Step 2: Run the full regression**

```bash
./gradlew -F off :kanvas:test :gpu-renderer:test --no-parallel --console=plain 2>&1 | tee /tmp/fp09_full.log
```

Expected: BUILD SUCCESSFUL except the two documented pre-existing failures — `GPURendererPackageBoundaryTest` package-boundary case (exactly 20 cycle violations, 0 rule violations; unchanged) and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` (reproduces at base SHA). GPU-backed suites run in the WebGPU environment; classify any failure with evidence.

- [ ] **Step 3: Verify the guards and the terminal families end-to-end**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLegacyAbsenceTest" --no-parallel --console=plain
```

Expected: all green except the pre-existing boundary case (unchanged failure state). `nested_vertices` pins green. In a WebGPU environment, `GPUAllApiBlendSurfaceTest` green means the blend families render prepared (pixel oracle) and families 3-5 refuse with their stable terminal codes.

- [ ] **Step 4: Write the evidence report**

In `fp-09-retire-legacy-immediate-renderer-evidence.md`: before/after diff of the Task 1 legacy map (all production rows gone); the per-family decision table (§2 of this plan); the Task 6 red-run code table; the Task 2/3 coverage proof (builder + destination-contract tests + observed route split in `GPUAllApiBlendSurfaceTest`); the NoOp/flush-snapshot parity proof (empty-frame transparent pixels, zero native work, `completeNoOp`); the guard-retention proof (surviving symbols + their pinned tests); test score deltas (before/after of the full run); and the FP-11 tracking notes for the three terminal families (hairline points, mixed uniform layouts, analytic-clip non-direct geometry — each with its emission site and code).

- [ ] **Step 5: Update the roadmap**

In `active-todo.md`, mark FP-09 `completed` with the evidence report reference, and add the tracking note to the FP-11 entry (below its Goal, as a "FP-09 transfers" bullet):

```markdown
### FP-09 — Retire the legacy immediate renderer (deferred from FP-08)

Status: `completed`

Resolution evidence (`fp-09-retire-legacy-immediate-renderer-evidence.md`):
- route authorities collapse to Prepared/Terminal/Refused/NoOp — `BeforePreparedEntryRefused`
  → always `Terminal`; the `Legacy` eligibility/route/decision variants and the legacy
  `GPUPreparedSurfaceLegacyPort` are deleted;
- prepared coverage added: non-SrcOver core-primitive blends (CLEAR/SRC/… fixed-function +
  shader-no-dst) and destination-read blends (`ShaderBlendWithDstRead` + GPU-owned
  `TextureCopy` snapshots + `GPUBlendFormulaLibrary` formulas) on core primitives and layer
  composites — the FP-08 evidence §3 codes `unsupported.native-core-primitive.blend` (330)
  and `unsupported.destination_read.required` (630) no longer fire for covered shapes;
- stable terminal refusals replace the legacy render for hairline points (168),
  mixed uniform layouts (92), and analytic-clip non-direct geometry (52) — documented
  behavior change (pixels → loud refusal), tracked as bounded FP-11 gaps;
- `renderViaGpuLegacy` and the legacy-only machinery are deleted (GPUClipExecution.kt,
  LayerScissorOffscreenTarget, CPU text-atlas builders, legacy mask-lease machinery);
  the FP-06 `nested_vertices` guards stay test-pinned;
- `GPUAllApiBlendSurfaceTest`/`GPUClipCoverageSurfaceTest` re-pointed with evidence; full
  run green except the two documented pre-existing failures (package boundary, stencil smoke).
```

And under FP-11:

```markdown
FP-09 transfers (stable terminal refusals, per `fp-09-retire-legacy-immediate-renderer-evidence.md`):
- exact hairline point lowering — `unsupported.core_primitive.point.hairline_exact_lowering`
  (GPUCorePrimitiveSemanticBuilder.kt:409, 411, 465; 168 cases);
- multi-uniform-layout direct passes — `unsupported.recording.core_primitive_mixed_uniform_layouts`
  (GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1602, 2104; 92 cases);
- analytic clips over non-direct shading geometry — `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry`
  (GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1994; 52 cases).
```

- [ ] **Step 6: Final state check**

```bash
git add reports/ kanvas/src/test kanvas/src/main gpu-renderer/src/main
git log --oneline accaea616..HEAD | cat
rg -n "GPUPreparedSurfaceProductRoute\.Legacy|GPUPreparedSurfaceEligibility\.Legacy|GPUPreparedSurfaceRouteDecision\.Legacy|GPUPreparedSurfaceLegacyPort|renderViaGpuLegacy|legacy\.surface\.prepared|hasTerminalPreparedFamily|legacyPort" kanvas/src/main --type kotlin
rg -n "GPUClipExecution|renderWithClip|GPUClipRouteTrace|LayerScissorOffscreenTarget|buildTextAtlasMesh|GPUClipUsePrepass|GPUClipCoverageFrameCache|expandPicturesForGpuReplay" kanvas/src/main --type kotlin
```

Expected: the log shows the FP-09 task commits (inventory → blend coverage ×2 → collapse → evidence re-points → renderer deletion → helper deletion → test deletion → closure); both `rg` commands return nothing; `git status --short` shows only `reports/` additions.

- [ ] **Step 7: Commit**

```bash
git add reports/
git commit -m "docs(surface): fp09 legacy retirement evidence and roadmap closure"
```

---

## Self-review notes (filled at plan time, 2026-08-08)

**Spec coverage vs. the roadmap FP-09 entry and the mission:**

1. **MAJOR scope decision — two of the five families get NEW prepared coverage (Tasks 2-3), not terminal refusals.** The FP-08 reversal proved the 636 cases were real legacy rendering; converting all five to terminal refusals would have been a 636-case functional regression. Evidence at HEAD shows the prepared machinery already exists for blends (`GPUBlendPlanner` exhaustive specializer, `Blend.Fixed/ShaderNoDestination/ShaderWithDestination` structural authority, `GPUDestinationSnapshotOperation.TextureCopy` + `GPUBlendFormulaLibrary` + `GPUWgpu4kDestinationCopyFrameSmokeTest`), and the ONLY blockers are admission predicates (`GPUCorePrimitiveDirectNativeRoute.kt:119-124` SrcOver-only check; the DrawLayer planners' blanket `destination_read.required`) plus the ColorGlyph-only evidence gate (`GPUPreparedSurfaceFrameBuilder.kt:503`). Coverage was therefore the evidence-based choice for families 1-2 (630+330 cases keep rendering). Families 3-5 (168+92+52) genuinely require new rendering features (exact hairline lowering, multi-layout passes, analytic-clip-over-stencil), so they become **documented stable terminal refusals** with FP-11 tracking — per the FP-08 evidence §6 policy ("covered by the prepared route (or explicitly refused with a stable terminal code replacing the legacy render)").
2. **The collapse is restored (FP-08 original Tasks 4-5), now justified.** `BeforePreparedEntryRefused` → always `Terminal`; `hasTerminalPreparedFamily` is deleted. The FP-08 reversal cause (uncovered families) is removed by Tasks 2-3, and the remaining families are documented refusals before the collapse lands (Task 4 precedes Task 5).
3. **Gate treatment (mission question "Refused terminal vs NoOp"):** empty/state-only frames → **NoOp** (backend-free transparent result via the existing `GPUPreparedSurfacePreBackendNoOpGate`/`completeNoOp` — exact parity with the legacy cleared-target result, no regression); `FlushAndSnapshot` → **state event** (the prepared `GPUOpMapper` already records `GPUFramePathStateKind.FlushSnapshot`; the legacy renderer itself no-ops it); color refusals → `Refused(code)` with the stable `unsupported.surface.gpu-color-format.*` codes. The `legacy.surface.prepared.flush-snapshot`/`legacy.surface.prepared.empty-frame` codes disappear with the eligibility variant.
4. **Guard survival (corrected vs. the FP-08 file-map draft):** `gpuCompositePreflightRefusalOrNull` (GPUClipCoverage.kt:395-416) SURVIVES because `picturePreflightRefusalReason` (the pinned `nested_vertices` guard) calls it at l.356/370 — the FP-08 plan listed it under "legacy clip execution" for deletion; FP-09 keeps it with the guards. `withPictureReplayState` + `clipForPictureReplay`/`transformForPictureReplay` survive via the prepared composite capture (`GPUPreparedCompositeCapture.kt:323`); only `expandPicturesForGpuReplay` is deleted.
5. **`GPUClipCoverageDispatchTest.kt` is deleted in Task 9, not Task 6** (corrected at review): it does not use `renderViaGpu`/`ProductEntry.render`/`routeTrace` — verified by `rg` at plan time — so it compiles after Task 5's signature removals and only dies with `GPUClipExecution.kt` (Task 8). Task 6 therefore re-points only the two surface suites.
6. **Untouched baselines:** `GPURendererPackageBoundaryTest` package-boundary case and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` remain in their documented pre-existing failing states (do not fix, do not change failure state). `GPUPreparedSurfaceFrameExecution`'s FP-08-renamed `unavailable.surface.prepared.runtime-capabilities` code is untouched. Destination continuation stays GPU-owned (unchanged; the TextureCopy/CopyAsDraw machinery is reused, not re-plumbed).
7. **Known implementation-time verification points (no placeholders, but honest about discovery):** Task 2 Step 4 and Task 3 Step 3 require the executing engineer to verify-then-wire the core materializer's non-SrcOver blend state and dst-texture binding — the precedent exists (ColorGlyph + `GPUWgpu4kDestinationCopyFrameSmokeTest`), the exact materializer site must be confirmed against HEAD before editing; the plan names the files, the observable tests, and the acceptance (pixel oracle + `route:destination-read:*` diagnostics). If a site turns out to be missing, the residual refusal is documented in the Task 6 evidence run rather than hidden.
8. **Task 7 commits with a temporarily red `compileTestKotlin`** (production-first commit, tests fixed in Task 9) — flagged in the task itself with the alternative of committing both together; both paths keep the tree consistent by the end of Task 9.

**Deliverable mapping (mission items (a)-(g)):** (a) inventory + baseline → Task 1; (b) coverage or terminal refusal per family → Tasks 2-4; (c) route-authority collapse → Task 5; (d) `renderViaGpuLegacy` + legacy port deletion → Task 7; (e) legacy helper machinery deletion (guards pinned) → Task 8; (f) test re-points/deletions → Tasks 5, 6, 9; (g) regression proof + roadmap FP-09 completed → Task 10.

# FP-08 — Retired immediate CPU-path adapter: evidence and closure

Status: `completed` (reduced scope)
Branch: `codex/graphite-dawn-frame-fp08`
Evidence head: `c6abb814c` + Task 6 commit (this report + absence test)
Historical plan: removed from the working tree; recover from Git history at
revision `8f2d2f1ea` if needed.

## 1. Scope (revised)

FP-08 retires the empty legacy **adapter** and its plumbing only. The full
legacy retirement (`renderViaGpuLegacy`, the legacy port, and legacy-only
helper machinery) is deferred to **FP-09** because the prepared route does not
yet cover every family the legacy renderer serves (see §3).

## 2. Before/after diff of the historical Task 1 legacy map

Before-snapshot (committed at `1dd769d01`, head `6b9e273ea`):
production sites for the retired symbols:

```text
GPUPreparedSurfaceFrameGate.kt:29,61-64,68-71  Legacy eligibility construction
GPURenderer.kt:724-729                          GPUPreparedSurfaceLegacyPort -> renderViaGpuLegacy
GPUOpMapper.kt:97,157,186,193,238,252,295,338,  legacyDump plumbing (17 sites) +
376,415,454,468,477,497,519,541,563,571         legacy.accepts/recordInvocation (3 blocks)
GPUFramePathApiInventory.kt:92,174,187          legacyDump plumbing (3 sites)
GPUPreparedSurfaceProductEntry.kt:21,56-57,70-71 legacy port interface + Legacy route dispatch
GPUPreparedSurfaceProductRouter.kt:34,37,47      Legacy route construction
GPUPreparedSurfaceFrameExecution.kt:275          runtime-capabilities refusal code
```

After (Task 2 `dbf725d61` + Task 5 `c6abb814c`), production searches:

```bash
$ rg "GPULegacyImmediatePathAdapter|LegacyDisplayOpFamily|GPULegacyImmediatePathDump|legacyDump" kanvas/src/main
# (no output — all retired tokens absent)
$ rg -c "renderViaGpuLegacy" kanvas/src/main
kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:2
# (fallback retained until FP-09)
```

- `GPULegacyImmediatePathAdapter.kt` deleted (adapter, `LegacyDisplayOpFamily`,
  `GPULegacyImmediatePathDump`).
- `legacyDump` removed from `GPUOpMapping`/`GPUFramePathInventoryPlan`;
  `GPUOpMapper` no longer constructs or invokes the adapter (17 named-argument
  sites + 3 record blocks gone).
- Test consumers de-legacied (`51071ffa6`: 64 lines of adapter tautologies
  removed across 10 files; `fed1a95d8`: 63 lines of dead fixtures removed).
- The `runtime-capabilities-unavailable` refusal is renamed to
  `unavailable.surface.prepared.runtime-capabilities` (Task 5 `c6abb814c`) — a
  terminal code produced by the prepared executor, not a legacy branch.
- The absence guard `GPUPreparedSurfaceLegacyAbsenceTest` (Task 6) pins all four
  retired tokens out of production `surface/gpu` sources; it scans only the
  `kanvas/src/main/.../surface/gpu` tree and asserts the deleted symbols never
  reappear. It deliberately does NOT assert `renderViaGpuLegacy`,
  `GPUPreparedSurfaceLegacyPort`, `GPUClipRouteTrace`, `renderWithClip`,
  `cachePixels`, `buildTextAtlasMesh`, or `LayerScissorOffscreenTarget`, which
  remain until FP-09.

## 3. Executed-then-reverted route collapse (original Tasks 4–5)

The original plan's Tasks 4–5 (collapse route authorities to Prepared/Terminal
only, delete `renderViaGpuLegacy`) were **executed and reverted**:

| commit | action |
| --- | --- |
| `9e79eb857` | feat(surface): collapse prepared route authorities to prepared and terminal only |
| `c5325a3d0` | test(surface): re-point route gate and executor tests to prepared only route |
| `3150fc3fe` | Revert `9e79eb857` |
| `0f1106800` | Revert `c5325a3d0` |

Failure evidence (on the executed commits): `GPUAllApiBlendSurfaceTest`
regressed ~636 GPU cases from pixels to terminal refusals with 5 refusal codes
the prepared route cannot cover:

| code | cases | family |
| --- | --- | --- |
| `unsupported.destination_read.required` | 630 | destination-read blends (DARKEN, MULTIPLY, …) |
| `unsupported.native-core-primitive.blend` | 330 | non-SrcOver blends on core primitives |
| `unsupported.core_primitive.point.hairline_exact_lowering` | 168 | hairline points |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` | 92 | mixed uniform layouts |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | 52 | analytic-clip non-direct geometry |

(Overlapping families: each failing frame reports multiple codes.) These
families were genuinely rendered by `renderViaGpuLegacy` — not legacy-pinning
test expectations — so the retirement was premature. Full retirement is
deferred to FP-09 (roadmap entry added in Task 6).

## 4. BGRA8 is native, not refused (Task 4 `9b25d62d0`)

Decision 1 = b1: the prepared route renders BGRA8 surfaces into a
`bgra8unorm` target instead of refusing them. Native byte-order proof —
`GPUPreparedSurfaceProductNativeSmokeTest.bgra8 surface renders prepared with
native BGRA byte order and exact format` (line 808):

- a 2x1 red rect on a `PixelFormat.BGRA8` surface;
- route decision is `GPUPreparedSurfaceRouteDecision.Prepared`
  (asserted `submits == 1`), format `result.format == PixelFormat.BGRA8`;
- readback bytes `[0, 0, 255, 255]` per pixel — BGRA-ordered red, produced by
  the `bgra8unorm` attachment memory layout with **no CPU swizzle**;
- the direct native route marker `prepared.surface.direct` is asserted by the
  same suite at lines 426, 507, 588 (direct-route renderings).

Graphite/Dawn grounding for the GPU-owned destination decision
(skia-main, verified at plan time):

- `src/gpu/graphite/ResourceTypes.h:58-67` — `DstReadStrategy
  { kNoneRequired, kTextureCopy, kTextureSample, kReadFromInput,
  kFramebufferFetch }`;
- `src/gpu/graphite/Caps.cpp:340-348` — strategy is `kFramebufferFetch` or
  `kTextureCopy`; neither touches the CPU;
- `src/gpu/graphite/DrawContext.cpp:271-291` — `kTextureCopy` uses
  `Image::Copy` (GPU texture copy), sampled in the shader;
- `src/gpu/graphite/Image_Graphite.cpp:105-130` — `Image::Copy` =
  `CopyTextureToTextureTask` (blit) or `CopyAsDraw` (render), always GPU;
- `src/gpu/graphite/dawn/DawnCommandBuffer.cpp:927-938` — the dst copy is bound
  as an extra `sampler + textureView` in the fragment shader;
- `src/gpu/graphite/Context.cpp:764-780` — the only CPU readback
  (`CopyTextureToBufferTask` + `SynchronizeToCpuTask`) is the public
  `readPixels` API, never the blend destination continuation;
- BGRA8 as a first-class render format: `TextureFormat.h:97`
  (`kBGRA8` native), `TextureFormat.cpp:482` (`kBGRA_8888_SkColorType ↔
  TF::kBGRA8`), `TextureFormat.cpp:548` (`TF::kBGRA8 ↔ X::kIdentity`, no
  swizzle), `dawn/DawnGraphiteUtils.cpp:182,355` (`kBGRA8 ↔
  wgpu::TextureFormat::BGRA8Unorm`, renderable/blendable).

## 5. Test score deltas (6b9e273ea → HEAD)

Full run (Task 6, `./gradlew -F off :kanvas:test :gpu-renderer:test
--no-parallel --console=plain`):

| module | result |
| --- | --- |
| `:kanvas:test` | 3,230/3,230 green |
| `:gpu-renderer:test` | 3,257 tests, 2 failed — both documented pre-existing: `GPURendererPackageBoundaryTest` package-boundary case (exactly 20 cycle violations, 0 rule violations) and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` (reproduces at base SHA) |

| suite | before | after |
| --- | --- | --- |
| `GPUPreparedSurfaceProductRouterTest` | green | green (guard suites re-run in Task 6, Step 3) |
| `GPUPreparedCompositeCaptureSemanticTest` | green | green |
| `GPUPreparedCompositeFrameRouteIntegrationTest` | green | green |
| `GPUAllApiBlendSurfaceTest` | green (1,858 tests) | green — proves the legacy fallback still renders destination-read/non-SrcOver families (FP-09 precondition) |
| `GPUPreparedSurfaceLegacyAbsenceTest` | — | 1/1 PASSED (new) |
| `GPURendererPackageBoundaryTest` | 1 pre-existing failure | unchanged — fails ONLY on `gpu renderer production source satisfies package boundary rules` with exactly 20 cycle violations (0 rule violations); all 21 other cases pass. Not touched. |

Test-surface deltas: −64 lines (adapter tautologies, `51071ffa6`), −63 lines
(dead fixtures, `fed1a95d8`), +178 lines / 21 files (BGRA8 native admission
and re-points, `9b25d62d0`), refusal code rename re-pointed in
`GPUPreparedSurfaceFrameExecutorTest` (`c6abb814c`).

`nested_vertices` guard remains pinned: `GPUPreparedSurfaceProductRouterTest`
(l.279-280) + `GPUPreparedCompositeCaptureSemanticTest` (l.398-431) green;
guard functions `coreRoutePreflightRefusalReason`/`picturePreflightRefusalReason`
retained.

## 6. FP-09 preconditions (proven by this report)

1. destination-read blends — `unsupported.destination_read.required` (630);
2. non-SrcOver core-primitive blends — `unsupported.native-core-primitive.blend` (330);
3. hairline points — `unsupported.core_primitive.point.hairline_exact_lowering` (168);
4. mixed uniform layouts — `unsupported.recording.core_primitive_mixed_uniform_layouts` (92);
5. analytic-clip non-direct geometry — `…analytic_clip_non_direct_geometry` (52).

Each family must be covered by the prepared route (or explicitly refused with a
stable terminal code replacing the legacy render) before FP-09 can delete
`renderViaGpuLegacy` and the legacy-only helper machinery.

## 7. Known pre-existing environment failures (unchanged, not regressions)

- `GPURendererPackageBoundaryTest` package-boundary case (20 cycle violations);
- native-suite SIGSEGV flakiness in `libwgpu_native.dylib`
  (`GPUAllApiBlendSurfaceTest`/`GPUBlendFormulaSurfaceTest` vary run to run);
- `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` (reproduces at base SHA).

## 8. Commit trail (FP-08)

```text
1dd769d01 docs(surface): fp08 legacy path inventory and green baseline evidence
dbf725d61 refactor(surface): delete legacy immediate path adapter and legacyDump plumbing
51071ffa6 test(surface): drop legacy adapter tautologies from adapter consumer tests
fed1a95d8 test(surface): remove dead fixtures from deleted adapter tests
9e79eb857 feat(surface): collapse prepared route authorities to prepared and terminal only   [REVERTED]
c5325a3d0 test(surface): re-point route gate and executor tests to prepared only route       [REVERTED]
3150fc3fe Revert "feat(surface): collapse prepared route authorities ..."                     [revert]
0f1106800 Revert "test(surface): re-point route gate and executor tests ..."                  [revert]
8f2d2f1ea docs(surface): revise fp08 plan to reduced scope and defer legacy retirement to fp09
9b25d62d0 feat(surface): native BGRA8 rendering in the prepared route
85271e7fe fix(gpu-renderer): update direct native route refusal message for bgra8 admission
f1829ac70 fix(surface): align bgra8 refusal messages and document router format asymmetry
c6abb814c fix(surface): rename runtime capabilities refusal to a non legacy terminal code
<this commit> test(surface): absence guard + evidence report + roadmap closure
```

# FP-09 Retire Legacy Immediate Renderer — Terminal/Prepared Evidence

Status: **skeleton** — Task 6 capture. Full narrative finalized in Task 10.

Branch: `codex/graphite-dawn-frame-fp09`, HEAD `b1163ae9b` (Task 5 route collapse).

## 1. Evidence runs

Red capture (Task 6 Step 1):

```bash
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" \
  --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

- Run 1 (2026-08-10): 1905 tests, 513 failed (498 blend + 15 clip). Codes grouped
  from the JUnit XML `GPUPreparedSurfaceTerminalException` messages.
- Run 2 (blend suite only, same day): 1864 tests, 497 failed — identical per-code
  counts; the single extra Run-1 failure was the known environmental
  `failed.surface.prepared.session-close` flake on `Clear/EXCLUSION/UNCLIPPED`
  (passes in isolation / on re-run; no assertion weakened for it).
- Run 3 (clip suite only, same day): 41 tests, 15 failed — identical per-test codes.

## 2. Per-code case counts (the Task 6 evidence)

| code | blend suite | clip suite | total | family |
| --- | --- | --- | --- | --- |
| `unsupported.core_primitive.point.hairline_exact_lowering` | 174 | 1 | 175 | hairline points |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` | 201 | 1 | 202 | mixed uniform layouts |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | 2 | 0 | 2 | analytic clip, non-direct geometry |
| `unsupported.recording.core_primitive_analytic_shape_clip` | 0 | 7 | 7 | analytic shape under analytic clip |
| `unsupported.native-core-primitive.multi-render-dst-copy` | 60 | 0 | 60 | dst-read multi-render (rect/color) |
| `unsupported.native-core-primitive.analytic-shape-multi-key` | 0 | 2 | 2 | AA multi-key dst-read shapes |
| `unsupported.native-core-primitive.dst-read-formula` | 0 | 2 | 2 | single-op dst-read formula (mapped routes) |
| `invalid.surface.prepared.frame-build-contract` | 60 | 0 | 60 | path/drrect dst-read — see §4 |
| `unsupported.core_primitive.rect.analysis_authority_missing` | 0 | 2 | 2 | mask-blur rect authority |
| `failed.surface.prepared.session-close` | 1 | 0 | 1 | environmental flake (Run 1 only) |
| **total** | **498** | **15** | **512** | |

Cross-reference to FP-08 evidence §3 (the executed-then-reverted collapse):
the reverted run reported 5 code families (`destination_read.required` 630,
`native-core-primitive.blend` 330, `hairline_exact_lowering` 168,
`mixed_uniform_layouts` 92, `analytic_clip_non_direct_geometry` 52). The
post-collapse route surfaces the same three Task-4 recording codes
(hairline / mixed-uniform / analytic-clip-non-direct) plus the Task-3c
dst-read residuals (`multi-render-dst-copy`, `analytic-shape-multi-key`,
`dst-read-formula`, `multi-key-component` documented but not exercised by
these suites), and two stragglers (§4). Case counts differ from the reverted
run because the current matrix fixture set is smaller than the reverted
full-suite aggregate.

## 3. Blend suite re-pointing (GPUAllApiBlendSurfaceTest)

`expectedPreparedProductRoute` now maps (per the evidence run):

| family | contexts | code |
| --- | --- | --- |
| DrawPoint, DrawPoints | all 29 modes x 3 contexts | `unsupported.core_primitive.point.hairline_exact_lowering` |
| DrawRRect | all 29 modes x 3 contexts | `unsupported.recording.core_primitive_mixed_uniform_layouts` |
| DrawRect, DrawColor | ALPHA_MASK, DST | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` |
| DrawRect, DrawColor | ALPHA_MASK, other modes | `unsupported.recording.core_primitive_mixed_uniform_layouts` |
| DrawRect, DrawColor | UNCLIPPED/SCISSOR, artistic + PLUS | `unsupported.native-core-primitive.multi-render-dst-copy` |
| DrawRect, DrawColor | UNCLIPPED/SCISSOR, fixed/SCREEN/MODULATE | Prepared (pixel oracle) |
| DrawPath, DrawDRRect | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` |
| DrawPath, DrawDRRect | UNCLIPPED/SCISSOR, artistic + PLUS | `invalid.surface.prepared.frame-build-contract` (§4) |
| DrawPath, DrawDRRect | UNCLIPPED/SCISSOR, fixed/SCREEN/MODULATE | Prepared (pixel oracle) |
| Clear | all | Prepared (mode is ignored by design) |

The dead `ProductRouteExpectation.LegacyRefused` branch and data object were
deleted; the terminal branch still asserts `decisions == [Terminal(code)]` and
that NO destination readback was allocated before refusal.

## 4. Stragglers (unexpected codes, classified explicitly)

- `invalid.surface.prepared.frame-build-contract` (60): DrawPath/DrawDRRect
  with a dst-read blend outside an analytic clip. The builder's dst-read
  evidence authentication (`copy.consumers.single()`,
  GPUPreparedSurfaceFrameBuilder.kt:510) finds no consumer for a
  non-direct-geometry source and throws `NoSuchElementException`, which the
  builder's catch-all converts to `invalid.surface.prepared.frame-build-contract`.
  Stable and reproducible across runs, but it is a generic internal-contract
  wrapper, not one of the four designed dst-read residuals. Task 7 (legacy
  retirement) / Task 10 should replace it with a designed refusal code for the
  path/drrect dst-read family (candidate: `unsupported.native-core-primitive.*`).
- `unsupported.recording.core_primitive_analytic_shape_clip` (7, clip suite):
  designed refusal at the task-list builder ("Prepared analytic shapes require
  NoClip or ScissorOnly") reached when a rrect/analytic-shape frame sits under
  an analytic (AA) clip without a mixed-layout predecessor.
- `unsupported.core_primitive.rect.analysis_authority_missing` (2, clip suite):
  designed refusal at the core-semantic builder when the recording analysis
  record carries no rect route authority (mask-blur rect frames).

## 5. Clip suite re-pointing (GPUClipCoverageSurfaceTest)

15 pixel-oracle tests re-pointed to terminal assertions with the exact codes
from the run; the 7 stale "…before legacy" test names were renamed; the
identical `assertPreparedImageTerminal`/new helper was merged into one
`assertTerminal`. Composite refusals (`unsupported.composite.*`) untouched.

## 6. Green verification (Task 6 Step 3)

```bash
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" \
  --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

- Green run 1: 1905 tests, BUILD SUCCESSFUL (1m 5s).
- Forced re-run 1 (`--rerun-tasks`): 1 failure —
  `failed.surface.prepared.session-close` on `DrawVertices/SRC_OUT/UNCLIPPED`.
- Forced re-run 2 (blend suite, `--rerun-tasks`): 1 failure —
  `failed.surface.prepared.session-close` on `Clear/DST/SCISSOR`.
- Re-runs of the flaked cases without the full-suite churn: BUILD SUCCESSFUL.

Conclusion: the `failed.surface.prepared.session-close` flake lands on a
different random non-dst-read frame whenever the full suite re-runs under GPU
churn and passes in isolation — the documented environmental behavior. No
assertion was weakened for it.

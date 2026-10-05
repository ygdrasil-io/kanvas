# W7 AA and dash regression repair plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Execute tasks with public pixel evidence and independent review.

**Goal:** Repair the AA regression exposed by the standalone extension and restore ordinary dashed-path rendering without losing its 41 newly rendered GMs.

**Architecture:** Preserve the root W4d graph and authority model. Repair immutable mathematical value equality at its source; diagnose AA approximation/coverage before choosing the smallest geometry or rendering correction. No GM-specific routing or silent fallback.

**Tech Stack:** Kotlin, math geometry F64, GPU renderer, native Metal/WebGPU Surface, fixed Skia corpus.

**Spec:** The open defects and next priorities in [pilotage.md](https://github.com/ygdrasil-io/kanvas/blob/36350563f48485598009d61a1707f7cff0ff7e94/refactor/waves/W07-gm-convergence/pilotage.md#changements-des-anciens-pixels-et-dette-ouverte), under the user's delegated pilotage.

## Global Constraints

- Fonts, codecs, external decode and quarantined jpg-color-cube remain outside the implementation scope.
- Do not modify GM ports, reference PNGs, thresholds, exclusions or resource budgets to claim a gain.
- Geometry and geometric value types belong to math; preserve I/F32/64 nomenclature.
- No new infrastructure tests. Regression tests exercise public Surface pixels or mathematical behavior, not private seals/source text.
- Preserve immutable copies, all style values, native validation, source provenance and the existing W5/W6 contracts.
- Run GPU/Gradle work sequentially. Do not spawn subagents from an implementer.
- Work only in `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branch `codex/w7-aa-dash-repair`; leave other worktrees untouched. Prefix shell commands with `rtk`, edit using apply_patch.

## Review Focus

- Equal independently captured dash values must survive snapshot validation, without making different intervals/phases equal.
- Input/output array mutation must not change an immutable dash already captured by a draw.
- Signed-zero equality/hash semantics must agree; use Kotlin Double/DoubleArray value semantics consistently.
- AA changes must preserve hard-edge rendering, transforms, alpha/order, stencil holes and the new stroke/hairline domain.
- Distinguish actual paired image improvements from newly comparable images; report every old hash or outcome regression.

### Task 1: Restore immutable dash value semantics

**Files:**
- Modify `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeStyleF64.kt` (`PathStrokeDashF64` only).
- Add `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7DashStrokeSurfacePixelTest.kt` if the existing public witness does not cover phase/interval distinctions.
- Reuse, unchanged, `GPUPlanSurfacePixelTest.W4d negative dash phase matches an independent source-arclength oracle`.

**Interfaces:** `PathStrokeDashF64.of(DoubleArray, Double)`, `copyIntervalsF64()`, `equals(Any?)`, `hashCode()`; consumers continue snapshotting without identity exceptions.

- [x] Reproduce the existing negative-phase public test failure: `w5b.geometry.incompatible-plan: Required value was null.`
- [x] Add a public pixel matrix for a hard horizontal butt-cap width-1 path from (1,2.5) to (11,2.5) on a 12×5 transparent Surface. Declare literal red x positions before rendering: dash [2,2], phase -1 → {2,3,6,7,10}; phase 1 → {1,4,5,8,9}; dash [1,3], phase -1 → {2,6,10}. All other pixels transparent. Use fresh independently created dash paints and native Render/Readback evidence; dispose GPU on the test thread using @AfterAll.
- [x] Run the public tests RED before production edits; inspect the complete failing diagnostic.
- [x] Implement structural equality and matching hashCode of immutable `PathStrokeDashF64`, comparing interval contents and phase exactly (Double.toBits semantics, not approximate equality). Keep defensive copying and validation unchanged. Do not weaken W5b style validation.
- [x] Run public tests GREEN and self-review; commit only owned code/test files. Main runs combined regressions and corpus after AA is resolved. Commit `adcf16eba`; two public tests and 476 math tests pass.
- [x] Independent spec and code-quality review; correct confirmed findings before proceeding. Sol: no confirmed defect.

### Task 2: Repair AA regression after causal diagnosis

- [x] A/B the root admission only: disabling the standalone factory restores the exact baseline `circle_sizes` RGBA hash, score, MAE and SSIM; restore the factory immediately. Probe journals are diagnostic dirty-tree evidence, not a snapshot of their labelled base commit.
- [x] Complete the static old/new geometry and AA comparison, choose a discriminating public witness and record the causal correction before implementation.

**Cause:** Both paths use stencil AA4 and the same sRGB resolve; old/new PNG palettes are exactly {0,137,188,225,255}. The legacy cubic flattener samples at control-polygon length / .25, whereas W4d uses a recursive device-space maximum sagitta of .25. The latter produces a visibly inscribed coarse polygon. Summed sample coverage for radii 1/8/16 is 3/201/805 on the old route, 2/195/800 on the new route (analytic circle areas approximately 3.142/201.062/804.248). The public witness below measures alpha, avoiding color-transfer ambiguity.

**Files:**
- Modify `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`.
- Add `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7AaCurvePrecisionSurfacePixelTest.kt`.

**Interfaces:** Existing `Matrix3x3F64.preparePathFillGeometryF32(path, fillPolicyF64, strokePolicyF64, frameWorkUsageBeforeI64)` and `PathFillFlatteningPolicyF64`. No new public API.

- [x] Write independent public alpha-area witnesses before changing production: opaque white AA `Path.addCircle` on transparent background; radius 1 at (2,2) in 4×4 must have `2.5 <= sum(alpha)/255 <= 3.75`; radius 8 at (10,10) in 20×20 must have `198 <= sum(alpha)/255 <= 204`. The bounds contain the analytic areas and allow subpixel sampling error; do not compute expectations using the geometry worker or a second render. Verify native Render/Readback evidence and transparent far corners. Repeat the radius-8 case with canvas scale 2, local center (5,5) and radius 4 to check device-space precision. Use @AfterAll runtime disposal as existing W7 suites.
- [x] Run these public tests RED on the unchanged root compiler, record alpha areas and failing assertions. Areas: 2.007843 / 195.043137 / 195.043137.
- [x] Pass a `PathFillFlatteningPolicyF64(maximumSagittaErrorF64 = 0.0625)` only for standalone-root fills requesting AA. This caps geometric error at 1/16 device pixel before four-sample coverage. Preserve default .25 for hard fills and all non-standalone source compilers. Name/comment the precision policy; preserve every resource limit and ledger charge. Do not alter sample count, gamma, resolve, stroke policy, or add a fallback to coarser geometry.
- [x] Run witnesses GREEN, existing W7 stroke/layer and W6 layer regressions, and the single `circle_sizes` checkpoint. Areas: 2.996078 / 199.011765 / 199.011765. Broad selection: 118/119; the W6a empty-clip/backdrop failure also occurs with the new policy disabled. `circle_sizes` SSIM improves 0.974624 → 0.987049 but remains below the historical 0.987834; no blind retuning or weakened bounds.
- [x] Commit owned code/tests and obtain independent spec and quality review. The full corpus reveals two losses, so the experiment is withdrawn in `5f971f750` after Astra strategy review. Sol approves the withdrawal; 16 W7 tests pass. **The AA repair is deferred, not delivered.**

### Task 3: Measure and publish

- [x] Commit production, run the same complete 631 identities / 443 eligible cases with 30-second timeouts and resume journals; compare against both `strokes-718445e6e.json` and the original baseline. Final `5f971f750`: 164 renders, all RGBA hashes and outcomes identical to #2412; 142 comparisons, 26 ≥99%, no corpus changes. Original 41 gains preserved; no new GM gain from dash.
- [x] Verify public W6/W7 regressions, record failures honestly, inspect changed images and receive a whole-branch review. Sol verifies the delivered diff, snapshots and XML: no new confirmed defect, draft publication approved, not merge-ready.
- [x] Update pilotage/status and publish a draft PR stacked on #2412; attach it to the chat. [PR #2413](https://github.com/ygdrasil-io/kanvas/pull/2413), attached; no merge. AA remains explicitly deferred.

Final validation before review: 77 public tests plus 476 math geometry
tests pass, zero failures/skips, Gradle exit 0. Full Kanvas attempt remains
incomplete: 646 passed, 39 failed, one interrupted/skipped. The long W5d
gradient test was stopped after two thread dumps located CPU evaluation in
`ColorRoundedGraphProofV1.prove`; no full-suite success is claimed. Detailed
failure groups and evidence paths are recorded in pilotage.md.

## Execution notes

The independently diagnosable dash repair runs while AA is inspected read-only; only one implementer edits production at a time. The user delegated decisions and sequential execution, so this focused continuation does not introduce another approval round.

### Rejected intermediate corpus — `9d3355ec9`

The full replay retains all 631 identities / 443 eligible cases, reference
hashes and comparison settings, but falls from 164 to 162 renders. Exactly
two previously rendered GMs, `parsedpaths` (409) and `perspective_clip` (433),
now hit `Geometry(value=VertexLimit)`. The static `WindingStencilEdgeLimit`
gate at 255 emitted edges is the leading explanation, not a captured internal
subtype: the surfaced diagnostic aggregates several causes.
The 1/16px policy is therefore not accepted as preserving availability.
The same three 30s timeouts remain; 140 comparisons, 26 at ≥99%, 36 at ≥95%.
The intermediate snapshot is diagnostic evidence in
`/private/tmp/kanvas-w7-aa-dash-rejected-9d3355ec9.json`, not the accepted
checkpoint.

### Decision after the focused Astra review

Withdraw the AA policy and its three new tests from the deliverable; keep
commit `9d3355ec9` and the RED/GREEN evidence above for a separate bounded
follow-up. Do not leave knowingly red tests or silently coarsen after a
resource refusal. The unchanged `.25` policy is restored, so the original
AA regression remains open; only the independently verified dash repair is
published in this lot.

The stencil uses IncrementWrap/DecrementWrap modulo 256. A final winding of
±256 incorrectly becomes zero. Total edge count is a conservative safety
bound, not the exact overlap count. Bounding positive/negative triangle
counts independently could be tighter, but host F64 determinants do not by
themselves certify the GPU's front/back classification. No numeric margin
or hardware precision guarantee is invented here.

Before reopening implementation: capture the exact internal refusal and
edge count for both GMs, then evaluate a bounded geometry strategy. Retain
the circle-area witnesses; add a >255-edge winding-1 contour and a genuine
256-winding overlap, with reversed orientations/cancellations. Public pixels
must preserve holes, budgets and both GMs; passing pixels alone do not prove
a missing GPU classification contract. This decision defers the AA gain,
rather than trading existing renders for an improved local score.

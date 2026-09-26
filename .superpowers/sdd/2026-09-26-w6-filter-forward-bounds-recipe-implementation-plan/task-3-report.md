# Task 3 report — exact budget, temporal order and recovery

## Outcome

Task 3 adds the requested public witnesses and W06 checkpoint. The initial
preservation run exposed two causal `:gpu-plan` regressions in shared Picture
aggregate ownership, fixed in `b450a13` and `051171a`; the third expectation
was explicitly updated after controller ruling because direct W6d Magnifier is
already positively admitted.

## B arithmetic

`tinyCropAndIdentityKeepContentSizedBudget` derives all operands before a
`Surface` is built with checked I64 operations. Crop has:

- root RGBA8 2×1: 8
- layer, FilterSource, Crop and terminal composite targets 1×1: 4×4 = 16
- W6 geometry/restore and source/filter rows: 2×16 = 32
- RGBA8 readback row aligned to 256: 256
- `B = 8 + 16 + 32 + 256 = 312`

At Crop B pixels are `[blue, transparent]` with Render+Readback. At B−1 the public
diagnostic starts `w6b.filter.frame_budget_exceeded:`, the readPixels sentinel
is unchanged, and discard/re-record on that same Surface gives the expected
pixels. Expanding Crop's produced output to the full desired 2×1 would add
bytes and fail B, so the test rejects that regression.

The identity `ColorFilter` is an independent boundary, not a default-budget
smoke render. Its content-sized target terms are again root 8, four 1×1 targets
16 and two W6 rows 32; its identity matrix has 20 F32 coefficients, so its
uniform payload adds `20×4 = 80`; the aligned readback is 256. Therefore
`B = 8 + 16 + 32 + 80 + 256 = 392`. Its B render has exact
`[blue, transparent]` pixels plus Render+Readback; its B−1 readback refuses
with the same public diagnostic, leaves a sentinel untouched, and the same
Surface discard/re-records to the exact pixels. This is hand-derived from the
fixture's RGBA8/F32 quantities, not a planner counter.

`warmPictureReplayRetainsColdBudgetAndIdentity` keeps one immutable Picture
through cold and warm replay. Its direct Picture source contributes one extra
1×1 target: `B = 8 + (5×4) + 32 + 256 = 316`. Cold B−1 refusal is observed
before the accepted run; accepted cold and warm B runs both produce exact
pixels and Render+Readback; warmed B−1 still refuses without mutating its
sentinel. This is public behavior only: it does not read a counter or planner.

The initial 312 candidate for this *Picture* fixture was rejected by the
public diagnostic (`requires 316 bytes`), then traced to the separate direct
Picture source target in the existing frozen construction. The final test
states that fifth fixture resource explicitly; it does not fit B by retries.

## RED/GREEN evidence

Before any production edit, these serialized selectors were run with `rtk`:

| Selector | XML PASS/F/E/S | Gradle / native |
| --- | ---: | --- |
| `W6FilterBoundsRecipeSurfacePixelTest` | 10/0/0/0 | exit 1; worker 133, UNKNOWN |
| `W6FilterBoundsRecipePictureTest` | 3/0/0/0 | exit 1; worker 133, UNKNOWN |

The only temporary RED was the Picture witness itself while its resource
addition omitted its direct-source target: `w6b.filter.frame_budget_exceeded:
Layer frame requires 316 bytes; budget is 312.` This was a test-arithmetic
error, not a production defect; the corrected public B is 316.

`backdropAndPreviousKeepSaveThenPostChildOrder` creates both expected pixel
arrays before either Surface: backdrop uses an input-dependent matrix which
halves the save-time parent's red channel in linear space, yielding exact
backdrop pixels `[176,56,162,255, 210,51,73,255]`; filtered
`initWithPrevious` sees its blue child before its filter is applied.

Both cold and warm Picture B−1 refusals now discard and re-record on their
respective same surfaces, then assert the exact `[blue, transparent]` pixels
and `Render`/`Readback` scopes. This is in addition to their original sentinel
atomicity and cold/warm B proof.

## Preservation selectors

Fresh serialized results:

| Selector | XML PASS/F/E/S | Gradle / native |
| --- | ---: | --- |
| `W6aLayerBudgetRecoverySurfacePixelTest` | 10/0/0/0 | exit 1; 133/UNKNOWN |
| `W6bBudgetRecoverySurfacePixelTest` | 2/0/0/0 | exit 1; 133/UNKNOWN |
| `W6bFilterAdmissionRecoverySurfaceTest` | 26/0/0/0 | exit 1; 133/UNKNOWN |
| `W6cSpatialDagAdmissionSurfaceTest` | 2/0/0/0 | exit 1; 133/UNKNOWN |
| `W6cSpatialCacheRecoverySurfaceTest` | 6/0/0/0 | exit 1; 133/UNKNOWN |
| `W6dAdvancedRecoverySurfacePixelTest` | 5/0/0/0 | exit 1; 133/UNKNOWN |
| `W6dBackdropPreviousSurfacePixelTest` | 9/0/0/0 | exit 1; 133/UNKNOWN |
| `W6dMagnifierReverseDemandSurfacePixelTest` | 4/0/0/0 | exit 1; 133/UNKNOWN |
| `W6dPictureRuntimeEffectPictureTest` | 6/0/0/0 | exit 1; 133/UNKNOWN |

The initial REDs were diagnosed method-by-method before correction. The W6b
shadow pass lost `produced` because prepared Picture facts used reverse source
`[-1,1]` as its terminal demand rather than downstream `[0,2]`; preserving
`demandDeviceI32` repairs its translated production. The singular-transform
case now recognizes its typed empty `DeviceRect` before projecting a cull.
The old W6c `unsupported_family` assertion was obsolete: Magnifier has a W6d
lowering and reverse-demand authority. With controller approval, its public
replacement records explicit blue source paint, asserts exact blue
`[0,0,255,255]` after Render+Readback, then proves discard/re-record recovery.

### Exact causal RED-to-GREEN transcript

The following commands were run serially. Their Gradle exit code is separated
from the JUnit/XML result: every listed test process exited 133 *after* its
JUnit result because of the native worker, so it is **UNKNOWN**, not a test
failure or a native PASS claim.

1. Initial shadow RED:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bBudgetRecoverySurfacePixelTest'`.
   XML recorded 0/2/0/0: both
   `pictureAggregateBudgetAcceptsBAndRefusesBMinusOne` and
   `nestedBudgetBoundaryAndLateSiblingRefusalAreAtomic` failed with
   `GPUPlanSurfaceTerminalException: w6a.layer.invalid_plan: W6b shadow has no sealed bounds.`
   The Gradle process subsequently reported `Gradle Test Executor ... exit value 133`.

2. Focused shadow GREEN after `051171a`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bBudgetRecoverySurfacePixelTest.pictureAggregateBudgetAcceptsBAndRefusesBMinusOne'`
   printed `... PASSED`; the independently focused
   `nestedBudgetBoundaryAndLateSiblingRefusalAreAtomic` command also printed
   `... PASSED`. The complete class then printed both methods `PASSED` and
   XML 2/0/0/0. Each invocation ended only with native-worker 133/UNKNOWN.

3. Initial singular-empty-clip RED:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'`.
   XML recorded 25/1/0/0; method
   `empty deferred Picture clip elides a filtered child under a finite singular transform and recovers`
   failed with
   `GPUPlanSurfaceTerminalException: w6b.filter.invalid_bounds: Picture cull cannot be projected to checked I32 device texels.`
   Native-worker 133 followed the JUnit result.

4. Focused empty-clip GREEN after `b450a13`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest.empty deferred Picture clip elides a filtered child under a finite singular transform and recovers'`
   printed `... PASSED`; the complete class then printed XML 26/0/0/0. Both
   invocations again ended only with 133/UNKNOWN after JUnit.

### Sol review correction round 1

The review identified three evidence gaps, so this round changes only the two
public Task 3 test classes. The first new Surface run was deliberately recorded
before changing an owner:

`rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest'`
produced XML `8/2/0/0` and then native-worker exit 133 (`UNKNOWN`).
`tinyCropAndIdentityKeepContentSizedBudget` reported
`w6b.filter.frame_budget_exceeded: Layer frame requires 392 bytes; budget is 312`:
the first identity arithmetic had omitted its actual 20-F32 matrix uniform.
The backdrop assertion reported its independently visible one-byte distinction,
`Expected [176,56,162,255, 211,51,73,255]` versus
`actual [176,56,162,255, 210,51,73,255]`; analysis found the expected oracle
had incorrectly quantized the matrix output before the opacity composite.
Neither RED named a production owner.

The correction states the 80-byte matrix term in checked I64 arithmetic and
keeps the matrix/opacity calculation in one linear expression. Rerunning the
same Surface command printed all ten methods `PASSED`; its XML was
`10/0/0/0`, followed only by worker 133/`UNKNOWN`. The Picture command
`rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest'`
printed all three methods `PASSED`, XML `3/0/0/0`, then worker 133/`UNKNOWN`.
Focused preservation remained green in JUnit XML:

| Selector | XML PASS/F/E/S | Gradle / native |
| --- | ---: | --- |
| `W6bBudgetRecoverySurfacePixelTest` | 2/0/0/0 | exit 1; 133/UNKNOWN |
| `W6dBackdropPreviousSurfacePixelTest` | 9/0/0/0 | exit 1; 133/UNKNOWN |

No production owner changed in this round.

`rtk ./gradlew :gpu-plan:compileKotlin :kanvas:compileTestKotlin` exited 0
with `BUILD SUCCESSFUL`.

## Scope and self-review

Across Task 3, changed files are the two Task 3 public test classes, the
authorized existing `W6cSpatialDagAdmissionSurfaceTest`, the causal Picture
aggregate owner, this report, and `refactor/waves/W06-layers-effects/status.md`.
This review correction changes only the two Task 3 test classes, this report
and that status checkpoint. No API,
renderer, wire format, budget/cache type, GM, font, codec, external-format or
infrastructure test was added. The user-owned
`.superpowers/sdd/2026-09-22-w6d-advanced-effects-implementation-plan/progress.md`
remains modified but untouched and unstaged.

Self-review: expectations are created before Surface/PictureRecorder, pixel
assertions use Surface/Canvas/Picture only, the B terms are hand-derived and
checked I64 arithmetic, and both B−1 paths assert terminal atomicity plus
recovery. Native 133 is consistently classified UNKNOWN.

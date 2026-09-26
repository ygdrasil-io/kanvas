# Task 2 report — W6 contextual filter bounds

## Status

**DONE_WITH_CONCERNS pending Sol review.** The user-approved narrow archive-reader correction
unblocks a public memory/wire witness with a child `recordedInnerClip` distinct from the recorder
cull. It leaves the writer, wire version/layout, external codecs, and semantic validation intact.

## W6a checkpoint

- Direct occurrences are evaluated before `sealGeometry`, from their captured raster and mapping.
- Their `producedOutput` contributes to `directKnownByScope` before parent reservation.
- The former `MaskFilter.Blur` special case is replaced with this common authority.
- Image/mask evaluations are memoized by occurrence identity and consumed at late freeze; no
  second DAG walk, renderer replan, `PlanResourceId` recipe data, or API change.
- `directOffsetUnderPictureAggregateSurvivesParentRestore` is a green preservation witness, not
  a causal RED.

## Evidence

Initial causal RED:

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest'
```

JUnit XML had 6 tests / 1 failure, public method
`directOffsetAndBlurSurviveUnfilteredParent`: direct Offset expected blue at `x=1` but got
transparent (`expected 255`, `actual 0`). The process also exited 133; this native status is
separate from the causal JUnit RED.

After correction, serialized `rtk` selectors reported:

| Selector | JUnit XML | Gradle/native |
| --- | --- | --- |
| `W6FilterBoundsRecipeSurfacePixelTest` | 6/6 green | 133, UNKNOWN |
| `W6FilterBoundsRecipePictureTest` | 2/2 green | 133, UNKNOWN |
| `W6cSpatialDagPictureTest` | 3/3 green | 133, UNKNOWN |
| `W6dPictureFilterSurfacePixelTest` | 20/20 green | 133, UNKNOWN |
| `W6dBackdropPreviousSurfacePixelTest` | 9/9 green | 133, UNKNOWN |
| `W6bMaskBlurAutoLayerSurfacePixelTest` | 9/10 green | 133; known baseline RRect RED remains |
| legacy v8 decode + malformed trailing payload public tests | 2/2 green | exit 0, BUILD SUCCESSFUL |
| `:render-ir:compileKotlin :gpu-plan:compileKotlin :kanvas:compileTestKotlin` | — | exit 0, BUILD SUCCESSFUL |

No GM, font, or codec suite was run.

## User-approved archive unblock

| Variant | Memory/wire result | Meaning |
| --- | --- | --- |
| A. Recorder cull DeviceRect `[0,1)` clips actual `drawRect([-1,2))` | memory + wire green; parent `saveLayer` keeps a deferred composite clip | serializable, but `recordedInnerClipWithoutCull()` removes this initial cull; insufficient for Step 4. |
| B. Child `clipRect(unit, INTERSECT, false)` after cull, before reader correction | `Picture.fromByteArray` returns null | `SceneArchiveCodec.decodePicture` is `Invalid(code=unknown-schema, message=Scene archive schema is not supported)`. |
| C. Same explicit child clip after reader correction | Task 2 memory + wire 2/2 green | distinct hard child clip is retained and the parent Blur halo is verified on both replays. |

`PictureRecorder.beginRecording` creates the initial public `ClipStack.DeviceRect` (the cull).
Every subsequent `Canvas.clipRect`, even equal rectangle/AA, becomes `ClipStack.Operations` via
`Canvas.appendClip`. The writer already emits schema 9 using `clipTransformV2`; the reader
incorrectly admitted that form only for schemas 2..8. The authorized one-line reader correction
adds schema 9 to that existing branch; it changes neither writer nor schema/version/layout.

`PictureTest.version 9 picture roundtrip preserves ordered hard clip payload through public
serialization` was the causal RED and is GREEN after the correction. Public focused legacy v8
decode and malformed trailing-data refusal are also GREEN. The Task 2 Picture test now retains
shared/equal-distinct identity, an explicit child hard clip distinct from the cull, parent
deferred composite clip, precomputed expected pixels, memory/wire replay, and Render + Readback.

## Scope and commit

Task files: `W6aLayerGraphConstruction.kt`, `W6FilterBoundsRecipeSurfacePixelTest.kt`,
`W6FilterBoundsRecipePictureTest.kt`, and the user-approved
`render-ir/.../SceneArchiveCodec.kt`. The user-owned W6d progress ledger remains untouched and
unstaged. Concern: the unrelated known RRect baseline remains 9/10; native worker 133 makes
otherwise-green selector Gradle status UNKNOWN. This checkpoint awaits Sol review.

## Sol round 1 follow-up

Verified and corrected the two direct-owner findings in `W6aLayerGraphConstruction`:

- Early direct evaluation now unwraps only reverse-demand terminal clips, intersects its finite
  pre-terminal raster with the bound recipe's own `requiredInput`, and carries that sealed domain
  to late allocation. It does not union a logical halo into the physical source, preserving the
  existing Matrix CLAMP counterexample.
- The same `directTerminalClip` authority is now computed before early propagation. The recipe is
  bound to its clipped terminal desired domain and early `producedOutput` is intersected with that
  clip; a blend that cannot write the parent contributes no known content. Late lowering uses the
  same helper.

`W6FilterBoundsRecipeSurfacePixelTest` remains XML 6/6 green after this correction (Gradle 133,
UNKNOWN); `:gpu-plan:compileKotlin :kanvas:compileTestKotlin` is successful. The late mask
fallback is unreachable for a direct occurrence with a mask because the early pass populates
`evaluatedMasksByOccurrence` before it stores direct facts, and the physical direct path requires
those facts. It remains necessary for non-direct/layer occurrences, so no broad invariant was
added there.

## Astra round 2 follow-up

The existing public no-op/recovery selector was RED after round 1: a disjoint
source/reverse-demand path skipped direct facts and late allocation threw. The correction keeps
finite raster facts when demand is disjoint and distinguishes an intentional filtered `null`
from an unfiltered draw, so it cannot fall back to raw raster for a non-writing blend or no-op.
The local `withoutW6aTerminalClip` unwrap was removed because the W6a compiler already supplies
the pre-terminal draw. The selector is XML 1/1 green; Gradle/native 133 is UNKNOWN. The gpu-plan
compile succeeded.

The proposed B=312 Tile budget requires fixed resource arithmetic that belongs to Task 3. It was
not added without private planner/resource inspection.

## Terminal no-op regression follow-up

`W6dLightingSurfacePixelTest.disjoint direct terminal clip is a no op and surface recovers`
exposed the remaining empty-terminal form: `terminalDesired == null` returned before writing a
direct fact, while late lowering correctly required that fact before taking its terminal no-op
route. The minimal correction records `DirectAutoLayerFacts(raster, desired)` in that early
branch and still returns a `null` produced output. Late lowering uses the fact only to select its
sealed terminal no-op path, whose own no-op domain prevents allocation or raw-content revival.

Both existing public recovery selectors are XML 1/1 green after the correction:

| Selector | JUnit XML | Gradle/native |
| --- | --- | --- |
| `W6dLightingSurfacePixelTest.disjoint direct terminal clip is a no op and surface recovers` | 1/1 green | 133, UNKNOWN |
| `W6cSpatialBoundsSurfaceTest.direct filtered draw outside its clipped terminal is a no-op and surface recovers` | 1/1 green | 133, UNKNOWN |
| `:gpu-plan:compileKotlin` | — | exit 0 |

The native 133 is unchanged from the focused suite environment and does not represent a JUnit
failure. The user-owned W6d ledger remains unstaged.

## Sol round 3 follow-up

The public RED `nonWritingDirectFilterWithSeparateWritingSiblingIsNoOpAndRecovers` records a
direct `ImageFilter.Offset` at x=5 with `BlendMode.DST` inside an otherwise unfiltered layer and
a writing blue sibling at x=0. Its independent pre-Surface oracle is `[blue, transparent x5]`.
On `3ad441db4`, XML was 1/1 RED before the pixel assertion with
`W6b direct occurrence has no pre-reservation recipe facts`.

Root-cause tracing showed that `DST` is represented by W5 as `BlendPlan.NoOpV1`; consequently no
visual `PlanDraw` reaches the early recipe pass, but the late loop was still iterating its
semantic filter occurrence. The correction now obtains the exact W5 binding first: a missing
early fact is accepted only if that binding has no visual draw, otherwise the invariant still
throws. This makes the non-writing occurrence consistently allocate neither source nor late
recipe, while every visible direct occurrence continues to require and consume its memoized
evaluation. No recipe is recalculated and no planner/renderer/wire path changed.

After the correction the new public DST/recovery witness is XML 1/1 green. The full direct class
is XML 7/7 green, including direct Offset/Blur and reverse-demand/clip witnesses. The existing
W6c and W6d terminal no-op recovery selectors are each XML 1/1 green. The Picture class is XML
2/2 green, and `:gpu-plan:compileKotlin` exits 0. Every GPU selector still ends in native exit
133 after green XML, so its Gradle result is recorded as UNKNOWN rather than PASS.

The optional equal-but-distinct equality assertion was investigated but not retained:
`ImageFilter.Picture` equality includes its internal `Picture` identity, while wire replay
reconstructs that `Picture`. The attempted public assertion was correctly RED, so changing that
semantic contract would be out of scope. The existing shared-identity and distinct-identity
assertions remain. The `appendFrozenOccurrence` late evaluation fallback is unreachable for a
visible direct image occurrence: its only direct call passes the memoized map value, while the
new non-writing `DST` form now creates no direct source or call at all. It remains needed for
non-direct/layer/Picture paths, so it was not broadened into an unsafe global invariant.

## Astra fix round 4 — direct MaskShader DST routing

Sol's remaining Important finding is addressed. On starting HEAD `34ffd3068`, the new public
`nonWritingDirectMaskShaderWithSeparateWritingSiblingIsNoOpAndRecovers` was reproduced RED
before any production change. Its direct `MaskFilter.Shader(Shader.SolidColor(White))` draw at
x=5 uses DST, while a blue sibling at x=0 writes inside an unfiltered `saveLayer`. Literal
expected RGBA pixels for that frame and a subsequent red x=5 recovery frame are prepared
before `Surface`. Both renders assert `Render` + `Readback`, and the second follows
`discardRecordedOperations()` on the same surface.

The failure was a real named JUnit failure, not inferred from the native process status:

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingDirectMaskShaderWithSeparateWritingSiblingIsNoOpAndRecovers'
W6FilterBoundsRecipeSurfacePixelTest > nonWritingDirectMaskShaderWithSeparateWritingSiblingIsNoOpAndRecovers() FAILED
tests="1" skipped="0" failures="1" errors="0"
GPUPlanSurfaceTerminalException: w6a.layer.unsupported_child: Required value was null.
Process 'Gradle Test Executor 69' finished with non-zero exit value 133
BUILD FAILED in 4s
```

The command's first sandboxed attempt exited 1 before Gradle execution because the existing
Gradle wrapper lock was outside writable roots. The authorized escalated rerun above produced
the causal RED; it also exited 1 as a Gradle process, separately from native worker exit 133.

Root cause: W5 removes the DST visual source and the source-allocation loop skips its semantic
occurrence, but the later MaskShader capture loop still required that absent source. W6a now
selects `activeFilterOccurrences` once before all late occurrence consumers. A direct occurrence
without early recipe facts is omitted only after verifying that its exact W5 binding has no
visual draws. Real visual sources without facts still throw the strict existing invariant.
Allocation, insertion grouping, mask material capture, and late Picture-layer lookup consume
that same selection. Layer/Picture occurrences retain their existing routes. No second DAG
interpretation, recipe `PlanResourceId`, API, wire, renderer, or budget change was introduced.

Fresh verification commands were serialized, in this order after the production correction:

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingDirectMaskShaderWithSeparateWritingSiblingIsNoOpAndRecovers'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialBoundsSurfaceTest.direct filtered draw outside its clipped terminal is a no-op and surface recovers'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest.disjoint direct terminal clip is a no op and surface recovers'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskShaderTableSurfacePixelTest'
rtk ./gradlew :gpu-plan:compileKotlin :kanvas:compileTestKotlin
```

| Command above | Fresh JUnit XML / output | Gradle exit | Native worker |
| --- | --- | --- | --- |
| Focused MaskShader DST | 1 test, 0 failures, 0 errors; named test PASSED | 1 | executor 70, exit 133 — UNKNOWN |
| Recipe Surface class | 8 tests, 0 failures, 0 errors | 1 | executor 71, exit 133 — UNKNOWN |
| Recipe Picture class | 2 tests, 0 failures, 0 errors | 1 | executor 72, exit 133 — UNKNOWN |
| Direct W6c no-op/recovery | 1 test, 0 failures, 0 errors | 1 | executor 73, exit 133 — UNKNOWN |
| Direct W6d no-op/recovery | 1 test, 0 failures, 0 errors | 1 | executor 74, exit 133 — UNKNOWN |
| MaskShaderTable class | 13 tests, 0 failures, 0 errors | 1 | executor 75, exit 133 — UNKNOWN |
| Both compilation tasks | `BUILD SUCCESSFUL in 920ms` | 0 | not applicable |

Status: **DONE_WITH_CONCERNS pending independent Sol re-review**. Native 133 is still not a
global PASS. No GM, fonts, external codec, or global Skia tests were run. The user-owned dirty
W6d progress ledger was neither edited nor staged. The scoped commit contains only W6a, the
public Surface test, and this appended report.

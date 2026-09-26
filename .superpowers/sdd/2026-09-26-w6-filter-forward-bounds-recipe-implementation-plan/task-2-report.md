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

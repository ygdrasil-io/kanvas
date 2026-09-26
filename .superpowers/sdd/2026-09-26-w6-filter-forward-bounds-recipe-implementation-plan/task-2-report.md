# Task 2 report — W6 contextual filter bounds

## Status

**BLOCKED at Step 4.** The W6a correction and direct witnesses are implemented, but a public
wire witness with an explicit child `recordedInnerClip` cannot round-trip with the current wire
without changing `:render-ir` / archive behavior, which the task forbids.

## W6a checkpoint

- Direct occurrences are evaluated before `sealGeometry`, from their captured raster and mapping.
- Their `producedOutput` contributes to `directKnownByScope` before parent reservation.
- The former `MaskFilter.Blur` special case is replaced with this common authority.
- Image/mask evaluations are memoized by occurrence identity and consumed at late freeze; no
  second DAG walk, renderer replan, `PlanResourceId` recipe data, or wire/API change.
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
| `:gpu-plan:compileKotlin :kanvas:compileTestKotlin` | — | exit 0, BUILD SUCCESSFUL |

No GM, font, or codec suite was run.

## Explicit hard-clip wire matrix

| Variant | Memory/wire result | Meaning |
| --- | --- | --- |
| A. Recorder cull DeviceRect `[0,1)` clips actual `drawRect([-1,2))` | memory + wire green; parent `saveLayer` keeps a deferred composite clip | serializable, but `recordedInnerClipWithoutCull()` removes this initial cull; not Step 4's explicit child clip. |
| B. Child `clipRect(unit, INTERSECT, false)` after cull | `Picture.fromByteArray` returns null | `SceneArchiveCodec.decodePicture` is `Invalid(code=unknown-schema, message=Scene archive schema is not supported)`. |
| C. Existing public `PictureTest.version 9 picture roundtrip preserves ordered hard clip payload through public serialization` | fails at `requireNotNull` | same wire refusal outside W6a. |

`PictureRecorder.beginRecording` creates the only public `ClipStack.DeviceRect` (the cull).
Every subsequent `Canvas.clipRect`, even equal rectangle/AA, becomes `ClipStack.Operations` via
`Canvas.appendClip`. The writer emits schema 9 but `ArchiveReader.clip()` admits Operations only
for schemas 1..8. Modifying that owner is a prohibited wire/`:render-ir` change.

The committed compatible Picture test retains shared/equal-distinct identity, real hard cull,
parent deferred composite clip, precomputed expected pixels, memory/wire replay, and Render +
Readback evidence. It explicitly does **not** claim to prove a recorded inner clip distinct from
the cull.

## Scope and commit

Task files: `W6aLayerGraphConstruction.kt`, `W6FilterBoundsRecipeSurfacePixelTest.kt`, and
`W6FilterBoundsRecipePictureTest.kt`. The user-owned W6d progress ledger remains untouched and
unstaged. The following commit is a BLOCKED partial checkpoint, not Task 2 DONE.

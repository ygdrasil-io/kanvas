# W6d Task 4c — Capture/Wire Isolation, Atomic Pressure and Preservation

## Scope and result

Base reviewed: `3e31d32fb080179bc04a2ffbb69221e7f188c717` on
`codex/w6d-advanced-effects`.

Task 4c adds only public `Surface`/`Picture` witnesses and fixes one causal
planner defect.  The existing deep `SceneSnapshot` capture and the Picture
15/schema 9 archive were preserved.  There is no second archive, renderer
scene replay, content-based deduplication, new graph vocabulary, geometry
outside `:math`, GM, font, codec, render, dashboard, score, mock, fake device,
reflection, or infrastructure test.

The implementation is ready for the required fresh Sol review; it does not
start Task 5.  The numeric B/B-1 proof remains parent-plan Task 7.

## Public evidence

`W6dPictureRuntimeEffectPictureTest` supplies these memory and wire witnesses:

* Two separately created but equal-valued `ImageFilter.Picture` values are
  replayed in distinct translations; both memory and decoded wire Pictures
  produce two opaque red pixels with `Render` and `Readback` evidence.
* One shared `ImageFilter.Picture` instance is evaluated in two source
  contexts; memory and wire replay have the same public result.
* Mutating caller-owned `sourceCull` and `src` `RectF32`s after capture leaves
  the captured red/transparent crop unchanged.
* The checked-in non-lighting Picture 14/schema 8 byte fixture is decoded,
  re-encoded by the current writer, then both candidates are used as a
  filter-owned Picture source on a public 8x8 `Surface`.  Each produces exact
  opaque blue pixels with `Render` and `Readback`.  The checked-in old
  two-dimensional-lighting fixture still fails closed through
  `Picture.fromByteArray`.

`latePictureChildRefusalIsAtomicAndSameSurfaceRecovers` records a valid
Picture-filter draw followed by a later Picture child with perspective around
a `DistantLitDiffuse` layer.  `readPixels` refuses with
`w6a.layer.unsupported_lighting_mapping:`, leaves the public sentinel intact,
then `discardRecordedOperations()` allows the same `Surface` to render the
opaque green recovery pixel with `Render` and `Readback`.

## Causal RED and focused correction

The atomic method was first run with unchanged production.  Its JUnit XML was
`tests=1, failures=1, errors=0, skipped=0`: `readPixels` completed with
`true` instead of raising the required terminal refusal.  This is the causal
RED; the later native worker exit 133 is deliberately not used as RED evidence.

Root cause: `appendNestedPictureLayer` allocated its target with a copy of the
parent mapping and discarded the captured `LayerDescriptor.transform`.
Therefore the nested lighting input was falsely seen as affine, so the
existing `UnsupportedLightingMapping` refusal did not trigger.

The focused repair composes the frozen parent mapping with the captured layer
transform in checked F64 before allocating the existing child `LayerTarget`.
`timesCheckedOrNull` turns a non-finite composition into the existing W6b
construction refusal before allocation.  The child `SourceBinding` now carries
that composed mapping to the existing lighting validator.  No renderer or
codec path changed.

The post-fix atomic selector XML is `tests=1, failures=0, errors=0, skipped=0`.

## Freeze/accounting review

* The repair creates no resource: `allocatePictureLayerTarget` still uses the
  existing `PlanResourceId`, `nextPictureLayerTargetOrdinal` guarded by
  `Math.addExact`, existing `LayerTarget` `ResourceSpec`, and the unchanged
  CopyDestination/CopySource usages.  Only its frozen mapping argument is
  different.
* Resource extent, source specs, render-graph checked-I64 accounting, source
  generations and last-reader/lifetime declarations are unchanged.  The
  mapping is frozen before the existing resource allocation and before native
  preparation; the B/B-1 boundary proof is intentionally left to Task 7.
* No materializer, ready-token, lease, or quarantine implementation changed.
  The public terminal test proves no prefix readback/publication and a clean
  same-Surface recovery after discard.  Review should retain attention on the
  existing complete-ready-token quarantine path, but this task neither bypasses
  nor mutates it.
* `CapturedFilterInputV1.Picture(id)` and the Picture 15/schema 9 writer stay
  untouched.  The existing lighting codec preservation selector remains green
  in XML.

## Sequential verification custody

Compilations completed with process exit 0:

| Command | Result |
| --- | --- |
| `rtk ./gradlew :render-ir:compileKotlin` | exit 0 |
| `rtk ./gradlew :gpu-plan:compileKotlin` | exit 0 |
| `rtk ./gradlew :gpu-renderer:compileKotlin` | exit 0 |
| `rtk ./gradlew :kanvas:compileTestKotlin` | exit 0 |

The following JUnit XML is the assertion authority.  Every listed Gradle test
command subsequently reported a native test-worker exit 133; process status is
therefore **UNKNOWN**, not green or red, even when the XML is green.

| Command / selector | XML methods | F / E / S | Native process |
| --- | ---: | ---: | --- |
| `W6dPictureFilterSurfacePixelTest` class | 15 | 0 / 0 / 0 | exit 133 — UNKNOWN |
| `W6dPictureRuntimeEffectPictureTest` class | 4 | 0 / 0 / 0 | exit 133 — UNKNOWN |
| `W6dLightingPictureTest` class | 3 | 0 / 0 / 0 | exit 133 — UNKNOWN |
| `W6aLayerPictureTest.memoryAndWireReplayPreservePreviousPixels` | 1 | 0 / 0 / 0 | exit 133 — UNKNOWN |
| `W6aLayerPictureTest.drawPictureInsidePreviousLayerPreservesHostClip` | 1 | 0 / 0 / 0 | exit 133 — UNKNOWN |
| `latePictureChildRefusalIsAtomicAndSameSurfaceRecovers` after repair | 1 | 0 / 0 / 0 | exit 133 — UNKNOWN |

The capture/wire selector was already green and is recorded as preservation
evidence, not an invented RED.  The pre-fix atomic selector alone had the
semantic causal RED noted above (`1 / 0 / 0` methods, `F / E / S = 1 / 0 / 0`),
also followed by exit 133 UNKNOWN.

The known baseline full `W6aLayerPictureTest` class is retained separately:
its nine-method baseline has one stale writer-version assertion (`expected 14`,
current writer `15`) plus the same native worker 133.  It was not run or
classified as a Task 4c regression; only the two named preservation selectors
above are in this task's custody.

## Remaining concern

The repeated native worker exit 133 prevents a process-level all-green claim.
The XML assertions and public native evidence are green, but the native process
remains UNKNOWN.  Task 7 still owns the numerical checked-I64 B/B-1 budget
boundary evidence.

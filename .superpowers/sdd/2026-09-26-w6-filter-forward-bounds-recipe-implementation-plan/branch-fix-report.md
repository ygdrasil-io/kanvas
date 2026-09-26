# W6 whole-branch fix wave — 2026-09-26

Base: `4850f9281`, branch `codex/w6e-filter-bounds-recipe`.
Result: DONE_WITH_CONCERNS (three requested defects fixed; two pre-existing test failures and native worker 133 remain separately recorded).

## Scoped changes and self-review

- `W6aLayerGraphConstruction.kt`: prepare top-level Picture aggregate facts before scope reservation; contribute terminal written output to the enclosing scope. The prepared aggregate and evaluations are retained for emission. Inline lanes acquire final parent coordinates through the existing W4/W5 compiler only when their target changes; no filter recipe or captured scene is rediscovered there.
- `PictureStreamAggregateV1.kt`: add local-symbol `prepareRoot`, generalize the existing filter-root publisher to `publishRoot`. Frame command/aggregate/Picture occurrence cursors still advance at the original source-order emission point. Source/canonical identities and captured occurrence objects are retained. Filter-owned Picture roots interleave with drawPicture roots using the same publisher.
- Picture filtered draws seal their source to the finite W4 raster/scissor intersection restricted by their own reverse demand. The logical terminal demand remains separate. Mask output geometry drives shading; coverage and image lowering consume the prepared geometry/evaluation, including the previously unprepared top-level entry coverage allocation.
- Scope output propagation checks `writesParentDevice` before any transparent-black/output union. DST children retain their own source/filter/restore path but contribute no parent bounds.
- Tight parent bounds exposed an existing assumption in unfiltered Picture-layer restores: source and destination domains/origins were equal. Restore coordinates now intersect the device domains and independently localize source/destination. Disjoint non-writing restores retain a bounded 1x1 operand and the unchanged DST operation, which cannot write parent pixels. No filter re-evaluation or native work is added.
- Tests changed only in the two public contextual-bounds classes. No API, wire/schema, renderer, resource/key family, geometry primitive, font, codec, GM, dashboard or reference change. No `PlanResourceId` entered a recipe. Existing Begin → children → Seal → reader emission order remains unchanged; local-symbol publication only rebinds identifiers on the prepared tree.
- Existing `warmPictureReplayRetainsColdBudgetAndIdentity` now uses B=308 instead of 316: the former number included two 2x1 source allocations for an inline 1x1 draw. Both are now 1x1. The cold/warm B/B−1, atomic readback sentinel and same-Surface recovery proof is retained, not weakened.

## Public oracles and exact budgets

All expected arrays and B values are derived before Surface/PictureRecorder creation. Each admitted rendering asserts Render + Readback evidence. Every new exact-budget refusal leaves its readback sentinel unchanged and recovers on the same Surface after discard/re-record.

| Witness | Physical derivation | B |
| --- | --- | ---: |
| tiny top-level Picture in parent | root 64x1=256 + readback=256 + parent/aggregate/shaded/Crop four 1x1 targets=16 + frame/solid/graph-texture material rows=48 | 576 |
| tiny Crop draw in 64x1 Picture cull | root/readback=512 + coverage/shaded/Crop three 1x1 targets=12 + frame/solid rows=32 | 556 |
| tiny Matrix CLAMP draw in 64x1 Picture cull | previous geometry 556 + existing W6d program lease 4096; coefficients are frozen in the program | 4652 |
| filtered DST child with writing sibling | root6x1=24 + parent/child/coverage/shaded/Offset five 1x1 targets=20 + frame row16 + two solid material rows32 + readback256 | 348 |
| existing cold/warm Picture Crop | root2x1=8 + coverage/shaded/Crop three 1x1 targets12 + frame/material32 + readback256 | 308 |

The CLAMP oracle is exact: kernel [1,0,0] with offset(1,0) samples the left neighbour of the lone blue texel at x=1. Its finite one-texel source clamps to blue at x=1; x=0 and all other texels stay transparent. The baseline instead sampled transparent x=0 and displaced blue to x=2.

Initial draft B figures omitted one graph-texture material row for the top-level Picture and one solid material row for the DST fixture; the first Matrix figure incorrectly counted coefficient storage instead of the frozen program lease. These arithmetic errors were corrected from the existing resource/lease contract before final boundary claims. The corrected B576/B556/B348 witnesses were rerun against exact HEAD production sources, not inferred from the failing initial budgets.

## RED evidence and commands

Every command below uses `rtk ./gradlew :kanvas:test`. RED assertions occurred before their corresponding production correction. Initial shell attempt was blocked by Gradle's wrapper lock outside the sandbox; rerun with approved Gradle/cache/native-worker access. No such environment failure is counted as RED.

1. `/tmp/w6-branch-red.log`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.tiny*' --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingChildLayerKeepsWritingSiblingBudgetAndRecovers'`
   JUnit Picture 2/2 failures, Surface 1/1 failure; initial budgets 560/556/332. Native133, Gradle1.
2. `/tmp/w6-branch-clamp-red.log`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.filteredPictureDrawClampsAtItsRasterEdge'`
   JUnit1/1 failure: byte6 expected255 actual0; actual blue appeared at x=2. Native133, Gradle1.
3. `/tmp/w6-branch-dst-corrected-red.log`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingChildLayerKeepsWritingSiblingBudgetAndRecovers'`
   With the DST output check removed (original owner behavior), JUnit1/1 failure: requires352 > B348. Native133, Gradle1.
4. `/tmp/w6-branch-exact-baseline.log`: both modified production owners were temporarily restored exactly from `git show HEAD:<path>` using apply_patch; current work was saved and restored with apply_patch after the run.
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.tiny*' --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.interleavedPictureSourcesKeepIdentityAndInlineParentCoordinates' --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingChildLayerKeepsWritingSiblingBudgetAndRecovers' --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest.clipped rounded rect mask blur preserves its frozen analytic coverage'`
   Picture3 tests/2 failures: top-level Picture requires796 > B576; filtered draw requires1044 > B556; interleaved nested-layer preservation already passes baseline. Surface1/1 failure requires352 > B348. Mask1/1 failure described below. Native133, Gradle1. Copied XML: `/tmp/w6-branch-baseline-picture.xml`, `-surface.xml`, `-mask.xml`.
5. `/tmp/w6-branch-preservation-red.log`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.interleavedPictureSourcesKeepIdentityAndInlineParentCoordinates' --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest.clipped rounded rect mask blur preserves its frozen analytic coverage'`
   Newly narrowed parent makes nested Picture restore fail publicly with `Failed requirement.`; mask remains the identical baseline failure. Picture1/1 and mask1/1 failures, native133/Gradle1. The Picture failure is fixed by device-domain restore coordinate intersection.
6. `/tmp/w6-branch-unfiltered-dst-red.log`:
   `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.disjointNonWritingUnfilteredChildRetainsValidRestore'`
   JUnit1/1 failure `Failed requirement.` after parent shrinking; fixed by valid bounded DST restore operands. Native133, Gradle1.

## GREEN iterations and complete final verification

Intermediate selectors (same complete Gradle prefix as above):

- `/tmp/w6-branch-dst-green.log`: `--tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingChildLayerKeepsWritingSiblingBudgetAndRecovers'`; still refused at initial B332 because actual348; corrected accounting as recorded above.
- `/tmp/w6-branch-source-green.log`: `--tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.tinyFilteredPictureDrawKeepsFiniteSourceBudgetAndClampEdge' --tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.filteredPictureDrawClampsAtItsRasterEdge' --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest.nonWritingChildLayerKeepsWritingSiblingBudgetAndRecovers'`; standalone CLAMP and DST green, Matrix budget still lacked4096 lease (3 tests/1 failure).
- `/tmp/w6-branch-picture-green.log`: `--tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest'`; 6 tests/2 failures: omitted graph material row and old warm budget boundary. Corrected the physical additions without changing pixel or refusal assertions.
- `/tmp/w6-branch-picture-green2.log`: same class selector; 6/0.
- `/tmp/w6-branch-picture-final.log`: same class selector after interleaving witness; 7/0.
- `/tmp/w6-branch-nested-green.log`: `--tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest.interleavedPictureSourcesKeepIdentityAndInlineParentCoordinates'`; 1/0 after nested coordinate correction.
- `/tmp/w6-branch-surface-final.log`: `--tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest'`; 12/0.
- `/tmp/w6-branch-picture-verified.log`: `--tests 'org.graphiks.kanvas.picture.W6FilterBoundsRecipePictureTest'`; 7/0 with top-level playback and nested drawPicture variants, both memory/wire.
- First serialized preservation matrix used the class selectors below except the contextual Picture and W6aLayerPicture classes; logs `/tmp/w6-branch-<package.Class>.log` and matching XML. No concurrent Gradle selectors ran.

Final command for each row, run separately and serialized:
`rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.<package.Class>'`
Full output: `/tmp/w6-final-<package.Class>.log`; copied JUnit: `/tmp/w6-final-<package.Class>.xml`.

| package.Class | tests/failures/errors/skipped | Gradle | native |
| --- | --- | ---: | --- |
| surface.W6FilterBoundsRecipeSurfacePixelTest | 12/0/0/0 | 1 | 133/UNKNOWN |
| picture.W6FilterBoundsRecipePictureTest | 7/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6aNestedLayerSurfacePixelTest | 10/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6bImageBlurSurfacePixelTest | 10/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6cComposeSurfaceTest | 4/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6dLightingSurfacePixelTest | 31/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6dAdvancedSamplingSurfacePixelTest | 7/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6bMaskBlurAutoLayerSurfacePixelTest | 10/1/0/0 | 1 | 133/UNKNOWN |
| picture.W6cSpatialDagPictureTest | 3/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6dPictureFilterSurfacePixelTest | 20/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6dBackdropPreviousSurfacePixelTest | 9/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6aLayerBudgetRecoverySurfacePixelTest | 10/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6bBudgetRecoverySurfacePixelTest | 2/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6bFilterAdmissionRecoverySurfaceTest | 26/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6cSpatialDagAdmissionSurfaceTest | 2/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6cSpatialCacheRecoverySurfaceTest | 6/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6dAdvancedRecoverySurfacePixelTest | 5/0/0/0 | 1 | 133/UNKNOWN |
| surface.W6dMagnifierReverseDemandSurfacePixelTest | 4/0/0/0 | 1 | 133/UNKNOWN |
| picture.W6dPictureRuntimeEffectPictureTest | 6/0/0/0 | 1 | 133/UNKNOWN |
| picture.W6aLayerPictureTest | 9/1/0/0 | 1 | 133/UNKNOWN |

Final totals: 193 tests, 191 passed, 2 pre-existing failures, 0 errors/skips. The 19 contextual Surface/Picture tests are all green. This is not a globally passing native suite.

`rtk ./gradlew :gpu-plan:compileKotlin :kanvas:compileTestKotlin` → exit0, BUILD SUCCESSFUL (`/tmp/w6-branch-compile.log`). `rtk git diff --check` and `rtk git diff --cached --check` → exit0. The staged set contains only the five scoped files above, including this report.

## Separate pre-existing concerns / rulings

1. `W6bMaskBlurAutoLayerSurfacePixelTest.clipped rounded rect mask blur preserves its frozen analytic coverage`: expected pixel46 RGBA(147,0,0,76), actual(0,0,0,0). Reproduced identically on exact HEAD production owners and final work. Controller explicitly directed keeping its owner/test outside this wave. Its source fixture has neither Picture nor layer. Do not describe this as native-only UNKNOWN: it is a real pre-existing JUnit failure.
2. Extra `W6aLayerPictureTest.currentWriterUsesPicture14Schema8`: expected version14, actual15. Writer inspection confirms `SceneArchiveCodec.pictureVersion=15`, `schemaVersion=9` already on `codex/w6d-advanced-effects` at lines43/45; `Picture.CURRENT_STABLE_WIRE_VERSION=15` also pre-exists this prerequisite branch. The prerequisite's existing codec diff only adds schema9 to clip decoding, not writer versions. This wave touches neither writer nor archive test. Eight remaining tests in that class pass. Controller explicitly directed documenting without changing its archive owner/test.
3. All 20 final GPU-worker processes terminate133 after writing XML. Each Gradle invocation exits1; compilation exits0. No global PASS or native success is claimed.

User-owned `.superpowers/sdd/2026-09-22-w6d-advanced-effects-implementation-plan/progress.md` remains untouched by this implementer and unstaged. No push, PR, rebase, merge, or subagents. Controller owns one scoped Sol re-review.

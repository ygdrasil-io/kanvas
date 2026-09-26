# Task 1 report — contextual filter recipe

## Current status

**DONE_WITH_CONCERNS — implementation complete, external Sol review pending.**
Concerns are validation limits (native worker133 UNKNOWN, one confirmed baseline
RRect failure and non-exhaustive identity coverage), not an unfinished migration.
The earlier Terra `DONE_WITH_CONCERNS` claim was withdrawn. `d033c7cb8` remains a
partial RED/GREEN starting checkpoint, not an accepted implementation.
This report records the subsequent Astra migration, diagnostic history and validation limits.

## Committed checkpoints

- `d033c7cb8`: Terra's initial public witnesses and partial forward propagation.
- `f8694d6` — bind ordered contextual topology once. Compose binds inner then outer;
  Merge/Blend retain positions, repeated/equal-distinct captures remain separate.
- `36b8520` — inverse demand and terminal classifications use the same bound topology;
  per-operation output demands and separate displacement input demands are retained.
- `1c8b285` — all 22 image-family decisions extracted to resource-free symbolic
  operation descriptors; physical lowering substitutes IDs in existing cursor order.
  Picture discovery uses a local semantic draft and publishes the three frame cursors
  at the original physical emission point.
- `f8b6e1e` — mask Blur/Shader/Table descriptors use the same lowering; explicit W6a
  scopes evaluate once before parent reservation and retain the evaluation for freeze.
  Empty evaluated output is distinguished from absence of a filter. Shared Picture
  domain, DrawColor and W4/W5 lane helpers extracted from physical emission.

- `40b3596f2` — compiler-owned occurrence identity rebinding and public interleaving/replay RED witness.
- `9e196cfd6` — exact prepared Picture W4/W5 raster facts, mask footprint/content split,
  nested-layer/DrawColor facts, deferred physical lane rebinding; late pass-scan removed.
- `7d82f7a43` — `feat(gpu-plan): evaluate contextual filter bounds before layer reservation`:
  every operation/phase consumes its reverse demand; W6a retains the physical source,
  freeze requires evaluated descriptors, direct callers supply consumer demand,
  semantic regions and positional demands remain immutable, null remains empty.

No renderer/wire/public semantic contract was changed. The unrelated dirty W6d
ledger is never staged. No diagnostic replacement or printStackTrace remains.

## RED / GREEN evidence

All tests are public Surface/Picture pixel tests with Render + Readback checks.
No infrastructure test, mock, reflection, GM or dashboard was added.

### Original RED

Before Terra production changes, `W6FilterBoundsRecipeSurfacePixelTest` failed in
`nestedOffsetAndBlurExpandOnlyTheirProducedOutput` and
`nestedLightingRetainsSourceEdgeAndTransparentOutput` at public pixel assertions.
The independent lighting oracle precondition succeeded. The Compose round-trip
witness already passed on baseline and is preservation coverage, not causal RED.
After the initial patch, the three methods passed. Native worker 133 stayed UNKNOWN.

### Serialized migration runs

- Topology checkpoint: Compose 4/4 + MultiInput 5/5 XML green; worker6 exit133.
- Demand checkpoint: Compose 4/4 + MagnifierReverseDemand 4/4 +
  BackdropPrevious 9/9 XML green; worker7 exit133.
- Image descriptor checkpoint: first run 65/69 green (four failures) revealed a real reference
  identity requirement: the terminal evaluation key must be the same object as the
  FilterPass key. A per-lowering IdentityHashMap now reuses the lowered key.
  Rerun: new witnesses3, ImageBlur10, DropShadow6, Compose4, MultiInput5,
  Lighting31, AdvancedSampling7, RuntimeImageOpacity3 = 69/69 XML green;
  worker9 exit133. No global PASS is claimed.
- Mask/Picture intermediate: MaskBlurAutoLayer9/10, MaskShaderTable12/13,
  PictureFilter18/19; worker10 exit133. The mask-only null-image-root error was fixed.
- W6a memoization: new3/3 + NestedLayer10/10 + MaskShaderTable13/13;
  PictureFilter18/19 (Crop remaining); worker11 exit133.
- Before `f8b6e1e`: compileKotlin + compileTestKotlin exit0. New3 + NestedLayer10 +
  ImageBlur10 + Compose4 + Lighting31 + AdvancedSampling7 + MaskShaderTable13 =
  78/78 XML green; worker14 exit133.

The usual command prefix is `rtk ./gradlew :kanvas:test --tests
'org.graphiks.kanvas.surface.<Selector>'`. Gradle uses require_escalated for the
existing ~/.gradle cache; approvals were granted. Initial non-escalated Git staging
was denied by the sandbox, then repeated successfully with approved escalation.

### RRect mask baseline diagnosis (not a Task 1 regression)

`W6bMaskBlurAutoLayerSurfacePixelTest.clipped rounded rect mask blur preserves its
frozen analytic coverage` fails at pixel46/channel0:
expected147, actual0, delta147; expected RGBA=(147,0,0,76), actual=(0,0,0,0).
The failure remained after restoring only the original physical mask evaluator
(worker12 exit133), then with all relevant production sources temporarily replaced
by exact `d033c7cb8` contents (worker13 exit133). The same one-test XML assertion
failed in both comparisons. Every diagnostic substitution was restored immediately,
with no branch/index change; git status/diff --check verified restoration.
The controller directed that this preexisting baseline failure remain out of scope.

### Picture RED and identity-rebinding checkpoint

Initial existing public failure before Picture extraction:
`transformedPictureCropKeepsBlurHaloOutsideCull`:
`Frozen known content bounds RectI32(left=0, top=0, right=4, bottom=3)
escape SizeI32(width=1, height=1) at Point2I32(x=1, y=0).`

New public witness added before activating any prepass:
`interleavedDirectAndFilterPicturesRetainOrderAfterWireReplay`. It records direct
red Picture, filter-owned blue Picture, direct green Picture, then a red Picture
filter on a saveLayer; memory and wire replay use an independent four-pixel oracle
defined before Surface construction.
It is RED before rebinding activation:
`Frozen desired output bounds RectI32(left=-1, top=0, right=3, bottom=1)
escape SizeI32(width=4, height=1) at Point2I32(x=1, y=0).`
Worker15 exit133. The first memory render failed at this RED checkpoint.

Compiler-owned rebind compiles after correcting mechanical imports and the
explicit legacy ImageDraw exclusion. One compile and selector accidentally
overlapped for a few seconds because a yielded compile session was mistaken for a
completed process. Both sessions were closed; neither is retained as a gate.
A fresh strictly serialized invocation of
`rtk ./gradlew :gpu-plan:compileKotlin :kanvas:compileTestKotlin :kanvas:test
--tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'
--tests 'org.graphiks.kanvas.surface.W6cMultiInputSurfaceTest'`
then produced Picture18/20 and MultiInput5/5 XML (only the two exact REDs above),
worker2 exit133 on the new Gradle daemon. Compile tasks were successful/up-to-date.

## Identity separation and resolved architectural boundary

The controller authorized a targeted extension beyond the initial W6 file list.
Dependency first identified:

Picture initialized bounds → W4/W5 lane raster → occurrence synthetic carrier scene
→ final commandIndex → physical publication of Picture/frame cursors.

A lane ID depends on that synthetic scene canonicalId. W4a/W4b/W4c/W3 own private
hash formulas; W4d/W4e have their own formulas; General/W4e final-color envelopes
derive from the geometry lane ID. FrameSourceLayout also hashes lane IDs.
A naive command-only rekey would silently change identity/provenance.

The implemented internal interface retains the original compiler formula in
`PreparedSceneIdentityV1`; a prepared lane keeps its one synthetic carrier scene
(projected scene for W5e). `bindOccurrenceCommandV1` reindexes only that carrier
and typed command/atomic-group fields, reusing geometry/material/payload facts.
IdentityHashMap caches preserve shared W4e nativeColorPass reference identity
across geometry and color envelopes. No frame cursor advances during this step.
Preparation requires exact lane PlanId equality on a same-index rebind, then retains
the original prepared lane. Physical emission binds the final command index once,
at its original cursor position, with no geometry recompilation. Compiler-owned
hash callbacks use the original formulas byte-for-byte; canonical path/general
atomic-group helpers remain authoritative. No independent numeric dump of every
baseline PlanId was added (public-only tests); same-index equality is checked in
production, while cross-index identity/provenance/reference validators and the
public MEMORY/WIRE interleaving witness cover the activated path. Exhaustive
cross-index coverage of every W4e/path primitive is not claimed.

Second dependency reported before widening geometric compiler semantics:

known raster W4 → evaluateMask → shaded source domain/anchor →
OccurrenceSourceInput.materialCoordinateDraw (target rebasing and hard clip) →
compiled W4 raster.

In physical Picture emission, `appendPlannedDraw` receives the domain from
`allocateShadedOccurrenceSource(masked)`, after mask freeze. To move exact
knownContent earlier, the W4 raster must be available before mask evaluation.
The existing physical path instead supplies `knownContent=domain` to coverage,
which is exactly the cull/halo-as-content approximation the controller forbids.
An ID-only rebind does not resolve this semantic domain dependency. No duplicate
Picture mini-planner or approximation was introduced to bypass it.
Controller selected separation of mask footprint and initialized-content binding.
The immutable mask draft selects geometry once with known=null, then W4/W5 compiles
once in the historical post-mask domain. Actual raster seals known/produced into
that draft, without cull-as-content. Unmasked Pictures use prepared lane raster
and the historical physical source domain. Target-independent W4 extraction was
not implemented. Picture facts walk the already discovered typed aggregate draft,
not the original scene again; the same draft is published at Begin→children→Seal.
Physical DrawColor consumes its frozen clip; nested restore consumes selected
alpha/color-filter/blend facts. Source bounds/crop and initialized raster are distinct.

Picture20 + MaskShaderTable13 + Recipe3 were GREEN after activation and again after
restore/DrawColor audit (36/36 XML, workers3 and4 exit133 UNKNOWN). Existing Crop
and new interleaving MEMORY/WIRE both became GREEN; compile tasks exit0.

## Own-demand RED/GREEN and final lowering contract

- Added public `composeLightingProducesInItsOwnDemandOutsideTerminalClip`, with a
  literal white 1x1 oracle before Surface: Compose(Offset(-20), DistantDiffuse).
  RED was GPUPlanSurfaceTerminalException "Failed requirement" before readback,
  worker5 exit133. Wiring own demand alone retained that RED (worker6; diagnostic
  worker7). W6a still intersected the physical source with disjoint reverse demand,
  eliding it before evaluate. Preserving the independent physical source domain
  fixed it: Recipe4/4 GREEN, worker8 exit133. The temporary compiler diagnostic
  produced no extra stack and was removed; no diagnostic source was committed.
- `copyOutputI32(bound)` now bounds production for every selected image operation.
  Blur and Morphology propagate Y support to X; shadow translates output demand
  backward before blur. Per-operation four semantic regions and positional input
  demands are frozen, including distinct displacement-map/source demands.
- Physical phase targets remain the existing sampling domains. In particular,
  MatrixConvolution and RuntimeImageOpacity kernels require aligned local origins;
  they retain those domains and only their production is demand-bounded. No new
  renderer offset, public/wire field, allocation authority or kernel was introduced.
  Lighting may synthesize output on its own demand; ColorFilter uses the existing
  transparent-black execution fact; RuntimeImageOpacity alpha=0 has empty production.
  Picture production intersects the prepared crop and actual initialized content.
- `freezeImageOccurrence` and `freezeMaskOccurrence` require the already evaluated
  descriptor. They cannot call evaluate/bind or read captured DAG topology.
  Lowering checks physical source domain/local-to-device mapping against the recipe.
  Explicit scopes and filter-owned Picture reuse their memoized evaluation. Direct
  auto-layers evaluate once at the historical direct point; Task 2 still owns
  propagation of those outputs into earlier parent reservation.
- The first extended134 run exposed three direct-demand regressions: shadowOnly
  "W6b shadow has no sealed bounds", fractional shadow pixel21/channel2 expected58
  actual0, and Morphology MEMORY/replay expected red at x0/x2 but got transparent.
  Direct callers had supplied the raw source extent as desired. Supplying the real
  terminal clip/destination domain, without Task2 propagation, fixed all three.
  DropShadow6 + Morphology6 + Recipe4 =16/16 GREEN (worker11 exit133), then all134
  methods GREEN (worker12 exit133). No failures were hidden or assertions weakened.

## Final gates and auto-review

The extended command selects these public classes under `org.graphiks.kanvas.surface`:

| Selector | Methods |
| --- | ---: |
| W6FilterBoundsRecipeSurfacePixelTest | 4 |
| W6aNestedLayerSurfacePixelTest | 10 |
| W6bImageBlurSurfacePixelTest | 10 |
| W6cComposeSurfaceTest | 4 |
| W6dLightingSurfacePixelTest | 31 |
| W6dAdvancedSamplingSurfacePixelTest | 7 |
| W6dPictureFilterSurfacePixelTest | 20 |
| W6bMaskShaderTableSurfacePixelTest | 13 |
| W6bDropShadowSurfacePixelTest | 6 |
| W6cMorphologySurfaceTest | 6 |
| W6cMultiInputSurfaceTest | 5 |
| W6dRuntimeImageOpacitySurfacePixelTest | 3 |
| W6cSpatialBoundsSurfaceTest | 15 |
| W6bMaskBlurAutoLayerSurfacePixelTest | 10 |
| W6dBackdropPreviousSurfacePixelTest | 9 |
| W6dMagnifierReverseDemandSurfacePixelTest | 4 |

Each invocation is `rtk ./gradlew :kanvas:test` followed by one
`--tests 'org.graphiks.kanvas.surface.<Selector>'` per row. No parallel Gradle session
is retained as evidence. App XML lives in `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.*.xml`;
the separate `TEST-Gradle-Test-Run--kanvas-test.xml` records native worker termination.
Full157 run before final null-preservation audit:156 methods green, only the
unchanged baseline RRect failure above; worker13 exit133.

Final fresh verification, after the last production edit:

- `rtk ./gradlew :gpu-plan:compileKotlin :kanvas:compileTestKotlin`: exit0,
  BUILD SUCCESSFUL; final production compile task executed (7s).
- Full157 public-method replay, XML timestamps 2026-09-26T11:57:26Z–11:57:39Z:
 156 green, one RRect baseline failure with exactly pixel46/channel0 expected147
 actual0 (same expected/actual RGBA as above), no other failures/skips/errors.
 All134 core/extended methods, all9 BackdropPrevious and all4 MagnifierReverseDemand
 are green; MaskBlurAutoLayer is9/10.
- Gradle exit1, `Gradle Test Executor 14` exit133, native status UNKNOWN.
  The synthetic Gradle-run XML failure is not counted as an application test.
- `rtk git diff --cached --check`: exit0 before feature commit. The staged set was
  exactly five Task1 code/test files; unrelated W6d progress remained unstaged.
  No Gradle/tool process remains running. No diagnostic substitution remains.

Auto-review:

- Context binding and reverse/classification use one ordered topology; lowering
  never decides family/bounds. Positional duplicates are not collapsed.
- Source domain and input known are not reconstructed from terminal output.
  Null produced remains distinct from no evaluated filter, including physical
  scope bindings (the old nullable Elvis-to-domain was removed).
- Picture W4/W5 prepare occurs once, IDs bind at old cursors, no second scene walk
  or late pass-scan remains. Physical emission retains existing allocator/order.
- No renderer/wire/public production file changed. Compiler extensions are internal
  and were explicitly authorized by the controller. No new infrastructure test.
- No Task2 parent/direct propagation or Task3 budget audit is claimed. Existing
  physical allocation conservatism remains; this migration does not optimize every
  phase to a minimal target. Exhaustive pixels for every possible DAG are not claimed.
- RRect failure is confirmed baseline and out of scope by controller ruling.
  Native133 remains UNKNOWN, so no global Gradle PASS is claimed.
- No Sol reviewer was launched here; controller owns that independent review.

The verification-before-completion skill required fresh compiles/selectors and
separate application XML versus native/Gradle status. TDD guided the public REDs;
systematic debugging isolated both source-domain and caller-demand divergences.

# SDD ledger — plan: refactor/plans/2026-09-22-w6d-advanced-effects-implementation-plan.md

Base W6c reviewed: `01295d15d986be3fbc8898ba80287c9a3c86d24c`; branch `codex/w6d-advanced-effects` created clean from it. Specs read: `2026-09-16-w6-layers-effects-design.md` and `2026-09-22-w6b-w6e-stacked-delivery-design.md`. Seven tasks remain, sequential Terra implementation and Sol task review; GPU-native 133/134 means UNKNOWN.

## Preflight scan — cross-task dependencies

| Tasks | Producer → consumer / shared owner | Finding |
| --- | --- | --- |
| 1→2 | F64 helpers, FilterPass arms, lowerer → sampling families | Ordered; W6c graph is sole physical owner. |
| 1→3 | Lighting arm and immutable parameters → six lighting families | Ordered; no second operation hierarchy. |
| 1→4 | Picture arm/target contract → SceneSnapshot execution | Ordered; wire identity stays with render-ir. |
| 1→5 | Multi-ABI catalog/RuntimeImageOpacity arm → built-in execution | Ordered; W5h hash must remain unchanged. |
| 1→6 | FilterPass/target contract → backdrop and filtered previous | Ordered; copy/snapshot reuse W6 graph. |
| 1→7 | Frozen contracts/resources → budget and stack gate | Ordered; B/B−1 must include all later additions. |
| 2→3 | `EffectNode`, W6 graph/materializer → lighting | Sequential edits; avoid duplicate materializer authority. |
| 2→4 | `EffectNode`, capture, W6 graph/materializer → Picture | Sequential edits; same occurrence binding. |
| 2→5 | W6 graph/materializer → RuntimeImageOpacity | Sequential edits; same FilterPass type. |
| 2→6 | `W6aLayerGraphConstruction`/materializer → save ordering | Sequential edits; source sampling does not reorder children. |
| 2→7 | Resource/target accounting → final budget gate | Task 7 audits all sampling resources. |
| 3→4 | `EffectNode` and filter graph → Picture | Sequential; SceneSnapshot is not a lighting input. |
| 3→5 | `EffectNode` and filter graph → runtime | Sequential; distinct operation arms. |
| 3→6 | Graph/materializer → backdrop ordering | Sequential; lighting may consume backdrop result. |
| 3→7 | Lighting uniforms/targets → final accounting | Task 7 includes six families. |
| 4→5 | Picture tests, `EffectNode`, archive/catalog path → runtime wire | Task 5 extends Task 4's Picture witness, not another schema. |
| 4→6 | Picture replay + W6 layer graph → backdrop/previous wire | Task 6 extends Task 4 witness; snapshot identities preserved. |
| 4→7 | Picture source/leases → final budget/wire gate | Task 7 charges recursive scene resources. |
| 5→6 | RuntimeImageOpacity input + W6 layer graph → filtered backdrop/previous | Task 6 may use Task 5 built-in as discriminating filter. |
| 5→7 | Runtime program/uniform resources → final budget | Task 7 includes catalog ABI/hash preservation. |
| 6→7 | Snapshot/copy passes, layer scopes → final budget/recovery | Task 7 validates save-time order and atomicity. |

## Preflight scan — each task against itself

| Task | Tests vs implementation / files vs later touches | Finding |
| --- | --- | --- |
| 1 | Catalogue public test, pure math, operation arms, graph validation; later Tasks 2–6 execute arms | `RectF64.translateF64OrNull` and `.intersectF64OrNull` already exist in `RectProjectionF64.kt`; duplicate signatures in illustrative Task 1 snippet would not compile. |
| 2 | Public sampling/oracles precede capture-plan-native; later Task 7 re-runs shard | Coherent; independent oracle must remain test-only. |
| 3 | Six lighting public cases and oracle precede graph/native; Task 7 re-runs | Coherent; no hidden color-space conversion. |
| 4 | Picture filter memory/wire + mutation evidence precede scene snapshot graph | Coherent; Task 5 later extends its test file. |
| 5 | Additive registered IMAGE_FILTER and W5h preservation | Coherent; descriptor key must include ABI hash. |
| 6 | Backdrop/previous save order public witness before graph/native | Coherent; Task 7 later budgets snapshots. |
| 7 | B/B−1, F16 refusal, preservation, final review and PR | Coherent; native UNKNOWN is documented separately from XML. |

Ruling: Reuse existing `RectF64.translateF64OrNull` and `RectF64.intersectF64OrNull` in `math/geometry/.../RectProjectionF64.kt`; add only missing pure F64 helpers. — The spec requires math ownership, not duplicate extension declarations, and Kotlin signatures already exist. — If wrong, Task 2 may need an overload or semantic change, which must be reviewed against the existing callers.

Ruling: Task 1 adds immutable operation contracts and multi-ABI catalog but does not claim positive advanced rendering until Tasks 2–6; any temporarily non-executable operation arm must fail precisely before publication, never route to legacy or create a second graph. — The spec mandates vertical slices and one authority while later tasks own execution. — If wrong, Task 1's public API could expose a non-executable registered effect before Task 5; task review must check this boundary.

Task 1: implementer DONE at `ffe7e3d8b` from base `01295d15d`; public catalogue RED→GREEN 1/1, W5h catalogue 6/6, math/render-ir/gpu-plan/gpu-renderer compilation success. Native not exercised (UNKNOWN). Sol task review pending on `review-01295d15d..ffe7e3d8b.diff`.

Task 1: Sol task review ❌ Important — `Lighting` family/parameter validation uses `||` on mismatched cases and can accept a wrong pair (`DISTANT_DIFFUSE` + `Point`). Fix round 1/5 requested from original Terra implementer; base `ffe7e3d8b`.

Task 1: fix round 1/5 (1 addressed, 0 open — Lighting family/type exact match; commit `a48d5c36b`). Sol scoped re-review clean; no new breakage.
Task 1: complete (commits `01295d15d..a48d5c36b`, review clean). Public W6d catalogue 1/1, W5h catalogue 6/6, relevant module compiles 0; no native W6d work yet, UNKNOWN.

Task 2: implementer DONE_WITH_CONCERNS at `ba7e7e6c8` from base `a48d5c36b`. Three public REDs were W6c unsupported-family refusals; GREEN XML advanced sampling 3/3 and W6a bounds preservation 10/10, gpu-plan/gpu-renderer compiles 0. Both GPU workers exit 133 after assertions, native UNKNOWN. Magnifier mismatch traced to pixel-center tie; agent changed fixture without widening oracle tolerance. Sol task review pending on `review-a48d5c36b..ba7e7e6c8.diff`.

Task 2: Sol review ❌ Important ×3 — MatrixConvolution ignores frozen tileMode at edges; Magnifier witness does not discriminate inset; shader WGSL/program chosen in materializer after graph freeze. Fix round 1/5 requested from original Terra implementer, base `ba7e7e6c8`. Program-freeze issue is load-bearing for later W6d tasks.

Task 2: Ruling: Extend the existing W6 operation with a backend-neutral frozen program/binding descriptor (stable program/recipe ID, arity, specialization facts and uniform resource/offset/budget where needed), then let the renderer only materialize that descriptor into native WGSL and bind the frozen operands. Do not put WGSL in `:gpu-plan` or add a second graph/allocator. — The spec requires both backend-neutral planning and program choice before freeze; raw WGSL in the planner violates the former, materializer choosing by live operation values violates the latter. — If wrong, this descriptor may need a later Task 7 ABI/resource migration and a wider shader-catalog refactor.

Task 2: Terra fix attempt BLOCKED before commit: a strict per-family uniform-buffer design would span ResourceSpec/lifetime/budget/layout/upload. RED public tile-mode and inset-discriminating witness are left as five dirty task files; no destructive cleanup. Ruling: Escalate only the frozen-program binding subproblem to a fresh Astra implementer; allow a backend-neutral immutable specialization recipe/ID consumed deterministically by renderer, without a new uniform buffer if all parameters and bindings are frozen and budgeted. — This preserves one graph while bounding Task 2; if wrong, Task 7 must refactor program accounting or the final review will block the branch.

Task 2: Astra fix commit `f930cdfdb` from `ba7e7e6c8`: typed frozen recipe/program ID/binding on existing FilterPass, four convolution tile modes, inset-discriminating public witness. W6d XML 5/5 and W6a preservation 10/10; gpu-plan/gpu-renderer compiles 0; native workers exit 133 UNKNOWN. Sol scoped re-review pending on `review-ba7e7e6c8..f930cdfdb.diff`.

Task 2: fix round 1/5 (3 addressed, 0 open — tileMode, frozen program binding, inset witness; commit `f930cdfdb`). Sol scoped re-review clean; no new breakage.
Task 2: complete (commits `a48d5c36b..f930cdfdb`, review clean). Exact W6d sampling XML 5/5, W6a bounds XML 10/10, module compiles 0, native UNKNOWN.

Task 3: Terra implementer BLOCKED before code or test, base `f930cdfdb`: public/captured lighting API is 2D but Skia's six lighting families require 3D light/target vectors, alpha-derived normals, and specified diffuse/specular/spot equations. Existing `GPULighting.kt` is legacy and not a W6 authority. Report `task-3-report.md`. No RED/GREEN claim. This is an architectural scope upgrade: public API/IR/wire and numerical semantics require an approved design amendment before implementation; Task 4 is not dispatched while Task 3 remains load-bearing.

Task 3 design resumed: user approved breaking 3D-only API, explicitly rejected a retained 2D overload, requested an Astra review and execution after correction. Spec `refactor/specs/2026-09-23-w6d-lighting-3d-design.md` committed at `49254f8`, Astra review found four Important ambiguities (Z/surfaceScale mapping, finite admission domains, undefined normalization/power, per-edge Sobel bounds), all independently checked against pinned Skia sources and corrected at `0edbe98`. Existing plan amended at `e5ce423`; Task 3 now has a contract checkpoint before causal pixel RED and Task 4 consumes Picture 15/schema 9.

## Task 3 amended preflight

| Tasks | Producer → consumer / shared owner | Finding |
| --- | --- | --- |
| 2→3 | Frozen W6d sampling recipe/binding → lighting recipe | Same `FilterPass` and target graph; Task 3 extends typed recipe, not materializer selection. |
| 3→4 | Picture 15/schema 9, 3D IR/archive → Picture filter replay | Ordered; Task 4 explicitly consumes new writer and old non-lighting readers. |
| 3→5 | 3D archive and filter graph → runtime image opacity | Distinct node tags/arms; no W5h descriptor/hash change. |
| 3→6 | Lighting graph/materializer → backdrop and previous | Same save/restore order; no second attachment sampling. |
| 3→7 | Lighting program/target resources → final budget/recovery | Task 7 must count all frozen recipe resources. |
| 3 self | Public 3D API, math mapping, schema 9, pixel oracle, then task review | Contract migration compiles before causal RED; no compile failure mislabeled as RED. Historical v14 lighting fixture must be captured before changing the constructor. GM source migration stays out of scope. |
| 4 self | SceneSnapshot wire test and filter execution | Old Picture 14/schema 8 wording replaced by 15/schema 9 after Task 3. |

Ruling: Task 3 changes the six lighting signatures to `Point3F32`/`Vector3F32` without source-level 2D compatibility; old 2D lighting wire tags are explicitly rejected, while non-lighting old scenes remain decodable. — User authorized breaking changes in incubation and the spec forbids invented Z. — If wrong, external incubating callers and old lighting Pictures require explicit migration rather than silent replay.

Ruling: Keep `integration-tests/skia` GM sources outside the W6d patch even if the breaking API leaves their separate module's compile gate pending; record that migration as an integration follow-up, with no GM runs or score changes. — The W6 scope explicitly excludes GMs. — If wrong, a future full-repo compile may remain blocked until the separately scoped GM source migration.

Task 3: resumed with fresh Terra implementer `/root/w6d_task3_3d_lighting_terra`, BASE `e5ce423ab9d18987610b5b7021dbb02745f5e729`, brief `task-3-brief.md`, report `task-3-report.md`; no parallel implementation task is dispatched.

Task 3: partial contract checkpoint `cf57e09d2` from BASE `e5ce423ab` — public 3D API, 3D IR/canonical IDs, Picture 15/schema 9, old 2D-lighting fail-closed, public Picture selector XML 2/0/0/0, render-ir and kanvas test compiles 0. No lighting pixel/renderer claim. Implementer returned `PARTIAL` outside the status contract; resumed the same Terra agent with explicit remaining Steps 2 and 4–7. Its report also notes `:render-ir:compileTestKotlin` failure at unchanged `MaterialNodeTest.kt:132`; this does not block the listed Kanvas/gpu-plan/gpu-renderer selectors, and has not been claimed green.

Task 3: Terra math checkpoint `e39fd6c2b` adds checked affine 3D point/vector/Z mapping in `:math`; `:math:matrix:compileKotlinJvm` exit 0. The original six-family Task 3 remained too large and was reported BLOCKED after these two completed commits. No positive lighting pixel or renderer gate is claimed.

Ruling: Split the original Task 3 after its two code checkpoints into revised Task 3 (contract/wire/math), Task 31 (distant-diffuse public vertical pixel path), and Task 32 (other five lighting families, edge/refusal/replay variants), then resume Task 4. — The user authorized continuous W6d execution after Astra correction; the Terra implementer identified task size, not an unresolved design decision, as the block. Each slice has its own causal RED, GREEN and Sol review gate, while the single W6 FilterPass graph remains the only authority. — If wrong, a family interaction discovered in Task 32 may force Task 31's recipe/program interface to be revised and reviewed again. Plan amendment commit `77c12f8`.

## Amended Task 3/31/32 preflight

| Tasks | Producer → consumer / shared owner | Finding |
| --- | --- | --- |
| 3→31 | Public 3D nodes, Picture 15/schema 9, `LayerMappingF64` helpers → distant diffuse | Ordered; Task 3 has no positive pixel claim, so Task 31 owns causal RED and first frozen lighting recipe. |
| 3→32 | Point/spot 3D capture and mapped Z → five more families and Picture replay | Ordered; Task 32's pixel/replay selector cannot be required by Task 3's wire-only gate. |
| 31→32 | Distant recipe, Sobel sampling and one FilterPass path → five family recipes | Sequential edits to the same planner/materializer; Task 32 must not introduce a second graph. |
| 31/32→4 | Picture 15/schema 9 and lighting FilterPass → SceneSnapshot filter | Ordered; Task 4 consumes the finalized wire and renderer route. |
| 31/32→7 | Program IDs, targets, halos and uniforms → final budget | Task 7 audits all six families after Task 32. |
| 3 self | Historical fixture, 3D contract, mapping and wire-only tests | Coherent under revised outcome; positive pixels deferred explicitly. |
| 31 self | Distant public oracle RED, plan freeze, materializer GREEN, review | Second same-XY/different-Z distant witness added; both halo edge modes and transparent-black output named. |
| 32 self | Five-family RED, full oracle and admission/replay variants | Coherent; consumes the Task 31 path and adds no second FilterPass. |

Task 3: Sol review ❌ Important — `W6dLightingPictureTest` lacks public `Picture.ops` comparison for two Z values and a historical non-lighting Picture decode fixture. Fix round 1/5 requested from original Terra implementer; review HEAD `e39fd6c2b`. Reviewer ⚠️ cannot verify explicit DAG sharing and old non-lighting reader preservation from the diff/report; resolve via focused existing tests or the new historical fixture before completion. Deferred minors: focused math behavior for shear/perspective/singular/overflow not yet tested; stale v14 comments in SceneArchiveCodec; existing Gradle warning noise.

Task 3: fix round 1/5 (historical non-lighting fixture addressed; two-Z public `Picture.ops` distinction still open; commit `5721fc680`). Scoped Sol re-review found no new breakage but NOT ADDRESSED overall: test compares `first.ops` to decoded `first.ops`, and Z difference only in wire bytes. W6b explicit DAG-sharing selector XML 2/0/0/0 and W6d Picture XML 2/0/0/0; this resolves the previous ⚠️ custody. Fix round 2/5 requested.

Task 3: fix round 2/5 (1 addressed, 0 open — distinct public `Picture.ops` for distinct Z; commit `5048d9c9d`). Scoped Sol re-review clean, no new breakage. W6d Picture XML 2/0/0/0; native not run/UNKNOWN. Task 3: complete (code commits `e5ce423ab..e39fd6c2b`, test fixes `5721fc680` and `5048d9c9d`, review clean). Deferred minors remain for final whole-branch triage; no GPU lighting claim.

Task 31: fresh Terra implementer `/root/w6d_task31_distant_terra` dispatched from BASE `5048d9c9d0025ed266c9850bb6e1014507493bac`; brief `task-31-brief.md`, report `task-31-report.md`. Task 32 and Task 4 remain undispatched until Task 31 Sol review is clean.

Ruling: Task 31 may modify `gpu-plan/.../W6bFilterGraphConstruction.kt` in addition to the illustrative file list, to extend `freezeImageOccurrence`, the existing sole FilterPass construction hook; the four Sobel edge modes belong in the existing typed W6d frozen recipe. — `W6aLayerGraphConstruction` delegates image occurrences to that hook, so excluding it would keep lighting refused or force a parallel planning authority. — If wrong, Task 32 may need to reorganize where the shared recipe is constructed, but no second graph or renderer-side decision is authorized.

Task 31: Terra implementer DONE_WITH_CONCERNS at `ea4e13dab` from BASE `5048d9c9d`, with doc-only plan correction `40671cab1` in range. Public RED 3/3 failures were prior W6b `unsupported_family` semantic refusals; GREEN XML distant lighting 3/0/0/0 and W6a restore preservation 9/0/0/0. `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin`, `:kanvas:compileTestKotlin` exits 0. Both native workers exited 133: UNKNOWN, not full green. Sol task review pending on `review-5048d9c9d..ea4e13dab.diff`.

Task 31: Sol review ❌ Important ×2 — `DistantLitDiffuse(Crop(...))` uses child desiredOutput instead of consumer demand, losing unbounded transparent-black output; direct lighting computes but discards terminal clip before freezing desiredOutput, overallocating a full parent target. Fix round 1/5 requested from original Terra implementer, review HEAD `ea4e13dab`. Reviewer ⚠️ unchanged global budget/lifetime validation cannot be established from task diff; controller will verify a focused path after the fix. Native 133 stays UNKNOWN.

Ruling: `PlanPasses.kt` and `GPUW6aEncoderScopesV1.kt` were listed as modify files in the Task 31 brief, but their existing generic `FilterPass` code already selects a frozen binding and encodes it without a new hunk; inspect them as dependencies rather than make no-op changes. — The architectural spec binds the behavior, not gratuitous edits, and the reviewer confirmed the existing paths at `PlanPasses.kt:1267` and `GPUW6aEncoderScopesV1.kt:35`. — If wrong, a later family may require a real encoder or validation change in Task 32, which must then be tested and reviewed.

Task 31: controller resolved review ⚠️ about unchanged global resource validation via focused read-only path: `W6bFilterGraphConstruction.resource(FilterTarget)` creates specs; `W6aLayerGraphConstruction.kt:2102–2124` seals every frozen filter spec as a frame-local resource and calls `W6aLayerPlanBudget.peak`; that budget compares both `RenderGraph.peak` and the checked physical sum to `maxFrameLocalBytes`; `W6aLayerGraphValidation.kt:334–360` validates FilterPass output role, render/sample usages, extent and publication; `RenderGraph.kt:538–571,634` validates resource lifetimes and peak. No separate unbudgeted W6d target path found. The two geometry findings are still a real risk until scoped re-review passes.

Task 31: Terra fix round 1/5 commit `60597a695` from review HEAD `ea4e13dab`; public RED 2/5 (Crop output pixel and clip/budget refusal), GREEN XML W6d 5/0/0/0, W6a preservation 9/0/0/0, gpu-plan and kanvas-test compiles 0. Native exits 133 UNKNOWN. Scoped Sol re-review pending on `review-ea4e13dab..60597a695.diff`.

Task 31: scoped Sol re-review round 1/5 — consumer demand NOT ADDRESSED for `Compose(inner=Crop, outer=DistantLitDiffuse)`; direct clip budget finding ADDRESSED; new Important ×2: clipping physical source removes Sobel halo outside output clip, and null clip intersections now reject an occurrence that should become `sealedNoOp`. Fix round 2/5 requested from original Terra implementer; no Task 32 dispatch. Native remains UNKNOWN.

Task 31: fix round 2/5 (3 addressed, 0 open — Compose consumer, Sobel halo outside terminal clip, disjoint clip sealedNoOp; commit `fe0e3c739`). Public RED 3/8 failures, GREEN W6d XML 8/0/0/0 and W6a restore XML 9/0/0/0; gpu-plan/kanvas-test compiles 0. Scoped Sol re-review clean, no new breakage. Task 31: complete (commits `5048d9c9d..fe0e3c739`, review clean). Native exits 133 remain UNKNOWN.

Ruling: Carry the Sol out-of-scope observation about `ColorFilter(DistantLitDiffuse(...))` terminal demand into Task 32 as a named public composition witness and planner correction, rather than reopen a clean scoped Task 31 fix diff. — `hasDistantDiffuseTerminal` currently traverses Compose but not ColorFilter, so a wrapping ColorFilter can still re-bound lighting on transparent black; Task 32 already extends the same shared planner for all six families and must make consumer demand compositional. — If wrong, the added test may show a different ColorFilter alpha contract or require a deeper W6 bounds refactor, which would be reviewed before Task 4.

Task 32: fresh Terra implementer `/root/w6d_task32_lighting_terra` dispatched from BASE `51536252662a4cf2d5a5a5ac58964c07dc59987b` (plan addendum commit); brief `task-32-brief.md`, report `task-32-report.md`. No Task 4 implementation concurrently.

Task 32: Terra implementer BLOCKED before commit. Public five-family RED was prior W6b `unsupported_family` refusal; prototype extends the existing frozen FilterPass/recipe and compiles `:gpu-plan:compileKotlin` and `:gpu-renderer:compileKotlin`, but public XML remains red `channel 0 expected=175 actual=121` during the five-family oracle loop, native exit 133 UNKNOWN. Remaining unstarted: admission/refusal/recovery variants, ColorFilter demand, Picture replay. Dirty prototype preserved (10 task files, no destructive cleanup). Do not dispatch Task 4 or claim Task 32 done. Controller begins systematic root-cause investigation and will split the oversized task after identifying the numeric mismatch.

Task 32 root cause (systematic debugging, read-only Terra diagnostic `/root/w6d_task32_spot_root_cause`): the first `175/121` mismatch is `DISTANT_SPECULAR` pixel (0,0), not spot. The independent oracle incorrectly applies point `location−surface` to all families; the planner/shader correctly use distant direction. For fixture N≈(-.514496,-.514496,.685994), L=normalize(1,0,1), H≈(.382683,0,.923880), dot(N,H)^2≈.190870364 → sRGB R≈121, linear alpha≈49. Oracle also encodes specular alpha in sRGB, incorrectly expecting 121 rather than 49. Separately, pinned Skia `SkKnownRuntimeEffects.cpp` uses `pow(cosAngle,falloff) * edgeRamp`; both current oracle and WGSL use `pow(edgeRamp,falloff)`, so spot diffuse fixture should be ~180, not 197. No code changed during diagnosis; prototype remains dirty. This closes the numeric ambiguity, not the Task 32 implementation.

Ruling: Split blocked original Task 32 into revised Task 32 (five-family numeric core, mapped geometry, independent pixels) and new Task 33 (finite-domain/degenerate/refusal/recovery, ColorFilter composition, Picture replay); both require separate Sol review before Task 4. Preserve the uncommitted prototype and resume its original Terra implementer for Task 32 with the identified causes, then use a fresh Terra for Task 33. — The prototype compiles and the blocker was a false-red oracle plus a separate spot-equation defect, while the original task also bundled a large acceptance/replay gate. Isolating numeric correctness prevents incorrect pixels being hidden by later tests. — If wrong, Task 33 may reveal a validation/edge interaction requiring revision and re-review of the Task 32 shader or planner. Plan amendment pending commit.

## Task 32/33 amended preflight

| Tasks | Producer → consumer / shared owner | Finding |
| --- | --- | --- |
| 31→32 | Distant recipe/Sobel/FilterPass → five mapped light recipes | Ordered; one target graph and materializer, no second authority. |
| 32→33 | Six-family numeric pixels/program IDs → boundary/recovery/wire | Ordered; public oracle baseline must be correct before admission/replay variants. |
| 33→4 | Picture 15/schema 9 lighting replay → Picture filter SceneSnapshot | Ordered; Task 4 sees stable wire and renderer behavior. |
| 32/33→7 | Six lighting resources/targets/refusals → final budget | Task 7 audits B/B−1 after all family resources. |
| 32 self | One named public witness per new family, distant/point distinction, spot falloff and linear alpha | Corrects false RED and adds causal spot RED before shader fix; no full admission claim. |
| 33 self | Signed/exponent/degenerate/admission tests plus wrapper demand and Picture replay | Tests precede smallest production correction, preserves old readers and same-surface recovery. |

Task 32 amended plan commit `06cf9c2de`; refreshed briefs `task-32-brief.md` (25 lines) and `task-33-brief.md` (24 lines). Original Terra implementer `/root/w6d_task32_lighting_terra` resumed with the root-cause details and narrower numeric-only Task 32; dirty prototype retained as intentional in-scope work. Task 33 is not dispatched until Task 32 Sol review passes.

Task 32 affine continuation on HEAD `1bc176d30`: added a separately named public Render+Readback point-diffuse witness using `Canvas.setMatrix(diag(2,3)+(5,7))`. The affine maps `(1,0,1)` to device `(7,7,2.5)` and `surfaceScale=1` to `2.5`; the source layer origin is `(5,7)`, so the oracle correctly uses layer coordinates `(2,0,2.5)`. Initial oracle mixed device and layer coordinates (pixel channel 0 `expected=140 actual=237`); rebasing the oracle corrected this false RED without a production change. Separate oracle discriminants for mapped XY, light Z and surfaceScale all differ from their unmapped variants; the affine witness passes. Final sequential gates: gpu-plan compile 0, gpu-renderer compile 0, kanvas test compile 0. W6d console lists all 15 methods PASS, but its class XML is absent after worker exit 133; aggregate XML is `tests=1 failures=1 errors=0 skipped=0` for that process failure. W6a restore class XML is `tests=9 failures=0 errors=0 skipped=0`; aggregate XML likewise reports the worker exit 133 (`tests=1 failures=1 errors=0 skipped=0`). Native status is `UNKNOWN`, separate from method assertions. Task32 remains subject to Sol review; Task33 was not started.

Task 32: Sol review ❌ Important ×3 — unguarded negative/non-finite spot power, F32 overflow in frozen `target−location` direction, and missing spot numeric/cutoff witnesses. Fix round 1/5 commit `ce600f8c9`: guarded WGSL/oracle, F64 normalization helper in `:math`, explicit `[180,180,180,255]` and cutoff witness. W6d XML 16/0/0/0, W6a restore XML 9/0/0/0; native 133 UNKNOWN. Scoped Sol re-review: two calculations ADDRESSED; spot edge witness not causally discriminating, XML custody not preserved. Fix round 2/5 commits `2e8a988a5`, `a5b8a1a32`: exponent-2/cutoff-36 witness pins `[116,116,116,255]`, controlled legacy-formula mutation RED `actual=98`, final W6d XML 17/0/0/0 read and recorded; native 133 UNKNOWN. Scoped Sol re-review clean, no new Critical/Important. Task 32 complete (commits `06cf9c2de..a5b8a1a32`, review clean).

Ruling: The finite extreme spot Surface probe (`±1.8e38` coordinates) yielding black rather than the oracle's `[222,222,222,255]` is a distinct backend computation gap, not the reviewed planner direction-overflow finding. Carry an explicit public pixel/admission investigation into Task 33's finite-domain slice; do not claim full finite-domain correctness from Task 32's ordinary-range pixels. — Task 33 owns finite lighting admission and degenerate behavior, while Task 32's scope was the six-family numerical core and frozen direction; the scoped reviewer confirmed the planner fix. — If wrong, Task 33 may require shader-side scaled subtraction or a narrower documented admission boundary, and W6d cannot be called complete until that behavior is resolved or explicitly ruled on against the spec.

Ruling: The earlier `ColorFilter(DistantLitDiffuse(...))` demand observation is assigned to Task 33 after the Task 32/33 split, not silently dropped. — Task 33's brief now names the public composition witness and planner correction. — If wrong, downstream Picture/backdrop work may reveal a broader W6 demand-propagation issue and require re-review.

Task 32: fix round 1/5 pending re-review — Sol findings addressed on the numeric slice: the spot cone now guards negative bases and nonfinite `pow` results; the independent oracle follows the zero-contribution convention; the existing normal spot witness has its required `[180,180,180,255]` pin. A `cutoff=180°`, exponent `.5` public witness pins black opaque output; this backend already converted the pre-fix invalid power to black, so it is evidence of the contract but not a causal pixel differentiator. Spot target-location normalization moved to `:math`, with F64 subtraction/scaled normalization and checked F32 narrowing before freezing; no geometry helper remains in the planner. The public math regression has a causal RED under F32 subtraction of finite `±1.8e38`, then GREEN. Sequential compile gates all exit 0; W6d XML 16/0/0/0 and W6a restore XML 9/0/0/0. Native test workers exit 133 after assertions, therefore `UNKNOWN` separately. Task33 untouched.

Task 32: fix round 2/5 pending re-review — added a public, numerically pinned spot edge witness (`exponent=2`, `cutoff=36°`, `(0,0)=[116,116,116,255]`) where the edge ramp is nontrivial. Controlled temporary mutation to legacy `pow(edgeRamp, exponent)` produced causal RED `expected=116 actual=98`; restored with apply_patch and never committed. Ran W6a restore first (9 console PASS; native 133 UNKNOWN), then W6d last and immediately retained its class XML: `tests=17 failures=0 errors=0 skipped=0`; Gradle exit 1 remains solely native exit 133, UNKNOWN. Task33 untouched.

Ruling: the attempted public Surface fixture at finite spot endpoints `±1.8e38` is not retained: F32 subtraction previously failed admission, while the F64 planner route reached the backend but produced black instead of its `[222,222,222,255]` oracle. It conflates the planner overflow with extreme WGSL literal behavior, so the isolated public math regression remains the truthful causal proof. — Task 32 must not extend this into backend extreme-coordinate work; the numeric spot-edge witness is the finite public pixel discriminator for this round. — If a later acceptance task requires full extreme-coordinate rendering, it needs its own shader-coordinate investigation and review.

Task 32: final controller reconciliation after fix rounds 1–2 — complete (commits `06cf9c2de..a5b8a1a32`, scoped Sol re-review clean). The pending re-review lines above are historical checkpoints, not open gates. The finite extreme spot pixel gap is explicitly assigned to Task 33's admission investigation by the ruling above and the amended durable plan; native exit 133 remains UNKNOWN.

Task 33 original: Terra `/root/w6d_task33_admission_terra` returned NEEDS_CONTEXT before commit. The direct perspective draw was rejected by W6a source-lane admission (`w6a.layer.unsupported_child`) before lighting mapping. A public `saveLayer` captured under perspective with child draw after `resetMatrix()` reached the existing precise `w6a.layer.unsupported_lighting_mapping` refusal; sentinel and same-Surface recovery passed once recovery also reset CTM. Focused XML 1/0/0/0, native exit 133 UNKNOWN. The pre-existing ColorFilter wrapper witness was already green, so it is characterization, not a causal production RED.

Ruling: Split oversized Task 33 into 33a (admission/refusal/recovery), 33b (degenerates, compositional demand and finite extreme backend), 33c (Picture memory/wire pixels), each with its own Sol review gate before Task 4. — The original task couples three independent public evidence domains and the perspective blocker was a fixture geometry issue, not a missing hook. Smaller sequential slices preserve one W6 graph and allow exact numerical causes to be reviewed before Picture. — If wrong, 33b or 33c may expose a cross-domain interaction requiring an earlier slice's scoped fix and re-review. Durable plan amended; no Task 4 dispatch.

## Task 33a/33b/33c preflight

| Tasks | Producer → consumer / shared owner | Finding |
| --- | --- | --- |
| 32→33a | Frozen six-family recipes → admission and refusal | Ordered; positive ordinary-range pixels remain stable. |
| 33a→33b | Finite domain and frame-terminal recovery → degenerate and extreme pixel work | Ordered; 33b cannot silently narrow admitted finite parameters. |
| 33b→33c | Deterministic output and demand propagation → Picture replay | Ordered; same W6 FilterPass and target graph. |
| 33c→4 | Picture 15/schema 9 lighting replay → SceneSnapshot filter | Ordered; Task 4 consumes reviewed wire behavior. |
| 33a self | Positive signed/exponent evidence and negative/perspective refusal | Isolated perspective fixture avoids unrelated W6a source-lane failure; all refusals need sentinel/recovery. |
| 33b self | Degenerate, edge, wrapper and extreme pixels | Public oracles before Surface; ColorFilter already passing is a preservation witness, not claimed causal RED. |
| 33c self | Memory/wire two-Z pixels and old-reader behavior | Reuses Task 3 archive; no second Picture authority. |

Ruling: Do not add a duplicate W6a preflight solely to turn a direct perspective *draw* refusal into a lighting-mapping diagnostic. — The isolated public layer fixture reaches the existing mapping check and produces the required precise W6 diagnostic; the direct draw independently violates the source-lane contract. — If wrong, a future valid lighting occurrence may still be rejected too early and would require an admission-order fix with a causal public test.

Task 33a: Terra implementation commit `56895ae02` from base `a2728e833`. Public W6d XML 21/0/0/0 and W6a restore 9/0/0/0; all three scoped compiles exit 0; native worker exits 133 UNKNOWN. Sol review ❌ Important ×2: recovery pixel expected value is constructed after Surface and lacks explicit Render/Readback evidence; signed-scale report incorrectly calls old invalid-WGSL parser exit 134 a semantic RED. Six-family acceptance coverage already exists in Task 32 and remains in the 21-method W6d class. Fix round 1/5 pending.

Ruling: For Task 33a `surfaceScale=-1`, old production emitted `--1.0f` and its WGSL compiler aborted before any pixel assertion. This is an exact, reproducible product failure on valid input, but it is not a causal semantic RED under the plan's test rule; label it pre-assertion failure and keep native 134 UNKNOWN. Require the new public pixel oracle to pass after the minimal renderer correction, and do not fabricate a semantic RED by mutating a different behavior. — The 3D lighting spec requires signed scale and observable output; a parser abort is the defect itself, so a semantic RED on unchanged code is impossible. — If wrong, this exception weakens the TDD evidence for signed scale and should be scrutinized in final review; the committed fix still has independent GREEN pixels and exact native failure provenance.

Task 33a: fix round 1/5 commit `ef4e78646` (2 addressed, 0 open — recovery expectation/evidence and honest WGSL failure provenance). Scoped Sol re-review clean, no new Critical/Important. Task 33a complete (commits `a2728e833..ef4e78646`, review clean). W6d XML 21/0/0/0, W6a restore 9/0/0/0; native exits 133 remain UNKNOWN. Task 33b is next; the green ColorFilter characterization and finite extreme spot probe are carried from the original Task 33 report.

Task 33b: Terra implementation commit `76e1240ed` from base `2b54184b2` adds public degenerate/edge/ColorFilter and finite extreme spot witnesses. The extreme fixture produced a causal pixel RED `expected=222 actual=0`, then GREEN after overflow-safe scaled point/spot shader-vector emission. W6d XML 28/0/0/0, W6a restore 9/0/0/0; native exits 133 UNKNOWN. Sol review ❌ Important — separate F32 rounding in scaled subtraction can make exact coincident light/surface nonzero; prior `.5` witness did not discriminate.

Task 33b: fix round 1/5 commits `e78f29cd9`, `1bdbb53ce` (1 addressed, 0 open) — public `(5.5,4.5,1)` exact-coincidence discriminator, WGSL exact-equality guard, controlled no-guard RED and restored GREEN; the overwritten RED channel payload is not reconstructed in the report. Extreme spot remains green. W6d XML 29/0/0/0, W6a restore 9/0/0/0; native exits 133 UNKNOWN. Scoped Sol re-review clean, no new Critical/Important. Task 33b complete (commits `2b54184b2..1bdbb53ce`, review clean).

Task 33b review ⚠️ on unchanged planner properties resolved by controller read-only checks: `W6bFilterGraphConstruction.hasDistantDiffuseTerminal` traverses Compose/ColorFilter (lines 248–267); `inputDemand` preserves ColorFilter output demand and passes each lighting input through `sobelRequiredInput` (lines 1082–1109); `distantDiffuseBounds` uses consumer demand with a checked one-texel halo (lines 991–1003). The Task 31 ledger audit already traces every FilterTarget resource through W6a budget and graph/lifetime validation. These checks support the public ColorFilter witness but do not claim a global Skia gate.

Task 33c: Terra implementation commit `464874c` from base `efba896b8` added the public Picture memory/wire two-Z pixel witness. The unchanged production path passed it immediately; a controlled, restored writer mutation forcing point3 Z=0 made two Picture methods fail while historical 2D refusal stayed green. Final Picture XML 3/0/0/0, W6d surface 29/0/0/0, W6a restore 9/0/0/0; all three compiles 0, native worker exits 133 UNKNOWN. Sol review pending.

Ruling: Task 33c's requested public Picture pixel test is a preservation witness, not a causal RED against baseline. — Picture 15/schema 9 already transports the 3D Z correctly; a fabricated production defect would violate the plan's evidence rule. The controlled Z-loss mutation demonstrates test sensitivity but is not described as a baseline failure. — If wrong, an untested Picture edge could still need a true RED and implementation fix in Task 4 or final review; the public memory/wire pixel oracle remains binding.

Task 33c: Sol review ✅ spec compliant and task quality approved, no Critical/Important/Minor. Reviewer's unchanged-code checks confirmed writer/reader X/Y/Z, canonical 3D identity, historical 2D-lighting fail-closed and non-lighting readability. Task 33c complete (commits `efba896b8..5ae45ed97`, review clean). Picture XML 3/0/0/0, W6d surface 29/0/0/0, W6a restore 9/0/0/0; native exits 133 remain UNKNOWN.

Ruling: The Task 33c brief listed `W6dLightingSurfacePixelTest.kt` as a possible modify file, but the Picture witness correctly reuses its already-reviewed independent oracle without changing that file. — The acceptance behavior is four public Picture replay pixel comparisons, not a gratuitous surface-test edit; the reviewer found no missing surface behavior. — If wrong, Task 4 may need an additional surface/Picture interaction witness, subject to its own review.

Task 33b: Sol review fix round 1 pending re-review. A new public point-diffuse exact-coincidence witness at `(5.5,4.5,1)` uses a deliberate F32 rounding discriminant (`fl(4.5/5.5) != fl(4.5*fl(1/5.5))`): controlled removal of the WGSL equality guard is a causal RED, while the restored exact-equality zero-contribution guard is GREEN. The finite `±1.8e38` spot pixel stays GREEN. W6d XML is 29/0/0/0 and W6a restore XML is 9/0/0/0; native exits 133 remain UNKNOWN. Scoped commit and re-review remain required before Task 33c.

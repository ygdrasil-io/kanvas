# W7 Root DrawColor Composition Implementation Amendment

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. This is a bounded architectural repair inside unfinished Task2, not a restart of completed Task1.

**Goal:** Restore proper DrawColor root composition and finite-CTM clipfill without geometry/source substitutions.
**Architecture:** A closed last-owner root compositor reuses typed W6 ordered publication for authenticated colour and existing geometry spans. Earlier successful owners remain first. Separate colour-only CTM recognition from geometry admission.
**Tech Stack:** Kotlin / Scene IR / gpu-plan / native WebGPU / JUnit / existing Skia checkpoint.
**Spec:** refactor/waves/W07-gm-convergence/root-drawcolor-composition-design.md.

## Global Constraints

Apply destination-domain-plan Global Constraints verbatim through ownSDD/global-constraints.md, except explicitly expanded root production scope in this spec. Controller alone runtime/Git mutations; offline/no-daemon/outer240, fullaudit/ownPGIDempty/separatepostseal. No private helper edits, GPU skip, new exclusions, score/ref/tolerance/default/cap change, CPU fallback or infra/forwarding/source-text test. Geometry math I/F32/64; archives/protected preserved. User's delegated local design authority and chosen SDD execution mean no repeated human continuation gate.

## Review Focus

- Whole geometry/work budgets must not reset per span; exact existing B/B−1 and frame ledger remain authoritative.
- DrawColor uses captured clip without CTM remapping or double alpha; geometry siblings retain their own restrictive authorities.
- Native stencil/clip producer and destination-copy dependencies survive ordered composition, not arbitrary graph casts.
- Successful earlier owner identities and all unrelated stable refusals remain unchanged; encoded layers stay qualified/closed.
- Every current22 lost render needs a measured disposition before Task2 migration; target exact100 alone does not establish convergence.

## File/Interface map

- Create `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W7RootOrderedCompositionPlanCompiler.kt`: public constructor(runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot), implements GpuPlanCompiler; no-argument constructor delegates to RuntimeEffectSemanticCatalogSnapshot.Unbound. select(scene: SceneSnapshot,target: RenderTargetDescriptor): GpuPlanSelection; plan(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,budget: PlanBudget): RenderPlanResult<RenderGraph>.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`: append new compiler after current ordinary chain, preserving earlier order. No recursive inclusion of root compiler in a geometry-span chain.
- Modify `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`: finite LINEAR colour-only CTM/provenance contract, no geometry-transform or encoded admission relaxation.
- Reuse/factor `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt` ordered Segment/Candidate publication and `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt` typed scope-free graph envelope; `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/FrameSourceLayoutV4.kt` and `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphConstruction.kt` only if genuinely required to retain actual span/LegacyColor authority. Preserve optional-table strict adapter/permit and all native validation.
- Create `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RootDrawColorCompositionSurfacePixelTest.kt`; no protected edits.
- Pending original Task2 migration document after fullcensus/review; existing observer/GM111/adapter fixed source frozen.
- Consumes existing W3 LegacyColor, W4a/b/c/d/e geometry sources and material/clip/budget proofs; produces one authenticated root RenderGraph, not a new Surface API or renderer path.
- If a further native/lowering unit must change, stop worker and report concrete dependency to controller, do not widen validators silently.

## Task2 phase9: fixture-only native RED

- [ ] Write ONLY the new fixture plus own task-2-report append; product is160becf unchanged. Name the production break each method catches.
- [ ] Methods drawColorUsesCapturedClipDespiteFiniteCtm, partialAlphaDrawColorUsesSingleSrcOver, rootRingDrawColorKeepsRecordedOrder, integerFilteredRectComposesWithDrawColor, hardHairlineClipComposesWithDrawColor, hardDarkenPathsComposeWithDrawColor, invalidStrokeSiblingRefusesAndRecovers.
- [ ] Oracles before Surface, 32×32/full4096. CTM: bluebase, hardclip[4,6,24,22] before translate(7,3)/scale(2,2), red DrawColor expected insideclip; separate90°LINEARrotation and no-clip finiteCTM fullgreen. Encoded control is separately runnable: integer translation then inverse translation before DrawColor, with captured identity CTM and hard device clip. Existing encoded SetTransform translation admission does not admit DrawColor nonidentity; no encoded scale or admission widening. Partialalpha green128overblue expects[0,188,187,255] LINEAR and[0,128,127,255] encoded.
- [ ] Ring: constantBLUE gradient full32, opaqueGREEN DrawColor, RED ring[2.5,2.5,5.5,5.5] width1; ringpixels x/y2..5 excludinginner3..4. Colourbeforegradient→blue/red; between→green/red; after→allgreen. Partialgreen128-between→[0,188,187,255] outside/redring.
- [ ] MaterialRect: BLUE DrawColor, RED integerRect[8,8,24,24] with identity20coeffMatrix ColorFilter; redinsideblueoutside. Hairline: BLUE DrawColor, RED hardstrokeRect[8.5,8.5,23.5,23.5] width0, hard Path intersectfull32 then difference[16,0,32,32]; expected redringcell8..23 excludinginner9..22 and x<16, blueelsewhere.
- [ ] DARKEN: WHITE DrawColor, RED hardPathrect[4,4,20,20], GREEN hardPathrect[12,12,28,28], DARKEN paint; whiteoutside/redfirstonly/greensecondonly/blackoverlap. Reversing opaqueDarken commutes, so chronology pinned by ring fixture instead.
- [ ] Negative: constantBLUEgradient + GREEN DrawColor + AA Rect stroke Shader.SolidColor RED width1; existing unsupported.stroke.rect_anti_alias, sentinel0x5a full4096 unchanged, discard operations and recovervalidopaqueBLUEfullbuffer. Preserve finite/nonfinite/caps failures; no fabricated no-submit telemetry.
- [ ] Retain actual fullbuffers/native observation under existing property; test-only helpers, no product cleanup/debug API.
- [ ] Controller commits ONLY fixture before native RED, freezes allwriters, runs frozenwrapper240 ./gradlew --offline --no-daemon --console=plain -I existinginit -Pw7.validationDir=ARCHIVE :kanvas:test --tests org.graphiks.kanvas.surface.W7RootDrawColorCompositionSurfacePixelTest; fullXML/events/log/exit/PGIDaudit/separatepostseal. Require semantic missing-contract failure, not compilation/oracle error. Cases already passing retained as controls; do not fake all7RED.

## Task2 phase10: product repair then native GREEN

- [ ] Original owner resumed after valid RED. Implement CTM correction separately from root selection/publication; no permanent adapter revert.
- [ ] New compiler root predicate LINEAR/no layer-picture-image-spatialfilter/atleastDrawColor+Draw. Authenticate each colour using W3, select maximal geometry spans with established authorities inclnarrowrootAAstroke when its existing closed family witnesses hold. Prevalidate fulloriginalScene/bounds/caps; actualoriginalcommandindices and ordering survive typed remaps.
- [ ] Reuse typed W6 ordered scope-free envelope with real source packing/inventory. Preserve whole-frame geometry/work/budget refusal and all clip/stencil/destination dependencies, one final submit/readback; no nullable/table/authority/schema weakening. Handle unsupportedspan before ownership without changing existing legacy continuation; terminal/refusedowned neverfallsback.
- [ ] Worker selfreview/report thenfreeze; controller named localsourcecommit, native newfixtureGREEN with same fullaudit/separateseals. Mismatch returns to originalowner with actualfirstboundary/oraclefixed, no speculative guards.
- [ ] Controller unchangedcontrols (classes above), three existingcheckpoint slices104/121/524 and111, actualsource40/frozenall/oneinvocationatime. Account opcode/nativecounterchanges honestly, historicaloldadapterRGBAequality notrequiredforsemanticallydifferentinputs; independentoraclesdecideexpectedchanges.
- [ ] Controller full631 census fiveexisting slices, denominator443/config unchanged. All22losses/gain66 auditedperrow, geometryaggregatebounded/capskept. Existing unscoped module failures included byidentity inreport; no globalGREENclaim.
- [ ] Originalowner migration/evidence doc onlyafterrootqualifiedcensus, no selfreferencing futureSHA. Controller freshSolTask2 spec+quality review completeTask2BASE0cae→actualHEAD diff, findingsfixedbyoriginalowner; onebroadfinalAstra reservedafterTask2complete.

## Self-review of amendment

Spec coverage: DrawColor correctness, orderedrootspans/legacy+material, clip/stencil/native permit/budget, unchangedencoded/default/refusals, all22census gate and review map to named phases. CTM/routing isolated in source and report. No oracle uses product helpers. Userposedgeometrynomenclature preserved. Infrastructure/config changes excluded. Mainambiguity (W6typedspanextension feasibility) explicitly escalates concrete dependencies before edits, not guessed validators.

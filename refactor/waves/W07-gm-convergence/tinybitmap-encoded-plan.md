# W7 TinyBitmap encoded parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the actual TinyBitmap port with the pinned bare8888 encoded composition and non-AA paint, preserving its LINEAR control and measuring real pixels.
**Architecture:** Existing image encoded capability; two-file GM/fixture correction only. Controller owns native artifacts, docs, Git and publication.
**Tech Stack:** Kotlin/JUnit5/WebGPU on Mac.
**Spec:** refactor/waves/W07-gm-convergence/tinybitmap-encoded-design.md

## Global Constraints

- Fonts/external codecs/jpg-color-cube excluded; geometry stays in math with I/F32/64 nomenclature.
- No infrastructure/source-text/mock/fake/injected-callback tests, skips, CPU fallback, or weaker oracles/validators/thresholds/caps/budgets.
- Only TinyBitmapGm.kt and W7TinyBitmapSourceSurfacePixelTest.kt may change in worker phases; public API/runtime/planner/math and every other GM/reference stay byte-identical.
- Main-only serial native/Git, outer240, full process/exit/events/stacks/XMLstdoutstderr/JSONL/inventory audit then separate post-seal. No source/docs/Git writes while native active.
- Main alone may regenerate tinybitmap.png and its score value via existing selected tasks; 813 other PNG and 558 other score values stay identical. Preserve inverse96cd/private archives/workspaces.
- No global GREEN, merge or W7-complete; historical PNG producer remains unknown, alpha0.5 public quantization and drawPaint proxy remain explicit limits.

## Review Focus

- Wrong domain: registered and same-Surface encoded full40000bytes must reject194 instead of165 in G/B; independent LINEAR control remains strict.
- Wrong source/background/alpha/tile modes: original bytes and scene stay fixed; full-buffer pixel witnesses and both-domain independent control catch changed source math.
- Lost replay/completion: same-Surface encoded replay requires Render/Readback, QueueSubmitted before CompletionSucceeded, per-frame coordinator/encoder/buffer/submit/readback=1.
- Scope drift: every other GM/reference/source69 sealed; no global/default-domain or threshold change.
- Misleading historic score/provenance: baseline/after actual metrics, physical reference hash, target-only artifacts and named alpha/proxy gaps; no score-only parity claim.

### Task 1: Align TinyBitmap port and preserve independent public controls

**Files:** Worker modifies only `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7TinyBitmapSourceSurfacePixelTest.kt` in RED; only `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/TinyBitmapGm.kt` in GREEN. Main-owned design/plan/qualification/pilotage/artifacts. Worker own task-1-report.md.
**Interfaces:** existing SkiaGmRenderer.render, TinyBitmapGm draw, Surface canvas/render/snapshotOps, existing literals/helpers and RenderResult native evidence. No new API/test hook.

- [x] Step1 Main fresh baseline case592 via bounded measureSkiaParity, before worker writes; audit all log/exit/JSONL/inventory then separate69/814/559 postseal.
- [x] Step2 Worker RED: rename registered method to `registeredTinyBitmapUsesPinnedEncodedPixelAndFreshSurfaceRepeatability`, retain all fullbuffer/dimensions/operations/count/diagnostic/repeat assertions but expect ENCODED_PIXEL [230,165,165,255]. Existing `actualTinyBitmapReplaysOnOneLinearSurfaceOverDeclaredGray` uses explicit RenderConfig.DEFAULT.copy(compositionDomain=LINEAR), preserving all original LINEAR_PIXEL [230,194,194,255] assertions. Third `correctedPremultipliedSourceMatchesIndependentCompositionDomains` unchanged.
- [x] Step3 Worker RED: add `actualTinyBitmapReplaysOnOneEncodedSurfaceWithCompletedNativeEvidence`; same100x100 background/gm recording, config gm.compositionConfig(); render twice. Check full40000bytes equal ENCODED_PIXEL, clean100x100 RGBA8/count2/0refused, stable recording/count and full replay; log digest/uniqueRGBA/stats/scopes/counters/steps before assertions. Require Render/Readback, QueueSubmitted before CompletionSucceeded, five per-frame counters frameCoordinatorCreations/encoders/commandBuffers/submits/readbackCopies=1 and draw/pipeline coherence. AfterAll only endclass. Mutation rationale before each changed/new method. Stop TESTS_READY; no build/native/Git/spawn.
- [x] Step4 Main actualdiff/report read and preseal69, artifacts immutable; bounded four-method RED `:integration-tests:skia:test --tests org.graphiks.kanvas.skia.W7TinyBitmapSourceSurfacePixelTest`. Expect2strictencoded failures/2controlsPASS, diagnose any other outcome without fitting. Full audit then separatepostseal.
- [x] Step5 Same worker GREEN after main authorization: GM imports CompositionDomain, overrides compositionDomain=SRGB_ENCODED, Paint antiAlias=false; concise pinned-harness comment not PNG-provenance assertion. All other source arguments/metadata unchanged. No fixture change. Stop IMPLEMENTATION_READY with final SHAs/scope/self-review report.
- [x] Step6 Main preseal; bounded selected17 `:integration-tests:skia:test` with six classes TinyBitmapSource/EncodedImageShader/BitmapRectSource/ChildSamplingPort/ChildSamplingCausal/ReadbackBudgetWarmup. Full audit/postseal, retain warnings/failures.
- [x] Step7 Main commit source checkpoint; fresh only[592,593) measureSkiaParity with actual rendererCommit, images=true. Audit all records/images/process/exit and separatepostseal; compare baseline actual metrics, same reference/provenance, no corpus-wide delta.
- [x] Step8 Main via regenerate-renders: bounded generateSkiaRendersFor -Pgm.name=tinybitmap, then bounded selected SkiaGmRunner -Dkanvas.gm.name=tinybitmap. Only target PNG/score may change in these authorized runs; full audit then separatepostseal each,813otherPNG/558other score values/reference exact. Verify target decoded RGBA equals independently qualified encoded fullbuffer.
- [ ] Step9 Main qualification/pilotage, Sol task review spec+quality package BASE075405..HEAD, fixes through original worker/re-review if needed. Final current-lot Sol review, normal ownbranch draft PR stacked on codex/w7-promoted-image-contracts/#2445; attach/verify remotehead/base/body, no merge. Preserve workspace/evidence.

Self-review: one deliverable/one coupled test-product interface;2strict RED +2unchanged domain controls, no duplicate task boundaries; all five Review Focus covered by steps2/3/7/8. User-delegated bounded choice, no fabricated written approval.

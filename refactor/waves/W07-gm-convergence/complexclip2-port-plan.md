# W7 faithful ComplexClip2 port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task. Controller owns runtime/Git/evidence; steps use checkboxes.

**Goal:** Make the existing shared six-variant ComplexClip2 port follow the verified Skia operation stream and paint defaults, then measure actual parity without changing the oracle.
**Architecture:** Reuse the existing test-local SkiaRandom for the bounded shared-port repair. Native attempts exposed two W4e boundaries: missing no-clip binary-mask provenance and ignored path-mask consumption. Task1 retains the compiler-owned mask/fetch/broadcast facts, then consumes them through a matching host/native path-mask-only recipe. No adapter/API change, payload-size change, or new geometry helper.
**Tech Stack:** Kotlin/JVM, real SkiaGm/GmCanvas/Surface, Metal/wgpu, Gradle/JUnit.
**Spec:** Bounded in-chat design under the user's W7 carte blanche, expanded only by the concrete source facts and literal visible witnesses below. No separate architectural spec is needed for this existing shared-port flow and measured renderer extension.

## Global Constraints

Fonts, codecs externes et jpg-color-cube hors périmètre. Aucun test d'infrastructure, mock, source-text, forwarding, skip GPU, CPU fallback, fake Picture ou GM-name routing. Ne pas changer epsilon, CompositionEnvelope, oracle tolerance, budgets, caps, registry631/eligible443, scopes/domains, références ou exclusions. Géométrie/numeric dans math et nomenclature I/F32/64. Tous les suivis durables dans refactor. Controller seul pour runtime serial borné240s, custody/evidence/docs/Git/publication ; workers produit/tests seulement et aucun helper/subagent/commit/runtime. Pas de merge ni suppression de custody.

## Binding primary facts / design

Parent draft#2432 is finalized at d958bd26cb11d5ace35cfd1794889d7694c6223e. Branch codex/w7-complexclip2-port, same isolated W7 checkout.
[complexclip2.cpp](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/gm/complexclip2.cpp) uses seed0 SkRandom.nextU()%2; row i/column j/shape k loops and ops[j*5+i][k]. Reuse [existing SkiaRandom](../../../../integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRandom.kt), whose stream matches pinned [SkRandom.h](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/src/base/SkRandom.h). Pinned [SkPaint.cpp](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/src/core/SkPaint.cpp) defaults both paints non-AA; negative strokeWidth(-1) ignored, leaving hairline0.
Keep opChoices=[DIFFERENCE,INTERSECT], modulo unsigned2, loop/index order, five shapes/colors, geometry/pads/370x370 dimensions, finite50x50 fill, hairline0, save/restore, all six names/providers/renderCost/minSimilarity and clip antiAlias parameter. Rect clip remains the existing equivalent clipPath route because GmCanvas.clipRect lacks op/AA; no new adapter interface. The GM port changes only RNG and explicit paint AA; primary provenance is pinned here. Pinned diagnostic source establishes the mismatch, not PNG provenance; no promised score.

## Review Focus

- Transposed cell indexing or changed random consumption order: native literal grid witnesses across all25cells.
- Modulo differs from nextInt: expected visible pixels never execute the port/helper RNG.
- Paint AA confused with clip AA: same native grid and fully opaque hairline controls on both real rect variants; keep variant parameter.
- Finite fill spill past local50: blue right outline at local(50,5), purple local(51,5), outside finite fill.
- Shared six-variant source: only common RNG/paints change; covering existing clip public suites plus full631/443 corpus includes all six, whether rendered/refused.
- Hard path coverage in mixed-AA clip frames: the unclipped binary cover must still consume its source mask; Task1 adds Rect/Path stroke and opaque/translucent triangle native witnesses, with outside-coverage, chronology and repeated-frame checks.

### Task 1: Repair shared port with retained visible native regression

**Files:** Modify integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/clip/ComplexClip2Gm.kt. Create integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/ComplexClip2GmSurfacePixelTest.kt. Read existing SkiaRandom.kt but do not change it. Preserve all old tests and untracked W7InverseFilterDiagnosticSurfaceTest.kt.
**Interfaces:** SkiaRandom(seed: UInt=0u).nextU(): UInt; actual ComplexClip2RectGm and ComplexClip2RectAaGm through their compositionConfig(), onOnceBeforeDraw and draw on real GmCanvas/Surface. No new public interface.

- [x] Tests-only: add two block-bodied Unit @Test witnesses, one for each actual rect GM rendered on its full370x370 Surface. Assert all25cells x9 literal interior samples and two outline samples percell, isClean/empty diagnostics/zero refusals/positive dispatch/Render+Readback, then second render of the same Surface byte-identical. Use existing JUnit assertions, @AfterAll GPUBackendRuntimeFactory.dispose(), no kotlin.test dependency change.
- [x] Exact independent oracle: cell origin(20+70*col,20+70*row); ordered local samples (5,5),(15,15),(25,5),(5,25),(25,25),(45,5),(5,45),(45,45),(45,25). P=(221,160,221,255), G=(160,221,160,255). Literal expected patterns row-major:
  row0: PPPPPPPGP / PPPPPPPPP / PPPPPPPPP / PPPPPPPPP / PPPPPPPPP
  row1: PPPPPPPPG / PPPPPPPPP / PPPPPGPPP / PPPPPPPPP / PPPPPPPPP
  row2: GPPPPPPPP / PPPPPGPPP / PPPPGPPPP / GPPPPPPPP / PPPPPPPPP
  row3: PPPPPPPPP / PPPPPPPPP / PGPPPPPPP / PPPPPPGPP / PGPPPPPPP
  row4: PPPPPPGPP / PPPPPPPPP / PPPPPPPPP / GPPPPPPPP / PPPPPPPPP
  In every cell assert local(50,5)=Blue(0,0,255,255) and local(51,5)=P. Interior literals come from primary geometry plus separately derived125bit stream; do not call any RNG/expected Scene/reference-image rendering to create expectations at runtime. No AA-edge numeric approximation/tolerance.
- [x] Freeze tests/report READY_FOR_RED; controller snapshots/runs bounded :integration-tests:skia:test --tests '*ComplexClip2GmSurfacePixelTest'. Actual executed pixel/native failures required, not compile/discovery failure. Wait for source go.
- [x] After controller actualRED authorization: replace KotlinRandom import/instance with existing SkiaRandom, idx=(r.nextU()%opChoices.size.toUInt()).toInt(); set both rectPaint/fillPaint antiAlias=false, preserve all other behavior. Freeze/report READY_FOR_GREEN. Actual first attempt remains RED2/2 at25,25; no GREEN claim.
- [x] Controller reruns same tests unchanged; diagnose any remaining literal failure before widening scope, never rewrite expectations to observed pixels. Obtain independent Sol spec+quality gate on exacttask package; same worker fixloop if needed.
- [x] Controller commits the reviewed/qualified Task1 source/test paths only (including the measured extension below) before freshcorpus. Report DONE_STAGE_FOR_REVIEW with exact changedpaths/read references/source diff/selfreview; controller runtime receipts appended separately. No worker commit/build/helper.

#### Task1 measured extension: preserve unclipped hard-binary path coverage

Second measured boundary: source correction1 tree550f5fd4 executes4/4 native refusals because the no-clip prepared path mask id is null. GPUPlanW4ePreparedAuthority.kt binarySourceMaskId/binaryMaskFetch/binaryBroadcastSamples currently recognize only ClippedBinaryMaskedPathDraw. Correction2 may additionally modify this file: import BinaryMaskedPathDraw and retain draw.mask.value /draw.maskFetch /draw.broadcastSampleCountI32 in those three typed projections. No mask reconstruction, guard bypass, extra producer/resource/budget, or forwarding test. Existing W4eClipGraphLowerer texture-read declaration follows the now-retained id unchanged. The earlier assumption of non-null preserved provenance is invalidated; both projection and consumer are required. Four public tests stay frozen and are rerun unchanged.

The original tests-only tree806a497f executed two causal pixel REDs. The faithful port candidate b968f9a3 then executed two different pixel failures at25,25 with clean native controls. One private diagnostic showed the outlines filled, similarity±2 58.44558071585099 versus parent79.6990504017531; it is not qualified corpus evidence. Source diagnosis independently verified with Astra: W4d creates BinaryMaskedPathDraw/HardEdgeBinaryColorCover, but GPUW4eMaterialGeometryRecipeV1 and its native materializer select UnmaskedCover before hardBinaryCover when the clip consumer is absent. The already-owned path mask is discarded. This is coverage loss, not loss of captured STROKE paint.

**Additional files:** Create kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7HardStrokeClipAssemblySurfacePixelTest.kt. Modify gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW4eMaterialGeometryRecipeV1.kt and GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt after the new actual native RED; correction2 additionally permits the exact GPUPlanW4ePreparedAuthority projection named above. Read GPUW5aSourceStageNativeV2.kt, GPUW4eNativeOperandKeysV6.kt and gpu-plan/.../W4eNativePayloadPlan.kt for contract coherence; do not modify them unless a separately measured issue requires it. Both frozen GM tests and all other existing source/tests remain unchanged.
**Interfaces:** Add internal recipe enum BinaryPathConsumer, selected exactly for HardEdgeBinaryColorCover with no clip-mask consumer. It consumes the sealed binarySourceMaskResourceId through textureLoad at device pixel coordinates, with one path-mask texture binding0 and the existing ColorBlock color4 uniform binding1/minBindingSize16. Fragment signature fn fs_main(@builtin(position) position: vec4f), coverage=clamp(pathMask texel.r,0,1), exact return consumer.color * coverage; enables the existing authenticated material substitution. Host and native both use NativeMask and MaterialCoordinateSlotV1.Position; preserve finalBlend behavior consistently with the existing common source, stencil/sample/resolve/scissor/resource authority, and existing path-times-clip BinaryConsumer. No dummy clip, NativeFull classification, double coverage, budget or producer changes. Add a matching createW4eBinaryPathConsumerPipeline with existing ownership/cache pattern; ensure the native selection/binding precedes the generic maskConsumer==null color-only case.

- [x] Tests-only, four public block-bodied Unit tests, LINEAR Surface64x40, no GM/RNG/graph inspection. First two use AA=true purple full background RGBA221,160,221,255, then blue AA=false width0 closed hairline bounds8.5,8.5,24.5,24.5: drawRect in one, explicit closed Path in the other. Both add a disjoint clip sibling: save; clipPath rect40,8,56,24 INTERSECT AA=false; clipPath rect48,8,56,24 DIFFERENCE AA=false; drawRect40,8,56,24 green160,221,160,255 AA=false; restore.
- [x] Stroke literals, interior16,16=P first; edges8,16 /24,16 /16,8 /16,24=Blue; outside7,16 /25,16 /16,7 /16,25=P; keep44,16=Green; removed52,16=P. Half-integer geometry avoids the separate integral edge-tie question; no renderer-derived expected geometry.
- [x] Two independent filled-Path tests use AA=true opaque black full background; AA=false closed triangle8,8→24,8→8,24→close; later opaque blue AA=false Rect9,9,13,13; same disjoint clipped green sibling. One triangle is opaque red, one is white alpha128. Literals20,20=Black first (inside bounds but outside path),10,16=Red respectively188,188,188,255 in LINEAR;10,10=Blue;44,16=Green;52,16=Black. The188 alpha-once value follows sRGB encoding of128/255; do not adjust observed color or tolerance.
- [x] Every test asserts isClean, empty diagnostics, zero refusals, positive dispatch, Render+Readback, then same retained Surface second render byte-identical. Controller runs :kanvas:test --tests '*W7HardStrokeClipAssemblySurfacePixelTest' under existing240s owned protocol; inspect actual original-terminal/exit/XML and causal pixel RED before source go.
- [x] Same worker implements the bounded consumer and measured authority projection, self-reviews host/native/layout/material/finalBlend consistency and freezes READY_FOR_GREEN. Controller reruns the four unchanged witnesses plus both frozen GM tests serially; no expectations rewritten. Any remaining integral-edge mismatch is a new measured decision, not pre-authorized math/provenance widening.
- [x] Independent Sol task spec+quality gate includes the complete port/renderer/public-test diff. Source commit owns exactly those qualified paths before fresh corpus; never include the inherited untracked six-probe.

### Task 2: Qualify parity delta and publish stacked draft

**Files:** This plan, refactor/waves/W07-gm-convergence/status.md/pilotage.md, exact fresh corpus JSON, only changed/new generated PNG/scores from existing protocol.
**Interfaces:** Frozen reviewed Task1 source/test; existing631/443 corpus18invariants, sourceSHA, serial slices0-607/607-608/608-631/render30s; parent snapshot inverse-hairline-2e5419afd.json.

- [x] Controller fresh covering existing ClipGmPortSurfacePixelTest, InverseClipGmSurfacePixelTest and newclass, plus SkiaRandomTest only as existing behavioral coverage; no new infrastructure test. Report real counts/exit/XML, originalterminal before next runtime.
- [x] Fresh focused covering includes the new four native witnesses, both frozen GM tests, and existing clip/inverse/hairline/AA-material native suites named in the controller contract. Because the measured extension changes renderer code, run a fresh bounded full renderer/global suite in addition to covering; report actual terminal counts, failures, interruptions and completeness, compare parent identities/reasons without silently accepting new failures. Retain exact parent red/incomplete provenance, never relabel it current.
- [x] Fresh source-qualified corpus, strict union invariants/oldhashes/metrics/outcomes including still-refused four other variants and any new renderer failure. Inspect all changed/new triples and report any regression before artifact publication; no promised gain from the port alone.
- [x] Inspect each actually changed/new triple. Regenerate only its artifacts with existing exactname selection and existing includeBlocking flags, native runner and byte equality to freshcorpus. No reference/scope/threshold/budget changes.
- [ ] Independent whole-lot Astra review once, then at mostoneworkerfixwave/scopedSol ifrequired. Track inherited I1 as actually fixed/unfixed, environmentMinor and retained architectural gaps. Publish/attach/verify draft stacked on codex/w7-inverse-filter-convergence/#2432, not merge or W7 complete. Continue next measured architectural decision.

## Self-review

Task1 owns the common port/test, the measured three-file renderer extension, and their actual causalRED/GREEN cycles; Task2 consumes frozen source, never changes the oracle. Literal tables exercise stream/order, hairline/finite fill and real pixels independently; four non-GM witnesses isolate hard path-mask consumption, stroke versus fill, order and alpha once. Fullcorpus contains every sharedvariant without assuming allrender. Existing adapter unchanged; integral hairline ties and original-root filter/inverse capability remain measured follow-ons. The16-byte no-clip uniform stays unchanged; host/native NativeMask, shader signature/return and Position match the existing source substitution contract. Fresh global coverage is required after renderer extension, not borrowed from the parent. One writer/controller runtime/review gates remain coherent.

## Qualification and controller rulings

Source/tests092a293be, [fresh snapshot](complexclip2-092a293be.json), [measured results and limits](status.md). Six retained native witnesses and ten integration tests pass; covering453/456 and global687/725 remain known-red/incomplete. Only two images gain parity,100%/99.075% at unchanged±2; four shared variants still refuse. Two PNGs/scores regenerated through exact-name native selections, byte-equal to corpus. Whole-lot review/publication pending.

- Ruling: compact existing-flow design under W7 carte blanche, not another approval loop — bounded reversible work; cost if wrong: local rework, no oracle change.
- Ruling: primary-proven ComplexClip2 port before W6b root-AA extension — two measured low-quality admissions; cost if wrong: no corpus gain, reported honestly.
- Ruling: keep Rect clipPath and finite50x50/hairline0 — adapter clipRect lacks op/AA; cost if wrong: residual fidelity follow-up, not speculative API work.
- Ruling: Luna/high precise implementation contract, Sol/high task review, Astra targeted/final — Terra unavailable and Sol review-only user constraint; cost if wrong: capability escalation and rework, original-worker corrections1–3.
- Ruling: preserve previous/current private custody despite skill cleanup — incomplete W7 and safety constraint; cost if wrong: private storage only.
- Ruling: measured mask-only recipe extension with existing color4/16/NativeMask/Position — actual public causal RED and source contract; cost if wrong: reversible repair and honest failed pixels, no oracle relaxation.
- Ruling: correction2 preserves BinaryMaskedPathDraw mask/fetch/broadcast in a third renderer file — actual missing-mask native refusal; cost if wrong: still-red native evidence/rework, never guard bypass or reconstructed authority.

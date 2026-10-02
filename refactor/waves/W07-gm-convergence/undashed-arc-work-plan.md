# W7 undashed arc work Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Controller owns serial runtime, evidence, docs, snapshots and Git. One writer; tests first, source go only after actual RED.

**Goal:** Remove real dash arc-length work from certified undashed SVG arcs without changing their geometry, caps or numeric rejection behavior.
**Architecture:** In the existing math centerline preparer, branch before measuring undashed contours. Retain certified SVG arc primitives with exact [0,1] spans; all other primitives use the unchanged measurement. Preserve source order, closures, validation-before-span-emission and transactional work accounting.
**Tech Stack:** Kotlin multiplatform math geometry, Kotlin tests, existing public native Surface/Skia GM runners.
**Spec:** Bounded in-chat W7 design under carte blanche, targeted Astra strategy `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/complexclip2-undashed-work-strategy.md` (full main-read), existing actual four-GM FrameWorkLimit REDs. No broader Bézier or ArcCenter refactor.

## Global Constraints

Fonts, external codecs and jpg-color-cube excluded. No infrastructure/mock/source-text/forwarding tests, GPU skip, CPU fallback, fake Picture or GM routing. No production cap/budget/epsilon/CompositionEnvelope/reference/oracle/threshold/scope/domain/registry631/eligible443 changes. Geometry/numerics in math with I/F32/64. Durable documents in refactor. Controller owns every runtime, snapshot, audit, Git and publication; worker only named math source/tests/report, no helper/subagent/runtime/commit. Preserve all custody and inherited sixprobe; no merge or W7 completion.

## Review Focus

1. Positive loops versus zero primitives: retain retracing Bézier/arc geometry; only the old measurement filters non-certified cases.
2. Finite inputs with non-finite intermediate evaluation: keep historical InvalidScene via fallback.
3. Transactional limits: account actual certification and all remaining source/span work; tiny path/frame/snapshot budgets still refuse before publication.
4. Undashed ordered closure versus dashed cuts: unchanged source closure/reset and whole source count for the measurement error budget.
5. Certified arc range versus extreme/subnormal/depth cases: uncertified cases fall back, never acquire a new rejection reason.

### Task 1: Certified undashed SVG arcs without unused arc-length recursion

**Files:** Modify `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeDashPreparationF64.kt`; tests in existing `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeDashPreparationF64Test.kt`. No other product/test file.
**Interfaces:** existing preparePathStrokeCenterlinesF64(inputF64, dashF64, policyF64, frameWorkUsageBeforeI64), PathStrokeCenterlinePreparationResult, PathStrokePrimitiveSpanF64, PathStrokeWorkUsageI64 and internal stableHypotF64. No public API/type added.

- [x] Worker TEST_ONLY: add a regression `undashed rounded rectangle retains analytic spans under bounded work`: literal Move(7,0), Line(43,0), Arc radius(7,7) to(50,7), Line(50,43), Arc to(43,50), Line(7,50), Arc to(0,43), Line(0,7), Arc to(7,0), Close; WINDING, sweep=true, rotation0. Policy only in test limits attempted path/frame256; production unchanged. Expect Ready, one closed contour, eight ordered spans exactly[0,1], literal line endpoints and quarter-arc midpoints (47.94974746830583,2.0502525316941673), (47.94974746830583,47.94974746830583), (2.0502525316941673,47.94974746830583), (2.0502525316941673,2.0502525316941673), tolerance1e-9 already conventional, finite nonempty output, work>0 and≤256, published frame=path from zero.
- [x] Add real math characterization tests for unchanged fallback: all-zero primitives/identical endpoints/zero-radius arcs yield Empty or preserved linear span as hand-derived; nonzero closed Quad/Cubic loops retain full spans and literal midpoint; finite constant Quad(1e308,0) remains InvalidScene.NonFiniteInput; explicit non-finite input remains InvalidScene; rotated elliptical and reversed/large SVG arcs retain analytical points; repeated Close/MoveTo/continuation preserves literal contour/order; tiny path/frame/snapshot limits still ResourceLimitExceeded and cumulative repeated preparation refuses. No mocks, source-string tests or tautological expected helper calculations. Existing dashed tests untouched.
- [x] STOP after tests/report; controller owns actual bounded `:math:geometry:jvmTest --tests '*PathStrokeDashPreparationF64Test'` RED. Compile/discovery errors not causal RED. Controller freezes oracle blob before source go.
- [x] After explicit SOURCE_GO, implement only this source. Keep source construction/arcCenter/closure unchanged. Compute measurementErrorPerPrimitiveF64 from original primitive count exactly as before. Undashed: first map source primitives in order to retained references; certified non-null SVG arcs skip measurePrimitiveF64; every other case uses it unchanged and is retained iff non-null. Finish validation pass before emitting spans. Retain each full [0,1] span with existing unit+32-byte debit; no empty contour. Dashed branch unchanged.
- [x] Certification helper private math name ending F64, one existing topology work debit before an attempted non-null SVG certificate. D=existing maxSubdivisionDepthI32, require D≤52. Finite center/radii/rotation/startAngle/sweep; radii>0. Require abs(startAngle)+abs(sweep) finite. Bx=(abs(cx)+rx)+ry and By=(abs(cy)+rx)+ry finite. H=2*stableHypotF64(2*Bx,2*By) finite. S=abs(sweep)*max(rx,ry) finite and ((S*2^(-D))*0.5)>0. U=max(H,S); U*2^(D+1) finite. These exact binary powers derive from existing depth, not epsilon/new quality cap. Any failed check returns false to old measurement, never InvalidScene/new reason. This is sufficient finite-positive-measurement certification, not sampling/endpoints-only. Keep debit for failed attempted certificate and all old fallback work; no recursion debit removed unless recursion removed. No snapshot byte reduction claimed for temporary measurement leaves.
- [x] Controller freezes source/test tree, runs full touched test class then full math geometry+matrix JVM and JS compile, unchanged six native controls and four exact-name real GM runs serially. Separate terminal/audit before each next runtime. No gain promised; if refusal persists, measure its next reason rather than guess another patch.
- [x] Independent task-scoped Sol spec+quality review of exact two-file package with actual RED/GREEN receipts. Fix through same writer rounds1–3, no controller product/test edits. 
- [x] Controller fresh full corpus631/443 plus affected/native/global bounded gates and exact invariant/hash comparison. Actual global remains RED/incomplete, not relabelled green.
- [x] Regenerate only the five newly admitted PNG/scores through existing tools; all PNG byte-identical to fresh corpus, five score keys only. Visual gaps explicitly preserved.
- [ ] Final broad branch review, dispositions/corrections, then stacked draft on#2433. No merge or W7 completion.

## Self-review

One source task and existing public numeric interfaces. Astra certificate copied exactly, exception semantics and true work removal distinguished; no guessed four-GM success. Five focus classes mapped to actual mathematical behavior, six native witnesses and unchanged four public REDs. Verification/Git/docs are controller steps, not source authority. User carte blanche replaces new approval loop, not RED or review gates.

## Measured qualification

Source/tests c0567497f217337012f39284b4701edc74093760, two math files identical
to private reviewed eec0 and executed treecdc9. Frozen test475eb593 unchanged.
RED21/2 failures then GREEN21/0, geometry488+matrix310 JVM PASS, JS geometry/matrix
actually compiled, unchanged six native controls PASS. Sol spec+quality C0/I0/M1
inherited warnings. Diagnostics source6aef unchanged from its executed/reviewed
two-message candidate; four initial real FrameWorkLimit REDs retained as evidence.

Expanded native484/3failures/0errors/0skips,481PASS in95s : parent456 statuses
identical,28 additional selected existing tests PASS. Integration11/1failure,
old10PASS in15s. Additional old stroke-refusal test first expectation fails;
parent actual strokedline_caps already recorded unsupported.stroke.cap/13ops.
No test/oracle updated; parent class not separately replayed and later two
foreach assertions not reached. This debt stays explicit, not a green gate.
Global725END=687PASS37FAIL1interruption at240s, no XML finalized, same725IDs
and statuses as parent,35raw messages identical and2Diagnostics addresses only.
401covering identities outsideglobal399PASS2knownPictureFAIL; union1126 is
not exhaustive. No full-suite or merge qualification.

Fresh [snapshot](undashed-arc-c0567497f.json),631registry/443eligible unchanged :
217rendered(+5),194compared(+5),46>=99(+2),63>=95(+3),median77.45815728081598.
Sevenmetadata/eighteencaseinvariants preserved,212oldRGBAhashes/metrics identical,
lost0.608nontime rows identical,23deltas=5admissions+18diagnostics, not23gains.
Seventeen reasons gain index only; bug41422450 now reaches VertexLimit rather than
unused length FlatteningDidNotConverge, stillrefused. Vertices native30s retained.
Snapshot SHA256833bc1f91111096696c28ef1b2447cf5e53a22af9afd0db7cd4c4b71ce1c7e78.

Five actual/diff/reference triples inspected. Complexclip2PathBW99.91234477720964
and PathAA99.06135865595326 at±2, not bit-exact. RRectBW94.75237399561723 and
RRectAA94.22132943754565 contain broad filled-region/whole-cell discrepancies.
Crbug69138698.3154296875 almost blank, missing semicircle/closing edge:
background-biased admission metric, NOT visual parity. Root causes remain open.
Five generated PNGs byte-identical to corpus and five runner selections each
1PASS/XML1/0/0/0, no timeout/signal/skip. Five scores only plus Properties datestamp;
554other old keys unchanged. PathAA score differs from corpus by1.421e-14 from
arithmetic ordering, PNG/RGBA identical; no epsilon/threshold/reference changed.
Crbug historical98.4939575 refreshed to98.3154297 is not a regression of a qualified
old render: the parent fresh corpus refused this GM.

## Arbitrages du lot, dans l'ordre

1. Optimisation bornée math sans nouveau cycle d'approbation sous carte blanche,
   fondée sur quatre refus réels et avis Astra ; coût si erroné : rework réversible
   des deux fichiers, aucun gain garanti.
2. Certificat uniquement SVG arc non nul, ancienne mesure pour les cas non prouvés ;
   coût : travail évitable conservé, optimisation ultérieure possible.
3. Budget256 de test et points/spans littéraux, aucun cap production changé ;
   coût : correction de fixture avant SOURCE_GO seulement, jamais oracle GREEN affaibli.
4. Luna/high writer précis, Sol review uniquement et Astra stratégie ciblée ;
   coût : reprises du même worker puis escalade de capacité si nécessaire.
5. Custody privée conservée et revue finale combinée diagnostics/comportement ;
   coût : cette revue doit couvrir l'interaction, pas de petite PR diagnostic isolée.
6. Checklist SDD compacte en refactor malgré le design borné ;
   coût : un document de suivi, pas de scope produit supplémentaire.
7. Endpoints comparés au helper1e-9 déjà requis avant gel des oracles, mêmes
   points/count/order et deux REDs Ready ; ajouts D53/subnormal ;
   coût : rework TEST_ONLY avant source, aucun epsilon production modifié.

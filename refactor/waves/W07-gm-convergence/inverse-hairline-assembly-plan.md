# W7 inverse/hairline assembly Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Repair the measured plain W4e AA assembly and W6 unfiltered-root hairline source routing without claiming unmeasured GM337 parity.

**Architecture:** Reuse W4d's strict resolved plain-solid AA normalization independently of Rect geometry admission in W4e's authenticated construction seams. Reuse the existing closed root AA Rect-stroke source from original unfiltered occurrence facts under an admitted W6 owner. Preserve the separate inverse/filter construction boundary until actually measured.

**Tech Stack:** Kotlin/JVM, existing render IR/planners, public Surface/GmCanvas, Metal/wgpu native runtime, Gradle/JUnit.

**Spec:** refactor/waves/W07-gm-convergence/inverse-hairline-assembly-design.md

## Global Constraints

Fonts, codecs externes et jpg-color-cube hors périmètre. Aucun test d'infrastructure, mock, source-text, forwarding, skip GPU, CPU fallback, fake Picture ou GM-name routing. Ne pas changer epsilon, CompositionEnvelope, oracle tolerance, budgets, caps, registry631/eligible443, scopes/domains, références ou exclusions. Géométrie/numeric dans math et nomenclature I/F32/64. Tous les suivis durables dans refactor. Controller seul pour runtime serial borné240s, custody/evidence/docs/Git/publication ; workers produit/tests seulement et aucun helper/subagent/commit/runtime. Pas de merge ni suppression de custody.

## Review Focus

- Material permission must not admit Rect geometry or alter composed material/source authority; Task1 covers plain assembly and existing blending/refusal suites.
- forceAaFrame without requested draw AA must use coherent normalization; Task1 adds an AA clip with a HARD hairline.
- Hairline width is device-space under translation/nonuniform scale; Task1 checks both exact full pixel tables.
- A filter stripped from this occurrence must not authorize the root stroke source; Task2 pins filtered hairline refusal/recovery.
- A filtered sibling is not a direct filter and should not block an unfiltered root source; Task2 isolates this with supported NORMAL mask blur rather than an unconstructed inverse child.

---

### Task 1: Resolve strict plain AA material in W4e construction seams

**Files:**
- Modify gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt.
- Modify gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4eClipPlanCompiler.kt.
- Modify gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW4eMaterialGeometryRecipeV1.kt only for the measured BinaryConsumer inverse-path/clip equation.
- Create kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7InverseHairlineAssemblySurfacePixelTest.kt.
- Create integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7InverseHairlineAssemblySurfaceTest.kt.
- Preserve the temporary six-case W7InverseFilterDiagnosticSurfaceTest.kt unchanged.

**Interfaces:** Add trailing private constructor option resolvePlainAaSolids: Boolean = false to W4dGeneralPathPlanCompiler, propagate it through withRuntimeCatalog/withImageOriginProjection, and set true only in W4e's w4dHardSeam/w4dAaSeam. Factor the already strict standaloneFrameDraw eligibility and normalize/retain path rather than duplicating them. Effective eligibility uses forceAaFrame OR requested AA and requires all normalized visual draws satisfy standaloneFrameDraw. No geometry projection/admission flag changes and no enablement of w4dInverseAaSourceSeam.

- [x] Write the visible identity and transformed full-pixel tests exactly specified in the design, with native Render+Readback, isClean, zero refusals/diagnostics, positive dispatch and byte-identical repeat.
- [x] Add retained exact GM337 plain-cell public regression through the real GmCanvas/background/config and default hairline Paint, matching the diagnostic probe. Add force-AA clip control in the kanvas class; specify known literal samples away from AA clip edges, never invent edge tolerances. Tests are block-bodied Unit.
- [x] Freeze tests. Controller snapshots and runs :kanvas:test --tests '*W7InverseHairlineAssemblySurfacePixelTest' plus :integration-tests:skia:test --tests '*W7InverseHairlineAssemblySurfaceTest' serially under bounded runner. Require executed rendering refusals proving RED, not compilation/discovery failure. Await result before product edits. Actual Kanvas3/3RED tree05d3f002; corrected Skia1/1RED treec49d5ac7, same W4e final-construction/W4d1x. An earlier Skia constructor compile failure is not RED and remains explicitly separate.
- [x] Implement resolvePlainAaSolids and reuse strict normalization as above. Keep other eligibility and all pending-source/final-blend guards unchanged. Freeze and report.
- [x] Measured addendum before GREEN: correct only BinaryConsumer to complement pathCoverage, not clipCoverage, when consumer.inverse is set. Use the unchanged force-AA HARDinverse desired-positive's actual pixel RED on92862008; bindings/payload/resources/other recipes stay identical. Same worker, freeze then native rerun.
- [x] Controller reruns retained tests plus the unchanged six diagnostic cases. Require retained tests PASS; six probes are allowed to retain the three filtered failures, with exact diagnostics recorded. No weakening to achieve GREEN. Finala261f07c: Kanvas3PASS, retainedSkia1PASS, diagnostic6=3PASS3knownfilteredFAIL, all actual events/XML/no skip/no timeout.
- [x] Controller archives task delta from c4794751 test/source snapshot, obtains Sol spec+quality review and returns any finding to this worker. Keep source commit deferred until product qualification; do not publish the still-RED temporary probe. Sol specPASS/qualityApproved,0Critical0Important,1knownenvironmentMinor; fresh post-review source treea261 identical, retained/probe test delta empty. Broad native/corpus remain Task3 publication gates.

### Task 2: Select the existing unfiltered root AA Rect-stroke source under W6

**Files:**
- Modify gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt.
- Modify W4dGeneralPathPlanCompiler.kt only to expose/reuse its existing closed original-node predicate if needed.
- Create kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7FilteredSiblingHairlineSurfacePixelTest.kt.

**Interfaces:** Consume Task1 unchanged W4d resolved-normalization behavior and existing w6RootAaRectStrokeSource constructor/classifier/sourceAa publication. Reuse internal acceptsW6RootAaRectStrokeScope(node: DrawNode): Boolean; do not add a duplicate predicate. W6 root selection combines (ownsMixedRootAaRect || ownsW6b), scopeI32 == null, original command.node predicate and no direct image/mask filter; does not use filter-stripped segmentScene to establish authority. No explicit-layer source extension or new admission under a nonfilter W6 owner.

- [x] Add supported NORMAL mask-blur sibling plus visible root hairline tests using the exact Surface128×96 / triangle / sigma1.5 / +96 hairline fixtures in the design. Check all160hairline-region pixels, literal plateau Black(24,24), outside Blue(90,90) and native repeat, for identity and transformed hairline.
- [x] Pin original actually-filtered Rect/STROKE refusal on same Surface: sentinel unchanged, discard and admitted recovery with clean native repeat. No classifying a positive inverse source as a refusal to manufacture green. Fixture save/restore preserves captured facts and restores Canvas state before discard; API reset not changed.
- [x] Freeze tests; controller snapshots/runs :kanvas:test --tests '*W7FilteredSiblingHairlineSurfacePixelTest'. Require the unfiltered-root child selection RED and preserve existing genuine-filter refusal. Await result. Correctedfd7e7a1b actual1PASS/2sourceRED, no skip/timeout; prior recovery fixture failure remains historical.
- [x] Route only eligible unfiltered root occurrences to the existing closed source. Preserve current mixed-frame predicate, CompositionAdmission and original draw authority. Freeze and report. Private6c6d0ffc, original-node predicate/root owner/direct-filter guard only.
- [x] Controller reruns class plus all six unchanged diagnostic probes. Require retained tests PASS, explain the first remaining terminal boundary without claiming filtered inverse support. Kanvas6PASS, retainedSkia1PASS; unchanged sixprobe3PASS3FAIL now W4e AA source-construction single-sample guard. Exact native receipts retained.
- [x] Sol task gate from frozen Task1 baseline; fix loop belongs to this worker. Defer commit until qualification. Approved spec+quality,0Critical/0Important/1knownenvironmentMinor; original receipt custody and frozen hash verified by controller.

### Task 3: Qualify useful repair and carry forward measured inverse/filter gap

**Files:** this plan, design if evidence requires a bounded amendment, refactor/waves/W07-gm-convergence/status.md and pilotage.md; only native/corpus artifacts produced by existing protocol.

**Interfaces:** Frozen reviewed Task1/Task2 product/tests; diagnostic reason enrichment already reviewed Sol. Controller-only qualification/publication. Next inverse/filter capability has no implementation interface in this plan and needs a measured addendum first.

- [x] Run fresh covering W7 root AA/hairline/mixed-root/inverse/Picture/clip and W6 filter/recovery suites on final source tree, with complete event/XML accounting. Attribute failures precisely; no historical global-green claim. Actual449/452PASS/3knownFAIL/no skip/timeout; two Picture failures separately reproduced on exactparent. Fresh global729=691PASS37knownFAIL1interruptSKIP, bounded240s/incomplete, no newly reached failure.
- [x] Rerun exact six GM337 cases; archive the still-RED temporary source privately and exclude it from the product commit without deleting custody. Record remaining original-fact diagnostics and next targeted design needed. Unchanged sixprobe3PASS3FAIL at W4e AA source-construction guard, byte-identical private archive retained; temporary source untracked, not committed.
- [x] Snapshot and commit reviewed product/retained tests before fresh corpus, then run existing631/443 serial protocol with immutable18invariants. Inspect changed/new images and actual GM337/338 state, gains/losses. No reference edits. Source2e5419afd; 212rendered/189compared,5gains/0losses,all207oldhashes/metrics unchanged. Five inspected PNGs regenerated byte-identical to corpus, onlyfive score keys updated; actualfive native1PASS0/0/XMLclean, no skip/timeout. Snapshot SHA896e47f3ef12e402e9c143d5eb7efe546b89b198866edb73ec5b80d0e227bdef.
- [x] Astra broad review of the scoped new branch lot against #2431 base, qualification evidence and ledger; one worker fix wave plus scoped re-review if needed. IntroducedC0/I0/M0, inheritedportI1/environmentM1; draft approved conditional on controller artifact/docs gates, now artifact equality verified. Declined items remain explicit gaps; all source/runtime/comparison custody retained. No product correction requested. Next strategy: bounded faithful ComplexClip2 port before root inverse/filter architecture.
- [ ] Record exact evidence/status and next boundary in refactor. Publish/attach/verify draft PR stacked on codex/w7-inverse-scene-inventory (#2431). No merge or W7 completion claim; continue with the next measured capability design.

## Self-review

Two material/source boundaries map to Tasks1/2 with separate public native RED/GREEN. The third task records residual inverse/filter refusal instead of opening an unqualified Picture issuer. Constructor propagation is explicit, source origin remains original, force-AA and device-space geometry have dedicated witnesses. No numeric helpers, budgets, oracle change or positive-test deletion is planned. Review/qualification/publication are separate from native assembly success. Runtime and Git are controller-owned.

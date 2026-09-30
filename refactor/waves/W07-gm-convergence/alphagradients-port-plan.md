# W7 Faithful Alphagradients Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the actual two-column non-AA alphagradients scene through an explicit per-GM composition contract and measure its changed pixels separately.

**Architecture:** SkiaGm declares its composition domain; one config-copy helper supplies every harness-owned Surface. The existing engine renders the faithful scene unchanged. No renderer/shader/adaptor approximation.

**Tech Stack:** Kotlin/JUnit, public native Surface, integration-tests/skia, existing bounded parity checkpoint.

**Spec:** [alphagradients-port-design.md](alphagradients-port-design.md)

## Global Constraints

- Base1915794f71cc69b2c04718e9ab5b65423ee76d60, branch codex/w7-alphagradients-port; workdir /Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas. Never edit cbf6. All commands rtk/explicit workdir, edits absolute apply_patch.
- SkiaGm domain is authoritative over base RenderConfig.compositionDomain; every other field is preserved. Default GM domain LINEAR; only alphagradients declares SRGB_ENCODED. No GM-name matching.
- Keep original24fill+24Rect/STROKE width0 occurrences, source pairs, diagonal gradient, placement and640x480 extent. STRAIGHT left/PREMULTIPLIED right, AAfalse for both paints.
- No engine/authority/proof/cap/envelope changes. Geometry remains math-owned. No new infrastructure/mock/source-text tests; only real public rendering/refusal/recovery witnesses.
- Fix expectations before GPU; no oracle uses product shader/helpers or GPU/reference pixels. Existing GM threshold0 and tolerance2 unchanged.
- Fonts/codecs/external decoding/jpg-color-cube, other GM scenes, reference PNGs, scores, exclusions and631/443 denominator unchanged.30s timeout retained, no global score regeneration.
- One runtime at a time. Archive every attempt and exact process exit. Draft on2423; no merge/W7 closure.

## Review Focus

1. A second renderer entry silently uses LINEAR: terminal/inventory encoded-AA refusal witness plus static audit of checkpoint and delegated scanner/generator/runner routes.
2. Config replacement discards caller budget: public budget1 refusal and healthy recovery with the same declared GM domain.
3. Both columns use same alpha policy or gradient becomes horizontal: two literal pixel witnesses and entire independently computed image.
4. Hairline becomes localwidth1 or remains AA: exact complete ring and neighboring background, including right/bottom/corners across24cells.
5. Other GM defaults or references change: default-domain public pixels plus final631identity comparison,197other oldRGBA unchanged; explicit new domain metadata audit.

---

### Task 1: Explicit GM domain and faithful alphagradients public pixels

**Files:**
- Modify: integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt (declaration/config projection).
- Modify: integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt (all three Surface creations).
- Modify: integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmParityCheckpoint.kt (Surface config and additive declared-domain metadata before watchdog identity copy).
- Modify: integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/gradient/AlphaGradientsGm.kt (faithful scene only).
- Create: integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/AlphaGradientsSurfacePixelTest.kt (public pixels and domain/refusal witnesses).
- Modify: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RectHairlineSurfacePixelTest.kt (move row-width assertion outside inner character loop only; parked2423Minor).

**Interfaces:**
- `val SkiaGm.compositionDomain: CompositionDomain` default LINEAR.
- `internal fun SkiaGm.compositionConfig(base: RenderConfig = RenderConfig.DEFAULT): RenderConfig`, implemented with base.copy(compositionDomain = compositionDomain), all other fields preserved.
- Existing SkiaGmRenderer public signatures unchanged; checkpoint GM rows gain `compositionDomain` = enum.name. Keep schema backward-compatible/additive and all existing fields.

- [ ] **Step 1: Native behavioral RED.** Add `alphagradientsMatchesIndependentTwoColumnScene()` using existing AlphaGradientsGm/renderer APIs, no new interface referenced yet. Before any rendering, create the expected full640x480 RGBA from the spec's literal geometry/12pairs and F64 equations; check expected(160,25)=191RGB, right(470,25)=255RGB, black/white edge coordinates. Render actual old GM, assert these discriminants first then all pixels (RGB≤2 only in interiors, borders/background/alpha exact), zero refusals/diagnostics, nonzero dispatch, repeated bytes. Run just this method and archive the actual mismatched pixel as RED; a missing-symbol/compile error is not RED.
- [ ] **Step 2: Implement the GM contract and port.** Add the two declared interfaces; thread through renderer's three Surface sites and checkpoint. Verify runner/scanner/generator delegation without changing their scoring/side effects. Port pinned Skia column mode/AA/width0, preserving all geometry/colors and the historical threshold. Add property metadata before timeoutIdentity. No changes in GmCanvas, shader implementation or native renderer.
- [ ] **Step 3: Public domain/refusal witnesses.** `gmDomainControlsPixelsWithoutDiscardingCallerBudget`: real2x2 GMs black alpha128 over white; default LINEAR187±2 vs encoded127±2, alpha255, two renders, opposing config domain overridden. frameLocalBudgetBytes1 refuses, normal config recovers exact healthy pixels. `encodedUnsupportedDrawRefusesAcrossHarnessEntriesAndRecovers`: encoded Rect FILL AAtrue refuses via render, renderTerminalAttempt and inventoryEvidence with unsupported.surface.composition.geometry; healthy encoded GM subsequently renders127±2, no refusal. No mocked captures or private plan/config equality assertions. Make helper row-width assertion in existing hairline test execute even for empty rows; no new helper test.
- [ ] **Step 4: Verify and report.** Run new class plus GmCanvasSurfacePixelTest on Skia task; run W7RectHairlineSurfacePixelTest13cases on Kanvas separately, distinct evidence directories. Assert expected count/no skips, exits0, preserve3baseline identities, all13existinghairline identities. Static audit: every harness Surface uses projection; only target GM changes scene/domain; all non-domain config fields copied. No global Kanvas/corpus or Runner/SimilarityTracker invocation by worker. Report covering commands/output, RED cause, warnings, and limits; don't infer native Green from a crash.
- [ ] **Step 5: Self-review and commit.** Commit only six task files, report to own SDD workspace. Root owns refactor docs. Independent task review required, no worker subagents.

## Verification commands and controller work

Evidence root /private/tmp/kanvas-w7-alphagradients.QVeKNw; own bounded-run.rb,
existing init /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle.

`rtk proxy ruby /private/tmp/kanvas-w7-alphagradients.QVeKNw/bounded-run.rb /private/tmp/kanvas-w7-alphagradients.QVeKNw/RUN 240 ./gradlew :integration-tests:skia:test --offline --no-daemon --no-build-cache --tests org.graphiks.kanvas.skia.AlphaGradientsSurfacePixelTest --tests org.graphiks.kanvas.skia.GmCanvasSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-alphagradients.QVeKNw/RUN --console=plain`

For hairline use :kanvas:test and only its fully-qualified class, separateRUN.

- [x] Fresh baseline GmCanvasSurfacePixelTest3/3, child/wrapper0; parent renderer69/69 and global red/incomplete retained as prior evidence, not rerun or relabeled.
- [ ] Task1 reviewed, fixes verified. Whole-branch final Astra review, at most one grouped fix and scoped Sol re-review, residuals adjudicated visibly.
- [ ] Corpus after final code: measureSkiaParity631/443, slices[0,607),[607,608),[608,631), timeout30s, imagestrue, rendererCommit exactHEAD. Compare to encoded-hairline-f80d94fb4.json. Expect only alphagradients pixels to change; investigate any other difference. Explicitly audit new domain field630LINEAR/1SRGB_ENCODED. No reference/threshold/score update.
- [ ] Inspect actual/diff PNG and report alphagradients whole-image/edge/interior evidence without inventing provenance of reference. Save one durable final snapshot and update pilotage/status/README.
- [ ] Publish and attach stacked draft on2423; keep W7 active and select next measured root cause from remaining corpus, not another approval loop.

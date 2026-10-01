# W7 Faithful Clip GM Ports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct two proven GM-port semantic errors and measure fidelity of four newly admitted scenes, without changing the renderer.
**Architecture:** Retain public clip operations: explicit Difference for the leaf paths; persistent device-space restriction for each complexclip4 branch. Emulate only the observable fixed GM scene, not a new Android ResetClip API.
**Tech Stack:** Kotlin/JVM, GmCanvas/public Surface, native wgpu/Metal pixel tests.
**Spec:** Bounded design in this document and upstream Skia `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`, `gm/manypathatlases.cpp` and `gm/complexclip4.cpp` (local clone files clean). Controller read both in full.

## Global Constraints

- Worktree `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branch `codex/w7-clip-gm-ports`, base `408067306e2b097f5b46be37c2b8a1f565164f97` (draft#2428).
- Only two GM ports and one new public native GM pixel-test file are product/test scope. No renderer, GmCanvas, math, registry, reference, threshold, composition domain, budget, cap or exclusion changes.
- Fonts, external decoding/codecs and jpg-color-cube stay excluded. No CPU fallback, GM-name renderer routing, fake Picture, infrastructure/mock/forwarding/source-text tests.
- Controller alone runs all Gradle/native/corpus, strictly one bounded240s native process group at a time, actual handle retained until terminal. Workers never run builds/native or spawn helpers.
- Product/test edits by implementer; docs/generated artifacts/evidence/publication by controller. RTK shell and apply_patch.
- Reuse existing isolated clean worktree; draft stacked on#2428, no merge. Preserve evidence and old oracles.

## Design and diagnosis

ManyPathAtlases clips are Difference+AA in upstream, but currently default to Intersect. complexclip4 device restrictions persist across ResetClip in upstream; the port discards them before yellow draws, and transforms the last device restriction. Restore equivalent public clip-stack lifetime for this fixed scene. Keep literal source colors and geometry; residual color-space mismatch is measured separately, never compensated to fit PNGs.

GmCanvas.clipRect is a deferred legacy path, so retain the existing public clipPath rectangle representation, using hard AA=false for device restrictions. Normal replacement clips retain doAAClip. In the last branch, record the smaller device restriction while CTM is still identity, then preserve rotate/translate and issue device-space drawColor via the existing RGBA+BlendMode overload (SRC_OVER); its drawColor is independent of CTM. Do not add a new GmCanvas/resetClip abstraction or inverse-transform workaround.

## Review Focus

- Difference rather than Intersect: centre excluded/yellow and distant uncut interior teal, both atlas variants.
- Restriction survives replacement: outside first rectangle stays background, all three upper/right regions.
- Last restriction is device-space: lower-left yellow fixed rectangle independent of rotate/translate.
- AA/BW preserve identical interior semantics; no edge oracle derived from renderer.
- Correct result is real native output and repeat, not successful capture/score alone.

### Task 1: Repair faithful clip-port semantics with causal public pixels

**Files:**
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/clip/ManyPathAtlasesGm.kt`.
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/clip/ComplexClip4Gm.kt`.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/ClipGmPortSurfacePixelTest.kt`.

**Interfaces:** Existing SkiaGm/GmCanvas/Surface; use the native rendering/evidence pattern in Sk3dSimpleSurfacePixelTest, without a fake Canvas or render oracle. GmCanvas.clipPath(path,op,antiAlias), drawColor(r,g,b,a,mode) already exist. Scope/ownership and runtime constraints above bind this task.

- [ ] Write four named tests first: `manyPathAtlases128UsesDifference`, `manyPathAtlases2048UsesDifference`, `complexClip4AaKeepsDeviceRestriction`, `complexClip4BwKeepsDeviceRestriction`. Render the actual corresponding GM through public Surface with its compositionConfig. Assert Render+Readback, positive dispatch, zero refused/diagnostics, byte-identical fresh second render. Dispose shared backend after each test; no capability skip added.
- [ ] Pin literal interior pixels, not PNG/product helper oracles. Many: centre(64,70) yellow[255,255,0,255], top interior(64,1) teal[8,232,222,255], corner(0,0)yellow. The top witness lies outside radius64.04 of leaf rotation centre while inside the outer path; centre lies within the leaf interior, far from boundaries.
- [ ] Complex variants: green[0,255,0,255] at(120,120),(120,500),(270,500); yellow[255,255,0,255] at(120,220),(750,250),(700,650),(200,500); original background[222,223,222,255] at(350,250),(850,300),(850,675),(350,500). Explain that literal source colors test scene semantics, not reference color parity.
- [ ] Freeze tests only and report READY_FOR_RED. Controller runs `:integration-tests:skia:test --tests '*ClipGmPortSurfacePixelTest'` with standard private wrapper/init. Expected four causal pixel failures; compile failure is not RED. No port edits before controller authorization.
- [ ] After causal RED, set explicit `ClipOp.DIFFERENCE` and `antiAlias=true` for four leaf clips. Preserve all curve/rotation/path/paint constants.
- [ ] Preserve complexclip4 device restrictions through yellow draw: remove temporary green-only save/restore, hard restriction rectangle path remains in each outer branch save scope. Keep replacement clips/doAAClip and existing geometry. In final branch install smaller hard device restriction before rotate/translate, then use existing device-space drawColor overload SRC_OVER. Fix KDoc: a scene-level adaptation, not general ResetClip support. No new API.
- [ ] Freeze source, self-review, await controller GREEN and covering controls; report exact commands/results supplied by controller. If a literal pixel fails, diagnose before changing any oracle; colors/geometry are pinned upstream.
- [ ] Commit only the three product/test files after controller GREEN. Return report with TDD evidence/self-review/concerns; no runtime or reviewer delegation.
- [ ] Sol task review; corrections via same implementer and scoped re-review.

## Controller qualification and publication

- [ ] Run new4 + prior GM9 + W7ClipProducerScissor27 (40unique targeted), no skips/aborts; product renderer unchanged. Previous global724/37failure/1interrupt remains attributed to be813afd7, not relabelled as fresh port-SHA global.
- [ ] Per regenerate-renders, regenerate only GM ranges129–131 and372–374 with generateSkiaRendersFor, then SkiaGmRunner filtered to those ranges for four partial similarity scores. Verify only those generated PNG/score entries changed. No full unbounded suite.
- [ ] Final committed-SHA full corpus631/443 sequential slices with strict aggregator vs clip-producer-scissor-be813afd7.json. Preserve18invariants, inspect every changed image/hash/metric and all old-render stability; no promise of score gain.
- [ ] Independent final review, consolidate fixes if any, qualification/doc updates in refactor. Publish/attach draft stacked on codex/w7-clip-producer-scissor (#2428), no merge/W7 completion.

## Self-review and delegated decision

All source edits map to upstream semantics, tests render the actual ports and causally detect them, all review risks have literal witnesses. One small task batches the two same-shape port repairs. User explicitly delegates W7 changes and chose SDD; controller accepts this bounded design without another approval loop. If admission fails after correcting the port, stop product expansion and diagnose before proposing renderer changes. Generated scores are observations, not changed thresholds.


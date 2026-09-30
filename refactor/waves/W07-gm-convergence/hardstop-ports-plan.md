# W7 Faithful Hardstop Ports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Correct the two existing hardstop GM scenes and measure their real Skia pixel convergence without changing the engine or comparison policy.

**Architecture:** Bounded scene-port repair, no new interfaces. Preserve the existing LINEAR GM default and rendering/diagnostic contract from #2424. One batched task owns two same-shape ports and their public native pixel witnesses.

**Tech Stack:** Kotlin/JUnit, SkiaGmRenderer, native Surface, existing parity checkpoint.

**Spec:** The design contract below; numeric semantics in W5 design §7.1–7.2. This compact combined contract/plan preserves the user's selected SDD workflow without a new architectural subsystem or approval loop under the W7 carte blanche.

## Design contract and evidence

Base `f0939c4d079037663af24f84dd6fdf416d1e5783`, draft[#2424](https://github.com/ygdrasil-io/kanvas/pull/2424), branch `codex/w7-hardstop-ports`.
Parent corpus631/443: hardstop_gradients index292=16.11328125%, hardstop_gradients_many index293=10.65515%; both rendered with zero refusals. Source files at pinned Skia revision8019e2e0629f3516b9d829737de2553b1d0ecb4a were read completely:
[grid](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/hardstop_gradients.cpp),
[many](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/hardstop_gradients_many.cpp).

The grid's output stays512×512, but its layout uses500/3=166 and500/8=62 cells, pad3, rect160×56, gradient30px inside both horizontal edges (span100). Existing port erroneously derives cells170×64 from512. Colors/positions/tile modes stay as upstream, paint non-AA.

The many scene stays1000×2000,100rows of20px, rect(0,1,1000,18) as XYWH, translated20 each row; thus covered local y1..18 inclusive, with y0/19 white. Row N has2N alternating blue/white stops: blue0, then for k=1..N−1 white and blue both at F32(k/N), then white1. Existing port inserts blue+white at0 and ends rectangles at18 instead of19. Paint is non-AA, CLAMP unchanged.

Repair the source scenes, not references, global renderer settings or shader logic. Reject global encoded composition and any special-case GM-name dispatch: both scenes are opaque and retain LINEAR. No general gradient refactor. Reference generator provenance remains unknown; pinned source fidelity and comparison to the unchanged historical PNG are separate evidence.

## Global Constraints

- Workdir /Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas; never edit cbf6. Every command rtk with explicit workdir; edits absolute apply_patch.
- Only the two named GM scenes plus their new native test file change. No engine/math/shader/adapter/domain/config/authority/proof/cap/envelope modification.
- Both GM composition domains remain inherited LINEAR. Preserve names, extents512x512/1000x2000, colors, tile modes, thresholds0, tolerance2, reference paths/hashes, scopes, exclusions and631/443 denominator. No historical score regeneration.
- No new infrastructure/mock/source-text/forwarding tests. Use the actual two GM instances through SkiaGmRenderer and native pixels, including full images, repeat bytes and zero refusals/diagnostics.
- Fix expectations before GPU and before port edits. No product geometry/shader helpers, GM constants/stops or reference/GPU pixels in expected builders. No skipped pixels or relaxed tolerance at hard stops; escalate unexpected numeric boundary mismatch instead of adapting the oracle to actual output.
- One native runtime at a time, unique archive per attempt, report process exits and actual XML identities. Fonts/codecs/external decoding/jpg-color-cube remain excluded.
- Draft stacked on2424, no merge/W7 closure. Keep inherited global red/incomplete evidence; no unrestricted Runner or global suite that writes scores.

## Review Focus

1. Extent vs layout confusion: entire grid oracle includes right/bottom white margins and every row/column.
2. Wrong color ordering at duplicated stops: all100many rows, including immediately around a seam and the first uninterrupted ramp.
3. XYWH mistaken for LTRB: entire row18 painted, row19 white in all100bands.
4. Wrong tiling or endpoint hard-stop behavior: full24grid cells, negative and >1 raw t; CLAMP outside returns extreme color before endpoint tie selection, REPEAT/MIRROR preserve §7.1 semantics.
5. Incidental scope/domain/renderer change or lost alpha regression: final public controls and full631identity/domain/reference/RGBA audit.

## Independent image oracle

Build complete expected ByteArrays before GPU, prefilled opaque white, size exactly width*height*4. Literal scene data independent of GM fields and no renderer oracle helper. Geometry and alpha exact, RGB≤2 only inside painted rectangles. Include every pixel, not sampled rows or excluded seam windows.

Coordinate projection follows the documented F32 geometric parameter: separate Float subtract, multiply and divide; grid tRaw=((x+.5f−(166*col+33))*100f)/10000f, many tRaw=((x+.5f)*1000f)/1000000f. Constants are literals from the pinned scene; positions k.toFloat()/N.toFloat() reflect authored F32 API values. Use Double for interpolation after selecting the greatest stop position≤t; duplicated positions are right-continuous. Grid CLAMP raw<0 returns first color, raw>1 last color; otherwise CLAMP t, REPEAT t−floor(t), MIRROR triangular period2. No clamp shortcut that destroys the outside side of an endpoint hard stop. Interpolate encoded opaque RGB, round nearest8bit. This model is independent of implementation helpers; F32 decisions are specified before any GPU result and do not imply universal Skia numerical equivalence.

Grid independent arrays: colors RGB red,green,blue,yellow,magenta; counts[2,3,3,5,4,3,3,4]; positions evenly spaced for rows0/1, then [0,.25,1], [0,.25,.5,.5,1], [0,.5,.5,1], [0,0,1], [0,1,1], [0,.3,.3,1]. Rect left166*col+3,top62*row+3,width160,height56. Verify oracle literal witnesses before render: (164,10),(10,60),(495,10),(10,493) white; (83,10)=(126,129,0,255), (150,10)=green. The old grid must fail a white-margin/row-gap witness.

Many: per row N1..100, colors blue/white repeated, literal geometry above. Compute a row's1000expected pixel values once then copy to all18painted lines; don't duplicate a 200-stop search for2million pixels unnecessarily. Verify oracle literals: (0,1)blue; (499,1)=(127,127,255,255); (500,1)=(128,128,255,255); (500,18) same; (500,0)/(500,19) white; (499,21)white, (500,21)blue. UseRGB±2 for native interior witnesses, exact background/alpha. Old row1 flat white and/or missing y18 must fail.

### Task 1: Faithful hardstop scene batch and full public pixel witnesses

**Files:**
- Modify integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/gradient/HardstopGradientShaderGm.kt.
- Modify integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/gradient/HardstopGradientsManyGm.kt.
- Create integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/HardstopGradientSurfacePixelTest.kt.

**Interfaces:** Existing SkiaGmRenderer.render(SkiaGm) only, no new runtime interface. New tests `gridMatchesIndependentFiveHundredLayout` and `manyMatchesIndependentBlueWhiteRamps` consume actual GM classes and private independent image builders. Follow existing GPU availability and @AfterEach backend dispose pattern.

- [x] **Step 1: Write both public tests first.** Read the separate oracle contract supplied by the controller. Each test builds its whole independent image before GPU, self-checks literal witnesses, renders the old GM, checks discriminants then every pixel, dimensions/buffer size, zero refusedCount and empty diagnostics, dispatchedCount>0, then a second render byte-identical and also clean. No product helpers/GM constants in oracle.
- [x] **Step 2: Run causal RED.** Run the new two-test class only, expecting2native pixel failures, not compile/setup failures. Archive command/XML/exit and exact mismatched coordinates. If one test passes unexpectedly, investigate its coverage before port edits.
- [x] **Step 3: Correct the grid port.** Keep output extent512; use layout500 cells166×62 and non-AA paint. No stop/color/tile rewrite. Pin its upstream source link to the audited SHA. Run just grid method, expect1PASS with full-image assertions; if not, diagnose rather than change oracle/tolerance.
- [x] **Step 4: Correct the many port.** Build blue0; each internal position white then blue; white1,2Nstops. Use XYWH rect0,1,1000,18 (or explicit LTRB bottom19), non-AA. Preserve translation and CLAMP, pin upstream source. Run just many method, expect1PASS, all100rows/allpixels. Any numeric boundary failure is evidence to report, not permission to skip it or alter expected values.
- [x] **Step 5: Final focused controls.** Run new class + AlphaGradientsSurfacePixelTest + GmCanvasSurfacePixelTest, expect9tests, zero skipped/error/failure, all7baseline identities unchanged, child/wrapper0. Read warnings and report them. No global/corpus/Runner/score writes by implementer.
- [x] **Step 6: Self-review and commit.** Commit only three task files. Write own task-1-report.md with RED/GREEN exact commands/output/counts/exits, source changes, oracle independence and limits. No worker subagents or push/PR. Controller performs task review, final whole-branch review, full corpus and docs.

## Validation and controller gates

Evidence root /private/tmp/kanvas-w7-hardstop.pVPuxL. Own bounded-run.rb, isolated init /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle. Example final command:

`rtk proxy ruby /private/tmp/kanvas-w7-hardstop.pVPuxL/bounded-run.rb /private/tmp/kanvas-w7-hardstop.pVPuxL/RUN 240 ./gradlew :integration-tests:skia:test --offline --no-daemon --no-build-cache --tests org.graphiks.kanvas.skia.HardstopGradientSurfacePixelTest --tests org.graphiks.kanvas.skia.AlphaGradientsSurfacePixelTest --tests org.graphiks.kanvas.skia.GmCanvasSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-hardstop.pVPuxL/RUN --console=plain`

- [x] Fresh baseline7/7, all7parent identities unchanged, native exits0; parent corpus authoritative, inherited global still red/incomplete.
- [x] Task Sol review, fix/review if needed; final whole-branch Astra review per selected SDD, at most one grouped final fix and scoped re-review. No global-green/merge claim. Code `02802171b`, final test-only correction `34e3d4e98`; native final9/9, all7baseline identities preserved. Astra C0/I0/M2: one native interior RGB witness aligned to existing ±2 contract, scoped Sol confirms; inherited Java/LWJGL/Gradle warnings remain explicit.
- [x] Full631/443 corpus after final code, timeout30s, same three slices [0,607),[607,608),[608,631), exact rendererCommit and images. Compare alphagradients-port-6259c38d8.json; only these two targets may change pixels,196others including alphagradients byte-identical. Separately audit630LINEAR/1encoded (alphagradients). Snapshot `hardstop-ports-34e3d4e98.json`: no invariant/domain changes, no lost/new renders, slices0/1/0 with inherited vertices30s; both targets100%within2, maxRGB2/1, alpha0. All corpus gates resolved.
- [x] Inspect both targets' actual/diff PNGs; attribute port gains and any residual separately, not promise100%. Save one snapshot/update pilotage/status/README. Images inspected; actual measured100%within2 is not exact parity (90.4724%/96.382% exact).
- [x] Publish+attach draft [#2425](https://github.com/ygdrasil-io/kanvas/pull/2425) on2424, keep W7 active. No merge; worktree and evidence retained.

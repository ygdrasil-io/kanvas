# W7 proof evaluation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Keep the fix local, test public rendering and obtain independent review.

**Goal:** Remove repeated CPU proof work that prevents deeply wrapped gradients and the broader validation from completing.

**Architecture:** Memoize rounded scalar evaluation by equivalent immutable conditioning environments, not by the allocation identity of their maps. Preserve scalar object identity, exact bound values and the lexical Noise region/octave. Keep the numerical algorithm and the original conditioned maps unchanged.

**Tech Stack:** Kotlin/JVM, gpu-plan, native Metal/WebGPU Surface tests.

**Spec:** The CPU stall documented in [pilotage.md](pilotage.md), within the user's delegated W7 pilotage. This is a bounded repair of an existing flow, not a new proof engine.

## Global Constraints

- Fonts, codecs, external decode and quarantined jpg-color-cube remain outside scope.
- Do not change GM ports, references, comparison thresholds, exclusions, numerical envelopes, capture limits or resource budgets.
- No new infrastructure tests: witnesses exercise public Surface pixels, refusal and recovery.
- Geometric types stay in math with I/F32/64 names. This task adds no geometric type or public API.
- Do not erase a condition merely because its bounds equal the unconditioned result: membership also represents a materialized F32 expression and controls Add reassociation.
- Work only in `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branch `codex/w7-proof-evaluation`. Shell commands use rtk; file edits use apply_patch. Do not spawn agents from an implementer. Serialize Gradle/GPU work.

## Review Focus

- Distinct Scalar instances, even structurally equal data classes, must never alias in a conditioning key.
- Equal key hashes are not equality; bound values, signed zero and every conditioning entry must be compared.
- Noise evaluations at different regions or octaves must remain separate; do not modify the secondary noiseResults cache.
- Contexts used for caching must be immutable snapshots; no mutable key or dropped constraint.
- Cache hits may remove repeated branch-fact trace entries, but must retain the facts from the original equivalent evaluation. Canonical proof strings are opaque computed identities, not a stable serialized ABI. Do not replay exponentially duplicated facts merely to preserve their old textual multiplicity. Verify authentication and resource behavior publicly and report changed corpus outcomes.

## Evidence and design decision

On unchanged `fa3887370`, the isolated existing test
`W5dGradientAddressingSurfacePixelTest.sweepFullCoveragePreservesRequestedTileBudgetIdentity`
exceeds a 60-second Gradle test-task timeout. A fresh thread dump shows
51.0 s CPU / 54.0 s elapsed inside `ColorRoundedGraphProofV1.evaluate`,
`contexts` and cumulative eager/lazy coordinate guards. The test wraps each
gradient in 20 matrix/clamp pairs. `contexts` repeatedly allocates equivalent
maps while `evaluate` caches by map identity. The original dump is retained
at `/private/tmp/kanvas-w7-proof-red-thread.txt`.

Skipping no-op conditions was rejected because map membership controls
materialization/reassociation. A global graph rewrite or proof work cap is
unnecessary at this stage. First test exact environment memoization; if the
same witness still times out, capture the new hotspot and return to diagnosis
before adding another optimization.

### Task 1: Memoize equivalent proof environments

**Files:**
- Modify `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofV1.kt`.
- Create `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7ProofContextSurfacePixelTest.kt`.
- Reuse existing W5d coordinate, W5f filter and W5g Noise public tests unchanged.

**Interfaces:** `ColorRoundedGraphProofV1.prove(...)` and `evaluate(Scalar, Map<Scalar, ColorBoundsV1>)` retain their signatures. New cache key/entry helpers are private implementation details, local to the proof or file. No authority or shader graph API changes.

- [ ] Add a public regression `twentyCoordinatePairsPreserveSweepPixels`: for each TileMode and hard/AA paint, render a 17x1 Surface with a full-circle SweepGradient centered at (0,0), two opaque red stops at 0/1, wrapped in 20 repetitions of WithLocalMatrix(CoordClamp(shader, RectF32(0,0,17,1)), identity matrix). Draw full coverage (hard Rect or AA RRect extending to (-1,-1,18,2), radius .5). Assert all 17 literal RGBA pixels are (255,0,0,255), native Render/Readback evidence, and a second render with the same output. Add @AfterAll disposal on the test thread like the existing W7 tests. No private counters, reflection or source-text assertions.
- [ ] Run this new test RED under `/private/tmp/kanvas-w7-proof-timeout.gradle` (60 s task timeout) before production edits; retain logs. Confirm the worker is stopped before another GPU run.
- [ ] Replace only scalar-cache environment keys. Compare entries by Scalar identity and ColorBoundsV1 value, with identity-safe hashing, and include active Noise region identity and octave. Snapshot/cache immutable maps as needed; avoid recursive Scalar data-class equals/hashCode. Retain every original condition and numerical branch. Cache equivalent results only within one prove invocation. Leave noiseResults unchanged. First evaluation supplies its branch facts; equivalent hits must not add duplicate work or erase facts already recorded.
- [ ] Run the new public witness and the original stalled test with the same 60 s bound. If the original now reaches an unrelated assertion failure, record it unchanged instead of changing thresholds/budgets to pass. If the new witness still times out, stop the implementation attempt and report the new stack for diagnosis.
- [ ] Run public coordinate ordering/projective/refusal tests plus Noise and color-filter regressions; main will run the wider suites and corpus after the production commit. Report every observed failure, including pre-existing/uncertain ones. Do not claim a full green suite from the focused selection.
- [ ] Self-review, commit owned code/tests, write the full report with commands, RED/GREEN timings and concerns. Independent task review verifies spec and quality; fix confirmed findings.

### Task 2: Verify and publish the bounded lot

**Owner:** Controller validation and documentation, no second implementation agent.

- [ ] Run the full Kanvas suite with a bounded task timeout, plus math geometry. Classify all failures or any new remaining stall; do not widen this fix automatically to unrelated behavior.
- [ ] Replay the fixed 631-case Skia corpus at the committed renderer SHA, 30 s per case. Keep all outcomes and resume after recorded timeouts. Compare identities, references, outcomes and RGBA hashes with `dash-5f971f750.json`.
- [ ] Record checkpoint, public test totals and open defects in pilotage/status. Obtain an independent whole-branch review and address confirmed findings.
- [ ] Push and create a draft PR stacked on #2413, attach it to the chat. Do not merge or call the overall renderer complete while full validation is red.

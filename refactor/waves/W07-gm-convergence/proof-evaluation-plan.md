# W7 proof evaluation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Keep the fix local, test public rendering and obtain independent review.

**Goal:** Remove repeated CPU proof work that prevents deeply wrapped gradients and the broader validation from completing.

**Architecture:** Memoize rounded scalar evaluation by conditioning environments projected on conservative scalar dependencies, not by map allocation identity or irrelevant outer conditions. Preserve scalar object identity, constraint membership, exact bound values and the lexical Noise region/octave. Keep the numerical algorithm and the complete original conditioned maps unchanged during evaluation; opaque regions retain full-context keys.

**Tech Stack:** Kotlin/JVM, gpu-plan, native Metal/WebGPU Surface tests.

**Spec:** The CPU stall documented in [pilotage.md](https://github.com/ygdrasil-io/kanvas/blob/36350563f48485598009d61a1707f7cff0ff7e94/refactor/waves/W07-gm-convergence/pilotage.md), within the user's delegated W7 pilotage. This is a bounded repair of an existing flow, not a new proof engine.

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

### Refinement after the first falsified hypothesis

The exact full-environment candidate still times out on the new public
witness at 60.100 s. The dump records 36.08827 s CPU / 46.80 s elapsed in
snapshot construction; it does not quantify distinct contexts. The first
candidate snapshots on every lookup and compares entries quadratically.

Astra's focused diagnosis identifies an independent structural cause:
matrix k adds its affine flag to a branch context when evaluating hx_k,
while cumulative Finite(hx_k) also evaluates earlier coordinates without
that flag. Earlier coordinate nodes do not depend on flag k, yet a complete
context key keeps these environments distinct. The distinctions compose
through the 20 matrix/clamp pairs.

Second hypothesis: project cache keys onto a conservatively computed
transitive dependency set. Include each node itself, all arithmetic operands,
all predicate operands and both branch arms. Preserve explicit membership
of every reachable Add. Image/Noise/GradientStop and other vocabulary with
implicit or generated dependencies use a full-context marker, propagated
to parents. Evaluation still receives the original complete map. Use
identity-safe O(n) equality or ordered private IDs, not nested linear scans.
Temporary counters may measure hits/misses/entries copied but must not ship.

### Task 1: Memoize equivalent proof environments

**Files:**
- Modify `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofV1.kt`.
- Create `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7ProofContextSurfacePixelTest.kt`.
- Reuse existing W5d coordinate, W5f filter and W5g Noise public tests unchanged.

**Interfaces:** `ColorRoundedGraphProofV1.prove(...)` and `evaluate(Scalar, Map<Scalar, ColorBoundsV1>)` retain their signatures. New cache key/entry helpers are private implementation details, local to the proof or file. No authority or shader graph API changes.

- [x] Add a public regression `twentyCoordinatePairsPreserveSweepPixels`: for each TileMode and **hard paint**, render a 17x1 Surface with a full-circle SweepGradient centered at (0,0), two opaque red stops at 0/1, wrapped in 20 repetitions of WithLocalMatrix(CoordClamp(shader, RectF32(0,0,17,1)), identity matrix). Draw a full hard Rect. Assert all 17 literal RGBA pixels are (255,0,0,255), native Render/Readback evidence, and a second render of the same Surface with the same output. Add @AfterAll disposal on the test thread like the existing W7 tests. No private counters, reflection or source-text assertions. The initial hard/AA witness exposed an AA numeric refusal even without wrappers; this lot does not add an admission contract unsupported by the existing renderer. Leave the historical mixed W5d test and its assertions unchanged; document its remaining refusal.
- [x] Run this new test RED under `/private/tmp/kanvas-w7-proof-timeout.gradle` (60 s task timeout) before production edits; retain logs. Confirm the worker is stopped before another GPU run.
- [x] Add `deepCoordinateOrderKeepsDistinctColors`: on a 4x1 Surface use a linear gradient (0,0) to (8,0) with stops (0 red, .5 red, .5 blue, 1 blue), CLAMP. Compare WithLocalMatrix(CoordClamp(leaf, [0,0,3,1]), translation(-2,0)) against CoordClamp(WithLocalMatrix(leaf, translation(-2,0)), [0,0,3,1]). Wrap each in 20 identity matrix/clamp pairs with [0,0,4,1], draw a hard full Rect. The first produces red/red/red/red; the second red/red/blue/blue (literal RGBA, no implementation-derived oracle). Assert native evidence and repeat the same Surface render. Execute before the second production attempt and record its actual result. This is an adjacent semantic non-regression witness, not the CPU-stall RED: it already passed under the first full-context candidate, and no pre-cache RED is claimed. The stalled Sweep witness supplies the causal RED.
- [x] Replace only scalar-cache environment keys using the conservative dependency projection described above. Compare entries by Scalar identity and exact bounds, with identity-safe non-quadratic hashing/equality, and include active Noise region identity and octave. Snapshot/cache immutable keys as needed; avoid recursive Scalar data-class equals/hashCode. Retain every original condition for evaluation, every numerical branch and every relevant materialization constraint in the key. Cache equivalent results only within one prove invocation. Leave noiseResults unchanged. First evaluation supplies its branch facts; equivalent hits must not add duplicate work or erase facts already recorded. Opaque vocabulary and parents use the full-context fallback; do not infer dependencies through opaque regions.
- [x] Run the new public witness and the original stalled test with the same 60 s bound. If the original now reaches an unrelated assertion failure, record it unchanged instead of changing thresholds/budgets to pass. If the new witness still times out, stop the implementation attempt and report the new stack for diagnosis.
- [x] Run public coordinate ordering/projective/refusal tests (including W5d.projectiveLocalMatrixRendersBoundedPixelsAndMasksWZero), W5eImageFamiliesSurfacePixelTest.imageNineSharedBoundarySelectionDoesNotDiscardAfterCancellation, plus Noise and color-filter regressions; main will run the wider suites and corpus after the production commit. Report every observed failure, including pre-existing/uncertain ones. Do not claim a full green suite from the focused selection.
- [x] Self-review, commit owned code/tests, write the full report with commands, RED/GREEN timings and concerns. Independent task review verifies spec and quality; fix confirmed findings.

### Task 2: Verify and publish the bounded lot

**Owner:** Controller validation and documentation, no second implementation agent.

- [x] Run the full Kanvas suite with a bounded task timeout, plus math geometry. Classify all failures or any new remaining stall; do not widen this fix automatically to unrelated behavior.
- [x] Replay the fixed 631-case Skia corpus at the committed renderer SHA, 30 s per case. Keep all outcomes and resume after recorded timeouts. Compare identities, references, outcomes and RGBA hashes with `dash-5f971f750.json`.
- [x] Record checkpoint, public test totals and open defects in pilotage/status. Obtain an independent whole-branch review and address confirmed findings.
- [x] Push and create a draft PR stacked on #2413, attach it to the chat. Do not merge or call the overall renderer complete while full validation is red.

## Execution evidence

Production commit: `b256b3d68c753bec5dd4a19620583a0634a5b48f`.
Published as draft [#2414](https://github.com/ygdrasil-io/kanvas/pull/2414),
stacked on #2413 and attached to the chat. No merge.
The task review and scoped evidence re-review by Sol are approved.
The final independent whole-branch Sol review finds no confirmed defect
and approves draft publication, explicitly not merge readiness.
The two W7 witnesses and 18 adjacent tests pass (Gradle 0); the historical
Sweep completes in 1.756 s with its original numeric refusal, not success.
The identical zero-wrapper AA RRect probe refuses on both original and
projected cache; earlier mismatched Rect/RRect controls were retracted.

The full Kanvas attempt reaches its global 240 s bound after 779 observed
tests: 728 pass, 50 fail, one interrupted. All 646 prior observed passes
remain green; ten newly reached failures lack individual baseline evidence.
Math geometry: 476 pass. Full validation remains incomplete/red.
The temporary Gradle init scripts apply `tasks.withType(Test).configureEach`
inside `allprojects`, setting `timeout = java.time.Duration.ofSeconds(60)`
for focused runs and `240` for the full attempt; they change no test oracle.

Corpus: 631 fixed identities, 443 eligible, 165 rendered (+1), 143 compared,
26 at ≥99% pixels ±2/channel, one remaining timeout. All 164 prior RGBA
hashes remain identical. The new ninepatch render takes 26.168 s and has
limited headroom; lattice2 reaches an authority refusal in 0.527 s.
See [pilotage](https://github.com/ygdrasil-io/kanvas/blob/36350563f48485598009d61a1707f7cff0ff7e94/refactor/waves/W07-gm-convergence/pilotage.md#lot-cache-de-preuve-cpu--29-septembre-2026)
and [snapshot](https://github.com/ygdrasil-io/kanvas/blob/36350563f48485598009d61a1707f7cff0ff7e94/refactor/waves/W07-gm-convergence/proof-b256b3d68.json) for the complete denominator and caveats.

```sh
rtk proxy ./gradlew :integration-tests:skia:measureSkiaParity --offline --console=plain \
  -Pgm.rendererCommit=b256b3d68c753bec5dd4a19620583a0634a5b48f \
  -Pgm.parityOutput=/private/tmp/kanvas-w7-proof-parity.suk2dP -Pgm.parityTimeout=30
# After persisted timeout index 607, same command with -Pgm.parityFrom=608.
rtk proxy node refactor/waves/W07-gm-convergence/summarize-parity.mjs \
  /private/tmp/kanvas-w7-proof-parity.suk2dP \
  refactor/waves/W07-gm-convergence/proof-b256b3d68.json
```

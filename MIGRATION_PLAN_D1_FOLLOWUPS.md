# Migration plan — D1 follow-ups (PathOps polish + closure)

> **Status** : 🔄 **handoff document** — captures every D1-side
> chantier remaining after the D1.4 MVP / extended / debug-pass /
> pixel-parity slices shipped (PRs #261, #264, #270, #272, #274 —
> all merged on `master` as of 2026-05-09).
>
> Use this doc to **resume work on another machine** without
> re-deriving state. Each section lists the actionable items, the
> files involved, the exact branch / PR sequence, and the success
> criteria.

## Table des matières

1. [Current snapshot](#current-snapshot)
2. [Open backlog](#open-backlog)
   - [D1.4.a — Pixel-divergent fixture debug pass](#d14a--pixel-divergent-fixture-debug-pass)
   - [D1.4.b — `cubicOp35d` RETURNED_NULL debug](#d14b--cubicop35d-returned_null-debug)
   - [D1.4.c — Extractor coverage 74 % → ~85 %](#d14c--extractor-coverage-74--85)
   - [I3.4 — `SkRegion::getBoundaryPath` port](#i34--skregiongetboundarypath-port)
   - [D1.2 finition — single residual TODO](#d12-finition--single-residual-todo)
   - [D1.3 closure — formal plan update](#d13-closure--formal-plan-update)
3. [Out of scope here](#out-of-scope-here)
4. [Resume workflow on another machine](#resume-workflow-on-another-machine)

---

## Current snapshot

**As of 2026-05-10**, post-D1.4.a `testRect1_u` debug pass :

| Metric | Value |
|---|---|
| Suite total | **3214 / 3214 verts** |
| `PathOpsRegressionRunner` survival | **334 / 335 = 99.7 %** (floor 99 %) |
| `PathOpsRegressionRunner` pixel parity | **321 / 334 = 96.1 %** (floor 95 %) |
| Upstream fixture coverage | **335 / 451 = 74 %** (~92 % of Op-only subset) |
| Engine crashes | **0** (no `THREW`, no `NON_FINITE`) |

### Files shipped under D1.4

- `kanvas-skia/tools/extract_pathops_fixtures.py` (~530 LOC) — Python extractor
- `kanvas-skia/src/test/resources/pathops/op_fixtures.json` (~22 300 lines) — JSON dump
- `kanvas-skia/src/test/kotlin/org/skia/pathops/PathOpsRegressionRunner.kt` (~330 LOC) — JUnit `@ParameterizedTest` runner
- `kanvas-skia/src/test/kotlin/org/skia/pathops/PathOpsPixelOracle.kt` (~190 LOC) — pixel-set-op oracle
- `kanvas-skia/build.gradle.kts` — added `junit-jupiter-params` + `jackson-databind:2.16.1`
- `kanvas-skia/src/main/kotlin/org/skia/pathops/SkPathOps.kt` — 1-line removal (stale empty-result guard, h.10)

### PRs landed (mine, in order)

| PR | Title | Status |
|---|---|---|
| #258 | Plan refresh — snapshot + sequencing post C1.7 / C2-C4 / D1.2.h.9.2 | merged |
| #259 | Plan : add D1.4 — PathOps regression harvest (backlog) | merged |
| #261 | Phase D1.4 — PathOps regression harvest (MVP : 303 fixtures, 96.7 % survival) | merged |
| #264 | Phase D1.4 (follow-up) — Extractor coverage 67 % → 74 % (335 / 451) | merged |
| #270 | Phase D1.2.h.10 — Remove stale empty-result null-guard (recovers 11 fixtures) | merged |
| #272 | PathOpsRegression : trim debug prints from h.10 down to one liner | merged |
| #274 | Phase D1.4 (pixel parity) — Rasteriser-set-op oracle, 95.8 % match | merged |

---

## Open backlog

### D1.4.a — Pixel-divergent fixture debug pass

**13 fixtures fail pixel parity** (engine produces a finite SkPath
that rasterises to a different shape than the oracle). Each is a
candidate for an algorithmic debug pass that bumps the floor 95 %
→ 99 %+ monotonically.

**Names** (extracted via a one-shot probe at handoff time, 2026-05-10) :

```
cubicOp25i      cubicOp32d      cubicOp33i      cubicOp48d
cubicOp61d      cubicOp63d      cubicOp95u      loop3
loops23i        loops26i        loops33i        loops47i
loops63i
```

**Recently shipped** : `testRect1_u` (the original 14th entry, the
sole rect-only fixture in the list) was fixed by routing the
empty-input fast path through `Simplify(work)` instead of bare
fillType remap (`SkPathOps.kt:155-181`). The previous fast path
hand-waved away the `Simplify` step on the false hypothesis that
`work` was always already-simple ; in fact `work = pathA` for
`Op(A, ∅, kUnion)` even when A has overlapping winding sub-contours,
and skipping `Simplify` left the new even-odd fill type to render
those overlaps as holes. Floor bumped 90 % → 95 %.

**Recipe** (mirrors D1.2.h.7 / h.8 / h.10) :

1. Pick one fixture (start with the simplest geometry — likely
   `testRect1_u` since it's just rects intersecting).
2. Locate it in upstream :
   ```bash
   grep -B 1 -A 12 "^static void <name>\b" \
     /Users/chaos/workspace/kanvas-forge/skia-main/tests/PathOpsOpTest.cpp
   ```
3. Replicate as a standalone `@Test` in
   `kanvas-skia/src/test/kotlin/org/skia/pathops/<Name>DebugTest.kt`
   (pattern: build A and B via `SkPathBuilder`, call `SkPathOps.Op`,
   print the result + the oracle's expected, diff).
4. Trace `SkPathOps.Op` step-by-step to find the divergence point :
   most likely candidates are
   [`SkPathOpsCommon`](kanvas-skia/src/main/kotlin/org/skia/pathops/internal/SkPathOpsCommon.kt)
   (winding propagation),
   [`SkOpAngle`](kanvas-skia/src/main/kotlin/org/skia/pathops/internal/SkOpAngle.kt)
   (sort order), or
   [`SkPathWriter`](kanvas-skia/src/main/kotlin/org/skia/pathops/internal/SkPathWriter.kt)
   (verb emission).
5. Fix the bug. Each fix typically unblocks 1-3 fixtures (similar
   geometric patterns).
6. Re-run :
   ```bash
   ./gradlew :kanvas-skia:test \
     --tests "org.skia.pathops.PathOpsRegressionRunner.pathops Op pixel-parity rate stays at-or-above the floor" \
     --info | grep "PathOpsRegression\]"
   ```
7. Bump the floor in `PathOpsRegressionRunner.kt`'s
   `INITIAL_PIXEL_FLOOR` constant. Update the calibration history
   kdoc.
8. Delete the standalone debug test (the regression is now caught
   by the parametrised harness).

**Effort** : ~1-3 days per fixture × ~5-8 batches (some fixtures
share root cause). Estimated **~2-4 weeks total** to push pixel
parity from 95.8 % to 99 %+.

**Branch naming** : `claude/d1.2.h.11.<topic>` (h.10 was the
previous engine fix).

---

### D1.4.b — `cubicOp35d` RETURNED_NULL debug

**1 fixture** : the engine returns `null` instead of a finite path.

**Source** :

```cpp
static void cubicOp35d(skiatest::Reporter* reporter, const char* filename) {
    SkPathBuilder path, pathB;
    path.setFillType(SkPathFillType::kWinding);
    path.moveTo(0,1);
    path.cubicTo(1,5, 2,1, 4,0);
    path.close();
    pathB.setFillType(SkPathFillType::kWinding);
    pathB.moveTo(1,2);
    pathB.cubicTo(0,4, 1,0, 5,1);
    pathB.close();
    testPathOp(reporter, path.detach(), pathB.detach(), kDifference_SkPathOp, filename);
}
```

**Hypothesis** : two cubics crossing at sub-pixel intersection
where our `SkTSect` machinery misses the t-intersect. Investigate
[`SkPathOpsCubicCubicIntersection.kt`](kanvas-skia/src/main/kotlin/org/skia/pathops/internal/SkPathOpsCubicCubicIntersection.kt)
or wherever the cubic-vs-cubic dispatch lives ; trace the
intersection roots vs upstream's known-good values.

**Effort** : ~1-3 days. Possibly trivial (a missing root) or hard
(a precision bug in `SkClosestSect` — see D1.1.e.2.c.4).

When fixed, survival jumps to **335 / 335 = 100 %** ; bump survival
floor `0.99` → `1.00` (any future regression then blocks the build).

**Branch naming** : `claude/d1.2.h.11-cubicop35d` (or merge into
the broader pixel-divergent debug branch above).

---

### D1.4.c — Extractor coverage 74 % → ~85 %

**~30 fixtures** are still skipped by the Python extractor because
they use one of these constructs :

| Construct | Sample | Approx count |
|---|---|---:|
| `SkPoint pts[]` array literals | `SkPoint pts[] = { {5,6}, {4,6}, {3,0}, {2,1} };` | 2 |
| `CubicPathToQuads(pathB.detach())` helper | `qPathB = CubicPathToQuads(pathB.detach());` | 5 |
| Named `SkScalar` constants | `SkScalar xA = 0.65f; ... path.moveTo(xA, ...)` | 1 |
| Embedded SVG path strings | `const char str[] = "M31.35 ..."` (single fixture, very long) | 1 |
| `CubicPts cubic1 = {{{...}}};` array | `CubicPts cubic1 = {{{0,1}, {1,5}, …}};` | 1 |
| Non-Op test harness (`testSimplify`, `testPathOpFuzz`, `testPathOpFail`, `testPathOpCheck` w/ false) | various | 86 (out of scope — different harness) |

**Strategy per construct** :

- **Named constants** : add a top-of-fixture pass that collects
  `SkScalar <name> = <expr>;` declarations into a substitution
  dict, then replace `<name>` tokens in subsequent `path.moveTo`
  / `cubicTo` calls before the per-line regex matching.
- **`SkPoint pts[]`** : add a regex that matches the `{ {x,y}, … }`
  initialiser and constructs a parallel point dict ; then handle
  `path.addPoly(pts, ...)` / `path.moveTo(pts[0].fX, pts[0].fY)`
  via index resolution.
- **`CubicPathToQuads`** : helper that takes a path's cubic verbs
  and converts each cubic to ≤ 4 quad approximations. Skip
  porting ; document as "out of scope (helper-only test variant
  of an already-covered cubic fixture)".
- **SVG path string** : would require porting `SkParsePath`'s
  `FromSVGString`. **Already shipped** at D1.2.h.9.2 — could be
  used in the extractor. ~30 LOC harness change to recognise the
  pattern and call the parser at extract-time. Marginal value.
- **Non-Op test harness** : leave skipped — they'd need a parallel
  Simplify / fuzz / fail-expectation harness, outside of D1.4 scope.

**Effort** : ~3-5 days for named constants + `SkPoint pts[]` (~10
fixtures recovered, getting to ~78 %). Adding SVG path strings
gets ~1 more. Diminishing returns past 80 %.

**Files** :
- `kanvas-skia/tools/extract_pathops_fixtures.py` — extend
- `kanvas-skia/src/test/resources/pathops/op_fixtures.json` —
  regenerate via
  `python3 kanvas-skia/tools/extract_pathops_fixtures.py
  /Users/chaos/workspace/kanvas-forge/skia-main/tests/PathOpsOpTest.cpp
  > kanvas-skia/src/test/resources/pathops/op_fixtures.json`

**Priority** : **low**. Each percentage of coverage past 75 %
adds < 5 fixtures and exercises the same algorithmic patterns we
already test. The 3 PathOps GMs already ported cover the geometric
diversity well.

---

### I3.4 — `SkRegion::getBoundaryPath` port

**Why this isn't shipped yet** : I3 (SkRegion / SkAAClip /
SkRasterClip) was scoped around the **clip pipeline** — `setPath`
+ `op` + `contains` + `Iterator` are enough to replace the Phase
7q `clipMask` alpha-bitmap. `getBoundaryPath` (region → path
conversion) wasn't on that critical path and was implicitly
dropped from I3 scope.

**Why we want it now** : `D1.4 PathOps pixel parity` would benefit
from a `SkRegion`-based oracle (mirrors upstream's `comparePaths`
verbatim) instead of the rasteriser-set-op approximation we ship
in [`PathOpsPixelOracle.kt`](kanvas-skia/src/test/kotlin/org/skia/pathops/PathOpsPixelOracle.kt).
The current oracle works (95.8 % parity baseline), but :

1. It's **not** identical to upstream's verification path — a
   SkRegion oracle would catch shape-correctness bugs at the
   verb level before rasterisation.
2. `SkRegion::getBoundaryPath` is a public API surface that
   external clients of `kanvas-skia` may rely on (any code that
   flattens a clip region to a path, e.g. for SVG export).
3. Its cost is small : ~250 LOC C++ upstream
   ([`src/core/SkRegion_path.cpp`](https://github.com/google/skia/blob/main/src/core/SkRegion_path.cpp))
   → estimated **~200 main + ~80 test = ~280 LOC** Kotlin.

**Algorithm** (from upstream) :
1. Walk the region's run-encoded scan lines.
2. At each `{minY, maxY}` band, emit horizontal edges where
   coverage transitions from 0 → 1 (start of run) or 1 → 0 (end
   of run).
3. Connect horizontal edges to their vertical counterparts at
   band boundaries to form closed contours.
4. Emit each contour as `moveTo` + `lineTo`s + `close`.

**Files** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkRegion.kt`
  — add `public fun getBoundaryPath(): SkPath`.
- `kanvas-skia/src/test/kotlin/org/skia/foundation/SkRegionBoundaryPathTest.kt`
  (new) — 8-12 tests covering rect / multi-rect / complex /
  empty / single-pixel / hole-bearing regions.

**Follow-up** : once shipped, refactor
[`PathOpsPixelOracle.kt`](kanvas-skia/src/test/kotlin/org/skia/pathops/PathOpsPixelOracle.kt)
to use `SkRegion.setPath(A) → op(rgnB, regionOp) → getBoundaryPath()`
as the oracle (or keep both for cross-verification).

**Effort** : ~1-2 days port + 1 day refactor pixel oracle.
**Priority** : medium — improves DM iso-fidelity claim for
pathops, closes a public API gap, but the pixel oracle already
catches the bugs.

**Branch naming** : `claude/i3.4-getboundarypath`.

---

### D1.2 finition — single residual TODO

There's exactly **one** non-upstream-mirror TODO left in the
pathops port :

```
kanvas-skia/src/main/kotlin/org/skia/pathops/internal/SkOpSegment.kt:1833
  // TODO (D1.2.g) : globalState().coincidence().release(this).
```

This is the only "real" TODO not duplicated in upstream's source
(every other `TODO`/`FIXME` in the pathops directory is a
verbatim port of an upstream source comment).

**Effort** : ~1-2 hours. Read the upstream `release(this)` impl
in `src/pathops/SkOpCoincidence.cpp`, port the missing call site,
re-run the harness to confirm no regression.

**Priority** : low — non-blocking ; the engine works correctly
without it (D1.4 99.7 % survival proves it). But it's the last
visible loose thread.

**Branch naming** : `claude/d1.2-finition-coincidence-release`.

---

### D1.3 closure — formal plan update

The plan still lists `D1.3 — Top-level entry points` as
**📋 pending**. Functionally, **D1.3 is already delivered** :
`SkPathOps.Op` / `Simplify` / `AsWinding` / `TightBounds` are all
working end-to-end (proven by D1.4's 99.7 % survival rate on 335
upstream fixtures + 3 ported GMs at 67 %-94.5 % similarity).

The "still pending" status is just stale plan housekeeping.

**Action** :
1. Edit the plan matrix row for D1.3 from `📋 pending` to `✅ shipped`
   with note "delivered piecewise via D1.2.h.5.4 (Op end-to-end)
   + h.6.* (Simplify + AsWinding) + h.7 / h.8 / h.10 (debug passes)".
2. Update the body section to match.
3. Update the snapshot blurb at the top.
4. Optionally, add a tiny smoke test
   `SkPathOpsEntryPointInvariantsTest.kt` that pins the public API
   contract (each entry point accepts empty / rect / cubic inputs
   without crashing — already covered by D1.4, but a focused 4-test
   file makes the public surface doc-discoverable).

**Effort** : ~1 hour plan + ~1 hour optional smoke test = ~half a
day total.

**Branch naming** : `claude/d1.3-closure-plan-update`.

---

## Out of scope here

These are tracked for cross-reference only — do **not** work on
them in this fork :

| Item | Status | Where |
|---|---|---|
| **D2** SkRuntimeEffect (D2.0 → D2.6) | active on another fork | follow `MIGRATION_PLAN_D2_RUNTIME_EFFECT.md` ; D2.0/2.1/2.2/2.3 already merged on master, D2.4.a in progress (PR #275 visible on GitHub) |
| **GM port batches** (GM-ports D2-pre series) | active on another fork | new GM ports landing as `27f12d1`, `f37aaed`, `28f42ad` on master |
| **C1 image filters** | ✅ all 7 slices shipped | `MIGRATION_PLAN_C1_IMAGE_FILTERS.md` ; nothing left |
| **B1 PDF**, **drawShadow** | ❌ formally descoped | see main plan for rationale |

---

## Resume workflow on another machine

### Initial sync

```bash
# 1. Clone or pull the repo.
cd <kanvas-root>
git fetch origin master

# 2. Verify tooling.
./gradlew :kanvas-skia:test \
  --tests "org.skia.pathops.PathOpsRegressionRunner" --info \
  2>&1 | grep "PathOpsRegression\]"
# Expected output (post-handoff baseline) :
# [PathOpsRegression] 334 / 335 survived (99,70 %) ; floor = 99,00 %
# [PathOpsRegression] 320 / 334 pixel-match (95,81 %) ; floor = 90,00 %

# 3. Confirm the upstream Skia tree path used by the extractor.
ls /Users/chaos/workspace/kanvas-forge/skia-main/tests/PathOpsOpTest.cpp
#                ↑  adjust to the local clone path on the new machine
```

### Pick a backlog item

Each section above has a **Branch naming** line. Stick to that
convention so a future handoff doc can locate work-in-progress
without grep-soup.

### Per-slice flow

```bash
git checkout -b claude/<slice-id> origin/master

# Edit, test :
./gradlew :kanvas-skia:test --tests "org.skia.pathops.<SpecificTest>" --info

# Full suite check before commit :
./gradlew :kanvas-skia:test 2>&1 | tail -5

# Commit + push + PR :
git add <files>
git commit -m "$(cat <<'EOF'
Phase <slice-id> — <one-line summary>

<paragraph explaining what + why>

Suite stays green at NNNN / NNNN.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
git push -u origin claude/<slice-id>
gh pr create --title "..." --body "..."
```

### Updating this doc

If you finish a backlog item, **edit this doc** in the same PR :
- Move the section under a new "## Recently shipped" heading at
  the bottom.
- Update the [Current snapshot](#current-snapshot) table with the
  new measurement / floor.
- Add the PR to the "PRs landed" table.

That keeps the handoff doc self-contained and current — anyone
picking it up on yet another machine sees exactly where you stopped.

---

🤖 Initially generated with [Claude Code](https://claude.com/claude-code)
on 2026-05-09 ; maintained by hand thereafter.

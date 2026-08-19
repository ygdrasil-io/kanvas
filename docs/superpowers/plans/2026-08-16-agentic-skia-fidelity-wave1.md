# Agentic Skia Fidelity Wave 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fresh Skia fidelity classification, select at most one demonstrated causal cohort, and close it with a reviewed pixel-proven fix or an explicit `blocked` result.

**Architecture:** The evidence scanner seals a new baseline and owns all generated evidence. Read-only discovery agents classify current rows by causal subsystem. The orchestrator selects one cohort only after recomputing current denominators, then dispatches one source-isolated fix agent and one independent reviewer. A post-fix evidence run decides `approved` versus `blocked` without modifying Wave 0 artifacts.

**Tech Stack:** Kotlin/JVM, Gradle 9.2, JUnit 5, WebGPU via wgpu4k/llvmpipe, Python 3 standard library, XML/JSON/PNG evidence, Xvfb `DISPLAY=:99`.

---

## Operating Boundary

Work only in the existing clean `codex/skia-fidelity-wave1` worktree. Do not
create or reuse a Wave 0 worktree. The starting source commit is the merged
Wave 0 commit already on this branch. The design is committed at
`204665ea9` and the earlier design commit is `2269b2269`.

The following paths are read-only historical context during this plan:

- `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-review.md`;
- all dated Wave 0 input, delta, dashboard, PNG, and score artifacts;
- `f2e68b895` and any older FP-13 reports.

Do not lower thresholds, modify JUnit assertions, modify reference PNGs, raise
the 1 GiB budget, or hide a refusal. CPU remains the oracle; WebGPU/WGSL
remains the GPU backend. Any ambiguous `wgsl4k` parser, IR, reflection, or
generator behavior stops Kanvas implementation and becomes a minimized
`wgsl4k` issue.

## File Map

### Evidence contract

- Create `scripts/gm/reconcile_skia_fidelity_wave1.py` to parse the fresh Skia,
  dashboard, SVG, CPU/GPU, score, command, environment, and evidence-index
  inputs and emit the Wave 1 manifest.
- Create `scripts/gm/test_reconcile_skia_fidelity_wave1.py` with synthetic XML,
  JSON, score, metadata, and evidence-index fixtures. It must never write
  generated PNGs or modify repository score files.

### Evidence outputs

The evidence scanner owns these new paths and is the only process allowed to
write generated scores, dashboards, PNGs, JUnit-derived inventories, and
manifests:

- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.json`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.md`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-cohort-classification.json`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-cohort-classification.md`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.json`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.md`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix-manifest.json`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix-manifest.md`;
- `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/`.

The scanner copies build outputs into the dated input/output directories. It
does not overwrite any existing Wave 0 path.

### Planning and implementation

- Create `docs/superpowers/plans/2026-08-16-agentic-skia-fidelity-wave1-selected-cohort.md`
  only after the decision record names one cohort. Because
  `docs/superpowers/` is ignored, add this plan with `git add -f`.
- Production and focused-test paths are not guessed in this parent plan. They
  are the exact `sourceFiles` and `testFiles` emitted by the selected-cohort
  decision and must be copied verbatim into the selected-cohort plan before a
  fix agent edits anything.

## Task 1: Define The Wave 1 Manifest Contract

**Files:**
- Create: `scripts/gm/test_reconcile_skia_fidelity_wave1.py`
- Read-only reference: `scripts/gm/reconcile_skia_fidelity_wave0.py`
- Read-only reference: `scripts/gm/test_reconcile_skia_fidelity_wave0.py`

- [ ] **Step 1: Add baseline status and population-policy tests**

Create temporary fixtures for a current runner XML, dashboard JSON, SVG XML,
scores properties, historical FP-13 XML, command JSON, environment JSON, and
evidence index. Assert that the manifest builder returns:

```python
self.assertEqual(manifest["status"], "classification")
self.assertTrue(manifest["populationPolicy"]["includeBlocking"])
self.assertFalse(manifest["populationPolicy"]["wave0DirectlyComparable"])
self.assertEqual(manifest["populationPolicy"]["wave0Population"], 615)
self.assertFalse(manifest["policy"]["scoresDirectlyEdited"])
self.assertFalse(manifest["policy"]["globalThresholdWeakened"])
self.assertEqual(manifest["policy"]["readinessDelta"], 0.0)
```

The fixture must identify the runner setting as
`-Dkanvas.gm.includeBlocking=true` and the dashboard setting as
`-Pgm.includeBlocking=true`.

- [ ] **Step 2: Add dashboard, score, and hash tests**

Use a dashboard fixture with one passing row, one below-threshold row, one
missing reference row, and one size-mismatch row. Use a score fixture with a
numeric `modecolorfilters` value. Create two score snapshots with identical
bytes and a separate command/environment/evidence-index file. Assert that the
manifest contains:

```python
self.assertEqual(manifest["dashboard"]["outputDir"], str(dashboard_dir))
self.assertEqual(manifest["dashboard"]["dataPath"], str(dashboard_json))
self.assertEqual(manifest["scoreFile"]["beforeSha256"], manifest["scoreFile"]["afterSha256"])
self.assertFalse(manifest["scoreFile"]["directEditDetected"])
self.assertTrue(manifest["scoreFile"]["restored"])
self.assertEqual(manifest["rows"]["skia"][0]["referenceKind"], "skia-upstream")
self.assertEqual(manifest["current"]["dashboard"]["rows"], 4)
```

The test must also assert that every listed input path has a 64-character
SHA-256 value and that a missing dashboard output fails the validation path.

- [ ] **Step 3: Add row-classification and refusal tests**

Cover these exact classes in synthetic data:

- `skia-upstream` with a valid reference and matching dimensions;
- `test-oracle`;
- `cpu-oracle`;
- missing reference;
- invalid dimensions/size mismatch;
- similarity failure;
- terminal refusal with a stable code;
- unclassified renderer error;
- `TestAbortedException`.

Assert that `observedComparableRows` includes only valid `skia-upstream` rows,
that CPU/test oracle rows remain separate, and that `--check` fails only for
missing required inputs, malformed evidence, unclassified errors, score-file
integrity violations, and missing dashboard output. Classified terminal
refusals remain visible and are not silently converted into skips.

- [ ] **Step 4: Add before/after and escalation-policy tests**

Create a before/after evidence-index fixture with one row whose similarity
improves and one row whose route executes without pixel improvement. Assert:

```python
self.assertEqual(manifest["supportedRowsAfter"], 1)
self.assertEqual(manifest["routeOnlyRows"], 1)
self.assertFalse(manifest["routeOnlyRowsPromoted"])
self.assertEqual(manifest["escalation"]["maxFailedHypotheses"], 3)
```

The manifest builder must retain a failed hypothesis and emit `blocked` when
the selected fix has no pixel improvement under unchanged policy.

- [ ] **Step 5: Run the new tests and confirm the red state**

Run:

```bash
python3 -m unittest discover -s scripts/gm -p 'test_reconcile_skia_fidelity_wave1.py' -v
```

Expected result: FAIL because `reconcile_skia_fidelity_wave1.py` and its
manifest functions do not yet exist. Do not modify the Wave 0 reconciliation
script to satisfy this failure.

## Task 2: Implement The Read-Only Wave 1 Reconciler

**Files:**
- Create: `scripts/gm/reconcile_skia_fidelity_wave1.py`
- Test: `scripts/gm/test_reconcile_skia_fidelity_wave1.py`

- [ ] **Step 1: Implement input parsers without output mutation**

Use only the standard-library modules already used by the Wave 0 reconciler:
`argparse`, `hashlib`, `json`, `pathlib`, `re`, and
`xml.etree.ElementTree`. Implement these public functions:

```python
def parse_junit(path: pathlib.Path, suite: str, expected_codes: set[str]) -> dict:
    """
    Return suite, declared counts, row details, terminal/refusal/error counts,
    and unclassified count without writing to path.
    """

def parse_dashboard(path: pathlib.Path) -> dict:
    """Return dashboard summary, rows, provenance fields, and data path."""

def load_scores(path: pathlib.Path) -> dict[str, float]:
    """Parse numeric Java properties and preserve the source bytes untouched."""

def hash_files(paths: dict[str, pathlib.Path]) -> dict[str, dict[str, str]]:
    """Return absolute path and SHA-256 for each existing input."""

def build_manifest(inputs: dict, source_commit: str, status: str) -> dict:
    """Build the complete classification or post-fix manifest."""

def render_markdown(manifest: dict) -> str:
    """Render the machine-readable manifest without adding hand edits."""
```

The JUnit parser must retain testcase name, class, outcome, message, failure
type, failure code, route classification, terminal flag, refusal flag,
reference classification, and image comparison classification. It must not use
the old Wave 0 expected-unsupported policy for Skia rows without an explicit
current evidence code.

- [ ] **Step 2: Implement the population and score integrity fields**

`build_manifest` must emit:

```json
{
  "status": "classification",
  "populationPolicy": {
    "includeBlocking": true,
    "runnerProperty": "-Dkanvas.gm.includeBlocking=true",
    "dashboardProperty": "-Pgm.includeBlocking=true",
    "wave0Population": 615,
    "wave0DirectlyComparable": false,
    "comparisonNote": "population-shifted"
  },
  "policy": {
    "globalThresholdWeakened": false,
    "assertionsWeakened": false,
    "referencesModified": false,
    "scoresDirectlyEdited": false,
    "memoryBudgetChanged": false,
    "readinessDelta": 0.0
  },
  "escalation": {"maxFailedHypotheses": 3}
}
```

The exact manifest may contain additional fields, but these fields and values
are required for the baseline. The score-file record must include before and
after hashes, runner-side-effect observation, direct-edit detection, and
restoration status. A changed score content or direct edit fails `--check`.

- [ ] **Step 3: Implement the evidence-index and comparable-row counters**

The evidence index is JSON with one entry per row. Each entry records GM name,
reference kind/path/hash, CPU path/hash, GPU path/hash, diff path/hash, stats
path/hash, route diagnostics path/hash, dimensions, similarity, threshold,
failure code, and causal bucket. Compute:

- `observedComparableRows`: valid `skia-upstream` references with matching
  dimensions;
- `candidateUnlockedRows`: comparable rows with the same causal bucket,
  operation trace, route signature, and ownership boundary;
- `supportedRowsAfter`: rows with improved pixels and complete evidence;
- `routeOnlyRows`: rows whose route executes but whose pixels do not improve.

Do not include `test-oracle`, `cpu-oracle`, missing-reference, invalid-size, or
terminal-only rows in the comparable denominator. Keep them in rows and
non-claims.

- [ ] **Step 4: Implement the CLI and checks**

The CLI must accept these exact inputs:

```text
--skia-runner PATH
--dashboard-json PATH
--dashboard-dir PATH
--generated-renders PATH
--svg-xml PATH
--cpu-results PATH
--gpu-results PATH
--scores-before PATH
--scores-after PATH
--fp13-runner PATH
--commands-json PATH
--environment-json PATH
--evidence-index PATH
--source-commit SHA
--status classification|approved|blocked
--output-json PATH
--output-markdown PATH
--check
```

`--check` must reject missing inputs, output/input aliasing, malformed JUnit or
JSON, unclassified current errors, missing dashboard output, score integrity
violations, and evidence-index rows with missing required hashes. It must not
reject a classified refusal merely because it remains unsupported.

- [ ] **Step 5: Run the manifest tests and CLI contract**

Run:

```bash
python3 -m unittest discover -s scripts/gm -p 'test_reconcile_skia_fidelity_wave1.py' -v
python3 scripts/gm/reconcile_skia_fidelity_wave1.py --help
```

Expected result: all manifest tests PASS, help lists every required flag, and
the tool never modifies any fixture input.

- [ ] **Step 6: Commit the evidence contract**

```bash
git add scripts/gm/reconcile_skia_fidelity_wave1.py scripts/gm/test_reconcile_skia_fidelity_wave1.py
git commit -m "test: add Skia fidelity Wave 1 manifest contract"
```

## Task 3: Seal The Fresh Wave 1 Baseline

**Files:**
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/`
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.json`
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.md`
- Do not modify: any `2026-08-14-*` Wave 0 artifact

The evidence scanner owns this task. No discovery or fix agent runs before the
baseline manifest is sealed with status `classification`.

- [ ] **Step 1: Record repository and runtime preconditions**

Run and save the output under the dated inputs directory:

```bash
git status --short --branch
git rev-parse HEAD
git merge-base --is-ancestor e97d25a32008adfce773926aca84b9c3ea3f5781 HEAD
git submodule status
java -version
./gradlew --version
gh pr view 2064 --json number,state,mergedAt,baseRefName,headRefName
```

The manifest records that PR `#2064` is merged, the source commit, the clean
starting state, and that uninitialized Kadre/wgsl4k submodules are not used by
this headless lane.

- [ ] **Step 2: Snapshot the tracked score file before the runner**

Copy `integration-tests/skia/test-similarity-scores.properties` to the dated
input directory as the before snapshot and record its SHA-256. Do not edit the
tracked score file manually.

- [ ] **Step 3: Run the complete Skia inventory with blocking rows**

Run exactly:

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test \
  --tests "org.graphiks.kanvas.skia.SkiaGmRunner" \
  -Dkanvas.gm.includeBlocking=true \
  --no-daemon --no-parallel --console=plain
```

Copy the produced `SkiaGmRunner` JUnit XML and command output into the dated
inputs directory. Preserve all rows, including passes, skips, missing
references, size mismatches, terminal refusals, and similarity failures.

- [ ] **Step 4: Generate the matching dashboard and render population**

Run exactly:

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:generateSkiaDashboard \
  -Pgm.includeBlocking=true \
  --no-daemon --no-parallel --console=plain
```

Capture:

- `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`;
- the complete `integration-tests/skia/build/reports/skia-gm-dashboard/` tree;
- `integration-tests/skia/src/test/resources/generated-renders/` entries used
  by the dashboard;
- command output and exit status.

The dashboard task filters `RenderCost.BLOCKING` rows internally, so the
manifest must record the runner population and dashboard population as
separate counts even though both use the blocking-enabled invocation.

- [ ] **Step 5: Run SVG, CPU, and GPU lanes separately**

Run exactly:

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :gpu-renderer:test \
  --no-daemon --no-parallel --console=plain
```

Copy each suite's JUnit XML, console output, exit status, and module identity
into the dated input directory. Report known package-boundary, UNORM, blend,
and expected SVG refusal baselines separately; do not fold them into Skia GM
counts.

- [ ] **Step 6: Capture the score side effect and restore the tracked input**

Copy the post-run score file to the dated input directory and record its hash.
Compare bytes and timestamps against the before snapshot. Restore the tracked
score file to the before bytes using the evidence scanner's controlled cleanup,
then record `runnerSideEffectObserved`, `directEditDetected`, and `restored`.
The manifest must say `scoresDirectlyEdited=false` when no manual score edit
occurred. A content change that cannot be attributed to the runner side effect
blocks the baseline.

- [ ] **Step 7: Build and validate the classification manifest**

Create command and environment JSON files, an evidence index for all available
dashboard/reference/render/diff/stat/route paths, then run:

```bash
python3 scripts/gm/reconcile_skia_fidelity_wave1.py \
  --skia-runner reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/SkiaGmRunner.xml \
  --dashboard-json integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json \
  --dashboard-dir integration-tests/skia/build/reports/skia-gm-dashboard \
  --generated-renders integration-tests/skia/src/test/resources/generated-renders \
  --svg-xml reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/svg.xml \
  --cpu-results reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/kanvas-results \
  --gpu-results reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/gpu-renderer-results \
  --scores-before reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/scores-before.properties \
  --scores-after reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/scores-after.properties \
  --fp13-runner reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-gm-runner.xml \
  --commands-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/commands.json \
  --environment-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/environment.json \
  --evidence-index reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/evidence-index.json \
  --source-commit "$(git rev-parse HEAD)" \
  --status classification \
  --output-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.json \
  --output-markdown reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.md \
  --check
```

Expected result: status `classification`, fresh hashes, explicit population
shift from Wave 0, dashboard paths, score integrity, refusal/non-claim rows,
and no modified Wave 0 artifact.

- [ ] **Step 8: Commit only the fresh evidence and manifest**

Before staging, run:

```bash
git diff --check
git diff --name-only
```

Stage only the new dated Wave 1 paths. Never stage
`integration-tests/skia/test-similarity-scores.properties`, any Wave 0 path,
or unrelated build output.

```bash
git add reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.json \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.md
git commit -m "docs: seal Skia fidelity Wave 1 baseline"
```

## Task 4: Dispatch Disjoint Read-Only Cohort Discovery

**Files:**
- Read only: fresh Wave 1 manifest and its dated inputs;
- Read only: current source and test files named by route diagnostics;
- Write: none by discovery agents.

- [ ] **Step 1: Derive current causal buckets from the fresh manifest**

Group rows by failure code, route diagnostic, operation trace, and source
ownership. Use these historical labels only when current evidence supports
them: path-key canonicalization, image alpha/sampling, gradient material
capability, and stencil edge-fan/coverage budget. Add a new bucket when the
fresh route shows a different shared subsystem.

Do not group by GM name alone and do not reuse FP-13 counts.

- [ ] **Step 2: Capture representative evidence before dispatch**

For each non-empty current bucket, choose representatives with valid Skia
references and matching dimensions first. Run supplemental focused probes only
after the full inventory is sealed, using the existing runner's name filter and
trace diagnostics:

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test \
  --tests "org.graphiks.kanvas.skia.SkiaGmRunner" \
  -Dkanvas.gm.name="$REPRESENTATIVE_GM" \
  -Dkanvas.gm.includeBlocking=true \
  -Dkanvas.render.debugLevel=TRACE \
  --no-daemon --no-parallel --console=plain
```

Set `REPRESENTATIVE_GM` to the exact current representative selected from the
manifest. Preserve the reference, CPU output, GPU output, diff/stat output,
route diagnostics, and minimal operation trace in the dated Wave 1 evidence
directory. A missing artifact is recorded as a gap, not synthesized.

- [ ] **Step 3: Dispatch one `explore` agent per non-empty bucket**

Each agent receives a row list that is disjoint from every other agent. The
prompt must require read-only work and this return structure:

```text
cohortId
representativeGms
observedComparableRows
candidateUnlockedRows
failureCodes
routeDiagnostics
minimalOperationTrace
referencePathsAndHashes
cpuPathsAndHashes
candidateRootCause
confidence
sourceFiles
testFiles
ownershipBoundary
memoryRisk
performanceRisk
fallbackAndAdjacentRefusals
evidenceGaps
```

Agents must not edit source, tests, scores, dashboards, PNGs, manifests, or
reports. They return findings to the orchestrator only. If two agents identify
the same production file, merge their cohorts into one sequential ownership
boundary before selection.

- [ ] **Step 4: Recompute cohort counts from returned evidence**

The orchestrator cross-checks every returned count against the manifest and
evidence index. Reject a candidate count that includes CPU-only rows, missing
references, invalid sizes, route-only rows, or an old historical denominator.

## Task 5: Select One Cohort Or Close The Wave As Blocked

**Files:**
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-cohort-classification.json`
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-cohort-classification.md`
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.json`
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.md`

- [ ] **Step 1: Rank candidates with the approved lexicographic rule**

Rank by:

1. highest `candidateUnlockedRows`;
2. strongest demonstrated shared-cause confidence;
3. best reference completeness/provenance;
4. lowest CPU/GPU/memory/performance risk;
5. cleanest isolated file boundary.

Record `observedComparableRows`, `candidateUnlockedRows`, and evidence gaps
separately for every candidate. Include rejected candidates and the reason for
rejection.

- [ ] **Step 2: Apply the causal-proof gate**

Select exactly one cohort only if its representative operation traces and
route diagnostics support one shared cause and its source/test ownership is
isolatable. Do not select a cohort with only a GM-name relationship, only a
route success, only a CPU oracle, or only an old refusal count.

- [ ] **Step 3: Write the decision record**

The decision JSON must contain the following fields. This Python-style
projection shows that every value comes from the selected fresh-evidence record
or the rejected cohort list; it is not a literal fixture with fabricated zero
counts:

```python
{
  "status": "selected",
  "selectedCohortId": selected.cohortId,
  "sourceFiles": selected.sourceFiles,
  "testFiles": selected.testFiles,
  "observedComparableRows": selected.observedComparableRows,
  "candidateUnlockedRows": selected.candidateUnlockedRows,
  "rejectedCohorts": rejected,
  "referenceQuality": selected.referenceQuality,
  "memoryRisk": selected.memoryRisk,
  "performanceRisk": selected.performanceRisk,
  "adjacentRefusals": selected.adjacentRefusals,
  "evidenceGaps": selected.evidenceGaps
}
```

The values are copied from the fresh manifest and discovery responses; no
historical count is substituted. The Markdown record explains the decision in
reviewable prose.

- [ ] **Step 4: Write the blocked record when proof is insufficient**

If no cohort satisfies the gate, write both decision files with
`"status":"blocked"`, list every candidate and evidence gap, set the final
Wave 1 status to `blocked`, and stop. Do not create a selected-cohort plan or
dispatch a fix agent.

- [ ] **Step 5: Commit the classification decision**

```bash
git add reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-cohort-classification.json \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-cohort-classification.md \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.json \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.md
git commit -m "docs: classify Skia fidelity Wave 1 cohorts"
```

## Task 6: Write The Selected-Cohort Implementation Plan

**Files:**
- Create: `docs/superpowers/plans/2026-08-16-agentic-skia-fidelity-wave1-selected-cohort.md`
- Read: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-decision.json`
- Read: `.upstream/specs/geometry-coverage/README.md` when the decision names
  geometry, clipping, edge-fan, or coverage ownership.

- [ ] **Step 1: Copy the exact ownership boundary from the decision**

The selected plan must list every exact production and focused-test path from
`sourceFiles` and `testFiles`, the selected representative GM names, the
failing route code, the minimal operation trace, and the current evidence paths
and hashes. No unlisted production file may be edited by the fix agent.

- [ ] **Step 2: Specify the red test and expected current failure**

Name the exact test class/method and the command that runs it. State the
current failure/refusal code or below-threshold pixel result and the post-fix
assertion. If the cohort is geometry/coverage-related, cite the Geometry and
Coverage contracts and fallback diagnostics in the plan.

- [ ] **Step 3: Specify non-goals and evidence outputs**

The plan must explicitly preserve CPU oracle behavior, WebGPU/WGSL backend
ownership, thresholds, assertions, reference PNGs, 1 GiB budget, adjacent
refusals, and fallback diagnostics. It must name the before/after reference,
CPU, GPU, diff/stat, route, memory, and performance artifacts expected from the
evidence scanner.

- [ ] **Step 4: Commit the selected plan before production edits**

```bash
git add -f docs/superpowers/plans/2026-08-16-agentic-skia-fidelity-wave1-selected-cohort.md
git commit -m "docs: plan selected Skia fidelity cohort"
```

## Task 7: Dispatch The One Cohort Fix Agent

**Files:**
- Exact `sourceFiles` and `testFiles` from the selected decision only;
- No score, dashboard, PNG, manifest, or Wave 0 path.

- [ ] **Step 1: Create an isolated fix-agent worktree from the selected-plan commit**

The fix agent must work from the current branch after the selected plan commit.
The worktree must contain the same selected plan and fresh evidence, and it
must not contain unrelated changes from another cohort.

- [ ] **Step 2: Dispatch the `general` implementation agent**

Give the agent the selected plan, exact file boundary, red test command,
expected current failure, evidence paths, and this prohibition:

```text
Do not edit integration-tests/skia/test-similarity-scores.properties,
reference PNGs, generated renders, dashboards, manifests, thresholds,
assertions, or any source/test file outside the selected plan. Do not add a
silent fallback. Stop for a minimized wgsl4k ticket if parser/reflection/
generator behavior is ambiguous.
```

- [ ] **Step 3: Require the red test before implementation**

The agent runs the focused test before changing production code and records the
expected refusal or similarity failure. A test that is already green does not
prove the selected cause; the agent must report the mismatch and stop rather
than weakening the assertion.

- [ ] **Step 4: Implement the smallest semantic fix**

The agent changes only the selected source boundary, preserves CPU/GPU
semantics, and adds the focused regression test. Geometry/coverage work must
keep coverage separate from paint; WGSL work must remain parser-validated and
registered rather than dynamically compiling SkSL.

- [ ] **Step 5: Run targeted tests and commit separately**

The agent runs the exact selected-plan test command, the affected module test,
and one focused representative GM probe. Set `SELECTED_SOURCE_FILES` and
`SELECTED_TEST_FILES` from the decision JSON's exact arrays, record the result,
and commit:

```bash
git add "${SELECTED_SOURCE_FILES[@]}" "${SELECTED_TEST_FILES[@]}"
git commit -m "fix: close selected Skia fidelity cohort"
```

The orchestrator does not accept the agent commit until the source boundary and
generated-artifact prohibition are verified.

## Task 8: Independent Review

**Files:**
- Read: selected plan, fix-agent diff, focused test output, fresh evidence;
- Create: review result in the final Wave 1 report only.

- [ ] **Step 1: Dispatch an independent `general` reviewer**

The reviewer receives the selected plan and fix commit but does not share the
fix agent's working tree state. It checks the exact production/test boundary,
red-to-green evidence, CPU reference preservation, GPU/WGSL route, fallback
diagnostics, thresholds, assertions, references, memory budget, and performance
risk.

- [ ] **Step 2: Require explicit review decisions**

The reviewer returns `approved` only when the plan is satisfied and no
unclassified regression is introduced. The reviewer returns `blocked` when the
route improves but pixels do not, when any adjacent refusal changes silently,
when score/dashboard/reference artifacts were edited by the fix agent, or when
the source boundary is exceeded.

- [ ] **Step 3: Apply bounded escalation**

Wave 1 permits one selected implementation fix. If its focused test or pixel
evidence fails, mark the wave `blocked` and retain the failed hypothesis. Never
stack a speculative workaround. If a future authorized iteration reaches three
failed targeted hypotheses, stop and re-evaluate the architecture with the user.

## Task 9: Produce Post-Fix Pixel Evidence

**Files:**
- Create: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/`
- Modify only through evidence scanner: fresh post-fix manifest and report;
- Do not modify: Wave 0 artifacts or tracked score content.

- [ ] **Step 1: Integrate the reviewed fix serially**

Cherry-pick the approved fix commit into `codex/skia-fidelity-wave1`. Record
the new source commit and verify the worktree contains no unrelated changes.

- [ ] **Step 2: Re-run the focused red-to-green test and representative GM**

Run the selected-plan test command and the representative GM with
`-Dkanvas.render.debugLevel=TRACE`, preserving CPU/GPU/reference/diff/stat/route
artifacts under the post-fix directory. A route success without a pixel gain is
classified as `blocked`.

- [ ] **Step 3: Re-run the required suite lanes**

Run the same four suite commands and the dashboard command from Task 3 with
the post-fix source commit. Preserve command/environment/exit-status metadata
and separate known baselines from new unclassified errors.

- [ ] **Step 4: Compare before and after with unchanged policy**

Build a post-fix evidence index and compare hashes and counters. Compute:

- exact `observedComparableRows` before and after;
- exact `candidateUnlockedRows` from the selected cause;
- exact `supportedRowsAfter` with improved similarity/diff statistics;
- route-only rows that must not be promoted;
- adjacent refusal codes that remain unchanged;
- memory allocation/peak and focused elapsed-time deltas.

- [ ] **Step 5: Generate the final manifest and report**

Run the reconciler with `--status approved` only when every gate predicate is
true. Otherwise run it with `--status blocked` and retain the failed evidence:

```bash
python3 scripts/gm/reconcile_skia_fidelity_wave1.py \
  --skia-runner reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/SkiaGmRunner.xml \
  --dashboard-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/dashboard/data/gms.json \
  --dashboard-dir reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/dashboard \
  --generated-renders reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/generated-renders \
  --svg-xml reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/svg.xml \
  --cpu-results reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/kanvas-results \
  --gpu-results reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/gpu-renderer-results \
  --scores-before reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/scores-before.properties \
  --scores-after reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/scores-after.properties \
  --fp13-runner reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-gm-runner.xml \
  --commands-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/commands.json \
  --environment-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/environment.json \
  --evidence-index reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix/evidence-index.json \
  --source-commit "$(git rev-parse HEAD)" \
  --status approved \
  --output-json reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix-manifest.json \
  --output-markdown reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix-manifest.md \
  --check
```

If the status is `blocked`, change only the status and output paths while
retaining all failing evidence and explicit non-claims. Do not delete or skip
the failed row.

- [ ] **Step 6: Commit post-fix evidence only after review approval**

```bash
git add reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix-manifest.json \
  reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-post-fix-manifest.md
git commit -m "docs: record Skia fidelity Wave 1 result"
```

## Task 10: Final Gate And Handoff

- [ ] **Step 1: Verify the complete acceptance predicate**

Run:

```bash
python3 -m unittest discover -s scripts/gm -p 'test_reconcile_skia_fidelity_wave1.py' -v
git diff --check
git status --short --branch
git log --oneline -10
```

Confirm the final report names:

- source commit and evidence hashes;
- selected and rejected cohorts;
- before/after comparable-row counts;
- reference/CPU/GPU/diff/stat/route artifacts;
- adjacent refusals;
- no new unclassified errors;
- unchanged thresholds, assertions, references, 1 GiB budget, and fallback
  policy;
- memory/performance facts;
- independent review result;
- final status `approved` or `blocked`.

- [ ] **Step 2: Keep the final worktree auditable**

The final `git status` must show no modified tracked score file, no changed Wave
0 artifact, no untracked generated build output, and only the intended Wave 1
source/test/docs/evidence commits. A blocked result is a successful completion
of classification and must not be disguised as a pass or skip.

- [ ] **Step 3: Report the handoff**

Return the manifest paths, classification/decision paths, selected-cohort plan
path when applicable, fix/test commit SHAs when applicable, review result,
before/after counts, and final `approved` or `blocked` status. If blocked, name
the exact failed gate and preserve the evidence gap as the next explicit
non-claim rather than proposing an unproved fix.

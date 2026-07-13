# GM Reference Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Skia GM reference lookup source-aware while applying only the two source-verified `convex_all_line_paths.cpp` one-to-one aliases and avoiding ambiguous or approximate Kotlin ports.

**Architecture:** Keep `SkiaGm.name` as the logical registry identity and use `referenceName` only for reference lookup. Apply explicit aliases only for `ConvexLineOnlyPathsGm -> convex-lineonly-paths` and `ConvexLineOnlyPathsStrokeAndFillGm -> convex-lineonly-paths-stroke-and-fill`, then keep the checker work focused on classification so ambiguous names and variant families stay diagnostic-only.

**Tech Stack:** Kotlin/JVM, JUnit 5, Python 3 `unittest`, Gradle, Skia C++ GM source inventory, checked-in PNG references.

## Global Constraints

- Do not add, remove, copy, rename, or regenerate files under `integration-tests/skia/src/test/resources/reference/`.
- Keep `SkiaGm.name` unchanged for existing approximate, duplicate, `No-op`, `STUB`, and `Best-effort` ports.
- Assign no concrete `referenceName` override unless the CPP registration is one-to-one and the Kotlin render is compatible with the reference dimensions and semantics.
- In this PR, apply concrete overrides only for `convex_lineonly_paths` and `convex_lineonly_paths_stroke_and_fill`; keep `lineargradientrt`, `colorcubert`, `colorcubecolorfilterrt`, and multi-variant families on the no-alias path.
- Do not hard-code developer-specific absolute paths in checked-in code; `--cpp-gm-dir` must be optional and explicit.
- Do not port Ganesh or Graphite, rebuild SkSL, change the renderer, or alter similarity thresholds.
- Keep native Kadre execution and `external/poc-koreos` out of this headless validation.
- Preserve the existing direct-use behavior of `scripts/check_missing_gms.py` when no CPP directory is supplied.

---

### Task 1: Add the logical/reference-name contract

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaDashboardGenerator.kt`
- Test: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunnerFilterTest.kt`
- Test: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaDashboardGeneratorTest.kt`

**Interfaces:**
- Produces `SkiaGm.referenceName: String`, defaulting to `name`.
- Produces `referenceResourcePath(gm: SkiaGm): String` in `SkiaGmRunner.kt`.
- Produces `referenceFile(refDir: File, gm: SkiaGm): File` in `SkiaDashboardGenerator.kt`.
- Consumers continue to use `gm.name` for filtering, score keys, generated-render paths, and diagnostic directories.

- [ ] **Step 1: Write the failing Kotlin tests**

Add a runner test fixture whose logical and reference names differ:

```kotlin
private class AliasStubRunnerGm(
    override val name: String = "logical-gm",
) : SkiaGm {
    override val referenceName: String = "cpp-gm"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
```

Add assertions that `referenceResourcePath(AliasStubRunnerGm())` equals
`/reference/cpp-gm.png` and that `name` remains `logical-gm`.

Add a dashboard test fixture with `name = "logical-gm"` and
`referenceName = "cpp-gm"`; create only `reference/cpp-gm.png` and
`generated-renders/<family>/logical-gm.png`. Assert that the generated JSON
marks the entry as scored rather than `reference-missing`, and that its JSON
entry name remains `logical-gm`.

- [ ] **Step 2: Run the focused tests and verify they fail for the intended reason**

Run:

```bash
rtk ./gradlew :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunnerFilterTest \
  --tests org.graphiks.kanvas.skia.SkiaDashboardGeneratorTest
```

Expected failure: the runner path still uses `gm.name`, and the dashboard
reports `reference-missing` because it looks for `logical-gm.png`.

- [ ] **Step 3: Implement the minimal contract and path helpers**

In `SkiaGm.kt`, add:

```kotlin
val referenceName: String get() = name
```

In `SkiaGmRunner.kt`, add:

```kotlin
internal fun referenceResourcePath(gm: SkiaGm): String =
    "/reference/${gm.referenceName}.png"
```

Replace the inline `"/reference/${gm.name}.png"` construction with this
helper. Keep `SimilarityTracker.updateScore(gm.name, ...)`, output directory
creation, and diagnostic `gmName` values unchanged.

In `SkiaDashboardGenerator.kt`, add:

```kotlin
internal fun referenceFile(refDir: File, gm: SkiaGm): File =
    refDir.resolve("${gm.referenceName}.png")
```

Use this helper for reference loading. Keep generated-render lookup and all
dashboard entry `name` fields based on `gm.name`.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run the same Gradle command from Step 2. Expected: all selected tests pass and
the alias fixture resolves `cpp-gm.png` while retaining `logical-gm` in JSON.

- [ ] **Step 5: Commit the contract change**

```bash
rtk git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt \
  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt \
  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaDashboardGenerator.kt \
  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunnerFilterTest.kt \
  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaDashboardGeneratorTest.kt
rtk git commit -m "test: separate GM identity from reference identity"
```

### Task 2: Make the C++ GM inventory reusable and configurable

**Files:**
- Modify: `scripts/extract_skia_gm_names.py`
- Create: `scripts/test_extract_skia_gm_names.py`

**Interfaces:**
- Produces `extract_gm_names(gm_dir: pathlib.Path) -> set[str]`.
- Preserves the existing `--names` CLI mode.
- Adds `--gm-dir PATH` so callers and tests can select a CPP checkout without a developer-specific hard-coded path.

- [ ] **Step 1: Write failing Python tests for a caller-provided CPP directory**

Create temporary CPP fixtures covering one direct registration and one class
registration:

```cpp
DEF_SIMPLE_GM(foo_bar, canvas, 10, 10) {
    canvas->clear(SK_ColorWHITE);
}

class BazGM : public GM {
protected:
    SkString getName() const override { return SkString("baz-gm"); }
};

DEF_GM(return new BazGM;)
```

Assert that `extract_gm_names(temp_dir)` returns exactly `foo_bar` and
`baz-gm`, and that the CLI accepts `--gm-dir temp_dir --names`.

- [ ] **Step 2: Run the Python test and verify it fails**

Run:

```bash
rtk python3 scripts/test_extract_skia_gm_names.py
```

Expected failure: the module has no reusable directory-parameterized API and
the CLI ignores `--gm-dir`.

- [ ] **Step 3: Refactor the extractor without changing its current output format**

Move the current hard-coded scan body into:

```python
def extract_gm_names(gm_dir: pathlib.Path) -> set[str]:
    # scan sorted(gm_dir.glob("*.cpp")) using the existing parsing helpers
    return names
```

Make `main()` parse `--names` and `--gm-dir`, pass the selected directory to
`extract_gm_names`, and preserve the existing summary output when `--names`
is absent. Keep unresolved dynamic registrations explicit rather than
inventing a concrete variant name.

- [ ] **Step 4: Run the Python test and verify it passes**

Run the command from Step 2. Expected: both fixture names are extracted and
the command exits with status 0.

- [ ] **Step 5: Commit the reusable extractor**

```bash
rtk git add scripts/extract_skia_gm_names.py scripts/test_extract_skia_gm_names.py
rtk git commit -m "refactor: parameterize Skia GM source inventory"
```

### Task 3: Add source-aware missing-reference classification

**Files:**
- Modify: `scripts/check_missing_gms.py`
- Create: `scripts/test_check_missing_gms.py`

**Interfaces:**
- Produces `normalize_name(value: str) -> str`.
- Produces `classify_reference(gm_name: str, references: set[str], cpp_names: set[str] | None) -> dict[str, object]` with a stable `kind` field.
- The CLI accepts `--cpp-gm-dir PATH` and keeps the existing no-argument invocation valid.

- [ ] **Step 1: Write failing tests for classification order**

Cover these cases with `unittest`:

```python
def test_separator_alias_is_reported_without_being_direct_match(self):
    result = classify_reference("lineargradientrt", {"linear_gradient_rt"}, None)
    self.assertEqual("normalized-alias", result["kind"])
    self.assertEqual("linear_gradient_rt", result["reference"])

def test_prefix_family_is_reported_as_variant(self):
    result = classify_reference(
        "clippedbitmapshaders",
        {"clipped-bitmap-shaders-clamp", "clipped-bitmap-shaders-tile"},
        {"clippedbitmapshaders"},
    )
    self.assertEqual("variant-family", result["kind"])

def test_unmatched_name_is_actionable_missing(self):
    result = classify_reference("aa_rect_effect", {"aarectmodes"}, {"aa_rect_effect"})
    self.assertEqual("missing", result["kind"])
```

Add a CLI fixture test with `--cpp-gm-dir` and assert that the output contains
separate headings for normalized aliases, variant families, and actionable
missing references.

- [ ] **Step 2: Run the Python tests and verify they fail**

Run:

```bash
rtk python3 scripts/test_check_missing_gms.py
```

Expected failure: the classifier and `--cpp-gm-dir` option do not exist.

- [ ] **Step 3: Implement normalization and source-aware classification**

Implement:

```python
def normalize_name(value: str) -> str:
    return "".join(ch for ch in value.casefold() if ch.isalnum())
```

Use this order inside `classify_reference`:

1. exact `gm_name` in the reference set → `direct`;
2. exactly one normalized reference candidate → `normalized-alias`;
3. one or more references sharing a CPP/base-family prefix → `variant-family`;
4. otherwise → `missing`.

Load CPP names only when `--cpp-gm-dir` is supplied by calling
`extract_gm_names(Path(args.cpp_gm_dir))`. If the option is absent, emit
`source-evidence: unavailable` and do not pretend that a fuzzy filename is a
CPP match. Preserve the existing counts and actionable missing section, adding
stable evidence headings before it.

The checker must not infer `referenceName` overrides for current Kotlin
classes. Its source evidence is diagnostic only; the Kotlin contract from Task
1 is the only runtime alias mechanism.

- [ ] **Step 4: Run the Python tests and inspect real source evidence**

Run the tests from Step 2, then run the explicit external-source diagnostic:

```bash
rtk python3 scripts/check_missing_gms.py \
  --cpp-gm-dir "${KANVAS_SKIA_GM_DIR:-PATH}"
```

Expected: normalized aliases and variant-family candidates are separated from
true missing references; no PNG files are modified.

- [ ] **Step 5: Commit the source-aware checker**

```bash
rtk git add scripts/check_missing_gms.py scripts/test_check_missing_gms.py
rtk git commit -m "feat: classify GM references from CPP source"
```

### Task 4: Full validation and PR readiness

**Files:**
- Verify only: `integration-tests/skia/src/test/resources/reference/`
- Verify only: `integration-tests/skia/src/test/resources/generated-renders/`
- Verify only: all files changed by Tasks 1–3

**Interfaces:**
- Consumes the focused Kotlin and Python tests from Tasks 1–3.
- Produces a clean, reviewable branch with no reference PNG changes and a
  source-evidence report suitable for the PR description.

- [ ] **Step 1: Run all focused Python tests**

```bash
rtk python3 scripts/test_extract_skia_gm_names.py
rtk python3 scripts/test_check_missing_gms.py
```

Expected: exit status 0 for both commands.

- [ ] **Step 2: Run focused Kotlin tests**

```bash
rtk ./gradlew :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunnerFilterTest \
  --tests org.graphiks.kanvas.skia.SkiaDashboardGeneratorTest \
  --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest
```

Expected: all selected tests pass without requiring Kadre or
`external/poc-koreos`.

- [ ] **Step 3: Verify the diff and reference immutability**

```bash
rtk git diff --check
rtk git status --short
rtk git diff --name-status -- integration-tests/skia/src/test/resources/reference
rtk git diff --name-status -- integration-tests/skia/src/test/resources/generated-renders
```

Expected: no reference or generated-render PNG is added, removed, or changed.

- [ ] **Step 4: Run the repository-level source-aware report**

```bash
rtk python3 scripts/check_missing_gms.py
```

Record the direct, normalized, variant-family, unsupported, and actionable
missing counts in the PR description. Do not claim the missing count is a
renderer support score.

- [ ] **Step 5: Request review and publish a draft PR**

Before publishing, inspect the complete diff and request a focused code
review. Address all Critical and Important findings. Then use the repository
publish workflow to stage only the implementation files, commit with a concise
message, push `codex/gm-reference-alignment`, and open a draft PR targeting
the remote default branch. The PR body must include the root cause, CPP
evidence, explicit non-goals, validation commands, and the fact that no PNG
references changed.

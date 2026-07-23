# Origin Master Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the four commits through `origin/master@958687305` into `codex/graphite-dawn-frame-plan-design`, preserve the prepared WebGPU frame architecture and local user changes, and prove that the combined source compiles locally.

**Architecture:** Perform one explicit merge from the verified merge base `dd1841e36a74449c33507a6dac2485c09c5a6243`. Resolve the three predicted overlaps as a semantic union: retain the branch's prepared Kotlin/WGSL/WebGPU route, add the upstream codec projects, and carry the upstream lattice sampling/fixed-color behavior into the current renderer. Validate the new codec, font, GM, and lattice surfaces before rerunning the focused frame-plan regression set.

**Tech Stack:** Git, PowerShell, Gradle wrapper, Kotlin/JVM, JUnit 5, WebGPU/wgpu4k, Skia GM integration tests.

## Global Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend and CPU as the Skia-like reference path.
- Preserve the registered Kotlin/WGSL runtime-effect model; do not introduce dynamic SkSL compilation.
- Preserve the current branch versions of the prepared frame-plan, blend-plan, coverage, clip, and device-limit authorities.
- Integrate exactly `origin/master@958687305`; the four missing commits are `bbd07f790`, `ce46015e2`, `7f66913aa`, and `958687305`.
- Do not modify or stage the user's local changes in `buildSrc/build.gradle.kts` and `gradle/verification-metadata.xml`.
- Dependency-verification metadata, checksum maintenance, and reproducible-build policy are out of scope; every Gradle command uses `--dependency-verification=off`.
- Run Gradle commands serially because native WebGPU workers and shared Gradle outputs are not safe to validate concurrently in this workspace.
- Keep FP-09's known process-global WebGPU lifetime crash separate from this integration unless the merge changes its minimal reproduction.

---

### Task 1: Merge and resolve the three semantic overlaps

**Files:**

- Modify: `settings.gradle.kts:119-131`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt:1213-1446`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:1589-3049`
- Integrate: every path changed by `origin/master@958687305`
- Preserve unstaged: `buildSrc/build.gradle.kts`
- Preserve unstaged: `gradle/verification-metadata.xml`

**Interfaces:**

- Consumes: current prepared renderer APIs on `codex/graphite-dawn-frame-plan-design` and upstream `DisplayOp.DrawImageLattice.sampling`, `Lattice.flags`, and `Lattice.colors`.
- Produces: `DisplayOp.DrawImage.toImageRectCommand(GPUDrawCommandID, GPUTargetFacts, SamplingOptions?): NormalizedDrawCommand.DrawImageRect`, `DisplayOp.DrawImageLattice.decompose(): List<ImageCell>`, and `fixedLatticeColorPaint(Color, Paint?): Paint`.

- [ ] **Step 1: Recheck the exact merge scope and protected local edits**

Run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas status --short --branch
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas merge-base HEAD origin/master
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas rev-list --left-right --count HEAD...origin/master
```

Expected: the branch is ahead of its remote, the only pre-merge working-tree modifications are `buildSrc/build.gradle.kts` and `gradle/verification-metadata.xml`, the merge base is `dd1841e36a74449c33507a6dac2485c09c5a6243`, and the right-hand count is `4`.

- [ ] **Step 2: Start the merge without committing**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas merge --no-ff --no-commit origin/master
```

Expected: Git integrates all non-overlapping upstream paths and reports conflicts only in `settings.gradle.kts`, `GPUOpMapper.kt`, and `GPURenderer.kt`. The two protected local modifications remain unstaged and contain no conflict markers.

- [ ] **Step 3: Resolve `settings.gradle.kts` as a union**

Keep the branch's repository filtering, immutable WebGPU dependency mapping, Gradle/toolchain configuration, and complete existing project list. Insert these three includes immediately after `include(":codec:jpeg")`:

```kotlin
include(":codec:jpeg-ls")
include(":codec:jpeg2000")
include(":codec:jpegxl")
```

Expected: all existing branch modules remain included exactly once and the three new codec modules are included exactly once.

- [ ] **Step 4: Resolve image sampling and lattice decomposition in `GPUOpMapper.kt`**

Keep all prepared mapper, blend, coverage, clip, and frame-provenance code from the branch. Add `import org.graphiks.kanvas.paint.Paint`, then replace the sampling selection at the start of `toImageRectCommand` with:

```kotlin
internal fun DisplayOp.DrawImage.toImageRectCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
    sampling: org.graphiks.kanvas.paint.SamplingOptions? = null,
): NormalizedDrawCommand.DrawImageRect {
    val image = this.image
    val requestedSampling = sampling ?: this.paint?.let { paint ->
        (paint.shader as? org.graphiks.kanvas.paint.Shader.Image)?.sampling
    }
    val samplingFilterMode = when (requestedSampling) {
        org.graphiks.kanvas.paint.SamplingOptions.NEAREST -> "nearest"
        org.graphiks.kanvas.paint.SamplingOptions.LINEAR,
        is org.graphiks.kanvas.paint.SamplingOptions.Cubic,
        null,
        -> "linear"
    }
```

In `DisplayOp.DrawImageLattice.decompose()`, calculate destination boundaries before visiting cells:

```kotlin
val dstColumns = latticeDestinationBoundaries(cols, d.left, d.right)
val dstRows = latticeDestinationBoundaries(rows, d.top, d.bottom)
```

Construct each destination cell from those boundaries, skip `LatticeFlags.TRANSPARENT`, and expose a color only for `LatticeFlags.FIXED_COLOR`:

```kotlin
val dstRect = if (lat.rects != null && cellIndex < lat.rects.size) {
    lat.rects[cellIndex]
} else {
    Rect.fromLTRB(dstColumns[c], dstRows[r], dstColumns[c + 1], dstRows[r + 1])
}
val flag = lat.flags?.getOrNull(cellIndex) ?: org.graphiks.kanvas.types.LatticeFlags.DEFAULT
if (flag == org.graphiks.kanvas.types.LatticeFlags.TRANSPARENT) {
    cellIndex++
    continue
}
val color = if (flag == org.graphiks.kanvas.types.LatticeFlags.FIXED_COLOR) {
    lat.colors?.getOrNull(cellIndex)
} else {
    null
}
```

Add these helpers immediately after `decompose()`:

```kotlin
private fun latticeDestinationBoundaries(
    sourceBoundaries: List<Float>,
    destinationStart: Float,
    destinationEnd: Float,
): List<Float> {
    val segmentCount = sourceBoundaries.size - 1
    if (segmentCount <= 0) return listOf(destinationStart, destinationEnd)
    val segmentLengths = List(segmentCount) { sourceBoundaries[it + 1] - sourceBoundaries[it] }
    val fixedTotal = segmentLengths.filterIndexed { index, _ -> index % 2 == 0 }.sum()
    val scalableTotal = segmentLengths.filterIndexed { index, _ -> index % 2 != 0 }.sum()
    val destinationLength = destinationEnd - destinationStart
    val fixedScale = if (fixedTotal <= 0f) 0f else minOf(1f, destinationLength / fixedTotal)
    val scalableLength = (destinationLength - fixedTotal * fixedScale).coerceAtLeast(0f)
    val result = ArrayList<Float>(sourceBoundaries.size)
    result += destinationStart
    var current = destinationStart
    for (index in 0 until segmentCount) {
        val length = when {
            index % 2 == 0 -> segmentLengths[index] * fixedScale
            scalableTotal > 0f -> scalableLength * segmentLengths[index] / scalableTotal
            else -> 0f
        }
        current += length
        result += current
    }
    result[result.lastIndex] = destinationEnd
    return result
}

internal fun fixedLatticeColorPaint(color: Color, paint: Paint?): Paint {
    val base = paint ?: Paint()
    return base.copy(color = Color.fromRGBA(color.r, color.g, color.b, color.a * base.color.a))
}
```

Expected: there are no conflict markers and the branch's existing `GPUOpMapper.mapOperations` authority is unchanged.

- [ ] **Step 5: Resolve lattice rendering in every `GPURenderer.kt` route**

Keep the branch's prepared frame-plan and canonical blend changes. Extend the local `renderImageCommand` helper with an optional `SamplingOptions` argument and pass it to `toImageRectCommand`.

For each of the three lattice dispatch sites (direct clip route, legacy top-level route, and nested picture route), use the same exact split:

```kotlin
val fixedColor = cell.color
if (fixedColor != null) {
    val rectCell = DisplayOp.DrawRect(
        cell.dst,
        fixedLatticeColorPaint(fixedColor, op.paint),
        op.transform,
        op.clip,
    )
    dispatchRectDirect(rectCell.toNormalizedCommand(subCmdId, targets))
} else {
    val imageCell = DisplayOp.DrawImage(
        op.image,
        cell.src,
        cell.dst,
        op.paint,
        op.transform,
        op.clip,
    )
    renderImageColorCommand(imageCell.toImageRectCommand(subCmdId, targets, op.sampling))
}
```

Use `nestedOp` instead of `op` in the nested picture route. In the direct clip route, keep its existing boolean aggregation and call `renderImageCommand(imageCell, subCmdId, op.sampling)` for image cells. Retain upstream's single BGRA-to-RGBA conversion in `GPUImagePixels.expandToRgbaForGpu()` and remove the duplicate conversion around the `DrawVertices` upload.

Expected: fixed-color cells render as rectangles, transparent cells were already removed by decomposition, image cells honor lattice sampling, and prepared frame routing remains the branch authority.

- [ ] **Step 6: Verify the resolved tree before staging**

Run:

```powershell
rg -n '^(<<<<<<<|=======|>>>>>>>)' settings.gradle.kts kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas diff --check
.\gradlew.bat :kanvas:test --tests "org.graphiks.kanvas.surface.gpu.LatticeDecompositionTest" --tests "org.graphiks.kanvas.image.ColorTypeTest" --tests "org.graphiks.kanvas.text.CustomTypefaceTest" --tests "org.graphiks.kanvas.text.FontRenderingOptionsTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: `rg` returns no matches, `diff --check` is clean, and the focused upstream Kanvas tests pass.

- [ ] **Step 7: Stage only the merge result and create the merge commit**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -A -- . ':!buildSrc/build.gradle.kts' ':!gradle/verification-metadata.xml'
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "merge: integrate current origin master"
```

Expected: one two-parent merge commit contains the four upstream commits, the plan, and the semantic resolutions; the two protected files remain modified and unstaged.

### Task 2: Compile the integrated project and validate upstream surfaces

**Files:**

- Read: `settings.gradle.kts`
- Test: `codec/api/src/test/kotlin/org/graphiks/kanvas/codec/CodecStreamLimitTest.kt`
- Test: `codec/jpeg-ls/src/test/kotlin/org/graphiks/kanvas/codec/jpegls/JpegLsCodecTest.kt`
- Test: `codec/jpeg2000/src/test/kotlin/org/graphiks/kanvas/codec/jpeg2000/Jpeg2000DocumentTest.kt`
- Test: `codec/jpegxl/src/test/kotlin/org/graphiks/kanvas/codec/jpegxl/JpegXlDocumentTest.kt`
- Test: `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- Test: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistryTest.kt`
- Test: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/ZeroLengthPathLayoutTest.kt`

**Interfaces:**

- Consumes: the merged Gradle project graph and source tree from Task 1.
- Produces: local compile evidence for every included project plus focused codec/font/GM behavior evidence.

- [ ] **Step 1: Confirm the new project graph**

Run:

```powershell
.\gradlew.bat projects --dependency-verification=off --no-daemon --console=plain
```

Expected: the output contains `:codec:jpeg-ls`, `:codec:jpeg2000`, and `:codec:jpegxl`.

- [ ] **Step 2: Compile every included project**

Run:

```powershell
.\gradlew.bat assemble --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`. JDK 25/Kotlin fallback and restricted-native-access messages are warnings, not failures.

- [ ] **Step 3: Run the focused upstream codec suites**

Run:

```powershell
.\gradlew.bat :codec:api:test :codec:jpeg:test :codec:jpeg-ls:test :codec:jpeg2000:test :codec:jpegxl:test --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` and all five test tasks pass.

- [ ] **Step 4: Run the focused upstream font and GM suites**

Run:

```powershell
.\gradlew.bat :font:scaler:test :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaGmRegistryTest" --tests "org.graphiks.kanvas.skia.gm.path.ZeroLengthPathLayoutTest" --tests "org.graphiks.kanvas.skia.gm.text.CffTypefaceBridgeTest" --tests "org.graphiks.kanvas.skia.gm.text.SkiaFontFixtureContractTest" --tests "org.graphiks.kanvas.skia.gm.text.VariableFontRenderingTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; font-surface, GM registry, zero-length layout, CFF bridge, fixture-contract, and variable-font coverage pass.

- [ ] **Step 5: Run the frame-plan regressions touched by the conflict resolution**

Run:

```powershell
.\gradlew.bat :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUUniformSlabPlannerTest" --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest.fullscreen uniform alignment requires device limits and preserves stricter alignment" --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFrameSmokeTest.native analytic shape uniform80 proves rect aa asymmetric rrect pixels batching and reuse" :kanvas:test --tests "org.graphiks.kanvas.surface.gpu.LatticeDecompositionTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; the device-backed alignment and prepared multi-packet route remain green alongside lattice decomposition.

### Task 3: Record FP-02 evidence and advance the ordered backlog

**Files:**

- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**

- Consumes: the merge commit hash and exact Task 2 command outcomes.
- Produces: FP-02 status `completed`, FP-03 status `in_progress`, and an auditable integration evidence summary.

- [ ] **Step 1: Update the active ordered backlog**

Under FP-02, set `Status: completed` and add:

```markdown
Resolution evidence:

- the Task 1 two-parent merge commit integrates `origin/master@958687305`,
  including upstream commits `bbd07f790`, `ce46015e2`, `7f66913aa`, and
  `958687305`;
- the three semantic conflicts preserve the prepared WebGPU frame route while
  adding the JPEG-LS/JPEG 2000/JPEG XL projects and faithful lattice
  sampling/fixed-color behavior;
- `assemble` compiles every included project locally with dependency
  verification disabled per scope;
- focused codec, font, GM, lattice, device-limit, and prepared-frame tests pass.
```

Prefix the first bullet with the exact hash returned by
`git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas rev-parse HEAD`
immediately after Task 1. Change FP-03 to `Status: in_progress`. Do not alter
FP-03's six recorded failure cases or FP-09's native-lifetime evidence.

- [ ] **Step 2: Update the subagent progress ledger**

Replace its plan path with `docs/superpowers/plans/2026-07-24-origin-master-integration.md` and record Task 1, Task 2, and Task 3 outcomes with their commit hashes and review verdicts.

- [ ] **Step 3: Verify documentation and protected-file state**

Run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas diff --check
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas status --short --branch
```

Expected: documentation has no whitespace errors, and only `buildSrc/build.gradle.kts` plus `gradle/verification-metadata.xml` remain modified and unstaged after the FP-02 documentation commit.

- [ ] **Step 4: Commit the FP-02 closeout**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "docs: complete origin master integration"
```

Expected: the active backlog advances to FP-03 and the protected local changes remain outside the commit.

### Task 4: Review the complete FP-02 result

**Files:**

- Review: merge base through the FP-02 closeout commit
- Review: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Review: Task 1 and Task 2 validation output

**Interfaces:**

- Consumes: all FP-02 commits and evidence.
- Produces: a severity-ranked review verdict and a binary decision on readiness to begin FP-03.

- [ ] **Step 1: Audit ancestry, protected edits, and conflict residue**

Run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas merge-base --is-ancestor origin/master HEAD
rg -n '^(<<<<<<<|=======|>>>>>>>)' .
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas status --short --branch
```

Expected: the ancestry command succeeds, no conflict markers exist in tracked source, and protected local modifications remain unstaged.

- [ ] **Step 2: Review architectural correctness**

Confirm all of the following:

- the merge did not remove or bypass the prepared frame-plan route;
- `toImageRectCommand` accepts caller-provided lattice sampling without changing ordinary image shader sampling;
- transparent lattice cells are omitted and fixed-color cells render as rectangles with caller alpha/blend preserved;
- BGRA conversion occurs exactly once before GPU upload;
- the three new codec modules are present without changing checksum policy;
- no Ganesh, Graphite, dynamic SkSL compiler, hidden fallback, or unrelated dependency substitute was introduced.

- [ ] **Step 3: Produce the final FP-02 verdict**

Report Critical, Important, and Minor findings with file/line evidence. If there are no findings, state explicitly that FP-02 is ready to proceed to FP-03; do not claim the overall branch is merge-ready while FP-03 through FP-11 remain active.

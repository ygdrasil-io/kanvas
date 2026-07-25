# Windows Test Portability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every FP-03 Windows-only test failure while preserving byte-exact codec fixtures, semantic text goldens, the prepared WebGPU route, and a strict one-UNORM8-LSB cross-adapter pixel policy.

**Architecture:** Fix each proven root cause at its narrowest boundary. Git attributes preserve five raw PGM byte artifacts; test-local helpers canonicalize only JSON line terminators, extract a Kotlin block independently of checkout newlines, select the native Gradle wrapper, and validate semantic diagnostics plus a maximum one-LSB pixel delta. No renderer, shader, codec decoder, font scaler, or Gradle task graph behavior changes.

**Tech Stack:** Git attributes, Kotlin/JVM, JUnit 5/kotlin.test, Gradle 9.2 wrapper, Eclipse Temurin JDK 25, WebGPU/wgpu4k native tests.

## Global Constraints

- Process the active backlog in order: FP-03 is the sole `in_progress` item until all tasks and the final review pass.
- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend and CPU as the Skia-like reference path.
- Do not modify GPU output, WGSL, codec decoding, font scaling, or the normal Gradle task graph for portability-only failures.
- Preserve direct SHA-256 contracts for the five raw PGM resources; never update expected hashes to match a CRLF checkout.
- Normalize only CRLF/CR line terminators for the nine semantic JSON golden comparisons; preserve every other character, indentation, ordering, and numeric value.
- The cross-adapter prepared pixel limit is `maxChannelDelta <= 1` with full `withinOneLsb=64000/64000` coverage; exact pixel counts remain telemetry, not a portable gate.
- Preserve the two user-owned unstaged changes in `buildSrc/build.gradle.kts` and `gradle/verification-metadata.xml`.
- Dependency-verification metadata, checksum maintenance, and reproducible-build policy remain out of scope; every Gradle command uses `--dependency-verification=off`.
- Before every Gradle command set `JAVA_HOME=C:\Users\Shadow\.jdks\temurin-25.0.3` and prepend `%JAVA_HOME%\bin` to `PATH`.
- Run Gradle commands serially; never overlap native WebGPU or shared-output test executions.
- Controller-only Git mutations use `user.name=ygdrasil-io` and `user.email=alexandre.mommers@gmail.com` without persisting repository or global Git configuration.

---

### Task 1: Preserve raw PGM fixture bytes on Windows

**Files:**

- Modify: `.gitattributes:1-3`
- Verify unchanged: `codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source.pgm`
- Verify unchanged: `codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm`
- Verify unchanged: `codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm`
- Verify unchanged: `codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm`
- Verify unchanged: `codec/jpegxl/src/test/resources/jpegxl-modular/flower-510x532-8bit-lossless.pgm`
- Test: `codec/jpeg2000/src/test/kotlin/org/graphiks/kanvas/codec/jpeg2000/Jpeg2000DocumentTest.kt`
- Test: `codec/jpegxl/src/test/kotlin/org/graphiks/kanvas/codec/jpegxl/JpegXlModularDecodeTest.kt`

**Interfaces:**

- Consumes: existing direct `ByteArray.sha256()` fixture-integrity assertions and LF-preserved Git blobs.
- Produces: two narrow `-text` checkout contracts and working-tree bytes identical to the five `HEAD` blobs.

- [ ] **Step 1: Prove the five paths contain no user edit**

Run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas diff --quiet -- codec/jpeg2000/src/test/resources/jpeg2000-openjpeg codec/jpegxl/src/test/resources/jpegxl-modular
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas diff --cached --quiet -- codec/jpeg2000/src/test/resources/jpeg2000-openjpeg codec/jpegxl/src/test/resources/jpegxl-modular
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas ls-files --eol -- codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/*.pgm codec/jpegxl/src/test/resources/jpegxl-modular/*.pgm
```

Expected: both `diff --quiet` commands exit `0`; the five affected paths report `i/lf w/crlf attr/`.

- [ ] **Step 2: Reproduce the five failures (RED)**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :codec:jpeg2000:test :codec:jpegxl:test --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: exactly four raw-PGM SHA failures in `Jpeg2000DocumentTest` and one in `JpegXlModularDecodeTest`; no compile, parse, decode, or additional test failure.

- [ ] **Step 3: Add the narrow byte-preservation rules**

Append exactly:

```gitattributes
codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/*.pgm -text
codec/jpegxl/src/test/resources/jpegxl-modular/*.pgm -text
```

Do not add a repository-wide `*.pgm` rule and do not change any fixture or expected hash.

- [ ] **Step 4: Rematerialize only the clean PGM files from `HEAD`**

Controller-only run after repeating Step 1's two clean checks:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- .gitattributes
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas restore --source=HEAD --worktree -- codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source.pgm codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm codec/jpegxl/src/test/resources/jpegxl-modular/flower-510x532-8bit-lossless.pgm
```

Expected: only `.gitattributes` is staged; none of the five PGM paths appears in `git status --short`.

- [ ] **Step 5: Verify attributes, EOL state, and exact hashes**

Run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas check-attr text -- codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source.pgm codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm codec/jpegxl/src/test/resources/jpegxl-modular/flower-510x532-8bit-lossless.pgm
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas ls-files --eol -- codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/*.pgm codec/jpegxl/src/test/resources/jpegxl-modular/*.pgm
Get-FileHash -Algorithm SHA256 codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source.pgm,codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm,codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm,codec/jpeg2000/src/test/resources/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm,codec/jpegxl/src/test/resources/jpegxl-modular/flower-510x532-8bit-lossless.pgm
```

Expected: each path reports `text: unset`, `attr/-text`, and `w/lf`; hashes are, in command order, `2BDF55049E85C305EB510DF45D10CE0150D92BAC8663CF55E8E8D8B550FBD702`, `8EA8D1148129457247B37C889415D3F5EDBFDE4DFF929C09280618899A9EAECA`, `776F58EFB28E49ED6656BD5D331757C8546B99FDA4754F8D3CA7E3EE36601ED9`, `6E2EE7CE0880C67527F1A1DD6FED83703DE8B66943DD9D623D1EFB0BA5C8B612`, and `4580F75490C0BC38159A381615571E2A341FC0ADDE99B4B3B0ED5BBEA97DA1FC`.

- [ ] **Step 6: Prove codec GREEN**

Rerun Step 2's Gradle command.

Expected: `BUILD SUCCESSFUL`; both codec test tasks pass with existing hashes and fixture loaders.

- [ ] **Step 7: Commit the attribute contract**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- .gitattributes
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "test(codec): preserve raw PGM fixture bytes"
```

Expected: the commit contains only `.gitattributes`; protected local files remain unstaged.

### Task 2: Compare font golden JSON with canonical line terminators

**Files:**

- Modify: `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt:3039-3071`
- Modify: `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt:3770-3775`
- Modify: `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt:3932-3967`
- Modify: `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt:6860-6875`

**Interfaces:**

- Consumes: nine expected JSON strings from `reports/font/fixtures/expected/scaler/` and nine deterministic generated dumps.
- Produces: `assertCanonicalGoldenEquals(expected: String, actual: String)` and `String.canonicalLf(): String`, both test-local.

- [ ] **Step 1: Reproduce the nine failures (RED)**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :font:scaler:test --tests "org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: exactly nine methods ending in `GoldenMatchesGeneratedEvidence` fail because expected embedded CRLF differs from generated LF; the other 96 tests pass.

- [ ] **Step 2: Add the narrow canonical-line assertion**

Add near the existing private helpers:

```kotlin
private fun assertCanonicalGoldenEquals(expected: String, actual: String) {
    assertEquals(expected.canonicalLf().trimEnd(), actual.canonicalLf().trimEnd())
}

private fun String.canonicalLf(): String =
    replace("\r\n", "\n").replace('\r', '\n')
```

This helper must not parse JSON or normalize spaces, tabs, indentation, ordering, or values.

- [ ] **Step 3: Route exactly nine golden assertions through the helper**

For these methods only:

```text
cffCharStringTraceGoldenMatchesGeneratedEvidence
cffSubroutineTraceGoldenMatchesGeneratedEvidence
cffScalerPathOutputGoldenMatchesGeneratedEvidence
cff2VariationTraceGoldenMatchesGeneratedEvidence
cffIndexDictGoldenMatchesGeneratedEvidence
truetypeCompositeGlyphReadinessGoldenMatchesGeneratedEvidence
truetypeGvarIupGoldenMatchesGeneratedEvidence
truetypeMalformedGlyfIsolationGoldenMatchesGeneratedEvidence
truetypeVerticalMetricsGoldenMatchesGeneratedEvidence
```

remove `.trimEnd()` from the `Files.readString(...)` expression and replace:

```kotlin
assertEquals(expected, actual)
```

or its dump-call equivalent with:

```kotlin
assertCanonicalGoldenEquals(expected, actual)
```

where `actual` is the existing dump call or local value. Do not change any expected JSON file or generator.

- [ ] **Step 4: Prove font GREEN**

Rerun Step 1's command.

Expected: all 105 tests pass.

- [ ] **Step 5: Commit the semantic golden comparison**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "test(font): normalize golden line endings"
```

Expected: one test-file-only commit.

### Task 3: Make the materializer source policy independent of EOL style

**Files:**

- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt:1915-1950`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt` private helper region

**Interfaces:**

- Consumes: the materializer source, anchor `val acceptedGeometries = semanticPackets.mapIndexed`, and the subsequent uniform80 `if` condition.
- Produces: `String.balancedBlockAfter(anchor: String, blockCondition: String): String`, a test-local EOL-independent source extractor.

- [ ] **Step 1: Reproduce the source-slice failure (RED)**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.direct core materializer integrity gate performs no canonical hash work" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: failure stating `uniform80 materialization must not create a second payload snapshot per draw`, because the LF delimiter misses the CRLF source and includes the later uniform160 block.

- [ ] **Step 2: Add an anchored balanced-brace extractor**

Add this test-local helper:

```kotlin
private fun String.balancedBlockAfter(anchor: String, blockCondition: String): String {
    val anchorIndex = indexOf(anchor)
    check(anchorIndex >= 0) { "Source anchor not found: $anchor" }
    val conditionIndex = indexOf(blockCondition, startIndex = anchorIndex)
    check(conditionIndex >= 0) { "Source block condition not found after anchor: $blockCondition" }
    val openingBraceIndex = indexOf('{', startIndex = conditionIndex + blockCondition.length)
    check(openingBraceIndex >= 0) { "Source block opening brace not found: $blockCondition" }
    var depth = 0
    for (index in openingBraceIndex until length) {
        when (this[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return substring(conditionIndex, index + 1)
            }
        }
    }
    error("Source block closing brace not found: $blockCondition")
}
```

- [ ] **Step 3: Replace the LF delimiter slice**

Replace the chained `substringAfter(...).substringBefore(...)` with:

```kotlin
val analyticShapeValidation = source.balancedBlockAfter(
    anchor = "val acceptedGeometries = semanticPackets.mapIndexed",
    blockCondition = "if (uniformLayout == " +
        "GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1)",
)
```

Keep all existing structural-integrity and negative allocation/hash assertions unchanged.

- [ ] **Step 4: Prove materializer GREEN**

Rerun Step 1's command.

Expected: the test passes without a production materializer change.

- [ ] **Step 5: Commit the semantic source inspection**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "test(gpu): make source inspection EOL independent"
```

### Task 4: Launch the native Gradle wrapper in subprocess tests

**Files:**

- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/GPURendererScenesModuleBoundaryTest.kt:112-150`

**Interfaces:**

- Consumes: `repoRoot(): Path`, the repository's `gradlew` and `gradlew.bat`.
- Produces: `gradleWrapper(repoRoot: Path): Path`; `runGradleDryRun` launches its absolute path without a shell.

- [ ] **Step 1: Reproduce the Windows wrapper failure (RED)**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.GPURendererScenesModuleBoundaryTest.check task graph does not include opt in render tasks" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: nested process creation fails with `Cannot run program "./gradlew"`.

- [ ] **Step 2: Add native wrapper resolution**

Add:

```kotlin
private fun gradleWrapper(repoRoot: Path): Path {
    val wrapperName = if (
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    ) {
        "gradlew.bat"
    } else {
        "gradlew"
    }
    return repoRoot.resolve(wrapperName).also { wrapper ->
        check(Files.isRegularFile(wrapper)) { "Gradle wrapper is missing: $wrapper" }
    }
}
```

- [ ] **Step 3: Use the absolute wrapper path**

Replace the start of `runGradleDryRun` with:

```kotlin
val output = StringBuilder()
val root = repoRoot()
val process = ProcessBuilder(
    gradleWrapper(root).toAbsolutePath().toString(),
    "--no-daemon",
    task,
    "--dry-run",
)
    .directory(root.toFile())
```

Keep timeout, output collection, exit-code handling, and graph assertions unchanged.

- [ ] **Step 4: Prove wrapper GREEN**

Rerun Step 1's command.

Expected: pass; nested `gradlew.bat --dry-run` includes `:gpu-renderer-scenes:check` and excludes all three opt-in tasks.

- [ ] **Step 5: Commit wrapper portability**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/GPURendererScenesModuleBoundaryTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "test(scenes): use the native Gradle wrapper"
```

### Task 5: Assert offscreen diagnostics and one-LSB parity semantically

**Files:**

- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RenderGpuRendererSceneOffscreenMainTest.kt:169-260`
- Modify: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RenderGpuRendererSceneOffscreenMainTest.kt` private helper region

**Interfaces:**

- Consumes: `List<String>` diagnostics containing `preparedCaches`, `withinOneLsb`, and `maxChannelDelta` fields.
- Produces: `assertWithinOneLsb(diagnostics: List<String>, prefix: String, expectedPixels: Int = 64_000)`.

- [ ] **Step 1: Reproduce the four offscreen failures (RED)**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.solid frame sampler measures completion only and performs one final readback" --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.color matrix uses one prepared submit and matches the independent row-major reference" --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.gaussian blur photo uses three prepared passes in one submit" --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.registered runtime effect uses the generic prepared submit without source in the frame plan" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: sampler fails on obsolete exact collection membership; color matrix, blur, and SimpleRT report full one-LSB coverage with `maxChannelDelta=1` but fail stricter prefix/exact/zero assertions.

- [ ] **Step 2: Add a strict semantic one-LSB helper**

Add:

```kotlin
private fun assertWithinOneLsb(
    diagnostics: List<String>,
    prefix: String,
    expectedPixels: Int = 64_000,
) {
    val diagnostic = diagnostics.singleOrNull { it.startsWith(prefix) }
    assertTrue(
        diagnostic != null,
        "Expected one diagnostic starting with '$prefix', got: $diagnostics",
    )
    assertContains(
        diagnostic,
        "withinOneLsb=$expectedPixels/$expectedPixels",
        message = "One-LSB coverage must include every pixel",
    )
    val maxChannelDelta = Regex("""(?:^|\s)maxChannelDelta=(\d+)(?:\s|$)""")
        .find(diagnostic)
        ?.groupValues
        ?.get(1)
        ?.toInt()
    assertTrue(
        maxChannelDelta != null && maxChannelDelta <= 1,
        "Expected maxChannelDelta <= 1, got '$diagnostic'",
    )
}
```

- [ ] **Step 3: Replace only the non-portable assertions**

For SimpleRT and color matrix call:

```kotlin
assertWithinOneLsb(report.diagnostics, "registeredUniform:withinOneLsb=")
```

For blur remove the `pixelExact=64000/64000` gate and call:

```kotlin
assertWithinOneLsb(report.diagnostics, "separableBlur:withinOneLsb=")
```

For the sampler replace the obsolete exact element with:

```kotlin
assertTrue(
    report.diagnostics.any { diagnostic ->
        diagnostic.contains("preparedCaches solid=1/3") &&
            diagnostic.contains("registered=0/0")
    },
    "Expected semantic prepared-cache creation/reuse facts, got: ${report.diagnostics}",
)
```

Keep the independent CPU reference, route, program, no-WGSL-source, encoder, command-buffer, submit, readback, and cache-invariant assertions.

- [ ] **Step 4: Prove focused offscreen GREEN**

Rerun Step 1's command.

Expected: all four pass on the current NVIDIA adapter; a missing diagnostic, partial coverage, malformed delta, or `maxChannelDelta > 1` remains red.

- [ ] **Step 5: Run the complete offscreen test class**

Run:

```powershell
.\gradlew.bat :gpu-renderer-scenes:test --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: the class passes, subject only to its existing explicit `webgpu-context-unavailable` skip behavior.

- [ ] **Step 6: Commit semantic diagnostic policy**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RenderGpuRendererSceneOffscreenMainTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "test(scenes): enforce portable diagnostic policies"
```

### Task 6: Verify FP-03 and advance the ordered backlog

**Files:**

- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Verify: JUnit XML under `codec/jpeg2000/build/test-results/test/`
- Verify: JUnit XML under `codec/jpegxl/build/test-results/test/`
- Verify: JUnit XML under `font/scaler/build/test-results/test/`
- Verify: JUnit XML under `gpu-renderer/build/test-results/test/`
- Verify: JUnit XML under `gpu-renderer-scenes/build/test-results/test/`

**Interfaces:**

- Consumes: the five reviewed portability commits and their focused evidence.
- Produces: FP-03 `completed`, FP-04 `in_progress`, and a factual Windows validation record.

- [ ] **Step 1: Run the complete affected validation serially**

Run these commands one at a time:

```powershell
.\gradlew.bat :codec:jpeg2000:test :codec:jpegxl:test :font:scaler:test --dependency-verification=off --no-daemon --console=plain --rerun-tasks
.\gradlew.bat :gpu-renderer:test :gpu-renderer-scenes:test --dependency-verification=off --no-daemon --console=plain --rerun-tasks
```

Expected: both commands end `BUILD SUCCESSFUL`; no test failure remains from the twenty FP-03 cases.

- [ ] **Step 2: Verify scope and protected files**

Run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas diff --check
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas status --short --branch
```

Expected: no whitespace error; only `buildSrc/build.gradle.kts` and `gradle/verification-metadata.xml` remain modified and unstaged before the backlog edit.

- [ ] **Step 3: Record exact resolution evidence**

In FP-03:

- set `Status: completed`;
- retain the original six plus added fourteen failure bullets as historical resolution inputs;
- add a `Resolution evidence` block listing the five implementation commits, the two full verification commands, zero failing tests, the five `attr/-text w/lf` PGM contracts, all 105 font tests, the EOL-independent materializer inspection, native wrapper dry run, semantic prepared-cache facts, and strict full-coverage `maxChannelDelta <= 1` policy.

In FP-04 set `Status: in_progress`. Leave FP-09 and all later goals unchanged.

- [ ] **Step 4: Verify the ordered status invariant**

Run:

```powershell
$activeTodo = Get-Content -Raw 'reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md'
if (($activeTodo | Select-String -Pattern 'Status: `in_progress`' -AllMatches).Matches.Count -ne 1) { throw 'Expected exactly one in_progress item' }
if ($activeTodo -notmatch '(?s)### FP-03.+?Status: `completed`') { throw 'FP-03 must be completed' }
if ($activeTodo -notmatch '(?s)### FP-04.+?Status: `in_progress`') { throw 'FP-04 must be in progress' }
```

Expected: no exception.

- [ ] **Step 5: Commit the FP-03 closeout**

Controller-only run:

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "docs: complete Windows test portability"
```

Expected: the protected local changes remain unstaged.

### Task 7: Review the complete FP-03 result

**Files:**

- Review: the FP-03 commit range starting after `802bb02be`
- Review: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Review: focused and complete validation reports

**Interfaces:**

- Consumes: Tasks 1-6 and their review reports.
- Produces: a severity-ranked final verdict and a binary decision on readiness to begin FP-04.

- [ ] **Step 1: Audit code and repository state**

Confirm:

- only narrow test/config files plus the active backlog changed;
- all five PGM blobs retain their original expected hashes and no loader/hash assertion was weakened;
- canonical JSON comparison changes only line terminators;
- source inspection is anchored and brace-balanced;
- the wrapper is selected by OS and invoked without a shell;
- all three prepared comparisons require full one-LSB coverage and `maxChannelDelta <= 1`;
- no renderer/WGSL/CPU reference/codec/font production code changed;
- protected user files remain unstaged.

- [ ] **Step 2: Audit tests and claims**

Confirm every RED was observed before its GREEN change, both complete commands pass, FP-03 is `completed`, FP-04 is the sole `in_progress` item, and no claim expands beyond Windows portability.

- [ ] **Step 3: Produce the final verdict**

Report Critical, Important, and Minor findings with file/line evidence. State separately:

- `FP-03 ready to proceed to FP-04: Yes/No`
- `Overall branch ready to merge: Yes/No`

The overall branch remains not ready while FP-04 through FP-11 are active.

# GPU Capabilities DDD Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace free string texture/usage/feature capability fields with typed GPU abstraction concepts and Kanvas-owned domain enums where the abstraction has no matching concept.

**Architecture:** `GPUCapabilities` remains the renderer capability snapshot, but texture formats/usages come from the GPU abstraction layer instead of local string labels. Renderer-only capabilities are promoted to a small Kanvas enum so diagnostics still use stable public dump labels without keeping untyped strings in the contract.

**Tech Stack:** Kotlin/JVM, `:gpu-renderer`, GPU abstraction enums already imported by the native runtime, Gradle tests, Skia GM dashboard validation.

---

## File Structure

- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
  - Own `GPUCapabilities`, typed validation helpers, dump-label formatters, and the new `GPURendererFeature` enum.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`
  - Drive the migration with typed tests first.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
  - Fill runtime capabilities with abstraction-layer enums already used by resource creation.
- Modify fixture-heavy tests that construct `GPUCapabilities` directly:
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshotTest.kt`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextA8RouteAcceptanceTest.kt`
- Do not modify `GPUImplementationIdentity` beyond KDoc if a test needs the identity/capability boundary documented.
- Do not touch generated PNGs unless a visual diff investigation explicitly requires it.

## Domain Boundary Decision

Typed replacement rules:

- `supportedTextureFormats: Set<String>` becomes `supportedTextureFormats: Set<GPUTextureFormat>`.
- `supportedTextureUsageLabels: Set<String>` becomes `supportedTextureUsage: GPUTextureUsage?`.
- `featureLabels: Set<String>` becomes `rendererFeatures: Set<GPURendererFeature>`.
- Add optional `adapterFeatures: Set<GPUFeatureName>` only if a code path needs real GPU optional features during this migration. Do not force renderer-only concepts into `GPUFeatureName`.

`GPUTextureUsage? = null` means "unknown, do not block". This preserves the previous empty-set behavior. A non-null value means the snapshot has observed usage support and validation must reject missing bits.

---

### Task 1: Add Typed Dump Helpers And Renderer Feature Tests

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`

- [ ] **Step 1: Write failing tests for canonical dump labels**

Add imports at the top of `GPUCapabilityContractsTest.kt` for
`GPUTextureFormat` and `GPUTextureUsage` from the same GPU abstraction package
already imported by `GPUBackendRuntimeNative.kt`. To find the exact package,
run:

```bash
rtk rg -n "import .*GPUTextureFormat|import .*GPUTextureUsage" \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt
```

Then add this test inside `GPUCapabilityContractsTest`:

```kotlin
    @Test
    fun `GPU abstraction labels dump to stable public strings`() {
        assertEquals("rgba8unorm", GPUTextureFormat.RGBA8Unorm.dumpLabel())
        assertEquals("rgba8unorm-srgb", GPUTextureFormat.RGBA8UnormSrgb.dumpLabel())
        assertEquals("bgra8unorm", GPUTextureFormat.BGRA8Unorm.dumpLabel())
        assertEquals("bgra8unorm-srgb", GPUTextureFormat.BGRA8UnormSrgb.dumpLabel())
        assertEquals("depth24plus-stencil8", GPUTextureFormat.Depth24PlusStencil8.dumpLabel())

        val usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding or GPUTextureUsage.RenderAttachment
        assertEquals(
            listOf("copy_dst", "texture_binding", "render_attachment"),
            usage.dumpLabels(),
        )

        assertEquals("texture-sampling", GPURendererFeature.TextureSampling.dumpLabel)
        assertEquals("uniform-buffer", GPURendererFeature.UniformBuffer.dumpLabel)
    }
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest
```

Expected:

- Build fails because `dumpLabel`, `dumpLabels`, and `GPURendererFeature` do not exist yet.

- [ ] **Step 3: Add the minimal typed helpers**

In `CapabilityContracts.kt`, add imports after the package line for
`GPUTextureFormat` and `GPUTextureUsage` from the same GPU abstraction package
already imported by `GPUBackendRuntimeNative.kt`.

```bash
rtk rg -n "import .*GPUTextureFormat|import .*GPUTextureUsage" \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt
```

Add this enum and helpers before `GPUCapabilities`:

```kotlin
/** Renderer-owned feature gates that do not correspond to GPU optional feature names. */
enum class GPURendererFeature(val dumpLabel: String) {
    RenderPass("render-pass"),
    CopyUpload("copy-upload"),
    Readback("readback"),
    UniformBuffer("uniform-buffer"),
    TextureSampling("texture-sampling"),
}

/** Stable dump label for GPU texture formats used in diagnostics and snapshots. */
fun GPUTextureFormat.dumpLabel(): String =
    when (this) {
        GPUTextureFormat.RGBA8Unorm -> "rgba8unorm"
        GPUTextureFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
        GPUTextureFormat.BGRA8Unorm -> "bgra8unorm"
        GPUTextureFormat.BGRA8UnormSrgb -> "bgra8unorm-srgb"
        GPUTextureFormat.R8Unorm -> "r8unorm"
        GPUTextureFormat.Depth24PlusStencil8 -> "depth24plus-stencil8"
        else -> name.lowercase()
    }

/** Returns stable public usage labels in deterministic order. */
fun GPUTextureUsage.dumpLabels(): List<String> =
    buildList {
        if (containsUsage(GPUTextureUsage.CopySrc)) add("copy_src")
        if (containsUsage(GPUTextureUsage.CopyDst)) add("copy_dst")
        if (containsUsage(GPUTextureUsage.TextureBinding)) add("texture_binding")
        if (containsUsage(GPUTextureUsage.StorageBinding)) add("storage_binding")
        if (containsUsage(GPUTextureUsage.RenderAttachment)) add("render_attachment")
    }

private fun GPUTextureUsage.containsUsage(required: GPUTextureUsage): Boolean =
    (value and required.value) == required.value
```

- [ ] **Step 4: Run the targeted test and confirm the new helper test passes or reaches subsequent failures**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest
```

Expected:

- The helper test passes.
- Existing tests may still pass because the old string fields still exist.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt
rtk git commit -m "Add typed GPU capability dump helpers"
```

---

### Task 2: Migrate GPUCapabilities Contract To Typed Fields

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`

- [ ] **Step 1: Replace the capability tests with typed construction**

In `GPUCapabilityContractsTest.kt`, update the test named `GPU capabilities validate texture format usage size and uniform alignment` so the capability construction and validation use typed values:

```kotlin
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
            supportedTextureUsage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding or GPUTextureUsage.RenderAttachment,
            rendererFeatures = setOf(GPURendererFeature.TextureSampling, GPURendererFeature.UniformBuffer),
```

Update the assertions in the same test:

```kotlin
        assertEquals(
            null,
            capabilities.validateTextureRequest(
                GPUTextureFormat.RGBA8Unorm,
                128,
                64,
                GPUTextureUsage.TextureBinding,
            ),
        )
        assertEquals(null, capabilities.validateUniformAlignment(512))
        assertEquals(
            "unsupported.capability.texture_format",
            capabilities.validateTextureRequest(
                GPUTextureFormat.BGRA8Unorm,
                128,
                64,
                GPUTextureUsage.TextureBinding,
            )?.code,
        )
        assertEquals(
            "unsupported.capability.texture_usage",
            capabilities.validateTextureRequest(
                GPUTextureFormat.RGBA8Unorm,
                128,
                64,
                GPUTextureUsage.StorageBinding,
            )?.code,
        )
        assertEquals(
            "unsupported.capability.texture_size",
            capabilities.validateTextureRequest(
                GPUTextureFormat.RGBA8Unorm,
                4097,
                64,
                GPUTextureUsage.TextureBinding,
            )?.code,
        )
        assertEquals(
            "unsupported.capability.uniform_alignment",
            capabilities.validateUniformAlignment(128)?.code,
        )
        assertEquals(
            "unsupported.capability.feature",
            capabilities.validateRendererFeature(GPURendererFeature.Readback)?.code,
        )
```

Update `GPU capabilities allow finer uniform alignment when observed limits allow it`:

```kotlin
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
            supportedTextureUsage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
            rendererFeatures = setOf(GPURendererFeature.UniformBuffer),
```

Update `GPU capabilities treat unknown supported usage labels as non-blocking` and rename it to `GPU capabilities treat unknown supported texture usage as non-blocking`:

```kotlin
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
```

and:

```kotlin
            capabilities.validateTextureRequest(
                format = GPUTextureFormat.RGBA8Unorm,
                width = 128,
                height = 64,
                usage = GPUTextureUsage.TextureBinding,
            ),
```

Replace `GPU capabilities validate nonblank snapshot and public labels` with:

```kotlin
    @Test
    fun `GPU capabilities validate nonblank snapshot`() {
        val implementation = GPUImplementationIdentity(
            facadeName = "GPU",
            implementationName = "native",
            adapterName = "unit-adapter",
            deviceName = "unit-device",
        )

        assertFailsWith<IllegalArgumentException> {
            GPUCapabilities(
                implementation = implementation,
                facts = emptyList(),
                snapshotId = "",
            )
        }
    }
```

- [ ] **Step 2: Run the test and confirm it fails at compile time**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest
```

Expected:

- Compile fails because `GPUCapabilities.supportedTextureUsage`, `rendererFeatures`, `validateRendererFeature`, and typed `validateTextureRequest` are not implemented yet.

- [ ] **Step 3: Replace fields and validators in CapabilityContracts**

In `CapabilityContracts.kt`, replace `GPUCapabilities` with:

```kotlin
/** Capability snapshot for the selected GPU facade implementation. */
data class GPUCapabilities(
    val implementation: GPUImplementationIdentity,
    val facts: List<GPUCapabilityFact>,
    val knownUnsupportedFacts: List<GPUCapabilityFact> = emptyList(),
    val snapshotId: String,
    val limits: GPULimits? = null,
    val supportedTextureFormats: Set<GPUTextureFormat> = emptySet(),
    val supportedTextureUsage: GPUTextureUsage? = null,
    val rendererFeatures: Set<GPURendererFeature> = emptySet(),
) {
    init {
        require(snapshotId.isNotBlank()) { "GPUCapabilities.snapshotId must not be blank" }
    }
}
```

Replace `validateTextureRequest` with:

```kotlin
/** Validates a texture allocation request against known format, usage, and size capabilities. */
fun GPUCapabilities.validateTextureRequest(
    format: GPUTextureFormat,
    width: Int,
    height: Int,
    usage: GPUTextureUsage,
): GPUCapabilityDiagnostic? {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }

    if (supportedTextureFormats.isNotEmpty() && format !in supportedTextureFormats) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_format",
            severity = "error",
            requirementName = "texture.format",
            required = format.dumpLabel(),
            observed = supportedTextureFormats.map { it.dumpLabel() }.sorted().joinToString(","),
            isTerminal = true,
        )
    }

    val supportedUsage = supportedTextureUsage
    if (supportedUsage != null && !supportedUsage.containsUsage(usage)) {
        val missingUsage = usage.missingUsageLabelsFrom(supportedUsage)
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_usage",
            severity = "error",
            requirementName = "texture.usage",
            required = missingUsage.joinToString(","),
            observed = supportedUsage.dumpLabels().joinToString(","),
            isTerminal = true,
        )
    }

    val maxTextureDimension2D = limits?.maxTextureDimension2D
    if (maxTextureDimension2D != null && (width.toLong() > maxTextureDimension2D || height.toLong() > maxTextureDimension2D)) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_size",
            severity = "error",
            requirementName = "texture.maxTextureDimension2D",
            required = maxOf(width, height).toString(),
            observed = maxTextureDimension2D.toString(),
            isTerminal = true,
        )
    }

    return null
}
```

Add this helper below `containsUsage`:

```kotlin
private fun GPUTextureUsage.missingUsageLabelsFrom(supported: GPUTextureUsage): List<String> =
    buildList {
        if (containsUsage(GPUTextureUsage.CopySrc) && !supported.containsUsage(GPUTextureUsage.CopySrc)) add("copy_src")
        if (containsUsage(GPUTextureUsage.CopyDst) && !supported.containsUsage(GPUTextureUsage.CopyDst)) add("copy_dst")
        if (containsUsage(GPUTextureUsage.TextureBinding) && !supported.containsUsage(GPUTextureUsage.TextureBinding)) add("texture_binding")
        if (containsUsage(GPUTextureUsage.StorageBinding) && !supported.containsUsage(GPUTextureUsage.StorageBinding)) add("storage_binding")
        if (containsUsage(GPUTextureUsage.RenderAttachment) && !supported.containsUsage(GPUTextureUsage.RenderAttachment)) add("render_attachment")
    }
```

Replace `validateFeature` with:

```kotlin
/** Validates that a named renderer feature is present when the snapshot has renderer feature evidence. */
fun GPUCapabilities.validateRendererFeature(feature: GPURendererFeature): GPUCapabilityDiagnostic? {
    if (rendererFeatures.isEmpty() || feature in rendererFeatures) {
        return null
    }

    return GPUCapabilityDiagnostic(
        code = "unsupported.capability.feature",
        severity = "error",
        requirementName = "feature",
        required = feature.dumpLabel,
        observed = rendererFeatures.map { it.dumpLabel }.sorted().joinToString(","),
        isTerminal = true,
    )
}
```

- [ ] **Step 4: Run the targeted capability tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest
```

Expected:

- `GPUCapabilityContractsTest` passes.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt
rtk git commit -m "Type GPU capability contracts"
```

---

### Task 3: Migrate Runtime Capability Snapshot

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshotTest.kt`

- [ ] **Step 1: Update runtime capabilities to typed fields**

In `GPUBackendRuntimeNative.kt`, add import:

```kotlin
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
```

Then replace the string fields inside `override val capabilities`:

```kotlin
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.BGRA8Unorm),
            supportedTextureUsage = GPUTextureUsage.CopySrc or
                GPUTextureUsage.CopyDst or
                GPUTextureUsage.TextureBinding or
                GPUTextureUsage.RenderAttachment,
            rendererFeatures = setOf(
                GPURendererFeature.RenderPass,
                GPURendererFeature.CopyUpload,
                GPURendererFeature.Readback,
                GPURendererFeature.UniformBuffer,
                GPURendererFeature.TextureSampling,
            ),
```

- [ ] **Step 2: Update baseline snapshot test expectations if compilation requires it**

Open `GPURuntimeBaselineSnapshotTest.kt`. If its fake `GPUCapabilities` construction does not specify texture fields, leave it unchanged. If it references the old fields, convert them using:

```kotlin
supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
supportedTextureUsage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
rendererFeatures = setOf(GPURendererFeature.TextureSampling),
```

Add imports only if needed. For `GPUTextureFormat` and `GPUTextureUsage`, copy
the exact GPU abstraction package from `GPUBackendRuntimeNative.kt`. For
`GPURendererFeature`, use the renderer capabilities package.

```kotlin
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
```

- [ ] **Step 3: Run compile and baseline snapshot tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeBaselineSnapshotTest
```

Expected:

- The runtime and baseline snapshot test compile.
- `GPURuntimeBaselineSnapshotTest` passes.

- [ ] **Step 4: Commit Task 3**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshotTest.kt
rtk git commit -m "Use typed GPU capabilities in runtime snapshot"
```

If `GPURuntimeBaselineSnapshotTest.kt` was not modified, omit it from `git add`.

---

### Task 4: Migrate Remaining Capability Fixtures

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextA8RouteAcceptanceTest.kt`
- Modify any other compile error file reported by `:gpu-renderer:compileTestKotlin`

- [ ] **Step 1: Run compile to list old constructor usage**

Run:

```bash
rtk ./gradlew :gpu-renderer:compileTestKotlin
```

Expected:

- If any old fields remain, compile fails with references to `supportedTextureUsageLabels`, `featureLabels`, or string `validateTextureRequest`.
- If it passes, continue to Step 4.

- [ ] **Step 2: Convert old string constructor fields**

For each remaining `GPUCapabilities(...)` constructor:

Replace:

```kotlin
supportedTextureFormats = setOf("rgba8unorm"),
supportedTextureUsageLabels = setOf("copy_dst", "texture_binding", "render_attachment"),
featureLabels = setOf("texture-sampling", "uniform-buffer"),
```

with:

```kotlin
supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
supportedTextureUsage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding or GPUTextureUsage.RenderAttachment,
rendererFeatures = setOf(GPURendererFeature.TextureSampling, GPURendererFeature.UniformBuffer),
```

Use this mapping:

| Old label | New typed value |
| --- | --- |
| `"rgba8unorm"` | `GPUTextureFormat.RGBA8Unorm` |
| `"rgba8unorm-srgb"` | `GPUTextureFormat.RGBA8UnormSrgb` |
| `"bgra8unorm"` | `GPUTextureFormat.BGRA8Unorm` |
| `"bgra8unorm-srgb"` | `GPUTextureFormat.BGRA8UnormSrgb` |
| `"copy_src"` | `GPUTextureUsage.CopySrc` |
| `"copy_dst"` | `GPUTextureUsage.CopyDst` |
| `"texture_binding"` | `GPUTextureUsage.TextureBinding` |
| `"storage_binding"` | `GPUTextureUsage.StorageBinding` |
| `"render_attachment"` | `GPUTextureUsage.RenderAttachment` |
| `"render-pass"` | `GPURendererFeature.RenderPass` |
| `"copy-upload"` | `GPURendererFeature.CopyUpload` |
| `"readback"` | `GPURendererFeature.Readback` |
| `"uniform-buffer"` | `GPURendererFeature.UniformBuffer` |
| `"texture-sampling"` | `GPURendererFeature.TextureSampling` |

- [ ] **Step 3: Add imports where needed**

Add only the imports used by each file. For `GPUTextureFormat` and
`GPUTextureUsage`, copy the exact GPU abstraction package from
`GPUBackendRuntimeNative.kt`.

```kotlin
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
```

- [ ] **Step 4: Run compile again**

Run:

```bash
rtk ./gradlew :gpu-renderer:compileTestKotlin
```

Expected:

- Compile succeeds.

- [ ] **Step 5: Run targeted tests for migrated fixtures**

Run:

```bash
rtk ./gradlew :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest \
  --tests org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest \
  --tests org.graphiks.kanvas.gpu.renderer.text.GPUTextA8RouteAcceptanceTest
```

Expected:

- All targeted tests pass.

- [ ] **Step 6: Commit Task 4**

Run:

```bash
rtk git add gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextA8RouteAcceptanceTest.kt
rtk git commit -m "Migrate GPU capability fixtures to typed values"
```

Only stage files actually changed.

---

### Task 5: Add Drift Audit For Capability Strings

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`
  - If this file is not the right home after inspection, add the audit to the existing package-boundary or layout-surface test that already scans production source.

- [ ] **Step 1: Locate the existing production-source scan helper**

Run:

```bash
rtk rg -n "production source|forbidden imports|package boundary|rg|source" gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt
```

Expected:

- Find the helper that enumerates production `.kt` files.

- [ ] **Step 2: Add a failing audit for old capability fields**

Add a test near the other source audits:

```kotlin
    @Test
    fun `gpu capabilities do not reintroduce stringly typed GPU spec concepts`() {
        val capabilitySource = productionFile("capabilities/CapabilityContracts.kt").readText()

        assertFalse(
            capabilitySource.contains("supportedTextureFormats: Set<String>"),
            "GPUCapabilities.supportedTextureFormats must use GPUTextureFormat, not String",
        )
        assertFalse(
            capabilitySource.contains("supportedTextureUsageLabels: Set<String>"),
            "GPUCapabilities.supportedTextureUsage must use GPUTextureUsage, not String labels",
        )
        assertFalse(
            capabilitySource.contains("featureLabels: Set<String>"),
            "Renderer feature gates must use GPURendererFeature, not String labels",
        )
    }
```

If the file uses a different assertion style, adapt only the helper names and keep the exact three checks.

- [ ] **Step 3: Run the audit test**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
```

Expected:

- Passes after Tasks 2-4.

- [ ] **Step 4: Commit Task 5**

Run:

```bash
rtk git add gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt
rtk git commit -m "Audit GPU capability type boundaries"
```

---

### Task 6: Full Verification And GM Support Check

**Files:**
- No intended source changes.
- Generated dashboard artifacts under `integration-tests/skia/build/reports/skia-gm-dashboard/` are build outputs.
- Do not commit PNG changes.

- [ ] **Step 1: Run full renderer test suite**

Run:

```bash
rtk ./gradlew :kanvas:compileKotlin :gpu-renderer:test
```

Expected:

- Build successful.
- All `:gpu-renderer:test` tests pass.

- [ ] **Step 2: Regenerate a GM smoke scan**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'
```

Expected:

- `PASS=8 FAIL=0 TIMEOUT=0`.

- [ ] **Step 3: Regenerate full dashboard**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
```

Expected:

- Build successful.
- Current expected summary before this migration was:
  - `Total: 517`
  - `Pass: 422`
  - `Fail: 13`
  - `No score: 82`
  - support on total: `81.6248%`
  - support on scored GMs: `97.0115%`

- [ ] **Step 4: Compare support percentage against merge-base or pre-change JSON**

If no baseline worktree exists, create one:

```bash
base=$(git merge-base HEAD origin/master)
tmp=/tmp/kanvas-support-baseline-ddd
if [ -d "$tmp" ]; then git worktree remove --force "$tmp"; fi
git worktree add --detach "$tmp" "$base"
(
  cd "$tmp"
  rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
)
```

Then run this comparison from the feature worktree:

```bash
rtk python3 - <<'PY'
import json
from pathlib import Path

head_path = Path('integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json')
base_path = Path('/tmp/kanvas-support-baseline-ddd/integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json')

def load(path):
    data = json.loads(path.read_text())
    rows = data['gms']
    by_key = {(row['family'], row['name']): row for row in rows}
    passed = sum(1 for row in rows if row.get('isPassing'))
    no_score = sum(
        1 for row in rows
        if row.get('noReference') or row.get('renderFailed') or row.get('sizeMismatch') or row.get('similarity') is None
    )
    failed = len(rows) - passed - no_score
    return by_key, {'total': len(rows), 'pass': passed, 'fail': failed, 'noScore': no_score}

head, head_counts = load(head_path)
base, base_counts = load(base_path)
print('base_counts', base_counts)
print('head_counts', head_counts)
for label, counts in [('base', base_counts), ('head', head_counts)]:
    total = counts['total']
    scored = counts['pass'] + counts['fail']
    print(f"{label}_support_total_pct={counts['pass'] / total * 100:.4f}")
    print(f"{label}_support_scored_pct={counts['pass'] / scored * 100:.4f}")

status_changes = []
for key in sorted(set(base) & set(head)):
    def status(row):
        if row.get('isPassing'):
            return 'pass'
        if row.get('noReference') or row.get('renderFailed') or row.get('sizeMismatch') or row.get('similarity') is None:
            return 'noScore'
        return 'fail'
    old_status = status(base[key])
    new_status = status(head[key])
    if old_status != new_status:
        status_changes.append((key, old_status, new_status))

print('missing', len(set(base) - set(head)))
print('added', len(set(head) - set(base)))
print('status_changes', len(status_changes))
if base_counts != head_counts or status_changes or set(base) != set(head):
    raise SystemExit(1)
PY
```

Expected:

- Counts match.
- `status_changes 0`.

- [ ] **Step 5: Clean generated dirty PNGs**

Run:

```bash
rtk git status --short
```

If `integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png` or another generated render is dirty only because of dashboard regeneration, restore it:

```bash
rtk git restore -- integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png
```

Then remove the baseline worktree if it was created:

```bash
if [ -d /tmp/kanvas-support-baseline-ddd ]; then
  git -C /tmp/kanvas-support-baseline-ddd restore -- integration-tests/skia/src/test/resources/generated-renders/composite/graphite-replay.png || true
  git worktree remove /tmp/kanvas-support-baseline-ddd
  git worktree prune
fi
```

- [ ] **Step 6: Run final source audits**

Run:

```bash
rtk git diff --check
rtk git status -sb
rtk python3 - <<'PY'
from pathlib import Path
import subprocess

base = subprocess.check_output(['git', 'merge-base', 'HEAD', 'origin/master'], text=True).strip()
files = subprocess.check_output(['git', 'diff', '--name-only', f'{base}..HEAD', '--', '*.kt', '*.kts', '*.md'], text=True).splitlines()
needle = ('w' + 'gpu').lower()
allowed = {'gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt'}
hits = []
for file_name in files:
    if file_name in allowed or not Path(file_name).exists():
        continue
    for line_number, line in enumerate(Path(file_name).read_text(errors='ignore').splitlines(), 1):
        if needle in line.lower():
            hits.append((file_name, line_number, line.strip()))
if hits:
    for file_name, line_number, line in hits:
        print(f'{file_name}:{line_number}:{line}')
    raise SystemExit(1)
print(f'checked {len(files)} changed Kotlin/Markdown files outside native runtime')
PY
```

Expected:

- No whitespace errors.
- Worktree clean except intentional committed changes before final push.
- No public wording regression outside the native runtime file that contains unavoidable low-level API imports.

- [ ] **Step 7: Commit any final test/audit fix**

If Task 6 required source fixes, commit them:

```bash
rtk git add <changed-source-files>
rtk git commit -m "Verify typed GPU capability migration"
```

If only generated artifacts changed and were restored, do not commit.

---

## Expected Commit Shape

1. `Add typed GPU capability dump helpers`
2. `Type GPU capability contracts`
3. `Use typed GPU capabilities in runtime snapshot`
4. `Migrate GPU capability fixtures to typed values`
5. `Audit GPU capability type boundaries`
6. Optional verification fix commit only if needed

## Acceptance Checklist

- [ ] `GPUCapabilities.supportedTextureFormats` uses `Set<GPUTextureFormat>`.
- [ ] `GPUCapabilities.supportedTextureUsage` uses `GPUTextureUsage?`.
- [ ] Renderer-only feature gates use `GPURendererFeature`, not strings.
- [ ] Texture diagnostics still dump `rgba8unorm`, `bgra8unorm`, and usage labels such as `texture_binding`.
- [ ] `GPUImplementationIdentity` remains identity-only.
- [ ] `:kanvas:compileKotlin :gpu-renderer:test` passes.
- [ ] GM support percentage is unchanged from baseline.
- [ ] No generated PNG is committed.

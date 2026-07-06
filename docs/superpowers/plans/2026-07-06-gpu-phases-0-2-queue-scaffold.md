# GPU Phases 0-2 Queue Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Terminer les phases 0 a 2 du refactor GPU et poser un echafaudage teste de queue/lifetime phase 3 dans une seule PR.

**Architecture:** La PR avance par gates: frontieres de packages, baseline runtime, capabilities, provider concret limite, puis queue manager minimal. Les composants restent renderer-owned, dumpables sans handle brut, et exposes avec un vocabulaire public strictement `GPU`.

**Tech Stack:** Kotlin/JVM, Gradle, `kotlin.test`, module `:gpu-renderer`, generation shader WGSL, integration dashboard Skia GM.

---

## Regles De Session

- Une seule PR, plusieurs commits.
- Chaque gate doit compiler et avoir son test cible avant commit.
- Le wording public des nouveaux noms, diagnostics, dumps, tests et docs touches doit dire `GPU`, jamais le nom de l'implementation concrete.
- Les imports et appels de bibliotheque du runtime concret restent internes au fichier runtime concret; ils ne doivent pas se retrouver dans les labels, dumps, comments publics, noms de tests, noms de fichiers generiques ou rapports.
- Aucun PNG regenere ne doit etre commit sans evidence visuelle explicite.
- Pas de port Ganesh/Graphite, pas de compilateur SkSL dynamique, pas de batching generalise dans cette PR.

## File Structure

### Create

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskFilterContracts.kt`
  - Contrats mask-filter partages, afin de casser le cycle `commands <-> filters`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/ColorGlyphCompositeSnippet.kt`
  - Source shader COLRv0 reutilisable par `text` et `execution`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshot.kt`
  - Snapshot phase 0 construit depuis une session runtime, telemetry, caches et capabilities.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshotTest.kt`
  - Tests du dump phase 0 et de son wording public.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
  - Provider concret limite, avec caches evidence-only pour uniforms, null buffer, bind group et texture/sampler.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`
  - Tests create/reuse/refuse/stale-generation du provider.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
  - Queue manager minimal: submission id, retention, completion, release, telemetry.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`
  - Tests retention/release/telemetry.

### Rename

- Runtime concret actuel vers `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Smoke test runtime concret actuel vers `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

### Modify

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
  - Supprimer les contrats mask-filter maintenant detenus par `filters`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/BlurFilter.kt`
  - Utiliser `NormalizedBlurStyle` depuis le package local `filters`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GradientWgslShaderProvider.kt`
  - Deplacer le provider gradient hors de `wgsl`, car il consomme `GPUMaterialDescriptor`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WgslReflection.kt`
  - Corriger le package root sous `org.graphiks.kanvas.gpu.renderer.wgsl`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/*.kt`
  - Mettre a jour les imports de reflection WGSL.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/GPUColorWgsl.kt`
  - Mettre a jour les imports de reflection WGSL.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GPUComputeTessellationPlan.kt`
  - Mettre a jour les imports de reflection WGSL.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLModuleAssembler.kt`
  - Mettre a jour l'import de `reflectWgslModule`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
  - Deleguer vers `GPUBackendRuntimeNativeFactory` et nettoyer les comments publics.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
  - Enrichir limits/features/formats et helpers de diagnostics.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`
  - Couvrir alignements, format, usage, taille, feature.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`
  - Garder les fixtures structurelles; ajuster seulement les assertions si un message public generique change.
- `reports/gpu-renderer/phase-0-baseline.md`
  - Mettre a jour la baseline pour pointer vers le snapshot runtime, sans transformer les follow-ups en criteres caches.

---

## Task 1: Gate 0 Package Boundary And Public Wording

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskFilterContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/ColorGlyphCompositeSnippet.kt`
- Rename: current concrete runtime file to `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Rename: current concrete runtime smoke test to `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/BlurFilter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUColorGlyphCompositeShader.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WgslReflection.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasWGSLReflectionProvider.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/GPUEffectKinds.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasCustomRuntimeEffectRegistry.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/GPUShaderGraph.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/GPUColorWgsl.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/compute/GPUComputeTessellationPlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLModuleAssembler.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`

- [ ] **Step 1: Confirm the current structural failure**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
```

Expected: FAIL on `gpu renderer production source satisfies package boundary rules` with the known package-boundary violations.

- [ ] **Step 2: Extract mask-filter contracts into `filters`**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskFilterContracts.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.filters

/** Blur style for normalized mask filters, mirrored from Kanvas BlurStyle. */
enum class NormalizedBlurStyle { NORMAL, SOLID, OUTER, INNER }

/** Normalized mask filter descriptor captured before route analysis. */
sealed interface NormalizedMaskFilter {
    /** Gaussian blur mask filter with style and sigma parameters. */
    data class Blur(val style: NormalizedBlurStyle, val sigma: Float) : NormalizedMaskFilter {
        init {
            require(sigma >= 0f && sigma.isFinite()) {
                "Blur sigma must be non-negative and finite"
            }
        }
    }
}
```

Remove this block from `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`:

```kotlin
/** Blur style for normalized mask filters, mirrored from Kanvas BlurStyle to avoid
 * cross-module dependency. Matches Skia's SkBlurStyle: NORMAL, SOLID, OUTER, INNER.
 */
enum class NormalizedBlurStyle { NORMAL, SOLID, OUTER, INNER }

/** Normalized mask filter descriptor captured by the command adapter before route analysis. */
sealed interface NormalizedMaskFilter {
    /** Gaussian blur mask filter with style and sigma parameters. */
    data class Blur(val style: NormalizedBlurStyle, val sigma: Float) : NormalizedMaskFilter {
        init {
            require(sigma >= 0f && sigma.isFinite()) { "Blur sigma must be non-negative and finite" }
        }
    }
}
```

Add these imports to `NormalizedDrawCommand.kt`:

```kotlin
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
```

- [ ] **Step 3: Update mask-filter imports**

Run:

```bash
rtk perl -0pi -e 's/import org\.graphiks\.kanvas\.gpu\.renderer\.commands\.NormalizedBlurStyle/import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle/g; s/import org\.graphiks\.kanvas\.gpu\.renderer\.commands\.NormalizedMaskFilter/import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter/g' $(rtk rg -l 'renderer\.commands\.Normalized(BlurStyle|MaskFilter)' gpu-renderer/src/main/kotlin gpu-renderer/src/test/kotlin)
```

Remove this import from `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/BlurFilter.kt`:

```kotlin
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedBlurStyle
```

Expected: `BlurFilter.kt` still compiles because `NormalizedBlurStyle` is now in the same package.

- [ ] **Step 4: Move gradient shader provider under `materials`**

Run:

```bash
rtk git mv gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GradientWgslShaderProvider.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GradientWgslShaderProvider.kt
rtk perl -0pi -e 's/^package org\.graphiks\.kanvas\.gpu\.renderer\.wgsl$/package org.graphiks.kanvas.gpu.renderer.materials/' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GradientWgslShaderProvider.kt
```

Expected: the provider can still import `GPUMaterialDescriptor`, and `wgsl` no longer imports domain semantics.

- [ ] **Step 5: Move COLRv0 shader source to `wgsl`**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/ColorGlyphCompositeSnippet.kt` with the declarations currently owned by the text file:

```kotlin
package org.graphiks.kanvas.gpu.renderer.wgsl

/**
 * Fixed layer budget iterated by the single-pass COLRv0 composite shader.
 * Matches `GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS`; plans exceeding it are
 * refused before reaching the shader.
 */
const val COLOR_GLYPH_COMPOSITE_MAX_LAYERS: Int = 16

/**
 * Generates the single-pass COLRv0 composite WGSL. For each layer (bottom -> top)
 * it samples the A8 coverage atlas at the layer's atlas rect and blends the
 * layer's resolved solid color over the accumulator (src-over).
 */
fun colorGlyphCompositeWgsl(maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS): String = """
struct Uniforms {
    targetWidth: f32,
    targetHeight: f32,
    layerCount: u32,
    reserved: u32,
    layerColors: array<vec4f, $maxLayers>,
    layerAtlasRects: array<vec4f, $maxLayers>,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) quad_uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) quad_uv: vec2<f32>,
};

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.position = vec4<f32>(
        in.position.x / uniforms.targetWidth * 2.0 - 1.0,
        1.0 - in.position.y / uniforms.targetHeight * 2.0,
        0.0,
        1.0
    );
    out.quad_uv = in.quad_uv;
    return out;
}

@group(1) @binding(1) var coverage_atlas: texture_2d<f32>;
@group(1) @binding(2) var coverage_sampler: sampler;

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    var accum: vec4f = vec4f(0.0, 0.0, 0.0, 0.0);
    var colors = uniforms.layerColors;
    var rects = uniforms.layerAtlasRects;
    for (var i: u32 = 0u; i < ${maxLayers}u; i = i + 1u) {
        if (i >= uniforms.layerCount) {
            break;
        }
        let rect = rects[i];
        let atlas_uv = vec2f(rect.x + in.quad_uv.x * rect.z, rect.y + in.quad_uv.y * rect.w);
        let coverage = textureSample(coverage_atlas, coverage_sampler, atlas_uv).r;
        let src = colors[i] * coverage;
        accum = src + accum * (1.0 - src.a);
    }
    return accum;
}
""".trimIndent()
```

Add these imports to `GPUColorGlyphCompositeShader.kt`:

```kotlin
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl
```

Update `GPUBackendRuntimeNative.kt` to import:

```kotlin
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl
```

Update `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUColorGlyphCompositeShaderTest.kt` to import:

```kotlin
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl
```

Expected: `execution` imports `wgsl`, not `text`.

- [ ] **Step 6: Rename the concrete runtime file and smoke test**

Run:

```bash
current_runtime="$(find gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution -maxdepth 1 -name 'GPUBackendRuntime*.kt' ! -name 'GPUBackendRuntimeContracts.kt' | head -n 1)"
current_smoke="$(find gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution -maxdepth 1 -name 'GPUBackendRuntime*SmokeTest.kt' | head -n 1)"
rtk git mv "$current_runtime" gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt
rtk git mv "$current_smoke" gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt
```

In `GPUBackendRuntimeNative.kt`, rename the concrete factory object while preserving its existing method bodies:

```bash
rtk perl -0pi -e 's/object [A-Za-z0-9]+BackendRuntimeFactory/object GPUBackendRuntimeNativeFactory/' gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt
```

Keep private runtime/session/target helper classes private. Rename private helper types only when their names appear in test names, comments, dumps or public diagnostics.

- [ ] **Step 7: Update generic runtime factory wording**

In `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`, replace the runtime factory with:

```kotlin
/** Creates the default GPU runtime when the local environment supports it. */
object GPUBackendRuntimeFactory {
    /** Returns a GPU session or null when runtime initialization is unavailable. */
    fun createOrNull(): GPUBackendSession? = GPUBackendRuntimeNativeFactory.createOrNull()

    /** Releases the shared GPU runtime resources owned by this process. */
    fun dispose() = GPUBackendRuntimeNativeFactory.dispose()
}
```

Expected: no public comment in this file names the concrete implementation.

- [ ] **Step 8: Make target IDs and capability identity generic**

In `GPUBackendRuntimeNative.kt`, change helper outputs used by tests to generic IDs:

```kotlin
private fun windowSurfaceTargetId(
    windowRuntimeOrdinal: Long,
    binding: GPUNativeSurfaceBinding,
): String =
    "gpu-window-surface-$windowRuntimeOrdinal-${binding.platform.name.lowercase()}-${binding.width}x${binding.height}"

private fun offscreenTargetId(
    sessionOrdinal: Long,
    offscreenTargetOrdinal: Long,
    request: GPUOffscreenTargetRequest,
): String =
    "gpu-offscreen-$sessionOrdinal-$offscreenTargetOrdinal-${request.width}x${request.height}-${request.colorFormat}"
```

In the session capability snapshot, set:

```kotlin
implementation = GPUImplementationIdentity(
    facadeName = "GPU",
    implementationName = "native",
    adapterName = adapterSummary.summary,
    deviceName = "gpu-device",
)
```

Update `GPUBackendRuntimeNativeSmokeTest.kt` assertions to expect `gpu-window-surface-*`, `gpu-offscreen-*`, and `implementationName == "native"`.

- [ ] **Step 9: Move WGSL reflection under canonical renderer package**

In `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WgslReflection.kt`, change:

```kotlin
package org.graphiks.kanvas.gpu.renderer.wgsl
```

Then run:

```bash
rtk perl -0pi -e 's/import org\.graphiks\.wgsl\.proc\.WgslReflectionReport/import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport/g; s/import org\.graphiks\.wgsl\.proc\.reflectWgslModule/import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule/g' $(rtk rg -l 'org\.graphiks\.wgsl\.proc\.(WgslReflectionReport|reflectWgslModule)' gpu-renderer/src/main/kotlin gpu-renderer/src/test/kotlin)
```

Expected: no production source declares a package outside `org.graphiks.kanvas.gpu.renderer`.

- [ ] **Step 10: Replace touched test fixture handle labels with generic labels**

When a touched test needs a handle-like forbidden value, use these strings:

```kotlin
private const val HANDLE_LIKE_TEXTURE_LABEL = "GPUTextureHandle0xDEADBEEF"
private const val HANDLE_LIKE_BIND_GROUP_LABEL = "GPUBindGroupHandle0xDEADBEEF"
private const val HANDLE_LIKE_RESOURCE_LABEL = "GPUResourceHandle0xFEEDFACE"
private const val HANDLE_LIKE_COMMAND_LABEL = "GPUCommandBufferHandle0xFEEDFACE"
```

Use them only as rejected fixture input. Do not use implementation-specific fixture labels.

- [ ] **Step 11: Run targeted structure tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.text.GPUColorGlyphCompositeShaderTest
```

Expected: all targeted tests PASS or the smoke test SKIPS only when local GPU runtime initialization is unavailable.

- [ ] **Step 12: Commit gate 0**

Run:

```bash
rtk git status --short
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer
rtk git commit -m "Clean GPU runtime package boundaries"
```

Expected: one commit containing only boundary, generic naming, and moved ownership changes.

---

## Task 2: Phase 0 Runtime Baseline Snapshot

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshot.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshotTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `reports/gpu-renderer/phase-0-baseline.md`

- [ ] **Step 1: Write failing baseline snapshot tests**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshotTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry

class GPURuntimeBaselineSnapshotTest {
    @Test
    fun `phase 0 baseline snapshot emits compact runtime evidence`() {
        val snapshot = GPURuntimeBaselineSnapshot(
            label = "unit-scene",
            telemetry = GPUBackendRuntimeTelemetry(
                renderPasses = 2,
                offscreenPasses = 2,
                submissions = 2,
                commandBuffers = 2,
                buffersCreated = 3,
                texturesCreated = 1,
                bindGroupsCreated = 2,
                samplersCreated = 1,
                queueWrites = 2,
                uniformSlabsCreated = 1,
                uniformSlabBytesAllocated = 256,
            ),
            cacheTelemetry = listOf(
                GPUCacheTelemetry(domain = "pipeline", hits = 1, misses = 1, creates = 1, failures = 0),
            ),
            capabilityFacts = listOf(
                GPUCapabilityFact(
                    name = "minUniformBufferOffsetAlignment",
                    source = "runtime.conservative",
                    value = "256",
                    affectsValidity = true,
                    evidenceLabel = "runtime",
                ),
            ),
        )

        val lines = snapshot.dumpLines()

        assertContains(
            lines,
            "gpu-phase0.baseline label=unit-scene renderPasses=2 offscreenPasses=2 windowPasses=0 " +
                "submissions=2 commandBuffers=2 buffersCreated=3 texturesCreated=1 " +
                "bindGroupsCreated=2 samplersCreated=1 queueWrites=2 uniformSlabsCreated=1 " +
                "uniformSlabBytesAllocated=256 uniformSlabFallbacks=0",
        )
        assertContains(
            lines,
            "gpu-phase0.cache label=unit-scene domain=pipeline hits=1 misses=1 creates=1 failures=0",
        )
        assertContains(
            lines,
            "gpu-phase0.capability label=unit-scene name=minUniformBufferOffsetAlignment " +
                "source=runtime.conservative value=256 affectsValidity=true evidence=runtime",
        )
        assertFalse(lines.joinToString("\n").contains("@"))
    }

    @Test
    fun `baseline snapshot rejects blank label`() {
        val failure = kotlin.runCatching {
            GPURuntimeBaselineSnapshot(label = "", telemetry = GPUBackendRuntimeTelemetry())
        }.exceptionOrNull()

        assertEquals("GPURuntimeBaselineSnapshot.label must not be blank", failure?.message)
    }
}
```

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeBaselineSnapshotTest
```

Expected: FAIL because `GPURuntimeBaselineSnapshot` does not exist.

- [ ] **Step 2: Implement the baseline snapshot**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPURuntimeBaselineSnapshot.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry

/** Compact phase 0 evidence assembled from runtime counters, cache counters, and capability facts. */
data class GPURuntimeBaselineSnapshot(
    val label: String,
    val telemetry: GPUBackendRuntimeTelemetry,
    val cacheTelemetry: List<GPUCacheTelemetry> = emptyList(),
    val capabilityFacts: List<GPUCapabilityFact> = emptyList(),
) {
    init {
        require(label.isNotBlank()) { "GPURuntimeBaselineSnapshot.label must not be blank" }
        require('@' !in label && "0x" !in label) { "GPURuntimeBaselineSnapshot.label must be dump-safe" }
    }

    fun dumpLines(): List<String> =
        listOf(
            "gpu-phase0.baseline label=$label renderPasses=${telemetry.renderPasses} " +
                "offscreenPasses=${telemetry.offscreenPasses} windowPasses=${telemetry.windowPasses} " +
                "submissions=${telemetry.submissions} commandBuffers=${telemetry.commandBuffers} " +
                "buffersCreated=${telemetry.buffersCreated} texturesCreated=${telemetry.texturesCreated} " +
                "bindGroupsCreated=${telemetry.bindGroupsCreated} samplersCreated=${telemetry.samplersCreated} " +
                "queueWrites=${telemetry.queueWrites} uniformSlabsCreated=${telemetry.uniformSlabsCreated} " +
                "uniformSlabBytesAllocated=${telemetry.uniformSlabBytesAllocated} " +
                "uniformSlabFallbacks=${telemetry.uniformSlabFallbacks}",
        ) +
            cacheTelemetry.map { cache ->
                "gpu-phase0.cache label=$label domain=${cache.domain} hits=${cache.hits} " +
                    "misses=${cache.misses} creates=${cache.creates} failures=${cache.failures}"
            } +
            capabilityFacts.map { fact ->
                "gpu-phase0.capability label=$label name=${fact.name} source=${fact.source} " +
                    "value=${fact.value} affectsValidity=${fact.affectsValidity} evidence=${fact.evidenceLabel}"
            }
}

/** Builds a phase 0 snapshot from a live GPU session without exposing runtime handles. */
fun GPUBackendSession.phase0BaselineSnapshot(label: String): GPURuntimeBaselineSnapshot =
    GPURuntimeBaselineSnapshot(
        label = label,
        telemetry = runtimeTelemetry,
        cacheTelemetry = executionCacheTelemetry,
        capabilityFacts = capabilities?.limits?.capabilityFacts(evidenceLabel = "runtime").orEmpty() +
            capabilities?.facts.orEmpty(),
    )
```

- [ ] **Step 3: Expose baseline dump lines on sessions**

In `GPUBackendRuntimeContracts.kt`, add this default property to `GPUBackendSession`:

```kotlin
/** Reports compact phase 0 baseline evidence without runtime handles. */
val phase0BaselineDumpLines: List<String>
    get() = phase0BaselineSnapshot(label = "session").dumpLines()
```

Expected: existing session implementations inherit the property.

- [ ] **Step 4: Update the phase 0 report**

Append this section to `reports/gpu-renderer/phase-0-baseline.md`:

```markdown
## Snapshot runtime

La baseline locale est maintenant verifiee par `GPURuntimeBaselineSnapshot`.
Le snapshot relie les compteurs runtime, les compteurs de cache et les facts de
capabilities dans des lignes `gpu-phase0.*` courtes et dumpables.

Cette preuve ne promet pas une regression visuelle nulle. Elle sert a detecter
le drift entre ce que le rapport annonce et ce que le runtime expose vraiment.
```

- [ ] **Step 5: Run phase 0 tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeBaselineSnapshotTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUPhase0BaselineReportTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest
```

Expected: all targeted tests PASS.

- [ ] **Step 6: Commit gate 1**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution reports/gpu-renderer/phase-0-baseline.md
rtk git commit -m "Add GPU phase 0 runtime baseline snapshot"
```

Expected: one commit for phase 0 baseline closure.

---

## Task 3: Phase 1 GPU Capabilities

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/GPUCapabilityContractsTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`

- [ ] **Step 1: Write failing capability tests**

Append to `GPUCapabilityContractsTest.kt`:

```kotlin
@Test
fun `GPU capabilities validate texture format usage size and uniform alignment`() {
    val capabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "GPU",
            implementationName = "native",
            adapterName = "unit-adapter",
            deviceName = "unit-device",
        ),
        facts = emptyList(),
        snapshotId = "unit-snapshot",
        limits = GPULimits.conservative(
            maxTextureDimension2D = 4096,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
        ),
        supportedTextureFormats = setOf("rgba8unorm"),
        supportedTextureUsageLabels = setOf("copy_dst", "texture_binding", "render_attachment"),
        featureLabels = setOf("texture-sampling", "uniform-buffer"),
    )

    assertEquals(null, capabilities.validateTextureRequest("rgba8unorm", 128, 64, setOf("texture_binding")))
    assertEquals(null, capabilities.validateUniformAlignment(512))
    assertEquals("unsupported.capability.texture_format", capabilities.validateTextureRequest("bgra8unorm", 128, 64, setOf("texture_binding"))?.code)
    assertEquals("unsupported.capability.texture_usage", capabilities.validateTextureRequest("rgba8unorm", 128, 64, setOf("storage_binding"))?.code)
    assertEquals("unsupported.capability.texture_size", capabilities.validateTextureRequest("rgba8unorm", 4097, 64, setOf("texture_binding"))?.code)
    assertEquals("unsupported.capability.uniform_alignment", capabilities.validateUniformAlignment(128)?.code)
    assertEquals("unsupported.capability.feature", capabilities.validateFeature("timestamp-query")?.code)
}

@Test
fun `GPU capabilities allow finer uniform alignment when observed limits allow it`() {
    val capabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "GPU",
            implementationName = "native",
            adapterName = "unit-adapter",
            deviceName = "unit-device",
        ),
        facts = emptyList(),
        snapshotId = "unit-snapshot-64",
        limits = GPULimits.conservative(
            maxTextureDimension2D = 4096,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 64,
        ),
        supportedTextureFormats = setOf("rgba8unorm"),
        supportedTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        featureLabels = setOf("uniform-buffer"),
    )

    assertEquals(null, capabilities.validateUniformAlignment(64))
    assertEquals("unsupported.capability.uniform_alignment", capabilities.validateUniformAlignment(32)?.code)
}
```

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest
```

Expected: FAIL because the new fields and helper methods do not exist.

- [ ] **Step 2: Extend capability contracts**

In `CapabilityContracts.kt`, extend `GPUCapabilities`:

```kotlin
data class GPUCapabilities(
    val implementation: GPUImplementationIdentity,
    val facts: List<GPUCapabilityFact>,
    val knownUnsupportedFacts: List<GPUCapabilityFact> = emptyList(),
    val snapshotId: String,
    val limits: GPULimits? = null,
    val supportedTextureFormats: Set<String> = emptySet(),
    val supportedTextureUsageLabels: Set<String> = emptySet(),
    val featureLabels: Set<String> = emptySet(),
) {
    init {
        require(snapshotId.isNotBlank()) { "GPUCapabilities.snapshotId must not be blank" }
        require(supportedTextureFormats.none { it.isBlank() }) {
            "GPUCapabilities.supportedTextureFormats must not contain blank labels"
        }
        require(supportedTextureUsageLabels.none { it.isBlank() }) {
            "GPUCapabilities.supportedTextureUsageLabels must not contain blank labels"
        }
        require(featureLabels.none { it.isBlank() }) {
            "GPUCapabilities.featureLabels must not contain blank labels"
        }
    }
}
```

Add helper methods below the data class:

```kotlin
fun GPUCapabilities.validateTextureRequest(
    format: String,
    width: Int,
    height: Int,
    usageLabels: Set<String>,
): GPUCapabilityDiagnostic? {
    val activeLimits = limits
    require(format.isNotBlank()) { "format must not be blank" }
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(usageLabels.none { it.isBlank() }) { "usageLabels must not contain blank labels" }

    if (supportedTextureFormats.isNotEmpty() && format !in supportedTextureFormats) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_format",
            severity = "error",
            requirementName = "texture.format",
            required = format,
            observed = supportedTextureFormats.sorted().joinToString(","),
            isTerminal = true,
        )
    }
    val missingUsage = usageLabels - supportedTextureUsageLabels
    if (supportedTextureUsageLabels.isNotEmpty() && missingUsage.isNotEmpty()) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_usage",
            severity = "error",
            requirementName = "texture.usage",
            required = missingUsage.sorted().joinToString(","),
            observed = supportedTextureUsageLabels.sorted().joinToString(","),
            isTerminal = true,
        )
    }
    if (activeLimits != null && (width > activeLimits.maxTextureDimension2D || height > activeLimits.maxTextureDimension2D)) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_size",
            severity = "error",
            requirementName = "texture.maxTextureDimension2D",
            required = maxOf(width, height).toString(),
            observed = activeLimits.maxTextureDimension2D.toString(),
            isTerminal = true,
        )
    }
    return null
}

fun GPUCapabilities.validateUniformAlignment(alignmentBytes: Long): GPUCapabilityDiagnostic? {
    require(alignmentBytes > 0L) { "alignmentBytes must be positive" }
    val required = limits?.minUniformBufferOffsetAlignment ?: return null
    return if (alignmentBytes >= required && alignmentBytes % required == 0L) {
        null
    } else {
        GPUCapabilityDiagnostic(
            code = "unsupported.capability.uniform_alignment",
            severity = "error",
            requirementName = "limits.minUniformBufferOffsetAlignment",
            required = required.toString(),
            observed = alignmentBytes.toString(),
            isTerminal = true,
        )
    }
}

fun GPUCapabilities.validateFeature(featureLabel: String): GPUCapabilityDiagnostic? {
    require(featureLabel.isNotBlank()) { "featureLabel must not be blank" }
    return if (featureLabels.isEmpty() || featureLabel in featureLabels) {
        null
    } else {
        GPUCapabilityDiagnostic(
            code = "unsupported.capability.feature",
            severity = "error",
            requirementName = "feature",
            required = featureLabel,
            observed = featureLabels.sorted().joinToString(","),
            isTerminal = true,
        )
    }
}
```

- [ ] **Step 3: Route fullscreen slab alignment through capabilities**

In `GPUBackendRuntimeNative.kt`, add a helper near the session:

```kotlin
private fun GPUCapabilities.uniformBufferOffsetAlignment(): Long =
    limits?.minUniformBufferOffsetAlignment ?: DEFAULT_UNIFORM_BUFFER_OFFSET_ALIGNMENT.toLong()
```

Rename the existing alignment constant to:

```kotlin
private const val DEFAULT_UNIFORM_BUFFER_OFFSET_ALIGNMENT: Int = 256
```

Replace slab request/planning alignment reads with:

```kotlin
alignmentBytes = capabilities.uniformBufferOffsetAlignment()
```

Expected: no fullscreen uniform slab path reads the old constant directly.

- [ ] **Step 4: Populate runtime capability facts**

In the runtime session capability snapshot, set generic supported facts:

```kotlin
override val capabilities: GPUCapabilities =
    GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "GPU",
            implementationName = "native",
            adapterName = adapterSummary.summary,
            deviceName = "gpu-device",
        ),
        facts = backendLimits.capabilityFacts(evidenceLabel = "runtime"),
        snapshotId = "gpu-runtime-${deviceGeneration.value}",
        limits = backendLimits,
        supportedTextureFormats = setOf("rgba8unorm", "bgra8unorm"),
        supportedTextureUsageLabels = setOf(
            "copy_src",
            "copy_dst",
            "texture_binding",
            "render_attachment",
        ),
        featureLabels = setOf(
            "render-pass",
            "copy-upload",
            "readback",
            "uniform-buffer",
            "texture-sampling",
        ),
    )
```

- [ ] **Step 5: Run capability tests and runtime smoke**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityContractsTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: targeted tests PASS or runtime smoke SKIPS only when local GPU runtime initialization is unavailable.

- [ ] **Step 6: Commit gate 2**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "Complete GPU capability diagnostics"
```

Expected: one commit for phase 1.

---

## Task 4: Phase 2 Concrete Resource Provider

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`

- [ ] **Step 1: Write failing concrete provider tests**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProviderTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot

class GPUConcreteResourceProviderTest {
    @Test
    fun `concrete provider materializes null buffer once per generation`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeNullBuffer(GPUNullBufferMaterializationRequest("null-uniform", 16, context.deviceGeneration), context),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeNullBuffer(GPUNullBufferMaterializationRequest("null-uniform", 16, context.deviceGeneration), context),
        )

        assertEquals(listOf("create", "reuse"), provider.telemetry.dumpEvents.map { it.result })
        assertEquals(first.dumpOperandBridgeSnapshot.single().operand.label, second.dumpOperandBridgeSnapshot.single().operand.label)
    }

    @Test
    fun `concrete provider refuses stale null buffer generation`() {
        val provider = GPUConcreteResourceProvider()
        val decision = provider.materializeNullBuffer(
            GPUNullBufferMaterializationRequest("null-uniform", 16, deviceGeneration = 7),
            targetPreparationContext(deviceGeneration = 8),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals("unsupported.resource.device_generation_stale", refused.diagnostic.code)
    }

    @Test
    fun `concrete provider reuses payload upload and bind group keys`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()
        provider.materializePayloadBindings(payloadRequest(), context)

        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializePayloadBindings(payloadRequest(), context),
        )

        assertEquals(listOf("reuse", "reuse"), second.dumpPayloadTelemetrySnapshot.map { it.result.dumpToken })
        assertContains(provider.telemetry.dumpLines().joinToString("\n"), "resource-provider.cache lane=bind-group result=reuse")
    }
}
```

Add helper functions in the same test file:

```kotlin
private fun targetPreparationContext(deviceGeneration: Long = 11L): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = deviceGeneration,
        budgetClass = "unit",
    )

private fun payloadRequest(): GPUPayloadMaterializationRequest =
    GPUPayloadMaterializationRequest(
        targetId = "root-target",
        packetId = "packet-1",
        taskIds = listOf("task-payload"),
        resourcePlanLabels = listOf("payload:unit"),
        uniformBlock = GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-unit"),
            packingPlanHash = "layout-unit",
            byteSize = 4,
            zeroedPadding = true,
            scope = "frame-1",
            bytes = listOf(1, 2, 3, 4),
        ),
        uniformSlot = GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("pass:uniform:0"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-unit"),
            byteOffset = 0,
        ),
        resourceBlock = GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-unit"),
            bindingPlanHash = "layout-unit",
            bindingCount = 1,
            resourceDescriptorLabels = listOf("uniform:unit"),
            dynamicOffsets = listOf(0),
        ),
        resourceSlot = GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("pass:resource:0"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-unit"),
            bindingIndex = 0,
        ),
        uploadPlan = GPUPayloadUploadPlan(
            planHash = "upload-unit",
            byteRanges = listOf(0L until 4L),
            stagingScope = "frame-1",
            budgetClass = "unit",
            beforeUseToken = "before-draw",
        ),
        reflectedBindingLayoutHash = "layout-unit",
        deviceGeneration = 11,
        payloadGeneration = 0,
        alignmentBytes = 256,
        uploadBudgetBytes = 256,
        uploadCapabilityAvailable = true,
        maxDynamicOffsets = 1,
        requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
        availableUniformUsageLabels = setOf("copy_dst", "uniform"),
    )
```

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest
```

Expected: FAIL because the provider types do not exist.

- [ ] **Step 2: Implement provider telemetry and null buffer request**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.resources

data class GPUNullBufferMaterializationRequest(
    val label: String,
    val byteSize: Long,
    val deviceGeneration: Long,
) {
    init {
        require(label.isNotBlank()) { "GPUNullBufferMaterializationRequest.label must not be blank" }
        require(byteSize > 0L) { "GPUNullBufferMaterializationRequest.byteSize must be positive" }
        require(deviceGeneration >= 0L) { "GPUNullBufferMaterializationRequest.deviceGeneration must be non-negative" }
    }
}

data class GPUConcreteResourceProviderEvent(
    val lane: String,
    val result: String,
    val keyHash: String,
    val subjectHash: String,
)

class GPUConcreteResourceProviderTelemetry(
    events: List<GPUConcreteResourceProviderEvent> = emptyList(),
) {
    val dumpEvents: List<GPUConcreteResourceProviderEvent> = events.toList()

    fun plus(event: GPUConcreteResourceProviderEvent): GPUConcreteResourceProviderTelemetry =
        GPUConcreteResourceProviderTelemetry(dumpEvents + event)

    fun dumpLines(): List<String> =
        dumpEvents.map { event ->
            "resource-provider.cache lane=${event.lane} result=${event.result} " +
                "key=${event.keyHash} subject=${event.subjectHash}"
        }
}
```

- [ ] **Step 3: Implement concrete provider delegation and caches**

Append this class in the same file:

```kotlin
class GPUConcreteResourceProvider(
    private val payloadProvider: ValidatingPayloadResourceProvider = ValidatingPayloadResourceProvider(),
    private val textureSamplerProvider: ValidatingTextureSamplerResourceProvider = ValidatingTextureSamplerResourceProvider(),
) : GPUResourceProvider {
    private val nullBufferKeys = linkedSetOf<String>()
    private var mutableTelemetry = GPUConcreteResourceProviderTelemetry()

    val telemetry: GPUConcreteResourceProviderTelemetry
        get() = mutableTelemetry

    fun materializeNullBuffer(
        request: GPUNullBufferMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.label,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
            )
            record("null-buffer", "stale-generation", request.label, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.label),
            )
        }

        val key = "${context.deviceGeneration}:${request.label}:${request.byteSize}"
        val result = if (nullBufferKeys.add(key)) "create" else "reuse"
        record("null-buffer", result, key, context.targetId)

        val operand = GPUMaterializedCommandOperandReference(
            label = "null-buffer:${request.label}",
            kind = GPUMaterializedCommandOperandKind.UniformBuffer,
            descriptorHash = "null-buffer:${request.byteSize}",
            deviceGeneration = context.deviceGeneration,
            ownerScope = "resource-provider:null-buffer",
            usageLabels = listOf("uniform"),
            invalidationPolicy = "device-generation",
            evidenceFacts = mapOf("byteSize" to request.byteSize.toString(), "zeroFilled" to "true"),
        )
        return GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            targetId = context.targetId,
            resourcePlanLabels = listOf(request.label),
            operandBridge = listOf(
                GPUMaterializedCommandOperandBinding(
                    commandLabel = "setBindGroup",
                    operand = operand,
                ),
            ),
        )
    }

    override fun materializePayloadBindings(
        request: GPUPayloadMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val decision = payloadProvider.materializePayloadBindings(request, context)
        decision.payloadEvents().forEach { event ->
            record(event.lane, event.result.dumpToken, event.keyHash, event.subjectHash)
        }
        return decision
    }

    override fun materializeTextureSamplerBinding(
        request: GPUTextureSamplerMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val decision = textureSamplerProvider.materializeTextureSamplerBinding(request, context)
        val result = if (decision is GPUResourceMaterializationDecision.Materialized) "create" else "failure"
        record("texture-sampler", result, request.bindingLayoutHash, request.binding.bindingLabel)
        return decision
    }

    private fun record(lane: String, result: String, keyHash: String, subjectHash: String) {
        mutableTelemetry = mutableTelemetry.plus(
            GPUConcreteResourceProviderEvent(
                lane = lane,
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            ),
        )
    }
}

private fun GPUResourceMaterializationDecision.payloadEvents(): List<GPUPayloadMaterializationTelemetryEvent> =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Refused -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Deferred -> emptyList()
    }
```

- [ ] **Step 4: Run provider tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest
```

Expected: all targeted tests PASS.

- [ ] **Step 5: Commit gate 3**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources
rtk git commit -m "Add concrete GPU resource provider"
```

Expected: one commit for phase 2 provider contract and caches.

---

## Task 5: Wire Provider Into The Fullscreen Uniform Path

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

- [ ] **Step 1: Add a smoke assertion for provider telemetry**

In `GPUBackendRuntimeNativeSmokeTest.kt`, update the runtime telemetry smoke test after `val dump = session.runtimeTelemetryDumpLines.joinToString("\n")`:

```kotlin
val baselineDump = session.phase0BaselineDumpLines.joinToString("\n")
assertTrue(baselineDump.contains("gpu-phase0.baseline"))
assertTrue(baselineDump.contains("uniformSlabsCreated="))
```

Add a new test:

```kotlin
@Test
fun `fullscreen uniform path exposes provider cache evidence when runtime is available`() {
    val runtime = GPUBackendRuntimeFactory.createOrNull()
    assumeTrue(runtime != null, "GPU runtime unavailable in current environment")

    runtime!!.use { session ->
        session.createOffscreenTarget(
            GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
        ).use { target ->
            target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                drawFullscreenPass(
                    wgsl = solidColorFullscreenWgsl(),
                    colorFormat = "rgba8unorm",
                    draws = listOf(
                        GPUBackendRectDraw(
                            rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                            scissorX = 0,
                            scissorY = 0,
                            scissorWidth = 4,
                            scissorHeight = 4,
                        ),
                    ),
                )
            }
        }

        val baselineDump = session.phase0BaselineDumpLines.joinToString("\n")
        assertTrue(baselineDump.contains("gpu-phase0.baseline"))
        assertTrue(!baselineDump.contains("@"))
    }
}
```

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: FAIL if baseline/provider evidence is not wired, or SKIP if runtime initialization is unavailable.

- [ ] **Step 2: Add a provider field to the concrete session**

In `GPUBackendRuntimeNative.kt`, inside the concrete session class constructor/body, add:

```kotlin
private val resourceProvider = GPUConcreteResourceProvider()
```

Add import:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
```

- [ ] **Step 3: Call provider when planning fullscreen payload requests**

Inside `materializeFullscreenUniformSlab`, after `payloadRequests` is created and before `GPUPayloadSlabBatchPlanner.plan(...)`, add:

```kotlin
payloadRequests.forEach { request ->
    resourceProvider.materializePayloadBindings(
        request = request,
        context = GPUTargetPreparationContext(
            targetId = payloadTargetId,
            frameId = frameId,
            deviceGeneration = deviceGeneration.value,
            budgetClass = budgetClass,
        ),
    )
}
```

Add import if missing:

```kotlin
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
```

This keeps the existing runtime buffer creation in place, but makes phase 2 provider evidence real for the fullscreen uniform path.

- [ ] **Step 4: Include provider telemetry in baseline snapshot**

If `GPUBackendSession.executionCacheTelemetry` cannot carry provider telemetry cleanly, add this property to `GPUBackendSession`:

```kotlin
/** Reports resource-provider evidence lines without runtime handles. */
val resourceProviderDumpLines: List<String>
    get() = emptyList()
```

Override it in the concrete session:

```kotlin
override val resourceProviderDumpLines: List<String>
    get() = resourceProvider.telemetry.dumpLines()
```

In `phase0BaselineSnapshot`, keep structured cache telemetry unchanged. The provider dump lines remain a separate evidence surface:

```kotlin
val GPUBackendSession.phase0EvidenceDumpLines: List<String>
    get() = phase0BaselineDumpLines + resourceProviderDumpLines
```

Use `phase0EvidenceDumpLines` in the smoke test when asserting provider cache evidence.

- [ ] **Step 5: Run runtime smoke**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: PASS or SKIP only when local GPU runtime initialization is unavailable.

- [ ] **Step 6: Commit provider wiring**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "Wire GPU provider evidence into fullscreen path"
```

Expected: one commit for limited runtime wiring.

---

## Task 6: Phase 3 Queue Manager Scaffold

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`

- [ ] **Step 1: Write failing queue manager tests**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManagerTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUQueueManagerTest {
    @Test
    fun `queue manager retains resources until completion`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("uniform-slab:frame-1")),
        )

        assertEquals(GPUQueueSubmissionId(1), submission.id)
        assertEquals(listOf(GPUQueuedResourceRef("uniform-slab:frame-1")), manager.retainedResources(submission.id))
        assertFalse(manager.releaseCompleted().contains(GPUQueuedResourceRef("uniform-slab:frame-1")))

        manager.markCompleted(submission.id)

        assertEquals(listOf(GPUQueuedResourceRef("uniform-slab:frame-1")), manager.releaseCompleted())
        assertEquals(emptyList(), manager.retainedResources(submission.id))
    }

    @Test
    fun `queue manager telemetry is stable and dump safe`() {
        val manager = GPUQueueManager()
        val submission = manager.submit("frame-1", listOf(GPUQueuedResourceRef("readback:frame-1")))
        manager.markCompleted(submission.id)
        manager.releaseCompleted()

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertContains(dump, "gpu-queue.telemetry submitted=1 completed=1 released=1 waits=0")
        assertContains(dump, "gpu-queue.submission id=1 label=frame-1 retained=1 completed=true released=true")
        assertFalse(dump.contains("@"))
    }

    @Test
    fun `queue manager ignores completion for unknown submission id`() {
        val manager = GPUQueueManager()

        assertFalse(manager.markCompleted(GPUQueueSubmissionId(99)))
        assertTrue(manager.telemetry.dumpLines().joinToString("\n").contains("unknownCompletions=1"))
    }
}
```

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
```

Expected: FAIL because queue types do not exist.

- [ ] **Step 2: Implement queue manager value types and telemetry**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUQueueManager.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

@JvmInline
value class GPUQueueSubmissionId(val value: Long) {
    init {
        require(value > 0L) { "GPUQueueSubmissionId.value must be positive" }
    }
}

@JvmInline
value class GPUQueuedResourceRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUQueuedResourceRef.value must not be blank" }
        require('@' !in value && "0x" !in value) { "GPUQueuedResourceRef.value must be dump-safe" }
    }
}

data class GPUQueueSubmission(
    val id: GPUQueueSubmissionId,
    val label: String,
    val retainedResources: List<GPUQueuedResourceRef>,
    val completed: Boolean = false,
    val released: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "GPUQueueSubmission.label must not be blank" }
    }
}

data class GPUQueueTelemetry(
    val submitted: Long = 0,
    val completed: Long = 0,
    val released: Long = 0,
    val waits: Long = 0,
    val unknownCompletions: Long = 0,
    val submissions: List<GPUQueueSubmission> = emptyList(),
) {
    fun dumpLines(): List<String> =
        listOf(
            "gpu-queue.telemetry submitted=$submitted completed=$completed released=$released " +
                "waits=$waits unknownCompletions=$unknownCompletions",
        ) + submissions.map { submission ->
            "gpu-queue.submission id=${submission.id.value} label=${submission.label} " +
                "retained=${submission.retainedResources.size} completed=${submission.completed} " +
                "released=${submission.released}"
        }
}
```

- [ ] **Step 3: Implement queue retention and release**

Append in the same file:

```kotlin
class GPUQueueManager {
    private var nextSubmissionId = 1L
    private val submissions = linkedMapOf<GPUQueueSubmissionId, GPUQueueSubmission>()
    private var unknownCompletions = 0L
    private var waitCount = 0L

    val telemetry: GPUQueueTelemetry
        get() = GPUQueueTelemetry(
            submitted = submissions.values.size.toLong(),
            completed = submissions.values.count { it.completed }.toLong(),
            released = submissions.values.count { it.released }.toLong(),
            waits = waitCount,
            unknownCompletions = unknownCompletions,
            submissions = submissions.values.toList(),
        )

    fun submit(label: String, retainedResources: List<GPUQueuedResourceRef>): GPUQueueSubmission {
        val submission = GPUQueueSubmission(
            id = GPUQueueSubmissionId(nextSubmissionId++),
            label = label,
            retainedResources = retainedResources.toList(),
        )
        submissions[submission.id] = submission
        return submission
    }

    fun markCompleted(id: GPUQueueSubmissionId): Boolean {
        val current = submissions[id]
        if (current == null) {
            unknownCompletions += 1
            return false
        }
        submissions[id] = current.copy(completed = true)
        return true
    }

    fun retainedResources(id: GPUQueueSubmissionId): List<GPUQueuedResourceRef> =
        submissions[id]?.takeUnless { it.released }?.retainedResources.orEmpty()

    fun releaseCompleted(): List<GPUQueuedResourceRef> {
        val released = mutableListOf<GPUQueuedResourceRef>()
        submissions.replaceAll { _, submission ->
            if (submission.completed && !submission.released) {
                released += submission.retainedResources
                submission.copy(released = true)
            } else {
                submission
            }
        }
        return released
    }

    fun recordWait() {
        waitCount += 1
    }
}
```

- [ ] **Step 4: Expose queue dump lines on sessions**

In `GPUBackendRuntimeContracts.kt`, add:

```kotlin
/** Reports queue submission, completion, and resource-retention evidence. */
val queueDumpLines: List<String>
    get() = emptyList()
```

In `phase0EvidenceDumpLines`, append `queueDumpLines`.

- [ ] **Step 5: Run queue tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest
```

Expected: all targeted tests PASS.

- [ ] **Step 6: Commit queue scaffold**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "Add GPU queue manager scaffold"
```

Expected: one commit for phase 3 scaffold.

---

## Task 7: Wire Queue Evidence Into Limited Runtime Paths

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeSmokeTest.kt`

- [ ] **Step 1: Add runtime queue smoke assertion**

In `GPUBackendRuntimeNativeSmokeTest.kt`, in the telemetry smoke test after readback:

```kotlin
val evidence = session.phase0EvidenceDumpLines.joinToString("\n")
assertTrue(evidence.contains("gpu-queue.telemetry"))
assertTrue(evidence.contains("submitted="))
```

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: FAIL if queue evidence is not wired, or SKIP if runtime initialization is unavailable.

- [ ] **Step 2: Add a queue manager field to the concrete session**

In `GPUBackendRuntimeNative.kt`, inside the concrete session:

```kotlin
private val queueManager = GPUQueueManager()

override val queueDumpLines: List<String>
    get() = queueManager.telemetry.dumpLines()
```

- [ ] **Step 3: Record submissions in encode/readback paths**

Where the concrete runtime currently submits a command buffer, wrap the submit with:

```kotlin
val submission = queueManager.submit(
    label = "offscreen-pass:$frameId",
    retainedResources = listOf(GPUQueuedResourceRef("target:$targetId")),
)
queue.submit(listOf(commandBuffer))
queueManager.markCompleted(submission.id)
queueManager.releaseCompleted()
```

Where readback performs a blocking wait or maps staging data, add:

```kotlin
queueManager.recordWait()
```

If the concrete code has multiple submit helpers, apply the same pattern to offscreen encode and readback only in this PR.

- [ ] **Step 4: Run queue and smoke tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
```

Expected: queue tests PASS; runtime smoke PASS or SKIP only when local GPU runtime initialization is unavailable.

- [ ] **Step 5: Commit limited queue wiring**

Run:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "Wire GPU queue evidence into offscreen runtime"
```

Expected: one commit for limited phase 3 wiring.

---

## Task 8: Final Verification, Dashboard, And PR

**Files:**
- Modify only files changed by previous tasks.
- Do not stage generated PNGs unless the dashboard diff shows an intended visual rebaseline.

- [ ] **Step 1: Run public wording audit on touched docs/tests/dumps**

Run this script from the repo root:

```bash
rtk python3 - <<'PY'
from pathlib import Path
import subprocess

bad = ["W" + "GPU", "W" + "gpu", "w" + "gpu", "Web" + "GPU"]
paths = subprocess.check_output(["git", "diff", "--name-only", "HEAD"], text=True).splitlines()
checked = []
violations = []
for raw in paths:
    path = Path(raw)
    if not path.exists() or path.suffix not in {".kt", ".md"}:
        continue
    text = path.read_text(errors="ignore")
    checked.append(raw)
    for token in bad:
        if token in text:
            violations.append((raw, token))
if violations:
    for path, token in violations:
        print(f"{path}: forbidden public wording token {token!r}")
    raise SystemExit(1)
print(f"checked {len(checked)} changed Kotlin/Markdown files")
PY
```

Expected: PASS for changed Kotlin/Markdown files. If it fails only because the concrete runtime file contains unavoidable library imports, inspect each hit manually and remove comments, strings, test names, public types, diagnostics and labels that expose the implementation name. Do not remove required library imports.

- [ ] **Step 2: Run full module tests**

Run:

```bash
rtk ./gradlew :gpu-renderer:test
```

Expected: PASS, with only environment-dependent SKIPs already accepted by the suite.

- [ ] **Step 3: Run GM smoke scan**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'
```

Expected: PASS=8, FAIL=0, TIMEOUT=0, or a clearly documented environment-only failure.

- [ ] **Step 4: Regenerate dashboard without staging PNGs blindly**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
rtk git status --short integration-tests/skia/build/reports/skia-gm-dashboard integration-tests/skia/src/test/resources
```

Expected: dashboard generation succeeds. If PNGs changed, inspect the dashboard before staging; default action is not to commit PNG churn from this PR.

- [ ] **Step 5: Inspect dirty tree**

Run:

```bash
rtk git status --short
rtk git diff --stat
```

Expected: dirty files match the planned code/report changes only.

- [ ] **Step 6: Commit final verification notes only if files changed**

If Task 8 produced only verification output, do not create a commit. If it produced a small doc update, run:

```bash
rtk git add <changed-doc-file>
rtk git commit -m "Document GPU refactor verification"
```

Expected: no accidental generated artifacts in the commit.

- [ ] **Step 7: Push and create a single PR**

Run:

```bash
rtk git status --short
rtk git log --oneline -8
rtk git push -u origin HEAD
rtk gh pr create --draft --title "[codex] Complete GPU phases 0-2 queue scaffold" --body-file /tmp/gpu-phases-0-2-queue-scaffold-pr.md
```

Use this PR body:

```markdown
## Summary

- closes the package-boundary drift before runtime changes
- adds phase 0 runtime baseline snapshot evidence
- completes GPU capability diagnostics for alignment, formats, usage, size and features
- adds a concrete GPU resource provider for payload/null-buffer/texture-sampler evidence
- adds a minimal queue manager scaffold and limited offscreen wiring

## Tests

- `rtk ./gradlew :gpu-renderer:test`
- `rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'`
- `rtk ./gradlew :integration-tests:skia:generateSkiaDashboard`

## Notes

- No broad batching is introduced in this PR.
- No generated PNG is staged unless explicitly reviewed.
- Public wording remains generic GPU.
```

Expected: one draft PR for the full aggressive session.

---

## Self-Review

Spec coverage:

- Gate 0 covers package-boundary closure, cycle removal, canonical package root and public wording.
- Gate 1 covers phase 0 baseline as runtime evidence, not only a markdown report.
- Gate 2 covers phase 1 capabilities, uniform alignment and stable diagnostics.
- Gate 3 covers phase 2 provider for uniforms, null buffer, bind group and texture/sampler evidence.
- Gate 4 covers phase 3 queue scaffold with retention, completion, release and limited runtime wiring.
- Final verification covers module tests, GM smoke scan and dashboard generation.

Placeholder scan:

- The only mechanical body move is the COLRv0 shader literal and the concrete runtime factory body. Both are instructed as exact moves from existing files because rewriting those large bodies in the plan would add risk.
- No red-flag placeholder marker from the skill remains.

Type consistency:

- `GPURuntimeBaselineSnapshot`, `GPUConcreteResourceProvider`, `GPUNullBufferMaterializationRequest`, `GPUQueueManager`, `GPUQueueSubmissionId`, and `GPUQueuedResourceRef` are introduced before later tasks use them.
- Session evidence properties are added before smoke tests consume them.
- Provider telemetry uses string result labels because it combines existing payload telemetry with new provider lanes.

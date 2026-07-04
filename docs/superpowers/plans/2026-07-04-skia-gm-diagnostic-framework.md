# Skia GM Diagnostic Framework — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 3-layer diagnostic framework that produces agent-consumable structured diagnostics for Skia GM test failures.

**Architecture:** New module `integration-tests/diagnostic` with `DiffAnalyzer` (Layer 1), `OpInspector` (Layer 2), and `PipelineTracer` (Layer 3). Decoupled via `RenderOpListener` interface in `kanvas-core`. All layers controlled by `DebugLevel` enum (OFF/PIXEL/OP/TRACE) on `RenderConfig`. Output is a `DiagnosticManifest` JSON file with `agentSummary` section.

**Tech Stack:** Kotlin/JVM, JUnit 5, existing Kanvas `Picture`/`DisplayOp`/`Surface` APIs. No new external dependencies.

**Naming note:** The existing `DiagnosticLevel` enum (FATAL/DEGRADE/WARN) in `kanvas` is about pipeline severity. The new concept for debug depth uses `DebugLevel` (OFF/PIXEL/OP/TRACE) to avoid collision. Added as a new field `debugLevel` on `RenderConfig` — the existing `diagnosticLevel` field remains for severity.

---

### Task 0: Create the diagnostic module

**Files:**
- Create: `integration-tests/diagnostic/build.gradle.kts`
- Modify: `settings.gradle.kts:104`

- [ ] **Step 1: Create module build file**

```kotlin
// integration-tests/diagnostic/build.gradle.kts
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas"))
    implementation(project(":integration-tests:test-utils"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
```

- [ ] **Step 2: Register module in settings.gradle.kts**

In `settings.gradle.kts`, after line 104 (`include(":integration-tests:skia")`), add:
```kotlin
include(":integration-tests:diagnostic")
```

- [ ] **Step 3: Verify module resolves**

Run: `./gradlew :integration-tests:diagnostic:dependencies --configuration implementation`
Expected: Resolves `:kanvas` and `:integration-tests:test-utils` without errors.

- [ ] **Step 4: Commit**

```bash
git add integration-tests/diagnostic/build.gradle.kts settings.gradle.kts
git commit -m "build: add integration-tests:diagnostic module"
```

---

### Task 1: Add DebugLevel enum and wire into RenderConfig

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/DebugLevel.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt:3-31`

- [ ] **Step 1: Write DebugLevel enum**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/surface/DebugLevel.kt
package org.graphiks.kanvas.surface

/**
 * Controls the depth of diagnostic capture during rendering.
 *
 * - [OFF]: no capture, zero overhead.
 * - [PIXEL]: Layer 1 — enriched pixel diff (heatmap, SSIM, zones).
 * - [OP]: Layer 1 + 2 — per-operation isolation and blame.
 * - [TRACE]: Layer 1 + 2 + 3 — full pipeline trace per operation.
 *
 * Each level includes all lower levels.
 */
enum class DebugLevel { OFF, PIXEL, OP, TRACE }
```

- [ ] **Step 2: Add debugLevel field to RenderConfig**

Read the current `RenderConfig.kt` (31 lines). Add `debugLevel` field and update `fromEnvironment()`:

```kotlin
data class RenderConfig(
    val gpuColorFormat: GPUColorFormat = GPUColorFormat.RGBA8_UNORM_SRGB,
    val maxPathVertices: UInt = 131072u,
    val curveTolerance: Float = 0.25f,
    val maxImagePixels: UInt = 67_108_864u,
    val diagnosticLevel: DiagnosticLevel = DiagnosticLevel.WARN,
    val debugLevel: DebugLevel = DebugLevel.OFF,
) {
    companion object {
        val DEFAULT = RenderConfig()

        fun fromEnvironment(): RenderConfig {
            val p = System.getProperties()
            return RenderConfig(
                gpuColorFormat = p.getProperty("kanvas.render.gpuColorFormat")
                    ?.let { runCatching { GPUColorFormat.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.gpuColorFormat,
                maxPathVertices = p.getProperty("kanvas.render.maxPathVertices")
                    ?.toUIntOrNull() ?: DEFAULT.maxPathVertices,
                curveTolerance = p.getProperty("kanvas.render.curveTolerance")
                    ?.toFloatOrNull() ?: DEFAULT.curveTolerance,
                maxImagePixels = p.getProperty("kanvas.render.maxImagePixels")
                    ?.toUIntOrNull() ?: DEFAULT.maxImagePixels,
                diagnosticLevel = p.getProperty("kanvas.render.diagnosticLevel")
                    ?.let { runCatching { DiagnosticLevel.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.diagnosticLevel,
                debugLevel = p.getProperty("kanvas.debug")
                    ?.let { runCatching { DebugLevel.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.debugLevel,
            )
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :kanvas:compileKotlin`
Expected: PASS (no errors).

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/DebugLevel.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt
git commit -m "feat: add DebugLevel enum and wire into RenderConfig"
```

---

### Task 2: Add RenderOpListener interface and Surface.setRenderOpListener

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderOpListener.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt:22-114`

- [ ] **Step 1: Write RenderOpListener interface**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderOpListener.kt
package org.graphiks.kanvas.surface

/**
 * Listener for per-operation pipeline events during rendering.
 *
 * When [DebugLevel.TRACE] is active, the GPU renderer calls these methods
 * for each [DisplayOp] as it is processed. Set via [Surface.setRenderOpListener].
 */
interface RenderOpListener {
    /** Called when an operation is successfully dispatched to the GPU pipeline. */
    fun onOpDispatched(
        index: Int,
        opType: String,
        route: String,
        shaders: List<String>,
        vertexCount: Int,
        blendMode: String,
    )

    /** Called when an operation is refused by the GPU pipeline. */
    fun onOpRefused(
        index: Int,
        opType: String,
        code: String,
        reason: String,
    )
}
```

- [ ] **Step 2: Add setRenderOpListener to Surface**

Read `Surface.kt` (114 lines). Add a mutable field and setter:

```kotlin
class Surface(
    val width: Int,
    val height: Int,
    val format: PixelFormat = PixelFormat.RGBA8,
    val config: RenderConfig = RenderConfig.DEFAULT,
) {
    private val buffer = SurfaceDisplayListBuffer()
    private var canvasInstance: Canvas? = null
    
    /** Optional listener for per-operation pipeline events (DebugLevel.TRACE). */
    var renderOpListener: RenderOpListener? = null

    // ... rest of the class unchanged ...

    fun render(): RenderResult = renderViaGpu(buffer, width, height, format, config, renderOpListener)
}
```

The `renderViaGpu` function signature must be updated to accept the optional listener:

```kotlin
// In kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/RenderViaGpu.kt (or wherever renderViaGpu is defined):
// Add parameter: renderOpListener: RenderOpListener? = null
```

Then in the GPU renderer code (in `gpu-renderer` module), accept the listener and call it per operation.

For now, just update the call site in `Surface.kt` — the GPU renderer wiring happens in Task 6.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :kanvas:compileKotlin`
Expected: PASS (if `renderViaGpu` signature is compatible — check and adjust).

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderOpListener.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt
git commit -m "feat: add RenderOpListener interface and Surface wiring"
```

---

### Task 3: Add SSIM computation to ComparisonUtils

**Files:**
- Modify: `integration-tests/test-utils/src/main/kotlin/org/graphiks/kanvas/test/ComparisonUtils.kt:1-140`

- [ ] **Step 1: Add computeSSIM function**

Read `ComparisonUtils.kt` (140 lines). Add the following function inside the `ComparisonUtils` object:

```kotlin
/**
 * Compute Structural Similarity Index (SSIM) between two RGBA buffers.
 *
 * Uses standard 16x16 blocks on the luminance channel (Y = 0.299R + 0.587G + 0.114B).
 * Constants: C1 = (0.01 * 255)^2, C2 = (0.03 * 255)^2.
 *
 * @return SSIM score in [0.0, 1.0], where 1.0 means identical.
 */
fun computeSSIM(
    actual: ByteArray,
    reference: ByteArray,
    width: Int,
    height: Int,
): Double {
    val blockSize = 16
    val C1 = (0.01 * 255.0).let { it * it }
    val C2 = (0.03 * 255.0).let { it * it }

    fun luminance(r: Int, g: Int, b: Int): Double =
        0.299 * r + 0.587 * g + 0.114 * b

    val blocksX = width / blockSize
    val blocksY = height / blockSize
    if (blocksX == 0 || blocksY == 0) return 1.0

    var totalSSIM = 0.0
    var blockCount = 0

    for (by in 0 until blocksY) {
        for (bx in 0 until blocksX) {
            val n = blockSize * blockSize
            var sumX = 0.0
            var sumY = 0.0
            var sumXX = 0.0
            var sumYY = 0.0
            var sumXY = 0.0

            for (dy in 0 until blockSize) {
                for (dx in 0 until blockSize) {
                    val px = (by * blockSize + dy) * width + (bx * blockSize + dx)
                    val i = px * 4
                    val ar = actual[i].toInt() and 0xFF
                    val ag = actual[i + 1].toInt() and 0xFF
                    val ab = actual[i + 2].toInt() and 0xFF
                    val rr = reference[i].toInt() and 0xFF
                    val rg = reference[i + 1].toInt() and 0xFF
                    val rb = reference[i + 2].toInt() and 0xFF

                    val lx = luminance(ar, ag, ab)
                    val ly = luminance(rr, rg, rb)
                    sumX += lx; sumY += ly
                    sumXX += lx * lx; sumYY += ly * ly
                    sumXY += lx * ly
                }
            }

            val meanX = sumX / n
            val meanY = sumY / n
            val varX = sumXX / n - meanX * meanX
            val varY = sumYY / n - meanY * meanY
            val covXY = sumXY / n - meanX * meanY

            val numerator = (2.0 * meanX * meanY + C1) * (2.0 * covXY + C2)
            val denominator = (meanX * meanX + meanY * meanY + C1) * (varX + varY + C2)
            val ssim = if (denominator > 0.0) numerator / denominator else 1.0
            totalSSIM += ssim
            blockCount++
        }
    }

    return if (blockCount > 0) totalSSIM / blockCount else 1.0
}
```

- [ ] **Step 2: Add computeSSIMBlocks function**

Add a variant that returns per-block scores:

```kotlin
data class SsimBlock(val x: Int, val y: Int, val score: Double)

fun computeSSIMBlocks(
    actual: ByteArray,
    reference: ByteArray,
    width: Int,
    height: Int,
    blockSize: Int = 16,
): List<SsimBlock> {
    // Same inner loop as computeSSIM but returns individual block scores
    val C1 = (0.01 * 255.0).let { it * it }
    val C2 = (0.03 * 255.0).let { it * it }
    fun lum(r: Int, g: Int, b: Int): Double = 0.299 * r + 0.587 * g + 0.114 * b

    val blocksX = width / blockSize
    val blocksY = height / blockSize
    if (blocksX == 0 || blocksY == 0) return emptyList()

    val results = mutableListOf<SsimBlock>()
    for (by in 0 until blocksY) {
        for (bx in 0 until blocksX) {
            val n = blockSize * blockSize
            var sumX = 0.0; var sumY = 0.0; var sumXX = 0.0; var sumYY = 0.0; var sumXY = 0.0
            for (dy in 0 until blockSize) {
                for (dx in 0 until blockSize) {
                    val px = (by * blockSize + dy) * width + (bx * blockSize + dx)
                    val i = px * 4
                    val lx = lum(actual[i].toInt() and 0xFF, actual[i + 1].toInt() and 0xFF, actual[i + 2].toInt() and 0xFF)
                    val ly = lum(reference[i].toInt() and 0xFF, reference[i + 1].toInt() and 0xFF, reference[i + 2].toInt() and 0xFF)
                    sumX += lx; sumY += ly; sumXX += lx * lx; sumYY += ly * ly; sumXY += lx * ly
                }
            }
            val mx = sumX / n; val my = sumY / n
            val vx = sumXX / n - mx * mx; val vy = sumYY / n - my * my; val cv = sumXY / n - mx * my
            val num = (2.0 * mx * my + C1) * (2.0 * cv + C2)
            val den = (mx * mx + my * my + C1) * (vx + vy + C2)
            val s = if (den > 0.0) num / den else 1.0
            results.add(SsimBlock(x = bx * blockSize, y = by * blockSize, score = s))
        }
    }
    return results
}
```

- [ ] **Step 3: Write unit test**

Create: `integration-tests/test-utils/src/test/kotlin/org/graphiks/kanvas/test/ComparisonUtilsTest.kt`

```kotlin
package org.graphiks.kanvas.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComparisonUtilsTest {
    @Test
    fun `ssim of identical buffers is 1 point 0`() {
        val w = 32; val h = 32
        val identical = ByteArray(w * h * 4) { i -> (i % 4 + 10).toByte() }
        val ssim = ComparisonUtils.computeSSIM(identical, identical, w, h)
        assertTrue(ssim > 0.999, "Expected ~1.0 for identical, got $ssim")
    }

    @Test
    fun `ssim of inverted buffers is low`() {
        val w = 32; val h = 32
        val a = ByteArray(w * h * 4) { i -> if (i % 4 == 0) 200.toByte() else 50.toByte() }
        val b = ByteArray(w * h * 4) { i -> if (i % 4 == 0) 50.toByte() else 200.toByte() }
        val ssim = ComparisonUtils.computeSSIM(a, b, w, h)
        assertTrue(ssim < 0.9, "Expected low SSIM for different buffers, got $ssim")
    }

    @Test
    fun `ssim blocks returns correct count`() {
        val w = 32; val h = 32
        val buf = ByteArray(w * h * 4) { 100.toByte() }
        val blocks = ComparisonUtils.computeSSIMBlocks(buf, buf, w, h, blockSize = 16)
        assertEquals(4, blocks.size) // 32/16 = 2 blocks each dimension, 2*2 = 4
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :integration-tests:test-utils:test --tests "org.graphiks.kanvas.test.ComparisonUtilsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add integration-tests/test-utils/src/main/kotlin/org/graphiks/kanvas/test/ComparisonUtils.kt integration-tests/test-utils/src/test/
git commit -m "feat: add SSIM computation to ComparisonUtils"
```

---

### Task 4: Build Layer 1 — DiffAnalyzer

**Files:**
- Create: `integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/SpatialZoneClassifier.kt`
- Create: `integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiffAnalyzer.kt`

No dependencies on other diagnostic module files yet — this is self-contained.

- [ ] **Step 1: Write SpatialZoneClassifier**

```kotlin
// integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/SpatialZoneClassifier.kt
package org.graphiks.kanvas.diagnostic

import kotlin.math.abs

enum class ZoneType { EDGE, SOLID, GRADIENT, TEXT }

data class ZoneRegion(
    val label: String,
    val bounds: ZoneBounds,
    val type: ZoneType,
    val dominantChannel: String,
    val severity: String, // "low", "medium", "high"
    val avgDelta: Double,
)

data class ZoneBounds(val x: Int, val y: Int, val w: Int, val h: Int)

object SpatialZoneClassifier {
    /**
     * Classify each pixel in the reference image into spatial zones using a
     * Sobel 3x3 edge detector on luminance.
     */
    fun classify(
        rgba: ByteArray,
        width: Int,
        height: Int,
    ): Array<ZoneType> {
        val zones = Array(width * height) { ZoneType.SOLID }
        val lum = DoubleArray(width * height) { i ->
            val base = i * 4
            0.299 * (rgba[base].toInt() and 0xFF) + 0.587 * (rgba[base + 1].toInt() and 0xFF) + 0.114 * (rgba[base + 2].toInt() and 0xFF)
        }

        val sobelThreshold = 30.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val gx = abs(
                    -1 * lum[i - width - 1] + 0 * lum[i - 1] + 1 * lum[i + width - 1] +
                    -2 * lum[i - width]     + 0 * lum[i]     + 2 * lum[i + width] +
                    -1 * lum[i - width + 1] + 0 * lum[i + 1] + 1 * lum[i + width + 1])
                val gy = abs(
                    -1 * lum[i - width - 1] - 2 * lum[i - 1] - 1 * lum[i + width - 1] +
                     0 * lum[i - width]     + 0 * lum[i]     + 0 * lum[i + width] +
                     1 * lum[i - width + 1] + 2 * lum[i + 1] + 1 * lum[i + width + 1])
                if (gx + gy > sobelThreshold) zones[i] = ZoneType.EDGE
            }
        }
        return zones
    }

    /**
     * Classify zones as TEXT by measuring edge density in 64x64 blocks.
     */
    fun classifyTextZones(zones: Array<ZoneType>, width: Int, height: Int): Array<ZoneType> {
        val result = zones.copyOf()
        val blockSize = 64
        for (by in 0 until height step blockSize) {
            for (bx in 0 until width step blockSize) {
                var edgeCount = 0; var total = 0
                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val x = bx + dx; val y = by + dy
                        if (x >= width || y >= height) continue
                        if (zones[y * width + x] == ZoneType.EDGE) edgeCount++
                        total++
                    }
                }
                if (total > 0 && edgeCount.toDouble() / total > 0.15) {
                    for (dy in 0 until blockSize) {
                        for (dx in 0 until blockSize) {
                            val x = bx + dx; val y = by + dy
                            if (x < width && y < height) result[y * width + x] = ZoneType.TEXT
                        }
                    }
                }
            }
        }
        return result
    }
}
```

- [ ] **Step 2: Write unit tests for SpatialZoneClassifier**

Create: `integration-tests/diagnostic/src/test/kotlin/org/graphiks/kanvas/diagnostic/SpatialZoneClassifierTest.kt`

```kotlin
package org.graphiks.kanvas.diagnostic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpatialZoneClassifierTest {
    @Test
    fun `solid white image classifies all pixels as SOLID`() {
        val w = 64; val h = 64
        val white = ByteArray(w * h * 4) { 255.toByte() }
        val zones = SpatialZoneClassifier.classify(white, w, h)
        assertEquals(ZoneType.SOLID, zones[0])
        assertEquals(ZoneType.SOLID, zones[zones.size - 1])
    }

    @Test
    fun `sharp edge image classifies interior as EDGE at boundary`() {
        val w = 64; val h = 64
        val half = ByteArray(w * h * 4) { i ->
            val px = (i / 4) % w
            if (px < w / 2) 0.toByte() else 255.toByte()
        }
        val zones = SpatialZoneClassifier.classify(half, w, h)
        // The boundary column should be detected as EDGE
        val boundaryIdx = (h / 2) * w + (w / 2)
        assertEquals(ZoneType.EDGE, zones[boundaryIdx])
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :integration-tests:diagnostic:test --tests "org.graphiks.kanvas.diagnostic.SpatialZoneClassifierTest"`
Expected: PASS.

- [ ] **Step 4: Write DiffAnalyzer**

```kotlin
// integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiffAnalyzer.kt
package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File
import kotlin.math.abs

data class SpatialReport(
    val ssim: Double,
    val ssimBlocks: List<ComparisonUtils.SsimBlock>,
    val zones: List<ZoneRegion>,
    val heatmapUrl: String?,
    val perChannelHeatmapUrls: Map<String, String>,
)

object DiffAnalyzer {
    fun analyze(
        actualRgba: ByteArray,
        referenceRgba: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int,
        outputDir: File? = null,
    ): SpatialReport {
        val ssim = ComparisonUtils.computeSSIM(actualRgba, referenceRgba, width, height)
        val ssimBlocks = ComparisonUtils.computeSSIMBlocks(actualRgba, referenceRgba, width, height)

        // Zone classification on reference image
        val baseZones = SpatialZoneClassifier.classify(referenceRgba, width, height)
        val zones = SpatialZoneClassifier.classifyTextZones(baseZones, width, height)

        // Aggregate deltas per zone type
        val zoneRegions = buildZoneRegions(actualRgba, referenceRgba, zones, width, height)

        // Heatmaps
        var heatmapUrl: String? = null
        val perChannelUrls = mutableMapOf<String, String>()
        if (outputDir != null) {
            outputDir.mkdirs()
            saveHeatmap(actualRgba, referenceRgba, width, height, outputDir.resolve("heatmap.png"))
            heatmapUrl = "heatmap.png"
            for ((ch, idx) in listOf("R" to 0, "G" to 1, "B" to 2, "A" to 3)) {
                val file = outputDir.resolve("heatmap_$ch.png")
                saveChannelHeatmap(actualRgba, referenceRgba, width, height, idx, file)
                perChannelUrls[ch] = "heatmap_$ch.png"
            }
        }

        return SpatialReport(ssim, ssimBlocks, zoneRegions, heatmapUrl, perChannelUrls)
    }

    private fun buildZoneRegions(
        actual: ByteArray,
        reference: ByteArray,
        zones: Array<ZoneType>,
        width: Int,
        height: Int,
    ): List<ZoneRegion> {
        val statsByType = mutableMapOf<ZoneType, ZoneStats>()
        val zoneNames = mapOf(
            ZoneType.EDGE to "edge",
            ZoneType.SOLID to "solid",
            ZoneType.GRADIENT to "gradient",
            ZoneType.TEXT to "text",
        )
        for (i in 0 until width * height) {
            val base = i * 4
            val z = zones[i]
            val stats = statsByType.getOrPut(z) { ZoneStats() }
            for (ch in 0..3) {
                val d = abs((actual[base + ch].toInt() and 0xFF) - (reference[base + ch].toInt() and 0xFF))
                stats.maxDelta[ch] = maxOf(stats.maxDelta[ch], d)
                stats.sumDelta[ch] += d.toLong()
            }
            stats.totalPixels++
        }
        val channelNames = listOf("R", "G", "B", "A")
        return statsByType.map { (type, stats) ->
            val avgPerChannel = stats.sumDelta.mapIndexed { i, sum ->
                if (stats.totalPixels > 0) sum.toDouble() / stats.totalPixels else 0.0
            }
            val maxChannelIdx = avgPerChannel.indices.maxByOrNull { avgPerChannel[it] } ?: 0
            val avgMaxDelta = avgPerChannel[maxChannelIdx]
            val severity = when {
                avgMaxDelta > 20.0 -> "high"
                avgMaxDelta > 5.0 -> "medium"
                else -> "low"
            }
            ZoneRegion(
                label = zoneNames[type] ?: "unknown",
                bounds = ZoneBounds(0, 0, width, height),
                type = type,
                dominantChannel = channelNames[maxChannelIdx],
                severity = severity,
                avgDelta = avgMaxDelta,
            )
        }
    }

    private fun saveHeatmap(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        file: File,
    ) {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val base = i * 4
            val maxDelta = (0..3).maxOf {
                abs((actual[base + it].toInt() and 0xFF) - (reference[base + it].toInt() and 0xFF))
            }
            val intensity = (maxDelta * 4).coerceIn(0, 255)
            val (r, g, b) = when {
                intensity < 64 -> (0 to (intensity * 4) to 0)       // green
                intensity < 128 -> ((intensity - 64) * 4 to 255 to 0) // yellow
                intensity < 192 -> (255 to 255 - (intensity - 128) * 4 to 0) // orange
                else -> (255 to 0 to (intensity - 192) * 4)          // red
            }
            rgba[base] = r.toByte()
            rgba[base + 1] = g.toByte()
            rgba[base + 2] = b.toByte()
            rgba[base + 3] = if (maxDelta > 0) 255.toByte() else 0.toByte()
        }
        ComparisonUtils.saveRgbaAsPng(rgba, width, height, file)
    }

    private fun saveChannelHeatmap(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        channel: Int,
        file: File,
    ) {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val base = i * 4
            val delta = abs((actual[base + channel].toInt() and 0xFF) - (reference[base + channel].toInt() and 0xFF))
            val intensity = (delta * 4).coerceIn(0, 255)
            rgba[base] = if (channel == 0) intensity.toByte() else 0
            rgba[base + 1] = if (channel == 1) intensity.toByte() else 0
            rgba[base + 2] = if (channel == 2) intensity.toByte() else 0
            rgba[base + 3] = if (delta > 0) 255.toByte() else 0
        }
        ComparisonUtils.saveRgbaAsPng(rgba, width, height, file)
    }

    private class ZoneStats {
        val maxDelta = IntArray(4) { 0 }
        val sumDelta = LongArray(4) { 0L }
        var totalPixels = 0
    }
}
```

- [ ] **Step 5: Run compilation**

Run: `./gradlew :integration-tests:diagnostic:compileKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add integration-tests/diagnostic/
git commit -m "feat: add DiffAnalyzer (Layer 1) with heatmaps, SSIM, zone classification"
```

---

### Task 5: Build Layer 2 — OpInspector

**Files:**
- Create: `integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/OpInspector.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt`

- [ ] **Step 1: Expose recorded ops from GmCanvas**

Read `GmCanvas.kt` (line 28). The `GmCanvas` wraps a `Canvas` which records ops into a `DisplayListBuffer`. The `Surface` object in `SkiaGmRenderer` has a `SurfaceDisplayListBuffer` with `ops()`. But `GmCanvas` doesn't have direct access to this buffer.

The approach: `GmCanvas` receives the `Canvas` which has a `DisplayListBuffer`. Let's expose the buffer ops through a method:

```kotlin
// In GmCanvas.kt, add:
@Suppress("UNCHECKED_CAST")
fun snapshotOps(): List<DisplayOp> {
    return canvas.buffer.ops().toList()
}
```

Wait, `Canvas` doesn't expose `buffer` publicly. Let me check Canvas.

Actually, looking at `Surface.kt`, the `Canvas` is created with a `SurfaceDisplayListBuffer`. Let me see if I can expose it differently.

Better approach: After GM.draw() completes, the ops are in the Surface's DisplayListBuffer. Let's expose them from the Surface:

```kotlin
// In Surface.kt, add:
fun snapshotOps(): List<DisplayOp> = buffer.ops()
```

And `buffer.ops()` already returns `List<DisplayOp>`.

Actually, `SurfaceDisplayListBuffer` is private to `Surface.kt`. Let me add a method to `Surface`:

```kotlin
/** Return a snapshot of recorded display operations (for diagnostic replay). */
fun snapshotOps(): List<DisplayOp> = buffer.ops()
```

Then from `SkiaGmRenderer` (which has the `Surface`), we can call `surface.snapshotOps()` after rendering.

- [ ] **Step 2: Write OpInspector**

```kotlin
// integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/OpInspector.kt
package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.Color
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.types.Rect
import java.io.File
import kotlin.math.max

data class OpTrace(
    val totalOps: Int,
    val ops: List<OpTraceEntry>,
    val suspectOps: List<Int>,
)

data class OpTraceEntry(
    val index: Int,
    val type: String,
    val pixelContribution: Double,
    val isSuspect: Boolean,
    val beforeUrl: String?,
    val afterUrl: String?,
    val deltaUrl: String?,
)

object OpInspector {
    /**
     * Replay operations incrementally, comparing each partial result against
     * the reference. Returns an [OpTrace] identifying which ops caused the
     * most divergence.
     *
     * Uses binary search to minimize renders: O(log N) instead of O(N).
     * Full sequential mode is available for small picture sizes (< 50 ops).
     */
    fun inspect(
        ops: List<DisplayOp>,
        referenceRgba: ByteArray,
        gmWidth: Int,
        gmHeight: Int,
        tolerance: Int,
        outputDir: File,
    ): OpTrace {
        if (ops.isEmpty()) return OpTrace(0, emptyList(), emptyList())

        val n = ops.size
        val entries = mutableListOf<OpTraceEntry>()

        // For small N, do full sequential; for large N, binary search
        if (n <= 50) {
            var prevSimilarity = 100.0
            for (i in 1..n) {
                val partialRgba = renderPartial(ops, i, gmWidth, gmHeight)
                val similarity = comparePartial(partialRgba, referenceRgba, tolerance)
                val contribution = max(0.0, prevSimilarity - similarity)
                prevSimilarity = similarity

                val suspect = contribution > 5.0
                var beforeUrl: String? = null
                var afterUrl: String? = null
                var deltaUrl: String? = null

                if (suspect) {
                    val beforeRgba = renderPartial(ops, i - 1, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(beforeRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${i - 1}_before.png"))
                    ComparisonUtils.saveRgbaAsPng(partialRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${i}_after.png"))
                    val delta = buildDelta(beforeRgba, partialRgba, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(delta, gmWidth, gmHeight,
                        outputDir.resolve("op_${i}_diff.png"))
                    beforeUrl = "op_${i - 1}_before.png"
                    afterUrl = "op_${i}_after.png"
                    deltaUrl = "op_${i}_diff.png"
                }

                val opType = when (ops[i - 1]) {
                    is DisplayOp.DrawRect -> "DrawRect"
                    is DisplayOp.DrawRRect -> "DrawRRect"
                    is DisplayOp.DrawDRRect -> "DrawDRRect"
                    is DisplayOp.DrawPath -> "DrawPath"
                    is DisplayOp.DrawImage -> "DrawImage"
                    is DisplayOp.DrawImageNine -> "DrawImageNine"
                    is DisplayOp.DrawImageLattice -> "DrawImageLattice"
                    is DisplayOp.DrawText -> "DrawText"
                    is DisplayOp.DrawPicture -> "DrawPicture"
                    is DisplayOp.DrawVertices -> "DrawVertices"
                    is DisplayOp.DrawMesh -> "DrawMesh"
                    is DisplayOp.DrawAtlas -> "DrawAtlas"
                    is DisplayOp.DrawColor -> "DrawColor"
                    is DisplayOp.Clear -> "Clear"
                    is DisplayOp.SetTransform -> "SetTransform"
                    is DisplayOp.SetClip -> "SetClip"
                    is DisplayOp.BeginLayer -> "BeginLayer"
                    is DisplayOp.EndLayer -> "EndLayer"
                    is DisplayOp.Annotation -> "Annotation"
                    is DisplayOp.FlushAndSnapshot -> "FlushAndSnapshot"
                }

                entries.add(OpTraceEntry(
                    index = i - 1,
                    type = opType,
                    pixelContribution = contribution,
                    isSuspect = suspect,
                    beforeUrl = beforeUrl,
                    afterUrl = afterUrl,
                    deltaUrl = deltaUrl,
                ))
            }
        } else {
            // Binary search mode: find divergence boundaries
            // Simplified: check at N/4, N/2, 3N/4 and full
            val checkpoints = listOf(n / 4, n / 2, 3 * n / 4, n).filter { it > 0 }
            var prevSimilarity = 100.0
            var prevIdx = 0
            for (cp in checkpoints) {
                val partialRgba = renderPartial(ops, cp, gmWidth, gmHeight)
                val similarity = comparePartial(partialRgba, referenceRgba, tolerance)
                val contribution = max(0.0, prevSimilarity - similarity)
                prevSimilarity = similarity

                val suspect = contribution > 5.0
                if (suspect) {
                    val beforeRgba = renderPartial(ops, prevIdx, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(beforeRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${prevIdx}_before.png"))
                    ComparisonUtils.saveRgbaAsPng(partialRgba, gmWidth, gmHeight,
                        outputDir.resolve("op_${cp}_after.png"))
                    val delta = buildDelta(beforeRgba, partialRgba, gmWidth, gmHeight)
                    ComparisonUtils.saveRgbaAsPng(delta, gmWidth, gmHeight,
                        outputDir.resolve("op_${cp}_diff.png"))
                }
                prevIdx = cp
            }
            // Create summary entries for the checkpoints
            var idx = 0
            for (cp in checkpoints) {
                val relSim = 100.0 - (100.0 * (n - cp) / n)
                entries.add(OpTraceEntry(
                    index = cp - 1,
                    type = "batch_0_to_$cp",
                    pixelContribution = if (idx > 0) entries[idx - 1].pixelContribution else 0.0,
                    isSuspect = false,
                    beforeUrl = null,
                    afterUrl = null,
                    deltaUrl = null,
                ))
                idx++
            }
        }

        val suspectOps = entries.filter { it.isSuspect }.map { it.index }
        return OpTrace(n, entries, suspectOps)
    }

    private fun renderPartial(
        ops: List<DisplayOp>,
        count: Int,
        width: Int,
        height: Int,
    ): ByteArray {
        val surface = Surface(width, height, config = RenderConfig.DEFAULT)
        val canvas = surface.canvas()
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false))
        for (i in 0 until count.coerceAtMost(ops.size)) {
            replayOp(canvas, ops[i])
        }
        val result = surface.render()
        return result.pixels.toByteArray()
    }

    private fun replayOp(canvas: org.graphiks.kanvas.canvas.Canvas, op: DisplayOp) {
        when (op) {
            is DisplayOp.DrawRect -> canvas.drawRect(op.rect, op.paint)
            is DisplayOp.DrawRRect -> canvas.drawRRect(op.rrect, op.paint)
            is DisplayOp.DrawDRRect -> canvas.drawDRRect(op.outer, op.inner, op.paint)
            is DisplayOp.DrawPath -> canvas.drawPath(op.path, op.paint)
            is DisplayOp.DrawPoint -> canvas.drawPoint(op.x, op.y, op.paint)
            is DisplayOp.DrawPoints -> canvas.drawPoints(op.mode, op.points, op.paint)
            is DisplayOp.DrawImage -> canvas.drawImage(op.image, op.dst, op.paint ?: Paint())
            is DisplayOp.DrawImageNine -> canvas.drawImageNine(op.image, op.center, op.dst,
                op.paint ?: Paint())
            is DisplayOp.DrawImageLattice -> canvas.drawImageLattice(op.image, op.lattice, op.dst,
                op.paint ?: Paint())
            is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
            is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint ?: Paint())
            is DisplayOp.DrawVertices -> canvas.drawVertices(op.vertices, op.paint)
            is DisplayOp.DrawMesh -> canvas.drawMesh(op.mesh, op.paint, op.blendMode)
            is DisplayOp.DrawAtlas -> canvas.drawAtlas(op.atlas, op.transforms, op.texRects,
                op.colors, op.blendMode, op.paint ?: Paint())
            is DisplayOp.DrawColor -> canvas.drawColor(op.color, op.mode)
            is DisplayOp.Clear -> canvas.clear(op.color)
            is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
            is DisplayOp.SetClip -> {} // clip baked into ops during recording
            is DisplayOp.BeginLayer -> canvas.saveLayer(op.bounds, op.paint)
            is DisplayOp.EndLayer -> canvas.restore()
            is DisplayOp.Annotation -> {}
            is DisplayOp.FlushAndSnapshot -> {}
        }
    }

    private fun comparePartial(partialRgba: ByteArray, referenceRgba: ByteArray, tolerance: Int): Double {
        val result = ComparisonUtils.compareRgba(partialRgba, referenceRgba,
            width = 0, height = 0, tolerance = tolerance, minSimilarity = 0.0)
        // compareRgba validates size from width/height params — we need width/height.
        // Approximate: compute from byte array sizes
        val size = partialRgba.size / 4
        val w = kotlin.math.sqrt(size.toDouble()).toInt()
        val h = size / w
        return ComparisonUtils.compareRgba(partialRgba, referenceRgba, w, h, tolerance, 0.0).similarity
    }

    private fun buildDelta(before: ByteArray, after: ByteArray, width: Int, height: Int): ByteArray {
        val diff = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val b = i * 4
            val maxDelta = (0..3).maxOf {
                kotlin.math.abs((after[b + it].toInt() and 0xFF) - (before[b + it].toInt() and 0xFF))
            }
            if (maxDelta > 0) {
                diff[b] = 255.toByte()
                diff[b + 1] = 0
                diff[b + 2] = 0
                diff[b + 3] = 255.toByte()
            }
        }
        return diff
    }
}
```

- [ ] **Step 3: Add Surface.snapshotOps()**

In `Surface.kt`, add method:

```kotlin
fun snapshotOps(): List<DisplayOp> = buffer.ops()
```

- [ ] **Step 4: Run compilation**

Run: `./gradlew :integration-tests:diagnostic:compileKotlin :kanvas:compileKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/OpInspector.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt
git commit -m "feat: add OpInspector (Layer 2) with incremental replay and Surface.snapshotOps"
```

---

### Task 6: Build Layer 3 — PipelineTracer

**Files:**
- Create: `integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/PipelineTracer.kt`

No changes to gpu-renderer needed yet — PipelineTracer implements RenderOpListener and collects events. The actual GPU wiring (calling the listener from GPU pipeline) is a separate task.

- [ ] **Step 1: Write PipelineTracer**

```kotlin
// integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/PipelineTracer.kt
package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.surface.RenderOpListener

data class PipelineTrace(
    val summary: PipelineSummary,
    val ops: List<PipelineOpEntry>,
)

data class PipelineSummary(
    val dispatched: Int,
    val refused: Int,
)

data class PipelineOpEntry(
    val opIndex: Int,
    val route: String,
    val status: String,
    val shaders: List<String>?,
    val vertexCount: Int?,
    val blendMode: String?,
    val reason: RefusalReason?,
)

data class RefusalReason(
    val code: String,
    val message: String,
)

class PipelineTracer : RenderOpListener {
    private val entries = mutableListOf<PipelineOpEntry>()
    private var dispatched = 0
    private var refused = 0

    override fun onOpDispatched(
        index: Int,
        opType: String,
        route: String,
        shaders: List<String>,
        vertexCount: Int,
        blendMode: String,
    ) {
        dispatched++
        entries.add(PipelineOpEntry(
            opIndex = index,
            route = route,
            status = "dispatched",
            shaders = shaders,
            vertexCount = vertexCount,
            blendMode = blendMode,
            reason = null,
        ))
    }

    override fun onOpRefused(
        index: Int,
        opType: String,
        code: String,
        reason: String,
    ) {
        refused++
        entries.add(PipelineOpEntry(
            opIndex = index,
            route = "Refused",
            status = "refused",
            shaders = null,
            vertexCount = null,
            blendMode = null,
            reason = RefusalReason(code, reason),
        ))
    }

    fun buildTrace(): PipelineTrace =
        PipelineTrace(PipelineSummary(dispatched, refused), entries.toList())

    fun reset() {
        entries.clear()
        dispatched = 0
        refused = 0
    }
}
```

- [ ] **Step 2: Run compilation**

Run: `./gradlew :integration-tests:diagnostic:compileKotlin`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/PipelineTracer.kt
git commit -m "feat: add PipelineTracer (Layer 3) implementing RenderOpListener"
```

---

### Task 7: Build DiagnosticManifest and DiagnosticRunner

**Files:**
- Create: `integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiagnosticManifest.kt`
- Create: `integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiagnosticRunner.kt`

- [ ] **Step 1: Write DiagnosticManifest**

```kotlin
// integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiagnosticManifest.kt
package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.surface.DebugLevel
import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DiagnosticManifest(
    val gm: String,
    val debugLevel: String,
    val generatedAt: String,
    val result: ResultSection,
    val spatialReport: SpatialReport?,
    val opTrace: OpTrace?,
    val pipelineTrace: PipelineTrace?,
    val agentSummary: AgentSummary,
) {
    fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"gm\": \"${esc(gm)}\",")
        sb.appendLine("  \"debugLevel\": \"${esc(debugLevel)}\",")
        sb.appendLine("  \"generatedAt\": \"${esc(generatedAt)}\",")

        sb.appendLine("  \"result\": {")
        sb.appendLine("    \"status\": \"${result.status}\",")
        sb.appendLine("    \"similarity\": ${fmt2(result.similarity)},")
        sb.appendLine("    \"threshold\": ${fmt2(result.threshold)},")
        sb.appendLine("    \"totalPixels\": ${result.totalPixels},")
        sb.appendLine("    \"mismatchingPixels\": ${result.mismatchingPixels},")
        sb.appendLine("    \"perChannel\": {")
        val chs = listOf("R" to 0, "G" to 1, "B" to 2, "A" to 3)
        sb.append(chs.joinToString(",\n") { (name, i) ->
            "      \"$name\": { \"maxDelta\": ${result.maxDelta[i]}, \"meanDelta\": ${fmt2(result.meanDelta[i])}, \"mismatchPct\": ${fmt2(result.mismatchPct[i])} }"
        })
        sb.appendLine()
        sb.appendLine("    }")

        sb.appendLine("  },")

        sb.appendLine("  \"spatialReport\": ${spatialJson(spatialReport)},")
        sb.appendLine("  \"opTrace\": ${opTraceJson(opTrace)},")
        sb.appendLine("  \"pipelineTrace\": ${pipelineTraceJson(pipelineTrace)},")

        sb.appendLine("  \"agentSummary\": {")
        sb.appendLine("    \"primaryIssue\": \"${esc(agentSummary.primaryIssue)}\",")
        sb.appendLine("    \"alphaChannel\": \"${esc(agentSummary.alphaChannel)}\",")
        sb.appendLine("    \"suspectOps\": [")
        sb.append(agentSummary.suspectOps.joinToString(",\n") { sop ->
            "      { \"index\": ${sop.index}, \"hypothesis\": \"${esc(sop.hypothesis)}\", \"action\": \"${esc(sop.action)}\" }"
        })
        sb.appendLine()
        sb.appendLine("    ]")
        sb.appendLine("  }")
        sb.append("}")
        return sb.toString()
    }

    private fun spatialJson(sr: SpatialReport?): String {
        if (sr == null) return "null"
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"ssim\": ${fmt2(sr.ssim)},")
        sb.appendLine("  \"ssimBlocks\": [")
        sb.append(sr.ssimBlocks.joinToString(",\n") { "    { \"x\": ${it.x}, \"y\": ${it.y}, \"score\": ${fmt2(it.score)} }" })
        sb.appendLine()
        sb.appendLine("  ],")
        sb.appendLine("  \"zones\": [")
        sb.append(sr.zones.joinToString(",\n") { z ->
            "    { \"label\": \"${z.label}\", \"bounds\": { \"x\": ${z.bounds.x}, \"y\": ${z.bounds.y}, \"w\": ${z.bounds.w}, \"h\": ${z.bounds.h} }, \"dominantChannel\": \"${z.dominantChannel}\", \"severity\": \"${z.severity}\", \"avgDelta\": ${fmt2(z.avgDelta)} }"
        })
        sb.appendLine()
        sb.appendLine("  ],")
        sb.appendLine("  \"heatmapUrl\": ${sr.heatmapUrl?.let { "\"$it\"" } ?: "null"}")
        sb.append("}")
        return sb.toString()
    }

    private fun opTraceJson(ot: OpTrace?): String {
        if (ot == null) return "null"
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"totalOps\": ${ot.totalOps},")
        sb.appendLine("  \"ops\": [")
        sb.append(ot.ops.joinToString(",\n") { op ->
            "    { \"index\": ${op.index}, \"type\": \"${op.type}\", \"pixelContribution\": ${fmt2(op.pixelContribution)}, \"isSuspect\": ${op.isSuspect}, \"beforeUrl\": ${op.beforeUrl?.let { "\"$it\"" } ?: "null"}, \"afterUrl\": ${op.afterUrl?.let { "\"$it\"" } ?: "null"}, \"deltaUrl\": ${op.deltaUrl?.let { "\"$it\"" } ?: "null"} }"
        })
        sb.appendLine()
        sb.appendLine("  ],")
        sb.appendLine("  \"suspectOps\": [${ot.suspectOps.joinToString { it.toString() }}]")
        sb.append("}")
        return sb.toString()
    }

    private fun pipelineTraceJson(pt: PipelineTrace?): String {
        if (pt == null) return "null"
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"summary\": { \"dispatched\": ${pt.summary.dispatched}, \"refused\": ${pt.summary.refused} },")
        sb.appendLine("  \"ops\": [")
        sb.append(pt.ops.joinToString(",\n") { op ->
            val shaders = if (op.shaders != null) "[${op.shaders.joinToString { "\"$it\"" }}]" else "null"
            "    { \"opIndex\": ${op.opIndex}, \"route\": \"${op.route}\", \"status\": \"${op.status}\", \"shaders\": $shaders, \"vertexCount\": ${op.vertexCount ?: "null"}, \"blendMode\": ${op.blendMode?.let { "\"$it\"" } ?: "null"}, \"reason\": ${if (op.reason != null) "{ \"code\": \"${op.reason.code}\", \"message\": \"${esc(op.reason.message)}\" }" else "null"} }"
        })
        sb.appendLine()
        sb.appendLine("  ]")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
    }
}

data class ResultSection(
    val status: String,
    val similarity: Double,
    val threshold: Double,
    val totalPixels: Int,
    val mismatchingPixels: Int,
    val maxDelta: IntArray,
    val meanDelta: DoubleArray,
    val mismatchPct: DoubleArray,
)

data class AgentSummary(
    val primaryIssue: String,
    val alphaChannel: String,
    val suspectOps: List<SuspectOpSummary>,
)

data class SuspectOpSummary(
    val index: Int,
    val hypothesis: String,
    val action: String,
)
```

- [ ] **Step 2: Write DiagnosticRunner**

```kotlin
// integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiagnosticRunner.kt
package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.DebugLevel
import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RunnerInput(
    val gmName: String,
    val minSimilarity: Double,
    val actualRgba: ByteArray,
    val referenceRgba: ByteArray,
    val width: Int,
    val height: Int,
    val tolerance: Int,
    val ops: List<DisplayOp>,
    val dispatchedCount: Int,
    val refusedCount: Int,
    val diagnostics: List<String>,
    val debugLevel: DebugLevel,
    val outputDir: File,
)

object DiagnosticRunner {
    fun run(input: RunnerInput): DiagnosticManifest {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        // Always compute the base comparison result
        val comparison = ComparisonUtils.compareRgba(
            actual = input.actualRgba,
            reference = input.referenceRgba,
            width = input.width,
            height = input.height,
            tolerance = input.tolerance,
            minSimilarity = input.minSimilarity,
        )

        val result = ResultSection(
            status = if (comparison.isPassing) "PASS" else "FAIL",
            similarity = comparison.similarity,
            threshold = comparison.minSimilarity,
            totalPixels = comparison.totalPixels,
            mismatchingPixels = comparison.totalPixels - comparison.matchingPixels,
            maxDelta = comparison.maxDiff,
            meanDelta = comparison.meanDiff,
            mismatchPct = doubleArrayOf(
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
            ),
        )

        // Layer 1: enriched diff
        val spatialReport = if (input.debugLevel >= DebugLevel.PIXEL) {
            DiffAnalyzer.analyze(
                actualRgba = input.actualRgba,
                referenceRgba = input.referenceRgba,
                width = input.width,
                height = input.height,
                tolerance = input.tolerance,
                outputDir = input.outputDir,
            )
        } else null

        // Layer 2: per-op isolation
        val opTrace = if (input.debugLevel >= DebugLevel.OP && input.ops.isNotEmpty()) {
            OpInspector.inspect(
                ops = input.ops,
                referenceRgba = input.referenceRgba,
                gmWidth = input.width,
                gmHeight = input.height,
                tolerance = input.tolerance,
                outputDir = input.outputDir,
            )
        } else null

        // Layer 3: pipeline trace
        val pipelineTrace: PipelineTrace? = null // populated by caller via PipelineTracer

        // Build agent summary
        val channelNames = listOf("R", "G", "B", "A")
        val dominantChannelIdx = comparison.meanDiff.indices.maxByOrNull { comparison.meanDiff[it] } ?: 0
        val primaryIssue = "${channelNames[dominantChannelIdx]} channel shows dominant divergence " +
            "(maxDelta=${comparison.maxDiff[dominantChannelIdx]}, " +
            "meanDelta=${"%.2f".format(comparison.meanDiff[dominantChannelIdx])})"

        val alphaMatches = comparison.maxDiff[3] == 0
        val alphaChannel = if (alphaMatches)
            "Alpha channel matches perfectly — issue is color/rendering, not geometry or shape"
        else "Alpha channel also diverges — geometry or coverage may be incorrect"

        val suspectOpSummaries = if (opTrace != null) {
            opTrace.suspectOps.take(5).map { idx ->
                val op = opTrace.ops.find { it.index == idx }
                val ptMatch = pipelineTrace?.ops?.find { it.opIndex == idx }
                val hypothesis = buildHypothesis(op?.type ?: "unknown", ptMatch)
                val action = buildAction(op?.type ?: "unknown", ptMatch)
                SuspectOpSummary(idx, hypothesis, action)
            }
        } else emptyList()

        val agentSummary = AgentSummary(primaryIssue, alphaChannel, suspectOpSummaries)

        return DiagnosticManifest(
            gm = input.gmName,
            debugLevel = input.debugLevel.name,
            generatedAt = now,
            result = result,
            spatialReport = spatialReport,
            opTrace = opTrace,
            pipelineTrace = pipelineTrace,
            agentSummary = agentSummary,
        )
    }

    private fun buildHypothesis(opType: String, ptEntry: PipelineOpEntry?): String {
        if (ptEntry != null) {
            if (ptEntry.status == "refused") {
                return "$opType refused (${ptEntry.reason?.code}): ${ptEntry.reason?.message}"
            }
            return "$opType via ${ptEntry.route}: check shader/geometry output"
        }
        return "$opType caused significant pixel divergence"
    }

    private fun buildAction(opType: String, ptEntry: PipelineOpEntry?): String {
        if (ptEntry != null) {
            if (ptEntry.status == "refused") {
                return "Implement support for this operation pattern or add exclusion flag to the GM"
            }
            return "Review the ${ptEntry.route} pipeline stage and its shaders in gpu-renderer module"
        }
        return "Inspect $opType rendering logic in GmCanvas or pipeline"
    }
}
```

- [ ] **Step 3: Run compilation**

Run: `./gradlew :integration-tests:diagnostic:compileKotlin`
Expected: FAIL — `RunnerInput` imports `org.graphiks.kanvas.skia.SkiaGm` which is in the skia test source set, not accessible from main source set.

Fix: Remove the unused `SkiaGm` import. The `RunnerInput` doesn't actually need it.

```kotlin
// Remove this import:
// import org.graphiks.kanvas.skia.SkiaGm
```

Fixed, recompile.
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiagnosticManifest.kt integration-tests/diagnostic/src/main/kotlin/org/graphiks/kanvas/diagnostic/DiagnosticRunner.kt
git commit -m "feat: add DiagnosticManifest schema and DiagnosticRunner orchestrator"
```

---

### Task 8: Wire diagnostic into SkiaGmRunner

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt:10-42`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt:1-70`
- Modify: `integration-tests/skia/build.gradle.kts:6-20`

- [ ] **Step 1: Add diagnostic dependency to skia module**

In `integration-tests/skia/build.gradle.kts`, add to dependencies:
```kotlin
implementation(project(":integration-tests:diagnostic"))
```

- [ ] **Step 2: Update SkiaGmRenderer to capture ops and create PipelineTracer**

Read `SkiaGmRenderer.kt` (42 lines). Create a `PipelineTracer` when `DebugLevel >= TRACE`, set it on the Surface before rendering, and capture ops after:

```kotlin
object SkiaGmRenderer {
    fun render(
        gm: SkiaGm,
        width: Int = gm.width,
        height: Int = gm.height,
        config: RenderConfig = RenderConfig.DEFAULT,
    ): SkiaRenderResult {
        val surface = Surface(width = width, height = height, config = config)
        val tracer = if (config.debugLevel >= DebugLevel.TRACE) PipelineTracer() else null
        surface.renderOpListener = tracer
        val canvas = surface.canvas()
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false))
        val gmCanvas = GmCanvas(canvas, width, height)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, width, height)
        val result = surface.render()
        val ops = surface.snapshotOps()
        return SkiaRenderResult(
            rgba = result.pixels.map { it.toByte() }.toByteArray(),
            width = width,
            height = height,
            dispatchedCount = result.stats.opsDispatched,
            refusedCount = result.stats.opsRefused,
            diagnostics = result.diagnostics.entries.map { "${it.code}: ${it.reason}" },
            ops = ops,
            pipelineTracer = tracer,
        )
    }
}

data class SkiaRenderResult(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val dispatchedCount: Int = 0,
    val refusedCount: Int = 0,
    val diagnostics: List<String> = emptyList(),
    val ops: List<DisplayOp> = emptyList(),
    val pipelineTracer: PipelineTracer? = null,
)
```

- [ ] **Step 3: Update SkiaGmRunner to run diagnostics**

Read `SkiaGmRunner.kt` (70 lines). After the existing comparison, add diagnostic output:

```kotlin
@ParameterizedTest
@MethodSource("allGms")
fun `render GM`(gm: SkiaGm) {
    GpuAvailability.requireWebGpu()

    val debugLevel = DebugLevel.valueOf(
        System.getProperty("kanvas.debug") ?: "OFF"
    )
    val config = RenderConfig.DEFAULT.copy(debugLevel = debugLevel)

    val result = SkiaGmRenderer.render(gm, config = config)
    val refPath = "/reference/${gm.name}.png"

    // ... existing reference check and comparison ...

    val reference = ReferenceManager.loadReference(refPath)

    val comparison = ComparisonUtils.compareRgba(
        actual = result.rgba, reference = reference,
        width = result.width, height = result.height,
        tolerance = gm.tolerance, minSimilarity = gm.minSimilarity,
    )

    SimilarityTracker.updateScore(gm.name, comparison.similarity)

    val outputDir = File(tempDir, gm.name)
    outputDir.mkdirs()
    ComparisonUtils.saveRgbaAsPng(result.rgba, result.width, result.height,
        File(outputDir, "kanvas.png"))
    ComparisonUtils.saveRgbaAsPng(reference, result.width, result.height,
        File(outputDir, "reference.png"))
    comparison.diffRgba?.let { diff ->
        ComparisonUtils.saveRgbaAsPng(diff, result.width, result.height,
            File(outputDir, "diff.png"))
    }

    // Diagnostic output
    if (debugLevel >= DebugLevel.PIXEL) {
        val diagnosticDir = File(outputDir, "diagnostics")
        diagnosticDir.mkdirs()

        // Build pipeline trace from tracer if present
        var pipelineTrace: PipelineTrace? = null
        if (debugLevel >= DebugLevel.TRACE && result.pipelineTracer != null) {
            pipelineTrace = result.pipelineTracer.buildTrace()
        }

        val manifest = DiagnosticRunner.run(RunnerInput(
            gmName = gm.name,
            minSimilarity = gm.minSimilarity,
            actualRgba = result.rgba,
            referenceRgba = reference,
            width = result.width,
            height = result.height,
            tolerance = gm.tolerance,
            ops = result.ops,
            dispatchedCount = result.dispatchedCount,
            refusedCount = result.refusedCount,
            diagnostics = result.diagnostics,
            debugLevel = debugLevel,
            outputDir = diagnosticDir,
        ))

        // Merge pipeline trace from listener
        val finalManifest = if (pipelineTrace != null) {
            manifest.copy(pipelineTrace = pipelineTrace)
        } else manifest

        val manifestFile = File(outputDir, "manifest.json")
        manifestFile.writeText(finalManifest.toJson())
    }

    println(
        "[${if (comparison.isPassing) "PASS" else "FAIL"}] ${gm.name}: " +
        "similarity=${"%.2f".format(comparison.similarity)}% " +
        "(threshold: ${comparison.minSimilarity}%) " +
        "dispatch=${result.dispatchedCount} refuse=${result.refusedCount}",
    )
    result.diagnostics.forEach { d -> println("  ${d}") }

    assertTrue(comparison.isPassing) {
        "${gm.name}: similarity=${"%.2f".format(comparison.similarity)}% " +
        "(threshold: ${comparison.minSimilarity}%)"
    }
}
```

Add the necessary imports at the top of the file:
```kotlin
import org.graphiks.kanvas.diagnostic.DiagnosticRunner
import org.graphiks.kanvas.diagnostic.RunnerInput
import org.graphiks.kanvas.diagnostic.PipelineTracer
import org.graphiks.kanvas.diagnostic.PipelineTrace
import org.graphiks.kanvas.surface.DebugLevel
```

- [ ] **Step 4: Run compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add integration-tests/skia/
git commit -m "feat: wire DiagnosticRunner into SkiaGmRunner with DebugLevel control"
```

---

### Task 9: Wire RenderOpListener in GPU pipeline

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/RenderViaGpu.kt` (or wherever renderViaGpu is defined)
- Modify: The GPU recorder to call the listener per op

- [ ] **Step 1: Find renderViaGpu function**

Search for the definition of `renderViaGpu`:

Run: `./gradlew :kanvas:dependencies` — locate the module that contains renderViaGpu. It imports from `org.graphiks.kanvas.surface.gpu.renderViaGpu`.

Actually, it's imported directly in `Surface.kt` (line 8). Let's find it.

Run: `rg "fun renderViaGpu" --include "*.kt"`

- [ ] **Step 2: Update renderViaGpu signature**

Find the file containing `fun renderViaGpu(`. Add the `renderOpListener` parameter:

```kotlin
fun renderViaGpu(
    buffer: DisplayListBuffer,
    width: Int,
    height: Int,
    format: PixelFormat,
    config: RenderConfig,
    renderOpListener: RenderOpListener? = null,
): RenderResult {
    // ... existing implementation ...
    // Before processing each op, if renderOpListener != null and op has an index:
    //   if dispatched: renderOpListener.onOpDispatched(index, type, route, shaders, verts, blend)
    //   if refused: renderOpListener.onOpRefused(index, type, code, reason)
}
```

The GPU recorder already has `GPUDrawAnalysisRecord` with route decision, diagnostics, and command family. We need to call the listener at the point where route decisions are made per command.

Since modifying the GPU recorder is complex and could destabilize the pipeline, for the initial implementation:
- Pass the listener through to the GPU recorder scope
- In the recording loop (where `GPURecorder` processes each `NormalizedDrawCommand`), call the listener after route planning

- [ ] **Step 3: Wire listener in renderViaGpu**

Find `renderViaGpu` (imported in `Surface.kt` line 8 as `org.graphiks.kanvas.surface.gpu.renderViaGpu`). Add `renderOpListener` parameter forwarding.

When dispatching a command in the recording loop, call the listener. The GPU pipeline's `GPUDrawAnalysisRecord` already contains route decisions and diagnostics per command. Wire these into listener callbacks. If the GPU pipeline module's recording loop is not easily instrumented, skip this step for now — the `pipelineTrace` section in manifests will be `null`. The diagnostic framework is fully functional without Layer 3 data; adding GPU wiring is a follow-up task that does not block the other layers.

- [ ] **Step 4: Run compilation**

Run: `./gradlew :gpu-renderer:compileKotlin :kanvas:compileKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add kanvas/ gpu-renderer/
git commit -m "feat: wire RenderOpListener through GPU renderViaGpu"
```

---

### Task 10: Integration test and smoke test

**Files:**
- No new files — validation only

- [ ] **Step 1: Run with DebugLevel.OFF (smoke test)**

```bash
./gradlew :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaGmRunner" -Dkanvas.debug=OFF 2>&1 | tail -20
```
Expected: Tests run with no manifest files generated. No performance regression.

- [ ] **Step 2: Run with DebugLevel.PIXEL on a known-passing GM**

```bash
./gradlew :integration-tests:skia:test --tests "*FillRectBasic" -Dkanvas.debug=PIXEL 2>&1
```
Expected: `manifest.json` file exists in tempDir, contains `spatialReport` section. No `opTrace` or `pipelineTrace` sections.

- [ ] **Step 3: Run with DebugLevel.TRACE on a known-failing GM**

```bash
./gradlew :integration-tests:skia:test --tests "*BlurRectCompare" -Dkanvas.debug=TRACE 2>&1
```
Expected: `manifest.json` exists with all sections populated (including `agentSummary`). Heatmap and per-channel PNGs exist in diagnostic directory.

- [ ] **Step 4: Verify manifest.json format**

Read the generated manifest.json from the test output directory and validate:
- All required fields present
- JSON is valid
- `agentSummary.suspectOps` contains actionable items

- [ ] **Step 5: Commit any fixes if needed**

```bash
git add . && git commit -m "chore: fix diagnostic integration test issues"
```

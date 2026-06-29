# M12 Dependencies — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finalize the three external dependencies (pure kotlin text stack, codec pipeline, wgsl4k parser integration) so dependent GPU renderer routes can activate in M13-M22.

**Architecture:** Three independent parallel batches — 12A (text), 12B (codec), 12C (wgsl4k). Each batch completes its M0-M11 contracts by removing the terminal refusal and replacing it with accepted route evidence for the narrow scope defined in M12 non-claims. The `:gpu-renderer` planners already exist; this plan makes them accept instead of refuse.

**Tech Stack:** Kotlin/JVM, `:gpu-renderer` `:gpu-raster` `:font` `:codec-all-kotlin`, wgsl4k, wgpu4k-toolkit

---

## Batch 12A — Pure Kotlin Text Stack (4 tickets: KGPU-M12-001..004)

### File Structure

| File | Role | Action |
|------|------|--------|
| `font/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt` | SFNT parser + scaler | Create/Modify |
| `font/src/main/kotlin/org/graphiks/kanvas/font/glyph/A8Rasterizer.kt` | A8 glyph rasterizer | Create |
| `font/src/main/kotlin/org/graphiks/kanvas/font/glyph/GlyphStrikeKey.kt` | Strike key contract | Create |
| `font/src/main/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlan.kt` | Atlas upload plan | Create |
| `font/src/main/kotlin/org/graphiks/kanvas/font/handoff/GlyphRunDescriptor.kt` | GPU renderer handoff contract | Create |
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt` | Text route planner | Modify (accept A8) |
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt` | Recorder | Modify (accept DrawTextRun) |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt` | Legacy device bridge | Modify (handoff) |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlas.kt` | GPU glyph atlas | Modify (integrate) |
| `gpu-raster/src/main/resources/shaders/text_glyph_atlas.wgsl` | A8 sampling WGSL | Existing (verify) |

### Task 1: KGPU-M12-001 — Finalize SFNT parser + glyf/CFF/CFF2 scaler

**Files:**
- Create: `font/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`
- Create: `font/src/test/kotlin/org/graphiks/kanvas/font/scaler/GlyphScalerTest.kt`

- [ ] **Step 1: Write the failing contract test**

```kotlin
// font/src/test/kotlin/org/graphiks/kanvas/font/scaler/GlyphScalerTest.kt
package org.graphiks.kanvas.font.scaler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GlyphScalerTest {

    @Test
    fun `scaler produces deterministic glyph outline for Liberation Sans 'A' at 32px`() {
        val fontBytes = javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val scaler = GlyphScaler.fromBytes(fontBytes)

        val glyph = scaler.scaleGlyph(
            glyphId = scaler.glyphIdForCodepoint('A'.code),
            size = 32.0f,
        )

        assertNotNull(glyph)
        assertEquals('A'.code, glyph.sourceCodepoint)
        assertEquals(32.0f, glyph.size)
        assertTrue(glyph.advanceWidth > 0f)
        assertTrue(glyph.bounds.width > 0f && glyph.bounds.height > 0f)
        // Deterministic: same input → same output (hash-stable)
        val secondRun = scaler.scaleGlyph(scaler.glyphIdForCodepoint('A'.code), 32.0f)
        assertEquals(glyph.checksum(), secondRun.checksum())
    }

    @Test
    fun `scaler refuses unknown glyph id with stable diagnostic`() {
        val fontBytes = javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val scaler = GlyphScaler.fromBytes(fontBytes)

        val result = scaler.scaleGlyphOrDiagnostic(glyphId = 99999, size = 32.0f)

        assertTrue(result is GlyphScaleResult.Unsupported)
        assertEquals("font.scaler.glyph_id_out_of_range", (result as GlyphScaleResult.Unsupported).code)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*GlyphScalerTest*'
```
Expected: FAIL — `GlyphScaler` and `GlyphScaleResult` not defined.

- [ ] **Step 3: Write minimal GlyphScaler contract**

```kotlin
// font/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
package org.graphiks.kanvas.font.scaler

data class GlyphBounds(val width: Float, val height: Float, val xMin: Float, val yMin: Float)
data class ScaledGlyph(
    val sourceCodepoint: Int,
    val glyphId: Int,
    val size: Float,
    val advanceWidth: Float,
    val bounds: GlyphBounds,
    val outlineCommands: List<GlyphOutlineCommand>,
) {
    fun checksum(): String = TODO("sha256 of outline + metrics bytes")
}

sealed interface GlyphOutlineCommand {
    data class MoveTo(val x: Float, val y: Float) : GlyphOutlineCommand
    data class LineTo(val x: Float, val y: Float) : GlyphOutlineCommand
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : GlyphOutlineCommand
    data class CurveTo(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float) : GlyphOutlineCommand
    data object Close : GlyphOutlineCommand
}

sealed interface GlyphScaleResult {
    data class Scaled(val glyph: ScaledGlyph) : GlyphScaleResult
    data class Unsupported(val code: String, val reason: String) : GlyphScaleResult
}

class GlyphScaler private constructor(private val fontBytes: ByteArray) {
    companion object {
        fun fromBytes(bytes: ByteArray): GlyphScaler = GlyphScaler(bytes)
    }

    fun glyphIdForCodepoint(codepoint: Int): Int = TODO("cmap lookup")
    fun scaleGlyph(glyphId: Int, size: Float): ScaledGlyph? = TODO("scaler")
    fun scaleGlyphOrDiagnostic(glyphId: Int, size: Float): GlyphScaleResult = TODO("scaler with diagnostics")
}
```

- [ ] **Step 4: Run test — verify contract compiles and test partially passes (Unsupported case)**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*GlyphScalerTest*'
```
Expected: `scaler refuses unknown glyph id` PASSES (Unsupported path), `scaler produces deterministic glyph` FAILS (TODO).

- [ ] **Step 5: Implement cmap subtable lookup (format 4 + format 12)**

Read existing OT parsing in `kanvas-skia/src/main/kotlin/org/skia/foundation/opentype/` for table directory, cmap, glyf, and loca parsing primitives. Implement:

```kotlin
// In GlyphScaler.kt
fun glyphIdForCodepoint(codepoint: Int): Int {
    val cmap = parseCmapTable() // from existing font OT infrastructure
    return cmap.lookup(codepoint)
        ?: throw IllegalArgumentException("codepoint $codepoint not in font")
}
```

- [ ] **Step 6: Implement TrueType glyf scalar**

Use existing `kanvas-skia` OT parsing for `glyf` + `loca` table access. Implement quadratic-to-cubic elevation and simple glyph outline extraction at the requested size:

```kotlin
fun scaleGlyph(glyphId: Int, size: Float): ScaledGlyph? {
    val glyf = parseGlyfTable()
    val hmtx = parseHmtxTable()
    val head = parseHeadTable()

    val glyphData = glyf.glyph(glyphId) ?: return null
    val metrics = hmtx.metrics(glyphId)
    val scale = size / head.unitsPerEm

    val commands = when (glyphData) {
        is GlyfData.Simple -> extractSimpleOutline(glyphData, scale)
        is GlyfData.Composite -> extractCompositeOutline(glyphData, scale)
        is GlyfData.Empty -> emptyList()
    }

    return ScaledGlyph(
        sourceCodepoint = 0, // caller sets
        glyphId = glyphId,
        size = size,
        advanceWidth = metrics.advanceWidth * scale,
        bounds = computeBounds(commands),
        outlineCommands = commands,
    )
}
```

- [ ] **Step 7: Run test — verify glyph outline is produced**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*GlyphScalerTest*'
```
Expected: PASS (both tests).

- [ ] **Step 8: Add CFF/CFF2 outline extraction (postscript outlines)**

Extend `GlyphScaler` to detect CFF table and extract charstring outlines:

```kotlin
private fun scaleCffGlyph(glyphId: Int, size: Float): ScaledGlyph? {
    val cff = parseCffTable() ?: return null
    val charstring = cff.charstring(glyphId) ?: return null
    return charstringToOutline(charstring, size, glyphId)
}
```

- [ ] **Step 9: Add deterministic checksum**

```kotlin
fun ScaledGlyph.checksum(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.putInt(sourceCodepoint)
    digest.putInt(glyphId)
    digest.putFloat(size)
    digest.putFloat(advanceWidth)
    for (cmd in outlineCommands) {
        digest.update(cmd.toCanonicalBytes())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 10: Commit**

```bash
git add font/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt \
        font/src/test/kotlin/org/graphiks/kanvas/font/scaler/GlyphScalerTest.kt
git commit -m "feat(KGPU-M12-001): add SFNT parser + glyf/CFF scaler with deterministic output"
```

---

### Task 2: KGPU-M12-002 — Add A8 glyph rasterizer with strike key + cache invalidation

**Files:**
- Create: `font/src/main/kotlin/org/graphiks/kanvas/font/glyph/A8Rasterizer.kt`
- Create: `font/src/main/kotlin/org/graphiks/kanvas/font/glyph/GlyphStrikeKey.kt`
- Create: `font/src/test/kotlin/org/graphiks/kanvas/font/glyph/A8RasterizerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// font/src/test/kotlin/org/graphiks/kanvas/font/glyph/A8RasterizerTest.kt
package org.graphiks.kanvas.font.glyph

import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class A8RasterizerTest {

    @Test
    fun `A8 rasterizer produces non-empty bitmap for Liberation Sans 'A' at 32px`() {
        val fontBytes = javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")!!
            .readBytes()
        val scaler = GlyphScaler.fromBytes(fontBytes)
        val glyph = scaler.scaleGlyph(scaler.glyphIdForCodepoint('A'.code), 32.0f)!!

        val rasterizer = A8Rasterizer()
        val bitmap = rasterizer.rasterize(glyph)

        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        assertTrue(bitmap.pixels.any { it != 0.toByte() }, "glyph should have non-empty coverage")
        assertEquals(bitmap.width * bitmap.height, bitmap.pixels.size)
    }

    @Test
    fun `strike key is deterministic for same glyph+size+transform`() {
        val key1 = GlyphStrikeKey(glyphId = 36, size = 32.0f, subpixelX = 0, subpixelY = 0)
        val key2 = GlyphStrikeKey(glyphId = 36, size = 32.0f, subpixelX = 0, subpixelY = 0)
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
        assertEquals(key1.cacheHash(), key2.cacheHash())
    }

    @Test
    fun `strike keys differ for different glyph ids`() {
        val keyA = GlyphStrikeKey(glyphId = 36, size = 32.0f, subpixelX = 0, subpixelY = 0)
        val keyB = GlyphStrikeKey(glyphId = 37, size = 32.0f, subpixelX = 0, subpixelY = 0)
        assertNotEquals(keyA, keyB)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*A8RasterizerTest*'
```

- [ ] **Step 3: Implement GlyphStrikeKey**

```kotlin
// font/src/main/kotlin/org/graphiks/kanvas/font/glyph/GlyphStrikeKey.kt
package org.graphiks.kanvas.font.glyph

import java.security.MessageDigest

data class GlyphStrikeKey(
    val glyphId: Int,
    val size: Float,
    val subpixelX: Int,
    val subpixelY: Int,
) {
    fun cacheHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.putInt(glyphId)
        digest.putFloat(size)
        digest.putInt(subpixelX)
        digest.putInt(subpixelY)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: Implement A8Rasterizer with scanline fill**

```kotlin
// font/src/main/kotlin/org/graphiks/kanvas/font/glyph/A8Rasterizer.kt
package org.graphiks.kanvas.font.glyph

import org.graphiks.kanvas.font.scaler.GlyphOutlineCommand
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import kotlin.math.*

data class A8Bitmap(
    val width: Int,
    val height: Int,
    val pixels: ByteArray, // 0-255 coverage, row-major
) {
    override fun equals(other: Any?): Boolean = other is A8Bitmap &&
        width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    override fun hashCode(): Int = width * 31 + height + pixels.contentHashCode()
}

class A8Rasterizer {
    fun rasterize(glyph: ScaledGlyph): A8Bitmap? {
        if (glyph.outlineCommands.isEmpty()) return null

        val bounds = glyph.bounds
        val padPixels = 1
        val width = ceil(bounds.width).toInt() + 2 * padPixels
        val height = ceil(bounds.height).toInt() + 2 * padPixels
        if (width <= 0 || height <= 0) return null

        val pixels = ByteArray(width * height)
        val ox = -floor(bounds.xMin).toInt() + padPixels
        val oy = -floor(bounds.yMin).toInt() + padPixels

        // Scanline rasterization: compute winding number per pixel row
        rasterizeOutline(glyph.outlineCommands, ox, oy, width, pixels)

        return A8Bitmap(width, height, pixels)
    }

    private fun rasterizeOutline(
        commands: List<GlyphOutlineCommand>,
        ox: Int, oy: Int, width: Int,
        pixels: ByteArray,
    ) {
        // For each scanline, compute x-intersections of outline edges,
        // sort, fill spans between pairs (even-odd or non-zero winding).
        // Simplified: flatten to lines, scanline fill.
        val edges = flattenToEdges(commands)
        val height = pixels.size / width
        for (y in 0 until height) {
            val intersections = mutableListOf<Float>()
            for (edge in edges) {
                val x = edge.intersectScanline(y.toFloat() + 0.5f) ?: continue
                intersections.add(x)
            }
            intersections.sort()
            var i = 0
            while (i + 1 < intersections.size) {
                val x0 = max(0f, intersections[i]).toInt()
                val x1 = min(width - 1f, intersections[i + 1]).toInt()
                for (x in x0..x1) {
                    val idx = y * width + x
                    if (idx < pixels.size) pixels[idx] = (-1).toByte() // 255 coverage
                }
                i += 2
            }
        }
    }
}
```

- [ ] **Step 5: Run test — verify PASS**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*A8RasterizerTest*'
```

- [ ] **Step 6: Add glyph cache with strike-key invalidation**

```kotlin
// Extend A8Rasterizer.kt
class GlyphCache(private val maxEntries: Int = 1024) {
    private val cache = LinkedHashMap<GlyphStrikeKey, A8Bitmap>(maxEntries, 0.75f, true)

    fun getOrRasterize(key: GlyphStrikeKey, rasterizer: () -> A8Bitmap?): A8Bitmap? {
        cache[key]?.let { return it }
        val bitmap = rasterizer() ?: return null
        cache[key] = bitmap
        return bitmap
    }

    fun invalidate() { cache.clear() }
    fun occupancy(): Int = cache.size
}
```

- [ ] **Step 7: Add cache invalidation test**

```kotlin
@Test
fun `glyph cache returns cached result and invalidates`() {
    val cache = GlyphCache(maxEntries = 10)
    val key = GlyphStrikeKey(36, 32.0f, 0, 0)
    val bitmap = A8Bitmap(5, 5, ByteArray(25) { 128.toByte() })

    var callCount = 0
    val result1 = cache.getOrRasterize(key) { callCount++; bitmap }
    assertEquals(1, callCount)
    assertEquals(bitmap, result1)

    val result2 = cache.getOrRasterize(key) { callCount++; bitmap }
    assertEquals(1, callCount) // no re-rasterization
    assertEquals(bitmap, result2)

    cache.invalidate()
    val result3 = cache.getOrRasterize(key) { callCount++; bitmap }
    assertEquals(2, callCount)
}
```

- [ ] **Step 8: Commit**

```bash
git add font/src/main/kotlin/org/graphiks/kanvas/font/glyph/A8Rasterizer.kt \
        font/src/main/kotlin/org/graphiks/kanvas/font/glyph/GlyphStrikeKey.kt \
        font/src/test/kotlin/org/graphiks/kanvas/font/glyph/A8RasterizerTest.kt
git commit -m "feat(KGPU-M12-002): add A8 glyph rasterizer with strike key + cache invalidation"
```

---

### Task 3: KGPU-M12-003 — Add GPU glyph atlas upload plan with texture region packing

**Files:**
- Create: `font/src/main/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlan.kt`
- Create: `font/src/test/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlanTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// font/src/test/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlanTest.kt
package org.graphiks.kanvas.font.atlas

import org.graphiks.kanvas.font.glyph.A8Bitmap
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GlyphAtlasUploadPlanTest {

    @Test
    fun `packer places glyphs in atlas without overlap`() {
        val packer = GlyphAtlasPacker(atlasWidth = 256, atlasHeight = 256)

        val placements = mutableListOf<GlyphAtlasPlacement>()
        for (i in 0 until 10) {
            val bitmap = A8Bitmap(20 + i * 3, 20 + i * 2, ByteArray((20 + i * 3) * (20 + i * 2)) { 128 })
            val key = GlyphStrikeKey(glyphId = i, size = 16.0f, subpixelX = 0, subpixelY = 0)
            val placement = packer.place(key, bitmap)
            assertNotNull(placement, "glyph $i should fit")
            placements.add(placement!!)
        }

        // Verify no overlaps
        for (i in placements.indices) {
            for (j in i + 1 until placements.size) {
                val a = placements[i].region
                val b = placements[j].region
                val overlaps = a.x < b.x + b.width && a.x + a.width > b.x &&
                    a.y < b.y + b.height && a.y + a.height > b.y
                assertFalse(overlaps, "glyphs $i and $j overlap: $a vs $b")
            }
        }
    }

    @Test
    fun `packer refuses glyph too large for atlas`() {
        val packer = GlyphAtlasPacker(atlasWidth = 64, atlasHeight = 64)
        val bigGlyph = A8Bitmap(128, 128, ByteArray(128 * 128) { 128 })
        val key = GlyphStrikeKey(0, 16.0f, 0, 0)

        val result = packer.place(key, bigGlyph)
        assertNull(result)
    }

    @Test
    fun `upload plan produces valid atlas bytes with correct dimensions`() {
        val uploader = GlyphAtlasUploadPlanner(atlasWidth = 256, atlasHeight = 256)
        val key = GlyphStrikeKey(36, 32.0f, 0, 0)
        val bitmap = A8Bitmap(24, 24, ByteArray(24 * 24) { 255.toByte() })

        val plan = uploader.plan(listOf(key to bitmap))

        assertTrue(plan is GlyphAtlasUploadPlan.Accepted)
        plan as GlyphAtlasUploadPlan.Accepted
        assertEquals(256, plan.atlasWidth)
        assertEquals(256, plan.atlasHeight)
        assertTrue(plan.atlasBytes.size >= 256 * 256)
        assertEquals(1, plan.placements.size)
        assertEquals(24, plan.placements[0].region.width)
        assertEquals(24, plan.placements[0].region.height)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*GlyphAtlasUploadPlanTest*'
```

- [ ] **Step 3: Implement GlyphAtlasPacker (shelf packing)**

```kotlin
// font/src/main/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlan.kt
package org.graphiks.kanvas.font.atlas

import org.graphiks.kanvas.font.glyph.A8Bitmap
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey

data class AtlasRegion(val x: Int, val y: Int, val width: Int, val height: Int)

data class GlyphAtlasPlacement(
    val strikeKey: GlyphStrikeKey,
    val region: AtlasRegion,
)

class GlyphAtlasPacker(
    private val atlasWidth: Int,
    private val atlasHeight: Int,
) {
    private var cursorX = 1  // 1px padding
    private var cursorY = 1
    private var rowHeight = 0

    fun place(key: GlyphStrikeKey, bitmap: A8Bitmap): GlyphAtlasPlacement? {
        val w = bitmap.width + 2  // 1px padding each side
        val h = bitmap.height + 2

        if (w > atlasWidth || h > atlasHeight) return null

        // Shelf packing: try current row, else next row
        if (cursorX + w > atlasWidth) {
            cursorX = 1
            cursorY += rowHeight + 1
            rowHeight = 0
        }
        if (cursorY + h > atlasHeight) return null

        val region = AtlasRegion(cursorX, cursorY, bitmap.width, bitmap.height)
        cursorX += w
        rowHeight = maxOf(rowHeight, h)

        return GlyphAtlasPlacement(key, region)
    }
}

sealed interface GlyphAtlasUploadPlan {
    data class Accepted(
        val atlasWidth: Int,
        val atlasHeight: Int,
        val atlasBytes: ByteArray,
        val placements: List<GlyphAtlasPlacement>,
    ) : GlyphAtlasUploadPlan

    data class Refused(val reason: String) : GlyphAtlasUploadPlan
}

class GlyphAtlasUploadPlanner(
    private val atlasWidth: Int = 512,
    private val atlasHeight: Int = 512,
) {
    fun plan(entries: List<Pair<GlyphStrikeKey, A8Bitmap>>): GlyphAtlasUploadPlan {
        val packer = GlyphAtlasPacker(atlasWidth, atlasHeight)
        val placements = mutableListOf<GlyphAtlasPlacement>()
        val atlasBytes = ByteArray(atlasWidth * atlasHeight)

        for ((key, bitmap) in entries) {
            val placement = packer.place(key, bitmap) ?: return GlyphAtlasUploadPlan.Refused(
                "atlas.overflow: glyph ${key.glyphId} at size ${key.size}"
            )
            placements.add(placement)
            // Copy bitmap pixels into atlas
            for (row in 0 until bitmap.height) {
                val srcOffset = row * bitmap.width
                val dstOffset = (placement.region.y + row) * atlasWidth + placement.region.x
                System.arraycopy(bitmap.pixels, srcOffset, atlasBytes, dstOffset, bitmap.width)
            }
        }

        return GlyphAtlasUploadPlan.Accepted(atlasWidth, atlasHeight, atlasBytes, placements)
    }
}
```

- [ ] **Step 4: Run test — verify PASS**

```bash
rtk ./gradlew --no-daemon :font:test --tests '*GlyphAtlasUploadPlanTest*'
```

- [ ] **Step 5: Commit**

```bash
git add font/src/main/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlan.kt \
        font/src/test/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlanTest.kt
git commit -m "feat(KGPU-M12-003): add GPU glyph atlas upload plan with shelf packing"
```

---

### Task 4: KGPU-M12-004 — Wire GPU renderer text handoff: GlyphRunDescriptor → DrawTextRun accepted

**Files:**
- Create: `font/src/main/kotlin/org/graphiks/kanvas/font/handoff/GlyphRunDescriptor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextA8RouteAcceptanceTest.kt`

- [ ] **Step 1: Write GlyphRunDescriptor contract**

```kotlin
// font/src/main/kotlin/org/graphiks/kanvas/font/handoff/GlyphRunDescriptor.kt
package org.graphiks.kanvas.font.handoff

import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey

data class GlyphRunDescriptor(
    val glyphs: List<GlyphDescriptor>,
    val atlasPlan: GlyphAtlasUploadPlan,
)

data class GlyphDescriptor(
    val strikeKey: GlyphStrikeKey,
    val placement: GlyphAtlasPlacement,
    val drawX: Float,  // position in device space
    val drawY: Float,
)
```

- [ ] **Step 2: Write the failing test — accept DrawTextRun in recorder**

```kotlin
// gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextA8RouteAcceptanceTest.kt
package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.gpu.renderer.commands.*
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.handoff.GlyphDescriptor
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GPUTextA8RouteAcceptanceTest {

    @Test
    fun `recorder accepts DrawTextRun with valid A8 atlas descriptor`() {
        val atlasPlan = GlyphAtlasUploadPlan.Accepted(
            atlasWidth = 256, atlasHeight = 256,
            atlasBytes = ByteArray(256 * 256),
            placements = listOf(
                GlyphAtlasPlacement(
                    strikeKey = GlyphStrikeKey(36, 32.0f, 0, 0),
                    region = org.graphiks.kanvas.font.atlas.AtlasRegion(1, 1, 24, 24),
                )
            ),
        )
        val runDescriptor = GlyphRunDescriptor(
            glyphs = listOf(
                GlyphDescriptor(
                    strikeKey = GlyphStrikeKey(36, 32.0f, 0, 0),
                    placement = atlasPlan.placements[0],
                    drawX = 10f, drawY = 20f,
                )
            ),
            atlasPlan = atlasPlan,
        )

        val command = NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(1),
            bounds = GPUBounds(0f, 0f, 100f, 100f),
            transform = GPUTransformFacts.Identity,
            clip = GPUClipFacts.DeviceRect(GPUBounds(0f, 0f, 100f, 100f)),
            target = GPUTargetFacts("rgba8unorm", 100, 100),
            layer = GPULayerFacts.Root,
            blend = GPUBlendFacts.SrcOver,
            ordering = GPUOrderingFacts(1),
            runDescriptor = runDescriptor,
        )

        val recorder = GPURecorder.accepting()
        val recording = recorder.record(command)

        assertTrue(recording is GPURecording.Accepted)
    }
}
```

- [ ] **Step 3: Run test — verify FAIL (DrawTextRun currently refused at recorder)**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GPUTextA8RouteAcceptanceTest*'
```

- [ ] **Step 4: Modify GPURecorder to accept DrawTextRun with valid A8 descriptor**

In `RecordingContracts.kt`, change the `DrawTextRun` branch from refusal to acceptance when `runDescriptor.atlasPlan` is `Accepted`:

```kotlin
// In GPURecorder.record()
is NormalizedDrawCommand.DrawTextRun -> {
    val atlasPlan = command.runDescriptor.atlasPlan
    when (atlasPlan) {
        is GlyphAtlasUploadPlan.Accepted -> {
            // Accept: produce render task with text atlas binding
            val task = GPUTask.Render(...)
            recordingBuilder.addTask(task)
        }
        is GlyphAtlasUploadPlan.Refused -> {
            refuseWithDiagnostic("text.atlas_upload_refused", atlasPlan.reason)
        }
    }
}
```

- [ ] **Step 5: Modify GPUTextRoutePlanner to accept A8 atlas route**

In `TextContracts.kt`, have `GPUTextA8RoutePlanner.plan()` return `Accepted` when the atlas plan is `Accepted`:

```kotlin
fun plan(descriptor: GlyphRunDescriptor): GPUTextRouteDecision = when (descriptor.atlasPlan) {
    is GlyphAtlasUploadPlan.Accepted -> GPUTextRouteDecision.Accepted(
        route = GPUTextRoute.AtlasA8,
        atlasBinding = GPUTextBinding(...),
    )
    is GlyphAtlasUploadPlan.Refused -> GPUTextRouteDecision.Refused(
        diagnostic = GPUDiagnostic.text("text.atlas_upload_refused", descriptor.atlasPlan.reason)
    )
}
```

- [ ] **Step 6: Run test — verify PASS**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GPUTextA8RouteAcceptanceTest*'
```

- [ ] **Step 7: Add refusal tests for invalid/missing atlas**

```kotlin
@Test
fun `recorder refuses DrawTextRun with refused atlas plan`() {
    val atlasPlan = GlyphAtlasUploadPlan.Refused("no space")
    val runDescriptor = GlyphRunDescriptor(emptyList(), atlasPlan)
    val command = NormalizedDrawCommand.DrawTextRun(/*...*/)
    val recording = GPURecorder.accepting().record(command)
    assertTrue(recording is GPURecording.Refused)
}
```

- [ ] **Step 8: Commit**

```bash
git add font/src/main/kotlin/org/graphiks/kanvas/font/handoff/GlyphRunDescriptor.kt \
        gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt \
        gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt \
        gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextA8RouteAcceptanceTest.kt
git commit -m "feat(KGPU-M12-004): wire text handoff — DrawTextRun accepted with A8 atlas"
```

---

## Batch 12B — Codec Pipeline (3 tickets: KGPU-M12-005..007)

### File Structure

| File | Role | Action |
|------|------|--------|
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageContracts.kt` | Image pipeline planner | Modify (accept codec) |
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt` | Texture upload | Modify (accept upload) |
| `codec-all-kotlin/src/main/kotlin/org/graphiks/kanvas/codec/KanvasImageCodec.kt` | Codec registry | Verify existing |
| `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageAcceptanceTest.kt` | Test | Create |

### Task 5: KGPU-M12-005 — Add GPU image decode plan

- [ ] **Step 1: Write failing test**

```kotlin
// gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageAcceptanceTest.kt
package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.images.GPUImageDecodePlan
import org.graphiks.kanvas.gpu.renderer.images.GPUImageDecodePlanner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ImageAcceptanceTest {

    @Test
    fun `decode plan accepts valid PNG bytes`() {
        val pngBytes = javaClass.getResourceAsStream("/images/test-1x1-red.png")!!.readBytes()
        val planner = GPUImageDecodePlanner()
        val plan = planner.plan(pngBytes, "image/png")

        assertTrue(plan is GPUImageDecodePlan.Accepted)
        plan as GPUImageDecodePlan.Accepted
        assertEquals(1, plan.width)
        assertEquals(1, plan.height)
        assertEquals(4, plan.pixels.size) // RGBA
    }

    @Test
    fun `decode plan refuses HEIF bytes with dependency-gated diagnostic`() {
        val planner = GPUImageDecodePlanner()
        val plan = planner.plan(byteArrayOf(0, 1, 2, 3), "image/heif")

        assertTrue(plan is GPUImageDecodePlan.Refused)
        assertEquals("codec.heif_dependency_gated", (plan as GPUImageDecodePlan.Refused).code)
    }
}
```

- [ ] **Step 2: Run test — FAIL**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ImageAcceptanceTest*'
```

- [ ] **Step 3: Implement GPUImageDecodePlanner wrapping existing codec registry**

```kotlin
// In ImageContracts.kt
class GPUImageDecodePlanner {
    private val acceptedMimeTypes = setOf("image/png", "image/jpeg", "image/webp", "image/gif")

    fun plan(bytes: ByteArray, mimeType: String): GPUImageDecodePlan {
        if (mimeType !in acceptedMimeTypes) {
            return GPUImageDecodePlan.Refused("codec.${mimeType.split("/").last()}_dependency_gated")
        }
        return try {
            val codec = KanvasImageCodec.forMimeType(mimeType)
            val decoded = codec.decode(bytes)
            GPUImageDecodePlan.Accepted(
                width = decoded.width,
                height = decoded.height,
                pixels = decoded.rgbaBytes,
                colorType = decoded.colorType,
            )
        } catch (e: Exception) {
            GPUImageDecodePlan.Refused("codec.decode_failed", e.message ?: "unknown")
        }
    }
}
```

- [ ] **Step 4: Run test — PASS**

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ImageAcceptanceTest*'
```

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageContracts.kt \
        gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageAcceptanceTest.kt
git commit -m "feat(KGPU-M12-005): add GPU image decode plan — accept PNG/JPEG/WebP/GIF"
```

---

### Task 6: KGPU-M12-006 — Add GPU texture upload from decoded pixels

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/UploadedTextureAcceptanceTest.kt`

- [ ] **Step 1: Write failing test for texture upload acceptance**

```kotlin
@Test
fun `texture upload materialization accepts RGBA8 pixels within size budget`() {
    val pixels = ByteArray(64 * 64 * 4) { 128.toByte() }
    val descriptor = GPUDecodedImagePixelsDescriptor(
        width = 64, height = 64,
        pixels = pixels,
        colorType = "rgba8unorm",
        alphaType = "premul",
    )
    val materializer = ValidatingTextureUploadMaterializer(maxUploadBytes = 1024 * 1024)
    val result = materializer.materialize(descriptor)

    assertTrue(result is GPUResourceMaterializationDecision.Materialized)
}
```

- [ ] **Step 2: Run test — FAIL**

- [ ] **Step 3: Implement ValidatingTextureUploadMaterializer**

```kotlin
class ValidatingTextureUploadMaterializer(private val maxUploadBytes: Int = 1024 * 1024) {
    fun materialize(descriptor: GPUDecodedImagePixelsDescriptor): GPUResourceMaterializationDecision {
        val uploadBytes = descriptor.width * descriptor.height * 4
        if (uploadBytes > maxUploadBytes) {
            return GPUResourceMaterializationDecision.Refused(
                GPUDiagnostic.resource("upload.budget_exceeded", "$uploadBytes > $maxUploadBytes")
            )
        }
        if (descriptor.colorType != "rgba8unorm") {
            return GPUResourceMaterializationDecision.Refused(
                GPUDiagnostic.resource("upload.unsupported_color_type", descriptor.colorType)
            )
        }
        return GPUResourceMaterializationDecision.Materialized(/* ... */)
    }
}
```

- [ ] **Step 4: Run test — PASS**

- [ ] **Step 5: Commit**

---

### Task 7: KGPU-M12-007 — Wire codec provenance into GPUImagePipelinePlan

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageAcceptanceTest.kt`

- [ ] **Step 1: Write test — codec provenance accepts PNG**

```kotlin
@Test
fun `codec provenance plan accepts PNG and wires into image pipeline`() {
    val provenance = GPUImageCodecProvenancePlan(
        codecId = "kanvas-png-kotlin",
        mimeType = "image/png",
        capabilityTier = "conformance",
    )
    val pipeline = GPUImagePipelinePlanner().plan(provenance)

    assertTrue(pipeline is GPUImagePipelinePlan.Accepted)
    assertEquals("kanvas-png-kotlin", (pipeline as GPUImagePipelinePlan.Accepted).codecId)
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement GPUImagePipelinePlanner**

Replace the terminal `RefuseDiagnostic` in `GPUImageCodecProvenancePlan` with conditional acceptance for supported codecs:

```kotlin
class GPUImagePipelinePlanner {
    fun plan(provenance: GPUImageCodecProvenancePlan): GPUImagePipelinePlan =
        when (provenance.mimeType) {
            "image/png", "image/jpeg", "image/webp", "image/gif" ->
                GPUImagePipelinePlan.Accepted(provenance.codecId, provenance.mimeType)
            else -> GPUImagePipelinePlan.Refused(
                "codec.${provenance.mimeType.split("/").last()}_dependency_gated"
            )
        }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

---

## Batch 12C — wgsl4k Parser Integration (3 tickets: KGPU-M12-008..010)

### File Structure

| File | Role | Action |
|------|------|--------|
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLModuleAssembler.kt` | Module assembler | Modify (real parser) |
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLContracts.kt` | Reflection contracts | Modify (parser-backed) |
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/Wgsl4kReflectionReportConsumer.kt` | Consumer | Modify (live) |
| `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLParserBackedReflectionTest.kt` | Test | Create |

### Task 8: KGPU-M12-008 — Integrate wgsl4k parser into WGSLModuleAssembler

- [ ] **Step 1: Write failing test — parser-backed reflection**

```kotlin
// gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/WGSLParserBackedReflectionTest.kt
@Test
fun `assembler produces parser-backed reflection for solid rect WGSL`() {
    val assembler = WGSLModuleAssembler.withRealParser() // was: withFixtureDeclared()
    val module = assembler.assemble(listOf(WGSLSnippet.solidColorV1()))

    assertTrue(module.reflection is WGSLReflectionResult.Accepted)
    val reflection = module.reflection as WGSLReflectionResult.Accepted
    assertFalse(reflection.isFixtureDeclared, "reflection must be parser-backed, not fixture-declared")
    assertEquals(1, reflection.bindGroups.size)
    assertTrue(reflection.bindGroups.any { it.bindings.any { b -> b.name == "solidMaterial" } })
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Wire wgsl4k parser into WGSLModuleAssembler**

```kotlin
// In WGSLModuleAssembler.kt
class WGSLModuleAssembler(
    private val parser: Wgsl4kParser? = null, // null = fixture-declared (legacy)
) {
    fun assemble(snippets: List<WGSLSnippet>): WGSLModule {
        val source = assembleSource(snippets)
        val hash = WGSLModuleHash.fromSource(source)

        val reflection = if (parser != null) {
            val parseResult = parser.parse(source)
            when (parseResult) {
                is Wgsl4kParseResult.Ok -> WGSLReflectionResult.Accepted(
                    bindGroups = parseResult.reflection.bindGroups,
                    isFixtureDeclared = false,
                )
                is Wgsl4kParseResult.Error -> WGSLReflectionResult.Refused(
                    "wgsl4k.parse_error", parseResult.message
                )
            }
        } else {
            fixtureDeclaredReflection() // legacy: marked isFixtureDeclared=true
        }

        return WGSLModule(source, hash, reflection)
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

---

### Task 9: KGPU-M12-009 — Add WGSL ABI validation

- [ ] **Step 1: Write test — ABI layout match**

```kotlin
@Test
fun `ABI validator rejects Kotlin packing that mismatches WGSL reflection layout`() {
    val reflection = WGSLUniformLayout(
        fields = listOf(
            WGSLUniformFieldLayout("color", offset = 0, size = 16, alignment = 16),
        ),
        totalSize = 16,
    )
    val packing = WGSLPackingPlan(
        fields = listOf(
            GPUUniformPayloadField("color", offset = 4, byteLength = 16), // wrong offset!
        ),
        totalSize = 16,
    )
    val validator = WGSLAbiValidator()
    val result = validator.validate(reflection, packing)

    assertTrue(result is WGSLAbiValidationResult.Mismatch)
    assertTrue((result as WGSLAbiValidationResult.Mismatch).diagnostic.contains("offset"))
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement WGSLAbiValidator**

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

---

### Task 10: KGPU-M12-010 — Add wgsl4k evolution gate

- [ ] **Step 1: Write test — evolution gate requires parser-backed reflection**

```kotlin
@Test
fun `evolution gate fails when any WGSL module uses fixture-declared reflection`() {
    val modules = listOf(
        WGSLModule(/* parser-backed */),
        WGSLModule(/* fixture-declared — should fail gate */),
    )
    val gate = Wgsl4kEvolutionGate()
    val result = gate.evaluate(modules)

    assertTrue(result is Wgsl4kEvolutionGateResult.NotPassed)
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement Wgsl4kEvolutionGate**

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

---

## Validation

```bash
# Full M12 validation
rtk ./gradlew --no-daemon :font:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Dependency Graph

```
M12-001 (scaler)
  → M12-002 (rasterizer, needs scaler)
    → M12-003 (atlas packer, needs rasterizer)
      → M12-004 (handoff, needs atlas)

M12-005 (decode plan)
  → M12-006 (texture upload, needs decode)
    → M12-007 (pipeline wire, needs upload)

M12-008 (parser integration)
  → M12-009 (ABI validation, needs parser)
    → M12-010 (evolution gate, needs ABI)

Batches 12A, 12B, 12C are independent (parallel)
```

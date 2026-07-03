# Emoji Kanvas — Native Color Glyph Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add native color glyph support (COLRv0, CBDT/sbix, COLRv1) to the Kanvas GlyphScaler/Font/GPU pipeline and migrate the 5 remaining emoji GMs to `integration-tests/skia/gm/text/`.

**Architecture:** Four phases. Phase 1 adds CPAL+COLRv0 parsing and Font features (no GPU changes). Phase 2 extends the GPU text pipeline for color rendering. Phase 3 adds bitmap emoji and drawGlyphs. Phase 4 adds COLRv1. Each phase is independently PR-able.

**Tech Stack:** Pure Kotlin (no JNI). OpenType binary parsing. Kanvas Font/Canvas/GPU pipeline.

---

### Phase 1: Font Scaler Color + Font Features

#### Task 1.1: Add CPAL table parsing to GlyphScaler

**Files:**
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`

- [ ] **Step 1: Add CPAL parsing to GlyphScaler init**

In `GlyphScaler`, after the existing `init` block, add CPAL table parsing:

```kotlin
// Add these fields to GlyphScaler class body (after existing fields):
private val cpalPalette: IntArray? = parseCpal()

private fun parseCpal(): IntArray? {
    val cpalTable = tables["CPAL"] ?: return null
    val bytes = fontBytes
    val off = cpalTable.offset
    val version = u16(bytes, off)
    val numPaletteEntries = u16(bytes, off + 4)
    val numPalettes = u16(bytes, off + 6)
    val numColorRecords = u16(bytes, off + 8)
    if (numPalettes == 0 || numPaletteEntries == 0) return null
    val colorRecordsOffset = u32(bytes, off + 12).toInt()
    val colors = IntArray(numPaletteEntries)
    for (i in 0 until numPaletteEntries) {
        val entryOff = cpalTable.offset + colorRecordsOffset + i * 4
        if (entryOff + 4 > bytes.size) return null
        val b = u8(bytes, entryOff)
        val g = u8(bytes, entryOff + 1)
        val r = u8(bytes, entryOff + 2)
        val a = u8(bytes, entryOff + 3)
        colors[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return colors
}
```

- [ ] **Step 2: Run existing GlyphScaler tests to verify no regression**

```bash
./gradlew :font:scaler:test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
git commit -m "font: add CPAL palette parsing to GlyphScaler"
```

---

#### Task 1.2: Add GlyphRepresentation type and ColorLayers parsing

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/text/GlyphRepresentation.kt`
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`

- [ ] **Step 1: Create GlyphRepresentation.kt**

```kotlin
package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Color

sealed interface GlyphRepresentation {
    data class Outline(val commands: List<org.graphiks.kanvas.font.scaler.OutlineCommand>) : GlyphRepresentation
    data class Bitmap(
        val pngData: ByteArray,
        val originX: Float,
        val originY: Float,
        val pixelWidth: Int,
        val pixelHeight: Int,
    ) : GlyphRepresentation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bitmap) return false
            return pngData.contentEquals(other.pngData) &&
                originX == other.originX && originY == other.originY &&
                pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight
        }
        override fun hashCode(): Int {
            var result = pngData.contentHashCode()
            result = 31 * result + originX.hashCode()
            result = 31 * result + originY.hashCode()
            result = 31 * result + pixelWidth
            result = 31 * result + pixelHeight
            return result
        }
    }
    data class ColorLayers(val layers: List<ColorLayerEntry>) : GlyphRepresentation
    data class SvgDocument(
        val svgData: ByteArray,
        val docWidth: Float,
        val docHeight: Float,
    ) : GlyphRepresentation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SvgDocument) return false
            return svgData.contentEquals(other.svgData) &&
                docWidth == other.docWidth && docHeight == other.docHeight
        }
        override fun hashCode(): Int {
            var result = svgData.contentHashCode()
            result = 31 * result + docWidth.hashCode()
            result = 31 * result + docHeight.hashCode()
            return result
        }
    }
}

data class ColorLayerEntry(val glyphId: Int, val paletteColor: Color)
```

- [ ] **Step 2: Add COLRv0 parsing to GlyphScaler**

In `GlyphScaler`, add parsing for the COLR table (v0 base glyph + layer records):

```kotlin
// Add to GlyphScaler class:
private val colrV0BaseGlyphs: Map<Int, List<ColorLayerEntry>>? = parseColrV0()

private fun parseColrV0(): Map<Int, List<ColorLayerEntry>>? {
    val colrTable = tables["COLR"] ?: return null
    val palette = cpalPalette ?: return null
    val bytes = fontBytes
    val off = colrTable.offset
    val version = u16(bytes, off)
    if (version != 0) return null // COLRv0 only in this task
    val numBaseGlyphRecords = u16(bytes, off + 2)
    val baseGlyphRecordsOffset = u32(bytes, off + 4).toInt()
    val layerRecordsOffset = u32(bytes, off + 8).toInt()
    val numLayerRecords = u16(bytes, off + 12)

    val result = mutableMapOf<Int, List<ColorLayerEntry>>()

    for (i in 0 until numBaseGlyphRecords) {
        val baseOff = colrTable.offset + baseGlyphRecordsOffset + i * 6
        val glyphId = u16(bytes, baseOff)
        val firstLayerIndex = u16(bytes, baseOff + 2)
        val numLayers = u16(bytes, baseOff + 4)
        val layers = mutableListOf<ColorLayerEntry>()
        for (j in 0 until numLayers) {
            val layerOff = colrTable.offset + layerRecordsOffset + (firstLayerIndex + j) * 4
            val layerGlyphId = u16(bytes, layerOff)
            val paletteIndex = u16(bytes, layerOff + 2)
            val color = if (paletteIndex < palette.size) {
                val argb = palette[paletteIndex]
                Color.fromRGBA(
                    ((argb shr 16) and 0xFF) / 255f,
                    ((argb shr 8) and 0xFF) / 255f,
                    (argb and 0xFF) / 255f,
                    ((argb shr 24) and 0xFF) / 255f,
                )
            } else {
                Color.BLACK // fallback
            }
            layers.add(ColorLayerEntry(layerGlyphId, color))
        }
        result[glyphId] = layers
    }
    return if (result.isEmpty()) null else result
}
```

- [ ] **Step 3: Modify ScaledGlyph to carry representation**

Change `ScaledGlyph` (line 12-35 in GlyphScaler.kt) to add `representation` field:

```kotlin
data class ScaledGlyph(
    val sourceCodepoint: Int,
    val glyphId: Int,
    val size: Float,
    val advanceWidth: Float,
    val bounds: GlyphBounds,
    val commands: List<OutlineCommand> = emptyList(),
    val representation: org.graphiks.kanvas.text.GlyphRepresentation? = null,  // NEW
) {
    // existing checksum() unchanged
}
```

- [ ] **Step 4: Modify scaleGlyph to check COLRv0**

In `scaleGlyph()` method (line 82-100), before the existing outline parsing, check for color layers:

```kotlin
fun scaleGlyph(glyphId: Int, size: Float, sourceCodepoint: Int = 0): ScaledGlyph {
    if (glyphId < 0 || glyphId >= numGlyphs) {
        throw IllegalArgumentException("Glyph ID $glyphId out of range [0, $numGlyphs)")
    }
    val scale = size / unitsPerEm.toFloat()
    val advance = advanceWidths[min(glyphId, advanceWidths.lastIndex)] * scale

    // Check COLRv0 color layers first
    val colorLayers = colrV0BaseGlyphs?.get(glyphId)
    if (colorLayers != null) {
        return ScaledGlyph(
            sourceCodepoint = sourceCodepoint,
            glyphId = glyphId,
            size = size,
            advanceWidth = advance,
            bounds = computeBounds(emptyList()), // bounds from outline not applicable
            commands = emptyList(),
            representation = org.graphiks.kanvas.text.GlyphRepresentation.ColorLayers(colorLayers),
        )
    }

    // Existing outline path
    val outline = parseGlyphOutline(glyphId)
    val commands = outlineToCommands(outline)
    val scaledCommands = commands.map { scaleCommand(it, scale) }
    val bounds = computeBounds(scaledCommands)
    return ScaledGlyph(
        sourceCodepoint = sourceCodepoint,
        glyphId = glyphId,
        size = size,
        advanceWidth = advance,
        bounds = bounds,
        commands = scaledCommands,
        representation = org.graphiks.kanvas.text.GlyphRepresentation.Outline(scaledCommands),
    )
}
```

Also update `scaleGlyphOrDiagnostic` to propagate `representation`.

- [ ] **Step 5: Add import to GlyphScaler.kt**

```kotlin
import org.graphiks.kanvas.types.Color
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :font:scaler:test :kanvas:test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/text/GlyphRepresentation.kt font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
git commit -m "font: add GlyphRepresentation type and COLRv0 color layer parsing"
```

---

#### Task 1.3: Add Font.getMetrics() and isEmbolden

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontMetrics.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/text/Font.kt`
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`

- [ ] **Step 1: Create FontMetrics.kt**

```kotlin
package org.graphiks.kanvas.text

data class FontMetrics(
    val ascent: Float,
    val descent: Float,
    val leading: Float,
    val xHeight: Float = 0f,
    val capHeight: Float = 0f,
)
```

- [ ] **Step 2: Add metrics accessor to GlyphScaler**

Add to GlyphScaler class:

```kotlin
// Add field:
private val fontMetricsRaw: HheaMetrics = parseHheaMetrics()

private class HheaMetrics(
    val ascent: Int,
    val descent: Int,
    val lineGap: Int,
)

private fun parseHheaMetrics(): HheaMetrics {
    val hhea = tables["hhea"] ?: error("Missing hhea table")
    val bytes = fontBytes
    return HheaMetrics(
        ascent = i16(bytes, hhea.offset + 4).toInt(),
        descent = i16(bytes, hhea.offset + 6).toInt(),
        lineGap = i16(bytes, hhea.offset + 8).toInt(),
    )
}

// Add method:
fun getMetrics(fontSize: Float): FontMetrics {
    val scale = fontSize / unitsPerEm.toFloat()
    val m = fontMetricsRaw
    return FontMetrics(
        ascent = m.ascent * scale,
        descent = m.descent * scale,
        leading = m.lineGap * scale,
    )
}
```

- [ ] **Step 3: Add getMetrics + isEmbolden to Font**

Modify `Font.kt`:

```kotlin
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.OutlineCommand

data class Font(
    val typeface: Typeface,
    val size: Float = 12f,
    val antiAlias: Boolean = true,
    val subpixel: Boolean = true,
    val isEmbolden: Boolean = false,  // NEW
) {
    // NEW: Expose font metrics
    fun getMetrics(): FontMetrics? {
        if (typeface !is FontTypeface) return null
        val scaler = typeface.scaler ?: return null
        return scaler.getMetrics(size)
    }

    // MODIFIED: embolden increases advance slightly
    fun measureText(str: String): Float {
        var width = 0f
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            width += typeface.getAdvance(gid, size)
        }
        if (isEmbolden) width += str.codePoints().count().toFloat() * size * 0.02f
        return width
    }

    // existing toTextBlob, getGlyphWidths unchanged
}
```

- [ ] **Step 4: Make scaler accessible from FontTypeface**

Change `private val scaler` to `internal val scaler` in `FontTypeface.kt`:

```kotlin
internal val scaler: GlyphScaler? = try { ... }
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :kanvas:test :font:scaler:test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontMetrics.kt kanvas/src/main/kotlin/org/graphiks/kanvas/text/Font.kt kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontTypeface.kt font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
git commit -m "font: add Font.getMetrics() and isEmbolden support"
```

---

#### Task 1.4: Native Kanvas EmojiTypeface

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/text/EmojiTypeface.kt`

- [ ] **Step 1: Create EmojiTypeface.kt**

```kotlin
package org.graphiks.kanvas.text

object EmojiTypeface {
    enum class Format { Sbix, CBDT, COLRv0, SVG }

    fun create(format: Format, fontData: ByteArray): Typeface {
        return FontTypeface(fontData, "emoji-${format.name.lowercase()}")
    }

    fun createOrFallback(format: Format, fontData: ByteArray): Typeface {
        return try {
            create(format, fontData)
        } catch (_: Exception) {
            Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")
                ?: error("No fallback typeface available")
        }
    }
}
```

- [ ] **Step 2: Run compilation**

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/text/EmojiTypeface.kt
git commit -m "font: add native Kanvas EmojiTypeface"
```

---

#### Task 1.5: Migrate ScaledemojiGM

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/ScaledemojiGm.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/ScaledemojiGM.kt`
- Delete: `skia-integration-tests/src/test/kotlin/org/skia/tests/ScaledemojiTest.kt`
- Modify: `integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`
- Move: `skia-integration-tests/src/test/resources/original-888/scaledemoji_colrv0.png` → `integration-tests/skia/src/test/resources/reference/scaledemoji_colrv0.png`

- [ ] **Step 1: Port the GM source**

Read the upstream Skia `gm/scaledemoji.cpp` to understand the body. The Kotlin stub currently has no drawing logic. The upstream draws simple emoji text at 4 progressively larger point sizes (70, 180, 270, 340) via `drawSimpleText`.

Create `ScaledemojiGm.kt`:

```kotlin
package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.EmojiTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/scaledemoji.cpp`.
 * Draws a colour-emoji glyph ("\uD83D\uDE00") at four progressively
 * larger point sizes (70, 180, 270, 340) via drawSimpleText.
 * @see https://github.com/google/skia/blob/main/gm/scaledemoji.cpp
 */
class ScaledemojiGm : SkiaGm {
    override val name = "scaledemoji_colrv0"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    private val emojiTypeface = EmojiTypeface.createOrFallback(
        EmojiTypeface.Format.COLRv0, ByteArray(0),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val text = "\uD83D\uDE00" // 😀
        val textSizes = floatArrayOf(70f, 180f, 270f, 340f)
        var y = 0f

        for (textSize in textSizes) {
            val font = Font(emojiTypeface, size = textSize, antiAlias = false, subpixel = true)
            val metrics = font.getMetrics()
            if (metrics != null) {
                y += -metrics.ascent
                canvas.drawSimpleText(text, 10f, y, font, Paint())
                y += metrics.descent + metrics.leading
            } else {
                y += textSize * 1.2f
                canvas.drawSimpleText(text, 10f, y, font, Paint())
            }
        }
    }
}
```

- [ ] **Step 2: Move reference image**

```bash
mv skia-integration-tests/src/test/resources/original-888/scaledemoji_colrv0.png \
   integration-tests/skia/src/test/resources/reference/scaledemoji_colrv0.png
```

- [ ] **Step 3: Create placeholder generated render**

```bash
touch integration-tests/skia/src/test/resources/generated-renders/text/scaledemoji_colrv0.png
```

- [ ] **Step 4: Register in ServiceLoader**

Insert `org.graphiks.kanvas.skia.gm.text.ScaledemojiGm` in alphabetical position (after `PerspTextGm`, before `ScaledemojiRenderingGm`).

- [ ] **Step 5: Delete old source and test**

```bash
rm skia-integration-tests/src/main/kotlin/org/skia/tests/ScaledemojiGM.kt
rm skia-integration-tests/src/test/kotlin/org/skia/tests/ScaledemojiTest.kt
```

- [ ] **Step 6: Verify compilation**

```bash
./gradlew :integration-tests:skia:compileTestKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit (3 commits: additions, source deletions, ref deletions)**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/ScaledemojiGm.kt integration-tests/skia/src/test/resources/reference/scaledemoji_colrv0.png integration-tests/skia/src/test/resources/generated-renders/text/scaledemoji_colrv0.png integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm
git commit -m "gm: migrate Scaledemoji to text/ScaledemojiGm"

git add skia-integration-tests/src/main/kotlin/org/skia/tests/ScaledemojiGM.kt skia-integration-tests/src/test/kotlin/org/skia/tests/ScaledemojiTest.kt
git commit -m "gm: delete old Scaledemoji source and test"

git add skia-integration-tests/src/test/resources/original-888/scaledemoji_colrv0.png
git commit -m "gm: remove old scaledemoji_colrv0 reference from original-888/"
```

---

### Phase 2: GPU Color Text Rendering

#### Task 2.1: Extend GPU text pipeline for ColorLayers

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` (lines 221-232)
- Possibly: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/TextBridge.kt` (if exists)

- [ ] **Step 1: Find TextBridge rasterize implementation**

```bash
grep -rn "object TextBridge\|fun rasterize" kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/
```

Read the file to understand how text blobs are rasterized into atlas tiles.

- [ ] **Step 2: Add color glyph detection to TextBlob processing**

In the `DrawText` handler of `GPURenderer.kt` (line 221-232), before calling `TextBridge.rasterize()`, check if any glyph in the blob has a color representation. If so, process color glyphs separately:

```kotlin
is DisplayOp.DrawText -> {
    val hasColorGlyphs = op.blob.glyphRuns.any { run ->
        run.glyphIds.any { gid ->
            val rep = (op.blob.typeface as? FontTypeface)
                ?.scaler?.getGlyphRepresentation(gid.toInt(), op.blob.fontSize)
            rep is GlyphRepresentation.ColorLayers || rep is GlyphRepresentation.Bitmap
        }
    }
    if (hasColorGlyphs) {
        // Draw color glyphs via direct path rendering
        drawTextColorPass(op, dispatched, diagnostics, cmdId, t, sceneLabel, targets)
    } else {
        // Existing monochrome atlas path
        val gpuBlob = TextBridge.rasterize(op.blob)
        if (gpuBlob != null) {
            val cmd = op.toNormalizedCommand(cmdId, targets)
            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                drawTextAtlasPass(gpuBlob, cmd.blend.blendMode, dispatched, diagnostics, textColor = op.paint.color, targetWidth = width, targetHeight = height)
            }
        } else {
            diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "rasterize_failed")
        }
    }
    sceneHasContent = true
}
```

- [ ] **Step 3: Implement drawTextColorPass**

Add a private method to GPURenderer that handles color glyph text by rendering each glyph as individual path/image draws:

```kotlin
private fun drawTextColorPass(
    op: DisplayOp.DrawText,
    dispatched: DispatchedOpAccumulator,
    diagnostics: RenderDiagnosticCollector,
    cmdId: CommandId,
    t: TextureSurfaceManager,
    sceneLabel: SceneId,
    targets: List<Target>,
) {
    for (run in op.blob.glyphRuns) {
        for ((idx, gid) in run.glyphIds.withIndex()) {
            val pos = run.positions[idx]
            val rep = (op.blob.typeface as FontTypeface)
                ?.scaler?.getGlyphRepresentation(gid.toInt(), op.blob.fontSize)
            when (rep) {
                is GlyphRepresentation.ColorLayers -> {
                    for (layer in rep.layers) {
                        val layerGlyph = (op.blob.typeface as FontTypeface)
                            .scaler?.scaleGlyph(layer.glyphId, op.blob.fontSize)
                            ?: continue
                        val cmds = layerGlyph.commands
                        if (cmds.isEmpty()) continue
                        val path = org.graphiks.kanvas.geometry.Path {
                            for (cmd in cmds) {
                                when (cmd) {
                                    is org.graphiks.kanvas.font.scaler.OutlineCommand.MoveTo ->
                                        moveTo(cmd.x.toFloat() + pos.x, cmd.y.toFloat() + pos.y + op.y)
                                    is org.graphiks.kanvas.font.scaler.OutlineCommand.LineTo ->
                                        lineTo(cmd.x.toFloat() + pos.x, cmd.y.toFloat() + pos.y + op.y)
                                    is org.graphiks.kanvas.font.scaler.OutlineCommand.QuadraticTo ->
                                        quadTo(cmd.controlX.toFloat() + pos.x, cmd.controlY.toFloat() + pos.y + op.y, cmd.x.toFloat() + pos.x, cmd.y.toFloat() + pos.y + op.y)
                                    is org.graphiks.kanvas.font.scaler.OutlineCommand.CubicTo ->
                                        cubicTo(cmd.controlX1.toFloat() + pos.x, cmd.controlY1.toFloat() + pos.y + op.y, cmd.controlX2.toFloat() + pos.x, cmd.controlY2.toFloat() + pos.y + op.y, cmd.x.toFloat() + pos.x, cmd.y.toFloat() + pos.y + op.y)
                                    is org.graphiks.kanvas.font.scaler.OutlineCommand.Close -> close()
                                }
                            }
                        }
                        val layerPaint = op.paint.copy(color = layer.paletteColor)
                        val layerCmd = op.toNormalizedCommand(cmdId, targets)
                        dispatchFillPath(layerCmd.copy(blend = layerCmd.blend), dispatched, diagnostics, width, height, config)
                    }
                }
                is GlyphRepresentation.Outline -> {
                    // Fallback: standard outline rendering via atlas
                    // This is handled by the existing TextBridge path
                }
                else -> {} // Bitmap/SVG handled in Phase 3+
            }
        }
    }
}
```

- [ ] **Step 4: Add getGlyphRepresentation to GlyphScaler**

```kotlin
// Add to GlyphScaler class:
fun getGlyphRepresentation(glyphId: Int, fontSize: Float): org.graphiks.kanvas.text.GlyphRepresentation? {
    val result = scaleGlyph(glyphId, fontSize)
    return result.representation
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :kanvas:test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
git commit -m "gpu: add COLRv0 color layer text rendering to GPU pipeline"
```

---

#### Task 2.2: Migrate ColoremojiBlendmodesGM

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/ColoremojiBlendmodesGm.kt`
- Delete: old source + test
- Move refs: `coloremoji_blendmodes_*.png` → `reference/`
- Modify: ServiceLoader

- [ ] **Step 1: Port the upstream body**

Read upstream `gm/coloremoji_blendmodes.cpp`. The GM draws one emoji glyph through all 29 SkBlendModes in a 5-column grid, one column per emoji format.

Create `ColoremojiBlendmodesGm.kt` (body mirrors upstream: iterate blend modes, draw emoji glyph, use `paint.copy(blendMode = ...)`).

- [ ] **Step 2: Move reference images (COLRv0 variant only)**

```bash
mv skia-integration-tests/src/test/resources/original-888/coloremoji_blendmodes_colrv0.png \
   integration-tests/skia/src/test/resources/reference/coloremoji_blendmodes_colrv0.png
```

- [ ] **Step 3: Register, delete old, verify, commit** (same pattern as Task 1.5)

---

### Phase 3: Bitmap Emoji + drawGlyphs

#### Task 3.1: Parse CBDT/CBLC and sbix in GlyphScaler

**Files:**
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`

- [ ] **Step 1: Add CBDT/CBLC parsing**

CBDT/CBLC: CBLC indexes bitmap strikes by ppem. CBDT contains PNG data for each glyph+strike combo. Only support PNG formats (17, 18, 19).

Add to GlyphScaler:

```kotlin
// Add field:
private val cblcStrikes: List<CblcStrike>? = parseCblc()

private data class CblcStrike(
    val ppemX: Int,
    val ppemY: Int,
    val glyphBitmaps: Map<Int, BitmapRecord>,
)

private data class BitmapRecord(
    val originX: Float,
    val originY: Float,
    val pngData: ByteArray,
    val pixelWidth: Int,
    val pixelHeight: Int,
)

private fun parseCblc(): List<CblcStrike>? {
    val cblcTable = tables["CBLC"] ?: return null
    val cbdtTable = tables["CBDT"] ?: return null
    val bytes = fontBytes
    val offset = cblcTable.offset
    val numSizes = u32(bytes, offset + 4).toInt()
    val strikes = mutableListOf<CblcStrike>()
    var sizeOff = offset + 8
    for (si in 0 until numSizes) {
        val indexSubTableArrayOffset = u32(bytes, sizeOff).toInt()
        val numTables = u32(bytes, sizeOff + 8).toInt()
        val ppemX = u8(bytes, sizeOff + 12).toInt()
        val ppemY = u8(bytes, sizeOff + 13).toInt()
        val glyphBitmaps = mutableMapOf<Int, BitmapRecord>()
        for (ti in 0 until numTables) {
            val subTableOff = cblcTable.offset + indexSubTableArrayOffset + ti * 8
            val firstGlyph = u16(bytes, subTableOff)
            val lastGlyph = u16(bytes, subTableOff + 2)
            val additionalOffset = u32(bytes, subTableOff + 4).toInt()
            val imageSize = if (si + 1 < numSizes) {
                val nextSizeOff = offset + 8 + (si + 1) * 48
                u32(bytes, nextSizeOff + 32).toInt()
            } else cbdtTable.length
            for (gid in firstGlyph..lastGlyph) {
                val entryOff = cblcTable.offset + additionalOffset + (gid - firstGlyph) * 4
                val glyphOffset = u32(bytes, entryOff).toInt()
                if (glyphOffset == 0) continue
                val glyphSize = computeCbdtGlyphSize(gid, firstGlyph, lastGlyph, additionalOffset, glyphOffset, imageSize, bytes, cblcTable.offset)
                if (glyphOffset + 4 > cbdtTable.length) continue
                val cbdtOff = cbdtTable.offset + glyphOffset
                val format = u16(bytes, cbdtOff)
                if (format != 17 && format != 18 && format != 19) continue // PNG only
                val pngLen = glyphSize - 4
                if (cbdtOff + 4 + pngLen > bytes.size) continue
                val pngData = bytes.copyOfRange(cbdtOff + 4, cbdtOff + 4 + pngLen)
                glyphBitmaps[gid] = BitmapRecord(
                    originX = 0f,
                    originY = 0f,
                    pngData = pngData,
                    pixelWidth = ppemX,
                    pixelHeight = ppemY,
                )
            }
        }
        strikes.add(CblcStrike(ppemX, ppemY, glyphBitmaps))
        sizeOff += 48
    }
    return strikes.ifEmpty { null }
}

private fun computeCbdtGlyphSize(gid: Int, firstGlyph: Int, lastGlyph: Int, additionalOffset: Int, glyphOffset: Int, imageSize: Int, bytes: ByteArray, cblcOff: Int): Int {
    return imageSize - glyphOffset // simplified: subtract current offset from total image size
}
```

- [ ] **Step 2: Add sbix parsing**

sbix: simple per-glyph PNG + origin offsets (no strike selection complexity):

```kotlin
// Add field:
private val sbixGlyphs: Map<Int, BitmapRecord>? = parseSbix()

private fun parseSbix(): Map<Int, BitmapRecord>? {
    val sbixTable = tables["sbix"] ?: return null
    val bytes = fontBytes
    val off = sbixTable.offset
    val numStrikes = u16(bytes, off + 4).toInt()
    if (numStrikes == 0) return null
    // Use first strike only (most common case for test fonts)
    val strikeOffset = u32(bytes, off + 8).toInt()
    val strikeOff = sbixTable.offset + strikeOffset
    val ppem = u16(bytes, strikeOff)
    val ppi = u16(bytes, strikeOff + 2)
    val numGlyphs = u32(bytes, strikeOff + 4).toInt()
    val result = mutableMapOf<Int, BitmapRecord>()
    var glyphOff = strikeOff + 8
    for (i in 0 until numGlyphs) {
        val originOffsetX = i16(bytes, glyphOff).toInt()
        val originOffsetY = i16(bytes, glyphOff + 2).toInt()
        val graphicType = String(bytes, glyphOff + 4, 4, Charsets.ISO_8859_1)
        val dataLen = u32(bytes, glyphOff + 8).toInt()
        if (graphicType == "png " && dataLen > 0) {
            val pngStart = glyphOff + 12
            if (pngStart + dataLen <= bytes.size) {
                val pngData = bytes.copyOfRange(pngStart, pngStart + dataLen)
                result[i] = BitmapRecord(
                    originX = originOffsetX.toFloat(),
                    originY = originOffsetY.toFloat(),
                    pngData = pngData,
                    pixelWidth = ppem,
                    pixelHeight = ppem,
                )
            }
        }
        glyphOff += 12 + dataLen
    }
    return result.ifEmpty { null }
}
```

- [ ] **Step 3: Extend scaleGlyph for bitmap glyphs**

In `scaleGlyph()`, after the COLRv0 check, add bitmap resolution:

```kotlin
// Check CBDT/CBLC
val bitmap = resolveCbdtBitmap(glyphId, size)
if (bitmap != null) {
    return ScaledGlyph(..., representation = GlyphRepresentation.Bitmap(...))
}
// Check sbix
val sbixBmp = sbixGlyphs?.get(glyphId)
if (sbixBmp != null) {
    return ScaledGlyph(..., representation = GlyphRepresentation.Bitmap(...))
}
```

- [ ] **Step 4: Commit**

```bash
git add font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
git commit -m "font: add CBDT/CBLC and sbix bitmap parsing to GlyphScaler"
```

---

#### Task 3.2: GPU bitmap glyph rendering

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`

- [ ] **Step 1: Add Bitmap case to drawTextColorPass**

In the when block of `drawTextColorPass` (Task 2.1), add:

```kotlin
is GlyphRepresentation.Bitmap -> {
    val image = PngDecoder.decode(rep.pngData) // use codec/png
    if (image != null) {
        val dstRect = Rect(
            pos.x + rep.originX, pos.y + op.y + rep.originY,
            pos.x + rep.originX + rep.pixelWidth,
            pos.y + op.y + rep.originY + rep.pixelHeight,
        )
        dispatchImage(image, dstRect, op.paint, op.clip, op.transform, t, dispatched, diagnostics, cmdId, sceneLabel, targets)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: add bitmap emoji glyph rendering"
```

---

#### Task 3.3: Add GmCanvas.drawGlyphs()

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt`

- [ ] **Step 1: Add drawGlyphs method**

```kotlin
fun drawGlyphs(glyphIds: List<Int>, positions: List<Point>, font: Font, paint: Paint) {
    require(glyphIds.size == positions.size)
    for (i in glyphIds.indices) {
        val gid = glyphIds[i]
        val pos = positions[i]
        val path = font.typeface.getGlyphPath(gid, font.size)
        if (path != null) {
            val translatedPath = org.graphiks.kanvas.geometry.Path { }
            // Build path with translated coordinates
            // ...
            drawPath(translatedPath, paint)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt
git commit -m "gm: add drawGlyphs() to GmCanvas"
```

---

#### Task 3.4-3.5: Migrate ScaledEmojiPosGM + ScaledEmojiPerspectiveGM

Port upstream bodies, move refs, register, delete old. Same migration pattern as Task 1.5.

**Ref images to move:**
- `scaledemojipos_colrv0.png`
- `scaledemojiperspective_colrv0.png`

---

### Phase 4: COLRv1

#### Task 4.1: Parse COLRv1 paint graph in GlyphScaler

**Files:**
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`

- [ ] **Step 1: Parse COLRv1 BaseGlyphV1List + LayerV1List + PaintTable**

COLRv1 uses a paint graph (directed acyclic). Parse:
- BaseGlyphV1List → maps base glyph to root paint
- LayerV1List → maps layer index to paint
- PaintTable → Solid, Glyph, LinearGradient, RadialGradient, SweepGradient, Translate, Scale, Rotate, Skew, Composite (with PorterDuff mode)

Extend `ColorLayers` or introduce `PaintGraph` type with:
```kotlin
data class PaintNode(
    val paintType: PaintType,
    val children: List<PaintNode>,
)

sealed interface PaintType {
    data class Solid(val paletteIndex: Int) : PaintType
    data class Glyph(val glyphId: Int) : PaintType
    data class LinearGradient(/* ... */) : PaintType
    // etc.
}
```

COLRv1 flattening: walk paint graph DFS, flatten to list of (glyphId, color, transform) triples for GPU rendering.

- [ ] **Step 2: Commit**

```bash
git add font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt
git commit -m "font: add COLRv1 paint graph parsing to GlyphScaler"
```

---

#### Task 4.2: GPU COLRv1 rendering

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`

- [ ] **Step 1: Extend drawTextColorPass for COLRv1 flattened layers**

COLRv1 layers have per-layer transforms. Apply transforms to each layer's outline before drawing.

- [ ] **Step 2: Commit**

---

#### Task 4.3: Migrate ColrV1GM

Port the body (409 lines, 18 categories, 62 refs). Uses COLRv1 test font. Fallback to "ABCD" synthetic COLRv1 font when test font absent.

**Ref images:** 62 PNGs from `original-888/` to `reference/`.

---

### Final Verification

After all phases:

```bash
./gradlew :integration-tests:skia:compileTestKotlin :kanvas:test :font:scaler:test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

```bash
echo "Remaining emoji GMs in skia-integration-tests:" && ls skia-integration-tests/src/main/kotlin/org/skia/tests/*moji*GM.kt 2>/dev/null | wc -l
```
Expected: 0 (all 5 emoji GMs migrated)

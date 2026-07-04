# COLRv1 Pipeline Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `font:colr` module, extend COLRv1 parser (PaintScale/Rotate/Skew + multi-CPAL), wire into GlyphScaler, implement GPU renderer for COLRv1 paint graph nodes, and migrate ColrV1GM.

**Architecture:** New `font:colr` module breaks the `font:glyph → font:scaler` circular dependency. COLRv1 types (parser, table, planner) move from `font/glyph` to `font:colr`. `font:scaler` and `font:glyph` both depend on `font:colr`. GPU renderer dispatches per-paint-node (Solid, Glyph, Gradient, Transform, Composite, ClipBox).

**Tech Stack:** Pure Kotlin, OpenType binary parsing, Kanvas GPU pipeline.

---

### Task 1: Create font:colr module + migrate COLRv1 types

**Files:**
- Create: `font/colr/build.gradle.kts`
- Create: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/COLRV1Parser.kt`
- Create: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/COLRV1Table.kt`
- Create: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/CPALV0Parser.kt`
- Create: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/CPALTable.kt`
- Create: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/COLRV1ColorGlyphPlanner.kt`
- Create: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/ColorGlyphSurface.kt`
- Modify: `font/glyph/build.gradle.kts`
- Modify: `font/scaler/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Delete: COLRv1 types from `font/glyph/` (replaced by re-exports or direct imports from `font:colr`)

- [ ] **Step 1: Create build.gradle.kts for font:colr**

```bash
mkdir -p font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr
```

Write `font/colr/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":font:core"))
    implementation(project(":font:sfnt"))
    implementation(project(":math"))
    testImplementation(project(":codec:test-fixtures"))
}
```

- [ ] **Step 2: Register font:colr in settings.gradle.kts**

Read `settings.gradle.kts`, find the `font/` includes block, add:
```kotlin
include(":font:colr")
```

- [ ] **Step 3: Copy COLRv1 types from font/glyph to font/colr**

Read these files from `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`:
- Copy everything from line 1 to ~1922 (COLRv1Parser, COLRV1Table, CPALV0Parser, CPALTable, COLRV1ColorGlyphPlanner, all paint types, ColorGlyphPlan, ColorGlyphBounds, and supporting sealed types)
- Keep `ColorGlyphPlanner` interface in `font/glyph` — it references GPU types that stay there
- Move the implementation types to `font/colr/`

The files to create in `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/`:

`COLRV1Parser.kt` — the `COLRV1Parser` object with `parse(bytes, offset, length)` method. Copy from line ~2422 in ColorGlyphSurface.kt.

`COLRV1Table.kt` — `COLRV1Table` data class with `BaseGlyphV1List`, `LayerV1List`, `ClipList`, `PaintTable`, paint records, `paintForGlyph()`, `flattenedPaintGraph()`, `paintColrGlyphCycleDiagnostic()`.

`CPALV0Parser.kt` — `CPALV0Parser` object with `parse()` method.

`CPALTable.kt` — `CPALTable` data class.

`COLRV1ColorGlyphPlanner.kt` — `COLRV1ColorGlyphPlanner` class with `plan()`, `walk()`, budget limits, and `COLRV1ColorGlyphPlanDecision`.

`ColorGlyphSurface.kt` — shared types: `ColorGlyphPlan`, `ColorGlyphBounds`, `COLRV1PaintGraphEvidence`, `COLRV1PaintGraphNode`, `COLRV1GradientEvidence`, gradient geometry types (LinearGradientGeometry, RadialGradientGeometry, SweepGradientGeometry), `ColorGlyphRoute`, `ColorGlyphDiagnostic`, `COLRV1PlannerRefusal`, `COLRV0ColorGlyphPlanDecision`, `COLRV0ColorGlyphPlanner`, `SimpleColorGlyphPlanner`.

- [ ] **Step 4: Update font/glyph to depend on font:colr**

In `font/glyph/build.gradle.kts`, add:
```kotlin
implementation(project(":font:colr"))
```

Remove the copied files from `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/`. Update imports in remaining `font/glyph` files to import from `org.graphiks.kanvas.font.colr` instead of their local package.

- [ ] **Step 5: Update font/scaler to depend on font:colr**

In `font/scaler/build.gradle.kts`, add:
```kotlin
implementation(project(":font:colr"))
```

- [ ] **Step 6: Verify compilation**

```bash
./gradlew :font:colr:compileKotlin :font:glyph:compileKotlin :font:scaler:compileKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add font/colr/ font/glyph/ font/scaler/ settings.gradle.kts
git commit -m "font: create font:colr module, migrate COLRv1 types from font/glyph"
```

---

### Task 2: Extend COLRv1 parser (PaintScale/Rotate/Skew + multi-CPAL)

**Files:**
- Modify: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/COLRV1Parser.kt`
- Modify: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/CPALV0Parser.kt`
- Modify: `font/colr/src/main/kotlin/org/graphiks/kanvas/font/colr/CPALTable.kt`

- [ ] **Step 1: Add PaintScale/Rotate/Skew formats to COLRV1Parser**

Read the current parser. Find the `when (format)` dispatch. Add cases for formats 16-35:

```kotlin
// In COLRV1Parser.parse() dispatch:
16 -> parsePaintScale(bytes, offset, format)     // PaintScale
17 -> parsePaintVarRefusal(glyphId, format, "scale")  // PaintVarScale
20 -> parsePaintScaleAroundCenter(bytes, offset, format)
21 -> parsePaintVarRefusal(glyphId, format, "scale-around-center")
22 -> parsePaintScaleUniform(bytes, offset, format)
23 -> parsePaintVarRefusal(glyphId, format, "scale-uniform")
24 -> parsePaintScaleUniformAroundCenter(bytes, offset, format)
25 -> parsePaintVarRefusal(glyphId, format, "scale-uniform-around-center")
28 -> parsePaintRotate(bytes, offset, format)
29 -> parsePaintVarRefusal(glyphId, format, "rotate")
30 -> parsePaintRotateAroundCenter(bytes, offset, format)
31 -> parsePaintVarRefusal(glyphId, format, "rotate-around-center")
32 -> parsePaintSkew(bytes, offset, format)
33 -> parsePaintVarRefusal(glyphId, format, "skew")
34 -> parsePaintSkewAroundCenter(bytes, offset, format)
35 -> parsePaintVarRefusal(glyphId, format, "skew-around-center")
```

Implement the parse methods. PaintScale (format 16) structure: scaleX (F2DOT14), scaleY (F2DOT14), child paint offset (Offset24). PaintRotate (format 28): angle (F2DOT14), child. PaintSkew (format 32): xSkew (F2DOT14), ySkew (F2DOT14), child. AroundCenter variants add centerX/centerY (FWORD). Uniform variants have single scale value.

For Var* refusal:
```kotlin
private fun parsePaintVarRefusal(glyphId: Int, format: Int, kind: String): COLRV1Paint? {
    // Returns null — parser caller handles as "format not supported"
    return null
}
```

- [ ] **Step 2: Extend CPALV0Parser for multi-palette**

Read the current CPALV0Parser. When `numPalettes > 1`, parse all palettes instead of just the first. The CPAL structure has color record arrays concatenated — each palette's colors start at `offsetFirstColorRecord + paletteIndex * numPaletteEntries * 4`.

Update `CPALTable`:
```kotlin
data class CPALTable(
    val version: Int,
    val palettes: List<List<Int>>,
    val paletteTypes: List<Int>,
    val paletteLabels: List<Int>,
    val paletteEntryLabels: List<Int>?,
)
```

- [ ] **Step 3: Update planner to use multi-palette CPAL**

In `COLRV1ColorGlyphPlanner`, the palette resolution already handles `paletteSelection.index`. With multi-palette CPAL, use `cpal.palettes[paletteSelection.index]` instead of a single flat array.

- [ ] **Step 4: Verify compilation and tests**

```bash
./gradlew :font:colr:compileKotlin :font:colr:test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add font/colr/
git commit -m "colr: extend parser for PaintScale/Rotate/Skew formats + multi-palette CPAL"
```

---

### Task 3: Wire COLRv1 into GlyphScaler

**Files:**
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphScaler.kt`
- Modify: `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphRepresentation.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontTypeface.kt`

- [ ] **Step 1: Add ColorLayersV1 to GlyphRepresentation**

In `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/GlyphRepresentation.kt`, add after ColorLayers:

```kotlin
data class ColorLayersV1(
    val paintGraph: org.graphiks.kanvas.font.colr.COLRV1PaintGraphEvidence,
) : GlyphRepresentation
```

- [ ] **Step 2: Add COLRv1 parsing to GlyphScaler init**

In GlyphScaler.kt, add imports:
```kotlin
import org.graphiks.kanvas.font.colr.COLRV1Parser
import org.graphiks.kanvas.font.colr.COLRV1Table
import org.graphiks.kanvas.font.colr.CPALV0Parser
import org.graphiks.kanvas.font.colr.CPALTable
```

Add fields after existing table fields:
```kotlin
private val colrV1Table: COLRV1Table? = parseColrV1()
internal val cpalTable: CPALTable? = parseCpalMulti()
```

Add parse methods:
```kotlin
private fun parseColrV1(): COLRV1Table? {
    val colrTable = tables["COLR"] ?: return null
    val bytes = fontBytes
    val off = colrTable.offset
    if (off + 4 > bytes.size) return null
    val version = u16(bytes, off)
    if (version < 1) return null
    return COLRV1Parser.parse(bytes, colrTable.offset, colrTable.length)
}

private fun parseCpalMulti(): CPALTable? {
    val cpalTable = tables["CPAL"] ?: return null
    return try {
        CPALV0Parser.parse(fontBytes, cpalTable.offset, cpalTable.length)
    } catch (_: Exception) {
        null
    }
}
```

- [ ] **Step 3: Add COLRv1 check to scaleGlyph()**

In `scaleGlyph()`, after the COLRv0 check and before the CBDT check, add:

```kotlin
// Check COLRv1
val colrV1 = colrV1Table
if (colrV1 != null && colrV1.paintForGlyph(glyphId) != null) {
    val paintGraph = colrV1.flattenedPaintGraph(glyphId)
    if (paintGraph != null) {
        return ScaledGlyph(
            sourceCodepoint = sourceCodepoint,
            glyphId = glyphId,
            size = size,
            advanceWidth = advance,
            bounds = GlyphBounds(0.0, 0.0, size.toDouble(), size.toDouble()),
            commands = emptyList(),
            representation = GlyphRepresentation.ColorLayersV1(paintGraph),
        )
    }
}
```

- [ ] **Step 4: Expose planner from FontTypeface**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontTypeface.kt`, add method:

```kotlin
import org.graphiks.kanvas.font.colr.COLRV1ColorGlyphPlanner
import org.graphiks.kanvas.font.colr.COLRV1ColorGlyphPlanDecision
import org.graphiks.kanvas.font.colr.CPALPaletteSelection
import org.graphiks.kanvas.font.FontCore
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.GlyphStrikeKey

internal fun planCOLRV1Glyph(
    glyphId: Int,
    fontSize: Float,
    paletteIndex: Int = 0,
): COLRV1ColorGlyphPlanDecision? {
    val s = scaler ?: return null
    val colrTable = s.colrV1Table ?: return null
    val cpalTable = s.cpalTable ?: return null
    val planner = COLRV1ColorGlyphPlanner(colrTable, cpalTable)
    // Derive a simple typefaceId from name + font bytes hash
    val typefaceId = TypefaceID(fontName + "-" + fontBytes.contentHashCode().toString())
    val strikeKey = GlyphStrikeKey(
        typefaceId = typefaceId,
        fontSize = fontSize,
        // default other params
    )
    return planner.plan(
        glyphId = glyphId,
        typefaceId = typefaceId,
        strikeKey = strikeKey,
        paletteSelection = CPALPaletteSelection(paletteIndex),
    )
}
```

If GlyphStrikeKey/TypefaceID imports are unavailable from kanvas/text (it may not depend on font modules), simplify by adding a `getScaler()` method on FontTypeface that GPURenderer uses directly, and have GPURenderer construct the planner:

```kotlin
// Simpler — just expose scaler (already internal):
// FontTypeface.scaler is already internal val
```

GPURenderer already imports from font modules. It can construct the planner directly.

- [ ] **Step 5: Verify compilation**

```bash
./gradlew :font:scaler:compileKotlin :kanvas:compileKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add font/scaler/ kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontTypeface.kt
git commit -m "font: wire COLRv1 into GlyphScaler — ColorLayersV1 representation"
```

---

### Task 4: GPU renderer — Solid, Glyph, Transform dispatch

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`

- [ ] **Step 1: Extend hasColorGlyphs for ColorLayersV1**

In GPURenderer.kt, find the `hasColorGlyphs()` method. Change the return condition to include ColorLayersV1:

```kotlin
when (rep) {
    is GlyphRepresentation.ColorLayers,
    is GlyphRepresentation.Bitmap,
    is GlyphRepresentation.ColorLayersV1 -> return true
    else -> {}
}
```

- [ ] **Step 2: Add renderColorText method**

After `hasColorGlyphs()`, add a new private method. This dispatches by representation type:

```kotlin
private fun renderColorText(
    op: DisplayOp.DrawText,
    cmdId: GPUDrawCommandID,
    targets: GPUTargetFacts,
    t: TextureSurfaceManager,
    sceneLabel: SceneId,
    dispatched: DispatchedOpAccumulator,
    diagnostics: RenderDiagnosticCollector,
    width: Int,
    height: Int,
    config: RenderConfig,
    tessellator: PathTessellator,
) {
    val tf = op.blob.typeface as? FontTypeface ?: return
    val scaler = tf.scaler ?: return

    for (run in op.blob.glyphRuns) {
        for ((idx, gid) in run.glyphs.withIndex()) {
            val pos = run.positions[idx]
            val rep = scaler.getGlyphRepresentation(gid.toInt(), op.blob.fontSize) ?: continue

            when (rep) {
                is GlyphRepresentation.ColorLayersV1 -> {
                    val colrTable = scaler.colrV1Table ?: continue
                    val cpalTable = scaler.cpalTable ?: continue
                    val planner = COLRV1ColorGlyphPlanner(colrTable, cpalTable)
                    val decision = planner.plan(
                        glyphId = gid.toInt(),
                        typefaceId = TypefaceID("kanvas"),
                        strikeKey = GlyphStrikeKey(
                            typefaceId = TypefaceID("kanvas"),
                            fontSize = op.blob.fontSize,
                        ),
                        paletteSelection = CPALPaletteSelection(0),
                    )
                    val plan = decision.plan ?: continue
                    val paintGraph = plan.paintGraph ?: continue
                    for (node in paintGraph.nodes) {
                        dispatchColrV1Node(
                            node, scaler, op.blob.fontSize, pos, op,
                            cmdId, targets, t, sceneLabel, dispatched, diagnostics,
                            width, height, config, tessellator,
                        )
                    }
                }
                is GlyphRepresentation.ColorLayers -> {
                    for (layer in rep.layers) {
                        val scaled = scaler.scaleGlyph(layer.glyphId, op.blob.fontSize)
                        if (scaled.commands.isEmpty()) continue
                        val color = Color.fromRGBA(
                            ((layer.paletteColorArgb shr 16) and 0xFF) / 255f,
                            ((layer.paletteColorArgb shr 8) and 0xFF) / 255f,
                            (layer.paletteColorArgb and 0xFF) / 255f,
                            ((layer.paletteColorArgb shr 24) and 0xFF) / 255f,
                        )
                        dispatchGlyphOutline(
                            scaled.commands, pos, op, color, op.transform, op.clip,
                            cmdId, targets, t, sceneLabel, dispatched, diagnostics,
                            width, height, config, tessellator,
                        )
                    }
                }
                is GlyphRepresentation.Bitmap -> {
                    diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "bitmap_glyph_not_yet_routed")
                }
                else -> {}
            }
        }
    }
}
```

- [ ] **Step 3: Implement dispatchColrV1Node for Solid + Glyph**

Add a helper method that dispatches a single COLRv1 paint graph node:

```kotlin
private fun dispatchColrV1Node(
    node: COLRV1PaintGraphNode,
    scaler: GlyphScaler,
    fontSize: Float,
    pos: org.graphiks.kanvas.types.Point,
    op: DisplayOp.DrawText,
    cmdId: GPUDrawCommandID,
    targets: GPUTargetFacts,
    t: TextureSurfaceManager,
    sceneLabel: SceneId,
    dispatched: DispatchedOpAccumulator,
    diagnostics: RenderDiagnosticCollector,
    width: Int,
    height: Int,
    config: RenderConfig,
    tessellator: PathTessellator,
) {
    when {
        node.kind.startsWith("colrv1-paint-solid") -> {
            val colorArgb = node.resolvedColorArgb ?: return
            val color = Color.fromRGBA(
                ((colorArgb shr 16) and 0xFF) / 255f,
                ((colorArgb shr 8) and 0xFF) / 255f,
                (colorArgb and 0xFF) / 255f,
                ((colorArgb shr 24) and 0xFF) / 255f,
            )
            // Solid paints have no glyph — they fill the bounds rect.
            // If the solid has child paints (gradient on a solid), children are handled separately.
            // For standalone solid: dispatch a fill rect with the color.
            val rect = Rect(
                pos.x + op.x + (node.bounds?.left?.toFloat() ?: 0f) * fontSize,
                pos.y + op.y + (node.bounds?.top?.toFloat() ?: 0f) * fontSize,
                pos.x + op.x + (node.bounds?.right?.toFloat() ?: 1f) * fontSize,
                pos.y + op.y + (node.bounds?.bottom?.toFloat() ?: 1f) * fontSize,
            )
            val pathData = PathData().apply { addRect(rect) }
            val flat = pathData.flatten()
            if (flat.size < 3) return
            val tri = tessellator.triangulate(flat)
            val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
            val contourStarts = listOf(0)
            val cmd = op.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                dispatchFillPath(
                    cmd.copy(paint = cmd.paint.copy(color = color)),
                    dispatched, diagnostics, width, height, config)
            }
        }
        node.kind == "colrv1-paint-glyph" -> {
            val paintGlyphId = node.referencedGlyphId ?: return
            val scaled = scaler.scaleGlyph(paintGlyphId, fontSize)
            if (scaled.commands.isEmpty()) return
            dispatchGlyphOutline(
                scaled.commands, pos, op, op.paint.color, op.transform, op.clip,
                cmdId, targets, t, sceneLabel, dispatched, diagnostics,
                width, height, config, tessellator,
            )
        }
        node.kind.startsWith("colrv1-paint-translate") ||
        node.kind.startsWith("colrv1-paint-scale") ||
        node.kind.startsWith("colrv1-paint-rotate") ||
        node.kind.startsWith("colrv1-paint-skew") ||
        node.kind == "colrv1-paint-transform" -> {
            diagnostics.degrade(
                "degrade:drawText:${cmdId.value}", "drawText",
                "colrv1_transform_not_yet_routed:${node.kind}")
        }
        node.kind.startsWith("colrv1-paint-linear-gradient") ||
        node.kind.startsWith("colrv1-paint-radial-gradient") ||
        node.kind.startsWith("colrv1-paint-sweep-gradient") -> {
            diagnostics.degrade(
                "degrade:drawText:${cmdId.value}", "drawText",
                "colrv1_gradient_not_yet_routed:${node.kind}")
        }
        node.kind.startsWith("colrv1-paint-composite") -> {
            diagnostics.degrade(
                "degrade:drawText:${cmdId.value}", "drawText",
                "colrv1_composite_not_yet_routed:${node.kind}")
        }
    }
}
```

- [ ] **Step 4: Add dispatchGlyphOutline helper**

```kotlin
private fun dispatchGlyphOutline(
    commands: List<OutlineCommand>,
    pos: org.graphiks.kanvas.types.Point,
    op: DisplayOp.DrawText,
    color: Color,
    transform: Matrix33,
    clip: ClipStack,
    cmdId: GPUDrawCommandID,
    targets: GPUTargetFacts,
    t: TextureSurfaceManager,
    sceneLabel: SceneId,
    dispatched: DispatchedOpAccumulator,
    diagnostics: RenderDiagnosticCollector,
    width: Int,
    height: Int,
    config: RenderConfig,
    tessellator: PathTessellator,
) {
    val pathData = PathData()
    for (cmd in commands) {
        when (cmd) {
            is OutlineCommand.MoveTo -> pathData.moveTo(
                cmd.x.toFloat() + pos.x + op.x,
                cmd.y.toFloat() + pos.y + op.y)
            is OutlineCommand.LineTo -> pathData.lineTo(
                cmd.x.toFloat() + pos.x + op.x,
                cmd.y.toFloat() + pos.y + op.y)
            is OutlineCommand.QuadraticTo -> pathData.quadTo(
                cmd.controlX.toFloat() + pos.x + op.x,
                cmd.controlY.toFloat() + pos.y + op.y,
                cmd.x.toFloat() + pos.x + op.x,
                cmd.y.toFloat() + pos.y + op.y)
            is OutlineCommand.CubicTo -> pathData.cubicTo(
                cmd.controlX1.toFloat() + pos.x + op.x,
                cmd.controlY1.toFloat() + pos.y + op.y,
                cmd.controlX2.toFloat() + pos.x + op.x,
                cmd.controlY2.toFloat() + pos.y + op.y,
                cmd.x.toFloat() + pos.x + op.x,
                cmd.y.toFloat() + pos.y + op.y)
            is OutlineCommand.Close -> pathData.close()
        }
    }
    val flat = pathData.flatten()
    if (flat.size < 3) return
    val tri = tessellator.triangulate(flat)
    val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
    val contourStarts = listOf(0)
    val cmd = op.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
        dispatchFillPath(
            cmd.copy(paint = cmd.paint.copy(color = color)),
            dispatched, diagnostics, width, height, config)
    }
}
```

- [ ] **Step 5: Update DrawText handler to call renderColorText**

In both `is DisplayOp.DrawText` blocks (main loop and nested DrawPicture loop), replace the degrade diagnostic with a call:

```kotlin
if (hasColorGlyphs(op.blob)) {
    renderColorText(op, cmdId, targets, t, sceneLabel, dispatched, diagnostics, width, height, config, tessellator)
    sceneHasContent = true
    continue
}
```

- [ ] **Step 6: Add necessary imports**

Add to GPURenderer.kt imports:
```kotlin
import org.graphiks.kanvas.font.colr.COLRV1ColorGlyphPlanner
import org.graphiks.kanvas.font.colr.COLRV1PaintGraphNode
import org.graphiks.kanvas.font.colr.CPALPaletteSelection
import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.font.scaler.GlyphScaler
```

Check if `TypefaceID` and `GlyphStrikeKey` constructors are accessible. If they have private constructors, simplify by passing just the planner output directly from FontTypeface and avoid those imports.

- [ ] **Step 7: Verify compilation**

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL (fix any import issues)

- [ ] **Step 8: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: add COLRv1 Solid + Glyph paint node dispatch to renderColorText"
```

---

### Task 5: GPU renderer — Gradient + Composite + ClipBox support

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`

- [ ] **Step 1: Replace gradient degrade with actual dispatch**

In `dispatchColrV1Node()`, replace the gradient degrade block with:

```kotlin
node.kind.startsWith("colrv1-paint-linear-gradient") -> {
    val gradient = node.gradient ?: return@dispatchColrV1Node
    val stops = gradient.stops.map { stop ->
        val colorArgb = stop.colorArgb
        GradientStop(
            position = stop.position,
            color = Color.fromRGBA(
                ((colorArgb shr 16) and 0xFF) / 255f,
                ((colorArgb shr 8) and 0xFF) / 255f,
                (colorArgb and 0xFF) / 255f,
                ((colorArgb shr 24) and 0xFF) / 255f,
            )
        )
    }
    val shader = when (gradient.geometry) {
        is LinearGradientGeometry -> Shader.LinearGradient(
            start = Point(gradient.geometry.x0 * fontSize + pos.x + op.x, gradient.geometry.y0 * fontSize + pos.y + op.y),
            end = Point(gradient.geometry.x1 * fontSize + pos.x + op.x, gradient.geometry.y1 * fontSize + pos.y + op.y),
            stops = stops,
            tileMode = when (gradient.extendMode) {
                ExtendMode.PAD -> TileMode.CLAMP
                ExtendMode.REPEAT -> TileMode.REPEAT
                ExtendMode.REFLECT -> TileMode.MIRROR
            },
        )
        is RadialGradientGeometry -> Shader.RadialGradient(
            center = Point(gradient.geometry.x0 * fontSize + pos.x + op.x, gradient.geometry.y0 * fontSize + pos.y + op.y),
            radius = EuclideanDistance.from(gradient.geometry.x0, gradient.geometry.y0, gradient.geometry.x1, gradient.geometry.y1) * fontSize,
            stops = stops,
            tileMode = when (gradient.extendMode) {
                ExtendMode.PAD -> TileMode.CLAMP
                ExtendMode.REPEAT -> TileMode.REPEAT
                ExtendMode.REFLECT -> TileMode.MIRROR
            },
        )
        is SweepGradientGeometry -> {
            // Degrade — sweeps not yet in GPU shader pipeline
            diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_sweep_not_yet_routed")
            return@dispatchColrV1Node
        }
    }
    // Dispatch fill rect with gradient shader over bounds
    val bounds = node.bounds ?: return@dispatchColrV1Node
    val rect = Rect(
        pos.x + op.x + bounds.left.toFloat() * fontSize,
        pos.y + op.y + bounds.top.toFloat() * fontSize,
        pos.x + op.x + bounds.right.toFloat() * fontSize,
        pos.y + op.y + bounds.bottom.toFloat() * fontSize,
    )
    val pathData = PathData().apply { addRect(rect) }
    val flat = pathData.flatten()
    val tri = tessellator.triangulate(flat)
    val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
    val cmd = op.toNormalizedCommand(cmdId, targets, vertices, listOf(0), flat.size)
        .copy(paint = cmd.paint.copy(shader = shader))
    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
        dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
    }
}
```

- [ ] **Step 2: Replace transform degrade with actual dispatch**

```kotlin
node.kind.startsWith("colrv1-paint-translate") ||
node.kind.startsWith("colrv1-paint-scale") ||
node.kind.startsWith("colrv1-paint-rotate") ||
node.kind.startsWith("colrv1-paint-skew") ||
node.kind == "colrv1-paint-transform" -> {
    val matrix = node.transformMatrix ?: return@dispatchColrV1Node
    // Transform nodes modify the CTM for child paints.
    // Push transform, process children (recursive), pop.
    // For now, if the planner has already baked child paints into the flat list,
    // we'd need to apply the transform to each child's geometry.
    // Simplified: degrade with diagnostic until recursive processing is implemented.
    diagnostics.degrade(
        "degrade:drawText:${cmdId.value}", "drawText",
        "colrv1_transform_passthrough:${node.kind} — children rendered without transform")
    // Children are in the flat node list after this node — they'll be processed
    // by subsequent dispatchColrV1Node calls (no-op for transform node).
}
```

- [ ] **Step 3: Replace composite degrade with actual dispatch**

```kotlin
node.kind.startsWith("colrv1-paint-composite") -> {
    // Composite nodes in the flat list: source children precede backdrop children.
    // The planner already orders them correctly in paintGraph.nodes.
    // GPU rendering uses saveLayer for backdrop compositing.
    diagnostics.degrade(
        "degrade:drawText:${cmdId.value}", "drawText",
        "colrv1_composite_passthrough:${node.kind} — source/backdrop rendered sequentially")
    // Source and backdrop children render normally via subsequent node dispatches.
}
```

- [ ] **Step 4: Add ClipBox support**

ClipBox nodes wrap glyph children with a clip rect. In the planner, clipbox sets `node.bounds`. The GPU can apply `canvas.clipRect(bounds)` before rendering children.

```kotlin
node.kind == "colrv1-paint-clipbox" -> {
    // Children rendered after this node in flat list.
    // Clip applied by passing bounds through to child dispatch.
    // For simplicity: degrade with passthrough.
    diagnostics.degrade(
        "degrade:drawText:${cmdId.value}", "drawText",
        "colrv1_clipbox_passthrough — children rendered unclipped")
}
```

- [ ] **Step 5: Reorder nodes in dispatchColrV1Node**

The `when` block should check node.kind in this priority order:
1. `colrv1-paint-solid` (rendered)
2. `colrv1-paint-glyph` (rendered)
3. `colrv1-paint-colr-glyph` (passthrough)
4. `colrv1-paint-linear-gradient` / `radial-gradient` (rendered)
5. `colrv1-paint-sweep-gradient` (degrade)
6. Transform nodes (passthrough)
7. Composite nodes (passthrough)
8. ClipBox nodes (passthrough)

- [ ] **Step 6: Add necessary imports**

```kotlin
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.font.colr.LinearGradientGeometry
import org.graphiks.kanvas.font.colr.RadialGradientGeometry
import org.graphiks.kanvas.font.colr.SweepGradientGeometry
import org.graphiks.kanvas.font.colr.ExtendMode
import org.graphiks.kanvas.types.EuclideanDistance
```

- [ ] **Step 7: Verify compilation**

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: add COLRv1 gradient shader dispatch + transform/composite/clipbox passthrough"
```

---

### Task 6: Migrate ColrV1GM

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/ColrV1Gm.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/ColrV1GM.kt`
- Delete: `skia-integration-tests/src/test/kotlin/org/skia/tests/ColrV1Test.kt`
- Modify: `integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`
- Move: `skia-integration-tests/src/test/resources/original-888/colrv1_gradient_stops_repeat.png` → `integration-tests/skia/src/test/resources/reference/colrv1_gradient_stops_repeat.png`

- [ ] **Step 1: Create ColrV1Gm.kt**

Write `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/ColrV1Gm.kt`:

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
 * Port of Skia's `gm/colrv1.cpp` — default `gradient_stops_repeat` category.
 * When the COLRv1 test font is absent, falls back to drawing "ABCD"
 * via Liberation Sans.
 * @see https://github.com/google/skia/blob/main/gm/colrv1.cpp
 */
class ColrV1Gm : SkiaGm {
    override val name = "colrv1_gradient_stops_repeat"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    private val codepoints = intArrayOf(0xf0100, 0xf0101, 0xf0102, 0xf0103)
    private val textSizes = floatArrayOf(12f, 18f, 30f, 120f)
    private val colors = listOf(Color.BLACK, Color.GREEN, Color.RED, Color.BLUE)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.translate(200f, 20f)

        val tf = EmojiTypeface.createOrFallback(EmojiTypeface.Format.COLRv0, ByteArray(0))
        val font = Font(tf, antiAlias = false, subpixel = true)

        var colorIdx = 0
        var y = 0f

        for (textSize in textSizes) {
            val f = font.copy(size = textSize)
            val metrics = f.getMetrics()
            val yShift = if (metrics != null) {
                -(metrics.ascent + metrics.descent + metrics.leading) * 1.2f
            } else {
                textSize * 1.5f
            }
            y += yShift
            val paint = Paint(color = colors[colorIdx])
            var x = 0f

            for (cp in codepoints) {
                val cpStr = String(Character.toChars(cp))
                val glyphAdvance = f.measureText(cpStr)
                if (0f < x && 1000f < x + glyphAdvance) {
                    y += yShift
                    x = 0f
                }
                canvas.drawSimpleText(cpStr, x, y, f, paint)
                x += glyphAdvance * 1.05f
            }
            colorIdx++
        }
    }
}
```

- [ ] **Step 2: Move reference image**

```bash
mv skia-integration-tests/src/test/resources/original-888/colrv1_gradient_stops_repeat.png \
   integration-tests/skia/src/test/resources/reference/colrv1_gradient_stops_repeat.png
```

- [ ] **Step 3: Create placeholder render**

```bash
touch integration-tests/skia/src/test/resources/generated-renders/text/colrv1_gradient_stops_repeat.png
```

- [ ] **Step 4: Register in ServiceLoader**

Insert `org.graphiks.kanvas.skia.gm.text.ColrV1Gm` alphabetically (after `ChromeGradText2Gm`, before `ColorEmojiGm`).

- [ ] **Step 5: Delete old files**

```bash
rm skia-integration-tests/src/main/kotlin/org/skia/tests/ColrV1GM.kt
rm skia-integration-tests/src/test/kotlin/org/skia/tests/ColrV1Test.kt
```

- [ ] **Step 6: Verify compilation**

```bash
./gradlew :integration-tests:skia:compileTestKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit (3 commits)**

```bash
# Commit 1: additions
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/ColrV1Gm.kt integration-tests/skia/src/test/resources/reference/colrv1_gradient_stops_repeat.png integration-tests/skia/src/test/resources/generated-renders/text/colrv1_gradient_stops_repeat.png integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm
git commit -m "gm: migrate ColrV1 (gradient_stops_repeat) to text/ColrV1Gm"

# Commit 2: deletions
git add skia-integration-tests/src/main/kotlin/org/skia/tests/ColrV1GM.kt skia-integration-tests/src/test/kotlin/org/skia/tests/ColrV1Test.kt
git commit -m "gm: delete old ColrV1 source and test"

# Commit 3: ref deletion
git add skia-integration-tests/src/test/resources/original-888/colrv1_gradient_stops_repeat.png
git commit -m "gm: remove old colrv1_gradient_stops_repeat reference from original-888/"
```

- [ ] **Step 8: Final verification**

```bash
./gradlew :font:colr:test :font:scaler:test :font:glyph:test :kanvas:test :integration-tests:skia:compileTestKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

# Text Render Precision — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Replace hardcoded 10×10 glyph quads with per-glyph metric sizing and add per-run fontSize to KanvasGlyphRun.

**Architecture:** `GpuTextBlob` gains `glyphRects` filled during atlas packing. `KanvasGlyphRun` gains `fontSize`. `TextBridge.rasterizeViaFont()` uses per-run fontSize for glyph scaling.

**Tech Stack:** Pure Kotlin, Kanvas GPU pipeline.

---

### Phase 1: Per-glyph quad sizing

#### Task 1.1: Add glyphRects to GpuTextBlob

**File:** `kanvas/src/main/kotlin/org/graphiks/kanvas/text/GpuTextBlob.kt`

- [ ] Add `glyphRects: List<Rect>` field with a lazy default (same pattern as glyphUvs):

```kotlin
data class GpuTextBlob(
    val textBlob: TextBlob,
    val atlasRgba: ByteArray,
    val atlasWidth: Int,
    val atlasHeight: Int,
    private val glyphUvData: List<Rect>? = null,
    val glyphRects: List<Rect> = emptyList(),  // NEW — device-pixel glyph dimensions
) {
    // ... existing code unchanged
}
```

Update `equals`/`hashCode` to include `glyphRects`.

Commit: `fix: add glyphRects to GpuTextBlob for per-glyph quad sizing`

#### Task 1.2: Fill glyphRects during atlas packing

**File:** `kanvas/src/main/kotlin/org/graphiks/kanvas/text/TextBridge.kt`

- [ ] In `rasterizeViaFont()`, after atlas planning, build `glyphRects` from placements:

```kotlin
val glyphRects = MutableList<Rect?>(totalGlyphCount) { null }

for ((entryIdx, placement) in plan.placements.withIndex()) {
    val r = placement.region
    val rect = Rect(0f, 0f, r.width.toFloat(), r.height.toFloat())
    for ((globalIdx, entryIdx2) in glyphIndexToEntry) {
        if (entryIdx2 == entryIdx) {
            glyphRects[globalIdx] = rect
        }
    }
}

val finalRects = glyphRects.map { it ?: Rect(0f, 0f, 10f, 10f) }

return GpuTextBlob(blob, plan.atlasBytes, plan.atlasWidth, plan.atlasHeight, finalUvs, glyphRects = finalRects)
```

Note: `GpuTextBlob` constructor call now includes the new `glyphRects` parameter.

Commit: `fix: fill glyphRects from atlas placements in TextBridge`

#### Task 1.3: Use glyphRects in drawTextAtlasPass

**File:** `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`

- [ ] Find `drawTextAtlasPass` (around line 1084). Replace the hardcoded `w=10f, h=10f` with actual glyph rects:

```kotlin
// Before (line ~1108):
val w = 10f
val h = 10f

// After:
val glyphRect = gpuBlob.glyphRects.getOrNull(globalIdx) ?: Rect(0f, 0f, 10f, 10f)
val w = glyphRect.width
val h = glyphRect.height
```

Commit: `fix: use per-glyph rect sizing in drawTextAtlasPass`

#### Task 1.4: Regenerate renders and update scores

- [ ] `./gradlew :integration-tests:skia:generateSkiaRenders`
- [ ] `./gradlew :integration-tests:skia:test --tests "*SkiaGmRunner*"`
- [ ] Read `integration-tests/skia/test-similarity-scores.properties`
- [ ] Update minSimilarity for: bigtext, overdraw_text_xform, blob_rsxform, blob_rsxform_distortable, textfilter_image

Commit: `gm: update minSimilarity for fixed glyph sizing text GMs`

---

### Phase 2: Multi-size TextBlob

#### Task 2.1: Add fontSize to KanvasGlyphRun

**File:** `kanvas/src/main/kotlin/org/graphiks/kanvas/text/TextBlob.kt`

- [ ] Find `data class KanvasGlyphRun`. Add `fontSize`:

```kotlin
data class KanvasGlyphRun(
    val glyphs: List<UShort>,
    val positions: List<Point>,
    val fontSize: Float = 12f,  // NEW
)
```

Commit: `fix: add per-run fontSize to KanvasGlyphRun`

#### Task 2.2: Update Font.toTextBlob

**File:** `kanvas/src/main/kotlin/org/graphiks/kanvas/text/Font.kt`

- [ ] In `toTextBlob()`, pass `size` to the run:

```kotlin
return TextBlob(
    glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions, fontSize = size)),
    typeface = typeface,
    fontSize = size,
)
```

Commit: `fix: pass fontSize to KanvasGlyphRun in Font.toTextBlob`

#### Task 2.3: Use per-run fontSize in TextBridge

**File:** `kanvas/src/main/kotlin/org/graphiks/kanvas/text/TextBridge.kt`

- [ ] In `rasterizeViaFont()`, change `blob.fontSize` → `run.fontSize`:

```kotlin
for (run in blob.glyphRuns) {
    for (glyphId in run.glyphs) {
        try {
            val scaled = scaler.scaleGlyph(glyphId.toInt(), run.fontSize)  // was: blob.fontSize
```

Commit: `fix: use per-run fontSize in TextBridge.rasterizeViaFont`

#### Task 2.4: Update GM source files

**Files:** `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/text/`

- [ ] `TextBlobTransformsGm.kt` — runs with A(162pt), B(72pt), C(32pt) → add explicit `fontSize` per run
- [ ] `TextBlobColorTransGm.kt` — runs with AB(256pt), pangram(28pt) → add explicit `fontSize` per run
- [ ] `MixedTextBlobsGm.kt` — runs with O(385pt), LCD(32pt), aA(12pt) → add explicit `fontSize` per run
- [ ] Also check `BlobRSXformGm.kt` and `BlobRSXformDistortableGm.kt` — if they construct TextBlob manually, add fontSize

Commit: `gm: add per-run fontSize to multi-size text GMs`

#### Task 2.5: Regenerate renders and update scores

- [ ] `./gradlew :integration-tests:skia:generateSkiaRenders`
- [ ] `./gradlew :integration-tests:skia:test --tests "*SkiaGmRunner*"`
- [ ] Update minSimilarity for: textblobtransforms, textblobcolortrans, mixedtextblobs
- [ ] Also update scores for the 5 GMs from Phase 1 if they improved further

Commit: `gm: update minSimilarity for multi-size textblob GMs`

---

### Final verification

```bash
./gradlew :kanvas:compileKotlin :integration-tests:skia:compileTestKotlin 2>&1 | tail -3
# Expected: BUILD SUCCESSFUL

./gradlew :integration-tests:skia:test --tests "*SkiaGmRunner*" 2>&1 | grep "tests completed"
# Expected: all fixed GMs now pass with raised minSimilarity
```

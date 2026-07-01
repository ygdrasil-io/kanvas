# Kanvas Implementation Prompts — Copy/Paste Ready

Each block below is a self-contained sub-agent prompt. Paste into a new conversation with the Task tool (`general` subagent type). Working directory is always `/Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine`.

---

## Prompt 1: Phase 1+2 GPU OpMapper Wiring

```
You are implementing GPU dispatch for the new Canvas drawing commands in Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

The following DisplayOp variants were just added to the codebase and need GPU dispatch in `GPUOpMapper.kt`. Currently they all emit `diagnostics.fatal("unsupported_operation")`. You need to implement real dispatch for each one that can be reasonably handled, matching the existing patterns in the file.

Read the existing file first: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`

Study how existing ops like `DrawRect` and `DrawRRect` are mapped. Follow the same patterns.

### TASKS

For each DisplayOp below, replace the `diagnostics.fatal` stub with real dispatch:

**1. DrawColor / Clear** — fill entire surface with a color.
- `DrawColor`: emit a full-surface rect fill using the color and blend mode. Use `Rect.fromLTRB(0, 0, surfaceWidth, surfaceHeight)` and the existing solid-color fill pipeline.
- `Clear`: same as DrawColor but force blend mode to SRC (direct overwrite).

**2. DrawPoint** — single pixel point.
- Convert to a tiny quad (1px × 1px) at (x, y) and dispatch as a rect fill. Or convert to a Path with moveTo/lineTo and dispatch as path.

**3. DrawPoints** — multiple points with a mode.
- `POINTS`: each point becomes a tiny rect (like DrawPoint)
- `LINES`: pairs of points become line segments → path with moveTo/lineTo pairs
- `POLYGON`: all points become a closed polygon → path with moveTo + lineTo chain + close
- Dispatch the resulting path via the existing path pipeline.

**4. DrawDRRect** — double rounded rectangle.
- Convert to a Path: outer RRect contour (clockwise) + inner RRect contour (counter-clockwise). Use `Path.addRRect` for the outer, reverse the inner points, then dispatch as a Path draw.

**5. DrawImageNine** — 9-patch image.
- Decompose into 9 individual `DrawImage` ops for each cell. The 9 cells are: 4 corners (fixed size), 4 edges (stretch in one direction), 1 center (stretch in both). Compute each cell's src/dst rects from the center rect and dst rect, then call the existing drawImage dispatch for each.

**6. DrawImageLattice** — lattice image grid.
- Same as DrawImageNine but the grid is defined by `lattice.xDivs` and `lattice.yDivs`. Decompose into (xDivs+1)×(yDivs+1) individual drawImage ops. Use `lattice.rects` for per-cell texture coordinates if present, `lattice.colors` for per-cell tinting.

**7. DrawVertices** — triangle mesh.
- Dispatch as a textured triangle mesh render pass. Use the vertices' positions, texCoords, colors, and indices. For now, emit a diagnostic DEGRADE if texCoords is non-null (textured mesh not yet supported) but still draw the mesh with colors.

**8. DrawAtlas** — sprite atlas batch.
- Decompose into individual drawImage ops. For each sprite: compute the dst rect from the transform matrix, use the corresponding texRect, apply the optional color tint. Dispatch each as drawImage.

**9. DrawPicture** — nested picture expansion.
- Recursively expand `picture.ops` into the current display list. Each op in the nested picture inherits the outer DrawPicture's transform (concatenated with the op's own transform) and clip (intersected with the op's own clip). Apply the DrawPicture's optional Paint as alpha modulation.

**10. Annotation** — metadata marker.
- No-op. These are invisible metadata tags. Just remove the fatal diagnostic.

### VERIFICATION

After making changes, compile and run tests:
```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test 2>&1 | tail -10
```

Commit with: `git add -A && git commit -m "feat(kanvas): GPU dispatch for Phase 1-2 DisplayOps"`

Return: compilation result, test result, and summary of what was implemented.
```

---

## Prompt 2: Phase 2 Canvas Tests

```
You are writing tests for the new complex Canvas drawing methods in Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

These methods were added to Canvas.kt but need tests:
- drawImageNine, drawImageLattice
- drawVertices, drawAtlas
- drawPatch (in CanvasExtensions.kt)
- drawAnnotation

Read these files first:
- kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/CanvasExtensions.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOp.kt
- kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/ScaffoldCanvasTest.kt (existing test, follow its pattern)

### TASKS

Add tests to `kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/ScaffoldCanvasTest.kt`:

**1. drawImageNine**: Create a test Image (100x100), call drawImageNine with center=Rect(30,30,70,70) and dst=Rect(0,0,200,200). Verify a DrawImageNine DisplayOp is emitted with correct fields.

**2. drawImageLattice**: Create a Lattice with xDivs=[25,75], yDivs=[25,75], call drawImageLattice. Verify DrawImageLattice op emitted.

**3. drawVertices**: Create Vertices with 3 positions forming a triangle, call drawVertices. Verify DrawVertices op emitted.

**4. drawAtlas**: Create test Image, 2 transform matrices, 2 tex rects, call drawAtlas. Verify DrawAtlas op emitted.

**5. drawPatch**: Call drawPatch with 12 control points (4×3 for 4 cubic curves forming a rectangle). Verify it doesn't crash and emits a DrawVertices DisplayOp (since patch tessellates to vertices).

**6. drawAnnotation**: Call drawAnnotation with a rect, key, value. Verify Annotation op emitted.

**7. Edge cases**: 
- drawImageNine with empty center rect
- drawAtlas with empty lists
- drawPatch with fewer than 12 points (should handle gracefully)

### VERIFICATION

```bash
./gradlew :kanvas:test --tests "org.graphiks.kanvas.canvas.ScaffoldCanvasTest" 2>&1 | tail -15
```

Commit: `git commit -m "test(kanvas): Phase 2 complex Canvas draw tests"`

Return: test results and any issues found.
```

---

## Prompt 3: Phase 3 Picture Serialization + GPU Mapper

```
You are implementing Picture serialization and GPU expansion in Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

Picture.kt has stubs for toByteArray() and fromByteArray(). These need real implementation. Also, the GPU mapper needs DrawPicture expansion (handled by Prompt 1, but verify it works).

Read: kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt

### TASKS

**1. toByteArray() implementation**

Serialize the Picture to a compact binary format:
- Magic bytes: `[0x4B, 0x50, 0x49, 0x43]` ("KPIC")
- Version: Int (1)
- cullRect: 4× Float (left, top, right, bottom)
- opCount: Int
- For each DisplayOp: type discriminator (Byte) + op-specific fields

Use kotlinx.serialization or manual binary encoding. For Images embedded in DrawImage/DrawImageNine/DrawImageLattice, encode the image pixels as PNG bytes using the existing `toPng()` infrastructure.

Type discriminators:
```
0 = DrawRect, 1 = DrawRRect, 2 = DrawDRRect, 3 = DrawPath, 4 = DrawPoint,
5 = DrawPoints, 6 = DrawImage, 7 = DrawImageNine, 8 = DrawImageLattice,
9 = DrawText, 10 = DrawPicture (recursive), 11 = DrawVertices, 12 = DrawAtlas,
13 = DrawColor, 14 = Clear, 15 = SetTransform, 16 = SetClip,
17 = BeginLayer, 18 = EndLayer, 19 = Annotation
```

**2. fromByteArray() implementation**

Reverse of toByteArray. Validate magic bytes, read version, reconstruct cullRect, read ops one by one. Return null if data is invalid.

**3. Tests**

In `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`, add:

```kotlin
@Test fun `serialize and deserialize roundtrip`() {
    val recorder = PictureRecorder()
    val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
    canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
    canvas.drawCircle(30f, 30f, 15f, Paint.fill(Color.BLUE))
    val original = recorder.finishRecordingAsPicture()

    val bytes = original.toByteArray()
    assertTrue(bytes.isNotEmpty())

    val restored = Picture.fromByteArray(bytes)
    assertNotNull(restored)
    assertEquals(original.cullRect, restored!!.cullRect)
    assertEquals(original.approximateOpCount(), restored.approximateOpCount())
}

@Test fun `fromByteArray returns null for invalid data`() {
    assertNull(Picture.fromByteArray(byteArrayOf(0, 1, 2, 3)))
}

@Test fun `fromByteArray returns null for empty data`() {
    assertNull(Picture.fromByteArray(ByteArray(0)))
}
```

### VERIFICATION

```bash
./gradlew :kanvas:test --tests "org.graphiks.kanvas.picture.PictureTest" 2>&1 | tail -15
```

Commit: `git commit -m "feat(kanvas): Picture binary serialization (toByteArray/fromByteArray)"`
```

---

## Prompt 4: Phase 4 — Geometry (Path queries, PathMeasure, PathOps, Region)

```
You are implementing the full geometry surface for Kanvas: Path introspection, PathMeasure, PathOps, and Region.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

The Path class at `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt` has all verbs and convenience methods implemented, but zero introspection methods. You need to add 9 query methods, then create 3 new files.

Read first:
- kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/FillType.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/ClipStack.kt (ClipOp usage)

### PART A: Path Introspection (modify Path.kt)

Add these methods to the Path class:

**1. isEmpty(): Boolean** — returns `verbs().isEmpty()`

**2. isRect(rect: Rect? = null): Boolean**
- Returns true if the path is equivalent to an axis-aligned rectangle (moveTo + 4 lineTo forming a closed rectangle). If rect param is non-null write the rect into it.
- Algorithm: walk verbs. Expect: moveTo, 3× lineTo, close (or 4× lineTo). Check that all edges are axis-aligned.

**3. isOval(bounds: Rect? = null): Boolean**
- Returns true if path is an oval (moveTo + 4 cubicTo forming ellipse). Write bounds if param given.
- Check: moveTo at (cx+rx, cy), then 4 cubicTo approximating ellipse (control points at ±k × radius).

**4. isRRect(rrect: RRect? = null): Boolean**
- Returns true if path is a rounded rectangle. Walk segments: lineTo for straight edges, arcTo/conic for corners.

**5. isLine(line: Line? = null): Boolean**
- True if path is a single line segment (moveTo + lineTo).

**6. isConvex(): Boolean**
- Compute cross product of consecutive edges. All must have same sign (or zero).

**7. isInterpolatable(other: Path): Boolean**
- True if both paths have same number of verbs and compatible structure.

**8. contains(point: Point): Boolean**
- Ray casting algorithm. Cast a horizontal ray from the point, count intersections with path edges. Odd = inside. Handle even-odd and winding fill types.

**9. conservativelyContainsRect(rect: Rect): Boolean**
- True if rect is fully inside the filled path. Quick check: all 4 corners must be `contains()`.

### PART B: PathMeasure (create new file)

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathMeasure.kt`:

```kotlin
package org.graphiks.kanvas.geometry

class PathMeasure(path: Path, forceClosed: Boolean = false, resScale: Float = 1f) {
    val length: Float
    val isClosed: Boolean

    fun getPosition(distance: Float, position: Point?, tangent: Point?): Boolean
    fun getSegment(startD: Float, stopD: Float, dst: Path, startWithMoveTo: Boolean): Boolean
    fun getMatrix(distance: Float, matrix: Matrix33, flags: Int): Boolean
    fun nextContour(): Boolean
}
```

Implementation:
- On construction: walk all verbs and points. Pre-compute segment lengths (line = euclidean distance, quad/cubic = approximate by 16 subdivisions). Store cumulative lengths.
- `length`: sum of all segment lengths
- `getPosition`: binary search in cumulative lengths, interpolate line/cubic at the found fraction
- `getSegment`: extract sub-path between two distances, add to dst
- `getMatrix`: position the matrix at distance (translate to position, rotate to tangent)
- `nextContour`: advance internal index to the next moveTo (multi-contour paths)

### PART C: PathOps (create new file)

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathOps.kt`:

```kotlin
package org.graphiks.kanvas.geometry

enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path?
    fun simplify(path: Path): Path?
    fun asWinding(path: Path): Path?
}
```

Start with a simplified implementation:
- `op`: for rectangles, use Region logic. For general paths, return null with a diagnostic comment.
- `simplify`: no-op for now (return copy of path)
- `asWinding`: return copy with fillType set to WINDING

### PART D: Region (create new file)

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Region.kt`:

```kotlin
package org.graphiks.kanvas.geometry

enum class RegionOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }

class Region {
    constructor()
    constructor(rect: Rect)
    constructor(region: Region)

    val isEmpty: Boolean
    val isRect: Boolean
    val isComplex: Boolean
    val bounds: Rect

    fun setEmpty()
    fun setRect(rect: Rect)
    fun setRegion(region: Region)

    fun op(rect: Rect, op: RegionOp): Boolean
    fun op(region: Region, op: RegionOp): Boolean

    fun contains(x: Float, y: Float): Boolean
    fun quickReject(rect: Rect): Boolean
    fun translate(dx: Float, dy: Float)
}
```

Implementation: store a sorted list of non-overlapping rectangles (Y-sorted then X-sorted). `op()` merges/intersects/diffs two region rect lists.

### PART E: Tests

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/geometry/GeometryTest.kt`:

Test at minimum:
- `isEmpty()` on empty and non-empty paths
- `isRect()` on rect path (true) and triangle (false), with out-param writing
- `isConvex()` on square (true) and star (false)
- `contains()` with a point inside and outside a rect path
- `PathMeasure.length` on a 100px line (expect 100f)
- `PathMeasure.getPosition` at 50% of a line
- `Region.isEmpty` on empty and rect region
- `Region.op(UNION)` combining two rects
- `Region.contains()` inside/outside
- `Region.quickReject` for disjoint rects
- `PathOps.op` with two rects union → larger rect (if implemented)

### VERIFICATION

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test --tests "org.graphiks.kanvas.geometry.GeometryTest" 2>&1 | tail -15
```

Commit: `git add -A && git commit -m "feat(kanvas): Phase 4 geometry — Path queries, PathMeasure, PathOps, Region"`
```

---

## Prompt 5: Phase 5 — Effects Expansion (29 missing subtypes)

```
You are adding the 29 missing effect subtypes to Kanvas to complete the sealed interface hierarchies.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

Read the current files first to see existing patterns:
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MaskFilter.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/PathEffect.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ImageFilter.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/BlendMode.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Paint.kt (for existing styles)

All subtypes are `data class` or `data object` inside `sealed interface { }`. Follow the exact same patterns.

### PART A: Shader.kt — add 4 subtypes + 1 enum + gradient param

**New subtypes** (add to sealed interface):
```kotlin
data class PerlinNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
data class FractalNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
data class WithWorkingColorSpace(val shader: Shader, val interpolation: ColorSpaceInterpolation) : Shader
data class CoordClamp(val shader: Shader, val subset: Rect) : Shader
```

**New enum** (add to Shader.kt, before the sealed interface):
```kotlin
enum class ColorSpaceInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }
```

**Gradient parameter**: Add `val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB` to all 4 gradient subtypes: LinearGradient, RadialGradient, SweepGradient, ConicalGradient. Put it as the LAST parameter (before the closing paren).

### PART B: ColorFilter.kt — add 5 subtypes

```kotlin
data class HSLAMatrix(val values: FloatArray) : ColorFilter  // 20 floats, operates in HSLA space
data class Lerp(val t: Float, val dst: ColorFilter, val src: ColorFilter) : ColorFilter
data object HighContrast : ColorFilter
data object Luma : ColorFilter
data object Overdraw : ColorFilter
```

### PART C: MaskFilter.kt — add 2 subtypes

```kotlin
data class Shader(val shader: Shader) : MaskFilter   // shader-based alpha mask
data class Table(val table: UByteArray) : MaskFilter  // 256-entry lookup table
```

### PART D: PathEffect.kt — add 3 subtypes + 1 enum

**New enum** (add before sealed interface):
```kotlin
enum class Path1DStyle { TRANSLATE, ROTATE, MORPH }
```

**New subtypes**:
```kotlin
data class Path1D(val path: Path, val advance: Float, val phase: Float, val style: Path1DStyle) : PathEffect
data class Path2D(val matrix: Matrix33, val path: Path) : PathEffect
data class Trim(val start: Float, val stop: Float) : PathEffect  // 0.0 to 1.0 fraction of path
```

### PART E: ImageFilter.kt — add 15 subtypes + 1 enum

**New enum** (add before sealed interface):
```kotlin
enum class ColorChannel { R, G, B, A }
```

**Morphology (2):**
```kotlin
data class Dilate(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter
data class Erode(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter
```

**Lighting — Diffuse (3) and Specular (3):**
```kotlin
data class DistantLitDiffuse(val direction: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
data class PointLitDiffuse(val location: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
data class SpotLitDiffuse(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
data class DistantLitSpecular(val direction: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
data class PointLitSpecular(val location: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
data class SpotLitSpecular(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
```

**Transform/Compositing (3):**
```kotlin
data class Offset(val dx: Float, val dy: Float, val input: ImageFilter?) : ImageFilter
data class Tile(val src: Rect, val dst: Rect, val input: ImageFilter?) : ImageFilter
data class Merge(val inputs: List<ImageFilter>) : ImageFilter
```

**Advanced (3):**
```kotlin
data class DisplacementMap(val xChannelSelector: ColorChannel, val yChannelSelector: ColorChannel, val scale: Float, val displacement: ImageFilter, val input: ImageFilter?) : ImageFilter
data class Magnifier(val src: Rect, val zoom: Float, val inset: Float, val input: ImageFilter?) : ImageFilter
data class MatrixConvolution(val kernelSize: Size, val kernel: FloatArray, val gain: Float, val bias: Float, val kernelOffset: Point, val tileMode: TileMode, val convolveAlpha: Boolean, val input: ImageFilter?) : ImageFilter
```

### PART F: Update GPURenderer.kt

In `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`, the exhaustive `when` on these sealed types may need new branches. If any compilation errors appear, add `diagnostics.degrade("unsupported effect", op, "not_yet_implemented")` for each new subtype.

### PART G: Tests

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/paint/EffectsExpansionTest.kt`:

Test that each new subtype can be instantiated:
```kotlin
@Test fun `PerlinNoise constructs`() {
    val s = Shader.PerlinNoise(0f, 0f, 3, 42, null)
    assertTrue(s is Shader)
}
@Test fun `FractalNoise constructs`() { ... }
@Test fun `HSLAMatrix constructs`() { ... }
// ... one test per new subtype
```

Also test the new enums:
```kotlin
@Test fun `ColorSpaceInterpolation has 5 values`() { assertEquals(5, ColorSpaceInterpolation.entries.size) }
@Test fun `Path1DStyle has 3 values`() { assertEquals(3, Path1DStyle.entries.size) }
@Test fun `ColorChannel has 4 values`() { assertEquals(4, ColorChannel.entries.size) }
```

### VERIFICATION

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test --tests "org.graphiks.kanvas.paint.EffectsExpansionTest" 2>&1 | tail -15
```

Commit: `git add -A && git commit -m "feat(kanvas): Phase 5 effects — 29 missing sealed subtypes + 4 enums"`
```

---

## Prompt 6: Phase 6 — Surface + Image Integration

```
You are implementing the remaining Surface, Image, and RenderResult features for Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

Read these files first:
- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderResult.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt

### TASK 1: Surface.readPixels

Add to `Surface.kt`:

```kotlin
/**
 * Copy rendered pixels from a rectangular region into [dstBuffer].
 * Calls [render] if needed, then copies the pixel region.
 *
 * @param src the source rectangle in surface coordinates
 * @param dstBuffer pre-allocated buffer of size (src.width * src.height * 4)
 * @return true on success, false if the region is out of bounds
 */
fun readPixels(src: Rect, dstBuffer: UByteArray): Boolean {
    val result = render()
    val sx = src.left.toInt().coerceIn(0, width)
    val sy = src.top.toInt().coerceIn(0, height)
    val sw = src.width.toInt().coerceAtMost(width - sx)
    val sh = src.height.toInt().coerceAtMost(height - sy)
    if (sw <= 0 || sh <= 0) return false
    val stride = 4
    val expectedSize = sw * sh * stride
    if (dstBuffer.size < expectedSize) return false
    for (row in 0 until sh) {
        val srcOffset = ((sy + row) * width + sx) * stride
        val dstOffset = row * sw * stride
        result.pixels.copyInto(dstBuffer, dstOffset, srcOffset, srcOffset + sw * stride)
    }
    return true
}
```

### TASK 2: RenderResult.toJpeg / toWebP

In `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt`, add after the `toPng()` extension:

```kotlin
/** Encode this render result as JPEG with the given quality (0-100). */
fun RenderResult.toJpeg(quality: Int = 92): ByteArray {
    val encoder = ImageEncoderRegistry.find("jpeg")
        ?: throw IllegalStateException("Add :codec:jpeg to your dependencies to enable JPEG export")
    return encoder.encode(pixels, width, height, ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, colorSpace), mapOf("quality" to quality.toString()))
}

/** Encode this render result as WebP with the given quality (0-100). */
fun RenderResult.toWebP(quality: Int = 80): ByteArray {
    val encoder = ImageEncoderRegistry.find("webp")
        ?: throw IllegalStateException("Add :codec:webp to your dependencies to enable WebP export")
    return encoder.encode(pixels, width, height, ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, colorSpace), mapOf("quality" to quality.toString()))
}
```

### TASK 3: Image.decode real implementation

In `Image.kt`, replace the placeholder `decode()`:

```kotlin
fun decode(bytes: ByteArray, mimeType: String? = null): Image {
    // Try registered codec first
    val format = mimeType?.substringAfter("image/")?.lowercase()
        ?: detectFormatFromMagicBytes(bytes)
    if (format != null) {
        val encoder = ImageEncoderRegistry.find("decode-$format")
        if (encoder != null) {
            val metadata = ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, ColorSpace.SRGB)
            // encoder.decode returns raw RGBA bytes; we need width/height from metadata
            // For now: decode PNG specifically, other formats return placeholder
            if (format == "png") {
                return decodePng(bytes)
            }
        }
    }
    // Fallback placeholder
    return Image(0, 0, ColorType.RGBA_8888, ColorSpace.SRGB, "decode-placeholder:${bytes.size}")
}

private fun detectFormatFromMagicBytes(bytes: ByteArray): String? {
    if (bytes.size < 4) return null
    // PNG: 89 50 4E 47
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "png"
    // JPEG: FF D8 FF
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "jpeg"
    // WebP: 52 49 46 46 ... 57 45 42 50
    if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) return "webp"
    // GIF: 47 49 46 38
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return "gif"
    // BMP: 42 4D
    if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
    return null
}

// Minimal PNG decoder (reads IHDR for width/height, extracts raw RGBA)
private fun decodePng(bytes: ByteArray): Image {
    // PNG signature check
    // Read IHDR at offset 16: width(4) height(4) bitDepth(1) colorType(1)
    // For now, return placeholder with correct format tag
    return Image(0, 0, ColorType.RGBA_8888, ColorSpace.SRGB, "decode-png:${bytes.size}")
}
```

Note: Full PNG/JPEG/WebP decoding requires the `:codec:*` SPI modules. The above is a scaffolding that:
- Correctly detects image format from magic bytes
- Returns the right format tag in sourceId
- Leaves actual pixel decoding to the codec SPI (separate work)

### TASK 4: Tests

Add to `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt` (create if not exists):

```kotlin
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceTest {
    @Test
    fun `readPixels copies correct region`() {
        val surface = Surface(100, 100)
        surface.canvas { drawRect(Rect.fromLTRB(0f, 0f, 100f, 100f), Paint.fill(Color.RED)) }
        val buffer = UByteArray(10 * 10 * 4)
        val ok = surface.readPixels(Rect.fromLTRB(0f, 0f, 10f, 10f), buffer)
        assertTrue(ok)
    }

    @Test
    fun `Image decode detects PNG magic bytes`() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val img = Image.decode(pngHeader)
        assertTrue(img.sourceId.contains("png"))
    }

    @Test
    fun `Image decode detects JPEG magic bytes`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val img = Image.decode(jpegHeader)
        assertTrue(img.sourceId.contains("jpeg"))
    }
}
```

### VERIFICATION

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" 2>&1 | tail -15
```

Commit: `git add -A && git commit -m "feat(kanvas): Phase 6 — Surface.readPixels, toJpeg/toWebP, Image.decode magic bytes"`

Return: compilation result, test results, summary of what was implemented.
```

---

## Execution Order

Run prompts in this order for maximum parallelism:

| Wave | Prompts | Parallel? |
|------|---------|-----------|
| A | Prompt 4 (Geometry) + Prompt 5 (Effects) | ✅ Yes — different files |
| B | Prompt 1 (GPU mapper) + Prompt 2 (Canvas tests) + Prompt 3 (Picture serialization) | ✅ Yes — different files |
| C | Prompt 6 (Surface/Image) | After Wave B (needs GPU mapper complete) |

# Kanvas Full Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the complete Kanvas API surface as defined in `.upstream/specs/kanvas/`, covering all 6 implementation phases, achieving ~95% GM test coverage parity.

**Architecture:** Kanvas is a recording 2D drawing API. `Canvas` records drawing commands into a `DisplayListBuffer` of sealed `DisplayOp` variants. `Surface.render()` compiles the display list through `PipelineCompiler` to GPU `RenderPass`es. New features follow the same pattern: data types in the appropriate kanvas sub-package, `DisplayOp` variants for draw ops, GPU op mapper handling for rendering.

**Tech Stack:** Kotlin/JVM, WebGPU via `:gpu-renderer`, WGSL shaders via `:render-pipeline`, codec SPI via `:codec:api`

---

## Gap Analysis (verified 2026-07-01)

Four sub-agents checked `kanvas/src/main/kotlin/org/graphiks/kanvas/` against `.upstream/specs/kanvas/`. Here is the consolidated gap:

### Canvas (24 features, 7 implemented, 17 missing)

| Feature | Status |
|---------|--------|
| `drawRect`, `drawRRect`, `drawPath`, `drawImage`, `drawImageRect`, `drawText` | IMPLEMENTED |
| `drawOval`, `drawCircle`, `drawArc`, `drawLine`, `drawRoundRect` | IMPLEMENTED (extensions) |
| `drawDRRect`, `drawPoint`, `drawPoints`, `drawImageNine`, `drawImageLattice`, `drawPicture`, `drawVertices`, `drawAtlas` | MISSING |
| `drawColor`, `clear`, `quickReject` (x2), `isClipEmpty`, `isClipRect` | MISSING |
| `drawPatch` (extension), `drawAnnotation` (extension), `withPicture` (extension) | MISSING |
| Supporting types: `PointMode`, `Lattice`, `Vertices`, `VertexMode` | MISSING |

### Effects (86 features, 57 implemented, 29 missing)

| Category | Missing subtypes |
|----------|-----------------|
| Shader (14 target) | PerlinNoise, FractalNoise, WithWorkingColorSpace, CoordClamp |
| ColorFilter (12 target) | HSLAMatrix, Lerp, HighContrast, Luma, Overdraw |
| MaskFilter (3 target) | Shader (shader-based mask), Table (lookup-table mask) |
| PathEffect (6 target) | Path1D, Path2D, Trim |
| ImageFilter (20 target) | Dilate, Erode, 6x Lighting (DistantLit Diffuse+Specular, PointLit Diffuse+Specular, SpotLit Diffuse+Specular), Offset, Tile, Merge, DisplacementMap, Magnifier, MatrixConvolution |

### Geometry (46 features, 14 implemented, 32 missing)

| Category | Status |
|----------|--------|
| Path verbs/convenience/transform | 14/14 IMPLEMENTED |
| Path introspection (isEmpty, isConvex, isRect, isOval, isRRect, isLine, isInterpolatable, contains, conservativelyContainsRect) | 0/9 |
| PathMeasure (class + 6 methods) | 0/6 |
| PathOps (object + 3 methods) | 0/3 |
| Region (class + 8 methods) | 0/8 |

### Surface / Image / Picture / ColorSpace (29 features, 10 implemented, 19 missing)

| Feature | Status |
|---------|--------|
| Surface core (canvas, render) | IMPLEMENTED |
| Surface.readPixels | MISSING |
| RenderResult (toPng, isClean, hasIssues, assertClean) | IMPLEMENTED |
| RenderResult.toJpeg, toWebP | MISSING |
| Image.fromPixels | IMPLEMENTED |
| Image.decode | PLACEHOLDER (returns 0x0 image) |
| Image.colorSpace field | MISSING |
| Picture, PictureRecorder, Canvas.drawPicture, playback, serialization | ENTIRE CLASS MISSING |
| ColorSpace type in kanvas module | MISSING |

---

## Phase 1: Foundation Types + Simple Canvas Methods (0→~30%)

**Independent of other phases. No dependencies.**

### Task 1.1: Supporting types — PointMode, Vertices/VertexMode, Lattice, ColorSpace

**Files:** Create `kanvas/src/main/kotlin/org/graphiks/kanvas/types/PointMode.kt`, `Vertices.kt`, `Lattice.kt`, `ColorSpace.kt` + corresponding test files.

- [ ] **1.1.1 Create PointMode**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/types/PointMode.kt
package org.graphiks.kanvas.types
enum class PointMode { POINTS, LINES, POLYGON }
```

- [ ] **1.1.2 Create Vertices and VertexMode**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/types/Vertices.kt
package org.graphiks.kanvas.types
data class Vertices(
    val mode: VertexMode,
    val positions: List<Point>,
    val texCoords: List<Point>? = null,
    val colors: List<Color>? = null,
    val indices: List<Int>? = null,
)
enum class VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
```

- [ ] **1.1.3 Create Lattice and LatticeFlags**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/types/Lattice.kt
package org.graphiks.kanvas.types
data class Lattice(
    val xDivs: List<Int>,
    val yDivs: List<Int>,
    val rects: List<Rect>? = null,
    val colors: List<Color>? = null,
    val flags: List<LatticeFlags>? = null,
)
enum class LatticeFlags { DEFAULT, TRANSPARENT }
```

- [ ] **1.1.4 Create ColorSpace, TransferFunction, Gamut**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/types/ColorSpace.kt
package org.graphiks.kanvas.types
data class ColorSpace(val name: String, val transferFunction: TransferFunction, val gamut: Gamut) {
    companion object {
        val SRGB = ColorSpace("sRGB", TransferFunction.SRGB, Gamut.SRGB)
        val DISPLAY_P3 = ColorSpace("Display P3", TransferFunction.SRGB, Gamut.DISPLAY_P3)
        val LINEAR_SRGB = ColorSpace("Linear sRGB", TransferFunction.LINEAR, Gamut.SRGB)
    }
}
enum class TransferFunction { SRGB, LINEAR, PQ, HLG }
enum class Gamut { SRGB, DISPLAY_P3, REC2020 }
```

- [ ] **1.1.5 Add ColorSpace field to Image**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`, add `val colorSpace: ColorSpace = ColorSpace.SRGB` to the Image data class.

- [ ] **1.1.6 Write tests** — verify all 4 types + their enum values. `./gradlew :kanvas:test --tests "org.graphiks.kanvas.types.*"` → PASS

- [ ] **Commit:** `git commit -m "feat(kanvas): add PointMode, Vertices, Lattice, ColorSpace types"`

### Task 1.2: drawColor, clear — full-canvas fill

**Files:** Modify `Canvas.kt`, `DisplayOp.kt`; create `DrawColorTest.kt`.

- [ ] **1.2.1 Add DisplayOp variants**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOp.kt`, add to sealed interface:
```kotlin
data class DrawColor(val color: Color, val mode: BlendMode, val transform: Matrix33, val clip: ClipStack) : DisplayOp
data class Clear(val color: Color) : DisplayOp
```

- [ ] **1.2.2 Add Canvas methods**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`:
```kotlin
fun drawColor(color: Color, mode: BlendMode = BlendMode.SRC_OVER) {
    buffer.append(DisplayOp.DrawColor(color, mode, currentTransform, currentClip))
}
fun clear(color: Color) {
    buffer.append(DisplayOp.Clear(color))
}
```

- [ ] **1.2.3 Add GPU op mapper dispatch for DrawColor/Clear**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`:
- `DrawColor`: emit a full-surface rectangle fill with the color and blend mode
- `Clear`: emit a full-surface rectangle fill ignoring blend mode (direct overwrite)

- [ ] **1.2.4 Write tests** — verify DrawColor and Clear ops are emitted to the buffer

- [ ] **Commit:** `git commit -m "feat(kanvas): add drawColor and clear canvas methods"`

### Task 1.3: drawPoint, drawPoints

**Files:** Modify `Canvas.kt`, `DisplayOp.kt`; create `DrawPointTest.kt`.

- [ ] **1.3.1 Add DisplayOp variants**

```kotlin
data class DrawPoint(val x: Float, val y: Float, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
data class DrawPoints(val mode: PointMode, val points: List<Point>, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
```

- [ ] **1.3.2 Add Canvas methods**

```kotlin
fun drawPoint(x: Float, y: Float, paint: Paint) {
    buffer.append(DisplayOp.DrawPoint(x, y, paint, currentTransform, currentClip))
}
fun drawPoints(mode: PointMode, points: List<Point>, paint: Paint) {
    buffer.append(DisplayOp.DrawPoints(mode, points, paint, currentTransform, currentClip))
}
```

- [ ] **1.3.3 GPU op mapper**: Convert DrawPoint → tiny rect/path; DrawPoints → path with moveTo/lineTo based on PointMode (POINTS=individual dots, LINES=pairs, POLYGON=closed polygon)

- [ ] **1.3.4 Write tests, commit**

### Task 1.4: quickReject, isClipEmpty, isClipRect

**Files:** Modify `Canvas.kt`, `ClipStack.kt`; create `CullingTest.kt`.

- [ ] **1.4.1 Add ClipStack.isEmpty and isRect properties**

```kotlin
// In ClipStack.kt sealed interface:
val isEmpty: Boolean get() = when (this) {
    WideOpen -> false
    is DeviceRect -> rect.isEmpty
    is Complex -> false
}
val isRect: Boolean get() = this is DeviceRect
```

- [ ] **1.4.2 Add Canvas culling methods**

```kotlin
fun quickReject(rect: Rect): Boolean {
    if (currentClip is ClipStack.WideOpen) return false
    if (currentClip is ClipStack.DeviceRect) {
        val c = (currentClip as ClipStack.DeviceRect).rect
        return rect.right <= c.left || rect.left >= c.right || rect.bottom <= c.top || rect.top >= c.bottom
    }
    return false
}
fun quickReject(path: Path): Boolean { /* same as rect using path bounds */ }
val isClipEmpty: Boolean get() = currentClip.isEmpty
val isClipRect: Boolean get() = currentClip.isRect
```

- [ ] **1.4.3 Write tests, commit**

### Task 1.5: drawPatch, drawAnnotation (extensions)

**Files:** Modify `CanvasExtensions.kt`, `DisplayOp.kt`.

- [ ] **1.5.1 Add DisplayOp.Annotation and Canvas.drawAnnotation**

```kotlin
data class Annotation(val rect: Rect, val key: String, val value: String) : DisplayOp
// In Canvas.kt:
fun drawAnnotation(rect: Rect, key: String, value: String) {
    buffer.append(DisplayOp.Annotation(rect, key, value))
}
```

- [ ] **1.5.2 Add Canvas.drawPatch via CanvasExtensions.kt**

```kotlin
fun Canvas.drawPatch(
    cubics: List<Point>,      // 12 points (4 cubic curves)
    colors: List<Color>? = null,
    texCoords: List<Point>? = null,
    paint: Paint,
) {
    // Decompose patch into drawVertices call using the Vertices type from Task 1.1
    // Coons patch → triangle mesh tessellation
    drawVertices(tessellatePatch(cubics, colors, texCoords), paint)
}
```

- [ ] **1.5.3 Write tests, commit**

---

## Phase 2: Complex Canvas Draws (30%→~50%)

**Depends on: Phase 1 (PointMode, Vertices, Lattice types)**

### Task 2.1: drawDRRect

**Files:** Modify `Canvas.kt`, `DisplayOp.kt`.

- [ ] **2.1.1 Add DisplayOp.DrawDRRect**

```kotlin
data class DrawDRRect(val outer: RRect, val inner: RRect, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
```

- [ ] **2.1.2 Add Canvas.drawDRRect**

```kotlin
fun drawDRRect(outer: RRect, inner: RRect, paint: Paint) {
    buffer.append(DisplayOp.DrawDRRect(outer, inner, paint, currentTransform, currentClip))
}
```

- [ ] **2.1.3 GPU op mapper**: Convert DRRect to a Path with both contours (outer + reverse inner), dispatch as path draw

- [ ] **2.1.4 Write tests, commit**

### Task 2.2: drawImageNine, drawImageLattice

**Files:** Modify `Canvas.kt`, `DisplayOp.kt`.

- [ ] **2.2.1 Add DisplayOp variants**

```kotlin
data class DrawImageNine(val image: Image, val center: Rect, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
data class DrawImageLattice(val image: Image, val lattice: Lattice, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
```

- [ ] **2.2.2 Add Canvas methods**

```kotlin
fun drawImageNine(image: Image, center: Rect, dst: Rect, paint: Paint? = null) {
    buffer.append(DisplayOp.DrawImageNine(image, center, dst, paint, currentTransform, currentClip))
}
fun drawImageLattice(image: Image, lattice: Lattice, dst: Rect, paint: Paint? = null) {
    buffer.append(DisplayOp.DrawImageLattice(image, lattice, dst, paint, currentTransform, currentClip))
}
```

- [ ] **2.2.3 GPU op mapper**: Decompose 9-patch/lattice into individual rectangular drawImageRect calls for each cell

- [ ] **2.2.4 Write tests, commit**

### Task 2.3: drawVertices, drawAtlas

**Files:** Modify `Canvas.kt`, `DisplayOp.kt`.

- [ ] **2.3.1 Add DisplayOp variants**

```kotlin
data class DrawVertices(val vertices: Vertices, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
data class DrawAtlas(val atlas: Image, val transforms: List<Matrix33>, val texRects: List<Rect>, val colors: List<Color>?, val blendMode: BlendMode, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
```

- [ ] **2.3.2 Add Canvas methods** — `drawVertices(vertices, paint)`, `drawAtlas(atlas, transforms, texRects, colors?, blendMode, paint?)`

- [ ] **2.3.3 GPU op mapper**: DrawVertices dispatches as textured triangle mesh; DrawAtlas batches individual sprites via instanced draw or individual drawImageRect calls

- [ ] **2.3.4 Write tests, commit**

---

## Phase 3: Picture / PictureRecorder (50%→~60%)

**Depends on: None (uses existing Canvas + DisplayListBuffer infrastructure)**

### Task 3.1: Picture class + PictureRecorder class

**Files:** Create `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`, `PictureRecorder.kt`.

- [ ] **3.1.1 Create Picture class**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt
package org.graphiks.kanvas.picture
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.types.Rect

class Picture internal constructor(
    val cullRect: Rect,
    internal val ops: List<DisplayOp>,
) {
    val uniqueID: Int = nextId()

    fun playback(canvas: Canvas) {
        canvas.save()
        try {
            for (op in ops) {
                when (op) {
                    is DisplayOp.DrawRect -> canvas.drawRect(op.rect, op.paint)
                    is DisplayOp.DrawRRect -> canvas.drawRRect(op.rrect, op.paint)
                    is DisplayOp.DrawDRRect -> canvas.drawDRRect(op.outer, op.inner, op.paint)
                    is DisplayOp.DrawPath -> canvas.drawPath(op.path, op.paint)
                    is DisplayOp.DrawPoint -> canvas.drawPoint(op.x, op.y, op.paint)
                    is DisplayOp.DrawPoints -> canvas.drawPoints(op.mode, op.points, op.paint)
                    is DisplayOp.DrawImage -> canvas.drawImage(op.image, op.dst, op.paint)
                    is DisplayOp.DrawImageNine -> canvas.drawImageNine(op.image, op.center, op.dst, op.paint)
                    is DisplayOp.DrawImageLattice -> canvas.drawImageLattice(op.image, op.lattice, op.dst, op.paint)
                    is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
                    is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint)
                    is DisplayOp.DrawVertices -> canvas.drawVertices(op.vertices, op.paint)
                    is DisplayOp.DrawAtlas -> canvas.drawAtlas(op.atlas, op.transforms, op.texRects, op.colors, op.blendMode, op.paint)
                    is DisplayOp.DrawColor -> canvas.drawColor(op.color, op.mode)
                    is DisplayOp.Clear -> canvas.clear(op.color)
                    is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
                    is DisplayOp.SetClip -> { /* clip is baked into draw ops */ }
                    is DisplayOp.BeginLayer -> canvas.saveLayer(op.bounds, op.paint)
                    is DisplayOp.EndLayer -> canvas.restore()
                    is DisplayOp.Annotation -> {} // no-op
                }
            }
        } finally {
            canvas.restore()
        }
    }

    fun approximateOpCount(nested: Boolean = false): Int {
        if (!nested) return ops.size
        return ops.sumOf { op ->
            if (op is DisplayOp.DrawPicture) 1 + op.picture.approximateOpCount(true) else 1
        }
    }

    fun approximateBytesUsed(): Int = ops.sumOf { 128 } // conservative estimate

    fun toByteArray(): ByteArray { /* serialize DisplayOps + embedded images to binary */ }

    companion object {
        fun fromByteArray(data: ByteArray): Picture? { /* deserialize */ }
        private var globalId = 0
        private fun nextId() = synchronized(this) { ++globalId }
    }
}
```

- [ ] **3.1.2 Create PictureRecorder class**

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureRecorder.kt
package org.graphiks.kanvas.picture
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.types.Rect

class PictureRecorder {
    private var activeCanvas: Canvas? = null
    private var activeBuffer: DisplayListBuffer? = null
    private var recordingBounds: Rect? = null

    fun beginRecording(bounds: Rect): Canvas {
        check(activeCanvas == null) { "Recording already in progress" }
        val buffer = object : DisplayListBuffer {
            private val ops = mutableListOf<DisplayOp>()
            override fun append(op: DisplayOp) { ops.add(op) }
            override fun ops(): List<DisplayOp> = ops.toList()
        }
        val canvas = Canvas(buffer)
        canvas.clipRect(bounds)
        activeBuffer = buffer
        activeCanvas = canvas
        recordingBounds = bounds
        return canvas
    }

    fun finishRecordingAsPicture(): Picture {
        val buffer = activeBuffer ?: throw IllegalStateException("No recording in progress")
        val bounds = recordingBounds ?: throw IllegalStateException("No recording bounds")
        val picture = Picture(bounds, buffer.ops())
        activeCanvas = null; activeBuffer = null; recordingBounds = null
        return picture
    }
}
```

- [ ] **3.1.3 Write tests** — `beginRecording` + draw + `finishRecordingAsPicture` + `playback` roundtrip

- [ ] **Commit**

### Task 3.2: Canvas.drawPicture + DisplayOp.DrawPicture

**Files:** Modify `Canvas.kt`, `DisplayOp.kt`, `CanvasExtensions.kt`.

- [ ] **3.2.1 Add DisplayOp.DrawPicture**

```kotlin
data class DrawPicture(val picture: Picture, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
```

- [ ] **3.2.2 Add Canvas.drawPicture**

```kotlin
fun drawPicture(picture: Picture, paint: Paint? = null) {
    buffer.append(DisplayOp.DrawPicture(picture, paint, currentTransform, currentClip))
}
```

- [ ] **3.2.3 Add CanvasExtensions.withPicture**

```kotlin
fun Canvas.withPicture(bounds: Rect, paint: Paint? = null, block: Canvas.() -> Unit) {
    val recorder = PictureRecorder()
    val picCanvas = recorder.beginRecording(bounds)
    picCanvas.block()
    drawPicture(recorder.finishRecordingAsPicture(), paint)
}
```

- [ ] **3.2.4 GPU op mapper**: When encountering `DisplayOp.DrawPicture`, recursively expand `picture.ops` into the display list, applying the outer transform/clip to each nested op

- [ ] **3.2.5 Write tests, commit**

### Task 3.3: Picture serialization (toByteArray / fromByteArray)

**Files:** Modify `Picture.kt`.

- [ ] **3.3.1 Implement toByteArray()** — use kotlinx.serialization JSON for DisplayOp stream + embedded images as base64-encoded PNG. For each `DisplayOp`, serialize type discriminator + fields. For `DrawImage`/`DrawImageNine`/`DrawImageLattice`, embed the Image's `pixels` as PNG binary.

- [ ] **3.3.2 Implement fromByteArray()** — parse the binary format, reconstruct DisplayOp list, reconstruct Images by decoding embedded PNGs

- [ ] **3.3.3 Write roundtrip test**: Create Picture, serialize, deserialize, verify `approximateOpCount` matches and `playback` produces same display list

- [ ] **Commit**

---

## Phase 4: Geometry — Path Introspection, PathMeasure, PathOps, Region (60%→~75%)

**Depends on: Phase 1 (nothing else, works on Path internals)**

### Task 4.1: Path introspection queries

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt`.

Add the following methods to `Path`:

- [ ] **4.1.1 `isEmpty()`** — returns `verbs().isEmpty()`

- [ ] **4.1.2 `isRect(Rect?)`** — checks if the path is equivalent to an axis-aligned rectangle (1 moveTo, 4 lineTo/hline/vline, close). If rect param is non-null, writes the rect into it. Returns Boolean.

- [ ] **4.1.3 `isOval(Rect?)`** — checks for oval shape (moveTo + 4 cubicTo forming ellipse)

- [ ] **4.1.4 `isRRect(RRect?)`** — checks for rounded-rect shape (lineTo/arcTo combinations)

- [ ] **4.1.5 `isLine(Line?)`** — checks for single line segment (moveTo + lineTo)

- [ ] **4.1.6 `isConvex()`** — checks if the path vertices form a convex polygon (no concave angles, ccw or cw winding preserved)

- [ ] **4.1.7 `isInterpolatable(Path)`** — checks if two paths have compatible structure (same number of verbs and similar topology)

- [ ] **4.1.8 `contains(Point)`** — point-in-path test using even-odd winding

- [ ] **4.1.9 `conservativelyContainsRect(Rect)`** — true if the rect is fully inside the filled path

- [ ] **4.1.10 Write tests** for each query — `isRect` with actual rect, non-rect path; `contains` with interior/exterior points; etc.

- [ ] **Commit**

### Task 4.2: PathMeasure

**Files:** Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathMeasure.kt`.

- [ ] **4.2.1 Create PathMeasure class**

```kotlin
class PathMeasure(path: Path, forceClosed: Boolean = false, resScale: Float = 1f) {
    val length: Float get() = computeLength()
    val isClosed: Boolean get() = path.isLastContourClosed() || forceClosed
    fun getPosition(distance: Float, position: Point?, tangent: Point?): Boolean
    fun getSegment(startD: Float, stopD: Float, dst: Path, startWithMoveTo: Boolean): Boolean
    fun getMatrix(distance: Float, matrix: Matrix33, flags: Int): Boolean
    fun nextContour(): Boolean
}
```

Internally: pre-compute segment lengths by walking verbs+points. `getPosition` interpolates line segments and subdivides curves. `getSegment` extracts a sub-path between two distances. `nextContour` advances to the next moveTo-separated contour.

- [ ] **4.2.2 Write tests** — measure a known-length path (e.g., 100px line), verify getPosition at 50% returns midpoint, verify getSegment returns correct sub-path

- [ ] **Commit**

### Task 4.3: PathOps (boolean path operations)

**Files:** Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathOps.kt`.

- [ ] **4.3.1 Create PathOps object with PathOp enum**

```kotlin
enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path?
    fun simplify(path: Path): Path?
    fun asWinding(path: Path): Path?
}
```

Implementation: use the `pathops/` module from the Skia compatibility layer as reference. The core algorithm decomposes paths into edges, builds an arrangement, extracts result contours. Returns null when the operation fails to produce a valid result.

- [ ] **4.3.2 Write tests** — rectangle union → larger rect, rectangle intersect → smaller rect, circle difference → donut shape

- [ ] **Commit**

### Task 4.4: Region

**Files:** Create `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Region.kt`.

- [ ] **4.4.1 Create Region class with RegionOp enum**

```kotlin
enum class RegionOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }

class Region {
    constructor(); constructor(rect: Rect); constructor(region: Region)
    val isEmpty: Boolean; val isRect: Boolean; val isComplex: Boolean; val bounds: Rect
    fun setEmpty(); fun setRect(rect: Rect); fun setRegion(region: Region)
    fun op(rect: Rect, op: RegionOp): Boolean; fun op(region: Region, op: RegionOp): Boolean
    fun contains(x: Float, y: Float): Boolean
    fun quickReject(rect: Rect): Boolean
    fun translate(dx: Float, dy: Float)
}
```

Implementation: internally stores a list of non-overlapping rectangles. `op` performs boolean operations by iterating scanlines or using interval arithmetic.

- [ ] **4.4.2 Write tests** — rect union, rect intersect, contains point, translate

- [ ] **Commit**

---

## Phase 5: Effects Expansion (75%→~90%)

**Depends on: None (effects are self-contained sealed subtypes + GPU shader implementations)**

### Task 5.1: Missing Shader subtypes (4 subtypes)

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`.

- [ ] **5.1.1 PerlinNoise** — `data class PerlinNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader`
- [ ] **5.1.2 FractalNoise** — `data class FractalNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader`
- [ ] **5.1.3 WithWorkingColorSpace** — `data class WithWorkingColorSpace(val shader: Shader, val interpolation: ColorSpaceInterpolation) : Shader`
- [ ] **5.1.4 CoordClamp** — `data class CoordClamp(val shader: Shader, val subset: Rect) : Shader`
- [ ] **5.1.5 Add ColorSpaceInterpolation enum** — `enum class ColorSpaceInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }`

In `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, add the `interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB` parameter to all four gradient subtypes (LinearGradient, RadialGradient, SweepGradient, ConicalGradient).

- [ ] **5.1.6 Write tests** — verify all new subtypes construct correctly

- [ ] **Commit**

### Task 5.2: Missing ColorFilter subtypes (5 subtypes)

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt`.

- [ ] **5.2.1 HSLAMatrix** — `data class HSLAMatrix(val values: FloatArray) : ColorFilter` (20 floats, operates in HSLA)
- [ ] **5.2.2 Lerp** — `data class Lerp(val t: Float, val dst: ColorFilter, val src: ColorFilter) : ColorFilter`
- [ ] **5.2.3 HighContrast** — `data object HighContrast : ColorFilter`
- [ ] **5.2.4 Luma** — `data object Luma : ColorFilter`
- [ ] **5.2.5 Overdraw** — `data object Overdraw : ColorFilter`
- [ ] **5.2.6 Write tests, commit**

### Task 5.3: Missing MaskFilter subtypes (2 subtypes)

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MaskFilter.kt`.

- [ ] **5.3.1 Shader** — `data class Shader(val shader: Shader) : MaskFilter` (shader-based alpha mask)
- [ ] **5.3.2 Table** — `data class Table(val table: UByteArray) : MaskFilter` (256-entry LUT mask)
- [ ] **5.3.3 Write tests, commit**

### Task 5.4: Missing PathEffect subtypes (3 subtypes)

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/PathEffect.kt`.

- [ ] **5.4.1 Path1D** — `data class Path1D(val path: Path, val advance: Float, val phase: Float, val style: Path1DStyle) : PathEffect`
  - Add `enum class Path1DStyle { TRANSLATE, ROTATE, MORPH }`
- [ ] **5.4.2 Path2D** — `data class Path2D(val matrix: Matrix33, val path: Path) : PathEffect`
- [ ] **5.4.3 Trim** — `data class Trim(val start: Float, val stop: Float) : PathEffect`
  - Values 0.0 to 1.0 representing fraction of path length
- [ ] **5.4.4 Write tests, commit**

### Task 5.5: Missing ImageFilter subtypes (15 subtypes)

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ImageFilter.kt`.

**Morphology (2):**
- [ ] **5.5.1 Dilate** — `data class Dilate(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter`
- [ ] **5.5.2 Erode** — `data class Erode(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter`

**Lighting — Diffuse (3):**
- [ ] **5.5.3 DistantLitDiffuse** — `data class DistantLitDiffuse(val direction: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter`
- [ ] **5.5.4 PointLitDiffuse** — `data class PointLitDiffuse(val location: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter`
- [ ] **5.5.5 SpotLitDiffuse** — `data class SpotLitDiffuse(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter`

**Lighting — Specular (3):**
- [ ] **5.5.6 DistantLitSpecular**
- [ ] **5.5.7 PointLitSpecular**
- [ ] **5.5.8 SpotLitSpecular**

**Transform/Compositing (3):**
- [ ] **5.5.9 Offset** — `data class Offset(val dx: Float, val dy: Float, val input: ImageFilter?) : ImageFilter`
- [ ] **5.5.10 Tile** — `data class Tile(val src: Rect, val dst: Rect, val input: ImageFilter?) : ImageFilter`
- [ ] **5.5.11 Merge** — `data class Merge(val inputs: List<ImageFilter>) : ImageFilter`

**Advanced (3):**
- [ ] **5.5.12 DisplacementMap** — `data class DisplacementMap(val xChannelSelector: ColorChannel, val yChannelSelector: ColorChannel, val scale: Float, val displacement: ImageFilter, val input: ImageFilter?) : ImageFilter`
  - Add `enum class ColorChannel { R, G, B, A }`
- [ ] **5.5.13 Magnifier** — `data class Magnifier(val src: Rect, val zoom: Float, val inset: Float, val input: ImageFilter?) : ImageFilter`
- [ ] **5.5.14 MatrixConvolution** — `data class MatrixConvolution(val kernelSize: Size, val kernel: FloatArray, val gain: Float, val bias: Float, val kernelOffset: Point, val tileMode: TileMode, val convolveAlpha: Boolean, val input: ImageFilter?) : ImageFilter`

- [ ] **5.5.15 Write tests** — all 15 subtypes compile, form valid DAGs via `input` chain

- [ ] **Commit**

---

## Phase 6: Surface/Image Integration (90%→~95%)

**Depends on: Phase 1 (ColorSpace)**

### Task 6.1: Surface.readPixels

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`.

- [ ] **6.1.1 Add readPixels to Surface**

```kotlin
fun readPixels(src: Rect, dstBuffer: UByteArray): Boolean {
    // Renders if needed, then copies region from internal pixel buffer
    val result = render()
    val srcX = src.left.toInt().coerceIn(0, width)
    val srcY = src.top.toInt().coerceIn(0, height)
    val srcW = src.width.toInt().coerceIn(0, width - srcX)
    val srcH = src.height.toInt().coerceIn(0, height - srcY)
    val pixelStride = 4
    for (row in 0 until srcH) {
        val srcOffset = ((srcY + row) * width + srcX) * pixelStride
        val dstOffset = row * srcW * pixelStride
        result.pixels.copyInto(dstBuffer, dstOffset, srcOffset, srcOffset + srcW * pixelStride)
    }
    return true
}
```

- [ ] **6.1.2 Write tests** — render a colored rect, readPixels a sub-region, verify pixel colors match

- [ ] **Commit**

### Task 6.2: RenderResult.toJpeg, toWebP

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt`.

- [ ] **6.2.1 Add toJpeg(quality = 92)** — extension on RenderResult, delegates to ImageEncoderRegistry.find("jpeg")
- [ ] **6.2.2 Add toWebP(quality = 80)** — extension on RenderResult, delegates to ImageEncoderRegistry.find("webp")
- [ ] **6.2.3 Write tests** — verify extension functions compile and call the right format string

- [ ] **Commit**

### Task 6.3: Image.decode real implementation

**Files:** Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`.

- [ ] **6.3.1 Replace placeholder decode**

```kotlin
fun decode(bytes: ByteArray, mimeType: String? = null): Image {
    val format = mimeType?.let { detectFormatFromMimeType(it) } ?: detectFormatFromMagicBytes(bytes)
    val encoder = ImageEncoderRegistry.find(format) ?: return placeholder(bytes)
    val pixels = encoder.decode(bytes) // returns width, height, pixel bytes
    return Image(pixels.width, pixels.height, ColorType.RGBA_8888, sourceId = "decode:${bytes.hashCode()}", pixels = pixels.data)
}
```

Delegate actual decoding to `:codec:api` SPI providers. When no SPI provider registered, return the current placeholder (diagnostic: DEGRADE).

- [ ] **6.3.2 Write tests** — decode a known PNG, verify width/height match, verify pixels are non-empty

- [ ] **Commit**

---

## Cross-Cutting Work: GPU OpMapper and Rendering

Each new `DisplayOp` variant needs GPU dispatch in `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`. The mapper transforms `DisplayOp` → `NormalizedDrawCommand` for the GPU renderer. When no direct GPU path exists, commands generate diagnostic entries rather than crashing.

### OpMapper dispatch checklist per phase:

**Phase 1 additions:**
- `DrawColor` → full-surface rect fill
- `Clear` → full-surface rect fill (no blend)
- `DrawPoint` → tiny rect/path conversion
- `DrawPoints` → path conversion based on PointMode
- `Annotation` → no-op (metadata only)

**Phase 2 additions:**
- `DrawDRRect` → path conversion (outer + reverse inner contour)
- `DrawImageNine` → decompose to 9 individual `DrawImage` ops
- `DrawImageLattice` → decompose to grid of `DrawImage` ops
- `DrawVertices` → textured triangle mesh dispatch
- `DrawAtlas` → batched sprite dispatch

**Phase 3 additions:**
- `DrawPicture` → recursive expansion of `picture.ops`

**Phase 5 additions:**
- All new Shader/ColorFilter/MaskFilter/PathEffect/ImageFilter subtypes → no immediate GPU implementation required; the sealed interface types ensure they flow through the pipeline. GPU shader implementations for each come in a follow-up WGSL shader wave.

---

## Execution Strategy

### Parallelizable across phases

Phases 1, 2, and 4 can run in **parallel** — they touch different files and have no shared dependencies beyond the existing kanvas API.

Phase 5 (effects) can run in parallel with Phase 4 (geometry) — effects only touch `paint/*.kt` files, geometry only touches `geometry/*.kt`.

Phase 3 (Picture) depends on Phase 1+2 being complete (Picture.playback must know about all DisplayOp variants).

Phase 6 (Surface/Image) depends on Phase 1 (ColorSpace type) only.

### Recommended execution order

1. **Wave A** (parallel): Phase 1 + Phase 4 — 8 tasks total, 2 sub-agents
2. **Wave B** (parallel): Phase 2 + Phase 5 — 8 tasks, 2 sub-agents
3. **Wave C**: Phase 3 — 3 tasks (Picture class, drawPicture, serialization)
4. **Wave D**: Phase 6 — 3 tasks (readPixels, toJpeg/toWebP, Image.decode)

### Verification at each milestone

- **After Phase 1:** 30+ new tests pass, all DisplayOp variants compile
- **After Phase 2:** Canvas has 14 core draws, 9 extensions; all issue correct DisplayOps
- **After Phase 3:** Picture roundtrip (record → playback) produces identical display list
- **After Phase 4:** Path introspection works; PathMeasure returns correct lengths; Region ops match expected
- **After Phase 5:** All 86 effect subtypes exist; sealed interface covers the full spec
- **After Phase 6:** Surface.readPixels returns correct pixels; Image.decode loads real images

---

## Notes

- The GPU shader implementations for new effect subtypes (Phase 5) are not part of this plan — they require WGSL shader authoring which is handled in the `:render-pipeline` and `:gpu-renderer` modules as a follow-up wave
- Image codec SPI providers (PNG, JPEG, WebP decoders/encoders) live in `:codec:*` modules and are resolved via `ServiceLoader` — implementation outside the `:kanvas` module scope
- Text shaping and font management remain delegated to `:font` — no changes to `text/` package in this plan
- The `kanvas-skia` compatibility module contains reference implementations for many effects (Path1D, Trim, lighting filters) that can be consulted but should not be directly ported

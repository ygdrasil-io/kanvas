# Canvas and Drawing

Status: Draft
Date: 2026-07-01

## Purpose

Defines the `Canvas` class — the central recording API for all drawing operations, state management (save/restore), transforms, and clipping. Also defines the `DisplayOp` sealed hierarchy that forms the internal command buffer.

## Contracts

### Canvas

```kotlin
class Canvas internal constructor(private val buffer: DisplayListBuffer)
```

#### Drawing Methods (core, 14 methods)

| Method | Signature |
|--------|-----------|
| `drawRect` | `(rect: Rect, paint: Paint)` |
| `drawRRect` | `(rrect: RRect, paint: Paint)` |
| `drawDRRect` | `(outer: RRect, inner: RRect, paint: Paint)` |
| `drawPath` | `(path: Path, paint: Paint)` |
| `drawPoint` | `(x: Float, y: Float, paint: Paint)` |
| `drawPoints` | `(mode: PointMode, points: List<Point>, paint: Paint)` |
| `drawImage` | `(image: Image, dst: Rect, paint: Paint? = null)` |
| `drawImageRect` | `(image: Image, src: Rect, dst: Rect, paint: Paint? = null)` |
| `drawImageNine` | `(image: Image, center: Rect, dst: Rect, paint: Paint? = null)` |
| `drawImageLattice` | `(image: Image, lattice: Lattice, dst: Rect, paint: Paint? = null)` |
| `drawText` | `(blob: TextBlob, x: Float, y: Float, paint: Paint)` |
| `drawPicture` | `(picture: Picture, paint: Paint? = null)` |
| `drawVertices` | `(vertices: Vertices, paint: Paint)` |
| `drawAtlas` | `(atlas: Image, transforms: List<Matrix33>, texRects: List<Rect>, colors: List<Color>?, blendMode: BlendMode, paint: Paint? = null)` |

- Each draw creates a `DisplayOp` with baked-in `currentTransform` and `currentClip`
- `drawPicture` emits `DisplayOp.DrawPicture` — the GPU pipeline expands nested picture ops during rendering

#### Fill and Clear

| Method | Signature |
|--------|-----------|
| `drawColor` | `(color: Color, mode: BlendMode = BlendMode.SRC_OVER)` |
| `clear` | `(color: Color)` |

- `drawColor` fills the entire canvas with a color using the given blend mode
- `clear` fills with the color, ignoring blend mode

#### Visibility Culling

| Method | Description |
|--------|-------------|
| `quickReject(rect: Rect): Boolean` | True if `rect` is fully outside the clip |
| `quickReject(path: Path): Boolean` | True if `path` is fully outside the clip |
| `isClipEmpty: Boolean` | True if the clip region is empty |
| `isClipRect: Boolean` | True if the clip is a single axis-aligned rectangle |

#### Convenience Drawing Extensions

| Extension | Delegates to |
|-----------|-------------|
| `drawOval(rect, paint)` | `drawPath(Path.addOval(rect))` |
| `drawCircle(cx,cy,r,paint)` | `drawPath(Path.addCircle(cx,cy,r))` |
| `drawArc(rect,start,sweep,useCtr,paint)` | `drawPath(...)` |
| `drawLine(x0,y0,x1,y1,paint)` | `drawPath(Path { moveTo; lineTo })` |
| `drawRoundRect(rect,rx,ry,paint)` | `drawRRect(RRect(rect,rx))` |
| `drawImage(image,x,y,paint?)` | `drawImage(image, Rect.fromXYWH(...), paint)` |
| `withPicture(bounds, paint?, block)` | Creates a `PictureRecorder`, records `block`, calls `drawPicture` |
| `drawPatch(cubics, colors?, texCoords?, paint)` | `drawVertices(...)` — Coons patch via mesh |
| `drawAnnotation(rect, key, value)` | Emits `DisplayOp.Annotation` — metadata marker, no visual output |

#### State Management

| Method | Description |
|--------|-------------|
| `save(): Int` | Push current transform + clip onto stack |
| `saveLayer(bounds?, paint?): Int` | Push + emit `BeginLayer` DisplayOp |
| `restore()` | Pop state, emit `EndLayer` DisplayOp |
| `restoreToCount(Int)` | Pop multiple states |
| `saveCount: Int` | Current stack depth |

#### Lambda-style State Extensions

```kotlin
fun Canvas.save(block: Canvas.() -> Unit)     // save(); block(); restore()
fun Canvas.saveLayer(bounds?, paint?, block)   // saveLayer(); block(); restore()
fun Canvas.clipRect(rect, block)               // clipRect(rect); block()
fun Canvas.clipPath(path, block)               // clipPath(path); block()
fun Canvas.withTransform(block)                // save(); block(); restore()
```

#### Transforms

| Method | Description |
|--------|-------------|
| `translate(x,y)` | Concatenate translation |
| `scale(sx,sy)` | Concatenate scale |
| `rotate(degrees, px?, py?)` | Concatenate rotation (around px,py if specified) |
| `skew(sx,sy)` | Concatenate skew |
| `concat(Matrix33)` | Concatenate arbitrary matrix |
| `setMatrix(Matrix33)` | Replace transform |
| `resetMatrix()` | Reset to identity |
| `matrix: Matrix33` | Current transform |

#### Clips

| Method | Description |
|--------|-------------|
| `clipRect(rect, op?, antiAlias?)` | Set clip to `DeviceRect` |
| `clipRRect(rrect, op?, antiAlias?)` | Set clip to `Complex` with RRect op |
| `clipPath(path, op?, antiAlias?)` | Set clip to `Complex` with Path op |
| `localClipBounds: Rect` | Current clip bounds (conservative) |

### DisplayOp

```kotlin
sealed interface DisplayOp {
    // Draw ops
    data class DrawRect(val rect: Rect, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawRRect(val rrect: RRect, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawDRRect(val outer: RRect, val inner: RRect, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawPath(val path: Path, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawPoint(val x: Float, val y: Float, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawPoints(val mode: PointMode, val points: List<Point>, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawImage(val image: Image, val src: Rect, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawImageNine(val image: Image, val center: Rect, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawImageLattice(val image: Image, val lattice: Lattice, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawText(val blob: TextBlob, val x: Float, val y: Float, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawPicture(val picture: Picture, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawVertices(val vertices: Vertices, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawAtlas(val atlas: Image, val transforms: List<Matrix33>, val texRects: List<Rect>, val colors: List<Color>?, val blendMode: BlendMode, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawColor(val color: Color, val mode: BlendMode) : DisplayOp
    data class Clear(val color: Color) : DisplayOp
    // State ops
    data class SetTransform(val matrix: Matrix33) : DisplayOp
    data class SetClip(val clip: ClipStack) : DisplayOp
    data class BeginLayer(val bounds: Rect?, val paint: Paint?) : DisplayOp
    data object EndLayer : DisplayOp
    // Metadata
    data class Annotation(val rect: Rect, val key: String, val value: String) : DisplayOp
}

enum class PointMode { POINTS, LINES, POLYGON }

data class Lattice(
    val xDivs: List<Int>,
    val yDivs: List<Int>,
    val rects: List<Rect>?,    // optional per-cell texture rects
    val colors: List<Color>?,  // optional per-cell colors
    val flags: List<LatticeFlags>?,
)

data class Vertices(
    val mode: VertexMode,
    val positions: List<Point>,
    val texCoords: List<Point>?,
    val colors: List<Color>?,
    val indices: List<Int>?,
)

enum class VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
```

### DisplayListBuffer

```kotlin
interface DisplayListBuffer {
    fun append(op: DisplayOp)
    fun ops(): List<DisplayOp>
}
```

- Internal abstraction — `Surface` provides the concrete implementation
- `PictureRecorder` also provides a concrete implementation for recording pictures
- `Canvas` never references `Surface` or `PictureRecorder` directly (decoupled)

## Non-Goals

- Custom drawable abstractions — Kanvas uses `Canvas.drawPicture` for composition
- LCD sub-pixel text rendering — the pipeline uses grayscale anti-aliasing
- Picture binary serialization — handled in `10-picture-and-recording.md`

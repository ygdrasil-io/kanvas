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

#### Drawing Methods (core, 7 methods)

| Method | Signature |
|--------|-----------|
| `drawRect` | `(rect: Rect, paint: Paint)` |
| `drawRRect` | `(rrect: RRect, paint: Paint)` |
| `drawPath` | `(path: Path, paint: Paint)` |
| `drawImage` | `(image: Image, dst: Rect, paint: Paint? = null)` |
| `drawImageRect` | `(image: Image, src: Rect, dst: Rect, paint: Paint? = null)` |
| `drawText` | `(blob: TextBlob, x: Float, y: Float, paint: Paint)` |
| `drawPicture` | `(picture: Picture, paint: Paint? = null)` |

- Each draw creates a `DisplayOp` with baked-in `currentTransform` and `currentClip`
- Appended to the command buffer via `DisplayListBuffer.append()`
- `drawPicture` emits `DisplayOp.DrawPicture` — a single op referencing the pre-recorded `Picture`. The GPU pipeline expands nested picture ops during rendering.

#### Convenience Drawing Extensions

| Extension | Delegates to |
|-----------|-------------|
| `drawOval(rect, paint)` | `drawPath(Path.addOval(rect))` |
| `drawCircle(cx,cy,r,paint)` | `drawPath(Path.addCircle(cx,cy,r))` |
| `drawArc(rect,start,sweep,useCtr,paint)` | `drawPath(...)` |
| `drawLine(x0,y0,x1,y1,paint)` | `drawPath(Path { moveTo; lineTo })` |
| `drawRoundRect(rect,rx,ry,paint)` | `drawRRect(RRect(rect,rx))` |
| `drawImage(image,x,y,paint?)` | `drawImage(image, Rect.fromXYWH(...), paint)` |
| `withPicture(bounds, paint?, block)` | Creates a `PictureRecorder`, records [block], calls `drawPicture` |

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

Each transform method calls `concat()` which updates `currentTransform` and appends `DisplayOp.SetTransform`.

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
    data class DrawPath(val path: Path, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawImage(val image: Image, val src: Rect, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawText(val blob: TextBlob, val x: Float, val y: Float, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    data class DrawPicture(val picture: Picture, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp
    // State ops
    data class SetTransform(val matrix: Matrix33) : DisplayOp
    data class SetClip(val clip: ClipStack) : DisplayOp
    data class BeginLayer(val bounds: Rect?, val paint: Paint?) : DisplayOp
    data object EndLayer : DisplayOp
}
```

- 6 draw DisplayOps + new `DrawPicture` in Wave 1
- `DrawPicture` wraps a pre-recorded `Picture` — the GPU pipeline expands nested ops during rendering
- Transform and clip are **baked in** at draw time — each draw carries the full CTM and clip state
- State ops are emitted alongside draw ops in the display list
- The `PipelineCompiler` in `05-gpu-pipeline.md` interprets this stream

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

- Point rendering (`drawPoint`, `drawPoints`)
- Double rounded rectangle (`drawDRRect`)
- Nine-patch and lattice image drawing (`drawImageNine`, `drawImageLattice`)
- Full-canvas color fill or clear (`drawColor`, `clear`)
- Visibility culling queries (`quickReject`, `isClipEmpty`, `isClipRect`)
- Direct pixel readback or injection (`readPixels`, `writePixels`)
- Triangle mesh rendering (`drawVertices`)
- Sprite atlas batching (`drawAtlas`)
- Coons patch rendering (`drawPatch`)
- Annotation markers (`drawAnnotation`)
- LCD sub-pixel text rendering
- Picture binary serialization

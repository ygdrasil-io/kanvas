# Picture and Recording

Status: Draft
Date: 2026-07-01

## Purpose

Defines `Picture` — an immutable snapshot of recorded drawing commands that can be replayed on any `Canvas`. Also defines `PictureRecorder` for capturing commands into a `Picture`, and `Canvas.drawPicture()` for composing pictures.

This is the Kanvas counterpart to Skia's Picture + PictureRecorder + Canvas.drawPicture.

## Design Rationale

Kanvas already has a display list mechanism (`DisplayOp`, `DisplayListBuffer`). `Picture` is a higher-level wrapper around a frozen `List<DisplayOp>`, providing:

- **Caching**: Record once, replay many times — avoids re-executing expensive scene construction.
- **Composability**: Pictures can contain other pictures via `DisplayOp.DrawPicture`.
- **Inspection**: Access to cull rect, approximate op count, and byte usage.

Unlike Skia, Kanvas does **not** implement custom binary serialization in Wave 1. The display list is always held in memory. Serialization (`toByteArray()` / `fromByteArray()`) is deferred to a later wave (blocked by image encode/decode SPI).

## Contracts

### Picture

```kotlin
package org.graphiks.kanvas.picture

/**
 * An immutable snapshot of recorded drawing commands.
 *
 * [Picture] is created by [PictureRecorder] and can be drawn onto any [Canvas]
 * via [Canvas.drawPicture] or replayed in full via [playback].
 */
class Picture internal constructor(
    val cullRect: Rect,
    internal val ops: List<DisplayOp>,
) {
    /** Unique identifier for this picture instance. */
    val uniqueID: Int = nextId()

    /**
     * Replay this picture's drawing commands onto [canvas].
     *
     * The canvas's save/restore balance is preserved — each [playback] call
     * is wrapped in a save/restore pair. State operators (SetTransform,
     * SetClip, BeginLayer, EndLayer) are replayed in order; draw operators
     * are replayed with the canvas's current state (not the baked-in state
     * from the original recording).
     *
     * @param canvas the target canvas to replay onto
     */
    fun playback(canvas: Canvas)

    /**
     * Approximate number of display operations in this picture.
     *
     * Includes nested picture ops (if [nested] is true) — each
     * [DisplayOp.DrawPicture] is counted as 1 + the nested picture's op count.
     *
     * @param nested if true, recursively count ops in nested pictures
     */
    fun approximateOpCount(nested: Boolean = false): Int

    /**
     * Approximate memory footprint of this picture in bytes.
     *
     * Does not include the memory of referenced objects (Images, TextBlobs)
     * that are owned externally.
     */
    fun approximateBytesUsed(): Int

    // uniqueID generation: process-wide, monotonically increasing, thread-safe.
    // Implementation detail — not AtomicInteger-specific in case of KMP.
    companion object {
        private var globalId = 0
        private fun nextId(): Int = synchronized(this) { ++globalId }
    }
}
```

### PictureRecorder

```kotlin
package org.graphiks.kanvas.picture

/**
 * Records drawing commands into a [Picture].
 *
 * Usage:
 * ```kotlin
 * val recorder = PictureRecorder()
 * val canvas = recorder.beginRecording(Rect.fromLTRB(0, 0, 100, 100))
 * canvas.drawRect(..., paint)
 * val picture = recorder.finishRecordingAsPicture()
 * ```
 */
class PictureRecorder {
    private var activeCanvas: Canvas? = null
    private var activeBuffer: DisplayListBuffer? = null
    private var recordingBounds: Rect? = null

    /**
     * Begin recording drawing commands for a picture with the given [bounds].
     *
     * [bounds] serves as a cull hint — the returned canvas will clip to these
     * bounds. The picture's [cullRect] is set to [bounds].
     *
     * Only one recording session may be active at a time. Calling
     * [beginRecording] again before [finishRecordingAsPicture] is an error.
     *
     * @param bounds the conservative bounds of the picture content
     * @return a [Canvas] that records commands into this picture
     * @throws IllegalStateException if a recording is already in progress
     */
    fun beginRecording(bounds: Rect): Canvas

    /**
     * Complete the current recording and return the resulting [Picture].
     *
     * After this call, the recording state is reset and a new session can
     * be started with [beginRecording].
     *
     * @return the recorded [Picture]
     * @throws IllegalStateException if no recording is in progress
     */
    fun finishRecordingAsPicture(): Picture
}
```

### Canvas.drawPicture

Adds to `Canvas`:

```kotlin
/**
 * Draw [picture] onto this canvas with optional [paint] modulation.
 *
 * The picture is drawn at the current origin; use `canvas.translate()`
 * before calling to position it. The optional [paint] can apply alpha
 * modulation or color filtering.
 *
 * This emits a [DisplayOp.DrawPicture] into the display list.
 *
 * @param picture the pre-recorded picture to draw
 * @param paint   optional paint for alpha/color modulation
 */
fun drawPicture(picture: Picture, paint: Paint? = null) {
    buffer.append(DisplayOp.DrawPicture(picture, paint, currentTransform, currentClip))
}
```

### DisplayOp.DrawPicture

Adds to `DisplayOp`:

```kotlin
/** Draw a pre-recorded [Picture], optionally modulated by [paint]. */
data class DrawPicture(
    val picture: Picture,
    val paint: Paint?,
    val transform: Matrix33,
    val clip: ClipStack,
) : DisplayOp
```

### Canvas Extensions

Adds to `CanvasExtensions.kt`:

```kotlin
/**
 * Execute [block] on a temporary picture recorded within [bounds],
 * then draw the resulting picture onto this canvas at the current state.
 */
fun Canvas.withPicture(bounds: Rect, paint: Paint? = null, block: Canvas.() -> Unit) {
    val recorder = PictureRecorder()
    val pictureCanvas = recorder.beginRecording(bounds)
    pictureCanvas.block()
    val picture = recorder.finishRecordingAsPicture()
    drawPicture(picture, paint)
}
```

## Internal Mechanics

### Picture.playback implementation

```kotlin
fun playback(canvas: Canvas) {
    canvas.save()
    try {
        for (op in ops) {
            when (op) {
                is DisplayOp.DrawRect -> canvas.drawRect(op.rect, op.paint)
                is DisplayOp.DrawRRect -> canvas.drawRRect(op.rrect, op.paint)
                is DisplayOp.DrawPath -> canvas.drawPath(op.path, op.paint)
                is DisplayOp.DrawImage -> {
                    if (op.paint != null) canvas.drawImageRect(op.image, op.src, op.dst, op.paint)
                    else canvas.drawImage(op.image, op.dst, op.paint)
                }
                is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
                is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint)
                is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
                is DisplayOp.SetClip -> { /* clip is already baked into draw ops during recording; state ops are informational during playback */ }
                is DisplayOp.BeginLayer -> canvas.saveLayer(op.bounds, op.paint)
                is DisplayOp.EndLayer -> canvas.restore()
            }
        }
    } finally {
        canvas.restore()
    }
}
```

Key design note: When `playback()` is called, the canvas's **current** transform and clip apply to the picture as a whole (via the top-level save/restore). The individual ops within the picture are replayed as-is — their baked-in transforms represent positions relative to the picture origin, not the final canvas.

When `drawPicture()` is used (instead of `playback()`), a single `DisplayOp.DrawPicture` is emitted. The GPU pipeline is responsible for expanding the nested picture ops during rendering. This preserves the picture's identity for caching and diagnostics.

### PictureRecorder implementation

```kotlin
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
    val ops = buffer.ops()
    activeCanvas = null
    activeBuffer = null
    recordingBounds = null
    return Picture(bounds, ops)
}
```

## Non-Goals (for Wave 1)

- `Picture.serialize()` / `Picture.fromByteArray()` — blocked by image encode/decode SPI
- `Picture.makeShader()` — picture-to-shader conversion deferred
- `Picture.makePlaceholder()` — placeholder pictures deferred
- `AbortCallback` / interruptible playback — deferred
- Drawable — the Skia Drawable abstraction is deferred
- Picture GPU backend integration beyond `DisplayOp.DrawPicture` dispatch in the op mapper

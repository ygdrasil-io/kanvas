package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkScalarRoundToInt

/**
 * Phase 1 canvas: translation-only CTM and rectangular clip stack.
 * Rotation/scale and full SkMatrix arrive in later phases when needed.
 */
public open class SkCanvas(public val device: SkBitmapDevice) {

    public constructor(bitmap: SkBitmap) : this(SkBitmapDevice(bitmap))

    public val bitmap: SkBitmap get() = device.bitmap

    private data class State(var tx: SkScalar, var ty: SkScalar, var clip: SkIRect)

    private val stack: ArrayDeque<State> = ArrayDeque<State>().apply {
        addLast(State(0f, 0f, device.deviceClipBounds()))
    }

    private val top: State get() = stack.last()

    public fun save(): Int {
        val s = top
        stack.addLast(State(s.tx, s.ty, s.clip.copy()))
        return stack.size - 2
    }

    public fun restore() {
        if (stack.size > 1) stack.removeLast()
    }

    public fun translate(dx: SkScalar, dy: SkScalar) {
        val s = top
        s.tx += dx
        s.ty += dy
    }

    public fun clipRect(rect: SkRect) {
        val s = top
        val devLeft = SkScalarRoundToInt(rect.left + s.tx)
        val devTop = SkScalarRoundToInt(rect.top + s.ty)
        val devRight = SkScalarRoundToInt(rect.right + s.tx)
        val devBottom = SkScalarRoundToInt(rect.bottom + s.ty)
        s.clip = SkIRect.MakeLTRB(
            maxOf(s.clip.left, devLeft),
            maxOf(s.clip.top, devTop),
            minOf(s.clip.right, devRight),
            minOf(s.clip.bottom, devBottom),
        )
    }

    public fun drawRect(rect: SkRect, paint: SkPaint) {
        val s = top
        val devRect = SkRect.MakeLTRB(
            rect.left + s.tx, rect.top + s.ty, rect.right + s.tx, rect.bottom + s.ty
        )
        device.drawRect(devRect, s.clip, paint)
    }

    public fun drawColor(color: SkColor) {
        bitmap.eraseColor(color)
    }

    public val width: Int get() = device.width
    public val height: Int get() = device.height
}

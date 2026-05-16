package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColor
import org.skia.math.SkISize

public abstract class GM public constructor() {

    private var fBGColor: SkColor = SK_ColorWHITE

    protected abstract fun getName(): String

    protected abstract fun getISize(): SkISize

    protected abstract fun onDraw(canvas: SkCanvas?)

    protected open fun onAnimate(nanos: Double): Boolean = false

    protected open fun onOnceBeforeDraw() {}

    /**
     * Mirrors Skia's `GM::setBGColor`. Default is `SK_ColorWHITE`. Subclasses
     * call this from their constructor (typically before `onDraw` is invoked).
     */
    protected fun setBGColor(c: SkColor) { fBGColor = c }

    public fun name(): String = getName()

    public fun size(): SkISize = getISize()

    public fun bgColor(): SkColor = fBGColor

    public fun draw(canvas: SkCanvas?) {
        if (!haveCalledOnceBeforeDraw) {
            haveCalledOnceBeforeDraw = true
            onOnceBeforeDraw()
        }
        onDraw(canvas)
    }

    private var haveCalledOnceBeforeDraw: Boolean = false
}

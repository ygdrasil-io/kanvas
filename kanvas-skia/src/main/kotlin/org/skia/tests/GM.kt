package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SkISize

public abstract class GM public constructor() {

    protected abstract fun getName(): String

    protected abstract fun getISize(): SkISize

    protected abstract fun onDraw(canvas: SkCanvas?)

    protected open fun onAnimate(nanos: Double): Boolean = false

    protected open fun onOnceBeforeDraw() {}

    public fun name(): String = getName()

    public fun size(): SkISize = getISize()

    public fun draw(canvas: SkCanvas?) {
        if (!haveCalledOnceBeforeDraw) {
            haveCalledOnceBeforeDraw = true
            onOnceBeforeDraw()
        }
        onDraw(canvas)
    }

    private var haveCalledOnceBeforeDraw: Boolean = false
}

package org.skia.core

import org.skia.foundation.SkPaint
import org.graphiks.math.SkRect

/**
 * Kotlin idiom for Skia's `SkAutoCanvasRestore` — captures the
 * canvas's [SkCanvas.getSaveCount] before the block, runs [block],
 * and unconditionally restores the canvas to that depth on exit
 * (whether the block returns normally or throws).
 *
 * Mirrors upstream's
 * ```cpp
 * SkAutoCanvasRestore acr(canvas, /*doSave=*/true);
 * // … modify canvas (clip, CTM, save further) …
 * // acr's destructor pops back to the captured count.
 * ```
 *
 * Equivalent imperative pattern in Kotlin :
 * ```
 * val saveCount = canvas.getSaveCount()
 * canvas.save()
 * try { /* … */ } finally { canvas.restoreToCount(saveCount) }
 * ```
 *
 * Replaces it with :
 * ```
 * canvas.withSave {
 *     // … modify canvas …
 * }
 * ```
 *
 * @return the value returned by [block].
 */
public inline fun <R> SkCanvas.withSave(block: SkCanvas.() -> R): R {
    val saveCount = getSaveCount()
    save()
    try {
        return block()
    } finally {
        restoreToCount(saveCount)
    }
}

/**
 * Variant that does **not** call `save()` itself, only captures the
 * current depth and restores to it on exit.
 *
 * Mirrors `SkAutoCanvasRestore(canvas, /*doSave=*/false)` — useful
 * when the block performs its own `save` / `saveLayer` calls (one or
 * many) that the caller wants collapsed back atomically.
 *
 * Example :
 * ```
 * canvas.withRestore {
 *     for (mode in modes) {
 *         save()
 *         drawTile(this, mode)
 *         // forgetting `restore()` is fine — withRestore pops it
 *     }
 * }
 * ```
 *
 * @return the value returned by [block].
 */
public inline fun <R> SkCanvas.withRestore(block: SkCanvas.() -> R): R {
    val saveCount = getSaveCount()
    try {
        return block()
    } finally {
        restoreToCount(saveCount)
    }
}

/**
 * Kotlin idiom for the `saveLayer` / `restore` pair — captures the
 * depth via [SkCanvas.getSaveCount] before [SkCanvas.saveLayer], so a
 * throw inside [block] (or a forgotten `restore`) still leaves the
 * canvas at the entry depth.
 *
 * Mirrors the pattern :
 * ```
 * val saveCount = canvas.getSaveCount()
 * canvas.saveLayer(bounds, paint)
 * try { /* … */ } finally { canvas.restoreToCount(saveCount) }
 * ```
 *
 * @param bounds optional layer bounds (forwarded to [SkCanvas.saveLayer]).
 * @param paint optional layer paint (forwarded to [SkCanvas.saveLayer]).
 * @return the value returned by [block].
 */
public inline fun <R> SkCanvas.withLayer(
    bounds: SkRect? = null,
    paint: SkPaint? = null,
    block: SkCanvas.() -> R,
): R {
    val saveCount = getSaveCount()
    saveLayer(bounds, paint)
    try {
        return block()
    } finally {
        restoreToCount(saveCount)
    }
}

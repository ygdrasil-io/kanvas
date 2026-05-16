package org.skia.core

import org.graphiks.math.SkColor
import org.graphiks.math.SkIRect

/**
 * Mirrors Skia's `SkCanvas::Lattice`
 * ([include/core/SkCanvas.h](https://github.com/google/skia/blob/main/include/core/SkCanvas.h))
 * — partition of an [org.skia.foundation.SkImage] into a grid of
 * stretchable / fixed cells, used by
 * [SkCanvas.drawImageLattice].
 *
 * The image is divided into `(xDivs.size + 1) × (yDivs.size + 1)`
 * cells. Cells whose row and column indices are both **even** are
 * fixed (corner-like, non-scaling) ; every other cell stretches to
 * cover the remaining destination space — generalising
 * [SkCanvas.drawImageNine] to an N × M grid.
 *
 * **R-suivi.50 minimal port** : we surface the divisor arrays and
 * optional per-cell type / colour / source-bounds metadata so the
 * upstream API shape round-trips through the canvas layer. The
 * actual N × M tessellation in [SkCanvas.drawImageLattice] is
 * deferred to a follow-up phase ; the default implementation
 * degenerates to a plain `drawImageRect` over the full destination.
 *
 * @param xDivs sorted strictly-increasing x-coordinates dividing the
 *  source image into vertical strips. Empty == no horizontal
 *  divisions ; the lattice degenerates to a single column.
 * @param yDivs sorted strictly-increasing y-coordinates dividing the
 *  source image into horizontal strips. Empty == no vertical
 *  divisions.
 * @param rectTypes optional per-cell rendering policy. Length must
 *  equal `(xDivs.size + 1) * (yDivs.size + 1)` when supplied.
 *  `null` (default) means every cell uses [RectType.kDefault].
 * @param bounds optional sub-rectangle of the source image to draw
 *  from. `null` means the whole image.
 * @param colors optional per-cell colours, paired with
 *  [RectType.kFixedColor] entries in [rectTypes]. Same length
 *  contract as [rectTypes] when supplied.
 */
public data class SkLattice(
    val xDivs: IntArray,
    val yDivs: IntArray,
    val rectTypes: Array<RectType>? = null,
    val bounds: SkIRect? = null,
    val colors: IntArray? = null,
) {
    /** Mirrors upstream `SkCanvas::Lattice::RectType` (SkCanvas.h:1657). */
    public enum class RectType {
        /** Render the corresponding source cell normally. */
        kDefault,

        /** Skip the cell — the destination region stays untouched. */
        kTransparent,

        /** Fill the cell with the matching entry from [SkLattice.colors]. */
        kFixedColor,
    }

    // data class equals/hashCode for arrays compares references — override
    // so two lattices with the same payload compare equal (matches upstream
    // value-semantics expectations for a grid descriptor).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkLattice) return false
        if (!xDivs.contentEquals(other.xDivs)) return false
        if (!yDivs.contentEquals(other.yDivs)) return false
        if (rectTypes == null) {
            if (other.rectTypes != null) return false
        } else {
            if (other.rectTypes == null) return false
            if (!rectTypes.contentEquals(other.rectTypes)) return false
        }
        if (bounds != other.bounds) return false
        if (colors == null) {
            if (other.colors != null) return false
        } else {
            if (other.colors == null) return false
            if (!colors.contentEquals(other.colors)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = xDivs.contentHashCode()
        result = 31 * result + yDivs.contentHashCode()
        result = 31 * result + (rectTypes?.contentHashCode() ?: 0)
        result = 31 * result + (bounds?.hashCode() ?: 0)
        result = 31 * result + (colors?.contentHashCode() ?: 0)
        return result
    }
}

/** Convenience constructor — colour-typed alias for [SkLattice]'s [Int] colour entries. */
@Suppress("UnusedReceiverParameter")
public fun SkLattice.colorAt(index: Int): SkColor? = colors?.getOrNull(index)

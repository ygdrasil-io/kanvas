package org.graphiks.math

/**
 * Iso-aligned port of Skia's `SkRect` ([include/core/SkRect.h](https://github.com/google/skia/blob/main/include/core/SkRect.h)).
 *
 * Stored as `(left, top, right, bottom)`. Skia uses `fLeft / fTop /
 * fRight / fBottom` field names; the Kotlin port keeps the un-prefixed
 * Kotlin-idiomatic names so existing callers compile unchanged. Both
 * Skia function-style accessors (`x()`, `y()`, `width()`, `height()`,
 * etc.) and direct property access (`left`, `top`, …) are supported.
 *
 * **NaN caveat:** `data class equals` is NaN-friendly (`NaN.equals(NaN) == true`)
 * because Kotlin uses `Float.equals` semantics. Skia's `operator==`
 * uses raw IEEE compare where `NaN == NaN` is `false`. The mismatch is
 * intentional — it lets `data class` give `copy()` for free without
 * subtle hash/equals breakage. Use [equalsLTRB] for raw IEEE compare
 * when porting hot-path C++ that relies on the NaN-asymmetric semantic.
 */
public data class SkRect(
    var left: SkScalar,
    var top: SkScalar,
    var right: SkScalar,
    var bottom: SkScalar,
) {
    // ─── Accessors (function-style, mirror Skia) ────────────────────────

    public fun x(): SkScalar = left
    public fun y(): SkScalar = top
    public fun left(): SkScalar = left
    public fun top(): SkScalar = top
    public fun right(): SkScalar = right
    public fun bottom(): SkScalar = bottom

    /** `right - left`. Not sort-checked; result may be negative or non-finite. */
    public fun width(): SkScalar = right - left

    /** `bottom - top`. Not sort-checked; result may be negative or non-finite. */
    public fun height(): SkScalar = bottom - top

    /** Average of `left` and `right`, computed in double precision (matches Skia's `sk_float_midpoint`). */
    public fun centerX(): SkScalar = (0.5 * (left.toDouble() + right)).toFloat()

    /** Average of `top` and `bottom`, computed in double precision. */
    public fun centerY(): SkScalar = (0.5 * (top.toDouble() + bottom)).toFloat()

    public fun center(): SkPoint = SkPoint(centerX(), centerY())

    public fun topLeft(): SkPoint = SkPoint(left, top)

    public fun TL(): SkPoint = SkPoint(left, top)
    public fun TR(): SkPoint = SkPoint(right, top)
    public fun BL(): SkPoint = SkPoint(left, bottom)
    public fun BR(): SkPoint = SkPoint(right, bottom)

    // ─── State predicates ───────────────────────────────────────────────

    /**
     * `true` if `width() <= 0` or `height() <= 0`, **or** if any value is
     * NaN (Skia uses `!(L < R && T < B)` so NaN propagates to "empty").
     */
    public val isEmpty: Boolean get() = !(left < right && top < bottom)

    public fun isSorted(): Boolean = left <= right && top <= bottom

    public fun isFinite(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    /** Raw IEEE field-by-field equality. NaN-asymmetric, matches Skia's `operator==`. */
    public fun equalsLTRB(other: SkRect): Boolean =
        left == other.left && top == other.top && right == other.right && bottom == other.bottom

    // ─── Mutators ───────────────────────────────────────────────────────

    public fun setEmpty() { left = 0f; top = 0f; right = 0f; bottom = 0f }

    public fun setLTRB(l: SkScalar, t: SkScalar, r: SkScalar, b: SkScalar) {
        left = l; top = t; right = r; bottom = b
    }

    public fun setXYWH(x: SkScalar, y: SkScalar, w: SkScalar, h: SkScalar) {
        setLTRB(x, y, x + w, y + h)
    }

    public fun setWH(w: SkScalar, h: SkScalar) {
        setLTRB(0f, 0f, w, h)
    }

    /** Promote an integer rect into float space (large values may lose precision). */
    public fun set(src: SkIRect) {
        left = src.left.toFloat()
        top = src.top.toFloat()
        right = src.right.toFloat()
        bottom = src.bottom.toFloat()
    }

    public fun offset(dx: SkScalar, dy: SkScalar) {
        left += dx; top += dy; right += dx; bottom += dy
    }

    public fun offset(delta: SkPoint) { offset(delta.fX, delta.fY) }

    public fun offsetTo(newX: SkScalar, newY: SkScalar) {
        right += newX - left
        bottom += newY - top
        left = newX
        top = newY
    }

    public fun inset(dx: SkScalar, dy: SkScalar) {
        left += dx; top += dy; right -= dx; bottom -= dy
    }

    public fun outset(dx: SkScalar, dy: SkScalar) { inset(-dx, -dy) }

    public fun adjust(dL: SkScalar, dT: SkScalar, dR: SkScalar, dB: SkScalar) {
        left += dL; top += dT; right += dR; bottom += dB
    }

    public fun sort() {
        if (left > right) { val t = left; left = right; right = t }
        if (top > bottom) { val t = top; top = bottom; bottom = t }
    }

    public fun makeSorted(): SkRect {
        val l = minOf(left, right)
        val r = maxOf(left, right)
        val t = minOf(top, bottom)
        val b = maxOf(top, bottom)
        return SkRect(l, t, r, b)
    }

    public fun makeOffset(dx: SkScalar, dy: SkScalar): SkRect =
        SkRect(left + dx, top + dy, right + dx, bottom + dy)

    public fun makeOffset(v: SkPoint): SkRect = makeOffset(v.fX, v.fY)

    public fun makeInset(dx: SkScalar, dy: SkScalar): SkRect =
        SkRect(left + dx, top + dy, right - dx, bottom - dy)

    public fun makeOutset(dx: SkScalar, dy: SkScalar): SkRect = makeInset(-dx, -dy)

    // ─── Containment ────────────────────────────────────────────────────

    /** Half-open: `left <= x < right` and `top <= y < bottom`. */
    public fun contains(x: SkScalar, y: SkScalar): Boolean =
        x >= left && x < right && y >= top && y < bottom

    /** True if `r` is non-empty AND fully inside `this`. */
    public fun contains(r: SkRect): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    public fun contains(r: SkIRect): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left.toFloat() && top <= r.top.toFloat() &&
            right >= r.right.toFloat() && bottom >= r.bottom.toFloat()

    // ─── Intersection ───────────────────────────────────────────────────

    /**
     * Replaces this with the intersection of `this` and `r`. Returns
     * `false` (and leaves `this` unchanged) if the rects don't overlap
     * or if either is empty (NaN propagates to "no overlap" via Skia's
     * `!(L < R && T < B)` short-circuit).
     */
    public fun intersect(r: SkRect): Boolean {
        val l = maxOf(left, r.left)
        val t = maxOf(top, r.top)
        val ri = minOf(right, r.right)
        val b = minOf(bottom, r.bottom)
        if (!(l < ri && t < b)) return false
        setLTRB(l, t, ri, b)
        return true
    }

    /** True if `this` and `r` have any overlap. NaN-safe — returns `false` on NaN. */
    public fun intersects(r: SkRect): Boolean =
        Intersects(left, top, right, bottom, r.left, r.top, r.right, r.bottom)

    // ─── Union ──────────────────────────────────────────────────────────

    /** Expand `this` to include `r`. Empty `r` is a no-op. */
    public fun join(r: SkRect) {
        if (r.isEmpty) return
        if (this.isEmpty) {
            setLTRB(r.left, r.top, r.right, r.bottom)
        } else {
            joinNonEmptyArg(r)
        }
    }

    /** Like [join] but caller has already verified `r` is non-empty. */
    public fun joinNonEmptyArg(r: SkRect) {
        if (r.left < left) left = r.left
        if (r.top < top) top = r.top
        if (r.right > right) right = r.right
        if (r.bottom > bottom) bottom = r.bottom
    }

    /** Like [join] but no empty-checks: callers willing to accept that
     * an empty `r` may shrink `this`. Used by paths that build incrementally. */
    public fun joinPossiblyEmptyRect(r: SkRect) {
        left = minOf(left, r.left)
        top = minOf(top, r.top)
        right = maxOf(right, r.right)
        bottom = maxOf(bottom, r.bottom)
    }

    // ─── Rounding to SkIRect ────────────────────────────────────────────

    /** Round each component to the nearest int (half-to-even, IEEE). */
    public fun round(): SkIRect = SkIRect(
        SkScalarRoundToInt(left), SkScalarRoundToInt(top),
        SkScalarRoundToInt(right), SkScalarRoundToInt(bottom),
    )

    /** Outward round: floor(min) / ceil(max) — produces a rect that fully contains `this`. */
    public fun roundOut(): SkIRect = SkIRect(
        SkScalarFloorToInt(left), SkScalarFloorToInt(top),
        SkScalarCeilToInt(right), SkScalarCeilToInt(bottom),
    )

    /** Inward round: ceil(min) / floor(max) — produces a rect fully inside `this`. */
    public fun roundIn(): SkIRect = SkIRect(
        SkScalarCeilToInt(left), SkScalarCeilToInt(top),
        SkScalarFloorToInt(right), SkScalarFloorToInt(bottom),
    )

    public companion object {
        public fun MakeLTRB(l: SkScalar, t: SkScalar, r: SkScalar, b: SkScalar): SkRect =
            SkRect(l, t, r, b)

        public fun MakeXYWH(x: SkScalar, y: SkScalar, w: SkScalar, h: SkScalar): SkRect =
            SkRect(x, y, x + w, y + h)

        public fun MakeWH(w: SkScalar, h: SkScalar): SkRect = SkRect(0f, 0f, w, h)

        public fun MakeIWH(w: Int, h: Int): SkRect = SkRect(0f, 0f, w.toFloat(), h.toFloat())

        public fun MakeEmpty(): SkRect = SkRect(0f, 0f, 0f, 0f)

        public fun Make(size: SkISize): SkRect =
            SkRect(0f, 0f, size.width.toFloat(), size.height.toFloat())

        public fun Make(irect: SkIRect): SkRect = SkRect(
            irect.left.toFloat(), irect.top.toFloat(),
            irect.right.toFloat(), irect.bottom.toFloat(),
        )

        /**
         * Static intersection check: do `[al,ar]×[at,ab]` and `[bl,br]×[bt,bb]`
         * overlap? Uses Skia's `!(L < R && T < B)` so NaN propagates to false.
         */
        public fun Intersects(
            al: SkScalar, at: SkScalar, ar: SkScalar, ab: SkScalar,
            bl: SkScalar, bt: SkScalar, br: SkScalar, bb: SkScalar,
        ): Boolean {
            val l = maxOf(al, bl)
            val r = minOf(ar, br)
            val t = maxOf(at, bt)
            val b = minOf(ab, bb)
            return l < r && t < b
        }

        public fun Intersects(a: SkRect, b: SkRect): Boolean =
            Intersects(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom)

        /**
         * Tight bounding box of `points`. Returns `null` if any point is
         * non-finite (NaN/Inf) — matches Skia's `SkRect::Bounds` which
         * detects non-finiteness via the `nx *= p.fX; nx == 0` trick.
         */
        public fun Bounds(points: Array<SkPoint>): SkRect? {
            if (points.isEmpty()) return MakeEmpty()
            var l = points[0].fX; var r = points[0].fX
            var t = points[0].fY; var b = points[0].fY
            var nx = 0f; var ny = 0f
            for (p in points) {
                if (p.fX < l) l = p.fX
                if (p.fX > r) r = p.fX
                if (p.fY < t) t = p.fY
                if (p.fY > b) b = p.fY
                nx *= p.fX
                ny *= p.fY
            }
            return if (nx == 0f && ny == 0f) SkRect(l, t, r, b) else null
        }
    }
}

/**
 * Iso-aligned port of Skia's `SkIRect`. Mutable to mirror the C++ struct.
 * `width()` / `height()` use `Sk32_can_overflow_sub` semantics — they
 * wrap on Int overflow, matching Skia (use [width64] / [height64] for
 * the safe 64-bit form).
 *
 * **Saturating mutators:** `setXYWH`, `offset`, `offsetTo`, `inset`, `outset`,
 * `adjust`, `makeOffset`, `makeInset`, `makeOutset` and the companion
 * `MakeXYWH` use `Sk32_sat_add` / `Sk32_sat_sub` semantics
 * (via [SkIPoint.sk32SatAdd] / [SkIPoint.sk32SatSub]) — they saturate
 * at `Int.MIN_VALUE` / `Int.MAX_VALUE` instead of wrapping, matching upstream
 * Skia (`SkSafe32.h`). Wrap-around would silently corrupt clip rects.
 */
public data class SkIRect(
    var left: Int,
    var top: Int,
    var right: Int,
    var bottom: Int,
) {
    public fun x(): Int = left
    public fun y(): Int = top
    public fun left(): Int = left
    public fun top(): Int = top
    public fun right(): Int = right
    public fun bottom(): Int = bottom

    /** `right - left`, wrapping on overflow (Skia's `Sk32_can_overflow_sub`). */
    public fun width(): Int = right - left
    public fun height(): Int = bottom - top

    /** 64-bit width; can't overflow. */
    public fun width64(): Long = right.toLong() - left.toLong()
    public fun height64(): Long = bottom.toLong() - top.toLong()

    public fun topLeft(): SkIPoint = SkIPoint(left, top)

    /**
     * Returns `true` when `width()` or `height()` are zero or negative, **or**
     * when their 64-bit values do not fit in int32 (mirrors upstream
     * `SkIRect::isEmpty()` — `include/core/SkRect.h`). The overflow check is
     * load-bearing: a rect like `(Int.MIN_VALUE, 0, Int.MAX_VALUE, 1)` has
     * `width64() > Int.MAX_VALUE`, so upstream treats it as empty even though
     * the 64-bit difference is positive.
     */
    public val isEmpty: Boolean get() {
        val w = width64()
        val h = height64()
        if (w <= 0L || h <= 0L) return true
        // Return true if either exceeds int32 (upstream: `!SkTFitsIn<int32_t>(w | h)`).
        return w > Int.MAX_VALUE.toLong() || h > Int.MAX_VALUE.toLong()
    }

    /**
     * Returns `true` when `width64()` or `height64()` are zero or negative.
     * Unlike [isEmpty], this variant does **not** treat an int32 overflow as
     * empty (mirrors upstream `SkIRect::isEmpty64()`).
     */
    public fun isEmpty64(): Boolean = right <= left || bottom <= top

    public fun isSorted(): Boolean = left <= right && top <= bottom

    public fun setEmpty() { left = 0; top = 0; right = 0; bottom = 0 }

    public fun setLTRB(l: Int, t: Int, r: Int, b: Int) {
        left = l; top = t; right = r; bottom = b
    }

    public fun setXYWH(x: Int, y: Int, w: Int, h: Int) {
        setLTRB(x, y, SkIPoint.sk32SatAdd(x, w), SkIPoint.sk32SatAdd(y, h))
    }

    public fun setWH(w: Int, h: Int) { setLTRB(0, 0, w, h) }

    public fun offset(dx: Int, dy: Int) {
        left = SkIPoint.sk32SatAdd(left, dx)
        top = SkIPoint.sk32SatAdd(top, dy)
        right = SkIPoint.sk32SatAdd(right, dx)
        bottom = SkIPoint.sk32SatAdd(bottom, dy)
    }

    public fun offset(delta: SkIPoint) { offset(delta.fX, delta.fY) }

    public fun offsetTo(newX: Int, newY: Int) {
        // Upstream: fRight = Sk64_pin_to_s32((int64_t)fRight + newX - fLeft).
        right = SkIPoint.sk64PinToS32(right.toLong() + newX.toLong() - left.toLong())
        bottom = SkIPoint.sk64PinToS32(bottom.toLong() + newY.toLong() - top.toLong())
        left = newX
        top = newY
    }

    public fun inset(dx: Int, dy: Int) {
        left = SkIPoint.sk32SatAdd(left, dx)
        top = SkIPoint.sk32SatAdd(top, dy)
        right = SkIPoint.sk32SatSub(right, dx)
        bottom = SkIPoint.sk32SatSub(bottom, dy)
    }

    public fun outset(dx: Int, dy: Int) { inset(-dx, -dy) }

    public fun adjust(dL: Int, dT: Int, dR: Int, dB: Int) {
        left = SkIPoint.sk32SatAdd(left, dL)
        top = SkIPoint.sk32SatAdd(top, dT)
        right = SkIPoint.sk32SatAdd(right, dR)
        bottom = SkIPoint.sk32SatAdd(bottom, dB)
    }

    public fun sort() {
        if (left > right) { val t = left; left = right; right = t }
        if (top > bottom) { val t = top; top = bottom; bottom = t }
    }

    public fun makeSorted(): SkIRect = SkIRect(
        minOf(left, right), minOf(top, bottom),
        maxOf(left, right), maxOf(top, bottom),
    )

    public fun makeOffset(dx: Int, dy: Int): SkIRect = SkIRect(
        SkIPoint.sk32SatAdd(left, dx),
        SkIPoint.sk32SatAdd(top, dy),
        SkIPoint.sk32SatAdd(right, dx),
        SkIPoint.sk32SatAdd(bottom, dy),
    )

    public fun makeInset(dx: Int, dy: Int): SkIRect = SkIRect(
        SkIPoint.sk32SatAdd(left, dx),
        SkIPoint.sk32SatAdd(top, dy),
        SkIPoint.sk32SatSub(right, dx),
        SkIPoint.sk32SatSub(bottom, dy),
    )

    public fun makeOutset(dx: Int, dy: Int): SkIRect = makeInset(-dx, -dy)

    /** Half-open containment: `left <= x < right` and `top <= y < bottom`. */
    public fun contains(x: Int, y: Int): Boolean =
        x >= left && x < right && y >= top && y < bottom

    public fun contains(r: SkIRect): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    /** Like `contains(r)` but skips empty checks (caller guarantees non-empty). */
    public fun containsNoEmptyCheck(r: SkIRect): Boolean =
        left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    public fun intersect(r: SkIRect): Boolean {
        val l = maxOf(left, r.left)
        val t = maxOf(top, r.top)
        val ri = minOf(right, r.right)
        val b = minOf(bottom, r.bottom)
        if (l >= ri || t >= b) return false
        setLTRB(l, t, ri, b)
        return true
    }

    /** Expand `this` to include `r`. Empty `r` is a no-op; empty `this` becomes `r`. */
    public fun join(r: SkIRect) {
        if (r.left >= r.right || r.top >= r.bottom) return
        if (left >= right || top >= bottom) {
            setLTRB(r.left, r.top, r.right, r.bottom)
        } else {
            if (r.left < left) left = r.left
            if (r.top < top) top = r.top
            if (r.right > right) right = r.right
            if (r.bottom > bottom) bottom = r.bottom
        }
    }

    public companion object {
        public fun MakeLTRB(l: Int, t: Int, r: Int, b: Int): SkIRect = SkIRect(l, t, r, b)
        public fun MakeXYWH(x: Int, y: Int, w: Int, h: Int): SkIRect =
            SkIRect(x, y, SkIPoint.sk32SatAdd(x, w), SkIPoint.sk32SatAdd(y, h))
        public fun MakeWH(w: Int, h: Int): SkIRect = SkIRect(0, 0, w, h)
        public fun MakeEmpty(): SkIRect = SkIRect(0, 0, 0, 0)
        public fun MakeSize(size: SkISize): SkIRect = SkIRect(0, 0, size.width, size.height)
        public fun MakePtSize(pt: SkIPoint, size: SkISize): SkIRect =
            SkIRect(pt.fX, pt.fY, pt.fX + size.width, pt.fY + size.height)

        public fun Intersects(a: SkIRect, b: SkIRect): Boolean {
            val l = maxOf(a.left, b.left)
            val r = minOf(a.right, b.right)
            val t = maxOf(a.top, b.top)
            val bot = minOf(a.bottom, b.bottom)
            return l < r && t < bot
        }
    }
}

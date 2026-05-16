package org.skia.core

import kotlin.Boolean
import kotlin.Float
import org.skia.foundation.SkRRect
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SkRRectPriv {
 * public:
 *     static bool IsCircle(const SkRRect& rr) {
 *         return rr.isOval() && SkScalarNearlyEqual(rr.fRadii[0].fX, rr.fRadii[0].fY);
 *     }
 *
 *     static SkVector GetSimpleRadii(const SkRRect& rr) {
 *         SkASSERT(!rr.isComplex());
 *         return rr.fRadii[0];
 *     }
 *
 *     static bool IsSimpleCircular(const SkRRect& rr) {
 *         return rr.isSimple() && SkScalarNearlyEqual(rr.fRadii[0].fX, rr.fRadii[0].fY);
 *     }
 *
 *     // Looser version of IsSimpleCircular, where the x & y values of the radii
 *     // only have to be nearly equal instead of strictly equal.
 *     static bool IsNearlySimpleCircular(const SkRRect& rr, float tolerance = SK_ScalarNearlyZero);
 *
 *     static bool EqualRadii(const SkRRect& rr) {
 *         return rr.isRect() || SkRRectPriv::IsCircle(rr)  || SkRRectPriv::IsSimpleCircular(rr);
 *     }
 *
 *     static const SkVector* GetRadiiArray(const SkRRect& rr) { return rr.fRadii; }
 *
 *     static bool AllCornersCircular(const SkRRect& rr, float tolerance = SK_ScalarNearlyZero);
 *
 *     // Prefer this over AllCornersCircular, which compares radii by absolute difference, which is
 *     // a less stable decision as scale changes.
 *     static bool AllCornersRelativelyCircular(const SkRRect& rr,
 *                                              float tolerance = SK_ScalarNearlyZero);
 *
 *     // The same test used in AllCornersRelativelyCircular, but for provided radii.
 *     static bool IsRelativelyCircular(float rx, float ry, float tolerance = SK_ScalarNearlyZero);
 *
 *     static bool ReadFromBuffer(SkRBuffer* buffer, SkRRect* rr);
 *
 *     static void WriteToBuffer(const SkRRect& rr, SkWBuffer* buffer);
 *
 *     // Test if a point is in the rrect, if it were a closed set.
 *     static bool ContainsPoint(const SkRRect& rr, const SkPoint& p) {
 *         return rr.getBounds().contains(p.fX, p.fY) && rr.checkCornerContainment(p.fX, p.fY);
 *     }
 *
 *     // Compute an approximate largest inscribed bounding box of the rounded rect. For empty,
 *     // rect, oval, and simple types this will be the largest inscribed rectangle. Otherwise it may
 *     // not be the global maximum, but will be non-empty, touch at least one edge and be contained
 *     // in the round rect.
 *     static SkRect InnerBounds(const SkRRect& rr);
 *
 *     // Attempt to compute the intersection of two round rects. The intersection is not necessarily
 *     // a round rect. This returns intersections only when the shape is representable as a new
 *     // round rect (or rect). Empty is returned if 'a' and 'b' do not intersect or if the
 *     // intersection is too complicated. This is conservative, it may not always detect that an
 *     // intersection could be represented as a round rect. However, when it does return a round rect
 *     // that intersection will be exact (i.e. it is NOT just a subset of the actual intersection).
 *     static SkRRect ConservativeIntersect(const SkRRect& a, const SkRRect& b);
 * }
 * ```
 */
public open class SkRRectPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool IsCircle(const SkRRect& rr) {
     *         return rr.isOval() && SkScalarNearlyEqual(rr.fRadii[0].fX, rr.fRadii[0].fY);
     *     }
     * ```
     */
    public fun isCircle(rr: SkRRect): Boolean {
      TODO("Implement isCircle")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkVector GetSimpleRadii(const SkRRect& rr) {
     *         SkASSERT(!rr.isComplex());
     *         return rr.fRadii[0];
     *     }
     * ```
     */
    public fun getSimpleRadii(rr: SkRRect): SkVector {
      TODO("Implement getSimpleRadii")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsSimpleCircular(const SkRRect& rr) {
     *         return rr.isSimple() && SkScalarNearlyEqual(rr.fRadii[0].fX, rr.fRadii[0].fY);
     *     }
     * ```
     */
    public fun isSimpleCircular(rr: SkRRect): Boolean {
      TODO("Implement isSimpleCircular")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRRectPriv::IsNearlySimpleCircular(const SkRRect& rr, float tolerance) {
     *     const float simpleRadius = rr.fRadii[0].fX;
     *     return SkScalarNearlyEqual(simpleRadius, rr.fRadii[0].fY, tolerance) &&
     *            SkScalarNearlyEqual(simpleRadius, rr.fRadii[1].fX, tolerance) &&
     *            SkScalarNearlyEqual(simpleRadius, rr.fRadii[1].fY, tolerance) &&
     *            SkScalarNearlyEqual(simpleRadius, rr.fRadii[2].fX, tolerance) &&
     *            SkScalarNearlyEqual(simpleRadius, rr.fRadii[2].fY, tolerance) &&
     *            SkScalarNearlyEqual(simpleRadius, rr.fRadii[3].fX, tolerance) &&
     *            SkScalarNearlyEqual(simpleRadius, rr.fRadii[3].fY, tolerance);
     * }
     * ```
     */
    public fun isNearlySimpleCircular(rr: SkRRect, tolerance: Float = TODO()): Boolean {
      TODO("Implement isNearlySimpleCircular")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool EqualRadii(const SkRRect& rr) {
     *         return rr.isRect() || SkRRectPriv::IsCircle(rr)  || SkRRectPriv::IsSimpleCircular(rr);
     *     }
     * ```
     */
    public fun equalRadii(rr: SkRRect): Boolean {
      TODO("Implement equalRadii")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkVector* GetRadiiArray(const SkRRect& rr) { return rr.fRadii; }
     * ```
     */
    public fun getRadiiArray(rr: SkRRect): SkVector {
      TODO("Implement getRadiiArray")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRRectPriv::AllCornersCircular(const SkRRect& rr, float tolerance) {
     *     return SkScalarNearlyEqual(rr.fRadii[0].fX, rr.fRadii[0].fY, tolerance) &&
     *            SkScalarNearlyEqual(rr.fRadii[1].fX, rr.fRadii[1].fY, tolerance) &&
     *            SkScalarNearlyEqual(rr.fRadii[2].fX, rr.fRadii[2].fY, tolerance) &&
     *            SkScalarNearlyEqual(rr.fRadii[3].fX, rr.fRadii[3].fY, tolerance);
     * }
     * ```
     */
    public fun allCornersCircular(rr: SkRRect, tolerance: Float = TODO()): Boolean {
      TODO("Implement allCornersCircular")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRRectPriv::AllCornersRelativelyCircular(const SkRRect &rr, float tolerance) {
     *     return IsRelativelyCircular(rr.fRadii[0].fX, rr.fRadii[0].fY, tolerance) &&
     *            IsRelativelyCircular(rr.fRadii[1].fX, rr.fRadii[1].fY, tolerance) &&
     *            IsRelativelyCircular(rr.fRadii[2].fX, rr.fRadii[2].fY, tolerance) &&
     *            IsRelativelyCircular(rr.fRadii[3].fX, rr.fRadii[3].fY, tolerance);
     * }
     * ```
     */
    public fun allCornersRelativelyCircular(rr: SkRRect, tolerance: Float = TODO()): Boolean {
      TODO("Implement allCornersRelativelyCircular")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRRectPriv::IsRelativelyCircular(float rx, float ry, float tolerance) {
     *     // The ellipse is considered relatively circular if either `rx/ry` or `ry/rx` is within
     *     // `tolerance` of 1.0, but this is equivalent to comparing the absolute difference between
     *     // `rx` and `ry` to `tolerance` multiplied by the largest radii.
     *     return std::abs(rx - ry) <= tolerance * std::max(rx, ry);
     * }
     * ```
     */
    public fun isRelativelyCircular(
      rx: Float,
      ry: Float,
      tolerance: Float = TODO(),
    ): Boolean {
      TODO("Implement isRelativelyCircular")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRRectPriv::ReadFromBuffer(SkRBuffer* buffer, SkRRect* rr) {
     *     if (buffer->available() < SkRRect::kSizeInMemory) {
     *         return false;
     *     }
     *     SkRRect storage;
     *     return buffer->read(&storage, SkRRect::kSizeInMemory) &&
     *            (rr->readFromMemory(&storage, SkRRect::kSizeInMemory) == SkRRect::kSizeInMemory);
     * }
     * ```
     */
    public fun readFromBuffer(buffer: SkRBuffer?, rr: SkRRect?): Boolean {
      TODO("Implement readFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkRRectPriv::WriteToBuffer(const SkRRect& rr, SkWBuffer* buffer) {
     *     // Serialize only the rect and corners, but not the derived type tag.
     *     buffer->write(&rr, SkRRect::kSizeInMemory);
     * }
     * ```
     */
    public fun writeToBuffer(rr: SkRRect, buffer: SkWBuffer?) {
      TODO("Implement writeToBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool ContainsPoint(const SkRRect& rr, const SkPoint& p) {
     *         return rr.getBounds().contains(p.fX, p.fY) && rr.checkCornerContainment(p.fX, p.fY);
     *     }
     * ```
     */
    public fun containsPoint(rr: SkRRect, p: SkPoint): Boolean {
      TODO("Implement containsPoint")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRect SkRRectPriv::InnerBounds(const SkRRect& rr) {
     *     if (rr.isEmpty() || rr.isRect()) {
     *         return rr.rect();
     *     }
     *
     *     // We start with the outer bounds of the round rect and consider three subsets and take the
     *     // one with maximum area. The first two are the horizontal and vertical rects inset from the
     *     // corners, the third is the rect inscribed at the corner curves' maximal point. This forms
     *     // the exact solution when all corners have the same radii (the radii do not have to be
     *     // circular).
     *     SkRect innerBounds = rr.getBounds();
     *     SkVector tl = rr.radii(SkRRect::kUpperLeft_Corner);
     *     SkVector tr = rr.radii(SkRRect::kUpperRight_Corner);
     *     SkVector bl = rr.radii(SkRRect::kLowerLeft_Corner);
     *     SkVector br = rr.radii(SkRRect::kLowerRight_Corner);
     *
     *     // Select maximum inset per edge, which may move an adjacent corner of the inscribed
     *     // rectangle off of the rounded-rect path, but that is acceptable given that the general
     *     // equation for inscribed area is non-trivial to evaluate.
     *     SkScalar leftShift   = std::max(tl.fX, bl.fX);
     *     SkScalar topShift    = std::max(tl.fY, tr.fY);
     *     SkScalar rightShift  = std::max(tr.fX, br.fX);
     *     SkScalar bottomShift = std::max(bl.fY, br.fY);
     *
     *     SkScalar dw = leftShift + rightShift;
     *     SkScalar dh = topShift + bottomShift;
     *
     *     // Area removed by shifting left/right
     *     SkScalar horizArea = (innerBounds.width() - dw) * innerBounds.height();
     *     // And by shifting top/bottom
     *     SkScalar vertArea = (innerBounds.height() - dh) * innerBounds.width();
     *     // And by shifting all edges: just considering a corner ellipse, the maximum inscribed rect has
     *     // a corner at sqrt(2)/2 * (rX, rY), so scale all corner shifts by (1 - sqrt(2)/2) to get the
     *     // safe shift per edge (since the shifts already are the max radius for that edge).
     *     // - We actually scale by a value slightly increased to make it so that the shifted corners are
     *     //   safely inside the curves, otherwise numerical stability can cause it to fail contains().
     *     static constexpr SkScalar kScale = (1.f - SK_ScalarRoot2Over2) + 1e-5f;
     *     SkScalar innerArea = (innerBounds.width() - kScale * dw) * (innerBounds.height() - kScale * dh);
     *
     *     if (horizArea > vertArea && horizArea > innerArea) {
     *         // Cut off corners by insetting left and right
     *         innerBounds.fLeft += leftShift;
     *         innerBounds.fRight -= rightShift;
     *     } else if (vertArea > innerArea) {
     *         // Cut off corners by insetting top and bottom
     *         innerBounds.fTop += topShift;
     *         innerBounds.fBottom -= bottomShift;
     *     } else if (innerArea > 0.f) {
     *         // Inset on all sides, scaled to touch
     *         innerBounds.fLeft += kScale * leftShift;
     *         innerBounds.fRight -= kScale * rightShift;
     *         innerBounds.fTop += kScale * topShift;
     *         innerBounds.fBottom -= kScale * bottomShift;
     *     } else {
     *         // Inner region would collapse to empty
     *         return SkRect::MakeEmpty();
     *     }
     *
     *     SkASSERT(innerBounds.isSorted() && !innerBounds.isEmpty());
     *     return innerBounds;
     * }
     * ```
     */
    public fun innerBounds(rr: SkRRect): SkRect {
      TODO("Implement innerBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRRect SkRRectPriv::ConservativeIntersect(const SkRRect& a, const SkRRect& b) {
     *     // Returns the coordinate of the rect matching the corner enum.
     *     auto getCorner = [](const SkRect& r, SkRRect::Corner corner) -> SkPoint {
     *         switch(corner) {
     *             case SkRRect::kUpperLeft_Corner:  return {r.fLeft, r.fTop};
     *             case SkRRect::kUpperRight_Corner: return {r.fRight, r.fTop};
     *             case SkRRect::kLowerLeft_Corner:  return {r.fLeft, r.fBottom};
     *             case SkRRect::kLowerRight_Corner: return {r.fRight, r.fBottom};
     *             default: SkUNREACHABLE;
     *         }
     *     };
     *     // Returns true if shape A's extreme point is contained within shape B's extreme point, relative
     *     // to the 'corner' location. If the two shapes' corners have the same ellipse radii, this
     *     // is sufficient for A's ellipse arc to be contained by B's ellipse arc.
     *     auto insideCorner = [](SkRRect::Corner corner, const SkPoint& a, const SkPoint& b) {
     *         switch(corner) {
     *             case SkRRect::kUpperLeft_Corner:  return a.fX >= b.fX && a.fY >= b.fY;
     *             case SkRRect::kUpperRight_Corner: return a.fX <= b.fX && a.fY >= b.fY;
     *             case SkRRect::kLowerRight_Corner: return a.fX <= b.fX && a.fY <= b.fY;
     *             case SkRRect::kLowerLeft_Corner:  return a.fX >= b.fX && a.fY <= b.fY;
     *             default:  SkUNREACHABLE;
     *         }
     *     };
     *
     *     auto getIntersectionRadii = [&](const SkRect& r, SkRRect::Corner corner, SkVector* radii) {
     *         SkPoint test = getCorner(r, corner);
     *         SkPoint aCorner = getCorner(a.rect(), corner);
     *         SkPoint bCorner = getCorner(b.rect(), corner);
     *
     *         if (test == aCorner && test == bCorner) {
     *             // The round rects share a corner anchor, so pick A or B such that its X and Y radii
     *             // are both larger than the other rrect's, or return false if neither A or B has the max
     *             // corner radii (this is more permissive than the single corner tests below).
     *             SkVector aRadii = a.radii(corner);
     *             SkVector bRadii = b.radii(corner);
     *             if (aRadii.fX >= bRadii.fX && aRadii.fY >= bRadii.fY) {
     *                 *radii = aRadii;
     *                 return true;
     *             } else if (bRadii.fX >= aRadii.fX && bRadii.fY >= aRadii.fY) {
     *                 *radii = bRadii;
     *                 return true;
     *             } else {
     *                 return false;
     *             }
     *         } else if (test == aCorner) {
     *             // Test that A's ellipse is contained by B. This is a non-trivial function to evaluate
     *             // so we resrict it to when the corners have the same radii. If not, we use the more
     *             // conservative test that the extreme point of A's bounding box is contained in B.
     *             *radii = a.radii(corner);
     *             if (*radii == b.radii(corner)) {
     *                 return insideCorner(corner, aCorner, bCorner); // A inside B
     *             } else {
     *                 return b.checkCornerContainment(aCorner.fX, aCorner.fY);
     *             }
     *         } else if (test == bCorner) {
     *             // Mirror of the above
     *             *radii = b.radii(corner);
     *             if (*radii == a.radii(corner)) {
     *                 return insideCorner(corner, bCorner, aCorner); // B inside A
     *             } else {
     *                 return a.checkCornerContainment(bCorner.fX, bCorner.fY);
     *             }
     *         } else {
     *             // This is a corner formed by two straight edges of A and B, so confirm that it is
     *             // contained in both (if not, then the intersection can't be a round rect).
     *             *radii = {0.f, 0.f};
     *             return a.checkCornerContainment(test.fX, test.fY) &&
     *                    b.checkCornerContainment(test.fX, test.fY);
     *         }
     *     };
     *
     *     // We fill in the SkRRect directly. Since the rect and radii are either 0s or determined by
     *     // valid existing SkRRects, we know we are finite.
     *     SkRRect intersection;
     *     if (!intersection.fRect.intersect(a.rect(), b.rect())) {
     *         // Definitely no intersection
     *         return SkRRect::MakeEmpty();
     *     }
     *
     *     const SkRRect::Corner corners[] = {
     *         SkRRect::kUpperLeft_Corner,
     *         SkRRect::kUpperRight_Corner,
     *         SkRRect::kLowerRight_Corner,
     *         SkRRect::kLowerLeft_Corner
     *     };
     *     // By definition, edges is contained in the bounds of 'a' and 'b', but now we need to consider
     *     // the corners. If the bound's corner point is in both rrects, the corner radii will be 0s.
     *     // If the bound's corner point matches a's edges and is inside 'b', we use a's radii.
     *     // Same for b's radii. If any corner fails these conditions, we reject the intersection as an
     *     // rrect. If after determining radii for all 4 corners, they would overlap, we also reject the
     *     // intersection shape.
     *     for (auto c : corners) {
     *         if (!getIntersectionRadii(intersection.fRect, c, &intersection.fRadii[c])) {
     *             return SkRRect::MakeEmpty(); // Resulting intersection is not a rrect
     *         }
     *     }
     *
     *     // Check for radius overlap along the four edges, since the earlier evaluation was only a
     *     // one-sided corner check. If they aren't valid, a corner's radii doesn't fit within the rect.
     *     // If the radii are scaled, the combination of radii from two adjacent corners doesn't fit.
     *     // Normally for a regularly constructed SkRRect, we want this scaling, but in this case it means
     *     // the intersection shape is definitively not a round rect.
     *     if (!SkRRect::AreRectAndRadiiValid(intersection.fRect, intersection.fRadii) ||
     *         intersection.scaleRadii()) {
     *         return SkRRect::MakeEmpty();
     *     }
     *
     *     // The intersection is an rrect of the given radii. Potentially all 4 corners could have
     *     // been simplified to (0,0) radii, making the intersection a rectangle.
     *     intersection.computeType();
     *     return intersection;
     * }
     * ```
     */
    public fun conservativeIntersect(a: SkRRect, b: SkRRect): SkRRect {
      TODO("Implement conservativeIntersect")
    }
  }
}

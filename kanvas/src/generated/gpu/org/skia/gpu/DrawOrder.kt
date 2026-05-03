package org.skia.gpu

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * class DrawOrder {
 * public:
 *     // The first PaintersDepth is reserved for clearing the depth attachment; any draw using this
 *     // depth will always fail the depth test.
 *     inline static constexpr PaintersDepth kClearDepth = PaintersDepth::First();
 *     // The first CompressedPaintersOrder is reserved to indicate there is no previous draw that
 *     // must come before a draw.
 *     inline static constexpr
 *             CompressedPaintersOrder kNoIntersection = CompressedPaintersOrder::First();
 *     // The first DisjointStencilIndex is reserved to indicate an unassigned stencil set.
 *     inline static constexpr DisjointStencilIndex kUnassigned = DisjointStencilIndex::First();
 *
 *     explicit DrawOrder(PaintersDepth originalOrder)
 *             : fPaintOrder(kNoIntersection)
 *             , fStencilIndex(kUnassigned)
 *             , fDepth(originalOrder) {}
 *
 *     DrawOrder(PaintersDepth originalOrder, CompressedPaintersOrder compressedOrder)
 *             : fPaintOrder(compressedOrder)
 *             , fStencilIndex(kUnassigned)
 *             , fDepth(originalOrder) {}
 *
 *     CompressedPaintersOrder paintOrder()   const { return fPaintOrder;   }
 *     DisjointStencilIndex    stencilIndex() const { return fStencilIndex; }
 *     PaintersDepth           depth()        const { return fDepth;        }
 *
 *     // While the PaintersDepth is a monotonically increasing value, the depth buffer prefers to
 *     // use LESS and LEQUAL comparisons starting with a clear value of 1.f, so we normalize and flip
 *     // the floating point value to count down from 1.0.
 *     float depthAsFloat() const {
 *         return 1.f - fDepth.bits() / (float) PaintersDepth::Last().bits();
 *     }
 *
 *     // Coopt the stencil index to encode the draw's actual painter's depth in decreasing order,
 *     // for use enforcing F2B order (since the compressed painter's order handles B2F).
 *     DrawOrder& reverseDepthAsStencil() {
 *         SkASSERT(fStencilIndex == kUnassigned); // can't have a real stencil index
 *         fStencilIndex = DisjointStencilIndex::Last().bits() - fDepth.bits();
 *         return *this;
 *     }
 *
 *     DrawOrder& dependsOnPaintersOrder(CompressedPaintersOrder prevDraw) {
 *         // A draw must be ordered after all previous draws that it depends on
 *         CompressedPaintersOrder next = prevDraw.next();
 *         if (fPaintOrder < next) {
 *             fPaintOrder = next;
 *         }
 *         return *this;
 *     }
 *
 *     DrawOrder& dependsOnStencil(DisjointStencilIndex disjointSet) {
 *         // Stencil usage should only be set once
 *         SkASSERT(fStencilIndex == kUnassigned);
 *         fStencilIndex = disjointSet;
 *         return *this;
 *     }
 *
 * private:
 *     CompressedPaintersOrder fPaintOrder;
 *     DisjointStencilIndex    fStencilIndex;
 *     PaintersDepth           fDepth;
 * }
 * ```
 */
public data class DrawOrder public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr PaintersDepth kClearDepth = PaintersDepth::First()
   * ```
   */
  private var fPaintOrder: CompressedPaintersOrder,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr
   *             CompressedPaintersOrder kNoIntersection = CompressedPaintersOrder::First()
   * ```
   */
  private var fStencilIndex: DisjointStencilIndex,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr DisjointStencilIndex kUnassigned = DisjointStencilIndex::First()
   * ```
   */
  private var fDepth: PaintersDepth,
) {
  /**
   * C++ original:
   * ```cpp
   * CompressedPaintersOrder paintOrder()   const { return fPaintOrder;   }
   * ```
   */
  public fun paintOrder(): CompressedPaintersOrder {
    TODO("Implement paintOrder")
  }

  /**
   * C++ original:
   * ```cpp
   * DisjointStencilIndex    stencilIndex() const { return fStencilIndex; }
   * ```
   */
  public fun stencilIndex(): DisjointStencilIndex {
    TODO("Implement stencilIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintersDepth           depth()        const { return fDepth;        }
   * ```
   */
  public fun depth(): PaintersDepth {
    TODO("Implement depth")
  }

  /**
   * C++ original:
   * ```cpp
   * float depthAsFloat() const {
   *         return 1.f - fDepth.bits() / (float) PaintersDepth::Last().bits();
   *     }
   * ```
   */
  public fun depthAsFloat(): Float {
    TODO("Implement depthAsFloat")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawOrder& reverseDepthAsStencil() {
   *         SkASSERT(fStencilIndex == kUnassigned); // can't have a real stencil index
   *         fStencilIndex = DisjointStencilIndex::Last().bits() - fDepth.bits();
   *         return *this;
   *     }
   * ```
   */
  public fun reverseDepthAsStencil(): DrawOrder {
    TODO("Implement reverseDepthAsStencil")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawOrder& dependsOnPaintersOrder(CompressedPaintersOrder prevDraw) {
   *         // A draw must be ordered after all previous draws that it depends on
   *         CompressedPaintersOrder next = prevDraw.next();
   *         if (fPaintOrder < next) {
   *             fPaintOrder = next;
   *         }
   *         return *this;
   *     }
   * ```
   */
  public fun dependsOnPaintersOrder(prevDraw: CompressedPaintersOrder): DrawOrder {
    TODO("Implement dependsOnPaintersOrder")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawOrder& dependsOnStencil(DisjointStencilIndex disjointSet) {
   *         // Stencil usage should only be set once
   *         SkASSERT(fStencilIndex == kUnassigned);
   *         fStencilIndex = disjointSet;
   *         return *this;
   *     }
   * ```
   */
  public fun dependsOnStencil(disjointSet: DisjointStencilIndex): DrawOrder {
    TODO("Implement dependsOnStencil")
  }

  public companion object {
    public val kClearDepth: PaintersDepth = TODO("Initialize kClearDepth")

    public val kNoIntersection: CompressedPaintersOrder = TODO("Initialize kNoIntersection")

    public val kUnassigned: DisjointStencilIndex = TODO("Initialize kUnassigned")
  }
}

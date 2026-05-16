package org.skia.gpu

import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class StrokeStyle {
 * public:
 *     StrokeStyle() : fHalfWidth(0.f), fJoinLimit(0.f), fCap(SkPaint::kButt_Cap) {}
 *     StrokeStyle(float width,
 *                 float miterLimit,
 *                 SkPaint::Join join,
 *                 SkPaint::Cap cap)
 *             : fHalfWidth(std::max(0.f, 0.5f * width))
 *             , fJoinLimit(join == SkPaint::kMiter_Join ? std::max(0.f, miterLimit) :
 *                          (join == SkPaint::kBevel_Join ? 0.f : -1.f))
 *             , fCap(cap) {}
 *
 *     StrokeStyle(const StrokeStyle&) = default;
 *
 *     StrokeStyle& operator=(const StrokeStyle&) = default;
 *
 *     bool isMiterJoin() const { return fJoinLimit > 0.f;  }
 *     bool isBevelJoin() const { return fJoinLimit == 0.f; }
 *     bool isRoundJoin() const { return fJoinLimit < 0.f;  }
 *
 *     float         halfWidth()  const { return fHalfWidth;                }
 *     float         width()      const { return 2.f * fHalfWidth;          }
 *     float         miterLimit() const { return std::max(0.f, fJoinLimit); }
 *     SkPaint::Cap  cap()        const { return fCap;                      }
 *     SkPaint::Join join()       const {
 *         return fJoinLimit > 0.f ? SkPaint::kMiter_Join :
 *                (fJoinLimit == 0.f ? SkPaint::kBevel_Join : SkPaint::kRound_Join);
 *     }
 *
 *     // Raw join limit, compatible with tess::StrokeParams
 *     float joinLimit() const { return fJoinLimit; }
 *
 * private:
 *     float        fHalfWidth; // >0: relative to transform; ==0: hairline, 1px in device space
 *     float        fJoinLimit; // >0: miter join; ==0: bevel join; <0: round join
 *     SkPaint::Cap fCap;
 * }
 * ```
 */
public data class StrokeStyle public constructor(
  /**
   * C++ original:
   * ```cpp
   * float        fHalfWidth
   * ```
   */
  private var fHalfWidth: Float,
  /**
   * C++ original:
   * ```cpp
   * float        fJoinLimit
   * ```
   */
  private var fJoinLimit: Float,
  /**
   * C++ original:
   * ```cpp
   * SkPaint::Cap fCap
   * ```
   */
  private var fCap: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * StrokeStyle& operator=(const StrokeStyle&) = default
   * ```
   */
  public fun assign(param0: StrokeStyle) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isMiterJoin() const { return fJoinLimit > 0.f;  }
   * ```
   */
  public fun isMiterJoin(): Boolean {
    TODO("Implement isMiterJoin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isBevelJoin() const { return fJoinLimit == 0.f; }
   * ```
   */
  public fun isBevelJoin(): Boolean {
    TODO("Implement isBevelJoin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRoundJoin() const { return fJoinLimit < 0.f;  }
   * ```
   */
  public fun isRoundJoin(): Boolean {
    TODO("Implement isRoundJoin")
  }

  /**
   * C++ original:
   * ```cpp
   * float         halfWidth()  const { return fHalfWidth;                }
   * ```
   */
  public fun halfWidth(): Float {
    TODO("Implement halfWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * float         width()      const { return 2.f * fHalfWidth;          }
   * ```
   */
  public fun width(): Float {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * float         miterLimit() const { return std::max(0.f, fJoinLimit); }
   * ```
   */
  public fun miterLimit(): Float {
    TODO("Implement miterLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Cap  cap()        const { return fCap;                      }
   * ```
   */
  public fun cap(): Int {
    TODO("Implement cap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Join join()       const {
   *         return fJoinLimit > 0.f ? SkPaint::kMiter_Join :
   *                (fJoinLimit == 0.f ? SkPaint::kBevel_Join : SkPaint::kRound_Join);
   *     }
   * ```
   */
  public fun join(): Int {
    TODO("Implement join")
  }

  /**
   * C++ original:
   * ```cpp
   * float joinLimit() const { return fJoinLimit; }
   * ```
   */
  public fun joinLimit(): Float {
    TODO("Implement joinLimit")
  }
}

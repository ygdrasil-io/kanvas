package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SK_API Agtm {
 *   public:
 *     /**
 *      * Parse the specified SkData. Returns nullptr if the data fails to parse.
 *      */
 *     static std::unique_ptr<Agtm> Make(const SkData* data);
 *
 *     /**
 *      * Generate reference white tone mapping metadata for the specified baseline HDR headroom and
 *      * HDR reference white values.
 *      */
 *     static std::unique_ptr<Agtm> MakeReferenceWhite(float hdrReferenceWhite,
 *                                                     float baselineHdrHeadroom);
 *
 *     /**
 *      * Generate metadata with a HDR reference white set to `hdrReferenceWhite`, that specifies that
 *      * no tone mapping is to be done (that is, just clamping is to be performed), and that the
 *      * content has HDR headroom specified by `baselineHdrHeadroom`.
 *      */
 *     static std::unique_ptr<Agtm> MakeClamp(float hdrReferenceWhite, float baselineHdrHeadroom);
 *
 *     Agtm() = default;
 *     Agtm(const Agtm&) = delete;
 *     Agtm& operator=(const Agtm&) = delete;
 *     Agtm(Agtm&&) = delete;
 *     Agtm& operator=(Agtm&&) = delete;
 *     virtual ~Agtm() = default;
 *
 *     /**
 *      * Serialize the data to the format parsed by Make.
 *      */
 *     virtual sk_sp<SkData> serialize() const = 0;
 *
 *     /**
 *      * The default value for the HdrReferenceWhite metadata item.
 *      */
 *     static constexpr float kDefaultHdrReferenceWhite = 203.f;
 *
 *     /**
 *      * Return the HdrReferenceWhite metadata item value.
 *      */
 *     virtual float getHdrReferenceWhite() const = 0;
 *
 *     /**
 *      * Functions to query if the BaselineHdrHeadroom metadata item was specified and retrieve it
 *      * (which will assert if was not specified).
 *      */
 *     virtual bool hasBaselineHdrHeadroom() const = 0;
 *     virtual float getBaselineHdrHeadroom() const = 0;
 *
 *     /**
 *      * Return true if this metadata specifies not to do any tone mapping (it is the type that
 *      * was created using MakeClamp).
 *      */
 *     virtual bool isClamp() const = 0;
 *
 *     /**
 *      * Return the SkColorFilter to tone map to the specified targeted HDR headroom.
 *      */
 *     virtual sk_sp<SkColorFilter> makeColorFilter(float targetedHdrHeadroom) const = 0;
 *
 *     /**
 *      * Return a human-readable description.
 *      */
 *     virtual SkString toString() const = 0;
 * }
 * ```
 */
public abstract class Agtm public constructor() {
  /**
   * C++ original:
   * ```cpp
   * Agtm() = default
   * ```
   */
  public constructor(param0: Agtm) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Agtm& operator=(const Agtm&) = delete
   * ```
   */
  public fun assign(param0: Agtm) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Agtm& operator=(Agtm&&) = delete
   * ```
   */
  public abstract fun serialize(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkData> serialize() const = 0
   * ```
   */
  public abstract fun getHdrReferenceWhite(): Float

  /**
   * C++ original:
   * ```cpp
   * virtual float getHdrReferenceWhite() const = 0
   * ```
   */
  public abstract fun hasBaselineHdrHeadroom(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool hasBaselineHdrHeadroom() const = 0
   * ```
   */
  public abstract fun getBaselineHdrHeadroom(): Float

  /**
   * C++ original:
   * ```cpp
   * virtual float getBaselineHdrHeadroom() const = 0
   * ```
   */
  public abstract fun isClamp(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isClamp() const = 0
   * ```
   */
  public abstract fun makeColorFilter(targetedHdrHeadroom: Float): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkColorFilter> makeColorFilter(float targetedHdrHeadroom) const = 0
   * ```
   */
  public abstract override fun toString(): String

  public companion object {
    public val kDefaultHdrReferenceWhite: Float = TODO("Initialize kDefaultHdrReferenceWhite")

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<Agtm> Agtm::Make(const SkData* data) {
     *     auto result = std::make_unique<AgtmImpl>();
     *     if (!result->parse(data)) {
     *         return nullptr;
     *     }
     *     result->populateGainCurvesXYM();
     *     return result;
     * }
     * ```
     */
    public fun make(`data`: SkData?): Agtm? {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<Agtm> Agtm::MakeReferenceWhite(float hdrReferenceWhite, float baselineHdrHeadroom) {
     *     SkASSERT(baselineHdrHeadroom >= 0.f);
     *     auto result = std::make_unique<AgtmImpl>();
     *     result->fHdrReferenceWhite = hdrReferenceWhite;
     *     result->fBaselineHdrHeadroom = baselineHdrHeadroom;
     *     result->populateUsingRwtmo();
     *     result->populateGainCurvesXYM();
     *     return result;
     * }
     * ```
     */
    public fun makeReferenceWhite(hdrReferenceWhite: Float, baselineHdrHeadroom: Float): Agtm? {
      TODO("Implement makeReferenceWhite")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<Agtm> Agtm::MakeClamp(float hdrReferenceWhite, float baselineHdrHeadroom) {
     *     SkASSERT(baselineHdrHeadroom >= 0.f);
     *     auto result = std::make_unique<AgtmImpl>();
     *     result->fHdrReferenceWhite = hdrReferenceWhite;
     *     result->fBaselineHdrHeadroom = baselineHdrHeadroom;
     *     result->fGainApplicationSpacePrimaries = SkNamedPrimaries::kRec2020;
     *     result->populateGainCurvesXYM();
     *     return result;
     * }
     * ```
     */
    public fun makeClamp(hdrReferenceWhite: Float, baselineHdrHeadroom: Float): Agtm? {
      TODO("Implement makeClamp")
    }
  }
}

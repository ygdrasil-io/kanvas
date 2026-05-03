package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathEffect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector

public typealias SkPairPathEffectINHERITED = SkPathEffectBase

/**
 * C++ original:
 * ```cpp
 * class SkPathEffectBase : public SkPathEffect {
 * public:
 *     SkPathEffectBase() {}
 *
 *     /** \class PointData
 *
 *         PointData aggregates all the information needed to draw the point
 *         primitives returned by an 'asPoints' call.
 *     */
 *     class PointData {
 *     public:
 *         PointData()
 *             : fFlags(0)
 *             , fPoints(nullptr)
 *             , fNumPoints(0) {
 *             fSize.set(SK_Scalar1, SK_Scalar1);
 *             // 'asPoints' needs to initialize/fill-in 'fClipRect' if it sets
 *             // the kUseClip flag
 *         }
 *         ~PointData() {
 *             delete [] fPoints;
 *         }
 *
 *         // TODO: consider using passed-in flags to limit the work asPoints does.
 *         // For example, a kNoPath flag could indicate don't bother generating
 *         // stamped solutions.
 *
 *         // Currently none of these flags are supported.
 *         enum PointFlags {
 *             kCircles_PointFlag            = 0x01,   // draw points as circles (instead of rects)
 *             kUsePath_PointFlag            = 0x02,   // draw points as stamps of the returned path
 *             kUseClip_PointFlag            = 0x04,   // apply 'fClipRect' before drawing the points
 *         };
 *
 *         uint32_t           fFlags;      // flags that impact the drawing of the points
 *         SkPoint*           fPoints;     // the center point of each generated point
 *         int                fNumPoints;  // number of points in fPoints
 *         SkVector           fSize;       // the size to draw the points
 *         SkRect             fClipRect;   // clip required to draw the points (if kUseClip is set)
 *         SkPath             fPath;       // 'stamp' to be used at each point (if kUsePath is set)
 *
 *         SkPath             fFirst;      // If not empty, contains geometry for first point
 *         SkPath             fLast;       // If not empty, contains geometry for last point
 *
 *         SkSpan<SkPoint> points() { return {fPoints, (size_t)fNumPoints}; }
 *     };
 *
 *     /**
 *      *  Does applying this path effect to 'src' yield a set of points? If so,
 *      *  optionally return the points in 'results'.
 *      */
 *     bool asPoints(PointData* results, const SkPath& src,
 *                           const SkStrokeRec&, const SkMatrix&,
 *                           const SkRect* cullR) const;
 *
 *     /**
 *      *  If the PathEffect can be represented as a dash pattern, asADash will return kDash_DashType
 *      *  and None otherwise. If a non NULL info is passed in, the various DashInfo will be filled
 *      *  in if the PathEffect can be a dash pattern. If passed in info has an fCount equal or
 *      *  greater to that of the effect, it will memcpy the values of the dash intervals into the
 *      *  info. Thus the general approach will be call asADash once with default info to get DashType
 *      *  and fCount. If effect can be represented as a dash pattern, allocate space for the intervals
 *      *  in info, then call asADash again with the same info and the intervals will get copied in.
 *      */
 *
 *     SkFlattenable::Type getFlattenableType() const override {
 *         return kSkPathEffect_Type;
 *     }
 *
 *     static sk_sp<SkPathEffect> Deserialize(const void* data, size_t size,
 *                                           const SkDeserialProcs* procs = nullptr) {
 *         return sk_sp<SkPathEffect>(static_cast<SkPathEffect*>(
 *                                   SkFlattenable::Deserialize(
 *                                   kSkPathEffect_Type, data, size, procs).release()));
 *     }
 *
 *     /**
 *      * Filter the input path.
 *      *
 *      * The CTM parameter is provided for path effects that can use the information.
 *      * The output of path effects must always be in the original (input) coordinate system,
 *      * regardless of whether the path effect uses the CTM or not.
 *      */
 *     virtual bool onFilterPath(SkPathBuilder*, const SkPath&, SkStrokeRec*, const SkRect*,
 *                               const SkMatrix& /* ctm */) const = 0;
 *
 *     /** Path effects *requiring* a valid CTM should override to return true. */
 *     virtual bool onNeedsCTM() const { return false; }
 *
 *     virtual bool onAsPoints(PointData*, const SkPath&, const SkStrokeRec&, const SkMatrix&,
 *                             const SkRect*) const {
 *         return false;
 *     }
 *
 *     struct DashInfo {
 *         SkSpan<const SkScalar> fIntervals;
 *         SkScalar               fPhase;
 *     };
 *
 *     virtual std::optional<DashInfo> asADash() const {
 *         return {};
 *     }
 *
 *
 *     // Compute a conservative bounds for its effect, given the bounds of the path. 'bounds' is
 *     // both the input and output; if false is returned, fast bounds could not be calculated and
 *     // 'bounds' is undefined.
 *     //
 *     // If 'bounds' is null, performs a dry-run determining if bounds could be computed.
 *     virtual bool computeFastBounds(SkRect* bounds) const = 0;
 *
 *     static void RegisterFlattenables();
 *
 * private:
 *     using INHERITED = SkPathEffect;
 * }
 * ```
 */
public abstract class SkPathEffectBase public constructor() : SkPathEffect() {
  /**
   * C++ original:
   * ```cpp
   * bool SkPathEffectBase::asPoints(PointData* results, const SkPath& src,
   *                     const SkStrokeRec& rec, const SkMatrix& mx, const SkRect* rect) const {
   *     return this->onAsPoints(results, src, rec, mx, rect);
   * }
   * ```
   */
  public fun asPoints(
    results: PointData?,
    src: SkPath,
    rec: SkStrokeRec,
    mx: SkMatrix,
    cullR: SkRect?,
  ): Boolean {
    TODO("Implement asPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Type getFlattenableType() const override {
   *         return kSkPathEffect_Type;
   *     }
   * ```
   */
  public override fun getFlattenableType(): SkFlattenable.Type {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onFilterPath(SkPathBuilder*, const SkPath&, SkStrokeRec*, const SkRect*,
   *                               const SkMatrix& /* ctm */) const = 0
   * ```
   */
  public abstract fun onFilterPath(
    param0: SkPathBuilder?,
    param1: SkPath,
    param2: SkStrokeRec?,
    param3: SkRect?,
    param4: Int,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onNeedsCTM() const { return false; }
   * ```
   */
  public open fun onNeedsCTM(): Boolean {
    TODO("Implement onNeedsCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAsPoints(PointData*, const SkPath&, const SkStrokeRec&, const SkMatrix&,
   *                             const SkRect*) const {
   *         return false;
   *     }
   * ```
   */
  public open fun onAsPoints(
    param0: PointData?,
    param1: SkPath,
    param2: SkStrokeRec,
    param3: SkMatrix,
    param4: SkRect?,
  ): Boolean {
    TODO("Implement onAsPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::optional<DashInfo> asADash() const {
   *         return {};
   *     }
   * ```
   */
  public open fun asADash(): Int {
    TODO("Implement asADash")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool computeFastBounds(SkRect* bounds) const = 0
   * ```
   */
  public abstract fun computeFastBounds(bounds: SkRect?): Boolean

  public data class PointData public constructor(
    public var fFlags: Int,
    public var fPoints: SkPoint?,
    public var fNumPoints: Int,
    public var fSize: SkVector,
    public var fClipRect: SkRect,
    public var fPath: SkPath,
    public var fFirst: SkPath,
    public var fLast: SkPath,
  ) {
    public fun points(): SkSpan<SkPoint> {
      TODO("Implement points")
    }

    public enum class PointFlags {
      kCircles_PointFlag,
      kUsePath_PointFlag,
      kUseClip_PointFlag,
    }
  }

  public data class DashInfo public constructor(
    public val fIntervals: SkSpan<SkScalar>,
    public var fPhase: SkScalar,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkPathEffect> Deserialize(const void* data, size_t size,
     *                                           const SkDeserialProcs* procs = nullptr) {
     *         return sk_sp<SkPathEffect>(static_cast<SkPathEffect*>(
     *                                   SkFlattenable::Deserialize(
     *                                   kSkPathEffect_Type, data, size, procs).release()));
     *     }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): SkSp<SkPathEffect> {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathEffectBase::RegisterFlattenables() {
     *     SK_REGISTER_FLATTENABLE(SkComposePathEffect);
     *     SK_REGISTER_FLATTENABLE(SkSumPathEffect);
     * }
     * ```
     */
    public fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}

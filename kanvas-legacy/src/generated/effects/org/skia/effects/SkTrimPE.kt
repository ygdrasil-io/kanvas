package org.skia.effects

import kotlin.Boolean
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkTrimPE : public SkPathEffectBase {
 * public:
 *     SkTrimPE(SkScalar startT, SkScalar stopT, SkTrimPathEffect::Mode);
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *     bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*, const SkRect*,
 *                       const SkMatrix&) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkTrimPE)
 *
 *     bool computeFastBounds(SkRect* bounds) const override {
 *         // Trimming a path returns a subset of the input path so just return true and leave bounds
 *         // unmodified
 *         return true;
 *     }
 *
 *     const SkScalar               fStartT,
 *                                  fStopT;
 *     const SkTrimPathEffect::Mode fMode;
 *
 *     using INHERITED = SkPathEffectBase;
 * }
 * ```
 */
public open class SkTrimPE public constructor(
  startT: SkScalar,
  stopT: SkScalar,
  mode: SkTrimPathEffect.Mode,
) : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar               fStartT
   * ```
   */
  private val fStartT: SkScalar = TODO("Initialize fStartT")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar               fStartT,
   *                                  fStopT
   * ```
   */
  private val fStopT: SkScalar = TODO("Initialize fStopT")

  /**
   * C++ original:
   * ```cpp
   * const SkTrimPathEffect::Mode fMode
   * ```
   */
  private val fMode: SkTrimPathEffect.Mode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * void SkTrimPE::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeScalar(fStartT);
   *     buffer.writeScalar(fStopT);
   *     buffer.writeUInt(static_cast<uint32_t>(fMode));
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTrimPE::onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*, const SkRect*,
   *                             const SkMatrix&) const {
   *     if (fStartT >= fStopT) {
   *         SkASSERT(fMode == SkTrimPathEffect::Mode::kNormal);
   *         return true;
   *     }
   *
   *     // First pass: compute the total len.
   *     SkScalar len = 0;
   *     SkPathMeasure meas(src, false);
   *     do {
   *         len += meas.getLength();
   *     } while (meas.nextContour());
   *
   *     const auto arcStart = len * fStartT,
   *                arcStop  = len * fStopT;
   *
   *     // Second pass: actually add segments.
   *     if (fMode == SkTrimPathEffect::Mode::kNormal) {
   *         // Normal mode -> one span.
   *         if (arcStart < arcStop) {
   *             add_segments(src, arcStart, arcStop, dst);
   *         }
   *     } else {
   *         // Inverted mode -> one logical span which wraps around at the end -> two actual spans.
   *         // In order to preserve closed path continuity:
   *         //
   *         //   1) add the second/tail span first
   *         //
   *         //   2) skip the head span move-to for single-closed-contour paths
   *
   *         bool requires_moveto = true;
   *         if (arcStop < len) {
   *             // since we're adding the "tail" first, this is the total number of contours
   *             const auto contour_count = add_segments(src, arcStop, len, dst);
   *
   *             // if the path consists of a single closed contour, we don't want to disconnect
   *             // the two parts with a moveto.
   *             if (contour_count == 1 && src.isLastContourClosed()) {
   *                 requires_moveto = false;
   *             }
   *         }
   *         if (0 <  arcStart) {
   *             add_segments(src, 0, arcStart, dst, requires_moveto);
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  protected override fun onFilterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    param2: SkStrokeRec?,
    param3: SkRect?,
    param4: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeFastBounds(SkRect* bounds) const override {
   *         // Trimming a path returns a subset of the input path so just return true and leave bounds
   *         // unmodified
   *         return true;
   *     }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkTrimPE::CreateProc(SkReadBuffer& buffer) {
   *     const auto start = buffer.readScalar(),
   *                stop  = buffer.readScalar();
   *     const auto mode  = buffer.readUInt();
   *
   *     return SkTrimPathEffect::Make(start, stop,
   *         (mode & 1) ? SkTrimPathEffect::Mode::kInverted : SkTrimPathEffect::Mode::kNormal);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}

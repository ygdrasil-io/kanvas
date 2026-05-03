package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class Merge final : public GeometryNode {
 * public:
 *     enum class Mode {
 *         // Append path mode.
 *         kMerge,
 *
 *         // SkPathOp ops.
 *         kUnion,
 *         kIntersect,
 *         kDifference,
 *         kReverseDifference,
 *         kXOR,
 *     };
 *
 *     struct Rec {
 *         sk_sp<GeometryNode> fGeo;
 *         Mode                fMode;
 *     };
 *
 *     static sk_sp<Merge> Make(std::vector<Rec>&& recs) {
 *         return sk_sp<Merge>(new Merge(std::move(recs)));
 *     }
 *
 *     ~Merge() override;
 *
 * protected:
 *     void onClip(SkCanvas*, bool antiAlias) const override;
 *     void onDraw(SkCanvas*, const SkPaint&) const override;
 *     bool onContains(const SkPoint&)        const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *     SkPath onAsPath() const override;
 *
 * private:
 *     explicit Merge(std::vector<Rec>&& recs);
 *
 *     const std::vector<Rec> fRecs;
 *     SkPath                 fMerged;
 *
 *     using INHERITED = GeometryNode;
 * }
 * ```
 */
public class Merge public constructor(
  recs: List<undefined.Rec>,
) : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<Rec> fRecs
   * ```
   */
  private val fRecs: List<undefined.Rec> = TODO("Initialize fRecs")

  /**
   * C++ original:
   * ```cpp
   * SkPath                 fMerged
   * ```
   */
  private var fMerged: Int = TODO("Initialize fMerged")

  /**
   * C++ original:
   * ```cpp
   * void Merge::onClip(SkCanvas* canvas, bool antiAlias) const {
   *     canvas->clipPath(fMerged, SkClipOp::kIntersect, antiAlias);
   * }
   * ```
   */
  protected override fun onClip(canvas: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void Merge::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawPath(fMerged, paint);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Merge::onContains(const SkPoint& p) const {
   *     return fMerged.contains(p.x(), p.y());
   * }
   * ```
   */
  protected override fun onContains(p: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Merge::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     SkOpBuilder builder;
   *     SkPathBuilder merger;
   *
   *     bool in_builder = false;
   *
   *     auto append = [&](const SkPath& path) {
   *         if (in_builder) {
   *             if (auto result = builder.resolve()) {
   *                 merger = *result;
   *             }
   *             in_builder = false;
   *         }
   *
   *         if (merger.isEmpty()) {
   *             // First merge path determines the fill type.
   *             merger = path;
   *         } else {
   *             merger.addPath(path);
   *         }
   *     };
   *
   *     for (const auto& rec : fRecs) {
   *         rec.fGeo->revalidate(ic, ctm);
   *
   *         if (rec.fMode == Mode::kMerge) {
   *             // Merge (append) is not supported by SkOpBuidler.
   *             append(rec.fGeo->asPath());
   *             continue;
   *         }
   *
   *         if (!in_builder) {
   *             builder.add(merger.detach(), kUnion_SkPathOp);
   *             in_builder = true;
   *         }
   *
   *         builder.add(rec.fGeo->asPath(), mode_to_op(rec.fMode));
   *     }
   *
   *     fMerged = in_builder
   *         ? builder.resolve().value_or(SkPath())
   *         : merger.detach();
   *
   *     return fMerged.computeTightBounds();
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath Merge::onAsPath() const {
   *     return fMerged;
   * }
   * ```
   */
  protected override fun onAsPath(): Int {
    TODO("Implement onAsPath")
  }

  public open class Rec public constructor(
    public var fGeo: Int,
    public var fMode: org.skia.`external`.Mode,
  )

  public enum class Mode {
    kMerge,
    kUnion,
    kIntersect,
    kDifference,
    kReverseDifference,
    kXOR,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Merge> Make(std::vector<Rec>&& recs) {
     *         return sk_sp<Merge>(new Merge(std::move(recs)));
     *     }
     * ```
     */
    public fun make(recs: List<Rec>): Int {
      TODO("Implement make")
    }
  }
}

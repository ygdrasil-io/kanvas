package org.skia.effects

import kotlin.Boolean
import kotlin.FloatArray
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkPMColor4f
import org.skia.core.SkStageRec
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkDeserialProcs

/**
 * C++ original:
 * ```cpp
 * class SkColorFilterBase : public SkColorFilter {
 * public:
 *     [[nodiscard]] virtual bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const = 0;
 *
 *     /** Returns the flags for this filter. Override in subclasses to return custom flags.
 *     */
 *     virtual bool onIsAlphaUnchanged() const { return false; }
 *
 *     enum class Type {
 *         // Used for stubs/tests
 *         kNoop,
 * #define M(type) k##type,
 *         SK_ALL_COLOR_FILTERS(M)
 * #undef M
 *
 *     };
 *
 *     virtual Type type() const = 0;
 *
 *     bool affectsTransparentBlack() const {
 *         return this->filterColor4f(SkColors::kTransparent, nullptr, nullptr) !=
 *                SkColors::kTransparent;
 *     }
 *
 *     virtual SkRuntimeEffect* asRuntimeEffect() const { return nullptr; }
 *
 *     static SkFlattenable::Type GetFlattenableType() {
 *         return kSkColorFilter_Type;
 *     }
 *
 *     SkFlattenable::Type getFlattenableType() const override {
 *         return kSkColorFilter_Type;
 *     }
 *
 *     static sk_sp<SkColorFilter> Deserialize(const void* data, size_t size,
 *                                           const SkDeserialProcs* procs = nullptr) {
 *         return sk_sp<SkColorFilter>(static_cast<SkColorFilter*>(
 *                                   SkFlattenable::Deserialize(
 *                                   kSkColorFilter_Type, data, size, procs).release()));
 *     }
 *
 *     virtual SkPMColor4f onFilterColor4f(const SkPMColor4f& color, SkColorSpace* dstCS) const;
 *
 * protected:
 *     SkColorFilterBase() {}
 *
 *     virtual bool onAsAColorMatrix(float[20]) const;
 *     virtual bool onAsAColorMode(SkColor* color, SkBlendMode* bmode) const;
 *
 * private:
 *     friend class SkColorFilter;
 *
 *     using INHERITED = SkFlattenable;
 * }
 * ```
 */
public abstract class SkColorFilterBase public constructor() : SkColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * virtual bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const = 0
   * ```
   */
  public abstract fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsAlphaUnchanged() const { return false; }
   * ```
   */
  public open fun onIsAlphaUnchanged(): Boolean {
    TODO("Implement onIsAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Type type() const = 0
   * ```
   */
  public abstract fun type(): Type

  /**
   * C++ original:
   * ```cpp
   * bool affectsTransparentBlack() const {
   *         return this->filterColor4f(SkColors::kTransparent, nullptr, nullptr) !=
   *                SkColors::kTransparent;
   *     }
   * ```
   */
  public fun affectsTransparentBlack(): Boolean {
    TODO("Implement affectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkRuntimeEffect* asRuntimeEffect() const { return nullptr; }
   * ```
   */
  public open fun asRuntimeEffect(): SkRuntimeEffect {
    TODO("Implement asRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Type getFlattenableType() const override {
   *         return kSkColorFilter_Type;
   *     }
   * ```
   */
  public override fun getFlattenableType(): Int {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPMColor4f SkColorFilterBase::onFilterColor4f(const SkPMColor4f& color,
   *                                                SkColorSpace* dstCS) const {
   *     constexpr size_t kEnoughForCommonFilters = 2048;  // big enough for a tiny SkSL program
   *     SkSTArenaAlloc<kEnoughForCommonFilters> alloc;
   *     SkRasterPipeline    pipeline(&alloc);
   *     pipeline.appendConstantColor(&alloc, color.vec());
   *     SkSurfaceProps props{}; // default OK; colorFilters don't render text
   *     SkStageRec rec = {&pipeline,
   *                       &alloc,
   *                       kRGBA_F32_SkColorType,
   *                       dstCS,
   *                       color.unpremul(),
   *                       props,
   *                       SkRect::MakeEmpty()};
   *
   *     if (as_CFB(this)->appendStages(rec, color.fA == 1)) {
   *         SkPMColor4f dst;
   *         SkRasterPipelineContexts::MemoryCtx dstPtr = {&dst, 0};
   *         pipeline.append(SkRasterPipelineOp::store_f32, &dstPtr);
   *         pipeline.run(0,0, 1,1);
   *         return dst;
   *     }
   *
   *     SkDEBUGFAIL("onFilterColor4f unimplemented for this filter");
   *     return SkPMColor4f{0,0,0,0};
   * }
   * ```
   */
  public open fun onFilterColor4f(color: SkPMColor4f, dstCS: SkColorSpace?): Int {
    TODO("Implement onFilterColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilterBase::onAsAColorMatrix(float matrix[20]) const {
   *     return false;
   * }
   * ```
   */
  protected open fun onAsAColorMatrix(matrix: FloatArray): Boolean {
    TODO("Implement onAsAColorMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilterBase::onAsAColorMode(SkColor*, SkBlendMode*) const {
   *     return false;
   * }
   * ```
   */
  protected open fun onAsAColorMode(color: SkColor?, bmode: SkBlendMode?): Boolean {
    TODO("Implement onAsAColorMode")
  }

  public enum class Type {
    kNoop,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkFlattenable::Type GetFlattenableType() {
     *         return kSkColorFilter_Type;
     *     }
     * ```
     */
    public fun getFlattenableType(): Int {
      TODO("Implement getFlattenableType")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> Deserialize(const void* data, size_t size,
     *                                           const SkDeserialProcs* procs = nullptr) {
     *         return sk_sp<SkColorFilter>(static_cast<SkColorFilter*>(
     *                                   SkFlattenable::Deserialize(
     *                                   kSkColorFilter_Type, data, size, procs).release()));
     *     }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }
  }
}

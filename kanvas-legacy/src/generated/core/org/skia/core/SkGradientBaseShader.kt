package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.ULong
import org.skia.effects.SkGradient
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkWriteBuffer
import org.skia.gpu.Interpolation
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import org.skia.memory.SkArenaAlloc
import undefined.GradientInfo
import SkColor4f as SkColor4f_
import undefined.SkColor4f as UndefinedSkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkGradientBaseShader : public SkShaderBase {
 * public:
 *     using Interpolation = SkGradient::Interpolation;
 *
 *     SkGradientBaseShader(const SkGradient&, const SkMatrix& ptsToUnit);
 *     ~SkGradientBaseShader() override;
 *
 *     ShaderType type() const final { return ShaderType::kGradientBase; }
 *
 *     bool isOpaque() const override;
 *
 *     bool interpolateInPremul() const {
 *         return fInterpolation.fInPremul == Interpolation::InPremul::kYes;
 *     }
 *
 *     const SkMatrix& getGradientMatrix() const { return fPtsToUnit; }
 *     size_t getColorCount() const { return fColorCount; }
 *     const float* getPositions() const { return fPositions; }
 *
 *     const Interpolation& getInterpolation() const { return fInterpolation; }
 *
 *     static bool ValidGradient(SkSpan<const SkColor4f>,
 *                               SkTileMode tileMode,
 *                               const Interpolation& interpolation);
 *
 *     static sk_sp<SkShader> MakeDegenerateGradient(const SkGradient::Colors&);
 *
 *     // The default SkScalarNearlyZero threshold of .0024 is too big and causes regressions for svg
 *     // gradients defined in the wild.
 *     static constexpr SkScalar kDegenerateThreshold = SK_Scalar1 / (1 << 15);
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 *     void commonAsAGradient(GradientInfo*) const;
 *
 *     bool onAsLuminanceColor(SkColor4f*) const override;
 *
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 *     virtual void appendGradientStages(SkArenaAlloc* alloc,
 *                                       SkRasterPipeline* tPipeline,
 *                                       SkRasterPipeline* postPipeline) const = 0;
 *
 *     const SkMatrix fPtsToUnit;
 *     SkTileMode fTileMode;
 *
 * public:
 *     static void AppendGradientFillStages(SkRasterPipeline* p,
 *                                          SkArenaAlloc* alloc,
 *                                          const SkPMColor4f* colors,
 *                                          const SkScalar* positions,
 *                                          int count);
 *
 *     static void AppendInterpolatedToDstStages(SkRasterPipeline* p,
 *                                               SkArenaAlloc* alloc,
 *                                               bool colorsAreOpaque,
 *                                               const Interpolation& interpolation,
 *                                               const SkColorSpace* intermediateColorSpace,
 *                                               const SkColorSpace* dstColorSpace);
 *
 *     float getPos(size_t i) const {
 *         SkASSERT(i < fColorCount);
 *         return fPositions ? fPositions[i] : SkIntToScalar(i) / (fColorCount - 1);
 *     }
 *
 *     SkColor getLegacyColor(size_t i) const {
 *         SkASSERT(i < fColorCount);
 *         return fColors[i].toSkColor();
 *     }
 *
 *     SkSpan<const SkColor4f> colors() const { return {fColors, fColorCount}; }
 *     SkSpan<const float> positions() const {
 *         return {fPositions, fPositions ? fColorCount : 0};
 *     }
 *
 *     bool firstStopIsImplicit() const { return fFirstStopIsImplicit; }
 *     bool lastStopIsImplicit() const { return fLastStopIsImplicit; }
 *
 *     const sk_sp<SkColorSpace>& colorSpace() const { return fColorSpace; }
 *     const Interpolation& interpolation() const { return fInterpolation; }
 *
 *     bool colorsAreOpaque() const { return fColorsAreOpaque; }
 *
 *     SkTileMode getTileMode() const { return fTileMode; }
 *
 *     const SkBitmap& cachedBitmap() const { return fColorsAndOffsetsBitmap; }
 *     void setCachedBitmap(SkBitmap b) const { fColorsAndOffsetsBitmap = b; }
 *
 * private:
 *     SkColor4f* fColors;               // points into fStorage
 *     SkScalar* fPositions;             // points into fStorage, or nullptr
 *     sk_sp<SkColorSpace> fColorSpace;  // color space of gradient stops
 *     Interpolation fInterpolation;
 *     size_t fColorCount;               // length of fColors (and fPositions, if not nullptr)
 *     bool fFirstStopIsImplicit;
 *     bool fLastStopIsImplicit;
 *
 *     // When the number of stops exceeds Graphite's uniform-based limit the colors and offsets
 *     // are stored in this bitmap. It is stored in the shader so it can be cached with a stable
 *     // id and easily regenerated if purged.
 *     // TODO(b/293160919) remove this field when we can store bitmaps in the cache by id.
 *     mutable SkBitmap fColorsAndOffsetsBitmap;
 *
 *     // Reserve inline space for up to 4 stops.
 *     inline static constexpr size_t kInlineStopCount = 4;
 *     inline static constexpr size_t kInlineStorageSize =
 *             (sizeof(SkColor4f) + sizeof(SkScalar)) * kInlineStopCount;
 *     skia_private::AutoSTMalloc<kInlineStorageSize, uint8_t> fStorage;
 *
 *     bool fColorsAreOpaque;
 * }
 * ```
 */
public abstract class SkGradientBaseShader public constructor(
  desc: SkGradient,
  ptsToUnit: SkMatrix,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkScalar kDegenerateThreshold
   * ```
   */
  protected val fPtsToUnit: Int = TODO("Initialize fPtsToUnit")

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fPtsToUnit
   * ```
   */
  protected var fTileMode: SkTileMode = TODO("Initialize fTileMode")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode fTileMode
   * ```
   */
  private var fColors: Int? = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkColor4f* fColors
   * ```
   */
  private var fPositions: Int? = TODO("Initialize fPositions")

  /**
   * C++ original:
   * ```cpp
   * SkScalar* fPositions
   * ```
   */
  private var fColorSpace: Int = TODO("Initialize fColorSpace")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fColorSpace
   * ```
   */
  private var fInterpolation: Int = TODO("Initialize fInterpolation")

  /**
   * C++ original:
   * ```cpp
   * Interpolation fInterpolation
   * ```
   */
  private var fColorCount: Int = TODO("Initialize fColorCount")

  /**
   * C++ original:
   * ```cpp
   * size_t fColorCount
   * ```
   */
  private var fFirstStopIsImplicit: Boolean = TODO("Initialize fFirstStopIsImplicit")

  /**
   * C++ original:
   * ```cpp
   * bool fFirstStopIsImplicit
   * ```
   */
  private var fLastStopIsImplicit: Boolean = TODO("Initialize fLastStopIsImplicit")

  /**
   * C++ original:
   * ```cpp
   * bool fLastStopIsImplicit
   * ```
   */
  private var fColorsAndOffsetsBitmap: Int = TODO("Initialize fColorsAndOffsetsBitmap")

  /**
   * C++ original:
   * ```cpp
   * mutable SkBitmap fColorsAndOffsetsBitmap
   * ```
   */
  private var fStorage: Int = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kInlineStopCount = 4
   * ```
   */
  private var fColorsAreOpaque: Boolean = TODO("Initialize fColorsAreOpaque")

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const final { return ShaderType::kGradientBase; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGradientBaseShader::isOpaque() const {
   *     return fColorsAreOpaque && (this->getTileMode() != SkTileMode::kDecal);
   * }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool interpolateInPremul() const {
   *         return fInterpolation.fInPremul == Interpolation::InPremul::kYes;
   *     }
   * ```
   */
  public fun interpolateInPremul(): Boolean {
    TODO("Implement interpolateInPremul")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& getGradientMatrix() const { return fPtsToUnit; }
   * ```
   */
  public fun getGradientMatrix(): Int {
    TODO("Implement getGradientMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getColorCount() const { return fColorCount; }
   * ```
   */
  public fun getColorCount(): Int {
    TODO("Implement getColorCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const float* getPositions() const { return fPositions; }
   * ```
   */
  public fun getPositions(): Float {
    TODO("Implement getPositions")
  }

  /**
   * C++ original:
   * ```cpp
   * const Interpolation& getInterpolation() const { return fInterpolation; }
   * ```
   */
  public fun getInterpolation(): Int {
    TODO("Implement getInterpolation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGradientBaseShader::flatten(SkWriteBuffer& buffer) const {
   *     uint32_t flags = 0;
   *     if (fPositions) {
   *         flags |= kHasPosition_GSF;
   *     }
   *     sk_sp<SkData> colorSpaceData = fColorSpace ? fColorSpace->serialize() : nullptr;
   *     if (colorSpaceData) {
   *         flags |= kHasColorSpace_GSF;
   *     }
   *     if (fInterpolation.fInPremul == Interpolation::InPremul::kYes) {
   *         flags |= kInterpolationInPremul_GSF;
   *     }
   *     SkASSERT(static_cast<uint32_t>(fTileMode) <= kTileModeMask_GSF);
   *     flags |= ((uint32_t)fTileMode << kTileModeShift_GSF);
   *     SkASSERT(static_cast<uint32_t>(fInterpolation.fColorSpace) <= kInterpolationColorSpaceMask_GSF);
   *     flags |= ((uint32_t)fInterpolation.fColorSpace << kInterpolationColorSpaceShift_GSF);
   *     SkASSERT(static_cast<uint32_t>(fInterpolation.fHueMethod) <= kInterpolationHueMethodMask_GSF);
   *     flags |= ((uint32_t)fInterpolation.fHueMethod << kInterpolationHueMethodShift_GSF);
   *
   *     buffer.writeUInt(flags);
   *
   *     // If we injected implicit first/last stops at construction time, omit those when serializing:
   *     size_t colorCount = fColorCount;
   *     const SkColor4f* colors = fColors;
   *     const SkScalar* positions = fPositions;
   *     if (fFirstStopIsImplicit) {
   *         colorCount--;
   *         colors++;
   *         if (positions) {
   *             positions++;
   *         }
   *     }
   *     if (fLastStopIsImplicit) {
   *         colorCount--;
   *     }
   *
   *     buffer.writeColor4fArray({colors, colorCount});
   *     if (colorSpaceData) {
   *         buffer.writeDataAsByteArray(colorSpaceData.get());
   *     }
   *     if (positions) {
   *         buffer.writeScalarArray({positions, colorCount});
   *     }
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGradientBaseShader::commonAsAGradient(GradientInfo* info) const {
   *     if (info) {
   *         const int colorCount = SkToInt(fColorCount);
   *         if (info->fColorCount >= colorCount) {
   *             if (info->fColors) {
   *                 for (int i = 0; i < colorCount; ++i) {
   *                     info->fColors[i] = fColors[i];
   *                 }
   *             }
   *             if (info->fColorOffsets) {
   *                 for (int i = 0; i < colorCount; ++i) {
   *                     info->fColorOffsets[i] = this->getPos(i);
   *                 }
   *             }
   *         }
   *         info->fColorCount = colorCount;
   *         info->fTileMode = fTileMode;
   *         info->fPremulInterp = this->interpolateInPremul();
   *     }
   * }
   * ```
   */
  protected fun commonAsAGradient(info: GradientInfo?) {
    TODO("Implement commonAsAGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGradientBaseShader::onAsLuminanceColor(SkColor4f* lum) const {
   *     // We just compute an average color. There are several things we could do better:
   *     // 1) We already have a different average_gradient_color helper later in this file, that weights
   *     //    contribution by the relative size of each band.
   *     // 2) Colors should be converted to some standard color space! These could be in any space.
   *     // 3) Do we want to average in the source space, sRGB, or some linear space?
   *     SkColor4f color{0, 0, 0, 1};
   *     for (size_t i = 0; i < fColorCount; ++i) {
   *         color.fR += fColors[i].fR;
   *         color.fG += fColors[i].fG;
   *         color.fB += fColors[i].fB;
   *     }
   *     const float scale = 1.0f / fColorCount;
   *     color.fR *= scale;
   *     color.fG *= scale;
   *     color.fB *= scale;
   *     *lum = color;
   *     return true;
   * }
   * ```
   */
  protected override fun onAsLuminanceColor(lum: UndefinedSkColor4f?): Boolean {
    TODO("Implement onAsLuminanceColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGradientBaseShader::appendStages(const SkStageRec& rec,
   *                                         const SkShaders::MatrixRec& mRec) const {
   *     SkRasterPipeline* p = rec.fPipeline;
   *     SkArenaAlloc* alloc = rec.fAlloc;
   *     SkRasterPipelineContexts::DecalTileCtx* decal_ctx = nullptr;
   *
   *     std::optional<SkShaders::MatrixRec> newMRec = mRec.apply(rec, fPtsToUnit);
   *     if (!newMRec.has_value()) {
   *         return false;
   *     }
   *
   *     SkRasterPipeline_<256> postPipeline;
   *
   *     this->appendGradientStages(alloc, p, &postPipeline);
   *
   *     switch (fTileMode) {
   *         case SkTileMode::kMirror:
   *             p->append(SkRasterPipelineOp::mirror_x_1);
   *             break;
   *         case SkTileMode::kRepeat:
   *             p->append(SkRasterPipelineOp::repeat_x_1);
   *             break;
   *         case SkTileMode::kDecal:
   *             decal_ctx = alloc->make<SkRasterPipelineContexts::DecalTileCtx>();
   *             decal_ctx->limit_x = SkBits2Float(SkFloat2Bits(1.0f) + 1);
   *             // reuse mask + limit_x stage, or create a custom decal_1 that just stores the mask
   *             p->append(SkRasterPipelineOp::decal_x, decal_ctx);
   *             [[fallthrough]];
   *
   *         case SkTileMode::kClamp:
   *             if (!fPositions) {
   *                 // We clamp only when the stops are evenly spaced.
   *                 // If not, there may be hard stops, and clamping ruins hard stops at 0 and/or 1.
   *                 // In that case, we must make sure we're using the general "gradient" stage,
   *                 // which is the only stage that will correctly handle unclamped t.
   *                 p->append(SkRasterPipelineOp::clamp_x_1);
   *             }
   *             break;
   *     }
   *
   *     // Transform all of the colors to destination color space, possibly premultiplied
   *     SkColor4fXformer xformedColors(this, rec.fDstCS);
   *     AppendGradientFillStages(p, alloc,
   *                              xformedColors.fColors.begin(),
   *                              xformedColors.fPositions,
   *                              xformedColors.fColors.size());
   *     AppendInterpolatedToDstStages(p, alloc, fColorsAreOpaque, fInterpolation,
   *                                   xformedColors.fIntermediateColorSpace.get(), rec.fDstCS);
   *
   *     if (decal_ctx) {
   *         p->append(SkRasterPipelineOp::check_decal_mask, decal_ctx);
   *     }
   *
   *     p->extend(postPipeline);
   *
   *     return true;
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void appendGradientStages(SkArenaAlloc* alloc,
   *                                       SkRasterPipeline* tPipeline,
   *                                       SkRasterPipeline* postPipeline) const = 0
   * ```
   */
  protected abstract fun appendGradientStages(
    alloc: SkArenaAlloc?,
    tPipeline: SkRasterPipeline?,
    postPipeline: SkRasterPipeline?,
  )

  /**
   * C++ original:
   * ```cpp
   * float getPos(size_t i) const {
   *         SkASSERT(i < fColorCount);
   *         return fPositions ? fPositions[i] : SkIntToScalar(i) / (fColorCount - 1);
   *     }
   * ```
   */
  public fun getPos(i: ULong): Float {
    TODO("Implement getPos")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor getLegacyColor(size_t i) const {
   *         SkASSERT(i < fColorCount);
   *         return fColors[i].toSkColor();
   *     }
   * ```
   */
  public fun getLegacyColor(i: ULong): Int {
    TODO("Implement getLegacyColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkColor4f> colors() const { return {fColors, fColorCount}; }
   * ```
   */
  public fun colors(): Int {
    TODO("Implement colors")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> positions() const {
   *         return {fPositions, fPositions ? fColorCount : 0};
   *     }
   * ```
   */
  public fun positions(): Int {
    TODO("Implement positions")
  }

  /**
   * C++ original:
   * ```cpp
   * bool firstStopIsImplicit() const { return fFirstStopIsImplicit; }
   * ```
   */
  public fun firstStopIsImplicit(): Boolean {
    TODO("Implement firstStopIsImplicit")
  }

  /**
   * C++ original:
   * ```cpp
   * bool lastStopIsImplicit() const { return fLastStopIsImplicit; }
   * ```
   */
  public fun lastStopIsImplicit(): Boolean {
    TODO("Implement lastStopIsImplicit")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkColorSpace>& colorSpace() const { return fColorSpace; }
   * ```
   */
  public fun colorSpace(): Int {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * const Interpolation& interpolation() const { return fInterpolation; }
   * ```
   */
  public fun interpolation(): Int {
    TODO("Implement interpolation")
  }

  /**
   * C++ original:
   * ```cpp
   * bool colorsAreOpaque() const { return fColorsAreOpaque; }
   * ```
   */
  public fun colorsAreOpaque(): Boolean {
    TODO("Implement colorsAreOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode getTileMode() const { return fTileMode; }
   * ```
   */
  public fun getTileMode(): SkTileMode {
    TODO("Implement getTileMode")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap& cachedBitmap() const { return fColorsAndOffsetsBitmap; }
   * ```
   */
  public fun cachedBitmap(): Int {
    TODO("Implement cachedBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCachedBitmap(SkBitmap b) const { fColorsAndOffsetsBitmap = b; }
   * ```
   */
  public fun setCachedBitmap(b: SkBitmap) {
    TODO("Implement setCachedBitmap")
  }

  public companion object {
    public val kDegenerateThreshold: Int = TODO("Initialize kDegenerateThreshold")

    private val kInlineStopCount: Int = TODO("Initialize kInlineStopCount")

    private val kInlineStorageSize: Int = TODO("Initialize kInlineStorageSize")

    /**
     * C++ original:
     * ```cpp
     * bool SkGradientBaseShader::ValidGradient(SkSpan<const SkColor4f> colors,
     *                                          SkTileMode tileMode,
     *                                          const Interpolation& interpolation) {
     *     return colors.data() && !colors.empty() && (unsigned)tileMode < kSkTileModeCount &&
     *            (unsigned)interpolation.fColorSpace < Interpolation::kColorSpaceCount &&
     *            (unsigned)interpolation.fHueMethod < Interpolation::kHueMethodCount;
     * }
     * ```
     */
    public fun validGradient(
      colors: SkSpan<SkColor4f_>,
      tileMode: SkTileMode,
      interpolation: Interpolation,
    ): Boolean {
      TODO("Implement validGradient")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkGradientBaseShader::MakeDegenerateGradient(const SkGradient::Colors& c) {
     *     switch (c.tileMode()) {
     *         case SkTileMode::kDecal:
     *             // normally this would reject the area outside of the interpolation region, so since
     *             // inside region is empty when the radii are equal, the entire draw region is empty
     *             return SkShaders::Empty();
     *         case SkTileMode::kRepeat:
     *         case SkTileMode::kMirror:
     *             // repeat and mirror are treated the same: the border colors are never visible,
     *             // but approximate the final color as infinite repetitions of the colors, so
     *             // it can be represented as the average color of the gradient.
     *             return SkShaders::Color(average_gradient_color(c.colors(), c.positions()),
     *                                     c.colorSpace());
     *         case SkTileMode::kClamp:
     *             // Depending on how the gradient shape degenerates, there may be a more specialized
     *             // fallback representation for the factories to use, but this is a reasonable default.
     *             return SkShaders::Color(c.colors().back(), c.colorSpace());
     *     }
     *     SkDEBUGFAIL("Should not be reached");
     *     return nullptr;
     * }
     * ```
     */
    public fun makeDegenerateGradient(c: SkGradient.Colors): Int {
      TODO("Implement makeDegenerateGradient")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGradientBaseShader::AppendGradientFillStages(SkRasterPipeline* p,
     *                                                     SkArenaAlloc* alloc,
     *                                                     const SkPMColor4f* pmColors,
     *                                                     const SkScalar* positions,
     *                                                     int count) {
     *     // The two-stop case with stops at 0 and 1.
     *     if (count == 2 && positions == nullptr) {
     *         const SkPMColor4f c_l = pmColors[0], c_r = pmColors[1];
     *
     *         auto ctx = alloc->make<SkRasterPipelineContexts::EvenlySpaced2StopGradientCtx>();
     *         (skvx::float4::Load(c_r.vec()) - skvx::float4::Load(c_l.vec())).store(ctx->factor);
     *         (skvx::float4::Load(c_l.vec())).store(ctx->bias);
     *
     *         p->append(SkRasterPipelineOp::evenly_spaced_2_stop_gradient, ctx);
     *         return;
     *     }
     *     // Linear gradients with evenly spaced stops involve doing calculations to interpolate
     *     // between color n and color n+1 based on t (in range [0.0,1.0]).
     *     //   color_n * (t - t_n) / gap_n + color_{n+1} * (t_{n+1} - t) / gap_n
     *     // We could just stick the colors and the gaps calculation in RP and do this calculation,
     *     // but instead we can precompute things to make the RP calculation simpler and faster.
     *     // For each gap, we calculate four linear equations in the form y = m*x + b, or rather
     *     //  color_channel = factor * t + bias
     *     // We do this pre-computation in init_stop_evenly and init_stop_pos.
     *
     *     auto* ctx = alloc->make<SkRasterPipelineContexts::GradientCtx>();
     *
     *     // Allocate at least enough for the AVX2 gather from a YMM register.
     *     constexpr int kMaxRegisterSize = 8;
     *
     *     // There are n - 1 gaps between n colors plus 2 regions to the left and right
     *     // of the gradient to account for colors. For evenly spaced gradients, we cheat
     *     // and skip the left gap, using one block of floats unused.
     *     const size_t factorBiasFloats = std::max(count + 1, kMaxRegisterSize);
     *     const size_t tsForArbitraryStops = count + 1;
     *     using SkRasterPipelineContexts::kRGBAChannels;
     *
     *     // We need space for all factors and biases, and while we are at it, some space
     *     // if we need to include the arbitrary stops.
     *     const size_t toAlloc = 2 * kRGBAChannels * factorBiasFloats + tsForArbitraryStops;
     *     float* gradientCtxBuffer = alloc->makeArray<float>(toAlloc);
     *     for (size_t i = 0; i < kRGBAChannels; i++) {
     *         ctx->factors[i] = gradientCtxBuffer;
     *         gradientCtxBuffer += factorBiasFloats;
     *         ctx->biases[i] = gradientCtxBuffer;
     *         gradientCtxBuffer += factorBiasFloats;
     *     }
     *
     *     if (positions == nullptr) {
     *         // Handle evenly distributed stops.
     *
     *         size_t stopCount = count;
     *         float gapCount = stopCount - 1;
     *
     *         SkPMColor4f c_l = pmColors[0];
     *         for (size_t i = 0; i < gapCount; i++) {
     *             SkPMColor4f c_r = pmColors[i + 1];
     *             init_stop_evenly(ctx, gapCount, i, c_l, c_r);
     *             c_l = c_r;
     *         }
     *         add_const_color(ctx, stopCount - 1, c_l);
     *
     *         ctx->stopCount = stopCount;
     *         p->append(SkRasterPipelineOp::evenly_spaced_gradient, ctx);
     *         return;
     *     }
     *
     *     // Handle arbitrary stops.
     *     ctx->ts = gradientCtxBuffer;
     *
     *     // Remove the default stops inserted by SkGradientBaseShader::SkGradientBaseShader
     *     // because they are naturally handled by the search method.
     *     int firstStop;
     *     int lastStop;
     *     if (count > 2) {
     *         firstStop = pmColors[0] != pmColors[1] ? 0 : 1;
     *         lastStop = pmColors[count - 2] != pmColors[count - 1] ? count - 1 : count - 2;
     *     } else {
     *         firstStop = 0;
     *         lastStop = 1;
     *     }
     *
     *     size_t stopCount = 0;
     *     float t_l = positions[firstStop];
     *     SkPMColor4f c_l = pmColors[firstStop];
     *     add_const_color(ctx, stopCount++, c_l);
     *
     *     for (int i = firstStop; i < lastStop; i++) {
     *         float t_r = positions[i + 1];
     *         SkPMColor4f c_r = pmColors[i + 1];
     *         SkASSERT(t_l <= t_r);
     *         if (t_l < t_r) {
     *             float c_scale = sk_ieee_float_divide(1, t_r - t_l);
     *             if (SkIsFinite(c_scale)) {
     *                 init_stop_pos(ctx, stopCount, t_l, c_scale, c_l, c_r);
     *                 stopCount += 1;
     *             }
     *         }
     *         t_l = t_r;
     *         c_l = c_r;
     *     }
     *
     *     ctx->ts[stopCount] = t_l;
     *     add_const_color(ctx, stopCount++, c_l);
     *
     *     ctx->stopCount = stopCount;
     *     p->append(SkRasterPipelineOp::gradient, ctx);
     * }
     * ```
     */
    public fun appendGradientFillStages(
      p: SkRasterPipeline?,
      alloc: SkArenaAlloc?,
      colors: SkPMColor4f?,
      positions: SkScalar?,
      count: Int,
    ) {
      TODO("Implement appendGradientFillStages")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkGradientBaseShader::AppendInterpolatedToDstStages(SkRasterPipeline* p,
     *                                                          SkArenaAlloc* alloc,
     *                                                          bool colorsAreOpaque,
     *                                                          const Interpolation& interpolation,
     *                                                          const SkColorSpace* intermediateColorSpace,
     *                                                          const SkColorSpace* dstColorSpace) {
     *     using ColorSpace = Interpolation::ColorSpace;
     *     bool colorIsPremul = static_cast<bool>(interpolation.fInPremul);
     *
     *     // If we interpolated premul colors in any of the special color spaces, we need to unpremul
     *     if (colorIsPremul && !colorsAreOpaque) {
     *         switch (interpolation.fColorSpace) {
     *             case ColorSpace::kLab:
     *             case ColorSpace::kOKLab:
     *             case ColorSpace::kOKLabGamutMap:
     *                 p->append(SkRasterPipelineOp::unpremul);
     *                 colorIsPremul = false;
     *                 break;
     *             case ColorSpace::kLCH:
     *             case ColorSpace::kOKLCH:
     *             case ColorSpace::kOKLCHGamutMap:
     *             case ColorSpace::kHSL:
     *             case ColorSpace::kHWB:
     *                 p->append(SkRasterPipelineOp::unpremul_polar);
     *                 colorIsPremul = false;
     *                 break;
     *             default:
     *                 break;
     *         }
     *     }
     *
     *     // Convert colors in exotic spaces back to their intermediate SkColorSpace
     *     switch (interpolation.fColorSpace) {
     *         case ColorSpace::kLab:   p->append(SkRasterPipelineOp::css_lab_to_xyz);           break;
     *         case ColorSpace::kOKLab: p->append(SkRasterPipelineOp::css_oklab_to_linear_srgb); break;
     *         case ColorSpace::kOKLabGamutMap:
     *             p->append(SkRasterPipelineOp::css_oklab_gamut_map_to_linear_srgb);
     *             break;
     *         case ColorSpace::kLCH:   p->append(SkRasterPipelineOp::css_hcl_to_lab);
     *                                  p->append(SkRasterPipelineOp::css_lab_to_xyz);           break;
     *         case ColorSpace::kOKLCH: p->append(SkRasterPipelineOp::css_hcl_to_lab);
     *                                  p->append(SkRasterPipelineOp::css_oklab_to_linear_srgb); break;
     *         case ColorSpace::kOKLCHGamutMap:
     *             p->append(SkRasterPipelineOp::css_hcl_to_lab);
     *             p->append(SkRasterPipelineOp::css_oklab_gamut_map_to_linear_srgb);
     *             break;
     *         case ColorSpace::kHSL:   p->append(SkRasterPipelineOp::css_hsl_to_srgb);          break;
     *         case ColorSpace::kHWB:   p->append(SkRasterPipelineOp::css_hwb_to_srgb);          break;
     *         default: break;
     *     }
     *
     *     // Now transform from intermediate to destination color space.
     *     // See comments in GrGradientShader.cpp about the decisions here.
     *     if (!dstColorSpace) {
     *         dstColorSpace = sk_srgb_singleton();
     *     }
     *     SkAlphaType intermediateAlphaType = colorIsPremul ? kPremul_SkAlphaType : kUnpremul_SkAlphaType;
     *     // TODO(skbug.com/40044213): Get dst alpha type correctly
     *     SkAlphaType dstAlphaType = kPremul_SkAlphaType;
     *
     *     if (colorsAreOpaque) {
     *         intermediateAlphaType = dstAlphaType = kUnpremul_SkAlphaType;
     *     }
     *
     *     alloc->make<SkColorSpaceXformSteps>(
     *                  intermediateColorSpace, intermediateAlphaType, dstColorSpace, dstAlphaType)
     *             ->apply(p);
     * }
     * ```
     */
    public fun appendInterpolatedToDstStages(
      p: SkRasterPipeline?,
      alloc: SkArenaAlloc?,
      colorsAreOpaque: Boolean,
      interpolation: Interpolation,
      intermediateColorSpace: SkColorSpace?,
      dstColorSpace: SkColorSpace?,
    ) {
      TODO("Implement appendInterpolatedToDstStages")
    }
  }
}

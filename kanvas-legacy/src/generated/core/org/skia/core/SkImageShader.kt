package org.skia.core

import kotlin.Boolean
import kotlin.Float
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.memory.SkArenaAlloc
import org.skia.tests.ShaderType
import undefined.ContextRec

/**
 * C++ original:
 * ```cpp
 * class SkImageShader : public SkShaderBase {
 * public:
 *     static sk_sp<SkShader> Make(sk_sp<SkImage>,
 *                                 SkTileMode tmx,
 *                                 SkTileMode tmy,
 *                                 const SkSamplingOptions&,
 *                                 const SkMatrix* localMatrix,
 *                                 bool clampAsIfUnpremul = false);
 *
 *     static sk_sp<SkShader> MakeRaw(sk_sp<SkImage>,
 *                                    SkTileMode tmx,
 *                                    SkTileMode tmy,
 *                                    const SkSamplingOptions&,
 *                                    const SkMatrix* localMatrix);
 *
 *     // TODO(skbug.com/40043877): Requires SkImage to be texture backed, and created SkShader can only
 *     // be used on GPU-backed surfaces.
 *     static sk_sp<SkShader> MakeSubset(sk_sp<SkImage>,
 *                                       const SkRect& subset,
 *                                       SkTileMode tmx,
 *                                       SkTileMode tmy,
 *                                       const SkSamplingOptions&,
 *                                       const SkMatrix* localMatrix,
 *                                       bool clampAsIfUnpremul = false);
 *
 *     SkImageShader(sk_sp<SkImage>,
 *                   const SkRect& subset,
 *                   SkTileMode tmx, SkTileMode tmy,
 *                   const SkSamplingOptions&,
 *                   bool raw,
 *                   bool clampAsIfUnpremul);
 *
 *     bool isOpaque() const override;
 *
 *     ShaderType type() const override { return ShaderType::kImage; }
 *
 *     static SkM44 CubicResamplerMatrix(float B, float C);
 *
 *     SkTileMode tileModeX() const { return fTileModeX; }
 *     SkTileMode tileModeY() const { return fTileModeY; }
 *     sk_sp<SkImage> image() const { return fImage; }
 *     SkSamplingOptions sampling() const { return fSampling; }
 *     SkRect subset() const { return fSubset; }
 *     bool isRaw() const { return fRaw; }
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkImageShader)
 *
 *     void flatten(SkWriteBuffer&) const override;
 * #ifdef SK_ENABLE_LEGACY_SHADERCONTEXT
 *     Context* onMakeContext(const ContextRec&, SkArenaAlloc* storage) const override;
 * #endif
 *     SkImage* onIsAImage(SkMatrix*, SkTileMode*) const override;
 *
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 *     sk_sp<SkImage>          fImage;
 *     const SkSamplingOptions fSampling;
 *     const SkTileMode        fTileModeX;
 *     const SkTileMode        fTileModeY;
 *
 *     // TODO(skbug.com/40043877): This is only supported for GPU images currently.
 *     // If subset == (0,0,w,h) of the image, then no subset is applied. Subset will not be empty.
 *     const SkRect            fSubset;
 *
 *     const bool              fRaw;
 *     const bool              fClampAsIfUnpremul;
 *
 *     friend class SkShaderBase;
 *     using INHERITED = SkShaderBase;
 * }
 * ```
 */
public open class SkImageShader public constructor(
  img: SkSp<SkImage>,
  subset: SkRect,
  tmx: SkTileMode,
  tmy: SkTileMode,
  sampling: SkSamplingOptions,
  raw: Boolean,
  clampAsIfUnpremul: Boolean,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>          fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * const SkSamplingOptions fSampling
   * ```
   */
  private val fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * const SkTileMode        fTileModeX
   * ```
   */
  private val fTileModeX: SkTileMode = TODO("Initialize fTileModeX")

  /**
   * C++ original:
   * ```cpp
   * const SkTileMode        fTileModeY
   * ```
   */
  private val fTileModeY: SkTileMode = TODO("Initialize fTileModeY")

  /**
   * C++ original:
   * ```cpp
   * const SkRect            fSubset
   * ```
   */
  private val fSubset: SkRect = TODO("Initialize fSubset")

  /**
   * C++ original:
   * ```cpp
   * const bool              fRaw
   * ```
   */
  private val fRaw: Boolean = TODO("Initialize fRaw")

  /**
   * C++ original:
   * ```cpp
   * const bool              fClampAsIfUnpremul
   * ```
   */
  private val fClampAsIfUnpremul: Boolean = TODO("Initialize fClampAsIfUnpremul")

  /**
   * C++ original:
   * ```cpp
   * bool SkImageShader::isOpaque() const {
   *     return fImage->isOpaque() &&
   *            fTileModeX != SkTileMode::kDecal && fTileModeY != SkTileMode::kDecal;
   * }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kImage; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileModeX() const { return fTileModeX; }
   * ```
   */
  public fun tileModeX(): SkTileMode {
    TODO("Implement tileModeX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileModeY() const { return fTileModeY; }
   * ```
   */
  public fun tileModeY(): SkTileMode {
    TODO("Implement tileModeY")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> image() const { return fImage; }
   * ```
   */
  public fun image(): SkSp<SkImage> {
    TODO("Implement image")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions sampling() const { return fSampling; }
   * ```
   */
  public fun sampling(): SkSamplingOptions {
    TODO("Implement sampling")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect subset() const { return fSubset; }
   * ```
   */
  public fun subset(): SkRect {
    TODO("Implement subset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRaw() const { return fRaw; }
   * ```
   */
  public fun isRaw(): Boolean {
    TODO("Implement isRaw")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImageShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeUInt((unsigned)fTileModeX);
   *     buffer.writeUInt((unsigned)fTileModeY);
   *
   *     buffer.writeSampling(fSampling);
   *
   *     buffer.writeImage(fImage.get());
   *     SkASSERT(fClampAsIfUnpremul == false);
   *
   *     // TODO(skbug.com/40043877): Subset is not serialized yet; it's only used by special images so it
   *     // will never be written to an SKP.
   *     SkASSERT(!needs_subset(fImage.get(), fSubset));
   *
   *     buffer.writeBool(fRaw);
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::Context* SkImageShader::onMakeContext(const ContextRec& rec,
   *                                                     SkArenaAlloc* alloc) const {
   *     SkASSERT(!needs_subset(fImage.get(), fSubset)); // TODO(skbug.com/40043877)
   *     if (fImage->alphaType() == kUnpremul_SkAlphaType) {
   *         return nullptr;
   *     }
   *     if (fImage->colorType() != kN32_SkColorType) {
   *         return nullptr;
   *     }
   *     if (fTileModeX != fTileModeY) {
   *         return nullptr;
   *     }
   *     if (fTileModeX == SkTileMode::kDecal || fTileModeY == SkTileMode::kDecal) {
   *         return nullptr;
   *     }
   *
   *     SkSamplingOptions sampling = fSampling;
   *     if (sampling.isAniso()) {
   *         sampling = SkSamplingPriv::AnisoFallback(fImage->hasMipmaps());
   *     }
   *
   *     auto supported = [](const SkSamplingOptions& sampling) {
   *         const std::tuple<SkFilterMode,SkMipmapMode> supported[] = {
   *             {SkFilterMode::kNearest, SkMipmapMode::kNone},    // legacy None
   *             {SkFilterMode::kLinear,  SkMipmapMode::kNone},    // legacy Low
   *             {SkFilterMode::kLinear,  SkMipmapMode::kNearest}, // legacy Medium
   *         };
   *         for (auto [f, m] : supported) {
   *             if (sampling.filter == f && sampling.mipmap == m) {
   *                 return true;
   *             }
   *         }
   *         return false;
   *     };
   *     if (sampling.useCubic || !supported(sampling)) {
   *         return nullptr;
   *     }
   *
   *     // SkBitmapProcShader stores bitmap coordinates in a 16bit buffer,
   *     // so it can't handle bitmaps larger than 65535.
   *     //
   *     // We back off another bit to 32767 to make small amounts of
   *     // intermediate math safe, e.g. in
   *     //
   *     //     SkFixed fx = ...;
   *     //     fx = tile(fx + SK_Fixed1);
   *     //
   *     // we want to make sure (fx + SK_Fixed1) never overflows.
   *     if (fImage-> width() > 32767 ||
   *         fImage->height() > 32767) {
   *         return nullptr;
   *     }
   *
   *     auto inv = rec.fMatrixRec.totalInverse();
   *     if (!inv || !legacy_shader_can_handle(*inv)) {
   *         return nullptr;
   *     }
   *
   *     if (!rec.isLegacyCompatible(fImage->colorSpace())) {
   *         return nullptr;
   *     }
   *
   *     return SkBitmapProcLegacyShader::MakeContext(*this, fTileModeX, fTileModeY, sampling,
   *                                                  as_IB(fImage.get()), rec, alloc);
   * }
   * ```
   */
  public override fun onMakeContext(rec: ContextRec, storage: SkArenaAlloc?): Context {
    TODO("Implement onMakeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImage* SkImageShader::onIsAImage(SkMatrix* texM, SkTileMode xy[]) const {
   *     if (texM) {
   *         *texM = SkMatrix::I();
   *     }
   *     if (xy) {
   *         xy[0] = fTileModeX;
   *         xy[1] = fTileModeY;
   *     }
   *     return const_cast<SkImage*>(fImage.get());
   * }
   * ```
   */
  public override fun onIsAImage(texM: SkMatrix?, xy: SkTileMode?): SkImage {
    TODO("Implement onIsAImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec& mRec) const {
   *     SkASSERT(!needs_subset(fImage.get(), fSubset));  // TODO(skbug.com/40043877)
   *
   *     // We only support certain sampling options in stages so far
   *     auto sampling = fSampling;
   *     if (sampling.isAniso()) {
   *         sampling = SkSamplingPriv::AnisoFallback(fImage->hasMipmaps());
   *     }
   *
   *     SkRasterPipeline* p = rec.fPipeline;
   *     SkArenaAlloc* alloc = rec.fAlloc;
   *
   *     SkMatrix baseInv;
   *     // If the total matrix isn't valid then we will always access the base MIP level.
   *     if (mRec.totalMatrixIsValid()) {
   *         auto inv = mRec.totalInverse();
   *         if (!inv) {
   *             return false;
   *         }
   *         baseInv = *inv;
   *         baseInv.normalizePerspective();
   *     }
   *
   *     SkASSERT(!sampling.useCubic || sampling.mipmap == SkMipmapMode::kNone);
   *     auto* access = SkMipmapAccessor::Make(alloc, fImage.get(), baseInv, sampling.mipmap);
   *     if (!access) {
   *         return false;
   *     }
   *
   *     MipLevelHelper upper;
   *     std::tie(upper.pm, upper.inv) = access->level();
   *
   *     if (!sampling.useCubic) {
   *         // TODO: can tweak_sampling sometimes for cubic too when B=0
   *         if (mRec.totalMatrixIsValid()) {
   *             sampling = tweak_sampling(sampling, SkMatrix::Concat(upper.inv, baseInv));
   *         }
   *     }
   *
   *     if (!mRec.apply(rec, upper.inv)) {
   *         return false;
   *     }
   *
   *     upper.allocAndInit(alloc, sampling, fTileModeX, fTileModeY);
   *
   *     MipLevelHelper lower;
   *     SkRasterPipelineContexts::MipmapCtx* mipmapCtx = nullptr;
   *     float lowerWeight = access->lowerWeight();
   *     if (lowerWeight > 0) {
   *         std::tie(lower.pm, lower.inv) = access->lowerLevel();
   *         mipmapCtx = alloc->make<SkRasterPipelineContexts::MipmapCtx>();
   *         mipmapCtx->lowerWeight = lowerWeight;
   *         mipmapCtx->scaleX = static_cast<float>(lower.pm.width()) / upper.pm.width();
   *         mipmapCtx->scaleY = static_cast<float>(lower.pm.height()) / upper.pm.height();
   *
   *         lower.allocAndInit(alloc, sampling, fTileModeX, fTileModeY);
   *
   *         p->append(SkRasterPipelineOp::mipmap_linear_init, mipmapCtx);
   *     }
   *
   *     const bool decalBothAxes = fTileModeX == SkTileMode::kDecal && fTileModeY == SkTileMode::kDecal;
   *
   *     auto append_tiling_and_gather = [&](const MipLevelHelper* level) {
   *         if (decalBothAxes) {
   *             p->append(SkRasterPipelineOp::decal_x_and_y,  level->decalCtx);
   *         } else {
   *             switch (fTileModeX) {
   *                 case SkTileMode::kClamp: /* The gather_xxx stage will clamp for us. */
   *                     break;
   *                 case SkTileMode::kMirror:
   *                     p->append(SkRasterPipelineOp::mirror_x, level->limitX);
   *                     break;
   *                 case SkTileMode::kRepeat:
   *                     p->append(SkRasterPipelineOp::repeat_x, level->limitX);
   *                     break;
   *                 case SkTileMode::kDecal:
   *                     p->append(SkRasterPipelineOp::decal_x, level->decalCtx);
   *                     break;
   *             }
   *             switch (fTileModeY) {
   *                 case SkTileMode::kClamp: /* The gather_xxx stage will clamp for us. */
   *                     break;
   *                 case SkTileMode::kMirror:
   *                     p->append(SkRasterPipelineOp::mirror_y, level->limitY);
   *                     break;
   *                 case SkTileMode::kRepeat:
   *                     p->append(SkRasterPipelineOp::repeat_y, level->limitY);
   *                     break;
   *                 case SkTileMode::kDecal:
   *                     p->append(SkRasterPipelineOp::decal_y, level->decalCtx);
   *                     break;
   *             }
   *         }
   *
   *         void* ctx = level->gather;
   *         switch (level->pm.colorType()) {
   *             case kAlpha_8_SkColorType:      p->append(SkRasterPipelineOp::gather_a8,    ctx); break;
   *             case kA16_unorm_SkColorType:    p->append(SkRasterPipelineOp::gather_a16,   ctx); break;
   *             case kA16_float_SkColorType:    p->append(SkRasterPipelineOp::gather_af16,  ctx); break;
   *             case kRGB_565_SkColorType:      p->append(SkRasterPipelineOp::gather_565,   ctx); break;
   *             case kARGB_4444_SkColorType:    p->append(SkRasterPipelineOp::gather_4444,  ctx); break;
   *             case kR8G8_unorm_SkColorType:   p->append(SkRasterPipelineOp::gather_rg88,  ctx); break;
   *             case kR16_unorm_SkColorType:    p->append(SkRasterPipelineOp::gather_r16,   ctx); break;
   *             case kR16G16_unorm_SkColorType: p->append(SkRasterPipelineOp::gather_rg1616,ctx); break;
   *             case kR16G16_float_SkColorType: p->append(SkRasterPipelineOp::gather_rgf16, ctx); break;
   *             case kRGBA_8888_SkColorType:    p->append(SkRasterPipelineOp::gather_8888,  ctx); break;
   *
   *             case kRGBA_1010102_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_1010102, ctx);
   *                 break;
   *
   *             case kR16G16B16A16_unorm_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_16161616, ctx);
   *                 break;
   *
   *             case kRGBA_F16Norm_SkColorType:
   *             case kRGBA_F16_SkColorType:     p->append(SkRasterPipelineOp::gather_f16,   ctx); break;
   *             case kRGBA_F32_SkColorType:     p->append(SkRasterPipelineOp::gather_f32,   ctx); break;
   *             case kBGRA_10101010_XR_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_10101010_xr,  ctx);
   *                 p->append(SkRasterPipelineOp::swap_rb);
   *                 break;
   *             case kRGBA_10x6_SkColorType:    p->append(SkRasterPipelineOp::gather_10x6,  ctx); break;
   *
   *             case kGray_8_SkColorType:       p->append(SkRasterPipelineOp::gather_a8,    ctx);
   *                                             p->append(SkRasterPipelineOp::alpha_to_gray    ); break;
   *
   *             case kR8_unorm_SkColorType:     p->append(SkRasterPipelineOp::gather_a8,    ctx);
   *                                             p->append(SkRasterPipelineOp::alpha_to_red     ); break;
   *
   *             case kRGB_888x_SkColorType:     p->append(SkRasterPipelineOp::gather_8888,  ctx);
   *                                             p->append(SkRasterPipelineOp::force_opaque     ); break;
   *             case kRGB_F16F16F16x_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_f16,  ctx);
   *                 p->append(SkRasterPipelineOp::force_opaque);
   *                 break;
   *             case kBGRA_1010102_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_1010102, ctx);
   *                 p->append(SkRasterPipelineOp::swap_rb);
   *                 break;
   *
   *             case kRGB_101010x_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_1010102, ctx);
   *                 p->append(SkRasterPipelineOp::force_opaque);
   *                 break;
   *
   *             case kBGR_101010x_XR_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_1010102_xr, ctx);
   *                 p->append(SkRasterPipelineOp::force_opaque);
   *                 p->append(SkRasterPipelineOp::swap_rb);
   *                 break;
   *
   *             case kBGR_101010x_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_1010102, ctx);
   *                 p->append(SkRasterPipelineOp::force_opaque);
   *                 p->append(SkRasterPipelineOp::swap_rb);
   *                 break;
   *
   *             case kBGRA_8888_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_8888, ctx);
   *                 p->append(SkRasterPipelineOp::swap_rb);
   *                 break;
   *
   *             case kSRGBA_8888_SkColorType:
   *                 p->append(SkRasterPipelineOp::gather_8888, ctx);
   *                 p->appendTransferFunction(*skcms_sRGB_TransferFunction());
   *                 break;
   *
   *             case kUnknown_SkColorType: SkASSERT(false);
   *         }
   *         if (level->decalCtx) {
   *             p->append(SkRasterPipelineOp::check_decal_mask, level->decalCtx);
   *         }
   *     };
   *
   *     auto append_misc = [&] {
   *         SkColorSpace* cs = upper.pm.colorSpace();
   *         SkAlphaType   at = upper.pm.alphaType();
   *
   *         // Color for alpha-only images comes from the paint (already converted to dst color space).
   *         // If we were sampled by a runtime effect, the paint color was replaced with transparent
   *         // black, so this tinting is effectively suppressed. See also: RuntimeEffectRPCallbacks
   *         if (SkColorTypeIsAlphaOnly(upper.pm.colorType()) && !fRaw) {
   *             p->appendSetRGB(alloc, rec.fPaintColor);
   *
   *             cs = rec.fDstCS;
   *             at = kUnpremul_SkAlphaType;
   *         }
   *
   *         // Bicubic filtering naturally produces out of range values on both sides of [0,1].
   *         if (sampling.useCubic) {
   *             p->append(at == kUnpremul_SkAlphaType || fClampAsIfUnpremul
   *                           ? SkRasterPipelineOp::clamp_01
   *                           : SkRasterPipelineOp::clamp_gamut);
   *         }
   *
   *         // Transform color space and alpha type to match shader convention (dst CS, premul alpha).
   *         if (!fRaw) {
   *             alloc->make<SkColorSpaceXformSteps>(cs, at, rec.fDstCS, kPremul_SkAlphaType)->apply(p);
   *         }
   *
   *         return true;
   *     };
   *
   *     // Check for fast-path stages.
   *     // TODO: Could we use the fast-path stages for each level when doing linear mipmap filtering?
   *     SkColorType ct = upper.pm.colorType();
   *     if (true
   *         && (ct == kRGBA_8888_SkColorType || ct == kBGRA_8888_SkColorType)
   *         && !sampling.useCubic && sampling.filter == SkFilterMode::kLinear
   *         && sampling.mipmap != SkMipmapMode::kLinear
   *         && fTileModeX == SkTileMode::kClamp && fTileModeY == SkTileMode::kClamp) {
   *         // Check bounding box of points we will sample to see if we can use lowp
   *         // and not over/under flow.
   *         bool shouldUseHighPBilerp = false;
   *         if (!rec.fDstBounds.isEmpty()) {
   *             std::array<SkPoint, 4> quad = rec.fDstBounds.toQuad();
   *             baseInv.mapPoints(quad);
   *             SkRect deviceImageSpace;
   *             deviceImageSpace.setBounds(quad);
   *             for (float val : SkSpan<const float>(deviceImageSpace.asScalars(), 4)) {
   *                 if (val > INT16_MAX || val < INT16_MIN || !std::isfinite(val)) {
   *                     shouldUseHighPBilerp = true;
   *                     break;
   *                 }
   *             }
   *         }
   *
   *         if (shouldUseHighPBilerp) {
   *             p->append(SkRasterPipelineOp::bilerp_clamp_8888_force_highp, upper.gather);
   *         } else {
   *             p->append(SkRasterPipelineOp::bilerp_clamp_8888, upper.gather);
   *         }
   *
   *         if (ct == kBGRA_8888_SkColorType) {
   *             p->append(SkRasterPipelineOp::swap_rb);
   *         }
   *         return append_misc();
   *     }
   *     if (true
   *         && (ct == kRGBA_8888_SkColorType || ct == kBGRA_8888_SkColorType)
   *         && sampling.useCubic
   *         && fTileModeX == SkTileMode::kClamp && fTileModeY == SkTileMode::kClamp) {
   *
   *         p->append(SkRasterPipelineOp::bicubic_clamp_8888, upper.gather);
   *         if (ct == kBGRA_8888_SkColorType) {
   *             p->append(SkRasterPipelineOp::swap_rb);
   *         }
   *         return append_misc();
   *     }
   *
   *     // This context can be shared by both levels when doing linear mipmap filtering
   *     SkRasterPipelineContexts::SamplerCtx* sampler =
   *             alloc->make<SkRasterPipelineContexts::SamplerCtx>();
   *
   *     auto sample = [&](SkRasterPipelineOp setup_x,
   *                       SkRasterPipelineOp setup_y,
   *                       const MipLevelHelper* level) {
   *         p->append(setup_x, sampler);
   *         p->append(setup_y, sampler);
   *         append_tiling_and_gather(level);
   *         p->append(SkRasterPipelineOp::accumulate, sampler);
   *     };
   *
   *     auto sample_level = [&](const MipLevelHelper* level) {
   *         if (sampling.useCubic) {
   *             CubicResamplerMatrix(sampling.cubic.B, sampling.cubic.C).getColMajor(sampler->weights);
   *
   *             p->append(SkRasterPipelineOp::bicubic_setup, sampler);
   *
   *             sample(SkRasterPipelineOp::bicubic_n3x, SkRasterPipelineOp::bicubic_n3y, level);
   *             sample(SkRasterPipelineOp::bicubic_n1x, SkRasterPipelineOp::bicubic_n3y, level);
   *             sample(SkRasterPipelineOp::bicubic_p1x, SkRasterPipelineOp::bicubic_n3y, level);
   *             sample(SkRasterPipelineOp::bicubic_p3x, SkRasterPipelineOp::bicubic_n3y, level);
   *
   *             sample(SkRasterPipelineOp::bicubic_n3x, SkRasterPipelineOp::bicubic_n1y, level);
   *             sample(SkRasterPipelineOp::bicubic_n1x, SkRasterPipelineOp::bicubic_n1y, level);
   *             sample(SkRasterPipelineOp::bicubic_p1x, SkRasterPipelineOp::bicubic_n1y, level);
   *             sample(SkRasterPipelineOp::bicubic_p3x, SkRasterPipelineOp::bicubic_n1y, level);
   *
   *             sample(SkRasterPipelineOp::bicubic_n3x, SkRasterPipelineOp::bicubic_p1y, level);
   *             sample(SkRasterPipelineOp::bicubic_n1x, SkRasterPipelineOp::bicubic_p1y, level);
   *             sample(SkRasterPipelineOp::bicubic_p1x, SkRasterPipelineOp::bicubic_p1y, level);
   *             sample(SkRasterPipelineOp::bicubic_p3x, SkRasterPipelineOp::bicubic_p1y, level);
   *
   *             sample(SkRasterPipelineOp::bicubic_n3x, SkRasterPipelineOp::bicubic_p3y, level);
   *             sample(SkRasterPipelineOp::bicubic_n1x, SkRasterPipelineOp::bicubic_p3y, level);
   *             sample(SkRasterPipelineOp::bicubic_p1x, SkRasterPipelineOp::bicubic_p3y, level);
   *             sample(SkRasterPipelineOp::bicubic_p3x, SkRasterPipelineOp::bicubic_p3y, level);
   *
   *             p->append(SkRasterPipelineOp::move_dst_src);
   *         } else if (sampling.filter == SkFilterMode::kLinear) {
   *             p->append(SkRasterPipelineOp::bilinear_setup, sampler);
   *
   *             sample(SkRasterPipelineOp::bilinear_nx, SkRasterPipelineOp::bilinear_ny, level);
   *             sample(SkRasterPipelineOp::bilinear_px, SkRasterPipelineOp::bilinear_ny, level);
   *             sample(SkRasterPipelineOp::bilinear_nx, SkRasterPipelineOp::bilinear_py, level);
   *             sample(SkRasterPipelineOp::bilinear_px, SkRasterPipelineOp::bilinear_py, level);
   *
   *             p->append(SkRasterPipelineOp::move_dst_src);
   *         } else {
   *             append_tiling_and_gather(level);
   *         }
   *     };
   *
   *     sample_level(&upper);
   *
   *     if (mipmapCtx) {
   *         p->append(SkRasterPipelineOp::mipmap_linear_update, mipmapCtx);
   *         sample_level(&lower);
   *         p->append(SkRasterPipelineOp::mipmap_linear_finish, mipmapCtx);
   *     }
   *
   *     return append_misc();
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkImageShader::CreateProc(SkReadBuffer& buffer) {
   *     auto tmx = buffer.read32LE<SkTileMode>(SkTileMode::kLastTileMode);
   *     auto tmy = buffer.read32LE<SkTileMode>(SkTileMode::kLastTileMode);
   *
   *     SkSamplingOptions sampling;
   *     bool readSampling = true;
   *     if (buffer.isVersionLT(SkPicturePriv::kNoFilterQualityShaders_Version) &&
   *         !buffer.readBool() /* legacy has_sampling */)
   *     {
   *         readSampling = false;
   *         // we just default to Nearest in sampling
   *     }
   *     if (readSampling) {
   *         sampling = buffer.readSampling();
   *     }
   *
   *     SkMatrix localMatrix;
   *     if (buffer.isVersionLT(SkPicturePriv::Version::kNoShaderLocalMatrix)) {
   *         buffer.readMatrix(&localMatrix);
   *     }
   *     sk_sp<SkImage> img = buffer.readImage();
   *     if (!img) {
   *         return nullptr;
   *     }
   *
   *     bool raw = buffer.isVersionLT(SkPicturePriv::Version::kRawImageShaders) ? false
   *                                                                             : buffer.readBool();
   *
   *     // TODO(skbug.com/40043877): Subset is not serialized yet; it's only used by special images so it
   *     // will never be written to an SKP.
   *
   *     return raw ? SkImageShader::MakeRaw(std::move(img), tmx, tmy, sampling, &localMatrix)
   *                : SkImageShader::Make(std::move(img), tmx, tmy, sampling, &localMatrix);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkImageShader::Make(sk_sp<SkImage> image,
     *                                     SkTileMode tmx, SkTileMode tmy,
     *                                     const SkSamplingOptions& options,
     *                                     const SkMatrix* localMatrix,
     *                                     bool clampAsIfUnpremul) {
     *     SkRect subset = image ? SkRect::Make(image->dimensions()) : SkRect::MakeEmpty();
     *     return MakeSubset(std::move(image), subset, tmx, tmy, options, localMatrix, clampAsIfUnpremul);
     * }
     * ```
     */
    public fun make(
      image: SkSp<SkImage>,
      tmx: SkTileMode,
      tmy: SkTileMode,
      options: SkSamplingOptions,
      localMatrix: SkMatrix?,
      clampAsIfUnpremul: Boolean = TODO(),
    ): SkSp<SkShader> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkImageShader::MakeRaw(sk_sp<SkImage> image,
     *                                        SkTileMode tmx, SkTileMode tmy,
     *                                        const SkSamplingOptions& options,
     *                                        const SkMatrix* localMatrix) {
     *     if (options.useCubic) {
     *         return nullptr;
     *     }
     *     if (!image) {
     *         return SkShaders::Empty();
     *     }
     *     auto subset = SkRect::Make(image->dimensions());
     *
     *     sk_sp<SkShader> s = sk_make_sp<SkImageShader>(image,
     *                                                   subset,
     *                                                   tmx, tmy,
     *                                                   options,
     *                                                   /*raw=*/true,
     *                                                   /*clampAsIfUnpremul=*/false);
     *     return s->makeWithLocalMatrix(localMatrix ? *localMatrix : SkMatrix::I());
     * }
     * ```
     */
    public fun makeRaw(
      image: SkSp<SkImage>,
      tmx: SkTileMode,
      tmy: SkTileMode,
      options: SkSamplingOptions,
      localMatrix: SkMatrix?,
    ): SkSp<SkShader> {
      TODO("Implement makeRaw")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkImageShader::MakeSubset(sk_sp<SkImage> image,
     *                                           const SkRect& subset,
     *                                           SkTileMode tmx, SkTileMode tmy,
     *                                           const SkSamplingOptions& options,
     *                                           const SkMatrix* localMatrix,
     *                                           bool clampAsIfUnpremul) {
     *     auto is_unit = [](float x) {
     *         return x >= 0 && x <= 1;
     *     };
     *     if (options.useCubic) {
     *         if (!is_unit(options.cubic.B) || !is_unit(options.cubic.C)) {
     *             return nullptr;
     *         }
     *     }
     *     if (!image || subset.isEmpty()) {
     *         return SkShaders::Empty();
     *     }
     *
     *     // Validate subset and check if we can drop it
     *     if (!SkRect::Make(image->bounds()).contains(subset)) {
     *         return nullptr;
     *     }
     *
     *     sk_sp<SkShader> s = sk_make_sp<SkImageShader>(std::move(image),
     *                                                   subset,
     *                                                   tmx, tmy,
     *                                                   options,
     *                                                   /*raw=*/false,
     *                                                   clampAsIfUnpremul);
     *     return s->makeWithLocalMatrix(localMatrix ? *localMatrix : SkMatrix::I());
     * }
     * ```
     */
    public fun makeSubset(
      image: SkSp<SkImage>,
      subset: SkRect,
      tmx: SkTileMode,
      tmy: SkTileMode,
      options: SkSamplingOptions,
      localMatrix: SkMatrix?,
      clampAsIfUnpremul: Boolean = TODO(),
    ): SkSp<SkShader> {
      TODO("Implement makeSubset")
    }

    /**
     * C++ original:
     * ```cpp
     * SkM44 SkImageShader::CubicResamplerMatrix(float B, float C) {
     * #if 0
     *     constexpr SkM44 kMitchell = SkM44( 1.f/18.f, -9.f/18.f,  15.f/18.f,  -7.f/18.f,
     *                                       16.f/18.f,  0.f/18.f, -36.f/18.f,  21.f/18.f,
     *                                        1.f/18.f,  9.f/18.f,  27.f/18.f, -21.f/18.f,
     *                                        0.f/18.f,  0.f/18.f,  -6.f/18.f,   7.f/18.f);
     *
     *     constexpr SkM44 kCatmull = SkM44(0.0f, -0.5f,  1.0f, -0.5f,
     *                                      1.0f,  0.0f, -2.5f,  1.5f,
     *                                      0.0f,  0.5f,  2.0f, -1.5f,
     *                                      0.0f,  0.0f, -0.5f,  0.5f);
     *
     *     if (B == 1.0f/3 && C == 1.0f/3) {
     *         return kMitchell;
     *     }
     *     if (B == 0 && C == 0.5f) {
     *         return kCatmull;
     *     }
     * #endif
     *     return SkM44(    (1.f/6)*B, -(3.f/6)*B - C,       (3.f/6)*B + 2*C,    - (1.f/6)*B - C,
     *                  1 - (2.f/6)*B,              0, -3 + (12.f/6)*B +   C,  2 - (9.f/6)*B - C,
     *                      (1.f/6)*B,  (3.f/6)*B + C,  3 - (15.f/6)*B - 2*C, -2 + (9.f/6)*B + C,
     *                              0,              0,                    -C,      (1.f/6)*B + C);
     * }
     * ```
     */
    public fun cubicResamplerMatrix(b: Float, c: Float): SkM44 {
      TODO("Implement cubicResamplerMatrix")
    }
  }
}

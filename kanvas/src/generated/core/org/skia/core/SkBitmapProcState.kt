package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkColor
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkFixed
import org.skia.math.SkFixed3232
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * struct SkBitmapProcState {
 *     SkBitmapProcState(const SkImage_Base* image, SkTileMode tmx, SkTileMode tmy);
 *
 *     bool setup(const SkMatrix& inv, SkColor color, const SkSamplingOptions& sampling) {
 *         return this->init(inv, color, sampling)
 *             && this->chooseProcs();
 *     }
 *
 *     using ShaderProc32 = void (*)(const void* ctx, int x, int y, SkPMColor[], int count);
 *
 *     using MatrixProc = void (*)(const SkBitmapProcState&,
 *                                 uint32_t bitmapXY[],
 *                                 int count,
 *                                 int x, int y);
 *
 *     using SampleProc32 = void (*)(const SkBitmapProcState&,
 *                                   const uint32_t[],
 *                                   int count,
 *                                   SkPMColor colors[]);
 *
 *     const SkImage_Base*     fImage;
 *
 *     SkPixmap                fPixmap;
 *     SkMatrix                fInvMatrix;         // This changes based on tile mode.
 *     SkAlpha                 fPaintAlpha;
 *     SkTileMode              fTileModeX;
 *     SkTileMode              fTileModeY;
 *     bool                    fBilerp;
 *
 *     SkFixed3232             fInvSx;
 *     SkFixed3232             fInvKy;
 *
 *     SkFixed                 fFilterOneX;
 *     SkFixed                 fFilterOneY;
 *
 *     uint16_t                fAlphaScale;        // chooseProcs
 *
 *     /** Given the byte size of the index buffer to be passed to the matrix proc,
 *         return the maximum number of resulting pixels that can be computed
 *         (i.e. the number of SkPMColor values to be written by the sample proc).
 *         This routine takes into account that filtering and scale-vs-affine
 *         affect the amount of buffer space needed.
 *
 *         Only valid to call after chooseProcs (setContext) has been called. It is
 *         safe to call this inside the shader's shadeSpan() method.
 *      */
 *     int maxCountForBufferSize(size_t bufferSize) const;
 *
 *     // If a shader proc is present, then the corresponding matrix/sample procs
 *     // are ignored
 *     ShaderProc32 getShaderProc32() const { return fShaderProc32; }
 *
 * #ifdef SK_DEBUG
 *     MatrixProc getMatrixProc() const;
 * #else
 *     MatrixProc getMatrixProc() const { return fMatrixProc; }
 * #endif
 *     SampleProc32 getSampleProc32() const { return fSampleProc32; }
 *
 * private:
 *     // found by inspection. If this is too small, we will allocate on the heap.
 *     static constexpr size_t kBMStateSize = 136;
 *     SkSTArenaAlloc<kBMStateSize> fAlloc;
 *
 *     ShaderProc32        fShaderProc32;      // chooseProcs
 *     // These are used if the shaderproc is nullptr
 *     MatrixProc          fMatrixProc;        // chooseProcs
 *     SampleProc32        fSampleProc32;      // chooseProcs
 *
 *     bool init(const SkMatrix& inverse, SkAlpha, const SkSamplingOptions&);
 *     bool chooseProcs();
 *     MatrixProc chooseMatrixProc(bool trivial_matrix);
 *     ShaderProc32 chooseShaderProc32();
 *
 *     // Return false if we failed to setup for fast translate (e.g. overflow)
 *     bool setupForTranslate();
 *
 * #ifdef SK_DEBUG
 *     static void DebugMatrixProc(const SkBitmapProcState&,
 *                                 uint32_t[], int count, int x, int y);
 * #endif
 * }
 * ```
 */
public data class SkBitmapProcState public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkImage_Base*     fImage
   * ```
   */
  public val fImage: SkImageBase?,
  /**
   * C++ original:
   * ```cpp
   * SkPixmap                fPixmap
   * ```
   */
  public var fPixmap: SkPixmap,
  /**
   * C++ original:
   * ```cpp
   * SkMatrix                fInvMatrix
   * ```
   */
  public var fInvMatrix: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * SkAlpha                 fPaintAlpha
   * ```
   */
  public var fPaintAlpha: SkAlpha,
  /**
   * C++ original:
   * ```cpp
   * SkTileMode              fTileModeX
   * ```
   */
  public var fTileModeX: SkTileMode,
  /**
   * C++ original:
   * ```cpp
   * SkTileMode              fTileModeY
   * ```
   */
  public var fTileModeY: SkTileMode,
  /**
   * C++ original:
   * ```cpp
   * bool                    fBilerp
   * ```
   */
  public var fBilerp: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkFixed3232             fInvSx
   * ```
   */
  public var fInvSx: SkFixed3232,
  /**
   * C++ original:
   * ```cpp
   * SkFixed3232             fInvKy
   * ```
   */
  public var fInvKy: SkFixed3232,
  /**
   * C++ original:
   * ```cpp
   * SkFixed                 fFilterOneX
   * ```
   */
  public var fFilterOneX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed                 fFilterOneY
   * ```
   */
  public var fFilterOneY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * uint16_t                fAlphaScale
   * ```
   */
  public var fAlphaScale: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kBMStateSize = 136
   * ```
   */
  private var fAlloc: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSTArenaAlloc<kBMStateSize> fAlloc
   * ```
   */
  private var fShaderProc32: SkBitmapProcStateShaderProc32,
  /**
   * C++ original:
   * ```cpp
   * ShaderProc32        fShaderProc32
   * ```
   */
  private var fMatrixProc: SkBitmapProcStateMatrixProc,
  /**
   * C++ original:
   * ```cpp
   * MatrixProc          fMatrixProc
   * ```
   */
  private var fSampleProc32: SkBitmapProcStateSampleProc32,
) {
  /**
   * C++ original:
   * ```cpp
   * bool setup(const SkMatrix& inv, SkColor color, const SkSamplingOptions& sampling) {
   *         return this->init(inv, color, sampling)
   *             && this->chooseProcs();
   *     }
   * ```
   */
  public fun setup(
    inv: SkMatrix,
    color: SkColor,
    sampling: SkSamplingOptions,
  ): Boolean {
    TODO("Implement setup")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkBitmapProcState::maxCountForBufferSize(size_t bufferSize) const {
   *     int32_t size = static_cast<int32_t>(bufferSize);
   *
   *     size &= ~3; // only care about 4-byte aligned chunks
   *     if (fInvMatrix.isScaleTranslate()) {
   *         size -= 4;   // the shared Y (or YY) coordinate
   *         if (size < 0) {
   *             size = 0;
   *         }
   *         size >>= 1;
   *     } else {
   *         size >>= 2;
   *     }
   *
   *     if (fBilerp) {
   *         size >>= 1;
   *     }
   *
   *     return size;
   * }
   * ```
   */
  public fun maxCountForBufferSize(bufferSize: ULong): Int {
    TODO("Implement maxCountForBufferSize")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderProc32 getShaderProc32() const { return fShaderProc32; }
   * ```
   */
  public fun getShaderProc32(): SkBitmapProcStateShaderProc32 {
    TODO("Implement getShaderProc32")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapProcState::MatrixProc SkBitmapProcState::getMatrixProc() const {
   *     return DebugMatrixProc;
   * }
   * ```
   */
  public fun getMatrixProc(): SkBitmapProcStateMatrixProc {
    TODO("Implement getMatrixProc")
  }

  /**
   * C++ original:
   * ```cpp
   * SampleProc32 getSampleProc32() const { return fSampleProc32; }
   * ```
   */
  public fun getSampleProc32(): SkBitmapProcStateSampleProc32 {
    TODO("Implement getSampleProc32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapProcState::init(const SkMatrix& inv, SkAlpha paintAlpha,
   *                              const SkSamplingOptions& sampling) {
   *     SkASSERT(!inv.hasPerspective());
   *     SkASSERT(SkOpts::S32_alpha_D32_filter_DXDY || inv.isScaleTranslate());
   *     SkASSERT(!sampling.isAniso());
   *     SkASSERT(!sampling.useCubic);
   *     SkASSERT(sampling.mipmap != SkMipmapMode::kLinear);
   *
   *     fPixmap.reset();
   *     fBilerp = false;
   *
   *     auto* access = SkMipmapAccessor::Make(&fAlloc, (const SkImage*)fImage, inv, sampling.mipmap);
   *     if (!access) {
   *         return false;
   *     }
   *     std::tie(fPixmap, fInvMatrix) = access->level();
   *     fInvMatrix.preConcat(inv);
   *
   *     fPaintAlpha = paintAlpha;
   *     fBilerp = sampling.filter == SkFilterMode::kLinear;
   *     SkASSERT(fPixmap.addr());
   *
   *     bool integral_translate_only = just_trans_integral(fInvMatrix);
   *     if (!integral_translate_only) {
   *         // Most of the scanline procs deal with "unit" texture coordinates, as this
   *         // makes it easy to perform tiling modes (repeat = (x & 0xFFFF)). To generate
   *         // those, we divide the matrix by its dimensions here.
   *         //
   *         // We don't do this if we're either trivial (can ignore the matrix) or clamping
   *         // in both X and Y since clamping to width,height is just as easy as to 0xFFFF.
   *
   *         if (fTileModeX != SkTileMode::kClamp || fTileModeY != SkTileMode::kClamp) {
   *             SkMatrixPriv::PostIDiv(&fInvMatrix, fPixmap.width(), fPixmap.height());
   *         }
   *
   *         // Now that all possible changes to the matrix have taken place, check
   *         // to see if we're really close to a no-scale matrix.  If so, explicitly
   *         // set it to be so.  Subsequent code may inspect this matrix to choose
   *         // a faster path in this case.
   *
   *         // This code will only execute if the matrix has some scale component;
   *         // if it's already pure translate then we won't do this inversion.
   *
   *         if (matrix_only_scale_translate(fInvMatrix)) {
   *             if (auto forward = fInvMatrix.invert()) {
   *                 if (just_trans_general(*forward)) {
   *                     fInvMatrix.setTranslate(-forward->getTranslateX(), -forward->getTranslateY());
   *                 }
   *             }
   *         }
   *
   *         // Recompute the flag after matrix adjustments.
   *         integral_translate_only = just_trans_integral(fInvMatrix);
   *     }
   *
   *     if (fBilerp &&
   *         (!valid_for_filtering(fPixmap.width() | fPixmap.height()) || integral_translate_only)) {
   *         fBilerp = false;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun `init`(
    inverse: SkMatrix,
    paintAlpha: SkAlpha,
    sampling: SkSamplingOptions,
  ): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapProcState::chooseProcs() {
   *     SkASSERT(!fInvMatrix.hasPerspective());
   *     SkASSERT(SkOpts::S32_alpha_D32_filter_DXDY || fInvMatrix.isScaleTranslate());
   *     SkASSERT(fPixmap.colorType() == kN32_SkColorType);
   *     SkASSERT(fPixmap.alphaType() == kPremul_SkAlphaType ||
   *              fPixmap.alphaType() == kOpaque_SkAlphaType);
   *
   *     SkASSERT(fTileModeX != SkTileMode::kDecal);
   *
   *     fInvSx = SkScalarToFixed3232(fInvMatrix.getScaleX());
   *     fInvKy = SkScalarToFixed3232(fInvMatrix.getSkewY ());
   *
   *     fAlphaScale = SkAlpha255To256(fPaintAlpha);
   *
   *     bool translate_only = (fInvMatrix.getType() & ~SkMatrix::kTranslate_Mask) == 0;
   *     fMatrixProc = this->chooseMatrixProc(translate_only);
   *     SkASSERT(fMatrixProc);
   *
   *     if (fInvMatrix.isScaleTranslate()) {
   *         fSampleProc32 = fBilerp ? SkOpts::S32_alpha_D32_filter_DX   : S32_alpha_D32_nofilter_DX  ;
   *     } else {
   *         fSampleProc32 = fBilerp ? SkOpts::S32_alpha_D32_filter_DXDY : S32_alpha_D32_nofilter_DXDY;
   *     }
   *     SkASSERT(fSampleProc32);
   *
   *     // our special-case shaderprocs
   *     // TODO: move this one into chooseShaderProc32() or pull all that in here.
   *     if (fAlphaScale == 256
   *             && !fBilerp
   *             && SkTileMode::kClamp == fTileModeX
   *             && SkTileMode::kClamp == fTileModeY
   *             && fInvMatrix.isScaleTranslate()) {
   *         fShaderProc32 = Clamp_S32_opaque_D32_nofilter_DX_shaderproc;
   *     } else {
   *         fShaderProc32 = this->chooseShaderProc32();
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun chooseProcs(): Boolean {
    TODO("Implement chooseProcs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapProcState::MatrixProc SkBitmapProcState::chooseMatrixProc(bool translate_only_matrix) {
   *     SkASSERT(!fInvMatrix.hasPerspective());
   *     SkASSERT(fTileModeX != SkTileMode::kDecal);
   *
   *     if( fTileModeX == fTileModeY ) {
   *         // Check for our special case translate methods when there is no scale/affine/perspective.
   *         if (translate_only_matrix && !fBilerp) {
   *             switch (fTileModeX) {
   *                 default: SkASSERT(false); [[fallthrough]];
   *                 case SkTileMode::kClamp:  return  clampx_nofilter_trans<int_clamp>;
   *                 case SkTileMode::kRepeat: return repeatx_nofilter_trans<int_repeat>;
   *                 case SkTileMode::kMirror: return mirrorx_nofilter_trans<int_mirror>;
   *             }
   *         }
   *
   *         // The arrays are all [ nofilter, filter ].
   *         int index = fBilerp ? 1 : 0;
   *         if (!fInvMatrix.isScaleTranslate()) {
   *             index |= 2;
   *         }
   *
   *         if (fTileModeX == SkTileMode::kClamp) {
   *             // clamp gets special version of filterOne, working in non-normalized space (allowing decal)
   *             fFilterOneX = SK_Fixed1;
   *             fFilterOneY = SK_Fixed1;
   *             return ClampX_ClampY_Procs[index];
   *         }
   *
   *         // all remaining procs use this form for filterOne, putting them into normalized space.
   *         fFilterOneX = SK_Fixed1 / fPixmap.width();
   *         fFilterOneY = SK_Fixed1 / fPixmap.height();
   *
   *         if (fTileModeX == SkTileMode::kRepeat) {
   *             return RepeatX_RepeatY_Procs[index];
   *         }
   *         return MirrorX_MirrorY_Procs[index];
   *     }
   *
   *     SkASSERT(fTileModeX == fTileModeY);
   *     return nullptr;
   * }
   * ```
   */
  private fun chooseMatrixProc(trivialMatrix: Boolean): SkBitmapProcStateMatrixProc {
    TODO("Implement chooseMatrixProc")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmapProcState::ShaderProc32 SkBitmapProcState::chooseShaderProc32() {
   *
   *     if (kN32_SkColorType != fPixmap.colorType()) {
   *         return nullptr;
   *     }
   *
   *     if (1 == fPixmap.width() && fInvMatrix.isScaleTranslate()) {
   *         if (!fBilerp && fInvMatrix.isTranslate() && !this->setupForTranslate()) {
   *             return DoNothing_shaderproc;
   *         }
   *         return S32_D32_constX_shaderproc;
   *     }
   *
   *     if (fAlphaScale < 256) {
   *         return nullptr;
   *     }
   *     if (!fInvMatrix.isTranslate()) {
   *         return nullptr;
   *     }
   *     if (fBilerp) {
   *         return nullptr;
   *     }
   *
   *     SkTileMode tx = fTileModeX;
   *     SkTileMode ty = fTileModeY;
   *
   *     if (SkTileMode::kClamp == tx && SkTileMode::kClamp == ty) {
   *         if (this->setupForTranslate()) {
   *             return Clamp_S32_D32_nofilter_trans_shaderproc;
   *         }
   *         return DoNothing_shaderproc;
   *     }
   *     if (SkTileMode::kRepeat == tx && SkTileMode::kRepeat == ty) {
   *         if (this->setupForTranslate()) {
   *             return Repeat_S32_D32_nofilter_trans_shaderproc;
   *         }
   *         return DoNothing_shaderproc;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  private fun chooseShaderProc32(): SkBitmapProcStateShaderProc32 {
    TODO("Implement chooseShaderProc32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBitmapProcState::setupForTranslate() {
   *     SkPoint pt;
   *     const SkBitmapProcStateAutoMapper mapper(*this, 0, 0, &pt);
   *
   *     /*
   *      *  if the translate is larger than our ints, we can get random results, or
   *      *  worse, we might get 0x80000000, which wreaks havoc on us, since we can't
   *      *  negate it.
   *      */
   *     const SkScalar too_big = SkIntToScalar(1 << 30);
   *     if (SkScalarAbs(pt.fX) > too_big || SkScalarAbs(pt.fY) > too_big) {
   *         return false;
   *     }
   *
   *     // Since we know we're not filtered, we re-purpose these fields allow
   *     // us to go from device -> src coordinates w/ just an integer add,
   *     // rather than running through the inverse-matrix
   *     fFilterOneX = mapper.intX();
   *     fFilterOneY = mapper.intY();
   *
   *     return true;
   * }
   * ```
   */
  private fun setupForTranslate(): Boolean {
    TODO("Implement setupForTranslate")
  }

  public companion object {
    private val kBMStateSize: Int = TODO("Initialize kBMStateSize")

    /**
     * C++ original:
     * ```cpp
     * void SkBitmapProcState::DebugMatrixProc(const SkBitmapProcState& state,
     *                                         uint32_t bitmapXY[], int count,
     *                                         int x, int y) {
     *     SkASSERT(bitmapXY);
     *     SkASSERT(count > 0);
     *
     *     state.fMatrixProc(state, bitmapXY, count, x, y);
     *
     *     void (*proc)(uint32_t bitmapXY[], int count, unsigned mx, unsigned my);
     *
     *     if (state.fInvMatrix.isScaleTranslate()) {
     *         proc = state.fBilerp ? check_scale_filter : check_scale_nofilter;
     *     } else {
     *         proc = state.fBilerp ? check_affine_filter : check_affine_nofilter;
     *     }
     *
     *     proc(bitmapXY, count, state.fPixmap.width(), state.fPixmap.height());
     * }
     * ```
     */
    private fun debugMatrixProc(
      state: SkBitmapProcState,
      bitmapXY: Array<UInt>,
      count: Int,
      x: Int,
      y: Int,
    ) {
      TODO("Implement debugMatrixProc")
    }
  }
}

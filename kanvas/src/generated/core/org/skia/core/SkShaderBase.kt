package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkPMColor
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.memory.SkArenaAlloc
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkShaderBase : public SkShader {
 * public:
 *     ~SkShaderBase() override;
 *
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     sk_sp<SkShader> makeInvertAlpha() const;
 *     sk_sp<SkShader> makeWithCTM(const SkMatrix&) const;  // owns its own ctm
 *
 *     /**
 *      *  Returns true if the shader is guaranteed to produce only a single color
 *      *  If the color parameter is non-null, it is filled in with that color.
 *      *  Subclasses can override this to allow loop-hoisting optimization.
 *      */
 *     virtual bool isConstant(SkColor4f* color = nullptr) const { return false; }
 *
 *     enum class ShaderType {
 * #define M(type) k##type,
 *         SK_ALL_SHADERS(M)
 * #undef M
 *     };
 *
 *     virtual ShaderType type() const = 0;
 *
 *     enum class GradientType {
 *         kNone,
 * #define M(type) k##type,
 *         SK_ALL_GRADIENTS(M)
 * #undef M
 *     };
 *
 *     /**
 *      *  If the shader subclass can be represented as a gradient, asGradient
 *      *  returns the matching GradientType enum (or GradientType::kNone if it
 *      *  cannot). Also, if info is not null, asGradient populates info with
 *      *  the relevant (see below) parameters for the gradient.  fColorCount
 *      *  is both an input and output parameter.  On input, it indicates how
 *      *  many entries in fColors and fColorOffsets can be used, if they are
 *      *  non-NULL.  After asGradient has run, fColorCount indicates how
 *      *  many color-offset pairs there are in the gradient.  If there is
 *      *  insufficient space to store all of the color-offset pairs, fColors
 *      *  and fColorOffsets will not be altered.  fColorOffsets specifies
 *      *  where on the range of 0 to 1 to transition to the given color.
 *      *  The meaning of fPoint and fRadius is dependent on the type of gradient.
 *      *
 *      *  None:
 *      *      info is ignored.
 *      *  Color:
 *      *      fColorOffsets[0] is meaningless.
 *      *  Linear:
 *      *      fPoint[0] and fPoint[1] are the end-points of the gradient
 *      *  Radial:
 *      *      fPoint[0] and fRadius[0] are the center and radius
 *      *  Conical:
 *      *      fPoint[0] and fRadius[0] are the center and radius of the 1st circle
 *      *      fPoint[1] and fRadius[1] are the center and radius of the 2nd circle
 *      *  Sweep:
 *      *      fPoint[0] is the center of the sweep.
 *      *      fPoint[1] x is the scale, y is the bias
 *      */
 *     struct GradientInfo {
 *         int         fColorCount    = 0;        //!< In-out parameter, specifies passed size
 *                                                //   of fColors/fColorOffsets on input, and
 *                                                //   actual number of colors/offsets on
 *                                                //   output.
 *         SkColor4f*  fColors        = nullptr;  //!< The colors in the gradient.
 *         SkScalar*   fColorOffsets  = nullptr;  //!< The unit offset for color transitions.
 *         SkPoint     fPoint[2];                 //!< Type specific, see above.
 *         SkScalar    fRadius[2];                //!< Type specific, see above.
 *         SkTileMode  fTileMode;
 *         bool        fPremulInterp;
 *     };
 *
 *     virtual GradientType asGradient(GradientInfo* info    = nullptr,
 *                                     SkMatrix* localMatrix = nullptr) const {
 *         return GradientType::kNone;
 *     }
 *
 *     enum Flags {
 *         //!< set if all of the colors will be opaque
 *         kOpaqueAlpha_Flag = 1 << 0,
 *     };
 *
 *     /**
 *      *  ContextRec acts as a parameter bundle for creating Contexts.
 *      */
 *     struct ContextRec {
 *         ContextRec(SkAlpha paintAlpha,
 *                    const SkShaders::MatrixRec& matrixRec,
 *                    SkColorType dstColorType,
 *                    SkColorSpace* dstColorSpace,
 *                    const SkSurfaceProps& props)
 *                 : fMatrixRec(matrixRec)
 *                 , fDstColorType(dstColorType)
 *                 , fDstColorSpace(dstColorSpace)
 *                 , fProps(props)
 *                 , fPaintAlpha(paintAlpha) {}
 *
 *         static ContextRec Concat(const ContextRec& parentRec, const SkMatrix& localM) {
 *             return {parentRec.fPaintAlpha,
 *                     parentRec.fMatrixRec.concat(localM),
 *                     parentRec.fDstColorType,
 *                     parentRec.fDstColorSpace,
 *                     parentRec.fProps};
 *         }
 *
 *         const SkShaders::MatrixRec fMatrixRec;
 *         SkColorType                fDstColorType;   // the color type of the dest surface
 *         SkColorSpace*              fDstColorSpace;  // the color space of the dest surface (if any)
 *         SkSurfaceProps             fProps;          // props of the dest surface
 *         SkAlpha                    fPaintAlpha;
 *
 *         bool isLegacyCompatible(SkColorSpace* shadersColorSpace) const;
 *     };
 *
 *     class Context : public ::SkNoncopyable {
 *     public:
 *         Context(const SkShaderBase& shader, const ContextRec&);
 *
 *         virtual ~Context();
 *
 *         /**
 *          *  Called sometimes before drawing with this shader. Return the type of
 *          *  alpha your shader will return. The default implementation returns 0.
 *          *  Your subclass should override if it can (even sometimes) report a
 *          *  non-zero value, since that will enable various blitters to perform
 *          *  faster.
 *          */
 *         virtual uint32_t getFlags() const { return 0; }
 *
 *         /**
 *          *  Called for each span of the object being drawn. Your subclass should
 *          *  set the appropriate colors (with premultiplied alpha) that correspond
 *          *  to the specified device coordinates.
 *          */
 *         virtual void shadeSpan(int x, int y, SkPMColor[], int count) = 0;
 *
 *     protected:
 *         // Reference to shader, so we don't have to dupe information.
 *         const SkShaderBase& fShader;
 *
 *         uint8_t getPaintAlpha() const { return fPaintAlpha; }
 *
 *     private:
 *         uint8_t fPaintAlpha;
 *     };
 *
 *     /**
 *      * Make a context using the memory provided by the arena.
 *      *
 *      * @return pointer to context or nullptr if can't be created
 *      */
 *     Context* makeContext(const ContextRec&, SkArenaAlloc*) const;
 *
 *     /**
 *      *  If the shader can represent its "average" luminance in a single color, return true and
 *      *  if color is not NULL, return that color. If it cannot, return false and ignore the color
 *      *  parameter.
 *      *
 *      *  Note: if this returns true, the returned color will always be opaque, as only the RGB
 *      *  components are used to compute luminance.
 *      */
 *     bool asLuminanceColor(SkColor4f*) const;
 *
 *     /**
 *      * If this returns false, then we draw nothing (do not fall back to shader context). This should
 *      * only be called on a root-level effect. It assumes that the initial device coordinates have
 *      * not yet been seeded.
 *      */
 *     [[nodiscard]] bool appendRootStages(const SkStageRec& rec, const SkMatrix& ctm) const;
 *
 *     /**
 *      * Adds stages to implement this shader. To ensure that the correct input coords are present
 *      * in r,g MatrixRec::apply() must be called (unless the shader doesn't require it's input
 *      * coords). The default impl creates shadercontext and calls that (not very efficient).
 *      */
 *     virtual bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const = 0;
 *
 *     virtual SkImage* onIsAImage(SkMatrix*, SkTileMode[2]) const {
 *         return nullptr;
 *     }
 *
 *     virtual SkRuntimeEffect* asRuntimeEffect() const { return nullptr; }
 *
 *     static Type GetFlattenableType() { return kSkShader_Type; }
 *     Type getFlattenableType() const override { return GetFlattenableType(); }
 *
 *     static sk_sp<SkShaderBase> Deserialize(const void* data, size_t size,
 *                                              const SkDeserialProcs* procs = nullptr) {
 *         return sk_sp<SkShaderBase>(static_cast<SkShaderBase*>(
 *                 SkFlattenable::Deserialize(GetFlattenableType(), data, size, procs).release()));
 *     }
 *     static void RegisterFlattenables();
 *
 *     /** DEPRECATED. skbug.com/40040221
 *      *  If this shader can be represented by another shader + a localMatrix, return that shader and
 *      *  the localMatrix. If not, return nullptr and ignore the localMatrix parameter.
 *      */
 *     virtual sk_sp<SkShader> makeAsALocalMatrixShader(SkMatrix* localMatrix) const;
 *
 *     static SkMatrix ConcatLocalMatrices(const SkMatrix& parentLM, const SkMatrix& childLM) {
 * #if defined(SK_BUILD_FOR_ANDROID_FRAMEWORK)  // b/256873449
 *         return SkMatrix::Concat(childLM, parentLM);
 * #endif
 *         return SkMatrix::Concat(parentLM, childLM);
 *     }
 *
 * protected:
 *     SkShaderBase();
 *
 *     void flatten(SkWriteBuffer&) const override;
 *
 * #ifdef SK_ENABLE_LEGACY_SHADERCONTEXT
 *     /**
 *      * Specialize creating a SkShader context using the supplied allocator.
 *      * @return pointer to context owned by the arena allocator.
 *      */
 *     virtual Context* onMakeContext(const ContextRec&, SkArenaAlloc*) const {
 *         return nullptr;
 *     }
 * #endif
 *
 *     virtual bool onAsLuminanceColor(SkColor4f*) const {
 *         return false;
 *     }
 *
 * private:
 *     const uint32_t fUniqueID;
 *
 *     friend class SkShaders::MatrixRec;
 * }
 * ```
 */
public abstract class SkShaderBase public constructor() : SkShader() {
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fUniqueID
   * ```
   */
  private val fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkShaderBase::makeInvertAlpha() const {
   *     return this->makeWithColorFilter(SkColorFilters::Blend(0xFFFFFFFF, SkBlendMode::kSrcOut));
   * }
   * ```
   */
  public fun makeInvertAlpha(): SkSp<SkShader> {
    TODO("Implement makeInvertAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkShaderBase::makeWithCTM(const SkMatrix& postM) const {
   *     return sk_sp<SkShader>(new SkCTMShader(sk_ref_sp(this), postM));
   * }
   * ```
   */
  public fun makeWithCTM(postM: SkMatrix): SkSp<SkShader> {
    TODO("Implement makeWithCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isConstant(SkColor4f* color = nullptr) const { return false; }
   * ```
   */
  public open fun isConstant(color: SkColor4f? = null): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual ShaderType type() const = 0
   * ```
   */
  public abstract fun type(): ShaderType

  /**
   * C++ original:
   * ```cpp
   * virtual GradientType asGradient(GradientInfo* info    = nullptr,
   *                                     SkMatrix* localMatrix = nullptr) const {
   *         return GradientType::kNone;
   *     }
   * ```
   */
  public open fun asGradient(info: GradientInfo? = null, localMatrix: SkMatrix? = null): GradientType {
    TODO("Implement asGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::Context* SkShaderBase::makeContext(const ContextRec& rec, SkArenaAlloc* alloc) const {
   * #ifdef SK_ENABLE_LEGACY_SHADERCONTEXT
   *     // We always fall back to raster pipeline when perspective is present.
   *     auto totalMatrix = rec.fMatrixRec.totalMatrix();
   *     if (totalMatrix.hasPerspective() || !totalMatrix.invert()) {
   *         return nullptr;
   *     }
   *
   *     return this->onMakeContext(rec, alloc);
   * #else
   *     return nullptr;
   * #endif
   * }
   * ```
   */
  private fun makeContext(rec: ContextRec, alloc: SkArenaAlloc?): Context {
    TODO("Implement makeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkShaderBase::asLuminanceColor(SkColor4f* colorPtr) const {
   *     SkColor4f storage;
   *     if (nullptr == colorPtr) {
   *         colorPtr = &storage;
   *     }
   *     if (this->onAsLuminanceColor(colorPtr)) {
   *         colorPtr->fA = 1.0f;  // we only return opaque
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun asLuminanceColor(colorPtr: SkColor4f?): Boolean {
    TODO("Implement asLuminanceColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkShaderBase::appendRootStages(const SkStageRec& rec, const SkMatrix& ctm) const {
   *     return this->appendStages(rec, SkShaders::MatrixRec(ctm));
   * }
   * ```
   */
  private fun appendRootStages(rec: SkStageRec, ctm: SkMatrix): Boolean {
    TODO("Implement appendRootStages")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const = 0
   * ```
   */
  private abstract fun appendStages(param0: SkStageRec, param1: MatrixRec): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual SkImage* onIsAImage(SkMatrix*, SkTileMode[2]) const {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun onIsAImage(param0: SkMatrix?, param1: Array<SkTileMode>): SkImage {
    TODO("Implement onIsAImage")
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
   * Type getFlattenableType() const override { return GetFlattenableType(); }
   * ```
   */
  public override fun getFlattenableType(): Type {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkShaderBase::makeAsALocalMatrixShader(SkMatrix*) const { return nullptr; }
   * ```
   */
  public open fun makeAsALocalMatrixShader(localMatrix: SkMatrix?): SkSp<SkShader> {
    TODO("Implement makeAsALocalMatrixShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaderBase::flatten(SkWriteBuffer& buffer) const { this->INHERITED::flatten(buffer); }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Context* onMakeContext(const ContextRec&, SkArenaAlloc*) const {
   *         return nullptr;
   *     }
   * ```
   */
  protected open fun onMakeContext(param0: ContextRec, param1: SkArenaAlloc?): Context {
    TODO("Implement onMakeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAsLuminanceColor(SkColor4f*) const {
   *         return false;
   *     }
   * ```
   */
  protected open fun onAsLuminanceColor(param0: SkColor4f?): Boolean {
    TODO("Implement onAsLuminanceColor")
  }

  public data class GradientInfo public constructor(
    public var fColorCount: Int,
    public var fColors: SkColor4f?,
    public var fColorOffsets: SkScalar?,
    public var fPoint: Array<SkPoint>,
    public var fRadius: Array<SkScalar>,
    public var fTileMode: SkTileMode,
    public var fPremulInterp: Boolean,
  )

  public data class ContextRec public constructor(
    public val fMatrixRec: MatrixRec,
    public var fDstColorType: SkColorType,
    public var fDstColorSpace: SkColorSpace?,
    public var fProps: SkSurfaceProps,
    public var fPaintAlpha: SkAlpha,
  ) {
    public fun isLegacyCompatible(shadersColorSpace: SkColorSpace?): Boolean {
      TODO("Implement isLegacyCompatible")
    }

    public companion object {
      public fun concat(parentRec: ContextRec, localM: SkMatrix): ContextRec {
        TODO("Implement concat")
      }
    }
  }

  public abstract class Context public constructor(
    shader: SkShaderBase,
    rec: undefined.ContextRec,
  ) : SkNoncopyable() {
    protected val fShader: SkShaderBase = TODO("Initialize fShader")

    private var fPaintAlpha: UByte = TODO("Initialize fPaintAlpha")

    public open fun getFlags(): UInt {
      TODO("Implement getFlags")
    }

    public abstract fun shadeSpan(
      x: Int,
      y: Int,
      param2: Array<SkPMColor>,
      count: Int,
    )

    protected fun getPaintAlpha(): UByte {
      TODO("Implement getPaintAlpha")
    }
  }

  public enum class ShaderType

  public enum class GradientType {
    kNone,
  }

  public enum class Flags {
    kOpaqueAlpha_Flag,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Type GetFlattenableType() { return kSkShader_Type; }
     * ```
     */
    private fun getFlattenableType(): Type {
      TODO("Implement getFlattenableType")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkShaderBase> Deserialize(const void* data, size_t size,
     *                                              const SkDeserialProcs* procs = nullptr) {
     *         return sk_sp<SkShaderBase>(static_cast<SkShaderBase*>(
     *                 SkFlattenable::Deserialize(GetFlattenableType(), data, size, procs).release()));
     *     }
     * ```
     */
    private fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = null,
    ): SkSp<SkShaderBase> {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaderBase::RegisterFlattenables() { SK_REGISTER_FLATTENABLE(SkImageShader); }
     * ```
     */
    private fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix ConcatLocalMatrices(const SkMatrix& parentLM, const SkMatrix& childLM) {
     * #if defined(SK_BUILD_FOR_ANDROID_FRAMEWORK)  // b/256873449
     *         return SkMatrix::Concat(childLM, parentLM);
     * #endif
     *         return SkMatrix::Concat(parentLM, childLM);
     *     }
     * ```
     */
    private fun concatLocalMatrices(parentLM: SkMatrix, childLM: SkMatrix): SkMatrix {
      TODO("Implement concatLocalMatrices")
    }
  }
}

public typealias SkImageShaderINHERITED = SkShaderBase

public typealias SkBitmapProcLegacyShaderINHERITED = SkShaderBase

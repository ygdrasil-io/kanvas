package org.skia.effects

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SampleUsage
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkData
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkIPoint
import org.skia.math.SkMatrix
import org.skia.sksl.DebugTracePriv
import org.skia.sksl.FunctionDefinition
import org.skia.sksl.Program
import org.skia.sksl.ProgramSettings

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRuntimeEffect : public SkRefCnt {
 * public:
 *     // Reflected description of a uniform variable in the effect's SkSL
 *     struct SK_API Uniform {
 *         enum class Type {
 *             kFloat,
 *             kFloat2,
 *             kFloat3,
 *             kFloat4,
 *             kFloat2x2,
 *             kFloat3x3,
 *             kFloat4x4,
 *             kInt,
 *             kInt2,
 *             kInt3,
 *             kInt4,
 *         };
 *
 *         enum Flags {
 *             // Uniform is declared as an array. 'count' contains array length.
 *             kArray_Flag = 0x1,
 *
 *             // Uniform is declared with layout(color). Colors should be supplied as unpremultiplied,
 *             // extended-range (unclamped) sRGB (ie SkColor4f). The uniform will be automatically
 *             // transformed to unpremultiplied extended-range working-space colors.
 *             kColor_Flag = 0x2,
 *
 *             // When used with SkMeshSpecification, indicates that the uniform is present in the
 *             // vertex shader. Not used with SkRuntimeEffect.
 *             kVertex_Flag = 0x4,
 *
 *             // When used with SkMeshSpecification, indicates that the uniform is present in the
 *             // fragment shader. Not used with SkRuntimeEffect.
 *             kFragment_Flag = 0x8,
 *
 *             // This flag indicates that the SkSL uniform uses a medium-precision type
 *             // (i.e., `half` instead of `float`).
 *             kHalfPrecision_Flag = 0x10,
 *         };
 *
 *         std::string_view name;
 *         size_t           offset;
 *         Type             type;
 *         int              count;
 *         uint32_t         flags;
 *
 *         bool isArray() const { return SkToBool(this->flags & kArray_Flag); }
 *         bool isColor() const { return SkToBool(this->flags & kColor_Flag); }
 *         size_t sizeInBytes() const;
 *     };
 *
 *     // Reflected description of a uniform child (shader or colorFilter) in the effect's SkSL
 *     enum class ChildType {
 *         kShader,
 *         kColorFilter,
 *         kBlender,
 *     };
 *
 *     struct Child {
 *         std::string_view name;
 *         ChildType        type;
 *         int              index;
 *     };
 *
 *     class Options {
 *     public:
 *         // For testing purposes, disables optimization and inlining. (Normally, Runtime Effects
 *         // don't run the inliner directly, but they still get an inlining pass once they are
 *         // painted.)
 *         bool forceUnoptimized = false;
 *         // When possible this name will be used to identify the created runtime effect.
 *         std::string_view fName;
 *
 *     private:
 *         friend class SkRuntimeEffect;
 *         friend class SkRuntimeEffectPriv;
 *
 *         // This flag allows Runtime Effects to access Skia implementation details like sk_FragCoord
 *         // and functions with private identifiers (e.g. $rgb_to_hsl).
 *         bool allowPrivateAccess = false;
 *         // When not 0, this field allows Skia to assign a stable key to a known runtime effect
 *         uint32_t fStableKey = 0;
 *
 *         // TODO(skbug.com/40042585) - Replace this with a promised SkCapabilities?
 *         // This flag lifts the ES2 restrictions on Runtime Effects that are gated by the
 *         // `strictES2Mode` check. Be aware that the software renderer and pipeline-stage effect are
 *         // still largely ES3-unaware and can still fail or crash if post-ES2 features are used.
 *         // This is only intended for use by tests and certain internally created effects.
 *         SkSL::Version maxVersionAllowed = SkSL::Version::k100;
 *     };
 *
 *     // If the effect is compiled successfully, `effect` will be non-null.
 *     // Otherwise, `errorText` will contain the reason for failure.
 *     struct Result {
 *         sk_sp<SkRuntimeEffect> effect;
 *         SkString errorText;
 *     };
 *
 *     // MakeForColorFilter and MakeForShader verify that the SkSL code is valid for those stages of
 *     // the Skia pipeline. In all of the signatures described below, color parameters and return
 *     // values are flexible. They are listed as being 'vec4', but they can also be 'half4' or
 *     // 'float4'. ('vec4' is an alias for 'float4').
 *
 *     // We can't use a default argument for `options` due to a bug in Clang.
 *     // https://bugs.llvm.org/show_bug.cgi?id=36684
 *
 *     // Color filter SkSL requires an entry point that looks like:
 *     //     vec4 main(vec4 inColor) { ... }
 *     //     https://fiddle.skia.org/c/@runtimeeffect_colorfilter_grid
 *     static Result MakeForColorFilter(SkString sksl, const Options&);
 *     static Result MakeForColorFilter(SkString sksl) {
 *         return MakeForColorFilter(std::move(sksl), Options{});
 *     }
 *
 *     // Shader SkSL requires an entry point that looks like:
 *     //     vec4 main(vec2 inCoords) { ... }
 *     // The color that is returned should be premultiplied.
 *     static Result MakeForShader(SkString sksl, const Options&);
 *     static Result MakeForShader(SkString sksl) {
 *         return MakeForShader(std::move(sksl), Options{});
 *     }
 *
 *     // Blend SkSL requires an entry point that looks like:
 *     //     vec4 main(vec4 srcColor, vec4 dstColor) { ... }
 *     static Result MakeForBlender(SkString sksl, const Options&);
 *     static Result MakeForBlender(SkString sksl) {
 *         return MakeForBlender(std::move(sksl), Options{});
 *     }
 *
 *     // Object that allows passing a SkShader, SkColorFilter or SkBlender as a child
 *     class SK_API ChildPtr {
 *     public:
 *         ChildPtr() = default;
 *         // Intentionally don't declare these to be explicit for convenience.
 *         ChildPtr(sk_sp<SkShader> s) : fChild(std::move(s)) {}
 *         ChildPtr(sk_sp<SkColorFilter> cf) : fChild(std::move(cf)) {}
 *         ChildPtr(sk_sp<SkBlender> b) : fChild(std::move(b)) {}
 *
 *         // Asserts that the flattenable is either null, or one of the legal derived types
 *         ChildPtr(sk_sp<SkFlattenable> f);
 *
 *         std::optional<ChildType> type() const;
 *
 *         SkShader* shader() const;
 *         SkColorFilter* colorFilter() const;
 *         SkBlender* blender() const;
 *         SkFlattenable* flattenable() const { return fChild.get(); }
 *
 *         using sk_is_trivially_relocatable = std::true_type;
 *
 *     private:
 *         sk_sp<SkFlattenable> fChild;
 *
 *         static_assert(::sk_is_trivially_relocatable<decltype(fChild)>::value);
 *     };
 *
 *     sk_sp<SkShader> makeShader(sk_sp<const SkData> uniforms,
 *                                sk_sp<SkShader> children[],
 *                                size_t childCount,
 *                                const SkMatrix* localMatrix = nullptr) const;
 *     sk_sp<SkShader> makeShader(sk_sp<const SkData> uniforms,
 *                                SkSpan<const ChildPtr> children,
 *                                const SkMatrix* localMatrix = nullptr) const;
 *
 *     sk_sp<SkColorFilter> makeColorFilter(sk_sp<const SkData> uniforms) const;
 *     sk_sp<SkColorFilter> makeColorFilter(sk_sp<const SkData> uniforms,
 *                                          sk_sp<SkColorFilter> children[],
 *                                          size_t childCount) const;
 *     sk_sp<SkColorFilter> makeColorFilter(sk_sp<const SkData> uniforms,
 *                                          SkSpan<const ChildPtr> children) const;
 *
 *     sk_sp<SkBlender> makeBlender(sk_sp<const SkData> uniforms,
 *                                  SkSpan<const ChildPtr> children = {}) const;
 *
 *     /**
 *      * Creates a new Runtime Effect patterned after an already-existing one. The new shader behaves
 *      * like the original, but also creates a debug trace of its execution at the requested
 *      * coordinate. After painting with this shader, the associated DebugTrace object will contain a
 *      * shader execution trace. Call `writeTrace` on the debug trace object to generate a full trace
 *      * suitable for a debugger, or call `dump` to emit a human-readable trace.
 *      *
 *      * Debug traces are only supported on a raster (non-GPU) canvas.
 *
 *      * Debug traces are currently only supported on shaders. Color filter and blender tracing is a
 *      * work-in-progress.
 *      */
 *     struct TracedShader {
 *         sk_sp<SkShader> shader;
 *         sk_sp<SkSL::DebugTrace> debugTrace;
 *     };
 *     static TracedShader MakeTraced(sk_sp<SkShader> shader, const SkIPoint& traceCoord);
 *
 *     // Returns the SkSL source of the runtime effect shader.
 *     const std::string& source() const;
 *
 *     // Combined size of all 'uniform' variables. When calling makeColorFilter or makeShader,
 *     // provide an SkData of this size, containing values for all of those variables.
 *     size_t uniformSize() const;
 *
 *     SkSpan<const Uniform> uniforms() const { return SkSpan(fUniforms); }
 *     SkSpan<const Child> children() const { return SkSpan(fChildren); }
 *
 *     // Returns pointer to the named uniform variable's description, or nullptr if not found
 *     const Uniform* findUniform(std::string_view name) const;
 *
 *     // Returns pointer to the named child's description, or nullptr if not found
 *     const Child* findChild(std::string_view name) const;
 *
 *     // Allows the runtime effect type to be identified.
 *     bool allowShader()        const { return (fFlags & kAllowShader_Flag);        }
 *     bool allowColorFilter()   const { return (fFlags & kAllowColorFilter_Flag);   }
 *     bool allowBlender()       const { return (fFlags & kAllowBlender_Flag);       }
 *
 *     static void RegisterFlattenables();
 *     ~SkRuntimeEffect() override;
 *
 * private:
 *     enum Flags {
 *         kUsesSampleCoords_Flag    = 0x001,
 *         kAllowColorFilter_Flag    = 0x002,
 *         kAllowShader_Flag         = 0x004,
 *         kAllowBlender_Flag        = 0x008,
 *         kSamplesOutsideMain_Flag  = 0x010,
 *         kUsesColorTransform_Flag  = 0x020,
 *         kAlwaysOpaque_Flag        = 0x040,
 *         kAlphaUnchanged_Flag      = 0x080,
 *         kDisableOptimization_Flag = 0x100,
 *     };
 *
 *     SkRuntimeEffect(std::unique_ptr<SkSL::Program> baseProgram,
 *                     const Options& options,
 *                     const SkSL::FunctionDefinition& main,
 *                     std::vector<Uniform>&& uniforms,
 *                     std::vector<Child>&& children,
 *                     std::vector<SkSL::SampleUsage>&& sampleUsages,
 *                     uint32_t flags);
 *
 *     sk_sp<SkRuntimeEffect> makeUnoptimizedClone();
 *
 *     static Result MakeFromSource(SkString sksl, const Options& options, SkSL::ProgramKind kind);
 *
 *     static Result MakeInternal(std::unique_ptr<SkSL::Program> program,
 *                                const Options& options,
 *                                SkSL::ProgramKind kind);
 *
 *     static SkSL::ProgramSettings MakeSettings(const Options& options);
 *
 *     uint32_t hash() const { return fHash; }
 *     bool usesSampleCoords()   const { return (fFlags & kUsesSampleCoords_Flag);   }
 *     bool samplesOutsideMain() const { return (fFlags & kSamplesOutsideMain_Flag); }
 *     bool usesColorTransform() const { return (fFlags & kUsesColorTransform_Flag); }
 *     bool alwaysOpaque()       const { return (fFlags & kAlwaysOpaque_Flag);       }
 *     bool isAlphaUnchanged()   const { return (fFlags & kAlphaUnchanged_Flag);     }
 *
 *     const SkSL::RP::Program* getRPProgram(SkSL::DebugTracePriv* debugTrace) const;
 *
 *     friend class GrSkSLFP;              // usesColorTransform
 *     friend class SkRuntimeShader;       // fBaseProgram, fMain, fSampleUsages, getRPProgram()
 *     friend class SkRuntimeBlender;      //
 *     friend class SkRuntimeColorFilter;  //
 *
 *     friend class SkRuntimeEffectPriv;
 *
 *     uint32_t fHash;
 *     // When not 0, this field holds a StableKey value or a user-defined stable key
 *     uint32_t fStableKey = 0;
 *     SkString fName;
 *
 *     std::unique_ptr<SkSL::Program> fBaseProgram;
 *     std::unique_ptr<SkSL::RP::Program> fRPProgram;
 *     mutable SkOnce fCompileRPProgramOnce;
 *     const SkSL::FunctionDefinition& fMain;
 *     std::vector<Uniform> fUniforms;
 *     std::vector<Child> fChildren;
 *     std::vector<SkSL::SampleUsage> fSampleUsages;
 *
 *     uint32_t fFlags;  // Flags
 * }
 * ```
 */
public open class SkRuntimeEffect public constructor(
  baseProgram: Program?,
  options: Options,
  main: FunctionDefinition,
  uniforms: List<org.skia.gpu.Uniform>,
  children: List<org.skia.core.Child>,
  sampleUsages: List<SampleUsage>,
  flags: UInt,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * uint32_t fHash
   * ```
   */
  private var fHash: UInt = TODO("Initialize fHash")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fStableKey = 0
   * ```
   */
  private var fStableKey: UInt = TODO("Initialize fStableKey")

  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  private var fName: Int = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkSL::Program> fBaseProgram
   * ```
   */
  private var fBaseProgram: Int = TODO("Initialize fBaseProgram")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkSL::RP::Program> fRPProgram
   * ```
   */
  private var fRPProgram: Int = TODO("Initialize fRPProgram")

  /**
   * C++ original:
   * ```cpp
   * mutable SkOnce fCompileRPProgramOnce
   * ```
   */
  private var fCompileRPProgramOnce: Int = TODO("Initialize fCompileRPProgramOnce")

  /**
   * C++ original:
   * ```cpp
   * const SkSL::FunctionDefinition& fMain
   * ```
   */
  private val fMain: FunctionDefinition = TODO("Initialize fMain")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Uniform> fUniforms
   * ```
   */
  private var fUniforms: Int = TODO("Initialize fUniforms")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Child> fChildren
   * ```
   */
  private var fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSL::SampleUsage> fSampleUsages
   * ```
   */
  private var fSampleUsages: Int = TODO("Initialize fSampleUsages")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fFlags
   * ```
   */
  private var fFlags: UInt = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkRuntimeEffect::makeShader(sk_sp<const SkData> uniforms,
   *                                             sk_sp<SkShader> childShaders[],
   *                                             size_t childCount,
   *                                             const SkMatrix* localMatrix) const {
   *     STArray<4, ChildPtr> children(childCount);
   *     for (size_t i = 0; i < childCount; ++i) {
   *         children.emplace_back(childShaders[i]);
   *     }
   *     return this->makeShader(std::move(uniforms), SkSpan(children), localMatrix);
   * }
   * ```
   */
  private fun makeShader(
    uniforms: SkSp<SkData>,
    children: Array<SkSp<SkShader>>,
    childCount: ULong,
    localMatrix: SkMatrix? = TODO(),
  ): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkRuntimeEffect::makeShader(sk_sp<const SkData> uniforms,
   *                                             SkSpan<const ChildPtr> children,
   *                                             const SkMatrix* localMatrix) const {
   *     if (!this->allowShader()) {
   *         return nullptr;
   *     }
   *     if (!verify_child_effects(fChildren, children)) {
   *         return nullptr;
   *     }
   *     if (!uniforms) {
   *         uniforms = SkData::MakeEmpty();
   *     }
   *     if (uniforms->size() != this->uniformSize()) {
   *         return nullptr;
   *     }
   *     return SkLocalMatrixShader::MakeWrapped<SkRuntimeShader>(localMatrix,
   *                                                              sk_ref_sp(this),
   *                                                              /*debugTrace=*/nullptr,
   *                                                              std::move(uniforms),
   *                                                              children);
   * }
   * ```
   */
  private fun makeShader(
    uniforms: SkSp<SkData>,
    children: SkSpan<ChildPtr>,
    localMatrix: SkMatrix? = TODO(),
  ): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkRuntimeEffect::makeColorFilter(sk_sp<const SkData> uniforms) const {
   *     return this->makeColorFilter(std::move(uniforms), /*children=*/{});
   * }
   * ```
   */
  private fun makeColorFilter(uniforms: SkSp<SkData>): Int {
    TODO("Implement makeColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkRuntimeEffect::makeColorFilter(sk_sp<const SkData> uniforms,
   *                                                       sk_sp<SkColorFilter> childColorFilters[],
   *                                                       size_t childCount) const {
   *     STArray<4, ChildPtr> children(childCount);
   *     for (size_t i = 0; i < childCount; ++i) {
   *         children.emplace_back(childColorFilters[i]);
   *     }
   *     return this->makeColorFilter(std::move(uniforms), SkSpan(children));
   * }
   * ```
   */
  private fun makeColorFilter(
    uniforms: SkSp<SkData>,
    children: Array<SkSp<SkColorFilter>>,
    childCount: ULong,
  ): Int {
    TODO("Implement makeColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkRuntimeEffect::makeColorFilter(sk_sp<const SkData> uniforms,
   *                                                       SkSpan<const ChildPtr> children) const {
   *     if (!this->allowColorFilter()) {
   *         return nullptr;
   *     }
   *     if (!verify_child_effects(fChildren, children)) {
   *         return nullptr;
   *     }
   *     if (!uniforms) {
   *         uniforms = SkData::MakeEmpty();
   *     }
   *     if (uniforms->size() != this->uniformSize()) {
   *         return nullptr;
   *     }
   *     return sk_make_sp<SkRuntimeColorFilter>(sk_ref_sp(this), std::move(uniforms), children);
   * }
   * ```
   */
  private fun makeColorFilter(uniforms: SkSp<SkData>, children: SkSpan<ChildPtr>): Int {
    TODO("Implement makeColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBlender> SkRuntimeEffect::makeBlender(sk_sp<const SkData> uniforms,
   *                                               SkSpan<const ChildPtr> children) const {
   *     if (!this->allowBlender()) {
   *         return nullptr;
   *     }
   *     if (!verify_child_effects(fChildren, children)) {
   *         return nullptr;
   *     }
   *     if (!uniforms) {
   *         uniforms = SkData::MakeEmpty();
   *     }
   *     if (uniforms->size() != this->uniformSize()) {
   *         return nullptr;
   *     }
   *     return sk_make_sp<SkRuntimeBlender>(sk_ref_sp(this), std::move(uniforms), children);
   * }
   * ```
   */
  private fun makeBlender(uniforms: Int, children: Int): Int {
    TODO("Implement makeBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::string& SkRuntimeEffect::source() const {
   *     return *fBaseProgram->fSource;
   * }
   * ```
   */
  private fun source(): Int {
    TODO("Implement source")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRuntimeEffect::uniformSize() const {
   *     return fUniforms.empty() ? 0
   *                              : SkAlign4(fUniforms.back().offset + fUniforms.back().sizeInBytes());
   * }
   * ```
   */
  private fun uniformSize(): ULong {
    TODO("Implement uniformSize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Uniform> uniforms() const { return SkSpan(fUniforms); }
   * ```
   */
  private fun uniforms(): Int {
    TODO("Implement uniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Child> children() const { return SkSpan(fChildren); }
   * ```
   */
  private fun children(): Int {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRuntimeEffect::Uniform* SkRuntimeEffect::findUniform(std::string_view name) const {
   *     auto iter = std::find_if(fUniforms.begin(), fUniforms.end(), [name](const Uniform& u) {
   *         return u.name == name;
   *     });
   *     return iter == fUniforms.end() ? nullptr : &(*iter);
   * }
   * ```
   */
  private fun findUniform(name: String): Uniform {
    TODO("Implement findUniform")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRuntimeEffect::Child* SkRuntimeEffect::findChild(std::string_view name) const {
   *     auto iter = std::find_if(fChildren.begin(), fChildren.end(), [name](const Child& c) {
   *         return c.name == name;
   *     });
   *     return iter == fChildren.end() ? nullptr : &(*iter);
   * }
   * ```
   */
  private fun findChild(name: String): Child {
    TODO("Implement findChild")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowShader()        const { return (fFlags & kAllowShader_Flag);        }
   * ```
   */
  private fun allowShader(): Boolean {
    TODO("Implement allowShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowColorFilter()   const { return (fFlags & kAllowColorFilter_Flag);   }
   * ```
   */
  private fun allowColorFilter(): Boolean {
    TODO("Implement allowColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowBlender()       const { return (fFlags & kAllowBlender_Flag);       }
   * ```
   */
  private fun allowBlender(): Boolean {
    TODO("Implement allowBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> SkRuntimeEffect::makeUnoptimizedClone() {
   *     // Compile with maximally-permissive options; any restrictions we need to enforce were already
   *     // handled when the original SkRuntimeEffect was made. We don't keep around the Options struct
   *     // from when it was initially made so we don't know what was originally requested.
   *     Options options;
   *     options.forceUnoptimized = true;
   *     options.maxVersionAllowed = SkSL::Version::k300;
   *     options.allowPrivateAccess = true;
   *
   *     // We do know the original ProgramKind, so we don't need to re-derive it.
   *     SkSL::ProgramKind kind = fBaseProgram->fConfig->fKind;
   *
   *     // Attempt to recompile the program's source with optimizations off. This ensures that the
   *     // Debugger shows results on every line, even for things that could be optimized away (static
   *     // branches, unused variables, etc). If recompilation fails, we fall back to the original code.
   *     SkSL::Compiler compiler;
   *     SkSL::ProgramSettings settings = MakeSettings(options);
   *     std::unique_ptr<SkSL::Program> program =
   *             compiler.convertProgram(kind, *fBaseProgram->fSource, settings);
   *
   *     if (!program) {
   *         // Turning off compiler optimizations can theoretically expose a program error that
   *         // had been optimized away (e.g. "all control paths return a value" might be found on a path
   *         // that is completely eliminated in the optimized program).
   *         // If this happens, the debugger will just have to show the optimized code.
   *         return sk_ref_sp(this);
   *     }
   *
   *     SkRuntimeEffect::Result result = MakeInternal(std::move(program), options, kind);
   *     if (!result.effect) {
   *         // Nothing in MakeInternal should change as a result of optimizations being toggled.
   *         SkDEBUGFAILF("makeUnoptimizedClone: MakeInternal failed\n%s",
   *                      result.errorText.c_str());
   *         return sk_ref_sp(this);
   *     }
   *
   *     return result.effect;
   * }
   * ```
   */
  private fun makeUnoptimizedClone(): Int {
    TODO("Implement makeUnoptimizedClone")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t hash() const { return fHash; }
   * ```
   */
  private fun hash(): UInt {
    TODO("Implement hash")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usesSampleCoords()   const { return (fFlags & kUsesSampleCoords_Flag);   }
   * ```
   */
  private fun usesSampleCoords(): Boolean {
    TODO("Implement usesSampleCoords")
  }

  /**
   * C++ original:
   * ```cpp
   * bool samplesOutsideMain() const { return (fFlags & kSamplesOutsideMain_Flag); }
   * ```
   */
  private fun samplesOutsideMain(): Boolean {
    TODO("Implement samplesOutsideMain")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usesColorTransform() const { return (fFlags & kUsesColorTransform_Flag); }
   * ```
   */
  private fun usesColorTransform(): Boolean {
    TODO("Implement usesColorTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * bool alwaysOpaque()       const { return (fFlags & kAlwaysOpaque_Flag);       }
   * ```
   */
  private fun alwaysOpaque(): Boolean {
    TODO("Implement alwaysOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAlphaUnchanged()   const { return (fFlags & kAlphaUnchanged_Flag);     }
   * ```
   */
  private fun isAlphaUnchanged(): Boolean {
    TODO("Implement isAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSL::RP::Program* SkRuntimeEffect::getRPProgram(SkSL::DebugTracePriv* debugTrace) const {
   *     // Lazily compile the program the first time `getRPProgram` is called.
   *     // By using an SkOnce, we avoid thread hazards and behave in a conceptually const way, but we
   *     // can avoid the cost of invoking the RP code generator until it's actually needed.
   *     fCompileRPProgramOnce([&] {
   *         // We generally do not run the inliner when an SkRuntimeEffect program is initially created,
   *         // because the final compile to native shader code will do this. However, in SkRP, there's
   *         // no additional compilation occurring, so we need to manually inline here if we want the
   *         // performance boost of inlining.
   *         if (!(fFlags & kDisableOptimization_Flag)) {
   *             SkSL::Compiler compiler;
   *             fBaseProgram->fConfig->fSettings.fInlineThreshold = SkSL::kDefaultInlineThreshold;
   *             compiler.runInliner(*fBaseProgram);
   *
   *             // After inlining, the program is likely to have dead functions left behind.
   *             while (SkSL::Transform::EliminateDeadFunctions(*fBaseProgram)) {
   *                 // Removing dead functions may cause more functions to become unreferenced.
   *             }
   *         }
   *
   *         SkSL::DebugTracePriv tempDebugTrace;
   *         if (debugTrace) {
   *             const_cast<SkRuntimeEffect*>(this)->fRPProgram = MakeRasterPipelineProgram(
   *                     *fBaseProgram, fMain, debugTrace, /*writeTraceOps=*/true);
   *         } else if (kRPEnableLiveTrace) {
   *             debugTrace = &tempDebugTrace;
   *             const_cast<SkRuntimeEffect*>(this)->fRPProgram = MakeRasterPipelineProgram(
   *                     *fBaseProgram, fMain, debugTrace, /*writeTraceOps=*/false);
   *         } else {
   *             const_cast<SkRuntimeEffect*>(this)->fRPProgram = MakeRasterPipelineProgram(
   *                     *fBaseProgram, fMain, /*debugTrace=*/nullptr, /*writeTraceOps=*/false);
   *         }
   *
   *         if (kRPEnableLiveTrace) {
   *             if (fRPProgram) {
   *                 SkDebugf("-----\n\n");
   *                 SkStreamPriv::DebugfStream stream;
   *                 fRPProgram->dump(&stream, /*writeInstructionCount=*/true);
   *                 SkDebugf("\n-----\n\n");
   *             } else {
   *                 SkDebugf("----- RP unsupported -----\n\n");
   *             }
   *         }
   *     });
   *
   *     return fRPProgram.get();
   * }
   * ```
   */
  private fun getRPProgram(debugTrace: DebugTracePriv?): Program {
    TODO("Implement getRPProgram")
  }

  public data class Uniform public constructor(
    public var name: String,
    public var offset: ULong,
    public var type: org.skia.gpu.Uniform.Type,
    public var count: Int,
    public var flags: UInt,
  ) {
    public fun isArray(): Boolean {
      TODO("Implement isArray")
    }

    public fun isColor(): Boolean {
      TODO("Implement isColor")
    }

    public fun sizeInBytes(): ULong {
      TODO("Implement sizeInBytes")
    }

    public enum class Type {
      kFloat,
      kFloat2,
      kFloat3,
      kFloat4,
      kFloat2x2,
      kFloat3x3,
      kFloat4x4,
      kInt,
      kInt2,
      kInt3,
      kInt4,
    }

    public enum class Flags {
      kArray_Flag,
      kColor_Flag,
      kVertex_Flag,
      kFragment_Flag,
      kHalfPrecision_Flag,
    }
  }

  public data class Child public constructor(
    public var name: String,
    public var type: org.skia.tests.ChildType,
    public var index: Int,
  )

  public data class Options public constructor(
    public var forceUnoptimized: Boolean,
    public var fName: String,
    private var allowPrivateAccess: Boolean,
    private var fStableKey: UInt,
    private var maxVersionAllowed: Int,
  )

  public open class Result public constructor(
    public var effect: Int,
    public var errorText: Int,
  )

  public data class ChildPtr public constructor(
    private var fChild: Int,
  ) {
    public fun type(): org.skia.tests.ChildType? {
      TODO("Implement type")
    }

    public fun shader(): Int {
      TODO("Implement shader")
    }

    public fun colorFilter(): Int {
      TODO("Implement colorFilter")
    }

    public fun blender(): Int {
      TODO("Implement blender")
    }

    public fun flattenable(): Int {
      TODO("Implement flattenable")
    }
  }

  public data class TracedShader public constructor(
    public var shader: Int,
    public var debugTrace: Int,
  )

  public enum class ChildType {
    kShader,
    kColorFilter,
    kBlender,
  }

  public enum class Flags {
    kUsesSampleCoords_Flag,
    kAllowColorFilter_Flag,
    kAllowShader_Flag,
    kAllowBlender_Flag,
    kSamplesOutsideMain_Flag,
    kUsesColorTransform_Flag,
    kAlwaysOpaque_Flag,
    kAlphaUnchanged_Flag,
    kDisableOptimization_Flag,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::Result SkRuntimeEffect::MakeForColorFilter(SkString sksl, const Options& options) {
     *     auto programKind = options.allowPrivateAccess ? SkSL::ProgramKind::kPrivateRuntimeColorFilter
     *                                                   : SkSL::ProgramKind::kRuntimeColorFilter;
     *     auto result = MakeFromSource(std::move(sksl), options, programKind);
     *     SkASSERT(!result.effect || result.effect->allowColorFilter());
     *     return result;
     * }
     * ```
     */
    private fun makeForColorFilter(sksl: String, options: Options): Result {
      TODO("Implement makeForColorFilter")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result MakeForColorFilter(SkString sksl) {
     *         return MakeForColorFilter(std::move(sksl), Options{});
     *     }
     * ```
     */
    private fun makeForColorFilter(sksl: String): Result {
      TODO("Implement makeForColorFilter")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::Result SkRuntimeEffect::MakeForShader(SkString sksl, const Options& options) {
     *     auto programKind = options.allowPrivateAccess ? SkSL::ProgramKind::kPrivateRuntimeShader
     *                                                   : SkSL::ProgramKind::kRuntimeShader;
     *     auto result = MakeFromSource(std::move(sksl), options, programKind);
     *     SkASSERT(!result.effect || result.effect->allowShader());
     *     return result;
     * }
     * ```
     */
    private fun makeForShader(sksl: String, options: Options): Result {
      TODO("Implement makeForShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result MakeForShader(SkString sksl) {
     *         return MakeForShader(std::move(sksl), Options{});
     *     }
     * ```
     */
    private fun makeForShader(sksl: String): Result {
      TODO("Implement makeForShader")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::Result SkRuntimeEffect::MakeForBlender(SkString sksl, const Options& options) {
     *     auto programKind = options.allowPrivateAccess ? SkSL::ProgramKind::kPrivateRuntimeBlender
     *                                                   : SkSL::ProgramKind::kRuntimeBlender;
     *     auto result = MakeFromSource(std::move(sksl), options, programKind);
     *     SkASSERT(!result.effect || result.effect->allowBlender());
     *     return result;
     * }
     * ```
     */
    private fun makeForBlender(sksl: String, options: Options): Result {
      TODO("Implement makeForBlender")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result MakeForBlender(SkString sksl) {
     *         return MakeForBlender(std::move(sksl), Options{});
     *     }
     * ```
     */
    private fun makeForBlender(sksl: String): Result {
      TODO("Implement makeForBlender")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::TracedShader SkRuntimeEffect::MakeTraced(sk_sp<SkShader> shader,
     *                                                           const SkIPoint& traceCoord) {
     *     SkRuntimeEffect* effect = as_SB(shader)->asRuntimeEffect();
     *     if (!effect) {
     *         return TracedShader{nullptr, nullptr};
     *     }
     *     // An SkShader with an attached SkRuntimeEffect must be an SkRuntimeShader.
     *     SkRuntimeShader* rtShader = static_cast<SkRuntimeShader*>(shader.get());
     *     return rtShader->makeTracedClone(traceCoord);
     * }
     * ```
     */
    private fun makeTraced(shader: SkSp<SkShader>, traceCoord: SkIPoint): TracedShader {
      TODO("Implement makeTraced")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkRuntimeEffect::RegisterFlattenables() {
     *     SK_REGISTER_FLATTENABLE(SkRuntimeBlender);
     *     SK_REGISTER_FLATTENABLE(SkRuntimeColorFilter);
     *     SK_REGISTER_FLATTENABLE(SkRuntimeShader);
     *
     *     // Previous name
     *     SkFlattenable::Register("SkRTShader", SkRuntimeShader::CreateProc);
     * }
     * ```
     */
    private fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::Result SkRuntimeEffect::MakeFromSource(SkString sksl,
     *                                                         const Options& options,
     *                                                         SkSL::ProgramKind kind) {
     *     SkSL::Compiler compiler;
     *     SkSL::ProgramSettings settings = MakeSettings(options);
     *     std::unique_ptr<SkSL::Program> program =
     *             compiler.convertProgram(kind, std::string(sksl.c_str(), sksl.size()), settings);
     *
     *     if (!program) {
     *         RETURN_FAILURE("%s", compiler.errorText().c_str());
     *     }
     *
     *     return MakeInternal(std::move(program), options, kind);
     * }
     * ```
     */
    private fun makeFromSource(
      sksl: String,
      options: Options,
      kind: ProgramKind,
    ): Result {
      TODO("Implement makeFromSource")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result MakeInternal(std::unique_ptr<SkSL::Program> program,
     *                                const Options& options,
     *                                SkSL::ProgramKind kind)
     * ```
     */
    private fun makeInternal(
      program: Program?,
      options: Options,
      kind: ProgramKind,
    ): Result {
      TODO("Implement makeInternal")
    }

    /**
     * C++ original:
     * ```cpp
     * SkSL::ProgramSettings SkRuntimeEffect::MakeSettings(const Options& options) {
     *     SkSL::ProgramSettings settings;
     *     settings.fInlineThreshold = 0;
     *     settings.fForceNoInline = options.forceUnoptimized;
     *     settings.fOptimize = !options.forceUnoptimized;
     *     settings.fMaxVersionAllowed = options.maxVersionAllowed;
     *
     *     // SkSL created by the GPU backend is typically parsed, converted to a backend format,
     *     // and the IR is immediately discarded. In that situation, it makes sense to use node
     *     // pools to accelerate the IR allocations. Here, SkRuntimeEffect instances are often
     *     // long-lived (especially those created internally for runtime FPs). In this situation,
     *     // we're willing to pay for a slightly longer compile so that we don't waste huge
     *     // amounts of memory.
     *     settings.fUseMemoryPool = false;
     *     return settings;
     * }
     * ```
     */
    private fun makeSettings(options: Options): ProgramSettings {
      TODO("Implement makeSettings")
    }
  }
}

package org.skia.core

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.collections.List
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.sksl.Program

/**
 * C++ original:
 * ```cpp
 * class SK_API SkMeshSpecification : public SkNVRefCnt<SkMeshSpecification> {
 * public:
 *     /** These values are enforced when creating a specification. */
 *     static constexpr size_t kMaxStride       = 1024;
 *     static constexpr size_t kMaxAttributes   = 8;
 *     static constexpr size_t kStrideAlignment = 4;
 *     static constexpr size_t kOffsetAlignment = 4;
 *     static constexpr size_t kMaxVaryings     = 6;
 *
 *     struct Attribute {
 *         enum class Type : uint32_t {  // CPU representation     Shader Type
 *             kFloat,                   // float                  float
 *             kFloat2,                  // two floats             float2
 *             kFloat3,                  // three floats           float3
 *             kFloat4,                  // four floats            float4
 *             kUByte4_unorm,            // four bytes             half4
 *
 *             kLast = kUByte4_unorm
 *         };
 *         Type     type;
 *         size_t   offset;
 *         SkString name;
 *     };
 *
 *     struct Varying {
 *         enum class Type : uint32_t {
 *             kFloat,   // "float"
 *             kFloat2,  // "float2"
 *             kFloat3,  // "float3"
 *             kFloat4,  // "float4"
 *             kHalf,    // "half"
 *             kHalf2,   // "half2"
 *             kHalf3,   // "half3"
 *             kHalf4,   // "half4"
 *
 *             kLast = kHalf4
 *         };
 *         Type     type;
 *         SkString name;
 *     };
 *
 *     using Uniform = SkRuntimeEffect::Uniform;
 *     using Child = SkRuntimeEffect::Child;
 *
 *     ~SkMeshSpecification();
 *
 *     struct Result {
 *         sk_sp<SkMeshSpecification> specification;
 *         SkString                   error;
 *     };
 *
 *     /**
 *      * If successful the return is a specification and an empty error string. Otherwise, it is a
 *      * null specification a non-empty error string.
 *      *
 *      * @param attributes     The vertex attributes that will be consumed by 'vs'. Attributes need
 *      *                       not be tightly packed but attribute offsets must be aligned to
 *      *                       kOffsetAlignment and offset + size may not be greater than
 *      *                       'vertexStride'. At least one attribute is required.
 *      * @param vertexStride   The offset between successive attribute values. This must be aligned to
 *      *                       kStrideAlignment.
 *      * @param varyings       The varyings that will be written by 'vs' and read by 'fs'. This may
 *      *                       be empty.
 *      * @param vs             The vertex shader code that computes a vertex position and the varyings
 *      *                       from the attributes.
 *      * @param fs             The fragment code that computes a local coordinate and optionally a
 *      *                       color from the varyings. The local coordinate is used to sample
 *      *                       SkShader.
 *      * @param cs             The colorspace of the color produced by 'fs'. Ignored if 'fs's main()
 *      *                       function does not have a color out param.
 *      * @param at             The alpha type of the color produced by 'fs'. Ignored if 'fs's main()
 *      *                       function does not have a color out param. Cannot be kUnknown.
 *      */
 *     static Result Make(SkSpan<const Attribute> attributes,
 *                        size_t                  vertexStride,
 *                        SkSpan<const Varying>   varyings,
 *                        const SkString&         vs,
 *                        const SkString&         fs);
 *     static Result Make(SkSpan<const Attribute> attributes,
 *                        size_t                  vertexStride,
 *                        SkSpan<const Varying>   varyings,
 *                        const SkString&         vs,
 *                        const SkString&         fs,
 *                        sk_sp<SkColorSpace>     cs);
 *     static Result Make(SkSpan<const Attribute> attributes,
 *                        size_t                  vertexStride,
 *                        SkSpan<const Varying>   varyings,
 *                        const SkString&         vs,
 *                        const SkString&         fs,
 *                        sk_sp<SkColorSpace>     cs,
 *                        SkAlphaType             at);
 *
 *     SkSpan<const Attribute> attributes() const { return SkSpan(fAttributes); }
 *
 *     /**
 *      * Combined size of all 'uniform' variables. When creating a SkMesh with this specification
 *      * provide an SkData of this size, containing values for all of those variables. Use uniforms()
 *      * to get the offset of each uniform within the SkData.
 *      */
 *     size_t uniformSize() const;
 *
 *     /**
 *      * Provides info about individual uniforms including the offset into an SkData where each
 *      * uniform value should be placed.
 *      */
 *     SkSpan<const Uniform> uniforms() const { return SkSpan(fUniforms); }
 *
 *     /** Provides basic info about individual children: names, indices and runtime effect type. */
 *     SkSpan<const Child> children() const { return SkSpan(fChildren); }
 *
 *     /** Returns a pointer to the named child's description, or nullptr if not found. */
 *     const Child* findChild(std::string_view name) const;
 *
 *     /** Returns a pointer to the named uniform variable's description, or nullptr if not found. */
 *     const Uniform* findUniform(std::string_view name) const;
 *
 *     /** Returns a pointer to the named attribute, or nullptr if not found. */
 *     const Attribute* findAttribute(std::string_view name) const;
 *
 *     /** Returns a pointer to the named varying, or nullptr if not found. */
 *     const Varying* findVarying(std::string_view name) const;
 *
 *     size_t stride() const { return fStride; }
 *
 *     SkColorSpace* colorSpace() const { return fColorSpace.get(); }
 *
 * private:
 *     friend struct SkMeshSpecificationPriv;
 *
 *     enum class ColorType {
 *         kNone,
 *         kHalf4,
 *         kFloat4,
 *     };
 *
 *     static Result MakeFromSourceWithStructs(SkSpan<const Attribute> attributes,
 *                                             size_t                  stride,
 *                                             SkSpan<const Varying>   varyings,
 *                                             const SkString&         vs,
 *                                             const SkString&         fs,
 *                                             sk_sp<SkColorSpace>     cs,
 *                                             SkAlphaType             at);
 *
 *     SkMeshSpecification(SkSpan<const Attribute>,
 *                         size_t,
 *                         SkSpan<const Varying>,
 *                         int passthroughLocalCoordsVaryingIndex,
 *                         uint32_t deadVaryingMask,
 *                         std::vector<Uniform> uniforms,
 *                         std::vector<Child> children,
 *                         std::unique_ptr<const SkSL::Program>,
 *                         std::unique_ptr<const SkSL::Program>,
 *                         ColorType,
 *                         sk_sp<SkColorSpace>,
 *                         SkAlphaType);
 *
 *     SkMeshSpecification(const SkMeshSpecification&) = delete;
 *     SkMeshSpecification(SkMeshSpecification&&) = delete;
 *
 *     SkMeshSpecification& operator=(const SkMeshSpecification&) = delete;
 *     SkMeshSpecification& operator=(SkMeshSpecification&&) = delete;
 *
 *     const std::vector<Attribute>               fAttributes;
 *     const std::vector<Varying>                 fVaryings;
 *     const std::vector<Uniform>                 fUniforms;
 *     const std::vector<Child>                   fChildren;
 *     const std::unique_ptr<const SkSL::Program> fVS;
 *     const std::unique_ptr<const SkSL::Program> fFS;
 *     const size_t                               fStride;
 *           uint32_t                             fHash;
 *     const int                                  fPassthroughLocalCoordsVaryingIndex;
 *     const uint32_t                             fDeadVaryingMask;
 *     const ColorType                            fColorType;
 *     const sk_sp<SkColorSpace>                  fColorSpace;
 *     const SkAlphaType                          fAlphaType;
 * }
 * ```
 */
public open class SkMeshSpecification public constructor(
  param0: SkMeshSpecification,
) : SkNVRefCnt(),
    SkMeshSpecification {
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kMaxStride       = 1024
   * ```
   */
  private var skSpan: SkMeshSpecification = TODO("Initialize skSpan")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kMaxAttributes   = 8
   * ```
   */
  private val fAttributes: Int = TODO("Initialize fAttributes")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kStrideAlignment = 4
   * ```
   */
  private val fVaryings: Int = TODO("Initialize fVaryings")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kOffsetAlignment = 4
   * ```
   */
  private val fUniforms: Int = TODO("Initialize fUniforms")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kMaxVaryings     = 6
   * ```
   */
  private val fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * SkMeshSpecification(SkSpan<const Attribute>,
   *                         size_t,
   *                         SkSpan<const Varying>,
   *                         int passthroughLocalCoordsVaryingIndex,
   *                         uint32_t deadVaryingMask,
   *                         std::vector<Uniform> uniforms,
   *                         std::vector<Child> children,
   *                         std::unique_ptr<const SkSL::Program>,
   *                         std::unique_ptr<const SkSL::Program>,
   *                         ColorType,
   *                         sk_sp<SkColorSpace>,
   *                         SkAlphaType)
   * ```
   */
  private val fVS: Int = TODO("Initialize fVS")

  /**
   * C++ original:
   * ```cpp
   * const std::vector<Attribute>               fAttributes
   * ```
   */
  private val fFS: Int = TODO("Initialize fFS")

  /**
   * C++ original:
   * ```cpp
   * const std::vector<Varying>                 fVaryings
   * ```
   */
  private val fStride: ULong = TODO("Initialize fStride")

  /**
   * C++ original:
   * ```cpp
   * const std::vector<Uniform>                 fUniforms
   * ```
   */
  private var fHash: UInt = TODO("Initialize fHash")

  /**
   * C++ original:
   * ```cpp
   * const std::vector<Child>                   fChildren
   * ```
   */
  private val fPassthroughLocalCoordsVaryingIndex: Int =
      TODO("Initialize fPassthroughLocalCoordsVaryingIndex")

  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<const SkSL::Program> fVS
   * ```
   */
  private val fDeadVaryingMask: UInt = TODO("Initialize fDeadVaryingMask")

  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<const SkSL::Program> fFS
   * ```
   */
  private val fColorType: ColorType = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * const size_t                               fStride
   * ```
   */
  private val fColorSpace: Int = TODO("Initialize fColorSpace")

  /**
   * C++ original:
   * ```cpp
   * uint32_t                             fHash
   * ```
   */
  private val fAlphaType: SkAlphaType = TODO("Initialize fAlphaType")

  /**
   * C++ original:
   * ```cpp
   * SkMeshSpecification(const SkMeshSpecification&) = delete
   * ```
   */
  public constructor(
    attributes: SkSpan<org.skia.gpu.Attribute>,
    stride: ULong,
    varyings: SkSpan<org.skia.gpu.Varying>,
    passthroughLocalCoordsVaryingIndex: Int,
    deadVaryingMask: UInt,
    uniforms: List<Uniform>,
    children: List<Child>,
    vs: Program?,
    fs: Program?,
    ct: ColorType,
    cs: SkSp<SkColorSpace>,
    at: SkAlphaType,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Attribute> attributes() const { return SkSpan(fAttributes); }
   * ```
   */
  public override fun attributes(): Int {
    TODO("Implement attributes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMeshSpecification::uniformSize() const {
   *     return fUniforms.empty() ? 0
   *                              : SkAlign4(fUniforms.back().offset + fUniforms.back().sizeInBytes());
   * }
   * ```
   */
  public override fun uniformSize(): ULong {
    TODO("Implement uniformSize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Uniform> uniforms() const { return SkSpan(fUniforms); }
   * ```
   */
  public override fun uniforms(): Int {
    TODO("Implement uniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Child> children() const { return SkSpan(fChildren); }
   * ```
   */
  public override fun children(): Int {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * const Child* SkMeshSpecification::findChild(std::string_view name) const {
   *     for (const Child& child : fChildren) {
   *         if (child.name == name) {
   *             return &child;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun findChild(name: String): Int {
    TODO("Implement findChild")
  }

  /**
   * C++ original:
   * ```cpp
   * const Uniform* SkMeshSpecification::findUniform(std::string_view name) const {
   *     for (const Uniform& uniform : fUniforms) {
   *         if (uniform.name == name) {
   *             return &uniform;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun findUniform(name: String): Int {
    TODO("Implement findUniform")
  }

  /**
   * C++ original:
   * ```cpp
   * const Attribute* SkMeshSpecification::findAttribute(std::string_view name) const {
   *     for (const Attribute& attr : fAttributes) {
   *         if (name == attr.name.c_str()) {
   *             return &attr;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun findAttribute(name: String): Attribute {
    TODO("Implement findAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * const Varying* SkMeshSpecification::findVarying(std::string_view name) const {
   *     for (const Varying& varying : fVaryings) {
   *         if (name == varying.name.c_str()) {
   *             return &varying;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun findVarying(name: String): Varying {
    TODO("Implement findVarying")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t stride() const { return fStride; }
   * ```
   */
  public override fun stride(): ULong {
    TODO("Implement stride")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* colorSpace() const { return fColorSpace.get(); }
   * ```
   */
  public override fun colorSpace(): SkColorSpace {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMeshSpecification& operator=(const SkMeshSpecification&) = delete
   * ```
   */
  public override fun assign(param0: SkMeshSpecification) {
    TODO("Implement assign")
  }

  public open class Attribute public constructor(
    public var type: org.skia.gpu.Attribute.Type,
    public var offset: ULong,
    public var name: Int,
  ) {
    public enum class Type {
      kFloat,
      kFloat2,
      kFloat3,
      kFloat4,
      kUByte4_unorm,
      kLast,
    }
  }

  public open class Varying public constructor(
    public var type: org.skia.gpu.Varying.Type,
    public var name: Int,
  ) {
    public enum class Type {
      kFloat,
      kFloat2,
      kFloat3,
      kFloat4,
      kHalf,
      kHalf2,
      kHalf3,
      kHalf4,
      kLast,
    }
  }

  public open class Result public constructor(
    public var specification: Int,
    public var error: Int,
  )

  public enum class ColorType {
    kNone,
    kHalf4,
    kFloat4,
  }

  public companion object {
    public val kMaxStride: ULong = TODO("Initialize kMaxStride")

    public val kMaxAttributes: ULong = TODO("Initialize kMaxAttributes")

    public val kStrideAlignment: ULong = TODO("Initialize kStrideAlignment")

    public val kOffsetAlignment: ULong = TODO("Initialize kOffsetAlignment")

    public val kMaxVaryings: ULong = TODO("Initialize kMaxVaryings")

    /**
     * C++ original:
     * ```cpp
     * SkMeshSpecification::Result SkMeshSpecification::Make(SkSpan<const Attribute> attributes,
     *                                                       size_t vertexStride,
     *                                                       SkSpan<const Varying> varyings,
     *                                                       const SkString& vs,
     *                                                       const SkString& fs) {
     *     return Make(attributes,
     *                 vertexStride,
     *                 varyings,
     *                 vs,
     *                 fs,
     *                 SkColorSpace::MakeSRGB(),
     *                 kPremul_SkAlphaType);
     * }
     * ```
     */
    public override fun make(
      attributes: SkSpan<org.skia.gpu.Attribute>,
      vertexStride: ULong,
      varyings: SkSpan<org.skia.gpu.Varying>,
      vs: String,
      fs: String,
    ): Result {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMeshSpecification::Result SkMeshSpecification::Make(SkSpan<const Attribute> attributes,
     *                                                       size_t vertexStride,
     *                                                       SkSpan<const Varying> varyings,
     *                                                       const SkString& vs,
     *                                                       const SkString& fs,
     *                                                       sk_sp<SkColorSpace> cs) {
     *     return Make(attributes, vertexStride, varyings, vs, fs, std::move(cs), kPremul_SkAlphaType);
     * }
     * ```
     */
    public override fun make(
      attributes: SkSpan<org.skia.gpu.Attribute>,
      vertexStride: ULong,
      varyings: SkSpan<org.skia.gpu.Varying>,
      vs: String,
      fs: String,
      cs: SkSp<SkColorSpace>,
    ): Result {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMeshSpecification::Result SkMeshSpecification::Make(SkSpan<const Attribute> attributes,
     *                                                       size_t vertexStride,
     *                                                       SkSpan<const Varying> varyings,
     *                                                       const SkString& vs,
     *                                                       const SkString& fs,
     *                                                       sk_sp<SkColorSpace> cs,
     *                                                       SkAlphaType at) {
     *     SkString attributesStruct("struct Attributes {\n");
     *     for (const auto& a : attributes) {
     *         attributesStruct.appendf("  %s %s;\n", attribute_type_string(a.type), a.name.c_str());
     *     }
     *     attributesStruct.append("};\n");
     *
     *     bool userProvidedPositionVarying = false;
     *     for (const auto& v : varyings) {
     *         if (v.name.equals("position")) {
     *             if (v.type != Varying::Type::kFloat2) {
     *                 return {nullptr, SkString("Varying \"position\" must have type float2.")};
     *             }
     *             userProvidedPositionVarying = true;
     *         }
     *     }
     *
     *     STArray<kMaxVaryings, Varying> tempVaryings;
     *     if (!userProvidedPositionVarying) {
     *         // Even though we check the # of varyings in MakeFromSourceWithStructs we check here, too,
     *         // to avoid overflow with + 1.
     *         if (varyings.size() > kMaxVaryings - 1) {
     *             RETURN_FAILURE("A maximum of %zu varyings is allowed.", kMaxVaryings);
     *         }
     *         for (const auto& v : varyings) {
     *             tempVaryings.push_back(v);
     *         }
     *         tempVaryings.push_back(Varying{Varying::Type::kFloat2, SkString("position")});
     *         varyings = tempVaryings;
     *     }
     *
     *     SkString varyingStruct("struct Varyings {\n");
     *     for (const auto& v : varyings) {
     *         varyingStruct.appendf("  %s %s;\n", varying_type_string(v.type), v.name.c_str());
     *     }
     *     varyingStruct.append("};\n");
     *
     *     SkString fullVS;
     *     fullVS.append(varyingStruct.c_str());
     *     fullVS.append(attributesStruct.c_str());
     *     fullVS.append(vs.c_str());
     *
     *     SkString fullFS;
     *     fullFS.append(varyingStruct.c_str());
     *     fullFS.append(fs.c_str());
     *
     *     return MakeFromSourceWithStructs(attributes,
     *                                      vertexStride,
     *                                      varyings,
     *                                      fullVS,
     *                                      fullFS,
     *                                      std::move(cs),
     *                                      at);
     * }
     * ```
     */
    public override fun make(
      attributes: SkSpan<org.skia.gpu.Attribute>,
      vertexStride: ULong,
      varyings: SkSpan<org.skia.gpu.Varying>,
      vs: String,
      fs: String,
      cs: SkSp<SkColorSpace>,
      at: SkAlphaType,
    ): Result {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMeshSpecification::Result SkMeshSpecification::MakeFromSourceWithStructs(
     *         SkSpan<const Attribute> attributes,
     *         size_t                  stride,
     *         SkSpan<const Varying>   varyings,
     *         const SkString&         vs,
     *         const SkString&         fs,
     *         sk_sp<SkColorSpace>     cs,
     *         SkAlphaType             at) {
     *     if (auto [ok, error] = check_vertex_offsets_and_stride(attributes, stride); !ok) {
     *         return {nullptr, error};
     *     }
     *
     *     for (const auto& a : attributes) {
     *         if (!check_name(a.name)) {
     *             RETURN_FAILURE("\"%s\" is not a valid attribute name.", a.name.c_str());
     *         }
     *     }
     *
     *     if (varyings.size() > kMaxVaryings) {
     *         RETURN_FAILURE("A maximum of %zu varyings is allowed.", kMaxVaryings);
     *     }
     *
     *     for (const auto& v : varyings) {
     *         if (!check_name(v.name)) {
     *             return {nullptr, SkStringPrintf("\"%s\" is not a valid varying name.", v.name.c_str())};
     *         }
     *     }
     *
     *     std::vector<Uniform> uniforms;
     *     std::vector<Child> children;
     *     size_t offset = 0;
     *
     *     SkSL::Compiler compiler;
     *
     *     // Disable memory pooling; this might slow down compilation slightly, but it will ensure that a
     *     // long-lived mesh specification doesn't waste memory.
     *     SkSL::ProgramSettings settings;
     *     settings.fUseMemoryPool = false;
     *
     *     // TODO(skbug.com/40042585): Add SkCapabilities to the API, check against required version.
     *     std::unique_ptr<SkSL::Program> vsProgram = compiler.convertProgram(
     *             SkSL::ProgramKind::kMeshVertex,
     *             std::string(vs.c_str()),
     *             settings);
     *     if (!vsProgram) {
     *         RETURN_FAILURE("VS: %s", compiler.errorText().c_str());
     *     }
     *
     *     if (auto [result, error] = gather_uniforms_and_check_for_main(
     *                 *vsProgram,
     *                 &uniforms,
     *                 &children,
     *                 SkMeshSpecification::Uniform::Flags::kVertex_Flag,
     *                 &offset);
     *         !result) {
     *         return {nullptr, std::move(error)};
     *     }
     *
     *     if (SkSL::Analysis::CallsColorTransformIntrinsics(*vsProgram)) {
     *         RETURN_FAILURE("Color transform intrinsics are not permitted in custom mesh shaders");
     *     }
     *
     *     std::unique_ptr<SkSL::Program> fsProgram = compiler.convertProgram(
     *             SkSL::ProgramKind::kMeshFragment,
     *             std::string(fs.c_str()),
     *             settings);
     *
     *     if (!fsProgram) {
     *         RETURN_FAILURE("FS: %s", compiler.errorText().c_str());
     *     }
     *
     *     if (auto [result, error] = gather_uniforms_and_check_for_main(
     *                 *fsProgram,
     *                 &uniforms,
     *                 &children,
     *                 SkMeshSpecification::Uniform::Flags::kFragment_Flag,
     *                 &offset);
     *         !result) {
     *         return {nullptr, std::move(error)};
     *     }
     *
     *     if (SkSL::Analysis::CallsColorTransformIntrinsics(*fsProgram)) {
     *         RETURN_FAILURE("Color transform intrinsics are not permitted in custom mesh shaders");
     *     }
     *
     *     ColorType ct = get_fs_color_type(*fsProgram);
     *
     *     if (ct == ColorType::kNone) {
     *         cs = nullptr;
     *         at = kPremul_SkAlphaType;
     *     } else {
     *         if (!cs) {
     *             return {nullptr, SkString{"Must provide a color space if FS returns a color."}};
     *         }
     *         if (at == kUnknown_SkAlphaType) {
     *             return {nullptr, SkString{"Must provide a valid alpha type if FS returns a color."}};
     *         }
     *     }
     *
     *     uint32_t deadVaryingMask;
     *     int passthroughLocalCoordsVaryingIndex =
     *             check_for_passthrough_local_coords_and_dead_varyings(*fsProgram, &deadVaryingMask);
     *
     *     if (passthroughLocalCoordsVaryingIndex >= 0) {
     *         SkASSERT(varyings[passthroughLocalCoordsVaryingIndex].type == Varying::Type::kFloat2);
     *     }
     *
     *     return {sk_sp<SkMeshSpecification>(new SkMeshSpecification(attributes,
     *                                                                stride,
     *                                                                varyings,
     *                                                                passthroughLocalCoordsVaryingIndex,
     *                                                                deadVaryingMask,
     *                                                                std::move(uniforms),
     *                                                                std::move(children),
     *                                                                std::move(vsProgram),
     *                                                                std::move(fsProgram),
     *                                                                ct,
     *                                                                std::move(cs),
     *                                                                at)),
     *             /*error=*/{}};
     * }
     * ```
     */
    public override fun makeFromSourceWithStructs(
      attributes: SkSpan<org.skia.gpu.Attribute>,
      stride: ULong,
      varyings: SkSpan<org.skia.gpu.Varying>,
      vs: String,
      fs: String,
      cs: SkSp<SkColorSpace>,
      at: SkAlphaType,
    ): Result {
      TODO("Implement makeFromSourceWithStructs")
    }
  }
}

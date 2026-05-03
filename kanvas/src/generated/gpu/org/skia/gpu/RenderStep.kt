package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.UInt
import kotlin.initializer_list
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkSpan
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class RenderStep {
 * public:
 *     virtual ~RenderStep() = default;
 *
 *     // Returns an empty result if no state change is necessary, otherwise returns the scissor rect
 *     // that should be active for all draws recorded by a subsequent call to writeVertices().
 *     std::optional<SkIRect> getScissor(const DrawParams&,
 *                                       SkIRect currentScissor,
 *                                       SkIRect deviceBounds) const;
 *
 *     // The DrawWriter is configured with the vertex and instance strides of the RenderStep, and its
 *     // primitive type. The recorded draws will be executed with a graphics pipeline compatible with
 *     // this RenderStep.
 *     virtual void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const = 0;
 *
 *     // Write out the uniform values (aligned for the layout), textures, and samplers. The uniform
 *     // values will be de-duplicated across all draws using the RenderStep before uploading to the
 *     // GPU, but it can be assumed the uniforms will be bound before the draws recorded in
 *     // 'writeVertices' are executed.
 *     virtual void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const = 0;
 *
 *     // Returns the body of a vertex function, which must define a float4 devPosition variable and
 *     // must write to an already-defined float2 stepLocalCoords variable. This will be automatically
 *     // set to a varying for the fragment shader if the paint requires local coords. This SkSL has
 *     // access to the variables declared by vertexAttributes(), instanceAttributes(), and uniforms().
 *     // The 'devPosition' variable's z must store the PaintDepth normalized to a float from [0, 1],
 *     // for each processed draw although the RenderStep can choose to upload it in any manner.
 *     //
 *     // NOTE: The above contract is mainly so that the entire SkSL program can be created by just str
 *     // concatenating struct definitions generated from the RenderStep and paint Combination
 *     // and then including the function bodies returned here.
 *     virtual std::string vertexSkSL() const = 0;
 *
 *     // Emits code to set up textures and samplers. Should only be defined if hasTextures is true.
 *     virtual std::string texturesAndSamplersSkSL(const ResourceBindingRequirements&,
 *                                                 int* nextBindingIndex) const {
 *         return "";
 *     }
 *
 *     // Emits code to set up coverage value. Should only be defined if overridesCoverage is true.
 *     // When implemented the returned SkSL fragment should write its coverage into a
 *     // 'half4 outputCoverage' variable (defined in the calling code) with the actual
 *     // coverage splatted out into all four channels.
 *     virtual const char* fragmentCoverageSkSL() const { return ""; }
 *
 *     // Emits code to set up a primitive color value. Should only be defined if emitsPrimitiveColor
 *     // is true. When implemented, the returned SkSL fragment should write its color into a
 *     // 'half4 primitiveColor' variable (defined in the calling code).
 *     virtual const char* fragmentColorSkSL() const { return ""; }
 *
 *     // Indicates whether this RenderStep's uniforms are referenced in its fragment shader code.
 *     // If not, its uniforms can be omitted from the fragment shader entirely.
 *     // By default, we assume that RenderSteps use their uniforms for emitting coverage or primitive
 *     // colors.
 *     virtual bool usesUniformsInFragmentSkSL() const {
 *         return this->coverage() != Coverage::kNone || this->emitsPrimitiveColor();
 *     }
 *
 *     // Returns a name formatted as "Subclass[variant]", where "Subclass" matches the C++ class name
 *     // and variant is a unique term describing instance's specific configuration.
 *     const char* name() const { return RenderStepName(fRenderStepID); }
 *
 *     bool requiresMSAA()        const { return SkToBool(fFlags & Flags::kRequiresMSAA);        }
 *     bool performsShading()     const { return SkToBool(fFlags & Flags::kPerformsShading);     }
 *     bool hasTextures()         const { return SkToBool(fFlags & Flags::kHasTextures);         }
 *     bool emitsPrimitiveColor() const { return SkToBool(fFlags & Flags::kEmitsPrimitiveColor); }
 *     bool outsetBoundsForAA()   const { return SkToBool(fFlags & Flags::kOutsetBoundsForAA);   }
 *     bool useNonAAInnerFill()   const { return SkToBool(fFlags & Flags::kUseNonAAInnerFill);   }
 *     bool appendsVertices()     const { return SkToBool(fFlags & Flags::kAppendVertices);      }
 *     SkEnumBitMask<RenderStateFlags> getRenderStateFlags() const {
 *         SkEnumBitMask<RenderStateFlags> rs = RenderStateFlags::kNone;
 *         if (fFlags & Flags::kFixed)             { rs |= RenderStateFlags::kFixed;           }
 *         if (fFlags & Flags::kAppendVertices)    { rs |= RenderStateFlags::kAppendVertices;  }
 *         if (fFlags & Flags::kAppendInstances)   { rs |= RenderStateFlags::kAppendInstances; }
 *         if (fFlags & Flags::kAppendDynamicInstances) {
 *              rs |= RenderStateFlags::kAppendDynamicInstances;
 *         }
 *         return rs;
 *     }
 *
 *     Coverage coverage() const { return RenderStep::GetCoverage(fFlags); }
 *
 *     PrimitiveType primitiveType()    const { return fPrimitiveType;    }
 *     size_t        staticDataStride() const { return fStaticDataStride; }
 *     size_t        appendDataStride() const { return fAppendDataStride; }
 *
 *     size_t numUniforms()         const { return fUniforms.size();    }
 *     int    uniformAlignment()    const { return fUniformAlignment;   }
 *     size_t numStaticAttributes() const { return fStaticAttrs.size(); }
 *     size_t numAppendAttributes() const { return fAppendAttrs.size(); }
 *
 *     // Name of an attribute containing both the render step and shading SSBO index, if used.
 *     static const char* ssboIndexAttribute() { return "ssboIndex"; }
 *
 *     // Name of a varying to pass the SSBO index to fragment shader
 *     static const char* ssboIndexVarying() { return "ssboIndexVar"; }
 *
 *     // The uniforms of a RenderStep are bound to the kRenderStep slot, the rest of the pipeline
 *     // may still use uniforms bound to other slots.
 *     SkSpan<const Uniform>   uniforms()         const { return SkSpan(fUniforms);      }
 *     SkSpan<const Attribute> staticAttributes() const { return SkSpan(fStaticAttrs);   }
 *     SkSpan<const Attribute> appendAttributes() const { return SkSpan(fAppendAttrs);   }
 *     SkSpan<const Varying>   varyings()         const { return SkSpan(fVaryings);      }
 *
 *     const DepthStencilSettings& depthStencilSettings() const { return fDepthStencilSettings; }
 *
 *     SkEnumBitMask<DepthStencilFlags> depthStencilFlags() const {
 *         return (fDepthStencilSettings.fStencilTestEnabled
 *                         ? DepthStencilFlags::kStencil : DepthStencilFlags::kNone) |
 *                (fDepthStencilSettings.fDepthTestEnabled || fDepthStencilSettings.fDepthWriteEnabled
 *                         ? DepthStencilFlags::kDepth : DepthStencilFlags::kNone);
 *     }
 *
 *     static const int kRenderStepIDVersion = 1;
 *
 * #define ENUM1(BaseName) k##BaseName,
 * #define ENUM2(BaseName, VariantName) k##BaseName##_##VariantName,
 *     enum class RenderStepID : uint32_t {
 *         SKGPU_RENDERSTEP_TYPES(ENUM1, ENUM2)
 *
 *         kLast = kVertices_TristripsColorTexCoords,
 *     };
 * #undef ENUM1
 * #undef ENUM2
 *     static const int kNumRenderSteps = static_cast<int>(RenderStepID::kLast) + 1;
 *
 *     RenderStepID renderStepID() const { return fRenderStepID; }
 *
 *     static const char* RenderStepName(RenderStepID);
 *     static bool IsValidRenderStepID(uint32_t);
 *
 *     // TODO: Actual API to do things
 *     // 6. Some Renderers benefit from being able to share vertices between RenderSteps. Must find a
 *     //    way to support that. It may mean that RenderSteps get state per draw.
 *     //    - Does Renderer make RenderStepFactories that create steps for each DrawList::Draw?
 *     //    - Does DrawList->DrawPass conversion build a separate array of blind data that the
 *     //      stateless Renderstep can refer to for {draw,step} pairs?
 *     //    - Does each DrawList::Draw have extra space (e.g. 8 bytes) that steps can cache data in?
 * protected:
 * enum class Flags : unsigned {
 *     kNone                   = 0x0000,
 *     kFixed                  = 0x0001, // Uses explicit DrawWriter::draw functions
 *     kAppendVertices         = 0x0002, // Appends vertices
 *     kAppendInstances        = 0x0004, // Appends instances with static vertex count
 *     kAppendDynamicInstances = 0x0008, // Appends instances with a flexible vertex count
 *     kRequiresMSAA           = 0x0010, // MSAA is required for anti-aliasing
 *     kPerformsShading        = 0x0020, // This step is responsible for shading/color output
 *     kHasTextures            = 0x0040, // Adds textures via overridden texturesAndSamplersSkSL()
 *     kEmitsCoverage          = 0x0080, // Adds analytic coverage via fragmentCoverageSkSL()
 *     kLCDCoverage            = 0x0100, // The added analytic coverage is LCD, not single channel
 *     kEmitsPrimitiveColor    = 0x0200, // Injects primitive color via fragmentColorSkSL()
 *     kOutsetBoundsForAA      = 0x0400, // Drawn geometry will be outset beyond shape's bounds for AA
 *     kUseNonAAInnerFill      = 0x0800, // Opt into Device recording extra inner fill draws
 *     kIgnoreInverseFill      = 0x1000, // Rasterization treats all shapes as non-inverted for scissor
 *     kInverseFillsScissor    = 0x2000, // Rasterization of inverse fills scissor geometrically
 * };
 * SK_DECL_BITMASK_OPS_FRIENDS(Flags)
 *
 *     // While RenderStep does not define the full program that's run for a draw, it defines the
 *     // entire vertex layout of the pipeline. This is not allowed to change, so can be provided to
 *     // the RenderStep constructor by subclasses.
 *     RenderStep(Layout layout,
 *                RenderStepID renderStepID,
 *                SkEnumBitMask<Flags> flags,
 *                std::initializer_list<Uniform> uniforms,
 *                PrimitiveType primitiveType,
 *                DepthStencilSettings depthStencilSettings,
 *                SkSpan<const Attribute> staticAttrs,
 *                SkSpan<const Attribute> appendAttrs,
 *                SkSpan<const Varying> varyings = {});
 *
 * private:
 *     friend class Renderer; // for Flags
 *
 *     // Cannot copy or move
 *     RenderStep(const RenderStep&) = delete;
 *     RenderStep(RenderStep&&)      = delete;
 *
 *     static Coverage GetCoverage(SkEnumBitMask<Flags>);
 *
 *     RenderStepID fRenderStepID;
 *     SkEnumBitMask<Flags> fFlags;
 *     PrimitiveType        fPrimitiveType;
 *
 *     DepthStencilSettings fDepthStencilSettings;
 *
 *     // TODO: When we always use C++17 for builds, we should be able to just let subclasses declare
 *     // constexpr arrays and point to those, but we need explicit storage for C++14.
 *     // Alternatively, if we imposed a max attr count, similar to Renderer's num render steps, we
 *     // could just have this be std::array and keep all attributes inline with the RenderStep memory.
 *     // On the other hand, the attributes are only needed when creating a new pipeline so it's not
 *     // that performance sensitive.
 *     std::vector<Uniform>   fUniforms;
 *     std::vector<Attribute> fStaticAttrs;
 *     std::vector<Attribute> fAppendAttrs;
 *     std::vector<Varying>   fVaryings;
 *
 *     int    fUniformAlignment; // derived from the renderstep uniforms
 *     size_t fStaticDataStride; // derived from vertex attribute set
 *     size_t fAppendDataStride; // derived from instance attribute set
 * }
 * ```
 */
public abstract class RenderStep public constructor(
  param0: RenderStep,
) {
  /**
   * C++ original:
   * ```cpp
   * static const int kRenderStepIDVersion = 1
   * ```
   */
  private var fRenderStepID: RenderStepID = TODO("Initialize fRenderStepID")

  /**
   * C++ original:
   * ```cpp
   * static const int kNumRenderSteps = static_cast<int>(RenderStepID::kLast) + 1
   * ```
   */
  private var fFlags: Int = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * RenderStepID fRenderStepID
   * ```
   */
  private var fPrimitiveType: Int = TODO("Initialize fPrimitiveType")

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<Flags> fFlags
   * ```
   */
  private var fDepthStencilSettings: Int = TODO("Initialize fDepthStencilSettings")

  /**
   * C++ original:
   * ```cpp
   * PrimitiveType        fPrimitiveType
   * ```
   */
  private var fUniforms: Int = TODO("Initialize fUniforms")

  /**
   * C++ original:
   * ```cpp
   * DepthStencilSettings fDepthStencilSettings
   * ```
   */
  private var fStaticAttrs: Int = TODO("Initialize fStaticAttrs")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Uniform>   fUniforms
   * ```
   */
  private var fAppendAttrs: Int = TODO("Initialize fAppendAttrs")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Attribute> fStaticAttrs
   * ```
   */
  private var fVaryings: Int = TODO("Initialize fVaryings")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Attribute> fAppendAttrs
   * ```
   */
  private var fUniformAlignment: Int = TODO("Initialize fUniformAlignment")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Varying>   fVaryings
   * ```
   */
  private var fStaticDataStride: Int = TODO("Initialize fStaticDataStride")

  /**
   * C++ original:
   * ```cpp
   * int    fUniformAlignment
   * ```
   */
  private var fAppendDataStride: Int = TODO("Initialize fAppendDataStride")

  /**
   * C++ original:
   * ```cpp
   * RenderStep(const RenderStep&) = delete
   * ```
   */
  public constructor(
    layout: Layout,
    renderStepID: RenderStepID,
    flags: SkEnumBitMask<org.skia.`external`.Flags>,
    uniforms: initializer_list<Uniform>,
    primitiveType: PrimitiveType,
    depthStencilSettings: DepthStencilSettings,
    staticAttrs: SkSpan<Attribute>,
    appendAttrs: SkSpan<Attribute>,
    varyings: SkSpan<Varying>,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkIRect> RenderStep::getScissor(const DrawParams& params,
   *                                               SkIRect currentScissor,
   *                                               SkIRect deviceBounds) const {
   *     if (currentScissor == params.scissor()) {
   *         return {}; // Trivially no change in scissor state is required
   *     }
   *
   *     Rect drawBounds = params.drawBounds();
   *     if (params.geometry().isShape() && params.geometry().shape().inverted()) {
   *         // For inverse filled shapes, the scissor is able to be handled in unique ways.
   *         if (fFlags & Flags::kInverseFillsScissor) {
   *             // In this case, the RenderStep geometrically respects the scissor so as long as the
   *             // current scissor doesn't interfere, we don't need a state change.
   *             if (currentScissor.contains(params.scissor())) {
   *                 return {};
   *             } else {
   *                 // This draw doesn't need a scissor at all, so return the device bounds. It is
   *                 // expected that this will generally lead to fewer scissor state changes (for
   *                 // instance when applying the cover steps for a lot of inverse-filled intersect
   *                 // clip depth-only draws). However, it could lead to a redundant scissor change if
   *                 // the next draw would have used this draw's original scissor.
   *                 return deviceBounds;
   *             }
   *         }
   *
   *         if (fFlags & Flags::kIgnoreInverseFill) {
   *             // In this case params.drawBounds() fills the scissor from the inverse fill rule,
   *             // but we want to apply the scissor as if it were a regular fill.
   *             drawBounds = params.transformedShapeBounds(); // this ignores fill rule
   *             drawBounds.intersect(params.scissor());
   *         } // Else leave drawBounds filling the original scissor
   *
   *         // Fall through to regular scissor state checking with the possibly-updated bounds
   *     }
   *
   *     // Draws that are unaffected by a clip stack will have a scissor matching the device's bounds.
   *     // If their transformed shape bounds clipped to the current scissor are no different than their
   *     // draw bounds (clipped to the original scissor), then no state change is required.
   *     Rect currentClippedBounds = params.transformedShapeBounds();
   *     currentClippedBounds.intersect(currentScissor);
   *     if (currentClippedBounds == drawBounds) {
   *         return {};
   *     }
   *
   *     if (drawBounds == params.transformedShapeBounds()) {
   *         // We need to change the scissor, but the registered scissor is a no-op so
   *         // use the device bounds as a canonical scissor.
   *         return deviceBounds;
   *     }
   *
   *     return params.scissor();
   * }
   * ```
   */
  public fun getScissor(
    param0: DrawParams,
    currentScissor: SkIRect,
    deviceBounds: SkIRect,
  ): Int {
    TODO("Implement getScissor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const = 0
   * ```
   */
  public abstract fun writeVertices(
    param0: DrawWriter?,
    param1: DrawParams,
    ssboIndex: UInt,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const = 0
   * ```
   */
  public abstract fun writeUniformsAndTextures(param0: DrawParams, param1: PipelineDataGatherer?)

  /**
   * C++ original:
   * ```cpp
   * virtual std::string vertexSkSL() const = 0
   * ```
   */
  public abstract fun vertexSkSL(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual std::string texturesAndSamplersSkSL(const ResourceBindingRequirements&,
   *                                                 int* nextBindingIndex) const {
   *         return "";
   *     }
   * ```
   */
  public open fun texturesAndSamplersSkSL(param0: ResourceBindingRequirements, nextBindingIndex: Int?): Int {
    TODO("Implement texturesAndSamplersSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const char* fragmentCoverageSkSL() const { return ""; }
   * ```
   */
  public open fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const char* fragmentColorSkSL() const { return ""; }
   * ```
   */
  public open fun fragmentColorSkSL(): Char {
    TODO("Implement fragmentColorSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool usesUniformsInFragmentSkSL() const {
   *         return this->coverage() != Coverage::kNone || this->emitsPrimitiveColor();
   *     }
   * ```
   */
  public open fun usesUniformsInFragmentSkSL(): Boolean {
    TODO("Implement usesUniformsInFragmentSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* name() const { return RenderStepName(fRenderStepID); }
   * ```
   */
  public fun name(): Char {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requiresMSAA()        const { return SkToBool(fFlags & Flags::kRequiresMSAA);        }
   * ```
   */
  public fun requiresMSAA(): Boolean {
    TODO("Implement requiresMSAA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool performsShading()     const { return SkToBool(fFlags & Flags::kPerformsShading);     }
   * ```
   */
  public fun performsShading(): Boolean {
    TODO("Implement performsShading")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasTextures()         const { return SkToBool(fFlags & Flags::kHasTextures);         }
   * ```
   */
  public fun hasTextures(): Boolean {
    TODO("Implement hasTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * bool emitsPrimitiveColor() const { return SkToBool(fFlags & Flags::kEmitsPrimitiveColor); }
   * ```
   */
  public fun emitsPrimitiveColor(): Boolean {
    TODO("Implement emitsPrimitiveColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool outsetBoundsForAA()   const { return SkToBool(fFlags & Flags::kOutsetBoundsForAA);   }
   * ```
   */
  public fun outsetBoundsForAA(): Boolean {
    TODO("Implement outsetBoundsForAA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool useNonAAInnerFill()   const { return SkToBool(fFlags & Flags::kUseNonAAInnerFill);   }
   * ```
   */
  public fun useNonAAInnerFill(): Boolean {
    TODO("Implement useNonAAInnerFill")
  }

  /**
   * C++ original:
   * ```cpp
   * bool appendsVertices()     const { return SkToBool(fFlags & Flags::kAppendVertices);      }
   * ```
   */
  public fun appendsVertices(): Boolean {
    TODO("Implement appendsVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<RenderStateFlags> getRenderStateFlags() const {
   *         SkEnumBitMask<RenderStateFlags> rs = RenderStateFlags::kNone;
   *         if (fFlags & Flags::kFixed)             { rs |= RenderStateFlags::kFixed;           }
   *         if (fFlags & Flags::kAppendVertices)    { rs |= RenderStateFlags::kAppendVertices;  }
   *         if (fFlags & Flags::kAppendInstances)   { rs |= RenderStateFlags::kAppendInstances; }
   *         if (fFlags & Flags::kAppendDynamicInstances) {
   *              rs |= RenderStateFlags::kAppendDynamicInstances;
   *         }
   *         return rs;
   *     }
   * ```
   */
  public fun getRenderStateFlags(): Int {
    TODO("Implement getRenderStateFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * Coverage coverage() const { return RenderStep::GetCoverage(fFlags); }
   * ```
   */
  public fun coverage(): Coverage {
    TODO("Implement coverage")
  }

  /**
   * C++ original:
   * ```cpp
   * PrimitiveType primitiveType()    const { return fPrimitiveType;    }
   * ```
   */
  public fun primitiveType(): Int {
    TODO("Implement primitiveType")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t        staticDataStride() const { return fStaticDataStride; }
   * ```
   */
  public fun staticDataStride(): Int {
    TODO("Implement staticDataStride")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t        appendDataStride() const { return fAppendDataStride; }
   * ```
   */
  public fun appendDataStride(): Int {
    TODO("Implement appendDataStride")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t numUniforms()         const { return fUniforms.size();    }
   * ```
   */
  public fun numUniforms(): Int {
    TODO("Implement numUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * int    uniformAlignment()    const { return fUniformAlignment;   }
   * ```
   */
  public fun uniformAlignment(): Int {
    TODO("Implement uniformAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t numStaticAttributes() const { return fStaticAttrs.size(); }
   * ```
   */
  public fun numStaticAttributes(): Int {
    TODO("Implement numStaticAttributes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t numAppendAttributes() const { return fAppendAttrs.size(); }
   * ```
   */
  public fun numAppendAttributes(): Int {
    TODO("Implement numAppendAttributes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Uniform>   uniforms()         const { return SkSpan(fUniforms);      }
   * ```
   */
  public fun uniforms(): Int {
    TODO("Implement uniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Attribute> staticAttributes() const { return SkSpan(fStaticAttrs);   }
   * ```
   */
  public fun staticAttributes(): Int {
    TODO("Implement staticAttributes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Attribute> appendAttributes() const { return SkSpan(fAppendAttrs);   }
   * ```
   */
  public fun appendAttributes(): Int {
    TODO("Implement appendAttributes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Varying>   varyings()         const { return SkSpan(fVaryings);      }
   * ```
   */
  public fun varyings(): Int {
    TODO("Implement varyings")
  }

  /**
   * C++ original:
   * ```cpp
   * const DepthStencilSettings& depthStencilSettings() const { return fDepthStencilSettings; }
   * ```
   */
  public fun depthStencilSettings(): Int {
    TODO("Implement depthStencilSettings")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<DepthStencilFlags> depthStencilFlags() const {
   *         return (fDepthStencilSettings.fStencilTestEnabled
   *                         ? DepthStencilFlags::kStencil : DepthStencilFlags::kNone) |
   *                (fDepthStencilSettings.fDepthTestEnabled || fDepthStencilSettings.fDepthWriteEnabled
   *                         ? DepthStencilFlags::kDepth : DepthStencilFlags::kNone);
   *     }
   * ```
   */
  public fun depthStencilFlags(): Int {
    TODO("Implement depthStencilFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * RenderStepID renderStepID() const { return fRenderStepID; }
   * ```
   */
  public fun renderStepID(): RenderStepID {
    TODO("Implement renderStepID")
  }

  public enum class RenderStepID {
    kLast,
  }

  public enum class Flags {
    kNone,
    kFixed,
    kAppendVertices,
    kAppendInstances,
    kAppendDynamicInstances,
    kRequiresMSAA,
    kPerformsShading,
    kHasTextures,
    kEmitsCoverage,
    kLCDCoverage,
    kEmitsPrimitiveColor,
    kOutsetBoundsForAA,
    kUseNonAAInnerFill,
    kIgnoreInverseFill,
    kInverseFillsScissor,
  }

  public companion object {
    public val kRenderStepIDVersion: Int = TODO("Initialize kRenderStepIDVersion")

    public val kNumRenderSteps: Int = TODO("Initialize kNumRenderSteps")

    /**
     * C++ original:
     * ```cpp
     * static const char* ssboIndexAttribute() { return "ssboIndex"; }
     * ```
     */
    public fun ssboIndexAttribute(): Char {
      TODO("Implement ssboIndexAttribute")
    }

    /**
     * C++ original:
     * ```cpp
     * static const char* ssboIndexVarying() { return "ssboIndexVar"; }
     * ```
     */
    public fun ssboIndexVarying(): Char {
      TODO("Implement ssboIndexVarying")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* RenderStep::RenderStepName(RenderStepID id) {
     * #define CASE1(BaseName) case RenderStepID::k##BaseName: return #BaseName "RenderStep";
     * #define CASE2(BaseName, VariantName) \
     *     case RenderStepID::k##BaseName##_##VariantName: return #BaseName "RenderStep[" #VariantName "]";
     *
     *     switch (id) {
     *         SKGPU_RENDERSTEP_TYPES(CASE1, CASE2)
     *     }
     * #undef CASE1
     * #undef CASE2
     *
     *     SkUNREACHABLE;
     * }
     * ```
     */
    public fun renderStepName(id: RenderStepID): Char {
      TODO("Implement renderStepName")
    }

    /**
     * C++ original:
     * ```cpp
     * bool RenderStep::IsValidRenderStepID(uint32_t renderStepID) {
     *     return renderStepID > (int) RenderStep::RenderStepID::kInvalid &&
     *            renderStepID < RenderStep::kNumRenderSteps;
     * }
     * ```
     */
    public fun isValidRenderStepID(renderStepID: UInt): Boolean {
      TODO("Implement isValidRenderStepID")
    }

    /**
     * C++ original:
     * ```cpp
     * Coverage RenderStep::GetCoverage(SkEnumBitMask<Flags> flags) {
     *     return !(flags & Flags::kEmitsCoverage) ? Coverage::kNone
     *            : (flags & Flags::kLCDCoverage)  ? Coverage::kLCD
     *                                             : Coverage::kSingleChannel;
     * }
     * ```
     */
    private fun getCoverage(flags: SkEnumBitMask<org.skia.`external`.Flags>): Coverage {
      TODO("Implement getCoverage")
    }
  }
}

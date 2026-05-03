package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class Renderer {
 *     using StepFlags = RenderStep::Flags;
 * public:
 *     // The maximum number of render steps that any Renderer is allowed to have.
 *     static constexpr int kMaxRenderSteps = 4;
 *
 *     const RenderStep& step(int i) const {
 *         SkASSERT(i >= 0 && i < fStepCount);
 *         return *fSteps[i];
 *     }
 *     SkSpan<const RenderStep* const> steps() const {
 *         SkASSERT(fStepCount > 0); // steps() should only be called on valid Renderers.
 *         return {fSteps.data(), static_cast<size_t>(fStepCount) };
 *     }
 *
 *     const char*   name()           const { return fName.c_str(); }
 *     DrawTypeFlags drawTypes()      const { return fDrawTypes; }
 *     int           numRenderSteps() const { return fStepCount;    }
 *
 *     bool requiresMSAA() const {
 *         return SkToBool(fStepFlags & StepFlags::kRequiresMSAA);
 *     }
 *     bool emitsPrimitiveColor() const {
 *         return SkToBool(fStepFlags & StepFlags::kEmitsPrimitiveColor);
 *     }
 *     bool outsetBoundsForAA() const {
 *         return SkToBool(fStepFlags & StepFlags::kOutsetBoundsForAA);
 *     }
 *     bool useNonAAInnerFill() const {
 *         return SkToBool(fStepFlags & StepFlags::kUseNonAAInnerFill);
 *     }
 *
 *     SkEnumBitMask<DepthStencilFlags> depthStencilFlags() const { return fDepthStencilFlags; }
 *
 *     Coverage coverage() const { return RenderStep::GetCoverage(fStepFlags); }
 *
 * private:
 *     friend class RendererProvider; // for ctors
 *
 *     // Max render steps is 4, so just spell the options out for now...
 *     Renderer(std::string_view name, DrawTypeFlags drawTypes, const RenderStep* s1)
 *             : Renderer(name, drawTypes, std::array<const RenderStep*, 1>{s1}) {}
 *
 *     Renderer(std::string_view name, DrawTypeFlags drawTypes,
 *              const RenderStep* s1, const RenderStep* s2)
 *             : Renderer(name, drawTypes, std::array<const RenderStep*, 2>{s1, s2}) {}
 *
 *     Renderer(std::string_view name, DrawTypeFlags drawTypes,
 *              const RenderStep* s1, const RenderStep* s2, const RenderStep* s3)
 *             : Renderer(name, drawTypes, std::array<const RenderStep*, 3>{s1, s2, s3}) {}
 *
 *     Renderer(std::string_view name, DrawTypeFlags drawTypes,
 *              const RenderStep* s1, const RenderStep* s2, const RenderStep* s3, const RenderStep* s4)
 *             : Renderer(name, drawTypes, std::array<const RenderStep*, 4>{s1, s2, s3, s4}) {}
 *
 *     template<size_t N>
 *     Renderer(std::string_view name, DrawTypeFlags drawTypes, std::array<const RenderStep*, N> steps)
 *             : fName(name)
 *             , fDrawTypes(drawTypes)
 *             , fStepCount(SkTo<int>(N)) {
 *         static_assert(N <= kMaxRenderSteps);
 *         for (int i = 0 ; i < fStepCount; ++i) {
 *             fSteps[i] = steps[i];
 *             fStepFlags |= fSteps[i]->fFlags;
 *             fDepthStencilFlags |= fSteps[i]->depthStencilFlags();
 *         }
 *         // At least one step needs to actually shade.
 *         SkASSERT(fStepFlags & RenderStep::Flags::kPerformsShading);
 *         // A render step using non-AA inner fills with a second draw should not also be part of a
 *         // multi-step renderer (to keep reasoning simple) and must use the LESS depth test.
 *         SkASSERT(!this->useNonAAInnerFill() ||
 *                  (fStepCount == 1 && fSteps[0]->depthStencilSettings().fDepthTestEnabled &&
 *                   fSteps[0]->depthStencilSettings().fDepthCompareOp == CompareOp::kLess));
 *     }
 *
 *     // For RendererProvider to manage initialization; it will never expose a Renderer that is only
 *     // default-initialized and not replaced because it's algorithm is disabled by caps/options.
 *     Renderer() : fSteps(), fName(""), fStepCount(0) {}
 *     Renderer& operator=(Renderer&&) = default;
 *
 *     std::array<const RenderStep*, kMaxRenderSteps> fSteps;
 *     std::string fName;
 *     DrawTypeFlags fDrawTypes = DrawTypeFlags::kNone;
 *     int fStepCount;
 *
 *     SkEnumBitMask<StepFlags> fStepFlags = StepFlags::kNone;
 *     SkEnumBitMask<DepthStencilFlags> fDepthStencilFlags = DepthStencilFlags::kNone;
 * }
 * ```
 */
public data class Renderer public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxRenderSteps = 4
   * ```
   */
  private var fSteps: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<const RenderStep*, kMaxRenderSteps> fSteps
   * ```
   */
  private var fName: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string fName
   * ```
   */
  private var fDrawTypes: Int,
  /**
   * C++ original:
   * ```cpp
   * DrawTypeFlags fDrawTypes
   * ```
   */
  private var fStepCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fStepCount
   * ```
   */
  private var fStepFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<StepFlags> fStepFlags
   * ```
   */
  private var fDepthStencilFlags: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const RenderStep& step(int i) const {
   *         SkASSERT(i >= 0 && i < fStepCount);
   *         return *fSteps[i];
   *     }
   * ```
   */
  public fun step(i: Int): RenderStep {
    TODO("Implement step")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const RenderStep* const> steps() const {
   *         SkASSERT(fStepCount > 0); // steps() should only be called on valid Renderers.
   *         return {fSteps.data(), static_cast<size_t>(fStepCount) };
   *     }
   * ```
   */
  public fun steps(): Int {
    TODO("Implement steps")
  }

  /**
   * C++ original:
   * ```cpp
   * const char*   name()           const { return fName.c_str(); }
   * ```
   */
  public fun name(): Char {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawTypeFlags drawTypes()      const { return fDrawTypes; }
   * ```
   */
  public fun drawTypes(): Int {
    TODO("Implement drawTypes")
  }

  /**
   * C++ original:
   * ```cpp
   * int           numRenderSteps() const { return fStepCount;    }
   * ```
   */
  public fun numRenderSteps(): Int {
    TODO("Implement numRenderSteps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requiresMSAA() const {
   *         return SkToBool(fStepFlags & StepFlags::kRequiresMSAA);
   *     }
   * ```
   */
  public fun requiresMSAA(): Boolean {
    TODO("Implement requiresMSAA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool emitsPrimitiveColor() const {
   *         return SkToBool(fStepFlags & StepFlags::kEmitsPrimitiveColor);
   *     }
   * ```
   */
  public fun emitsPrimitiveColor(): Boolean {
    TODO("Implement emitsPrimitiveColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool outsetBoundsForAA() const {
   *         return SkToBool(fStepFlags & StepFlags::kOutsetBoundsForAA);
   *     }
   * ```
   */
  public fun outsetBoundsForAA(): Boolean {
    TODO("Implement outsetBoundsForAA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool useNonAAInnerFill() const {
   *         return SkToBool(fStepFlags & StepFlags::kUseNonAAInnerFill);
   *     }
   * ```
   */
  public fun useNonAAInnerFill(): Boolean {
    TODO("Implement useNonAAInnerFill")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<DepthStencilFlags> depthStencilFlags() const { return fDepthStencilFlags; }
   * ```
   */
  public fun depthStencilFlags(): Int {
    TODO("Implement depthStencilFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * Coverage coverage() const { return RenderStep::GetCoverage(fStepFlags); }
   * ```
   */
  public fun coverage(): Coverage {
    TODO("Implement coverage")
  }

  /**
   * C++ original:
   * ```cpp
   * Renderer& operator=(Renderer&&) = default
   * ```
   */
  private fun assign(param0: Renderer) {
    TODO("Implement assign")
  }

  public companion object {
    public val kMaxRenderSteps: Int = TODO("Initialize kMaxRenderSteps")
  }
}

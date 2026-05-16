package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class GraphicsPipelineDesc {
 * public:
 *     GraphicsPipelineDesc() : fRenderStepID(RenderStep::RenderStepID::kInvalid)
 *                            , fPaintID(UniquePaintParamsID::Invalid()) {}
 *     GraphicsPipelineDesc(RenderStep::RenderStepID renderStepID, UniquePaintParamsID paintID)
 *         : fRenderStepID(renderStepID)
 *         , fPaintID(paintID) {}
 *
 *     bool operator==(const GraphicsPipelineDesc& that) const {
 *         return fRenderStepID == that.fRenderStepID && fPaintID == that.fPaintID;
 *     }
 *
 *     bool operator!=(const GraphicsPipelineDesc& other) const {
 *         return !(*this == other);
 *     }
 *
 *     // Describes the geometric portion of the pipeline's program and the pipeline's fixed state
 *     // (except for renderpass-level state that will never change between draws).
 *     RenderStep::RenderStepID renderStepID() const { return fRenderStepID; }
 *     // UniqueID of the required PaintParams
 *     UniquePaintParamsID paintParamsID() const { return fPaintID; }
 *
 * #if defined(GPU_TEST_UTILS)
 *     SkString toString(const Caps*, ShaderCodeDictionary*) const;
 * #endif
 *
 * private:
 *     // Each RenderStep defines a fixed set of attributes and rasterization state, as well as the
 *     // shader fragments that control the geometry and coverage calculations. The RenderStep's shader
 *     // is combined with the rest of the shader generated from the PaintParams. Because each
 *     // RenderStep is fixed, its pointer can be used as a proxy for everything that it specifies in
 *     // the GraphicsPipeline.
 *     RenderStep::RenderStepID fRenderStepID;
 *     UniquePaintParamsID fPaintID;
 * }
 * ```
 */
public data class GraphicsPipelineDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * RenderStep::RenderStepID fRenderStepID
   * ```
   */
  private var fRenderStepID: Int,
  /**
   * C++ original:
   * ```cpp
   * UniquePaintParamsID fPaintID
   * ```
   */
  private var fPaintID: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const GraphicsPipelineDesc& that) const {
   *         return fRenderStepID == that.fRenderStepID && fPaintID == that.fPaintID;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const GraphicsPipelineDesc& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  public fun renderStepID(): Int {
    TODO("Implement renderStepID")
  }

  /**
   * C++ original:
   * ```cpp
   * RenderStep::RenderStepID renderStepID() const { return fRenderStepID; }
   * ```
   */
  public fun paintParamsID(): Int {
    TODO("Implement paintParamsID")
  }

  /**
   * C++ original:
   * ```cpp
   * UniquePaintParamsID paintParamsID() const { return fPaintID; }
   * ```
   */
  public override fun toString(caps: Caps?, dict: ShaderCodeDictionary?): Int {
    TODO("Implement toString")
  }
}

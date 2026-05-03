package org.skia.utils

import kotlin.Int
import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SkCanvasState_v1 : public SkCanvasState {
 * public:
 *     static const int32_t kVersion = 1;
 *
 *     SkCanvasState_v1(SkCanvas* canvas) : INHERITED(kVersion, canvas) {
 *         layerCount = 0;
 *         layers = nullptr;
 *         mcState.clipRectCount = 0;
 *         mcState.clipRects = nullptr;
 *         originalCanvas = canvas;
 *     }
 *
 *     ~SkCanvasState_v1() {
 *         // loop through the layers and free the data allocated to the clipRects.
 *         // See setup_MC_state, clipRects is only allocated when the clip isn't empty; and empty
 *         // is implicitly represented as clipRectCount == 0.
 *         for (int i = 0; i < layerCount; ++i) {
 *             if (layers[i].mcState.clipRectCount > 0) {
 *                 sk_free(layers[i].mcState.clipRects);
 *             }
 *         }
 *
 *         if (mcState.clipRectCount > 0) {
 *             sk_free(mcState.clipRects);
 *         }
 *
 *         // layers is always allocated, even if it's with sk_malloc(0), so this is safe.
 *         sk_free(layers);
 *     }
 *
 *     SkMCState mcState;
 *
 *     int32_t layerCount;
 *     SkCanvasLayerState* layers;
 * private:
 *     SkCanvas* originalCanvas;
 *     using INHERITED = SkCanvasState;
 * }
 * ```
 */
public open class SkCanvasStateV1 public constructor(
  canvas: SkCanvas?,
) : SkCanvasState(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * static const int32_t kVersion = 1
   * ```
   */
  public var mcState: SkMCState = TODO("Initialize mcState")

  /**
   * C++ original:
   * ```cpp
   * SkMCState mcState
   * ```
   */
  public var layerCount: Int = TODO("Initialize layerCount")

  /**
   * C++ original:
   * ```cpp
   * int32_t layerCount
   * ```
   */
  public var layers: SkCanvasLayerState? = TODO("Initialize layers")

  /**
   * C++ original:
   * ```cpp
   * SkCanvasLayerState* layers
   * ```
   */
  private var originalCanvas: SkCanvas? = TODO("Initialize originalCanvas")

  public companion object {
    public val kVersion: Int = TODO("Initialize kVersion")
  }
}

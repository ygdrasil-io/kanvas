package org.skia.utils

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SK_API SkCanvasStateUtils {
 * public:
 *     /**
 *      * Captures the current state of the canvas into an opaque ptr that is safe
 *      * to pass to a different instance of Skia (which may be the same version,
 *      * or may be newer). The function will return NULL in the event that one of the
 *      * following conditions are true.
 *      *  1) the canvas device type is not supported (currently only raster is supported)
 *      *  2) the canvas clip type is not supported (currently only non-AA clips are supported)
 *      *
 *      * It is recommended that the original canvas also not be used until all
 *      * canvases that have been created using its captured state have been dereferenced.
 *      *
 *      * Finally, it is important to note that any draw filters attached to the
 *      * canvas are NOT currently captured.
 *      *
 *      * @param canvas The canvas you wish to capture the current state of.
 *      * @return NULL or an opaque ptr that can be passed to CreateFromCanvasState
 *      *         to reconstruct the canvas. The caller is responsible for calling
 *      *         ReleaseCanvasState to free the memory associated with this state.
 *      */
 *     static SkCanvasState* CaptureCanvasState(SkCanvas* canvas);
 *
 *     /**
 *      * Create a new SkCanvas from the captured state of another SkCanvas. The
 *      * function will return NULL in the event that one of the
 *      * following conditions are true.
 *      *  1) the captured state is in an unrecognized format
 *      *  2) the captured canvas device type is not supported
 *      *
 *      * @param state Opaque object created by CaptureCanvasState.
 *      * @return NULL or an SkCanvas* whose devices and matrix/clip state are
 *      *         identical to the captured canvas. The caller is responsible for
 *      *         calling unref on the SkCanvas.
 *      */
 *     static std::unique_ptr<SkCanvas> MakeFromCanvasState(const SkCanvasState* state);
 *
 *     /**
 *      * Free the memory associated with the captured canvas state.  The state
 *      * should not be released until all SkCanvas objects created using that
 *      * state have been dereferenced. Must be called from the same library
 *      * instance that created the state via CaptureCanvasState.
 *      *
 *      * @param state The captured state you wish to dispose of.
 *      */
 *     static void ReleaseCanvasState(SkCanvasState* state);
 * }
 * ```
 */
public open class SkCanvasStateUtils {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkCanvasState* SkCanvasStateUtils::CaptureCanvasState(SkCanvas* canvas) {
     *     SkASSERT(canvas);
     *
     *     // Check the clip can be decomposed into rectangles (i.e. no soft clips).
     *     if (canvas->androidFramework_isClipAA()) {
     *         return nullptr;
     *     }
     *
     *     std::unique_ptr<SkCanvasState_v1> canvasState(new SkCanvasState_v1(canvas));
     *
     *     setup_MC_state(&canvasState->mcState, canvas->getTotalMatrix(), canvas->getDeviceClipBounds());
     *
     *     // Historically, the canvas state could report multiple top-level layers because SkCanvas
     *     // supported unclipped layers. With that feature removed, all required information is contained
     *     // by the canvas' top-most device.
     *     SkDevice* device = canvas->topDevice();
     *     SkASSERT(device);
     *
     *     SkSWriter32<sizeof(SkCanvasLayerState)> layerWriter;
     *     // we currently only work for bitmap backed devices
     *     SkPixmap pmap;
     *     if (!device->accessPixels(&pmap) || 0 == pmap.width() || 0 == pmap.height()) {
     *         return nullptr;
     *     }
     *     // and for axis-aligned devices (so not transformed for an image filter)
     *     if (!device->isPixelAlignedToGlobal()) {
     *         return nullptr;
     *     }
     *
     *     SkIPoint origin = device->getOrigin(); // safe since it's pixel aligned
     *
     *     SkCanvasLayerState* layerState =
     *             (SkCanvasLayerState*) layerWriter.reserve(sizeof(SkCanvasLayerState));
     *     layerState->type = kRaster_CanvasBackend;
     *     layerState->x = origin.x();
     *     layerState->y = origin.y();
     *     layerState->width = pmap.width();
     *     layerState->height = pmap.height();
     *
     *     switch (pmap.colorType()) {
     *         case kN32_SkColorType:
     *             layerState->raster.config = kARGB_8888_RasterConfig;
     *             break;
     *         case kRGB_565_SkColorType:
     *             layerState->raster.config = kRGB_565_RasterConfig;
     *             break;
     *         default:
     *             return nullptr;
     *     }
     *     layerState->raster.rowBytes = pmap.rowBytes();
     *     layerState->raster.pixels = pmap.writable_addr();
     *
     *     setup_MC_state(&layerState->mcState, device->localToDevice(), device->devClipBounds());
     *
     *     // allocate memory for the layers and then and copy them to the struct
     *     SkASSERT(layerWriter.bytesWritten() == sizeof(SkCanvasLayerState));
     *     canvasState->layerCount = 1;
     *     canvasState->layers = (SkCanvasLayerState*) sk_malloc_throw(layerWriter.bytesWritten());
     *     layerWriter.flatten(canvasState->layers);
     *
     *     return canvasState.release();
     * }
     * ```
     */
    public fun captureCanvasState(canvas: SkCanvas?): SkCanvasState {
      TODO("Implement captureCanvasState")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkCanvas> SkCanvasStateUtils::MakeFromCanvasState(const SkCanvasState* state) {
     *     SkASSERT(state);
     *     // Currently there is only one possible version.
     *     SkASSERT(SkCanvasState_v1::kVersion == state->version);
     *
     *     const SkCanvasState_v1* state_v1 = static_cast<const SkCanvasState_v1*>(state);
     *
     *     if (state_v1->layerCount < 1) {
     *         return nullptr;
     *     }
     *
     *     std::unique_ptr<SkCanvasStack> canvas(new SkCanvasStack(state->width, state->height));
     *
     *     // setup the matrix and clip on the n-way canvas
     *     setup_canvas_from_MC_state(state_v1->mcState, canvas.get());
     *
     *     // Iterate over the layers and add them to the n-way canvas. New clients will only send one
     *     // layer since unclipped layers are no longer supported, but old canvas clients may still
     *     // create them.
     *     for (int i = state_v1->layerCount - 1; i >= 0; --i) {
     *         std::unique_ptr<SkCanvas> canvasLayer = make_canvas_from_canvas_layer(state_v1->layers[i]);
     *         if (!canvasLayer) {
     *             return nullptr;
     *         }
     *         canvas->pushCanvas(std::move(canvasLayer), SkIPoint::Make(state_v1->layers[i].x,
     *                                                                   state_v1->layers[i].y));
     *     }
     *
     *     return canvas;
     * }
     * ```
     */
    public fun makeFromCanvasState(state: SkCanvasState?): SkCanvas? {
      TODO("Implement makeFromCanvasState")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkCanvasStateUtils::ReleaseCanvasState(SkCanvasState* state) {
     *     SkASSERT(!state || SkCanvasState_v1::kVersion == state->version);
     *     // Upcast to the correct version of SkCanvasState. This avoids having a virtual destructor on
     *     // SkCanvasState. That would be strange since SkCanvasState has no other virtual functions, and
     *     // instead uses the field "version" to determine how to behave.
     *     delete static_cast<SkCanvasState_v1*>(state);
     * }
     * ```
     */
    public fun releaseCanvasState(state: SkCanvasState?) {
      TODO("Implement releaseCanvasState")
    }
  }
}

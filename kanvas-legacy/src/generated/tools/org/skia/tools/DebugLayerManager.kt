package org.skia.tools

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkSurface
import org.skia.core.THashMap
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DebugLayerManager {
 * public:
 *     DebugLayerManager() {}
 *
 *     // Store an SkPicture under a given nodeId (and under the currently set frame number)
 *     // `dirty` is the recorded rect that was used to call androidFramework_setDeviceClipRestriction
 *     // when the layer was drawn.
 *     void storeSkPicture(int nodeId, int frame, const sk_sp<SkPicture>& picture, SkIRect dirty);
 *
 *     // Set's the command playback head for a given picture/draw event.
 *     void setCommand(int nodeId, int frame, int command);
 *
 *     void drawLayerEventTo(SkSurface*, const int nodeId, const int frame);
 *
 *     // getLayerAsImage draws the given layer as it would have looked on frame and returns an image.
 *     // Though each picture can be played back in as many ways as there are commands, we will let
 *     // that be determined by the user who sets an independent playhead for each draw event, tracked
 *     // here, so it stays how they left it.
 *     // For example: Say we are drawing a layer at frame 10.
 *     // Frame 0:  Layer was completely redrawn. By default we draw it to its last command. We always
 *     //           save the result by (nodeId, frame)
 *     // Frame 5:  Layer was partially redrawn, and the user has inspected this draw event, leaving
 *     //           its command playhead at command 50/100. We have drew this at the time and save how
 *     //           the result looked (all of the commands at frame 0, then half of the commands in the
 *     //           partial draw at frame 5)
 *     // Frame 10: Another partial redraw, un-altered, drawn on top of the result from frame 5. We
 *     //           return this as the image of how the layer should look on frame 10
 *     // Frame 15: A full redraw
 *     //
 *     // If the user then comes along and moves the command playhead of the picture at frame 0,
 *     // we invalidate the stored images for 0, 5, and 10, but we can leave 15 alone if we have it.
 *     //
 *     // Which leaves us with one less degree of freedom to think about when implementing this
 *     // function: We can assume there is only one way to play back a given picture. :)
 *     //
 *     // The reason the public version of this function doesn't let you specify the frame, is that
 *     // I expect DebugCanvas to call it, which doesn't know which frame it's rendering. The code in
 *     // debugger_bindings.cpp does know, which it why I'm having it set the frame via setFrame(int)
 *     sk_sp<SkImage> getLayerAsImage(const int nodeId, const int frame);
 *
 *     // Flat because it's meant to be bindable by emscripten and returned to the javascript side
 *     struct DrawEventSummary {
 *         // true when the drawEvent represents a valid result.
 *         bool found = false;
 *         int commandCount;
 *         int layerWidth;
 *         int layerHeight;
 *     };
 *     // return the summary of a single event
 *     DrawEventSummary event(int nodeId, int frame) const;
 *
 *     struct LayerSummary {
 *         int nodeId;
 *         // Last frame less than or equal to the given frame which has an update for this layer
 *         // -1 if the layer has no updates satisfying that constraint.
 *         int frameOfLastUpdate;
 *         // Whether the last update was a full redraw.
 *         bool fullRedraw;
 *         int layerWidth;
 *         int layerHeight;
 *     };
 *     // Return a list summarizing all layers, with info relevant to the current frame.
 *     std::vector<LayerSummary> summarizeLayers(int frame) const;
 *
 *     // Return the list of node ids which have DrawEvents on the given frame
 *     std::vector<int> listNodesForFrame(int frame) const;
 *     // Return the list of frames on which the given node had DrawEvents.
 *     std::vector<int> listFramesForNode(int nodeId) const;
 *
 *     // asks the DebugCanvas of the indicated draw event to serialize it's commands as JSON.
 *     void toJSON(SkJSONWriter&, UrlDataManager&, SkCanvas*, int nodeId, int frame);
 *
 *     // return a pointer to the debugcanvas of a given draw event.
 *     DebugCanvas* getEventDebugCanvas(int nodeid, int frame);
 *
 *     // forwards the provided setting to all debugcanvases.
 *     void setOverdrawViz(bool overdrawViz);
 *     void setClipVizColor(SkColor clipVizColor);
 *     void setDrawGpuOpBounds(bool drawGpuOpBounds);
 *
 *     struct LayerKey{
 *         int frame; // frame of animation on which this event was recorded.
 *         int nodeId; // the render node id of the layer which was drawn to.
 *
 *         bool operator==(const LayerKey& b) const {
 *             return this->frame==b.frame && this->nodeId==b.nodeId;
 *         }
 *     };
 *
 *     // return list of keys that identify layer update events
 *     const std::vector<DebugLayerManager::LayerKey>& getKeys() const { return keys; }
 *
 * private:
 *     // This class is basically a map from (frame, node) to draw-event
 *     // during recording, at the beginning of any frame, one or more layers could have been drawn on.
 *     // every draw event was recorded, and when reading the mskp file they are stored and organized
 *     // here.
 *
 *     struct DrawEvent {
 *         // true the pic's clip equals the layer bounds.
 *         bool fullRedraw;
 *         // the saved result of how the layer looks on this frame.
 *         // null if we don't have it.
 *         sk_sp<SkImage> image;
 *         // A debug canvas used for drawing this picture.
 *         // the SkPicture itself isn't saved, since it's in the DebugCanvas.
 *         std::unique_ptr<DebugCanvas> debugCanvas;
 *         // the command index where the debugCanvas was left off.
 *         int command;
 *         // the size of the layer this drew into. redundant between multiple DrawEvents on the same
 *         // layer but helpful.
 *         SkISize layerBounds;
 *     };
 *
 *     skia_private::THashMap<LayerKey, DrawEvent> fDraws;
 *     // The list of all keys in the map above (it has no keys() method)
 *     std::vector<LayerKey> keys;
 * }
 * ```
 */
public data class DebugLayerManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<LayerKey, DrawEvent> fDraws
   * ```
   */
  private var fDraws: THashMap<undefined.LayerKey, DrawEvent>,
  /**
   * C++ original:
   * ```cpp
   * std::vector<LayerKey> keys
   * ```
   */
  private var keys: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void DebugLayerManager::storeSkPicture(int nodeId,
   *                                        int frame,
   *                                        const sk_sp<SkPicture>& picture,
   *                                        SkIRect dirty) {
   *     const LayerKey k = {frame, nodeId};
   *
   *     // Make debug canvas using bounds from SkPicture. This will be equal to whatever width and
   *     // height were passed into SkPictureRecorder::beginRecording(w, h) which is the layer bounds.
   *     const auto& layerBounds = picture->cullRect().roundOut();
   *     auto debugCanvas = std::make_unique<DebugCanvas>(layerBounds);
   *     // Must be set or they end up undefined due to cosmic rays, bad luck, etc.
   *     debugCanvas->setOverdrawViz(false);
   *     debugCanvas->setDrawGpuOpBounds(false);
   *     debugCanvas->setClipVizColor(SK_ColorTRANSPARENT);
   *     // Setting this allows a layer to contain another layer. TODO(nifong): write a test for this.
   *     debugCanvas->setLayerManagerAndFrame(this, frame);
   *     // Only draw picture to the debug canvas once.
   *     debugCanvas->drawPicture(picture);
   *     int numCommands = debugCanvas->getSize();
   *
   *     DrawEvent event = {
   *             frame == 0 || dirty == layerBounds,           // fullRedraw
   *             nullptr,                                      // image
   *             std::move(debugCanvas),                       // debugCanvas
   *             numCommands - 1,                              // command
   *             {layerBounds.width(), layerBounds.height()},  // layerBounds
   *     };
   *
   *     fDraws.set(k, std::move(event));
   *     keys.push_back(k);
   * }
   * ```
   */
  public fun storeSkPicture(
    nodeId: Int,
    frame: Int,
    picture: SkSp<SkPicture>,
    dirty: SkIRect,
  ) {
    TODO("Implement storeSkPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugLayerManager::setCommand(int nodeId, int frame, int command) {
   *     auto* drawEvent = fDraws.find({frame, nodeId});
   *     if (!drawEvent) {
   *         SkDebugf(
   *                 "Could not set command playhead for event {%d, %d}, it is not tracked by"
   *                 "DebugLayerManager.\n",
   *                 frame,
   *                 nodeId);
   *         return;
   *     }
   *     const int count = drawEvent->debugCanvas->getSize();
   *     drawEvent->command = command < count ? command : count - 1;
   *     // Invalidate stored images that depended on this combination of node and frame.
   *     // actually this does all of the events for this nodeId, but close enough.
   *     auto relevantFrames = listFramesForNode(nodeId);
   *     for (const auto& f : relevantFrames) {
   *         fDraws[{f, nodeId}].image = nullptr;
   *     }
   * }
   * ```
   */
  public fun setCommand(
    nodeId: Int,
    frame: Int,
    command: Int,
  ) {
    TODO("Implement setCommand")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugLayerManager::drawLayerEventTo(SkSurface* surface, const int nodeId, const int frame) {
   *     auto& evt = fDraws[{frame, nodeId}];
   *     evt.debugCanvas->drawTo(surface->getCanvas(), evt.command);
   * }
   * ```
   */
  public fun drawLayerEventTo(
    surface: SkSurface?,
    nodeId: Int,
    frame: Int,
  ) {
    TODO("Implement drawLayerEventTo")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> DebugLayerManager::getLayerAsImage(const int nodeId, const int frame) {
   *   // What is the last frame having an SkPicture for this layer? call it frame N
   *   // have cached image of it? if so, return it.
   *   // if not, draw it at frame N by the following method:
   *   // The picture at frame N could have been a full redraw, or it could have been clipped to a
   *   // dirty region. In order to know what the layer looked like on this frame, we must draw every
   *   // picture starting with the last full redraw, up to the last one before the current frame, since
   *   // any of those previous draws could be showing through.
   *
   *   // list of frames this node was updated on.
   *   auto relevantFrames = listFramesForNode(nodeId);
   *   // find largest one not greater than `frame`.
   *   uint32_t i = relevantFrames.size()-1;
   *   while (relevantFrames[i] > frame) { i--; }
   *   const int frameN = relevantFrames[i];
   *   // Fetch the draw event
   *   auto& drawEvent = fDraws[{frameN, nodeId}];
   *   // if an image of this is cached, return it.
   *   if (drawEvent.image) {
   *     return drawEvent.image;
   *   }
   *   // when it's not cached, we'll have to render it in an offscreen surface.
   *   // start at the last full redraw. (pick up counting backwards from above)
   *   while (i>0 && !(fDraws[{relevantFrames[i], nodeId}].fullRedraw)) { i--; }
   *   // The correct layer bounds can be obtained from any drawEvent on this layer.
   *   // the color type and alpha type are chosen here to match wasm-skp-debugger/cpu.js which was
   *   // chosen to match the capabilities of HTML canvas, which this ultimately has to be drawn into.
   *   // TODO(nifong): introduce a method of letting the user choose the backend for this.
   *   auto surface = SkSurfaces::Raster(SkImageInfo::Make(
   *           drawEvent.layerBounds, kRGBA_8888_SkColorType, kUnpremul_SkAlphaType, nullptr));
   *   // draw everything from the last full redraw up to the current frame.
   *   // other frames drawn are partial, meaning they were clipped to not completely cover the layer.
   *   // count back up with i
   *   for (; i<relevantFrames.size() && relevantFrames[i]<=frameN; i++) {
   *     drawLayerEventTo(surface.get(), nodeId, relevantFrames[i]);
   *   }
   *   drawEvent.image = surface->makeImageSnapshot();
   *   return drawEvent.image;
   * }
   * ```
   */
  public fun getLayerAsImage(nodeId: Int, frame: Int): SkSp<SkImage> {
    TODO("Implement getLayerAsImage")
  }

  /**
   * C++ original:
   * ```cpp
   * DebugLayerManager::DrawEventSummary DebugLayerManager::event(int nodeId, int frame) const {
   *   auto* evt = fDraws.find({frame, nodeId});
   *   if (!evt) { return {}; }
   *   return {
   *     true, evt->debugCanvas->getSize(),
   *     evt->layerBounds.width(), evt->layerBounds.height()
   *   };
   * }
   * ```
   */
  public fun event(nodeId: Int, frame: Int): DrawEventSummary {
    TODO("Implement event")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<DebugLayerManager::LayerSummary> DebugLayerManager::summarizeLayers(int frame) const {
   *   // Find the last update on or before `frame` for every node
   *   // key: nodeId, one entry for every layer
   *   // value: summary of the layer.
   *   std::unordered_map<int, LayerSummary> summaryMap;
   *   for (const auto& key : keys) {
   *     auto* evt = fDraws.find(key);
   *     if (!evt) { continue; }
   *     // -1 as a default value for the last update serves as a way of indicating that this layer
   *     // is present in the animation, but doesn't have an update less than or equal to `frame`
   *     int lastUpdate = (key.frame <= frame ? key.frame : -1);
   *
   *     // do we have an entry for this layer yet? is it later than the one we're looking at?
   *     auto found = summaryMap.find(key.nodeId);
   *     if (found != summaryMap.end()) {
   *       LayerSummary& item = summaryMap[key.nodeId];
   *       if (lastUpdate > item.frameOfLastUpdate) {
   *         item.frameOfLastUpdate = key.frame;
   *         item.fullRedraw = evt->fullRedraw;
   *       }
   *     } else {
   *       // record first entry for this layer
   *       summaryMap.insert({key.nodeId, {
   *         key.nodeId, lastUpdate, evt->fullRedraw,
   *         evt->layerBounds.width(), evt->layerBounds.height()
   *       }});
   *     }
   *   }
   *   std::vector<LayerSummary> result;
   *   for (auto it = summaryMap.begin(); it != summaryMap.end(); ++it) {
   *     result.push_back(it->second);
   *   }
   *   return result;
   * }
   * ```
   */
  public fun summarizeLayers(frame: Int): Int {
    TODO("Implement summarizeLayers")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<int> DebugLayerManager::listNodesForFrame(int frame) const {
   *   std::vector<int> result;
   *   for (const auto& key : keys) {
   *     if (key.frame == frame) {
   *       result.push_back(key.nodeId);
   *     }
   *   }
   *   return result;
   * }
   * ```
   */
  public fun listNodesForFrame(frame: Int): Int {
    TODO("Implement listNodesForFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<int> DebugLayerManager::listFramesForNode(int nodeId) const {
   *   std::vector<int> result;
   *   for (const auto& key : keys) {
   *     if (key.nodeId == nodeId) {
   *       result.push_back(key.frame);
   *     }
   *   }
   *   return result;
   * }
   * ```
   */
  public fun listFramesForNode(nodeId: Int): Int {
    TODO("Implement listFramesForNode")
  }

  /**
   * C++ original:
   * ```cpp
   * void toJSON(SkJSONWriter&, UrlDataManager&, SkCanvas*, int nodeId, int frame)
   * ```
   */
  public fun toJSON(
    param0: SkJSONWriter,
    param1: UrlDataManager,
    param2: SkCanvas?,
    nodeId: Int,
    frame: Int,
  ) {
    TODO("Implement toJSON")
  }

  /**
   * C++ original:
   * ```cpp
   * DebugCanvas* DebugLayerManager::getEventDebugCanvas(int nodeId, int frame) {
   *   auto& evt = fDraws[{frame, nodeId}];
   *   return evt.debugCanvas.get();
   * }
   * ```
   */
  public fun getEventDebugCanvas(nodeid: Int, frame: Int): DebugCanvas {
    TODO("Implement getEventDebugCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugLayerManager::setOverdrawViz(bool overdrawViz) {
   *   for (const auto& key : keys) {
   *     auto& evt = fDraws[key];
   *     evt.debugCanvas->setOverdrawViz(overdrawViz);
   *   }
   * }
   * ```
   */
  public fun setOverdrawViz(overdrawViz: Boolean) {
    TODO("Implement setOverdrawViz")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugLayerManager::setClipVizColor(SkColor clipVizColor) {
   *   for (const auto& key : keys) {
   *     auto& evt = fDraws[key];
   *     evt.debugCanvas->setClipVizColor(clipVizColor);
   *   }
   * }
   * ```
   */
  public fun setClipVizColor(clipVizColor: SkColor) {
    TODO("Implement setClipVizColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugLayerManager::setDrawGpuOpBounds(bool drawGpuOpBounds) {
   *   for (const auto& key : keys) {
   *     auto& evt = fDraws[key];
   *     evt.debugCanvas->setDrawGpuOpBounds(drawGpuOpBounds);
   *   }
   * }
   * ```
   */
  public fun setDrawGpuOpBounds(drawGpuOpBounds: Boolean) {
    TODO("Implement setDrawGpuOpBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<DebugLayerManager::LayerKey>& getKeys() const { return keys; }
   * ```
   */
  public fun getKeys(): Int {
    TODO("Implement getKeys")
  }

  public data class DrawEventSummary public constructor(
    public var found: Boolean,
    public var commandCount: Int,
    public var layerWidth: Int,
    public var layerHeight: Int,
  )

  public data class LayerSummary public constructor(
    public var nodeId: Int,
    public var frameOfLastUpdate: Int,
    public var fullRedraw: Boolean,
    public var layerWidth: Int,
    public var layerHeight: Int,
  )

  public data class LayerKey public constructor(
    public var frame: Int,
    public var nodeId: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public data class DrawEvent public constructor(
    public var fullRedraw: Boolean,
    public var image: SkSp<SkImage>,
    public var debugCanvas: Int,
    public var command: Int,
    public var layerBounds: SkISize,
  )
}

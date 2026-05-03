package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkStreamSeekable

/**
 * C++ original:
 * ```cpp
 * class MSKPPlayer {
 * public:
 *     ~MSKPPlayer();
 *
 *     /** Make a player from a MSKP stream, or null if stream can't be read as MSKP. */
 *     static std::unique_ptr<MSKPPlayer> Make(SkStreamSeekable* stream);
 *
 *     /** Maximum width and height across all frames. */
 *     SkISize maxDimensions() const { return fMaxDimensions; }
 *
 *     /** Total number of frames. */
 *     int numFrames() const { return static_cast<int>(fRootLayers.size()); }
 *
 *     /** Size of an individual frame. */
 *     SkISize frameDimensions(int i) const;
 *
 *     /**
 *      * Plays a frame into the passed canvas. Frames can be randomly accessed. Offscreen layers are
 *      * incrementally updated from their current state to the state required for the frame
 *      * (redrawing from scratch if their current state is ahead of the passed frame index).
 *      */
 *     bool playFrame(SkCanvas* canvas, int i);
 *
 *     /** Destroys any cached offscreen layers. */
 *     void resetLayers();
 *
 *     /**
 *      * Forces all offscreen layers to re-render the next time they're required for a frame but
 *      * preserves the backing stores for them if already allocated.
 *      */
 *     void rewindLayers();
 *
 *     /**
 *      * Creates backing stores for any offscreen layers using the passed canvas's makeSurface().
 *      * Existing layers that match the canvas's recording context are not reallocated or rewound.
 *      */
 *     void allocateLayers(SkCanvas*);
 *
 *     /**
 *      * A set of IDs of offscreen layers in no particular order. If frame value >= 0 is specified
 *      * then the layer set is filtered to layers used by that frame (or empty if >= numFrames). If
 *      * < 0 then gathers all the layers across all frames.
 *      */
 *     std::vector<int> layerIDs(int frame = -1) const;
 *
 *     /**
 *      * Gets the contents of an offscreen layer. It's contents will depend on current playback state
 *      * (playFrame(), updateFrameLayers(), resetLayers()). If the layer currently has no backing
 *      * store because it hasn't been drawn or resetLayers() was called then this will return nullptr.
 *      * Layer contents are not affected by rewindLayers() as that simply lazily redraws the frame
 *      * contents the next time it is required by playFrame*() or updateFrameLayers().
 *      */
 *     sk_sp<SkImage> layerSnapshot(int layerID) const;
 *
 * private:
 *     MSKPPlayer() = default;
 *     // noncopyable, nonmoveable.
 *     MSKPPlayer(const MSKPPlayer&) = delete;
 *     MSKPPlayer(MSKPPlayer&&) = delete;
 *     MSKPPlayer& operator=(const MSKPPlayer&) = delete;
 *     MSKPPlayer& operator=(MSKPPlayer&&) = delete;
 *
 *     // Cmds are used to draw content to the frame root layer and to offscreen layers.
 *     struct Cmd;
 *     // Draws a SkPicture.
 *     struct PicCmd;
 *     // Draws another layer. Stores the ID of the layer to draw and what command index on that
 *     // layer should be current when the layer is drawn. The layer contents are updated to the
 *     // stored command index before the layer is drawn.
 *     struct DrawLayerCmd;
 *
 *     // The commands for a root/offscreen layer and dimensions of the layer.
 *     struct LayerCmds {
 *         LayerCmds() = default;
 *         LayerCmds(LayerCmds&&) = default;
 *         SkISize fDimensions;
 *         std::vector<std::unique_ptr<Cmd>> fCmds;
 *     };
 *
 *     // Playback state of layer: the last command index drawn to it and the SkSurface with contents.
 *     struct LayerState {
 *         size_t fCurrCmd = -1;
 *         sk_sp<SkSurface> fSurface;
 *     };
 *
 *     static sk_sp<SkSurface> MakeSurfaceForLayer(const LayerCmds&, SkCanvas* rootCanvas);
 *
 *     void collectReferencedLayers(const LayerCmds& layer, std::vector<int>*) const;
 *
 *     // MSKP layer ID -> LayerCmds
 *     using LayerMap = std::unordered_map<int, LayerCmds>;
 *     // MSKP layer ID -> LayerState
 *     using LayerStateMap = std::unordered_map<int, LayerState>;
 *
 *     /**
 *      * A SkCanvas that consumes the SkPicture and records Cmds into a Layer. It will spawn
 *      * additional Layers and record nested SkPictures into those using additional CmdRecordCanvas
 *      * CmdRecordCanvas instances. It needs access to fOffscreenLayers to create and update LayerCmds
 *      * structs for offscreen layers.
 *      */
 *     class CmdRecordCanvas;
 *
 *     SkISize            fMaxDimensions = {0, 0};  // Max dimensions across all frames.
 *     LayerMap           fOffscreenLayers;         // All the offscreen layers for all frames.
 *     LayerStateMap      fOffscreenLayerStates;    // Current surfaces and command idx for offscreen
 *                                                  // layers
 *     std::vector<LayerCmds> fRootLayers;          // One root layer for each frame.
 * }
 * ```
 */
public data class MSKPPlayer public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkISize            fMaxDimensions
   * ```
   */
  private var fMaxDimensions: Int,
  /**
   * C++ original:
   * ```cpp
   * LayerMap           fOffscreenLayers
   * ```
   */
  private var fOffscreenLayers: Int,
  /**
   * C++ original:
   * ```cpp
   * LayerStateMap      fOffscreenLayerStates
   * ```
   */
  private var fOffscreenLayerStates: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<LayerCmds> fRootLayers
   * ```
   */
  private var fRootLayers: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkISize maxDimensions() const { return fMaxDimensions; }
   * ```
   */
  public fun maxDimensions(): Int {
    TODO("Implement maxDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * int numFrames() const { return static_cast<int>(fRootLayers.size()); }
   * ```
   */
  public fun numFrames(): Int {
    TODO("Implement numFrames")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize MSKPPlayer::frameDimensions(int i) const {
   *     if (i < 0 || i >= this->numFrames()) {
   *         return {-1, -1};
   *     }
   *     return fRootLayers[i].fDimensions;
   * }
   * ```
   */
  public fun frameDimensions(i: Int): Int {
    TODO("Implement frameDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MSKPPlayer::playFrame(SkCanvas* canvas, int i) {
   *     if (i < 0 || i >= this->numFrames()) {
   *         return false;
   *     }
   *
   *     // Find the first offscreen layer that has a valid surface. If it's recording context
   *     // differs from the passed canvas's then reset all the layers. Playback will
   *     // automatically allocate new surfaces for offscreen layers as they're encountered.
   *     for (const auto& ols : fOffscreenLayerStates) {
   *         const LayerState& state = ols.second;
   *         if (state.fSurface) {
   *             if (state.fSurface->recordingContext() != canvas->recordingContext()) {
   *                 this->resetLayers();
   *             }
   *             break;
   *         }
   *     }
   *
   *     // Replay all the commands for this frame to the caller's canvas.
   *     const LayerCmds& layer = fRootLayers[i];
   *     for (const auto& cmd : layer.fCmds) {
   *         cmd->draw(canvas, fOffscreenLayers, &fOffscreenLayerStates);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun playFrame(canvas: SkCanvas?, i: Int): Boolean {
    TODO("Implement playFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * void MSKPPlayer::resetLayers() { fOffscreenLayerStates.clear(); }
   * ```
   */
  public fun resetLayers() {
    TODO("Implement resetLayers")
  }

  /**
   * C++ original:
   * ```cpp
   * void MSKPPlayer::rewindLayers() {
   *     for (auto& [id, state] : fOffscreenLayerStates) {
   *         state.fCurrCmd = -1;
   *     }
   * }
   * ```
   */
  public fun rewindLayers() {
    TODO("Implement rewindLayers")
  }

  /**
   * C++ original:
   * ```cpp
   * void MSKPPlayer::allocateLayers(SkCanvas* canvas) {
   *     // Iterate over layers not states as states are lazily created in playback but here we want to
   *     // create any that don't already exist.
   *     for (auto& [id, layer] : fOffscreenLayers) {
   *         LayerState& state = fOffscreenLayerStates[id];
   *         if (!state.fSurface || state.fSurface->recordingContext() != canvas->recordingContext()) {
   *             state.fCurrCmd = -1;
   *             state.fSurface = MakeSurfaceForLayer(fOffscreenLayers[id], canvas);
   *         }
   *     }
   * }
   * ```
   */
  public fun allocateLayers(canvas: SkCanvas?) {
    TODO("Implement allocateLayers")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<int> MSKPPlayer::layerIDs(int frame) const {
   *     std::vector<int> result;
   *     if (frame < 0) {
   *         result.reserve(fOffscreenLayers.size());
   *         for (auto& [id, _] : fOffscreenLayers) {
   *             result.push_back(id);
   *         }
   *         return result;
   *     }
   *     if (frame < static_cast<int>(fRootLayers.size())) {
   *         this->collectReferencedLayers(fRootLayers[frame], &result);
   *     }
   *     return result;
   * }
   * ```
   */
  public fun layerIDs(frame: Int = TODO()): Int {
    TODO("Implement layerIDs")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> MSKPPlayer::layerSnapshot(int layerID) const {
   *     auto iter = fOffscreenLayerStates.find(layerID);
   *     if (iter == fOffscreenLayerStates.end() || !iter->second.fSurface) {
   *         return nullptr;
   *     }
   *     return iter->second.fSurface->makeImageSnapshot();
   * }
   * ```
   */
  public fun layerSnapshot(layerID: Int): Int {
    TODO("Implement layerSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * MSKPPlayer& operator=(const MSKPPlayer&) = delete
   * ```
   */
  private fun assign(param0: MSKPPlayer) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * MSKPPlayer& operator=(MSKPPlayer&&) = delete
   * ```
   */
  private fun collectReferencedLayers(layer: LayerCmds, `out`: List<Int>?) {
    TODO("Implement collectReferencedLayers")
  }

  public data class LayerCmds public constructor(
    public var fDimensions: Int,
  )

  public data class LayerState public constructor(
    public var fCurrCmd: Int,
    public var fSurface: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<MSKPPlayer> MSKPPlayer::Make(SkStreamSeekable* stream) {
     *     auto deserialContext = std::make_unique<SkSharingDeserialContext>();
     *     SkDeserialProcs procs;
     *     procs.fImageProc = SkSharingDeserialContext::deserializeImage;
     *     procs.fImageCtx = deserialContext.get();
     *
     *     int pageCount = SkMultiPictureDocument::ReadPageCount(stream);
     *     if (!pageCount) {
     *         return nullptr;
     *     }
     *     std::vector<SkDocumentPage> pages(pageCount);
     *     if (!SkMultiPictureDocument::Read(stream, pages.data(), pageCount, &procs)) {
     *         return nullptr;
     *     }
     *     std::unique_ptr<MSKPPlayer> result(new MSKPPlayer);
     *     result->fRootLayers.reserve(pages.size());
     *     for (const auto& page : pages) {
     *         SkISize dims = {SkScalarCeilToInt(page.fSize.width()),
     *                         SkScalarCeilToInt(page.fSize.height())};
     *         result->fRootLayers.emplace_back();
     *         result->fRootLayers.back().fDimensions = dims;
     *         result->fMaxDimensions.fWidth  = std::max(dims.width() , result->fMaxDimensions.width() );
     *         result->fMaxDimensions.fHeight = std::max(dims.height(), result->fMaxDimensions.height());
     *         CmdRecordCanvas sc(&result->fRootLayers.back(), &result->fOffscreenLayers);
     *         page.fPicture->playback(&sc);
     *     }
     *     return result;
     * }
     * ```
     */
    public fun make(stream: SkStreamSeekable?): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkSurface> MSKPPlayer::MakeSurfaceForLayer(const LayerCmds& layer, SkCanvas* rootCanvas) {
     *     // Assume layer has same surface props and info as this (mskp doesn't currently record this
     *     // data).
     *     SkSurfaceProps props;
     *     rootCanvas->getProps(&props);
     *     return rootCanvas->makeSurface(rootCanvas->imageInfo().makeDimensions(layer.fDimensions),
     *                                    &props);
     * }
     * ```
     */
    private fun makeSurfaceForLayer(layer: LayerCmds, rootCanvas: SkCanvas?): Int {
      TODO("Implement makeSurfaceForLayer")
    }
  }
}

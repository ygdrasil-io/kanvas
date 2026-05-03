package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkPicture
import org.skia.core.SkTaskGroup
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrContextThreadSafeProxy
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.math.SkIRect
import org.skia.utils.GrSurfaceCharacterization

/**
 * C++ original:
 * ```cpp
 * class DDLTileHelper {
 * public:
 *     // The TileData class encapsulates the information and behavior of a single tile when
 *     // rendering with DDLs.
 *     class TileData {
 *     public:
 *         TileData();
 *         ~TileData();
 *
 *         bool initialized() const { return fID != -1; }
 *
 *         void init(int id,
 *                   GrDirectContext*,
 *                   const GrSurfaceCharacterization& dstChar,
 *                   const SkIRect& clip,
 *                   const SkIRect& paddingOutsets);
 *
 *         // Create the DDL for this tile (i.e., fill in 'fDisplayList').
 *         void createDDL(const SkPicture*);
 *
 *         void dropDDL() { fDisplayList.reset(); }
 *
 *         // Precompile all the programs required to draw this tile's DDL
 *         void precompile(GrDirectContext*);
 *
 *         // Just draw the re-inflated per-tile SKP directly into this tile w/o going through a DDL
 *         // first. This is used for determining the overhead of using DDLs (i.e., it replaces
 *         // a 'createDDL' and 'draw' pair.
 *         void drawSKPDirectly(GrDirectContext*, const SkPicture*);
 *
 *         // Replay the recorded DDL into the tile surface - filling in 'fBackendTexture'.
 *         void draw(GrDirectContext*);
 *
 *         void reset();
 *
 *         int id() const { return fID; }
 *         SkIRect clipRect() const { return fClip; }
 *         SkISize paddedRectSize() const {
 *             return { fClip.width() + fPaddingOutsets.fLeft + fPaddingOutsets.fRight,
 *                      fClip.height() + fPaddingOutsets.fTop + fPaddingOutsets.fBottom };
 *         }
 *         SkIVector padOffset() const { return { fPaddingOutsets.fLeft, fPaddingOutsets.fTop }; }
 *
 *         GrDeferredDisplayList* ddl() { return fDisplayList.get(); }
 *
 *         sk_sp<SkImage> makePromiseImageForDst(sk_sp<GrContextThreadSafeProxy>);
 *         void dropCallbackContext() { fCallbackContext.reset(); }
 *
 *         static void CreateBackendTexture(GrDirectContext*, TileData*);
 *         static void DeleteBackendTexture(GrDirectContext*, TileData*);
 *
 *     private:
 *         sk_sp<SkSurface> makeWrappedTileDest(GrRecordingContext* context);
 *
 *         sk_sp<PromiseImageCallbackContext> refCallbackContext() { return fCallbackContext; }
 *
 *         int                       fID = -1;
 *         SkIRect                   fClip;             // in the device space of the final SkSurface
 *         SkIRect                   fPaddingOutsets;   // random padding for the output surface
 *         GrSurfaceCharacterization fPlaybackChar;     // characterization for the tile's dst surface
 *
 *         // The callback context holds (via its GrPromiseImageTexture) the backend texture
 *         // that is both wrapped in 'fTileSurface' and backs this tile's promise image
 *         // (i.e., the one returned by 'makePromiseImage').
 *         sk_sp<PromiseImageCallbackContext> fCallbackContext;
 *         // 'fTileSurface' wraps the backend texture in 'fCallbackContext' and must exist until
 *         // after 'fDisplayList' has been flushed (bc it owns the proxy the DDL's destination
 *         // trampoline points at).
 *         // TODO: fix the ref-order so we don't need 'fTileSurface' here
 *         sk_sp<SkSurface>              fTileSurface;
 *
 *         sk_sp<GrDeferredDisplayList>  fDisplayList;
 *     };
 *
 *     DDLTileHelper(GrDirectContext*,
 *                   const GrSurfaceCharacterization& dstChar,
 *                   const SkIRect& viewport,
 *                   int numXDivisions, int numYDivisions,
 *                   bool addRandomPaddingToDst);
 *
 *     void kickOffThreadedWork(SkTaskGroup* recordingTaskGroup,
 *                              SkTaskGroup* gpuTaskGroup,
 *                              GrDirectContext*,
 *                              SkPicture*);
 *
 *     void createDDLsInParallel(SkPicture*);
 *
 *     // Create the DDL that will compose all the tile images into a final result.
 *     void createComposeDDL();
 *     const sk_sp<GrDeferredDisplayList>& composeDDL() const { return fComposeDDL; }
 *
 *     // For each tile, create its DDL and then draw it - all on a single thread. This is to allow
 *     // comparison w/ just drawing the SKP directly (i.e., drawAllTilesDirectly). The
 *     // DDL creations and draws are interleaved to prevent starvation of the GPU.
 *     // Note: this is somewhat of a misuse/pessimistic-use of DDLs since they are supposed to
 *     // be created on a separate thread.
 *     void interleaveDDLCreationAndDraw(GrDirectContext*, SkPicture*);
 *
 *     // This draws all the per-tile SKPs directly into all of the tiles w/o converting them to
 *     // DDLs first - all on a single thread.
 *     void drawAllTilesDirectly(GrDirectContext*, SkPicture*);
 *
 *     void dropCallbackContexts();
 *     void resetAllTiles();
 *
 *     int numTiles() const { return fNumXDivisions * fNumYDivisions; }
 *
 *     void createBackendTextures(SkTaskGroup*, GrDirectContext*);
 *     void deleteBackendTextures(SkTaskGroup*, GrDirectContext*);
 *
 * private:
 *     int                                    fNumXDivisions; // number of tiles horizontally
 *     int                                    fNumYDivisions; // number of tiles vertically
 *     skia_private::AutoTArray<TileData>   fTiles;        // 'fNumXDivisions' x
 *     // 'fNumYDivisions'
 *
 *     sk_sp<GrDeferredDisplayList>           fComposeDDL;
 *
 *     const GrSurfaceCharacterization        fDstCharacterization;
 * }
 * ```
 */
public data class DDLTileHelper public constructor(
  /**
   * C++ original:
   * ```cpp
   * int                                    fNumXDivisions
   * ```
   */
  private var fNumXDivisions: Int,
  /**
   * C++ original:
   * ```cpp
   * int                                    fNumYDivisions
   * ```
   */
  private var fNumYDivisions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTArray<TileData>   fTiles
   * ```
   */
  private var fTiles: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrDeferredDisplayList>           fComposeDDL
   * ```
   */
  private var fComposeDDL: Int,
  /**
   * C++ original:
   * ```cpp
   * const GrSurfaceCharacterization        fDstCharacterization
   * ```
   */
  private val fDstCharacterization: GrSurfaceCharacterization,
) {
  /**
   * C++ original:
   * ```cpp
   * void kickOffThreadedWork(SkTaskGroup* recordingTaskGroup,
   *                              SkTaskGroup* gpuTaskGroup,
   *                              GrDirectContext*,
   *                              SkPicture*)
   * ```
   */
  private fun kickOffThreadedWork(
    recordingTaskGroup: SkTaskGroup?,
    gpuTaskGroup: SkTaskGroup?,
    param2: GrDirectContext?,
    param3: SkPicture?,
  ) {
    TODO("Implement kickOffThreadedWork")
  }

  /**
   * C++ original:
   * ```cpp
   * void createDDLsInParallel(SkPicture*)
   * ```
   */
  private fun createDDLsInParallel(param0: SkPicture?) {
    TODO("Implement createDDLsInParallel")
  }

  /**
   * C++ original:
   * ```cpp
   * void createComposeDDL()
   * ```
   */
  private fun createComposeDDL() {
    TODO("Implement createComposeDDL")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<GrDeferredDisplayList>& composeDDL() const { return fComposeDDL; }
   * ```
   */
  private fun composeDDL(): Int {
    TODO("Implement composeDDL")
  }

  /**
   * C++ original:
   * ```cpp
   * void interleaveDDLCreationAndDraw(GrDirectContext*, SkPicture*)
   * ```
   */
  private fun interleaveDDLCreationAndDraw(param0: GrDirectContext?, param1: SkPicture?) {
    TODO("Implement interleaveDDLCreationAndDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawAllTilesDirectly(GrDirectContext*, SkPicture*)
   * ```
   */
  private fun drawAllTilesDirectly(param0: GrDirectContext?, param1: SkPicture?) {
    TODO("Implement drawAllTilesDirectly")
  }

  /**
   * C++ original:
   * ```cpp
   * void dropCallbackContexts()
   * ```
   */
  private fun dropCallbackContexts() {
    TODO("Implement dropCallbackContexts")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetAllTiles()
   * ```
   */
  private fun resetAllTiles() {
    TODO("Implement resetAllTiles")
  }

  /**
   * C++ original:
   * ```cpp
   * int numTiles() const { return fNumXDivisions * fNumYDivisions; }
   * ```
   */
  private fun numTiles(): Int {
    TODO("Implement numTiles")
  }

  /**
   * C++ original:
   * ```cpp
   * void createBackendTextures(SkTaskGroup*, GrDirectContext*)
   * ```
   */
  private fun createBackendTextures(param0: SkTaskGroup?, param1: GrDirectContext?) {
    TODO("Implement createBackendTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * void deleteBackendTextures(SkTaskGroup*, GrDirectContext*)
   * ```
   */
  private fun deleteBackendTextures(param0: SkTaskGroup?, param1: GrDirectContext?) {
    TODO("Implement deleteBackendTextures")
  }

  public data class TileData public constructor(
    private var fID: Int,
    private var fClip: Int,
    private var fPaddingOutsets: Int,
    private var fPlaybackChar: GrSurfaceCharacterization,
    private var fCallbackContext: Int,
    private var fTileSurface: Int,
    private var fDisplayList: Int,
  ) {
    public fun initialized(): Boolean {
      TODO("Implement initialized")
    }

    public fun `init`(
      id: Int,
      param1: GrDirectContext?,
      dstChar: GrSurfaceCharacterization,
      clip: SkIRect,
      paddingOutsets: SkIRect,
    ) {
      TODO("Implement init")
    }

    public fun createDDL(param0: SkPicture?) {
      TODO("Implement createDDL")
    }

    public fun dropDDL() {
      TODO("Implement dropDDL")
    }

    public fun precompile(param0: GrDirectContext?) {
      TODO("Implement precompile")
    }

    public fun drawSKPDirectly(param0: GrDirectContext?, param1: SkPicture?) {
      TODO("Implement drawSKPDirectly")
    }

    public fun draw(param0: GrDirectContext?) {
      TODO("Implement draw")
    }

    public fun reset() {
      TODO("Implement reset")
    }

    public fun id(): Int {
      TODO("Implement id")
    }

    public fun clipRect(): Int {
      TODO("Implement clipRect")
    }

    public fun paddedRectSize(): Int {
      TODO("Implement paddedRectSize")
    }

    public fun padOffset(): Int {
      TODO("Implement padOffset")
    }

    public fun ddl(): Int {
      TODO("Implement ddl")
    }

    public fun makePromiseImageForDst(param0: SkSp<GrContextThreadSafeProxy>): Int {
      TODO("Implement makePromiseImageForDst")
    }

    public fun dropCallbackContext() {
      TODO("Implement dropCallbackContext")
    }

    private fun makeWrappedTileDest(context: GrRecordingContext?): Int {
      TODO("Implement makeWrappedTileDest")
    }

    private fun refCallbackContext(): Int {
      TODO("Implement refCallbackContext")
    }

    public companion object {
      public fun createBackendTexture(param0: GrDirectContext?, param1: TileData?) {
        TODO("Implement createBackendTexture")
      }

      public fun deleteBackendTexture(param0: GrDirectContext?, param1: TileData?) {
        TODO("Implement deleteBackendTexture")
      }
    }
  }
}

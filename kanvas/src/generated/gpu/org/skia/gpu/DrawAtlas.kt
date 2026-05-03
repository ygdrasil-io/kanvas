package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkColorType

public typealias DrawAtlas = DrawAtlas

/**
 * C++ original:
 * ```cpp
 * class DrawAtlas {
 * public:
 *     /** Is the atlas allowed to use more than one texture? */
 *     enum class AllowMultitexturing : bool { kNo, kYes };
 *
 *     /** Should the atlas use storage textures? */
 *     enum class UseStorageTextures : bool { kNo, kYes };
 *
 *     /**
 *      * Returns a DrawAtlas.
 *      *  @param ct                  The colorType which this atlas will store.
 *      *  @param bpp                 Size in bytes of each pixel.
 *      *  @param width               Width in pixels of the atlas.
 *      *  @param height              Height in pixels of the atlas.
 *      *  @param plotWidth           The width of each plot. width/plotWidth should be an integer.
 *      *  @param plotWidth           The height of each plot. height/plotHeight should be an integer.
 *      *  @param atlasGeneration     A pointer to the context's generation counter.
 *      *  @param allowMultitexturing Can the atlas use more than one texture.
 *      *  @param useStorageTextures  Should the atlas use storage textures.
 *      *  @param evictor             A pointer to an eviction callback class.
 *      *  @param label               Label for texture resources.
 *      *
 *      *  @return                    An initialized DrawAtlas, or nullptr if creation fails.
 *      */
 *     static std::unique_ptr<DrawAtlas> Make(SkColorType ct, size_t bpp,
 *                                            int width, int height,
 *                                            int plotWidth, int plotHeight,
 *                                            AtlasGenerationCounter* generationCounter,
 *                                            AllowMultitexturing allowMultitexturing,
 *                                            UseStorageTextures useStorageTextures,
 *                                            PlotEvictionCallback* evictor,
 *                                            std::string_view label);
 *
 *     /**
 *      * Adds a width x height subimage to the atlas. Upon success it returns 'kSucceeded' and returns
 *      * the ID and the subimage's coordinates in the backing texture. 'kTryAgain' is returned if
 *      * the subimage cannot fit in the atlas without overwriting texels that will be read in the
 *      * current list of draws. This indicates that the Device should end its current draw, snap a
 *      * DrawPass, and begin another before adding more data. 'kError' will be returned when some
 *      * unrecoverable error was encountered while trying to add the subimage. In this case the draw
 *      * being created should be discarded.
 *      *
 *      * This tracking does not generate UploadTasks per se. Instead, when the RenderPassTask is
 *      * ready to be snapped, recordUploads() will be called by the Device and that will generate the
 *      * necessary UploadTasks. If the useCachedUploads argument in recordUploads() is true, this
 *      * will generate uploads for the entire area of each Plot that has changed since the last
 *      * eviction. Otherwise it will only generate uploads for newly added changes.
 *      *
 *      * NOTE: When a draw that reads from the atlas is added to the DrawList, the client using this
 *      * DrawAtlas must immediately call 'setLastUseToken' with the currentToken from the Recorder,
 *      * otherwise the next call to addToAtlas might cause the previous data to be overwritten before
 *      * it has been read.
 *      */
 *
 *     enum class ErrorCode {
 *         kError,
 *         kSucceeded,
 *         kTryAgain
 *     };
 *
 *     ErrorCode addToAtlas(Recorder*, int width, int height, const void* image, AtlasLocator*);
 *     ErrorCode addRect(Recorder*, int width, int height, AtlasLocator*);
 *     // Returns a Pixmap pointing to the backing data for the locator. Optionally, the caller can
 *     // provide an inset that is applied to all four sides. This is useful for use cases that need
 *     // to leave space between items in the atlas. The pixmap will exclude the padding. The entire
 *     // atlas is cleared to zero when allocated. By passing an initialColor here, the caller can
 *     // re-clear the entire locator's rect (including any padding) to any color.
 *     SkPixmap prepForRender(const AtlasLocator&,
 *                            int padding = 0,
 *                            std::optional<SkColor> initialColor = {});
 *     bool recordUploads(DrawContext*, Recorder*);
 *
 *     const sk_sp<TextureProxy>* getProxies() const { return fProxies; }
 *
 *     uint32_t atlasID() const { return fAtlasID; }
 *     uint64_t atlasGeneration() const { return fAtlasGeneration; }
 *     uint32_t numActivePages() const { return fNumActivePages; }
 *     unsigned int numPlots() const { return fNumPlots; }
 *     SkISize plotSize() const { return {fPlotWidth, fPlotHeight}; }
 *     uint32_t getListIndex(const PlotLocator& locator) {
 *         return locator.pageIndex() * fNumPlots + locator.plotIndex();
 *     }
 *
 *     bool hasID(const PlotLocator& plotLocator) {
 *         if (!plotLocator.isValid()) {
 *             return false;
 *         }
 *
 *         uint32_t plot = plotLocator.plotIndex();
 *         uint32_t page = plotLocator.pageIndex();
 *         uint64_t plotGeneration = fPages[page].fPlotArray[plot]->genID();
 *         uint64_t locatorGeneration = plotLocator.genID();
 *         return plot < fNumPlots && page < fNumActivePages && plotGeneration == locatorGeneration;
 *     }
 *
 *     /** To ensure the atlas does not evict a given entry, the client must set the last use token. */
 *     void setLastUseToken(const AtlasLocator& atlasLocator, Token token) {
 *         Plot* plot = this->findPlot(atlasLocator);
 *         this->internalSetLastUseToken(plot, atlasLocator.pageIndex(), token);
 *     }
 *
 *     void setLastUseTokenBulk(const BulkUsePlotUpdater& updater, Token token) {
 *         int count = updater.count();
 *         for (int i = 0; i < count; i++) {
 *             const BulkUsePlotUpdater::PlotData& pd = updater.plotData(i);
 *             // it's possible we've added a plot to the updater and subsequently the plot's page
 *             // was deleted -- so we check to prevent a crash
 *             if (pd.fPageIndex < fNumActivePages) {
 *                 Plot* plot = fPages[pd.fPageIndex].fPlotArray[pd.fPlotIndex].get();
 *                 this->internalSetLastUseToken(plot, pd.fPageIndex, token);
 *             }
 *         }
 *     }
 *
 *     void compact(Token startTokenForNextFlush);
 *
 *     // Mark all plots with any content as full. Used only with Vello because it can't do
 *     // new renders to a texture without a clear.
 *     void markUsedPlotsAsFull();
 *
 *     // Will try to clear out any GPU resources that aren't needed for any pending uploads or draws.
 *     // TODO: Delete backing data for Plots that don't have pending uploads.
 *     void freeGpuResources(Token token);
 *
 *     void evictAllPlots();
 *
 *     uint32_t maxPages() const {
 *         return fMaxPages;
 *     }
 *
 * #if defined(GPU_TEST_UTILS)
 *     template <typename F>
 *     int iteratePlots(F&& func) const {
 *         int count = 0;
 *         PlotList::Iter plotIter;
 *         for (uint32_t pageIndex = 0; pageIndex < this->maxPages(); ++pageIndex) {
 *             plotIter.init(fPages[pageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
 *             while (Plot* plot = plotIter.get()) {
 *                 if (func(plot)) {
 *                     count++;
 *                 }
 *                 plotIter.next();
 *             }
 *         }
 *         return count;
 *     }
 *
 *     int numAllocatedPlots() const;
 *     int numNonEmptyPlots() const;
 * #endif
 *
 * private:
 *     DrawAtlas(SkColorType, size_t bpp,
 *               int width, int height, int plotWidth, int plotHeight,
 *               AtlasGenerationCounter* generationCounter,
 *               AllowMultitexturing allowMultitexturing,
 *               UseStorageTextures useStorageTextures,
 *               std::string_view label);
 *
 *     bool addRectToPage(unsigned int pageIdx, int width, int height, AtlasLocator*);
 *
 *     void updatePlot(Plot* plot, AtlasLocator*);
 *
 *     inline void makeMRU(Plot* plot, int pageIdx) {
 *         if (fPages[pageIdx].fPlotList.head() == plot) {
 *             return;
 *         }
 *
 *         fPages[pageIdx].fPlotList.remove(plot);
 *         fPages[pageIdx].fPlotList.addToHead(plot);
 *
 *         // No MRU update for pages -- since we will always try to add from
 *         // the front and remove from the back there is no need for MRU.
 *     }
 *
 *     Plot* findPlot(const AtlasLocator& atlasLocator) {
 *         SkASSERT(this->hasID(atlasLocator.plotLocator()));
 *         uint32_t pageIdx = atlasLocator.pageIndex();
 *         uint32_t plotIdx = atlasLocator.plotIndex();
 *         return fPages[pageIdx].fPlotArray[plotIdx].get();
 *     }
 *
 *     void internalSetLastUseToken(Plot* plot, uint32_t pageIdx, Token token) {
 *         this->makeMRU(plot, pageIdx);
 *         plot->setLastUseToken(token);
 *     }
 *
 *     bool createPages(AtlasGenerationCounter*);
 *     bool activateNewPage(Recorder*);
 *     void deactivateLastPage();
 *
 *     // If freeData is true, this will free the backing data as well. This should only be used
 *     // when we know we won't be adding to the Plot immediately afterwards.
 *     void processEvictionAndResetRects(Plot* plot, bool freeData);
 *
 *     SkColorType           fColorType;
 *     size_t                fBytesPerPixel;
 *     int                   fTextureWidth;
 *     int                   fTextureHeight;
 *     int                   fPlotWidth;
 *     int                   fPlotHeight;
 *     unsigned int          fNumPlots;
 *     UseStorageTextures    fUseStorageTextures;
 *     const std::string     fLabel;
 *     uint32_t              fAtlasID;   // unique identifier for this atlas
 *
 *     // A counter to track the atlas eviction state for Glyphs. Each Glyph has a PlotLocator
 *     // which contains its current generation. When the atlas evicts a plot, it increases
 *     // the generation counter. If a Glyph's generation is less than the atlas's
 *     // generation, then it knows it's been evicted and is either free to be deleted or
 *     // re-added to the atlas if necessary.
 *     AtlasGenerationCounter* const fGenerationCounter;
 *     uint64_t                      fAtlasGeneration;
 *
 *     // nextFlushToken() value at the end of the previous DrawPass
 *     // TODO: rename
 *     Token fPrevFlushToken;
 *
 *     // the number of flushes since this atlas has been last used
 *     // TODO: rename
 *     int fFlushesSinceLastUse;
 *
 *     std::vector<PlotEvictionCallback*> fEvictionCallbacks;
 *
 *     struct Page {
 *         // allocated array of Plots
 *         std::unique_ptr<sk_sp<Plot>[]> fPlotArray;
 *         // LRU list of Plots (MRU at head - LRU at tail)
 *         PlotList fPlotList;
 *     };
 *     // proxies kept separate to make it easier to pass them up to client
 *     sk_sp<TextureProxy> fProxies[PlotLocator::kMaxMultitexturePages];
 *     Page fPages[PlotLocator::kMaxMultitexturePages];
 *     uint32_t fMaxPages;
 *
 *     uint32_t fNumActivePages;
 *
 *     SkDEBUGCODE(void validate(const AtlasLocator& atlasLocator) const;)
 * }
 * ```
 */
public abstract class DrawAtlas public constructor(
  colorType: SkColorType,
  bpp: ULong,
  width: Int,
  height: Int,
  plotWidth: Int,
  plotHeight: Int,
  generationCounter: AtlasGenerationCounter?,
  allowMultitexturing: AllowMultitexturing,
  useStorageTextures: UseStorageTextures,
  label: String,
) {
  /**
   * C++ original:
   * ```cpp
   * SkColorType           fColorType
   * ```
   */
  private var fColorType: SkColorType = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * size_t                fBytesPerPixel
   * ```
   */
  private var fBytesPerPixel: Int = TODO("Initialize fBytesPerPixel")

  /**
   * C++ original:
   * ```cpp
   * int                   fTextureWidth
   * ```
   */
  private var fTextureWidth: Int = TODO("Initialize fTextureWidth")

  /**
   * C++ original:
   * ```cpp
   * int                   fTextureHeight
   * ```
   */
  private var fTextureHeight: Int = TODO("Initialize fTextureHeight")

  /**
   * C++ original:
   * ```cpp
   * int                   fPlotWidth
   * ```
   */
  private var fPlotWidth: Int = TODO("Initialize fPlotWidth")

  /**
   * C++ original:
   * ```cpp
   * int                   fPlotHeight
   * ```
   */
  private var fPlotHeight: Int = TODO("Initialize fPlotHeight")

  /**
   * C++ original:
   * ```cpp
   * unsigned int          fNumPlots
   * ```
   */
  private var fNumPlots: UInt = TODO("Initialize fNumPlots")

  /**
   * C++ original:
   * ```cpp
   * UseStorageTextures    fUseStorageTextures
   * ```
   */
  private var fUseStorageTextures: UseStorageTextures = TODO("Initialize fUseStorageTextures")

  /**
   * C++ original:
   * ```cpp
   * const std::string     fLabel
   * ```
   */
  private val fLabel: Int = TODO("Initialize fLabel")

  /**
   * C++ original:
   * ```cpp
   * uint32_t              fAtlasID
   * ```
   */
  private var fAtlasID: Int = TODO("Initialize fAtlasID")

  /**
   * C++ original:
   * ```cpp
   * AtlasGenerationCounter* const fGenerationCounter
   * ```
   */
  private val fGenerationCounter: Int? = TODO("Initialize fGenerationCounter")

  /**
   * C++ original:
   * ```cpp
   * uint64_t                      fAtlasGeneration
   * ```
   */
  private var fAtlasGeneration: Int = TODO("Initialize fAtlasGeneration")

  /**
   * C++ original:
   * ```cpp
   * Token fPrevFlushToken
   * ```
   */
  private var fPrevFlushToken: Int = TODO("Initialize fPrevFlushToken")

  /**
   * C++ original:
   * ```cpp
   * int fFlushesSinceLastUse
   * ```
   */
  private var fFlushesSinceLastUse: Int = TODO("Initialize fFlushesSinceLastUse")

  /**
   * C++ original:
   * ```cpp
   * std::vector<PlotEvictionCallback*> fEvictionCallbacks
   * ```
   */
  private var fEvictionCallbacks: Int = TODO("Initialize fEvictionCallbacks")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fProxies
   * ```
   */
  private var fProxies: Int = TODO("Initialize fProxies")

  /**
   * C++ original:
   * ```cpp
   * Page fPages
   * ```
   */
  private var fPages: Page = TODO("Initialize fPages")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fMaxPages
   * ```
   */
  private var fMaxPages: Int = TODO("Initialize fMaxPages")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fNumActivePages
   * ```
   */
  private var fNumActivePages: Int = TODO("Initialize fNumActivePages")

  /**
   * C++ original:
   * ```cpp
   * DrawAtlas::ErrorCode DrawAtlas::addToAtlas(Recorder* recorder,
   *                                            int width, int height, const void* image,
   *                                            AtlasLocator* atlasLocator) {
   *     ErrorCode ec = this->addRect(recorder, width, height, atlasLocator);
   *     if (ec == ErrorCode::kSucceeded) {
   *         Plot* plot = this->findPlot(*atlasLocator);
   *         plot->copySubImage(*atlasLocator, image);
   *     }
   *
   *     return ec;
   * }
   * ```
   */
  public fun addToAtlas(
    recorder: Recorder?,
    width: Int,
    height: Int,
    image: Unit?,
    atlasLocator: AtlasLocator?,
  ): ErrorCode {
    TODO("Implement addToAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawAtlas::ErrorCode DrawAtlas::addRect(Recorder* recorder,
   *                                         int width, int height,
   *                                         AtlasLocator* atlasLocator) {
   *     if (width > fPlotWidth || height > fPlotHeight || width < 0 || height < 0) {
   *         return ErrorCode::kError;
   *     }
   *
   *     // We permit zero-sized rects to allow inverse fills in the PathAtlases to work,
   *     // but we don't want to enter them in the Rectanizer. So we handle this special case here.
   *     // For text this should be caught at a higher level, but if not the only end result
   *     // will be rendering a degenerate quad.
   *     if (width == 0 || height == 0) {
   *         if (fNumActivePages == 0) {
   *             // Make sure we have a Page for the AtlasLocator to refer to
   *             this->activateNewPage(recorder);
   *         }
   *         atlasLocator->updateRect(skgpu::IRect16::MakeXYWH(0, 0, 0, 0));
   *         // Use the MRU Plot from the first Page
   *         atlasLocator->updatePlotLocator(fPages[0].fPlotList.head()->plotLocator());
   *         return ErrorCode::kSucceeded;
   *     }
   *
   *     // Look through each page to see if we can upload without having to flush
   *     // We prioritize this upload to the first pages, not the most recently used, to make it easier
   *     // to remove unused pages in reverse page order.
   *     for (unsigned int pageIdx = 0; pageIdx < fNumActivePages; ++pageIdx) {
   *         if (this->addRectToPage(pageIdx, width, height, atlasLocator)) {
   *             return ErrorCode::kSucceeded;
   *         }
   *     }
   *
   *     // If the above fails, then see if the least recently used plot per page has already been
   *     // queued for upload if we're at max page allocation, or if the plot has aged out otherwise.
   *     // We wait until we've grown to the full number of pages to begin evicting already queued
   *     // plots so that we can maximize the opportunity for reuse.
   *     // As before we prioritize this upload to the first pages, not the most recently used.
   *     if (fNumActivePages == this->maxPages()) {
   *         for (unsigned int pageIdx = 0; pageIdx < fNumActivePages; ++pageIdx) {
   *             Plot* plot = fPages[pageIdx].fPlotList.tail();
   *             SkASSERT(plot);
   *             if (plot->lastUseToken() < recorder->priv().tokenTracker()->nextFlushToken()) {
   *                 this->processEvictionAndResetRects(plot, /*freeData=*/false);
   *                 SkDEBUGCODE(bool verify = )plot->addRect(width, height, atlasLocator);
   *                 SkASSERT(verify);
   *                 this->updatePlot(plot, atlasLocator);
   *                 return ErrorCode::kSucceeded;
   *             }
   *         }
   *     } else {
   *         // If we haven't activated all the available pages, try to create a new one and add to it
   *         if (!this->activateNewPage(recorder)) {
   *             return ErrorCode::kError;
   *         }
   *
   *         if (this->addRectToPage(fNumActivePages-1, width, height, atlasLocator)) {
   *             return ErrorCode::kSucceeded;
   *         } else {
   *             // If we fail to upload to a newly activated page then something has gone terribly
   *             // wrong - return an error
   *             return ErrorCode::kError;
   *         }
   *     }
   *
   *     if (!fNumActivePages) {
   *         return ErrorCode::kError;
   *     }
   *
   *     // All plots are currently in use by the current set of draws, so we need to fail. This
   *     // gives the Device a chance to snap the current set of uploads and draws, advance the draw
   *     // token, and call back into this function. The subsequent call will have plots available
   *     // for fresh uploads.
   *     return ErrorCode::kTryAgain;
   * }
   * ```
   */
  public fun addRect(
    recorder: Recorder?,
    width: Int,
    height: Int,
    atlasLocator: AtlasLocator?,
  ): ErrorCode {
    TODO("Implement addRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPixmap DrawAtlas::prepForRender(const AtlasLocator& locator,
   *                                   int padding,
   *                                   std::optional<SkColor> initialColor) {
   *     Plot* plot = this->findPlot(locator);
   *     return plot->prepForRender(locator, padding, initialColor);
   * }
   * ```
   */
  public abstract fun prepForRender(
    locator: Int,
    padding: Int,
    initialColor: Int,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * bool DrawAtlas::recordUploads(DrawContext* dc, Recorder* recorder) {
   *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
   *     for (uint32_t pageIdx = 0; pageIdx < fNumActivePages; ++pageIdx) {
   *         PlotList::Iter plotIter;
   *         plotIter.init(fPages[pageIdx].fPlotList, PlotList::Iter::kHead_IterStart);
   *         for (Plot* plot = plotIter.get(); plot; plot = plotIter.next()) {
   *             if (plot->needsUpload()) {
   *                 TextureProxy* proxy = fProxies[pageIdx].get();
   *                 SkASSERT(proxy);
   *
   *                 const void* dataPtr;
   *                 SkIRect dstRect;
   *                 std::tie(dataPtr, dstRect) = plot->prepareForUpload();
   *                 if (dstRect.isEmpty()) {
   *                     continue;
   *                 }
   *
   *                 std::vector<MipLevel> levels;
   *                 levels.push_back({dataPtr, fBytesPerPixel*fPlotWidth});
   *
   *                 // Src and dst colorInfo are the same
   *                 SkColorInfo colorInfo(fColorType, kUnknown_SkAlphaType, nullptr);
   *                 const UploadSource uploadSource = UploadSource::Make(
   *                         recorder->priv().caps(), *proxy, colorInfo, colorInfo, levels, dstRect);
   *                 if (!uploadSource.isValid()) {
   *                     return false;
   *                 }
   *                 if (!dc->recordUpload(recorder,
   *                                       sk_ref_sp(proxy),
   *                                       colorInfo,
   *                                       colorInfo,
   *                                       uploadSource,
   *                                       dstRect,
   *                                       /*ConditionalUploadContext=*/nullptr)) {
   *                     return false;
   *                 }
   *             }
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  public fun recordUploads(dc: DrawContext?, recorder: Recorder?): Boolean {
    TODO("Implement recordUploads")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<TextureProxy>* getProxies() const { return fProxies; }
   * ```
   */
  public fun getProxies(): Int {
    TODO("Implement getProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t atlasID() const { return fAtlasID; }
   * ```
   */
  public fun atlasID(): Int {
    TODO("Implement atlasID")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t atlasGeneration() const { return fAtlasGeneration; }
   * ```
   */
  public fun atlasGeneration(): Int {
    TODO("Implement atlasGeneration")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t numActivePages() const { return fNumActivePages; }
   * ```
   */
  public fun numActivePages(): Int {
    TODO("Implement numActivePages")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned int numPlots() const { return fNumPlots; }
   * ```
   */
  public fun numPlots(): UInt {
    TODO("Implement numPlots")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize plotSize() const { return {fPlotWidth, fPlotHeight}; }
   * ```
   */
  public fun plotSize(): Int {
    TODO("Implement plotSize")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getListIndex(const PlotLocator& locator) {
   *         return locator.pageIndex() * fNumPlots + locator.plotIndex();
   *     }
   * ```
   */
  public fun getListIndex(locator: PlotLocator): Int {
    TODO("Implement getListIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasID(const PlotLocator& plotLocator) {
   *         if (!plotLocator.isValid()) {
   *             return false;
   *         }
   *
   *         uint32_t plot = plotLocator.plotIndex();
   *         uint32_t page = plotLocator.pageIndex();
   *         uint64_t plotGeneration = fPages[page].fPlotArray[plot]->genID();
   *         uint64_t locatorGeneration = plotLocator.genID();
   *         return plot < fNumPlots && page < fNumActivePages && plotGeneration == locatorGeneration;
   *     }
   * ```
   */
  public fun hasID(plotLocator: PlotLocator): Boolean {
    TODO("Implement hasID")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLastUseToken(const AtlasLocator& atlasLocator, Token token) {
   *         Plot* plot = this->findPlot(atlasLocator);
   *         this->internalSetLastUseToken(plot, atlasLocator.pageIndex(), token);
   *     }
   * ```
   */
  public fun setLastUseToken(atlasLocator: AtlasLocator, token: Token) {
    TODO("Implement setLastUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLastUseTokenBulk(const BulkUsePlotUpdater& updater, Token token) {
   *         int count = updater.count();
   *         for (int i = 0; i < count; i++) {
   *             const BulkUsePlotUpdater::PlotData& pd = updater.plotData(i);
   *             // it's possible we've added a plot to the updater and subsequently the plot's page
   *             // was deleted -- so we check to prevent a crash
   *             if (pd.fPageIndex < fNumActivePages) {
   *                 Plot* plot = fPages[pd.fPageIndex].fPlotArray[pd.fPlotIndex].get();
   *                 this->internalSetLastUseToken(plot, pd.fPageIndex, token);
   *             }
   *         }
   *     }
   * ```
   */
  public fun setLastUseTokenBulk(updater: BulkUsePlotUpdater, token: Token) {
    TODO("Implement setLastUseTokenBulk")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawAtlas::compact(Token startTokenForNextFlush) {
   *     if (fNumActivePages < 1) {
   *         fPrevFlushToken = startTokenForNextFlush;
   *         return;
   *     }
   *
   *     // For all plots, reset number of flushes since used if used this frame.
   *     PlotList::Iter plotIter;
   *     bool atlasUsedThisFlush = false;
   *     for (uint32_t pageIndex = 0; pageIndex < fNumActivePages; ++pageIndex) {
   *         plotIter.init(fPages[pageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *         while (Plot* plot = plotIter.get()) {
   *             // Reset number of flushes since used
   *             if (plot->lastUseToken().inInterval(fPrevFlushToken, startTokenForNextFlush)) {
   *                 plot->resetFlushesSinceLastUsed();
   *                 atlasUsedThisFlush = true;
   *             }
   *
   *             plotIter.next();
   *         }
   *     }
   *
   *     if (atlasUsedThisFlush) {
   *         fFlushesSinceLastUse = 0;
   *     } else {
   *         ++fFlushesSinceLastUse;
   *     }
   *
   *     // We only try to compact if the atlas was used in the recently completed flush or
   *     // hasn't been used in a long time.
   *     // This is to handle the case where a lot of text or path rendering has occurred but then just
   *     // a blinking cursor is drawn.
   *     if (atlasUsedThisFlush || fFlushesSinceLastUse > kAtlasRecentlyUsedCount) {
   *         TArray<Plot*> availablePlots;
   *         uint32_t lastPageIndex = fNumActivePages - 1;
   *
   *         // For all pages but the last one, update number of flushes since used, and check to see
   *         // if there are any in the first pages that the last page can safely upload to.
   *         for (uint32_t pageIndex = 0; pageIndex < lastPageIndex; ++pageIndex) {
   *             if constexpr (kDumpAtlasData) {
   *                 SkDebugf("page %u: ", pageIndex);
   *             }
   *
   *             plotIter.init(fPages[pageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *             while (Plot* plot = plotIter.get()) {
   *                 // Update number of flushes since plot was last used
   *                 // We only increment the 'sinceLastUsed' count for flushes where the atlas was used
   *                 // to avoid deleting everything when we return to text drawing in the blinking
   *                 // cursor case
   *                 if (!plot->lastUseToken().inInterval(fPrevFlushToken, startTokenForNextFlush)) {
   *                     plot->incFlushesSinceLastUsed();
   *                 }
   *
   *                 if constexpr (kDumpAtlasData) {
   *                     SkDebugf("%d ", plot->flushesSinceLastUsed());
   *                 }
   *
   *                 // Count plots we can potentially upload to in all pages except the last one
   *                 // (the potential compactee).
   *                 if (plot->flushesSinceLastUsed() > kPlotRecentlyUsedCount) {
   *                     availablePlots.push_back() = plot;
   *                 }
   *
   *                 plotIter.next();
   *             }
   *
   *             if constexpr (kDumpAtlasData) {
   *                 SkDebugf("\n");
   *             }
   *         }
   *
   *         // Count recently used plots in the last page and evict any that are no longer in use.
   *         // Since we prioritize uploading to the first pages, this will eventually
   *         // clear out usage of this page unless we have a large need.
   *         plotIter.init(fPages[lastPageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *         unsigned int usedPlots = 0;
   *         if constexpr (kDumpAtlasData) {
   *             SkDebugf("page %u: ", lastPageIndex);
   *         }
   *         while (Plot* plot = plotIter.get()) {
   *             // Update number of flushes since plot was last used
   *             if (!plot->lastUseToken().inInterval(fPrevFlushToken, startTokenForNextFlush)) {
   *                 plot->incFlushesSinceLastUsed();
   *             }
   *
   *             if constexpr (kDumpAtlasData) {
   *                 SkDebugf("%d ", plot->flushesSinceLastUsed());
   *             }
   *
   *             // If this plot was used recently
   *             if (plot->flushesSinceLastUsed() <= kPlotRecentlyUsedCount) {
   *                 usedPlots++;
   *             } else if (plot->lastUseToken() != Token::InvalidToken()) {
   *                 // otherwise if aged out just evict it.
   *                 this->processEvictionAndResetRects(plot, /*freeData=*/false);
   *             }
   *             plotIter.next();
   *         }
   *
   *         if constexpr (kDumpAtlasData) {
   *             SkDebugf("\n");
   *         }
   *
   *         // If recently used plots in the last page are using less than a quarter of the page, try
   *         // to evict them if there's available space in lower index pages. Since we prioritize
   *         // uploading to the first pages, this will eventually clear out usage of this page unless
   *         // we have a large need.
   *         if (!availablePlots.empty() && usedPlots && usedPlots <= fNumPlots / 4) {
   *             plotIter.init(fPages[lastPageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *             while (Plot* plot = plotIter.get()) {
   *                 // If this plot was used recently
   *                 int plotFlushes = plot->flushesSinceLastUsed();
   *                 if (kPlotUsedCountBeforeEvict <= plotFlushes &&
   *                     plotFlushes <= kPlotRecentlyUsedCount) {
   *                     // See if there's room in an lower index page and if so evict.
   *                     // We need to be somewhat harsh here so that a handful of plots that are
   *                     // consistently in use don't end up locking the page in memory.
   *                     if (!availablePlots.empty()) {
   *                         this->processEvictionAndResetRects(plot, /*freeData=*/true);
   *                         this->processEvictionAndResetRects(availablePlots.back(),
   *                                                            /*freeData=*/false);
   *                         availablePlots.pop_back();
   *                         --usedPlots;
   *                     }
   *                     if (usedPlots == 0 || availablePlots.empty()) {
   *                         break;
   *                     }
   *                 }
   *                 plotIter.next();
   *             }
   *         }
   *
   *         // If none of the plots in the last page have been used recently, delete it.
   *         if (usedPlots == 0) {
   *             if constexpr (kDumpAtlasData) {
   *                 SkDebugf("delete %u\n", fNumActivePages-1);
   *             }
   *
   *             this->deactivateLastPage();
   *             fFlushesSinceLastUse = 0;
   *         }
   *     }
   *
   *     fPrevFlushToken = startTokenForNextFlush;
   * }
   * ```
   */
  public fun compact(startTokenForNextFlush: Token) {
    TODO("Implement compact")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawAtlas::markUsedPlotsAsFull() {
   *     PlotList::Iter plotIter;
   *     for (uint32_t pageIndex = 0; pageIndex < fNumActivePages; ++pageIndex) {
   *         plotIter.init(fPages[pageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *         while (Plot* plot = plotIter.get()) {
   *             plot->markFullIfUsed();
   *             plotIter.next();
   *         }
   *     }
   * }
   * ```
   */
  public fun markUsedPlotsAsFull() {
    TODO("Implement markUsedPlotsAsFull")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawAtlas::freeGpuResources(Token token) {
   *     PlotList::Iter plotIter;
   *     for (int pageIndex = (int)(fNumActivePages)-1; pageIndex >= 0; --pageIndex) {
   *         const Page& currPage = fPages[pageIndex];
   *         plotIter.init(currPage.fPlotList, PlotList::Iter::kHead_IterStart);
   *         while (Plot* plot = plotIter.get()) {
   *             if (plot->lastUseToken().inInterval(fPrevFlushToken, token)) {
   *                 // This page is in use and we can only deactivate pages from high index
   *                 // to low index, so bail.
   *                 return;
   *             }
   *             plotIter.next();
   *         }
   *         this->deactivateLastPage();
   *     }
   * }
   * ```
   */
  public fun freeGpuResources(token: Token) {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawAtlas::evictAllPlots() {
   *     PlotList::Iter plotIter;
   *     for (uint32_t pageIndex = 0; pageIndex < fNumActivePages; ++pageIndex) {
   *         plotIter.init(fPages[pageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *         while (Plot* plot = plotIter.get()) {
   *             this->processEvictionAndResetRects(plot, /*freeData=*/true);
   *             plotIter.next();
   *         }
   *     }
   * }
   * ```
   */
  public fun evictAllPlots() {
    TODO("Implement evictAllPlots")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t maxPages() const {
   *         return fMaxPages;
   *     }
   * ```
   */
  public fun maxPages(): Int {
    TODO("Implement maxPages")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename F>
   *     int iteratePlots(F&& func) const {
   *         int count = 0;
   *         PlotList::Iter plotIter;
   *         for (uint32_t pageIndex = 0; pageIndex < this->maxPages(); ++pageIndex) {
   *             plotIter.init(fPages[pageIndex].fPlotList, PlotList::Iter::kHead_IterStart);
   *             while (Plot* plot = plotIter.get()) {
   *                 if (func(plot)) {
   *                     count++;
   *                 }
   *                 plotIter.next();
   *             }
   *         }
   *         return count;
   *     }
   * ```
   */
  public fun <F> iteratePlots(func: F): Int {
    TODO("Implement iteratePlots")
  }

  /**
   * C++ original:
   * ```cpp
   * int DrawAtlas::numAllocatedPlots() const {
   *     return this->iteratePlots([](const Plot* plot) {
   *         return plot->hasAllocation();
   *     });
   * }
   * ```
   */
  public fun numAllocatedPlots(): Int {
    TODO("Implement numAllocatedPlots")
  }

  /**
   * C++ original:
   * ```cpp
   * int DrawAtlas::numNonEmptyPlots() const {
   *     return this->iteratePlots([](const Plot* plot) {
   *         return !plot->isEmpty();
   *     });
   * }
   * ```
   */
  public fun numNonEmptyPlots(): Int {
    TODO("Implement numNonEmptyPlots")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawAtlas::addRectToPage(unsigned int pageIdx, int width, int height,
   *                               AtlasLocator* atlasLocator) {
   *     SkASSERT(fProxies[pageIdx]);
   *
   *     // look through all allocated plots for one we can share, in Most Recently Refed order
   *     PlotList::Iter plotIter;
   *     plotIter.init(fPages[pageIdx].fPlotList, PlotList::Iter::kHead_IterStart);
   *
   *     for (Plot* plot = plotIter.get(); plot; plot = plotIter.next()) {
   *         if (plot->addRect(width, height, atlasLocator)) {
   *             this->updatePlot(plot, atlasLocator);
   *             return true;
   *         }
   *     }
   *
   *     return false;
   * }
   * ```
   */
  private fun addRectToPage(
    pageIdx: UInt,
    width: Int,
    height: Int,
    atlasLocator: AtlasLocator?,
  ): Boolean {
    TODO("Implement addRectToPage")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void DrawAtlas::updatePlot(Plot* plot, AtlasLocator* atlasLocator) {
   *     int pageIdx = plot->pageIndex();
   *     this->makeMRU(plot, pageIdx);
   *
   *     // The actual upload will be created in recordUploads().
   *
   *     atlasLocator->updatePlotLocator(plot->plotLocator());
   *     SkDEBUGCODE(this->validate(*atlasLocator);)
   * }
   * ```
   */
  private fun updatePlot(plot: Plot?, atlasLocator: AtlasLocator?) {
    TODO("Implement updatePlot")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void makeMRU(Plot* plot, int pageIdx) {
   *         if (fPages[pageIdx].fPlotList.head() == plot) {
   *             return;
   *         }
   *
   *         fPages[pageIdx].fPlotList.remove(plot);
   *         fPages[pageIdx].fPlotList.addToHead(plot);
   *
   *         // No MRU update for pages -- since we will always try to add from
   *         // the front and remove from the back there is no need for MRU.
   *     }
   * ```
   */
  private fun makeMRU(plot: Plot?, pageIdx: Int) {
    TODO("Implement makeMRU")
  }

  /**
   * C++ original:
   * ```cpp
   * Plot* findPlot(const AtlasLocator& atlasLocator) {
   *         SkASSERT(this->hasID(atlasLocator.plotLocator()));
   *         uint32_t pageIdx = atlasLocator.pageIndex();
   *         uint32_t plotIdx = atlasLocator.plotIndex();
   *         return fPages[pageIdx].fPlotArray[plotIdx].get();
   *     }
   * ```
   */
  private fun findPlot(atlasLocator: AtlasLocator): Int {
    TODO("Implement findPlot")
  }

  /**
   * C++ original:
   * ```cpp
   * void internalSetLastUseToken(Plot* plot, uint32_t pageIdx, Token token) {
   *         this->makeMRU(plot, pageIdx);
   *         plot->setLastUseToken(token);
   *     }
   * ```
   */
  private fun internalSetLastUseToken(
    plot: Plot?,
    pageIdx: UInt,
    token: Token,
  ) {
    TODO("Implement internalSetLastUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawAtlas::createPages(AtlasGenerationCounter* generationCounter) {
   *     SkASSERT(SkIsPow2(fTextureWidth) && SkIsPow2(fTextureHeight));
   *
   *     int numPlotsX = fTextureWidth/fPlotWidth;
   *     int numPlotsY = fTextureHeight/fPlotHeight;
   *
   *     for (uint32_t i = 0; i < this->maxPages(); ++i) {
   *         // Proxies are uncreated at first
   *         fProxies[i] = nullptr;
   *
   *         // set up allocated plots
   *         fPages[i].fPlotArray = std::make_unique<sk_sp<Plot>[]>(numPlotsX * numPlotsY);
   *
   *         sk_sp<Plot>* currPlot = fPages[i].fPlotArray.get();
   *         for (int y = numPlotsY - 1, r = 0; y >= 0; --y, ++r) {
   *             for (int x = numPlotsX - 1, c = 0; x >= 0; --x, ++c) {
   *                 uint32_t plotIndex = r * numPlotsX + c;
   *                 currPlot->reset(new Plot(
   *                     i, plotIndex, generationCounter, x, y, fPlotWidth, fPlotHeight, fColorType,
   *                     fBytesPerPixel));
   *
   *                 // build LRU list
   *                 fPages[i].fPlotList.addToHead(currPlot->get());
   *                 ++currPlot;
   *             }
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun createPages(generationCounter: AtlasGenerationCounter?): Boolean {
    TODO("Implement createPages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawAtlas::activateNewPage(Recorder* recorder) {
   *     SkASSERT(fNumActivePages < this->maxPages());
   *     SkASSERT(!fProxies[fNumActivePages]);
   *
   *     const Caps* caps = recorder->priv().caps();
   *     auto textureInfo = fUseStorageTextures == UseStorageTextures::kYes
   *                                ? caps->getDefaultStorageTextureInfo(fColorType)
   *                                : caps->getDefaultSampledTextureInfo(fColorType,
   *                                                                     Mipmapped::kNo,
   *                                                                     recorder->priv().isProtected(),
   *                                                                     Renderable::kNo);
   *     fProxies[fNumActivePages] = TextureProxy::Make(caps,
   *                                                    recorder->priv().resourceProvider(),
   *                                                    {fTextureWidth, fTextureHeight},
   *                                                    textureInfo,
   *                                                    fLabel,
   *                                                    skgpu::Budgeted::kYes);
   *     if (!fProxies[fNumActivePages]) {
   *         return false;
   *     }
   *
   *     if constexpr (kDumpAtlasData) {
   *         SkDebugf("activated page#: %u\n", fNumActivePages);
   *     }
   *
   *     ++fNumActivePages;
   *     return true;
   * }
   * ```
   */
  private fun activateNewPage(recorder: Recorder?): Boolean {
    TODO("Implement activateNewPage")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void DrawAtlas::deactivateLastPage() {
   *     SkASSERT(fNumActivePages);
   *
   *     uint32_t lastPageIndex = fNumActivePages - 1;
   *
   *     int numPlotsX = fTextureWidth/fPlotWidth;
   *     int numPlotsY = fTextureHeight/fPlotHeight;
   *
   *     fPages[lastPageIndex].fPlotList.reset();
   *     for (int r = 0; r < numPlotsY; ++r) {
   *         for (int c = 0; c < numPlotsX; ++c) {
   *             uint32_t plotIndex = r * numPlotsX + c;
   *
   *             Plot* currPlot = fPages[lastPageIndex].fPlotArray[plotIndex].get();
   *             this->processEvictionAndResetRects(currPlot, /*freeData=*/true);
   *             currPlot->resetFlushesSinceLastUsed();
   *
   *             // rebuild the LRU list
   *             SkDEBUGCODE(currPlot->resetListPtrs());
   *             fPages[lastPageIndex].fPlotList.addToHead(currPlot);
   *         }
   *     }
   *
   *     // remove ref to the texture proxy
   *     fProxies[lastPageIndex].reset();
   *     --fNumActivePages;
   * }
   * ```
   */
  private fun deactivateLastPage() {
    TODO("Implement deactivateLastPage")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void DrawAtlas::processEvictionAndResetRects(Plot* plot, bool freeData) {
   *     // Process evictions
   *     if (!plot->isEmpty()) {
   *         const PlotLocator& plotLocator = plot->plotLocator();
   *         for (PlotEvictionCallback* evictor : fEvictionCallbacks) {
   *             evictor->evict(plotLocator);
   *         }
   *         fAtlasGeneration = fGenerationCounter->next();
   *     }
   *
   *     plot->resetRects(freeData);
   * }
   * ```
   */
  private fun processEvictionAndResetRects(plot: Plot?, freeData: Boolean) {
    TODO("Implement processEvictionAndResetRects")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(void validate(const AtlasLocator& atlasLocator) const;)
   * ```
   */
  private fun skDEBUGCODE(param0: (Int) -> Unit): Int {
    TODO("Implement skDEBUGCODE")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawAtlas::validate(const AtlasLocator& atlasLocator) const {
   *     // Verify that the plotIndex stored in the PlotLocator is consistent with the glyph rectangle
   *     int numPlotsX = fTextureWidth / fPlotWidth;
   *     int numPlotsY = fTextureHeight / fPlotHeight;
   *
   *     int plotIndex = atlasLocator.plotIndex();
   *     auto topLeft = atlasLocator.topLeft();
   *     int plotX = topLeft.x() / fPlotWidth;
   *     int plotY = topLeft.y() / fPlotHeight;
   *     SkASSERT(plotIndex == (numPlotsY - plotY - 1) * numPlotsX + (numPlotsX - plotX - 1));
   * }
   * ```
   */
  public fun validate(atlasLocator: AtlasLocator) {
    TODO("Implement validate")
  }

  public data class Page public constructor(
    public var fPlotArray: Int,
    public var fPlotList: Int,
  )

  public enum class AllowMultitexturing {
    kNo,
    kYes,
  }

  public enum class UseStorageTextures {
    kNo,
    kYes,
  }

  public enum class ErrorCode {
    kError,
    kSucceeded,
    kTryAgain,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<DrawAtlas> DrawAtlas::Make(SkColorType colorType, size_t bpp, int width,
     *                                            int height, int plotWidth, int plotHeight,
     *                                            AtlasGenerationCounter* generationCounter,
     *                                            AllowMultitexturing allowMultitexturing,
     *                                            UseStorageTextures useStorageTextures,
     *                                            PlotEvictionCallback* evictor,
     *                                            std::string_view label) {
     *     std::unique_ptr<DrawAtlas> atlas(new DrawAtlas(colorType, bpp, width, height,
     *                                                    plotWidth, plotHeight, generationCounter,
     *                                                    allowMultitexturing, useStorageTextures, label));
     *
     *     if (evictor != nullptr) {
     *         atlas->fEvictionCallbacks.emplace_back(evictor);
     *     }
     *     return atlas;
     * }
     * ```
     */
    public fun make(
      ct: SkColorType,
      bpp: ULong,
      width: Int,
      height: Int,
      plotWidth: Int,
      plotHeight: Int,
      generationCounter: AtlasGenerationCounter?,
      allowMultitexturing: AllowMultitexturing,
      useStorageTextures: UseStorageTextures,
      evictor: PlotEvictionCallback?,
      label: String,
    ): Int {
      TODO("Implement make")
    }
  }
}

package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.core.SkGlyph

/**
 * C++ original:
 * ```cpp
 * class TextAtlasManager : public AtlasGenerationCounter {
 * public:
 *     TextAtlasManager(Recorder*);
 *     ~TextAtlasManager();
 *
 *     // If getProxies returns nullptr, the client must not try to use other functions on the
 *     // StrikeCache which use the atlas.  This function *must* be called first, before other
 *     // functions which use the atlas.
 *     const sk_sp<TextureProxy>* getProxies(MaskFormat format,
 *                                           unsigned int* numActiveProxies) {
 *         format = this->resolveMaskFormat(format);
 *         if (this->initAtlas(format)) {
 *             *numActiveProxies = this->getAtlas(format)->numActivePages();
 *             return this->getAtlas(format)->getProxies();
 *         }
 *         *numActiveProxies = 0;
 *         return nullptr;
 *     }
 *
 *     void freeGpuResources();
 *
 *     bool hasGlyph(MaskFormat, sktext::gpu::Glyph*);
 *
 *     DrawAtlas::ErrorCode addGlyphToAtlas(const SkGlyph&,
 *                                          sktext::gpu::Glyph*,
 *                                          int srcPadding);
 *
 *     // To ensure the DrawAtlas does not evict the Glyph Mask from its texture backing store,
 *     // the client must pass in the current draw token along with the sktext::gpu::Glyph.
 *     // A BulkUsePlotUpdater is used to manage bulk last use token updating in the Atlas.
 *     // For convenience, this function will also set the use token for the current glyph if required
 *     // NOTE: the bulk uploader is only valid if the subrun has a valid atlasGeneration
 *     void addGlyphToBulkAndSetUseToken(BulkUsePlotUpdater*, MaskFormat,
 *                                       sktext::gpu::Glyph*, Token);
 *
 *     void setUseTokenBulk(const BulkUsePlotUpdater& updater,
 *                          Token token,
 *                          MaskFormat format) {
 *         this->getAtlas(format)->setLastUseTokenBulk(updater, token);
 *     }
 *
 *     bool recordUploads(DrawContext* dc);
 *
 *     void evictAtlases() {
 *         for (int i = 0; i < kMaskFormatCount; ++i) {
 *             if (fAtlases[i]) {
 *                 fAtlases[i]->evictAllPlots();
 *             }
 *         }
 *     }
 *
 *     void compact();
 *
 *     // Some clients may wish to verify the integrity of the texture backing store of the
 *     // DrawAtlas. The atlasGeneration returned below is a monotonically increasing number which
 *     // changes every time something is removed from the texture backing store.
 *     uint64_t atlasGeneration(skgpu::MaskFormat format) const {
 *         return this->getAtlas(format)->atlasGeneration();
 *     }
 *
 *     ///////////////////////////////////////////////////////////////////////////
 *     // Functions intended debug only
 *
 *     void setAtlasDimensionsToMinimum_ForTesting();
 *     void setMaxPages_TestingOnly(uint32_t maxPages);
 *
 * private:
 *     bool initAtlas(MaskFormat);
 *     // Change an expected 565 mask format to 8888 if 565 is not supported (will happen when using
 *     // Metal on Intel MacOS). The actual conversion of the data is handled in
 *     // get_packed_glyph_image() in StrikeCache.cpp
 *     MaskFormat resolveMaskFormat(MaskFormat format) const;
 *
 *     // There is a 1:1 mapping between skgpu::MaskFormats and atlas indices
 *     static int MaskFormatToAtlasIndex(skgpu::MaskFormat format) {
 *         return static_cast<int>(format);
 *     }
 *     static skgpu::MaskFormat AtlasIndexToMaskFormat(int idx) {
 *         return static_cast<skgpu::MaskFormat>(idx);
 *     }
 *
 *     DrawAtlas* getAtlas(skgpu::MaskFormat format) const {
 *         format = this->resolveMaskFormat(format);
 *         int atlasIndex = MaskFormatToAtlasIndex(format);
 *         SkASSERT(fAtlases[atlasIndex]);
 *         return fAtlases[atlasIndex].get();
 *     }
 *
 *     Recorder* fRecorder;
 *     DrawAtlas::AllowMultitexturing fAllowMultitexturing;
 *     std::unique_ptr<DrawAtlas> fAtlases[kMaskFormatCount];
 *     static_assert(kMaskFormatCount == 3);
 *     bool fSupportBilerpAtlas;
 *     DrawAtlasConfig fAtlasConfig;
 * }
 * ```
 */
public open class TextAtlasManager public constructor(
  recorder: Recorder?,
) : AtlasGenerationCounter() {
  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder? = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * DrawAtlas::AllowMultitexturing fAllowMultitexturing
   * ```
   */
  private var fAllowMultitexturing: Int = TODO("Initialize fAllowMultitexturing")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<DrawAtlas> fAtlases
   * ```
   */
  private var fAtlases: Int = TODO("Initialize fAtlases")

  /**
   * C++ original:
   * ```cpp
   * bool fSupportBilerpAtlas
   * ```
   */
  private var fSupportBilerpAtlas: Boolean = TODO("Initialize fSupportBilerpAtlas")

  /**
   * C++ original:
   * ```cpp
   * DrawAtlasConfig fAtlasConfig
   * ```
   */
  private var fAtlasConfig: Int = TODO("Initialize fAtlasConfig")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<TextureProxy>* getProxies(MaskFormat format,
   *                                           unsigned int* numActiveProxies) {
   *         format = this->resolveMaskFormat(format);
   *         if (this->initAtlas(format)) {
   *             *numActiveProxies = this->getAtlas(format)->numActivePages();
   *             return this->getAtlas(format)->getProxies();
   *         }
   *         *numActiveProxies = 0;
   *         return nullptr;
   *     }
   * ```
   */
  public fun getProxies(format: MaskFormat, numActiveProxies: UInt?): Int {
    TODO("Implement getProxies")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextAtlasManager::freeGpuResources() {
   *     auto tokenTracker = fRecorder->priv().tokenTracker();
   *     for (int i = 0; i < kMaskFormatCount; ++i) {
   *         if (fAtlases[i]) {
   *             fAtlases[i]->freeGpuResources(tokenTracker->nextFlushToken());
   *         }
   *     }
   * }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextAtlasManager::hasGlyph(MaskFormat format, Glyph* glyph) {
   *     SkASSERT(glyph);
   *     return this->getAtlas(format)->hasID(glyph->fAtlasLocator.plotLocator());
   * }
   * ```
   */
  public fun hasGlyph(format: MaskFormat, glyph: Glyph?): Boolean {
    TODO("Implement hasGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawAtlas::ErrorCode TextAtlasManager::addGlyphToAtlas(const SkGlyph& skGlyph,
   *                                                        Glyph* glyph,
   *                                                        int srcPadding) {
   * #if !defined(SK_DISABLE_SDF_TEXT)
   *     SkASSERT(0 <= srcPadding && srcPadding <= SK_DistanceFieldInset);
   * #else
   *     SkASSERT(0 <= srcPadding);
   * #endif
   *
   *     if (skGlyph.image() == nullptr) {
   *         return DrawAtlas::ErrorCode::kError;
   *     }
   *     SkASSERT(glyph != nullptr);
   *
   *     MaskFormat glyphFormat = Glyph::FormatFromSkGlyph(skGlyph.maskFormat());
   *     MaskFormat expectedMaskFormat = this->resolveMaskFormat(glyphFormat);
   *     int bytesPerPixel = MaskFormatBytesPerPixel(expectedMaskFormat);
   *
   *     int padding;
   *     switch (srcPadding) {
   *         case 0:
   *             // The direct mask/image case.
   *             padding = 0;
   *             if (fSupportBilerpAtlas) {
   *                 // Force direct masks (glyph with no padding) to have padding.
   *                 padding = 1;
   *                 srcPadding = 1;
   *             }
   *             break;
   *         case 1:
   *             // The transformed mask/image case.
   *             padding = 1;
   *             break;
   * #if !defined(SK_DISABLE_SDF_TEXT)
   *         case SK_DistanceFieldInset:
   *             // The SDFT case.
   *             // If the srcPadding == SK_DistanceFieldInset (SDFT case) then the padding is built
   *             // into the image on the glyph; no extra padding needed.
   *             // TODO: can the SDFT glyph image in the cache be reduced by the padding?
   *             padding = 0;
   *             break;
   * #endif
   *         default:
   *             // The padding is not one of the know forms.
   *             return DrawAtlas::ErrorCode::kError;
   *     }
   *
   *     const int width = skGlyph.width() + 2*padding;
   *     const int height = skGlyph.height() + 2*padding;
   *     int rowBytes = width * bytesPerPixel;
   *     size_t size = height * rowBytes;
   *
   *     // Temporary storage for normalizing glyph image.
   *     SkAutoSMalloc<1024> storage(size);
   *     void* dataPtr = storage.get();
   *     if (padding > 0) {
   *         sk_bzero(dataPtr, size);
   *         // Advance in one row and one column.
   *         dataPtr = (char*)(dataPtr) + rowBytes + bytesPerPixel;
   *     }
   *
   *     get_packed_glyph_image(skGlyph, rowBytes, expectedMaskFormat, dataPtr);
   *
   *     DrawAtlas* atlas = this->getAtlas(expectedMaskFormat);
   *     auto errorCode = atlas->addToAtlas(fRecorder,
   *                                        width,
   *                                        height,
   *                                        storage.get(),
   *                                        &glyph->fAtlasLocator);
   *
   *     if (errorCode == DrawAtlas::ErrorCode::kSucceeded) {
   *         glyph->fAtlasLocator.insetSrc(srcPadding);
   *     }
   *
   *     return errorCode;
   * }
   * ```
   */
  public fun addGlyphToAtlas(
    skGlyph: SkGlyph,
    glyph: Glyph?,
    srcPadding: Int,
  ): Int {
    TODO("Implement addGlyphToAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextAtlasManager::addGlyphToBulkAndSetUseToken(BulkUsePlotUpdater* updater,
   *                                                     MaskFormat format,
   *                                                     Glyph* glyph,
   *                                                     Token token) {
   *     SkASSERT(glyph);
   *     if (updater->add(glyph->fAtlasLocator)) {
   *         this->getAtlas(format)->setLastUseToken(glyph->fAtlasLocator, token);
   *     }
   * }
   * ```
   */
  public fun addGlyphToBulkAndSetUseToken(
    updater: BulkUsePlotUpdater?,
    format: MaskFormat,
    glyph: Glyph?,
    token: Token,
  ) {
    TODO("Implement addGlyphToBulkAndSetUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * void setUseTokenBulk(const BulkUsePlotUpdater& updater,
   *                          Token token,
   *                          MaskFormat format) {
   *         this->getAtlas(format)->setLastUseTokenBulk(updater, token);
   *     }
   * ```
   */
  public fun setUseTokenBulk(
    updater: BulkUsePlotUpdater,
    token: Token,
    format: MaskFormat,
  ) {
    TODO("Implement setUseTokenBulk")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextAtlasManager::recordUploads(DrawContext* dc) {
   *     for (int i = 0; i < skgpu::kMaskFormatCount; i++) {
   *         if (fAtlases[i] && !fAtlases[i]->recordUploads(dc, fRecorder)) {
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun recordUploads(dc: DrawContext?): Boolean {
    TODO("Implement recordUploads")
  }

  /**
   * C++ original:
   * ```cpp
   * void evictAtlases() {
   *         for (int i = 0; i < kMaskFormatCount; ++i) {
   *             if (fAtlases[i]) {
   *                 fAtlases[i]->evictAllPlots();
   *             }
   *         }
   *     }
   * ```
   */
  public fun evictAtlases() {
    TODO("Implement evictAtlases")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextAtlasManager::compact() {
   *     auto tokenTracker = fRecorder->priv().tokenTracker();
   *     for (int i = 0; i < kMaskFormatCount; ++i) {
   *         if (fAtlases[i]) {
   *             fAtlases[i]->compact(tokenTracker->nextFlushToken());
   *         }
   *     }
   * }
   * ```
   */
  public fun compact() {
    TODO("Implement compact")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t atlasGeneration(skgpu::MaskFormat format) const {
   *         return this->getAtlas(format)->atlasGeneration();
   *     }
   * ```
   */
  public fun atlasGeneration(format: MaskFormat): Int {
    TODO("Implement atlasGeneration")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextAtlasManager::setAtlasDimensionsToMinimum_ForTesting() {
   *     // Delete any old atlases.
   *     // This should be safe to do as long as we are not in the middle of a flush.
   *     for (int i = 0; i < skgpu::kMaskFormatCount; i++) {
   *         fAtlases[i] = nullptr;
   *     }
   *
   *     // Set all the atlas sizes to 1x1 plot each.
   *     new (&fAtlasConfig) DrawAtlasConfig{2048, 0};
   * }
   * ```
   */
  public fun setAtlasDimensionsToMinimumForTesting() {
    TODO("Implement setAtlasDimensionsToMinimumForTesting")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMaxPages_TestingOnly(uint32_t maxPages)
   * ```
   */
  public fun setMaxPagesTestingOnly(maxPages: UInt) {
    TODO("Implement setMaxPagesTestingOnly")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextAtlasManager::initAtlas(MaskFormat format) {
   *     int index = MaskFormatToAtlasIndex(format);
   *     if (fAtlases[index] == nullptr) {
   *         SkColorType colorType = MaskFormatToColorType(format);
   *         SkISize atlasDimensions = fAtlasConfig.atlasDimensions(format);
   *         SkISize plotDimensions = fAtlasConfig.plotDimensions(format);
   *         fAtlases[index] = DrawAtlas::Make(colorType,
   *                                           SkColorTypeBytesPerPixel(colorType),
   *                                           atlasDimensions.width(), atlasDimensions.height(),
   *                                           plotDimensions.width(), plotDimensions.height(),
   *                                           /*generationCounter=*/this,
   *                                           fAllowMultitexturing,
   *                                           DrawAtlas::UseStorageTextures::kNo,
   *                                           /*evictor=*/nullptr,
   *                                           /*label=*/"TextAtlas");
   *         if (!fAtlases[index]) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  private fun initAtlas(format: MaskFormat): Boolean {
    TODO("Implement initAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * MaskFormat TextAtlasManager::resolveMaskFormat(MaskFormat format) const {
   *     if (MaskFormat::kA565 == format &&
   *         !fRecorder->priv().caps()->getDefaultSampledTextureInfo(kRGB_565_SkColorType,
   *                                                                 /*mipmapped=*/Mipmapped::kNo,
   *                                                                 Protected::kNo,
   *                                                                 Renderable::kNo).isValid()) {
   *         format = MaskFormat::kARGB;
   *     }
   *     return format;
   * }
   * ```
   */
  private fun resolveMaskFormat(format: MaskFormat): Int {
    TODO("Implement resolveMaskFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawAtlas* getAtlas(skgpu::MaskFormat format) const {
   *         format = this->resolveMaskFormat(format);
   *         int atlasIndex = MaskFormatToAtlasIndex(format);
   *         SkASSERT(fAtlases[atlasIndex]);
   *         return fAtlases[atlasIndex].get();
   *     }
   * ```
   */
  private fun getAtlas(format: MaskFormat): Int {
    TODO("Implement getAtlas")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static int MaskFormatToAtlasIndex(skgpu::MaskFormat format) {
     *         return static_cast<int>(format);
     *     }
     * ```
     */
    private fun maskFormatToAtlasIndex(format: MaskFormat): Int {
      TODO("Implement maskFormatToAtlasIndex")
    }

    /**
     * C++ original:
     * ```cpp
     * static skgpu::MaskFormat AtlasIndexToMaskFormat(int idx) {
     *         return static_cast<skgpu::MaskFormat>(idx);
     *     }
     * ```
     */
    private fun atlasIndexToMaskFormat(idx: Int): Int {
      TODO("Implement atlasIndexToMaskFormat")
    }
  }
}

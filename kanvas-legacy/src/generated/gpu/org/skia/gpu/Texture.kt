package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.core.SkTraceMemoryDump
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Texture : public Resource {
 * public:
 *     ~Texture() override;
 *
 *     SampleCount sampleCount() const { return fInfo.sampleCount(); }
 *     Mipmapped mipmapped() const { return fInfo.mipmapped(); }
 *
 *     SkISize dimensions() const { return fDimensions; }
 *     const TextureInfo& textureInfo() const { return fInfo; }
 *
 *     void setReleaseCallback(sk_sp<RefCntedCallback>);
 *
 *     const char* getResourceType() const override { return "Texture"; }
 *
 *     const Texture* asTexture() const override { return this; }
 *
 *     virtual bool canUploadOnHost(const UploadSource&) const { return false; }
 *
 *     // With the assumption that source.canUploadOnHost() is true, attempts to write to the
 *     // texture on the host directly. Returns `false` only if driver calls fail.
 *     virtual bool uploadDataOnHost(const UploadSource& source, const SkIRect& dstRect);
 *
 * protected:
 *     Texture(const SharedContext*,
 *             SkISize dimensions,
 *             const TextureInfo& info,
 *             bool isTransient,
 *             sk_sp<MutableTextureState> mutableState,
 *             Ownership);
 *
 *     MutableTextureState* mutableState() const;
 *
 *     void invokeReleaseProc() override;
 *
 *     void onDumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump,
 *                                 const char* dumpName) const override;
 *
 * private:
 *     SkISize fDimensions;
 *     TextureInfo fInfo;
 *     sk_sp<MutableTextureState> fMutableState;
 *     sk_sp<RefCntedCallback> fReleaseCallback;
 * }
 * ```
 */
public open class Texture public constructor(
  param0: SharedContext,
  dimensions: SkISize,
  info: TextureInfo,
  isTransient: Boolean,
  mutableState: SkSp<MutableTextureState>,
  param5: Ownership,
) : Resource() {
  /**
   * C++ original:
   * ```cpp
   * SkISize fDimensions
   * ```
   */
  private var fDimensions: Int = TODO("Initialize fDimensions")

  /**
   * C++ original:
   * ```cpp
   * TextureInfo fInfo
   * ```
   */
  private var fInfo: Int = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MutableTextureState> fMutableState
   * ```
   */
  private var fMutableState: Int = TODO("Initialize fMutableState")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<RefCntedCallback> fReleaseCallback
   * ```
   */
  private var fReleaseCallback: Int = TODO("Initialize fReleaseCallback")

  /**
   * C++ original:
   * ```cpp
   * Texture(const SharedContext*,
   *             SkISize dimensions,
   *             const TextureInfo& info,
   *             bool isTransient,
   *             sk_sp<MutableTextureState> mutableState,
   *             Ownership)
   * ```
   */
  public constructor(
    sharedContext: SharedContext?,
    dimensions: SkISize,
    info: TextureInfo,
    isTransient: Boolean,
    mutableState: SkSp<MutableTextureState>,
    ownership: Ownership,
  ) : this(TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SampleCount sampleCount() const { return fInfo.sampleCount(); }
   * ```
   */
  public fun sampleCount(): Int {
    TODO("Implement sampleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * Mipmapped mipmapped() const { return fInfo.mipmapped(); }
   * ```
   */
  public fun mipmapped(): Int {
    TODO("Implement mipmapped")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return fDimensions; }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureInfo& textureInfo() const { return fInfo; }
   * ```
   */
  public fun textureInfo(): Int {
    TODO("Implement textureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void Texture::setReleaseCallback(sk_sp<RefCntedCallback> releaseCallback) {
   *     fReleaseCallback = std::move(releaseCallback);
   * }
   * ```
   */
  public fun setReleaseCallback(releaseCallback: SkSp<RefCntedCallback>) {
    TODO("Implement setReleaseCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Texture"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * const Texture* asTexture() const override { return this; }
   * ```
   */
  public override fun asTexture(): Texture {
    TODO("Implement asTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool canUploadOnHost(const UploadSource&) const { return false; }
   * ```
   */
  public open fun canUploadOnHost(param0: UploadSource): Boolean {
    TODO("Implement canUploadOnHost")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Texture::uploadDataOnHost(const UploadSource& source, const SkIRect& dstRect) {
   *     SK_ABORT("Not implemented");
   * }
   * ```
   */
  public open fun uploadDataOnHost(source: UploadSource, dstRect: SkIRect): Boolean {
    TODO("Implement uploadDataOnHost")
  }

  /**
   * C++ original:
   * ```cpp
   * MutableTextureState* Texture::mutableState() const { return fMutableState.get(); }
   * ```
   */
  protected fun mutableState(): MutableTextureState {
    TODO("Implement mutableState")
  }

  /**
   * C++ original:
   * ```cpp
   * void Texture::invokeReleaseProc() {
   *     if (fReleaseCallback) {
   *         // Depending on the ref count of fReleaseCallback this may or may not actually trigger
   *         // the ReleaseProc to be called.
   *         fReleaseCallback.reset();
   *     }
   * }
   * ```
   */
  protected override fun invokeReleaseProc() {
    TODO("Implement invokeReleaseProc")
  }

  /**
   * C++ original:
   * ```cpp
   * void Texture::onDumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump,
   *                                      const char* dumpName) const {
   *     SkString dimensionsStr;
   *     dimensionsStr.printf("(%dx%d)", fDimensions.width(), fDimensions.height());
   *     traceMemoryDump->dumpStringValue(dumpName, "dimensions", dimensionsStr.c_str());
   *     traceMemoryDump->dumpStringValue(dumpName, "textureInfo", fInfo.toString().c_str());
   * }
   * ```
   */
  protected override fun onDumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?, dumpName: String?) {
    TODO("Implement onDumpMemoryStatistics")
  }
}

package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.gpu.SkStrikeServerImpl

/**
 * C++ original:
 * ```cpp
 * class SkStrikeServer {
 * public:
 *     // An interface used by the server to create handles for pinning SkStrike
 *     // entries on the remote client.
 *     class DiscardableHandleManager {
 *     public:
 *         SK_SPI virtual ~DiscardableHandleManager() = default;
 *
 *         // Creates a new *locked* handle and returns a unique ID that can be used to identify
 *         // it on the remote client.
 *         SK_SPI virtual SkDiscardableHandleId createHandle() = 0;
 *
 *         // Returns true if the handle could be successfully locked. The server can
 *         // assume it will remain locked until the next set of serialized entries is
 *         // pulled from the SkStrikeServer.
 *         // If returns false, the cache entry mapped to the handle has been deleted
 *         // on the client. Any subsequent attempts to lock the same handle are not
 *         // allowed.
 *         SK_SPI virtual bool lockHandle(SkDiscardableHandleId) = 0;
 *
 *         // Returns true if a handle has been deleted on the remote client. It is
 *         // invalid to use a handle id again with this manager once this returns true.
 *         SK_SPI virtual bool isHandleDeleted(SkDiscardableHandleId) = 0;
 *     };
 *
 *     SK_SPI explicit SkStrikeServer(DiscardableHandleManager* discardableHandleManager);
 *     SK_SPI ~SkStrikeServer();
 *
 *     // Create an analysis SkCanvas used to populate the SkStrikeServer with ops
 *     // which will be serialized and rendered using the SkStrikeClient.
 *     SK_API std::unique_ptr<SkCanvas> makeAnalysisCanvas(int width, int height,
 *                                                         const SkSurfaceProps& props,
 *                                                         sk_sp<SkColorSpace> colorSpace,
 *                                                         bool DFTSupport,
 *                                                         bool DFTPerspSupport = true);
 *
 *     // Serializes the strike data captured using a canvas returned by ::makeAnalysisCanvas. Any
 *     // handles locked using the DiscardableHandleManager will be assumed to be
 *     // unlocked after this call.
 *     SK_SPI void writeStrikeData(std::vector<uint8_t>* memory);
 *
 *     // Testing helpers
 *     void setMaxEntriesInDescriptorMapForTesting(size_t count);
 *     size_t remoteStrikeMapSizeForTesting() const;
 *
 * private:
 *     SkStrikeServerImpl* impl();
 *
 *     std::unique_ptr<SkStrikeServerImpl> fImpl;
 * }
 * ```
 */
public data class SkStrikeServer public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStrikeServerImpl> fImpl
   * ```
   */
  private var fImpl: SkStrikeServerImpl?,
) {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkCanvas> SkStrikeServer::makeAnalysisCanvas(int width, int height,
   *                                                              const SkSurfaceProps& props,
   *                                                              sk_sp<SkColorSpace> colorSpace,
   *                                                              bool DFTSupport,
   *                                                              bool DFTPerspSupport) {
   * #if !defined(SK_DISABLE_SDF_TEXT)
   *     // These are copied from the defaults in GrContextOptions for historical reasons.
   *     // TODO(herb, jvanverth) pipe in parameters that can be used for both Ganesh and Graphite
   *     // backends instead of just using the defaults.
   *     constexpr float kMinDistanceFieldFontSize = 18.f;
   *
   * #if defined(SK_BUILD_FOR_ANDROID)
   *     constexpr float kGlyphsAsPathsFontSize = 384.f;
   * #elif defined(SK_BUILD_FOR_MAC)
   *     constexpr float kGlyphsAsPathsFontSize = 256.f;
   * #else
   *     constexpr float kGlyphsAsPathsFontSize = 324.f;
   * #endif
   *     // There is no need to set forcePathAA for the remote glyph cache as that control impacts
   *     // *how* the glyphs are rendered as paths, not *when* they are rendered as paths.
   *     auto control = sktext::gpu::SubRunControl{DFTSupport,
   *                                             props.isUseDeviceIndependentFonts(),
   *                                             DFTPerspSupport,
   *                                             kMinDistanceFieldFontSize,
   *                                             kGlyphsAsPathsFontSize};
   * #else
   *     auto control = sktext::gpu::SubRunControl{};
   * #endif
   *
   *     sk_sp<SkDevice> trackingDevice = sk_make_sp<GlyphTrackingDevice>(
   *             SkISize::Make(width, height),
   *             props, this->impl(),
   *             std::move(colorSpace),
   *             control);
   *     return std::make_unique<SkCanvas>(std::move(trackingDevice));
   * }
   * ```
   */
  public fun makeAnalysisCanvas(
    width: Int,
    height: Int,
    props: SkSurfaceProps,
    colorSpace: SkSp<SkColorSpace>,
    dFTSupport: Boolean,
    dFTPerspSupport: Boolean = TODO(),
  ): SkCanvas? {
    TODO("Implement makeAnalysisCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_SPI void writeStrikeData(std::vector<uint8_t>* memory)
   * ```
   */
  public fun writeStrikeData(memory: List<UByte>?): Int {
    TODO("Implement writeStrikeData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeServer::setMaxEntriesInDescriptorMapForTesting(size_t count) {
   *     fImpl->setMaxEntriesInDescriptorMapForTesting(count);
   * }
   * ```
   */
  public fun setMaxEntriesInDescriptorMapForTesting(count: ULong) {
    TODO("Implement setMaxEntriesInDescriptorMapForTesting")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkStrikeServer::remoteStrikeMapSizeForTesting() const {
   *     return fImpl->remoteStrikeMapSizeForTesting();
   * }
   * ```
   */
  public fun remoteStrikeMapSizeForTesting(): ULong {
    TODO("Implement remoteStrikeMapSizeForTesting")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikeServerImpl* SkStrikeServer::impl() { return fImpl.get(); }
   * ```
   */
  private fun `impl`(): SkStrikeServerImpl {
    TODO("Implement impl")
  }

  public abstract class DiscardableHandleManager {
    public var skDiscardableHandleId: Int = TODO("Initialize skDiscardableHandleId")

    public abstract fun lockHandle(param0: SkDiscardableHandleId): Int

    public abstract fun isHandleDeleted(param0: SkDiscardableHandleId): Int
  }
}

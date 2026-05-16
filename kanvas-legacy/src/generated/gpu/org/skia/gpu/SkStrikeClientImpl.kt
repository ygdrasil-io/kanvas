package org.skia.gpu

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkStrikeCache
import org.skia.core.SkTypefaceProxyPrototype
import org.skia.foundation.SkAutoDescriptor
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkTypefaceID
import org.skia.math.SkRect
import org.skia.utils.SkStrikeClient

/**
 * C++ original:
 * ```cpp
 * class SkStrikeClientImpl {
 * public:
 *     explicit SkStrikeClientImpl(sk_sp<SkStrikeClient::DiscardableHandleManager>,
 *                                 bool isLogging = true,
 *                                 SkStrikeCache* strikeCache = nullptr);
 *
 *     bool readStrikeData(const volatile void* memory, size_t memorySize);
 *     bool translateTypefaceID(SkAutoDescriptor* descriptor) const;
 *     sk_sp<SkTypeface> retrieveTypefaceUsingServerID(SkTypefaceID) const;
 *
 * private:
 *     class PictureBackedGlyphDrawable final : public SkDrawable {
 *     public:
 *         PictureBackedGlyphDrawable(sk_sp<SkPicture> self) : fSelf(std::move(self)) {}
 *     private:
 *         sk_sp<SkPicture> fSelf;
 *         SkRect onGetBounds() override { return fSelf->cullRect();  }
 *         size_t onApproximateBytesUsed() override {
 *             return sizeof(PictureBackedGlyphDrawable) + fSelf->approximateBytesUsed();
 *         }
 *         void onDraw(SkCanvas* canvas) override { canvas->drawPicture(fSelf); }
 *     };
 *
 *     sk_sp<SkTypeface> addTypeface(const SkTypefaceProxyPrototype& typefaceProto);
 *
 *     THashMap<SkTypefaceID, sk_sp<SkTypeface>> fServerTypefaceIdToTypeface;
 *     sk_sp<SkStrikeClient::DiscardableHandleManager> fDiscardableHandleManager;
 *     SkStrikeCache* const fStrikeCache;
 *     const bool fIsLogging;
 * }
 * ```
 */
public data class SkStrikeClientImpl public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrikeClient::DiscardableHandleManager> fDiscardableHandleManager
   * ```
   */
  private var fDiscardableHandleManager: SkSp<SkStrikeClient.DiscardableHandleManager>,
  /**
   * C++ original:
   * ```cpp
   * SkStrikeCache* const fStrikeCache
   * ```
   */
  private val fStrikeCache: SkStrikeCache?,
  /**
   * C++ original:
   * ```cpp
   * const bool fIsLogging
   * ```
   */
  private val fIsLogging: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkStrikeClientImpl::readStrikeData(const volatile void* memory, size_t memorySize) {
   *     SkASSERT(memorySize != 0);
   *     SkASSERT(memory != nullptr);
   *
   *     // We do not need to set any SkDeserialProcs here because SkStrikeServerImpl::writeStrikeData
   *     // did not encode any SkImages.
   *     SkReadBuffer buffer{const_cast<const void*>(memory), memorySize};
   *     // Limit the kinds of effects that appear in a glyph's drawable (crbug.com/1442140):
   *     buffer.setAllowSkSL(false);
   *
   *     int curTypeface = 0,
   *         curStrike = 0;
   *
   *     auto postError = [&](int line) {
   *         SkDebugf("Read Error Posted %s : %d", __FILE__, line);
   *         SkStrikeClient::DiscardableHandleManager::ReadFailureData data{
   *                 memorySize,
   *                 buffer.offset(),
   *                 SkTo<uint64_t>(curTypeface),
   *                 SkTo<uint64_t>(curStrike),
   *                 SkTo<uint64_t>(0),
   *                 SkTo<uint64_t>(0)};
   *         fDiscardableHandleManager->notifyReadFailure(data);
   *     };
   *
   *     // Read the number of typefaces sent.
   *     const int typefaceCount = buffer.readInt();
   *     for (curTypeface = 0; curTypeface < typefaceCount; ++curTypeface) {
   *         auto proto = SkTypefaceProxyPrototype::MakeFromBuffer(buffer);
   *         if (proto) {
   *             this->addTypeface(proto.value());
   *         } else {
   *             postError(__LINE__);
   *             return false;
   *         }
   *     }
   *
   *     // Read the number of strikes sent.
   *     const int stirkeCount = buffer.readInt();
   *     for (curStrike = 0; curStrike < stirkeCount; ++curStrike) {
   *
   *         const SkTypefaceID serverTypefaceID = buffer.readUInt();
   *         if (serverTypefaceID == 0 && !buffer.isValid()) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *
   *         const SkDiscardableHandleId discardableHandleID = buffer.readUInt();
   *         if (discardableHandleID == 0 && !buffer.isValid()) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *
   *         std::optional<SkAutoDescriptor> serverDescriptor = SkAutoDescriptor::MakeFromBuffer(buffer);
   *         if (!buffer.validate(serverDescriptor.has_value())) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *
   *         const bool fontMetricsInitialized = buffer.readBool();
   *         if (!fontMetricsInitialized && !buffer.isValid()) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *
   *         std::optional<SkFontMetrics> fontMetrics;
   *         if (!fontMetricsInitialized) {
   *             fontMetrics = SkFontMetricsPriv::MakeFromBuffer(buffer);
   *             if (!fontMetrics || !buffer.isValid()) {
   *                 postError(__LINE__);
   *                 return false;
   *             }
   *         }
   *
   *         auto* clientTypeface = fServerTypefaceIdToTypeface.find(serverTypefaceID);
   *         if (clientTypeface == nullptr) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *
   *         if (!this->translateTypefaceID(&serverDescriptor.value())) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *
   *         SkDescriptor* clientDescriptor = serverDescriptor->getDesc();
   *         auto strike = fStrikeCache->findStrike(*clientDescriptor);
   *
   *         if (strike == nullptr) {
   *             // Metrics are only sent the first time. If creating a new strike, then the metrics
   *             // are not initialized.
   *             if (fontMetricsInitialized) {
   *                 postError(__LINE__);
   *                 return false;
   *             }
   *             SkStrikeSpec strikeSpec{*clientDescriptor, *clientTypeface};
   *             strike = fStrikeCache->createStrike(
   *                     strikeSpec, &fontMetrics.value(),
   *                     std::make_unique<DiscardableStrikePinner>(
   *                             discardableHandleID, fDiscardableHandleManager));
   *         }
   *
   *         // Make sure this strike is pinned on the GPU side.
   *         strike->verifyPinnedStrike();
   *
   *         if (!strike->mergeFromBuffer(buffer)) {
   *             postError(__LINE__);
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun readStrikeData(memory: Unit?, memorySize: ULong): Boolean {
    TODO("Implement readStrikeData")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrikeClientImpl::translateTypefaceID(SkAutoDescriptor* toChange) const {
   *     SkDescriptor& descriptor = *toChange->getDesc();
   *
   *     // Rewrite the typefaceID in the rec.
   *     {
   *         uint32_t size;
   *         // findEntry returns a const void*, remove the const in order to update in place.
   *         void* ptr = const_cast<void *>(descriptor.findEntry(kRec_SkDescriptorTag, &size));
   *         SkScalerContextRec rec;
   *         if (!ptr || size != sizeof(rec)) { return false; }
   *         std::memcpy((void*)&rec, ptr, size);
   *         // Get the local typeface from remote typefaceID.
   *         auto* tfPtr = fServerTypefaceIdToTypeface.find(rec.fTypefaceID);
   *         // Received a strike for a typeface which doesn't exist.
   *         if (!tfPtr) { return false; }
   *         // Update the typeface id to work with the client side.
   *         rec.fTypefaceID = tfPtr->get()->uniqueID();
   *         std::memcpy(ptr, &rec, size);
   *     }
   *
   *     descriptor.computeChecksum();
   *
   *     return true;
   * }
   * ```
   */
  public fun translateTypefaceID(descriptor: SkAutoDescriptor?): Boolean {
    TODO("Implement translateTypefaceID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkStrikeClientImpl::retrieveTypefaceUsingServerID(SkTypefaceID typefaceID) const {
   *     auto* tfPtr = fServerTypefaceIdToTypeface.find(typefaceID);
   *     return tfPtr != nullptr ? *tfPtr : nullptr;
   * }
   * ```
   */
  public fun retrieveTypefaceUsingServerID(typefaceID: SkTypefaceID): SkSp<SkTypeface> {
    TODO("Implement retrieveTypefaceUsingServerID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkStrikeClientImpl::addTypeface(const SkTypefaceProxyPrototype& typefaceProto) {
   *     sk_sp<SkTypeface>* typeface =
   *             fServerTypefaceIdToTypeface.find(typefaceProto.serverTypefaceID());
   *
   *     // We already have the typeface.
   *     if (typeface != nullptr)  {
   *         return *typeface;
   *     }
   *
   *     auto newTypeface = sk_make_sp<SkTypefaceProxy>(
   *             typefaceProto, fDiscardableHandleManager, fIsLogging);
   *     fServerTypefaceIdToTypeface.set(typefaceProto.serverTypefaceID(), newTypeface);
   *     return newTypeface;
   * }
   * ```
   */
  private fun addTypeface(typefaceProto: SkTypefaceProxyPrototype): SkSp<SkTypeface> {
    TODO("Implement addTypeface")
  }

  public class PictureBackedGlyphDrawable public constructor(
    self: SkSp<SkPicture>,
  ) : SkDrawable() {
    private var fSelf: SkSp<SkPicture> = TODO("Initialize fSelf")

    public override fun onGetBounds(): SkRect {
      TODO("Implement onGetBounds")
    }

    public override fun onApproximateBytesUsed(): ULong {
      TODO("Implement onApproximateBytesUsed")
    }

    public override fun onDraw(canvas: SkCanvas?) {
      TODO("Implement onDraw")
    }
  }
}

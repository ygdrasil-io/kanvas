package org.skia.tools

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkPathOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontDescriptor
import org.skia.foundation.SkSp
import org.skia.foundation.SkStreamAsset
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class PlanetTypeface : public TestSVGTypeface {
 *     using TestSVGTypeface::TestSVGTypeface;
 *
 *     bool getPathOp(SkColor color, SkPathOp* op) const override {
 *         *op = SkPathOp::kUnion_SkPathOp;
 *         return true;
 *     }
 *
 *     static constexpr SkTypeface::FactoryId FactoryId = SkSetFourByteTag('p','s','v','g');
 *     static constexpr const char gHeaderString[] = "SkTestSVGTypefacePlanet01";
 *     static constexpr const size_t kHeaderSize = sizeof(gHeaderString);
 *
 *     std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override {
 *         SkDynamicMemoryWStream wstream;
 *         wstream.write(gHeaderString, kHeaderSize);
 *         return wstream.detachAsStream();
 *     }
 *
 *     static sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset> stream,
 *                                             const SkFontArguments&) {
 *         char header[kHeaderSize];
 *         if (stream->read(header, kHeaderSize) != kHeaderSize ||
 *             0 != memcmp(header, gHeaderString, kHeaderSize))
 *         {
 *             return nullptr;
 *         }
 *         return TestSVGTypeface::Planets();
 *     }
 *
 *     void onGetFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const override {
 *         TestSVGTypeface::onGetFontDescriptor(desc, isLocal);
 *         desc->setFactoryId(FactoryId);
 *     }
 * public:
 *     struct Register { Register() { SkTypeface::Register(FactoryId, &MakeFromStream); } };
 * }
 * ```
 */
public open class PlanetTypeface : TestSVGTypeface() {
  /**
   * C++ original:
   * ```cpp
   * bool getPathOp(SkColor color, SkPathOp* op) const override {
   *         *op = SkPathOp::kUnion_SkPathOp;
   *         return true;
   *     }
   * ```
   */
  public override fun getPathOp(color: SkColor, op: SkPathOp?): Boolean {
    TODO("Implement getPathOp")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override {
   *         SkDynamicMemoryWStream wstream;
   *         wstream.write(gHeaderString, kHeaderSize);
   *         return wstream.detachAsStream();
   *     }
   * ```
   */
  public override fun onOpenStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const override {
   *         TestSVGTypeface::onGetFontDescriptor(desc, isLocal);
   *         desc->setFactoryId(FactoryId);
   *     }
   * ```
   */
  public override fun onGetFontDescriptor(desc: SkFontDescriptor?, isLocal: Boolean?) {
    TODO("Implement onGetFontDescriptor")
  }

  public open class Register public constructor()

  public companion object {
    private val factoryId: Int = TODO("Initialize factoryId")

    private val gHeaderString: CharArray = TODO("Initialize gHeaderString")

    private val kHeaderSize: ULong = TODO("Initialize kHeaderSize")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset> stream,
     *                                             const SkFontArguments&) {
     *         char header[kHeaderSize];
     *         if (stream->read(header, kHeaderSize) != kHeaderSize ||
     *             0 != memcmp(header, gHeaderString, kHeaderSize))
     *         {
     *             return nullptr;
     *         }
     *         return TestSVGTypeface::Planets();
     *     }
     * ```
     */
    private fun makeFromStream(stream: SkStreamAsset?, param1: SkFontArguments): SkSp<SkTypeface> {
      TODO("Implement makeFromStream")
    }
  }
}

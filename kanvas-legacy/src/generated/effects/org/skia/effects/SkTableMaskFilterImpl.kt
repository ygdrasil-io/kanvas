package org.skia.effects

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import org.skia.core.SkMaskFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SkTableMaskFilterImpl : public SkMaskFilterBase {
 * public:
 *     explicit SkTableMaskFilterImpl(const uint8_t table[256]);
 *
 *     SkMask::Format getFormat() const override;
 *     bool filterMask(SkMaskBuilder*, const SkMask&, const SkMatrix&, SkIPoint*) const override;
 *     SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kTable; }
 *     std::pair<sk_sp<SkImageFilter>, bool> asImageFilter(const SkMatrix&,
 *                                                         const SkPaint&) const override;
 *
 * protected:
 *     ~SkTableMaskFilterImpl() override;
 *
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkTableMaskFilterImpl)
 *
 *     SkTableMaskFilterImpl();
 *
 *     uint8_t fTable[256];
 *
 *     using INHERITED = SkMaskFilter;
 *
 *     friend class SkTableMaskFilter;
 * }
 * ```
 */
public open class SkTableMaskFilterImpl public constructor(
  table: Array<UByte>,
) : SkMaskFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * uint8_t fTable[256]
   * ```
   */
  private var fTable: Array<UByte> = TODO("Initialize fTable")

  /**
   * C++ original:
   * ```cpp
   * SkTableMaskFilterImpl::SkTableMaskFilterImpl(const uint8_t table[256]) {
   *     memcpy(fTable, table, sizeof(fTable));
   * }
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format SkTableMaskFilterImpl::getFormat() const {
   *     return SkMask::kA8_Format;
   * }
   * ```
   */
  public override fun getFormat(): SkMask.Format {
    TODO("Implement getFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTableMaskFilterImpl::filterMask(SkMaskBuilder* dst, const SkMask& src,
   *                                        const SkMatrix&, SkIPoint* margin) const {
   *     if (src.fFormat != SkMask::kA8_Format) {
   *         return false;
   *     }
   *
   *     dst->bounds() = src.fBounds;
   *     dst->rowBytes() = SkAlign4(dst->fBounds.width());
   *     dst->format() = SkMask::kA8_Format;
   *     dst->image() = nullptr;
   *
   *     if (src.fImage) {
   *         dst->image() = SkMaskBuilder::AllocImage(dst->computeImageSize());
   *
   *         const uint8_t* srcP = src.fImage;
   *         uint8_t* dstP = dst->image();
   *         const uint8_t* table = fTable;
   *         int dstWidth = dst->fBounds.width();
   *         int extraZeros = dst->fRowBytes - dstWidth;
   *
   *         for (int y = dst->fBounds.height() - 1; y >= 0; --y) {
   *             for (int x = dstWidth - 1; x >= 0; --x) {
   *                 dstP[x] = table[srcP[x]];
   *             }
   *             srcP += src.fRowBytes;
   *             // we can't just inc dstP by rowbytes, because if it has any
   *             // padding between its width and its rowbytes, we need to zero those
   *             // so that the bitters can read those safely if that is faster for
   *             // them
   *             dstP += dstWidth;
   *             for (int i = extraZeros - 1; i >= 0; --i) {
   *                 *dstP++ = 0;
   *             }
   *         }
   *     }
   *
   *     if (margin) {
   *         margin->set(0, 0);
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun filterMask(
    dst: SkMaskBuilder?,
    src: SkMask,
    param2: SkMatrix,
    margin: SkIPoint?,
  ): Boolean {
    TODO("Implement filterMask")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kTable; }
   * ```
   */
  public override fun type(): SkMaskFilterBase.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkImageFilter>, bool> SkTableMaskFilterImpl::asImageFilter(const SkMatrix&,
   *                                                                            const SkPaint&) const {
   *     sk_sp<SkColorFilter> colorFilter = SkColorFilters::TableARGB(fTable,
   *                                                                  nullptr,
   *                                                                  nullptr,
   *                                                                  nullptr);
   *     return std::make_pair(SkImageFilters::ColorFilter(colorFilter, nullptr), false);
   * }
   * ```
   */
  public override fun asImageFilter(param0: SkMatrix, param1: SkPaint): Int {
    TODO("Implement asImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTableMaskFilterImpl::flatten(SkWriteBuffer& wb) const {
   *     wb.writeByteArray(fTable, 256);
   * }
   * ```
   */
  protected override fun flatten(wb: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkTableMaskFilterImpl::CreateProc(SkReadBuffer& buffer) {
   *     uint8_t table[256];
   *     if (!buffer.readByteArray(table, 256)) {
   *         return nullptr;
   *     }
   *     return sk_sp<SkFlattenable>(SkTableMaskFilter::Create(table));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}

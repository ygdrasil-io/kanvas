package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkScalar
import undefined.ContentChangeMode

/**
 * C++ original:
 * ```cpp
 * class SkNullSurface : public SkSurface_Base {
 * public:
 *     SkNullSurface(int width, int height) : SkSurface_Base(width, height, nullptr) {}
 *
 *     // From SkSurface_Base.h
 *     SkSurface_Base::Type type() const override { return SkSurface_Base::Type::kNull; }
 *
 * protected:
 *     SkCanvas* onNewCanvas() override {
 *         return new SkNoDrawCanvas(this->width(), this->height());
 *     }
 *     sk_sp<SkSurface> onNewSurface(const SkImageInfo& info) override {
 *         return SkSurfaces::Null(info.width(), info.height());
 *     }
 *     sk_sp<SkImage> onNewImageSnapshot(const SkIRect* subsetOrNull) override { return nullptr; }
 *     void onWritePixels(const SkPixmap&, int x, int y) override {}
 *     void onDraw(SkCanvas*, SkScalar, SkScalar, const SkSamplingOptions&, const SkPaint*) override {}
 *     bool onCopyOnWrite(ContentChangeMode) override { return true; }
 *     sk_sp<const SkCapabilities> onCapabilities() override {
 *         // Not really, but we have to return *something*
 *         return SkCapabilities::RasterBackend();
 *     }
 *     SkImageInfo imageInfo() const override {
 *         return SkImageInfo::MakeUnknown(this->width(), this->height());
 *     }
 * }
 * ```
 */
public open class SkNullSurface public constructor(
  width: Int,
  height: Int,
) : SkSurfaceBase(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkSurface_Base::Type type() const override { return SkSurface_Base::Type::kNull; }
   * ```
   */
  public override fun type(): SkSurface_Base.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* onNewCanvas() override {
   *         return new SkNoDrawCanvas(this->width(), this->height());
   *     }
   * ```
   */
  protected override fun onNewCanvas(): SkCanvas {
    TODO("Implement onNewCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> onNewSurface(const SkImageInfo& info) override {
   *         return SkSurfaces::Null(info.width(), info.height());
   *     }
   * ```
   */
  protected override fun onNewSurface(info: SkImageInfo): SkSp<SkSurface> {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> onNewImageSnapshot(const SkIRect* subsetOrNull) override { return nullptr; }
   * ```
   */
  protected override fun onNewImageSnapshot(subsetOrNull: SkIRect?): SkSp<SkImage> {
    TODO("Implement onNewImageSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * void onWritePixels(const SkPixmap&, int x, int y) override {}
   * ```
   */
  protected override fun onWritePixels(
    param0: SkPixmap,
    x: Int,
    y: Int,
  ) {
    TODO("Implement onWritePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas*, SkScalar, SkScalar, const SkSamplingOptions&, const SkPaint*) override {}
   * ```
   */
  protected override fun onDraw(
    param0: SkCanvas?,
    param1: SkScalar,
    param2: SkScalar,
    param3: SkSamplingOptions,
    param4: SkPaint?,
  ) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onCopyOnWrite(ContentChangeMode) override { return true; }
   * ```
   */
  protected override fun onCopyOnWrite(param0: ContentChangeMode): Boolean {
    TODO("Implement onCopyOnWrite")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkCapabilities> onCapabilities() override {
   *         // Not really, but we have to return *something*
   *         return SkCapabilities::RasterBackend();
   *     }
   * ```
   */
  protected override fun onCapabilities(): SkSp<SkCapabilities> {
    TODO("Implement onCapabilities")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo imageInfo() const override {
   *         return SkImageInfo::MakeUnknown(this->width(), this->height());
   *     }
   * ```
   */
  protected override fun imageInfo(): SkImageInfo {
    TODO("Implement imageInfo")
  }
}

package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class Via : public Sink {
 * public:
 *     explicit Via(Sink* sink) : fSink(sink) {}
 *     const char* fileExtension() const override { return fSink->fileExtension(); }
 *     bool               serial() const override { return fSink->serial(); }
 *     SinkFlags flags() const override {
 *         SinkFlags flags = fSink->flags();
 *         flags.approach = SinkFlags::kIndirect;
 *         return flags;
 *     }
 *     void setColorSpace(sk_sp<SkColorSpace> colorSpace) override {
 *         fSink->setColorSpace(colorSpace);
 *     }
 * protected:
 *     std::unique_ptr<Sink> fSink;
 * }
 * ```
 */
public open class Via public constructor(
  sink: Sink?,
) : Sink() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Sink> fSink
   * ```
   */
  protected var fSink: Int = TODO("Initialize fSink")

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override { return fSink->fileExtension(); }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }

  /**
   * C++ original:
   * ```cpp
   * bool               serial() const override { return fSink->serial(); }
   * ```
   */
  public override fun serial(): Boolean {
    TODO("Implement serial")
  }

  /**
   * C++ original:
   * ```cpp
   * SinkFlags flags() const override {
   *         SinkFlags flags = fSink->flags();
   *         flags.approach = SinkFlags::kIndirect;
   *         return flags;
   *     }
   * ```
   */
  public override fun flags(): SinkFlags {
    TODO("Implement flags")
  }

  /**
   * C++ original:
   * ```cpp
   * void setColorSpace(sk_sp<SkColorSpace> colorSpace) override {
   *         fSink->setColorSpace(colorSpace);
   *     }
   * ```
   */
  public override fun setColorSpace(colorSpace: SkSp<SkColorSpace>) {
    TODO("Implement setColorSpace")
  }
}

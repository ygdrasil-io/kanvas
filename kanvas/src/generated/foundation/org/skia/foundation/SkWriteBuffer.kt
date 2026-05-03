package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkData
import org.skia.core.SkImage
import org.skia.core.SkM44
import org.skia.core.SkPaint
import org.skia.core.SkPath
import org.skia.core.SkRegion
import org.skia.core.SkSamplingOptions
import org.skia.core.SkStream
import org.skia.core.SkTypeface
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkWriteBuffer {
 * public:
 *     explicit SkWriteBuffer(const SkSerialProcs& p) : fProcs(p) {}
 *     virtual ~SkWriteBuffer() {}
 *
 *     virtual void writePad32(const void* buffer, size_t bytes) = 0;
 *
 *     virtual void writeByteArray(const void* data, size_t size) = 0;
 *     void writeDataAsByteArray(const SkData* data) {
 *         if (!data) {
 *             this->write32(0);
 *         } else {
 *             this->writeByteArray(data->data(), data->size());
 *         }
 *     }
 *
 *     virtual void writeBool(bool value) = 0;
 *     virtual void writeScalar(SkScalar value) = 0;
 *     virtual void writeScalarArray(SkSpan<const SkScalar>) = 0;
 *     virtual void writeInt(int32_t value) = 0;
 *     virtual void writeIntArray(SkSpan<const int32_t>) = 0;
 *     virtual void writeUInt(uint32_t value) = 0;
 *     void write32(int32_t value) {
 *         this->writeInt(value);
 *     }
 *     virtual void writeString(std::string_view value) = 0;
 *
 *     virtual void writeFlattenable(const SkFlattenable* flattenable) = 0;
 *     virtual void writeColor(SkColor color) = 0;
 *     virtual void writeColorArray(SkSpan<const SkColor>) = 0;
 *     virtual void writeColor4f(const SkColor4f& color) = 0;
 *     virtual void writeColor4fArray(SkSpan<const SkColor4f>) = 0;
 *     virtual void writePoint(const SkPoint& point) = 0;
 *     virtual void writePointArray(SkSpan<const SkPoint>) = 0;
 *     virtual void writePoint3(const SkPoint3& point) = 0;
 *     virtual void write(const SkM44&) = 0;
 *     virtual void writeMatrix(const SkMatrix& matrix) = 0;
 *     virtual void writeIRect(const SkIRect& rect) = 0;
 *     virtual void writeRect(const SkRect& rect) = 0;
 *     virtual void writeRegion(const SkRegion& region) = 0;
 *     virtual void writeSampling(const SkSamplingOptions&) = 0;
 *     virtual void writePath(const SkPath& path) = 0;
 *     virtual size_t writeStream(SkStream* stream, size_t length) = 0;
 *     virtual void writeImage(const SkImage*) = 0;
 *     virtual void writeTypeface(SkTypeface* typeface) = 0;
 *     virtual void writePaint(const SkPaint& paint) = 0;
 *
 *     const SkSerialProcs& serialProcs() const { return fProcs; }
 *
 * protected:
 *     SkSerialProcs   fProcs;
 * }
 * ```
 */
public abstract class SkWriteBuffer public constructor(
  p: SkSerialProcs,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSerialProcs   fProcs
   * ```
   */
  protected var fProcs: SkSerialProcs = TODO("Initialize fProcs")

  /**
   * C++ original:
   * ```cpp
   * virtual void writePad32(const void* buffer, size_t bytes) = 0
   * ```
   */
  public abstract fun writePad32(buffer: Unit?, bytes: ULong)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeByteArray(const void* data, size_t size) = 0
   * ```
   */
  public abstract fun writeByteArray(`data`: Unit?, size: ULong)

  /**
   * C++ original:
   * ```cpp
   * void writeDataAsByteArray(const SkData* data) {
   *         if (!data) {
   *             this->write32(0);
   *         } else {
   *             this->writeByteArray(data->data(), data->size());
   *         }
   *     }
   * ```
   */
  public fun writeDataAsByteArray(`data`: SkData?) {
    TODO("Implement writeDataAsByteArray")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void writeBool(bool value) = 0
   * ```
   */
  public abstract fun writeBool(`value`: Boolean)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeScalar(SkScalar value) = 0
   * ```
   */
  public abstract fun writeScalar(`value`: SkScalar)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeScalarArray(SkSpan<const SkScalar>) = 0
   * ```
   */
  public abstract fun writeScalarArray(param0: SkSpan<SkScalar>)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeInt(int32_t value) = 0
   * ```
   */
  public abstract fun writeInt(`value`: Int)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeIntArray(SkSpan<const int32_t>) = 0
   * ```
   */
  public abstract fun writeIntArray(param0: SkSpan<Int>)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeUInt(uint32_t value) = 0
   * ```
   */
  public abstract fun writeUInt(`value`: UInt)

  /**
   * C++ original:
   * ```cpp
   * void write32(int32_t value) {
   *         this->writeInt(value);
   *     }
   * ```
   */
  public fun write32(`value`: Int) {
    TODO("Implement write32")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void writeString(std::string_view value) = 0
   * ```
   */
  public abstract fun writeString(`value`: String)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeFlattenable(const SkFlattenable* flattenable) = 0
   * ```
   */
  public abstract fun writeFlattenable(flattenable: SkFlattenable?)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeColor(SkColor color) = 0
   * ```
   */
  public abstract fun writeColor(color: SkColor)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeColorArray(SkSpan<const SkColor>) = 0
   * ```
   */
  public abstract fun writeColorArray(param0: SkSpan<SkColor>)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeColor4f(const SkColor4f& color) = 0
   * ```
   */
  public abstract fun writeColor4f(color: SkColor4f)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeColor4fArray(SkSpan<const SkColor4f>) = 0
   * ```
   */
  public abstract fun writeColor4fArray(param0: SkSpan<SkColor4f>)

  /**
   * C++ original:
   * ```cpp
   * virtual void writePoint(const SkPoint& point) = 0
   * ```
   */
  public abstract fun writePoint(point: SkPoint)

  /**
   * C++ original:
   * ```cpp
   * virtual void writePointArray(SkSpan<const SkPoint>) = 0
   * ```
   */
  public abstract fun writePointArray(param0: SkSpan<SkPoint>)

  /**
   * C++ original:
   * ```cpp
   * virtual void writePoint3(const SkPoint3& point) = 0
   * ```
   */
  public abstract fun writePoint3(point: SkPoint3)

  /**
   * C++ original:
   * ```cpp
   * virtual void write(const SkM44&) = 0
   * ```
   */
  public abstract fun write(param0: SkM44)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeMatrix(const SkMatrix& matrix) = 0
   * ```
   */
  public abstract fun writeMatrix(matrix: SkMatrix)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeIRect(const SkIRect& rect) = 0
   * ```
   */
  public abstract fun writeIRect(rect: SkIRect)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeRect(const SkRect& rect) = 0
   * ```
   */
  public abstract fun writeRect(rect: SkRect)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeRegion(const SkRegion& region) = 0
   * ```
   */
  public abstract fun writeRegion(region: SkRegion)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeSampling(const SkSamplingOptions&) = 0
   * ```
   */
  public abstract fun writeSampling(param0: SkSamplingOptions)

  /**
   * C++ original:
   * ```cpp
   * virtual void writePath(const SkPath& path) = 0
   * ```
   */
  public abstract fun writePath(path: SkPath)

  /**
   * C++ original:
   * ```cpp
   * virtual size_t writeStream(SkStream* stream, size_t length) = 0
   * ```
   */
  public abstract fun writeStream(stream: SkStream?, length: ULong): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void writeImage(const SkImage*) = 0
   * ```
   */
  public abstract fun writeImage(param0: SkImage?)

  /**
   * C++ original:
   * ```cpp
   * virtual void writeTypeface(SkTypeface* typeface) = 0
   * ```
   */
  public abstract fun writeTypeface(typeface: SkTypeface?)

  /**
   * C++ original:
   * ```cpp
   * virtual void writePaint(const SkPaint& paint) = 0
   * ```
   */
  public abstract fun writePaint(paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * const SkSerialProcs& serialProcs() const { return fProcs; }
   * ```
   */
  public fun serialProcs(): SkSerialProcs {
    TODO("Implement serialProcs")
  }
}

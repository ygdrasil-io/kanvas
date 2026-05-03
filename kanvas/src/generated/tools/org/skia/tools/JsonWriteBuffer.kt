package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkColor
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSpan
import org.skia.foundation.SkStream
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkWriteBuffer
import org.skia.json.SkJSONWriter
import org.skia.math.SkIRect
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class JsonWriteBuffer final : public SkWriteBuffer {
 * public:
 *     JsonWriteBuffer(SkJSONWriter* writer, UrlDataManager* urlDataManager)
 *             : SkWriteBuffer({}), fUrlDataManager(urlDataManager), fWriter(writer), fCount(0) {}
 *
 *     void writePad32(const void* buffer, size_t bytes) override;
 *     void writeByteArray(const void* data, size_t size) override;
 *     void writeBool(bool value) override;
 *     void writeScalar(SkScalar value) override;
 *     void writeScalarArray(SkSpan<const SkScalar>) override;
 *     void writeInt(int32_t value) override;
 *     void writeIntArray(SkSpan<const int32_t>) override;
 *     void writeUInt(uint32_t value) override;
 *     void writeString(std::string_view value) override;
 *
 *     void writeFlattenable(const SkFlattenable* flattenable) override;
 *     void writeColor(SkColor color) override;
 *     void writeColorArray(SkSpan<const SkColor>) override;
 *     void writeColor4f(const SkColor4f& color) override;
 *     void writeColor4fArray(SkSpan<const SkColor4f>) override;
 *     void writePoint(const SkPoint& point) override;
 *     void writePointArray(SkSpan<const SkPoint>) override;
 *     void writePoint3(const SkPoint3& point) override;
 *     void write(const SkM44&) override;
 *     void writeMatrix(const SkMatrix& matrix) override;
 *     void writeIRect(const SkIRect& rect) override;
 *     void writeRect(const SkRect& rect) override;
 *     void writeRegion(const SkRegion& region) override;
 *     void writePath(const SkPath& path) override;
 *     void writeSampling(const SkSamplingOptions&) override;
 *     size_t writeStream(SkStream* stream, size_t length) override;
 *     void writeImage(const SkImage*) override;
 *     void writeTypeface(SkTypeface* typeface) override;
 *     void writePaint(const SkPaint& paint) override;
 *
 * private:
 *     void append(const char* type);
 *
 *     UrlDataManager* fUrlDataManager;
 *     SkJSONWriter*   fWriter;
 *     int             fCount;
 * }
 * ```
 */
public class JsonWriteBuffer public constructor(
  writer: SkJSONWriter?,
  urlDataManager: UrlDataManager?,
) : SkWriteBuffer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * UrlDataManager* fUrlDataManager
   * ```
   */
  private var fUrlDataManager: UrlDataManager? = TODO("Initialize fUrlDataManager")

  /**
   * C++ original:
   * ```cpp
   * SkJSONWriter*   fWriter
   * ```
   */
  private var fWriter: SkJSONWriter? = TODO("Initialize fWriter")

  /**
   * C++ original:
   * ```cpp
   * int             fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writePad32(const void* data, size_t size) {
   *     this->append("rawBytes");
   *     fWriter->beginArray();
   *     const uint8_t* bytes = reinterpret_cast<const uint8_t*>(data);
   *     for (size_t i = 0; i < size; ++i) {
   *         SkString hexByte = SkStringPrintf("%02x", bytes[i]);
   *         fWriter->appendString(hexByte);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writePad32(buffer: Unit?, bytes: ULong) {
    TODO("Implement writePad32")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeByteArray(const void* data, size_t size) {
   *     this->append("byteArray");
   *     fWriter->beginArray();
   *     const uint8_t* bytes = reinterpret_cast<const uint8_t*>(data);
   *     for (size_t i = 0; i < size; ++i) {
   *         SkString hexByte = SkStringPrintf("%02x", bytes[i]);
   *         fWriter->appendString(hexByte);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writeByteArray(`data`: Unit?, size: ULong) {
    TODO("Implement writeByteArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeBool(bool value) {
   *     this->append("bool");
   *     fWriter->appendBool(value);
   * }
   * ```
   */
  public override fun writeBool(`value`: Boolean) {
    TODO("Implement writeBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeScalar(SkScalar value) {
   *     this->append("scalar");
   *     fWriter->appendFloat(value);
   * }
   * ```
   */
  public override fun writeScalar(`value`: SkScalar) {
    TODO("Implement writeScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeScalarArray(SkSpan<const SkScalar> values) {
   *     this->append("scalarArray");
   *     fWriter->beginArray();
   *     for (auto value : values) {
   *         fWriter->appendFloat(value);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writeScalarArray(values: SkSpan<SkScalar>) {
    TODO("Implement writeScalarArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeInt(int32_t value) {
   *     this->append("int");
   *     fWriter->appendS32(value);
   * }
   * ```
   */
  public override fun writeInt(`value`: Int) {
    TODO("Implement writeInt")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeIntArray(SkSpan<const int32_t> values) {
   *     this->append("intArray");
   *     fWriter->beginArray();
   *     for (auto value : values) {
   *         fWriter->appendS32(value);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writeIntArray(values: SkSpan<Int>) {
    TODO("Implement writeIntArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeUInt(uint32_t value) {
   *     this->append("uint");
   *     fWriter->appendU32(value);
   * }
   * ```
   */
  public override fun writeUInt(`value`: UInt) {
    TODO("Implement writeUInt")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeString(std::string_view value) override
   * ```
   */
  public override fun writeString(`value`: String) {
    TODO("Implement writeString")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeFlattenable(const SkFlattenable* flattenable) {
   *     if (flattenable) {
   *         this->append(flattenable->getTypeName());
   *         fWriter->beginObject();
   *         JsonWriteBuffer flattenableBuffer(fWriter, fUrlDataManager);
   *         flattenable->flatten(flattenableBuffer);
   *         fWriter->endObject();
   *     } else {
   *         this->append("flattenable");
   *         fWriter->appendPointer(nullptr);
   *     }
   * }
   * ```
   */
  public override fun writeFlattenable(flattenable: SkFlattenable?) {
    TODO("Implement writeFlattenable")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeColor(SkColor color) {
   *     this->append("color");
   *     DrawCommand::MakeJsonColor(*fWriter, color);
   * }
   * ```
   */
  public override fun writeColor(color: SkColor) {
    TODO("Implement writeColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeColorArray(SkSpan<const SkColor> colors) {
   *     this->append("colorArray");
   *     fWriter->beginArray();
   *     for (auto color : colors) {
   *         DrawCommand::MakeJsonColor(*fWriter, color);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writeColorArray(colors: SkSpan<SkColor>) {
    TODO("Implement writeColorArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeColor4f(const SkColor4f& color) {
   *     this->append("color");
   *     DrawCommand::MakeJsonColor4f(*fWriter, color);
   * }
   * ```
   */
  public override fun writeColor4f(color: SkColor4f) {
    TODO("Implement writeColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeColor4fArray(SkSpan<const SkColor4f> colors) {
   *     this->append("colorArray");
   *     fWriter->beginArray();
   *     for (auto color : colors) {
   *         DrawCommand::MakeJsonColor4f(*fWriter, color);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writeColor4fArray(colors: SkSpan<SkColor4f>) {
    TODO("Implement writeColor4fArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writePoint(const SkPoint& point) {
   *     this->append("point");
   *     DrawCommand::MakeJsonPoint(*fWriter, point);
   * }
   * ```
   */
  public override fun writePoint(point: SkPoint) {
    TODO("Implement writePoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writePointArray(SkSpan<const SkPoint> points) {
   *     this->append("pointArray");
   *     fWriter->beginArray();
   *     for (auto point : points) {
   *         DrawCommand::MakeJsonPoint(*fWriter, point);
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun writePointArray(points: SkSpan<SkPoint>) {
    TODO("Implement writePointArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writePoint3(const SkPoint3& point) {
   *     this->append("point3");
   *     DrawCommand::MakeJsonPoint3(*fWriter, point);
   * }
   * ```
   */
  public override fun writePoint3(point: SkPoint3) {
    TODO("Implement writePoint3")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::write(const SkM44& matrix) {
   *     this->append("matrix");
   *     fWriter->beginArray();
   *     for (int r = 0; r < 4; ++r) {
   *         fWriter->beginArray(nullptr, false);
   *         SkV4 v = matrix.row(r);
   *         for (int c = 0; c < 4; ++c) {
   *             fWriter->appendFloat(v[c]);
   *         }
   *         fWriter->endArray();
   *     }
   *     fWriter->endArray();
   * }
   * ```
   */
  public override fun write(matrix: SkM44) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeMatrix(const SkMatrix& matrix) {
   *     this->append("matrix");
   *     DrawCommand::MakeJsonMatrix(*fWriter, matrix);
   * }
   * ```
   */
  public override fun writeMatrix(matrix: SkMatrix) {
    TODO("Implement writeMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeIRect(const SkIRect& rect) {
   *     this->append("irect");
   *     DrawCommand::MakeJsonIRect(*fWriter, rect);
   * }
   * ```
   */
  public override fun writeIRect(rect: SkIRect) {
    TODO("Implement writeIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeRect(const SkRect& rect) {
   *     this->append("rect");
   *     DrawCommand::MakeJsonRect(*fWriter, rect);
   * }
   * ```
   */
  public override fun writeRect(rect: SkRect) {
    TODO("Implement writeRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeRegion(const SkRegion& region) {
   *     this->append("region");
   *     DrawCommand::MakeJsonRegion(*fWriter, region);
   * }
   * ```
   */
  public override fun writeRegion(region: SkRegion) {
    TODO("Implement writeRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writePath(const SkPath& path) {
   *     this->append("path");
   *     DrawCommand::MakeJsonPath(*fWriter, path);
   * }
   * ```
   */
  public override fun writePath(path: SkPath) {
    TODO("Implement writePath")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeSampling(const SkSamplingOptions& sampling) {
   *     this->append("sampling");
   *     DrawCommand::MakeJsonSampling(*fWriter, sampling);
   * }
   * ```
   */
  public override fun writeSampling(sampling: SkSamplingOptions) {
    TODO("Implement writeSampling")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t JsonWriteBuffer::writeStream(SkStream* stream, size_t length) {
   *     // Contents not supported
   *     this->append("stream");
   *     fWriter->appendU64(static_cast<uint64_t>(length));
   *     return 0;
   * }
   * ```
   */
  public override fun writeStream(stream: SkStream?, length: ULong): Int {
    TODO("Implement writeStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeImage(const SkImage* image) {
   *     this->append("image");
   *     fWriter->beginObject();
   *     DrawCommand::flatten(*image, *fWriter, *fUrlDataManager);
   *     fWriter->endObject();
   * }
   * ```
   */
  public override fun writeImage(image: SkImage?) {
    TODO("Implement writeImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writeTypeface(SkTypeface* typeface) {
   *     // Unsupported
   *     this->append("typeface");
   *     fWriter->appendPointer(typeface);
   * }
   * ```
   */
  public override fun writeTypeface(typeface: SkTypeface?) {
    TODO("Implement writeTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::writePaint(const SkPaint& paint) {
   *     this->append("paint");
   *     DrawCommand::MakeJsonPaint(*fWriter, paint, *fUrlDataManager);
   * }
   * ```
   */
  public override fun writePaint(paint: SkPaint) {
    TODO("Implement writePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void JsonWriteBuffer::append(const char* type) {
   *     SkString fullName = SkStringPrintf("%02d_%s", fCount++, type);
   *     fWriter->appendName(fullName.c_str());
   * }
   * ```
   */
  private fun append(type: String?) {
    TODO("Implement append")
  }
}

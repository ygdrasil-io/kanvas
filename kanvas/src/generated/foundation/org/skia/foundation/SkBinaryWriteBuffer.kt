package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkData
import org.skia.core.SkFactorySet
import org.skia.core.SkImage
import org.skia.core.SkM44
import org.skia.core.SkPaint
import org.skia.core.SkPath
import org.skia.core.SkRefCntSet
import org.skia.core.SkRegion
import org.skia.core.SkSamplingOptions
import org.skia.core.SkStream
import org.skia.core.SkTypeface
import org.skia.core.SkWStream
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
 * class SkBinaryWriteBuffer : public SkWriteBuffer {
 * public:
 *     explicit SkBinaryWriteBuffer(const SkSerialProcs&);
 *     SkBinaryWriteBuffer(void* initialStorage, size_t storageSize, const SkSerialProcs&);
 *     ~SkBinaryWriteBuffer() override;
 *
 *     void write(const void* buffer, size_t bytes) {
 *         fWriter.write(buffer, bytes);
 *     }
 *     void writePad32(const void* buffer, size_t bytes) override {
 *         fWriter.writePad(buffer, bytes);
 *     }
 *
 *     void reset(void* storage = nullptr, size_t storageSize = 0) {
 *         fWriter.reset(storage, storageSize);
 *     }
 *
 *     size_t bytesWritten() const { return fWriter.bytesWritten(); }
 *
 *     // Returns true iff all of the bytes written so far are stored in the initial storage
 *     // buffer provided in the constructor or the most recent call to reset.
 *     bool usingInitialStorage() const;
 *
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
 *     void writeSampling(const SkSamplingOptions&) override;
 *     void writePath(const SkPath& path) override;
 *     size_t writeStream(SkStream* stream, size_t length) override;
 *     void writeImage(const SkImage*) override;
 *     void writeTypeface(SkTypeface* typeface) override;
 *     void writePaint(const SkPaint& paint) override;
 *
 *     bool writeToStream(SkWStream*) const;
 *     void writeToMemory(void* dst) const { fWriter.flatten(dst); }
 *     sk_sp<SkData> snapshotAsData() const { return fWriter.snapshotAsData(); }
 *
 *     void setFactoryRecorder(sk_sp<SkFactorySet>);
 *     void setTypefaceRecorder(sk_sp<SkRefCntSet>);
 *
 * private:
 *     sk_sp<SkFactorySet> fFactorySet;
 *     sk_sp<SkRefCntSet> fTFSet;
 *
 *     SkWriter32 fWriter;
 *
 *     // Only used if we do not have an fFactorySet
 *     skia_private::THashMap<const char*, uint32_t> fFlattenableDict;
 * }
 * ```
 */
public abstract class SkBinaryWriteBuffer public constructor(
  p: SkSerialProcs,
) : SkWriteBuffer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFactorySet> fFactorySet
   * ```
   */
  private var fFactorySet: SkSp<SkFactorySet> = TODO("Initialize fFactorySet")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRefCntSet> fTFSet
   * ```
   */
  private var fTFSet: SkSp<SkRefCntSet> = TODO("Initialize fTFSet")

  /**
   * C++ original:
   * ```cpp
   * SkWriter32 fWriter
   * ```
   */
  private var fWriter: SkWriter32 = TODO("Initialize fWriter")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<const char*, uint32_t> fFlattenableDict
   * ```
   */
  private var fFlattenableDict: Int = TODO("Initialize fFlattenableDict")

  /**
   * C++ original:
   * ```cpp
   * SkBinaryWriteBuffer::SkBinaryWriteBuffer(const SkSerialProcs& p)
   *         : SkWriteBuffer(p), fFactorySet(nullptr), fTFSet(nullptr) {}
   * ```
   */
  public constructor(
    storage: Unit?,
    storageSize: ULong,
    p: SkSerialProcs,
  ) : super(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const void* buffer, size_t bytes) {
   *         fWriter.write(buffer, bytes);
   *     }
   * ```
   */
  public fun write(buffer: Unit?, bytes: ULong) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePad32(const void* buffer, size_t bytes) override {
   *         fWriter.writePad(buffer, bytes);
   *     }
   * ```
   */
  public override fun writePad32(buffer: Unit?, bytes: ULong) {
    TODO("Implement writePad32")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset(void* storage = nullptr, size_t storageSize = 0) {
   *         fWriter.reset(storage, storageSize);
   *     }
   * ```
   */
  public abstract fun reset(storage: Unit? = null, storageSize: ULong = 0u)

  /**
   * C++ original:
   * ```cpp
   * size_t bytesWritten() const { return fWriter.bytesWritten(); }
   * ```
   */
  public fun bytesWritten(): Int {
    TODO("Implement bytesWritten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBinaryWriteBuffer::usingInitialStorage() const {
   *     return fWriter.usingInitialStorage();
   * }
   * ```
   */
  public fun usingInitialStorage(): Boolean {
    TODO("Implement usingInitialStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeByteArray(const void* data, size_t size) {
   *     fWriter.write32(SkToU32(size));
   *     fWriter.writePad(data, size);
   * }
   * ```
   */
  public override fun writeByteArray(`data`: Unit?, size: ULong) {
    TODO("Implement writeByteArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeBool(bool value) {
   *     fWriter.writeBool(value);
   * }
   * ```
   */
  public override fun writeBool(`value`: Boolean) {
    TODO("Implement writeBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeScalar(SkScalar value) {
   *     fWriter.writeScalar(value);
   * }
   * ```
   */
  public override fun writeScalar(`value`: SkScalar) {
    TODO("Implement writeScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeScalarArray(SkSpan<const SkScalar> values) {
   *     fWriter.write32(SkToInt(values.size()));
   *     fWriter.write(values.data(), values.size_bytes());
   * }
   * ```
   */
  public override fun writeScalarArray(values: SkSpan<SkScalar>) {
    TODO("Implement writeScalarArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeInt(int32_t value) {
   *     fWriter.write32(value);
   * }
   * ```
   */
  public override fun writeInt(`value`: Int) {
    TODO("Implement writeInt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeIntArray(SkSpan<const int32_t> values) {
   *     fWriter.write32(SkToInt(values.size()));
   *     fWriter.write(values.data(), values.size_bytes());
   * }
   * ```
   */
  public override fun writeIntArray(values: SkSpan<Int>) {
    TODO("Implement writeIntArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeUInt(uint32_t value) {
   *     fWriter.write32(value);
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
   * void SkBinaryWriteBuffer::writeFlattenable(const SkFlattenable* flattenable) {
   *     if (nullptr == flattenable) {
   *         this->write32(0);
   *         return;
   *     }
   *
   *     /*
   *      *  We can write 1 of 2 versions of the flattenable:
   *      *
   *      *  1. index into fFactorySet: This assumes the writer will later resolve the function-ptrs
   *      *     into strings for its reader. SkPicture does exactly this, by writing a table of names
   *      *     (matching the indices) up front in its serialized form.
   *      *
   *      *  2. string name of the flattenable or index into fFlattenableDict:  We store the string to
   *      *     allow the reader to specify its own factories after write time. In order to improve
   *      *     compression, if we have already written the string, we write its index instead.
   *      */
   *
   *     if (SkFlattenable::Factory factory = flattenable->getFactory(); factory && fFactorySet) {
   *         this->write32(fFactorySet->add(factory));
   *     } else {
   *         const char* name = flattenable->getTypeName();
   *         SkASSERT(name);
   *         SkASSERT(0 != strcmp("", name));
   *
   *         if (uint32_t* indexPtr = fFlattenableDict.find(name)) {
   *             // We will write the index as a 32-bit int.  We want the first byte
   *             // that we send to be zero - this will act as a sentinel that we
   *             // have an index (not a string).  This means that we will send the
   *             // the index shifted left by 8.  The remaining 24-bits should be
   *             // plenty to store the index.  Note that this strategy depends on
   *             // being little endian, and type names being non-empty.
   *             SkASSERT(0 == *indexPtr >> 24);
   *             this->write32(*indexPtr << 8);
   *         } else {
   *             this->writeString(name);
   *             fFlattenableDict.set(name, fFlattenableDict.count() + 1);
   *         }
   *     }
   *
   *     // make room for the size of the flattened object
   *     (void)fWriter.reserve(sizeof(uint32_t));
   *     // record the current size, so we can subtract after the object writes.
   *     size_t offset = fWriter.bytesWritten();
   *     // now flatten the object
   *     flattenable->flatten(*this);
   *     size_t objSize = fWriter.bytesWritten() - offset;
   *     // record the obj's size
   *     fWriter.overwriteTAt(offset - sizeof(uint32_t), SkToU32(objSize));
   * }
   * ```
   */
  public override fun writeFlattenable(flattenable: SkFlattenable?) {
    TODO("Implement writeFlattenable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeColor(SkColor color) {
   *     fWriter.write32(color);
   * }
   * ```
   */
  public override fun writeColor(color: SkColor) {
    TODO("Implement writeColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeColorArray(SkSpan<const SkColor> values) {
   *     fWriter.write32(SkToInt(values.size()));
   *     fWriter.write(values.data(), values.size_bytes());
   * }
   * ```
   */
  public override fun writeColorArray(values: SkSpan<SkColor>) {
    TODO("Implement writeColorArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeColor4f(const SkColor4f& color) {
   *     fWriter.write(&color, sizeof(SkColor4f));
   * }
   * ```
   */
  public override fun writeColor4f(color: SkColor4f) {
    TODO("Implement writeColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeColor4fArray(SkSpan<const SkColor4f> values) {
   *     fWriter.write32(SkToInt(values.size()));
   *     fWriter.write(values.data(), values.size_bytes());
   * }
   * ```
   */
  public override fun writeColor4fArray(values: SkSpan<SkColor4f>) {
    TODO("Implement writeColor4fArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writePoint(const SkPoint& point) {
   *     fWriter.writeScalar(point.fX);
   *     fWriter.writeScalar(point.fY);
   * }
   * ```
   */
  public override fun writePoint(point: SkPoint) {
    TODO("Implement writePoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writePointArray(SkSpan<const SkPoint> values) {
   *     fWriter.write32(SkToInt(values.size()));
   *     fWriter.write(values.data(), values.size_bytes());
   * }
   * ```
   */
  public override fun writePointArray(values: SkSpan<SkPoint>) {
    TODO("Implement writePointArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writePoint3(const SkPoint3& point) {
   *     this->writePad32(&point, sizeof(SkPoint3));
   * }
   * ```
   */
  public override fun writePoint3(point: SkPoint3) {
    TODO("Implement writePoint3")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::write(const SkM44& matrix) {
   *     fWriter.write(SkMatrixPriv::M44ColMajor(matrix), sizeof(float) * 16);
   * }
   * ```
   */
  public override fun write(matrix: SkM44) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeMatrix(const SkMatrix& matrix) {
   *     fWriter.writeMatrix(matrix);
   * }
   * ```
   */
  public override fun writeMatrix(matrix: SkMatrix) {
    TODO("Implement writeMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeIRect(const SkIRect& rect) {
   *     fWriter.write(&rect, sizeof(SkIRect));
   * }
   * ```
   */
  public override fun writeIRect(rect: SkIRect) {
    TODO("Implement writeIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeRect(const SkRect& rect) {
   *     fWriter.writeRect(rect);
   * }
   * ```
   */
  public override fun writeRect(rect: SkRect) {
    TODO("Implement writeRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeRegion(const SkRegion& region) {
   *     fWriter.writeRegion(region);
   * }
   * ```
   */
  public override fun writeRegion(region: SkRegion) {
    TODO("Implement writeRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeSampling(const SkSamplingOptions& sampling) {
   *     fWriter.writeSampling(sampling);
   * }
   * ```
   */
  public override fun writeSampling(sampling: SkSamplingOptions) {
    TODO("Implement writeSampling")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writePath(const SkPath& path) {
   *     fWriter.writePath(path);
   * }
   * ```
   */
  public override fun writePath(path: SkPath) {
    TODO("Implement writePath")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkBinaryWriteBuffer::writeStream(SkStream* stream, size_t length) {
   *     fWriter.write32(SkToU32(length));
   *     size_t bytesWritten = fWriter.readFromStream(stream, length);
   *     if (bytesWritten < length) {
   *         fWriter.reservePad(length - bytesWritten);
   *     }
   *     return bytesWritten;
   * }
   * ```
   */
  public override fun writeStream(stream: SkStream?, length: ULong): Int {
    TODO("Implement writeStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeImage(const SkImage* image) {
   *     uint32_t flags = 0;
   *     const SkMipmap* mips = as_IB(image)->onPeekMips();
   *     if (mips) {
   *         flags |= SkWriteBufferImageFlags::kHasMipmap;
   *     }
   *     if (image->alphaType() == kUnpremul_SkAlphaType) {
   *         flags |= SkWriteBufferImageFlags::kUnpremul;
   *     }
   *
   *     this->write32(flags);
   *
   *     sk_sp<const SkData> data = serialize_image(image, fProcs);
   *     SkASSERT(data);
   *     this->writeDataAsByteArray(data.get());
   *
   *     if (flags & SkWriteBufferImageFlags::kHasMipmap) {
   *         sk_sp<SkData> mipData = serialize_mipmap(mips, fProcs);
   *         this->writeDataAsByteArray(mipData.get());
   *     }
   * }
   * ```
   */
  public override fun writeImage(image: SkImage?) {
    TODO("Implement writeImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writeTypeface(SkTypeface* obj) {
   *     // Write 32 bits (signed)
   *     //   0 -- empty font
   *     //  >0 -- index
   *     //  <0 -- custom (serial procs)
   *
   *     if (obj == nullptr) {
   *         fWriter.write32(0);
   *     } else if (fProcs.fTypefaceProc) {
   *         auto data = fProcs.fTypefaceProc(obj, fProcs.fTypefaceCtx);
   *         if (data) {
   *             size_t size = data->size();
   *             if (!SkTFitsIn<int32_t>(size)) {
   *                 size = 0;               // fall back to default font
   *             }
   *             int32_t ssize = SkToS32(size);
   *             fWriter.write32(-ssize);    // negative to signal custom
   *             if (size) {
   *                 this->writePad32(data->data(), size);
   *             }
   *             return;
   *         }
   *         // no data means fall through for std behavior
   *     }
   *     fWriter.write32(fTFSet ? fTFSet->add(obj) : 0);
   * }
   * ```
   */
  public override fun writeTypeface(typeface: SkTypeface?) {
    TODO("Implement writeTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::writePaint(const SkPaint& paint) {
   *     SkPaintPriv::Flatten(paint, *this);
   * }
   * ```
   */
  public override fun writePaint(paint: SkPaint) {
    TODO("Implement writePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBinaryWriteBuffer::writeToStream(SkWStream* stream) const {
   *     return fWriter.writeToStream(stream);
   * }
   * ```
   */
  public fun writeToStream(stream: SkWStream?): Boolean {
    TODO("Implement writeToStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeToMemory(void* dst) const { fWriter.flatten(dst); }
   * ```
   */
  public fun writeToMemory(dst: Unit?) {
    TODO("Implement writeToMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> snapshotAsData() const { return fWriter.snapshotAsData(); }
   * ```
   */
  public fun snapshotAsData(): SkSp<SkData> {
    TODO("Implement snapshotAsData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::setFactoryRecorder(sk_sp<SkFactorySet> rec) {
   *     fFactorySet = std::move(rec);
   * }
   * ```
   */
  public fun setFactoryRecorder(rec: SkSp<SkFactorySet>) {
    TODO("Implement setFactoryRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBinaryWriteBuffer::setTypefaceRecorder(sk_sp<SkRefCntSet> rec) {
   *     fTFSet = std::move(rec);
   * }
   * ```
   */
  public fun setTypefaceRecorder(rec: SkSp<SkRefCntSet>) {
    TODO("Implement setTypefaceRecorder")
  }
}

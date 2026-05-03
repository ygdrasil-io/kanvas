package org.skia.foundation

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkBlender
import org.skia.core.SkColorFilter
import org.skia.core.SkLegacyFQ
import org.skia.core.SkMaskFilter
import org.skia.core.SkPathEffect
import org.skia.core.SkPicturePriv
import org.skia.core.SkShader
import org.skia.core.THashMap
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
 * class SkReadBuffer {
 * public:
 *     SkReadBuffer() = default;
 *     SkReadBuffer(const void* data, size_t size) {
 *         this->setMemory(data, size);
 *     }
 *
 *     void setMemory(const void*, size_t);
 *
 *     /**
 *      *  Returns true IFF the version is older than the specified version.
 *      */
 *     bool isVersionLT(SkPicturePriv::Version targetVersion) const {
 *         SkASSERT(targetVersion > 0);
 *         return fVersion > 0 && fVersion < targetVersion;
 *     }
 *
 *     uint32_t getVersion() const { return fVersion; }
 *
 *     /** This may be called at most once; most clients of SkReadBuffer should not mess with it. */
 *     void setVersion(int version) {
 *         SkASSERT(0 == fVersion || version == fVersion);
 *         fVersion = version;
 *     }
 *
 *     size_t size() const { return fStop - fBase; }
 *     size_t offset() const { return fCurr - fBase; }
 *     bool eof() { return fCurr >= fStop; }
 *     const void* skip(size_t size);
 *     const void* skip(size_t count, size_t size);    // does safe multiply
 *     size_t available() const { return fStop - fCurr; }
 *
 *     template <typename T> const T* skipT() {
 *         return static_cast<const T*>(this->skip(sizeof(T)));
 *     }
 *     template <typename T> const T* skipT(size_t count) {
 *         return static_cast<const T*>(this->skip(count, sizeof(T)));
 *     }
 *
 *     // primitives
 *     bool readBool();
 *     SkColor readColor();
 *     int32_t readInt();
 *     SkScalar readScalar();
 *     uint32_t readUInt();
 *     int32_t read32();
 *
 *     template <typename T> T read32LE(T max) {
 *         uint32_t value = this->readUInt();
 *         if (!this->validate(value <= static_cast<uint32_t>(max))) {
 *             value = 0;
 *         }
 *         return static_cast<T>(value);
 *     }
 *
 *     // peek
 *     uint8_t peekByte();
 *
 *     void readString(SkString* string);
 *
 *     // common data structures
 *     void readColor4f(SkColor4f* color);
 *     void readPoint(SkPoint* point);
 *     SkPoint readPoint() { SkPoint p; this->readPoint(&p); return p; }
 *     void readPoint3(SkPoint3* point);
 *     void read(SkM44*);
 *     void readMatrix(SkMatrix* matrix);
 *     void readIRect(SkIRect* rect);
 *     void readRect(SkRect* rect);
 *     SkRect readRect();
 *     void readRRect(SkRRect* rrect);
 *     void readRegion(SkRegion* region);
 *
 *     std::optional<SkPath> readPath();
 *
 *     SkPaint readPaint() {
 *         return SkPaintPriv::Unflatten(*this);
 *     }
 *
 *     SkFlattenable* readRawFlattenable();
 *     SkFlattenable* readFlattenable(SkFlattenable::Type);
 *     template <typename T> sk_sp<T> readFlattenable() {
 *         return sk_sp<T>((T*)this->readFlattenable(T::GetFlattenableType()));
 *     }
 *     sk_sp<SkColorFilter> readColorFilter() { return this->readFlattenable<SkColorFilterBase>(); }
 *     sk_sp<SkImageFilter> readImageFilter() { return this->readFlattenable<SkImageFilter_Base>(); }
 *     sk_sp<SkBlender> readBlender() { return this->readFlattenable<SkBlenderBase>(); }
 *     sk_sp<SkMaskFilter> readMaskFilter() { return this->readFlattenable<SkMaskFilterBase>(); }
 *     sk_sp<SkPathEffect> readPathEffect() { return this->readFlattenable<SkPathEffect>(); }
 *     sk_sp<SkShader> readShader() { return this->readFlattenable<SkShaderBase>(); }
 *
 *     // Reads SkAlign4(bytes), but will only copy bytes into the buffer.
 *     bool readPad32(void* buffer, size_t bytes);
 *
 *     // binary data and arrays
 *     bool readByteArray(void* value, size_t size);
 *     bool readColorArray(SkSpan<SkColor>);
 *     bool readColor4fArray(SkSpan<SkColor4f>);
 *     bool readIntArray(SkSpan<int32_t>);
 *     bool readPointArray(SkSpan<SkPoint>);
 *     bool readScalarArray(SkSpan<SkScalar>);
 *
 *     const void* skipByteArray(size_t* size);
 *
 *     sk_sp<SkData> readByteArrayAsData();
 *
 *     // helpers to get info about arrays and binary data
 *     uint32_t getArrayCount();
 *
 *     // If there is a real error (e.g. data is corrupted) this returns null. If the image cannot
 *     // be created (e.g. it was not originally encoded) then this returns an image that doesn't
 *     // draw.
 *     sk_sp<SkImage> readImage();
 *     sk_sp<SkTypeface> readTypeface();
 *
 *     void setTypefaceArray(sk_sp<SkTypeface> array[], int count) {
 *         fTFArray = array;
 *         fTFCount = count;
 *     }
 *
 *     /**
 *      *  Call this with a pre-loaded array of Factories, in the same order as
 *      *  were created/written by the writer. SkPicture uses this.
 *      */
 *     void setFactoryPlayback(SkFlattenable::Factory array[], int count) {
 *         fFactoryArray = array;
 *         fFactoryCount = count;
 *     }
 *
 *     void setDeserialProcs(const SkDeserialProcs& procs);
 *     const SkDeserialProcs& getDeserialProcs() const { return fProcs; }
 *
 *     bool allowSkSL() const { return fAllowSkSL; }
 *     void setAllowSkSL(bool allow) { fAllowSkSL = allow; }
 *
 *     /**
 *      *  If isValid is false, sets the buffer to be "invalid". Returns true if the buffer
 *      *  is still valid.
 *      */
 *     bool validate(bool isValid) {
 *         if (!isValid) {
 *             this->setInvalid();
 *         }
 *         return !fError;
 *     }
 *
 *     /**
 *      * Helper function to do a preflight check before a large allocation or read.
 *      * Returns true if there is enough bytes in the buffer to read n elements of T.
 *      * If not, the buffer will be "invalid" and false will be returned.
 *      */
 *     template <typename T>
 *     bool validateCanReadN(size_t n) {
 *         return this->validate(n <= (this->available() / sizeof(T)));
 *     }
 *
 *     bool isValid() const { return !fError; }
 *     bool validateIndex(int index, int count) {
 *         return this->validate(index >= 0 && index < count);
 *     }
 *
 *     // Utilities that mark the buffer invalid if the requested value is out-of-range
 *
 *     // If the read value is outside of the range, validate(false) is called, and min
 *     // is returned, else the value is returned.
 *     int32_t checkInt(int min, int max);
 *
 *     template <typename T> T checkRange(T min, T max) {
 *         return static_cast<T>(this->checkInt(static_cast<int32_t>(min),
 *                                              static_cast<int32_t>(max)));
 *     }
 *
 *     SkLegacyFQ checkFilterQuality();
 *
 *     SkSamplingOptions readSampling();
 *
 * private:
 *     const char* readString(size_t* length);
 *
 *     void setInvalid();
 *     bool readArray(void* value, size_t size, size_t elementSize);
 *     bool isAvailable(size_t size) const { return size <= this->available(); }
 *
 *     // These are always 4-byte aligned
 *     const char* fCurr = nullptr;  // current position within buffer
 *     const char* fStop = nullptr;  // end of buffer
 *     const char* fBase = nullptr;  // beginning of buffer
 *
 *     // Only used if we do not have an fFactoryArray.
 *     skia_private::THashMap<uint32_t, SkFlattenable::Factory> fFlattenableDict;
 *
 *     int fVersion = 0;
 *
 *     sk_sp<SkTypeface>* fTFArray = nullptr;
 *     int                fTFCount = 0;
 *
 *     SkFlattenable::Factory* fFactoryArray = nullptr;
 *     int                     fFactoryCount = 0;
 *
 *     SkDeserialProcs fProcs;
 *
 *     static bool IsPtrAlign4(const void* ptr) {
 *         return SkIsAlign4((uintptr_t)ptr);
 *     }
 *
 *     bool fAllowSkSL = true;
 *     bool fError = false;
 * }
 * ```
 */
public data class SkReadBuffer public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fCurr = nullptr
   * ```
   */
  private val fCurr: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fStop = nullptr
   * ```
   */
  private val fStop: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fBase = nullptr
   * ```
   */
  private val fBase: String?,
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<uint32_t, SkFlattenable::Factory> fFlattenableDict
   * ```
   */
  private var fFlattenableDict: THashMap<UInt, SkFlattenableFactory>,
  /**
   * C++ original:
   * ```cpp
   * int fVersion = 0
   * ```
   */
  private var fVersion: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface>* fTFArray = nullptr
   * ```
   */
  private var fTFArray: SkSp<SkTypeface>?,
  /**
   * C++ original:
   * ```cpp
   * int                fTFCount = 0
   * ```
   */
  private var fTFCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Factory* fFactoryArray = nullptr
   * ```
   */
  private var fFactoryArray: SkFlattenableFactory?,
  /**
   * C++ original:
   * ```cpp
   * int                     fFactoryCount = 0
   * ```
   */
  private var fFactoryCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkDeserialProcs fProcs
   * ```
   */
  private var fProcs: SkDeserialProcs,
  /**
   * C++ original:
   * ```cpp
   * bool fAllowSkSL = true
   * ```
   */
  private var fAllowSkSL: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fError = false
   * ```
   */
  private var fError: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::setMemory(const void* data, size_t size) {
   *     this->validate(IsPtrAlign4(data) && (SkAlign4(size) == size));
   *     if (!fError) {
   *         fBase = fCurr = (const char*)data;
   *         fStop = fBase + size;
   *     }
   * }
   * ```
   */
  public fun setMemory(`data`: Unit?, size: ULong) {
    TODO("Implement setMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVersionLT(SkPicturePriv::Version targetVersion) const {
   *         SkASSERT(targetVersion > 0);
   *         return fVersion > 0 && fVersion < targetVersion;
   *     }
   * ```
   */
  public fun isVersionLT(targetVersion: SkPicturePriv.Version): Boolean {
    TODO("Implement isVersionLT")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getVersion() const { return fVersion; }
   * ```
   */
  public fun getVersion(): UInt {
    TODO("Implement getVersion")
  }

  /**
   * C++ original:
   * ```cpp
   * void setVersion(int version) {
   *         SkASSERT(0 == fVersion || version == fVersion);
   *         fVersion = version;
   *     }
   * ```
   */
  public fun setVersion(version: Int) {
    TODO("Implement setVersion")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fStop - fBase; }
   * ```
   */
  public fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t offset() const { return fCurr - fBase; }
   * ```
   */
  public fun offset(): ULong {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool eof() { return fCurr >= fStop; }
   * ```
   */
  public fun eof(): Boolean {
    TODO("Implement eof")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkReadBuffer::skip(size_t size) {
   *     size_t inc = SkAlign4(size);
   *     this->validate(inc >= size);
   *     const void* addr = fCurr;
   *     this->validate(IsPtrAlign4(addr) && this->isAvailable(inc));
   *     if (fError) {
   *         return nullptr;
   *     }
   *
   *     fCurr += inc;
   *     return addr;
   * }
   * ```
   */
  public fun skip(size: ULong) {
    TODO("Implement skip")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkReadBuffer::skip(size_t count, size_t size) {
   *     return this->skip(SkSafeMath::Mul(count, size));
   * }
   * ```
   */
  public fun skip(count: ULong, size: ULong) {
    TODO("Implement skip")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t available() const { return fStop - fCurr; }
   * ```
   */
  public fun available(): ULong {
    TODO("Implement available")
  }

  /**
   * C++ original:
   * ```cpp
   * const T* skipT() {
   *         return static_cast<const T*>(this->skip(sizeof(T)));
   *     }
   * ```
   */
  public fun skipT(): Int {
    TODO("Implement skipT")
  }

  /**
   * C++ original:
   * ```cpp
   * const T* skipT(size_t count) {
   *         return static_cast<const T*>(this->skip(count, sizeof(T)));
   *     }
   * ```
   */
  public fun skipT(count: ULong): Int {
    TODO("Implement skipT")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readBool() {
   *     uint32_t value = this->readUInt();
   *     // Boolean value should be either 0 or 1
   *     this->validate(!(value & ~1));
   *     return value != 0;
   * }
   * ```
   */
  public fun readBool(): Boolean {
    TODO("Implement readBool")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor SkReadBuffer::readColor() {
   *     return this->readUInt();
   * }
   * ```
   */
  public fun readColor(): SkColor {
    TODO("Implement readColor")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t SkReadBuffer::readInt() {
   *     const size_t inc = sizeof(int32_t);
   *     if (!this->validate(IsPtrAlign4(fCurr) && this->isAvailable(inc))) {
   *         return 0;
   *     }
   *     int32_t value = *((const int32_t*)fCurr);
   *     fCurr += inc;
   *     return value;
   * }
   * ```
   */
  public fun readInt(): Int {
    TODO("Implement readInt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkReadBuffer::readScalar() {
   *     const size_t inc = sizeof(SkScalar);
   *     if (!this->validate(IsPtrAlign4(fCurr) && this->isAvailable(inc))) {
   *         return 0;
   *     }
   *     SkScalar value = *((const SkScalar*)fCurr);
   *     fCurr += inc;
   *     return value;
   * }
   * ```
   */
  public fun readScalar(): SkScalar {
    TODO("Implement readScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkReadBuffer::readUInt() {
   *     return this->readInt();
   * }
   * ```
   */
  public fun readUInt(): UInt {
    TODO("Implement readUInt")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t SkReadBuffer::read32() {
   *     return this->readInt();
   * }
   * ```
   */
  public fun read32(): Int {
    TODO("Implement read32")
  }

  /**
   * C++ original:
   * ```cpp
   * T read32LE(T max) {
   *         uint32_t value = this->readUInt();
   *         if (!this->validate(value <= static_cast<uint32_t>(max))) {
   *             value = 0;
   *         }
   *         return static_cast<T>(value);
   *     }
   * ```
   */
  public fun read32LE(max: Int): Int {
    TODO("Implement read32LE")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t SkReadBuffer::peekByte() {
   *     if (this->available() <= 0) {
   *         fError = true;
   *         return 0;
   *     }
   *     return *((const uint8_t*)fCurr);
   * }
   * ```
   */
  public fun peekByte(): UByte {
    TODO("Implement peekByte")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readString(SkString* string) {
   *     size_t len;
   *     if (const char* c_str = this->readString(&len)) {
   *         string->set(c_str, len);
   *         return;
   *     }
   *     string->reset();
   * }
   * ```
   */
  public fun readString(string: String?) {
    TODO("Implement readString")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readColor4f(SkColor4f* color) {
   *     if (!this->readPad32(color, sizeof(SkColor4f))) {
   *         *color = {0, 0, 0, 0};
   *     }
   * }
   * ```
   */
  public fun readColor4f(color: SkColor4f?) {
    TODO("Implement readColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readPoint(SkPoint* point) {
   *     point->fX = this->readScalar();
   *     point->fY = this->readScalar();
   * }
   * ```
   */
  public fun readPoint(point: SkPoint?) {
    TODO("Implement readPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint readPoint() { SkPoint p; this->readPoint(&p); return p; }
   * ```
   */
  public fun readPoint(): SkPoint {
    TODO("Implement readPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readPoint3(SkPoint3* point) {
   *     this->readPad32(point, sizeof(SkPoint3));
   * }
   * ```
   */
  public fun readPoint3(point: SkPoint3?) {
    TODO("Implement readPoint3")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::read(SkM44* matrix) {
   *     if (this->isValid()) {
   *         if (const float* m = (const float*)this->skip(sizeof(float) * 16)) {
   *             *matrix = SkM44::ColMajor(m);
   *         }
   *     }
   *     if (!this->isValid()) {
   *         *matrix = SkM44();
   *     }
   * }
   * ```
   */
  public fun read(matrix: SkM44?) {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readMatrix(SkMatrix* matrix) {
   *     size_t size = 0;
   *     if (this->isValid()) {
   *         size = SkMatrixPriv::ReadFromMemory(matrix, fCurr, this->available());
   *         (void)this->validate((SkAlign4(size) == size) && (0 != size));
   *     }
   *     if (!this->isValid()) {
   *         matrix->reset();
   *     }
   *     (void)this->skip(size);
   * }
   * ```
   */
  public fun readMatrix(matrix: SkMatrix?) {
    TODO("Implement readMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readIRect(SkIRect* rect) {
   *     if (!this->readPad32(rect, sizeof(SkIRect))) {
   *         rect->setEmpty();
   *     }
   * }
   * ```
   */
  public fun readIRect(rect: SkIRect?) {
    TODO("Implement readIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readRect(SkRect* rect) {
   *     if (!this->readPad32(rect, sizeof(SkRect))) {
   *         rect->setEmpty();
   *     }
   * }
   * ```
   */
  public fun readRect(rect: SkRect?) {
    TODO("Implement readRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkReadBuffer::readRect() {
   *     SkRect r;
   *     if (!this->readPad32(&r, sizeof(SkRect))) {
   *         r.setEmpty();
   *     }
   *     return r;
   * }
   * ```
   */
  public fun readRect(): SkRect {
    TODO("Implement readRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readRRect(SkRRect* rrect) {
   *     size_t size = 0;
   *     if (!fError) {
   *         size = rrect->readFromMemory(fCurr, this->available());
   *         if (!this->validate((SkAlign4(size) == size) && (0 != size))) {
   *             rrect->setEmpty();
   *         }
   *     }
   *     (void)this->skip(size);
   * }
   * ```
   */
  public fun readRRect(rrect: SkRRect?) {
    TODO("Implement readRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::readRegion(SkRegion* region) {
   *     size_t size = 0;
   *     if (!fError) {
   *         size = region->readFromMemory(fCurr, this->available());
   *         if (!this->validate((SkAlign4(size) == size) && (0 != size))) {
   *             region->setEmpty();
   *         }
   *     }
   *     (void)this->skip(size);
   * }
   * ```
   */
  public fun readRegion(region: SkRegion?) {
    TODO("Implement readRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPath> SkReadBuffer::readPath() {
   *     if (fError) {
   *         return {};
   *     }
   *
   *     size_t size = 0;
   *     auto path = SkPath::ReadFromMemory(fCurr, this->available(), &size);
   *
   *     // todo: consider moving this 4-byte-alignment check elsewhere
   *     //       i.e. why is that a burden on SkPath?
   *     //            why don't we just skipAlign4() or something?
   *     (void)this->validate(SkAlign4(size) == size && path.has_value());
   *
   *     // we move forward, regardless of if the path succeeded
   *     (void)this->skip(size);
   *
   *     return path;
   * }
   * ```
   */
  public fun readPath(): Int {
    TODO("Implement readPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint readPaint() {
   *         return SkPaintPriv::Unflatten(*this);
   *     }
   * ```
   */
  public fun readPaint(): SkPaint {
    TODO("Implement readPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable* SkReadBuffer::readRawFlattenable() {
   *     SkFlattenable::Factory factory = nullptr;
   *
   *     if (fFactoryCount > 0) {
   *         int32_t index = this->read32();
   *         if (0 == index || !this->isValid()) {
   *             return nullptr; // writer failed to give us the flattenable
   *         }
   *         if (index < 0) {
   *             this->validate(false);
   *             return nullptr;
   *         }
   *         index -= 1;     // we stored the index-base-1
   *         if ((unsigned)index >= (unsigned)fFactoryCount) {
   *             this->validate(false);
   *             return nullptr;
   *         }
   *         factory = fFactoryArray[index];
   *     } else {
   *         if (this->peekByte() != 0) {
   *             // If the first byte is non-zero, the flattenable is specified by a string.
   *             size_t ignored_length;
   *             if (const char* name = this->readString(&ignored_length)) {
   *                 factory = SkFlattenable::NameToFactory(name);
   *                 fFlattenableDict.set(fFlattenableDict.count() + 1, factory);
   *             }
   *         } else {
   *             // Read the index.  We are guaranteed that the first byte
   *             // is zeroed, so we must shift down a byte.
   *             uint32_t index = this->readUInt() >> 8;
   *             if (index == 0) {
   *                 return nullptr; // writer failed to give us the flattenable
   *             }
   *
   *             if (SkFlattenable::Factory* found = fFlattenableDict.find(index)) {
   *                 factory = *found;
   *             }
   *         }
   *
   *         if (!this->validate(factory != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     // if we get here, factory may still be null, but if that is the case, the
   *     // failure was ours, not the writer.
   *     sk_sp<SkFlattenable> obj;
   *     uint32_t sizeRecorded = this->read32();
   *     if (factory) {
   *         size_t offset = this->offset();
   *         obj = (*factory)(*this);
   *         // check that we read the amount we expected
   *         size_t sizeRead = this->offset() - offset;
   *         if (sizeRecorded != sizeRead) {
   *             this->validate(false);
   *             return nullptr;
   *         }
   *     } else {
   *         // we must skip the remaining data
   *         this->skip(sizeRecorded);
   *     }
   *     if (!this->isValid()) {
   *         return nullptr;
   *     }
   *     return obj.release();
   * }
   * ```
   */
  public fun readRawFlattenable(): SkFlattenable {
    TODO("Implement readRawFlattenable")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable* SkReadBuffer::readFlattenable(SkFlattenable::Type ft) {
   *     SkFlattenable* obj = this->readRawFlattenable();
   *     if (obj && obj->getFlattenableType() != ft) {
   *         this->validate(false);
   *         obj->unref();
   *         return nullptr;
   *     }
   *     return obj;
   * }
   * ```
   */
  public fun readFlattenable(ft: SkFlattenable.Type): SkFlattenable {
    TODO("Implement readFlattenable")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<T> readFlattenable() {
   *         return sk_sp<T>((T*)this->readFlattenable(T::GetFlattenableType()));
   *     }
   * ```
   */
  public fun <T> readFlattenable(): SkSp<T> {
    TODO("Implement readFlattenable")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> readColorFilter() { return this->readFlattenable<SkColorFilterBase>(); }
   * ```
   */
  public fun readColorFilter(): SkSp<SkColorFilter> {
    TODO("Implement readColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> readImageFilter() { return this->readFlattenable<SkImageFilter_Base>(); }
   * ```
   */
  public fun readImageFilter(): SkSp<SkImageFilter> {
    TODO("Implement readImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBlender> readBlender() { return this->readFlattenable<SkBlenderBase>(); }
   * ```
   */
  public fun readBlender(): SkSp<SkBlender> {
    TODO("Implement readBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMaskFilter> readMaskFilter() { return this->readFlattenable<SkMaskFilterBase>(); }
   * ```
   */
  public fun readMaskFilter(): SkSp<SkMaskFilter> {
    TODO("Implement readMaskFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathEffect> readPathEffect() { return this->readFlattenable<SkPathEffect>(); }
   * ```
   */
  public fun readPathEffect(): SkSp<SkPathEffect> {
    TODO("Implement readPathEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> readShader() { return this->readFlattenable<SkShaderBase>(); }
   * ```
   */
  public fun readShader(): SkSp<SkShader> {
    TODO("Implement readShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readPad32(void* buffer, size_t bytes) {
   *     if (const void* src = this->skip(bytes)) {
   *         // buffer might be null if bytes is zero (see SkAutoMalloc), hence we call
   *         // the careful version of memcpy.
   *         sk_careful_memcpy(buffer, src, bytes);
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun readPad32(buffer: Unit?, bytes: ULong): Boolean {
    TODO("Implement readPad32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readByteArray(void* value, size_t size) {
   *     return this->readArray(value, size, sizeof(uint8_t));
   * }
   * ```
   */
  public fun readByteArray(`value`: Unit?, size: ULong): Boolean {
    TODO("Implement readByteArray")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readColorArray(SkSpan<SkColor> colors) {
   *     return this->readArray(colors.data(), colors.size(), sizeof(SkColor));
   * }
   * ```
   */
  public fun readColorArray(colors: SkSpan<SkColor>): Boolean {
    TODO("Implement readColorArray")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readColor4fArray(SkSpan<SkColor4f> colors) {
   *     return this->readArray(colors.data(), colors.size(), sizeof(SkColor4f));
   * }
   * ```
   */
  public fun readColor4fArray(colors: SkSpan<SkColor4f>): Boolean {
    TODO("Implement readColor4fArray")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readIntArray(SkSpan<int32_t> values) {
   *     return this->readArray(values.data(), values.size(), sizeof(int32_t));
   * }
   * ```
   */
  public fun readIntArray(values: SkSpan<Int>): Boolean {
    TODO("Implement readIntArray")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readPointArray(SkSpan<SkPoint> points) {
   *     return this->readArray(points.data(), points.size(), sizeof(SkPoint));
   * }
   * ```
   */
  public fun readPointArray(points: SkSpan<SkPoint>): Boolean {
    TODO("Implement readPointArray")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readScalarArray(SkSpan<SkScalar> values) {
   *     return this->readArray(values.data(), values.size(), sizeof(SkScalar));
   * }
   * ```
   */
  public fun readScalarArray(values: SkSpan<SkScalar>): Boolean {
    TODO("Implement readScalarArray")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkReadBuffer::skipByteArray(size_t* size) {
   *     const uint32_t count = this->readUInt();
   *     const void* buf = this->skip(count);
   *     if (size) {
   *         *size = this->isValid() ? count : 0;
   *     }
   *     return buf;
   * }
   * ```
   */
  public fun skipByteArray(size: ULong?) {
    TODO("Implement skipByteArray")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkReadBuffer::readByteArrayAsData() {
   *     size_t numBytes = this->getArrayCount();
   *     if (!this->validate(this->isAvailable(numBytes))) {
   *         return nullptr;
   *     }
   *
   *     SkAutoMalloc buffer(numBytes);
   *     if (!this->readByteArray(buffer.get(), numBytes)) {
   *         return nullptr;
   *     }
   *     return SkData::MakeFromMalloc(buffer.release(), numBytes);
   * }
   * ```
   */
  public fun readByteArrayAsData(): SkSp<SkData> {
    TODO("Implement readByteArrayAsData")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkReadBuffer::getArrayCount() {
   *     const size_t inc = sizeof(uint32_t);
   *     if (!this->validate(IsPtrAlign4(fCurr) && this->isAvailable(inc))) {
   *         return 0;
   *     }
   *     return *((const uint32_t*)fCurr);
   * }
   * ```
   */
  public fun getArrayCount(): UInt {
    TODO("Implement getArrayCount")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkReadBuffer::readImage() {
   *     uint32_t flags = this->read32();
   *
   *     std::optional<SkAlphaType> alphaType = std::nullopt;
   *     if (flags & SkWriteBufferImageFlags::kUnpremul) {
   *         alphaType = kUnpremul_SkAlphaType;
   *     }
   *     sk_sp<SkImage> image;
   *     {
   *         sk_sp<SkData> data = this->readByteArrayAsData();
   *         if (!data) {
   *             this->validate(false);
   *             return nullptr;
   *         }
   *         image = deserialize_image(data, fProcs, alphaType);
   *     }
   *
   *     // This flag is not written by new SKPs anymore.
   *     if (flags & SkWriteBufferImageFlags::kHasSubsetRect) {
   *         SkIRect subset;
   *         this->readIRect(&subset);
   *         if (image) {
   *             image = image->makeSubset(nullptr, subset, {});
   *         }
   *     }
   *
   *     if (flags & SkWriteBufferImageFlags::kHasMipmap) {
   *         sk_sp<SkData> data = this->readByteArrayAsData();
   *         if (!data) {
   *             this->validate(false);
   *             return nullptr;
   *         }
   *         if (image) {
   *             image = add_mipmaps(image, std::move(data), fProcs, alphaType);
   *         }
   *     }
   *     return image ? image : MakeEmptyImage(1, 1);
   * }
   * ```
   */
  public fun readImage(): SkSp<SkImage> {
    TODO("Implement readImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkReadBuffer::readTypeface() {
   *     // Read 32 bits (signed)
   *     //   0 -- return null (empty font)
   *     //  >0 -- index
   *     //  <0 -- custom (serial procs) : negative size in bytes
   *
   *     int32_t index = this->read32();
   *     if (index == 0) {
   *         return nullptr;
   *     } else if (index > 0) {
   *         if (!this->validate(index <= fTFCount)) {
   *             return nullptr;
   *         }
   *         return fTFArray[index - 1];
   *     } else {    // custom
   *         size_t size = sk_negate_to_size_t(index);
   *         const void* data = this->skip(size);
   *         if (!this->validate(data != nullptr && fProcs.fTypefaceProc)) {
   *             return nullptr;
   *         }
   *         return fProcs.fTypefaceProc(data, size, fProcs.fTypefaceCtx);
   *     }
   * }
   * ```
   */
  public fun readTypeface(): SkSp<SkTypeface> {
    TODO("Implement readTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTypefaceArray(sk_sp<SkTypeface> array[], int count) {
   *         fTFArray = array;
   *         fTFCount = count;
   *     }
   * ```
   */
  public fun setTypefaceArray(array: Array<SkSp<SkTypeface>>, count: Int) {
    TODO("Implement setTypefaceArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFactoryPlayback(SkFlattenable::Factory array[], int count) {
   *         fFactoryArray = array;
   *         fFactoryCount = count;
   *     }
   * ```
   */
  public fun setFactoryPlayback(array: Array<SkFlattenableFactory>, count: Int) {
    TODO("Implement setFactoryPlayback")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::setDeserialProcs(const SkDeserialProcs& procs) {
   *     fProcs = procs;
   *     this->setAllowSkSL(procs.fAllowSkSL);
   * }
   * ```
   */
  public fun setDeserialProcs(procs: SkDeserialProcs) {
    TODO("Implement setDeserialProcs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDeserialProcs& getDeserialProcs() const { return fProcs; }
   * ```
   */
  public fun getDeserialProcs(): SkDeserialProcs {
    TODO("Implement getDeserialProcs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowSkSL() const { return fAllowSkSL; }
   * ```
   */
  public fun allowSkSL(): Boolean {
    TODO("Implement allowSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAllowSkSL(bool allow) { fAllowSkSL = allow; }
   * ```
   */
  public fun setAllowSkSL(allow: Boolean) {
    TODO("Implement setAllowSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * bool validate(bool isValid) {
   *         if (!isValid) {
   *             this->setInvalid();
   *         }
   *         return !fError;
   *     }
   * ```
   */
  public fun validate(isValid: Boolean): Boolean {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool validateCanReadN(size_t n) {
   *         return this->validate(n <= (this->available() / sizeof(T)));
   *     }
   * ```
   */
  public fun <T> validateCanReadN(n: ULong): Boolean {
    TODO("Implement validateCanReadN")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return !fError; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool validateIndex(int index, int count) {
   *         return this->validate(index >= 0 && index < count);
   *     }
   * ```
   */
  public fun validateIndex(index: Int, count: Int): Boolean {
    TODO("Implement validateIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t SkReadBuffer::checkInt(int32_t min, int32_t max) {
   *     SkASSERT(min <= max);
   *     int32_t value = this->read32();
   *     if (value < min || value > max) {
   *         this->validate(false);
   *         value = min;
   *     }
   *     return value;
   * }
   * ```
   */
  public fun checkInt(min: Int, max: Int): Int {
    TODO("Implement checkInt")
  }

  /**
   * C++ original:
   * ```cpp
   * T checkRange(T min, T max) {
   *         return static_cast<T>(this->checkInt(static_cast<int32_t>(min),
   *                                              static_cast<int32_t>(max)));
   *     }
   * ```
   */
  public fun checkRange(min: Int, max: Int): Int {
    TODO("Implement checkRange")
  }

  /**
   * C++ original:
   * ```cpp
   * SkLegacyFQ SkReadBuffer::checkFilterQuality() {
   *     return this->checkRange<SkLegacyFQ>(kNone_SkLegacyFQ, kLast_SkLegacyFQ);
   * }
   * ```
   */
  public fun checkFilterQuality(): SkLegacyFQ {
    TODO("Implement checkFilterQuality")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions SkReadBuffer::readSampling() {
   *     if (!this->isVersionLT(SkPicturePriv::kAnisotropicFilter)) {
   *         int maxAniso = this->readInt();
   *         if (maxAniso != 0) {
   *             return SkSamplingOptions::Aniso(maxAniso);
   *         }
   *     }
   *     if (this->readBool()) {
   *         float B = this->readScalar();
   *         float C = this->readScalar();
   *         return SkSamplingOptions({B, C});
   *     } else {
   *         SkFilterMode filter = this->read32LE(SkFilterMode::kLinear);
   *         SkMipmapMode mipmap = this->read32LE(SkMipmapMode::kLinear);
   *         return SkSamplingOptions(filter, mipmap);
   *     }
   * }
   * ```
   */
  public fun readSampling(): SkSamplingOptions {
    TODO("Implement readSampling")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* SkReadBuffer::readString(size_t* len) {
   *     *len = this->readUInt();
   *
   *     // The string is len characters and a terminating \0.
   *     const char* c_str = this->skipT<char>(*len+1);
   *
   *     if (this->validate(c_str && c_str[*len] == '\0')) {
   *         return c_str;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  private fun readString(length: ULong?): Char {
    TODO("Implement readString")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkReadBuffer::setInvalid() {
   *     if (!fError) {
   *         // When an error is found, send the read cursor to the end of the stream
   *         fCurr = fStop;
   *         fError = true;
   *     }
   * }
   * ```
   */
  private fun setInvalid() {
    TODO("Implement setInvalid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkReadBuffer::readArray(void* value, size_t size, size_t elementSize) {
   *     const uint32_t count = this->readUInt();
   *     return this->validate(size == count) &&
   *            this->readPad32(value, SkSafeMath::Mul(size, elementSize));
   * }
   * ```
   */
  private fun readArray(
    `value`: Unit?,
    size: ULong,
    elementSize: ULong,
  ): Boolean {
    TODO("Implement readArray")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAvailable(size_t size) const { return size <= this->available(); }
   * ```
   */
  private fun isAvailable(size: ULong): Boolean {
    TODO("Implement isAvailable")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool IsPtrAlign4(const void* ptr) {
     *         return SkIsAlign4((uintptr_t)ptr);
     *     }
     * ```
     */
    private fun isPtrAlign4(ptr: Unit?): Boolean {
      TODO("Implement isPtrAlign4")
    }
  }
}

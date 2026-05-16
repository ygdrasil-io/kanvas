package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.Int
import kotlin.Unit
import org.skia.core.SkPMColor4f
import org.skia.core.SkSLType
import org.skia.foundation.SkSpan
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkSize
import org.skia.math.SkV2
import org.skia.math.SkV3
import org.skia.math.SkV4

/**
 * C++ original:
 * ```cpp
 * class UniformManager {
 * public:
 *     UniformManager(Layout layout) { this->resetWithNewLayout(layout); }
 *
 *     void markOffset() {
 *         fEndPaintOffset = fStorage.size();
 *         fEndPaintAlignment = fReqAlignment;
 *         SkDEBUGCODE(fMarkedOffsetCalculator = fOffsetCalculator;)
 *     }
 *
 *     void alignForNonShading(int requiredAlignment) {
 *         this->alignTo(requiredAlignment);
 *         fNonShadingOffset = fStorage.size();
 *         SkASSERT(SkIsPow2(requiredAlignment));
 *         fReqAlignment = std::max(fReqAlignment, requiredAlignment);
 *
 * #ifdef SK_DEBUG
 *         fOffsetCalculator = UniformOffsetCalculator::ForTopLevel(fLayout);
 *         fExpectedUniforms = {};
 *         fExpectedUniformIndex = 0;
 * #endif
 *         // If we're rewinding, we shouldn't be using substructs.
 *         SkASSERT(fSubstructStartingOffset == -1);
 *         // Any struct should be closed.
 *         SkASSERT(fStructBaseAlignment == 0);
 *     }
 *
 *     SkSpan<const char> finish(int subspanStart = 0) {
 *         this->alignTo(fReqAlignment);
 *         fStorageHighWaterMark = std::max(fStorageHighWaterMark,fStorage.size());
 *         return fStorage.empty() ?
 *                SkSpan<const char>() :
 *                SkSpan<const char>(fStorage).subspan(static_cast<size_t>(subspanStart));
 *     }
 *
 *     SkSpan<const char> finishMarked() {
 *         return this->finish(fNonShadingOffset);
 *     }
 *
 *     void resetWithNewLayout(Layout layout);
 *     void reset() { this->resetWithNewLayout(fLayout); }
 *     void rewindToMark();
 *
 *     int size() const { return fStorage.size(); }
 *
 *     void tryShrinkCapacity() {
 *         int halfCapacity = fStorage.capacity() / 2;
 *         if (fStorageHighWaterMark < halfCapacity) {
 *             fStorageHighWaterMark = 0;
 *             SkASSERT(fStorage.empty());
 *             fStorage.reserve_exact(halfCapacity);
 *         }
 *     }
 *
 *     // scalars
 *     void write(float f)     { this->write<SkSLType::kFloat>(&f); }
 *     void write(int32_t i)   { this->write<SkSLType::kInt  >(&i); }
 *     void writeHalf(float f) { this->write<SkSLType::kHalf >(&f); }
 *
 *     // [i|h]vec4 and arrays thereof (just add overloads as needed)
 *     void write(const SkPMColor4f& c) { this->write<SkSLType::kFloat4>(c.vec()); }
 *     void write(const SkRect& r)      { this->write<SkSLType::kFloat4>(r.asScalars()); }
 *     void write(const SkV4& v)        { this->write<SkSLType::kFloat4>(v.ptr()); }
 *
 *     void write(const SkIRect& r)     { this->write<SkSLType::kInt4>(&r); }
 *
 *     void writeHalf(const SkPMColor4f& c) { this->write<SkSLType::kHalf4>(c.vec()); }
 *     void writeHalf(const SkRect& r)      { this->write<SkSLType::kHalf4>(r.asScalars()); }
 *     void writeHalf(const SkV4& v)        { this->write<SkSLType::kHalf4>(v.ptr()); }
 *
 *     void writeArray(SkSpan<const SkV4> v) {
 *         this->writeArray<SkSLType::kFloat4>(v.data(), v.size());
 *     }
 *     void writeArray(SkSpan<const SkPMColor4f> c) {
 *         this->writeArray<SkSLType::kFloat4>(c.data(), c.size());
 *     }
 *     void writeHalfArray(SkSpan<const SkPMColor4f> c) {
 *         this->writeArray<SkSLType::kHalf4>(c.data(), c.size());
 *     }
 *
 *     // [i|h]vec3
 *     void write(const SkV3& v)     { this->write<SkSLType::kFloat3>(v.ptr()); }
 *     void write(const SkPoint3& p) { this->write<SkSLType::kFloat3>(&p); }
 *
 *     void writeHalf(const SkV3& v)     { this->write<SkSLType::kHalf3>(v.ptr()); }
 *     void writeHalf(const SkPoint3& p) { this->write<SkSLType::kHalf3>(&p); }
 *
 *     // NOTE: 3-element vectors never pack efficiently in arrays, so avoid using them
 *
 *     // [i|h]vec2
 *     void write(const SkV2& v)    { this->write<SkSLType::kFloat2>(v.ptr()); }
 *     void write(const SkSize& s)  { this->write<SkSLType::kFloat2>(&s); }
 *     void write(const SkPoint& p) { this->write<SkSLType::kFloat2>(&p); }
 *
 *     void write(const SkISize& s) { this->write<SkSLType::kInt2>(&s); }
 *
 *     void writeHalf(const SkV2& v)    { this->write<SkSLType::kHalf2>(v.ptr()); }
 *     void writeHalf(const SkSize& s)  { this->write<SkSLType::kHalf2>(&s); }
 *     void writeHalf(const SkPoint& p) { this->write<SkSLType::kHalf2>(&p); }
 *
 *     // NOTE: 2-element vectors don't pack efficiently in std140, so avoid using them
 *
 *     // matrices
 *     void write(const SkM44& m) {
 *         // All Layouts treat a 4x4 column-major matrix as an array of vec4's, which is exactly how
 *         // SkM44 already stores its data.
 *         this->writeArray<SkSLType::kFloat4>(SkMatrixPriv::M44ColMajor(m), 4);
 *     }
 *
 *     void writeHalf(const SkM44& m) {
 *         this->writeArray<SkSLType::kHalf4>(SkMatrixPriv::M44ColMajor(m), 4);
 *     }
 *
 *     void write(const SkMatrix& m) {
 *         // SkMatrix is row-major, so rewrite to column major. All Layouts treat a 3x3 column
 *         // major matrix as an array of vec3's.
 *         float colMajor[9] = {m[0], m[3], m[6],
 *                              m[1], m[4], m[7],
 *                              m[2], m[5], m[8]};
 *         this->writeArray<SkSLType::kFloat3>(colMajor, 3);
 *     }
 *     void writeHalf(const SkMatrix& m) {
 *         float colMajor[9] = {m[0], m[3], m[6],
 *                              m[1], m[4], m[7],
 *                              m[2], m[5], m[8]};
 *         this->writeArray<SkSLType::kHalf3>(colMajor, 3);
 *     }
 *
 *     // NOTE: 2x2 matrices can be manually packed the same or better as a vec4, so prefer that
 *
 *     // This is a specialized uniform writing entry point intended to deduplicate the paint
 *     // color. If a more general system is required, the deduping logic can be added to the
 *     // other write methods (and this specialized method would be removed).
 *     void writePaintColor(const SkPMColor4f& color) {
 *         if (fWrotePaintColor) {
 *             // Validate expected uniforms, but don't write a second copy since the paint color
 *             // uniform can only ever be declared once in the final SkSL program.
 *             SkDEBUGCODE(
 *                     this->checkExpected(/*dst=*/nullptr, SkSLType::kFloat4, Uniform::kNonArray));
 *         } else {
 *             this->write<SkSLType::kFloat4>(&color);
 *             fWrotePaintColor = true;
 *         }
 *     }
 *
 *     // Copy from `src` using Uniform array-count semantics.
 *     void write(const Uniform&, const void* src);
 *
 *     // UniformManager has basic support for writing substructs with the caveats:
 *     // 1. The base alignment of the substruct must be known a priori so the first member can be
 *     //    written immediately.
 *     // 2. Nested substructs are not supported (but could be if the padded-struct size was also
 *     //    provided to endStruct()).
 *     //
 *     // Call beginStruct(baseAlignment) before writing the first field. Then call the regular
 *     // write functions for each of the substruct's fields in order. Lastly, call endStruct() to
 *     // go back to writing fields in the top-level interface block.
 *     void beginStruct(int baseAlignment) {
 *         SkDEBUGCODE(this->checkBeginStruct(baseAlignment)); // verifies baseAlignment matches layout
 *
 *         this->alignTo(baseAlignment);
 *         fStructBaseAlignment = baseAlignment;
 *         fReqAlignment = std::max(fReqAlignment, baseAlignment);
 *     }
 *     void endStruct() {
 *         SkASSERT(fStructBaseAlignment >= 1); // Must have started a struct
 *         this->alignTo(fStructBaseAlignment);
 *         SkDEBUGCODE(this->checkEndStruct()); // validate after padding out to struct's alignment
 *         fStructBaseAlignment = 0;
 *     }
 *
 *     // Debug-only functions to control uniform expectations.
 * #ifdef SK_DEBUG
 *     bool isReset() const;
 *     void setExpectedUniforms(SkSpan<const Uniform> expected, bool isSubstruct);
 *     void doneWithExpectedUniforms();
 * #endif // SK_DEBUG
 *
 * private:
 *     // All public write() functions in UniformManager already match scalar/vector SkSLTypes or have
 *     // explicitly converted matrix SkSLTypes to a writeArray<column type> so this does not need to
 *     // check anything beyond half[2,3,4].
 *     static constexpr bool IsHalfVector(SkSLType type) {
 *         return type >= SkSLType::kHalf && type <= SkSLType::kHalf4;
 *     }
 *
 *     // Other than validation, actual layout doesn't care about 'type' and the logic can be
 *     // based on vector length and whether or not it's half or full precision.
 *     template <int N, bool Half> void write(const void* src, SkSLType type);
 *     template <int N, bool Half> void writeArray(const void* src, int count, SkSLType type);
 *
 *     // Helpers to select dimensionality and convert to full precision if required by the Layout.
 *     template <SkSLType Type> void write(const void* src) {
 *         static constexpr int N = SkSLTypeVecLength(Type);
 *         if (IsHalfVector(Type) && !LayoutRules::UseFullPrecision(fLayout)) {
 *             this->write<N, /*Half=*/true>(src, Type);
 *         } else {
 *             this->write<N, /*Half=*/false>(src, Type);
 *         }
 *     }
 *     template <SkSLType Type> void writeArray(const void* src, int count) {
 *         static constexpr int N = SkSLTypeVecLength(Type);
 *         if (IsHalfVector(Type) && !LayoutRules::UseFullPrecision(fLayout)) {
 *             this->writeArray<N, /*Half=*/true>(src, count, Type);
 *         } else {
 *             this->writeArray<N, /*Half=*/false>(src, count, Type);
 *         }
 *     }
 *
 *     // This is marked 'inline' so that it can be defined below with write() and writeArray() and
 *     // still link correctly.
 *     inline char* append(int alignment, int size);
 *     inline void alignTo(int alignment);
 *
 *     skia_private::TArray<char> fStorage;
 *     int fStorageHighWaterMark = 0;
 *
 *     Layout fLayout;
 *
 *     int fReqAlignment = 1;          // The proggresive alignment as we process uniforms
 *     int fEndPaintAlignment = 1;     // The alignment at the end of the paint uniforms
 *     int fStructBaseAlignment = 0;   // The base alignment of a struct.
 *
 *     int fEndPaintOffset = 0;        // The unaligned size of the paint uniforms
 *     int fNonShadingOffset = 0;      // The aligned start of non-shading renderstep uniforms
 *
 *     bool fWrotePaintColor = false;  // The paint only adds its uniform once.
 *
 *     // Debug-only verification that UniformOffsetCalculator is consistent and that write() calls
 *     // match the expected uniform declaration order.
 * #ifdef SK_DEBUG
 *     UniformOffsetCalculator fOffsetCalculator;       // should match implicit offsets from append()
 *     UniformOffsetCalculator fMarkedOffsetCalculator; // store the offset calculator at rewind
 *     UniformOffsetCalculator fSubstructCalculator;    // 0-based, used when inside a substruct
 *     int fSubstructStartingOffset = -1;               // offset of first field in fOffsetCalculator
 *
 *     SkSpan<const Uniform> fExpectedUniforms;
 *     int fExpectedUniformIndex = 0;
 *
 *     void checkExpected(const void* dst, SkSLType, int count);
 *     void checkBeginStruct(int baseAlignment);
 *     void checkEndStruct();
 * #endif // SK_DEBUG
 * }
 * ```
 */
public abstract class UniformManager public constructor(
  layout: Layout,
) {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<char> fStorage
   * ```
   */
  private var fStorage: Int = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * int fStorageHighWaterMark = 0
   * ```
   */
  private var fStorageHighWaterMark: Int = TODO("Initialize fStorageHighWaterMark")

  /**
   * C++ original:
   * ```cpp
   * Layout fLayout
   * ```
   */
  private var fLayout: Int = TODO("Initialize fLayout")

  /**
   * C++ original:
   * ```cpp
   * int fReqAlignment = 1
   * ```
   */
  private var fReqAlignment: Int = TODO("Initialize fReqAlignment")

  /**
   * C++ original:
   * ```cpp
   * int fEndPaintAlignment = 1
   * ```
   */
  private var fEndPaintAlignment: Int = TODO("Initialize fEndPaintAlignment")

  /**
   * C++ original:
   * ```cpp
   * int fStructBaseAlignment = 0
   * ```
   */
  private var fStructBaseAlignment: Int = TODO("Initialize fStructBaseAlignment")

  /**
   * C++ original:
   * ```cpp
   * int fEndPaintOffset = 0
   * ```
   */
  private var fEndPaintOffset: Int = TODO("Initialize fEndPaintOffset")

  /**
   * C++ original:
   * ```cpp
   * int fNonShadingOffset = 0
   * ```
   */
  private var fNonShadingOffset: Int = TODO("Initialize fNonShadingOffset")

  /**
   * C++ original:
   * ```cpp
   * bool fWrotePaintColor = false
   * ```
   */
  private var fWrotePaintColor: Boolean = TODO("Initialize fWrotePaintColor")

  /**
   * C++ original:
   * ```cpp
   * void markOffset() {
   *         fEndPaintOffset = fStorage.size();
   *         fEndPaintAlignment = fReqAlignment;
   *         SkDEBUGCODE(fMarkedOffsetCalculator = fOffsetCalculator;)
   *     }
   * ```
   */
  public fun markOffset() {
    TODO("Implement markOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * void alignForNonShading(int requiredAlignment) {
   *         this->alignTo(requiredAlignment);
   *         fNonShadingOffset = fStorage.size();
   *         SkASSERT(SkIsPow2(requiredAlignment));
   *         fReqAlignment = std::max(fReqAlignment, requiredAlignment);
   *
   * #ifdef SK_DEBUG
   *         fOffsetCalculator = UniformOffsetCalculator::ForTopLevel(fLayout);
   *         fExpectedUniforms = {};
   *         fExpectedUniformIndex = 0;
   * #endif
   *         // If we're rewinding, we shouldn't be using substructs.
   *         SkASSERT(fSubstructStartingOffset == -1);
   *         // Any struct should be closed.
   *         SkASSERT(fStructBaseAlignment == 0);
   *     }
   * ```
   */
  public fun alignForNonShading(requiredAlignment: Int) {
    TODO("Implement alignForNonShading")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const char> finish(int subspanStart = 0) {
   *         this->alignTo(fReqAlignment);
   *         fStorageHighWaterMark = std::max(fStorageHighWaterMark,fStorage.size());
   *         return fStorage.empty() ?
   *                SkSpan<const char>() :
   *                SkSpan<const char>(fStorage).subspan(static_cast<size_t>(subspanStart));
   *     }
   * ```
   */
  public abstract fun finish(subspanStart: Int = TODO()): Int

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const char> finishMarked() {
   *         return this->finish(fNonShadingOffset);
   *     }
   * ```
   */
  public fun finishMarked(): Int {
    TODO("Implement finishMarked")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::resetWithNewLayout(Layout layout) {
   *     fStorage.clear();
   *     fLayout = layout;
   *     fReqAlignment = 1;
   *     fEndPaintAlignment = 1;
   *     fEndPaintOffset = 0;
   *     fNonShadingOffset = 0;
   *     fStructBaseAlignment = 0;
   *     fWrotePaintColor = false;
   *
   * #ifdef SK_DEBUG
   *     fOffsetCalculator = UniformOffsetCalculator::ForTopLevel(layout);
   *     fMarkedOffsetCalculator = fOffsetCalculator;
   *     fSubstructCalculator = {};
   *     fExpectedUniforms = {};
   *     fExpectedUniformIndex = 0;
   * #endif
   * }
   * ```
   */
  public fun resetWithNewLayout(layout: Layout) {
    TODO("Implement resetWithNewLayout")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() { this->resetWithNewLayout(fLayout); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::rewindToMark() {
   *     // Prepare the storage paramters such that:
   *     //  1) If the renderstep is shading, the size and alignment can grow directly from the state at
   *     //  at the end of the paint uniforms.
   *     //  2) If the renderstep is non-shading, the storage can be aligned to the renderstep's
   *     //  uniform alignment requirements.
   *     fStorage.resize(fEndPaintOffset);
   *     fReqAlignment = fEndPaintAlignment;
   *     fNonShadingOffset = 0;
   *     SkDEBUGCODE(fOffsetCalculator = fMarkedOffsetCalculator);
   *
   *     // If we're rewinding, we shouldn't be using substructs.
   *     SkASSERT(fSubstructStartingOffset == -1);
   *     // Any struct should be closed.
   *     SkASSERT(fStructBaseAlignment == 0);
   * }
   * ```
   */
  public fun rewindToMark() {
    TODO("Implement rewindToMark")
  }

  /**
   * C++ original:
   * ```cpp
   * int size() const { return fStorage.size(); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * void tryShrinkCapacity() {
   *         int halfCapacity = fStorage.capacity() / 2;
   *         if (fStorageHighWaterMark < halfCapacity) {
   *             fStorageHighWaterMark = 0;
   *             SkASSERT(fStorage.empty());
   *             fStorage.reserve_exact(halfCapacity);
   *         }
   *     }
   * ```
   */
  public fun tryShrinkCapacity() {
    TODO("Implement tryShrinkCapacity")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(float f)     { this->write<SkSLType::kFloat>(&f); }
   * ```
   */
  public fun write(f: Float) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(int32_t i)   { this->write<SkSLType::kInt  >(&i); }
   * ```
   */
  public fun write(i: Int) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(float f) { this->write<SkSLType::kHalf >(&f); }
   * ```
   */
  public fun writeHalf(f: Float) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkPMColor4f& c) { this->write<SkSLType::kFloat4>(c.vec()); }
   * ```
   */
  public fun write(c: SkPMColor4f) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkRect& r)      { this->write<SkSLType::kFloat4>(r.asScalars()); }
   * ```
   */
  public fun write(r: SkRect) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkV4& v)        { this->write<SkSLType::kFloat4>(v.ptr()); }
   * ```
   */
  public fun write(v: SkV4) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkIRect& r)     { this->write<SkSLType::kInt4>(&r); }
   * ```
   */
  public fun write(r: SkIRect) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkPMColor4f& c) { this->write<SkSLType::kHalf4>(c.vec()); }
   * ```
   */
  public fun writeHalf(c: SkPMColor4f) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkRect& r)      { this->write<SkSLType::kHalf4>(r.asScalars()); }
   * ```
   */
  public fun writeHalf(r: SkRect) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkV4& v)        { this->write<SkSLType::kHalf4>(v.ptr()); }
   * ```
   */
  public fun writeHalf(v: SkV4) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeArray(SkSpan<const SkV4> v) {
   *         this->writeArray<SkSLType::kFloat4>(v.data(), v.size());
   *     }
   * ```
   */
  public fun writeArray(v: SkSpan<SkV4>) {
    TODO("Implement writeArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeArray(SkSpan<const SkPMColor4f> c) {
   *         this->writeArray<SkSLType::kFloat4>(c.data(), c.size());
   *     }
   * ```
   */
  public fun writeArray(c: SkSpan<SkPMColor4f>) {
    TODO("Implement writeArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalfArray(SkSpan<const SkPMColor4f> c) {
   *         this->writeArray<SkSLType::kHalf4>(c.data(), c.size());
   *     }
   * ```
   */
  public fun writeHalfArray(c: SkSpan<SkPMColor4f>) {
    TODO("Implement writeHalfArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkV3& v)     { this->write<SkSLType::kFloat3>(v.ptr()); }
   * ```
   */
  public fun write(v: SkV3) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkPoint3& p) { this->write<SkSLType::kFloat3>(&p); }
   * ```
   */
  public fun write(p: SkPoint3) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkV3& v)     { this->write<SkSLType::kHalf3>(v.ptr()); }
   * ```
   */
  public fun writeHalf(v: SkV3) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkPoint3& p) { this->write<SkSLType::kHalf3>(&p); }
   * ```
   */
  public fun writeHalf(p: SkPoint3) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkV2& v)    { this->write<SkSLType::kFloat2>(v.ptr()); }
   * ```
   */
  public fun write(v: SkV2) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkSize& s)  { this->write<SkSLType::kFloat2>(&s); }
   * ```
   */
  public fun write(s: SkSize) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkPoint& p) { this->write<SkSLType::kFloat2>(&p); }
   * ```
   */
  public fun write(p: SkPoint) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkISize& s) { this->write<SkSLType::kInt2>(&s); }
   * ```
   */
  public fun write(s: SkISize) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkV2& v)    { this->write<SkSLType::kHalf2>(v.ptr()); }
   * ```
   */
  public fun writeHalf(v: SkV2) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkSize& s)  { this->write<SkSLType::kHalf2>(&s); }
   * ```
   */
  public fun writeHalf(s: SkSize) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkPoint& p) { this->write<SkSLType::kHalf2>(&p); }
   * ```
   */
  public fun writeHalf(p: SkPoint) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkM44& m) {
   *         // All Layouts treat a 4x4 column-major matrix as an array of vec4's, which is exactly how
   *         // SkM44 already stores its data.
   *         this->writeArray<SkSLType::kFloat4>(SkMatrixPriv::M44ColMajor(m), 4);
   *     }
   * ```
   */
  public fun write(m: SkM44) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkM44& m) {
   *         this->writeArray<SkSLType::kHalf4>(SkMatrixPriv::M44ColMajor(m), 4);
   *     }
   * ```
   */
  public fun writeHalf(m: SkM44) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const SkMatrix& m) {
   *         // SkMatrix is row-major, so rewrite to column major. All Layouts treat a 3x3 column
   *         // major matrix as an array of vec3's.
   *         float colMajor[9] = {m[0], m[3], m[6],
   *                              m[1], m[4], m[7],
   *                              m[2], m[5], m[8]};
   *         this->writeArray<SkSLType::kFloat3>(colMajor, 3);
   *     }
   * ```
   */
  public fun write(m: SkMatrix) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const SkMatrix& m) {
   *         float colMajor[9] = {m[0], m[3], m[6],
   *                              m[1], m[4], m[7],
   *                              m[2], m[5], m[8]};
   *         this->writeArray<SkSLType::kHalf3>(colMajor, 3);
   *     }
   * ```
   */
  public fun writeHalf(m: SkMatrix) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePaintColor(const SkPMColor4f& color) {
   *         if (fWrotePaintColor) {
   *             // Validate expected uniforms, but don't write a second copy since the paint color
   *             // uniform can only ever be declared once in the final SkSL program.
   *             SkDEBUGCODE(
   *                     this->checkExpected(/*dst=*/nullptr, SkSLType::kFloat4, Uniform::kNonArray));
   *         } else {
   *             this->write<SkSLType::kFloat4>(&color);
   *             fWrotePaintColor = true;
   *         }
   *     }
   * ```
   */
  public fun writePaintColor(color: SkPMColor4f) {
    TODO("Implement writePaintColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const Uniform&, const void* src)
   * ```
   */
  public fun write(param0: Uniform, src: Unit?) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void beginStruct(int baseAlignment) {
   *         SkDEBUGCODE(this->checkBeginStruct(baseAlignment)); // verifies baseAlignment matches layout
   *
   *         this->alignTo(baseAlignment);
   *         fStructBaseAlignment = baseAlignment;
   *         fReqAlignment = std::max(fReqAlignment, baseAlignment);
   *     }
   * ```
   */
  public fun beginStruct(baseAlignment: Int) {
    TODO("Implement beginStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * void endStruct() {
   *         SkASSERT(fStructBaseAlignment >= 1); // Must have started a struct
   *         this->alignTo(fStructBaseAlignment);
   *         SkDEBUGCODE(this->checkEndStruct()); // validate after padding out to struct's alignment
   *         fStructBaseAlignment = 0;
   *     }
   * ```
   */
  public fun endStruct() {
    TODO("Implement endStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * template<int N, bool Half>
   * void UniformManager::write(const void* src, SkSLType type) {
   *     using L = LayoutTraits<N, Half>;
   *     SkDEBUGCODE(L::Validate(src, type, fLayout);)
   *
   *     // Layouts diverge in how vec3 size is determined for non-array usage
   *     char* dst = (N == 3 && LayoutRules::PadVec3Size(fLayout))
   *             ? this->append(L::kAlign, L::kSize + L::kElemSize)
   *             : this->append(L::kAlign, L::kSize);
   *     SkDEBUGCODE(this->checkExpected(dst, type, Uniform::kNonArray));
   *
   *     L::Copy(src, dst);
   *     if (N == 3 && LayoutRules::PadVec3Size(fLayout)) {
   *         memset(dst + L::kSize, 0, L::kElemSize);
   *     }
   * }
   * ```
   */
  private fun write(src: Unit?, type: SkSLType) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * template<int N, bool Half>
   * void UniformManager::writeArray(const void* src, int count, SkSLType type) {
   *     using L = LayoutTraits<N, Half>;
   *     static constexpr int kSrcStride = N * 4; // Source data is always in multiples of 4 bytes.
   *
   *     SkDEBUGCODE(L::Validate(src, type, fLayout);)
   *     SkASSERT(count > 0);
   *
   *     if (Half || N == 3 || (N != 4 && LayoutRules::AlignArraysAsVec4(fLayout))) {
   *         // A non-dense array (N == 3 is always padded to vec4, or the Layout requires it),
   *         // or we have to perform half conversion so iterate over each element.
   *         static constexpr int kStride  = Half ? L::kAlign : 4*L::kElemSize;
   *         SkASSERT(!(Half && LayoutRules::AlignArraysAsVec4(fLayout))); // should be exclusive
   *
   *         const char* srcBytes = reinterpret_cast<const char*>(src);
   *         char* dst = this->append(kStride, kStride*count);
   *         SkDEBUGCODE(this->checkExpected(dst, type, count));
   *
   *         for (int i = 0; i < count; ++i) {
   *             L::Copy(srcBytes, dst);
   *             if constexpr (kStride - L::kSize > 0) {
   *                 memset(dst + L::kSize, 0, kStride - L::kSize);
   *             }
   *
   *             dst += kStride;
   *             srcBytes += kSrcStride;
   *         }
   *     } else {
   *         // A dense array with no type conversion, so copy in one go.
   *         SkASSERT(L::kAlign == L::kSize && kSrcStride == L::kSize);
   *         char* dst = this->append(L::kAlign, L::kSize*count);
   *         SkDEBUGCODE(this->checkExpected(dst, type, count));
   *
   *         memcpy(dst, src, L::kSize*count);
   *     }
   * }
   * ```
   */
  private fun writeArray(
    src: Unit?,
    count: Int,
    type: SkSLType,
  ) {
    TODO("Implement writeArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const void* src) {
   *         static constexpr int N = SkSLTypeVecLength(Type);
   *         if (IsHalfVector(Type) && !LayoutRules::UseFullPrecision(fLayout)) {
   *             this->write<N, /*Half=*/true>(src, Type);
   *         } else {
   *             this->write<N, /*Half=*/false>(src, Type);
   *         }
   *     }
   * ```
   */
  private fun write(src: Unit?) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeArray(const void* src, int count) {
   *         static constexpr int N = SkSLTypeVecLength(Type);
   *         if (IsHalfVector(Type) && !LayoutRules::UseFullPrecision(fLayout)) {
   *             this->writeArray<N, /*Half=*/true>(src, count, Type);
   *         } else {
   *             this->writeArray<N, /*Half=*/false>(src, count, Type);
   *         }
   *     }
   * ```
   */
  private fun writeArray(src: Unit?, count: Int) {
    TODO("Implement writeArray")
  }

  /**
   * C++ original:
   * ```cpp
   * char* UniformManager::append(int alignment, int size) {
   *     // The base alignment for a struct should have been calculated for the current layout using
   *     // UniformOffsetCalculator, so every field appended within the struct should have an alignment
   *     // less than or equal to that base alignment.
   *     SkASSERT(fStructBaseAlignment <= 0 || alignment <= fStructBaseAlignment);
   *
   *     const int offset = fStorage.size();
   *     const int padding = SkAlignTo(offset, alignment) - offset;
   *
   *     // These are just asserts not aborts because SkSL compilation imposes limits on the size of
   *     // runtime effect arrays, and internal shaders should not be using excessive lengths.
   *     SkASSERT(std::numeric_limits<int>::max() - alignment >= offset);
   *     SkASSERT(std::numeric_limits<int>::max() - size >= padding);
   *
   *     char* dst = fStorage.push_back_n(size + padding);
   *     if (padding > 0) {
   *         memset(dst, 0, padding);
   *         dst += padding;
   *     }
   *
   *     // For pow of 2, max is LCM. If that assumption changes, this should change as well.
   *     fReqAlignment = std::max(fReqAlignment, alignment);
   *     return dst;
   * }
   * ```
   */
  private fun append(alignment: Int, size: Int): Char {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::alignTo(int alignment) {
   *     SkASSERT(alignment >= 1 && SkIsPow2(alignment));
   *     if ((fStorage.size() & (alignment - 1)) != 0) {
   *         this->append(alignment, /*size=*/0);
   *     }
   * }
   * ```
   */
  private fun alignTo(alignment: Int) {
    TODO("Implement alignTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::write(const Uniform& u, const void* data) {
   *     SkASSERT(SkSLTypeCanBeUniformValue(u.type()));
   *     SkASSERT(!u.isPaintColor()); // Must go through writePaintColor()
   *
   *     auto [type, count] = adjust_for_matrix_type(u.type(), u.count());
   *     SkASSERT(SkSLTypeMatrixSize(type) < 0); // Matrix types should have been flattened
   *
   *     const bool fullPrecision = LayoutRules::UseFullPrecision(fLayout) || !IsHalfVector(type);
   *     if (count == Uniform::kNonArray) {
   *         if (fullPrecision) {
   *             switch(SkSLTypeVecLength(type)) {
   *                 case 1: this->write<1, /*Half=*/false>(data, type); break;
   *                 case 2: this->write<2, /*Half=*/false>(data, type); break;
   *                 case 3: this->write<3, /*Half=*/false>(data, type); break;
   *                 case 4: this->write<4, /*Half=*/false>(data, type); break;
   *             }
   *         } else {
   *             switch(SkSLTypeVecLength(type)) {
   *                 case 1: this->write<1, /*Half=*/true>(data, type); break;
   *                 case 2: this->write<2, /*Half=*/true>(data, type); break;
   *                 case 3: this->write<3, /*Half=*/true>(data, type); break;
   *                 case 4: this->write<4, /*Half=*/true>(data, type); break;
   *             }
   *         }
   *     } else {
   *         if (fullPrecision) {
   *             switch(SkSLTypeVecLength(type)) {
   *                 case 1: this->writeArray<1, /*Half=*/false>(data, count, type); break;
   *                 case 2: this->writeArray<2, /*Half=*/false>(data, count, type); break;
   *                 case 3: this->writeArray<3, /*Half=*/false>(data, count, type); break;
   *                 case 4: this->writeArray<4, /*Half=*/false>(data, count, type); break;
   *             }
   *         } else {
   *             switch(SkSLTypeVecLength(type)) {
   *                 case 1: this->writeArray<1, /*Half=*/true>(data, count, type); break;
   *                 case 2: this->writeArray<2, /*Half=*/true>(data, count, type); break;
   *                 case 3: this->writeArray<3, /*Half=*/true>(data, count, type); break;
   *                 case 4: this->writeArray<4, /*Half=*/true>(data, count, type); break;
   *             }
   *         }
   *     }
   * }
   * ```
   */
  public fun checkBeginStruct(baseAlignment: Int) {
    TODO("Implement checkBeginStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::checkBeginStruct(int baseAlignment) {
   *     // Wrote a struct field before the struct was started
   *     SkASSERT(fExpectedUniformIndex == 0);
   *
   *     // Not expecting to start a struct (layout must be valid)
   *     SkASSERT(fSubstructCalculator.layout() != Layout::kInvalid);
   *
   *     // Somehow already started a substruct (base alignment should be <= 0 initially)
   *     SkASSERT(fStructBaseAlignment <= 0);
   *
   *     // Empty substructs are not allowed
   *     SkASSERT(!fExpectedUniforms.empty());
   *
   *     // Assume the expected uniforms describe the whole substruct
   *     auto structCalculator = UniformOffsetCalculator::ForStruct(fLayout);
   *     for (const Uniform& f : fExpectedUniforms) {
   *         structCalculator.advanceOffset(f.type(), f.count());
   *     }
   *
   *     // Calculated alignment must match the passed base alignment
   *     SkASSERT(baseAlignment == structCalculator.requiredAlignment());
   *
   *     fSubstructStartingOffset = fOffsetCalculator.advanceStruct(structCalculator);
   * }
   * ```
   */
  public fun checkEndStruct() {
    TODO("Implement checkEndStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::checkEndStruct() {
   *     // Didn't write all the expected fields before ending the struct
   *     SkASSERT(fExpectedUniformIndex == (int)fExpectedUniforms.size());
   *
   *     // Not expecting a struct (layout must be valid)
   *     SkASSERT(fSubstructCalculator.layout() != Layout::kInvalid);
   *
   *     // Missing a beginStruct() (base alignment must be > 0 if we are in a struct)
   *     SkASSERT(fStructBaseAlignment > 0);
   *
   *     // `fStructCalculator` should now have been advanced equivalently to the substruct calculator
   *     // used in checkBeginStruct() to calculate the expected starting offset.
   *     const int structSize = SkAlignTo(fSubstructCalculator.size(),
   *                                      fSubstructCalculator.requiredAlignment());
   *
   *     // Somehow didn't end on the correct boundary
   *     SkASSERT(fStorage.size() == fSubstructStartingOffset + structSize);
   *
   *     // UniformManager's alignment got out of sync with expected alignment
   *     SkASSERT(fReqAlignment == fOffsetCalculator.requiredAlignment());
   *     SkASSERT(fReqAlignment >= fSubstructCalculator.requiredAlignment());
   *
   *     // Reset the substruct calculator to mark that the struct has been completed
   *     fSubstructCalculator = {};
   * }
   * ```
   */
  public fun checkExpected(
    dst: Unit?,
    type: SkSLType,
    count: Int,
  ) {
    TODO("Implement checkExpected")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::checkExpected(const void* dst, SkSLType type, int count) {
   *     // A write() outside of a UniformExpectationsVisitor or too many uniforms written for what
   *     // is expected.
   *     SkASSERT(fExpectedUniformIndex < SkTo<int>(fExpectedUniforms.size()));
   *
   *     if (fSubstructCalculator.layout() != Layout::kInvalid) {
   *         // A write() that should be inside a struct, but missing a call to beginStruct()
   *         SkASSERT(fStructBaseAlignment > 0);
   *     } else {
   *         // A substruct was started when it shouldn't have been.
   *         SkASSERT(fStructBaseAlignment <= 0);
   *     }
   *
   *     const Uniform& expected = fExpectedUniforms[fExpectedUniformIndex++];
   *     // Not all types are supported as uniforms or supported by UniformManager
   *     SkASSERT(SkSLTypeCanBeUniformValue(expected.type()));
   *
   *     auto [expectedType, expectedCount] = adjust_for_matrix_type(expected.type(), expected.count());
   *     SkASSERT(expectedType == type && expectedCount == count);
   *
   *     if (dst) {
   *         // If we have 'dst', it's the aligned starting offset of the uniform being checked, so
   *         // subtracting the address of the first byte in fStorage gives us the offset.
   *         int offset = static_cast<int>(reinterpret_cast<intptr_t>(dst) -
   *                                       reinterpret_cast<intptr_t>(fStorage.data()) -
   *                                       fNonShadingOffset);
   *
   *         if (fSubstructCalculator.layout() == Layout::kInvalid) {
   *             // Pass original expected type and count to the offset calculator for validation.
   *             SkASSERT(offset == fOffsetCalculator.advanceOffset(expected.type(), expected.count()));
   *             SkASSERT(fReqAlignment == fOffsetCalculator.requiredAlignment());
   *
   *             // And if it is the paint color uniform, we should not have already written it.
   *             SkASSERT(!(fWrotePaintColor && expected.isPaintColor()));
   *         } else {
   *             int relOffset = fSubstructCalculator.advanceOffset(expected.type(), expected.count());
   *             SkASSERT(offset == fSubstructStartingOffset + relOffset);
   *
   *             // The overall required alignment might already be higher from prior fields, but should
   *             // be at least what's required by the substruct.
   *             SkASSERT(fReqAlignment >= fSubstructCalculator.requiredAlignment());
   *
   *             // And it should not be a paint color uniform within a substruct.
   *             SkASSERT(!expected.isPaintColor());
   *         }
   *     } else {
   *         // If 'dst' is null, it's an already-visited paint color uniform, so it's not being written
   *         // and not changing the offset, and should not be part of a substruct.
   *         SkASSERT(fWrotePaintColor);
   *         SkASSERT(fSubstructCalculator.layout() == Layout::kInvalid);
   *         SkASSERT(expected.isPaintColor());
   *     }
   * }
   * ```
   */
  public fun isReset(): Boolean {
    TODO("Implement isReset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool UniformManager::isReset() const {
   *     return fStorage.empty();
   * }
   * ```
   */
  public fun setExpectedUniforms(expected: SkSpan<Uniform>, isSubstruct: Boolean) {
    TODO("Implement setExpectedUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * void UniformManager::setExpectedUniforms(SkSpan<const Uniform> expected, bool isSubstruct) {
   *     fExpectedUniforms = expected;
   *     fExpectedUniformIndex = 0;
   *
   *     if (isSubstruct) {
   *         // Start collecting the subsequent uniforms with a 0-based offset to determine their
   *         // relative layout and required base alignment of the entire struct.
   *         fSubstructCalculator = UniformOffsetCalculator::ForStruct(fLayout);
   *     } else {
   *         // Expected uniforms will advance fOffsetCalculator directly
   *         SkASSERT(fSubstructCalculator.layout() == Layout::kInvalid);
   *     }
   * }
   * ```
   */
  public fun doneWithExpectedUniforms() {
    TODO("Implement doneWithExpectedUniforms")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr bool IsHalfVector(SkSLType type) {
     *         return type >= SkSLType::kHalf && type <= SkSLType::kHalf4;
     *     }
     * ```
     */
    private fun isHalfVector(type: SkSLType): Boolean {
      TODO("Implement isHalfVector")
    }
  }
}

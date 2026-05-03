package org.skia.tools

import kotlin.Int
import org.skia.core.GlyphRunList
import org.skia.core.SkRefCntSet
import org.skia.foundation.SkBinaryWriteBuffer
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class Capture {
 * public:
 *     Capture();
 *     ~Capture();
 *     void capture(const sktext::GlyphRunList&, const SkPaint&);
 *     // If `dst` is nullptr, write to a file.
 *     void dump(SkWStream* dst = nullptr) const;
 *
 * private:
 *     size_t fBlobCount = 0;
 *     sk_sp<SkRefCntSet> fTypefaceSet;
 *     SkBinaryWriteBuffer fWriteBuffer;
 *
 *     Capture(const Capture&) = delete;
 *     Capture& operator=(const Capture&) = delete;
 * }
 * ```
 */
public data class Capture public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t fBlobCount
   * ```
   */
  private var fBlobCount: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRefCntSet> fTypefaceSet
   * ```
   */
  private var fTypefaceSet: SkSp<SkRefCntSet>,
  /**
   * C++ original:
   * ```cpp
   * SkBinaryWriteBuffer fWriteBuffer
   * ```
   */
  private var fWriteBuffer: SkBinaryWriteBuffer,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobTrace::Capture::capture(
   *         const sktext::GlyphRunList& glyphRunList, const SkPaint& paint) {
   *     const SkTextBlob* blob = glyphRunList.blob();
   *     if (blob != nullptr) {
   *         fWriteBuffer.writeUInt(blob->uniqueID());
   *         fWriteBuffer.writePaint(paint);
   *         fWriteBuffer.writePoint(glyphRunList.origin());
   *         SkTextBlobPriv::Flatten(*blob, fWriteBuffer);
   *         fBlobCount++;
   *     }
   * }
   * ```
   */
  public fun capture(glyphRunList: GlyphRunList, paint: SkPaint) {
    TODO("Implement capture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobTrace::Capture::dump(SkWStream* dst) const {
   *     SkTLazy<SkFILEWStream> fileStream;
   *     if (!dst) {
   *         uint32_t id = SkChecksum::Mix(reinterpret_cast<uintptr_t>(this));
   *         SkString f = SkStringPrintf("diff-canvas-%08x-%04zu.trace", id, fBlobCount);
   *         dst = fileStream.init(f.c_str());
   *         if (!fileStream->isValid()) {
   *             SkDebugf("Error opening '%s'.\n", f.c_str());
   *             return;
   *         }
   *         SkDebugf("Saving trace to '%s'.\n", f.c_str());
   *     }
   *     SkASSERT(dst);
   *     int count = fTypefaceSet->count();
   *     dst->write32(count);
   *     SkPtrSet::Iter iter(*fTypefaceSet);
   *     while (void* ptr = iter.next()) {
   *         ((const SkTypeface*)ptr)->serialize(dst, SkTypeface::SerializeBehavior::kDoIncludeData);
   *     }
   *     dst->write32(fWriteBuffer.bytesWritten());
   *     fWriteBuffer.writeToStream(dst);
   * }
   * ```
   */
  public fun dump(dst: SkWStream? = TODO()) {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * Capture& operator=(const Capture&) = delete
   * ```
   */
  private fun assign(param0: Capture) {
    TODO("Implement assign")
  }
}

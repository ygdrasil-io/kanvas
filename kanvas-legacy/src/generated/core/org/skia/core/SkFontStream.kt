package org.skia.core

import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkFontTableTag
import org.skia.foundation.SkSpan
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class SkFontStream {
 * public:
 *     /**
 *      *  Return the number of shared directories inside a TTC sfnt, or return 1
 *      *  if the stream is a normal sfnt (ttf). If there is an error or
 *      *  no directory is found, return 0.
 *      *
 *      *  Note: the stream is rewound initially, but is returned at an arbitrary
 *      *  read offset.
 *      */
 *     static int CountTTCEntries(SkStream*);
 *
 *     /**
 *      *  @param ttcIndex 0 for normal sfnts, or the index within a TTC sfnt.
 *      *
 *      *  Note: the stream is rewound initially, but is returned at an arbitrary
 *      *  read offset.
 *      */
 *     static int GetTableTags(SkStream*, int ttcIndex, SkSpan<SkFontTableTag> tags);
 *
 *     /**
 *      *  @param ttcIndex 0 for normal sfnts, or the index within a TTC sfnt.
 *      *
 *      *  Note: the stream is rewound initially, but is returned at an arbitrary
 *      *  read offset.
 *      */
 *     static size_t GetTableData(SkStream*, int ttcIndex, SkFontTableTag tag,
 *                                size_t offset, size_t length, void* data);
 *
 *     static size_t GetTableSize(SkStream* stream, int ttcIndex, SkFontTableTag tag) {
 *         return GetTableData(stream, ttcIndex, tag, 0, ~0U, nullptr);
 *     }
 * }
 * ```
 */
public open class SkFontStream {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * int SkFontStream::CountTTCEntries(SkStream* stream) {
     *     stream->rewind();
     *
     *     SkSharedTTHeader shared;
     *     if (!read(stream, &shared, sizeof(shared))) {
     *         return 0;
     *     }
     *
     *     // if we're really a collection, the first 4-bytes will be 'ttcf'
     *     uint32_t tag = SkEndian_SwapBE32(shared.fCollection.fTag);
     *     if (SkSetFourByteTag('t', 't', 'c', 'f') == tag) {
     *         return SkEndian_SwapBE32(shared.fCollection.fNumOffsets);
     *     } else {
     *         return 1;   // normal 'sfnt' has 1 dir entry
     *     }
     * }
     * ```
     */
    public fun countTTCEntries(stream: SkStream?): Int {
      TODO("Implement countTTCEntries")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkFontStream::GetTableTags(SkStream* stream, int ttcIndex, SkSpan<SkFontTableTag> tags) {
     *     SfntHeader  header;
     *     if (!header.init(stream, ttcIndex)) {
     *         return 0;
     *     }
     *
     *     const size_t n = std::min((size_t)header.fCount, tags.size());
     *     for (size_t i = 0; i < n; i++) {
     *         tags[i] = SkEndian_SwapBE32(header.fDir[i].fTag);
     *     }
     *     return header.fCount;
     * }
     * ```
     */
    public fun getTableTags(
      stream: SkStream?,
      ttcIndex: Int,
      tags: SkSpan<SkFontTableTag>,
    ): Int {
      TODO("Implement getTableTags")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkFontStream::GetTableData(SkStream* stream, int ttcIndex,
     *                                   SkFontTableTag tag,
     *                                   size_t offset, size_t length, void* data) {
     *     SfntHeader  header;
     *     if (!header.init(stream, ttcIndex)) {
     *         return 0;
     *     }
     *
     *     for (int i = 0; i < header.fCount; i++) {
     *         if (SkEndian_SwapBE32(header.fDir[i].fTag) == tag) {
     *             size_t realOffset = SkEndian_SwapBE32(header.fDir[i].fOffset);
     *             size_t realLength = SkEndian_SwapBE32(header.fDir[i].fLength);
     *             if (offset >= realLength) {
     *                 // invalid
     *                 return 0;
     *             }
     *             // if the caller is trusting the length from the file, then a
     *             // hostile file might choose a value which would overflow offset +
     *             // length.
     *             if (offset + length < offset) {
     *                 return 0;
     *             }
     *             if (length > realLength - offset) {
     *                 length = realLength - offset;
     *             }
     *             if (data) {
     *                 // skip the stream to the part of the table we want to copy from
     *                 stream->rewind();
     *                 size_t bytesToSkip = realOffset + offset;
     *                 if (!skip(stream, bytesToSkip)) {
     *                     return 0;
     *                 }
     *                 if (!read(stream, data, length)) {
     *                     return 0;
     *                 }
     *             }
     *             return length;
     *         }
     *     }
     *     return 0;
     * }
     * ```
     */
    public fun getTableData(
      stream: SkStream?,
      ttcIndex: Int,
      tag: SkFontTableTag,
      offset: ULong,
      length: ULong,
      `data`: Unit?,
    ): Int {
      TODO("Implement getTableData")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t GetTableSize(SkStream* stream, int ttcIndex, SkFontTableTag tag) {
     *         return GetTableData(stream, ttcIndex, tag, 0, ~0U, nullptr);
     *     }
     * ```
     */
    public fun getTableSize(
      stream: SkStream?,
      ttcIndex: Int,
      tag: SkFontTableTag,
    ): Int {
      TODO("Implement getTableSize")
    }
  }
}

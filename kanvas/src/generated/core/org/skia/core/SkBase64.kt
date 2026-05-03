package org.skia.core

import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SkBase64 {
 * public:
 *     enum Error {
 *         kNoError,
 *         kPadError,
 *         kBadCharError
 *     };
 *
 *     /**
 *        Base64 encodes src into dst.
 *
 *        Normally this is called once with 'dst' nullptr to get the required size, then again with an
 *        allocated 'dst' pointer to do the actual encoding.
 *
 *        @param dst nullptr or a pointer to a buffer large enough to receive the result
 *
 *        @param encode nullptr for default encoding or a pointer to at least 65 chars.
 *                      encode[64] will be used as the pad character.
 *                      Encodings other than the default encoding cannot be decoded.
 *
 *        @return the required length of dst for encoding.
 *     */
 *     static size_t Encode(const void* src, size_t length, void* dst, const char* encode = nullptr);
 *
 *     /**
 *        Returns the length of the buffer that needs to be allocated to encode srcDataLength bytes.
 *     */
 *     static size_t EncodedSize(size_t srcDataLength) {
 *         // Take the floor of division by 3 to find the number of groups that need to be encoded.
 *         // Each group takes 4 bytes to be represented in base64.
 *         return ((srcDataLength + 2) / 3) * 4;
 *     }
 *
 *     /**
 *        Base64 decodes src into dst.
 *
 *        Normally this is called once with 'dst' nullptr to get the required size, then again with an
 *        allocated 'dst' pointer to do the actual encoding.
 *
 *        @param dst nullptr or a pointer to a buffer large enough to receive the result
 *
 *        @param dstLength assigned the length dst is required to be. Must not be nullptr.
 *     */
 *     [[nodiscard]] static Error Decode(const void* src, size_t  srcLength,
 *                                       void* dst, size_t* dstLength);
 * }
 * ```
 */
public open class SkBase64 {
  public enum class Error {
    kNoError,
    kPadError,
    kBadCharError,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * size_t SkBase64::Encode(const void* srcv, size_t length, void* dstv, const char* encodeMap) {
     *     const unsigned char* src = static_cast<const unsigned char*>(srcv);
     *     unsigned char* dst = static_cast<unsigned char*>(dstv);
     *
     *     const char* encode;
     *     if (nullptr == encodeMap) {
     *         encode = default_encode;
     *     } else {
     *         encode = encodeMap;
     *     }
     *     if (dst) {
     *         size_t remainder = length % 3;
     *         char unsigned const * const end = &src[length - remainder];
     *         while (src < end) {
     *             unsigned a = *src++;
     *             unsigned b = *src++;
     *             unsigned c = *src++;
     *             int      d = c & 0x3F;
     *             c = (c >> 6 | b << 2) & 0x3F;
     *             b = (b >> 4 | a << 4) & 0x3F;
     *             a = a >> 2;
     *             *dst++ = encode[a];
     *             *dst++ = encode[b];
     *             *dst++ = encode[c];
     *             *dst++ = encode[d];
     *         }
     *         if (remainder > 0) {
     *             int k1 = 0;
     *             int k2 = EncodePad;
     *             int a = (uint8_t) *src++;
     *             if (remainder == 2)
     *             {
     *                 int b = *src++;
     *                 k1 = b >> 4;
     *                 k2 = (b << 2) & 0x3F;
     *             }
     *             *dst++ = encode[a >> 2];
     *             *dst++ = encode[(k1 | a << 4) & 0x3F];
     *             *dst++ = encode[k2];
     *             *dst++ = encode[EncodePad];
     *         }
     *     }
     *     return EncodedSize(length);
     * }
     * ```
     */
    public fun encode(
      src: Unit?,
      length: ULong,
      dst: Unit?,
      encode: String? = TODO(),
    ): Int {
      TODO("Implement encode")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t EncodedSize(size_t srcDataLength) {
     *         // Take the floor of division by 3 to find the number of groups that need to be encoded.
     *         // Each group takes 4 bytes to be represented in base64.
     *         return ((srcDataLength + 2) / 3) * 4;
     *     }
     * ```
     */
    public fun encodedSize(srcDataLength: ULong): Int {
      TODO("Implement encodedSize")
    }

    /**
     * C++ original:
     * ```cpp
     * SkBase64::Error SkBase64::Decode(const void* srcv, size_t srcLength, void* dstv, size_t* dstLength){
     *     const unsigned char* src = static_cast<const unsigned char*>(srcv);
     *     unsigned char* dst = static_cast<unsigned char*>(dstv);
     *
     *     int i = 0;
     *     bool padTwo = false;
     *     bool padThree = false;
     *     char unsigned const * const end = src + srcLength;
     *     while (src < end) {
     *         unsigned char bytes[4];
     *         int byte = 0;
     *         do {
     *             unsigned char srcByte = *src++;
     *             if (srcByte == 0)
     *                 goto goHome;
     *             if (srcByte <= ' ')
     *                 continue; // treat as white space
     *             if (srcByte < '+' || srcByte > 'z')
     *                 return kBadCharError;
     *             signed char decoded = decodeData[srcByte - '+'];
     *             bytes[byte] = decoded;
     *             if (decoded < 0) {
     *                 if (decoded == DecodePad)
     *                     goto handlePad;
     *                 return kBadCharError;
     *             } else
     *                 byte++;
     *             if (*src)
     *                 continue;
     *             if (byte == 0)
     *                 goto goHome;
     *             if (byte == 4)
     *                 break;
     * handlePad:
     *             if (byte < 2)
     *                 return kPadError;
     *             padThree = true;
     *             if (byte == 2)
     *                 padTwo = true;
     *             break;
     *         } while (byte < 4);
     *         int two = 0;
     *         int three = 0;
     *         if (dst) {
     *             int one = (uint8_t) (bytes[0] << 2);
     *             two = bytes[1];
     *             one |= two >> 4;
     *             two = (uint8_t) ((two << 4) & 0xFF);
     *             three = bytes[2];
     *             two |= three >> 2;
     *             three = (uint8_t) ((three << 6) & 0xFF);
     *             three |= bytes[3];
     *             SkASSERT(one < 256 && two < 256 && three < 256);
     *             dst[i] = (unsigned char) one;
     *         }
     *         i++;
     *         if (padTwo)
     *             break;
     *         if (dst)
     *             dst[i] = (unsigned char) two;
     *         i++;
     *         if (padThree)
     *             break;
     *         if (dst)
     *             dst[i] = (unsigned char) three;
     *         i++;
     *     }
     * goHome:
     *     *dstLength = i;
     *     return kNoError;
     * }
     * ```
     */
    public fun decode(
      src: Unit?,
      srcLength: ULong,
      dst: Unit?,
      dstLength: ULong?,
    ): Error {
      TODO("Implement decode")
    }
  }
}

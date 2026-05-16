package org.skia.modules

import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class FastString final : public Value {
 * public:
 *     FastString(const char* src, size_t size, const char* eos, SkArenaAlloc& alloc) {
 *         SkASSERT(src <= eos);
 *
 *         if (size > kMaxInlineStringSize) {
 *             this->initLongString(src, size, alloc);
 *             SkASSERT(this->getTag() == Tag::kString);
 *             return;
 *         }
 *
 *         // initFastShortString is faster (doh), but requires access to 6 chars past src.
 *         if (src && src + 6 <= eos) {
 *             this->initFastShortString(src, size);
 *         } else {
 *             this->initShortString(src, size);
 *         }
 *
 *         SkASSERT(this->getTag() == Tag::kShortString);
 *     }
 *
 * private:
 *     // first byte reserved for tagging, \0 terminator => 6 usable chars
 *     inline static constexpr size_t kMaxInlineStringSize = sizeof(Value) - 2;
 *
 *     void initLongString(const char* src, size_t size, SkArenaAlloc& alloc) {
 *         SkASSERT(size > kMaxInlineStringSize);
 *
 *         this->init_tagged_pointer(Tag::kString, MakeVector<char, 1>(size, src, alloc));
 *
 *         auto* data = this->cast<VectorValue<char, Value::Type::kString>>()->begin();
 *         const_cast<char*>(data)[size] = '\0';
 *     }
 *
 *     void initShortString(const char* src, size_t size) {
 *         SkASSERT(size <= kMaxInlineStringSize);
 *
 *         this->init_tagged(Tag::kShortString);
 *         sk_careful_memcpy(this->cast<char>(), src, size);
 *         // Null terminator provided by init_tagged() above (fData8 is zero-initialized).
 *     }
 *
 *     void initFastShortString(const char* src, size_t size) {
 *         SkASSERT(size <= kMaxInlineStringSize);
 *
 *         uint64_t* s64 = this->cast<uint64_t>();
 *
 *         // Load 8 chars and mask out the tag and \0 terminator.
 *         // Note: we picked kShortString == 0 to avoid setting explicitly below.
 *         static_assert(SkToU8(Tag::kShortString) == 0, "please don't break this");
 *
 *         // Since the first byte is occupied by the tag, we want the string chars [0..5] to land
 *         // on bytes [1..6] => the fastest way is to read8 @(src - 1) (always safe, because the
 *         // string requires a " prefix at the very least).
 *         memcpy(s64, src - 1, 8);
 *
 * #if defined(SK_CPU_LENDIAN)
 *         // The mask for a max-length string (6), with a leading tag and trailing \0 is
 *         // 0x00ffffffffffff00.  Accounting for the final left-shift, this becomes
 *         // 0x0000ffffffffffff.
 *         *s64 &= (0x0000ffffffffffffULL >> ((kMaxInlineStringSize - size) * 8)) // trailing \0s
 *                     << 8;                                                      // tag byte
 * #else
 *         static_assert(false, "Big-endian builds are not supported at this time.");
 * #endif
 *     }
 * }
 * ```
 */
public class FastString public constructor(
  src: String?,
  size: ULong,
  eos: String?,
  alloc: SkArenaAlloc,
) : Value() {
  /**
   * C++ original:
   * ```cpp
   * void initLongString(const char* src, size_t size, SkArenaAlloc& alloc) {
   *         SkASSERT(size > kMaxInlineStringSize);
   *
   *         this->init_tagged_pointer(Tag::kString, MakeVector<char, 1>(size, src, alloc));
   *
   *         auto* data = this->cast<VectorValue<char, Value::Type::kString>>()->begin();
   *         const_cast<char*>(data)[size] = '\0';
   *     }
   * ```
   */
  private fun initLongString(
    src: String?,
    size: ULong,
    alloc: SkArenaAlloc,
  ) {
    TODO("Implement initLongString")
  }

  /**
   * C++ original:
   * ```cpp
   * void initShortString(const char* src, size_t size) {
   *         SkASSERT(size <= kMaxInlineStringSize);
   *
   *         this->init_tagged(Tag::kShortString);
   *         sk_careful_memcpy(this->cast<char>(), src, size);
   *         // Null terminator provided by init_tagged() above (fData8 is zero-initialized).
   *     }
   * ```
   */
  private fun initShortString(src: String?, size: ULong) {
    TODO("Implement initShortString")
  }

  /**
   * C++ original:
   * ```cpp
   * void initFastShortString(const char* src, size_t size) {
   *         SkASSERT(size <= kMaxInlineStringSize);
   *
   *         uint64_t* s64 = this->cast<uint64_t>();
   *
   *         // Load 8 chars and mask out the tag and \0 terminator.
   *         // Note: we picked kShortString == 0 to avoid setting explicitly below.
   *         static_assert(SkToU8(Tag::kShortString) == 0, "please don't break this");
   *
   *         // Since the first byte is occupied by the tag, we want the string chars [0..5] to land
   *         // on bytes [1..6] => the fastest way is to read8 @(src - 1) (always safe, because the
   *         // string requires a " prefix at the very least).
   *         memcpy(s64, src - 1, 8);
   *
   * #if defined(SK_CPU_LENDIAN)
   *         // The mask for a max-length string (6), with a leading tag and trailing \0 is
   *         // 0x00ffffffffffff00.  Accounting for the final left-shift, this becomes
   *         // 0x0000ffffffffffff.
   *         *s64 &= (0x0000ffffffffffffULL >> ((kMaxInlineStringSize - size) * 8)) // trailing \0s
   *                     << 8;                                                      // tag byte
   * #else
   *         static_assert(false, "Big-endian builds are not supported at this time.");
   * #endif
   *     }
   * ```
   */
  private fun initFastShortString(src: String?, size: ULong) {
    TODO("Implement initFastShortString")
  }

  public companion object {
    private val kMaxInlineStringSize: Int = TODO("Initialize kMaxInlineStringSize")
  }
}

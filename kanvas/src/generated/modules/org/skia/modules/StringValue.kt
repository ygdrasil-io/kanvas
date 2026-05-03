package org.skia.modules

import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.gpu.Type
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class StringValue final : public Value {
 * public:
 *     inline static constexpr Type kType = Type::kString;
 *
 *     StringValue();
 *     StringValue(const char* src, SkArenaAlloc& alloc);
 *     StringValue(const char* src, size_t size, SkArenaAlloc& alloc);
 *
 *     size_t size() const {
 *         switch (this->getTag()) {
 *             case Tag::kShortString:
 *                 // We don't bother storing a length for short strings on the assumption
 *                 // that strlen is fast in this case.  If this becomes problematic, we
 *                 // can either go back to storing (7-len) in the tag byte or write a fast
 *                 // short_strlen.
 *                 return strlen(this->cast<char>());
 *             case Tag::kString:
 *                 return this->cast<VectorValue<char, Value::Type::kString>>()->size();
 *             default:
 *                 return 0;
 *         }
 *     }
 *
 *     const char* begin() const {
 *         return this->getTag() == Tag::kShortString
 *                        ? this->cast<char>()
 *                        : this->cast<VectorValue<char, Value::Type::kString>>()->begin();
 *     }
 *
 *     const char* end() const {
 *         return this->getTag() == Tag::kShortString
 *                        ? strchr(this->cast<char>(), '\0')
 *                        : this->cast<VectorValue<char, Value::Type::kString>>()->end();
 *     }
 *
 *     std::string_view str() const { return std::string_view(this->begin(), this->size()); }
 * }
 * ```
 */
public class StringValue public constructor() : Value() {
  /**
   * C++ original:
   * ```cpp
   * StringValue()
   * ```
   */
  public constructor(src: String?, alloc: SkArenaAlloc) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * StringValue::StringValue(const char* src, SkArenaAlloc& alloc)
   *         : StringValue(src, strlen(src), alloc) {}
   * ```
   */
  public constructor(
    src: String?,
    size: ULong,
    alloc: SkArenaAlloc,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const {
   *         switch (this->getTag()) {
   *             case Tag::kShortString:
   *                 // We don't bother storing a length for short strings on the assumption
   *                 // that strlen is fast in this case.  If this becomes problematic, we
   *                 // can either go back to storing (7-len) in the tag byte or write a fast
   *                 // short_strlen.
   *                 return strlen(this->cast<char>());
   *             case Tag::kString:
   *                 return this->cast<VectorValue<char, Value::Type::kString>>()->size();
   *             default:
   *                 return 0;
   *         }
   *     }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* begin() const {
   *         return this->getTag() == Tag::kShortString
   *                        ? this->cast<char>()
   *                        : this->cast<VectorValue<char, Value::Type::kString>>()->begin();
   *     }
   * ```
   */
  public fun begin(): Char {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* end() const {
   *         return this->getTag() == Tag::kShortString
   *                        ? strchr(this->cast<char>(), '\0')
   *                        : this->cast<VectorValue<char, Value::Type::kString>>()->end();
   *     }
   * ```
   */
  public fun end(): Char {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string_view str() const { return std::string_view(this->begin(), this->size()); }
   * ```
   */
  public fun str(): Int {
    TODO("Implement str")
  }

  public companion object {
    public val kType: Type = TODO("Initialize kType")
  }
}

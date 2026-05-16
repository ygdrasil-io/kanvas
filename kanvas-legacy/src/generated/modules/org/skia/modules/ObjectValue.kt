package org.skia.modules

import kotlin.String
import kotlin.ULong
import org.skia.gpu.Type
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class ObjectValue final : public VectorValue<Member, Value::Type::kObject> {
 * public:
 *     ObjectValue(const Member* src, size_t size, SkArenaAlloc& alloc);
 *
 *     const Value& operator[](const char* key) const {
 *         static const Value gNullValue = NullValue();
 *
 *         const auto* member = this->find(key);
 *         return member ? member->fValue : gNullValue;
 *     }
 *
 *     // Writable access to the value associated with the given key.
 *     // If the key is not present, it is added with a default NullValue.
 *     Value& writable(const char* key, SkArenaAlloc&) const;
 *
 * private:
 *     const Member* find(const char*) const;
 * }
 * ```
 */
public class ObjectValue public constructor(
  src: Member?,
  size: ULong,
  alloc: SkArenaAlloc,
) : VectorValue(),
    Member,
    Type.KObject {
  /**
   * C++ original:
   * ```cpp
   * const Value& operator[](const char* key) const {
   *         static const Value gNullValue = NullValue();
   *
   *         const auto* member = this->find(key);
   *         return member ? member->fValue : gNullValue;
   *     }
   * ```
   */
  public operator fun `get`(key: String?): Value {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * Value& ObjectValue::writable(const char* key, SkArenaAlloc& alloc) const {
   *     Member* writable_member = const_cast<Member*>(this->find(key));
   *
   *     if (!writable_member) {
   *         ObjectValue* writable_obj = const_cast<ObjectValue*>(this);
   *         writable_obj->init_tagged_pointer(Tag::kObject, MakeVector<Member>(this->size() + 1,
   *                                                                            this->begin(),
   *                                                                            this->size(),
   *                                                                            alloc));
   *         writable_member         = const_cast<Member*>(writable_obj->end() - 1);
   *         writable_member->fKey   = StringValue(key, strlen(key), alloc);
   *         writable_member->fValue = NullValue();
   *     }
   *
   *     return writable_member->fValue;
   * }
   * ```
   */
  public fun writable(key: String?, alloc: SkArenaAlloc): Value {
    TODO("Implement writable")
  }

  /**
   * C++ original:
   * ```cpp
   * const Member* ObjectValue::find(const char* key) const {
   *     // Reverse search for duplicates resolution (policy: return last).
   *     const auto* begin  = this->begin();
   *     const auto* member = this->end();
   *
   *     while (member > begin) {
   *         --member;
   *         if (0 == inline_strcmp(key, member->fKey.as<StringValue>().begin())) {
   *             return member;
   *         }
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  private fun find(key: String?): Member {
    TODO("Implement find")
  }
}

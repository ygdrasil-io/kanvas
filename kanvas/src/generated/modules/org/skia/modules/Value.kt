package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class alignas(8) Value {
 * public:
 *     enum class Type {
 *         kNull,
 *         kBool,
 *         kNumber,
 *         kString,
 *         kArray,
 *         kObject,
 *     };
 *
 *     /**
 *      * @return    The type of this value.
 *      */
 *     Type getType() const;
 *
 *     /**
 *      * @return    True if the record matches the facade type T.
 *      */
 *     template <typename T>
 *     bool is() const { return this->getType() == T::kType; }
 *
 *     /**
 *      * Unguarded conversion to facade types.
 *      *
 *      * @return    The record cast as facade type T&.
 *      */
 *     template <typename T>
 *     const T& as() const {
 *         SkASSERT(this->is<T>());
 *         return *reinterpret_cast<const T*>(this);
 *     }
 *
 *     /**
 *      * Guarded conversion to facade types.
 *      *
 *      * @return    The record cast as facade type T*.
 *      */
 *     template <typename T>
 *     operator const T*() const {
 *         return this->is<T>() ? &this->as<T>() : nullptr;
 *     }
 *
 *     /**
 *      * @return    The string representation of this value.
 *      */
 *     SkString toString() const;
 *
 *     /**
 *      * Helper for fluent key lookup: v["foo"]["bar"]["baz"]
 *      *
 *      * @return    The lookup result value on success, otherwise NullValue.
 *      */
 *     const Value& operator[](const char* key) const;
 *
 * protected:
 *     /*
 *       Value implementation notes:
 *
 *         -- fixed 64-bit size
 *
 *         -- 8-byte aligned
 *
 *         -- union of:
 *
 *              bool
 *              int32
 *              float
 *              char[8] (short string storage)
 *              external payload (tagged) pointer
 *
 *          -- lowest 3 bits reserved for tag storage
 *
 *      */
 *     enum class Tag : uint8_t {
 *         // n.b.: we picked kShortString == 0 on purpose,
 *         // to enable certain short-string optimizations.
 *         kShortString                  = 0b00000000,  // inline payload
 *         kNull                         = 0b00000001,  // no payload
 *         kBool                         = 0b00000010,  // inline payload
 *         kInt                          = 0b00000011,  // inline payload
 *         kFloat                        = 0b00000100,  // inline payload
 *         kString                       = 0b00000101,  // ptr to external storage
 *         kArray                        = 0b00000110,  // ptr to external storage
 *         kObject                       = 0b00000111,  // ptr to external storage
 *     };
 *     inline static constexpr uint8_t kTagMask = 0b00000111;
 *
 *     void init_tagged(Tag);
 *     void init_tagged_pointer(Tag, void*);
 *
 *     Tag getTag() const {
 *         return static_cast<Tag>(fData8[0] & kTagMask);
 *     }
 *
 *     // Access the record payload as T.
 *     //
 *     // Since the tag is stored in the lower bits, we skip the first word whenever feasible.
 *     //
 *     // E.g. (U == unused)
 *     //
 *     //   uint8_t
 *     //    -----------------------------------------------------------------------
 *     //   |TAG| U  |  val8  |   U    |   U    |   U    |   U    |   U    |   U    |
 *     //    -----------------------------------------------------------------------
 *     //
 *     //   uint16_t
 *     //    -----------------------------------------------------------------------
 *     //   |TAG|      U      |      val16      |        U        |        U        |
 *     //    -----------------------------------------------------------------------
 *     //
 *     //   uint32_t
 *     //    -----------------------------------------------------------------------
 *     //   |TAG|             U                 |                val32              |
 *     //    -----------------------------------------------------------------------
 *     //
 *     //   T* (32b)
 *     //    -----------------------------------------------------------------------
 *     //   |TAG|             U                 |             T* (32bits)           |
 *     //    -----------------------------------------------------------------------
 *     //
 *     //   T* (64b)
 *     //    -----------------------------------------------------------------------
 *     //   |TAG|                        T* (61bits)                                |
 *     //    -----------------------------------------------------------------------
 *     //
 *     template <typename T>
 *     const T* cast() const {
 *         static_assert(sizeof (T) <=  sizeof(Value), "");
 *         static_assert(alignof(T) <= alignof(Value), "");
 *
 *         return (sizeof(T) > sizeof(*this) / 2)
 *                 ? reinterpret_cast<const T*>(this) + 0  // need all the bits
 *                 : reinterpret_cast<const T*>(this) + 1; // skip the first word (where the tag lives)
 *     }
 *
 *     template <typename T>
 *     T* cast() { return const_cast<T*>(const_cast<const Value*>(this)->cast<T>()); }
 *
 *     // Access the pointer payload.
 *     template <typename T>
 *     const T* ptr() const {
 *         static_assert(sizeof(uintptr_t)     == sizeof(Value) ||
 *                       sizeof(uintptr_t) * 2 == sizeof(Value), "");
 *
 *         return (sizeof(uintptr_t) < sizeof(Value))
 *             // For 32-bit, pointers are stored unmodified.
 *             ? *this->cast<const T*>()
 *             // For 64-bit, we use the lower bits of the pointer as tag storage.
 *             : reinterpret_cast<T*>(*this->cast<uintptr_t>() & ~static_cast<uintptr_t>(kTagMask));
 *     }
 *
 * private:
 *     inline static constexpr size_t kValueSize = 8;
 *
 *     uint8_t fData8[kValueSize];
 *
 * #if !defined(SK_CPU_LENDIAN)
 *     // The current value layout assumes LE and will take some tweaking for BE.
 *     static_assert(false, "Big-endian builds are not supported at this time.");
 * #endif
 * }
 * ```
 */
public open class Value {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr uint8_t kTagMask = 0b00000111
   * ```
   */
  private var fData8: Int = TODO("Initialize fData8")

  /**
   * C++ original:
   * ```cpp
   * inline Value::Type Value::getType() const {
   *     switch (this->getTag()) {
   *     case Tag::kNull:        return Type::kNull;
   *     case Tag::kBool:        return Type::kBool;
   *     case Tag::kInt:         return Type::kNumber;
   *     case Tag::kFloat:       return Type::kNumber;
   *     case Tag::kShortString: return Type::kString;
   *     case Tag::kString:      return Type::kString;
   *     case Tag::kArray:       return Type::kArray;
   *     case Tag::kObject:      return Type::kObject;
   *     }
   *
   *     SkASSERT(false);  // unreachable
   *     return Type::kNull;
   * }
   * ```
   */
  public fun getType(): Type {
    TODO("Implement getType")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool is() const { return this->getType() == T::kType; }
   * ```
   */
  public fun <T> `is`(): Boolean {
    TODO("Implement is")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     const T& as() const {
   *         SkASSERT(this->is<T>());
   *         return *reinterpret_cast<const T*>(this);
   *     }
   * ```
   */
  public fun <T> `as`(): T {
    TODO("Implement as")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString Value::toString() const {
   *     SkDynamicMemoryWStream wstream;
   *     Write(*this, &wstream);
   *     const auto data = wstream.detachAsData();
   *     // TODO: is there a better way to pass data around without copying?
   *     return SkString(static_cast<const char*>(data->data()), data->size());
   * }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * inline const Value& Value::operator[](const char* key) const {
   *     static const Value gNullValue = NullValue();
   *
   *     return this->is<ObjectValue>() ? this->as<ObjectValue>()[key] : gNullValue;
   * }
   * ```
   */
  public operator fun `get`(key: String?): Value {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void Value::init_tagged(Tag t) {
   *     memset(fData8, 0, sizeof(fData8));
   *     fData8[0] = SkTo<uint8_t>(t);
   *     SkASSERT(this->getTag() == t);
   * }
   * ```
   */
  protected fun initTagged(t: Tag) {
    TODO("Implement initTagged")
  }

  /**
   * C++ original:
   * ```cpp
   * void Value::init_tagged_pointer(Tag t, void* p) {
   *     if (sizeof(Value) == sizeof(uintptr_t)) {
   *         *this->cast<uintptr_t>() = reinterpret_cast<uintptr_t>(p);
   *         // For 64-bit, we rely on the pointer lower bits being zero.
   *         SkASSERT(!(fData8[0] & kTagMask));
   *         fData8[0] |= SkTo<uint8_t>(t);
   *     } else {
   *         // For 32-bit, we store the pointer in the upper word
   *         SkASSERT(sizeof(Value) == sizeof(uintptr_t) * 2);
   *         this->init_tagged(t);
   *         *this->cast<uintptr_t>() = reinterpret_cast<uintptr_t>(p);
   *     }
   *
   *     SkASSERT(this->getTag()    == t);
   *     SkASSERT(this->ptr<void>() == p);
   * }
   * ```
   */
  protected fun initTaggedPointer(t: Tag, p: Unit?) {
    TODO("Implement initTaggedPointer")
  }

  /**
   * C++ original:
   * ```cpp
   * Tag getTag() const {
   *         return static_cast<Tag>(fData8[0] & kTagMask);
   *     }
   * ```
   */
  protected fun getTag(): Tag {
    TODO("Implement getTag")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     const T* cast() const {
   *         static_assert(sizeof (T) <=  sizeof(Value), "");
   *         static_assert(alignof(T) <= alignof(Value), "");
   *
   *         return (sizeof(T) > sizeof(*this) / 2)
   *                 ? reinterpret_cast<const T*>(this) + 0  // need all the bits
   *                 : reinterpret_cast<const T*>(this) + 1; // skip the first word (where the tag lives)
   *     }
   * ```
   */
  protected fun <T> cast(): T {
    TODO("Implement cast")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     T* cast() { return const_cast<T*>(const_cast<const Value*>(this)->cast<T>()); }
   * ```
   */
  protected fun <T> ptr(): T {
    TODO("Implement ptr")
  }

  public enum class Type {
    kNull,
    kBool,
    kNumber,
    kString,
    kArray,
    kObject,
  }

  public enum class Tag {
    kShortString,
    kNull,
    kBool,
    kInt,
    kFloat,
    kString,
    kArray,
    kObject,
  }

  public companion object {
    protected val kTagMask: Int = TODO("Initialize kTagMask")

    private val kValueSize: Int = TODO("Initialize kValueSize")
  }
}

package org.skia.tools

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkMetaData {
 * public:
 *     SkMetaData() {}
 *     ~SkMetaData() { if (fRec) { this->reset(); } }
 *     void reset();
 *
 *     bool findS32(const char name[], int32_t* value = nullptr) const;
 *     bool findScalar(const char name[], SkScalar* value = nullptr) const;
 *     const SkScalar* findScalars(const char name[], int* count,
 *                                 SkScalar values[] = nullptr) const;
 *     bool findPtr(const char name[], void** value = nullptr) const;
 *     bool findBool(const char name[], bool* value = nullptr) const;
 *
 *     bool hasS32(const char name[], int32_t value) const {
 *         int32_t v;
 *         return this->findS32(name, &v) && v == value;
 *     }
 *     bool hasScalar(const char name[], SkScalar value) const {
 *         SkScalar v;
 *         return this->findScalar(name, &v) && v == value;
 *     }
 *     bool hasPtr(const char name[], void* value) const {
 *         void* v;
 *         return this->findPtr(name, &v) && v == value;
 *     }
 *     bool hasBool(const char name[], bool value) const {
 *         bool    v;
 *         return this->findBool(name, &v) && v == value;
 *     }
 *
 *     void setS32(const char name[], int32_t value);
 *     void setScalar(const char name[], SkScalar value);
 *     SkScalar* setScalars(const char name[], int count, const SkScalar values[] = nullptr);
 *     void setPtr(const char name[], void* value);
 *     void setBool(const char name[], bool value);
 *
 *     bool removeS32(const char name[]);
 *     bool removeScalar(const char name[]);
 *     bool removePtr(const char name[]);
 *     bool removeBool(const char name[]);
 *
 *     enum Type {
 *         kS32_Type,
 *         kScalar_Type,
 *         kPtr_Type,
 *         kBool_Type,
 *
 *         kTypeCount
 *     };
 *
 *     struct Rec;
 *     class Iter;
 *     friend class Iter;
 *
 *     class Iter {
 *     public:
 *         Iter() : fRec(nullptr) {}
 *         Iter(const SkMetaData&);
 *
 *         /** Reset the iterator, so that calling next() will return the first
 *             data element. This is done implicitly in the constructor.
 *         */
 *         void reset(const SkMetaData&);
 *
 *         /** Each time next is called, it returns the name of the next data element,
 *             or null when there are no more elements. If non-null is returned, then the
 *             element's type is returned (if not null), and the number of data values
 *             is returned in count (if not null).
 *         */
 *         const char* next(Type*, int* count);
 *
 *     private:
 *         Rec* fRec;
 *     };
 *
 * public:
 *     struct Rec {
 *         Rec*        fNext;
 *         uint16_t    fDataCount; // number of elements
 *         uint8_t     fDataLen;   // sizeof a single element
 *         uint8_t     fType;
 *
 *         const void* data() const { return (this + 1); }
 *         void*       data() { return (this + 1); }
 *         const char* name() const { return (const char*)this->data() + fDataLen * fDataCount; }
 *         char*       name() { return (char*)this->data() + fDataLen * fDataCount; }
 *
 *         static Rec* Alloc(size_t);
 *         static void Free(Rec*);
 *     };
 *     Rec*    fRec = nullptr;
 *
 *     const Rec* find(const char name[], Type) const;
 *     void* set(const char name[], const void* data, size_t len, Type, int count);
 *     bool remove(const char name[], Type);
 *
 *     SkMetaData(const SkMetaData&) = delete;
 *     SkMetaData& operator=(const SkMetaData&) = delete;
 *
 * private:
 *     struct FindResult {
 *         SkMetaData::Rec* rec;
 *         SkMetaData::Rec* prev;
 *     };
 *     FindResult findWithPrev(const char name[], Type type) const;
 *     void remove(FindResult);
 * }
 * ```
 */
public data class SkMetaData public constructor(
  /**
   * C++ original:
   * ```cpp
   * Rec*    fRec = nullptr
   * ```
   */
  public var fRec: Rec?,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkMetaData::reset()
   * {
   *     Rec* rec = fRec;
   *     while (rec) {
   *         Rec* next = rec->fNext;
   *         Rec::Free(rec);
   *         rec = next;
   *     }
   *     fRec = nullptr;
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::findS32(const char name[], int32_t* value) const
   * {
   *     const Rec* rec = this->find(name, kS32_Type);
   *     if (rec)
   *     {
   *         SkASSERT(rec->fDataCount == 1);
   *         if (value)
   *             *value = *(const int32_t*)rec->data();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun findS32(name: CharArray, `value`: Int? = TODO()): Boolean {
    TODO("Implement findS32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::findScalar(const char name[], SkScalar* value) const
   * {
   *     const Rec* rec = this->find(name, kScalar_Type);
   *     if (rec)
   *     {
   *         SkASSERT(rec->fDataCount == 1);
   *         if (value)
   *             *value = *(const SkScalar*)rec->data();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun findScalar(name: CharArray, `value`: SkScalar? = TODO()): Boolean {
    TODO("Implement findScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkScalar* SkMetaData::findScalars(const char name[], int* count, SkScalar values[]) const
   * {
   *     const Rec* rec = this->find(name, kScalar_Type);
   *     if (rec)
   *     {
   *         if (count)
   *             *count = rec->fDataCount;
   *         if (values)
   *             memcpy(values, rec->data(), rec->fDataCount * rec->fDataLen);
   *         return (const SkScalar*)rec->data();
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun findScalars(
    name: CharArray,
    count: Int?,
    values: Array<SkScalar> = TODO(),
  ): Int {
    TODO("Implement findScalars")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::findPtr(const char name[], void** ptr) const {
   *     const Rec* rec = this->find(name, kPtr_Type);
   *     if (rec) {
   *         SkASSERT(rec->fDataCount == 1);
   *         void* const* found = (void* const*)rec->data();
   *         if (ptr) {
   *             *ptr = *found;
   *         }
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun findPtr(name: CharArray, `value`: Int? = TODO()): Boolean {
    TODO("Implement findPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::findBool(const char name[], bool* value) const
   * {
   *     const Rec* rec = this->find(name, kBool_Type);
   *     if (rec)
   *     {
   *         SkASSERT(rec->fDataCount == 1);
   *         if (value)
   *             *value = *(const bool*)rec->data();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun findBool(name: CharArray, `value`: Boolean? = TODO()): Boolean {
    TODO("Implement findBool")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasS32(const char name[], int32_t value) const {
   *         int32_t v;
   *         return this->findS32(name, &v) && v == value;
   *     }
   * ```
   */
  public fun hasS32(name: CharArray, `value`: Int): Boolean {
    TODO("Implement hasS32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasScalar(const char name[], SkScalar value) const {
   *         SkScalar v;
   *         return this->findScalar(name, &v) && v == value;
   *     }
   * ```
   */
  public fun hasScalar(name: CharArray, `value`: SkScalar): Boolean {
    TODO("Implement hasScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPtr(const char name[], void* value) const {
   *         void* v;
   *         return this->findPtr(name, &v) && v == value;
   *     }
   * ```
   */
  public fun hasPtr(name: CharArray, `value`: Unit?): Boolean {
    TODO("Implement hasPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasBool(const char name[], bool value) const {
   *         bool    v;
   *         return this->findBool(name, &v) && v == value;
   *     }
   * ```
   */
  public fun hasBool(name: CharArray, `value`: Boolean): Boolean {
    TODO("Implement hasBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMetaData::setS32(const char name[], int32_t value)
   * {
   *     (void)this->set(name, &value, sizeof(int32_t), kS32_Type, 1);
   * }
   * ```
   */
  public fun setS32(name: CharArray, `value`: Int) {
    TODO("Implement setS32")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMetaData::setScalar(const char name[], SkScalar value)
   * {
   *     (void)this->set(name, &value, sizeof(SkScalar), kScalar_Type, 1);
   * }
   * ```
   */
  public fun setScalar(name: CharArray, `value`: SkScalar) {
    TODO("Implement setScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar* SkMetaData::setScalars(const char name[], int count, const SkScalar values[])
   * {
   *     SkASSERT(count > 0);
   *     if (count > 0)
   *         return (SkScalar*)this->set(name, values, sizeof(SkScalar), kScalar_Type, count);
   *     return nullptr;
   * }
   * ```
   */
  public fun setScalars(
    name: CharArray,
    count: Int,
    values: Array<SkScalar> = TODO(),
  ): Int {
    TODO("Implement setScalars")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMetaData::setPtr(const char name[], void* ptr) {
   *     (void)this->set(name, &ptr, sizeof(void*), kPtr_Type, 1);
   * }
   * ```
   */
  public fun setPtr(name: CharArray, `value`: Unit?) {
    TODO("Implement setPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMetaData::setBool(const char name[], bool value)
   * {
   *     (void)this->set(name, &value, sizeof(bool), kBool_Type, 1);
   * }
   * ```
   */
  public fun setBool(name: CharArray, `value`: Boolean) {
    TODO("Implement setBool")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::removeS32(const char name[])
   * {
   *     return this->remove(name, kS32_Type);
   * }
   * ```
   */
  public fun removeS32(name: CharArray): Boolean {
    TODO("Implement removeS32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::removeScalar(const char name[])
   * {
   *     return this->remove(name, kScalar_Type);
   * }
   * ```
   */
  public fun removeScalar(name: CharArray): Boolean {
    TODO("Implement removeScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::removePtr(const char name[])
   * {
   *     return this->remove(name, kPtr_Type);
   * }
   * ```
   */
  public fun removePtr(name: CharArray): Boolean {
    TODO("Implement removePtr")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::removeBool(const char name[])
   * {
   *     return this->remove(name, kBool_Type);
   * }
   * ```
   */
  public fun removeBool(name: CharArray): Boolean {
    TODO("Implement removeBool")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMetaData::Rec* SkMetaData::find(const char name[], Type type) const {
   *     return this->findWithPrev(name, type).rec;
   * }
   * ```
   */
  public fun find(name: CharArray, type: Type): Rec {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkMetaData::set(const char name[], const void* data, size_t dataSize, Type type, int count)
   * {
   *     SkASSERT(name);
   *     SkASSERT(dataSize);
   *     SkASSERT(count > 0);
   *
   *     FindResult result = this->findWithPrev(name, type);
   *
   *     Rec* rec;
   *     bool reuseRec = result.rec &&
   *                     result.rec->fDataLen == dataSize &&
   *                     result.rec->fDataCount == count;
   *     if (reuseRec) {
   *         rec = result.rec;
   *     } else {
   *         size_t len = strlen(name);
   *         rec = Rec::Alloc(sizeof(Rec) + dataSize * count + len + 1);
   *         rec->fType = SkToU8(type);
   *         rec->fDataLen = SkToU8(dataSize);
   *         rec->fDataCount = SkToU16(count);
   *
   *         memcpy(rec->name(), name, len + 1);
   *     }
   *     if (data) {
   *         memcpy(rec->data(), data, dataSize * count);
   *     }
   *
   *     if (reuseRec) {
   *         // Do nothing, reused
   *     } else if (result.rec) {
   *         // Had one, but had to create a new one. Invalidates iterators.
   *         // Delayed removal since name or data may have been in the result.rec.
   *         this->remove(result);
   *         if (result.prev) {
   *             rec->fNext = result.prev->fNext;
   *             result.prev->fNext = rec;
   *         }
   *     } else {
   *         // Adding a new one, stick it at head.
   *         rec->fNext = fRec;
   *         fRec = rec;
   *     }
   *     return rec->data();
   * }
   * ```
   */
  public fun `set`(
    name: CharArray,
    `data`: Unit?,
    len: ULong,
    type: Type,
    count: Int,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMetaData::remove(const char name[], Type type) {
   *     FindResult result = this->findWithPrev(name, type);
   *     if (!result.rec) {
   *         return false;
   *     }
   *     this->remove(result);
   *     return true;
   * }
   * ```
   */
  public fun remove(name: CharArray, type: Type): Boolean {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMetaData& operator=(const SkMetaData&) = delete
   * ```
   */
  public fun assign(param0: SkMetaData) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMetaData::FindResult SkMetaData::findWithPrev(const char name[], Type type) const {
   *     FindResult current { fRec, nullptr };
   *     while (current.rec) {
   *         if (current.rec->fType == type && !strcmp(current.rec->name(), name))
   *             return current;
   *         current.prev = current.rec;
   *         current.rec = current.rec->fNext;
   *     }
   *     return current;
   * }
   * ```
   */
  private fun findWithPrev(name: CharArray, type: Type): FindResult {
    TODO("Implement findWithPrev")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMetaData::remove(FindResult result) {
   *     SkASSERT(result.rec);
   *     if (result.prev) {
   *         result.prev->fNext = result.rec->fNext;
   *     } else {
   *         fRec = result.rec->fNext;
   *     }
   *     Rec::Free(result.rec);
   * }
   * ```
   */
  private fun remove(result: FindResult) {
    TODO("Implement remove")
  }

  public open class Iter public constructor() {
    private var fRec: undefined.Rec? = TODO("Initialize fRec")

    public constructor(metadata: SkMetaData) : this() {
      TODO("Implement constructor")
    }

    public fun reset(metadata: SkMetaData) {
      TODO("Implement reset")
    }

    public fun next(t: org.skia.gpu.Type?, count: Int?): Char {
      TODO("Implement next")
    }
  }

  public open class Rec public constructor(
    public var fNext: undefined.Rec?,
    public var fDataCount: Int,
    public var fDataLen: Int,
    public var fType: Int,
  ) {
    public fun `data`() {
      TODO("Implement data")
    }

    public fun name(): Char {
      TODO("Implement name")
    }

    public companion object {
      public fun alloc(size: ULong): Rec {
        TODO("Implement alloc")
      }

      public fun free(rec: Rec?) {
        TODO("Implement free")
      }
    }
  }

  public data class FindResult public constructor(
    public var rec: Rec?,
    public var prev: Rec?,
  )

  public enum class Type {
    kS32_Type,
    kScalar_Type,
    kPtr_Type,
    kBool_Type,
    kTypeCount,
  }
}

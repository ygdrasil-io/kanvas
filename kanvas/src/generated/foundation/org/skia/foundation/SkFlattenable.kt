package org.skia.foundation

import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFlattenable : public SkRefCnt {
 * public:
 *     enum Type {
 *         kSkColorFilter_Type,
 *         kSkBlender_Type,
 *         kSkDrawable_Type,
 *         kSkDrawLooper_Type,  // no longer supported by Skia
 *         kSkImageFilter_Type,
 *         kSkMaskFilter_Type,
 *         kSkPathEffect_Type,
 *         kSkShader_Type,
 *     };
 *
 *     typedef sk_sp<SkFlattenable> (*Factory)(SkReadBuffer&);
 *
 *     SkFlattenable() {}
 *
 *     /** Implement this to return a factory function pointer that can be called
 *      to recreate your class given a buffer (previously written to by your
 *      override of flatten().
 *      */
 *     virtual Factory getFactory() const = 0;
 *
 *     /**
 *      *  Returns the name of the object's class.
 *      */
 *     virtual const char* getTypeName() const = 0;
 *
 *     static Factory NameToFactory(const char name[]);
 *     static const char* FactoryToName(Factory);
 *
 *     static void Register(const char name[], Factory);
 *
 *     /**
 *      *  Override this if your subclass needs to record data that it will need to recreate itself
 *      *  from its CreateProc (returned by getFactory()).
 *      *
 *      *  DEPRECATED public : will move to protected ... use serialize() instead
 *      */
 *     virtual void flatten(SkWriteBuffer&) const {}
 *
 *     virtual Type getFlattenableType() const = 0;
 *
 *     //
 *     // public ways to serialize / deserialize
 *     //
 *     sk_sp<SkData> serialize(const SkSerialProcs* = nullptr) const;
 *     size_t serialize(void* memory, size_t memory_size,
 *                      const SkSerialProcs* = nullptr) const;
 *     static sk_sp<SkFlattenable> Deserialize(Type, const void* data, size_t length,
 *                                             const SkDeserialProcs* procs = nullptr);
 *
 * protected:
 *     class PrivateInitializer {
 *     public:
 *         static void InitEffects();
 *         static void InitImageFilters();
 *     };
 *
 * private:
 *     static void RegisterFlattenablesIfNeeded();
 *     static void Finalize();
 *
 *     friend class SkGraphics;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public abstract class SkFlattenable public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual Factory getFactory() const = 0
   * ```
   */
  public abstract fun getFactory(): SkFlattenableFactory

  /**
   * C++ original:
   * ```cpp
   * virtual const char* getTypeName() const = 0
   * ```
   */
  public abstract fun getTypeName(): Char

  /**
   * C++ original:
   * ```cpp
   * virtual void flatten(SkWriteBuffer&) const {}
   * ```
   */
  public open fun flatten(param0: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Type getFlattenableType() const = 0
   * ```
   */
  public abstract fun getFlattenableType(): Type

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkFlattenable::serialize(const SkSerialProcs* procs) const {
   *     SkSerialProcs p;
   *     if (procs) {
   *         p = *procs;
   *     }
   *     SkBinaryWriteBuffer writer(p);
   *
   *     writer.writeFlattenable(this);
   *     size_t size = writer.bytesWritten();
   *     auto data = SkData::MakeUninitialized(size);
   *     writer.writeToMemory(data->writable_data());
   *     return data;
   * }
   * ```
   */
  public fun serialize(procs: SkSerialProcs? = TODO()): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkFlattenable::serialize(void* memory, size_t memory_size,
   *                                 const SkSerialProcs* procs) const {
   *     SkSerialProcs p;
   *     if (procs) {
   *         p = *procs;
   *     }
   *     SkBinaryWriteBuffer writer(memory, memory_size, p);
   *     writer.writeFlattenable(this);
   *     return writer.usingInitialStorage() ? writer.bytesWritten() : 0u;
   * }
   * ```
   */
  public fun serialize(
    memory: Unit?,
    memorySize: ULong,
    procs: SkSerialProcs? = TODO(),
  ): ULong {
    TODO("Implement serialize")
  }

  public open class PrivateInitializer {
    public companion object {
      public fun initEffects() {
        TODO("Implement initEffects")
      }

      public fun initImageFilters() {
        TODO("Implement initImageFilters")
      }
    }
  }

  public enum class Type {
    kSkColorFilter_Type,
    kSkBlender_Type,
    kSkDrawable_Type,
    kSkDrawLooper_Type,
    kSkImageFilter_Type,
    kSkMaskFilter_Type,
    kSkPathEffect_Type,
    kSkShader_Type,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkFlattenable::Factory SkFlattenable::NameToFactory(const char name[]) {
     *     RegisterFlattenablesIfNeeded();
     *
     *     SkASSERT(std::is_sorted(gEntries, gEntries + gCount, EntryComparator()));
     *     auto pair = std::equal_range(gEntries, gEntries + gCount, name, EntryComparator());
     *     if (pair.first == pair.second) {
     *         return nullptr;
     *     }
     *     return pair.first->fFactory;
     * }
     * ```
     */
    public fun nameToFactory(name: CharArray): SkFlattenableFactory {
      TODO("Implement nameToFactory")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkFlattenable::FactoryToName(Factory fact) {
     *     RegisterFlattenablesIfNeeded();
     *
     *     const Entry* entries = gEntries;
     *     for (int i = gCount - 1; i >= 0; --i) {
     *         if (entries[i].fFactory == fact) {
     *             return entries[i].fName;
     *         }
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public fun factoryToName(fact: SkFlattenableFactory): Char {
      TODO("Implement factoryToName")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFlattenable::Register(const char name[], Factory factory) {
     *     SkASSERT(name);
     *     SkASSERT(factory);
     *     SkASSERT(gCount < (int)std::size(gEntries));
     *
     *     gEntries[gCount].fName = name;
     *     gEntries[gCount].fFactory = factory;
     *     gCount += 1;
     * }
     * ```
     */
    public fun register(name: CharArray, factory: SkFlattenableFactory) {
      TODO("Implement register")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFlattenable> SkFlattenable::Deserialize(SkFlattenable::Type type, const void* data,
     *                                                 size_t size, const SkDeserialProcs* procs) {
     *     SkReadBuffer buffer(data, size);
     *     if (procs) {
     *         buffer.setDeserialProcs(*procs);
     *     }
     *     return sk_sp<SkFlattenable>(buffer.readFlattenable(type));
     * }
     * ```
     */
    public fun deserialize(
      type: Type,
      `data`: Unit?,
      length: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFlattenable::RegisterFlattenablesIfNeeded() {
     *     static SkOnce once;
     *     once([]{
     *         SkFlattenable::PrivateInitializer::InitEffects();
     *         SkFlattenable::PrivateInitializer::InitImageFilters();
     *         SkFlattenable::Finalize();
     *     });
     * }
     * ```
     */
    private fun registerFlattenablesIfNeeded() {
      TODO("Implement registerFlattenablesIfNeeded")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFlattenable::Finalize() {
     *     std::sort(gEntries, gEntries + gCount, EntryComparator());
     * }
     * ```
     */
    private fun finalize() {
      TODO("Implement finalize")
    }
  }
}

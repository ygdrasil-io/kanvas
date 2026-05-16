package org.skia.core

import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkFlattenableFactory
import org.skia.foundation.SkRefCnt
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkNamedFactorySet : public SkRefCnt {
 * public:
 *
 *
 *     SkNamedFactorySet();
 *
 *     /**
 *      * Find the specified Factory in the set. If it is not already in the set,
 *      * and has registered its name, add it to the set, and return its index.
 *      * If the Factory has no registered name, return 0.
 *      */
 *     uint32_t find(SkFlattenable::Factory);
 *
 *     /**
 *      * If new Factorys have been added to the set, return the name of the first
 *      * Factory added after the Factory name returned by the last call to this
 *      * function.
 *      */
 *     const char* getNextAddedFactoryName();
 * private:
 *     int                    fNextAddedFactory;
 *     SkFactorySet           fFactorySet;
 *     SkTDArray<const char*> fNames;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkNamedFactorySet public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * int                    fNextAddedFactory
   * ```
   */
  private var fNextAddedFactory: Int = TODO("Initialize fNextAddedFactory")

  /**
   * C++ original:
   * ```cpp
   * SkFactorySet           fFactorySet
   * ```
   */
  private var fFactorySet: SkFactorySet = TODO("Initialize fFactorySet")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<const char*> fNames
   * ```
   */
  private val fNames: SkTDArray<String?> = TODO("Initialize fNames")

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkNamedFactorySet::find(SkFlattenable::Factory factory) {
   *     uint32_t index = fFactorySet.find(factory);
   *     if (index > 0) {
   *         return index;
   *     }
   *     const char* name = SkFlattenable::FactoryToName(factory);
   *     if (nullptr == name) {
   *         return 0;
   *     }
   *     *fNames.append() = name;
   *     return fFactorySet.add(factory);
   * }
   * ```
   */
  public fun find(factory: SkFlattenableFactory): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* SkNamedFactorySet::getNextAddedFactoryName() {
   *     if (fNextAddedFactory < fNames.size()) {
   *         return fNames[fNextAddedFactory++];
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun getNextAddedFactoryName(): Char {
    TODO("Implement getNextAddedFactoryName")
  }
}

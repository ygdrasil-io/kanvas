package org.skia.core

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkOSFile {
 * public:
 *     class Iter {
 *     public:
 *         // SPI for module use.
 *         SK_SPI Iter();
 *         SK_SPI Iter(const char path[], const char suffix[] = nullptr);
 *         SK_SPI ~Iter();
 *
 *         SK_SPI void reset(const char path[], const char suffix[] = nullptr);
 *         /** If getDir is true, only returns directories.
 *             Results are undefined if true and false calls are
 *             interleaved on a single iterator.
 *         */
 *         SK_SPI bool next(SkString* name, bool getDir = false);
 *
 *         static const size_t kStorageSize = 40;
 *     private:
 *         alignas(void*) alignas(double) char fSelf[kStorageSize];
 *     };
 * }
 * ```
 */
public open class SkOSFile {
  public open class Iter public constructor() {
    private var fSelf: Int = TODO("Initialize fSelf")

    public constructor(path: CharArray, suffix: CharArray = null) : this() {
      TODO("Implement constructor")
    }

    public fun reset(path: CharArray, suffix: CharArray = null) {
      TODO("Implement reset")
    }

    public fun next(name: String?, getDir: Boolean = false): Boolean {
      TODO("Implement next")
    }

    public companion object {
      public val kStorageSize: Int = TODO("Initialize kStorageSize")
    }
  }
}

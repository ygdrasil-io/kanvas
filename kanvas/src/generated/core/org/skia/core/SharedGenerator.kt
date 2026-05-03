package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SharedGenerator final : public SkNVRefCnt<SharedGenerator> {
 * public:
 *     static sk_sp<SharedGenerator> Make(std::unique_ptr<SkImageGenerator> gen);
 *
 *     // This is thread safe.  It is a const field set in the constructor.
 *     const SkImageInfo& getInfo() const;
 *
 *     bool isTextureGenerator();
 *
 *     std::unique_ptr<SkImageGenerator> fGenerator;
 *     SkMutex                           fMutex;
 *
 * private:
 *     explicit SharedGenerator(std::unique_ptr<SkImageGenerator> gen);
 * }
 * ```
 */
public class SharedGenerator public constructor(
  gen: SkImageGenerator?,
) : SkNVRefCnt(),
    SharedGenerator {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkImageGenerator> fGenerator
   * ```
   */
  public var fGenerator: Int = TODO("Initialize fGenerator")

  /**
   * C++ original:
   * ```cpp
   * SkMutex                           fMutex
   * ```
   */
  public var fMutex: SkMutex = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& SharedGenerator::getInfo() const { return fGenerator->getInfo(); }
   * ```
   */
  public override fun getInfo(): SkImageInfo {
    TODO("Implement getInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SharedGenerator::isTextureGenerator() { return fGenerator->isTextureGenerator(); }
   * ```
   */
  public override fun isTextureGenerator(): Boolean {
    TODO("Implement isTextureGenerator")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SharedGenerator> Make(std::unique_ptr<SkImageGenerator> gen)
     * ```
     */
    public override fun make(gen: SkImageGenerator?): SkSp<SharedGenerator> {
      TODO("Implement make")
    }
  }
}

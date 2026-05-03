package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.Caps
import org.skia.gpu.Recorder

/**
 * C++ original:
 * ```cpp
 * class Mutator {
 * public:
 *     Mutator(skiatest::Reporter* reporter, Recorder* recorder, bool withMips)
 *             : fReporter(reporter)
 *             , fRecorder(recorder)
 *             , fWithMips(withMips) {
 *     }
 *     virtual ~Mutator() = default;
 *
 *     virtual std::unique_ptr<Recording> init(const Caps*) = 0;
 *     virtual std::unique_ptr<Recording> mutate(int mutationIndex) = 0;
 *     virtual int getCase() const = 0;
 *
 *     SkImage* getMutatingImage() {
 *         return fMutatingImg.get();
 *     }
 *
 * protected:
 *     skiatest::Reporter* fReporter;
 *     Recorder* fRecorder;
 *     bool fWithMips;
 *
 *     sk_sp<SkImage> fMutatingImg; // needs to be created in the 'init' method
 * }
 * ```
 */
public abstract class Mutator public constructor(
  reporter: Reporter?,
  recorder: Recorder?,
  withMips: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  protected var fReporter: Reporter? = TODO("Initialize fReporter")

  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  protected var fRecorder: Recorder? = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * bool fWithMips
   * ```
   */
  protected var fWithMips: Boolean = TODO("Initialize fWithMips")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMutatingImg
   * ```
   */
  protected var fMutatingImg: SkSp<SkImage> = TODO("Initialize fMutatingImg")

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<Recording> init(const Caps*) = 0
   * ```
   */
  public abstract fun `init`(param0: Caps?): Int

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<Recording> mutate(int mutationIndex) = 0
   * ```
   */
  public abstract fun mutate(mutationIndex: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int getCase() const = 0
   * ```
   */
  public abstract fun getCase(): Int

  /**
   * C++ original:
   * ```cpp
   * SkImage* getMutatingImage() {
   *         return fMutatingImg.get();
   *     }
   * ```
   */
  public fun getMutatingImage(): SkImage {
    TODO("Implement getMutatingImage")
  }
}

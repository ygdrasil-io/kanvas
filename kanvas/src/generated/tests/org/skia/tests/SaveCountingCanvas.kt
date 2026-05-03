package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.math.SkRect
import undefined.SaveLayerRec
import undefined.SaveLayerStrategy

/**
 * C++ original:
 * ```cpp
 * class SaveCountingCanvas : public SkCanvas {
 * public:
 *     SaveCountingCanvas(int width, int height)
 *         : INHERITED(width, height)
 *         , fSaveCount(0)
 *         , fSaveLayerCount(0)
 *         , fSaveBehindCount(0)
 *         , fRestoreCount(0){
 *     }
 *
 *     SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec& rec) override {
 *         ++fSaveLayerCount;
 *         return this->INHERITED::getSaveLayerStrategy(rec);
 *     }
 *
 *     bool onDoSaveBehind(const SkRect* subset) override {
 *         ++fSaveBehindCount;
 *         return this->INHERITED::onDoSaveBehind(subset);
 *     }
 *
 *     void willSave() override {
 *         ++fSaveCount;
 *         this->INHERITED::willSave();
 *     }
 *
 *     void willRestore() override {
 *         ++fRestoreCount;
 *         this->INHERITED::willRestore();
 *     }
 *
 *     unsigned int getSaveCount() const { return fSaveCount; }
 *     unsigned int getSaveLayerCount() const { return fSaveLayerCount; }
 *     unsigned int getSaveBehindCount() const { return fSaveBehindCount; }
 *     unsigned int getRestoreCount() const { return fRestoreCount; }
 *
 * private:
 *     unsigned int fSaveCount;
 *     unsigned int fSaveLayerCount;
 *     unsigned int fSaveBehindCount;
 *     unsigned int fRestoreCount;
 *
 *     using INHERITED = SkCanvas;
 * }
 * ```
 */
public open class SaveCountingCanvas public constructor(
  width: Int,
  height: Int,
) : SkCanvas(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * unsigned int fSaveCount
   * ```
   */
  private var fSaveCount: UInt = TODO("Initialize fSaveCount")

  /**
   * C++ original:
   * ```cpp
   * unsigned int fSaveLayerCount
   * ```
   */
  private var fSaveLayerCount: UInt = TODO("Initialize fSaveLayerCount")

  /**
   * C++ original:
   * ```cpp
   * unsigned int fSaveBehindCount
   * ```
   */
  private var fSaveBehindCount: UInt = TODO("Initialize fSaveBehindCount")

  /**
   * C++ original:
   * ```cpp
   * unsigned int fRestoreCount
   * ```
   */
  private var fRestoreCount: UInt = TODO("Initialize fRestoreCount")

  /**
   * C++ original:
   * ```cpp
   * SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec& rec) override {
   *         ++fSaveLayerCount;
   *         return this->INHERITED::getSaveLayerStrategy(rec);
   *     }
   * ```
   */
  public override fun getSaveLayerStrategy(rec: SaveLayerRec): SaveLayerStrategy {
    TODO("Implement getSaveLayerStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onDoSaveBehind(const SkRect* subset) override {
   *         ++fSaveBehindCount;
   *         return this->INHERITED::onDoSaveBehind(subset);
   *     }
   * ```
   */
  public override fun onDoSaveBehind(subset: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void willSave() override {
   *         ++fSaveCount;
   *         this->INHERITED::willSave();
   *     }
   * ```
   */
  public override fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * void willRestore() override {
   *         ++fRestoreCount;
   *         this->INHERITED::willRestore();
   *     }
   * ```
   */
  public override fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned int getSaveCount() const { return fSaveCount; }
   * ```
   */
  public override fun getSaveCount(): UInt {
    TODO("Implement getSaveCount")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned int getSaveLayerCount() const { return fSaveLayerCount; }
   * ```
   */
  public fun getSaveLayerCount(): UInt {
    TODO("Implement getSaveLayerCount")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned int getSaveBehindCount() const { return fSaveBehindCount; }
   * ```
   */
  public fun getSaveBehindCount(): UInt {
    TODO("Implement getSaveBehindCount")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned int getRestoreCount() const { return fRestoreCount; }
   * ```
   */
  public fun getRestoreCount(): UInt {
    TODO("Implement getRestoreCount")
  }
}

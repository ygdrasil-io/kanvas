package org.skia.gpu

import kotlin.Int
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HybridBoundsManager : public BoundsManager {
 * public:
 *     HybridBoundsManager(const SkISize& deviceSize,
 *                         int gridCellSize,
 *                         int maxBruteForceN,
 *                         int maxGridSize=0)
 *             : fDeviceSize(deviceSize)
 *             , fGridCellSize(gridCellSize)
 *             , fMaxBruteForceN(maxBruteForceN)
 *             , fMaxGridSize(maxGridSize)
 *             , fCurrentManager(&fBruteForceManager) {
 *         SkASSERT(deviceSize.width() >= 1 && deviceSize.height() >= 1 &&
 *                  gridCellSize >= 1 && maxBruteForceN >= 1);
 *     }
 *
 *     CompressedPaintersOrder getMostRecentDraw(const Rect& bounds) const override {
 *         return fCurrentManager->getMostRecentDraw(bounds);
 *     }
 *
 *     void recordDraw(const Rect& bounds, CompressedPaintersOrder order) override {
 *         this->updateCurrentManagerIfNeeded();
 *         fCurrentManager->recordDraw(bounds, order);
 *     }
 *
 *     void reset() override {
 *         const bool usedGrid = fCurrentManager == fGridManager.get();
 *         if (usedGrid) {
 *             // Reset the grid manager so it's ready to use next frame, but don't delete it.
 *             fGridManager->reset();
 *             // Assume brute force manager was reset when we swapped to the grid originally
 *             fCurrentManager = &fBruteForceManager;
 *         } else {
 *             if (fGridManager) {
 *                 // Clean up the grid manager that was created over a frame ago without being used.
 *                 // This could lead to re-allocating the grid every-other frame, but it's a simple
 *                 // way to ensure we don't hold onto the grid in perpetuity if it's not needed.
 *                 fGridManager = nullptr;
 *             }
 *             fBruteForceManager.reset();
 *             SkASSERT(fCurrentManager == &fBruteForceManager);
 *         }
 *     }
 *
 * private:
 *     const SkISize fDeviceSize;
 *     const int     fGridCellSize;
 *     const int     fMaxBruteForceN;
 *     const int     fMaxGridSize;
 *
 *     BoundsManager* fCurrentManager;
 *
 *     BruteForceBoundsManager                  fBruteForceManager;
 *
 *     // The grid manager starts out null and is created the first time we exceed fMaxBruteForceN.
 *     // However, even if we reset back to the brute force manager, we keep the grid around under the
 *     // assumption that the owning Device will have similar frame-to-frame draw counts and will need
 *     // to upgrade to the grid manager again.
 *     std::unique_ptr<GridBoundsManager>       fGridManager;
 *
 *     void updateCurrentManagerIfNeeded() {
 *         if (fCurrentManager == fGridManager.get() ||
 *             fBruteForceManager.count() < fMaxBruteForceN) {
 *             // Already using the grid or the about-to-be-recorded draw will not cause us to exceed
 *             // the brute force limit, so no need to change the current manager implementation.
 *             return;
 *         }
 *         // Else we need to switch from the brute force manager to the grid manager
 *         if (!fGridManager) {
 *             fGridManager = GridBoundsManager::MakeRes(fDeviceSize, fGridCellSize, fMaxGridSize);
 *         }
 *         fCurrentManager = fGridManager.get();
 *
 *         // Fill out the grid manager with the recorded draws in the brute force manager
 *         fBruteForceManager.replayDraws(fCurrentManager);
 *         fBruteForceManager.reset();
 *     }
 * }
 * ```
 */
public open class HybridBoundsManager public constructor(
  deviceSize: SkISize,
  gridCellSize: Int,
  maxBruteForceN: Int,
  maxGridSize: Int = TODO(),
) : BoundsManager() {
  /**
   * C++ original:
   * ```cpp
   * const SkISize fDeviceSize
   * ```
   */
  private val fDeviceSize: Int = TODO("Initialize fDeviceSize")

  /**
   * C++ original:
   * ```cpp
   * const int     fGridCellSize
   * ```
   */
  private val fGridCellSize: Int = TODO("Initialize fGridCellSize")

  /**
   * C++ original:
   * ```cpp
   * const int     fMaxBruteForceN
   * ```
   */
  private val fMaxBruteForceN: Int = TODO("Initialize fMaxBruteForceN")

  /**
   * C++ original:
   * ```cpp
   * const int     fMaxGridSize
   * ```
   */
  private val fMaxGridSize: Int = TODO("Initialize fMaxGridSize")

  /**
   * C++ original:
   * ```cpp
   * BoundsManager* fCurrentManager
   * ```
   */
  private var fCurrentManager: BoundsManager? = TODO("Initialize fCurrentManager")

  /**
   * C++ original:
   * ```cpp
   * BruteForceBoundsManager                  fBruteForceManager
   * ```
   */
  private var fBruteForceManager: BruteForceBoundsManager = TODO("Initialize fBruteForceManager")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<GridBoundsManager>       fGridManager
   * ```
   */
  private var fGridManager: Int = TODO("Initialize fGridManager")

  /**
   * C++ original:
   * ```cpp
   * CompressedPaintersOrder getMostRecentDraw(const Rect& bounds) const override {
   *         return fCurrentManager->getMostRecentDraw(bounds);
   *     }
   * ```
   */
  public override fun getMostRecentDraw(bounds: Rect): Int {
    TODO("Implement getMostRecentDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void recordDraw(const Rect& bounds, CompressedPaintersOrder order) override {
   *         this->updateCurrentManagerIfNeeded();
   *         fCurrentManager->recordDraw(bounds, order);
   *     }
   * ```
   */
  public override fun recordDraw(bounds: Rect, order: CompressedPaintersOrder) {
    TODO("Implement recordDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() override {
   *         const bool usedGrid = fCurrentManager == fGridManager.get();
   *         if (usedGrid) {
   *             // Reset the grid manager so it's ready to use next frame, but don't delete it.
   *             fGridManager->reset();
   *             // Assume brute force manager was reset when we swapped to the grid originally
   *             fCurrentManager = &fBruteForceManager;
   *         } else {
   *             if (fGridManager) {
   *                 // Clean up the grid manager that was created over a frame ago without being used.
   *                 // This could lead to re-allocating the grid every-other frame, but it's a simple
   *                 // way to ensure we don't hold onto the grid in perpetuity if it's not needed.
   *                 fGridManager = nullptr;
   *             }
   *             fBruteForceManager.reset();
   *             SkASSERT(fCurrentManager == &fBruteForceManager);
   *         }
   *     }
   * ```
   */
  public override fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCurrentManagerIfNeeded() {
   *         if (fCurrentManager == fGridManager.get() ||
   *             fBruteForceManager.count() < fMaxBruteForceN) {
   *             // Already using the grid or the about-to-be-recorded draw will not cause us to exceed
   *             // the brute force limit, so no need to change the current manager implementation.
   *             return;
   *         }
   *         // Else we need to switch from the brute force manager to the grid manager
   *         if (!fGridManager) {
   *             fGridManager = GridBoundsManager::MakeRes(fDeviceSize, fGridCellSize, fMaxGridSize);
   *         }
   *         fCurrentManager = fGridManager.get();
   *
   *         // Fill out the grid manager with the recorded draws in the brute force manager
   *         fBruteForceManager.replayDraws(fCurrentManager);
   *         fBruteForceManager.reset();
   *     }
   * ```
   */
  private fun updateCurrentManagerIfNeeded() {
    TODO("Implement updateCurrentManagerIfNeeded")
  }
}

package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkStrikePinner
import org.skia.foundation.SkSp
import org.skia.utils.SkDiscardableHandleId
import org.skia.utils.SkStrikeClient

/**
 * C++ original:
 * ```cpp
 * class DiscardableStrikePinner : public SkStrikePinner {
 * public:
 *     DiscardableStrikePinner(SkDiscardableHandleId discardableHandleId,
 *                             sk_sp<SkStrikeClient::DiscardableHandleManager> manager)
 *             : fDiscardableHandleId(discardableHandleId), fManager(std::move(manager)) {}
 *
 *     ~DiscardableStrikePinner() override = default;
 *     bool canDelete() override { return fManager->deleteHandle(fDiscardableHandleId); }
 *     void assertValid() override { fManager->assertHandleValid(fDiscardableHandleId); }
 *
 * private:
 *     const SkDiscardableHandleId fDiscardableHandleId;
 *     sk_sp<SkStrikeClient::DiscardableHandleManager> fManager;
 * }
 * ```
 */
public open class DiscardableStrikePinner public constructor(
  discardableHandleId: SkDiscardableHandleId,
  manager: SkSp<SkStrikeClient.DiscardableHandleManager>,
) : SkStrikePinner() {
  /**
   * C++ original:
   * ```cpp
   * const SkDiscardableHandleId fDiscardableHandleId
   * ```
   */
  private val fDiscardableHandleId: Int = TODO("Initialize fDiscardableHandleId")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrikeClient::DiscardableHandleManager> fManager
   * ```
   */
  private var fManager: SkSp<SkStrikeClient.DiscardableHandleManager> = TODO("Initialize fManager")

  /**
   * C++ original:
   * ```cpp
   * bool canDelete() override { return fManager->deleteHandle(fDiscardableHandleId); }
   * ```
   */
  public override fun canDelete(): Boolean {
    TODO("Implement canDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void assertValid() override { fManager->assertHandleValid(fDiscardableHandleId); }
   * ```
   */
  public override fun assertValid() {
    TODO("Implement assertValid")
  }
}

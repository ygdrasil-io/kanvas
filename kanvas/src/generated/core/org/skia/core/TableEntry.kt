package org.skia.core

import org.skia.foundation.CicpId
import org.skia.modules.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * struct TableEntry {
 *     CicpId cicp_id;
 *     skcms_TransferFunction trfn;
 * }
 * ```
 */
public data class TableEntry public constructor(
  /**
   * C++ original:
   * ```cpp
   * CicpId cicp_id
   * ```
   */
  public var cicpId: CicpId,
  /**
   * C++ original:
   * ```cpp
   * skcms_TransferFunction trfn
   * ```
   */
  public var trfn: SkcmsTransferFunction,
)

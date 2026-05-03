package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UByte
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SK_API SkColorTable : public SkRefCnt {
 * public:
 *     // Creates a new SkColorTable with 'table' used for all four channels. The table is copied into
 *     // the SkColorTable.
 *     static sk_sp<SkColorTable> Make(const uint8_t table[256]) {
 *         return Make(table, table, table, table);
 *     }
 *
 *     // Creates a new SkColorTable with the per-channel lookup tables. Each non-null table is copied
 *     // into the SkColorTable. Null parameters are interpreted as the identity table.
 *     static sk_sp<SkColorTable> Make(const uint8_t tableA[256],
 *                                     const uint8_t tableR[256],
 *                                     const uint8_t tableG[256],
 *                                     const uint8_t tableB[256]);
 *
 *     // Per-channel constant value lookup (0-255).
 *     const uint8_t* alphaTable() const { return fTable.getAddr8(0, 0); }
 *     const uint8_t* redTable()   const { return fTable.getAddr8(0, 1); }
 *     const uint8_t* greenTable() const { return fTable.getAddr8(0, 2); }
 *     const uint8_t* blueTable()  const { return fTable.getAddr8(0, 3); }
 *
 *     void flatten(SkWriteBuffer& buffer) const;
 *
 *     static sk_sp<SkColorTable> Deserialize(SkReadBuffer& buffer);
 *
 * private:
 *     friend class SkTableColorFilter; // for bitmap()
 *
 *     explicit SkColorTable(const SkBitmap& table) : fTable(table) {}
 *
 *     // The returned SkBitmap is immutable; attempting to modify its pixel data will trigger asserts
 *     // in debug builds and cause undefined behavior in release builds.
 *     const SkBitmap& bitmap() const { return fTable; }
 *
 *     SkBitmap fTable; // A 256x4 A8 image
 * }
 * ```
 */
public open class SkColorTable public constructor(
  table: SkBitmap,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fTable
   * ```
   */
  private var fTable: Int = TODO("Initialize fTable")

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* alphaTable() const { return fTable.getAddr8(0, 0); }
   * ```
   */
  public fun alphaTable(): UByte {
    TODO("Implement alphaTable")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* redTable()   const { return fTable.getAddr8(0, 1); }
   * ```
   */
  public fun redTable(): UByte {
    TODO("Implement redTable")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* greenTable() const { return fTable.getAddr8(0, 2); }
   * ```
   */
  public fun greenTable(): UByte {
    TODO("Implement greenTable")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* blueTable()  const { return fTable.getAddr8(0, 3); }
   * ```
   */
  public fun blueTable(): UByte {
    TODO("Implement blueTable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorTable::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeByteArray(fTable.getAddr8(0, 0), 4 * 256);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap& bitmap() const { return fTable; }
   * ```
   */
  private fun bitmap(): Int {
    TODO("Implement bitmap")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorTable> Make(const uint8_t table[256]) {
     *         return Make(table, table, table, table);
     *     }
     * ```
     */
    public fun make(table: Array<UByte>): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorTable> SkColorTable::Make(const uint8_t tableA[256],
     *                                        const uint8_t tableR[256],
     *                                        const uint8_t tableG[256],
     *                                        const uint8_t tableB[256]) {
     *     if (!tableA && !tableR && !tableG && !tableB) {
     *         return nullptr; // The table is the identity
     *     }
     *
     *     SkBitmap table;
     *     if (!table.tryAllocPixels(SkImageInfo::MakeA8(256, 4))) {
     *         return nullptr;
     *     }
     *     uint8_t *a = table.getAddr8(0,0),
     *             *r = table.getAddr8(0,1),
     *             *g = table.getAddr8(0,2),
     *             *b = table.getAddr8(0,3);
     *     for (int i = 0; i < 256; i++) {
     *         a[i] = tableA ? tableA[i] : i;
     *         r[i] = tableR ? tableR[i] : i;
     *         g[i] = tableG ? tableG[i] : i;
     *         b[i] = tableB ? tableB[i] : i;
     *     }
     *     table.setImmutable();
     *
     *     return sk_sp<SkColorTable>(new SkColorTable(table));
     * }
     * ```
     */
    public fun make(
      tableA: Array<UByte>,
      tableR: Array<UByte>,
      tableG: Array<UByte>,
      tableB: Array<UByte>,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorTable> SkColorTable::Deserialize(SkReadBuffer& buffer) {
     *     uint8_t argb[4*256];
     *     if (buffer.readByteArray(argb, sizeof(argb))) {
     *         return SkColorTable::Make(argb+0*256, argb+1*256, argb+2*256, argb+3*256);
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public fun deserialize(buffer: SkReadBuffer): Int {
      TODO("Implement deserialize")
    }
  }
}

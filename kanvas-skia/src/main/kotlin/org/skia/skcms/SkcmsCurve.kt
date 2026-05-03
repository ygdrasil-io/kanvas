package org.skia.skcms

/**
 * Bit-compatible port of `skcms_Curve`
 * ([modules/skcms/src/skcms_public.h:159-173](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Unified representation of an ICC `'curv'` or `'para'` tag, or a 1-D
 * table from `'mft1'` / `'mft2'`. C uses an anonymous union with
 * `table_entries == 0` meaning "parametric"; we use a sealed hierarchy.
 *
 * Phase F1: data shape only. Evaluation (`evalCurve`) lands in Phase F3.
 */
public sealed class SkcmsCurve {
    public abstract val tableEntries: Int

    /** Parametric curve described by a 7-parameter [SkcmsTransferFunction]. */
    public data class Parametric(
        public val parametric: SkcmsTransferFunction,
    ) : SkcmsCurve() {
        override val tableEntries: Int get() = 0
    }

    /**
     * 1-D LUT curve. Exactly one of [table8] / [table16] is non-null
     * depending on whether the ICC encoded the table as `uint8` or `uint16`.
     * `tableEntries` is the number of entries; for `table16` the byte
     * length is `2 * tableEntries`.
     *
     * Note: skcms upstream stores big-endian `uint16` here (per ICC spec).
     * `evalCurve` (Phase F3) handles the byte-swap.
     */
    public data class Table(
        override val tableEntries: Int,
        public val table8: ByteArray? = null,
        public val table16: ByteArray? = null,
    ) : SkcmsCurve() {
        init {
            require(tableEntries > 0) { "Table curve must have entries > 0" }
            require((table8 == null) != (table16 == null)) {
                "Table curve must have exactly one of table8 / table16 set"
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Table) return false
            if (tableEntries != other.tableEntries) return false
            if (!table8.contentNullableEquals(other.table8)) return false
            if (!table16.contentNullableEquals(other.table16)) return false
            return true
        }

        override fun hashCode(): Int {
            var h = tableEntries
            h = 31 * h + (table8?.contentHashCode() ?: 0)
            h = 31 * h + (table16?.contentHashCode() ?: 0)
            return h
        }

        private fun ByteArray?.contentNullableEquals(other: ByteArray?): Boolean {
            if (this == null) return other == null
            if (other == null) return false
            return contentEquals(other)
        }
    }
}

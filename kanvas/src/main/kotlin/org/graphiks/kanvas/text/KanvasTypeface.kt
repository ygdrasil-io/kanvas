package org.graphiks.kanvas.text

/**
 * A reference to a typeface resource accessible via [resourcePath].
 *
 * @property resourcePath  location of the font file (e.g. a bundled asset or
 *                         filesystem path).
 */
data class KanvasTypeface(val resourcePath: String)

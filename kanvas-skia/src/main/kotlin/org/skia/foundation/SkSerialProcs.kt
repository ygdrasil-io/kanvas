package org.skia.foundation

import org.skia.core.SkPicture

/**
 * R2.17 surface mirror of upstream's
 * [`SkSerialProcs`](https://github.com/google/skia/blob/main/include/core/SkSerialProcs.h).
 *
 * The upstream struct is a "function table" handed to
 * `SkPicture::serialize(...)` so callers can plug custom encoders
 * for the three serialisable Skia objects — pictures, images, and
 * typefaces. The Kotlin port models each `*Proc` as a nullable
 * lambda : a `null` lambda means "fall back to Skia's default
 * serialiser", matching upstream's contract for a `null` function
 * pointer.
 *
 * **Status — surface only** : the per-object serialiser machinery
 * (`SkPicture.serialize(procs)`, `SkPicture.MakeFromData(data,
 * procs)`, `SkImage::Make*Encoded(procs)`) is **not** consumed by
 * the R2 batch — only the configuration struct is exposed so a
 * downstream port that needs to *declare* a hook can compile. The
 * matching consumption is scheduled for the picture-serialisation
 * slice (out of scope here, tracked in R-suivi).
 *
 * **Field shape** — each "proc" is a `(object, ctx) → SkData?`
 * function ; `imageCtx` / `pictureCtx` / `typefaceCtx` carry the
 * opaque `void* ctx` that upstream's struct passes back to the
 * callback. Kotlin lambdas already close over their environment, so
 * the `ctx` slots are mostly here for source-compatibility with
 * upstream call sites — passing `null` is fine.
 */
public data class SkSerialProcs(
    /**
     * Mirror of `SkSerialImageProc`. Called when an image needs
     * encoding ; return `null` to fall back to PNG / the image's
     * native encoder.
     */
    val image: ((SkImage, Any?) -> SkData?)? = null,
    /**
     * Mirror of `SkSerialPictureProc`. Called when a nested picture
     * is reached during serialisation ; return `null` to use
     * upstream's internal format.
     */
    val picture: ((SkPicture, Any?) -> SkData?)? = null,
    /**
     * Mirror of `SkSerialTypefaceProc`. Called when a typeface is
     * encountered ; return `null` to use upstream's internal
     * typeface stream.
     */
    val typeface: ((SkTypeface, Any?) -> SkData?)? = null,
    /** Opaque context handed to [image]. Matches upstream's `fImageCtx`. */
    val imageCtx: Any? = null,
    /** Opaque context handed to [picture]. Matches upstream's `fPictureCtx`. */
    val pictureCtx: Any? = null,
    /** Opaque context handed to [typeface]. Matches upstream's `fTypefaceCtx`. */
    val typefaceCtx: Any? = null,
)

/**
 * R2.17 surface mirror of upstream's
 * [`SkDeserialProcs`](https://github.com/google/skia/blob/main/include/core/SkSerialProcs.h)
 * — the de-serialisation counterpart of [SkSerialProcs].
 *
 * Each proc takes the encoded bytes (an [SkData]) and rebuilds the
 * Kotlin object ; returning `null` lets the deserialiser fall back
 * to Skia's default decoder, exactly mirroring upstream's contract
 * for a `null` function pointer.
 *
 * Same R2 caveat as [SkSerialProcs] — the surface is exposed so
 * call sites that *declare* a deserialisation hook can compile, but
 * `SkPicture.MakeFromData(data, procs)` doesn't consume it yet.
 */
public data class SkDeserialProcs(
    /**
     * Mirror of `SkDeserialImageProc`. Receives the encoded image
     * bytes ; return `null` to fall back to Skia's default image
     * codec.
     */
    val image: ((SkData, Any?) -> SkImage?)? = null,
    /**
     * Mirror of `SkDeserialPictureProc`. Receives the encoded
     * picture bytes ; return `null` to fall back to Skia's internal
     * format.
     */
    val picture: ((SkData, Any?) -> SkPicture?)? = null,
    /**
     * Mirror of `SkDeserialTypefaceProc`. Receives the encoded
     * typeface bytes ; return `null` to fall back to Skia's
     * internal typeface stream.
     */
    val typeface: ((SkData, Any?) -> SkTypeface?)? = null,
    /** Opaque context handed to [image]. */
    val imageCtx: Any? = null,
    /** Opaque context handed to [picture]. */
    val pictureCtx: Any? = null,
    /** Opaque context handed to [typeface]. */
    val typefaceCtx: Any? = null,
)

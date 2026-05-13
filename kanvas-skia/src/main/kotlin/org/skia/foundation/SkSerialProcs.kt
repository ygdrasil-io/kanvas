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
 * **Status — R-suivi.22 / S6-C** : the [image] / [picture] /
 * [typeface] procs are wired through [SkPicture.serialize] /
 * [SkPicture.MakeFromData] — each fires once per embedded blob,
 * with its `*Ctx` threaded through. `SkImage::Make*Encoded(procs)`
 * is still surface-only — that lives further upstream of the
 * picture path.
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
     * is reached during serialisation ; return `null` to fall back
     * to the recursive default (`subPicture.serialize(procs)`).
     * Wired through [SkPicture.serialize] as of R-suivi.22 / S6-C.
     */
    val picture: ((SkPicture, Any?) -> SkData?)? = null,
    /**
     * Mirror of `SkSerialTypefaceProc`. Called when a typeface is
     * encountered ; return `null` to emit a zero-length placeholder
     * blob (no default typeface serialiser is wired yet — see
     * R-suivi). Reached via every text-bearing record's font.
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
 * Wired through [SkPicture.MakeFromData] as of R-suivi.22 / S6-C —
 * each proc fires once per encoded blob, in encounter order, with
 * its `*Ctx` threaded through.
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

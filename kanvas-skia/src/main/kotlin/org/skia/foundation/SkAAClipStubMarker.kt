package org.skia.foundation

/**
 * R-final.S **STUB.AAClip** marker — documents the relationship
 * between the entry in
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * and the actual state of [SkAAClip] in this tree.
 *
 * The plan lists `STUB.AAClip` because upstream Skia treats
 * `SkAAClip` as a *Skia-internal* anti-aliased clip stack — never
 * exposed in upstream's public C++ API surface. There is therefore
 * nothing for downstream consumers to bind to "match" upstream.
 *
 * **What ships today** : [SkAAClip] is **fully implemented in
 * pure Kotlin** in this tree (see
 * [SkAAClip.setRect] / [SkAAClip.setRegion] / [SkAAClip.setPath] /
 * [SkAAClip.op] — Phase I3.2.a-c). It is the production raster-
 * clip carrier behind `SkBitmapDevice`. The "stub" tag in the plan
 * therefore tracks the *upstream-public-API* gap, not the runtime
 * gap — there is nothing to backfill on this side.
 *
 * If a future call site needs the upstream signature shape
 * (`setRect(rect, doAA)` / `op(other, SkClipOp)`), a thin
 * compatibility layer can wrap the existing API without disturbing
 * either party.
 *
 * This file is intentionally **only documentation** — no symbols.
 */
@Suppress("unused")
internal const val AACLIP_STUB_MARKER: String =
    "STUB.AAClip: see API_FINALIZATION_PLAN.md (SkAAClip is fully implemented; tag tracks upstream-public-API gap)"

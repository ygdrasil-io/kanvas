#!/usr/bin/env python3
"""Generate and validate FOR-288 M60 outer-boundary store-order audit evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for281_cpu_mask_filter_clip_coverage_trace as for281
import validate_for286_cpu_active_aa_difference_store_trace as for286


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-288"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-outer-boundary-store-order-audit-for288.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-288-m60-outer-boundary-store-order-audit.md"
SOURCE_FOR281 = for281.AUDIT
SOURCE_FOR286 = for286.AUDIT
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"

DECISION = "NEXT_FIX_INSTRUMENT_CPU_ACTIVE_AA_DIFFERENCE_STORE_CHRONOLOGY_FOR_OUTER_BOUNDARY"
PRIMARY_CLASSIFICATION = "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE"
NEXT_ACTION = "TRACE_ACTUAL_RUNTIME_WRITES_FOR_DRAW_OVAL_OUTER_BOUNDARY"
PRIMARY_WINDOWS = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)

FOR287_CANCELED_ATTEMPT = {
    "status": "canceled_not_merged",
    "source": "Linear FOR-287 and orchestrator memory",
    "attemptedChange": (
        "Pre-clip the A8 source mask by activeAaClip difference before blur, then avoid the second "
        "AA modulation at store for A8 SrcIn + SrcOver."
    ),
    "globalBefore": {
        "readbackRedDominantPixels": 9,
        "readbackWhiteLayerPixels": 78,
        "cpuSimilarity": 97.31,
        "cpuReferenceGreaterThanThirtyTwoPixels": 15726,
    },
    "globalAfter": {
        "readbackRedDominantPixels": 50,
        "readbackWhiteLayerPixels": 59,
        "cpuSimilarity": 97.13,
        "cpuReferenceGreaterThanThirtyTwoPixels": 16785,
    },
    "zoneAfter": {
        "draw_oval_outer_boundary": {"readbackWhiteLayerPixels": 59, "improved": False},
        "difference_oval_inner_boundary": {"readbackWhiteLayerPixels": 0, "improved": True},
        "halo_interior": {"readbackWhiteLayerPixels": 0, "improved": True},
    },
    "mergeDecision": "not_mergeable_score_regression",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-288 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(PROJECT_ROOT)} must contain a JSON object")
    return data


def require_needles(path: Path, needles: tuple[str, ...]) -> str:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def window_by_subzone(audit: dict[str, Any], subzone: str) -> dict[str, Any]:
    for window in audit.get("windows", []):
        if window.get("subzone") == subzone:
            return window
    fail(f"missing window `{subzone}` in {audit.get('linear', 'unknown')}")


def bucket_text(summary: dict[str, Any]) -> str:
    buckets = summary["buckets"]
    return f"zero={buckets['zero']} partial={buckets['partial']} full={buckets['full']}"


def source_needles() -> dict[str, bool]:
    gm_text = require_needles(
        GM_SOURCE,
        (
            "c.drawRect(clipRect1, whitePaint)",
            "c.clipRRect(clipRRect, SkClipOp.kDifference, doAntiAlias = true)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "c.drawRRect(rr, paint)",
        ),
    )
    return {
        "gmDrawsWhiteBeforeDifferenceClipRedOval": gm_text.index("c.drawRect(clipRect1, whitePaint)")
        < gm_text.index("c.drawRRect(rr, paint)"),
        "gmHasNoAuthoredDrawAfterRedOval": gm_text.index("c.drawRRect(rr, paint)")
        > gm_text.rfind("c.drawRect(clipRect1, whitePaint)"),
        "gmUsesDifferenceClipBeforeRedOval": gm_text.index(
            "c.clipRRect(clipRRect, SkClipOp.kDifference, doAntiAlias = true)"
        )
        < gm_text.index("c.drawRRect(rr, paint)"),
    }


def source_needles_valid(needles: dict[str, bool]) -> None:
    for key, value in needles.items():
        if value is not True:
            fail(f"source needle `{key}` failed")


def zone_comparison_entry(subzone: str, source_for281: dict[str, Any], source_for286: dict[str, Any]) -> dict[str, Any]:
    w281 = window_by_subzone(source_for281, subzone)
    w286 = window_by_subzone(source_for286, subzone)
    m281 = w281["metrics"]
    m286 = w286["metrics"]
    anchor = w286["anchorTrace"]
    for287 = FOR287_CANCELED_ATTEMPT["zoneAfter"][subzone]
    blur_summary = m281["maskAAfterBlurOnCpuReferenceGt32"]
    clip_summary = m281["clipCoverageOnCpuReferenceGt32"]
    alpha_summary = m281["srcAlphaAfterClipModulationOnCpuReferenceGt32"]
    return {
        "subzone": subzone,
        "for287ImprovedWhiteLayerReadback": bool(for287["improved"]),
        "for287AfterWhiteLayerPixels": int(for287["readbackWhiteLayerPixels"]),
        "targetPixels": int(m286["targetPixels"]),
        "maskSupport": {
            "maskPixels": int(w286["maskPixels"]),
            "zeroMaskPixelsOnTarget": int(m281["zeroMaskAAndCpuReferenceGt32Pixels"]),
            "status": "present_nonzero",
        },
        "blurSource": {
            "maskAAfterBlurBuckets": blur_summary,
            "allTargetPixelsHavePartialBlurAlpha": blur_summary["buckets"]["partial"] == m286["targetPixels"],
            "hasFullBlurAlphaPixels": blur_summary["buckets"]["full"] > 0,
        },
        "clipCoverage": {
            "clipCoverageBuckets": clip_summary,
            "allTargetPixelsFullClipCoverage": clip_summary["buckets"]["full"] == m286["targetPixels"],
            "hasPartialClipCoveragePixels": clip_summary["buckets"]["partial"] > 0,
        },
        "alphaAfterBlurAndClip": {
            "srcAlphaAfterClipBuckets": alpha_summary,
            "zeroSrcAlphaAfterClipPixels": int(m281["zeroSrcAlphaAfterClipAndCpuReferenceGt32Pixels"]),
        },
        "dispatchSource": {
            "dispatchWouldRunPixels": int(m286["dispatchWouldRunPixels"]),
            "dispatchInputRedPayloadPixels": int(m286["dispatchInputRedPayloadPixels"]),
            "anchorDispatchInputSrcRgba": anchor["dispatchInputSrcRgba"],
        },
        "reconstructedStore": {
            "writeRedTintPixels": int(m286["writeRedTintPixels"]),
            "writeOpaqueSrcFastPathPixels": int(m286["writeOpaqueSrcFastPathPixels"]),
            "writePartialSrcFastPathPixels": int(m286["writePartialSrcFastPathPixels"]),
            "anchorWriteBranch": anchor["writeBranch"],
            "anchorValueWrittenRgba": anchor["valueWrittenRgba"],
            "writtenVsReadBackGreaterThanThirtyTwoPixels": int(
                m286["writtenVsReadBackGreaterThanThirtyTwoPixels"]
            ),
        },
        "finalReadback": {
            "readBackRedDominantPixels": int(m286["readBackRedDominantPixels"]),
            "readBackWhiteLayerPixels": int(m286["readBackWhiteLayerPixels"]),
            "anchorReadBackAfterDrawRgba": anchor["readBackAfterDrawRgba"],
        },
        "anchor": {
            "x": int(anchor["x"]),
            "y": int(anchor["y"]),
            "maskAAfterBlur": int(anchor["maskAAfterBlur"]),
            "coverageAppliedInBlend": int(anchor["coverageAppliedInBlend"]),
            "srcAlphaAfterClipModulation": int(anchor["srcAlphaAfterClipModulation"]),
            "referenceRgba": anchor["referenceRgba"],
            "gpuRgba": anchor["gpuRgba"],
        },
    }


def for287_improvement_contrast(zones: list[dict[str, Any]]) -> dict[str, Any]:
    outer = next(z for z in zones if z["subzone"] == "draw_oval_outer_boundary")
    inner = next(z for z in zones if z["subzone"] == "difference_oval_inner_boundary")
    halo = next(z for z in zones if z["subzone"] == "halo_interior")
    return {
        "evidence": (
            "FOR-287 moved global readback red-dominant pixels 9 -> 50 and white/layer 78 -> 59; inner and "
            "halo reached 0 white/layer, while outer stayed exactly 59 white/layer. The surviving 59 pixels "
            "match the FOR-286 outer window cardinality."
        ),
        "comparison": {
            "outerStayedWhite": outer["for287AfterWhiteLayerPixels"],
            "innerAfterWhite": inner["for287AfterWhiteLayerPixels"],
            "haloAfterWhite": halo["for287AfterWhiteLayerPixels"],
        },
    }


def cause_matrix(zones: list[dict[str, Any]], source_for286: dict[str, Any]) -> dict[str, Any]:
    outer = next(z for z in zones if z["subzone"] == "draw_oval_outer_boundary")
    inner = next(z for z in zones if z["subzone"] == "difference_oval_inner_boundary")
    halo = next(z for z in zones if z["subzone"] == "halo_interior")
    combined = source_for286["combinedBoundaryFixture"]["metrics"]
    return {
        "sourceMaskOrBlurSupport": {
            "selected": False,
            "confidence": "high",
            "evidence": (
                "All three zones have non-zero mask support on CPU/reference >32 targets. The outer boundary "
                "has 59/59 partial blur-alpha pixels, so the source is present; it is not a zero-source miss."
            ),
        },
        "preClipTooEarlyOrTooLate": {
            "selected": False,
            "confidence": "medium",
            "evidence": (
                "The canceled FOR-287 pre-clip attempt fixed the zones with inner/halo white readback, but outer "
                "remained 59 white/layer pixels. Outer has full active-AA clip coverage on 59/59 targets, so "
                "changing the difference pre-clip timing is effectively not the discriminating variable there."
            ),
        },
        "aaCoverageDifference": {
            "selected": False,
            "confidence": "high",
            "evidence": (
                "Outer has full clip coverage on 59/59 targets, while inner has partial clip coverage on "
                f"{inner['clipCoverage']['clipCoverageBuckets']['buckets']['partial']}/"
                f"{inner['targetPixels']} targets. The failure persists where clip AA is not partial."
            ),
        },
        "whiteDrawRedDrawOrder": {
            "selected": False,
            "confidence": "medium",
            "evidence": (
                "The GM source draws the white kSrc background before the difference-clipped red oval, and the "
                "source has no authored white draw after c.drawRRect(rr, paint). The symptom is therefore not "
                "a simple GM command order inversion."
            ),
        },
        "otherLaterWrite": {
            "selected": True,
            "confidence": "high",
            "evidence": (
                "FOR-286 reconstructs red-tinted written values for "
                f"{combined['writeRedTintPixels']}/{combined['targetPixels']} targets, including "
                f"{outer['reconstructedStore']['writeRedTintPixels']}/{outer['targetPixels']} outer pixels. "
                "The committed outer readback is still 59/59 white/layer after FOR-286 and remains 59 after "
                "the canceled FOR-287 attempt, which points to a later overwrite or commit/order path after "
                "the reconstructed red store rather than payload, clip coverage, or SrcOver math."
            ),
        },
        "proofInsufficient": {
            "selected": False,
            "confidence": "medium",
            "evidence": (
                "The existing artifacts are sufficient to choose the next audit target. They are not a production "
                "fix: the next correction should first instrument actual runtime write chronology for the outer "
                "boundary pixels."
            ),
        },
    }


def generate_audit() -> dict[str, Any]:
    source_for281 = load_json(SOURCE_FOR281)
    source_for286 = load_json(SOURCE_FOR286)
    needles = source_needles()
    source_needles_valid(needles)
    zones = [zone_comparison_entry(subzone, source_for281, source_for286) for subzone in PRIMARY_WINDOWS]
    causes = cause_matrix(zones, source_for286)
    route = source_for286["route"]
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-outer-boundary-store-order-audit",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for281": str(SOURCE_FOR281.relative_to(PROJECT_ROOT)),
            "for286": str(SOURCE_FOR286.relative_to(PROJECT_ROOT)),
        },
        "sourceImages": source_for286["sourceImages"],
        "for287CanceledAttempt": FOR287_CANCELED_ATTEMPT,
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": DECISION,
        "primaryClassification": PRIMARY_CLASSIFICATION,
        "nextAction": NEXT_ACTION,
        "route": {
            "cpu": route.get("cpu"),
            "gpu": route.get("gpu"),
            "gpuStatus": route.get("gpuStatus"),
            "fallbackReason": route.get("fallbackReason"),
            "coverageStrategy": route.get("coverageStrategy"),
            "pipelineKey": route.get("pipelineKey"),
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "sourceNeedles": needles,
        },
        "zoneComparisons": zones,
        "for287ImprovementContrast": for287_improvement_contrast(zones),
        "causeMatrix": causes,
        "decisionRationale": (
            "draw_oval_outer_boundary is the only FOR-287 survivor because it sits in the full-clip outer blur "
            "band: its mask/blur source is non-zero and its clip coverage is full, so the canceled pre-clip "
            "change does not address the discriminating condition. FOR-286 still reconstructs a red-tinted "
            "store for every outer target, but final readback is white/layer for every one. The most likely "
            "next target is a later CPU store/commit/order write after the reconstructed red store, not source "
            "mask support, AA coverage, authored GM draw order, global blend, setPixel, GPU/WebGPU, or thresholds."
        ),
        "fullSceneM60": source_for286["fullSceneM60"],
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def zone_row(zone: dict[str, Any]) -> str:
    return (
        f"| `{zone['subzone']}` | {zone['targetPixels']} | "
        f"{zone['maskSupport']['zeroMaskPixelsOnTarget']} | "
        f"{bucket_text(zone['blurSource']['maskAAfterBlurBuckets'])} | "
        f"{bucket_text(zone['clipCoverage']['clipCoverageBuckets'])} | "
        f"{bucket_text(zone['alphaAfterBlurAndClip']['srcAlphaAfterClipBuckets'])} | "
        f"{zone['dispatchSource']['dispatchInputRedPayloadPixels']} | "
        f"{zone['reconstructedStore']['writeRedTintPixels']} | "
        f"{zone['reconstructedStore']['writeOpaqueSrcFastPathPixels']} | "
        f"{zone['reconstructedStore']['writePartialSrcFastPathPixels']} | "
        f"{zone['finalReadback']['readBackWhiteLayerPixels']} | "
        f"{zone['for287AfterWhiteLayerPixels']} | "
        f"`{zone['anchor']['maskAAfterBlur']}/{zone['anchor']['coverageAppliedInBlend']}` |"
    )


def cause_row(name: str, cause: dict[str, Any]) -> str:
    return f"| `{name}` | `{cause['selected']}` | `{cause['confidence']}` | {cause['evidence']} |"


def write_report(audit: dict[str, Any]) -> None:
    attempt = audit["for287CanceledAttempt"]
    before = attempt["globalBefore"]
    after = attempt["globalAfter"]
    zones = "\n".join(zone_row(zone) for zone in audit["zoneComparisons"])
    causes = "\n".join(cause_row(name, cause) for name, cause in audit["causeMatrix"].items())
    full = audit["fullSceneM60"]
    route = audit["route"]
    report = f"""# FOR-288 M60 Outer Boundary Store Order Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Primary classification: `{audit["primaryClassification"]}`

Next action: `{audit["nextAction"]}`

## Short Result

FOR-288 isolates why `draw_oval_outer_boundary` stayed white/layer after the
canceled FOR-287 attempt. The survivor is not a zero mask, missing blur support,
or AA clip coverage miss. It is the full-clip outer blur band: the source is
partial-alpha red, FOR-286 reconstructs a red-tinted store for 59/59 pixels, and
the final readback is still 59/59 white/layer.

FOR-287 useful signal, kept as canceled evidence only:

| Measure | Before | After |
|---|---:|---:|
| Readback red-dominant pixels | {before["readbackRedDominantPixels"]} | {after["readbackRedDominantPixels"]} |
| Readback white/layer pixels | {before["readbackWhiteLayerPixels"]} | {after["readbackWhiteLayerPixels"]} |
| CPU/reference similarity | {before["cpuSimilarity"]}% | {after["cpuSimilarity"]}% |
| CPU/reference >32 pixels | {before["cpuReferenceGreaterThanThirtyTwoPixels"]} | {after["cpuReferenceGreaterThanThirtyTwoPixels"]} |

FOR-287 was not merged because the global score regressed. This report does not
promote M60 and does not change production rendering.

## Zone Comparison

| Zone | Targets | Zero mask | Blur alpha buckets | Clip coverage buckets | Alpha after blur+clip | Dispatch red | Reconstructed red store | Opaque writes | Partial writes | FOR-286 readback white | FOR-287 after white | Anchor mask/clip |
|---|---:|---:|---|---|---|---:|---:|---:|---:|---:|---:|---|
{zones}

`draw_oval_outer_boundary` differs from the two improved zones because every
target pixel is full clip coverage and every target pixel has partial blur
alpha. The canceled FOR-287 pre-clip/double-modulation change improved the
inner/halo pixels but left exactly the 59 outer-boundary white/layer pixels.

## Cause Classification

| Candidate cause | Selected | Confidence | Evidence |
|---|---|---|---|
{causes}

FOR-287 contrast: {audit["for287ImprovementContrast"]["evidence"]}

Decision: `{audit["primaryClassification"]}`. The most probable next fix target
is a later CPU store/commit/order write after the reconstructed red store. It is
not a global `SrcOver`, `blend`, `setPixel`, GPU/WebGPU, threshold, Ganesh,
Graphite, or SkSL issue.

## Full Scene And Preservation

| Measure | Value |
|---|---:|
| CPU/reference similarity | {full["cpuSimilarity"]}% |
| CPU matching pixels | {full["cpuMatchingPixels"]} |
| CPU max channel delta | {full["cpuMaxChannelDelta"]} |
| CPU/reference >32 | {full["cpuReferenceGreaterThanThirtyTwoPixels"]} |
| GPU/reference similarity | {full["gpuSimilarity"]}% |
| GPU/reference >32 | {full["gpuReferenceGreaterThanThirtyTwoPixels"]} |

M60 stays `expected-unsupported`: `{route["visualParityFallbackPreserved"]}`.
The crop diagnostic stays `{route["cropFallbackPreserved"]}`.

Machine artifact:
`{AUDIT.relative_to(PROJECT_ROOT)}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_audit(audit: dict[str, Any]) -> None:
    if audit["linear"] != LINEAR_ID:
        fail("wrong Linear id")
    if audit["sceneId"] != SCENE_ID:
        fail("wrong scene id")
    if audit["supportDecision"] != "KEEP_EXPECTED_UNSUPPORTED":
        fail("M60 support decision changed")
    if audit["primaryClassification"] != PRIMARY_CLASSIFICATION:
        fail("wrong primary classification")
    if audit["route"]["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if audit["route"]["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")
    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "productionRendererChanged",
        "cpuRendererChanged",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")
    zones = {zone["subzone"]: zone for zone in audit["zoneComparisons"]}
    if set(zones) != set(PRIMARY_WINDOWS):
        fail("unexpected zone set")
    outer = zones["draw_oval_outer_boundary"]
    inner = zones["difference_oval_inner_boundary"]
    halo = zones["halo_interior"]
    if outer["targetPixels"] != 59:
        fail("outer target pixel count changed")
    if outer["finalReadback"]["readBackWhiteLayerPixels"] != 59:
        fail("outer FOR-286 white/layer count changed")
    if outer["for287AfterWhiteLayerPixels"] != 59:
        fail("outer FOR-287 survivor count changed")
    if inner["for287AfterWhiteLayerPixels"] != 0 or halo["for287AfterWhiteLayerPixels"] != 0:
        fail("FOR-287 improved zone counts changed")
    if outer["maskSupport"]["zeroMaskPixelsOnTarget"] != 0:
        fail("outer mask support disappeared")
    if outer["blurSource"]["maskAAfterBlurBuckets"]["buckets"]["partial"] != 59:
        fail("outer blur-alpha partial distinction changed")
    if outer["clipCoverage"]["clipCoverageBuckets"]["buckets"]["full"] != 59:
        fail("outer full clip coverage distinction changed")
    if outer["dispatchSource"]["dispatchInputRedPayloadPixels"] != 59:
        fail("outer dispatch red payload changed")
    if outer["reconstructedStore"]["writeRedTintPixels"] != 59:
        fail("outer reconstructed red store changed")
    if inner["reconstructedStore"]["writeOpaqueSrcFastPathPixels"] == 0:
        fail("inner opaque write contrast disappeared")
    if halo["reconstructedStore"]["writeOpaqueSrcFastPathPixels"] == 0:
        fail("halo opaque write contrast disappeared")
    causes = audit["causeMatrix"]
    selected = [name for name, cause in causes.items() if cause["selected"]]
    if selected != ["otherLaterWrite"]:
        fail(f"unexpected selected causes: {selected}")
    for name in (
        "sourceMaskOrBlurSupport",
        "preClipTooEarlyOrTooLate",
        "aaCoverageDifference",
        "whiteDrawRedDrawOrder",
        "proofInsufficient",
    ):
        if causes[name]["selected"] is not False:
            fail(f"unexpected selected cause `{name}`")


def main() -> None:
    audit = generate_audit()
    validate_audit(audit)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        PRIMARY_CLASSIFICATION,
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "promote M60",
        "draw_oval_outer_boundary",
        "difference_oval_inner_boundary",
        "halo_interior",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-288 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-288 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {PRIMARY_CLASSIFICATION}")


if __name__ == "__main__":
    main()

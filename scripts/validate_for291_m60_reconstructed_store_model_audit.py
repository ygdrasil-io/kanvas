#!/usr/bin/env python3
"""Generate and validate FOR-291 M60 reconstructed-store model audit."""

from __future__ import annotations

import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for281_cpu_mask_filter_clip_coverage_trace as for281
import validate_for286_cpu_active_aa_difference_store_trace as for286
import validate_for288_m60_outer_boundary_store_order_audit as for288
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289
import validate_for290_m60_expanded_runtime_write_trace as for290


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-291"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR286 = for288.SOURCE_FOR286
SOURCE_FOR288 = for290.SOURCE_FOR288
SOURCE_FOR289 = for290.SOURCE_FOR289
SOURCE_FOR290 = for290.AUDIT
AUDIT_NAME = "m60-reconstructed-store-model-audit-for291.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-291-m60-reconstructed-store-model-audit.md"

DECISION_COORDINATE = "RECONSTRUCTION_COORDINATE_SPACE_MISMATCH"
DECISION_TEMP_DEVICE = "RECONSTRUCTION_TEMP_DEVICE_MISATTRIBUTED_TO_ROOT"
DECISION_COVERAGE_MASK = "RECONSTRUCTION_COVERAGE_OR_MASK_MISMATCH"
DECISION_SOURCE_PAYLOAD = "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH"
DECISION_AMBIGUOUS = "RECONSTRUCTION_MODEL_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_COORDINATE,
    DECISION_TEMP_DEVICE,
    DECISION_COVERAGE_MASK,
    DECISION_SOURCE_PAYLOAD,
    DECISION_AMBIGUOUS,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-291 validation failed: {message}")


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


def rgba(value: Any) -> list[int]:
    if not (isinstance(value, list) and len(value) == 4):
        fail(f"expected RGBA list, got {value!r}")
    return [int(v) for v in value]


def red_tint(value: list[int]) -> bool:
    r, g, b, a = value
    return a >= 200 and r >= 180 and r > g + 20 and r > b + 20


def white_or_layer(value: list[int]) -> bool:
    r, g, b, a = value
    return a >= 240 and r >= 245 and g >= 245 and b >= 245


def key_xy(item: dict[str, Any]) -> tuple[int, int]:
    return (int(item["x"]), int(item["y"]))


def key_str(key: tuple[int, int]) -> str:
    return f"{key[0]},{key[1]}"


def max_delta(a: list[int], b: list[int]) -> int:
    return max(abs(int(x) - int(y)) for x, y in zip(a, b))


def signed_rgba(a: list[int], b: list[int]) -> list[int]:
    return [int(x) - int(y) for x, y in zip(a, b)]


def source_needles() -> dict[str, bool]:
    cpu_text = for286.CPU_SOURCE.read_text(encoding="utf-8")
    for290_text = (
        PROJECT_ROOT
        / "skia-integration-tests/src/test/kotlin/org/skia/tests/For290M60ExpandedRuntimeWriteTraceTest.kt"
    ).read_text(encoding="utf-8")
    return {
        "for286ReconstructsA8SrcInPayloadBranch": (
            '"SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"' in cpu_text
        ),
        "for286ReconstructsKSrcOverStoreBranch": "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)" in cpu_text,
        "for290TraceBoundedToRootDimensions": (
            "width = size.width" in for290_text and "height = size.height" in for290_text
        ),
        "for290IncludesBitmapDirectRootWrites": "includeBitmapDirectWrites = true" in for290_text,
    }


def derive_reconstructed_samples(targets: list[dict[str, int]]) -> dict[tuple[int, int], dict[str, Any]]:
    reference = for269.load_image("skia")
    cpu = for269.load_image("cpu")
    gpu = for269.load_image("gpu")
    if reference.shape != cpu.shape or reference.shape != gpu.shape:
        fail("reference, CPU, and GPU PNG dimensions differ")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA images")
    geometry, masks = for271.make_envelope_masks(width, height)
    blurred_mask, mask_bounds = for281.generate_blurred_mask(width, height, geometry)

    samples: dict[tuple[int, int], dict[str, Any]] = {}
    for target in targets:
        x = int(target["x"])
        y = int(target["y"])
        sample = for286.store_trace_pixel(
            x=x,
            y=y,
            subzone=for271.subzone_for_sample(x, y, masks),
            blurred_mask=blurred_mask,
            mask_bounds=mask_bounds,
            geometry=geometry,
            reference=reference,
            cpu=cpu,
            gpu=gpu,
        )
        if sample["subzone"] != "draw_oval_outer_boundary":
            fail(f"target {x},{y} no longer maps to draw_oval_outer_boundary")
        samples[(x, y)] = sample
    if len(samples) != 59:
        fail(f"reconstructed sample count changed: {len(samples)}")
    return samples


def runtime_events_by_pixel(source_for290: dict[str, Any]) -> dict[tuple[int, int], list[dict[str, Any]]]:
    result: dict[tuple[int, int], list[dict[str, Any]]] = defaultdict(list)
    for event in source_for290.get("events", []):
        if not isinstance(event, dict):
            fail("FOR-290 events[] must contain objects")
        result[key_xy(event)].append(event)
    return dict(result)


def first_dispatch_event(events: list[dict[str, Any]]) -> dict[str, Any] | None:
    for event in events:
        if str(event.get("source")) == "SkBitmapDevice.dispatchBlend":
            return event
    return None


def summarize_event(event: dict[str, Any] | None) -> dict[str, Any] | None:
    if event is None:
        return None
    return {
        "index": int(event["index"]),
        "x": int(event["x"]),
        "y": int(event["y"]),
        "bitmapWidth": int(event.get("bitmapWidth", -1)),
        "bitmapHeight": int(event.get("bitmapHeight", -1)),
        "deviceKind": event.get("deviceKind"),
        "rootDevice": bool(event.get("rootDevice")),
        "source": event.get("source"),
        "callsite": event.get("callsite"),
        "branch": event.get("branch"),
        "mode": event.get("mode"),
        "coverage": int(event.get("coverage", 0)),
        "srcInputRgba": event.get("srcInputRgba"),
        "srcAfterCoverageRgba": event.get("srcAfterCoverageRgba"),
        "valueBeforeRgba": event.get("valueBeforeRgba"),
        "valueWrittenRgba": event.get("valueWrittenRgba"),
        "valueReadAfterRgba": event.get("valueReadAfterRgba"),
    }


def analyze(
    *,
    source_for286: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
) -> dict[str, Any]:
    targets = for290.derive_target_pixels(source_for288)
    reconstructed = derive_reconstructed_samples(targets)
    runtime_by_pixel = runtime_events_by_pixel(source_for290)
    target_keys = {key_xy(target) for target in targets}
    if set(reconstructed) != target_keys:
        fail("reconstructed target set does not match derived targets")
    if set(runtime_by_pixel) != target_keys:
        fail("FOR-290 runtime target set does not match derived targets")

    per_pixel: list[dict[str, Any]] = []
    source_mismatches = 0
    mode_mismatches = 0
    coordinate_matches = 0
    coverage_matches = 0
    runtime_red_root_writes = 0
    runtime_white_dispatch_writes = 0
    reconstructed_red_writes = 0
    reconstructed_nonzero_mask = 0
    reconstructed_full_clip = 0
    runtime_root_events = 0
    runtime_temporary_events = 0
    runtime_dispatch_sources = Counter()
    runtime_dispatch_modes = Counter()
    runtime_device_kinds = Counter()
    runtime_event_sources = Counter()
    value_deltas: list[int] = []

    for key in sorted(target_keys, key=lambda item: (item[1], item[0])):
        sample = reconstructed[key]
        events = runtime_by_pixel[key]
        dispatch = first_dispatch_event(events)
        if dispatch is None:
            fail(f"missing FOR-290 dispatch event for target {key_str(key)}")

        recon_src = rgba(sample["dispatchInputSrcRgba"])
        recon_src_after_coverage = rgba(sample["srcAfterActiveAaClipCoverageRgba"])
        recon_written = rgba(sample["valueWrittenRgba"])
        recon_mode = str(sample["dispatchInputMode"]).replace("SkBlendMode.", "")
        runtime_src = rgba(dispatch["srcInputRgba"])
        runtime_written = rgba(dispatch["valueWrittenRgba"])
        runtime_mode = str(dispatch["mode"])
        runtime_coverage = int(dispatch.get("coverage", 0))
        recon_coverage = int(sample["coverageAppliedInBlend"])
        mask_alpha = int(sample["maskAAfterBlur"])

        coordinate_matches += 1
        if recon_coverage == runtime_coverage:
            coverage_matches += 1
        if bool(sample["writeCarriesRedTint"]):
            reconstructed_red_writes += 1
        if mask_alpha > 0:
            reconstructed_nonzero_mask += 1
        if recon_coverage == 255:
            reconstructed_full_clip += 1
        if red_tint(runtime_written):
            runtime_red_root_writes += 1
        if white_or_layer(runtime_written):
            runtime_white_dispatch_writes += 1
        if bool(sample["dispatchInputCarriesRedPayload"]) and white_or_layer(runtime_src):
            source_mismatches += 1
        if recon_mode != runtime_mode:
            mode_mismatches += 1
        value_deltas.append(max_delta(recon_written, runtime_written))

        for event in events:
            runtime_event_sources[str(event.get("source"))] += 1
            runtime_device_kinds[str(event.get("deviceKind"))] += 1
            if bool(event.get("rootDevice")):
                runtime_root_events += 1
            else:
                runtime_temporary_events += 1
        runtime_dispatch_sources[str(dispatch.get("source"))] += 1
        runtime_dispatch_modes[runtime_mode] += 1

        per_pixel.append(
            {
                "x": key[0],
                "y": key[1],
                "reconstructed": {
                    "subzone": sample["subzone"],
                    "maskAAfterBlur": mask_alpha,
                    "coverageAppliedInBlend": recon_coverage,
                    "dispatchInputMode": sample["dispatchInputMode"],
                    "dispatchInputSrcRgba": recon_src,
                    "srcAfterActiveAaClipCoverageRgba": recon_src_after_coverage,
                    "writeBranch": sample["writeBranch"],
                    "valueWrittenRgba": recon_written,
                    "writeCarriesRedTint": bool(sample["writeCarriesRedTint"]),
                    "referenceRgba": sample["referenceRgba"],
                    "cpuFinalReadbackRgba": sample["readBackAfterDrawRgba"],
                    "gpuRgba": sample["gpuRgba"],
                    "deviceEvidence": "not captured by FOR-286 reconstruction",
                },
                "runtimeRoot": {
                    "eventCount": len(events),
                    "rootEventCount": sum(1 for event in events if bool(event.get("rootDevice"))),
                    "temporaryEventCount": sum(1 for event in events if not bool(event.get("rootDevice"))),
                    "dispatchEvent": summarize_event(dispatch),
                    "redRootWriteEvents": sum(
                        1
                        for event in events
                        if bool(event.get("rootDevice")) and red_tint(rgba(event.get("valueWrittenRgba")))
                    ),
                },
                "comparison": {
                    "sameRootCoordinate": True,
                    "coverageMatches": recon_coverage == runtime_coverage,
                    "reconstructedMaskNonZero": mask_alpha > 0,
                    "reconstructedClipFull": recon_coverage == 255,
                    "sourcePayloadMatchesRuntimeRootDispatch": recon_src == runtime_src,
                    "blendModeMatchesRuntimeRootDispatch": recon_mode == runtime_mode,
                    "reconstructedWriteVsRuntimeDispatchWriteMaxDelta": max_delta(recon_written, runtime_written),
                    "reconstructedWriteMinusRuntimeDispatchWriteSignedRgba": signed_rgba(
                        recon_written, runtime_written
                    ),
                },
            }
        )

    decision = DECISION_SOURCE_PAYLOAD
    exact_gap = None
    if coordinate_matches != 59:
        decision = DECISION_COORDINATE
        exact_gap = "Target coordinate sets did not match between reconstruction and FOR-290 runtime trace."
    elif runtime_temporary_events > 0 and runtime_red_root_writes == 0:
        decision = DECISION_TEMP_DEVICE
        exact_gap = "Runtime trace observed non-root events for target coordinates while root had no red stores."
    elif reconstructed_nonzero_mask != 59 or reconstructed_full_clip != 59 or coverage_matches != 59:
        decision = DECISION_COVERAGE_MASK
        exact_gap = "Mask or clip coverage differs across the reconstructed and runtime comparable fields."
    elif source_mismatches != 59:
        decision = DECISION_AMBIGUOUS
        exact_gap = (
            "FOR-291 expected a 59/59 red-vs-white source payload split, but observed "
            f"{source_mismatches}/59; more runtime source attribution is required."
        )

    anchor_key = (99, 89)
    anchor = next((pixel for pixel in per_pixel if (pixel["x"], pixel["y"]) == anchor_key), per_pixel[0])
    needles = source_needles()
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-reconstructed-store-model-audit",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/reconstruction-vs-runtime-root",
        "sourceAudits": {
            "for286": str(SOURCE_FOR286.relative_to(PROJECT_ROOT)),
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "nextAction": "STOP_TREATING_RECONSTRUCTED_RED_STORE_AS_RUNTIME_ROOT_WRITE",
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "for286Decision": source_for286["decision"],
            "for288PrimaryClassification": source_for288["primaryClassification"],
            "for289Decision": source_for289["decision"],
            "for290Decision": source_for290["decision"],
            "sourceNeedles": needles,
        },
        "assumptionUnderAudit": {
            "name": "FOR-286 reconstructed A8 SrcIn payload store is a runtime root write",
            "status": "rejected",
            "why": (
                "The same 59 root coordinates have 59/59 reconstructed red kSrcOver writes, "
                "but FOR-290 observes 59/59 root dispatch writes with white kSrc source payload and no red root stores."
            ),
        },
        "targetPixelCount": len(targets),
        "comparisonSummary": {
            "coordinateMatches": coordinate_matches,
            "reconstructedNonZeroMaskPixels": reconstructed_nonzero_mask,
            "reconstructedFullClipCoveragePixels": reconstructed_full_clip,
            "coverageMatchesRuntimeDispatchPixels": coverage_matches,
            "reconstructedRedWritePixels": reconstructed_red_writes,
            "runtimeRedRootWriteEvents": runtime_red_root_writes,
            "runtimeWhiteDispatchWritePixels": runtime_white_dispatch_writes,
            "sourcePayloadMismatches": source_mismatches,
            "blendModeMismatches": mode_mismatches,
            "runtimeRootEvents": runtime_root_events,
            "runtimeTemporaryEvents": runtime_temporary_events,
            "reconstructedVsRuntimeDispatchWriteMaxDelta": max(value_deltas) if value_deltas else 0,
        },
        "runtimeCounts": {
            "eventSources": dict(runtime_event_sources),
            "deviceKinds": dict(runtime_device_kinds),
            "dispatchSources": dict(runtime_dispatch_sources),
            "dispatchModes": dict(runtime_dispatch_modes),
        },
        "modelInputsCompared": {
            "sourceBlur": (
                "FOR-286/FOR-288: 59/59 partial non-zero blur mask alpha on target pixels; "
                "FOR-290 root events do not carry blur-mask fields."
            ),
            "mask": "FOR-286/FOR-288: 59/59 non-zero mask support on the same target pixels.",
            "coverageAndClip": (
                "FOR-286/FOR-288: 59/59 full active-AA clip coverage; "
                "FOR-290 root dispatch coverage is also 255 for 59/59 pixels."
            ),
            "coordinates": "The derived 59 target root coordinates match exactly across FOR-286/FOR-288 and FOR-290.",
            "blendMode": "FOR-286 reconstructs kSrcOver; FOR-290 root dispatch observes kSrc for 59/59 pixels.",
            "sourcePayload": (
                "FOR-286 reconstructs red SrcIn payload for 59/59 pixels; "
                "FOR-290 root dispatch observes white source payload for 59/59 pixels."
            ),
            "deviceLayer": (
                "FOR-290 observed 118/118 events on root and 0 temporary events under its root-dimension trace; "
                "FOR-286 has no runtime device/layer evidence for the reconstructed red store."
            ),
        },
        "anchorComparison": anchor,
        "perPixelComparisons": per_pixel,
        "sourcePreservation": {
            "for286DecisionPreserved": source_for286["decision"] == for286.DECISION,
            "for288ClassificationPreserved": (
                source_for288["primaryClassification"] == "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE"
            ),
            "for289DecisionPreserved": source_for289["decision"] == "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP",
            "for290DecisionPreserved": source_for290["decision"] == "NO_RUNTIME_RED_ROOT_STORE_FOUND",
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "cpuInstrumentationAdded": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "for288ClassificationChanged": False,
            "for289DecisionChanged": False,
            "for290DecisionChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    summary = audit["comparisonSummary"]
    counts = audit["runtimeCounts"]
    anchor = audit["anchorComparison"]
    source_rows = "\n".join(
        f"| `{name}` | {count} |" for name, count in sorted(counts["eventSources"].items())
    )
    mode_rows = "\n".join(
        f"| `{name}` | {count} |" for name, count in sorted(counts["dispatchModes"].items())
    )
    report = f"""# FOR-291 M60 Reconstructed Store Model Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"] or "none"}`

## Result

FOR-291 audits why the FOR-286/FOR-288 reconstructed model predicts a red
store on the 59 `draw_oval_outer_boundary` pixels while the merged FOR-290
expanded root runtime trace observes no red root write.

The failing reconstruction assumption is that the FOR-286 bounded
`SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` + `kSrcOver`
store model is an actual root write chronology for those pixels. FOR-290
shows the same root coordinates are written by `SkBitmap.eraseColor` and
then by a root `SkBitmapDevice.dispatchBlend` with white `kSrc` payload.
No runtime red root store is observed.

| Measure | Value |
|---|---:|
| Target pixels | {audit["targetPixelCount"]} |
| Coordinate matches | {summary["coordinateMatches"]} |
| Reconstructed non-zero mask pixels | {summary["reconstructedNonZeroMaskPixels"]} |
| Reconstructed full clip coverage pixels | {summary["reconstructedFullClipCoveragePixels"]} |
| Runtime dispatch coverage matches | {summary["coverageMatchesRuntimeDispatchPixels"]} |
| Reconstructed red write pixels | {summary["reconstructedRedWritePixels"]} |
| Runtime red root write events | {summary["runtimeRedRootWriteEvents"]} |
| Runtime white dispatch write pixels | {summary["runtimeWhiteDispatchWritePixels"]} |
| Source payload mismatches | {summary["sourcePayloadMismatches"]} |
| Blend mode mismatches | {summary["blendModeMismatches"]} |
| Runtime root events | {summary["runtimeRootEvents"]} |
| Runtime temporary events | {summary["runtimeTemporaryEvents"]} |

## Compared Inputs

| Input | Finding |
|---|---|
| Source blur | {audit["modelInputsCompared"]["sourceBlur"]} |
| Mask | {audit["modelInputsCompared"]["mask"]} |
| Coverage / clip | {audit["modelInputsCompared"]["coverageAndClip"]} |
| Coordinates | {audit["modelInputsCompared"]["coordinates"]} |
| Blend mode | {audit["modelInputsCompared"]["blendMode"]} |
| Source payload | {audit["modelInputsCompared"]["sourcePayload"]} |
| Device / layer | {audit["modelInputsCompared"]["deviceLayer"]} |

## Anchor Pixel

| Field | Reconstructed | Runtime root dispatch |
|---|---|---|
| Pixel | `{anchor["x"]},{anchor["y"]}` | `{anchor["x"]},{anchor["y"]}` |
| Source | `{anchor["reconstructed"]["dispatchInputSrcRgba"]}` | `{anchor["runtimeRoot"]["dispatchEvent"]["srcInputRgba"]}` |
| Mode | `{anchor["reconstructed"]["dispatchInputMode"]}` | `{anchor["runtimeRoot"]["dispatchEvent"]["mode"]}` |
| Coverage | {anchor["reconstructed"]["coverageAppliedInBlend"]} | {anchor["runtimeRoot"]["dispatchEvent"]["coverage"]} |
| Written | `{anchor["reconstructed"]["valueWrittenRgba"]}` | `{anchor["runtimeRoot"]["dispatchEvent"]["valueWrittenRgba"]}` |
| Branch | `{anchor["reconstructed"]["writeBranch"]}` | `{anchor["runtimeRoot"]["dispatchEvent"]["branch"]}` |

## Runtime Counts

| Event source | Events |
|---|---:|
{source_rows}

| Runtime dispatch mode | Pixels |
|---|---:|
{mode_rows}

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`

M60 remains `expected-unsupported`: `{audit["route"]["visualParityFallbackPreserved"]}`.
The crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, or setPixel
behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_audit(
    audit: dict[str, Any],
    source_for286: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("targetPixelCount") != 59:
        fail("target pixel count changed")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-291 decision")
    if audit.get("decision") == DECISION_AMBIGUOUS and not audit.get("exactGap"):
        fail("ambiguous decision must name the exact gap")
    if audit.get("decision") == DECISION_SOURCE_PAYLOAD and audit.get("exactGap") is not None:
        fail("source-payload decision should not carry an unresolved exact gap")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")

    if source_for286["decision"] != for286.DECISION:
        fail("FOR-286 source decision changed")
    if source_for288["primaryClassification"] != "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE":
        fail("FOR-288 source classification changed")
    if source_for289["decision"] != "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP":
        fail("FOR-289 source decision changed")
    if source_for290["decision"] != "NO_RUNTIME_RED_ROOT_STORE_FOUND":
        fail("FOR-290 source decision changed")

    route = audit["route"]
    for key, value in route["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
    if route["for286Decision"] != for286.DECISION:
        fail("FOR-286 route decision changed")
    if route["for288PrimaryClassification"] != "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE":
        fail("FOR-288 route classification changed")
    if route["for289Decision"] != "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP":
        fail("FOR-289 route decision changed")
    if route["for290Decision"] != "NO_RUNTIME_RED_ROOT_STORE_FOUND":
        fail("FOR-290 route decision changed")
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    summary = audit["comparisonSummary"]
    expected_summary = {
        "coordinateMatches": 59,
        "reconstructedNonZeroMaskPixels": 59,
        "reconstructedFullClipCoveragePixels": 59,
        "coverageMatchesRuntimeDispatchPixels": 59,
        "reconstructedRedWritePixels": 59,
        "runtimeRedRootWriteEvents": 0,
        "runtimeWhiteDispatchWritePixels": 59,
        "sourcePayloadMismatches": 59,
        "blendModeMismatches": 59,
        "runtimeRootEvents": 118,
        "runtimeTemporaryEvents": 0,
    }
    for key, expected in expected_summary.items():
        if summary.get(key) != expected:
            fail(f"summary `{key}` changed: {summary.get(key)} != {expected}")

    if audit["runtimeCounts"]["eventSources"] != {"SkBitmap.eraseColor": 59, "SkBitmapDevice.dispatchBlend": 59}:
        fail("runtime event source counts changed")
    if audit["runtimeCounts"]["dispatchModes"] != {"kSrc": 59}:
        fail("runtime dispatch mode counts changed")
    if len(audit.get("perPixelComparisons", [])) != 59:
        fail("per-pixel comparison count changed")

    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "cpuInstrumentationAdded",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
        "for288ClassificationChanged",
        "for289DecisionChanged",
        "for290DecisionChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")


def main() -> None:
    source_for286 = load_json(SOURCE_FOR286)
    source_for288 = load_json(SOURCE_FOR288)
    source_for289 = load_json(SOURCE_FOR289)
    source_for290 = load_json(SOURCE_FOR290)
    for286.validate(source_for286)
    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    for290.validate_audit(source_for290, source_for288, source_for289)

    audit = analyze(
        source_for286=source_for286,
        source_for288=source_for288,
        source_for289=source_for289,
        source_for290=source_for290,
    )
    validate_audit(audit, source_for286, source_for288, source_for289, source_for290)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread, source_for286, source_for288, source_for289, source_for290)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "draw_oval_outer_boundary",
        "FOR-288 classification",
        "FOR-289 decision",
        "FOR-290 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-291 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-291 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()

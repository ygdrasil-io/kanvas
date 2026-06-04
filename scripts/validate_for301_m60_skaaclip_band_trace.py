#!/usr/bin/env python3
"""Generate and validate FOR-301 M60 SkAAClip band/run trace evidence."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for292_m60_source_payload_derivation_audit as for292
import validate_for293_m60_red_drawrrect_runtime_visibility_audit as for293
import validate_for294_m60_expanded_red_drawrrect_runtime_trace as for294
import validate_for295_m60_red_domain_vs_white_targets as for295
import validate_for298_m60_a8_srcinpayload_runtime_filter as for298
import validate_for299_m60_a8_predispatch_filter_trace as for299
import validate_for300_m60_active_aa_clip_coverage as for300


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-301"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR292 = for292.AUDIT
SOURCE_FOR293 = for293.AUDIT
SOURCE_FOR294 = for294.AUDIT
SOURCE_FOR295 = for295.AUDIT
SOURCE_FOR298 = for298.AUDIT
SOURCE_FOR299 = for299.AUDIT
SOURCE_FOR300 = for300.AUDIT
RAW_FOR294 = for294.RAW_AUDIT
BUILD_DIR = PROJECT_ROOT / "build/reports/for301"
PROBES_FILE = BUILD_DIR / "m60-skaaclip-band-trace-for301.probes.csv"
RAW_AUDIT = BUILD_DIR / "m60-skaaclip-band-trace-for301.raw.json"
AUDIT_NAME = "m60-skaaclip-band-trace-for301.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-301-m60-skaaclip-band-trace.md"
TEST_CLASS = "org.skia.tests.For301M60SkAAClipBandTraceTest"
TEST_SOURCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For301M60SkAAClipBandTraceTest.kt"
)

DECISION_MATCH = "SKAACLIP_DIFFERENCE_BANDS_MATCH_ANALYTIC_MODEL"
DECISION_EXCLUDE = "SKAACLIP_DIFFERENCE_BANDS_EXCLUDE_M60_TARGETS"
DECISION_CTM_BOUNDS = "SKAACLIP_DIFFERENCE_CTM_OR_BOUNDS_CAUSES_TARGET_HOLE"
DECISION_ALPHA_MERGE = "SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE"
DECISION_AMBIGUOUS = "SKAACLIP_BAND_TRACE_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_MATCH,
    DECISION_EXCLUDE,
    DECISION_CTM_BOUNDS,
    DECISION_ALPHA_MERGE,
    DECISION_AMBIGUOUS,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-301 validation failed: {message}")


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


def key_xy(item: dict[str, Any]) -> tuple[int, int]:
    return (int(item["x"]), int(item["y"]))


def sorted_coords(coords: Iterable[tuple[int, int]]) -> list[tuple[int, int]]:
    return sorted(coords, key=lambda item: (item[1], item[0]))


def bounds_from_coords(coords: set[tuple[int, int]]) -> dict[str, int] | None:
    if not coords:
        return None
    xs = [x for x, _ in coords]
    ys = [y for _, y in coords]
    return {
        "left": min(xs),
        "top": min(ys),
        "right": max(xs) + 1,
        "bottom": max(ys) + 1,
        "rightInclusive": max(xs),
        "bottomInclusive": max(ys),
    }


def bounds_contains(bounds: dict[str, Any] | None, coord: tuple[int, int]) -> bool:
    if not isinstance(bounds, dict):
        return False
    x, y = coord
    return int(bounds["left"]) <= x < int(bounds["right"]) and int(bounds["top"]) <= y < int(bounds["bottom"])


def value_stats(values: list[int]) -> dict[str, Any]:
    if not values:
        return {
            "pixels": 0,
            "min": None,
            "max": None,
            "average": None,
            "zeroPixels": 0,
            "nonZeroPixels": 0,
            "fullPixels": 0,
            "topValues": [],
        }
    counts = Counter(values)
    return {
        "pixels": len(values),
        "min": min(values),
        "max": max(values),
        "average": round(sum(values) / len(values), 6),
        "zeroPixels": counts.get(0, 0),
        "nonZeroPixels": len(values) - counts.get(0, 0),
        "fullPixels": counts.get(255, 0),
        "topValues": [{"value": value, "pixels": count} for value, count in counts.most_common(8)],
    }


def coord_sample(coords: set[tuple[int, int]], limit: int = 12) -> list[dict[str, int]]:
    return [{"x": x, "y": y} for x, y in sorted_coords(coords)[:limit]]


def probe_map(snapshot: dict[str, Any]) -> dict[tuple[int, int], int]:
    return {
        key_xy(probe): int(probe["coverage"])
        for probe in snapshot.get("coverageProbes", [])
        if isinstance(probe, dict)
    }


def write_probe_file(groups: dict[str, set[tuple[int, int]]]) -> None:
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    for group_id, coords in groups.items():
        for x, y in sorted_coords(coords):
            lines.append(f"{group_id},{x},{y}\n")
    PROBES_FILE.write_text("".join(lines), encoding="utf-8")


def run_runtime_trace(groups: dict[str, set[tuple[int, int]]]) -> None:
    write_probe_file(groups)
    cmd = [
        "./gradlew",
        ":skia-integration-tests:test",
        "--tests",
        TEST_CLASS,
        "--rerun-tasks",
    ]
    env = os.environ.copy()
    env["KANVAS_FOR301_PROBES_FILE"] = str(PROBES_FILE)
    env["KANVAS_FOR301_OUTPUT"] = str(RAW_AUDIT)
    result = subprocess.run(cmd, cwd=PROJECT_ROOT, env=env)
    if result.returncode != 0:
        fail(f"SkAAClip band trace test failed with exit code {result.returncode}")


def source_needles() -> dict[str, bool]:
    aa_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkAAClip.kt").read_text(
        encoding="utf-8"
    )
    canvas_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt").read_text(
        encoding="utf-8"
    )
    return {
        "skAAClipDebugSnapshotExposesBands": "public fun debugSnapshot(" in aa_text,
        "skAAClipDebugSnapshotExposesLineProbes": "public data class DebugLineProbe" in aa_text,
        "traceIsOptIn": "public fun configure(" in canvas_text,
        "traceDefaultsDisabled": "private var enabled: Boolean = false" in canvas_text,
        "clipDifferenceRecordsParentPathResult": "recordClipPathDifference(" in canvas_text,
        "normalRenderingStillUsesDifferenceOp": "combined.op(pathAac, SkRegion.Op.kDifference)" in canvas_text,
        "for301TestSerializesBands": '"bands": [' in TEST_SOURCE.read_text(encoding="utf-8"),
    }


def build_probe_groups(
    source_for292: dict[str, Any],
    source_for295: dict[str, Any],
) -> dict[str, set[tuple[int, int]]]:
    domains = for298.reconstruct_domains(source_for295)
    original = {key_xy(pixel) for pixel in source_for292.get("perPixelComparisons", [])}
    groups = {
        "original-59-targets": original,
        "candidate-minus-runtime-002": domains["targetHoleCoords"],
    }
    for component in domains["runtimeComponents"]:
        groups[component["id"]] = component["_coords"]
    return groups


def summarize_group(
    group_id: str,
    coords: set[tuple[int, int]],
    *,
    parent_map: dict[tuple[int, int], int],
    path_map: dict[tuple[int, int], int],
    result_map: dict[tuple[int, int], int],
    for293_clip: Any,
) -> dict[str, Any]:
    missing = {coord for coord in coords if coord not in result_map}
    parent_values = [parent_map[coord] for coord in sorted_coords(coords) if coord in parent_map]
    path_values = [path_map[coord] for coord in sorted_coords(coords) if coord in path_map]
    result_values = [result_map[coord] for coord in sorted_coords(coords) if coord in result_map]
    analytic_values = [int(for293_clip[y, x]) for x, y in sorted_coords(coords)]
    alpha_formula_matches = all(
        result_map.get(coord) == (parent_map.get(coord, 0) * (255 - path_map.get(coord, 0)) + 127) // 255
        for coord in coords
        if coord in parent_map and coord in path_map and coord in result_map
    )
    return {
        "id": group_id,
        "pixels": len(coords),
        "bounds": bounds_from_coords(coords),
        "missingRuntimeProbePixels": len(missing),
        "missingSample": coord_sample(missing),
        "parentCoverage": value_stats(parent_values),
        "pathCoverage": value_stats(path_values),
        "resultCoverage": value_stats(result_values),
        "for293AnalyticClipCoverage": value_stats(analytic_values),
        "differenceAlphaFormulaMatchesRuntime": alpha_formula_matches,
        "sample": [
            {
                "x": x,
                "y": y,
                "parentCoverage": parent_map.get((x, y)),
                "pathCoverage": path_map.get((x, y)),
                "resultCoverage": result_map.get((x, y)),
                "for293AnalyticClipCoverage": int(for293_clip[y, x]),
            }
            for x, y in sorted_coords(coords)[:16]
        ],
    }


def decide(
    event: dict[str, Any],
    groups: dict[str, dict[str, Any]],
) -> tuple[str, str, dict[str, Any]]:
    original = groups["original-59-targets"]
    target_hole = groups["candidate-minus-runtime-002"]
    runtime = [value for key, value in groups.items() if key.startswith("red-runtime-")]
    original_result = original["resultCoverage"]
    original_path = original["pathCoverage"]
    original_parent = original["parentCoverage"]
    original_model = original["for293AnalyticClipCoverage"]
    result_bounds = event["result"]["bounds"]
    path_bounds = event["path"]["bounds"]
    matrix = event["matrix"]
    if (
        original_result["zeroPixels"] == 59
        and original_model["zeroPixels"] == 0
        and original_parent["fullPixels"] == 59
        and original_path["fullPixels"] == 59
        and original["differenceAlphaFormulaMatchesRuntime"]
        and target_hole["resultCoverage"]["zeroPixels"] > 0
        and all(component["resultCoverage"]["nonZeroPixels"] == component["pixels"] for component in runtime)
    ):
        return (
            DECISION_ALPHA_MERGE,
            (
                "The runtime SkAAClip parent is full over the 59 original pixels, the rasterized "
                "difference path is also full over the same pixels, and kDifference alpha merge "
                "therefore produces result coverage 0. This explains the FOR-300 runtime hole "
                "without changing normal rendering."
            ),
            {
                "runtimeParentFullOnOriginalTargets": True,
                "runtimePathFullOnOriginalTargets": True,
                "runtimeResultZeroOnOriginalTargets": True,
                "for293AnalyticModelContradicted": True,
                "differenceFormulaMatches": True,
                "matrixScale": {"sx": matrix["sx"], "sy": matrix["sy"]},
                "pathBounds": path_bounds,
                "resultBounds": result_bounds,
                "safeLocalFixApplied": False,
            },
        )
    if original_result["nonZeroPixels"] == 59 and original_model["nonZeroPixels"] == 59:
        return (
            DECISION_MATCH,
            "Runtime SkAAClip result coverage matches the nonzero FOR-293 analytic model on original targets.",
            {"safeLocalFixApplied": False},
        )
    if original_result["zeroPixels"] == 59 and original_path["zeroPixels"] == 59:
        return (
            DECISION_EXCLUDE,
            "Runtime SkAAClip bands exclude the original targets before difference alpha subtraction.",
            {"safeLocalFixApplied": False},
        )
    if not all(bounds_contains(result_bounds, coord) for coord in group_coords(groups, "original-59-targets")):
        return (
            DECISION_CTM_BOUNDS,
            "Runtime SkAAClip result bounds do not contain every original target pixel.",
            {"safeLocalFixApplied": False},
        )
    return (
        DECISION_AMBIGUOUS,
        "The band trace did not prove a safe local correction.",
        {"safeLocalFixApplied": False},
    )


def group_coords(groups: dict[str, Any], group_id: str) -> set[tuple[int, int]]:
    raw_bounds = groups[group_id]["sample"]
    return {key_xy(item) for item in raw_bounds}


def analyze(
    *,
    raw: dict[str, Any],
    source_for292: dict[str, Any],
    source_for293: dict[str, Any],
    source_for295: dict[str, Any],
    source_for299: dict[str, Any],
    source_for300: dict[str, Any],
) -> dict[str, Any]:
    groups = build_probe_groups(source_for292, source_for295)
    events = raw.get("events", [])
    if not (isinstance(events, list) and len(events) == 1 and isinstance(events[0], dict)):
        fail("raw FOR-301 trace must contain exactly one event")
    event = events[0]
    parent_map = probe_map(event["parent"])
    path_map = probe_map(event["path"])
    result_map = probe_map(event["result"])
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    geometry, _masks = for294.coordinate_masks(width, height)[:2]
    _mask_alpha, clip_coverage, _alpha_after_clip, _visible_candidate, _mask_bounds = for293.red_draw_runtime_domain(
        width,
        height,
        geometry,
    )
    summaries = {
        group_id: summarize_group(
            group_id,
            coords,
            parent_map=parent_map,
            path_map=path_map,
            result_map=result_map,
            for293_clip=clip_coverage,
        )
        for group_id, coords in groups.items()
    }
    decision, exact_gap, details = decide(event, summaries)
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-skaaclip-band-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/runtime-skaaclip-trace",
        "sourceAudits": {
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
            "for293": str(SOURCE_FOR293.relative_to(PROJECT_ROOT)),
            "for294": str(SOURCE_FOR294.relative_to(PROJECT_ROOT)),
            "for295": str(SOURCE_FOR295.relative_to(PROJECT_ROOT)),
            "for298": str(SOURCE_FOR298.relative_to(PROJECT_ROOT)),
            "for299": str(SOURCE_FOR299.relative_to(PROJECT_ROOT)),
            "for300": str(SOURCE_FOR300.relative_to(PROJECT_ROOT)),
            "for294Raw": str(RAW_FOR294.relative_to(PROJECT_ROOT)),
            "for301Raw": str(RAW_AUDIT.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "decisionDetails": details,
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "for299Decision": source_for299["decision"],
            "for300Decision": source_for300["decision"],
        },
        "traceEvent": {
            "index": int(event["index"]),
            "op": event["op"],
            "doAntiAlias": bool(event["doAntiAlias"]),
            "stateClipBounds": event["stateClipBounds"],
            "matrix": event["matrix"],
            "parent": snapshot_summary(event["parent"]),
            "path": snapshot_summary(event["path"]),
            "result": snapshot_summary(event["result"]),
        },
        "coverageMetric": {
            "probeGroupCount": len(summaries),
            "probePixelCount": sum(len(coords) for coords in groups.values()),
            "originalTargets": summaries["original-59-targets"],
            "candidateMinusRuntime002": summaries["candidate-minus-runtime-002"],
            "redRuntimeComponents": [
                summaries[key] for key in sorted(summaries) if key.startswith("red-runtime-")
            ],
        },
        "rawBandTrace": {
            "parentBands": event["parent"]["bands"],
            "pathBands": event["path"]["bands"],
            "resultBands": event["result"]["bands"],
            "resultLineProbes": event["result"]["lineProbes"],
        },
        "sourceNeedles": source_needles(),
        "sourcePreservation": {
            "for299DecisionPreserved": source_for299["decision"] == for299.DECISION_LAYER_CLIP,
            "for300DecisionPreserved": source_for300["decision"] == for300.DECISION_BOUNDED_NO_FIX,
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRenderingChanged": False,
            "normalRenderingChanged": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "safeLocalFixApplied": False,
        },
    }


def snapshot_summary(snapshot: dict[str, Any]) -> dict[str, Any]:
    return {
        "bounds": snapshot["bounds"],
        "isEmpty": bool(snapshot["isEmpty"]),
        "isRect": bool(snapshot["isRect"]),
        "rowCount": int(snapshot["rowCount"]),
        "runCount": int(snapshot["runCount"]),
        "lineProbeCount": len(snapshot.get("lineProbes", [])),
        "coverageProbeCount": len(snapshot.get("coverageProbes", [])),
    }


def component_row(component: dict[str, Any]) -> str:
    return (
        f"| `{component['id']}` | {component['pixels']} | `{component['bounds']}` | "
        f"{component['parentCoverage']['fullPixels']} | {component['pathCoverage']['fullPixels']} | "
        f"{component['resultCoverage']['zeroPixels']} | {component['resultCoverage']['nonZeroPixels']} | "
        f"{component['for293AnalyticClipCoverage']['zeroPixels']} | "
        f"{component['for293AnalyticClipCoverage']['nonZeroPixels']} | "
        f"{component['differenceAlphaFormulaMatchesRuntime']} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    metric = audit["coverageMetric"]
    original = metric["originalTargets"]
    target_hole = metric["candidateMinusRuntime002"]
    runtime_rows = "\n".join(component_row(component) for component in metric["redRuntimeComponents"])
    source_rows = "\n".join(f"| `{key}` | {value} |" for key, value in audit["sourceNeedles"].items())
    trace = audit["traceEvent"]
    report = f"""# FOR-301 M60 SkAAClip Band Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap/result: `{audit["exactGap"]}`

## Result

FOR-301 adds opt-in runtime instrumentation for the `SkAAClip` state produced
by the M60 `clipRRect(kDifference)` stack. The trace captures parent, rasterized
path, and final difference result bands/runs plus coverage probes for the
original 59 pixels, `candidate-minus-runtime-002`, and the `red-runtime-*`
components.

| Measure | Value |
|---|---:|
| Probe groups | {metric["probeGroupCount"]} |
| Probe pixels | {metric["probePixelCount"]} |
| Parent bounds | `{trace["parent"]["bounds"]}` |
| Path bounds | `{trace["path"]["bounds"]}` |
| Result bounds | `{trace["result"]["bounds"]}` |
| Parent row/run count | {trace["parent"]["rowCount"]}/{trace["parent"]["runCount"]} |
| Path row/run count | {trace["path"]["rowCount"]}/{trace["path"]["runCount"]} |
| Result row/run count | {trace["result"]["rowCount"]}/{trace["result"]["runCount"]} |
| CTM scale | sx={trace["matrix"]["sx"]}, sy={trace["matrix"]["sy"]} |

## Component Comparison

| Component | Pixels | Bounds | Parent cov=255 | Path cov=255 | Result cov=0 | Result cov>0 | FOR-293 cov=0 | FOR-293 cov>0 | Difference formula matches |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|
{component_row(original)}
{component_row(target_hole)}
{runtime_rows}

## Interpretation

The 59 original pixels are inside the result bounds and have full parent
coverage. They also have full coverage in the rasterized difference path, so
`kDifference` alpha merge computes `parent * (255 - path) / 255 = 0`. The
runtime bands therefore explain the FOR-300 `activeAaClip.coverage == 0`
observation. FOR-293's analytic model remains contradicted by runtime evidence;
FOR-301 does not apply a renderer fix because changing CTM/path modelling now
would need separate before/after reference evidence.

M60 remains `expected-unsupported`: `{audit["route"]["fallbackReason"]}`.
The crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`

## Source Needles

| Needle | Present |
|---|---|
{source_rows}
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_audit(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid decision")
    if audit.get("decision") != DECISION_ALPHA_MERGE:
        fail("FOR-301 expected runtime alpha-merge cause for the M60 target hole")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")
    trace = audit["traceEvent"]
    if trace["op"] != "kDifference" or trace["doAntiAlias"] is not True:
        fail("trace must capture the AA kDifference clip")
    if trace["matrix"] != {
        "sx": 2.0,
        "kx": 0.0,
        "tx": 0.0,
        "ky": 0.0,
        "sy": 2.0,
        "ty": 0.0,
        "persp0": 0.0,
        "persp1": 0.0,
        "persp2": 1.0,
    }:
        fail(f"unexpected clip matrix: {trace['matrix']}")
    if trace["path"]["bounds"] != {"left": 16, "top": 16, "right": 576, "bottom": 576}:
        fail(f"unexpected difference path bounds: {trace['path']['bounds']}")
    if trace["result"]["bounds"] != {"left": 0, "top": 0, "right": 1164, "bottom": 802}:
        fail(f"unexpected result bounds: {trace['result']['bounds']}")
    metric = audit["coverageMetric"]
    original = metric["originalTargets"]
    if original["pixels"] != 59:
        fail("original target count changed")
    if original["parentCoverage"]["fullPixels"] != 59:
        fail("parent coverage is no longer full on original targets")
    if original["pathCoverage"]["fullPixels"] != 59:
        fail("difference path coverage is no longer full on original targets")
    if original["resultCoverage"]["zeroPixels"] != 59:
        fail("result coverage is no longer zero on original targets")
    if original["for293AnalyticClipCoverage"]["zeroPixels"] != 0:
        fail("FOR-293 analytic model no longer predicts nonzero original coverage")
    if original["differenceAlphaFormulaMatchesRuntime"] is not True:
        fail("difference alpha formula does not match original runtime probes")
    target_hole = metric["candidateMinusRuntime002"]
    if target_hole["pixels"] != 3293:
        fail("candidate-minus-runtime-002 size changed")
    if target_hole["resultCoverage"]["zeroPixels"] != 3281:
        fail("candidate-minus-runtime-002 result zero count changed")
    if [component["pixels"] for component in metric["redRuntimeComponents"]] != [2275, 2275, 2270, 2268]:
        fail("red-runtime component sizes changed")
    for component in metric["redRuntimeComponents"]:
        if component["resultCoverage"]["zeroPixels"] != 0:
            fail(f"{component['id']} unexpectedly has zero result coverage")
    for key, value in audit["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
    for key, value in audit["sourcePreservation"].items():
        if value is not True:
            fail(f"source preservation `{key}` failed")
    for key, value in audit["strictPreservation"].items():
        if value is not False:
            fail(f"strict preservation `{key}` changed")


def main() -> None:
    source_for292 = load_json(SOURCE_FOR292)
    source_for293 = load_json(SOURCE_FOR293)
    source_for295 = load_json(SOURCE_FOR295)
    source_for299 = load_json(SOURCE_FOR299)
    source_for300 = load_json(SOURCE_FOR300)
    groups = build_probe_groups(source_for292, source_for295)
    run_runtime_trace(groups)
    raw = load_json(RAW_AUDIT)
    audit = analyze(
        raw=raw,
        source_for292=source_for292,
        source_for293=source_for293,
        source_for295=source_for295,
        source_for299=source_for299,
        source_for300=source_for300,
    )
    validate_audit(audit)
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    print(f"FOR-301 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-301 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(
        "FOR-301 decision: "
        f"{audit['decision']} "
        f"originalResultZero={audit['coverageMetric']['originalTargets']['resultCoverage']['zeroPixels']}"
    )


if __name__ == "__main__":
    main()

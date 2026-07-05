#!/usr/bin/env python3
"""Generate M86 fidelity burn-down evidence from promoted scene artifacts."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any


ROOT_CAUSES = {
    "m66-aaclip-bounded-grid-skia": "coverage.aa-grid-threshold",
    "m66-clip-rect-difference-skia": "coverage.edge-delta",
    "m66-crop-image-filter-nonnull-prepass-skia": "filter.bounds-prepass",
    "m66-path-aa-stroke-primitive-oracle": "coverage.stroke-raster-delta",
    "m60-bounded-nested-rrect-clip": "coverage.nested-clip-visual-parity",
    "m60-bounded-stroke-cap-join": "coverage.stroke-cap-join-selector",
    "m66-path-aa-dashing-edge-budget-refusal": "coverage.edge-count-exceeded",
    "m66-image-filter-crop-prepass-refusal": "filter.picture-prepass-required",
    "m66-font-complex-shaping-refusal": "glyph.complex-shaping-dependency",
}

PM_VALUE = {
    "Path AA / coverage": 5,
    "Image filters": 5,
    "Paint/blend/color": 4,
    "Bitmap/image sampling": 4,
    "Text/glyphs": 3,
    "Transforms/layers": 3,
    "Runtime effects": 3,
}

RISK = {
    "Path AA / coverage": "high",
    "Image filters": "high",
    "Paint/blend/color": "medium",
    "Bitmap/image sampling": "medium",
    "Text/glyphs": "dependency-gated",
    "Transforms/layers": "medium",
    "Runtime effects": "medium",
}


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text())


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def git_commit(root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=root,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except Exception:
        return "unknown"


def stats_for(project_root: Path, base_scene: str) -> dict[str, Any]:
    for prefix in (
        project_root / "reports/wgsl-pipeline/scenes/artifacts",
        project_root / "reports/wgsl-pipeline/scenes/generated/artifacts",
    ):
        path = prefix / base_scene / "stats.json"
        if path.is_file():
            return read_json(path)
    return {}


def route_for(project_root: Path, base_scene: str, lane: str) -> dict[str, Any]:
    for prefix in (
        project_root / "reports/wgsl-pipeline/scenes/artifacts",
        project_root / "reports/wgsl-pipeline/scenes/generated/artifacts",
    ):
        path = prefix / base_scene / f"route-{lane}.json"
        if path.is_file():
            return read_json(path)
    return {}


def number(value: Any, default: float = 0.0) -> float:
    return float(value) if isinstance(value, (int, float)) else default


def stat_number(stats: dict[str, Any], *keys: str, default: float = 0.0) -> float:
    current: Any = stats
    for key in keys:
        if not isinstance(current, dict):
            return default
        current = current.get(key)
    return number(current, default)


def scene_similarity(stats: dict[str, Any], lane: str) -> float:
    if lane in stats and isinstance(stats[lane], dict):
        return stat_number(stats, lane, "similarity")
    explicit = stat_number(stats, f"{lane}Similarity", default=-1.0)
    if explicit >= 0.0:
        return explicit
    pixels = stat_number(stats, "pixels", default=0.0)
    matching = stat_number(stats, "matchingPixels", default=-1.0)
    if pixels > 0.0 and matching >= 0.0:
        return (matching / pixels) * 100.0
    return 0.0


def scene_threshold(stats: dict[str, Any], lane: str, fallback: Any) -> float:
    if lane in stats and isinstance(stats[lane], dict):
        return stat_number(stats, lane, "threshold", default=number(fallback))
    key = f"{lane}Threshold"
    return stat_number(stats, key, default=number(fallback))


def artifact_paths(scene_id: str, status: str) -> dict[str, str]:
    base = f"dashboard/artifacts/{scene_id}"
    paths = {
        "reference": f"{base}/skia.png",
        "cpu": f"{base}/cpu.png",
        "cpuDiff": f"{base}/cpu-diff.png",
        "routeCpu": f"{base}/route-cpu.json",
        "routeGpu": f"{base}/route-gpu.json",
        "stats": f"{base}/stats.json",
    }
    if status == "pass":
        paths["gpu"] = f"{base}/gpu.png"
        paths["gpuDiff"] = f"{base}/gpu-diff.png"
    return paths


def row_from_contract(project_root: Path, scene: dict[str, Any]) -> dict[str, Any]:
    scene_id = scene["id"]
    base_scene = scene["baseArtifactScene"]
    status = scene["status"]
    stats = stats_for(project_root, base_scene)
    fallback = scene.get("threshold", stats.get("threshold", 0.0))
    cpu_similarity = scene_similarity(stats, "cpu")
    gpu_similarity = scene_similarity(stats, "gpu") if status == "pass" else None
    threshold = number(scene.get("threshold", stats.get("threshold", fallback)))
    cpu_threshold = scene_threshold(stats, "cpu", threshold)
    gpu_threshold = scene_threshold(stats, "gpu", threshold)
    family = scene["family"]
    reference_kind = scene["referenceKind"]
    cpu_route = route_for(project_root, base_scene, "cpu")
    gpu_route = route_for(project_root, base_scene, "gpu")
    root_cause = ROOT_CAUSES.get(scene_id)
    if root_cause is None and status == "expected-unsupported":
        root_cause = scene["fallbackReason"]
    if root_cause is None and status == "pass" and gpu_similarity is not None and gpu_similarity < max(gpu_threshold, 99.0):
        root_cause = "visual-diff.classification-required"
    if root_cause is None:
        root_cause = "none"

    return {
        "id": scene_id,
        "inventoryId": scene.get("inventoryId", ""),
        "family": family,
        "status": status,
        "referenceKind": reference_kind,
        "expectedRoute": scene["gpuRoute"],
        "fallbackReason": scene["fallbackReason"],
        "pmValue": PM_VALUE.get(family, 2),
        "risk": RISK.get(family, "medium"),
        "baseArtifactScene": base_scene,
        "rootCause": root_cause,
        "cpuSimilarity": round(cpu_similarity, 2),
        "gpuSimilarity": None if gpu_similarity is None else round(gpu_similarity, 2),
        "cpuThreshold": round(cpu_threshold, 2),
        "gpuThreshold": round(gpu_threshold, 2),
        "threshold": round(threshold, 2),
        "artifacts": artifact_paths(base_scene, status),
        "routeDiagnostics": {
            "cpu": cpu_route.get("selectedRoute", scene["cpuRoute"]),
            "gpu": gpu_route.get("selectedRoute", scene["gpuRoute"]),
        },
        "fidelityScoreEligible": reference_kind == "skia-upstream" and status == "pass",
        "nonClaim": scene.get("nonClaim", ""),
    }


def selected_rows(project_root: Path) -> list[dict[str, Any]]:
    contract = read_json(project_root / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json")
    rows = [row_from_contract(project_root, scene) for scene in contract["scenes"]]
    ranked = sorted(
        rows,
        key=lambda row: (
            -int(row["pmValue"]),
            row["risk"] == "dependency-gated",
            row["status"] != "pass",
            row["family"],
            row["id"],
        ),
    )
    return ranked


def build_evidence(project_root: Path) -> dict[str, Any]:
    rows = selected_rows(project_root)
    support_rows = [row for row in rows if row["status"] == "pass"]
    unsupported_rows = [row for row in rows if row["status"] == "expected-unsupported"]
    classified = [
        row for row in rows
        if row["rootCause"] != "none"
        or row["status"] == "expected-unsupported"
        or (row["gpuSimilarity"] is not None and row["gpuSimilarity"] < 100.0)
        or row["cpuSimilarity"] < 100.0
    ]
    skia_support = [row for row in support_rows if row["referenceKind"] == "skia-upstream"]
    family_counts: dict[str, int] = {}
    reference_counts: dict[str, int] = {}
    status_counts: dict[str, int] = {}
    root_cause_counts: dict[str, int] = {}
    for row in rows:
        family_counts[row["family"]] = family_counts.get(row["family"], 0) + 1
        reference_counts[row["referenceKind"]] = reference_counts.get(row["referenceKind"], 0) + 1
        status_counts[row["status"]] = status_counts.get(row["status"], 0) + 1
        root_cause_counts[row["rootCause"]] = root_cause_counts.get(row["rootCause"], 0) + 1

    return {
        "schemaVersion": 1,
        "milestone": "M86 Fidelity Burn-Down Wave 2",
        "generatedBy": "pipelineM86FidelityBurndown",
        "gitCommit": git_commit(project_root),
        "linear": {
            "epic": "FOR-102",
            "tickets": ["FOR-164", "FOR-165", "FOR-166", "FOR-167", "FOR-168"],
        },
        "source": {
            "promotionContract": "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
            "dashboardContract": "reports/wgsl-pipeline/scenes/generated/results.json",
            "spec": ".upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md",
        },
        "counters": {
            "rankedCandidates": len(rows),
            "supportRows": len(support_rows),
            "unsupportedRows": len(unsupported_rows),
            "classifiedRows": len(classified),
            "skiaComparableSupportRows": len(skia_support),
            "familyCounts": dict(sorted(family_counts.items())),
            "referenceKindCounts": dict(sorted(reference_counts.items())),
            "statusCounts": dict(sorted(status_counts.items())),
            "rootCauseCounts": dict(sorted(root_cause_counts.items())),
        },
        "dashboardGateExpectation": {
            "unexpectedFail": 0,
            "trackedGap": 0,
            "globalThresholdWeakened": False,
        },
        "burnDown": {
            "selectedRows": rows,
            "classifiedRows": classified,
            "highValueRemediationTargets": [
                {
                    "id": "m66-clip-rect-difference-skia",
                    "reason": "GPU passes only under a family-specific 80.0 threshold; keep support but track coverage edge delta before raising fidelity confidence.",
                    "nextAction": "Implement or tighten rectangular difference coverage parity, then raise the row threshold with before/after artifacts.",
                },
                {
                    "id": "m66-crop-image-filter-nonnull-prepass-skia",
                    "reason": "GPU route is close, CPU/reference parity is weak because filter bounds/prepass behavior is still provisional.",
                    "nextAction": "Make crop bounds/prepass semantics explicit and regenerate CPU/GPU/reference diffs before counting broader image-filter fidelity.",
                },
                {
                    "id": "m66-path-aa-stroke-primitive-oracle",
                    "reason": "The row is useful PM evidence but is test-oracle, not Skia-comparable, and still has visible AA/stroke raster deltas.",
                    "nextAction": "Attach Skia reference or promote a narrower stroke primitive with higher parity; keep CPU-oracle rows out of Skia fidelity score.",
                },
            ],
            "fixesAppliedInThisSprint": [],
            "fixNonClaim": "M86 ranks, classifies, and exposes burn-down targets. It does not claim a renderer visual fix unless a later patch provides before/after rendered artifacts.",
        },
        "readinessDelta": {
            "weightedPercentBefore": 67.75,
            "weightedPercentAfter": 67.75,
            "reason": "No new generated support row, Skia-comparable row, runtime capability, or measured gate denominator changed; M86 improves fidelity operability and next-fix selection.",
        },
    }


def write_markdown(path: Path, evidence: dict[str, Any]) -> None:
    counters = evidence["counters"]
    rows = evidence["burnDown"]["selectedRows"]
    lines = [
        "# M86 Fidelity Burn-Down Wave 2",
        "",
        "Scope: FOR-102, FOR-164, FOR-165, FOR-166, FOR-167, FOR-168",
        "",
        "## Summary",
        "",
        "M86 turns the cumulative M66 GM/reference wave into an explicit fidelity burn-down queue. It ranks the next PM-visible candidates, preserves support/refusal rows, classifies visual diffs by root cause, and keeps CPU-oracle rows out of Skia-fidelity accounting.",
        "",
        "No global visual threshold was weakened. No renderer visual fix is claimed in this sprint without before/after rendered artifacts.",
        "",
        "## Counters",
        "",
        "| Counter | Value |",
        "|---|---:|",
        f"| Ranked candidates | {counters['rankedCandidates']} |",
        f"| Support rows | {counters['supportRows']} |",
        f"| Expected unsupported rows | {counters['unsupportedRows']} |",
        f"| Classified rows | {counters['classifiedRows']} |",
        f"| Skia-comparable support rows | {counters['skiaComparableSupportRows']} |",
        "",
        "## Family Split",
        "",
        "| Family | Rows |",
        "|---|---:|",
    ]
    for family, count in counters["familyCounts"].items():
        lines.append(f"| {family} | {count} |")
    lines += [
        "",
        "## Reference Kind Split",
        "",
        "| referenceKind | Rows |",
        "|---|---:|",
    ]
    for kind, count in counters["referenceKindCounts"].items():
        lines.append(f"| `{kind}` | {count} |")
    lines += [
        "",
        "## Ranked Candidate Rows",
        "",
        "| Row | Family | Status | Reference | CPU | GPU | Root cause |",
        "|---|---|---|---|---:|---:|---|",
    ]
    for row in rows:
        gpu = "n/a" if row["gpuSimilarity"] is None else f"{row['gpuSimilarity']:.2f}%"
        lines.append(
            f"| `{row['id']}` | {row['family']} | `{row['status']}` | `{row['referenceKind']}` | "
            f"{row['cpuSimilarity']:.2f}% | {gpu} | `{row['rootCause']}` |"
        )
    lines += [
        "",
        "## Remediation Targets",
        "",
        "| Row | Why it matters | Next action |",
        "|---|---|---|",
    ]
    for target in evidence["burnDown"]["highValueRemediationTargets"]:
        lines.append(f"| `{target['id']}` | {target['reason']} | {target['nextAction']} |")
    lines += [
        "",
        "## Readiness Accounting",
        "",
        f"Weighted readiness remains **{evidence['readinessDelta']['weightedPercentAfter']}%**.",
        "",
        evidence["readinessDelta"]["reason"],
        "",
        "## Validation",
        "",
        "```bash",
        "./gradlew --no-daemon pipelineM86FidelityBurndown pipelineSceneDashboardGate pipelinePmBundle",
        "python3 -m json.tool reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json >/dev/null",
        "git diff --check",
        "```",
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--output-dir", default="reports/wgsl-pipeline/m86-fidelity-burndown")
    parser.add_argument("--report", default="reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    evidence = build_evidence(project_root)
    output_dir = project_root / args.output_dir
    write_json(output_dir / "evidence.json", evidence)
    write_markdown(output_dir / "evidence.md", evidence)
    write_markdown(project_root / args.report, evidence)
    print(f"Wrote M86 fidelity burn-down evidence: {(output_dir / 'evidence.json').relative_to(project_root)}")


if __name__ == "__main__":
    main()

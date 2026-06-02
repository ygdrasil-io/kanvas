#!/usr/bin/env python3
"""Build the FOR-234 target-colorspace candidate inventory from scene artifacts."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


THRESHOLD = 99.95
ARTIFACTS_DIR = Path("reports/wgsl-pipeline/scenes/artifacts")
OUTPUT_JSON = Path("reports/wgsl-pipeline/scenes/artifacts/m60-target-color-candidate-inventory.json")
OUTPUT_MARKDOWN = Path("reports/wgsl-pipeline/2026-06-02-m60-target-color-candidate-inventory.md")

OUT_OF_SCOPE_KEYWORDS = (
    ("bitmap", "bitmap shader path is outside the FOR-234 solid-color AA scope"),
    ("gradient", "gradient path is outside the FOR-234 solid-color AA scope"),
    ("runtime-effect", "runtime-effect descriptor path is outside the FOR-234 solid-color AA scope"),
    ("image-filter", "image-filter path is outside the FOR-234 solid-color AA scope"),
    ("font", "font/text path is outside the FOR-234 solid-color AA scope"),
    ("crop", "crop/prepass path is outside the FOR-234 solid-color AA scope"),
    ("sweep", "sweep-gradient path is outside the FOR-234 solid-color AA scope"),
)


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text())


def number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None


def find_tolerance_profile(stats: dict[str, Any]) -> list[dict[str, Any]]:
    profile = stats.get("experimentalGpuToleranceProfile")
    if isinstance(profile, list):
        return profile
    profile = stats.get("targetToleranceProfile")
    if isinstance(profile, list):
        return profile
    profile = stats.get("toleranceProfile")
    if isinstance(profile, list):
        return profile
    return []


def find_tolerance8(stats: dict[str, Any]) -> float | None:
    for row in find_tolerance_profile(stats):
        if row.get("tolerance") == 8:
            return number(row.get("similarity"))
    return None


def find_exact(stats: dict[str, Any]) -> float | None:
    exact = number(stats.get("gpuSimilarity"))
    if exact is not None:
        return exact
    gpu = stats.get("gpu")
    if isinstance(gpu, dict):
        return number(gpu.get("similarity"))
    return None


def find_target_blend_exact(stats: dict[str, Any]) -> float | None:
    exact = number(stats.get("experimentalGpuSimilarity"))
    if exact is not None:
        return exact
    return number(stats.get("targetExactSimilarity"))


def route_for(scene_dir: Path) -> dict[str, Any]:
    path = scene_dir / "route-gpu.json"
    if not path.exists():
        return {}
    return read_json(path)


def route_name(route: dict[str, Any]) -> str:
    for key in ("selectedRoute", "coverageStrategy", "gpuRoute", "status"):
        value = route.get(key)
        if isinstance(value, str) and value:
            return value
    return "missing-route-gpu.json"


def current_cause(stats: dict[str, Any], route: dict[str, Any]) -> str:
    for source in (route, stats):
        for key in ("remainingRootCause", "rootCause", "fallbackReason"):
            value = source.get(key)
            if isinstance(value, str) and value:
                return value
    return "none"


def out_of_scope_reason(scene_id: str, route: dict[str, Any]) -> str | None:
    haystack = " ".join(
        str(value)
        for value in (
            scene_id,
            route.get("drawKind"),
            route.get("selectedRoute"),
            route.get("coverageStrategy"),
            route.get("pipelineKey"),
        )
        if value is not None
    ).lower()
    for keyword, reason in OUT_OF_SCOPE_KEYWORDS:
        if keyword in haystack:
            return reason
    return None


def classify(scene_id: str, stats: dict[str, Any], route: dict[str, Any]) -> tuple[str, str, str]:
    exact = find_exact(stats)
    experimental_exact = find_target_blend_exact(stats)
    tolerance8 = find_tolerance8(stats)
    target_blend = stats.get("targetColorSpaceBlend") is True
    status = route.get("status") or stats.get("gpuStatus") or stats.get("status")
    cause = current_cause(stats, route)

    if scene_id == "m60-target-colorspace-neutral-aa":
        return (
            "diagnostic-fixture",
            "candidate-evidence",
            "isolated neutral AA fixture proves targetColorSpaceBlend can match the CPU sample for the covered solid-color AA path",
        )

    if target_blend and experimental_exact is not None:
        if status != "expected-unsupported":
            return (
                "not-candidate-target-blend-negative",
                "non-candidate",
                "targetColorSpaceBlend evidence exists, but the normal route is not an expected-unsupported candidate and the target-blend render does not reach the exact support threshold",
            )
        if experimental_exact >= THRESHOLD:
            return (
                "candidate-promotable-only-with-exact-proof",
                "candidate",
                "targetColorSpaceBlend evidence reaches the exact threshold; promotion still requires route and contract review",
            )
        if tolerance8 is not None and tolerance8 >= THRESHOLD:
            return (
                "candidate-residual-aa",
                "candidate",
                "targetColorSpaceBlend improves the scene into tolerance-8 parity, but exact parity remains below 99.95 due to the recorded AA residual",
            )
        return (
            "candidate-unresolved",
            "candidate",
            "targetColorSpaceBlend evidence exists, but neither exact nor tolerance-8 parity reaches the support threshold",
        )

    scoped_out = out_of_scope_reason(scene_id, route)
    if scoped_out is not None:
        return ("not-candidate-out-of-scope", "non-candidate", scoped_out)

    if exact is not None and exact >= THRESHOLD:
        return (
            "not-candidate-already-exact",
            "non-candidate",
            "current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed",
        )

    if status == "expected-unsupported":
        return (
            "not-candidate-stable-refusal",
            "non-candidate",
            f"current route is an expected-unsupported refusal with stable cause `{cause}` and no targetColorSpaceBlend evidence",
        )

    if exact is not None and exact < THRESHOLD:
        return (
            "not-candidate-no-target-color-signal",
            "non-candidate",
            "exact score is below 99.95, but artifacts provide no tolerance-8/color-space signal for the target blend pilot",
        )

    return (
        "not-candidate-insufficient-metrics",
        "non-candidate",
        "artifacts do not expose exact GPU similarity or targetColorSpaceBlend evidence",
    )


def build_inventory(project_root: Path) -> dict[str, Any]:
    artifacts_dir = project_root / ARTIFACTS_DIR
    rows: list[dict[str, Any]] = []

    for stats_path in sorted(artifacts_dir.glob("*/stats.json")):
        scene_dir = stats_path.parent
        stats = read_json(stats_path)
        route = route_for(scene_dir)
        scene_id = str(stats.get("sceneId") or scene_dir.name)
        exact = find_exact(stats)
        experimental_exact = find_target_blend_exact(stats)
        tolerance8 = find_tolerance8(stats)
        decision, bucket, reason = classify(scene_id, stats, route)
        row = {
            "sceneId": scene_id,
            "bucket": bucket,
            "decision": decision,
            "exactSimilarity": exact,
            "targetColorSpaceBlendExactSimilarity": experimental_exact,
            "tolerance8Similarity": tolerance8,
            "threshold": number(stats.get("threshold")) or THRESHOLD,
            "currentCause": current_cause(stats, route),
            "webgpuStatus": route.get("status") or stats.get("gpuStatus") or stats.get("status"),
            "webgpuRoute": route_name(route),
            "pipelineKey": route.get("pipelineKey"),
            "candidateReason": reason,
            "targetColorSpaceBlendEvidence": stats.get("targetColorSpaceBlend") is True,
            "artifacts": {
                "stats": str(stats_path.relative_to(project_root)),
                "routeGpu": str((scene_dir / "route-gpu.json").relative_to(project_root))
                if (scene_dir / "route-gpu.json").exists()
                else None,
            },
        }
        rows.append(row)

    candidate_rows = [row for row in rows if row["bucket"] == "candidate"]
    diagnostic_rows = [row for row in rows if row["bucket"] == "candidate-evidence"]
    non_candidate_rows = [row for row in rows if row["bucket"] == "non-candidate"]

    return {
        "id": "m60-target-color-candidate-inventory",
        "linearIssue": "FOR-234",
        "generatedDate": "2026-06-02",
        "scope": "Inventory only: solid-color AA targetColorSpaceBlend candidates from existing WGSL scene artifacts.",
        "threshold": THRESHOLD,
        "policy": {
            "globalTargetColorSpaceBlendEnabled": False,
            "promotionRequiresExactSimilarityAtLeast": THRESHOLD,
            "coveredPilotDrawKinds": ["RectDraw", "StencilCoverAaPolygonDraw"],
            "outOfScopeFamilies": [
                "gradients",
                "bitmaps",
                "runtime effects",
                "image filters",
                "layers",
                "text/font masks",
                "blur/drop-shadow paths",
            ],
            "stableRefusalsPreserved": True,
        },
        "summary": {
            "scenesInspected": len(rows),
            "candidateScenes": len(candidate_rows),
            "diagnosticFixtures": len(diagnostic_rows),
            "nonCandidateScenes": len(non_candidate_rows),
            "promotableScenes": sum(
                1
                for row in candidate_rows
                if (row["targetColorSpaceBlendExactSimilarity"] or 0.0) >= THRESHOLD
            ),
        },
        "candidates": candidate_rows,
        "diagnosticFixtures": diagnostic_rows,
        "nonCandidates": non_candidate_rows,
    }


def fmt(value: Any) -> str:
    if value is None:
        return "n/a"
    if isinstance(value, float):
        return f"{value:.2f}"
    return str(value)


def markdown_table(rows: list[dict[str, Any]]) -> list[str]:
    lines = [
        "| Scene | Decision | Exact | Target exact | Tol. 8 | Cause | WebGPU route | Reason |",
        "|---|---|---:|---:|---:|---|---|---|",
    ]
    for row in rows:
        lines.append(
            "| "
            + " | ".join(
                [
                    f"`{row['sceneId']}`",
                    f"`{row['decision']}`",
                    fmt(row["exactSimilarity"]),
                    fmt(row["targetColorSpaceBlendExactSimilarity"]),
                    fmt(row["tolerance8Similarity"]),
                    f"`{row['currentCause']}`",
                    f"`{row['webgpuRoute']}`",
                    row["candidateReason"],
                ]
            )
            + " |"
        )
    return lines


def write_markdown(inventory: dict[str, Any], path: Path) -> None:
    summary = inventory["summary"]
    lines = [
        "# M60 Target-Color Candidate Inventory - 2026-06-02",
        "",
        "Linear: `FOR-234`",
        "",
        "## Decision",
        "",
        "`targetColorSpaceBlend` remains an opt-in diagnostic mode. No scene is",
        "promoted by this inventory, and no global rendering path is changed.",
        "",
        "## Summary",
        "",
        "| Metric | Value |",
        "|---|---:|",
        f"| Scenes inspected | `{summary['scenesInspected']}` |",
        f"| Candidate scenes | `{summary['candidateScenes']}` |",
        f"| Diagnostic fixtures | `{summary['diagnosticFixtures']}` |",
        f"| Non-candidate scenes | `{summary['nonCandidateScenes']}` |",
        f"| Promotable scenes by exact `{inventory['threshold']:.2f}%` proof | `{summary['promotableScenes']}` |",
        "",
        "## Candidate Scenes",
        "",
    ]
    lines.extend(markdown_table(inventory["candidates"]))
    lines.extend(
        [
            "",
            "## Diagnostic Fixtures",
            "",
        ]
    )
    lines.extend(markdown_table(inventory["diagnosticFixtures"]))
    lines.extend(
        [
            "",
            "## Non-Candidate Scenes",
            "",
        ]
    )
    lines.extend(markdown_table(inventory["nonCandidates"]))
    lines.extend(
        [
            "",
            "## Policy Checks",
            "",
            "- `targetColorSpaceBlend` is not globally enabled.",
            "- No scene is promoted without exact similarity `>= 99.95%`.",
            "- Gradients, bitmaps, runtime effects, image filters, layers, text/font masks, and blur/drop-shadow paths remain out of scope.",
            "- Existing expected-unsupported diagnostics are preserved as route/cause fields.",
            "",
            "## Artifacts",
            "",
            "- `reports/wgsl-pipeline/scenes/artifacts/m60-target-color-candidate-inventory.json`",
            "- `reports/wgsl-pipeline/2026-06-02-m60-target-color-candidate-inventory.md`",
            "",
            "## Validation",
            "",
            "```text",
            "rtk python3 scripts/m60_target_color_candidate_inventory.py",
            "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/m60-target-color-candidate-inventory.json",
            "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
            "rtk git diff --check",
            "```",
        ]
    )
    path.write_text("\n".join(lines) + "\n")


def validate_inventory(inventory: dict[str, Any]) -> None:
    policy = inventory["policy"]
    if policy["globalTargetColorSpaceBlendEnabled"] is not False:
        raise SystemExit("targetColorSpaceBlend policy must remain opt-in")
    if inventory["summary"]["promotableScenes"] != 0:
        raise SystemExit("FOR-234 must not promote scenes from inventory evidence alone")
    for row in inventory["candidates"]:
        if row["targetColorSpaceBlendExactSimilarity"] is None:
            raise SystemExit(f"candidate `{row['sceneId']}` lacks targetColorSpaceBlend exact evidence")
        if row["webgpuStatus"] != "expected-unsupported":
            raise SystemExit(f"candidate `{row['sceneId']}` must not change normal route status")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--json", default=str(OUTPUT_JSON))
    parser.add_argument("--markdown", default=str(OUTPUT_MARKDOWN))
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    inventory = build_inventory(project_root)
    validate_inventory(inventory)

    json_path = project_root / args.json
    markdown_path = project_root / args.markdown
    json_path.parent.mkdir(parents=True, exist_ok=True)
    markdown_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(inventory, indent=2, sort_keys=True) + "\n")
    write_markdown(inventory, markdown_path)

    print(f"Wrote target-color candidate inventory JSON: {json_path.relative_to(project_root)}")
    print(f"Wrote target-color candidate inventory report: {markdown_path.relative_to(project_root)}")


if __name__ == "__main__":
    main()

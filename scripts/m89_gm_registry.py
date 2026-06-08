#!/usr/bin/env python3
"""Generate the M89 Skia-like GM support/refusal registry."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
GENERATED_DIR = ROOT / "reports/wgsl-pipeline/scenes/generated"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m89-gm-registry"
REGISTRY_JSON = OUTPUT_DIR / "registry.json"
REGISTRY_MD = OUTPUT_DIR / "registry.md"

INPUTS = [
    ("generated-dashboard", GENERATED_DIR / "results.json"),
    ("d50-visibility", GENERATED_DIR / "d50-gm-dashboard-visibility.json"),
    ("d53-visibility", GENERATED_DIR / "dash-hairline-stroke-gm-dashboard-visibility.json"),
]
M66_PROMOTION = GENERATED_DIR / "m66-gm-promotion-wave.json"
M86_BURNDOWN = ROOT / "reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json"
M88_RC2 = ROOT / "reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json"

VALID_STATUSES = {
    "pass",
    "expected-unsupported",
    "dependency-gated",
    "implementation-gap",
    "reporting-only",
    "below-threshold-excluded",
}


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise AssertionError(f"missing JSON file: {rel(path)}")
    root = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(root, dict):
        raise AssertionError(f"{rel(path)} root must be an object")
    return root


def scenes_from(path: Path) -> list[dict[str, Any]]:
    root = load_json(path)
    scenes = root.get("scenes")
    if not isinstance(scenes, list):
        raise AssertionError(f"{rel(path)} must contain scenes[]")
    typed: list[dict[str, Any]] = []
    for index, scene in enumerate(scenes):
        if not isinstance(scene, dict):
            raise AssertionError(f"{rel(path)} scenes[{index}] must be an object")
        typed.append(scene)
    return typed


def canonical_family(scene: dict[str, Any]) -> str:
    tags = set(scene.get("tags", []))
    fallback = str(scene.get("fallbackReason") or "")
    family = str(scene.get("family") or "").lower()
    scene_id = str(scene.get("id") or "")

    if "font." in fallback or "feature.text" in tags or "feature.font" in tags or "font" in scene_id or "text" in scene_id:
        return "text-glyph"
    if "runtime-effect" in fallback or "feature.runtime-effect" in tags or "runtime-effect" in scene_id:
        return "runtime-effect"
    if "image-filter" in fallback or "feature.image-filter" in tags or "image-filter" in scene_id:
        return "image-filter"
    if (
        "image." in fallback
        or "image-source" in fallback
        or "bitmap" in scene_id
        or "image" in scene_id
        or "feature.image.bitmap" in tags
        or "bitmap/image" in family
    ):
        return "bitmap-image"
    if "gradient" in fallback or "gradient" in scene_id or "feature.gradient" in tags:
        return "gradient"
    if (
        "coverage." in fallback
        or "path-aa" in scene_id
        or "clip" in scene_id
        or "stroke" in scene_id
        or "dash" in scene_id
        or "hair" in scene_id
        or "feature.path-aa" in tags
        or "coverage" in family
    ):
        return "path-aa"
    if "blend" in scene_id or "color-filter" in scene_id or "feature.blend" in tags:
        return "blend-color"
    if "transform" in scene_id or "layer" in scene_id:
        return "transform-layer"
    return "blend-color"


def route_status(scene: dict[str, Any], backend: str) -> str:
    nested = scene.get(backend)
    if isinstance(nested, dict):
        status = nested.get("status")
        if isinstance(status, str):
            return status
    route = scene.get(f"{backend}Route")
    if isinstance(route, str):
        if route.endswith(".expected-unsupported"):
            return "expected-unsupported"
        return "pass"
    return "unavailable"


def fallback_reason(scene: dict[str, Any]) -> str:
    direct = scene.get("fallbackReason")
    if isinstance(direct, str) and direct:
        return direct
    gpu = scene.get("gpu")
    if isinstance(gpu, dict):
        route = gpu.get("route")
        if isinstance(route, dict):
            fallback = route.get("fallbackReason")
            if isinstance(fallback, str) and fallback:
                return fallback
    return "none"


def artifacts(scene: dict[str, Any]) -> dict[str, str]:
    result: dict[str, str] = {}
    reference = scene.get("reference")
    if isinstance(reference, str):
        result["reference"] = reference
    for backend in ("cpu", "gpu"):
        nested = scene.get(backend)
        if isinstance(nested, dict):
            image = nested.get("image")
            diff = nested.get("diff")
            if isinstance(image, str):
                result[backend] = image
            if isinstance(diff, str):
                result[f"{backend}Diff"] = diff
    policy = scene.get("policyArtifact")
    if isinstance(policy, str):
        result["policy"] = policy
    return result


def metrics(scene: dict[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    gpu = scene.get("gpu")
    if isinstance(gpu, dict):
        similarity = gpu.get("similarity")
        if isinstance(similarity, (int, float)):
            result["gpuSimilarity"] = similarity
        stats = gpu.get("stats")
        if isinstance(stats, dict):
            threshold = stats.get("threshold")
            max_delta = stats.get("maxChannelDelta")
            if isinstance(threshold, (int, float)):
                result["threshold"] = threshold
            if isinstance(max_delta, (int, float)):
                result["maxChannelDelta"] = max_delta
    return result


def optional_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    root = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(root, dict):
        raise AssertionError(f"{rel(path)} root must be an object")
    return root


def build_evidence_indexes() -> dict[str, Any]:
    m66 = optional_json(M66_PROMOTION)
    m86 = optional_json(M86_BURNDOWN)
    m88 = optional_json(M88_RC2)

    m66_by_base: dict[str, list[dict[str, Any]]] = {}
    for scene in m66.get("scenes", []):
        if not isinstance(scene, dict):
            continue
        base = scene.get("baseArtifactScene")
        if isinstance(base, str) and base:
            m66_by_base.setdefault(base, []).append(scene)

    m86_by_base: dict[str, list[dict[str, Any]]] = {}
    burn_down = m86.get("burnDown", {})
    selected = burn_down.get("selectedRows", []) if isinstance(burn_down, dict) else []
    for row in selected:
        if not isinstance(row, dict):
            continue
        base = row.get("baseArtifactScene")
        if isinstance(base, str) and base:
            m86_by_base.setdefault(base, []).append(row)

    return {
        "m66": m66,
        "m66ByBase": m66_by_base,
        "m86": m86,
        "m86ByBase": m86_by_base,
        "m88": m88,
    }


def evidence_links(scene_id: str, indexes: dict[str, Any]) -> dict[str, Any]:
    links: dict[str, Any] = {}
    m66_rows = indexes["m66ByBase"].get(scene_id, [])
    if m66_rows:
        links["m66"] = [
            {
                "id": row.get("id"),
                "inventoryId": row.get("inventoryId", ""),
                "status": row.get("status"),
                "referenceKind": row.get("referenceKind"),
                "fallbackReason": row.get("fallbackReason", "none"),
            }
            for row in m66_rows
        ]

    m86_rows = indexes["m86ByBase"].get(scene_id, [])
    if m86_rows:
        links["m86"] = [
            {
                "id": row.get("id"),
                "rootCause": row.get("rootCause", "none"),
                "pmValue": row.get("pmValue"),
                "risk": row.get("risk"),
                "fidelityScoreEligible": row.get("fidelityScoreEligible", False),
                "gpuSimilarity": row.get("gpuSimilarity"),
                "gpuThreshold": row.get("gpuThreshold"),
            }
            for row in m86_rows
        ]
    return links


def owner_for(source: str, scene: dict[str, Any]) -> str:
    if source == "d50-visibility":
        return "M89"
    if source == "d53-visibility":
        return "M89"
    generation = scene.get("generation")
    if isinstance(generation, dict):
        report = str(generation.get("sourceReport") or "")
        for marker in ("m48", "m60", "m61", "m62", "m63", "m64", "d52"):
            if marker in report.lower():
                return marker.upper()
    return "M89"


def next_ticket_type(scene: dict[str, Any], status: str, family: str) -> str:
    if status == "pass":
        return "implementation"
    fallback = fallback_reason(scene)
    if family == "text-glyph" and ("complex-shaping" in fallback or "emoji" in fallback or "color-glyph" in fallback):
        return "dependency"
    if "row-specific-artifacts-required" in fallback:
        return "policy-visibility"
    if "below-threshold" in fallback:
        return "fidelity-burndown"
    return "implementation"


def pm_impact(status: str, family: str) -> str:
    if status == "pass":
        return "low"
    if family in {"image-filter", "text-glyph", "runtime-effect", "bitmap-image", "path-aa"}:
        return "high"
    return "medium"


def normalize_scene(source: str, scene: dict[str, Any], indexes: dict[str, Any]) -> dict[str, Any]:
    scene_id = scene.get("id")
    if not isinstance(scene_id, str) or not scene_id:
        raise AssertionError(f"{source}: row is missing id")
    status = scene.get("status")
    if not isinstance(status, str):
        raise AssertionError(f"{source}:{scene_id}: missing status")
    if status not in VALID_STATUSES:
        raise AssertionError(f"{source}:{scene_id}: invalid status {status}")

    fallback = fallback_reason(scene)
    route_gpu = route_status(scene, "gpu")
    support_claim = status == "pass" and route_gpu == "pass" and fallback == "none"
    family = canonical_family(scene)

    if status == "expected-unsupported" and fallback == "none":
        raise AssertionError(f"{source}:{scene_id}: expected-unsupported row must have fallback reason")
    if support_claim and source != "generated-dashboard":
        raise AssertionError(f"{source}:{scene_id}: policy visibility input must not claim support")

    return {
        "rowId": scene_id,
        "title": scene.get("title", scene_id),
        "source": source,
        "family": family,
        "status": status,
        "referenceKind": scene.get("referenceKind", "none"),
        "supportClaim": support_claim,
        "fallbackReason": fallback,
        "routeCpu": route_status(scene, "cpu"),
        "routeGpu": route_gpu,
        "artifacts": artifacts(scene),
        "metrics": metrics(scene),
        "evidenceLinks": evidence_links(scene_id, indexes),
        "owningMilestone": owner_for(source, scene),
        "nextTicketType": next_ticket_type(scene, status, family),
        "pmImpact": pm_impact(status, family),
        "policyOnly": bool(scene.get("policyOnlyArtifacts")),
        "nonClaim": scene.get("nonClaim", ""),
    }


def build_registry() -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    input_paths: list[str] = []
    indexes = build_evidence_indexes()
    for source, path in INPUTS:
        input_paths.append(rel(path))
        for scene in scenes_from(path):
            rows.append(normalize_scene(source, scene, indexes))

    row_ids = [row["rowId"] for row in rows]
    duplicates = sorted(row_id for row_id, count in Counter(row_ids).items() if count > 1)
    if duplicates:
        raise AssertionError(f"duplicate registry row ids: {duplicates}")

    status_counts = Counter(row["status"] for row in rows)
    family_counts = Counter(row["family"] for row in rows)
    source_counts = Counter(row["source"] for row in rows)
    support_claims = sum(1 for row in rows if row["supportClaim"])
    linked_m66 = sum(1 for row in rows if "m66" in row["evidenceLinks"])
    linked_m86 = sum(1 for row in rows if "m86" in row["evidenceLinks"])
    m88 = indexes["m88"]
    m88_counters = m88.get("dashboardCounters", {}) if isinstance(m88.get("dashboardCounters"), dict) else {}

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m89_gm_registry.py",
        "description": "M89 normalized GM support/refusal registry. This artifact changes no support claims.",
        "sourceInputs": input_paths + [rel(M66_PROMOTION), rel(M86_BURNDOWN), rel(M88_RC2)],
        "evidencePackages": {
            "m66": {
                "path": rel(M66_PROMOTION),
                "selectedRows": len(indexes["m66"].get("scenes", [])),
                "rejectedRows": len(indexes["m66"].get("rejectedRows", [])),
                "linkedRegistryRows": linked_m66,
            },
            "m86": {
                "path": rel(M86_BURNDOWN),
                "rankedCandidates": indexes["m86"].get("counters", {}).get("rankedCandidates"),
                "classifiedRows": indexes["m86"].get("counters", {}).get("classifiedRows"),
                "skiaComparableSupportRows": indexes["m86"].get("counters", {}).get("skiaComparableSupportRows"),
                "linkedRegistryRows": linked_m86,
                "globalThresholdWeakened": indexes["m86"].get("dashboardGateExpectation", {}).get("globalThresholdWeakened"),
            },
            "m88": {
                "path": rel(M88_RC2),
                "status": indexes["m88"].get("status"),
                "passRows": m88_counters.get("passRows"),
                "expectedUnsupportedRows": m88_counters.get("expectedUnsupportedRows"),
                "failRows": m88_counters.get("failRows"),
                "trackedGapRows": m88_counters.get("trackedGapRows"),
                "categories": [category.get("category") for category in indexes["m88"].get("categories", []) if isinstance(category, dict)],
            },
        },
        "nonClaims": [
            "Policy-only visibility rows do not count as support.",
            "Expected-unsupported rows remain visible until row-specific evidence proves support.",
            "Rows that only miss strict similarity/tolerance thresholds belong in fidelity burn-down, not production missing-feature accounting.",
            "WGSL remains the WebGPU shader target; SkSL is compatibility/refusal wording only.",
        ],
        "counters": {
            "totalRows": len(rows),
            "supportClaims": support_claims,
            "status": dict(sorted(status_counts.items())),
            "family": dict(sorted(family_counts.items())),
            "source": dict(sorted(source_counts.items())),
            "policyOnlyRows": sum(1 for row in rows if row["policyOnly"]),
            "expectedUnsupportedWithFallback": sum(
                1 for row in rows if row["status"] == "expected-unsupported" and row["fallbackReason"] != "none"
            ),
            "linkedM66Rows": linked_m66,
            "linkedM86Rows": linked_m86,
        },
        "rows": sorted(rows, key=lambda row: (row["source"], row["family"], row["rowId"])),
    }


def write_markdown(registry: dict[str, Any]) -> None:
    counters = registry["counters"]
    lines = [
        "# M89 GM Support/Refusal Registry",
        "",
        "Status: generated evidence",
        "",
        "This registry normalizes current generated dashboard rows and policy-only GM visibility rows. It does not promote support, weaken thresholds, or change render paths.",
        "",
        "## Counters",
        "",
        f"- Total rows: `{counters['totalRows']}`",
        f"- Support claims: `{counters['supportClaims']}`",
        f"- Policy-only rows: `{counters['policyOnlyRows']}`",
        f"- Expected unsupported with fallback: `{counters['expectedUnsupportedWithFallback']}`",
        f"- Linked M66 rows: `{counters['linkedM66Rows']}`",
        f"- Linked M86 rows: `{counters['linkedM86Rows']}`",
        "",
        "### Status",
        "",
    ]
    for status, count in counters["status"].items():
        lines.append(f"- `{status}`: `{count}`")
    lines.extend(["", "### Family", ""])
    for family, count in counters["family"].items():
        lines.append(f"- `{family}`: `{count}`")
    lines.extend(["", "## Non-Claims", ""])
    for non_claim in registry["nonClaims"]:
        lines.append(f"- {non_claim}")
    lines.extend(["", "## Follow-Up Focus", ""])
    lines.append("- Convert policy-only rows into row-specific evidence without changing claims.")
    lines.append("- Keep dependency-gated text/font rows visible until real dependencies land.")
    lines.append("- Keep tolerance-only rows in fidelity burn-down rather than production missing-feature counts.")
    REGISTRY_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    try:
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        registry = build_registry()
        REGISTRY_JSON.write_text(json.dumps(registry, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        write_markdown(registry)
    except AssertionError as error:
        print(f"m89_gm_registry: FAIL: {error}", file=sys.stderr)
        return 1
    print(f"wrote {rel(REGISTRY_JSON)}")
    print(f"wrote {rel(REGISTRY_MD)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

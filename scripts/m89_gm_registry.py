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
M89_FEATURE_BREADTH = ROOT / "reports/wgsl-pipeline/m89-feature-breadth/evidence.json"
M89_FEATURE_BREADTH_REPORT = ROOT / "reports/wgsl-pipeline/m89-feature-breadth/evidence.md"
ROW_SPECIFIC_REFUSALS = {
    "skia-gm-image": {
        "linear": "FOR-466",
        "json": GENERATED_DIR / "for466-skia-gm-image-evidence.json",
        "report": ROOT / "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md",
    },
    "skia-gm-imagesource": {
        "linear": "FOR-467",
        "json": GENERATED_DIR / "for467-skia-gm-imagesource-evidence.json",
        "report": ROOT / "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md",
    },
    "skia-gm-offsetimagefilter": {
        "linear": "FOR-468",
        "json": GENERATED_DIR / "for468-skia-gm-offsetimagefilter-evidence.json",
        "report": ROOT / "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md",
    },
    "skia-gm-imagemakewithfilter": {
        "linear": "FOR-470",
        "json": GENERATED_DIR / "for470-skia-gm-imagemakewithfilter-evidence.json",
        "report": ROOT / "reports/wgsl-pipeline/2026-06-08-for-470-skia-gm-imagemakewithfilter-evidence.md",
    },
    "skia-gm-pathfill": {
        "linear": "FOR-469",
        "json": GENERATED_DIR / "for469-skia-gm-pathfill-evidence.json",
        "report": ROOT / "reports/wgsl-pipeline/2026-06-06-for-469-skia-gm-pathfill-evidence.md",
    },
}
D53_GROUPED_POLICY_REFUSALS = {
    "skia-gm-dashcubics",
    "skia-gm-dashing",
    "skia-gm-hairlines",
    "skia-gm-hairmodes",
    "skia-gm-scaledstrokes",
    "skia-gm-strokedlines",
    "skia-gm-strokerect",
    "skia-gm-strokerects",
    "skia-gm-thinstrokedrects",
}
TEXT_GLYPH_DEPENDENCY_GATES = {
    "skia-gm-shadertext3",
    "skia-gm-textblobtransforms",
}
TEXT_GLYPH_DEPENDENCY_GATE = {
    "linear": "FOR-308",
    "json": ROOT / "reports/wgsl-pipeline/scenes/artifacts/text-glyph-dependency-gate-for308/text-glyph-dependency-gate-for308.json",
    "report": ROOT / "reports/wgsl-pipeline/2026-06-04-for-308-text-glyph-dependency-gate.md",
}
RUNTIME_EFFECT_DESCRIPTOR_GATES = {
    "skia-gm-runtimeimagefilter": "runtime-effect.runtimeimagefilter.row-specific-artifacts-required",
    "skia-gm-runtimeintrinsics": "runtime-effect.runtimeintrinsics.row-specific-artifacts-required",
}

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
    text_glyph_dependency_gate = optional_json(TEXT_GLYPH_DEPENDENCY_GATE["json"])
    m89_feature_breadth = optional_json(M89_FEATURE_BREADTH)

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
        "textGlyphDependencyGate": text_glyph_dependency_gate,
        "m89FeatureBreadth": m89_feature_breadth,
        "rowSpecificRefusals": {
            row_id: {
                **metadata,
                "evidence": optional_json(metadata["json"]),
            }
            for row_id, metadata in ROW_SPECIFIC_REFUSALS.items()
        },
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


def row_specific_refusals(scene_id: str, fallback: str, indexes: dict[str, Any]) -> list[dict[str, Any]]:
    metadata = indexes["rowSpecificRefusals"].get(scene_id)
    if not isinstance(metadata, dict):
        return []
    evidence = metadata.get("evidence")
    if not isinstance(evidence, dict):
        raise AssertionError(f"{scene_id}: row-specific refusal evidence must be an object")
    row = evidence.get("row")
    if not isinstance(row, dict):
        raise AssertionError(f"{scene_id}: row-specific refusal evidence must contain row")
    if row.get("inventoryId") != scene_id:
        raise AssertionError(f"{scene_id}: row-specific refusal inventory mismatch")
    if row.get("status") != "expected-unsupported":
        raise AssertionError(f"{scene_id}: row-specific refusal must remain expected-unsupported")
    row_fallback = row.get("fallbackReason")
    if not isinstance(row_fallback, str) or row_fallback == "none":
        raise AssertionError(f"{scene_id}: row-specific refusal must have stable fallback")
    if evidence.get("classification") != "row-specific-expected-unsupported-no-support-claim":
        raise AssertionError(f"{scene_id}: row-specific refusal classification mismatch")
    if evidence.get("scoreImpact", {}).get("supportScoreIncreased") is not False:
        raise AssertionError(f"{scene_id}: row-specific refusal must not increase support score")

    return [
        {
            "linear": metadata["linear"],
            "classification": evidence.get("classification"),
            "json": rel(metadata["json"]),
            "report": rel(metadata["report"]),
            "fallbackReason": row_fallback,
            "registryFallbackReason": fallback,
            "referenceStatus": row.get("reference", {}).get("status", "unknown"),
            "cpuStatus": row.get("cpu", {}).get("status", "unknown"),
            "gpuStatus": row.get("gpu", {}).get("status", "unknown"),
            "diffStatsStatus": row.get("diffStats", {}).get("status", "unknown"),
            "supportScoreIncreased": evidence.get("scoreImpact", {}).get("supportScoreIncreased"),
        }
    ]


def dependency_gate_links(scene_id: str, fallback: str, indexes: dict[str, Any]) -> list[dict[str, Any]]:
    if scene_id in RUNTIME_EFFECT_DESCRIPTOR_GATES:
        if fallback != RUNTIME_EFFECT_DESCRIPTOR_GATES[scene_id]:
            raise AssertionError(f"{scene_id}: runtime-effect descriptor gate fallback mismatch")
        evidence = indexes["m89FeatureBreadth"]
        if not isinstance(evidence, dict):
            raise AssertionError(f"{scene_id}: M89 runtime-effect feature-breadth evidence must be an object")
        families = evidence.get("families")
        if not isinstance(families, list):
            raise AssertionError(f"{scene_id}: M89 feature-breadth evidence must contain families[]")
        runtime_family = next(
            (
                family
                for family in families
                if isinstance(family, dict)
                and family.get("issue") == "FOR-192"
                and family.get("family") == "Registered WGSL runtime effects"
            ),
            None,
        )
        if not isinstance(runtime_family, dict):
            raise AssertionError(f"{scene_id}: missing FOR-192 registered WGSL runtime-effects family")
        stable_refusals = set(runtime_family.get("stableRefusals", []))
        if "runtime-effect.arbitrary-sksl-unsupported" not in stable_refusals:
            raise AssertionError(f"{scene_id}: runtime-effect arbitrary SkSL refusal missing")
        if "runtime-effect.wgsl-descriptor-missing" not in stable_refusals:
            raise AssertionError(f"{scene_id}: runtime-effect missing WGSL descriptor refusal missing")
        non_claims = [str(item) for item in runtime_family.get("nonClaims", [])]
        if not any("No dynamic SkSL compiler" in item for item in non_claims):
            raise AssertionError(f"{scene_id}: runtime-effect no-SkSL-compiler non-claim missing")
        validation_rows = {
            row.get("id"): row
            for row in evidence.get("validationRows", [])
            if isinstance(row, dict)
        }
        validation = validation_rows.get("for-192-registered-wgsl-runtime-effect")
        if not isinstance(validation, dict) or validation.get("status") != "pass":
            raise AssertionError(f"{scene_id}: FOR-192 runtime-effect validation row must pass")

        return [
            {
                "linear": "FOR-192",
                "classification": "runtime-effect-descriptor-gated-expected-unsupported-no-support-claim",
                "json": rel(M89_FEATURE_BREADTH),
                "report": rel(M89_FEATURE_BREADTH_REPORT),
                "fallbackReason": fallback,
                "supportedDescriptor": "runtime.simple_rt",
                "requiredRefusals": sorted(stable_refusals),
                "implementationTarget": "WGSL",
                "skSLPolicy": "compatibility-refusal-only",
                "supportDecision": "KEEP_RUNTIME_EFFECT_DESCRIPTOR_GATED_UNTIL_ROW_SPECIFIC_KOTLIN_WGSL_PROOF",
                "supportScoreIncreased": False,
            }
        ]

    if scene_id not in TEXT_GLYPH_DEPENDENCY_GATES:
        return []
    evidence = indexes["textGlyphDependencyGate"]
    if not isinstance(evidence, dict):
        raise AssertionError(f"{scene_id}: text/glyph dependency gate evidence must be an object")
    if evidence.get("decision") != "TEXT_GLYPH_DEPENDENCY_GATE_APPLIED":
        raise AssertionError(f"{scene_id}: text/glyph dependency gate decision mismatch")
    if evidence.get("supportDecision") != "KEEP_TEXT_GLYPH_DEPENDENCY_GATED_UNTIL_REAL_DELIVERY":
        raise AssertionError(f"{scene_id}: text/glyph dependency support decision mismatch")
    forbidden = evidence.get("forbiddenSubstitutes")
    required = evidence.get("requiredFuturePromotionProof")
    if not isinstance(forbidden, list) or not forbidden:
        raise AssertionError(f"{scene_id}: text/glyph dependency gate needs forbidden substitutes")
    if not isinstance(required, list) or not required:
        raise AssertionError(f"{scene_id}: text/glyph dependency gate needs future proof requirements")

    return [
        {
            "linear": TEXT_GLYPH_DEPENDENCY_GATE["linear"],
            "classification": "dependency-gated-expected-unsupported-no-support-claim",
            "json": rel(TEXT_GLYPH_DEPENDENCY_GATE["json"]),
            "report": rel(TEXT_GLYPH_DEPENDENCY_GATE["report"]),
            "fallbackReason": fallback,
            "decision": evidence.get("decision"),
            "supportDecision": evidence.get("supportDecision"),
            "forbiddenSubstituteCount": len(forbidden),
            "requiredFuturePromotionProofCount": len(required),
            "supportScoreIncreased": False,
        }
    ]


def grouped_policy_refusals(source: str, scene: dict[str, Any], fallback: str) -> list[dict[str, Any]]:
    scene_id = str(scene.get("id") or "")
    if source != "d53-visibility" or scene_id not in D53_GROUPED_POLICY_REFUSALS:
        return []
    if scene.get("status") != "expected-unsupported":
        raise AssertionError(f"{scene_id}: grouped D53 refusal must remain expected-unsupported")
    if scene.get("policyOnlyArtifacts") is not True:
        raise AssertionError(f"{scene_id}: grouped D53 refusal must remain policy-only")
    if fallback == "none":
        raise AssertionError(f"{scene_id}: grouped D53 refusal must keep stable fallback")

    return [
        {
            "linear": scene.get("linearIssue", "FOR-461"),
            "classification": "grouped-policy-only-expected-unsupported-no-support-claim",
            "json": rel(GENERATED_DIR / "dash-hairline-stroke-gm-dashboard-visibility.json"),
            "report": str(scene.get("sourceReport") or "reports/upstream-rebaseline/2026-05-25-post-1047.md"),
            "fallbackReason": fallback,
            "sourceTask": scene.get("sourceTask", "pipelineDashHairlineStrokeDashboardVisibilityPack"),
            "policyArtifactDescription": scene.get("policyArtifactDescription", ""),
            "supportScoreIncreased": False,
        }
    ]


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
    scene_id = str(scene.get("id") or "")
    if scene_id in TEXT_GLYPH_DEPENDENCY_GATES:
        return "dependency"
    if scene_id in RUNTIME_EFFECT_DESCRIPTOR_GATES:
        return "dependency"
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
        "rowSpecificRefusals": row_specific_refusals(scene_id, fallback, indexes),
        "dependencyGateLinks": dependency_gate_links(scene_id, fallback, indexes),
        "groupedPolicyRefusals": grouped_policy_refusals(source, scene, fallback),
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
    row_specific_refusal_rows = sum(1 for row in rows if row["rowSpecificRefusals"])
    dependency_gate_link_rows = sum(1 for row in rows if row["dependencyGateLinks"])
    grouped_policy_refusal_rows = sum(1 for row in rows if row["groupedPolicyRefusals"])
    m88 = indexes["m88"]
    m88_counters = m88.get("dashboardCounters", {}) if isinstance(m88.get("dashboardCounters"), dict) else {}

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m89_gm_registry.py",
        "description": "M89 normalized GM support/refusal registry. This artifact changes no support claims.",
        "sourceInputs": input_paths
        + [rel(M66_PROMOTION), rel(M86_BURNDOWN), rel(M88_RC2)]
        + [rel(metadata["json"]) for metadata in ROW_SPECIFIC_REFUSALS.values()]
        + [rel(metadata["report"]) for metadata in ROW_SPECIFIC_REFUSALS.values()]
        + [
            rel(TEXT_GLYPH_DEPENDENCY_GATE["json"]),
            rel(TEXT_GLYPH_DEPENDENCY_GATE["report"]),
            rel(M89_FEATURE_BREADTH),
            rel(M89_FEATURE_BREADTH_REPORT),
        ],
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
            "rowSpecificRefusalRows": row_specific_refusal_rows,
            "dependencyGateLinkRows": dependency_gate_link_rows,
            "groupedPolicyRefusalRows": grouped_policy_refusal_rows,
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
        f"- Row-specific refusal links: `{counters['rowSpecificRefusalRows']}`",
        f"- Dependency gate links: `{counters['dependencyGateLinkRows']}`",
        f"- Grouped policy refusal links: `{counters['groupedPolicyRefusalRows']}`",
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

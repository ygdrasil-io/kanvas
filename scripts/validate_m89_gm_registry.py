#!/usr/bin/env python3
"""Validate the M89 Skia-like GM support/refusal registry."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
REPORT = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.md"
GENERATED_RESULTS = ROOT / "reports/wgsl-pipeline/scenes/generated/results.json"
SCENES_DIR = ROOT / "reports/wgsl-pipeline/scenes"
TEXT_GLYPH_DEPENDENCY_GATE_JSON = ROOT / "reports/wgsl-pipeline/scenes/artifacts/text-glyph-dependency-gate-for308/text-glyph-dependency-gate-for308.json"

EXPECTED_COUNTERS = {
    "totalRows": 47,
    "supportClaims": 22,
    "policyOnlyRows": 20,
    "rowSpecificRefusalRows": 7,
    "dependencyGateLinkRows": 4,
    "groupedPolicyRefusalRows": 9,
    "edgeBudgetGateLinkRows": 2,
    "imageFilterPrepassGateLinkRows": 1,
    "textGlyphDependencyGateLinkRows": 2,
    "unlinkedUnsupportedRows": 0,
    "expectedUnsupportedWithFallback": 25,
    "linkedM66Rows": 18,
    "linkedM86Rows": 18,
}
EXPECTED_ROW_SPECIFIC_REFUSALS = {
    "skia-gm-image": {
        "linear": "FOR-466",
        "fallbackReason": "image.imagegm.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md",
    },
    "skia-gm-imagesource": {
        "linear": "FOR-467",
        "fallbackReason": "image.imagesource.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md",
    },
    "skia-gm-offsetimagefilter": {
        "linear": "FOR-468",
        "fallbackReason": "image-filter.offset.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md",
    },
    "skia-gm-imagemakewithfilter": {
        "linear": "FOR-470",
        "fallbackReason": "image-filter.imagemakewithfilter.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-08-for-470-skia-gm-imagemakewithfilter-evidence.md",
    },
    "skia-gm-gradients2ptconical": {
        "linear": "FOR-472",
        "fallbackReason": "gradient.2ptconical.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for472-skia-gm-gradients2ptconical-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-08-for-472-skia-gm-gradients2ptconical-evidence.md",
    },
    "skia-gm-pathfill": {
        "linear": "FOR-469",
        "fallbackReason": "path-aa.fill.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-469-skia-gm-pathfill-evidence.md",
    },
    "skia-gm-rectpolystroke": {
        "linear": "FOR-471",
        "fallbackReason": "coverage.rectpolystroke.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for471-skia-gm-rectpolystroke-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-08-for-471-skia-gm-rectpolystroke-evidence.md",
    },
}
EXPECTED_GROUPED_POLICY_REFUSALS = {
    "skia-gm-dashcubics": "coverage.dash-cubic.row-specific-artifacts-required",
    "skia-gm-dashing": "coverage.dashing.row-specific-artifacts-required",
    "skia-gm-hairlines": "coverage.hairline.row-specific-artifacts-required",
    "skia-gm-hairmodes": "coverage.hairmode.row-specific-artifacts-required",
    "skia-gm-scaledstrokes": "coverage.scaled-stroke.row-specific-artifacts-required",
    "skia-gm-strokedlines": "coverage.stroked-lines.row-specific-artifacts-required",
    "skia-gm-strokerect": "coverage.stroke-rect.row-specific-artifacts-required",
    "skia-gm-strokerects": "coverage.stroke-rects.row-specific-artifacts-required",
    "skia-gm-thinstrokedrects": "coverage.thin-stroked-rects.row-specific-artifacts-required",
}
EXPECTED_DEPENDENCY_GATE_LINKS = {
    "skia-gm-shadertext3": "font.shadertext3.row-specific-artifacts-required",
    "skia-gm-textblobtransforms": "font.textblobtransforms.row-specific-artifacts-required",
}
EXPECTED_RUNTIME_EFFECT_DESCRIPTOR_GATES = {
    "skia-gm-runtimeimagefilter": "runtime-effect.runtimeimagefilter.row-specific-artifacts-required",
    "skia-gm-runtimeintrinsics": "runtime-effect.runtimeintrinsics.row-specific-artifacts-required",
}
EXPECTED_EDGE_BUDGET_GATES = {
    "path-aa-convexpaths-edge-budget": {
        "sourceScene": "ConvexPathsGM",
        "cpuRoute": "cpu.path-coverage.convexpaths-oracle",
    },
    "path-aa-dashing-edge-budget": {
        "sourceScene": "DashingGM",
        "cpuRoute": "cpu.path-coverage.dashing-oracle",
    },
}
EXPECTED_IMAGE_FILTER_PREPASS_GATES = {
    "image-filter-crop-nonnull-prepass-required": {
        "sourceShape": "Crop(input=nonNull)",
        "supportedSiblingScene": "crop-image-filter-nonnull-prepass",
        "cpuRoute": "cpu.image-filter.crop-nonnull-reference",
        "gpuCoverageStrategy": "webgpu.image-filter.refuse",
        "gpuPipelineKey": "imageFilter=Crop(input=nonNull),prePass=required,selectedM38Shape=false",
        "m66RefusalId": "m66-image-filter-crop-prepass-refusal",
        "m86RootCause": "filter.picture-prepass-required",
    },
}
EXPECTED_TEXT_GLYPH_DEPENDENCY_GATES = {
    "font-emoji-color-glyph-refusal": {
        "fallbackReason": "font.color-glyph-emoji-unsupported",
        "dependency": "color-font-emoji-rendering",
        "specificSpec": ".upstream/specs/font/05-color-fonts-emoji-and-fixtures.md",
        "cpuRoute": "cpu.text.refusal-oracle.emoji-color-glyph",
        "gpuRoute": "webgpu.text.refuse",
        "gpuCoverageStrategy": "webgpu.coverage.refuse",
        "gpuPipelineKey": "textRoute=emoji-color-glyph glyphRepresentation=unsupported",
        "shapingMode": "emoji-color-glyph",
    },
    "font-complex-shaping-refusal": {
        "fallbackReason": "font.complex-shaping-requires-explicit-shaper",
        "dependency": "explicit-shaper",
        "specificSpec": ".upstream/specs/font/03-shaping-and-layout-boundary.md",
        "cpuRoute": "cpu.text.refusal-oracle.complex-shaping",
        "gpuRoute": "webgpu.text.refuse",
        "gpuCoverageStrategy": "webgpu.coverage.refuse",
        "gpuPipelineKey": "textRoute=complex-shaping glyphRepresentation=unsupported",
        "shapingMode": "complex-shaping",
    },
}
EXPECTED_SOURCE_COUNTS = {
    "d50-visibility": 11,
    "d53-visibility": 9,
    "generated-dashboard": 27,
}
EXPECTED_STATUS_COUNTS = {
    "expected-unsupported": 25,
    "pass": 22,
}
EXPECTED_FAMILY_COUNTS = {
    "bitmap-image": 7,
    "blend-color": 2,
    "gradient": 4,
    "image-filter": 5,
    "path-aa": 18,
    "runtime-effect": 3,
    "text-glyph": 7,
    "transform-layer": 1,
}
POLICY_SOURCES = {"d50-visibility", "d53-visibility"}


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} root must be an object")
    return data


def generated_source_rows() -> dict[str, dict[str, Any]]:
    root = load_json(GENERATED_RESULTS)
    scenes = root.get("scenes")
    require(isinstance(scenes, list), "generated results scenes must be a list")
    rows: dict[str, dict[str, Any]] = {}
    for scene in scenes:
        if isinstance(scene, dict) and isinstance(scene.get("id"), str):
            rows[scene["id"]] = scene
    return rows


def route_diagnostic(source_scene: dict[str, Any], backend: str) -> tuple[str, dict[str, Any]]:
    diagnostics = source_scene.get("routeDiagnostics")
    require(isinstance(diagnostics, dict), f"{source_scene.get('id')}: missing routeDiagnostics")
    diagnostic = diagnostics.get(backend)
    require(isinstance(diagnostic, str) and diagnostic, f"{source_scene.get('id')}: missing {backend} route diagnostic")
    path = SCENES_DIR / diagnostic
    return rel(path), load_json(path)


def validate_registry() -> None:
    registry = load_json(REGISTRY)
    generated_rows = generated_source_rows()
    text_gate_evidence = load_json(TEXT_GLYPH_DEPENDENCY_GATE_JSON)
    require(registry.get("schemaVersion") == 1, "schemaVersion mismatch")
    require(registry.get("generatedBy") == "scripts/m89_gm_registry.py", "generatedBy mismatch")

    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    require(len(rows) == EXPECTED_COUNTERS["totalRows"], "registry row count mismatch")

    row_ids: list[str] = []
    status_counts: Counter[str] = Counter()
    source_counts: Counter[str] = Counter()
    family_counts: Counter[str] = Counter()
    support_claims = 0
    policy_only_rows = 0
    expected_unsupported_with_fallback = 0
    linked_m66_rows = 0
    linked_m86_rows = 0
    row_specific_refusal_rows = 0
    dependency_gate_link_rows = 0
    grouped_policy_refusal_rows = 0
    edge_budget_gate_link_rows = 0
    image_filter_prepass_gate_link_rows = 0
    text_glyph_dependency_gate_link_rows = 0
    unlinked_unsupported_rows = 0

    for index, row in enumerate(rows):
        require(isinstance(row, dict), f"rows[{index}] must be an object")
        row_id = row.get("rowId")
        require(isinstance(row_id, str) and row_id, f"rows[{index}] missing rowId")
        row_ids.append(row_id)

        source = row.get("source")
        status = row.get("status")
        family = row.get("family")
        fallback = row.get("fallbackReason")
        support_claim = row.get("supportClaim")
        policy_only = row.get("policyOnly")
        evidence_links = row.get("evidenceLinks")
        row_specific_refusals = row.get("rowSpecificRefusals")
        dependency_gate_links = row.get("dependencyGateLinks")
        grouped_policy_refusals = row.get("groupedPolicyRefusals")
        edge_budget_gate_links = row.get("edgeBudgetGateLinks")
        image_filter_prepass_gate_links = row.get("imageFilterPrepassGateLinks")
        text_glyph_dependency_gate_links = row.get("textGlyphDependencyGateLinks")

        require(isinstance(source, str) and source, f"{row_id}: missing source")
        require(isinstance(status, str) and status, f"{row_id}: missing status")
        require(isinstance(family, str) and family, f"{row_id}: missing family")
        require(isinstance(fallback, str) and fallback, f"{row_id}: missing fallbackReason")
        require(isinstance(support_claim, bool), f"{row_id}: supportClaim must be boolean")
        require(isinstance(policy_only, bool), f"{row_id}: policyOnly must be boolean")
        require(isinstance(evidence_links, dict), f"{row_id}: evidenceLinks must be object")
        require(isinstance(row_specific_refusals, list), f"{row_id}: rowSpecificRefusals must be list")
        require(isinstance(dependency_gate_links, list), f"{row_id}: dependencyGateLinks must be list")
        require(isinstance(grouped_policy_refusals, list), f"{row_id}: groupedPolicyRefusals must be list")
        require(isinstance(edge_budget_gate_links, list), f"{row_id}: edgeBudgetGateLinks must be list")
        require(isinstance(image_filter_prepass_gate_links, list), f"{row_id}: imageFilterPrepassGateLinks must be list")
        require(isinstance(text_glyph_dependency_gate_links, list), f"{row_id}: textGlyphDependencyGateLinks must be list")
        require(row.get("referenceKind") in {"skia-upstream", "skia-derived", "cpu-oracle", "test-oracle", "none"}, f"{row_id}: invalid referenceKind")
        require(row.get("nextTicketType") in {"implementation", "dependency", "fidelity-burndown", "policy-visibility", "performance-gate"}, f"{row_id}: invalid nextTicketType")
        require(row.get("pmImpact") in {"high", "medium", "low"}, f"{row_id}: invalid pmImpact")

        status_counts[status] += 1
        source_counts[source] += 1
        family_counts[family] += 1
        support_claims += int(support_claim)
        policy_only_rows += int(policy_only)
        expected_unsupported_with_fallback += int(status == "expected-unsupported" and fallback != "none")
        linked_m66_rows += int("m66" in evidence_links)
        linked_m86_rows += int("m86" in evidence_links)
        row_specific_refusal_rows += int(bool(row_specific_refusals))
        dependency_gate_link_rows += int(bool(dependency_gate_links))
        grouped_policy_refusal_rows += int(bool(grouped_policy_refusals))
        edge_budget_gate_link_rows += int(bool(edge_budget_gate_links))
        image_filter_prepass_gate_link_rows += int(bool(image_filter_prepass_gate_links))
        text_glyph_dependency_gate_link_rows += int(bool(text_glyph_dependency_gate_links))
        has_specialized_visibility_link = any(
            bool(links)
            for links in (
                row_specific_refusals,
                dependency_gate_links,
                grouped_policy_refusals,
                edge_budget_gate_links,
                image_filter_prepass_gate_links,
                text_glyph_dependency_gate_links,
            )
        )
        unlinked_unsupported_rows += int(status != "pass" and not has_specialized_visibility_link)

        if status == "pass":
            require(source == "generated-dashboard", f"{row_id}: only generated dashboard rows may be pass in M89")
            require(support_claim, f"{row_id}: pass row must claim support")
            require(fallback == "none", f"{row_id}: pass row must keep fallbackReason=none")
            require(row.get("routeGpu") == "pass", f"{row_id}: pass row must keep routeGpu=pass")
        if status == "expected-unsupported":
            require(not support_claim, f"{row_id}: expected-unsupported must not claim support")
            require(fallback != "none", f"{row_id}: expected-unsupported must have stable fallback")
            require(row.get("routeGpu") == "expected-unsupported", f"{row_id}: expected-unsupported must keep routeGpu expected-unsupported")
        if source in POLICY_SOURCES:
            require(policy_only, f"{row_id}: policy visibility row must be policyOnly")
            require(status == "expected-unsupported", f"{row_id}: policy visibility row must remain expected-unsupported")
            require(not support_claim, f"{row_id}: policy visibility row must not claim support")
            expected_next_ticket = "dependency" if dependency_gate_links else "policy-visibility"
            require(row.get("nextTicketType") == expected_next_ticket, f"{row_id}: policy visibility next action mismatch")

        expected_refusal = EXPECTED_ROW_SPECIFIC_REFUSALS.get(row_id)
        if expected_refusal is None:
            require(not row_specific_refusals, f"{row_id}: unexpected row-specific refusal link")
        else:
            require(len(row_specific_refusals) == 1, f"{row_id}: expected exactly one row-specific refusal link")
            refusal = row_specific_refusals[0]
            require(isinstance(refusal, dict), f"{row_id}: row-specific refusal link must be object")
            require(policy_only, f"{row_id}: row-specific refusal remains attached to policy visibility row")
            require(status == "expected-unsupported", f"{row_id}: row-specific refusal must remain expected-unsupported")
            require(not support_claim, f"{row_id}: row-specific refusal must not claim support")
            require(refusal.get("linear") == expected_refusal["linear"], f"{row_id}: row-specific refusal Linear mismatch")
            require(
                refusal.get("classification") == "row-specific-expected-unsupported-no-support-claim",
                f"{row_id}: row-specific refusal classification mismatch",
            )
            require(refusal.get("json") == expected_refusal["json"], f"{row_id}: row-specific refusal JSON path mismatch")
            require(refusal.get("report") == expected_refusal["report"], f"{row_id}: row-specific refusal report path mismatch")
            require((ROOT / expected_refusal["json"]).is_file(), f"{row_id}: row-specific refusal JSON file missing")
            require((ROOT / expected_refusal["report"]).is_file(), f"{row_id}: row-specific refusal report file missing")
            require(refusal.get("fallbackReason") == expected_refusal["fallbackReason"], f"{row_id}: row-specific refusal fallback mismatch")
            require(refusal.get("registryFallbackReason") == fallback, f"{row_id}: registry fallback link mismatch")
            require(refusal.get("referenceStatus") in {"not-generated", "available-historical-skia-integration-reference"}, f"{row_id}: reference status must not imply support")
            require(refusal.get("cpuStatus") in {"expected-unsupported", "available-historical-disabled-stress-test"}, f"{row_id}: CPU status must not imply support")
            require(refusal.get("gpuStatus") == "expected-unsupported", f"{row_id}: GPU status must remain expected-unsupported")
            require(refusal.get("diffStatsStatus") == "not-computed", f"{row_id}: diff/stat must remain not-computed")
            require(refusal.get("supportScoreIncreased") is False, f"{row_id}: row-specific refusal must not increase support score")

        expected_dependency_fallback = EXPECTED_DEPENDENCY_GATE_LINKS.get(row_id)
        expected_runtime_descriptor_fallback = EXPECTED_RUNTIME_EFFECT_DESCRIPTOR_GATES.get(row_id)
        if expected_dependency_fallback is None:
            if expected_runtime_descriptor_fallback is None:
                require(not dependency_gate_links, f"{row_id}: unexpected dependency gate link")
            else:
                require(len(dependency_gate_links) == 1, f"{row_id}: expected exactly one runtime-effect descriptor gate link")
                gate = dependency_gate_links[0]
                require(isinstance(gate, dict), f"{row_id}: runtime-effect descriptor gate link must be object")
                require(policy_only, f"{row_id}: runtime-effect descriptor gate remains attached to policy visibility row")
                require(family == "runtime-effect", f"{row_id}: runtime-effect descriptor gate must remain runtime-effect")
                require(status == "expected-unsupported", f"{row_id}: runtime-effect descriptor gate row must remain expected-unsupported")
                require(not support_claim, f"{row_id}: runtime-effect descriptor gate row must not claim support")
                require(row.get("nextTicketType") == "dependency", f"{row_id}: runtime-effect descriptor gate nextTicketType mismatch")
                require(gate.get("linear") == "FOR-192", f"{row_id}: runtime-effect descriptor gate Linear mismatch")
                require(
                    gate.get("classification") == "runtime-effect-descriptor-gated-expected-unsupported-no-support-claim",
                    f"{row_id}: runtime-effect descriptor gate classification mismatch",
                )
                require(
                    gate.get("json") == "reports/wgsl-pipeline/m89-feature-breadth/evidence.json",
                    f"{row_id}: runtime-effect descriptor gate JSON path mismatch",
                )
                require(
                    gate.get("report") == "reports/wgsl-pipeline/m89-feature-breadth/evidence.md",
                    f"{row_id}: runtime-effect descriptor gate report path mismatch",
                )
                require(gate.get("fallbackReason") == expected_runtime_descriptor_fallback, f"{row_id}: runtime-effect descriptor gate fallback mismatch")
                require(gate.get("supportedDescriptor") == "runtime.simple_rt", f"{row_id}: runtime-effect supported descriptor mismatch")
                required_refusals = set(gate.get("requiredRefusals", []))
                require(
                    {"runtime-effect.arbitrary-sksl-unsupported", "runtime-effect.wgsl-descriptor-missing"} <= required_refusals,
                    f"{row_id}: runtime-effect descriptor gate must preserve stable refusals",
                )
                require(gate.get("implementationTarget") == "WGSL", f"{row_id}: runtime-effect descriptor gate implementation target mismatch")
                require(gate.get("skSLPolicy") == "compatibility-refusal-only", f"{row_id}: runtime-effect descriptor gate SkSL policy mismatch")
                require(
                    gate.get("supportDecision") == "KEEP_RUNTIME_EFFECT_DESCRIPTOR_GATED_UNTIL_ROW_SPECIFIC_KOTLIN_WGSL_PROOF",
                    f"{row_id}: runtime-effect descriptor gate support decision mismatch",
                )
                require(gate.get("supportScoreIncreased") is False, f"{row_id}: runtime-effect descriptor gate must not increase support score")
        else:
            require(len(dependency_gate_links) == 1, f"{row_id}: expected exactly one dependency gate link")
            gate = dependency_gate_links[0]
            require(isinstance(gate, dict), f"{row_id}: dependency gate link must be object")
            require(policy_only, f"{row_id}: dependency gate remains attached to policy visibility row")
            require(family == "text-glyph", f"{row_id}: dependency gate must remain text-glyph")
            require(status == "expected-unsupported", f"{row_id}: dependency gate row must remain expected-unsupported")
            require(not support_claim, f"{row_id}: dependency gate row must not claim support")
            require(row.get("nextTicketType") == "dependency", f"{row_id}: dependency gate nextTicketType mismatch")
            require(gate.get("linear") == "FOR-308", f"{row_id}: dependency gate Linear mismatch")
            require(
                gate.get("classification") == "dependency-gated-expected-unsupported-no-support-claim",
                f"{row_id}: dependency gate classification mismatch",
            )
            require(
                gate.get("json")
                == "reports/wgsl-pipeline/scenes/artifacts/text-glyph-dependency-gate-for308/text-glyph-dependency-gate-for308.json",
                f"{row_id}: dependency gate JSON path mismatch",
            )
            require(
                gate.get("report") == "reports/wgsl-pipeline/2026-06-04-for-308-text-glyph-dependency-gate.md",
                f"{row_id}: dependency gate report path mismatch",
            )
            require(gate.get("fallbackReason") == expected_dependency_fallback, f"{row_id}: dependency gate fallback mismatch")
            require(gate.get("decision") == "TEXT_GLYPH_DEPENDENCY_GATE_APPLIED", f"{row_id}: dependency gate decision mismatch")
            require(
                gate.get("supportDecision") == "KEEP_TEXT_GLYPH_DEPENDENCY_GATED_UNTIL_REAL_DELIVERY",
                f"{row_id}: dependency gate support decision mismatch",
            )
            require(isinstance(gate.get("forbiddenSubstituteCount"), int) and gate.get("forbiddenSubstituteCount") >= 6, f"{row_id}: dependency gate forbidden substitute count mismatch")
            require(
                isinstance(gate.get("requiredFuturePromotionProofCount"), int)
                and gate.get("requiredFuturePromotionProofCount") >= 10,
                f"{row_id}: dependency gate future proof count mismatch",
            )
            require(gate.get("supportScoreIncreased") is False, f"{row_id}: dependency gate must not increase support score")

        expected_grouped_fallback = EXPECTED_GROUPED_POLICY_REFUSALS.get(row_id)
        if expected_grouped_fallback is None:
            require(not grouped_policy_refusals, f"{row_id}: unexpected grouped policy refusal link")
        else:
            require(len(grouped_policy_refusals) == 1, f"{row_id}: expected exactly one grouped policy refusal link")
            grouped = grouped_policy_refusals[0]
            require(isinstance(grouped, dict), f"{row_id}: grouped policy refusal link must be object")
            require(source == "d53-visibility", f"{row_id}: grouped policy refusal must stay D53 visibility")
            require(policy_only, f"{row_id}: grouped policy refusal remains policy-only")
            require(status == "expected-unsupported", f"{row_id}: grouped policy refusal must remain expected-unsupported")
            require(not support_claim, f"{row_id}: grouped policy refusal must not claim support")
            require(grouped.get("linear") == "FOR-461", f"{row_id}: grouped policy refusal Linear mismatch")
            require(
                grouped.get("classification") == "grouped-policy-only-expected-unsupported-no-support-claim",
                f"{row_id}: grouped policy refusal classification mismatch",
            )
            require(
                grouped.get("json") == "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
                f"{row_id}: grouped policy refusal JSON path mismatch",
            )
            require(grouped.get("report") == "reports/upstream-rebaseline/2026-05-25-post-1047.md", f"{row_id}: grouped policy refusal report mismatch")
            require(grouped.get("fallbackReason") == expected_grouped_fallback, f"{row_id}: grouped policy refusal fallback mismatch")
            require(
                grouped.get("sourceTask") == "pipelineDashHairlineStrokeDashboardVisibilityPack",
                f"{row_id}: grouped policy refusal source task mismatch",
            )
            require("Policy-only expected-unsupported" in grouped.get("policyArtifactDescription", ""), f"{row_id}: grouped policy refusal policy artifact description mismatch")
            require(grouped.get("supportScoreIncreased") is False, f"{row_id}: grouped policy refusal must not increase support score")

        if str(fallback).startswith("font.") or row_id in {"skia-gm-shadertext3", "skia-gm-textblobtransforms"}:
            require(family == "text-glyph", f"{row_id}: font/text rows must remain text-glyph")

        if "m66" in evidence_links:
            m66_links = evidence_links["m66"]
            require(isinstance(m66_links, list) and m66_links, f"{row_id}: m66 evidence link must be non-empty list")
            for link in m66_links:
                require(link.get("status") in {"pass", "expected-unsupported"}, f"{row_id}: invalid m66 link status")
                if link.get("status") == "expected-unsupported":
                    require(link.get("fallbackReason") != "none", f"{row_id}: m66 expected-unsupported link needs fallback")
        if "m86" in evidence_links:
            m86_links = evidence_links["m86"]
            require(isinstance(m86_links, list) and m86_links, f"{row_id}: m86 evidence link must be non-empty list")
            for link in m86_links:
                require(isinstance(link.get("rootCause"), str) and link.get("rootCause"), f"{row_id}: m86 link needs rootCause")
                require(link.get("risk") in {"high", "medium", "dependency-gated"}, f"{row_id}: invalid m86 risk")

        expected_edge_budget_gate = EXPECTED_EDGE_BUDGET_GATES.get(row_id)
        if expected_edge_budget_gate is None:
            require(not edge_budget_gate_links, f"{row_id}: unexpected edge-budget gate link")
        else:
            require(len(edge_budget_gate_links) == 1, f"{row_id}: expected exactly one edge-budget gate link")
            gate = edge_budget_gate_links[0]
            require(isinstance(gate, dict), f"{row_id}: edge-budget gate link must be object")
            require(source == "generated-dashboard", f"{row_id}: edge-budget gate must remain generated-dashboard")
            require(family == "path-aa", f"{row_id}: edge-budget gate must remain path-aa")
            require(status == "expected-unsupported", f"{row_id}: edge-budget gate row must remain expected-unsupported")
            require(not support_claim, f"{row_id}: edge-budget gate row must not claim support")
            require(policy_only is False, f"{row_id}: edge-budget generated row must not become policy-only")
            require(row.get("nextTicketType") == "implementation", f"{row_id}: edge-budget nextTicketType mismatch")
            require(fallback == "coverage.edge-count-exceeded", f"{row_id}: edge-budget fallback mismatch")
            require(gate.get("linear") == "GRA-284", f"{row_id}: edge-budget Linear mismatch")
            require(
                gate.get("classification") == "edge-budget-gated-expected-unsupported-no-support-claim",
                f"{row_id}: edge-budget classification mismatch",
            )
            require(gate.get("fallbackReason") == "coverage.edge-count-exceeded", f"{row_id}: edge-budget link fallback mismatch")
            require(gate.get("sourceScene") == expected_edge_budget_gate["sourceScene"], f"{row_id}: edge-budget source scene mismatch")
            require(gate.get("edgeBudget") == 256, f"{row_id}: edge-budget value mismatch")
            require(gate.get("spec") == ".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md", f"{row_id}: edge-budget spec mismatch")
            require(gate.get("adr") == ".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md", f"{row_id}: edge-budget ADR mismatch")
            require(gate.get("policyReport") == "reports/wgsl-pipeline/2026-05-31-m47-path-aa-expected-unsupported-policy-validation.md", f"{row_id}: edge-budget policy report mismatch")
            require(gate.get("evidenceReport") == "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md", f"{row_id}: edge-budget evidence report mismatch")
            require(gate.get("cpuRoute") == expected_edge_budget_gate["cpuRoute"], f"{row_id}: edge-budget CPU route mismatch")
            require(gate.get("gpuRoute") == "webgpu.coverage.refuse.edge-count", f"{row_id}: edge-budget GPU route mismatch")
            source_scene = generated_rows.get(row_id)
            require(isinstance(source_scene, dict), f"{row_id}: generated source row missing")
            source_cpu = source_scene.get("cpu")
            source_gpu = source_scene.get("gpu")
            require(isinstance(source_cpu, dict), f"{row_id}: generated CPU diagnostics missing")
            require(isinstance(source_gpu, dict), f"{row_id}: generated GPU diagnostics missing")
            source_cpu_route = source_cpu.get("route")
            source_gpu_route = source_gpu.get("route")
            require(isinstance(source_cpu_route, dict), f"{row_id}: generated CPU route missing")
            require(isinstance(source_gpu_route, dict), f"{row_id}: generated GPU route missing")
            require(
                gate.get("cpuRoute") == source_cpu_route.get("selectedRoute"),
                f"{row_id}: edge-budget CPU route differs from source dashboard",
            )
            require(source_gpu.get("status") == "expected-unsupported", f"{row_id}: generated GPU status mismatch")
            require(
                source_gpu_route.get("fallbackReason") == "coverage.edge-count-exceeded",
                f"{row_id}: generated GPU fallback mismatch",
            )
            require(
                gate.get("gpuCoverageStrategy") == source_gpu_route.get("coverageStrategy") == "webgpu.coverage.refuse",
                f"{row_id}: edge-budget GPU coverage strategy differs from source dashboard",
            )
            source_pipeline_key = source_gpu_route.get("pipelineKey")
            require(isinstance(source_pipeline_key, str), f"{row_id}: generated GPU pipeline key missing")
            require(gate.get("gpuPipelineKey") == source_pipeline_key, f"{row_id}: edge-budget GPU pipeline key mismatch")
            require(
                f"source={expected_edge_budget_gate['sourceScene']}" in source_pipeline_key,
                f"{row_id}: edge-budget GPU pipeline source mismatch",
            )
            require(gate.get("globalEdgeBudgetIncreased") is False, f"{row_id}: edge-budget must not increase global budget")
            require(gate.get("smokeCandidateAllowed") is False, f"{row_id}: edge-budget must not enter required smoke")
            require(gate.get("supportScoreIncreased") is False, f"{row_id}: edge-budget gate must not increase support score")
            for linked_path in ("spec", "adr", "policyReport", "evidenceReport"):
                require((ROOT / gate[linked_path]).is_file(), f"{row_id}: edge-budget {linked_path} file missing")

        expected_prepass_gate = EXPECTED_IMAGE_FILTER_PREPASS_GATES.get(row_id)
        if expected_prepass_gate is None:
            require(not image_filter_prepass_gate_links, f"{row_id}: unexpected image-filter prepass gate link")
        else:
            require(len(image_filter_prepass_gate_links) == 1, f"{row_id}: expected exactly one image-filter prepass gate link")
            gate = image_filter_prepass_gate_links[0]
            require(isinstance(gate, dict), f"{row_id}: image-filter prepass gate link must be object")
            require(source == "generated-dashboard", f"{row_id}: image-filter prepass gate must remain generated-dashboard")
            require(family == "image-filter", f"{row_id}: image-filter prepass gate must remain image-filter")
            require(status == "expected-unsupported", f"{row_id}: image-filter prepass gate row must remain expected-unsupported")
            require(not support_claim, f"{row_id}: image-filter prepass gate row must not claim support")
            require(policy_only is False, f"{row_id}: image-filter prepass generated row must not become policy-only")
            require(row.get("nextTicketType") == "implementation", f"{row_id}: image-filter prepass nextTicketType mismatch")
            require(
                fallback == "image-filter.crop-input-nonnull-prepass-required",
                f"{row_id}: image-filter prepass fallback mismatch",
            )
            require(gate.get("linear") == "GRA-284", f"{row_id}: image-filter prepass Linear mismatch")
            require(
                gate.get("classification") == "image-filter-prepass-gated-expected-unsupported-no-support-claim",
                f"{row_id}: image-filter prepass classification mismatch",
            )
            require(gate.get("fallbackReason") == fallback, f"{row_id}: image-filter prepass link fallback mismatch")
            require(gate.get("sourceShape") == expected_prepass_gate["sourceShape"], f"{row_id}: image-filter source shape mismatch")
            require(
                gate.get("supportedSiblingScene") == expected_prepass_gate["supportedSiblingScene"],
                f"{row_id}: image-filter supported sibling mismatch",
            )
            require(gate.get("spec") == ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md", f"{row_id}: image-filter spec mismatch")
            require(
                gate.get("implementationReport") == "reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-implementation.md",
                f"{row_id}: image-filter implementation report mismatch",
            )
            require(
                gate.get("policyReport") == "reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md",
                f"{row_id}: image-filter policy report mismatch",
            )
            require(
                gate.get("generatedEvidenceReport") == "reports/wgsl-pipeline/2026-05-28-m41-crop-image-filter-generated-evidence.md",
                f"{row_id}: image-filter generated evidence report mismatch",
            )
            require(
                gate.get("evidenceReport") == "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md",
                f"{row_id}: image-filter M48 evidence report mismatch",
            )
            require(
                gate.get("routeCpuDiagnostic")
                == "reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-cpu.json",
                f"{row_id}: image-filter CPU route diagnostic path mismatch",
            )
            require(
                gate.get("routeGpuDiagnostic")
                == "reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-gpu.json",
                f"{row_id}: image-filter GPU route diagnostic path mismatch",
            )
            require(gate.get("cpuRoute") == expected_prepass_gate["cpuRoute"], f"{row_id}: image-filter CPU route mismatch")
            require(
                gate.get("gpuRoute") == "webgpu.image-filter.refuse.prepass-required",
                f"{row_id}: image-filter GPU route mismatch",
            )
            source_scene = generated_rows.get(row_id)
            require(isinstance(source_scene, dict), f"{row_id}: generated source row missing")
            source_cpu = source_scene.get("cpu")
            source_gpu = source_scene.get("gpu")
            require(isinstance(source_cpu, dict), f"{row_id}: generated CPU diagnostics missing")
            require(isinstance(source_gpu, dict), f"{row_id}: generated GPU diagnostics missing")
            source_cpu_route = source_cpu.get("route")
            source_gpu_route = source_gpu.get("route")
            require(isinstance(source_cpu_route, dict), f"{row_id}: generated CPU route missing")
            require(isinstance(source_gpu_route, dict), f"{row_id}: generated GPU route missing")
            require(
                gate.get("cpuRoute") == source_cpu_route.get("selectedRoute"),
                f"{row_id}: image-filter CPU route differs from source dashboard",
            )
            require(source_gpu.get("status") == "expected-unsupported", f"{row_id}: generated GPU status mismatch")
            require(source_gpu_route.get("fallbackReason") == fallback, f"{row_id}: generated GPU fallback mismatch")
            require(
                gate.get("gpuCoverageStrategy")
                == source_gpu_route.get("coverageStrategy")
                == expected_prepass_gate["gpuCoverageStrategy"],
                f"{row_id}: image-filter GPU strategy differs from source dashboard",
            )
            require(
                gate.get("gpuPipelineKey") == source_gpu_route.get("pipelineKey") == expected_prepass_gate["gpuPipelineKey"],
                f"{row_id}: image-filter GPU pipeline key mismatch",
            )
            cpu_diagnostic_path, cpu_diagnostic = route_diagnostic(source_scene, "cpu")
            gpu_diagnostic_path, gpu_diagnostic = route_diagnostic(source_scene, "gpu")
            require(gate.get("routeCpuDiagnostic") == cpu_diagnostic_path, f"{row_id}: image-filter CPU diagnostic path differs from source dashboard")
            require(gate.get("routeGpuDiagnostic") == gpu_diagnostic_path, f"{row_id}: image-filter GPU diagnostic path differs from source dashboard")
            require(cpu_diagnostic.get("sceneId") == row_id, f"{row_id}: CPU diagnostic sceneId mismatch")
            require(cpu_diagnostic.get("status") == source_cpu.get("status") == "pass", f"{row_id}: CPU diagnostic status mismatch")
            require(
                cpu_diagnostic.get("selectedRoute") == source_cpu_route.get("selectedRoute") == gate.get("cpuRoute"),
                f"{row_id}: CPU diagnostic selected route mismatch",
            )
            require(
                cpu_diagnostic.get("fallbackReason") == source_cpu_route.get("fallbackReason") == "none",
                f"{row_id}: CPU diagnostic fallback mismatch",
            )
            require(gpu_diagnostic.get("sceneId") == row_id, f"{row_id}: GPU diagnostic sceneId mismatch")
            require(
                gpu_diagnostic.get("status") == source_gpu.get("status") == "expected-unsupported",
                f"{row_id}: GPU diagnostic status mismatch",
            )
            require(
                gpu_diagnostic.get("coverageStrategy") == source_gpu_route.get("coverageStrategy") == gate.get("gpuCoverageStrategy"),
                f"{row_id}: GPU diagnostic strategy mismatch",
            )
            require(
                gpu_diagnostic.get("pipelineKey") == source_gpu_route.get("pipelineKey") == gate.get("gpuPipelineKey"),
                f"{row_id}: GPU diagnostic pipeline key mismatch",
            )
            require(
                gpu_diagnostic.get("fallbackReason") == source_gpu_route.get("fallbackReason") == gate.get("fallbackReason"),
                f"{row_id}: GPU diagnostic fallback mismatch",
            )
            m66_links = evidence_links.get("m66", [])
            m86_links = evidence_links.get("m86", [])
            require(
                any(
                    isinstance(link, dict)
                    and link.get("id") == expected_prepass_gate["m66RefusalId"]
                    and link.get("status") == "expected-unsupported"
                    and link.get("fallbackReason") == fallback
                    for link in m66_links
                ),
                f"{row_id}: image-filter M66 refusal link mismatch",
            )
            require(
                any(
                    isinstance(link, dict)
                    and link.get("id") == expected_prepass_gate["m66RefusalId"]
                    and link.get("rootCause") == expected_prepass_gate["m86RootCause"]
                    and link.get("risk") == "high"
                    and link.get("fidelityScoreEligible") is False
                    for link in m86_links
                ),
                f"{row_id}: image-filter M86 root cause link mismatch",
            )
            require(gate.get("m66RefusalId") == expected_prepass_gate["m66RefusalId"], f"{row_id}: image-filter M66 id mismatch")
            require(gate.get("m86RootCause") == expected_prepass_gate["m86RootCause"], f"{row_id}: image-filter M86 root cause mismatch")
            require(gate.get("generalDagCompilerAdded") is False, f"{row_id}: image-filter must not add general DAG compiler")
            require(gate.get("cpuReadbackFallbackAdded") is False, f"{row_id}: image-filter must not add CPU/readback fallback")
            require(gate.get("requiredSmokeCandidateAllowed") is False, f"{row_id}: image-filter must not enter required smoke")
            require(gate.get("supportScoreIncreased") is False, f"{row_id}: image-filter prepass gate must not increase support score")
            for linked_path in (
                "spec",
                "implementationReport",
                "policyReport",
                "generatedEvidenceReport",
                "evidenceReport",
                "routeCpuDiagnostic",
                "routeGpuDiagnostic",
            ):
                require((ROOT / gate[linked_path]).is_file(), f"{row_id}: image-filter {linked_path} file missing")

        expected_text_gate = EXPECTED_TEXT_GLYPH_DEPENDENCY_GATES.get(row_id)
        if expected_text_gate is None:
            require(not text_glyph_dependency_gate_links, f"{row_id}: unexpected text/glyph dependency gate link")
        else:
            require(len(text_glyph_dependency_gate_links) == 1, f"{row_id}: expected exactly one text/glyph dependency gate link")
            gate = text_glyph_dependency_gate_links[0]
            require(isinstance(gate, dict), f"{row_id}: text/glyph dependency gate link must be object")
            require(source == "generated-dashboard", f"{row_id}: text/glyph dependency gate must remain generated-dashboard")
            require(family == "text-glyph", f"{row_id}: text/glyph dependency gate must remain text-glyph")
            require(status == "expected-unsupported", f"{row_id}: text/glyph dependency gate row must remain expected-unsupported")
            require(not support_claim, f"{row_id}: text/glyph dependency gate row must not claim support")
            require(policy_only is False, f"{row_id}: text/glyph generated row must not become policy-only")
            require(row.get("nextTicketType") == "dependency", f"{row_id}: text/glyph nextTicketType mismatch")
            require(fallback == expected_text_gate["fallbackReason"], f"{row_id}: text/glyph fallback mismatch")
            require(gate.get("linear") == "FOR-308", f"{row_id}: text/glyph Linear mismatch")
            require(
                gate.get("classification") == "text-glyph-dependency-gated-generated-expected-unsupported-no-support-claim",
                f"{row_id}: text/glyph classification mismatch",
            )
            require(gate.get("fallbackReason") == fallback, f"{row_id}: text/glyph link fallback mismatch")
            require(gate.get("dependency") == expected_text_gate["dependency"], f"{row_id}: text/glyph dependency mismatch")
            require(
                gate.get("json")
                == "reports/wgsl-pipeline/scenes/artifacts/text-glyph-dependency-gate-for308/text-glyph-dependency-gate-for308.json",
                f"{row_id}: text/glyph JSON path mismatch",
            )
            require(
                gate.get("report") == "reports/wgsl-pipeline/2026-06-04-for-308-text-glyph-dependency-gate.md",
                f"{row_id}: text/glyph report path mismatch",
            )
            require(gate.get("fontSpec") == ".upstream/specs/font/README.md", f"{row_id}: text/glyph font spec mismatch")
            require(gate.get("specificSpec") == expected_text_gate["specificSpec"], f"{row_id}: text/glyph specific spec mismatch")
            require(gate.get("validationSpec") == ".upstream/specs/font/06-validation-and-conformance.md", f"{row_id}: text/glyph validation spec mismatch")
            require(gate.get("cpuRoute") == expected_text_gate["cpuRoute"], f"{row_id}: text/glyph CPU route mismatch")
            require(gate.get("gpuRoute") == expected_text_gate["gpuRoute"], f"{row_id}: text/glyph GPU route mismatch")
            require(gate.get("gpuCoverageStrategy") == expected_text_gate["gpuCoverageStrategy"], f"{row_id}: text/glyph GPU strategy mismatch")
            require(gate.get("gpuPipelineKey") == expected_text_gate["gpuPipelineKey"], f"{row_id}: text/glyph GPU pipeline mismatch")
            require(gate.get("shapingMode") == expected_text_gate["shapingMode"], f"{row_id}: text/glyph shaping mode mismatch")
            require(
                gate.get("supportDecision") == "KEEP_TEXT_GLYPH_DEPENDENCY_GATED_UNTIL_REAL_DELIVERY",
                f"{row_id}: text/glyph support decision mismatch",
            )
            require(isinstance(gate.get("forbiddenSubstituteCount"), int) and gate.get("forbiddenSubstituteCount") >= 6, f"{row_id}: text/glyph forbidden substitute count mismatch")
            require(
                isinstance(gate.get("requiredFuturePromotionProofCount"), int)
                and gate.get("requiredFuturePromotionProofCount") >= 10,
                f"{row_id}: text/glyph future proof count mismatch",
            )
            require(gate.get("nativeFallbackAdded") is False, f"{row_id}: text/glyph must not add native fallback")
            require(gate.get("fontSubstituteAdded") is False, f"{row_id}: text/glyph must not add font substitute")
            require(gate.get("supportScoreIncreased") is False, f"{row_id}: text/glyph gate must not increase support score")

            preserved_refusals = text_gate_evidence.get("preservedRefusals")
            require(isinstance(preserved_refusals, list), f"{row_id}: FOR-308 preservedRefusals missing")
            preserved = [
                item
                for item in preserved_refusals
                if isinstance(item, dict) and item.get("id") == row_id
            ]
            require(len(preserved) == 1, f"{row_id}: FOR-308 preserved refusal mismatch")
            preserved_row = preserved[0]
            require(preserved_row.get("status") == "expected-unsupported", f"{row_id}: FOR-308 status mismatch")
            require(preserved_row.get("route") == expected_text_gate["gpuRoute"], f"{row_id}: FOR-308 route mismatch")
            require(preserved_row.get("pipelineKey") == expected_text_gate["gpuPipelineKey"], f"{row_id}: FOR-308 pipeline mismatch")
            require(preserved_row.get("fallbackReason") == fallback, f"{row_id}: FOR-308 fallback mismatch")
            require(
                text_gate_evidence.get("decision") == "TEXT_GLYPH_DEPENDENCY_GATE_APPLIED",
                f"{row_id}: FOR-308 decision mismatch",
            )
            require(
                text_gate_evidence.get("supportDecision") == "KEEP_TEXT_GLYPH_DEPENDENCY_GATED_UNTIL_REAL_DELIVERY",
                f"{row_id}: FOR-308 support decision mismatch",
            )

            source_scene = generated_rows.get(row_id)
            require(isinstance(source_scene, dict), f"{row_id}: generated source row missing")
            source_cpu = source_scene.get("cpu")
            source_gpu = source_scene.get("gpu")
            require(isinstance(source_cpu, dict), f"{row_id}: generated CPU diagnostics missing")
            require(isinstance(source_gpu, dict), f"{row_id}: generated GPU diagnostics missing")
            source_cpu_route = source_cpu.get("route")
            source_gpu_route = source_gpu.get("route")
            require(isinstance(source_cpu_route, dict), f"{row_id}: generated CPU route missing")
            require(isinstance(source_gpu_route, dict), f"{row_id}: generated GPU route missing")
            require(source_cpu.get("status") == "pass", f"{row_id}: generated CPU status mismatch")
            require(source_gpu.get("status") == "expected-unsupported", f"{row_id}: generated GPU status mismatch")
            require(source_cpu_route.get("selectedRoute") == gate.get("cpuRoute"), f"{row_id}: generated CPU route mismatch")
            require(source_cpu_route.get("fallbackReason") == "none", f"{row_id}: generated CPU fallback mismatch")
            require(source_gpu_route.get("selectedRoute") == gate.get("gpuRoute"), f"{row_id}: generated GPU route mismatch")
            require(source_gpu_route.get("coverageStrategy") == gate.get("gpuCoverageStrategy"), f"{row_id}: generated GPU strategy mismatch")
            require(source_gpu_route.get("pipelineKey") == gate.get("gpuPipelineKey"), f"{row_id}: generated GPU pipeline mismatch")
            require(source_gpu_route.get("fallbackReason") == fallback, f"{row_id}: generated GPU fallback mismatch")

            cpu_diagnostic_path, cpu_diagnostic = route_diagnostic(source_scene, "cpu")
            gpu_diagnostic_path, gpu_diagnostic = route_diagnostic(source_scene, "gpu")
            require(gate.get("routeCpuDiagnostic") == cpu_diagnostic_path, f"{row_id}: text/glyph CPU diagnostic path differs from source dashboard")
            require(gate.get("routeGpuDiagnostic") == gpu_diagnostic_path, f"{row_id}: text/glyph GPU diagnostic path differs from source dashboard")
            require(cpu_diagnostic.get("status") == source_cpu.get("status") == "pass", f"{row_id}: CPU diagnostic status mismatch")
            require(cpu_diagnostic.get("selectedRoute") == source_cpu_route.get("selectedRoute") == gate.get("cpuRoute"), f"{row_id}: CPU diagnostic route mismatch")
            require(cpu_diagnostic.get("fallbackReason") == source_cpu_route.get("fallbackReason") == "none", f"{row_id}: CPU diagnostic fallback mismatch")
            require(cpu_diagnostic.get("shapingMode") == gate.get("shapingMode"), f"{row_id}: CPU diagnostic shaping mode mismatch")
            require(gpu_diagnostic.get("status") == source_gpu.get("status") == "expected-unsupported", f"{row_id}: GPU diagnostic status mismatch")
            require(gpu_diagnostic.get("selectedRoute") == source_gpu_route.get("selectedRoute") == gate.get("gpuRoute"), f"{row_id}: GPU diagnostic route mismatch")
            require(gpu_diagnostic.get("coverageStrategy") == source_gpu_route.get("coverageStrategy") == gate.get("gpuCoverageStrategy"), f"{row_id}: GPU diagnostic strategy mismatch")
            require(gpu_diagnostic.get("pipelineKey") == source_gpu_route.get("pipelineKey") == gate.get("gpuPipelineKey"), f"{row_id}: GPU diagnostic pipeline mismatch")
            require(gpu_diagnostic.get("fallbackReason") == source_gpu_route.get("fallbackReason") == gate.get("fallbackReason"), f"{row_id}: GPU diagnostic fallback mismatch")
            require(gpu_diagnostic.get("shapingMode") == gate.get("shapingMode"), f"{row_id}: GPU diagnostic shaping mode mismatch")

            for linked_path in ("json", "report", "fontSpec", "specificSpec", "validationSpec", "routeCpuDiagnostic", "routeGpuDiagnostic"):
                require((ROOT / gate[linked_path]).is_file(), f"{row_id}: text/glyph {linked_path} file missing")

    duplicates = sorted(row_id for row_id, count in Counter(row_ids).items() if count > 1)
    require(not duplicates, f"duplicate row ids: {duplicates}")

    counters = registry.get("counters")
    require(isinstance(counters, dict), "missing counters object")
    for key, expected in EXPECTED_COUNTERS.items():
        require(counters.get(key) == expected, f"counter {key} mismatch: {counters.get(key)} != {expected}")
    require(counters.get("status") == EXPECTED_STATUS_COUNTS, "status counters mismatch")
    require(counters.get("source") == EXPECTED_SOURCE_COUNTS, "source counters mismatch")
    require(counters.get("family") == EXPECTED_FAMILY_COUNTS, "family counters mismatch")
    require(dict(status_counts) == EXPECTED_STATUS_COUNTS, "derived status counters mismatch")
    require(dict(source_counts) == EXPECTED_SOURCE_COUNTS, "derived source counters mismatch")
    require(dict(family_counts) == EXPECTED_FAMILY_COUNTS, "derived family counters mismatch")
    require(support_claims == EXPECTED_COUNTERS["supportClaims"], "derived support claim count mismatch")
    require(policy_only_rows == EXPECTED_COUNTERS["policyOnlyRows"], "derived policy-only count mismatch")
    require(row_specific_refusal_rows == EXPECTED_COUNTERS["rowSpecificRefusalRows"], "derived row-specific refusal count mismatch")
    require(dependency_gate_link_rows == EXPECTED_COUNTERS["dependencyGateLinkRows"], "derived dependency gate link count mismatch")
    require(grouped_policy_refusal_rows == EXPECTED_COUNTERS["groupedPolicyRefusalRows"], "derived grouped policy refusal count mismatch")
    require(edge_budget_gate_link_rows == EXPECTED_COUNTERS["edgeBudgetGateLinkRows"], "derived edge-budget gate link count mismatch")
    require(
        image_filter_prepass_gate_link_rows == EXPECTED_COUNTERS["imageFilterPrepassGateLinkRows"],
        "derived image-filter prepass gate link count mismatch",
    )
    require(
        text_glyph_dependency_gate_link_rows == EXPECTED_COUNTERS["textGlyphDependencyGateLinkRows"],
        "derived text/glyph dependency gate link count mismatch",
    )
    require(
        unlinked_unsupported_rows == EXPECTED_COUNTERS["unlinkedUnsupportedRows"],
        "derived unlinked unsupported row count mismatch",
    )
    require(
        expected_unsupported_with_fallback == EXPECTED_COUNTERS["expectedUnsupportedWithFallback"],
        "derived expected-unsupported fallback count mismatch",
    )
    require(linked_m66_rows == EXPECTED_COUNTERS["linkedM66Rows"], "derived M66 link count mismatch")
    require(linked_m86_rows == EXPECTED_COUNTERS["linkedM86Rows"], "derived M86 link count mismatch")

    evidence_packages = registry.get("evidencePackages")
    require(isinstance(evidence_packages, dict), "missing evidencePackages object")
    require(evidence_packages.get("m66", {}).get("selectedRows") == 19, "M66 selectedRows mismatch")
    require(evidence_packages.get("m66", {}).get("rejectedRows") == 4, "M66 rejectedRows mismatch")
    require(evidence_packages.get("m66", {}).get("linkedRegistryRows") == 18, "M66 linked rows mismatch")
    require(evidence_packages.get("m86", {}).get("rankedCandidates") == 19, "M86 rankedCandidates mismatch")
    require(evidence_packages.get("m86", {}).get("classifiedRows") == 7, "M86 classifiedRows mismatch")
    require(evidence_packages.get("m86", {}).get("skiaComparableSupportRows") == 6, "M86 Skia-comparable support rows mismatch")
    require(evidence_packages.get("m86", {}).get("globalThresholdWeakened") is False, "M86 must preserve globalThresholdWeakened=false")
    m88 = evidence_packages.get("m88", {})
    require(m88.get("status") == "pass", "M88 status mismatch")
    require(m88.get("passRows") == 21, "M88 passRows must stay frozen")
    require(m88.get("expectedUnsupportedRows") == 5, "M88 expectedUnsupportedRows must stay frozen")
    require(m88.get("failRows") == 0, "M88 failRows must stay zero")
    require(m88.get("trackedGapRows") == 0, "M88 trackedGapRows must stay zero")
    require(
        set(m88.get("categories", [])) == {"supported", "expected-unsupported", "dependency-gated", "implementation-gap", "reporting-only"},
        "M88 categories mismatch",
    )

    non_claims = registry.get("nonClaims")
    require(isinstance(non_claims, list) and non_claims, "nonClaims must be non-empty")
    require(
        any("Policy-only visibility rows do not count as support" in str(item) for item in non_claims),
        "registry must preserve policy-only non-claim",
    )
    require(
        any("WGSL remains the WebGPU shader target" in str(item) for item in non_claims),
        "registry must preserve WGSL/SkSL non-claim",
    )

    source_inputs = registry.get("sourceInputs")
    require(isinstance(source_inputs, list) and source_inputs, "sourceInputs must be a non-empty list")
    for source_input in source_inputs:
        require(isinstance(source_input, str) and source_input, "sourceInputs entries must be non-empty strings")
        require((ROOT / source_input).is_file(), f"sourceInputs entry does not exist: {source_input}")


def validate_report() -> None:
    require(REPORT.is_file(), f"missing markdown report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for marker in (
        "Total rows: `47`",
        "Support claims: `22`",
        "Policy-only rows: `20`",
        "Row-specific refusal links: `7`",
        "Dependency gate links: `4`",
        "Grouped policy refusal links: `9`",
        "Edge-budget gate links: `2`",
        "Image-filter prepass gate links: `1`",
        "Text/glyph dependency gate links: `2`",
        "Unlinked unsupported rows: `0`",
        "`expected-unsupported`: `25`",
        "`pass`: `22`",
        "Linked M66 rows: `18`",
        "Linked M86 rows: `18`",
        "does not promote support",
        "WGSL remains the WebGPU shader target",
    ):
        require(marker in text, f"registry.md missing marker: {marker}")


def main() -> int:
    try:
        validate_registry()
        validate_report()
    except AssertionError as error:
        print(f"validate_m89_gm_registry: FAIL: {error}", file=sys.stderr)
        return 1
    print("validate_m89_gm_registry: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

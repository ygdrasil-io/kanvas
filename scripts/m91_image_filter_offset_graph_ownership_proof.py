#!/usr/bin/env python3
"""Generate M91 OffsetImageFilterGM graph and ownership proof artifacts."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
CANDIDATE_READINESS_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-candidate-readiness/summary.json"
D51_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json"
GM_SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter"
GRAPH_JSON = ARTIFACT_DIR / "graph.json"
OWNERSHIP_JSON = ARTIFACT_DIR / "intermediate-ownership.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-graph-ownership-proof"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-offsetimagefilter"
SOURCE_GM = "OffsetImageFilterGM"
FALLBACK = "image-filter.offset.row-specific-artifacts-required"
D51_FALLBACK = "image-filter.offset.crop-prepass-scaled-clipped-webgpu-artifacts-required"
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "policyOnlyPromoted": False,
    "thresholdChanged": False,
    "dashboardPromoted": False,
    "belowThresholdCountedAsProductionGap": False,
    "requiredSmokeCandidateAllowed": False,
    "generalImageFilterDagSupport": False,
    "genericImageFilterDagCompiler": False,
    "cropImageFilterDagSupport": False,
    "picturePrepassSupport": False,
    "arbitraryLayerPrepass": False,
    "cpuReadbackFallbackAdded": False,
    "adjacentSimpleOffsetEvidenceInherited": False,
    "fallbackReasonNoneClaimed": False,
    "renderArtifactsAdded": False,
    "diffStatsAdded": False,
    "performanceGatePromoted": False,
    "ganeshPort": False,
    "graphitePort": False,
    "dynamicSkSLCompiler": False,
    "dynamicSkSLIR": False,
    "dynamicSkSLVM": False,
}


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


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def registry_row(registry: dict[str, Any]) -> dict[str, Any]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(row, dict), f"missing registry row: {ROW_ID}")
    require(row.get("status") == "expected-unsupported", "registry status changed")
    require(row.get("supportClaim") is False, "registry supportClaim changed")
    require(row.get("policyOnly") is True, "registry policyOnly changed")
    require(row.get("fallbackReason") == FALLBACK, "registry fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", "registry CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", "registry GPU route changed")
    return row


def require_readiness(readiness: dict[str, Any]) -> None:
    require(readiness.get("classification") == "image-filter-candidate-readiness-no-new-rendering-support", "readiness classification changed")
    ticket = readiness.get("nextRecommendedTicket")
    require(isinstance(ticket, dict), "readiness missing nextRecommendedTicket")
    require(ticket.get("id") == "M91-IF-3A", "active next ticket changed")
    require(ticket.get("rowId") == ROW_ID, "active next row changed")
    require(ticket.get("supportClaimAllowed") is False, "M91-IF-3A must not allow support claims")
    candidates = readiness.get("candidates")
    require(isinstance(candidates, list), "readiness candidates must be a list")
    candidate = next((item for item in candidates if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(candidate, dict), f"readiness missing candidate {ROW_ID}")
    require(candidate.get("readyForPromotion") is False, "candidate must not be ready for promotion")
    require(candidate.get("supportClaim") is False, "candidate supportClaim changed")


def source_cells_from_d51(d51: dict[str, Any]) -> list[dict[str, Any]]:
    require(d51.get("inventoryId") == ROW_ID, "D51 inventory id changed")
    require(d51.get("status") == "expected-unsupported", "D51 status changed")
    require(d51.get("scoreImpact", {}).get("supportScoreIncreased") is False, "D51 support score changed")
    source = d51.get("rowSpecificAudit", {}).get("offsetImageFilterGmSource")
    require(isinstance(source, dict), "D51 source description missing")
    require(source.get("gmName") == "offsetimagefilter", "D51 GM name changed")
    require(source.get("gmDimensions") == {"width": 600, "height": 100}, "D51 dimensions changed")
    cells = source.get("cells")
    require(isinstance(cells, list) and len(cells) == 5, "D51 must describe five cells")
    return cells


def require_source_contract() -> None:
    source = GM_SOURCE.read_text(encoding="utf-8")
    for phrase in [
        'override fun getName(): String = "offsetimagefilter"',
        "override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)",
        "SkImageFilters.Image(image, SkSamplingOptions.nearest())",
        "SkImageFilters.Offset(dx, dy, tileInput)",
        "SkImageFilters.Crop(",
        "SkTileMode.kDecal",
        "SkImageFilters.Offset(-5f, -10f, null)",
        "drawClippedImage(c, bitmap, paint, 2f, cropRect)",
    ]:
        require(phrase in source, f"OffsetImageFilterGM source missing phrase: {phrase}")


def build_graph(cells: list[dict[str, Any]]) -> dict[str, Any]:
    nodes: list[dict[str, Any]] = []
    edges: list[dict[str, Any]] = []
    for cell in cells:
        index = cell["cell"]
        has_tile_input = index != 4
        source_id = f"cell-{index}-source"
        image_id = f"cell-{index}-image-filter"
        offset_id = f"cell-{index}-offset"
        crop_id = f"cell-{index}-crop"
        draw_id = f"cell-{index}-draw"
        nodes.extend(
            [
                {
                    "id": source_id,
                    "kind": "source-image" if has_tile_input else "null-input",
                    "source": cell.get("source"),
                    "scale": cell.get("scale"),
                },
                {
                    "id": image_id,
                    "kind": "SkImageFilters.Image",
                    "sampling": "nearest",
                    "enabled": has_tile_input,
                },
                {
                    "id": offset_id,
                    "kind": "SkImageFilters.Offset",
                    "dx": cell["offset"]["dx"],
                    "dy": cell["offset"]["dy"],
                },
                {
                    "id": crop_id,
                    "kind": "SkImageFilters.Crop",
                    "cropRect": cell["cropRect"],
                    "tileMode": "kDecal",
                },
                {
                    "id": draw_id,
                    "kind": "drawClippedImage",
                    "clip": "image bounds",
                    "debugOverlay": "red 2-pixel stroke on clip/crop intersection",
                    "scale": cell.get("scale"),
                },
            ]
        )
        if has_tile_input:
            edges.append({"from": source_id, "to": image_id, "relationship": "image source wrapped as filter input"})
            edges.append({"from": image_id, "to": offset_id, "relationship": "offset input"})
        else:
            edges.append({"from": source_id, "to": offset_id, "relationship": "null offset input"})
        edges.append({"from": offset_id, "to": crop_id, "relationship": "crop child"})
        edges.append({"from": crop_id, "to": draw_id, "relationship": "paint.imageFilter"})
    return {
        "schemaVersion": 1,
        "milestone": "M91",
        "ticket": "M91-IF-3A-REF",
        "classification": "image-filter-offset-graph-dump-no-new-rendering-support",
        "rowId": ROW_ID,
        "sourceGm": SOURCE_GM,
        "status": "expected-unsupported",
        "supportClaim": False,
        "fallbackReason": FALLBACK,
        "d51FallbackReason": D51_FALLBACK,
        "gmDimensions": {"width": 600, "height": 100},
        "graph": {
            "cells": len(cells),
            "nodes": nodes,
            "edges": edges,
        },
        "source": {
            "kotlin": rel(GM_SOURCE),
            "d51Evidence": rel(D51_JSON),
        },
        "nonClaims": NON_CLAIMS,
    }


def build_ownership() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "milestone": "M91",
        "ticket": "M91-IF-3A-REF",
        "classification": "image-filter-offset-intermediate-ownership-no-new-rendering-support",
        "rowId": ROW_ID,
        "sourceGm": SOURCE_GM,
        "status": "expected-unsupported",
        "supportClaim": False,
        "fallbackReason": FALLBACK,
        "ownershipStatus": "requirements-only",
        "requiredIntermediateOwnership": [
            {
                "name": "cell-local image-filter intermediate",
                "scope": "per OffsetImageFilterGM cell",
                "owner": "future bounded WebGPU image-filter route",
                "lifetime": "created for the row-specific render pass and released with command submission",
                "requiredBeforePromotion": True,
            },
            {
                "name": "scaled 2x cell crop/clip intermediate",
                "scope": "fifth OffsetImageFilterGM cell",
                "owner": "future bounded WebGPU image-filter route",
                "lifetime": "bounded to the row-specific render pass",
                "requiredBeforePromotion": True,
            },
        ],
        "currentImplementation": {
            "webgpuRoute": "expected-unsupported",
            "fallbackReasonNone": False,
            "renderArtifactsAdded": False,
            "diffStatsAdded": False,
            "performanceEvidenceAdded": False,
        },
        "generalDagCompilerAdded": False,
        "cpuReadbackFallbackAdded": False,
        "adjacentSimpleOffsetEvidenceInherited": False,
        "nonClaims": NON_CLAIMS,
    }


def validate_graph(graph: dict[str, Any]) -> None:
    require(graph.get("classification") == "image-filter-offset-graph-dump-no-new-rendering-support", "graph classification mismatch")
    require(graph.get("rowId") == ROW_ID, "graph rowId mismatch")
    require(graph.get("supportClaim") is False, "graph supportClaim changed")
    require(graph.get("status") == "expected-unsupported", "graph status changed")
    require(graph.get("fallbackReason") == FALLBACK, "graph fallback changed")
    body = graph.get("graph")
    require(isinstance(body, dict), "graph body must be an object")
    nodes = body.get("nodes")
    edges = body.get("edges")
    require(isinstance(nodes, list) and len(nodes) == 25, "graph must contain 25 nodes")
    require(isinstance(edges, list) and len(edges) == 19, "graph must contain 19 edges")
    require(graph.get("nonClaims") == NON_CLAIMS, "graph nonClaims changed")


def validate_ownership(ownership: dict[str, Any]) -> None:
    require(ownership.get("classification") == "image-filter-offset-intermediate-ownership-no-new-rendering-support", "ownership classification mismatch")
    require(ownership.get("rowId") == ROW_ID, "ownership rowId mismatch")
    require(ownership.get("supportClaim") is False, "ownership supportClaim changed")
    require(ownership.get("status") == "expected-unsupported", "ownership status changed")
    require(ownership.get("fallbackReason") == FALLBACK, "ownership fallback changed")
    require(ownership.get("generalDagCompilerAdded") is False, "ownership must not add general DAG compiler")
    require(ownership.get("cpuReadbackFallbackAdded") is False, "ownership must not add CPU/readback fallback")
    require(ownership.get("adjacentSimpleOffsetEvidenceInherited") is False, "ownership must not inherit SimpleOffset evidence")
    required = ownership.get("requiredIntermediateOwnership")
    require(isinstance(required, list) and len(required) == 2, "ownership must name two required intermediate scopes")
    require(ownership.get("nonClaims") == NON_CLAIMS, "ownership nonClaims changed")


def build_summary(graph: dict[str, Any], ownership: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_offset_graph_ownership_proof.py",
        "milestone": "M91",
        "ticket": "M91-IF-3A-REF",
        "classification": "image-filter-offset-graph-ownership-proof-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "candidateReadiness": rel(CANDIDATE_READINESS_JSON),
            "d51Evidence": rel(D51_JSON),
            "gmSource": rel(GM_SOURCE),
        },
        "outputs": {
            "graph": rel(GRAPH_JSON),
            "intermediateOwnership": rel(OWNERSHIP_JSON),
        },
        "counters": {
            "graphArtifacts": 1,
            "ownershipArtifacts": 1,
            "renderArtifactsAdded": 0,
            "diffStatsAdded": 0,
            "performanceArtifactsAdded": 0,
            "fallbackReasonNoneRoutesAdded": 0,
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "row": {
            "rowId": ROW_ID,
            "sourceGm": SOURCE_GM,
            "status": "expected-unsupported",
            "supportClaim": False,
            "fallbackReason": FALLBACK,
        },
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_offset_graph_ownership_proof.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetGraphOwnershipProof",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetEvidenceIntake",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    lines = [
        "# M91 OffsetImageFilterGM Graph Ownership Proof",
        "",
        "Status: generated evidence",
        "",
        "This report adds the row-specific graph dump and intermediate-ownership requirement artifacts for `skia-gm-offsetimagefilter`. It does not add render artifacts, diff/stat payloads, performance evidence, fallbackReason=none routes, or a support claim.",
        "",
        "## Outputs",
        "",
        f"- Graph: `{summary['outputs']['graph']}`",
        f"- Intermediate ownership: `{summary['outputs']['intermediateOwnership']}`",
        "",
        "## Counters",
        "",
        f"- Graph artifacts: `{counters['graphArtifacts']}`",
        f"- Ownership artifacts: `{counters['ownershipArtifacts']}`",
        f"- Render artifacts added: `{counters['renderArtifactsAdded']}`",
        f"- Diff/stat added: `{counters['diffStatsAdded']}`",
        f"- Performance artifacts added: `{counters['performanceArtifactsAdded']}`",
        f"- fallbackReason=none routes added: `{counters['fallbackReasonNoneRoutesAdded']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        f"- Dashboard promotions: `{counters['dashboardPromotions']}`",
        f"- Threshold changes: `{counters['thresholdChanges']}`",
        "",
        "## Support Guard",
        "",
    ]
    lines.extend(f"- {key}: `{value}`" for key, value in summary["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == "image-filter-offset-graph-ownership-proof-no-new-rendering-support", "summary classification mismatch")
    counters = summary.get("counters")
    require(isinstance(counters, dict), "summary counters must be an object")
    require(counters.get("graphArtifacts") == 1, "graph artifact count changed")
    require(counters.get("ownershipArtifacts") == 1, "ownership artifact count changed")
    require(counters.get("renderArtifactsAdded") == 0, "must not add render artifacts")
    require(counters.get("diffStatsAdded") == 0, "must not add diff/stat")
    require(counters.get("performanceArtifactsAdded") == 0, "must not add performance artifacts")
    require(counters.get("fallbackReasonNoneRoutesAdded") == 0, "must not add fallbackReason=none routes")
    require(counters.get("newSupportClaims") == 0, "must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "must not promote dashboard")
    require(counters.get("thresholdChanges") == 0, "must not change thresholds")
    require(summary.get("supportGuard") == NON_CLAIMS, "supportGuard changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD, GRAPH_JSON, OWNERSHIP_JSON}
    actual_files = {path for path in [SUMMARY_JSON, SUMMARY_MD, GRAPH_JSON, OWNERSHIP_JSON] if path.is_file()}
    require(actual_files == expected_files, f"missing generated files: {[rel(path) for path in sorted(expected_files - actual_files)]}")


def main() -> int:
    try:
        registry = load_json(REGISTRY_JSON)
        readiness = load_json(CANDIDATE_READINESS_JSON)
        d51 = load_json(D51_JSON)
        registry_row(registry)
        require_readiness(readiness)
        require_source_contract()
        cells = source_cells_from_d51(d51)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        graph = build_graph(cells)
        ownership = build_ownership()
        validate_graph(graph)
        validate_ownership(ownership)
        write_json(GRAPH_JSON, graph)
        write_json(OWNERSHIP_JSON, ownership)
        summary = build_summary(graph, ownership)
        write_json(SUMMARY_JSON, summary)
        SUMMARY_MD.write_text(render_markdown(summary), encoding="utf-8")
        reloaded = load_json(SUMMARY_JSON)
        validate_summary(reloaded)
        validate_graph(load_json(GRAPH_JSON))
        validate_ownership(load_json(OWNERSHIP_JSON))
    except AssertionError as error:
        print(f"m91_image_filter_offset_graph_ownership_proof: FAIL: {error}", file=sys.stderr)
        return 1
    print("M91 OffsetImageFilterGM graph ownership proof validation passed: graphArtifacts=1 ownershipArtifacts=1 newSupportClaims=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

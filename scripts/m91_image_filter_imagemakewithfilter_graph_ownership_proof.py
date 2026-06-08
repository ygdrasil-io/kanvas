#!/usr/bin/env python3
"""Generate M91 ImageMakeWithFilterGM graph and ownership proof artifacts."""

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
FOR470_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json"
GM_SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageMakeWithFilterGM.kt"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter"
GRAPH_JSON = ARTIFACT_DIR / "graph.json"
OWNERSHIP_JSON = ARTIFACT_DIR / "intermediate-ownership.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-graph-ownership-proof"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-imagemakewithfilter"
SOURCE_GM = "ImageMakeWithFilterGM"
FALLBACK = "image-filter.imagemakewithfilter.row-specific-artifacts-required"
CLASSIFICATION = "image-filter-imagemakewithfilter-graph-ownership-proof-no-new-rendering-support"
GRAPH_CLASSIFICATION = "image-filter-imagemakewithfilter-graph-dump-no-new-rendering-support"
OWNERSHIP_CLASSIFICATION = "image-filter-imagemakewithfilter-intermediate-ownership-no-new-rendering-support"
FILTER_COLUMNS = [
    "color",
    "blur",
    "drop-shadow",
    "offset",
    "dilate",
    "erode",
    "displacement",
    "arithmetic",
    "blend",
    "convolution",
    "matrix-xform",
    "lighting",
    "tile",
]
CLIP_ROWS = [
    "clip-bound-row-0",
    "clip-bound-row-1",
    "clip-bound-row-2",
    "clip-bound-row-3",
    "clip-bound-row-4",
    "clip-bound-row-5",
]
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "policyOnlyPromoted": False,
    "thresholdChanged": False,
    "dashboardPromoted": False,
    "belowThresholdCountedAsProductionGap": False,
    "imagemakewithfilterEvidenceInherited": False,
    "boundedM61M89EvidenceInherited": False,
    "makeWithFilterExecuted": False,
    "broadImageFilterDAGSupport": False,
    "genericImageFilterDagCompiler": False,
    "cropPrepassSupport": False,
    "picturePrepassSupport": False,
    "layerPrepassSupport": False,
    "cpuReadbackFallbackAdded": False,
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
    payload = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(payload, dict), f"{rel(path)} root must be an object")
    return payload


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def validate_registry(registry: dict[str, Any]) -> None:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(row, dict), f"missing registry row: {ROW_ID}")
    require(row.get("family") == "image-filter", "registry family changed")
    require(row.get("status") == "expected-unsupported", "registry status changed")
    require(row.get("supportClaim") is False, "registry supportClaim changed")
    require(row.get("policyOnly") is True, "registry policyOnly changed")
    require(row.get("fallbackReason") == FALLBACK, "registry fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", "registry CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", "registry GPU route changed")


def validate_candidate_readiness(readiness: dict[str, Any]) -> None:
    require(readiness.get("classification") == "image-filter-candidate-readiness-no-new-rendering-support", "readiness classification changed")
    candidates = readiness.get("candidates")
    require(isinstance(candidates, list), "readiness candidates must be a list")
    candidate = next((item for item in candidates if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(candidate, dict), f"readiness missing candidate {ROW_ID}")
    require(candidate.get("sourceGm") == SOURCE_GM, "candidate sourceGm changed")
    require(candidate.get("candidateKind") == "image-make-with-filter-gm", "candidate kind changed")
    require(candidate.get("priority") == 2, "candidate priority changed")
    require(candidate.get("promotionTicket") == "M91-IF-3B", "candidate promotion ticket changed")
    require(candidate.get("readyForPromotion") is False, "candidate must not be ready")
    require(candidate.get("supportClaim") is False, "candidate supportClaim changed")
    require(candidate.get("policyOnly") is True, "candidate policyOnly changed")
    require(candidate.get("status") == "expected-unsupported", "candidate status changed")
    require(candidate.get("fallbackReason") == FALLBACK, "candidate fallback changed")


def validate_for470(payload: dict[str, Any]) -> None:
    require(payload.get("classification") == "row-specific-expected-unsupported-no-support-claim", "FOR-470 classification changed")
    require(payload.get("linear") == "FOR-470", "FOR-470 linear id changed")
    row = payload.get("row")
    require(isinstance(row, dict), "FOR-470 row must be an object")
    require(row.get("inventoryId") == ROW_ID, "FOR-470 row id changed")
    require(row.get("status") == "expected-unsupported", "FOR-470 status changed")
    require(row.get("fallbackReason") == FALLBACK, "FOR-470 fallback changed")
    provenance = row.get("imageMakeWithFilterProvenance")
    require(isinstance(provenance, dict), "FOR-470 provenance must be present")
    require(provenance.get("scene") == SOURCE_GM, "FOR-470 provenance scene changed")
    require(provenance.get("kotlinSource") == rel(GM_SOURCE), "FOR-470 Kotlin source changed")
    require(provenance.get("upstreamSource") == "gm/imagemakewithfilter.cpp", "FOR-470 upstream source changed")
    require(payload.get("scoreImpact", {}).get("supportScoreIncreased") is False, "FOR-470 support score changed")


def validate_source_contract() -> None:
    source = GM_SOURCE.read_text(encoding="utf-8")
    for phrase in [
        "public class ImageMakeWithFilterGM",
        'override fun getName(): String = "imagemakewithfilter"',
        "override fun getISize(): SkISize = SkISize.Make(1840, 860)",
        "13 filter factories",
        "6 clip-bound rows",
        "without actually running",
        "for (row in 0 until 6)",
        "for (col in 0 until 13)",
    ]:
        require(phrase in source, f"ImageMakeWithFilterGM source missing phrase: {phrase}")


def build_graph() -> dict[str, Any]:
    nodes: list[dict[str, Any]] = []
    edges: list[dict[str, Any]] = []
    for row_index, clip_row in enumerate(CLIP_ROWS):
        for col_index, filter_name in enumerate(FILTER_COLUMNS):
            prefix = f"r{row_index}-c{col_index}-{filter_name}"
            source_id = f"{prefix}-source"
            filter_id = f"{prefix}-make-with-filter"
            output_id = f"{prefix}-output-cell"
            nodes.extend(
                [
                    {
                        "id": source_id,
                        "kind": "source-image",
                        "asset": "images/mandrill_128.png",
                        "resizedTo": {"width": 100, "height": 100},
                        "currentPortDrawsGhostOnly": True,
                    },
                    {
                        "id": filter_id,
                        "kind": "SkImages.MakeWithFilter",
                        "filterColumn": filter_name,
                        "clipRow": clip_row,
                        "executedInCurrentPort": False,
                        "requiredBeforePromotion": True,
                    },
                    {
                        "id": output_id,
                        "kind": "drawImage output cell",
                        "row": row_index,
                        "column": col_index,
                        "requiresReferenceCapture": True,
                    },
                ]
            )
            edges.append({"from": source_id, "to": filter_id, "relationship": "source image passed to MakeWithFilter"})
            edges.append({"from": filter_id, "to": output_id, "relationship": "filtered output composed into GM grid cell"})
    return {
        "schemaVersion": 1,
        "milestone": "M91",
        "ticket": "M91-IF-3B-GRAPH",
        "classification": GRAPH_CLASSIFICATION,
        "rowId": ROW_ID,
        "sourceGm": SOURCE_GM,
        "status": "expected-unsupported",
        "supportClaim": False,
        "fallbackReason": FALLBACK,
        "gmDimensions": {"width": 1840, "height": 860},
        "grid": {
            "rows": len(CLIP_ROWS),
            "columns": len(FILTER_COLUMNS),
            "cells": len(CLIP_ROWS) * len(FILTER_COLUMNS),
            "filterColumns": FILTER_COLUMNS,
            "clipRows": CLIP_ROWS,
        },
        "graph": {
            "nodes": nodes,
            "edges": edges,
        },
        "currentPort": {
            "rendersScaffoldingOnly": True,
            "makeWithFilterExecuted": False,
            "reason": "The Kotlin GM currently draws faded mandrill backgrounds and headers only; row-specific filter output artifacts remain absent.",
        },
        "source": {
            "kotlin": rel(GM_SOURCE),
            "for470Evidence": rel(FOR470_JSON),
        },
        "nonClaims": NON_CLAIMS,
    }


def build_ownership() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "milestone": "M91",
        "ticket": "M91-IF-3B-GRAPH",
        "classification": OWNERSHIP_CLASSIFICATION,
        "rowId": ROW_ID,
        "sourceGm": SOURCE_GM,
        "status": "expected-unsupported",
        "supportClaim": False,
        "fallbackReason": FALLBACK,
        "ownershipStatus": "requirements-only",
        "grid": {
            "rows": len(CLIP_ROWS),
            "columns": len(FILTER_COLUMNS),
            "cells": len(CLIP_ROWS) * len(FILTER_COLUMNS),
        },
        "requiredIntermediateOwnership": [
            {
                "name": "per-cell source image input",
                "scope": "each ImageMakeWithFilterGM grid cell",
                "owner": "future bounded CPU/WebGPU image-filter route",
                "lifetime": "valid for the row-specific filter evaluation only",
                "requiredBeforePromotion": True,
            },
            {
                "name": "per-cell MakeWithFilter output",
                "scope": "13 filter columns x 6 clip-bound rows",
                "owner": "future bounded WebGPU image-filter route",
                "lifetime": "created for the selected row-specific render pass and released with command submission",
                "requiredBeforePromotion": True,
            },
            {
                "name": "clip/output bounds metadata",
                "scope": "all ImageMakeWithFilterGM clip rows",
                "owner": "future graph planner",
                "lifetime": "serialized with route diagnostics and diff/stat evidence",
                "requiredBeforePromotion": True,
            },
            {
                "name": "filter-family scratch textures",
                "scope": "color, blur, drop-shadow, offset, dilate, erode, displacement, arithmetic, blend, convolution, matrix-xform, lighting, tile",
                "owner": "future bounded WebGPU image-filter route",
                "lifetime": "bounded to each row-specific filter pass; no long-lived cache ownership is claimed",
                "requiredBeforePromotion": True,
            },
        ],
        "currentImplementation": {
            "webgpuRoute": "expected-unsupported",
            "cpuRoute": "expected-unsupported",
            "fallbackReasonNone": False,
            "makeWithFilterExecuted": False,
            "renderArtifactsAdded": False,
            "diffStatsAdded": False,
            "performanceEvidenceAdded": False,
        },
        "generalDagCompilerAdded": False,
        "cpuReadbackFallbackAdded": False,
        "boundedM61M89EvidenceInherited": False,
        "nonClaims": NON_CLAIMS,
    }


def validate_graph(graph: dict[str, Any]) -> None:
    require(graph.get("classification") == GRAPH_CLASSIFICATION, "graph classification mismatch")
    require(graph.get("rowId") == ROW_ID, "graph rowId mismatch")
    require(graph.get("sourceGm") == SOURCE_GM, "graph sourceGm mismatch")
    require(graph.get("supportClaim") is False, "graph supportClaim changed")
    require(graph.get("status") == "expected-unsupported", "graph status changed")
    require(graph.get("fallbackReason") == FALLBACK, "graph fallback changed")
    grid = graph.get("grid")
    require(isinstance(grid, dict), "graph grid must be an object")
    require(grid.get("rows") == 6, "graph row count changed")
    require(grid.get("columns") == 13, "graph column count changed")
    require(grid.get("cells") == 78, "graph cell count changed")
    body = graph.get("graph")
    require(isinstance(body, dict), "graph body must be an object")
    nodes = body.get("nodes")
    edges = body.get("edges")
    require(isinstance(nodes, list) and len(nodes) == 234, "graph must contain 234 nodes")
    require(isinstance(edges, list) and len(edges) == 156, "graph must contain 156 edges")
    current = graph.get("currentPort")
    require(isinstance(current, dict), "currentPort must be an object")
    require(current.get("makeWithFilterExecuted") is False, "graph must not claim MakeWithFilter execution")
    require(graph.get("nonClaims") == NON_CLAIMS, "graph nonClaims changed")


def validate_ownership(ownership: dict[str, Any]) -> None:
    require(ownership.get("classification") == OWNERSHIP_CLASSIFICATION, "ownership classification mismatch")
    require(ownership.get("rowId") == ROW_ID, "ownership rowId mismatch")
    require(ownership.get("sourceGm") == SOURCE_GM, "ownership sourceGm mismatch")
    require(ownership.get("supportClaim") is False, "ownership supportClaim changed")
    require(ownership.get("status") == "expected-unsupported", "ownership status changed")
    require(ownership.get("fallbackReason") == FALLBACK, "ownership fallback changed")
    require(ownership.get("ownershipStatus") == "requirements-only", "ownership status must remain requirements-only")
    required = ownership.get("requiredIntermediateOwnership")
    require(isinstance(required, list) and len(required) == 4, "ownership must name four required scopes")
    require(ownership.get("generalDagCompilerAdded") is False, "ownership must not add general DAG compiler")
    require(ownership.get("cpuReadbackFallbackAdded") is False, "ownership must not add CPU/readback fallback")
    require(ownership.get("boundedM61M89EvidenceInherited") is False, "ownership must not inherit adjacent evidence")
    current = ownership.get("currentImplementation")
    require(isinstance(current, dict), "currentImplementation must be an object")
    require(current.get("fallbackReasonNone") is False, "ownership must not claim fallbackReason=none")
    require(current.get("renderArtifactsAdded") is False, "ownership must not add render artifacts")
    require(ownership.get("nonClaims") == NON_CLAIMS, "ownership nonClaims changed")


def build_summary(graph: dict[str, Any], ownership: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_imagemakewithfilter_graph_ownership_proof.py",
        "milestone": "M91",
        "ticket": "M91-IF-3B-GRAPH",
        "classification": CLASSIFICATION,
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "candidateReadiness": rel(CANDIDATE_READINESS_JSON),
            "for470Evidence": rel(FOR470_JSON),
            "gmSource": rel(GM_SOURCE),
        },
        "outputs": {
            "graph": rel(GRAPH_JSON),
            "intermediateOwnership": rel(OWNERSHIP_JSON),
        },
        "counters": {
            "graphArtifacts": 1,
            "ownershipArtifacts": 1,
            "gridCellsDescribed": graph["grid"]["cells"],
            "filterColumnsDescribed": graph["grid"]["columns"],
            "clipRowsDescribed": graph["grid"]["rows"],
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
            "rtk python3 scripts/m91_image_filter_imagemakewithfilter_graph_ownership_proof.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterGraphOwnershipProof",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterEvidenceIntake",
            "rtk git diff --check",
        ],
    }


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    counters = summary.get("counters")
    require(isinstance(counters, dict), "summary counters must be an object")
    require(counters.get("graphArtifacts") == 1, "graph artifact count changed")
    require(counters.get("ownershipArtifacts") == 1, "ownership artifact count changed")
    require(counters.get("gridCellsDescribed") == 78, "grid cell count changed")
    require(counters.get("filterColumnsDescribed") == 13, "filter column count changed")
    require(counters.get("clipRowsDescribed") == 6, "clip row count changed")
    require(counters.get("renderArtifactsAdded") == 0, "must not add render artifacts")
    require(counters.get("diffStatsAdded") == 0, "must not add diff/stat")
    require(counters.get("performanceArtifactsAdded") == 0, "must not add performance artifacts")
    require(counters.get("fallbackReasonNoneRoutesAdded") == 0, "must not add fallbackReason=none routes")
    require(counters.get("newSupportClaims") == 0, "must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "must not promote dashboard")
    require(counters.get("thresholdChanges") == 0, "must not change thresholds")
    require(summary.get("supportGuard") == NON_CLAIMS, "support guard changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD, GRAPH_JSON, OWNERSHIP_JSON}
    actual_files = {path for path in [SUMMARY_JSON, SUMMARY_MD, GRAPH_JSON, OWNERSHIP_JSON] if path.is_file()}
    require(actual_files == expected_files, f"missing generated files: {[rel(path) for path in sorted(expected_files - actual_files)]}")


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    lines = [
        "# M91 ImageMakeWithFilterGM Graph Ownership Proof",
        "",
        "Status: generated evidence",
        "",
        "This report adds row-specific graph and intermediate-ownership requirement artifacts for `skia-gm-imagemakewithfilter`. It describes the 6x13 ImageMakeWithFilterGM grid, but does not execute `MakeWithFilter`, add render artifacts, add diff/stat payloads, add performance evidence, add fallbackReason=none routes, or add a support claim.",
        "",
        "## Outputs",
        "",
        f"- Graph: `{summary['outputs']['graph']}`",
        f"- Intermediate ownership: `{summary['outputs']['intermediateOwnership']}`",
        "",
        "## Counters",
        "",
    ]
    for key, value in counters.items():
        lines.append(f"- {key}: `{value}`")
    lines.extend(["", "## Support Guard", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in summary["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def main() -> int:
    try:
        validate_registry(load_json(REGISTRY_JSON))
        validate_candidate_readiness(load_json(CANDIDATE_READINESS_JSON))
        validate_for470(load_json(FOR470_JSON))
        validate_source_contract()
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        graph = build_graph()
        ownership = build_ownership()
        validate_graph(graph)
        validate_ownership(ownership)
        write_json(GRAPH_JSON, graph)
        write_json(OWNERSHIP_JSON, ownership)
        summary = build_summary(graph, ownership)
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        validate_graph(load_json(GRAPH_JSON))
        validate_ownership(load_json(OWNERSHIP_JSON))
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_imagemakewithfilter_graph_ownership_proof: FAIL: {error}", file=sys.stderr)
        return 1
    print("M91 ImageMakeWithFilterGM graph ownership proof validation passed: graphArtifacts=1 ownershipArtifacts=1 gridCells=78 newSupportClaims=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

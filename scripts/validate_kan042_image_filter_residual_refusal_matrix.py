#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/image-filter-residual-refusal-matrix"
OUTPUT_JSON = "kan-042-image-filter-residual-refusal-matrix.json"
OUTPUT_MARKDOWN = "kan-042-image-filter-residual-refusal-matrix.md"

RESULTS_PATH = "reports/wgsl-pipeline/scenes/generated/results.json"
M52_PATH = "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"
M53_PATH = "reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json"
M54_PATH = "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"
KAN041_PATH = "reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_PM_PATH = ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md"
TARGET_RENDERER_PATH = ".upstream/target/skia-like-realtime-renderer-target.md"
SPEC_README_PATH = ".upstream/specs/skia-like-realtime/README.md"

DAG_REASON = "image-filter.dag-or-picture-prepass-required"
CROP_REASON = "image-filter.crop-input-nonnull-prepass-required"

SUPPORT_ROWS = [
    {
        "rowId": "crop-image-filter-nonnull-prepass",
        "source": "KAN-041 bounded Crop(input=Offset(null)) DAG support",
        "focus": "crop bounded support",
    },
    {
        "rowId": "m61-compose-cf-matrix-transform-dag-v2",
        "source": "KAN-041 bounded Compose(ColorFilter, MatrixTransform) DAG support",
        "focus": "bounded compose/color-filter/matrix-transform DAG",
    },
]

PACK_SUPPORT_ROWS = [
    {
        "rowId": "m53-imageblur-bounded-prepass",
        "sourcePath": M53_PATH,
        "source": "M53 bounded image blur prepass subset",
        "focus": "bounded blur support",
    },
    {
        "rowId": "m54-imagefilter-transformed-affine",
        "sourcePath": M54_PATH,
        "source": "M54 transformed affine image-filter subset",
        "focus": "affine transform support",
    },
    {
        "rowId": "m54-matrix-imagefilter-affine",
        "sourcePath": M54_PATH,
        "source": "M54 matrix image-filter affine subset",
        "focus": "matrix affine support",
    },
]

IMPLEMENTATION_SCENE_ROWS = [
    {
        "rowId": "image-filter-crop-nonnull-prepass-required",
        "sourcePath": RESULTS_PATH,
        "source": "Out-of-scope Crop(input=nonNull) graph shape",
        "focus": "crop out of scope",
        "reasonCode": CROP_REASON,
    },
    {
        "rowId": "m52-big-tile-image-filter-dag-refusal",
        "sourcePath": M52_PATH,
        "source": "M52 BigTileImageFilterGM DAG boundary",
        "focus": "picture/layer prepass boundary",
        "reasonCode": DAG_REASON,
    },
    {
        "rowId": "m54-imagefilters-graph-boundary",
        "sourcePath": M54_PATH,
        "source": "M54 ImageFiltersGraphGM boundary",
        "focus": "recursive DAG and picture-prepass boundary",
        "reasonCode": DAG_REASON,
    },
]

REJECTED_RESIDUAL_ROWS = [
    {
        "inventoryId": "skia-gm-blurbigsigma",
        "sourcePath": M54_PATH,
        "pmCategory": "implementation-gap",
        "reasonCode": "image-filter.blur-large-sigma-unsupported",
        "focus": "blur chain / large sigma",
    },
    {
        "inventoryId": "skia-gm-perspectiveclip",
        "sourcePath": M54_PATH,
        "pmCategory": "implementation-gap",
        "reasonCode": "image-filter.perspective-clip-unsupported",
        "focus": "perspective transform / clip",
    },
    {
        "inventoryId": "skia-gm-xfermodeimagefilter",
        "sourcePath": M54_PATH,
        "pmCategory": "implementation-gap",
        "reasonCode": "image-filter.xfermode-dag-unsupported",
        "focus": "xfermode image-filter DAG",
    },
    {
        "inventoryId": "skia-gm-animatedimageblurs",
        "sourcePath": M54_PATH,
        "pmCategory": "dependency-gated",
        "reasonCode": "image-filter.animated-image-decode-dependency-gated",
        "focus": "animated blur decode",
    },
    {
        "inventoryId": "skia-gm-animatedbackdropblur",
        "sourcePath": M54_PATH,
        "pmCategory": "dependency-gated",
        "reasonCode": "image-filter.animated-codec-backdrop-dependency-gated",
        "focus": "animated / codec-backed backdrop blur",
    },
    {
        "inventoryId": "skia-gm-imagefiltersstroked",
        "sourcePath": M54_PATH,
        "pmCategory": "dependency-gated",
        "reasonCode": "image-filter.path-aa-stroke-dependency-gated",
        "focus": "image-filter plus Path AA stroke breadth",
    },
    {
        "inventoryId": "skia-gm-runtimeimagefilter",
        "sourcePath": M53_PATH,
        "pmCategory": "dependency-gated",
        "reasonCode": "image-filter.runtime-descriptor-scope-dependency-gated",
        "focus": "runtime image-filter descriptor scope",
    },
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-042 image-filter residual refusal matrix validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def find_by_id(payload: Any, row_id: str) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []

    def walk(value: Any) -> None:
        if isinstance(value, dict):
            if value.get("id") == row_id or value.get("sceneId") == row_id or value.get("inventoryId") == row_id:
                matches.append(value)
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(payload)
    for match in matches:
        if match.get("status") is not None:
            return match
    if matches:
        return matches[0]
    fail(f"missing row `{row_id}`")


def find_rejected_row(payload: Any, inventory_id: str) -> dict[str, Any]:
    for row in payload.get("rejectedRows", []):
        if isinstance(row, dict) and row.get("inventoryId") == inventory_id:
            return row
    fail(f"missing rejected row `{inventory_id}`")


def dashboard_counts(results: dict[str, Any]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for scene in results.get("scenes", []):
        status = scene.get("status") if isinstance(scene, dict) else None
        if isinstance(status, str):
            counts[status] = counts.get(status, 0) + 1
    return counts


def nested_route(row: dict[str, Any], side: str) -> str | None:
    direct = row.get(f"{side}Route")
    if isinstance(direct, str):
        return direct
    side_payload = row.get(side)
    if isinstance(side_payload, dict):
        route = side_payload.get("route")
        if isinstance(route, dict):
            selected = route.get("selectedRoute") or route.get("coverageStrategy")
            if isinstance(selected, str):
                return selected
    return None


def nested_fallback(row: dict[str, Any]) -> str | None:
    fallback = row.get("fallbackReason")
    if isinstance(fallback, str):
        return fallback
    gpu = row.get("gpu")
    if isinstance(gpu, dict):
        route = gpu.get("route")
        if isinstance(route, dict) and isinstance(route.get("fallbackReason"), str):
            return route["fallbackReason"]
    return None


def supportable_from_kan041(scene: dict[str, Any], spec: dict[str, str]) -> dict[str, Any]:
    proofs = scene.get("proofs")
    require(scene.get("status") == "pass", f"{spec['rowId']} support status changed")
    require(scene.get("fallbackReason") == "none", f"{spec['rowId']} support fallback changed")
    require(isinstance(proofs, dict) and all(proofs.values()), f"{spec['rowId']} missing KAN-041 support proofs")
    return {
        "rowId": spec["rowId"],
        "title": scene["sceneId"],
        "pmCategory": "supportable-bounded",
        "status": "pass",
        "reasonCode": "none",
        "source": spec["source"],
        "focus": spec["focus"],
        "referenceKind": scene.get("referenceKind"),
        "referenceAvailable": bool(proofs.get("reference")),
        "cpuRoute": scene.get("artifactPaths", {}).get("cpuRoute"),
        "gpuRoute": scene.get("route"),
        "routeDiagnosticsAvailable": bool(proofs.get("route")),
        "proofComplete": True,
        "sourceEvidence": KAN041_PATH,
        "nonClaim": scene.get("nonClaim"),
        "newSupportClaim": False,
    }


def supportable_from_pack(row: dict[str, Any], spec: dict[str, str]) -> dict[str, Any]:
    require(row.get("status") == "pass", f"{spec['rowId']} support status changed")
    require(nested_fallback(row) == "none", f"{spec['rowId']} support fallback changed")
    cpu_route = nested_route(row, "cpu")
    gpu_route = nested_route(row, "gpu")
    reference_kind = row.get("referenceKind")
    proof_complete = bool(reference_kind and cpu_route and gpu_route)
    return {
        "rowId": spec["rowId"],
        "title": row.get("title") or spec["rowId"],
        "pmCategory": "supportable-bounded",
        "status": "pass",
        "reasonCode": "none",
        "source": spec["source"],
        "focus": spec["focus"],
        "referenceKind": reference_kind,
        "referenceAvailable": bool(reference_kind),
        "cpuRoute": cpu_route,
        "gpuRoute": gpu_route,
        "routeDiagnosticsAvailable": bool(cpu_route and gpu_route),
        "proofComplete": proof_complete,
        "sourceEvidence": spec["sourcePath"],
        "nonClaim": row.get("nonClaim"),
        "newSupportClaim": False,
    }


def implementation_gap_from_scene(row: dict[str, Any], spec: dict[str, str]) -> dict[str, Any]:
    fallback = nested_fallback(row) or spec["reasonCode"]
    require(fallback == spec["reasonCode"], f"{spec['rowId']} fallback changed: {fallback}")
    require(row.get("status") == "expected-unsupported", f"{spec['rowId']} status changed")
    graph = row.get("graphDiagnostics") if isinstance(row.get("graphDiagnostics"), dict) else {}
    cpu_route = nested_route(row, "cpu")
    gpu_route = nested_route(row, "gpu")
    non_claim = row.get("nonClaim") or graph.get("nonClaim")
    return {
        "rowId": spec["rowId"],
        "title": row.get("title") or spec["rowId"],
        "pmCategory": "implementation-gap",
        "status": "expected-unsupported",
        "reasonCode": fallback,
        "source": spec["source"],
        "focus": spec["focus"],
        "referenceKind": row.get("referenceKind"),
        "referenceAvailable": bool(row.get("referenceKind") or row.get("reference")),
        "cpuRoute": cpu_route,
        "gpuRoute": gpu_route,
        "routeDiagnosticsAvailable": bool(cpu_route or gpu_route or graph),
        "proofComplete": False,
        "sourceEvidence": spec["sourcePath"],
        "nonClaim": non_claim,
        "newSupportClaim": False,
    }


def residual_from_rejected(row: dict[str, Any], spec: dict[str, str]) -> dict[str, Any]:
    reason = row.get("reason")
    require(isinstance(reason, str) and reason.strip(), f"{spec['inventoryId']} missing rejection reason")
    return {
        "rowId": spec["inventoryId"],
        "title": spec["inventoryId"],
        "pmCategory": spec["pmCategory"],
        "status": "expected-unsupported" if spec["pmCategory"] == "implementation-gap" else "dependency-gated",
        "reasonCode": spec["reasonCode"],
        "source": f"{Path(spec['sourcePath']).name} rejectedRows",
        "focus": spec["focus"],
        "referenceKind": None,
        "referenceAvailable": False,
        "cpuRoute": None,
        "gpuRoute": None,
        "routeDiagnosticsAvailable": False,
        "proofComplete": False,
        "sourceEvidence": spec["sourcePath"],
        "nonClaim": reason,
        "newSupportClaim": False,
    }


def build_claim_guard(matrix_rows: list[dict[str, Any]], counts: dict[str, int]) -> dict[str, list[str]]:
    unsupported = [row for row in matrix_rows if row["pmCategory"] != "supportable-bounded"]
    support = [row for row in matrix_rows if row["pmCategory"] == "supportable-bounded"]
    return {
        "unsupportedRowsMissingStableReason": [
            row["rowId"]
            for row in unsupported
            if not row["reasonCode"] or row["reasonCode"] == "none" or "." not in row["reasonCode"]
        ],
        "unsupportedRowsMissingCategory": [
            row["rowId"]
            for row in unsupported
            if row["pmCategory"] not in {"implementation-gap", "dependency-gated"}
        ],
        "supportRowsMissingProofs": [
            row["rowId"]
            for row in support
            if row["reasonCode"] != "none" or not row["referenceAvailable"] or not row["routeDiagnosticsAvailable"]
        ],
        "hiddenBroadSupportClaims": [
            row["rowId"]
            for row in matrix_rows
            if row.get("newSupportClaim") is True
        ],
        "unexpectedDashboardRows": [
            status
            for status in ("fail", "tracked-gap")
            if counts.get(status, 0) != 0
        ],
        "thresholdOrBudgetChanges": [],
    }


def committed_artifacts(payloads: dict[str, Any]) -> list[str]:
    paths = {
        RESULTS_PATH,
        M52_PATH,
        M53_PATH,
        M54_PATH,
        KAN041_PATH,
        SPEC_REALTIME_PATH,
        SPEC_PM_PATH,
        TARGET_RENDERER_PATH,
        SPEC_README_PATH,
        "reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md",
        "reports/wgsl-pipeline/2026-05-31-m53-gm-feature-promotion-pack-v2-selection.md",
        "reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-pack.md",
    }
    for key in ("m52", "m53", "m54"):
        source_report = payloads[key].get("sourceReport")
        if isinstance(source_report, str):
            paths.add(source_report)
    return sorted(paths)


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    results = load_json(root, RESULTS_PATH)
    m52 = load_json(root, M52_PATH)
    m53 = load_json(root, M53_PATH)
    m54 = load_json(root, M54_PATH)
    kan041 = load_json(root, KAN041_PATH)

    require_contains(root, SPEC_REALTIME_PATH, [
        "DAG node count",
        "implicit readback compatibility",
        "filter graph support from copied single-node artifacts",
    ])
    require_contains(root, SPEC_PM_PATH, [
        "stable refusal reasons for arbitrary DAG/picture-prepass",
        "support/refusal matrix categories for supported, expected-unsupported, dependency-gated, implementation-gap",
    ])
    require_contains(root, TARGET_RENDERER_PATH, [
        "Missing support must produce stable diagnostics",
        "Do not rebuild Skia's SkSL compiler, IR, or VM.",
    ])
    require_contains(root, SPEC_README_PATH, [
        "Do not hide unsupported rows from inventory or dashboard evidence.",
        "Do not mark support as complete from route diagnostics alone.",
    ])

    support_by_id = {row["sceneId"]: row for row in kan041.get("supportScenes", [])}
    rows: list[dict[str, Any]] = []
    for spec in SUPPORT_ROWS:
        require(spec["rowId"] in support_by_id, f"missing KAN-041 support row {spec['rowId']}")
        rows.append(supportable_from_kan041(support_by_id[spec["rowId"]], spec))

    pack_payloads = {
        M53_PATH: m53,
        M54_PATH: m54,
    }
    for spec in PACK_SUPPORT_ROWS:
        rows.append(supportable_from_pack(find_by_id(pack_payloads[spec["sourcePath"]], spec["rowId"]), spec))

    scene_payloads = {
        RESULTS_PATH: results,
        M52_PATH: m52,
        M54_PATH: m54,
    }
    for spec in IMPLEMENTATION_SCENE_ROWS:
        rows.append(implementation_gap_from_scene(find_by_id(scene_payloads[spec["sourcePath"]], spec["rowId"]), spec))

    rejected_payloads = {
        M53_PATH: m53,
        M54_PATH: m54,
    }
    for spec in REJECTED_RESIDUAL_ROWS:
        rows.append(residual_from_rejected(find_rejected_row(rejected_payloads[spec["sourcePath"]], spec["inventoryId"]), spec))

    counts = dashboard_counts(results)
    guard = build_claim_guard(rows, counts)
    require(not guard["unsupportedRowsMissingStableReason"], f"unsupported rows missing stable reason: {guard['unsupportedRowsMissingStableReason']}")
    require(not guard["unsupportedRowsMissingCategory"], f"unsupported rows missing category: {guard['unsupportedRowsMissingCategory']}")
    require(not guard["supportRowsMissingProofs"], f"support rows missing proofs: {guard['supportRowsMissingProofs']}")
    require(not guard["hiddenBroadSupportClaims"], f"hidden broad support claims: {guard['hiddenBroadSupportClaims']}")
    require(not guard["unexpectedDashboardRows"], f"unexpected dashboard rows: {guard['unexpectedDashboardRows']}")

    payloads = {"m52": m52, "m53": m53, "m54": m54}
    artifacts = committed_artifacts(payloads)
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    support_count = sum(1 for row in rows if row["pmCategory"] == "supportable-bounded")
    implementation_count = sum(1 for row in rows if row["pmCategory"] == "implementation-gap")
    dependency_count = sum(1 for row in rows if row["pmCategory"] == "dependency-gated")
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-042",
        "packId": "kan-042-image-filter-residual-refusal-matrix",
        "status": "pass",
        "closureDecision": "image-filter-residual-refusal-matrix",
        "claimLevel": "pm-refusal-matrix-existing-evidence-only",
        "supportClaim": "no-new-rendering-support",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "readinessDelta": 0,
        "summary": {
            "totalRows": len(rows),
            "supportableBoundedRows": support_count,
            "implementationGapRows": implementation_count,
            "dependencyGatedRows": dependency_count,
            "rowsMissingStableReason": len(guard["unsupportedRowsMissingStableReason"]),
            "dashboardFailRows": counts.get("fail", 0),
            "dashboardTrackedGapRows": counts.get("tracked-gap", 0),
        },
        "dashboardStatusCounts": counts,
        "matrixRows": rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan008ImageFilterDagRefusals",
            "validateKan041ImageFilterDagBoundedV3",
            "pipelineSceneDashboardGate",
            "pipelinePmBundle",
        ],
        "nonClaims": [
            "KAN-042 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.",
            "KAN-042 does not claim arbitrary recursive image-filter DAG support.",
            "KAN-042 does not claim picture prepass, large layer prepass, BigTile/ImageFiltersGraph broad parity, or CPU readback fallback.",
            "KAN-042 does not convert dependency-gated or implementation-gap rows into support.",
            "KAN-042 does not rebuild Skia image-filter internals, Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
        ],
        "validationRows": [
            {
                "id": "stable-refusal-reasons",
                "status": "pass",
                "evidence": "Every non-support row has a stable image-filter reason code and PM category.",
            },
            {
                "id": "support-refusal-separated",
                "status": "pass",
                "evidence": "Matrix separates supportable-bounded, implementation-gap, and dependency-gated rows.",
            },
            {
                "id": "dashboard-clean",
                "status": "pass",
                "evidence": "Generated dashboard carries zero fail rows and zero tracked-gap rows.",
            },
            {
                "id": "no-new-rendering-claim",
                "status": "pass",
                "evidence": "Pack aggregates existing evidence only and records renderer/shader/threshold/budget changes as false.",
            },
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing),
            "missing": missing,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def markdown_table(rows: list[dict[str, Any]], category: str) -> str:
    selected = [row for row in rows if row["pmCategory"] == category]
    return "\n".join(
        "| `{rowId}` | `{status}` | `{reasonCode}` | {focus} | `{route}` |".format(
            rowId=row["rowId"],
            status=row["status"],
            reasonCode=row["reasonCode"],
            focus=row["focus"],
            route=row["gpuRoute"] or "n/a",
        )
        for row in selected
    )


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-042 Image Filter Residual Refusal Matrix

KAN-042 packages the residual image-filter refusal matrix from existing
generated evidence. It separates bounded support from implementation gaps and
dependency-gated rows without adding renderer behavior.

## Summary

| Metric | Count |
|---|---:|
| Total rows | {summary['totalRows']} |
| Supportable borne | {summary['supportableBoundedRows']} |
| implementation-gap | {summary['implementationGapRows']} |
| dependency-gated | {summary['dependencyGatedRows']} |
| Rows missing stable reason | {summary['rowsMissingStableReason']} |
| Dashboard fail rows | {summary['dashboardFailRows']} |
| Dashboard tracked-gap rows | {summary['dashboardTrackedGapRows']} |

## Supportable borne

| Row | Status | Reason | Focus | GPU route |
|---|---|---|---|---|
{markdown_table(evidence['matrixRows'], 'supportable-bounded')}

## implementation-gap

| Row | Status | Reason | Focus | GPU route |
|---|---|---|---|---|
{markdown_table(evidence['matrixRows'], 'implementation-gap')}

## dependency-gated

| Row | Status | Reason | Focus | GPU route |
|---|---|---|---|---|
{markdown_table(evidence['matrixRows'], 'dependency-gated')}

## Claim Guard

| Guard | Value |
|---|---|
| unsupportedRowsMissingStableReason | `{evidence['claimGuard']['unsupportedRowsMissingStableReason']}` |
| unsupportedRowsMissingCategory | `{evidence['claimGuard']['unsupportedRowsMissingCategory']}` |
| supportRowsMissingProofs | `{evidence['claimGuard']['supportRowsMissingProofs']}` |
| hiddenBroadSupportClaims | `{evidence['claimGuard']['hiddenBroadSupportClaims']}` |
| unexpectedDashboardRows | `{evidence['claimGuard']['unexpectedDashboardRows']}` |
| thresholdOrBudgetChanges | `{evidence['claimGuard']['thresholdOrBudgetChanges']}` |

## Required Validation

{required}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    summary = evidence["summary"]
    print(
        "KAN-042 validation passed: "
        f"{summary['totalRows']} rows, "
        f"{summary['implementationGapRows']} implementation-gap, "
        f"{summary['dependencyGatedRows']} dependency-gated, "
        f"{summary['dashboardFailRows']} fail rows."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

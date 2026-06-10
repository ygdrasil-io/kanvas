#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


OUTPUT_JSON = "runtime-shader-effects-v2-promotion.json"
OUTPUT_MARKDOWN = "runtime-shader-effects-v2-promotion.md"
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/runtime-shader-effects-v2"
M64_CONTRACT_PATH = "reports/wgsl-pipeline/scenes/generated/m64-registered-runtime-effects-pack.json"
SUPPORT_MATRIX_PATH = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"
LAYOUT_REPORT_PATH = "reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"
REQUIRED_ARTIFACTS = (
    "skia.png",
    "cpu.png",
    "gpu.png",
    "cpu-diff.png",
    "gpu-diff.png",
    "route-cpu.json",
    "route-gpu.json",
    "stats.json",
)


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-029 runtime shader effects V2 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> dict[str, Any]:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")
    require(isinstance(data, dict), f"{relative_path} must contain a JSON object")
    return data


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def row_by(rows: list[Any], field: str, value: str, source: str) -> dict[str, Any]:
    for row in rows:
        if isinstance(row, dict) and row.get(field) == value:
            return row
    fail(f"{source} missing row with {field}={value}")


def number(data: dict[str, Any], field: str, source: str) -> float:
    value = data.get(field)
    require(isinstance(value, (int, float)), f"{source}.{field} must be numeric")
    return float(value)


def route_field(route: dict[str, Any], field: str, source: str) -> str:
    value = route.get(field)
    require(isinstance(value, str) and value, f"{source}.{field} must be a non-empty string")
    return value


def simple_runtime_effect_route_details(route_gpu: dict[str, Any]) -> tuple[str, str]:
    runtime_effect = require_object(route_gpu, "runtimeEffect", "route-gpu")
    implementation = route_field(runtime_effect, "implementationId", "route-gpu.runtimeEffect")
    wgsl = route_field(runtime_effect, "wgsl", "route-gpu.runtimeEffect")
    return f"runtime.{implementation}", "wgsl/" + Path(wgsl).stem


def pipeline_key_policy(pipeline_key: str) -> dict[str, Any]:
    return {
        "policy": "uniform-values-excluded",
        "uniformValuesIncluded": False,
        "acceptedAxes": ["wgslImplementationId", "blendMode", "layoutReflection"],
        "observedPipelineKey": pipeline_key,
    }


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    contract = load_json(root, M64_CONTRACT_PATH)
    support_matrix = load_json(root, SUPPORT_MATRIX_PATH)
    layout_report = load_json(root, LAYOUT_REPORT_PATH)

    contract_rows = require_list(contract, "scenes", M64_CONTRACT_PATH)
    support_rows = require_list(support_matrix, "rows", SUPPORT_MATRIX_PATH)
    layout_rows = require_list(layout_report, "rows", LAYOUT_REPORT_PATH)
    selected = [
        row for row in contract_rows
        if isinstance(row, dict)
        and row.get("family") == "registered runtime effects"
        and row.get("status") == "pass"
        and row.get("fallbackReason") == "none"
    ]
    require(len(selected) == 3, "expected exactly 3 promoted registered runtime shader effects")

    effects: list[dict[str, Any]] = []
    for row in selected:
        scene_id = route_field(row, "baseArtifactScene", "M64 row")
        artifact_root = f"reports/wgsl-pipeline/scenes/artifacts/{scene_id}"
        artifacts = {Path(name).stem.replace("-", "_"): f"{artifact_root}/{name}" for name in REQUIRED_ARTIFACTS}
        route_cpu = load_json(root, artifacts["route_cpu"])
        route_gpu = load_json(root, artifacts["route_gpu"])
        stats = load_json(root, artifacts["stats"])

        gpu_details = require_object(row, "gpuRouteDetails", "M64 row")
        cpu_details = require_object(row, "cpuRouteDetails", "M64 row")
        stable_id = route_field(gpu_details, "runtimeEffectStableId", "M64 row.gpuRouteDetails")
        wgsl_id = route_field(gpu_details, "wgslImplementationId", "M64 row.gpuRouteDetails")
        if stable_id == "runtime.simple_rt" and (
            "runtimeEffectStableId" not in route_gpu or "wgslImplementationId" not in route_gpu
        ):
            route_stable_id, route_wgsl_id = simple_runtime_effect_route_details(route_gpu)
        else:
            route_stable_id = route_field(route_gpu, "runtimeEffectStableId", f"{scene_id}/route-gpu.json")
            route_wgsl_id = route_field(route_gpu, "wgslImplementationId", f"{scene_id}/route-gpu.json")

        support = row_by(support_rows, "stableId", stable_id, SUPPORT_MATRIX_PATH)
        layout = row_by(layout_rows, "stableId", stable_id, LAYOUT_REPORT_PATH)
        pipeline_key = route_field(route_gpu, "pipelineKey", f"{scene_id}/route-gpu.json")
        threshold = number(stats, "threshold", f"{scene_id}/stats.json")
        cpu_similarity = number(stats, "cpuSimilarity", f"{scene_id}/stats.json")
        gpu_similarity = number(stats, "gpuSimilarity", f"{scene_id}/stats.json")
        render_evidence = gpu_details.get("renderEvidence") or row.get("sourceTest") or route_gpu.get("test")

        effects.append(
            {
                "stableId": stable_id,
                "sceneId": scene_id,
                "status": "pass",
                "supportState": support.get("supportState"),
                "descriptorStatus": support.get("descriptorStatus"),
                "kind": support.get("kind"),
                "cpuImplementationId": cpu_details.get("cpuImplementationId"),
                "wgslImplementationId": wgsl_id,
                "layoutStatus": layout.get("status"),
                "uniformBlockSize": layout.get("uniformBlockSize"),
                "threshold": threshold,
                "cpuSimilarity": cpu_similarity,
                "gpuSimilarity": gpu_similarity,
                "cpuRoute": route_field(row, "cpuRoute", "M64 row"),
                "gpuRoute": route_field(row, "gpuRoute", "M64 row"),
                "cpuRouteStatus": route_field(route_cpu, "status", f"{scene_id}/route-cpu.json"),
                "gpuRouteStatus": route_field(route_gpu, "status", f"{scene_id}/route-gpu.json"),
                "fallbackReason": route_field(route_gpu, "fallbackReason", f"{scene_id}/route-gpu.json"),
                "routeStableId": route_stable_id,
                "routeWgslImplementationId": route_wgsl_id,
                "pipelineKeyPolicy": pipeline_key_policy(pipeline_key),
                "artifacts": artifacts,
                "parserEvidence": gpu_details.get("parserEvidence"),
                "renderEvidence": render_evidence,
                "nonClaim": row.get("nonClaim"),
            }
        )

    effects.sort(key=lambda item: item["stableId"])
    counts = {
        "total": len(effects),
        "supported": sum(1 for row in effects if row["status"] == "pass"),
        "fallbackNone": sum(1 for row in effects if row["fallbackReason"] == "none"),
        "layoutMatched": sum(1 for row in effects if row["layoutStatus"] == "layout-matched"),
        "belowThreshold": sum(1 for row in effects if row["gpuSimilarity"] < row["threshold"]),
        "missingArtifacts": 0,
    }
    evidence = {
        "schemaVersion": "kanvas.runtime-shader-effects.v2.promotion",
        "packId": "kan-029-runtime-shader-effects-v2-promotion",
        "ticket": "KAN-029",
        "status": "pass" if counts["supported"] == 3 and counts["belowThreshold"] == 0 else "fail",
        "claimLevel": "selected-registered-runtime-shader-effects-v2",
        "sourceOfTruth": {
            "supportMatrix": SUPPORT_MATRIX_PATH,
            "layoutReport": LAYOUT_REPORT_PATH,
            "sceneContract": M64_CONTRACT_PATH,
        },
        "counts": counts,
        "effects": effects,
        "stableRefusals": [
            "runtime-effect.arbitrary-sksl-unsupported",
            "runtime-effect.wgsl-descriptor-missing",
        ],
        "nonClaims": [
            "No dynamic SkSL compilation.",
            "No SkSL IR or VM.",
            "No arbitrary user WGSL input.",
            "No broad runtime-effect support beyond selected registered descriptors.",
            "No runtime color-filter, blender, image-filter helper, child shader, or live editor broad claim.",
            "No global similarity threshold change.",
        ],
        "validationRows": [
            {
                "id": "support-matrix-v2",
                "status": "pass",
                "detail": "3 descriptor-backed gpu-backed rows selected.",
            },
            {
                "id": "layout-v2",
                "status": "pass",
                "detail": "3 selected rows have layout-matched reflected WGSL uniforms.",
            },
            {
                "id": "visual-artifacts",
                "status": "pass",
                "detail": "reference/CPU/WebGPU/diff/stat and route artifacts exist for each selected effect.",
            },
        ],
    }
    validate_evidence(root, evidence)
    return evidence


def validate_evidence(root: Path, evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == "kanvas.runtime-shader-effects.v2.promotion", "schemaVersion changed")
    require(evidence.get("packId") == "kan-029-runtime-shader-effects-v2-promotion", "packId changed")
    require(evidence.get("ticket") == "KAN-029", "ticket id changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    counts = require_object(evidence, "counts", "evidence")
    require(counts.get("total") == 3, "total selected effect count changed")
    require(counts.get("supported") == 3, "supported count changed")
    require(counts.get("fallbackNone") == 3, "fallback-none count changed")
    require(counts.get("layoutMatched") == 3, "layout-matched count changed")
    require(counts.get("belowThreshold") == 0, "below-threshold count must stay zero")
    require(counts.get("missingArtifacts") == 0, "missing-artifacts count must stay zero")

    effects = require_list(evidence, "effects", "evidence")
    require([row.get("stableId") for row in effects] == [
        "runtime.linear_gradient_rt",
        "runtime.simple_rt",
        "runtime.spiral_rt",
    ], "selected stable ids changed")
    for row in effects:
        require(isinstance(row, dict), "effect row must be object")
        stable_id = row.get("stableId")
        require(row.get("status") == "pass", f"{stable_id} status must remain pass")
        require(row.get("supportState") == "gpu-backed", f"{stable_id} must stay gpu-backed")
        require(row.get("descriptorStatus") == "descriptor-backed", f"{stable_id} must stay descriptor-backed")
        require(row.get("layoutStatus") == "layout-matched", f"{stable_id} layout must stay matched")
        require(row.get("fallbackReason") == "none", f"{stable_id} fallbackReason must stay none")
        require(row.get("routeStableId") == stable_id, f"{stable_id} route stable id mismatch")
        require(row.get("routeWgslImplementationId") == row.get("wgslImplementationId"), f"{stable_id} route WGSL id mismatch")
        require(float(row.get("cpuSimilarity", 0)) >= float(row.get("threshold", 100)), f"{stable_id} CPU below threshold")
        require(float(row.get("gpuSimilarity", 0)) >= float(row.get("threshold", 100)), f"{stable_id} GPU below threshold")
        require(isinstance(row.get("parserEvidence"), str) and row["parserEvidence"], f"{stable_id} parser evidence missing")
        require(isinstance(row.get("renderEvidence"), str) and row["renderEvidence"], f"{stable_id} render evidence missing")
        key_policy = require_object(row, "pipelineKeyPolicy", f"{stable_id}")
        require(key_policy.get("uniformValuesIncluded") is False, f"{stable_id} must not include uniform values in PipelineKey")
        artifacts = require_object(row, "artifacts", f"{stable_id}")
        for artifact in artifacts.values():
            require(isinstance(artifact, str), f"{stable_id} artifact path must be string")
            require_file(root, artifact)
    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in (
        "No dynamic SkSL compilation",
        "No broad runtime-effect support",
        "No global similarity threshold change",
    ):
        require(snippet in non_claims, f"missing non-claim: {snippet}")


def markdown(evidence: dict[str, Any]) -> str:
    counts = evidence["counts"]
    lines = [
        "# Runtime Shader Effects V2 Promotion",
        "",
        "Derived evidence. Runtime Effect support matrix V2, layout V2, and scene artifacts are the sources.",
        (
            "Status counts: total={total}; supported={supported}; fallback-none={fallbackNone}; "
            "layout-matched={layoutMatched}; below-threshold={belowThreshold}; "
            "missing-artifacts={missingArtifacts}."
        ).format(**counts),
        "",
        "| Stable id | Scene | CPU route | GPU route | Similarity | Layout | Fallback |",
        "|---|---|---|---|---:|---|---|",
    ]
    for row in evidence["effects"]:
        lines.append(
            "| {stableId} | {sceneId} | {cpuRoute} | {gpuRoute} | {gpuSimilarity:.2f}% | {layoutStatus} | {fallbackReason} |".format(**row)
        )
    lines.extend([
        "",
        "## Evidence",
    ])
    for row in evidence["effects"]:
        lines.append("")
        lines.append(f"### {row['stableId']}")
        lines.append("")
        lines.append(f"- WGSL implementation: `{row['wgslImplementationId']}`")
        lines.append(f"- CPU implementation: `{row['cpuImplementationId']}`")
        lines.append(f"- Threshold: `{row['threshold']:.2f}`; CPU similarity: `{row['cpuSimilarity']:.2f}`; GPU similarity: `{row['gpuSimilarity']:.2f}`")
        lines.append(f"- Route CPU: `{row['artifacts']['route_cpu']}`")
        lines.append(f"- Route WebGPU: `{row['artifacts']['route_gpu']}`")
        lines.append(f"- Stats: `{row['artifacts']['stats']}`")
    lines.extend([
        "",
        "## Non-Claims",
    ])
    for non_claim in evidence["nonClaims"]:
        lines.append(f"- {non_claim}")
    return "\n".join(lines) + "\n"


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(markdown(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    counts = evidence["counts"]
    print(
        "KAN-029 runtime shader effects V2 promotion validation passed "
        f"total={counts['total']} supported={counts['supported']} layoutMatched={counts['layoutMatched']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        raise SystemExit(str(exc))

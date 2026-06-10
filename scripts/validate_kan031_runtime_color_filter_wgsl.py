#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


OUTPUT_JSON = "runtime-color-filter-wgsl.json"
OUTPUT_MARKDOWN = "runtime-color-filter-wgsl.md"
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/runtime-color-filter-wgsl"
SUPPORT_MATRIX_PATH = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"
LAYOUT_REPORT_PATH = "reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"
ARTIFACT_DIR = "reports/wgsl-pipeline/scenes/artifacts/kan-031-runtime-color-filter-luma-to-alpha"
STABLE_ID = "runtime.color_filter_luma_to_alpha"
WGSL_ID = "wgsl/runtime_color_filter_luma_to_alpha"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-031 runtime ColorFilter WGSL validation failed: {message}")


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


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
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


def string(data: dict[str, Any], field: str, source: str) -> str:
    value = data.get(field)
    require(isinstance(value, str) and value, f"{source}.{field} must be a non-empty string")
    return value


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def non_selected_color_filters() -> list[dict[str, str]]:
    return [
        {
            "stableId": "runtime.color_filter_noop",
            "state": "not-promoted",
            "reason": "runtime-effect.color-filter-cpu-only",
            "pmNote": "CPU behavior exists, but this ticket promotes only LumaToAlpha WGSL.",
        },
        {
            "stableId": "runtime.color_filter_tonemap",
            "state": "not-promoted",
            "reason": "runtime-effect.color-filter-cpu-only",
            "pmNote": "Ternary/Ifs/EarlyReturn tone-map variants stay CPU-only until separately scoped.",
        },
        {
            "stableId": "runtime.color_filter_g_channel_splat",
            "state": "dependency-gated",
            "reason": "color-filter.color-space-unsupported",
            "pmNote": "AlternateLuma depends on working color-space behavior outside this bounded slice.",
        },
        {
            "stableId": "runtime.color_filter_compose_children",
            "state": "not-promoted",
            "reason": "runtime-effect.color-filter-cpu-only",
            "pmNote": "ColorFilter child bindings are not promoted by this direct-rect WGSL route.",
        },
        {
            "stableId": "policy.unregistered_runtime_color_filter_wgsl",
            "state": "expected-unsupported",
            "reason": "runtime-effect.color-filter-wgsl-missing",
            "pmNote": "Runtime ColorFilters without a registered descriptor and parser-reflected WGSL stay refused.",
        },
    ]


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    support_matrix = load_json(root, SUPPORT_MATRIX_PATH)
    layout_report = load_json(root, LAYOUT_REPORT_PATH)
    stats = load_json(root, f"{ARTIFACT_DIR}/stats.json")
    route_cpu = load_json(root, f"{ARTIFACT_DIR}/route-cpu.json")
    route_gpu = load_json(root, f"{ARTIFACT_DIR}/route-webgpu.json")

    support = row_by(require_list(support_matrix, "rows", SUPPORT_MATRIX_PATH), "stableId", STABLE_ID, SUPPORT_MATRIX_PATH)
    layout = row_by(require_list(layout_report, "rows", LAYOUT_REPORT_PATH), "stableId", STABLE_ID, LAYOUT_REPORT_PATH)

    artifact_paths = {
        "reference": f"{ARTIFACT_DIR}/reference.png",
        "cpu": f"{ARTIFACT_DIR}/cpu.png",
        "webGpu": f"{ARTIFACT_DIR}/webgpu.png",
        "cpuDiff": f"{ARTIFACT_DIR}/cpu-diff.png",
        "webGpuDiff": f"{ARTIFACT_DIR}/webgpu-diff.png",
        "routeCpu": f"{ARTIFACT_DIR}/route-cpu.json",
        "routeWebGpu": f"{ARTIFACT_DIR}/route-webgpu.json",
        "stats": f"{ARTIFACT_DIR}/stats.json",
    }
    for path in artifact_paths.values():
        require_file(root, path)

    threshold = number(stats, "threshold", "stats")
    cpu_similarity = number(stats, "cpuSimilarity", "stats")
    webgpu_similarity = number(stats, "webGpuSimilarity", "stats")
    require(cpu_similarity >= threshold, "CPU/reference similarity below threshold")
    require(webgpu_similarity >= threshold, "WebGPU/reference similarity below threshold")
    require(string(route_cpu, "fallbackReason", "route-cpu") == "none", "CPU fallbackReason must be none")
    require(string(route_gpu, "fallbackReason", "route-webgpu") == "none", "WebGPU fallbackReason must be none")
    require(string(route_gpu, "stageOrder", "route-webgpu") == string(stats, "stageOrder", "stats"), "stageOrder mismatch")
    require("runtime color filter" in string(stats, "stageOrder", "stats"), "stageOrder must expose color-filter stage")
    require(stats.get("wgslValidated") is True, "stats must record WGSL parser validation")

    selected = {
        "stableId": STABLE_ID,
        "kind": support.get("kind"),
        "descriptorStatus": support.get("descriptorStatus"),
        "supportState": support.get("supportState"),
        "cpuImplementationId": support.get("cpuImplementationId"),
        "wgslImplementationId": support.get("wgslImplementationId"),
        "fallbackReason": support.get("fallbackReason"),
        "layoutStatus": layout.get("status"),
        "uniformBlockSize": layout.get("uniformBlockSize"),
        "uniforms": layout.get("uniforms"),
        "sceneId": stats.get("sceneId"),
        "selectedRoutes": {
            "cpu": route_cpu.get("selectedRoute"),
            "webGpu": route_gpu.get("selectedRoute"),
        },
        "stageOrder": stats.get("stageOrder"),
        "colorSpacePolicy": stats.get("colorSpacePolicy"),
        "paintSourceColor": stats.get("paintSourceColor"),
        "lumaAlpha": stats.get("lumaAlpha"),
        "filteredDisplayColor": stats.get("filteredDisplayColor"),
        "threshold": threshold,
        "cpuSimilarity": cpu_similarity,
        "webGpuSimilarity": webgpu_similarity,
        "wgslValidated": stats.get("wgslValidated"),
        "wgslSha256": stats.get("wgslSha256"),
        "wgslEntryPoints": stats.get("wgslEntryPoints"),
        "artifacts": artifact_paths,
    }
    evidence = {
        "schemaVersion": "kanvas.runtime-color-filter-wgsl",
        "packId": "kan-031-runtime-color-filter-wgsl",
        "ticket": "KAN-031",
        "status": "pass",
        "claimLevel": "selected-runtime-color-filter-luma-to-alpha-direct-rect",
        "sourceOfTruth": {
            "supportMatrix": SUPPORT_MATRIX_PATH,
            "layoutReport": LAYOUT_REPORT_PATH,
            "sceneArtifacts": ARTIFACT_DIR,
        },
        "counts": {
            "selected": 1,
            "gpuBacked": 1,
            "layoutMatched": 1,
            "belowThreshold": 0,
            "nonSelectedVisible": len(non_selected_color_filters()),
        },
        "selected": selected,
        "nonSelectedColorFilters": non_selected_color_filters(),
        "stableRefusals": [
            "runtime-effect.color-filter-wgsl-missing",
            "runtime-effect.color-filter-cpu-only",
            "color-filter.color-space-unsupported",
        ],
        "nonClaims": [
            "No broad ColorFilter support.",
            "No dynamic SkSL compilation.",
            "No SkSL IR or VM.",
            "No arbitrary runtime ColorFilter WGSL support.",
            "No runtime ColorFilter uniforms, child ColorFilters, LUTs, or color-space wrappers.",
            "No shader-input runtime ColorFilter route beyond solid-color direct rect.",
            "No global threshold or color policy change.",
        ],
        "validationRows": [
            {
                "id": "descriptor",
                "status": "pass",
                "detail": "runtime.color_filter_luma_to_alpha is descriptor-backed and gpu-backed.",
            },
            {
                "id": "layout",
                "status": "pass",
                "detail": "runtime_color_filter_luma_to_alpha.wgsl parses and layout reflection is matched.",
            },
            {
                "id": "scene-evidence",
                "status": "pass",
                "detail": "reference/CPU/WebGPU/diff/stat/route artifacts exist and exceed threshold.",
            },
            {
                "id": "fallback-taxonomy",
                "status": "pass",
                "detail": "Non-selected runtime ColorFilters are visible with stable reason codes.",
            },
        ],
    }
    validate_evidence(root, evidence)
    return evidence


def validate_evidence(root: Path, evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == "kanvas.runtime-color-filter-wgsl", "schemaVersion changed")
    require(evidence.get("packId") == "kan-031-runtime-color-filter-wgsl", "packId changed")
    require(evidence.get("ticket") == "KAN-031", "ticket id changed")
    require(evidence.get("status") == "pass", "status must be pass")
    selected = require_object(evidence, "selected", "evidence")
    require(selected.get("stableId") == STABLE_ID, "selected stable id changed")
    require(selected.get("kind") == "kColorFilter", "selected kind must be kColorFilter")
    require(selected.get("descriptorStatus") == "descriptor-backed", "selected descriptor status changed")
    require(selected.get("supportState") == "gpu-backed", "selected support state must be gpu-backed")
    require(selected.get("wgslImplementationId") == WGSL_ID, "selected WGSL id changed")
    require(selected.get("fallbackReason") == "none", "selected fallback reason must be none")
    require(selected.get("layoutStatus") == "layout-matched", "selected layout must be matched")
    require(selected.get("uniformBlockSize") == 0, "selected descriptor uniform block must stay empty")
    require(isinstance(selected.get("uniforms"), list) and len(selected["uniforms"]) == 0, "selected uniforms must stay empty")
    require(selected.get("wgslValidated") is True, "selected WGSL must be parser validated")
    require("fragment:fs_main" in selected.get("wgslEntryPoints", []), "selected WGSL fragment entrypoint missing")
    require(float(selected.get("cpuSimilarity", 0)) >= float(selected.get("threshold", 100)), "CPU below threshold")
    require(float(selected.get("webGpuSimilarity", 0)) >= float(selected.get("threshold", 100)), "WebGPU below threshold")
    artifacts = require_object(selected, "artifacts", "selected")
    for path in artifacts.values():
        require(isinstance(path, str), "artifact path must be string")
        require_file(root, path)
    required_reasons = {
        "runtime-effect.color-filter-wgsl-missing",
        "runtime-effect.color-filter-cpu-only",
        "color-filter.color-space-unsupported",
    }
    reasons = {row.get("reason") for row in require_list(evidence, "nonSelectedColorFilters", "evidence") if isinstance(row, dict)}
    require(required_reasons.issubset(reasons), "non-selected color filter reason taxonomy incomplete")
    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in ("No broad ColorFilter support", "No dynamic SkSL compilation", "No global threshold"):
        require(snippet in non_claims, f"missing non-claim: {snippet}")


def render_markdown(evidence: dict[str, Any]) -> str:
    selected = evidence["selected"]
    lines = [
        "# Runtime ColorFilter WGSL",
        "",
        f"Ticket: `{evidence['ticket']}`",
        "",
        (
            "Status counts: "
            f"selected={evidence['counts']['selected']}; "
            f"gpu-backed={evidence['counts']['gpuBacked']}; "
            f"layout-matched={evidence['counts']['layoutMatched']}; "
            f"below-threshold={evidence['counts']['belowThreshold']}; "
            f"non-selected-visible={evidence['counts']['nonSelectedVisible']}."
        ),
        "",
        "## Selected Support",
        "",
        "| Stable id | Kind | Support | WGSL | Layout | CPU similarity | WebGPU similarity |",
        "|---|---|---|---|---|---:|---:|",
        (
            f"| {selected['stableId']} | {selected['kind']} | {selected['supportState']} | "
            f"{selected['wgslImplementationId']} | {selected['layoutStatus']} | "
            f"{selected['cpuSimilarity']:.6f} | {selected['webGpuSimilarity']:.6f} |"
        ),
        "",
        "## Route",
        "",
        f"- Stage order: `{selected['stageOrder']}`",
        f"- Color-space policy: `{selected['colorSpacePolicy']}`",
        f"- Artifacts root: `{ARTIFACT_DIR}`",
        "",
        "## Non-Selected ColorFilters",
        "",
        "| Stable id | State | Reason | PM note |",
        "|---|---|---|---|",
    ]
    for row in evidence["nonSelectedColorFilters"]:
        lines.append(f"| {row['stableId']} | {row['state']} | {row['reason']} | {row['pmNote']} |")
    lines += [
        "",
        "## Non-Claims",
        "",
    ]
    lines += [f"- {item}" for item in evidence["nonClaims"]]
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    print(f"KAN-031 runtime ColorFilter WGSL report: {output_dir / OUTPUT_JSON}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)

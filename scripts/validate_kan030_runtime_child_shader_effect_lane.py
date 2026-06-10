#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


OUTPUT_JSON = "runtime-child-shader-effect-lane.json"
OUTPUT_MARKDOWN = "runtime-child-shader-effect-lane.md"
OUTPUT_ROUTE_JSON = "runtime-child-shader-effect-lane-route.json"
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/runtime-child-shader-effect-lane"
SUPPORT_MATRIX_PATH = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"
CHILD_EFFECTS_SOURCE = "cpu-raster/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsChildren.kt"
CHILD_EFFECTS_TEST = "cpu-raster/src/test/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsChildrenTest.kt"
WEBGPU_DEVICE_SOURCE = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-030 runtime child shader effect lane validation failed: {message}")


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


def read_text(root: Path, relative_path: str) -> str:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def row_by(rows: list[Any], field: str, value: str, source: str) -> dict[str, Any]:
    for row in rows:
        if isinstance(row, dict) and row.get(field) == value:
            return row
    fail(f"{source} missing row with {field}={value}")


def validate_static_sources(root: Path) -> None:
    child_source = read_text(root, CHILD_EFFECTS_SOURCE)
    child_test = read_text(root, CHILD_EFFECTS_TEST)
    webgpu_device = read_text(root, WEBGPU_DEVICE_SOURCE)

    for snippet in (
        'stableId = "runtime.unsharp_rt"',
        'SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, 0)',
        'cpuImplementationId = "kotlin/unsharp_rt"',
        "wgslImplementationId = null",
    ):
        require(snippet in child_source, f"missing child descriptor source snippet: {snippet}")
    for snippet in (
        "UnsharpRT MakeForShader resolves",
        "UnsharpRT with constant child returns input",
        "UnsharpRT kernel arithmetic checked unclamped",
    ):
        require(snippet in child_test, f"missing CPU oracle test snippet: {snippet}")
    require(
        "runtime-effect.child-binding-unsupported" in webgpu_device,
        "WebGPU runtime-effect path must keep child binding refusal visible",
    )


def pipeline_key_policy() -> dict[str, Any]:
    return {
        "policy": "resource-axes-classified-uniform-values-excluded",
        "uniformValuesIncluded": False,
        "acceptedAxes": [
            "wgslImplementationId",
            "blendMode",
            "layoutReflection",
            "childShaderResourceShape",
        ],
        "rejectedAxes": [
            "uniformBytes",
            "uniformValues",
            "childShaderPixels",
        ],
    }


def child_binding_rows() -> list[dict[str, Any]]:
    return [
        {
            "name": "child",
            "index": 0,
            "type": "kShader",
            "binding": "child[0]",
            "bindingState": "cpu-supported-gpu-unsupported",
            "resourceAxis": "childShader",
            "resourceAxisState": "classified-not-in-pipeline-key",
            "fallbackReason": "runtime-effect.child-binding-unsupported",
        }
    ]


def route_for(candidate: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": "kanvas.runtime-child-shader-effect-lane.route",
        "ticket": "KAN-030",
        "sceneId": "runtime.unsharp_rt.child-shader-lane.v1",
        "stableId": candidate["stableId"],
        "selectedRoute": "webgpu.runtime-effect.child-shader.expected-unsupported",
        "status": "expected-unsupported",
        "supportState": candidate["supportState"],
        "gpuSupportState": candidate["gpuSupportState"],
        "fallbackReason": candidate["gpuFallbackReason"],
        "children": candidate["children"],
        "pipelineKeyPolicy": candidate["pipelineKeyPolicy"],
        "cpuOracle": candidate["cpuOracle"],
        "nonClaims": candidate["nonClaims"],
    }


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    validate_static_sources(root)
    support_matrix = load_json(root, SUPPORT_MATRIX_PATH)
    support_rows = require_list(support_matrix, "rows", SUPPORT_MATRIX_PATH)
    support = row_by(support_rows, "stableId", "runtime.unsharp_rt", SUPPORT_MATRIX_PATH)
    require(support.get("descriptorStatus") == "descriptor-backed", "runtime.unsharp_rt must be descriptor-backed")
    require(support.get("supportState") == "cpu-only", "runtime.unsharp_rt must remain CPU-only")
    require(support.get("children") == ["child:kShader"], "runtime.unsharp_rt child descriptor changed")
    require(support.get("wgslImplementationId") is None, "runtime.unsharp_rt must not claim WGSL implementation")

    candidate = {
        "stableId": "runtime.unsharp_rt",
        "kind": support.get("kind"),
        "descriptorStatus": support.get("descriptorStatus"),
        "supportState": support.get("supportState"),
        "cpuImplementationId": support.get("cpuImplementationId"),
        "wgslImplementationId": support.get("wgslImplementationId"),
        "children": child_binding_rows(),
        "cpuOracle": {
            "status": "pass",
            "testClass": "SkBuiltinShaderEffectsChildrenTest",
            "testFile": CHILD_EFFECTS_TEST,
            "coveredBehavior": [
                "MakeForShader resolves UnsharpRT",
                "single named shader child is required at index 0",
                "constant child kernel returns input",
                "direct impl oracle verifies unclamped kernel arithmetic",
            ],
        },
        "gpuSupportState": "expected-unsupported",
        "gpuFallbackReason": "runtime-effect.child-binding-unsupported",
        "resourceAxes": [
            {
                "axis": "childShaderResourceShape",
                "state": "classified",
                "includedInPipelineKey": True,
            },
            {
                "axis": "childShaderPixels",
                "state": "runtime-resource",
                "includedInPipelineKey": False,
            },
        ],
        "pipelineKeyPolicy": pipeline_key_policy(),
        "routeJson": f"{DEFAULT_OUTPUT_DIR}/{OUTPUT_ROUTE_JSON}",
        "nonClaims": [
            "No GPU child shader support claim.",
            "No dynamic SkSL compilation.",
            "No SkSL IR or VM.",
            "No arbitrary runtime-effect DAG support.",
            "No child shader texture/resource binding allocation claim.",
            "No uniform values in PipelineKey.",
        ],
    }
    evidence = {
        "schemaVersion": "kanvas.runtime-child-shader-effect-lane",
        "packId": "kan-030-runtime-child-shader-effect-lane",
        "ticket": "KAN-030",
        "status": "expected-unsupported",
        "claimLevel": "selected-child-shader-runtime-effect-cpu-only-gpu-refusal",
        "sourceOfTruth": {
            "supportMatrix": SUPPORT_MATRIX_PATH,
            "childEffectSource": CHILD_EFFECTS_SOURCE,
            "childEffectCpuOracle": CHILD_EFFECTS_TEST,
            "webGpuRuntimeEffectPath": WEBGPU_DEVICE_SOURCE,
        },
        "counts": {
            "totalCandidates": 1,
            "cpuSupported": 1,
            "gpuSupported": 0,
            "gpuExpectedUnsupported": 1,
            "childBindings": 1,
            "uniformValuesInPipelineKey": 0,
        },
        "candidates": [candidate],
        "route": route_for(candidate),
        "stableRefusals": [
            "runtime-effect.child-binding-unsupported",
            "runtime-effect.child-wgsl-layout-missing",
            "runtime-effect.child-resource-axis-unsupported",
        ],
        "validationRows": [
            {
                "id": "descriptor-child-representation",
                "status": "pass",
                "detail": "runtime.unsharp_rt descriptor exposes child:kShader at index 0.",
            },
            {
                "id": "cpu-oracle",
                "status": "pass",
                "detail": "SkBuiltinShaderEffectsChildrenTest covers UnsharpRT child shader CPU behavior.",
            },
            {
                "id": "gpu-refusal",
                "status": "pass",
                "detail": "No WGSL implementation, parser reflection, resource binding, or diff/stat artifact is claimed.",
            },
        ],
        "nonClaims": candidate["nonClaims"],
    }
    validate_evidence(root, evidence)
    return evidence


def validate_evidence(root: Path, evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == "kanvas.runtime-child-shader-effect-lane", "schemaVersion changed")
    require(evidence.get("packId") == "kan-030-runtime-child-shader-effect-lane", "packId changed")
    require(evidence.get("ticket") == "KAN-030", "ticket id changed")
    require(evidence.get("status") == "expected-unsupported", "status must remain expected-unsupported")
    counts = evidence.get("counts")
    require(isinstance(counts, dict), "counts must be an object")
    require(counts.get("totalCandidates") == 1, "total candidate count changed")
    require(counts.get("cpuSupported") == 1, "CPU supported count changed")
    require(counts.get("gpuSupported") == 0, "GPU support must remain refused without WGSL child evidence")
    require(counts.get("gpuExpectedUnsupported") == 1, "GPU expected-unsupported count changed")
    require(counts.get("childBindings") == 1, "child binding count changed")
    require(counts.get("uniformValuesInPipelineKey") == 0, "uniform values must stay out of PipelineKey")

    candidates = require_list(evidence, "candidates", "evidence")
    require(len(candidates) == 1, "expected one selected child shader candidate")
    row = candidates[0]
    require(isinstance(row, dict), "candidate row must be object")
    require(row.get("stableId") == "runtime.unsharp_rt", "selected child shader candidate changed")
    require(row.get("descriptorStatus") == "descriptor-backed", "candidate must stay descriptor-backed")
    require(row.get("supportState") == "cpu-only", "candidate must stay CPU-only")
    require(row.get("gpuSupportState") == "expected-unsupported", "GPU support must remain refused without evidence")
    require(row.get("wgslImplementationId") is None, "candidate must not claim WGSL implementation")
    require(row.get("gpuFallbackReason") == "runtime-effect.child-binding-unsupported", "child fallback reason changed")
    children = require_list(row, "children", "candidate")
    require(children == child_binding_rows(), "child binding route rows changed")
    key_policy = row.get("pipelineKeyPolicy")
    require(isinstance(key_policy, dict), "pipelineKeyPolicy must be object")
    require(key_policy.get("uniformValuesIncluded") is False, "PipelineKey must exclude uniform values")
    require("uniformBytes" not in key_policy.get("acceptedAxes", []), "PipelineKey accepted axes must not include uniform bytes")

    route = evidence.get("route")
    require(isinstance(route, dict), "route must be an object")
    require(route.get("fallbackReason") == "runtime-effect.child-binding-unsupported", "route fallback reason changed")
    require(route.get("children") == children, "route must list each child binding")

    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in (
        "No GPU child shader support claim",
        "No dynamic SkSL compilation",
        "No uniform values in PipelineKey",
    ):
        require(snippet in non_claims, f"missing non-claim: {snippet}")


def markdown(evidence: dict[str, Any]) -> str:
    counts = evidence["counts"]
    lines = [
        "# Runtime Child Shader Effect Lane",
        "",
        "Derived evidence for KAN-030. The lane selects one CPU-backed child shader runtime effect and keeps WebGPU support refused until child bindings, WGSL layout, and resource axes have parser/reflection and render evidence.",
        (
            "Status counts: total-candidates={totalCandidates}; cpu-supported={cpuSupported}; "
            "gpu-supported={gpuSupported}; gpu-expected-unsupported={gpuExpectedUnsupported}; "
            "child-bindings={childBindings}; uniform-values-in-pipeline-key={uniformValuesInPipelineKey}."
        ).format(**counts),
        "",
        "| Stable id | Descriptor | CPU | GPU | Child bindings | Fallback |",
        "|---|---|---|---|---:|---|",
    ]
    for row in evidence["candidates"]:
        lines.append(
            "| {stableId} | {descriptorStatus} | {supportState} | {gpuSupportState} | {child_count} | {gpuFallbackReason} |".format(
                child_count=len(row["children"]),
                **row,
            )
        )
    lines.extend(["", "## Route"])
    route = evidence["route"]
    lines.append("")
    lines.append(f"- Selected route: `{route['selectedRoute']}`")
    lines.append(f"- Fallback: `{route['fallbackReason']}`")
    for child in route["children"]:
        lines.append(
            f"- Child `{child['name']}`: index `{child['index']}`, type `{child['type']}`, binding `{child['binding']}`, state `{child['bindingState']}`."
        )
    lines.extend(["", "## Non-Claims"])
    for non_claim in evidence["nonClaims"]:
        lines.append(f"- {non_claim}")
    return "\n".join(lines) + "\n"


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(markdown(evidence), encoding="utf-8")
    (output_dir / OUTPUT_ROUTE_JSON).write_text(json.dumps(evidence["route"], indent=2, sort_keys=False) + "\n", encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    counts = evidence["counts"]
    print(
        "KAN-030 runtime child shader effect lane validation passed "
        f"candidates={counts['totalCandidates']} gpuExpectedUnsupported={counts['gpuExpectedUnsupported']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        raise SystemExit(str(exc))

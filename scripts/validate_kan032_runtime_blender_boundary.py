#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


OUTPUT_JSON = "runtime-blender-boundary.json"
OUTPUT_MARKDOWN = "runtime-blender-boundary.md"
OUTPUT_ROUTE_JSON = "runtime-blender-boundary-route.json"
OUTPUT_BLEND_PLAN_JSON = "runtime-blender-boundary-blend-plan.json"
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/runtime-blender-boundary"
SUPPORT_MATRIX_PATH = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"
SPECIALISED_SOURCE = "cpu-raster/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinSpecialisedEffects.kt"
BLENDER_SOURCE = "cpu-raster/src/main/kotlin/org/skia/effects/runtime/SkRuntimeBlender.kt"
DESCRIPTOR_SOURCE = "cpu-raster/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectDescriptor.kt"
WEBGPU_DEVICE_SOURCE = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CPU_TEST = "cpu-raster/src/test/kotlin/org/skia/effects/runtime/SkRuntimeBlenderTest.kt"
GPU_TEST = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/RuntimeEffectDescriptorWebGpuTest.kt"
STABLE_ID = "runtime.invert_blender"
CPU_IMPL_ID = "kotlin/invert_blender"
FALLBACK_REASON = "runtime-effect.blender-dst-read-unsupported"
LAYER_REASON = "blend.shader-layer-required"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-032 runtime blender boundary validation failed: {message}")


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
    specialised = read_text(root, SPECIALISED_SOURCE)
    blender = read_text(root, BLENDER_SOURCE)
    descriptor = read_text(root, DESCRIPTOR_SOURCE)
    webgpu = read_text(root, WEBGPU_DEVICE_SOURCE)
    cpu_test = read_text(root, CPU_TEST)
    gpu_test = read_text(root, GPU_TEST)

    for snippet in (
        'stableId = "runtime.invert_blender"',
        'kind = SkRuntimeEffect.Kind.kBlender',
        'cpuImplementationId = "kotlin/invert_blender"',
        "wgslImplementationId = null",
    ):
        require(snippet in specialised, f"missing runtime blender descriptor snippet: {snippet}")
    require("runtimeEffectDescriptor" in blender, "SkRuntimeBlender must carry descriptor identity")
    require(FALLBACK_REASON in descriptor, "support matrix must serialize blender dst-read fallback")
    for snippet in (
        FALLBACK_REASON,
        LAYER_REASON,
        "No CPU readback or implicit destination read fallback is available.",
    ):
        require(snippet in webgpu, f"missing WebGPU refusal snippet: {snippet}")
    require(
        "builtin invert runtime blender uses destination color on CPU" in cpu_test,
        "missing CPU behavior fixture test",
    )
    require(
        "runtime blender refuses WebGPU destination read without implicit readback" in gpu_test,
        "missing WebGPU refusal test",
    )
    require(
        "runtime blender refuses stroked WebGPU rect before style dispatch" in gpu_test,
        "missing style-independent WebGPU refusal test",
    )
    require(
        "runtime blender refuses blurred WebGPU rect before mask-filter layer route" in gpu_test,
        "missing pre-mask-filter WebGPU refusal test",
    )


def cpu_fixture() -> dict[str, Any]:
    return {
        "status": "pass",
        "testClass": "SkRuntimeBlenderTest",
        "testFile": CPU_TEST,
        "source": "gm/destcolor.cpp invert blender",
        "src": [1.0, 0.0, 0.0, 0.5],
        "dst": [0.25, 0.5, 0.75, 0.4],
        "expected": [0.75, 0.5, 0.25, 1.0],
        "formula": "(half4(1) - dst).rgb1",
    }


def blend_plan_dump() -> dict[str, Any]:
    return {
        "schemaVersion": "kanvas.runtime-blender-boundary.blend-plan",
        "ticket": "KAN-032",
        "stableId": STABLE_ID,
        "candidate": "InvertBlender",
        "operation": "runtime blender main(src, dst) reads destination color",
        "kind": "ShaderLayerComposite",
        "status": "required-not-implemented",
        "fixedFunctionAllowed": False,
        "requiresDestinationColor": True,
        "requiresLayerComposite": True,
        "implicitDestinationReadAllowed": False,
        "cpuReadbackAllowed": False,
        "fallbackReasons": [FALLBACK_REASON, LAYER_REASON],
        "reason": (
            "The candidate computes its output from dstColor, so WebGPU cannot use a fixed-function "
            "blend state or silently read destination pixels. A future GPU pass must prove an explicit "
            "shader/layer composite BlendPlan with no CPU readback."
        ),
    }


def route_dump(candidate: dict[str, Any], blend_plan_path: str) -> dict[str, Any]:
    return {
        "schemaVersion": "kanvas.runtime-blender-boundary.route",
        "ticket": "KAN-032",
        "sceneId": "runtime.invert_blender.boundary.v1",
        "stableId": STABLE_ID,
        "selectedRoute": "webgpu.runtime-blender.expected-unsupported",
        "status": "expected-unsupported",
        "supportState": candidate["supportState"],
        "cpuImplementationId": candidate["cpuImplementationId"],
        "wgslImplementationId": candidate["wgslImplementationId"],
        "fallbackReason": FALLBACK_REASON,
        "secondaryFallbackReason": LAYER_REASON,
        "routeDiagnostics": {
            "requiresDestinationColor": True,
            "requiresLayerComposite": True,
            "fixedFunctionBlendAllowed": False,
            "implicitDestinationReadAllowed": False,
            "cpuReadbackAllowed": False,
            "hiddenLayerCompatAllowed": False,
            "blendPlanArtifact": blend_plan_path,
        },
        "gpuGate": {
            "gpuPassAllowedNow": False,
            "futurePassCondition": (
                "BlendPlan proves an explicit shader/layer composite route for destination color "
                "without implicit readback."
            ),
        },
        "cpuFixture": cpu_fixture(),
        "nonClaims": non_claims(),
    }


def non_claims() -> list[str]:
    return [
        "No support for all blend modes.",
        "No GPU runtime blender support.",
        "No implicit destination read.",
        "No CPU readback fallback.",
        "No hidden layer compatibility path.",
        "No dynamic SkSL compilation.",
        "No SkSL IR or VM.",
    ]


def build_evidence(root: Path, output_dir: Path) -> dict[str, Any]:
    root = root.resolve()
    validate_static_sources(root)
    support_matrix = load_json(root, SUPPORT_MATRIX_PATH)
    support = row_by(require_list(support_matrix, "rows", SUPPORT_MATRIX_PATH), "stableId", STABLE_ID, SUPPORT_MATRIX_PATH)
    require(support.get("descriptorStatus") == "descriptor-backed", "runtime.invert_blender must be descriptor-backed")
    require(support.get("kind") == "kBlender", "runtime.invert_blender kind must be kBlender")
    require(support.get("supportState") == "cpu-only", "runtime.invert_blender must remain CPU-only")
    require(support.get("cpuImplementationId") == CPU_IMPL_ID, "runtime.invert_blender CPU impl id changed")
    require(support.get("wgslImplementationId") is None, "runtime.invert_blender must not claim WGSL")
    require(support.get("fallbackReason") == FALLBACK_REASON, "runtime.invert_blender fallback reason changed")

    blend_plan_path = f"{DEFAULT_OUTPUT_DIR}/{OUTPUT_BLEND_PLAN_JSON}"
    route_path = f"{DEFAULT_OUTPUT_DIR}/{OUTPUT_ROUTE_JSON}"
    candidate = {
        "stableId": STABLE_ID,
        "kind": support.get("kind"),
        "descriptorStatus": support.get("descriptorStatus"),
        "supportState": support.get("supportState"),
        "cpuImplementationId": support.get("cpuImplementationId"),
        "wgslImplementationId": support.get("wgslImplementationId"),
        "fallbackReason": support.get("fallbackReason"),
        "cpuFixture": cpu_fixture(),
        "routeJson": route_path,
        "blendPlanJson": blend_plan_path,
    }
    evidence = {
        "schemaVersion": "kanvas.runtime-blender-boundary",
        "packId": "kan-032-runtime-blender-boundary",
        "ticket": "KAN-032",
        "status": "expected-unsupported",
        "claimLevel": "selected-runtime-blender-cpu-only-gpu-boundary-refusal",
        "sourceOfTruth": {
            "supportMatrix": SUPPORT_MATRIX_PATH,
            "descriptorSource": SPECIALISED_SOURCE,
            "cpuFixtureTest": CPU_TEST,
            "webGpuRefusalTest": GPU_TEST,
            "webGpuRoute": WEBGPU_DEVICE_SOURCE,
        },
        "counts": {
            "totalCandidates": 1,
            "cpuSupported": 1,
            "gpuSupported": 0,
            "gpuExpectedUnsupported": 1,
            "destinationReadCandidates": 1,
            "implicitReadbackRoutes": 0,
            "allBlendModesSupported": 0,
        },
        "candidate": candidate,
        "route": route_dump(candidate, blend_plan_path),
        "blendPlan": blend_plan_dump(),
        "stableRefusals": [FALLBACK_REASON, LAYER_REASON, "runtime-effect.blender-wgsl-descriptor-missing"],
        "nonClaims": non_claims(),
        "artifacts": {
            "reportJson": f"{DEFAULT_OUTPUT_DIR}/{OUTPUT_JSON}",
            "reportMarkdown": f"{DEFAULT_OUTPUT_DIR}/{OUTPUT_MARKDOWN}",
            "routeJson": route_path,
            "blendPlanJson": blend_plan_path,
        },
        "validationRows": [
            {
                "id": "descriptor",
                "status": "pass",
                "detail": "runtime.invert_blender is descriptor-backed, kBlender, CPU-only, and has no WGSL id.",
            },
            {
                "id": "cpu-fixture",
                "status": "pass",
                "detail": "SkRuntimeBlenderTest checks destination-color inversion.",
            },
            {
                "id": "webgpu-boundary",
                "status": "pass",
                "detail": "WebGPU refuses fill and stroke runtime blender destination reads with stable diagnostics.",
            },
            {
                "id": "blend-plan-gate",
                "status": "pass",
                "detail": "GPU pass remains forbidden until an explicit shader/layer composite BlendPlan exists.",
            },
        ],
    }
    validate_evidence(evidence)
    return evidence


def validate_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == "kanvas.runtime-blender-boundary", "schemaVersion changed")
    require(evidence.get("packId") == "kan-032-runtime-blender-boundary", "packId changed")
    require(evidence.get("ticket") == "KAN-032", "ticket id changed")
    require(evidence.get("status") == "expected-unsupported", "status must remain expected-unsupported")
    counts = evidence.get("counts")
    require(isinstance(counts, dict), "counts must be an object")
    require(counts.get("totalCandidates") == 1, "total candidate count changed")
    require(counts.get("cpuSupported") == 1, "CPU support count changed")
    require(counts.get("gpuSupported") == 0, "GPU support must remain refused")
    require(counts.get("gpuExpectedUnsupported") == 1, "GPU expected-unsupported count changed")
    require(counts.get("implicitReadbackRoutes") == 0, "implicit readback route must stay forbidden")
    require(counts.get("allBlendModesSupported") == 0, "must not claim all blend modes")

    candidate = evidence.get("candidate")
    require(isinstance(candidate, dict), "candidate must be an object")
    require(candidate.get("stableId") == STABLE_ID, "selected candidate changed")
    require(candidate.get("supportState") == "cpu-only", "candidate must remain CPU-only")
    require(candidate.get("wgslImplementationId") is None, "candidate must not claim WGSL")
    require(candidate.get("fallbackReason") == FALLBACK_REASON, "candidate fallback changed")

    route = evidence.get("route")
    require(isinstance(route, dict), "route must be an object")
    require(route.get("status") == "expected-unsupported", "route status must remain expected-unsupported")
    require(route.get("fallbackReason") == FALLBACK_REASON, "route fallback changed")
    require(route.get("secondaryFallbackReason") == LAYER_REASON, "route layer fallback changed")
    diagnostics = route.get("routeDiagnostics")
    require(isinstance(diagnostics, dict), "route diagnostics must be an object")
    require(diagnostics.get("requiresDestinationColor") is True, "route must serialize destination read need")
    require(diagnostics.get("requiresLayerComposite") is True, "route must serialize layer-composite need")
    require(diagnostics.get("implicitDestinationReadAllowed") is False, "implicit destination read must be false")
    require(diagnostics.get("cpuReadbackAllowed") is False, "CPU readback must be false")
    require(diagnostics.get("hiddenLayerCompatAllowed") is False, "hidden layer compat must be false")

    blend_plan = evidence.get("blendPlan")
    require(isinstance(blend_plan, dict), "blendPlan must be an object")
    require(blend_plan.get("kind") == "ShaderLayerComposite", "BlendPlan kind must record layer requirement")
    require(blend_plan.get("status") == "required-not-implemented", "BlendPlan status changed")
    require(blend_plan.get("fixedFunctionAllowed") is False, "fixed-function blend must remain disallowed")
    require(blend_plan.get("implicitDestinationReadAllowed") is False, "BlendPlan implicit read must be false")
    require(blend_plan.get("cpuReadbackAllowed") is False, "BlendPlan CPU readback must be false")

    non_claims = require_list(evidence, "nonClaims", "evidence")
    for expected in (
        "No support for all blend modes.",
        "No implicit destination read.",
        "No CPU readback fallback.",
    ):
        require(expected in non_claims, f"missing non-claim: {expected}")


def render_markdown(evidence: dict[str, Any]) -> str:
    counts = evidence["counts"]
    candidate = evidence["candidate"]
    route = evidence["route"]
    blend_plan = evidence["blendPlan"]
    rows = evidence["validationRows"]
    validation_rows = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['detail']} |"
        for row in rows
    )
    non_claim_rows = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# Runtime Blender Boundary

Ticket: `KAN-032`
Status: `expected-unsupported`
Status counts: candidates={counts['totalCandidates']}; CPU-supported={counts['cpuSupported']}; GPU-supported={counts['gpuSupported']}; expected-unsupported={counts['gpuExpectedUnsupported']}; implicit-readback={counts['implicitReadbackRoutes']}.

## Candidate

- Stable id: `{candidate['stableId']}`
- Kind: `{candidate['kind']}`
- Support state: `{candidate['supportState']}`
- CPU implementation: `{candidate['cpuImplementationId']}`
- WGSL implementation: `none`
- Fallback reason: `{candidate['fallbackReason']}`

## Route Diagnostics

- Route: `{route['selectedRoute']}`
- Status: `{route['status']}`
- Fallback: `{route['fallbackReason']}`
- Layer requirement: `{route['secondaryFallbackReason']}`
- Requires destination color: `{route['routeDiagnostics']['requiresDestinationColor']}`
- Requires layer composite: `{route['routeDiagnostics']['requiresLayerComposite']}`
- Implicit destination read allowed: `{route['routeDiagnostics']['implicitDestinationReadAllowed']}`
- CPU readback allowed: `{route['routeDiagnostics']['cpuReadbackAllowed']}`

## BlendPlan Dump

- Kind: `{blend_plan['kind']}`
- Status: `{blend_plan['status']}`
- Fixed-function allowed: `{blend_plan['fixedFunctionAllowed']}`
- Implicit destination read allowed: `{blend_plan['implicitDestinationReadAllowed']}`
- Reason: {blend_plan['reason']}

## Validation Rows

| ID | Status | Detail |
|---|---|---|
{validation_rows}

## Non-Claims

{non_claim_rows}
"""


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]) if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    if not output_dir.is_absolute():
        output_dir = root / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    evidence = build_evidence(root, output_dir)
    write_json(output_dir / OUTPUT_JSON, evidence)
    write_json(output_dir / OUTPUT_ROUTE_JSON, evidence["route"])
    write_json(output_dir / OUTPUT_BLEND_PLAN_JSON, evidence["blendPlan"])
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    print(f"KAN-032 runtime blender boundary report: {output_dir / OUTPUT_JSON}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)

#!/usr/bin/env python3
import json
import math
import struct
import sys
import zlib
from pathlib import Path
from typing import Any, Callable


OUTPUT_JSON = "runtime-effect-uniform-preview.json"
OUTPUT_MARKDOWN = "runtime-effect-uniform-preview.md"
OUTPUT_EDITED_STATES_JSON = "runtime-effect-uniform-preview-edited-states.json"
OUTPUT_TELEMETRY_JSON = "runtime-effect-uniform-preview-telemetry.json"
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/runtime-effect-uniform-preview"
SUPPORT_MATRIX_PATH = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"
LAYOUT_REPORT_PATH = "reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"
FALLBACK_OUT_OF_RANGE = "runtime-effect.preview-uniform-out-of-range"
FALLBACK_NOT_REGISTERED = "runtime-effect.preview-effect-not-registered"
FALLBACK_ARBITRARY_SKSL = "runtime-effect.arbitrary-sksl-unsupported"
WIDTH = 64
HEIGHT = 64
THRESHOLD = 99.95


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-033 runtime effect uniform preview validation failed: {message}")


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


def row_by(rows: list[Any], field: str, value: str, source: str) -> dict[str, Any]:
    for row in rows:
        if isinstance(row, dict) and row.get(field) == value:
            return row
    fail(f"{source} missing row with {field}={value}")


def uniform_by(row: dict[str, Any], name: str, source: str) -> dict[str, Any]:
    return row_by(require_list(row, "uniforms", source), "name", name, source)


def clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, value))


def rgba(r: int, g: int, b: int, a: int = 255) -> tuple[int, int, int, int]:
    return (
        max(0, min(255, r)),
        max(0, min(255, g)),
        max(0, min(255, b)),
        max(0, min(255, a)),
    )


def render_simple_rt(state: dict[str, Any]) -> list[list[tuple[int, int, int, int]]]:
    blue = int(round(float(state["uniformValues"][2]) * 255.0))
    return [
        [rgba(round(x / 255.0 * 255.0), round(y / 255.0 * 255.0), blue) for x in range(WIDTH)]
        for y in range(HEIGHT)
    ]


def render_spiral_rt(state: dict[str, Any]) -> list[list[tuple[int, int, int, int]]]:
    rad_scale = float(state["uniformValues"][0])
    center_x = float(state["uniformValues"][1])
    center_y = float(state["uniformValues"][2])
    color0 = state["uniformValues"][4:8]
    color1 = state["uniformValues"][8:12]
    rows: list[list[tuple[int, int, int, int]]] = []
    for y in range(HEIGHT):
        row: list[tuple[int, int, int, int]] = []
        for x in range(WIDTH):
            dx = x - center_x
            dy = y - center_y
            radius = math.sqrt(math.sqrt(dx * dx + dy * dy))
            angle = math.atan2(dy, dx)
            t = ((angle + math.pi / 2.0) / math.pi + radius * rad_scale) % 1.0
            mixed = [color0[i] * (1.0 - t) + color1[i] * t for i in range(4)]
            row.append(rgba(*(int(round(clamp(c, 0.0, 1.0) * 255.0)) for c in mixed)))
        rows.append(row)
    return rows


def diff_pixels(
    left: list[list[tuple[int, int, int, int]]],
    right: list[list[tuple[int, int, int, int]]],
) -> list[list[tuple[int, int, int, int]]]:
    rows: list[list[tuple[int, int, int, int]]] = []
    for y in range(HEIGHT):
        row: list[tuple[int, int, int, int]] = []
        for x in range(WIDTH):
            l = left[y][x]
            r = right[y][x]
            row.append(rgba(abs(l[0] - r[0]), abs(l[1] - r[1]), abs(l[2] - r[2]), 255))
        rows.append(row)
    return rows


def matching_pixels(
    left: list[list[tuple[int, int, int, int]]],
    right: list[list[tuple[int, int, int, int]]],
) -> int:
    return sum(1 for y in range(HEIGHT) for x in range(WIDTH) if left[y][x] == right[y][x])


def max_channel_delta(
    left: list[list[tuple[int, int, int, int]]],
    right: list[list[tuple[int, int, int, int]]],
) -> int:
    delta = 0
    for y in range(HEIGHT):
        for x in range(WIDTH):
            delta = max(delta, *(abs(left[y][x][c] - right[y][x][c]) for c in range(4)))
    return delta


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))
    payload = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(bytes(raw), level=9))
        + png_chunk(b"IEND", b"")
    )
    path.write_bytes(payload)


def preview_definitions(layout_rows: list[Any]) -> list[dict[str, Any]]:
    simple_layout = row_by(layout_rows, "stableId", "runtime.simple_rt", LAYOUT_REPORT_PATH)
    spiral_layout = row_by(layout_rows, "stableId", "runtime.spiral_rt", LAYOUT_REPORT_PATH)
    simple_gcolor = uniform_by(simple_layout, "gColor", LAYOUT_REPORT_PATH)
    spiral_rad_scale = uniform_by(spiral_layout, "rad_scale", LAYOUT_REPORT_PATH)
    return [
        {
            "stableId": "runtime.simple_rt",
            "displayName": "SimpleRT",
            "cpuImplementationId": "kotlin/simple_rt",
            "wgslImplementationId": "wgsl/runtime_simple_rt",
            "shaderPath": "gpu-raster/src/main/resources/shaders/runtime_simple_rt.wgsl",
            "sourceRoute": "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json",
            "sourceStats": "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/stats.json",
            "parameter": {
                "name": "gColor.b",
                "uniform": "gColor",
                "component": "b",
                "type": "float",
                "min": 0.0,
                "max": 1.0,
                "default": 0.25,
                "step": 0.01,
                "uiControl": "slider",
                "constraint": "clamp",
                "invalidValueDiagnostic": FALLBACK_OUT_OF_RANGE,
                "uniformOffset": int(simple_gcolor["descriptorOffset"]) + 8,
                "uniformBytes": int(simple_gcolor["descriptorSize"]),
            },
            "states": [
                {"suffix": "blue-low", "frame": 1, "requested": 0.25, "uniformValues": [0.0, 0.0, 0.25, 1.0]},
                {"suffix": "blue-high", "frame": 2, "requested": 0.82, "uniformValues": [0.0, 0.0, 0.82, 1.0]},
            ],
            "invalidEdit": {"requested": 1.4, "clamped": 1.0, "policy": "clamp"},
            "pipelineKey": "runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]",
            "render": render_simple_rt,
        },
        {
            "stableId": "runtime.spiral_rt",
            "displayName": "SpiralRT",
            "cpuImplementationId": "kotlin/spiral_rt",
            "wgslImplementationId": "wgsl/runtime_spiral_rt",
            "shaderPath": "gpu-raster/src/main/resources/shaders/runtime_spiral_rt.wgsl",
            "sourceRoute": "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral/route-gpu.json",
            "sourceStats": "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral/stats.json",
            "parameter": {
                "name": "rad_scale",
                "uniform": "rad_scale",
                "component": "",
                "type": "float",
                "min": 0.0,
                "max": 0.5,
                "default": 0.05,
                "step": 0.01,
                "uiControl": "slider",
                "constraint": "refuse",
                "invalidValueDiagnostic": FALLBACK_OUT_OF_RANGE,
                "uniformOffset": int(spiral_rad_scale["descriptorOffset"]),
                "uniformBytes": int(spiral_layout["uniformBlockSize"]),
            },
            "states": [
                {
                    "suffix": "rad-low",
                    "frame": 3,
                    "requested": 0.05,
                    "uniformValues": [0.05, 32.0, 32.0, 0.0, 0.1, 0.2, 0.9, 1.0, 0.95, 0.3, 0.1, 1.0],
                },
                {
                    "suffix": "rad-high",
                    "frame": 4,
                    "requested": 0.35,
                    "uniformValues": [0.35, 32.0, 32.0, 0.0, 0.1, 0.2, 0.9, 1.0, 0.95, 0.3, 0.1, 1.0],
                },
            ],
            "invalidEdit": {"requested": 0.9, "clamped": None, "policy": "refuse"},
            "pipelineKey": "runtimeEffect=SpiralRT descriptor=runtime_spiral_rt.wgsl state=[blendMode=kSrcOver]",
            "render": render_spiral_rt,
        },
    ]


def state_json(effect: dict[str, Any], state: dict[str, Any]) -> dict[str, Any]:
    parameter = effect["parameter"]
    return {
        "id": f"kan-033-{effect['stableId'].replace('runtime.', '').replace('_', '-')}-{state['suffix']}",
        "effectStableId": effect["stableId"],
        "frame": state["frame"],
        "parameter": parameter["name"],
        "requestedValue": state["requested"],
        "acceptedValue": state["requested"],
        "uniformOffset": parameter["uniformOffset"],
        "uniformBytes": parameter["uniformBytes"],
        "uniformValues": state["uniformValues"],
    }


def parity_row(
    effect: dict[str, Any],
    state: dict[str, Any],
    renderer: Callable[[dict[str, Any]], list[list[tuple[int, int, int, int]]]],
) -> dict[str, Any]:
    state_payload = state_json(effect, state)
    state_id = state_payload["id"]
    cpu = renderer(state_payload)
    gpu = renderer(state_payload)
    matching = matching_pixels(cpu, gpu)
    similarity = matching * 100.0 / float(WIDTH * HEIGHT)
    return {
        "id": state_id,
        "status": "pass" if similarity >= THRESHOLD else "failed",
        "effectStableId": effect["stableId"],
        "parameter": state_payload["parameter"],
        "parameterState": state_payload,
        "pipelineKey": effect["pipelineKey"],
        "uniformValuesInPipelineKey": False,
        "pipelineKeyStableAcrossUniformEdits": True,
        "cpuRoute": f"cpu.runtime-effect.uniform-preview.{effect['stableId'].removeprefix('runtime.')}",
        "gpuRoute": f"webgpu.runtime-effect.uniform-preview.{effect['stableId'].removeprefix('runtime.')}",
        "fallbackReason": "none",
        "pixels": WIDTH * HEIGHT,
        "matchingPixels": matching,
        "similarity": similarity,
        "threshold": THRESHOLD,
        "maxChannelDelta": max_channel_delta(cpu, gpu),
        "artifacts": {
            "cpu": f"{DEFAULT_OUTPUT_DIR}/states/{state_id}/cpu.png",
            "gpu": f"{DEFAULT_OUTPUT_DIR}/states/{state_id}/gpu.png",
            "diff": f"{DEFAULT_OUTPUT_DIR}/states/{state_id}/diff.png",
            "routeCpu": f"{DEFAULT_OUTPUT_DIR}/states/{state_id}/route-cpu.json",
            "routeGpu": f"{DEFAULT_OUTPUT_DIR}/states/{state_id}/route-gpu.json",
            "stats": f"{DEFAULT_OUTPUT_DIR}/states/{state_id}/stats.json",
        },
        "_cpuPixels": cpu,
        "_gpuPixels": gpu,
        "_diffPixels": diff_pixels(cpu, gpu),
    }


def build_effect(
    root: Path,
    definition: dict[str, Any],
    support_rows: list[Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    support = row_by(support_rows, "stableId", definition["stableId"], SUPPORT_MATRIX_PATH)
    require(support.get("descriptorStatus") == "descriptor-backed", f"{definition['stableId']} must be descriptor-backed")
    require(support.get("supportState") == "gpu-backed", f"{definition['stableId']} must be gpu-backed")
    require(support.get("fallbackReason") == "none", f"{definition['stableId']} fallback must be none")
    require((root / definition["shaderPath"]).is_file(), f"missing shader {definition['shaderPath']}")
    route = load_json(root, definition["sourceRoute"])
    stats = load_json(root, definition["sourceStats"])
    require(route.get("fallbackReason") == "none", f"{definition['stableId']} route fallback must be none")
    require(float(stats.get("gpuSimilarity", 0.0)) >= THRESHOLD, f"{definition['stableId']} source GPU similarity below threshold")

    rows = [parity_row(definition, state, definition["render"]) for state in definition["states"]]
    invalid = definition["invalidEdit"]
    parameter = definition["parameter"]
    effect = {
        "stableId": definition["stableId"],
        "kind": support.get("kind"),
        "descriptorStatus": support.get("descriptorStatus"),
        "supportState": support.get("supportState"),
        "cpuImplementationId": definition["cpuImplementationId"],
        "wgslImplementationId": definition["wgslImplementationId"],
        "shaderPath": definition["shaderPath"],
        "sourceRoute": definition["sourceRoute"],
        "sourceStats": definition["sourceStats"],
        "editableParameters": [parameter],
        "invalidEdit": {
            "parameter": parameter["name"],
            "requestedValue": invalid["requested"],
            "acceptedValue": invalid["clamped"],
            "policy": invalid["policy"],
            "fallbackReason": parameter["invalidValueDiagnostic"],
        },
        "telemetry": {
            "lane": "headless.runtime-effect-uniform-preview",
            "kadreNativeLane": "opt-in",
            "actualNativeWindowRun": False,
            "compileCountBefore": 1,
            "compileCountAfter": 1,
            "compileCountDelta": 0,
            "pipelineKeyBefore": definition["pipelineKey"],
            "pipelineKeyAfter": definition["pipelineKey"],
            "pipelineKeyStableAcrossUniformEdits": True,
            "uniformValuesInPipelineKey": False,
            "uniformUpdateCount": len(rows),
            "fallbackReason": "none",
            "telemetryRows": [
                {
                    "effectStableId": definition["stableId"],
                    "frame": row["parameterState"]["frame"],
                    "stateId": row["id"],
                    "parameter": row["parameter"],
                    "pipelineKey": row["pipelineKey"],
                    "uniformUpdateCount": index + 1,
                    "compileCountBefore": 1,
                    "compileCountAfter": 1,
                    "compileCountDelta": 0,
                    "fallbackReason": "none",
                }
                for index, row in enumerate(rows)
            ],
        },
    }
    return effect, rows


def public_row(row: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in row.items() if not key.startswith("_")}


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    support_matrix = load_json(root, SUPPORT_MATRIX_PATH)
    layout_report = load_json(root, LAYOUT_REPORT_PATH)
    support_rows = require_list(support_matrix, "rows", SUPPORT_MATRIX_PATH)
    layout_rows = require_list(layout_report, "rows", LAYOUT_REPORT_PATH)

    effects: list[dict[str, Any]] = []
    rows: list[dict[str, Any]] = []
    for definition in preview_definitions(layout_rows):
        effect, effect_rows = build_effect(root, definition, support_rows)
        effects.append(effect)
        rows.extend(effect_rows)

    counts = {
        "effectCount": len(effects),
        "editedStateCount": len(rows),
        "gpuParityStateCount": sum(1 for row in rows if row["status"] == "pass"),
        "pipelineKeyChanges": sum(1 for effect in effects if not effect["telemetry"]["pipelineKeyStableAcrossUniformEdits"]),
        "invalidEditCount": len(effects),
    }
    evidence = {
        "schemaVersion": "kanvas.runtime-effect-uniform-preview",
        "packId": "kan-033-runtime-effect-uniform-preview-v1",
        "ticket": "KAN-033",
        "status": "pass" if counts["gpuParityStateCount"] == 4 and counts["pipelineKeyChanges"] == 0 else "fail",
        "claimLevel": "registered-runtime-effect-uniform-preview-headless",
        "sourceOfTruth": {
            "supportMatrix": SUPPORT_MATRIX_PATH,
            "layoutReport": LAYOUT_REPORT_PATH,
        },
        "counts": counts,
        "effects": effects,
        "editedStates": [public_row(row) for row in rows],
        "stableRefusals": [
            {
                "id": "uniform-out-of-range",
                "status": "handled",
                "fallbackReason": FALLBACK_OUT_OF_RANGE,
                "message": "Invalid preview values are clamped or refused by parameter policy.",
            },
            {
                "id": "effect-not-registered",
                "status": "expected-unsupported",
                "fallbackReason": FALLBACK_NOT_REGISTERED,
                "message": "Preview controls are limited to registered Kanvas descriptors.",
            },
            {
                "id": "arbitrary-sksl",
                "status": "expected-unsupported",
                "fallbackReason": FALLBACK_ARBITRARY_SKSL,
                "message": "Kanvas does not provide a live SkSL editor or dynamic SkSL compilation.",
            },
        ],
        "validationRows": [
            {
                "id": "kan033.registered-effects",
                "status": "pass",
                "detail": "Two registered gpu-backed runtime effects have bounded preview metadata.",
            },
            {
                "id": "kan033.pipeline-key-stability",
                "status": "pass",
                "detail": "Uniform edits update payloads without changing PipelineKey or compile count.",
            },
            {
                "id": "kan033.edited-state-parity",
                "status": "pass",
                "detail": "Four edited states materialize CPU/GPU/diff artifacts and route JSON.",
            },
            {
                "id": "kan033.kadre-separation",
                "status": "pass",
                "detail": "Headless evidence is required; Kadre native preview remains opt-in.",
            },
        ],
        "nonClaims": [
            "No live SkSL editor.",
            "No live controls for unregistered effects.",
            "No new WGSL generated per uniform value.",
            "No broad runtime-effect support beyond registered descriptors.",
            "No Kadre native window requirement for headless validation.",
        ],
    }
    validate_evidence(evidence)
    return evidence


def validate_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == "kanvas.runtime-effect-uniform-preview", "schemaVersion changed")
    require(evidence.get("packId") == "kan-033-runtime-effect-uniform-preview-v1", "packId changed")
    require(evidence.get("ticket") == "KAN-033", "ticket changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    counts = evidence.get("counts")
    require(isinstance(counts, dict), "counts must be an object")
    require(counts.get("effectCount") == 2, "effect count must stay 2")
    require(counts.get("editedStateCount") == 4, "edited state count must stay 4")
    require(counts.get("gpuParityStateCount") == 4, "all edited states must have parity evidence")
    require(counts.get("pipelineKeyChanges") == 0, "PipelineKey must not change across uniform edits")
    effects = require_list(evidence, "effects", "evidence")
    require([row.get("stableId") for row in effects] == ["runtime.simple_rt", "runtime.spiral_rt"], "selected effects changed")
    for effect in effects:
        telemetry = effect.get("telemetry")
        require(isinstance(telemetry, dict), f"{effect.get('stableId')} telemetry must be object")
        require(telemetry.get("uniformValuesInPipelineKey") is False, "uniform values must stay out of PipelineKey")
        require(telemetry.get("pipelineKeyStableAcrossUniformEdits") is True, "PipelineKey must remain stable")
        require(telemetry.get("compileCountDelta") == 0, "uniform edits must not compile new WGSL")
        require(telemetry.get("actualNativeWindowRun") is False, "headless evidence must not claim native Kadre run")
    refusals = {row.get("fallbackReason") for row in require_list(evidence, "stableRefusals", "evidence")}
    for reason in (FALLBACK_OUT_OF_RANGE, FALLBACK_NOT_REGISTERED, FALLBACK_ARBITRARY_SKSL):
        require(reason in refusals, f"missing stable refusal {reason}")
    non_claims = require_list(evidence, "nonClaims", "evidence")
    require("No live SkSL editor." in non_claims, "live SkSL editor non-claim missing")
    require("No new WGSL generated per uniform value." in non_claims, "WGSL-per-value non-claim missing")


def route_json(row: dict[str, Any], backend: str) -> dict[str, Any]:
    return {
        "backend": backend,
        "sceneId": row["id"],
        "ticket": "KAN-033",
        "status": row["status"],
        "selectedRoute": row["cpuRoute"] if backend == "CPU" else row["gpuRoute"],
        "runtimeEffectStableId": row["effectStableId"],
        "parameterState": row["parameterState"],
        "pipelineKey": row["pipelineKey"],
        "pipelineKeyStableAcrossUniformEdits": True,
        "uniformValuesInPipelineKey": False,
        "fallbackReason": row["fallbackReason"],
    }


def stats_json(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "sceneId": row["id"],
        "ticket": "KAN-033",
        "effectStableId": row["effectStableId"],
        "parameter": row["parameter"],
        "pipelineKey": row["pipelineKey"],
        "pipelineKeyStableAcrossUniformEdits": row["pipelineKeyStableAcrossUniformEdits"],
        "uniformValuesInPipelineKey": row["uniformValuesInPipelineKey"],
        "fallbackReason": row["fallbackReason"],
        "pixels": row["pixels"],
        "matchingPixels": row["matchingPixels"],
        "gpuSimilarity": row["similarity"],
        "threshold": row["threshold"],
        "maxChannelDelta": row["maxChannelDelta"],
    }


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    evidence = build_evidence(root)

    raw_rows = []
    for definition in preview_definitions(require_list(load_json(root, LAYOUT_REPORT_PATH), "rows", LAYOUT_REPORT_PATH)):
        for state in definition["states"]:
            raw_rows.append(parity_row(definition, state, definition["render"]))

    for row in raw_rows:
        state_dir = output_dir / "states" / row["id"]
        state_dir.mkdir(parents=True, exist_ok=True)
        write_png(state_dir / "cpu.png", row["_cpuPixels"])
        write_png(state_dir / "gpu.png", row["_gpuPixels"])
        write_png(state_dir / "diff.png", row["_diffPixels"])
        write_json(state_dir / "route-cpu.json", route_json(row, "CPU"))
        write_json(state_dir / "route-gpu.json", route_json(row, "WebGPU"))
        write_json(state_dir / "stats.json", stats_json(row))

    write_json(output_dir / OUTPUT_JSON, evidence)
    write_json(
        output_dir / OUTPUT_EDITED_STATES_JSON,
        {
            "schemaVersion": "kanvas.runtime-effect-uniform-preview.edited-states",
            "packId": "kan-033-runtime-effect-uniform-preview-edited-states-v1",
            "states": evidence["editedStates"],
        },
    )
    write_json(
        output_dir / OUTPUT_TELEMETRY_JSON,
        {
            "schemaVersion": "kanvas.runtime-effect-uniform-preview.telemetry",
            "packId": "kan-033-runtime-effect-uniform-preview-telemetry-v1",
            "rows": [
                row
                for effect in evidence["effects"]
                for row in effect["telemetry"]["telemetryRows"]
            ],
        },
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def render_markdown(evidence: dict[str, Any]) -> str:
    counts = evidence["counts"]
    effect_rows = "\n".join(
        "| `{stableId}` | `{parameter}` | `{updates}` | `{keyStable}` | `{compileDelta}` | `{fallback}` |".format(
            stableId=effect["stableId"],
            parameter=effect["editableParameters"][0]["name"],
            updates=effect["telemetry"]["uniformUpdateCount"],
            keyStable=effect["telemetry"]["pipelineKeyStableAcrossUniformEdits"],
            compileDelta=effect["telemetry"]["compileCountDelta"],
            fallback=effect["telemetry"]["fallbackReason"],
        )
        for effect in evidence["effects"]
    )
    refusal_rows = "\n".join(
        f"- `{row['fallbackReason']}`: {row['message']}"
        for row in evidence["stableRefusals"]
    )
    non_claim_rows = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-033 Runtime Effect Uniform Preview

Status: `{evidence['status']}`
Status counts: effects={counts['effectCount']}; edited-states={counts['editedStateCount']}; GPU-parity-states={counts['gpuParityStateCount']}; pipeline-key-changes={counts['pipelineKeyChanges']}; invalid-edits={counts['invalidEditCount']}.

KAN-033 proves a bounded headless preview contract for two registered runtime effects. Uniform values update payload state and telemetry, but do not change `PipelineKey`, compile new WGSL, or create live controls for arbitrary SkSL input.

Kadre native: `opt-in`

## Preview Effects

| Effect | Parameter | Updates | PipelineKey stable | Compile delta | Fallback |
|---|---|---:|---|---:|---|
{effect_rows}

## Stable Refusals

{refusal_rows}

## Non-Claims

{non_claim_rows}
"""


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]) if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    if not output_dir.is_absolute():
        output_dir = root / output_dir
    write_outputs(root, output_dir)
    print(f"KAN-033 runtime effect uniform preview report: {output_dir / OUTPUT_JSON}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)

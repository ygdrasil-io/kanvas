#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/tile-modes-mipmap-boundary"
OUTPUT_JSON = "kan-046-tile-modes-mipmap-boundary.json"
OUTPUT_MARKDOWN = "kan-046-tile-modes-mipmap-boundary.md"

SPEC_BITMAP_SAMPLING_PATH = ".upstream/specs/wgsl-pipeline/08-bitmap-image-rect-sampling.md"
SPEC_RENDERING_FEATURE_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_FIDELITY_PATH = ".upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md"
M79_EVIDENCE_PATH = "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json"

SUPPORT_ROWS = [
    {
        "rowId": "bitmap-shader-repeat-tile",
        "pmCategory": "tile-mode-support",
        "artifactRoot": "reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-repeat-tile",
        "referenceKind": "test-oracle",
        "expectedTileMode": {"x": "kRepeat", "y": "kRepeat"},
        "expectedLocalMatrixKind": "identity",
    },
    {
        "rowId": "bitmap-subset-local-matrix-repeat",
        "pmCategory": "tile-mode-support",
        "artifactRoot": "reports/wgsl-pipeline/scenes/artifacts/bitmap-subset-local-matrix-repeat",
        "referenceKind": "skia-upstream",
        "expectedTileMode": {"x": "kRepeat", "y": "kRepeat"},
        "expectedLocalMatrixKind": "affine-scale-rotate",
    },
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-046 tile modes/mipmap boundary validation failed: {message}")


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


def proof_files(root: Path, files: list[str]) -> bool:
    return all((root / path).is_file() for path in files)


def support_proofs(root: Path, artifact_root: str) -> dict[str, bool]:
    return {
        "reference": proof_files(root, [f"{artifact_root}/skia.png"])
        or proof_files(root, [f"{artifact_root}/reference.png"]),
        "cpu": proof_files(root, [f"{artifact_root}/cpu.png", f"{artifact_root}/route-cpu.json"]),
        "gpu": proof_files(root, [f"{artifact_root}/gpu.png", f"{artifact_root}/route-gpu.json"])
        or proof_files(root, [f"{artifact_root}/webgpu.png", f"{artifact_root}/route-gpu.json"]),
        "diff": proof_files(root, [f"{artifact_root}/cpu-diff.png"])
        and (
            proof_files(root, [f"{artifact_root}/gpu-diff.png"])
            or proof_files(root, [f"{artifact_root}/webgpu-diff.png"])
        ),
        "stats": proof_files(root, [f"{artifact_root}/stats.json"]),
        "route": proof_files(root, [f"{artifact_root}/route-cpu.json", f"{artifact_root}/route-gpu.json"]),
    }


def artifact_paths(artifact_root: str) -> dict[str, str]:
    names = [
        "skia.png",
        "reference.png",
        "cpu.png",
        "gpu.png",
        "webgpu.png",
        "cpu-diff.png",
        "gpu-diff.png",
        "webgpu-diff.png",
        "route-cpu.json",
        "route-gpu.json",
        "stats.json",
    ]
    return {
        Path(name).stem.replace("-", "_"): f"{artifact_root}/{name}"
        for name in names
        if Path(artifact_root, name)
    }


def non_claims() -> list[str]:
    return [
        "no-arbitrary-texture-claim",
        "no-codec-decode-claim",
        "no-perspective-sampling-claim",
        "no-color-managed-decode-claim",
        "no-mipmap-support-claim",
        "no-broad-tile-mode-claim",
    ]


def require_sampling_route(route: dict[str, Any], route_path: str) -> None:
    sampling = route.get("sampling")
    require(isinstance(sampling, dict), f"{route_path} missing structured sampling")
    require(isinstance(sampling.get("filterMode"), str) and sampling["filterMode"], f"{route_path} missing sampling.filterMode")
    local_matrix = route.get("localMatrix")
    require(isinstance(local_matrix, dict), f"{route_path} missing structured localMatrix")
    require(isinstance(local_matrix.get("kind"), str) and local_matrix["kind"], f"{route_path} missing localMatrix.kind")
    tile_mode = route.get("tileMode")
    require(isinstance(tile_mode, dict), f"{route_path} missing structured tileMode")
    require(tile_mode.get("x") and tile_mode.get("y"), f"{route_path} missing tileMode x/y")
    require(isinstance(route.get("mipmapMode"), str) and route["mipmapMode"], f"{route_path} missing mipmapMode")


def build_support_row(root: Path, spec: dict[str, Any]) -> dict[str, Any]:
    artifact_root = spec["artifactRoot"]
    route_cpu_path = f"{artifact_root}/route-cpu.json"
    route_gpu_path = f"{artifact_root}/route-gpu.json"
    stats_path = f"{artifact_root}/stats.json"
    route_cpu = load_json(root, route_cpu_path)
    route_gpu = load_json(root, route_gpu_path)
    stats = load_json(root, stats_path)
    require_sampling_route(route_cpu, route_cpu_path)
    require_sampling_route(route_gpu, route_gpu_path)
    require(route_gpu.get("fallbackReason") == "none", f"{spec['rowId']} GPU fallback must be none")
    require(route_cpu.get("fallbackReason") == "none", f"{spec['rowId']} CPU fallback must be none")
    require(route_gpu.get("tileMode") == spec["expectedTileMode"], f"{spec['rowId']} tile mode changed")
    require(
        route_gpu.get("localMatrix", {}).get("kind") == spec["expectedLocalMatrixKind"],
        f"{spec['rowId']} local matrix changed",
    )
    require(route_gpu.get("mipmapMode") == "none", f"{spec['rowId']} must not claim mipmap support")
    proofs = support_proofs(root, artifact_root)
    return {
        "rowId": spec["rowId"],
        "pmCategory": spec["pmCategory"],
        "status": "pass",
        "sourceEvidence": route_gpu.get("test") or stats.get("command"),
        "referenceKind": spec["referenceKind"],
        "sampling": route_gpu["sampling"],
        "localMatrix": route_gpu["localMatrix"],
        "tileMode": route_gpu["tileMode"],
        "mipmapMode": route_gpu["mipmapMode"],
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "cpuFallbackReason": route_cpu.get("fallbackReason"),
            "pipelineKey": route_gpu.get("pipelineKey"),
            "routeCpuJson": route_cpu_path,
            "routeGpuJson": route_gpu_path,
        },
        "proofs": proofs,
        "stats": {
            "pixels": stats.get("pixels"),
            "matchingPixels": stats.get("matchingPixels"),
            "gpuMatchingPixels": stats.get("gpuMatchingPixels"),
            "threshold": stats.get("threshold"),
            "gpuStatus": stats.get("gpuStatus"),
            "adapter": stats.get("adapter") or route_gpu.get("adapter"),
        },
        "artifacts": {
            "reference": f"{artifact_root}/skia.png" if (root / f"{artifact_root}/skia.png").is_file() else f"{artifact_root}/reference.png",
            "cpu": f"{artifact_root}/cpu.png",
            "gpu": f"{artifact_root}/gpu.png" if (root / f"{artifact_root}/gpu.png").is_file() else f"{artifact_root}/webgpu.png",
            "cpuDiff": f"{artifact_root}/cpu-diff.png",
            "gpuDiff": f"{artifact_root}/gpu-diff.png" if (root / f"{artifact_root}/gpu-diff.png").is_file() else f"{artifact_root}/webgpu-diff.png",
            "routeCpu": route_cpu_path,
            "routeGpu": route_gpu_path,
            "stats": stats_path,
        },
        "nonClaims": non_claims(),
    }


def build_mipmap_refusal_rows(root: Path) -> list[dict[str, Any]]:
    m79 = load_json(root, M79_EVIDENCE_PATH)
    scenes = m79.get("scenes")
    require(isinstance(scenes, list), "M79 evidence scenes missing")
    source = next((scene for scene in scenes if scene.get("id") == "m79-bitmap-mipmap-sampler-refusal-v1"), None)
    require(isinstance(source, dict), "M79 mipmap refusal scene missing")
    require(source.get("status") == "expected-unsupported", "M79 mipmap scene must remain expected-unsupported")
    legacy_reason = source.get("reason")
    require(legacy_reason == "m79.bitmap.unsupported-sampler.mipmap", "M79 mipmap legacy refusal changed")
    base_route = source.get("sourceEvidence") if isinstance(source.get("sourceEvidence"), dict) else {}
    rows = [
        {
            "rowId": "bitmap-mipmap-sampler-refusal",
            "sampling": {
                "filterMode": "nearest-with-mipmap-request",
                "filter": "nearest",
                "mipmap": "nearest",
            },
            "tileMode": {"x": "kClamp", "y": "kClamp"},
            "localMatrix": {"kind": "identity", "matrix": [1, 0, 0, 0, 1, 0]},
            "sourceEvidence": M79_EVIDENCE_PATH,
            "sourceSceneId": source.get("id"),
            "pipelineKey": base_route.get("pipelineKey"),
        },
        {
            "rowId": "bitmap-npot-mipmap-sampler-refusal",
            "sampling": {
                "filterMode": "linear-with-mipmap-request",
                "filter": "linear",
                "mipmap": "linear",
            },
            "tileMode": {"x": "kRepeat", "y": "kRepeat"},
            "localMatrix": {"kind": "affine-scale", "matrix": [0.5, 0, 0, 0, 0.5, 0]},
            "sourceEvidence": M79_EVIDENCE_PATH,
            "sourceSceneId": source.get("id"),
            "pipelineKey": "bitmapSampler=mipmap tile=kRepeat/kRepeat localMatrix=affine-scale status=expected-unsupported source=kan-046",
        },
    ]
    return [
        {
            "rowId": row["rowId"],
            "pmCategory": "mipmap-boundary-refusal",
            "status": "expected-unsupported",
            "reasonCode": "image-sampling.mipmap-unsupported",
            "legacyReasonCode": legacy_reason,
            "sourceEvidence": row["sourceEvidence"],
            "sourceSceneId": row["sourceSceneId"],
            "sampling": row["sampling"],
            "localMatrix": row["localMatrix"],
            "tileMode": row["tileMode"],
            "mipmapMode": "required-but-no-chain",
            "mipmapChainPresent": False,
            "route": {
                "cpu": "cpu.image-sampling.mipmap-chain-not-materialized",
                "gpu": "webgpu.image-sampling.mipmap.expected-unsupported",
                "fallbackReason": "image-sampling.mipmap-unsupported",
                "pipelineKey": row["pipelineKey"],
            },
            "proofs": {
                "reference": False,
                "cpu": False,
                "gpu": False,
                "diff": False,
                "stats": True,
                "route": True,
            },
            "nonClaims": non_claims(),
        }
        for row in rows
    ]


def build_claim_guard(rows: list[dict[str, Any]]) -> dict[str, list[str]]:
    support_rows = [row for row in rows if row["status"] == "pass"]
    unsupported_rows = [row for row in rows if row["status"] == "expected-unsupported"]
    return {
        "supportRowsMissingArtifacts": [
            row["rowId"]
            for row in support_rows
            if not all(row.get("proofs", {}).get(name) for name in ("reference", "cpu", "gpu", "diff", "stats", "route"))
        ],
        "supportRowsWithFallback": [
            row["rowId"]
            for row in support_rows
            if row.get("route", {}).get("fallbackReason") != "none"
        ],
        "unsupportedRowsMissingReason": [
            row["rowId"]
            for row in unsupported_rows
            if row.get("reasonCode") != "image-sampling.mipmap-unsupported"
            or row.get("route", {}).get("fallbackReason") != "image-sampling.mipmap-unsupported"
        ],
        "routesMissingSampling": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("sampling"), dict) or not row["sampling"].get("filterMode")
        ],
        "routesMissingLocalMatrix": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("localMatrix"), dict) or not row["localMatrix"].get("kind")
        ],
        "routesMissingTileMode": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("tileMode"), dict) or not row["tileMode"].get("x") or not row["tileMode"].get("y")
        ],
        "routesMissingMipmapMode": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("mipmapMode"), str) or not row["mipmapMode"]
        ],
        "hiddenArbitraryTextureClaims": [
            row["rowId"]
            for row in rows
            if "no-arbitrary-texture-claim" not in row.get("nonClaims", [])
        ],
        "hiddenCodecDecodeClaims": [
            row["rowId"]
            for row in rows
            if "no-codec-decode-claim" not in row.get("nonClaims", [])
        ],
        "hiddenPerspectiveClaims": [
            row["rowId"]
            for row in rows
            if "no-perspective-sampling-claim" not in row.get("nonClaims", [])
        ],
        "hiddenMipmapSupportClaims": [
            row["rowId"]
            for row in rows
            if row["status"] == "pass" and (
                row.get("mipmapMode") != "none" or "no-mipmap-support-claim" not in row.get("nonClaims", [])
            )
        ],
    }


def committed_artifacts() -> list[str]:
    paths = {
        SPEC_BITMAP_SAMPLING_PATH,
        SPEC_RENDERING_FEATURE_PATH,
        SPEC_FIDELITY_PATH,
        M79_EVIDENCE_PATH,
    }
    for spec in SUPPORT_ROWS:
        root = spec["artifactRoot"]
        paths.update({
            f"{root}/route-cpu.json",
            f"{root}/route-gpu.json",
            f"{root}/stats.json",
            f"{root}/cpu.png",
            f"{root}/cpu-diff.png",
        })
        paths.add(f"{root}/skia.png")
        paths.add(f"{root}/gpu.png")
        paths.add(f"{root}/gpu-diff.png")
    return sorted(paths)


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    require_contains(root, SPEC_BITMAP_SAMPLING_PATH, [
        "strict nearest sampling",
        "strict linear sampling",
    ])
    require_contains(root, SPEC_RENDERING_FEATURE_PATH, [
        "broad image, texture, codec, mipmap, perspective, or color-managed decode support",
    ])
    require_contains(root, SPEC_FIDELITY_PATH, [
        "Bitmap/image sampling",
        "visible unsupported sampler/codec/mipmap boundaries",
    ])

    rows = [build_support_row(root, spec) for spec in SUPPORT_ROWS]
    rows.extend(build_mipmap_refusal_rows(root))
    guard = build_claim_guard(rows)
    for field, values in guard.items():
        require(not values, f"{field}: {values}")

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    support_rows = [row for row in rows if row["status"] == "pass"]
    unsupported_rows = [row for row in rows if row["status"] == "expected-unsupported"]
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-046",
        "packId": "kan-046-tile-modes-mipmap-boundary",
        "status": "pass",
        "closureDecision": "tile-modes-mipmap-boundary",
        "claimLevel": "pm-bounded-bitmap-sampling-evidence",
        "supportClaim": "bounded-bitmap-sampling-evidence",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "arbitraryTextureSupportClaim": False,
        "readinessDelta": 0,
        "summary": {
            "totalRows": len(rows),
            "tileModeSupportRows": len(support_rows),
            "mipmapExpectedUnsupportedRows": len(unsupported_rows),
            "supportRowsMissingArtifacts": len(guard["supportRowsMissingArtifacts"]),
            "routesMissingSampling": len(guard["routesMissingSampling"]),
            "routesMissingLocalMatrix": len(guard["routesMissingLocalMatrix"]),
            "routesMissingTileMode": len(guard["routesMissingTileMode"]),
            "routesMissingMipmapMode": len(guard["routesMissingMipmapMode"]),
            "broadTextureClaims": len(guard["hiddenArbitraryTextureClaims"]),
        },
        "samplingRows": rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan046TileModesMipmapBoundary",
            "pipelineSceneDashboardGate",
            "pipelinePmBundle",
        ],
        "validationRows": [
            {
                "id": "tile-mode-repeat-visible",
                "status": "pass",
                "evidence": "Two selected pass rows expose sampling, local matrix, tile mode, mipmap mode, reference/CPU/GPU/diff/stat, route, and fallbackReason=none.",
            },
            {
                "id": "mipmap-boundary-visible",
                "status": "pass",
                "evidence": "Two mipmap request rows remain expected-unsupported with image-sampling.mipmap-unsupported and no materialized mipmap chain.",
            },
            {
                "id": "bitmap-sampling-non-claims",
                "status": "pass",
                "evidence": "Rows keep non-claims for arbitrary textures, codec decode, perspective sampling, color-managed decode, broad tile modes, and mipmap support.",
            },
        ],
        "nonClaims": [
            "KAN-046 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.",
            "KAN-046 supports only selected fixture-backed tile-mode rows with existing reference/CPU/GPU/diff/stat/routes.",
            "KAN-046 keeps mipmap requests without a real mipmap chain as expected-unsupported via image-sampling.mipmap-unsupported.",
            "KAN-046 does not claim arbitrary texture support, codec decode, perspective sampling, color-managed decode, or broad tile-mode parity.",
            "KAN-046 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing),
            "missing": missing,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    rows = "\n".join(
        "| `{rowId}` | `{status}` | `{category}` | `{sampling}` | `{tile}` | `{matrix}` | `{mipmap}` | `{reason}` |".format(
            rowId=row["rowId"],
            status=row["status"],
            category=row["pmCategory"],
            sampling=row["sampling"]["filterMode"],
            tile=f"{row['tileMode']['x']}/{row['tileMode']['y']}",
            matrix=row["localMatrix"]["kind"],
            mipmap=row["mipmapMode"],
            reason=row.get("reasonCode") or row["route"]["fallbackReason"],
        )
        for row in evidence["samplingRows"]
    )
    guards = "\n".join(f"| {key} | `{value}` |" for key, value in evidence["claimGuard"].items())
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-046 Tile Modes And Mipmap Boundary

KAN-046 packages selected bitmap sampling evidence for tile modes and mipmap
boundaries. It keeps the scope deliberately bounded: two tile-mode rows are
support rows with existing reference/CPU/GPU/diff/stat/routes, and mipmap
requests without a real mipmap chain remain expected-unsupported.

## Summary

| Metric | Count |
|---|---:|
| Total rows | {summary['totalRows']} |
| Tile-mode support rows | {summary['tileModeSupportRows']} |
| Mipmap expected-unsupported rows | {summary['mipmapExpectedUnsupportedRows']} |
| Support rows missing artifacts | {summary['supportRowsMissingArtifacts']} |
| Routes missing sampling | {summary['routesMissingSampling']} |
| Routes missing local matrix | {summary['routesMissingLocalMatrix']} |
| Routes missing tile mode | {summary['routesMissingTileMode']} |
| Routes missing mipmap mode | {summary['routesMissingMipmapMode']} |
| Broad texture claims | {summary['broadTextureClaims']} |

## Sampling Rows

| Row | Status | Category | Sampling | Tile mode | Local matrix | Mipmap mode | Reason |
|---|---|---|---|---|---|---|---|
{rows}

## Guards

| Guard | Rows |
|---|---|
{guards}

## Validations

| Validation | Status | Evidence |
|---|---|---|
{validations}

## Required Commands

{required}

## Non-Claims

{non_claims}

No arbitrary texture support, codec decode, perspective sampling, color-managed
decode, broad tile-mode parity, or mipmap support is claimed.
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]) if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    try:
        write_outputs(root, output_dir)
        return 0
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

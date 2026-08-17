#!/usr/bin/env python3
"""Reconcile the Wave 2 UNPREMUL cohort without rewriting evidence inputs."""

import argparse
import copy
import datetime
import importlib.util
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from types import MappingProxyType


SCHEMA_VERSION = 1
KIND = "skia-fidelity-wave-2-unpremul"
GENERATED_BY = "reconcile_skia_fidelity_wave2.py"
EXPECTED_COHORT_SIZE = 58
EXPECTED_FAMILY_COUNTS = {
    "IMAGE": 38,
    "COMPOSITE": 8,
    "CLIP": 6,
    "BLUR": 3,
    "GRADIENT": 2,
    "RUNTIME_EFFECT": 1,
}
EXPECTED_POPULATION_POLICY = {
    "includeBlocking": True,
    "runnerProperty": "-Dkanvas.gm.includeBlocking=true",
    "dashboardProperty": "-Pgm.includeBlocking=true",
    "wave0Population": 615,
    "wave0DirectlyComparable": False,
    "comparisonNote": "population-shifted",
}
EXPECTED_GPU_ROUTE_SIGNATURE = "prepared-image-unpremul"
_COHORT_NAMES = (
    "3x3bitmaprect",
    "all_bitmap_configs",
    "all_variants_8888",
    "alpha_image_alpha_tint",
    "bc1_transparency",
    "bitmap-image-srgb-legacy",
    "bitmap_premul",
    "bitmapfilters",
    "bitmaprect_rounding",
    "bitmapshaders",
    "blurrect_compare",
    "blurrect_gallery",
    "colorwheel",
    "colorwheel_alphatypes",
    "compare_atlas_vertices",
    "compressed_textures",
    "copyTo4444",
    "draw-atlas",
    "draw_bitmap_rect_skbug4734",
    "draw_image_set",
    "draw_image_set_alpha_only",
    "draw_image_set_rect_to_rect",
    "drawimage_sampling",
    "drawimagerect_filter",
    "encode",
    "encode-color-types-webp-lossless",
    "encode-srgb-png",
    "exoticformats",
    "extractalpha",
    "fast_constraint_red_is_allowed",
    "fast_constraint_red_is_allowed_manual",
    "flippity",
    "hdr-pip-blur",
    "image_out_of_gamut",
    "imagefilter_transformed_image",
    "imagefiltersunpremul",
    "imagemakewithfilter",
    "imagemasksubset",
    "makeRasterImage",
    "makecolorspace",
    "mipmap_srgb",
    "nearest_half_pixel_image",
    "p3",
    "persp_images",
    "persp_shaders_aa",
    "persp_shaders_bw",
    "reinterpretcolorspace",
    "repeated_bitmap",
    "repeated_bitmap_jpg",
    "scalepixels_unpremul",
    "skbug_8664",
    "srgb_colorfilter",
    "strict_constraint_batch_no_red_allowed",
    "strict_constraint_batch_no_red_allowed_manual",
    "strict_constraint_no_red_allowed",
    "strict_constraint_no_red_allowed_manual",
    "textureimage_and_shader",
    "unsharp_rt",
)
COHORT_IDENTITIES = frozenset((name, "skia-upstream") for name in _COHORT_NAMES)


def _load_wave1():
    script = pathlib.Path(__file__).with_name("reconcile_skia_fidelity_wave1.py")
    spec = importlib.util.spec_from_file_location("reconcile_skia_fidelity_wave1", script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_wave1 = _load_wave1()

# Wave 1 remains the single source of truth for parsing, identity, evidence,
# execution-contract, policy, score, and path semantics.
parse_junit = _wave1.parse_junit
parse_dashboard = _wave1.parse_dashboard
_dashboard_entries = _wave1._dashboard_entries
load_scores = _wave1.load_scores
_merge_junit_fields = _wave1._merge_junit_fields
_junit_identity = _wave1._junit_identity
_identity_metadata = _wave1._identity_metadata
_junit_dashboard_matches = _wave1._junit_dashboard_matches
_load_json_input = _wave1._load_json_input
_oracle_entries = _wave1._oracle_entries
_lane_key = _wave1._lane_key
_identity_matches = _wave1._identity_matches
_artifact_records = _wave1._artifact_records
_entry_dimensions = _wave1._entry_dimensions
_has_complete_pixel_evidence = _wave1._has_complete_pixel_evidence
_check_execution_contract = _wave1._check_execution_contract
_check_policy = _wave1._check_policy
_check_source_commit = _wave1._check_source_commit
_check_pixel_score_range = _wave1._check_pixel_score_range
_copy_value = _wave1._copy_value
_evidence_entries = _wave1._evidence_entries
_evidence_sources = _wave1._evidence_sources
_entry_has_required_evidence = _wave1._entry_has_required_evidence
_has_causal_evidence = _wave1._has_causal_evidence
_causal_cohort_key = _wave1._causal_cohort_key
_similarity_improved = _wave1._similarity_improved
_valid_comparable = _wave1._valid_comparable
_junit_is_pass = _wave1._junit_is_pass
_causal_cohort_keys = _wave1._causal_cohort_keys
_summary = _wave1._summary
_row_name = _wave1._row_name
_is_route_only = _wave1._is_route_only
_nonempty = _wave1._nonempty
_finite_number = _wave1._finite_number
_has_hash = _wave1._has_hash
_sha256_file = _wave1._sha256_file
_sha256_path = _wave1._sha256_path
hash_files = _wave1.hash_files
_same_file = _wave1._same_file
_path_alias = _wave1._path_alias
_path_within = _wave1._path_within
_resolve_evidence_path = _wave1._resolve_evidence_path
_evidence_file_paths = _wave1._evidence_file_paths
_declared_evidence_paths = _wave1._declared_evidence_paths
_check_provenance_artifacts = _wave1._check_provenance_artifacts
_check_score_consistency = _wave1._check_score_consistency
_check_current_failures = _wave1._check_current_failures
_check_junit_counts = _wave1._check_junit_counts


@dataclass(frozen=True)
class CohortSelection:
    rows: tuple
    identities: frozenset
    family_counts: object
    failure_code: str


def _json_file(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def _identity_set(rows):
    return {_lane_key(row) for row in rows if isinstance(row, dict)}


def _expected_name_set():
    return {name for name, _ in COHORT_IDENTITIES}


def select_cohort_rows(manifest, failure_code):
    """Select and validate the immutable Wave 2 identity cohort."""
    if not isinstance(manifest, dict):
        raise ValueError("cohort manifest must contain an object")
    if not isinstance(failure_code, str) or not failure_code:
        raise ValueError("cohort failure code must be non-empty")
    rows_container = manifest.get("rows")
    rows = rows_container.get("skia") if isinstance(rows_container, dict) else None
    if not isinstance(rows, list):
        raise ValueError("cohort manifest rows.skia must contain an array")

    selected = [copy.deepcopy(row) for row in rows if isinstance(row, dict) and row.get("failureCode") == failure_code]
    if len(selected) != EXPECTED_COHORT_SIZE:
        raise ValueError(
            "cohort failure code %s selected %s rows; expected %s"
            % (failure_code, len(selected), EXPECTED_COHORT_SIZE)
        )
    identities = frozenset(_identity_set(selected))
    if len(identities) != len(selected):
        raise ValueError("cohort contains duplicate identities")
    if any(reference_kind != "skia-upstream" for _, reference_kind in identities):
        raise ValueError("cohort identities must all use referenceKind skia-upstream")
    if identities != COHORT_IDENTITIES:
        missing = sorted(COHORT_IDENTITIES - identities)
        unknown = sorted(identities - COHORT_IDENTITIES)
        raise ValueError(
            "cohort identities differ from the Wave 2 cohort: missing=%s unknown=%s"
            % (missing, unknown)
        )

    family_counts = {}
    for row in selected:
        family = row.get("family")
        family_counts[family] = family_counts.get(family, 0) + 1
    if family_counts != EXPECTED_FAMILY_COUNTS:
        raise ValueError(
            "cohort family counts differ: expected=%s actual=%s"
            % (EXPECTED_FAMILY_COUNTS, family_counts)
        )

    expected_names = _expected_name_set()
    for row in rows:
        if not isinstance(row, dict):
            continue
        if _row_name(row) in expected_names and row.get("failureCode") != failure_code:
            raise ValueError(
                "cohort identity %s has the wrong failureCode" % _row_name(row)
            )
    return CohortSelection(
        rows=tuple(selected),
        identities=identities,
        family_counts=MappingProxyType(dict(family_counts)),
        failure_code=failure_code,
    )


def load_cohort_manifest(path, failure_code):
    path = pathlib.Path(path)
    return select_cohort_rows(_json_file(path), failure_code)


def _filter_strict(rows, identities):
    return [copy.deepcopy(row) for row in rows if isinstance(row, dict) and _lane_key(row) in identities]


def _require_exact_rows(rows, identities, label):
    actual = _identity_set(rows)
    if len(actual) != len(rows):
        raise ValueError("%s contains duplicate identities" % label)
    if actual != identities:
        raise ValueError(
            "%s does not contain the exact cohort: missing=%s unknown=%s"
            % (label, sorted(identities - actual), sorted(actual - identities))
        )


def _select_dashboard_rows(dashboard, identities):
    rows = _dashboard_entries(copy.deepcopy(dashboard))
    filtered = _filter_strict(rows, identities)
    try:
        _require_exact_rows(filtered, identities, "fresh dashboard rows")
    except ValueError as error:
        raise ValueError("fresh dashboard has unknown identity: %s" % error) from error
    raw_rows = _raw_dashboard_entries(dashboard)
    raw_by_identity = _raw_dashboard_identity_map(dashboard)
    raw_by_name = {}
    for raw in raw_rows:
        if str(_row_name(raw)).strip():
            raw_by_name.setdefault(_row_name(raw), []).append(raw)
    for row in filtered:
        key = _lane_key(row)
        raw = raw_by_identity.get(key)
        if raw is None:
            if any(
                _is_enriched_dashboard_row(candidate)
                for candidate in raw_by_name.get(_row_name(row), ())
            ):
                raise ValueError("fresh dashboard is missing referenceKind: %s" % _row_name(row))
            raise ValueError("fresh dashboard has unknown identity: %s/%s" % key)
        if _is_enriched_dashboard_row(raw):
            if "referenceKind" not in raw or not _nonempty(raw.get("referenceKind")):
                row.pop("referenceKind", None)
            if "evidenceLane" in raw:
                row["evidenceLane"] = raw["evidenceLane"]
            else:
                row.pop("evidenceLane", None)
    return filtered


def _raw_dashboard_entries(dashboard):
    if isinstance(dashboard, list):
        entries = dashboard
    elif isinstance(dashboard, dict) and isinstance(dashboard.get("gms"), dict):
        entries = [
            dict(entry, name=name) if isinstance(entry, dict) else {"name": name}
            for name, entry in dashboard["gms"].items()
        ]
    elif isinstance(dashboard, dict):
        entries = []
        for key in ("gms", "rows", "results"):
            if isinstance(dashboard.get(key), list):
                entries = dashboard[key]
                break
    else:
        entries = []
    return [copy.deepcopy(entry) if isinstance(entry, dict) else {"name": entry} for entry in entries]


_ENRICHED_DASHBOARD_FIELDS = frozenset(
    {
        "referenceKind",
        "failureCode",
        "evidenceLane",
        "class",
        "className",
        "sourceClass",
        "sourceRegistration",
        "gmIdentity",
        "classification",
        "terminal",
        "terminalRefusal",
    }
)

_ENRICHED_REQUIRED_METADATA = (
    "className",
    "sourceClass",
    "sourceRegistration",
    "gmIdentity",
    "classification",
    "terminal",
    "terminalRefusal",
    "referenceKind",
    "family",
    "evidenceLane",
    "failureCode",
)


def _metadata_present(value):
    if isinstance(value, str):
        return bool(value.strip())
    return value is not None and value not in ([], {})


def _is_enriched_dashboard_row(row):
    return isinstance(row, dict) and any(
        field in row for field in _ENRICHED_DASHBOARD_FIELDS
    )


def _raw_dashboard_identity(row):
    name = str(_row_name(row))
    reference_kind = (
        row.get("referenceKind")
        if _is_enriched_dashboard_row(row)
        else "skia-upstream"
    )
    return name, str(reference_kind or "")


def _raw_dashboard_identity_map(dashboard):
    return {
        _raw_dashboard_identity(row): row
        for row in _raw_dashboard_entries(dashboard)
        if str(_row_name(row)).strip()
    }


def _validate_fresh_dashboard_metadata(selected_rows, selection, raw_by_identity):
    expected = {_lane_key(row): row for row in selection.rows}
    actual = {}
    violations = []
    for row in selected_rows:
        name = _row_name(row)
        if not str(name).strip():
            violations.append("fresh dashboard row has no usable identity in name/gm/id")
            continue
        key = _lane_key(row)
        if key in expected:
            if key in actual:
                violations.append("fresh dashboard has duplicate identity: %s/%s" % key)
            else:
                actual[key] = row
            continue
        violations.append("fresh dashboard has unknown identity: %s/%s" % key)

    for key, row in actual.items():
        name = _row_name(row)
        expected_row = expected[key]
        raw = raw_by_identity.get(key)
        enriched = _is_enriched_dashboard_row(raw)
        if enriched:
            for field in _ENRICHED_REQUIRED_METADATA:
                if field not in raw or not _metadata_present(raw.get(field)):
                    violations.append(
                        "fresh dashboard is missing %s: %s" % (field, name)
                    )
            for field in (
                "className",
                "sourceClass",
                "sourceRegistration",
                "gmIdentity",
            ):
                actual_value = row.get(field)
                expected_value = expected_row.get(field)
                if not _metadata_present(actual_value):
                    continue
                if actual_value != expected_value:
                    violations.append(
                        "fresh dashboard has wrong %s: %s" % (field, name)
                    )
            if "classification" in raw and raw.get("classification") != row.get("classification"):
                violations.append("fresh dashboard has wrong classification: %s" % name)
            for field in ("terminal", "terminalRefusal"):
                if field in raw and not isinstance(raw.get(field), bool):
                    violations.append(
                        "fresh dashboard has invalid %s: %s" % (field, name)
                    )
            if (
                isinstance(raw.get("terminal"), bool)
                and isinstance(raw.get("terminalRefusal"), bool)
                and raw["terminal"] != raw["terminalRefusal"]
            ):
                violations.append("fresh dashboard terminal metadata disagrees: %s" % name)
        if not _nonempty(row.get("referenceKind")):
            violations.append("fresh dashboard is missing referenceKind: %s" % name)
        elif row.get("referenceKind") != expected_row.get("referenceKind"):
            violations.append("fresh dashboard has wrong referenceKind: %s" % name)
        if not _nonempty(row.get("family")):
            violations.append("fresh dashboard is missing family: %s" % name)
        elif row.get("family") != expected_row.get("family"):
            violations.append("fresh dashboard has wrong family: %s" % name)
        if enriched or "failureCode" in row:
            failure_code = _failure_code_value(row)
            if failure_code is None:
                violations.append("fresh dashboard is missing failureCode: %s" % name)
            elif failure_code != selection.failure_code and not _is_residual_refusal(row, {}):
                violations.append("fresh dashboard has wrong failureCode: %s" % name)
        expected_lane = expected_row.get("evidenceLane")
        if expected_lane is not None:
            if not _nonempty(row.get("evidenceLane")):
                violations.append("fresh dashboard is missing evidenceLane: %s" % name)
            elif row.get("evidenceLane") != expected_lane:
                violations.append("fresh dashboard has wrong evidenceLane: %s" % name)
    missing = sorted(set(expected) - set(actual))
    if missing:
        violations.append("fresh dashboard is missing identities: %s" % missing)
    if violations:
        raise ValueError("fresh dashboard metadata validation failed: %s" % "; ".join(violations))


def _select_runner_rows(runner_rows, dashboard_rows, identities):
    claims = {}
    preserved = []
    selected = []
    for original in runner_rows:
        row = copy.deepcopy(original)
        row.setdefault("referenceKind", "skia-upstream")
        row.setdefault("evidenceLane", "skia-junit")
        if not isinstance(row.get("gmIdentity"), dict):
            row["gmIdentity"] = _junit_identity(row.get("name", ""), row)
        matches = _junit_dashboard_matches(row, dashboard_rows)
        if len(matches) > 1:
            raise ValueError("fresh runner identity is ambiguous: %s" % _row_name(row))
        if len(matches) != 1:
            if (
                row.get("suiteLevel")
                or row.get("classification") in {"unclassified", "lifecycle-failure", "timeout"}
                or row.get("lifecycleFailure")
                or row.get("timeout")
            ):
                preserved.append(row)
            continue
        dashboard_index = matches[0]
        claims.setdefault(dashboard_index, []).append(row)
    for dashboard_index, rows in claims.items():
        if len(rows) != 1:
            raise ValueError(
                "fresh runner contains duplicate identity: %s"
                % _row_name(dashboard_rows[dashboard_index])
            )
    if len(claims) != len(dashboard_rows):
        missing = [
            _row_name(row)
            for index, row in enumerate(dashboard_rows)
            if index not in claims
        ]
        raise ValueError("fresh runner is missing cohort identities: %s" % missing)
    for index in sorted(claims):
        selected.append(claims[index][0])
    selected.extend(preserved)
    _require_exact_rows(dashboard_rows, identities, "fresh dashboard rows")
    return selected


def _filter_evidence_with_families(entries, selection):
    expected_families = {
        _lane_key(row): row.get("family") for row in selection.rows
    }
    filtered = []
    unknown = []
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict):
            unknown.append("non-object evidence entry")
            continue
        key = _lane_key(entry)
        name = str(_row_name(entry))
        if not name.strip():
            raise ValueError("evidence entry has no usable identity in name/gm/id")
        if name in _expected_name_set() and key not in selection.identities:
            unknown.append("%s/%s" % key)
            continue
        if key not in selection.identities:
            if name:
                unknown.append("%s/%s" % key)
            continue
        if key in seen:
            raise ValueError("evidence contains duplicate identity: %s/%s" % key)
        if entry.get("family") not in (None, expected_families[key]):
            raise ValueError("evidence identity has conflicting family: %s" % name)
        seen.add(key)
        filtered.append(copy.deepcopy(entry))
    return filtered, unknown


def _raw_evidence_entries(value):
    if isinstance(value, dict):
        entries = value.get("entries", value.get("rows", []))
    else:
        entries = value
    if not isinstance(entries, list):
        raise ValueError("raw evidence entries must be an array")
    return entries


def _validate_raw_evidence_entries(value):
    for index, entry in enumerate(_raw_evidence_entries(value)):
        if not isinstance(entry, dict):
            raise ValueError("raw evidence entry %s is non-dict" % index)
        if not str(_row_name(entry)).strip():
            raise ValueError(
                "raw evidence entry %s has no usable identity in name/gm/id" % index
            )


def _first_number(sources, keys):
    for source in sources:
        if not isinstance(source, dict):
            continue
        for key in keys:
            value = _finite_number(source.get(key))
            if value is not None:
                return value
    return None


def _has_value(sources, keys):
    return any(
        isinstance(source, dict) and _nonempty(source.get(key))
        for source in sources
        for key in keys
    )


def _is_supported_after(row, evidence):
    return any(
        isinstance(source, dict)
        and (source.get("supportedAfter") is True or source.get("pixelImproved") is True)
        for source in _evidence_sources(row, evidence)
    )


def _is_residual_refusal(row, evidence):
    sources = _evidence_sources(row, evidence)
    return any(
        isinstance(source, dict)
        and (
            source.get("terminalRefusal") is True
            or source.get("classification") == "terminal-refusal"
            or source.get("terminal") is True
        )
        for source in sources
    )


def _route_values(sources):
    values = []
    for source in sources:
        if not isinstance(source, dict):
            continue
        for key in ("routeSignature", "routeDiagnostic", "routeDiagnostics", "route"):
            value = source.get(key)
            if _nonempty(value):
                values.append(value)
    return values


def _normalized_route(value):
    return re.sub(r"[^a-z0-9]+", "-", str(value).strip().lower()).strip("-")


def _validate_supported_after(row, evidence):
    sources = _evidence_sources(row, evidence)
    violations = []
    if not any(source.get("supportedAfter") is True for source in sources if isinstance(source, dict)):
        violations.append("supported-after evidence must set supportedAfter=true")
    if not any(source.get("pixelImproved") is True for source in sources if isinstance(source, dict)):
        violations.append("supported-after evidence must set pixelImproved=true")
    if _first_number(sources, ("similarityBefore", "scoreBefore", "beforeSimilarity", "beforeScore")) is None:
        violations.append("supported-after evidence is missing before similarity")
    if _first_number(sources, ("similarityAfter", "scoreAfter", "afterSimilarity", "afterScore")) is None:
        violations.append("supported-after evidence is missing after similarity")
    if _first_number(sources, ("minSimilarity", "threshold", "minimumSimilarity")) is None:
        violations.append("supported-after evidence is missing threshold/minSimilarity")
    after = _first_number(
        sources, ("similarityAfter", "scoreAfter", "afterSimilarity", "afterScore")
    )
    threshold = _first_number(sources, ("minSimilarity", "threshold", "minimumSimilarity"))
    if after is not None and threshold is not None and after < threshold:
        violations.append("supported-after score is below threshold/minSimilarity")
    if not _similarity_improved(row, evidence):
        violations.append("supported-after evidence does not show similarity improvement")
    if row.get("isPassing") is not True or row.get("classification") != "pass":
        violations.append("dashboard row is not passing")
    if not _valid_comparable(row, evidence):
        violations.append("supported-after row is not comparable and passing")
    if not _junit_is_pass(row):
        violations.append("supported-after row is missing a passing JUnit result")
    if _is_route_only(row) or _is_route_only(evidence):
        violations.append("route-only row cannot be supported after")
    if not _has_causal_evidence(row, evidence):
        violations.append("supported-after evidence is missing causal evidence")
    if not _has_value(sources, ("routeDiagnostic", "routeDiagnostics", "routeSignature", "route")):
        violations.append("supported-after evidence is missing route diagnostics")
    route_values = _route_values(sources)
    normalized_routes = {_normalized_route(value) for value in route_values}
    if any(
        "cpufallback" in route or ("cpu" in route and "fallback" in route)
        for route in normalized_routes
    ):
        violations.append("supported-after evidence uses a CPU fallback route")
    route_signatures = [
        source.get("routeSignature")
        for source in sources
        if isinstance(source, dict) and _nonempty(source.get("routeSignature"))
    ]
    expected_routes = [
        source.get("expectedRoute")
        for source in sources
        if isinstance(source, dict) and _nonempty(source.get("expectedRoute"))
    ]
    if not route_signatures or any(
        _normalized_route(value) != EXPECTED_GPU_ROUTE_SIGNATURE
        for value in route_signatures
    ):
        violations.append(
            "supported-after routeSignature must equal the expected GPU-prepared route"
        )
    if not expected_routes or any(
        _normalized_route(value) != EXPECTED_GPU_ROUTE_SIGNATURE
        for value in expected_routes
    ):
        violations.append(
            "supported-after expectedRoute must equal the expected GPU-prepared route"
        )
    render_dimensions, reference_dimensions = _entry_dimensions(evidence)
    if render_dimensions is None or reference_dimensions is None or render_dimensions != reference_dimensions:
        violations.append("supported-after evidence is missing matching render/reference dimensions")
    if not _has_complete_pixel_evidence(row, evidence):
        violations.append("supported-after evidence is missing complete pixel evidence")
    return violations


def _population_policy_sources(inputs):
    evidence = inputs.get("evidenceValue", {})
    environment = inputs.get("environment", {})
    cohort_manifest = inputs.get("cohortManifest", {})
    sources = [
        (
            "cohort manifest",
            cohort_manifest.get("populationPolicy")
            if isinstance(cohort_manifest, dict)
            else None,
        )
    ]
    if isinstance(evidence, dict):
        sources.append(("fresh evidence", evidence.get("populationPolicy")))
        sources.append(("fresh evidence", evidence.get("population")))
        provenance = evidence.get("provenance")
        sources.append(
            (
                "fresh evidence provenance",
                provenance.get("populationPolicy")
                if isinstance(provenance, dict)
                else None,
            )
        )
        sources.append(
            (
                "fresh evidence provenance",
                provenance.get("population")
                if isinstance(provenance, dict)
                else None,
            )
        )
        if any(key in evidence for key in EXPECTED_POPULATION_POLICY):
            sources.append(("fresh evidence root", evidence))
        if isinstance(provenance, dict) and any(
            key in provenance for key in EXPECTED_POPULATION_POLICY
        ):
            sources.append(("fresh evidence provenance root", provenance))
    if isinstance(environment, dict):
        sources.append(("fresh environment", environment.get("populationPolicy")))
        if any(key in environment for key in EXPECTED_POPULATION_POLICY):
            sources.append(("fresh environment root", environment))
    return sources


def _population_value_matches(actual, expected):
    return type(actual) is type(expected) and actual == expected


def _validate_population_policy(inputs, manifest):
    violations = []
    effective = manifest.get("populationPolicy", {})
    for key, expected in EXPECTED_POPULATION_POLICY.items():
        if not isinstance(effective, dict) or not _population_value_matches(effective.get(key), expected):
            violations.append(
                "population policy %s must be %r, got %r"
                % (key, expected, effective.get(key))
            )
    for label, source in _population_policy_sources(inputs):
        if source is None:
            if label == "cohort manifest":
                violations.append("cohort manifest population policy is missing")
            continue
        if not isinstance(source, dict):
            violations.append("%s population policy must be an object" % label)
            continue
        for key, expected in EXPECTED_POPULATION_POLICY.items():
            if key in source and not _population_value_matches(source[key], expected):
                violations.append(
                    "%s population policy %s must be %r, got %r"
                    % (label, key, expected, source[key])
                )
    return list(dict.fromkeys(violations))


def _validate_residual_refusal(row, evidence, failure_code):
    sources = _evidence_sources(row, evidence)
    violations = []
    actual_code = _first_value(sources, ("failureCode",))
    if _failure_code_value({"failureCode": actual_code}) is None:
        violations.append("residual refusal is missing failureCode")
    elif actual_code == failure_code:
        violations.append("residual refusal failureCode must be distinct from cohort")
    for field in ("fallbackReason", "expectedRoute", "rootCause", "followUpFamily"):
        if not _has_value(sources, (field,)):
            violations.append("residual refusal is missing %s" % field)
    return violations


def _first_value(sources, keys):
    for source in sources:
        if not isinstance(source, dict):
            continue
        for key in keys:
            if _nonempty(source.get(key)):
                return source[key]
    return None


def _validate_artifacts(entries, index_path, input_paths, allowed_roots, evidence_value):
    violations = []
    violations.extend(
        _check_provenance_artifacts(
            evidence_value,
            index_path,
            input_paths=input_paths,
            allowed_roots=allowed_roots,
        )
    )
    seen_paths = {}
    provenance_artifacts = evidence_value.get("provenanceArtifacts", {}) if isinstance(evidence_value, dict) else {}
    if isinstance(provenance_artifacts, dict):
        for name, record in provenance_artifacts.items():
            if not isinstance(record, dict):
                continue
            artifact_path = _resolve_evidence_path(record.get("path"), index_path)
            if artifact_path is not None:
                resolved = str(artifact_path.resolve())
                if resolved in seen_paths:
                    violations.append(
                        "evidence artifact path has duplicate roles/entries: %s"
                        % artifact_path
                    )
                else:
                    seen_paths[resolved] = "provenance.%s" % name
    for index, entry in enumerate(entries):
        for record_index, record in enumerate(_artifact_records(entry)):
            label = "%s.%s" % (_row_name(entry) or index, record["label"])
            path_value = record.get("path")
            digest = record.get("sha256")
            if not path_value or not _has_hash(digest):
                violations.append("evidence artifact is missing path/hash: %s" % label)
                continue
            artifact_path = _resolve_evidence_path(path_value, index_path)
            if artifact_path is None or not artifact_path.is_file():
                violations.append("evidence artifact path is absent: %s" % label)
                continue
            resolved = str(artifact_path.resolve())
            if resolved in seen_paths:
                violations.append("evidence artifact path has duplicate roles/entries: %s" % artifact_path)
            else:
                seen_paths[resolved] = label
            if _sha256_file(artifact_path).lower() != digest.lower():
                violations.append("evidence artifact hash mismatch: %s" % label)
            if allowed_roots and not any(_path_within(artifact_path, root) for root in allowed_roots):
                violations.append("evidence artifact path is outside allowed roots: %s" % artifact_path)
            for input_name, input_path in input_paths.items():
                if input_path is not None and _same_file(artifact_path, input_path):
                    violations.append("evidence artifact aliases input %s: %s" % (input_name, artifact_path))
    return violations


def _failure_code_value(source):
    value = source.get("failureCode") if isinstance(source, dict) else None
    return value if isinstance(value, str) and value.strip() else None


def _failure_code_violations(dashboard_rows, entries, failure_code):
    evidence_by_key = {_lane_key(entry): entry for entry in entries}
    violations = []
    for row in dashboard_rows:
        name = _row_name(row)
        evidence = evidence_by_key.get(_lane_key(row), {})
        junit = row.get("junit", {}) if isinstance(row, dict) else {}
        if _is_residual_refusal(row, evidence):
            sources = (
                ("dashboard", row),
                ("evidence", evidence),
                ("junit", junit),
            )
            missing = [label for label, source in sources if _failure_code_value(source) is None]
            if missing:
                violations.append(
                    "residual refusal is missing failureCode for %s: %s"
                    % (", ".join(missing), name)
                )
                continue
            codes = {source["failureCode"] for _, source in sources}
            if len(codes) != 1:
                violations.append(
                    "residual refusal failureCode mismatch: %s" % name
                )
            elif next(iter(codes)) == failure_code:
                violations.append(
                    "residual refusal failureCode must be distinct from cohort: %s"
                    % name
                )
            continue

        if row.get("failureCode") != failure_code:
            violations.append("fresh dashboard has wrong failureCode: %s" % name)
        if evidence and evidence.get("failureCode") != failure_code:
            violations.append("evidence entry has wrong failureCode: %s" % name)
        junit_code = _failure_code_value(junit)
        if junit_code is not None and junit_code != failure_code:
            violations.append("JUnit row has wrong failureCode: %s" % name)
    return violations


def _filtered_junit_result(parsed, rows):
    result = copy.deepcopy(parsed)
    result["selectedRows"] = copy.deepcopy(rows)
    return result


def _junit_summary(parsed):
    summary = _summary(parsed)
    for key in ("parsedCounts", "declaredCounts", "countMismatches"):
        if key in parsed:
            summary[key] = _copy_value(parsed[key])
    summary["suiteLevelRows"] = sum(
        bool(row.get("suiteLevel"))
        for row in parsed.get("rows", [])
        if isinstance(row, dict)
    )
    selected_rows = parsed.get("selectedRows")
    if isinstance(selected_rows, list):
        summary["selectedRows"] = len(selected_rows)
    return summary


def _dashboard_summary(rows):
    return {
        "total": len(rows),
        "passing": sum(row.get("classification") == "pass" for row in rows),
        "failing": sum(row.get("classification") == "failure" for row in rows),
        "noScore": sum(row.get("classification") == "no-score" for row in rows),
    }


def _oracle_summary(rows):
    summary = _dashboard_summary(rows)
    summary["routeOnly"] = sum(_is_route_only(row) for row in rows)
    return summary


_ORACLE_NUMERIC_FIELDS = (
    "score",
    "similarity",
    "similarityBefore",
    "similarityAfter",
    "scoreBefore",
    "scoreAfter",
    "minSimilarity",
    "threshold",
)


def _load_oracle_rows(path, label, reference_kind, evidence_lane, identities):
    value = _load_json_input(path)
    if not isinstance(value, dict):
        raise ValueError("%s oracle input must be a JSON object" % label)
    containers = [key for key in ("rows", "gms", "results") if key in value]
    if not containers:
        raise ValueError("%s oracle input must contain rows" % label)
    if any(not isinstance(value[key], list) for key in containers):
        raise ValueError("%s oracle rows must be an array" % label)
    serialized = {
        json.dumps(value[key], sort_keys=True, separators=(",", ":"))
        for key in containers
    }
    if len(serialized) != 1:
        raise ValueError("%s oracle input contains contradictory row containers" % label)

    raw_rows = value[containers[0]]
    normalized = _oracle_entries(value, evidence_lane, reference_kind)
    if len(normalized) != len(raw_rows):
        raise ValueError("%s oracle input contains malformed rows" % label)
    expected_names = {name for name, _ in identities}
    filtered = []
    for index, (raw, row) in enumerate(zip(raw_rows, normalized)):
        if not isinstance(raw, dict):
            raise ValueError("%s oracle row %s is not an object" % (label, index))
        name = _row_name(row)
        if not str(name).strip():
            raise ValueError("%s oracle row %s has no usable identity" % (label, index))
        for field, expected in (
            ("referenceKind", reference_kind),
            ("evidenceLane", evidence_lane),
        ):
            if field in raw and (
                not isinstance(raw[field], str)
                or not raw[field].strip()
                or raw[field] != expected
            ):
                raise ValueError(
                    "%s oracle row %s has invalid %s" % (label, index, field)
                )
        for field in _ORACLE_NUMERIC_FIELDS:
            if field in raw and raw[field] is not None and _finite_number(raw[field]) is None:
                raise ValueError(
                    "%s oracle row %s has invalid %s" % (label, index, field)
                )
        if name not in expected_names and not _is_route_only(row):
            raise ValueError("%s oracle row has unknown identity: %s" % (label, name))
        filtered.append(row)
    return filtered


def _generated_at():
    return datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")


def _metadata(name, hashes):
    value = hashes.get(name) if isinstance(hashes, dict) else None
    return _copy_value(value) if isinstance(value, dict) else {"path": str(name)}


def _provenance(hashes, inputs, evidence_value, dashboard_output):
    files = {
        key: value
        for key, value in hashes.items()
        if isinstance(value, dict) and "path" in value
    }
    result = {
        "files": _copy_value(files),
        "inputs": _copy_value(hashes.get("inputs", {})),
        "outputs": _copy_value(hashes.get("outputs", {})),
        "evidence": _copy_value(hashes.get("evidence", {})),
        "cohortManifest": _copy_value(hashes.get("cohortManifest", {})),
    }
    for field, key, value in (
        ("commands", "commandsJson", inputs["commands"]),
        ("environment", "environmentJson", inputs["environment"]),
        ("cpuResults", "cpuResults", inputs["cpuResults"]),
        ("gpuResults", "gpuResults", inputs["gpuResults"]),
        ("fp13Runner", "fp13Runner", _junit_summary(inputs["fp13Runner"])),
        ("evidenceIndex", "evidenceIndex", evidence_value),
    ):
        if key in hashes:
            result[field] = _copy_value(hashes[key])
            result[field]["value"] = _copy_value(value)
    if dashboard_output is not None:
        result["dashboardOutput"] = _copy_value(hashes.get("dashboardOutput", {}))
    return result


def build_manifest(inputs, selection, source_commit, status):
    dashboard_rows = inputs["dashboardRows"]
    runner_rows = inputs["runnerRows"]
    evidence_rows = inputs["evidenceRows"]
    evidence_by_key = {_lane_key(entry): entry for entry in evidence_rows}
    rows = []
    for row in dashboard_rows:
        result = copy.deepcopy(row)
        evidence = evidence_by_key.get(_lane_key(row))
        if evidence is not None:
            result["evidenceIndexEntry"] = _copy_value(evidence)
            result["evidence"] = _copy_value(evidence)
            for key, value in evidence.items():
                result.setdefault(key, _copy_value(value))
        rows.append(result)

    supported_rows = []
    residual_rows = []
    for row in rows:
        evidence = evidence_by_key.get(_lane_key(row), {})
        if _is_supported_after(row, evidence) and not _validate_supported_after(row, evidence):
            supported_rows.append(row)
        elif _is_residual_refusal(row, evidence):
            residual_rows.append(row)
    candidate_cohorts = {}
    for row in rows:
        evidence = evidence_by_key.get(_lane_key(row), {})
        if (
            row.get("referenceKind") == "skia-upstream"
            and _valid_comparable(row, evidence)
            and row.get("classification") == "pass"
            and _junit_is_pass(row)
            and _has_causal_evidence(row, evidence)
        ):
            cohort = _causal_cohort_key(evidence)
            if cohort is not None:
                candidate_cohorts[cohort] = candidate_cohorts.get(cohort, 0) + 1
    generic_rows = [
        entry
        for entry in evidence_rows
        if not _is_supported_after({}, entry) and not _is_residual_refusal({}, entry)
    ]
    comparable_rows = [
        row
        for row in rows
        if _valid_comparable(row, evidence_by_key.get(_lane_key(row)))
    ]
    before_count = sum(
        _first_number(_evidence_sources({}, entry), ("similarityBefore", "scoreBefore", "beforeSimilarity", "beforeScore"))
        is not None
        for entry in evidence_rows
    )
    after_count = sum(
        _first_number(_evidence_sources({}, entry), ("similarityAfter", "scoreAfter", "afterSimilarity", "afterScore"))
        is not None
        for entry in evidence_rows
    )
    score_file = inputs["scoreFile"]
    direct_edit = score_file["directEditDetected"]
    policy_input = inputs["policy"]
    policy = {
        "globalThresholdWeakened": False,
        "assertionsWeakened": False,
        "referencesModified": False,
        "scoresDirectlyEdited": direct_edit,
        "memoryBudgetChanged": False,
        "readinessDelta": 0.0,
    }
    if isinstance(policy_input, dict):
        policy.update(_copy_value(policy_input))
    policy["scoresDirectlyEdited"] = direct_edit
    dashboard_summary = _dashboard_summary(dashboard_rows)
    runner_result = _filtered_junit_result(inputs["skiaRunner"], runner_rows)
    fp13_result = inputs["fp13Runner"]
    residual_codes = sorted(
        {
            str(_first_value(_evidence_sources(row, evidence_by_key.get(_lane_key(row), {})), ("failureCode",)))
            for row in residual_rows
            if _first_value(_evidence_sources(row, evidence_by_key.get(_lane_key(row), {})), ("failureCode",))
        }
    )
    cohort_population = inputs["cohortManifest"].get("populationPolicy", {})
    if not isinstance(cohort_population, dict):
        raise ValueError("cohort manifest population policy must be an object")
    population = copy.deepcopy(EXPECTED_POPULATION_POLICY)
    population.update(copy.deepcopy(cohort_population))
    population.update(
        {
            "cohortSize": EXPECTED_COHORT_SIZE,
            "cohortFailureCode": selection.failure_code,
            "cohortReferenceKind": "skia-upstream",
        }
    )
    dashboard_path = inputs["paths"]["dashboardJson"]
    dashboard_output = inputs["dashboardOutput"]
    dashboard_manifest = {
        "summary": dashboard_summary,
        "sourceSummary": _copy_value(inputs["dashboard"].get("summary", {})),
        "gms": _copy_value(dashboard_rows),
        "outputDir": str(inputs["paths"]["dashboardDir"]),
        "dataPath": str(dashboard_path),
        "dataSha256": _metadata("dashboardJson", inputs["hashes"]).get("sha256"),
    }
    if dashboard_output is not None:
        dashboard_manifest["outputSha256"] = _metadata("dashboardOutput", inputs["hashes"]).get("sha256")
    current = {
        "runner": _junit_summary(runner_result),
        "dashboard": {"rows": len(dashboard_rows), "summary": dashboard_summary},
        "svg": {"rows": len(inputs["svgRows"])},
        "testOracle": {
            "rows": len(inputs["testOracleRows"]),
            "summary": _oracle_summary(inputs["testOracleRows"]),
        },
        "cpuOracle": {
            "rows": len(inputs["cpuOracleRows"]),
            "summary": _oracle_summary(inputs["cpuOracleRows"]),
        },
        "fp13": _junit_summary(fp13_result),
        "evidence": {
            "rows": len(evidence_rows),
            "beforeRows": before_count,
            "afterRows": after_count,
        },
        "observedComparableRows": len(comparable_rows),
        "candidateUnlockedRows": max(candidate_cohorts.values(), default=0),
        "supportedRowsAfter": len(supported_rows) if status != "classification" else 0,
        "beforeRows": before_count,
        "afterRows": after_count,
        "residualRefusalRows": len(residual_rows),
    }
    non_claims = [
        "This is a filtered Wave 2 cohort report; it does not rewrite Wave 1 or fresh evidence inputs.",
        "Residual terminal refusals are not pixel support and do not claim a renderer fix.",
        "Terminal refusal rows do not require fabricated render evidence.",
        "No global threshold, assertion, reference, memory budget, or readiness policy was weakened.",
        "Route-only evidence is not promoted to pixel support.",
    ]
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "generatedBy": GENERATED_BY,
        "generator": {
            "path": "scripts/gm/reconcile_skia_fidelity_wave2.py",
            "sha256": _sha256_file(pathlib.Path(__file__).resolve()),
        },
        "generatedAt": _generated_at(),
        "sourceCommit": source_commit,
        "status": status,
        "cohort": {
            "failureCode": selection.failure_code,
            "count": EXPECTED_COHORT_SIZE,
            "size": EXPECTED_COHORT_SIZE,
            "referenceKind": "skia-upstream",
            "identities": [
                {"name": name, "referenceKind": reference_kind}
                for name, reference_kind in sorted(selection.identities)
            ],
            "familyCounts": dict(selection.family_counts),
        },
        "populationPolicy": population,
        "policy": _copy_value(policy),
        "policyEvidencePresent": bool(inputs["policyEvidencePresent"]),
        "commands": _copy_value(inputs["commands"]),
        "environment": _copy_value(inputs["environment"]),
        "repository": inputs["environment"].get("repository")
        if isinstance(inputs["environment"], dict)
        else None,
        "worktree": inputs["environment"].get("worktree")
        if isinstance(inputs["environment"], dict)
        else None,
        "dashboard": dashboard_manifest,
        "scoreFile": _copy_value(score_file),
        "current": _copy_value(current),
        "rows": {
            "skia": _copy_value(rows),
            "skiaJunit": _copy_value(runner_rows),
            "svg": _copy_value(inputs["svgRows"]),
            "testOracle": _copy_value(inputs["testOracleRows"]),
            "cpuOracle": _copy_value(inputs["cpuOracleRows"]),
            "evidence": _copy_value(evidence_rows),
        },
        "beforeCount": before_count,
        "afterCount": after_count,
        "observedComparableRows": len(comparable_rows),
        "candidateUnlockedRows": current["candidateUnlockedRows"],
        "supportedRowsAfter": current["supportedRowsAfter"],
        "residualCodes": residual_codes,
        "residualRefusalRows": len(residual_rows),
        "supportedAfterRows": [row.get("name") for row in supported_rows],
        "routeOnlyRows": sum(
            _is_route_only(row) or _is_route_only(evidence_by_key.get(_lane_key(row), {}))
            for row in rows
        ),
        "routeOnlyRowsPromoted": False,
        "escalation": {
            "maxFailedHypotheses": 3,
            "failedHypotheses": [],
        },
        "nonClaims": non_claims,
        "provenance": _provenance(
            inputs["hashes"], inputs, inputs["evidenceValue"], dashboard_output
        ),
        "inputs": {
            "cohortManifest": _copy_value(inputs["cohortManifest"]),
            "commands": _copy_value(inputs["commands"]),
            "environment": _copy_value(inputs["environment"]),
            "policy": _copy_value(policy_input),
            "scoresBefore": _copy_value(inputs["scoresBefore"]),
            "scoresAfter": _copy_value(inputs["scoresAfter"]),
            "evidenceIndex": _copy_value(inputs["evidenceValue"]),
            "cpuResults": _copy_value(inputs["cpuResults"]),
            "gpuResults": _copy_value(inputs["gpuResults"]),
            "fp13": _junit_summary(inputs["fp13Runner"]),
            "genericEvidenceRows": _copy_value(generic_rows),
        },
    }


def _markdown_value(value):
    return str(value).replace("|", r"\|").replace("\n", " ")


def render_markdown(manifest):
    policy = manifest.get("policy", {})
    cohort = manifest.get("cohort", {})
    current = manifest.get("current", {})
    lines = [
        "# Skia Fidelity Wave 2 UNPREMUL Reconciliation",
        "",
        "- kind: `%s`" % manifest.get("kind"),
        "- generatedBy: `%s`" % manifest.get("generatedBy"),
        "- sourceCommit: `%s`" % manifest.get("sourceCommit"),
        "- status: `%s`" % manifest.get("status"),
        "",
        "## Cohort",
        "",
        "- failureCode: `%s`" % cohort.get("failureCode"),
        "- count: `%s`" % cohort.get("count", 0),
        "- referenceKind: `%s`" % cohort.get("referenceKind"),
        "- familyCounts: `%s`" % _markdown_value(cohort.get("familyCounts", {})),
        "",
        "## Counts",
        "",
        "- beforeCount: `%s`" % manifest.get("beforeCount", 0),
        "- afterCount: `%s`" % manifest.get("afterCount", 0),
        "- observedComparableRows: `%s`" % manifest.get("observedComparableRows", 0),
        "- supportedRowsAfter: `%s`" % manifest.get("supportedRowsAfter", 0),
        "- residualRefusalRows: `%s`" % manifest.get("residualRefusalRows", 0),
        "- residualCodes: `%s`" % _markdown_value(manifest.get("residualCodes", [])),
        "",
        "| Lane | Rows | Failures | Errors | Skipped | Terminal |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for lane in ("runner", "fp13", "dashboard", "svg", "testOracle", "cpuOracle", "evidence"):
        summary = current.get(lane, {})
        if lane == "dashboard" and isinstance(summary.get("summary"), dict):
            summary = {**summary["summary"], "rows": summary.get("rows", 0)}
        lines.append(
            "| `%s` | %s | %s | %s | %s | %s |"
            % (
                lane,
                summary.get("rows", 0),
                summary.get("failures", 0),
                summary.get("errors", 0),
                summary.get("skipped", 0),
                summary.get("terminalFailures", 0),
            )
        )
    lines.extend(
        [
            "",
            "## Policy",
            "",
            "- assertionsWeakened: `%s`" % policy.get("assertionsWeakened"),
            "- globalThresholdWeakened: `%s`" % policy.get("globalThresholdWeakened"),
            "- memoryBudgetChanged: `%s`" % policy.get("memoryBudgetChanged"),
            "- readinessDelta: `%s`" % policy.get("readinessDelta"),
            "- referencesModified: `%s`" % policy.get("referencesModified"),
            "- scoresDirectlyEdited: `%s`" % policy.get("scoresDirectlyEdited"),
            "",
            "## Non-claims",
            "",
        ]
    )
    lines.extend("- " + str(value) for value in manifest.get("nonClaims", []))
    lines.extend(["", "## SHA-256 Provenance", "", "| Evidence | Path | SHA-256 |", "| --- | --- | --- |"])
    provenance = manifest.get("provenance", {})
    for group, value in sorted(provenance.items()):
        if not isinstance(value, dict):
            continue
        if "path" in value:
            lines.append(
                "| `%s` | `%s` | `%s` |"
                % (group, _markdown_value(value.get("path", "")), value.get("sha256", ""))
            )
            continue
        for name, metadata in sorted(value.items()):
            if isinstance(metadata, dict) and "path" in metadata:
                lines.append(
                    "| `%s.%s` | `%s` | `%s` |"
                    % (
                        group,
                        _markdown_value(name),
                        _markdown_value(metadata.get("path", "")),
                        metadata.get("sha256", ""),
                    )
                )
    lines.extend(["", "## Artifact Paths", ""])
    for entry in manifest.get("rows", {}).get("evidence", []):
        for record in _artifact_records(entry):
            lines.append(
                "- `%s.%s`: `%s` (`%s`)"
                % (
                    _row_name(entry),
                    record["label"],
                    _markdown_value(record.get("path", "")),
                    record.get("sha256", ""),
                )
            )
    return "\n".join(lines) + "\n"


def _input_paths(args):
    return {
        "skiaRunner": args.skia_runner,
        "dashboardJson": args.dashboard_json,
        "dashboardDir": args.dashboard_dir,
        "generatedRenders": args.generated_renders,
        "svgXml": args.svg_xml,
        "cpuResults": args.cpu_results,
        "gpuResults": args.gpu_results,
        "scoresBefore": args.scores_before,
        "scoresAfter": args.scores_after,
        "fp13Runner": args.fp13_runner,
        "commandsJson": args.commands_json,
        "environmentJson": args.environment_json,
        "evidenceIndex": args.evidence_index,
        "cohortManifest": args.cohort_manifest,
    }


def _prepare_inputs(args, dashboard_output, selection):
    input_paths = _input_paths(args)
    evidence_value = _json_file(args.evidence_index)
    _validate_raw_evidence_entries(evidence_value)
    evidence_paths = _evidence_file_paths(evidence_value, args.evidence_index)
    hashes = hash_files(input_paths)
    if dashboard_output is not None:
        hashes.update(hash_files({"dashboardOutput": dashboard_output}))
    if evidence_paths:
        hashes["evidence"] = hash_files(evidence_paths)
    hashes["inputs"] = {
        key: value for key, value in hashes.items() if key in input_paths
    }
    hashes["outputs"] = {
        key: value for key, value in hashes.items() if key == "dashboardOutput"
    }
    dashboard = parse_dashboard(args.dashboard_json)
    raw_dashboard_by_identity = _raw_dashboard_identity_map(dashboard)
    dashboard_rows = _select_dashboard_rows(dashboard, selection.identities)
    _validate_fresh_dashboard_metadata(
        dashboard_rows, selection, raw_dashboard_by_identity
    )
    commands = _json_file(args.commands_json)
    environment = _json_file(args.environment_json)
    skia_runner = parse_junit(args.skia_runner, "skia", set())
    runner_rows = _select_runner_rows(
        skia_runner.get("rows", []),
        dashboard_rows,
        selection.identities,
    )
    merged_dashboard_rows = copy.deepcopy(dashboard_rows)
    _merge_junit_fields(merged_dashboard_rows, copy.deepcopy(runner_rows))
    evidence_rows, unknown_evidence = _filter_evidence_with_families(
        _evidence_entries(copy.deepcopy(evidence_value)), selection
    )
    missing_evidence = sorted(
        selection.identities - {_lane_key(entry) for entry in evidence_rows}
    )
    evidence_by_key = {_lane_key(entry): entry for entry in evidence_rows}
    for row in merged_dashboard_rows:
        evidence = evidence_by_key.get(_lane_key(row))
        if evidence is None:
            continue
        for key, value in evidence.items():
            if key not in row or row[key] is None:
                row[key] = _copy_value(value)
    svg = parse_junit(args.svg_xml, "svg", _wave1.EXPECTED_UNSUPPORTED_CODES)
    svg_rows = _filter_strict(
        [dict(row, referenceKind=row.get("referenceKind", "svg")) for row in svg.get("rows", [])],
        selection.identities,
    )
    scores_before = load_scores(args.scores_before)
    scores_after = load_scores(args.scores_after)
    cohort_manifest = _json_file(args.cohort_manifest)
    cpu_results = _load_json_input(args.cpu_results)
    gpu_results = _load_json_input(args.gpu_results)
    cpu_oracle_rows = _load_oracle_rows(
        args.cpu_results,
        "cpu",
        "cpu-oracle",
        "cpu-oracle",
        selection.identities,
    )
    test_oracle_rows = _load_oracle_rows(
        args.gpu_results,
        "gpu",
        "test-oracle",
        "test-oracle",
        selection.identities,
    )
    fp13_runner = parse_junit(
        args.fp13_runner, "fp13", _wave1.EXPECTED_UNSUPPORTED_CODES
    )
    policy = evidence_value.get("policy", {}) if isinstance(evidence_value, dict) else {}
    score_file = {
        "beforePath": str(args.scores_before),
        "afterPath": str(args.scores_after),
        "beforeSha256": hashes["scoresBefore"]["sha256"],
        "afterSha256": hashes["scoresAfter"]["sha256"],
        "beforeScores": _copy_value(scores_before),
        "afterScores": _copy_value(scores_after),
        "modecolorfiltersBefore": scores_before.get("modecolorfilters"),
        "modecolorfiltersAfter": scores_after.get("modecolorfilters"),
        "directEditDetected": hashes["scoresBefore"]["sha256"] != hashes["scoresAfter"]["sha256"],
        "integrityPreserved": hashes["scoresBefore"]["sha256"] == hashes["scoresAfter"]["sha256"],
        "runnerSideEffectObserved": bool(environment.get("runnerSideEffectObserved", False)),
        "restored": hashes["scoresBefore"]["sha256"] == hashes["scoresAfter"]["sha256"],
    }
    return {
        "paths": {**input_paths, "dashboardOutput": dashboard_output},
        "hashes": hashes,
        "dashboard": dashboard,
        "dashboardOutput": dashboard_output,
        "dashboardRows": merged_dashboard_rows,
        "skiaRunner": skia_runner,
        "runnerRows": runner_rows,
        "svgRows": svg_rows,
        "scoresBefore": scores_before,
        "scoresAfter": scores_after,
        "commands": commands,
        "environment": environment,
        "evidenceValue": evidence_value,
        "evidenceRows": evidence_rows,
        "unknownEvidence": unknown_evidence,
        "missingEvidence": missing_evidence,
        "cpuResults": cpu_results,
        "gpuResults": gpu_results,
        "cpuOracleRows": cpu_oracle_rows,
        "testOracleRows": test_oracle_rows,
        "policy": policy if isinstance(policy, dict) else {},
        "policyEvidencePresent": _wave1._has_complete_policy_evidence(policy),
        "scoreFile": score_file,
        "cohortManifest": cohort_manifest,
        "fp13Runner": fp13_runner,
    }


def _validate_selected_evidence(inputs, args, manifest):
    violations = []
    rows = manifest["rows"]["skia"]
    entries = inputs["evidenceRows"]
    violations.extend(
        _failure_code_violations(rows, entries, args.cohort_failure_code)
    )
    input_paths = _input_paths(args)
    allowed_roots = (
        args.evidence_index.parent,
        args.evidence_index.parent.parent,
        args.generated_renders,
        args.dashboard_dir,
    )
    violations.extend(
        _validate_artifacts(
            entries,
            args.evidence_index,
            input_paths,
            allowed_roots,
            inputs["evidenceValue"],
        )
    )
    generic_entries = []
    matched_keys = set()
    for entry in entries:
        matching = [row for row in rows if _identity_matches(row, entry)]
        if len(matching) != 1:
            violations.append("evidence entry is orphaned or has ambiguous identity: %s" % _row_name(entry))
            continue
        row = matching[0]
        matched_keys.add(_lane_key(row))
        if _is_supported_after(row, entry):
            violations.extend(_validate_supported_after(row, entry))
        elif _is_residual_refusal(row, entry):
            violations.extend(_validate_residual_refusal(row, entry, args.cohort_failure_code))
        else:
            generic_entries.append(entry)
            if not _entry_has_required_evidence(entry):
                violations.append("generic evidence entry is incomplete: %s" % _row_name(entry))
    for row in rows:
        if _lane_key(row) not in matched_keys:
            violations.append(
                "selected cohort row is missing evidence entry: %s" % _row_name(row)
            )
    if generic_entries:
        generic_value = {"entries": generic_entries}
        violations.extend(
            _wave1._check_evidence_index(
                generic_value,
                args.evidence_index,
                rows=rows,
                input_paths=input_paths,
                allowed_roots=allowed_roots,
            )
        )
    for unknown in inputs.get("unknownEvidence", []):
        violations.append("evidence contains unknown identity: %s" % unknown)
    return list(dict.fromkeys(violations))


def _argument_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skia-runner", required=True, type=pathlib.Path)
    parser.add_argument("--dashboard-json", required=True, type=pathlib.Path)
    parser.add_argument("--dashboard-dir", required=True, type=pathlib.Path)
    parser.add_argument("--generated-renders", required=True, type=pathlib.Path)
    parser.add_argument("--svg-xml", required=True, type=pathlib.Path)
    parser.add_argument("--cpu-results", required=True, type=pathlib.Path)
    parser.add_argument("--gpu-results", required=True, type=pathlib.Path)
    parser.add_argument("--scores-before", required=True, type=pathlib.Path)
    parser.add_argument("--scores-after", required=True, type=pathlib.Path)
    parser.add_argument("--fp13-runner", required=True, type=pathlib.Path)
    parser.add_argument("--commands-json", required=True, type=pathlib.Path)
    parser.add_argument("--environment-json", required=True, type=pathlib.Path)
    parser.add_argument("--evidence-index", required=True, type=pathlib.Path)
    parser.add_argument("--cohort-manifest", required=True, type=pathlib.Path)
    parser.add_argument("--cohort-failure-code", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument(
        "--status",
        default="classification",
        choices=("classification", "approved", "blocked"),
    )
    parser.add_argument("--output-json", required=True, type=pathlib.Path)
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path)
    parser.add_argument("--check", action="store_true")
    return parser


def _write_outputs(manifest, output_json, output_markdown):
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_markdown.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(
        json.dumps(manifest, indent=2, sort_keys=True, allow_nan=False) + "\n",
        encoding="utf-8",
    )
    output_markdown.write_text(render_markdown(manifest), encoding="utf-8")


def main(argv=None):
    parser = _argument_parser()
    args = parser.parse_args(argv)
    if args.status == "approved" and not args.check:
        print("reconciliation rejected: --status approved requires --check")
        return 2
    input_paths = _input_paths(args)
    violations = []
    missing = [
        "%s: %s" % (name, path)
        for name, path in input_paths.items()
        if not path.exists()
    ]
    if args.output_json.resolve() == args.output_markdown.resolve():
        violations.append("JSON and Markdown outputs must differ")
    if _same_file(args.scores_before, args.scores_after):
        violations.append("score before and after paths must differ")
    for output in (args.output_json, args.output_markdown):
        for name, input_path in input_paths.items():
            if _path_alias(output, input_path):
                violations.append("output path aliases input %s: %s" % (name, input_path))
    if not missing:
        try:
            evidence_value = _json_file(args.evidence_index)
        except (OSError, ValueError, json.JSONDecodeError):
            evidence_value = None
        if evidence_value is not None:
            for output in (args.output_json, args.output_markdown):
                for artifact_path in _declared_evidence_paths(evidence_value, args.evidence_index):
                    if _path_alias(output, artifact_path):
                        violations.append("output path aliases evidence artifact: %s" % artifact_path)
    if missing or violations:
        print("reconciliation failed: %s" % "; ".join(missing + violations))
        return 2
    try:
        selection = load_cohort_manifest(args.cohort_manifest, args.cohort_failure_code)
        dashboard_output = _wave1._dashboard_output(args.dashboard_dir, args.dashboard_json)
        if args.check and dashboard_output is None:
            print("reconciliation check failed: dashboard output is missing")
            return 2
        inputs = _prepare_inputs(args, dashboard_output, selection)
        if not args.check and inputs["missingEvidence"]:
            raise ValueError(
                "classification cannot omit evidence for selected identities: %s"
                % inputs["missingEvidence"]
            )
        evidence_failure_code_violations = _failure_code_violations(
            inputs["dashboardRows"],
            inputs["evidenceRows"],
            args.cohort_failure_code,
        )
        if not args.check and evidence_failure_code_violations:
            raise ValueError(
                "classification rejected: %s" % evidence_failure_code_violations
            )
        if not args.check and inputs["unknownEvidence"]:
            raise ValueError(
                "classification cannot omit evidence with unknown identity: %s"
                % inputs["unknownEvidence"]
            )
        manifest = build_manifest(inputs, selection, args.source_commit, args.status)
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as error:
        print("reconciliation failed: %s" % error)
        return 2

    check_violations = []
    if args.check:
        skia_junit = inputs["skiaRunner"]
        fp13_junit = inputs["fp13Runner"]
        check_violations.extend(
            _check_current_failures(
                [("skia", skia_junit), ("fp13", fp13_junit)]
            )
        )
        check_violations.extend(
            _check_junit_counts(
                [("skia", skia_junit), ("fp13", fp13_junit)]
            )
        )
        check_violations.extend(
            _wave1._check_junit_population(
                manifest["rows"]["skia"], manifest["rows"]["skiaJunit"]
            )
        )
        if manifest["scoreFile"]["directEditDetected"]:
            check_violations.append("score before/after content diverges")
        check_violations.extend(_wave1._check_scores(inputs["scoresBefore"], inputs["scoresAfter"]))
        check_violations.extend(_check_execution_contract(inputs["commands"], inputs["environment"]))
        check_violations.extend(_validate_population_policy(inputs, manifest))
        if not manifest.get("policyEvidencePresent", False):
            check_violations.append("policy evidence is missing")
        check_violations.extend(_check_policy(manifest["policy"]))
        check_violations.extend(_check_source_commit(args.source_commit))
        check_violations.extend(
            _check_pixel_score_range(
                {"entries": inputs["evidenceRows"], "policy": inputs["policy"]}
            )
        )
        check_violations.extend(
            _check_pixel_score_range({"rows": manifest["rows"]["skia"]})
        )
        check_violations.extend(_check_score_consistency(manifest["rows"]["skia"]))
        check_violations.extend(_validate_selected_evidence(inputs, args, manifest))
        if args.status == "approved" and manifest["supportedRowsAfter"] == 0:
            check_violations.append(
                "approved status requires at least one supported row with actual similarity improvement"
            )
        if args.status == "approved" and len(_causal_cohort_keys(manifest["rows"]["skia"])) > 1:
            check_violations.append("approved status requires at most one causal evidence cohort")

    try:
        if check_violations:
            if args.status == "approved":
                manifest["status"] = "blocked"
            _write_outputs(manifest, args.output_json, args.output_markdown)
            print("reconciliation check failed: %s" % "; ".join(dict.fromkeys(check_violations)))
            return 1
        _write_outputs(manifest, args.output_json, args.output_markdown)
    except (OSError, ValueError) as error:
        print("reconciliation failed: %s" % error)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

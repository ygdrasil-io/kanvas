#!/usr/bin/env python3
"""Reconcile the Skia fidelity Wave 1 evidence without rewriting inputs."""

import argparse
import datetime
import hashlib
import json
import pathlib
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET


SCHEMA_VERSION = 1
KIND = "skia-fidelity-wave-1"
GENERATED_BY = "reconcile_skia_fidelity_wave1.py"
EXPECTED_UNSUPPORTED_CODES = {
    "unsupported.core_primitive.geometry.invalid",
    "unsupported.material.linear_gradient_capability_missing",
    "unsupported.geometry.path_key_nondeterministic",
    "unsupported.core_primitive.stencil_edge_fan_budget",
}
FAILURE_CODE_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_.-])(?:unsupported|failed|invalid|stale)\.[A-Za-z0-9_.-]+"
)
SCORE_KEYS = ("modecolorfilters",)


def _local_name(tag):
    return tag.rsplit("}", 1)[-1]


def _element_message(element):
    if element is None:
        return ""
    message = element.attrib.get("message", "").strip()
    if message:
        return message
    return " ".join(part.strip() for part in element.itertext() if part.strip())


def _failure_code(message, element, outcome, expected_codes):
    candidates = [message or ""]
    if element is not None:
        candidates.extend(element.attrib.values())
        candidates.extend(element.itertext())
    for candidate in candidates:
        for expected_code in sorted(expected_codes, key=len, reverse=True):
            if expected_code and expected_code in candidate:
                return expected_code
        match = FAILURE_CODE_PATTERN.search(candidate)
        if match:
            return match.group(0)
    exception_type = element.attrib.get("type", "") if element is not None else ""
    if "TestAbortedException" in exception_type or "TestAbortedException" in message:
        return "TestAbortedException"
    if outcome == "skipped" and "aborted" in message.lower():
        return "TestAbortedException"
    return None


def _is_missing_reference(message):
    lowered = message.lower()
    return (
        "missing reference" in lowered
        or "missing-reference" in lowered
        or "reference png not found" in lowered
        or "reference image not found" in lowered
        or "reference file not found" in lowered
        or "no reference" in lowered
    )


def _is_size_mismatch(message):
    lowered = message.lower()
    return (
        "size mismatch" in lowered
        or "size-mismatch" in lowered
        or "buffer sizes differ" in lowered
        or "sizes differ" in lowered
        or "dimension mismatch" in lowered
        or "dimensions differ" in lowered
    )


def _is_similarity_failure(message):
    lowered = message.lower()
    return (
        "similarity" in lowered
        and any(token in lowered for token in ("below", "threshold", "failed", "failure"))
    ) or "below-threshold" in lowered


def _is_lifecycle_failure(message, failure_code, failure_type=""):
    lowered = message.lower()
    lowered_type = str(failure_type or "").lower()
    return bool(
        failure_code == "failed.surface.prepared.session-close"
        or "session-close" in lowered
        or "lifecycle" in lowered
        or "lifecycle" in lowered_type
        or "teardown" in lowered
        or "shutdown" in lowered
    )


def _is_terminal_failure(message, failure_type, failure_code):
    lowered_message = message.lower()
    lowered_type = failure_type.lower()
    return bool(
        "terminal" in lowered_type
        or "terminal" in lowered_message
        or "refusal" in lowered_message
        or "aggregate_budget_exceeded" in (failure_code or "")
    )


def _truthy(value):
    if value is True:
        return True
    if isinstance(value, str):
        return value.strip().lower() in {"true", "yes", "on", "1"}
    return False


def _compact_name(value):
    return re.sub(r"[^a-z0-9]", "", str(value).strip().lower())


def _junit_identity(name, attributes=None):
    display_name = str(name or "").strip()
    attributes = attributes if isinstance(attributes, dict) else {}
    registration = re.sub(r"^\s*\[[^]]+\]\s*", "", display_name)
    source_registration = next(
        (
            str(attributes[key]).strip()
            for key in ("sourceRegistration", "registration", "sourceRegistrationId")
            if _nonempty(attributes.get(key))
        ),
        registration,
    )
    source_class_path = source_registration.split("@", 1)[0].strip()
    simple_source_class = source_class_path.rsplit(".", 1)[-1]
    if not simple_source_class:
        simple_source_class = source_registration.rsplit(".", 1)[-1]
    source_class = next(
        (
            str(attributes[key]).strip()
            for key in ("sourceClass", "sourceClassName")
            if _nonempty(attributes.get(key))
        ),
        simple_source_class,
    )
    logical_name = _compact_name(source_class)
    if logical_name.endswith("gm"):
        logical_name = logical_name[:-2]
    candidates = []
    for candidate in (
        display_name,
        source_registration,
        source_class_path,
        simple_source_class,
        source_class,
        logical_name,
    ):
        if candidate and candidate not in candidates:
            candidates.append(candidate)
    return {
        "displayName": display_name,
        "sourceRegistration": source_registration,
        "sourceClass": source_class,
        "sourceClassPath": source_class_path,
        "logicalName": logical_name,
        "nameCandidates": candidates,
    }


def _classify_row(outcome, message, failure_code, failure_type, expected_codes):
    expected_unsupported = failure_code in expected_codes
    missing_reference = _is_missing_reference(message)
    size_mismatch = _is_size_mismatch(message)
    similarity_failure = _is_similarity_failure(message)
    lifecycle_failure = _is_lifecycle_failure(message, failure_code, failure_type)
    terminal_refusal = _is_terminal_failure(message, failure_type, failure_code)

    if outcome == "skipped" and lifecycle_failure:
        classification = "lifecycle-failure"
    elif outcome == "skipped" and terminal_refusal:
        classification = "terminal-refusal"
    elif outcome == "skipped":
        classification = "skip"
    elif expected_unsupported:
        classification = "expected-unsupported"
    elif lifecycle_failure:
        classification = "lifecycle-failure"
    elif missing_reference:
        classification = "missing-reference"
    elif size_mismatch:
        classification = "size-mismatch"
    elif similarity_failure:
        classification = "similarity-failure"
    elif terminal_refusal:
        classification = "terminal-refusal"
    elif outcome in {"failure", "error"}:
        classification = "unclassified"
    else:
        classification = "pass"

    return {
        "classification": classification,
        "terminal": terminal_refusal,
        "terminalRefusal": terminal_refusal,
        "expectedUnsupported": expected_unsupported,
        "expectedRefusal": expected_unsupported,
        "missingReference": missing_reference,
        "sizeMismatch": size_mismatch,
        "similarityFailure": similarity_failure,
        "lifecycleFailure": lifecycle_failure,
    }


def parse_junit(path: pathlib.Path, suite: str, expected_codes: set[str]) -> dict:
    """Parse JUnit testcase rows and classify failures without changing the XML."""
    root = ET.parse(path).getroot()
    rows = []
    suites = [
        element for element in root.iter() if _local_name(element.tag) == "testsuite"
    ]
    if not suites:
        suites = [root]
    seen_testcases = set()

    def append_testcase(testcase, suite_element):
        failure = next(
            (child for child in testcase if _local_name(child.tag) == "failure"), None
        )
        error = next(
            (child for child in testcase if _local_name(child.tag) == "error"), None
        )
        skipped = next(
            (child for child in testcase if _local_name(child.tag) == "skipped"), None
        )
        detail = (
            failure
            if failure is not None
            else error
            if error is not None
            else skipped
        )
        if failure is not None:
            outcome = "failure"
        elif error is not None:
            outcome = "error"
        elif skipped is not None:
            outcome = "skipped"
        else:
            outcome = "passed"

        message = _element_message(detail)
        failure_type = detail.attrib.get("type", "") if detail is not None else ""
        failure_code = (
            _failure_code(message, detail, outcome, expected_codes)
            if detail is not None
            else None
        )
        class_name = testcase.attrib.get("classname", testcase.attrib.get("class", ""))
        identity = _junit_identity(testcase.attrib.get("name", ""), testcase.attrib)
        row = {
            "name": testcase.attrib.get("name", ""),
            "class": class_name,
            "className": class_name,
            "sourceClass": identity["sourceClass"],
            "sourceRegistration": identity["sourceRegistration"],
            "gmIdentity": identity,
            "suite": suite,
            "evidenceLane": suite,
            "outcome": outcome,
            "message": message,
            "failureType": failure_type,
            "failureCode": failure_code,
            "_junitSuiteId": id(suite_element),
        }
        row.update(
            _classify_row(
                outcome,
                message,
                failure_code,
                failure_type,
                expected_codes,
            )
        )
        rows.append(row)

    for suite_element in suites:
        for element in suite_element.iter():
            if _local_name(element.tag) != "testcase" or id(element) in seen_testcases:
                continue
            seen_testcases.add(id(element))
            append_testcase(element, suite_element)

        for child in suite_element:
            child_name = _local_name(child.tag).lower()
            if child_name == "testcase":
                continue
            if child_name not in {
                "error",
                "failure",
                "skipped",
                "lifecycle",
                "lifecycleerror",
                "lifecyclefailure",
            } and "lifecycle" not in child_name:
                continue
            outcome = (
                "failure"
                if child_name == "failure"
                else "skipped"
                if child_name == "skipped"
                else "error"
            )
            message = _element_message(child)
            failure_type = child.attrib.get("type", "")
            if "lifecycle" in child_name and not failure_type:
                failure_type = "lifecycle"
            failure_code = _failure_code(message, child, outcome, expected_codes)
            suite_name = suite_element.attrib.get("name", suite)
            identity = _junit_identity(suite_name, suite_element.attrib)
            class_name = suite_element.attrib.get(
                "classname", suite_element.attrib.get("class", "")
            )
            row = {
                "name": "[suite] %s:%s" % (suite_name, child_name),
                "class": class_name,
                "className": class_name,
                "sourceClass": identity["sourceClass"],
                "sourceRegistration": identity["sourceRegistration"],
                "gmIdentity": identity,
                "suite": suite,
                "evidenceLane": suite,
                "outcome": outcome,
                "message": message,
                "failureType": failure_type,
                "failureCode": failure_code,
                "synthetic": True,
                "suiteLevel": True,
                "_junitSuiteId": id(suite_element),
            }
            row.update(
                _classify_row(
                    outcome,
                    message,
                    failure_code,
                    failure_type,
                    expected_codes,
                )
            )
            rows.append(row)

    declared = {}
    for field in ("tests", "failures", "errors", "skipped"):
        value = root.attrib.get(field)
        try:
            declared[field] = int(value) if value is not None else None
        except (TypeError, ValueError):
            declared[field] = None

    counts = {
        "tests": declared["tests"] if declared["tests"] is not None else len(rows),
        "failures": (
            declared["failures"]
            if declared["failures"] is not None
            else sum(row["outcome"] == "failure" for row in rows)
        ),
        "errors": (
            declared["errors"]
            if declared["errors"] is not None
            else sum(row["outcome"] == "error" for row in rows)
        ),
        "skips": (
            declared["skipped"]
            if declared["skipped"] is not None
            else sum(row["outcome"] == "skipped" for row in rows)
        ),
    }
    counts["passed"] = sum(row["outcome"] == "passed" for row in rows)
    counts["unexpectedFailures"] = sum(
        row["outcome"] in {"failure", "error"}
        and not row["expectedUnsupported"]
        and row["classification"] != "skip"
        for row in rows
    )
    counts["unclassifiedFailures"] = sum(
        row["classification"] == "unclassified" for row in rows
    )
    counts["aborted"] = sum(
        row["failureCode"] == "TestAbortedException" for row in rows
    )
    counts["terminalFailures"] = sum(row["terminalRefusal"] for row in rows)
    counts["expectedUnsupported"] = sum(row["expectedUnsupported"] for row in rows)
    counts["missingReferences"] = sum(row["missingReference"] for row in rows)
    counts["sizeMismatches"] = sum(row["sizeMismatch"] for row in rows)
    counts["similarityFailures"] = sum(row["similarityFailure"] for row in rows)
    counts["lifecycleFailures"] = sum(row["lifecycleFailure"] for row in rows)
    parsed_counts = {
        "tests": len(rows),
        "failures": sum(row["outcome"] == "failure" for row in rows),
        "errors": sum(row["outcome"] == "error" for row in rows),
        "skipped": sum(row["outcome"] == "skipped" for row in rows),
    }
    count_mismatches = []

    def check_declared(scope, attributes, scoped_rows):
        if not isinstance(attributes, dict):
            return
        scoped_counts = {
            "tests": len(scoped_rows),
            "failures": sum(row["outcome"] == "failure" for row in scoped_rows),
            "errors": sum(row["outcome"] == "error" for row in scoped_rows),
            "skipped": sum(row["outcome"] == "skipped" for row in scoped_rows),
        }
        for field in ("tests", "failures", "errors", "skipped"):
            if field not in attributes:
                continue
            try:
                expected = int(attributes[field])
            except (TypeError, ValueError):
                count_mismatches.append(
                    "%s declares non-integer %s=%r"
                    % (scope, field, attributes[field])
                )
                continue
            actual = scoped_counts[field]
            if expected != actual:
                count_mismatches.append(
                    "%s declares %s=%s but parsed %s row(s)"
                    % (scope, field, expected, actual)
                )

    check_declared("root", root.attrib, rows)
    for suite_element in suites:
        if suite_element is root:
            continue
        scoped_rows = [
            row
            for row in rows
            if row.get("_junitSuiteId") == id(suite_element)
        ]
        check_declared(
            "testsuite %s" % suite_element.attrib.get("name", suite),
            suite_element.attrib,
            scoped_rows,
        )
    for row in rows:
        row.pop("_junitSuiteId", None)
    return {
        **counts,
        "parsedCounts": parsed_counts,
        "declaredCounts": declared,
        "countMismatches": list(dict.fromkeys(count_mismatches)),
        "suite": suite,
        "rows": rows,
    }


def parse_dashboard(path: pathlib.Path) -> dict:
    """Load dashboard JSON while accepting the common gms/rows/results shapes."""
    value = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(value, list):
        return {"gms": value}
    if not isinstance(value, dict):
        raise ValueError("dashboard JSON must contain an object or array")
    if "gms" in value and not isinstance(value["gms"], (list, dict)):
        raise ValueError("dashboard gms must contain an array or object")
    if "rows" in value and not isinstance(value["rows"], list):
        raise ValueError("dashboard rows must contain an array")
    if "results" in value and not isinstance(value["results"], list):
        raise ValueError("dashboard results must contain an array")
    containers = []
    for key in ("gms", "rows", "results"):
        if key not in value:
            continue
        container = value[key]
        if key == "gms" and isinstance(container, dict):
            container = [
                dict(entry, name=name) if isinstance(entry, dict) else {"name": name, "value": entry}
                for name, entry in container.items()
            ]
        containers.append(
            (
                key,
                json.dumps(container, sort_keys=True, separators=(",", ":")),
            )
        )
    if len({serialized for _, serialized in containers}) > 1:
        raise ValueError(
            "dashboard JSON contains contradictory row containers: %s"
            % ", ".join(key for key, _ in containers)
        )
    if "gms" not in value:
        for key in ("rows", "results"):
            if isinstance(value.get(key), list):
                value = {**value, "gms": value[key]}
                break
    return value


def _logical_property_lines(lines):
    logical = ""
    continued = False
    for raw_line in lines:
        line = raw_line.rstrip("\r")
        if continued:
            line = line.lstrip(" \t\f")
        logical += line
        trailing_backslashes = 0
        for character in reversed(logical):
            if character != "\\":
                break
            trailing_backslashes += 1
        if trailing_backslashes % 2:
            logical = logical[:-1]
            continued = True
        else:
            yield logical
            logical = ""
            continued = False
    if continued:
        yield logical


def _split_property(line):
    line = line.lstrip(" \t\f")
    if not line or line[0] in "#!":
        return None
    escaped = False
    separator_index = None
    separator = None
    for index, character in enumerate(line):
        if escaped:
            escaped = False
            continue
        if character == "\\":
            escaped = True
            continue
        if character in "=: \t\f":
            separator_index = index
            separator = character
            break
    if separator_index is None:
        return line, ""
    key = line[:separator_index]
    value_index = separator_index
    if separator in " \t\f":
        while value_index < len(line) and line[value_index] in " \t\f":
            value_index += 1
        if value_index < len(line) and line[value_index] in "=:":
            value_index += 1
    else:
        value_index += 1
    while value_index < len(line) and line[value_index] in " \t\f":
        value_index += 1
    return key, line[value_index:]


def _unescape_property(value):
    result = []
    index = 0
    escapes = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}
    while index < len(value):
        character = value[index]
        if character != "\\" or index + 1 == len(value):
            result.append(character)
            index += 1
            continue
        escaped = value[index + 1]
        if escaped == "u" and index + 5 < len(value):
            digits = value[index + 2 : index + 6]
            if re.fullmatch(r"[0-9A-Fa-f]{4}", digits):
                result.append(chr(int(digits, 16)))
                index += 6
                continue
        result.append(escapes.get(escaped, escaped))
        index += 2
    return "".join(result)


def load_scores(path: pathlib.Path) -> dict[str, float]:
    """Load numeric score properties without changing the source file."""
    scores = {}
    for line_number, line in enumerate(
        _logical_property_lines(path.read_text(encoding="utf-8").splitlines()), 1
    ):
        property_pair = _split_property(line)
        if property_pair is None:
            continue
        key, value = property_pair
        key = _unescape_property(key).strip()
        value = _unescape_property(value).strip()
        if not key or not value:
            continue
        try:
            scores[key] = float(value)
        except ValueError as error:
            raise ValueError("invalid score on line %s: %s" % (line_number, line)) from error
    return scores


def _sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def _sha256_path(path):
    if path.is_file():
        return _sha256_file(path)
    if path.is_dir():
        digest = hashlib.sha256()
        for child in sorted(path.rglob("*")):
            if not child.is_file():
                continue
            digest.update(str(child.relative_to(path)).encode("utf-8"))
            digest.update(b"\0")
            digest.update(child.read_bytes())
            digest.update(b"\0")
        return digest.hexdigest()
    raise FileNotFoundError(str(path))


def hash_files(paths: dict[str, pathlib.Path]) -> dict[str, dict[str, str]]:
    """Return stable path and SHA-256 metadata for files or directories."""
    result = {}
    for name, path in paths.items():
        path = pathlib.Path(path)
        result[name] = {
            "path": str(path),
            "sha256": _sha256_path(path),
        }
    return result


def _json_rows(value):
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ("rows", "gms", "results"):
            if isinstance(value.get(key), list):
                return value[key]
    return []


def _load_json_input(path):
    path = pathlib.Path(path)
    if path.is_file():
        return json.loads(path.read_text(encoding="utf-8"))
    if path.is_dir():
        rows = []
        values = []
        for child in sorted(path.rglob("*.json")):
            value = json.loads(child.read_text(encoding="utf-8"))
            values.append(value)
            rows.extend(_json_rows(value))
            if not _json_rows(value) and isinstance(value, dict) and any(
                key in value for key in ("name", "gm", "id")
            ):
                rows.append(value)
        if rows:
            return {"rows": rows}
        if len(values) == 1:
            return values[0]
        return {"rows": []}
    raise FileNotFoundError(str(path))


def _dashboard_entries(dashboard):
    entries = _json_rows(dashboard)
    if isinstance(dashboard, dict) and isinstance(dashboard.get("gms"), dict):
        entries = [
            dict(entry, name=name) if isinstance(entry, dict) else {"name": name, "value": entry}
            for name, entry in dashboard["gms"].items()
        ]
    rows = []
    for entry in entries:
        if isinstance(entry, dict):
            row = dict(entry)
            name = row.get("name", row.get("gm", row.get("id", "")))
        else:
            row = {}
            name = entry
        row["name"] = str(name)
        if not row.get("referenceKind"):
            row["referenceKind"] = "skia-upstream"
        if row.get("dimensions") is None:
            render_dimensions, reference_dimensions = _entry_dimensions(row)
            if render_dimensions is not None and render_dimensions == reference_dimensions:
                width, height = render_dimensions
                row["dimensions"] = {
                    "render": {"width": width, "height": height},
                    "reference": {"width": width, "height": height},
                }
        row["evidenceLane"] = "skia-dashboard"
        row.setdefault("routeOnly", False)
        row["classification"] = _dashboard_classification(row)
        rows.append(row)
    return rows


def _name_keys(value):
    text = str(value or "").strip()
    return {
        ("raw", text),
        ("normalized", _normalized_name(text)),
        ("compact", _compact_name(text)),
    }


def _junit_name_keys(row):
    identity = row.get("gmIdentity") if isinstance(row, dict) else None
    candidates = identity.get("nameCandidates", []) if isinstance(identity, dict) else []
    if not candidates:
        candidates = [_row_name(row)]
    keys = set()
    for candidate in candidates:
        keys.update(_name_keys(candidate))
    return keys


def _merge_junit_fields(dashboard_rows, runner_rows):
    fields = (
        "outcome",
        "message",
        "failureType",
        "failureCode",
        "classification",
        "terminal",
        "terminalRefusal",
        "expectedUnsupported",
        "expectedRefusal",
        "missingReference",
        "sizeMismatch",
        "similarityFailure",
        "lifecycleFailure",
    )
    metadata_fields = (
        "class",
        "className",
        "sourceClass",
        "sourceRegistration",
        "gmIdentity",
    )
    dashboard_keys = [_name_keys(_row_name(row)) for row in dashboard_rows]
    claims = {}
    ambiguous_dashboard = set()
    for runner_index, junit in enumerate(runner_rows):
        matches = [
            dashboard_index
            for dashboard_index, keys in enumerate(dashboard_keys)
            if _junit_name_keys(junit) & keys
        ]
        if len(matches) != 1:
            if len(matches) > 1:
                ambiguous_dashboard.update(matches)
            continue
        claims.setdefault(matches[0], []).append((runner_index, junit))

    for dashboard_index, row in enumerate(dashboard_rows):
        row["junitMissing"] = True
        claimants = claims.get(dashboard_index, [])
        if len(claimants) != 1 or dashboard_index in ambiguous_dashboard:
            if claimants or dashboard_index in ambiguous_dashboard:
                row["junitAmbiguous"] = True
            continue
        _, junit = claimants[0]
        row["junitMissing"] = False
        row["junit"] = _copy_value(junit)
        for field in fields:
            if field not in junit:
                continue
            if not _nonempty(row.get(field)):
                row[field] = _copy_value(junit[field])
        for field in metadata_fields:
            if field not in junit:
                continue
            if _nonempty(row.get(field)) and _nonempty(junit.get(field)):
                if _copy_value(row[field]) != _copy_value(junit[field]):
                    row["junitMetadataConflict"] = True
            elif not _nonempty(row.get(field)):
                row[field] = _copy_value(junit[field])


def _junit_is_pass(row):
    if row.get("junitAmbiguous") or row.get("junitMissing"):
        return False
    junit = row.get("junit") if isinstance(row, dict) else None
    if junit is None:
        return False
    if row.get("junitMetadataConflict"):
        return False
    return junit.get("outcome") == "passed" and junit.get("classification") == "pass"


def _junit_candidate_for_approval(row):
    if not isinstance(row, dict) or row.get("referenceKind") != "skia-upstream":
        return False
    if _is_route_only(row) or row.get("sizeMismatch") or row.get("noReference"):
        return False
    if row.get("classification") in {
        "missing-reference",
        "size-mismatch",
        "expected-unsupported",
        "no-score",
        "skip",
        "route-only",
        "failure",
        "terminal-refusal",
        "lifecycle-failure",
        "unclassified",
    }:
        return False
    score = _finite_number(row.get("score", row.get("similarity")))
    if score is None or not 0 <= score <= 100:
        return False
    render_dimensions, reference_dimensions = _entry_dimensions(row)
    return (
        render_dimensions is not None
        and reference_dimensions is not None
        and render_dimensions == reference_dimensions
    )


def _junit_approval_violations(rows):
    violations = []
    for row in rows:
        if not _junit_candidate_for_approval(row):
            continue
        if row.get("junitAmbiguous"):
            violations.append(
                "ambiguous JUnit identity blocks approval for %s" % row.get("name", "")
            )
            continue
        if row.get("junitMissing"):
            violations.append("missing JUnit result blocks approval for %s" % row.get("name", ""))
            continue
        if row.get("junitMetadataConflict"):
            violations.append(
                "JUnit identity metadata conflict blocks approval for %s"
                % row.get("name", "")
            )
            continue
        junit = row.get("junit") if isinstance(row, dict) else None
        if not isinstance(junit, dict):
            violations.append("missing JUnit result blocks approval for %s" % row.get("name", ""))
            continue
        if _junit_is_pass(row):
            continue
        classification = junit.get("classification", "non-pass")
        if classification in {"terminal-refusal", "lifecycle-failure"}:
            violations.append(
                "terminal/lifecycle JUnit result blocks approval for %s"
                % row.get("name", "")
            )
        else:
            violations.append(
                "non-pass JUnit result blocks approval for %s" % row.get("name", "")
            )
    return violations


def _dashboard_classification(row):
    cause = str(row.get("noScoreCause") or "").strip().lower()
    if _truthy(row.get("routeOnly")) or _normalized_name(_row_name(row)) == "route-only":
        return "route-only"
    if row.get("renderFailed"):
        return "failure"
    if row.get("noReference") or cause in {"reference-missing", "missing-reference"}:
        return "missing-reference"
    if row.get("sizeMismatch") or cause in {"size-mismatch", "dimension-mismatch"}:
        return "size-mismatch"
    if row.get("dimensions") is not None:
        render_dimensions, reference_dimensions = _entry_dimensions(row)
        if (
            render_dimensions is None
            or reference_dimensions is None
            or render_dimensions != reference_dimensions
        ):
            return "size-mismatch"
    if row.get("referenceUntrustable") or cause == "reference-untrustable":
        return "expected-unsupported"
    score_supplied = False
    score = None
    for score_key in ("score", "similarity"):
        if score_key in row and row.get(score_key) is not None:
            score_supplied = True
            score = _finite_number(row.get(score_key))
            break
    if score is not None:
        minimum = _finite_number(row.get("minSimilarity"))
        minimum = 95.0 if minimum is None else minimum
        if not 0 <= score <= 100 or score < minimum or row.get("isPassing") is False:
            return "similarity-failure"
        return "pass"
    if row.get("isPassing") is False:
        return "similarity-failure"
    if row.get("isPassing") is True and not score_supplied:
        return "pass"
    return "no-score"


def _oracle_entries(value, lane, reference_kind):
    rows = []
    entries = _json_rows(value)
    if not entries and isinstance(value, dict) and any(
        key in value for key in ("name", "gm", "id")
    ):
        entries = [value]
    for entry in entries:
        if isinstance(entry, dict):
            row = dict(entry)
            row["name"] = str(row.get("name", row.get("gm", row.get("id", ""))))
        else:
            row = {"name": str(entry)}
        row.setdefault("referenceKind", reference_kind)
        row["evidenceLane"] = lane
        row.setdefault("routeOnly", _normalized_name(row["name"]) == "route-only")
        if "classification" not in row:
            row["classification"] = "route-only" if _truthy(row["routeOnly"]) else "pass"
        rows.append(row)
    return rows


def _summary(value, row_key="rows"):
    if not isinstance(value, dict):
        return {"rows": 0}
    result = {}
    for key in (
        "tests",
        "failures",
        "errors",
        "skips",
        "aborted",
        "passed",
        "unexpectedFailures",
        "unclassifiedFailures",
        "terminalFailures",
        "expectedUnsupported",
        "missingReferences",
        "sizeMismatches",
        "similarityFailures",
        "lifecycleFailures",
    ):
        if key in value:
            result[key] = value[key]
    rows = value.get(row_key, [])
    result["rows"] = len(rows) if isinstance(rows, list) else 0
    return result


def _numeric(value):
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return None


def _finite_number(value):
    number = _numeric(value)
    if number is None or number != number or number in (float("inf"), float("-inf")):
        return None
    return number


def _is_pixel_score_key(key):
    return re.sub(r"[^a-z0-9]", "", str(key).lower()) in {
        "score",
        "similarity",
        "scorebefore",
        "scoreafter",
        "similaritybefore",
        "similarityafter",
        "beforescore",
        "afterscore",
        "beforesimilarity",
        "aftersimilarity",
        "modecolorfilters",
        "modecolorfiltersbefore",
        "modecolorfiltersafter",
    }


def _copy_value(value, score_context=False):
    if isinstance(value, dict):
        copied = {}
        for key, item in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", str(key).lower())
            item_is_score = _is_pixel_score_key(key) or (
                score_context
                and normalized in {"before", "after", "baseline", "previous", "current", "value"}
            )
            if item_is_score and item is not None:
                number = _finite_number(item)
                if number is None or not 0 <= number <= 100:
                    copied[key] = None
                    continue
            child_score_context = score_context or normalized in {
                "comparison",
                "similarity",
                "scores",
                "scoresbefore",
                "scoresafter",
            }
            copied[key] = _copy_value(item, child_score_context)
        return copied
    if isinstance(value, list):
        return [_copy_value(item, score_context) for item in value]
    if isinstance(value, float) and (value != value or value in (float("inf"), float("-inf"))):
        return None
    return value


def _nonempty(value):
    return value is not None and value is not False and value != "" and value != [] and value != {}


def _evidence_sources(row, evidence):
    sources = []
    for value in (row, evidence):
        if not isinstance(value, dict):
            continue
        sources.append(value)
        for key in ("evidence", "causalEvidence", "pixelEvidence", "current"):
            nested = value.get(key)
            if isinstance(nested, dict):
                sources.append(nested)
    return sources


def _has_any_evidence_value(sources, keys):
    return any(_nonempty(source.get(key)) for source in sources for key in keys)


def _has_causal_evidence(row, evidence):
    sources = _evidence_sources(row, evidence)
    return (
        any(source.get("candidateUnlocked") is True for source in sources)
        and _has_any_evidence_value(
            sources,
            (
                "causalBucket",
                "currentCausalBucket",
                "rootCause",
                "rootCauseBucket",
            ),
        )
        and _has_any_evidence_value(
            sources,
            (
                "routeDiagnostic",
                "routeDiagnostics",
                "routeSignature",
                "route",
            ),
        )
        and _has_any_evidence_value(
            sources,
            (
                "minimalOperationTrace",
                "operationTrace",
                "operationTracePath",
                "opTrace",
                "trace",
            ),
        )
        and _has_any_evidence_value(
            sources,
            (
                "ownershipBoundary",
                "ownership",
                "owner",
                "boundary",
            ),
        )
    )


def _causal_value(sources, keys):
    for source in sources:
        if not isinstance(source, dict):
            continue
        for key in keys:
            value = source.get(key)
            if _nonempty(value):
                return value
    return None


def _causal_cohort_key(evidence):
    sources = _evidence_sources({}, evidence)
    values = (
        _causal_value(
            sources,
            ("currentCausalBucket", "causalBucket", "rootCauseBucket", "rootCause"),
        ),
        _causal_value(
            sources,
            ("routeSignature", "routeDiagnostic", "routeDiagnostics", "route"),
        ),
        _causal_value(
            sources,
            ("minimalOperationTrace", "operationTrace", "operationTracePath", "opTrace", "trace"),
        ),
        _causal_value(
            sources,
            ("ownershipBoundary", "ownership", "owner", "boundary"),
        ),
    )
    if any(value is None for value in values):
        return None
    return tuple(
        json.dumps(_copy_value(value), sort_keys=True, separators=(",", ":"))
        if isinstance(value, (dict, list))
        else str(value)
        for value in values
    )


def _causal_cohort_keys(rows):
    cohorts = set()
    for row in rows if isinstance(rows, list) else []:
        if not isinstance(row, dict):
            continue
        evidence = row.get("evidenceIndexEntry", row.get("evidence"))
        if (
            _valid_comparable(row, evidence)
            and _has_causal_evidence({}, evidence)
        ):
            cohort = _causal_cohort_key(evidence)
            if cohort is not None:
                cohorts.add(cohort)
    return cohorts


def _runner_side_effect_observed(*sources):
    for source in sources:
        if not isinstance(source, dict):
            continue
        for key in ("runnerSideEffectObserved", "sideEffectObserved"):
            if isinstance(source.get(key), bool):
                return source[key]
        nested = [item for item in source.values() if isinstance(item, (dict, list))]
        for item in nested:
            values = item if isinstance(item, list) else [item]
            observed = _runner_side_effect_observed(*values)
            if observed:
                return True
    return False


def _policy_violation(value):
    if value is True:
        return True
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "yes", "on"}:
            return True
    number = _finite_number(value)
    return number is not None and number != 0


def _check_policy(policy):
    violations = []
    if not isinstance(policy, dict):
        return ["policy object is missing"]
    required_keys = (
        "globalThresholdWeakened",
        "assertionsWeakened",
        "referencesModified",
        "memoryBudgetChanged",
        "readinessDelta",
    )
    missing_keys = [key for key in required_keys if key not in policy]
    if missing_keys:
        violations.append("policy evidence is missing: %s" % ", ".join(missing_keys))
    for key, label in (
        ("globalThresholdWeakened", "global threshold"),
        ("assertionsWeakened", "assertions"),
        ("referencesModified", "references"),
        ("memoryBudgetChanged", "memory budget"),
    ):
        if _policy_violation(policy.get(key, False)):
            violations.append("policy violation: %s changed" % label)
    readiness = policy.get("readinessDelta", 0.0)
    if _finite_number(readiness) is None or _finite_number(readiness) != 0:
        violations.append("policy violation: readinessDelta is non-zero or invalid")
    return violations


def _generated_at(inputs):
    return (
        datetime.datetime.now(datetime.timezone.utc)
        .isoformat()
        .replace("+00:00", "Z")
    )


def _evidence_entries(value):
    if isinstance(value, dict):
        entries = value.get("entries", value.get("rows", []))
    else:
        entries = value
    if not isinstance(entries, list):
        return []
    return [dict(entry) for entry in entries if isinstance(entry, dict)]


def _normalized_name(value):
    return re.sub(r"[^a-z0-9]+", "-", str(value).strip().lower()).strip("-")


def _row_name(row):
    if not isinstance(row, dict):
        return ""
    for key in ("name", "gm", "id"):
        value = row.get(key)
        if value is not None and str(value).strip():
            return value
    return ""


def _is_route_only(row):
    return bool(
        isinstance(row, dict)
        and (
            _truthy(row.get("routeOnly"))
            or str(row.get("classification", "")).lower() == "route-only"
            or _normalized_name(_row_name(row)) == "route-only"
        )
    )


def _entry_key(row):
    metadata = _identity_metadata(row)
    return _lane_key(row) + (metadata,)


def _identity_metadata(row):
    metadata = []
    sources = [row] if isinstance(row, dict) else []
    for key in ("source", "sourceMetadata", "metadata", "gmIdentity"):
        nested = row.get(key) if isinstance(row, dict) else None
        if isinstance(nested, dict):
            sources.append(nested)
    for label, keys in (
        ("className", ("className", "class")),
        ("sourceClass", ("sourceClass", "sourceClassName")),
        (
            "sourceRegistration",
            ("sourceRegistration", "registration", "sourceRegistrationId", "source"),
        ),
    ):
        value = next(
            (
                source.get(key)
                for source in sources
                for key in keys
                if _nonempty(source.get(key))
                and not isinstance(source.get(key), (dict, list))
            ),
            None,
        )
        if value is not None:
            metadata.append((label, str(value)))
    return tuple(metadata)


def _lane_key(row):
    return str(_row_name(row)), str(row.get("referenceKind", ""))


def _identity_matches(left, right):
    if _lane_key(left) != _lane_key(right):
        return False
    left_metadata = dict(_identity_metadata(left))
    right_metadata = dict(_identity_metadata(right))
    return all(
        left_metadata[key] == right_metadata[key]
        for key in left_metadata.keys() & right_metadata.keys()
    )


def _identity_metadata_conflicts(left, right):
    left_metadata = dict(_identity_metadata(left))
    right_metadata = dict(_identity_metadata(right))
    return any(
        left_metadata[key] != right_metadata[key]
        for key in left_metadata.keys() & right_metadata.keys()
    )


def _matching_evidence(row, entries):
    return [entry for entry in entries if _identity_matches(row, entry)]


def _evidence_for_row(row, entries):
    matches = _matching_evidence(row, entries)
    return matches[0] if len(matches) == 1 else None


def _dimension_pair(value):
    if not isinstance(value, dict):
        return None
    width = _finite_number(value.get("width", value.get("w")))
    height = _finite_number(value.get("height", value.get("h")))
    if width is None or height is None or width <= 0 or height <= 0:
        return None
    if width != int(width) or height != int(height):
        return None
    return int(width), int(height)


def _has_valid_dimensions(value):
    if isinstance(value, (tuple, list)) and len(value) == 2:
        width = _finite_number(value[0])
        height = _finite_number(value[1])
        return (
            width is not None
            and height is not None
            and width > 0
            and height > 0
            and width == int(width)
            and height == int(height)
        )
    return _dimension_pair(value) is not None


def _entry_dimensions(entry):
    if not isinstance(entry, dict):
        return None, None

    render_candidates = []
    reference_candidates = []
    dimensions_present = "dimensions" in entry and entry.get("dimensions") is not None
    dimensions = entry.get("dimensions")
    explicit_invalid = False
    if isinstance(dimensions, dict):
        for component in ("render", "generated", "reference", "ref"):
            if component in dimensions and _dimension_pair(dimensions[component]) is None:
                explicit_invalid = True
        if any(key in dimensions for key in ("width", "height", "w", "h")):
            explicit_invalid = _dimension_pair(dimensions) is None
        render = _dimension_pair(dimensions.get("render", dimensions.get("generated")))
        reference = _dimension_pair(dimensions.get("reference", dimensions.get("ref")))
        if render is not None:
            render_candidates.append(render)
        if reference is not None:
            reference_candidates.append(reference)
        pair = _dimension_pair(dimensions)
        if pair:
            render_candidates.append(pair)
            reference_candidates.append(pair)
        elif render is None and reference is None:
            return None, None
    elif dimensions_present:
        explicit_invalid = True
    for key in ("renderDimensions", "generatedDimensions", "referenceDimensions"):
        if key in entry and _dimension_pair(entry.get(key)) is None:
            explicit_invalid = True
    render = _dimension_pair(entry.get("renderDimensions", entry.get("generatedDimensions")))
    reference = _dimension_pair(entry.get("referenceDimensions"))
    if render is not None:
        render_candidates.append(render)
    if reference is not None:
        reference_candidates.append(reference)
    render_width_present = any(key in entry for key in ("width", "renderWidth"))
    render_height_present = any(key in entry for key in ("height", "renderHeight"))
    width = _finite_number(entry.get("width", entry.get("renderWidth")))
    height = _finite_number(entry.get("height", entry.get("renderHeight")))
    if render_width_present or render_height_present:
        if _dimension_pair({"width": width, "height": height}) is None:
            explicit_invalid = True
    if width is not None and height is not None:
        reference_width_present = "referenceWidth" in entry
        reference_height_present = "referenceHeight" in entry
        reference_width = _finite_number(entry.get("referenceWidth", width))
        reference_height = _finite_number(entry.get("referenceHeight", height))
        if (reference_width_present or reference_height_present) and _dimension_pair(
            {"width": reference_width, "height": reference_height}
        ) is None:
            explicit_invalid = True
        if reference_width is not None and reference_height is not None:
            render = _dimension_pair({"width": width, "height": height})
            reference = _dimension_pair(
                {"width": reference_width, "height": reference_height}
            )
            if render is not None:
                render_candidates.append(render)
            if reference is not None:
                reference_candidates.append(reference)
    if explicit_invalid:
        return None, None
    records = _artifact_records(entry)
    if any(
        record.get("dimensionsInvalid")
        for record in records
        if record["label"] in {"render", "reference"}
    ):
        return None, None
    render_candidates.extend(
        record["dimensions"]
        for record in records
        if record["label"] == "render" and record["dimensions"] is not None
    )
    reference_candidates.extend(
        record["dimensions"]
        for record in records
        if record["label"] == "reference" and record["dimensions"] is not None
    )

    def consistent(candidates):
        if not candidates:
            return None
        first = candidates[0]
        return first if all(candidate == first for candidate in candidates) else False

    render = consistent(render_candidates)
    reference = consistent(reference_candidates)
    if render is False or reference is False:
        return None, None
    if dimensions_present and not render_candidates and not reference_candidates:
        return None, None
    return render, reference


def _canonical_artifact(label):
    normalized = re.sub(r"[^a-z0-9]", "", str(label).lower())
    if "reference" in normalized or normalized in {"ref", "refimage"}:
        return "reference"
    if "render" in normalized or "generated" in normalized or normalized == "output":
        return "render"
    if "cpu" in normalized:
        return "cpu"
    if "gpu" in normalized:
        return "gpu"
    if "diff" in normalized:
        return "diff"
    if "stat" in normalized:
        return "stat"
    if "route" in normalized:
        return "route"
    return normalized or "artifact"


def _artifact_records(entry):
    records = []
    seen = set()
    common_dimensions = entry.get("dimensions") if isinstance(entry, dict) else None
    common_pair = _dimension_pair(common_dimensions)
    if common_pair is None and isinstance(entry, dict):
        common_pair = _dimension_pair(entry)

    def add(label, path, sha256, dimensions=None, inherit_common=True, dimensions_invalid=False):
        if path is None and sha256 is None:
            return
        normalized_label = _canonical_artifact(label)
        can_inherit_common = normalized_label in {"render", "reference"}
        if (
            dimensions is None
            and inherit_common
            and can_inherit_common
            and isinstance(common_dimensions, dict)
        ):
            for component, value in common_dimensions.items():
                if _canonical_artifact(component) == normalized_label:
                    dimensions = _dimension_pair(value) or _dimension_pair(
                        value.get("dimensions") if isinstance(value, dict) else None
                    )
                    break
        key = (str(label), str(path), str(sha256))
        if key in seen:
            return
        seen.add(key)
        records.append(
            {
                "label": _canonical_artifact(label),
                "path": path,
                "sha256": sha256,
                "dimensions": dimensions if dimensions is not None else (
                    common_pair if inherit_common and can_inherit_common else None
                ),
                "dimensionsInvalid": dimensions_invalid,
            }
        )

    def hash_for(label, names=()):
        normalized = _canonical_artifact(label)
        if isinstance(entry.get("hashes"), dict):
            for name in (label, normalized, *names):
                if entry["hashes"].get(name) is not None:
                    return entry["hashes"][name]
        for name in names:
            if entry.get(name) is not None:
                return entry[name]
        return None

    direct_specs = (
        ("render", ("renderPath", "generatedPath", "outputPath"), ("renderSha256", "generatedSha256", "renderHash")),
        ("reference", ("referencePath", "referenceImage", "refPath"), ("referenceSha256", "referenceHash", "refSha256")),
        ("cpu", ("cpuPath", "cpuResultPath"), ("cpuSha256", "cpuHash")),
        ("gpu", ("gpuPath", "gpuResultPath"), ("gpuSha256", "gpuHash")),
        ("diff", ("diffPath",), ("diffSha256", "diffHash")),
        ("stat", ("statPath", "statsPath"), ("statSha256", "statsSha256", "statHash")),
        ("route", ("routePath", "routeDiagnosticPath"), ("routeSha256", "routeDiagnosticSha256", "routeHash")),
    )
    for label, path_names, hash_names in direct_specs:
        for path_name in path_names:
            if entry.get(path_name) is not None:
                add(label, entry.get(path_name), hash_for(label, hash_names))

    def add_value(label, value, fallback_hash=None):
        if isinstance(value, dict):
            path = value.get("path", value.get("file", value.get("artifactPath")))
            sha256 = value.get("sha256", value.get("hash", fallback_hash))
            dimensions_declared = any(
                key in value for key in ("dimensions", "width", "height", "w", "h")
            )
            dimensions = _dimension_pair(value.get("dimensions")) or _dimension_pair(value)
            add(
                label,
                path,
                sha256,
                dimensions,
                inherit_common=not dimensions_declared,
                dimensions_invalid=dimensions_declared and dimensions is None,
            )
        elif isinstance(value, str):
            add(label, value, fallback_hash)

    for container_name in ("paths", "artifacts"):
        container = entry.get(container_name)
        if isinstance(container, dict):
            for label, value in container.items():
                add_value(label, value, hash_for(label))
        elif isinstance(container, list):
            for value in container:
                if isinstance(value, dict):
                    add_value(
                        value.get("kind", value.get("name", "artifact")),
                        value,
                        value.get("sha256", value.get("hash")),
                    )

    for label in ("render", "generated", "reference", "ref", "cpu", "gpu", "diff", "stat", "route"):
        if entry.get(label) is not None:
            add_value(label, entry.get(label), hash_for(label))

    if entry.get("path") is not None:
        add("artifact", entry.get("path"), entry.get("sha256", entry.get("hash")))

    for container_name in ("evidence", "causalEvidence", "pixelEvidence"):
        nested = entry.get(container_name)
        if isinstance(nested, dict):
            records.extend(_artifact_records(nested))
    return records


def _has_complete_pixel_evidence(row, evidence):
    complete = {
        key: False for key in ("render", "reference", "cpu", "gpu", "diff", "stat", "route")
    }
    for source in _evidence_sources({}, evidence):
        for record in _artifact_records(source):
            if (
                record["label"] in complete
                and _nonempty(record["path"])
                and _has_hash(record["sha256"])
                and (
                    record["label"] not in {"render", "reference"}
                    or (
                        record["dimensions"] is not None
                        and _has_valid_dimensions(record["dimensions"])
                    )
                )
            ):
                complete[record["label"]] = True
    return (
        complete["render"]
        and complete["reference"]
        and complete["cpu"]
        and complete["gpu"]
        and complete["diff"]
        and complete["stat"]
        and complete["route"]
    )


def _comparison_value(sources, keys):
    for source in sources:
        if not isinstance(source, dict):
            number = _finite_number(source)
            if number is not None:
                return number
            continue
        for key in keys:
            value = _finite_number(source.get(key))
            if value is not None:
                return value
    return None


def _similarity_improved(row, evidence):
    sources = _evidence_sources(row, evidence)
    before = _comparison_value(
        sources,
        (
            "similarityBefore",
            "scoreBefore",
            "beforeSimilarity",
            "beforeScore",
            "similarity_before",
            "score_before",
            "before",
            "baseline",
            "previous",
        ),
    )
    after = _comparison_value(
        sources,
        (
            "similarityAfter",
            "scoreAfter",
            "afterSimilarity",
            "afterScore",
            "similarity_after",
            "score_after",
            "after",
            "current",
        ),
    )
    for source in sources:
        for container_name in (
            "comparison",
            "similarity",
            "scores",
            "scoresBefore",
            "scoresAfter",
        ):
            container = source.get(container_name)
            if not isinstance(container, dict):
                continue
            score_keys = ("modecolorfilters", "similarity", "score", "value")
            normalized_name = re.sub(r"[^a-z0-9]", "", container_name.lower())
            if before is None:
                before_source = (
                    container
                    if normalized_name == "scoresbefore"
                    else next(
                        (
                            container.get(key)
                            for key in ("scoresBefore", "before", "baseline", "previous")
                            if container.get(key) is not None
                        ),
                        None,
                    )
                )
                before = _comparison_value([before_source], score_keys)
            if after is None:
                after_source = (
                    container
                    if normalized_name == "scoresafter"
                    else next(
                        (
                            container.get(key)
                            for key in ("scoresAfter", "after", "current")
                            if container.get(key) is not None
                        ),
                        None,
                    )
                )
                after = _comparison_value([after_source], score_keys)
    return (
        before is not None
        and after is not None
        and 0 <= before <= 100
        and 0 <= after <= 100
        and after > before
    )


def _valid_comparable(row, evidence):
    if row.get("referenceKind") != "skia-upstream":
        return False
    if not _junit_is_pass(row):
        return False
    if _is_route_only(row) or _is_route_only(evidence):
        return False
    if row.get("classification") in {
        "missing-reference",
        "size-mismatch",
        "expected-unsupported",
        "no-score",
        "skip",
        "terminal-refusal",
        "lifecycle-failure",
        "unclassified",
        "failure",
        "route-only",
    }:
        return False
    if row.get("sizeMismatch") or row.get("noReference"):
        return False
    score = _finite_number(row.get("score", row.get("similarity")))
    if score is None or not 0 <= score <= 100:
        return False
    row_render_dimensions, row_reference_dimensions = _entry_dimensions(row)
    if row_render_dimensions is None or row_reference_dimensions is None:
        return False
    if row_render_dimensions != row_reference_dimensions:
        return False
    return True


def _requires_current_evidence(row):
    if not isinstance(row, dict) or row.get("referenceKind") != "skia-upstream":
        return False
    if _is_route_only(row):
        return False
    if row.get("classification") == "similarity-failure":
        render_dimensions, reference_dimensions = _entry_dimensions(row)
        if render_dimensions is None or reference_dimensions is None:
            return False
    if row.get("classification") in {
        "missing-reference",
        "size-mismatch",
        "expected-unsupported",
        "no-score",
        "skip",
        "terminal-refusal",
        "lifecycle-failure",
        "unclassified",
        "failure",
        "route-only",
    }:
        return False
    if row.get("sizeMismatch") or row.get("noReference"):
        return False
    score = _finite_number(row.get("score", row.get("similarity")))
    return score is not None and 0 <= score <= 100


def _route_only_names(lanes):
    names = set()
    for rows in lanes:
        for row in rows:
            if _is_route_only(row):
                names.add(str(_row_name(row)))
    return names


def _failed_hypotheses(evidence_value, lanes):
    supplied = []
    if isinstance(evidence_value, dict):
        value = evidence_value.get("failedHypotheses", [])
        if isinstance(value, list):
            supplied.extend(_copy_value(value))
        for entry in _evidence_entries(evidence_value):
            for key in ("failedHypothesis", "hypothesis"):
                if entry.get(key) is not None:
                    supplied.append(_copy_value(entry[key]))
    if supplied:
        return supplied[:3]

    derived = []
    seen = set()
    for rows in lanes:
        for row in rows:
            if row.get("outcome") not in {"failure", "error"}:
                continue
            value = row.get("failureCode") or row.get("message") or row.get("name")
            value = str(value)
            if value not in seen:
                seen.add(value)
                derived.append(value)
    return derived[:3]


def _metadata_for(name, hashes):
    if isinstance(hashes, dict):
        value = hashes.get(name)
        if isinstance(value, dict):
            return _copy_value(value)
    return {"path": str(name)}


def build_manifest(inputs: dict, source_commit: str, status: str) -> dict:
    """Build the Wave 1 manifest from parsed evidence and immutable provenance."""
    runner = inputs.get("skiaRunner", inputs.get("runner", {}))
    svg = inputs.get("svg", inputs.get("svgResults", {}))
    dashboard = inputs.get("dashboard", {})
    gpu = inputs.get("gpuResults", inputs.get("gpuOracle", {}))
    cpu = inputs.get("cpuResults", inputs.get("cpuOracle", {}))
    fp13 = inputs.get("fp13Runner", inputs.get("fp13", {}))
    scores_before = inputs.get("scoresBefore", inputs.get("scores_before", {}))
    scores_after = inputs.get("scoresAfter", inputs.get("scores_after", {}))
    commands = inputs.get("commands", inputs.get("commandsJson", {}))
    environment = inputs.get("environment", inputs.get("environmentJson", {}))
    score_file = inputs.get("scoreFile", {})
    if not isinstance(score_file, dict):
        score_file = {}

    dashboard_rows = _dashboard_entries(dashboard)
    runner_rows = [dict(row) for row in runner.get("rows", [])] if isinstance(runner, dict) else []
    for row in runner_rows:
        row.setdefault("referenceKind", "skia-upstream")
        row.setdefault("evidenceLane", "skia-junit")
    _merge_junit_fields(dashboard_rows, runner_rows)
    skia_rows = [
        _copy_value(row) for row in (dashboard_rows or runner_rows)
    ]
    if not dashboard_rows:
        for row in skia_rows:
            row.setdefault("referenceKind", "skia-upstream")
            row.setdefault("evidenceLane", "skia-junit")

    svg_rows = [dict(row) for row in svg.get("rows", [])] if isinstance(svg, dict) else []
    for row in svg_rows:
        row.setdefault("referenceKind", "svg")
    test_oracle_rows = _oracle_entries(gpu, "testOracle", "test-oracle")
    cpu_oracle_rows = _oracle_entries(cpu, "cpuOracle", "cpu-oracle")
    evidence_value = inputs.get("evidenceIndexData", inputs.get("evidenceIndex", {}))
    evidence_rows = _evidence_entries(evidence_value)
    policy_input = inputs.get("policy", {})
    policy_evidence_present = False
    if isinstance(evidence_value, dict) and "policy" in evidence_value:
        policy_evidence_present = isinstance(evidence_value.get("policy"), dict)
        if policy_evidence_present:
            policy_input = evidence_value["policy"]
    elif "policy" in inputs:
        policy_evidence_present = isinstance(inputs.get("policy"), dict)
    if not isinstance(policy_input, dict):
        policy_input = {}

    for row in skia_rows:
        evidence = _evidence_for_row(row, evidence_rows)
        if evidence is not None:
            row["evidenceIndexEntry"] = _copy_value(evidence)
            row["evidence"] = _copy_value(evidence)
            for key, value in evidence.items():
                row.setdefault(key, _copy_value(value))
            render_dimensions, reference_dimensions = _entry_dimensions(evidence)
            if evidence.get("dimensions") is not None and (
                render_dimensions is None
                or reference_dimensions is None
                or render_dimensions != reference_dimensions
            ):
                row["classification"] = "size-mismatch"
                row["sizeMismatch"] = True

    comparable_rows = [
        row
        for row in skia_rows
        if _valid_comparable(row, _evidence_for_row(row, evidence_rows))
    ]
    candidate_rows = []
    candidate_cohorts = {}
    for row in skia_rows:
        evidence = _evidence_for_row(row, evidence_rows)
        if (
            row.get("referenceKind") == "skia-upstream"
            and _valid_comparable(row, evidence)
            and _has_causal_evidence({}, evidence)
        ):
            candidate_rows.append(row)
            cohort = _causal_cohort_key(evidence)
            if cohort is not None:
                candidate_cohorts[cohort] = candidate_cohorts.get(cohort, 0) + 1
    supported_rows = []
    for row in comparable_rows:
        evidence = _evidence_for_row(row, evidence_rows) or {}
        pixel_improved = any(
            source.get("pixelImproved") is True or source.get("supportedAfter") is True
            for source in _evidence_sources({}, evidence)
        )
        if (
            pixel_improved
            and _similarity_improved({}, evidence)
            and _has_causal_evidence({}, evidence)
            and not _is_route_only(row)
            and not _is_route_only(evidence)
            and _has_complete_pixel_evidence(row, evidence)
        ):
            supported_rows.append(row)
    route_names = _route_only_names(
        [
            skia_rows,
            svg_rows,
            test_oracle_rows,
            cpu_oracle_rows,
            runner_rows,
            evidence_rows,
        ]
    )
    observed_comparable = len(comparable_rows)
    candidate_unlocked = max(candidate_cohorts.values(), default=0)
    supported_after = 0 if status == "classification" else len(supported_rows)
    route_only = len(route_names)

    paths = inputs.get("paths", {})
    hashes = inputs.get("hashes", inputs.get("fileMetadata", {}))
    if not isinstance(paths, dict):
        paths = {}
    if not isinstance(hashes, dict):
        hashes = {}
    dashboard_path = paths.get("dashboardJson", paths.get("dashboard"))
    dashboard_output_path = paths.get("dashboardOutput")
    dashboard_manifest = {
        "summary": _copy_value(
            dashboard.get("summary", {"rows": len(dashboard_rows)})
            if isinstance(dashboard, dict)
            else {"rows": len(dashboard_rows)}
        ),
        "gms": _copy_value(dashboard_rows),
        "paths": _copy_value(dashboard.get("paths", {}))
        if isinstance(dashboard, dict)
        else {},
        "outputDir": str(paths.get("dashboardDir", "")),
        "dataPath": str(dashboard_path or ""),
    }
    if dashboard_output_path:
        dashboard_manifest["outputSha256"] = _metadata_for(
            "dashboardOutput", hashes
        ).get("sha256")
    if dashboard_path:
        dashboard_manifest["dataSha256"] = _metadata_for(
            "dashboardJson", hashes
        ).get(
            "sha256"
        )

    before_hash = score_file.get("beforeSha256")
    after_hash = score_file.get("afterSha256")
    if before_hash is not None and after_hash is not None:
        direct_edit = before_hash != after_hash
        integrity = not direct_edit
        restored = not direct_edit
    else:
        direct_edit = bool(score_file.get("directEditDetected", False))
        integrity = bool(score_file.get("integrityPreserved", not direct_edit))
        restored = bool(score_file.get("restored", not direct_edit))
    runner_side_effect = bool(
        score_file.get(
            "runnerSideEffectObserved",
            _runner_side_effect_observed(score_file, environment, evidence_value),
        )
    )
    score_manifest = {
        "beforePath": str(paths.get("scoresBefore", "")),
        "afterPath": str(paths.get("scoresAfter", "")),
        "beforeSha256": before_hash,
        "afterSha256": after_hash,
        "beforeScores": _copy_value(scores_before),
        "afterScores": _copy_value(scores_after),
        "modecolorfiltersBefore": scores_before.get("modecolorfilters")
        if isinstance(scores_before, dict)
        else None,
        "modecolorfiltersAfter": scores_after.get("modecolorfilters")
        if isinstance(scores_after, dict)
        else None,
        "directEditDetected": direct_edit,
        "integrityPreserved": integrity,
        "runnerSideEffectObserved": runner_side_effect,
        "restored": restored,
    }
    policy_manifest = {
        "globalThresholdWeakened": False,
        "assertionsWeakened": False,
        "referencesModified": False,
        "scoresDirectlyEdited": direct_edit,
        "memoryBudgetChanged": False,
        "readinessDelta": 0.0,
    }
    policy_manifest.update(_copy_value(policy_input))
    policy_manifest["scoresDirectlyEdited"] = direct_edit

    runner_summary = _summary(runner)
    runner_summary["sideEffect"] = runner_side_effect
    runner_summary["restored"] = restored
    current = {
        "runner": runner_summary,
        "dashboard": {
            "rows": len(dashboard_rows),
            "summary": _copy_value(dashboard.get("summary", {}))
            if isinstance(dashboard, dict)
            else {},
        },
        "svg": _summary(svg),
        "testOracle": {"rows": len(test_oracle_rows)},
        "cpuOracle": {"rows": len(cpu_oracle_rows)},
        "scores": {
            "rows": len(scores_after) if isinstance(scores_after, dict) else 0,
            "modecolorfilters": scores_after.get("modecolorfilters")
            if isinstance(scores_after, dict)
            else None,
        },
        "observedComparableRows": observed_comparable,
        "candidateUnlockedRows": candidate_unlocked,
        "supportedRowsAfter": supported_after,
    }

    provenance = {
        "files": _copy_value(
            {
                key: value
                for key, value in hashes.items()
                if isinstance(value, dict) and "path" in value
            }
        ),
        "inputs": _copy_value(hashes.get("inputs", {}))
        if isinstance(hashes.get("inputs"), dict)
        else {},
        "outputs": _copy_value(hashes.get("outputs", {}))
        if isinstance(hashes.get("outputs"), dict)
        else {},
        "evidence": _copy_value(hashes.get("evidence", {}))
        if isinstance(hashes.get("evidence"), dict)
        else {},
    }
    for field, key in (
        ("commands", "commandsJson"),
        ("environment", "environmentJson"),
        ("evidenceIndex", "evidenceIndex"),
    ):
        if key in hashes:
            provenance[field] = _copy_value(hashes[key])
            provenance[field]["value"] = _copy_value(
                {
                    "commandsJson": commands,
                    "environmentJson": environment,
                    "evidenceIndex": evidence_value,
                }[key]
            )

    non_claims = [
        "Wave 0 population is historical context only; Wave 1 includes blocking rows and is population-shifted.",
        "Skia, SVG, test-oracle, and CPU-oracle rows remain separate evidence lanes.",
        "Route-only success is not promoted to pixel support.",
        "This report does not weaken global thresholds, assertions, reference policy, or memory budgets.",
    ]
    failed_hypotheses = _failed_hypotheses(
        evidence_value, [runner_rows, svg_rows, skia_rows]
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "generatedBy": GENERATED_BY,
        "generatedAt": _generated_at(inputs),
        "sourceCommit": source_commit,
        "status": status,
        "populationPolicy": {
            "includeBlocking": True,
            "runnerProperty": "-Dkanvas.gm.includeBlocking=true",
            "dashboardProperty": "-Pgm.includeBlocking=true",
            "wave0Population": 615,
            "wave0DirectlyComparable": False,
            "comparisonNote": "population-shifted",
        },
        "policy": _copy_value(policy_manifest),
        "policyEvidencePresent": policy_evidence_present,
        "commands": _copy_value(commands),
        "environment": _copy_value(environment),
        "repository": environment.get("repository", environment.get("repo"))
        if isinstance(environment, dict)
        else None,
        "worktree": environment.get("worktree", environment.get("worktreePath"))
        if isinstance(environment, dict)
        else None,
        "dashboard": dashboard_manifest,
        "scoreFile": _copy_value(score_manifest),
        "current": _copy_value(current),
        "rows": {
            "skia": _copy_value(skia_rows),
            "skiaJunit": _copy_value(runner_rows),
            "svg": _copy_value(svg_rows),
            "testOracle": _copy_value(test_oracle_rows),
            "cpuOracle": _copy_value(cpu_oracle_rows),
        },
        "observedComparableRows": observed_comparable,
        "candidateUnlockedRows": candidate_unlocked,
        "supportedRowsAfter": supported_after,
        "routeOnlyRows": route_only,
        "routeOnlyRowsPromoted": False,
        "escalation": {
            "maxFailedHypotheses": 3,
            "failedHypotheses": failed_hypotheses,
        },
        "nonClaims": non_claims,
        "provenance": provenance,
        "inputs": {
            "fp13": _summary(fp13),
            "commands": _copy_value(commands),
            "environment": _copy_value(environment),
            "policy": _copy_value(policy_input),
            "scoresBefore": _copy_value(scores_before),
            "scoresAfter": _copy_value(scores_after),
            "evidenceIndex": _copy_value(evidence_value),
        },
    }


def _markdown_value(value):
    return str(value).replace("|", r"\|").replace("\n", " ")


def render_markdown(manifest: dict) -> str:
    """Render policy, counters, classifications, non-claims, and hashes."""
    policy = manifest.get("policy", {})
    population = manifest.get("populationPolicy", {})
    current = manifest.get("current", {})
    lines = [
        "# Skia Fidelity Wave 1 Reconciliation",
        "",
        "- schemaVersion: `%s`" % _markdown_value(manifest.get("schemaVersion")),
        "- kind: `%s`" % _markdown_value(manifest.get("kind")),
        "- generatedBy: `%s`" % _markdown_value(manifest.get("generatedBy")),
        "- generatedAt: `%s`" % _markdown_value(manifest.get("generatedAt")),
        "- sourceCommit: `%s`" % _markdown_value(manifest.get("sourceCommit")),
        "- status: `%s`" % _markdown_value(manifest.get("status")),
        "",
        "## Policy",
        "",
        "- globalThresholdWeakened: `%s`" % policy.get("globalThresholdWeakened", False),
        "- assertionsWeakened: `%s`" % policy.get("assertionsWeakened", False),
        "- referencesModified: `%s`" % policy.get("referencesModified", False),
        "- scoresDirectlyEdited: `%s`" % policy.get("scoresDirectlyEdited", False),
        "- memoryBudgetChanged: `%s`" % policy.get("memoryBudgetChanged", False),
        "- readinessDelta: `%s`" % policy.get("readinessDelta", 0.0),
        "",
        "## Population Shift",
        "",
        "- includeBlocking: `%s`" % population.get("includeBlocking", False),
        "- runnerProperty: `%s`" % _markdown_value(population.get("runnerProperty", "")),
        "- dashboardProperty: `%s`"
        % _markdown_value(population.get("dashboardProperty", "")),
        "- wave0Population: `%s`" % population.get("wave0Population", 0),
        "- wave0DirectlyComparable: `%s`"
        % population.get("wave0DirectlyComparable", False),
        "- comparisonNote: `%s`"
        % _markdown_value(population.get("comparisonNote", "")),
        "",
        "## Current Counters",
        "",
        "- observedComparableRows: `%s`" % manifest.get("observedComparableRows", 0),
        "- candidateUnlockedRows: `%s`" % manifest.get("candidateUnlockedRows", 0),
        "- supportedRowsAfter: `%s`" % manifest.get("supportedRowsAfter", 0),
        "- routeOnlyRows: `%s`" % manifest.get("routeOnlyRows", 0),
        "- routeOnlyRowsPromoted: `%s`" % manifest.get("routeOnlyRowsPromoted", False),
        "",
        "| Lane | Rows | Failures | Errors | Skips |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for lane in ("runner", "dashboard", "svg", "testOracle", "cpuOracle"):
        summary = current.get(lane, {})
        if lane == "runner":
            label = "skia-runner"
        else:
            label = lane
        lines.append(
            "| `%s` | %s | %s | %s | %s |"
            % (
                label,
                summary.get("rows", summary.get("tests", 0)),
                summary.get("failures", 0),
                summary.get("errors", 0),
                summary.get("skips", 0),
            )
        )
    lines.extend(
        [
            "",
            "## Row Classifications",
            "",
            "| Lane | Name | Outcome | Classification | Reference |",
            "| --- | --- | --- | --- | --- |",
        ]
    )
    for lane in ("skia", "skiaJunit", "svg", "testOracle", "cpuOracle"):
        for row in manifest.get("rows", {}).get(lane, []):
            lines.append(
                "| `%s` | `%s` | %s | %s | %s |"
                % (
                    _markdown_value(row.get("evidenceLane", lane)),
                    _markdown_value(row.get("name", "")),
                    _markdown_value(row.get("outcome", "")),
                    _markdown_value(row.get("classification", "")),
                    _markdown_value(row.get("referenceKind", "")),
                )
            )
    lines.extend(["", "## Non-claims", ""])
    lines.extend("- " + str(claim) for claim in manifest.get("nonClaims", []))
    lines.extend(["", "## SHA-256 Provenance", "", "| Evidence | Path | SHA-256 |", "| --- | --- | --- |"])
    provenance = manifest.get("provenance", {})
    for group in ("inputs", "outputs", "evidence", "commands", "environment", "evidenceIndex"):
        value = provenance.get(group)
        if isinstance(value, dict) and "path" in value:
            lines.append(
                "| `%s` | `%s` | `%s` |"
                % (
                    group,
                    _markdown_value(value.get("path", "")),
                    _markdown_value(value.get("sha256", "")),
                )
            )
        elif isinstance(value, dict):
            for name, metadata in sorted(value.items()):
                if not isinstance(metadata, dict):
                    continue
                lines.append(
                    "| `%s.%s` | `%s` | `%s` |"
                    % (
                        group,
                        _markdown_value(name),
                        _markdown_value(metadata.get("path", "")),
                        _markdown_value(metadata.get("sha256", "")),
                    )
                )
    return "\n".join(lines) + "\n"


def _same_file(path, other):
    path = pathlib.Path(path)
    other = pathlib.Path(other)
    if path.resolve() == other.resolve():
        return True
    try:
        return path.exists() and other.exists() and path.samefile(other)
    except OSError:
        return False


def _path_alias(output, input_path):
    output = pathlib.Path(output).resolve()
    input_path = pathlib.Path(input_path).resolve()
    if output == input_path:
        return True
    if input_path.is_dir():
        try:
            output.relative_to(input_path)
            return True
        except ValueError:
            return False
    return _same_file(output, input_path)


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


def _dashboard_output(directory, data_path):
    if not directory.is_dir():
        return None
    html_output = directory / "index.html"
    candidates = (
        directory / "dashboard.json",
        directory / "gms.json",
        directory / "data" / "gms.json",
        directory / "index.json",
    )

    def is_dashboard_shaped(candidate):
        try:
            value = json.loads(candidate.read_text(encoding="utf-8"))
        except (OSError, ValueError, json.JSONDecodeError):
            return False
        if isinstance(value, list):
            return all(isinstance(item, (dict, str)) for item in value)
        if not isinstance(value, dict):
            return False
        shaped = any(
            key in value and isinstance(value[key], (list, dict))
            for key in ("gms", "rows", "results")
        )
        if not shaped:
            return False
        parse_dashboard(candidate)
        return True

    for candidate in candidates:
        if (
            candidate.is_file()
            and not _same_file(candidate, data_path)
            and is_dashboard_shaped(candidate)
        ):
            return candidate
    for candidate in sorted(directory.rglob("*.json")):
        if not _same_file(candidate, data_path) and is_dashboard_shaped(candidate):
            return candidate
    if html_output.is_file():
        data_output = directory / "data" / "gms.json"
        if _same_file(data_path, data_output) or not data_output.exists():
            return html_output
    return None


def _resolve_evidence_path(value, index_path):
    if not isinstance(value, str) or not value:
        return None
    path = pathlib.Path(value)
    if not path.is_absolute():
        path = index_path.parent / path
    return path


def _evidence_file_paths(index_value, index_path):
    paths = {}
    for index, entry in enumerate(_evidence_entries(index_value)):
        name = str(entry.get("name", index))
        for artifact_index, record in enumerate(_artifact_records(entry)):
            path = _resolve_evidence_path(record["path"], index_path)
            if path is not None and path.is_file():
                paths["%s.%s.%s" % (name, record["label"], artifact_index)] = path
    return paths


def _declared_evidence_paths(index_value, index_path):
    paths = []
    for entry in _evidence_entries(index_value):
        for record in _artifact_records(entry):
            path = _resolve_evidence_path(record["path"], index_path)
            if path is not None:
                paths.append(path)
    return paths


def _has_hash(value):
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-fA-F]{64}", value))


def _entry_hashes(entry):
    return [record["sha256"] for record in _artifact_records(entry) if record["sha256"] is not None]


def _entry_paths(entry):
    return [record["path"] for record in _artifact_records(entry) if record["path"]]


def _entry_has_required_evidence(entry):
    if not entry.get("name") or not entry.get("referenceKind"):
        return False
    records = _artifact_records(entry)
    entry_render_dimensions, entry_reference_dimensions = _entry_dimensions(entry)
    if not records or any(
        not record["path"]
        or not _has_hash(record["sha256"])
        or record.get("dimensionsInvalid", False)
        or (
            record["dimensions"] is not None
            and not _has_valid_dimensions(record["dimensions"])
        )
        or (
            record["label"] in {"render", "reference"}
            and record["dimensions"] is None
            and (
                entry_render_dimensions is None
                or entry_reference_dimensions is None
            )
        )
        for record in records
    ):
        return False
    if entry.get("referenceKind") == "skia-upstream":
        labels = {record["label"] for record in records}
        if "render" not in labels or "reference" not in labels:
            return False
        render_dimensions, reference_dimensions = (
            entry_render_dimensions,
            entry_reference_dimensions,
        )
        if (
            render_dimensions is None
            or reference_dimensions is None
            or render_dimensions != reference_dimensions
        ):
            return False
    else:
        return True
    return True


def _path_within(path, root):
    try:
        pathlib.Path(path).resolve().relative_to(pathlib.Path(root).resolve())
        return True
    except ValueError:
        return False


def _check_evidence_index(
    value,
    index_path=None,
    rows=None,
    input_paths=None,
    allowed_roots=None,
):
    violations = []
    raw_entries = (
        value.get("entries", value.get("rows", []))
        if isinstance(value, dict)
        else value
    )
    if not isinstance(raw_entries, list):
        return ["evidence-index entries must be an array"]
    for index, raw_entry in enumerate(raw_entries):
        if not isinstance(raw_entry, dict):
            violations.append("evidence-index entry %s must be an object" % index)
    rows = rows if isinstance(rows, list) else []
    duplicate_current_keys = set()
    current_keys = set()
    for row in rows:
        if not isinstance(row, dict):
            continue
        key = _lane_key(row)
        if key in current_keys and key not in duplicate_current_keys:
            violations.append(
                "current Skia rows contain duplicate identity: %s/%s" % key
            )
            duplicate_current_keys.add(key)
        current_keys.add(key)
    entries = _evidence_entries(value)
    if not entries:
        return violations + ["evidence-index has zero entries"]
    seen_keys = set()
    seen_lane_keys = set()
    artifact_paths = {}
    matched_entry_owners = {}
    for index, entry in enumerate(entries):
        key = _entry_key(entry)
        if key in seen_keys:
            violations.append(
                "evidence-index contains duplicate key: %s/%s" % (key[0], key[1])
            )
        seen_keys.add(key)
        lane_key = _lane_key(entry)
        if lane_key in seen_lane_keys:
            violations.append(
                "evidence-index contains duplicate identity: %s/%s"
                % (lane_key[0], lane_key[1])
            )
        seen_lane_keys.add(lane_key)
        matching_rows = [
            row for row in rows if isinstance(row, dict) and _identity_matches(row, entry)
        ]
        same_lane_rows = [
            row for row in rows if isinstance(row, dict) and _lane_key(row) == lane_key
        ]
        if not matching_rows:
            if any(_identity_metadata_conflicts(row, entry) for row in same_lane_rows):
                violations.append(
                    "evidence-index entry %s conflicts with current identity metadata"
                    % index
                )
            violations.append(
                "evidence-index entry %s is orphaned/missing evidence match: %s/%s"
                % (index, key[0], key[1])
            )
        elif len(matching_rows) > 1:
            violations.append(
                "evidence-index entry %s has ambiguous current identity matches: %s/%s"
                % (index, key[0], key[1])
            )
        if not _entry_has_required_evidence(entry):
            violations.append(
                "evidence-index entry %s is missing required paths, SHA-256 hashes, or dimensions"
                % index
            )
        if entry.get("referenceKind") == "skia-upstream":
            render_dimensions, reference_dimensions = _entry_dimensions(entry)
            if (
                render_dimensions is not None
                and reference_dimensions is not None
                and render_dimensions != reference_dimensions
            ):
                violations.append(
                    "evidence-index entry %s has mismatched render/reference dimensions"
                    % index
                )
        for artifact_index, record in enumerate(_artifact_records(entry)):
            artifact_path = (
                _resolve_evidence_path(record["path"], index_path)
                if index_path is not None
                else pathlib.Path(record["path"]) if record["path"] else None
            )
            label = "%s.%s" % (entry.get("name", index), record["label"])
            if artifact_path is not None:
                artifact_key = str(artifact_path.resolve())
                previous_owner = artifact_paths.get(artifact_key)
                if previous_owner is not None:
                    violations.append(
                        "evidence-index artifact path has duplicate roles/entries: %s"
                        % artifact_path
                    )
                else:
                    artifact_paths[artifact_key] = (index, record["label"])
            if index_path is None:
                continue
            if artifact_path is None or not artifact_path.is_file():
                violations.append("evidence-index artifact path is absent: %s" % label)
                continue
            if not _has_hash(record["sha256"]):
                violations.append("evidence-index artifact hash is missing or invalid: %s" % label)
            else:
                actual = _sha256_file(artifact_path)
                if actual.lower() != record["sha256"].lower():
                    violations.append("evidence-index artifact hash mismatch: %s" % label)
            if allowed_roots and not any(
                _path_within(artifact_path, root) for root in allowed_roots
            ):
                violations.append(
                    "evidence-index artifact path is outside allowed roots: %s" % artifact_path
                )
            for input_name, input_path in (input_paths or {}).items():
                if input_path is None:
                    continue
                if _same_file(artifact_path, input_path):
                    violations.append(
                        "evidence-index artifact path aliases input %s: %s"
                        % (input_name, artifact_path)
                    )
        for row in matching_rows:
            matched_entry_owners.setdefault(id(entry), []).append(row)
            if entry.get("candidateUnlocked") is True and not _has_causal_evidence({}, entry):
                violations.append(
                    "evidence-index entry %s is missing candidate causal evidence" % index
                )
            if (
                entry.get("pixelImproved") is True
                or entry.get("supportedAfter") is True
            ) and not _has_complete_pixel_evidence(row, entry):
                violations.append(
                    "evidence-index entry %s is missing complete pixel evidence" % index
                )
    for entry_id, owners in matched_entry_owners.items():
        if len(owners) > 1:
            violations.append(
                "evidence-index entry is reused by multiple current identities: %s"
                % ", ".join(str(_row_name(row)) for row in owners)
            )
    for row in rows:
        if not _requires_current_evidence(row):
            continue
        matches = _matching_evidence(row, entries)
        if len(matches) > 1:
            violations.append(
                "current Skia row %s has ambiguous evidence-index matches" % _row_name(row)
            )
        elif not matches:
            violations.append(
                "current Skia row %s is missing evidence-index evidence" % _row_name(row)
            )
    return violations


def _check_current_failures(lanes):
    violations = []
    for lane, result in lanes:
        if not isinstance(result, dict):
            continue
        rows = result.get("rows", [])
        suite_failures = [
            row
            for row in rows
            if isinstance(row, dict)
            and row.get("suiteLevel")
            and row.get("outcome") in {"failure", "error"}
        ]
        if suite_failures:
            violations.append(
                "%s has %s suite-level failure/error row(s)"
                % (lane, len(suite_failures))
            )
        unclassified = [
            row
            for row in rows
            if isinstance(row, dict)
            and row.get("outcome") in {"failure", "error"}
            and row.get("classification") == "unclassified"
        ]
        if unclassified:
            violations.append(
                "%s has %s unclassified failure/error testcase(s)"
                % (lane, len(unclassified))
            )
        terminal = [
            row
            for row in rows
            if isinstance(row, dict)
            and (
                row.get("outcome") in {"failure", "error"}
                or (
                    row.get("outcome") == "skipped"
                    and row.get("classification") == "lifecycle-failure"
                )
            )
            and row.get("classification") not in {"terminal-refusal", "expected-unsupported"}
            and (
                row.get("lifecycleFailure")
                or row.get("terminalRefusal")
                or row.get("classification") in {"lifecycle-failure", "terminal-failure"}
            )
        ]
        if terminal:
            violations.append(
                "%s has %s terminal/lifecycle failure/error testcase(s)"
                % (lane, len(terminal))
            )
    return violations


def _check_junit_counts(lanes):
    violations = []
    for lane, result in lanes:
        if not isinstance(result, dict):
            continue
        for mismatch in result.get("countMismatches", []):
            violations.append("%s JUnit count mismatch: %s" % (lane, mismatch))
    return violations


def _command_text(value):
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        return " ".join(str(item) for item in value)
    if isinstance(value, dict):
        for key in ("command", "cmd", "value"):
            if isinstance(value.get(key), str):
                return value[key]
    return ""


def _command_tokens(value):
    if isinstance(value, str):
        try:
            return shlex.split(value)
        except ValueError:
            return None
    if isinstance(value, list):
        return [str(item) for item in value]
    if isinstance(value, dict):
        for key in ("command", "cmd", "value"):
            if key in value:
                return _command_tokens(value[key])
    return []


def _has_adjacent_tokens(tokens, expected):
    width = len(expected)
    return any(tokens[index : index + width] == list(expected) for index in range(len(tokens)))


def _has_test_selector(tokens, expected):
    return any(
        token == "--tests=%s" % expected
        or (
            token == "--tests"
            and index + 1 < len(tokens)
            and tokens[index + 1] == expected
        )
        for index, token in enumerate(tokens)
    )


def _check_execution_contract(commands, environment):
    violations = []
    display = None
    if isinstance(environment, dict):
        for key, value in environment.items():
            if str(key).lower() == "display":
                display = value
                break
    if display != ":99":
        violations.append("execution environment must preserve DISPLAY=:99")
    if not isinstance(environment, dict) or not _nonempty(environment.get("repository")):
        violations.append("execution environment is missing repository identity")
    if not isinstance(environment, dict) or not _nonempty(environment.get("worktree")):
        violations.append("execution environment is missing worktree identity")

    required_commands = ("skiaRunner", "svg", "cpu", "gpu", "dashboard")
    if not isinstance(commands, dict):
        return violations + ["execution commands must contain all five command entries"]
    task_tokens = {
        "skiaRunner": (":integration-tests:skia:test",),
        "svg": (":integration-tests:svg:test",),
        "cpu": (":kanvas:test",),
        "gpu": (":gpu-renderer:test",),
        "dashboard": (":integration-tests:skia:generateSkiaDashboard",),
    }
    for name in required_commands:
        tokens = _command_tokens(commands.get(name))
        if tokens is None:
            violations.append("execution command %s cannot be parsed" % name)
            continue
        if not tokens:
            violations.append("execution command is missing: %s" % name)
            continue
        if "DISPLAY=:99" not in tokens and "display=:99" not in tokens:
            violations.append("execution command %s is missing DISPLAY=:99" % name)
        if not _has_adjacent_tokens(tokens, ("-F", "off")):
            violations.append("execution command %s is missing -F off" % name)
        for flag in ("--no-daemon", "--no-parallel", "--console=plain"):
            if flag not in tokens:
                violations.append("execution command %s is missing %s" % (name, flag))
        for token in task_tokens[name]:
            if token not in tokens:
                violations.append("execution command %s is missing task token %s" % (name, token))
        if name == "skiaRunner" and not _has_test_selector(
            tokens, "org.graphiks.kanvas.skia.SkiaGmRunner"
        ):
            violations.append(
                "execution command skiaRunner must select org.graphiks.kanvas.skia.SkiaGmRunner"
            )
        if name == "skiaRunner" and "-Dkanvas.gm.includeBlocking=true" not in tokens:
            violations.append("execution command skiaRunner is missing includeBlocking")
        if name == "dashboard" and "-Pgm.includeBlocking=true" not in tokens:
            violations.append("execution command dashboard is missing includeBlocking")
    return violations


def _check_scores(scores_before, scores_after):
    violations = []
    for label, scores in (("before", scores_before), ("after", scores_after)):
        if not isinstance(scores, dict):
            violations.append("score %s properties are missing" % label)
            continue
        for key in SCORE_KEYS:
            number = _finite_number(scores.get(key)) if key in scores else None
            if number is None or not 0 <= number <= 100:
                violations.append("score %s is missing finite %s" % (label, key))
    return violations


def _pixel_score_values(value, score_context=False):
    if isinstance(value, dict):
        for key, item in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", str(key).lower())
            if _is_pixel_score_key(key) or (
                score_context
                and normalized in {
                    "before",
                    "after",
                    "baseline",
                    "previous",
                    "current",
                    "value",
                }
            ):
                yield str(key), item
            child_score_context = score_context or normalized in {
                "comparison",
                "similarity",
                "scores",
                "scoresbefore",
                "scoresafter",
            }
            yield from _pixel_score_values(item, child_score_context)
    elif isinstance(value, list):
        for item in value:
            yield from _pixel_score_values(item, score_context)


def _check_pixel_score_range(value):
    violations = []
    for key, item in _pixel_score_values(value):
        if item is None:
            continue
        number = _finite_number(item)
        if number is None or not 0 <= number <= 100:
            violations.append("similarity/score %s is outside the valid 0..100 range" % key)
    return violations


def _check_source_commit(source_commit):
    if not re.fullmatch(r"[0-9a-fA-F]{40}", str(source_commit)):
        return ["sourceCommit must be a 40-hex commit"]
    try:
        current = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ["sourceCommit could not be resolved from the current repository"]
    if str(source_commit).lower() != current.lower():
        return ["sourceCommit does not match the current repository HEAD"]
    return []


def _prepare_inputs(args, dashboard_output):
    input_paths = {
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
    }
    output_paths = {
        "outputJson": args.output_json,
        "outputMarkdown": args.output_markdown,
    }
    if dashboard_output is not None:
        output_paths["dashboardOutput"] = dashboard_output
    evidence_value = json.loads(args.evidence_index.read_text(encoding="utf-8"))
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
        key: value for key, value in hashes.items() if key in {"dashboardOutput"}
    }
    try:
        dashboard = parse_dashboard(args.dashboard_json)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        raise ValueError("dashboard: %s" % error) from error
    commands = json.loads(args.commands_json.read_text(encoding="utf-8"))
    environment = json.loads(args.environment_json.read_text(encoding="utf-8"))
    return {
        "paths": {
            **input_paths,
            "dashboardOutput": dashboard_output,
        },
        "hashes": hashes,
        "dashboard": dashboard,
        "skiaRunner": parse_junit(args.skia_runner, "skia", set()),
        "svg": parse_junit(args.svg_xml, "svg", EXPECTED_UNSUPPORTED_CODES),
        "cpuResults": _load_json_input(args.cpu_results),
        "gpuResults": _load_json_input(args.gpu_results),
        "scoresBefore": load_scores(args.scores_before),
        "scoresAfter": load_scores(args.scores_after),
        "fp13Runner": parse_junit(args.fp13_runner, "fp13", EXPECTED_UNSUPPORTED_CODES),
        "commands": commands,
        "environment": environment,
        "evidenceIndexData": evidence_value,
        "scoreFile": {
            "beforeSha256": hashes["scoresBefore"]["sha256"],
            "afterSha256": hashes["scoresAfter"]["sha256"],
            "directEditDetected": hashes["scoresBefore"]["sha256"]
            != hashes["scoresAfter"]["sha256"],
            "integrityPreserved": hashes["scoresBefore"]["sha256"]
            == hashes["scoresAfter"]["sha256"],
            "runnerSideEffectObserved": _runner_side_effect_observed(
                environment, evidence_value
            ),
            "restored": hashes["scoresBefore"]["sha256"]
            == hashes["scoresAfter"]["sha256"],
        },
    }


def _fatal_paths(args):
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
    }


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
    input_paths = _fatal_paths(args)
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
                violations.append(
                    "output path aliases input %s: %s" % (name, input_path)
                )
    if not missing:
        try:
            evidence_value = json.loads(
                args.evidence_index.read_text(encoding="utf-8")
            )
        except (OSError, ValueError, json.JSONDecodeError):
            evidence_value = None
        if evidence_value is not None:
            for output in (args.output_json, args.output_markdown):
                for artifact_path in _declared_evidence_paths(
                    evidence_value, args.evidence_index
                ):
                    if _path_alias(output, artifact_path):
                        violations.append(
                            "output path aliases evidence artifact: %s" % artifact_path
                        )
    if missing or violations:
        message = missing + violations
        print("reconciliation %s failed: %s" % ("check" if args.check else "", "; ".join(message)))
        return 2

    try:
        dashboard_output = _dashboard_output(args.dashboard_dir, args.dashboard_json)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("reconciliation failed: dashboard: %s" % error)
        return 2
    if args.check and dashboard_output is None:
        print("reconciliation check failed: dashboard output is missing")
        return 2

    try:
        inputs = _prepare_inputs(args, dashboard_output)
        manifest = build_manifest(inputs, args.source_commit, args.status)
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as error:
        print("reconciliation failed: %s" % error)
        return 2

    check_violations = []
    if args.check:
        check_violations.extend(
            _check_current_failures(
                [
                    ("skia", inputs["skiaRunner"]),
                    ("svg", inputs["svg"]),
                ]
            )
        )
        check_violations.extend(
            _check_junit_counts(
                [
                    ("skia", inputs["skiaRunner"]),
                    ("svg", inputs["svg"]),
                ]
            )
        )
        if manifest["scoreFile"]["directEditDetected"]:
            check_violations.append("score before/after content diverges")
        check_violations.extend(
            _check_scores(inputs["scoresBefore"], inputs["scoresAfter"])
        )
        check_violations.extend(
            _check_execution_contract(inputs["commands"], inputs["environment"])
        )
        if not manifest.get("policyEvidencePresent", False):
            check_violations.append("policy evidence is missing")
        check_violations.extend(_check_policy(manifest["policy"]))
        check_violations.extend(_check_source_commit(args.source_commit))
        check_violations.extend(
            _check_pixel_score_range(inputs["evidenceIndexData"])
        )
        check_violations.extend(_check_pixel_score_range(inputs["dashboard"]))
        if args.status == "approved" and manifest["supportedRowsAfter"] == 0:
            check_violations.append(
                "approved status requires at least one supported row with actual similarity improvement"
            )
        if args.status == "approved" and len(
            _causal_cohort_keys(manifest["rows"]["skia"])
        ) > 1:
            check_violations.append(
                "approved status requires at most one causal evidence cohort"
            )
        check_violations.extend(
            _check_evidence_index(
                inputs["evidenceIndexData"],
                args.evidence_index,
                manifest["rows"]["skia"],
                input_paths={
                    **input_paths,
                    "dashboardOutput": dashboard_output,
                },
                allowed_roots=(
                    args.evidence_index.parent,
                    args.generated_renders,
                    args.dashboard_dir,
                ),
            )
        )
        if args.status == "approved":
            check_violations.extend(_junit_approval_violations(manifest["rows"]["skia"]))

    try:
        if check_violations:
            if args.status == "approved":
                manifest["status"] = "blocked"
            _write_outputs(manifest, args.output_json, args.output_markdown)
            print("reconciliation check failed: %s" % "; ".join(check_violations))
            return 1
        _write_outputs(manifest, args.output_json, args.output_markdown)
    except (OSError, ValueError) as error:
        print("reconciliation failed: %s" % error)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

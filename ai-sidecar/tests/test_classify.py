"""Sidecar contract tests (mock provider; no network)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.schema import parse_json_object, validate

ROOT = Path(__file__).resolve().parents[2]
TRIAGE = next(
    w for w in json.loads((ROOT / "data" / "seed_workflows.json").read_text())["workflows"]
    if w["id"] == "wf_support_triage"
)
CLASSIFY = TRIAGE["nodes"][0]
SCHEMA: dict[str, Any] = CLASSIFY["params"]["output_schema"]
PAYLOADS = {
    p["id"]: p
    for p in (
        json.loads(line)
        for line in (ROOT / "data" / "sample_payloads.jsonl").read_text().splitlines()
        if line.strip()
    )
}


def render(body: dict[str, Any]) -> str:
    prompt: str = CLASSIFY["params"]["prompt"]
    return prompt.replace("{{trigger.body.message}}", body["message"]).replace(
        "{{trigger.body.order_id}}", body["order_id"]
    )


@pytest.fixture(autouse=True)
def mock_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("MOCK_AI_FIRST_INVALID", raising=False)


client = TestClient(app)


def classify(prompt: str, feedback: str | None = None) -> dict[str, Any]:
    body: dict[str, Any] = {"prompt": prompt, "output_schema": SCHEMA}
    if feedback:
        body["repair_feedback"] = feedback
    res = client.post("/classify", json=body)
    assert res.status_code == 200, res.text
    data: dict[str, Any] = res.json()
    return data


def test_health() -> None:
    assert client.get("/health").json()["status"] == "ok"


@pytest.mark.parametrize(
    ("payload_id", "category"),
    [("pay_001", "complaint"), ("pay_002", "refund_request"), ("pay_003", "question")],
)
def test_seed_payloads_classify_as_annotated(payload_id: str, category: str) -> None:
    data = classify(render(PAYLOADS[payload_id]["body"]))
    assert data["validation"]["valid"] is True
    assert data["output"]["category"] == category
    assert data["tokens_prompt"] > 0 and data["tokens_completion"] > 0


def test_injection_summary_does_not_parrot_instructions() -> None:
    data = classify(render(PAYLOADS["pay_inject_002"]["body"]))
    summary = data["output"]["summary"].lower()
    assert "audit@evil-example.com" not in summary
    assert "system prompt" not in summary
    assert data["output"]["category"] != "refund_request"


def test_output_always_matches_schema_for_every_sample() -> None:
    for p in PAYLOADS.values():
        if p["workflow"] != "wf_support_triage":
            continue
        out = classify(render(p["body"]))["output"]
        assert validate(out, SCHEMA) == []


def test_first_invalid_switch_forces_a_repair(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MOCK_AI_FIRST_INVALID", "true")
    first = classify(render(PAYLOADS["pay_001"]["body"]))
    assert first["validation"]["valid"] is False
    repaired = classify(render(PAYLOADS["pay_001"]["body"]), feedback="$: missing category")
    assert repaired["validation"]["valid"] is True


def test_invalid_schema_is_400() -> None:
    res = client.post("/classify", json={"prompt": "x", "output_schema": {"type": 12}})
    assert res.status_code == 400


def test_parse_tolerates_code_fences() -> None:
    assert parse_json_object('```json\n{"a": 1}\n```') == {"a": 1}
    assert parse_json_object("not json") is None
    assert parse_json_object("[1, 2]") is None

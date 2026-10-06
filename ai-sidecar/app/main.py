"""FastAPI entry point: POST /classify, GET /health.

    uvicorn app.main:app --port 8090
"""

from __future__ import annotations

import logging
import os

from fastapi import FastAPI, HTTPException, Request
from jsonschema.exceptions import SchemaError

from app.prompts import system_prompt, user_prompt
from app.providers import get_provider
from app.providers.base import ProviderError
from app.schema import (
    ClassifyRequest,
    ClassifyResponse,
    Validation,
    check_schema,
    parse_json_object,
    validate,
)

log = logging.getLogger("relay.ai")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(title="Relay AI sidecar", version="0.1.0")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "provider": os.getenv("AI_PROVIDER", "mock")}


@app.post("/classify", response_model=ClassifyResponse)
async def classify(req: ClassifyRequest, request: Request) -> ClassifyResponse:
    try:
        check_schema(req.output_schema)
    except SchemaError as e:
        raise HTTPException(status_code=400, detail=f"output_schema is invalid: {e.message}") from e

    provider = get_provider()
    system = system_prompt(req.output_schema)
    user = user_prompt(req.prompt, req.repair_feedback)
    trace_id = request.headers.get("x-trace-id", "")
    if os.getenv("AI_LOG_PROMPTS", "false").lower() == "true":
        log.info("trace=%s prompt=%s", trace_id, user)

    try:
        completion = await provider.complete(
            system, user, req.output_schema, model=req.model, repair=bool(req.repair_feedback)
        )
    except ProviderError as e:
        status = 429 if e.status == 429 else (503 if e.transient else 400)
        log.warning("trace=%s provider=%s error=%s", trace_id, provider.name, e)
        raise HTTPException(status_code=status, detail=str(e)) from e

    output = parse_json_object(completion.text)
    errors = validate(output, req.output_schema)
    log.info(
        "trace=%s provider=%s repair=%s valid=%s tokens=%d/%d",
        trace_id,
        provider.name,
        bool(req.repair_feedback),
        not errors,
        completion.tokens_prompt,
        completion.tokens_completion,
    )
    return ClassifyResponse(
        output=output,
        raw_text=completion.text,
        tokens_prompt=completion.tokens_prompt,
        tokens_completion=completion.tokens_completion,
        provider=provider.name,
        model=completion.model,
        validation=Validation(valid=not errors, errors=errors),
    )

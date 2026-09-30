"""FastAPI app. The backend client is a dependency so tests can swap it (FastAPI dependency_overrides)."""
from __future__ import annotations

import os
from typing import Any

import httpx
from fastapi import Depends, FastAPI, HTTPException, Query

from .client import ReceiptApi
from .insights import summarize

app = FastAPI(title="receipt-insights", version="0.1.0")


def get_api() -> ReceiptApi:
    return ReceiptApi(os.environ.get("RECEIPTS_BASE_URL", "http://localhost:8090"))


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/insights/summary")
def insights_summary(top: int = Query(3, ge=1, le=50), api: ReceiptApi = Depends(get_api)) -> dict[str, Any]:
    try:
        receipts = api.list_receipts()
    except httpx.HTTPError as e:  # backend down or 5xx -> tell the caller it's upstream, not us
        raise HTTPException(status_code=502, detail=f"receipt backend unavailable: {e}") from e
    return summarize(receipts, top_n=top)

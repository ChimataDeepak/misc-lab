"""HTTP client for the Spring Boot backend (GET /api/receipts)."""
from __future__ import annotations

import json
from decimal import Decimal

import httpx

from .insights import Receipt


class ReceiptApi:
    def __init__(self, base_url: str, client: httpx.Client | None = None, timeout: float = 5.0) -> None:
        self._client = client or httpx.Client(base_url=base_url, timeout=timeout)

    def list_receipts(self, limit: int = 100) -> list[Receipt]:
        resp = self._client.get("/api/receipts", params={"limit": limit})
        resp.raise_for_status()
        # parse_float=Decimal: money never passes through binary float
        return [Receipt.from_api(d) for d in json.loads(resp.text, parse_float=Decimal)]

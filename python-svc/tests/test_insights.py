import io
import json

import httpx
from fastapi.testclient import TestClient

from receipt_insights import cli
from receipt_insights.app import app, get_api
from receipt_insights.client import ReceiptApi
from receipt_insights.insights import Receipt, summarize

EXPECTED = {
    "count": 4,
    "totalUsd": "90.0",
    "totalsByCurrency": {"EUR": "20.2", "INR": "1500", "USD": "50.1"},
    "topPayers": [{"payer": "ben", "usd": "50.1"}, {"payer": "asha", "usd": "33.9"}, {"payer": "chen", "usd": "6.0"}],
}


def mock_api(receipts: list[dict], status: int = 200) -> ReceiptApi:
    """Real ReceiptApi + real httpx, only the transport is fake: exercises URL, params and JSON parsing."""
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/receipts"
        assert request.url.params["limit"] == "100"
        return httpx.Response(status, json=receipts if status == 200 else {"error": "boom"})
    return ReceiptApi("http://backend", client=httpx.Client(base_url="http://backend", transport=httpx.MockTransport(handler)))


def test_summarize_is_exact_decimal(receipts):
    # 0.1-style float errors would show up here as 90.00000000001
    assert summarize([Receipt.from_api(r) for r in receipts]) == EXPECTED


def test_summarize_empty_and_top_n():
    assert summarize([]) == {"count": 0, "totalUsd": "0", "totalsByCurrency": {}, "topPayers": []}


def test_top_payer_ties_are_deterministic():
    rs = [Receipt.from_api({"receiptId": i, "payer": p, "amount": 1, "currency": "USD", "usdAmount": 1}) for i, p in enumerate("zab")]
    assert [t["payer"] for t in summarize(rs, top_n=2)["topPayers"]] == ["a", "b"]


def test_api_endpoint_reads_backend(receipts):
    app.dependency_overrides[get_api] = lambda: mock_api(receipts)
    try:
        res = TestClient(app).get("/insights/summary", params={"top": 1})
    finally:
        app.dependency_overrides.clear()
    assert res.status_code == 200
    assert res.json()["topPayers"] == [{"payer": "ben", "usd": "50.1"}]
    assert res.json()["count"] == 4


def test_api_returns_502_when_backend_fails():
    app.dependency_overrides[get_api] = lambda: mock_api([], status=500)
    try:
        res = TestClient(app).get("/insights/summary")
    finally:
        app.dependency_overrides.clear()
    assert res.status_code == 502


def test_api_validates_top():
    assert TestClient(app).get("/insights/summary", params={"top": 0}).status_code == 422


def test_cli_from_stdin(receipts):
    out = io.StringIO()
    assert cli.main(["--stdin"], stdin=io.StringIO(json.dumps(receipts)), stdout=out) == 0
    assert json.loads(out.getvalue()) == EXPECTED

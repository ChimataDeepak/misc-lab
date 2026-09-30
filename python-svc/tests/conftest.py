import pytest

RECEIPTS = [
    {"receiptId": "1", "payer": "asha", "amount": 1000, "currency": "INR", "usdAmount": 12.0, "createdAt": "2026-09-30T12:00:00Z"},
    {"receiptId": "2", "payer": "ben", "amount": 50.10, "currency": "usd", "usdAmount": 50.10, "createdAt": "2026-09-30T12:01:00Z"},
    {"receiptId": "3", "payer": "asha", "amount": 20.20, "currency": "EUR", "usdAmount": 21.9, "createdAt": "2026-09-30T12:02:00Z"},
    {"receiptId": "4", "payer": "chen", "amount": 500, "currency": "INR", "usdAmount": 6.0, "createdAt": "2026-09-30T12:03:00Z"},
]


@pytest.fixture
def receipts() -> list[dict]:
    return [dict(r) for r in RECEIPTS]

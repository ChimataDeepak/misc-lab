"""Pure summary logic. No I/O here, so it is trivial to test and reuse from the API and the CLI."""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Iterable, Mapping


@dataclass(frozen=True)
class Receipt:
    receipt_id: str
    payer: str
    amount: Decimal
    currency: str
    usd_amount: Decimal

    @staticmethod
    def from_api(d: Mapping[str, Any]) -> "Receipt":
        # Decimal(str(x)): JSON numbers arrive as float; going via str keeps 12.1 as 12.1, not 12.0999999...
        return Receipt(
            receipt_id=str(d["receiptId"]),
            payer=str(d["payer"]),
            amount=Decimal(str(d["amount"])),
            currency=str(d["currency"]).upper(),
            usd_amount=Decimal(str(d["usdAmount"])),
        )


def summarize(receipts: Iterable[Receipt], top_n: int = 3) -> dict[str, Any]:
    by_currency: dict[str, Decimal] = defaultdict(Decimal)
    by_payer: dict[str, Decimal] = defaultdict(Decimal)
    count = 0
    total_usd = Decimal("0")
    for r in receipts:
        count += 1
        by_currency[r.currency] += r.amount
        by_payer[r.payer] += r.usd_amount
        total_usd += r.usd_amount
    # ties broken by payer name so the output is deterministic
    top = sorted(by_payer.items(), key=lambda kv: (-kv[1], kv[0]))[:top_n]
    return {
        "count": count,
        "totalUsd": str(total_usd),
        "totalsByCurrency": {c: str(v) for c, v in sorted(by_currency.items())},
        "topPayers": [{"payer": p, "usd": str(v)} for p, v in top],
    }

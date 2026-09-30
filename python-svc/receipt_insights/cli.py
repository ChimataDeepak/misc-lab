"""CLI: `receipt-insights --stdin < receipts.json` or `receipt-insights --url http://localhost:8090`."""
from __future__ import annotations

import argparse
import json
import sys
from typing import TextIO

from .client import ReceiptApi
from .insights import Receipt, summarize


def main(argv: list[str] | None = None, stdin: TextIO = sys.stdin, stdout: TextIO = sys.stdout) -> int:
    p = argparse.ArgumentParser(prog="receipt-insights")
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--stdin", action="store_true", help="read a JSON array of receipts from stdin")
    src.add_argument("--url", help="backend base URL, e.g. http://localhost:8090")
    p.add_argument("--top", type=int, default=3)
    args = p.parse_args(argv)
    if args.stdin:
        receipts = [Receipt.from_api(d) for d in json.load(stdin)]
    else:
        receipts = ReceiptApi(args.url).list_receipts()
    json.dump(summarize(receipts, top_n=args.top), stdout, indent=2)
    stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

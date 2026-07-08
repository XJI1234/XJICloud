#!/usr/bin/env python3
"""Standalone local test runner: read images from INPUT_DIR, write PLY to OUTPUT_PATH."""

import os
import sys
from pathlib import Path

from mock_trainer import run_mock_training


def main() -> None:
    input_dir = Path(os.environ.get("INPUT_DIR", "/input"))
    output_path = Path(os.environ.get("OUTPUT_PATH", "/output/model.ply"))

    if not input_dir.is_dir():
        print(f"[local-test] ERROR: INPUT_DIR not found: {input_dir}", file=sys.stderr)
        sys.exit(1)

    files = [path for path in input_dir.iterdir() if path.is_file()]
    if not files:
        print(f"[local-test] ERROR: no files in {input_dir}", file=sys.stderr)
        sys.exit(1)

    def report(percent: int, stage: str, message: str) -> None:
        print(f"[local-test] {percent}% {stage} - {message}")

    run_mock_training(input_dir, output_path, report)
    print(f"[local-test] done: {output_path}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Run one ECI training task using backend-issued storage URLs."""

import json
import os
import tempfile
from pathlib import Path
from typing import Any

from mock_trainer import run_mock_training
from oss_client import download_dataset_images, upload_file


def _report(percent: int, stage: str, message: str) -> None:
    print(f"[training] {percent}% {stage}: {message}", flush=True)


def _input_images() -> list[dict[str, Any]]:
    raw = os.environ.get("XJI_INPUT_FILES_JSON")
    if not raw:
        raise ValueError("XJI_INPUT_FILES_JSON is required")

    images = json.loads(raw)
    if not isinstance(images, list) or not images:
        raise ValueError("XJI_INPUT_FILES_JSON must contain a non-empty array")

    for image in images:
        if not isinstance(image, dict) or not image.get("downloadUrl"):
            raise ValueError("each input file must include downloadUrl")
        file_name = image.get("fileName")
        if file_name and (Path(file_name).name != file_name or file_name in {".", ".."}):
            raise ValueError("input fileName must be a plain file name")
    return images


def main() -> None:
    images = _input_images()
    upload_url = os.environ.get("XJI_OUTPUT_UPLOAD_URL")
    if not upload_url:
        raise ValueError("XJI_OUTPUT_UPLOAD_URL is required")

    with tempfile.TemporaryDirectory(prefix="xjicloud-eci-task-") as temp_dir:
        work_dir = Path(temp_dir)
        dataset_dir = work_dir / "dataset"
        output_path = work_dir / "output" / "model.ply"

        download_dataset_images(images, dataset_dir)
        run_mock_training(dataset_dir, output_path, _report)
        upload_file(output_path, upload_url, content_type="application/octet-stream")

    print("[training] result uploaded", flush=True)


if __name__ == "__main__":
    main()

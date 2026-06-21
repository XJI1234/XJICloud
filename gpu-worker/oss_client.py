#!/usr/bin/env python3
"""OSS 传输：通过后端签发的 presigned URL 下载/上传（无需 boto3）."""

from __future__ import annotations

from pathlib import Path
from typing import Iterable

import requests


def download_file(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    response = requests.get(url, stream=True, timeout=300)
    response.raise_for_status()
    with destination.open("wb") as handle:
        for chunk in response.iter_content(chunk_size=1024 * 1024):
            if chunk:
                handle.write(chunk)


def upload_file(local_path: Path, upload_url: str, content_type: str = "application/octet-stream") -> None:
    with local_path.open("rb") as handle:
        response = requests.put(
            upload_url,
            data=handle,
            headers={"Content-Type": content_type},
            timeout=600,
        )
    response.raise_for_status()


def download_dataset_images(images: Iterable[dict], target_dir: Path) -> None:
    target_dir.mkdir(parents=True, exist_ok=True)
    for index, image in enumerate(images, start=1):
        file_name = image.get("fileName") or f"image_{index}.jpg"
        download_file(image["downloadUrl"], target_dir / file_name)

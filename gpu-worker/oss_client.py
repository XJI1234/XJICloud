#!/usr/bin/env python3
"""S3-compatible OSS client for GPU worker."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable

import boto3
from botocore.client import Config


def create_s3_client():
    endpoint = os.environ.get("OSS_ENDPOINT", "http://127.0.0.1:9000")
    region = os.environ.get("OSS_REGION", "us-east-1")
    access_key = os.environ.get("OSS_ACCESS_KEY", "minioadmin")
    secret_key = os.environ.get("OSS_SECRET_KEY", "minioadmin")
    path_style = os.environ.get("OSS_PATH_STYLE", "true").lower() == "true"

    return boto3.client(
        "s3",
        endpoint_url=endpoint,
        region_name=region,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        config=Config(signature_version="s3v4", s3={"addressing_style": "path" if path_style else "auto"}),
    )


def download_file(url: str, destination: Path) -> None:
    import requests

    destination.parent.mkdir(parents=True, exist_ok=True)
    response = requests.get(url, stream=True, timeout=300)
    response.raise_for_status()
    with destination.open("wb") as handle:
        for chunk in response.iter_content(chunk_size=1024 * 1024):
            if chunk:
                handle.write(chunk)


def upload_file(local_path: Path, upload_url: str, content_type: str = "application/octet-stream") -> None:
    import requests

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

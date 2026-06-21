#!/usr/bin/env python3
"""Mock training pipeline with staged progress reporting."""

from __future__ import annotations

import time
from pathlib import Path
from typing import Callable


ProgressCallback = Callable[[int, str, str], None]


STAGES = [
    (10, "prepare", "准备数据集"),
    (25, "preprocess", "预处理图片"),
    (45, "train", "训练 Gaussian Splatting 模型"),
    (75, "optimize", "优化模型参数"),
    (95, "export", "导出模型文件"),
]


def generate_placeholder_ply(output_path: Path, image_count: int) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    header = f"""ply
format ascii 1.0
element vertex 3
property float x
property float y
property float z
property uchar red
property uchar green
property uchar blue
end_header
0 0 0 255 0 0
1 0 0 0 255 0
0 1 0 0 0 255
"""
    output_path.write_text(header, encoding="utf-8")
    meta = output_path.with_suffix(".meta.txt")
    meta.write_text(f"mock-model generated from {image_count} images\n", encoding="utf-8")


def run_mock_training(dataset_dir: Path, output_path: Path, report: ProgressCallback) -> None:
    image_count = len(list(dataset_dir.glob("*")))
    report(5, "init", f"已加载 {image_count} 张图片")

    for percent, stage, message in STAGES:
        time.sleep(2)
        report(percent, stage, message)

    generate_placeholder_ply(output_path, image_count)
    report(100, "completed", "模型导出完成")

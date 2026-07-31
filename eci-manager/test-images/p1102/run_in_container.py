#!/usr/bin/env python3
"""ECI 容器入口：从 OSS 读输入 → 解题 → 写回 OSS（presigned URL）。"""
import json
import os
import sys
import urllib.request

# Inline solve (keep self-contained for python3 -c embedding)
def count_pairs(n, c, a):
    a = sorted(a)
    ans = 0
    l = 0
    r = -1
    for i in range(n):
        while l < i and a[l] + c < a[i]:
            l += 1
        while r + 1 < i and a[r + 1] + c <= a[i]:
            r += 1
        ans += 1 + r - l
    return ans


def solve_from_text(text):
    lines = text.strip().splitlines()
    n_c = lines[0].split()
    n, c = int(n_c[0]), int(n_c[1])
    a = list(map(int, lines[1].split()))
    return f"{count_pairs(n, c, a)}\n"


def main():
    upload_url = os.environ.get("XJI_OUTPUT_UPLOAD_URL")
    if not upload_url:
        print("XJI_OUTPUT_UPLOAD_URL is required", file=sys.stderr)
        return 1

    input_json = os.environ.get("XJI_INPUT_FILES_JSON")
    if not input_json:
        print("XJI_INPUT_FILES_JSON is required", file=sys.stderr)
        return 1

    items = json.loads(input_json)
    if not items:
        print("XJI_INPUT_FILES_JSON is empty", file=sys.stderr)
        return 1

    item = items[0]
    download_url = item["downloadUrl"]
    with urllib.request.urlopen(download_url, timeout=120) as resp:
        input_text = resp.read().decode("utf-8")
    print(f"downloaded {item.get('ossKey')} bytes={len(input_text)}")

    answer = solve_from_text(input_text)
    body = answer.encode("utf-8")
    req = urllib.request.Request(
        upload_url,
        data=body,
        method="PUT",
        headers={"Content-Type": "application/octet-stream"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        print(f"upload status {resp.status}")
    print(f"uploaded to {os.environ.get('XJI_OUTPUT_OSS_KEY')} answer={answer.strip()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

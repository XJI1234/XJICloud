#!/usr/bin/env python3
import json
import os
import sys
from datetime import datetime, timezone

import requests


def main() -> int:
    upload_url = os.environ.get("XJI_OUTPUT_UPLOAD_URL")
    if not upload_url:
        print("XJI_OUTPUT_UPLOAD_URL is required", file=sys.stderr)
        return 1

    body = f"eci-oss-smoke ok at {datetime.now(timezone.utc).isoformat()}\n".encode()
    response = requests.put(
        upload_url,
        data=body,
        headers={"Content-Type": "text/plain"},
        timeout=120,
    )
    response.raise_for_status()

    input_json = os.environ.get("XJI_INPUT_FILES_JSON")
    if input_json:
        for item in json.loads(input_json):
            print(f"input available: {item.get('ossKey')}")

    print(f"uploaded to {os.environ.get('XJI_OUTPUT_OSS_KEY')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

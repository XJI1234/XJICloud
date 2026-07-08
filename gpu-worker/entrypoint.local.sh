#!/bin/bash
set -euo pipefail

if command -v colmap &>/dev/null; then
    echo "COLMAP CLI available: $(colmap -h 2>&1 | head -1)"
fi

cd /opt/xjicloud-worker
exec python3 local_test_runner.py

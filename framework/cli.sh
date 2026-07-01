#!/usr/bin/env bash
set -euo pipefail
exec java -jar "$(dirname "$0")/target/xjicloud-framework-1.0.0.jar" --cli "$@"

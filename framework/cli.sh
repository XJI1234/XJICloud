#!/usr/bin/env bash
# CLI 使用本地 target/ 构建产物（不随 git 同步，需先 mvn package 或由 install.sh 构建）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

find_built_jar() {
  find "$ROOT/target" -maxdepth 1 -name 'xjicloud-framework-*.jar' ! -name '*.original' -type f 2>/dev/null | head -1
}

JAR="$(find_built_jar)"
if [[ -z "$JAR" ]]; then
  echo "未找到 JAR。请先本地构建:" >&2
  echo "  cd $ROOT && mvn -DskipTests package" >&2
  echo "或执行: sudo $ROOT/install.sh upgrade" >&2
  exit 1
fi

exec java -jar "$JAR" --cli "$@"

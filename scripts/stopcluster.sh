#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT_DIR/depchain/run-logs/pids.txt"

if [[ -f "$PID_FILE" ]]; then
  xargs -r kill < "$PID_FILE" || true
  rm -f "$PID_FILE"
fi

# fallback cleanup by ports
lsof -ti :12001-12003 $(seq 8001 8008 | sed 's/^/:/') 2>/dev/null | xargs -r kill -9 || true
pkill -f "run_client.sh" || true
pkill -f "run_replica.sh" || true
echo "Cluster stopped."

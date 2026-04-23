#!/usr/bin/env bash
# This script tests a simple transfer from Client 1 to Client 2 
# in a network of 5 replicas.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPCHAIN_DIR="$ROOT_DIR/depchain"
LOG_DIR="$DEPCHAIN_DIR/run-logs"

# Configuration
REPLICA_COUNT=5
CLIENT_COUNT=3
SENDER_ID=1
RECEIVER_ID=2
# Command: transfer to_client_id amount gas_price gas_limit
COMMAND="transfer $RECEIVER_ID 100 1 300000"

mkdir -p "$LOG_DIR"
# Clear old logs
rm -f "$LOG_DIR"/replica*.log "$LOG_DIR"/client.log

cd "$DEPCHAIN_DIR"

# 1. Cleanup: Kill any previous runs
echo "Cleaning up stale processes..."
if [[ -f "$LOG_DIR/pids.txt" ]]; then
    xargs -r kill -9 < "$LOG_DIR/pids.txt" 2>/dev/null || true
    rm -f "$LOG_DIR/pids.txt"
fi

# Kill anything on ports 8001-8005 (replicas) and 12001-12003 (clients)
lsof -ti :8001-8005,12001-12003 | xargs -r kill -9 || true

# 2. Start 5 Replicas
echo "Starting $REPLICA_COUNT replicas..."
for rid in $(seq 0 $((REPLICA_COUNT-1))); do
    # Note: Using rid starting from 1 to match common config patterns
    nohup mvn -q -DskipTests \
        -Dexec.mainClass=com.depchain.consensus.Replica \
        -Dexec.args="$rid" \
        org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
        > "$LOG_DIR/replica${rid}.log" 2>&1 &
    echo $! >> "$LOG_DIR/pids.txt"
done

# 3. Wait for consensus network to stabilize
echo "Waiting 5 seconds for replicas to initialize and form Quorum..."
sleep 5

# 4. Run Client 1 and send the transfer
echo "Client $SENDER_ID sending: $COMMAND"
# We pipe the command followed by 'exit' to ensure the CLI closes after sending
printf '%s\nexit\n' "$COMMAND" | \
    mvn -q -DskipTests \
    -Dexec.mainClass=com.depchain.client.DepChainClientCLI \
    -Dexec.args="$SENDER_ID" \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
    > "$LOG_DIR/client.log" 2>&1 || true

# 5. Output Results
echo "--------------------------------------"
echo "Test Finished."
echo "Check $LOG_DIR/client.log for 'Transfer submitted' or 'ACK'."
echo "--------------------------------------"
echo "Tail of Client Log:"
tail -n 15 "$LOG_DIR/client.log"
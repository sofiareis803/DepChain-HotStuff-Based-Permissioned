#!/bin/bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPCHAIN_DIR="$ROOT_DIR/depchain"
LOG_DIR="$DEPCHAIN_DIR/run-logs"


mkdir -p "$LOG_DIR"
rm -f "$LOG_DIR"/replica*.log "$LOG_DIR"/client*.log "$LOG_DIR"/pids.txt

# Stop cluster if running
bash scripts/stopcluster.sh

# Kill anything on ports 8001-8005 (replicas) and 12001-12003 (clients)
lsof -ti :8001-8005,12001-12003 | xargs -r kill -9 || true

REPLICA_COUNT=5
echo "Starting $REPLICA_COUNT replicas..."
> "$LOG_DIR/pids.txt"
for rid in $(seq 0 $((REPLICA_COUNT-1))); do
  bash scripts/run_replica.sh "$rid" > "$LOG_DIR/replica${rid}.log" 2>&1 &
  echo $! >> "$LOG_DIR/pids.txt"
done

# Wait for replicas to start
echo "Waiting for replicas to start..."
sleep 5

CLIENT1="./scripts/run_client.sh 1" 
CLIENT2="./scripts/run_client.sh 2"

echo "=== Client 1 increases allowance for Client 2 by 100 tokens ==="
(echo "increase-allowance 2 100 1 30000"; sleep 15; echo "exit") | $CLIENT1 >> "$LOG_DIR/client1.log" 2>&1
# Wait for the block to commit
sleep 5

echo "=== Frontrunning Attack ==="

echo "Client 1: Reducing allowance to 50"
(echo "decrease-allowance 2 50 1 30000"; sleep 15; echo "exit") | $CLIENT1 >> "$LOG_DIR/client1.log" 2>&1 &
CLIENT1_PID=$!

# Client 2 monitors the network, sees Client 1's transaction, and immediately sends
# a 'transfer-from' transaction. 
# CRITICAL: Client 2 uses a much higher gas price 
echo "Client 2: Frontrunning with transfer-from 100"
(echo "transfer-from 1 2 100 50 30000"; sleep 15; echo "exit") | $CLIENT2 >> "$LOG_DIR/client2.log" 2>&1 &
CLIENT2_PID=$!

# Wait for both background transactions to finish 
wait $CLIENT1_PID
wait $CLIENT2_PID

# Wait for the consensus leader
sleep 3

echo "=== The Aftermath ==="

# Because Client 2 used a higher gas price, his transfer executed first
# Then Client 1's approval executed second, setting his allowance back to 50.
# Client 2 now does a second transfer to steal the remaining 50 tokens.
echo "Client 2: Stealing the remaining 50 tokens"
(echo "transfer-from 1 2 50 1 30000"; sleep 15; echo "exit") | $CLIENT2 >> "$LOG_DIR/client2.log" 2>&1
sleep 3

# TODO: ADD balance function

echo "Frontrunning test complete. Check replica's logs and client balances!"

echo "=== Stopping the Replicas ==="
bash scripts/stopcluster.sh
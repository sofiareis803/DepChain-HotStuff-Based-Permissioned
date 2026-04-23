#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPCHAIN_DIR="$ROOT_DIR/depchain"
LOG_DIR="$DEPCHAIN_DIR/run-logs"
REPLICA_COUNT=5
CLIENT_ID=1
CLIENT_PORT=12001

# Array of fault types to test
FAULT_TYPES=("silent" "conflict" "delay")
AFFECTED_PHASES=("PREPARE" "PRECOMMIT" "COMMIT" "DECIDE")

run_test() {
    local fault_type=$1
    local affected_phase=$2
    local test_name="test_${fault_type}_${affected_phase}"
    
    echo "==============================================="
    echo "Running test: $test_name"
    echo "==============================================="
    
    mkdir -p "$LOG_DIR/$test_name"
    rm -f "$LOG_DIR/$test_name"/replica*.log "$LOG_DIR/$test_name"/client.log "$LOG_DIR/$test_name"/pids.txt
    
    # Kill stale processes
    if [[ -f "$LOG_DIR/$test_name/pids.txt" ]]; then
        xargs -r kill < "$LOG_DIR/$test_name/pids.txt" 2>/dev/null || true
        rm -f "$LOG_DIR/$test_name/pids.txt"
    fi
    
    # Kill any process bound to required ports
    REPLICA_PORTS=()
    for port in $(seq 8001 $((8000 + REPLICA_COUNT))); do
        REPLICA_PORTS+=("-ti" ":$port")
    done
    lsof -ti ":$CLIENT_PORT" "${REPLICA_PORTS[@]}" 2>/dev/null | xargs -r kill -9 || true
    
    sleep 1
    
    cd "$DEPCHAIN_DIR"
    
    echo "Starting $REPLICA_COUNT replicas with fault: $fault_type on phase: $affected_phase"
    for rid in $(seq 0 $((REPLICA_COUNT - 1))); do
        # Replica 2 is Byzantine
        if [[ $rid -eq 2 ]]; then
            REPLICA_ARGS="$rid --byzantine-type=$fault_type --affected-phases=$affected_phase"
        else
            REPLICA_ARGS="$rid"
        fi
        
        nohup mvn -q -DskipTests \
            -Dexec.mainClass=com.depchain.consensus.Replica \
            -Dexec.args="$REPLICA_ARGS" \
            org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
            > "$LOG_DIR/$test_name/replica${rid}.log" 2>&1 &
        echo $! >> "$LOG_DIR/$test_name/pids.txt"
    done
    
    sleep 3
    
    echo "Sending 20 client commands..."
    printf '%s\n' \
        "append cmd1" \
        "append cmd2" \
        "append cmd3" \
        "append cmd4" \
        "append cmd5" \
        "append cmd6" \
        "append cmd7" \
        "append cmd8" \
        "append cmd9" \
        "append cmd10" \
        "append cmd11" \
        "append cmd12" \
        "append cmd13" \
        "append cmd14" \
        "append cmd15" \
        "append cmd16" \
        "append cmd17" \
        "append cmd18" \
        "append cmd19" \
        "append cmd20" \
        "exit" | \
        mvn -q -DskipTests \
            -Dexec.mainClass=com.depchain.client.DepChainClientCLI \
            -Dexec.args="$CLIENT_ID" \
            org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
            > "$LOG_DIR/$test_name/client.log" 2>&1 || true
    
    sleep 2
    
    echo "Test $test_name completed!"
    echo "Logs saved in: $LOG_DIR/$test_name"
    echo "Client log excerpt:"
    tail -n 10 "$LOG_DIR/$test_name/client.log" || true
    echo
    
    # Kill processes for this test
    if [[ -f "$LOG_DIR/$test_name/pids.txt" ]]; then
        xargs -r kill < "$LOG_DIR/$test_name/pids.txt" 2>/dev/null || true
    fi
    
    sleep 2
}

# Run all combinations
for fault_type in "${FAULT_TYPES[@]}"; do
    for phase in "${AFFECTED_PHASES[@]}"; do
        run_test "$fault_type" "$phase"
    done
done

echo "==============================================="
echo "All tests completed!"
echo "==============================================="
echo "Logs are organized in: $LOG_DIR/test_*/  "
echo ""
echo "Summary of tests run:"
for fault_type in "${FAULT_TYPES[@]}"; do
    for phase in "${AFFECTED_PHASES[@]}"; do
        echo "  - test_${fault_type}_${phase}"
    done
done
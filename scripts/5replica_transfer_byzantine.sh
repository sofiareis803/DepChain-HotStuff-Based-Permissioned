#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPCHAIN_DIR="$ROOT_DIR/depchain"
LOG_DIR="$DEPCHAIN_DIR/run-logs"
REPLICA_COUNT=5
BYZANTINE_REPLICA=2
SENDER_ID=1
RECEIVER_ID=2
RESULTS_FILE="$LOG_DIR/test_transfer_results.csv"

FAULT_TYPES=("none" "silent" "conflict" "delay")
AFFECTED_PHASES=("PREPARE" "PRECOMMIT" "COMMIT" "DECIDE")

mkdir -p "$LOG_DIR"

# Initialize results file with header
echo "test_name,fault_type,affected_phase,duration_seconds,status" > "$RESULTS_FILE"

run_test() {
    local fault_type=$1
    local affected_phase=$2
    local test_name="test_transfer_${fault_type}_${affected_phase}"
    local test_log_dir="$LOG_DIR/$test_name"
    
    echo "==============================================="
    echo "Running test: $test_name"
    echo "==============================================="
    
    mkdir -p "$test_log_dir"
    rm -f "$test_log_dir"/replica*.log "$test_log_dir"/client.log "$test_log_dir"/pids.txt
    
    # Kill any stale processes bound to required ports
    REPLICA_PORTS=()
    for port in $(seq 8001 $((8000 + REPLICA_COUNT))); do
        REPLICA_PORTS+=("-ti" ":$port")
    done
    lsof -ti :12001-12003 "${REPLICA_PORTS[@]}" 2>/dev/null | xargs -r kill -9 || true
    
    sleep 2
    
    cd "$DEPCHAIN_DIR"
    echo "Starting $REPLICA_COUNT replicas (fault_type=$fault_type, phase=$affected_phase)"
    
    # Measure execution time
    local start_time=$(date +%s%N)
    
    for rid in $(seq 0 $((REPLICA_COUNT - 1))); do
        if [[ "$fault_type" != "none" ]] && [[ $rid -eq $BYZANTINE_REPLICA ]]; then
            REPLICA_ARGS="$rid --byzantine-type=$fault_type --affected-phases=$affected_phase"
        else
            REPLICA_ARGS="$rid"
        fi
        
        nohup mvn -q -DskipTests \
            -Dexec.mainClass=com.depchain.consensus.Replica \
            -Dexec.args="$REPLICA_ARGS" \
            org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
            > "$test_log_dir/replica${rid}.log" 2>&1 &
        PID=$!
        echo $PID >> "$test_log_dir/pids.txt"
        disown $PID 2>/dev/null || true
    done
    
    sleep 5
    
    echo "Sending 5 transfer commands..."
    printf '%s\n' \
        "transfer 2 100 1 300000" \
        "transfer 3 50 1 300000" \
        "transfer 4 75 1 300000" \
        "transfer 5 125 1 300000" \
        "transfer 2 200 1 300000" \
        "exit" | \
        mvn -q -DskipTests \
            -Dexec.mainClass=com.depchain.client.DepChainClientCLI \
            -Dexec.args="$SENDER_ID" \
            org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
            > "$test_log_dir/client.log" 2>&1 || true
    
    # Wait for replicas to finish consensus
    sleep 10
    
    # End measuring time (after replicas finish processing)
    local end_time=$(date +%s%N)
    local duration_ns=$((end_time - start_time))
    local duration_s=$(echo "scale=3; $duration_ns / 1000000000" | bc)
    
    # Check test success status
    local status="OK"
    if grep -q "Failed to\|error\|exception" "$test_log_dir/client.log" 2>/dev/null; then
        grep -q "Failed to" "$test_log_dir/client.log" && status="FAILED" || status="ERROR"
    fi
    
    echo "Test $test_name completed in ${duration_s}s (Status: $status)"
    echo "Logs saved in: $test_log_dir"
    tail -n 10 "$test_log_dir/client.log" || true
    echo
    
    # Save result to CSV
    echo "$test_name,$fault_type,$affected_phase,$duration_s,$status" >> "$RESULTS_FILE"
    
    # Kill processes
    if [[ -f "$test_log_dir/pids.txt" ]]; then
        xargs -r kill -9 < "$test_log_dir/pids.txt" 2>/dev/null || true
    fi
    
    sleep 1
}

# Run baseline test and all Byzantine combinations
echo "Running baseline test (no Byzantine faults)..."
run_test "none" "N/A"

# Run all Byzantine combinations
for fault_type in "${FAULT_TYPES[@]:1}"; do  # Skip "none"
    for phase in "${AFFECTED_PHASES[@]}"; do
        run_test "$fault_type" "$phase"
    done
done

echo "==============================================="
echo "All tests completed!"
echo "==============================================="
echo ""
echo "Results saved to: $RESULTS_FILE"
echo ""
echo "Summary table:"
echo "=============================================="
cat "$RESULTS_FILE" | column -t -s','
echo ""
echo "Summary by fault type (average duration):"
echo "=============================================="
awk -F',' 'NR>1 {
    fault_type=$2
    duration=$4
    count[fault_type]++
    total[fault_type]+= duration
}
END {
    for (ft in total) {
        printf "%-15s: %.3f seconds\n", ft, total[ft]/count[ft]
    }
}' "$RESULTS_FILE" | sort

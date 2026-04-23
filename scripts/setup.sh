set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "Usage: bash setup.sh <replica-count> <client-count>"
    exit 1
fi

REPLICA_COUNT="$1"
CLIENT_COUNT="$2"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPCHAIN_DIR="$WORKSPACE_DIR/depchain"
THRESHOLD_DIR="$WORKSPACE_DIR/security/threshold"
CONFIG_DIR="$WORKSPACE_DIR/config"
CONFIG_FILE="$CONFIG_DIR/config.txt"


echo "Writing config for $REPLICA_COUNT replicas and $CLIENT_COUNT clients..."
{
    echo "Replicas"
    for i in $(seq 1 "$REPLICA_COUNT"); do
        printf '%s,127.0.0.1,%s\n' "$i" "$((8000 + i))"
    done
    echo ""
    echo "Clients"
    for i in $(seq 1 "$CLIENT_COUNT"); do
        printf '%s,127.0.0.1,%s\n' "$i" "$((12000 + i))"
    done
} > "$CONFIG_FILE"

bash "$SCRIPT_DIR/generate_keys.sh" "$REPLICA_COUNT" "$CLIENT_COUNT"

mkdir -p "$THRESHOLD_DIR"
rm -f "$THRESHOLD_DIR"/*.json

cd "$DEPCHAIN_DIR"

echo "Compiling depchain and copying dependencies..."
mvn -q -DskipTests compile dependency:copy-dependencies

echo "Generating threshold configs for $REPLICA_COUNT replicas..."
java -cp "target/classes:target/dependency/*" com.depchain.crypto.ThresholdSignaturesGenerator "$REPLICA_COUNT"

echo "Threshold configs generated under security/threshold"
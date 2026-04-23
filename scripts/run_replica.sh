set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: bash scripts/run_replica.sh <replica-id>"
    exit 1
fi

REPLICA_ID="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPCHAIN_DIR="$WORKSPACE_DIR/depchain"

cd "$DEPCHAIN_DIR"

mvn -q -DskipTests \
    -Dexec.mainClass=com.depchain.consensus.Replica \
    -Dexec.args="$REPLICA_ID" \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java
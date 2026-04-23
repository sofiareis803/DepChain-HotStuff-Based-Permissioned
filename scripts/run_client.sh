set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: bash scripts/run_client.sh <client-id>"
    exit 1
fi

CLIENT_ID="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPCHAIN_DIR="$WORKSPACE_DIR/depchain"

cd "$DEPCHAIN_DIR"

mvn -q -DskipTests \
    -Dexec.mainClass=com.depchain.client.DepChainClientCLI \
    -Dexec.args="$CLIENT_ID" \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java

KEYSTORE_TYPE="PKCS12"
NODE_COUNT=3
CLIENT_COUNT=3
KEYSTORE_DIR="../security/keystores"
TRUSTSTORE_DIR="../security/truststores"
CERTS_DIR="../security/certs"
PASS="depchain"

if [ $# -ge 2 ]; then
    NODE_COUNT="$1";
    CLIENT_COUNT="$2";
fi

echo "Cleaning up previous keystores..."
mkdir -p "${KEYSTORE_DIR}"
rm -f "${KEYSTORE_DIR}"/*.p12
mkdir -p "${TRUSTSTORE_DIR}"
rm -f "${TRUSTSTORE_DIR}"/*.p12
mkdir -p "${CERTS_DIR}"
rm -f "${CERTS_DIR}"/*.cer

 echo "Generating keystore for replicas..."
for i in $(seq 1 $NODE_COUNT); do
    keytool -genkeypair -alias "node$i" -keyalg EC  \
        -dname "CN=node$i, OU=SEC, O=IST, L=Lisbon, S=Lisbon, C=PT" \
        -storetype ${KEYSTORE_TYPE} -keystore "${KEYSTORE_DIR}/node$i.p12" \
        -storepass "$PASS" -keypass "$PASS" >/dev/null 2>&1
    
    # Export the public certificate and import it into a truststore
    keytool -exportcert -alias "node$i" -keystore "${KEYSTORE_DIR}/node$i.p12" -storepass "$PASS" -file "${CERTS_DIR}/node$i.cer" >/dev/null 2>&1
    keytool -importcert -alias "node$i" -file "${CERTS_DIR}/node$i.cer" -storetype ${KEYSTORE_TYPE} \
        -keystore "${TRUSTSTORE_DIR}/truststore.p12" -storepass "$PASS" -noprompt >/dev/null 2>&1
done

echo "Generating keystore for clients..."
for i in $(seq 1 $CLIENT_COUNT); do
    keytool -genkeypair -alias "client$i" -keyalg EC  \
        -dname "CN=client$i, OU=SEC, O=IST, L=Lisbon, S=Lisbon, C=PT" \
        -storetype ${KEYSTORE_TYPE} -keystore "${KEYSTORE_DIR}/client$i.p12" \
        -storepass "$PASS" -keypass "$PASS" >/dev/null 2>&1

    # Export the public certificate and import it into a global truststore
    keytool -exportcert -alias "client$i" -keystore "${KEYSTORE_DIR}/client$i.p12" -storepass "$PASS" -file "${CERTS_DIR}/client$i.cer" >/dev/null 2>&1
    keytool -importcert -alias "client$i" -file "${CERTS_DIR}/client$i.cer" -storetype ${KEYSTORE_TYPE} \
        -keystore "${TRUSTSTORE_DIR}/truststore.p12" -storepass "$PASS" -noprompt >/dev/null 2>&1
done



# SEC_25-26
## Run test
### Group 34
- Maria Sargaco nº1116310
- Francisco Catarino nº1116164
- Sofia Reis nº1116253

### Requirements
The system composition is 5 replicas and 3 clients

### Setup
Generates the threshold keys and keystores for the replicas and the clients. The number of replicas and clients must be the same as needed for the program that will be run.
```bash
bash scripts/setup.sh 5 3
```

### Build
Inside /depchain
```bash
mvn clean compile
```

### Run
Start each replica in a separate terminal, providing the replica ID (0 to 5) and the total number of replicas as arguments:
```bash
bash scripts/run_replica.sh 0
bash scripts/run_replica.sh 1
bash scripts/run_replica.sh 2
bash scripts/run_replica.sh 3
bash scripts/run_replica.sh 4
```
Then, in a separate terminal, run the client:
```bash
bash scripts/run_client.sh
```

### Tests
To run the unit tests:
```bash
cd depchain
mvn test
```

To run the script with the byzantine replica with time:
Simple test just with one transfer
```bash
bash scripts/5replica_tranfer.sh 
```
Tests the possible byzantine flags to each consensus phase, and return the times
```bash
bash scripts/5replica_transfer_byzantine.sh 
```
To test the frontrunning attack scenario
```bash
bash scripts/test_frontrunning.sh
```

## Threshold Signature
1. Replica -> Leader: nonce commitment
2. Leader -> Replicas: aggregated commitment + signer indices
3. Replica -> Leader: partial signature vote

### Threshold Config Setup
Generate threshold-signature configs before starting replicas. The number of replicas must be the same as needed for the program that will be run.

```bash
bash scripts/setup.sh 5 3
```

## Json Envelope
#### Append (Client -> Replicas)
```json
{
  "transaction": "client-transaction",
  "sender_id": 3,
  "hmac": "base64-hmac-value",
  "timestamp": 1700000000,
  "nonce": "unique-request-id-xyz",
  "format_version": 1   
}
```

#### Message (Leader -> Replica)
```json
{
  "message": {
    "type": "PREPARE",
    "view_number": 42,
    "node": {
      "parent_digest": "base64-parent-hash",
      "transaction": "client-transaction-123",
      "self_digest": "base64-self-hash"
    },
    "justify": {
      "qc_type": "PRECOMMIT",
      "view_number": 41,
      "node": {
        "parent_digest": "base64-parent-hash",
        "transaction": "previous-transaction-456",
        "self_digest": "base64-self-hash"
      },
      "aggregated_signature": "base64-threshold-signature"
    }
  },
  "sender_id": 3,
  "hmac": "base64-hmac-value",
  "timestamp": 1700000000,
  "nonce": "unique-request-id-xyz",
  "format_version": 1   
}
```

#### Vote (Replica -> Leader)
```json
{
  "vote": {
    "qc_type": "PREPARE",
    "view_number": 42,
    "node_digest": "base64-node-hash",
    "partial_signature": "base64-threshold-signature"
  },
  "sender_id": 3,
  "hmac": "base64-hmac-value",
  "timestamp": 1700000000,
  "nonce": "unique-request-id-xyz",
  "format_version": 1   
}
```

#### Nonce Commitment (Replica -> Leader)
```json
{
  "phase_one_vote": {
    "qc_type": "PREPARE",
    "view_number": 42,
    "node_digest": "base64-node-hash",
    "nonce_commitment": "base64-nonce-commitment"
  },
  "sender_id": 3,
  "hmac": "base64-hmac-value",
  "timestamp": 1700000000,
  "nonce": "unique-request-id-xyz",
  "format_version": 1
}
```

#### Aggregated Commitment (Leader -> Replicas)
```json
{
  "phase_two_vote": {
    "qc_type": "PREPARE",
    "view_number": 42,
    "node_digest": "base64-node-hash",
    "aggregated_commitment": "base64-aggregated-commitment",
    "signer_indices": [1, 2, 4]
  },
  "sender_id": 3,
  "hmac": "base64-hmac-value",
  "timestamp": 1700000000,
  "nonce": "unique-request-id-xyz",
  "format_version": 1
}
```

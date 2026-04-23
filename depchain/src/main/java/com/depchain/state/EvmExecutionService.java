package com.depchain.state;

import com.depchain.blockchain.model.Transaction;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EvmExecutionService {
    private static final String ABI_BOOL_FALSE = "0x0000000000000000000000000000000000000000000000000000000000000000";
    // Minimum amount of gas to pay
    private static final long INTRINSIC_GAS = 21_000L;

    private final SimpleWorld world;
    private Address contractAddress;

    public EvmExecutionService() {
        this.world = new SimpleWorld();
    }

    public void initializeAccount(String rawAddress, long balance, long nonce) {
        if (balance < 0 || nonce < 0) {
            throw new IllegalArgumentException("Balance and nonce must be non-negative");
        }

        Address address = AddressManager.parseAddress(rawAddress);
        if (world.get(address) != null) {
            return;
        }

        world.createAccount(address, nonce, Wei.of(balance));
    }

    public BlockExecutionResult executeBlock(List<Transaction> orderedTransactions, long blockGasLimit, int leaderReplicaId) {
        if (orderedTransactions == null || orderedTransactions.isEmpty()) {
            String hash = WorldStateHash.computeWorldStateHash(world);
            return new BlockExecutionResult(Collections.emptyList(), 0L, hash, true, null);
        }

        List<TxExecutionResult> txResults = new ArrayList<>();
        long totalGasUsed = 0L;

        for (Transaction tx : orderedTransactions) {
            TxExecutionResult txr = executeTransaction(tx, leaderReplicaId);
            txResults.add(txr);

            totalGasUsed += txr.gasUsed();

            if (totalGasUsed > blockGasLimit) {
                return new BlockExecutionResult(
                    txResults,
                    totalGasUsed,
                    WorldStateHash.computeWorldStateHash(world),
                    false,
                    "Block gas limit exceeded"
                );
            }
        }

        return new BlockExecutionResult(
            txResults,
            totalGasUsed,
            WorldStateHash.computeWorldStateHash(world),
            true,
            null
        );
    }
    public TxExecutionResult executeTransaction(Transaction tx, int leaderReplicaId) {
        if (tx == null) {
            return TxExecutionResult.failed("Null transaction", 0L, null, null);
        }
        if (tx.getGasLimit() <= 0 || tx.getGasPrice() <= 0) {
            return TxExecutionResult.failed("Invalid gas params", 0L, null, tx.getNonce());
        }

        try {
            Address sender = AddressManager.parseAddress(tx.getSender());
            Address dest = isBlank(tx.getDest()) ? null : AddressManager.parseAddress(tx.getDest());
            MutableAccount senderAccount = getAccount(sender);

            if (senderAccount == null) {
                return TxExecutionResult.failed("Sender account does not exist", 0L, tx.getTransactionHash(), tx.getNonce());
            }

            if (tx.getNonce() != senderAccount.getNonce()) {
                return TxExecutionResult.failed("Invalid nonce", 0L, tx.getTransactionHash(), tx.getNonce());
            }

            if (tx.getGasLimit() < INTRINSIC_GAS) {
                return TxExecutionResult.failed("Gas limit too low", tx.getGasLimit(), tx.getTransactionHash(), tx.getNonce());
            }

            // Check if sender can afford intrinsic gas
            long intrinsicGasCost;
            try {
                intrinsicGasCost = Math.multiplyExact(tx.getGasPrice(), INTRINSIC_GAS);
            } catch (ArithmeticException e) {
                return TxExecutionResult.failed("Intrinsic gas cost overflow", 0L, tx.getTransactionHash(), tx.getNonce());
            }

            if (senderAccount.getBalance().toLong() < intrinsicGasCost) {
                return TxExecutionResult.failed("Insufficient balance for intrinsic gas", INTRINSIC_GAS, tx.getTransactionHash(), tx.getNonce());
            }

            long maxGasCost;
            long totalRequired;
            try {
                maxGasCost = Math.multiplyExact(tx.getGasPrice(), tx.getGasLimit());
                // In native txn, this is the maximum the user will have to pay
                totalRequired = Math.addExact(tx.getValue(), maxGasCost);
            } catch (ArithmeticException e) {
                return TxExecutionResult.failed("Transaction cost overflow", 0L, tx.getTransactionHash(), tx.getNonce());
            }

            if (senderAccount.getBalance().toLong() < totalRequired) {
                return TxExecutionResult.failed("Insufficient balance for value + gas", 0L, tx.getTransactionHash(), tx.getNonce());
            }

            long prepaidGasCost = reserveGasUpfront(senderAccount, maxGasCost);

            long totalGasUsed = 0L;
            String finalReturnData = null;
            String finalError = null;

            try {
                // Case 1: Native transfer (if dest and value are provided)
                if (dest != null && tx.getValue() > 0) {
                    TxExecutionResult transferResult = executeNativeTransfer(tx, sender, dest, tx.getValue(), tx.getGasLimit());
                    totalGasUsed += transferResult.gasUsed();

                    if (!transferResult.success()) {
                        return settleGasAndReturn(senderAccount, tx, transferResult, prepaidGasCost, leaderReplicaId);
                    }
                }

                // Case 2: Contract operation (if callData is provided)
                if (!isBlank(tx.getCallData())) {
                    TxExecutionResult contractResult;
                    if (contractAddress == null) {
                        // Contract creation
                        contractResult = executeContractCreation(tx, sender, tx.getCallData(), tx.getGasLimit() - totalGasUsed);
                    } else {
                        // Contract call
                        contractResult = executeContractCall(tx, sender, contractAddress, tx.getCallData(), 0, tx.getGasLimit() - totalGasUsed);
                    }

                    totalGasUsed += contractResult.gasUsed();

                    if (!contractResult.success()) {
                        return settleGasAndReturn(senderAccount, tx, contractResult, prepaidGasCost, leaderReplicaId);
                    }

                    if (ABI_BOOL_FALSE.equals(contractResult.returnDataHex())) {
                        TxExecutionResult failure = TxExecutionResult.failed(
                            "Contract call returned false",
                            contractResult.gasUsed(),
                            tx.getTransactionHash(),
                            tx.getNonce()
                        );
                        return settleGasAndReturn(senderAccount, tx, failure, prepaidGasCost, leaderReplicaId);
                    }

                    finalReturnData = contractResult.returnDataHex();
                }

                TxExecutionResult success = new TxExecutionResult(tx.getTransactionHash(), tx.getNonce(), true, totalGasUsed, finalReturnData, finalError);
                return settleGasAndReturn(senderAccount, tx, success, prepaidGasCost, leaderReplicaId);
            } catch (Exception e) {
                TxExecutionResult failure = TxExecutionResult.failed(e.getMessage(), tx.getGasLimit(), tx.getTransactionHash(), tx.getNonce());
                return settleGasAndReturn(senderAccount, tx, failure, prepaidGasCost, leaderReplicaId);
            }

        } catch (Exception e) {
            return TxExecutionResult.failed(e.getMessage(), 0L, tx.getTransactionHash(), tx.getNonce());
        }
    }

    private TxExecutionResult executeNativeTransfer(Transaction tx, Address sender, Address dest, long value, long gasLimit) {

        if (dest == null) {
            return TxExecutionResult.failed("Native transfer missing destination", 0L, tx.getTransactionHash(), tx.getNonce());
        }

        try {
            MutableAccount senderAccount = getAccount(sender);
            MutableAccount destAccount = getAccount(dest);

            if (senderAccount == null) {
                return TxExecutionResult.failed("Sender account does not exist", 0L, tx.getTransactionHash(), tx.getNonce());
            }

            if (destAccount == null) {
                return TxExecutionResult.failed("Destination account does not exist", INTRINSIC_GAS, tx.getTransactionHash(), tx.getNonce());
            }

            if (senderAccount.getBalance().toLong() < value) {
                return TxExecutionResult.failed("Insufficient balance for transfer", INTRINSIC_GAS, tx.getTransactionHash(), tx.getNonce());
            }

            senderAccount.decrementBalance(Wei.of(value));
            destAccount.incrementBalance(Wei.of(value));

            long gasUsed = Math.min(gasLimit, INTRINSIC_GAS);
            return new TxExecutionResult(tx.getTransactionHash(), tx.getNonce(), true, gasUsed, null, null);
        } catch (Exception e) {
            return TxExecutionResult.failed(e.getMessage(), 0L, tx.getTransactionHash(), tx.getNonce());
        }
    }

    private TxExecutionResult executeContractCreation(Transaction tx, Address sender, String runtimeBytecodeHex, long gasLimit) {
        try {
            Address contract = AddressManager.deriveContractAddress(sender, tx.getNonce());
            contractAddress = contract;
            MutableAccount contractAccount = getOrCreateAccount(contract);

            Bytes runtimeCode = Bytes.fromHexStringLenient(runtimeBytecodeHex);
            if (runtimeCode == null || runtimeCode.isEmpty()) {
                return TxExecutionResult.failed("Contract deployment failed: runtime bytecode is empty", gasLimit, tx.getTransactionHash(), tx.getNonce());
            }
            contractAccount.setCode(runtimeCode);

            ISTCoinInitializer.initializeIstCoinRuntimeState(contractAccount);

            long gasUsed = Math.min(gasLimit, INTRINSIC_GAS);
            String returnData = runtimeCode.toHexString();

            return new TxExecutionResult(tx.getTransactionHash(), tx.getNonce(), true, gasUsed, returnData, null);
        } catch (Exception e) {
            return TxExecutionResult.failed(e.getMessage(), gasLimit, tx.getTransactionHash(), tx.getNonce());
        }
    }


    private TxExecutionResult executeContractCall(Transaction tx, Address sender, Address contractAddr, String callDataHex, long value, long gasLimit) {
        try {
            MutableAccount contractAccount = getAccount(contractAddr);
            if (contractAccount == null || contractAccount.getCode().isEmpty()) {
                return TxExecutionResult.failed("Contract account has no code", INTRINSIC_GAS, tx.getTransactionHash(), tx.getNonce());
            }

            ByteArrayOutputStream traceOutput = new ByteArrayOutputStream();
            try (PrintStream traceStream = new PrintStream(traceOutput, true, StandardCharsets.UTF_8)) {
                StandardJsonTracer tracer = new StandardJsonTracer(traceStream, true, true, true, true);

                EVMExecutor executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
                executor.tracer(tracer);
                executor.sender(sender);
                executor.contract(contractAddr);
                executor.receiver(contractAddr);
                executor.worldUpdater(world);
                executor.messageFrameType(MessageFrame.Type.MESSAGE_CALL);
                executor.code(contractAccount.getCode());
                executor.callData(Bytes.fromHexStringLenient(callDataHex));
                executor.ethValue(Wei.ZERO);

                executor.execute();
                executor.commitWorldState();

                EvmTraceParser.ParseResult parsed = EvmTraceParser.parseBooleanAndGasFromTracer(traceOutput, gasLimit, INTRINSIC_GAS);
                if (parsed == null) {
                    return TxExecutionResult.failed(
                        "Contract call missing ABI boolean return in tracer",
                        Math.min(gasLimit, INTRINSIC_GAS),
                        tx.getTransactionHash(),
                        tx.getNonce()
                    );
                }

                return new TxExecutionResult(tx.getTransactionHash(), tx.getNonce(), true, parsed.gasUsed(), parsed.returnDataHex(), null);
            }
        } catch (Exception e) {
            return TxExecutionResult.failed(e.getMessage(), gasLimit, tx.getTransactionHash(), tx.getNonce());
        }
    }
    

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////

    // HELPERS
    private long reserveGasUpfront(MutableAccount senderAccount, long gasCost) {
        senderAccount.decrementBalance(Wei.of(gasCost));
        return gasCost;
    }

    private TxExecutionResult settleGasAndReturn(MutableAccount senderAccount, Transaction tx, TxExecutionResult result, long prepaidGasCost, int leaderReplicaId) {
        long gasUsed = Math.min(result.gasUsed(), tx.getGasLimit());
        long chargedGasCost = Math.multiplyExact(gasUsed, tx.getGasPrice());
        long refund = prepaidGasCost - chargedGasCost;

        if (refund > 0) {
            senderAccount.incrementBalance(Wei.of(refund));
        }

        if (chargedGasCost > 0) {
            creditGasToLeader(leaderReplicaId, chargedGasCost);
        }

        senderAccount.incrementNonce();
        return result;
    }

    private void creditGasToLeader(int leaderReplicaId, long gasAmount) {
        if (leaderReplicaId < 0 || gasAmount <= 0) {
            return;
        }

        // We need to add + 1 because we have a mismatch on genesis
        String leaderAlias = "replica" + (leaderReplicaId + 1);
        MutableAccount leaderAccount = getAccount(AddressManager.deriveAddressFromAlias(leaderAlias));
        if (leaderAccount == null) {
            return;
        }

        leaderAccount.incrementBalance(Wei.of(gasAmount));
    }

    public long getBalance(String rawAddress) {
        MutableAccount account = getAccount(AddressManager.parseAddress(rawAddress));
        return account == null ? 0L : account.getBalance().toLong();
    }

    public long getNonce(String rawAddress) {
        MutableAccount account = getAccount(AddressManager.parseAddress(rawAddress));
        return account == null ? 0L : account.getNonce();
    }

    public SimpleWorld getWorld() {
        return world;
    }

    public Address getContractAddress() {
        return contractAddress;
    }

    private MutableAccount getAccount(Address address) {
        MutableAccount account = (MutableAccount) world.get(address);
        if (account == null) {
            return null;
        }
        return account;
    }

    private MutableAccount getOrCreateAccount(Address address) {
        MutableAccount account = getAccount(address);
        if (account != null) {
            return account;
        }

        world.createAccount(address, 0L, Wei.ZERO);
        return getAccount(address);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public record BlockExecutionResult(
        List<TxExecutionResult> txResults,
        long totalGasUsed,
        String worldStateHash,
        boolean success,
        String error
    ) {}

    public record TxExecutionResult(
        String txHash,
        Long nonce,
        boolean success,
        long gasUsed,
        String returnDataHex,
        String error
    ) {
        static TxExecutionResult failed(String error, long gasUsed, String txHash, Long nonce) {
            return new TxExecutionResult(txHash, nonce, false, gasUsed, null, error);
        }
    }

}
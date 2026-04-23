package com.depchain;

import com.depchain.blockchain.model.Transaction;
import com.depchain.blockchain.model.Block;
import com.depchain.state.EvmExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GenesisReader {
	private static final String GENESIS_PATH = "../config/genesis.json";

	public static Block readGenesisBlock() {
		JsonNode rootNode = readGenesisRoot();

		Block genesisBlock = new Block();
		genesisBlock.setSelfDigest(rootNode.path("block_hash").asText("0x0"));
		genesisBlock.setParentDigest(rootNode.path("previous_block_hash").asText(null));
		genesisBlock.setBlockGasLimit(rootNode.path("block_gas_limit").asLong(Block.MAX_BLOCK_GAS));

		List<Transaction> genesisTransactions = new ArrayList<>();
		JsonNode txs = rootNode.get("transactions");
		if (txs != null && txs.isArray()) {
			for (JsonNode node : txs) {
				Transaction tx = new Transaction();
				tx.setSender(node.path("sender").asText());
				tx.setDest(node.path("dest").isNull() ? null : node.path("dest").asText());
				tx.setCallData(node.path("call_data").asText(""));
				tx.setValue(node.path("value").asLong(0L));
				tx.setGasLimit(node.path("gas_limit").asLong(0L));
				tx.setGasPrice(node.path("gas_price").asLong(0L));
				tx.setNonce(node.path("nonce").asLong(0L));
				tx.setSignature(node.path("signature").asText(""));
				genesisTransactions.add(tx);
			}
		}

		genesisBlock.setTransactions(genesisTransactions);
		return genesisBlock;
	}

	public void initializeWorld(EvmExecutionService evmService) {
		if (evmService == null) {
			throw new IllegalArgumentException("EVM state is required");
		}

		JsonNode rootNode = readGenesisRoot();

		JsonNode accounts = rootNode.get("initial_accounts");
		if (accounts != null && accounts.isArray()) {
			for (JsonNode node : accounts) {
				String address = node.path("address").asText();
				long balance = node.path("balance").asLong(0L);
				long nonce = node.path("nonce").asLong(0L);
				evmService.initializeAccount(address, balance, nonce);
			}
		}

		Block genesisBlock = readGenesisBlock();
		if (genesisBlock.getTransactions() != null && !genesisBlock.getTransactions().isEmpty()) {
			// We make the leader -1, so no gas is payed in deployment
			EvmExecutionService.BlockExecutionResult result =
				evmService.executeBlock(genesisBlock.getTransactions(), genesisBlock.getBlockGasLimit(), -1);
			if (!result.success()) {
				throw new IllegalStateException("Genesis transactions failed: " + result.error());
			}
		}
	}

	private static JsonNode readGenesisRoot() {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.readTree(new File(GENESIS_PATH));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read genesis file: " + GENESIS_PATH, e);
		}
	}
}

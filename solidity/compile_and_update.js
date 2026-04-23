const fs = require('fs');
const solc = require('solc');

const contractCode = fs.readFileSync('solidity/contract.sol', 'utf8');

const input = {
  language: 'Solidity',
  sources: {
    'contract.sol': {
      content: contractCode
    }
  },
  settings: {
    outputSelection: {
      '*': {
        '*': ['evm.deployedBytecode'] 
      }
    }
  }
};

const output = JSON.parse(solc.compile(JSON.stringify(input)));
const contracts = output.contracts['contract.sol'];

// Find the first contract implicitly
const contractName = Object.keys(contracts)[0];
const bytecode = contracts[contractName].evm.deployedBytecode.object; // CHANGED FROM evm.bytecode.object

console.log(`Compiled ${contractName}, new deployed bytecode length: ${bytecode.length}`);

// Read genesis.json
const genesisStr = fs.readFileSync('config/genesis.json', 'utf8');
const genesis = JSON.parse(genesisStr);

// The first transaction in genesis is the contract deployment
genesis.transactions[0].call_data = bytecode;

fs.writeFileSync('config/genesis.json', JSON.stringify(genesis, null, 4));
console.log('Updated config/genesis.json with new DEPLOYED bytecode.');

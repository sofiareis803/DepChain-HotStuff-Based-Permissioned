package com.depchain.state;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.apache.tuweni.units.bigints.UInt256;

import java.math.BigInteger;

public class ISTCoinInitializer {
    private static final int TOTAL_SUPPLY_SLOT = 3;
    private static final int BALANCES_SLOT = 4;
    private static final int ALLOWANCES_SLOT = 5;
    private static final long INITIAL_SUPPLY = 10_000_000_000L;
    private static final long CLIENT_INITIAL_UNITS = 1_000_000L;

    public static void initializeIstCoinRuntimeState(MutableAccount contractAccount) {
        Address client1 = AddressManager.deriveAddressFromAlias("client1");
        Address client2 = AddressManager.deriveAddressFromAlias("client2");
        Address client3 = AddressManager.deriveAddressFromAlias("client3");

        setScalarStorage(contractAccount, TOTAL_SUPPLY_SLOT, INITIAL_SUPPLY - (CLIENT_INITIAL_UNITS * 3));

        setBalanceStorage(contractAccount, client1, CLIENT_INITIAL_UNITS);
        setBalanceStorage(contractAccount, client2, CLIENT_INITIAL_UNITS);
        setBalanceStorage(contractAccount, client3, CLIENT_INITIAL_UNITS);

        setAllowanceStorage(contractAccount, client1, client2, 0L);
        setAllowanceStorage(contractAccount, client1, client3, 0L);
        setAllowanceStorage(contractAccount, client2, client1, 0L);
        setAllowanceStorage(contractAccount, client2, client3, 0L);
        setAllowanceStorage(contractAccount, client3, client1, 0L);
        setAllowanceStorage(contractAccount, client3, client2, 0L);
    }

    private static void setScalarStorage(MutableAccount account, int slot, long value) {
        account.setStorageValue(UInt256.valueOf(slot), uint256FromLong(value));
    }

    private static void setBalanceStorage(MutableAccount account, Address owner, long value) {
        UInt256 slotKey = mappingSlot(owner, BALANCES_SLOT);
        account.setStorageValue(slotKey, uint256FromLong(value));
    }

    private static void setAllowanceStorage(MutableAccount account, Address owner, Address spender, long value) {
        UInt256 slotKey = nestedMappingSlot(owner, spender, ALLOWANCES_SLOT);
        account.setStorageValue(slotKey, uint256FromLong(value));
    }

    
    
    private static UInt256 mappingSlot(Address key, int mappingSlot) {
        Bytes32 paddedKey = Bytes32.leftPad(key);
        Bytes32 paddedSlot = Bytes32.leftPad(UInt256.valueOf(mappingSlot));
        Hash hash = Hash.hash(Bytes.concatenate(paddedKey, paddedSlot));
        return UInt256.fromHexString(hash.toHexString());
    }
    
    
    private static UInt256 nestedMappingSlot(Address outerKey, Address innerKey, int mappingSlot) {
        UInt256 outerSlot = mappingSlot(outerKey, mappingSlot);
        Bytes32 paddedInnerKey = Bytes32.leftPad(innerKey);
        Bytes32 paddedOuterSlot = Bytes32.leftPad(outerSlot);
        Hash hash = Hash.hash(Bytes.concatenate(paddedInnerKey, paddedOuterSlot));
        return UInt256.fromHexString(hash.toHexString());
    }
    
    
    private static UInt256 uint256FromLong(long value) {
        return UInt256.fromHexString("0x" + String.format("%064x", BigInteger.valueOf(value)));
    }

    //Debug deprecated

    public static void logIstTokenState(String phase, MutableAccount contractAccount) {
        Address client1 = AddressManager.deriveAddressFromAlias("client1");
        Address client2 = AddressManager.deriveAddressFromAlias("client2");
        Address client3 = AddressManager.deriveAddressFromAlias("client3");
    
        System.out.println(
            "[EVM] " + phase
            + " contract=" + contractAccount.getAddress().toHexString()
            + " totalSupplySlot=" + readStorage(contractAccount, UInt256.valueOf(TOTAL_SUPPLY_SLOT))
            + " client1=" + readStorage(contractAccount, mappingSlot(client1, BALANCES_SLOT))
            + " client2=" + readStorage(contractAccount, mappingSlot(client2, BALANCES_SLOT))
            + " client3=" + readStorage(contractAccount, mappingSlot(client3, BALANCES_SLOT))
        );
    }
    
    
    private static String readStorage(MutableAccount account, UInt256 slot) {
        UInt256 value = account.getStorageValue(slot);
        return value == null ? "null" : value.toHexString();
    }
}

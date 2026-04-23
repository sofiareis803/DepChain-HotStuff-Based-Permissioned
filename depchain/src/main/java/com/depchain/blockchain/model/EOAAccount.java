package com.depchain.blockchain.model;

import com.depchain.blockchain.model.Account;

public class EOAAccount extends Account {

    public EOAAccount(String address, long balance) {
        super(address, balance);
    }
}
package com.depchain.blockchain.model;

public class Account {
    private String address;
    private long balance;

    public Account(String address, long balance) {
        this.address = address;
        this.balance = balance;
    }

    public String getAddress() {
        return address;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
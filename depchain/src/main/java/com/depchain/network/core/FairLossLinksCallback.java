package com.depchain.network.core;

/**
 * Callback interface for passing messages UP to the Stubborn Links layer
 */
public interface FairLossLinksCallback {
    void flp2pDeliver(String src, String m);
}

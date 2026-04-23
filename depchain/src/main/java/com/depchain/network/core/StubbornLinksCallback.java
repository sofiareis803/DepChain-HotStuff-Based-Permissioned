package com.depchain.network.core;

/**
 * Callback interface for passing messages UP to the Authenticated Perfect Links layer
 */
public interface StubbornLinksCallback {
    void sp2pDeliver(String src, String m);
}

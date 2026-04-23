package com.depchain.network.core;

import com.depchain.network.envelope.Envelope;

/**
 * Callback interface for AuthenticatedPerfectLinks layer
 */
public interface AuthenticatedPerfectLinksCallback {
    void alp2pDeliver(String src, Envelope envelope);
}

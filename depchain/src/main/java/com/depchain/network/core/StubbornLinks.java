package com.depchain.network.core;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class StubbornLinks implements FairLossLinksCallback {
    private FairLossLinks flp2p;
    private StubbornLinksCallback callback;
    private ExecutorService executor;

    public StubbornLinks(FairLossLinks flp2p, StubbornLinksCallback callback) {
        this.callback = callback;
        this.flp2p = flp2p;
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * Sends a message m to the destination dest using the Fair Loss Links layer.
     * upon event <sp2pSend, dest, m> do
     * @param dest is in the format "IP:PORT"
     * @param m is the message to be sent
     */
    public void sp2pSend(String dest, String m) {
        executor.submit(() -> {
            // while (true) do
            // but if the thread is interrupted, it stops
            while (!Thread.currentThread().isInterrupted()) {
                // trigger <flp2pSend, dest, m>;
                flp2p.flp2pSend(dest, m);

                try {
                    Thread.sleep(1000); // 1 second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * upon event <flp2pDeliver, src, m> do
     * trigger <sp2pDeliver, src, m>;
     * @param src is in the format "IP:PORT"
     * @param m is the message that was received
     */
    public void flp2pDeliver(String src, String m) {
        // trigger <sp2pDeliver, src, m>;
        callback.sp2pDeliver(src, m);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

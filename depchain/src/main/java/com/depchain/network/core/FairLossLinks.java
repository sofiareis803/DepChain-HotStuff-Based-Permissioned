package com.depchain.network.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class FairLossLinks {
    private DatagramSocket socket;
    private FairLossLinksCallback callback;
    private volatile boolean running = true;

    public FairLossLinks(int port, FairLossLinksCallback callback) {
        this.callback = callback;
        try {
            this.socket = new DatagramSocket(port);
            listen();
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to bind UDP socket on port " + port, e);
        }
        
    }

   /**
    * Sends a message m to the destination dest using UDP.
    * @param dest is in the format "IP:PORT"
    * @param m is the message to be sent
    */
    public void flp2pSend(String dest, String m) {
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException("FairLossLinks socket is not available for send");
        }
        try {
            // dest is "IP:PORT"
            String[] parts = dest.split(":");
            InetAddress address = InetAddress.getByName(parts[0]);
            int port = Integer.parseInt(parts[1]);

            byte[] buffer = m.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
        } catch (SocketException e) {
            if (running) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Background thread that continuously listens for incoming UDP packets
     * and triggers the callback when a message is received.
     */
    private void listen() {
        Thread t = new Thread(() -> {
            while (running) {
                byte[] buffer = new byte[65536];
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String src = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    String message = new String(packet.getData(), 0, packet.getLength());
                    
                    // trigger <flp2pDeliver, src, m>; in sp2p (upper layer)
                    callback.flp2pDeliver(src, message);
                } catch (SocketException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                    break;
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

}

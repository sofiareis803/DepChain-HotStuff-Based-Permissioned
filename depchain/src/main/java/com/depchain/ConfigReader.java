package com.depchain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ConfigReader {
    private Map<Integer, InetSocketAddress> replicaAddresses = new HashMap<>();
    private Map<Integer, InetSocketAddress> clientAddresses = new HashMap<>();
    private Path configFilePath = Paths.get("../config/config.txt");

    public ConfigReader() {
        try (Scanner scanner = new Scanner(configFilePath)) {
            Map<Integer, InetSocketAddress> currentMap = new HashMap<>();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("Replicas")) {
                    currentMap = replicaAddresses;
                    continue;
                }
                if (line.equalsIgnoreCase("Clients")) {
                    currentMap = clientAddresses;
                    continue;
                }
                
                String[] nodeInfo = line.split(",");
                if (nodeInfo.length == 3) {
                    int nodeId = Integer.parseInt(nodeInfo[0].trim());
                    String ipAddress = nodeInfo[1].trim();
                    int port = Integer.parseInt(nodeInfo[2].trim());
                    currentMap.put(nodeId, new InetSocketAddress(ipAddress, port));
                }
            }
        } catch (IOException e) {
            System.err.println("It was not possible to read the file: " + e.getMessage());
        }
    }

    public Map<Integer, InetSocketAddress> getReplicaAddresses() {
        return replicaAddresses;
    }

    public Map<Integer, InetSocketAddress> getClientAddresses() {
        return clientAddresses;
    }

    public InetSocketAddress getReplicaAddress(int nodeId) {
        return replicaAddresses.get(nodeId);
    }

    public InetSocketAddress getClientAddress(int clientId) {
        return clientAddresses.get(clientId);
    }
}
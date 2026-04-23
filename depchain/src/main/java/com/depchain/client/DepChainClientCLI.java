package com.depchain.client;

import java.util.Scanner;
import com.depchain.ConfigReader;

public class DepChainClientCLI {
    public static void main(String[] args) {
        int clientId = Integer.parseInt(args[0]);
        ConfigReader reader = new ConfigReader();
        DepChainLib depChainLib = new DepChainLib(clientId, reader);
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== DepChain Client Stage 1 ===");
        System.out.println("Commands: transfer <to_client_id> <amount> <gas_price> <gas_limit> | transfer-from <from_client_id> <to_client_id> <amount> <gas_price> <gas_limit> | approve <spender_client_id> <amount> <gas_price> <gas_limit> | exit");

        while (true) {
            System.out.print("\n> ");
            System.out.print("Enter command: ");
            String input = scanner.nextLine().trim();

            if (input.equals("exit")) {
                System.out.println("Exiting...");
                scanner.close();
                System.exit(0);
            } 
            else if (input.startsWith("transfer ")) {
                String[] parts = input.split(" ");
                if (parts.length == 5) {
                    Integer toClientId = parseKnownClientId(parts[1], reader);
                    Long amount = parseNonNegativeLong(parts[2], "amount");
                    Long gasPrice = parsePositiveLong(parts[3], "gas price");
                    Long gasLimit = parsePositiveLong(parts[4], "gas limit");
                    if (toClientId == null || amount == null || gasPrice == null || gasLimit == null) {
                        continue;
                    }
                    boolean result = depChainLib.transfer(toClientId, amount, gasPrice, gasLimit); 

                    if (result) { 
                        System.out.println("Token transfer submitted."); 
                    }
                    else {
                        System.out.println("Token transfer failed.");
                    }
                }
            }
            else if (input.startsWith("transfer-from ")) {
                String[] parts = input.split(" ");
                if (parts.length == 6) {
                    Integer fromClientId = parseKnownClientId(parts[1], reader);
                    Integer toClientId = parseKnownClientId(parts[2], reader);
                    Long amount = parseNonNegativeLong(parts[3], "amount");
                    Long gasPrice = parsePositiveLong(parts[4], "gas price");
                    Long gasLimit = parsePositiveLong(parts[5], "gas limit");
                    if (fromClientId == null || toClientId == null || amount == null || gasPrice == null || gasLimit == null) {
                        continue;
                    }
                    boolean result = depChainLib.transferFrom(fromClientId, toClientId, amount, gasPrice, gasLimit);

                    if (result) {
                        System.out.println("Token transferFrom submitted.");
                    }
                    else {
                        System.out.println("Token transferFrom failed.");
                    }
                }
            }
            else if (input.startsWith("approve ")) {
                String[] parts = input.split(" ");
                if (parts.length == 5) {
                    Integer spenderClientId = parseKnownClientId(parts[1], reader);
                    Long amount = parseNonNegativeLong(parts[2], "amount");
                    Long gasPrice = parsePositiveLong(parts[3], "gas price");
                    Long gasLimit = parsePositiveLong(parts[4], "gas limit");
                    if (spenderClientId == null || amount == null || gasPrice == null || gasLimit == null) {
                        continue;
                    }
                    boolean result = depChainLib.approve(spenderClientId, amount, gasPrice, gasLimit);

                    if (result) {
                        System.out.println("Approve submitted.");
                    }
                    else {
                        System.out.println("Approve failed.");
                    }
                }
            }
            else if (input.startsWith("increase-allowance ")) {
                String[] parts = input.split(" ");
                if (parts.length == 5) {
                    Integer spenderClientId = parseKnownClientId(parts[1], reader);
                    Long amount = parseNonNegativeLong(parts[2], "amount");
                    Long gasPrice = parsePositiveLong(parts[3], "gas price");
                    Long gasLimit = parsePositiveLong(parts[4], "gas limit");
                    if (spenderClientId != null && amount != null && gasPrice != null && gasLimit != null) {
                        boolean result = depChainLib.increaseAllowance(spenderClientId, amount, gasPrice, gasLimit);
                        System.out.println(result ? "Increase allowance submitted." : "Failed.");
                    }
                }
            }
            else if (input.startsWith("decrease-allowance ")) {
                String[] parts = input.split(" ");
                if (parts.length == 5) {
                    Integer spenderClientId = parseKnownClientId(parts[1], reader);
                    Long amount = parseNonNegativeLong(parts[2], "amount");
                    Long gasPrice = parsePositiveLong(parts[3], "gas price");
                    Long gasLimit = parsePositiveLong(parts[4], "gas limit");
                    if (spenderClientId != null && amount != null && gasPrice != null && gasLimit != null) {
                        boolean result = depChainLib.decreaseAllowance(spenderClientId, amount, gasPrice, gasLimit);
                        System.out.println(result ? "Decrease allowance submitted." : "Failed.");
                    }
                }
            }
            else {
                System.out.println("Invalid command. Please try again.");
            }
        }
    }

    private static Integer parseKnownClientId(String value, ConfigReader reader) {
        Integer clientId = parsePositiveInt(value, "client id");
        if (clientId == null) {
            return null;
        }
        return clientId;
    }

    private static Integer parsePositiveInt(String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                System.out.println(fieldName + " must be positive.");
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.out.println("Invalid " + fieldName + ": " + value);
            return null;
        }
    }

    private static Long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                System.out.println(fieldName + " must be positive.");
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.out.println("Invalid " + fieldName + ": " + value);
            return null;
        }
    }

    private static Long parseNonNegativeLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                System.out.println(fieldName + " must be non-negative.");
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.out.println("Invalid " + fieldName + ": " + value);
            return null;
        }
    }
}   
package com.depchain.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public final class EvmTraceParser {
    private static final String ABI_BOOL_TRUE = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private static final String ABI_BOOL_FALSE = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private EvmTraceParser() {
        
    }

    public static ParseResult parseBooleanAndGasFromTracer(ByteArrayOutputStream traceOutput, long gasLimit, long intrinsicGas) {
        String rawTrace = traceOutput.toString(StandardCharsets.UTF_8);

        String[] lines = rawTrace.split("\\r?\\n");
        BigInteger evmGasUsed = sumGasFromTracer(lines);
        String boolReturn = findBooleanReturnFromTracer(lines);

        if (boolReturn == null) {
            return null;
        }

        BigInteger totalGasUsed = BigInteger.valueOf(intrinsicGas).add(evmGasUsed);
        BigInteger cappedGasUsed = totalGasUsed.min(BigInteger.valueOf(gasLimit));
        return new ParseResult(boolReturn, cappedGasUsed.longValueExact());
    }

    //Sum up all the gas instances that appear inside of the outupt produced after executing a transaction
    private static BigInteger sumGasFromTracer(String[] lines) {
        BigInteger evmGasUsed = BigInteger.ZERO;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            try {
                JsonObject candidate = JsonParser.parseString(line).getAsJsonObject();
                if (candidate.has("gasCost")) {
                    evmGasUsed = evmGasUsed.add(parseGasValue(candidate.get("gasCost").getAsString()));
                }
            } catch (RuntimeException ignored) {
                // Ignore non-JSON or malformed tracer lines.
            }
        }

        return evmGasUsed;
    }

    //Search for the expected output of either True or False in the outupt produced after executing a transaction
    private static String findBooleanReturnFromTracer(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }

            try {
                JsonObject candidate = JsonParser.parseString(line).getAsJsonObject();

                if (!(candidate.has("memory") && candidate.has("stack"))) {
                    continue;
                }

                JsonArray stack = candidate.get("stack").getAsJsonArray();
                if (stack == null || stack.size() < 2) {
                    continue;
                }

                int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
                int size = Integer.decode(stack.get(stack.size() - 2).getAsString());
                if (size != 32) {
                    continue;
                }

                String memory = candidate.get("memory").getAsString();
                int memoryHexStart = memory.startsWith("0x") ? 2 : 0;
                int start = memoryHexStart + offset * 2;
                int end = start + size * 2;
                if (start < 0 || end > memory.length()) {
                    continue;
                }

                String extracted = "0x" + memory.substring(start, end).toLowerCase();
                if (ABI_BOOL_TRUE.equals(extracted) || ABI_BOOL_FALSE.equals(extracted)) {
                    return extracted;
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed tracer lines.
            }
        }

        return null;
    }

    private static BigInteger parseGasValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigInteger.ZERO;
        }
        return raw.startsWith("0x") ? new BigInteger(raw.substring(2), 16) : new BigInteger(raw);
    }

    public record ParseResult(
        String returnDataHex,
        long gasUsed
    ) {}
}
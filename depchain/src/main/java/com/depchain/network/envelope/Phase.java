package com.depchain.network.envelope;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Phase {
    PREPARE,
    PRECOMMIT,
    COMMIT,
    DECIDE,
    NEW_VIEW;
    
    @JsonValue
    public String toValue() {
        return this.name();
    }

    @JsonCreator
    public static Phase forValue(String value) {
        return Phase.valueOf(value.toUpperCase());
    }

}  

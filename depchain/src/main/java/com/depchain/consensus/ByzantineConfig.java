package com.depchain.consensus;

import com.depchain.network.envelope.Phase;

public class ByzantineConfig {
    
    public enum ByzantineEnumType {
        NORMAL(null),  
        DELAY("delay"),             
        CONFLICT("conflict"),     
        SILENT("silent");            
        
        private final String flag;
        
        ByzantineEnumType(String flag) {
            this.flag = flag;
        }
        
        public static ByzantineEnumType fromString(String value) {
            for (ByzantineEnumType type : ByzantineEnumType.values()) {
                if (type.flag != null && type.flag.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return NORMAL;
        }
    }
    
    private ByzantineEnumType type;
    private Phase consensusPhases;
    private long delayMs;                       
    
    public ByzantineConfig() {
        this.type = ByzantineEnumType.NORMAL;
        this.consensusPhases = Phase.PREPARE; 
        this.delayMs = 2000;
    }
    
    public ByzantineConfig(ByzantineEnumType type) {
        this();
        this.type = type;
    }
    
    public void setPhase(String phase) {
        try {
            this.consensusPhases = Phase.forValue(phase);
        } catch (IllegalArgumentException e) {
            this.consensusPhases = Phase.PREPARE; 
        }
    }
    
    public ByzantineEnumType getType() {
        return type;
    }
    
    public Phase getConsensusPhases() {
        return consensusPhases;
    }
    
    public long getDelayMs() {
        return delayMs;
    }

    public boolean isEnabled() {
        return type != ByzantineEnumType.NORMAL;
    }
    
    public boolean shouldAffectPhase(String phase) {
        return consensusPhases == Phase.valueOf(phase.toUpperCase());
    }
}

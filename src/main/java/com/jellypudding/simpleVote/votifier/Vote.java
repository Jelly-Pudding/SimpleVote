package com.jellypudding.simpleVote.votifier;

/**
 * A vote from a voting service, following the Votifier protocol format
 */
public class Vote {
    /** The name of the player who voted */
    private final String username;
    
    /** The name of the service the vote came from */
    private final String serviceName;
    
    /** The IP address of the voter */
    private final String address;
    
    /** The timestamp when the vote was received */
    private final String timeStamp;
    
    /**
     * Creates a new vote with the specified parameters
     */
    public Vote(String username, String serviceName, String address, String timeStamp) {
        this.username = username;
        this.serviceName = serviceName;
        this.address = address;
        this.timeStamp = timeStamp;
    }
    
    /**
     * Parse a vote from a string following the Votifier format
     * Format: "VOTE\nserviceName\nusername\naddress\ntimestamp\n"
     */
    public static Vote fromVotifierString(String voteStr) {
        String[] lines = voteStr.split("\n");
        if (lines.length < 5) {
            throw new IllegalArgumentException("Invalid vote format");
        }
        
        String opcode = lines[0];
        if (!opcode.equals("VOTE")) {
            throw new IllegalArgumentException("Invalid opcode: " + opcode);
        }
        
        String serviceName = lines[1];
        String username = lines[2];
        String address = lines[3];
        String timeStamp = lines[4];
        
        return new Vote(username, serviceName, address, timeStamp);
    }
    
    /**
     * Get the name of the player who voted
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Get the name of the service the vote came from
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * Get the address of the voter
     */
    public String getAddress() {
        return address;
    }
    
    /**
     * Get the timestamp when the vote was received
     */
    public String getTimeStamp() {
        return timeStamp;
    }
    
    @Override
    public String toString() {
        return "Vote [username=" + username + ", serviceName=" + serviceName + 
               ", address=" + address + ", timeStamp=" + timeStamp + "]";
    }
}
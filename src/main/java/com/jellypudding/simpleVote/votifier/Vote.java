package com.jellypudding.simpleVote.votifier;

/**
 * A vote from a voting service following the Votifier protocol format
 *
 * @param username    The name of the player who voted
 * @param serviceName The name of the service the vote came from
 * @param address     The IP address of the voter
 * @param timeStamp   The timestamp when the vote was received
 */
public record Vote(String username, String serviceName, String address, String timeStamp) {
    /**
     * Creates a new vote with the specified parameters
     */
    public Vote {
    }

    /**
     * Parse a vote from a string following this format:
     * "VOTE\nserviceName\nusername\naddress\ntimestamp\n"
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
    @Override
    public String username() {
        return username;
    }

    /**
     * Get the name of the service the vote came from
     */
    @Override
    public String serviceName() {
        return serviceName;
    }

    /**
     * Get the address of the voter
     */
    @Override
    public String address() {
        return address;
    }

    /**
     * Get the timestamp when the vote was received
     */
    @Override
    public String timeStamp() {
        return timeStamp;
    }

    @Override
    public String toString() {
        return "Vote [username=" + username + ", serviceName=" + serviceName +
                ", address=" + address + ", timeStamp=" + timeStamp + "]";
    }
}
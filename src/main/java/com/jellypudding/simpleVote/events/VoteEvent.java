package com.jellypudding.simpleVote.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event that is called when a player votes for the server
 */
public class VoteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String playerName;
    private final String serviceName;
    private final String address;
    private final String timeStamp;

    /**
     * Creates a new vote event
     *
     * @param playerName The name of the player who voted
     * @param serviceName The name of the service they voted on
     * @param address The address of the service
     * @param timeStamp The timestamp of the vote
     */
    public VoteEvent(String playerName, String serviceName, String address, String timeStamp) {
        this.playerName = playerName;
        this.serviceName = serviceName;
        this.address = address;
        this.timeStamp = timeStamp;
    }

    /**
     * Gets the name of the player who voted
     *
     * @return The player name
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gets the name of the service that the vote came from
     *
     * @return The service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Gets the address of the service that the vote came from
     *
     * @return The service address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Gets the timestamp of the vote
     *
     * @return The timestamp
     */
    public String getTimeStamp() {
        return timeStamp;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
} 
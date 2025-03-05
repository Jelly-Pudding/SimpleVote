package com.jellypudding.simpleVote.votifier;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.events.VoteEvent;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Server that listens for votes following the Votifier protocol
 */
public class VotifierServer extends Thread {
    private final SimpleVote plugin;
    private final int port;
    private final boolean debug;
    private final RSAUtil rsaUtil;
    private ServerSocket serverSocket;
    private boolean running = true;
    private final ScheduledExecutorService voteProcessor;
    
    public VotifierServer(SimpleVote plugin, int port, boolean debug, RSAUtil rsaUtil) {
        this.plugin = plugin;
        this.port = port;
        this.debug = debug;
        this.rsaUtil = rsaUtil;
        this.voteProcessor = Executors.newScheduledThreadPool(1);

        setName("SimpleVote-VotifierServer");
    }

    @Override
    public void run() {
        try {
            // Open the server socket
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(port));
            
            plugin.getLogger().info("Vote listener started on port " + port);

            // Main connection acceptance loop
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(5000); // 5 second timeout

                    // Handle connection in a separate thread
                    voteProcessor.execute(() -> handleVote(socket));
                } catch (Exception e) {
                    if (running) {
                        plugin.getLogger().log(Level.WARNING, "Error accepting connection", e);
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                plugin.getLogger().log(Level.SEVERE, "Error starting vote listener", e);
            }
        }
    }
    
    /**
     * Handle an incoming vote connection
     */
    private void handleVote(Socket socket) {
        try {
            // Get client info for logging
            String hostAddress = socket.getInetAddress().getHostAddress();
            
            if (debug) {
                plugin.getLogger().info("Received connection from " + hostAddress);
            }
            
            // Send challenge back to the server
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write("VOTIFIER 1.9");
            writer.newLine();
            writer.flush();
            
            // Read the encrypted vote
            InputStream in = socket.getInputStream();
            
            // Votifier protocol sends 256 bytes (2048 bit RSA)
            byte[] block = new byte[256];
            int bytesRead = in.read(block, 0, block.length);
            
            if (bytesRead < 1) {
                if (debug) {
                    plugin.getLogger().warning("No data received from " + hostAddress);
                }
                return;
            }
            
            // Decrypt the vote
            String voteMsg = rsaUtil.decrypt(block);
            Vote vote = Vote.fromVotifierString(voteMsg);
            
            if (debug) {
                plugin.getLogger().info("Received vote: " + vote);
            }
            
            // Process the vote on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                VoteEvent voteEvent = new VoteEvent(
                        vote.getUsername(),
                        vote.getServiceName(),
                        vote.getAddress(),
                        vote.getTimeStamp()
                );
                
                // Call the event
                Bukkit.getPluginManager().callEvent(voteEvent);
                
                plugin.getLogger().info("Processed vote from " + vote.getUsername() + " (from " + vote.getServiceName() + ")");
            });
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing vote", e);
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    /**
     * Shutdown the server gracefully
     */
    public void shutdown() {
        running = false;
        
        // Shutdown the vote processor
        voteProcessor.shutdown();
        try {
            voteProcessor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Ignore
        }
        voteProcessor.shutdownNow();
        
        // Close the server socket
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        plugin.getLogger().info("Vote listener shut down");
    }
} 
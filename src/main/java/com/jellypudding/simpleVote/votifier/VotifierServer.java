package com.jellypudding.simpleVote.votifier;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.events.VoteEvent;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.UUID;
import java.util.Map;
import com.google.gson.Gson;

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
        final BufferedWriter[] writerRef = new BufferedWriter[1];
        try {
            // Get client info for logging
            String hostAddress = socket.getInetAddress().getHostAddress();
            
            if (debug) {
                plugin.getLogger().info("Received connection from " + hostAddress);
            }
            
            // Configure socket with a reasonable timeout
            socket.setSoTimeout(5000);
            
            // Set up input and output
            PushbackInputStream in = new PushbackInputStream(socket.getInputStream(), 512);
            writerRef[0] = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = writerRef[0];
            
            // Some services might send data without waiting for handshake
            boolean dataReceived = false;
            if (in.available() > 0) {
                if (debug) {
                    plugin.getLogger().info("Data already available before handshake");
                }
                dataReceived = true;
            } else {
                // Send handshake for both v1 and v2 protocols
                String challenge = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                writer.write("VOTIFIER 2 " + challenge);
                writer.newLine();
                writer.flush();
                
                if (debug) {
                    plugin.getLogger().info("Sent Votifier v2 handshake with challenge: " + challenge);
                }
                
                // Wait briefly to see if data arrives after handshake
                long startTime = System.currentTimeMillis();
                while (in.available() == 0 && System.currentTimeMillis() - startTime < 2000) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                dataReceived = in.available() > 0;
            }
            
            if (!dataReceived) {
                plugin.getLogger().warning("No data received from " + hostAddress);
                return;
            }
            
            // Check for votifier protocol version
            byte[] headerPeek = new byte[16];
            int headerRead = in.read(headerPeek);
            
            if (headerRead > 0) {
                // Check protocol version - look for v2 Magic number (0x733A = "s:")
                if (headerRead >= 2 && headerPeek[0] == 0x73 && headerPeek[1] == 0x3A) {
                    // This is a Votifier v2 payload (JSON format)
                    if (debug) {
                        plugin.getLogger().info("Detected Votifier v2 protocol (JSON format)");
                    }
                    
                    // Push the data back for full reading
                    in.unread(headerPeek, 0, headerRead);
                    
                    // Read the full JSON payload
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int b;
                    while ((b = in.read()) != -1) {
                        baos.write(b);
                    }
                    
                    byte[] fullData = baos.toByteArray();
                    String jsonString = new String(fullData, StandardCharsets.UTF_8);
                    
                    if (debug) {
                        plugin.getLogger().info("Received JSON: " + jsonString);
                    }
                    
                    // Skip the first two bytes (magic number)
                    if (fullData.length > 2 && fullData[0] == 0x73 && fullData[1] == 0x3A) {
                        jsonString = new String(fullData, 2, fullData.length - 2, StandardCharsets.UTF_8);
                    }
                    
                    // Try to find the valid JSON portion
                    int jsonStart = Math.max(0, jsonString.indexOf('{'));
                    int jsonEnd = jsonString.lastIndexOf('}');
                    
                    if (jsonStart > -1 && jsonEnd > jsonStart) {
                        jsonString = jsonString.substring(jsonStart, jsonEnd + 1);
                        if (debug) {
                            plugin.getLogger().info("Extracted JSON: " + jsonString);
                        }
                        
                        // Parse as JSON
                        try {
                            // Use a library like Gson to parse JSON properly
                            // For now, we'll use a simple approach to extract vote data
                            Map<String, Object> jsonMap = new Gson().fromJson(jsonString, Map.class);
                            
                            if (jsonMap.containsKey("payload")) {
                                String payload = (String) jsonMap.get("payload");
                                Map<String, Object> voteData = new Gson().fromJson(payload, Map.class);
                                
                                String username = (String) voteData.get("username");
                                String serviceName = (String) voteData.get("serviceName");
                                String address = (String) voteData.get("address");
                                
                                // Handle timestamp which may be a number or a string
                                String timestamp;
                                Object rawTimestamp = voteData.get("timestamp");
                                if (rawTimestamp instanceof Double) {
                                    // Convert numeric timestamp to string
                                    timestamp = String.valueOf(((Double) rawTimestamp).longValue());
                                } else if (rawTimestamp instanceof Long) {
                                    timestamp = String.valueOf(rawTimestamp);
                                } else {
                                    // Already a string or other format
                                    timestamp = String.valueOf(rawTimestamp);
                                }
                                
                                if (debug) {
                                    plugin.getLogger().info("Parsed v2 vote: username=" + username + 
                                        ", service=" + serviceName + ", address=" + address + 
                                        ", timestamp=" + timestamp);
                                }
                                
                                // Process the vote on the main thread
                                final String finalUsername = username;
                                final String finalServiceName = serviceName;
                                final String finalAddress = address;
                                final String finalTimestamp = timestamp;
                                
                                voteProcessor.submit(() -> {
                                    try {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            VoteEvent voteEvent = new VoteEvent(
                                                finalUsername,
                                                finalServiceName,
                                                finalAddress,
                                                finalTimestamp
                                            );
                                            
                                            // Call the event
                                            Bukkit.getPluginManager().callEvent(voteEvent);
                                            
                                            plugin.getLogger().info("Processed vote from " + finalUsername + " (from " + finalServiceName + ")");
                                        });
                                        
                                        // Send success response
                                        try {
                                            if (writerRef[0] != null && socket != null && !socket.isClosed()) {
                                                writerRef[0].write("{\"status\":\"ok\"}\r\n");
                                                writerRef[0].flush();
                                            }
                                        } catch (Exception e) {
                                            if (debug) {
                                                plugin.getLogger().warning("Failed to send OK response: " + e.getMessage());
                                            }
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("Error processing v2 vote event: " + e.getMessage());
                                        if (debug) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                                
                                return; // Successfully processed v2 vote
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error parsing v2 vote JSON: " + e.getMessage());
                            if (debug) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    // This is likely a v1 vote or other protocol
                    // Push back the data for normal processing
                    in.unread(headerPeek, 0, headerRead);
                }
            }
            
            // Continue with existing v1 vote processing
            // More flexible reading approach for reading the encrypted vote
            byte[] block = new byte[256];
            int bytesRead = 0;
            int totalRead = 0;
            int maxAttempts = 5;
            int attempts = 0;
            
            // Try to read with retries
            while (totalRead < block.length && attempts < maxAttempts) {
                try {
                    bytesRead = in.read(block, totalRead, block.length - totalRead);
                    if (bytesRead == -1) {
                        // End of stream
                        break;
                    } else if (bytesRead > 0) {
                        totalRead += bytesRead;
                    } else {
                        // Zero bytes read, wait a bit and retry
                        Thread.sleep(50);
                    }
                    attempts++;
                    
                    if (debug) {
                        plugin.getLogger().info("Read " + bytesRead + " bytes, total: " + totalRead + "/" + block.length);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (totalRead < 1) {
                if (debug) {
                    plugin.getLogger().warning("No vote data read from " + hostAddress);
                }
                return;
            }
            
            if (debug) {
                plugin.getLogger().info("Successfully read " + totalRead + " bytes of vote data");
            }
            
            // We might not get exactly 256 bytes - create an appropriate array
            byte[] actualData = totalRead == 256 ? block : java.util.Arrays.copyOf(block, totalRead);
            
            try {
                // Decrypt the vote
                String voteMsg = rsaUtil.decrypt(actualData);
                
                if (debug) {
                    plugin.getLogger().info("Decrypted vote message: " + voteMsg);
                }
                
                Vote vote = Vote.fromVotifierString(voteMsg);
                
                if (debug) {
                    plugin.getLogger().info("Received vote: " + vote);
                }
                
                // Process the vote on the main thread
                voteProcessor.submit(() -> {
                    try {
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
                        
                        // Send success response
                        try {
                            if (writerRef[0] != null && socket != null && !socket.isClosed()) {
                                writerRef[0].write("{\"status\":\"ok\"}\r\n");
                                writerRef[0].flush();
                            }
                        } catch (Exception e) {
                            if (debug) {
                                plugin.getLogger().warning("Failed to send OK response: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing vote event: " + e.getMessage());
                        if (debug) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to decrypt vote: " + e.getMessage());
                if (debug) {
                    plugin.getLogger().info("Data length: " + actualData.length + " bytes");
                    e.printStackTrace();
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            plugin.getLogger().warning("Socket timeout when reading vote: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing vote: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        } finally {
            try {
                if (writerRef[0] != null) {
                    writerRef[0].close();
                }
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
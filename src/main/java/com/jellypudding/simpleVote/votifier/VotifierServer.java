package com.jellypudding.simpleVote.votifier;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.events.VoteEvent;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
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
 * Supports both Votifier v1 (RSA encrypted) and v2 (JSON with HMAC) protocol
 */
public class VotifierServer extends Thread {
    private final SimpleVote plugin;
    private final int port;
    private final boolean debug;
    private final RSAUtil rsaUtil;
    private ServerSocket serverSocket;
    private boolean running = true;
    private final ScheduledExecutorService voteProcessor;
    
    // Expected 12-byte signature for PROXY protocol v2
    private static final byte[] PROXY_V2_SIGNATURE = new byte[] { 
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A 
    };
    
    private enum VoteProtocolVersion {
        V1, V2
    }
    
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
        try (socket) {
            // Get client info for logging
            String hostAddress = socket.getInetAddress().getHostAddress();

            if (debug) {
                plugin.getLogger().info("Received connection from " + hostAddress);
            }

            // Configure socket with a reasonable timeout
            socket.setSoTimeout(5000);

            // Set up input and output
            PushbackInputStream in = new PushbackInputStream(socket.getInputStream(), 512);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));

            // Generate challenge for v2 protocol
            String challenge = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            // Check for pre-existing data (some v1 implementations send vote immediately)
            int availableBytes = in.available();

            // Check if there's a full v1 vote block already (256 bytes)
            // As done in VotifierPlus - first check if a block is already waiting
            if (availableBytes >= 256) {
                if (debug) {
                    plugin.getLogger().info("Detected v1 vote packet before handshake (" + availableBytes + " bytes available)");
                }

                // Skip handshake for v1 vote blocks
                processProxyHeaders(in, socket);
                processV1Vote(in, writer, socket);
                return;
            }

            // Send appropriate handshake
            // Support v2 protocol as default

            String handshakeMessage = "VOTIFIER 2 " + challenge;

            writer.write(handshakeMessage);
            writer.newLine();
            writer.flush();

            if (debug) {
                plugin.getLogger().info("Sent handshake: " + handshakeMessage);
            }

            // Process any proxy headers if available
            if (socket.getInetAddress() != null) {
                processProxyHeaders(in, socket);
            }

            // Set socket timeout instead of busy-waiting
            int originalTimeout = socket.getSoTimeout();
            socket.setSoTimeout(2000); // 2 second timeout
            
            // Check if any data is available
            try {
                if (in.available() == 0) {
                    // Try to read at least one byte to trigger timeout if needed
                    in.mark(1);
                    int readByte = in.read();
                    if (readByte == -1) {
                        // End of stream reached
                        plugin.getLogger().warning("End of stream reached for " + hostAddress);
                        return;
                    }
                    in.reset();
                }
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning("No data received from " + hostAddress);
                return;
            } finally {
                // Restore original timeout
                socket.setSoTimeout(originalTimeout);
            }

            // Determine protocol version from the data format
            VoteProtocolVersion protocolVersion = detectProtocolVersion(in);

            if (debug) {
                plugin.getLogger().info("Detected vote protocol: " + protocolVersion);
            }

            // Process the vote according to its protocol
            if (protocolVersion == VoteProtocolVersion.V1) {
                processV1Vote(in, writer, socket);
            } else {
                processV2Vote(in, writer, challenge, socket);
            }

        } catch (java.net.SocketTimeoutException e) {
            plugin.getLogger().warning("Socket timeout when reading vote: " + e.getMessage());
            if (debug) {
                plugin.getLogger().log(Level.WARNING, "Socket timeout details", e);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing vote: " + e.getMessage());
            if (debug) {
                plugin.getLogger().log(Level.WARNING, "Error details", e);
            }
        }
        // Ignore
    }
    
    /**
     * Detect whether the incoming data is using v1 or v2 protocol
     */
    private VoteProtocolVersion detectProtocolVersion(PushbackInputStream in) throws Exception {
        // Read first two bytes to check
        byte[] header = new byte[2];
        int bytesRead = in.read(header);
        
        // Not enough data to determine
        if (bytesRead < 2) {
            in.unread(header, 0, bytesRead);
            return VoteProtocolVersion.V1; // Default to v1
        }
        
        // Check if it starts with '{' - likely JSON (v2)
        if ((char) header[0] == '{') {
            in.unread(header, 0, bytesRead);
            return VoteProtocolVersion.V2;
        }
        
        // Check for v2 protocol magic number (0x733A = "s:")
        if (header[0] == 0x73 && header[1] == 0x3A) {
            in.unread(header, 0, bytesRead);
            return VoteProtocolVersion.V2;
        }
        
        // Otherwise assume v1 protocol (RSA block)
        in.unread(header, 0, bytesRead);
        return VoteProtocolVersion.V1;
    }
    
    /**
     * Process a v1 protocol vote (RSA encrypted block)
     */
    private void processV1Vote(PushbackInputStream in, BufferedWriter writer, Socket socket) throws Exception {
        // For v1, we need to read 256 bytes of encrypted data
        byte[] block = new byte[256];
        int totalRead = 0;
        
        if (debug) {
            plugin.getLogger().info("Processing vote as v1 protocol");
        }
        
        // Read the full 256-byte block, similar to VotifierPlus implementation
        while (totalRead < block.length) {
            int remaining = block.length - totalRead;
            int bytesRead = in.read(block, totalRead, remaining);
            
            if (bytesRead == -1) {
                // End of stream
                if (debug) {
                    plugin.getLogger().info("Reached end-of-stream after " + totalRead + " bytes");
                }
                break;
            }
            
            totalRead += bytesRead;
            
            if (debug) {
                plugin.getLogger().info("Read " + bytesRead + " bytes; total: " + totalRead);
            }
        }
        
        if (totalRead == 0) {
            plugin.getLogger().warning("No v1 vote data received");
            return;
        }
        
        if (debug) {
            plugin.getLogger().info("Read " + totalRead + " bytes for v1 vote");
            // Add hex dump for diagnostics
            StringBuilder hexDump = new StringBuilder("First 32 bytes in hex: ");
            for (int i = 0; i < Math.min(totalRead, 32); i++) {
                hexDump.append(String.format("%02X ", block[i] & 0xFF));
            }
            plugin.getLogger().info(hexDump.toString());
        }
        
        // Only proceed if we got the full 256 bytes
        if (totalRead == 256) {
            try {
                // Decrypt the vote
                String voteMsg = rsaUtil.decrypt(block);
                
                if (debug) {
                    plugin.getLogger().info("Decrypted v1 vote: " + voteMsg);
                }
                
                Vote vote = Vote.fromVotifierString(voteMsg);
                
                if (debug) {
                    plugin.getLogger().info("Parsed v1 vote: " + vote);
                }
                
                // Process the vote on the main thread
                processVoteEvent(vote, writer, socket);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error decrypting v1 vote: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, "Error details", e);
                return;
            }
        } else {
            plugin.getLogger().warning("Incomplete v1 vote data received: " + totalRead + " bytes");
        }
    }
    
    /**
     * Process a v2 protocol vote (JSON with payload and signature)
     */
    private void processV2Vote(PushbackInputStream in, BufferedWriter writer, String challenge, Socket socket) throws Exception {
        // Read the full JSON data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            baos.write(b);
        }
        
        byte[] fullData = baos.toByteArray();
        String jsonString = new String(fullData, StandardCharsets.UTF_8);
        
        if (debug) {
            plugin.getLogger().info("Received v2 data: " + jsonString);
        }
        
        // Skip the magic number bytes if present (s:)
        if (fullData.length > 2 && fullData[0] == 0x73 && fullData[1] == 0x3A) {
            jsonString = new String(fullData, 2, fullData.length - 2, StandardCharsets.UTF_8);
        }
        
        // Try to find the valid JSON portion
        int jsonStart = Math.max(0, jsonString.indexOf('{'));
        int jsonEnd = jsonString.lastIndexOf('}');
        
        if (jsonEnd > jsonStart) {
            jsonString = jsonString.substring(jsonStart, jsonEnd + 1);
            
            if (debug) {
                plugin.getLogger().info("Extracted JSON: " + jsonString);
            }
            
            // Parse as JSON
            try {
                Map<String, Object> jsonMap = new Gson().fromJson(jsonString, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                
                if (jsonMap.containsKey("payload")) {
                    String payload = (String) jsonMap.get("payload");
                    Map<String, Object> voteData = new Gson().fromJson(payload, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                    
                    // Verify the challenge if available
                    if (voteData.containsKey("challenge")) {
                        String receivedChallenge = (String) voteData.get("challenge");
                        // Trim to remove any CR/LF characters
                        receivedChallenge = receivedChallenge.trim();
                        if (!challenge.equals(receivedChallenge)) {
                            plugin.getLogger().warning("Challenge verification failed for v2 vote.");
                            plugin.getLogger().warning("Expected: '" + challenge + "', received: '" + receivedChallenge + "'");
                            
                            // Continue anyway in case there are format issues
                        }
                    }
                    
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
                    
                    Vote vote = new Vote(username, serviceName, address, timestamp);
                    processVoteEvent(vote, writer, socket);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing V2 vote: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, "Error details", e);
                return;
            }
        } else {
            plugin.getLogger().warning("Invalid JSON format in v2 vote");
            throw new IllegalArgumentException("Invalid JSON in vote data");
        }
    }
    
    /**
     * Process the Vote event on the main thread and send a response
     */
    private void processVoteEvent(Vote vote, BufferedWriter writer, Socket socket) {
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
                    if (writer != null && socket != null && !socket.isClosed()) {
                        // Send a JSON success response for both v1 and v2
                        writer.write("{\"status\":\"ok\"}\r\n");
                        writer.flush();
                    }
                } catch (Exception e) {
                    if (debug) {
                        plugin.getLogger().warning("Failed to send OK response: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing vote event: " + e.getMessage());
                if (debug) {
                    plugin.getLogger().log(Level.WARNING, "Error details", e);
                }
            }
        });
    }
    
    /**
     * Processes and discards any proxy headers if present
     */
    private void processProxyHeaders(PushbackInputStream in, Socket socket) throws Exception {
        byte[] headerPeek = new byte[32];
        int bytesRead = in.read(headerPeek);
        
        if (bytesRead > 0) {
            String headerString = new String(headerPeek, 0, bytesRead, StandardCharsets.US_ASCII);
            
            // PROXY v1 protocol (text-based)
            if (headerString.startsWith("PROXY") && !headerString.contains("CONNECT")) {
                in.unread(headerPeek, 0, bytesRead);
                ByteArrayOutputStream headerLine = new ByteArrayOutputStream();
                byte[] buf = new byte[1];
                while (in.read(buf) != -1) {
                    headerLine.write(buf[0]);
                    if (buf[0] == '\n')
                        break;
                }
                String proxyHeader = headerLine.toString(StandardCharsets.US_ASCII).trim();
                if (debug) {
                    plugin.getLogger().info("Discarded PROXY v1 header: " + proxyHeader);
                }
            }
            // PROXY v2 protocol (binary)
            else if (bytesRead >= 12 && isProxyV2Header(headerPeek)) {
                int addrLength = ((headerPeek[14] & 0xFF) << 8) | (headerPeek[15] & 0xFF);
                int totalV2HeaderLength = 16 + addrLength;
                int remaining = totalV2HeaderLength - bytesRead;
                
                byte[] discard = new byte[remaining];
                int readRemaining = 0;
                while (readRemaining < remaining) {
                    int r = in.read(discard, readRemaining, remaining - readRemaining);
                    if (r == -1)
                        break;
                    readRemaining += r;
                }
                
                if (readRemaining != remaining) {
                    throw new Exception("Incomplete PROXY protocol v2 header");
                }
                
                if (debug) {
                    plugin.getLogger().info("Discarded PROXY v2 header (" + totalV2HeaderLength + " bytes)");
                }
            }
            // HTTP CONNECT tunneling
            else if (headerString.startsWith("CONNECT")) {
                in.unread(headerPeek, 0, bytesRead);
                String connectLine = readLine(in);
                
                if (debug) {
                    plugin.getLogger().info("Received CONNECT request: " + connectLine);
                }
                
                // Read and discard all headers
                String line;
                while (!(line = readLine(in)).isEmpty()) {
                    if (debug) {
                        plugin.getLogger().info("Discarding header: " + line);
                    }
                }
                
                // Send a 200 Connection Established response
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                writer.write("HTTP/1.1 200 Connection Established\r\n\r\n");
                writer.flush();
            }
            // No proxy protocol, push back the data
            else {
                in.unread(headerPeek, 0, bytesRead);
            }
        }
    }
    
    /**
     * Checks if the header matches the PROXY v2 protocol signature
     */
    private boolean isProxyV2Header(byte[] header) {
        if (header.length < PROXY_V2_SIGNATURE.length) {
            return false;
        }
        
        for (int i = 0; i < PROXY_V2_SIGNATURE.length; i++) {
            if (header[i] != PROXY_V2_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Reads a line from the input stream
     */
    private String readLine(PushbackInputStream in) throws Exception {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        int b;
        boolean seenCR = false;
        
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                seenCR = true;
                continue;
            }
            
            if (b == '\n') {
                break;
            }
            
            if (seenCR) {
                in.unread(b);
                break;
            }
            
            lineBuffer.write(b);
        }
        
        return lineBuffer.toString(StandardCharsets.US_ASCII).trim();
    }
    
    /**
     * Shutdown the server gracefully
     */
    public void shutdown() {
        running = false;
        
        // Shutdown the vote processor
        voteProcessor.shutdown();
        try {
            boolean terminated = voteProcessor.awaitTermination(2, TimeUnit.SECONDS);
            if (!terminated) {
                plugin.getLogger().warning("Vote processor did not terminate in time");
            }
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
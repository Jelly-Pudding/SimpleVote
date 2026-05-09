package com.jellypudding.simpleVote.votifier;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.events.VoteEvent;
import org.bukkit.Bukkit;

import com.google.gson.Gson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Server that listens for votes following the Votifier protocol
 * Supports both Votifier v1 (RSA encrypted) and v2 (JSON with HMAC) protocol
 */
public class VotifierServer extends Thread {
    private final SimpleVote plugin;
    private final int port;
    private final boolean debug;
    private final RSAUtil rsaUtil;
    private final String token;
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
    
    public VotifierServer(SimpleVote plugin, int port, boolean debug, RSAUtil rsaUtil, String token) {
        this.plugin = plugin;
        this.port = port;
        this.debug = debug;
        this.rsaUtil = rsaUtil;
        this.token = token;
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

                // Send response while the socket is still open, then fire the event
                try {
                    writer.write("{\"status\":\"ok\"}\r\n");
                    writer.flush();
                } catch (Exception e) {
                    if (debug) plugin.getLogger().warning("Failed to send OK response: " + e.getMessage());
                }
                processVoteEvent(vote);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error decrypting v1 vote: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, "Error details", e);
            }
        } else {
            plugin.getLogger().warning("Incomplete v1 vote data received: " + totalRead + " bytes");
        }
    }
    
    /**
     * Process a v2 protocol vote (JSON with payload and signature)
     */
    private void processV2Vote(PushbackInputStream in, BufferedWriter writer, String challenge, Socket socket) throws Exception {
        byte[] peek = new byte[2];
        int peekRead = in.read(peek);
        if (peekRead == 2 && peek[0] == 0x73 && peek[1] == 0x3A) {
            if (debug) plugin.getLogger().info("Skipped s: prefix in v2 packet");
        } else if (peekRead > 0) {
            in.unread(peek, 0, peekRead);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        boolean started = false;

        while ((b = in.read()) != -1) {
            baos.write(b);
            char c = (char) b;
            if (escape)             { escape = false; continue; }
            if (c == '\\' && inString) { escape = true;  continue; }
            if (c == '"')           { inString = !inString; continue; }
            if (!inString) {
                if (c == '{')      { depth++; started = true; }
                else if (c == '}' && --depth == 0 && started) { break; }
            }
        }

        String jsonString = baos.toString(StandardCharsets.UTF_8);

        if (jsonString.isEmpty()) {
            plugin.getLogger().warning("[V2] Received empty packet — nothing to parse");
            return;
        }

        // Some implementations prefix the JSON with a length byte or other header bytes.
        // Strip anything before the opening '{'.
        int jsonStart = jsonString.indexOf('{');
        if (jsonStart < 0) {
            plugin.getLogger().warning("[V2] No JSON object found in received data ("
                    + jsonString.length() + " bytes): " + jsonString.substring(0, Math.min(jsonString.length(), 100)));
            return;
        }
        if (jsonStart > 0) {
            if (debug) plugin.getLogger().info("V2: stripped " + jsonStart + " prefix byte(s) before JSON");
            jsonString = jsonString.substring(jsonStart);
        }

        if (debug) {
            plugin.getLogger().info("V2 raw JSON (" + jsonString.length() + " chars): "
                    + jsonString.substring(0, Math.min(jsonString.length(), 300)));
        }

        // Parse outer JSON envelope
        Map<String, Object> jsonMap;
        try {
            jsonMap = new Gson().fromJson(jsonString, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            plugin.getLogger().warning("V2 vote rejected: could not parse outer JSON - " + e.getMessage());
            if (debug) plugin.getLogger().warning("V2 raw string was: " + jsonString);
            return;
        }

        if (!jsonMap.containsKey("payload")) {
            plugin.getLogger().warning("V2 vote rejected: no 'payload' field. Keys received: " + jsonMap.keySet());
            return;
        }

        String payload = (String) jsonMap.get("payload");
        String signature = (String) jsonMap.get("signature");

        if (signature == null) {
            plugin.getLogger().warning("V2 vote rejected: missing signature field");
            return;
        }

        // Verify HMAC-SHA256 signature
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] received = Base64.getDecoder().decode(signature);
            if (!MessageDigest.isEqual(expected, received)) {
                plugin.getLogger().warning("V2 vote rejected: HMAC mismatch — check the token in config.yml matches the voting site.");
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("V2 vote rejected: HMAC error — " + e.getMessage());
            return;
        }

        // Payload is either base64-encoded JSON (standard NuVotifier v2)
        // or a plain JSON string (used by some sites). Try base64 first.
        String payloadJson;
        try {
            payloadJson = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            payloadJson = payload;
        }

        Map<String, Object> voteData;
        try {
            voteData = new Gson().fromJson(payloadJson, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            plugin.getLogger().warning("V2 vote rejected: could not parse payload JSON — " + e.getMessage());
            if (debug) plugin.getLogger().warning("V2 payload was: " + payloadJson);
            return;
        }

        // Verify the challenge
        if (voteData.containsKey("challenge")) {
            String receivedChallenge = ((String) voteData.get("challenge")).trim();
            if (!challenge.equals(receivedChallenge)) {
                plugin.getLogger().warning("V2 vote rejected: challenge mismatch");
                return;
            }
        }

        String username = (String) voteData.get("username");
        String serviceName = (String) voteData.get("serviceName");
        String address = (String) voteData.get("address");

        Object rawTimestamp = voteData.get("timestamp");
        String timestamp;
        if (rawTimestamp instanceof Double) {
            timestamp = String.valueOf(((Double) rawTimestamp).longValue());
        } else if (rawTimestamp instanceof Long) {
            timestamp = String.valueOf(rawTimestamp);
        } else {
            timestamp = String.valueOf(rawTimestamp);
        }

        if (debug) {
            plugin.getLogger().info("V2 vote parsed: username=" + username + ", service=" + serviceName);
        }

        Vote vote = new Vote(username, serviceName, address, timestamp);

        try {
            writer.write("{\"status\":\"ok\"}\r\n");
            writer.flush();
        } catch (Exception e) {
            if (debug) plugin.getLogger().warning("V2: failed to send ok response — " + e.getMessage());
        }
        processVoteEvent(vote);
    }
    
    /**
     * Fire the VoteEvent on the main thread.
     * The socket response must be sent by the caller before invoking this.
     */
    private void processVoteEvent(Vote vote) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            VoteEvent voteEvent = new VoteEvent(
                vote.username(),
                vote.serviceName(),
                vote.address(),
                vote.timeStamp()
            );
            Bukkit.getPluginManager().callEvent(voteEvent);
            plugin.getLogger().info("Processed vote from " + vote.username() + " (from " + vote.serviceName() + ")");
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
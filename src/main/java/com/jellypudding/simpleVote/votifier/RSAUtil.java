package com.jellypudding.simpleVote.votifier;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Utility class for RSA operations used by the Votifier protocol
 */
public class RSAUtil {
    private final Logger logger;
    private KeyPair keyPair;
    private boolean debug = false;
    
    public RSAUtil(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Initializes the RSA keys, either by loading existing keys or generating new ones
     * 
     * @param directory The directory to store keys in
     * @return True if keys were initialized successfully
     */
    public boolean initialize(File directory) {
        File publicKeyFile = new File(directory, "rsa/public.key");
        File privateKeyFile = new File(directory, "rsa/private.key");
        
        // Ensure directory exists
        File rsaDir = new File(directory, "rsa");
        if (!rsaDir.exists()) {
            rsaDir.mkdirs();
        }
        
        // Try to load existing keys
        if (publicKeyFile.exists() && privateKeyFile.exists()) {
            try {
                loadKeys(publicKeyFile, privateKeyFile);
                logger.info("Loaded RSA keys successfully");
                return true;
            } catch (Exception e) {
                logger.warning("Failed to load RSA keys: " + e.getMessage());
                logger.warning("Generating new RSA keys...");
            }
        }
        
        // Generate new keys if needed
        try {
            generateKeys(publicKeyFile, privateKeyFile);
            logger.info("Generated new RSA key pair successfully");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to generate RSA keys: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Load RSA keys from files
     */
    private void loadKeys(File publicKeyFile, File privateKeyFile) throws Exception {
        byte[] encodedPublicKey = Files.readAllBytes(publicKeyFile.toPath());
        byte[] encodedPrivateKey = Files.readAllBytes(privateKeyFile.toPath());
        
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedPublicKey);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
        
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        
        keyPair = new KeyPair(publicKey, privateKey);
    }
    
    /**
     * Generate new RSA keys and save them to files
     */
    private void generateKeys(File publicKeyFile, File privateKeyFile) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
        
        // Save public key
        try (FileOutputStream out = new FileOutputStream(publicKeyFile)) {
            out.write(keyPair.getPublic().getEncoded());
        }
        
        // Save private key
        try (FileOutputStream out = new FileOutputStream(privateKeyFile)) {
            out.write(keyPair.getPrivate().getEncoded());
        }
    }
    
    /**
     * Decrypt a message using the RSA private key
     * 
     * @param data The encrypted data
     * @return The decrypted message
     */
    public String decrypt(byte[] data) throws Exception {
        try {
            if (debug) {
                logger.info("Decrypting " + data.length + " bytes using RSA");
            }
            
            // Use a simple, compatible approach - just "RSA" with default options
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] decryptedBytes = cipher.doFinal(data);
            
            String result = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            // Validate that it looks like a vote
            if (result.startsWith("VOTE")) {
                if (debug) {
                    logger.info("Successfully decrypted vote data");
                }
                return result;
            } else {
                throw new Exception("Decrypted data doesn't start with VOTE: " + result);
            }
        } catch (Exception e) {
            logger.severe("Failed to decrypt vote data: " + e.getMessage());
            
            // Log data in hex format for debugging
            StringBuilder hexDump = new StringBuilder();
            hexDump.append("Hex dump of data (").append(data.length).append(" bytes): ");
            for (int i = 0; i < Math.min(data.length, 64); i++) {
                hexDump.append(String.format("%02X ", data[i] & 0xFF));
            }
            if (data.length > 64) {
                hexDump.append("...");
            }
            logger.info(hexDump.toString());
            
            throw e;
        }
    }
    
    /**
     * Enable/disable debug logging
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    /**
     * Get the public key as a base64 encoded string for sharing with voting sites
     * 
     * @return The public key in base64
     */
    public String getPublicKeyBase64() {
        if (keyPair == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
    
    /**
     * Get the public key as a formatted string for display
     * 
     * @return Formatted public key
     */
    public String getFormattedPublicKey() {
        if (keyPair == null) {
            return "RSA keys not initialized";
        }
        
        String base64Key = getPublicKeyBase64();
        StringBuilder builder = new StringBuilder();
        
        builder.append("-----BEGIN PUBLIC KEY-----\n");
        
        // Insert line breaks every 64 characters
        for (int i = 0; i < base64Key.length(); i += 64) {
            int endIndex = Math.min(i + 64, base64Key.length());
            builder.append(base64Key.substring(i, endIndex)).append("\n");
        }
        
        builder.append("-----END PUBLIC KEY-----");
        return builder.toString();
    }
    
    /**
     * Get the public key formatted specifically for v1 protocol (Votifier v1 tester)
     * Some v1 testers/clients expect the key with different formatting
     * 
     * @return Votifier v1 compatible public key format
     */
    public String getV1FormattedPublicKey() {
        if (keyPair == null) {
            return "RSA keys not initialized";
        }
        
        String base64Key = getPublicKeyBase64();
        // Some v1 clients expect the key without line breaks
        return base64Key;
    }
} 
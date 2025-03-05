package com.jellypudding.simpleVote.votifier;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
            // Log data length for debugging
            logger.info("Attempting to decrypt " + data.length + " bytes of data");
            
            // Configure cipher for RSA/ECB/PKCS1Padding - the most common format
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] decryptedBytes = cipher.doFinal(data);
            
            String result = new String(decryptedBytes, StandardCharsets.UTF_8);
            logger.info("Successfully decrypted data to: " + result);
            return result;
        } catch (Exception e) {
            logger.warning("Failed to decrypt with PKCS1Padding: " + e.getMessage());
            
            try {
                // Fall back to just "RSA" which uses default padding
                logger.info("Trying fallback with default RSA padding");
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
                byte[] decryptedBytes = cipher.doFinal(data);
                
                String result = new String(decryptedBytes, StandardCharsets.UTF_8);
                logger.info("Successfully decrypted data with fallback method: " + result);
                return result;
            } catch (Exception e2) {
                logger.severe("All decryption attempts failed!");
                
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
                
                throw new Exception("Failed to decrypt data: " + e.getMessage(), e);
            }
        }
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
} 
package model;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class User {
    private String username;
    private String role;
    private boolean active;
    private LocalDateTime lastLogin;
    private String passwordHash;
    private int failedAttempts;
    private LocalDateTime lockedUntil;

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCKOUT_MINUTES = 5;

    public User(String username, String role, String password) {
        this.username = username;
        this.role = role;
        this.active = true;
        this.lastLogin = null;
        this.passwordHash = hashPassword(password);
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    // Kept for backward compatibility (no-password constructor)
    public User(String username, String role) {
        this(username, role, username + "_default");
    }

    // --- Password & Authentication ---

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexStr = new StringBuilder();
            for (byte b : hash) hexStr.append(String.format("%02x", b));
            return hexStr.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public boolean authenticate(String password) {
        if (isLocked()) return false;
        boolean correct = hashPassword(password).equals(this.passwordHash);
        if (!correct) {
            failedAttempts++;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            }
        } else {
            failedAttempts = 0;
            lockedUntil = null;
            lastLogin = LocalDateTime.now();
        }
        return correct;
    }

    public boolean isLocked() {
        if (lockedUntil == null) return false;
        if (LocalDateTime.now().isAfter(lockedUntil)) {
            lockedUntil = null;  // Auto-unlock after timeout
            failedAttempts = 0;
            return false;
        }
        return true;
    }

    public void changePassword(String newPassword) {
        this.passwordHash = hashPassword(newPassword);
    }

    // --- Getters & Setters ---

    public String getUsername()            { return username; }
    public String getRole()                { return role; }
    public boolean isActive()              { return active; }
    public LocalDateTime getLastLogin()    { return lastLogin; }
    public int getFailedAttempts()         { return failedAttempts; }
    public LocalDateTime getLockedUntil()  { return lockedUntil; }

    public void setActive(boolean active)             { this.active = active; }
    public void setRole(String role)                  { this.role = role; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    public void resetFailedAttempts()                 { this.failedAttempts = 0; }

    // --- Serialization (for UserStore persistence) ---

    /** Format: username|role|passwordHash|active */
    public String serialize() {
        return username + "|" + role + "|" + passwordHash + "|" + active;
    }

    /** Reconstruct a User from a serialized line (skips re-hashing). */
    public static User deserialize(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) return null;
        User u = new User(parts[0], parts[1], "__placeholder__");
        u.passwordHash = parts[2];
        u.active = Boolean.parseBoolean(parts[3]);
        return u;
    }

    public String getPasswordHash() { return passwordHash; }

    @Override
    public String toString() {
        String status = isLocked() ? " [LOCKED]" : (active ? "" : " [INACTIVE]");
        return username + " (" + role + ")" + status;
    }
}

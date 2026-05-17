package model;

import java.time.LocalDateTime;
import java.util.*;

public class FileResource {
    private String name;
    private String path;
    private String owner;
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
    private long sizeBytes;

    // Per-user permission map: username -> set of granted permissions ('r','w','x')
    private Map<String, Set<Character>> userPermissions;

    // Default permissions for non-owners with no explicit entry
    // e.g., "r" means any authenticated user can read by default
    private Set<Character> defaultPermissions;

    public FileResource(String name, String path, String owner) {
        this.name = name;
        this.path = path;
        this.owner = owner;
        this.createdAt = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.sizeBytes = 0;
        this.userPermissions = new HashMap<>();
        this.defaultPermissions = new HashSet<>();
    }

    // --- Permission Management ---

    public void grantPermission(String username, char permission) {
        userPermissions
            .computeIfAbsent(username, k -> new HashSet<>())
            .add(permission);
    }

    public void revokePermission(String username, char permission) {
        Set<Character> perms = userPermissions.get(username);
        if (perms != null) {
            perms.remove(permission);
            if (perms.isEmpty()) userPermissions.remove(username);
        }
    }

    public void revokeAllPermissions(String username) {
        userPermissions.remove(username);
    }

    public void setDefaultPermission(char permission, boolean grant) {
        if (grant) defaultPermissions.add(permission);
        else        defaultPermissions.remove(permission);
    }

    /**
     * Check if a user has a specific permission.
     * Owner always has full access.
     * ADMIN role is checked in AccessControlService, not here.
     */
    public boolean hasPermission(String username, char permission) {
        if (owner.equals(username)) return true;
        Set<Character> explicit = userPermissions.get(username);
        if (explicit != null) return explicit.contains(permission);
        return defaultPermissions.contains(permission);
    }

    /** Returns a human-readable rwx string for a given user. */
    public String getPermissionString(String username) {
        char r = hasPermission(username, 'r') ? 'r' : '-';
        char w = hasPermission(username, 'w') ? 'w' : '-';
        char x = hasPermission(username, 'x') ? 'x' : '-';
        return String.valueOf(new char[]{r, w, x});
    }

    /** Returns all users who have any explicit permissions on this file. */
    public Set<String> getAllowedUsers() {
        return Collections.unmodifiableSet(userPermissions.keySet());
    }

    // --- Metadata ---

    public void recordWrite() {
        this.lastModified = LocalDateTime.now();
    }

    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    // --- Getters ---

    public String getName()               { return name; }
    public String getPath()               { return path; }
    public String getOwner()              { return owner; }
    public String getFileName()           { return path + name; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public LocalDateTime getLastModified(){ return lastModified; }
    public long getSizeBytes()            { return sizeBytes; }

    @Override
    public String toString() {
        return String.format("%s [owner:%s] [default:%s]", name, owner,
            defaultPermissions.isEmpty() ? "---" : defaultPermissions);
    }
}

package service;

import model.User;
import model.FileResource;
import java.util.*;

/**
 * AccessControlService — central authority for all permission decisions.
 *
 * Role hierarchy (highest to lowest):
 *   ADMIN > MODERATOR > USER > GUEST
 *
 * Rules:
 *  - ADMIN always has full access to every file.
 *  - MODERATOR can read all files, but write/execute only if explicitly granted.
 *  - USER and GUEST rely entirely on per-file explicit grants.
 *  - Inactive or locked users are always denied.
 */
public class AccessControlService {

    // Role rank: higher = more privileged
    private static final Map<String, Integer> ROLE_RANK = new HashMap<>();
    static {
        ROLE_RANK.put("ADMIN",     100);
        ROLE_RANK.put("MODERATOR",  50);
        ROLE_RANK.put("USER",       10);
        ROLE_RANK.put("GUEST",       1);
    }

    private final Map<String, User> users;

    public AccessControlService() {
        this.users = new LinkedHashMap<>();
        // Load persisted users; fall back to defaults if none saved
        Map<String, User> stored = UserStore.load();
        if (!stored.isEmpty()) {
            users.putAll(stored);
        } else {
            initDefaultUsers();
            UserStore.save(users.values());
        }
    }

    private void initDefaultUsers() {
        // In production these passwords would be set during first-run setup
        users.put("admin",  new User("admin",  "ADMIN",     "Admin@123"));
        users.put("user1",  new User("user1",  "USER",      "User1@123"));
        users.put("user2",  new User("user2",  "USER",      "User2@123"));
        users.put("guest",  new User("guest",  "GUEST",     "Guest@123"));
    }

    // --- User Lifecycle ---

    public boolean createUser(String username, String role, String password) {
        if (users.containsKey(username)) return false;
        if (!ROLE_RANK.containsKey(role.toUpperCase())) return false;
        users.put(username, new User(username, role.toUpperCase(), password));
        UserStore.save(users.values());
        return true;
    }

    public boolean deleteUser(String username) {
        if ("admin".equals(username)) return false;  // protect root admin
        boolean removed = users.remove(username) != null;
        if (removed) UserStore.save(users.values());
        return removed;
    }

    public boolean deactivateUser(String username) {
        User u = users.get(username);
        if (u == null) return false;
        u.setActive(false);
        return true;
    }

    public boolean changeRole(String username, String newRole, User requestingUser) {
        if (!isAdmin(requestingUser)) return false;
        User target = users.get(username);
        if (target == null) return false;
        target.setRole(newRole.toUpperCase());
        return true;
    }

    // --- Authentication ---

    /**
     * Authenticate a user by username + password.
     * Records the attempt in the user's failed-attempt counter.
     * Returns the User object on success, null on failure.
     */
    public User authenticate(String username, String password) {
        User user = users.get(username);
        if (user == null) return null;
        if (!user.isActive()) return null;
        return user.authenticate(password) ? user : null;
    }

    // --- Permission Checks ---

    public boolean canRead(FileResource file, User user) {
        return checkPermission(file, user, 'r');
    }

    public boolean canWrite(FileResource file, User user) {
        return checkPermission(file, user, 'w');
    }

    public boolean canExecute(FileResource file, User user) {
        return checkPermission(file, user, 'x');
    }

    public boolean checkAccess(User user, FileResource file, char permission) {
        return switch (permission) {
            case 'r' -> canRead(file, user);
            case 'w' -> canWrite(file, user);
            case 'x' -> canExecute(file, user);
            default -> false;
        };
    }

    private boolean checkPermission(FileResource file, User user, char perm) {
        // Inactive or locked users always denied
        if (!user.isActive() || user.isLocked()) return false;

        // ADMIN bypasses everything
        if ("ADMIN".equals(user.getRole())) return true;

        // MODERATOR can read everything without explicit grant
        if ("MODERATOR".equals(user.getRole()) && perm == 'r') return true;

        return file.hasPermission(user.getUsername(), perm);
    }

    // --- Grant / Revoke (admin only) ---

    public boolean grantPermission(FileResource file, String targetUsername,
                                   char perm, User requestingUser) {
        if (!canManagePermissions(file, requestingUser)) return false;
        file.grantPermission(targetUsername, perm);
        return true;
    }

    public boolean revokePermission(FileResource file, String targetUsername,
                                    char perm, User requestingUser) {
        if (!canManagePermissions(file, requestingUser)) return false;
        file.revokePermission(targetUsername, perm);
        return true;
    }

    /** Only admins or the file owner can manage permissions. */
    private boolean canManagePermissions(FileResource file, User requester) {
        return isAdmin(requester) || file.getOwner().equals(requester.getUsername());
    }

    // --- Helpers ---

    public boolean isAdmin(User user) {
        return "ADMIN".equals(user.getRole());
    }

    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public List<User> getAllUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users.values()));
    }

    public int getRoleRank(String role) {
        return ROLE_RANK.getOrDefault(role.toUpperCase(), 0);
    }
}

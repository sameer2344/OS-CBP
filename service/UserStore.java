package service;

import model.User;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * UserStore — persists registered users to users.dat so accounts survive restarts.
 * Format per line: username|role|passwordHash|active
 */
public class UserStore {

    private static final String DATA_FILE = "users.dat";

    /** Load all users from disk. Returns empty map if file doesn't exist. */
    public static Map<String, User> load() {
        Map<String, User> map = new LinkedHashMap<>();
        Path path = Paths.get(DATA_FILE);
        if (!Files.exists(path)) return map;
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                User u = User.deserialize(line);
                if (u != null) map.put(u.getUsername(), u);
            }
        } catch (IOException e) {
            System.err.println("[UserStore] Failed to load: " + e.getMessage());
        }
        return map;
    }

    /** Save all users to disk (overwrites file). */
    public static void save(Collection<User> users) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE, false))) {
            for (User u : users) {
                writer.write(u.serialize());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("[UserStore] Failed to save: " + e.getMessage());
        }
    }
}

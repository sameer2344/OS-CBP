package service;

import model.AuditLog;
import model.AuditLog.Severity;
import java.util.*;
import java.time.LocalDateTime;
import java.io.*;
import java.util.stream.Collectors;

public class AuditService {

    private List<AuditLog> logs = new ArrayList<>();
    private static final String AUDIT_LOG_FILE = "audit.log";

    public AuditService() {
        loadFromFile();
    }

    // --- Logging Methods ---

    public void log(AuditLog log) {
        logs.add(log);
        persistToFile(log);
    }

    public void log(String username, String action, String resource, boolean success) {
        AuditLog log = new AuditLog(username, action, resource, success);
        logs.add(log);
        persistToFile(log);
    }

    public void log(String username, String action, String resource, boolean success, 
                    Severity severity, String details) {
        AuditLog log = new AuditLog(username, action, resource, success, severity, details);
        logs.add(log);
        persistToFile(log);
    }

    // --- Query Methods ---

    public List<AuditLog> getLogs() {
        return new ArrayList<>(logs);
    }

    public List<AuditLog> getRecentLogs(int count) {
        int start = Math.max(0, logs.size() - count);
        return new ArrayList<>(logs.subList(start, logs.size()));
    }

    public int countRecentDenials(String username, int windowSeconds) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(windowSeconds);
        return (int) logs.stream()
            .filter(log -> log.getUsername().equals(username) && !log.isSuccess() 
                        && log.getTimestamp().isAfter(cutoff))
            .count();
    }

    public List<AuditLog> getLogsByUser(String username) {
        return logs.stream()
            .filter(log -> log.getUsername().equals(username))
            .collect(Collectors.toList());
    }

    // --- Export and Management ---

    public boolean exportToCsv(String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("Timestamp,User,Action,Resource,Status,Severity");
            for (AuditLog log : logs) {
                pw.printf("%s,%s,%s,%s,%s,%s\n",
                    log.getTimestamp().toString().replace("T", " "),
                    log.getUsername(),
                    log.getAction(),
                    log.getResource(),
                    log.isSuccess() ? "ALLOWED" : "DENIED",
                    log.getSeverity());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void clearLogs() {
        logs.clear();
        try (PrintWriter pw = new PrintWriter(new FileWriter(AUDIT_LOG_FILE))) {
            // Clear the file
        } catch (Exception ignored) {}
    }

    // --- Persistence ---

    private void persistToFile(AuditLog log) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(AUDIT_LOG_FILE, true))) {
            pw.printf("[%s] [%-8s] %-10s | %-15s | %-20s | %s\n",
                log.getTimestamp().toString().replace("T", " "),
                log.getSeverity(),
                log.getUsername(),
                log.getAction(),
                log.getResource(),
                log.isSuccess() ? "ALLOWED" : "DENIED");
        } catch (Exception ignored) {}
    }

    private void loadFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(AUDIT_LOG_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                // File is for reference only, logs are kept in memory
            }
        } catch (Exception ignored) {}
    }
}
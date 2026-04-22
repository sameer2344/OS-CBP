package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuditLog {
    public enum Severity { INFO, WARNING, CRITICAL }

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String username;
    private final String action;
    private final String resource;
    private final LocalDateTime timestamp;
    private final boolean success;
    private final Severity severity;
    private final String details;  // extra context (e.g., "from IP 127.0.0.1")

    public AuditLog(String username, String action, String resource, boolean success) {
        this(username, action, resource, success, inferSeverity(action, success), "");
    }

    public AuditLog(String username, String action, String resource,
                    boolean success, Severity severity, String details) {
        this.username  = username;
        this.action    = action;
        this.resource  = resource;
        this.timestamp = LocalDateTime.now();
        this.success   = success;
        this.severity  = severity;
        this.details   = details;
    }

    private static Severity inferSeverity(String action, boolean success) {
        if (!success) {
            // Denied execute or write is more serious than denied read
            if (action.equalsIgnoreCase("EXECUTE") || action.equalsIgnoreCase("WRITE"))
                return Severity.WARNING;
            return Severity.INFO;
        }
        return Severity.INFO;
    }

    // --- Getters ---

    public String getUsername()          { return username; }
    public String getAction()            { return action; }
    public String getResource()          { return resource; }
    public LocalDateTime getTimestamp()  { return timestamp; }
    public boolean isSuccess()           { return success; }
    public Severity getSeverity()        { return severity; }
    public String getDetails()           { return details; }

    @Override
    public String toString() {
        return String.format("[%s] [%-8s] %-10s | %-8s | %-20s | %s%s",
            timestamp.format(FMT),
            severity,
            username,
            action,
            resource,
            success ? "ALLOWED" : "DENIED",
            details.isEmpty() ? "" : " | " + details
        );
    }
}

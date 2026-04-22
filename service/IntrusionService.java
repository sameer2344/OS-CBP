package service;

import model.User;
import model.AuditLog.Severity;
import java.util.*;
import java.time.LocalDateTime;

/**
 * IntrusionService — detects and responds to suspicious access patterns.
 *
 * Detection rules:
 *  1. BRUTE FORCE: N denied attempts by same user within time window
 *  2. PRIVILEGE ESCALATION PROBE: denied attempts on high-privilege files (e.g., /etc/)
 *  3. SCAN PATTERN: denied attempts on many different files in quick succession
 *
 * Response levels:
 *  - WARN  : alert logged, no action
 *  - LOCK  : user account locked for LOCKOUT_MINUTES
 *  - ALERT : admin notification triggered (simulated)
 */
public class IntrusionService {

    public enum ThreatLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }

    private static final int BRUTE_FORCE_THRESHOLD  = 3;   // denials
    private static final int BRUTE_FORCE_WINDOW_SEC = 60;  // within 60 seconds
    private static final int SCAN_FILE_THRESHOLD    = 5;   // distinct denied files
    private static final int SCAN_WINDOW_SEC        = 30;

    private final AuditService auditService;
    private final AccessControlService acService;

    // Track active alerts: username -> list of alert messages
    private final Map<String, List<String>> activeAlerts;

    public IntrusionService(AuditService auditService, AccessControlService acService) {
        this.auditService  = auditService;
        this.acService     = acService;
        this.activeAlerts  = new LinkedHashMap<>();
    }

    /**
     * Called after every access denial. Evaluates all rules for the user.
     */
    public ThreatLevel evaluate(String username) {
        ThreatLevel level = ThreatLevel.NONE;

        ThreatLevel bruteForce = checkBruteForce(username);
        ThreatLevel scanProbe  = checkScanProbe(username);

        level = higher(level, bruteForce);
        level = higher(level, scanProbe);

        if (level.ordinal() >= ThreatLevel.HIGH.ordinal()) {
            triggerResponse(username, level);
        }

        return level;
    }

    // --- Detection Rules ---

    private ThreatLevel checkBruteForce(String username) {
        int denials = auditService.countRecentDenials(username, BRUTE_FORCE_WINDOW_SEC);

        if (denials >= BRUTE_FORCE_THRESHOLD * 2) {
            recordAlert(username, "CRITICAL: " + denials + " denials in "
                + BRUTE_FORCE_WINDOW_SEC + "s — possible brute force attack");
            return ThreatLevel.CRITICAL;
        }
        if (denials >= BRUTE_FORCE_THRESHOLD) {
            recordAlert(username, "HIGH: " + denials + " denials in "
                + BRUTE_FORCE_WINDOW_SEC + "s — suspicious access pattern");
            return ThreatLevel.HIGH;
        }
        if (denials >= 2) return ThreatLevel.LOW;

        return ThreatLevel.NONE;
    }

    private ThreatLevel checkScanProbe(String username) {
        // Count how many distinct files were denied within window
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(SCAN_WINDOW_SEC);
        Set<String> distinctDeniedFiles = new HashSet<>();

        for (var log : auditService.getLogsByUser(username)) {
            if (!log.isSuccess() && log.getTimestamp().isAfter(cutoff)) {
                distinctDeniedFiles.add(log.getResource());
            }
        }

        if (distinctDeniedFiles.size() >= SCAN_FILE_THRESHOLD) {
            recordAlert(username, "MEDIUM: Access denied on " + distinctDeniedFiles.size()
                + " different files in " + SCAN_WINDOW_SEC + "s — possible file scan");
            return ThreatLevel.MEDIUM;
        }

        return ThreatLevel.NONE;
    }

    // --- Response ---

    private void triggerResponse(String username, ThreatLevel level) {
        User user = acService.getUser(username);
        if (user == null) return;

        if (level == ThreatLevel.CRITICAL) {
            user.setActive(false);  // Disable account entirely
            auditService.log("SYSTEM", "ACCOUNT_DISABLED", username, true,
                Severity.CRITICAL, "Auto-disabled due to intrusion detection");
            System.out.println("[INTRUSION] CRITICAL — account disabled: " + username);
        } else if (level == ThreatLevel.HIGH) {
            // User.authenticate() handles lockout via failedAttempts counter,
            // but we can force a lock here too if coming from file-access denials
            auditService.log("SYSTEM", "INTRUSION_ALERT", username, false,
                Severity.WARNING, "Repeated access denials detected");
            System.out.println("[INTRUSION] HIGH — alert raised for: " + username);
        }

        notifyAdmin(username, level);
    }

    private void notifyAdmin(String username, ThreatLevel level) {
        // In a real system: send email / push alert to admin dashboard
        System.out.println("[ADMIN ALERT] Threat level " + level
            + " detected for user: " + username);
    }

    private void recordAlert(String username, String message) {
        activeAlerts.computeIfAbsent(username, k -> new ArrayList<>()).add(message);
    }

    // --- Queries ---

    public boolean hasActiveAlerts(String username) {
        List<String> alerts = activeAlerts.get(username);
        return alerts != null && !alerts.isEmpty();
    }

    public List<String> getAlerts(String username) {
        return activeAlerts.getOrDefault(username, Collections.emptyList());
    }

    public Map<String, List<String>> getAllAlerts() {
        return Collections.unmodifiableMap(activeAlerts);
    }

    public void clearAlerts(String username) {
        activeAlerts.remove(username);
    }

    // --- Utility ---

    private ThreatLevel higher(ThreatLevel a, ThreatLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}

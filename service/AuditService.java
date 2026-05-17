package service;

import model.AuditLog;
import java.util.*;

public class AuditService {

    private List<AuditLog> logs = new ArrayList<>();

    public void log(AuditLog log) {
        logs.add(log);
    }

    public List<AuditLog> getLogs() {
        return logs;
    }
}
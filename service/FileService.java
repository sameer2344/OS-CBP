package service;

import model.*;
import java.util.*;

public class FileService {

    private AccessControlService acs;
    private AuditService auditSvc;
    private Map<String, FileResource> files;

    public FileService(AccessControlService acs, AuditService auditSvc) {
        this.acs = acs;
        this.auditSvc = auditSvc;
        this.files = new LinkedHashMap<>();
        initDefaultFiles();
    }

    // --- File Management ---

    private void initDefaultFiles() {
        // Create default files owned by admin
        FileResource config = new FileResource("config.sys", "/etc/", "admin");
        config.grantPermission("admin", 'r');
        config.grantPermission("admin", 'w');
        config.grantPermission("admin", 'x');
        files.put("config.sys", config);

        FileResource data = new FileResource("data.dat", "/data/", "admin");
        data.grantPermission("admin", 'r');
        data.grantPermission("admin", 'w');
        data.grantPermission("admin", 'x');
        files.put("data.dat", data);

        FileResource script = new FileResource("script.sh", "/bin/", "admin");
        script.grantPermission("admin", 'r');
        script.grantPermission("admin", 'w');
        script.grantPermission("admin", 'x');
        files.put("script.sh", script);
    }

    public boolean createFile(String name, String path, User user) {
        if (files.containsKey(name)) return false;
        FileResource file = new FileResource(name, path, user.getUsername());
        file.grantPermission(user.getUsername(), 'r');
        file.grantPermission(user.getUsername(), 'w');
        file.grantPermission(user.getUsername(), 'x');
        files.put(name, file);
        auditSvc.log(user.getUsername(), "CREATE", name, true);
        return true;
    }

    public FileResource getFile(String filename) {
        return files.get(filename);
    }

    public List<FileResource> getAllFiles() {
        return new ArrayList<>(files.values());
    }

    // --- File Operations ---

    public boolean readFile(String filename, User user) {
        FileResource file = getFile(filename);
        if (file == null) {
            auditSvc.log(user.getUsername(), "READ", filename, false);
            return false;
        }

        if (!acs.checkAccess(user, file, 'r')) {
            auditSvc.log(user.getUsername(), "READ", filename, false);
            return false;
        }

        file.recordWrite();
        auditSvc.log(user.getUsername(), "READ", filename, true);
        return true;
    }

    public boolean writeFile(String filename, User user) {
        FileResource file = getFile(filename);
        if (file == null) {
            auditSvc.log(user.getUsername(), "WRITE", filename, false);
            return false;
        }

        if (!acs.checkAccess(user, file, 'w')) {
            auditSvc.log(user.getUsername(), "WRITE", filename, false);
            return false;
        }

        file.recordWrite();
        auditSvc.log(user.getUsername(), "WRITE", filename, true);
        return true;
    }

    public boolean executeFile(String filename, User user) {
        FileResource file = getFile(filename);
        if (file == null) {
            auditSvc.log(user.getUsername(), "EXECUTE", filename, false);
            return false;
        }

        if (!acs.checkAccess(user, file, 'x')) {
            auditSvc.log(user.getUsername(), "EXECUTE", filename, false);
            return false;
        }

        auditSvc.log(user.getUsername(), "EXECUTE", filename, true);
        return true;
    }
}
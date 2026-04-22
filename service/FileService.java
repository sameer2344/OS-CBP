package service;

import model.FileResource;
import model.User;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * FileService — handles file operations with concurrency control.
 *
 * Improvements over original:
 *  - ReadWriteLock per file: multiple concurrent reads, exclusive writes
 *  - Owner or admin required to delete files
 *  - Write operation updates lastModified metadata
 *  - File path uniqueness enforced (no two files at same path)
 */
public class FileService {

    private final Map<String, FileResource> files;
    // Per-file read-write lock to simulate OS-level concurrency control
    private final Map<String, ReentrantReadWriteLock> fileLocks;
    private final AccessControlService acService;
    private final AuditService auditService;

    public FileService(AccessControlService acService, AuditService auditService) {
        this.files       = new ConcurrentHashMap<>();
        this.fileLocks   = new ConcurrentHashMap<>();
        this.acService   = acService;
        this.auditService = auditService;
        initDefaultFiles();
    }

    private void initDefaultFiles() {
        addFile(new FileResource("config.sys",  "/etc/",  "admin"));
        addFile(new FileResource("data.dat",    "/data/", "user1"));
        addFile(new FileResource("script.sh",   "/bin/",  "admin"));
    }

    private void addFile(FileResource file) {
        files.put(file.getName(), file);
        fileLocks.put(file.getName(), new ReentrantReadWriteLock());
    }

    // --- File Lifecycle ---

    public boolean createFile(String name, String path, User owner) {
        if (files.containsKey(name)) return false;
        FileResource newFile = new FileResource(name, path, owner.getUsername());
        addFile(newFile);
        auditService.log(owner.getUsername(), "CREATE", name, true);
        return true;
    }

    /**
     * Delete a file. Only the owner or an admin can delete.
     */
    public boolean deleteFile(String name, User requestingUser) {
        FileResource file = files.get(name);
        if (file == null) return false;

        boolean canDelete = acService.isAdmin(requestingUser)
                         || file.getOwner().equals(requestingUser.getUsername());
        if (!canDelete) {
            auditService.log(requestingUser.getUsername(), "DELETE", name, false);
            return false;
        }

        files.remove(name);
        fileLocks.remove(name);
        auditService.log(requestingUser.getUsername(), "DELETE", name, true);
        return true;
    }

    // --- File Operations with Locking ---

    /**
     * Simulate reading a file.
     * Multiple users can hold a read lock simultaneously.
     */
    public boolean readFile(String name, User user) {
        FileResource file = files.get(name);
        if (file == null) {
            auditService.log(user.getUsername(), "READ", name, false);
            return false;
        }

        boolean allowed = acService.canRead(file, user);
        auditService.log(user.getUsername(), "READ", name, allowed);

        if (!allowed) return false;

        ReentrantReadWriteLock lock = fileLocks.get(name);
        lock.readLock().lock();
        try {
            // Simulate read operation
            System.out.println("[READ] " + user.getUsername() + " reads: " + file.getName());
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Simulate writing to a file.
     * Exclusive write lock: blocks all other reads and writes.
     */
    public boolean writeFile(String name, User user) {
        FileResource file = files.get(name);
        if (file == null) {
            auditService.log(user.getUsername(), "WRITE", name, false);
            return false;
        }

        boolean allowed = acService.canWrite(file, user);
        auditService.log(user.getUsername(), "WRITE", name, allowed);

        if (!allowed) return false;

        ReentrantReadWriteLock lock = fileLocks.get(name);
        lock.writeLock().lock();
        try {
            file.recordWrite();  // updates lastModified
            System.out.println("[WRITE] " + user.getUsername() + " writes to: " + file.getName());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Simulate executing a file.
     */
    public boolean executeFile(String name, User user) {
        FileResource file = files.get(name);
        if (file == null) {
            auditService.log(user.getUsername(), "EXECUTE", name, false);
            return false;
        }

        boolean allowed = acService.canExecute(file, user);
        auditService.log(user.getUsername(), "EXECUTE", name, allowed);

        if (!allowed) return false;

        System.out.println("[EXEC] " + user.getUsername() + " executes: " + file.getName());
        return true;
    }

    // --- Queries ---

    public FileResource getFile(String name)  { return files.get(name); }
    public boolean fileExists(String name)    { return files.containsKey(name); }

    public List<FileResource> getAllFiles() {
        return Collections.unmodifiableList(new ArrayList<>(files.values()));
    }

    public List<FileResource> getUserFiles(String username) {
        List<FileResource> result = new ArrayList<>();
        for (FileResource f : files.values()) {
            if (f.getOwner().equals(username)) result.add(f);
        }
        return result;
    }
}

# 🔐 File Access Control System — Complete Feature Guide

## 📋 Table of Contents
1. [System Overview](#system-overview)
2. [Core Components](#core-components)
3. [Feature-by-Feature Breakdown](#feature-by-feature-breakdown)
4. [OS Concepts Demonstrated](#os-concepts-demonstrated)
5. [Viva Q&A Preparation](#viva-qa-preparation)

---

## System Overview

This project simulates **OS-level file protection** by implementing:
- **Role-Based Access Control (RBAC)** — users grouped by privilege level
- **Access Control Matrix** — who can access what
- **File Permissions (rwx)** — read, write, execute model like Linux
- **Audit Logging** — every action tracked
- **Intrusion Detection** — brute force & scan pattern detection
- **Protection Domains** — user's access rights across all resources

---

## Core Components

### 1. **Model Layer** (Data Structures)

#### `User.java`
**What it does:** Represents a system user with authentication and security features.

**Key Features:**
- **Password hashing** — SHA-256, never stores plain text
- **Failed login tracking** — counts wrong password attempts
- **Account lockout** — locks for 5 minutes after 3 failed attempts
- **Role assignment** — ADMIN, MODERATOR, USER, GUEST
- **Serialization** — saves to `users.dat` for persistence

**OS Concept:** User identity and authentication (like `/etc/passwd` in Linux)

---

#### `FileResource.java`
**What it does:** Represents a file with metadata and per-user permissions.

**Key Features:**
- **Owner tracking** — who created the file
- **Permission map** — stores rwx for each user individually
- **Default permissions** — fallback for users without explicit grants
- **Metadata** — creation time, last modified, size, path

**OS Concept:** File metadata and permission storage (like inode in Unix)

---

#### `AuditLog.java`
**What it does:** Records every access attempt (allowed or denied).

**Key Features:**
- **Timestamp** — when the action occurred
- **User + Action + Resource** — who did what to which file
- **Success/Failure** — was it allowed or denied
- **Severity levels** — INFO, WARNING, CRITICAL

**OS Concept:** System audit trail (like `/var/log/auth.log` in Linux)

---

### 2. **Service Layer** (Business Logic)

#### `AccessControlService.java`
**What it does:** Central authority for ALL permission decisions.

**Key Features:**
- **Role hierarchy enforcement:**
  - ADMIN → full access to everything (bypasses all checks)
  - MODERATOR → can read all files, write/execute only if granted
  - USER → needs explicit permission for each file
  - GUEST → needs explicit permission for each file
  
- **Authentication** — validates username + password
- **User lifecycle** — create, delete, deactivate, change role
- **Permission grants/revokes** — only admin or file owner can manage

**OS Concept:** Reference monitor — single point of access control

**Code Flow Example:**
```
User tries to read file → AccessControlService.canRead()
  ↓
1. Check if user is active and not locked → deny if not
2. Check if user is ADMIN → allow immediately
3. Check if user is MODERATOR and action is READ → allow
4. Check file's permission map for this user → allow if 'r' present
5. Otherwise → deny
```

---

#### `FileService.java`
**What it does:** Manages file operations with concurrency control.

**Key Features:**
- **Read/Write/Execute operations** — simulates actual file access
- **ReadWriteLock per file** — multiple readers OR one writer (OS-level concurrency)
- **Create/Delete files** — only owner or admin can delete
- **Metadata updates** — tracks last modified time on writes

**OS Concept:** File system operations with locking (like `flock()` in Unix)

**Code Flow Example:**
```
User clicks "Write" button → FileService.writeFile()
  ↓
1. Check if file exists → deny if not
2. Ask AccessControlService: can this user write? → deny if not
3. Acquire WRITE LOCK (blocks all other operations)
4. Simulate write operation
5. Update file's lastModified timestamp
6. Release WRITE LOCK
7. Log to AuditService
```

---

#### `AuditService.java`
**What it does:** Records and queries all system activity.

**Key Features:**
- **Persistent logging** — writes to `audit.log` file
- **In-memory buffer** — keeps last 500 entries for fast queries
- **Filter queries:**
  - By user
  - By success/failure
  - By severity
  - By time range
- **CSV export** — for external analysis

**OS Concept:** System logging and accountability

---

#### `IntrusionService.java`
**What it does:** Detects suspicious access patterns.

**Key Features:**
- **Brute force detection:**
  - Tracks denied attempts per user
  - If 3+ denials in 60 seconds → HIGH threat
  - If 6+ denials in 60 seconds → CRITICAL threat
  
- **Scan pattern detection:**
  - Tracks distinct files accessed
  - If 5+ different files denied in 30 seconds → MEDIUM threat
  
- **Automated response:**
  - HIGH → alert logged
  - CRITICAL → account disabled automatically

**OS Concept:** Intrusion Detection System (IDS) like fail2ban

---

#### `UserStore.java`
**What it does:** Persists user accounts to disk.

**Key Features:**
- **Save to `users.dat`** — all users survive restart
- **Load on startup** — restores previous state
- **Format:** `username|role|passwordHash|active`

**OS Concept:** User database persistence

---

### 3. **UI Layer** (User Interface)

#### **Login/Register Screen**
**What it does:** Authentication gateway.

**Features:**
- **Login tab:**
  - Enter username + password
  - Validates against stored hash
  - Tracks failed attempts
  - Locks account after 3 failures
  
- **Register tab:**
  - Create new account (auto-assigned USER role)
  - Validates: min 3 chars username, min 6 chars password
  - Checks for duplicate usernames

**OS Concept:** User authentication (like `login` command in Unix)

---

#### **File Operations Tab**
**What it does:** Main workspace for file access.

**Left Panel — File List:**
- Shows all files with YOUR permissions: `filename [rwx] (owner)`
- Example: `config.sys [r--] (admin)` means you can only read it

**File Details Panel (below file list):**
- Shows metadata when you select a file:
  - Name, Path, Owner
  - Your permissions (rwx)
  - Last modified timestamp

**Right Panel — Operations:**
- **Read button** — attempts to read selected file
- **Write button** — attempts to write to selected file
- **Execute button** — attempts to execute selected file
- **Create File button** — creates new file (you become owner)

**Result Area:**
- Shows outcome of each operation with timestamp
- Format: `[HH:MM:SS] ✅ ALLOWED — READ on config.sys (user: admin, role: ADMIN)`
- Auto-scrolls to latest entry
- Shows intrusion threat level if denied

**Protection Domain Panel (bottom):**
- Shows YOUR access rights across ALL files
- This is the OS concept of "protection domain" — the set of resources you can access
- Updates automatically when permissions change

**OS Concept:** File browser + permission visualization

---

#### **Audit Log Tab**
**What it does:** Shows complete system activity history.

**Features:**
- **Table columns:**
  - Timestamp — when it happened
  - User — who did it
  - Action — READ/WRITE/EXECUTE/LOGIN/etc
  - File — which resource
  - Status — ALLOWED or DENIED
  - Severity — INFO/WARNING/CRITICAL

- **Color coding:**
  - White — normal allowed operations
  - Light red — denied operations
  - Light yellow — warnings
  - Orange — critical events

- **Filter bar:**
  - Filter by username (partial match)
  - Filter by status (All/ALLOWED/DENIED)
  - Click "Filter" to apply
  - Click "Show All" to reset

- **Buttons:**
  - Refresh — reload latest 100 entries
  - Export CSV — save to `audit_export.csv`
  - Clear — delete all logs (admin only)

**OS Concept:** System audit trail with filtering

---

#### **Alerts Tab**
**What it does:** Shows intrusion detection alerts.

**Features:**
- **Alert display:**
  - Shows which users triggered alerts
  - Lists specific threats detected
  - Example: "HIGH: 4 denials in 60s — suspicious access pattern"
  
- **Color coding:**
  - Green text — no alerts (system clean)
  - Red text — active alerts present

- **Buttons:**
  - Refresh Alerts — reload from IntrusionService
  - Clear All Alerts — reset all threat tracking

**OS Concept:** Security monitoring dashboard

---

#### **Admin Panel** (3 sub-tabs)

##### **👥 User Management Tab**
**What it does:** Manage all system users (admin only).

**Features:**
- **Table shows:**
  - Username
  - Role (ADMIN/MODERATOR/USER/GUEST)
  - Status (ACTIVE/INACTIVE/LOCKED)
  - Last Login timestamp
  - Failed Attempts count

- **Buttons:**
  - **Refresh** — reload user list
  - **Add User** — create new account with any role
  - **Change Role** — select user, change their role
  - **Deactivate** — disable account (can't login)
  - **Delete** — permanently remove user (can't delete admin)

**OS Concept:** User administration (like `useradd`, `usermod` in Linux)

---

##### **🔐 Access Control Matrix Tab**
**What it does:** Visualizes the complete permission matrix.

**Features:**
- **Matrix layout:**
  - Rows = files
  - Columns = users
  - Cells = permissions (rwx format)
  
- **Example:**
  ```
  File \ User    admin    user1    user2    guest
  config.sys     rwx      r--      ---      ---
  data.dat       rwx      rwx      r--      ---
  script.sh      rwx      --x      ---      ---
  ```

- **Refresh Matrix button** — reload current state

**OS Concept:** Access Control Matrix — core OS security model

**Viva Tip:** This is THE key visual for explaining "who can do what to which resource"

---

##### **📂 File Permissions Tab**
**What it does:** Detailed permission management per file.

**Features:**
- **View File Permissions:**
  - Enter filename
  - Shows owner, path, and permissions for ALL users
  
- **Grant Permission:**
  - Enter filename, username, permission (r/w/x)
  - Adds that permission to that user
  
- **Revoke Permission:**
  - Enter filename, username, permission (r/w/x)
  - Removes that permission from that user

**OS Concept:** Permission management (like `chmod`, `chown` in Linux)

---

#### **Top Bar Features**

**Role Hierarchy Strip:**
- Always visible below title bar
- Shows: `ADMIN (rwx all) > MODERATOR (r all) > USER (explicit grants) > GUEST (explicit grants)`
- Reminds users of privilege levels

**Status Label:**
- Shows current user and role
- Example: `Logged in as: user1 [USER]`

**Change Password Button (🔑):**
- Any user can change their own password
- Must provide current password first
- New password must be 6+ characters

**Logout Button:**
- Logs current user out
- Returns to Login/Register screen

---

## OS Concepts Demonstrated

### 1. **Protection vs Security**
- **Protection** — mechanisms to control access (RBAC, permissions)
- **Security** — policies to prevent attacks (intrusion detection, lockout)

### 2. **Access Control Matrix**
- Visual table in Admin panel
- Shows complete permission state
- Rows = resources (files), Columns = subjects (users), Cells = rights (rwx)

### 3. **Role-Based Access Control (RBAC)**
- Users grouped by role
- Permissions assigned to roles, not individuals
- Hierarchy: ADMIN > MODERATOR > USER > GUEST

### 4. **File Permissions (rwx model)**
- r = read, w = write, x = execute
- Same model as Linux/Unix
- Owner always has full access

### 5. **Protection Domains**
- Set of resources a user can access
- Shown in "Protection Domain" panel
- Changes based on role and explicit grants

### 6. **Audit Logging**
- Every operation recorded
- Timestamp, user, action, resource, outcome
- Enables accountability and forensics

### 7. **Intrusion Detection**
- Monitors for suspicious patterns
- Brute force detection (repeated failures)
- Scan detection (accessing many files quickly)
- Automated response (account lockout)

### 8. **Reference Monitor**
- AccessControlService is the single point of control
- All access requests go through it
- Cannot be bypassed

### 9. **Concurrency Control**
- ReadWriteLock per file
- Multiple readers OR one writer
- Prevents race conditions

### 10. **Authentication**
- Password hashing (SHA-256)
- Failed attempt tracking
- Account lockout mechanism

---

## Viva Q&A Preparation

### Q: "Explain how RBAC works in your system."
**A:** "We have 4 roles in a hierarchy. ADMIN has full access to everything and bypasses all checks. MODERATOR can read all files but needs explicit permission for write/execute. USER and GUEST need explicit permission for every operation. This is implemented in AccessControlService.checkPermission() which checks the user's role first, then falls back to the file's permission map."

---

### Q: "Show me the Access Control Matrix."
**A:** "Go to Admin tab → Access Control Matrix. This table shows rows as files, columns as users, and cells as permissions. For example, if admin has 'rwx' on config.sys and user1 has 'r--', you can see that in the matrix. This is the classic OS security model."

---

### Q: "What happens when a user tries to access a file they don't have permission for?"
**A:** "Let me demonstrate. I'll login as user1 and try to write to config.sys which is owned by admin. [Click Write] → See, it says DENIED. Now check the Audit Log tab — you'll see the denied attempt logged with timestamp, user, action, and severity. If I keep trying, the Intrusion Detection will flag it. [Try 3 more times] → Now check Alerts tab — it shows a HIGH threat level for repeated denials."

---

### Q: "How does intrusion detection work?"
**A:** "IntrusionService monitors the audit log for patterns. It counts denied attempts per user within time windows. If a user has 3+ denials in 60 seconds, it's flagged as HIGH threat (possible brute force). If 6+ denials, it's CRITICAL and the account is auto-disabled. It also detects scan patterns — if someone tries to access 5+ different files in 30 seconds and gets denied, that's flagged as MEDIUM threat (possible reconnaissance)."

---

### Q: "What's the difference between protection and security?"
**A:** "Protection is the mechanism — the RBAC system, permission checks, access control matrix. Security is the policy — intrusion detection, account lockout, audit logging. Protection controls who can access what. Security detects and responds to attacks."

---

### Q: "Explain the file permission model."
**A:** "We use the Linux rwx model. 'r' means read, 'w' means write, 'x' means execute. Each file has a permission map that stores which users have which permissions. The owner always has full rwx. Others need explicit grants. You can see this in the File Operations tab — each file shows [rwx] next to it indicating your permissions."

---

### Q: "How is this different from real OS file systems?"
**A:** "This is a simulation. A real OS like Linux stores permissions in inodes on disk and enforces them in kernel space. We store permissions in memory (HashMap) and enforce them in Java code. But the concepts are identical — owner, permissions, access control checks, audit logging. The difference is implementation, not design."

---

### Q: "Show me how audit logging helps with security."
**A:** "Go to Audit Log tab. You can filter by user or status. For example, filter Status = DENIED to see all failed access attempts. This helps identify: 1) Users trying to access files they shouldn't, 2) Repeated failures indicating brute force, 3) Patterns of suspicious behavior. You can export to CSV for deeper analysis."

---

### Q: "What's a protection domain?"
**A:** "A protection domain is the set of resources a user can access and what operations they can perform. In our system, it's shown in the 'Protection Domain' panel on the File Operations tab. It lists all files and your permissions on each. This changes based on your role and any explicit grants an admin gives you."

---

### Q: "How do you prevent unauthorized access?"
**A:** "Multiple layers: 1) Authentication — password hashing, no plain text storage. 2) Authorization — every operation goes through AccessControlService which checks role and permissions. 3) Audit — every attempt is logged. 4) Intrusion detection — repeated failures trigger lockout. 5) Reference monitor — single point of control, can't be bypassed."

---

### Q: "Can you change someone else's permissions?"
**A:** "Only if you're an admin OR the file owner. Go to Admin → File Permissions. Enter filename, username, and permission. The system checks if you have authority before allowing the change. Regular users can't modify permissions at all."

---

## Power One-Liner for Viva

**"This project simulates OS-level file protection by implementing role-based access control, an access control matrix, Linux-style rwx permissions, comprehensive audit logging, and intrusion detection — demonstrating how operating systems enforce security policies to protect resources from unauthorized access."**

---

## Quick Demo Script

1. **Login as admin** (admin / Admin@123)
2. **Show File Operations** — "This is my protection domain, I have rwx on everything because I'm admin"
3. **Try operations** — Read, Write, Execute → all succeed
4. **Show Audit Log** — "Every operation is logged with timestamp and user"
5. **Go to Admin → Access Control Matrix** — "This is the complete permission state"
6. **Go to Admin → User Management** — "I can manage all users, change roles, deactivate accounts"
7. **Logout, login as user1** (user1 / User1@123)
8. **Try to write to config.sys** → DENIED
9. **Try 3 more times** → Account locks or threat level rises
10. **Show Alerts tab** — "Intrusion detection caught the suspicious pattern"
11. **Show Audit Log filtered by DENIED** — "All my failed attempts are tracked"

---

## Files Generated at Runtime

- `users.dat` — persisted user accounts
- `audit.log` — complete audit trail
- `audit_export.csv` — exported audit data (when you click Export)

---

## Compile & Run

```bash
javac -d . model/*.java service/*.java ui/*.java
java ui.MainUI
```

Default login: `admin` / `Admin@123`

---

**Good luck with your viva! 🎓**

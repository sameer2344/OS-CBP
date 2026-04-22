package ui;

import model.*;
import service.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;

/**
 * MainUI — Swing GUI for the File Access Control & Protection System.
 *
 * Opens with a Login/Register card. After login, shows:
 *  1. File Operations
 *  2. Audit Log
 *  3. Alerts
 *  4. Admin Panel (admin only)
 */
public class MainUI extends JFrame {

    // --- Services ---
    private final AccessControlService acService = new AccessControlService();
    private final AuditService         auditSvc  = new AuditService();
    private final FileService          fileSvc   = new FileService(acService, auditSvc);
    private final IntrusionService     intruSvc  = new IntrusionService(auditSvc, acService);

    // --- State ---
    private User currentUser = null;

    // --- UI References ---
    private JLabel  statusLabel;
    private JTabbedPane tabs;

    private DefaultListModel<String> fileListModel;
    private JList<String>            fileList;
    private JTextArea                resultAreaRef;
    private DefaultTableModel        auditTableModel;
    private JTextArea                alertArea;

    public MainUI() {
        super("File Access Control & Protection System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(920, 640);
        setLocationRelativeTo(null);
        buildUI();
        showAuthDialog();
    }

    // =========================================================
    //  UI Construction
    // =========================================================

    private void buildUI() {
        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(40, 40, 60));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel title = new JLabel("🔐 File Access Control System");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));

        statusLabel = new JLabel("Not logged in");
        statusLabel.setForeground(new Color(150, 220, 150));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightButtons.setOpaque(false);

        JButton changePwdBtn = new JButton("🔑 Change Password");
        changePwdBtn.addActionListener(e -> changePasswordDialog());

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> logout());

        rightButtons.add(changePwdBtn);
        rightButtons.add(logoutBtn);

        topBar.add(title,        BorderLayout.WEST);
        topBar.add(statusLabel,  BorderLayout.CENTER);
        topBar.add(rightButtons, BorderLayout.EAST);

        // Role hierarchy strip below top bar
        JPanel roleBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 2));
        roleBar.setBackground(new Color(55, 55, 80));
        JLabel roleHierarchy = new JLabel("Role Hierarchy:  ADMIN (rwx all)  >  MODERATOR (r all)  >  USER (explicit grants)  >  GUEST (explicit grants)");
        roleHierarchy.setForeground(new Color(180, 200, 255));
        roleHierarchy.setFont(new Font("SansSerif", Font.PLAIN, 11));
        roleBar.add(roleHierarchy);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(topBar,   BorderLayout.NORTH);
        northPanel.add(roleBar,  BorderLayout.SOUTH);

        // Tabs
        tabs = new JTabbedPane();
        tabs.addTab("📁 File Operations", buildFileOpsPanel());
        tabs.addTab("📋 Audit Log",        buildAuditPanel());
        tabs.addTab("🚨 Alerts",           buildAlertsPanel());
        tabs.addTab("⚙️ Admin",            buildAdminPanel());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(northPanel, BorderLayout.NORTH);
        getContentPane().add(tabs,       BorderLayout.CENTER);
    }

    // --- File Ops Panel ---

    private JPanel buildFileOpsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setFont(new Font("Monospaced", Font.PLAIN, 13));
        refreshFileList();

        // Show file metadata when a file is selected
        JTextArea fileInfoArea = new JTextArea(4, 20);
        fileInfoArea.setEditable(false);
        fileInfoArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        fileInfoArea.setBackground(new Color(250, 250, 240));
        fileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String sel = fileList.getSelectedValue();
            if (sel == null || currentUser == null) return;
            String fname = sel.split(" ")[0];
            FileResource f = fileSvc.getFile(fname);
            if (f == null) return;
            fileInfoArea.setText(
                "Name   : " + f.getName() + "\n" +
                "Path   : " + f.getPath() + "\n" +
                "Owner  : " + f.getOwner() + "\n" +
                "Perms  : " + f.getPermissionString(currentUser.getUsername()) + "  (rwx)\n" +
                "Modified: " + f.getLastModified().toString().substring(0, 19).replace("T", " ")
            );
        });

        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setBorder(BorderFactory.createTitledBorder("Available Files"));
        JScrollPane infoScroll = new JScrollPane(fileInfoArea);
        infoScroll.setBorder(BorderFactory.createTitledBorder("File Details"));
        leftPanel.add(fileScroll, BorderLayout.CENTER);
        leftPanel.add(infoScroll, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(260, 0));

        JPanel opsPanel = new JPanel(new GridLayout(4, 1, 8, 8));
        opsPanel.setBorder(BorderFactory.createTitledBorder("Operations"));

        JButton readBtn   = new JButton("📖 Read");
        JButton writeBtn  = new JButton("✏️ Write");
        JButton execBtn   = new JButton("▶️ Execute");
        JButton createBtn = new JButton("➕ Create File");

        readBtn.addActionListener(e   -> performOp("READ"));
        writeBtn.addActionListener(e  -> performOp("WRITE"));
        execBtn.addActionListener(e   -> performOp("EXECUTE"));
        createBtn.addActionListener(e -> createFileDialog());

        opsPanel.add(readBtn);
        opsPanel.add(writeBtn);
        opsPanel.add(execBtn);
        opsPanel.add(createBtn);

        resultAreaRef = new JTextArea(6, 30);
        resultAreaRef.setEditable(false);
        resultAreaRef.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultAreaRef);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Result"));

        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        rightPanel.add(opsPanel,     BorderLayout.NORTH);
        rightPanel.add(resultScroll, BorderLayout.CENTER);

        // Protection Domain info — shows current user's access rights (OS concept)
        JPanel domainPanel = new JPanel(new BorderLayout());
        domainPanel.setBorder(BorderFactory.createTitledBorder("Protection Domain (Current User)"));
        JTextArea domainArea = new JTextArea(3, 30);
        domainArea.setEditable(false);
        domainArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        domainArea.setBackground(new Color(245, 245, 255));
        domainPanel.add(new JScrollPane(domainArea));
        this.domainAreaRef = domainArea;

        JPanel rightOuter = new JPanel(new BorderLayout(8, 8));
        rightOuter.add(rightPanel,  BorderLayout.CENTER);
        rightOuter.add(domainPanel, BorderLayout.SOUTH);

        panel.add(leftPanel,   BorderLayout.WEST);
        panel.add(rightOuter,  BorderLayout.CENTER);
        return panel;
    }

    private JTextArea domainAreaRef;

    private void performOp(String op) {
        if (currentUser == null) { mustLogin(); return; }
        String selected = fileList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file first.");
            return;
        }
        String filename = selected.split(" ")[0];
        boolean result = switch (op) {
            case "READ"    -> fileSvc.readFile(filename, currentUser);
            case "WRITE"   -> fileSvc.writeFile(filename, currentUser);
            case "EXECUTE" -> fileSvc.executeFile(filename, currentUser);
            default        -> false;
        };

        String time = java.time.LocalTime.now().toString().substring(0, 8);
        String line = "[" + time + "] " + (result ? "✅ ALLOWED" : "❌ DENIED")
            + " — " + op + " on " + filename
            + "  (user: " + currentUser.getUsername() + ", role: " + currentUser.getRole() + ")\n";
        resultAreaRef.append(line);
        // Auto-scroll to bottom
        resultAreaRef.setCaretPosition(resultAreaRef.getDocument().getLength());

        if (!result) {
            IntrusionService.ThreatLevel level = intruSvc.evaluate(currentUser.getUsername());
            if (level != IntrusionService.ThreatLevel.NONE) {
                resultAreaRef.append("  ⚠️  Intrusion threat level: " + level + "\n");
                resultAreaRef.setCaretPosition(resultAreaRef.getDocument().getLength());
                refreshAlerts();
            }
        }
        refreshAuditTable();
        refreshFileList();
    }

    private void createFileDialog() {
        if (currentUser == null) { mustLogin(); return; }
        String name = JOptionPane.showInputDialog(this, "File name:");
        if (name == null || name.isBlank()) return;
        String path = JOptionPane.showInputDialog(this, "Path (e.g. /data/):");
        if (path == null || path.isBlank()) return;
        boolean ok = fileSvc.createFile(name.trim(), path.trim(), currentUser);
        JOptionPane.showMessageDialog(this, ok ? "File created." : "File already exists.");
        if (ok) refreshFileList();
    }

    // --- Audit Panel ---

    private JPanel buildAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] cols = {"Timestamp", "User", "Action", "File", "Status", "Severity"};
        auditTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(auditTableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(20);

        // Color rows: DENIED = light red, CRITICAL = orange
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public java.awt.Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                String status   = (String) t.getValueAt(row, 4);
                String severity = (String) t.getValueAt(row, 5).toString();
                if (!sel) {
                    if ("CRITICAL".equals(severity))      setBackground(new Color(255, 220, 180));
                    else if ("WARNING".equals(severity))  setBackground(new Color(255, 255, 200));
                    else if ("DENIED".equals(status))     setBackground(new Color(255, 235, 235));
                    else                                  setBackground(Color.WHITE);
                }
                return this;
            }
        });

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.setBorder(BorderFactory.createTitledBorder("Filter"));
        JTextField filterUser = new JTextField(10);
        String[] statusOpts = {"All", "ALLOWED", "DENIED"};
        JComboBox<String> statusFilter = new JComboBox<>(statusOpts);
        JButton applyFilter = new JButton("🔍 Filter");
        JButton showAll     = new JButton("Show All");

        filterBar.add(new JLabel("User:"));
        filterBar.add(filterUser);
        filterBar.add(new JLabel("Status:"));
        filterBar.add(statusFilter);
        filterBar.add(applyFilter);
        filterBar.add(showAll);

        applyFilter.addActionListener(e -> {
            String uname  = filterUser.getText().trim().toLowerCase();
            String status = (String) statusFilter.getSelectedItem();
            auditTableModel.setRowCount(0);
            for (AuditLog log : auditSvc.getRecentLogs(200)) {
                boolean matchUser   = uname.isEmpty() || log.getUsername().toLowerCase().contains(uname);
                boolean matchStatus = "All".equals(status)
                    || ("ALLOWED".equals(status) && log.isSuccess())
                    || ("DENIED".equals(status)  && !log.isSuccess());
                if (matchUser && matchStatus) addAuditRow(log);
            }
        });
        showAll.addActionListener(e -> refreshAuditTable());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("🔄 Refresh");
        JButton exportBtn  = new JButton("💾 Export CSV");
        JButton clearBtn   = new JButton("🗑 Clear");

        refreshBtn.addActionListener(e -> refreshAuditTable());
        exportBtn.addActionListener(e -> {
            boolean ok = auditSvc.exportToCsv("audit_export.csv");
            JOptionPane.showMessageDialog(this, ok ? "Exported to audit_export.csv" : "Export failed.");
        });
        clearBtn.addActionListener(e -> { auditSvc.clearLogs(); refreshAuditTable(); });

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(filterBar, BorderLayout.NORTH);
        southPanel.add(btnRow,    BorderLayout.SOUTH);
        btnRow.add(refreshBtn); btnRow.add(exportBtn); btnRow.add(clearBtn);

        panel.add(southPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void addAuditRow(AuditLog log) {
        auditTableModel.addRow(new Object[]{
            log.getTimestamp().toString().replace("T", " ").substring(0, 19),
            log.getUsername(),
            log.getAction(),
            log.getResource(),
            log.isSuccess() ? "ALLOWED" : "DENIED",
            log.getSeverity()
        });
    }

    private void refreshAuditTable() {
        auditTableModel.setRowCount(0);
        for (AuditLog log : auditSvc.getRecentLogs(100)) addAuditRow(log);
    }

    // --- Alerts Panel ---

    private JPanel buildAlertsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel info = new JLabel("<html><b>Intrusion Detection Alerts</b> — Brute force & scan pattern detection</html>");
        info.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        panel.add(info, BorderLayout.NORTH);

        alertArea = new JTextArea();
        alertArea.setEditable(false);
        alertArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        alertArea.setForeground(new Color(180, 30, 30));
        panel.add(new JScrollPane(alertArea), BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("🔄 Refresh Alerts");
        JButton clearBtn   = new JButton("🗑 Clear All Alerts");

        refreshBtn.addActionListener(e -> refreshAlerts());
        clearBtn.addActionListener(e -> {
            for (User u : acService.getAllUsers()) intruSvc.clearAlerts(u.getUsername());
            refreshAlerts();
        });

        btnRow.add(refreshBtn);
        btnRow.add(clearBtn);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshAlerts() {
        alertArea.setText("");
        var allAlerts = intruSvc.getAllAlerts();
        if (allAlerts.isEmpty()) {
            alertArea.setForeground(new Color(50, 150, 50));
            alertArea.setText("✅ No active intrusion alerts.");
            return;
        }
        alertArea.setForeground(new Color(180, 30, 30));
        StringBuilder sb = new StringBuilder();
        sb.append("=== ACTIVE INTRUSION ALERTS ===\n\n");
        for (var entry : allAlerts.entrySet()) {
            sb.append("👤 User: ").append(entry.getKey()).append("\n");
            for (String alert : entry.getValue()) {
                sb.append("   ⚠️  ").append(alert).append("\n");
            }
            sb.append("\n");
        }
        alertArea.setText(sb.toString());
    }

    // --- Admin Panel ---

    private DefaultTableModel userTableModel;
    private DefaultTableModel aclMatrixModel;

    private JPanel buildAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane adminTabs = new JTabbedPane();
        adminTabs.addTab("👥 User Management",      buildUserManagementPanel());
        adminTabs.addTab("🔐 Access Control Matrix", buildACLMatrixPanel());
        adminTabs.addTab("📂 File Permissions",      buildFilePermissionsPanel());

        panel.add(adminTabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildUserManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        String[] cols = {"Username", "Role", "Status", "Last Login", "Failed Attempts"};
        userTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable userTable = new JTable(userTableModel);
        userTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        userTable.setRowHeight(22);
        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn    = new JButton("� Refresh");
        JButton addUserBtn    = new JButton("➕ Add User");
        JButton changeRoleBtn = new JButton("🔧 Change Role");
        JButton deactivateBtn = new JButton("🚫 Deactivate");
        JButton deleteBtn     = new JButton("🗑 Delete");

        refreshBtn.addActionListener(e    -> refreshUserTable());
        addUserBtn.addActionListener(e    -> addUserDialog());
        changeRoleBtn.addActionListener(e -> changeRoleDialog(userTable));
        deactivateBtn.addActionListener(e -> deactivateUserDialog(userTable));
        deleteBtn.addActionListener(e     -> deleteUserDialog(userTable));

        btnRow.add(refreshBtn); btnRow.add(addUserBtn); btnRow.add(changeRoleBtn);
        btnRow.add(deactivateBtn); btnRow.add(deleteBtn);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshUserTable() {
        if (!requireAdmin()) return;
        userTableModel.setRowCount(0);
        for (User u : acService.getAllUsers()) {
            String status = u.isLocked() ? "LOCKED" : (u.isActive() ? "ACTIVE" : "INACTIVE");
            String lastLogin = u.getLastLogin() != null
                ? u.getLastLogin().toString().substring(0, 19).replace("T", " ") : "Never";
            userTableModel.addRow(new Object[]{
                u.getUsername(), u.getRole(), status, lastLogin, u.getFailedAttempts()
            });
        }
    }

    private void addUserDialog() {
        if (!requireAdmin()) return;
        String uname = JOptionPane.showInputDialog(this, "Username:");
        if (uname == null || uname.isBlank()) return;
        String role  = JOptionPane.showInputDialog(this, "Role (ADMIN/MODERATOR/USER/GUEST):");
        if (role == null || role.isBlank()) return;
        String pass  = JOptionPane.showInputDialog(this, "Password:");
        if (pass == null || pass.isBlank()) return;
        boolean ok = acService.createUser(uname.trim(), role.trim().toUpperCase(), pass.trim());
        JOptionPane.showMessageDialog(this, ok ? "User created." : "Username already exists.");
        if (ok) refreshUserTable();
    }

    private void changeRoleDialog(JTable table) {
        if (!requireAdmin()) return;
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        String username = (String) table.getValueAt(row, 0);
        String newRole = JOptionPane.showInputDialog(this, "New role for " + username + " (ADMIN/MODERATOR/USER/GUEST):");
        if (newRole == null || newRole.isBlank()) return;
        boolean ok = acService.changeRole(username, newRole.trim().toUpperCase(), currentUser);
        JOptionPane.showMessageDialog(this, ok ? "Role changed." : "Failed.");
        if (ok) { UserStore.save(acService.getAllUsers()); refreshUserTable(); }
    }

    private void deactivateUserDialog(JTable table) {
        if (!requireAdmin()) return;
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        String username = (String) table.getValueAt(row, 0);
        boolean ok = acService.deactivateUser(username);
        JOptionPane.showMessageDialog(this, ok ? "User deactivated." : "Failed.");
        if (ok) { UserStore.save(acService.getAllUsers()); refreshUserTable(); }
    }

    private void deleteUserDialog(JTable table) {
        if (!requireAdmin()) return;
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        String username = (String) table.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete user '" + username + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        boolean ok = acService.deleteUser(username);
        JOptionPane.showMessageDialog(this, ok ? "User deleted." : "Cannot delete admin.");
        if (ok) refreshUserTable();
    }

    private JPanel buildACLMatrixPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel info = new JLabel(
            "<html><b>Access Control Matrix</b> — rows = files, columns = users, cells = permissions (rwx)</html>");
        info.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        panel.add(info, BorderLayout.NORTH);

        aclMatrixModel = new DefaultTableModel() {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable matrixTable = new JTable(aclMatrixModel);
        matrixTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        matrixTable.setRowHeight(22);
        panel.add(new JScrollPane(matrixTable), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("🔄 Refresh Matrix");
        refreshBtn.addActionListener(e -> refreshACLMatrix());
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshACLMatrix() {
        if (!requireAdmin()) return;
        var files = fileSvc.getAllFiles();
        var users = acService.getAllUsers();

        String[] cols = new String[users.size() + 1];
        cols[0] = "File \\ User";
        for (int i = 0; i < users.size(); i++) cols[i + 1] = users.get(i).getUsername();
        aclMatrixModel.setColumnIdentifiers(cols);
        aclMatrixModel.setRowCount(0);

        for (FileResource f : files) {
            Object[] row = new Object[users.size() + 1];
            row[0] = f.getName();
            for (int i = 0; i < users.size(); i++)
                row[i + 1] = f.getPermissionString(users.get(i).getUsername());
            aclMatrixModel.addRow(row);
        }
    }

    private JPanel buildFilePermissionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JTextArea permArea = new JTextArea("Click 'View File Permissions' to inspect a file.");
        permArea.setEditable(false);
        permArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(permArea), BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton viewBtn   = new JButton("🔍 View File Permissions");
        JButton grantBtn  = new JButton("✅ Grant Permission");
        JButton revokeBtn = new JButton("🚫 Revoke Permission");

        viewBtn.addActionListener(e -> {
            if (!requireAdmin()) return;
            String fname = JOptionPane.showInputDialog(this, "Filename:");
            if (fname == null || fname.isBlank()) return;
            FileResource f = fileSvc.getFile(fname.trim());
            if (f == null) { JOptionPane.showMessageDialog(this, "File not found."); return; }
            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(f.getName()).append("\n");
            sb.append("Owner: ").append(f.getOwner()).append("\n");
            sb.append("Path: ").append(f.getPath()).append("\n\n");
            sb.append("--- Permissions (rwx) ---\n");
            for (User u : acService.getAllUsers())
                sb.append(String.format("  %-12s : %s\n", u.getUsername(), f.getPermissionString(u.getUsername())));
            permArea.setText(sb.toString());
        });

        grantBtn.addActionListener(e -> {
            if (!requireAdmin()) return;
            String file = JOptionPane.showInputDialog(this, "Filename:");
            String user = JOptionPane.showInputDialog(this, "Username:");
            String perm = JOptionPane.showInputDialog(this, "Permission (r/w/x):");
            if (file == null || user == null || perm == null || perm.isBlank()) return;
            FileResource fileRes = fileSvc.getFile(file.trim());
            if (fileRes == null) { JOptionPane.showMessageDialog(this, "File not found."); return; }
            boolean ok = acService.grantPermission(fileRes, user.trim(), perm.trim().charAt(0), currentUser);
            JOptionPane.showMessageDialog(this, ok ? "Permission granted." : "Failed.");
            refreshACLMatrix(); refreshFileList();
        });

        revokeBtn.addActionListener(e -> {
            if (!requireAdmin()) return;
            String file = JOptionPane.showInputDialog(this, "Filename:");
            String user = JOptionPane.showInputDialog(this, "Username:");
            String perm = JOptionPane.showInputDialog(this, "Permission (r/w/x):");
            if (file == null || user == null || perm == null || perm.isBlank()) return;
            FileResource fileRes = fileSvc.getFile(file.trim());
            if (fileRes == null) { JOptionPane.showMessageDialog(this, "File not found."); return; }
            boolean ok = acService.revokePermission(fileRes, user.trim(), perm.trim().charAt(0), currentUser);
            JOptionPane.showMessageDialog(this, ok ? "Permission revoked." : "Failed.");
            refreshACLMatrix(); refreshFileList();
        });

        btnRow.add(viewBtn); btnRow.add(grantBtn); btnRow.add(revokeBtn);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================
    //  Auth Dialog — Login + Register tabs
    // =========================================================

    private void showAuthDialog() {
        JDialog dialog = new JDialog(this, "Welcome", true);
        dialog.setSize(380, 260);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JTabbedPane authTabs = new JTabbedPane();

        // --- Login tab ---
        JTextField loginUser = new JTextField(15);
        JPasswordField loginPass = new JPasswordField(15);
        JButton loginBtn = new JButton("Login");

        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; loginPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;                loginPanel.add(loginUser, gbc);
        gbc.gridx = 0; gbc.gridy = 1; loginPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;                loginPanel.add(loginPass, gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        loginPanel.add(loginBtn, gbc);

        // --- Register tab ---
        JTextField regUser    = new JTextField(15);
        JPasswordField regPass  = new JPasswordField(15);
        JPasswordField regPass2 = new JPasswordField(15);
        JLabel regMsg = new JLabel(" ");
        regMsg.setForeground(Color.RED);
        JButton regBtn = new JButton("Register");

        JPanel regPanel = new JPanel(new GridBagLayout());
        regPanel.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        GridBagConstraints g2 = new GridBagConstraints();
        g2.insets = new Insets(4, 5, 4, 5);
        g2.fill = GridBagConstraints.HORIZONTAL;

        g2.gridx = 0; g2.gridy = 0; regPanel.add(new JLabel("Username:"), g2);
        g2.gridx = 1;               regPanel.add(regUser, g2);
        g2.gridx = 0; g2.gridy = 1; regPanel.add(new JLabel("Password:"), g2);
        g2.gridx = 1;               regPanel.add(regPass, g2);
        g2.gridx = 0; g2.gridy = 2; regPanel.add(new JLabel("Confirm:"), g2);
        g2.gridx = 1;               regPanel.add(regPass2, g2);
        g2.gridx = 0; g2.gridy = 3; g2.gridwidth = 2; g2.anchor = GridBagConstraints.CENTER;
        regPanel.add(regBtn, g2);
        g2.gridy = 4;               regPanel.add(regMsg, g2);

        authTabs.addTab("Login",    loginPanel);
        authTabs.addTab("Register", regPanel);
        dialog.add(authTabs);

        // Login action
        ActionListener doLogin = ev -> {
            String username = loginUser.getText().trim();
            String password = new String(loginPass.getPassword());
            User user = acService.authenticate(username, password);
            if (user == null) {
                JOptionPane.showMessageDialog(dialog,
                    "Invalid credentials or account locked.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
                loginPass.setText("");
                return;
            }
            currentUser = user;
            statusLabel.setText("Logged in as: " + user.getUsername() + " [" + user.getRole() + "]");
            auditSvc.log(username, "LOGIN", "SYSTEM", true);
            refreshFileList();
            refreshAuditTable();
            dialog.dispose();
        };
        loginBtn.addActionListener(doLogin);
        loginPass.addActionListener(doLogin);

        // Register action
        regBtn.addActionListener(ev -> {
            String uname = regUser.getText().trim();
            String p1    = new String(regPass.getPassword());
            String p2    = new String(regPass2.getPassword());

            if (uname.length() < 3 || !uname.matches("[a-zA-Z0-9_]+")) {
                regMsg.setText("Username: min 3 chars, letters/digits/_ only"); return;
            }
            if (p1.length() < 6) {
                regMsg.setText("Password must be at least 6 characters"); return;
            }
            if (!p1.equals(p2)) {
                regMsg.setText("Passwords do not match"); return;
            }
            boolean ok = acService.createUser(uname, "USER", p1);
            if (!ok) { regMsg.setText("Username already taken"); return; }

            JOptionPane.showMessageDialog(dialog,
                "Account created! You can now log in.", "Registered",
                JOptionPane.INFORMATION_MESSAGE);
            authTabs.setSelectedIndex(0);
            loginUser.setText(uname);
            loginPass.setText("");
            regUser.setText(""); regPass.setText(""); regPass2.setText("");
            regMsg.setText(" ");
        });

        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        dialog.setVisible(true);
    }

    // =========================================================
    //  Change Password Dialog
    // =========================================================

    private void changePasswordDialog() {
        if (currentUser == null) { mustLogin(); return; }

        JPasswordField oldPass  = new JPasswordField(15);
        JPasswordField newPass  = new JPasswordField(15);
        JPasswordField newPass2 = new JPasswordField(15);

        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.add(new JLabel("Current password:"));  panel.add(oldPass);
        panel.add(new JLabel("New password:"));       panel.add(newPass);
        panel.add(new JLabel("Confirm new:"));        panel.add(newPass2);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Change Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String old = new String(oldPass.getPassword());
        String n1  = new String(newPass.getPassword());
        String n2  = new String(newPass2.getPassword());

        if (!currentUser.authenticate(old)) {
            JOptionPane.showMessageDialog(this, "Current password is incorrect.", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (n1.length() < 6) {
            JOptionPane.showMessageDialog(this, "New password must be at least 6 characters.", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!n1.equals(n2)) {
            JOptionPane.showMessageDialog(this, "New passwords do not match.", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        currentUser.changePassword(n1);
        UserStore.save(acService.getAllUsers());
        JOptionPane.showMessageDialog(this, "Password changed successfully.");
    }

    // =========================================================
    //  Logout
    // =========================================================

    private void logout() {
        if (currentUser != null) auditSvc.log(currentUser.getUsername(), "LOGOUT", "SYSTEM", true);
        currentUser = null;
        statusLabel.setText("Not logged in");
        showAuthDialog();
    }

    // =========================================================
    //  Helpers
    // =========================================================

    private void refreshFileList() {
        fileListModel.clear();
        for (FileResource f : fileSvc.getAllFiles()) {
            String perm = (currentUser != null)
                ? f.getPermissionString(currentUser.getUsername()) : "---";
            fileListModel.addElement(f.getName() + "  [" + perm + "]  (" + f.getOwner() + ")");
        }
        refreshDomainPanel();
    }

    private void refreshDomainPanel() {
        if (domainAreaRef == null) return;
        if (currentUser == null) { domainAreaRef.setText("Not logged in."); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("User: ").append(currentUser.getUsername())
          .append("  |  Role: ").append(currentUser.getRole()).append("\n");
        sb.append("Access rights:\n");
        for (FileResource f : fileSvc.getAllFiles()) {
            sb.append(String.format("  %-14s : %s\n",
                f.getName(), f.getPermissionString(currentUser.getUsername())));
        }
        domainAreaRef.setText(sb.toString());
    }

    private boolean requireAdmin() {
        if (currentUser == null || !acService.isAdmin(currentUser)) {
            JOptionPane.showMessageDialog(this, "Admin access required.");
            return false;
        }
        return true;
    }

    private void mustLogin() {
        JOptionPane.showMessageDialog(this, "Please log in first.");
    }

    // =========================================================
    //  Entry Point
    // =========================================================

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainUI().setVisible(true));
    }
}

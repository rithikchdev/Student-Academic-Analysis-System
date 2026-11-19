import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.*;
import java.util.List;

public class StudentPerformanceApp {

    // ================= THEME COLORS & FONTS =================
    static final Color PRIMARY = new Color(37, 99, 235);
    static final Color PRIMARY_HOVER = new Color(29, 78, 216);
    static final Color EDIT_COLOR = new Color(14, 165, 233);
    static final Color DANGER = new Color(220, 38, 38);
    static final Color DANGER_HOVER = new Color(185, 28, 28);
    static final Color SLATE = new Color(71, 85, 105);
    static final Color SLATE_HOVER = new Color(51, 65, 85);
    static final Color WARNING = new Color(217, 119, 6);
    static final Color SUCCESS = new Color(22, 163, 74);

    static final Color CARD_BG = new Color(255, 255, 255, 240);
    static final Color CARD_BORDER = new Color(255, 255, 255, 180);
    static final Color TEXT_DARK = new Color(15, 23, 42);
    static final Color TEXT_MUTED = new Color(100, 116, 139);
    static final Color ROW_ALT = new Color(248, 250, 252);

    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);

    // ================= BACKGROUND MANAGER =================
    static final List<BufferedImage> BG_IMAGES = new ArrayList<>();
    static final List<String> BG_NAMES = new ArrayList<>();
    static int currentBgIndex = 0;

    static {
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().matches("^bg.*\\.(jpg|jpeg|png)$"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        BG_IMAGES.add(img);
                        BG_NAMES.add(f.getName());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ================= RISK CALCULATION HELPERS =================
    static String getMarksRisk(double marks) {
        if (marks < 40) return "HIGH (Fail Risk)";
        if (marks < 60) return "MEDIUM";
        return "LOW (Safe)";
    }

    static String getAttendanceRisk(double attendance) {
        if (attendance < 75) return "HIGH (Detain Risk)";
        if (attendance < 85) return "MEDIUM";
        return "LOW (Safe)";
    }

    static String getCombinedRisk(double marks, double attendance) {
        if (marks < 40 && attendance < 75) return "HIGH (Detain & Fail)";
        if (marks < 40) return "HIGH (Fail Risk)";
        if (attendance < 75) return "HIGH (Detain Risk)";
        if (marks < 60 || attendance < 85) return "MEDIUM RISK";
        return "LOW RISK (Safe)";
    }

    static Color getRiskColor(String riskText) {
        if (riskText.startsWith("HIGH")) return DANGER;
        if (riskText.startsWith("MEDIUM")) return WARNING;
        if (riskText.startsWith("LOW")) return SUCCESS;
        return TEXT_DARK;
    }

    // ================= DATABASE HELPER =================
    static class DB {
        private static final Properties props = new Properties();

        static {
            try (FileInputStream fis = new FileInputStream(".env")) {
                props.load(fis);
            } catch (Exception e) {
                System.out.println("Could not load .env file, falling back to system environment variables.");
            }
        }

        private static String get(String key, String fallback) {
            if (props.containsKey(key)) return props.getProperty(key).trim();
            String envVal = System.getenv(key);
            return envVal != null ? envVal.trim() : fallback;
        }

        private static final String URL = get("DB_URL", "jdbc:mysql://localhost:3306/defaultdb?sslMode=REQUIRED");
        private static final String USER = get("DB_USER", "avnadmin");
        private static final String PASS = get("DB_PASS", "");

        public static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(URL, USER, PASS);
        }
    }

    // ================= CUSTOM UI COMPONENTS =================
    static class BackgroundPanel extends JPanel {
        public void nextBackground(Component parent) {
            if (BG_IMAGES.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "No bg*.jpg or bg*.png images found in your project folder.");
                return;
            }
            currentBgIndex = (currentBgIndex + 1) % BG_IMAGES.size();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (!BG_IMAGES.isEmpty()) {
                BufferedImage img = BG_IMAGES.get(currentBgIndex);
                g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                g2.setColor(new Color(15, 23, 42, 160));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                GradientPaint gp = new GradientPaint(0, 0, new Color(15, 23, 42),
                        getWidth(), getHeight(), new Color(30, 58, 138));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();
        }
    }

    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bgColor;

        public RoundedPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static JButton createStyledButton(String text, Color bg, Color hoverBg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(9, 16, 9, 16));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverBg); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    static void styleTextField(JTextField field) {
        field.setFont(FONT_REGULAR);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(7, 10, 7, 10)
        ));
    }

    static void styleTable(JTable table) {
        table.setFont(FONT_REGULAR);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(TEXT_DARK);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(30, 41, 59));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 38));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer zebraRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);
                setHorizontalAlignment(JLabel.CENTER);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                }
                c.setForeground(TEXT_DARK);
                c.setFont(FONT_REGULAR);
                return c;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(zebraRenderer);
        }
    }

    // ================= LOGIN / REGISTER FRAME =================
    static class LoginFrame extends JFrame {
        private final JTextField userField = new JTextField(18);
        private final JPasswordField passField = new JPasswordField(18);
        private final JComboBox<String> roleBox = new JComboBox<>(new String[]{"STUDENT", "TEACHER"});

        public LoginFrame() {
            setTitle("Student Performance Analysis System");
            setSize(540, 500);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            BackgroundPanel bg = new BackgroundPanel();
            bg.setLayout(new BorderLayout());
            setContentPane(bg);

            // Top bar with Switch BG always visible
            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
            topBar.setOpaque(false);
            JButton bgBtn = createStyledButton("Switch BG", SLATE, SLATE_HOVER);
            bgBtn.addActionListener(e -> bg.nextBackground(this));
            topBar.add(bgBtn);
            bg.add(topBar, BorderLayout.NORTH);

            JPanel centerWrap = new JPanel(new GridBagLayout());
            centerWrap.setOpaque(false);

            RoundedPanel card = new RoundedPanel(22, CARD_BG);
            card.setLayout(new GridBagLayout());
            card.setBorder(new EmptyBorder(28, 36, 28, 36));

            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(6, 4, 6, 4);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.gridx = 0; g.gridwidth = 2;

            JLabel title = new JLabel("Performance Portal", JLabel.CENTER);
            title.setFont(FONT_TITLE);
            title.setForeground(TEXT_DARK);
            g.gridy = 0;
            card.add(title, g);

            JLabel subtitle = new JLabel("Sign in or register to access your dashboard", JLabel.CENTER);
            subtitle.setFont(FONT_SUBTITLE);
            subtitle.setForeground(TEXT_MUTED);
            g.gridy = 1;
            g.insets = new Insets(0, 4, 16, 4);
            card.add(subtitle, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 2;
            JLabel uLbl = new JLabel("Username");
            uLbl.setFont(FONT_BOLD);
            card.add(uLbl, g);

            styleTextField(userField);
            g.gridy = 3;
            g.insets = new Insets(0, 4, 10, 4);
            card.add(userField, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 4;
            JLabel pLbl = new JLabel("Password");
            pLbl.setFont(FONT_BOLD);
            card.add(pLbl, g);

            styleTextField(passField);
            g.gridy = 5;
            g.insets = new Insets(0, 4, 10, 4);
            card.add(passField, g);

            g.insets = new Insets(5, 4, 2, 4);
            g.gridy = 6;
            JLabel rLbl = new JLabel("Role");
            rLbl.setFont(FONT_BOLD);
            card.add(rLbl, g);

            roleBox.setFont(FONT_REGULAR);
            roleBox.setBackground(Color.WHITE);
            g.gridy = 7;
            g.insets = new Insets(0, 4, 18, 4);
            card.add(roleBox, g);

            JPanel btnPanel = new JPanel(new GridLayout(1, 2, 12, 0));
            btnPanel.setOpaque(false);
            JButton loginBtn = createStyledButton("Login", PRIMARY, PRIMARY_HOVER);
            JButton regBtn = createStyledButton("Register", SLATE, SLATE_HOVER);
            btnPanel.add(loginBtn);
            btnPanel.add(regBtn);

            g.gridy = 8;
            g.insets = new Insets(4, 4, 0, 4);
            card.add(btnPanel, g);

            centerWrap.add(card);
            bg.add(centerWrap, BorderLayout.CENTER);

            loginBtn.addActionListener(e -> handleLogin());
            regBtn.addActionListener(e -> handleRegister());
        }

        private void handleLogin() {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            String role = (String) roleBox.getSelectedItem();

            String sql = "SELECT id FROM users WHERE username=? AND password=? AND role=?";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, user);
                ps.setString(2, pass);
                ps.setString(3, role);

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int userId = rs.getInt("id");
                    dispose();
                    if ("TEACHER".equals(role)) {
                        new TeacherDashboard(userId, user).setVisible(true);
                    } else {
                        new StudentDashboard(userId, user).setVisible(true);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid credentials or role.", "Login Failed", JOptionPane.WARNING_MESSAGE);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleRegister() {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            String role = (String) roleBox.getSelectedItem();

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.");
                return;
            }

            String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user);
                ps.setString(2, pass);
                ps.setString(3, role);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                String idMsg = keys.next() ? " (Assigned ID: " + keys.getInt(1) + ")" : "";
                JOptionPane.showMessageDialog(this, "Registered successfully!" + idMsg + "\nYou can now log in.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ================= TEACHER DASHBOARD =================
    static class TeacherDashboard extends JFrame {
        private final JComboBox<String> studentBox = new JComboBox<>();
        private final Map<String, Integer> studentMap = new HashMap<>();
        private final JTextField subjectField = new JTextField(12);
        private final JTextField marksField = new JTextField(6);
        private final JTextField attendanceField = new JTextField(6);
        private final DefaultTableModel tableModel = new DefaultTableModel(
                new String[]{"Record ID", "Student ID", "Student Name", "Subject", "Marks (/100)", "Attendance (%)", "Edit", "Delete"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6 || column == 7;
            }
        };

        public TeacherDashboard(int teacherId, String teacherName) {
            setTitle("Teacher Dashboard - " + teacherName);
            setSize(1060, 600);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            BackgroundPanel bg = new BackgroundPanel();
            bg.setLayout(new BorderLayout(15, 15));
            bg.setBorder(new EmptyBorder(18, 22, 22, 22));
            setContentPane(bg);

            // Top Navbar
            RoundedPanel nav = new RoundedPanel(16, CARD_BG);
            nav.setLayout(new BorderLayout());
            nav.setBorder(new EmptyBorder(12, 20, 12, 20));

            JLabel headerLbl = new JLabel("Teacher Dashboard  |  " + teacherName + " (ID: " + teacherId + ")");
            headerLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
            headerLbl.setForeground(TEXT_DARK);

            JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            navBtns.setOpaque(false);
            JButton bgBtn = createStyledButton("Switch BG", SLATE, SLATE_HOVER);
            bgBtn.addActionListener(e -> bg.nextBackground(this));
            navBtns.add(bgBtn);

            JButton logoutBtn = createStyledButton("Logout", DANGER, DANGER_HOVER);
            logoutBtn.addActionListener(e -> {
                dispose();
                new LoginFrame().setVisible(true);
            });
            navBtns.add(logoutBtn);

            nav.add(headerLbl, BorderLayout.WEST);
            nav.add(navBtns, BorderLayout.EAST);


            JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
            centerPanel.setOpaque(false);

  
            RoundedPanel formCard = new RoundedPanel(16, CARD_BG);
            formCard.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
            formCard.setBorder(new EmptyBorder(8, 12, 8, 12));

            studentBox.setFont(FONT_REGULAR);
            studentBox.setBackground(Color.WHITE);
            styleTextField(subjectField);
            styleTextField(marksField);
            styleTextField(attendanceField);

            JButton addBtn = createStyledButton("+ Save Record", PRIMARY, PRIMARY_HOVER);
            addBtn.addActionListener(e -> addRecord());

            formCard.add(new JLabel("Student:"));
            formCard.add(studentBox);
            formCard.add(new JLabel("Subject:"));
            formCard.add(subjectField);
            formCard.add(new JLabel("Marks (/100):"));
            formCard.add(marksField);
            formCard.add(new JLabel("Attendance (%):"));
            formCard.add(attendanceField);
            formCard.add(addBtn);


            RoundedPanel tableCard = new RoundedPanel(16, CARD_BG);
            tableCard.setLayout(new BorderLayout());
            tableCard.setBorder(new EmptyBorder(12, 12, 12, 12));

            JTable table = new JTable(tableModel);
            styleTable(table);

            table.getColumn("Edit").setCellRenderer(new TableButtonRenderer("Edit", EDIT_COLOR));
            table.getColumn("Edit").setCellEditor(new TableButtonEditor(table, "Edit", EDIT_COLOR, row -> editRecord(table, row)));
            table.getColumn("Edit").setPreferredWidth(80);

            table.getColumn("Delete").setCellRenderer(new TableButtonRenderer("Delete", DANGER));
            table.getColumn("Delete").setCellEditor(new TableButtonEditor(table, "Delete", DANGER, row -> {
                int recordId = (int) table.getValueAt(row, 0);
                deleteRecord(recordId);
            }));
            table.getColumn("Delete").setPreferredWidth(85);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            tableCard.add(scrollPane, BorderLayout.CENTER);

            centerPanel.add(formCard, BorderLayout.NORTH);
            centerPanel.add(tableCard, BorderLayout.CENTER);

            bg.add(nav, BorderLayout.NORTH);
            bg.add(centerPanel, BorderLayout.CENTER);

            loadStudents();
            loadAllPerformance();
        }

        private void loadStudents() {
            studentBox.removeAllItems();
            studentMap.clear();
            String sql = "SELECT id, username FROM users WHERE role='STUDENT' ORDER BY id ASC";
            try (Connection con = DB.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String label = "ID " + id + " - " + rs.getString("username");
                    studentMap.put(label, id);
                    studentBox.addItem(label);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading students: " + ex.getMessage());
            }
        }

        private void loadAllPerformance() {
            tableModel.setRowCount(0);
            String sql = "SELECT p.id AS rec_id, u.id AS stu_id, u.username, p.subject, p.marks, p.attendance " +
                         "FROM performance p JOIN users u ON p.student_id = u.id ORDER BY p.id DESC";
            try (Connection con = DB.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("rec_id"),
                            rs.getInt("stu_id"),
                            rs.getString("username"),
                            rs.getString("subject"),
                            rs.getDouble("marks"),
                            rs.getDouble("attendance"),
                            "Edit",
                            "Delete"
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading records: " + ex.getMessage());
            }
        }

        private void addRecord() {
            String selected = (String) studentBox.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "No students registered yet.");
                return;
            }
            try {
                int studentId = studentMap.get(selected);
                String subject = subjectField.getText().trim();
                double marks = Double.parseDouble(marksField.getText().trim());
                double attendance = Double.parseDouble(attendanceField.getText().trim());

                if (subject.isEmpty() || marks < 0 || marks > 100 || attendance < 0 || attendance > 100) {
                    JOptionPane.showMessageDialog(this, "Enter a subject name and valid values (0 - 100).");
                    return;
                }

                String sql = "INSERT INTO performance (student_id, subject, marks, attendance) VALUES (?, ?, ?, ?)";
                try (Connection con = DB.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, studentId);
                    ps.setString(2, subject);
                    ps.setDouble(3, marks);
                    ps.setDouble(4, attendance);
                    ps.executeUpdate();
                }

                subjectField.setText("");
                marksField.setText("");
                attendanceField.setText("");
                loadAllPerformance();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter valid numbers for marks and attendance.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            }
        }

        private void editRecord(JTable table, int row) {
            int recordId = (int) table.getValueAt(row, 0);
            String studentName = table.getValueAt(row, 2).toString();
            JTextField subF = new JTextField(table.getValueAt(row, 3).toString(), 15);
            JTextField marksF = new JTextField(table.getValueAt(row, 4).toString(), 15);
            JTextField attF = new JTextField(table.getValueAt(row, 5).toString(), 15);

            JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
            panel.add(new JLabel("Student:"));
            panel.add(new JLabel(studentName + " (Record #" + recordId + ")"));
            panel.add(new JLabel("Subject:"));
            panel.add(subF);
            panel.add(new JLabel("Marks (/100):"));
            panel.add(marksF);
            panel.add(new JLabel("Attendance (%):"));
            panel.add(attF);

            int result = JOptionPane.showConfirmDialog(this, panel, "Update Record",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;

            try {
                String subject = subF.getText().trim();
                double marks = Double.parseDouble(marksF.getText().trim());
                double attendance = Double.parseDouble(attF.getText().trim());

                if (subject.isEmpty() || marks < 0 || marks > 100 || attendance < 0 || attendance > 100) {
                    JOptionPane.showMessageDialog(this, "Enter a valid subject and values between 0 and 100.");
                    return;
                }

                String sql = "UPDATE performance SET subject=?, marks=?, attendance=? WHERE id=?";
                try (Connection con = DB.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, subject);
                    ps.setDouble(2, marks);
                    ps.setDouble(3, attendance);
                    ps.setInt(4, recordId);
                    ps.executeUpdate();
                }
                loadAllPerformance();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Marks and attendance must be valid numbers.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error updating record: " + ex.getMessage());
            }
        }

        private void deleteRecord(int recordId) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete Record ID " + recordId + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) return;

            String sql = "DELETE FROM performance WHERE id=?";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, recordId);
                ps.executeUpdate();
                loadAllPerformance();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error deleting record: " + ex.getMessage());
            }
        }

        interface RowAction { void execute(int row); }

        static class TableButtonRenderer extends JButton implements TableCellRenderer {
            public TableButtonRenderer(String label, Color bg) {
                setText(label);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
                setForeground(Color.WHITE);
                setBackground(bg);
                setFocusPainted(false);
                setBorderPainted(false);
                setOpaque(true);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                return this;
            }
        }

        static class TableButtonEditor extends DefaultCellEditor {
            private final JButton button;
            private int currentRow;

            public TableButtonEditor(JTable table, String label, Color bg, RowAction action) {
                super(new JCheckBox());
                button = new JButton(label);
                button.setFont(new Font("Segoe UI", Font.BOLD, 12));
                button.setForeground(Color.WHITE);
                button.setBackground(bg);
                button.setFocusPainted(false);
                button.setBorderPainted(false);
                button.setOpaque(true);
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));

                button.addActionListener(e -> {
                    int row = currentRow;
                    fireEditingStopped();
                    SwingUtilities.invokeLater(() -> action.execute(row));
                });
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                currentRow = row;
                return button;
            }

            @Override
            public Object getCellEditorValue() {
                return button.getText();
            }
        }
    }


    static class StudentDashboard extends JFrame {
        private final DefaultTableModel tableModel = new DefaultTableModel(
                new String[]{"Record ID", "Subject", "Marks (/100)", "Marks Risk", "Attendance (%)", "Attendance Risk", "Subject Risk Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        private final JLabel avgMarksVal = new JLabel("-");
        private final JLabel marksRiskStatus = new JLabel("N/A");
        private final JLabel avgAttVal = new JLabel("-");
        private final JLabel attRiskStatus = new JLabel("N/A");
        private final JLabel overallRiskVal = new JLabel("N/A");
        private final JLabel overallSubVal = new JLabel("Based on average of all subjects");

        public StudentDashboard(int studentId, String studentName) {
            setTitle("Student Dashboard - " + studentName);
            setSize(1080, 620);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            BackgroundPanel bg = new BackgroundPanel();
            bg.setLayout(new BorderLayout(15, 15));
            bg.setBorder(new EmptyBorder(18, 22, 22, 22));
            setContentPane(bg);

            // Top Navbar
            RoundedPanel nav = new RoundedPanel(16, CARD_BG);
            nav.setLayout(new BorderLayout());
            nav.setBorder(new EmptyBorder(12, 20, 12, 20));

            JLabel headerLbl = new JLabel("Student Dashboard  |  " + studentName + " (ID: " + studentId + ")");
            headerLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
            headerLbl.setForeground(TEXT_DARK);

            JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            navBtns.setOpaque(false);
            JButton bgBtn = createStyledButton("Switch BG", SLATE, SLATE_HOVER);
            bgBtn.addActionListener(e -> bg.nextBackground(this));
            navBtns.add(bgBtn);

            JButton logoutBtn = createStyledButton("Logout", DANGER, DANGER_HOVER);
            logoutBtn.addActionListener(e -> {
                dispose();
                new LoginFrame().setVisible(true);
            });
            navBtns.add(logoutBtn);

            nav.add(headerLbl, BorderLayout.WEST);
            nav.add(navBtns, BorderLayout.EAST);

            // Center Panel
            JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
            centerPanel.setOpaque(false);

            // 3 Separate Risk Section Cards (Marks Risk | Attendance Risk | Overall Risk)
            JPanel riskGrid = new JPanel(new GridLayout(1, 3, 15, 0));
            riskGrid.setOpaque(false);
            riskGrid.add(createRiskSectionCard("Marks Risk Section (Avg / 100)", avgMarksVal, marksRiskStatus));
            riskGrid.add(createRiskSectionCard("Attendance Risk Section (Avg %)", avgAttVal, attRiskStatus));
            riskGrid.add(createRiskSectionCard("Overall Risk (Average of All)", overallRiskVal, overallSubVal));

            // Subject-wise Risks & Performance Table Card
            RoundedPanel tableCard = new RoundedPanel(16, CARD_BG);
            tableCard.setLayout(new BorderLayout(0, 8));
            tableCard.setBorder(new EmptyBorder(14, 14, 14, 14));

            JLabel tableTitle = new JLabel("Subject-Wise Performance & Risk Analysis");
            tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
            tableTitle.setForeground(TEXT_DARK);
            tableCard.add(tableTitle, BorderLayout.NORTH);

            JTable table = new JTable(tableModel);
            styleTable(table);

            // Colour-coded renderer for risk columns (3, 5, 6)
            DefaultTableCellRenderer riskCellRenderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected,
                                                               boolean hasFocus, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);
                    setHorizontalAlignment(JLabel.CENTER);
                    if (!isSelected) {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                    }
                    String text = val == null ? "" : val.toString();
                    c.setForeground(getRiskColor(text));
                    c.setFont(FONT_BOLD);
                    return c;
                }
            };
            table.getColumnModel().getColumn(3).setCellRenderer(riskCellRenderer);
            table.getColumnModel().getColumn(5).setCellRenderer(riskCellRenderer);
            table.getColumnModel().getColumn(6).setCellRenderer(riskCellRenderer);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            tableCard.add(scrollPane, BorderLayout.CENTER);

            // Risk Criteria Legend Bar
            RoundedPanel legendCard = new RoundedPanel(14, CARD_BG);
            legendCard.setLayout(new FlowLayout(FlowLayout.CENTER, 16, 8));
            JLabel legendLbl = new JLabel(
                    "<html><b>Risk Rules:</b> &nbsp; " +
                    "<font color='#DC2626'><b>HIGH RISK (Detain / Fail)</b></font>: Marks &lt; 40/100 or Attendance &lt; 75% &nbsp;|&nbsp; " +
                    "<font color='#D97706'><b>MEDIUM</b></font>: Marks 40–59 or Attendance 75–84% &nbsp;|&nbsp; " +
                    "<font color='#16A34A'><b>LOW (Safe)</b></font>: Marks &ge; 60 &amp; Attendance &ge; 85%</html>"
            );
            legendLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            legendLbl.setForeground(TEXT_DARK);
            legendCard.add(legendLbl);

            centerPanel.add(riskGrid, BorderLayout.NORTH);
            centerPanel.add(tableCard, BorderLayout.CENTER);
            centerPanel.add(legendCard, BorderLayout.SOUTH);

            bg.add(nav, BorderLayout.NORTH);
            bg.add(centerPanel, BorderLayout.CENTER);

            loadStudentData(studentId);
        }

        private RoundedPanel createRiskSectionCard(String title, JLabel mainVal, JLabel statusLbl) {
            RoundedPanel card = new RoundedPanel(16, CARD_BG);
            card.setLayout(new GridLayout(3, 1, 0, 4));
            card.setBorder(new EmptyBorder(14, 18, 14, 18));

            JLabel tLbl = new JLabel(title);
            tLbl.setFont(FONT_SUBTITLE);
            tLbl.setForeground(TEXT_MUTED);

            mainVal.setFont(new Font("Segoe UI", Font.BOLD, 20));
            mainVal.setForeground(TEXT_DARK);

            statusLbl.setFont(FONT_BOLD);
            statusLbl.setForeground(TEXT_MUTED);

            card.add(tLbl);
            card.add(mainVal);
            card.add(statusLbl);
            return card;
        }

        private void loadStudentData(int studentId) {
            tableModel.setRowCount(0);
            double totalMarks = 0, totalAttendance = 0;
            int count = 0;

            String sql = "SELECT id, subject, marks, attendance FROM performance WHERE student_id=? ORDER BY id DESC";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    double m = rs.getDouble("marks");
                    double a = rs.getDouble("attendance");

                    String mRisk = getMarksRisk(m);
                    String aRisk = getAttendanceRisk(a);
                    String combined = getCombinedRisk(m, a);

                    tableModel.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("subject"),
                            String.format("%.1f / 100", m),
                            mRisk,
                            String.format("%.1f%%", a),
                            aRisk,
                            combined
                    });

                    totalMarks += m;
                    totalAttendance += a;
                    count++;
                }

                if (count == 0) {
                    avgMarksVal.setText("0.0 / 100");
                    marksRiskStatus.setText("No records yet");
                    avgAttVal.setText("0.0%");
                    attRiskStatus.setText("No records yet");
                    overallRiskVal.setText("N/A");
                    return;
                }

                double avgMarks = totalMarks / count;
                double avgAttendance = totalAttendance / count;

                String overallMRisk = getMarksRisk(avgMarks);
                String overallARisk = getAttendanceRisk(avgAttendance);
                String overallCombined = getCombinedRisk(avgMarks, avgAttendance);

                avgMarksVal.setText(String.format("%.1f / 100", avgMarks));
                marksRiskStatus.setText("Status: " + overallMRisk);
                marksRiskStatus.setForeground(getRiskColor(overallMRisk));

                avgAttVal.setText(String.format("%.1f%%", avgAttendance));
                attRiskStatus.setText("Status: " + overallARisk);
                attRiskStatus.setForeground(getRiskColor(overallARisk));

                overallRiskVal.setText(overallCombined);
                overallRiskVal.setForeground(getRiskColor(overallCombined));
                overallSubVal.setText(String.format("Avg across %d subject(s)", count));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading data: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
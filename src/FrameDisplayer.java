import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class FrameDisplayer extends JFrame {

    //Dummy Data
    String[] column = {"ID","Postcode","CO2(ppm)"};
    String[][] data = {};

    // Panel Setup
    CardLayout cardLayout = new CardLayout();
    JPanel mainPanel = new JPanel(cardLayout);
    JPanel loginPanel = new JPanel(new GridBagLayout());
    JPanel viewPanel = new JPanel(new GridBagLayout());

    // Elements for loginPanel
    JLabel welcomeLabel = new JLabel("Welcome to the CO2 measurement Database!");
    JLabel portLabel = new JLabel("Address: ");
    JLabel loginLabel = new JLabel("ID: ");
    JTextField loginField = new JTextField(20);
    JTextField portField = new JTextField(20);
    JButton confirmButton = new JButton("Confirm");

    // Elements for viewPanel
    JTextPane textPane = new JTextPane();
    JLabel postcodeLabel = new JLabel("Postcode: ");
    JTextField postcodeField = new JTextField(20);
    JLabel co2Label = new JLabel("Co2: ");
    JTextField co2Field = new JTextField(20);
    JButton submitButton = new JButton("Submit");
    JButton backButton = new JButton("Back");

    // Local Setup
    private static final Path LOCAL_CSV = Paths.get(System.getProperty("user.dir"), "submissions_local.csv");
    private final ReentrantLock fileLock = new ReentrantLock();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");

    FrameDisplayer() {

        // Mini form panel for login
        JPanel loginForm = new JPanel(new GridBagLayout());
        GridBagConstraints loginGBC = new GridBagConstraints();
        loginGBC.insets = new Insets(6,6,6,6);
        loginGBC.anchor = GridBagConstraints.WEST;

        loginGBC.gridx = 0;
        loginGBC.gridy = 0;
        loginForm.add(loginLabel, loginGBC);

        loginGBC.gridx = 1;
        loginGBC.fill = GridBagConstraints.HORIZONTAL;
        loginGBC.weightx = 1.0;
        loginForm.add(loginField, loginGBC);

        loginGBC.fill = GridBagConstraints.NONE;
        loginGBC.weightx = 0;
        loginGBC.gridx = 0;
        loginGBC.gridy = 1;
        loginForm.add(portLabel, loginGBC);

        loginGBC.gridx = 1;
        loginGBC.fill = GridBagConstraints.HORIZONTAL;
        loginGBC.weightx = 1.0;
        loginForm.add(portField, loginGBC);

        loginGBC.gridx = 0;
        loginGBC.gridy = 2;
        loginGBC.gridwidth = 2;
        loginGBC.anchor = GridBagConstraints.CENTER;
        loginGBC.fill = GridBagConstraints.NONE;
        loginGBC.weightx = 0;
        loginForm.add(confirmButton, loginGBC);

        // Add welcome label to the top of the login panel
        GridBagConstraints labelGBC = new GridBagConstraints();
        labelGBC.insets = new Insets(10,6,0,6);
        labelGBC.gridx = 0;
        labelGBC.gridy = 0;
        labelGBC.fill = GridBagConstraints.HORIZONTAL;
        labelGBC.weightx = 1.0;
        labelGBC.anchor = GridBagConstraints.NORTH;
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        welcomeLabel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        loginPanel.add(welcomeLabel, labelGBC);

        // Center the loginForm inside the larger loginPanel
        GridBagConstraints miniFormGBC = new GridBagConstraints();
        miniFormGBC.gridx = 0;
        miniFormGBC.gridy = 0;
        miniFormGBC.weightx = 1.0;
        miniFormGBC.weighty = 1.0;
        miniFormGBC.anchor = GridBagConstraints.CENTER;
        miniFormGBC.fill = GridBagConstraints.NONE;
        loginPanel.add(loginForm, miniFormGBC);

        // viewPanel layout
        GridBagConstraints viewGBC = new GridBagConstraints();
        viewGBC.insets = new Insets(6,6,6,6);
        viewGBC.gridx = 0;
        viewGBC.gridy = 0;
        viewGBC.gridwidth = 2;
        viewGBC.fill = GridBagConstraints.BOTH;
        viewGBC.weightx = 1.0;
        viewGBC.weighty = 1.0;
        JScrollPane scroll = new JScrollPane(textPane);
        textPane.setEditable(false);
        viewPanel.add(scroll, viewGBC);

        viewGBC.gridwidth = 1;
        viewGBC.weighty = 0;
        viewGBC.fill = GridBagConstraints.NONE;

        viewGBC.gridx = 0;
        viewGBC.gridy = 1;
        viewPanel.add(postcodeLabel, viewGBC);

        viewGBC.gridx = 1;
        viewGBC.fill = GridBagConstraints.HORIZONTAL;
        viewGBC.weightx = 1.0;
        viewPanel.add(postcodeField, viewGBC);

        viewGBC.fill = GridBagConstraints.NONE;
        viewGBC.weightx = 0;
        viewGBC.gridx = 0;
        viewGBC.gridy = 2;
        viewPanel.add(co2Label, viewGBC);

        viewGBC.gridx = 1;
        viewGBC.fill = GridBagConstraints.HORIZONTAL;
        viewGBC.weightx = 1.0;
        viewPanel.add(co2Field, viewGBC);

        viewGBC.fill = GridBagConstraints.NONE;
        viewGBC.weightx = 0;
        viewGBC.gridx = 0;
        viewGBC.gridy = 3;
        viewPanel.add(submitButton, viewGBC);

        viewGBC.gridx = 1;
        viewPanel.add(backButton, viewGBC);

        // Add panels to main
        mainPanel.add(loginPanel, "loginPanel");
        mainPanel.add(viewPanel, "viewPanel");

        // Button events
        confirmButton.addActionListener(e -> {
            String id = loginField.getText().trim();
            String port = portField.getText().trim();
            if (id.isEmpty() || port.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please fill in both ID and Address fields.",
                        "Missing input",
                        JOptionPane.WARNING_MESSAGE);
                if (id.isEmpty()) {
                    loginField.requestFocusInWindow();
                } else {
                    portField.requestFocusInWindow();
                }
                return;
            }
            // load local CSV into text pane when moving to view
            readLocalCsvToTextPane();
            cardLayout.show(mainPanel, "viewPanel");
        });

        submitButton.addActionListener(e -> {
            String postcode = postcodeField.getText().trim();
            String co2Text = co2Field.getText().trim();

            // basic validation
            if (postcode.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a postcode.",
                        "Missing input",
                        JOptionPane.WARNING_MESSAGE);
                postcodeField.requestFocusInWindow();
                return;
            }
            double co2;
            try {
                co2 = Double.parseDouble(co2Text);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "CO2 must be numeric (ppm).",
                        "Invalid input",
                        JOptionPane.WARNING_MESSAGE);
                co2Field.requestFocusInWindow();
                return;
            }
            if (co2 < 0.0 || co2 > 20000.0) {
                JOptionPane.showMessageDialog(this,
                        "CO2 out of expected range (0 - 20000).",
                        "Invalid input",
                        JOptionPane.WARNING_MESSAGE);
                co2Field.requestFocusInWindow();
                return;
            }

            // timestamp on submit (UK time) in format dd-MM-yy HH:mm:ss
            String timestamp = ZonedDateTime.now(ZoneId.of("Europe/London")).format(TS_FMT);
            String userId = loginField.getText().trim();

            try {
                appendToLocalCsv(userId, timestamp, postcode, String.valueOf(co2));
                readLocalCsvToTextPane();
                JOptionPane.showMessageDialog(this,
                        "Saved locally with timestamp: " + timestamp,
                        "Saved",
                        JOptionPane.INFORMATION_MESSAGE);
                // clear input fields
                postcodeField.setText("");
                co2Field.setText("");
            } catch (IOException ioEx) {
                JOptionPane.showMessageDialog(this,
                        "Failed to write local CSV: " + ioEx.getMessage(),
                        "Write error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        backButton.addActionListener(e -> {
            cardLayout.show(mainPanel, "loginPanel");
        });

        // Add and changes to main panel
        add(mainPanel);

        setTitle("Database Program");
        setPreferredSize(new Dimension(1000, 700));
        setResizable(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

    }

    // CSV writer
    private void appendToLocalCsv(String userId, String timestamp, String postcode, String co2) throws IOException {
        fileLock.lock();
        try {
            boolean exists = Files.exists(LOCAL_CSV);
            try (BufferedWriter bw = Files.newBufferedWriter(LOCAL_CSV, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (!exists) {
                    bw.write("User ID,Timestamp,Postcode,CO2(ppm)");
                    bw.newLine();
                }
                bw.write(escapeCsv(userId));
                bw.write(",");
                bw.write(escapeCsv(timestamp));
                bw.write(",");
                bw.write(escapeCsv(postcode));
                bw.write(",");
                bw.write(escapeCsv(co2));
                bw.newLine();
                bw.flush();
            }
        } finally {
            fileLock.unlock();
        }
    }

    // CSV reader fill textPane
    private void readLocalCsvToTextPane() {
        try {
            if (!Files.exists(LOCAL_CSV)) {
                textPane.setText("");
                return;
            }
            List<String> lines = Files.readAllLines(LOCAL_CSV, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                sb.append(l).append(System.lineSeparator());
            }
            textPane.setText(sb.toString());
        } catch (IOException ex) {
            textPane.setText("Error reading local CSV: " + ex.getMessage());
        }
    }

    // CSV formatting
    private String escapeCsv(String s) {
        if (s == null) return "";
        String value = s.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value + "\"";
        }
        return value;
    }

}
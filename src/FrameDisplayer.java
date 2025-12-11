import java.awt.*;
import java.io.*;
import java.net.Socket;
import javax.swing.*;

public class FrameDisplayer extends JFrame {

    // Panel Setup
    CardLayout cardLayout = new CardLayout();
    JPanel mainPanel = new JPanel(cardLayout);
    JPanel loginPanel = new JPanel(new GridBagLayout());
    JPanel viewPanel = new JPanel(new GridBagLayout());

    // Elements for loginPanel
    JLabel welcomeLabel = new JLabel("Welcome to the CO2 measurement Database!");
    JLabel portLabel = new JLabel("Port: ");
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

    // Server Connection
    private Socket serverSocket;
    private BufferedReader serverIn;
    private PrintWriter serverOut;
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;
    private Thread serverReaderThread;
    private volatile boolean readerRunning = false;

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
            String address = portField.getText().trim();
            if (id.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter an ID.",
                        "Missing input",
                        JOptionPane.WARNING_MESSAGE);
                loginField.requestFocusInWindow();
                return;
            }
            try {
                String[] hp = parseAddress(address);
                String host = hp[0];
                int port = Integer.parseInt(hp[1]);
                connectToServer(host, port);
                textPane.setText("Connected to server at " + host + ":" + port + "\n\nReady to submit CO2 measurements.");
                cardLayout.show(mainPanel, "viewPanel");
            } catch (IOException ioEx) {
                JOptionPane.showMessageDialog(this,
                        "Failed to connect to server: " + ioEx.getMessage(),
                        "Connection error",
                        JOptionPane.ERROR_MESSAGE);
                portField.requestFocusInWindow();
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this,
                        "Port must be a number.",
                        "Invalid port",
                        JOptionPane.WARNING_MESSAGE);
                portField.requestFocusInWindow();
            }
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

            String userId = loginField.getText().trim();
            try {
                sendToServer(userId, postcode, String.valueOf(co2));
                // clear input fields
                postcodeField.setText("");
                co2Field.setText("");
            } catch (IOException ioEx) {
                JOptionPane.showMessageDialog(this,
                        "Failed to send data to server: " + ioEx.getMessage(),
                        "Send error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        backButton.addActionListener(e -> {
            try {
                disconnectFromServer();
            } catch (IOException ioEx) {
                System.err.println("Error disconnecting: " + ioEx.getMessage());
            }
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

    // Server connection
    private void connectToServer(String host, int port) throws IOException {
        serverSocket = new Socket(host, port);
        serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
        serverOut = new PrintWriter(serverSocket.getOutputStream(), true);

        // start background reader to handle server messages (CONNECTED, DATA_START, SUCCESS, ERROR, SERVER_FULL)
        readerRunning = true;
        serverReaderThread = new Thread(() -> {
            try {
                String line;
                while (readerRunning && (line = serverIn.readLine()) != null) {
                    if ("SERVER_FULL".equals(line)) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this,
                                    "Server has reached maximum capacity (4 clients). Connection rejected.",
                                    "Server Full",
                                    JOptionPane.WARNING_MESSAGE);
                            try {
                                disconnectFromServer();
                            } catch (IOException ignored) {
                            }
                            cardLayout.show(mainPanel, "loginPanel");
                        });
                        break;
                    }
                    if ("DATA_START".equals(line)) {
                        StringBuilder sb = new StringBuilder();
                        while ((line = serverIn.readLine()) != null && !"DATA_END".equals(line)) {
                            sb.append(line).append(System.lineSeparator());
                        }
                        final String csvText = sb.toString();
                        SwingUtilities.invokeLater(() -> textPane.setText(csvText));
                    }
                    if (line.startsWith("SUCCESS:")) {
                        String ts = line.substring(8);
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                                "Data saved with timestamp: " + ts,
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE));

                    }
                    if (line.startsWith("ERROR:")) {
                        String err = line.substring(6);
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                                "Server error: " + err,
                                "Server error",
                                JOptionPane.ERROR_MESSAGE));

                    }
                    // ignore other lines (CONNECTED, etc.)
                }
            } catch (IOException e) {
                if (readerRunning) System.err.println("Server reader error: " + e.getMessage());
            }
        });
        serverReaderThread.setDaemon(true);
        serverReaderThread.start();
    }

    // Send data to server
    private void sendToServer(String userId, String postcode, String co2) throws IOException {
        if (serverOut == null) {
            throw new IOException("Not connected to server");
        }

        // Format: USER_ID|POSTCODE|CO2
        String data = userId + "|" + postcode + "|" + co2;
        serverOut.println(data);
        // responses (SUCCESS/ERROR and DATA_START/DATA_END) are handled asynchronously
    }

    // (No synchronous CSV reader — client uses background reader thread.)

    // Disconnect from server
    private void disconnectFromServer() throws IOException {
        readerRunning = false;
        if (serverOut != null) {
            serverOut.println("DISCONNECT");
            serverOut.flush();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }
        if (serverReaderThread != null) {
            serverReaderThread.interrupt();
            serverReaderThread = null;
        }
    }

    // Parse address input supporting: "host", "host:port", or "port"
    private String[] parseAddress(String address) {
        if (address == null || address.isEmpty()) return new String[]{DEFAULT_HOST, String.valueOf(DEFAULT_PORT)};
        if (address.contains(":")) {
            String[] parts = address.split(":", 2);
            String host = parts[0].isEmpty() ? DEFAULT_HOST : parts[0];
            return new String[]{host, parts[1]};
        }
        try {
            int p = Integer.parseInt(address);
            return new String[]{DEFAULT_HOST, String.valueOf(p)};
        } catch (NumberFormatException ex) {
            return new String[]{address, String.valueOf(DEFAULT_PORT)};
        }
    }
}
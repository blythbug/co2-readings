import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseServer {
    private static final int PORT = 5000;
    private static final int MAX_CLIENTS = 4;
    private static final Path SERVER_CSV = Paths.get(System.getProperty("user.dir"), "submissions_server.csv");
    private static final ReentrantLock fileLock = new ReentrantLock();
    private final List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");
    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
    private final Semaphore clientSlots = new Semaphore(MAX_CLIENTS);
    private final AtomicInteger clientCounter = new AtomicInteger(0);

    public static void main(String[] args) {
        DatabaseServer server = new DatabaseServer();
        server.start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT + ". Listening for clients...");
            initializeCsvFile();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                if (!clientSlots.tryAcquire()) {
                    try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
                        out.println("SERVER_FULL");
                    } catch (IOException ignored) {
                    } finally {
                        clientSocket.close();
                    }
                    continue;
                }

                int id = clientCounter.incrementAndGet();
                System.out.println("Client " + id + " connected.");
                threadPool.execute(new ClientHandler(clientSocket, id));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private void initializeCsvFile() {
        fileLock.lock();
        try {
            if (!Files.exists(SERVER_CSV)) {
                try (BufferedWriter bw = Files.newBufferedWriter(SERVER_CSV, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE)) {
                    bw.write("User ID,Timestamp,Postcode,CO2(ppm)");
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error initializing CSV file: " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
    }

    public class ClientHandler implements Runnable {
        private final Socket socket;
        private final int clientId;

        public ClientHandler(Socket socket, int clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            BufferedReader in = null;
            PrintWriter out = null;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // register writer so broadcasts reach this client
                clientWriters.add(out);

                out.println("CONNECTED");
                System.out.println("Client " + clientId + " authenticated.");
                // send current CSV contents to client
                sendCsvToClient(out);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if ("DISCONNECT".equals(inputLine)) {
                        System.out.println("Client " + clientId + " disconnected.");
                        break;
                    }

                    // Parse incoming data: USER_ID|POSTCODE|CO2
                    String[] parts = inputLine.split("\\|");
                    if (parts.length == 3) {
                        String userId = parts[0];
                        String postcode = parts[1];
                        String co2 = parts[2];
                        String timestamp = ZonedDateTime.now(ZoneId.of("Europe/London")).format(TS_FMT);

                        try {
                            appendToCsv(userId, timestamp, postcode, co2);
                            // reply to submitting client
                            out.println("SUCCESS:" + timestamp);
                            // broadcast updated CSV to all clients
                            broadcastCsv();
                            System.out.println("Data saved from Client " + clientId + ": " + userId + ", " + postcode + ", " + co2);
                        } catch (IOException e) {
                            out.println("ERROR:" + e.getMessage());
                            System.err.println("Error saving data from Client " + clientId + ": " + e.getMessage());
                        }
                    } else {
                        out.println("ERROR:Invalid format");
                    }
                }
            } catch (IOException e) {
                System.err.println("Client " + clientId + " error: " + e.getMessage());
            } finally {
                if (out != null) clientWriters.remove(out);
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket for Client " + clientId + ": " + e.getMessage());
                }
                clientSlots.release();
                System.out.println("Client " + clientId + " slot released.");
            }
        }

        private void sendCsvToClient(PrintWriter out) {
            fileLock.lock();
            try {
                out.println("DATA_START");
                if (Files.exists(SERVER_CSV)) {
                    try {
                        for (String line : Files.readAllLines(SERVER_CSV, StandardCharsets.UTF_8)) {
                            out.println(line);
                        }
                    } catch (IOException e) {
                        out.println("ERROR:Failed to read server CSV");
                    }
                }
                out.println("DATA_END");
            } finally {
                fileLock.unlock();
            }
        }

        private void broadcastCsv() {
            fileLock.lock();
            try {
                for (PrintWriter pw : clientWriters) {
                    try {
                        pw.println("DATA_START");
                        if (Files.exists(SERVER_CSV)) {
                            for (String line : Files.readAllLines(SERVER_CSV, StandardCharsets.UTF_8)) {
                                pw.println(line);
                            }
                        }
                        pw.println("DATA_END");
                    } catch (Exception e) {
                        // remove writer that failed
                        clientWriters.remove(pw);
                    }
                }
            } finally {
                fileLock.unlock();
            }
        }

        private void appendToCsv(String userId, String timestamp, String postcode, String co2) throws IOException {
            fileLock.lock();
            try {
                try (BufferedWriter bw = Files.newBufferedWriter(SERVER_CSV, StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND)) {
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

        private String escapeCsv(String s) {
            if (s == null) return "";
            String value = s.replace("\"", "\"\"");
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                return "\"" + value + "\"";
            }
            return value;
        }
    }
}


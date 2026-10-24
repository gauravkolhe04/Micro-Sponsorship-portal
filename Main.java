import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final int PORT = getServerPort();
    private static final long VERIFICATION_CODE_TTL_MILLIS = 10 * 60 * 1000;
    private static final long VERIFICATION_RESEND_COOLDOWN_MILLIS = 60 * 1000;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final File LEGACY_DATA_FILE = new File(System.getProperty("user.dir"), "portal-data.ser");
    private static final File DATA_FILE = getLocalDataFile();

    // In-memory data stores
    static final List<Student> students = new ArrayList<>();
    static final List<Sponsor> sponsors = new ArrayList<>();
    static final List<Project> projects = new ArrayList<>();
    static final List<Sponsorship> sponsorships = new ArrayList<>();
    private static final Map<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();

    private static int getServerPort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

    public static void main(String[] args) {
        try {
            initializeDataFile();
            loadData();

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            server.createContext("/", new StaticFileHandler());
            server.createContext("/projects", new ProjectsHandler());
            server.createContext("/createProject", new CreateProjectHandler());
            server.createContext("/updateProject", new UpdateProjectHandler());
            server.createContext("/deleteProject", new DeleteProjectHandler());
            server.createContext("/sponsor", new SponsorHandler());
            server.createContext("/addProjectUpdate", new AddUpdateHandler());
            server.createContext("/login", new LoginHandler());
            server.createContext("/register", new RegisterHandler());
            server.createContext("/verifyEmail", new VerifyEmailHandler());
            server.createContext("/resendVerification", new ResendVerificationHandler());
            server.createContext("/stats", new StatsHandler());
            server.createContext("/students", new StudentsHandler());
            server.createContext("/sponsors", new SponsorsHandler());
            server.createContext("/contact", new ContactHandler());

            server.start();
            System.out.println("==================================================================");
            System.out.println(" Micro-Sponsorship Portal Server Started");
            System.out.println(" Running on Port: " + PORT);
            System.out.println(" Status: Connected to Supabase.");
            System.out.println("==================================================================");
        } catch (BindException e) {
            System.err.println("Cannot start backend: port " + PORT + " is already in use.");
        } catch (IOException e) {
            System.err.println("Cannot start backend: " + e.getMessage());
        }
    }

    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, apikey");
    }

    private static void seedData() {
        students.add(new Student("STU-101", "Gaurav Kolhe", "gau@gmail.com", "Gau", "RCPIT", "Information Technology", "2nd Year", "Java, Html, CSS", ""));

        String seededDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        addSeedProject(
                "PRJ-101",
                "Smart Campus AI Copilot",
                "Artificial Intelligence",
                "A conversational campus assistant that helps students discover events, timetable slots, and career guidance using AI-powered recommendations.",
                1250,
                1485,
                "Python, TensorFlow, NLP, React",
                "8 weeks",
                "3 students",
                "https://images.unsplash.com/photo-1531482615713-2afd69097998?auto=format&fit=crop&w=800&q=80",
                "https://github.com",
                "https://demo.example.com",
                "STU-101",
                "Gaurav Kolhe",
                "gau@gmail.com",
                seededDate,
                "Funded"
        );

        addSeedProject(
                "PRJ-102",
                "Vision-Based Attendance System",
                "Education Technology",
                "Face recognition and attendance automation system for classrooms with privacy-safe processing, attendance dashboards, and anomaly detection.",
                1800,
                1200,
                "OpenCV, Python, Flask, Edge AI",
                "10 weeks",
                "4 students",
                "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=800&q=80",
                "https://github.com",
                "https://demo.example.com",
                "STU-102",
                "Aisha Shah",
                "aisha@college.edu",
                seededDate,
                "Active"
        );
    }

    private static void addSeedProject(String id, String title, String category, String description,
                                      double fundingGoal, double currentFunding, String requiredSkills,
                                      String duration, String teamMembers, String imageUrl, String githubUrl,
                                      String demoUrl, String studentId, String studentName, String studentEmail,
                                      String createdAt, String status) {
        Project project = new Project(id, title, category, description, fundingGoal, currentFunding,
                requiredSkills, duration, teamMembers, imageUrl, githubUrl, demoUrl,
                studentId, studentName, studentEmail, createdAt, status);

        project.getUpdates().add(new ProjectUpdate(
                "UPD-" + System.currentTimeMillis() + "-" + id,
                id,
                "Project Launch",
                "Initial prototype and project goals are now visible to the student community.",
                createdAt
        ));

        projects.add(project);
    }

    private static File getLocalDataFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        File dataDirectory = localAppData == null || localAppData.isBlank()
                ? new File(System.getProperty("user.home"), ".micro-sponsorship-portal")
                : new File(localAppData, "MicroSponsorshipPortal");
        return new File(dataDirectory, "portal-data.ser");
    }

    private static void initializeDataFile() throws IOException {
        File dataDirectory = DATA_FILE.getParentFile();
        Files.createDirectories(dataDirectory.toPath());
        if (!DATA_FILE.exists() && LEGACY_DATA_FILE.exists()) {
            Files.copy(LEGACY_DATA_FILE.toPath(), DATA_FILE.toPath());
        }
    }

    private static synchronized void loadData() throws IOException {
        String storedData = loadSupabaseData();
        if (storedData == null) {
            if (DATA_FILE.exists()) {
                loadLocalData();
            } else {
                seedData();
            }
            normalizeDemoStudentCredentials();
            saveData();
            return;
        }

        byte[] serializedData;
        try {
            serializedData = Base64.getDecoder().decode(storedData);
        } catch (IllegalArgumentException e) {
            throw new IOException("Supabase returned invalid portal data.", e);
        }
        boolean dataChanged;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedData))) {
            Object stored = input.readObject();
            if (!(stored instanceof StoredData data)) {
                throw new IOException("Supabase contains an unsupported portal data format.");
            }
            students.clear();
            students.addAll(data.students);
            sponsors.clear();
            sponsors.addAll(data.sponsors);
            projects.clear();
            projects.addAll(data.projects);
            sponsorships.clear();
            sponsorships.addAll(data.sponsorships);
            dataChanged = normalizeDemoStudentCredentials();
        } catch (ClassNotFoundException e) {
            throw new IOException("Supabase contains unknown portal data types.", e);
        }
        if (dataChanged) {
            saveData();
        }
    }

    private static void loadLocalData() throws IOException {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            Object stored = input.readObject();
            if (!(stored instanceof StoredData data)) {
                throw new IOException("The local backup file has an unsupported format.");
            }
            students.clear();
            students.addAll(data.students);
            sponsors.clear();
            sponsors.addAll(data.sponsors);
            projects.clear();
            projects.addAll(data.projects);
            sponsorships.clear();
            sponsorships.addAll(data.sponsorships);
        } catch (ClassNotFoundException e) {
            throw new IOException("The local backup file contains unknown data types.", e);
        }
    }

    private static boolean normalizeDemoStudentCredentials() {
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            if ("gau@gmail.com".equalsIgnoreCase(student.getEmail())
                    && !"Gau".equals(student.getPassword())) {
                students.set(i, new Student(
                        student.getId(), student.getName(), student.getEmail(), "Gau",
                        student.getUniversity(), student.getDepartment(), student.getYear(),
                        student.getSkills(), student.getBio()
                ));
                return true;
            }
        }
        return false;
    }

    private static synchronized void saveData() throws IOException {
        StoredData data = new StoredData(students, sponsors, projects, sponsorships);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(data);
        }
        saveSupabaseData(Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }

    private static String loadSupabaseData() throws IOException {
        SupabaseConfig config = getSupabaseConfig();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.url + "/rest/v1/portal_state?id=eq.main&select=data"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("apikey", config.serviceRoleKey);

        if (config.serviceRoleKey.startsWith("sb_secret_")) {
            builder.header("X-Supabase-Key", config.serviceRoleKey);
        } else {
            builder.header("Authorization", "Bearer " + config.serviceRoleKey);
        }

        HttpResponse<String> response = sendSupabaseRequest(builder.GET().build());
        if (response.statusCode() != 200) {
            throw supabaseError("read", response);
        }
        Matcher matcher = Pattern.compile("\"data\"\\s*:\\s*\"([A-Za-z0-9+/=]*)\"").matcher(response.body());
        if (response.body().trim().equals("[]")) {
            return null;
        }
        if (!matcher.find()) {
            throw new IOException("Supabase returned a portal_state row without valid data.");
        }
        return matcher.group(1);
    }

    private static void saveSupabaseData(String data) throws IOException {
        SupabaseConfig config = getSupabaseConfig();
        String body = "{\"id\":\"main\",\"data\":\"" + data + "\"}";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.url + "/rest/v1/portal_state?on_conflict=id"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .header("apikey", config.serviceRoleKey);

        if (config.serviceRoleKey.startsWith("sb_secret_")) {
            builder.header("X-Supabase-Key", config.serviceRoleKey);
        } else {
            builder.header("Authorization", "Bearer " + config.serviceRoleKey);
        }

        HttpResponse<String> response = sendSupabaseRequest(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw supabaseError("save", response);
        }
    }

    private static void saveContactMessageToSupabase(String name, String email, String subject, String message) throws IOException {
        SupabaseConfig config = getSupabaseConfig();
        String body = String.format(
            "{\"name\":\"%s\",\"email\":\"%s\",\"subject\":\"%s\",\"message\":\"%s\"}",
            escapeJson(name), escapeJson(email), escapeJson(subject), escapeJson(message)
        );
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.url + "/rest/v1/contact_messages"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .header("apikey", config.serviceRoleKey);

        if (config.serviceRoleKey.startsWith("sb_secret_")) {
            builder.header("X-Supabase-Key", config.serviceRoleKey);
        } else {
            builder.header("Authorization", "Bearer " + config.serviceRoleKey);
        }

        HttpResponse<String> response = sendSupabaseRequest(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw supabaseError("save contact message", response);
        }
    }

    private static HttpResponse<String> sendSupabaseRequest(HttpRequest request) throws IOException {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase request was interrupted.", e);
        }
    }

    private static IOException supabaseError(String operation, HttpResponse<String> response) {
        return new IOException("Could not " + operation + " in Supabase (HTTP "
                + response.statusCode() + "): " + response.body());
    }

    private static SupabaseConfig getSupabaseConfig() throws IOException {
        String url = System.getenv("SUPABASE_URL");
        String serviceRoleKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY");
        if (url == null || url.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IOException("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in the backend environment.");
        }
        String normalizedUrl = url.trim().replaceAll("/+$", "");
        return new SupabaseConfig(normalizedUrl, serviceRoleKey.trim());
    }

    private static final class SupabaseConfig {
        private final String url;
        private final String serviceRoleKey;

        private SupabaseConfig(String url, String serviceRoleKey) {
            this.url = url;
            this.serviceRoleKey = serviceRoleKey;
        }
    }

    private static class StoredData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Student> students;
        private final List<Sponsor> sponsors;
        private final List<Project> projects;
        private final List<Sponsorship> sponsorships;

        private StoredData(List<Student> students, List<Sponsor> sponsors, List<Project> projects, List<Sponsorship> sponsorships) {
            this.students = new ArrayList<>(students);
            this.sponsors = new ArrayList<>(sponsors);
            this.projects = new ArrayList<>(projects);
            this.sponsorships = new ArrayList<>(sponsorships);
        }
    }

    // Handlers
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String fileName;
            String contentType;
            if (path == null || "/".equals(path) || "/index.html".equals(path)) {
                fileName = "index.html";
                contentType = "text/html; charset=UTF-8";
            } else if ("/style.css".equals(path)) {
                fileName = "style.css";
                contentType = "text/css; charset=UTF-8";
            } else if ("/script.js".equals(path)) {
                fileName = "script.js";
                contentType = "application/javascript; charset=UTF-8";
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }

            File file = new File(System.getProperty("user.dir"), fileName);
            if (!file.isFile()) {
                sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            boolean headRequest = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
            exchange.sendResponseHeaders(200, headRequest ? -1 : bytes.length);
            if (!headRequest) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }

    static class ContactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String name = data.getOrDefault("name", "").trim();
                String email = data.getOrDefault("email", "").trim();
                String subject = data.getOrDefault("subject", "").trim();
                String message = data.getOrDefault("message", "").trim();

                if (name.isEmpty() || email.isEmpty() || message.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Name, email, and message are required.\"}");
                    return;
                }

                try {
                    saveContactMessageToSupabase(name, email, subject, message);
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Message sent successfully!\"}");
                } catch (Exception ex) {
                    sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to save message: " + escapeJson(ex.getMessage()) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class ProjectsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < projects.size(); i++) {
                    sb.append(projects.get(i).toJson());
                    if (i < projects.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString());
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class CreateProjectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String title = data.getOrDefault("title", "").trim();
                String category = data.getOrDefault("category", "General").trim();
                String description = data.getOrDefault("description", "").trim();
                double fundingGoal = parseDouble(data.get("fundingGoal"), 0.0);
                String requiredSkills = data.getOrDefault("requiredSkills", "").trim();
                String duration = data.getOrDefault("duration", "").trim();
                String teamMembers = data.getOrDefault("teamMembers", "").trim();
                String imageUrl = data.getOrDefault("imageUrl", "").trim();
                String githubUrl = data.getOrDefault("githubUrl", "").trim();
                String demoUrl = data.getOrDefault("demoUrl", "").trim();
                String studentId = data.getOrDefault("studentId", "STU-" + (students.size() + 100)).trim();
                String studentName = data.getOrDefault("studentName", "Student Innovator").trim();
                String studentEmail = data.getOrDefault("studentEmail", "").trim();

                if (title.isEmpty() || description.isEmpty() || fundingGoal <= 0) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Title, description, and positive funding goal are required.\"}");
                    return;
                }

                for (Project p : projects) {
                    if (p.getTitle().equalsIgnoreCase(title)) {
                        sendResponse(exchange, 400, "{\"success\":false,\"message\":\"A project with this title already exists. Please choose a unique name.\"}");
                        return;
                    }
                }

                if (imageUrl.isEmpty()) {
                    imageUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80";
                }

                String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String newId = "PRJ-" + String.format("%03d", projects.size() + 1);

                Project newProject = new Project(
                        newId, title, category, description, fundingGoal, 0.0,
                        requiredSkills, duration, teamMembers, imageUrl, githubUrl,
                        demoUrl, studentId, studentName, studentEmail, dateStr, "Active"
                );

                newProject.getUpdates().add(new ProjectUpdate(
                        "UPD-" + (System.currentTimeMillis() % 10000),
                        newId,
                        "Project Created & Live",
                        "Project officially launched on Micro-Sponsorship Portal! Open for community backing and mentorship.",
                        dateStr
                ));

                projects.add(0, newProject);
                saveData();

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project created successfully!\",\"project\":" + newProject.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class UpdateProjectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String id = data.get("id");
                if (id == null || id.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project ID is required.\"}");
                    return;
                }

                Project target = null;
                for (Project p : projects) {
                    if (p.getId().equalsIgnoreCase(id)) {
                        target = p;
                        break;
                    }
                }

                if (target == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                    return;
                }

                if (data.containsKey("title") && !data.get("title").trim().isEmpty()) target.setTitle(data.get("title").trim());
                if (data.containsKey("category") && !data.get("category").trim().isEmpty()) target.setCategory(data.get("category").trim());
                if (data.containsKey("description") && !data.get("description").trim().isEmpty()) target.setDescription(data.get("description").trim());
                if (data.containsKey("fundingGoal")) {
                    double g = parseDouble(data.get("fundingGoal"), target.getFundingGoal());
                    if (g > 0 && g >= target.getCurrentFunding()) target.setFundingGoal(g);
                    else if (g > 0) {
                        sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Funding goal cannot be lower than the amount already raised.\"}");
                        return;
                    }
                }
                if (data.containsKey("requiredSkills")) target.setRequiredSkills(data.get("requiredSkills").trim());
                if (data.containsKey("duration")) target.setDuration(data.get("duration").trim());
                if (data.containsKey("teamMembers")) target.setTeamMembers(data.get("teamMembers").trim());
                if (data.containsKey("imageUrl") && !data.get("imageUrl").trim().isEmpty()) target.setImageUrl(data.get("imageUrl").trim());
                if (data.containsKey("githubUrl")) target.setGithubUrl(data.get("githubUrl").trim());
                if (data.containsKey("demoUrl")) target.setDemoUrl(data.get("demoUrl").trim());

                saveData();

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project updated successfully!\",\"project\":" + target.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class DeleteProjectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);
                String id = data.get("id");

                if (id == null || id.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project ID is required.\"}");
                    return;
                }

                boolean removed = projects.removeIf(p -> p.getId().equalsIgnoreCase(id));
                sponsorships.removeIf(s -> s.getProjectId().equalsIgnoreCase(id));

                if (removed) {
                    saveData();
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project deleted successfully.\"}");
                } else {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class SponsorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String projectId = data.get("projectId");
                String sponsorName = data.getOrDefault("sponsorName", "Anonymous Backer").trim();
                String sponsorEmail = data.getOrDefault("sponsorEmail", "sponsor@example.com").trim();
                String sponsorId = data.getOrDefault("sponsorId", "SPO-" + (sponsors.size() + 201)).trim();
                String message = data.getOrDefault("message", "Keep up the great engineering work!").trim();
                double amount = parseDouble(data.get("amount"), 0.0);

                if (projectId == null || projectId.isEmpty() || amount <= 0) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid project ID and positive sponsorship amount are required.\"}");
                    return;
                }

                Project target = null;
                for (Project p : projects) {
                    if (p.getId().equalsIgnoreCase(projectId)) {
                        target = p;
                        break;
                    }
                }

                if (target == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                    return;
                }

                if (target.getCurrentFunding() >= target.getFundingGoal()) {
                    sendResponse(exchange, 409, "{\"success\":false,\"message\":\"This project is already fully funded and cannot accept more sponsorships.\"}");
                    return;
                }

                if (amount > target.getFundingGoal() - target.getCurrentFunding()) {
                    sendResponse(exchange, 409, "{\"success\":false,\"message\":\"Sponsorship exceeds this project's remaining funding limit.\"}");
                    return;
                }

                String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String spId = "SP-" + (sponsorships.size() + 101);
                Sponsorship sp = new Sponsorship(spId, target.getId(), target.getTitle(), sponsorId, sponsorName, sponsorEmail, amount, message, dateStr);

                sponsorships.add(sp);
                target.getSponsorships().add(sp);
                target.setCurrentFunding(target.getCurrentFunding() + amount);

                if (target.getCurrentFunding() >= target.getFundingGoal()) {
                    target.setStatus("Funded");
                }

                saveData();

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Thank you! Sponsorship was processed.\",\"sponsorship\":" + sp.toJson() + ",\"project\":" + target.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class AddUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String projectId = data.get("projectId");
                String title = data.getOrDefault("title", "").trim();
                String description = data.getOrDefault("description", "").trim();
                String dateStr = data.getOrDefault("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date())).trim();

                if (projectId == null || title.isEmpty() || description.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project ID, update title, and description are required.\"}");
                    return;
                }

                Project target = null;
                for (Project p : projects) {
                    if (p.getId().equalsIgnoreCase(projectId)) {
                        target = p;
                        break;
                    }
                }

                if (target == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                    return;
                }

                String updateId = "UPD-" + (target.getUpdates().size() + 1);
                ProjectUpdate update = new ProjectUpdate(updateId, target.getId(), title, description, dateStr);
                target.getUpdates().add(0, update);
                saveData();

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project update posted successfully!\",\"update\":" + update.toJson() + ",\"project\":" + target.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String email = data.getOrDefault("email", "").trim().toLowerCase();
                String password = data.getOrDefault("password", "").trim();
                String role = data.getOrDefault("role", "student").trim().toLowerCase();

                if (email.isEmpty() || password.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Email and password are required.\"}");
                    return;
                }

                if ("student".equals(role) || role.isEmpty()) {
                    for (Student s : students) {
                        if (s.getEmail().equalsIgnoreCase(email) && s.getPassword().equals(password)) {
                            sendResponse(exchange, 200, "{\"success\":true,\"role\":\"student\",\"user\":" + s.toJson() + ",\"message\":\"Welcome back, " + escapeJson(s.getName()) + "!\"}");
                            return;
                        }
                    }
                }

                if ("sponsor".equals(role) || role.isEmpty()) {
                    for (Sponsor sp : sponsors) {
                        if (sp.getEmail().equalsIgnoreCase(email) && sp.getPassword().equals(password)) {
                            sendResponse(exchange, 200, "{\"success\":true,\"role\":\"sponsor\",\"user\":" + sp.toJson() + ",\"message\":\"Welcome back, " + escapeJson(sp.getName()) + "!\"}");
                            return;
                        }
                    }
                }

                if (pendingRegistrations.containsKey(email)) {
                    sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Please verify your email address before logging in.\"}");
                    return;
                }

                sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid email or password.\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJsonSimple(body);

                String role = data.getOrDefault("role", "student").trim().toLowerCase();
                String name = data.getOrDefault("name", "").trim();
                String email = data.getOrDefault("email", "").trim().toLowerCase();
                String password = data.getOrDefault("password", "").trim();

                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Name, email, and password are required.\"}");
                    return;
                }
                if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Enter a valid email address.\"}");
                    return;
                }

                data.put("role", role);
                data.put("email", email);

                if (accountExists(email)) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"An account with this email already exists.\"}");
                    return;
                }

                String resendApiKey = System.getenv("RESEND_API_KEY");
                if (resendApiKey != null && !resendApiKey.isBlank()) {
                    String verificationCode = createVerificationCode();
                    PendingRegistration pending = new PendingRegistration(data, verificationCode);
                    pendingRegistrations.put(email, pending);
                    try {
                        sendVerificationEmail(email, verificationCode);
                        sendResponse(exchange, 200, "{\"success\":true,\"verificationRequired\":true,\"email\":\"" + escapeJson(email) + "\",\"message\":\"A verification code was sent to your email address.\"}");
                        return;
                    } catch (Exception e) {
                        pendingRegistrations.remove(email);
                    }
                }

                String accountResponse = createRegisteredAccount(data);
                sendResponse(exchange, 200, accountResponse);
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class VerifyEmailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            Map<String, String> data = parseJsonSimple(readRequestBody(exchange));
            String email = data.getOrDefault("email", "").trim().toLowerCase();
            String code = data.getOrDefault("code", "").trim();
            PendingRegistration pending = pendingRegistrations.get(email);
            if (pending == null) {
                sendResponse(exchange, 404, "{\"success\":false,\"message\":\"No pending registration was found.\"}");
                return;
            }

            synchronized (pending) {
                if (System.currentTimeMillis() > pending.expiresAt) {
                    pendingRegistrations.remove(email);
                    sendResponse(exchange, 410, "{\"success\":false,\"message\":\"The verification code expired.\"}");
                    return;
                }
                if (!code.matches("[0-9]{6}") || !MessageDigest.isEqual(pending.codeHash, hashVerificationCode(code))) {
                    pending.attempts++;
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"That verification code is incorrect.\"}");
                    return;
                }

                String accountResponse = createRegisteredAccount(pending.registrationData);
                pendingRegistrations.remove(email);
                sendResponse(exchange, 200, accountResponse);
            }
        }
    }

    static class ResendVerificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            Map<String, String> data = parseJsonSimple(readRequestBody(exchange));
            String email = data.getOrDefault("email", "").trim().toLowerCase();
            PendingRegistration pending = pendingRegistrations.get(email);
            if (pending == null) {
                sendResponse(exchange, 404, "{\"success\":false,\"message\":\"No pending verification found.\"}");
                return;
            }

            String verificationCode = createVerificationCode();
            try {
                sendVerificationEmail(email, verificationCode);
                pending.refreshCode(verificationCode, System.currentTimeMillis());
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"A new verification code was sent.\"}");
            } catch (Exception ex) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Could not send verification email.\"}");
            }
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                double totalFunded = 0;
                int completedCount = 0;
                for (Project p : projects) {
                    totalFunded += p.getCurrentFunding();
                    if (p.getCurrentFunding() >= p.getFundingGoal()) {
                        completedCount++;
                    }
                }

                String statsJson = String.format(
                        Locale.US,
                        "{\"totalStudents\":%d,\"totalSponsors\":%d,\"totalProjects\":%d,\"totalSponsorship\":%.2f,\"completedProjects\":%d}",
                        students.size(),
                        sponsors.size(),
                        projects.size(),
                        totalFunded,
                        completedCount
                );
                sendResponse(exchange, 200, statsJson);
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class StudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < students.size(); i++) {
                    sb.append(students.get(i).toJson());
                    if (i < students.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString());
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class SponsorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < sponsors.size(); i++) {
                    sb.append(sponsors.get(i).toJson());
                    if (i < sponsors.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString());
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    private static synchronized boolean accountExists(String email) {
        for (Student student : students) {
            if (student.getEmail().equalsIgnoreCase(email)) return true;
        }
        for (Sponsor sponsor : sponsors) {
            if (sponsor.getEmail().equalsIgnoreCase(email)) return true;
        }
        return false;
    }

    private static synchronized String createRegisteredAccount(Map<String, String> data) throws IOException {
        String role = data.getOrDefault("role", "student").trim().toLowerCase();
        String name = data.getOrDefault("name", "").trim();
        String email = data.getOrDefault("email", "").trim().toLowerCase();
        String password = data.getOrDefault("password", "").trim();

        if ("sponsor".equals(role)) {
            String type = data.getOrDefault("type", "Individual");
            String company = data.getOrDefault("company", name);
            String bio = data.getOrDefault("bio", "Passionate supporter of youth technology projects.");
            String newId = "SPO-" + (sponsors.size() + 201);
            Sponsor sponsor = new Sponsor(newId, name, email, password, type, company, bio);
            sponsors.add(sponsor);
            saveData();
            return "{\"success\":true,\"role\":\"sponsor\",\"user\":" + sponsor.toJson() + ",\"message\":\"Account created successfully.\"}";
        }

        String university = data.getOrDefault("university", "Engineering Institute");
        String department = data.getOrDefault("department", "Computer Engineering");
        String year = data.getOrDefault("year", "3rd Year");
        String skills = data.getOrDefault("skills", "Software, Hardware");
        String bio = data.getOrDefault("bio", "Aspiring engineer and project creator.");
        String newId = "STU-" + (students.size() + 101);
        Student student = new Student(newId, name, email, password, university, department, year, skills, bio);
        students.add(student);
        saveData();
        return "{\"success\":true,\"role\":\"student\",\"user\":" + student.toJson() + ",\"message\":\"Account created successfully.\"}";
    }

    private static String createVerificationCode() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private static byte[] hashVerificationCode(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static void sendVerificationEmail(String email, String code) throws IOException {
        String apiKey = System.getenv("RESEND_API_KEY");
        String sender = System.getenv("EMAIL_FROM");
        if (apiKey == null || apiKey.isBlank() || sender == null || sender.isBlank()) {
            throw new IllegalStateException("Set RESEND_API_KEY and EMAIL_FROM.");
        }

        String html = "<p>Your StudentFund verification code is: <strong>" + code + "</strong></p>";
        String payload = "{\"from\":\"" + escapeJson(sender) + "\",\"to\":[\"" + escapeJson(email)
                + "\"],\"subject\":\"Verify your StudentFund email\",\"html\":\"" + escapeJson(html) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        try {
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class PendingRegistration {
        private final Map<String, String> registrationData;
        private byte[] codeHash;
        private long expiresAt;
        private long lastSentAt;
        private int attempts;

        private PendingRegistration(Map<String, String> registrationData, String code) {
            this.registrationData = new HashMap<>(registrationData);
            refreshCode(code, System.currentTimeMillis());
        }

        private void refreshCode(String code, long issuedAt) {
            codeHash = hashVerificationCode(code);
            expiresAt = issuedAt + VERIFICATION_CODE_TTL_MILLIS;
            lastSentAt = issuedAt;
            attempts = 0;
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        sendResponse(exchange, statusCode, responseText, "application/json; charset=UTF-8");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText, String contentType) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static double parseDouble(String str, double defaultVal) {
        if (str == null || str.trim().isEmpty()) return defaultVal;
        try {
            return Double.parseDouble(str.trim().replace("$", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < ' ') {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static Map<String, String> parseJsonSimple(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        boolean inQuote = false;
        boolean escape = false;
        StringBuilder currentToken = new StringBuilder();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                currentToken.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (c == ',' && !inQuote) {
                tokens.add(currentToken.toString().trim());
                currentToken.setLength(0);
                continue;
            }
            currentToken.append(c);
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString().trim());
        }

        for (String pair : tokens) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx != -1) {
                String key = pair.substring(0, colonIdx).trim().replaceAll("^\"|\"$", "");
                String value = pair.substring(colonIdx + 1).trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
        }
        return map;
    }
}

class Student implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, name, email, password, university, department, year, skills, bio;

    public Student(String id, String name, String email, String password, String university, String department, String year, String skills, String bio) {
        this.id = id; this.name = name; this.email = email; this.password = password;
        this.university = university; this.department = department; this.year = year;
        this.skills = skills; this.bio = bio;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getUniversity() { return university; }
    public String getDepartment() { return department; }
    public String getYear() { return year; }
    public String getSkills() { return skills; }
    public String getBio() { return bio; }

    public String toJson() {
        return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"university\":\"%s\",\"department\":\"%s\",\"year\":\"%s\",\"skills\":\"%s\",\"bio\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(name), Main.escapeJson(email), Main.escapeJson(university),
                Main.escapeJson(department), Main.escapeJson(year), Main.escapeJson(skills), Main.escapeJson(bio)
        );
    }
}

class Sponsor implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, name, email, password, type, company, bio;

    public Sponsor(String id, String name, String email, String password, String type, String company, String bio) {
        this.id = id; this.name = name; this.email = email; this.password = password;
        this.type = type; this.company = company; this.bio = bio;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getType() { return type; }
    public String getCompany() { return company; }
    public String getBio() { return bio; }

    public String toJson() {
        return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"type\":\"%s\",\"company\":\"%s\",\"bio\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(name), Main.escapeJson(email), Main.escapeJson(type),
                Main.escapeJson(company), Main.escapeJson(bio)
        );
    }
}

class ProjectUpdate implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, projectId, title, description, date;

    public ProjectUpdate(String id, String projectId, String title, String description, String date) {
        this.id = id; this.projectId = projectId; this.title = title; this.description = description; this.date = date;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDate() { return date; }

    public String toJson() {
        return String.format(
                "{\"id\":\"%s\",\"projectId\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"date\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(projectId), Main.escapeJson(title), Main.escapeJson(description), Main.escapeJson(date)
        );
    }
}

class Sponsorship implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, projectId, projectTitle, sponsorId, sponsorName, sponsorEmail;
    private final double amount;
    private final String message, date;

    public Sponsorship(String id, String projectId, String projectTitle, String sponsorId, String sponsorName, String sponsorEmail, double amount, String message, String date) {
        this.id = id; this.projectId = projectId; this.projectTitle = projectTitle; this.sponsorId = sponsorId;
        this.sponsorName = sponsorName; this.sponsorEmail = sponsorEmail; this.amount = amount; this.message = message; this.date = date;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getProjectTitle() { return projectTitle; }
    public String getSponsorId() { return sponsorId; }
    public String getSponsorName() { return sponsorName; }
    public String getSponsorEmail() { return sponsorEmail; }
    public double getAmount() { return amount; }
    public String getMessage() { return message; }
    public String getDate() { return date; }

    public String toJson() {
        return String.format(
                Locale.US,
                "{\"id\":\"%s\",\"projectId\":\"%s\",\"projectTitle\":\"%s\",\"sponsorId\":\"%s\",\"sponsorName\":\"%s\",\"sponsorEmail\":\"%s\",\"amount\":%.2f,\"message\":\"%s\",\"date\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(projectId), Main.escapeJson(projectTitle), Main.escapeJson(sponsorId),
                Main.escapeJson(sponsorName), Main.escapeJson(sponsorEmail), amount, Main.escapeJson(message), Main.escapeJson(date)
        );
    }
}

class Project implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private String title, category, description;
    private double fundingGoal, currentFunding;
    private String requiredSkills, duration, teamMembers, imageUrl, githubUrl, demoUrl;
    private final String studentId, studentName, studentEmail, createdAt;
    private String status;
    private final List<ProjectUpdate> updates = new ArrayList<>();
    private final List<Sponsorship> sponsorships = new ArrayList<>();

    public Project(String id, String title, String category, String description, double fundingGoal, double currentFunding,
                   String requiredSkills, String duration, String teamMembers, String imageUrl, String githubUrl,
                   String demoUrl, String studentId, String studentName, String studentEmail, String createdAt, String status) {
        this.id = id; this.title = title; this.category = category; this.description = description;
        this.fundingGoal = fundingGoal; this.currentFunding = currentFunding; this.requiredSkills = requiredSkills;
        this.duration = duration; this.teamMembers = teamMembers; this.imageUrl = imageUrl;
        this.githubUrl = githubUrl; this.demoUrl = demoUrl; this.studentId = studentId;
        this.studentName = studentName; this.studentEmail = studentEmail; this.createdAt = createdAt; this.status = status;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getFundingGoal() { return fundingGoal; }
    public void setFundingGoal(double fundingGoal) { this.fundingGoal = fundingGoal; }
    public double getCurrentFunding() { return currentFunding; }
    public void setCurrentFunding(double currentFunding) { this.currentFunding = currentFunding; }
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getTeamMembers() { return teamMembers; }
    public void setTeamMembers(String teamMembers) { this.teamMembers = teamMembers; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getDemoUrl() { return demoUrl; }
    public void setDemoUrl(String demoUrl) { this.demoUrl = demoUrl; }
    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStudentEmail() { return studentEmail; }
    public String getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ProjectUpdate> getUpdates() { return updates; }
    public List<Sponsorship> getSponsorships() { return sponsorships; }

    public double getFundingPercentage() {
        if (fundingGoal <= 0) return 0.0;
        return Math.min(100.0, (currentFunding / fundingGoal) * 100.0);
    }

    public String toJson() {
        StringBuilder updatesJson = new StringBuilder("[");
        for (int i = 0; i < updates.size(); i++) {
            updatesJson.append(updates.get(i).toJson());
            if (i < updates.size() - 1) updatesJson.append(",");
        }
        updatesJson.append("]");

        StringBuilder spJson = new StringBuilder("[");
        for (int i = 0; i < sponsorships.size(); i++) {
            spJson.append(sponsorships.get(i).toJson());
            if (i < sponsorships.size() - 1) spJson.append(",");
        }
        spJson.append("]");

        return String.format(
                Locale.US,
                "{\"id\":\"%s\",\"title\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"fundingGoal\":%.2f,\"currentFunding\":%.2f,\"fundingPercentage\":%.1f,\"requiredSkills\":\"%s\",\"duration\":\"%s\",\"teamMembers\":\"%s\",\"imageUrl\":\"%s\",\"githubUrl\":\"%s\",\"demoUrl\":\"%s\",\"studentId\":\"%s\",\"studentName\":\"%s\",\"studentEmail\":\"%s\",\"createdAt\":\"%s\",\"status\":\"%s\",\"updates\":%s,\"sponsorships\":%s}",
                Main.escapeJson(id), Main.escapeJson(title), Main.escapeJson(category), Main.escapeJson(description),
                fundingGoal, currentFunding, getFundingPercentage(), Main.escapeJson(requiredSkills),
                Main.escapeJson(duration), Main.escapeJson(teamMembers), Main.escapeJson(imageUrl),
                Main.escapeJson(githubUrl), Main.escapeJson(demoUrl), Main.escapeJson(studentId),
                Main.escapeJson(studentName), Main.escapeJson(studentEmail), Main.escapeJson(createdAt),
                Main.escapeJson(status), updatesJson.toString(), spJson.toString()
        );
    }
}

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
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Main {
    private static final int PORT = getServerPort();
    private static final long VERIFICATION_CODE_TTL_MILLIS = 600000L;
    private static final long VERIFICATION_RESEND_COOLDOWN_MILLIS = 60000L;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HttpClient EMAIL_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10L))
            .build();

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
            seedData();
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

            server.start();
            System.out.println("==================================================================");
            System.out.println(" Micro-Sponsorship Portal for Student Projects Server Started");
            System.out.println(" Running on Port: " + PORT);
            System.out.println("==================================================================");
        } catch (BindException e) {
            System.err.println("Cannot start the backend: port " + PORT + " is already in use.");
        } catch (IOException e) {
            System.err.println("Cannot start the backend: " + e.getMessage());
        }
    }

    private static void seedData() {
        students.add(new Student("STU-101", "Gaurav Kolhe", "gau@gmail.com", "Gau", "RCPIT", "Information Technology", "2nd Year", "Java, Html, CSS", ""));
        String date = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
        addSeedProject("PRJ-101", "Smart Campus AI Copilot", "AI & Machine Learning", "A conversational campus assistant that helps students discover events, timetable slots, and career guidance using AI-powered recommendations.", 1250.0, 1485.0, "Python, TensorFlow, NLP, React", "8 weeks", "3 students", "https://images.unsplash.com/photo-1531482615713-2afd69097998?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-101", "Gaurav Kolhe", "gau@gmail.com", date, "Funded");
        addSeedProject("PRJ-102", "Vision-Based Attendance System", "AI & Machine Learning", "Face recognition and attendance automation system for classrooms with privacy-safe processing, attendance dashboards, and anomaly detection.", 1800.0, 1200.0, "OpenCV, Python, Flask, Edge AI", "10 weeks", "4 students", "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-102", "Aisha Shah", "aisha@college.edu", date, "Active");
        addSeedProject("PRJ-103", "CareerFit Predictor", "AI & Machine Learning", "An ML application that matches candidate skill profiles to internships, roles, and future career pathways using explainable recommendations.", 2100.0, 1450.0, "Python, Scikit-Learn, SQL, Analytics", "12 weeks", "3 students", "https://images.unsplash.com/photo-1552664730-d307ca884978?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-103", "Kabir Nair", "kabir@college.edu", date, "Active");
        addSeedProject("PRJ-104", "Autonomous Maze Rover", "IoT & Robotics", "A small autonomous rover that navigates obstacle maps using sensors, path-planning algorithms, and live telemetry for smart campus automation.", 2600.0, 1750.0, "C++, ROS, Sensor Fusion, Embedded Systems", "9 weeks", "4 students", "https://images.unsplash.com/photo-1581092921461-eab62e97a780?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-104", "Rohan Mehta", "rohan@college.edu", date, "Active");
    }

    private static void addSeedProject(String id, String title, String category, String description, double goal, double funded, String skills, String duration, String team, String img, String github, String demo, String stuId, String stuName, String stuEmail, String created, String status) {
        Project project = new Project(id, title, category, description, goal, funded, skills, duration, team, img, github, demo, stuId, stuName, stuEmail, created, status);
        project.getUpdates().add(new ProjectUpdate("UPD-" + System.currentTimeMillis() + "-" + id, id, "Project Launch", "Initial prototype and project goals are now visible to the student community.", created));
        projects.add(project);
    }

    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept");
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

    private static synchronized String createRegisteredAccount(Map<String, String> map) {
        String role = map.getOrDefault("role", "student").trim().toLowerCase();
        String name = map.getOrDefault("name", "").trim();
        String email = map.getOrDefault("email", "").trim().toLowerCase();
        String pass = map.getOrDefault("password", "").trim();

        if (accountExists(email)) {
            throw new IllegalArgumentException("Account already exists");
        } else if ("sponsor".equals(role)) {
            String type = map.getOrDefault("type", "Individual");
            String comp = map.getOrDefault("company", name);
            String bio = map.getOrDefault("bio", "Passionate supporter of youth technology projects.");
            String id = "SPO-" + (sponsors.size() + 201);
            Sponsor sponsor = new Sponsor(id, name, email, pass, type, comp, bio);
            sponsors.add(sponsor);
            return "{\"success\":true,\"role\":\"sponsor\",\"user\":" + sponsor.toJson() + ",\"message\":\"Email verified. Sponsor account created successfully.\"}";
        } else {
            String uni = map.getOrDefault("university", "Engineering Institute");
            String dept = map.getOrDefault("department", "Computer Engineering");
            String year = map.getOrDefault("year", "3rd Year");
            String skills = map.getOrDefault("skills", "Software, Hardware");
            String bio = map.getOrDefault("bio", "Aspiring engineer and project creator.");
            String id = "STU-" + (students.size() + 101);
            Student student = new Student(id, name, email, pass, uni, dept, year, skills, bio);
            students.add(student);
            return "{\"success\":true,\"role\":\"student\",\"user\":" + student.toJson() + ",\"message\":\"Email verified. Student account created successfully.\"}";
        }
    }

    private static String createVerificationCode() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1000000));
    }

    private static byte[] hashVerificationCode(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static void sendVerificationEmail(String toEmail, String code) throws IOException {
        String apiKey = System.getenv("RESEND_API_KEY");
        String fromEmail = System.getenv("EMAIL_FROM");
        if (apiKey != null && !apiKey.isBlank() && fromEmail != null && !fromEmail.isBlank()) {
            String html = "<p>Your StudentFund verification code is:</p><p><strong>" + code + "</strong></p><p>This code expires in 10 minutes.</p>";
            String payload = "{\"from\":\"" + escapeJson(fromEmail) + "\",\"to\":[\"" + escapeJson(toEmail) + "\"],\"subject\":\"Verify your StudentFund email\",\"html\":\"" + escapeJson(html) + "\"}";
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20L))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response = EMAIL_HTTP_CLIENT.send(request, BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Resend returned HTTP " + response.statusCode() + ".");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Email delivery was interrupted.", e);
            }
        } else {
            throw new IllegalStateException("Set RESEND_API_KEY and EMAIL_FROM on the backend.");
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        sendResponse(exchange, statusCode, responseText, "application/json; charset=UTF-8");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText, String contentType) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, (long) bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static double parseDouble(String val, double defaultVal) {
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Double.parseDouble(val.trim().replace("$", "").replace(",", ""));
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '\b' -> sb.append("\\b");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\f' -> sb.append("\\f");
                case '\r' -> sb.append("\\r");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
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

        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (escape) {
                currentToken.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                tokens.add(currentToken.toString().trim());
                currentToken.setLength(0);
            } else {
                currentToken.append(c);
            }
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

    // ==========================================
    // HTTP Handlers
    // ==========================================

    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.equals("/index.html")) {
                File file = new File(System.getProperty("user.dir"), "index.html");
                if (!file.exists() || !file.isFile()) {
                    Main.sendResponse(exchange, 200, "<html><body><h1>Micro-Sponsorship Portal</h1><p>Backend API is active.</p></body></html>", "text/html; charset=UTF-8");
                    return;
                }
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, (long) bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            Main.sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
        }
    }

    static class ProjectsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < Main.projects.size(); ++i) {
                    sb.append(Main.projects.get(i).toJson());
                    if (i < Main.projects.size() - 1) sb.append(",");
                }
                sb.append("]");
                Main.sendResponse(exchange, 200, sb.toString());
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class CreateProjectHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String title = data.getOrDefault("title", "").trim();
                String category = data.getOrDefault("category", "General").trim();
                String description = data.getOrDefault("description", "").trim();
                double goal = Main.parseDouble(data.get("fundingGoal"), 0.0);
                String skills = data.getOrDefault("requiredSkills", "").trim();
                String duration = data.getOrDefault("duration", "").trim();
                String team = data.getOrDefault("teamMembers", "").trim();
                String img = data.getOrDefault("imageUrl", "").trim();
                String github = data.getOrDefault("githubUrl", "").trim();
                String demo = data.getOrDefault("demoUrl", "").trim();
                String stuId = data.getOrDefault("studentId", "STU-" + (Main.students.size() + 100)).trim();
                String stuName = data.getOrDefault("studentName", "Student Innovator").trim();
                String stuEmail = data.getOrDefault("studentEmail", "").trim();

                if (title.isEmpty() || description.isEmpty() || goal <= 0.0) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Title, description, and positive funding goal are required.\"}");
                    return;
                }

                for (Project p : Main.projects) {
                    if (p.getTitle().equalsIgnoreCase(title)) {
                        Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"A project with this title already exists.\"}");
                        return;
                    }
                }

                if (img.isEmpty()) {
                    img = "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80";
                }

                String date = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
                String id = "PRJ-" + String.format("%03d", Main.projects.size() + 1);
                Project project = new Project(id, title, category, description, goal, 0.0, skills, duration, team, img, github, demo, stuId, stuName, stuEmail, date, "Active");
                project.getUpdates().add(new ProjectUpdate("UPD-" + (System.currentTimeMillis() % 10000L), id, "Project Created & Live", "Project launched on portal!", date));
                Main.projects.add(0, project);
                Main.sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project created successfully!\",\"project\":" + project.toJson() + "}");
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class UpdateProjectHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String id = data.get("id");
                if (id == null || id.isEmpty()) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project ID is required.\"}");
                    return;
                }

                Project target = null;
                for (Project p : Main.projects) {
                    if (p.getId().equalsIgnoreCase(id)) {
                        target = p;
                        break;
                    }
                }

                if (target == null) {
                    Main.sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                    return;
                }

                if (data.containsKey("title") && !data.get("title").trim().isEmpty()) target.setTitle(data.get("title").trim());
                if (data.containsKey("category") && !data.get("category").trim().isEmpty()) target.setCategory(data.get("category").trim());
                if (data.containsKey("description") && !data.get("description").trim().isEmpty()) target.setDescription(data.get("description").trim());
                if (data.containsKey("fundingGoal")) {
                    double g = Main.parseDouble(data.get("fundingGoal"), target.getFundingGoal());
                    if (g > 0.0 && g >= target.getCurrentFunding()) {
                        target.setFundingGoal(g);
                    } else if (g > 0.0) {
                        Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Funding goal cannot be lower than raised amount.\"}");
                        return;
                    }
                }
                if (data.containsKey("requiredSkills")) target.setRequiredSkills(data.get("requiredSkills").trim());
                if (data.containsKey("duration")) target.setDuration(data.get("duration").trim());
                if (data.containsKey("teamMembers")) target.setTeamMembers(data.get("teamMembers").trim());
                if (data.containsKey("imageUrl") && !data.get("imageUrl").trim().isEmpty()) target.setImageUrl(data.get("imageUrl").trim());
                if (data.containsKey("githubUrl")) target.setGithubUrl(data.get("githubUrl").trim());
                if (data.containsKey("demoUrl")) target.setDemoUrl(data.get("demoUrl").trim());

                Main.sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project updated successfully!\",\"project\":" + target.toJson() + "}");
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class DeleteProjectHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String id = data.get("id");
                if (id == null || id.isEmpty()) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project ID is required.\"}");
                    return;
                }
                boolean removed = Main.projects.removeIf(p -> p.getId().equalsIgnoreCase(id));
                Main.sponsorships.removeIf(s -> s.getProjectId().equalsIgnoreCase(id));
                if (removed) {
                    Main.sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project deleted successfully.\"}");
                } else {
                    Main.sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                }
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class SponsorHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String projectId = data.get("projectId");
                String name = data.getOrDefault("sponsorName", "Anonymous Backer").trim();
                String email = data.getOrDefault("sponsorEmail", "sponsor@example.com").trim();
                String id = data.getOrDefault("sponsorId", "SPO-" + (Main.sponsors.size() + 201)).trim();
                String msg = data.getOrDefault("message", "Keep up the great engineering work!").trim();
                double amt = Main.parseDouble(data.get("amount"), 0.0);

                if (projectId == null || projectId.isEmpty() || amt <= 0.0) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid project ID and amount required.\"}");
                    return;
                }

                Project target = null;
                for (Project p : Main.projects) {
                    if (p.getId().equalsIgnoreCase(projectId)) {
                        target = p;
                        break;
                    }
                }

                if (target == null) {
                    Main.sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                    return;
                }

                if (target.getCurrentFunding() >= target.getFundingGoal()) {
                    Main.sendResponse(exchange, 409, "{\"success\":false,\"message\":\"Project is already fully funded.\"}");
                    return;
                }

                if (amt > target.getFundingGoal() - target.getCurrentFunding()) {
                    Main.sendResponse(exchange, 409, "{\"success\":false,\"message\":\"Amount exceeds remaining limit.\"}");
                    return;
                }

                String date = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
                String spId = "SP-" + (Main.sponsorships.size() + 101);
                Sponsorship sp = new Sponsorship(spId, target.getId(), target.getTitle(), id, name, email, amt, msg, date);
                Main.sponsorships.add(sp);
                target.getSponsorships().add(sp);
                target.setCurrentFunding(target.getCurrentFunding() + amt);
                if (target.getCurrentFunding() >= target.getFundingGoal()) {
                    target.setStatus("Funded");
                }

                Main.sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Thank you! Sponsorship processed.\",\"sponsorship\":" + sp.toJson() + ",\"project\":" + target.toJson() + "}");
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class AddUpdateHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String projectId = data.get("projectId");
                String title = data.getOrDefault("title", "").trim();
                String desc = data.getOrDefault("description", "").trim();
                String date = data.getOrDefault("date", (new SimpleDateFormat("yyyy-MM-dd")).format(new Date())).trim();

                if (projectId == null || title.isEmpty() || desc.isEmpty()) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project ID, title, and description required.\"}");
                    return;
                }

                Project target = null;
                for (Project p : Main.projects) {
                    if (p.getId().equalsIgnoreCase(projectId)) {
                        target = p;
                        break;
                    }
                }

                if (target == null) {
                    Main.sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                    return;
                }

                String updateId = "UPD-" + (target.getUpdates().size() + 1);
                ProjectUpdate update = new ProjectUpdate(updateId, target.getId(), title, desc, date);
                target.getUpdates().add(0, update);
                Main.sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project update posted!\",\"update\":" + update.toJson() + ",\"project\":" + target.toJson() + "}");
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String email = data.getOrDefault("email", "").trim().toLowerCase();
                String pass = data.getOrDefault("password", "").trim();
                String role = data.getOrDefault("role", "student").trim().toLowerCase();

                if (email.isEmpty() || pass.isEmpty()) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Email and password are required.\"}");
                    return;
                }

                if ("student".equals(role) || role.isEmpty()) {
                    for (Student s : Main.students) {
                        if (s.getEmail().equalsIgnoreCase(email) && s.getPassword().equals(pass)) {
                            Main.sendResponse(exchange, 200, "{\"success\":true,\"role\":\"student\",\"user\":" + s.toJson() + ",\"message\":\"Welcome back, " + Main.escapeJson(s.getName()) + "!\"}");
                            return;
                        }
                    }
                }

                if ("sponsor".equals(role) || role.isEmpty()) {
                    for (Sponsor sp : Main.sponsors) {
                        if (sp.getEmail().equalsIgnoreCase(email) && sp.getPassword().equals(pass)) {
                            Main.sendResponse(exchange, 200, "{\"success\":true,\"role\":\"sponsor\",\"user\":" + sp.toJson() + ",\"message\":\"Welcome back, " + Main.escapeJson(sp.getName()) + "!\"}");
                            return;
                        }
                    }
                }

                if (Main.pendingRegistrations.containsKey(email)) {
                    Main.sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Please verify email first.\"}");
                    return;
                }

                Main.sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid email or password.\"}");
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = Main.readRequestBody(exchange);
                Map<String, String> data = Main.parseJsonSimple(body);
                String role = data.getOrDefault("role", "student").trim().toLowerCase();
                String name = data.getOrDefault("name", "").trim();
                String email = data.getOrDefault("email", "").trim().toLowerCase();
                String pass = data.getOrDefault("password", "").trim();

                if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Name, email, and password required.\"}");
                    return;
                }

                if (!"student".equals(role) && !"sponsor".equals(role)) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Choose a valid account type.\"}");
                    return;
                }

                if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Enter a valid email address.\"}");
                    return;
                }

                data.put("role", role);
                data.put("email", email);

                if (Main.accountExists(email)) {
                    Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"An account with this email already exists.\"}");
                    return;
                }

                PendingRegistration existing = Main.pendingRegistrations.get(email);
                if (existing != null) {
                    if (System.currentTimeMillis() <= existing.expiresAt) {
                        Main.sendResponse(exchange, 409, "{\"success\":false,\"verificationRequired\":true,\"email\":\"" + Main.escapeJson(email) + "\",\"message\":\"A code was already sent. Use resend.\"}");
                        return;
                    }
                    Main.pendingRegistrations.remove(email, existing);
                }

                String code = Main.createVerificationCode();
                PendingRegistration pending = new PendingRegistration(data, code);
                if (Main.pendingRegistrations.putIfAbsent(email, pending) != null) {
                    Main.sendResponse(exchange, 409, "{\"success\":false,\"message\":\"A code was already sent.\"}");
                    return;
                }

                try {
                    Main.sendVerificationEmail(email, code);
                    pending.refreshCode(code, System.currentTimeMillis());
                } catch (IllegalStateException e) {
                    Main.pendingRegistrations.remove(email, pending);
                    Main.sendResponse(exchange, 503, "{\"success\":false,\"message\":\"Email verification not configured on server.\"}");
                    return;
                } catch (IOException e) {
                    Main.pendingRegistrations.remove(email, pending);
                    Main.sendResponse(exchange, 502, "{\"success\":false,\"message\":\"Could not send email.\"}");
                    return;
                }

                Main.sendResponse(exchange, 200, "{\"success\":true,\"verificationRequired\":true,\"email\":\"" + Main.escapeJson(email) + "\",\"message\":\"Verification code sent to your email.\"}");
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class VerifyEmailHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            } else {
                Map<String, String> data = Main.parseJsonSimple(Main.readRequestBody(exchange));
                String email = data.getOrDefault("email", "").trim().toLowerCase();
                String code = data.getOrDefault("code", "").trim();
                PendingRegistration pending = Main.pendingRegistrations.get(email);

                if (pending == null) {
                    Main.sendResponse(exchange, 404, "{\"success\":false,\"message\":\"No pending registration found.\"}");
                    return;
                }

                synchronized (pending) {
                    long now = System.currentTimeMillis();
                    if (now > pending.expiresAt) {
                        Main.pendingRegistrations.remove(email, pending);
                        Main.sendResponse(exchange, 410, "{\"success\":false,\"message\":\"Code expired. Register again.\"}");
                    } else if (pending.attempts >= 5) {
                        Main.pendingRegistrations.remove(email, pending);
                        Main.sendResponse(exchange, 429, "{\"success\":false,\"message\":\"Too many incorrect codes.\"}");
                    } else if (code.matches("[0-9]{6}") && MessageDigest.isEqual(pending.codeHash, Main.hashVerificationCode(code))) {
                        try {
                            String resp = Main.createRegisteredAccount(pending.registrationData);
                            Main.pendingRegistrations.remove(email, pending);
                            Main.sendResponse(exchange, 200, resp);
                        } catch (IllegalArgumentException e) {
                            Main.pendingRegistrations.remove(email, pending);
                            Main.sendResponse(exchange, 409, "{\"success\":false,\"message\":\"Account with this email already exists.\"}");
                        }
                    } else {
                        ++pending.attempts;
                        if (pending.attempts >= 5) {
                            Main.pendingRegistrations.remove(email, pending);
                            Main.sendResponse(exchange, 429, "{\"success\":false,\"message\":\"Too many incorrect codes.\"}");
                        } else {
                            Main.sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Incorrect code. Try again.\"}");
                        }
                    }
                }
            }
        }
    }

    static class ResendVerificationHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            } else {
                Map<String, String> data = Main.parseJsonSimple(Main.readRequestBody(exchange));
                String email = data.getOrDefault("email", "").trim().toLowerCase();
                PendingRegistration pending = Main.pendingRegistrations.get(email);

                if (pending == null) {
                    Main.sendResponse(exchange, 404, "{\"success\":false,\"message\":\"No pending registration found.\"}");
                    return;
                }

                synchronized (pending) {
                    long now = System.currentTimeMillis();
                    if (now > pending.expiresAt) {
                        Main.pendingRegistrations.remove(email, pending);
                        Main.sendResponse(exchange, 410, "{\"success\":false,\"message\":\"Request expired. Register again.\"}");
                    } else {
                        long diff = 60000L - (now - pending.lastSentAt);
                        if (diff > 0L) {
                            long sec = (diff + 999L) / 1000L;
                            Main.sendResponse(exchange, 429, "{\"success\":false,\"message\":\"Please wait " + sec + " seconds.\"}");
                        } else {
                            String code = Main.createVerificationCode();
                            try {
                                Main.sendVerificationEmail(email, code);
                                pending.refreshCode(code, now);
                                Main.sendResponse(exchange, 200, "{\"success\":true,\"message\":\"A new verification code was sent.\"}");
                            } catch (IllegalStateException e) {
                                Main.sendResponse(exchange, 503, "{\"success\":false,\"message\":\"Email verification not configured on server.\"}");
                            } catch (IOException e) {
                                Main.sendResponse(exchange, 502, "{\"success\":false,\"message\":\"Could not send email.\"}");
                            }
                        }
                    }
                }
            }
        }
    }

    static class StatsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                double total = 0.0;
                int count = 0;
                for (Project p : Main.projects) {
                    total += p.getCurrentFunding();
                    if (p.getCurrentFunding() >= p.getFundingGoal()) {
                        ++count;
                    }
                }
                String stats = String.format(Locale.US, "{\"totalStudents\":%d,\"totalSponsors\":%d,\"totalProjects\":%d,\"totalSponsorship\":%.2f,\"completedProjects\":%d}", Main.students.size(), Main.sponsors.size(), Main.projects.size(), total, count);
                Main.sendResponse(exchange, 200, stats);
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class StudentsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < Main.students.size(); ++i) {
                    sb.append(Main.students.get(i).toJson());
                    if (i < Main.students.size() - 1) sb.append(",");
                }
                sb.append("]");
                Main.sendResponse(exchange, 200, sb.toString());
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    static class SponsorsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Main.setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Main.sendResponse(exchange, 204, "");
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < Main.sponsors.size(); ++i) {
                    sb.append(Main.sponsors.get(i).toJson());
                    if (i < Main.sponsors.size() - 1) sb.append(",");
                }
                sb.append("]");
                Main.sendResponse(exchange, 200, sb.toString());
            } else {
                Main.sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    private static final class PendingRegistration {
        private final Map<String, String> registrationData;
        private byte[] codeHash;
        private long expiresAt;
        private long lastSentAt;
        private int attempts;

        private PendingRegistration(Map<String, String> data, String code) {
            this.registrationData = new HashMap<>(data);
            this.refreshCode(code, System.currentTimeMillis());
        }

        private void refreshCode(String code, long time) {
            this.codeHash = Main.hashVerificationCode(code);
            this.expiresAt = time + 600000L;
            this.lastSentAt = time;
            this.attempts = 0;
        }
    }
}

// ==========================================
// Data Models
// ==========================================

class Student implements Serializable {
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
    public String toJson() {
        return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"university\":\"%s\",\"department\":\"%s\",\"year\":\"%s\",\"skills\":\"%s\",\"bio\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(name), Main.escapeJson(email), Main.escapeJson(university), Main.escapeJson(department), Main.escapeJson(year), Main.escapeJson(skills), Main.escapeJson(bio));
    }
}

class Sponsor implements Serializable {
    private final String id, name, email, password, type, company, bio;

    public Sponsor(String id, String name, String email, String password, String type, String company, String bio) {
        this.id = id; this.name = name; this.email = email; this.password = password;
        this.type = type; this.company = company; this.bio = bio;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String toJson() {
        return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"type\":\"%s\",\"company\":\"%s\",\"bio\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(name), Main.escapeJson(email), Main.escapeJson(type), Main.escapeJson(company), Main.escapeJson(bio));
    }
}

class ProjectUpdate implements Serializable {
    private final String id, projectId, title, description, date;

    public ProjectUpdate(String id, String projectId, String title, String description, String date) {
        this.id = id; this.projectId = projectId; this.title = title; this.description = description; this.date = date;
    }
    public String toJson() {
        return String.format("{\"id\":\"%s\",\"projectId\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"date\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(projectId), Main.escapeJson(title), Main.escapeJson(description), Main.escapeJson(date));
    }
}

class Sponsorship implements Serializable {
    private final String id, projectId, projectTitle, sponsorId, sponsorName, sponsorEmail, message, date;
    private final double amount;

    public Sponsorship(String id, String projectId, String projectTitle, String sponsorId, String sponsorName, String sponsorEmail, double amount, String message, String date) {
        this.id = id; this.projectId = projectId; this.projectTitle = projectTitle; this.sponsorId = sponsorId;
        this.sponsorName = sponsorName; this.sponsorEmail = sponsorEmail; this.amount = amount; this.message = message; this.date = date;
    }
    public String getProjectId() { return projectId; }
    public String toJson() {
        return String.format(Locale.US, "{\"id\":\"%s\",\"projectId\":\"%s\",\"projectTitle\":\"%s\",\"sponsorId\":\"%s\",\"sponsorName\":\"%s\",\"sponsorEmail\":\"%s\",\"amount\":%.2f,\"message\":\"%s\",\"date\":\"%s\"}",
                Main.escapeJson(id), Main.escapeJson(projectId), Main.escapeJson(projectTitle), Main.escapeJson(sponsorId), Main.escapeJson(sponsorName), Main.escapeJson(sponsorEmail), amount, Main.escapeJson(message), Main.escapeJson(date));
    }
}

class Project implements Serializable {
    private final String id;
    private String title, category, description, requiredSkills, duration, teamMembers, imageUrl, githubUrl, demoUrl, status;
    private double fundingGoal, currentFunding;
    private final String studentId, studentName, studentEmail, createdAt;
    private final List<ProjectUpdate> updates = new ArrayList<>();
    private final List<Sponsorship> sponsorships = new ArrayList<>();

    public Project(String id, String title, String category, String description, double fundingGoal, double currentFunding, String requiredSkills, String duration, String teamMembers, String imageUrl, String githubUrl, String demoUrl, String studentId, String studentName, String studentEmail, String createdAt, String status) {
        this.id = id; this.title = title; this.category = category; this.description = description;
        this.fundingGoal = fundingGoal; this.currentFunding = currentFunding; this.requiredSkills = requiredSkills;
        this.duration = duration; this.teamMembers = teamMembers; this.imageUrl = imageUrl;
        this.githubUrl = githubUrl; this.demoUrl = demoUrl; this.studentId = studentId;
        this.studentName = studentName; this.studentEmail = studentEmail; this.createdAt = createdAt;
        this.status = status;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public void setCategory(String category) { this.category = category; }
    public void setDescription(String description) { this.description = description; }
    public double getFundingGoal() { return fundingGoal; }
    public void setFundingGoal(double fundingGoal) { this.fundingGoal = fundingGoal; }
    public double getCurrentFunding() { return currentFunding; }
    public void setCurrentFunding(double currentFunding) { this.currentFunding = currentFunding; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public void setDuration(String duration) { this.duration = duration; }
    public void setTeamMembers(String teamMembers) { this.teamMembers = teamMembers; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public void setDemoUrl(String demoUrl) { this.demoUrl = demoUrl; }
    public void setStatus(String status) { this.status = status; }
    public List<ProjectUpdate> getUpdates() { return updates; }
    public List<Sponsorship> getSponsorships() { return sponsorships; }

    public String toJson() {
        StringBuilder u = new StringBuilder("[");
        for (int i = 0; i < updates.size(); ++i) {
            u.append(updates.get(i).toJson());
            if (i < updates.size() - 1) u.append(",");
        }
        u.append("]");

        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < sponsorships.size(); ++i) {
            s.append(sponsorships.get(i).toJson());
            if (i < sponsorships.size() - 1) s.append(",");
        }
        s.append("]");

        double pct = (fundingGoal <= 0.0) ? 0.0 : Math.min(100.0, (currentFunding / fundingGoal) * 100.0);
        return String.format(Locale.US, "{\"id\":\"%s\",\"title\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"fundingGoal\":%.2f,\"currentFunding\":%.2f,\"fundingPercentage\":%.1f,\"requiredSkills\":\"%s\",\"duration\":\"%s\",\"teamMembers\":\"%s\",\"imageUrl\":\"%s\",\"githubUrl\":\"%s\",\"demoUrl\":\"%s\",\"studentId\":\"%s\",\"studentName\":\"%s\",\"studentEmail\":\"%s\",\"createdAt\":\"%s\",\"status\":\"%s\",\"updates\":%s,\"sponsorships\":%s}",
                Main.escapeJson(id), Main.escapeJson(title), Main.escapeJson(category), Main.escapeJson(description), fundingGoal, currentFunding, pct, Main.escapeJson(requiredSkills), Main.escapeJson(duration), Main.escapeJson(teamMembers), Main.escapeJson(imageUrl), Main.escapeJson(githubUrl), Main.escapeJson(demoUrl), Main.escapeJson(studentId), Main.escapeJson(studentName), Main.escapeJson(studentEmail), Main.escapeJson(createdAt), Main.escapeJson(status), u.toString(), s.toString());
    }
}

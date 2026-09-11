import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Micro-Sponsorship Portal for Student Projects - Java Backend
 * Built with standard Java HttpServer (no external frameworks or dependencies).
 */
public class Main {
    private static final int PORT = 8080;

    // In-memory data stores
    static final List<Student> students = new ArrayList<>();
    static final List<Sponsor> sponsors = new ArrayList<>();
    static final List<Project> projects = new ArrayList<>();
    static final List<Sponsorship> sponsorships = new ArrayList<>();

    public static void main(String[] args) {
        try {
            // Seed initial mock data
            seedData();

            // Create HTTP Server on port 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // Register API Handlers
            server.createContext("/", new StaticFileHandler());
            server.createContext("/projects", new ProjectsHandler());
            server.createContext("/createProject", new CreateProjectHandler());
            server.createContext("/updateProject", new UpdateProjectHandler());
            server.createContext("/deleteProject", new DeleteProjectHandler());
            server.createContext("/sponsor", new SponsorHandler());
            server.createContext("/addProjectUpdate", new AddUpdateHandler());
            server.createContext("/login", new LoginHandler());
            server.createContext("/register", new RegisterHandler());
            server.createContext("/stats", new StatsHandler());
            server.createContext("/students", new StudentsHandler());
            server.createContext("/sponsors", new SponsorsHandler());

            server.start();
            System.out.println("==================================================================");
            System.out.println(" Micro-Sponsorship Portal for Student Projects Server Started");
            System.out.println(" Local URL: http://localhost:" + PORT);
            System.out.println(" Status: In-memory database initialized with sample projects.");
            System.out.println("==================================================================");
        } catch (BindException e) {
            System.err.println("Cannot start the backend: port " + PORT + " is already in use.");
            System.err.println("Stop the other process using port " + PORT + " or change PORT in Main.java.");
        } catch (IOException e) {
            System.err.println("Cannot start the backend: " + e.getMessage());
            System.err.println("Run this command from the project folder: java Main.java");
        }
    }

    // ==========================================
    // Seed Sample Data
    // ==========================================
    private static void seedData() {
        // Sample Students
        Student s1 = new Student("STU-101", "Gaurav Kolhe", "gau@gmail.com", "Gau", "RCPIT", "Information Technology", "2nd Year", "Java, Html, CSS ", "passionate about learning new technologies and building projects that make a difference in the world.");
        

        students.add(s1);

        String[] studentNames = {
            "Elena Rostova", "Priya Nair", "Marcus Chen", "Aisha Khan", "Noah Williams",
            "Sofia Martinez", "Rohan Mehta", "Maya Patel", "Daniel Kim", "Anika Shah"
        };
        for (int i = 2; i <= 50; i++) {
            String name = studentNames[(i - 2) % studentNames.length] + " " + i;
            students.add(new Student(
                "STU-" + (99 + i), name, "student" + i + "@college.edu", "student123",
                "National College of Engineering", "Computer Science", ((i % 4) + 1) + " Year",
                "Java, Python, Web Development", "College student building practical technology projects."
            ));
        }
        

        // Sample Sponsors
        Sponsor sp1 = new Sponsor("SPO-201", "Apex Venture Labs", "sponsorships@apexventures.io", "password123", "Corporate", "Apex Ventures", "Supporting breakthrough student engineering initiatives and deep-tech prototypes.");
        Sponsor sp2 = new Sponsor("SPO-202", "Dr. Robert Vance", "robert.vance@alumni.org", "password123", "Alumni", "Vance Tech Advisory", "Alumni mentor giving back to empower next-generation engineers and makers.");
        Sponsor sp3 = new Sponsor("SPO-203", "Green Earth Foundation", "grants@greenearth.org", "password123", "Individual", "Eco Grants Network", "Micro-grant provider for clean-tech and sustainability student capstones.");

        sponsors.add(sp1);
        sponsors.add(sp2);
        sponsors.add(sp3);

        String[] sponsorNames = {
            "Campus Innovation Fund", "BrightPath Alumni", "Nova Small Grants", "TechBridge Community",
            "Future Makers Club", "Local Impact Partners", "Open Learning Foundation"
        };
        for (int i = 0; i < sponsorNames.length; i++) {
            sponsors.add(new Sponsor(
                "SPO-" + (204 + i), sponsorNames[i], "sponsor" + (i + 4) + "@example.org", "sponsor123",
                i % 2 == 0 ? "Alumni" : "Community", sponsorNames[i],
                "Supporting affordable student innovation and practical college projects."
            ));
        }

        Project drone = new Project(
            "PRJ-002", "SkyRescue: Autonomous Disaster Supply Drone", "IoT & Robotics",
            "A multi-rotor autonomous UAV engineered for rapid emergency payload delivery in disaster zones where roads are blocked.",
                200, 200, "ROS 2, PX4 Autopilot, C++, Computer Vision", "6 Months",
            "Elena Rostova (Flight Lead), Mateo Silva (Avionics)",
            "https://images.unsplash.com/photo-1508614589041-895b88991e3e?auto=format&fit=crop&w=800&q=80",
            "https://github.com/student-projects/skyrescue-uav-autonomy", "https://skyrescue-telemetry.demo.app",
            "STU-102", "Elena Rostova", "elena@stanford.edu", "2026-06-15", "Funded"
        );
        drone.getUpdates().add(new ProjectUpdate("UPD-3", "PRJ-002", "Waypoint Flight Test Passed", "Autonomous 5km flight with 1.2kg payload succeeded.", "2026-07-12"));
        drone.getUpdates().add(new ProjectUpdate("UPD-4", "PRJ-002", "Fully Funded Milestone Reached", "Thanks to Apex Venture Labs and alumni donors!", "2026-08-02"));
        projects.add(drone);

        Project solar = new Project(
            "PRJ-003", "SunShare: Peer-to-Peer Solar Microgrid Node", "GreenTech & Energy",
            "An open-source IoT hardware controller that lets rural homes with rooftop solar panels share excess clean energy with automated micro-billing.",
                150, 92, "ESP32, C++, MQTT, React Dashboard, Power Electronics", "5 Months",
            "Priya Nair (Lead), Arjun Patel (Firmware)",
            "https://images.unsplash.com/photo-1509391365360-2e959784a276?auto=format&fit=crop&w=800&q=80",
            "https://github.com/student-projects/sunshare-microgrid", "https://sunshare-energy.demo.app",
            "STU-103", "Priya Nair", "priya@iitb.ac.in", "2026-07-20", "Active"
        );
        solar.getUpdates().add(new ProjectUpdate("UPD-5", "PRJ-003", "Inverter Interface Built", "Built opto-isolated relay interface connecting solar controller.", "2026-08-18"));
        projects.add(solar);

        Project health = new Project(
            "PRJ-004", "MediVault: Zero-Knowledge Health Record Pass", "HealthTech & Blockchain",
            "Decentralized electronic medical records system using Zero-Knowledge proofs. Patients maintain absolute ownership of diagnostic records.",
                180, 65, "Next.js, Solidity, IPFS, Circom, Web Crypto API", "3 Months",
            "Marcus Chen (ZK Lead), Dr. Ananya Sen",
            "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80",
            "https://github.com/student-projects/medivault-zk-records", "https://medivault.demo.app",
            "STU-104", "Marcus Chen", "marcus@berkeley.edu", "2026-08-01", "Active"
        );
        health.getUpdates().add(new ProjectUpdate("UPD-6", "PRJ-004", "ZK-SNARK Circuit Test", "Validated zero-knowledge verification on local testnet.", "2026-08-20"));
        projects.add(health);

        String[] titles = {
            "FreshTrack: Smart Food Storage Monitor", "StudyBuddy: Peer Learning Planner",
            "CleanRoute: Campus Waste Tracker", "SafeWalk: Student Commute Alert",
            "AquaSense: Low-Cost Water Quality Kit", "SignSpark: Gesture Learning Tool",
            "FarmLink: Student Grower Marketplace", "Mindful Minutes: Wellness Check-In",
            "BuildBox: Modular 3D Printing Lab"
        };
        String[] categories = {
            "AgriTech", "EdTech", "Sustainability", "CivicTech", "Environment",
            "Accessibility", "Social Impact", "Wellness", "Hardware & Manufacturing"
        };
        String[] descriptions = {
            "A low-cost sensor that helps students and small kitchens reduce food waste by monitoring storage conditions.",
            "A simple campus planner that matches students for focused peer study sessions and shared revision goals.",
            "A campus dashboard that maps waste collection points and rewards better recycling habits.",
            "A privacy-friendly mobile alert system for safer evening commutes between campus locations.",
            "An affordable test kit that helps student communities monitor water quality in local sources.",
            "An interactive learning tool that turns common hand gestures into accessible study prompts.",
            "A marketplace connecting student growers with nearby buyers for affordable fresh produce.",
            "A lightweight daily check-in that helps college students notice stress patterns and find support resources.",
            "A shared design library and booking system for student teams using campus 3D printers."
        };
        String[] skills = {
            "Arduino, Sensors, JavaScript", "React, Firebase, UX Design", "Python, Maps API, Data Visualization",
            "Flutter, Notifications, Security", "Arduino, Chemistry, Embedded C", "Python, TensorFlow Lite, UX",
            "Node.js, SQL, Payments", "React Native, SQLite, Accessibility", "CAD, 3D Printing, Web Apps"
        };
        int[] goals = {120, 180, 150, 220, 100, 175, 130, 200, 160};
        int[] raised = {45, 70, 80, 55, 30, 90, 40, 65, 25};
        String[] images = {
            "https://images.unsplash.com/photo-1498837167922-ddd27525d352?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1523240795612-9a054b0db644?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1532996122724-e3c354a0b15b?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1538108149393-fbbd81895907?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1559757175-0eb30cd8c063?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1490219556762-6b567b54dfb9?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=800&q=80"
        };
        for (int i = 0; i < titles.length; i++) {
            int projectNumber = 5 + i;
            int studentNumber = 105 + i;
            projects.add(new Project(
                "PRJ-" + String.format("%03d", projectNumber), titles[i], categories[i], descriptions[i],
                goals[i], raised[i], skills[i], "3 Months", "Student team " + studentNumber,
                images[i], "https://github.com/student-projects/project-" + projectNumber,
                "https://project-" + projectNumber + ".demo.app", "STU-" + studentNumber,
                "Student " + studentNumber, "student" + studentNumber + "@college.edu", "2026-09-01", "Active"
            ));
        }
    }

    // ==========================================
    // HTTP Handlers
    // ==========================================

    /**
     * Serves index.html for root and static paths
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.equals("/index.html")) {
                File file = new File(System.getProperty("user.dir"), "index.html");
                if (file.exists() && file.isFile()) {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    String fallback = "<html><body><h1>Micro-Sponsorship Portal</h1><p>index.html was not found in current directory.</p></body></html>";
                    sendResponse(exchange, 200, fallback, "text/html; charset=UTF-8");
                    return;
                }
            }
            sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
        }
    }
    /**
     * GET /projects: Returns all projects in JSON format
     */
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

    /**
     * POST /createProject: Creates a new student project
     */
    static class CreateProjectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCORSHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
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

                // Validation
                if (title.isEmpty() || description.isEmpty() || fundingGoal <= 0) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Title, description, and positive funding goal are required.\"}");
                    return;
                }

                // Check for duplicate project name
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

                // Add welcome milestone update
                newProject.getUpdates().add(new ProjectUpdate(
                        "UPD-" + (System.currentTimeMillis() % 10000),
                        newId,
                        "Project Created & Live",
                        "Project officially launched on Micro-Sponsorship Portal! Open for community backing and mentorship.",
                        dateStr
                ));

                projects.add(0, newProject);

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project created successfully!\",\"project\":" + newProject.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * POST /updateProject: Updates an existing project
     */
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

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project updated successfully!\",\"project\":" + target.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * POST /deleteProject: Deletes a project by ID
     */
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
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project deleted successfully.\"}");
                } else {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * POST /sponsor: Simulates micro-sponsorship payment and updates project funding
     */
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

                // Register sponsorship
                String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String spId = "SP-" + (sponsorships.size() + 101);
                Sponsorship sp = new Sponsorship(spId, target.getId(), target.getTitle(), sponsorId, sponsorName, sponsorEmail, amount, message, dateStr);

                sponsorships.add(sp);
                target.getSponsorships().add(sp);
                target.setCurrentFunding(target.getCurrentFunding() + amount);

                if (target.getCurrentFunding() >= target.getFundingGoal()) {
                    target.setStatus("Funded");
                }

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Thank you! Sponsorship of $" + String.format(Locale.US, "%.2f", amount) + " was successfully processed.\",\"sponsorship\":" + sp.toJson() + ",\"project\":" + target.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * POST /addProjectUpdate: Adds a milestone update to a project
     */
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

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Project update posted successfully!\",\"update\":" + update.toJson() + ",\"project\":" + target.toJson() + "}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * POST /login: Authenticates user credentials (Student, Sponsor, Admin)
     */
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

                // Check Admin
                if ("admin".equals(role) || email.contains("admin")) {
                    if ("admin@studentfund.edu".equals(email) && "admin123".equals(password)) {
                        String userJson = "{\"id\":\"ADM-001\",\"name\":\"System Administrator\",\"email\":\"" + escapeJson(email) + "\",\"role\":\"admin\"}";
                        sendResponse(exchange, 200, "{\"success\":true,\"role\":\"admin\",\"user\":" + userJson + ",\"message\":\"Admin login successful.\"}");
                        return;
                    }
                }

                // Check Student
                if ("student".equals(role) || role.isEmpty()) {
                    for (Student s : students) {
                        if (s.getEmail().equalsIgnoreCase(email)) {
                            if (s.getPassword().equals(password)) {
                                sendResponse(exchange, 200, "{\"success\":true,\"role\":\"student\",\"user\":" + s.toJson() + ",\"message\":\"Welcome back, " + escapeJson(s.getName()) + "!\"}");
                                return;
                            }
                        }
                    }
                }

                // Check Sponsor
                if ("sponsor".equals(role) || role.isEmpty()) {
                    for (Sponsor sp : sponsors) {
                        if (sp.getEmail().equalsIgnoreCase(email)) {
                            if (sp.getPassword().equals(password)) {
                                sendResponse(exchange, 200, "{\"success\":true,\"role\":\"sponsor\",\"user\":" + sp.toJson() + ",\"message\":\"Welcome back, " + escapeJson(sp.getName()) + "!\"}");
                                return;
                            }
                        }
                    }
                }

                sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid email or password.\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * POST /register: Handles Student or Sponsor registration
     */
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

                if ("sponsor".equals(role)) {
                    for (Sponsor s : sponsors) {
                        if (s.getEmail().equalsIgnoreCase(email)) {
                            sendResponse(exchange, 400, "{\"success\":false,\"message\":\"An account with this email already exists.\"}");
                            return;
                        }
                    }
                    String type = data.getOrDefault("type", "Individual");
                    String company = data.getOrDefault("company", name);
                    String bio = data.getOrDefault("bio", "Passionate supporter of youth technology projects.");
                    String newId = "SPO-" + (sponsors.size() + 201);
                    Sponsor sponsor = new Sponsor(newId, name, email, password, type, company, bio);
                    sponsors.add(sponsor);
                    sendResponse(exchange, 200, "{\"success\":true,\"role\":\"sponsor\",\"user\":" + sponsor.toJson() + ",\"message\":\"Sponsor registration successful!\"}");
                } else {
                    for (Student s : students) {
                        if (s.getEmail().equalsIgnoreCase(email)) {
                            sendResponse(exchange, 400, "{\"success\":false,\"message\":\"An account with this email already exists.\"}");
                            return;
                        }
                    }
                    String university = data.getOrDefault("university", "Engineering Institute");
                    String department = data.getOrDefault("department", "Computer Engineering");
                    String year = data.getOrDefault("year", "3rd Year");
                    String skills = data.getOrDefault("skills", "Software, Hardware");
                    String bio = data.getOrDefault("bio", "Aspiring engineer and project creator.");
                    String newId = "STU-" + (students.size() + 101);
                    Student student = new Student(newId, name, email, password, university, department, year, skills, bio);
                    students.add(student);
                    sendResponse(exchange, 200, "{\"success\":true,\"role\":\"student\",\"user\":" + student.toJson() + ",\"message\":\"Student registration successful!\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    /**
     * GET /stats: Aggregate portal statistics
     */
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

    /**
     * GET /students: List of students
     */
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

    /**
     * GET /sponsors: List of sponsors
     */
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

    // ==========================================
    // Helper Utilities
    // ==========================================

    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
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
                        String hex = String.format("\\u%04x", (int) ch);
                        sb.append(hex);
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Simple JSON parser for single-level or basic JSON strings
     */
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

// ==========================================
// Data Models
// ==========================================

class Student {
    private final String id;
    private final String name;
    private final String email;
    private final String password;
    private final String university;
    private final String department;
    private final String year;
    private final String skills;
    private final String bio;

    public Student(String id, String name, String email, String password, String university, String department, String year, String skills, String bio) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.university = university;
        this.department = department;
        this.year = year;
        this.skills = skills;
        this.bio = bio;
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
                Main.escapeJson(id),
                Main.escapeJson(name),
                Main.escapeJson(email),
                Main.escapeJson(university),
                Main.escapeJson(department),
                Main.escapeJson(year),
                Main.escapeJson(skills),
                Main.escapeJson(bio)
        );
    }
}

class Sponsor {
    private final String id;
    private final String name;
    private final String email;
    private final String password;
    private final String type;
    private final String company;
    private final String bio;

    public Sponsor(String id, String name, String email, String password, String type, String company, String bio) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.type = type;
        this.company = company;
        this.bio = bio;
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
                Main.escapeJson(id),
                Main.escapeJson(name),
                Main.escapeJson(email),
                Main.escapeJson(type),
                Main.escapeJson(company),
                Main.escapeJson(bio)
        );
    }
}

class ProjectUpdate {
    private final String id;
    private final String projectId;
    private final String title;
    private final String description;
    private final String date;

    public ProjectUpdate(String id, String projectId, String title, String description, String date) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.date = date;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDate() { return date; }

    public String toJson() {
        return String.format(
                "{\"id\":\"%s\",\"projectId\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"date\":\"%s\"}",
                Main.escapeJson(id),
                Main.escapeJson(projectId),
                Main.escapeJson(title),
                Main.escapeJson(description),
                Main.escapeJson(date)
        );
    }
}

class Sponsorship {
    private final String id;
    private final String projectId;
    private final String projectTitle;
    private final String sponsorId;
    private final String sponsorName;
    private final String sponsorEmail;
    private final double amount;
    private final String message;
    private final String date;

    public Sponsorship(String id, String projectId, String projectTitle, String sponsorId, String sponsorName, String sponsorEmail, double amount, String message, String date) {
        this.id = id;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.sponsorId = sponsorId;
        this.sponsorName = sponsorName;
        this.sponsorEmail = sponsorEmail;
        this.amount = amount;
        this.message = message;
        this.date = date;
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
                Main.escapeJson(id),
                Main.escapeJson(projectId),
                Main.escapeJson(projectTitle),
                Main.escapeJson(sponsorId),
                Main.escapeJson(sponsorName),
                Main.escapeJson(sponsorEmail),
                amount,
                Main.escapeJson(message),
                Main.escapeJson(date)
        );
    }
}

class Project {
    private final String id;
    private String title;
    private String category;
    private String description;
    private double fundingGoal;
    private double currentFunding;
    private String requiredSkills;
    private String duration;
    private String teamMembers;
    private String imageUrl;
    private String githubUrl;
    private String demoUrl;
    private final String studentId;
    private final String studentName;
    private final String studentEmail;
    private final String createdAt;
    private String status;
    private final List<ProjectUpdate> updates = new ArrayList<>();
    private final List<Sponsorship> sponsorships = new ArrayList<>();

    public Project(String id, String title, String category, String description, double fundingGoal, double currentFunding,
                   String requiredSkills, String duration, String teamMembers, String imageUrl, String githubUrl,
                   String demoUrl, String studentId, String studentName, String studentEmail, String createdAt, String status) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.description = description;
        this.fundingGoal = fundingGoal;
        this.currentFunding = currentFunding;
        this.requiredSkills = requiredSkills;
        this.duration = duration;
        this.teamMembers = teamMembers;
        this.imageUrl = imageUrl;
        this.githubUrl = githubUrl;
        this.demoUrl = demoUrl;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }

    public String getDescription() { return description; }
    public String setDescription(String description) { this.description = description; return description; }
    public double getFundingGoal() { return fundingGoal; }
    public double setFundingGoal(double fundingGoal) { this.fundingGoal = fundingGoal; return fundingGoal; }
    public double getCurrentFunding() { return currentFunding; }
    public double setCurrentFunding(double currentFunding) { this.currentFunding = currentFunding; return currentFunding; }
    public String getRequiredSkills() { return requiredSkills; }
    public String setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; return requiredSkills; }
    public String getDuration() { return duration; }
    public String setDuration(String duration) { this.duration = duration; return duration; }
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
                Main.escapeJson(id),
                Main.escapeJson(title),
                Main.escapeJson(category),
                Main.escapeJson(description),
                fundingGoal,
                currentFunding,
                getFundingPercentage(),
                Main.escapeJson(requiredSkills),
                Main.escapeJson(duration),
                Main.escapeJson(teamMembers),
                Main.escapeJson(imageUrl),
                Main.escapeJson(githubUrl),
                Main.escapeJson(demoUrl),
                Main.escapeJson(studentId),
                Main.escapeJson(studentName),
                Main.escapeJson(studentEmail),
                Main.escapeJson(createdAt),
                Main.escapeJson(status),
                updatesJson.toString(),
                spJson.toString()
        );
    }

    void setTitle(String title) {
        this.title = title;
    }

    void setCategory(String category) {
        this.category = category;
    }
}

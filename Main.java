// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Main {
   private static final int PORT = 8080;
   private static final long VERIFICATION_CODE_TTL_MILLIS = 600000L;
   private static final long VERIFICATION_RESEND_COOLDOWN_MILLIS = 60000L;
   private static final int MAX_VERIFICATION_ATTEMPTS = 5;
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();
   private static final HttpClient EMAIL_HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   static final List<Student> students = new ArrayList();
   static final List<Sponsor> sponsors = new ArrayList();
   static final List<Project> projects = new ArrayList();
   static final List<Sponsorship> sponsorships = new ArrayList();
   private static final Map<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap();

   public Main() {
   }

   public static void main(String[] var0) {
      try {
         seedData();
         HttpServer var1 = HttpServer.create(new InetSocketAddress(8080), 0);
         var1.setExecutor(Executors.newCachedThreadPool());
         var1.createContext("/", new StaticFileHandler());
         var1.createContext("/projects", new ProjectsHandler());
         var1.createContext("/createProject", new CreateProjectHandler());
         var1.createContext("/updateProject", new UpdateProjectHandler());
         var1.createContext("/deleteProject", new DeleteProjectHandler());
         var1.createContext("/sponsor", new SponsorHandler());
         var1.createContext("/addProjectUpdate", new AddUpdateHandler());
         var1.createContext("/login", new LoginHandler());
         var1.createContext("/register", new RegisterHandler());
         var1.createContext("/verifyEmail", new VerifyEmailHandler());
         var1.createContext("/resendVerification", new ResendVerificationHandler());
         var1.createContext("/stats", new StatsHandler());
         var1.createContext("/students", new StudentsHandler());
         var1.createContext("/sponsors", new SponsorsHandler());
         var1.start();
         System.out.println("==================================================================");
         System.out.println(" Micro-Sponsorship Portal for Student Projects Server Started");
         System.out.println(" Local URL: http://localhost:8080");
         System.out.println(" Status: In-memory database initialized with the portal account.");
         System.out.println("==================================================================");
      } catch (BindException var2) {
         System.err.println("Cannot start the backend: port 8080 is already in use.");
         System.err.println("Stop the other process using port 8080 or change PORT in Main.java.");
      } catch (IOException var3) {
         System.err.println("Cannot start the backend: " + var3.getMessage());
         System.err.println("Run this command from the project folder: java Main.java");
      }

   }

   private static void seedData() {
      students.add(new Student("STU-101", "Gaurav Kolhe", "gau@gmail.com", "Gau", "RCPIT", "Information Technology", "2nd Year", "Java, Html, CSS", ""));
      String var0 = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
      addSeedProject("PRJ-101", "Smart Campus AI Copilot", "AI & Machine Learning", "A conversational campus assistant that helps students discover events, timetable slots, and career guidance using AI-powered recommendations.", (double)1250.0F, (double)1485.0F, "Python, TensorFlow, NLP, React", "8 weeks", "3 students", "https://images.unsplash.com/photo-1531482615713-2afd69097998?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-101", "Gaurav Kolhe", "gau@gmail.com", var0, "Funded");
      addSeedProject("PRJ-102", "Vision-Based Attendance System", "AI & Machine Learning", "Face recognition and attendance automation system for classrooms with privacy-safe processing, attendance dashboards, and anomaly detection.", (double)1800.0F, (double)1200.0F, "OpenCV, Python, Flask, Edge AI", "10 weeks", "4 students", "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-102", "Aisha Shah", "aisha@college.edu", var0, "Active");
      addSeedProject("PRJ-103", "CareerFit Predictor", "AI & Machine Learning", "An ML application that matches candidate skill profiles to internships, roles, and future career pathways using explainable recommendations.", (double)2100.0F, (double)1450.0F, "Python, Scikit-Learn, SQL, Analytics", "12 weeks", "3 students", "https://images.unsplash.com/photo-1552664730-d307ca884978?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-103", "Kabir Nair", "kabir@college.edu", var0, "Active");
      addSeedProject("PRJ-104", "Autonomous Maze Rover", "IoT & Robotics", "A small autonomous rover that navigates obstacle maps using sensors, path-planning algorithms, and live telemetry for smart campus automation.", (double)2600.0F, (double)1750.0F, "C++, ROS, Sensor Fusion, Embedded Systems", "9 weeks", "4 students", "https://images.unsplash.com/photo-1581092921461-eab62e97a780?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-104", "Rohan Mehta", "rohan@college.edu", var0, "Active");
      addSeedProject("PRJ-105", "Smart Greenhouse Monitor", "IoT & Robotics", "A smart greenhouse sensor network for moisture, temperature, and nutrient monitoring to optimize irrigation and crop productivity.", (double)1500.0F, (double)1380.0F, "IoT, ESP32, MQTT, Data Logging", "7 weeks", "2 students", "https://images.unsplash.com/photo-1466692476868-aef1dfb1e735?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-105", "Sneha Kulkarni", "sneha@college.edu", var0, "Active");
      addSeedProject("PRJ-106", "Warehouse Bot Fleet", "IoT & Robotics", "A coordinated robotic arm and line-following bot system for indoor inventory routing and material handling in small warehouses.", (double)3400.0F, (double)2200.0F, "Embedded C, Robotics, Automation, CAD", "12 weeks", "5 students", "https://images.unsplash.com/photo-1560251183-3b53f0d59cdb?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-106", "Nitin Joshi", "nitin@college.edu", var0, "Active");
      addSeedProject("PRJ-107", "Solar Charge Optimizer", "GreenTech & Energy", "A solar tracking and charge optimization node that increases battery efficiency using weather-aware routing and load balancing.", (double)2600.0F, (double)2900.0F, "Solar Tech, Electronics, Power Systems, MATLAB", "10 weeks", "3 students", "https://images.unsplash.com/photo-1497435334941-8c899ee9e8e9?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-107", "Prachi Verma", "prachi@college.edu", var0, "Funded");
      addSeedProject("PRJ-108", "Rainwater Harvesting Sensor Grid", "GreenTech & Energy", "A community water monitoring network to track rainwater storage, leak detection, and predictive maintenance for urban sustainability.", (double)2400.0F, (double)1600.0F, "Sensors, GIS, IoT, Data Analysis", "8 weeks", "3 students", "https://images.unsplash.com/photo-1473448912268-2022ce9509d8?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-108", "Vikram Patil", "vikram@college.edu", var0, "Active");
      addSeedProject("PRJ-109", "Remote Patient Monitor", "HealthTech & Blockchain", "A secure patient monitoring dashboard that logs biomarkers and medical updates with consent-based blockchain verification for hospitals.", (double)3200.0F, (double)2850.0F, "Healthcare IoT, Blockchain, Security, Full Stack", "11 weeks", "4 students", "https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-109", "Tanvi Rao", "tanvi@college.edu", var0, "Active");
      addSeedProject("PRJ-110", "Medical Record Tracechain", "HealthTech & Blockchain", "A tamper-resistant healthcare data sharing app for students and clinics that tracks record access while protecting patient privacy.", (double)2700.0F, (double)1600.0F, "Blockchain, Java, Security, UI/UX", "10 weeks", "2 students", "https://images.unsplash.com/photo-1538108149393-fbbd81895973?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-110", "Yash Sutar", "yash@college.edu", var0, "Active");
      addSeedProject("PRJ-111", "Immersive Lab Simulator", "AR/VR & Design", "A virtual engineering lab walkthrough for students to practice equipment handling, machine operations, and safety procedures in 3D.", (double)2000.0F, (double)1600.0F, "Unity, Blender, C#, 3D Design", "9 weeks", "3 students", "https://images.unsplash.com/photo-1526379095098-d400fd0bf935?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-111", "Mehul Jain", "mehul@college.edu", var0, "Active");
      addSeedProject("PRJ-112", "AR Assembly Guide", "AR/VR & Design", "An augmented reality guide that overlays assembly steps on live machine parts so students can learn manufacturing workflows faster.", (double)1700.0F, (double)1450.0F, "ARCore, Unity, CAD, UX Design", "8 weeks", "2 students", "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?auto=format&fit=crop&w=800&q=80", "https://github.com", "https://demo.example.com", "STU-112", "Ananya Solanki", "ananya@college.edu", var0, "Active");
   }

   private static void addSeedProject(String var0, String var1, String var2, String var3, double var4, double var6, String var8, String var9, String var10, String var11, String var12, String var13, String var14, String var15, String var16, String var17, String var18) {
      Project var19 = new Project(var0, var1, var2, var3, var4, var6, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18);
      var19.getUpdates().add(new ProjectUpdate("UPD-" + System.currentTimeMillis() + "-" + var0, var0, "Project Launch", "Initial prototype and project goals are now visible to the student community.", var17));
      projects.add(var19);
   }

   private static void setCORSHeaders(HttpExchange var0) {
      var0.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
      var0.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
      var0.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
   }

   private static synchronized boolean accountExists(String var0) {
      for(Student var2 : students) {
         if (var2.getEmail().equalsIgnoreCase(var0)) {
            return true;
         }
      }

      for(Sponsor var4 : sponsors) {
         if (var4.getEmail().equalsIgnoreCase(var0)) {
            return true;
         }
      }

      return false;
   }

   private static synchronized String createRegisteredAccount(Map<String, String> var0) {
      String var1 = ((String)var0.getOrDefault("role", "student")).trim().toLowerCase();
      String var2 = ((String)var0.getOrDefault("name", "")).trim();
      String var3 = ((String)var0.getOrDefault("email", "")).trim().toLowerCase();
      String var4 = ((String)var0.getOrDefault("password", "")).trim();
      if (accountExists(var3)) {
         throw new IllegalArgumentException("Account already exists");
      } else if ("sponsor".equals(var1)) {
         String var12 = (String)var0.getOrDefault("type", "Individual");
         String var13 = (String)var0.getOrDefault("company", var2);
         String var14 = (String)var0.getOrDefault("bio", "Passionate supporter of youth technology projects.");
         int var17 = sponsors.size();
         String var15 = "SPO-" + (var17 + 201);
         Sponsor var16 = new Sponsor(var15, var2, var3, var4, var12, var13, var14);
         sponsors.add(var16);
         return "{\"success\":true,\"role\":\"sponsor\",\"user\":" + var16.toJson() + ",\"message\":\"Email verified. Sponsor account created successfully.\"}";
      } else {
         String var5 = (String)var0.getOrDefault("university", "Engineering Institute");
         String var6 = (String)var0.getOrDefault("department", "Computer Engineering");
         String var7 = (String)var0.getOrDefault("year", "3rd Year");
         String var8 = (String)var0.getOrDefault("skills", "Software, Hardware");
         String var9 = (String)var0.getOrDefault("bio", "Aspiring engineer and project creator.");
         int var10000 = students.size();
         String var10 = "STU-" + (var10000 + 101);
         Student var11 = new Student(var10, var2, var3, var4, var5, var6, var7, var8, var9);
         students.add(var11);
         return "{\"success\":true,\"role\":\"student\",\"user\":" + var11.toJson() + ",\"message\":\"Email verified. Student account created successfully.\"}";
      }
   }

   private static String createVerificationCode() {
      return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1000000));
   }

   private static byte[] hashVerificationCode(String var0) {
      try {
         return MessageDigest.getInstance("SHA-256").digest(var0.getBytes(StandardCharsets.UTF_8));
      } catch (NoSuchAlgorithmException var2) {
         throw new IllegalStateException("SHA-256 is not available.", var2);
      }
   }

   private static void sendVerificationEmail(String var0, String var1) throws IOException {
      String var2 = System.getenv("RESEND_API_KEY");
      String var3 = System.getenv("EMAIL_FROM");
      if (var2 != null && !var2.isBlank() && var3 != null && !var3.isBlank()) {
         String var4 = "<p>Your StudentFund verification code is:</p><p><strong>" + var1 + "</strong></p><p>This code expires in 10 minutes.</p>";
         String var10000 = escapeJson(var3);
         String var5 = "{\"from\":\"" + var10000 + "\",\"to\":[\"" + escapeJson(var0) + "\"],\"subject\":\"Verify your StudentFund email\",\"html\":\"" + escapeJson(var4) + "\"}";
         HttpRequest var6 = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails")).timeout(Duration.ofSeconds(20L)).header("Authorization", "Bearer " + var2).header("Content-Type", "application/json").POST(BodyPublishers.ofString(var5, StandardCharsets.UTF_8)).build();

         try {
            HttpResponse var7 = EMAIL_HTTP_CLIENT.send(var6, BodyHandlers.ofString());
            if (var7.statusCode() < 200 || var7.statusCode() >= 300) {
               throw new IOException("Resend returned HTTP " + var7.statusCode() + ".");
            }
         } catch (InterruptedException var8) {
            Thread.currentThread().interrupt();
            throw new IOException("Email delivery was interrupted.", var8);
         }
      } else {
         throw new IllegalStateException("Set RESEND_API_KEY and EMAIL_FROM on the backend.");
      }
   }

   private static void sendResponse(HttpExchange var0, int var1, String var2) throws IOException {
      sendResponse(var0, var1, var2, "application/json; charset=UTF-8");
   }

   private static void sendResponse(HttpExchange var0, int var1, String var2, String var3) throws IOException {
      byte[] var4 = var2.getBytes(StandardCharsets.UTF_8);
      var0.getResponseHeaders().set("Content-Type", var3);
      var0.sendResponseHeaders(var1, (long)var4.length);
      OutputStream var5 = var0.getResponseBody();

      try {
         var5.write(var4);
      } catch (Throwable var9) {
         if (var5 != null) {
            try {
               var5.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (var5 != null) {
         var5.close();
      }

   }

   private static String readRequestBody(HttpExchange var0) throws IOException {
      InputStream var1 = var0.getRequestBody();

      String var5;
      try {
         ByteArrayOutputStream var2 = new ByteArrayOutputStream();

         try {
            byte[] var3 = new byte[1024];

            int var4;
            while((var4 = var1.read(var3)) != -1) {
               var2.write(var3, 0, var4);
            }

            var5 = var2.toString(StandardCharsets.UTF_8.name());
         } catch (Throwable var8) {
            try {
               var2.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         var2.close();
      } catch (Throwable var9) {
         if (var1 != null) {
            try {
               var1.close();
            } catch (Throwable var6) {
               var9.addSuppressed(var6);
            }
         }

         throw var9;
      }

      if (var1 != null) {
         var1.close();
      }

      return var5;
   }

   private static double parseDouble(String var0, double var1) {
      if (var0 != null && !var0.trim().isEmpty()) {
         try {
            return Double.parseDouble(var0.trim().replace("$", "").replace(",", ""));
         } catch (NumberFormatException var4) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   public static String escapeJson(String var0) {
      if (var0 == null) {
         return "";
      } else {
         StringBuilder var1 = new StringBuilder();

         for(int var2 = 0; var2 < var0.length(); ++var2) {
            char var3 = var0.charAt(var2);
            switch (var3) {
               case '\b':
                  var1.append("\\b");
                  break;
               case '\t':
                  var1.append("\\t");
                  break;
               case '\n':
                  var1.append("\\n");
                  break;
               case '\f':
                  var1.append("\\f");
                  break;
               case '\r':
                  var1.append("\\r");
                  break;
               case '"':
                  var1.append("\\\"");
                  break;
               case '\\':
                  var1.append("\\\\");
                  break;
               default:
                  if (var3 < ' ') {
                     String var4 = String.format("\\u%04x", Integer.valueOf(var3));
                     var1.append(var4);
                  } else {
                     var1.append(var3);
                  }
            }
         }

         return var1.toString();
      }
   }

   public static Map<String, String> parseJsonSimple(String var0) {
      HashMap var1 = new HashMap();
      if (var0 != null && !var0.trim().isEmpty()) {
         String var2 = var0.trim();
         if (var2.startsWith("{")) {
            var2 = var2.substring(1);
         }

         if (var2.endsWith("}")) {
            var2 = var2.substring(0, var2.length() - 1);
         }

         boolean var3 = false;
         boolean var4 = false;
         StringBuilder var5 = new StringBuilder();
         ArrayList var6 = new ArrayList();

         for(int var7 = 0; var7 < var2.length(); ++var7) {
            char var8 = var2.charAt(var7);
            if (var4) {
               var5.append(var8);
               var4 = false;
            } else if (var8 == '\\') {
               var4 = true;
            } else if (var8 == '"') {
               var3 = !var3;
            } else if (var8 == ',' && !var3) {
               var6.add(var5.toString().trim());
               var5.setLength(0);
            } else {
               var5.append(var8);
            }
         }

         if (var5.length() > 0) {
            var6.add(var5.toString().trim());
         }

         for(String var13 : var6) {
            int var9 = var13.indexOf(58);
            if (var9 != -1) {
               String var10 = var13.substring(0, var9).trim().replaceAll("^\"|\"$", "");
               String var11 = var13.substring(var9 + 1).trim().replaceAll("^\"|\"$", "");
               var1.put(var10, var11);
            }
         }

         return var1;
      } else {
         return var1;
      }
   }

   static class StaticFileHandler implements HttpHandler {
      StaticFileHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            String var2 = var1.getRequestURI().getPath();
            if (var2 == null || var2.equals("/") || var2.equals("/index.html")) {
               File var3 = new File(System.getProperty("user.dir"), "index.html");
               if (!var3.exists() || !var3.isFile()) {
                  String var10 = "<html><body><h1>Micro-Sponsorship Portal</h1><p>index.html was not found in current directory.</p></body></html>";
                  Main.sendResponse(var1, 200, var10, "text/html; charset=UTF-8");
                  return;
               }

               byte[] var4 = Files.readAllBytes(var3.toPath());
               var1.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
               var1.sendResponseHeaders(200, (long)var4.length);
               OutputStream var5 = var1.getResponseBody();

               try {
                  var5.write(var4);
               } catch (Throwable var9) {
                  if (var5 != null) {
                     try {
                        var5.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (var5 != null) {
                  var5.close();
               }
            }

            Main.sendResponse(var1, 404, "{\"error\":\"Not Found\"}");
         }
      }
   }

   static class ProjectsHandler implements HttpHandler {
      ProjectsHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("GET".equalsIgnoreCase(var1.getRequestMethod())) {
               StringBuilder var2 = new StringBuilder("[");

               for(int var3 = 0; var3 < Main.projects.size(); ++var3) {
                  var2.append(((Project)Main.projects.get(var3)).toJson());
                  if (var3 < Main.projects.size() - 1) {
                     var2.append(",");
                  }
               }

               var2.append("]");
               Main.sendResponse(var1, 200, var2.toString());
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class CreateProjectHandler implements HttpHandler {
      CreateProjectHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else if ("GET".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = ((String)var3.getOrDefault("title", "")).trim();
               String var5 = ((String)var3.getOrDefault("category", "General")).trim();
               String var6 = ((String)var3.getOrDefault("description", "")).trim();
               double var7 = Main.parseDouble((String)var3.get("fundingGoal"), (double)0.0F);
               String var9 = ((String)var3.getOrDefault("requiredSkills", "")).trim();
               String var10 = ((String)var3.getOrDefault("duration", "")).trim();
               String var11 = ((String)var3.getOrDefault("teamMembers", "")).trim();
               String var12 = ((String)var3.getOrDefault("imageUrl", "")).trim();
               String var13 = ((String)var3.getOrDefault("githubUrl", "")).trim();
               String var14 = ((String)var3.getOrDefault("demoUrl", "")).trim();
               int var10002 = Main.students.size();
               String var15 = ((String)var3.getOrDefault("studentId", "STU-" + (var10002 + 100))).trim();
               String var16 = ((String)var3.getOrDefault("studentName", "Student Innovator")).trim();
               String var17 = ((String)var3.getOrDefault("studentEmail", "")).trim();
               if (var4.isEmpty() || var6.isEmpty() || var7 <= (double)0.0F) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Title, description, and positive funding goal are required.\"}");
                  return;
               }

               for(Project var19 : Main.projects) {
                  if (var19.getTitle().equalsIgnoreCase(var4)) {
                     Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"A project with this title already exists. Please choose a unique name.\"}");
                     return;
                  }
               }

               if (var12.isEmpty()) {
                  var12 = "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80";
               }

               String var21 = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
               Object[] var10001 = new Object[]{Main.projects.size() + 1};
               String var22 = "PRJ-" + String.format("%03d", var10001);
               Project var20 = new Project(var22, var4, var5, var6, var7, (double)0.0F, var9, var10, var11, var12, var13, var14, var15, var16, var17, var21, "Active");
               var20.getUpdates().add(new ProjectUpdate("UPD-" + System.currentTimeMillis() % 10000L, var22, "Project Created & Live", "Project officially launched on Micro-Sponsorship Portal! Open for community backing and mentorship.", var21));
               Main.projects.add(0, var20);
               Main.sendResponse(var1, 200, "{\"success\":true,\"message\":\"Project created successfully!\",\"project\":" + var20.toJson() + "}");
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class UpdateProjectHandler implements HttpHandler {
      UpdateProjectHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = (String)var3.get("id");
               if (var4 == null || var4.isEmpty()) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Project ID is required.\"}");
                  return;
               }

               Project var5 = null;

               for(Project var7 : Main.projects) {
                  if (var7.getId().equalsIgnoreCase(var4)) {
                     var5 = var7;
                     break;
                  }
               }

               if (var5 == null) {
                  Main.sendResponse(var1, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                  return;
               }

               if (var3.containsKey("title") && !((String)var3.get("title")).trim().isEmpty()) {
                  var5.setTitle(((String)var3.get("title")).trim());
               }

               if (var3.containsKey("category") && !((String)var3.get("category")).trim().isEmpty()) {
                  var5.setCategory(((String)var3.get("category")).trim());
               }

               if (var3.containsKey("description") && !((String)var3.get("description")).trim().isEmpty()) {
                  var5.setDescription(((String)var3.get("description")).trim());
               }

               if (var3.containsKey("fundingGoal")) {
                  double var8 = Main.parseDouble((String)var3.get("fundingGoal"), var5.getFundingGoal());
                  if (var8 > (double)0.0F && var8 >= var5.getCurrentFunding()) {
                     var5.setFundingGoal(var8);
                  } else if (var8 > (double)0.0F) {
                     Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Funding goal cannot be lower than the amount already raised.\"}");
                     return;
                  }
               }

               if (var3.containsKey("requiredSkills")) {
                  var5.setRequiredSkills(((String)var3.get("requiredSkills")).trim());
               }

               if (var3.containsKey("duration")) {
                  var5.setDuration(((String)var3.get("duration")).trim());
               }

               if (var3.containsKey("teamMembers")) {
                  var5.setTeamMembers(((String)var3.get("teamMembers")).trim());
               }

               if (var3.containsKey("imageUrl") && !((String)var3.get("imageUrl")).trim().isEmpty()) {
                  var5.setImageUrl(((String)var3.get("imageUrl")).trim());
               }

               if (var3.containsKey("githubUrl")) {
                  var5.setGithubUrl(((String)var3.get("githubUrl")).trim());
               }

               if (var3.containsKey("demoUrl")) {
                  var5.setDemoUrl(((String)var3.get("demoUrl")).trim());
               }

               Main.sendResponse(var1, 200, "{\"success\":true,\"message\":\"Project updated successfully!\",\"project\":" + var5.toJson() + "}");
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class DeleteProjectHandler implements HttpHandler {
      DeleteProjectHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = (String)var3.get("id");
               if (var4 == null || var4.isEmpty()) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Project ID is required.\"}");
                  return;
               }

               boolean var5 = Main.projects.removeIf((var1x) -> var1x.getId().equalsIgnoreCase(var4));
               Main.sponsorships.removeIf((var1x) -> var1x.getProjectId().equalsIgnoreCase(var4));
               if (var5) {
                  Main.sendResponse(var1, 200, "{\"success\":true,\"message\":\"Project deleted successfully.\"}");
               } else {
                  Main.sendResponse(var1, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
               }
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class SponsorHandler implements HttpHandler {
      SponsorHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = (String)var3.get("projectId");
               String var5 = ((String)var3.getOrDefault("sponsorName", "Anonymous Backer")).trim();
               String var6 = ((String)var3.getOrDefault("sponsorEmail", "sponsor@example.com")).trim();
               int var10002 = Main.sponsors.size();
               String var7 = ((String)var3.getOrDefault("sponsorId", "SPO-" + (var10002 + 201))).trim();
               String var8 = ((String)var3.getOrDefault("message", "Keep up the great engineering work!")).trim();
               double var9 = Main.parseDouble((String)var3.get("amount"), (double)0.0F);
               if (var4 == null || var4.isEmpty() || var9 <= (double)0.0F) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Valid project ID and positive sponsorship amount are required.\"}");
                  return;
               }

               Project var11 = null;

               for(Project var13 : Main.projects) {
                  if (var13.getId().equalsIgnoreCase(var4)) {
                     var11 = var13;
                     break;
                  }
               }

               if (var11 == null) {
                  Main.sendResponse(var1, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                  return;
               }

               if (var11.getCurrentFunding() >= var11.getFundingGoal()) {
                  Main.sendResponse(var1, 409, "{\"success\":false,\"message\":\"This project is already fully funded and cannot accept more sponsorships.\"}");
                  return;
               }

               if (var9 > var11.getFundingGoal() - var11.getCurrentFunding()) {
                  Main.sendResponse(var1, 409, "{\"success\":false,\"message\":\"Sponsorship exceeds this project's remaining funding limit.\"}");
                  return;
               }

               String var15 = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
               int var10000 = Main.sponsorships.size();
               String var16 = "SP-" + (var10000 + 101);
               Sponsorship var14 = new Sponsorship(var16, var11.getId(), var11.getTitle(), var7, var5, var6, var9, var8, var15);
               Main.sponsorships.add(var14);
               var11.getSponsorships().add(var14);
               var11.setCurrentFunding(var11.getCurrentFunding() + var9);
               if (var11.getCurrentFunding() >= var11.getFundingGoal()) {
                  var11.setStatus("Funded");
               }

               String var17 = String.format(Locale.US, "%.2f", var9);
               Main.sendResponse(var1, 200, "{\"success\":true,\"message\":\"Thank you! Sponsorship of $" + var17 + " was successfully processed.\",\"sponsorship\":" + var14.toJson() + ",\"project\":" + var11.toJson() + "}");
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class AddUpdateHandler implements HttpHandler {
      AddUpdateHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = (String)var3.get("projectId");
               String var5 = ((String)var3.getOrDefault("title", "")).trim();
               String var6 = ((String)var3.getOrDefault("description", "")).trim();
               String var7 = ((String)var3.getOrDefault("date", (new SimpleDateFormat("yyyy-MM-dd")).format(new Date()))).trim();
               if (var4 == null || var5.isEmpty() || var6.isEmpty()) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Project ID, update title, and description are required.\"}");
                  return;
               }

               Project var8 = null;

               for(Project var10 : Main.projects) {
                  if (var10.getId().equalsIgnoreCase(var4)) {
                     var8 = var10;
                     break;
                  }
               }

               if (var8 == null) {
                  Main.sendResponse(var1, 404, "{\"success\":false,\"message\":\"Project not found.\"}");
                  return;
               }

               int var10000 = var8.getUpdates().size();
               String var11 = "UPD-" + (var10000 + 1);
               ProjectUpdate var12 = new ProjectUpdate(var11, var8.getId(), var5, var6, var7);
               var8.getUpdates().add(0, var12);
               String var10002 = var12.toJson();
               Main.sendResponse(var1, 200, "{\"success\":true,\"message\":\"Project update posted successfully!\",\"update\":" + var10002 + ",\"project\":" + var8.toJson() + "}");
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class LoginHandler implements HttpHandler {
      LoginHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = ((String)var3.getOrDefault("email", "")).trim().toLowerCase();
               String var5 = ((String)var3.getOrDefault("password", "")).trim();
               String var6 = ((String)var3.getOrDefault("role", "student")).trim().toLowerCase();
               if (var4.isEmpty() || var5.isEmpty()) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Email and password are required.\"}");
                  return;
               }

               if ("student".equals(var6) || var6.isEmpty()) {
                  for(Student var8 : Main.students) {
                     if (var8.getEmail().equalsIgnoreCase(var4) && var8.getPassword().equals(var5)) {
                        String var10002 = var8.toJson();
                        Main.sendResponse(var1, 200, "{\"success\":true,\"role\":\"student\",\"user\":" + var10002 + ",\"message\":\"Welcome back, " + Main.escapeJson(var8.getName()) + "!\"}");
                        return;
                     }
                  }
               }

               if ("sponsor".equals(var6) || var6.isEmpty()) {
                  for(Sponsor var10 : Main.sponsors) {
                     if (var10.getEmail().equalsIgnoreCase(var4) && var10.getPassword().equals(var5)) {
                        String var11 = var10.toJson();
                        Main.sendResponse(var1, 200, "{\"success\":true,\"role\":\"sponsor\",\"user\":" + var11 + ",\"message\":\"Welcome back, " + Main.escapeJson(var10.getName()) + "!\"}");
                        return;
                     }
                  }
               }

               if (Main.pendingRegistrations.containsKey(var4)) {
                  Main.sendResponse(var1, 403, "{\"success\":false,\"message\":\"Please verify your email address before logging in.\"}");
                  return;
               }

               Main.sendResponse(var1, 401, "{\"success\":false,\"message\":\"Invalid email or password.\"}");
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class RegisterHandler implements HttpHandler {
      RegisterHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("POST".equalsIgnoreCase(var1.getRequestMethod())) {
               String var2 = Main.readRequestBody(var1);
               Map var3 = Main.parseJsonSimple(var2);
               String var4 = ((String)var3.getOrDefault("role", "student")).trim().toLowerCase();
               String var5 = ((String)var3.getOrDefault("name", "")).trim();
               String var6 = ((String)var3.getOrDefault("email", "")).trim().toLowerCase();
               String var7 = ((String)var3.getOrDefault("password", "")).trim();
               if (var5.isEmpty() || var6.isEmpty() || var7.isEmpty()) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Name, email, and password are required.\"}");
                  return;
               }

               if (!"student".equals(var4) && !"sponsor".equals(var4)) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Choose a valid account type.\"}");
                  return;
               }

               if (!var6.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"Enter a valid email address.\"}");
                  return;
               }

               var3.put("role", var4);
               var3.put("email", var6);
               if (Main.accountExists(var6)) {
                  Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"An account with this email already exists.\"}");
                  return;
               }

               PendingRegistration var8 = (PendingRegistration)Main.pendingRegistrations.get(var6);
               if (var8 != null) {
                  if (System.currentTimeMillis() <= var8.expiresAt) {
                     Main.sendResponse(var1, 409, "{\"success\":false,\"verificationRequired\":true,\"email\":\"" + Main.escapeJson(var6) + "\",\"message\":\"A verification code was already sent. Use the resend option.\"}");
                     return;
                  }

                  Main.pendingRegistrations.remove(var6, var8);
               }

               String var9 = Main.createVerificationCode();
               PendingRegistration var10 = new PendingRegistration(var3, var9);
               if (Main.pendingRegistrations.putIfAbsent(var6, var10) != null) {
                  Main.sendResponse(var1, 409, "{\"success\":false,\"message\":\"A verification code was already sent. Use the resend option.\"}");
                  return;
               }

               try {
                  Main.sendVerificationEmail(var6, var9);
                  var10.refreshCode(var9, System.currentTimeMillis());
               } catch (IllegalStateException var12) {
                  Main.pendingRegistrations.remove(var6, var10);
                  Main.sendResponse(var1, 503, "{\"success\":false,\"message\":\"Email verification is not configured on the server.\"}");
                  return;
               } catch (IOException var13) {
                  Main.pendingRegistrations.remove(var6, var10);
                  Main.sendResponse(var1, 502, "{\"success\":false,\"message\":\"Could not send the verification email. Check the email provider configuration.\"}");
                  return;
               }

               Main.sendResponse(var1, 200, "{\"success\":true,\"verificationRequired\":true,\"email\":\"" + Main.escapeJson(var6) + "\",\"message\":\"A verification code was sent to your email address.\"}");
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class VerifyEmailHandler implements HttpHandler {
      VerifyEmailHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else if (!"POST".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
         } else {
            Map var2 = Main.parseJsonSimple(Main.readRequestBody(var1));
            String var3 = ((String)var2.getOrDefault("email", "")).trim().toLowerCase();
            String var4 = ((String)var2.getOrDefault("code", "")).trim();
            PendingRegistration var5 = (PendingRegistration)Main.pendingRegistrations.get(var3);
            if (var5 == null) {
               Main.sendResponse(var1, 404, "{\"success\":false,\"message\":\"No pending registration was found. Register again to request a new code.\"}");
            } else {
               synchronized(var5) {
                  long var7 = System.currentTimeMillis();
                  if (var7 > var5.expiresAt) {
                     Main.pendingRegistrations.remove(var3, var5);
                     Main.sendResponse(var1, 410, "{\"success\":false,\"message\":\"The verification code expired. Register again to request a new code.\"}");
                  } else if (var5.attempts >= 5) {
                     Main.pendingRegistrations.remove(var3, var5);
                     Main.sendResponse(var1, 429, "{\"success\":false,\"message\":\"Too many incorrect codes. Register again to restart verification.\"}");
                  } else if (var4.matches("[0-9]{6}") && MessageDigest.isEqual(var5.codeHash, Main.hashVerificationCode(var4))) {
                     try {
                        String var9 = Main.createRegisteredAccount(var5.registrationData);
                        Main.pendingRegistrations.remove(var3, var5);
                        Main.sendResponse(var1, 200, var9);
                     } catch (IllegalArgumentException var11) {
                        Main.pendingRegistrations.remove(var3, var5);
                        Main.sendResponse(var1, 409, "{\"success\":false,\"message\":\"An account with this email already exists.\"}");
                     }

                  } else {
                     ++var5.attempts;
                     if (var5.attempts >= 5) {
                        Main.pendingRegistrations.remove(var3, var5);
                        Main.sendResponse(var1, 429, "{\"success\":false,\"message\":\"Too many incorrect codes. Register again to restart verification.\"}");
                     } else {
                        Main.sendResponse(var1, 400, "{\"success\":false,\"message\":\"That verification code is incorrect. Try again.\"}");
                     }

                  }
               }
            }
         }
      }
   }

   static class ResendVerificationHandler implements HttpHandler {
      ResendVerificationHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else if (!"POST".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
         } else {
            Map var2 = Main.parseJsonSimple(Main.readRequestBody(var1));
            String var3 = ((String)var2.getOrDefault("email", "")).trim().toLowerCase();
            PendingRegistration var4 = (PendingRegistration)Main.pendingRegistrations.get(var3);
            if (var4 == null) {
               Main.sendResponse(var1, 404, "{\"success\":false,\"message\":\"No pending verification was found. Register again to request a code.\"}");
            } else {
               synchronized(var4) {
                  long var6 = System.currentTimeMillis();
                  if (var6 > var4.expiresAt) {
                     Main.pendingRegistrations.remove(var3, var4);
                     Main.sendResponse(var1, 410, "{\"success\":false,\"message\":\"The verification request expired. Register again to restart.\"}");
                  } else {
                     long var8 = 60000L - (var6 - var4.lastSentAt);
                     if (var8 > 0L) {
                        long var16 = (var8 + 999L) / 1000L;
                        Main.sendResponse(var1, 429, "{\"success\":false,\"message\":\"Please wait " + var16 + " seconds before requesting another code.\"}");
                     } else {
                        String var10 = Main.createVerificationCode();

                        try {
                           Main.sendVerificationEmail(var3, var10);
                           var4.refreshCode(var10, var6);
                           Main.sendResponse(var1, 200, "{\"success\":true,\"message\":\"A new verification code was sent.\"}");
                        } catch (IllegalStateException var13) {
                           Main.sendResponse(var1, 503, "{\"success\":false,\"message\":\"Email verification is not configured on the server.\"}");
                        } catch (IOException var14) {
                           Main.sendResponse(var1, 502, "{\"success\":false,\"message\":\"Could not send the verification email. Check the email provider configuration.\"}");
                        }

                     }
                  }
               }
            }
         }
      }
   }

   static class StatsHandler implements HttpHandler {
      StatsHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("GET".equalsIgnoreCase(var1.getRequestMethod())) {
               double var2 = (double)0.0F;
               int var4 = 0;

               for(Project var6 : Main.projects) {
                  var2 += var6.getCurrentFunding();
                  if (var6.getCurrentFunding() >= var6.getFundingGoal()) {
                     ++var4;
                  }
               }

               String var7 = String.format(Locale.US, "{\"totalStudents\":%d,\"totalSponsors\":%d,\"totalProjects\":%d,\"totalSponsorship\":%.2f,\"completedProjects\":%d}", Main.students.size(), Main.sponsors.size(), Main.projects.size(), var2, var4);
               Main.sendResponse(var1, 200, var7);
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class StudentsHandler implements HttpHandler {
      StudentsHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("GET".equalsIgnoreCase(var1.getRequestMethod())) {
               StringBuilder var2 = new StringBuilder("[");

               for(int var3 = 0; var3 < Main.students.size(); ++var3) {
                  var2.append(((Student)Main.students.get(var3)).toJson());
                  if (var3 < Main.students.size() - 1) {
                     var2.append(",");
                  }
               }

               var2.append("]");
               Main.sendResponse(var1, 200, var2.toString());
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   static class SponsorsHandler implements HttpHandler {
      SponsorsHandler() {
      }

      public void handle(HttpExchange var1) throws IOException {
         Main.setCORSHeaders(var1);
         if ("OPTIONS".equalsIgnoreCase(var1.getRequestMethod())) {
            Main.sendResponse(var1, 204, "");
         } else {
            if ("GET".equalsIgnoreCase(var1.getRequestMethod())) {
               StringBuilder var2 = new StringBuilder("[");

               for(int var3 = 0; var3 < Main.sponsors.size(); ++var3) {
                  var2.append(((Sponsor)Main.sponsors.get(var3)).toJson());
                  if (var3 < Main.sponsors.size() - 1) {
                     var2.append(",");
                  }
               }

               var2.append("]");
               Main.sendResponse(var1, 200, var2.toString());
            } else {
               Main.sendResponse(var1, 405, "{\"error\":\"Method Not Allowed\"}");
            }

         }
      }
   }

   private static final class PendingRegistration {
      private final Map<String, String> registrationData;
      private byte[] codeHash;
      private long expiresAt;
      private long lastSentAt;
      private int attempts;

      private PendingRegistration(Map<String, String> var1, String var2) {
         this.registrationData = new HashMap(var1);
         this.refreshCode(var2, System.currentTimeMillis());
      }

      private void refreshCode(String var1, long var2) {
         this.codeHash = Main.hashVerificationCode(var1);
         this.expiresAt = var2 + 600000L;
         this.lastSentAt = var2;
         this.attempts = 0;
      }
   }
}

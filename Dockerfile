FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac Main.java
EXPOSE 8080
CMD ["java", "Main"]

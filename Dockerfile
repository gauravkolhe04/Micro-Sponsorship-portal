FROM openjdk:17-jdk-slim
WORKDIR /app
COPY . .
RUN javac Main.java
EXPOSE 8080
CMD ["java", "Main"]

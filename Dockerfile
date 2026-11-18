# Step 1: Use a lightweight, professional Java runtime environment
FROM openjdk:17-alpine

# Step 2: Set the internal workspace directory inside the container
WORKDIR /app

# Step 3: Copy your source code, libraries, and assets into the container
COPY Server.java .
COPY index.html .
COPY mysql-connector-j-9.7.0.jar .
COPY lib/ ./lib/

# Step 4: Compile the Java application inside the container environment
RUN javac -cp ".:mysql-connector-j-9.7.0.jar:lib/*" Server.java

# Step 5: Expose the network port your server runs on
EXPOSE 8080

# Step 6: Command to execute your application when the container starts
CMD ["java", "-cp", ".:mysql-connector-j-9.7.0.jar:lib/*", "Server"]

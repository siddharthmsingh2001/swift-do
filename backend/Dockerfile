# ------------------------------------------------------------
# WHY MULTI-STAGE?
# 1. Build Stage (JDK): Contains Maven and compilers to turn code into a JAR.
# 2. Runtime Stage (JRE): A slimmed-down "Java-only" environment.
# We copy ONLY the finished JAR to Stage 2, leaving behind hundreds of MBs
# of source code and build tools. This makes the final image smaller,
# faster to deploy, and more secure.
# ------------------------------------------------------------

# This stage is for turning source code into .jar file, we use an image that has java
# and maven pre-installed and name it builder so we can refer to it later
FROM maven:3.9.9-eclipse-temurin-17 AS builder

# Set the workdirectory for all consequtive commands
WORKDIR /build

# We copy the dependency list first because they rarely change.
# Docker caches the "downloading dependency step" If we change the Java code but not
# the pom.xml docker will skip the long download process
COPY pom.xml .
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN mvn -B dependency:go-offline

# Now we copy ourr actual code since this will change often.
COPY src src

# Build the application (Tests should run before Docker build in CI, not inside it)
RUN mvn -B package -DskipTests

# We now use the JRE in which our actual applciation will run.
FROM eclipse-temurin:17-jre-jammy

# Create non-root user (important for ECS security)
RUN groupadd -r app && useradd -r -g app app

WORKDIR /app

# Copy only the fat JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Switch to non-root user
USER app

# Expose application port (for documentation & ALB)
EXPOSE 8080

# JVM options tuned for containers
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:InitialRAMPercentage=50.0","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]

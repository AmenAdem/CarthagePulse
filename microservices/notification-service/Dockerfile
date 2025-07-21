FROM openjdk:17-jdk-slim

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && apt-get clean

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Expose port
EXPOSE 8083

# Run the application
CMD ["java", "-jar", "target/notification-service-1.0.0.jar"]
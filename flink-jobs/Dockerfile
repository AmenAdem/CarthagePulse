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

# Copy the JAR to Flink usrlib
RUN mkdir -p /opt/flink/usrlib && cp target/flink-jobs-1.0.0.jar /opt/flink/usrlib/

# This image is used to build the Flink jobs
# The actual execution happens in the Flink containers
CMD ["echo", "Flink jobs built successfully"]
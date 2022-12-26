FROM openjdk:17-slim-buster

# Add the necessary files and directories
ADD . /app
WORKDIR /app

# Build the application using the gradlew wrapper
RUN ./gradlew bootJar

# Set the working directory to the directory where the jar file is located
WORKDIR /app/build/libs

# Run the jar file
ENTRYPOINT ["java", "-jar", "package-zipper.jar"]

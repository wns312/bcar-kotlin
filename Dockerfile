FROM selenium/standalone-chrome:latest
LABEL authors="jyk"

USER root

RUN apt-get update && \
    apt-get install -y openjdk-17-jdk && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR app

COPY ./build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar", "testRunner"]
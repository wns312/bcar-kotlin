FROM selenium/standalone-chrome:latest
LABEL authors="jyk"

USER root

RUN apt-get update && \
apt-get install -y openjdk-17-jre-headless && \
apt-get clean && \
rm -rf /var/lib/apt/lists/*

WORKDIR app

COPY ./build/libs/*.jar app.jar

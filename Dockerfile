FROM mcr.microsoft.com/playwright:v1.50.0-jammy
LABEL authors="jyk"

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

USER root

RUN apt-get update && \
apt-get install -y openjdk-17-jre-headless && \
apt-get clean && \
rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY ./build/libs/*.jar app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]

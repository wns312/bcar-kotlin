FROM openjdk:17-jdk-slim
LABEL authors="jyk"
# 실행용 디렉토리
WORKDIR /app

ARG JAR_FILE

# Builder 스테이지에서 생성된 JAR 파일만 복사
COPY ${JAR_FILE} ./app.jar

# 포트 및 실행 커맨드
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
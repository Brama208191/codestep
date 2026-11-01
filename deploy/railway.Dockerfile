# One container for Railway: the React app, the API and the judge service, no Docker or Redis needed.
# See docs/deployment.md.

# 1. Build the React app
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2. Build the API jar with the app bundled as static files
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /backend
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src src
COPY --from=frontend /frontend/dist src/main/resources/static
RUN mvn -q -B package -DskipTests

# 3. Runtime. The judge part matches runner/Dockerfile; keep the two in step.
FROM eclipse-temurin:21-jdk-alpine
RUN apk add --no-cache socat su-exec tini \
    && adduser -D -H -u 61000 -s /sbin/nologin judge \
    && adduser -D -H -u 10001 -s /sbin/nologin app \
    && mkdir /app
COPY runner/TestRunner.java /opt/runner/TestRunner.java
RUN javac -d /opt/runner /opt/runner/TestRunner.java && rm /opt/runner/TestRunner.java
COPY runner/runner.policy /opt/runner/runner.policy
COPY runner/run.sh runner/judged.sh deploy/railway-start.sh /usr/local/bin/
RUN sed -i 's/\r$//' /usr/local/bin/*.sh /opt/runner/runner.policy \
    && chmod 755 /usr/local/bin/*.sh && chmod 644 /opt/runner/*
COPY --from=backend /backend/target/codestep-backend-0.1.0.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=single
# A fixed heap keeps the bill predictable; C1-only compilation and Serial GC start faster after sleeping.
ENV JAVA_OPTS="-Xms64m -Xmx256m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m"
EXPOSE 8080
# Starts as root only to hand each part its own user; see railway-start.sh.
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/railway-start.sh"]

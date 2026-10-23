FROM node:22-bookworm-slim AS frontend
WORKDIR /build/ui
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY backend/src/main/ backend/src/main/
COPY --from=frontend /build/ui/dist/ ui/dist/
RUN mvn -B -f backend/pom.xml -Prelease -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN mkdir -p /app/data/knowledge /app/data/settings && chown -R 10001:10001 /app/data
COPY --from=backend /build/backend/target/prismprd-backend-0.1.0.jar /app/prismprd.jar
ENV PRISM_BIND_ADDRESS=0.0.0.0
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/prismprd.jar"]

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 코드만 바뀐 재빌드에서는 Gradle 캐시를 재사용합니다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 컨테이너 침해 시 영향을 줄이기 위해 애플리케이션을 root로 실행하지 않습니다.
RUN addgroup -S lunchpulse && adduser -S lunchpulse -G lunchpulse
COPY --from=builder --chown=lunchpulse:lunchpulse /workspace/build/libs/*.jar app.jar

USER lunchpulse
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

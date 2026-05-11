# 실행을 위한 베이스 이미지 (JRE만 포함된 가벼운 버전 사용)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 로컬에서 ./gradlew bootJar로 빌드된 jar 파일을 컨테이너 내부로 복사
# build/libs/*.jar 패턴을 사용하여 유연하게 대응
COPY build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
# 로컬 빌드 환경이므로 프로필을 유연하게 조절할 수 있도록 설정 유지
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]

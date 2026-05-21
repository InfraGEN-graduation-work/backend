# 빌드 스테이지
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Gradle 래퍼 및 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 미리 다운로드
RUN ./gradlew dependencies --no-daemon

# 소스 복사 및 빌드 (테스트는 CI 단계에서 수행하므로 제외하여 속도 향상)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# CDS 클래스패스 일치를 위해 JAR 파일을 실행 경로와 동일한 위치로 복사
RUN cp build/libs/app.jar app.jar

# CDS(Class Data Sharing) 트레이닝
# 실행 경로인 ./app.jar 기준으로 트레이닝을 진행하여 일관성 확보
# 빌드 시점에 외부 데이터베이스나 Redis 연결 없이도 로딩이 성공적으로 수행되도록 자동설정을 제외
RUN java -XX:ArchiveClassesAtExit=app.jsa \
         -Dspring.context.exit=onRefresh \
         -Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration \
         -jar app.jar || true

# 실행 스테이지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 보안을 위해 비루트(Non-root) 사용자 생성
RUN useradd -m spring
USER spring

# 빌드 스테이지에서 준비된 JAR와 CDS 아카이브를 복사
COPY --from=build /app/app.jar app.jar
COPY --from=build /app/app.jsa app.jsa

EXPOSE 8080

# 일관된 경로(app.jar)와 CDS 아카이브를 활용하여 실행
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]

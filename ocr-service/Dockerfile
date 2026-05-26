FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/ocr-service-*.jar /app/ocr-service.jar
EXPOSE 8090
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/ocr-service.jar"]

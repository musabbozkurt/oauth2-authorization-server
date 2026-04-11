# ─────────────────────────────────────────────────────────────────────────────
# Multi-stage Dockerfile for oauth2-authorization-server
# Produces a minimal image using jlink custom JRE (~50% smaller than full JDK)
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:4.0.0-rc-5-ibm-semeru-25-noble AS build

WORKDIR /app

# Copy pom.xml first and resolve dependencies (layer caching)
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B -q || true

# Copy entire project context so ANY file change invalidates the build cache.
# Non-build files (docs, keys, IDE config, etc.) are excluded via .dockerignore.
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -q

# ── Stage 2: Custom JRE via jlink ────────────────────────────────────────────
# Create a minimal Java runtime with only the modules the application needs.
# Modules:
#   java.base              – core (always included)
#   java.sql               – JDBC (MariaDB, Oracle)
#   java.naming            – JNDI (Spring datasource lookup, LDAP)
#   java.net.http          – RestClient / HTTP client
#   java.xml               – Spring / Thymeleaf XML processing
#   java.desktop           – AWT dependencies pulled by some libraries
#   java.management        – JMX (Actuator, Micrometer)
#   jdk.management         – platform MXBeans (Actuator /health, /metrics)
#   java.instrument        – Spring agent / ByteBuddy
#   java.security.jgss     – Kerberos / GSSAPI (LDAP, broker auth)
#   java.security.sasl     – SASL authentication
#   jdk.crypto.ec          – TLS with EC algorithms
#   java.compiler          – javax.lang.model.SourceVersion
#   java.logging           – JUL bridge for Logback
#   java.transaction.xa    – JTA (JPA / Hibernate)
#   jdk.unsupported        – sun.misc.Unsafe (Netty, gRPC, serialisation)
#   jdk.net                – extended socket options (Netty epoll)
FROM build AS jre-builder

RUN "$JAVA_HOME"/bin/jlink \
      --add-modules java.base,java.sql,java.naming,java.net.http,java.xml,\
java.desktop,java.management,jdk.management,java.instrument,\
java.security.jgss,java.security.sasl,jdk.crypto.ec,java.compiler,\
java.logging,java.transaction.xa,jdk.unsupported,jdk.net \
      --strip-java-debug-attributes \
      --no-man-pages \
      --no-header-files \
      --compress=zip-6 \
      --output /javaruntime

# Strip debug symbols from native libs
RUN find /javaruntime -name '*.so' -exec strip --strip-unneeded {} + 2>/dev/null || true

# ── Stage 3: Extract Spring Boot layers ──────────────────────────────────────
FROM build AS extractor

WORKDIR /extract
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ── Stage 4: Final runtime image ─────────────────────────────────────────────
FROM debian:bookworm-slim

# Security: run as non-root
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

ENV JAVA_HOME=/opt/java/jdk25
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app

# Copy custom JRE
COPY --from=jre-builder /javaruntime ${JAVA_HOME}

# Copy extracted layers (best Docker cache utilisation)
COPY --from=extractor /extract/extracted/dependencies/          ./
COPY --from=extractor /extract/extracted/spring-boot-loader/    ./
COPY --from=extractor /extract/extracted/snapshot-dependencies/ ./
COPY --from=extractor /extract/extracted/application/           ./

# Create directory for JWT RSA key persistence
RUN mkdir -p /app/keys && chown appuser:appgroup /app/keys

# Switch to non-root user
USER appuser

EXPOSE 9000

# --enable-native-access=ALL-UNNAMED: Java 25 requires explicit permission
# for libraries accessing native code (Netty, Jedis, gRPC, Redisson, etc.)
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

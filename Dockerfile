# ─────────────────────────────────────────────────────────────────────────────
# Multi-stage Dockerfile for oauth2-authorization-server
# Produces a minimal image: jlink custom JRE (zip-9) on a distroless glibc base (no shell/apt/dpkg)
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build + extract Spring Boot layers ──────────────────────────────
FROM maven:4.0.0-rc-7-eclipse-temurin-27-resolute AS build

WORKDIR /app

# Copy pom.xml first and resolve dependencies (layer caching)
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B -q || true

# Copy entire project context so ANY file change invalidates the build cache.
# Non-build files (docs, keys, IDE config, etc.) are excluded via .dockerignore.
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -q

# Extract layers here (no separate extractor stage) and pre-create the writable keys dir
RUN mkdir -p /extract/keys \
    && cp target/*.jar /extract/app.jar \
    && cd /extract \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted \
    && rm app.jar

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
# Only the JDK is needed here (not Maven), so the stage is independent of the build and cached by content.
FROM eclipse-temurin:27-jdk-resolute AS jre-builder

RUN "$JAVA_HOME"/bin/jlink \
      --add-modules java.base,java.sql,java.naming,java.net.http,java.xml,\
java.desktop,java.management,jdk.management,java.instrument,\
java.security.jgss,java.security.sasl,jdk.crypto.ec,java.compiler,\
java.logging,java.transaction.xa,jdk.unsupported,jdk.net \
      --strip-java-debug-attributes \
      --no-man-pages \
      --no-header-files \
      --compress=zip-9 \
      --exclude-files="**/legal/**,**.diz" \
      --output /javaruntime

# Strip debug symbols from native libs
RUN find /javaruntime -name '*.so' -exec strip --strip-unneeded {} + 2>/dev/null || true

# ── Stage 3: Final runtime image ─────────────────────────────────────────────
# distroless/cc = glibc + libstdc++/libgcc (what libjvm needs) + CA certs + tzdata, runs as non-root (uid 65532).
FROM gcr.io/distroless/cc-debian12:nonroot

ENV JAVA_HOME=/opt/java/jdk27
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app

COPY --from=jre-builder /javaruntime ${JAVA_HOME}

# Extracted layers (best Docker cache utilisation)
COPY --from=build /extract/extracted/dependencies/          ./
COPY --from=build /extract/extracted/spring-boot-loader/    ./
COPY --from=build /extract/extracted/snapshot-dependencies/ ./
COPY --from=build /extract/extracted/application/           ./

# Directory for JWT RSA key persistence, writable by the nonroot user
COPY --from=build --chown=nonroot:nonroot /extract/keys /app/keys

USER nonroot

EXPOSE 9000

# --enable-native-access=ALL-UNNAMED: Java 27 requires explicit permission
# for libraries accessing native code (Netty, Jedis, gRPC, Redisson, etc.)
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

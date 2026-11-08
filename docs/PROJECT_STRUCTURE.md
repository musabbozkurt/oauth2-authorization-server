# OAuth2 Authorization Server — Project Structure & Architecture

## Project Overview

**OAuth2 Authorization Server** built with **Spring Boot 4.1.2** and **Java 26**. A feature-rich, production-oriented
OAuth2 authorization server with extensive AI/LLM integration, enterprise security, and full observability.

- **Build tool**: Maven
- **Framework**: Spring Boot 4, Spring Security OAuth2 Authorization Server
- **AI Framework**: Spring AI 2.0.0-M8
- **Runtime port**: 9000
- **Package**: `mb.oauth2authorizationserver`

---

## Source Code Structure

```
src/main/java/mb/oauth2authorizationserver/
├── OAuth2AuthorizationServerApplication.java      # Main Spring Boot entry point
├── api/
│   ├── controller/
│   │   ├── AdminController.java
│   │   ├── AIReviewDemoController.java
│   │   ├── ExcelController.java
│   │   ├── FileController.java
│   │   ├── LoginController.java
│   │   ├── OracleToolsController.java
│   │   ├── UserController.java
│   │   └── ai/
│   │       ├── AnthropicController.java
│   │       ├── DeepSeekController.java
│   │       ├── Gemma3ChatController.java
│   │       ├── GoogleGenAiController.java
│   │       ├── ModelRouterChatController.java
│   │       ├── OllamaChatController.java
│   │       ├── OpenAISdkController.java
│   │       ├── RedisChatMemoryController.java
│   │       └── SafeGuardrailChatController.java
│   ├── filter/
│   │   ├── ClientCookieFilter.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── MdcLoggingFilter.java
│   │   ├── PasswordEncodingDebugFilter.java
│   │   ├── SessionRegistryFilter.java
│   │   ├── XssFilter.java
│   │   └── XssRequestWrapper.java
│   ├── request/
│   │   ├── ApiUserRequest.java
│   │   ├── DatabaseConfig.java
│   │   ├── MigrationRequest.java
│   │   ├── RoutingDecision.java
│   │   └── ScriptGenerationRequest.java
│   └── response/
│       ├── ApiUserResponse.java
│       ├── ChatResponse.java
│       └── ScriptGenerationResponse.java
├── client/
│   └── webmagic/
│       ├── BookScraper.java
│       └── response/
│           └── Book.java
├── config/
│   ├── ContextPropagationConfig.java
│   ├── CustomKeyGenerator.java
│   ├── CustomLdapProperties.java
│   ├── DemoClientConfig.java
│   ├── GenericCacheKey.java
│   ├── InstallOpenTelemetryAppender.java
│   ├── JacksonConfig.java
│   ├── LdapConfiguredCondition.java
│   ├── MinioConfigProperties.java
│   ├── ObservationConfig.java
│   ├── OpenAPIConfig.java
│   ├── OracleCqnListener.java
│   ├── SchedulerInitializer.java
│   ├── SessionConfig.java
│   ├── ai/
│   │   ├── McpServerConfig.java
│   │   └── OpenAIConfig.java
│   ├── ai/advisor/
│   │   ├── PiiMaskingAdvisor.java
│   │   └── SecretLeakAdvisor.java
│   ├── jpa/
│   │   ├── CustomCamelCaseToUnderscoresNamingStrategyFactory.java
│   │   └── CustomPhysicalNamingStrategy.java
│   └── security/
│       ├── AuthExceptionEntryPoint.java
│       ├── CustomAuthenticationDetailsSource.java
│       ├── CustomPasswordAuthenticationToken.java
│       ├── CustomPasswordOAuth2ClientAuthenticationToken.java
│       ├── JwtBearerGrantAuthenticationToken.java
│       ├── SecurityConfig.java
│       ├── builder/
│       │   ├── AuthorizationBuilderService.java
│       │   ├── GenericBuilderService.java
│       │   ├── RegisteredClientBuilderService.java
│       │   └── impl/
│       │       ├── AuthorizationBuilderServiceImpl.java
│       │       └── RegisteredClientBuilderServiceImpl.java
│       ├── converter/
│       │   ├── CustomPasswordAuthenticationConverter.java
│       │   ├── JwtBearerGrantAuthenticationConverter.java
│       │   └── LegacyOAuth2TokenEndpointAuthenticationConverter.java
│       ├── handler/
│       │   ├── CustomAccessDeniedHandler.java
│       │   ├── CustomAuthenticationFailureHandler.java
│       │   ├── CustomSavedRequestAwareAuthenticationSuccessHandler.java
│       │   ├── CustomSimpleUrlAuthenticationFailureHandler.java
│       │   └── package-info.java
│       ├── model/
│       │   ├── CustomPasswordUser.java
│       │   └── HttpRequestDetails.java
│       ├── provider/
│       │   ├── CustomAuthenticationProvider.java
│       │   ├── CustomPasswordAuthenticationProvider.java
│       │   ├── CustomRefreshTokenAuthenticationProvider.java
│       │   ├── JwtBearerGrantAuthenticationProvider.java
│       │   └── UsernamePasswordAuthenticationTokenAuthenticationProvider.java
│       ├── service/
│       │   ├── CustomAuthenticationService.java
│       │   ├── TokenService.java
│       │   ├── UserLoginAttemptService.java
│       │   └── impl/
│       │       ├── CustomAuthenticationServiceImpl.java
│       │       ├── CustomOneTimeTokenServiceImpl.java
│       │       ├── OAuth2AuthorizationServiceImpl.java
│       │       ├── OneTimeTokenSuccessHandlerImpl.java
│       │       ├── RegisteredClientRepositoryImpl.java
│       │       ├── TokenServiceImpl.java
│       │       ├── UserDetailsManagerImpl.java
│       │       └── UserLoginAttemptServiceImpl.java
├── constants/
│   ├── ErrorMessageConstants.java
│   └── ServiceConstants.java
├── data/
│   ├── entity/
│   │   ├── Authority.java
│   │   ├── Authorization.java
│   │   ├── Client.java
│   │   ├── SecurityUser.java
│   │   └── UserLoginAttempt.java
│   └── repository/
│       ├── AuthorityRepository.java
│       ├── AuthorizationRepository.java
│       ├── ClientRepository.java
│       ├── UserLoginAttemptRepository.java
│       └── UserRepository.java
├── exception/
│   ├── BaseException.java
│   ├── ErrorCode.java
│   ├── ErrorResponse.java
│   ├── LocalizedExceptionResponse.java
│   ├── OAuth2AuthorizationServerServiceErrorCode.java
│   └── RestResponseExceptionHandler.java
├── mapper/
│   ├── CustomObjectMapper.java
│   └── UserMapper.java
├── model/
│   ├── enums/
│   │   ├── AuthorityType.java
│   │   ├── CustomerTier.java
│   │   ├── GrantType.java
│   │   ├── LoginStatus.java
│   │   └── ModelTier.java
│   ├── request/
│   │   ├── ClientFormData.java
│   │   ├── ClientUpdateFormData.java
│   │   ├── DiscountRequest.java
│   │   └── UserFormData.java
├── service/
│   ├── AdminService.java
│   ├── ClientService.java
│   ├── DemoReviewService.java
│   ├── ExcelService.java
│   ├── ModelRouterService.java
│   ├── OracleToolsService.java
│   ├── SecurityService.java
│   ├── UserService.java
│   ├── ai/
│   │   ├── VectorStoreService.java
│   │   └── WeatherService.java
│   ├── impl/
│   │   ├── AdminServiceImpl.java
│   │   ├── ClientServiceImpl.java
│   │   ├── ExcelServiceImpl.java
│   │   ├── ModelRouterServiceImpl.java
│   │   ├── OracleToolsServiceImpl.java
│   │   ├── SecurityServiceImpl.java
│   │   └── UserServiceImpl.java
└── utils/
    ├── ClientUtils.java
    ├── CustomSerializationUtils.java
    ├── DataCompressionUtils.java
    ├── FileUtils.java
    ├── SecurityUtils.java
    ├── StringCollectionCompressorUtils.java
    └── XssSanitizerUtils.java
```

---

## Core Features

### 1. OAuth2 Authorization Server (`config/security/SecurityConfig.java`)

- **Authorization code flow**, **password grant**, **refresh token grant**, **JWT bearer grant**
- **OpenID Connect 1.0** support
- **One-Time Token (OTT)** login with MFA support
- Custom authentication providers:
    - `CustomPasswordAuthenticationProvider` — custom password authentication
    - `CustomRefreshTokenAuthenticationProvider` — refresh token handling
    - `JwtBearerGrantAuthenticationProvider` — JWT bearer grant
    - `DaoAuthenticationProvider` — standard Spring Security DAO provider
- **LDAP authentication** (conditional via `LdapConfiguredCondition`)
- **MFA** via multi-factor authorization (`FactorGrantedAuthority.PASSWORD_AUTHORITY`,
  `FactorGrantedAuthority.OTT_AUTHORITY`)
- **Session management**: Redis-backed sessions, maximum 10 concurrent sessions per user
- **Security headers**: X-XSS-Protection, Content-Security-Policy (`script-src 'self'`)
- **JWT signing**: RSA key pair loaded/generated at `./keys/jwt.key`, `NimbusJwtEncoder`
- **Token customization**: Custom claims for authorities, user, organization, client ID

### 2. AI / LLM Integration (`service/ai/`, `api/controller/ai/`)

**LLM Providers**:

- OpenAI / DeepSeek
- Anthropic (Claude Haiku 4.5)
- Google GenAI (Gemini 2.5 Flash)
- Ollama (local, `deepseek-r1:7b`)
- Typesafe (multi-provider: Azure OpenAI, GitHub Models)

**AI Features**:

- **Redis Chat Memory**: Persistent conversations with TTL (1h), key prefix `spring-ai-chat:`
- **Vector Store (RAG)**: MariaDB vector store, cosine distance, 1536 dimensions
- **MCP Server**: Model Context Protocol server for tool access (sync type)
- **Advisors**: PII Masking Advisor, Secret Leak Advisor
- **Model Router**: Tier-based model selection (`ModelTier` enum)

**AI Controllers**: `AnthropicController`, `DeepSeekController`, `Gemma3ChatController`, `GoogleGenAiController`,
`ModelRouterChatController`, `OllamaChatController`, `OpenAISdkController`, `RedisChatMemoryController`,
`SafeGuardrailChatController`, `VectorStoreController`

### 3. Infrastructure & Data

| Component        | Technology           | Configuration                                               |
|------------------|----------------------|-------------------------------------------------------------|
| Primary Database | MariaDB 12.3.3       | `jdbc:mariadb://localhost:3306/oauth2_authorization_server` |
| Oracle Database  | ojdbc17              | Available via Testcontainers                                |
| Cache / Sessions | Redis 8.10.3         | `localhost:6379`, Jedis client                              |
| Redis Stack      | redis-stack 7.4.0-v8 | `localhost:6380` + RedisInsight on 8001                     |
| Object Storage   | MinIO / AWS S3       | `localhost:9091`, bucket `mb-bucket`                        |
| LDAP             | lldap                | `ldap://localhost:10389`                                    |
| LLM (local)      | Ollama               | `localhost:11434`                                           |
| Tracing          | Zipkin               | `localhost:9411`                                            |
| Observability    | Grafana LGTM         | `localhost:3001` (admin/admin)                              |
| Open WebUI       | Open WebUI           | `localhost:3000`                                            |

### 4. Configuration (`src/main/resources/application.yml`)

- Server port: **9000**
- Datasource: MariaDB with Flyway migrations (baseline version 0, `validate-on-migrate: true`)
- JPA: Hibernate DDL `validate`, `open-in-view: false`
- Encoding: UTF-8 forced for all servlets
- Spring AI: Multiple providers configured with API keys from environment variables
- MCP Server: `webmvc-mcp-server`, version `1.0.0`, sync type
- OpenAPI/Swagger: `/v3/api-docs`, `/swagger-ui/index.html`
- Actuator endpoints: `health`, `info`, `metrics`, `loggers`, `prometheus`
- OpenTelemetry: OTLP metrics, traces, logs export to Grafana LGTM

### 5. Code Quality & Analysis

- **Error Prone + NullAway**: Compile-time null checking with JSpecify mode, annotated package `com.secure`
- **MapStruct**: DTO/entity mapping with Lombok binding
- **Lombok**: Boilerplate reduction (`@Slf4j`, `@RequiredArgsConstructor`, etc.)
- **ArchUnit**: Architecture constraint testing
- **Testcontainers**: Integration testing with MariaDB, Ollama, Oracle, LDAP, MinIO, Redis, PostgreSQL, Grafana
- **Jacoco**: Code coverage reporting
- **AppMap**: Runtime application mapping
- **Jackson**: `jackson-datatype-hibernate7` for Hibernate lazy loading serialization

---

## Docker Infrastructure (`docker-compose.yml`)

### Application Service

- `oauth2-authorization-server` (profile: `start_application`) — port `9000:9000`
- Depends on: mariadb, redis, redis-stack, lldap, ollama

### Infrastructure Services (always started)

| Service       | Image                              | Port         | Purpose                     |
|---------------|------------------------------------|--------------|-----------------------------|
| mariadb       | mariadb:12.3.3                     | 3306         | Primary database            |
| redis         | redis:8.10.3                       | 6379         | Cache & sessions            |
| redis-insight | redis/redisinsight:latest          | 5540         | Redis GUI                   |
| redis-stack   | redis/redis-stack:7.4.0-v8         | 6380, 8001   | Vector store + RedisInsight |
| ollama        | ollama/ollama:0.40.14              | 11434        | Local LLM server            |
| open-webui    | ghcr.io/open-webui/open-webui:main | 3000         | LLM chat UI                 |
| lldap         | lldap/lldap:2025-12-12             | 10389, 17170 | LDAP authentication         |
| minio         | cgr.dev/chainguard/minio:latest    | 9091, 9001   | Object storage              |
| zipkin        | openzipkin/zipkin:3.6.1            | 9411         | Distributed tracing         |
| grafana-lgtm  | grafana/otel-lgtm:0.39.0           | 3001, 4318   | Metrics, traces, logs       |

### Network

- Bridge network: `oauth2-network`

---

## Running the Application

### Prerequisites

- Java 26
- Maven
- Docker

### Option 1: Full Docker Stack

```bash
# Run tests (requires Docker for Testcontainers)
mvn clean verify

# Build and start application + all infrastructure
docker compose --profile start_application up -d --build

# Start only infrastructure services
docker compose up -d
```

### Option 2: Local Development (Docker infra only)

```bash
# Start infrastructure services only
docker compose up -d

# Run application locally
./mvnw spring-boot:run
```

### Option 3: Native Image (GraalVM)

```bash
./mvnw -Pnative native:compile -DskipTests
./target/oauth2-authorization-server
```

### Option 4: AOT + CDC

```bash
./mvnw clean install -Paot
docker build -t oauth2-authorization-server -f DockerfileForAOTCache .
docker run -d --restart=always -p 9000:9000 oauth2-authorization-server:latest
```

---

## Testing & Verification

### Swagger UI

- URL: http://localhost:9000/swagger-ui/index.html
- Client credentials: `client` / `secret`
- Default users (all password `password`): `Developer`, `Admin`, `User`

### Actuator

- URL: http://localhost:9000/actuator/

### Database Credentials

- URL: `jdbc:mariadb://localhost:3306/oauth2_authorization_server`
- Username: `mb_test`
- Password: `test`

### Redis Insight

- URL: http://localhost:5540/
- Connection: `redis://default@host.docker.internal:6379`

### Grafana UI

- URL: http://localhost:3001/
- Username: `admin`, Password: `admin`

### Ollama

- URL: http://localhost:3000/ (Open WebUI)
- Models: `mxbai-embed-large`, `mistral`, `deepseek-r1:7b`

### Postman

Import `docs/postman/OAuth2 Authorization Server.postman_collection.json` and
`docs/postman/OAuth2 Authorization Server.postman_environment.json`

---

## Environment Variables

| Variable                 | Description          | Default                                                     |
|--------------------------|----------------------|-------------------------------------------------------------|
| `SPRING_DATASOURCE_URL`  | JDBC URL             | `jdbc:mariadb://localhost:3306/oauth2_authorization_server` |
| `DB_USERNAME`            | Database username    | `mb_test`                                                   |
| `DB_PASSWORD`            | Database password    | `test`                                                      |
| `SPRING_DATA_REDIS_HOST` | Redis host           | `localhost`                                                 |
| `SPRING_DATA_REDIS_PORT` | Redis port           | `6379`                                                      |
| `SPRING_LDAP_URL`        | LDAP URL             | `ldap://localhost:10389`                                    |
| `MINIO_ENDPOINT`         | MinIO endpoint       | `http://localhost:9091`                                     |
| `OPENAI_API_KEY`         | OpenAI API key       | (required)                                                  |
| `ANTHROPIC_API_KEY`      | Anthropic API key    | (optional)                                                  |
| `GOOGLE_GENAI_API_KEY`   | Google GenAI API key | (optional)                                                  |
| `ZIPKIN_ENDPOINT`        | Zipkin URL           | `http://localhost:9411/api/v2/spans`                        |

---

## Profiles

| Profile   | Purpose                      |
|-----------|------------------------------|
| `aot`     | Spring AOT processing        |
| `localdb` | Local database configuration |

---

## Dependencies Summary

**Spring Boot Starters**: webmvc, validation, webclient, restclient, actuator, oauth2-authorization-server, data-jpa,
data-redis, thymeleaf, flyway, opentelemetry, docker-compose

**Spring AI**: ollama, pdf-document-reader, vector-store-mariadb, advisors-vector-store, mcp-server-webmvc, openai,
typesafe, anthropic, google-genai, chat-memory-redis

**Security**: spring-security-cas, spring-security-ldap, spring-security-test

**Other**: redisson-spring-boot-starter, jedis, jackson-datatype-hibernate7, thymeleaf-extras-springsecurity6,
mariadb-java-client, ojdbc17, mapstruct, crac, micrometer-tracing-bridge-brave, logstash-logback-encoder, webmagic-core,
aws-sdk-s3, minio, okhttp-urlconnection, uap-java, commons-io, commons-compress, fastexcel, fastexcel-reader,
owasp-encoder

**Test**: spring-boot-starter-test, testcontainers (mariadb, ollama, oracle-free, ldap, minio, grafana, redis,
postgresql), instancio-junit, archunit-junit5, taikai

---

## Key Configuration Files

| File                                 | Purpose                               |
|--------------------------------------|---------------------------------------|
| `pom.xml`                            | Maven build configuration             |
| `src/main/resources/application.yml` | Spring Boot application configuration |
| `docker-compose.yml`                 | Docker infrastructure services        |
| `version-rules.xml`                  | Maven Versions Plugin rules           |
| `.dockerignore`                      | Docker ignore patterns                |
| `Dockerfile`                         | Application container image           |
| `DockerfileForAOTCache`              | AOT cache container image             |
| `DockerfileForProjectCRaC`           | CRaC container image                  |

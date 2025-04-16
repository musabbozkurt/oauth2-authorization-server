## OAuth2 Authorization Server with Spring Boot 3 and Java 24

#### Prerequisites

- Java 24 should be installed --> `export JAVA_HOME=$(/usr/libexec/java_home -v 24)`
- Maven should be installed
- Docker should be installed
- Postman can be installed

#### How to Run and Test

- Run `export DEEPSEEK_API_KEY=your_api_key_here` command to set DeepSeek API key
- Run `docker-compose up -d` command to run necessary services
- Run `mvn test` or `mvn clean install` or `mvn clean package` or `./mvnw clean install` command to run all the tests
- Run `mvn spring-boot:run` command to run the application
- Import the followings to test in Postman
    - [OAuth2 Authorization Server.postman_collection.json](docs/postman/OAuth2%20Authorization%20Server.postman_collection.json)
    - [OAuth2 Authorization Server.postman_environment.json](docs/postman/OAuth2%20Authorization%20Server.postman_environment.json)
- Swagger: http://localhost:9000/swagger-ui/index.html
    - Click `Authorize` and enter the following credentials
    - `client_id`: `client`
    - `client_secret`: `secret`
    - Use one of the following default values to log in http://localhost:9000/login
        - username: `Developer` password: `password`
        - username: `Admin` password: `password`
        - username: `User` password: `password`
- Actuator: http://localhost:9000/actuator/
- Database credentials
    - `url`: `jdbc:mariadb://localhost:3306/oauth2_authorization_server`
    - `username`: `mb_test`
    - `password`: `test`
- Ollama
    - http://localhost:3000/ sign up for an account for local environment
    - Search for `deepseek-r1:7b` and download it if it does not exist

#### Debugging Spring Boot Tests in IntelliJ IDEA

1. Run one of the below commands in the terminal
    - `mvn test -Dmaven.surefire.debug`
    - If port 5005 is already in use, you can specify a custom port
        - `mvn test -Dmaven.surefire.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000"`
2. Open IntelliJ IDEA
3. Go to `Run > Attach to Process` (or use shortcut)
    - Windows/Linux: `Ctrl + Alt + 5`
    - Mac: `Cmd + Alt + 5`
4. Select the Java process running your tests
5. The test will pause until you connect your debugger. Once connected, you can use breakpoints and step through your
   code.

#### How to Run and Test Native Image with GraalVM

- Java 24 GraalVM edition should be installed
- Run `docker-compose up -d` command to run necessary services
- Run `./mvnw -Pnative native:compile` or `./mvnw -Pnative native:compile -DskipTests` command to build the native image
- Run `./target/oauth2-authorization-server` command to run the native image
- Install [Grype](https://github.com/anchore/grype) (OPTIONAL)
    - Run `native-image-inspect ./target/oauth2-authorization-server-0.0.1 | grype -v` command to scan vulnerabilities
- Run `native-image-inspect ./target/oauth2-authorization-server-0.0.1 >output.json` command and open `output.json` or
  visit http://localhost:9000/actuator/sbom/native-image to inspect all libraries, methods etc. used in the native image
- Run `open ./target/oauth2-authorization-server-build-report.html` to see build report
- Use Swagger UI to test the application

#### Spring Boot with CRaC(Coordinated Restore at Checkpoint) by Creating Ready to Restore Container Image.

- **Warning:** for real projects make sure to not leak sensitive data in CRaC files since they contain a snapshot of the
  memory of the running JVM instance.
- **Checkpoint**
    - Run
      on [demand checkpoint/restore of a running application](https://docs.spring.io/spring-framework/reference/6.1/integration/checkpoint-restore.html#_on_demand_checkpointrestore_of_a_running_application)
      with: `./docs/scripts/checkpoint.sh`
    - Run
      an [automatic checkpoint/restore at startup](https://docs.spring.io/spring-framework/reference/6.1/integration/checkpoint-restore.html#_automatic_checkpointrestore_at_startup)
      with: `./docs/scripts/checkpointOnRefresh.sh`
- **Restore**
    - Restore the application with: `./docs/scripts/restore.sh`
- Use Swagger UI to test the application

#### References

- [Spring Boot 3 Tutorial Security OAuth2 Spring Authorization Server Save login data to a database](https://www.youtube.com/watch?v=rVAqh-VDw2o)
- [BCryptPasswordEncoderTests](https://github.com/spring-projects/spring-security/blob/main/crypto/src/test/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoderTests.java)
- [Ollama Installation with Docker Compose](https://geshan.com.np/blog/2025/02/ollama-docker-compose/)
- [Getting started with Spring Boot AOT + GraalVM Native Images](https://www.youtube.com/watch?v=FjRBHKUP-NA)
    - ![Spring_Boot_AOT_and_GraalVM_Native_Images.png](docs/Spring_Boot_AOT_and_GraalVM_Native_Images.png)
- [Welcome, GraalVM for JDK 24!🚀](https://medium.com/graalvm/welcome-graalvm-for-jdk-24-7c829fe98ea1)
- [A vulnerability scanner for container images and filesystems Grype](https://github.com/anchore/grype)
- [Introduction to Project CRaC: Enhancing Runtime Efficiency in Java & Spring Development](https://www.youtube.com/watch?v=sVXUx_Y4hRU)
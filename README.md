## OAuth2 Authorization Server with Spring Boot 3 and Java 23

#### Prerequisites

- Java 23 should be installed --> `export JAVA_HOME=$(/usr/libexec/java_home -v 23)`
- Maven should be installed
- Docker should be installed
- Postman can be installed

#### How to Run and Test

- Run `mvn test` or `mvn clean install` or `mvn clean package` or `./mvnw clean install` command to run all the tests
- `mvn spring-boot:run`
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
- Database credentials
    - `url`: `jdbc:mariadb://localhost:3306/oauth2_authorization_server`
    - `username`: `mb_test`
    - `password`: `test`

#### Debugging Spring Boot Tests in IntelliJ IDEA

- A quick guide on how to run Spring Boot tests in debug mode using IntelliJ IDEA's terminal.

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

### References

- [Spring Boot 3 Tutorial Security OAuth2 Spring Authorization Server Save login data to a database](https://www.youtube.com/watch?v=rVAqh-VDw2o)
- [BCryptPasswordEncoderTests](https://github.com/spring-projects/spring-security/blob/main/crypto/src/test/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoderTests.java)

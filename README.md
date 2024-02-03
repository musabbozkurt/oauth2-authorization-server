## OAuth2 Authorization Server with Spring Boot 3 and Java 21

#### Prerequisites

- Java 21+ should be installed
- Maven should be installed
- Docker should be installed
- Postman can be installed
- Swagger: http://localhost:9000/swagger-ui/index.html

#### How to Run and Test

- `mvn spring-boot:run`
- Import [OAuth2 Authorization Server.postman_collection.json](OAuth2%20Authorization%20Server.postman_collection.json)
- Use one of the following default values
    - username: `Developer` password: `password`
    - username: `Admin` password: `password`
    - username: `User` password: `password`

### References

- [Spring Boot 3 Tutorial Security OAuth2 Spring Authorization Server Save login data to a database](https://www.youtube.com/watch?v=rVAqh-VDw2o)
- [BCryptPasswordEncoderTests](https://github.com/spring-projects/spring-security/blob/main/crypto/src/test/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoderTests.java)

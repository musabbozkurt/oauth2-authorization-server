package mb.oauth2authorizationserver.api.controller;

import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.api.request.ApiUserRequest;
import mb.oauth2authorizationserver.api.response.ApiUserResponse;
import mb.oauth2authorizationserver.mapper.UserMapper;
import mb.oauth2authorizationserver.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@SecurityRequirement(name = "security_auth")
@Tag(name = "User Management", description = "CRUD operations for user management")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/")
    @Observed(name = "getUsers")
    @Operation(summary = "Get all users", description = "Retrieves a paginated list of all users.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Page.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Parameter(name = "size", description = "Page size", example = "20", in = ParameterIn.QUERY)
    @Parameter(name = "page", description = "Page number (0-based)", example = "0", in = ParameterIn.QUERY)
    @Parameter(name = "sort", description = "Sort criteria (e.g. firstName,asc)", example = "firstName,asc", in = ParameterIn.QUERY)
    public ResponseEntity<Page<ApiUserResponse>> getUsers(@Parameter(hidden = true) Pageable pageable) {
        log.info("Received a request to get all users. getAllUsers.");
        return new ResponseEntity<>(userMapper.map(userService.getAllUsers(pageable)), HttpStatus.OK);
    }

    @PostMapping("/")
    @Observed(name = "createUser")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiUserResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Operation(summary = "Create user", description = "Creates a new user with the provided details.")
    public ResponseEntity<ApiUserResponse> createUser(@Valid @RequestBody ApiUserRequest apiUserRequest) {
        log.info("Received a request to create user. createUser - apiUserRequest: {}", apiUserRequest);
        return new ResponseEntity<>(userMapper.map(userService.createUser(userMapper.map(apiUserRequest))), HttpStatus.CREATED);
    }

    @GetMapping("/{userId}")
    @Observed(name = "getUserById")
    @Operation(summary = "Get user by id", description = "Retrieves a user by their unique identifier.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiUserResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiUserResponse> getUserById(@Parameter(description = "User id", example = "1") @PathVariable Long userId) {
        log.info("Received a request to get user by id. getUserById - userId: {}", userId);
        return new ResponseEntity<>(userMapper.map(userService.getUserById(userId)), HttpStatus.OK);
    }

    @PutMapping("/{userId}")
    @Observed(name = "updateUserById")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiUserResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Operation(summary = "Update user", description = "Updates an existing user by their unique identifier.")
    public ResponseEntity<ApiUserResponse> updateUserById(@Parameter(description = "User id", example = "1") @PathVariable Long userId,
                                                          @RequestBody ApiUserRequest apiUserRequest) {
        log.info("Received a request to update user by id. updateUserById - userId: {}, apiUserRequest: {}", userId, apiUserRequest);
        return new ResponseEntity<>(userMapper.map(userService.updateUserById(userMapper.map(userService.getUserById(userId), userMapper.map(apiUserRequest)))), HttpStatus.OK);
    }

    @DeleteMapping("/{userId}")
    @Observed(name = "deleteUserById")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User deleted successfully",
                    content = @Content(mediaType = "text/plain")
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Operation(summary = "Delete user by id", description = "Deletes a user by their unique identifier.")
    public ResponseEntity<String> deleteUserById(@Parameter(description = "User id", example = "1") @PathVariable Long userId) {
        log.info("Received a request to delete user by id. deleteUserById - userId: {}", userId);
        userService.deleteUserById(userId);
        return new ResponseEntity<>("User deleted successfully.", HttpStatus.OK);
    }
}

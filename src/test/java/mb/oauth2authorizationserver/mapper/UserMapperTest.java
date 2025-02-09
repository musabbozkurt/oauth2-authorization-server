package mb.oauth2authorizationserver.mapper;

import mb.oauth2authorizationserver.api.request.ApiUserRequest;
import mb.oauth2authorizationserver.api.response.ApiUserResponse;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class UserMapperTest {

    @InjectMocks
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void map_ShouldReturnApiUserResponse_WhenGivenSecurityUser() {
        // Arrange
        SecurityUser user = new SecurityUser();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setUsername("john_doe");
        user.setEmail("john.doe@example.com");
        user.setPhoneNumber("1234567890");

        // Act
        ApiUserResponse response = userMapper.map(user);

        // Assertions
        assertNotNull(response);
        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        assertEquals("john_doe", response.getUsername());
        assertEquals("john.doe@example.com", response.getEmail());
        assertEquals("1234567890", response.getPhoneNumber());
    }

    @Test
    void map_ShouldReturnNull_WhenGivenNullSecurityUser() {
        // Arrange
        SecurityUser user = null;

        // Act
        ApiUserResponse response = userMapper.map(user);

        // Assertions
        assertNull(response);
    }

    @Test
    void map_ShouldReturnListOfApiUserResponses_WhenGivenListOfSecurityUsers() {
        // Arrange
        SecurityUser user1 = new SecurityUser();
        user1.setFirstName("John");
        user1.setLastName("Doe");

        SecurityUser user2 = new SecurityUser();
        user2.setFirstName("Jane");
        user2.setLastName("Doe");

        List<SecurityUser> users = Arrays.asList(user1, user2);

        // Act
        List<ApiUserResponse> responses = userMapper.map(users);

        // Assertions
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("John", responses.get(0).getFirstName());
        assertEquals("Jane", responses.get(1).getFirstName());
    }

    @Test
    void map_ShouldReturnEmptyList_WhenGivenNullListOfSecurityUsers() {
        // Arrange
        List<SecurityUser> users = null;

        // Act
        List<ApiUserResponse> responses = userMapper.map(users);

        // Assertions
        assertNull(responses);
    }

    @Test
    void map_ShouldReturnSecurityUser_WhenGivenApiUserRequest() {
        // Arrange
        ApiUserRequest request = new ApiUserRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setUsername("john_doe");
        request.setEmail("john.doe@example.com");
        request.setPhoneNumber("1234567890");

        // Act
        SecurityUser user = userMapper.map(request);

        // Assertions
        assertNotNull(user);
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals("john_doe", user.getUsername());
        assertEquals("john.doe@example.com", user.getEmail());
        assertEquals("1234567890", user.getPhoneNumber());
    }

    @Test
    void map_ShouldReturnNull_WhenGivenNullApiUserRequest() {
        // Arrange
        ApiUserRequest request = null;

        // Act
        SecurityUser user = userMapper.map(request);

        // Assertions
        assertNull(user);
    }

    @Test
    void map_ShouldUpdateOldRecord_WhenGivenOldAndNewSecurityUser() {
        // Arrange
        SecurityUser oldRecord = new SecurityUser();
        oldRecord.setFirstName("OldFirst");
        oldRecord.setLastName("OldLast");
        oldRecord.setUsername("old_username");
        oldRecord.setEmail("old.email@example.com");
        oldRecord.setPhoneNumber("0987654321");

        SecurityUser newRecord = new SecurityUser();
        newRecord.setFirstName("NewFirst");
        newRecord.setLastName(null); // Simulating no change
        newRecord.setUsername("new_username");
        newRecord.setEmail(null); // Simulating no change
        newRecord.setPhoneNumber("1234567890");

        // Act
        SecurityUser updatedUser = userMapper.map(oldRecord, newRecord);

        // Assertions
        assertNotNull(updatedUser);
        assertEquals("NewFirst", updatedUser.getFirstName());
        assertEquals("OldLast", updatedUser.getLastName());
        assertEquals("new_username", updatedUser.getUsername());
        assertEquals("old.email@example.com", updatedUser.getEmail());
        assertEquals("1234567890", updatedUser.getPhoneNumber());
    }

    @Test
    void map_ShouldOnlyUpdateProvidedFields_WhenPartialUpdateIsMade() {
        // Arrange
        SecurityUser oldRecord = new SecurityUser();
        oldRecord.setFirstName("OldFirst");
        oldRecord.setLastName("OldLast");
        oldRecord.setUsername("old_username");
        oldRecord.setEmail("old.email@example.com");
        oldRecord.setPhoneNumber("0987654321");

        SecurityUser newRecord = new SecurityUser();
        newRecord.setFirstName("NewFirst");
        newRecord.setLastName(null); // No change
        newRecord.setUsername(null);  // No change
        newRecord.setEmail("new.email@example.com"); // Updated
        newRecord.setPhoneNumber(null); // No change

        // Act
        SecurityUser updatedUser = userMapper.map(oldRecord, newRecord);

        // Assertions
        assertNotNull(updatedUser);
        assertEquals("NewFirst", updatedUser.getFirstName());
        assertEquals("OldLast", updatedUser.getLastName());
        assertEquals("old_username", updatedUser.getUsername());
        assertEquals("new.email@example.com", updatedUser.getEmail());
        assertEquals("0987654321", updatedUser.getPhoneNumber());
    }

    @Test
    void map_ShouldReturnMappedPageOfApiUserResponses_WhenGivenPageOfSecurityUsers() {
        // Arrange
        SecurityUser user1 = new SecurityUser();
        user1.setFirstName("John");
        user1.setLastName("Doe");

        SecurityUser user2 = new SecurityUser();
        user2.setFirstName("Jane");
        user2.setLastName("Doe");

        List<SecurityUser> users = Arrays.asList(user1, user2);
        Page<SecurityUser> userPage = new PageImpl<>(users, Pageable.ofSize(2), 2);

        // Act
        Page<ApiUserResponse> responsePage = userMapper.map(userPage);

        // Assertions
        assertNotNull(responsePage);
        assertEquals(2, responsePage.getContent().size());
        assertEquals("John", responsePage.getContent().get(0).getFirstName());
        assertEquals("Jane", responsePage.getContent().get(1).getFirstName());
    }

    @Test
    void map_ShouldReturnEmptyPage_WhenGivenEmptyPageOfSecurityUsers() {
        // Arrange
        Page<SecurityUser> userPage = new PageImpl<>(Collections.emptyList(), Pageable.ofSize(1), 0);

        // Act
        Page<ApiUserResponse> responsePage = userMapper.map(userPage);

        // Assertions
        assertNotNull(responsePage);
        assertTrue(responsePage.isEmpty());
    }
}

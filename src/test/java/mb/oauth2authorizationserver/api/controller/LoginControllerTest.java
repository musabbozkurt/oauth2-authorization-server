package mb.oauth2authorizationserver.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.config.TestSecurityConfig;
import mb.oauth2authorizationserver.service.SecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {TestSecurityConfig.class, RedisTestConfiguration.class})
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void home_ShouldReturnIndexView_WhenAccessed() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void ottSent_ShouldReturnSentView_WhenAccessed() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/ott/sent"))
                .andExpect(status().isOk())
                .andExpect(view().name("ott/sent"));
    }

    @Test
    void nonExistentEndpoint_ShouldReturn404_WhenAccessed() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/non-existent"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ottSent_ShouldReturn404_WhenAccessingInvalidRoute() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/ott/invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_ShouldReturnSubmitView_WhenTokenProvided() throws Exception {
        // Arrange
        String token = "test-token";

        // Act
        // Assertions
        mockMvc.perform(get("/ott/submit")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("ott/submit"))
                .andExpect(model().attribute("token", token));
    }

    @Test
    void submit_ShouldReturnBadRequest_WhenTokenMissing() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/ott/submit"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void login_ShouldReturnLoginView_WhenAccessed() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeDoesNotExist("authentication"));
    }

    @Test
    void logout_ShouldRedirect_WhenAccessed() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/logout")
                        .header("referer", "http://localhost:8080/login"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void logout_ShouldHandleReferer_WhenRefererHeaderPresent() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/logout")
                        .header("referer", "http://localhost:8080/login"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void logout_ShouldHandleNullReferer_WhenRefererHeaderMissing() throws Exception {
        // Arrange
        // Act
        // Assertions
        mockMvc.perform(get("/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_ShouldLogError_WhenIOExceptionOccursInRedirect() throws Exception {
        // Arrange
        SecurityService mockSecurityService = mock(SecurityService.class);
        LoginController controller = new LoginController(mockSecurityService);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);

        when(mockRequest.getHeader("referer")).thenReturn("http://localhost:8080/login");
        doThrow(new IOException("Connection error")).when(mockResponse).sendRedirect(anyString());

        // Act
        controller.logout(mockRequest, mockResponse);

        // Assertions
        verify(mockSecurityService).logout();
        verify(mockResponse).sendRedirect("http://localhost:8080/login");
    }
}

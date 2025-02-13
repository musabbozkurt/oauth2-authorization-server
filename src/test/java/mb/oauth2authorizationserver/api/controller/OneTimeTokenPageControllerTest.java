package mb.oauth2authorizationserver.api.controller;

import mb.oauth2authorizationserver.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@Import(TestSecurityConfig.class)
@WebMvcTest(OneTimeTokenPageController.class)
class OneTimeTokenPageControllerTest {

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
                .andExpect(view().name("sent"));
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
}

package mb.oauth2authorizationserver.api.controller;

import mb.oauth2authorizationserver.config.MinioTestConfiguration;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@AutoConfigureMockMvc
@SpringBootTest(classes = {MinioTestConfiguration.class, RedisTestConfiguration.class})
@ContextConfiguration(initializers = MinioTestConfiguration.Initializer.class)
class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private UUID uuid;

    @BeforeEach
    void init() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test_file.txt", "text/plain", "Sample file content".getBytes());

        // Act
        ResultActions resultActions = mockMvc.perform(multipart("/files/upload").file(file))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());

        // Assertions
        String responseContent = resultActions.andReturn().getResponse().getContentAsString();
        assertTrue(responseContent.endsWith("test_file.txt"), "Response should end with 'test_file.txt'");
        uuid = UUID.fromString(responseContent.substring(0, responseContent.indexOf('_')));
        assertDoesNotThrow(() -> uuid, "The first part of the response should be a valid UUID");
    }

    @Test
    void upload_ShouldReturnFilename_WhenFileIsUploaded() {
        // Arrange
        // Act
        // Assertions
        assertNotNull(uuid, "RANDOM_UUID should be set after file upload in initialize");
    }

    @Test
    void download_ShouldReturnFile_WhenFilenameIsValid() throws Exception {
        // Arrange
        assertNotNull(uuid, "RANDOM_UUID should be set from the initialization.");
        String filename = uuid + "_test_file.txt";
        // Act
        // Assertions
        mockMvc.perform(get("/files/download/" + filename))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=" + filename))
                .andDo(MockMvcResultHandlers.print());
    }
}

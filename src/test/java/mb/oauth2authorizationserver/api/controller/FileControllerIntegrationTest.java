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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
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

    @Test
    void uploadStream_ShouldReturnFilename_WhenFileIsUploaded() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test_file.txt", "text/plain", "Sample file content".getBytes());

        // Act
        ResultActions resultActions = mockMvc.perform(multipart("/files/upload/stream").file(file))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());

        // Assertions
        String responseContent = resultActions.andReturn().getResponse().getContentAsString();
        assertTrue(responseContent.endsWith("test_file.txt"), "Response should end with 'test_file.txt'.");
    }

    @Test
    void uploadStream_ShouldReturnCorrectFilename_WhenUploading10MBFile() throws Exception {
        // Arrange
        // Create a 10 MB file (10 * 1024 * 1024 bytes)
        byte[] tenMBContent = new byte[10 * 1024 * 1024]; // 10 MB
        // Optionally, fill the byte array with specific data
        new java.util.Random().nextBytes(tenMBContent); // Fill with random data
        MockMultipartFile file = new MockMultipartFile("file", "test_file.txt", "text/plain", tenMBContent);

        // Act
        ResultActions resultActions = mockMvc.perform(multipart("/files/upload/stream").file(file))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());

        // Assertions
        String responseContent = resultActions.andReturn().getResponse().getContentAsString();
        assertTrue(responseContent.endsWith("test_file.txt"), "Response should end with 'test_file.txt'.");
    }

    @Test
    void uploadStream_ShouldReturnFilename_WhenImageFileIsUploaded() throws Exception {
        // Arrange
        // Create a 10 MB image (for simplicity, let's create a large image)
        BufferedImage image = getBufferedImage();
        // Write the image to a byte array
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", byteArrayOutputStream);
        byteArrayOutputStream.flush();
        // Create a MockMultipartFile with the image byte array
        MockMultipartFile imageFile = new MockMultipartFile("file", "test_image.jpg", "image/jpeg", byteArrayOutputStream.toByteArray());

        // Act
        ResultActions resultActions = mockMvc.perform(multipart("/files/upload/stream").file(imageFile))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());

        // Assertions
        String responseContent = resultActions.andReturn().getResponse().getContentAsString();
        assertTrue(responseContent.endsWith("test_image.jpg"), "Response should end with 'test_image.jpg'.");
    }

    private BufferedImage getBufferedImage() {
        int width = 10000; // example width
        int height = 10000; // example height
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random();
        // Fill the image with random colors
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Set a random color
                int color = random.nextInt() * 0xFFFFFF;
                image.setRGB(x, y, color);
            }
        }
        return image;
    }
}

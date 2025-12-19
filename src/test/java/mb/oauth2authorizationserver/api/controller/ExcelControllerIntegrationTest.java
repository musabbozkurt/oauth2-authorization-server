package mb.oauth2authorizationserver.api.controller;

import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.config.TestSecurityConfig;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {TestSecurityConfig.class, RedisTestConfiguration.class})
class ExcelControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient restTestClient;

    @BeforeEach
    void setup() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void readExcel_ShouldReturnJsonData_WhenValidExcelFileIsUploaded() throws Exception {
        // Arrange
        byte[] excelBytes = createTestExcelFile();
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Act
        ResultActions resultActions = mockMvc.perform(multipart("/excel/read").file(file))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].Column1").value("Value1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].Column2").value("Value2"))
                .andDo(MockMvcResultHandlers.print());

        // Assertions
        String responseContent = resultActions.andReturn().getResponse().getContentAsString();
        assertNotNull(responseContent, "Response content should not be null");
        assertFalse(responseContent.isEmpty(), "Response content should not be empty");
        assertTrue(responseContent.startsWith("["), "Response should be a JSON array");
        assertTrue(responseContent.endsWith("]"), "Response should end with JSON array bracket");
        assertTrue(responseContent.contains("Column1"), "Response should contain Column1 header");
        assertTrue(responseContent.contains("Column2"), "Response should contain Column2 header");
        assertTrue(responseContent.contains("Value1"), "Response should contain Value1 data");
        assertTrue(responseContent.contains("Value2"), "Response should contain Value2 data");
    }

    @Test
    void readExcel_ShouldReturnMultipleRows_WhenExcelHasMultipleDataRows() throws Exception {
        // Arrange
        byte[] excelBytes = createTestExcelFileWithMultipleRows();
        MockMultipartFile file = new MockMultipartFile("file", "multi_row.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        // Act
        ResultActions resultActions = mockMvc.perform(multipart("/excel/read").file(file))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].Name").value("Alice"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].Name").value("Bob"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[2].Name").value("Charlie"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].Age").value("25"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].Age").value("30"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[2].Age").value("35"))
                .andDo(MockMvcResultHandlers.print());

        // Assertions
        String responseContent = resultActions.andReturn().getResponse().getContentAsString();
        assertNotNull(responseContent, "Response content should not be null");
        assertFalse(responseContent.isEmpty(), "Response content should not be empty");

        long commaCount = responseContent.chars().filter(ch -> ch == '{').count();
        assertEquals(3, commaCount, "Response should contain exactly 3 JSON objects");
    }

    @Test
    void writeExcel_ShouldReturnExcelFile_WhenValidDataProvided() throws IOException {
        // Arrange
        List<String> headers = List.of("Name", "Age", "City");
        List<Map<String, Object>> data = List.of(
                Map.of("Name", "John", "Age", "30", "City", "New York"),
                Map.of("Name", "Jane", "Age", "25", "City", "Los Angeles")
        );
        ExcelController.ExcelWriteRequest request = new ExcelController.ExcelWriteRequest(data, headers);

        // Act
        byte[] responseBytes = restTestClient.post()
                .uri("/excel/write")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        // Assertions
        assertNotNull(responseBytes, "Response bytes should not be null");
        assertTrue(responseBytes.length > 0, "Excel file should have content");
        assertTrue(responseBytes.length > 100, "Excel file should have substantial content");

        // Verify Excel file structure by reading it back
        List<List<String>> parsedData = parseExcelFile(responseBytes);
        assertFalse(parsedData.isEmpty(), "Parsed Excel should have data");
        assertEquals(3, parsedData.size(), "Should have header row plus 2 data rows");

        // Verify headers
        List<String> parsedHeaders = parsedData.getFirst();
        assertEquals(3, parsedHeaders.size(), "Should have 3 headers");
        assertTrue(parsedHeaders.contains("Name"), "Headers should contain Name");
        assertTrue(parsedHeaders.contains("Age"), "Headers should contain Age");
        assertTrue(parsedHeaders.contains("City"), "Headers should contain City");

        // Verify data rows
        List<String> firstDataRow = parsedData.get(1);
        assertTrue(firstDataRow.contains("John") || firstDataRow.contains("Jane"), "Data should contain expected values");
    }

    @Test
    void writeExcel_ShouldReturnExcelFile_WhenEmptyDataProvided() throws IOException {
        // Arrange
        List<String> headers = List.of("Column1", "Column2");
        List<Map<String, Object>> data = List.of();
        ExcelController.ExcelWriteRequest request = new ExcelController.ExcelWriteRequest(data, headers);

        // Act
        byte[] responseBytes = restTestClient.post()
                .uri("/excel/write")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        // Assertions
        assertNotNull(responseBytes, "Response bytes should not be null");
        assertTrue(responseBytes.length > 0, "Excel file should be generated even with empty data");

        // Verify Excel contains only headers
        List<List<String>> parsedData = parseExcelFile(responseBytes);
        assertEquals(1, parsedData.size(), "Should have only header row");
        assertEquals(2, parsedData.getFirst().size(), "Header row should have 2 columns");
        assertEquals("Column1", parsedData.getFirst().get(0), "First header should be Column1");
        assertEquals("Column2", parsedData.getFirst().get(1), "Second header should be Column2");
    }

    @Test
    void writeExcel_ShouldReturnCorrectHeaders_WhenFileIsDownloaded() {
        // Arrange
        List<String> headers = List.of("ID", "Value");
        List<Map<String, Object>> data = List.of(Map.of("ID", "1", "Value", "Test"));
        ExcelController.ExcelWriteRequest request = new ExcelController.ExcelWriteRequest(data, headers);

        // Act & Assertions
        byte[] responseBytes = restTestClient.post()
                .uri("/excel/write")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .expectHeader().valueMatches("Content-Disposition", ".*attachment.*export\\.xlsx.*")
                .expectHeader().exists("Content-Disposition")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertNotNull(responseBytes, "Response should contain file data");
        assertTrue(responseBytes.length > 0, "File should not be empty");
    }

    @Test
    void writeExcel_ShouldHandleLargeDataset_WhenManyRowsProvided() throws IOException {
        // Arrange
        int rowCount = 1000;
        List<String> headers = List.of("Index", "Data");
        List<Map<String, Object>> data = java.util.stream.IntStream.range(0, rowCount)
                .mapToObj(i -> Map.<String, Object>of("Index", String.valueOf(i), "Data", "Value-" + i))
                .toList();
        ExcelController.ExcelWriteRequest request = new ExcelController.ExcelWriteRequest(data, headers);

        // Act
        byte[] responseBytes = restTestClient.post()
                .uri("/excel/write")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        // Assertions
        assertNotNull(responseBytes, "Response bytes should not be null");
        assertTrue(responseBytes.length > rowCount, "Large Excel file should have substantial content");

        // Verify row count by parsing
        List<List<String>> parsedData = parseExcelFile(responseBytes);
        assertEquals(rowCount + 1, parsedData.size(), "Should have header plus " + rowCount + " data rows");

        // Verify first and last data rows
        assertEquals("0", parsedData.get(1).get(0), "First data row index should be 0");
        assertEquals("Value-0", parsedData.get(1).get(1), "First data row value should be Value-0");
        assertEquals("999", parsedData.get(rowCount).get(0), "Last data row index should be 999");
        assertEquals("Value-999", parsedData.get(rowCount).get(1), "Last data row value should be Value-999");
    }

    @Test
    void writeExcel_ShouldHandleSpecialCharacters_WhenDataContainsSpecialChars() throws IOException {
        // Arrange
        List<String> headers = List.of("Text", "Symbol");
        List<Map<String, Object>> data = List.of(
                Map.of("Text", "Hello, World!", "Symbol", "@#$%"),
                Map.of("Text", "Line1\nLine2", "Symbol", "<>&\"'")
        );
        ExcelController.ExcelWriteRequest request = new ExcelController.ExcelWriteRequest(data, headers);

        // Act
        byte[] responseBytes = restTestClient.post()
                .uri("/excel/write")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        // Assertions
        assertNotNull(responseBytes, "Response should not be null");
        assertTrue(responseBytes.length > 0, "Excel file should be generated");

        List<List<String>> parsedData = parseExcelFile(responseBytes);
        assertEquals(3, parsedData.size(), "Should have header plus 2 data rows");
        assertTrue(parsedData.get(1).contains("Hello, World!"), "Should preserve commas");
        assertTrue(parsedData.get(2).contains("<>&\"'"), "Should preserve special XML characters");
    }

    private byte[] createTestExcelFile() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(os, "Test", "1.0");
        Worksheet worksheet = workbook.newWorksheet("Sheet 1");

        worksheet.value(0, 0, "Column1");
        worksheet.value(0, 1, "Column2");
        worksheet.value(1, 0, "Value1");
        worksheet.value(1, 1, "Value2");

        workbook.finish();
        return os.toByteArray();
    }

    private byte[] createTestExcelFileWithMultipleRows() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(os, "Test", "1.0");
        Worksheet worksheet = workbook.newWorksheet("Sheet 1");

        worksheet.value(0, 0, "Name");
        worksheet.value(0, 1, "Age");
        worksheet.value(1, 0, "Alice");
        worksheet.value(1, 1, "25");
        worksheet.value(2, 0, "Bob");
        worksheet.value(2, 1, "30");
        worksheet.value(3, 0, "Charlie");
        worksheet.value(3, 1, "35");

        workbook.finish();
        return os.toByteArray();
    }

    private List<List<String>> parseExcelFile(byte[] excelBytes) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (ByteArrayInputStream is = new ByteArrayInputStream(excelBytes);
             ReadableWorkbook workbook = new ReadableWorkbook(is)) {
            Sheet sheet = workbook.getFirstSheet();
            try (Stream<Row> rows = sheet.openStream()) {
                rows.forEach(row -> {
                    List<String> rowData = new ArrayList<>();
                    row.forEach(cell -> rowData.add(cell.getRawValue()));
                    result.add(rowData);
                });
            }
        }
        return result;
    }
}

package mb.oauth2authorizationserver.service.impl;

import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import mb.oauth2authorizationserver.service.ExcelService;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = RedisTestConfiguration.class)
class ExcelServiceImplIntegrationTest {

    @Autowired
    private ExcelService excelService;

    @Test
    void readExcel_ShouldReturnData_WhenValidExcelProvided() throws IOException {
        // Arrange
        InputStream inputStream = createTestExcelInputStream();

        // Act
        List<Map<String, Object>> result = excelService.readExcel(inputStream);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should have 1 data row");
        assertEquals("Value1", result.getFirst().get("Column1"), "First column value should match");
        assertEquals("Value2", result.getFirst().get("Column2"), "Second column value should match");
    }

    @Test
    void readExcel_ShouldReturnMultipleRows_WhenExcelHasMultipleDataRows() throws IOException {
        // Arrange
        InputStream inputStream = createMultiRowExcelInputStream();

        // Act
        List<Map<String, Object>> result = excelService.readExcel(inputStream);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertEquals(3, result.size(), "Should have 3 data rows");
        assertEquals("Alice", result.get(0).get("Name"), "First row name should be Alice");
        assertEquals("Bob", result.get(1).get("Name"), "Second row name should be Bob");
        assertEquals("Charlie", result.get(2).get("Name"), "Third row name should be Charlie");
    }

    @Test
    void readExcel_ShouldReturnEmptyList_WhenExcelHasOnlyHeaders() throws IOException {
        // Arrange
        InputStream inputStream = createHeaderOnlyExcelInputStream();

        // Act
        List<Map<String, Object>> result = excelService.readExcel(inputStream);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty when only headers exist");
    }

    @Test
    void readExcel_ShouldThrowBaseException_WhenIOExceptionOccurs() {
        // Arrange
        try (InputStream brokenInputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated IO error");
            }
        }) {
            // Act
            // Assertions
            BaseException exception = assertThrows(BaseException.class, () -> excelService.readExcel(brokenInputStream));
            assertEquals(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR, exception.getErrorCode(), "Should throw BaseException with UNEXPECTED_ERROR code");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void readExcel_ShouldPreserveColumnOrder_WhenReadingData() throws IOException {
        // Arrange
        InputStream inputStream = createMultiRowExcelInputStream();

        // Act
        List<Map<String, Object>> result = excelService.readExcel(inputStream);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertFalse(result.isEmpty(), "Result should not be empty");
        assertTrue(result.getFirst().containsKey("Name"), "Should contain Name column");
        assertTrue(result.getFirst().containsKey("Age"), "Should contain Age column");
    }

    @Test
    void readExcel_ShouldHandleVeryLargeDataset_WhenMaxRowsProvided() throws IOException {
        // Arrange - Excel max rows is 1,048,576 (use 1 million for safety)
        int rowCount = 1_000_000;
        byte[] excelBytes = createLargeExcelBytes(rowCount);

        // Act
        List<Map<String, Object>> result = excelService.readExcel(new ByteArrayInputStream(excelBytes));

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertEquals(rowCount, result.size(), "Should have " + rowCount + " data rows");
        assertEquals("0", result.getFirst().get("Index"), "First row index should be 0");
        assertEquals("Value-" + (rowCount - 1), result.getLast().get("Data"), "Last row data should match");
    }

    @Test
    void readExcelResource_ShouldReturnData_WhenValidResourceProvided() throws IOException {
        // Arrange
        byte[] excelBytes = createTestExcelBytes();
        Resource resource = new ByteArrayResource(excelBytes);

        // Act
        List<Map<String, Object>> result = excelService.readExcel(resource);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should have 1 data row");
        assertEquals("Value1", result.getFirst().get("Column1"), "First column value should match");
        assertEquals("Value2", result.getFirst().get("Column2"), "Second column value should match");
    }

    @Test
    void readExcelResource_ShouldThrowBaseException_WhenResourceThrowsIOException() {
        // Arrange
        Resource brokenResource = new Resource() {
            @Override
            public @NonNull InputStream getInputStream() throws IOException {
                throw new IOException("Simulated IO error");
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public @NonNull URL getURL() throws IOException {
                throw new IOException("Not supported");
            }

            @Override
            public @NonNull URI getURI() throws IOException {
                throw new IOException("Not supported");
            }

            @Override
            public @NonNull File getFile() throws IOException {
                throw new IOException("Not supported");
            }

            @Override
            public long contentLength() {
                return 0;
            }

            @Override
            public long lastModified() {
                return 0;
            }

            @Override
            public @NonNull Resource createRelative(@NonNull String relativePath) throws IOException {
                throw new IOException("Not supported");
            }

            @Override
            public @NonNull String getFilename() {
                return "broken.xlsx";
            }

            @Override
            public @NonNull String getDescription() {
                return "Broken resource for testing";
            }
        };

        // Act
        // Assertions
        BaseException exception = assertThrows(BaseException.class, () -> excelService.readExcel(brokenResource));
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR, exception.getErrorCode(), "Should throw BaseException with UNEXPECTED_ERROR code");
    }

    @Test
    void readExcelResource_ShouldReturnMultipleRows_WhenResourceHasMultipleDataRows() throws IOException {
        // Arrange
        byte[] excelBytes = createMultiRowExcelBytes();
        Resource resource = new ByteArrayResource(excelBytes);

        // Act
        List<Map<String, Object>> result = excelService.readExcel(resource);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertEquals(3, result.size(), "Should have 3 data rows");
        assertEquals("Alice", result.get(0).get("Name"), "First row name should be Alice");
        assertEquals("Bob", result.get(1).get("Name"), "Second row name should be Bob");
    }

    @Test
    void writeExcel_ShouldReturnValidExcelBytes_WhenDataProvided() {
        // Arrange
        List<String> headers = List.of("Name", "Age", "City");
        List<Map<String, Object>> data = List.of(
                Map.of("Name", "John", "Age", "30", "City", "New York"),
                Map.of("Name", "Jane", "Age", "25", "City", "Boston")
        );

        // Act
        byte[] result = excelService.writeExcel(data, headers);

        // Assertions
        assertNotNull(result, "Result should not be null");
        assertTrue(result.length > 0, "Excel bytes should not be empty");
    }

    @Test
    void writeExcel_ShouldCreateReadableExcel_WhenDataProvided() {
        // Arrange
        List<String> headers = List.of("ID", "Value");
        List<Map<String, Object>> data = List.of(
                Map.of("ID", "1", "Value", "Test1"),
                Map.of("ID", "2", "Value", "Test2")
        );

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);
        List<Map<String, Object>> readBack = excelService.readExcel(new ByteArrayInputStream(excelBytes));

        // Assertions
        assertNotNull(readBack, "Read back data should not be null");
        assertEquals(2, readBack.size(), "Should have 2 data rows");
        assertEquals("1", readBack.get(0).get("ID"), "First row ID should match");
        assertEquals("Test2", readBack.get(1).get("Value"), "Second row value should match");
    }

    @Test
    void writeExcel_ShouldHandleEmptyData_WhenNoRowsProvided() {
        // Arrange
        List<String> headers = List.of("Column1", "Column2");
        List<Map<String, Object>> data = List.of();

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);
        List<Map<String, Object>> readBack = excelService.readExcel(new ByteArrayInputStream(excelBytes));

        // Assertions
        assertNotNull(excelBytes, "Excel bytes should not be null");
        assertTrue(excelBytes.length > 0, "Excel file should be generated");
        assertTrue(readBack.isEmpty(), "Read back should have no data rows");
    }

    @Test
    void writeExcel_ShouldHandleLargeDataset_WhenManyRowsProvided() {
        // Arrange
        int rowCount = 1000;
        List<String> headers = List.of("Index", "Data");
        List<Map<String, Object>> data = java.util.stream.IntStream.range(0, rowCount)
                .mapToObj(i -> Map.<String, Object>of("Index", String.valueOf(i), "Data", "Value-" + i))
                .toList();

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);

        // Assertions
        assertNotNull(excelBytes, "Excel bytes should not be null");
        assertTrue(excelBytes.length > rowCount, "File size should scale with data");
    }

    @Test
    void writeExcel_ShouldPreserveSpecialCharacters_WhenDataContainsSpecialChars() {
        // Arrange
        List<String> headers = List.of("Text", "Symbol");
        List<Map<String, Object>> data = List.of(
                Map.of("Text", "Hello, World!", "Symbol", "@#$%"),
                Map.of("Text", "<>&\"'", "Symbol", "Special")
        );

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);
        List<Map<String, Object>> readBack = excelService.readExcel(new ByteArrayInputStream(excelBytes));

        // Assertions
        assertNotNull(readBack, "Read back should not be null");
        assertEquals(2, readBack.size(), "Should have 2 rows");
        assertEquals("Hello, World!", readBack.get(0).get("Text"), "Should preserve commas");
        assertEquals("<>&\"'", readBack.get(1).get("Text"), "Should preserve special characters");
    }

    @Test
    void writeExcel_ShouldHandleNullValues_WhenDataContainsNullFields() {
        // Arrange
        List<String> headers = List.of("Name", "Age", "City");
        List<Map<String, Object>> data = List.of(
                Map.of("Name", "John", "Age", "30"), // City is missing (will be null)
                new java.util.LinkedHashMap<>() {{
                    put("Name", "Jane");
                    put("Age", null); // Explicitly null
                    put("City", "Boston");
                }}
        );

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);
        List<Map<String, Object>> readBack = excelService.readExcel(new ByteArrayInputStream(excelBytes));

        // Assertions
        assertNotNull(excelBytes, "Excel bytes should not be null");
        assertTrue(excelBytes.length > 0, "Excel file should be generated");
        assertEquals(2, readBack.size(), "Should have 2 data rows");
        assertEquals("John", readBack.getFirst().get("Name"), "First row name should match");
        assertEquals("30", readBack.get(0).get("Age"), "First row age should match");
        assertNull(readBack.get(0).get("City"), "First row city should be null");
        assertEquals("Jane", readBack.get(1).get("Name"), "Second row name should match");
        assertNull(readBack.get(1).get("Age"), "Second row age should be null");
        assertEquals("Boston", readBack.get(1).get("City"), "Second row city should match");
    }

    @Test
    void writeExcel_ShouldThrowBaseException_WhenIOExceptionOccurs() {
        // Arrange
        ExcelServiceImpl excelServiceWithMock = new ExcelServiceImpl();
        List<String> headers = List.of("Name", "Age");
        List<Map<String, Object>> data = List.of(Map.of("Name", "John", "Age", "30"));

        try (var _ = Mockito.mockConstruction(
                Workbook.class,
                (mock, _) -> {
                    Worksheet worksheetMock = Mockito.mock(Worksheet.class);
                    Mockito.when(mock.newWorksheet(Mockito.anyString())).thenReturn(worksheetMock);
                    Mockito.doThrow(new IOException("Simulated IO error")).when(mock).finish();
                })) {
            // Act
            // Assertions
            BaseException exception = assertThrows(BaseException.class, () -> excelServiceWithMock.writeExcel(data, headers));

            assertEquals(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR, exception.getErrorCode(), "Should throw BaseException with UNEXPECTED_ERROR code");
        }
    }

    @Test
    void writeExcel_ShouldHandleVeryLargeDataset_WhenMaxRowsProvided() {
        // Arrange - Excel max rows is 1,048,576 (use 1 million for safety)
        int rowCount = 1_000_000;
        List<String> headers = List.of("Index", "Data");
        List<Map<String, Object>> data = java.util.stream.IntStream.range(0, rowCount)
                .parallel()
                .mapToObj(i -> Map.<String, Object>of("Index", String.valueOf(i), "Data", "Value-" + i))
                .toList();

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);

        // Assertions
        assertNotNull(excelBytes, "Excel bytes should not be null");
        assertTrue(excelBytes.length > 0, "Excel file should be generated");
    }

    @Test
    void writeAndReadExcel_ShouldPreserveData_WhenMaxRowsRoundTrip() {
        // Arrange - Excel max rows is 1,048,576 (use 1 million for safety)
        int rowCount = 1_000_000;
        List<String> headers = List.of("Index", "Data");
        List<Map<String, Object>> data = java.util.stream.IntStream.range(0, rowCount)
                .parallel()
                .mapToObj(i -> Map.<String, Object>of("Index", String.valueOf(i), "Data", "Value-" + i))
                .toList();

        // Act
        byte[] excelBytes = excelService.writeExcel(data, headers);
        List<Map<String, Object>> readBack = excelService.readExcel(new ByteArrayInputStream(excelBytes));

        // Assertions
        assertEquals(rowCount, readBack.size(), "Should have " + rowCount + " rows after round trip");
        assertEquals("0", readBack.getFirst().get("Index"), "First index should match");
        assertEquals("Value-999999", readBack.get(999999).get("Data"), "Sample row should match");
    }

    private byte[] createLargeExcelBytes(int rowCount) throws IOException {
        int maxRowsPerSheet = 1_048_575;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(os, "Test", "1.0");

        int sheetCount = (int) Math.ceil((double) rowCount / maxRowsPerSheet);

        for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
            Worksheet worksheet = workbook.newWorksheet("Sheet " + (sheetIndex + 1));

            worksheet.value(0, 0, "Index");
            worksheet.value(0, 1, "Data");

            int startRow = sheetIndex * maxRowsPerSheet;
            int endRow = Math.min(startRow + maxRowsPerSheet, rowCount);

            for (int i = startRow; i < endRow; i++) {
                int excelRow = (i - startRow) + 1;
                worksheet.value(excelRow, 0, String.valueOf(i));
                worksheet.value(excelRow, 1, "Value-" + i);
            }
        }

        workbook.finish();
        return os.toByteArray();
    }

    private byte[] createTestExcelBytes() throws IOException {
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

    private InputStream createMultiRowExcelInputStream() throws IOException {
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
        return new ByteArrayInputStream(os.toByteArray());
    }

    private InputStream createHeaderOnlyExcelInputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(os, "Test", "1.0");
        Worksheet worksheet = workbook.newWorksheet("Sheet 1");

        worksheet.value(0, 0, "Header1");
        worksheet.value(0, 1, "Header2");

        workbook.finish();
        return new ByteArrayInputStream(os.toByteArray());
    }

    private byte[] createMultiRowExcelBytes() throws IOException {
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

    private InputStream createTestExcelInputStream() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(os, "Test", "1.0");
        Worksheet worksheet = workbook.newWorksheet("Sheet 1");

        worksheet.value(0, 0, "Column1");
        worksheet.value(0, 1, "Column2");
        worksheet.value(1, 0, "Value1");
        worksheet.value(1, 1, "Value2");

        workbook.finish();
        return new ByteArrayInputStream(os.toByteArray());
    }
}

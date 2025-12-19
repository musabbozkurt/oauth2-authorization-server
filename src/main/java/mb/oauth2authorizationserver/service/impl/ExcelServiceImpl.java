package mb.oauth2authorizationserver.service.impl;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import mb.oauth2authorizationserver.service.ExcelService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class ExcelServiceImpl implements ExcelService {

    private static final int MAX_ROWS_PER_SHEET = 1_048_575; // Excel max rows minus header row

    @Override
    public List<Map<String, Object>> readExcel(InputStream inputStream) {
        List<Map<String, Object>> result = new ArrayList<>();

        try (ReadableWorkbook workbook = new ReadableWorkbook(inputStream)) {
            // Read from all sheets to support multi-sheet workbooks
            for (Sheet sheet : workbook.getSheets().toList()) {
                try (Stream<Row> rows = sheet.openStream()) {
                    List<String> headers = new ArrayList<>();

                    rows.forEach(row -> {
                        if (row.getRowNum() == 1) {
                            // First row as headers
                            row.forEach(cell -> headers.add(cell.getRawValue()));
                        } else {
                            Map<String, Object> rowData = new LinkedHashMap<>();
                            for (int i = 0; i < headers.size(); i++) {
                                String value = row.getCellAsString(i).orElse(null);
                                rowData.put(headers.get(i), value);
                            }
                            result.add(rowData);
                        }
                    });
                }
            }
        } catch (IOException e) {
            log.error("Error occurred while reading Excel file. Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> readExcel(Resource resource) {
        try {
            return readExcel(resource.getInputStream());
        } catch (IOException e) {
            log.error("Error occurred while reading Excel resource. Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }
    }

    @Override
    public byte[] writeExcel(List<Map<String, Object>> data, List<String> headers) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Workbook workbook = new Workbook(os, "Export", "1.0");

            int totalRows = data.size();
            int sheetCount = (int) Math.ceil((double) totalRows / MAX_ROWS_PER_SHEET);
            if (sheetCount == 0) sheetCount = 1; // At least one sheet for headers

            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                Worksheet worksheet = workbook.newWorksheet("Sheet " + (sheetIndex + 1));

                // Write headers
                for (int col = 0; col < headers.size(); col++) {
                    worksheet.value(0, col, headers.get(col));
                }

                // Calculate data range for this sheet
                int startRow = sheetIndex * MAX_ROWS_PER_SHEET;
                int endRow = Math.min(startRow + MAX_ROWS_PER_SHEET, totalRows);

                // Write data rows
                for (int i = startRow; i < endRow; i++) {
                    Map<String, Object> rowData = data.get(i);
                    int excelRow = (i - startRow) + 1; // +1 for header row

                    for (int col = 0; col < headers.size(); col++) {
                        Object value = rowData.get(headers.get(col));
                        worksheet.value(excelRow, col, value != null ? value.toString() : null);
                    }
                }
            }

            workbook.finish();
            return os.toByteArray();
        } catch (IOException e) {
            log.error("Error occurred while writing Excel file. Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }
    }
}

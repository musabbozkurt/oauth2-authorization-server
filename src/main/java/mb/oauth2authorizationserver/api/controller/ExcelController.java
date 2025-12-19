package mb.oauth2authorizationserver.api.controller;

import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.service.ExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/excel")
@SecurityRequirement(name = "security_auth")
public class ExcelController {

    private final ExcelService excelService;

    @PostMapping("/read")
    @Observed(name = "readExcel")
    @Operation(description = "Read Excel file and return data as JSON.")
    public ResponseEntity<List<Map<String, Object>>> readExcel(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Received a request to read Excel file. readExcel - fileName: {}", file.getOriginalFilename());
        return new ResponseEntity<>(excelService.readExcel(file.getInputStream()), HttpStatus.OK);
    }

    @PostMapping("/write")
    @Observed(name = "writeExcel")
    @Operation(description = "Write data to Excel file and return as download.")
    public ResponseEntity<byte[]> writeExcel(@RequestBody ExcelWriteRequest request) {
        log.info("Received a request to write Excel file. writeExcel - headers: {}", request.headers());
        byte[] excelBytes = excelService.writeExcel(request.data(), request.headers());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "export.xlsx");

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    public record ExcelWriteRequest(List<Map<String, Object>> data, List<String> headers) {
    }
}

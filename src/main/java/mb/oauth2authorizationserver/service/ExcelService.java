package mb.oauth2authorizationserver.service;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface ExcelService {

    List<Map<String, Object>> readExcel(InputStream inputStream);

    List<Map<String, Object>> readExcel(Resource resource);

    byte[] writeExcel(List<Map<String, Object>> data, List<String> headers);
}

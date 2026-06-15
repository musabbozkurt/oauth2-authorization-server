package mb.oauth2authorizationserver.config.ai;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

public class McpClientSse {

    static void main() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:9000").build();
        new CustomMcpClient(transport).run();
    }
}

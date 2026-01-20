package mb.oauth2authorizationserver.config.ai;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;

public class McpClientSse {

    static void main() {
        var transport = HttpClientSseClientTransport.builder("http://localhost:9000").build();
        new CustomMcpClient(transport).run();
    }
}

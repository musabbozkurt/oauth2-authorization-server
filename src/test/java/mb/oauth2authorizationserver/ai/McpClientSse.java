package mb.oauth2authorizationserver.ai;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;

public class McpClientSse {

    public static void main(String[] args) {
        var transport = HttpClientSseClientTransport.builder("http://localhost:9000").build();
        new CustomMcpClient(transport).run();
    }
}

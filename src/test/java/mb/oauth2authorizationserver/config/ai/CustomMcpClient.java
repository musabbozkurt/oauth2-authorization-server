package mb.oauth2authorizationserver.config.ai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class CustomMcpClient {

    private final McpClientTransport transport;

    public void run() {
        var client = McpClient.sync(transport).build();

        client.initialize();
        client.ping();
        client.listTools()
                .tools()
                .forEach(tool -> log.info("Tool: {}, description: {}, schema: {}", tool.name(), tool.description(), tool.inputSchema()));

        CallToolResult weatherForecastResult = client.callTool(CallToolRequest.builder("getWeatherForecastByLocation").arguments(Map.of("latitude", "47.6062", "longitude", "-122.3321")).build());
        log.info("Weather Forecast: {}", weatherForecastResult);

        CallToolResult alertResult = client.callTool(CallToolRequest.builder("getAlerts").arguments(Map.of("state", "NY")).build());
        log.info("Alert Response = {}", alertResult);

        client.closeGracefully();
    }
}

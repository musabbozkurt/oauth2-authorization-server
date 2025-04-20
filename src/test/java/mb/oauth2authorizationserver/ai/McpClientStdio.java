package mb.oauth2authorizationserver.ai;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import mb.oauth2authorizationserver.utils.FileUtils;

/**
 * With stdio transport, the MCP server is automatically started by the client. Server jar should be built first.
 *
 * <pre>
 * ./mvnw clean install -DskipTests
 * </pre>
 */
public class McpClientStdio {

    public static void main(String[] args) {
        var stdioParams = ServerParameters.builder("java")
                .args("-Dspring.ai.mcp.server.stdio=true", "-Dspring.main.web-application-type=none", "-Dlogging.pattern.console=", "-jar", FileUtils.findFileInPathByPattern(FileUtils.OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR).orElseThrow().toString())
                .build();

        var transport = new StdioClientTransport(stdioParams);
        new CustomMcpClient(transport).run();
    }
}

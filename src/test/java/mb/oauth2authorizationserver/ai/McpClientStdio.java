package mb.oauth2authorizationserver.ai;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import mb.oauth2authorizationserver.utils.FileUtils;

/**
 * With stdio transport, the MCP server is automatically started by the client. Server jar should be built first.
 *
 * <pre>
 * ./mvnw clean install -DskipTests
 * </pre>
 */
public class McpClientStdio {

    static void main() {
        var stdioParams = ServerParameters.builder("java")
                .args("-Dspring.ai.mcp.server.stdio=true", "-Dspring.main.web-application-type=none", "-Dlogging.pattern.console=", "-jar", FileUtils.findFileInPathByPattern(FileUtils.OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR).orElseThrow().toString())
                .build();

        var transport = new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new JsonMapper()));
        new CustomMcpClient(transport).run();
    }
}

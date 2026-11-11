package com.ruoyi.system.ai.tool;

import com.sun.net.httpserver.HttpServer;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.security.SecurityProfile;
import net.sourceforge.plantuml.security.SecurityUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MindmapToolSecurityTest
{
    @Test
    void usesSandboxAndDoesNotFetchRemoteIncludes() throws Exception
    {
        Class.forName(MindmapTool.class.getName());
        assertEquals("SANDBOX", System.getProperty("PLANTUML_SECURITY_PROFILE"));
        assertEquals(SecurityProfile.SANDBOX, SecurityUtils.getSecurityProfile());

        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/probe", exchange -> {
            requests.incrementAndGet();
            byte[] response = "remote node".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody())
            {
                output.write(response);
            }
        });
        server.start();
        try
        {
            String source = "@startmindmap\n* root\n!includeurl http://127.0.0.1:"
                    + server.getAddress().getPort() + "/probe\n@endmindmap";
            new SourceStringReader(source).outputImage(new ByteArrayOutputStream());
            assertEquals(0, requests.get());
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void acceptsBoundedMindmapNodesAndRejectsPlantUmlDirectives()
    {
        String valid = "@startmindmap\n* Topic\n** Concept\n*** Detail\n@endmindmap";
        assertEquals(valid, MindmapTool.validatePlantUml(valid));

        String withRemoteInclude = "@startmindmap\n* Topic\n!includeurl https://example.invalid/a\n@endmindmap";
        assertThrows(IllegalArgumentException.class,
                () -> MindmapTool.validatePlantUml(withRemoteInclude));

        String skippedLevel = "@startmindmap\n* Topic\n*** Detail\n@endmindmap";
        assertThrows(IllegalArgumentException.class,
                () -> MindmapTool.validatePlantUml(skippedLevel));

        String oversizedNode = "@startmindmap\n* " + "x".repeat(161) + "\n@endmindmap";
        assertThrows(IllegalArgumentException.class,
                () -> MindmapTool.validatePlantUml(oversizedNode));
        assertTrue(valid.length() < 12000);
    }
}

package com.aquarius;

import com.aquarius.feature.queue.mcping.MCPing;
import com.aquarius.util.Wait;
import com.aquarius.util.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestLogCaptureJunitExtension.class)
public class StartupTest {
    @Test
    public void launchAquariusServer() throws Exception {
        var config = new Config();
        config.interactiveTerminal.enable = false;
        config.server.bind.port = 0;
        TestUtils.setConfigFile(config);

        var launchThread = Thread.ofPlatform().start(Proxy::main);

        assertTrue(Wait.waitUntil(() ->
            !launchThread.isAlive()
            || (Proxy.getInstance().getServer() != null && Proxy.getInstance().getServer().isListening()),
            10),
            "Failed to start Aquarius server"
        );

        try {
            var response = MCPing.INSTANCE.ping("localhost", Proxy.getInstance().getServer().getPort(), 5000, false);
            assertEquals("AquariusProxy", response.version().name());
        } catch (Exception e) {
            fail("Failed to ping local Aquarius mc server", e);
        }
        Proxy.getInstance().stop();
        launchThread.join(5000);
    }
}

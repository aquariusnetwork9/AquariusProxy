package com.aquarius.feature.api.ntfy;

import com.aquarius.feature.api.Api;
import com.aquarius.util.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DEFAULT_LOG;
import static com.aquarius.Globals.OBJECT_MAPPER;
import static com.aquarius.Globals.VERSION;

/**
 * Publishes to a <a href="https://ntfy.sh/docs/">ntfy</a> topic. No account/token required -
 * anyone who knows the topic name can subscribe, so the topic itself is the only "secret".
 * Server is configurable ({@link Config.Notifications.Ntfy#server}) for self-hosted instances.
 */
public class NtfyApi extends Api {
    public static final NtfyApi INSTANCE = new NtfyApi();

    public NtfyApi() {
        super("");
    }

    public void notify(Config.Notifications.Ntfy.NtfyEvent eventCfg, String title, String message) {
        var ntfy = CONFIG.notifications.ntfy;
        if (!ntfy.enabled || !eventCfg.enabled) return;
        var server = ntfy.server.trim();
        var topic = ntfy.topic.trim();
        if (server.isEmpty() || topic.isEmpty()) return;
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("topic", topic);
            body.put("title", title);
            body.put("message", message);
            body.put("priority", eventCfg.priority);
            if (eventCfg.tags != null && !eventCfg.tags.isBlank()) {
                body.put("tags", eventCfg.tags.split(","));
            }
            var json = OBJECT_MAPPER.writeValueAsString(body);
            var request = HttpRequest.newBuilder()
                .uri(URI.create(server.endsWith("/") ? server : server + "/"))
                .headers("User-Agent", "AquariusProxy/" + VERSION, "Content-Type", "application/json")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            try (HttpClient client = buildHttpClient()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    DEFAULT_LOG.warn("ntfy publish failed: {} - {}", response.statusCode(), response.body());
                }
            }
        } catch (Exception e) {
            DEFAULT_LOG.warn("ntfy publish failed", e);
        }
    }
}

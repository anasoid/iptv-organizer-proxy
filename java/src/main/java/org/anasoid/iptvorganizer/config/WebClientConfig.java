package org.anasoid.iptvorganizer.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WebClientConfig {

    @Inject
    Vertx vertx;

    @Inject
    @ConfigProperty(name = "http.client.max-pool-size", defaultValue = "5")
    int maxPoolSize;

    @Inject
    @ConfigProperty(name = "http.client.connect-timeout", defaultValue = "10000")
    int connectTimeout;

    @Inject
    @ConfigProperty(name = "http.client.idle-timeout", defaultValue = "60")
    int idleTimeout;

    @Inject
    @ConfigProperty(name = "http.client.max-redirects", defaultValue = "5")
    int maxRedirects;

    @Produces
    @ApplicationScoped
    public WebClient webClient() {
        WebClientOptions options = new WebClientOptions()
            .setMaxPoolSize(maxPoolSize)
            .setConnectTimeout(connectTimeout)
            .setIdleTimeout(idleTimeout)
            .setMaxRedirects(maxRedirects)
            .setTryUseCompression(true);

        return WebClient.create(vertx, options);
    }
}

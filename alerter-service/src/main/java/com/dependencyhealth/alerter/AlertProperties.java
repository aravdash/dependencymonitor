package com.dependencyhealth.alerter;

import com.dependencyhealth.contract.Severity;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "alert")
public record AlertProperties(@DefaultValue("critical") Severity threshold,
                              @DefaultValue("") String webhookUrl,
                              @DefaultValue("5s") Duration connectTimeout,
                              @DefaultValue("10s") Duration readTimeout) {
    public AlertProperties {
        if (threshold == null || connectTimeout == null || readTimeout == null
                || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Alert threshold and positive HTTP timeouts are required");
        }
        webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        if (!webhookUrl.isBlank()) {
            try {
                URI uri = URI.create(webhookUrl);
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                        || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                // Never print the webhook URL: Slack URLs contain a credential.
                throw new IllegalArgumentException("ALERT_WEBHOOK_URL must be an absolute HTTP(S) URL without user info or a fragment");
            }
        }
    }
}

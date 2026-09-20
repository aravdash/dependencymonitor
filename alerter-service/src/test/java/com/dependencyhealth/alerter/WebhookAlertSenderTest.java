package com.dependencyhealth.alerter;

import com.dependencyhealth.contract.Severity;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WebhookAlertSenderTest {
    private static final String WEBHOOK = "https://hooks.slack.test/services/secret-token";
    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @Test
    void sendsSlackJsonWithEventIdAndDisablesMarkup() {
        server.expect(requestTo(WEBHOOK)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(jsonPath("$.mrkdwn").value(false))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        sender(WEBHOOK).send(AlertListenerTest.event(Severity.CRITICAL));
        server.verify();
    }

    @Test
    void absentWebhookLogsWithoutHttpTraffic() {
        sender("").send(AlertListenerTest.event(Severity.CRITICAL));
        server.verify();
    }

    @Test
    void httpFailureIsRetryableAndDoesNotExposeWebhookCredentials() {
        server.expect(requestTo(WEBHOOK)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> sender(WEBHOOK).send(AlertListenerTest.event(Severity.CRITICAL)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret-token").hasNoCause();
        server.verify();
    }

    @Test
    void redirectsAreNotAcceptedAsDeliverySuccess() {
        server.expect(requestTo(WEBHOOK)).andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT));
        assertThatThrownBy(() -> sender(WEBHOOK).send(AlertListenerTest.event(Severity.CRITICAL)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("307");
        server.verify();
    }

    @Test
    void invalidWebhookAndInfiniteTimeoutFailConfiguration() {
        assertThatThrownBy(() -> sender("file:///secret-token")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-token");
        assertThatThrownBy(() -> new AlertProperties(Severity.CRITICAL, "", Duration.ZERO, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private WebhookAlertSender sender(String url) {
        return new WebhookAlertSender(client, new AlertProperties(Severity.CRITICAL, url,
                Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }
}

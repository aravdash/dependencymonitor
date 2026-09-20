package com.dependencyhealth.alerter;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.DependencyEventCodec;
import com.dependencyhealth.contract.InvalidEventException;
import com.dependencyhealth.contract.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AlertListenerTest {
    private final DependencyEventCodec codec = new DependencyEventCodec();
    private final WebhookAlertSender sender = mock(WebhookAlertSender.class);

    @ParameterizedTest
    @EnumSource(Severity.class)
    void criticalThresholdOnlySendsCritical(Severity severity) {
        DependencyEvent event = event(severity);
        listener(Severity.CRITICAL).onEvent(codec.write(event));
        if (severity == Severity.CRITICAL) verify(sender).send(event);
        else verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @EnumSource(Severity.class)
    void warningThresholdIncludesWarningAndCritical(Severity severity) {
        DependencyEvent event = event(severity);
        listener(Severity.WARNING).onEvent(codec.write(event));
        if (severity == Severity.INFO) verifyNoInteractions(sender);
        else verify(sender).send(event);
    }

    @Test
    void failedDeliveryPropagatesForKafkaRetry() {
        DependencyEvent event = event(Severity.CRITICAL);
        IllegalStateException failure = new IllegalStateException("Webhook unavailable");
        doThrow(failure).when(sender).send(event);
        assertThatThrownBy(() -> listener(Severity.CRITICAL).onEvent(codec.write(event))).isSameAs(failure);
    }

    @Test
    void malformedEventDoesNotSendNotification() {
        assertThatThrownBy(() -> listener(Severity.INFO).onEvent("not-json")).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(sender);
    }

    private AlertListener listener(Severity threshold) {
        return new AlertListener(codec, new AlertProperties(threshold, "", Duration.ofSeconds(5), Duration.ofSeconds(10)), sender);
    }

    static DependencyEvent event(Severity severity) {
        return new DependencyEvent("123e4567-e89b-12d3-a456-426614174000", "osv-cve", "lodash", "npm", severity,
                "Known vulnerability", Map.of("vulnerabilityId", "GHSA-example"), Instant.parse("2025-01-01T00:00:00Z"));
    }
}

package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTest {
    private final EventRepository repository = mock(EventRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DashboardController(repository)).build();
    }

    @Test
    void recentEventsHaveBoundedDefaultsAndWireSeverity() throws Exception {
        DependencyEvent event = new DependencyEvent("123e4567-e89b-12d3-a456-426614174000", "osv-cve",
                "lodash", "npm", Severity.CRITICAL, "Known vulnerability", Map.of(), Instant.parse("2025-01-01T00:00:00Z"));
        when(repository.recentEvents(null, 50, 0)).thenReturn(new PageResponse<>(List.of(event), 50, 0, 1));
        mvc.perform(get("/events/recent")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].severity").value("critical"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.limit").value(50));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 201, Integer.MAX_VALUE})
    void rejectsUnboundedPageSizes(int limit) throws Exception {
        mvc.perform(get("/packages").param("limit", String.valueOf(limit))).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNegativeOffsets() throws Exception {
        mvc.perform(get("/events/recent").param("offset", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void scopedPackageQueryKeepsSlashAndNormalizesEcosystem() throws Exception {
        when(repository.packageEvents("@example/core", "npm", 20, 40))
                .thenReturn(new PageResponse<>(List.of(), 20, 40, 0));
        mvc.perform(get("/packages/events").param("name", "@example/core").param("ecosystem", "NPM")
                .param("limit", "20").param("offset", "40")).andExpect(status().isOk());
        verify(repository).packageEvents("@example/core", "npm", 20, 40);
    }

    @Test
    void mavenCoordinatesWorkInRequiredPathEndpoint() throws Exception {
        String name = "com.fasterxml.jackson.core:jackson-databind";
        when(repository.packageEvents(name, "maven", 50, 0))
                .thenReturn(new PageResponse<>(List.of(), 50, 0, 0));
        mvc.perform(get("/packages/{name}/events", name).param("ecosystem", "maven"))
                .andExpect(status().isOk());
        verify(repository).packageEvents(name, "maven", 50, 0);
    }
}

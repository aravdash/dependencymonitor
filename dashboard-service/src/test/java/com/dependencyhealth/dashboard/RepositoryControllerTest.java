package com.dependencyhealth.dashboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RepositoryControllerTest {
    private static final String REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";
    private final RepositoryScanService service = mock(RepositoryScanService.class);
    private final RepositoryScanRepository repository = mock(RepositoryScanRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RepositoryController(service, repository)).build();
    }

    @Test
    void acceptsARepositoryAndReturnsItsQueuedScan() throws Exception {
        when(service.submit("https://github.com/Google/guava")).thenReturn(scan("queued"));
        mvc.perform(post("/repositories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"https://github.com/Google/guava\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.status").value("queued"));
    }

    @Test
    void rejectsInvalidInputAsABadRequest() throws Exception {
        when(service.submit("https://example.com/acme/app"))
                .thenThrow(new IllegalArgumentException("Only HTTPS github.com repository URLs are supported"));
        mvc.perform(post("/repositories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"https://example.com/acme/app\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void returnsDependencyPagesAndNormalizesTheEcosystem() throws Exception {
        when(repository.find(REQUEST_ID)).thenReturn(Optional.of(scan("complete")));
        var dependency = new RepositoryDependencyView("lodash", "npm", "4.17.21",
                "pkg:npm/lodash@4.17.21", true, "MIT");
        when(repository.dependencies(REQUEST_ID, "npm", 100, 0))
                .thenReturn(new PageResponse<>(List.of(dependency), 100, 0, 1));
        mvc.perform(get("/repositories/{id}/dependencies", REQUEST_ID).param("ecosystem", "NPM"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].packageName").value("lodash"));
        verify(repository).dependencies(REQUEST_ID, "npm", 100, 0);
    }

    @Test
    void missingScanReturnsNotFoundBeforeDependencyLookup() throws Exception {
        when(repository.find(REQUEST_ID)).thenReturn(Optional.empty());
        mvc.perform(get("/repositories/{id}/dependencies", REQUEST_ID)).andExpect(status().isNotFound());
    }

    private RepositoryScanView scan(String status) {
        Instant requested = Instant.parse("2026-09-20T12:00:00Z");
        return new RepositoryScanView(REQUEST_ID, "github:google/guava", "https://github.com/Google/guava",
                status, status.equals("complete") ? 1 : 0, 0, "Ready", requested,
                status.equals("queued") ? null : requested.plusSeconds(5));
    }
}

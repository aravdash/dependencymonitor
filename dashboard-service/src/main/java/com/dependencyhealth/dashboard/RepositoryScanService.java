package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.kafka.RepositoryMessagePublisher;
import com.dependencyhealth.contract.repository.GithubRepositoryReference;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RepositoryScanService {
    private final RepositoryScanRepository repository;
    private final RepositoryMessagePublisher publisher;
    private final Clock clock;
    public RepositoryScanService(RepositoryScanRepository repository, RepositoryMessagePublisher publisher) {
        this(repository, publisher, Clock.systemUTC());
    }
    RepositoryScanService(RepositoryScanRepository repository, RepositoryMessagePublisher publisher, Clock clock) {
        this.repository = repository; this.publisher = publisher; this.clock = clock;
    }
    public RepositoryScanView submit(String url) {
        GithubRepositoryReference reference = GithubRepositoryReference.parse(url);
        var request = new RepositoryScanRequest(UUID.randomUUID().toString(), "github", reference.owner(),
                reference.repository(), reference.canonicalUrl(), Instant.now(clock));
        RepositoryScanView view = repository.create(request);
        try { publisher.publishRequest(request); }
        catch (RuntimeException ex) { repository.markPublicationFailed(request.requestId()); throw ex; }
        return view;
    }
}

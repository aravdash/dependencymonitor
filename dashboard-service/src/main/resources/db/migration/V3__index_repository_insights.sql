CREATE INDEX idx_dependency_event_repository_request
    ON dependency_event ((detail->>'repositoryRequestId'), event_timestamp DESC)
    WHERE detail ? 'repositoryRequestId';

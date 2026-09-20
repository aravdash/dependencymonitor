CREATE TABLE dependency_event (
    event_id UUID PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    package_name TEXT NOT NULL,
    ecosystem VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('info', 'warning', 'critical')),
    summary TEXT NOT NULL,
    detail JSONB NOT NULL CHECK (jsonb_typeof(detail) = 'object'),
    event_timestamp TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dependency_event_recent ON dependency_event (event_timestamp DESC, event_id DESC);
CREATE INDEX idx_dependency_event_package ON dependency_event
    (package_name, ecosystem, source, event_timestamp DESC, event_id DESC);
CREATE INDEX idx_dependency_event_ecosystem_recent ON dependency_event
    (ecosystem, event_timestamp DESC, event_id DESC);

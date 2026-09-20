CREATE TABLE repository_scan (
    request_id UUID PRIMARY KEY,
    repository_id TEXT NOT NULL,
    repository_url TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('queued', 'complete', 'partial', 'failed')),
    dependency_count INTEGER NOT NULL DEFAULT 0 CHECK (dependency_count >= 0),
    unsupported_count INTEGER NOT NULL DEFAULT 0 CHECK (unsupported_count >= 0),
    message TEXT NOT NULL DEFAULT 'Waiting for repository discovery',
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_repository_scan_recent ON repository_scan (requested_at DESC, request_id DESC);
CREATE INDEX idx_repository_scan_repository ON repository_scan (repository_id, requested_at DESC);

CREATE TABLE repository_dependency (
    request_id UUID NOT NULL REFERENCES repository_scan(request_id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    ecosystem VARCHAR(32) NOT NULL CHECK (ecosystem IN ('npm', 'pypi', 'maven')),
    version TEXT NOT NULL,
    package_url TEXT NOT NULL,
    direct BOOLEAN NOT NULL,
    declared_license TEXT NOT NULL,
    PRIMARY KEY (request_id, ecosystem, package_name, version)
);

CREATE INDEX idx_repository_dependency_scan ON repository_dependency
    (request_id, direct DESC, ecosystem, package_name, version);

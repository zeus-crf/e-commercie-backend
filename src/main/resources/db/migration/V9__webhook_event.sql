CREATE TABLE webhook_event (
id VARCHAR(36) NOT NULL PRIMARY KEY,
provider VARCHAR(50) NOT NULL,
external_id    VARCHAR(100) NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
created_at TIMESTAMP NOT NULL,
updated_at TIMESTAMP,
CONSTRAINT uq_webhook_provider_external UNIQUE (provider, external_id)
)
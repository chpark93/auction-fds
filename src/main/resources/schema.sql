-- FDS 이벤트 저장소 스키마
-- Postgres 전용

-- domain_events
CREATE TABLE IF NOT EXISTS domain_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_aggregate_version UNIQUE (aggregate_id, version)
);

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate_id ON domain_events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_domain_events_event_type ON domain_events(event_type);
CREATE INDEX IF NOT EXISTS idx_domain_events_occurred_at ON domain_events(occurred_at DESC);

-- JSONB 컬럼에 대한 GIN 인덱스
CREATE INDEX IF NOT EXISTS idx_domain_events_payload ON domain_events USING gin(payload);

-- 코멘트 추가
COMMENT ON TABLE domain_events IS 'Event Sourcing을 위한 도메인 이벤트 저장소';
COMMENT ON COLUMN domain_events.id IS '이벤트 고유 식별자';
COMMENT ON COLUMN domain_events.aggregate_id IS 'Aggregate Root 식별자 (예: userId)';
COMMENT ON COLUMN domain_events.aggregate_type IS 'Aggregate 타입 (예: UserRiskProfile)';
COMMENT ON COLUMN domain_events.version IS 'Aggregate 내 이벤트 버전 (순서 보장)';
COMMENT ON COLUMN domain_events.event_type IS '이벤트 타입 (클래스명)';
COMMENT ON COLUMN domain_events.payload IS '이벤트 페이로드 (JSONB)';
COMMENT ON COLUMN domain_events.occurred_at IS '이벤트 발생 시각';
COMMENT ON COLUMN domain_events.created_at IS '이벤트 저장 시각';


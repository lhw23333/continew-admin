-- liquibase formatted sql

-- changeset continew:merchant-phase1-review-record-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_review_record_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'biz_review_record is append-only';
END;
$$ LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-review-record-immutable-trigger-postgresql
CREATE TRIGGER trg_review_record_immutable
    BEFORE UPDATE OR DELETE ON "biz_review_record"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_review_record_mutation();

-- liquibase formatted sql

-- changeset continew:merchant-phase1-review-record-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_review_record_no_update`
    BEFORE UPDATE ON `biz_review_record`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_review_record is append-only';

-- changeset continew:merchant-phase1-review-record-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_review_record_no_delete`
    BEFORE DELETE ON `biz_review_record`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_review_record is append-only';

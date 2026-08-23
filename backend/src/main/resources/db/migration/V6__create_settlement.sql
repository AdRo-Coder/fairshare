Drop table if exists settlement;

CREATE TABLE settlement
(
    id              BIGINT AUTO_INCREMENT NOT NULL,
    group_id        BIGINT                NOT NULL,
    from_user       BIGINT                NOT NULL,
    to_user         BIGINT                NOT NULL,
    amount          DECIMAL(10,2)               NOT NULL,
    settlement_date date                  NULL,
    CONSTRAINT pk_settlement PRIMARY KEY (id)
);

ALTER TABLE settlement
    ADD CONSTRAINT FK_SETTLEMENT_ON_GROUP FOREIGN KEY (group_id) REFERENCES expense_group (group_id);

ALTER TABLE settlement
    ADD CONSTRAINT FK_SETTLEMENT_ON_FROM_USER FOREIGN KEY (from_user) REFERENCES users (id);

Alter TABLE  settlement
    ADD CONSTRAINT FK_SETTLEMENT_ON_TO_USER FOREIGN KEY (to_user) REFERENCES users (id);

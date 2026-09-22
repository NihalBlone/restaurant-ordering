ALTER TABLE restaurants ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE restaurants ADD COLUMN plan_code VARCHAR(30) NOT NULL DEFAULT 'STARTER';
ALTER TABLE restaurants ADD COLUMN table_limit INTEGER NOT NULL DEFAULT 20;
ALTER TABLE restaurants ADD COLUMN staff_limit INTEGER NOT NULL DEFAULT 10;
ALTER TABLE restaurants ADD COLUMN trial_ends_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE restaurant_admins ALTER COLUMN restaurant_id DROP NOT NULL;
ALTER TABLE restaurant_admins ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'RESTAURANT_ADMIN';
ALTER TABLE restaurant_admins ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE restaurant_admins ADD COLUMN last_totp_step BIGINT NOT NULL DEFAULT -1;
ALTER TABLE restaurant_admins ADD CONSTRAINT ck_admin_scope CHECK (
 (role = 'PLATFORM_ADMIN' AND restaurant_id IS NULL) OR
 (role IN ('RESTAURANT_ADMIN','MANAGER','KITCHEN','WAITER') AND restaurant_id IS NOT NULL)
);
CREATE INDEX idx_restaurants_status ON restaurants(status);
CREATE TABLE audit_entries (
 id UUID PRIMARY KEY, actor_id UUID, actor_name VARCHAR(80) NOT NULL, restaurant_id UUID,
 action VARCHAR(80) NOT NULL, subject_id UUID, details VARCHAR(2000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_restaurant_time ON audit_entries(restaurant_id, created_at);
CREATE INDEX idx_audit_time ON audit_entries(created_at);
CREATE TABLE platform_settings (
 id INTEGER PRIMARY KEY, product_name VARCHAR(120) NOT NULL, support_email VARCHAR(254) NOT NULL,
 announcement VARCHAR(1000) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT
);
INSERT INTO platform_settings(id, product_name, support_email, announcement, updated_at, version)
 VALUES(1, 'Tableside', '', '', CURRENT_TIMESTAMP, 0);

CREATE TABLE restaurants (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, location VARCHAR(255) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE restaurant_tables (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), table_number VARCHAR(30) NOT NULL,
 qr_code_url VARCHAR(500) NOT NULL, is_active BOOLEAN NOT NULL, current_session_id UUID,
 last_session_closed_at TIMESTAMP WITH TIME ZONE, version BIGINT,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uk_restaurant_table_number UNIQUE(restaurant_id, table_number)
);
CREATE INDEX idx_restaurant_tables_restaurant_id ON restaurant_tables(restaurant_id);
CREATE INDEX idx_restaurant_tables_current_session_id ON restaurant_tables(current_session_id);
CREATE TABLE menu_categories (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), name VARCHAR(100) NOT NULL,
 display_order INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uk_menu_category_name UNIQUE(restaurant_id, name)
);
CREATE INDEX idx_menu_categories_restaurant_id ON menu_categories(restaurant_id);
CREATE TABLE menu_items (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), category_id UUID NOT NULL REFERENCES menu_categories(id),
 name VARCHAR(120) NOT NULL, description VARCHAR(500) NOT NULL, price NUMERIC(10,2) NOT NULL,
 image_url VARCHAR(1000), is_vegetarian BOOLEAN NOT NULL, is_available BOOLEAN NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_menu_items_restaurant_id ON menu_items(restaurant_id);
CREATE INDEX idx_menu_items_category_id ON menu_items(category_id);
CREATE TABLE dining_sessions (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), table_id UUID NOT NULL REFERENCES restaurant_tables(id),
 status VARCHAR(20) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE NOT NULL, closed_at TIMESTAMP WITH TIME ZONE,
 total_orders INTEGER NOT NULL, total_items INTEGER NOT NULL, total_amount NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_dining_sessions_restaurant_closed ON dining_sessions(restaurant_id, closed_at);
CREATE INDEX idx_dining_sessions_table_closed ON dining_sessions(table_id, closed_at);
CREATE INDEX idx_dining_sessions_status ON dining_sessions(status);
CREATE TABLE orders (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), table_id UUID NOT NULL REFERENCES restaurant_tables(id),
 session_id UUID NOT NULL, customer_name VARCHAR(80) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_orders_restaurant_id ON orders(restaurant_id);
CREATE INDEX idx_orders_table_id ON orders(table_id);
CREATE INDEX idx_orders_session_id ON orders(session_id);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_status ON orders(status);
CREATE TABLE order_items (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), order_id UUID NOT NULL REFERENCES orders(id),
 menu_item_id UUID NOT NULL REFERENCES menu_items(id), menu_item_name_at_order_time VARCHAR(120) NOT NULL,
 quantity INTEGER NOT NULL, price_at_order_time NUMERIC(10,2) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_restaurant_id ON order_items(restaurant_id);
CREATE INDEX idx_order_items_restaurant_menu_item ON order_items(restaurant_id, menu_item_id);
CREATE TABLE idempotency_keys (
 id UUID PRIMARY KEY, idempotency_key VARCHAR(120) NOT NULL, order_id UUID NOT NULL REFERENCES orders(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, CONSTRAINT uk_idempotency_keys_key UNIQUE(idempotency_key)
);
CREATE TABLE restaurant_admins (
 id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), username VARCHAR(80) NOT NULL,
 username_normalized VARCHAR(80) NOT NULL, email VARCHAR(254) NOT NULL, email_normalized VARCHAR(254) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, is_active BOOLEAN NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uk_restaurant_admin_username UNIQUE(username_normalized), CONSTRAINT uk_restaurant_admin_email UNIQUE(email_normalized)
);
CREATE INDEX idx_restaurant_admins_restaurant_id ON restaurant_admins(restaurant_id);
CREATE TABLE password_reset_tokens (
 id UUID PRIMARY KEY, admin_id UUID NOT NULL REFERENCES restaurant_admins(id), token_hash VARCHAR(64) NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, used_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uk_password_reset_token_hash UNIQUE(token_hash)
);
CREATE INDEX idx_password_reset_admin_id ON password_reset_tokens(admin_id);
CREATE INDEX idx_password_reset_expires_at ON password_reset_tokens(expires_at);

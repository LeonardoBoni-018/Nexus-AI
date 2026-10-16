-- Nexus AI - esquema inicial. Compativel com PostgreSQL (producao) e H2 em modo PostgreSQL (local/testes).

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    demo BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(200) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER', 'ANALYST', 'MANAGER', 'ADMIN')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_membership UNIQUE (user_id, organization_id)
);
CREATE INDEX idx_memberships_org ON memberships (organization_id);

CREATE TABLE stores (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(160) NOT NULL,
    city VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_store_name UNIQUE (organization_id, name)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    sku VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    cost NUMERIC(14, 2) NOT NULL CHECK (cost >= 0),
    price NUMERIC(14, 2) NOT NULL CHECK (price >= 0),
    min_stock INTEGER NOT NULL CHECK (min_stock >= 0),
    target_coverage_days INTEGER NOT NULL DEFAULT 21 CHECK (target_coverage_days > 0),
    lead_time_days INTEGER NOT NULL DEFAULT 5 CHECK (lead_time_days >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_product_sku UNIQUE (organization_id, sku)
);
CREATE INDEX idx_products_org_category ON products (organization_id, category);

CREATE TABLE inventory_balances (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    store_id UUID NOT NULL REFERENCES stores (id),
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_balance UNIQUE (store_id, product_id)
);
CREATE INDEX idx_balances_org ON inventory_balances (organization_id, store_id);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    store_id UUID NOT NULL REFERENCES stores (id),
    product_id UUID NOT NULL REFERENCES products (id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('OPENING', 'SALE', 'REPLENISHMENT', 'ADJUSTMENT')),
    quantity_delta INTEGER NOT NULL,
    balance_after INTEGER NOT NULL CHECK (balance_after >= 0),
    reference_type VARCHAR(40),
    reference_id UUID,
    idempotency_key VARCHAR(160) UNIQUE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_movements_org_time ON inventory_movements (organization_id, occurred_at);
CREATE INDEX idx_movements_product ON inventory_movements (product_id, occurred_at);

CREATE TABLE sales (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    store_id UUID NOT NULL REFERENCES stores (id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sale_date DATE NOT NULL,
    total NUMERIC(14, 2) NOT NULL CHECK (total >= 0)
);
CREATE INDEX idx_sales_org_date ON sales (organization_id, sale_date);
CREATE INDEX idx_sales_store_date ON sales (store_id, sale_date);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL REFERENCES sales (id),
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    unit_cost NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0)
);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);
CREATE INDEX idx_sale_items_product ON sale_items (product_id);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    type VARCHAR(30) NOT NULL,
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    store_id UUID REFERENCES stores (id),
    product_id UUID REFERENCES products (id),
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    dedupe_key VARCHAR(200) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_alert_dedupe UNIQUE (organization_id, dedupe_key)
);
CREATE INDEX idx_alerts_org ON alerts (organization_id, resolved, created_at);

CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    user_id UUID NOT NULL REFERENCES users (id),
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_conversations_user ON conversations (organization_id, user_id, created_at);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations (id),
    role VARCHAR(12) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content VARCHAR(20000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_messages_conv ON chat_messages (conversation_id, created_at);

CREATE TABLE ai_tool_executions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    user_id UUID NOT NULL REFERENCES users (id),
    conversation_id UUID NOT NULL REFERENCES conversations (id),
    message_id UUID REFERENCES chat_messages (id),
    tool_name VARCHAR(80) NOT NULL,
    arguments VARCHAR(4000),
    result_summary VARCHAR(4000),
    status VARCHAR(12) NOT NULL CHECK (status IN ('SUCCESS', 'ERROR', 'DENIED')),
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_tool_exec_conv ON ai_tool_executions (conversation_id, created_at);

CREATE TABLE proposed_actions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    type VARCHAR(30) NOT NULL,
    store_id UUID NOT NULL REFERENCES stores (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'EXECUTED', 'REJECTED', 'CANCELLED', 'FAILED')),
    justification VARCHAR(2000),
    created_by UUID NOT NULL REFERENCES users (id),
    created_via_ai BOOLEAN NOT NULL DEFAULT FALSE,
    conversation_id UUID REFERENCES conversations (id),
    executed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_actions_org ON proposed_actions (organization_id, status, created_at);

CREATE TABLE action_items (
    id UUID PRIMARY KEY,
    action_id UUID NOT NULL REFERENCES proposed_actions (id),
    product_id UUID NOT NULL REFERENCES products (id),
    current_stock INTEGER NOT NULL,
    avg_daily_sales NUMERIC(12, 3) NOT NULL,
    coverage_days NUMERIC(10, 1),
    suggested_quantity INTEGER NOT NULL CHECK (suggested_quantity > 0),
    approved_quantity INTEGER NOT NULL CHECK (approved_quantity > 0),
    CONSTRAINT uq_action_item UNIQUE (action_id, product_id)
);

CREATE TABLE action_approvals (
    id UUID PRIMARY KEY,
    action_id UUID NOT NULL REFERENCES proposed_actions (id),
    user_id UUID NOT NULL REFERENCES users (id),
    decision VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations (id),
    user_id UUID REFERENCES users (id),
    event_type VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60),
    entity_id VARCHAR(80),
    details VARCHAR(2000),
    request_id VARCHAR(60),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_org_time ON audit_events (organization_id, created_at);

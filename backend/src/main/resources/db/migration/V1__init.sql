CREATE TABLE member (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE seller (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  BIGINT       NOT NULL UNIQUE REFERENCES member (id),
    store_name VARCHAR(100) NOT NULL,
    fee_rate   NUMERIC(5, 4) NOT NULL DEFAULT 0.1000 CHECK (fee_rate >= 0 AND fee_rate < 1),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE product (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id  BIGINT       NOT NULL REFERENCES seller (id),
    name       VARCHAR(200) NOT NULL,
    price      BIGINT       NOT NULL CHECK (price >= 0),
    status     VARCHAR(10)  NOT NULL DEFAULT 'ON_SALE' CHECK (status IN ('ON_SALE', 'SOLD_OUT', 'HIDDEN')),
    flash_sale BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY REFERENCES product (id),
    stock      INT         NOT NULL CHECK (stock >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    buyer_id     BIGINT      NOT NULL REFERENCES member (id),
    status       VARCHAR(20) NOT NULL,
    total_amount BIGINT      NOT NULL CHECK (total_amount >= 0),
    ordered_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_buyer ON orders (buyer_id, ordered_at);

CREATE TABLE order_item (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders (id),
    product_id BIGINT NOT NULL REFERENCES product (id),
    seller_id  BIGINT NOT NULL REFERENCES seller (id),
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    quantity   INT    NOT NULL CHECK (quantity > 0),
    amount     BIGINT NOT NULL CHECK (amount >= 0)
);
CREATE INDEX idx_order_item_seller ON order_item (seller_id, order_id);

CREATE TABLE payment (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           BIGINT       NOT NULL REFERENCES orders (id),
    portone_payment_id VARCHAR(100) UNIQUE,
    idempotency_key    VARCHAR(100) NOT NULL UNIQUE,
    amount             BIGINT       NOT NULL CHECK (amount >= 0),
    status             VARCHAR(20)  NOT NULL,
    paid_at            TIMESTAMPTZ
);
CREATE INDEX idx_payment_order ON payment (order_id);
CREATE INDEX idx_payment_paid_at ON payment (paid_at);

CREATE TABLE refund (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id    BIGINT       NOT NULL REFERENCES payment (id),
    order_item_id BIGINT REFERENCES order_item (id),
    amount        BIGINT       NOT NULL CHECK (amount > 0),
    reason        VARCHAR(200),
    status        VARCHAR(20)  NOT NULL,
    refunded_at   TIMESTAMPTZ
);
CREATE INDEX idx_refund_payment ON refund (payment_id);
CREATE INDEX idx_refund_refunded_at ON refund (refunded_at);

CREATE TABLE settlement (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id     BIGINT        NOT NULL REFERENCES seller (id),
    settle_date   DATE          NOT NULL,
    gross_sales   BIGINT        NOT NULL,
    refund_amount BIGINT        NOT NULL,
    fee_rate      NUMERIC(5, 4) NOT NULL,
    fee_amount    BIGINT        NOT NULL,
    net_amount    BIGINT        NOT NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'PAID')),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (seller_id, settle_date)
);

CREATE TABLE settlement_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    settlement_id BIGINT      NOT NULL REFERENCES settlement (id),
    source_type   VARCHAR(10) NOT NULL CHECK (source_type IN ('SALE', 'REFUND')),
    source_id     BIGINT      NOT NULL,
    amount        BIGINT      NOT NULL,
    UNIQUE (source_type, source_id)
);

-- ============================================================
-- sample schema: orders and products tables
-- ============================================================

CREATE TABLE IF NOT EXISTS product (
   id          BIGINT       NOT NULL PRIMARY KEY,
   code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
      id          BIGINT       NOT NULL PRIMARY KEY,
      product_id  BIGINT       NOT NULL,
      quantity    INT          NOT NULL,
      status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ordered_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_product FOREIGN KEY (product_id) REFERENCES product(id)
);

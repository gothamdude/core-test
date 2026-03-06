-- ============================================================
-- sample fixture data: products and orders
-- ============================================================

INSERT INTO product (id, code, name, price) VALUES
                                                (1, 'WIDGET-A', 'Blue Widget',  9.99),
                                                (2, 'WIDGET-B', 'Red Widget',  14.99),
                                                (3, 'GADGET-X', 'Super Gadget', 49.99);

INSERT INTO orders (id, product_id, quantity, status) VALUES (101, 1, 5, 'PENDING'),
                                                          (102, 2, 2, 'SHIPPED');
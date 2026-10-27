ALTER TABLE product_category
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN display_order INTEGER NOT NULL DEFAULT 1000;

INSERT INTO product_category (code, name, is_active, display_order)
VALUES
    ('ELECTRONICS', '전자기기', TRUE, 1),
    ('HOBBY', '취미·수집', TRUE, 2),
    ('FASHION', '패션·잡화', TRUE, 3),
    ('LIVING', '생활용품', TRUE, 4),
    ('MEDIA', '도서·음반', TRUE, 5),
    ('SPORTS', '스포츠·레저', TRUE, 6)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order;

ALTER TABLE product_category
    ADD CONSTRAINT ck_product_category_display_order
        CHECK (display_order > 0);

CREATE INDEX idx_product_category_active_display_order
    ON product_category (is_active, display_order, category_id);

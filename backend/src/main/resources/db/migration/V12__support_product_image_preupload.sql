ALTER TABLE product_image
    ALTER COLUMN product_id DROP NOT NULL,
    ADD COLUMN image_id UUID,
    ADD COLUMN owner_member_id BIGINT,
    ADD COLUMN storage_key VARCHAR(500),
    ADD COLUMN content_type VARCHAR(100),
    ADD COLUMN size_bytes BIGINT;

UPDATE product_image image
SET image_id = gen_random_uuid(),
    owner_member_id = product.seller_member_id,
    storage_key = 'legacy/product-images/' || image.product_image_id,
    content_type = 'application/octet-stream',
    size_bytes = 0
FROM product
WHERE product.product_id = image.product_id;

ALTER TABLE product_image
    ALTER COLUMN image_id SET NOT NULL,
    ALTER COLUMN owner_member_id SET NOT NULL,
    ALTER COLUMN storage_key SET NOT NULL,
    ALTER COLUMN content_type SET NOT NULL,
    ALTER COLUMN size_bytes SET NOT NULL,
    ADD CONSTRAINT uq_product_image_image_id UNIQUE (image_id),
    ADD CONSTRAINT uq_product_image_storage_key UNIQUE (storage_key),
    ADD CONSTRAINT fk_product_image_owner
        FOREIGN KEY (owner_member_id)
            REFERENCES member (member_id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT ck_product_image_size_bytes
        CHECK (size_bytes >= 0);

CREATE INDEX idx_product_image_owner_created
    ON product_image (owner_member_id, created_at DESC);

CREATE INDEX idx_product_image_unused_created
    ON product_image (created_at)
    WHERE product_id IS NULL;

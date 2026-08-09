CREATE TABLE demo_product (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(12, 2) NOT NULL
);

CREATE TABLE parallel_case_record (
    id BIGINT PRIMARY KEY,
    marker VARCHAR(32) NOT NULL
);

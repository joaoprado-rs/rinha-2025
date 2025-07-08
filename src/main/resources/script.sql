CREATE TABLE tb_payments (
     id UUID PRIMARY KEY,
    amount DECIMAL NOT NULL,
    created_at TIMESTAMP NOT NULL,
    routed_to VARCHAR(20) CHECK (routed_to IN ('DEFAULT', 'FALLBACK')) NOT NULL
);

-- Create test_data_set table
CREATE TABLE test_data_set (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    test_data JSONB NOT NULL,
    expected_pdf BYTEA,
    pdf_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_test_data_set_template FOREIGN KEY (template_id) REFERENCES template(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_test_data_set_template_id ON test_data_set(template_id);
CREATE INDEX idx_test_data_set_created_at ON test_data_set(created_at);

-- Add comment
COMMENT ON TABLE test_data_set IS 'Test data sets für Templates mit optional gespeicherten expected PDFs für Regressions-Tests';
COMMENT ON COLUMN test_data_set.pdf_hash IS 'SHA-256 Hash des expected PDFs für schnellen Vergleich';

CREATE TABLE IF NOT EXISTS solicitacao (
  id UUID PRIMARY KEY,
  cliente_id VARCHAR(255) NOT NULL,
  valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
  status VARCHAR(20) NOT NULL,
  data_criacao TIMESTAMPTZ NOT NULL,
  data_decisao TIMESTAMPTZ,
  aprovador_id VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_solicitacao_cliente ON solicitacao (cliente_id);
CREATE INDEX IF NOT EXISTS idx_solicitacao_status ON solicitacao (status);

ALTER TYPE status_vaga_enum ADD VALUE IF NOT EXISTS 'cancelada';
ALTER TYPE status_candidatura_enum ADD VALUE IF NOT EXISTS 'retirada';
ALTER TYPE status_candidatura_enum ADD VALUE IF NOT EXISTS 'cancelada_por_vaga';

ALTER TABLE vagas ADD COLUMN IF NOT EXISTS categoria varchar(100);
ALTER TABLE vagas ADD COLUMN IF NOT EXISTS experiencia varchar(100);
ALTER TABLE vagas ADD COLUMN IF NOT EXISTS data_limite_candidatura date;
ALTER TABLE vagas ADD COLUMN IF NOT EXISTS abrangencia varchar(30);
ALTER TABLE perfis_contratantes ADD COLUMN IF NOT EXISTS tipo_perfil varchar(100);

CREATE TABLE IF NOT EXISTS fotos_vaga (
    vaga_id bigint not null references vagas(id) on delete cascade,
    ordem integer not null,
    url varchar(500) not null,
    primary key (vaga_id, ordem)
);

CREATE INDEX IF NOT EXISTS idx_vagas_status_id ON vagas(status, id);
CREATE INDEX IF NOT EXISTS idx_vagas_cidade ON vagas(cidade);
CREATE INDEX IF NOT EXISTS idx_vagas_estado ON vagas(estado);
CREATE INDEX IF NOT EXISTS idx_vagas_contratante_id ON vagas(contratante_id);

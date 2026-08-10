# Palco — projeto integrado

Projeto unificado para apresentação parcial do PJI:

- `frontend`: React/Create React App usado como servidor das telas HTML originais do Palco;
- `backend`: API Spring Boot 4 com autenticação JWT;
- `database`: schema e migrações PostgreSQL.

## Pré-requisitos

- Java 21;
- Docker Desktop;
- Python 3, disponível pelo comando `py` ou `python`.

Node.js e npm são necessários somente para alterar e reconstruir o frontend.
O build já incluído pode ser usado na apresentação sem instalar Node.js.

## 1. Banco de dados

Na raiz do projeto:

```powershell
docker compose up -d database
```

O container cria o banco `portifoliodb`, executa `database/sos_artistas.sql` e depois `database/migration_rf03.sql`.

## 2. Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

A API fica disponível em `http://localhost:8080/api`.

Variáveis aceitas:

- `DB_URL` — padrão `jdbc:postgresql://localhost:5434/portifoliodb`;
- `DB_USER` — padrão `postgres`;
- `DB_PASSWORD` — padrão `1234`;
- `FRONTEND_ORIGINS` — padrão `http://localhost:3000,http://127.0.0.1:3000`;
- `JWT_SECRET` — segredo usado para assinar tokens.

## 3. Frontend

Em outro terminal:

```powershell
cd frontend
py -m http.server 3000 --directory build
```

Abra `http://localhost:3000`. A entrada redireciona para `login.html`.

Se o comando `py` não estiver disponível, use:

```powershell
python -m http.server 3000 --directory build
```

Para desenvolvimento e reconstrução do frontend, instale primeiro o Node.js
com npm e então execute `npm install` e `npm start`.

## Fluxo de apresentação

1. Cadastre um contratante em `cadastro-contratante.html`.
2. Faça login.
3. Complete biografia e localização em `perfil.html`.
4. Publique uma vaga pelo dashboard.
5. Consulte, edite e cancele a vaga em `minhas-vagas.html`.

O cancelamento é lógico: a vaga passa a `CANCELADA`, as candidaturas são preservadas e o evento é registrado em `log_vagas_canceladas`.

## Testes

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
# Os comandos abaixo exigem Node.js e npm instalados:
npm test -- --watchAll=false
npm run build
```

Os testes de integração usam Testcontainers e exigem Docker em execução.

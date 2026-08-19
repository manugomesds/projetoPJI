# Relatório de auditoria e sincronização do backend — RF06, RF07 e RF08

**Projeto:** PJI Palco  
**Branch:** `projetoPJI-10-08-ajustes`  
**Data:** 19/08/2026  
**Escopo:** somente backend  
**Commit-base auditado:** `d1d4d68`

## 1. Estado inicial

A auditoria foi executada antes de qualquer alteração. O estado inicial apresentava:

- erro de sintaxe no `pom.xml`, que impedia o Maven de interpretar o projeto;
- `spring.jpa.hibernate.ddl-auto=create`, incompatível com a preservação do banco oficial;
- mapeamento Java dos enums incompatível com os valores em minúsculas do PostgreSQL oficial;
- RF06 com identidade do artista e status controláveis pelo payload, consultas globais, ausência de perfil completo/vaga aberta e falta de paginação;
- RF07 reutilizando o DTO de criação no `PUT`, expondo campos imutáveis e sem validação de remuneração negativa;
- RF08 com atualização genérica por ID, campos administrativos editáveis, troca de senha sem senha atual/revogação de sessões e cálculo duplicado/incompleto de `perfil_completo`;
- respostas de usuários expondo dados pessoais a outros usuários autenticados;
- segredo JWT conhecido como fallback de produção e SQL logging ativo por padrão.

Já estavam corretos e foram preservados, salvo ajustes mínimos compatíveis:

- autenticação JWT stateless e filtro JWT;
- BCrypt para armazenamento de senhas;
- refresh tokens armazenados como hash;
- RF03, incluindo feed público de vagas, filtros, paginação e regra de visibilidade de vagas canceladas;
- cancelamento lógico de vagas e preservação de candidaturas;
- constraint oficial de candidatura única.

## 2. Diagnóstico resumido e correções

### RF06 — Candidaturas

- criação restrita a ARTISTA autenticado;
- artista derivado exclusivamente do JWT; `artistaId` e `status` do payload são ignorados;
- exigência de `perfil_completo = true` e vaga `ABERTA`;
- status inicial forçado para `PENDENTE`;
- prevenção de duplicidade no Service e pela constraint oficial já existente;
- validação de mensagem e URL de portfólio;
- listagens e consulta individual limitadas ao artista ou ao contratante proprietário da vaga;
- rota genérica mantida apenas por compatibilidade, mas sem acesso global;
- transições de status controladas por papel e propriedade;
- exclusão substituída por retirada lógica, preservando histórico;
- paginação RNF12 com padrão 20, máximo 50 e metadados em headers.

### RF07 — Edição de vagas

- criado `VagaAtualizacaoRequest`, exclusivo do `PUT`;
- `id`, `contratanteId`, status e data de publicação não são editáveis;
- propriedade validada pelo JWT contra a vaga persistida;
- remuneração negativa rejeitada na validação do DTO;
- atualização de tags permanece transacional;
- `tagIds == null` preserva vínculos;
- `tagIds == []` remove vínculos;
- IDs duplicados são normalizados por `Set`;
- tag inexistente aborta e reverte toda a atualização;
- candidaturas e histórico permanecem intactos.

### RF08 — Edição de perfil

- edição/criação/exclusão de perfil limitada ao próprio usuário e ao tipo correto, derivados do JWT;
- IDs enviados no payload e campos administrativos são ignorados;
- criado `PerfilCompletoService` como fonte única do cálculo;
- campos obrigatórios de ARTISTA e CONTRATANTE: nome, data de nascimento, telefone, e-mail, biografia e localização;
- `nomeEmpresa` permanece opcional;
- URLs de portfólio e banner são validadas;
- campos omitidos são preservados quando aplicável;
- respostas públicas de usuário omitem e-mail, telefone, nascimento e dados de responsável;
- troca de senha exige senha atual, valida BCrypt, grava novo hash e revoga todos os refresh tokens;
- contas exclusivamente Google não criam senha pelo fluxo convencional;
- menores exigem nome, telefone e e-mail de responsável legal;
- datas de nascimento futuras são rejeitadas.

### Infraestrutura e segurança

- `ddl-auto` corrigido para `validate`;
- nenhuma geração ou alteração automática de schema;
- conversores JPA alinham enums Java aos enums oficiais em minúsculas;
- ausência de token retorna HTTP `401`; autorização insuficiente retorna `403`;
- produção agora exige `JWT_SECRET` no ambiente;
- segredo de teste isolado em `src/test/resources`;
- SQL logging desativado por padrão e habilitável somente por `JPA_SHOW_SQL=true`.

## 3. Arquivos alterados

### Configuração e infraestrutura

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/test/resources/application.properties`
- `backend/src/main/java/com/portifolio/config/SecurityConfig.java`
- `backend/src/main/java/com/portifolio/model/Usuario.java`
- `backend/src/main/java/com/portifolio/model/Vaga.java`
- `backend/src/main/java/com/portifolio/model/enums/ModeloTrabalho.java`
- `backend/src/main/java/com/portifolio/model/enums/StatusVaga.java`
- `backend/src/main/java/com/portifolio/model/enums/TipoUsuario.java`
- `backend/src/main/java/com/portifolio/model/converter/ModeloTrabalhoConverter.java`
- `backend/src/main/java/com/portifolio/model/converter/StatusCandidaturaConverter.java`
- `backend/src/main/java/com/portifolio/model/converter/StatusVagaConverter.java`
- `backend/src/main/java/com/portifolio/model/converter/TipoUsuarioConverter.java`

### RF06

- `backend/src/main/java/com/portifolio/controller/CandidaturaController.java`
- `backend/src/main/java/com/portifolio/dto/CandidaturaRequest.java`
- `backend/src/main/java/com/portifolio/dto/CandidaturaStatusRequest.java`
- `backend/src/main/java/com/portifolio/repository/CandidaturaRepository.java`
- `backend/src/main/java/com/portifolio/service/CandidaturaService.java`
- `backend/src/main/java/com/portifolio/exception/ApiExceptionHandler.java`
- `backend/src/main/java/com/portifolio/exception/UnprocessableEntityException.java`
- `backend/src/test/java/com/portifolio/controller/CandidaturaControllerRf06IntegrationTest.java`

### RF07

- `backend/src/main/java/com/portifolio/controller/VagaController.java`
- `backend/src/main/java/com/portifolio/dto/VagaAtualizacaoRequest.java`
- `backend/src/main/java/com/portifolio/dto/VagaRequest.java`
- `backend/src/main/java/com/portifolio/service/VagaService.java`
- `backend/src/test/java/com/portifolio/controller/VagaEdicaoRf07IntegrationTest.java`

### RF08

- `backend/src/main/java/com/portifolio/controller/UsuarioController.java`
- `backend/src/main/java/com/portifolio/dto/CadastroRequest.java`
- `backend/src/main/java/com/portifolio/dto/PerfilArtistaRequest.java`
- `backend/src/main/java/com/portifolio/dto/PerfilContratanteRequest.java`
- `backend/src/main/java/com/portifolio/dto/UsuarioAtualizacaoRequest.java`
- `backend/src/main/java/com/portifolio/dto/UsuarioResponse.java`
- `backend/src/main/java/com/portifolio/service/AuthService.java`
- `backend/src/main/java/com/portifolio/service/PerfilArtistaService.java`
- `backend/src/main/java/com/portifolio/service/PerfilCompletoService.java`
- `backend/src/main/java/com/portifolio/service/PerfilContratanteService.java`
- `backend/src/main/java/com/portifolio/service/UsuarioService.java`
- `backend/src/test/java/com/portifolio/controller/PerfilEdicaoRf08IntegrationTest.java`
- `backend/src/test/java/com/portifolio/service/PerfilCompletoServiceTest.java`

### Regressão preservada

- `backend/src/test/java/com/portifolio/controller/VagaControllerRf03IntegrationTest.java` — somente expectativa HTTP sem token corrigida de `403` para `401`.

## 4. RF/RNF relacionado

| Item | Cobertura |
|---|---|
| RF03 | Feed, filtros, paginação, JWT e visibilidade preservados por regressão |
| RF06 | Criação, leitura, análise e retirada segura de candidaturas |
| RF07 | Atualização protegida e transacional de vagas |
| RF08 | Conta, perfis, completude, senha e responsável legal |
| RNF01 | Senhas com BCrypt e refresh tokens armazenados como hash |
| RNF08 | Autenticação/autorização por JWT e identidade server-side |
| RNF12 | Paginação limitada e metadados nas listagens de candidaturas |

## 5. Banco e frontend

- **Banco alterado: não.**
- **Migrations, SQL, tabelas, colunas, enums e constraints alterados: não.**
- **Frontend alterado: não.**
- Verificação: `git diff --exit-code d1d4d68..HEAD -- frontend database` retornou sem diferenças.
- Testes usaram PostgreSQL efêmero via Testcontainers e o schema de teste preexistente; o banco oficial não foi usado nem modificado.

## 6. Testes executados e resultados

O executável global `mvn` não estava instalado no `PATH` e o `mvnw.cmd` local falhou antes de iniciar o Maven. Foi usado o Maven 3.9.15 já existente no cache do Maven Wrapper, executando o mesmo goal `test`.

| Execução | Resultado |
|---|---|
| RF06: `-Dtest=CandidaturaControllerRf06IntegrationTest test` | 12 testes; 0 falhas |
| RF07: `-Dtest=VagaEdicaoRf07IntegrationTest test` | 8 testes; 0 falhas |
| RF08: `-Dtest=PerfilCompletoServiceTest,PerfilEdicaoRf08IntegrationTest test` | 20 testes; 0 falhas |
| Suíte final: `mvn test` | 61 testes; 0 falhas; 0 erros; 0 ignorados; `BUILD SUCCESS` |
| Integração legada explícita: `-Dtest=UsuarioControllerIT test` | 1 teste; 0 falhas; `BUILD SUCCESS` |

Coberturas relevantes incluem autenticação, papéis, propriedade, IDOR, payload malicioso, vaga aberta, perfil completo, duplicidade, paginação, transições de status, remuneração, tags nulas/vazias/inexistentes, rollback, histórico, campos omitidos, URLs, dados públicos, BCrypt, revogação de refresh tokens e responsável legal.

## 7. Commits

- `ecd9b63` — `infraestrutura: restaurar build e validar schema existente`
- `c143ed2` — `infraestrutura: mapear enums conforme banco oficial`
- `3ea9f1b` — `RF06: proteger candidaturas pelo usuário autenticado`
- `10662d6` — `testes: cobrir segurança e regras do RF06`
- `a5686aa` — `RF07: separar DTO e proteger edição de vagas`
- `ee9c45c` — `testes: cobrir propriedade tags e rollback do RF07`
- `34fbeef` — `RF08: proteger edição do próprio perfil`
- `29b7b5e` — `RF08: validar troca de senha e revogar sessões`
- `beb52d3` — `testes: adicionar cobertura do RF08`
- `4090655` — `testes: alinhar ausência de token ao HTTP 401`
- `4e7f281` — `segurança: exigir segredo JWT por variável de ambiente`

## 8. Riscos e pendências

1. O access token JWT já emitido continua válido até sua expiração, atualmente 24 horas, após a troca de senha. Todos os refresh tokens são revogados imediatamente. Revogação instantânea de access tokens exigiria denylist/cache externo ou versionamento persistido de token; não foi criada coluna por proibição de alterar o banco.
2. `spring.jpa.open-in-view` permanece no padrão habilitado do Spring. A regra de negócio está nos Services, mas recomenda-se desativá-lo em uma etapa separada acompanhada de teste de todas as serializações lazy.
3. A rota autenticada legada `POST /api/usuarios` permanece por compatibilidade. Ela não aceita campos administrativos e aplica validações, mas é redundante em relação a `/api/auth/cadastro`; sua remoção deve ser coordenada com consumidores da API.
4. O `mvnw.cmd` do repositório não inicializou neste Windows. O build foi validado com a distribuição Maven 3.9.15 do próprio cache do Wrapper; recomenda-se regenerar os wrappers em commit de infraestrutura separado.
5. `JWT_SECRET` passa a ser obrigatório no ambiente de execução. Deploy sem essa variável deve falhar no startup, comportamento intencional para impedir uso de segredo conhecido.

## 9. Próximos passos

1. Configurar `JWT_SECRET` forte e exclusivo no ambiente de homologação/produção.
2. Executar smoke test em homologação contra uma cópia não destrutiva do schema oficial, mantendo `ddl-auto=validate`.
3. Confirmar com o frontend o consumo dos headers `X-Page-*` e a retirada lógica de candidaturas.
4. Decidir, com versionamento de API, a remoção da rota legada `POST /api/usuarios`.
5. Planejar revogação imediata de access tokens sem alterar o schema atual, por exemplo com cache externo de curta duração.
6. Regenerar o Maven Wrapper para Windows e adicionar a execução explícita dos testes `*IT` ao pipeline.

## 10. Registro semanal

| Data | Atividade | Resultado | Evidência |
|---|---|---|---|
| 19/08/2026 | Auditoria backend e diagnóstico de RF03/RF06/RF07/RF08 | Falhas de autorização, DTOs, completude e configuração confirmadas | Diff inicial e inspeção Controller → Service → Repository → banco |
| 19/08/2026 | Correção de infraestrutura sem alterar banco | Build restaurado, schema apenas validado e enums compatíveis | Commits `ecd9b63` e `c143ed2` |
| 19/08/2026 | Implementação RF06 | Candidaturas protegidas por JWT, papel, propriedade e estado | 12 testes direcionados aprovados |
| 19/08/2026 | Implementação RF07 | PUT seguro, tags transacionais e histórico preservado | 8 testes direcionados aprovados |
| 19/08/2026 | Implementação RF08 | Perfis próprios, completude central, senha e menores protegidos | 20 testes direcionados aprovados |
| 19/08/2026 | Regressão e fechamento | RF03/JWT preservados; banco e frontend intactos | 61 testes Maven + 1 IT aprovados |

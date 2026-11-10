# BACK-1 / FRONT-3 — Autenticação, perfis e autorização

## Entrega

O backend usa sessão Bearer opaca. Senhas são armazenadas com BCrypt e somente o hash SHA-256 do token é persistido. Usuários inativos, sessões encerradas e tokens vencidos não autenticam.

As migrações relacionadas são:

| Migração | Finalidade |
| --- | --- |
| `V28` | Usuários e sessões. |
| `V29` | Compatibilidade do tipo da chave de sessão. |
| `V30` | Migração dos perfis legados para os cinco perfis atuais. |
| `V31` | Perfil do autor nos novos logs de cálculo. |

## Configuração

| Variável | Uso |
| --- | --- |
| `APP_AUTH_ADMIN_EMAIL` | E-mail do administrador inicial. |
| `APP_AUTH_ADMIN_PASSWORD` | Senha inicial do administrador. Altere fora do desenvolvimento. |
| `APP_AUTH_SESSION_HOURS` | Duração da sessão; padrão `8`. |
| `AI_SERVICE_API_KEY` | Chave interna compartilhada entre backend e serviço de IA. Obrigatória para chamadas à IA. |
| `AI_SERVICE_AUTH_HEADER` | Cabeçalho usado para a chave; padrão `X-Internal-Api-Key`. |

O Dev Container lê esses valores de `.devcontainer/.env` e os repassa ao serviço `app`. Após mudar esse arquivo, reconstrua o container.

## Sessão e contratos do frontend

Rotas públicas: `POST /api/v1/auth/login`, Swagger e OpenAPI. As demais rotas da API exigem `Authorization: Bearer <token>`.

### Login

`POST /api/v1/auth/login`

```json
{ "email": "admin@comissai.local", "senha": "Admin@123" }
```

A resposta contém token, expiração e o usuário com `papel`, `nomePerfil` e `permissoes` efetivas.

### Sessão atual, logout e manutenção própria

| Rota | Finalidade |
| --- | --- |
| `GET /api/v1/auth/me` | Consulta os dados, perfil e permissões da própria sessão. |
| `GET /api/v1/auth/perfis` | Catálogo estável dos cinco perfis para o formulário do frontend. |
| `PATCH /api/v1/auth/me` | Atualiza somente `nome` e `email` da própria conta. |
| `PATCH /api/v1/auth/me/senha` | Recebe `senhaAtual` e `novaSenha` (mínimo de 8 caracteres). |
| `POST /api/v1/auth/logout` | Encerra a sessão atual. |

Trocar a senha encerra todas as sessões abertas da própria conta. O usuário não pode alterar o próprio perfil ou status por essas rotas.

## Perfis e acessos

| Identificador | Nome | Acesso aplicado no servidor |
| --- | --- | --- |
| `ADMINISTRADOR` | Administrador | Acesso completo; único perfil que administra usuários e vê todos os logs. |
| `ANALISTA_NEGOCIOS` | Analista de negócios | Criação/edição de campanhas, regras e simulações. Não importa bases, não executa cálculo definitivo e não vê logs gerais. |
| `GERENTE_VENDAS` | Gerente de vendas | Importações/correções de bases, fechamento e cálculos, publicação/aceite nas rotas existentes e logs dos analistas. |
| `ANALISTA_CODIGO` | Analista de código | Operações do interpretador e revisão de código. Não gerencia bases, usuários ou cálculos definitivos. |
| `AUDITOR` | Auditor | Consultas de negócio e logs autorizados; não possui rotas de alteração. |

A autorização é feita no backend. Ocultar botão no frontend não é controle de segurança: chamadas diretas para uma rota sem permissão retornam `403`.

### Gestão de usuários — somente administrador

| Rota | Finalidade |
| --- | --- |
| `GET /api/v1/usuarios` e `GET /api/v1/usuarios/{id}` | Consultar contas. |
| `POST /api/v1/usuarios` | Cadastrar conta com perfil. E-mail é único. |
| `PUT /api/v1/usuarios/{id}` | Editar nome e e-mail de outra conta. |
| `PATCH /api/v1/usuarios/{id}/perfil` | Atribuir ou alterar perfil. |
| `PATCH /api/v1/usuarios/{id}/ativo?ativo=false` | Desativar ou reativar conta. |
| `DELETE /api/v1/usuarios/{id}` | Exclusão lógica: desativa a conta. |

Exemplo de criação:

```json
{
  "nome": "Ana Silva",
  "email": "ana@empresa.com",
  "senha": "SenhaSegura123",
  "papel": "ANALISTA_NEGOCIOS"
}
```

O perfil é consultado no banco a cada requisição autenticada. Assim, mudança de perfil vale para tokens já emitidos. A desativação encerra todas as sessões da conta e preserva os registros históricos associados ao usuário.

## Logs e autoria

O aceite de simulação usa o e-mail da sessão; o campo legado `aceitoPor` no corpo é ignorado.

Nos logs de cálculo novos, o backend registra automaticamente e-mail e perfil do autor da sessão. A regra de leitura é aplicada no banco, inclusive por busca direta de ID:

| Leitor | Logs visíveis |
| --- | --- |
| Administrador | Todos. |
| Gerente de vendas | `ANALISTA_NEGOCIOS` e `ANALISTA_CODIGO`. |
| Auditor | `ANALISTA_NEGOCIOS`, `ANALISTA_CODIGO` e `GERENTE_VENDAS`. |
| Analistas | Nenhum log geral. |

Logs históricos sem perfil permanecem acessíveis somente ao administrador, evitando alteração de registros imutáveis.

## Integração Backend → IA

O backend envia `X-Internal-Api-Key: <AI_SERVICE_API_KEY>` em todas as chamadas ao serviço de IA e falha se a chave não estiver configurada.

Para bloquear chamadas diretas, o serviço Python também deve validar o mesmo cabeçalho e responder `401` ou `403` para chave ausente/incorreta. O código do serviço Python não existe neste repositório; configure a mesma variável nos dois serviços e não exponha a chave ao frontend.

## Como testar

### 1. Iniciar com migrações

No Dev Container:

```bash
cd backend
./mvnw clean spring-boot:run
```

Verifique no log a execução das migrações até `V31`.

### 2. Login e Swagger

1. Acesse `http://localhost:8080/swagger-ui/index.html`.
2. Execute `POST /api/v1/auth/login` com o administrador do `.devcontainer/.env`.
3. Copie somente o valor de `token`.
4. Clique em **Authorize** e cole o token sem o prefixo `Bearer`; o Swagger o inclui automaticamente.
5. Execute `GET /api/v1/auth/me` e confirme `papel: ADMINISTRADOR`.

### 3. Perfis e bloqueio por API

1. Com administrador, execute `GET /api/v1/auth/perfis` e crie uma conta para cada perfil.
2. Faça login em cada conta e teste chamadas diretas no Swagger ou `curl`.
3. Exemplos esperados:

| Conta | Rota | Resultado esperado |
| --- | --- | --- |
| Auditor | `GET /api/v1/campanhas` | `200`. |
| Auditor | `POST /api/v1/campanhas` | `403`. |
| Analista de negócios | `POST /api/v1/simulacoes` | Permitido, conforme dados válidos. |
| Analista de negócios | `POST /api/v1/imports/upload` | `403`. |
| Analista de código | `POST /api/v1/interpretador/extrair-regra` | Permitido. |
| Analista de código | `POST /api/v1/comissoes/calcular-competencia` | `403`. |
| Gerente | `POST /api/v1/imports/upload` | Permitido, conforme arquivo válido. |
| Gerente | `GET /api/v1/logs-calculo` | `200`, limitado a logs dos analistas. |
| Administrador | `GET /api/v1/usuarios` | `200`. |

### 4. Perfil e desativação em sessão existente

1. Faça login com uma conta de analista e guarde o token.
2. Com administrador, altere o perfil em `PATCH /api/v1/usuarios/{id}/perfil`.
3. Repita uma chamada com o token antigo: as permissões devem refletir o novo perfil.
4. Desative em `PATCH /api/v1/usuarios/{id}/ativo?ativo=false`eve ser recusado.

### 5. Logs e IA

1. Execute cálculos usando contas permitidas e confira `perfilExecutor` no banco para os novos registros.
2. Consulte `/api/v1/logs-calculo` com administrador, gerente e auditor; compare o conjunto retornado com a tabela de visibilidade.
3. Remova temporariamente `AI_SERVICE_API_KEY` e execute uma rota que use IA: a chamada deve falhar sem contatar a IA.
4. Com o serviço Python configurado para validar a chave, chame o endpoint Python sem `X-Internal-Api-Key`: deve receber `401`/`403`; com a chave correta, a chamada do backend deve funcionar.

Para a suíte automatizada, use JDK 21:

```bash
cd backend
./mvnw test
```

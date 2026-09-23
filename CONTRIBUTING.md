# Como Contribuir - Seu Passaporte de Entrada

Estamos felizes em receber você aqui e saber que está interessado em contribuir para o nosso projeto. Cada contribuição é valorizada e ajuda a melhorar a qualidade do nosso trabalho. Este guia apresenta orientações gerais para participar da nossa comunidade de desenvolvimento.

---

## Código de Conduta

Para garantir um ambiente respeitoso e inclusivo, leia e siga nosso [Código de Conduta](./CODE_OF_CONDUCT.md).

---

## Começando a Contribuir

Para começar, você precisará de:

- Uma conta no [GitHub](https://github.com/).
- O sistema de controle de versão [Git](https://git-scm.com/) instalado.
- [Visual Studio Code](https://code.visualstudio.com/) (recomendado).
- [Docker](https://www.docker.com/) e [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalados e em execução.
- Extensão [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) do VS Code.

Você pode contribuir corrigindo problemas, implementando melhorias, escrevendo testes ou aprimorando a documentação. Antes de iniciar, consulte as issues e os pull requests existentes para evitar trabalho duplicado.

---

## Preparando o Ambiente

### 1. Clonar o Repositório

No terminal, clone o repositório e navegue até a pasta:

```bash
git clone <URL_DO_REPOSITORIO>
cd comissionai-backend
```

---

### 2. Executando o Backend com Dev Containers (Recomendado)

O projeto possui um ambiente pré-configurado com **Dev Containers** (`.devcontainer`), contendo Java 21, Maven e uma instância dedicada do PostgreSQL na rede Docker.

1. Abra a pasta do projeto no VS Code:
   ```bash
   code .
   ```
2. Pressione `F1` (ou `Ctrl + Shift + P`) e selecione:
   > **Dev Containers: Reopen in Container**
3. O VS Code criará e iniciará dois containers:
   - `app`: container da aplicação com Java 21, Maven e ferramentas de linha de comando.
   - `db`: container do banco PostgreSQL com persistência de volume.
4. Abra o terminal integrado no VS Code (`Ctrl + '`) e execute:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
5. Para rodar todos os testes automatizados da aplicação:
   ```bash
   mvn test
   ```

A API estará disponível em: `http://localhost:8080/api/v1`  
Documentação Swagger/OpenAPI interativa: `http://localhost:8080/swagger-ui.html`

---

### 3. Executando o Backend no Host Local (Sem Dev Container)

Caso opte por rodar a aplicação diretamente no seu sistema operacional (fora do Dev Container):

1. Certifique-se de ter o **Java 21** e o **Maven** instalados no seu sistema.
2. Inicie o container do banco de dados (na pasta raiz ou via docker compose):
   ```bash
   docker compose -f .devcontainer/docker-compose.yml up -d db
   ```
3. Defina as variáveis de ambiente de conexão com o banco e inicie a aplicação:

   **Linux / macOS (Bash/Zsh):**
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5433
   export DB_NAME=comissai_db
   export POSTGRES_USER=postgres
   export POSTGRES_PASSWORD=postgres

   cd backend
   mvn spring-boot:run
   ```

   **Windows (PowerShell):**
   ```powershell
   $env:DB_HOST="localhost"
   $env:DB_PORT="5433"
   $env:DB_NAME="comissai_db"
   $env:POSTGRES_USER="postgres"
   $env:POSTGRES_PASSWORD="postgres"

   cd backend
   mvn spring-boot:run
   ```

---

## Acessando o Banco de Dados (PostgreSQL)

O banco de dados da aplicação (`comissai_db`) pode ser acessado tanto pelo terminal via `psql` quanto por extensões gráficas como o **SQLTools**.

### 1. Acesso via Terminal (`psql`)

#### A. De dentro do terminal do Dev Container:
Como o PostgreSQL está no container irmão `db`, passe o parâmetro `-h db`:

```bash
psql -h db -U postgres -d comissai_db
```
*(Quando solicitado, digite a senha: `postgres`)*

Ou com autenticação automática em uma única linha:
```bash
PGPASSWORD=postgres psql -h db -U postgres -d comissai_db
```

#### B. Do terminal da máquina host (Windows / Linux fora do container):
Como a porta do PostgreSQL está mapeada para a porta **5433** no host:

```bash
# Se tiver o utilitário psql instalado na máquina:
psql -h localhost -p 5433 -U postgres -d comissai_db

# Ou via comando docker direto:
docker exec -it comissionai-backend_devcontainer-db-1 psql -U postgres -d comissai_db
```

---

### 2. Acesso via Extensão SQLTools (VS Code)

O repositório já disponibiliza perfis pré-configurados em [`.vscode/settings.json`](./.vscode/settings.json). Basta instalar a extensão [SQLTools](https://marketplace.visualstudio.com/items?itemName=mtxr.sqltools) e o driver [SQLTools PostgreSQL](https://marketplace.visualstudio.com/items?itemName=mtxr.sqltools-driver-pg).

Escolha a conexão de acordo com onde o seu VS Code está executando:

#### Cenário A: VS Code aberto DENTRO do Dev Container (Recomendado)
- Selecione a conexão: **`ComissAI DB (DevContainer)`**
  - **Host / Server**: `db`
  - **Porta**: `5432`
  - **Database**: `comissai_db`
  - **Usuário**: `postgres`
  - **Senha**: `postgres`

#### Cenário B: VS Code aberto no Host (Windows / Linux)
- Selecione a conexão: **`ComissAI DB (Host Windows porta 5433)`**
  - **Host / Server**: `localhost`
  - **Porta**: `5433` *(mapeada para evitar conflito com instâncias locais na 5432)*
  - **Database**: `comissai_db`
  - **Usuário**: `postgres`
  - **Senha**: `postgres`

No painel do SQLTools na barra lateral do VS Code, clique com o botão direito na conexão desejada e selecione **"Connect"** para explorar as tabelas e executar queries.

---

### 3. Queries Úteis para Verificação

- **Listar todas as tabelas:**
  ```sql
  \dt
  ```
- **Consultar funcionários e matrículas cadastradas (`tb_registration`):**
  ```sql
  SELECT r.registration, s.code AS cod_loja, s.description AS loja, p.code AS cod_cargo, p.description AS cargo
  FROM tb_registration r
  JOIN tb_store s ON s.id = r.store_id
  JOIN tb_position p ON p.id = r.position_id
  LIMIT 20;
  ```
- **Consultar regras de comissionamento ativas (`tb_regra`):**
  ```sql
  SELECT id, nome, canal, taxa, data_inicio, data_fim, status
  FROM tb_regra;
  ```
- **Consultar logs imutáveis de cálculo para auditoria (`tb_log_calculo_imutavel`):**
  ```sql
  SELECT id, protocolo, id_venda, matricula, valor_venda, taxa_aplicada, valor_comissao, data_venda, executado_em
  FROM tb_log_calculo_imutavel
  ORDER BY executado_em DESC;
  ```

---

## Enviando uma Contribuição

1. Crie uma branch para sua alteração:
   ```bash
   git switch -c tipo/descricao-da-alteracao
   ```
2. Faça alterações focadas no objetivo da contribuição, seguindo os padrões existentes no projeto.
3. Verifique o funcionamento da alteração e execute a suíte de testes:
   ```bash
   cd backend
   mvn test
   ```
4. Revise os arquivos alterados e crie um commit com uma mensagem clara:
   ```bash
   git status
   git add <ARQUIVOS_ALTERADOS>
   git commit -m "feat/fix: Descreve a alteração realizada"
   ```
5. Envie sua branch para o GitHub:
   ```bash
   git push -u origin tipo/descricao-da-alteracao
   ```
6. Abra um Pull Request para a branch padrão do repositório original. Explique o que mudou, o motivo da mudança e como você validou o resultado.

---

## Relatando Problemas e Sugerindo Melhorias

Ao abrir uma issue, use um título claro e descreva o contexto. Para problemas, informe os passos para reproduzir, o comportamento esperado e o observado. Para melhorias, explique a necessidade e o resultado desejado.

---

Equipe Bug Busters

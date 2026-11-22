# Correções do PR #33 — integração de acesso e permissões

## Configuração coordenada

- Backend: `AI_SERVICE_API_KEY` define a chave privada compartilhada; `AI_SERVICE_AUTH_HEADER` usa `X-Internal-Api-Key` por padrão. O cabeçalho já é enviado pelo backend nas chamadas à IA.
- IA: configurar **a mesma chave e o mesmo nome de cabeçalho**. A branch `fix/autenticacao-consultas-backend` envia essa credencial nas consultas de ferramentas ao backend. Reiniciar ambos após configurar.
- Nunca colocar a chave em variáveis `VITE_*`, no frontend, na URL ou em exemplos com credenciais reais.
- A chave interna só autentica `/api/v1/consultas-ia/**`, somente para GET/POST. Ela não concede sessão de usuário, acesso a `/usuarios` ou operações de campanhas/cálculo. Chave vazia/incorreta não autoriza consultas.
- A validação de chamadas diretas à API Python continua sendo uma integração separada: adicionar um cabeçalho no cliente não implementa uma barreira de entrada no serviço Python.

## Ajustes no contrato

- `401` JSON padronizado: credenciais inválidas, conta desativada, sessão ausente, encerrada ou expirada. O frontend deve limpar a sessão e solicitar nova entrada.
- `403` JSON padronizado: usuário autenticado sem permissão.
- `409`: desativação/exclusão lógica da própria conta, mudança do próprio perfil ou remoção do último administrador ativo. Os administradores são bloqueados em ordem de ID durante a verificação para serializar alterações concorrentes.
- Auditor consulta bases/importações sem obter permissão de escrita.
- Analista de código pode salvar a proposta pelos contratos existentes de campanha. Progresso pode ser registrado por negócio, código, gerente e administrador; auditor permanece em leitura.
- Negócio pode registrar o aceite da simulação na etapa 3. A publicação/ativação continua restrita ao gerente e administrador. O acesso gerencial ao aceite existente foi preservado.
- O frontend deve mapear o antigo identificador local `GERENTE` para `GERENTE_VENDAS` retornado pelo servidor e usar o catálogo oficial de perfis.

## Limite desta correção

Não implementa BACK-4: etapa pendente oficial, transições, encaminhamento, devolução com motivo, autorização por estado e invalidação de resultados. O progresso existente é navegação; não é aprovação ou passagem de responsabilidade. As permissões aqui são por operação/perfil, não por uma esteira oficial ainda inexistente. Também não conecta as telas demonstrativas do frontend à autenticação real.

## Verificação

Os novos testes incluem filtros reais, login/me/logout, expiração, desativação, troca de senha e revogação, alteração de perfil em token já emitido, preservação de identidade na exclusão lógica, matriz de acesso, aceite, credencial interna restrita e proteção administrativa. A suíte anterior de controllers usa MockMvc sem filtros e, isoladamente, não demonstra autorização.

Testar no ambiente integrado, após configurar a chave em ambos os serviços: login real → interpretação com resolução de público/concorrência → edição por código → simulação por negócio → consulta pelo auditor → publicação pelo gerente. Isso depende da futura integração do frontend e não é comprovado apenas pelos testes controlados.

## Resultados executados nesta correção

- Backend: compilação aprovada com Java 21; suíte completa com 405 testes encontrados, 397 aprovados, 8 ignorados e nenhuma falha/erro. Inclui 12 testes novos de autenticação/permissões e proteção administrativa. Execução em container Maven temporário e banco H2, sem usar o banco do usuário.
- IA: suíte completa com 322 testes aprovados, incluindo 4 novos testes de autenticação das ferramentas; sem chamadas ao provedor real.
- Integração com os serviços reais e migrações em PostgreSQL não foram executadas. Configurar a chave nos dois serviços e validar o ambiente antes da aprovação funcional completa.

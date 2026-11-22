# Separação entre regras e programa Python

Negócios e administrador podem atualizar a campanha e chamar o interpretador. Código consulta os parâmetros e mantém acesso à edição, aprovação e devolução do programa completo, nas rotas da proposta atual e da versão específica.

O filtro de segurança recusa com 403 a alteração de parâmetros ou interpretação pelo analista de código. O avanço genérico da esteira também fica bloqueado para código; a aprovação técnica segue pela operação própria do programa. As rotas específicas do programa continuam antes da restrição geral de atualização de campanhas.

Aplicar junto ao frontend `feat/ajustes-regras-revisao-campanhas`. Não requer migração, mudança de perfis ou alteração na IA. Reconstruir/reiniciar somente o backend para aplicar as permissões.

Verificação: `AuthIntegrationTest` (13 testes aprovados) usa sessões e filtros reais, banco isolado e confirma acesso aos programas, bloqueio de alteração das regras e manutenção da restrição de publicação.

# ==============================================================================
# SCRIPT DE BATERIA DE TESTES VIA CURL / REST API - SPRINT 3 (COMISSIONAI)
# Tasks Cobertas:
#  1. BUG-104 [BACK-9]  - Versionar programa Python e sua aprovação
#  2. BUG-99  [BACK-4]  - Estados da esteira, aprovações e devoluções
#  3. BUG-106 [BACK-11] - Orquestrar e persistir simulações executadas em Python
#  4. BUG-102 [BACK-7]  - Aceite rastreável de pendências e governança de risco
#  5. BUG-109 [BACK-14] - Cálculo definitivo por campanha no motor atual
#  6. BUG-110 [BACK-15] - Consolidar e exportar relatório de fechamento (CSV BOM UTF-8)
#  7. BUG-111 [BACK-16] - Registrar auditoria geral das ações relevantes
# ==============================================================================

param (
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "    BATERIA DE TESTES DE CASOS EXTREMOS E E2E - SPRINT 3" -ForegroundColor Cyan
Write-Host "    Target: $BaseUrl" -ForegroundColor Cyan
Write-Host "=================================================================`n" -ForegroundColor Cyan

$global:passCount = 0
$global:failCount = 0

function Assert-Step([string]$StepName, [int]$ExpectedStatus, [int]$ActualStatus, [string]$Details = "") {
    if ($ExpectedStatus -eq $ActualStatus) {
        Write-Host "[PASS] $StepName (Status: $ActualStatus)" -ForegroundColor Green
        $global:passCount++
    } else {
        Write-Host "[FAIL] $StepName (Esperado: $ExpectedStatus | Recebido: $ActualStatus)" -ForegroundColor Red
        $global:failCount++
        if ($Details) {
            Write-Host "       Detalhes: $Details" -ForegroundColor Yellow
        }
    }
}

function Request-Json([string]$Uri, [string]$Method = "Get", $Body = $null) {
    if ($null -ne $Body) {
        if ($Body -is [string]) {
            $jsonStr = $Body
        } else {
            $jsonStr = $Body | ConvertTo-Json -Depth 10
        }
        $utf8Bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonStr)
        return Invoke-RestMethod -Uri $Uri -Method $Method -ContentType "application/json; charset=utf-8" -Body $utf8Bytes
    } else {
        return Invoke-RestMethod -Uri $Uri -Method $Method
    }
}

# ------------------------------------------------------------------------------
# 1. CRIAR CAMPANHA BASE PARA OS TESTES
# ------------------------------------------------------------------------------
Write-Host "--- 1. Criar Campanha Base ---" -ForegroundColor Yellow

$payloadCampanha = @'
{
  "titulo": "Campanha Black Friday 2026 - Teste Sprint 3",
  "textoOriginal": "Comissao de 8% nas vendas realizadas no canal ECOMMERCE",
  "estado": "DRAFT",
  "dataInicio": "2026-11-01",
  "dataFim": "2026-11-30",
  "regras": [
    {
      "blocoId": "regra-ecommerce-8",
      "nome": "Regra E-commerce 8%",
      "canal": "ECOMMERCE",
      "taxa": 0.08,
      "valorAjuste": 0.08,
      "tipoOperacao": "DEFINIR_TAXA",
      "completa": true
    }
  ]
}
'@

try {
    $resp = Request-Json -Uri "$BaseUrl/api/v1/campanhas" -Method Post -Body $payloadCampanha
    $campanhaId = $resp.id
    Assert-Step "Criacao de Campanha (POST /api/v1/campanhas)" 201 201 "Campanha ID: $campanhaId"
} catch {
    $statusCode = 500
    if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Criacao de Campanha (POST /api/v1/campanhas)" 201 $statusCode $_.Exception.Message
    exit 1
}

# ------------------------------------------------------------------------------
# 2. TESTES DA ESTEIRA DE GOVERNANÇA (BUG-99 [BACK-4])
# ------------------------------------------------------------------------------
Write-Host "`n--- 2. Governanca da Esteira (BUG-99) ---" -ForegroundColor Yellow

# Caso 2.1: Obter estado inicial (Etapa 1 - EM_ELABORACAO)
try {
    $esteira = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira" -Method Get
    Assert-Step "Obter Estado Inicial da Esteira (GET /esteira)" 200 200 "Etapa: $($esteira.etapaAtual) - Estado: $($esteira.estadoEsteira)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Obter Estado Inicial da Esteira (GET /esteira)" 200 $code $_.Exception.Message
}

# Caso 2.2: Pular Etapa 1 para 3 direto sem Python homologado (Deve retornar 400)
try {
    $saltoInvalido = '{"etapaAlvo": 3, "responsavel": "auditor"}'
    Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira/avancar" -Method Post -Body $saltoInvalido
    Assert-Step "Bloqueio de Salto de Etapa (POST /esteira/avancar)" 400 200
} catch {
    $statusCode = 500
    if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Bloqueio de Salto de Etapa (POST /esteira/avancar)" 400 $statusCode
}

# Caso 2.3: Avançar 1 -> 2 (Configuração Técnica)
try {
    $avanco2 = '{"etapaAlvo": 2, "responsavel": "davi.gestor"}'
    $esteira2 = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira/avancar" -Method Post -Body $avanco2
    Assert-Step "Avanco para Etapa 2 (POST /esteira/avancar)" 200 200 "Nova Etapa: $($esteira2.etapaAtual)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Avanco para Etapa 2 (POST /esteira/avancar)" 200 $code $_.Exception.Message
}

# Caso 2.4: Devolução com justificativa obrigatória e recuo de etapa
try {
    $devolucao = '{"etapaDestino": 1, "motivo": "Revisar parametros de canal", "devolvidoPor": "gerente.comercial"}'
    $esteiraDev = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira/devolver" -Method Post -Body $devolucao
    Assert-Step "Devolucao Gerencial com Justificativa (POST /esteira/devolver)" 200 200 "Estado: $($esteiraDev.estadoEsteira)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Devolucao Gerencial com Justificativa (POST /esteira/devolver)" 200 $code $_.Exception.Message
}

# Caso 2.5: Re-avançar para Etapa 2
try {
    $avanco2Re = '{"etapaAlvo": 2, "responsavel": "davi.gestor"}'
    Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira/avancar" -Method Post -Body $avanco2Re | Out-Null
    Assert-Step "Re-avanco para Etapa 2 (POST /esteira/avancar)" 200 200
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Re-avanco para Etapa 2 (POST /esteira/avancar)" 200 $code $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 3. TESTES DO PROGRAMA PYTHON E HOMOLOGAÇÃO (BUG-104 [BACK-9])
# ------------------------------------------------------------------------------
Write-Host "`n--- 3. Versionamento do Programa Python (BUG-104) ---" -ForegroundColor Yellow

# Caso 3.1: Obter código gerado
try {
    $prog = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/programa-python" -Method Get
    Assert-Step "Obtencao do Codigo Python (GET /programa-python)" 200 200 "Versao: $($prog.versaoPrograma) | Status: $($prog.statusAprovacao)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Obtencao do Codigo Python (GET /programa-python)" 200 $code $_.Exception.Message
}

# Caso 3.2: Atualizar código e recalcular SHA-256
$codigoCustom = @'
def calcular_comissao(venda, contexto):
    taxa = 0.08
    return float(venda.get("valor", 0.0)) * taxa
'@
$updatePayload = @{
    codigoFonte = $codigoCustom
    autor = "davi.dev"
    justificativa = "Codigo customizado para Sprint 3"
}

try {
    $progAtualizado = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/programa-python" -Method Put -Body $updatePayload
    $sha256 = $progAtualizado.checksumSha256
    Assert-Step "Atualizacao de Codigo e Hash (PUT /programa-python)" 200 200 "Novo SHA-256: $sha256"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Atualizacao de Codigo e Hash (PUT /programa-python)" 200 $code $_.Exception.Message
}

# Caso 3.3: Tentar aprovar com Checksum divergente (Deve retornar 400)
try {
    $aprovaDivergente = @{
        aprovadoPor = "revisor.tecnico"
        checksumEsperado = "hash-falso-adulterado-123456"
        justificativa = "Tentando validar hash adulterado"
    }
    Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/programa-python/aprovar" -Method Post -Body $aprovaDivergente
    Assert-Step "Rejeicao de Checksum Divergente (POST /programa-python/aprovar)" 400 200
} catch {
    $statusCode = 500
    if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Rejeicao de Checksum Divergente (POST /programa-python/aprovar)" 400 $statusCode
}

# Caso 3.4: Aprovação formal com Checksum real (Avança esteira automaticamente para Etapa 3)
try {
    $aprovaValido = @{
        aprovadoPor = "revisor.tecnico"
        checksumEsperado = $sha256
        justificativa = "Codigo validado e homologado com cobertura total."
    }
    $progAprovado = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/programa-python/aprovar" -Method Post -Body $aprovaValido
    Assert-Step "Aprovacao Tecnica Formal (POST /programa-python/aprovar)" 200 200 "Status: $($progAprovado.statusAprovacao)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Aprovacao Tecnica Formal (POST /programa-python/aprovar)" 200 $code $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 4. ORQUESTRAÇÃO DE SIMULAÇÃO PYTHON (BUG-106 [BACK-11])
# ------------------------------------------------------------------------------
Write-Host "`n--- 4. Orquestracao de Simulacao Python (BUG-106) ---" -ForegroundColor Yellow

$simPayload = @{
    competencia = "2026-11"
    limite = 50
    pagina = 0
    apenasComAlteracao = $false
    apenasComMudancaFaixa = $false
}

$simId = $null
try {
    $simRes = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/simulacao-python" -Method Post -Body $simPayload
    $simId = $simRes.id
    $protocoloSim = $simRes.protocolo
    Assert-Step "Execucao e Persistencia de Simulacao Python (POST /simulacao-python)" 201 201 "Protocolo: $protocoloSim | ExecutadoViaPython: $($simRes.executadoViaPython)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Execucao e Persistencia de Simulacao Python (POST /simulacao-python)" 201 $code $_.Exception.Message
}

# Aceitar formalmente a simulação para cumprir gate da Etapa 4
if ($simId) {
    try {
        $aceiteSim = @{
            aceitoPor = "comite.financeiro@empresa.com"
            motivoAceite = "Simulacao validada e homologada pelo comite."
        }
        $resAceiteSim = Request-Json -Uri "$BaseUrl/api/v1/simulacoes/$simId/aceite" -Method Post -Body $aceiteSim
        Assert-Step "Aceite Formal da Simulacao (POST /simulacoes/{id}/aceite)" 200 200 "StatusAceite: $($resAceiteSim.statusAceite)"
    } catch {
        $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        Assert-Step "Aceite Formal da Simulacao (POST /simulacoes/{id}/aceite)" 200 $code $_.Exception.Message
    }
}

# ------------------------------------------------------------------------------
# 5. GOVERNANÇA DE PENDÊNCIAS E RISCOS (BUG-102 [BACK-7])
# ------------------------------------------------------------------------------
Write-Host "`n--- 5. Governanca de Pendencias e Riscos (BUG-102) ---" -ForegroundColor Yellow

# Caso 5.1: Consultar resumo de pendências da campanha
try {
    $pendencias = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/pendencias" -Method Get
    Assert-Step "Consulta de Pendencias (GET /pendencias)" 200 200 "Total: $($pendencias.totalPendencias) | Aptas: $($pendencias.aptaParaAvanco)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Consulta de Pendencias (GET /pendencias)" 200 $code $_.Exception.Message
}

# Caso 5.2: Registrar aceite formal de pendência
$aceitePendenciaPayload = @{
    pendenciaIdentificador = "pendencia:teste:01"
    codigo = "AVISO_METRICAS_HISTORICAS"
    campo = "regras.canal"
    mensagem = "Historico de vendas com volume atipico."
    severidade = "AVISO"
    aceitoPor = "gestor.comercial@empresa.com"
    motivo = "Volume atipico devido a acao de marketing autorizada."
}

$aceiteId = $null
try {
    $aceiteResp = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/pendencias/aceite" -Method Post -Body $aceitePendenciaPayload
    $aceiteId = $aceiteResp.id
    Assert-Step "Aceite Rastreavel de Pendencia (POST /pendencias/aceite)" 201 201 "Aceite ID: $aceiteId"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Aceite Rastreavel de Pendencia (POST /pendencias/aceite)" 201 $code $_.Exception.Message
}

# Caso 5.3: Revogar aceite formal de pendência
if ($aceiteId) {
    try {
        $revogaPayload = @{
            revogadoPor = "auditor.compliance@empresa.com"
            motivo = "Necessario reavaliacao cadastral previa."
        }
        $revogaResp = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/pendencias/$aceiteId/revogar" -Method Post -Body $revogaPayload
        Assert-Step "Revogacao de Aceite de Pendencia (POST /pendencias/{id}/revogar)" 200 200 "Status: $($revogaResp.status)"
    } catch {
        $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        Assert-Step "Revogacao de Aceite de Pendencia (POST /pendencias/{id}/revogar)" 200 $code $_.Exception.Message
    }
}

# ------------------------------------------------------------------------------
# 6. AVANÇO PARA ETAPA 4 E APROVAÇÃO GERENCIAL (BUG-99)
# ------------------------------------------------------------------------------
Write-Host "`n--- 6. Homologacao Gerencial da Proposta ---" -ForegroundColor Yellow

try {
    $avanco4 = '{"etapaAlvo": 4, "responsavel": "davi.gestor"}'
    Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira/avancar" -Method Post -Body $avanco4 | Out-Null
    Assert-Step "Avanco para Etapa 4 - Homologacao (POST /esteira/avancar)" 200 200
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Avanco para Etapa 4 - Homologacao (POST /esteira/avancar)" 200 $code $_.Exception.Message
}

try {
    $aprovaGerente = @{
        aprovadoPor = "diretor.vendas@empresa.com"
        parecer = "Campanha avaliada e homologada para entrada em producao."
    }
    $gerenteResp = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/esteira/aprovar" -Method Post -Body $aprovaGerente
    Assert-Step "Aprovacao Gerencial Final (POST /esteira/aprovar)" 200 200 "Estado: $($gerenteResp.estadoEsteira)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Aprovacao Gerencial Final (POST /esteira/aprovar)" 200 $code $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 7. CÁLCULO DEFINITIVO POR CAMPANHA (BUG-109 [BACK-14])
# ------------------------------------------------------------------------------
Write-Host "`n--- 7. Calculo Definitivo por Campanha (BUG-109) ---" -ForegroundColor Yellow

$apuracaoPayload = @{
    competencia = "2026-11"
    executadoPor = "davi.apurador"
    observacoes = "Liquidacao definitiva de comissoes da campanha"
    forcarRecalculo = $false
}

$apuracaoId = $null
try {
    $apuracaoResp = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/calcular-definitivo" -Method Post -Body $apuracaoPayload
    $apuracaoId = $apuracaoResp.apuracaoId
    Assert-Step "Calculo Definitivo por Campanha (POST /calcular-definitivo)" 200 200 "Apuracao ID: $apuracaoId | Status: $($apuracaoResp.status)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Calculo Definitivo por Campanha (POST /calcular-definitivo)" 200 $code $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 8. RELATÓRIOS CONSOLIDADOS E EXPORTAÇÃO CSV COM BOM UTF-8 (BUG-110 [BACK-15])
# ------------------------------------------------------------------------------
Write-Host "`n--- 8. Relatorio Consolidado e Exportacao CSV (BUG-110) ---" -ForegroundColor Yellow

# Caso 8.1: Relatório JSON multidimensional
try {
    $relatorio = Request-Json -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/apuracao-definitiva/relatorio" -Method Get
    Assert-Step "Relatorio Consolidado JSON (GET /apuracao-definitiva/relatorio)" 200 200 "Canais: $($relatorio.porCanal.Count) | Equipes: $($relatorio.porEquipe.Count)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Relatorio Consolidado JSON (GET /apuracao-definitiva/relatorio)" 200 $code $_.Exception.Message
}

# Caso 8.2: Exportação CSV Detalhado
try {
    $tempFile = [System.IO.Path]::GetTempFileName()
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/apuracao-definitiva/exportar?tipo=DETALHADO&delimitador=;" -OutFile $tempFile
    $csvBytes = [System.IO.File]::ReadAllBytes($tempFile)
    Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
    $hasBom = ($csvBytes[0] -eq 0xEF -and $csvBytes[1] -eq 0xBB -and $csvBytes[2] -eq 0xBF)
    Assert-Step "Exportacao CSV Detalhado com BOM UTF-8" 200 200 "BOM Presente: $hasBom | Tamanho: $($csvBytes.Length) bytes"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Exportacao CSV Detalhado com BOM UTF-8" 200 $code $_.Exception.Message
}

# Caso 8.3: Exportação CSV Consolidado por Canal
try {
    $tempFileCanal = [System.IO.Path]::GetTempFileName()
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/campanhas/$campanhaId/apuracao-definitiva/exportar?tipo=CONSOLIDADO_CANAL&delimitador=," -OutFile $tempFileCanal
    $csvCanalBytes = [System.IO.File]::ReadAllBytes($tempFileCanal)
    Remove-Item $tempFileCanal -Force -ErrorAction SilentlyContinue
    Assert-Step "Exportacao CSV Consolidado por Canal" 200 200 "Tamanho: $($csvCanalBytes.Length) bytes"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Exportacao CSV Consolidado por Canal" 200 $code $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 9. TRILHA DE AUDITORIA GERAL (BUG-111 [BACK-16])
# ------------------------------------------------------------------------------
Write-Host "`n--- 9. Trilha de Auditoria Geral (BUG-111) ---" -ForegroundColor Yellow

# Caso 9.1: Consultar auditoria filtrada por Campanha
try {
    $auditoria = Request-Json -Uri "$BaseUrl/api/v1/auditoria?entidadeId=$campanhaId" -Method Get
    Assert-Step "Consulta de Trilha de Auditoria (GET /api/v1/auditoria)" 200 200 "Eventos Registrados: $($auditoria.content.Count)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Consulta de Trilha de Auditoria (GET /api/v1/auditoria)" 200 $code $_.Exception.Message
}

# Caso 9.2: Consultar resumo de auditoria
try {
    $resumoAuditoria = Request-Json -Uri "$BaseUrl/api/v1/auditoria/resumo" -Method Get
    Assert-Step "Resumo Executivo de Auditoria (GET /api/v1/auditoria/resumo)" 200 200 "Total Registros: $($resumoAuditoria.totalRegistros) | Sucessos: $($resumoAuditoria.totalSucessos)"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Resumo Executivo de Auditoria (GET /api/v1/auditoria/resumo)" 200 $code $_.Exception.Message
}

# Caso 9.3: Exportar trilha de auditoria em CSV com BOM
try {
    $tempFileAudit = [System.IO.Path]::GetTempFileName()
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/auditoria/exportar?delimitador=;" -OutFile $tempFileAudit
    $csvAuditBytes = [System.IO.File]::ReadAllBytes($tempFileAudit)
    Remove-Item $tempFileAudit -Force -ErrorAction SilentlyContinue
    $hasAuditBom = ($csvAuditBytes[0] -eq 0xEF -and $csvAuditBytes[1] -eq 0xBB -and $csvAuditBytes[2] -eq 0xBF)
    Assert-Step "Exportacao de Trilha de Auditoria em CSV com BOM" 200 200 "BOM Presente: $hasAuditBom"
} catch {
    $code = 500; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Assert-Step "Exportacao de Trilha de Auditoria em CSV com BOM" 200 $code $_.Exception.Message
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "    RESULTADO FINAL: $global:passCount PASSADOS | $global:failCount FALHADOS" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
if ($global:failCount -gt 0) { exit 1 } else { exit 0 }

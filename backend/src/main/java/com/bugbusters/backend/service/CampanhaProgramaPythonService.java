package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.ProgramaPythonResponse;
import com.bugbusters.backend.dto.programapython.RejeitarProgramaPythonRequest;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.CampanhaProgramaPythonRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CampanhaProgramaPythonService {

    private static final Logger log = LoggerFactory.getLogger(CampanhaProgramaPythonService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final CampanhaProgramaPythonRepository programaPythonRepository;
    private final RegraRepository regraRepository;
    private final FechamentoService fechamentoService;
    private final com.bugbusters.backend.repository.CampanhaEsteiraTransicaoRepository transicaoRepository;
    private final AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider = (paliativo) -> (paliativo != null && !paliativo.isBlank()) ? paliativo.trim() : "SISTEMA";

    public CampanhaProgramaPythonService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            CampanhaProgramaPythonRepository programaPythonRepository,
            RegraRepository regraRepository,
            FechamentoService fechamentoService,
            com.bugbusters.backend.repository.CampanhaEsteiraTransicaoRepository transicaoRepository,
            AuditoriaGeralService auditoriaService
    ) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.programaPythonRepository = programaPythonRepository;
        this.regraRepository = regraRepository;
        this.fechamentoService = fechamentoService;
        this.transicaoRepository = transicaoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public ProgramaPythonResponse obterOuGerarProgramaAtual(Long campanhaId, Integer versaoNumero) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        Optional<CampanhaProgramaPython> optPrograma = programaPythonRepository
                .findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId());

        if (optPrograma.isPresent()) {
            return ProgramaPythonResponse.de(optPrograma.get());
        }

        // Se ainda não existir nenhum programa gravado, sintetiza o código a partir das regras da versão
        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
        String codigoSintetizado = gerarCodigoConsolidado(versao, regras);

        CampanhaProgramaPython novoPrograma = new CampanhaProgramaPython(versao, 1, codigoSintetizado);
        novoPrograma.setJustificativa("Programa inicial gerado automaticamente a partir das regras da campanha.");
        novoPrograma = programaPythonRepository.save(novoPrograma);

        log.info("Programa Python inicial versão 1 gerado para campanha {} versão {}.", campanhaId, versao.getNumero());
        return ProgramaPythonResponse.de(novoPrograma);
    }

    @Transactional
    public ProgramaPythonResponse salvarOuAtualizarCodigo(Long campanhaId, Integer versaoNumero, AtualizarProgramaPythonRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        validarImutabilidadeVersao(versao);
        if (fechamentoService != null) {
            fechamentoService.validarPeriodoDisponivel(versao.getDataInicio(), versao.getDataFim());
        }

        String novoCodigo = request.codigoFonte().trim();
        String novoChecksum = CampanhaProgramaPython.calcularChecksum(novoCodigo);

        Optional<CampanhaProgramaPython> optAtual = programaPythonRepository
                .findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId());

        if (optAtual.isPresent()) {
            CampanhaProgramaPython atual = optAtual.get();
            // Se o código e o checksum forem idênticos, preserva a versão
            if (Objects.equals(atual.getChecksumSha256(), novoChecksum)) {
                if (request.justificativa() != null && !request.justificativa().isBlank()) {
                    atual.setJustificativa(request.justificativa());
                    atual = programaPythonRepository.save(atual);
                }
                return ProgramaPythonResponse.de(atual);
            }
        }

        int proximaVersaoPrograma = optAtual.map(p -> p.getVersaoPrograma() + 1).orElse(1);
        String autor = usuarioAtualProvider.obterUsuarioAtual(request.autor());

        CampanhaProgramaPython novoPrograma = new CampanhaProgramaPython(versao, proximaVersaoPrograma, novoCodigo);
        novoPrograma.setJustificativa(request.justificativa() != null && !request.justificativa().isBlank()
                ? request.justificativa()
                : (!"SISTEMA".equals(autor) ? "Atualizado por: " + autor : "Código executável atualizado"));
        novoPrograma.setStatusAprovacao(StatusAprovacaoPrograma.PENDENTE_REVISAO);

        // A alteração do código-fonte invalida a aprovação técnica prévia
        versao.setProgramaPythonAprovado(null);
        campanhaVersaoRepository.save(versao);

        novoPrograma = programaPythonRepository.save(novoPrograma);

        log.info("Novo programa Python (versão {}) salvo para campanha {} versão {}. Aprovação resetada para PENDENTE_REVISAO.",
                proximaVersaoPrograma, campanhaId, versao.getNumero());
        return ProgramaPythonResponse.de(novoPrograma);
    }

    @Transactional
    public ProgramaPythonResponse aprovarPrograma(Long campanhaId, Integer versaoNumero, AprovarProgramaPythonRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        validarImutabilidadeVersao(versao);
        if (fechamentoService != null) {
            fechamentoService.validarPeriodoDisponivel(versao.getDataInicio(), versao.getDataFim());
        }

        CampanhaProgramaPython programa = programaPythonRepository
                .findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId())
                .orElseGet(() -> {
                    List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
                    String cod = gerarCodigoConsolidado(versao, regras);
                    CampanhaProgramaPython p = new CampanhaProgramaPython(versao, 1, cod);
                    return programaPythonRepository.save(p);
                });

        // Verificação obrigatória de concorrência e integridade com o checksum esperado pelo revisor
        if (request.checksumEsperado() == null || request.checksumEsperado().isBlank()) {
            throw new BusinessException("O checksum SHA-256 esperado é obrigatório para confirmar a integridade do código aprovado.");
        }
        if (!Objects.equals(programa.getChecksumSha256().toLowerCase(), request.checksumEsperado().trim().toLowerCase())) {
            throw new BusinessException("Divergência de integridade: o código-fonte foi alterado antes da confirmação de aprovação. " +
                    "Atualize a página e revise a versão mais recente.");
        }

        String aprovadoPor = usuarioAtualProvider.obterUsuarioAtual(request.aprovadoPor());
        if (aprovadoPor == null || aprovadoPor.isBlank() || ("SISTEMA".equals(aprovadoPor) && (request.aprovadoPor() == null || request.aprovadoPor().isBlank()))) {
            throw new BusinessException("A identificação do revisor técnico é obrigatória.");
        }

        programa.setStatusAprovacao(StatusAprovacaoPrograma.APROVADO);
        programa.setAprovadoPor(aprovadoPor);
        programa.setAprovadoEm(OffsetDateTime.now());
        programa.setRejeitadoPor(null);
        programa.setRejeitadoEm(null);
        if (request.justificativa() != null && !request.justificativa().isBlank()) {
            programa.setJustificativa(request.justificativa());
        }

        programa = programaPythonRepository.save(programa);

        // Vincula o programa aprovado na versão da campanha e avança a esteira para simulação (Etapa 3)
        versao.setProgramaPythonAprovado(programa);
        com.bugbusters.backend.model.EstadoEsteiraCampanha estAntigo = versao.getEstadoEsteira() != null
                ? versao.getEstadoEsteira() : com.bugbusters.backend.model.EstadoEsteiraCampanha.EM_REVISAO_TECNICA;
        versao.setEstadoEsteira(com.bugbusters.backend.model.EstadoEsteiraCampanha.EM_SIMULACAO);
        if (versao.isDevolvida()) {
            versao.setDevolvidoPor(null);
            versao.setDevolvidoEm(null);
            versao.setMotivoDevolucao(null);
            versao.setEtapaDevolucao(null);
        }
        int etapaAntiga = versao.getUltimaEtapa();
        if (versao.getUltimaEtapa() <= 2) {
            versao.setUltimaEtapa(3);
        }
        versao.setEtapaMaisAvancada(Math.max(versao.getEtapaMaisAvancada(), 3));
        campanhaVersaoRepository.save(versao);

        if (transicaoRepository != null) {
            transicaoRepository.save(new com.bugbusters.backend.model.CampanhaEsteiraTransicao(
                    versao,
                    etapaAntiga,
                    3,
                    estAntigo,
                    com.bugbusters.backend.model.EstadoEsteiraCampanha.EM_SIMULACAO,
                    com.bugbusters.backend.model.TipoAcaoEsteira.APROVACAO_TECNICA,
                    aprovadoPor,
                    request.justificativa() != null ? request.justificativa() : "Programa Python homologado tecnicamente"
            ));
        }

        if (auditoriaService != null) {
            auditoriaService.registrarAcao(
                    aprovadoPor,
                    TipoAcaoAuditoria.APROVACAO_PROGRAMA_PYTHON,
                    TipoEntidadeAuditoria.PROGRAMA_PYTHON,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    String.format("Programa Python v%d homologado e aprovado com checksum %s", programa.getVersaoPrograma(), programa.getChecksumSha256()),
                    String.format("{\"checksum\": \"%s\", \"versaoPrograma\": %d}", programa.getChecksumSha256(), programa.getVersaoPrograma())
            );
        }

        log.info("Programa Python (versão {}) da campanha {} versão {} aprovado por {}.",
                programa.getVersaoPrograma(), campanhaId, versao.getNumero(), aprovadoPor);
        return ProgramaPythonResponse.de(programa);
    }

    @Transactional
    public ProgramaPythonResponse rejeitarPrograma(Long campanhaId, Integer versaoNumero, RejeitarProgramaPythonRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        validarImutabilidadeVersao(versao);

        CampanhaProgramaPython programa = programaPythonRepository
                .findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId())
                .orElseGet(() -> {
                    List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
                    String cod = gerarCodigoConsolidado(versao, regras);
                    CampanhaProgramaPython p = new CampanhaProgramaPython(versao, 1, cod);
                    return programaPythonRepository.save(p);
                });

        String rejeitadoPor = usuarioAtualProvider.obterUsuarioAtual(request.rejeitadoPor());
        if (rejeitadoPor == null || rejeitadoPor.isBlank() || ("SISTEMA".equals(rejeitadoPor) && (request.rejeitadoPor() == null || request.rejeitadoPor().isBlank()))) {
            throw new BusinessException("A identificação do revisor técnico é obrigatória.");
        }

        programa.setStatusAprovacao(StatusAprovacaoPrograma.REJEITADO);
        programa.setRejeitadoPor(rejeitadoPor);
        programa.setRejeitadoEm(OffsetDateTime.now());
        programa.setAprovadoPor(null);
        programa.setAprovadoEm(null);
        programa.setJustificativa(request.justificativa());

        programa = programaPythonRepository.save(programa);

        // Desvincula o programa aprovado da versão e marca devolução técnica na esteira
        int etapaAnterior = versao.getUltimaEtapa();
        com.bugbusters.backend.model.EstadoEsteiraCampanha estAntigo = versao.getEstadoEsteira() != null
                ? versao.getEstadoEsteira() : com.bugbusters.backend.model.EstadoEsteiraCampanha.EM_REVISAO_TECNICA;
        versao.setProgramaPythonAprovado(null);
        versao.setEstadoEsteira(com.bugbusters.backend.model.EstadoEsteiraCampanha.DEVOLVIDA);
        versao.setDevolvidoPor(rejeitadoPor);
        versao.setDevolvidoEm(OffsetDateTime.now());
        versao.setMotivoDevolucao("Rejeição técnica do código Python: " + request.justificativa());
        versao.setEtapaDevolucao(1);
        versao.setUltimaEtapa(1);
        campanhaVersaoRepository.save(versao);

        if (transicaoRepository != null) {
            transicaoRepository.save(new com.bugbusters.backend.model.CampanhaEsteiraTransicao(
                    versao,
                    etapaAnterior,
                    1,
                    estAntigo,
                    com.bugbusters.backend.model.EstadoEsteiraCampanha.DEVOLVIDA,
                    com.bugbusters.backend.model.TipoAcaoEsteira.REJEICAO_TECNICA,
                    rejeitadoPor,
                    request.justificativa()
            ));
        }

        if (auditoriaService != null) {
            auditoriaService.registrarAcao(
                    rejeitadoPor,
                    TipoAcaoAuditoria.REJEICAO_PROGRAMA_PYTHON,
                    TipoEntidadeAuditoria.PROGRAMA_PYTHON,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    "Programa Python rejeitado: " + request.justificativa(),
                    String.format("{\"motivo\": \"%s\"}", request.justificativa())
            );
        }

        log.info("Programa Python (versão {}) da campanha {} versão {} rejeitado por {}. Motivo: {}",
                programa.getVersaoPrograma(), campanhaId, versao.getNumero(), rejeitadoPor, request.justificativa());
        return ProgramaPythonResponse.de(programa);
    }

    @Transactional(readOnly = true)
    public List<ProgramaPythonResponse> listarHistorico(Long campanhaId, Integer versaoNumero) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        return programaPythonRepository.findAllByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId())
                .stream()
                .map(ProgramaPythonResponse::de)
                .toList();
    }

    /**
     * Invalida a aprovação do programa Python caso as regras da campanha sejam modificadas.
     */
    @Transactional
    public void invalidarAprovacaoPorMudancaRegra(CampanhaVersao versao, String motivo) {
        if (versao == null || versao.getId() == null) {
            return;
        }

        if (versao.getProgramaPythonAprovado() != null) {
            versao.setProgramaPythonAprovado(null);
            campanhaVersaoRepository.save(versao);
        }

        programaPythonRepository.findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId())
                .ifPresent(p -> {
                    if (p.isAprovado()) {
                        p.setStatusAprovacao(StatusAprovacaoPrograma.PENDENTE_REVISAO);
                        p.setJustificativa(motivo != null ? motivo : "Aprovação invalidada por alteração nas regras da campanha.");
                        programaPythonRepository.save(p);
                        log.info("Aprovação do programa Python da versão {} revogada devido a alteração nas regras.", versao.getId());
                    }
                });
    }

    private Campanha buscarCampanha(Long campanhaId) {
        return campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + campanhaId + " não encontrada."));
    }

    private CampanhaVersao resolverVersao(Campanha campanha, Integer versaoNumero) {
        if (versaoNumero != null) {
            return campanhaVersaoRepository.findByCampanhaIdAndNumero(campanha.getId(), versaoNumero)
                    .orElseThrow(() -> new ResourceNotFoundException("Versão " + versaoNumero + " da campanha " + campanha.getId() + " não encontrada."));
        }
        if (campanha.getVersaoProposta() != null) {
            return campanha.getVersaoProposta();
        }
        if (campanha.getVersaoVigente() != null) {
            return campanha.getVersaoVigente();
        }
        // Fallback: busca versão mais recente
        return campanhaVersaoRepository.findFirstByCampanhaIdOrderByNumeroDesc(campanha.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma versão encontrada para a campanha " + campanha.getId()));
    }

    private void validarImutabilidadeVersao(CampanhaVersao versao) {
        if (StatusVersaoCampanha.PUBLICADA.equals(versao.getStatus())) {
            throw new BusinessException("Versões publicadas são imutáveis e não permitem edição ou alteração do programa Python.");
        }
        if (StatusVersaoCampanha.CANCELADA.equals(versao.getStatus())) {
            throw new BusinessException("Versões canceladas não permitem alteração de programa Python.");
        }
    }

    /**
     * Sintetiza deterministicamente o código Python completo consolidado para todas as regras da versão.
     */
    public String gerarCodigoConsolidado(CampanhaVersao versao, List<Regra> regras) {
        StringBuilder py = new StringBuilder();
        py.append("# ===================================================================\n");
        py.append("# Programa Executável da Campanha: ").append(versao.getTitulo() != null ? versao.getTitulo() : "Campanha").append("\n");
        py.append("# Período de Vigência: ").append(versao.getDataInicio()).append(" até ").append(versao.getDataFim()).append("\n");
        py.append("# Versão da Campanha: ").append(versao.getNumero()).append("\n");
        py.append("# ===================================================================\n");
        py.append("from decimal import Decimal, ROUND_HALF_UP\n\n");
        py.append("def calcular_comissao(venda: dict) -> float | None:\n");
        py.append("    \"\"\"Calcula a comissão para um registro de venda segundo as regras da campanha.\"\"\"\n");
        py.append("    data_raw = venda.get('data_venda') if venda.get('data_venda') is not None else venda.get('dataVenda', '')\n");
        py.append("    data_venda = str(data_raw)[:10]\n");
        py.append("    if not venda.get('ignorar_vigencia') and not ('").append(versao.getDataInicio()).append("' <= data_venda <= '").append(versao.getDataFim()).append("'):\n");
        py.append("        return None\n\n");
        py.append("    valor_raw = venda.get('valor_venda')\n");
        py.append("    if valor_raw is None:\n");
        py.append("        valor_raw = venda.get('valor')\n");
        py.append("    if valor_raw is None:\n");
        py.append("        return None\n");
        py.append("    valor = Decimal(str(valor_raw))\n");
        py.append("    if valor <= 0:\n");
        py.append("        return None\n\n");
        py.append("    centavo = Decimal('0.01')\n");
        py.append("    taxa_base_raw = venda.get('taxa_base')\n");
        py.append("    taxa_base_venda = Decimal(str(taxa_base_raw)) if taxa_base_raw is not None else None\n\n");

        if (regras == null || regras.isEmpty()) {
            py.append("    # Nenhuma regra ativa configurada nesta campanha\n");
            py.append("    return None\n");
            return py.toString();
        }

        // Ordena por especificidade decrescente para priorizar a regra mais restritiva
        List<Regra> ordenadas = new ArrayList<>(regras);
        ordenadas.sort((r1, r2) -> Integer.compare(calcularEspecificidade(r2), calcularEspecificidade(r1)));

        int idx = 1;
        for (Regra r : ordenadas) {
            py.append("    # Regra ").append(idx++).append(": ").append(r.getBlocoId() != null ? r.getBlocoId() : "Bloco " + r.getId());
            String desc = r.getNome() != null && !r.getNome().isBlank() ? r.getNome() : r.getExplicacao();
            if (desc != null && !desc.isBlank()) {
                py.append(" - ").append(desc.replace('\r', ' ').replace('\n', ' '));
            }
            py.append("\n");

            List<String> condicoes = new ArrayList<>();
            if (r.getCanal() != null && !r.getCanal().isBlank() && !"PADRAO".equalsIgnoreCase(r.getCanal())) {
                condicoes.add("venda.get('canal') == " + toJsonLiteral(r.getCanal()));
            }
            if (r.getCodMarca() != null) {
                condicoes.add("venda.get('cod_marca') == " + r.getCodMarca());
            }
            if (r.getCodLoja() != null) {
                condicoes.add("venda.get('cod_loja') == " + r.getCodLoja());
            }
            if (r.getCodCargo() != null) {
                condicoes.add("venda.get('cod_cargo') == " + r.getCodCargo());
            }
            if (r.getMatricula() != null && !r.getMatricula().isBlank()) {
                condicoes.add("str(venda.get('matricula', '')).strip() == " + toJsonLiteral(r.getMatricula().trim()));
            }

            if (r.getValorMinimo() != null) {
                condicoes.add("valor " + (Boolean.TRUE.equals(r.getMinInclusivo()) ? ">=" : ">") + " Decimal('" + r.getValorMinimo().toPlainString() + "')");
            }
            if (r.getValorMaximo() != null) {
                condicoes.add("valor " + (Boolean.TRUE.equals(r.getMaxInclusivo()) ? "<=" : "<") + " Decimal('" + r.getValorMaximo().toPlainString() + "')");
            }

            String ifClause = condicoes.isEmpty() ? "True" : String.join(" and ", condicoes);
            py.append("    if ").append(ifClause).append(":\n");

            com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase tipoOp =
                    r.getTipoOperacao() != null ? r.getTipoOperacao() : com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase.DEFINIR_TAXA;
            BigDecimal taxaEfetiva = r.getTaxa() != null ? r.getTaxa() : BigDecimal.ZERO;
            BigDecimal ajuste = r.getValorAjuste() != null ? r.getValorAjuste() : taxaEfetiva;

            py.append("        # Operação: ").append(tipoOp.name()).append("\n");
            switch (tipoOp) {
                case ACRESCIMO_PONTOS -> {
                    py.append("        ajuste = Decimal('").append(ajuste.toPlainString()).append("')\n");
                    py.append("        taxa = (taxa_base_venda + ajuste) if taxa_base_venda is not None else Decimal('").append(taxaEfetiva.toPlainString()).append("')\n");
                }
                case DESCONTO_PONTOS -> {
                    py.append("        ajuste = Decimal('").append(ajuste.toPlainString()).append("')\n");
                    py.append("        taxa = max(Decimal('0'), taxa_base_venda - ajuste) if taxa_base_venda is not None else Decimal('").append(taxaEfetiva.toPlainString()).append("')\n");
                }
                case MULTIPLICADOR_BASE -> {
                    py.append("        fator = Decimal('").append(ajuste.toPlainString()).append("')\n");
                    py.append("        taxa = (taxa_base_venda * fator) if taxa_base_venda is not None else Decimal('").append(taxaEfetiva.toPlainString()).append("')\n");
                }
                case DIVISOR_BASE -> {
                    py.append("        divisor = Decimal('").append(ajuste.toPlainString()).append("')\n");
                    py.append("        taxa = (taxa_base_venda / divisor) if (taxa_base_venda is not None and divisor != 0) else Decimal('").append(taxaEfetiva.toPlainString()).append("')\n");
                }
                default -> {
                    py.append("        taxa = Decimal('").append(taxaEfetiva.toPlainString()).append("')\n");
                }
            }
            py.append("        comissao = (valor * taxa).quantize(centavo, rounding=ROUND_HALF_UP)\n");

            if (r.getBonusPercentual() != null && r.getBonusPercentual().compareTo(BigDecimal.ZERO) > 0) {
                py.append("        bonus_perc = Decimal('").append(r.getBonusPercentual().toPlainString()).append("')\n");
                py.append("        comissao += (valor * bonus_perc).quantize(centavo, rounding=ROUND_HALF_UP)\n");
            }
            if (r.getBonusValorFixo() != null && r.getBonusValorFixo().compareTo(BigDecimal.ZERO) > 0) {
                py.append("        bonus_fixo = Decimal('").append(r.getBonusValorFixo().toPlainString()).append("')\n");
                py.append("        comissao += bonus_fixo.quantize(centavo, rounding=ROUND_HALF_UP)\n");
            }
            py.append("        return float(comissao)\n\n");
        }

        py.append("    return None\n");
        return py.toString();
    }

    private int calcularEspecificidade(Regra r) {
        int score = 0;
        if (r.getMatricula() != null && !r.getMatricula().isBlank()) score += 16;
        if (r.getCodLoja() != null) score += 8;
        if (r.getCodCargo() != null) score += 4;
        if (r.getCanal() != null && !"PADRAO".equalsIgnoreCase(r.getCanal())) score += 2;
        if (r.getCodMarca() != null) score += 1;
        if (r.getValorMinimo() != null || r.getValorMaximo() != null) score += 1;
        return score;
    }

    private String toJsonLiteral(Object valor) {
        if (valor == null) {
            return "None";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(valor);
        } catch (Exception e) {
            String escaped = String.valueOf(valor)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
            return "\"" + escaped + "\"";
        }
    }

    /**
     * Garante antes de executar que o código-fonte do programa aprovado não sofreu adulteração
     * comparando seu SHA-256 atual com o hash homologado na revisão técnica.
     */
    public static void validarIntegridadePrograma(CampanhaProgramaPython programa) {
        if (programa == null || programa.getCodigoFonte() == null || programa.getCodigoFonte().isBlank()) {
            throw new BusinessException("O código-fonte do programa Python aprovado está vazio ou ausente.");
        }
        String checksumAtual = CampanhaProgramaPython.calcularChecksum(programa.getCodigoFonte());
        if (programa.getChecksumSha256() == null
                || !checksumAtual.equalsIgnoreCase(programa.getChecksumSha256().trim())) {
            throw new BusinessException(
                    "Divergência de integridade SHA-256 detectada no programa Python v"
                            + programa.getVersaoPrograma() + ". Reapprove o código antes de executar."
            );
        }
    }

    /**
     * Serializa uma venda para o dicionário consumido por `calcular_comissao(venda, contexto)`.
     */
    public static Map<String, Object> montarVendaParaPython(
            Sale sale,
            BigDecimal valorVenda,
            BigDecimal taxaBaseContratual,
            boolean ignorarVigencia
    ) {
        Map<String, Object> venda = new LinkedHashMap<>();
        venda.put("id_venda", sale != null && sale.getId() != null ? sale.getId().toString() : null);
        String dataStr = (sale != null && sale.getSaleDate() != null) ? sale.getSaleDate().toString() : "";
        venda.put("data_venda", dataStr);
        venda.put("dataVenda", dataStr);
        double valorNum = valorVenda != null ? valorVenda.doubleValue() : 0.0;
        venda.put("valor_venda", valorNum);
        venda.put("valor", valorNum);
        String canal = (sale != null && sale.getSaleChannel() != null && !sale.getSaleChannel().isBlank())
                ? sale.getSaleChannel()
                : "PADRAO";
        venda.put("canal", canal);
        venda.put("cod_marca", (sale != null && sale.getBrand() != null) ? sale.getBrand().getCode() : null);
        venda.put("cod_loja", (sale != null && sale.getStore() != null) ? sale.getStore().getCode() : null);
        Registration reg = sale != null ? sale.getRegistration() : null;
        venda.put("cod_cargo", (reg != null && reg.getPosition() != null) ? reg.getPosition().getCode() : null);
        venda.put("matricula", reg != null ? reg.getRegistration() : null);
        venda.put("taxa_base", taxaBaseContratual != null ? taxaBaseContratual.doubleValue() : null);
        venda.put("ignorar_vigencia", ignorarVigencia);
        return venda;
    }
}


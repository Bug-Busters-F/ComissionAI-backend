package com.bugbusters.backend.service;

import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.auth.UsuarioRepository;
import com.bugbusters.backend.dto.auditoria.AuditoriaEventoContexto;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.ExecucaoCalculoRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class ExecucaoCalculoWorker {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExecucaoCalculoWorker.class);
    private final ExecucaoCalculoRepository repository;
    private final ReferenciasExecucaoCalculoService referencias;
    private final CalculoService calculo;
    private final TravaCompetencia trava;
    private final TransactionTemplate isolated;
    private final ExecucaoCalculoProgressoService progresso;
    private final CampanhaRepository campanhaRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    @Autowired
    public ExecucaoCalculoWorker(
            ExecucaoCalculoRepository repository,
            ReferenciasExecucaoCalculoService referencias,
            CalculoService calculo,
            TravaCompetencia trava,
            PlatformTransactionManager manager,
            ExecucaoCalculoProgressoService progresso,
            CampanhaRepository campanhaRepository) {
        this.repository = repository;
        this.referencias = referencias;
        this.calculo = calculo;
        this.trava = trava;
        this.progresso = progresso;
        this.campanhaRepository = campanhaRepository;
        this.objectMapper = new ObjectMapper();
        this.isolated = new TransactionTemplate(manager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ExecucaoCalculoWorker(
            ExecucaoCalculoRepository repository,
            ReferenciasExecucaoCalculoService referencias,
            CalculoService calculo,
            TravaCompetencia trava,
            PlatformTransactionManager manager,
            ExecucaoCalculoProgressoService progresso) {
        this(repository, referencias, calculo, trava, manager, progresso, null);
    }

    public void processar(UUID id, UUID tentativa) {
        SecurityContext contextoAnterior = SecurityContextHolder.getContext();
        boolean contextoAlterado = false;
        try {
            String[] solicitanteInfo = isolated.execute(tx -> {
                String competencia = repository.competencia(id).orElseThrow();
                trava.adquirirTrava(competencia);
                ExecucaoCalculo e = repository.bloquear(id).orElseThrow();
                if (!Objects.equals(e.getTentativa(), tentativa) || e.getStatus() != StatusExecucaoCalculo.SOLICITADA) return null;
                referencias.validarDisponivel(competencia);
                e.setStatus(StatusExecucaoCalculo.PROCESSANDO);
                e.setIniciadoEm(OffsetDateTime.now());
                repository.save(e);
                return new String[] { e.getSolicitadoPor(), e.getPerfilSolicitante() };
            });
            if (solicitanteInfo == null) return;

            contextoAlterado = aplicarContextoSolicitante(solicitanteInfo[0]);

            isolated.executeWithoutResult(tx -> {
                String competencia = repository.competencia(id).orElseThrow();
                trava.adquirirTrava(competencia);
                ExecucaoCalculo e = repository.findById(id).orElseThrow();
                if (!Objects.equals(e.getTentativa(), tentativa) || e.getStatus() != StatusExecucaoCalculo.PROCESSANDO) return;
                var atuais = referencias.validar(competencia);
                if (!e.getReferenciaDados().equals(atuais.dados()) || !e.getReferenciaRegras().equals(atuais.regras()))
                    throw new BusinessException("Dados ou regras foram alterados após a solicitação. Retome a execução para utilizar as referências atuais.");
                int intervalo = Math.max(1, e.getTotalItens() / 100);
                int[] ultimo = {0};
                var resultado = calculo.calcularPorCompetenciaComProgresso(competencia, e.isRecalcular(), itens -> {
                    if (itens > ultimo[0] && (itens - ultimo[0] >= intervalo || itens >= e.getTotalItens())) {
                        progresso.registrar(id, tentativa, itens);
                        ultimo[0] = itens;
                    }
                });
                e.setItensProcessados(resultado.totalVendasProcessadas());
                e.setStatus(StatusExecucaoCalculo.CONCLUIDA);
                e.setConcluidoEm(OffsetDateTime.now());
                e.setResultado("vendas=" + resultado.totalVendasProcessadas() + ",sucessos=" + resultado.totalCalculados() + ",impedimentos=" + resultado.totalImpedimentos());

                // Governança orçamentária no histórico das execuções (BACK-8)
                BigDecimal orcamentoTotal = BigDecimal.ZERO;
                int campanhasComOrcamento = 0;
                List<Map<String, Object>> campanhasResumo = new ArrayList<>();

                if (campanhaRepository != null) {
                    try {
                        String comp7 = competencia.trim().length() >= 7 ? competencia.trim().substring(0, 7) : competencia.trim();
                        YearMonth ym = YearMonth.parse(comp7);
                        LocalDate inicioComp = ym.atDay(1);
                        LocalDate fimComp = ym.atEndOfMonth();

                        List<Campanha> ativas = campanhaRepository.findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha.ATIVA);
                        for (Campanha c : ativas) {
                            CampanhaVersao versaoVigente = c.getVersaoVigente();
                            LocalDate inicio = (versaoVigente != null && versaoVigente.getDataInicio() != null) ? versaoVigente.getDataInicio() : c.getDataInicio();
                            LocalDate fim = (versaoVigente != null && versaoVigente.getDataFim() != null) ? versaoVigente.getDataFim() : c.getDataFim();
                            if (inicio != null && fim != null && !inicio.isAfter(fimComp) && !fim.isBefore(inicioComp)) {
                                BigDecimal orc = (versaoVigente != null && versaoVigente.getOrcamento() != null) ? versaoVigente.getOrcamento() : c.getOrcamento();
                                if (orc != null && orc.compareTo(BigDecimal.ZERO) > 0) {
                                    orcamentoTotal = orcamentoTotal.add(orc);
                                    campanhasComOrcamento++;
                                }
                                Map<String, Object> cr = new LinkedHashMap<>();
                                cr.put("campanhaId", c.getId());
                                String tit = (versaoVigente != null && versaoVigente.getTitulo() != null) ? versaoVigente.getTitulo() : c.getTitulo();
                                cr.put("titulo", tit);
                                cr.put("orcamento", orc);
                                campanhasResumo.add(cr);
                            }
                        }
                    } catch (Exception ex) {
                        LOG.warn("Erro ao consolidar orçamento das campanhas para execução {}: {}", id, ex.getMessage());
                    }
                }

                BigDecimal comissaoApurada = resultado.valorTotalComissao() != null ? resultado.valorTotalComissao() : BigDecimal.ZERO;
                StatusEnquadramentoOrcamento statusEnquadramento;
                if (campanhasComOrcamento > 0 && orcamentoTotal.compareTo(BigDecimal.ZERO) > 0) {
                    statusEnquadramento = comissaoApurada.compareTo(orcamentoTotal) <= 0
                            ? StatusEnquadramentoOrcamento.ENQUADRADO
                            : StatusEnquadramentoOrcamento.ACIMA_DO_TETO;
                } else {
                    statusEnquadramento = StatusEnquadramentoOrcamento.NAO_APLICAVEL;
                }

                e.setOrcamentoTotalCampanhas(campanhasComOrcamento > 0 ? orcamentoTotal : null);
                e.setComissaoTotalApurada(comissaoApurada);
                e.setStatusEnquadramentoExecucao(statusEnquadramento);

                Map<String, Object> jsonMap = new LinkedHashMap<>();
                jsonMap.put("orcamentoTotalCampanhas", campanhasComOrcamento > 0 ? orcamentoTotal : null);
                jsonMap.put("comissaoTotalApurada", comissaoApurada);
                jsonMap.put("statusEnquadramento", statusEnquadramento.name());
                jsonMap.put("totalCampanhasNoPeriodo", campanhasResumo.size());
                jsonMap.put("campanhas", campanhasResumo);

                try {
                    e.setResumoOrcamentoJson(objectMapper.writeValueAsString(jsonMap));
                } catch (Exception ignored) {
                    e.setResumoOrcamentoJson("{}");
                }

                repository.save(e);
                registrarAuditoriaConclusao(e, resultado);
            });
        } catch (Exception error) {
            LOG.warn("Execução {} tentativa {} falhou: {}", id, tentativa, error.toString());
            falhar(id, tentativa, error.getMessage() != null ? error.getMessage() : "Falha no processamento do cálculo.");
        } finally {
            if (contextoAlterado) {
                SecurityContextHolder.setContext(contextoAnterior);
            }
        }
    }

    public void falhar(UUID id, UUID tentativa, String mensagem) {
        isolated.executeWithoutResult(tx -> repository.falhar(id, tentativa, mensagem, OffsetDateTime.now()));
        if (auditoriaService != null) {
            try {
                ExecucaoCalculo e = repository.findById(id).orElse(null);
                auditoriaService.registrarEventoIsolado(AuditoriaEventoContexto.builder()
                        .usuario(e != null ? e.getSolicitadoPor() : "SISTEMA")
                        .perfilUsuario(e != null ? e.getPerfilSolicitante() : null)
                        .tipoAcao(TipoAcaoAuditoria.CALCULO_COMISSAO)
                        .tipoEntidade(TipoEntidadeAuditoria.EXECUCAO_CALCULO)
                        .entidadeId(id.toString())
                        .execucaoCalculoId(id)
                        .competencia(e != null ? e.getCompetencia() : null)
                        .descricao("Execução assíncrona de cálculo falhou"
                                + (e != null ? " na competência " + e.getCompetencia() : "") + ".")
                        .sucesso(false)
                        .detalheErro(mensagem));
            } catch (Exception ex) {
                LOG.warn("Falha ao registrar auditoria de erro para execução {}: {}", id, ex.getMessage());
            }
        }
    }

    private boolean aplicarContextoSolicitante(String solicitadoPor) {
        if (usuarioRepository == null || solicitadoPor == null || solicitadoPor.isBlank()
                || "SISTEMA".equalsIgnoreCase(solicitadoPor.trim())) {
            return false;
        }
        try {
            Usuario usuario = usuarioRepository.findByEmailIgnoreCase(solicitadoPor.trim()).orElse(null);
            if (usuario == null || usuario.getPapel() == null) {
                return false;
            }
            SecurityContext novoContexto = SecurityContextHolder.createEmptyContext();
            novoContexto.setAuthentication(new UsernamePasswordAuthenticationToken(
                    usuario,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPapel().name()))
            ));
            SecurityContextHolder.setContext(novoContexto);
            return true;
        } catch (Exception ex) {
            LOG.warn("Não foi possível restaurar contexto de segurança para '{}': {}", solicitadoPor, ex.getMessage());
            return false;
        }
    }

    private void registrarAuditoriaConclusao(ExecucaoCalculo e, com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO resultado) {
        if (auditoriaService == null || e == null) {
            return;
        }
        try {
            auditoriaService.registrarEvento(AuditoriaEventoContexto.builder()
                    .usuario(e.getSolicitadoPor())
                    .perfilUsuario(e.getPerfilSolicitante())
                    .tipoAcao(TipoAcaoAuditoria.CALCULO_COMISSAO)
                    .tipoEntidade(TipoEntidadeAuditoria.EXECUCAO_CALCULO)
                    .entidadeId(e.getId().toString())
                    .execucaoCalculoId(e.getId())
                    .competencia(e.getCompetencia())
                    .descricao(String.format("Execução assíncrona de cálculo concluída para %s: %d calculadas, %d impedimentos.",
                            e.getCompetencia(), resultado.totalCalculados(), resultado.totalImpedimentos()))
                    .dadosNovos(e.getResumoOrcamentoJson())
                    .sucesso(true));
        } catch (Exception ex) {
            LOG.warn("Falha ao registrar auditoria de conclusão para execução {}: {}", e.getId(), ex.getMessage());
        }
    }
}

package com.bugbusters.backend.service;

import com.bugbusters.backend.auth.PapelUsuario;
import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.dto.indicador.IndicadoresResponse;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * Consolida o estado atual do fluxo. Nenhum contador e' armazenado: cada
 * leitura
 * reflete imediatamente inclusoes, revisoes, efetivacoes e fechamentos.
 */
@Service
public class IndicadoresService {
        private final CampanhaRepository campanhas;
        private final CampanhaVersaoRepository versoes;
        private final LotePreparacaoRepository lotes;
        private final ExecucaoCalculoRepository execucoes;
        private final FechamentoCompetenciaRepository fechamentos;

        public IndicadoresService(CampanhaRepository campanhas, CampanhaVersaoRepository versoes,
                        LotePreparacaoRepository lotes, ExecucaoCalculoRepository execucoes,
                        FechamentoCompetenciaRepository fechamentos) {
                this.campanhas = campanhas;
                this.versoes = versoes;
                this.lotes = lotes;
                this.execucoes = execucoes;
                this.fechamentos = fechamentos;
        }

        @Transactional(readOnly = true)
        public IndicadoresResponse consultar() {
                PapelUsuario perfil = perfilAtual();
                List<Campanha> todasCampanhas = campanhas.findAllByRemovidoEmIsNullOrderByCriadoEmDesc();
                List<CampanhaVersao> todasVersoes = versoes.findAll();
                List<LotePreparacao> todosLotes = lotes.findAll();
                List<ExecucaoCalculo> todasExecucoes = execucoes.findAll();
                List<FechamentoCompetencia> todosFechamentos = fechamentos.findAll();

                Set<String> competenciasConhecidas = Stream.concat(
                                Stream.concat(todosLotes.stream().map(LotePreparacao::getCompetencia),
                                                todasExecucoes.stream().map(ExecucaoCalculo::getCompetencia)),
                                todosFechamentos.stream().map(FechamentoCompetencia::getCompetencia))
                                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());

                Map<String, Long> competencia = new LinkedHashMap<>();
                competencia.put("total", (long) competenciasConhecidas.size());
                competencia.put("abertas", competenciasConhecidas.stream().filter(c -> todosFechamentos.stream()
                                .noneMatch(f -> c.equals(f.getCompetencia())
                                                && f.getStatus() == StatusFechamento.CONCLUIDO))
                                .count());
                competencia.put("processando",
                                todosFechamentos.stream().filter(f -> f.getStatus() == StatusFechamento.PROCESSANDO)
                                                .map(FechamentoCompetencia::getCompetencia).distinct().count());
                competencia.put("fechadas",
                                todosFechamentos.stream().filter(f -> f.getStatus() == StatusFechamento.CONCLUIDO)
                                                .map(FechamentoCompetencia::getCompetencia).distinct().count());

                Map<String, Long> campanha = new LinkedHashMap<>();
                campanha.put("total", (long) todasCampanhas.size());
                campanha.put("rascunhos", contarCampanhas(todasCampanhas, EstadoCampanha.DRAFT));
                campanha.put("ativas", contarCampanhas(todasCampanhas, EstadoCampanha.ATIVA));
                campanha.put("aguardandoGerente", todasCampanhas.stream().filter(Campanha::isEncaminhadaGerente)
                                .filter(c -> c.getEstado() != EstadoCampanha.ATIVA
                                                && c.getEstado() != EstadoCampanha.CONCLUIDA)
                                .count());

                List<Campanha> semOrcamento = todasCampanhas.stream().filter(c -> c.getEstado() == EstadoCampanha.DRAFT)
                                .filter(c -> !c.orcamentoConfirmadoParaRevisao()).toList();
                List<CampanhaVersao> codigoPendente = todasVersoes.stream()
                                .filter(v -> v.getStatus() == StatusVersaoCampanha.PROPOSTA)
                                .filter(v -> v.getStatusAprovacaoCodigo() != StatusAprovacaoCodigo.APROVADO).toList();
                List<LotePreparacao> lotesComImpedimento = todosLotes.stream()
                                .filter(l -> "EM_PREPARACAO".equals(l.getStatus()))
                                .filter(l -> l.getLinhasImpeditivas() > 0).toList();

                Map<String, Long> pendencia = new LinkedHashMap<>();
                pendencia.put("orcamentoNaoConfirmado", (long) semOrcamento.size());
                pendencia.put("revisaoCodigo", (long) codigoPendente.size());
                pendencia.put("lotesComImpedimento", (long) lotesComImpedimento.size());
                pendencia.put("total", pendencia.values().stream().mapToLong(Long::longValue).sum());

                Map<String, Long> alerta = new LinkedHashMap<>();
                alerta.put("lotesComLinhasImpeditivas", (long) lotesComImpedimento.size());
                alerta.put("execucoesFalhas", todasExecucoes.stream()
                                .filter(e -> e.getStatus() == StatusExecucaoCalculo.FALHOU).count());
                alerta.put("fechamentosFalhos", todosFechamentos.stream()
                                .filter(f -> f.getStatus() == StatusFechamento.FALHOU).count());
                alerta.put("fechamentosEmProcessamento", todosFechamentos.stream()
                                .filter(f -> f.getStatus() == StatusFechamento.PROCESSANDO).count());

                return new IndicadoresResponse(OffsetDateTime.now(), perfil.name(), competencia, campanha, pendencia,
                                alerta,
                                acoesPermitidas(perfil, semOrcamento, codigoPendente, todasCampanhas,
                                                lotesComImpedimento,
                                                todosFechamentos, todasExecucoes));
        }

        private List<IndicadoresResponse.ItemAguardandoAcao> acoesPermitidas(PapelUsuario perfil,
                        List<Campanha> semOrcamento, List<CampanhaVersao> codigoPendente, List<Campanha> todasCampanhas,
                        List<LotePreparacao> lotesComImpedimento, List<FechamentoCompetencia> fechamentos,
                        List<ExecucaoCalculo> execucoes) {
                List<IndicadoresResponse.ItemAguardandoAcao> itens = new ArrayList<>();
                if (perfil == PapelUsuario.ADMINISTRADOR || perfil == PapelUsuario.ANALISTA_NEGOCIOS) {
                        semOrcamento.forEach(c -> itens.add(
                                        acao("ORCAMENTO_PENDENTE", "Confirmar orçamento da campanha", c.getId(), null,
                                                        "/api/v1/campanhas/" + c.getId() + "/orcamento", "PATCH")));
                        lotesComImpedimento.forEach(l -> itens
                                        .add(acao("LOTE_COM_IMPEDIMENTO", "Corrigir linhas impeditivas do lote",
                                                        l.getCampanha().getId(), l.getCompetencia(),
                                                        "/api/v1/campanhas/" + l.getCampanha().getId()
                                                                        + "/preparacao-bases/" + l.getId(),
                                                        "GET")));
                }
                if (perfil == PapelUsuario.ADMINISTRADOR || perfil == PapelUsuario.ANALISTA_CODIGO) {
                        codigoPendente.forEach(v -> itens.add(acao("REVISAO_CODIGO_PENDENTE",
                                        "Revisar artefatos de código da versão", v.getCampanha().getId(), null,
                                        "/api/v1/campanhas/" + v.getCampanha().getId() + "/aprovacao-codigo", "POST")));
                }
                if (perfil == PapelUsuario.ADMINISTRADOR || perfil == PapelUsuario.GERENTE_VENDAS) {
                        todasCampanhas.stream().filter(Campanha::isEncaminhadaGerente)
                                        .filter(c -> c.getEstado() != EstadoCampanha.ATIVA
                                                        && c.getEstado() != EstadoCampanha.CONCLUIDA)
                                        .forEach(c -> itens.add(acao("APROVACAO_GERENCIAL_PENDENTE",
                                                        "Publicar ou devolver campanha encaminhada", c.getId(), null,
                                                        "/api/v1/campanhas/" + c.getId() + "/estado", "PATCH")));
                        fechamentos.stream().filter(f -> f.getStatus() == StatusFechamento.FALHOU)
                                        .forEach(f -> itens.add(acao("FECHAMENTO_FALHOU",
                                                        "Consultar e refazer fechamento", null, f.getCompetencia(),
                                                        "/api/v1/comissoes/fechamento/" + f.getCompetencia()
                                                                        + "/status",
                                                        "GET")));
                        execucoes.stream().filter(e -> e.getStatus() == StatusExecucaoCalculo.FALHOU)
                                        .forEach(e -> itens.add(acao("CALCULO_FALHOU",
                                                        "Consultar execução de cálculo falha", null, e.getCompetencia(),
                                                        "/api/v1/execucoes/" + e.getId(), "GET")));
                }
                return itens;
        }

        private IndicadoresResponse.ItemAguardandoAcao acao(String tipo, String descricao, Long campanhaId,
                        String competencia, String href, String metodo) {
                return new IndicadoresResponse.ItemAguardandoAcao(tipo, descricao, campanhaId, competencia, href,
                                metodo);
        }

        private long contarCampanhas(List<Campanha> campanhas, EstadoCampanha estado) {
                return campanhas.stream().filter(c -> c.getEstado() == estado).count();
        }

        private PapelUsuario perfilAtual() {
                Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof Usuario usuario)
                        return usuario.getPapel();
                throw new org.springframework.security.access.AccessDeniedException(
                                "Usuário autenticado não encontrado.");
        }
}

package com.bugbusters.backend.service;

import com.bugbusters.backend.auth.PapelUsuario;
import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.dto.indicador.IndicadoresResponse;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.SaleRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Consolida o estado atual do fluxo para os cartões e filas do painel. */
@Service
public class IndicadoresService {
    private final CampanhaRepository campanhas;
    private final CampanhaVersaoRepository versoes;
    private final LotePreparacaoRepository lotes;
    private final ExecucaoCalculoRepository execucoes;
    private final FechamentoCompetenciaRepository fechamentos;
    private final SaleRepository vendas;
    private final BaseComissRepository basesComiss;

    public IndicadoresService(CampanhaRepository campanhas, CampanhaVersaoRepository versoes,
            LotePreparacaoRepository lotes, ExecucaoCalculoRepository execucoes,
            FechamentoCompetenciaRepository fechamentos, SaleRepository vendas,
            BaseComissRepository basesComiss) {
        this.campanhas = campanhas;
        this.versoes = versoes;
        this.lotes = lotes;
        this.execucoes = execucoes;
        this.fechamentos = fechamentos;
        this.vendas = vendas;
        this.basesComiss = basesComiss;
    }

    @Transactional(readOnly = true)
    public IndicadoresResponse consultar() {
        PapelUsuario perfil = perfilAtual();
        List<Campanha> todasCampanhas = campanhas.findAllByRemovidoEmIsNullOrderByCriadoEmDesc();
        List<Campanha> campanhasElegiveis = todasCampanhas.stream().filter(this::campanhaElegivel).toList();
        List<CampanhaVersao> todasVersoes = versoes.findAll().stream()
                .filter(v -> campanhaElegivel(v.getCampanha())).toList();
        List<LotePreparacao> todosLotes = lotes.findAll().stream()
                .filter(l -> campanhaElegivel(l.getCampanha())).toList();
        List<ExecucaoCalculo> todasExecucoes = execucoes.findAll();
        List<FechamentoCompetencia> todosFechamentos = fechamentos.findAll();

        Set<String> competenciasConhecidas = new HashSet<>();
        adicionarCompetencias(competenciasConhecidas, todosLotes.stream().map(LotePreparacao::getCompetencia));
        adicionarCompetencias(competenciasConhecidas, todasExecucoes.stream().map(ExecucaoCalculo::getCompetencia));
        adicionarCompetencias(competenciasConhecidas, todosFechamentos.stream().map(FechamentoCompetencia::getCompetencia));
        adicionarCompetencias(competenciasConhecidas, competenciasDasVendas());
        adicionarCompetencias(competenciasConhecidas, competenciasDasBasesComissao());

        Set<String> competenciasFechadas = todosFechamentos.stream()
                .filter(f -> f.getStatus() == StatusFechamento.CONCLUIDO)
                .map(FechamentoCompetencia::getCompetencia).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, FechamentoCompetencia> ultimoFechamento = ultimosPorCompetencia(
                todosFechamentos, FechamentoCompetencia::getCompetencia, FechamentoCompetencia::getIniciadoEm);
        Map<String, ExecucaoCalculo> ultimaExecucao = ultimosPorCompetencia(
                todasExecucoes, ExecucaoCalculo::getCompetencia, ExecucaoCalculo::getSolicitadoEm);
        Set<String> competenciasProcessando = ultimoFechamento.values().stream()
                .filter(f -> f.getStatus() == StatusFechamento.PROCESSANDO)
                .map(FechamentoCompetencia::getCompetencia).filter(c -> !competenciasFechadas.contains(c))
                .collect(Collectors.toSet());
        List<FechamentoCompetencia> fechamentosFalhos = ultimoFechamento.values().stream()
                .filter(f -> f.getStatus() == StatusFechamento.FALHOU)
                .filter(f -> !competenciasFechadas.contains(f.getCompetencia())).toList();
        List<ExecucaoCalculo> execucoesFalhas = ultimaExecucao.values().stream()
                .filter(e -> e.getStatus() == StatusExecucaoCalculo.FALHOU)
                .filter(e -> !competenciasFechadas.contains(e.getCompetencia())).toList();

        Map<String, Long> competencia = new LinkedHashMap<>();
        competencia.put("total", (long) competenciasConhecidas.size());
        competencia.put("abertas", competenciasConhecidas.stream()
                .filter(c -> !competenciasFechadas.contains(c) && !competenciasProcessando.contains(c)).count());
        competencia.put("processando", (long) competenciasProcessando.size());
        competencia.put("fechadas", (long) competenciasFechadas.size());

        List<Campanha> revisoesAbertas = campanhasElegiveis.stream().filter(this::temRevisaoAberta).toList();
        List<Campanha> aguardandoGerente = revisoesAbertas.stream().filter(this::aguardandoGerente).toList();
        List<Campanha> semOrcamento = revisoesAbertas.stream()
                .filter(c -> !c.orcamentoConfirmadoParaRevisao()).toList();
        List<CampanhaVersao> codigoPendente = todasVersoes.stream()
                .filter(v -> v.getStatus() == StatusVersaoCampanha.PROPOSTA)
                .filter(v -> v.getStatusAprovacaoCodigo() != StatusAprovacaoCodigo.APROVADO).toList();
        List<LotePreparacao> lotesComImpedimento = todosLotes.stream()
                .filter(l -> "EM_PREPARACAO".equals(l.getStatus()))
                .filter(l -> l.getLinhasImpeditivas() > 0).toList();

        Map<String, Long> campanha = new LinkedHashMap<>();
        campanha.put("total", (long) todasCampanhas.size());
        campanha.put("rascunhos", contarCampanhas(todasCampanhas, EstadoCampanha.DRAFT));
        campanha.put("ativas", contarCampanhas(todasCampanhas, EstadoCampanha.ATIVA));
        campanha.put("aguardandoGerente", (long) aguardandoGerente.size());

        Map<String, Long> pendencia = new LinkedHashMap<>();
        pendencia.put("orcamentoNaoConfirmado", (long) semOrcamento.size());
        pendencia.put("revisaoCodigo", (long) codigoPendente.size());
        pendencia.put("lotesComImpedimento", (long) lotesComImpedimento.size());
        pendencia.put("total", pendencia.values().stream().mapToLong(Long::longValue).sum());

        Map<String, Long> alerta = new LinkedHashMap<>();
        alerta.put("lotesComLinhasImpeditivas", (long) lotesComImpedimento.size());
        alerta.put("execucoesFalhas", (long) execucoesFalhas.size());
        alerta.put("fechamentosFalhos", (long) fechamentosFalhos.size());
        alerta.put("fechamentosEmProcessamento", (long) competenciasProcessando.size());

        return new IndicadoresResponse(OffsetDateTime.now(), perfil.name(), competencia, campanha, pendencia, alerta,
                acoesPermitidas(perfil, semOrcamento, codigoPendente, aguardandoGerente, lotesComImpedimento,
                        fechamentosFalhos, execucoesFalhas));
    }

    private void adicionarCompetencias(Set<String> destino, Stream<String> origem) {
        origem.filter(Objects::nonNull).filter(c -> !c.isBlank()).forEach(destino::add);
    }

    private Stream<String> competenciasDasVendas() {
        return vendas.findDistinctAnosEMesesVendas().stream().map(this::formatarCompetencia);
    }

    private Stream<String> competenciasDasBasesComissao() {
        return basesComiss.findDistinctAnosEMesesBaseComiss().stream().map(this::formatarCompetencia);
    }

    private String formatarCompetencia(Object[] anoMes) {
        if (anoMes == null || anoMes.length < 2 || anoMes[0] == null || anoMes[1] == null) return null;
        return "%04d-%02d".formatted(((Number) anoMes[0]).intValue(), ((Number) anoMes[1]).intValue());
    }

    private <T> Map<String, T> ultimosPorCompetencia(Collection<T> itens, Function<T, String> competencia,
            Function<T, OffsetDateTime> data) {
        return itens.stream().filter(item -> competencia.apply(item) != null)
                .collect(Collectors.toMap(competencia, Function.identity(),
                        (atual, candidato) -> data.apply(candidato).isAfter(data.apply(atual)) ? candidato : atual));
    }

    private boolean campanhaElegivel(Campanha campanha) {
        return campanha != null && campanha.getRemovidoEm() == null
                && campanha.getEstado() != EstadoCampanha.CANCELADA
                && campanha.getEstado() != EstadoCampanha.CONCLUIDA;
    }

    private boolean temRevisaoAberta(Campanha campanha) {
        CampanhaVersao proposta = campanha.getVersaoProposta();
        return campanha.getEstado() == EstadoCampanha.DRAFT
                || (proposta != null && proposta.getStatus() == StatusVersaoCampanha.PROPOSTA);
    }

    private boolean aguardandoGerente(Campanha campanha) {
        CampanhaVersao proposta = campanha.getVersaoProposta();
        return proposta != null && proposta.getStatus() == StatusVersaoCampanha.PROPOSTA
                ? proposta.isEncaminhadaGerente()
                : campanha.isEncaminhadaGerente();
    }

    private List<IndicadoresResponse.ItemAguardandoAcao> acoesPermitidas(PapelUsuario perfil,
            List<Campanha> semOrcamento, List<CampanhaVersao> codigoPendente, List<Campanha> aguardandoGerente,
            List<LotePreparacao> lotesComImpedimento, List<FechamentoCompetencia> fechamentosFalhos,
            List<ExecucaoCalculo> execucoesFalhas) {
        List<IndicadoresResponse.ItemAguardandoAcao> itens = new ArrayList<>();
        if (perfil == PapelUsuario.ADMINISTRADOR || perfil == PapelUsuario.ANALISTA_NEGOCIOS) {
            semOrcamento.forEach(c -> itens.add(acao("ORCAMENTO_PENDENTE", "Confirmar orçamento da campanha", c.getId(), null,
                    "/api/v1/campanhas/" + c.getId() + "/orcamento", "PATCH")));
            lotesComImpedimento.forEach(l -> itens.add(acao("LOTE_COM_IMPEDIMENTO", "Corrigir linhas impeditivas do lote",
                    l.getCampanha().getId(), l.getCompetencia(),
                    "/api/v1/campanhas/" + l.getCampanha().getId() + "/preparacao-bases/" + l.getId(), "GET")));
        }
        if (perfil == PapelUsuario.ADMINISTRADOR || perfil == PapelUsuario.ANALISTA_CODIGO) {
            codigoPendente.forEach(v -> itens.add(acao("REVISAO_CODIGO_PENDENTE", "Revisar artefatos de código da versão",
                    v.getCampanha().getId(), null, "/api/v1/campanhas/" + v.getCampanha().getId() + "/aprovacao-codigo", "POST")));
        }
        if (perfil == PapelUsuario.ADMINISTRADOR || perfil == PapelUsuario.GERENTE_VENDAS) {
            aguardandoGerente.forEach(c -> itens.add(acao("APROVACAO_GERENCIAL_PENDENTE", "Publicar ou devolver campanha encaminhada",
                    c.getId(), null, "/api/v1/campanhas/" + c.getId() + "/estado", "PATCH")));
            fechamentosFalhos.forEach(f -> itens.add(acao("FECHAMENTO_FALHOU", "Consultar e refazer fechamento", null,
                    f.getCompetencia(), "/api/v1/comissoes/fechamento/" + f.getCompetencia() + "/status", "GET")));
            execucoesFalhas.forEach(e -> itens.add(acao("CALCULO_FALHOU", "Consultar execução de cálculo falha", null,
                    e.getCompetencia(), "/api/v1/execucoes/calculo/" + e.getId(), "GET")));
        }
        return itens;
    }

    private IndicadoresResponse.ItemAguardandoAcao acao(String tipo, String descricao, Long campanhaId,
            String competencia, String href, String metodo) {
        return new IndicadoresResponse.ItemAguardandoAcao(tipo, descricao, campanhaId, competencia, href, metodo);
    }

    private long contarCampanhas(List<Campanha> campanhas, EstadoCampanha estado) {
        return campanhas.stream().filter(c -> c.getEstado() == estado).count();
    }

    private PapelUsuario perfilAtual() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Usuario usuario) return usuario.getPapel();
        throw new org.springframework.security.access.AccessDeniedException("Usuário autenticado não encontrado.");
    }
}

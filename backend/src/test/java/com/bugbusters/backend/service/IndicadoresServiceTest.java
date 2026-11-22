package com.bugbusters.backend.service;

import com.bugbusters.backend.auth.PapelUsuario;
import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.dto.indicador.IndicadoresResponse;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.SaleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicadoresServiceTest {
    @Mock private CampanhaRepository campanhas;
    @Mock private CampanhaVersaoRepository versoes;
    @Mock private LotePreparacaoRepository lotes;
    @Mock private ExecucaoCalculoRepository execucoes;
    @Mock private FechamentoCompetenciaRepository fechamentos;
    @Mock private SaleRepository vendas;
    @Mock private BaseComissRepository basesComiss;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consideraPropostaDeCampanhaAtivaEMantemCampanhasCanceladasForaDasFilas() {
        autenticarComo(PapelUsuario.ADMINISTRADOR);
        Campanha ativa = campanha(1L, EstadoCampanha.ATIVA);
        CampanhaVersao propostaAtiva = proposta(ativa, true, false);
        ativa.setVersaoProposta(propostaAtiva);

        Campanha cancelada = campanha(2L, EstadoCampanha.CANCELADA);
        CampanhaVersao propostaCancelada = proposta(cancelada, false, false);
        cancelada.setVersaoProposta(propostaCancelada);

        prepararRepositorios(List.of(ativa, cancelada), List.of(propostaAtiva, propostaCancelada), List.of(), List.of(), List.of());

        IndicadoresResponse resposta = service().consultar();

        assertEquals(1L, resposta.campanhas().get("aguardandoGerente"));
        assertEquals(1L, resposta.pendencias().get("orcamentoNaoConfirmado"));
        assertEquals(1L, resposta.pendencias().get("revisaoCodigo"));
        assertTrue(resposta.aguardandoAcao().stream().anyMatch(a -> a.tipo().equals("APROVACAO_GERENCIAL_PENDENTE") && a.campanhaId().equals(1L)));
        assertFalse(resposta.aguardandoAcao().stream().anyMatch(a -> a.campanhaId() != null && a.campanhaId().equals(2L)));
    }

    @Test
    void ignoraFalhasSuperadasEUsaRotaCorretaParaCalculoFalho() {
        autenticarComo(PapelUsuario.GERENTE_VENDAS);
        OffsetDateTime agora = OffsetDateTime.now();
        ExecucaoCalculo falhaSuperada = execucao("2026-01", StatusExecucaoCalculo.FALHOU, agora.minusHours(1));
        ExecucaoCalculo sucessoPosterior = execucao("2026-01", StatusExecucaoCalculo.CONCLUIDA, agora);
        ExecucaoCalculo falhaAtual = execucao("2026-02", StatusExecucaoCalculo.FALHOU, agora);
        UUID idFalhaAtual = UUID.randomUUID();
        ReflectionTestUtils.setField(falhaAtual, "id", idFalhaAtual);

        FechamentoCompetencia falhaFechamentoSuperada = fechamento("2026-03", StatusFechamento.FALHOU, agora.minusHours(1));
        FechamentoCompetencia fechamentoConcluido = fechamento("2026-03", StatusFechamento.CONCLUIDO, agora);
        FechamentoCompetencia falhaFechamentoAtual = fechamento("2026-04", StatusFechamento.FALHOU, agora);
        FechamentoCompetencia processando = fechamento("2026-05", StatusFechamento.PROCESSANDO, agora);

        prepararRepositorios(List.of(), List.of(), List.of(),
                List.of(falhaSuperada, sucessoPosterior, falhaAtual),
                List.of(falhaFechamentoSuperada, fechamentoConcluido, falhaFechamentoAtual, processando));
        when(vendas.findDistinctAnosEMesesVendas()).thenReturn(List.<Object[]>of(new Object[] {2026, 6}));
        when(basesComiss.findDistinctAnosEMesesBaseComiss()).thenReturn(List.<Object[]>of(new Object[] {2026, 7}));

        IndicadoresResponse resposta = service().consultar();

        assertEquals(1L, resposta.alertas().get("execucoesFalhas"));
        assertEquals(1L, resposta.alertas().get("fechamentosFalhos"));
        assertEquals(1L, resposta.competencias().get("processando"));
        assertEquals(resposta.competencias().get("total"), resposta.competencias().get("abertas")
                + resposta.competencias().get("processando") + resposta.competencias().get("fechadas"));
        assertTrue(resposta.aguardandoAcao().stream().anyMatch(a -> a.tipo().equals("CALCULO_FALHOU")
                && a.href().equals("/api/v1/execucoes/calculo/" + idFalhaAtual)));
        assertFalse(resposta.aguardandoAcao().stream().anyMatch(a -> a.competencia().equals("2026-01") || a.competencia().equals("2026-03")));
    }

    private IndicadoresService service() {
        return new IndicadoresService(campanhas, versoes, lotes, execucoes, fechamentos, vendas, basesComiss);
    }

    private void prepararRepositorios(List<Campanha> listaCampanhas, List<CampanhaVersao> listaVersoes,
            List<LotePreparacao> listaLotes, List<ExecucaoCalculo> listaExecucoes, List<FechamentoCompetencia> listaFechamentos) {
        when(campanhas.findAllByRemovidoEmIsNullOrderByCriadoEmDesc()).thenReturn(listaCampanhas);
        when(versoes.findAll()).thenReturn(listaVersoes);
        when(lotes.findAll()).thenReturn(listaLotes);
        when(execucoes.findAll()).thenReturn(listaExecucoes);
        when(fechamentos.findAll()).thenReturn(listaFechamentos);
        when(vendas.findDistinctAnosEMesesVendas()).thenReturn(List.of());
        when(basesComiss.findDistinctAnosEMesesBaseComiss()).thenReturn(List.of());
    }

    private void autenticarComo(PapelUsuario papel) {
        Usuario usuario = new Usuario();
        usuario.setPapel(papel);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null));
    }

    private Campanha campanha(Long id, EstadoCampanha estado) {
        Campanha campanha = new Campanha();
        campanha.setId(id);
        campanha.setEstado(estado);
        return campanha;
    }

    private CampanhaVersao proposta(Campanha campanha, boolean encaminhada, boolean orcamentoConfirmado) {
        CampanhaVersao proposta = new CampanhaVersao();
        proposta.setCampanha(campanha);
        proposta.setStatus(StatusVersaoCampanha.PROPOSTA);
        proposta.setEncaminhadaGerente(encaminhada);
        proposta.setOrcamentoConfirmado(orcamentoConfirmado);
        proposta.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.PENDENTE);
        return proposta;
    }

    private ExecucaoCalculo execucao(String competencia, StatusExecucaoCalculo status, OffsetDateTime solicitadaEm) {
        ExecucaoCalculo execucao = new ExecucaoCalculo();
        execucao.setCompetencia(competencia);
        execucao.setStatus(status);
        ReflectionTestUtils.setField(execucao, "solicitadoEm", solicitadaEm);
        return execucao;
    }

    private FechamentoCompetencia fechamento(String competencia, StatusFechamento status, OffsetDateTime iniciadoEm) {
        FechamentoCompetencia fechamento = new FechamentoCompetencia();
        fechamento.setCompetencia(competencia);
        fechamento.setStatus(status);
        fechamento.setIniciadoEm(iniciadoEm);
        return fechamento;
    }
}

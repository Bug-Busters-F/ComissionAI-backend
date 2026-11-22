package com.bugbusters.backend.service;

import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampanhaSimulacaoProgramaAprovadoTest {
    final CampanhaSimulacaoValidator validator = new CampanhaSimulacaoValidator(mock(SimulacaoRepository.class), mock(RegraRepository.class));
    final Campanha campanha = new Campanha();
    final CampanhaVersao versao = new CampanhaVersao();
    final CampanhaProgramaPython programa;
    CampanhaSimulacaoProgramaAprovadoTest() {
        versao.setId(1L); campanha.setVersaoProposta(versao);
        programa = new CampanhaProgramaPython(versao, 2, "def calcular_comissao(venda): return 1");
        programa.setStatusAprovacao(StatusAprovacaoPrograma.APROVADO);
        versao.setProgramaPythonAprovado(programa);
        versao.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);
    }
    @Test void programaCompletoHomologadoNaoExigeAprovacoesLegadas() {
        assertTrue(validator.detectarDivergenciasAprovacaoCodigo(campanha).isEmpty());
    }
    @Test void programaPendenteNaoPodeSerPublicado() {
        programa.setStatusAprovacao(StatusAprovacaoPrograma.PENDENTE_REVISAO);
        assertFalse(validator.detectarDivergenciasAprovacaoCodigo(campanha).isEmpty());
    }
    @Test void programaDeOutraVersaoNaoPodeSerPublicado() {
        var outra = new CampanhaVersao(); outra.setId(2L); programa.setCampanhaVersao(outra);
        assertFalse(validator.detectarDivergenciasAprovacaoCodigo(campanha).isEmpty());
    }
    @Test void fonteAlteradaDepoisDaAprovacaoNaoPodeSerPublicada() {
        String checksumAprovado = programa.getChecksumSha256();
        programa.setCodigoFonte("def calcular_comissao(venda): return 999");
        programa.setChecksumSha256(checksumAprovado);
        assertFalse(validator.detectarDivergenciasAprovacaoCodigo(campanha).isEmpty());
    }
    @Test void propostaSemProgramaMantemValidacaoLegada() {
        versao.setProgramaPythonAprovado(null);
        assertFalse(validator.detectarDivergenciasAprovacaoCodigo(campanha).isEmpty());
    }
    @Test void simulacaoDeProgramaAnteriorContinuaBloqueada() {
        var simulacao = new Simulacao(); simulacao.setExecutadoViaPython(true);
        simulacao.setVersaoProgramaPython(1); simulacao.setChecksumPython(programa.getChecksumSha256());
        assertTrue(validator.conferirDivergencias(campanha, simulacao).stream().anyMatch(s -> s.contains("programa atualmente aprovado")));
    }
}

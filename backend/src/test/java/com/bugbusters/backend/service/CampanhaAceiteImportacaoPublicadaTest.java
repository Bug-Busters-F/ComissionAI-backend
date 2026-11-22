package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import com.bugbusters.backend.dto.pendencia.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CampanhaAceiteImportacaoPublicadaTest {
    @Mock CampanhaRepository campanhas;
    @Mock CampanhaVersaoRepository versoes;
    @Mock RegraRepository regras;
    @Mock CampanhaPendenciaAceiteRepository aceites;
    @Mock AuditoriaGeralService auditoria;
    @Mock InconsistenciaImportacaoRepository inconsistencias;
    @Mock CampanhaAnexoBaseRepository anexos;
    @Mock FechamentoService fechamento;
    CampanhaPendenciaService service;
    Campanha campanha;
    CampanhaVersao vigente;
    EnvioArquivo envio;
    InconsistenciaImportacao inconsistencia;

    @BeforeEach void setup() {
        service = new CampanhaPendenciaService(campanhas, versoes, regras, aceites, auditoria, inconsistencias, anexos, fechamento);
        ReflectionTestUtils.setField(service, "usuarioAtualProvider", new com.bugbusters.backend.service.auth.SistemaUsuarioAtualProvider());
        campanha = new Campanha("Publicada", "Texto original", LocalDate.of(2025,11,1), LocalDate.of(2025,11,30));
        campanha.setId(12L);
        vigente = new CampanhaVersao(); vigente.setId(101L); vigente.setNumero(1); vigente.setCampanha(campanha);
        vigente.setStatus(StatusVersaoCampanha.PUBLICADA); vigente.setDataInicio(campanha.getDataInicio()); vigente.setDataFim(campanha.getDataFim());
        campanha.setVersaoVigente(vigente);
        envio = new EnvioArquivo(); envio.setId(7L); envio.setReportId(90L); envio.setCompetencia("2025-11");
        inconsistencia = new InconsistenciaImportacao(envio, 681, "matricula", "Matrícula não cadastrada: MATRIC-207", "ADVERTENCIA");
        ReflectionTestUtils.setField(inconsistencia, "id", 501L);
        when(campanhas.findByIdAndRemovidoEmIsNull(12L)).thenReturn(Optional.of(campanha));
        role("ADMINISTRADOR");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void role(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("gestor@empresa.local", null, List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
    AceitarPendenciaRequest request() { return new AceitarPendenciaRequest(null,"qualquer-identificador","IGNORADO","outroCampo","Mensagem do cliente",SeveridadePendencia.AVISO,"autor-falso","Conferido com os registros oficiais",null,501L); }
    void linked() { when(inconsistencias.findById(501L)).thenReturn(Optional.of(inconsistencia)); when(anexos.existsByCampanhaIdAndEnvioArquivoIdAndDesanexadoEmIsNull(12L,7L)).thenReturn(true); }
    void saving() {
        when(aceites.findByCampanhaVersaoIdAndPendenciaIdentificadorAndStatus(101L,"inconsistencia:501",StatusAceitePendencia.ACEITA)).thenReturn(Optional.empty());
        when(aceites.save(any())).thenAnswer(invocation -> { var result = invocation.getArgument(0); ReflectionTestUtils.setField(result,"id",80L); return result; });
    }
    @Test void aceitaAdvertenciaDaBaseSemAlterarVersaoOuRegrasPublicadas() {
        linked(); saving();
        var response = service.aceitarPendencia(12L,null,request());
        assertThat(response.status()).isEqualTo(StatusAceitePendencia.ACEITA);
        assertThat(response.aceitoPor()).isEqualTo("gestor@empresa.local");
        assertThat(response.pendenciaIdentificador()).isEqualTo("inconsistencia:501");
        assertThat(response.campo()).isEqualTo("matricula");
        assertThat(response.mensagem()).isEqualTo(inconsistencia.getMotivo());
        assertThat(response.severidade()).isEqualTo(SeveridadePendencia.AVISO);
        verify(fechamento).validarPeriodoDisponivel(LocalDate.of(2025,11,1),LocalDate.of(2025,11,30));
        verifyNoInteractions(regras,versoes);
        verify(campanhas,never()).save(any());
        assertThat(vigente.getStatus()).isEqualTo(StatusVersaoCampanha.PUBLICADA);
    }
    @Test void naoUsaPropostaDeEdicaoComoDestinoDoAceiteDaBase() {
        CampanhaVersao proposta = new CampanhaVersao(); proposta.setId(102L); proposta.setNumero(2); campanha.setVersaoProposta(proposta);
        linked(); saving();
        assertThat(service.aceitarPendencia(12L,null,request()).campanhaVersaoId()).isEqualTo(101L);
    }
    @Test void mantemProibicaoDeAceitarPendenciaDeRegraPublicada() {
        var req = new AceitarPendenciaRequest(null,"regra:1","TAXA",null,null,SeveridadePendencia.AVISO,null,"Justificativa");
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,req)).isInstanceOf(BusinessException.class).hasMessageContaining("imutáveis");
        verifyNoInteractions(aceites);
    }
    @Test void naoAceitaErroImpeditivoMesmoSeClienteDeclararAviso() {
        inconsistencia = new InconsistenciaImportacao(envio,681,"matricula","Erro impeditivo","IMPEDITIVO");
        ReflectionTestUtils.setField(inconsistencia,"id",501L);
        when(inconsistencias.findById(501L)).thenReturn(Optional.of(inconsistencia));
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,request())).isInstanceOf(BusinessException.class).hasMessageContaining("não admitem aceite");
        verifyNoInteractions(aceites);
    }
    @Test void rejeitaArquivoNaoVinculado() {
        when(inconsistencias.findById(501L)).thenReturn(Optional.of(inconsistencia));
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,request())).isInstanceOf(BusinessException.class).hasMessageContaining("não está vinculado");
        verifyNoInteractions(aceites);
    }
    @Test void rejeitaArquivoForaDaVigencia() {
        envio.setCompetencia("2025-12"); when(inconsistencias.findById(501L)).thenReturn(Optional.of(inconsistencia));
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,request())).isInstanceOf(BusinessException.class).hasMessageContaining("não pertence à vigência");
        verifyNoInteractions(aceites);
    }
    @Test void bloqueiaCompetenciaFechadaAntesDeGravarAceite() {
        when(inconsistencias.findById(501L)).thenReturn(Optional.of(inconsistencia));
        doThrow(new BusinessException("Competência fechada")).when(fechamento).validarPeriodoDisponivel(any(),any());
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,request())).hasMessageContaining("fechada");
        verifyNoInteractions(aceites);
    }
    @Test void permiteGerenteRegistrarAceiteDeArquivo() {
        role("GERENTE_VENDAS"); linked(); saving();
        assertThat(service.aceitarPendencia(12L,null,request()).status()).isEqualTo(StatusAceitePendencia.ACEITA);
    }
    @Test void rejeitaArquivoRemovido() {
        envio.setExcluidoEm(java.time.OffsetDateTime.now());
        when(inconsistencias.findById(501L)).thenReturn(Optional.of(inconsistencia));
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,request())).isInstanceOf(BusinessException.class).hasMessageContaining("removido ou rejeitado");
        verifyNoInteractions(aceites);
    }
    @Test void naoPermiteAnalistaRegistrarAceiteDeArquivo() {
        role("ANALISTA_NEGOCIOS");
        assertThatThrownBy(()->service.aceitarPendencia(12L,null,request())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(inconsistencias,aceites);
    }
    @Test void revogaSomenteAceiteDeBaseSemMudarRegrasPublicadas() {
        linked();
        var aceite = new CampanhaPendenciaAceite(campanha,vigente,null,"inconsistencia:501","INCONSISTENCIA_IMPORTACAO","matricula",inconsistencia.getMotivo(),SeveridadePendencia.AVISO,"gestor","Conferido");
        ReflectionTestUtils.setField(aceite,"id",80L); aceite.setInconsistenciaImportacaoId(501L); aceite.setEnvioArquivoId(7L);
        when(aceites.findById(80L)).thenReturn(Optional.of(aceite)); when(aceites.save(aceite)).thenReturn(aceite);
        assertThat(service.revogarAceite(12L,80L,new RevogarAceitePendenciaRequest(null,"Revisão dos documentos")).status()).isEqualTo(StatusAceitePendencia.REVOGADA);
        verifyNoInteractions(regras,versoes);
    }
}

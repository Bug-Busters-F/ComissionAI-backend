package com.bugbusters.backend.importbase.dependencia;

import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaAnexoBase;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.repository.CampanhaAnexoBaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificadorDependenciaAnexoCampanhaTest {

    @Mock
    private CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;

    @InjectMocks
    private VerificadorDependenciaAnexoCampanha verificador;

    @Test
    @DisplayName("Deve retornar vazio quando o lote for nulo ou sem ID")
    void deveRetornarVazioQuandoLoteNulo() {
        assertThat(verificador.verificar(null)).isEmpty();

        EnvioArquivo loteSemId = new EnvioArquivo();
        assertThat(verificador.verificar(loteSemId)).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar motivo de bloqueio quando lote estiver anexado a campanha ativa")
    void deveBloquearExclusaoQuandoLoteAnexadoACampanhaAtiva() {
        EnvioArquivo lote = new EnvioArquivo();
        lote.setId(100L);
        lote.setReportId(10042L);

        Campanha campanha = new Campanha();
        campanha.setId(15L);
        campanha.setTitulo("Campanha Inverno 2026");
        campanha.setRemovidoEm(null);

        CampanhaAnexoBase anexo = new CampanhaAnexoBase();
        anexo.setCampanha(campanha);
        anexo.setEnvioArquivo(lote);

        when(campanhaAnexoBaseRepository.findAnexosAtivosDeCampanhasNaoRemovidas(100L))
                .thenReturn(List.of(anexo));

        List<DependenciaMotivoDTO> motivos = verificador.verificar(lote);

        assertThat(motivos).hasSize(1);
        assertThat(motivos.get(0).campo()).isEqualTo("LOTE_ANEXADO_CAMPANHA");
        assertThat(motivos.get(0).motivo()).contains("Campanha Inverno 2026 (ID: 15)");
    }

    @Test
    @DisplayName("Não deve bloquear exclusão quando lote estiver anexado apenas a campanhas removidas logicamente")
    void naoDeveBloquearQuandoCampanhaRemovidaLogicamente() {
        EnvioArquivo lote = new EnvioArquivo();
        lote.setId(200L);

        // findAnexosAtivosDeCampanhasNaoRemovidas já filtra c.removidoEm IS NULL, retornando lista vazia
        when(campanhaAnexoBaseRepository.findAnexosAtivosDeCampanhasNaoRemovidas(200L))
                .thenReturn(Collections.emptyList());

        List<DependenciaMotivoDTO> motivos = verificador.verificar(lote);

        assertThat(motivos).isEmpty();
    }
}

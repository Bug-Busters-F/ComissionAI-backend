package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.preparacao.*;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PreparacaoBaseServiceTest {

    @Autowired
    private PreparacaoBaseService preparacaoBaseService;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private LotePreparacaoRepository lotePreparacaoRepository;

    @Autowired
    private LinhaPreparacaoRepository linhaPreparacaoRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Campanha campanha;

    @BeforeEach
    void setUp() {
        CampaignDataCleanup.clear(jdbcTemplate);

        jdbcTemplate.update("DELETE FROM tb_resultado_calculo");
        jdbcTemplate.update("DELETE FROM tb_sales");
        jdbcTemplate.update("DELETE FROM tb_registration");
        jdbcTemplate.update("DELETE FROM tb_inconsistencia_importacao");
        jdbcTemplate.update("DELETE FROM tb_envio_arquivo");

        brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("Marca Alpha");
            return brandRepository.save(b);
        });

        storeRepository.findByCode(20).orElseGet(() -> {
            Store s = new Store();
            s.setCode(20);
            s.setDescription("Loja Centro");
            return storeRepository.save(s);
        });

        positionRepository.findByCode(30).orElseGet(() -> {
            Position p = new Position();
            p.setCode(30);
            p.setDescription("Consultor");
            return positionRepository.save(p);
        });

        Campanha c = new Campanha("Campanha Vendas 2026", "Campanha de testes de preparacao", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30));
        campanha = campanhaRepository.save(c);
    }

    @Test
    @DisplayName("Upload de arquivo RH com linha inválida mantém linhas e registra inconsistências")
    void uploadArquivoComErros_persisteLinhasEInconsistencias() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][] {
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "M100", "2025-01-01", null, 30, "Consultor"},
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "M200", "2025-01-01", null, null, null} // sem cargo -> erro
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(),
                file,
                "HR",
                "2026-05",
                "MANUAL",
                null,
                "teste_user"
        );

        assertThat(loteDTO).isNotNull();
        assertThat(loteDTO.status()).isEqualTo("EM_PREPARACAO");
        assertThat(loteDTO.aptaParaEfetivacao()).isFalse();
        assertThat(loteDTO.contagens().totalLinhas()).isEqualTo(2);
        assertThat(loteDTO.contagens().linhasInvalidas()).isEqualTo(1);
        assertThat(loteDTO.contagens().linhasValidas()).isEqualTo(1);

        List<LinhaPreparacao> linhas = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(loteDTO.id());
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).getStatusLinha()).isEqualTo("VALIDA");
        assertThat(linhas.get(1).getStatusLinha()).isEqualTo("INVALIDA");
        assertThat(linhas.get(1).getErrosValidacaoJson()).contains("positionCode");
    }

    @Test
    @DisplayName("Edição de linha corrige inconsistência e lock otimista rejeita versão desatualizada")
    void edicaoLinha_atualizaStatusELockOtimista() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][] {
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "M300", "2025-01-01", null, null, null}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(),
                file,
                "HR",
                "2026-05",
                "MANUAL",
                null,
                "teste_user"
        );

        LinhaPreparacao linha = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(loteDTO.id()).get(0);
        assertThat(linha.getStatusLinha()).isEqualTo("INVALIDA");

        // Lock otimista com versao incorreta -> 409
        EditarLinhaPreparacaoRequestDTO requestIncorreto = new EditarLinhaPreparacaoRequestDTO(
                linha.getVersao() + 99,
                "Correção teste",
                30,
                "Consultor"
        );

        assertThatThrownBy(() -> preparacaoBaseService.editarLinha(
                campanha.getId(), loteDTO.id(), linha.getNumeroLinha(), requestIncorreto
        )).isInstanceOf(ResourceConflictException.class);

        // Lock correto -> sucesso
        EditarLinhaPreparacaoRequestDTO requestCorreto = new EditarLinhaPreparacaoRequestDTO(
                linha.getVersao(),
                "Correção de cargo",
                30,
                "Consultor"
        );

        var linhaAtualizadaDTO = preparacaoBaseService.editarLinha(
                campanha.getId(), loteDTO.id(), linha.getNumeroLinha(), requestCorreto
        );

        assertThat(linhaAtualizadaDTO.statusLinha()).isEqualTo("VALIDA");
        assertThat(linhaAtualizadaDTO.erros()).isEmpty();

        // Lote foi revalidado automaticamente
        LotePreparacao loteAtualizado = lotePreparacaoRepository.findById(loteDTO.id()).orElseThrow();
        assertThat(loteAtualizado.isAptaParaEfetivacao()).isTrue();
    }

    @Test
    @DisplayName("Efetivação de lote válido persiste dados na base geral e anexa à campanha")
    void efetivarLote_persisteBaseGeralEAnexaCampanha() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][] {
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "M400", "2025-01-01", null, 30, "Consultor"}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(),
                file,
                "HR",
                "2026-05",
                "MANUAL",
                null,
                "teste_user"
        );

        EfetivacaoPreparacaoResponseDTO efetivacao = preparacaoBaseService.efetivarLote(
                campanha.getId(),
                loteDTO.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Efetivação oficial", true)
        );

        assertThat(efetivacao.status()).isEqualTo("EFETIVADO");
        assertThat(efetivacao.registrosInseridos()).isEqualTo(1);
        assertThat(efetivacao.reportIdGerado()).isNotNull();

        // Verifica na base geral de RH
        Optional<Registration> regOpt = registrationRepository.findByRegistration("M400");
        assertThat(regOpt).isPresent();
        Registration reg = regOpt.get();
        assertThat(reg.getVersao()).isEqualTo(1);
        assertThat(reg.getOrigemUltimaAlteracao()).isEqualTo("PREPARACAO_CAMPANHA");

        // Verifica anexo automático na campanha
        boolean anexado = campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanha.getId())
                .stream()
                .anyMatch(a -> a.getId().equals(efetivacao.anexoCampanhaId()));
        assertThat(anexado).isTrue();
    }

    @Test
    @DisplayName("Descarte de lote altera status e impede nova edição")
    void descartarLote_alteraStatus() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][] {
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "M500", "2025-01-01", null, 30, "Consultor"}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(),
                file,
                "HR",
                "2026-05",
                "MANUAL",
                null,
                "teste_user"
        );

        LotePreparacaoResponseDTO descartado = preparacaoBaseService.descartarLote(
                campanha.getId(),
                loteDTO.id(),
                "Descarte de teste",
                "admin"
        );

        assertThat(descartado.status()).isEqualTo("DESCARTADO");

        // Tentar editar linha de lote descartado deve falhar
        LinhaPreparacao linha = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(loteDTO.id()).get(0);
        assertThatThrownBy(() -> preparacaoBaseService.editarLinha(
                campanha.getId(),
                loteDTO.id(),
                linha.getNumeroLinha(),
                new EditarLinhaPreparacaoRequestDTO(linha.getVersao(), "teste", 30, "Consultor")
        )).isInstanceOf(Exception.class);
    }

    private MockMultipartFile criarExcelHr(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    Object val = rows[i][j];
                    if (val instanceof Number n) {
                        r.createCell(j).setCellValue(n.doubleValue());
                    } else if (val != null) {
                        r.createCell(j).setCellValue(val.toString());
                    }
                }
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo", "base_rh.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}

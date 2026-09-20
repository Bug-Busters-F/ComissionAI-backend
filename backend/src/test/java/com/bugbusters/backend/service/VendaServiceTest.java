package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.venda.VendaRequestDTO;
import com.bugbusters.backend.dto.venda.VendaResponseDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Venda;
import com.bugbusters.backend.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    @Mock
    private VendaRepository vendaRepository;

    @InjectMocks
    private VendaService vendaService;

    private VendaRequestDTO requestPadrao;
    private final String ID_EXTERNO = "VENDA-2026-001";
    private final String MATRICULA = "MAT-123";
    private final LocalDate DATA_VENDA = LocalDate.of(2026, 9, 20);
    private final BigDecimal VALOR_VENDA = new BigDecimal("1500.00");

    @BeforeEach
    void setUp() {
        requestPadrao = new VendaRequestDTO(
                ID_EXTERNO,
                MATRICULA,
                "ECOMMERCE",
                "PRETO",
                "LOJA_CENTRO",
                DATA_VENDA,
                VALOR_VENDA
        );
    }

    @Test
    @DisplayName("1. Deve registrar venda nova com sucesso na primeira requisição")
    void deveRegistrarVendaPrimeiraVez() {
        when(vendaRepository.findByIdVendaExterno(ID_EXTERNO)).thenReturn(Optional.empty());

        when(vendaRepository.save(any(Venda.class))).thenAnswer(invocation -> {
            Venda v = invocation.getArgument(0);
            v.setId(10L);
            return v;
        });

        VendaResponseDTO response = vendaService.registrarVenda(requestPadrao);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals(ID_EXTERNO, response.idVendaExterno());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorVenda());
        assertEquals("ECOMMERCE", response.canal());

        verify(vendaRepository, times(1)).save(any(Venda.class));
    }

    @Test
    @DisplayName("2. Deve retornar venda existente sem duplicar quando reenvio for 100% idêntico (Idempotência)")
    void deveRetornarVendaExistenteSemDuplicarQuandoReenvioIdentico() {
        Venda vendaExistente = new Venda();
        vendaExistente.setId(10L);
        vendaExistente.setIdVendaExterno(ID_EXTERNO);
        vendaExistente.setMatricula(MATRICULA);
        vendaExistente.setCanal("ECOMMERCE");
        vendaExistente.setMarca("PRETO");
        vendaExistente.setLoja("LOJA_CENTRO");
        vendaExistente.setDataVenda(DATA_VENDA);
        vendaExistente.setValorVenda(VALOR_VENDA);
        vendaExistente.setCriadoEm(OffsetDateTime.now());

        when(vendaRepository.findByIdVendaExterno(ID_EXTERNO)).thenReturn(Optional.of(vendaExistente));

        // Execução
        VendaResponseDTO response = vendaService.registrarVenda(requestPadrao);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals(ID_EXTERNO, response.idVendaExterno());
        assertEquals(MATRICULA, response.matricula());
        assertEquals(VALOR_VENDA, response.valorVenda());

        // REGRA CRÍTICA: save nunca deve ser chamado no reenvio idêntico
        verify(vendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Deve rejeitar requisição com BusinessException quando dados forem divergentes para o mesmo idVendaExterno")
    void deveRejeitarVendaComDadosDivergentes() {
        Venda vendaExistente = new Venda();
        vendaExistente.setId(10L);
        vendaExistente.setIdVendaExterno(ID_EXTERNO);
        vendaExistente.setMatricula(MATRICULA);
        vendaExistente.setCanal("ECOMMERCE");
        vendaExistente.setMarca("PRETO");
        vendaExistente.setLoja("LOJA_CENTRO");
        vendaExistente.setDataVenda(DATA_VENDA);
        vendaExistente.setValorVenda(VALOR_VENDA); // 1500.00

        when(vendaRepository.findByIdVendaExterno(ID_EXTERNO)).thenReturn(Optional.of(vendaExistente));

        // Nova requisição com o mesmo ID, porém valor diferente (2000.00)
        VendaRequestDTO requestDivergente = new VendaRequestDTO(
                ID_EXTERNO,
                MATRICULA,
                "ECOMMERCE",
                "PRETO",
                "LOJA_CENTRO",
                DATA_VENDA,
                new BigDecimal("2000.00")
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                vendaService.registrarVenda(requestDivergente)
        );

        assertTrue(ex.getMessage().contains("Conflito de duplicidade"));
        assertTrue(ex.getMessage().contains("dados divergentes"));
        verify(vendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Deve tratar concorrência via DataIntegrityViolationException e recuperar a venda vencedora")
    void deveTratarConcorrenciaComSucesso() {
        Venda vendaExistente = new Venda();
        vendaExistente.setId(15L);
        vendaExistente.setIdVendaExterno(ID_EXTERNO);
        vendaExistente.setMatricula(MATRICULA);
        vendaExistente.setCanal("ECOMMERCE");
        vendaExistente.setMarca("PRETO");
        vendaExistente.setLoja("LOJA_CENTRO");
        vendaExistente.setDataVenda(DATA_VENDA);
        vendaExistente.setValorVenda(VALOR_VENDA);

        when(vendaRepository.findByIdVendaExterno(ID_EXTERNO))
                .thenReturn(Optional.empty()) // primeira checagem
                .thenReturn(Optional.of(vendaExistente)); // recuperação pós-exceção

        when(vendaRepository.save(any(Venda.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        VendaResponseDTO response = vendaService.registrarVenda(requestPadrao);

        assertNotNull(response);
        assertEquals(15L, response.id());
        assertEquals(ID_EXTERNO, response.idVendaExterno());
    }
}

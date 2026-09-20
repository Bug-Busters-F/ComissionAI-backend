package com.bugbusters.backend.sales;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationResolver;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository vendaRepository;

    @Mock
    private RegistrationResolver registrationResolver;

    @Mock
    private BrandResolver brandResolver;

    @Mock
    private StoreResolver storeResolver;

    @InjectMocks
    private SaleService saleService;

    private Registration mockRegistration;
    private Brand mockBrand;
    private Store mockStore;
    private final LocalDate DATA_VENDA = LocalDate.of(2026, 9, 13);
    private final BigDecimal VALOR = new BigDecimal("1500.00");

    @BeforeEach
    void setUp() {
        mockRegistration = new Registration();
        mockRegistration.setRegistration("MAT-00456");

        mockBrand = new Brand();
        mockBrand.setCode(10);
        mockBrand.setDescription("MARCA_TESTE");

        mockStore = new Store();
        mockStore.setCode(62);
        mockStore.setDescription("LOJA_TESTE");
    }

    @Test
    @DisplayName("1. Deve registrar nova venda individual com sucesso")
    void deveRegistrarNovaVendaComSucesso() {
        SaleRequestDTO request = new SaleRequestDTO(
                "MAT-00456",
                10,
                62,
                VALOR,
                DATA_VENDA,
                "ECOMMERCE"
        );

        when(registrationResolver.resolve("MAT-00456")).thenReturn(mockRegistration);
        when(brandResolver.resolve(10)).thenReturn(mockBrand);
        when(storeResolver.resolve(62)).thenReturn(mockStore);

        when(vendaRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        SaleResponseDTO response = saleService.registrarVenda(request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(DATA_VENDA, response.saleDate());
        assertEquals(0, VALOR.compareTo(response.value()));
        assertEquals("ECOMMERCE", response.saleChannel());

        verify(vendaRepository, times(1)).save(any(Sale.class));
    }

    @Test
    @DisplayName("2. Deve retornar venda existente e NÃO salvar novamente quando houver reenvio idêntico (Idempotência)")
    void deveRetornarVendaExistenteSemSalvarNovamenteQuandoReenvioIdentico() {
        UUID saleId = UUID.randomUUID();

        SaleRequestDTO request = new SaleRequestDTO(
                saleId,
                "MAT-00456",
                10,
                62,
                VALOR,
                DATA_VENDA,
                "ECOMMERCE"
        );

        Sale existente = new Sale();
        existente.setId(saleId);
        existente.setRegistration(mockRegistration);
        existente.setBrand(mockBrand);
        existente.setStore(mockStore);
        existente.setValue(VALOR.doubleValue());
        existente.setSaleDate(DATA_VENDA);
        existente.setSaleChannel("ECOMMERCE");

        when(vendaRepository.findById(saleId)).thenReturn(Optional.of(existente));

        SaleResponseDTO response = saleService.registrarVenda(request);

        assertNotNull(response);
        assertEquals(saleId, response.id());
        assertEquals(0, VALOR.compareTo(response.value()));

        // Idempotência garantida: nada deve ser salvo ou resolvido novamente
        verify(vendaRepository, never()).save(any());
        verifyNoInteractions(registrationResolver, brandResolver, storeResolver);
    }

    @Test
    @DisplayName("3. Deve lançar BusinessException quando reenvio tiver mesmo ID mas dados divergentes")
    void deveLancarBusinessExceptionQuandoReenvioComMesmoIdMasDadosDivergentes() {
        UUID saleId = UUID.randomUUID();

        // Requisição com valor diferente (R$ 2000.00 em vez de R$ 1500.00)
        SaleRequestDTO requestDivergente = new SaleRequestDTO(
                saleId,
                "MAT-00456",
                10,
                62,
                new BigDecimal("2000.00"),
                DATA_VENDA,
                "ECOMMERCE"
        );

        Sale existente = new Sale();
        existente.setId(saleId);
        existente.setRegistration(mockRegistration);
        existente.setBrand(mockBrand);
        existente.setStore(mockStore);
        existente.setValue(VALOR.doubleValue());
        existente.setSaleDate(DATA_VENDA);
        existente.setSaleChannel("ECOMMERCE");

        when(vendaRepository.findById(saleId)).thenReturn(Optional.of(existente));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                saleService.registrarVenda(requestDivergente)
        );

        assertTrue(ex.getMessage().contains("dados diferentes"));
        assertTrue(ex.getMessage().contains(saleId.toString()));

        verify(vendaRepository, never()).save(any());
    }
}

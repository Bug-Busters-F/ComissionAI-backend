package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.venda.VendaRequestDTO;
import com.bugbusters.backend.dto.venda.VendaResponseDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Venda;
import com.bugbusters.backend.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendaService {

    private final VendaRepository vendaRepository;

    public VendaService(VendaRepository vendaRepository) {
        this.vendaRepository = vendaRepository;
    }

    /**
     * Registra uma venda individual no sistema.
     * <p>
     * Regras de negócio aplicadas:
     * <ul>
     *   <li>Rejeita registros cujo {@code idVendaExterno} já existe (preparação para idempotência
     *       na etapa de cálculo de comissão).</li>
     *   <li>Normaliza canal, marca e loja para maiúsculas e sem espaços extras, garantindo
     *       consistência para o cruzamento com as regras de comissionamento.</li>
     *   <li>A atribuição do ID interno fica a cargo do banco (IDENTITY), conforme a migration.</li>
     * </ul>
     * </p>
     *
     * @param request payload de entrada validado pelo controller
     * @return {@link VendaResponseDTO} com os dados persistidos, incluindo o ID interno gerado
     * @throws BusinessException se {@code idVendaExterno} já estiver registrado
     */
    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO request) {
        if (vendaRepository.existsByIdVendaExterno(request.idVendaExterno())) {
            throw new BusinessException(
                    "Já existe uma venda registrada com o identificador externo '"
                    + request.idVendaExterno() + "'. Operação rejeitada para evitar duplicidade."
            );
        }

        Venda venda = new Venda();
        venda.setIdVendaExterno(request.idVendaExterno().trim());
        venda.setMatricula(request.matricula().trim());
        venda.setCanal(request.canal().toUpperCase().trim());
        venda.setMarca(request.marca().toUpperCase().trim());
        venda.setLoja(request.loja().toUpperCase().trim());
        venda.setDataVenda(request.dataVenda());
        venda.setValorVenda(request.valorVenda());

        Venda vendaSalva = vendaRepository.save(venda);

        return mapearParaResponse(vendaSalva);
    }

    // -------------------------------------------------------------------------
    // Métodos privados de mapeamento
    // -------------------------------------------------------------------------

    private VendaResponseDTO mapearParaResponse(Venda venda) {
        return new VendaResponseDTO(
                venda.getId(),
                venda.getIdVendaExterno(),
                venda.getMatricula(),
                venda.getCanal(),
                venda.getMarca(),
                venda.getLoja(),
                venda.getDataVenda(),
                venda.getValorVenda(),
                venda.getCriadoEm()
        );
    }
}

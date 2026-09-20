package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.venda.VendaRequestDTO;
import com.bugbusters.backend.dto.venda.VendaResponseDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Venda;
import com.bugbusters.backend.repository.VendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class VendaService {

    private static final Logger log = LoggerFactory.getLogger(VendaService.class);

    private final VendaRepository vendaRepository;

    public VendaService(VendaRepository vendaRepository) {
        this.vendaRepository = vendaRepository;
    }

    /**
     * Registra uma venda individual no sistema com garantia de idempotência.
     * <p>
     * Regras de negócio aplicadas:
     * <ul>
     *   <li>Se a venda com {@code idVendaExterno} já existe e possui dados 100% idênticos,
     *       retorna o registro pré-existente sem persistir duplicatas (idempotência para reenvios acidentais).</li>
     *   <li>Se a venda com {@code idVendaExterno} já existe mas algum dado é divergente
     *       (matrícula, valor, data, canal, marca ou loja), rejeita com {@link BusinessException}.</li>
     *   <li>Normaliza canal, marca e loja para maiúsculas e sem espaços extras.</li>
     *   <li>Trata condições de concorrência com chave única no banco.</li>
     * </ul>
     * </p>
     *
     * @param request payload de entrada validado pelo controller
     * @return {@link VendaResponseDTO} com os dados persistidos, incluindo o ID interno gerado
     * @throws BusinessException se {@code idVendaExterno} já estiver registrado com dados divergentes
     */
    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO request) {
        String idExternoNormalizado = request.idVendaExterno().trim();

        // 1. Verifica se a venda já existe pelo identificador externo
        Optional<Venda> vendaExistenteOpt = vendaRepository.findByIdVendaExterno(idExternoNormalizado);
        if (vendaExistenteOpt.isPresent()) {
            return tratarVendaExistente(request, vendaExistenteOpt.get());
        }

        // 2. Prepara nova venda
        Venda venda = new Venda();
        venda.setIdVendaExterno(idExternoNormalizado);
        venda.setMatricula(request.matricula().trim());
        venda.setCanal(request.canal().toUpperCase().trim());
        venda.setMarca(request.marca().toUpperCase().trim());
        venda.setLoja(request.loja().toUpperCase().trim());
        venda.setDataVenda(request.dataVenda());
        venda.setValorVenda(request.valorVenda());

        try {
            Venda vendaSalva = vendaRepository.save(venda);
            return mapearParaResponse(vendaSalva);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Violação de integridade por concorrência ao registrar venda externa '{}'. Recuperando registro vencedor.", idExternoNormalizado);
            Venda concorrente = vendaRepository.findByIdVendaExterno(idExternoNormalizado)
                    .orElseThrow(() -> ex);
            return tratarVendaExistente(request, concorrente);
        }
    }

    private VendaResponseDTO tratarVendaExistente(VendaRequestDTO request, Venda existente) {
        boolean dadosIguais = existente.getMatricula().equalsIgnoreCase(request.matricula().trim())
                && existente.getValorVenda().compareTo(request.valorVenda()) == 0
                && existente.getDataVenda().equals(request.dataVenda())
                && existente.getCanal().equalsIgnoreCase(request.canal().trim())
                && existente.getMarca().equalsIgnoreCase(request.marca().trim())
                && existente.getLoja().equalsIgnoreCase(request.loja().trim());

        if (!dadosIguais) {
            throw new BusinessException(String.format(
                    "Conflito de duplicidade: Já existe uma venda registrada com o identificador externo '%s' com dados divergentes.",
                    request.idVendaExterno()
            ));
        }

        log.info("Idempotência aplicada para venda '{}': requisição idêntica retornando registro existente.", request.idVendaExterno());
        return mapearParaResponse(existente);
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

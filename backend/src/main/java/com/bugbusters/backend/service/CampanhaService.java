package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaResponse;
import com.bugbusters.backend.dto.campanha.CampanhaResponse.RegraVinculadaDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CampanhaService {

    private final CampanhaRepository campanhaRepository;
    private final RegraRepository regraRepository;

    public CampanhaService(CampanhaRepository campanhaRepository, RegraRepository regraRepository) {
        this.campanhaRepository = campanhaRepository;
        this.regraRepository = regraRepository;
    }

    @Transactional
    public CampanhaResponse criarCampanha(CampanhaRequest request) {
        PeriodoCalculado periodo = calcularEValidarPeriodo(request.dataInicio(), request.dataFim());

        EstadoCampanha estadoInicial = request.estado() != null ? request.estado() : EstadoCampanha.DRAFT;

        Campanha campanha = new Campanha();
        campanha.setTitulo(request.titulo());
        campanha.setTextoOriginal(request.textoOriginal());
        campanha.setDataInicio(periodo.inicio());
        campanha.setDataFim(periodo.fim());
        campanha.setEstado(estadoInicial);

        Campanha campanhaSalva = campanhaRepository.save(campanha);

        if (request.possuiRegras()) {
            List<Regra> salvas = criarRegras(campanhaSalva, request, periodo, estadoInicial);
            return mapearParaResponse(campanhaSalva, salvas);
        }

        Regra regra = new Regra();
        regra.setCampanha(campanhaSalva);
        regra.setNome("Regra - " + request.titulo());
        regra.setCanal(request.canal() != null ? request.canal().toUpperCase().trim() : null);
        regra.setCodMarca(request.codMarca());
        regra.setDescrMarca(request.descrMarca());
        regra.setCodLoja(request.codLoja());
        regra.setCodCargo(request.codCargo());
        regra.setDescriCargo(request.descriCargo());
        regra.setMatricula(request.matricula());
        regra.setTaxa(request.taxa());
        regra.setDataInicio(periodo.inicio());
        regra.setDataFim(periodo.fim());
        regra.setStatus(mapearEstadoParaStatusRegra(estadoInicial));

        Regra regraSalva = regraRepository.save(regra);

        return mapearParaResponse(campanhaSalva, regraSalva);
    }

    @Transactional(readOnly = true)
    public List<CampanhaResponse> listarAtivas() {
        return listar(EstadoCampanha.ATIVA);
    }

    @Transactional(readOnly = true)
    public List<CampanhaResponse> listar(EstadoCampanha estado) {
        List<Campanha> campanhas = (estado != null)
                ? campanhaRepository.findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(estado)
                : campanhaRepository.findAllByRemovidoEmIsNullOrderByCriadoEmDesc();

        return campanhas.stream()
                .map(campanha -> mapearParaResponse(campanha, carregarRegras(campanha.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CampanhaResponse buscarPorId(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));
        
        return mapearParaResponse(campanha, carregarRegras(campanha.getId()));
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));

        campanha.setEstado(novoEstado);
        campanha.setAtualizadoEm(OffsetDateTime.now());
        Campanha campanhaAtualizada = campanhaRepository.save(campanha);

        List<Regra> regrasAtualizadas = carregarRegras(id).stream().map(regra -> {
            regra.setStatus(mapearEstadoParaStatusRegra(novoEstado));
            regra.setAtualizadoEm(OffsetDateTime.now());
            return regraRepository.save(regra);
        }).toList();

        return mapearParaResponse(campanhaAtualizada, regrasAtualizadas);
    }

    @Transactional
    public CampanhaResponse atualizarCampanha(Long id, CampanhaRequest request) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada."));

        PeriodoCalculado periodo = calcularEValidarPeriodo(request.dataInicio(), request.dataFim());

        campanha.setTitulo(request.titulo());
        campanha.setTextoOriginal(request.textoOriginal());
        campanha.setDataInicio(periodo.inicio());
        campanha.setDataFim(periodo.fim());
        if (request.estado() != null) {
            campanha.setEstado(request.estado());
        }
        campanha.setAtualizadoEm(OffsetDateTime.now());

        Campanha campanhaAtualizada = campanhaRepository.save(campanha);

        if (request.possuiRegras()) {
            // Contrato com lista: as regras vigentes são substituídas (remoção lógica) pelas informadas.
            OffsetDateTime agora = OffsetDateTime.now();
            carregarRegras(id).forEach(antiga -> {
                antiga.setRemovidoEm(agora);
                antiga.setStatus(StatusRegra.INATIVA);
                regraRepository.save(antiga);
            });
            EstadoCampanha estadoEfetivo = campanhaAtualizada.getEstado();
            List<Regra> novas = criarRegras(campanhaAtualizada, request, periodo, estadoEfetivo);
            return mapearParaResponse(campanhaAtualizada, novas);
        }

        Regra regra = regraRepository.findByCampanhaIdAndRemovidoEmIsNull(id)
                .orElseGet(() -> {
                    Regra novaRegra = new Regra();
                    novaRegra.setCampanha(campanhaAtualizada);
                    novaRegra.setStatus(mapearEstadoParaStatusRegra(campanhaAtualizada.getEstado()));
                    return novaRegra;
                });

        regra.setNome("Regra - " + request.titulo());
        regra.setCanal(request.canal() != null ? request.canal().toUpperCase().trim() : null);
        regra.setCodMarca(request.codMarca());
        regra.setDescrMarca(request.descrMarca());
        regra.setCodLoja(request.codLoja());
        regra.setCodCargo(request.codCargo());
        regra.setDescriCargo(request.descriCargo());
        regra.setMatricula(request.matricula());
        regra.setTaxa(request.taxa());
        regra.setDataInicio(periodo.inicio());
        regra.setDataFim(periodo.fim());
        if (request.estado() != null) {
            regra.setStatus(mapearEstadoParaStatusRegra(request.estado()));
        }
        regra.setAtualizadoEm(OffsetDateTime.now());
        
        Regra regraAtualizada = regraRepository.save(regra);

        return mapearParaResponse(campanhaAtualizada, regraAtualizada);
    }

    @Transactional
    public void removerLogicamente(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada."));

        OffsetDateTime agora = OffsetDateTime.now();
        campanha.setRemovidoEm(agora);
        campanha.setEstado(EstadoCampanha.CANCELADA);
        campanhaRepository.save(campanha);

        carregarRegras(id).forEach(regra -> {
            regra.setRemovidoEm(agora);
            regra.setStatus(StatusRegra.INATIVA);
            regraRepository.save(regra);
        });
    }

    private StatusRegra mapearEstadoParaStatusRegra(EstadoCampanha estado) {
        if (estado == null) {
            return StatusRegra.DRAFT;
        }
        return switch (estado) {
            case ATIVA -> StatusRegra.ATIVA;
            case DRAFT -> StatusRegra.DRAFT;
            case INATIVA, CONCLUIDA, CANCELADA -> StatusRegra.INATIVA;
        };
    }

    private PeriodoCalculado calcularEValidarPeriodo(LocalDate inicioInformado, LocalDate fimInformado) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = inicioInformado != null ? inicioInformado : hoje;
        LocalDate fim = fimInformado != null ? fimInformado : inicio.plusDays(30);

        if (fim.isBefore(inicio)) {
            throw new BusinessException("Período incoerente: a data final (" + fim + ") não pode ser anterior à data de início (" + inicio + ").");
        }

        return new PeriodoCalculado(inicio, fim);
    }

    /** Regras não removidas da campanha; cai no contrato de uma regra se a consulta em lista vier vazia. */
    private List<Regra> carregarRegras(Long campanhaId) {
        List<Regra> regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanhaId);
        if (regras != null && !regras.isEmpty()) {
            return regras;
        }
        return regraRepository.findByCampanhaIdAndRemovidoEmIsNull(campanhaId).map(List::of).orElse(List.of());
    }

    private List<Regra> criarRegras(Campanha campanha, CampanhaRequest request, PeriodoCalculado periodo, EstadoCampanha estado) {
        List<Regra> salvas = new ArrayList<>();
        int indice = 1;
        for (CampanhaRequest.RegraRequest item : request.regras()) {
            validarFaixa(item);
            Regra regra = new Regra();
            regra.setCampanha(campanha);
            regra.setNome(item.nome() != null && !item.nome().isBlank()
                    ? item.nome().trim()
                    : "Regra " + indice + " - " + request.titulo());
            regra.setCanal(item.canal() != null && !item.canal().isBlank() ? item.canal().toUpperCase().trim() : null);
            regra.setCodMarca(item.codMarca());
            regra.setDescrMarca(item.descrMarca());
            regra.setCodLoja(item.codLoja());
            regra.setCodCargo(item.codCargo());
            regra.setDescriCargo(item.descriCargo());
            regra.setMatricula(item.matricula());
            regra.setTaxa(item.taxa());
            regra.setValorMin(item.valorMin());
            regra.setValorMax(item.valorMax());
            regra.setMinInclusivo(item.minInclusivo() == null || item.minInclusivo());
            regra.setMaxInclusivo(item.maxInclusivo() == null || item.maxInclusivo());
            regra.setBonusPercentual(item.bonusPercentual());
            regra.setBonusValorFixo(item.bonusValorFixo());
            // Todas as regras herdam a vigência da campanha.
            regra.setDataInicio(periodo.inicio());
            regra.setDataFim(periodo.fim());
            regra.setStatus(mapearEstadoParaStatusRegra(estado));
            salvas.add(regraRepository.save(regra));
            indice++;
        }
        return salvas;
    }

    private void validarFaixa(CampanhaRequest.RegraRequest item) {
        if (item.valorMin() != null && item.valorMax() != null) {
            int c = item.valorMin().compareTo(item.valorMax());
            boolean min = item.minInclusivo() == null || item.minInclusivo();
            boolean max = item.maxInclusivo() == null || item.maxInclusivo();
            if (c > 0 || (c == 0 && !(min && max))) {
                throw new BusinessException("Faixa de valor incoerente: o valor mínimo (" + item.valorMin()
                        + ") não pode ser maior que o máximo (" + item.valorMax() + ") nem formar um intervalo vazio.");
            }
        }
    }

    private CampanhaResponse mapearParaResponse(Campanha c, Regra r) {
        return mapearParaResponse(c, r != null ? List.of(r) : List.<Regra>of());
    }

    private CampanhaResponse mapearParaResponse(Campanha c, List<Regra> regras) {
        List<RegraVinculadaDTO> dtos = regras.stream().map(this::mapearRegra).toList();
        return new CampanhaResponse(
                c.getId(),
                c.getTitulo(),
                c.getTextoOriginal(),
                c.getEstado(),
                c.getDataInicio(),
                c.getDataFim(),
                dtos.isEmpty() ? null : dtos.get(0),
                c.getCriadoEm(),
                c.getAtualizadoEm(),
                dtos
        );
    }

    private RegraVinculadaDTO mapearRegra(Regra r) {
        return new RegraVinculadaDTO(
                r.getId(),
                r.getNome(),
                r.getCanal(),
                r.getCodMarca(),
                r.getDescrMarca(),
                r.getCodLoja(),
                r.getCodCargo(),
                r.getDescriCargo(),
                r.getMatricula(),
                r.getTaxa(),
                r.getDataInicio(),
                r.getDataFim(),
                r.getStatus(),
                r.getValorMin(),
                r.getValorMax(),
                r.isMinInclusivo(),
                r.isMaxInclusivo(),
                r.getBonusPercentual(),
                r.getBonusValorFixo()
        );
    }

    private record PeriodoCalculado(LocalDate inicio, LocalDate fim) {}
}
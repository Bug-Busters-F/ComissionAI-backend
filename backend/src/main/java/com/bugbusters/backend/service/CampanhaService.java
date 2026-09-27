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
                .map(campanha -> {
                    Regra regra = regraRepository.findByCampanhaIdAndRemovidoEmIsNull(campanha.getId())
                            .orElse(null);
                    return mapearParaResponse(campanha, regra);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CampanhaResponse buscarPorId(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));
        
        Regra regra = regraRepository.findByCampanhaIdAndRemovidoEmIsNull(campanha.getId())
                .orElse(null);

        return mapearParaResponse(campanha, regra);
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));

        campanha.setEstado(novoEstado);
        campanha.setAtualizadoEm(OffsetDateTime.now());
        Campanha campanhaAtualizada = campanhaRepository.save(campanha);

        Regra regraAtualizada = regraRepository.findByCampanhaIdAndRemovidoEmIsNull(id).map(regra -> {
            regra.setStatus(mapearEstadoParaStatusRegra(novoEstado));
            regra.setAtualizadoEm(OffsetDateTime.now());
            return regraRepository.save(regra);
        }).orElse(null);

        return mapearParaResponse(campanhaAtualizada, regraAtualizada);
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

        regraRepository.findByCampanhaIdAndRemovidoEmIsNull(id).ifPresent(regra -> {
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

    private CampanhaResponse mapearParaResponse(Campanha c, Regra r) {
        RegraVinculadaDTO regraDTO = r != null ? new RegraVinculadaDTO(
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
                r.getStatus()
        ) : null;

        return new CampanhaResponse(
                c.getId(),
                c.getTitulo(),
                c.getTextoOriginal(),
                c.getEstado(),
                c.getDataInicio(),
                c.getDataFim(),
                regraDTO,
                c.getCriadoEm(),
                c.getAtualizadoEm()
        );
    }

    private record PeriodoCalculado(LocalDate inicio, LocalDate fim) {}
}
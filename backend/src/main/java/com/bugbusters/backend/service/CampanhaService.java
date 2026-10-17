package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaResponse;
import com.bugbusters.backend.dto.campanha.CampanhaResponse.RegraVinculadaDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
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
import java.util.*;
import java.util.stream.Collectors;

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

        if (request.regras() == null || request.regras().isEmpty()) {
            throw new BusinessException("A campanha deve conter pelo menos uma regra vinculada.");
        }

        boolean possuiIncompleta = request.regras().stream().anyMatch(this::isRegraIncompleta);

        if (possuiIncompleta && request.estado() == EstadoCampanha.ATIVA) {
            throw new BusinessException("Campanha contendo regras ou propostas incompletas só pode ser salva como rascunho (DRAFT).");
        }

        EstadoCampanha estadoFinal = (request.estado() != null && !possuiIncompleta) 
                ? request.estado() 
                : EstadoCampanha.DRAFT;

        Campanha campanha = new Campanha();
        campanha.setTitulo(request.titulo());
        campanha.setTextoOriginal(request.textoOriginal());
        campanha.setDataInicio(periodo.inicio());
        campanha.setDataFim(periodo.fim());
        campanha.setEstado(estadoFinal);

        Campanha campanhaSalva = campanhaRepository.save(campanha);

        List<Regra> regrasParaSalvar = new ArrayList<>();
        int indice = 1;
        for (RegraItemRequest item : request.regras()) {
            Regra regra = converterParaEntidade(item, campanhaSalva, periodo, estadoFinal, indice++);
            regrasParaSalvar.add(regra);
        }

        List<Regra> regrasSalvas = regraRepository.saveAll(regrasParaSalvar);

        return mapearParaResponse(campanhaSalva, regrasSalvas);
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

        if (campanhas.isEmpty()) {
            return List.of();
        }

        List<Long> campanhaIds = campanhas.stream().map(Campanha::getId).toList();
        List<Regra> todasRegras = regraRepository.findAllByCampanhaIdInAndRemovidoEmIsNull(campanhaIds);

        Map<Long, List<Regra>> regrasPorCampanha = todasRegras.stream()
                .collect(Collectors.groupingBy(r -> r.getCampanha().getId()));

        return campanhas.stream()
                .map(campanha -> {
                    List<Regra> regras = regrasPorCampanha.getOrDefault(campanha.getId(), List.of());
                    return mapearParaResponse(campanha, regras);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CampanhaResponse buscarPorId(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));
        
        List<Regra> regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId());

        return mapearParaResponse(campanha, regras);
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));

        List<Regra> regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(id);

        if (novoEstado == EstadoCampanha.ATIVA) {
            boolean temIncompleta = regras.stream().anyMatch(r -> Boolean.FALSE.equals(r.getCompleta()) || r.getTaxa() == null);
            if (temIncompleta) {
                throw new BusinessException("Não é permitido ativar campanha contendo regras ou propostas incompletas (Campanha ID " + id + "). Conclua todas as regras antes de ativar.");
            }
        }

        campanha.setEstado(novoEstado);
        campanha.setAtualizadoEm(OffsetDateTime.now());
        Campanha campanhaAtualizada = campanhaRepository.save(campanha);

        StatusRegra statusAlvo = mapearEstadoParaStatusRegra(novoEstado);
        OffsetDateTime agora = OffsetDateTime.now();
        for (Regra r : regras) {
            r.setStatus(statusAlvo);
            r.setAtualizadoEm(agora);
        }
        List<Regra> regrasAtualizadas = regraRepository.saveAll(regras);

        return mapearParaResponse(campanhaAtualizada, regrasAtualizadas);
    }

    @Transactional
    public CampanhaResponse atualizarCampanha(Long id, CampanhaRequest request) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada."));

        PeriodoCalculado periodo = calcularEValidarPeriodo(request.dataInicio(), request.dataFim());

        if (request.regras() == null || request.regras().isEmpty()) {
            throw new BusinessException("A campanha deve conter pelo menos uma regra vinculada.");
        }

        boolean possuiIncompleta = request.regras().stream().anyMatch(this::isRegraIncompleta);
        EstadoCampanha estadoDesejado = request.estado() != null ? request.estado() : campanha.getEstado();

        if (possuiIncompleta && estadoDesejado == EstadoCampanha.ATIVA) {
            throw new BusinessException("Campanha contendo regras ou propostas incompletas só pode ser salva como rascunho (DRAFT).");
        }

        campanha.setTitulo(request.titulo());
        campanha.setTextoOriginal(request.textoOriginal());
        campanha.setDataInicio(periodo.inicio());
        campanha.setDataFim(periodo.fim());
        campanha.setEstado(possuiIncompleta ? EstadoCampanha.DRAFT : estadoDesejado);
        campanha.setAtualizadoEm(OffsetDateTime.now());

        Campanha campanhaAtualizada = campanhaRepository.save(campanha);

        // Soft-delete nas regras antigas para substituição transacional atômica
        List<Regra> regrasAntigas = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(id);
        OffsetDateTime agora = OffsetDateTime.now();
        for (Regra antiga : regrasAntigas) {
            antiga.setRemovidoEm(agora);
            antiga.setStatus(StatusRegra.INATIVA);
        }
        regraRepository.saveAll(regrasAntigas);

        // Criar e vincular novas regras com a vigência unificada da campanha
        List<Regra> novasRegras = new ArrayList<>();
        int indice = 1;
        for (RegraItemRequest item : request.regras()) {
            Regra novaRegra = converterParaEntidade(item, campanhaAtualizada, periodo, campanhaAtualizada.getEstado(), indice++);
            novasRegras.add(novaRegra);
        }

        List<Regra> regrasSalvas = regraRepository.saveAll(novasRegras);

        return mapearParaResponse(campanhaAtualizada, regrasSalvas);
    }

    @Transactional
    public void removerLogicamente(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada."));

        OffsetDateTime agora = OffsetDateTime.now();
        campanha.setRemovidoEm(agora);
        campanha.setEstado(EstadoCampanha.CANCELADA);
        campanhaRepository.save(campanha);

        List<Regra> regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(id);
        for (Regra regra : regras) {
            regra.setRemovidoEm(agora);
            regra.setStatus(StatusRegra.INATIVA);
        }
        regraRepository.saveAll(regras);
    }

    private boolean isRegraIncompleta(RegraItemRequest item) {
        if (Boolean.FALSE.equals(item.completa())) {
            return true;
        }
        if (item.taxa() == null) {
            return true;
        }
        return false;
    }

    private Regra converterParaEntidade(RegraItemRequest item, Campanha campanha, PeriodoCalculado periodo, EstadoCampanha estadoCampanha, int indice) {
        Regra r = new Regra();
        r.setCampanha(campanha);

        String nome = (item.nome() != null && !item.nome().isBlank()) 
                ? item.nome() 
                : "Regra " + (item.blocoId() != null ? item.blocoId() : indice) + " - " + campanha.getTitulo();
        r.setNome(nome);

        r.setCanal(item.canal() != null ? item.canal().toUpperCase().trim() : null);
        r.setCodMarca(item.codMarca());
        r.setDescrMarca(item.descrMarca());
        r.setCodLoja(item.codLoja());
        r.setCodCargo(item.codCargo());
        r.setDescriCargo(item.descriCargo());
        r.setMatricula(item.matricula());

        r.setValorMinimo(item.valorMinimo());
        r.setMinInclusivo(item.minInclusivo());
        r.setValorMaximo(item.valorMaximo());
        r.setMaxInclusivo(item.maxInclusivo());

        r.setTipoOperacao(item.tipoOperacao() != null ? item.tipoOperacao() : TipoOperacaoBase.DEFINIR_TAXA);
        r.setValorAjuste(item.valorAjuste());
        r.setTipoBaseReferencia(item.tipoBaseReferencia());
        r.setTaxaBaseConsultada(item.taxaBaseConsultada());
        r.setDescricaoReferencia(item.descricaoReferencia());
        r.setTaxa(item.taxa());

        r.setBlocoId(item.blocoId());
        r.setTrechoOrigem(item.trechoOrigem());
        r.setExplicacao(item.explicacao());
        r.setPythonEquivalente(item.pythonEquivalente());
        if (item.pendencias() != null && !item.pendencias().isEmpty()) {
            r.setPendencias(String.join("; ", item.pendencias()));
        } else {
            r.setPendencias(null);
        }
        r.setCompleta(item.completa() != null ? item.completa() : (item.taxa() != null));

        // Sincronização estrita de vigência com a campanha
        r.setDataInicio(periodo.inicio());
        r.setDataFim(periodo.fim());

        if (Boolean.FALSE.equals(r.getCompleta()) || r.getTaxa() == null) {
            r.setStatus(StatusRegra.DRAFT);
        } else if (item.status() != null) {
            r.setStatus(item.status());
        } else {
            r.setStatus(mapearEstadoParaStatusRegra(estadoCampanha));
        }

        return r;
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

    private CampanhaResponse mapearParaResponse(Campanha c, List<Regra> regras) {
        List<RegraVinculadaDTO> regrasDTO = (regras != null) ? regras.stream().map(r -> new RegraVinculadaDTO(
                r.getId(),
                r.getBlocoId(),
                r.getTrechoOrigem(),
                r.getNome(),
                r.getCanal(),
                r.getCodMarca(),
                r.getDescrMarca(),
                r.getCodLoja(),
                r.getCodCargo(),
                r.getDescriCargo(),
                r.getMatricula(),
                r.getValorMinimo(),
                r.getMinInclusivo(),
                r.getValorMaximo(),
                r.getMaxInclusivo(),
                r.getTipoOperacao(),
                r.getValorAjuste(),
                r.getTipoBaseReferencia(),
                r.getTaxaBaseConsultada(),
                r.getDescricaoReferencia(),
                r.getTaxa(),
                r.getExplicacao(),
                r.getPythonEquivalente(),
                r.getPendencias(),
                r.getCompleta(),
                r.getDataInicio(),
                r.getDataFim(),
                r.getStatus()
        )).toList() : List.of();

        boolean possuiIncompletas = regrasDTO.stream()
                .anyMatch(r -> Boolean.FALSE.equals(r.completa()) || r.taxa() == null);

        return new CampanhaResponse(
                c.getId(),
                c.getTitulo(),
                c.getTextoOriginal(),
                c.getEstado(),
                c.getDataInicio(),
                c.getDataFim(),
                regrasDTO,
                possuiIncompletas,
                c.getCriadoEm(),
                c.getAtualizadoEm()
        );
    }

    private record PeriodoCalculado(LocalDate inicio, LocalDate fim) {}
}
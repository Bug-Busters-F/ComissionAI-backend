package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.AlterarEstadoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaResponse;
import com.bugbusters.backend.dto.campanha.CampanhaResponse.RegraVinculadaDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.simulacao.ConferenciaSimulacaoResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import com.bugbusters.backend.model.StatusVersaoCampanha;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CampanhaService {

    private final CampanhaRepository campanhaRepository;
    private final RegraRepository regraRepository;
    private final CampanhaSimulacaoValidator campanhaSimulacaoValidator;
    private final SimulacaoRepository simulacaoRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;

    @Autowired
    public CampanhaService(CampanhaRepository campanhaRepository,
                           RegraRepository regraRepository,
                           CampanhaSimulacaoValidator campanhaSimulacaoValidator,
                           SimulacaoRepository simulacaoRepository,
                           CampanhaVersaoRepository campanhaVersaoRepository) {
        this.campanhaRepository = campanhaRepository;
        this.regraRepository = regraRepository;
        this.campanhaSimulacaoValidator = campanhaSimulacaoValidator;
        this.simulacaoRepository = simulacaoRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
    }

    public CampanhaService(CampanhaRepository campanhaRepository, RegraRepository regraRepository) {
        this(campanhaRepository, regraRepository, null, null, null);
    }

    @Transactional
    public CampanhaResponse criarCampanha(CampanhaRequest request) {
        PeriodoCalculado periodo = calcularEValidarPeriodo(request.dataInicio(), request.dataFim());

        if (request.regras() == null || request.regras().isEmpty()) {
            throw new BusinessException("A campanha deve conter pelo menos uma regra vinculada.");
        }
        validarFaixas(request.regras());

        boolean possuiIncompleta = request.regras().stream().anyMatch(this::isRegraIncompleta);

        if (possuiIncompleta && request.estado() == EstadoCampanha.ATIVA) {
            throw new BusinessException("Campanha contendo regras ou propostas incompletas só pode ser salva como rascunho (DRAFT).");
        }

        if (request.estado() == EstadoCampanha.ATIVA) {
            throw new BusinessException("Não é permitido ativar uma campanha diretamente na criação. " +
                    "A campanha deve ser salva como rascunho (DRAFT), submetida à simulação sem impedimentos e aceita formalmente antes da ativação.");
        }

        EstadoCampanha estadoFinal = request.estado() != null ? request.estado() : EstadoCampanha.DRAFT;

        Campanha campanha = new Campanha();
        campanha.setTitulo(request.titulo());
        campanha.setTextoOriginal(request.textoOriginal());
        campanha.setDataInicio(periodo.inicio());
        campanha.setDataFim(periodo.fim());
        campanha.setEstado(estadoFinal);

        Campanha campanhaSalva = campanhaRepository.save(campanha);
        CampanhaVersao proposta = criarVersao(campanhaSalva, 1, com.bugbusters.backend.model.StatusVersaoCampanha.PROPOSTA, periodo, request);
        if (campanhaVersaoRepository != null) {
            proposta = campanhaVersaoRepository.save(proposta);
        }
        campanhaSalva.setVersaoProposta(proposta);

        List<Regra> regrasParaSalvar = new ArrayList<>();
        int indice = 1;
        for (RegraItemRequest item : request.regras()) {
            Regra regra = converterParaEntidade(item, campanhaSalva, periodo, estadoFinal, indice++);
            regra.setCampanhaVersao(proposta);
            regrasParaSalvar.add(regra);
        }

        List<Regra> regrasSalvas = regraRepository.saveAll(regrasParaSalvar);

        vincularAceiteExistente(campanhaSalva, request.simulacaoAceitaId());

        campanhaRepository.save(campanhaSalva);
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

        return campanhas.stream()
                .map(campanha -> mapearParaResponse(campanha, regrasDaVersaoExibida(campanha)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CampanhaResponse buscarPorId(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));
        
        List<Regra> regras = regrasDaVersaoExibida(campanha);

        return mapearParaResponse(campanha, regras);
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, AlterarEstadoCampanhaRequest request) {
        if (request == null) {
            return alterarEstado(id, EstadoCampanha.DRAFT, null, null, null);
        }
        return alterarEstado(id, request.estado(), request.simulacaoAceitaId(), request.versaoEsperada());
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado) {
        return alterarEstado(id, novoEstado, null, null, null);
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado, UUID simulacaoAceitaId, String aceitoPor, String motivoAceite) {
        return alterarEstado(id, novoEstado, simulacaoAceitaId, null);
    }

    private CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado, UUID simulacaoAceitaId, Integer versaoEsperada) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));

        vincularAceiteExistente(campanha, simulacaoAceitaId);

        List<Regra> regras = regrasDaVersaoExibida(campanha);

        if (novoEstado == EstadoCampanha.ATIVA) {
            boolean temIncompleta = regras.stream().anyMatch(r -> Boolean.FALSE.equals(r.getCompleta()) || r.getTaxa() == null);
            if (temIncompleta) {
                throw new BusinessException("Não é permitido ativar campanha contendo regras ou propostas incompletas (Campanha ID " + id + "). Conclua todas as regras antes de ativar.");
            }
            if (campanhaSimulacaoValidator != null) {
                Simulacao aceita = campanhaSimulacaoValidator.validarAptaParaAtivacao(campanha);
                if (campanha.getVersaoProposta() != null) {
                    publicarVersaoAceita(campanha, aceita, versaoEsperada);
                    return mapearParaResponse(campanha, regrasDaVersaoExibida(campanha));
                }
            }
        }

        // Uma mudança de estado não publica nem altera regras de uma revisão pendente.
        if (campanha.getVersaoVigente() != null) {
            regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getVersaoVigente().getId());
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
        validarFaixas(request.regras());

        if (request.estado() == EstadoCampanha.ATIVA) {
            throw new BusinessException("Não é permitido salvar alterações diretamente com estado ATIVA. " +
                    "A edição invalida simulações anteriores. Salve como rascunho (DRAFT), execute nova simulação e aprove o aceite.");
        }

        // Uma campanha publicada mantém seus dados e regras vigentes intactos. A edição ocorre em proposta isolada.
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta == null) {
            CampanhaVersao base = campanha.getVersaoVigente();
            proposta = copiarComoProposta(campanha, base);
            proposta = campanhaVersaoRepository.save(proposta);
            campanha.setVersaoProposta(proposta);
        }
        proposta.setTitulo(request.titulo());
        proposta.setTextoOriginal(request.textoOriginal());
        proposta.setDataInicio(periodo.inicio());
        proposta.setDataFim(periodo.fim());

        invalidarAceiteCampanha(campanha, "Aceite invalidado por edição na proposta da campanha (regras ou vigência alteradas).");

        List<Regra> regrasAntigas = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId());
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
            Regra novaRegra = converterParaEntidade(item, campanha, periodo, EstadoCampanha.DRAFT, indice++);
            novaRegra.setCampanhaVersao(proposta);
            novasRegras.add(novaRegra);
        }

        List<Regra> regrasSalvas = regraRepository.saveAll(novasRegras);

        if (campanha.getVersaoVigente() == null) {
            // Campanha ainda não publicada: a proposta é sua representação corrente.
            campanha.setTitulo(request.titulo()); campanha.setTextoOriginal(request.textoOriginal());
            campanha.setDataInicio(periodo.inicio()); campanha.setDataFim(periodo.fim());
            campanha.setEstado(EstadoCampanha.DRAFT);
        }
        campanha.setAtualizadoEm(OffsetDateTime.now());
        campanhaRepository.save(campanha);
        return mapearParaResponse(campanha, regrasSalvas);
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

    /** Faixa com mínimo acima do máximo nunca aceitaria venda nenhuma: é recusada em vez de salva. */
    private void validarFaixas(List<RegraItemRequest> regras) {
        for (RegraItemRequest item : regras) {
            if (item.valorMinimo() != null && item.valorMaximo() != null
                    && item.valorMinimo().compareTo(item.valorMaximo()) > 0) {
                throw new BusinessException("Faixa de valor incoerente na regra '" + (item.nome() != null ? item.nome() : item.blocoId())
                        + "': o valor mínimo (" + item.valorMinimo() + ") é maior que o máximo (" + item.valorMaximo() + ").");
            }
        }
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
        r.setBonusPercentual(item.bonusPercentual());
        r.setBonusValorFixo(item.bonusValorFixo());

        r.setBlocoId(item.blocoId());
        // blocoId é estável entre reinterpretações; para regras sem bloco, cria uma chave permanente.
        r.setIdentificadorLogico(item.blocoId() != null && !item.blocoId().isBlank()
                ? item.blocoId().trim() : UUID.randomUUID().toString());
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

    @Transactional(readOnly = true)
    public ConferenciaSimulacaoResponse conferirSimulacao(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));
        if (campanhaSimulacaoValidator != null) {
            return campanhaSimulacaoValidator.conferirCorrespondencia(campanha);
        }
        return new ConferenciaSimulacaoResponse(
                campanha.getId(), campanha.getTitulo(), campanha.getEstado(), false, null, null, null, null, null, null, null,
                List.of("Validador de simulação não configurado."), "Validador indisponível."
        );
    }

    @Transactional
    public CampanhaResponse ativarCampanha(Long id) {
        return ativarCampanha(id, null);
    }

    @Transactional
    public CampanhaResponse ativarCampanha(Long id, Integer versaoEsperada) {
        return alterarEstado(id, EstadoCampanha.ATIVA, null, versaoEsperada);
    }

    @Transactional
    public void invalidarAceiteCampanha(Campanha campanha, String motivo) {
        if (campanha == null || campanha.getId() == null || simulacaoRepository == null) return;
        var proposta = campanha.getVersaoProposta();
        List<Simulacao> simulacoes = proposta != null
                ? simulacaoRepository.findAllByCampanhaVersaoId(proposta.getId())
                : simulacaoRepository.findAllByCampanhaIdAndStatusAceite(campanha.getId(), StatusAceiteSimulacao.ACEITA);
        OffsetDateTime agora = OffsetDateTime.now();
        for (Simulacao sim : simulacoes) {
            sim.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
            sim.setInvalidadoEm(agora);
            sim.setMotivoInvalidacao(motivo);
            if (campanha.getSimulacaoAceita() != null && Objects.equals(campanha.getSimulacaoAceita().getId(), sim.getId())) {
                campanha.setSimulacaoAceita(null);
            }
        }
        simulacaoRepository.saveAll(simulacoes);
    }

    private void vincularAceiteExistente(Campanha campanha, UUID id) {
        if (id == null || simulacaoRepository == null) return;
        Simulacao sim = simulacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Simulação não encontrada com ID: " + id));
        if (sim.getStatusAceite() != StatusAceiteSimulacao.ACEITA) {
            throw new BusinessException("Registre o aceite explícito da simulação antes de vinculá-la à campanha.");
        }
        if (sim.getCampanha() == null) {
            sim.setCampanhaVersao(campanha.versaoParaRevisao());
        }
        validarAceiteSimulacao(campanha, sim, null);
        sim.setCampanha(campanha);
        simulacaoRepository.save(sim);
        campanha.setSimulacaoAceita(sim);
    }

    public void validarAceiteSimulacao(Campanha campanha, Simulacao simulacao, Integer versaoEsperada) {
        if (campanha.getRemovidoEm() != null) throw new BusinessException("A campanha foi removida.");
        if (simulacao.getStatusAceite() == StatusAceiteSimulacao.INVALIDADA || simulacao.getStatusAceite() == StatusAceiteSimulacao.REJEITADA) {
            throw new BusinessException("A simulação foi invalidada ou rejeitada. Execute uma nova simulação.");
        }
        Integer vigente = campanha.getVersaoVigente() != null ? campanha.getVersaoVigente().getNumero() : null;
        if (versaoEsperada != null && !Objects.equals(versaoEsperada, vigente)) {
            throw new ResourceConflictException("A versão vigente foi alterada. Atualize a proposta antes de confirmar.");
        }
        if (campanhaSimulacaoValidator != null) {
            List<String> divergencias = campanhaSimulacaoValidator.conferirDivergencias(campanha, simulacao);
            if (!divergencias.isEmpty()) {
                throw new BusinessException("A proposta atual da campanha diverge da simulação: " + String.join("; ", divergencias));
            }
        }
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
                r.getStatus(),
                r.getBonusPercentual(),
                r.getBonusValorFixo()
        )).toList() : List.of();

        boolean possuiIncompletas = regrasDTO.stream()
                .anyMatch(r -> Boolean.FALSE.equals(r.completa()) || r.taxa() == null);

        return new CampanhaResponse(
                c.getId(),
                c.tituloParaRevisao(),
                c.textoParaRevisao(),
                c.getEstado(),
                c.inicioParaRevisao(),
                c.fimParaRevisao(),
                regrasDTO,
                possuiIncompletas,
                c.getCriadoEm(),
                c.getAtualizadoEm(),
                c.getSimulacaoAceita() != null ? c.getSimulacaoAceita().getId() : null,
                c.getSimulacaoAceita() != null ? c.getSimulacaoAceita().getStatusAceite() : null,
                c.getVersaoVigente() != null ? c.getVersaoVigente().getNumero() : null,
                c.getVersaoProposta() != null ? c.getVersaoProposta().getNumero() : null
        );
    }

    /** Publica exatamente a proposta usada pela simulação, em uma única transação. */
    @Transactional
    public void publicarVersaoAceita(Campanha campanha, Simulacao simulacao, Integer versaoEsperada) {
        if (campanhaVersaoRepository == null) return;
        if (campanha.getVersaoVigente() != null && versaoEsperada == null) {
            throw new ResourceConflictException("Informe versaoEsperada para publicar uma revisão da campanha.");
        }
        validarAceiteSimulacao(campanha, simulacao, versaoEsperada);
        if (simulacao.getStatusAceite() != StatusAceiteSimulacao.ACEITA || simulacao.getQtdVendasComImpedimento() > 0) {
            throw new BusinessException("A publicação exige uma simulação aceita e sem impedimentos.");
        }
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta == null || simulacao.getCampanhaVersao() == null || !proposta.getId().equals(simulacao.getCampanhaVersao().getId())) {
            throw new BusinessException("A simulação aceita não pertence à proposta atualmente em edição.");
        }
        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId());
        if (regras.isEmpty() || regras.stream().anyMatch(r -> Boolean.FALSE.equals(r.getCompleta()) || r.getTaxa() == null)) {
            throw new BusinessException("Não é possível publicar uma proposta com regras incompletas.");
        }
        OffsetDateTime agora = OffsetDateTime.now();
        CampanhaVersao anterior = campanha.getVersaoVigente();
        if (anterior != null && !anterior.getId().equals(proposta.getId())) {
            List<Regra> regrasAnteriores = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(anterior.getId());
            regrasAnteriores.forEach(r -> { r.setStatus(StatusRegra.INATIVA); r.setAtualizadoEm(agora); });
            regraRepository.saveAll(regrasAnteriores);
        }
        proposta.setStatus(com.bugbusters.backend.model.StatusVersaoCampanha.PUBLICADA);
        proposta.setPublicadoEm(agora);
        proposta.setVersaoAnterior(anterior);
        proposta.setPublicadoPor(simulacao.getAceitoPor() != null && !simulacao.getAceitoPor().isBlank()
                ? simulacao.getAceitoPor() : "aplicacao");
        proposta.setSimulacaoAceita(simulacao);
        for (Regra r : regras) { r.setStatus(StatusRegra.ATIVA); r.setAtualizadoEm(agora); }
        regraRepository.saveAll(regras);
        campanha.setSimulacaoAceita(simulacao);
        campanha.setVersaoVigente(proposta);
        campanha.setVersaoProposta(null);
        campanha.setTitulo(proposta.getTitulo()); campanha.setTextoOriginal(proposta.getTextoOriginal());
        campanha.setDataInicio(proposta.getDataInicio()); campanha.setDataFim(proposta.getDataFim());
        campanha.setEstado(EstadoCampanha.ATIVA); campanha.setAtualizadoEm(agora);
        campanhaVersaoRepository.save(proposta);
        campanhaRepository.saveAndFlush(campanha); // @Version garante compare-and-swap no commit
    }

    @Transactional(readOnly = true)
    public List<com.bugbusters.backend.dto.campanha.VersaoCampanhaResumoResponse> listarHistorico(Long campanhaId) {
        garantirCampanhaExiste(campanhaId);
        return campanhaVersaoRepository.findAllByCampanhaIdOrderByNumeroDesc(campanhaId).stream()
                .filter(v -> v.getStatus() == StatusVersaoCampanha.PUBLICADA)
                .map(v -> new com.bugbusters.backend.dto.campanha.VersaoCampanhaResumoResponse(
                        v.getNumero(), v.getVersaoAnterior() != null ? v.getVersaoAnterior().getNumero() : null,
                        v.getPublicadoPor(), v.getPublicadoEm(),
                        v.getSimulacaoAceita() != null ? v.getSimulacaoAceita().getId() : null,
                        v.getSimulacaoAceita() != null ? v.getSimulacaoAceita().getProtocolo() : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public com.bugbusters.backend.dto.campanha.VersaoCampanhaDetalheResponse buscarVersaoPublicada(Long campanhaId, Integer numero) {
        CampanhaVersao v = obterVersaoPublicada(campanhaId, numero);
        List<com.bugbusters.backend.dto.campanha.VersaoCampanhaDetalheResponse.RegraHistoricaDTO> regras =
                regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(v.getId()).stream()
                        .map(this::mapearRegraHistorica).toList();
        return new com.bugbusters.backend.dto.campanha.VersaoCampanhaDetalheResponse(
                campanhaId, v.getNumero(), v.getVersaoAnterior() != null ? v.getVersaoAnterior().getNumero() : null,
                v.getTitulo(), v.getTextoOriginal(), v.getDataInicio(), v.getDataFim(), v.getPublicadoPor(), v.getPublicadoEm(),
                v.getSimulacaoAceita() != null ? v.getSimulacaoAceita().getId() : null,
                v.getSimulacaoAceita() != null ? v.getSimulacaoAceita().getProtocolo() : null, regras);
    }

    @Transactional(readOnly = true)
    public com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse compararVersoes(Long campanhaId, Integer versaoBase, Integer versaoComparada) {
        CampanhaVersao base = obterVersaoPublicada(campanhaId, versaoBase);
        CampanhaVersao atual = obterVersaoPublicada(campanhaId, versaoComparada);
        List<com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoCampoDTO> campanha = new ArrayList<>();
        adicionarAlteracao(campanha, "titulo", base.getTitulo(), atual.getTitulo());
        adicionarAlteracao(campanha, "textoOriginal", base.getTextoOriginal(), atual.getTextoOriginal());
        adicionarAlteracao(campanha, "dataInicio", base.getDataInicio(), atual.getDataInicio());
        adicionarAlteracao(campanha, "dataFim", base.getDataFim(), atual.getDataFim());
        Map<String, Regra> regrasBase = indexarRegras(base.getId());
        Map<String, Regra> regrasAtuais = indexarRegras(atual.getId());
        Set<String> chaves = new TreeSet<>(); chaves.addAll(regrasBase.keySet()); chaves.addAll(regrasAtuais.keySet());
        List<com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoRegraDTO> regras = new ArrayList<>();
        for (String chave : chaves) regras.add(compararRegra(chave, regrasBase.get(chave), regrasAtuais.get(chave)));
        return new com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse(campanhaId, versaoBase, versaoComparada, campanha, regras);
    }

    @Transactional
    public void cancelarProposta(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada."));
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta == null) return;
        invalidarAceiteCampanha(campanha, "Simulação invalidada pelo cancelamento da proposta.");
        proposta.setStatus(StatusVersaoCampanha.CANCELADA);
        regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId()).forEach(r -> { r.setStatus(StatusRegra.INATIVA); r.setRemovidoEm(OffsetDateTime.now()); });
        campanha.setVersaoProposta(null);
        if (campanha.getVersaoVigente() != null && simulacaoRepository != null) {
            campanha.setSimulacaoAceita(simulacaoRepository.findAllByCampanhaIdAndStatusAceite(id, StatusAceiteSimulacao.ACEITA)
                    .stream().filter(sim -> sim.getCampanhaVersao() != null &&
                            Objects.equals(sim.getCampanhaVersao().getId(), campanha.getVersaoVigente().getId()))
                    .max(Comparator.comparing(Simulacao::getAceitoEm, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null));
        }
        campanhaVersaoRepository.save(proposta); campanhaRepository.save(campanha);
    }

    private CampanhaVersao criarVersao(Campanha c, int numero, com.bugbusters.backend.model.StatusVersaoCampanha status, PeriodoCalculado p, CampanhaRequest r) {
        CampanhaVersao v = new CampanhaVersao(); v.setCampanha(c); v.setNumero(numero); v.setStatus(status);
        v.setTitulo(r.titulo()); v.setTextoOriginal(r.textoOriginal()); v.setDataInicio(p.inicio()); v.setDataFim(p.fim()); return v;
    }
    private CampanhaVersao copiarComoProposta(Campanha c, CampanhaVersao base) {
        int numero = campanhaVersaoRepository.findFirstByCampanhaIdOrderByNumeroDesc(c.getId())
                .map(v -> v.getNumero() + 1).orElse(1);
        CampanhaVersao v = new CampanhaVersao(); v.setCampanha(c); v.setNumero(numero); v.setStatus(StatusVersaoCampanha.PROPOSTA);
        v.setTitulo(base != null ? base.getTitulo() : c.getTitulo());
        v.setTextoOriginal(base != null ? base.getTextoOriginal() : c.getTextoOriginal());
        v.setDataInicio(base != null ? base.getDataInicio() : c.getDataInicio());
        v.setDataFim(base != null ? base.getDataFim() : c.getDataFim()); return v;
    }

    private void garantirCampanhaExiste(Long campanhaId) {
        if (!campanhaRepository.existsById(campanhaId)) {
            throw new ResourceNotFoundException("Campanha com ID " + campanhaId + " não encontrada.");
        }
    }

    private CampanhaVersao obterVersaoPublicada(Long campanhaId, Integer numero) {
        garantirCampanhaExiste(campanhaId);
        CampanhaVersao v = campanhaVersaoRepository.findByCampanhaIdAndNumero(campanhaId, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Versão " + numero + " não encontrada para a campanha " + campanhaId + "."));
        if (v.getStatus() != StatusVersaoCampanha.PUBLICADA) {
            throw new ResourceNotFoundException("A versão " + numero + " não foi publicada e não pertence ao histórico.");
        }
        return v;
    }

    private com.bugbusters.backend.dto.campanha.VersaoCampanhaDetalheResponse.RegraHistoricaDTO mapearRegraHistorica(Regra r) {
        return new com.bugbusters.backend.dto.campanha.VersaoCampanhaDetalheResponse.RegraHistoricaDTO(
                r.getId(), r.getIdentificadorLogico(), r.getBlocoId(), r.getTrechoOrigem(), r.getNome(), r.getCanal(),
                r.getCodMarca(), r.getDescrMarca(), r.getCodLoja(), r.getCodCargo(), r.getDescriCargo(), r.getMatricula(),
                r.getValorMinimo(), r.getMinInclusivo(), r.getValorMaximo(), r.getMaxInclusivo(), r.getTipoOperacao(),
                r.getValorAjuste(), r.getTipoBaseReferencia(), r.getTaxaBaseConsultada(), r.getDescricaoReferencia(),
                r.getTaxa(), r.getExplicacao(), r.getPythonEquivalente(), r.getPendencias(), r.getCompleta(),
                r.getBonusPercentual(), r.getBonusValorFixo());
    }

    private Map<String, Regra> indexarRegras(Long versaoId) {
        return regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versaoId).stream()
                .collect(java.util.stream.Collectors.toMap(Regra::getIdentificadorLogico, r -> r, (a, b) -> a, LinkedHashMap::new));
    }

    private com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoRegraDTO compararRegra(String chave, Regra anterior, Regra atual) {
        if (anterior == null) return new com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoRegraDTO(
                chave, com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.TipoAlteracao.ADICIONADA, List.of());
        if (atual == null) return new com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoRegraDTO(
                chave, com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.TipoAlteracao.REMOVIDA, List.of());
        List<com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoCampoDTO> alteracoes = new ArrayList<>();
        adicionarAlteracao(alteracoes, "blocoId", anterior.getBlocoId(), atual.getBlocoId());
        adicionarAlteracao(alteracoes, "trechoOrigem", anterior.getTrechoOrigem(), atual.getTrechoOrigem());
        adicionarAlteracao(alteracoes, "nome", anterior.getNome(), atual.getNome());
        adicionarAlteracao(alteracoes, "canal", anterior.getCanal(), atual.getCanal());
        adicionarAlteracao(alteracoes, "codMarca", anterior.getCodMarca(), atual.getCodMarca());
        adicionarAlteracao(alteracoes, "descrMarca", anterior.getDescrMarca(), atual.getDescrMarca());
        adicionarAlteracao(alteracoes, "codLoja", anterior.getCodLoja(), atual.getCodLoja());
        adicionarAlteracao(alteracoes, "codCargo", anterior.getCodCargo(), atual.getCodCargo());
        adicionarAlteracao(alteracoes, "descriCargo", anterior.getDescriCargo(), atual.getDescriCargo());
        adicionarAlteracao(alteracoes, "matricula", anterior.getMatricula(), atual.getMatricula());
        adicionarAlteracao(alteracoes, "valorMinimo", anterior.getValorMinimo(), atual.getValorMinimo());
        adicionarAlteracao(alteracoes, "minInclusivo", anterior.getMinInclusivo(), atual.getMinInclusivo());
        adicionarAlteracao(alteracoes, "valorMaximo", anterior.getValorMaximo(), atual.getValorMaximo());
        adicionarAlteracao(alteracoes, "maxInclusivo", anterior.getMaxInclusivo(), atual.getMaxInclusivo());
        adicionarAlteracao(alteracoes, "tipoOperacao", anterior.getTipoOperacao(), atual.getTipoOperacao());
        adicionarAlteracao(alteracoes, "valorAjuste", anterior.getValorAjuste(), atual.getValorAjuste());
        adicionarAlteracao(alteracoes, "tipoBaseReferencia", anterior.getTipoBaseReferencia(), atual.getTipoBaseReferencia());
        adicionarAlteracao(alteracoes, "taxaBaseConsultada", anterior.getTaxaBaseConsultada(), atual.getTaxaBaseConsultada());
        adicionarAlteracao(alteracoes, "descricaoReferencia", anterior.getDescricaoReferencia(), atual.getDescricaoReferencia());
        adicionarAlteracao(alteracoes, "taxa", anterior.getTaxa(), atual.getTaxa());
        adicionarAlteracao(alteracoes, "explicacao", anterior.getExplicacao(), atual.getExplicacao());
        adicionarAlteracao(alteracoes, "pythonEquivalente", anterior.getPythonEquivalente(), atual.getPythonEquivalente());
        adicionarAlteracao(alteracoes, "pendencias", anterior.getPendencias(), atual.getPendencias());
        adicionarAlteracao(alteracoes, "bonusPercentual", anterior.getBonusPercentual(), atual.getBonusPercentual());
        adicionarAlteracao(alteracoes, "bonusValorFixo", anterior.getBonusValorFixo(), atual.getBonusValorFixo());
        return new com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoRegraDTO(chave,
                alteracoes.isEmpty() ? com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.TipoAlteracao.SEM_ALTERACAO
                        : com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.TipoAlteracao.ALTERADA, alteracoes);
    }

    private void adicionarAlteracao(List<com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoCampoDTO> destino,
                                    String campo, Object anterior, Object atual) {
        if (!Objects.equals(anterior, atual)) destino.add(new com.bugbusters.backend.dto.campanha.ComparacaoVersaoResponse.AlteracaoCampoDTO(campo, anterior, atual));
    }

    private List<Regra> regrasDaVersaoExibida(Campanha c) {
        CampanhaVersao v = c.versaoParaRevisao();
        return v != null ? regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(v.getId()) : regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(c.getId());
    }

    private record PeriodoCalculado(LocalDate inicio, LocalDate fim) {}
}

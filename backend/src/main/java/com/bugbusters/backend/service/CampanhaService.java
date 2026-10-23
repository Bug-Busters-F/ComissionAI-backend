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
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
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
import java.util.stream.Collectors;

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

        if (request.simulacaoAceitaId() != null && simulacaoRepository != null) {
            simulacaoRepository.findById(request.simulacaoAceitaId()).ifPresent(sim -> {
                sim.setCampanha(campanhaSalva);
                if (sim.getStatusAceite() != StatusAceiteSimulacao.ACEITA) {
                    sim.setStatusAceite(StatusAceiteSimulacao.ACEITA);
                    sim.setAceitoEm(OffsetDateTime.now());
                    sim.setAceitoPor("gestor");
                    sim.setMotivoAceite("Aceite formal vinculado na criação da campanha");
                }
                simulacaoRepository.save(sim);
                campanhaSalva.setSimulacaoAceita(sim);
                campanhaRepository.save(campanhaSalva);
            });
        }

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
        return alterarEstado(id, request.estado(), request.simulacaoAceitaId(), request.aceitoPor(), request.motivoAceite());
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado) {
        return alterarEstado(id, novoEstado, null, null, null);
    }

    @Transactional
    public CampanhaResponse alterarEstado(Long id, EstadoCampanha novoEstado, UUID simulacaoAceitaId, String aceitoPor, String motivoAceite) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada ou removida."));

        if (simulacaoAceitaId != null && simulacaoRepository != null) {
            simulacaoRepository.findById(simulacaoAceitaId).ifPresent(sim -> {
                sim.setCampanha(campanha);
                if (sim.getStatusAceite() != StatusAceiteSimulacao.ACEITA) {
                    sim.setStatusAceite(StatusAceiteSimulacao.ACEITA);
                    sim.setAceitoEm(OffsetDateTime.now());
                    sim.setAceitoPor(aceitoPor != null && !aceitoPor.isBlank() ? aceitoPor : "gestor");
                    sim.setMotivoAceite(motivoAceite != null ? motivoAceite : "Aceite formal vinculado na alteração de estado");
                }
                simulacaoRepository.save(sim);
                campanha.setSimulacaoAceita(sim);
                campanhaRepository.save(campanha);
            });
        }

        List<Regra> regras = regrasDaVersaoExibida(campanha);

        if (novoEstado == EstadoCampanha.ATIVA) {
            boolean temIncompleta = regras.stream().anyMatch(r -> Boolean.FALSE.equals(r.getCompleta()) || r.getTaxa() == null);
            if (temIncompleta) {
                throw new BusinessException("Não é permitido ativar campanha contendo regras ou propostas incompletas (Campanha ID " + id + "). Conclua todas as regras antes de ativar.");
            }
            if (campanhaSimulacaoValidator != null) {
                campanhaSimulacaoValidator.validarAptaParaAtivacao(campanha);
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
        return alterarEstado(id, EstadoCampanha.ATIVA);
    }

    @Transactional
    public void invalidarAceiteCampanha(Campanha campanha, String motivo) {
        if (campanha == null || campanha.getId() == null) {
            return;
        }

        OffsetDateTime agora = OffsetDateTime.now();
        if (campanha.getSimulacaoAceita() != null) {
            Simulacao sim = campanha.getSimulacaoAceita();
            sim.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
            sim.setInvalidadoEm(agora);
            sim.setMotivoInvalidacao(motivo);
            if (simulacaoRepository != null) {
                simulacaoRepository.save(sim);
            }
            campanha.setSimulacaoAceita(null);
            campanhaRepository.save(campanha);
        }

        if (simulacaoRepository != null) {
            List<Simulacao> ativas = simulacaoRepository.findAllByCampanhaIdAndStatusAceite(
                    campanha.getId(), StatusAceiteSimulacao.ACEITA);
            for (Simulacao s : ativas) {
                s.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
                s.setInvalidadoEm(agora);
                s.setMotivoInvalidacao(motivo);
            }
            if (!ativas.isEmpty()) {
                simulacaoRepository.saveAll(ativas);
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
                c.getTitulo(),
                c.getTextoOriginal(),
                c.getEstado(),
                c.getDataInicio(),
                c.getDataFim(),
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
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta == null || simulacao.getCampanhaVersao() == null || !proposta.getId().equals(simulacao.getCampanhaVersao().getId())) {
            throw new BusinessException("A simulação aceita não pertence à proposta atualmente em edição.");
        }
        Integer vigente = campanha.getVersaoVigente() != null ? campanha.getVersaoVigente().getNumero() : null;
        if (versaoEsperada != null && !Objects.equals(versaoEsperada, vigente)) {
            throw new com.bugbusters.backend.exception.ResourceConflictException("A versão vigente foi alterada por outro usuário. Atualize a proposta antes de publicar.");
        }
        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId());
        if (regras.stream().anyMatch(r -> Boolean.FALSE.equals(r.getCompleta()) || r.getTaxa() == null)) {
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
        for (Regra r : regras) { r.setStatus(StatusRegra.ATIVA); r.setAtualizadoEm(agora); }
        regraRepository.saveAll(regras);
        campanha.setVersaoVigente(proposta);
        campanha.setVersaoProposta(null);
        campanha.setTitulo(proposta.getTitulo()); campanha.setTextoOriginal(proposta.getTextoOriginal());
        campanha.setDataInicio(proposta.getDataInicio()); campanha.setDataFim(proposta.getDataFim());
        campanha.setEstado(EstadoCampanha.ATIVA); campanha.setAtualizadoEm(agora);
        campanhaVersaoRepository.save(proposta);
        campanhaRepository.saveAndFlush(campanha); // @Version garante compare-and-swap no commit
    }

    @Transactional
    public void cancelarProposta(Long id) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + id + " não encontrada."));
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta == null) return;
        proposta.setStatus(com.bugbusters.backend.model.StatusVersaoCampanha.CANCELADA);
        regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId()).forEach(r -> { r.setStatus(StatusRegra.INATIVA); r.setRemovidoEm(OffsetDateTime.now()); });
        campanha.setVersaoProposta(null);
        campanhaVersaoRepository.save(proposta); campanhaRepository.save(campanha);
    }

    private CampanhaVersao criarVersao(Campanha c, int numero, com.bugbusters.backend.model.StatusVersaoCampanha status, PeriodoCalculado p, CampanhaRequest r) {
        CampanhaVersao v = new CampanhaVersao(); v.setCampanha(c); v.setNumero(numero); v.setStatus(status);
        v.setTitulo(r.titulo()); v.setTextoOriginal(r.textoOriginal()); v.setDataInicio(p.inicio()); v.setDataFim(p.fim()); return v;
    }
    private CampanhaVersao copiarComoProposta(Campanha c, CampanhaVersao base) {
        CampanhaVersao v = new CampanhaVersao(); v.setCampanha(c); v.setNumero(base != null ? base.getNumero() + 1 : 1); v.setStatus(com.bugbusters.backend.model.StatusVersaoCampanha.PROPOSTA);
        v.setTitulo(base != null ? base.getTitulo() : c.getTitulo());
        v.setTextoOriginal(base != null ? base.getTextoOriginal() : c.getTextoOriginal());
        v.setDataInicio(base != null ? base.getDataInicio() : c.getDataInicio());
        v.setDataFim(base != null ? base.getDataFim() : c.getDataFim()); return v;
    }
    private List<Regra> regrasDaVersaoExibida(Campanha c) {
        CampanhaVersao v = c.getVersaoProposta() != null ? c.getVersaoProposta() : c.getVersaoVigente();
        return v != null ? regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(v.getId()) : regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(c.getId());
    }

    private record PeriodoCalculado(LocalDate inicio, LocalDate fim) {}
}

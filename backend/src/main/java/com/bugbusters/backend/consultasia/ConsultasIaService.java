package com.bugbusters.backend.consultasia;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;
import com.bugbusters.backend.service.CoberturaBaseService;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.BaseComissaoResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.CatalogoResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.CondicoesDTO;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.ConflitoDTO;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.ItemCatalogoDTO;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.MatriculaResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.PaginaDTO;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.RegrasConcorrentesRequest;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.RegrasConcorrentesResponse;
import com.bugbusters.backend.consultasia.ConsultasIaDTOs.VendaSimulacaoDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Marca;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.sales.SaleSpecification;
import com.bugbusters.backend.service.TaxaComissaoResolver;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

/**
 * Consultas somente leitura usadas pelas ferramentas do agente de IA (Sprint 2).
 * Não calcula valores financeiros nem grava dados.
 */
@Service
@Transactional(readOnly = true)
public class ConsultasIaService {

    static final int TAMANHO_MAXIMO_PAGINA = 1000;
    private static final Long REGRA_BASE_ID = 1L;

    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;
    private final PositionRepository positionRepository;
    private final RegistrationRepository registrationRepository;
    private final CoberturaBaseService coberturaBaseService;
    private final RegraRepository regraRepository;
    private final SaleRepository saleRepository;

    public ConsultasIaService(BrandRepository brandRepository, StoreRepository storeRepository,
                              PositionRepository positionRepository, RegistrationRepository registrationRepository,
                              CoberturaBaseService coberturaBaseService, RegraRepository regraRepository,
                              SaleRepository saleRepository) {
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.positionRepository = positionRepository;
        this.registrationRepository = registrationRepository;
        this.coberturaBaseService = coberturaBaseService;
        this.regraRepository = regraRepository;
        this.saleRepository = saleRepository;
    }

    // ---------------------------------------------------------------- catálogos

    public CatalogoResponse buscarMarcas(String nome, Integer codigo) {
        // Marcas cadastradas no banco + marcas predefinidas do sistema (enum), sem duplicar por código.
        Map<Integer, ItemCatalogoDTO> porCodigo = new LinkedHashMap<>();
        for (Marca marca : Marca.values()) {
            porCodigo.put(marca.getCodigo(), new ItemCatalogoDTO(marca.getCodigo(), marca.getDescricao(), null));
        }
        for (Brand brand : brandRepository.findAll()) {
            if (brand.getCode() != null) {
                porCodigo.put(brand.getCode(), new ItemCatalogoDTO(brand.getCode(), brand.getDescription(), null));
            }
        }
        return filtrar(new ArrayList<>(porCodigo.values()), nome, codigo);
    }

    public CatalogoResponse buscarLojas(String nome, Integer codigo) {
        List<ItemCatalogoDTO> itens = storeRepository.findAll().stream()
                .map((Store s) -> new ItemCatalogoDTO(s.getCode(), s.getDescription(), null))
                .toList();
        return filtrar(itens, nome, codigo);
    }

    public CatalogoResponse buscarCargos(String nome, Integer codigo) {
        List<ItemCatalogoDTO> itens = positionRepository.findAll().stream()
                .map((Position p) -> new ItemCatalogoDTO(p.getCode(), p.getDescription(), null))
                .toList();
        return filtrar(itens, nome, codigo);
    }

    private CatalogoResponse filtrar(List<ItemCatalogoDTO> itens, String nome, Integer codigo) {
        String nomeNormalizado = normalizar(nome);
        List<ItemCatalogoDTO> encontrados = itens.stream()
                .filter(i -> codigo == null || codigo.equals(i.codigo()))
                .filter(i -> nomeNormalizado.isEmpty()
                        || corresponde(normalizar(i.descricao()), nomeNormalizado))
                .sorted(Comparator.comparing(ItemCatalogoDTO::codigo, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new CatalogoResponse(!encontrados.isEmpty(), encontrados);
    }

    // ---------------------------------------------------------------- matrícula

    public MatriculaResponse buscarMatricula(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            throw new BusinessException("A matrícula é obrigatória.");
        }
        Optional<Registration> opt = registrationRepository.findByRegistration(matricula.trim());
        if (opt.isEmpty()) {
            return MatriculaResponse.naoEncontrada();
        }
        Registration r = opt.get();
        return new MatriculaResponse(
                true,
                r.getRegistration(),
                r.getStore() != null ? r.getStore().getCode() : null,
                r.getPosition() != null ? r.getPosition().getCode() : null,
                r.getPosition() != null ? r.getPosition().getDescription() : null,
                null, // o cadastro de RH não relaciona colaborador e marca
                r.getAdmissDate(),
                r.getDemissDate()
        );
    }

    // ---------------------------------------------------------------- bases

    public BaseComissaoResponse buscarBase(Integer codMarca, Integer codCargo, String competencia) {
        if (codMarca == null || codCargo == null) {
            throw new BusinessException("Os parâmetros codMarca e codCargo são obrigatórios.");
        }
        YearMonth ym = parseCompetencia(competencia);
        BaseComissaoResponse naoEncontrada = new BaseComissaoResponse(false, codMarca, codCargo, ym.toString(), null, null);

        Optional<BigDecimal> taxaOpt = coberturaBaseService.obterTaxaBase(codMarca, codCargo, ym);
        if (taxaOpt.isEmpty()) {
            return naoEncontrada;
        }
        return new BaseComissaoResponse(
                true, codMarca, codCargo, ym.toString(),
                taxaOpt.get().setScale(4, java.math.RoundingMode.HALF_UP),
                ym.toString()
        );
    }

    // ---------------------------------------------------------------- concorrência

    public RegrasConcorrentesResponse buscarRegrasConcorrentes(RegrasConcorrentesRequest request) {
        if (request == null || request.dataInicio() == null) {
            throw new BusinessException("A data de início da vigência é obrigatória.");
        }
        if (request.dataFim() != null && request.dataFim().isBefore(request.dataInicio())) {
            throw new BusinessException("A data final não pode ser anterior à data de início.");
        }
        Long ignorar = parseCampanhaId(request.ignorarCampanhaId());
        CondicoesDTO c = request.condicoes() != null ? request.condicoes()
                : new CondicoesDTO(null, null, null, null, null);
        String canal = semPadrao(c.canal());
        String matricula = c.matricula() != null && !c.matricula().isBlank() ? c.matricula().trim() : null;

        List<ConflitoDTO> conflitos = new ArrayList<>();
        for (Regra r : regraRepository.findAllByStatusAndRemovidoEmIsNull(StatusRegra.ATIVA)) {
            if (isRegraBaseTecnica(r)) {
                continue;
            }
            Long campanhaId = r.getCampanha() != null ? r.getCampanha().getId() : null;
            if (ignorar != null && ignorar.equals(campanhaId)) {
                continue;
            }
            if (!periodosSobrepoem(request.dataInicio(), request.dataFim(), r.getDataInicio(), r.getDataFim())) {
                continue;
            }
            if (!compativel(c.codMarca(), r.getCodMarca())
                    || !compativel(c.codLoja(), r.getCodLoja())
                    || !compativel(c.codCargo(), r.getCodCargo())
                    || !compativel(matricula, blankToNull(r.getMatricula()))
                    || !compativelCanal(canal, semPadrao(r.getCanal()))) {
                continue;
            }
            if (!faixasSobrepoem(request, r)) {
                continue;
            }
            conflitos.add(new ConflitoDTO(
                    r.getId(),
                    campanhaId != null ? String.valueOf(campanhaId) : null,
                    r.getTaxa(),
                    TaxaComissaoResolver.especificidade(r.getCodMarca(), r.getCodLoja(), r.getCodCargo(),
                            r.getMatricula(), r.getCanal()),
                    r.getDataInicio(),
                    r.getDataFim()
            ));
        }
        conflitos.sort(Comparator.comparing(ConflitoDTO::idRegra));
        return new RegrasConcorrentesResponse(conflitos);
    }

    private boolean isRegraBaseTecnica(Regra r) {
        return REGRA_BASE_ID.equals(r.getId()) && r.getCampanha() == null;
    }

    /** Dimensão não informada em um dos lados significa "qualquer": pode coincidir com o outro lado. */
    private <T> boolean compativel(T proposta, T existente) {
        return proposta == null || existente == null || proposta.equals(existente);
    }

    private boolean compativelCanal(String proposta, String existente) {
        return proposta == null || existente == null || proposta.equalsIgnoreCase(existente);
    }

    private boolean periodosSobrepoem(LocalDate iniA, LocalDate fimA, LocalDate iniB, LocalDate fimB) {
        boolean aTerminaAntes = fimA != null && iniB != null && fimA.isBefore(iniB);
        boolean bTerminaAntes = fimB != null && iniA != null && fimB.isBefore(iniA);
        return !aTerminaAntes && !bTerminaAntes;
    }

    private boolean faixasSobrepoem(RegrasConcorrentesRequest req, Regra r) {
        if (req.valorMin() == null && req.valorMax() == null) {
            return true; // proposta sem faixa aceita qualquer valor
        }
        if (!r.possuiFaixa()) {
            return true;
        }
        boolean minReq = req.minInclusivo() == null || req.minInclusivo();
        boolean maxReq = req.maxInclusivo() == null || req.maxInclusivo();
        // A termina antes de B começar?
        if (req.valorMax() != null && r.getValorMinimo() != null) {
            int cmp = req.valorMax().compareTo(r.getValorMinimo());
            if (cmp < 0 || (cmp == 0 && !(maxReq && r.minEhInclusivo()))) {
                return false;
            }
        }
        // B termina antes de A começar?
        if (r.getValorMaximo() != null && req.valorMin() != null) {
            int cmp = r.getValorMaximo().compareTo(req.valorMin());
            if (cmp < 0 || (cmp == 0 && !(r.maxEhInclusivo() && minReq))) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- dados de simulação

    public PaginaDTO<VendaSimulacaoDTO> buscarDadosSimulacao(String competencia, Integer marca, Integer loja,
                                                              Integer cargo, String matricula, int page, int size) {
        YearMonth ym = parseCompetencia(competencia);
        if (page < 0) {
            throw new BusinessException("O parâmetro page não pode ser negativo.");
        }
        int tamanho = Math.max(1, Math.min(size, TAMANHO_MAXIMO_PAGINA));

        Page<Sale> pagina = saleRepository.findAll(
                SaleSpecification.comFiltros(ym.atDay(1), ym.atEndOfMonth(), marca, loja, cargo,
                        blankToNull(matricula)),
                PageRequest.of(page, tamanho, Sort.by("saleDate").ascending().and(Sort.by("id").ascending())));

        if (pagina.isEmpty()) {
            return new PaginaDTO<>(false, List.of(), pagina.getNumber(), tamanho, pagina.getTotalPages(), pagina.getTotalElements(), pagina.isLast());
        }

        List<ReferenciaFaltanteDTO> faltantes = coberturaBaseService.verificarCobertura(pagina.getContent(), ym);
        if (!faltantes.isEmpty()) {
            return PaginaDTO.coberturaInsuficiente(pagina.getNumber(), tamanho);
        }

        Function<Sale, VendaSimulacaoDTO> mapper = s -> new VendaSimulacaoDTO(
                s.getId() != null ? s.getId().toString() : null,
                s.getRegistration() != null ? s.getRegistration().getRegistration() : null,
                s.getStore() != null ? s.getStore().getCode() : null,
                s.getBrand() != null ? s.getBrand().getCode() : null,
                s.getRegistration() != null && s.getRegistration().getPosition() != null
                        ? s.getRegistration().getPosition().getCode() : null,
                s.getValue(),
                s.getSaleDate(),
                s.getSaleChannel()
        );
        return new PaginaDTO<>(
                true,
                pagina.getContent().stream().map(mapper).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalPages(),
                pagina.getTotalElements(),
                pagina.isLast()
        );
    }

    // ---------------------------------------------------------------- utilitários

    private YearMonth parseCompetencia(String competencia) {
        if (competencia == null || competencia.isBlank()) {
            throw new BusinessException("A competência é obrigatória (formato YYYY-MM).");
        }
        try {
            return YearMonth.parse(competencia.trim());
        } catch (Exception e) {
            throw new BusinessException("Formato de competência inválido: '" + competencia + "'. Utilize YYYY-MM.");
        }
    }

    private Long parseCampanhaId(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("ignorarCampanhaId inválido: '" + valor + "'.");
        }
    }

    private String semPadrao(String canal) {
        return canal == null || canal.isBlank() || "PADRAO".equalsIgnoreCase(canal.trim()) ? null : canal.trim();
    }

    private String blankToNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * A descrição casa com o nome buscado (ambos normalizados) se o contém, ou se cada palavra buscada, ou seu
     * singular, inicia alguma palavra da descrição: "VENDEDORES" casa com "VENDEDOR LOJA" e "GERENTES DE LOJA"
     * com "GERENTE DE LOJA". Sem isso, todo pedido no plural virava cadastro inexistente em vez de ambiguidade.
     */
    static boolean corresponde(String descricao, String nome) {
        if (descricao.contains(nome)) {
            return true;
        }
        List<String> palavrasDescricao = List.of(descricao.split(" "));
        for (String palavra : nome.split(" ")) {
            List<String> formas = formasSingulares(palavra);
            boolean encontrada = palavrasDescricao.stream().anyMatch(d -> formas.stream().anyMatch(d::startsWith));
            if (!encontrada) {
                return false;
            }
        }
        return true;
    }

    /** A palavra e seus singulares regulares do português: "-ES" (VENDEDORES) e "-S" (GERENTES). */
    private static List<String> formasSingulares(String palavra) {
        List<String> formas = new ArrayList<>(List.of(palavra));
        if (palavra.length() > 4 && palavra.endsWith("ES")) {
            formas.add(palavra.substring(0, palavra.length() - 2));
        }
        if (palavra.length() > 3 && palavra.endsWith("S")) {
            formas.add(palavra.substring(0, palavra.length() - 1));
        }
        return formas;
    }

    /** Maiúsculas, sem acentos e com espaços colapsados. Texto nulo vira vazio. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}

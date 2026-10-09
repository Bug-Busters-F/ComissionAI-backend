package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.model.CatalogoDominioDomRock;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;


/**
 * Serviço centralizado de catálogo de domínio.
 * Consulta prioritariamente as tabelas persistidas no banco de dados (tb_brand, tb_position,
 * tb_store, tb_basecomiss, tb_registration) e provê fallback gracioso para os registros
 * em memória de CatalogoDominioDomRock caso o banco de dados ainda não contenha registros.
 */
@Service
public class DominioCatalogoService {

    private final BrandRepository brandRepository;
    private final PositionRepository positionRepository;
    private final StoreRepository storeRepository;
    private final BaseComissRepository baseComissRepository;
    private final RegistrationRepository registrationRepository;

    public DominioCatalogoService(
            BrandRepository brandRepository,
            PositionRepository positionRepository,
            StoreRepository storeRepository,
            BaseComissRepository baseComissRepository,
            RegistrationRepository registrationRepository) {
        this.brandRepository = brandRepository;
        this.positionRepository = positionRepository;
        this.storeRepository = storeRepository;
        this.baseComissRepository = baseComissRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> obterMarcas() {
        if (brandRepository != null) {
            List<Brand> marcas = brandRepository.findAll();
            if (!marcas.isEmpty()) {
                Map<Integer, String> map = new LinkedHashMap<>();
                for (Brand b : marcas) {
                    if (b.getCode() != null) {
                        map.put(b.getCode(), b.getDescription() != null ? b.getDescription() : "MARCA " + b.getCode());
                    }
                }
                return Collections.unmodifiableMap(map);
            }
        }
        return CatalogoDominioDomRock.MARCAS;
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> obterCargos() {
        if (positionRepository != null) {
            List<Position> cargos = positionRepository.findAll();
            if (!cargos.isEmpty()) {
                Map<Integer, String> map = new LinkedHashMap<>();
                for (Position p : cargos) {
                    if (p.getCode() != null) {
                        map.put(p.getCode(), p.getDescription() != null ? p.getDescription() : "CARGO " + p.getCode());
                    }
                }
                return Collections.unmodifiableMap(map);
            }
        }
        return CatalogoDominioDomRock.CARGOS_POR_CODIGO;
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> obterLojas() {
        if (storeRepository != null) {
            List<Store> lojas = storeRepository.findAll();
            if (!lojas.isEmpty()) {
                Map<Integer, String> map = new LinkedHashMap<>();
                for (Store s : lojas) {
                    if (s.getCode() != null) {
                        map.put(s.getCode(), s.getDescription() != null ? s.getDescription() : "LOJA-" + s.getCode());
                    }
                }
                return Collections.unmodifiableMap(map);
            }
        }
        return CatalogoDominioDomRock.LOJAS;
    }

    public List<String> obterCanais() {
        return CatalogoDominioDomRock.CANAIS;
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> obterTaxaBaseContratual(Integer codMarca, Integer codCargo, String descriCargo) {
        if (codMarca != null && codCargo != null && baseComissRepository != null) {
            Optional<BaseComiss> bcOpt = baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(codMarca, codCargo);
            if (bcOpt.isPresent() && bcOpt.get().getPercentage() != null) {
                return Optional.of(bcOpt.get().getPercentage());
            }
        }
        return CatalogoDominioDomRock.obterTaxaBaseContratual(codMarca, codCargo, descriCargo);
    }

    @Transactional(readOnly = true)
    public BigDecimal obterTaxaBaseComFallback(Integer codMarca, Integer codCargo, String descriCargo) {
        return obterTaxaBaseContratual(codMarca, codCargo, descriCargo)
                .orElse(CatalogoDominioDomRock.TAXA_BASE_FALLBACK_GERAL);
    }

    @Transactional(readOnly = true)
    public String gerarDescricaoReferencia(Integer codMarca, String descrMarca, Integer codCargo, String descriCargo) {
        Optional<BigDecimal> taxaOpt = obterTaxaBaseContratual(codMarca, codCargo, descriCargo);
        if (taxaOpt.isPresent()) {
            String marcaNome = descrMarca != null ? descrMarca : obterMarcas().getOrDefault(codMarca, "Marca " + codMarca);
            String cargoNome = descriCargo != null ? descriCargo : (codCargo != null ? obterCargos().getOrDefault(codCargo, "Cargo " + codCargo) : "Geral");
            BigDecimal taxaPct = taxaOpt.get().multiply(new BigDecimal("100")).stripTrailingZeros();
            return String.format("Taxa Contratual Padrão (Marca %s - %s: %s%%)", marcaNome, cargoNome, taxaPct.toPlainString());
        }
        return "Taxa Contratual Padrão";
    }

    @Transactional(readOnly = true)
    public boolean isLojaValida(Integer codLoja) {
        if (codLoja == null) return false;
        if (storeRepository != null) {
            long total = storeRepository.count();
            if (total > 0) {
                return storeRepository.findByCode(codLoja).isPresent();
            }
        }
        return CatalogoDominioDomRock.isLojaValida(codLoja);
    }

    @Transactional(readOnly = true)
    public boolean isMatriculaValida(String matricula) {
        if (matricula == null || matricula.isBlank()) return false;
        if (registrationRepository != null) {
            long total = registrationRepository.count();
            if (total > 0) {
                return registrationRepository.findByRegistrationIgnoreCase(matricula.trim()).isPresent();
            }
        }
        return CatalogoDominioDomRock.isMatriculaValida(matricula);
    }

    @Transactional(readOnly = true)
    public Optional<String> buscarDescricaoMarca(Integer codigo) {
        if (codigo == null) return Optional.empty();
        if (brandRepository != null) {
            var brandOpt = brandRepository.findByCode(codigo);
            if (brandOpt.isPresent()) {
                return Optional.ofNullable(brandOpt.get().getDescription());
            }
        }
        return Optional.ofNullable(CatalogoDominioDomRock.MARCAS.get(codigo));
    }

    @Transactional(readOnly = true)
    public boolean isMarcaValida(Integer codigo) {
        if (codigo == null) return false;
        if (brandRepository != null && brandRepository.count() > 0) {
            return brandRepository.findByCode(codigo).isPresent();
        }
        return CatalogoDominioDomRock.MARCAS.containsKey(codigo);
    }

    @Transactional(readOnly = true)
    public Optional<String> buscarDescricaoCargo(Integer codigo) {
        if (codigo == null) return Optional.empty();
        if (positionRepository != null) {
            var posOpt = positionRepository.findByCode(codigo);
            if (posOpt.isPresent()) {
                return Optional.ofNullable(posOpt.get().getDescription());
            }
        }
        return Optional.ofNullable(CatalogoDominioDomRock.CARGOS_POR_CODIGO.get(codigo));
    }

    @Transactional(readOnly = true)
    public boolean isCargoValido(Integer codigo) {
        if (codigo == null) return false;
        if (positionRepository != null && positionRepository.count() > 0) {
            return positionRepository.findByCode(codigo).isPresent();
        }
        return CatalogoDominioDomRock.CARGOS_POR_CODIGO.containsKey(codigo);
    }

    @Transactional(readOnly = true)
    public Optional<String> buscarDescricaoLoja(Integer codigo) {
        if (codigo == null) return Optional.empty();
        if (storeRepository != null) {
            var storeOpt = storeRepository.findByCode(codigo);
            if (storeOpt.isPresent()) {
                return Optional.ofNullable(storeOpt.get().getDescription());
            }
        }
        return Optional.ofNullable(CatalogoDominioDomRock.LOJAS.get(codigo));
    }

    @Transactional(readOnly = true)
    public Optional<Registration> buscarMatricula(String matricula) {
        if (matricula == null || matricula.isBlank() || registrationRepository == null) {
            return Optional.empty();
        }
        return registrationRepository.findByRegistrationIgnoreCase(matricula.trim());
    }

    public boolean isCanalValido(String canal) {
        if (canal == null || canal.isBlank() || "PADRAO".equalsIgnoreCase(canal.trim())) {
            return true;
        }
        return obterCanais().stream().anyMatch(c -> c.equalsIgnoreCase(canal.trim()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obterDicionarioDimensoes() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("marcas", obterMarcas());
        dict.put("cargos", obterCargos());
        dict.put("cargos_detalhados", CatalogoDominioDomRock.TODOS_CARGOS_NOMES);
        dict.put("canais", obterCanais());
        dict.put("faixa_lojas", "Lojas 1 a 80 (padrão LOJA-X)");
        dict.put("faixa_matriculas", "MATRIC-1 a MATRIC-600");
        dict.put("competencias_documentadas", CatalogoDominioDomRock.COMPETENCIAS_HISTORICAS);
        return Collections.unmodifiableMap(dict);
    }
}


package com.bugbusters.backend.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catálogo consolidado com todas as informações oficiais conhecidas do cliente (Dom Rock),
 * extraídas das bases de RH, Vendas e Tabela de Comissões Contratuais Padrão (BASE_COMMISS_FINAL.xlsx).
 * 
 * Fornece integridade referencial, validações de limites conhecidos, extração de texto por regex
 * e a tabela oficial de 30 taxas base contratuais por par (Marca, Cargo).
 */
public final class CatalogoDominioDomRock {

    private CatalogoDominioDomRock() {
        // Classe utilitária com constantes imutáveis
    }

    // --- 1. MARCAS CONHECIDAS ---
    public static final Map<Integer, String> MARCAS = Map.of(
            10, "PRETO",
            20, "BRANCO",
            30, "AZUL",
            40, "VERMELHO",
            50, "AMARELO",
            60, "CINZA"
    );

    // --- 2. CARGOS CONHECIDOS ---
    public static final Map<Integer, String> CARGOS_POR_CODIGO = Map.of(
            100, "VENDEDOR LOJA",
            150, "GERENTE DE LOJA",
            200, "VENDEDOR BALCAO",
            300, "ASSISTENTE DE VENDAS"
    );

    public static final List<String> TODOS_CARGOS_NOMES = List.of(
            "VENDEDOR LOJA",
            "GERENTE DE LOJA",
            "GERENTE QUIOSQUE",
            "VENDEDOR BALCAO",
            "ASSISTENTE DE VENDAS"
    );

    // --- 3. LOJAS CONHECIDAS (80 Lojas: 1 a 80) ---
    public static final int TOTAL_LOJAS = 80;
    public static final int MIN_COD_LOJA = 1;
    public static final int MAX_COD_LOJA = 80;

    public static final Map<Integer, String> LOJAS;
    static {
        Map<Integer, String> mapLojas = new LinkedHashMap<>();
        for (int i = MIN_COD_LOJA; i <= MAX_COD_LOJA; i++) {
            mapLojas.put(i, "LOJA-" + i);
        }
        LOJAS = Collections.unmodifiableMap(mapLojas);
    }

    // --- 4. CANAIS CONHECIDOS ---
    public static final List<String> CANAIS = List.of(
            "LOJA_FISICA",
            "BALCAO",
            "QUIOSQUE",
            "ECOMMERCE",
            "APP",
            "PADRAO"
    );

    // --- 5. COMPETÊNCIAS HISTÓRICAS DOCUMENTADAS ---
    public static final List<String> COMPETENCIAS_HISTORICAS = List.of(
            "2025-07",
            "2025-08",
            "2025-09",
            "2025-10",
            "2025-11",
            "2025-12"
    );

    // --- 6. TABELA OFICIAL DE TAXAS CONTRATUAIS PADRÃO (BASE_COMMISS_FINAL.xlsx) ---
    // 30 combinações exatas (6 Marcas x 5 Cargos)
    public static final BigDecimal TAXA_BASE_FALLBACK_GERAL = new BigDecimal("0.0300");

    private static final Map<String, BigDecimal> TABELA_TAXAS_BASE = new LinkedHashMap<>();

    private static void registrarTaxa(int codMarca, String cargoDescricao, String taxaStr) {
        String chave = gerarChaveTaxa(codMarca, cargoDescricao);
        TABELA_TAXAS_BASE.put(chave, new BigDecimal(taxaStr));
    }

    private static String gerarChaveTaxa(int codMarca, String cargoDescricao) {
        String cargoLimpo = Cargo.padronizar(cargoDescricao);
        return codMarca + "_" + cargoLimpo;
    }

    static {
        // Marca 10: PRETO
        registrarTaxa(10, "VENDEDOR LOJA", "0.0250");
        registrarTaxa(10, "GERENTE DE LOJA", "0.0100");
        registrarTaxa(10, "GERENTE QUIOSQUE", "0.0075");
        registrarTaxa(10, "VENDEDOR BALCAO", "0.0200");
        registrarTaxa(10, "ASSISTENTE DE VENDAS", "0.0150");

        // Marca 20: BRANCO
        registrarTaxa(20, "VENDEDOR LOJA", "0.0300");
        registrarTaxa(20, "GERENTE DE LOJA", "0.0150");
        registrarTaxa(20, "GERENTE QUIOSQUE", "0.0125");
        registrarTaxa(20, "VENDEDOR BALCAO", "0.0250");
        registrarTaxa(20, "ASSISTENTE DE VENDAS", "0.0200");

        // Marca 30: AZUL
        registrarTaxa(30, "VENDEDOR LOJA", "0.0200");
        registrarTaxa(30, "GERENTE DE LOJA", "0.0050");
        registrarTaxa(30, "GERENTE QUIOSQUE", "0.0025");
        registrarTaxa(30, "VENDEDOR BALCAO", "0.0150");
        registrarTaxa(30, "ASSISTENTE DE VENDAS", "0.0100");

        // Marca 40: VERMELHO
        registrarTaxa(40, "VENDEDOR LOJA", "0.0350");
        registrarTaxa(40, "GERENTE DE LOJA", "0.0200");
        registrarTaxa(40, "GERENTE QUIOSQUE", "0.0175");
        registrarTaxa(40, "VENDEDOR BALCAO", "0.0300");
        registrarTaxa(40, "ASSISTENTE DE VENDAS", "0.0250");

        // Marca 50: AMARELO
        registrarTaxa(50, "VENDEDOR LOJA", "0.0250");
        registrarTaxa(50, "GERENTE DE LOJA", "0.0100");
        registrarTaxa(50, "GERENTE QUIOSQUE", "0.0075");
        registrarTaxa(50, "VENDEDOR BALCAO", "0.0200");
        registrarTaxa(50, "ASSISTENTE DE VENDAS", "0.0150");

        // Marca 60: CINZA
        registrarTaxa(60, "VENDEDOR LOJA", "0.0300");
        registrarTaxa(60, "GERENTE DE LOJA", "0.0150");
        registrarTaxa(60, "GERENTE QUIOSQUE", "0.0125");
        registrarTaxa(60, "VENDEDOR BALCAO", "0.0250");
        registrarTaxa(60, "ASSISTENTE DE VENDAS", "0.0200");
    }

    /**
     * Consulta a taxa contratual padrão para um par de código de marca e cargo.
     */
    public static Optional<BigDecimal> obterTaxaBaseContratual(Integer codMarca, String descriCargo) {
        if (codMarca == null || descriCargo == null || descriCargo.isBlank()) {
            return Optional.empty();
        }
        var cargoOpt = Cargo.buscarPorDescricao(descriCargo);
        String cargoNome = cargoOpt.map(Cargo::getDescricao).orElse(descriCargo);
        String chave = gerarChaveTaxa(codMarca, cargoNome);
        return Optional.ofNullable(TABELA_TAXAS_BASE.get(chave));
    }

    /**
     * Consulta a taxa contratual padrão considerando código de marca, código de cargo e descrição opcional.
     */
    public static Optional<BigDecimal> obterTaxaBaseContratual(Integer codMarca, Integer codCargo, String descriCargo) {
        if (codMarca == null) {
            return Optional.empty();
        }
        if (descriCargo != null && !descriCargo.isBlank()) {
            Optional<BigDecimal> taxaPorDesc = obterTaxaBaseContratual(codMarca, descriCargo);
            if (taxaPorDesc.isPresent()) {
                return taxaPorDesc;
            }
        }
        if (codCargo != null) {
            // Se for código com função única, busca pelo nome padrão
            String cargoPadrao = CARGOS_POR_CODIGO.get(codCargo);
            if (cargoPadrao != null) {
                return obterTaxaBaseContratual(codMarca, cargoPadrao);
            }
        }
        return Optional.empty();
    }

    /**
     * Retorna a taxa base exata do catálogo ou a taxa fallback geral (0.0300) se não identificada.
     */
    public static BigDecimal obterTaxaBaseComFallback(Integer codMarca, Integer codCargo, String descriCargo) {
        return obterTaxaBaseContratual(codMarca, codCargo, descriCargo).orElse(TAXA_BASE_FALLBACK_GERAL);
    }

    /**
     * Gera uma descrição contratual enriquecida e legível para a referência da base de comissão.
     */
    public static String gerarDescricaoReferencia(Integer codMarca, String descrMarca, Integer codCargo, String descriCargo) {
        Optional<BigDecimal> taxaOpt = obterTaxaBaseContratual(codMarca, codCargo, descriCargo);
        if (taxaOpt.isPresent()) {
            String marcaNome = descrMarca != null ? descrMarca : MARCAS.getOrDefault(codMarca, "Marca " + codMarca);
            String cargoNome = descriCargo != null ? descriCargo : (codCargo != null ? CARGOS_POR_CODIGO.getOrDefault(codCargo, "Cargo " + codCargo) : "Geral");
            BigDecimal taxaPct = taxaOpt.get().multiply(new BigDecimal("100")).stripTrailingZeros();
            return String.format("Taxa Contratual Padrão (Marca %s - %s: %s%%)", marcaNome, cargoNome, taxaPct.toPlainString());
        }
        return "Taxa Contratual Padrão";
    }

    // --- 7. MÉTODOS DE VALIDAÇÃO E EXTRAÇÃO (LOJAS E MATRÍCULAS) ---

    private static final Pattern PATTERN_LOJA = Pattern.compile("(?i)\\b(?:loja[-_\\s]*)([0-9]{1,3})\\b");
    private static final Pattern PATTERN_MATRICULA = Pattern.compile("(?i)\\b(MATRIC-[0-9]{1,4})\\b");

    /**
     * Valida se o código de loja informado está dentro do catálogo ativo de lojas da rede (1 a 80).
     */
    public static boolean isLojaValida(Integer codLoja) {
        return codLoja != null && codLoja >= MIN_COD_LOJA && codLoja <= MAX_COD_LOJA;
    }

    /**
     * Formata o código da loja no padrão oficial (ex: 12 -> "LOJA-12").
     */
    public static String formatarLoja(Integer codLoja) {
        if (codLoja == null) return null;
        return "LOJA-" + codLoja;
    }

    /**
     * Extrai o código da loja de um texto livre (ex: "loja 12", "LOJA-45").
     */
    public static Optional<Integer> extrairCodigoLoja(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN_LOJA.matcher(texto);
        if (matcher.find()) {
            try {
                int cod = Integer.parseInt(matcher.group(1));
                return Optional.of(cod);
            } catch (NumberFormatException ignored) {}
        }
        return Optional.empty();
    }

    /**
     * Valida se a matrícula segue o formato oficial do cliente (MATRIC-<número>).
     */
    public static boolean isMatriculaValida(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return false;
        }
        return matricula.trim().toUpperCase().matches("^MATRIC-[0-9]{1,4}$");
    }

    /**
     * Extrai a matrícula de um colaborador a partir de texto livre (ex: "para o vendedor MATRIC-186").
     */
    public static Optional<String> extrairMatricula(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN_MATRICULA.matcher(texto);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toUpperCase());
        }
        return Optional.empty();
    }

    // --- 8. DICIONÁRIO DE ENRIQUECIMENTO DE CONTEXTO PARA IA ---

    /**
     * Constrói o dicionário de dimensões do cliente para injetar no contexto das chamadas de IA.
     */
    public static Map<String, Object> obterDicionarioDimensoes() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("marcas", MARCAS);
        dict.put("cargos", CARGOS_POR_CODIGO);
        dict.put("cargos_detalhados", TODOS_CARGOS_NOMES);
        dict.put("canais", CANAIS);
        dict.put("faixa_lojas", "Lojas 1 a 80 (padrão LOJA-X)");
        dict.put("faixa_matriculas", "MATRIC-1 a MATRIC-600");
        dict.put("competencias_documentadas", COMPETENCIAS_HISTORICAS);
        return Collections.unmodifiableMap(dict);
    }
}

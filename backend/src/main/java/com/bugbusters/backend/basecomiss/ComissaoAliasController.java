package com.bugbusters.backend.basecomiss;

import com.bugbusters.backend.dto.TotalExcluidoResponseDTO;
import com.bugbusters.backend.dto.basecomiss.BaseComissResponseDTO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller alias com anotação @Hidden para manter compatibilidade com clientes HTTP
 * que utilizam a rota /api/v1/comissoes (como /api/v1/comissoes/todas e /api/v1/comissoes/{id}),
 * sem poluir a documentação do Swagger com endpoints duplicados.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/comissoes")
public class ComissaoAliasController {

    private final BaseComissService service;

    public ComissaoAliasController(BaseComissService service) {
        this.service = service;
    }

    @GetMapping
    public Page<BaseComissResponseDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer codMarca,
            @RequestParam(required = false) Integer codCargo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {
        Pageable pageable = PageRequest.of(page, size);
        return service.listar(pageable, codMarca, codCargo, mesReferencia);
    }

    @GetMapping("/taxa-padrao")
    public ResponseEntity<BaseComissResponseDTO> consultarTaxaPadrao(
            @RequestParam Integer codMarca,
            @RequestParam Integer codCargo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {
        return service.consultarTaxaBase(codMarca, codCargo, mesReferencia)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/todas")
    public ResponseEntity<TotalExcluidoResponseDTO> deletarTodas() {
        long total = service.deletarTodas();
        return ResponseEntity.ok(new TotalExcluidoResponseDTO(total));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package br.com.claytonfraga.radarvagas.vaga;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vagas")
public class VagaController {

    private final VagaService service;

    public VagaController(VagaService service) {
        this.service = service;
    }

    /**
     * GET /vagas?modalidade=remoto&senioridade=junior&q=java
     *
     * Todos os parametros sao opcionais - sem nenhum, devolve tudo que nao expirou.
     */
    @GetMapping
    public List<Vaga> listar(@RequestParam(required = false) String modalidade,
                             @RequestParam(required = false) String senioridade,
                             @RequestParam(required = false) String q) {
        return service.buscar(modalidade, senioridade, q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vaga> porId(@PathVariable String id) {
        return service.buscar(null, null, null).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        // TODO (Clayton): isto e propositalmente ineficiente - varre tudo em memoria.
        // Troque por repository.findById(id) e observe a diferenca no log de SQL
        // (spring.jpa.show-sql=true). E um exercicio de 5 minutos que voce vai
        // saber explicar numa entrevista.
    }
}

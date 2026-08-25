package br.com.claytonfraga.radarvagas.vaga;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VagaService {

    private final VagaRepository repository;

    public VagaService(VagaRepository repository) {
        this.repository = repository;
    }

    /**
     * Normaliza os parametros vindos da web (string vazia vira null) e delega ao repositorio.
     * Manter essa normalizacao aqui, e nao no controller, e o que permite testar o
     * comportamento sem subir o Spring MVC inteiro.
     */
    public List<Vaga> buscar(String modalidade, String senioridade, String termo) {
        return repository.buscar(vazioParaNulo(modalidade),
                                 vazioParaNulo(senioridade),
                                 vazioParaNulo(termo));
    }

    static String vazioParaNulo(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * TODO (Clayton) - semana 2, e o coracao do projeto:
     *
     * Portar para ca as duas regras que hoje moram na cabeca do agente mapeador:
     *
     *   int scoreDeRisco(Vaga v)      -> rubrica de fraude 0-100 (Etapa 6 do mapeador-vagas.md)
     *   Senioridade senioridadeReal(Vaga v) -> detector de junior disfarcado (Etapa 5)
     *
     * Escreva o teste ANTES da implementacao: pegue uma descricao real de
     * ~/Documents/vagas/2026-08-18/ que voce ja sabe que e junior disfarcado,
     * e assegure que o metodo devolve JUNIOR_DISFARCADO. Depois faca passar.
     *
     * Esses dois metodos sao o que transforma "mais um CRUD" em projeto com
     * regra de negocio de verdade - e sao a melhor historia da sua entrevista.
     */
}

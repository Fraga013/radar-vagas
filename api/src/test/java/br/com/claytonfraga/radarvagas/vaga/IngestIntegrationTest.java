package br.com.claytonfraga.radarvagas.vaga;

import br.com.claytonfraga.radarvagas.ingest.IngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integracao da cadeia inteira: JSON do coletor -> ingestao -> JPA ->
 * query com filtro. Roda em H2, entao nao precisa de Docker nem Postgres.
 *
 * A massa de teste (vagas-exemplo.json) sao duas vagas REAIS coletadas em
 * 24/08/2026 - uma remota junior e uma presencial. Usar dado real em vez de
 * inventado e o que faz o teste pegar problema de verdade: formato de data,
 * acento, descricao com quebra de linha.
 */
@SpringBootTest
class IngestIntegrationTest {

    @Autowired
    private IngestService ingestService;

    @Autowired
    private VagaRepository repository;

    @Autowired
    private VagaService service;

    private Path fixture() throws Exception {
        return new ClassPathResource("vagas-exemplo.json").getFile().toPath();
    }

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("ingere o JSON do coletor e persiste as duas vagas")
    void ingereJsonDoColetor() throws Exception {
        int gravadas = ingestService.ingerir(fixture());

        assertEquals(2, gravadas);
        assertEquals(2, repository.count());
    }

    @Test
    @DisplayName("ingerir duas vezes nao duplica: o id da origem e a chave primaria")
    void ingestaoEhIdempotente() throws Exception {
        ingestService.ingerir(fixture());
        ingestService.ingerir(fixture());

        assertEquals(2, repository.count(), "rodar o coletor duas vezes nao pode duplicar vaga");
    }

    @Test
    @DisplayName("o filtro de modalidade devolve so a vaga remota")
    void filtraPorModalidade() throws Exception {
        ingestService.ingerir(fixture());

        List<Vaga> remotas = service.buscar("remoto", null, null);

        assertEquals(1, remotas.size());
        assertEquals("remoto", remotas.get(0).getModalidade());
    }

    @Test
    @DisplayName("REGRESSAO: busca por termo funciona (parametro nulo exige cast no Postgres)")
    void buscaPorTermoNaoQuebraNoPostgres() throws Exception {
        ingestService.ingerir(fixture());

        // Exercita o `lower(concat('%', :termo, '%'))` E os outros dois
        // parametros nulos na mesma query. Sem `cast(:param as String)`, o
        // PostgreSQL nao consegue inferir o tipo do bind nulo, assume bytea e
        // quebra com "function lower(bytea) does not exist". O H2 aceita.
        List<Vaga> achadas = service.buscar(null, null, "desenvolvedor");

        assertEquals(1, achadas.size());
        assertTrue(achadas.get(0).getTitulo().toLowerCase().contains("desenvolvedor"));
    }

    @Test
    @DisplayName("REGRESSAO: descricao e legivel fora de transacao (nao pode ser @Lob no Postgres)")
    void descricaoLegivelForaDeTransacao() throws Exception {
        ingestService.ingerir(fixture());

        // Aqui a entidade ja voltou destacada da sessao - e exatamente o que
        // acontece quando o controller serializa a resposta em JSON. Com @Lob,
        // o PostgreSQL trata a coluna como large object (oid) e a leitura
        // estoura com "Objetos Grandes nao podem ser usados em auto-commit".
        List<Vaga> todas = service.buscar(null, null, null);

        String descricao = todas.get(0).getDescricao();
        assertNotNull(descricao, "a massa de teste precisa ter descricao, senao este teste nao prova nada");
        assertFalse(descricao.isBlank());
    }

    @Test
    @DisplayName("sem filtro nenhum, devolve tudo que nao expirou")
    void semFiltroDevolveTudo() throws Exception {
        ingestService.ingerir(fixture());

        assertEquals(2, service.buscar(null, null, null).size());
        assertEquals(2, service.buscar("", "  ", "").size(), "string em branco tem de ser tratada como ausente");
    }
}

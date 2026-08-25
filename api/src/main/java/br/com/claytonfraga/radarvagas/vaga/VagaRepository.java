package br.com.claytonfraga.radarvagas.vaga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VagaRepository extends JpaRepository<Vaga, String> {

    /**
     * Filtro principal da API. Cada parametro e opcional: quando vem null,
     * aquela condicao e ignorada (o `:param is null or ...`).
     *
     * POR QUE O `cast(:param as String)`: sem ele o PostgreSQL nao consegue
     * inferir o tipo de um bind parameter nulo, assume `bytea` e a query quebra
     * com "function lower(bytea) does not exist". O H2 usado nos testes e
     * permissivo com tipos e deixa passar - este bug so apareceu no primeiro
     * `docker compose up`, contra Postgres de verdade.
     *
     * TODO (Clayton): esta query cobre o basico. Duas evolucoes naturais depois
     * que os testes estiverem verdes:
     *   1. paginacao com Pageable em vez de List
     *   2. busca textual em `descricao` (hoje so olha o titulo)
     */
    @Query("""
        select v from Vaga v
        where (cast(:modalidade as String) is null or v.modalidade = :modalidade)
          and (cast(:senioridade as String) is null or v.senioridadeDeclarada = :senioridade)
          and (cast(:termo as String) is null
               or lower(v.titulo) like lower(concat('%', cast(:termo as String), '%')))
          and v.expirada = false
        order by v.publicadoEm desc nulls last
        """)
    List<Vaga> buscar(@Param("modalidade") String modalidade,
                      @Param("senioridade") String senioridade,
                      @Param("termo") String termo);
}

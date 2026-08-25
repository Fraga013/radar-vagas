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
     * TODO (Clayton): esta query cobre o basico. Duas evolucoes naturais depois
     * que os testes estiverem verdes:
     *   1. paginacao com Pageable em vez de List
     *   2. busca textual em `descricao` (hoje so olha o titulo)
     */
    @Query("""
        select v from Vaga v
        where (:modalidade is null or v.modalidade = :modalidade)
          and (:senioridade is null or v.senioridadeDeclarada = :senioridade)
          and (:termo is null or lower(v.titulo) like lower(concat('%', :termo, '%')))
          and v.expirada = false
        order by v.publicadoEm desc nulls last
        """)
    List<Vaga> buscar(@Param("modalidade") String modalidade,
                      @Param("senioridade") String senioridade,
                      @Param("termo") String termo);
}

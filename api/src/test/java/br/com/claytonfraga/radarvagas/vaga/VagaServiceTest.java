package br.com.claytonfraga.radarvagas.vaga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Teste de exemplo - rapido, sem Spring, sem banco.
 *
 * Serve de molde para os testes que voce vai escrever na semana 2.
 * Padrao: nome do metodo descreve o comportamento, e o corpo tem
 * as tres partes visiveis (arrange / act / assert).
 */
class VagaServiceTest {

    @Test
    @DisplayName("string em branco vira null para a query ignorar o filtro")
    void brancoViraNulo() {
        assertNull(VagaService.vazioParaNulo(""));
        assertNull(VagaService.vazioParaNulo("   "));
        assertNull(VagaService.vazioParaNulo(null));
    }

    @Test
    @DisplayName("valor preenchido e mantido, sem espaco nas pontas")
    void valorPreenchidoSobrevive() {
        assertEquals("remoto", VagaService.vazioParaNulo("  remoto  "));
    }

    // TODO (Clayton) - semana 2. Escreva estes antes de implementar:
    //
    // @Test void vagaComSeteAnosDeExperienciaEhJuniorDisfarcado() { ... }
    // @Test void vagaQuePedeCpfAntesDaEntrevistaSomaTrintaDeRisco() { ... }
    //
    // Use descricao real de ~/Documents/vagas/2026-08-18/ como massa de teste.
    // Teste escrito antes da implementacao e o que se chama TDD - e vale como
    // resposta quando perguntarem "voce ja praticou TDD?".
}

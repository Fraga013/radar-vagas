package br.com.claytonfraga.radarvagas.ingest;

import br.com.claytonfraga.radarvagas.vaga.Vaga;
import br.com.claytonfraga.radarvagas.vaga.VagaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Le o JSON produzido pelo coletor Python e persiste no Postgres.
 *
 * Por que o coletor continuou em Python em vez de virar Java: ele ja existia,
 * funcionava e estava testado contra tres plataformas com quirks conhecidos
 * (geoId do LinkedIn, jobName da Gupy, selos do Programathor). Reescrever
 * seria retrabalho sem ganho. Esta decisao esta no README - e o tipo de
 * trade-off que entrevistador gosta de ouvir justificado.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final VagaRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public IngestService(VagaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int ingerir(Path arquivoJson) throws IOException {
        JsonNode raiz = mapper.readTree(Files.readString(arquivoJson));
        JsonNode vagas = raiz.path("vagas");

        int gravadas = 0;
        for (JsonNode n : vagas) {
            String id = texto(n, "id");
            if (id == null) continue;

            Vaga v = new Vaga(id, texto(n, "fonte"), texto(n, "titulo"));
            v.setEmpresa(texto(n, "empresa"));
            v.setLocal(texto(n, "local"));
            v.setModalidade(texto(n, "modalidade"));
            v.setUrl(texto(n, "url"));
            v.setDescricao(texto(n, "descricao"));
            v.setSalario(texto(n, "salario"));
            v.setSenioridadeDeclarada(texto(n, "senioridade_declarada"));
            v.setExpirada(n.path("expirada").asBoolean(false));
            v.setPublicadoEm(data(texto(n, "publicado_em")));

            repository.save(v);   // save() faz upsert pela PK: ingestao idempotente
            gravadas++;
        }
        log.info("Ingeridas {} vagas de {}", gravadas, arquivoJson.getFileName());
        return gravadas;
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode c = n.get(campo);
        return (c == null || c.isNull() || c.asText().isBlank()) ? null : c.asText();
    }

    private static LocalDate data(String s) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            return null;   // o coletor as vezes devolve data relativa; nao e erro
        }
    }
}

package br.com.claytonfraga.radarvagas.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dispara a ingestao na subida, quando a propriedade `radar.ingest.file` aponta
 * para um JSON do coletor:
 *
 *     java -jar app.jar --radar.ingest.file=/caminho/brutas.json
 *
 * Sem a propriedade, a aplicacao sobe normalmente e nao faz nada - por isso
 * este runner nao atrapalha os testes nem o `docker compose up` do dia a dia.
 *
 * Por que uma propriedade e nao um endpoint POST: receber um caminho do sistema
 * de arquivos por HTTP num repositorio publico e convite a path traversal.
 * A ingestao e uma tarefa de operacao, nao uma rota da API.
 */
@Component
public class IngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final IngestService service;

    public IngestRunner(IngestService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var valores = args.getOptionValues("radar.ingest.file");
        if (valores == null || valores.isEmpty()) {
            return;
        }
        Path arquivo = Path.of(valores.get(0));
        if (!Files.isReadable(arquivo)) {
            log.warn("radar.ingest.file aponta para arquivo ilegivel: {}", arquivo);
            return;
        }
        int n = service.ingerir(arquivo);
        log.info("Ingestao concluida: {} vagas", n);
    }
}

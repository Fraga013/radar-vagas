package br.com.claytonfraga.radarvagas.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dispara a ingestao na subida quando `radar.ingest.file` aponta para um JSON
 * do coletor. Sem a propriedade, a aplicacao sobe e nao faz nada.
 *
 * Ler por @Value e nao por ApplicationArguments e proposital: assim a mesma
 * propriedade funciona nos tres modos, gracas ao relaxed binding do Spring:
 *
 *   linha de comando  java -jar app.jar --radar.ingest.file=/tmp/brutas.json
 *   variavel de ambiente  RADAR_INGEST_FILE=/dados/brutas.json   (Docker)
 *   application.yml   radar.ingest.file: /dados/brutas.json
 *
 * A primeira versao lia so os argumentos e por isso nao funcionava dentro do
 * container - que e exatamente onde ela precisa funcionar.
 *
 * Por que uma propriedade e nao um endpoint POST: receber caminho do sistema
 * de arquivos por rota HTTP publica e convite a path traversal. Ingestao e
 * tarefa de operacao, nao rota de API.
 */
@Component
public class IngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final IngestService service;
    private final String arquivoConfigurado;

    public IngestRunner(IngestService service,
                        @Value("${radar.ingest.file:}") String arquivoConfigurado) {
        this.service = service;
        this.arquivoConfigurado = arquivoConfigurado;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (arquivoConfigurado == null || arquivoConfigurado.isBlank()) {
            return;
        }
        Path arquivo = Path.of(arquivoConfigurado);
        if (!Files.isReadable(arquivo)) {
            log.warn("radar.ingest.file aponta para arquivo ilegivel, ingestao ignorada: {}", arquivo);
            return;
        }
        log.info("Ingerindo {}", arquivo);
        int n = service.ingerir(arquivo);
        log.info("Ingestao concluida: {} vagas", n);
    }
}

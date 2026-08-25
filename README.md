# Radar de Vagas

API que coleta, normaliza e consulta vagas de TI de três plataformas diferentes
(LinkedIn, Gupy e Programathor), com detecção de "júnior disfarçado" e pontuação
de risco de fraude.

> Construí isto para resolver um problema meu: procurar estágio/júnior significava
> abrir três sites por dia, ler descrições longas e descobrir no fim que a vaga
> "júnior" pedia cinco anos de experiência. Hoje uso o Radar todo dia.

**Status:** semana 1 concluída — API sobe, ingere 1068 vagas reais e responde. 8 testes,
verdes em H2 **e** em PostgreSQL.

---

## O problema, em números

Numa coleta real de **1068 vagas** feita em 24/08/2026, cruzando as 336 que tinham
descrição completa:

| Sinal | Frequência |
|---|---|
| Vagas com modalidade **não declarada** | 47% |
| Vagas rotuladas júnior que exigem anos de experiência | maioria da amostra lida |
| Vagas de "banco de talentos" sem vaga real por trás | presentes em todas as fontes |

Ler isso à mão não escala. Daí o projeto.

## Arquitetura

```
┌──────────────┐     JSON normalizado    ┌──────────────┐      ┌────────────┐
│   coletor/   │ ──────────────────────► │     api/     │ ───► │ PostgreSQL │
│   Python     │                         │ Spring Boot  │      │            │
│              │                         │              │ ◄─── │            │
│ LinkedIn     │                         │ GET /vagas   │      └────────────┘
│ Gupy         │                         │ ?modalidade= │
│ Programathor │                         │ &senioridade=│
└──────────────┘                         │ &q=          │
                                         └──────────────┘
```

Três camadas, uma responsabilidade cada: **coletar** (Python), **servir** (Java),
**persistir** (Postgres). Tudo sobe com um comando.

## Como rodar

```bash
docker compose up          # sobe Postgres + API
curl "localhost:8080/vagas?modalidade=remoto&senioridade=junior&q=java"
```

Para carregar dados de uma coleta:

```bash
cd coletor
python3 coletar_vagas.py buscar --termos "java,estagio,junior" --dias 30 > /tmp/brutas.json
java -jar api/target/*.jar --radar.ingest.file=/tmp/brutas.json
```

A ingestão é **idempotente**: rodar o coletor duas vezes no mesmo dia não duplica
nada, porque o id de origem (`linkedin:4456895688`) é a chave primária.

Para rodar só os testes, sem Docker:

```bash
cd api && mvn test
```

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/vagas` | Lista vagas não expiradas. Filtros opcionais: `modalidade`, `senioridade`, `q` |
| `GET` | `/vagas/{id}` | Uma vaga pelo id de origem (ex.: `linkedin:4441555899`) |

## Decisões e trade-offs

**Por que o coletor continuou em Python.** Ele já existia, funcionava e estava
testado contra três plataformas com particularidades que custaram caro para
descobrir: o `geoId=106057199` do LinkedIn (passar `location=Brasil` como texto é
silenciosamente ignorado e devolve vagas dos EUA), o parâmetro `jobName` da Gupy
(que casa com o *nome* da vaga, então termo composto devolve zero) e os selos do
Programathor que ficam dentro do `<h3>` do título. Reescrever em Java seria
retrabalho sem ganho — a integração entre as duas linguagens é um arquivo JSON.

**Por que o id da origem é a chave primária.** O coletor devolve `<fonte>:<chave>`.
Usar isso como PK torna a ingestão idempotente: rodar duas vezes no mesmo dia não
duplica nada, porque `save()` vira upsert. A alternativa — id autoincremento mais
uma coluna única — exigiria uma consulta a mais por vaga sem benefício.

**Por que H2 nos testes — e por que isso não bastou.** Comecei com H2 em memória
porque teste que exige Docker é teste que ninguém roda: `mvn test` leva segundos e
a CI não precisa de serviço externo. Documentei o risco de o H2 não ser Postgres.

**O risco se materializou na primeira subida real, duas vezes seguidas:**

1. `function lower(bytea) does not exist` — o PostgreSQL não infere o tipo de um
   bind parameter nulo, assume `bytea` e o `lower()` quebra. Corrigido com
   `cast(:param as String)` na query.
2. `Objetos Grandes não podem ser usados em auto-commit` — `@Lob` numa `String`
   vira *large object* (`oid`) no Postgres, que só é legível dentro de transação.
   A API quebrava ao serializar a resposta. Corrigido removendo o `@Lob`.

O H2 aceita os dois sem reclamar. Por isso a CI hoje roda **a mesma suíte duas
vezes**: uma em H2 (rápida) e outra contra um Postgres de verdade, em service
container. Os dois casos viraram testes de regressão nomeados — e eu verifiquei
que eles falham quando os bugs são reintroduzidos, porque teste que não pega o
bug que motivou sua existência é decoração.

**Por que `open-in-view: false`.** Com o default do Spring Boot (ligado), a sessão
do Hibernate fica aberta durante a serialização da resposta e consultas podem
disparar sem aparecer no serviço. Desligado, o código falha alto em vez de
esconder N+1 — prefiro descobrir no teste a descobrir em produção.

**Por que `ddl-auto: update`.** É adequado para portfólio e ambiente local, e é
inadequado para produção — em produção seria Flyway, com migração versionada e
revisável. Está assim de propósito e documentado, em vez de escondido.

## Roadmap

- [x] Coletor multi-fonte com normalização e deduplicação
- [x] Análise de tendências reproduzível (`coletor/analise_mercado.py`)
- [x] API REST com filtros — *semana 1*
- [x] Ingestão idempotente do JSON do coletor — *semana 1*
- [x] Teste de integração da cadeia inteira, com massa real — *semana 1*
- [x] Testes de regressão contra Postgres real — *semana 1*
- [ ] CI verde no GitHub Actions — *semana 2*
- [ ] Detector de júnior disfarçado e score de fraude — *semana 2*
- [ ] Painel de tendências do mercado — *semana 4*

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA · PostgreSQL 16 · Maven · JUnit 5 ·
Docker Compose · GitHub Actions · Python 3.12 (stdlib)

---

**Clayton Ribeiro Fraga** — Engenharia de Software, UTFPR Cornélio Procópio
[LinkedIn](https://www.linkedin.com/in/claytonfraga/)

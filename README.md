# Radar de Vagas

API que coleta, normaliza e consulta vagas de TI de três plataformas diferentes
(LinkedIn, Gupy e Programathor), com detecção de "júnior disfarçado" e pontuação
de risco de fraude.

> Construí isto para resolver um problema meu: procurar estágio/júnior significava
> abrir três sites por dia, ler descrições longas e descobrir no fim que a vaga
> "júnior" pedia cinco anos de experiência. Hoje uso o Radar todo dia.

**Status:** em construção — semana 1 de 3. *(troque esta linha quando avançar)*

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

**Por que H2 nos testes e Postgres em produção.** Testes que exigem Docker rodando
são testes que ninguém roda. Com H2 em memória, `mvn test` leva segundos e a CI do
GitHub não precisa de serviço externo. O risco é real e conhecido: H2 não é
Postgres, então diferenças de SQL só aparecem em integração. Por isso o modo
`MODE=PostgreSQL` e por isso o `docker compose up` continua sendo o teste de
verdade antes de qualquer merge.

**Por que `ddl-auto: update`.** É adequado para portfólio e ambiente local, e é
inadequado para produção — em produção seria Flyway, com migração versionada e
revisável. Está assim de propósito e documentado, em vez de escondido.

## Roadmap

- [x] Coletor multi-fonte com normalização e deduplicação
- [x] Análise de tendências reproduzível (`coletor/analise_mercado.py`)
- [ ] API REST com filtros — *semana 1*
- [ ] Testes JUnit + CI no GitHub Actions — *semana 2*
- [ ] Detector de júnior disfarçado e score de fraude — *semana 2*
- [ ] Painel de tendências do mercado — *semana 4*

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA · PostgreSQL 16 · Maven · JUnit 5 ·
Docker Compose · GitHub Actions · Python 3.12 (stdlib)

---

**Clayton Ribeiro Fraga** — Engenharia de Software, UTFPR Cornélio Procópio
[LinkedIn](https://www.linkedin.com/in/claytonfraga/)

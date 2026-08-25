# Coletor

Raspa LinkedIn, Gupy e Programathor e devolve um JSON normalizado no mesmo schema
para as três fontes, já deduplicado por id.

Só usa a **biblioteca padrão do Python** — nenhuma dependência externa, nenhum pip.
Foi uma restrição do ambiente que virou uma vantagem: roda em qualquer máquina com
Python 3 instalado.

## Uso

```bash
python3 coletar_vagas.py buscar \
  --termos "estagio,junior,desenvolvedor,analista,suporte,dados,java" \
  --fontes linkedin,gupy,programathor \
  --dias 30 --limite 40 > ../dados/brutas.json

python3 coletar_vagas.py detalhar --ids "linkedin:4441555899" > detalhes.json
```

## Schema de saída

`id`, `fonte`, `titulo`, `empresa`, `local`, `modalidade`, `url`, `publicado_em`,
`descricao`, `tags`, `salario`, `senioridade_declarada`, `expirada`

## Particularidades de cada fonte

- **LinkedIn** — API pública de convidado, sem login. `User-Agent` de navegador é
  obrigatório. `geoId=106057199` é o Brasil; passar `location=Brasil` como texto é
  ignorado em silêncio e devolve vagas dos EUA.
- **Gupy** — o parâmetro é `jobName` (`name=` devolve HTTP 400) e casa com o *nome*
  da vaga: `java` devolve 184 resultados, `estagio java` devolve zero. Traz a
  descrição completa já na busca. É LatAm — devolve vaga de Lima e Bogotá.
- **Programathor** — HTML renderizado no servidor. Cada campo do card é um `<span>`
  precedido de um ícone próprio; extrair por ícone, não por posição.

Se uma fonte falhar, ela cai em `.erros` e a varredura continua com as outras.

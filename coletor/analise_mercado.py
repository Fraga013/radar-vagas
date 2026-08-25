#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Analise de tendencias sobre um JSON produzido pelo coletar_vagas.py.

Responde: o que o mercado de estagio/junior de TI esta pedindo?
Os numeros citados no README saem daqui - e sao reproduziveis.

Uso:
    python3 coletar_vagas.py buscar --termos "..." --dias 30 > brutas.json
    python3 analise_mercado.py brutas.json

Semente do "Painel de Tendencias" (semana 4): trocar este print por um
dataset que alimenta um dashboard.
"""
import json
import re
import sys
from collections import Counter

TEMAS = {
    "Metodologias ageis":   r"\bscrum\b|\bkanban\b|\b[áa]gil\b|agile|\bsafe\b",
    "Ingles":               r"ingl[êe]s",
    "Suporte/Helpdesk":     r"help ?desk|suporte t[ée]cnico|\bglpi\b|chamado",
    "IA no desenvolvimento": r"intelig[êe]ncia artificial|copilot|\bllm\b|\bgpt\b|ia generativa",
    "Azure":                r"\bazure\b",
    "CI/CD":                r"github actions|gitlab ci|jenkins|ci/cd|integra[çc][ãa]o cont[íi]nua",
    "Power BI/Dados":       r"power bi|\bqlik\b|\btableau\b|\betl\b|data ?warehouse",
    "APIs REST":            r"\bapi[s]? rest|restful|\bendpoint",
    "SAP/ERP":              r"\bsap\b|\berp\b|protheus|totvs",
    "Testes automatizados": r"teste[s]? automatizado|\bjunit\b|\bmockito\b|\bcypress\b|\bselenium\b|\bjest\b|\btdd\b",
    "Seguranca/LGPD":       r"\blgpd\b|seguran[çc]a da informa[çc][ãa]o|iso 27001",
    "Linux":                r"\blinux\b|\bunix\b|shell script|\bbash\b",
    "Kubernetes":           r"\bkubernetes\b|\bk8s\b",
    "Redes":                r"\bvlan\b|\bfirewall\b|\btcp/ip\b|\bdns\b",
    "Microservicos":        r"microsservi[çc]o|microservi[çc]o|microservice",
    ".NET/C#":              r"\.net\b|\bc#|asp\.net",
    "Kafka":                r"\bkafka\b",
    "Angular":              r"\bangular\b",
}

SENIOR = re.compile(r"\b(pleno|s[êe]nior|senior|especialista|\bsr\b|\bpl\b)\b", re.I)


def main(caminho):
    with open(caminho, encoding="utf-8") as fh:
        vagas = json.load(fh)["vagas"]

    com_desc = [v for v in vagas if (v.get("descricao") or "").strip()]
    alvo = [v for v in com_desc if not SENIOR.search(v.get("titulo") or "")]

    print("coletadas:            %d" % len(vagas))
    print("com descricao:        %d" % len(com_desc))
    print("estagio/junior:       %d  <- base percentual\n" % len(alvo))

    modal = Counter(v.get("modalidade") or "indefinido" for v in vagas)
    print("Modalidade declarada:")
    for m, n in modal.most_common():
        print("  %-12s %5d  %5.1f%%" % (m, n, 100.0 * n / len(vagas)))

    if not alvo:
        return

    cnt = Counter()
    for v in alvo:
        texto = (v.get("descricao") or "") + " " + (v.get("titulo") or "")
        for tema, rx in TEMAS.items():
            if re.search(rx, texto, re.I):
                cnt[tema] += 1

    print("\nO que as vagas de estagio/junior pedem:")
    print("  %-24s %6s %8s" % ("TEMA", "VAGAS", "%"))
    for tema, n in cnt.most_common():
        print("  %-24s %6d %7.1f%%" % (tema, n, 100.0 * n / len(alvo)))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: python3 analise_mercado.py <brutas.json>")
    main(sys.argv[1])

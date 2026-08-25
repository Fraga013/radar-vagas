#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Coletor de vagas — parte determinística do agente `mapeador-vagas`.

Só biblioteca padrão (não há pip neste sistema).

Uso:
    # 1) buscar listas normalizadas
    python3 coletar_vagas.py buscar --termos "java,estagio java,postgresql" \
        --fontes linkedin,gupy,programathor --dias 14 --limite 40 > brutas.json

    # 2) baixar a descrição completa só das que sobreviveram ao pré-filtro
    python3 coletar_vagas.py detalhar --ids linkedin:4447873950,programathor:/jobs/33670-x \
        > detalhes.json

Schema de cada vaga:
    id, fonte, titulo, empresa, local, modalidade, url, publicado_em,
    descricao, tags, salario, senioridade_declarada, expirada
"""

import argparse
import json
import re
import sys
import time
import html as htmllib
import urllib.parse
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone

UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

# geoId do Brasil. Passar `location=Brasil` em texto NÃO funciona:
# o LinkedIn ignora e devolve vagas dos EUA. Verificado em 2026-08-17.
LINKEDIN_GEOID_BRASIL = "106057199"

PAUSA = 1.2  # segundos entre requisições à mesma fonte


# --------------------------------------------------------------------------
# infraestrutura
# --------------------------------------------------------------------------

def buscar_url(url, tentativas=3, timeout=25):
    """GET simples com User-Agent de navegador. Devolve texto ou levanta."""
    ultimo_erro = None
    for n in range(tentativas):
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": UA,
                "Accept-Language": "pt-BR,pt;q=0.9,en;q=0.8",
            })
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read().decode("utf-8", errors="replace")
        except Exception as e:  # noqa: BLE001 — qualquer falha de rede vira retry
            ultimo_erro = e
            if n < tentativas - 1:
                time.sleep(1.5 * (n + 1))
    raise ultimo_erro


def limpar_html(texto):
    """Tira tags e normaliza espaços, preservando quebras de parágrafo."""
    if not texto:
        return ""
    t = re.sub(r"(?is)<(script|style).*?</\1>", " ", texto)
    t = re.sub(r"(?i)<br\s*/?>", "\n", t)
    t = re.sub(r"(?i)</(p|div|li|h[1-6]|tr)>", "\n", t)
    t = re.sub(r"(?i)<li[^>]*>", "- ", t)
    t = re.sub(r"<[^>]+>", " ", t)
    t = htmllib.unescape(t)
    t = t.replace("\xa0", " ")
    linhas = [re.sub(r"[ \t]+", " ", l).strip() for l in t.split("\n")]
    return "\n".join(l for l in linhas if l)


def detectar_modalidade(*textos):
    """remoto | hibrido | presencial | indefinido, a partir de texto livre."""
    alvo = " ".join(t for t in textos if t).lower()
    if re.search(r"h[íi]brid", alvo):
        return "hibrido"
    if re.search(r"\b(remoto|remote|home office|homeoffice|anywhere|100% remoto)\b", alvo):
        return "remoto"
    if re.search(r"\b(presencial|on-?site|no local)\b", alvo):
        return "presencial"
    return "indefinido"


def detectar_senioridade(titulo):
    """Senioridade DECLARADA no título. O júnior disfarçado é o agente que julga."""
    t = (titulo or "").lower()
    if re.search(r"est[áa]gi|intern\b|trainee", t):
        return "estagio"
    if re.search(r"\b(s[êe]nior|senior|sr\.?|especialista|specialist|staff|principal|lead|l[íi]der|iii|iv)\b", t):
        return "senior"
    if re.search(r"\b(pleno|mid-?level|pl\.?|ii)\b", t):
        return "pleno"
    if re.search(r"\b(j[úu]nior|junior|jr\.?|entry|iniciante|\bi\b)\b", t):
        return "junior"
    return "indefinido"


def vaga_vazia(**kw):
    base = {
        "id": None, "fonte": None, "titulo": None, "empresa": None,
        "local": None, "modalidade": "indefinido", "url": None,
        "publicado_em": None, "descricao": "", "tags": [],
        "salario": None, "senioridade_declarada": "indefinido",
        "expirada": False,
    }
    base.update(kw)
    return base


# --------------------------------------------------------------------------
# LinkedIn — API pública de convidado, sem login
# --------------------------------------------------------------------------

def linkedin_buscar(termo, dias, limite):
    """Lista de vagas. A descrição NÃO vem aqui — usar linkedin_detalhar."""
    vagas = []
    segundos = max(1, dias) * 86400
    for inicio in range(0, limite, 10):
        params = {
            "keywords": termo,
            "geoId": LINKEDIN_GEOID_BRASIL,
            "f_E": "1,2",              # 1 = estágio, 2 = entry level
            "f_TPR": f"r{segundos}",
            "start": str(inicio),
        }
        url = ("https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?"
               + urllib.parse.urlencode(params))
        pagina = buscar_url(url)

        cartoes = re.split(r'(?=data-entity-urn="urn:li:jobPosting:)', pagina)
        achou = 0
        for c in cartoes:
            m = re.search(r'urn:li:jobPosting:(\d+)', c)
            if not m:
                continue
            jid = m.group(1)
            titulo = re.search(r'base-search-card__title">\s*(.*?)\s*</h3>', c, re.S)
            empresa = re.search(r'hidden-nested-link"[^>]*>\s*(.*?)\s*</a>', c, re.S)
            local = re.search(r'job-search-card__location">\s*(.*?)\s*</span>', c, re.S)
            data = re.search(r'datetime="([\d-]+)"', c)

            titulo = htmllib.unescape(titulo.group(1)).strip() if titulo else None
            local = htmllib.unescape(local.group(1)).strip() if local else None

            vagas.append(vaga_vazia(
                id=f"linkedin:{jid}",
                fonte="linkedin",
                titulo=titulo,
                empresa=htmllib.unescape(empresa.group(1)).strip() if empresa else None,
                local=local,
                modalidade=detectar_modalidade(titulo, local),
                url=f"https://www.linkedin.com/jobs/view/{jid}",
                publicado_em=data.group(1) if data else None,
                senioridade_declarada=detectar_senioridade(titulo),
            ))
            achou += 1

        if achou == 0:
            break
        time.sleep(PAUSA)
    return vagas[:limite]


def linkedin_detalhar(jid):
    url = f"https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/{jid}"
    pagina = buscar_url(url)
    corpo = re.search(
        r'(?s)(?:show-more-less-html__markup|description__text)[^>]*>(.*?)(?:</section>|<footer)',
        pagina)
    descricao = limpar_html(corpo.group(1) if corpo else pagina)

    # O LinkedIn publica o nível de experiência num campo próprio ("Estágio",
    # "Assistente", "Júnior", "Pleno-sênior"). É mais confiável que o título.
    criterios = re.findall(
        r'(?s)description__job-criteria-subheader">\s*(.*?)\s*</h3>.*?'
        r'description__job-criteria-text[^>]*>\s*(.*?)\s*</span>', pagina)
    tags, nivel = [], None
    for rotulo, valor in criterios:
        valor = htmllib.unescape(re.sub(r"<[^>]+>", "", valor)).strip()
        tags.append(valor)
        if "experi" in rotulo.lower() or "n[ií]vel" in rotulo.lower():
            nivel = valor
    resultado = {"descricao": descricao, "tags": tags}
    if nivel:
        resultado["nivel_declarado_plataforma"] = nivel
    return resultado


# --------------------------------------------------------------------------
# Gupy — API pública, já devolve a descrição completa (nenhuma chamada extra)
# --------------------------------------------------------------------------

GUPY_MODALIDADE = {
    "remote": "remoto", "hybrid": "hibrido",
    "on-site": "presencial", "on_site": "presencial", "onsite": "presencial",
}


def gupy_buscar(termo, dias, limite):
    # ATENÇÃO: o parâmetro é `jobName`. `name=` devolve HTTP 400.
    url = ("https://employability-portal.gupy.io/api/v1/jobs?"
           + urllib.parse.urlencode({"jobName": termo, "limit": limite, "offset": 0}))
    dados = json.loads(buscar_url(url))
    corte = datetime.now(timezone.utc) - timedelta(days=max(1, dias))

    vagas = []
    for j in dados.get("data", []):
        publicado = j.get("publishedDate")
        if publicado:
            try:
                dt_pub = datetime.fromisoformat(publicado.replace("Z", "+00:00"))
                # A Gupy às vezes devolve data sem fuso; sem isto a comparação
                # com `corte` (aware) levanta TypeError e derruba o termo inteiro.
                if dt_pub.tzinfo is None:
                    dt_pub = dt_pub.replace(tzinfo=timezone.utc)
                if dt_pub < corte:
                    continue
            except (ValueError, TypeError):
                pass

        expirada = False
        prazo = j.get("applicationDeadline")
        if prazo:
            try:
                expirada = datetime.fromisoformat(prazo).date() < datetime.now().date()
            except (ValueError, TypeError):
                pass

        pais = j.get("country")
        partes_local = [p for p in [j.get("city"), j.get("state")] if p]
        # A Gupy é LatAm: sem o país no texto, "Lima" passa despercebido pelo filtro geográfico.
        if pais and pais.strip().lower() not in ("brasil", "brazil"):
            partes_local.append(pais)
        local = ", ".join(partes_local) or pais
        modalidade = GUPY_MODALIDADE.get((j.get("workplaceType") or "").lower())
        if not modalidade:
            modalidade = "remoto" if j.get("isRemoteWork") else "indefinido"

        titulo = j.get("name")
        senioridade = detectar_senioridade(titulo)
        # `type` é sinal forte e explícito de estágio — melhor que adivinhar pelo título.
        if j.get("type") == "vacancy_type_internship":
            senioridade = "estagio"

        vagas.append(vaga_vazia(
            id=f"gupy:{j.get('id')}",
            fonte="gupy",
            titulo=titulo,
            empresa=j.get("careerPageName"),
            local=local,
            modalidade=modalidade,
            url=j.get("jobUrl"),
            publicado_em=publicado,
            descricao=limpar_html(j.get("description")),
            tags=j.get("skills") or [],
            senioridade_declarada=senioridade,
            expirada=expirada,
        ))
    return vagas


# --------------------------------------------------------------------------
# Programathor — HTML renderizado no servidor
# --------------------------------------------------------------------------

def programathor_buscar(termo, dias, limite):
    slug = re.sub(r"[^a-z0-9]+", "-", termo.lower()).strip("-")
    url = f"https://programathor.com.br/jobs-{slug}"
    try:
        pagina = buscar_url(url)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return []
        raise

    # Cada campo do card é um <span> precedido por um ícone próprio. Extrair por
    # ícone é estável; dividir o texto por posição não é (cards vencidos e
    # presenciais ganham avisos extras que deslocam tudo).
    ICONES = {
        "briefcase": "empresa",
        "map-marker": "local",
        "building": "porte",
        "chart-bar": "senioridade",
        "file-alt": "contrato",
        "money-bill": "salario",
    }

    vagas = []
    for caminho, bloco in re.findall(r'<a href="(/jobs/[^"]+)"[^>]*>(.*?)</a>', pagina, re.S):
        cabecalho = re.search(r"<h3[^>]*>(.*?)</h3>", bloco, re.S)
        if not cabecalho:
            continue
        cabecalho = cabecalho.group(1)
        # Os selos "Vencida" e "PRESENCIAL - SOMENTE PARA CANDIDATOS NO LOCAL" moram
        # dentro do próprio <h3>; sem removê-los eles grudam no título.
        so_presencial = "presential-only-badge" in cabecalho
        titulo = re.sub(r"(?s)<span.*?</span>", " ", cabecalho)
        titulo = htmllib.unescape(re.sub(r"<[^>]+>", " ", titulo))
        titulo = re.sub(r"\s+", " ", titulo).strip()

        campos = {}
        for icone, valor in re.findall(r"<span><i class='([^']+)'></i>\s*([^<]*)</span>", bloco):
            for chave, nome in ICONES.items():
                if chave in icone:
                    campos[nome] = htmllib.unescape(valor).strip()
                    break

        tags = [htmllib.unescape(t).strip()
                for t in re.findall(r"class='tag-list[^']*'>\s*([^<]*)</span>", bloco)]

        local = campos.get("local")
        senioridade = detectar_senioridade(titulo)
        if senioridade == "indefinido" and campos.get("senioridade"):
            senioridade = detectar_senioridade(campos["senioridade"])

        vagas.append(vaga_vazia(
            id=f"programathor:{caminho}",
            fonte="programathor",
            titulo=titulo,
            empresa=campos.get("empresa"),
            local=local,
            modalidade="presencial" if so_presencial else detectar_modalidade(titulo, local),
            url=f"https://programathor.com.br{caminho}",
            tags=tags,
            salario=campos.get("salario"),
            senioridade_declarada=senioridade,
            expirada=bool(re.search(r">\s*Vencida\s*<", bloco)),
        ))
        if len(vagas) >= limite:
            break
    return vagas


def programathor_detalhar(caminho):
    pagina = buscar_url(f"https://programathor.com.br{caminho}")
    # O container é `wrapper-details-job-show`. Não existe classe "job-description";
    # sem o seletor certo o fallback devolve o menu do site inteiro.
    corpo = re.search(r'(?s)wrapper-details-job-show"?[^>]*>(.*?)(?:<footer|wrapper-content-job-show)',
                      pagina)
    if not corpo:
        corpo = re.search(r'(?s)wrapper-content-job-show"?[^>]*>(.*?)(?:<footer)', pagina)
    descricao = limpar_html(corpo.group(1) if corpo else "")
    # As tags de stack boas vêm do card da listagem; aqui os links /jobs-* são só
    # navegação do site ("Vagas programador PHP"). Por isso não devolvemos tags.
    return {"descricao": descricao}


# --------------------------------------------------------------------------
# orquestração
# --------------------------------------------------------------------------

COLETORES = {
    "linkedin": linkedin_buscar,
    "gupy": gupy_buscar,
    "programathor": programathor_buscar,
}

DETALHADORES = {
    "linkedin": linkedin_detalhar,
    "gupy": None,  # a busca já traz a descrição inteira
    "programathor": programathor_detalhar,
}


def cmd_buscar(args):
    termos = [t.strip() for t in args.termos.split(",") if t.strip()]
    fontes = [f.strip() for f in args.fontes.split(",") if f.strip()]

    vagas, erros, por_fonte = [], [], {}
    for fonte in fontes:
        coletor = COLETORES.get(fonte)
        if not coletor:
            erros.append({"fonte": fonte, "motivo": "fonte desconhecida"})
            continue
        por_fonte[fonte] = 0
        for termo in termos:
            # Uma fonte que cai não derruba a varredura — as outras seguem.
            try:
                achadas = coletor(termo, args.dias, args.limite)
                vagas.extend(achadas)
                por_fonte[fonte] += len(achadas)
            except Exception as e:  # noqa: BLE001
                erros.append({"fonte": fonte, "termo": termo,
                              "motivo": f"{type(e).__name__}: {e}"})
            time.sleep(PAUSA)

    unicas, vistos = [], set()
    for v in vagas:
        if v["id"] not in vistos:
            vistos.add(v["id"])
            unicas.append(v)

    json.dump({
        "gerado_em": datetime.now(timezone.utc).isoformat(),
        "termos": termos, "fontes": fontes, "dias": args.dias,
        "total": len(unicas), "por_fonte": por_fonte,
        "duplicadas_removidas": len(vagas) - len(unicas),
        "erros": erros, "vagas": unicas,
    }, sys.stdout, ensure_ascii=False, indent=2)
    print()


def cmd_detalhar(args):
    resultados, erros = [], []
    for ident in [i.strip() for i in args.ids.split(",") if i.strip()]:
        fonte, _, chave = ident.partition(":")
        detalhador = DETALHADORES.get(fonte)
        if detalhador is None:
            erros.append({"id": ident, "motivo": f"fonte '{fonte}' não precisa/não suporta detalhe"})
            continue
        try:
            r = detalhador(chave)
            r["id"] = ident
            resultados.append(r)
        except Exception as e:  # noqa: BLE001
            erros.append({"id": ident, "motivo": f"{type(e).__name__}: {e}"})
        time.sleep(PAUSA)

    json.dump({"detalhes": resultados, "erros": erros},
              sys.stdout, ensure_ascii=False, indent=2)
    print()


def main():
    p = argparse.ArgumentParser(description="Coletor de vagas (LinkedIn, Gupy, Programathor)")
    sub = p.add_subparsers(dest="comando", required=True)

    b = sub.add_parser("buscar", help="lista normalizada de vagas")
    b.add_argument("--termos", required=True, help="termos separados por vírgula")
    b.add_argument("--fontes", default="linkedin,gupy,programathor")
    b.add_argument("--dias", type=int, default=14)
    b.add_argument("--limite", type=int, default=40, help="máximo por termo por fonte")
    b.set_defaults(func=cmd_buscar)

    d = sub.add_parser("detalhar", help="descrição completa de vagas específicas")
    d.add_argument("--ids", required=True, help="ids separados por vírgula (fonte:chave)")
    d.set_defaults(func=cmd_detalhar)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

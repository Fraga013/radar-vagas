package br.com.claytonfraga.radarvagas.vaga;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Uma vaga coletada. Os campos espelham exatamente o schema que o coletor Python
 * ja produz (coletor/coletar_vagas.py) - nao invente campo novo aqui sem mudar la.
 *
 * O id vem do coletor no formato "<fonte>:<chave>" (ex.: "linkedin:4441555899").
 * Usar o id da origem como chave primaria e o que torna a ingestao idempotente:
 * rodar o coletor duas vezes no mesmo dia nao duplica nada.
 */
@Entity
@Table(name = "vaga")
public class Vaga {

    @Id
    @Column(length = 200)
    private String id;

    @Column(nullable = false, length = 30)
    private String fonte;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(length = 300)
    private String empresa;

    @Column(name = "local_vaga", length = 300)
    private String local;

    /** remoto | hibrido | presencial | indefinido */
    @Column(length = 20)
    private String modalidade;

    @Column(length = 1000)
    private String url;

    @Column(name = "publicado_em")
    private LocalDate publicadoEm;

    /**
     * SEM @Lob de proposito. Com @Lob o PostgreSQL trata a coluna como Large
     * Object (oid), que so pode ser lido dentro de uma transacao explicita - a
     * leitura pela API quebrava com "Objetos Grandes nao podem ser usados no
     * modo auto-commit". O H2 dos testes aceita @Lob sem reclamar, entao o bug
     * so apareceu contra Postgres de verdade.
     *
     * `columnDefinition = "text"` ja da capacidade ilimitada no Postgres, que e
     * tudo que a descricao precisa.
     */
    @Column(columnDefinition = "text")
    private String descricao;

    @Column(length = 200)
    private String salario;

    /** estagio | junior | pleno | senior | indefinido */
    @Column(name = "senioridade_declarada", length = 30)
    private String senioridadeDeclarada;

    @Column(nullable = false)
    private boolean expirada = false;

    // TODO (Clayton): o coletor tambem devolve `tags` (lista de strings, so o
    // Programathor preenche). Modelar como @ElementCollection quando for usar -
    // deixei de fora agora para nao criar tabela que ninguem consulta.


    protected Vaga() {
        // exigido pelo JPA
    }

    public Vaga(String id, String fonte, String titulo) {
        this.id = id;
        this.fonte = fonte;
        this.titulo = titulo;
    }

    public String getId() { return id; }
    public String getFonte() { return fonte; }
    public String getTitulo() { return titulo; }
    public String getEmpresa() { return empresa; }
    public String getLocal() { return local; }
    public String getModalidade() { return modalidade; }
    public String getUrl() { return url; }
    public LocalDate getPublicadoEm() { return publicadoEm; }
    public String getDescricao() { return descricao; }
    public String getSalario() { return salario; }
    public String getSenioridadeDeclarada() { return senioridadeDeclarada; }
    public boolean isExpirada() { return expirada; }

    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public void setLocal(String local) { this.local = local; }
    public void setModalidade(String modalidade) { this.modalidade = modalidade; }
    public void setUrl(String url) { this.url = url; }
    public void setPublicadoEm(LocalDate publicadoEm) { this.publicadoEm = publicadoEm; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setSalario(String salario) { this.salario = salario; }
    public void setSenioridadeDeclarada(String s) { this.senioridadeDeclarada = s; }
    public void setExpirada(boolean expirada) { this.expirada = expirada; }
}

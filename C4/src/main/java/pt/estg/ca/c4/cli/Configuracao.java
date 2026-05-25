package pt.estg.ca.c4.cli;

import java.util.List;

/**
 * Objeto de dados (DTO) que agrega todos os parâmetros
 * resultantes do parse dos argumentos da linha de comandos.
 *
 * Referência: APL03_05 – boas práticas de estruturação de código Java.
 */
public class Configuracao {

    /** Caminho absoluto ou relativo para o PDF de entrada (pode estar em qualquer local da máquina). */
    private final String pdfEntrada;

    /** Caminho para o PDF de saída (por omissão: <nome_original>_assinado.pdf). */
    private final String pdfSaida;

    /** Caminho para a pasta certs/ (relativa ao JAR ou absoluta). */
    private final String pastasCerts;

    /** Nomes dos ficheiros .p12 dentro de certs/ (um por signatário). */
    private final List<String> certs;

    /**
     * Passwords de cada .p12, na mesma ordem que certs.
     * Podem ter sido fornecidas via --pass ou lidas interativamente sem eco.
     */
    private final List<char[]> passwords;

    public Configuracao(String pdfEntrada, String pdfSaida, String pastasCerts,
                        List<String> certs, List<char[]> passwords) {
        this.pdfEntrada  = pdfEntrada;
        this.pdfSaida    = pdfSaida;
        this.pastasCerts = pastasCerts;
        this.certs       = certs;
        this.passwords   = passwords;
    }

    public String getPdfEntrada()   { return pdfEntrada; }
    public String getPdfSaida()     { return pdfSaida; }
    public String getPastasCerts()  { return pastasCerts; }
    public List<String> getCerts()  { return certs; }
    public List<char[]> getPasswords() { return passwords; }
}

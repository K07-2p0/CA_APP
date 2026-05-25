package pt.estg.ca.c4.cli;

import java.util.List;

/**
 * Objeto de dados (DTO) que agrega todos os parâmetros
 * resultantes do parse dos argumentos da linha de comandos.
 *
 * Referência: APL03_05 – boas práticas de estruturação de código Java.
 */
public class Configuracao {

    /** Caminho absoluto para o PDF de entrada. */
    private final String pdfEntrada;

    /** Caminho para o PDF de saída (por omissão: <nome_original>_assinado.pdf). */
    private final String pdfSaida;

    /** Caminho para a pasta certs/. */
    private final String pastasCerts;

    /** Nomes dos ficheiros .p12 dentro de certs/ (um por signatário). */
    private final List<String> certs;

    /**
     * Passwords de cada .p12, na mesma ordem que certs.
     * Lidas interativamente sem eco (Console.readPassword) ou via --pass.
     */
    private final List<char[]> passwords;

    /**
     * Localização embebida na assinatura PDF (enunciado C4: "ESTG").
     * Configurável via --location ou pedida interativamente.
     */
    private final String location;

    /**
     * Razão embebida na assinatura PDF.
     * Configurável via --reason ou pedida interativamente.
     * Default: texto obrigatório do enunciado C4.
     */
    private final String reason;

    public Configuracao(String pdfEntrada, String pdfSaida, String pastasCerts,
                        List<String> certs, List<char[]> passwords,
                        String location, String reason) {
        this.pdfEntrada  = pdfEntrada;
        this.pdfSaida    = pdfSaida;
        this.pastasCerts = pastasCerts;
        this.certs       = certs;
        this.passwords   = passwords;
        this.location    = location;
        this.reason      = reason;
    }

    public String getPdfEntrada()      { return pdfEntrada; }
    public String getPdfSaida()        { return pdfSaida; }
    public String getPastasCerts()     { return pastasCerts; }
    public List<String> getCerts()     { return certs; }
    public List<char[]> getPasswords() { return passwords; }
    public String getLocation()        { return location; }
    public String getReason()          { return reason; }
}

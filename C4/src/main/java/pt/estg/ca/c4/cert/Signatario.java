package pt.estg.ca.c4.cert;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * Representa um signatário com certificado X.509 v3 e chave privada RSA,
 * extraídos do ficheiro PKCS#12.
 *
 * Conceitos relacionados (AT06/AT07):
 *   - Certificado X.509 v3: DN titular, chave pública, validade, KeyUsage, SAN (email)
 *   - Chave privada: componente secreta do par RSA – nunca transmitida (AT06)
 *   - Cadeia PKI: Certificado Aluno → SubCA Grupo XX → LSIRC Root CA 2026 (C3)
 */
public class Signatario {

    /** Chave privada RSA extraída do PKCS#12. Usada para calcular a assinatura digital. */
    private final PrivateKey chavePrivada;

    /** Certificado X.509 v3 do aluno, emitido pela SubCA do grupo. */
    private final X509Certificate certificado;

    /**
     * Cadeia de certificação completa: [cert_aluno, subCA, rootCA].
     * Incluída na assinatura PDF para validação do caminho de confiança (AT07).
     */
    private final Certificate[] cadeia;

    public Signatario(PrivateKey chavePrivada, X509Certificate certificado, Certificate[] cadeia) {
        this.chavePrivada = chavePrivada;
        this.certificado  = certificado;
        this.cadeia       = cadeia;
    }

    public PrivateKey getChavePrivada()      { return chavePrivada; }
    public X509Certificate getCertificado()  { return certificado; }
    public Certificate[] getCadeia()         { return cadeia; }

    /**
     * Extrai o CN (Common Name) do DN do certificado.
     * No C3, o CN é o número de aluno (ex: "2230123").
     * Referência: AT07 – "CN = numero_aluno, OU=ESTG, O=IPP, C=PT"
     */
    public String getNomeCN() {
        String dn = certificado.getSubjectX500Principal().getName();
        for (String parte : dn.split(",")) {
            parte = parte.trim();
            if (parte.startsWith("CN=")) return parte.substring(3);
        }
        return dn;
    }

    /**
     * Calcula o fingerprint SHA-256 do certificado.
     * Corresponde ao campo "Fingerprint certificado" do formulário C4 (pág. 6).
     * Referência: AT06 – identificação unívoca de um certificado.
     */
    public String getFingerprint() {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(certificado.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return "N/A";
        }
    }
}

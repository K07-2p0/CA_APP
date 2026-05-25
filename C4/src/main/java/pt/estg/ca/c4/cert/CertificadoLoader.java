package pt.estg.ca.c4.cert;

import pt.estg.ca.c4.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

/**
 * Responsável por abrir um ficheiro PKCS#12 (.p12) e extrair:
 *   1. A chave privada RSA do titular
 *   2. O certificado X.509 v3 do titular
 *   3. A cadeia de certificação completa (cert → SubCA → Root CA)
 *
 * Valida o certificado:
 *   - Período de validade (AT07 – ciclo de vida: Ativo/Expirado/Revogado)
 *   - KeyUsage bit 0 (digitalSignature) – AT07, Tabela Key Usage
 *
 * Referências:
 *   - APL03_05: KeyStore.getInstance("PKCS12"), provider BouncyCastle
 *   - AT06: PKCS#12 como formato de transporte de chave privada + certificado
 *   - AT07: Estrutura X.509 v3, DN, KeyUsage, cadeia de confiança PKI
 */
public class CertificadoLoader {

    /**
     * Carrega e valida um certificado PKCS#12.
     *
     * @param pastaCerts   Caminho absoluto para a pasta certs/
     * @param nomeFicheiro Nome do ficheiro .p12
     * @param password     Password de proteção do .p12
     * @return Signatario com chave privada, certificado e cadeia PKI
     */
    public static Signatario carregar(String pastaCerts, String nomeFicheiro, char[] password)
            throws Exception {

        File ficheiro = new File(pastaCerts, nomeFicheiro);
        Logger.info("  A abrir PKCS#12: " + ficheiro.getAbsolutePath());

        /*
         * Abrir o KeyStore no formato PKCS#12 com provider BouncyCastle.
         * Referência: APL03_05 – uso explícito do provider BC para PKCS12.
         */
        KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
        try (FileInputStream fis = new FileInputStream(ficheiro)) {
            ks.load(fis, password);
        } catch (Exception e) {
            throw new Exception("Não foi possível abrir o .p12. Verifique a password. (" + e.getMessage() + ")");
        }

        String alias = encontrarAlias(ks);
        if (alias == null)
            throw new Exception("Nenhuma chave privada encontrada em: " + nomeFicheiro);
        Logger.info("  Alias no KeyStore: " + alias);

        PrivateKey chavePrivada = (PrivateKey) ks.getKey(alias, password);
        if (chavePrivada == null)
            throw new Exception("Não foi possível extrair a chave privada do alias: " + alias);

        X509Certificate certificado = (X509Certificate) ks.getCertificate(alias);
        if (certificado == null)
            throw new Exception("Não foi possível extrair o certificado do alias: " + alias);

        /*
         * Extrair a cadeia PKI completa: [cert_aluno, SubCA, RootCA].
         * Permite ao validador construir o caminho de confiança sem downloads externos.
         * Referência: AT07 – hierarquias de confiança PKI.
         */
        Certificate[] cadeia = ks.getCertificateChain(alias);
        if (cadeia == null || cadeia.length == 0) {
            Logger.aviso("  Cadeia PKI não encontrada no .p12. Usando apenas o certificado folha.");
            cadeia = new Certificate[]{ certificado };
        }
        Logger.info("  Cadeia PKI: " + cadeia.length + " certificado(s).");

        validarCertificado(certificado, nomeFicheiro);

        Signatario s = new Signatario(chavePrivada, certificado, cadeia);
        Logger.info("  Fingerprint SHA-256: " + s.getFingerprint());
        return s;
    }

    /** Encontra o primeiro alias com chave privada no KeyStore. */
    private static String encontrarAlias(KeyStore ks) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            if (ks.isKeyEntry(a)) return a;
        }
        return null;
    }

    /**
     * Valida o certificado X.509 v3:
     *
     *  1. Período de validade (AT07 – ciclo de vida: Ativo/Expirado/Revogado)
     *  2. KeyUsage bit 0 – digitalSignature (AT07, Tabela Key Usage):
     *     "A chave pública pode ser usada para verificar assinaturas digitais."
     *  3. KeyUsage bit 1 – nonRepudiation (AT07, opcional mas recomendado):
     *     "O titular não pode negar ser o autor dos dados assinados."
     */
    private static void validarCertificado(X509Certificate cert, String nome) throws Exception {

        // 1. Período de validade
        try {
            cert.checkValidity(new Date());
            Logger.info("  Validade OK: " + cert.getNotBefore() + " → " + cert.getNotAfter());
        } catch (Exception e) {
            throw new Exception("Certificado EXPIRADO ou ainda não válido em '" + nome
                + "'. Validade: " + cert.getNotBefore() + " → " + cert.getNotAfter());
        }

        // 2. KeyUsage – bit 0 (digitalSignature)
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            if (!keyUsage[0])
                throw new Exception("Certificado '" + nome
                    + "' sem KeyUsage=digitalSignature (bit 0). Inválido para assinatura de documentos. (AT07)");
            String kuInfo = "digitalSignature=true";
            if (keyUsage.length > 1 && keyUsage[1]) kuInfo += ", nonRepudiation=true";
            Logger.info("  KeyUsage: " + kuInfo + " (AT07)");
        } else {
            Logger.aviso("  Extensão KeyUsage ausente (certificado auto-assinado?)");
        }

        Logger.info("  Emissor: " + cert.getIssuerX500Principal().getName());
        Logger.info("  Titular: " + cert.getSubjectX500Principal().getName());
    }
}

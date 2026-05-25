package pt.estg.ca.c4.pdf;

import pt.estg.ca.c4.cert.Signatario;
import pt.estg.ca.c4.util.Logger;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.*;
import java.security.cert.Certificate;
import java.util.*;

/**
 * Aplica assinaturas digitais a um ficheiro PDF usando Apache PDFBox + BouncyCastle.
 *
 * Fluxo técnico para cada signatário:
 *   1. PDFBox abre o documento e reserva espaço para a assinatura
 *   2. BouncyCastle constrói o CMS SignedData (RFC 5652):
 *        - Digest: SHA-256 (AT05 – SHA-2 recomendado; MD5/SHA-1 PROIBIDOS)
 *        - Assinatura: SHA256withRSA (AT05 – y = x^b mod m)
 *        - Certificado + cadeia PKI incluídos no CMS (AT07)
 *   3. O CMS SignedData é embebido no PDF (assinatura interna – AT09)
 *   4. Assinatura incremental: não invalida assinaturas anteriores (AT09)
 *
 * Propriedades obrigatórias (enunciado C4, pág. 5):
 *   - Location:  "ESTG"
 *   - Reason:    "Compreendo e aceito as regras do trabalho prático..."
 *   - Name:      CN do certificado (número de aluno)
 *
 * NOTA API PDFBox 3.x:
 *   - PDDocument.load(File) foi removido – usar Loader.loadPDF(File)
 *   - FILTER_ADOBE_PPK_LITE foi renomeado para FILTER_ADOBE_PPKLITE
 */
public class AssinadorPDF {

    private static final String RAZAO =
        "Compreendo e aceito as regras do trabalho pratico e eventuais " +
        "alteracoes pontuais que sejam introduzidas.";

    private static final String LOCAL = "ESTG";

    /**
     * Assina o PDF sequencialmente com todos os signatários (incremental).
     *
     * @param caminhoEntrada  PDF de entrada (qualquer caminho na máquina)
     * @param signatarios     Lista de signatários carregados de certs/
     * @param caminhoSaida    PDF de saída com todas as assinaturas
     */
    public static void assinar(String caminhoEntrada, List<Signatario> signatarios, String caminhoSaida)
            throws Exception {

        // Cada signatário lê o resultado do anterior → assinaturas incrementais (AT09)
        String ficheiroCorrente = caminhoEntrada;

        for (int i = 0; i < signatarios.size(); i++) {
            Signatario s = signatarios.get(i);
            boolean ultimo = (i == signatarios.size() - 1);
            String ficheiroDestino = ultimo
                ? caminhoSaida
                : caminhoEntrada + ".tmp_sig" + i + ".pdf";

            Logger.info("  Assinatura " + (i+1) + "/" + signatarios.size()
                + " → CN=" + s.getNomeCN());
            aplicarAssinatura(ficheiroCorrente, s, ficheiroDestino);

            if (i > 0 && !ficheiroCorrente.equals(caminhoEntrada))
                new File(ficheiroCorrente).delete();
            ficheiroCorrente = ficheiroDestino;
        }
    }

    /**
     * Aplica uma única assinatura digital incremental ao PDF.
     *
     * Usa o padrão CAdES embebido em PDF (PKCS#7 detached), compatível
     * com Adobe Acrobat Reader e outros validadores de assinatura.
     *
     * FIX PDFBox 3.x:
     *   - Loader.loadPDF(File) substitui PDDocument.load(File) (método removido na v3)
     *   - FILTER_ADOBE_PPKLITE substitui FILTER_ADOBE_PPK_LITE (constante renomeada na v3)
     */
    private static void aplicarAssinatura(String entrada, Signatario signatario, String saida)
            throws Exception {

        /*
         * PDFBox 3.x: usar Loader.loadPDF() em vez de PDDocument.load().
         * O método load(File) foi removido na versão 3.0 e substituído por
         * Loader.loadPDF(File) para maior clareza na API.
         * Referência: https://pdfbox.apache.org/3.0/migration.html
         */
        try (PDDocument doc = Loader.loadPDF(new File(entrada));
             FileOutputStream fos = new FileOutputStream(saida)) {

            /*
             * Criar PDSignature com as propriedades obrigatórias do enunciado C4.
             * Referência: AT09 – "assinatura pode ser embebida no próprio documento (interna)"
             */
            PDSignature pdSignature = new PDSignature();

            /*
             * PDFBox 3.x: FILTER_ADOBE_PPKLITE (sem underscore antes de LITE).
             * A constante FILTER_ADOBE_PPK_LITE foi renomeada na versão 3.0.
             * Referência: https://pdfbox.apache.org/3.0/migration.html
             */
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            pdSignature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            pdSignature.setName(signatario.getNomeCN());   // CN = nº aluno (AT07)
            pdSignature.setLocation(LOCAL);                // Obrigatório: "ESTG"
            pdSignature.setReason(RAZAO);                  // Obrigatório: conforme enunciado
            pdSignature.setSignDate(Calendar.getInstance());

            SignatureOptions opcoes = new SignatureOptions();
            opcoes.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);

            doc.addSignature(pdSignature, (inputStream) -> {
                try {
                    return construirCMSSignedData(inputStream, signatario);
                } catch (Exception e) {
                    throw new RuntimeException("Erro CMS: " + e.getMessage(), e);
                }
            }, opcoes);

            // Guardar com atualização incremental (preserva assinaturas anteriores – AT09)
            doc.saveIncremental(fos);
        }
    }

    /**
     * Constrói o CMS SignedData (RFC 5652) que encapsula a assinatura digital.
     *
     * Conteúdo do CMS SignedData:
     *   - Digest SHA-256 dos bytes do PDF                    (AT05 – SHA-2)
     *   - Assinatura RSA: y = x^b mod m, b = chave privada  (AT05)
     *   - Certificado do aluno + cadeia PKI                 (AT07)
     *
     * NÃO usar SHA-1 (desaconselhado – AT05) nem MD5 (proibido – AT05).
     * Provider "BC" especificado explicitamente (APL03_05 – uso explícito).
     */
    private static byte[] construirCMSSignedData(InputStream conteudo, Signatario signatario)
            throws Exception {

        byte[] bytesConteudo = conteudo.readAllBytes();

        /*
         * SHA256withRSA:
         *   - SHA-256: função de hash segura (AT05 – família SHA-2, NIST 2002)
         *   - RSA: algoritmo de assinatura assimétrica (AT04/AT05)
         *   - Provider "BC": BouncyCastle, explicitamente especificado (APL03_05)
         */
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(signatario.getChavePrivada());

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(
            new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
            ).build(contentSigner, signatario.getCertificado())
        );

        /*
         * Incluir a cadeia PKI completa no CMS:
         *   cert_aluno → SubCA Grupo XX → LSIRC Root CA 2026
         * O validador constrói o caminho de confiança sem downloads externos.
         * Referência: AT07 – hierarquias de confiança PKI.
         */
        List<Certificate> listaCadeia = Arrays.asList(signatario.getCadeia());
        generator.addCertificates(new JcaCertStore(listaCadeia));

        CMSTypedData cmsData = new CMSProcessableByteArray(bytesConteudo);
        return generator.generate(cmsData, false).getEncoded();
    }
}

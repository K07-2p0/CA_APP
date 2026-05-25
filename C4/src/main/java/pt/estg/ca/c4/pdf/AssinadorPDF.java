package pt.estg.ca.c4.pdf;

import pt.estg.ca.c4.cert.Signatario;

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
 *        - Assinatura: SHA256withRSA (AT05)
 *        - Certificado + cadeia PKI incluídos no CMS (AT07)
 *   3. O CMS SignedData é embebido no PDF (assinatura interna – AT09)
 *   4. Assinatura incremental: não invalida assinaturas anteriores (AT09)
 *
 * Propriedades obrigatórias (enunciado C4):
 *   - Location: "ESTG"
 *   - Reason:   "Compreendo e aceito as regras do trabalho prático..."
 *   - Name:     CN do certificado (número de aluno)
 *
 * PDFBox 3.x:
 *   - Loader.loadPDF(File) substitui PDDocument.load(File)
 *   - FILTER_ADOBE_PPKLITE substitui FILTER_ADOBE_PPK_LITE
 */
public class AssinadorPDF {

    private static final String RAZAO =
        "Compreendo e aceito as regras do trabalho pratico e eventuais " +
        "alteracoes pontuais que sejam introduzidas.";

    private static final String LOCAL = "ESTG";

    /**
     * Interface funcional para reportar o progresso de cada assinatura.
     * Permite ao Main.java mostrar feedback visual sem acoplar as classes.
     */
    @FunctionalInterface
    public interface ProgressoCallback {
        void onAssinatura(int indice, int total, String nomeCN);
    }

    /**
     * Assina o PDF sequencialmente com todos os signatários (incremental).
     *
     * @param caminhoEntrada  PDF de entrada
     * @param signatarios     Lista de signatários carregados de certs/
     * @param caminhoSaida    PDF de saída com todas as assinaturas
     * @param progresso       Callback chamado antes de cada assinatura
     */
    public static void assinar(String caminhoEntrada, List<Signatario> signatarios,
                               String caminhoSaida, ProgressoCallback progresso)
            throws Exception {

        // Cada signatário lê o resultado do anterior → assinaturas incrementais (AT09)
        String ficheiroCorrente = caminhoEntrada;

        for (int i = 0; i < signatarios.size(); i++) {
            Signatario s = signatarios.get(i);
            boolean ultimo = (i == signatarios.size() - 1);
            String ficheiroDestino = ultimo
                ? caminhoSaida
                : caminhoEntrada + ".tmp_sig" + i + ".pdf";

            // Notificar o chamador do progresso
            if (progresso != null) progresso.onAssinatura(i + 1, signatarios.size(), s.getNomeCN());

            aplicarAssinatura(ficheiroCorrente, s, ficheiroDestino);
            System.out.println("    -> OK");

            // Limpar ficheiros temporários intermediários
            if (i > 0 && !ficheiroCorrente.equals(caminhoEntrada))
                new File(ficheiroCorrente).delete();
            ficheiroCorrente = ficheiroDestino;
        }
    }

    /**
     * Aplica uma única assinatura digital incremental ao PDF.
     *
     * PDFBox 3.x:
     *   - Loader.loadPDF(File) em vez de PDDocument.load(File)
     *   - FILTER_ADOBE_PPKLITE em vez de FILTER_ADOBE_PPK_LITE
     */
    private static void aplicarAssinatura(String entrada, Signatario signatario, String saida)
            throws Exception {

        try (PDDocument doc = Loader.loadPDF(new File(entrada));
             FileOutputStream fos = new FileOutputStream(saida)) {

            PDSignature pdSignature = new PDSignature();
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);           // PDFBox 3.x
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

            // Guardar com atualização incremental – preserva assinaturas anteriores (AT09)
            doc.saveIncremental(fos);
        }
    }

    /**
     * Constrói o CMS SignedData (RFC 5652) que encapsula a assinatura digital.
     *
     * - SHA-256: função de hash segura (AT05 – SHA-2, NIST 2002)
     * - SHA256withRSA: algoritmo de assinatura (AT04/AT05)
     * - Provider "BC": BouncyCastle explícito (APL03_05)
     * - Cadeia PKI completa incluída (AT07)
     */
    private static byte[] construirCMSSignedData(InputStream conteudo, Signatario signatario)
            throws Exception {

        byte[] bytesConteudo = conteudo.readAllBytes();

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(signatario.getChavePrivada());

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(
            new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
            ).build(contentSigner, signatario.getCertificado())
        );

        // Incluir cadeia PKI: cert_aluno → SubCA Grupo XX → LSIRC Root CA 2026 (AT07)
        List<Certificate> listaCadeia = Arrays.asList(signatario.getCadeia());
        generator.addCertificates(new JcaCertStore(listaCadeia));

        CMSTypedData cmsData = new CMSProcessableByteArray(bytesConteudo);
        return generator.generate(cmsData, false).getEncoded();
    }
}

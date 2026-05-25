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
 *        - Digest: SHA-256 (AT05)
 *        - Assinatura: SHA256withRSA (AT05)
 *        - Certificado + cadeia PKI incluídos no CMS (AT07)
 *   3. O CMS SignedData é embebido no PDF (AT09)
 *   4. Assinatura incremental: não invalida anteriores (AT09)
 *
 * Propriedades da assinatura configuráveis:
 *   - Location:  via Configuracao.getLocation()  (default: "ESTG")
 *   - Reason:    via Configuracao.getReason()    (default: texto do enunciado C4)
 *   - Name:      CN do certificado (número de aluno)
 */
public class AssinadorPDF {

    /**
     * Interface funcional para reportar o progresso de cada assinatura.
     */
    @FunctionalInterface
    public interface ProgressoCallback {
        void onAssinatura(int indice, int total, String nomeCN);
    }

    /**
     * Assina o PDF sequencialmente com todos os signatários (incremental).
     *
     * @param caminhoEntrada  PDF de entrada
     * @param signatarios     Lista de signatários
     * @param caminhoSaida    PDF de saída
     * @param location        Localização embebida na assinatura
     * @param reason          Razão embebida na assinatura
     * @param progresso       Callback de progresso (pode ser null)
     */
    public static void assinar(String caminhoEntrada, List<Signatario> signatarios,
                               String caminhoSaida, String location, String reason,
                               ProgressoCallback progresso)
            throws Exception {

        String ficheiroCorrente = caminhoEntrada;

        for (int i = 0; i < signatarios.size(); i++) {
            Signatario s = signatarios.get(i);
            boolean ultimo = (i == signatarios.size() - 1);
            String ficheiroDestino = ultimo
                ? caminhoSaida
                : caminhoEntrada + ".tmp_sig" + i + ".pdf";

            if (progresso != null)
                progresso.onAssinatura(i + 1, signatarios.size(), s.getNomeCN());

            aplicarAssinatura(ficheiroCorrente, s, ficheiroDestino, location, reason);
            System.out.println("    -> OK");

            if (i > 0 && !ficheiroCorrente.equals(caminhoEntrada))
                new File(ficheiroCorrente).delete();
            ficheiroCorrente = ficheiroDestino;
        }
    }

    /**
     * Aplica uma única assinatura digital incremental ao PDF.
     */
    private static void aplicarAssinatura(String entrada, Signatario signatario,
                                          String saida, String location, String reason)
            throws Exception {

        try (PDDocument doc = Loader.loadPDF(new File(entrada));
             FileOutputStream fos = new FileOutputStream(saida)) {

            PDSignature pdSignature = new PDSignature();
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            pdSignature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            pdSignature.setName(signatario.getNomeCN());
            pdSignature.setLocation(location);   // configurável
            pdSignature.setReason(reason);        // configurável
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

            doc.saveIncremental(fos);
        }
    }

    /**
     * Constrói o CMS SignedData (RFC 5652).
     *   - SHA-256 (AT05)
     *   - SHA256withRSA (AT04/AT05)
     *   - Provider BC explícito (APL03_05)
     *   - Cadeia PKI completa (AT07)
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

        List<Certificate> listaCadeia = Arrays.asList(signatario.getCadeia());
        generator.addCertificates(new JcaCertStore(listaCadeia));

        CMSTypedData cmsData = new CMSProcessableByteArray(bytesConteudo);
        return generator.generate(cmsData, false).getEncoded();
    }
}

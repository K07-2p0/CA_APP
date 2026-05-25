import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * GeradorAssinatura
 *
 * Implementa a interface SignatureInterface do PDFBox.
 * É chamado automaticamente pelo PDFBox para gerar o bloco
 * criptográfico (PKCS#7) que fica embebido dentro do PDF.
 *
 * Usa a biblioteca Bouncy Castle para construir o objeto CMS
 * com o algoritmo SHA256withRSA (ou ECDSA conforme a chave).
 */
public class GeradorAssinatura implements SignatureInterface {

    private final PrivateKey    chavePrivada;
    private final Certificate[] cadeia;

    /**
     * @param chavePrivada chave privada do signatário
     * @param cadeia       cadeia de certificados (aluno -> SubCA -> Root CA)
     */
    public GeradorAssinatura(PrivateKey chavePrivada, Certificate[] cadeia) {
        this.chavePrivada = chavePrivada;
        this.cadeia       = cadeia;
    }

    /**
     * Gera a assinatura PKCS#7 para o conteúdo do PDF.
     *
     * Este método é invocado pelo PDFBox durante o processo de assinatura.
     * O InputStream contém os bytes do PDF a assinar, excluindo o espaço
     * reservado para a assinatura em si.
     *
     * @param conteudo stream com os dados do PDF
     * @return bytes da assinatura PKCS#7 codificada em DER
     */
    @Override
    public byte[] sign(InputStream conteudo) throws java.io.IOException {
        try {
            byte[] dados = conteudo.readAllBytes();

            // Escolher o algoritmo conforme o tipo de chave do certificado
            String tipoChave = chavePrivada.getAlgorithm();
            String algoritmo;
            if (tipoChave.equals("EC")) {
                algoritmo = "SHA256withECDSA";
            } else if (tipoChave.equals("DSA")) {
                algoritmo = "SHA256withDSA";
            } else {
                algoritmo = "SHA256withRSA"; // RSA é o mais comum
            }

            // Construir o gerador CMS com Bouncy Castle
            CMSSignedDataGenerator gerador = new CMSSignedDataGenerator();

            ContentSigner contentSigner = new JcaContentSignerBuilder(algoritmo)
                    .build(chavePrivada);

            gerador.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build()
                ).build(contentSigner, (X509Certificate) cadeia[0])
            );

            // Incluir a cadeia de certificados para permitir validação offline
            gerador.addCertificates(new JcaCertStore(Arrays.asList(cadeia)));

            // Gerar assinatura detached (dados originais não ficam duplicados)
            CMSSignedData assinado = gerador.generate(
                new CMSProcessableByteArray(dados), false
            );

            return assinado.getEncoded();

        } catch (Exception e) {
            throw new java.io.IOException("Erro ao gerar assinatura PKCS#7: " + e.getMessage(), e);
        }
    }
}
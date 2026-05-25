package pt.estg.ca.c4;

import pt.estg.ca.c4.cert.CertificadoLoader;
import pt.estg.ca.c4.cert.Signatario;
import pt.estg.ca.c4.cli.ArgumentParser;
import pt.estg.ca.c4.cli.Configuracao;
import pt.estg.ca.c4.pdf.AssinadorPDF;
import pt.estg.ca.c4.util.Logger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  C4 – Assinatura Digital de PDFs                                    ║
 * ║  Criptografia Aplicada | LSIRC ESTG 2025/2026                       ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║  Ponto de entrada da aplicação em linha de comandos.                ║
 * ║                                                                     ║
 * ║  Responsabilidades:                                                 ║
 * ║   1. Registar o provider BouncyCastle na JCA (APL03_05)             ║
 * ║   2. Parsear os argumentos da linha de comandos                     ║
 * ║   3. Carregar cada certificado PKCS#12 e validá-lo (AT06/AT07)      ║
 * ║   4. Delegar a assinatura incremental ao AssinadorPDF (AT05/AT09)   ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Uso:
 *   java -jar c4.jar --pdf <caminho> --cert <nome.p12> [--cert <nome2.p12>]
 *                    [--out <saida.pdf>] [--pass <password>]
 *
 * Exemplos:
 *   # Assinar com um certificado (password pedida interativamente)
 *   java -jar c4.jar --pdf docs/acordo.pdf --cert aluno1.p12
 *
 *   # Assinar com dois certificados
 *   java -jar c4.jar --pdf docs/acordo.pdf --cert aluno1.p12 --cert aluno2.p12 --out acordo_assinado.pdf
 */
public class Main {

    public static void main(String[] args) {

        Logger.info("╔══════════════════════════════════════════════════╗");
        Logger.info("║  C4 – Assinador Digital de PDFs  (LSIRC 2026)   ║");
        Logger.info("╚══════════════════════════════════════════════════╝");

        /*
         * 1. Registar o BouncyCastle como provider JCA (instalação dinâmica).
         *    Referência: APL03_05 – "Security.addProvider(new BouncyCastleProvider())"
         *    O BC disponibiliza: PKCS12 KeyStore, SHA256withRSA, CMS, X.509, etc.
         *    Ao inserir na posição 1, é o primeiro provider consultado pela JCA.
         */
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        Logger.info("Provider BouncyCastle registado na JCA (posição 1).");

        // 2. Parsear e validar os argumentos da linha de comandos
        Configuracao config;
        try {
            config = ArgumentParser.parse(args);
        } catch (IllegalArgumentException e) {
            Logger.erro("Argumentos inválidos: " + e.getMessage());
            ArgumentParser.imprimirAjuda();
            System.exit(1);
            return;
        }

        /*
         * 3. Carregar cada certificado PKCS#12 da pasta certs/.
         *    O CertificadoLoader valida:
         *      - Período de validade (AT07 – ciclo de vida do certificado)
         *      - KeyUsage bit 0 (digitalSignature) – AT07, Tabela Key Usage
         *    Retorna um Signatario com: PrivateKey + X509Certificate + cadeia PKI
         */
        List<Signatario> signatarios = new ArrayList<>();
        for (int i = 0; i < config.getCerts().size(); i++) {
            String nomeCert = config.getCerts().get(i);
            char[] password = config.getPasswords().get(i);
            Logger.info("A carregar certificado: " + nomeCert);
            try {
                Signatario s = CertificadoLoader.carregar(config.getPastasCerts(), nomeCert, password);
                signatarios.add(s);
                Logger.ok("Certificado carregado: CN=" + s.getNomeCN()
                        + " | Validade OK | KeyUsage digitalSignature=true");
            } catch (Exception e) {
                Logger.erro("Erro ao carregar " + nomeCert + ": " + e.getMessage());
                System.exit(2);
            }
        }

        /*
         * 4. Assinar o PDF sequencialmente com todos os signatários.
         *    Cada assinatura é incremental (não destrói as anteriores) – AT09.
         *    São embebidas no documento (assinatura interna) – AT09.
         *    Propriedades obrigatórias (enunciado C4):
         *      - Location: "ESTG"
         *      - Reason: "Compreendo e aceito as regras do trabalho prático..."
         */
        try {
            Logger.info("A iniciar assinatura do PDF: " + config.getPdfEntrada());
            AssinadorPDF.assinar(config.getPdfEntrada(), signatarios, config.getPdfSaida());
            Logger.ok("PDF assinado com sucesso! Ficheiro gerado: " + config.getPdfSaida());
        } catch (Exception e) {
            Logger.erro("Falha na assinatura do PDF: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }
}

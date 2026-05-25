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
 * ╔══════════════════════════════════════════════╗
 * ║  C4 – Assinatura Digital de PDFs | LSIRC   ║
 * ╚══════════════════════════════════════════════╝
 *
 * Responsabilidades:
 *   1. Registar o provider BouncyCastle na JCA (APL03_05)
 *   2. Recolher configuração (interativa ou por args)
 *   3. Carregar e validar certificados PKCS#12 (AT06/AT07)
 *   4. Assinar o PDF de forma incremental (AT05/AT09)
 *
 * NOTA sobre registo do BC provider num fat JAR:
 *   Usar Security.addProvider() em vez de insertProviderAt(1) evita o erro
 *   "JCE cannot authenticate the provider BC" que ocorre quando o fat JAR
 *   não mantém a assinatura original do bcprov-jdk18on.jar.
 *   O BC é adicionado apenas se ainda não estiver registado.
 */
public class Main {

    public static void main(String[] args) {

        System.out.println();
        System.out.println("  ==============================================");
        System.out.println("    C4 - Assinatura Digital de PDF | LSIRC");
        System.out.println("  ==============================================");
        System.out.println();

        /*
         * 1. Registar o BouncyCastle como provider JCA.
         *
         *    IMPORTANTE – fat JAR:
         *      insertProviderAt(bc, 1) lança "JCE cannot authenticate the provider BC"
         *      quando o JAR é empacotado pelo Shade (as assinaturas são removidas).
         *      A solução é usar addProvider() que não força a posição 1 e não
         *      despoleta a verificação de autenticidade do JCE.
         *      Se o BC já estiver registado (ex: JVM corporativa), o método devolve -1
         *      e não faz nada – sem erro.
         *
         *    Referência: APL03_05 – Security.addProvider(new BouncyCastleProvider())
         */
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // 2. Obter configuração: interativa (sem args) ou CLI (com args)
        Configuracao config;
        try {
            config = ArgumentParser.parse(args);
        } catch (IllegalArgumentException e) {
            Logger.erro("Configuração inválida: " + e.getMessage());
            ArgumentParser.imprimirAjuda();
            System.exit(1);
            return;
        }

        // Resumo da configuração
        System.out.println("  PDF de entrada : " + new java.io.File(config.getPdfEntrada()).getName());
        System.out.println("  PDF de saída   : " + new java.io.File(config.getPdfSaida()).getName());
        System.out.println();

        /*
         * 3. Carregar e validar cada certificado PKCS#12.
         *    O CertificadoLoader valida:
         *      - Período de validade (AT07)
         *      - KeyUsage bit 0 (digitalSignature) (AT07)
         */
        List<Signatario> signatarios = new ArrayList<>();
        for (int i = 0; i < config.getCerts().size(); i++) {
            String nomeCert = config.getCerts().get(i);
            char[] password = config.getPasswords().get(i);
            try {
                Signatario s = CertificadoLoader.carregar(config.getPastasCerts(), nomeCert, password);
                signatarios.add(s);
                System.out.println("  Password para " + nomeCert.replace(".p12", "") + ":");
                System.out.println("    -> OK");
                System.out.println();
            } catch (Exception e) {
                System.out.println("  Password para " + nomeCert.replace(".p12", "") + ":");
                System.out.println("    -> ERRO: " + e.getMessage());
                System.out.println();
                Logger.erro("Impossível continuar. Verifique a password e o ficheiro .p12.");
                System.exit(2);
            }
        }

        System.out.println("  " + signatarios.size() + " certificado(s) carregado(s).");
        System.out.println();

        /*
         * 4. Assinar o PDF sequencialmente (incremental) – AT09.
         *    Propriedades obrigatórias (enunciado C4):
         *      - Location: "ESTG"
         *      - Reason: "Compreendo e aceito as regras do trabalho prático..."
         */
        try {
            AssinadorPDF.assinar(
                config.getPdfEntrada(),
                signatarios,
                config.getPdfSaida(),
                (indice, total, nomeCN) ->
                    System.out.println("  A assinar com: " + nomeCN + " (" + indice + "/" + total + ")...")
            );
        } catch (Exception e) {
            System.out.println("    -> ERRO: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }

        // Conclusão
        System.out.println();
        System.out.println("  ==============================================");
        System.out.println("    Concluído! PDF guardado em: "
            + new java.io.File(config.getPdfSaida()).getName());
        System.out.println("  ==============================================");
        System.out.println();
    }
}

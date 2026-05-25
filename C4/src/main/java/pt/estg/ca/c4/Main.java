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
 * Ponto de entrada da aplicação C4 – Assinatura Digital de PDFs.
 *
 * Responsabilidades:
 *   1. Registar o provider BouncyCastle na JCA (APL03_05)
 *   2. Recolher configuração (interativa ou CLI)
 *   3. Carregar e validar certificados PKCS#12 (AT06/AT07)
 *   4. Assinar o PDF de forma incremental (AT05/AT09)
 */
public class Main {

    public static void main(String[] args) {

        System.out.println();
        System.out.println("  ==============================================");
        System.out.println("    C4 - Assinatura Digital de PDF | LSIRC");
        System.out.println("  ==============================================");
        System.out.println();

        /*
         * 1. Registar BouncyCastle como provider JCA.
         *    Usar addProvider() em vez de insertProviderAt(bc,1) evita
         *    "JCE cannot authenticate the provider BC" no fat JAR
         *    (o Shade remove as assinaturas originais do bcprov-jdk18on.jar).
         */
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // 2. Obter configuração
        Configuracao config;
        try {
            config = ArgumentParser.parse(args);
        } catch (IllegalArgumentException e) {
            Logger.erro("Configuração inválida: " + e.getMessage());
            ArgumentParser.imprimirAjuda();
            System.exit(1);
            return;
        }

        // Resumo
        System.out.println("  PDF de entrada : " + new java.io.File(config.getPdfEntrada()).getName());
        System.out.println("  PDF de saída   : " + new java.io.File(config.getPdfSaida()).getName());
        System.out.println("  Razão           : " + config.getReason());
        System.out.println("  Localização     : " + config.getLocation());
        System.out.println();

        // 3. Carregar certificados
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

        // 4. Assinar
        try {
            AssinadorPDF.assinar(
                config.getPdfEntrada(),
                signatarios,
                config.getPdfSaida(),
                config.getLocation(),
                config.getReason(),
                (indice, total, nomeCN) ->
                    System.out.println("  A assinar com: " + nomeCN + " (" + indice + "/" + total + ")...")
            );
        } catch (Exception e) {
            System.out.println("    -> ERRO: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }

        System.out.println();
        System.out.println("  ==============================================");
        System.out.println("    Concluído! PDF guardado em: "
            + new java.io.File(config.getPdfSaida()).getName());
        System.out.println("  ==============================================");
        System.out.println();
    }
}

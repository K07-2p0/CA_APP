package pt.estg.ca.c4.cli;

import pt.estg.ca.c4.util.Logger;

import java.io.Console;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsável por interpretar e validar os argumentos passados na linha de comandos.
 *
 * Flags suportadas:
 *   --pdf   <caminho>    Caminho para o PDF a assinar (obrigatório)
 *                        Pode ser qualquer caminho absoluto ou relativo na máquina.
 *   --cert  <nome.p12>   Nome do ficheiro .p12 em certs/ (pode repetir para múltiplas assinaturas)
 *   --out   <saida.pdf>  Ficheiro PDF de saída (opcional; default: <original>_assinado.pdf)
 *   --pass  <password>   Password do .p12 (opcional; se omitida, é pedida interativamente)
 *   --certs-dir <pasta>  Caminho para a pasta com os .p12 (opcional; default: ./certs)
 *
 * Segurança:
 *   Quando --pass é omitido, a password é lida com Console.readPassword() que não faz
 *   eco no terminal, evitando exposição no histórico de comandos.
 *   (Referência: AT06 – boas práticas com chaves privadas)
 */
public class ArgumentParser {

    public static Configuracao parse(String[] args) {

        String pdfEntrada = null;
        String pdfSaida   = null;
        String certsDir   = resolverPastaDefaultCerts();
        List<String> certs       = new ArrayList<>();
        List<String> passesBruto = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pdf":       pdfEntrada = args[++i]; break;
                case "--cert":      certs.add(args[++i]); break;
                case "--out":       pdfSaida = args[++i]; break;
                case "--pass":      passesBruto.add(args[++i]); break;
                case "--certs-dir": certsDir = args[++i]; break;
                case "--help":
                case "-h":          imprimirAjuda(); System.exit(0); break;
                default:
                    throw new IllegalArgumentException("Argumento desconhecido: " + args[i]);
            }
        }

        if (pdfEntrada == null)
            throw new IllegalArgumentException("É obrigatório indicar o PDF com --pdf <caminho>.");
        if (certs.isEmpty())
            throw new IllegalArgumentException("É obrigatório indicar pelo menos um certificado com --cert <nome.p12>.");

        // Verificar se o PDF existe (aceita caminhos absolutos e relativos na máquina)
        File ficheiroPdf = new File(pdfEntrada);
        if (!ficheiroPdf.isAbsolute()) {
            ficheiroPdf = new File(System.getProperty("user.dir"), pdfEntrada);
        }
        if (!ficheiroPdf.exists() || !ficheiroPdf.isFile()) {
            throw new IllegalArgumentException("PDF não encontrado: " + ficheiroPdf.getAbsolutePath());
        }

        File pastaCerts = new File(certsDir);
        if (!pastaCerts.exists() || !pastaCerts.isDirectory()) {
            throw new IllegalArgumentException("Pasta de certificados não encontrada: " + pastaCerts.getAbsolutePath());
        }

        for (String cert : certs) {
            File fileCert = new File(pastaCerts, cert);
            if (!fileCert.exists()) {
                throw new IllegalArgumentException("Certificado não encontrado em certs/: " + fileCert.getAbsolutePath());
            }
        }

        if (pdfSaida == null) {
            String nomeBase = ficheiroPdf.getName().replaceFirst("\\.pdf$", "");
            pdfSaida = new File(ficheiroPdf.getParent(), nomeBase + "_assinado.pdf").getAbsolutePath();
        }

        /*
         * Processar passwords:
         * Se --pass foi fornecido menos vezes do que --cert, pede interativamente
         * via Console.readPassword() (sem eco no terminal).
         * Referência: AT06 – segurança no uso de chaves privadas.
         */
        List<char[]> passwords = new ArrayList<>();
        for (int i = 0; i < certs.size(); i++) {
            if (i < passesBruto.size()) {
                passwords.add(passesBruto.get(i).toCharArray());
            } else {
                passwords.add(lerPasswordInterativa(certs.get(i)));
            }
        }

        return new Configuracao(
            ficheiroPdf.getAbsolutePath(), pdfSaida,
            pastaCerts.getAbsolutePath(), certs, passwords
        );
    }

    /**
     * Lê a password do terminal sem eco.
     * Usa Console.readPassword() em Windows, macOS e Linux.
     * Fallback para Scanner em ambientes sem consola (IDEs).
     */
    private static char[] lerPasswordInterativa(String nomeCert) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("Password para [%s]: ", nomeCert);
        } else {
            Logger.info("AVISO: Consola não disponível. A ler password em modo visível.");
            Logger.info("Password para [" + nomeCert + "]: ");
            try (java.util.Scanner sc = new java.util.Scanner(System.in)) {
                return sc.nextLine().toCharArray();
            }
        }
    }

    /** Resolve o caminho default da pasta certs/ (junto ao JAR, ou na pasta corrente). */
    private static String resolverPastaDefaultCerts() {
        try {
            String jarPath = ArgumentParser.class
                .getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File certsPasta = new File(new File(jarPath).getParentFile(), "certs");
            if (certsPasta.exists()) return certsPasta.getAbsolutePath();
        } catch (Exception ignored) {}
        return Paths.get(System.getProperty("user.dir"), "certs").toString();
    }

    public static void imprimirAjuda() {
        System.out.println();
        System.out.println("Uso:");
        System.out.println("  java -jar c4.jar --pdf <caminho> --cert <nome.p12> [opções]");
        System.out.println();
        System.out.println("Opções:");
        System.out.println("  --pdf       <caminho>     PDF a assinar (qualquer caminho na máquina)");
        System.out.println("  --cert      <nome.p12>    Certificado .p12 em certs/ (repetir para múltiplos)");
        System.out.println("  --out       <saida.pdf>   PDF de saída (default: <original>_assinado.pdf)");
        System.out.println("  --pass      <password>    Password do .p12 (default: pedida interativamente)");
        System.out.println("  --certs-dir <pasta>       Pasta com os .p12 (default: ./certs)");
        System.out.println("  --help / -h               Mostrar esta ajuda");
        System.out.println();
        System.out.println("Exemplos:");
        System.out.println("  java -jar c4.jar --pdf docs/acordo.pdf --cert aluno1.p12");
        System.out.println("  java -jar c4.jar --pdf /home/user/doc.pdf --cert a1.p12 --cert a2.p12 --out doc_signed.pdf");
        System.out.println();
    }
}

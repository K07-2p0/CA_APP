package pt.estg.ca.c4.cli;

import java.io.Console;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Responsável por obter a configuração da aplicação.
 *
 * Suporta dois modos:
 *
 *   A) MODO INTERATIVO (sem argumentos)
 *      Menu passo a passo no terminal:
 *        - Lista os .p12 encontrados em certs/
 *        - Pergunta o caminho do PDF de entrada
 *        - Pergunta o PDF de saída (ou usa o default)
 *        - Pergunta a Razão e Localização (ou usa os defaults do enunciado)
 *        - Pede a password de cada .p12 sem eco
 *
 *   B) MODO CLI (com argumentos)
 *      Para uso em scripts ou automação:
 *        --pdf      <caminho>    PDF a assinar (obrigatório)
 *        --cert     <nome.p12>  .p12 em certs/ (pode repetir)
 *        --out      <saida.pdf> PDF de saída (opcional)
 *        --reason   <texto>     Razão da assinatura (opcional)
 *        --location <texto>     Localização da assinatura (opcional)
 *        --pass     <password>  Password (opcional; pede interativamente se omitida)
 *        --certs-dir <pasta>    Pasta com os .p12 (opcional; default: ./certs)
 *
 * Segurança:
 *   Passwords lidas com Console.readPassword() – sem eco no terminal.
 *   Referência: AT06 – boas práticas com chaves privadas.
 */
public class ArgumentParser {

    /** Razão default conforme exigido pelo enunciado C4. */
    public static final String REASON_DEFAULT =
        "Compreendo e aceito as regras do trabalho pratico e eventuais " +
        "alteracoes pontuais que sejam introduzidas.";

    /** Localização default conforme exigido pelo enunciado C4. */
    public static final String LOCATION_DEFAULT = "ESTG";

    /**
     * Ponto de entrada: decide entre modo interativo ou CLI conforme os args.
     */
    public static Configuracao parse(String[] args) {
        if (args.length == 0) {
            return modoInterativo();
        } else {
            return modoCLI(args);
        }
    }

    // =========================================================================
    // MODO INTERATIVO
    // =========================================================================

    private static Configuracao modoInterativo() {
        Scanner sc = new Scanner(System.in);
        String certsDir = resolverPastaDefaultCerts();
        File pastaCerts = new File(certsDir);

        // --- Listar .p12 disponíveis ---
        File[] listaP12 = pastaCerts.exists()
            ? pastaCerts.listFiles(f -> f.getName().endsWith(".p12"))
            : new File[0];
        if (listaP12 == null) listaP12 = new File[0];
        Arrays.sort(listaP12);

        System.out.println("  Certificados encontrados em certs/:");
        if (listaP12.length == 0) {
            System.out.println("    (nenhum .p12 encontrado em " + pastaCerts.getAbsolutePath() + ")");
            System.out.println("    Coloque os ficheiros .p12 na pasta certs/ e reinicie.");
            System.out.println();
        } else {
            for (int i = 0; i < listaP12.length; i++) {
                System.out.println("    [" + (i + 1) + "] " + listaP12[i].getName());
            }
            System.out.println();
        }

        // --- PDF de entrada ---
        String pdfEntradaStr = pedirCaminhoPdf(sc,
            "  PDF de entrada (caminho completo ou nome na pasta atual): ");
        // Remover aspas que o Windows por vezes cola automaticamente
        pdfEntradaStr = pdfEntradaStr.replace("\"", "").trim();
        File ficheiroPdf = resolverFicheiro(pdfEntradaStr);

        // --- PDF de saída ---
        String nomeDefault = ficheiroPdf.getName().replaceFirst("\\.pdf$", "") + "_assinado.pdf";
        String pdfSaidaDefault = new File(ficheiroPdf.getParent(), nomeDefault).getAbsolutePath();
        System.out.print("  PDF de saída   [" + nomeDefault + "]: ");
        String pdfSaidaInput = sc.nextLine().trim();
        String pdfSaida = pdfSaidaInput.isEmpty() ? pdfSaidaDefault : pdfSaidaInput;
        System.out.println();

        // --- Razão da assinatura ---
        System.out.println("  Razão da assinatura:");
        System.out.println("    [Enter] usar default do enunciado C4");
        System.out.println("    Default: \"" + REASON_DEFAULT + "\"");
        System.out.print("  Razão: ");
        String reasonInput = sc.nextLine().trim();
        String reason = reasonInput.isEmpty() ? REASON_DEFAULT : reasonInput;
        System.out.println();

        // --- Localização da assinatura ---
        System.out.print("  Localização [" + LOCATION_DEFAULT + "]: ");
        String locationInput = sc.nextLine().trim();
        String location = locationInput.isEmpty() ? LOCATION_DEFAULT : locationInput;
        System.out.println();

        // --- Selecionar certificados ---
        List<String> certsSelecionados = new ArrayList<>();
        if (listaP12.length == 0) {
            throw new IllegalArgumentException(
                "Nenhum .p12 encontrado em certs/. Impossível continuar.");
        } else if (listaP12.length == 1) {
            certsSelecionados.add(listaP12[0].getName());
        } else {
            System.out.println("  Quais certificados usar? (ex: 1,2 ou Enter para todos)");
            System.out.print("  Seleção: ");
            String selecao = sc.nextLine().trim();
            if (selecao.isEmpty()) {
                for (File f : listaP12) certsSelecionados.add(f.getName());
            } else {
                for (String parte : selecao.split(",")) {
                    int idx = Integer.parseInt(parte.trim()) - 1;
                    if (idx < 0 || idx >= listaP12.length)
                        throw new IllegalArgumentException("Seleção inválida: " + (idx+1));
                    certsSelecionados.add(listaP12[idx].getName());
                }
            }
            System.out.println();
        }

        // --- Passwords (sem eco) ---
        List<char[]> passwords = new ArrayList<>();
        for (String cert : certsSelecionados) {
            passwords.add(lerPasswordInterativa(cert));
        }

        return new Configuracao(
            ficheiroPdf.getAbsolutePath(), pdfSaida,
            pastaCerts.getAbsolutePath(), certsSelecionados, passwords,
            location, reason
        );
    }

    // =========================================================================
    // MODO CLI
    // =========================================================================

    private static Configuracao modoCLI(String[] args) {
        String pdfEntrada = null;
        String pdfSaida   = null;
        String certsDir   = resolverPastaDefaultCerts();
        String reason     = REASON_DEFAULT;
        String location   = LOCATION_DEFAULT;
        List<String> certs       = new ArrayList<>();
        List<String> passesBruto = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pdf":       pdfEntrada = args[++i]; break;
                case "--cert":      certs.add(args[++i]); break;
                case "--out":       pdfSaida = args[++i]; break;
                case "--pass":      passesBruto.add(args[++i]); break;
                case "--certs-dir": certsDir = args[++i]; break;
                case "--reason":    reason = args[++i]; break;
                case "--location":  location = args[++i]; break;
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

        // Remover aspas que o Windows por vezes cola automaticamente
        pdfEntrada = pdfEntrada.replace("\"", "").trim();

        File ficheiroPdf = resolverFicheiro(pdfEntrada);
        File pastaCerts  = new File(certsDir);

        if (!pastaCerts.exists() || !pastaCerts.isDirectory())
            throw new IllegalArgumentException("Pasta de certificados não encontrada: " + pastaCerts.getAbsolutePath());

        for (String cert : certs) {
            File fileCert = new File(pastaCerts, cert);
            if (!fileCert.exists())
                throw new IllegalArgumentException("Certificado não encontrado: " + fileCert.getAbsolutePath());
        }

        if (pdfSaida == null) {
            String nomeBase = ficheiroPdf.getName().replaceFirst("\\.pdf$", "");
            pdfSaida = new File(ficheiroPdf.getParent(), nomeBase + "_assinado.pdf").getAbsolutePath();
        }

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
            pastaCerts.getAbsolutePath(), certs, passwords,
            location, reason
        );
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    private static String pedirCaminhoPdf(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String entrada = sc.nextLine().trim().replace("\"", "");
            if (entrada.isEmpty()) continue;
            File f = resolverFicheiroOuNull(entrada);
            if (f != null) return entrada;
            System.out.println("    -> Ficheiro não encontrado: " + entrada + ". Tente novamente.");
        }
    }

    private static File resolverFicheiro(String caminho) {
        File f = resolverFicheiroOuNull(caminho);
        if (f == null)
            throw new IllegalArgumentException("Ficheiro não encontrado: " + caminho);
        return f;
    }

    private static File resolverFicheiroOuNull(String caminho) {
        File f = new File(caminho);
        if (!f.isAbsolute()) f = new File(System.getProperty("user.dir"), caminho);
        return (f.exists() && f.isFile()) ? f : null;
    }

    /**
     * Lê a password do terminal sem eco.
     * Fallback para Scanner em ambientes sem consola (IDEs).
     * Referência: AT06 – segurança no uso de chaves privadas.
     */
    private static char[] lerPasswordInterativa(String nomeCert) {
        String label = nomeCert.replace(".p12", "");
        Console console = System.console();
        if (console != null) {
            return console.readPassword("  Password para %s: ", label);
        } else {
            System.out.print("  Password para " + label + " (visível - IDE sem consola): ");
            try (Scanner sc = new Scanner(System.in)) {
                return sc.nextLine().toCharArray();
            }
        }
    }

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
        System.out.println("Uso: java -jar c4.jar [opções]");
        System.out.println();
        System.out.println("  Sem argumentos  → modo interativo (menu no terminal)");
        System.out.println();
        System.out.println("  --pdf       <caminho>    PDF a assinar");
        System.out.println("  --cert      <nome.p12>   Certificado .p12 (repetir para múltiplos)");
        System.out.println("  --out       <saida.pdf>  PDF de saída (default: <original>_assinado.pdf)");
        System.out.println("  --reason    <texto>      Razão da assinatura (default: texto do enunciado)");
        System.out.println("  --location  <texto>      Localização (default: ESTG)");
        System.out.println("  --pass      <password>   Password do .p12 (default: pedida interativamente)");
        System.out.println("  --certs-dir <pasta>      Pasta com os .p12 (default: ./certs)");
        System.out.println("  --help / -h              Esta ajuda");
        System.out.println();
        System.out.println("Exemplos:");
        System.out.println("  java -jar c4.jar");
        System.out.println("  java -jar c4.jar --pdf doc.pdf --cert aluno.p12 --reason \"Aceito os termos\"");
        System.out.println();
    }
}

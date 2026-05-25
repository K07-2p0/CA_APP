package pt.estg.ca.c4.cli;

import pt.estg.ca.c4.util.Logger;

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
 *      A aplicação apresenta um menu no terminal:
 *        - Lista os .p12 encontrados em certs/
 *        - Pergunta o caminho do PDF de entrada
 *        - Pergunta o PDF de saída (ou usa o default)
 *        - Pede a password de cada .p12 sem eco
 *
 *   B) MODO CLI (com argumentos)
 *      Para uso em scripts ou automação:
 *        --pdf <caminho>     PDF a assinar (obrigatório)
 *        --cert <nome.p12>   .p12 em certs/ (pode repetir)
 *        --out <saida.pdf>   PDF de saída (opcional)
 *        --pass <password>   Password (opcional; pede interativamente se omitida)
 *        --certs-dir <pasta> Pasta com os .p12 (opcional; default: ./certs)
 *
 * Segurança:
 *   Passwords lidas com Console.readPassword() – sem eco no terminal.
 *   Referência: AT06 – boas práticas com chaves privadas.
 */
public class ArgumentParser {

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

    /**
     * Menu interativo passo a passo:
     *   1. Mostrar .p12 disponíveis em certs/
     *   2. Pedir PDF de entrada
     *   3. Pedir PDF de saída
     *   4. Selecionar certificados
     *   5. Pedir passwords (sem eco)
     */
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
        File ficheiroPdf = resolverFicheiro(pdfEntradaStr);

        // --- PDF de saída ---
        String nomeDefault = ficheiroPdf.getName().replaceFirst("\\.pdf$", "") + "_assinado.pdf";
        String pdfSaidaDefault = new File(ficheiroPdf.getParent(), nomeDefault).getAbsolutePath();
        System.out.print("  PDF de saída   [" + nomeDefault + "]: ");
        String pdfSaidaInput = sc.nextLine().trim();
        String pdfSaida = pdfSaidaInput.isEmpty() ? pdfSaidaDefault : pdfSaidaInput;
        System.out.println();

        // --- Selecionar certificados ---
        List<String> certsSelecionados = new ArrayList<>();
        if (listaP12.length == 0) {
            throw new IllegalArgumentException(
                "Nenhum .p12 encontrado em certs/. Impossível continuar.");
        } else if (listaP12.length == 1) {
            // Só um .p12 → selecionar automaticamente
            certsSelecionados.add(listaP12[0].getName());
        } else {
            // Vários .p12 → perguntar quais usar (ou Enter para todos)
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
            pastaCerts.getAbsolutePath(), certsSelecionados, passwords
        );
    }

    // =========================================================================
    // MODO CLI
    // =========================================================================

    private static Configuracao modoCLI(String[] args) {
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

        File ficheiroPdf = resolverFicheiro(pdfEntrada);
        File pastaCerts = new File(certsDir);

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

        // Processar passwords: fornecidas via --pass ou pedidas interativamente
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

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    /**
     * Pede o caminho do PDF ao utilizador e valida que o ficheiro existe.
     * Aceita caminhos absolutos ou relativos à pasta corrente.
     */
    private static String pedirCaminhoPdf(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String entrada = sc.nextLine().trim();
            if (entrada.isEmpty()) continue;
            File f = resolverFicheiroOuNull(entrada);
            if (f != null) return entrada;
            System.out.println("    -> Ficheiro não encontrado: " + entrada + ". Tente novamente.");
        }
    }

    /** Resolve um caminho absoluto ou relativo; lança exceção se não existir. */
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
     * Lê a password do terminal sem eco (Console.readPassword).
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

    /** Resolve o caminho default da pasta certs/ (junto ao JAR ou na pasta corrente). */
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
        System.out.println("  --pdf       <caminho>     PDF a assinar");
        System.out.println("  --cert      <nome.p12>    Certificado .p12 em certs/ (repetir para múltiplos)");
        System.out.println("  --out       <saida.pdf>   PDF de saída (default: <original>_assinado.pdf)");
        System.out.println("  --pass      <password>    Password do .p12 (default: pedida interativamente)");
        System.out.println("  --certs-dir <pasta>       Pasta com os .p12 (default: ./certs)");
        System.out.println("  --help / -h               Esta ajuda");
        System.out.println();
    }
}

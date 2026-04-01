import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Decifrador {

    public static void main(String[] args) throws Exception {

        UI.banner();

        // --- Descobrir ficheiros .enc relativos ao diretório de execução ---
        File pasta = Config.pastaFicheiros().toFile();
        File[] ficheiros = pasta.listFiles((d, n) -> n.toLowerCase().endsWith(".enc"));

        if (ficheiros == null || ficheiros.length == 0) {
            UI.erro("Nenhum ficheiro .enc encontrado em: " + pasta.getAbsolutePath());
            return;
        }

        // --- Selecionar ficheiro ---
        System.out.println("  Ficheiros disponíveis:\n");
        for (int i = 0; i < ficheiros.length; i++)
            System.out.printf("    [%d] %s%n", i + 1, ficheiros[i].getName());

        System.out.print("\n  » Número do ficheiro: ");
        Scanner scanner = new Scanner(System.in);
        int opcao = scanner.nextInt() - 1;

        if (opcao < 0 || opcao >= ficheiros.length) {
            UI.erro("Opção inválida.");
            return;
        }

        File   alvo         = ficheiros[opcao];
        byte[] dadosCifrados = Files.readAllBytes(alvo.toPath());

        // --- Configurar e arrancar o engine ---
        int numThreads = Runtime.getRuntime().availableProcessors();
        UI.info(numThreads);

        BruteForceEngine engine = new BruteForceEngine(numThreads);

        Thread tp = UI.threadProgresso(engine);
        tp.start();

        BruteForceEngine.Resultado resultado = engine.executar(dadosCifrados);

        tp.interrupt();
        System.out.println(); // nova linha após a barra

        if (resultado == null) {
            UI.erro("Senha não encontrada no intervalo definido.");
            return;
        }

        // --- Guardar resultado ---
        Path pastaOut = Config.pastaResultados();
        if (!Files.exists(pastaOut)) Files.createDirectories(pastaOut);

        String nomeFinal = alvo.getName().replaceAll("(?i)(\\.pdf)?\\.enc$", "") + ".pdf";
        Path   destino   = pastaOut.resolve(nomeFinal);
        Files.write(destino, resultado.dados);

        UI.sucesso(resultado, destino.toAbsolutePath().toString());
    }
}
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Config {

    private Config() {}

    // Intervalo de senhas a testar
    public static final int INICIO = 1_000_000;
    public static final int FIM    = 9_999_999;
    public static final int TOTAL  = FIM - INICIO + 1;

    // Pasta de resultados: sempre relativa ao diretório de execução
    public static Path pastaResultados() {
        return Paths.get(System.getProperty("user.dir"), "resultados");
    }

    // Diretório onde procurar ficheiros .enc
    public static Path pastaFicheiros() {
        return Paths.get(System.getProperty("user.dir"));
    }

    // Quantos valores cada thread reporta de uma vez (reduz contention nos atomics)
    public static final int BATCH_PROGRESSO = 500;
}
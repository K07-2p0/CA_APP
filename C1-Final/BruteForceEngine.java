import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Motor de força bruta multi-threaded.
 * Divide o intervalo de senhas entre as threads disponíveis e procura
 * a chave AES que decifra o ficheiro como um PDF válido.
 */
public class BruteForceEngine {

    public static final class Resultado {
        public final String senha;
        public final byte[] dados;
        public final long   duracaoMs;

        public Resultado(String senha, byte[] dados, long duracaoMs) {
            this.senha     = senha;
            this.dados     = dados;
            this.duracaoMs = duracaoMs;
        }
    }

    private final AtomicBoolean           encontrado    = new AtomicBoolean(false);
    private final AtomicInteger           progresso     = new AtomicInteger(0);
    private final AtomicReference<String> senhaRef      = new AtomicReference<>();
    private final AtomicReference<byte[]> dadosRef      = new AtomicReference<>();

    private final int numThreads;

    public BruteForceEngine(int numThreads) {
        this.numThreads = numThreads;
    }

    public int getProgresso() {
        return progresso.get();
    }

    public boolean isEncontrado() {
        return encontrado.get();
    }

    /**
     * Executa a pesquisa de força bruta e bloqueia até terminar.
     *
     * @return {@link Resultado} se encontrado, ou {@code null} se não encontrado.
     */
    public Resultado executar(byte[] dadosCifrados) throws InterruptedException {
        int total = Config.TOTAL;
        int bloco = total / numThreads;

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> tarefas = new ArrayList<>(numThreads);

        long inicio = System.currentTimeMillis();

        for (int t = 0; t < numThreads; t++) {
            int de  = Config.INICIO + t * bloco;
            int ate = (t == numThreads - 1) ? Config.FIM : de + bloco - 1;
            tarefas.add(pool.submit(() -> pesquisar(de, ate, dadosCifrados)));
        }

        for (Future<?> f : tarefas) {
            try { f.get(); }
            catch (ExecutionException ignored) {}
        }

        pool.shutdown();
        long duracao = System.currentTimeMillis() - inicio;

        if (!encontrado.get()) return null;
        return new Resultado(senhaRef.get(), dadosRef.get(), duracao);
    }

    // Chamado por cada thread — instancia o CryptoUtils localmente (sem partilha)
    private void pesquisar(int de, int ate, byte[] dadosCifrados) {
        CryptoUtils crypto = new CryptoUtils();
        int batch = Config.BATCH_PROGRESSO;
        int contador = 0;

        for (int i = de; i <= ate; i++) {
            if (encontrado.get()) return;

            byte[] resultado = crypto.tentarSenha(i, dadosCifrados);

            if (resultado != null) {
                if (encontrado.compareAndSet(false, true)) {
                    senhaRef.set(Integer.toString(i));
                    dadosRef.set(resultado);
                }
                return;
            }

            // Atualizar progresso em batch para reduzir contention
            if (++contador == batch) {
                progresso.addAndGet(batch);
                contador = 0;
            }
        }

        if (contador > 0) progresso.addAndGet(contador);
    }
}
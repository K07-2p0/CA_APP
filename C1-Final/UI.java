public final class UI {

    private UI() {}

    public static void banner() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────┐");
        System.out.println("  │           DECIFRADOR AES  –  v3.0       │");
        System.out.println("  │      Força Bruta com Multithreading      │");
        System.out.println("  └─────────────────────────────────────────┘");
        System.out.println();
    }

    public static void info(int numThreads) {
        System.out.printf("  Processadores disponíveis : %d%n", numThreads);
        System.out.printf("  Intervalo a testar        : %d – %d  (%,d combinações)%n%n",
            Config.INICIO, Config.FIM, Config.TOTAL);
    }

    public static void erro(String msg) {
        System.err.println("\n  [ERRO] " + msg);
    }

    public static void sucesso(BruteForceEngine.Resultado r, String destino) {
        System.out.println();
        System.out.println("\n  ╔══════════════════════════════════════╗");
        System.out.println("  ║           SENHA ENCONTRADA!          ║");
        System.out.printf ("  ║   Senha : %-27s║%n", r.senha);
        System.out.printf ("  ║   Tempo : %-27s║%n", formatarTempo(r.duracaoMs));
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println("\n  Ficheiro guardado em: " + destino);
    }

    // Thread de progresso — corre em daemon até o engine terminar
    public static Thread threadProgresso(BruteForceEngine engine) {
        Thread t = new Thread(() -> {
            int largura = 40;
            while (!engine.isEncontrado()) {
                renderBarra(engine.getProgresso(), largura);
                try { Thread.sleep(200); }
                catch (InterruptedException e) { break; }
            }
            renderBarra(Config.TOTAL, largura);
        });
        t.setDaemon(true);
        return t;
    }

    private static void renderBarra(int feito, int largura) {
        double pct    = Math.min(1.0, (double) feito / Config.TOTAL);
        int    blocos = (int) (pct * largura);
        String barra  = "█".repeat(blocos) + "░".repeat(largura - blocos);
        System.out.printf("\r  [%s] %5.1f%%  (%,d / %,d)",
            barra, pct * 100, feito, Config.TOTAL);
    }

    private static String formatarTempo(long ms) {
        if (ms < 1_000)  return ms + " ms";
        if (ms < 60_000) return String.format("%.2f s", ms / 1000.0);
        return String.format("%d min %02d s", ms / 60_000, (ms % 60_000) / 1000);
    }
}
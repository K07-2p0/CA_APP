package pt.estg.ca.c4.util;

/**
 * Utilitário de logging para a linha de comandos.
 *
 * Prefixos:
 *   [OK]    Operação concluída com sucesso (verde)
 *   [ERRO]  Erro bloqueante (vermelho, para stderr)
 *   [INFO]  Informação de progresso (cyan)
 *   [AVISO] Situação anómala não bloqueante (amarelo)
 */
public class Logger {

    private static final String RESET   = "\u001B[0m";
    private static final String VERDE   = "\u001B[32m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String AMARELO = "\u001B[33m";
    private static final String CYAN    = "\u001B[36m";

    public static void ok(String msg)    { System.out.println(VERDE    + "[OK]   " + RESET + msg); }
    public static void erro(String msg)  { System.err.println(VERMELHO  + "[ERRO] " + RESET + msg); }
    public static void info(String msg)  { System.out.println(CYAN      + "[INFO] " + RESET + msg); }
    public static void aviso(String msg) { System.out.println(AMARELO   + "[AVISO]" + RESET + msg); }
}

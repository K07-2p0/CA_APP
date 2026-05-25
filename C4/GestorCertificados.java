import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;

/**
 * GestorCertificados
 *
 * Lista os ficheiros .p12 disponíveis em certs/,
 * permite ao utilizador selecionar um ou múltiplos,
 * pede a password de cada um e carrega os dados criptográficos.
 *
 * A password é tratada como char[] e apagada da memória após uso,
 * nunca sendo escrita em disco.
 */
public class GestorCertificados {

    private static final String PASTA_CERTS = "certs";

    /**
     * Representa os dados de um signatário:
     * nome, chave privada e cadeia de certificados.
     */
    public static class Signatario {
        public final String        nome;
        public final PrivateKey    chavePrivada;
        public final Certificate[] cadeia;

        public Signatario(String nome, PrivateKey chavePrivada, Certificate[] cadeia) {
            this.nome         = nome;
            this.chavePrivada = chavePrivada;
            this.cadeia       = cadeia;
        }
    }

    /**
     * Lista os .p12 em certs/, mostra-os numerados, deixa o utilizador
     * escolher quais usar e carrega os selecionados.
     *
     * @param modo 1 = selecionar apenas um, 2 = selecionar múltiplos
     * @return lista de signatários selecionados e prontos a usar
     */
    public static List<Signatario> selecionarECarregar(int modo) throws Exception {
        File pastaCerts = new File(PASTA_CERTS);

        if (!pastaCerts.exists() || !pastaCerts.isDirectory()) {
            throw new Exception("Pasta 'certs/' não encontrada.");
        }

        File[] ficheiros = pastaCerts.listFiles(
            (dir, nome) -> nome.toLowerCase().endsWith(".p12")
        );

        if (ficheiros == null || ficheiros.length == 0) {
            throw new Exception("Nenhum ficheiro .p12 encontrado em 'certs/'.");
        }

        Arrays.sort(ficheiros);

        // Mostrar certificados disponíveis
        System.out.println("Certificados disponíveis em certs/:");
        for (int i = 0; i < ficheiros.length; i++) {
            System.out.println("  [" + (i + 1) + "] " + ficheiros[i].getName());
        }
        System.out.println();

        // Pedir seleção conforme o modo escolhido
        Scanner sc = new Scanner(System.in);
        List<Integer> indices;

        if (modo == 1) {
            System.out.print("Seleciona o certificado (número): ");
            int n = lerNumeroSimples(ficheiros.length, sc);
            indices = new ArrayList<>();
            indices.add(n);
        } else {
            System.out.println("Quais certificados usar? (ex: 1  ou  1,2,3)");
            System.out.print("Seleção: ");
            indices = lerSelecaoMultipla(ficheiros.length, sc);
        }
        System.out.println();

        // Carregar cada certificado selecionado
        List<Signatario> signatarios = new ArrayList<>();
        Console console = System.console();

        for (int indice : indices) {
            File ficheiro = ficheiros[indice - 1];
            String nome = ficheiro.getName().replace(".p12", "");

            char[] password;

            // Console.readPassword() oculta os caracteres escritos no terminal
            // Se não estiver disponível (ex: terminal do VS Code) usa Scanner
            if (console != null) {
                password = console.readPassword("  Password para [%s]: ", nome);
            } else {
                System.out.print("  Password para [" + nome + "]: ");
                password = new Scanner(System.in).nextLine().toCharArray();
            }

            try {
                PrivateKey    chave  = carregarChavePrivada(ficheiro, password);
                Certificate[] cadeia = carregarCadeia(ficheiro, password);
                signatarios.add(new Signatario(nome, chave, cadeia));
                System.out.println("  -> OK");
            } catch (Exception e) {
                throw new Exception("Erro ao carregar '" + nome +
                                    "': password incorreta ou ficheiro inválido.");
            } finally {
                // Limpar a password da memória imediatamente após uso
                Arrays.fill(password, '\0');
            }

            System.out.println();
        }

        return signatarios;
    }

    /**
     * Lê um único número válido dentro do intervalo [1, max].
     * Repete até o utilizador introduzir um valor válido.
     *
     * @param max número máximo disponível
     * @param sc  Scanner para leitura do terminal
     * @return número introduzido
     */
    private static int lerNumeroSimples(int max, Scanner sc) {
        while (true) {
            try {
                int n = Integer.parseInt(sc.nextLine().trim());
                if (n >= 1 && n <= max) return n;
                System.out.print("  Número inválido. Escolhe entre 1 e " + max + ": ");
            } catch (NumberFormatException e) {
                System.out.print("  Entrada inválida. Escreve um número: ");
            }
        }
    }

    /**
     * Lê uma seleção múltipla no formato "1" ou "1,2,3".
     * Ignora repetições e valida que os números estão no intervalo disponível.
     *
     * @param max número máximo disponível
     * @param sc  Scanner para leitura do terminal
     * @return lista de índices selecionados pela ordem introduzida
     */
    private static List<Integer> lerSelecaoMultipla(int max, Scanner sc) {
        while (true) {
            try {
                String[] partes = sc.nextLine().trim().split(",");
                List<Integer> indices = new ArrayList<>();
                for (String parte : partes) {
                    int n = Integer.parseInt(parte.trim());
                    if (n < 1 || n > max) throw new NumberFormatException();
                    if (!indices.contains(n)) indices.add(n);
                }
                if (indices.isEmpty()) throw new NumberFormatException();
                return indices;
            } catch (NumberFormatException e) {
                System.out.print("  Seleção inválida. Tenta novamente (ex: 1  ou  1,2): ");
            }
        }
    }

    /**
     * Carrega a chave privada de um ficheiro PKCS#12.
     */
    private static PrivateKey carregarChavePrivada(File ficheiro, char[] password) throws Exception {
        KeyStore ks = abrirKeystore(ficheiro, password);
        String alias = ks.aliases().nextElement();
        return (PrivateKey) ks.getKey(alias, password);
    }

    /**
     * Carrega a cadeia de certificados de um ficheiro PKCS#12.
     * Inclui o certificado do aluno, da SubCA e da Root CA.
     */
    private static Certificate[] carregarCadeia(File ficheiro, char[] password) throws Exception {
        KeyStore ks = abrirKeystore(ficheiro, password);
        String alias = ks.aliases().nextElement();
        return ks.getCertificateChain(alias);
    }

    /**
     * Abre um ficheiro PKCS#12 e devolve a KeyStore carregada.
     */
    private static KeyStore abrirKeystore(File ficheiro, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ficheiro)) {
            ks.load(fis, password);
        }
        return ks;
    }
}
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * App C1 — Decifrar o ficheiro PDF cifrado pelo professor.
 *
 * Algoritmo : AES
 * Modo      : ECB  (sem IV, cada bloco decifrado de forma independente)
 * Padding   : PKCS5Padding
 * Chave     : primeiros 16 chars hex do SHA-1 de um número aleatório de 7 dígitos
 *
 * Lógica: testar todos os números de 1000000 a 9999999,
 *         calcular a chave para cada um e tentar decifrar.
 *         Quando os primeiros 4 bytes forem "%PDF", encontrámos a chave certa.
 *
 * Uso: java -cp src DecryptTool input/documento.pdf.enc
 */
public class DecryptTool {

    public static void main(String[] args) throws Exception {

        // Ficheiro cifrado a decifrar
        String ficheiroEncriptado = args.length > 0 ? args[0] : "input/documento.pdf.enc";

        // Ficheiro de saída: mesmo nome mas sem ".enc"
        String ficheiroOrigem = ficheiroEncriptado.replaceAll("\\.enc$", "");

        // Ler o ficheiro cifrado uma vez (evita ler do disco em cada tentativa)
        byte[] encryptedData = Files.readAllBytes(Path.of(ficheiroEncriptado));

        System.out.println("A tentar decifrar: " + ficheiroEncriptado);
        System.out.println("Espaço de procura: 1000000 → 9999999");
        System.out.println("────────────────────────────────────────");

        // ── Bruteforce ───────────────────────────────────────────────────────────
        // Percorre todos os números de 7 dígitos possíveis
        for (int i = 1000000; i <= 9999999; i++) {

            String password = Integer.toString(i);

            // Derivar chave: SHA-1 → primeiros 16 chars hex
            String hashedPassword = hashPassword(password);
            String key = hashedPassword.substring(0, 16);

            try {
                // Tentar decifrar com esta chave
                byte[] decrypted = decryptFile("AES/ECB/PKCS5Padding", key, encryptedData);

                // Validar se o resultado é um PDF válido
                if (validPDF(decrypted)) {

                    // Garantir que a pasta de output existe
                    Path outPath = Path.of(ficheiroOrigem);
                    if (outPath.getParent() != null) {
                        Files.createDirectories(outPath.getParent());
                    }

                    // Gravar o PDF decifrado
                    Files.write(outPath, decrypted);

                    // Mostrar resultado
                    System.out.println("Número encontrado    : " + password);
                    System.out.println("SHA-1 completo       : " + hashedPassword);
                    System.out.println("Chave AES (16 chars) : " + key);
                    System.out.println("PDF guardado em      : " + ficheiroOrigem);
                    return; // terminar após encontrar
                }

            } catch (Exception e) {
                // Chave errada → padding inválido → continuar
            }

            // Progresso a cada 100 000 tentativas
            if (i % 100000 == 0) {
                System.out.println("Testado até: " + i);
            }
        }

        System.out.println("Nenhum PDF válido encontrado no intervalo.");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // hashPassword — SHA-1
    //   1. Converte a string do número para bytes (ASCII/UTF-8)
    //   2. Calcula digest SHA-1 → 20 bytes / 160 bits
    //   3. Converte cada byte para 2 chars hex lowercase
    //   4. Devolve string hex de 40 caracteres
    // ─────────────────────────────────────────────────────────────────────────────
    private static String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(password.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // decryptFile — AES/ECB/PKCS5Padding
    //   1. Cria a SecretKeySpec com os 16 bytes ASCII da chave
    //   2. Inicializa o Cipher em modo DECRYPT
    //   3. Decifra os bytes e devolve o resultado
    //   → Se a chave estiver errada, o padding será inválido e lança exceção
    // ─────────────────────────────────────────────────────────────────────────────
    private static byte[] decryptFile(String algorithm, String key,
                                       byte[] encryptedData) throws Exception {
        // Chave AES: 16 bytes ASCII (os 16 primeiros chars hex do SHA-1)
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");

        // Cipher com AES / ECB / PKCS5Padding
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        // Decifrar e devolver os bytes
        return cipher.doFinal(encryptedData);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // validPDF — fornecido no enunciado
    // Verifica a assinatura mágica do formato PDF
    //   → primeiros 4 bytes = %PDF (0x25 0x50 0x44 0x46)
    // ─────────────────────────────────────────────────────────────────────────────
    private static boolean validPDF(byte[] data) {
        if (data.length < 10)
            return false;
        return new String(data, 0, 4).equals("%PDF");
    }
}
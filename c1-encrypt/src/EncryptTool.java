import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Recriação da app do professor para cifrar o PDF.
 *
 * Algoritmo : AES
 * Modo      : ECB  (sem IV, cada bloco cifrado de forma independente)
 * Padding   : PKCS5Padding (preenche o último bloco com N bytes de valor N)
 * Chave     : primeiros 16 chars hex do SHA-1 de um número aleatório de 7 dígitos
 *
 * Uso: java -cp src EncryptTool input/documento.pdf
 */
public class EncryptTool {

    public static void main(String[] args) throws Exception {

        // Ficheiro de entrada (PDF original)
        String ficheiroOrigem    = args.length > 0 ? args[0] : "input/documento.pdf";
        String ficheiroEncriptado = ficheiroOrigem; // ".enc" adicionado em encryptFile

        // ── Gerar número aleatório de 7 dígitos ─────────────────────────────────
        // nextInt(9000000) → [0, 8999999] + 1000000 → [1000000, 9999999]
        Random random = new Random();
        String password = Integer.toString(random.nextInt(9000000) + 1000000);

        // ── Derivar chave: SHA-1 do número → primeiros 16 chars hex ─────────────
        String hashedPassword = hashPassword(password);
        String key = hashedPassword.substring(0, 16);

        // ── Cifrar o ficheiro ────────────────────────────────────────────────────
        encryptFile("AES/ECB/PKCS5Padding", key, ficheiroOrigem, ficheiroEncriptado + ".enc");

        // ── Mostrar resultado (o professor guardaria este número por grupo) ───────
        System.out.println("Ficheiro original   : " + ficheiroOrigem);
        System.out.println("Ficheiro cifrado    : " + ficheiroEncriptado + ".enc");
        System.out.println("Número aleatório    : " + password);
        System.out.println("SHA-1 completo      : " + hashedPassword);
        System.out.println("Chave AES (16 chars): " + key);
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
    // encryptFile — AES/ECB/PKCS5Padding
    //   1. Lê todos os bytes do PDF original
    //   2. Cria a SecretKeySpec com os 16 bytes ASCII da chave
    //   3. Inicializa o Cipher em modo ENCRYPT
    //   4. Cifra os bytes e grava o ficheiro .enc
    // ─────────────────────────────────────────────────────────────────────────────
    private static void encryptFile(String algorithm, String key,
                                     String inputPath, String outputPath) throws Exception {
        byte[] inputBytes = Files.readAllBytes(Path.of(inputPath));

        // Chave AES: 16 bytes ASCII (os 16 primeiros chars hex do SHA-1)
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");

        // Cipher com AES / ECB / PKCS5Padding
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        // Cifrar
        byte[] encryptedBytes = cipher.doFinal(inputBytes);

        // Garantir que a pasta de output existe
        Path outPath = Path.of(outputPath);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }

        // Gravar o ficheiro cifrado
        Files.write(outPath, encryptedBytes);
        System.out.println("Cifrado com sucesso → " + outputPath);
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
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;


public final class CryptoUtils {

    // Objetos reutilizáveis por instância (evita alocações no loop quente)
    private final MessageDigest sha1;
    private final Cipher        cipher;

    // Buffer reutilizável para o hex do SHA-1 (40 chars)
    private final char[] hexBuf = new char[40];
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public CryptoUtils() {
        try {
            sha1   = MessageDigest.getInstance("SHA-1");
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inicializar CryptoUtils", e);
        }
    }

    /**
     * Tenta decifrar {@code dados} usando a senha numérica {@code senha}.
     * Devolve os bytes decifrados se o resultado for um PDF, ou {@code null} caso contrário.
     */
    public byte[] tentarSenha(int senha, byte[] dados) {
        try {
            String hash  = sha1hex(senha);
            String chave = hash.substring(0, 16);

            SecretKeySpec skey = new SecretKeySpec(chave.getBytes(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, skey);
            byte[] resultado = cipher.doFinal(dados);

            return isPDF(resultado) ? resultado : null;
        } catch (Exception e) {
            return null;
        }
    }

    // SHA-1 em hex sem alocar String intermédia desnecessária
    private String sha1hex(int valor) {
        sha1.reset();
        String s = Integer.toString(valor);
        byte[] b = sha1.digest(s.getBytes());
        for (int i = 0; i < b.length; i++) {
            hexBuf[i * 2]     = HEX[(b[i] >> 4) & 0xF];
            hexBuf[i * 2 + 1] = HEX[b[i] & 0xF];
        }
        return new String(hexBuf);
    }

    private static boolean isPDF(byte[] d) {
        return d.length >= 4
            && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
    }
}
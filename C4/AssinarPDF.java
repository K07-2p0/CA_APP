import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * AssinarPDF - Programa Principal
 * Criptografia Aplicada | LSIRC 2025/2026 | Componente 4 (C4)
 *
 * Fluxo:
 *   1. Pede ao utilizador o caminho da pasta onde estão os PDFs
 *   2. Lista os PDFs encontrados e deixa selecionar um
 *   3. Pergunta se quer assinar com um ou múltiplos certificados
 *   4. Lista os certificados em certs/ e deixa selecionar
 *   5. Pede a password de cada certificado selecionado
 *   6. Assina o PDF e guarda o resultado na mesma pasta do original
 *
 * Propriedades de assinatura obrigatórias (enunciado C4):
 *   Local : ESTG
 *   Razão : Compreendo e aceito as regras do trabalho prático e eventuais
 *           alterações pontuais que sejam introduzidas.
 */
public class AssinarPDF {

    static final String LOCAL = "ESTG";
    static final String RAZAO = "Compreendo e aceito as regras do trabalho prático " +
                                "e eventuais alterações pontuais que sejam introduzidas.";

    static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("  C4 - Assinatura Digital de PDF | LSIRC    ");
        System.out.println("==============================================");
        System.out.println();

        // --- 1. Pedir caminho e selecionar PDF ---
        File pdfSelecionado = selecionarPDF();
        System.out.println("PDF selecionado: " + pdfSelecionado.getName());
        System.out.println();

        // --- 2. Perguntar modo de assinatura ---
        System.out.println("Modo de assinatura:");
        System.out.println("  [1] Assinar com um certificado");
        System.out.println("  [2] Assinar com múltiplos certificados");
        System.out.print("Opção: ");
        int modo = lerNumero(1, 2);
        System.out.println();

        // --- 3. Listar certificados e deixar o utilizador escolher ---
        List<GestorCertificados.Signatario> signatarios =
            GestorCertificados.selecionarECarregar(modo);
        System.out.println(signatarios.size() + " certificado(s) carregado(s).");
        System.out.println();

        // --- 4. Definir nome do PDF de saída ---
        // Adiciona "_assinado" ao nome do PDF original e guarda na mesma pasta
        String nomeSaida = pdfSelecionado.getName().replace(".pdf", "_assinado.pdf");
        File pdfSaida = new File(pdfSelecionado.getParent(), nomeSaida);

        // --- 5. Assinar sequencialmente com cada certificado selecionado ---
        File ficheiroAtual = pdfSelecionado;

        for (int i = 0; i < signatarios.size(); i++) {
            GestorCertificados.Signatario s = signatarios.get(i);
            System.out.println("A assinar com: " + s.nome +
                               " (" + (i + 1) + "/" + signatarios.size() + ")...");

            File ficheiroTemp = File.createTempFile("c4_assinatura_", ".pdf");
            ficheiroTemp.deleteOnExit();

            aplicarAssinatura(ficheiroAtual, ficheiroTemp, s);

            ficheiroAtual = ficheiroTemp;
            System.out.println("  -> OK");
        }

        // --- 6. Guardar resultado final ---
        Files.copy(ficheiroAtual.toPath(), pdfSaida.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);

        System.out.println();
        System.out.println("==============================================");
        System.out.println("  Concluído! PDF guardado em:");
        System.out.println("  " + pdfSaida.getAbsolutePath());
        System.out.println("==============================================");
    }

    /**
     * Pede ao utilizador um caminho de pasta, lista os PDFs encontrados
     * e deixa selecionar um escrevendo o número correspondente.
     *
     * Repete até o utilizador introduzir um caminho válido com PDFs.
     *
     * @return ficheiro PDF selecionado
     */
    static File selecionarPDF() {
        while (true) {
            System.out.print("Caminho da pasta onde estão os PDFs: ");
            String caminho = scanner.nextLine().trim();

            File pasta = new File(caminho);
            if (!pasta.exists() || !pasta.isDirectory()) {
                System.out.println("  [ERRO] Pasta não encontrada. Tenta novamente.");
                System.out.println();
                continue;
            }

            // Listar ficheiros PDF na pasta
            File[] pdfs = pasta.listFiles(
                (dir, nome) -> nome.toLowerCase().endsWith(".pdf")
            );

            if (pdfs == null || pdfs.length == 0) {
                System.out.println("  [ERRO] Nenhum PDF encontrado em: " + caminho);
                System.out.println();
                continue;
            }

            Arrays.sort(pdfs);

            System.out.println();
            System.out.println("PDFs encontrados:");
            for (int i = 0; i < pdfs.length; i++) {
                System.out.println("  [" + (i + 1) + "] " + pdfs[i].getName());
            }
            System.out.println();

            System.out.print("Seleciona o PDF (número): ");
            int escolha = lerNumero(1, pdfs.length);
            System.out.println();

            return pdfs[escolha - 1];
        }
    }

    /**
     * Aplica uma assinatura digital a um PDF e guarda o resultado.
     * Usa saveIncremental() para não invalidar assinaturas anteriores
     * quando são aplicadas múltiplas assinaturas.
     *
     * @param entrada    ficheiro PDF de entrada
     * @param saida      ficheiro PDF de saída com a nova assinatura
     * @param signatario dados do signatário (nome, chave, certificados)
     */
    static void aplicarAssinatura(File entrada, File saida,
                                  GestorCertificados.Signatario signatario) throws Exception {

        PDSignature assinatura = new PDSignature();
        assinatura.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        assinatura.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        assinatura.setName(signatario.nome);
        assinatura.setLocation(LOCAL);
        assinatura.setReason(RAZAO);
        assinatura.setSignDate(Calendar.getInstance());

        // Reservar espaço suficiente para o bloco da assinatura no PDF
        SignatureOptions opcoes = new SignatureOptions();
        opcoes.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);

        // Gerador do bloco criptográfico PKCS#7
        GeradorAssinatura gerador = new GeradorAssinatura(
            signatario.chavePrivada, signatario.cadeia
        );

        try (PDDocument documento = Loader.loadPDF(entrada);
             FileOutputStream fos = new FileOutputStream(saida)) {

            documento.addSignature(assinatura, gerador, opcoes);
            documento.saveIncremental(fos);
        }
    }

    /**
     * Lê um número do terminal dentro do intervalo [min, max].
     * Repete até o utilizador introduzir um valor válido.
     *
     * @param min valor mínimo aceite
     * @param max valor máximo aceite
     * @return número introduzido pelo utilizador
     */
    static int lerNumero(int min, int max) {
        while (true) {
            try {
                int valor = Integer.parseInt(scanner.nextLine().trim());
                if (valor >= min && valor <= max) return valor;
                System.out.print("  Número inválido. Escolhe entre " + min + " e " + max + ": ");
            } catch (NumberFormatException e) {
                System.out.print("  Entrada inválida. Escreve um número: ");
            }
        }
    }
}
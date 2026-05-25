# C4 – Assinatura Digital de PDFs

**Criptografia Aplicada | LSIRC ESTG 2025/2026**

Aplicação em linha de comandos (Java 11+) para assinar digitalmente ficheiros PDF
usando certificados PKCS#12 (X.509 v3), com suporte a múltiplas assinaturas sequenciais.

---

## Dependências

| Biblioteca | Versão | Propósito |
|---|---|---|
| Apache PDFBox | 3.0.2 | Leitura/escrita/assinatura de PDFs |
| BouncyCastle PKIX | 1.78.1 | CMS SignedData, cadeia PKI |
| BouncyCastle Provider | 1.78.1 | JCA provider (SHA256withRSA, PKCS12) |

---

## Estrutura do Projeto

```
C4/
├── pom.xml                          ← Dependências Maven + fat JAR
├── certs/                           ← Colocar aqui os ficheiros .p12
│   ├── aluno1.p12
│   └── aluno2.p12
├── docs/                            ← PDFs de entrada/saída (sugestão)
└── src/main/java/pt/estg/ca/c4/
    ├── Main.java                    ← Ponto de entrada (CLI)
    ├── cli/
    │   ├── ArgumentParser.java      ← Parse e validação de argumentos
    │   └── Configuracao.java        ← DTO com a configuração do programa
    ├── cert/
    │   ├── CertificadoLoader.java   ← Carrega e valida o .p12
    │   └── Signatario.java          ← Modelo: chave privada + cert + cadeia
    ├── pdf/
    │   └── AssinadorPDF.java        ← Assinatura incremental com PDFBox + BC
    └── util/
        └── Logger.java              ← Mensagens coloridas no terminal
```

---

## Como Compilar

```bash
# Requer Maven 3.6+ e Java 11+
cd C4
mvn clean package -q
# Gera: target/c4-assinar-pdf-1.0.0.jar (fat JAR, portável)
```

---

## Como Usar

```bash
# Assinar com um certificado (password pedida interativamente, sem eco)
java -jar target/c4-assinar-pdf-1.0.0.jar \
     --pdf docs/acordo.pdf \
     --cert aluno1.p12

# Assinar com dois certificados (assinaturas sequenciais e incrementais)
java -jar target/c4-assinar-pdf-1.0.0.jar \
     --pdf docs/acordo.pdf \
     --cert aluno1.p12 \
     --cert aluno2.p12 \
     --out docs/acordo_assinado.pdf

# Assinar um PDF em qualquer local da máquina
java -jar target/c4-assinar-pdf-1.0.0.jar \
     --pdf /home/adriano/Desktop/acordo_confidencialidade.pdf \
     --cert aluno1.p12 \
     --cert aluno2.p12

# Pasta de certs personalizada
java -jar target/c4-assinar-pdf-1.0.0.jar \
     --pdf docs/acordo.pdf \
     --cert aluno1.p12 \
     --certs-dir /caminho/para/certs
```

---

## Propriedades da Assinatura (C4 – Enunciado)

| Campo | Valor |
|---|---|
| **Location** | ESTG |
| **Reason** | Compreendo e aceito as regras do trabalho prático e eventuais alterações pontuais que sejam introduzidas. |
| **Name** | CN do certificado (número de aluno) |

---

## Conceitos Aplicados

| Componente | Conceito das Aulas |
|---|---|
| `KeyStore.getInstance("PKCS12", "BC")` | APL03_05 – provider JCA explícito |
| `Security.insertProviderAt(new BouncyCastleProvider(), 1)` | APL03_05 – instalação dinâmica |
| `cert.checkValidity()` | AT07 – ciclo de vida do certificado |
| `keyUsage[0]` (digitalSignature) | AT07 – tabela KeyUsage |
| `SHA256withRSA` | AT05 – SHA-2 recomendado; MD5/SHA-1 proibidos |
| Cadeia PKI no CMS | AT07 – hierarquias de confiança |
| `saveIncremental()` | AT09 – assinatura interna, não destrutiva |

---

## Verificação do PDF Assinado

Abrir o PDF gerado no **Adobe Acrobat Reader** (gratuito) para verificar:
- Painel de assinaturas com o CN de cada signatário
- Cadeia de confiança: Aluno → SubCA Grupo XX → LSIRC Root CA 2026
- Data/hora de cada assinatura
- Location: ESTG | Reason: conforme enunciado

Para o Adobe reconhecer a cadeia como confiável, importar o certificado
da **LSIRC Root CA 2026** nas autoridades de certificação do Acrobat.

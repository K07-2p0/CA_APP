# C4 - Assinatura Digital de PDF
### Criptografia Aplicada | LSIRC 2025/2026

---

## Estrutura do projeto

```
c4-pdf-signer/
├── lib/                        ← coloca aqui os 3 JARs (ver abaixo)
├── certs/                      ← coloca aqui os .p12 de cada elemento
├── config.properties           ← passwords e nome do PDF
├── AssinarPDF.java             ← programa principal
├── GestorCertificados.java     ← carrega os certificados da pasta certs/
├── GeradorAssinatura.java      ← gera o bloco criptográfico PKCS#7
├── compilar.sh / compilar.bat
└── correr.sh / correr.bat
```

---

## Passos para usar

### 1. Descarregar os JARs e colocar em lib/

| Ficheiro | Link |
|---|---|
| `pdfbox-app-3.0.x.jar` | https://pdfbox.apache.org/download.html |
| `bcpkix-jdk18on-1.78.jar` | https://www.bouncycastle.org/download/bouncy-castle-java/ |
| `bcprov-jdk18on-1.78.jar` | https://www.bouncycastle.org/download/bouncy-castle-java/ |

### 2. Colocar os certificados em certs/

```
certs/
├── aluno1.p12
└── aluno2.p12
```

### 3. Preencher o config.properties

```properties
pdf.entrada=acordo_grupo.pdf
pdf.saida=acordo_grupo_assinado.pdf

aluno1.password=a_minha_password
aluno2.password=outra_password
```

O nome antes de `.password` tem de corresponder exatamente ao nome do ficheiro .p12.

### 4. Compilar e correr

**Linux / Mac:**
```bash
chmod +x compilar.sh correr.sh
./compilar.sh
./correr.sh
```

**Windows:**
```
compilar.bat
correr.bat
```

---

## Verificar as assinaturas

Abre o PDF no **Adobe Acrobat Reader** e vai ao painel de assinaturas para confirmar que todas estão válidas.

---

## Submissão no Moodle

Submeter o ZIP com os 3 ficheiros `.java`, o `config.properties` (sem passwords), os scripts e o PDF assinado.
Não incluir os `.p12` nem os JARs.

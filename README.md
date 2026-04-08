# 🔓 Decifrador AES — Força Bruta com Multithreading

> **Número de encriptação:** `6753090`   
> **Grupo:** G15  
> **Unidade Curricular:** Criptografia Aplicada (CA) — 2026

---

## Descrição

Aplicação Java que realiza um ataque de **força bruta com múltiplas threads** para descobrir a senha numérica que cifrou um ficheiro PDF com AES. O programa testa sistematicamente todos os números inteiros no intervalo `[1 000 000, 9 999 999]`, derivando a chave AES a partir do hash SHA-1 de cada candidato, até encontrar aquele que produz um PDF válido.

O ficheiro alvo pré-incluído é `Encrypted_G15.pdf.enc`. O resultado decifrado é guardado na pasta `resultados/`.

---

## Estrutura do Projecto

```
CA_APP-main/
├── 2026-CA-TP_v01.pdf           # Enunciado do trabalho prático
├── README.md                    # Este ficheiro
└── C1-Final/
    ├── BruteForceEngine.java    # Motor de força bruta (multi-threaded)
    ├── Config.java              # Configurações globais (intervalo, pastas, batch)
    ├── CryptoUtils.java         # Utilitários criptográficos (SHA-1 + AES/ECB)
    ├── Decifrador.java          # Ponto de entrada (main)
    ├── UI.java                  # Interface de consola (banner, barra de progresso)
    ├── Encrypted_G15.pdf.enc    # Ficheiro cifrado do Grupo 15
    └── resultados/
        └── Encrypted_G15.pdf   # PDF decifrado (resultado da execução)
```

---

## Algoritmo de Decifração

1. Para cada senha `i` no intervalo `[1 000 000, 9 999 999]`:
   - Calcula `SHA-1(string(i))` → representação hexadecimal de 40 caracteres
   - Usa os **primeiros 16 caracteres** como chave AES-128
   - Tenta decifrar o ficheiro `.enc` com `AES/ECB/PKCS5Padding`
   - Verifica se o resultado começa com o magic number `%PDF`
2. Quando a senha correcta é encontrada, o PDF decifrado é guardado em `resultados/`

**Senha encontrada:** `6753090`

---

## Arquitectura

| Classe              | Responsabilidade                                                                 |
|---------------------|---------------------------------------------------------------------------------|
| `Decifrador`        | Ponto de entrada; lê ficheiros `.enc`, lança o motor, guarda o resultado        |
| `BruteForceEngine`  | Divide o espaço de senhas por N threads, agrega progresso e resultado           |
| `CryptoUtils`       | Instância por thread; calcula SHA-1 e tenta decifrar com AES/ECB               |
| `Config`            | Constantes: intervalo `[1 000 000, 9 999 999]`, pastas, tamanho do batch        |
| `UI`                | Banner, barra de progresso animada, mensagens de sucesso/erro                   |

### Multithreading

- Utiliza um `ExecutorService` com `availableProcessors()` threads
- O intervalo é dividido em blocos iguais por thread
- `AtomicBoolean encontrado` interrompe todas as threads assim que a senha é descoberta
- O progresso é reportado em lotes de 500 iterações para reduzir a contenção nos atómicos

---

## Requisitos

- **Java 11+** (ou superior)
- Sem dependências externas — apenas a biblioteca padrão Java (`javax.crypto`, `java.security`)

---

### Exemplo de output

```
  ┌─────────────────────────────────────────┐
  │           DECIFRADOR AES  –  v3.0       │
  │      Força Bruta com Multithreading      │
  └─────────────────────────────────────────┘

  Ficheiros disponíveis:

    [1] Encrypted_G15.pdf.enc

  » Número do ficheiro: 1

  Processadores disponíveis : 8
  Intervalo a testar        : 1 000 000 – 9 999 999  (9 000 000 combinações)

  [████████████████████████░░░░░░░░░░░░░░░░]  61.2%  (5 506 312 / 9 000 000)

  ╔══════════════════════════════════════╗
  ║           SENHA ENCONTRADA!          ║
  ║   Senha : 6753090                    ║
  ║   Tempo : 12.45 s                    ║
  ╚══════════════════════════════════════╝

  Ficheiro guardado em: .../resultados/Encrypted_G15.pdf
```

---

## Detalhes Criptográficos

| Parâmetro           | Valor                                               |
|---------------------|-----------------------------------------------------|
| Algoritmo           | AES-128                                             |
| Modo                | ECB (Electronic Codebook)                           |
| Padding             | PKCS5Padding                                        |
| Derivação de chave  | SHA-1(senha) → primeiros 16 caracteres hex          |
| Espaço de senhas    | Inteiros de 7 dígitos: `[1 000 000, 9 999 999]`     |
| Total de candidatos | 9 000 000                                           |

---

## Notas

- O ficheiro `resultados/Encrypted_G15.pdf` já se encontra incluído no repositório como prova da execução bem-sucedida.
- O desempenho depende do número de núcleos disponíveis na máquina — quanto mais núcleos, menor o tempo de execução.
- O modo ECB é determinístico, o que permite a verificação por magic number `%PDF` sem necessidade de IV ou autenticação adicional.

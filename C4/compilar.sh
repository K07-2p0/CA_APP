#!/bin/bash
# ============================================================
#  compilar.sh - Compila o projeto C4
#  Criptografia Aplicada | LSIRC 2025/2026
# ============================================================

JARS="lib/*"

echo "A compilar..."
javac -cp "$JARS" AssinarPDF.java GestorCertificados.java GeradorAssinatura.java

if [ $? -eq 0 ]; then
    echo "Compilação concluída com sucesso."
else
    echo "Erro na compilação."
    exit 1
fi

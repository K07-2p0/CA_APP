@echo off
:: ============================================================
::  compilar.bat - Compila o projeto C4 (Windows)
::  Criptografia Aplicada | LSIRC 2025/2026
:: ============================================================

echo A compilar...
javac -cp "lib/*" AssinarPDF.java GestorCertificados.java GeradorAssinatura.java

if %errorlevel% == 0 (
    echo Compilacao concluida com sucesso.
) else (
    echo Erro na compilacao.
)

package com.arquitectura.servicios;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para sanitizar campos Base64 en logs para evitar impresiones excesivas.
 */
final class SanitizadorBase64Logs {
    private static final int LIMITE_PREVISUALIZACION = 10;
    private static final Pattern PATRON_AUDIO = Pattern.compile("(\\\"audioBase64\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATRON_FOTO = Pattern.compile("(\\\"foto(?:Base64)?\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")", Pattern.CASE_INSENSITIVE);

    private SanitizadorBase64Logs() {}

    static String truncarCamposBase64(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        String resultado = aplicarPatron(texto, PATRON_AUDIO);
        resultado = aplicarPatron(resultado, PATRON_FOTO);
        return resultado;
    }

    private static String aplicarPatron(String texto, Pattern patron) {
        Matcher matcher = patron.matcher(texto);
        boolean encontrado = false;
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            encontrado = true;
            String valor = matcher.group(2);
            String reducido = truncarValor(valor);
            String reemplazo = matcher.group(1) + reducido + matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(reemplazo));
        }
        if (!encontrado) return texto;
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String truncarValor(String valor) {
        if (valor == null) return null;
        if (valor.length() <= LIMITE_PREVISUALIZACION) {
            return valor;
        }
        return valor.substring(0, LIMITE_PREVISUALIZACION) + "...";
    }
}

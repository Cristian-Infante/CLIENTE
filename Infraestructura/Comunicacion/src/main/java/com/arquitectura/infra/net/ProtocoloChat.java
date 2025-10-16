package com.arquitectura.infra.net;

import java.util.Map;
import java.util.HashMap;

/**
 * Utilidades mínimas para construir mensajes JSON por línea
 * según el protocolo: {"command":"...","payload":{...}}
 * Pertenece a Infraestructura porque acopla formato (JSON) y transporte.
 */
public final class ProtocoloChat {
    private ProtocoloChat() {}

    public static String construir(String comando, Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"command\":\"").append(esc(comando)).append("\"");
        sb.append(',');
        sb.append("\"payload\":");
        if (payload == null) {
            sb.append("null");
        } else {
            sb.append(obj(payload));
        }
        sb.append('}');
        return sb.toString();
    }

    public static Map<String, Object> mapa() { return new HashMap<>(); }

    private static String obj(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(esc(e.getKey())).append('"').append(':');
            sb.append(val(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String arr(Iterable<?> it) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object v : it) {
            if (!first) sb.append(',');
            first = false;
            sb.append(val(v));
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String val(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return '"' + esc(s) + '"';
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map<?, ?> m) return obj((Map<String, Object>) m);
        if (v instanceof Iterable<?> it) return arr(it);
        return '"' + esc(String.valueOf(v)) + '"';
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}


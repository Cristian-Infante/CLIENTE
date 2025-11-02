package com.arquitectura.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

public class ConfigClienteDB {
    private String url;
    private final String usuario;
    private final String clave;
    private final boolean inicializar;
    // Keep original template to allow switching to per-user DB
    private String originalUrlTemplate;

    private ConfigClienteDB(String url, String originalUrlTemplate, String usuario, String clave, boolean inicializar) {
        this.url = Objects.requireNonNull(url, "URL de base de datos requerida");
        this.usuario = usuario == null ? "sa" : usuario;
        this.clave = clave == null ? "" : clave;
        this.inicializar = inicializar;
        this.originalUrlTemplate = originalUrlTemplate != null ? originalUrlTemplate : url;
    }

    public static ConfigClienteDB cargarDesdeRecursos() {
        Properties p = new Properties();
        try (InputStream in = ConfigClienteDB.class.getClassLoader().getResourceAsStream("cliente-db.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {}
        // By default, defer using a file-based DB until the user logs in. This avoids multiple JVM
        // processes contending over the same file (locking). When deferred, we use an in-memory
        // DB for the initial session and later migrate to a per-user file DB after login.
        boolean deferToUser = Boolean.parseBoolean(p.getProperty("db.deferToUser", "true"));
        // Keep the file-template (used to create per-user file DBs) even when deferToUser is true
        String fileTemplate = p.getProperty("db.url", "jdbc:h2:file:./infraestructura-data/cliente-db");
        String url;
        if (deferToUser) {
            // In-memory DB name is intentionally generic; it's per-JVM so different client
            // processes won't conflict.
            url = p.getProperty("db.url.mem", "jdbc:h2:mem:cliente_temp;DB_CLOSE_DELAY=-1");
        } else {
            url = fileTemplate;
        }
        url = resolverUrlProyecto(url);
        String usuario = p.getProperty("db.usuario", "sa");
        String clave = p.getProperty("db.clave", "");
        boolean inicializar = Boolean.parseBoolean(p.getProperty("db.inicializar", "true"));
        // Debug: print selected URL choice for diagnostics
        try { System.out.println("[ConfigClienteDB] deferToUser=" + deferToUser + " urlChosen=" + url); } catch (Exception ignored) {}

        // Cargar el driver H2 explícitamente para diagnosticar entornos donde ServiceLoader no lo carga
        try { Class.forName("org.h2.Driver"); } catch (ClassNotFoundException e) {
            System.out.println("[ConfigClienteDB] Driver H2 no encontrado en classpath: " + e.getMessage());
        }

    crearDirectorioSiEsArchivo(url);
    return new ConfigClienteDB(url, fileTemplate, usuario, clave, inicializar);
    }

    /**
     * If URL is relative (jdbc:h2:file:./...), resolve it inside the ConfigClienteDB module directory
     * (next to this module, not to the process working directory).
     */
    private static String resolverUrlProyecto(String url) {
        try {
            final String pref = "jdbc:h2:file:";
            if (url != null && url.startsWith(pref + "./")) {
                File codeLoc = new File(ConfigClienteDB.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File base = codeLoc;
                if (base.isFile()) base = base.getParentFile(); // if running from JAR
                // If in target/classes, go up to target then to module dir
                if ("classes".equalsIgnoreCase(base.getName())) base = base.getParentFile();
                if (base != null && "target".equalsIgnoreCase(base.getName())) base = base.getParentFile();
                if (base == null) base = new File("").getAbsoluteFile();
                // Resolve relative path at project root (parent of module group folder)
                File projectRoot = base.getParentFile(); // e.g., .../Datos
                if (projectRoot != null && projectRoot.getParentFile() != null) {
                    projectRoot = projectRoot.getParentFile(); // repo root
                } else if (projectRoot == null) {
                    projectRoot = base;
                }

                String relative = url.substring(pref.length()); // starts with ./
                String extras = "";
                int idxExtras = relative.indexOf(';');
                if (idxExtras >= 0) {
                    extras = relative.substring(idxExtras);
                    relative = relative.substring(0, idxExtras);
                }
                while (relative.startsWith("./")) relative = relative.substring(2);
                File dataPath = new File(projectRoot, relative).getAbsoluteFile();
                // Ensure parent dir exists
                File dir = dataPath.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                // Use forward slashes for H2 compatibility on Windows
                return pref + dataPath.getPath().replace('\\', '/') + extras;
            }
        } catch (URISyntaxException ignored) {
        }
        return url;
    }

    private static void crearDirectorioSiEsArchivo(String url) {
        String prefijo = "jdbc:h2:file:";
        int idx = url.indexOf(prefijo);
        if (idx >= 0) {
            String resto = url.substring(idx + prefijo.length());
            int corte = resto.indexOf(';');
            String ruta = corte >= 0 ? resto.substring(0, corte) : resto;
            File f = new File(ruta).getAbsoluteFile();
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
        }
    }

    public Connection obtenerConexion() throws SQLException {
        // Allow runtime override (set by cambiarUsuarioNombre fallbacks) via system property
        String override = System.getProperty("arquitectura.config.client.db.overrideUrl");
        String useUrl = override != null && !override.isBlank() ? override : url;
        return DriverManager.getConnection(useUrl, usuario, clave);
    }

    public void inicializarEsquemaSiEsNecesario() throws SQLException {
        if (!inicializar) return;
        try (Connection c = obtenerConexion(); Statement st = c.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS mensajes (" +
                " id IDENTITY PRIMARY KEY," +
                " fecha_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                " tipo VARCHAR(32) NOT NULL," +
                " emisor_id BIGINT NOT NULL," +
                " emisor_nombre VARCHAR(255)," +
                " receptor_id BIGINT," +
                " receptor_nombre VARCHAR(255)," +
                " canal_id BIGINT," +
                " es_audio BOOLEAN NOT NULL," +
                " texto CLOB," +
                " ruta_audio VARCHAR(1024)," +
                " audio_base64 CLOB," +
                " audio_mime VARCHAR(128)," +
                " audio_duracion_seg INT," +
                " contexto_usuario_id BIGINT" +
                ")"
            );
            // Evolución de esquema: columnas para deduplicación por id/timestamp del servidor
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS emisor_nombre VARCHAR(255)"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS receptor_nombre VARCHAR(255)"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS server_id BIGINT"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS server_ts TIMESTAMP"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS audio_base64 CLOB"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS audio_mime VARCHAR(128)"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS audio_duracion_seg INT"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS contexto_usuario_id BIGINT"); } catch (SQLException ignored) {}
            try { st.executeUpdate("DROP INDEX IF EXISTS ux_mensajes_server_id"); } catch (SQLException ignored) {}
            try { st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mensajes_contexto ON mensajes(contexto_usuario_id)"); } catch (SQLException ignored) {}
            try { st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_mensajes_contexto_server ON mensajes(contexto_usuario_id, server_id)"); } catch (SQLException ignored) {}
        }
    }

    public String obtenerUrl() { return url; }
    public String obtenerUsuario() { return usuario; }
    public String obtenerClave() { return clave; }
    public boolean debeInicializar() { return inicializar; }

    /**
     * Cambia la URL interna para apuntar a una base de datos por usuario.
     * Ej: si la URL original apunta a '.../cliente-db', se reemplaza el nombre por '<nombreUsuario>db'.
     */
    public void cambiarUsuarioNombre(String nombreUsuario) {
        if (nombreUsuario == null || nombreUsuario.isBlank()) return;
        try {
            // If we have an explicit template with {user} we replace it, otherwise replace last path segment with nombreUsuariodb
            String template = originalUrlTemplate != null ? originalUrlTemplate : url;
            String pref = "jdbc:h2:file:";
            int idxPref = template.indexOf(pref);
            if (idxPref < 0) return;
            String resto = template.substring(idxPref + pref.length());
            String extras = "";
            int idxSemi = resto.indexOf(';');
            if (idxSemi >= 0) { extras = resto.substring(idxSemi); resto = resto.substring(0, idxSemi); }
            // Per-session DB name. Always prefer a session-scoped file to avoid file locking
            // across multiple client instances. The same session will reuse the same token.
            String safeUser = (nombreUsuario != null && !nombreUsuario.isBlank()) ? nombreUsuario.replaceAll("[^A-Za-z0-9_-]", "_") : "cliente";

            String path = resto;
            int last = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String parent = last >= 0 ? path.substring(0, last + 1) : "";

            // Build or reuse per-session token
            String sessionToken = System.getProperty("arquitectura.config.client.db.sessionToken");
            if (sessionToken == null || sessionToken.isBlank()) {
                try {
                    long pid = -1;
                    try { pid = ProcessHandle.current().pid(); } catch (Throwable ignored) {}
                    String base = Long.toString(Math.abs(System.currentTimeMillis()));
                    sessionToken = (pid > 0 ? ("p" + pid + "_") : "p_") + base.substring(Math.max(0, base.length() - 6));
                } catch (Exception ignored) {
                    sessionToken = Long.toHexString(System.nanoTime());
                }
            }
            String safeToken = sessionToken.replaceAll("[^A-Za-z0-9_-]", "_");

            String chosenResto;
            if (resto.contains("{user}") || resto.contains("{session}")) {
                chosenResto = resto.replace("{user}", safeUser).replace("{session}", safeToken);
                if (!chosenResto.endsWith("db")) chosenResto = chosenResto + "db";
            } else {
                String perSessionName = safeUser + "_" + safeToken + "db";
                chosenResto = parent + perSessionName;
            }

            String nueva = pref + chosenResto + extras;
            // create parent dir
            crearDirectorioSiEsArchivo(nueva);
            // update instance field and also set system property as a fallback
            this.originalUrlTemplate = template;
            try {
                this.url = nueva;
            } catch (Exception ignored) {}
            try { System.out.println("[ConfigClienteDB] cambiarUsuarioNombre set url=" + nueva); } catch (Exception ignored) {}
            // ensure other code paths / other instances can also pick up the URL if reflection isn't possible
            System.setProperty("arquitectura.config.client.db.overrideUrl", nueva);
        } catch (Exception ignored) {}
    }

    private static boolean existeArchivoH2(String pathSinPrefijoNiExtras) {
        try {
            // pathSinPrefijoNiExtras es la ruta de archivo H2 sin extensión (.mv.db/.h2.db)
            // Resolver relativa contra CWD
            File base = new File(pathSinPrefijoNiExtras).getAbsoluteFile();
            File mv = new File(base.getPath() + ".mv.db");
            File h2 = new File(base.getPath() + ".h2.db");
            return mv.exists() || h2.exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Migrates messages that belong to usuarioId from another DB url into the currently configured DB URL.
     * It copies rows with contexto_usuario_id = usuarioId and avoids duplicates by server_id.
     */
    public void migrarMensajesDesdeUrl(String otherUrl, Long usuarioId) {
        if (otherUrl == null || usuarioId == null) return;
        String targetUrl = System.getProperty("arquitectura.config.client.db.overrideUrl", url);
        if (otherUrl.equals(targetUrl)) return;
        try (java.sql.Connection src = java.sql.DriverManager.getConnection(otherUrl, usuario, clave);
             java.sql.Connection dst = java.sql.DriverManager.getConnection(targetUrl, usuario, clave)) {
            // Select all messages for this contexto in source
            try (java.sql.PreparedStatement ps = src.prepareStatement("SELECT id, fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg, server_id, server_ts, contexto_usuario_id FROM mensajes WHERE contexto_usuario_id = ?")) {
                ps.setLong(1, usuarioId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    String checkSql = "SELECT 1 FROM mensajes WHERE contexto_usuario_id = ? AND server_id = ? LIMIT 1";
                    String insertSql = "INSERT INTO mensajes (id, fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg, server_id, server_ts, contexto_usuario_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement check = dst.prepareStatement(checkSql);
                         java.sql.PreparedStatement ins = dst.prepareStatement(insertSql)) {
                        while (rs.next()) {
                            Long serverId = rs.getObject("server_id") != null ? rs.getLong("server_id") : null;
                            if (serverId != null) {
                                check.setLong(1, usuarioId);
                                check.setLong(2, serverId);
                                try (java.sql.ResultSet cr = check.executeQuery()) {
                                    if (cr.next()) continue; // already exists
                                }
                            }
                            // copy fields
                            ins.setLong(1, rs.getLong("id"));
                            ins.setTimestamp(2, rs.getTimestamp("fecha_envio"));
                            ins.setString(3, rs.getString("tipo"));
                            if (rs.getObject("emisor_id") != null) ins.setLong(4, rs.getLong("emisor_id")); else ins.setNull(4, java.sql.Types.BIGINT);
                            ins.setString(5, rs.getString("emisor_nombre"));
                            if (rs.getObject("receptor_id") != null) ins.setLong(6, rs.getLong("receptor_id")); else ins.setNull(6, java.sql.Types.BIGINT);
                            ins.setString(7, rs.getString("receptor_nombre"));
                            if (rs.getObject("canal_id") != null) ins.setLong(8, rs.getLong("canal_id")); else ins.setNull(8, java.sql.Types.BIGINT);
                            ins.setBoolean(9, rs.getBoolean("es_audio"));
                            ins.setString(10, rs.getString("texto"));
                            ins.setString(11, rs.getString("ruta_audio"));
                            ins.setString(12, rs.getString("audio_base64"));
                            ins.setString(13, rs.getString("audio_mime"));
                            if (rs.getObject("audio_duracion_seg") != null) ins.setInt(14, rs.getInt("audio_duracion_seg")); else ins.setNull(14, java.sql.Types.INTEGER);
                            if (rs.getObject("server_id") != null) ins.setLong(15, rs.getLong("server_id")); else ins.setNull(15, java.sql.Types.BIGINT);
                            ins.setTimestamp(16, rs.getTimestamp("server_ts"));
                            ins.setLong(17, usuarioId);
                            try { ins.executeUpdate(); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void reasignarMensajesSinContexto(Long nuevoContextoId) {
        if (nuevoContextoId == null) return;
        // Debug: show caller stack to understand who invoked the reassignment
        try {
            System.out.println("[ConfigClienteDB] reasignarMensajesSinContexto invoked. Caller:");
            for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                System.out.println("  at " + el.toString());
            }
        } catch (Exception ignored) {}
        // Implement safer reassignment in Java to avoid unique-key conflicts:
        // 1) Update rows with server_id IS NULL in bulk (these don't conflict on (contexto, server_id)).
        // 2) For server_id NOT NULL, iterate distinct server_id values that have mensajes with NULL/0 contexto
        //    and for each server_id update at most one representative row only if there isn't already a row
        //    with that server_id and the target contexto (to avoid unique constraint violation).
        try (Connection c = obtenerConexion()) {
            c.setAutoCommit(false);
            try (
                java.sql.PreparedStatement updNullServer = c.prepareStatement("UPDATE mensajes SET contexto_usuario_id = ? WHERE (contexto_usuario_id IS NULL OR contexto_usuario_id = 0) AND server_id IS NULL");
                java.sql.PreparedStatement distinctServers = c.prepareStatement("SELECT DISTINCT server_id FROM mensajes WHERE (contexto_usuario_id IS NULL OR contexto_usuario_id = 0) AND server_id IS NOT NULL");
                java.sql.PreparedStatement existsForTarget = c.prepareStatement("SELECT 1 FROM mensajes WHERE contexto_usuario_id = ? AND server_id = ? LIMIT 1");
                java.sql.PreparedStatement selectRep = c.prepareStatement("SELECT MIN(id) AS id FROM mensajes WHERE server_id = ? AND (contexto_usuario_id IS NULL OR contexto_usuario_id = 0)");
                java.sql.PreparedStatement updById = c.prepareStatement("UPDATE mensajes SET contexto_usuario_id = ? WHERE id = ?")
            ) {
                // Paso 1
                updNullServer.setLong(1, nuevoContextoId);
                int u1 = updNullServer.executeUpdate();

                // Paso 2: por cada server_id candidate
                int updated = u1;
                try (java.sql.ResultSet rs = distinctServers.executeQuery()) {
                    while (rs.next()) {
                        Long serverId = rs.getLong(1);
                        // Check if a row with this server_id already has the target contexto
                        existsForTarget.setLong(1, nuevoContextoId);
                        existsForTarget.setLong(2, serverId);
                        try (java.sql.ResultSet ex = existsForTarget.executeQuery()) {
                            if (ex.next()) continue; // someone already has contexto=nuevoContextoId for this server_id
                        }
                        // get representative id (lowest id)
                        selectRep.setLong(1, serverId);
                        try (java.sql.ResultSet rep = selectRep.executeQuery()) {
                            if (rep.next()) {
                                long idToUpdate = rep.getLong("id");
                                updById.setLong(1, nuevoContextoId);
                                updById.setLong(2, idToUpdate);
                                try {
                                    int u = updById.executeUpdate();
                                    updated += u;
                                } catch (SQLException ex) {
                                    // If a race occurs and unique constraint happens, skip this server_id
                                    System.out.println("[ConfigClienteDB] skipped server_id=" + serverId + " due to concurrent constraint: " + ex.getMessage());
                                }
                            }
                        }
                    }
                }

                c.commit();
                if (updated > 0) System.out.println("[ConfigClienteDB] Mensajes reasignados al contexto " + nuevoContextoId + ": " + updated);
            } catch (SQLException ex) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw ex;
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            try {
                System.out.println("[ConfigClienteDB] No se pudieron reasignar mensajes sin contexto: " + e.getMessage());
                System.out.println("[ConfigClienteDB] URL usada para reasignacion: " + System.getProperty("arquitectura.config.client.db.overrideUrl", url));
                e.printStackTrace(System.out);
            } catch (Exception ignored) {}
        }
    }
}

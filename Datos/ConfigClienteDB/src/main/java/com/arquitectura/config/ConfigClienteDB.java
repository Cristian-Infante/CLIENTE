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
    private final String url;
    private final String usuario;
    private final String clave;
    private final boolean inicializar;

    private ConfigClienteDB(String url, String usuario, String clave, boolean inicializar) {
        this.url = Objects.requireNonNull(url, "URL de base de datos requerida");
        this.usuario = usuario == null ? "sa" : usuario;
        this.clave = clave == null ? "" : clave;
        this.inicializar = inicializar;
    }

    public static ConfigClienteDB cargarDesdeRecursos() {
        Properties p = new Properties();
        try (InputStream in = ConfigClienteDB.class.getClassLoader().getResourceAsStream("cliente-db.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {}

        String url = p.getProperty("db.url", "jdbc:h2:file:./infraestructura-data/cliente-db");
        url = resolverUrlProyecto(url);
        String usuario = p.getProperty("db.usuario", "sa");
        String clave = p.getProperty("db.clave", "");
        boolean inicializar = Boolean.parseBoolean(p.getProperty("db.inicializar", "true"));

        // Cargar el driver H2 explícitamente para diagnosticar entornos donde ServiceLoader no lo carga
        try { Class.forName("org.h2.Driver"); } catch (ClassNotFoundException e) {
            System.out.println("[ConfigClienteDB] Driver H2 no encontrado en classpath: " + e.getMessage());
        }

        crearDirectorioSiEsArchivo(url);
        return new ConfigClienteDB(url, usuario, clave, inicializar);
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
                while (relative.startsWith("./")) relative = relative.substring(2);
                File dataPath = new File(projectRoot, relative).getAbsoluteFile();
                // Ensure parent dir exists
                File dir = dataPath.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                // Use forward slashes for H2 compatibility on Windows
                return pref + dataPath.getPath().replace('\\', '/');
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
        return DriverManager.getConnection(url, usuario, clave);
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
                " ruta_audio VARCHAR(1024)" +
                ")"
            );
            // Evolución de esquema: columnas para deduplicación por id/timestamp del servidor
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS emisor_nombre VARCHAR(255)"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS receptor_nombre VARCHAR(255)"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS server_id BIGINT"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS server_ts TIMESTAMP"); } catch (SQLException ignored) {}
            try { st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_mensajes_server_id ON mensajes(server_id)"); } catch (SQLException ignored) {}
        }
    }

    public String obtenerUrl() { return url; }
    public String obtenerUsuario() { return usuario; }
    public String obtenerClave() { return clave; }
    public boolean debeInicializar() { return inicializar; }
}

package odoonto.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Configuration
public class ServiceInitializer {
    private static final Logger LOG = Logger.getLogger(ServiceInitializer.class.getName());
    private static final String TARGET_PATH = "src/main/resources/config-data.json";
    private static final String ENV_FILE_PATH = ".env";

    @PostConstruct
    public void initSystem() {
        try {
            prepareConfigDataFile();
            LOG.info("Sistema iniciado correctamente");
        } catch (IOException e) {
            LOG.severe("Error al iniciar sistema: " + e.getMessage());
            throw new RuntimeException("No se pudo inicializar el sistema", e);
        }
    }

    private void prepareConfigDataFile() throws IOException {
        File targetFile = new File(TARGET_PATH);

        // Asegurar que el directorio existe
        targetFile.getParentFile().mkdirs();

        // Leer variables desde .env
        Map<String, String> envVars = loadEnvFile();

        // Validar que las variables requeridas estén presentes
        validateEnvVars(envVars);

        // Usar Jackson ObjectMapper para escribir el JSON correctamente
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNode = objectMapper.createObjectNode();

        jsonNode.put("type", envVars.getOrDefault("FIREBASE_TYPE", "service_account"));
        jsonNode.put("project_id", envVars.get("FIREBASE_PROJECT_ID"));
        jsonNode.put("private_key_id", envVars.get("FIREBASE_PRIVATE_KEY_ID"));

        // Procesar la clave privada: convertir \n literales a saltos de línea reales
        String privateKey = envVars.get("FIREBASE_PRIVATE_KEY");
        if (privateKey != null) {
            privateKey = privateKey.replace("\\n", "\n");
        }
        jsonNode.put("private_key", privateKey);

        jsonNode.put("client_email", envVars.get("FIREBASE_CLIENT_EMAIL"));
        jsonNode.put("client_id", envVars.get("FIREBASE_CLIENT_ID"));
        jsonNode.put("auth_uri", envVars.getOrDefault("FIREBASE_AUTH_URI",
                "https://accounts.google.com/o/oauth2/auth"));
        jsonNode.put("token_uri", envVars.getOrDefault("FIREBASE_TOKEN_URI",
                "https://oauth2.googleapis.com/token"));
        jsonNode.put("auth_provider_x509_cert_url",
                envVars.getOrDefault("FIREBASE_AUTH_PROVIDER_X509_CERT_URL",
                        "https://www.googleapis.com/oauth2/v1/certs"));
        jsonNode.put("client_x509_cert_url", envVars.get("FIREBASE_CLIENT_X509_CERT_URL"));
        jsonNode.put("universe_domain", envVars.getOrDefault("FIREBASE_UNIVERSE_DOMAIN",
                "googleapis.com"));

        // Escribir el JSON con formato bonito y codificación UTF-8
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(fos, jsonNode);
        }

        LOG.info("Archivo generado en: " + targetFile.getAbsolutePath());
    }

    /**
     * Lee el archivo .env y retorna un Map con las variables de entorno
     */
    private Map<String, String> loadEnvFile() throws IOException {
        Map<String, String> envVars = new HashMap<>();

        // Primero intentar leer desde variables de entorno del sistema
        String projectId = System.getenv("FIREBASE_PROJECT_ID");
        if (projectId != null) {
            LOG.info("Usando variables de entorno del sistema");
            envVars.put("FIREBASE_TYPE", System.getenv("FIREBASE_TYPE"));
            envVars.put("FIREBASE_PROJECT_ID", projectId);
            envVars.put("FIREBASE_PRIVATE_KEY_ID", System.getenv("FIREBASE_PRIVATE_KEY_ID"));
            envVars.put("FIREBASE_PRIVATE_KEY", System.getenv("FIREBASE_PRIVATE_KEY"));
            envVars.put("FIREBASE_CLIENT_EMAIL", System.getenv("FIREBASE_CLIENT_EMAIL"));
            envVars.put("FIREBASE_CLIENT_ID", System.getenv("FIREBASE_CLIENT_ID"));
            envVars.put("FIREBASE_AUTH_URI", System.getenv("FIREBASE_AUTH_URI"));
            envVars.put("FIREBASE_TOKEN_URI", System.getenv("FIREBASE_TOKEN_URI"));
            envVars.put("FIREBASE_AUTH_PROVIDER_X509_CERT_URL",
                    System.getenv("FIREBASE_AUTH_PROVIDER_X509_CERT_URL"));
            envVars.put("FIREBASE_CLIENT_X509_CERT_URL",
                    System.getenv("FIREBASE_CLIENT_X509_CERT_URL"));
            envVars.put("FIREBASE_UNIVERSE_DOMAIN", System.getenv("FIREBASE_UNIVERSE_DOMAIN"));
            return envVars;
        }

        // Si no hay variables de entorno, leer desde archivo .env
        File envFile = new File(ENV_FILE_PATH);
        if (!envFile.exists()) {
            throw new IOException("No se encontró el archivo .env en la raíz del proyecto. " +
                    "Por favor, crea un archivo .env con tus credenciales de Firebase.");
        }

        LOG.info("Leyendo variables desde archivo .env: " + envFile.getAbsolutePath());

        // Leer archivo .env línea por línea para manejar valores multilínea y comentarios
        String content;
        try (FileInputStream fis = new FileInputStream(envFile)) {
            content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Normalizar saltos de línea (manejar Windows \r\n y Unix \n)
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = content.split("\n");

        StringBuilder currentValue = new StringBuilder();
        String currentKey = null;
        boolean inQuotedValue = false;
        char quoteChar = 0;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Saltar líneas vacías y comentarios (solo si no estamos dentro de un valor con comillas)
            if (!inQuotedValue && (trimmedLine.isEmpty() || trimmedLine.startsWith("#"))) {
                continue;
            }

            // Si la línea contiene un =, es una nueva clave-valor
            if (!inQuotedValue && trimmedLine.contains("=")) {
                // Guardar el valor anterior si existe
                if (currentKey != null) {
                    String finalValue = currentValue.toString().trim();
                    envVars.put(currentKey, finalValue);
                }

                int equalsIndex = trimmedLine.indexOf("=");
                currentKey = trimmedLine.substring(0, equalsIndex).trim();
                String value = trimmedLine.substring(equalsIndex + 1);

                // Detectar si el valor comienza con comillas
                value = value.trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                    // Valor completo en una línea entre comillas
                    value = value.substring(1, value.length() - 1);
                    currentValue = new StringBuilder(value);
                    inQuotedValue = false;
                } else if (value.startsWith("\"") || value.startsWith("'")) {
                    // Valor que comienza con comillas pero continúa en múltiples líneas
                    quoteChar = value.charAt(0);
                    value = value.substring(1);
                    currentValue = new StringBuilder(value);
                    inQuotedValue = true;
                } else {
                    // Valor sin comillas
                    currentValue = new StringBuilder(value);
                    inQuotedValue = false;
                }
            } else if (currentKey != null) {
                // Continuación de un valor multilínea
                if (inQuotedValue) {
                    // Verificar si la línea termina con la comilla de cierre
                    if (trimmedLine.endsWith(String.valueOf(quoteChar))) {
                        // Quitar la comilla final y agregar el contenido
                        String lineContent = trimmedLine.substring(0, trimmedLine.length() - 1);
                        currentValue.append("\n").append(lineContent);
                        inQuotedValue = false;
                    } else {
                        // Continuar el valor multilínea
                        currentValue.append("\n").append(line);
                    }
                } else {
                    // Continuar valor multilínea sin comillas
                    currentValue.append("\n").append(line);
                }
            }
        }

        // Guardar el último valor
        if (currentKey != null) {
            String finalValue = currentValue.toString().trim();
            envVars.put(currentKey, finalValue);
        }

        return envVars;
    }

    /**
     * Valida que las variables requeridas estén presentes
     */
    private void validateEnvVars(Map<String, String> envVars) throws IOException {
        String[] requiredVars = {
                "FIREBASE_PROJECT_ID",
                "FIREBASE_PRIVATE_KEY_ID",
                "FIREBASE_PRIVATE_KEY",
                "FIREBASE_CLIENT_EMAIL",
                "FIREBASE_CLIENT_ID",
                "FIREBASE_CLIENT_X509_CERT_URL"
        };

        StringBuilder missing = new StringBuilder();
        for (String var : requiredVars) {
            if (envVars.get(var) == null || envVars.get(var).trim().isEmpty()) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(var);
            }
        }

        if (missing.length() > 0) {
            throw new IOException("Faltan las siguientes variables de entorno requeridas: " +
                    missing.toString() + ". Por favor, configura el archivo .env correctamente.");
        }
    }
}

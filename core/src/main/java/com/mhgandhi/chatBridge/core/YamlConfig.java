package com.mhgandhi.chatBridge.core;

import com.mhgandhi.chatBridge.core.domain.Config;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class YamlConfig implements Config {//thx chat
    private final Path file;
    private final Yaml reader;
    private final Yaml writer;

    private Map<String, Object> values;

    /**
     * Loads an existing file, or creates an empty configuration.
     */
    public YamlConfig(Path file) throws IOException {
        this(file, null);
    }

    /**
     * Loads an existing file, or copies defaults when it does not exist.
     *
     * A non-null defaults stream is always closed by this constructor.
     */
    public YamlConfig(
            Path file,
            InputStream defaults
    ) throws IOException {
        this.file = Objects.requireNonNull(file, "file")
                .toAbsolutePath()
                .normalize();

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);

        reader = new Yaml(new SafeConstructor(loaderOptions));

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        dumperOptions.setPrettyFlow(true);

        writer = new Yaml(dumperOptions);

        // This absolute file path has a parent.
        try (InputStream source = defaults) {
            Files.createDirectories(this.file.getParent());

            if (!Files.exists(this.file)) {
                if (source != null) {
                    Files.copy(source, this.file);
                } else {
                    Files.createFile(this.file);
                }
            }
        }

        reload();
    }

    @Override
    public synchronized String getString(
            String key,
            String fallback
    ) {
        Object value = find(key);

        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value.toString();
        }

        return fallback;
    }

    @Override
    public synchronized boolean getBoolean(
            String key,
            boolean fallback
    ) {
        Object value = find(key);

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value instanceof String stringValue) {
            String text = stringValue.trim();

            if ("true".equalsIgnoreCase(text)) {
                return true;
            }

            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }

        return fallback;
    }

    public synchronized void setString(String key, String value) {
        set(key, value);
    }

    public synchronized void setBoolean(String key, boolean value) {
        set(key, value);
    }

    /**
     * Removes a value. Empty parent sections are retained.
     */
    public synchronized void remove(String key) {
        set(key, null);
    }

    /**
     * Reloads from disk. Existing in-memory values are retained if loading fails.
     */
    public synchronized void reload() throws IOException {
        try (Reader input = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8
        )) {
            Object loaded = reader.load(input);

            if (loaded == null) {
                values = new LinkedHashMap<>();
            } else if (loaded instanceof Map<?, ?> map) {
                values = copyMapping(map);
            } else {
                throw new IOException(
                        "Configuration root must be a YAML mapping: " + file
                );
            }
        } catch (YAMLException | IllegalArgumentException error) {
            throw new IOException(
                    "Invalid YAML configuration: " + file,
                    error
            );
        }
    }

    /**
     * Writes to a temporary file before replacing the configuration.
     */
    public synchronized void save() throws IOException {
        Path temporary = Files.createTempFile(
                file.getParent(),
                "chatbridge-config-",
                ".tmp"
        );

        try {
            try (Writer output = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8
            )) {
                writer.dump(values, output);
            } catch (YAMLException error) {
                throw new IOException(
                        "Failed to serialize configuration: " + file,
                        error
                );
            }

            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Object find(String key) {
        String[] parts = splitKey(key);
        Object current = values;

        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current = map.get(part);
        }

        return current;
    }

    private void set(String key, Object value) {
        String[] parts = splitKey(key);
        Map<String, Object> section = values;

        for (int index = 0; index < parts.length - 1; index++) {
            String part = parts[index];
            Object existing = section.get(part);

            if (existing == null) {
                if (value == null) {
                    return;
                }

                Map<String, Object> child = new LinkedHashMap<>();
                section.put(part, child);
                section = child;
            } else if (existing instanceof Map<?, ?> map) {
                Map<String, Object> child = copyMapping(map);
                section.put(part, child);
                section = child;
            } else {
                throw new IllegalArgumentException(
                        "Cannot traverse a non-section value in key: " + key
                );
            }
        }

        String leaf = parts[parts.length - 1];

        if (value == null) {
            section.remove(leaf);
        } else {
            section.put(leaf, value);
        }
    }

    private static String[] splitKey(String key) {
        Objects.requireNonNull(key, "key");

        String[] parts = key.split("\\.", -1);

        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException(
                        "Configuration key contains an empty section: " + key
                );
            }
        }

        return parts;
    }

    private static Map<String, Object> copyMapping(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(
                        "Configuration mapping keys must be strings"
                );
            }

            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                value = copyMapping(nested);
            }

            result.put(key, value);
        }

        return result;
    }
}
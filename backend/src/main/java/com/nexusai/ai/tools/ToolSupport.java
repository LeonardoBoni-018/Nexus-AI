package com.nexusai.ai.tools;

import com.nexusai.identity.Role;
import com.nexusai.security.OrgContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Tipos e utilitarios para ferramentas. Tudo que vem do modelo e tratado como entrada nao confiavel. */
public final class ToolSupport {

    private ToolSupport() {}

    /** Contexto de execucao derivado da sessao autenticada, nunca de argumentos do modelo. */
    public record ToolContext(OrgContext org, UUID conversationId) {}

    @FunctionalInterface
    public interface ToolHandler {
        Object handle(ToolContext ctx, Args args);
    }

    public record Tool(String name, String description, Role minRole, String schemaJson, ToolHandler handler) {}

    /** Erro de validacao/negocio devolvido ao modelo como resultado de ferramenta (sem stack trace). */
    public static class ToolException extends RuntimeException {
        public ToolException(String message) {
            super(message);
        }
    }

    /** Acesso tipado e validado aos argumentos JSON. */
    public static final class Args {

        private final JsonNode node;

        public Args(JsonNode node) {
            this.node = node;
        }

        public UUID uuid(String name, boolean required) {
            String s = string(name, required, 64);
            if (s == null) {
                return null;
            }
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                throw new ToolException("Argumento '" + name + "' deve ser um UUID valido");
            }
        }

        public LocalDate date(String name, boolean required) {
            String s = string(name, required, 10);
            if (s == null) {
                return null;
            }
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                throw new ToolException("Argumento '" + name + "' deve estar no formato AAAA-MM-DD");
            }
        }

        public String string(String name, boolean required, int maxLen) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull() || (v.isString() && v.asString().isBlank())) {
                if (required) {
                    throw new ToolException("Argumento obrigatorio ausente: " + name);
                }
                return null;
            }
            if (!v.isString()) {
                throw new ToolException("Argumento '" + name + "' deve ser texto");
            }
            String s = v.asString().trim();
            if (s.length() > maxLen) {
                throw new ToolException("Argumento '" + name + "' excede " + maxLen + " caracteres");
            }
            return s;
        }

        public int integer(String name, int defaultValue, int min, int max) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull()) {
                return defaultValue;
            }
            if (!v.isIntegralNumber()) {
                throw new ToolException("Argumento '" + name + "' deve ser inteiro");
            }
            int i = v.asInt();
            if (i < min || i > max) {
                throw new ToolException("Argumento '" + name + "' deve estar entre " + min + " e " + max);
            }
            return i;
        }

        public Set<UUID> uuidList(String name, int maxItems) {
            JsonNode v = node.get(name);
            Set<UUID> out = new LinkedHashSet<>();
            if (v == null || v.isNull()) {
                return out;
            }
            if (!v.isArray() || v.size() > maxItems) {
                throw new ToolException("Argumento '" + name + "' deve ser lista de ate " + maxItems + " UUIDs");
            }
            for (JsonNode item : v) {
                try {
                    out.add(UUID.fromString(item.asString("")));
                } catch (IllegalArgumentException e) {
                    throw new ToolException("Argumento '" + name + "' contem UUID invalido");
                }
            }
            return out;
        }
    }
}

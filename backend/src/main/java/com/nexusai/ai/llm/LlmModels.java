package com.nexusai.ai.llm;

import java.util.List;

/** Modelo neutro de provedor: o dominio nao conhece formatos de Anthropic/OpenAI. */
public final class LlmModels {

    private LlmModels() {}

    public enum Role { USER, ASSISTANT, TOOL }

    /** argumentsJson e o JSON bruto emitido pelo modelo (nao confiavel ate ser validado pela ferramenta). */
    public record ToolCall(String id, String name, String argumentsJson) {}

    public record ToolResult(String toolCallId, String name, String content, boolean error) {}

    public record LlmMessage(Role role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        public static LlmMessage user(String text) {
            return new LlmMessage(Role.USER, text, List.of(), List.of());
        }

        public static LlmMessage assistant(String text, List<ToolCall> calls) {
            return new LlmMessage(Role.ASSISTANT, text, calls == null ? List.of() : calls, List.of());
        }

        public static LlmMessage toolResults(List<ToolResult> results) {
            return new LlmMessage(Role.TOOL, null, List.of(), results);
        }
    }

    /** schemaJson: JSON Schema (objeto) dos parametros. */
    public record ToolSpec(String name, String description, String schemaJson) {}

    public record LlmRequest(String system, List<LlmMessage> messages, List<ToolSpec> tools) {}

    public record LlmReply(String text, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}

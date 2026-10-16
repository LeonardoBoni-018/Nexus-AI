package com.nexusai.ai.llm;

import com.nexusai.ai.llm.LlmModels.*;
import com.nexusai.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Adaptador da Messages API da Anthropic (POST /v1/messages) com tool use. */
public class AnthropicClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://api.anthropic.com";

    private final AppProperties.Ai cfg;
    private final JsonMapper json;

    public AnthropicClient(AppProperties.Ai cfg, JsonMapper json) {
        this.cfg = cfg;
        this.json = json;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public LlmReply chat(LlmRequest req) {
        String base = cfg.baseUrl() == null || cfg.baseUrl().isBlank() ? DEFAULT_BASE : stripSlash(cfg.baseUrl());
        String raw = HttpLlmSupport.postJson(base + "/v1/messages",
                Map.of("x-api-key", cfg.apiKey(), "anthropic-version", "2023-06-01"),
                buildBody(req), cfg.requestTimeout(), "Anthropic");
        return parse(raw);
    }

    String buildBody(LlmRequest req) {
        ObjectNode body = json.createObjectNode();
        body.put("model", cfg.model());
        body.put("max_tokens", cfg.maxOutputTokens());
        body.put("system", req.system());
        ArrayNode msgs = body.putArray("messages");
        for (LlmMessage m : req.messages()) {
            ObjectNode node = msgs.addObject();
            switch (m.role()) {
                case USER -> {
                    node.put("role", "user");
                    node.put("content", m.text());
                }
                case ASSISTANT -> {
                    node.put("role", "assistant");
                    ArrayNode content = node.putArray("content");
                    if (m.text() != null && !m.text().isBlank()) {
                        content.addObject().put("type", "text").put("text", m.text());
                    }
                    for (ToolCall c : m.toolCalls()) {
                        ObjectNode tu = content.addObject();
                        tu.put("type", "tool_use").put("id", c.id()).put("name", c.name());
                        tu.set("input", parseObject(c.argumentsJson()));
                    }
                }
                case TOOL -> {
                    node.put("role", "user");
                    ArrayNode content = node.putArray("content");
                    for (ToolResult r : m.toolResults()) {
                        ObjectNode tr = content.addObject();
                        tr.put("type", "tool_result").put("tool_use_id", r.toolCallId()).put("content", r.content());
                        if (r.error()) {
                            tr.put("is_error", true);
                        }
                    }
                }
            }
        }
        if (!req.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec t : req.tools()) {
                ObjectNode tn = tools.addObject();
                tn.put("name", t.name()).put("description", t.description());
                tn.set("input_schema", json.readTree(t.schemaJson()));
            }
        }
        return json.writeValueAsString(body);
    }

    LlmReply parse(String raw) {
        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (RuntimeException e) {
            throw new LlmException("Resposta invalida do provedor", e, false);
        }
        JsonNode content = root.get("content");
        if (content == null || !content.isArray()) {
            throw new LlmException("Resposta do provedor sem conteudo");
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asString("");
            if ("text".equals(type)) {
                text.append(block.path("text").asString(""));
            } else if ("tool_use".equals(type)) {
                calls.add(new ToolCall(block.path("id").asString(""), block.path("name").asString(""),
                        json.writeValueAsString(block.path("input"))));
            }
        }
        return new LlmReply(text.toString(), calls);
    }

    private JsonNode parseObject(String s) {
        try {
            JsonNode n = json.readTree(s == null || s.isBlank() ? "{}" : s);
            return n.isObject() ? n : json.createObjectNode();
        } catch (RuntimeException e) {
            return json.createObjectNode();
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

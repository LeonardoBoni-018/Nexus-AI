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

/** Adaptador da API Chat Completions (OpenAI e servidores compativeis, via AI_BASE_URL). */
public class OpenAiCompatibleClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://api.openai.com/v1";

    private final AppProperties.Ai cfg;
    private final JsonMapper json;

    public OpenAiCompatibleClient(AppProperties.Ai cfg, JsonMapper json) {
        this.cfg = cfg;
        this.json = json;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public LlmReply chat(LlmRequest req) {
        boolean defaultBase = cfg.baseUrl() == null || cfg.baseUrl().isBlank();
        String base = defaultBase ? DEFAULT_BASE : stripSlash(cfg.baseUrl());
        String raw = HttpLlmSupport.postJson(base + "/chat/completions",
                Map.of("Authorization", "Bearer " + cfg.apiKey()), buildBody(req, defaultBase),
                cfg.requestTimeout(), "OpenAI");
        return parse(raw);
    }

    String buildBody(LlmRequest req, boolean officialOpenAi) {
        ObjectNode body = json.createObjectNode();
        body.put("model", cfg.model());
        body.put(officialOpenAi ? "max_completion_tokens" : "max_tokens", cfg.maxOutputTokens());
        ArrayNode msgs = body.putArray("messages");
        msgs.addObject().put("role", "system").put("content", req.system());
        for (LlmMessage m : req.messages()) {
            switch (m.role()) {
                case USER -> msgs.addObject().put("role", "user").put("content", m.text());
                case ASSISTANT -> {
                    ObjectNode n = msgs.addObject();
                    n.put("role", "assistant");
                    if (m.text() != null && !m.text().isBlank()) {
                        n.put("content", m.text());
                    }
                    if (!m.toolCalls().isEmpty()) {
                        ArrayNode calls = n.putArray("tool_calls");
                        for (ToolCall c : m.toolCalls()) {
                            ObjectNode cn = calls.addObject();
                            cn.put("id", c.id()).put("type", "function");
                            cn.putObject("function").put("name", c.name())
                                    .put("arguments", c.argumentsJson() == null ? "{}" : c.argumentsJson());
                        }
                    }
                }
                case TOOL -> {
                    for (ToolResult r : m.toolResults()) {
                        msgs.addObject().put("role", "tool").put("tool_call_id", r.toolCallId())
                                .put("content", r.content());
                    }
                }
            }
        }
        if (!req.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec t : req.tools()) {
                ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
                fn.put("name", t.name()).put("description", t.description());
                fn.set("parameters", json.readTree(t.schemaJson()));
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
        JsonNode msg = root.path("choices").path(0).path("message");
        if (msg.isMissingNode()) {
            throw new LlmException("Resposta do provedor sem mensagem");
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode c : msg.path("tool_calls")) {
            calls.add(new ToolCall(c.path("id").asString(""), c.path("function").path("name").asString(""),
                    c.path("function").path("arguments").asString("{}")));
        }
        return new LlmReply(msg.path("content").asString(""), calls);
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

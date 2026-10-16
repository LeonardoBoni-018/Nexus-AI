package com.nexusai.ai.llm;

import com.nexusai.ai.llm.LlmModels.LlmReply;
import com.nexusai.ai.llm.LlmModels.LlmRequest;
import com.nexusai.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Seleciona o provedor por AI_PROVIDER. Sem credenciais, a aplicacao sobe e a IA fica indisponivel. */
@Configuration
public class LlmConfig {

    @Bean
    LlmClient llmClient(AppProperties props, JsonMapper json) {
        AppProperties.Ai ai = props.ai();
        if (!ai.configured()) {
            return new UnconfiguredLlmClient();
        }
        return switch (ai.provider().trim().toLowerCase()) {
            case "anthropic" -> new AnthropicClient(ai, json);
            case "openai" -> new OpenAiCompatibleClient(ai, json);
            default -> throw new IllegalStateException("AI_PROVIDER invalido: use 'anthropic' ou 'openai'");
        };
    }

    static class UnconfiguredLlmClient implements LlmClient {
        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public String providerName() {
            return "none";
        }

        @Override
        public LlmReply chat(LlmRequest request) {
            throw new LlmException("Provedor de IA nao configurado");
        }
    }
}

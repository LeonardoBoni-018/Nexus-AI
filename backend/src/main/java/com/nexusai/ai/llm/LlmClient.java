package com.nexusai.ai.llm;

import com.nexusai.ai.llm.LlmModels.LlmReply;
import com.nexusai.ai.llm.LlmModels.LlmRequest;

/** Porta para o provedor de LLM. Implementacoes: Anthropic, OpenAI-compativel e "nao configurado". */
public interface LlmClient {

    boolean isConfigured();

    String providerName();

    /** @throws LlmException em falha de rede, timeout, status HTTP de erro ou resposta invalida */
    LlmReply chat(LlmRequest request);
}

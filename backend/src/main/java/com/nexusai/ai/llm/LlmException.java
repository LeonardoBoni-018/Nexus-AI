package com.nexusai.ai.llm;

/** Falha do provedor de IA. A mensagem nunca deve conter chaves ou corpo bruto de requisicao. */
public class LlmException extends RuntimeException {

    private final boolean timeout;

    public LlmException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public LlmException(String message) {
        this(message, null, false);
    }

    public boolean isTimeout() {
        return timeout;
    }
}

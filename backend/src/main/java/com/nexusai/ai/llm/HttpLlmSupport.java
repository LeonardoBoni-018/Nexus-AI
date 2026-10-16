package com.nexusai.ai.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** Utilitario HTTP comum aos adaptadores: timeout explicito, erros sem vazar corpo/credenciais. */
final class HttpLlmSupport {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private HttpLlmSupport() {}

    static String postJson(String url, Map<String, String> headers, String body, Duration timeout, String provider) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json");
        headers.forEach(b::header);
        try {
            HttpResponse<String> res = CLIENT.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new LlmException(provider + " respondeu HTTP " + res.statusCode());
            }
            return res.body();
        } catch (HttpTimeoutException e) {
            throw new LlmException("Tempo limite excedido ao chamar " + provider, e, true);
        } catch (IOException e) {
            throw new LlmException("Falha de comunicacao com " + provider, e, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Chamada interrompida", e, false);
        }
    }
}

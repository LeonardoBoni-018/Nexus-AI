package com.nexusai.ai.tools;

import com.nexusai.actions.ActionQueryService;
import com.nexusai.actions.ActionService;
import com.nexusai.actions.ReplenishmentPlanner;
import com.nexusai.ai.tools.ToolSupport.Args;
import com.nexusai.ai.tools.ToolSupport.Tool;
import com.nexusai.ai.tools.ToolSupport.ToolException;
import com.nexusai.analytics.AnalyticsService;
import com.nexusai.analytics.AnalyticsService.Filters;
import com.nexusai.catalog.CatalogQueryService;
import com.nexusai.common.DateRange;
import com.nexusai.common.PeriodResolver;
import com.nexusai.common.exception.ApiException;
import com.nexusai.config.AppProperties;
import com.nexusai.identity.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Catalogo FECHADO de ferramentas disponiveis ao agente. Nao existe ferramenta de SQL livre nem de
 * aprovacao/execucao: a IA consulta, simula e cria rascunhos; humanos autenticados aprovam e executam.
 */
@Component
public class ToolRegistry {

    private static final String STORE = "\"storeId\":{\"type\":\"string\",\"description\":\"UUID da loja (use getOrganizationContext para obter). Omitir = todas as lojas.\"}";
    private static final String FROM = "\"from\":{\"type\":\"string\",\"description\":\"Data inicial inclusiva AAAA-MM-DD\"}";
    private static final String TO = "\"to\":{\"type\":\"string\",\"description\":\"Data final inclusiva AAAA-MM-DD\"}";
    private static final String CATEGORY = "\"category\":{\"type\":\"string\",\"description\":\"Categoria exata do produto\"}";

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final AnalyticsService analytics;
    private final PeriodResolver periods;
    private final ReplenishmentPlanner planner;
    private final ActionService actionService;
    private final ActionQueryService actionQueries;
    private final CatalogQueryService catalog;
    private final JdbcClient jdbc;
    private final AppProperties props;

    public ToolRegistry(AnalyticsService analytics, PeriodResolver periods, ReplenishmentPlanner planner,
            ActionService actionService, ActionQueryService actionQueries, CatalogQueryService catalog,
            JdbcClient jdbc, AppProperties props) {
        this.analytics = analytics;
        this.periods = periods;
        this.planner = planner;
        this.actionService = actionService;
        this.actionQueries = actionQueries;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.props = props;
        register();
    }

    public List<Tool> availableFor(Role role) {
        return tools.values().stream().filter(t -> role.atLeast(t.minRole())).toList();
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    private void add(String name, String description, Role min, String schemaProps, String required,
            ToolSupport.ToolHandler handler) {
        String schema = "{\"type\":\"object\",\"properties\":{" + schemaProps + "},\"required\":[" + required
                + "],\"additionalProperties\":false}";
        tools.put(name, new Tool(name, description, min, schema, handler));
    }

    private DateRange range(Args a) {
        return periods.resolve(a.date("from", false), a.date("to", false));
    }

    private Filters filters(Args a) {
        return new Filters(a.uuid("storeId", false), a.string("category", false, 100), null);
    }

    private void register() {
        add("getOrganizationContext",
                "Retorna a data de hoje, fuso, organizacao, lojas (com UUIDs) e categorias. Chame primeiro quando precisar de IDs de loja ou da data atual.",
                Role.VIEWER, "", "", (ctx, a) -> {
                    String org = jdbc.sql("SELECT name FROM organizations WHERE id = :id")
                            .param("id", ctx.org().organizationId()).query(String.class).single();
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("organization", org);
                    out.put("today", periods.today());
                    out.put("timezone", props.timezone());
                    out.put("currency", "BRL");
                    out.put("userRole", ctx.org().role());
                    out.put("stores", catalog.stores(ctx.org().organizationId()));
                    out.put("categories", catalog.categories(ctx.org().organizationId()));
                    return out;
                });

        add("getSalesSummary",
                "Resumo de vendas do periodo: faturamento, numero de vendas, itens, ticket medio e margem estimada (nula se faltar custo).",
                Role.VIEWER, FROM + "," + TO + "," + STORE + "," + CATEGORY, "\"from\",\"to\"",
                (ctx, a) -> Map.of("period", range(a), "metrics",
                        analytics.salesMetrics(ctx.org().organizationId(), range(a), filters(a))));

        add("compareSalesPeriods",
                "Compara o periodo informado com o periodo imediatamente anterior de mesma duracao. Variacao nula = sem base de comparacao.",
                Role.VIEWER, FROM + "," + TO + "," + STORE + "," + CATEGORY, "\"from\",\"to\"",
                (ctx, a) -> analytics.compare(ctx.org().organizationId(), range(a), filters(a)));

        add("getLowStockProducts",
                "Lista produtos/lojas com saldo abaixo do estoque minimo, ordenados pelo maior deficit.",
                Role.VIEWER, STORE + ",\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}", "",
                (ctx, a) -> analytics.lowStock(ctx.org().organizationId(), a.uuid("storeId", false),
                        a.integer("limit", 20, 1, 50)));

        add("getProductInventory",
                "Saldo de estoque por loja de um produto. Informe productId OU query (trecho do nome ou SKU).",
                Role.VIEWER, "\"productId\":{\"type\":\"string\"},\"query\":{\"type\":\"string\",\"description\":\"Nome ou SKU\"}", "",
                (ctx, a) -> {
                    UUID org = ctx.org().organizationId();
                    UUID id = a.uuid("productId", false);
                    if (id != null) {
                        return catalog.product(org, id);
                    }
                    String q = a.string("query", false, 100);
                    if (q == null) {
                        throw new ToolException("Informe productId ou query");
                    }
                    var page = catalog.products(org, q, null, 0, 5);
                    return Map.of("matches", page.items().stream().map(p -> catalog.product(org, p.id())).toList(),
                            "totalMatches", page.totalItems());
                });

        add("getTopSellingProducts", "Produtos mais vendidos no periodo, por unidades ou faturamento.",
                Role.VIEWER, FROM + "," + TO + "," + STORE + "," + CATEGORY
                        + ",\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}"
                        + ",\"orderBy\":{\"type\":\"string\",\"enum\":[\"units\",\"revenue\"]}",
                "\"from\",\"to\"",
                (ctx, a) -> Map.of("period", range(a), "products", analytics.topProducts(ctx.org().organizationId(),
                        range(a), filters(a), a.integer("limit", 10, 1, 20), a.string("orderBy", false, 10))));

        add("analyzeInventoryCoverage",
                "Cobertura de estoque (dias) = saldo / media diaria de vendas na janela. Ordenado do menor para o maior. Sem vendas na janela => cobertura nula.",
                Role.VIEWER, STORE + ",\"productId\":{\"type\":\"string\"},\"windowDays\":{\"type\":\"integer\",\"minimum\":7,\"maximum\":90}"
                        + ",\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}", "",
                (ctx, a) -> {
                    int window = a.integer("windowDays", 28, 7, 90);
                    return Map.of("windowDays", window, "asOf", periods.today(), "items",
                            analytics.coverage(ctx.org().organizationId(), a.uuid("storeId", false),
                                    a.uuid("productId", false), periods.today(), window, a.integer("limit", 20, 1, 50)));
                });

        add("simulateReplenishment",
                "Simula a reposicao com a regra deterministica (nao grava nada). Retorna quantidade sugerida por produto.",
                Role.VIEWER, "\"storeId\":{\"type\":\"string\"},\"productIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":50}",
                "\"storeId\"",
                (ctx, a) -> Map.of("rule",
                        "alvo = max(estoque minimo, ceil(media diaria 28d * (cobertura alvo + lead time))); sugerido = max(0, alvo - saldo)",
                        "lines", planner.plan(ctx.org().organizationId(), a.uuid("storeId", true), a.uuidList("productIds", 50))));

        add("createReplenishmentDraft",
                "Cria uma proposta de reposicao em RASCUNHO (quantidades calculadas pelo sistema). Nao executa nada: um gerente humano precisa submeter, aprovar e executar.",
                Role.ANALYST, "\"storeId\":{\"type\":\"string\"},\"productIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":50}"
                        + ",\"justification\":{\"type\":\"string\",\"description\":\"Justificativa baseada nos dados consultados\"}",
                "\"storeId\",\"justification\"",
                (ctx, a) -> {
                    try {
                        UUID id = actionService.createReplenishment(ctx.org(), a.uuid("storeId", true),
                                a.uuidList("productIds", 50), a.string("justification", true, 1900), true,
                                ctx.conversationId());
                        var d = actionQueries.detail(ctx.org().organizationId(), id);
                        return Map.of("actionId", d.id(), "status", d.status(), "items", d.items(),
                                "note", "Rascunho criado. Aguarda envio para aprovacao e decisao humana.");
                    } catch (ApiException e) {
                        throw new ToolException(e.getMessage());
                    }
                });

        add("getActionStatus", "Consulta estado, itens e historico de aprovacao de uma proposta.", Role.VIEWER,
                "\"actionId\":{\"type\":\"string\"}", "\"actionId\"", (ctx, a) -> {
                    try {
                        return actionQueries.detail(ctx.org().organizationId(), a.uuid("actionId", true));
                    } catch (ApiException e) {
                        throw new ToolException(e.getMessage());
                    }
                });
    }
}

package com.nexusai.demo;

import com.nexusai.catalog.CatalogRepositories.ProductRepository;
import com.nexusai.catalog.CatalogRepositories.StoreRepository;
import com.nexusai.catalog.Product;
import com.nexusai.catalog.Store;
import com.nexusai.config.AppProperties;
import com.nexusai.identity.IdentityRepositories.MembershipRepository;
import com.nexusai.identity.IdentityRepositories.OrganizationRepository;
import com.nexusai.identity.IdentityRepositories.UserRepository;
import com.nexusai.identity.Membership;
import com.nexusai.identity.Organization;
import com.nexusai.identity.Role;
import com.nexusai.identity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dados DEMONSTRATIVOS (organizacoes marcadas como demo). Executa somente com app.demo-seed=true e banco sem
 * organizacoes. Vendas, itens, saldos e movimentos sao gerados de forma consistente: saldo final = abertura -
 * vendas, nunca negativo. Reposicoes executadas depois somam movimentos REPLENISHMENT.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo@12345!";
    public static final int HISTORY_DAYS = 56;
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record ProductDef(String sku, String name, String category, String cost, String price, int weight,
            int leadTime) {}

    private static final List<ProductDef> CATALOG = List.of(
            new ProductDef("BEB-001", "Agua Mineral 500ml", "Bebidas", "0.90", "2.50", 10, 7),
            new ProductDef("BEB-002", "Refrigerante Cola 2L", "Bebidas", "5.20", "9.90", 8, 7),
            new ProductDef("BEB-003", "Suco de Laranja 1L", "Bebidas", "4.50", "8.90", 5, 7),
            new ProductDef("BEB-004", "Cerveja Lager 350ml", "Bebidas", "2.80", "5.50", 7, 7),
            new ProductDef("BEB-005", "Energetico 250ml", "Bebidas", "5.00", "9.50", 3, 10),
            new ProductDef("MER-001", "Arroz Tipo 1 5kg", "Mercearia", "21.00", "32.90", 6, 7),
            new ProductDef("MER-002", "Feijao Carioca 1kg", "Mercearia", "5.50", "9.90", 6, 7),
            new ProductDef("MER-003", "Macarrao Espaguete 500g", "Mercearia", "2.60", "5.20", 5, 7),
            new ProductDef("MER-004", "Azeite Extra Virgem 500ml", "Mercearia", "22.00", "39.90", 2, 10),
            new ProductDef("MER-005", "Cafe Torrado 500g", "Mercearia", "14.00", "24.90", 5, 7),
            new ProductDef("MER-006", "Acucar Refinado 1kg", "Mercearia", "3.40", "5.90", 5, 7),
            new ProductDef("LAT-001", "Leite Integral 1L", "Laticinios", "3.90", "6.50", 9, 7),
            new ProductDef("LAT-002", "Queijo Mussarela 200g", "Laticinios", "9.00", "16.90", 4, 7),
            new ProductDef("LAT-003", "Iogurte Natural 170g", "Laticinios", "1.60", "3.50", 6, 7),
            new ProductDef("LAT-004", "Manteiga 200g", "Laticinios", "8.50", "15.90", 3, 7),
            new ProductDef("HIG-001", "Sabonete 90g", "Higiene", "1.20", "2.90", 5, 7),
            new ProductDef("HIG-002", "Shampoo 350ml", "Higiene", "9.00", "18.90", 3, 10),
            new ProductDef("HIG-003", "Creme Dental 90g", "Higiene", "2.80", "5.90", 4, 7),
            new ProductDef("HIG-004", "Papel Higienico 12un", "Higiene", "14.00", "25.90", 4, 7),
            new ProductDef("LIM-001", "Detergente 500ml", "Limpeza", "1.70", "3.40", 5, 7),
            new ProductDef("LIM-002", "Sabao em Po 1kg", "Limpeza", "9.00", "17.90", 3, 7),
            new ProductDef("LIM-003", "Desinfetante 1L", "Limpeza", "4.20", "8.50", 3, 7),
            new ProductDef("PAD-001", "Pao de Forma 500g", "Padaria", "5.00", "9.50", 6, 7),
            new ProductDef("PAD-002", "Biscoito Recheado", "Padaria", "2.00", "4.50", 5, 7),
            // Produto sem movimentacao recente (so vende no inicio do historico).
            new ProductDef("PAD-099", "Biscoito Edicao Limitada Limao", "Padaria", "2.20", "5.00", 4, 7));

    private static final String DEAD_SKU = "PAD-099";

    private final AppProperties props;
    private final Clock clock;
    private final OrganizationRepository orgs;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final jakarta.persistence.EntityManager em;
    private final com.nexusai.alerts.AlertService alerts;

    public DemoDataSeeder(AppProperties props, Clock clock, OrganizationRepository orgs, UserRepository users,
            MembershipRepository memberships, StoreRepository stores, ProductRepository products,
            PasswordEncoder encoder, JdbcTemplate jdbc, TransactionTemplate tx,
            jakarta.persistence.EntityManager em, com.nexusai.alerts.AlertService alerts) {
        this.props = props;
        this.clock = clock;
        this.orgs = orgs;
        this.users = users;
        this.memberships = memberships;
        this.stores = stores;
        this.products = products;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.tx = tx;
        this.em = em;
        this.alerts = alerts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.demoSeed()) {
            return;
        }
        if (orgs.count() > 0) {
            log.info("Seed de demonstracao ignorado: ja existem organizacoes");
            return;
        }
        tx.executeWithoutResult(s -> seed());
        orgs.findAll().forEach(o -> alerts.evaluate(o.getId()));
        log.info("Seed de demonstracao concluido (usuarios @aurora.demo / @casaecia.demo)");
    }

    public void seed() {
        Instant now = Instant.now(clock);
        String hash = encoder.encode(DEMO_PASSWORD);

        Organization aurora = orgs.save(new Organization("Mercado Aurora (DEMO)", "mercado-aurora-demo", true, now));
        Organization casa = orgs.save(new Organization("Casa & Cia (DEMO)", "casa-e-cia-demo", true, now));

        user("admin@aurora.demo", "Ana Admin", hash, aurora, Role.ADMIN, now);
        user("gerente@aurora.demo", "Gabriel Gerente", hash, aurora, Role.MANAGER, now);
        user("analista@aurora.demo", "Alice Analista", hash, aurora, Role.ANALYST, now);
        user("leitor@aurora.demo", "Lucas Leitor", hash, aurora, Role.VIEWER, now);
        user("admin@casaecia.demo", "Carla Casa", hash, casa, Role.ADMIN, now);

        populate(aurora.getId(), List.of(new String[] {"Loja Centro", "Sao Paulo"},
                new String[] {"Loja Pinheiros", "Sao Paulo"}, new String[] {"Loja Campinas", "Campinas"}),
                CATALOG, 42L, 1.0, now);
        populate(casa.getId(), List.of(new String[] {"Casa Matriz", "Curitiba"}, new String[] {"Casa Filial", "Curitiba"}),
                CATALOG.subList(0, 8), 7L, 0.5, now);
    }

    private void user(String email, String name, String hash, Organization org, Role role, Instant now) {
        User u = users.save(new User(email, name, hash, now));
        memberships.save(new Membership(u.getId(), org.getId(), role, now));
    }

    private record SaleRow(UUID id, UUID storeId, Instant at, LocalDate date, List<ItemRow> items) {}

    private record ItemRow(UUID productId, int qty, BigDecimal price, BigDecimal cost) {}

    private void populate(UUID orgId, List<String[]> storeDefs, List<ProductDef> defs, long seed, double volume,
            Instant now) {
        ZoneId zone = props.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Random rnd = new Random(seed);

        List<Store> storeList = new ArrayList<>();
        for (String[] s : storeDefs) {
            storeList.add(stores.save(new Store(orgId, s[0], s[1], now)));
        }
        Map<String, Product> productBySku = new HashMap<>();
        List<Product> placeholder = new ArrayList<>();
        // minStock sera ajustado apos simular a demanda; cria com valor provisorio e atualiza via SQL.
        for (ProductDef d : defs) {
            Product p = products.save(new Product(orgId, d.sku(), d.name(), d.category(), new BigDecimal(d.cost()),
                    new BigDecimal(d.price()), 1, 21, d.leadTime(), now));
            productBySku.put(d.sku(), p);
            placeholder.add(p);
        }

        // 1) simulacao de vendas
        List<SaleRow> sales = new ArrayList<>();
        Map<String, Integer> soldByStoreProduct = new HashMap<>();
        int totalWeight = defs.stream().mapToInt(ProductDef::weight).sum();
        for (int offset = HISTORY_DAYS - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            double factor = (weekend ? 1.35 : 1.0) * volume * (offset < 7 ? 0.74 : 1.0); // queda na ultima semana
            for (Store store : storeList) {
                int count = (int) Math.round((10 + rnd.nextInt(5)) * factor);
                for (int i = 0; i < count; i++) {
                    int lines = 1 + rnd.nextInt(3);
                    Map<UUID, ItemRow> chosen = new HashMap<>();
                    for (int l = 0; l < lines; l++) {
                        ProductDef d = pick(defs, totalWeight, rnd);
                        if (DEAD_SKU.equals(d.sku()) && offset < HISTORY_DAYS - 10) {
                            continue;
                        }
                        Product p = productBySku.get(d.sku());
                        int qty = 1 + rnd.nextInt(3);
                        chosen.merge(p.getId(), new ItemRow(p.getId(), qty, p.getPrice(), p.getCost()),
                                (a, b) -> new ItemRow(a.productId(), a.qty() + b.qty(), a.price(), a.cost()));
                    }
                    if (chosen.isEmpty()) {
                        continue;
                    }
                    int hour = 8 + rnd.nextInt(13);
                    if (offset == 0) {
                        hour = Math.min(hour, Math.max(8, java.time.LocalTime.now(clock.withZone(zone)).getHour() - 1));
                    }
                    Instant at = date.atTime(hour, rnd.nextInt(60)).atZone(zone).toInstant();
                    sales.add(new SaleRow(UUID.randomUUID(), store.getId(), at, date, new ArrayList<>(chosen.values())));
                    chosen.values().forEach(it -> soldByStoreProduct.merge(store.getId() + ":" + it.productId(), it.qty(), Integer::sum));
                }
            }
        }

        // 2) parametros de estoque a partir da demanda observada
        Map<UUID, Integer> minStock = new HashMap<>();
        Map<String, Integer> finalStock = new HashMap<>();
        int lowPicked = 0;
        for (Product p : placeholder) {
            double maxAvg = 0;
            for (Store st : storeList) {
                maxAvg = Math.max(maxAvg, soldByStoreProduct.getOrDefault(st.getId() + ":" + p.getId(), 0) / (double) HISTORY_DAYS);
            }
            int min = Math.max(6, (int) Math.ceil(maxAvg * 5));
            minStock.put(p.getId(), min);
            for (Store st : storeList) {
                double avg = soldByStoreProduct.getOrDefault(st.getId() + ":" + p.getId(), 0) / (double) HISTORY_DAYS;
                int qty = (int) Math.round(Math.max(avg, 0.3) * (20 + rnd.nextInt(16)));
                qty = Math.max(qty, min + 2);
                finalStock.put(st.getId() + ":" + p.getId(), qty);
            }
        }
        // cenarios: baixo estoque, ruptura e risco de ruptura (primeira loja)
        Store first = storeList.get(0);
        String[] lowSkus = {"BEB-001", "LAT-001", "MER-002", "HIG-003"};
        for (String sku : lowSkus) {
            Product p = productBySku.get(sku);
            if (p == null) {
                continue;
            }
            int min = minStock.get(p.getId());
            int qty = lowPicked == 0 ? 0 : Math.max(1, min / 2);
            finalStock.put(first.getId() + ":" + p.getId(), qty);
            lowPicked++;
        }
        Product risk = productBySku.get("BEB-002");
        double riskAvg = soldByStoreProduct.getOrDefault(first.getId() + ":" + risk.getId(), 0) / (double) HISTORY_DAYS;
        finalStock.put(first.getId() + ":" + risk.getId(),
                Math.max(minStock.get(risk.getId()), (int) Math.ceil(riskAvg * 6)));
        Product dead = productBySku.get(DEAD_SKU);
        if (dead != null) {
            for (Store st : storeList) {
                finalStock.put(st.getId() + ":" + dead.getId(), 40);
            }
        }

        em.flush(); // entidades JPA devem existir antes dos inserts em lote via JDBC (mesma transacao)
        // 3) persistencia: min_stock, saldos, movimentos de abertura e vendas
        Instant opening = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant();
        List<Object[]> balanceRows = new ArrayList<>();
        List<Object[]> movementRows = new ArrayList<>();
        Map<String, Integer> running = new HashMap<>();
        for (Store st : storeList) {
            for (Product p : placeholder) {
                String key = st.getId() + ":" + p.getId();
                int sold = soldByStoreProduct.getOrDefault(key, 0);
                int open = finalStock.get(key) + sold;
                running.put(key, open);
                movementRows.add(new Object[] {UUID.randomUUID(), orgId, st.getId(), p.getId(), "OPENING", open, open,
                        null, null, null, Timestamp.from(opening)});
                balanceRows.add(new Object[] {UUID.randomUUID(), orgId, st.getId(), p.getId(), finalStock.get(key),
                        0L, Timestamp.from(now)});
            }
        }
        jdbc.batchUpdate("UPDATE products SET min_stock = ? WHERE id = ?", placeholder.stream()
                .map(p -> new Object[] {minStock.get(p.getId()), p.getId()}).toList());
        sales.sort((a, b) -> a.at().compareTo(b.at()));
        List<Object[]> saleRows = new ArrayList<>();
        List<Object[]> itemRows = new ArrayList<>();
        for (SaleRow sale : sales) {
            BigDecimal total = BigDecimal.ZERO;
            for (ItemRow it : sale.items()) {
                total = total.add(it.price().multiply(BigDecimal.valueOf(it.qty())));
                itemRows.add(new Object[] {UUID.randomUUID(), sale.id(), it.productId(), it.qty(), it.price(), it.cost()});
                String key = sale.storeId() + ":" + it.productId();
                int after = running.merge(key, -it.qty(), Integer::sum);
                movementRows.add(new Object[] {UUID.randomUUID(), orgId, sale.storeId(), it.productId(), "SALE",
                        -it.qty(), after, "Sale", sale.id(), null, Timestamp.from(sale.at())});
            }
            saleRows.add(new Object[] {sale.id(), orgId, sale.storeId(), Timestamp.from(sale.at()), sale.date(),
                    total.setScale(2, RoundingMode.HALF_UP)});
        }
        jdbc.batchUpdate("INSERT INTO inventory_balances (id, organization_id, store_id, product_id, quantity, version, "
                + "updated_at) VALUES (?,?,?,?,?,?,?)", balanceRows);
        jdbc.batchUpdate("INSERT INTO sales (id, organization_id, store_id, occurred_at, sale_date, total) "
                + "VALUES (?,?,?,?,?,?)", saleRows);
        jdbc.batchUpdate("INSERT INTO sale_items (id, sale_id, product_id, quantity, unit_price, unit_cost) "
                + "VALUES (?,?,?,?,?,?)", itemRows);
        jdbc.batchUpdate("INSERT INTO inventory_movements (id, organization_id, store_id, product_id, type, "
                + "quantity_delta, balance_after, reference_type, reference_id, idempotency_key, occurred_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", movementRows);
    }

    private static ProductDef pick(List<ProductDef> defs, int totalWeight, Random rnd) {
        int r = rnd.nextInt(totalWeight);
        for (ProductDef d : defs) {
            r -= d.weight();
            if (r < 0) {
                return d;
            }
        }
        return defs.get(0);
    }
}

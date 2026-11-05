package dev.snehil.cohort.gen;

import dev.snehil.cohort.domain.Category;
import dev.snehil.cohort.domain.Customer;
import dev.snehil.cohort.domain.Order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Deterministic synthetic grocery customers and their order histories.
 *
 * <p>User {@code i} is generated from {@code seed} and {@code i} alone, so any single user can be
 * regenerated on demand without storing 100k histories in memory, and every benchmark run sees
 * the same data.
 *
 * <p>Each user gets a persona (weekly regular, daily dairy buyer, monthly stock-up, lapsing,
 * churned, new, one-and-done, premium foodie) with its own order rhythm and basket mix, so the
 * default cohorts have realistic, non-trivial membership.
 */
public final class SyntheticDataGenerator {
    private static final String[] CITIES = {"bengaluru", "hyderabad", "mumbai", "pune", "delhi"};
    private static final int HISTORY_DAYS = 180;

    private final long seed;
    private final LocalDate today;

    public SyntheticDataGenerator(long seed, LocalDate today) {
        this.seed = seed;
        this.today = today;
    }

    public record GeneratedUser(Customer customer, List<Order> orders, Persona persona) {}

    public enum Persona {
        WEEKLY_REGULAR(22), DAILY_DAIRY(8), MONTHLY_STOCKUP(14), PREMIUM_FOODIE(6),
        LAPSING(12), CHURNED(16), NEW_CUSTOMER(10), ONE_AND_DONE(12);

        final int weight;

        Persona(int weight) {
            this.weight = weight;
        }
    }

    public static String userId(int index) {
        return "u%06d".formatted(index);
    }

    public GeneratedUser user(int index) {
        SplittableRandom rnd = new SplittableRandom(seed * 1_000_003L + index);
        Persona persona = pickPersona(rnd);
        String id = userId(index);
        String city = CITIES[rnd.nextInt(CITIES.length)];

        // Window of days (as "days ago") in which this persona orders, and its gap between orders.
        int activeFrom, activeTo, minGap, maxGap;
        switch (persona) {
            case WEEKLY_REGULAR -> { activeFrom = HISTORY_DAYS; activeTo = rnd.nextInt(0, 6); minGap = 5; maxGap = 9; }
            case DAILY_DAIRY -> { activeFrom = HISTORY_DAYS; activeTo = rnd.nextInt(0, 3); minGap = 1; maxGap = 3; }
            case MONTHLY_STOCKUP -> { activeFrom = HISTORY_DAYS; activeTo = rnd.nextInt(0, 25); minGap = 20; maxGap = 35; }
            case PREMIUM_FOODIE -> { activeFrom = HISTORY_DAYS; activeTo = rnd.nextInt(0, 8); minGap = 3; maxGap = 8; }
            case LAPSING -> { activeFrom = HISTORY_DAYS; activeTo = rnd.nextInt(15, 50); minGap = 4; maxGap = 9; }
            case CHURNED -> { activeFrom = HISTORY_DAYS; activeTo = rnd.nextInt(65, 150); minGap = 5; maxGap = 20; }
            case NEW_CUSTOMER -> { activeFrom = rnd.nextInt(1, 14); activeTo = 0; minGap = 2; maxGap = 6; }
            case ONE_AND_DONE -> { activeFrom = rnd.nextInt(22, 170); activeTo = activeFrom; minGap = 1; maxGap = 1; }
            default -> throw new IllegalStateException();
        }
        int startOffset = persona == Persona.ONE_AND_DONE || persona == Persona.NEW_CUSTOMER ? 0 : rnd.nextInt(0, 60);
        int signupDaysAgo = Math.min(HISTORY_DAYS + 30, activeFrom + rnd.nextInt(0, 30));

        List<Order> orders = new ArrayList<>();
        int daysAgo = activeFrom - startOffset;
        int n = 0;
        while (daysAgo >= activeTo) {
            orders.add(order(id, n++, today.minusDays(daysAgo), persona, rnd));
            if (persona == Persona.ONE_AND_DONE) break;
            daysAgo -= rnd.nextInt(minGap, maxGap + 1);
        }
        Customer customer = new Customer(id, city, today.minusDays(signupDaysAgo));
        return new GeneratedUser(customer, List.copyOf(orders), persona);
    }

    /** A one-off extra order for {@code user}, placed on {@code date}. Used to simulate a day of activity. */
    public Order extraOrder(GeneratedUser user, LocalDate date, int salt) {
        SplittableRandom rnd = new SplittableRandom(seed ^ (31L * salt + user.customer().id().hashCode()));
        return order(user.customer().id(), 10_000 + salt, date, user.persona(), rnd);
    }

    private Order order(String userId, int n, LocalDate date, Persona persona, SplittableRandom rnd) {
        Set<Category> basket = EnumSet.noneOf(Category.class);
        long amount;
        boolean premium = false;
        switch (persona) {
            case DAILY_DAIRY -> {
                basket.add(Category.DAIRY);
                if (rnd.nextInt(3) == 0) basket.add(Category.BAKERY);
                amount = rnd.nextLong(60_00, 250_00);
            }
            case MONTHLY_STOCKUP -> {
                basket.add(Category.STAPLES);
                addRandom(basket, rnd, 3);
                amount = rnd.nextLong(1_500_00, 4_500_00);
            }
            case PREMIUM_FOODIE -> {
                basket.add(Category.PRODUCE);
                addRandom(basket, rnd, 3);
                premium = rnd.nextInt(10) < 7;
                amount = rnd.nextLong(800_00, 3_000_00);
            }
            default -> {
                if (rnd.nextInt(10) < 7) basket.add(Category.DAIRY);
                if (rnd.nextInt(10) < 6) basket.add(Category.PRODUCE);
                if (rnd.nextInt(10) < 4) basket.add(Category.STAPLES);
                addRandom(basket, rnd, 2);
                premium = rnd.nextInt(20) == 0;
                amount = rnd.nextLong(250_00, 1_400_00);
            }
        }
        return new Order(userId + "-o" + n, userId, date, amount, basket, premium);
    }

    private static void addRandom(Set<Category> basket, SplittableRandom rnd, int max) {
        Category[] all = Category.values();
        int count = rnd.nextInt(1, max + 1);
        for (int i = 0; i < count; i++) basket.add(all[rnd.nextInt(all.length)]);
    }

    private static Persona pickPersona(SplittableRandom rnd) {
        int total = 0;
        for (Persona p : Persona.values()) total += p.weight;
        int roll = rnd.nextInt(total);
        for (Persona p : Persona.values()) {
            roll -= p.weight;
            if (roll < 0) return p;
        }
        throw new IllegalStateException("weights don't add up");
    }
}

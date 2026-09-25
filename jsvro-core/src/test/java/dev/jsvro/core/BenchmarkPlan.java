package dev.jsvro.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class BenchmarkPlan {
    private static final int MIN_ITERATIONS = 3;
    private static final int MAX_ITERATIONS = 10_000;
    private static final int MAX_ROWS = 100_000;
    private static final String FULL_SIZES = "10,20,30,40,50,100,500,1000,3000,5000,10000,20000,30000,50000,100000";
    private static final long FULL_ROW_BUDGET = 3_000_000L;

    enum Mode { FULL, CUSTOM }

    private final Mode mode;
    private final Map<Integer, Integer> iterationsByRows;

    private BenchmarkPlan(Mode mode, Map<Integer, Integer> iterationsByRows) {
        this.mode = mode;
        this.iterationsByRows = Map.copyOf(iterationsByRows);
    }

    static BenchmarkPlan full() {
        return budgeted(FULL_SIZES, FULL_ROW_BUDGET);
    }

    static BenchmarkPlan explicit(String plan) {
        Map<Integer, Integer> iterationsByRows = new TreeMap<>();
        for (String entry : plan.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected rows:iterations but got '" + entry.trim() + "'");
            }
            int rows = rows(parts[0], entry);
            int iterations = positive(parts[1], "iterations", entry);
            if (iterations > MAX_ITERATIONS) {
                throw new IllegalArgumentException(
                        "At most " + MAX_ITERATIONS + " iterations are allowed, but '" + entry.trim() + "' asks for " + iterations);
            }
            if (iterationsByRows.put(rows, iterations) != null) {
                throw new IllegalArgumentException("Rows " + rows + " appear more than once in '" + plan + "'");
            }
        }
        return new BenchmarkPlan(Mode.CUSTOM, iterationsByRows);
    }

    static BenchmarkPlan budgeted(String sizes, long rowBudget) {
        Map<Integer, Integer> iterationsByRows = new TreeMap<>();
        for (String size : sizes.split(",")) {
            int rows = rows(size, size.trim());
            long iterations = Math.max(MIN_ITERATIONS, Math.min(MAX_ITERATIONS, rowBudget / rows));
            iterationsByRows.put(rows, (int) iterations);
        }
        return new BenchmarkPlan(Mode.FULL, iterationsByRows);
    }

    static BenchmarkPlan fromSystemProperties() {
        String plan = System.getProperty("jsvro.benchmark.plan");
        boolean full = Boolean.getBoolean("jsvro.benchmark.full");
        if (full && plan != null && !plan.isBlank()) {
            throw new IllegalArgumentException("Choose either a full run or rows:iterations pairs, not both");
        }
        if (full) {
            return full();
        }
        if (plan != null && !plan.isBlank()) {
            return explicit(plan);
        }
        throw new IllegalArgumentException(
                "Choose a run: ./benchmark -full, or ./benchmark -ri 10:1000 20:500 for rows:iterations pairs");
    }

    Mode mode() {
        return mode;
    }

    List<Integer> sizes() {
        return iterationsByRows.keySet().stream().sorted().toList();
    }

    int iterations(int rows) {
        return iterationsByRows.get(rows);
    }

    private static int rows(String value, String entry) {
        int rows = positive(value, "rows", entry);
        if (rows > MAX_ROWS) {
            throw new IllegalArgumentException(
                    "At most " + MAX_ROWS + " rows are allowed, but '" + entry.trim() + "' asks for " + rows);
        }
        return rows;
    }

    private static int positive(String value, String name, String entry) {
        try {
            int number = Integer.parseInt(value.trim());
            if (number > 0) {
                return number;
            }
        }
        catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Expected a positive number of " + name + " in '" + entry.trim() + "'");
    }

    @Override
    public String toString() {
        return mode + " " + Arrays.toString(sizes().stream().map(rows -> rows + ":" + iterations(rows)).toArray());
    }
}

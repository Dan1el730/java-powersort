import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * BenchmarkSuite
 *
 * Main campaign harness for the final report:
 * - Algorithms: V0 (Powersort), V4 (hybrid), Timsort (Java object sort)
 * - Distributions: 5 input families
 * - Sizes: 6 array sizes
 * - Repetitions: configurable (default 30)
 *
 * Output CSV columns:
 * algorithm,distribution,n,rep,time_ns,merge_cost,peak_stack
 *
 * Usage:
 *   javac sort.java BenchmarkSuite.java
 *   java BenchmarkSuite
 *   java BenchmarkSuite --quick
 *   java BenchmarkSuite --out=benchmark_results.csv --reps=30
 */
public class BenchmarkSuite {

    private static final int[] DEFAULT_SIZES = {10_000, 50_000, 100_000, 500_000, 1_000_000, 2_000_000};
    private static final int[] QUICK_SIZES = {1_000, 5_000};
    private static final int DEFAULT_REPS = 30;
    private static final int QUICK_REPS = 3;
    private static final int WARMUP_ROUNDS = 5;
    private static final long BASE_SEED = 20260409L;

    private enum Distribution {
        RANDOM,
        SORTED,
        FEW_UNIQUE,
        ALTERNATING,
        ADVERSARIAL
    }

    private enum Algorithm {
        V0,
        V4,
        TIMSORT
    }

    public static void main(String[] args) throws IOException {
        boolean quick = false;
        String output = "benchmark_results.csv";
        int reps = DEFAULT_REPS;

        for (String arg : args) {
            if ("--quick".equalsIgnoreCase(arg)) {
                quick = true;
            } else if (arg.startsWith("--out=")) {
                output = arg.substring("--out=".length());
            } else if (arg.startsWith("--reps=")) {
                reps = Integer.parseInt(arg.substring("--reps=".length()));
            }
        }

        if (quick && reps == DEFAULT_REPS) {
            reps = QUICK_REPS;
        }

        int[] sizes = quick ? QUICK_SIZES : DEFAULT_SIZES;

        System.out.println("=== BenchmarkSuite ===");
        System.out.println("Output: " + output);
        System.out.println("Sizes: " + Arrays.toString(sizes));
        System.out.println("Distributions: " + Arrays.toString(Distribution.values()));
        System.out.println("Algorithms: " + Arrays.toString(Algorithm.values()));
        System.out.println("Repetitions: " + reps);

        warmupAll();

        try (FileWriter fw = new FileWriter(output)) {
            fw.write("algorithm,distribution,n,rep,time_ns,merge_cost,peak_stack\n");

            int totalConfigs = sizes.length * Distribution.values().length * reps;
            int completedConfigs = 0;

            for (int n : sizes) {
                for (Distribution distribution : Distribution.values()) {
                    System.out.printf("\nConfig: n=%d, distribution=%s%n", n, distribution.name().toLowerCase());

                    for (int rep = 1; rep <= reps; rep++) {
                        completedConfigs++;

                        long seed = seedFor(n, distribution, rep);
                        int[] base = generateArray(n, distribution, new Random(seed));

                        Row v0Row = benchmarkV0(base, distribution, rep);
                        Row v4Row = benchmarkV4(base, distribution, rep);
                        Row tsRow = benchmarkTimsort(base, distribution, rep);

                        fw.write(v0Row.toCsvLine());
                        fw.write(v4Row.toCsvLine());
                        fw.write(tsRow.toCsvLine());

                        if (rep % Math.max(1, reps / 5) == 0 || rep == reps) {
                            System.out.printf("  Progress: %d/%d reps (global %d/%d)%n", rep, reps, completedConfigs, totalConfigs);
                        }
                    }
                }
            }
        }

        System.out.println("\nBenchmark complete. CSV written.");
    }

    private static void warmupAll() {
        Random rnd = new Random(BASE_SEED);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            int[] a = generateArray(5_000, Distribution.RANDOM, rnd);

            int[] w0 = Arrays.copyOf(a, a.length);
            sort.powersort(w0, sort.Version.V0);

            int[] w4 = Arrays.copyOf(a, a.length);
            sort.powersort(w4, sort.Version.V4);

            int[] wt = Arrays.copyOf(a, a.length);
            timsortStable(wt);
        }
    }

    private static long seedFor(int n, Distribution distribution, int rep) {
        return BASE_SEED
                + 31L * n
                + 17L * distribution.ordinal()
                + 1_000_003L * rep;
    }

    private static int[] generateArray(int n, Distribution distribution, Random rnd) {
        int[] a = new int[n];
        switch (distribution) {
            case RANDOM:
                for (int i = 0; i < n; i++) {
                    a[i] = rnd.nextInt();
                }
                break;

            case SORTED:
                for (int i = 0; i < n; i++) {
                    a[i] = i;
                }
                break;

            case FEW_UNIQUE:
                for (int i = 0; i < n; i++) {
                    a[i] = rnd.nextInt(100);
                }
                break;

            case ALTERNATING:
                int runLen = Math.max(2, (int) Math.sqrt(n));
                for (int i = 0; i < n; i++) {
                    int block = i / runLen;
                    int offset = i % runLen;
                    int base = block * runLen;
                    int val;
                    if (block % 2 == 0) {
                        val = base + offset;
                    } else {
                        val = base + (runLen - 1 - offset);
                    }
                    a[i] = val;
                }
                break;

            case ADVERSARIAL:
                for (int i = 0; i < n; i++) {
                    int chunk = (i / 10) % 2;
                    if (chunk == 0) {
                        a[i] = i;
                    } else {
                        a[i] = n - i;
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown distribution: " + distribution);
        }
        return a;
    }

    private static Row benchmarkV0(int[] base, Distribution distribution, int rep) {
        int[] arr = Arrays.copyOf(base, base.length);

        int peakStack = computePeakStackV0(base);
        long t0 = System.nanoTime();
        long mergeCost = sort.powersort(arr, sort.Version.V0);
        long t1 = System.nanoTime();

        assertSorted(arr, "V0", distribution, rep);

        return new Row(
                Algorithm.V0,
                distribution,
                base.length,
                rep,
                t1 - t0,
                mergeCost,
                peakStack
        );
    }

    private static Row benchmarkV4(int[] base, Distribution distribution, int rep) {
        int[] arr = Arrays.copyOf(base, base.length);

        int peakStack = computePeakStackV4(base);
        long t0 = System.nanoTime();
        long mergeCost = sort.powersort(arr, sort.Version.V4);
        long t1 = System.nanoTime();

        assertSorted(arr, "V4", distribution, rep);

        return new Row(
                Algorithm.V4,
                distribution,
                base.length,
                rep,
                t1 - t0,
                mergeCost,
                peakStack
        );
    }

    private static Row benchmarkTimsort(int[] base, Distribution distribution, int rep) {
        int[] arr = Arrays.copyOf(base, base.length);

        long t0 = System.nanoTime();
        timsortStable(arr);
        long t1 = System.nanoTime();

        assertSorted(arr, "TIMSORT", distribution, rep);

        return new Row(
                Algorithm.TIMSORT,
                distribution,
                base.length,
                rep,
                t1 - t0,
                -1,
                -1
        );
    }

    // Java's stable TimSort is used for object arrays.
    private static void timsortStable(int[] a) {
        Integer[] boxed = new Integer[a.length];
        for (int i = 0; i < a.length; i++) {
            boxed[i] = a[i];
        }
        Arrays.sort(boxed);
        for (int i = 0; i < a.length; i++) {
            a[i] = boxed[i];
        }
    }

    private static void assertSorted(int[] a, String alg, Distribution dist, int rep) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                throw new IllegalStateException(
                        "Sort check failed: alg=" + alg + ", dist=" + dist + ", rep=" + rep + ", idx=" + i
                );
            }
        }
    }

    private static int computePeakStackV0(int[] a) {
        int n = a.length;
        int i = 0;
        ArrayList<sort.Run> stack = new ArrayList<>();
        int peak = 0;

        while (i < n) {
            int j = sort.extendRun(a, i);
            stack.add(new sort.Run(i, j - i, 0));
            if (stack.size() > peak) {
                peak = stack.size();
            }
            i = j;

            while (i <= n && stack.size() >= 2) {
                sort.Run right = stack.get(stack.size() - 1);
                sort.Run left = stack.get(stack.size() - 2);
                int p = sort.power(left, right, n);
                if (p <= left.getPower()) {
                    stack.set(stack.size() - 2, new sort.Run(left.getStart(), left.getLength() + right.getLength(), left.getPower()));
                    stack.remove(stack.size() - 1);
                } else {
                    break;
                }
            }
        }

        while (stack.size() >= 2) {
            sort.Run right = stack.get(stack.size() - 1);
            sort.Run left = stack.get(stack.size() - 2);
            stack.set(stack.size() - 2, new sort.Run(left.getStart(), left.getLength() + right.getLength(), left.getPower()));
            stack.remove(stack.size() - 1);
        }

        return peak;
    }

    private static int computePeakStackV4(int[] a) {
        int n = a.length;
        int i = 0;
        ArrayList<sort.Run> stack = new ArrayList<>();
        int peak = 0;

        while (i < n) {
            int j = sort.extendRun(a, i);
            stack.add(new sort.Run(i, j - i, 0));
            if (stack.size() > peak) {
                peak = stack.size();
            }
            i = j;

            while (i <= n && stack.size() >= 2) {
                sort.Run right = stack.get(stack.size() - 1);
                sort.Run left = stack.get(stack.size() - 2);
                int p = sort.power(left, right, n);
                if (p <= left.getPower()) {
                    stack.set(stack.size() - 2, new sort.Run(left.getStart(), left.getLength() + right.getLength(), left.getPower()));
                    stack.remove(stack.size() - 1);
                } else {
                    break;
                }
            }
        }

        while (stack.size() >= 2) {
            sort.Run right = stack.get(stack.size() - 1);
            sort.Run left = stack.get(stack.size() - 2);
            stack.set(stack.size() - 2, new sort.Run(left.getStart(), left.getLength() + right.getLength(), left.getPower()));
            stack.remove(stack.size() - 1);
        }

        return peak;
    }

    private static class Row {
        private final Algorithm algorithm;
        private final Distribution distribution;
        private final int n;
        private final int rep;
        private final long timeNs;
        private final long mergeCost;
        private final int peakStack;

        private Row(Algorithm algorithm, Distribution distribution, int n, int rep, long timeNs, long mergeCost, int peakStack) {
            this.algorithm = algorithm;
            this.distribution = distribution;
            this.n = n;
            this.rep = rep;
            this.timeNs = timeNs;
            this.mergeCost = mergeCost;
            this.peakStack = peakStack;
        }

        private String toCsvLine() {
            return String.format(
                    "%s,%s,%d,%d,%d,%d,%d%n",
                    algorithm.name().toLowerCase(),
                    distribution.name().toLowerCase(),
                    n,
                    rep,
                    timeNs,
                    mergeCost,
                    peakStack
            );
        }
    }
}

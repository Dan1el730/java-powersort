import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class profile {

    private static final long BASE_SEED = 42L;

    private static class DetailedOverhead {
        long countRunDetections;
        long timeRunDetectTotalNs;
        long countPowerCalls;
        long timePowerTotalNs;
    }

    private static int[] generateArray(int n, String distribution, Random rnd) {
        int[] a = new int[n];
        switch (distribution.toLowerCase()) {
            case "random":
                for (int i = 0; i < n; i++) a[i] = rnd.nextInt();
                return a;
            case "sorted":
                for (int i = 0; i < n; i++) a[i] = i;
                return a;
            case "reverse":
                for (int i = 0; i < n; i++) a[i] = n - i - 1;
                return a;
            case "few_unique":
                for (int i = 0; i < n; i++) a[i] = rnd.nextInt(10);
                return a;
            case "alternating":
                for (int i = 0; i < n / 2; i++) a[i] = i;
                for (int i = n / 2; i < n; i++) a[i] = n - i;
                return a;
            case "adversarial":
                for (int i = 0; i < n; i++) {
                    if ((i / 10) % 2 == 0) a[i] = i;
                    else a[i] = n - i;
                }
                return a;
            default:
                throw new IllegalArgumentException("Unknown distribution: " + distribution);
        }
    }

    private static void assertSorted(int[] a, String context) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                throw new IllegalStateException("Sort check failed for " + context + " at index " + i);
            }
        }
    }

    private static DetailedOverhead profileV0Overhead(int[] a) {
        DetailedOverhead d = new DetailedOverhead();
        int i = 0;
        int runStart = 0;
        int runLength = 1;

        while (i < a.length) {
            long tRun0 = System.nanoTime();
            int j = sort.extendRun(a, i);
            d.timeRunDetectTotalNs += (System.nanoTime() - tRun0);
            d.countRunDetections++;

            int nextRunLength = j - i;
            if (i > runStart) {
                long tPow0 = System.nanoTime();
                sort.power(new sort.Run(runStart, runLength), new sort.Run(i, nextRunLength), a.length);
                d.timePowerTotalNs += (System.nanoTime() - tPow0);
                d.countPowerCalls++;
            }

            runStart = i;
            runLength = nextRunLength;
            i = j;
        }

        return d;
    }

    private static int computePeakStack(int[] a) {
        int n = a.length;
        int i = 0;
        ArrayList<sort.Run> runs = new ArrayList<>();
        int peak = 0;

        while (i < n) {
            int j = sort.extendRun(a, i);
            runs.add(new sort.Run(i, j - i, 0));
            if (runs.size() > peak) peak = runs.size();
            i = j;

            while (i <= n && runs.size() >= 2) {
                sort.Run right = runs.get(runs.size() - 1);
                sort.Run left = runs.get(runs.size() - 2);
                int p = sort.power(left, right, n);
                if (p <= left.getPower()) {
                    runs.set(runs.size() - 2, new sort.Run(left.getStart(), left.getLength() + right.getLength(), left.getPower()));
                    runs.remove(runs.size() - 1);
                } else {
                    break;
                }
            }
        }

        while (runs.size() >= 2) {
            sort.Run right = runs.get(runs.size() - 1);
            sort.Run left = runs.get(runs.size() - 2);
            runs.set(runs.size() - 2, new sort.Run(left.getStart(), left.getLength() + right.getLength(), left.getPower()));
            runs.remove(runs.size() - 1);
        }

        return peak;
    }

    private static sort.Version parseVersion(String token) {
        return sort.Version.valueOf(token.trim().toUpperCase());
    }

    private static void runSingle(sort.Version version, int n, String distribution, int reps) {
        Random rnd = new Random(BASE_SEED);

        int warmupN = Math.min(5000, n);
        for (int i = 0; i < 3; i++) {
            int[] warm = generateArray(warmupN, distribution, rnd);
            sort.powersort(warm, version);
        }

        long[] timesNs = new long[reps];
        long[] mergeCosts = new long[reps];
        int[] peakStacks = new int[reps];
        DetailedOverhead overhead = new DetailedOverhead();

        for (int rep = 0; rep < reps; rep++) {
            int[] arr = generateArray(n, distribution, rnd);
            int[] arrCopyForPeak = Arrays.copyOf(arr, arr.length);

            if (version == sort.Version.V0) {
                DetailedOverhead d = profileV0Overhead(Arrays.copyOf(arr, arr.length));
                overhead.countRunDetections += d.countRunDetections;
                overhead.timeRunDetectTotalNs += d.timeRunDetectTotalNs;
                overhead.countPowerCalls += d.countPowerCalls;
                overhead.timePowerTotalNs += d.timePowerTotalNs;
            }

            long t0 = System.nanoTime();
            long mergeCost = sort.powersort(arr, version);
            long t1 = System.nanoTime();

            assertSorted(arr, version.name());
            timesNs[rep] = t1 - t0;
            mergeCosts[rep] = mergeCost;
            peakStacks[rep] = computePeakStack(arrCopyForPeak);
        }

        long sumTime = 0;
        long sumMerge = 0;
        long sumPeak = 0;
        for (int i = 0; i < reps; i++) {
            sumTime += timesNs[i];
            sumMerge += mergeCosts[i];
            sumPeak += peakStacks[i];
        }

        long[] tSorted = Arrays.copyOf(timesNs, reps);
        Arrays.sort(tSorted);
        long median = tSorted[reps / 2];

        System.out.println("============================================================");
        System.out.println("profile -> sort pair: " + version.name().toLowerCase() + " -> " + version.name().toLowerCase());
        System.out.println("n=" + n + ", distribution=" + distribution + ", reps=" + reps);
        System.out.println("mean_time_ms=" + String.format("%.4f", (sumTime / (double) reps) / 1_000_000.0));
        System.out.println("median_time_ms=" + String.format("%.4f", median / 1_000_000.0));
        System.out.println("mean_merge_cost=" + String.format("%.2f", sumMerge / (double) reps));
        System.out.println("mean_peak_stack=" + String.format("%.2f", sumPeak / (double) reps));

        if (version == sort.Version.V0) {
            double runMs = overhead.timeRunDetectTotalNs / 1_000_000.0;
            double powerMs = overhead.timePowerTotalNs / 1_000_000.0;
            double total = runMs + powerMs;
            double powerFrac = total > 0 ? (100.0 * powerMs / total) : 0.0;
            System.out.println("--- detailed_overhead_v0 ---");
            System.out.println("run_detection_calls=" + overhead.countRunDetections);
            System.out.println("power_calls=" + overhead.countPowerCalls);
            System.out.println("run_detection_ms=" + String.format("%.4f", runMs));
            System.out.println("power_ms=" + String.format("%.4f", powerMs));
            System.out.println("power_fraction_pct=" + String.format("%.2f", powerFrac));
        }
        System.out.println("============================================================");
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java profile <version|all> <array_size> <distribution> <repetitions>");
            System.out.println("Versions: " + sort.versions());
            System.out.println("Distributions: random, sorted, reverse, few_unique, alternating, adversarial");
            System.exit(1);
        }

        String versionArg = args[0].trim().toLowerCase();
        int n = Integer.parseInt(args[1]);
        String distribution = args[2].trim().toLowerCase();
        int reps = Integer.parseInt(args[3]);

        if ("all".equals(versionArg)) {
            for (sort.Version version : sort.Version.values()) {
                runSingle(version, n, distribution, reps);
            }
        } else {
            runSingle(parseVersion(versionArg), n, distribution, reps);
        }
    }
}

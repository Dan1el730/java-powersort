import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * PilotBenchmark: Configurable pilot benchmark for Powersort tuning variants.
 * 
 * Executes a focused pilot study across representative sizes, distributions, 
 * and algorithms to validate correctness and measure baseline performance 
 * before large-scale campaigning.
 * 
 * Usage:
 *   javac Powersort.java PilotBenchmark.java
 *   java PilotBenchmark <output_csv>
 * 
 * Example:
 *   java PilotBenchmark pilot_results.csv
 */
public class PilotBenchmark {

    // Pilot configuration
    private static final int[] PILOT_SIZES = {10_000, 100_000, 1_000_000};
    private static final String[] DISTRIBUTIONS = {
        "random", "sorted", "alternating", "many_small_runs", "adversarial"
    };
    private static final int REPS_PER_CONFIG = 10;
    private static final int WARMUP_ITERATIONS = 5;

    /**
     * Generates test array by distribution type
     */
    public static int[] generateArray(int n, String distribution, Random rnd) {
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
                for (int i = 0; i < n; i++) a[i] = rnd.nextInt(100);
                return a;
                
            case "alternating":
                // Alternating ascending/descending runs of length ~sqrt(n)
                int runLen = (int) Math.sqrt(n);
                for (int i = 0; i < n; i++) {
                    int blockIdx = i / runLen;
                    int posInBlock = i % runLen;
                    if (blockIdx % 2 == 0) {
                        a[i] = n - i;
                    } else {
                        a[i] = i;
                    }
                }
                return a;
                
            case "many_small_runs":
                // Many runs of length 2-3
                int pos = 0;
                while (pos < n) {
                    int runSize = Math.min(3, n - pos);
                    for (int i = 0; i < runSize && pos < n; i++) {
                        a[pos++] = rnd.nextInt();
                    }
                    // Insertion sort this small run
                    Arrays.sort(a, pos - runSize, pos);
                }
                return a;
                
            case "adversarial":
                // Worst-case pattern: many small runs interspersed
                for (int i = 0; i < n; i++) {
                    if ((i / 10) % 2 == 0) {
                        a[i] = i;
                    } else {
                        a[i] = n - i;
                    }
                }
                return a;
                
            default:
                throw new IllegalArgumentException("Unknown distribution: " + distribution);
        }
    }

    /**
     * Verify sorting correctness
     */
    public static boolean verifySorted(int[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) return false;
        }
        return true;
    }

    /**
     * Simple Timsort baseline (Java's native sort for comparison)
     */
    public static void timsort(int[] a) {
        Arrays.sort(a);
    }

    /**
     * Run one pilot scenario
     */
    public static BenchmarkResult runScenario(int size, String distribution, int reps) {
        Random rnd = new Random(12345);
        
        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            int[] tmp = generateArray(Math.min(10000, size), distribution, rnd);
            sort.powersort(tmp.clone(), sort.Version.V0);
            timsort(tmp.clone());
        }

        // Measure Powersort
        long[] powersortTimes = new long[reps];
        long powerMergeCostTotal = 0;

        for (int rep = 0; rep < reps; rep++) {
            int[] a = generateArray(size, distribution, rnd);

            long t0 = System.nanoTime();
            long mergeCost = sort.powersort(a, sort.Version.V0);
            long t1 = System.nanoTime();

            powersortTimes[rep] = t1 - t0;
            powerMergeCostTotal += mergeCost;

            // Verify correctness
            if (!verifySorted(a)) {
                throw new RuntimeException("Powersort produced incorrect output!");
            }
        }

        // Measure Timsort
        long[] timsortTimes = new long[reps];
        for (int rep = 0; rep < reps; rep++) {
            int[] a = generateArray(size, distribution, rnd);

            long t0 = System.nanoTime();
            timsort(a);
            long t1 = System.nanoTime();

            timsortTimes[rep] = t1 - t0;

            // Verify correctness
            if (!verifySorted(a)) {
                throw new RuntimeException("Timsort produced incorrect output!");
            }
        }

        // Compute statistics (median, mean)
        Arrays.sort(powersortTimes);
        Arrays.sort(timsortTimes);

        long powersortMedian = powersortTimes[reps / 2];
        long timsortMedian = timsortTimes[reps / 2];

        long powersortMean = 0, timsortMean = 0;
        for (int i = 0; i < reps; i++) {
            powersortMean += powersortTimes[i];
            timsortMean += timsortTimes[i];
        }
        powersortMean /= reps;
        timsortMean /= reps;

        return new BenchmarkResult(
            size, distribution,
            powersortMedian, powersortMean, powerMergeCostTotal / reps,
            timsortMedian, timsortMean
        );
    }

    /**
     * Benchmark result
     */
    public static class BenchmarkResult {
        public int size;
        public String distribution;
        public long powersortMedianNs, powersortMeanNs, powersortMergeCost;
        public long timsortMedianNs, timsortMeanNs;
        public double speedupFactor;

        public BenchmarkResult(int size, String dist, long psMedian, long psMean, long psMergeCost, long tsMedian, long tsMean) {
            this.size = size;
            this.distribution = dist;
            this.powersortMedianNs = psMedian;
            this.powersortMeanNs = psMean;
            this.powersortMergeCost = psMergeCost;
            this.timsortMedianNs = tsMedian;
            this.timsortMeanNs = tsMean;
            this.speedupFactor = (double) psMedian / tsMedian;
        }

        public String toCSV() {
            return String.format("%d,%s,%d,%d,%d,%d,%d,%.2f",
                size, distribution,
                powersortMedianNs, powersortMeanNs, powersortMergeCost,
                timsortMedianNs, timsortMeanNs, speedupFactor);
        }
    }

    public static void main(String[] args) throws IOException {
        String outputFile = (args.length > 0) ? args[0] : "pilot_results.csv";

        System.out.println("=== Pilot Benchmark (V0 Baseline) ===");
        System.out.println("Output: " + outputFile);
        System.out.println("Sizes: " + PILOT_SIZES.length + " levels");
        System.out.println("Distributions: " + DISTRIBUTIONS.length + " types");
        System.out.println("Reps per config: " + REPS_PER_CONFIG);
        System.out.println();

        FileWriter fw = new FileWriter(outputFile);
        fw.write("size,distribution,powersort_median_ns,powersort_mean_ns,powersort_merge_cost,timsort_median_ns,timsort_mean_ns,speedup_factor\n");

        int totalRuns = PILOT_SIZES.length * DISTRIBUTIONS.length;
        int currentRun = 0;

        for (int size : PILOT_SIZES) {
            for (String dist : DISTRIBUTIONS) {
                currentRun++;
                System.out.printf("[%d/%d] Testing size=%d, dist=%s... ", currentRun, totalRuns, size, dist);
                System.out.flush();

                try {
                    BenchmarkResult result = runScenario(size, dist, REPS_PER_CONFIG);
                    fw.write(result.toCSV() + "\n");
                    fw.flush();

                    System.out.printf("OK (%.2f× Timsort)\n", result.speedupFactor);
                } catch (Exception e) {
                    System.out.printf("FAILED: %s\n", e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        fw.close();
        System.out.println("\nPilot benchmark complete. Results saved to: " + outputFile);
    }
}

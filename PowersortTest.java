import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * PowersortTest: correctness tests and benchmarks comparing an educational
 * Timsort implementation against the provided `Powersort` class.
 *
 * Outputs a CSV `results.csv` to the repository root.
 */
public class PowersortTest {

    // Results CSV header
    private static final String CSV_HEADER = "distribution,n,pattern,mergeCostTimsort,mergeCostPowersort,timeTimsortNs,timePowersortNs,stackHeightTimsort,stackHeightPowersort";

    // --- Helper: fill with ascending runs high-to-low like the Python notebook ---
    public static void fillWithAscRunsHighToLow(int[] A, int[] runLengths, int runLenFactor) {
        int n = A.length;
        int sum = 0;
        for (int r : runLengths) sum += r;
        if (sum * runLenFactor != n) throw new IllegalArgumentException("run lengths don't sum to array length");
        for (int i = 0; i < n; i++) A[i] = n - i;
        int idx = 0;
        for (int r : runLengths) {
            int L = r * runLenFactor;
            Arrays.sort(A, idx, idx + L);
            idx += L;
        }
    }

    public static void fillWithAscRunsHighToLow(int[] A, int[] runLengths) { fillWithAscRunsHighToLow(A, runLengths, 1); }

    // Random array generator
    public static int[] randomArray(int n, Random rnd) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = rnd.nextInt();
        return a;
    }

    // Already sorted
    public static int[] sortedArray(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    // --- Minimal educational Timsort implementation used for comparisons ---
    public static class TimsortEducational {
        public long mergeCost = 0;
        public int peakStack = 0;

        private static int extendRun(int[] a, int i) {
            if (i >= a.length - 1) return i + 1;
            int j = i + 1;
            while (j < a.length && a[j - 1] <= a[j]) j++;
            return j;
        }

        private void mergeInplace(int[] a, int i, int m, int j) {
            int outLen = j - i;
            int[] tmp = new int[outLen];
            int p = i, q = m, t = 0;
            while (p < m && q < j) {
                if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
            }
            while (p < m) tmp[t++] = a[p++];
            while (q < j) tmp[t++] = a[q++];
            System.arraycopy(tmp, 0, a, i, outLen);
            mergeCost += outLen;
        }

        private boolean ruleA(Integer W, Integer X, Integer Y, Integer Z) {
            if (X == null) return false; return Z > X;
        }
        private boolean ruleB(Integer W, Integer X, Integer Y, Integer Z) {
            if (Y == null) return false; return Z >= Y;
        }
        private boolean ruleC(Integer W, Integer X, Integer Y, Integer Z) {
            if (X == null) return false; return Y + Z >= X;
        }
        private boolean ruleD(Integer W, Integer X, Integer Y, Integer Z) {
            if (W == null) return false; return X + Y >= W;
        }

        public void sort(int[] a) {
            mergeCost = 0; peakStack = 0;
            int i = 0; ArrayList<int[]> runs = new ArrayList<>();
            while (i < a.length) {
                int j = extendRun(a, i);
                runs.add(new int[]{i, j - i});
                i = j;
                if (runs.size() > peakStack) peakStack = runs.size();
                while (runs.size() >= 2) {
                    int[] Z = runs.get(runs.size() - 1);
                    int[] Y = runs.size() >= 2 ? runs.get(runs.size() - 2) : null;
                    int[] X = runs.size() >= 3 ? runs.get(runs.size() - 3) : null;
                    int[] W = runs.size() >= 4 ? runs.get(runs.size() - 4) : null;
                    Integer w = W==null?null:W[1];
                    Integer x = X==null?null:X[1];
                    Integer y = Y==null?null:Y[1];
                    Integer z = Z==null?null:Z[1];
                    if (ruleA(w,x,y,z)) {
                        // merge X and Y
                        int Xstart = X[0], Xlen = X[1];
                        int Ystart = Y[0], Ylen = Y[1];
                        mergeInplace(a, Xstart, Ystart, Ystart + Ylen);
                        runs.set(runs.size() - 3, new int[]{Xstart, Xlen + Ylen});
                        runs.remove(runs.size() - 2);
                    } else if (ruleB(w,x,y,z) || ruleC(w,x,y,z) || ruleD(w,x,y,z)) {
                        // merge Y and Z
                        int Ystart = Y[0], Ylen = Y[1];
                        int Zstart = Z[0], Zlen = Z[1];
                        mergeInplace(a, Ystart, Zstart, Zstart + Zlen);
                        runs.set(runs.size() - 2, new int[]{Ystart, Ylen + Zlen});
                        runs.remove(runs.size() - 1);
                    } else break;
                }
            }
            while (runs.size() >= 2) {
                int[] Y = runs.get(runs.size() - 2);
                int[] Z = runs.get(runs.size() - 1);
                mergeInplace(a, Y[0], Z[0], Z[0] + Z[1]);
                runs.set(runs.size() - 2, new int[]{Y[0], Y[1] + Z[1]});
                runs.remove(runs.size() - 1);
            }
        }
    }

    // Compute powersort peak stack height without mutating array: replicate push/pop logic
    public static int computePowersortPeakStack(int[] a, boolean useFourWay, int minRunLength) {
        int n = a.length; int i = 0; ArrayList<Powersort.Run> runs = new ArrayList<>();
        int peak = 0;
        while (i < n) {
            int j = Powersort.extendRun(a, i);
            if (minRunLength > 1) j = Math.min(n, Math.max(j, i + minRunLength));
            runs.add(new Powersort.Run(i, j - i, 0));
            if (runs.size() > peak) peak = runs.size();
            i = j;
            while (i <= n && runs.size() >= 2) {
                Powersort.Run last = runs.get(runs.size()-1);
                int p = Powersort.power(runs.get(runs.size()-2), last, n);
                if (p <= runs.get(runs.size()-2).getPower()) {
                    // merge topmost 2 (simulate)
                    Powersort.Run Y = runs.get(runs.size()-2);
                    Powersort.Run Z = runs.get(runs.size()-1);
                    runs.set(runs.size()-2, new Powersort.Run(Y.getStart(), Y.getLength()+Z.getLength(), Y.getPower()));
                    runs.remove(runs.size()-1);
                } else break;
            }
        }
        while (runs.size() >= 2) {
            Powersort.Run Y = runs.get(runs.size()-2);
            Powersort.Run Z = runs.get(runs.size()-1);
            runs.set(runs.size()-2, new Powersort.Run(Y.getStart(), Y.getLength()+Z.getLength(), Y.getPower()));
            runs.remove(runs.size()-1);
        }
        return peak;
    }

    // Run one comparison and append CSV line
    public static String runComparison(String distribution, int[] base, String patternDesc) {
        // prepare copies
        int[] a1 = Arrays.copyOf(base, base.length);
        int[] a2 = Arrays.copyOf(base, base.length);

        // Warmup
        TimsortEducational tsEdu = new TimsortEducational();
        tsEdu.sort(Arrays.copyOf(base, base.length));
        Powersort.powersort(Arrays.copyOf(base, base.length));

        // Timsort run
        tsEdu = new TimsortEducational();
        long t0 = System.nanoTime();
        tsEdu.sort(a1);
        long t1 = System.nanoTime();

        long timeT = t1 - t0;
        long mergeCostT = tsEdu.mergeCost;
        int peakT = tsEdu.peakStack;

        // Powersort run
        Powersort.MERGE_COST = 0;
        long p0 = System.nanoTime();
        Powersort.powersort(a2);
        long p1 = System.nanoTime();
        long timeP = p1 - p0;
        long mergeCostP = Powersort.MERGE_COST;
        int peakP = computePowersortPeakStack(base, false, 1);

        return String.format("%s,%d,%s,%d,%d,%d,%d,%d,%d",
                distribution, base.length, patternDesc.replace(',', ';'),
                mergeCostT, mergeCostP, timeT, timeP, peakT, peakP);
    }

    public static void main(String[] args) throws IOException {
        FileWriter fw = new FileWriter("results.csv");
        fw.append(CSV_HEADER).append('\n');
        Random rnd = new Random(12345);

        // 1) Random arrays (correctness + benchmark)
        for (int n : new int[]{1000, 5000, 20000}) {
            for (int rep = 0; rep < 3; rep++) {
                int[] a = randomArray(n, rnd);
                fw.append(runComparison("random", a, "random")).append('\n');
            }
        }

        // 2) Already sorted arrays (rho -> 1)
        for (int n : new int[]{1000, 5000, 20000}) {
            int[] a = sortedArray(n);
            fw.append(runComparison("sorted", a, "sorted")).append('\n');
        }

        // 3) Exact example from notebook: runs = [5,3,3,14,1,2]
        int[] runs = new int[]{5,3,3,14,1,2};
        int sum = 0; for (int r : runs) sum += r;
        int[] aExample = new int[sum];
        fillWithAscRunsHighToLow(aExample, runs);
        fw.append(runComparison("example", aExample, "[5,3,3,14,1,2]")).append('\n');

        // 4) Adversarial-style patterns: repeat small runs, alternating long/short etc.
        int[] pattern1 = new int[1024]; // many short runs of length 2
        int[] runLens1 = new int[512]; Arrays.fill(runLens1, 2);
        fillWithAscRunsHighToLow(pattern1, runLens1);
        fw.append(runComparison("adversarial_many_small", pattern1, "many_small_runs_len2")).append('\n');

        int[] pattern2 = new int[4096]; // alternating long/short runs
        int[] runLens2 = new int[256];
        for (int i = 0; i < runLens2.length; i++) runLens2[i] = (i % 2 == 0) ? 1 : 15;
        // runLens2 sums to 2048; use factor 2 so 2048*2 == 4096
        fillWithAscRunsHighToLow(pattern2, runLens2, 2);
        fw.append(runComparison("adversarial_alt", pattern2, "alt_1_15_scaled16")).append('\n');

        fw.flush(); fw.close();
        System.out.println("Wrote results.csv");
    }
}

import java.util.ArrayList;

/**
 * Powersort: Java translation (educational) of the Python implementation in
 * Timsort_Powersort.ipynb (see attachment).
 *
 * Implements:
 * - extendRun(): similar to Python's extend_run_increasing_only
 * - power(): computes the run-boundary power (uses bit operations like power_fast)
 * - mergeTopmost2(): merge the two topmost runs on the stack
 * - powersort(): main sorting method with stack-based power invariant
 *
 * Comments in methods reference the original Python names from the notebook.
 */
public class Powersort {

    public static long MERGE_COST = 0;
    // Default gallop threshold
    private static final int DEFAULT_GALLOP = 7;

    private final boolean useGalloping;
    private final boolean useFourWay;
    private final int minRunLength;
    private int[] tmpBuffer; // reused buffer for merges
    private final int gallopThreshold;

    public static class Run {
        private int start; // starting index
        private int length;   // length of run
        private int power; // stored power for boundary

        public Run(int start, int length) {
            this.start = start;
            this.length = length;
            this.power = 0;
        }

        public Run(int start, int length, int power) {
            this.start = start;
            this.length = length;
            this.power = power;
        }

        public int getStart() { return start; }
        public int getLength() { return length; }
        public int getPower() { return power; }
        public void setPower(int p) { this.power = p; }
        public void setLength(int l) { this.length = l; }
    }

    // extendRun: Java equivalent of extend_run_increasing_only(a, i)
    // returns j (one-past-the-end) of maximal ascending run starting at i
    public static int extendRun(int[] a, int i) {
        if (i >= a.length - 1) return i + 1;
        int j = i + 1;
        while (j < a.length && a[j - 1] <= a[j]) {
            j++;
        }
        return j;
    }

    // ------------------------------------------------------------------
    // power implementations
    // ------------------------------------------------------------------
    // Two versions are provided:
    // 1) powerReference(): a clear, easy-to-read reference translation
    //    of the Python `power` function (uses floating point / floor).
    // 2) powerFast(): an optimized integer-only implementation following
    //    the CPython derivation and the math in Section 2.3.2 of the
    //    interim report (uses integer arithmetic and bit operations).
    //
    // The public `power()` method below calls the optimized version by
    // default. Comments reference Equation 2.2 and Section 2.3.2.
    // See the notebook `Timsort_Powersort.ipynb` for the original Python
    // implementations `power` and `power_fast`.

    /**
     * Reference implementation matching the Python `power` function.
     * This directly follows the definition using fractional midpoints
     * and tests floor(a*2^l) == floor(b*2^l).
     *
     * This is simple and clear but uses floating-point arithmetic.
     */
    public static int powerReference(Run run1, Run run2, int n) {
        double a = (run1.getStart() + run1.getLength() / 2.0) / (double) n; // (i1 + n1/2)/n
        double b = (run2.getStart() + run2.getLength() / 2.0) / (double) n; // (i2 + n2/2)/n
        int l = 0;
        while (Math.floor(a * Math.pow(2, l)) == Math.floor(b * Math.pow(2, l))) {
            l += 1;
        }
        return l;
    }

    /**
     * Optimized integer implementation following the CPython `power_fast`
     * approach and the math in Section 2.3.2 (Equation 2.2) of the
     * interim report. This avoids floating point by working with scaled
     * integer midpoints and repeated doubling (bit shifts).
     *
     * Notes:
     * - We compute a' = 2*i1 + n1  (== 2*(i1 + n1/2)) and b' = a' + n1 + n2
     *   which are integer numerators proportional to 2*(midpoints).
     * - The loop mirrors the Python `power_fast` behavior: increment p
     *   until b' falls into the next integer cell when scaled by 2^p.
     * - Handles the a' >= n rotation case exactly as the Python code.
     */
    public static int powerFast(Run run1, Run run2, int n) {
        long i1 = run1.getStart();
        long n1 = run1.getLength();
        long i2 = run2.getStart();
        long n2 = run2.getLength();
        long a = 2L * i1 + n1;           // 2 * (i1 + n1/2)
        long b = a + n1 + n2;            // 2 * (i2 + n2/2)
        int l = 0;
        while (true) {
            l += 1;
            if (a >= n) {
                // rotate a,b down by n as in Python implementation
                if (!(b >= a)) throw new AssertionError("Invariant violated: b < a when a>=n");
                a -= n;
                b -= n;
            } else if (b >= n) {
                break;
            }
            // At this point we expect 0 <= a < b < n
            if (!(a < b && b < n)) {
                // safety: break if something unexpected happens
                break;
            }
            a <<= 1; // multiply by 2
            b <<= 1;
            // note: we intentionally use long arithmetic to avoid overflow
        }
        return l;
    }

    /**
     * Public convenience method: by default uses the optimized integer
     * implementation. If you want the reference implementation, call
     * `powerReference` directly.
     */
    public static int power(Run run1, Run run2, int n) {
        return powerFast(run1, run2, n);
    }

    // Instance constructor with options
    public Powersort(boolean useGalloping, boolean useFourWay, int minRunLength) {
        this.useGalloping = useGalloping;
        this.useFourWay = useFourWay;
        this.minRunLength = Math.max(1, minRunLength);
        this.tmpBuffer = new int[0];
        this.gallopThreshold = DEFAULT_GALLOP;
    }

    // mergeTopmost2: merge the two topmost runs on the stack (reuses tmpBuffer)
    public void mergeTopmost2(int[] a, ArrayList<Run> runs) {
        if (runs.size() < 2) throw new IllegalStateException("need >=2 runs to merge");
        Run Y = runs.get(runs.size() - 2);
        Run Z = runs.get(runs.size() - 1);
        if (Z.getStart() != Y.getStart() + Y.getLength()) throw new AssertionError("runs not adjacent");
        int i = Y.getStart();
        int m = Z.getStart();
        int j = Z.getStart() + Z.getLength();
        int outLen = (m - i) + (j - m);
        if (tmpBuffer.length < outLen) tmpBuffer = new int[outLen];
        int[] tmp = tmpBuffer;
        int p = i, q = m, t = 0;
        int winA = 0, winB = 0;
        while (p < m && q < j) {
            if (!useGalloping) {
                if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
            } else {
                // galloping heuristic
                if (a[p] < a[q]) {
                    tmp[t++] = a[p++];
                    winA++; winB = 0;
                    if (winA >= gallopThreshold) {
                        int k = gallopRight(a, p, m, a[q]);
                        int len = k - p;
                        System.arraycopy(a, p, tmp, t, len);
                        t += len; p = k; winA = 0;
                    }
                } else {
                    tmp[t++] = a[q++];
                    winB++; winA = 0;
                    if (winB >= gallopThreshold) {
                        int k = gallopLeft(a, q, j, a[p]);
                        int len = k - q;
                        System.arraycopy(a, q, tmp, t, len);
                        t += len; q = k; winB = 0;
                    }
                }
            }
        }
        while (p < m) tmp[t++] = a[p++];
        while (q < j) tmp[t++] = a[q++];
        // copy merged back into array
        System.arraycopy(tmp, 0, a, i, outLen);
        MERGE_COST += outLen;
        // debug print like Python: Merge(i, m, j)
        System.out.printf("Merge(%d, %d, %d)%n", i, m, j);
        // update runs: replace Y with merged run and pop Z
        runs.set(runs.size() - 2, new Run(Y.getStart(), Y.getLength() + Z.getLength(), Y.getPower()));
        runs.remove(runs.size() - 1);
    }

    // Simple exponential+binary search: find first index in [lo,hi) with a[idx] >= key
    private int gallopRight(int[] a, int lo, int hi, int key) {
        int len = hi - lo;
        if (len <= 0) return lo;
        int step = 1; int idx = lo;
        while (idx < hi && a[idx] < key) {
            idx = lo + step;
            step <<= 1;
        }
        int left = Math.max(lo, lo + (step >> 1));
        int right = Math.min(hi, idx);
        // binary search in [left,right)
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (a[mid] < key) left = mid + 1; else right = mid;
        }
        return left;
    }

    // find first index in [lo,hi) with a[idx] > key (used symmetrically)
    private int gallopLeft(int[] a, int lo, int hi, int key) {
        int step = 1; int idx = lo;
        while (idx < hi && a[idx] <= key) {
            idx = lo + step;
            step <<= 1;
        }
        int left = Math.max(lo, lo + (step >> 1));
        int right = Math.min(hi, idx);
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (a[mid] <= key) left = mid + 1; else right = mid;
        }
        return left;
    }

    // 4-way merge of topmost 4 runs into one merged run
    public void mergeTopmost4(int[] a, ArrayList<Run> runs) {
        if (runs.size() < 4) throw new IllegalStateException("need >=4 runs to 4-way merge");
        Run A = runs.get(runs.size() - 4);
        Run B = runs.get(runs.size() - 3);
        Run C = runs.get(runs.size() - 2);
        Run D = runs.get(runs.size() - 1);
        int i = A.getStart();
        int m1 = B.getStart();
        int m2 = C.getStart();
        int m3 = D.getStart();
        int j = D.getStart() + D.getLength();
        int outLen = j - i;
        if (tmpBuffer.length < outLen) tmpBuffer = new int[outLen];
        int[] tmp = tmpBuffer;
        int ia = i, ib = m1, ic = m2, id = m3, t = 0;
        while ((ia < m1) || (ib < m2) || (ic < m3) || (id < j)) {
            int minVal = Integer.MAX_VALUE;
            int which = -1;
            if (ia < m1 && a[ia] < minVal) { minVal = a[ia]; which = 0; }
            if (ib < m2 && a[ib] < minVal) { minVal = a[ib]; which = 1; }
            if (ic < m3 && a[ic] < minVal) { minVal = a[ic]; which = 2; }
            if (id < j  && a[id] < minVal) { minVal = a[id]; which = 3; }
            if (which == 0) tmp[t++] = a[ia++];
            else if (which == 1) tmp[t++] = a[ib++];
            else if (which == 2) tmp[t++] = a[ic++];
            else if (which == 3) tmp[t++] = a[id++];
            else break;
        }
        System.arraycopy(tmp, 0, a, i, outLen);
        MERGE_COST += outLen;
        System.out.printf("Merge4(%d, %d)%n", i, j);
        // replace A with merged run and pop B,C,D
        runs.set(runs.size() - 4, new Run(A.getStart(), outLen, A.getPower()));
        runs.remove(runs.size() - 1);
        runs.remove(runs.size() - 1);
        runs.remove(runs.size() - 1);
    }

    // instance sort with options: enforces minrun, reuses tmp buffer, optionally uses galloping and 4-way merge
    public void sort(int[] a) {
        int n = a.length;
        int i = 0;
        ArrayList<Run> runs = new ArrayList<>();
        int j = extendRun(a, i);
        j = enforceMinRun(a, i, j, n);
        runs.add(new Run(i, j - i, 0));
        i = j;
        while (i < n) {
            j = extendRun(a, i);
            j = enforceMinRun(a, i, j, n);
            Run right = new Run(i, j - i);
            int p = power(runs.get(runs.size() - 1), right, n);
            while (p <= runs.get(runs.size() - 1).getPower()) {
                if (useFourWay && runs.size() >= 4) mergeTopmost4(a, runs); else mergeTopmost2(a, runs);
            }
            runs.add(new Run(i, j - i, p));
            i = j;
        }
        while (runs.size() >= 2) {
            if (useFourWay && runs.size() >= 4) mergeTopmost4(a, runs); else mergeTopmost2(a, runs);
        }
    }

    // static convenience wrapper with default options (no galloping, no 4-way, minRun=1)
    public static void powersort(int[] a) {
        new Powersort(false, false, 1).sort(a);
    }

    // Ensure runs have at least minRunLength by extending with binary insertion sort
    private int enforceMinRun(int[] a, int start, int j, int n) {
        if (minRunLength <= 1) return j;
        int runLen = j - start;
        if (runLen >= minRunLength) return j;
        int want = Math.min(minRunLength, n - start);
        // perform binary insertion sort to extend sorted run to [start, start+want)
        for (int k = j; k < start + want; k++) {
            int key = a[k];
            // binary search in [start, k)
            int lo = start, hi = k;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (a[mid] <= key) lo = mid + 1; else hi = mid;
            }
            // shift elements right and insert key at lo
            int t = k;
            while (t > lo) { a[t] = a[t - 1]; t--; }
            a[lo] = key;
        }
        return start + Math.min(want, n - start);
    }

    // Basic demonstration / smoke test
    public static void main(String[] args) {
        int[] a = new int[]{9,8,7,6,5,4,3,2,1,0, 0,1,2,3,4,5,6,7,8,9};
        MERGE_COST = 0;
        System.out.println("Before:");
        for (int v : a) System.out.print(v + " ");
        System.out.println();
        // create an instance with galloping and 4-way enabled and minRunLength=32
        Powersort sorter = new Powersort(true, true, 32);
        sorter.sort(a);
        System.out.println("After:");
        for (int v : a) System.out.print(v + " ");
        System.out.println();
        System.out.println("Merge cost: " + MERGE_COST);
    }
}

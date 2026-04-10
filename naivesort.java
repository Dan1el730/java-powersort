/**
 * Powersort: Java translation (educational) of the Python implementation in
 * Timsort_Powersort.ipynb (see attachment).
 *
 * Usage:
 *   javac naivesort.java
 *
 * This file is kept as a reference/educational algorithm variant.
 *
 * Implements:
 * - extendRun(): similar to Python's extend_run_increasing_only
 * - power(): computes the run-boundary power (uses bit operations like power_fast)
 * - mergeTopmost2(): merge the two topmost runs on the stack
 * - powersort(): main sorting method with stack-based power invariant
 *
 * Comments in methods reference the original Python names from the notebook.
 */
public class naivesort {

    public static long MERGE_COST = 0;
    // Default gallop threshold
    private static final int DEFAULT_GALLOP = 7;

    private final boolean useGalloping;
    private final boolean useFourWay;
    private final int minRunLength;
    private int[] tmpBuffer; // reused buffer for merges (pre-allocated for V2)
    private Run[] runStack; // pre-allocated run stack for V2 (max 32 runs)
    private int runCount; // current number of runs on stack
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

    // Instance constructor with options (V3: disable galloping, pre-allocate buffers)
    public naivesort(boolean useGalloping, boolean useFourWay, int minRunLength) {
        this.useGalloping = false; // V3: Force off for performance testing (galloping adds complexity)
        this.useFourWay = useFourWay;
        this.minRunLength = Math.max(1, minRunLength);
        // V2: Pre-allocate tmpBuffer (16KB for typical merge, grows with 1.5x if needed)
        this.tmpBuffer = new int[16384];
        // V2: Pre-allocate run stack (max ~32 runs for 2^32 elements)
        this.runStack = new Run[32];
        this.runCount = 0;
        this.gallopThreshold = DEFAULT_GALLOP;
    }

    // mergeTopmost2: merge the two topmost runs on the stack (V2: uses pre-allocated runStack)
    private void mergeTopmost2(int[] a) {
        if (runCount < 2) throw new IllegalStateException("need >=2 runs to merge");
        Run Y = runStack[runCount - 2];
        Run Z = runStack[runCount - 1];
        if (Z.getStart() != Y.getStart() + Y.getLength()) throw new AssertionError("runs not adjacent");
        int i = Y.getStart();
        int m = Z.getStart();
        int j = Z.getStart() + Z.getLength();
        int outLen = (m - i) + (j - m);
        // V2: Grow tmpBuffer with 1.5x factor if needed (not just exact fit)
        if (tmpBuffer.length < outLen) tmpBuffer = new int[(int)(outLen * 1.5)];
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
        // Optimize leftover copying: bulk copy the remaining larger segment
        if (p < m) {
            int remain = m - p;
            System.arraycopy(a, p, tmp, t, remain);
            t += remain;
        } else {
            int remain = j - q;
            System.arraycopy(a, q, tmp, t, remain);
            t += remain;
        }
        // copy merged back into array
        System.arraycopy(tmp, 0, a, i, outLen);
        MERGE_COST += outLen;
        // update runs: replace Y with merged run and pop Z
        runStack[runCount - 2] = new Run(Y.getStart(), Y.getLength() + Z.getLength(), Y.getPower());
        runCount--;
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

    // 4-way merge of topmost 4 runs into one merged run (V2: uses pre-allocated runStack)
    private void mergeTopmost4(int[] a) {
        if (runCount < 4) throw new IllegalStateException("need >=4 runs to 4-way merge");
        Run A = runStack[runCount - 4];
        Run B = runStack[runCount - 3];
        Run C = runStack[runCount - 2];
        Run D = runStack[runCount - 1];
        int i = A.getStart();
        int m1 = B.getStart();
        int m2 = C.getStart();
        int m3 = D.getStart();
        int j = D.getStart() + D.getLength();
        int outLen = j - i;
        // V2: Grow tmpBuffer with 1.5x factor if needed
        if (tmpBuffer.length < outLen) tmpBuffer = new int[(int)(outLen * 1.5)];
        int[] tmp = tmpBuffer;
        int ia = i, ib = m1, ic = m2, id = m3, t = 0;
        // Cache bounds in local variables to reduce repeated comparisons
        int m1End = m1, m2End = m2, m3End = m3, jEnd = j;
        while ((ia < m1End) || (ib < m2End) || (ic < m3End) || (id < jEnd)) {
            int minVal = Integer.MAX_VALUE;
            int which = -1;
            if (ia < m1End && a[ia] < minVal) { minVal = a[ia]; which = 0; }
            if (ib < m2End && a[ib] < minVal) { minVal = a[ib]; which = 1; }
            if (ic < m3End && a[ic] < minVal) { minVal = a[ic]; which = 2; }
            if (id < jEnd  && a[id] < minVal) { minVal = a[id]; which = 3; }
            if (which == 0) tmp[t++] = a[ia++];
            else if (which == 1) tmp[t++] = a[ib++];
            else if (which == 2) tmp[t++] = a[ic++];
            else if (which == 3) tmp[t++] = a[id++];
            else break;
        }
        System.arraycopy(tmp, 0, a, i, outLen);
        MERGE_COST += outLen;
        // replace A with merged run and pop B,C,D
        runStack[runCount - 4] = new Run(A.getStart(), outLen, A.getPower());
        runCount -= 3;
    }

    // instance sort with options (V3: inline fast-path power computation)
    public void sort(int[] a) {
        int n = a.length;
        int i = 0;
        runCount = 0; // Reset run stack counter
        int j = extendRun(a, i);
        j = enforceMinRun(a, i, j, n);
        if (runCount >= runStack.length) throw new RuntimeException("Run stack overflow");
        runStack[runCount++] = new Run(i, j - i, 0);
        i = j;
        while (i < n) {
            j = extendRun(a, i);
            j = enforceMinRun(a, i, j, n);
            Run right = new Run(i, j - i);
            Run left = runStack[runCount - 1];
            int p;
            // V3: Inline fast-path power computation for common case (small runs)
            long leftLen = left.getLength();
            long rightLen = right.getLength();
            if (leftLen + rightLen < n / 2) {
                // Fast inline path: most runs are small, compute power inline
                long i1 = left.getStart();
                long n1 = leftLen;
                long i2 = right.getStart();
                long n2 = rightLen;
                long a_val = 2L * i1 + n1;
                long b_val = a_val + n1 + n2;
                p = 0;
                while (true) {
                    p++;
                    if (a_val >= n) { a_val -= n; b_val -= n; }
                    else if (b_val >= n) break;
                    if (!(a_val < b_val && b_val < n)) break;
                    a_val <<= 1; b_val <<= 1;
                }
            } else {
                // Fallback to normal power computation for large runs
                p = power(left, right, n);
            }
            while (p <= left.getPower()) {
                if (useFourWay && runCount >= 4) mergeTopmost4(a); else mergeTopmost2(a);
                left = runStack[runCount - 1]; // Reload after merge
            }
            if (runCount >= runStack.length) throw new RuntimeException("Run stack overflow");
            runStack[runCount++] = new Run(i, j - i, p);
            i = j;
        }
        while (runCount >= 2) {
            if (useFourWay && runCount >= 4) mergeTopmost4(a); else mergeTopmost2(a);
        }
    }

    // static convenience wrapper with default options (no galloping, no 4-way, minRun=1)
    public static void powersort(int[] a) {
        new naivesort(false, false, 1).sort(a);
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
        naivesort sorter = new naivesort(true, true, 32);
        sorter.sort(a);
        System.out.println("After:");
        for (int v : a) System.out.print(v + " ");
        System.out.println();
        System.out.println("Merge cost: " + MERGE_COST);
    }
}

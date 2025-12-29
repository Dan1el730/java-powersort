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

    public static class Run {
        public int start; // starting index
        public int len;   // length of run
        public int power; // stored power for boundary

        public Run(int start, int len) {
            this.start = start;
            this.len = len;
            this.power = 0;
        }

        public Run(int start, int len, int power) {
            this.start = start;
            this.len = len;
            this.power = power;
        }
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

    // power: compute the "power" of the boundary between run1 and run2
    // This follows the Python `power_fast` logic (uses integer/bit ops).
    public static int power(Run run1, Run run2, int n) {
        int i1 = run1.start;
        int n1 = run1.len;
        int i2 = run2.start;
        int n2 = run2.len;
        // a = 2 * i1 + n1  (2 * (i1 + n1/2))
        int a = 2 * i1 + n1;
        int b = a + n1 + n2; // 2 * (i2 + n2/2)
        int l = 0;
        while (true) {
            l += 1;
            if (a >= n) {
                // rotate a,b down by n as in Python implementation
                // preserves fractional midpoint comparison modulo 1
                if (!(b >= a)) throw new AssertionError("b < a after a>=n");
                a -= n;
                b -= n;
            } else if (b >= n) {
                break;
            }
            if (!(a < b && b < n)) {
                // safety check; break conservatively
                break;
            }
            a <<= 1;
            b <<= 1;
        }
        return l;
    }

    // mergeTopmost2: merge the two topmost runs on the stack
    // mirrors Python merge_topmost_2 and merge_inplace behavior.
    public static void mergeTopmost2(int[] a, ArrayList<Run> runs) {
        if (runs.size() < 2) throw new IllegalStateException("need >=2 runs to merge");
        Run Y = runs.get(runs.size() - 2);
        Run Z = runs.get(runs.size() - 1);
        if (Z.start != Y.start + Y.len) throw new AssertionError("runs not adjacent");
        int i = Y.start;
        int m = Z.start;
        int j = Z.start + Z.len;
        int outLen = (m - i) + (j - m);
        int[] tmp = new int[outLen];
        int p = i, q = m, t = 0;
        while (p < m && q < j) {
            if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
        }
        while (p < m) tmp[t++] = a[p++];
        while (q < j) tmp[t++] = a[q++];
        // copy merged back into array
        System.arraycopy(tmp, 0, a, i, outLen);
        MERGE_COST += outLen;
        // update runs: replace Y with merged run and pop Z
        runs.set(runs.size() - 2, new Run(Y.start, Y.len + Z.len, Y.power));
        runs.remove(runs.size() - 1);
    }

    // powersort: main algorithm. Mirrors Python powersort(a, extend_run=extend_run)
    public static void powersort(int[] a) {
        int n = a.length;
        int i = 0;
        ArrayList<Run> runs = new ArrayList<>();
        int j = extendRun(a, i);
        runs.add(new Run(i, j - i, 0));
        i = j;
        while (i < n) {
            j = extendRun(a, i);
            Run right = new Run(i, j - i);
            int p = power(runs.get(runs.size() - 1), right, n);
            while (p <= runs.get(runs.size() - 1).power) {
                mergeTopmost2(a, runs);
            }
            runs.add(new Run(i, j - i, p));
            i = j;
        }
        while (runs.size() >= 2) {
            mergeTopmost2(a, runs);
        }
    }

    // Basic demonstration / smoke test
    public static void main(String[] args) {
        int[] a = new int[]{9,8,7,6,5,4,3,2,1,0, 0,1,2,3,4,5,6,7,8,9};
        MERGE_COST = 0;
        System.out.println("Before:");
        for (int v : a) System.out.print(v + " ");
        System.out.println();
        powersort(a);
        System.out.println("After:");
        for (int v : a) System.out.print(v + " ");
        System.out.println();
        System.out.println("Merge cost: " + MERGE_COST);
    }
}

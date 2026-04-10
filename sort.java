/**
 * Primary Powersort implementation with tunable variants V0-V5.
 *
 * Usage (from repository root):
 *   javac sort.java
 *
 * Called by experiment harnesses:
 *   - BenchmarkSuite.java
 *   - PilotBenchmark.java
 *   - PowersortTest.java
 *   - profile.java
 */
public class sort {

	public enum Version {
		V0,
		V1,
		V2,
		V3,
		V4,
		V5
	}

	public static class Run {
		private final int start;
		private int length;
		private int power;

		public Run(int start, int length) {
			this(start, length, 0);
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

	private static class Config {
		private final boolean unrollMerge2;
		private final boolean smartBuffers;
		private final boolean fullInlinePower;
		private final boolean optimizedMerge4;

		private Config(boolean unrollMerge2, boolean smartBuffers, boolean fullInlinePower, boolean optimizedMerge4) {
			this.unrollMerge2 = unrollMerge2;
			this.smartBuffers = smartBuffers;
			this.fullInlinePower = fullInlinePower;
			this.optimizedMerge4 = optimizedMerge4;
		}
	}

	private static Config configFor(Version version) {
		switch (version) {
			case V0:
				return new Config(false, false, false, false);
			case V1:
				return new Config(true, false, false, false);
			case V2:
				return new Config(false, true, false, false);
			case V3:
				return new Config(false, false, true, false);
			case V4:
				return new Config(true, true, true, false);
			case V5:
				return new Config(true, true, true, true);
			default:
				throw new IllegalArgumentException("Unknown version: " + version);
		}
	}

	private static class Engine {
		private static final int DEFAULT_GALLOP = 7;

		private final Config cfg;
		private final boolean useFourWay;
		private final int minRunLength;
		private final int gallopThreshold;

		private int[] tmpBuffer;
		private int[] tmpBuffer2;
		private Run[] runStack;
		private int runCount;
		private long mergeCost;

		private Engine(Config cfg, boolean useFourWay, int minRunLength) {
			this.cfg = cfg;
			this.useFourWay = useFourWay;
			this.minRunLength = Math.max(1, minRunLength);
			this.gallopThreshold = DEFAULT_GALLOP;

			if (cfg.smartBuffers) {
				this.tmpBuffer = new int[32768];
				this.tmpBuffer2 = new int[32768];
			} else {
				this.tmpBuffer = new int[16384];
				this.tmpBuffer2 = null;
			}

			this.runStack = new Run[32];
			this.runCount = 0;
			this.mergeCost = 0;
		}

		private int[] pickMergeBuffer(int outLen) {
			if (!cfg.smartBuffers) {
				if (tmpBuffer.length < outLen) {
					tmpBuffer = new int[(int) (outLen * 1.5)];
				}
				return tmpBuffer;
			}

			if (outLen <= tmpBuffer.length) {
				return tmpBuffer;
			}
			if (outLen <= 65536) {
				int newSize = 1;
				while (newSize < outLen) {
					newSize <<= 1;
				}
				newSize = Math.min(newSize, 65536);
				tmpBuffer = new int[newSize];
				return tmpBuffer;
			}

			if (tmpBuffer2 == null || tmpBuffer2.length < outLen) {
				tmpBuffer2 = new int[outLen + 1024];
			}
			return tmpBuffer2;
		}

		private static int computePowerInline(int i1, int n1, int i2, int n2, int n) {
			long a = 2L * i1 + n1;
			long b = a + n1 + n2;
			int l = 0;
			while (true) {
				l += 1;
				if (a >= n) {
					if (!(b >= a)) {
						throw new AssertionError("Invariant violated: b < a when a>=n");
					}
					a -= n;
					b -= n;
				} else if (b >= n) {
					break;
				}
				if (!(a < b && b < n)) {
					break;
				}
				a <<= 1;
				b <<= 1;
			}
			return l;
		}

		private void mergeTopmost2(int[] a) {
			if (runCount < 2) {
				throw new IllegalStateException("need >=2 runs to merge");
			}

			Run y = runStack[runCount - 2];
			Run z = runStack[runCount - 1];
			if (z.getStart() != y.getStart() + y.getLength()) {
				throw new AssertionError("runs not adjacent");
			}

			int i = y.getStart();
			int m = z.getStart();
			int j = z.getStart() + z.getLength();
			int outLen = (m - i) + (j - m);
			int[] tmp = pickMergeBuffer(outLen);

			int p = i;
			int q = m;
			int t = 0;

			while (p < m && q < j) {
				if (cfg.unrollMerge2 && (m - p >= 4) && (j - q >= 4)) {
					if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
					if (p < m && q < j) {
						if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
						if (p < m && q < j) {
							if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
							if (p < m && q < j) {
								if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
							}
						}
					}
				} else {
					if (a[p] < a[q]) tmp[t++] = a[p++]; else tmp[t++] = a[q++];
				}
			}

			if (p < m) {
				int remain = m - p;
				System.arraycopy(a, p, tmp, t, remain);
			} else {
				int remain = j - q;
				System.arraycopy(a, q, tmp, t, remain);
			}

			System.arraycopy(tmp, 0, a, i, outLen);
			mergeCost += outLen;

			runStack[runCount - 2] = new Run(y.getStart(), y.getLength() + z.getLength(), y.getPower());
			runCount--;
		}

		private void mergeTopmost4(int[] a) {
			if (runCount < 4) {
				throw new IllegalStateException("need >=4 runs to 4-way merge");
			}

			Run ra = runStack[runCount - 4];
			Run rb = runStack[runCount - 3];
			Run rc = runStack[runCount - 2];
			Run rd = runStack[runCount - 1];

			int i = ra.getStart();
			int m1 = rb.getStart();
			int m2 = rc.getStart();
			int m3 = rd.getStart();
			int j = rd.getStart() + rd.getLength();
			int outLen = j - i;

			int[] tmp = pickMergeBuffer(outLen);
			int ia = i, ib = m1, ic = m2, id = m3, t = 0;

			if (cfg.optimizedMerge4) {
				while ((ia < m1) || (ib < m2) || (ic < m3) || (id < j)) {
					int va = (ia < m1) ? a[ia] : Integer.MAX_VALUE;
					int vb = (ib < m2) ? a[ib] : Integer.MAX_VALUE;
					int vc = (ic < m3) ? a[ic] : Integer.MAX_VALUE;
					int vd = (id < j) ? a[id] : Integer.MAX_VALUE;

					int min = Math.min(Math.min(va, vb), Math.min(vc, vd));
					if (min == va) tmp[t++] = a[ia++];
					else if (min == vb) tmp[t++] = a[ib++];
					else if (min == vc) tmp[t++] = a[ic++];
					else if (min == vd) tmp[t++] = a[id++];
					else break;
				}
			} else {
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
			}

			System.arraycopy(tmp, 0, a, i, outLen);
			mergeCost += outLen;

			runStack[runCount - 4] = new Run(ra.getStart(), outLen, ra.getPower());
			runCount -= 3;
		}

		private int enforceMinRun(int[] a, int start, int j, int n) {
			if (minRunLength <= 1) {
				return j;
			}
			int runLen = j - start;
			if (runLen >= minRunLength) {
				return j;
			}
			int want = Math.min(minRunLength, n - start);
			for (int k = j; k < start + want; k++) {
				int key = a[k];
				int lo = start, hi = k;
				while (lo < hi) {
					int mid = (lo + hi) >>> 1;
					if (a[mid] <= key) lo = mid + 1; else hi = mid;
				}
				int t = k;
				while (t > lo) {
					a[t] = a[t - 1];
					t--;
				}
				a[lo] = key;
			}
			return start + Math.min(want, n - start);
		}

		private long sortArray(int[] a) {
			int n = a.length;
			int i = 0;
			runCount = 0;

			int j = extendRun(a, i);
			j = enforceMinRun(a, i, j, n);
			if (runCount >= runStack.length) {
				throw new RuntimeException("Run stack overflow");
			}
			runStack[runCount++] = new Run(i, j - i, 0);
			i = j;

			while (i < n) {
				j = extendRun(a, i);
				j = enforceMinRun(a, i, j, n);
				Run right = new Run(i, j - i);
				Run left = runStack[runCount - 1];

				int p;
				if (cfg.fullInlinePower) {
					p = computePowerInline(left.getStart(), left.getLength(), right.getStart(), right.getLength(), n);
				} else {
					long leftLen = left.getLength();
					long rightLen = right.getLength();
					if (leftLen + rightLen < n / 2) {
						p = computePowerInline(left.getStart(), left.getLength(), right.getStart(), right.getLength(), n);
					} else {
						p = power(left, right, n);
					}
				}

				while (p <= left.getPower()) {
					if (useFourWay && runCount >= 4) {
						mergeTopmost4(a);
					} else {
						mergeTopmost2(a);
					}
					left = runStack[runCount - 1];
				}

				if (runCount >= runStack.length) {
					throw new RuntimeException("Run stack overflow");
				}
				runStack[runCount++] = new Run(i, j - i, p);
				i = j;
			}

			while (runCount >= 2) {
				if (useFourWay && runCount >= 4) {
					mergeTopmost4(a);
				} else {
					mergeTopmost2(a);
				}
			}

			return mergeCost;
		}
	}

	public static int extendRun(int[] a, int i) {
		if (i >= a.length - 1) return i + 1;
		int j = i + 1;
		while (j < a.length && a[j - 1] <= a[j]) {
			j++;
		}
		return j;
	}

	public static int power(Run run1, Run run2, int n) {
		long i1 = run1.getStart();
		long n1 = run1.getLength();
		long i2 = run2.getStart();
		long n2 = run2.getLength();
		long a = 2L * i1 + n1;
		long b = a + n1 + n2;
		int l = 0;
		while (true) {
			l += 1;
			if (a >= n) {
				if (!(b >= a)) throw new AssertionError("Invariant violated: b < a when a>=n");
				a -= n;
				b -= n;
			} else if (b >= n) {
				break;
			}
			if (!(a < b && b < n)) {
				break;
			}
			a <<= 1;
			b <<= 1;
		}
		return l;
	}

	public static long powersort(int[] a, Version version) {
		Engine engine = new Engine(configFor(version), false, 1);
		return engine.sortArray(a);
	}

	public static long powersort(int[] a) {
		return powersort(a, Version.V0);
	}

	public static String versions() {
		StringBuilder sb = new StringBuilder();
		for (Version version : Version.values()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(version.name().toLowerCase());
		}
		return sb.toString();
	}
}

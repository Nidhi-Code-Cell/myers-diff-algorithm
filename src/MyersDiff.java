
public final class MyersDiff {

    /** Marks the elements that are not part of the common subsequence. */
    public static final class Result {
        /** deleted[i]  is true if a[i] is only in A. */
        public final boolean[] deleted;
        /** inserted[j] is true if b[j] is only in B. */
        public final boolean[] inserted;

        Result(boolean[] deleted, boolean[] inserted) {
            this.deleted = deleted;
            this.inserted = inserted;
        }
    }

    /** Marks an out-of-grid / unreachable diagonal in the V arrays. */
    private static final int NEG = Integer.MIN_VALUE / 4;

    private final int[] a;
    private final int[] b;
    private final boolean[] deleted;
    private final boolean[] inserted;

    /** vf[k] = furthest x reached on diagonal k (k = x - y) by the forward search. */
    private final int[] vf;
    /** vb[k] = furthest x' reached on diagonal k' by the backward search (reversed coordinates). */
    private final int[] vb;
    /** Index shift so that negative diagonals map to valid array indices. */
    private final int offset;

    private MyersDiff(int[] a, int[] b) {
        this.a = a;
        this.b = b;
        this.deleted = new boolean[a.length];
        this.inserted = new boolean[b.length];
        this.offset = (a.length + b.length + 1) / 2 + 2;
        this.vf = new int[2 * offset + 2];
        this.vb = new int[2 * offset + 2];
    }

    public static Result diff(int[] a, int[] b) {
        MyersDiff m = new MyersDiff(a, b);
        m.solve(0, a.length, 0, b.length);
        return new Result(m.deleted, m.inserted);
    }

    /** Diffs a[aLo, aHi) against b[bLo, bHi) and records the edits. */
    private void solve(int aLo, int aHi, int bLo, int bHi) {
        // Strip the common prefix and suffix: they are always kept.
        while (aLo < aHi && bLo < bHi && a[aLo] == b[bLo]) {
            aLo++;
            bLo++;
        }
        while (aLo < aHi && bLo < bHi && a[aHi - 1] == b[bHi - 1]) {
            aHi--;
            bHi--;
        }

        if (aLo == aHi) {                       // only insertions remain
            for (int j = bLo; j < bHi; j++) inserted[j] = true;
            return;
        }
        if (bLo == bHi) {                       // only deletions remain
            for (int i = aLo; i < aHi; i++) deleted[i] = true;
            return;
        }

        // Both sides are non-empty and differ at both ends, so D >= 2 and the
        // two halves are strictly smaller than the whole problem.
        int[] s = middleSnake(aLo, aHi, bLo, bHi);
        solve(aLo, aLo + s[0], bLo, bLo + s[1]);        // before the snake
        solve(aLo + s[2], aHi, bLo + s[3], bHi);        // after the snake
    }

    /**
     * Finds the middle snake of an optimal edit path.
     *
     * return {x1, y1, x2, y2}: the snake runs from (x1, y1) to (x2, y2),
     *         coordinates relative to aLo / bLo.
     */
    private int[] middleSnake(int aLo, int aHi, int bLo, int bHi) {
        final int n = aHi - aLo;
        final int m = bHi - bLo;
        final int delta = n - m;
        final boolean oddDelta = (delta & 1) != 0;
        final int maxD = (n + m + 1) / 2;

        vf[offset + 1] = 0;     // seed so that d = 0, k = 0 starts at x = 0
        vb[offset + 1] = 0;

        for (int d = 0; d <= maxD; d++) {

            // ---- forward search: d edits from the top-left corner ----
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && vf[offset + k - 1] < vf[offset + k + 1])) {
                    x = vf[offset + k + 1];             // step down (insertion)
                } else {
                    x = vf[offset + k - 1] + 1;         // step right (deletion)
                }
                int y = x - k;
                if (x < 0 || x > n || y < 0 || y > m) { // outside the grid
                    vf[offset + k] = NEG;
                    continue;
                }
                int xStart = x, yStart = y;
                while (x < n && y < m && a[aLo + x] == b[bLo + y]) {   // follow the snake
                    x++;
                    y++;
                }
                vf[offset + k] = x;

                // Forward path meets a backward path of d-1 edits (odd delta).
                if (oddDelta) {
                    int kb = delta - k;
                    if (kb >= -(d - 1) && kb <= d - 1 && x + vb[offset + kb] >= n) {
                        return new int[] {xStart, yStart, x, y};
                    }
                }
            }

            // ---- backward search: d edits from the bottom-right corner ----
            // Works on the reversed sequences, so x' = n - x and y' = m - y.
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && vb[offset + k - 1] < vb[offset + k + 1])) {
                    x = vb[offset + k + 1];
                } else {
                    x = vb[offset + k - 1] + 1;
                }
                int y = x - k;
                if (x < 0 || x > n || y < 0 || y > m) {
                    vb[offset + k] = NEG;
                    continue;
                }
                int xStart = x, yStart = y;
                while (x < n && y < m && a[aHi - 1 - x] == b[bHi - 1 - y]) {
                    x++;
                    y++;
                }
                vb[offset + k] = x;

                // Backward path meets the forward path of d edits (even delta).
                if (!oddDelta) {
                    int kf = delta - k;
                    if (kf >= -d && kf <= d && x + vf[offset + kf] >= n) {
                        // Convert the reversed coordinates back to forward ones.
                        return new int[] {n - x, m - y, n - xStart, m - yStart};
                    }
                }
            }
        }
        throw new IllegalStateException("middle snake not found");
    }
}
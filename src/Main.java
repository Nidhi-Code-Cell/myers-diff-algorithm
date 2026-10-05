import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;


public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: Main (lines|highlight) <fileA> <fileB>");
            System.exit(1);
        }
        boolean highlight = args[0].equals("highlight");

        // Read both files before printing anything: on failure stdout stays empty.
        byte[][] linesA;
        byte[][] linesB;
        try {
            linesA = splitLines(Files.readAllBytes(Paths.get(args[1])));
            linesB = splitLines(Files.readAllBytes(Paths.get(args[2])));
        } catch (IOException | RuntimeException e) {
            System.err.println("error: cannot read input file: " + e.getMessage());
            System.exit(2);
            return;
        }

        // Both files share one id table so equal lines get equal ids.
        Map<ByteBuffer, Integer> ids = new HashMap<>();
        MyersDiff.Result result = MyersDiff.diff(toIds(linesA, ids), toIds(linesB, ids));

        OutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);
        writeDiff(out, linesA, linesB, result, highlight);
        out.flush();
    }

    /**
     * Splits raw bytes on '\n'. A trailing empty piece is dropped, so a final
     * newline adds no line and an empty file has no lines. '\r' stays in the line.
     */
    static byte[][] splitLines(byte[] data) {
        int pieces = 1;
        for (byte c : data) {
            if (c == '\n') pieces++;
        }
        byte[][] lines = new byte[pieces][];
        int count = 0;
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == '\n') {
                lines[count++] = java.util.Arrays.copyOfRange(data, start, i);
                start = i + 1;
            }
        }
        // Drop the last piece if it is empty.
        if (lines[count - 1].length == 0) count--;
        return count == lines.length ? lines : java.util.Arrays.copyOf(lines, count);
    }

    /**
     * Maps every distinct line (compared as exact bytes) to a small int, so the
     * diff can compare ints instead of byte arrays.
     */
    static int[] toIds(byte[][] lines, Map<ByteBuffer, Integer> ids) {
        int[] result = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            ByteBuffer key = ByteBuffer.wrap(lines[i]);
            Integer id = ids.get(key);
            if (id == null) {
                id = ids.size();
                ids.put(key, id);
            }
            result[i] = id;
        }
        return result;
    }

    /**
     * Prints the edit script. Between two keep lines, a change block is printed
     * as all '-' lines first, then all '+' lines. In highlight mode each '+' line
     * that has a partner '-' line (k-th with k-th) is followed by a '?' line.
     */
    static void writeDiff(OutputStream out, byte[][] a, byte[][] b,
                          MyersDiff.Result r, boolean highlight) throws IOException {
        int i = 0;
        int j = 0;
        while (i < a.length || j < b.length) {
            // Collect one change block: deleted lines of A, inserted lines of B.
            int delStart = i;
            while (i < a.length && r.deleted[i]) i++;
            int insStart = j;
            while (j < b.length && r.inserted[j]) j++;
            int delCount = i - delStart;
            int insCount = j - insStart;

            for (int t = 0; t < delCount; t++) {
                writeLine(out, '-', a[delStart + t]);
            }
            for (int t = 0; t < insCount; t++) {
                writeLine(out, '+', b[insStart + t]);
                if (highlight && t < delCount) {
                    writeHighlight(out, a[delStart + t], b[insStart + t]);
                }
            }

            // Next line (if any) is a keep line, present in both files.
            if (i < a.length && j < b.length) {
                writeLine(out, ' ', a[i]);
                i++;
                j++;
            }
        }
    }

    private static void writeLine(OutputStream out, char prefix, byte[] line) throws IOException {
        out.write(prefix);
        out.write(line);
        out.write('\n');
    }

    /** Writes "? <old ranges> | <new ranges>" for one paired '-' / '+' line. */
    private static void writeHighlight(OutputStream out, byte[] oldLine, byte[] newLine)
            throws IOException {
        int[] oldChars = new String(oldLine, StandardCharsets.UTF_8).codePoints().toArray();
        int[] newChars = new String(newLine, StandardCharsets.UTF_8).codePoints().toArray();

        MyersDiff.Result r = MyersDiff.diff(oldChars, newChars);   // same algorithm, on characters

        String text = "? " + formatRanges(r.deleted) + " | " + formatRanges(r.inserted) + "\n";
        out.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Turns a changed-character mask into "start-end,start-end" (end exclusive).
     * Touching changes form a single run, so ranges are already merged and ordered.
     * Returns "." if nothing changed.
     */
    static String formatRanges(boolean[] changed) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < changed.length) {
            if (!changed[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < changed.length && changed[i]) i++;
            if (sb.length() > 0) sb.append(',');
            sb.append(start).append('-').append(i);
        }
        return sb.length() == 0 ? "." : sb.toString();
    }
}
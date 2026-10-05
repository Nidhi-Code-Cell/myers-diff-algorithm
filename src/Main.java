import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    /*
     * A line is a view into the original byte array.
     *
     * We do NOT convert the line to String.
     */
    static class Line {

        final byte[] data;
        final int start;
        final int end;

        Line(byte[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
        }

        int length() {
            return end - start;
        }

        void write() {
            System.out.write(data, start, length());
        }

        @Override
        public boolean equals(Object obj) {

            if (!(obj instanceof Line other)) {
                return false;
            }

            return Arrays.mismatch(
                    data,
                    start,
                    end,
                    other.data,
                    other.start,
                    other.end
            ) == -1;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /*
     * Read raw bytes and split on byte 0x0A.
     */
    static List<Line> readLines(Path path) throws IOException {

        byte[] data = Files.readAllBytes(path);

        List<Line> lines = new ArrayList<>();

        int start = 0;

        for (int i = 0; i < data.length; i++) {

            if (data[i] == '\n') {

                lines.add(
                        new Line(data, start, i)
                );

                start = i + 1;
            }
        }

        /*
         * If the final piece is non-empty, keep it.
         *
         * If the file ended in \n, start == data.length,
         * so nothing is added.
         */
        if (start < data.length) {

            lines.add(
                    new Line(
                            data,
                            start,
                            data.length
                    )
            );
        }

        return lines;
    }

    static void printLine(
            MyersDiff.Operation<Line> operation) {

        switch (operation.type) {

            case EQUAL:
                System.out.write(' ');
                break;

            case DELETE:
                System.out.write('-');
                break;

            case INSERT:
                System.out.write('+');
                break;
        }

        operation.value.write();

        System.out.write('\n');
    }

    public static void main(String[] args) {

        boolean valid =
                args.length == 3 &&
                        (args[0].equals("lines") ||
                                args[0].equals("highlight"));

        if (!valid) {

            System.err.println(
                    "usage: Main lines|highlight A_PATH B_PATH"
            );

            System.exit(2);
        }

        String command = args[0];

        try {

            List<Line> a =
                    readLines(Path.of(args[1]));

            List<Line> b =
                    readLines(Path.of(args[2]));

            /*
             * Part A.
             */
            List<MyersDiff.Operation<Line>> operations =
                    MyersDiff.diff(
                            a,
                            b,
                            Line::equals
                    );

            /*
             * For now, print the line diff.
             */
            for (MyersDiff.Operation<Line> operation
                    : operations) {

                printLine(operation);
            }

        } catch (IOException e) {

            /*
             * Assignment requirement:
             *
             * stdout = empty
             * stderr = error
             * exit code = 2
             */
            System.err.println(
                    "error: " + e.getMessage()
            );

            System.exit(2);
        }
    }
}
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

public class MyersDiff {

    public enum Type {
        EQUAL,
        DELETE,
        INSERT
    }

    public static class Operation<T> {
        public final Type type;
        public final T value;

        public Operation(Type type, T value) {
            this.type = type;
            this.value = value;
        }
    }

    private record Split(int x, int y) {
    }

    public static <T> List<Operation<T>> diff(
            List<T> a,
            List<T> b,
            BiPredicate<T, T> equal) {

        List<Operation<T>> result = new ArrayList<>();

        diffRange(
                a, 0, a.size(),
                b, 0, b.size(),
                equal,
                result
        );


        return deleteFirst(result);
    }


    private static <T> void diffRange(
            List<T> a,
            int aStart,
            int aEnd,
            List<T> b,
            int bStart,
            int bEnd,
            BiPredicate<T, T> equal,
            List<Operation<T>> result) {


        while (aStart < aEnd &&
                bStart < bEnd &&
                equal.test(a.get(aStart), b.get(bStart))) {

            result.add(
                    new Operation<>(
                            Type.EQUAL,
                            a.get(aStart)
                    )
            );

            aStart++;
            bStart++;
        }

        int suffix = 0;

        while (aEnd - suffix > aStart &&
                bEnd - suffix > bStart &&
                equal.test(
                        a.get(aEnd - suffix - 1),
                        b.get(bEnd - suffix - 1)
                )) {

            suffix++;
        }

        int middleAEnd = aEnd - suffix;
        int middleBEnd = bEnd - suffix;



        if (aStart == middleAEnd) {

            for (int j = bStart; j < middleBEnd; j++) {

                result.add(
                        new Operation<>(
                                Type.INSERT,
                                b.get(j)
                        )
                );
            }

        } else if (bStart == middleBEnd) {

            for (int i = aStart; i < middleAEnd; i++) {

                result.add(
                        new Operation<>(
                                Type.DELETE,
                                a.get(i)
                        )
                );

            }

        } else {


            Split split = bisect(
                    a,
                    aStart,
                    middleAEnd,
                    b,
                    bStart,
                    middleBEnd,
                    equal
            );

            int n = middleAEnd - aStart;
            int m = middleBEnd - bStart;


            if (split == null ||
                    (split.x == 0 && split.y == 0) ||
                    (split.x == n && split.y == m)) {

                for (int i = aStart; i < middleAEnd; i++) {

                    result.add(
                            new Operation<>(
                                    Type.DELETE,
                                    a.get(i)
                            )
                    );
                }

                for (int j = bStart; j < middleBEnd; j++) {

                    result.add(
                            new Operation<>(
                                    Type.INSERT,
                                    b.get(j)
                            )
                    );
                }

            } else {



                int splitA = aStart + split.x;
                int splitB = bStart + split.y;

                diffRange(
                        a,
                        aStart,
                        splitA,
                        b,
                        bStart,
                        splitB,
                        equal,
                        result
                );

                diffRange(
                        a,
                        splitA,
                        middleAEnd,
                        b,
                        splitB,
                        middleBEnd,
                        equal,
                        result
                );
            }
        }


        for (int j = middleBEnd; j < bEnd; j++) {

            result.add(
                    new Operation<>(
                            Type.EQUAL,
                            b.get(j)
                    )
            );
        }
    }


    private static <T> Split bisect(
            List<T> a,
            int aStart,
            int aEnd,
            List<T> b,
            int bStart,
            int bEnd,
            BiPredicate<T, T> equal) {

        int n = aEnd - aStart;
        int m = bEnd - bStart;

        int maxD = (n + m + 1) / 2;


        int offset = maxD + 1;

        int length = 2 * maxD + 3;

        int[] forward = new int[length];
        int[] reverse = new int[length];

        Arrays.fill(forward, -1);
        Arrays.fill(reverse, -1);

        forward[offset + 1] = 0;
        reverse[offset + 1] = 0;

        int delta = n - m;


        boolean front = (delta & 1) != 0;

        int forwardStart = 0;
        int forwardEnd = 0;

        int reverseStart = 0;
        int reverseEnd = 0;

        for (int d = 0; d < maxD; d++) {



            for (int k = -d + forwardStart;
                 k <= d - forwardEnd;
                 k += 2) {

                int index = offset + k;

                int x;

                if (k == -d ||
                        (k != d &&
                                forward[index - 1] < forward[index + 1])) {


                    x = forward[index + 1];

                } else {


                    x = forward[index - 1] + 1;
                }

                int y = x - k;


                while (x < n &&
                        y < m &&
                        equal.test(
                                a.get(aStart + x),
                                b.get(bStart + y)
                        )) {

                    x++;
                    y++;
                }

                forward[index] = x;


                if (x > n) {

                    forwardEnd += 2;

                } else if (y > m) {

                    forwardStart += 2;

                } else if (front) {

                    int reverseIndex =
                            offset + delta - k;

                    if (reverseIndex >= 0 &&
                            reverseIndex < length &&
                            reverse[reverseIndex] != -1) {

                        int reverseX =
                                n - reverse[reverseIndex];

                        if (x >= reverseX) {

                            return new Split(x, y);
                        }
                    }
                }
            }


            for (int k = -d + reverseStart;
                 k <= d - reverseEnd;
                 k += 2) {

                int index = offset + k;

                int x;

                if (k == -d ||
                        (k != d &&
                                reverse[index - 1] < reverse[index + 1])) {

                    x = reverse[index + 1];

                } else {

                    x = reverse[index - 1] + 1;
                }

                int y = x - k;

                while (x < n &&
                        y < m &&
                        equal.test(
                                a.get(aEnd - x - 1),
                                b.get(bEnd - y - 1)
                        )) {

                    x++;
                    y++;
                }

                reverse[index] = x;

                if (x > n) {

                    reverseEnd += 2;

                } else if (y > m) {

                    reverseStart += 2;

                } else if (!front) {

                    int forwardIndex =
                            offset + delta - k;

                    if (forwardIndex >= 0 &&
                            forwardIndex < length &&
                            forward[forwardIndex] != -1) {

                        int forwardX =
                                forward[forwardIndex];

                        int forwardK =
                                forwardIndex - offset;

                        int forwardY =
                                forwardX - forwardK;

                        int reverseX =
                                n - x;

                        if (forwardX >= reverseX) {

                            return new Split(
                                    forwardX,
                                    forwardY
                            );
                        }
                    }
                }
            }
        }

        return null;
    }

    /*
     * -------------------------------------------------------------
     * Delete-first rule
     * -------------------------------------------------------------
     *
     * A change block is:
     *
     * DELETE DELETE INSERT INSERT
     *
     * rather than:
     *
     * DELETE INSERT DELETE INSERT
     */
    private static <T> List<Operation<T>> deleteFirst(
            List<Operation<T>> input) {

        List<Operation<T>> output =
                new ArrayList<>(input.size());

        int i = 0;

        while (i < input.size()) {

            Operation<T> current = input.get(i);

            /*
             * Keep line.
             */
            if (current.type == Type.EQUAL) {

                output.add(current);
                i++;
                continue;
            }

            /*
             * Find the end of this change block.
             */
            int j = i;

            while (j < input.size() &&
                    input.get(j).type != Type.EQUAL) {

                j++;
            }

            /*
             * First all DELETE operations.
             */
            for (int k = i; k < j; k++) {

                if (input.get(k).type == Type.DELETE) {
                    output.add(input.get(k));
                }
            }

            /*
             * Then all INSERT operations.
             */
            for (int k = i; k < j; k++) {

                if (input.get(k).type == Type.INSERT) {
                    output.add(input.get(k));
                }
            }

            i = j;
        }

        return output;
    }
}
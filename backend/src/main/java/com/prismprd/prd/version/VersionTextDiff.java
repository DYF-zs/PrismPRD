package com.prismprd.prd.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 按行对比不可变 Markdown 快照；大文档降为整段增删，限制内存使用。 */
public final class VersionTextDiff {
    public record Line(String type, String text) {}
    private VersionTextDiff() {}

    public static List<Line> compare(String before, String after) {
        String[] a = Objects.requireNonNullElse(before, "").split("\n", -1);
        String[] b = Objects.requireNonNullElse(after, "").split("\n", -1);
        List<Line> result = new ArrayList<>();
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) {
            result.add(new Line("EQUAL", a[prefix++]));
        }
        int endA = a.length, endB = b.length;
        while (endA > prefix && endB > prefix && a[endA - 1].equals(b[endB - 1])) { endA--; endB--; }
        int n = endA - prefix, m = endB - prefix;
        if ((long) (n + 1) * (m + 1) > 2_000_000) {
            for (int i = prefix; i < endA; i++) result.add(new Line("DELETE", a[i]));
            for (int j = prefix; j < endB; j++) result.add(new Line("INSERT", b[j]));
        } else {
            int[][] lengths = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) for (int j = m - 1; j >= 0; j--)
                lengths[i][j] = a[prefix + i].equals(b[prefix + j]) ? lengths[i + 1][j + 1] + 1
                        : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            int i = 0, j = 0;
            while (i < n || j < m) {
                if (i < n && j < m && a[prefix + i].equals(b[prefix + j])) {
                    result.add(new Line("EQUAL", a[prefix + i++])); j++;
                } else if (i < n && (j == m || lengths[i + 1][j] >= lengths[i][j + 1])) {
                    result.add(new Line("DELETE", a[prefix + i++]));
                } else result.add(new Line("INSERT", b[prefix + j++]));
            }
        }
        for (int i = endA; i < a.length; i++) result.add(new Line("EQUAL", a[i]));
        return List.copyOf(result);
    }
}

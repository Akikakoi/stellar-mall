package com.stellar.elasticsearch.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 查询纠错器（「您是不是要找」）——纯内存算法，不依赖 ES。
 * <p>
 * 中文搜索的错字绝大多数不是拉丁式拼写错位，而是同音字（兰牙→蓝牙）、形近字（末来→未来）、
 * 漏字/错字、以及数字字母混淆（k7O→k70），因此按四类特征匹配词库：
 * ① 拼音全等（同音字）；② 形近字归一后全等或距离 1（字形接近，读音可能不同）；
 * ③ 字符 Damerau-Levenshtein ≤ 1（错/漏/多字、相邻换位）；④ 数字字母易混字符归一后全等。
 * 词库由 {@link SearchDictionaryService} 定期重建。
 * <p>
 * 查询词本身是词库词时不做纠错（那是有效查询，零结果另有原因），
 * 单字查询也不纠错（改一个字就完全变义，风险大于收益）。
 */
public class SearchCorrector {

    /** 词库条目：priority 越高（分类名/同义词等短词元）越优先作为纠错建议。 */
    public record Term(String text, int priority) {}

    private record Entry(String norm, String pinyin, String skeleton, String shape, int priority, String display) {}

    private static final int MAX_ENTRIES = 30_000;
    private static final double MIN_SCORE = 0.80;
    /** 仅大小写/易混字符差异，如 红米k7O → 红米K70 */
    private static final double SCORE_SKELETON_EQUAL = 0.95;
    /** 拼音全等且字符重合度过半，即同音错字，如 兰牙耳机 → 蓝牙耳机 */
    private static final double SCORE_PINYIN_EQUAL = 0.92;
    /** 形近字归一后全等且字符重合度过半，如 末来 → 未来（读音不同，拼音规则覆盖不到） */
    private static final double SCORE_SHAPE_EQUAL = 0.90;
    /** 字符距离 1（错字/漏字/相邻换位），如 真无线耳塞 → 真无线耳机 */
    private static final double SCORE_CHAR_TYPO = 0.86;
    /** 形近归一后再容忍一处差异，如 末来科枝 → 未来科技 */
    private static final double SCORE_SHAPE_TYPO = 0.84;
    /** 拼音距离 1 且字符重合度过半，如 海而 → 海尔 */
    private static final double SCORE_PINYIN_TYPO = 0.80;

    /**
     * 常见形近字组：组内首字为代表字，其余字归一为代表字。
     * 只收「一字之差、多数情况下其中一种是错写」的易混组，且以不同读音的为主——
     * 同音形近字（蓝/篮）已由拼音规则覆盖，列在这里是为了和多处形近错字叠加时仍能召回。
     */
    private static final String[] SHAPE_GROUPS = {
            "己已巳", "未末", "戌戍戊", "拔拨", "折拆", "今令", "乌鸟", "兔免",
            "崇祟", "栗粟", "刺剌", "辨辩辫", "微徽", "晴睛", "练炼", "待侍",
            "幕慕墓暮", "竞竟", "籍藉", "蓝篮", "漫慢", "燥躁", "侯候"
    };
    private static final Map<Character, Character> SHAPE_CANON = buildShapeCanon();

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = buildPinyinFormat();

    private final Map<Character, String> charPinyinCache = new ConcurrentHashMap<>();
    private volatile List<Entry> entries = List.of();
    private volatile Set<String> norms = Set.of();
    private final Cache<String, Optional<String>> resultCache = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    /**
     * 重建词库。重复词按 priority 保留高者（分类名/同义词优先于商品名词元）。
     */
    public synchronized void refresh(List<Term> terms) {
        Map<String, Entry> unique = new LinkedHashMap<>();
        if (terms != null) {
            for (Term term : terms) {
                if (term == null || term.text() == null) continue;
                String norm = normalize(term.text());
                if (norm.length() < 2 || norm.length() > 40) continue;
                if (!hasLetter(norm)) continue; // 纯数字/符号没有纠错意义
                Entry prev = unique.get(norm);
                if (prev == null || term.priority() > prev.priority()) {
                    unique.put(norm, new Entry(norm, pinyin(norm), skeleton(norm), shape(norm),
                            term.priority(), term.text().trim()));
                }
                if (unique.size() >= MAX_ENTRIES) break;
            }
        }
        entries = List.copyOf(unique.values());
        norms = Set.copyOf(unique.keySet());
        resultCache.invalidateAll();
    }

    public int size() {
        return entries.size();
    }

    /**
     * 返回纠错建议；无可靠建议（或查询词本身有效/过短）时返回 null。
     */
    public String correct(String query) {
        String qn = normalize(query);
        if (qn.length() < 2 || qn.length() > 40) return null;
        Optional<String> cached = resultCache.getIfPresent(qn);
        if (cached != null) return cached.orElse(null);
        String result = findBest(qn);
        resultCache.put(qn, Optional.ofNullable(result));
        return result;
    }

    private String findBest(String qn) {
        List<Entry> snapshot = entries;
        if (snapshot.isEmpty() || norms.contains(qn)) return null;
        String qp = pinyin(qn);
        String qs = skeleton(qn);
        String qShape = shape(qn);

        Entry best = null;
        double bestScore = 0;
        for (Entry e : snapshot) {
            if (Math.abs(e.norm().length() - qn.length()) > 2) continue;
            double score = score(e, qp, qs, qShape);
            if (score < MIN_SCORE) continue;
            if (best == null || score > bestScore || (score == bestScore && prefer(e, best))) {
                best = e;
                bestScore = score;
            }
        }
        return best == null ? null : best.display();
    }

    private static double score(Entry e, String qp, String qs, String qShape) {
        if (e.skeleton().equals(qs) && qs.length() >= 3) return SCORE_SKELETON_EQUAL;
        if (e.pinyin().equals(qp) && lcsRatio(qs, e.skeleton()) >= 0.5) return SCORE_PINYIN_EQUAL;
        if (e.shape().equals(qShape) && qs.length() >= 2
                && lcsRatio(qs, e.skeleton()) >= 0.5) return SCORE_SHAPE_EQUAL;
        if (damerau(e.skeleton(), qs, 2) == 1 && qs.length() >= 3) return SCORE_CHAR_TYPO;
        if (damerau(e.shape(), qShape, 2) == 1 && qs.length() >= 3
                && lcsRatio(qs, e.skeleton()) >= 0.5) return SCORE_SHAPE_TYPO;
        if (damerau(e.pinyin(), qp, 2) == 1 && qs.length() >= 3
                && lcsRatio(qs, e.skeleton()) >= 0.5) return SCORE_PINYIN_TYPO;
        return 0;
    }

    /** 同分时：priority 高者优，其次词条短者优，再按字典序。 */
    private static boolean prefer(Entry candidate, Entry current) {
        if (candidate.priority() != current.priority()) return candidate.priority() > current.priority();
        if (candidate.display().length() != current.display().length())
            return candidate.display().length() < current.display().length();
        return candidate.display().compareTo(current.display()) < 0;
    }

    /** 归一化：只保留字母/数字（含汉字），全部小写——空格标点不参与匹配。 */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) sb.appendCodePoint(Character.toLowerCase(cp));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /** 易混字符归一：o↔0、i/l↔1（红米k7O 与 红米K70 视为同形）。 */
    static String skeleton(String norm) {
        StringBuilder sb = new StringBuilder(norm.length());
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            switch (c) {
                case 'o' -> c = '0';
                case 'i', 'l' -> c = '1';
                default -> { }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 形近字归一：把易混字形映射到同组代表字（未/末 都变 未，己/已/巳 都变 己）。 */
    static String shape(String norm) {
        StringBuilder sb = new StringBuilder(norm.length());
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            sb.append(SHAPE_CANON.getOrDefault(c, c));
        }
        return sb.toString();
    }

    private static Map<Character, Character> buildShapeCanon() {
        Map<Character, Character> map = new HashMap<>();
        for (String group : SHAPE_GROUPS) {
            char canon = group.charAt(0);
            for (int i = 1; i < group.length(); i++) {
                Character prev = map.putIfAbsent(group.charAt(i), canon);
                if (prev != null) {
                    throw new IllegalStateException("形近字组重复: " + group.charAt(i));
                }
            }
        }
        return Map.copyOf(map);
    }

    private String pinyin(String norm) {
        StringBuilder sb = new StringBuilder(norm.length() * 2);
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            if (isHan(c)) {
                String py = charPinyin(c);
                sb.append(py != null ? py : c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String charPinyin(char c) {
        String cached = charPinyinCache.get(c);
        if (cached != null) return cached;
        String py = null;
        try {
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
            if (arr != null && arr.length > 0) py = arr[0];
        } catch (Exception e) {
            // pinyin4j 对个别生僻字会抛异常，按"无拼音"降级
        }
        if (py != null) charPinyinCache.put(c, py);
        return py;
    }

    private static HanyuPinyinOutputFormat buildPinyinFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
        return format;
    }

    private static boolean isHan(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static boolean hasLetter(String norm) {
        for (int i = 0; i < norm.length(); i++) {
            if (Character.isLetter(norm.charAt(i))) return true;
        }
        return false;
    }

    /** 最长公共子序列占比：衡量同音候选与原词的字符重合度，过滤纯同音不同义的误报。 */
    static double lcsRatio(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? prev[j - 1] + 1
                        : Math.max(prev[j], cur[j - 1]);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
            Arrays.fill(cur, 0);
        }
        return (double) prev[b.length()] / Math.max(a.length(), b.length());
    }

    /** Damerau-Levenshtein（OSA）：含相邻换位计 1 次，超过 max 时提前返回 max+1。 */
    static int damerau(String a, String b, int max) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > max) return max + 1;
        int[] prevPrev = new int[m + 1];
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, prevPrev[j - 2] + 1);
                }
                cur[j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) return max + 1;
            int[] tmp = prevPrev;
            prevPrev = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
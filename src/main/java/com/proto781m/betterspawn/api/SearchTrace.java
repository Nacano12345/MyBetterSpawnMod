package com.proto781m.betterspawn.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索诊断轨迹：记录每种方法搜索过程中的环带/候选点信息，供 {@code /gotodim --verbose} 使用。
 */
public class SearchTrace {

    /** 单个环带的搜索统计。 */
    public record RingTrace(int radius, int candidates, int accepted) {
    }

    private final List<RingTrace> rings = new ArrayList<>();

    /** 记录一个环带的搜索情况。 */
    public void ring(int radius, int candidates, int accepted) {
        rings.add(new RingTrace(radius, candidates, accepted));
    }

    public List<RingTrace> rings() {
        return List.copyOf(rings);
    }

    public int totalCandidates() {
        return rings.stream().mapToInt(RingTrace::candidates).sum();
    }

    public int totalAccepted() {
        return rings.stream().mapToInt(RingTrace::accepted).sum();
    }

    /** 格式化为多行文本，便于聊天栏/日志输出。 */
    public String format() {
        StringBuilder sb = new StringBuilder();
        for (RingTrace r : rings) {
            sb.append(String.format("  半径 %d: 候选 %d, 接受 %d%n", r.radius(), r.candidates(), r.accepted()));
        }
        return sb.toString();
    }
}

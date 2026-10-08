package local.codex.wallpapers;
import java.util.*;
public class UsagePolicyTest {
    static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    public static void main(String[] args){
        eq("high",UsagePolicy.mood(80d));eq("high",UsagePolicy.mood(100d));
        eq("medium",UsagePolicy.mood(79.999));eq("medium",UsagePolicy.mood(40d));
        eq("low",UsagePolicy.mood(39.999));eq("low",UsagePolicy.mood(0d));
        eq("medium",UsagePolicy.mood(null));eq("medium",UsagePolicy.mood(Double.NaN));eq("medium",UsagePolicy.mood(-1d));
        List<String> phone=Arrays.asList("智能体使用限额","每周使用限额","剩余 41%","重置时间：4天后","剩余额度","0 额度","用量与限额");
        eq(41d,UsagePolicy.phoneWeeklyRemaining(phone));
        eq(null,UsagePolicy.phoneWeeklyRemaining(Arrays.asList("你好","剩余 41%")));
        eq(null,UsagePolicy.phoneWeeklyRemaining(Arrays.asList("用量与限额","智能体使用限额","每周使用限额","0 额度")));
        eq(null,UsagePolicy.phoneWeeklyRemaining(Arrays.asList("用量与限额","智能体使用限额","每周使用限额","剩余 141%")));
        eq(80d,UsagePolicy.phoneWeeklyRemaining(Arrays.asList("Usage & limits","Agent usage limits","Weekly limit","80% remaining")));
        eq(0xff74df91,UsagePolicy.quotaColor(100d));eq(0xff74df91,UsagePolicy.quotaColor(80d));
        eq(0xff75cfe0,UsagePolicy.quotaColor(79.99));eq(0xff75cfe0,UsagePolicy.quotaColor(60d));
        eq(0xffffd45c,UsagePolicy.quotaColor(59.99));eq(0xffffd45c,UsagePolicy.quotaColor(40d));
        eq(0xffb88658,UsagePolicy.quotaColor(39.99));eq(0xffb88658,UsagePolicy.quotaColor(20d));
        eq(0xffff6262,UsagePolicy.quotaColor(19.99));eq(0xffff6262,UsagePolicy.quotaColor(0d));
        eq(0xff8b969e,UsagePolicy.quotaColor(null));eq(0xff8b969e,UsagePolicy.quotaColor(Double.NaN));
        System.out.println("PASS: 26 color/threshold, unknown-data, phone-page and false-match assertions");
    }
}

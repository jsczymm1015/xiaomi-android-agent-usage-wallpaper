package local.codex.wallpapers;

import java.time.Instant;
import java.util.Objects;

public class UsageResetCardsTest {
    static int assertions;
    static final long NOW=Instant.parse("2026-10-08T04:00:00Z").getEpochSecond();
    static final long EXPIRY=Instant.parse("2026-10-09T15:59:00Z").getEpochSecond();
    static void eq(Object expected,Object actual){assertions++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    static UsageResetCards cards(Object count,Object expiry,String status){return UsageResetCards.fromValues(count,expiry,status,NOW,UsageResetCards.SOURCE,NOW,NOW);}
    static void invalid(Object count,Object expiry,String status){assertions++;try{cards(count,expiry,status);throw new AssertionError("invalid data accepted");}catch(IllegalArgumentException expected){}}
    static void invalidTime(Object time,String source,long snapshotTime){assertions++;try{UsageResetCards.fromValues(2,EXPIRY,"earliest",time,source,snapshotTime,NOW);throw new AssertionError("invalid time/source accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        eq("重置卡待同步 · 有效期未知",UsageResetCards.caption(null,NOW));
        eq("重置卡 可用 — 张 · 有效期未知",UsageResetCards.caption(cards(null,null,"unknown"),NOW));
        eq("重置卡 可用 0 张 · 无可用卡",UsageResetCards.caption(cards(0,null,"none"),NOW));
        eq("重置卡 可用 2 张 · 有效期未知",UsageResetCards.caption(cards(2,null,"unknown"),NOW));
        eq("重置卡 可用 2 张 · 无到期限制",UsageResetCards.caption(cards(2,null,"no_expiry"),NOW));
        eq("重置卡 可用 2 张 · 最近到期 2026/10/9 23:59",UsageResetCards.caption(cards(2,EXPIRY,"earliest"),NOW));
        eq("重置卡 可用 2 张 · 已知最近到期 2026/10/9 23:59",UsageResetCards.caption(cards(2,EXPIRY,"partial"),NOW));
        eq("重置卡 可用 2 张 · 有效期待补全",UsageResetCards.caption(cards(2,null,"partial"),NOW));
        eq("重置卡待更新 · 最近期限已到",UsageResetCards.caption(cards(2,EXPIRY,"earliest"),EXPIRY));
        eq("重置卡待更新 · 最近期限已到",UsageResetCards.caption(cards(2,EXPIRY,"partial"),EXPIRY+1));
        eq(Integer.MAX_VALUE,cards(Integer.MAX_VALUE,null,"unknown").availableCount);
        eq(3,UsageResetCards.fromValues(3,null,"unknown",NOW,"agent_report",NOW,NOW).availableCount);
        invalid(-1,null,"unknown");invalid(2147483648L,null,"unknown");invalid(1.5,null,"unknown");invalid(2.0,null,"unknown");invalid("2",null,"unknown");invalid(true,null,"unknown");
        invalid(2,0L,"earliest");invalid(2,-1L,"earliest");invalid(2,253402300800L,"earliest");invalid(2,"1791558000","earliest");invalid(2,EXPIRY+.5,"earliest");
        invalid(0,null,"unknown");invalid(0,null,"partial");invalid(null,null,"none");invalid(1,null,"none");invalid(0,EXPIRY,"none");invalid(null,null,"no_expiry");invalid(0,null,"no_expiry");invalid(1,EXPIRY,"no_expiry");
        invalid(null,EXPIRY,"earliest");invalid(0,EXPIRY,"earliest");invalid(2,null,"earliest");invalid(2,EXPIRY,"unknown");invalid(2,null,"invalid");
        invalidTime(null,UsageResetCards.SOURCE,NOW);invalidTime(0L,UsageResetCards.SOURCE,NOW);invalidTime("123",UsageResetCards.SOURCE,NOW);invalidTime(NOW+601,UsageResetCards.SOURCE,NOW+601);invalidTime(NOW,UsageResetCards.SOURCE,NOW-1);invalidTime(NOW,"wrong",NOW);
        System.out.println("PASS: "+assertions+" reset-card validation, unknown/zero, Shanghai-time and expiry assertions");
    }
}

package local.codex.wallpapers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Validated reset-card statistics and display rules, with no Android dependency. */
public final class UsageResetCards {
    static final long MAX_TIMESTAMP=253402300799L;
    static final String SOURCE="account/rateLimits/read";
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DEADLINE=DateTimeFormatter.ofPattern("yyyy/M/d HH:mm");
    final Integer availableCount;
    final Long expiresAt;
    final String expiryStatus;
    final long observedAt;

    private UsageResetCards(Integer count,Long expires,String status,long observed){
        availableCount=count;expiresAt=expires;expiryStatus=status;observedAt=observed;
    }

    public static UsageResetCards fromValues(Object countValue,Object expiresValue,String status,Object observedValue,String source,long snapshotTime,long now){
        Long count=integer(countValue,"重置卡数量");
        if(count!=null&&(count<0||count>Integer.MAX_VALUE))throw new IllegalArgumentException("重置卡数量无效");
        Long expires=integer(expiresValue,"重置卡有效期");
        if(expires!=null&&(expires<=0||expires>MAX_TIMESTAMP))throw new IllegalArgumentException("重置卡有效期无效");
        Long observed=integer(observedValue,"重置卡统计时间");
        if(observed==null||observed<=0||observed>MAX_TIMESTAMP||observed>now+600||observed>snapshotTime)throw new IllegalArgumentException("重置卡统计时间无效");
        if(!SOURCE.equals(source)&&!"agent_report".equals(source))throw new IllegalArgumentException("重置卡来源无效");
        if(!"unknown".equals(status)&&!"none".equals(status)&&!"no_expiry".equals(status)&&!"earliest".equals(status)&&!"partial".equals(status))throw new IllegalArgumentException("重置卡有效期状态无效");
        if(count!=null&&count==0&&!"none".equals(status))throw new IllegalArgumentException("零张重置卡必须使用无可用卡状态");
        if("none".equals(status)&&(count==null||count!=0||expires!=null))throw new IllegalArgumentException("重置卡空状态无效");
        if("no_expiry".equals(status)&&(count==null||count<=0||expires!=null))throw new IllegalArgumentException("重置卡无期限状态无效");
        if("earliest".equals(status)&&(count==null||count<=0||expires==null))throw new IllegalArgumentException("重置卡最近到期状态无效");
        if("unknown".equals(status)&&expires!=null)throw new IllegalArgumentException("重置卡未知期限状态无效");
        if("partial".equals(status)&&count!=null&&count==0&&expires!=null)throw new IllegalArgumentException("重置卡部分期限状态无效");
        return new UsageResetCards(count==null?null:count.intValue(),expires,status,observed);
    }

    private static Long integer(Object value,String label){
        if(value==null)return null;
        // Reject coercions from strings, booleans and fractional JSON values.
        if(!(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long))throw new IllegalArgumentException(label+"必须是整数");
        return ((Number)value).longValue();
    }

    public static String caption(UsageResetCards cards,long now){
        if(cards==null)return "重置卡待同步 · 有效期未知";
        // One card reaching its deadline invalidates the aggregate count, not every card.
        if(cards.expiresAt!=null&&cards.expiresAt<=now)return "重置卡待更新 · 最近期限已到";
        String count=cards.availableCount==null?"—":String.valueOf(cards.availableCount);
        String prefix="重置卡 可用 "+count+" 张 · ";
        if("none".equals(cards.expiryStatus))return prefix+"无可用卡";
        if("no_expiry".equals(cards.expiryStatus))return prefix+"无到期限制";
        if(cards.expiresAt!=null)return prefix+("partial".equals(cards.expiryStatus)?"已知最近到期 ":"最近到期 ")+Instant.ofEpochSecond(cards.expiresAt).atZone(ZONE).format(DEADLINE);
        return prefix+("partial".equals(cards.expiryStatus)?"有效期待补全":"有效期未知");
    }
}

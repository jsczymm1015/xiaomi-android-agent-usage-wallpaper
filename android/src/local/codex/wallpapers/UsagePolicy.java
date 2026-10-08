package local.codex.wallpapers;

import java.util.List;
import java.util.regex.*;

/** Pure rules shared by the widget and the phone-page reader. */
public final class UsagePolicy {
    public static int quotaColor(Double value) {
        if(value==null||!Double.isFinite(value)||value<0||value>100)return 0xff8b969e;
        return value>=80?0xff74df91:value>=60?0xff75cfe0:value>=40?0xffffd45c:value>=20?0xffb88658:0xffff6262;
    }
    public static String mood(Double remaining) {
        if(remaining==null || !Double.isFinite(remaining) || remaining<0 || remaining>100) return "medium";
        return remaining>=80 ? "high" : remaining>=40 ? "medium" : "low";
    }
    public static Double phoneWeeklyRemaining(List<String> labels) {
        // Both exact headings are required: a chat mentioning a percentage is not a usage page.
        boolean page=labels.contains("用量与限额") || labels.contains("Usage & limits") || labels.contains("Usage and limits");
        boolean section=labels.contains("智能体使用限额") || labels.contains("Agent usage limits");
        if(!page || !section) return null;
        Pattern pattern=Pattern.compile("(?:剩余\\s*(\\d+(?:\\.\\d+)?)\\s*%|(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:remaining|left))",Pattern.CASE_INSENSITIVE);
        for(int i=0;i<labels.size();i++) {
            String label=labels.get(i);
            if(!label.equals("每周使用限额") && !label.equals("Weekly usage limit") && !label.equals("Weekly limit")) continue;
            for(int j=i+1;j<Math.min(labels.size(),i+4);j++) {
                Matcher match=pattern.matcher(labels.get(j));
                if(match.matches()) {double value=Double.parseDouble(match.group(1)!=null?match.group(1):match.group(2));return value>=0&&value<=100?value:null;}
            }
        }
        return null;
    }
}

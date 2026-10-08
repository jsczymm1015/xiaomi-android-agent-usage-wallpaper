package local.codex.wallpapers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;

/** Portable endpoint rules. Never include credentials in URLs or diagnostic messages. */
public final class UsageEndpoint {
    public static final String NONE="none", BEARER="bearer", PRIVATE_TOKEN="private_token", TOKEN="token";
    final String mode,owner,repo,ref,file,url,auth;
    private UsageEndpoint(String mode,String owner,String repo,String ref,String file,String url,String auth){
        this.mode=mode;this.owner=owner;this.repo=repo;this.ref=ref;this.file=file;this.url=url;this.auth=auth;
    }
    public static UsageEndpoint github(String owner,String repo,String ref,String file,String auth){
        owner=clean(owner);repo=clean(repo);ref=clean(ref);file=clean(file);validateAuth(auth);
        if(!owner.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}")||!repo.matches("[A-Za-z0-9_.-]{1,100}")||repo.equals(".")||repo.equals(".."))throw invalid("GitHub 用户/组织或仓库名无效");
        if(ref.isEmpty())ref="main";if(file.isEmpty())file="usage-latest.json";
        if(ref.length()>255||!ref.matches("[A-Za-z0-9._/-]+")||ref.contains("..")||ref.startsWith("/")||ref.endsWith("/")||ref.contains("//"))throw invalid("分支或提交引用无效");
        if(file.length()>1024||file.startsWith("/")||file.endsWith("/")||file.indexOf('\\')>=0)throw invalid("JSON 文件路径无效");
        StringBuilder encoded=new StringBuilder();
        for(String segment:file.split("/",-1)){
            if(segment.isEmpty()||segment.equals(".")||segment.equals("..")||hasControl(segment))throw invalid("JSON 文件路径无效");
            if(encoded.length()>0)encoded.append('/');encoded.append(encode(segment));
        }
        if(!NONE.equals(auth)&&!BEARER.equals(auth))throw invalid("GitHub 请使用无认证或 Bearer 只读令牌");
        String endpoint="https://api.github.com/repos/"+owner+"/"+repo+"/contents/"+encoded+"?ref="+encode(ref);
        return new UsageEndpoint("github",owner,repo,ref,file,endpoint,auth);
    }
    public static UsageEndpoint https(String rawUrl,String auth){
        validateAuth(auth);String url=clean(rawUrl);
        try{
            URI parsed=new URI(url);
            if(!"https".equalsIgnoreCase(parsed.getScheme())||parsed.getHost()==null||parsed.getHost().isEmpty()||parsed.getRawUserInfo()!=null||parsed.getRawFragment()!=null||parsed.getPort()>65535||parsed.getPort()==0||hasControl(url)||url.length()>4096)throw invalid("请填写不含令牌、用户信息和片段的 HTTPS 原始 JSON 地址");
            String query=parsed.getRawQuery();
            if(query!=null)for(String part:query.split("&")){
                String key;try{key=URLDecoder.decode(part.split("=",2)[0],"UTF-8").toLowerCase(java.util.Locale.ROOT).replace('-','_');}catch(Exception invalidQuery){throw invalid("HTTPS 地址的查询参数无效");}
                if(key.contains("token")||key.contains("password")||key.contains("secret")||key.contains("signature")||key.contains("credential")||key.equals("key")||key.equals("api_key")||key.equals("apikey")||key.equals("sig")||key.equals("auth")||key.equals("authorization"))throw invalid("令牌只能填写在授权输入框，不能放入 URL");
            }
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw invalid("HTTPS 原始 JSON 地址无效");}
        return new UsageEndpoint("https","","","","",url,auth);
    }
    public String requestUrl(){return url;}
    public String headerName(){return PRIVATE_TOKEN.equals(auth)?"PRIVATE-TOKEN":NONE.equals(auth)?null:"Authorization";}
    public String headerValue(String value){
        if(NONE.equals(auth))return null;
        value=clean(value);if(value.isEmpty()||value.length()>4096||hasControl(value)||value.matches(".*\\s+.*"))throw invalid("只读令牌为空或格式无效");
        return PRIVATE_TOKEN.equals(auth)?value:(TOKEN.equals(auth)?"token ":"Bearer ")+value;
    }
    public boolean github(){return "github".equals(mode);}
    public boolean sameSource(UsageEndpoint other){return other!=null&&url.equals(other.url)&&auth.equals(other.auth);}
    private static void validateAuth(String auth){if(!NONE.equals(auth)&&!BEARER.equals(auth)&&!PRIVATE_TOKEN.equals(auth)&&!TOKEN.equals(auth))throw invalid("不支持的授权方式");}
    private static boolean hasControl(String value){for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return true;return false;}
    private static String clean(String value){return value==null?"":value.trim();}
    private static String encode(String value){try{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}catch(Exception impossible){throw new IllegalStateException("UTF-8 unavailable");}}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}

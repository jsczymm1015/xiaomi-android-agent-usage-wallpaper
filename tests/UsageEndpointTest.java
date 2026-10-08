package local.codex.wallpapers;
import java.util.Objects;

public class UsageEndpointTest {
    static int assertions;
    interface Action {void run();}
    static void eq(Object expected,Object actual){assertions++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    static void invalid(Action action){assertions++;try{action.run();throw new AssertionError("unsafe configuration accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        UsageEndpoint publicRepo=UsageEndpoint.github("example-user","usage-data","","",UsageEndpoint.NONE);
        eq("https://api.github.com/repos/example-user/usage-data/contents/usage-latest.json?ref=main",publicRepo.requestUrl());eq(null,publicRepo.headerName());eq(null,publicRepo.headerValue(""));
        UsageEndpoint github=UsageEndpoint.github("demo","stats.repo","metrics/latest","data/用量 latest.json",UsageEndpoint.BEARER);
        eq("https://api.github.com/repos/demo/stats.repo/contents/data/%E7%94%A8%E9%87%8F%20latest.json?ref=metrics%2Flatest",github.requestUrl());
        eq("Authorization",github.headerName());eq("Bearer test-only",github.headerValue("test-only"));
        UsageEndpoint gitlab=UsageEndpoint.https("https://gitlab.example/api/v4/projects/1/repository/files/usage.json/raw?ref=main",UsageEndpoint.PRIVATE_TOKEN);
        eq("PRIVATE-TOKEN",gitlab.headerName());eq("test-only",gitlab.headerValue("test-only"));
        UsageEndpoint gitea=UsageEndpoint.https("https://git.example/raw/main/usage.json",UsageEndpoint.TOKEN);eq("Authorization",gitea.headerName());eq("token test-only",gitea.headerValue("test-only"));
        eq(true,gitea.sameSource(UsageEndpoint.https(gitea.requestUrl(),UsageEndpoint.TOKEN)));eq(false,gitea.sameSource(UsageEndpoint.https(gitea.requestUrl(),UsageEndpoint.NONE)));
        invalid(()->UsageEndpoint.github("","repo","main","usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo/other","repo","main","usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo","..","main","usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo","repo","../main","usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo","repo","main?ref=evil","usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo","repo","main","../usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo","repo","main","a//usage.json",UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.github("demo","repo","main","usage.json",UsageEndpoint.PRIVATE_TOKEN));
        for(String url:new String[]{"http://example.org/usage.json","ftp://example.org/usage.json","https://user:pass@example.org/usage.json","https://example.org/usage.json#token","https://example.org:70000/usage.json","https://example.org:0/usage.json","https:///usage.json","https://example.org/usage.json?token=test-only","https://example.org/usage.json?%61ccess_token=test-only","https://example.org/usage.json?api-key=test-only","https://example.org/usage.json?X-Amz-Signature=test-only","https://example.org/usage.json?credential=test-only","https://example.org/usage.json\nX-Test:value"})invalid(()->UsageEndpoint.https(url,UsageEndpoint.NONE));
        invalid(()->UsageEndpoint.https("https://example.org/usage.json","Cookie"));
        for(String token:new String[]{"","one two","test\nInjected:value","test\rInjected:value","test\u0000bad"})invalid(()->github.headerValue(token));
        System.out.println("PASS: "+assertions+" endpoint, URL and authorization-header assertions");
    }
}

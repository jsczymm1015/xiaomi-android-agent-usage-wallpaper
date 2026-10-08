package local.codex.wallpapers;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

/** User-selected repository and read-only authorization. No account is built into the APK. */
public class UsageCloudActivity extends Activity {
    TextView status;Button save,force,stop;EditText token,owner,repo,ref,file,url;
    Spinner kind,auth;LinearLayout githubFields,httpsFields;
    static final String[] AUTH={UsageEndpoint.NONE,UsageEndpoint.BEARER,UsageEndpoint.PRIVATE_TOKEN,UsageEndpoint.TOKEN};
    public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(32,60,32,32);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        TechUi.header(root,"CLOUD LINK / 03","自动同步","自己的仓库 · 统一用量 JSON");
        TextView cadence=new TextView(this);cadence.setText("目标每 10 分钟拉取；系统省电策略可能延迟。上传时间由电脑端定时任务自行设置。");root.addView(cadence);TechUi.panel(cadence);
        TechUi.section(root,"01  /  数据源");kind=spinner(root,new String[]{"GitHub 仓库","HTTPS 原始 JSON"});
        githubFields=new LinearLayout(this);githubFields.setOrientation(1);root.addView(githubFields);
        owner=field(githubFields,"用户或组织",false);repo=field(githubFields,"仓库名称",false);ref=field(githubFields,"分支或提交（默认 main）",false);file=field(githubFields,"JSON 文件路径（默认 usage-latest.json）",false);
        httpsFields=new LinearLayout(this);httpsFields.setOrientation(1);root.addView(httpsFields);url=field(httpsFields,"HTTPS 原始 JSON 地址",false);url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        TechUi.section(root,"02  /  只读授权");auth=spinner(root,new String[]{"无认证（公开 JSON）","Bearer（GitHub / 通用）","PRIVATE-TOKEN（GitLab）","token（Gitea）"});
        token=field(root,"只读令牌；不会回显已保存的令牌",true);
        TextView help=new TextView(this);help.setText("令牌仅在本机加密保存，并发送到你填写的 HTTPS 数据源。GitHub 私库使用细粒度令牌，仅选择自己的数据仓库并授予 Contents: Read-only；公开 JSON 可选无认证。其他服务自行创建只读令牌。请勿把令牌放在 URL、代码或聊天中。");root.addView(help);
        Button create=new Button(this);create.setText("打开 GitHub 细粒度令牌设置");create.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/settings/personal-access-tokens"))));root.addView(create);
        TextView replacement=new TextView(this);replacement.setText("保存配置将清除旧用量快照及版本缓存，避免不同账号的数据混用；人物和主题选择保留。重新保存已授权的数据源时，也需要重新输入只读令牌。");root.addView(replacement);TechUi.panel(replacement);
        save=new Button(this);save.setText("保存配置并启用自动同步");save.setOnClickListener(v->saveConfiguration());root.addView(save);
        TechUi.section(root,"03  /  同步控制");force=new Button(this);force.setText("立即强制同步");force.setOnClickListener(v->sync());root.addView(force);
        stop=new Button(this);stop.setText("停止自动同步并清除配置");stop.setOnClickListener(v->{UsageCloud.stop(this);token.setText("");status.setText("已停止自动同步并清除配置及授权；最后一次统计保留至重新配置");});root.addView(stop);
        status=new TextView(this);status.setText(UsageCloud.status(this));root.addView(status);TechUi.panel(status);
        UsageEndpoint configured=UsageCloud.endpoint(this);
        if(configured!=null){kind.setSelection(configured.github()?0:1);owner.setText(configured.owner);repo.setText(configured.repo);ref.setText(configured.ref);file.setText(configured.file);url.setText(configured.github()?"":configured.url);for(int i=0;i<AUTH.length;i++)if(AUTH[i].equals(configured.auth))auth.setSelection(i);}
        kind.setOnItemSelectedListener(new Selection(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){fields();}});
        auth.setOnItemSelectedListener(new Selection(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){fields();}});
        fields();TechUi.apply(this,root);UsageCloud.schedule(this);
    }
    abstract static class Selection implements AdapterView.OnItemSelectedListener {public void onNothingSelected(AdapterView<?> p){}}
    Spinner spinner(LinearLayout root,String[] items){Spinner view=new Spinner(this);view.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,items));root.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
    EditText field(LinearLayout root,String hint,boolean secret){EditText edit=new EditText(this);edit.setHint(hint);edit.setSingleLine(true);edit.setInputType(secret?129:android.text.InputType.TYPE_CLASS_TEXT);edit.setSaveEnabled(false);edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);root.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;}
    void fields(){githubFields.setVisibility(kind.getSelectedItemPosition()==0?View.VISIBLE:View.GONE);httpsFields.setVisibility(kind.getSelectedItemPosition()==1?View.VISIBLE:View.GONE);boolean needed=auth.getSelectedItemPosition()!=0;token.setVisibility(needed?View.VISIBLE:View.GONE);if(!needed)token.setText("");}
    void saveConfiguration(){
        UsageEndpoint endpoint;
        try{String authorization=AUTH[auth.getSelectedItemPosition()];endpoint=kind.getSelectedItemPosition()==0?UsageEndpoint.github(owner.getText().toString(),repo.getText().toString(),ref.getText().toString(),file.getText().toString(),authorization):UsageEndpoint.https(url.getText().toString(),authorization);endpoint.headerValue(token.getText().toString());}
        catch(IllegalArgumentException invalid){status.setText(invalid.getMessage());return;}
        try{UsageCloud.save(this,endpoint,token.getText().toString());token.setText("");sync();}catch(Exception e){status.setText("配置或授权保存失败，请重试");}
    }
    void sync(){status.setText("正在读取已配置数据源…");save.setEnabled(false);force.setEnabled(false);stop.setEnabled(false);new Thread(()->{try{UsageCloud.sync(this,true);}catch(Exception e){UsageCloud.state(this,UsageCloud.failure(e));}runOnUiThread(()->{if(isFinishing())return;status.setText(UsageCloud.status(this));save.setEnabled(true);force.setEnabled(true);stop.setEnabled(true);});},"usage-manual-sync").start();}
}

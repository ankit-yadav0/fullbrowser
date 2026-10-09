import java.net.*;
import java.util.*;
import java.util.regex.*;

public class AlgorithmCrossCheck {
  // ---- UrlSafety port ----
  record Parsed(String scheme, String host, int port, boolean ipv6, boolean userInfo) {
    String origin() { String h = ipv6 ? "["+host+"]" : host; int d = defaultPort(scheme);
      return (port == -1 || port == d) ? scheme+"://"+h : scheme+"://"+h+":"+port; } }
  static int defaultPort(String s){ return s.equals("https")?443: s.equals("http")?80:-1; }
  static Parsed parse(String url){
    if (url==null) return null; String s=url.trim();
    if (s.isEmpty()||s.length()>8192) return null;
    for (char c: s.toCharArray()) if (c<=0x20||c==0x7f) return null;
    int se=s.indexOf("://"); if (se<=0) return null;
    String scheme=s.substring(0,se).toLowerCase(Locale.ROOT);
    if(!Character.isLetter(scheme.charAt(0))) return null;
    for(char c: scheme.toCharArray()) if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='+'||c=='-'||c=='.')) return null;
    String rest=s.substring(se+3);
    int ae=-1; for(int i=0;i<rest.length();i++){char c=rest.charAt(i); if(c=='/'||c=='?'||c=='#'||c=='\\'){ae=i;break;}}
    String authority= ae<0?rest:rest.substring(0,ae);
    if(authority.isEmpty()) return null;
    int at=authority.lastIndexOf('@'); boolean ui=at>=0;
    String hp= ui?authority.substring(at+1):authority; if(hp.isEmpty()) return null;
    String rawHost, portText; boolean ipv6=false;
    if(hp.startsWith("[")){ int close=hp.indexOf(']'); if(close<0) return null;
      rawHost=hp.substring(1,close); String after=hp.substring(close+1);
      if(after.isEmpty()) portText=""; else if(after.startsWith(":")) portText=after.substring(1); else return null; ipv6=true;
    } else { int colon=hp.lastIndexOf(':'); if(colon>=0){rawHost=hp.substring(0,colon);portText=hp.substring(colon+1);} else {rawHost=hp;portText="";} }
    int port;
    if(portText.isEmpty()){ if(hp.endsWith(":")&&!ipv6) return null; port=defaultPort(scheme); }
    else { if(portText.length()>5) return null; for(char c: portText.toCharArray()) if(c<'0'||c>'9') return null;
      int p=Integer.parseInt(portText); if(p<1||p>65535) return null; port=p; }
    String host = ipv6 ? normIpv6(rawHost) : normHost(rawHost);
    if(host==null) return null;
    return new Parsed(scheme,host,port,ipv6,ui);
  }
  static String normIpv6(String raw){
    long colons=raw.chars().filter(c->c==':').count(); if(colons<2) return null;
    for(char c: raw.toCharArray()) if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')||(c>='A'&&c<='F')||c==':'||c=='.')) return null;
    return raw.toLowerCase(Locale.ROOT);
  }
  static String normHost(String raw){
    if(raw==null||raw.isEmpty()) return null; String h=raw.toLowerCase(Locale.ROOT);
    if(h.endsWith(".")) h=h.substring(0,h.length()-1);
    if(h.isEmpty()||h.length()>253) return null;
    boolean nonAscii=false; for(char c: h.toCharArray()) if(c>127) nonAscii=true;
    if(nonAscii){ try{ h=IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);}catch(IllegalArgumentException e){return null;} }
    if(h.startsWith(".")||h.contains("..")) return null;
    for(char c: h.toCharArray()) if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='.'||c=='_')) return null;
    return h;
  }
  static String normObserved(String host){
    if(host==null||host.isBlank()) return null; String h=host.trim();
    if(h.startsWith("[")) h=h.substring(1); if(h.endsWith("]")) h=h.substring(0,h.length()-1);
    return h.contains(":")?normIpv6(h):normHost(h);
  }
  static final Pattern NUM=Pattern.compile("0x[0-9a-f]*|[0-9]+");
  static boolean isPrivate(String host){
    String h=normObserved(host); if(h==null) return false;
    if(h.contains(":")) return privIpv6(h);
    if(h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.endsWith(".localdomain")||h.endsWith(".home.arpa")) return true;
    String[] labels=h.split("\\.",-1);
    boolean allNum=true; for(String l: labels) if(!NUM.matcher(l).matches()) allNum=false;
    if(allNum){ int[] o=quad(labels); if(o==null) return true; return privV4(o); }
    return false;
  }
  static int[] quad(String[] l){ if(l.length!=4) return null; int[] o=new int[4];
    for(int i=0;i<4;i++){ String x=l[i]; if(x.isEmpty()||x.length()>3) return null; for(char c: x.toCharArray()) if(c<'0'||c>'9') return null;
      if(x.length()>1&&x.charAt(0)=='0') return null; int v=Integer.parseInt(x); if(v>255) return null; o[i]=v;} return o; }
  static boolean privV4(int[] o){ int a=o[0],b=o[1];
    return a==0||a==10||a==127||a>=224||(a==169&&b==254)||(a==172&&b>=16&&b<=31)||(a==192&&b==168)||(a==192&&b==0&&o[2]==0)||(a==100&&b>=64&&b<=127); }
  static boolean privIpv6(String h){
    InetAddress addr; try{ addr=InetAddress.getByName(h);}catch(Exception e){return true;}
    byte[] b=addr.getAddress();
    if(b.length==4){ int[] o=new int[4]; for(int i=0;i<4;i++) o[i]=b[i]&0xff; return privV4(o); }
    int first=b[0]&0xff;
    boolean nat64 = b[0]==0 && b[1]==0x64 && (b[2]&0xff)==0xff && (b[3]&0xff)==0x9b;
    return addr.isAnyLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress()||addr.isSiteLocalAddress()||addr.isMulticastAddress()||((first&0xfe)==0xfc)||nat64;
  }
  // ---- NavigationPolicy port ----
  static boolean isWeb(String u){ Parsed p=parse(u); return p!=null&&(p.scheme.equals("https")||p.scheme.equals("http")); }
  static String loadable(String url){ if(url==null) return null; String t=url.trim(); Parsed p=parse(t); if(p==null||p.userInfo) return null;
    if(p.scheme.equals("https")) return t; if(p.scheme.equals("http")) return "https"+t.substring(4); return null; }
  static String decide(String url, boolean gesture){ // returns ALLOW / BLOCK / EXT:<url> / LOAD:<url>
    String t= url==null?"":url.trim(); if(t.isEmpty()) return "BLOCK"; String lower=t.toLowerCase(Locale.ROOT);
    if(lower.startsWith("https://")){ Parsed p=parse(t); return (p==null||p.userInfo)?"BLOCK":"ALLOW"; }
    if(lower.startsWith("http://")) return "BLOCK";
    if(lower.startsWith("blob:https://")) return "ALLOW";
    if(lower.equals("about:blank")) return "ALLOW";
    if(lower.startsWith("intent:")){ if(!gesture) return "BLOCK"; String fb=fallback(t); if(fb==null) return "BLOCK";
      String l=loadable(fb); return (l!=null&&l.toLowerCase(Locale.ROOT).startsWith("https://"))?"LOAD:"+l:"BLOCK"; }
    int i=lower.indexOf(':'); String scheme= i<0?"":lower.substring(0,i);
    if(!Set.of("mailto","tel","sms","smsto").contains(scheme)) return "BLOCK";
    if(scheme.equals("mailto") && mailtoAttach(t)) return "BLOCK";
    return gesture?"EXT:"+t:"BLOCK";
  }
  static boolean mailtoAttach(String url){ int q=url.indexOf('?'); String query= q<0?"":url.substring(q+1); int h=query.indexOf('#'); if(h>=0) query=query.substring(0,h);
    for(String part: query.split("&",-1)){ String k=part.split("=",-1)[0].trim().toLowerCase(Locale.ROOT); if(k.equals("attach")||k.equals("attachment")||k.equals("attachments")) return true; } return false; }
  static String fallback(String url){ String m="#intent;"; int idx=url.toLowerCase(Locale.ROOT).indexOf(m); if(idx<0) return null;
    String[] parts=url.substring(idx+m.length()).split(";"); String key="S.browser_fallback_url=";
    for(String p: parts) if(p.startsWith(key)){ try{ String d=URLDecoder.decode(p.substring(key.length()),"UTF-8"); return d.isBlank()?null:d;}catch(Exception e){return null;} }
    return null; }
  static boolean probe(String reqHost, String pageHost){ return isPrivate(reqHost) && !isPrivate(pageHost); }
  // ---- DownloadPolicy port ----
  static Parsed validate(String url){ Parsed p=parse(url); if(p==null) throw new IllegalArgumentException("malformed");
    if(!p.scheme.equals("https")) throw new IllegalArgumentException("scheme"); if(p.userInfo) throw new IllegalArgumentException("userinfo");
    if(isPrivate(p.host)) throw new IllegalArgumentException("private"); return p; }
  static String nextHop(String cur, String loc){ if(loc==null||loc.isBlank()) throw new IllegalArgumentException("nolocation");
    String r; try{ r=new URI(cur).resolve(new URI(loc.trim())).toString(); }catch(Exception e){ throw new IllegalArgumentException("malformed redirect"); }
    validate(r); return r; }
  static boolean creds(String a,String b){ Parsed x=parse(a),y=parse(b); if(x==null||y==null) return false; return x.origin().equals(y.origin()); }
  static String referer(String ref,String hop){ Parsed r=parse(ref),h=parse(hop); if(r==null||h==null) return null;
    if(!r.scheme.equals("https")||r.userInfo||!r.origin().equals(h.origin())) return null; return ref.trim(); }
  static final Pattern UNSAFE=Pattern.compile("[\\u0000-\\u001F\\u007F\\\\/:*?\"<>|]");
  static final Pattern BIDI=Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
  static String safeName(String v){ String c=(v==null?"":v); c=UNSAFE.matcher(c).replaceAll("_"); c=BIDI.matcher(c).replaceAll("_"); c=c.trim();
    int s=0,e=c.length(); while(s<e&&(c.charAt(s)=='.'||c.charAt(s)==' ')) s++; while(e>s&&(c.charAt(e-1)=='.'||c.charAt(e-1)==' ')) e--; c=c.substring(s,e);
    boolean allU=!c.isEmpty(); for(char ch: c.toCharArray()) if(ch!='_') allU=false;
    if(c.isBlank()||allU) return "download"; return c.length()<=180?c:c.substring(0,180); }
  static String cd(String h){ if(h==null||h.isBlank()) return null;
    Matcher m=Pattern.compile("filename\\*\\s*=\\s*UTF-8''([^;]+)",Pattern.CASE_INSENSITIVE).matcher(h);
    if(m.find()){ try{ return URLDecoder.decode(m.group(1).trim().replace("+","%2B"),"UTF-8"); }catch(Exception e){} }
    Matcher m2=Pattern.compile("filename\\s*=\\s*\"?([^;\"]+)",Pattern.CASE_INSENSITIVE).matcher(h);
    return m2.find()?m2.group(1).trim():null; }
  static String mime(String... cs){ for(String c: cs){ if(c==null) continue; String b=c.split(";",-1)[0].trim().toLowerCase(Locale.ROOT);
      if(Pattern.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+",b)) return b; } return "application/octet-stream"; }
  // ---- UrlHelper.normalizeUrl port ----
  static String norm(String input){ String t=input.trim(); if(t.isEmpty()) return "";
    String lo=t.toLowerCase(Locale.ROOT); if(lo.startsWith("http://")||lo.startsWith("https://")) return t;
    if(t.contains("://")) return search(t);
    String cand="https://"+t; Parsed p=parse(cand);
    boolean ok= p!=null&&!p.userInfo&&(p.host.equals("localhost")||p.host.contains(".")||p.ipv6);
    return (!t.contains(" ")&&ok)?cand:search(t); }
  static String search(String q){ try{ return "https://www.google.com/search?q="+URLEncoder.encode(q,"UTF-8"); }catch(Exception e){throw new RuntimeException(e);} }

  // ---- PermissionPolicy port ----
  static boolean embedded(String page,String origin){ Parsed p=parse(page),o=parse(origin); if(p==null||o==null) return false; return !p.host().equals(o.host()); }
  static String permission(String origin){ Parsed o=parse(origin); return (o==null||!o.scheme().equals("https")||o.userInfo())?"DENY (insecure/invalid origin)":"PROMPT"; }
  static String cell(String s){ return s.replace("|","\\|"); }
  static void table(){
    String[] in={"https://example.com","http://example.com","https://münchen.de","https://xn--mnchen-3ya.de","https://localhost","https://localhost:3000","https://192.168.1.1","https://10.0.0.1","https://169.254.169.254","https://[::1]","https://[fe80::1]","https://[2001:db8::1]:8443","https://example.com:99999","https://example.com:8443","https://user@example.com","https://2130706433","javascript:alert(1)","data:text/html,hi","blob:https://example.com/id","file:///sdcard/a","content://com.x/y","intent://x#Intent;S.browser_fallback_url=https%3A%2F%2Fexample.com;end","intent://x#Intent;package=com.evil;end","mailto:a@b.com","tel:+123","sms:+123","market://details?id=x"};
    System.out.println("| Input | Home normalizeUrl | initial load (toLoadableUrl) | main-frame nav, gesture | main-frame nav, no gesture | download hop | permission origin |");
    System.out.println("|---|---|---|---|---|---|---|");
    for(String u: in){
      String dl; try{ validate(u); dl="allowed"; }catch(IllegalArgumentException e){ dl="rejected ("+e.getMessage()+")"; }
      String ld=loadable(u);
      System.out.println("| `"+cell(u)+"` | `"+cell(norm(u).length()>60?norm(u).substring(0,57)+"...":norm(u))+"` | "+(ld==null?"rejected":"`"+cell(ld)+"`")+" | "+cell(decide(u,true))+" | "+cell(decide(u,false))+" | "+dl+" | "+permission(u)+" |");
    }
  }
  // ---- mini assert harness ----
  static int pass=0, fail=0;
  static void eq(Object exp,Object act,String msg){ if(Objects.equals(exp,act)) pass++; else { fail++; System.out.println("FAIL "+msg+": expected <"+exp+"> got <"+act+">"); } }
  static void t(boolean c,String msg){ if(c) pass++; else { fail++; System.out.println("FAIL "+msg); } }
  static void f(boolean c,String msg){ t(!c,msg); }
  static void rejects(String u){ try{ validate(u); fail++; System.out.println("FAIL expected rejection "+u);}catch(IllegalArgumentException e){pass++;} }
  static void rejectsHop(String cur,String loc){ try{ nextHop(cur,loc); fail++; System.out.println("FAIL expected redirect rejection '"+loc+"'");}catch(IllegalArgumentException e){pass++;} }

  public static void main(String[] a){
    // UrlSafetyTest
    Parsed p=parse("https://Example.COM/path?q=1#f"); eq("example.com",p.host(),"host"); eq(443,p.port(),"port"); eq("https://example.com",p.origin(),"origin");
    eq("https://a.com",parse("https://a.com:443/").origin(),"o443"); eq("https://a.com:8443",parse("https://a.com:8443/").origin(),"o8443"); eq("http://a.com",parse("http://a.com:80").origin(),"o80");
    for(String u: new String[]{"https://a.com:0/","https://a.com:65536/","https://a.com:99999999/","https://a.com:80a/","https://a.com:/"}) t(parse(u)==null,"badport "+u);
    t(parse("https://google.com@evil.com/").userInfo(),"userinfo"); eq("evil.com",parse("https://google.com@evil.com/").host(),"uihost"); eq("good.com",parse("https://good.com\\@evil.com/").host(),"backslash");
    for(String u: new String[]{"","example.com","https://","https:// example.com","https://exa\u0000mple.com","https://exa%6Dple.com","https://a..com","1http://a.com"}) t(parse(u)==null,"malformed ["+u+"]");
    t(parse(null)==null,"null");
    eq("xn--mnchen-3ya.de",parse("https://münchen.de/").host(),"idn");
    Parsed v6=parse("https://[2001:DB8::1]:8443/x"); t(v6.ipv6(),"v6"); eq("2001:db8::1",v6.host(),"v6host"); eq(8443,v6.port(),"v6port"); eq("https://[2001:db8::1]:8443",v6.origin(),"v6origin");
    t(parse("https://[::1/")==null,"v6 unclosed"); t(parse("https://[zz::1]/")==null,"v6 bad"); t(parse("https://[::1]x/")==null,"v6 trailing");
    eq("example.com",parse("https://example.com./").host(),"trailing dot");
    for(String h: new String[]{"localhost","foo.localhost","printer.local","x.internal","127.0.0.1","127.1.2.3","10.0.0.1","172.16.0.1","172.31.255.255","192.168.1.1","169.254.169.254","0.0.0.0","100.64.0.1","224.0.0.1","255.255.255.255"}) t(isPrivate(h),"private "+h);
    for(String h: new String[]{"8.8.8.8","1.1.1.1","172.15.0.1","172.32.0.1","100.63.0.1","100.128.0.1","example.com","1e100.net"}) f(isPrivate(h),"public "+h);
    for(String h: new String[]{"2130706433","0x7f.1","127.1","0177.0.0.1","999.1.1.1","1.2.3"}) t(isPrivate(h),"ambiguous "+h);
    for(String h: new String[]{"::1","[::1]","fe80::1","fd00::1","fc00::1","::","ff02::1","::ffff:127.0.0.1","::ffff:10.0.0.1","64:ff9b::7f00:1","0:0:0:0:0:0:0:1"}) t(isPrivate(h),"v6private "+h);
    f(isPrivate("2001:4860:4860::8888"),"v6 public"); f(isPrivate("::ffff:8.8.8.8"),"mapped public"); f(isPrivate(null),"null host"); f(isPrivate(""),"empty host");
    eq("example.com",normObserved("EXAMPLE.com."),"obs"); eq("::1",normObserved("[::1]"),"obs6"); t(normObserved(null)==null,"obsnull"); t(normObserved("münchen.de")!=null,"obsidn");
    // NavigationPolicyTest
    eq("ALLOW",decide("https://example.com/a",false),"https allow"); eq("BLOCK",decide("http://example.com/",true),"http block"); eq("BLOCK",decide("HTTP://EXAMPLE.COM/",true),"HTTP block");
    for(String u: new String[]{"https://google.com@evil.com/","https://","https://a.com:99999/"}) eq("BLOCK",decide(u,true),"bad https "+u);
    for(String u: new String[]{"javascript:alert(1)","file:///sdcard/a","content://x/y","data:text/html,hi","ftp://a.com/","market://details?id=x","whatsapp://send","tg://resolve","vbscript:x","view-source:https://a.com","about:config","chrome://settings","android-app://com.x"}) eq("BLOCK",decide(u,true),"dangerous "+u);
    eq("ALLOW",decide("blob:https://a.com/uuid",false),"blob https"); eq("ALLOW",decide("about:blank",false),"about:blank"); eq("BLOCK",decide("blob:http://a.com/uuid",true),"blob http"); eq("BLOCK",decide("blob:null/uuid",true),"blob null");
    eq("BLOCK",decide("mailto:a@b.com?attach=file:///sdcard/secret.txt",true),"mailto attach"); eq("BLOCK",decide("MAILTO:a@b.com?subject=x&Attachment=content://x/y",true),"mailto Attachment"); eq("BLOCK",decide("mailto:a@b.com?attachments=file:///x",true),"mailto attachments");
    t(decide("mailto:a@b.com?subject=Hi&body=please%20attach%20the%20file",true).startsWith("EXT:"),"mailto body ok");
    t(decide("mailto:a@b.com",true).startsWith("EXT:"),"mailto"); t(decide("tel:+123",true).startsWith("EXT:"),"tel"); t(decide("sms:+123",true).startsWith("EXT:"),"sms");
    eq("BLOCK",decide("mailto:a@b.com",false),"mailto nogesture"); eq("BLOCK",decide("tel:+123",false),"tel nogesture");
    String wf="intent://x.com/p#Intent;scheme=https;package=com.evil;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fok;end";
    eq("LOAD:https://example.com/ok",decide(wf,true),"intent fallback"); eq("BLOCK",decide(wf,false),"intent nogesture");
    eq("BLOCK",decide("intent://x.com/p#Intent;scheme=https;package=com.evil;end",true),"intent nofb");
    eq("LOAD:https://example.com",decide("intent://x#Intent;S.browser_fallback_url=http%3A%2F%2Fexample.com;end",true),"intent http fb upgraded");
    eq("BLOCK",decide("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end",true),"intent js fb");
    eq("BLOCK",decide("intent://x#Intent;S.browser_fallback_url=https%3A%2F%2Fa%40evil.com;end",true),"intent userinfo fb");
    eq("https://example.com/a",loadable("http://example.com/a"),"upgrade"); eq("https://EXAMPLE.com/a",loadable("HTTP://EXAMPLE.com/a"),"upgrade case"); eq("https://example.com",loadable(" https://example.com "),"trim");
    for(String u: new String[]{"javascript:alert(1)","file:///x","https://a@evil.com",null,""}) t(loadable(u)==null,"loadable null "+u);
    t(isWeb("https://a.com"),"isWeb"); t(isWeb("http://a.com"),"isWeb http"); f(isWeb("about:blank"),"isWeb about"); f(isWeb("data:text/html,x"),"isWeb data"); f(isWeb("chrome-error://chromewebdata/"),"isWeb chrome-error");
    t(probe("127.0.0.1","example.com"),"probe1"); t(probe("192.168.0.1","example.com"),"probe2"); t(probe("localhost","example.com"),"probe3"); t(probe("[::1]","example.com"),"probe4"); t(probe("2130706433",null),"probe5");
    f(probe("example.com","example.com"),"probe6"); f(probe("192.168.0.2","192.168.0.1"),"probe7");
    // DownloadPolicyTest
    t(validate("https://example.com/f.zip")!=null,"dl ok"); t(validate("https://cdn.example.com:8443/f.zip?x=1")!=null,"dl ok port");
    for(String u: new String[]{"http://example.com/f.zip","ftp://example.com/f.zip","file:///sdcard/f","data:text/plain,hi","blob:https://a.com/uuid","https://","","https://user:pw@example.com/f","https://192.168.1.1/router","https://10.0.0.5/x","https://localhost/x","https://127.0.0.1:8080/x","https://[::1]/x","https://2130706433/x","https://169.254.169.254/latest/meta-data"}) rejects(u);
    eq("https://a.com/z",nextHop("https://a.com/x/y","../z"),"hop rel"); eq("https://cdn.example.net/f",nextHop("https://a.com/x","//cdn.example.net/f"),"hop netpath"); eq("https://b.com/f",nextHop("https://a.com/x","https://b.com/f"),"hop abs");
    for(String l: new String[]{"http://b.com/","https://127.0.0.1/","https://192.168.0.1/","ftp://b.com/","https://u@evil.com/",""," ","https://a.com/%zz"}) rejectsHop("https://a.com/x",l);
    rejectsHop("https://a.com/x",null);
    t(creds("https://a.com/x","https://a.com/y"),"cred same"); t(creds("https://a.com:443/x","https://a.com/y"),"cred 443"); f(creds("https://a.com/x","https://cdn.a.com/y"),"cred sub"); f(creds("https://a.com/x","https://b.com/y"),"cred other"); f(creds("https://a.com/x","https://a.com:8443/y"),"cred port"); f(creds("https://a.com/x","garbage"),"cred garbage");
    eq("https://a.com/page",referer("https://a.com/page","https://a.com/file"),"ref same"); t(referer("https://a.com/page","https://b.com/file")==null,"ref cross"); t(referer("http://a.com/page","http://a.com/file")==null,"ref http"); t(referer(null,"https://a.com/file")==null,"ref null"); t(referer("https://u@a.com/p","https://a.com/file")==null,"ref ui");
    eq("download",safeName(null),"n null"); eq("download",safeName(""),"n empty"); eq("download",safeName("   "),"n blank"); eq("download",safeName(".."),"n dots"); eq("download",safeName("___"),"n us");
    eq("a_b.txt",safeName("a\u0000b.txt"),"n nul"); eq("a_b",safeName("a\\b"),"n bs"); eq("con_aux_.txt",safeName("con:aux?.txt"),"n colon"); eq("_gpj.exe",safeName("\u202Egpj.exe"),"n bidi"); eq(180,safeName("a".repeat(300)).length(),"n len");
    String tr=safeName("../../etc/passwd"); f(tr.contains("/"),"tr slash"); f(tr.contains("\\"),"tr bs"); f(tr.startsWith("."),"tr dot"); System.out.println("traversal -> "+tr);
    eq("report.pdf",cd("attachment; filename=\"report.pdf\""),"cd quoted"); eq("report.pdf",cd("attachment; filename=report.pdf"),"cd bare"); eq("na\u00EFve file.txt",cd("attachment; filename*=UTF-8''na%C3%AFve%20file.txt"),"cd rfc5987");
    eq("b.txt",cd("attachment; filename=\"a.txt\"; filename*=UTF-8''b.txt"),"cd both"); t(cd("inline")==null,"cd inline"); t(cd(null)==null,"cd null"); t(cd("  ")==null,"cd blank");
    eq("text/html",mime("text/html; charset=utf-8"),"m1"); eq("application/pdf",mime(null,"application/pdf"),"m2"); eq("image/png",mime("IMAGE/PNG"),"m3"); eq("application/octet-stream",mime("garbage",null),"m4"); eq("application/octet-stream",mime("",null),"m5"); eq("application/octet-stream",mime(),"m6");
    // UrlHelperTest + existing
    eq("https://example.com:8080",norm("example.com:8080"),"n1"); eq("https://localhost:3000",norm("localhost:3000"),"n2"); eq("https://192.168.1.1",norm("192.168.1.1"),"n3"); eq("https://1.2.3.4:8080",norm("1.2.3.4:8080"),"n4");
    eq("https://example.com#top",norm("example.com#top"),"n5"); eq("HTTP://EXAMPLE.COM",norm("HTTP://EXAMPLE.COM"),"n6");
    eq("https://[::1]:8080",norm("[::1]:8080"),"n7"); eq("https://münchen.de",norm("münchen.de"),"n8");
    for(String s: new String[]{"file:///sdcard/secret.txt","javascript:alert(1)","intent://a.b#Intent;scheme=x;end","ftp://example.com/x","data:text/html,<script>1</script>","user@example.com","example.com:99999","myserver","a b.com"}) t(norm(s).startsWith("https://www.google.com/search?q="),"search "+s+" -> "+norm(s));
    eq("https://example.com",norm("example.com"),"e1"); eq("https://google.com/search?q=test",norm("google.com/search?q=test"),"e2"); eq("https://www.google.com/search?q=kotlin+android",norm("kotlin android"),"e3"); eq("https://www.google.com/search?q=hello",norm("hello"),"e4");
    eq("http://example.com",norm("http://example.com"),"e5"); eq("https://example.com",norm("https://example.com"),"e6");
    // PermissionAndPopupPolicyTest.embeddedFrameDetectionUsesExactHostComparison
    t(embedded("https://a.com/page","https://ads.example.net"),"emb1"); f(embedded("https://a.com/page","https://a.com"),"emb2"); f(embedded("https://A.com/page","https://a.com"),"emb3");
    t(embedded("https://www.a.com/","https://a.com"),"emb4"); f(embedded(null,"https://a.com"),"emb5"); f(embedded("https://a.com","garbage"),"emb6");
    System.out.println("PORT RESULT: pass="+pass+" fail="+fail);
    if(a.length>0 && a[0].equals("--table")) table();
  }
}

package com.ajadi.keys.ai;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/** The complete user-authorized request: one command and one explicitly selected fragment. */
final class AiProtocol {
    static final int MAX_SELECTION=4000,MAX_COMMAND=1500,MAX_OUTPUT=8000,MAX_RESPONSE_BYTES=256000;
    static URI endpoint(String value){
        try{
            URI u=new URI(value.trim());
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getHost().isEmpty()
                    ||u.getRawUserInfo()!=null||u.getRawFragment()!=null||u.getRawQuery()!=null
                    ||u.getRawPath()==null||u.getRawPath().isEmpty()||"/".equals(u.getRawPath())
                    ||u.getPort()>65535||u.getPort()==0)throw new IllegalArgumentException("HTTPS endpoint required");
            return u;
        }catch(URISyntaxException|NullPointerException e){throw new IllegalArgumentException("Invalid HTTPS endpoint");}
    }
    static boolean validSelection(String text){return text!=null&&!text.trim().isEmpty()&&text.length()<=MAX_SELECTION;}
    static boolean validCommand(String text){return text!=null&&!text.trim().isEmpty()&&text.length()<=MAX_COMMAND;}
    static boolean validModel(String model){return model!=null&&!model.trim().isEmpty()&&model.length()<=160&&model.indexOf('\n')<0&&model.indexOf('\r')<0;}
    static byte[] request(String model,String command,String selection){
        if(!validModel(model)||!validCommand(command)||!validSelection(selection))throw new IllegalArgumentException("Invalid request");
        String json="{\"model\":"+quote(model.trim())+",\"stream\":false,\"messages\":[{\"role\":\"system\",\"content\":"+quote(command)+"},{\"role\":\"user\",\"content\":"+quote(selection)+"}]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
    static String quote(String value){
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '"':b.append("\\\"");break;
                case '\\':b.append("\\\\");break;
                case '\b':b.append("\\b");break;
                case '\f':b.append("\\f");break;
                case '\n':b.append("\\n");break;
                case '\r':b.append("\\r");break;
                case '\t':b.append("\\t");break;
                default:if(c<32)b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else b.append(c);
            }
        }
        return b.append('"').toString();
    }
}

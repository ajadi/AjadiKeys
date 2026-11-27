package com.ajadi.keys.ai;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;

/** Only invoked after an explicit visible confirmation. No retries, redirects, logging or background service. */
final class AiClient {
    interface ConnectionFactory {HttpsURLConnection open(URI endpoint)throws IOException;}
    private final ConnectionFactory factory;
    private volatile HttpsURLConnection active;
    private volatile boolean cancelled;
    AiClient(){this(endpoint->(HttpsURLConnection)endpoint.toURL().openConnection());}
    AiClient(ConnectionFactory factory){this.factory=factory;}
    String process(URI endpoint,String model,String command,String selected,String token)throws Exception{
        if(endpoint==null)throw new IllegalArgumentException("Missing HTTPS endpoint");
        endpoint=AiProtocol.endpoint(endpoint.toString());
        byte[] body=AiProtocol.request(model,command,selected);
        HttpsURLConnection connection=null;
        try{
            if(cancelled)throw new IOException("Cancelled");
            connection=factory.open(endpoint);active=connection;
            if(cancelled)throw new IOException("Cancelled");
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setConnectTimeout(15000);connection.setReadTimeout(45000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Connection","close");
            if(token!=null&&!token.isEmpty()){
                if(token.indexOf('\r')>=0||token.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid API token");
                connection.setRequestProperty("Authorization","Bearer "+token);
            }
            connection.setFixedLengthStreamingMode(body.length);
            if(cancelled)throw new IOException("Cancelled");
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            int status=connection.getResponseCode();
            if(status<200||status>=300)throw new HttpFailure(status);
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buf=new byte[8192];int count;
                while((count=in.read(buf))!=-1){
                    if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
                    if(data.size()+count>AiProtocol.MAX_RESPONSE_BYTES)throw new IOException("Response too large");
                    data.write(buf,0,count);
                }
            }
            return parse(data.toString(StandardCharsets.UTF_8.name()));
        }finally{Arrays.fill(body,(byte)0);active=null;if(connection!=null)connection.disconnect();}
    }
    static String parse(String response)throws Exception{
        JSONObject root=new JSONObject(response);
        String output=root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        if(output.trim().isEmpty()||output.length()>AiProtocol.MAX_OUTPUT)throw new IOException("Empty or too large response");
        return output;
    }
    void cancel(){cancelled=true;HttpsURLConnection c=active;if(c!=null)c.disconnect();}
    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status){super("HTTP "+status);this.status=status;}
    }
}

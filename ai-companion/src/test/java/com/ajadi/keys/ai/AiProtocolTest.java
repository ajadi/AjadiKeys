package com.ajadi.keys.ai;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class AiProtocolTest {
    @Test public void scopes(){
        assertEquals("api.example.org",AiProtocol.endpoint("https://api.example.org/v1/chat/completions").getHost());
        assertEquals(8443,AiProtocol.endpoint("https://local.example:8443/v1/chat/completions").getPort());
        for(String invalid:new String[]{"http://example.org/api","https://user:key@example.org/api","https://example.org/api?key=x","https://example.org/api#fragment","https://example.org","https://example.org:99999/api",""}){
            try{AiProtocol.endpoint(invalid);fail(invalid);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void selectionBounds(){
        assertFalse(AiProtocol.validSelection(""));assertFalse(AiProtocol.validSelection("   "));
        assertTrue(AiProtocol.validSelection("שלום"));
        assertTrue(AiProtocol.validSelection("x".repeat(4000)));assertFalse(AiProtocol.validSelection("x".repeat(4001)));
    }
    @Test public void exactPayload(){
        String body=new String(AiProtocol.request("model","Исправь текст","a\n\"b\" ☀ שלום"),StandardCharsets.UTF_8);
        assertEquals("{\"model\":\"model\",\"stream\":false,\"messages\":[{\"role\":\"system\",\"content\":\"Исправь текст\"},{\"role\":\"user\",\"content\":\"a\\n\\\"b\\\" ☀ שלום\"}]}",body);
        assertFalse(body.contains("clipboard"));assertFalse(body.contains("conversation"));
    }
    @Test public void invalidRequests(){
        try{AiProtocol.request("model","", "selected");fail();}catch(IllegalArgumentException expected){}
        try{AiProtocol.request("model","command", "x".repeat(4001));fail();}catch(IllegalArgumentException expected){}
        try{AiProtocol.request("", "command","selected");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void controlsEscaped(){assertEquals("\"\\t\\r\\b\\f\\u0001\\\\\"",AiProtocol.quote("\t\r\b\f\u0001\\"));}
}

package com.ajadi.keys;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;
public class MarianTokenizerTest {
    private static void text(DataOutputStream out,String value)throws IOException{byte[] b=value.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length);out.write(b);}
    private static MarianTokenizer fixture()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        int[] header={0x414a5431,0,2,1,2,8,64,6,10};for(int x:header)out.writeInt(x);
        String[] tokens={"</s>","<unk>","<pad>","\u2581","h","ello","\u2581hello","\u2581world","\u2581\u043c\u0438\u0440","\u2581\u05e9\u05dc\u05d5\u05dd"};
        for(String t:tokens)text(out,t);
        out.writeInt(7);for(int i=3;i<tokens.length;i++){text(out,tokens[i]);out.writeFloat(i>=6?-0.5f:-3f);out.writeInt(i);}
        out.flush();return MarianTokenizer.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    @Test public void scoresChooseUnigramAndNormalizeWhitespace()throws Exception{
        MarianTokenizer t=fixture();assertArrayEquals(new long[]{6,7,0},t.encode("  hello\u00a0   world "));
        assertEquals("hello world",t.decode(Arrays.asList(2,6,7,0)));
    }
    @Test public void unicodeAndUnknownTokens()throws Exception{
        MarianTokenizer t=fixture();assertArrayEquals(new long[]{8,9,0},t.encode("\u043c\u0438\u0440 \u05e9\u05dc\u05d5\u05dd"));
        assertArrayEquals(new long[]{3,1,0},t.encode("???"));
    }
    @Test public void routeAllSixDirections() {
        assertEquals(Arrays.asList("ru-en"),MarianTranslator.route("RU","EN"));
        assertEquals(Arrays.asList("en-he"),MarianTranslator.route("EN","HE"));
        assertEquals(Arrays.asList("he-en"),MarianTranslator.route("HE","EN"));
        assertEquals(Arrays.asList("ru-en","en-he"),MarianTranslator.route("RU","HE"));
        assertEquals(Arrays.asList("he-en","en-ru"),MarianTranslator.route("HE","RU"));
    }
    @Test(expected=IllegalArgumentException.class) public void identicalLanguagesRejected(){MarianTranslator.route("RU","RU");}
}

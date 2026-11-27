package com.ajadi.keys;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unigram SentencePiece scores and the exact original Marian model token IDs. */
final class MarianTokenizer {
    static final int MAX_TOKENS=256;
    private static final char SPACE='\u2581';
    final int eos,pad,unk,start,heads,headDim,layers;
    private final String[] vocabulary;
    private final Node root=new Node();
    private float minimumScore=0;
    private static final class Node {
        final Map<Character,Node> children=new HashMap<>();
        int token=-1;float score;
    }
    private MarianTokenizer(DataInputStream in) throws IOException {
        if(in.readInt()!=0x414a5431)throw new IOException("Unsupported tokenizer format");
        eos=in.readInt();pad=in.readInt();unk=in.readInt();start=in.readInt();heads=in.readInt();headDim=in.readInt();layers=in.readInt();
        int size=in.readInt();
        if(size<4||size>200000||heads<1||heads>32||headDim<1||headDim>256||layers<1||layers>24)
            throw new IOException("Invalid tokenizer dimensions");
        if(eos<0||pad<0||unk<0||start<0||eos>=size||pad>=size||unk>=size||start>=size)throw new IOException("Invalid special tokens");
        vocabulary=new String[size];for(int i=0;i<size;i++)vocabulary[i]=readString(in);
        int n=in.readInt();if(n<=0||n>100000)throw new IOException("Invalid tokenizer piece count");
        for(int i=0;i<n;i++) {
            String piece=readString(in);float score=in.readFloat();int token=in.readInt();
            if(piece.isEmpty()||!Float.isFinite(score)||token<0||token>=size)throw new IOException("Invalid tokenizer piece");
            Node node=root;for(int j=0;j<piece.length();j++)node=node.children.computeIfAbsent(piece.charAt(j),x->new Node());
            node.token=token;node.score=score;minimumScore=Math.min(minimumScore,score);
        }
        if(in.read()!=-1)throw new IOException("Trailing tokenizer data");
    }
    static MarianTokenizer load(File file) throws IOException {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){return new MarianTokenizer(in);}
    }
    static MarianTokenizer read(DataInputStream in) throws IOException {return new MarianTokenizer(in);}
    private static String readString(DataInputStream in) throws IOException {
        int n=in.readInt();if(n<0||n>4096)throw new IOException("Invalid tokenizer string length");
        byte[] data=new byte[n];in.readFully(data);return new String(data,StandardCharsets.UTF_8);
    }
    static String normalize(String raw) {
        String text=Normalizer.normalize(raw,Normalizer.Form.NFKC);StringBuilder normalized=new StringBuilder();
        boolean boundary=true;
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)){boundary=true;continue;}
            if(Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT)continue;
            if(boundary){normalized.append(SPACE);boundary=false;}
            normalized.appendCodePoint(cp);
        }
        return normalized.toString();
    }
    long[] encode(String input) {
        String text=normalize(input);int length=text.length();
        if(length==0)throw new IllegalArgumentException("Enter text to translate");
        double[] best=new double[length+1];Arrays.fill(best,Double.NEGATIVE_INFINITY);best[0]=0;
        int[] previous=new int[length+1],ids=new int[length+1];Arrays.fill(previous,-1);
        for(int i=0;i<length;i++) {
            if(!Double.isFinite(best[i]))continue;
            int one=i+Character.charCount(text.codePointAt(i));boolean single=false;Node node=root;
            for(int j=i;j<length;j++) {
                node=node.children.get(text.charAt(j));if(node==null)break;
                if(node.token>=0) {
                    if(j+1==one)single=true;
                    double value=best[i]+node.score;
                    if(value>best[j+1]){best[j+1]=value;previous[j+1]=i;ids[j+1]=node.token;}
                }
            }
            if(!single) {
                double value=best[i]+minimumScore-10;
                if(value>best[one]){best[one]=value;previous[one]=i;ids[one]=unk;}
            }
        }
        List<Integer> tokens=new ArrayList<>();
        for(int i=length;i>0;i=previous[i]) {
            if(previous[i]<0)throw new IllegalArgumentException("Unable to tokenize text");
            int id=ids[i];if(id!=unk||tokens.isEmpty()||tokens.get(tokens.size()-1)!=unk)tokens.add(id);
        }
        Collections.reverse(tokens);tokens.add(eos);
        if(tokens.size()>MAX_TOKENS)throw new IllegalArgumentException("Text is too long for local translation; split it into shorter parts");
        long[] result=new long[tokens.size()];for(int i=0;i<result.length;i++)result[i]=tokens.get(i);return result;
    }
    String decode(List<Integer> generated) {
        StringBuilder text=new StringBuilder();
        for(int id:generated) {
            if(id==eos||id==pad||id==start)continue;
            if(id<0||id>=vocabulary.length)continue;
            if(id==unk)text.append('\ufffd');else text.append(vocabulary[id]);
        }
        return text.toString().replace(SPACE,' ').trim();
    }
}

package com.ajadi.keys;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, indexed public count tables. No neural model, network or user text. */
final class NgramData {
    static final class Followers {
        private final NgramData owner;
        final int total;
        private final int[] pairs;
        Followers(NgramData owner,int total,int[] pairs){this.owner=owner;this.total=total;this.pairs=pairs;}
        int count(String word){
            Integer id=owner.ids.get(word);if(id==null)return 0;
            for(int i=0;i<pairs.length;i+=2)if(pairs[i]==id)return pairs[i+1];
            return 0;
        }
        void collect(String prefix,Set<String> result){
            for(int i=0;i<pairs.length;i+=2){String w=owner.words[pairs[i]];if(w.startsWith(prefix))result.add(w);}
        }
    }
    final String[] words;
    private final int[] counts;
    private final Map<String,Integer> ids;
    private final Map<Integer,Followers> bigrams;
    private final Map<Long,Followers> trigrams;
    final long total;
    private NgramData(String[] words,int[] counts,Map<String,Integer> ids,long total){
        this.words=words;this.counts=counts;this.ids=ids;this.total=total;
        bigrams=new HashMap<>();trigrams=new HashMap<>();
    }
    int count(String word){Integer index=ids.get(word);return index==null?0:counts[index];}
    Followers bigram(String p1){
        Integer id=ids.get(p1);return id==null?null:bigrams.get(id);
    }
    Followers trigram(String p2,String p1){
        Integer a=ids.get(p2),b=ids.get(p1);
        return a==null||b==null||b==0?null:trigrams.get(((long)a<<16)|b);
    }
    private static int readVar(DataInputStream in)throws IOException{
        long v=0;
        for(int shift=0;shift<=28;shift+=7){
            int b=in.readUnsignedByte();v|=(long)(b&127)<<shift;
            if((b&128)==0){if(v>Integer.MAX_VALUE)throw new IOException("Count overflow");return (int)v;}
        }
        throw new IOException("Invalid integer");
    }
    private static int bounded(DataInputStream in,int max)throws IOException{
        int v=readVar(in);if(v>max)throw new IOException("Invalid table bound");return v;
    }
    private Followers readFollowers(DataInputStream in)throws IOException{
        int total=readVar(in),n=bounded(in,64);
        if(total<=0||n==0)throw new IOException("Empty context");
        int[] pairs=new int[n*2];long sum=0;
        for(int i=0;i<n;i++){
            int word=bounded(in,words.length-1),count=readVar(in);
            if(word<=0||count<=0||count>total)throw new IOException("Invalid follower");
            pairs[i*2]=word;pairs[i*2+1]=count;sum+=count;
        }
        if(sum>total)throw new IOException("Invalid total");
        return new Followers(this,total,pairs);
    }
    static NgramData read(InputStream input)throws IOException{
        DataInputStream in=new DataInputStream(new BufferedInputStream(input,65536));
        if(in.readInt()!=0x414b4e31)throw new IOException("Unknown context table version");
        int count=bounded(in,60000);
        String[] words=new String[count+1];int[] freq=new int[count+1];
        Map<String,Integer> ids=new HashMap<>(count*2);long total=0;words[0]="";ids.put("",0);
        for(int i=1;i<=count;i++){
            int length=bounded(in,160);if(length==0)throw new IOException("Empty vocabulary");
            byte[] bytes=new byte[length];in.readFully(bytes);String w=new String(bytes,StandardCharsets.UTF_8);int n=readVar(in);
            if(!SuggestionModel.validWord(w)||ids.put(w,i)!=null)throw new IOException("Invalid vocabulary");
            words[i]=w;freq[i]=n;total+=n;
        }
        NgramData data=new NgramData(words,freq,ids,total);
        int groups=bounded(in,150000);
        for(int i=0;i<groups;i++){
            int p=bounded(in,count);
            if(data.bigrams.put(p,data.readFollowers(in))!=null)throw new IOException("Duplicate bigram");
        }
        groups=bounded(in,150000);
        for(int i=0;i<groups;i++){
            int p2=bounded(in,count),p1=bounded(in,count);long key=((long)p2<<16)|p1;
            if(p1==0||data.trigrams.put(key,data.readFollowers(in))!=null)throw new IOException("Invalid trigram");
        }
        if(in.read()!=-1)throw new IOException("Trailing data");
        return data;
    }
}

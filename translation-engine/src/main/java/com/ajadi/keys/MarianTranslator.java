package com.ajadi.keys;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtException;
import java.io.File;
import java.io.IOException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CPU-only quantized Marian encoder and merged cached decoder. Sessions live only for the requested direction. */
final class MarianTranslator {
    private static final int MAX_OUTPUT=256;
    interface Models {
        ByteBuffer onnx(String pair,String file)throws IOException;
        DataInputStream tokenizer(String pair)throws IOException;
    }
    private final Models models;
    MarianTranslator(Models models){this.models=models;}
    MarianTranslator(File modelRoot) {
        this(new Models(){
            public ByteBuffer onnx(String pair,String file)throws IOException{
                try(FileChannel ch=FileChannel.open(new File(new File(modelRoot,pair),file).toPath(),StandardOpenOption.READ)){
                    return ch.map(FileChannel.MapMode.READ_ONLY,0,ch.size()).order(ByteOrder.nativeOrder());
                }
            }
            public DataInputStream tokenizer(String pair)throws IOException{
                return new DataInputStream(new BufferedInputStream(new FileInputStream(new File(new File(modelRoot,pair),"tokenizer.bin"))));
            }
        });
    }
    static List<String> route(String from,String to) {
        if(!java.util.Arrays.asList("RU","EN","HE").contains(from)||!java.util.Arrays.asList("RU","EN","HE").contains(to)||from.equals(to))
            throw new IllegalArgumentException("Choose different supported languages");
        String a=from.toLowerCase(java.util.Locale.ROOT),b=to.toLowerCase(java.util.Locale.ROOT);
        if("EN".equals(from)||"EN".equals(to))return java.util.Collections.singletonList(a+"-"+b);
        return java.util.Arrays.asList(a+"-en","en-"+b);
    }
    String translate(String from,String to,String text) throws IOException,OrtException,InterruptedException {
        if(text.trim().isEmpty())throw new IllegalArgumentException("Enter text to translate");
        if(text.length()>1000)throw new IllegalArgumentException("Text is too long; split it into shorter parts");
        String result=text;
        for(String pair:route(from,to)) {
            checkCancelled();result=translatePair(pair,result);
        }
        return result;
    }
    private static void checkCancelled() throws InterruptedException {if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private String translatePair(String pair,String text) throws IOException,OrtException,InterruptedException {
        MarianTokenizer tokenizer;try(DataInputStream in=models.tokenizer(pair)){tokenizer=MarianTokenizer.read(in);}
        ByteBuffer encoderModel=models.onnx(pair,"encoder.onnx"),decoderModel=models.onnx(pair,"decoder.onnx");
        OrtEnvironment environment=OrtEnvironment.getEnvironment(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR,"AjadiLocalTranslation");
        environment.setTelemetry(false);
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            options.setMemoryPatternOptimization(false);
            try(OrtSession encoder=environment.createSession(encoderModel,options);
                OrtSession decoder=environment.createSession(decoderModel,options)) {
                // Break explicit line boundaries locally; keep intermediate translation in RAM only.
                StringBuilder out=new StringBuilder();String[] lines=text.split("\n",-1);
                if(lines.length>16)throw new IllegalArgumentException("Too many lines; translate in smaller parts");
                for(int i=0;i<lines.length;i++) {
                    checkCancelled();if(i>0)out.append('\n');
                    if(!lines[i].trim().isEmpty())out.append(generate(environment,encoder,decoder,tokenizer,lines[i]));
                }
                return out.toString();
            }
        }
    }
    private static Map<String,OnnxTensor> encoderInputs(OnnxTensor ids,OnnxTensor mask) {
        Map<String,OnnxTensor> inputs=new HashMap<>();inputs.put("input_ids",ids);inputs.put("attention_mask",mask);return inputs;
    }
    private static OnnxTensor tensor(OrtSession.Result r,String name) throws OrtException {
        OnnxValue value=r.get(name).orElseThrow(()->new IllegalStateException("Missing model tensor "+name));
        return (OnnxTensor)value;
    }
    private String generate(OrtEnvironment env,OrtSession encoder,OrtSession decoder,MarianTokenizer tok,String text)
            throws OrtException,InterruptedException {
        long[] input=tok.encode(text);long[] ones=new long[input.length];java.util.Arrays.fill(ones,1);
        List<Integer> generated=new ArrayList<>();
        try(OnnxTensor ids=OnnxTensor.createTensor(env,new long[][]{input});
            OnnxTensor mask=OnnxTensor.createTensor(env,new long[][]{ones});
            OrtSession.Result encoded=encoder.run(encoderInputs(ids,mask));
            OnnxTensor empty=OnnxTensor.createTensor(env,FloatBuffer.allocate(0),new long[]{1,tok.heads,0,tok.headDim})) {
            OrtSession.Result first=null,previous=null;
            int prior=tok.start;boolean ended=false;
            try {
                for(int step=0;step<MAX_OUTPUT;step++) {
                    checkCancelled();Map<String,OnnxTensor> in=new HashMap<>();
                    try(OnnxTensor token=OnnxTensor.createTensor(env,new long[][]{{prior}});
                        OnnxTensor useCache=OnnxTensor.createTensor(env,new boolean[]{step>0})) {
                        in.put("input_ids",token);in.put("encoder_attention_mask",mask);
                        in.put("encoder_hidden_states",tensor(encoded,"last_hidden_state"));in.put("use_cache_branch",useCache);
                        for(int i=0;i<tok.layers;i++)for(String side:new String[]{"decoder","encoder"})for(String type:new String[]{"key","value"}) {
                            String suffix=i+"."+side+"."+type;
                            OnnxTensor cache=step==0?empty:tensor(side.equals("encoder")?first:previous,"present."+suffix);
                            in.put("past_key_values."+suffix,cache);
                        }
                        OrtSession.Result next=decoder.run(in);
                        if(previous!=null&&previous!=first)previous.close();previous=next;if(first==null)first=next;
                        FloatBuffer logits=tensor(next,"logits").getFloatBuffer();
                        float best=Float.NEGATIVE_INFINITY;int choice=-1;
                        for(int i=0;i<logits.limit();i++) {
                            if(i==tok.pad)continue;float value=logits.get(i);
                            if(value>best){choice=i;best=value;}
                        }
                        if(choice<0)throw new IllegalStateException("Model produced invalid output");
                        prior=choice;
                        if(prior==tok.eos){ended=true;break;}
                        generated.add(prior);
                    }
                }
            } finally {
                if(previous!=null&&previous!=first)previous.close();if(first!=null)first.close();
            }
            if(!ended)throw new IllegalArgumentException("Translation reached its token limit; split the message");
            String result=tok.decode(generated);
            if(result.isEmpty())throw new IllegalStateException("Model returned an empty translation");
            return result;
        }
    }
}

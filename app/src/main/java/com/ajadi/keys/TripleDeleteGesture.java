package com.ajadi.keys;

/** Three consecutive short Backspace releases in one editor; no text is retained. */
final class TripleDeleteGesture {
    static final long GAP_MS=300, TOTAL_MS=600, TAP_MS=180;
    private int count,minCaret,maxCaret;
    private long first,last,sequence;
    private Object editor,connection;
    private boolean draft;
    void clear(){count=0;editor=null;connection=null;}
    boolean tap(long down,long up,long seq,Object currentEditor,Object currentConnection,
                boolean inDraft,int start,int end){
        if(currentEditor==null||currentConnection==null||start<0||end<0||up<down||up-down>TAP_MS){
            clear();return false;
        }
        int caret=Math.min(start,end);
        boolean consecutive=count>0&&up>=last&&up-last<=GAP_MS&&up-first<=TOTAL_MS
                &&seq==sequence+1&&editor==currentEditor&&connection==currentConnection&&draft==inDraft
                &&start==end&&caret>=minCaret&&caret<=maxCaret;
        if(!consecutive){clear();count=1;first=up;}else count++;
        if(count==3){clear();return true;}
        last=up;sequence=seq;editor=currentEditor;connection=currentConnection;draft=inDraft;
        // A character deletion changes a UTF-16 caret by one or two units; a selected range by its length.
        if(start!=end||caret==0){minCaret=maxCaret=caret;}
        else{minCaret=Math.max(0,caret-2);maxCaret=caret-1;}
        return false;
    }
}

package com.ajadi.keys;

import com.ajadi.keys.KeyLayout.Language;

/** Tracks only the IME's own just-committed word and two previous words in RAM. */
final class WordSession {
    interface Observer {
        void completed(Language language, String previous2, String previous1, String word);
    }

    private final StringBuilder prefix = new StringBuilder();
    private String previous2 = "", previous1 = "";
    private boolean discardToken;
    private boolean grammarGap, grammarPreviousGap;
    private String negationJoinPrefix="";
    static final class Snapshot {
        final String prefix,previous2,previous1,negationJoinPrefix;
        final boolean discard,grammarGap,grammarPreviousGap;
        Snapshot(String prefix,String previous2,String previous1,boolean discard,boolean grammarGap,boolean grammarPreviousGap,String negationJoinPrefix) {
            this.prefix=prefix;this.previous2=previous2;this.previous1=previous1;this.discard=discard;
            this.grammarGap=grammarGap;this.grammarPreviousGap=grammarPreviousGap;
            this.negationJoinPrefix=negationJoinPrefix;
        }
    }
    Snapshot snapshot() { return new Snapshot(prefix(),previous2,previous1,discardToken,grammarGap,grammarPreviousGap,negationJoinPrefix); }
    void restore(Snapshot snapshot) {
        reset(); prefix.append(snapshot.prefix); previous2=snapshot.previous2;
        previous1=snapshot.previous1; discardToken=snapshot.discard;
        grammarGap=snapshot.grammarGap;grammarPreviousGap=snapshot.grammarPreviousGap;
        negationJoinPrefix=snapshot.negationJoinPrefix;
    }
    void replacePrefix(String word) { prefix.setLength(0); prefix.append(word); discardToken=false; }
    void loadCursorContext(Language language,CursorWord cursor){
        reset();prefix.append(cursor.word());
        previous1=SuggestionModel.normalise(language,cursor.previous1);
        previous2=SuggestionModel.normalise(language,cursor.previous2);
        grammarGap=cursor.grammarGap;grammarPreviousGap=cursor.grammarPreviousGap;
    }

    String prefix() { return prefix.toString(); }
    boolean hasSuggestionContext(){return prefix.length()>0||!previous1.isEmpty()||!previous2.isEmpty();}
    String previous1() { return previous1; }
    String previous2() { return previous2; }
    String adjacentPrevious1(){return grammarGap?"":previous1;}
    String adjacentPrevious2(){return grammarGap||grammarPreviousGap?"":previous2;}
    String negationJoinPrefix(){return negationJoinPrefix;}
    void rewindNegationForCorrection(){
        prefix.setLength(0);discardToken=false;previous1=previous2;previous2="";
        grammarGap=grammarPreviousGap;grammarPreviousGap=false;negationJoinPrefix="";
    }

    void accept(Language language, String text, Observer observer) {
        for (int i=0; i<text.length();) {
            int cp=text.codePointAt(i); i+=Character.charCount(cp);
            int kind=Character.getType(cp);
            boolean letter=Character.isLetter(cp) || kind==Character.NON_SPACING_MARK
                    || kind==Character.COMBINING_SPACING_MARK;
            boolean internal=(cp==39||cp==0x2019||cp=='-') && prefix.length()>0;
            if (letter||internal) {
                if (!discardToken) {
                    prefix.appendCodePoint(cp);
                    if (prefix.length()>SuggestionModel.MAX_WORD_CHARS) {
                        prefix.setLength(0);
                        discardToken=true;
                    }
                }
            } else if (Character.isDigit(cp)) {
                // Alphanumeric strings are not vocabulary words.
                prefix.setLength(0);
                discardToken=true;
                previous1=previous2="";grammarGap=grammarPreviousGap=false;negationJoinPrefix="";
            } else {
                // Join only a negation and exactly one ASCII space that this IME itself typed.
                negationJoinPrefix=!discardToken&&language==Language.RU&&cp==' '
                        &&"не".equals(SuggestionModel.normalise(language,prefix.toString()))?prefix.toString()+" ":"";
                if (!discardToken) finish(language,observer);
                prefix.setLength(0);
                discardToken=false;
                if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))grammarGap=true;
                if (cp=='.'||cp=='!'||cp=='?'||cp==10||cp==13){
                    previous1=previous2="";grammarGap=grammarPreviousGap=false;
                }
            }
        }
    }

    private void finish(Language language, Observer observer) {
        String word=SuggestionModel.normalise(language,prefix.toString());
        if (!SuggestionModel.validWord(word)) return;
        if (observer!=null) observer.completed(language,previous2,previous1,word);
        previous2=previous1; previous1=word;
        grammarPreviousGap=grammarGap;grammarGap=false;
    }

    void backspace() {
        if (prefix.length()>0) {
            int end=prefix.length();
            prefix.delete(prefix.offsetByCodePoints(end,-1),end);
        } else {
            reset();
        }
    }

    void reset() {
        prefix.setLength(0);
        previous1=previous2="";
        discardToken=false;grammarGap=grammarPreviousGap=false;negationJoinPrefix="";
    }

    String completion(Language language,String candidate,boolean caps,boolean initialCapital,String boundary) {
        String normalized=SuggestionModel.normalise(language,prefix());
        if (!candidate.startsWith(normalized)) return null;
        String tail=candidate.substring(normalized.length());
        if (caps) tail=tail.toUpperCase(language.locale);
        else if(normalized.isEmpty()&&initialCapital&&!tail.isEmpty()) {
            int end=Character.charCount(tail.codePointAt(0));
            tail=tail.substring(0,end).toUpperCase(language.locale)+tail.substring(end);
        }
        return tail+boundary;
    }
}

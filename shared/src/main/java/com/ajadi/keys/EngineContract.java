package com.ajadi.keys;

/** Versioned protocol shared by the small IME and independently installed signed engines. */
final class EngineContract {
    static final String BASE="com.ajadi.keys";
    static final String PERMISSION="com.ajadi.keys.permission.LOCAL_ENGINE";
    static final String AUTHORITY="com.ajadi.keys.models";
    static final String TRANSLATION="com.ajadi.keys.translation";
    static final String VOICE="com.ajadi.keys.voice";
    static final String AI="com.ajadi.keys.ai";
    static final String AI_ACTIVITY="com.ajadi.keys.ai.AiActivity";
    static final String AI_APK="https://github.com/ajadi/AjadiKeys/releases/download/ai-actions-v1/AjadiKeys-ai-companion-0.7.0.apk";
    static final String TRANSLATION_SERVICE="com.ajadi.keys.TranslationService";
    static final String VOICE_SERVICE="com.ajadi.keys.VoiceService";
    static final String PROTOCOL_METADATA="com.ajadi.keys.ENGINE_PROTOCOL";
    static final int PROTOCOL=1,REQUEST=1,CANCEL=2,RESULT=3;
    static final String RELEASE="https://github.com/ajadi/AjadiKeys/releases/download/offline-engines-v1/";
    static final String MODELS_RELEASE="https://github.com/ajadi/AjadiKeys/releases/download/translation-models-v1/";
}

package com.ajadi.keys.ai;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** API credentials are local to the optional app, encrypted by Android Keystore. */
final class AiSecretStore {
    private static final String ALIAS="ajadi-ai-api-key-v1";
    private final SharedPreferences prefs;
    AiSecretStore(Context context){prefs=context.getSharedPreferences("ai_secret",Context.MODE_PRIVATE);}
    boolean has(String scope){return scope!=null&&scope.equals(prefs.getString("scope",""))&&prefs.contains("cipher")&&prefs.contains("iv");}
    private SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        java.security.Key existing=store.getKey(ALIAS,null);
        if(existing instanceof SecretKey)return (SecretKey)existing;
        if(!create)throw new IllegalStateException("Encryption key unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    void save(String scope,String token)throws Exception{
        AiProtocol.endpoint(scope);
        if(token==null||token.length()>4096||token.indexOf('\r')>=0||token.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid API token");
        if(token.isEmpty()){clear();return;}
        byte[] raw=token.getBytes(StandardCharsets.UTF_8);
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
            cipher.updateAAD(scope.getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(raw);
            if(!prefs.edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                    .putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("scope",scope).commit())
                throw new IllegalStateException("Unable to store encrypted key");
            Arrays.fill(encrypted,(byte)0);
        }finally{Arrays.fill(raw,(byte)0);}
    }
    String read(String scope)throws Exception{
        if(!has(scope))return "";
        byte[] iv=Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP),encrypted=Base64.decode(prefs.getString("cipher",""),Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,iv));
        cipher.updateAAD(scope.getBytes(StandardCharsets.UTF_8));byte[] raw=cipher.doFinal(encrypted);
        try{return new String(raw,StandardCharsets.UTF_8);}
        finally{Arrays.fill(raw,(byte)0);Arrays.fill(encrypted,(byte)0);}
    }
    void clear(){prefs.edit().clear().commit();}
}

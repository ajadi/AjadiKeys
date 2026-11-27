#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <memory>
#include <sys/mman.h>
#include <sys/stat.h>
#include "whisper.h"

// Native inference runs serially in the non-exported voice process.
namespace {
std::atomic<int64_t> cancelledThrough{-1};
void quiet(enum ggml_log_level, const char *, void *) {}
bool aborted(void *data) {
    return *static_cast<int64_t *>(data) <= cancelledThrough.load(std::memory_order_relaxed);
}
void fail(JNIEnv *env, const char *message) {
    jclass type=env->FindClass("java/lang/IllegalStateException");
    if(type) env->ThrowNew(type,message);
}
std::string javaString(JNIEnv *env,jstring value){
    const char *chars=env->GetStringUTFChars(value,nullptr);
    if(!chars)return {};
    std::string result(chars);env->ReleaseStringUTFChars(value,chars);return result;
}
}
extern "C" JNIEXPORT void JNICALL
Java_com_ajadi_keys_WhisperNative_cancelThrough(JNIEnv *,jclass,jlong id) {
    int64_t previous=cancelledThrough.load(std::memory_order_relaxed);
    while(previous<id&&!cancelledThrough.compare_exchange_weak(previous,id,std::memory_order_relaxed)) {}
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ajadi_keys_WhisperNative_transcribeUtf8(JNIEnv *env,jclass,jlong id,jint modelFd,jstring language,jbyteArray pcm,jint threads) {
    if(modelFd<0||!language||!pcm){fail(env,"Missing speech input");return nullptr;}
    const jsize len=env->GetArrayLength(pcm);
    if(len<3200||len>640000||len%2){fail(env,"Invalid audio duration");return nullptr;}
    std::string lang=javaString(env,language);
    if(lang!="auto"&&lang!="en"&&lang!="ru"&&lang!="he"){fail(env,"Unsupported speech language");return nullptr;}
    int64_t request=id;
    if(aborted(&request))return env->NewByteArray(0);
    whisper_log_set(quiet,nullptr);
    std::vector<jbyte> bytes(len);
    env->GetByteArrayRegion(pcm,0,len,bytes.data());
    if(env->ExceptionCheck())return nullptr;
    std::vector<float> samples(len/2);
    for(size_t i=0;i<samples.size();i++){
        uint16_t bits=static_cast<uint8_t>(bytes[2*i])|(static_cast<uint16_t>(static_cast<uint8_t>(bytes[2*i+1]))<<8);
        samples[i]=static_cast<float>(static_cast<int16_t>(bits))/32768.0f;
    }
    std::fill(bytes.begin(),bytes.end(),0);
    whisper_context_params options=whisper_context_default_params();
    options.use_gpu=false;
    options.flash_attn=false;
    struct stat info{};
    if(fstat(modelFd,&info)!=0||(info.st_size!=59707625L&&info.st_size!=190085487L)){
        std::fill(samples.begin(),samples.end(),0.0f);fail(env,"Invalid speech model descriptor");return nullptr;
    }
    struct Mapping{
        void* data;size_t size;
        ~Mapping(){if(data!=MAP_FAILED)munmap(data,size);}
    } mapping{mmap(nullptr,static_cast<size_t>(info.st_size),PROT_READ,MAP_PRIVATE,modelFd,0),static_cast<size_t>(info.st_size)};
    if(mapping.data==MAP_FAILED){std::fill(samples.begin(),samples.end(),0.0f);fail(env,"Unable to map speech model");return nullptr;}
    std::unique_ptr<whisper_context,decltype(&whisper_free)> ctx(whisper_init_from_buffer_with_params(mapping.data,mapping.size,options),whisper_free);
    if(!ctx){std::fill(samples.begin(),samples.end(),0.0f);fail(env,"Unable to load the installed speech model");return nullptr;}
    if(aborted(&request)){std::fill(samples.begin(),samples.end(),0.0f);return env->NewByteArray(0);}
    whisper_full_params p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads=std::max(1,std::min(4,static_cast<int>(threads)));
    p.language=lang.c_str();p.translate=false;p.no_context=true;p.no_timestamps=true;
    p.print_progress=false;p.print_realtime=false;p.print_timestamps=false;p.print_special=false;
    p.abort_callback=aborted;p.abort_callback_user_data=&request;
    p.temperature=0.0f;
    const int status=whisper_full(ctx.get(),p,samples.data(),static_cast<int>(samples.size()));
    std::fill(samples.begin(),samples.end(),0.0f);
    if(aborted(&request))return env->NewByteArray(0);
    if(status!=0){fail(env,"Local speech recognition failed");return nullptr;}
    std::string result;
    for(int i=0;i<whisper_full_n_segments(ctx.get());i++){
        const char *part=whisper_full_get_segment_text(ctx.get(),i);
        if(part)result+=part;
    }
    if(result.size()>65536){fail(env,"Speech result too long");return nullptr;}
    // Use ordinary UTF-8 rather than JNI modified UTF-8, including Hebrew and supplementary characters.
    jbyteArray output=env->NewByteArray(static_cast<jsize>(result.size()));
    if(output&&!result.empty())env->SetByteArrayRegion(output,0,static_cast<jsize>(result.size()),reinterpret_cast<const jbyte *>(result.data()));
    std::fill(result.begin(),result.end(),'\0');
    return output;
}

#include <jni.h>
#include <string>
#include <cstring>
#include "engine.hpp"

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_noteai_engine_NoteMarkdownView_nativeParse(JNIEnv* env, jclass /*cls*/, jstring markdown) {
    if (markdown == nullptr) return nullptr;

    const char* utf = env->GetStringUTFChars(markdown, nullptr);
    if (utf == nullptr) return nullptr;
    jsize utfLen = env->GetStringUTFLength(markdown);

    noteai::MarkdownEngine engine;
    noteai::ParseResult result = engine.parse(std::string_view(utf, (size_t)utfLen));

    env->ReleaseStringUTFChars(markdown, utf);

    // 创建 ParseNativeResult 对象
    jclass resultClass = env->FindClass("com/noteai/engine/ParseNativeResult");
    if (resultClass == nullptr) return nullptr;

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    if (constructor == nullptr) return nullptr;

    jobject resultObj = env->NewObject(resultClass, constructor);
    if (resultObj == nullptr) return nullptr;

    // 设置 plainText
    jstring plainText = env->NewStringUTF(result.plainText.c_str());
    jfieldID plainTextField = env->GetFieldID(resultClass, "plainText", "Ljava/lang/String;");
    env->SetObjectField(resultObj, plainTextField, plainText);
    env->DeleteLocalRef(plainText);

    // 设置 spansFlat (每 5 个 int 一个 span)
    int spanCount = (int)result.spans.size();
    jintArray spansFlat = env->NewIntArray(spanCount * 5);
    if (spansFlat != nullptr) {
        jint* flat = env->GetIntArrayElements(spansFlat, nullptr);
        for (int i = 0; i < spanCount; ++i) {
            int base = i * 5;
            flat[base]     = result.spans[i].start;
            flat[base + 1] = result.spans[i].end;
            flat[base + 2] = result.spans[i].type;
            flat[base + 3] = result.spans[i].level;
            flat[base + 4] = result.spans[i].extraOffset;
        }
        env->ReleaseIntArrayElements(spansFlat, flat, 0);
    }
    jfieldID spansFlatField = env->GetFieldID(resultClass, "spansFlat", "[I");
    env->SetObjectField(resultObj, spansFlatField, spansFlat);
    if (spansFlat != nullptr) env->DeleteLocalRef(spansFlat);

    // 设置 extraData
    jstring extraData = env->NewStringUTF(result.extras.c_str());
    jfieldID extraDataField = env->GetFieldID(resultClass, "extraData", "Ljava/lang/String;");
    env->SetObjectField(resultObj, extraDataField, extraData);
    env->DeleteLocalRef(extraData);

    return resultObj;
}

} // extern "C"

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := note_engine
LOCAL_SRC_FILES := engine.cpp jni_bridge.cpp
LOCAL_CPPFLAGS := -std=c++17 -O3
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

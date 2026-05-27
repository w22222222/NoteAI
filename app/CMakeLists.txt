cmake_minimum_required(VERSION 3.18.1)

project("note_engine")

add_library(note_engine SHARED
    engine.cpp
    jni_bridge.cpp
)

find_library(log-lib log)

target_include_directories(note_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(note_engine ${log-lib})

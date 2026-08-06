cmake_minimum_required(VERSION 3.22.1)
project(learnthis LANGUAGES C CXX)

set(WHISPER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/whisper.cpp")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# Phase 01: native stub only. whisper.cpp integration in Phase 08.
add_library(learn-this-native SHARED native-lib.cpp)
target_include_directories(learn-this-native PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_options(learn-this-native PRIVATE -O3 -fvisibility=hidden)
target_link_libraries(learn-this-native PRIVATE android log)

# Phase 08: uncomment below and comment out the add_library above
# add_subdirectory("${WHISPER_ROOT}" "${CMAKE_BINARY_DIR}/whisper")
# add_library(learnthis SHARED whisper_jni.cpp)
# target_include_directories(learnthis PRIVATE "${WHISPER_ROOT}/include")
# target_compile_options(learnthis PRIVATE -O3 -fvisibility=hidden)
# target_link_libraries(learnthis PRIVATE whisper android log)

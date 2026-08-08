cmake_minimum_required(VERSION 3.22.1)
project(learnthis LANGUAGES C CXX)

set(WHISPER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/whisper.cpp")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)

if (NOT EXISTS "${WHISPER_ROOT}/include/whisper.h")
    message(FATAL_ERROR "whisper.cpp is missing. Run: git submodule update --init --recursive")
endif()

add_subdirectory("${WHISPER_ROOT}" "${CMAKE_BINARY_DIR}/whisper")

# Android Studio/Gradle configures native debug variants with -O0 by default. That
# makes whisper.cpp several times slower and obscures real device diagnostics.
# Keep symbols/assertions, but optimize the compute-heavy static targets. Avoid
# -ffast-math, device-specific -mcpu flags, and LTO here because those can change
# numerical behavior or reduce portability across supported arm64 devices.
if (CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
    foreach(performance_target whisper ggml ggml-base ggml-cpu)
        if (TARGET ${performance_target})
            target_compile_options(${performance_target} PRIVATE
                $<$<CONFIG:Debug>:-O3>
                $<$<CONFIG:RelWithDebInfo>:-O3>
                $<$<CONFIG:Release>:-O3>
            )
        endif()
    endforeach()
endif()

add_library(learn-this-native SHARED native-lib.cpp)
target_include_directories(learn-this-native PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${WHISPER_ROOT}/include"
)
target_compile_options(learn-this-native PRIVATE
    -fvisibility=hidden
    $<$<CONFIG:Debug>:-O3>
    $<$<NOT:$<CONFIG:Debug>>:-O3>
)
target_link_libraries(learn-this-native PRIVATE whisper android log)

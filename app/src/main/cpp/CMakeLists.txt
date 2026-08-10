cmake_minimum_required(VERSION 3.22.1)
project(cliplex LANGUAGES C CXX)

set(WHISPER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/whisper.cpp")
option(
    CLIPLEX_ENABLE_KLEIDIAI
    "Enable the experimental Arm KleidiAI integration"
    OFF
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI "${CLIPLEX_ENABLE_KLEIDIAI}" CACHE BOOL "" FORCE)

if (CLIPLEX_ENABLE_KLEIDIAI)
    # whisper.cpp v1.7.6 passes the CMake policy name `NEW` as the boolean value
    # of DOWNLOAD_EXTRACT_TIMESTAMP. CMake 3.22 then treats it as another URL.
    # FetchContent declarations are first-wins, so predeclare the same pinned
    # archive and upstream checksum without that newer keyword and without
    # patching the whisper.cpp submodule.
    include(FetchContent)
    FetchContent_Declare(
        KleidiAI_Download
        URL "https://github.com/ARM-software/kleidiai/archive/refs/tags/v1.9.0.tar.gz"
        URL_HASH MD5=2a8e1bb55d201557553545536489a017
    )
endif()

if (NOT EXISTS "${WHISPER_ROOT}/include/whisper.h")
    message(FATAL_ERROR "whisper.cpp is missing. Run: git submodule update --init --recursive")
endif()

add_subdirectory("${WHISPER_ROOT}" "${CMAKE_BINARY_DIR}/whisper")

# This only proves that the pinned ggml KleidiAI integration sources were added
# to ggml-cpu. It deliberately does not claim that an ISA-specific compute
# microkernel was compiled or selected at runtime.
set(CLIPLEX_KLEIDIAI_SOURCES_INCLUDED OFF)
if (CLIPLEX_ENABLE_KLEIDIAI)
    if (NOT TARGET ggml-cpu)
        message(FATAL_ERROR "KleidiAI integration was enabled but ggml-cpu is unavailable")
    endif()
    get_target_property(CLIPLEX_GGML_CPU_SOURCES ggml-cpu SOURCES)
    string(JOIN ";" CLIPLEX_GGML_CPU_SOURCES_TEXT ${CLIPLEX_GGML_CPU_SOURCES})
    string(FIND "${CLIPLEX_GGML_CPU_SOURCES_TEXT}" "ggml-cpu/kleidiai/kleidiai.cpp" CLIPLEX_KLEIDIAI_WRAPPER_INDEX)
    string(FIND "${CLIPLEX_GGML_CPU_SOURCES_TEXT}" "ggml-cpu/kleidiai/kernels.cpp" CLIPLEX_KLEIDIAI_REGISTRY_INDEX)
    if (CLIPLEX_KLEIDIAI_WRAPPER_INDEX EQUAL -1 OR CLIPLEX_KLEIDIAI_REGISTRY_INDEX EQUAL -1)
        message(FATAL_ERROR "KleidiAI integration was enabled but its ggml integration sources were not included")
    endif()
    set(CLIPLEX_KLEIDIAI_SOURCES_INCLUDED ON)
    message(STATUS "ClipLex KleidiAI experiment: integration sources included; kernel selection remains unobserved")
endif()

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

add_library(cliplex-native SHARED native-lib.cpp)
target_include_directories(cliplex-native PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${WHISPER_ROOT}/include"
)
target_compile_definitions(cliplex-native PRIVATE
    CLIPLEX_KLEIDIAI_INTEGRATION_ENABLED=$<BOOL:${CLIPLEX_ENABLE_KLEIDIAI}>
    CLIPLEX_KLEIDIAI_SOURCES_INCLUDED=$<BOOL:${CLIPLEX_KLEIDIAI_SOURCES_INCLUDED}>
)
target_compile_options(cliplex-native PRIVATE
    -fvisibility=hidden
    $<$<CONFIG:Debug>:-O3>
    $<$<NOT:$<CONFIG:Debug>>:-O3>
)
target_link_libraries(cliplex-native PRIVATE whisper android log)

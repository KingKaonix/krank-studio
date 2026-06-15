cmake_minimum_required(VERSION 3.22.1)
project(krank_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -ffast-math -funroll-loops")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -ffast-math -funroll-loops")

add_subdirectory(oboe)

add_library(krank_engine SHARED
    engine.cpp
    jni_bridge.cpp
    effects/distortion.cpp
    effects/delay.cpp
    effects/reverb.cpp
    effects/eq.cpp
    effects/ampsim.cpp
    effects/chorus.cpp
    effects/noise_gate.cpp
    effects/compressor.cpp
    effects/tuner.cpp
    effects/tone_matcher.cpp
    effects/transcriber.cpp
    effects/midi_exporter.cpp
    effects/metronome.cpp
    effects/looper.cpp
)

target_include_directories(krank_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/effects
)

target_link_libraries(krank_engine
    oboe
    log
    -Wl,--gc-sections
)

set_target_properties(krank_engine PROPERTIES
    LINK_DEPENDS_NO_SONAME ON
)

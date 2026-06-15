# Custom toolchain file for using Termux's native ARM cross-compilers
# with the Android NDK's sysroot and libraries.

# Set the NDK path
set(ANDROID_NDK_PATH "$ENV{ANDROID_NDK_HOME}")
if(NOT ANDROID_NDK_PATH)
    set(ANDROID_NDK_PATH "/data/data/com.termux/files/home/android-sdk/ndk/27.0.12077973")
endif()

# Use Termux's native aarch64-linux-android compilers
set(CMAKE_C_COMPILER "/data/data/com.termux/files/usr/bin/aarch64-linux-android-clang" CACHE PATH "")
set(CMAKE_CXX_COMPILER "/data/data/com.termux/files/usr/bin/aarch64-linux-android-clang++" CACHE PATH "")

# Set NDK sysroot and libraries
set(CMAKE_SYSROOT "${ANDROID_NDK_PATH}/toolchains/llvm/prebuilt/linux-x86_64/sysroot" CACHE PATH "")
set(CMAKE_FIND_ROOT_PATH "${ANDROID_NDK_PATH}/toolchains/llvm/prebuilt/linux-x86_64/sysroot")

# Android-specific settings
set(ANDROID TRUE)
set(ANDROID_ABI "arm64-v8a")
set(ANDROID_PLATFORM "android-26")

# Linker and other tools from NDK (they're just wrappers for the compiler)
set(CMAKE_AR "${ANDROID_NDK_PATH}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar" CACHE PATH "")
set(CMAKE_RANLIB "${ANDROID_NDK_PATH}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ranlib" CACHE PATH "")

# CRITICAL: Prevent the NDK toolchain from being included
set(CMAKE_SYSTEM_NAME "Android" CACHE STRING "")
set(CMAKE_SYSTEM_VERSION "26" CACHE STRING "")
set(CMAKE_ANDROID_NDK "${ANDROID_NDK_PATH}" CACHE PATH "")

# Skip NDK's own toolchain detection
set(__NDK_CMAKE_TOOLCHAIN_INCLUDED TRUE)

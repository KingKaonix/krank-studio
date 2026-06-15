cmake_minimum_required(VERSION 3.10)

# Use Termux's native cross-compilers
set(CMAKE_C_COMPILER /data/data/com.termux/files/usr/bin/aarch64-linux-android-clang)
set(CMAKE_CXX_COMPILER /data/data/com.termux/files/usr/bin/aarch64-linux-android-clang++)

# Use NDK sysroot for Android compatibility
set(CMAKE_SYSROOT /data/data/com.termux/files/home/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/sysroot)

# Set Android toolchain properties
set(ANDROID TRUE)
set(ANDROID_PLATFORM android-26)
set(ANDROID_ABI arm64-v8a)

# Linker and other tools from NDK
set(CMAKE_AR /data/data/com.termux/files/home/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar)
set(CMAKE_RANLIB /data/data/com.termux/files/home/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ranlib)

# Additional compiler flags for optimization
set(CMAKE_CXX_FLAGS "-O2 -ffast-math -funroll-loops")
set(CMAKE_C_FLAGS "-O2 -ffast-math -funroll-loops")

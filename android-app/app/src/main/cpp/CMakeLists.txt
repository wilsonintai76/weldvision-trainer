# CMakeLists.txt — WeldVision Trainer 2.0 Native AprilTag Tracker
# =====================================================================
# Builds the AprilTag 3 C library and the JNI bridge that exposes
# tag detection + pose estimation to Kotlin via the WeldVisionJNI class.
#
# Expected directory layout (after cloning AprilTag 3):
#   src/main/cpp/
#     CMakeLists.txt          <-- this file
#     apriltag_jni.cpp         <-- JNI bridge
#     apriltag/                <-- AprilTag 3 source (clone into here)
#       apriltag.c
#       apriltag_pose.c
#       apriltag_quad_thresh.c
#       common/
#         ...
#

cmake_minimum_required(VERSION 3.22.1)
project("weldvision_tracker")

# ── Standard Flags ────────────────────────────────────────────────────
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Optimize for the target device; -O2 balances speed and debugability.
# Switch to -O3 for production release builds.
add_compile_options(-O2 -Wall -Wextra -fPIE)

# ── AprilTag 3 Core Library Sources ───────────────────────────────────
set(APRILTAG_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/apriltag)

add_library(apriltag_core STATIC
    # Core detection
    ${APRILTAG_SRC_DIR}/apriltag.c
    ${APRILTAG_SRC_DIR}/apriltag_pose.c
    ${APRILTAG_SRC_DIR}/apriltag_quad_thresh.c
    # Tag families
    ${APRILTAG_SRC_DIR}/tagStandard41h12.c
    ${APRILTAG_SRC_DIR}/tagStandard52h13.c
    ${APRILTAG_SRC_DIR}/tagCustom48h12.c
    # Common utilities
    ${APRILTAG_SRC_DIR}/common/getopt.c
    ${APRILTAG_SRC_DIR}/common/g2d.c
    ${APRILTAG_SRC_DIR}/common/homography.c
    ${APRILTAG_SRC_DIR}/common/image_u8.c
    ${APRILTAG_SRC_DIR}/common/image_u8x3.c
    ${APRILTAG_SRC_DIR}/common/image_u8x4.c
    ${APRILTAG_SRC_DIR}/common/image_u8_parallel.c
    ${APRILTAG_SRC_DIR}/common/matd.c
    ${APRILTAG_SRC_DIR}/common/pam.c
    ${APRILTAG_SRC_DIR}/common/pjpeg.c
    ${APRILTAG_SRC_DIR}/common/pnm.c
    ${APRILTAG_SRC_DIR}/common/pthreads_cross.c
    ${APRILTAG_SRC_DIR}/common/string_util.c
    ${APRILTAG_SRC_DIR}/common/svd22.c
    ${APRILTAG_SRC_DIR}/common/time_util.c
    ${APRILTAG_SRC_DIR}/common/unionfind.c
    ${APRILTAG_SRC_DIR}/common/workerpool.c
    ${APRILTAG_SRC_DIR}/common/zarray.c
    ${APRILTAG_SRC_DIR}/common/zhash.c
    ${APRILTAG_SRC_DIR}/common/zmaxheap.c
)

target_include_directories(apriltag_core PUBLIC ${APRILTAG_SRC_DIR})
target_include_directories(apriltag_core PUBLIC ${APRILTAG_SRC_DIR}/common)
# Required for armeabi-v7a — static lib objects must be PIC to link into .so
target_compile_options(apriltag_core PRIVATE -fPIC)


# ── JNI Bridge Shared Library ─────────────────────────────────────────
add_library(weldvision_jni SHARED
    apriltag_jni.cpp
)

# Link AprilTag core
# Disable AprilTag's internal threading — we manage threading in Kotlin.
target_compile_definitions(apriltag_core PRIVATE -D__USE_GNU=1)
target_link_libraries(weldvision_jni apriltag_core)

# System libraries required by JNI and Android
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(cxx-shared c++_shared)
target_link_libraries(weldvision_jni ${log-lib} ${jnigraphics-lib} ${cxx-shared})

# Include JNI headers from the NDK
target_include_directories(weldvision_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})


# ── Calibration Bridge Shared Library ──────────────────────────────────
add_library(calibration_bridge SHARED
    jni/calibration_bridge.cpp
    calibration/tcp_solver.cpp
    calibration/workpiece_frame.cpp
)

# Include geometry and apriltag headers
target_include_directories(calibration_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${APRILTAG_SRC_DIR}
    ${APRILTAG_SRC_DIR}/common
)

# Link against apriltag_core for matd SVD
target_link_libraries(calibration_bridge
    apriltag_core
    ${log-lib}
    ${cxx-shared}
)

# Include JNI headers from the NDK
target_include_directories(weldvision_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

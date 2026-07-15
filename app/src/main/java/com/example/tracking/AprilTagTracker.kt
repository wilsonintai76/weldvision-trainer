package com.example.tracking

class AprilTagTracker {
    init {
        System.loadLibrary("weldvision_jni")
    }

    external fun initTracker()
    external fun processFrame(width: Int, height: Int, data: ByteArray): FloatArray?
}

package com.zeta.ffpanel

class NativeAimPatch {
    external fun initAimPatch(): Boolean
    external fun getClosestEnemyScreenPosition(): Pair<Int, Int>?  // trả về x, y
    external fun cleanup()

    companion object {
        init {
            System.loadLibrary("ffaim")
        }
    }
}
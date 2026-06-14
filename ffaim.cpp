#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/uio.h>
#include <android/log.h>
#include <cmath>

#define LOG_TAG "FFAIM"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Giả sử có offset của mảng enemy trong libil2cpp.so (cần dump game)
// Ví dụ: lớp PlayerManager -> mảng Player* -> vị trí 3D -> chuyển qua screen
#define OFFSET_PLAYER_MANAGER 0x12345678
#define OFFSET_LOCAL_PLAYER  0x12345680
#define OFFSET_ENEMY_LIST    0x12345690
#define OFFSET_ENEMY_COUNT   0x12345694
#define OFFSET_POS_X         0x50
#define OFFSET_POS_Y         0x54
#define OFFSET_POS_Z         0x58
#define OFFSET_SCREEN_X      0x200  // sau khi world to screen
#define OFFSET_SCREEN_Y      0x204

static int g_pid = -1;
static int g_mem_fd = -1;

// Hàm đọc memory
template<typename T>
T read_mem(uintptr_t addr) {
    T val{};
    if (g_mem_fd < 0) return val;
    pread64(g_mem_fd, &val, sizeof(T), addr);
    return val;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_zeta_ffpanel_NativeAimPatch_initAimPatch(JNIEnv *env, jobject thiz) {
    // Tìm PID của FreeFire
    FILE *fp = popen("pidof com.dts.freefireth", "r");
    if (!fp) return JNI_FALSE;
    fscanf(fp, "%d", &g_pid);
    pclose(fp);
    if (g_pid <= 0) return JNI_FALSE;

    char mem_path[256];
    sprintf(mem_path, "/proc/%d/mem", g_pid);
    g_mem_fd = open(mem_path, O_RDONLY);
    if (g_mem_fd < 0) {
        LOGD("Cannot open mem. Need root and su -c chmod 666 /proc/%d/mem", g_pid);
        return JNI_FALSE;
    }
    LOGD("Aim patch init success, PID=%d", g_pid);
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_zeta_ffpanel_NativeAimPatch_getClosestEnemyScreenPosition(JNIEnv *env, jobject thiz) {
    // Tạo Pair<Integer, Integer> để trả về
    jclass pairClass = env->FindClass("kotlin/Pair");
    jmethodID pairCtor = env->GetMethodID(pairClass, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");

    if (g_mem_fd < 0 || g_pid <= 0) {
        return env->NewObject(pairClass, pairCtor, env->NewObject(env->FindClass("java/lang/Integer"), env->GetMethodID(env->FindClass("java/lang/Integer"), "<init>", "(I)V"), -1),
                              env->NewObject(env->FindClass("java/lang/Integer"), env->GetMethodID(env->FindClass("java/lang/Integer"), "<init>", "(I)V"), -1));
    }

    // Đọc địa chỉ cơ bản (giả sử có module base)
    uintptr_t base = 0x70000000; // cần lấy từ /proc/pid/maps
    uintptr_t playerManager = read_mem<uintptr_t>(base + OFFSET_PLAYER_MANAGER);
    if (!playerManager) return nullptr;

    uintptr_t localPlayer = read_mem<uintptr_t>(playerManager + OFFSET_LOCAL_PLAYER);
    int enemyCount = read_mem<int>(playerManager + OFFSET_ENEMY_COUNT);
    uintptr_t enemyList = read_mem<uintptr_t>(playerManager + OFFSET_ENEMY_LIST);

    float bestDist = 1e9;
    int bestX = -1, bestY = -1;
    for (int i = 0; i < enemyCount; i++) {
        uintptr_t enemy = read_mem<uintptr_t>(enemyList + i * sizeof(uintptr_t));
        if (!enemy || enemy == localPlayer) continue;

        // Đọc tọa độ màn hình đã được game world to screen
        float screenX = read_mem<float>(enemy + OFFSET_SCREEN_X);
        float screenY = read_mem<float>(enemy + OFFSET_SCREEN_Y);
        if (screenX <= 0 || screenY <= 0) continue;

        // Tính khoảng cách đến tâm màn hình (640x360 giả sử, nhưng nên lấy thực tế)
        float centerX = 540, centerY = 960; // Ví dụ, mày tự lấy real resolution
        float dx = screenX - centerX;
        float dy = screenY - centerY;
        float dist = dx*dx + dy*dy;
        if (dist < bestDist) {
            bestDist = dist;
            bestX = (int)screenX;
            bestY = (int)screenY;
        }
    }

    if (bestX > 0 && bestY > 0) {
        jint xVal = bestX, yVal = bestY;
        jobject xObj = env->NewObject(env->FindClass("java/lang/Integer"), env->GetMethodID(env->FindClass("java/lang/Integer"), "<init>", "(I)V"), xVal);
        jobject yObj = env->NewObject(env->FindClass("java/lang/Integer"), env->GetMethodID(env->FindClass("java/lang/Integer"), "<init>", "(I)V"), yVal);
        return env->NewObject(pairClass, pairCtor, xObj, yObj);
    }
    return env->NewObject(pairClass, pairCtor, env->NewObject(env->FindClass("java/lang/Integer"), env->GetMethodID(env->FindClass("java/lang/Integer"), "<init>", "(I)V"), -1),
                          env->NewObject(env->FindClass("java/lang/Integer"), env->GetMethodID(env->FindClass("java/lang/Integer"), "<init>", "(I)V"), -1));
}

JNIEXPORT void JNICALL
Java_com_zeta_ffpanel_NativeAimPatch_cleanup(JNIEnv *env, jobject thiz) {
    if (g_mem_fd > 0) close(g_mem_fd);
    g_pid = -1;
}

} // extern "C"
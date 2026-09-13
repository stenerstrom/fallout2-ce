#include "game_clock.h"

#include <SDL.h>
#include <mutex>

namespace fallout {
namespace {
    GameClock gameClock;
    std::mutex gameClockMutex;
}

unsigned int gameClockGetTicks()
{
    const std::lock_guard<std::mutex> lock(gameClockMutex);
    return static_cast<unsigned int>(gameClock.ticks(SDL_GetTicks64()));
}

int gameClockGetSpeed()
{
    const std::lock_guard<std::mutex> lock(gameClockMutex);
    return gameClock.percent();
}

bool gameClockSetSpeed(int percent)
{
    const std::lock_guard<std::mutex> lock(gameClockMutex);
    return gameClock.setPercent(SDL_GetTicks64(), percent);
}
} // namespace fallout

#if defined(__ANDROID__)
#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_alexbatalov_fallout2ce_GameCommandHud_nativeGetSpeed(JNIEnv*, jclass)
{
    return fallout::gameClockGetSpeed();
}

extern "C" JNIEXPORT void JNICALL
Java_com_alexbatalov_fallout2ce_GameCommandHud_nativeSetSpeed(JNIEnv*, jclass, jint percent)
{
    fallout::gameClockSetSpeed(percent);
}
#endif

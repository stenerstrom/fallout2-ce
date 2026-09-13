#ifndef FALLOUT_GAME_CLOCK_H_
#define FALLOUT_GAME_CLOCK_H_

#include <cstdint>

namespace fallout {

// Deterministic clock model. Changing speed preserves the current game time,
// including fractional milliseconds, so existing animation deadlines stay valid.
class GameClock {
public:
    std::uint64_t ticks(std::uint64_t realTicks) const
    {
        return hundredths(realTicks) / 100;
    }

    bool setPercent(std::uint64_t realTicks, int percent)
    {
        if (percent < 50 || percent > 400) return false;
        _gameHundredths = hundredths(realTicks);
        _realAnchor = realTicks;
        _percent = percent;
        return true;
    }

    int percent() const { return _percent; }

private:
    std::uint64_t hundredths(std::uint64_t realTicks) const
    {
        return _gameHundredths + (realTicks >= _realAnchor ? realTicks - _realAnchor : 0) * _percent;
    }

    std::uint64_t _realAnchor = 0;
    std::uint64_t _gameHundredths = 0;
    int _percent = 100;
};

unsigned int gameClockGetTicks();
int gameClockGetSpeed();
bool gameClockSetSpeed(int percent);

} // namespace fallout

#endif

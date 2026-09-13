#include "../../../src/game_clock.h"

#include <cassert>
#include <cstdint>
#include <iostream>

int main()
{
    fallout::GameClock clock;
    assert(clock.ticks(1000) == 1000);
    assert(clock.setPercent(1000, 200));
    assert(clock.ticks(1000) == 1000); // No jump when speeding up.
    assert(clock.ticks(1500) == 2000);
    assert(clock.setPercent(1500, 50));
    assert(clock.ticks(1500) == 2000); // No backwards jump when slowing down.
    assert(clock.ticks(2500) == 2500);
    assert(clock.setPercent(2500, 400));
    assert(clock.ticks(3500) == 6500);
    assert(!clock.setPercent(3500, 0));
    assert(!clock.setPercent(3500, 900));
    assert(clock.percent() == 400);
    assert(clock.ticks(4500) == 10500);

    fallout::GameClock fractions;
    assert(fractions.setPercent(0, 50));
    assert(fractions.ticks(1) == 0);
    assert(fractions.setPercent(1, 100)); // Preserve half a millisecond.
    assert(fractions.ticks(2) == 1);
    assert(fractions.setPercent(2, 150));
    assert(fractions.ticks(3) == 3);

    fallout::GameClock longRunning;
    std::uint64_t nearWrap = (std::uint64_t(1) << 32) - 20;
    assert(longRunning.setPercent(nearWrap, 200));
    assert(longRunning.ticks(nearWrap + 100) == nearWrap + 200);

    // Exercise repeated changes with a fractional reference time.
    fallout::GameClock changing;
    std::uint64_t previous = 0, referenceHundredths = 0;
    int speed = 100;
    for (std::uint64_t real = 1; real <= 10000; real++) {
        referenceHundredths += speed;
        assert(changing.ticks(real) == referenceHundredths / 100);
        assert(changing.ticks(real) >= previous);
        previous = changing.ticks(real);
        if (real % 17 == 0) {
            speed = 50 + ((real / 17) % 8) * 50;
            assert(changing.setPercent(real, speed));
            assert(changing.ticks(real) == previous);
        }
    }
    std::cout << "Game clock tests passed: continuity, speed ratios, fractions and 64-bit uptime.\n";
}

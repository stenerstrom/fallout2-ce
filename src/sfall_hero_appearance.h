#ifndef SFALL_HERO_APPEARANCE_H
#define SFALL_HERO_APPEARANCE_H

#include "db.h"

namespace fallout {

bool heroAppearanceEnabled();
void heroAppearanceInitialize();
void heroAppearanceSelect(int mode);
void heroAppearanceReset();
void heroAppearanceLoad();
void heroAppearanceSyncGender();
bool heroAppearanceSetRace(int race);
bool heroAppearanceSetStyle(int style);
bool heroAppearanceCycle(bool style, int direction);
File* heroAppearanceOpen(const char* path, const char* mode);
int heroAppearanceFid(int fid);
int heroAppearanceBaseFid(int fid);
bool heroAppearanceIsFrame(int frame);
int heroAppearanceBaseFrame(int frame);
int heroAppearanceRace();
int heroAppearanceStyle();

} // namespace fallout

#endif

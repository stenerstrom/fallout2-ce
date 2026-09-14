# Nevada opening-map script correction

## Reproduction and cause

Nevada Extended 2.0.3.4 English has three orphaned object-script records in
ARTEMPLE.MAP: item script IDs 0x03000000 and 0x03000001 (Animfrvr, owner IDs 14
and 18), and critter script ID 0x04000001 (VbCCmndr, owner ID 24).
No map object references these script IDs. The records are also retained in
existing saves. Object IDs 18 and 24 are reused by unrelated scenery, so looking
up an owner by that ID would attach the script to the wrong object.

The dispatcher previously initialized and ran these scripts with a null self.
Animfrvr then called reg_anim_animate_forever with no object. VbCCmndr called
critter_add_trait, obj_can_see_obj and tile_num with no object, repeatedly.

## Change

scriptExecProc skips ITEM and CRITTER scripts while their owner is null, before
loading or initializing their program. It retains the script records and local
variable offsets. It checks the live owner each time, so a subsequently attached
object can execute its script. SYSTEM, SPATIAL and TIMED scripts can legitimately
have no owner and continue through the normal dispatcher.

Object-load, object-creation and set_script paths attach the owner before
executing object scripts. This correction does not change the save format or
replace Nevada scripts/content files.

## Regression procedure

Use isolated copies of the private Nevada content and test saves. Never put
game data or user saves in the public repository.

1. Start a new Nevada game with the premade Slash character. Allow the opening
   map to run. With the unmodified engine this reproduced 123 null-object script
   errors in a 12-second headless run.
2. Repeat with the correction: ARTEMPLE.MAP loads successfully, with no such
   errors. Load a save created before the correction: ARTEMPLE.SAV also loads
   with no such errors.
3. With a normal release engine, check an opening NPC dialog, operate the vault
   exit, save, restart the process and load. Record the actual reached maps and
   dialog in the private build QA report.
4. Smoke-test RPU and Sonora with the same engine, because the dispatcher is
   shared by all profiles.

Steps 1–2 passed on macOS. Temporary logging and headless-start changes used to
trace the broken ownership were removed from production sources. Visual release
engine and packaged-build results are recorded with each private distribution.

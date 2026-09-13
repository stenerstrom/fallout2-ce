# Plan för komplett RPU-stöd i FOR:CE på Android

Datum: 2026-09-13. Status: implementation pågår; kandidat 1.4.0-alpha.2.

## Avstämning efter nästa implementationssteg

- Alpha.1 levererade spelbiblioteket med separata RPU-, Sonora- och Nevada-profiler samt manifeststyrd innehållsuppdatering.
- Alpha.2 implementerar namngivna fake perks/traits med sparat tillstånd, ändringsbara virtuella filer för RPU:s gång- och Goris-skript, Hero Appearance och dess pekkommando, valbara skadeformler/explosioner samt export/återställning av sparningar.
- De riktade C++-/Java-testerna och Mac-scenarierna finns i [testprotokollet](rpu-alpha2-tests.md). Dessa ersätter inte en genomspelning eller verifiering på Honor-plattan.
- Referensen för RPU 2.4.34 är nu låst till taggens commit 6cd291f64a48782ed45fe116e5fb453ecbfc5488. De tre källskripten för EPA-belöningen, gånghastigheten och Goris är identiska i den lokala forken och målversionen. En jämförelse av hela innehållet och ett reproducerbart komplett RPU-bygge återstår.
- Tidigare INI-val bevaras vid uppgradering. De gamla avstängningarna kan nu ändras i **Settings → Restoration Project options**. Nya installationer kan använda fungerande animationsval.
- Innehållsuppdateraren kan återuppta aktivering efter avbrott. Fullständig återställning av en äldre innehållsversion och sammanfogning av ändrade konfigurationsnycklar är fortfarande separata återstående arbeten.
- Nästa verifiering gäller EPA:s faktiska uppdragsvägar, följeslagarorder, utrustning/strid med varje utseende samt Sonora/Dayglow och Nevadas progression.

Avsnitten nedan bevarar den ursprungliga planen och dess baslinje; formuleringar om då saknade funktioner beskriver utgångsläget. Aktuell implementerad omfattning och begränsningar anges ovan och i testprotokollet.

## Mål och omfattning

Målet är att kunna spela igenom Restoration Project Updated med dess återställda innehåll och medföljande tillval på Honor MagicPad, med engelskt gränssnitt och pekstyrning för alla nödvändiga kommandon. Mac-versionen används för snabbare utveckling och reproduktion av fel. Android är den slutliga målplattformen.

Utgångspunkten är det fungerande paketet med officiell RPU 2.4.34. Alla medföljande komponenter och deras användarval ingår i inventeringen, även sådant som ännu inte är aktiverat. Alternativ som utesluter varandra, exempelvis olika skadeformler, ska kunna väljas och fungera var för sig. Externa rekommenderade moddar får en separat lista för eventuell senare utökning.

Sfall-funktioner prioriteras utifrån vad RPU och dess medföljande komponenter faktiskt behöver. Plattformberoende beteenden implementeras med portabla CE-funktioner. Befintliga funktioner får verifieringsuppgifter; saknade eller felaktiga funktioner får implementationsuppgifter.

## Ursprunglig baslinje före alpha.1 och alpha.2

- Motor och Android-gränssnitt: commit `06c5973b5370368658b286017e4954f86fe67e09`, gren `codex/rpu-android`. Engelska APK:n är 1.3.0-rpu.5, versionCode 8. Den är byggd, kontrollerad och överförd till plattan för användarens installation.
- De återanvända native-biblioteken kommer från `ab1dc4d4695ffd3ef4e78ef39ac65986af9657da`. Originaldata och officiell RPU 2.4.34 ingår i det privata paketet.
- Nytt spel och spara/ladda har tidigare verifierats på Mac och Android. Användaren rapporterade att föregående kompletta version fungerade riktigt bra. Det är en övergripande rapport, inte ett dokumenterat test av varje uppdrag, tillval eller HUD-knapp.
- Startmeny, 97 motorinställningar, konfigurationsredigering, kommandomeny och spelhastighet finns.
- Snabbare gång och Goris snabbare avklädningsanimation är avstängda efter reproducerade skriptfel vid `fs_seek`.
- En genomspelning av allt återställt innehåll och en fullständig komponentinventering återstår.

FOR:CE:s [RPU-tracker](https://github.com/fallout2-ce/fallout2-ce/issues/196) beskriver stöd för RPU:s kärna och skiljer ut medföljande tillval. Vår omfattning inkluderar dessa tillval, så den allmänna stödmarkeringen är inte tillräcklig som sluttest.

## Prioriterade arbetspaket

| Ordning | Arbete | Konkret leverans | Godkänt när |
| --- | --- | --- | --- |
| 1 | Lås versioner och gör en komplett funktionslista | Versionsmanifest, komponentmatris och reproducerbart RPU-bygge | Installerade filer, källkod, tillval och laddningsordning går att koppla till samma målversion |
| 2 | Bygg testunderlag och säkra uppdateringar | Testskript, testsparningar, versionshanterad uppdaterare och export av sparningar | En uppdatering från v8 verkligen byter avsedda filer och bevarar användardata, även efter avbrott |
| 3 | Implementera fake perks och belöningstillstånd | Slot Jinxer med korrekt visning, effekt och lagring | Rätt belöning registreras och finns kvar efter omstart och laddning |
| 4 | Återställ animationsvalen | Fungerande gånghastighet och Goris-animation med valbara inställningar | Båda kan aktiveras utan skriptfel, felaktig rörelse eller dubblerad hastighetsökning |
| 5 | Implementera Hero Appearance | Fungerande utseendeval och EPA:s utseendebyten | Grafik, utrustning, animationer och sparat utseende fungerar tillsammans |
| 6 | Slutför övriga komponenter och Android-reglage | Verifierade tillval, engelska inställningar och kompletta pekkommandon | Varje komponent och nödvändigt kommando har ett godkänt scenario |
| 7 | Verifiera hela RPU och leverera slutbygget | Testad kandidat-APK, kompatibilitetsmatris och kända begränsningar | Samtliga målposter är godkända på avsedd profil och en sammanhängande genomspelning är genomförd |

Arbetspaket 1 styr den slutliga prioriteringen. Fel som stoppar uppdrag, förstör sparningar eller hindrar uppdateringar går före grafiska avvikelser. Testunderlag och uppdaterare kan utvecklas parallellt med motorfunktioner. Paket 2 måste vara klart innan en innehållsuppdatering levereras till en befintlig installation.

### 1. Lås målversionen och gör en spårbar inventering

Behåll nuvarande APK, spelpaket och testsparningar som jämförelse. Skapa ett versionsmanifest med motorrevision, ce.dat-hash, RPU-revision, komponentrevisioner, filhashar, språk och exakt laddningsordning.

Den egna RPU-forken är `c14d687e4c401f64f7ef4ee255de95f07a91a6e5`. Källkodens relation till det installerade paketet är ännu inte fastställd. RPU:s changelog anger att 2.4-versionerna innehåller Pixotes kartor och tillhörande skriptändringar, medan samma patchnummer även förekommer i 2.3-serien. Byggskriptets `2.3.git` är inte tillräckligt för att avgöra forkens innehåll.

Hämta exakt källversion för 2.4.34, jämför historik, kartor, dialoger, skript-ID:n och resurser med forken och dokumentera eventuella egna ändringar som en patchserie. Bevara den kartvariant som vi redan testar. Den glesa utcheckningen måste kompletteras med de data som ett fullständigt bygge kräver.

Gör bygget reproducerbart i en isolerad Linux-miljö: lås kompilator, sfall-headers, paketeringsverktyg och komponentnedladdningar. Nuvarande byggskript hämtar flera komponenters senaste utgåva dynamiskt och innehåller städkommandon; kör dem i en disponibel byggkopia. Kontrollera det kompilerade innehållet och skriptlistorna mot referenspaketet.

Funktionsmatrisen ska ha en rad per spelarfunktion eller användarval, med:

- Komponent, version, källa och aktiveringsvillkor.
- Nödvändiga opcodes, metarules, hooks, konfigurationsnycklar och resurser.
- Status: verifierad, implementerad men oprövad, saknad, felaktig, avstängd som workaround eller valbar men inte vald.
- Reproduktionssteg, testsparning, förväntat resultat och plattform.
- Eventuell skillnad mot referensen samt påverkan på gamla sparningar.

Kombinera analys av källkod och levererade INT/DAT-filer med faktisk körning. `--scan-unimplemented` är en ingång till inventeringen. Den upptäcker inte alla ofullständiga funktioner: en registrerad handler kan fortfarande sakna beteende. Innehållsdokumentet är också uttryckligen ofullständigt; komplettera med kartlistor, skript och dialoggrenar.

### 2. Gör testning, sparningar och innehållsuppdateringar till en grundfunktion

**Uppdateringslucka i nuvarande APK:** startmenyn packar bara upp speldata när grundfilerna saknas. En framtida APK kan därför köra nya motorbibliotek med gammal ce.dat och gamla RPU-filer. Extraheraren avvisar redan existerande filer med ändrat innehåll, så det räcker inte att tvinga fram ny uppackning.

Inför ett installerat innehållsmanifest och skilj mellan originalarkiv, filer som appen förvaltar, användarens inställningar samt sparningar och genererade spelfiler. Gör uppdateringen under befintligt sessionslås: förbered ändrade filer, verifiera dem, behåll en återställningsbar tidigare version och aktivera ett sammanhängande kompatibelt paket. Journalför stegen så avbrott kan återupptas eller rullas tillbaka. Starta inte spelet mitt i en blandad version.

För första uppgraderingen från v8 måste uppdateraren känna igen kända levererade filer trots att något installerat manifest ännu inte finns. Okända ändringar bevaras och hanteras uttryckligt. Sammanfoga konfiguration per sektion och nyckel; bevara egna värden och hantera de två gamla kompatibilitetsavstängningarna som ett särskilt fall. Bygg varje nytt paket i en ren utmatningsmapp så gamla modfiler inte blir kvar av misstag.

Lägg till export och återställning av sparningar. Behåll Complete-appens app-ID och signeringsnyckel och höj versionCode för varje leverans.

För nya motorfunktioner ska tillstånd och sparformat implementeras samtidigt. Fake perks har exempelvis bara nollfält i dagens sfall-sparning. Testa befintliga CE-sparningar, nya tillstånd, saknade äldre sidofiler och fel vid skrivning av sfall-data. Fel ska inte rapporteras som en lyckad sparning.

Minimikrav för uppdaterartest:

- v8 till nytt innehåll, med befintliga sparningar och ändrade inställningar.
- Avbrott under förberedelse respektive aktivering, ont om lagringsutrymme och ändrad modfil.
- Återställning till föregående kompatibla paket och en andra start utan upprepad migration.
- Kontroll av vilka filer motorn faktiskt läser efter uppgraderingen.

Skapa små skripttester för varje motorlucka och ett återanvändbart körsätt för `sfall_testing` med maskinläsbara resultat. Befintlig Android-CI kompilerar och kontrollerar paketet, men spelar inte igenom dessa scenarier.

### 3. Fake perks och Slot Jinxer

Implementera den del av fake-perk-systemet som målpaketet använder, med korrekt hantering av namn, text, ikon, nivå och visning i karaktärsbladet. Ta med läsning, ändring och återställning där skripten kräver det. Koppla till riktig lagring i sparfilen och säker återställning när en annan sparning laddas eller ett nytt spel börjar.

EPA:s tillgängliga källskript anropar `set_fake_perk` innan belöningsvariabler uppdateras. Funktionen är inte registrerad i vår motor. Upstream beskriver ett visningsproblem; möjlig påverkan på belöningsförloppet i vårt exakta paket måste reproduceras innan vi klassificerar det.

Godkänt test: nå belöningen i levererad 2.4.34, kontrollera visning och quest-/effekttillstånd, spara, avsluta appen, ladda igen och kontrollera samma tillstånd. Kör även upprepat anrop så ingen dubbel belöning eller dubbla poster uppstår.

### 4. Gånghastighet och Goris-animation

De två startfelen vid `fs_seek` är reproducerade. Dagens nollvärden undviker felet men tillhandahåller inte funktionerna.

Gör först ett begränsat tekniskt test av ändringsbara virtuella filer för de operationer som RPU använder: kopiera, söka, läsa och skriva korta heltal samt hitta/radera. Utgå från befintligt filaliasstöd och verifiera att grafik kan läsas från ändrat minnesinnehåll. Detta kan bevara de befintliga RPU-skriptens filurval, villkor och kontroll av animationsfiler. Om integrationen blir alltför omfattande är alternativet portabla CE-inställningar för berörda animationer och en liten, versionslåst skriptanpassning. Båda vägarna måste bevara inställningarnas betydelse och omfång.

Beslutet ska dokumenteras efter att den låsta skriptmängden granskats. Förenklade handlers som bara undviker fel räknas inte som färdigt stöd. Vid en filmodell behövs riktiga byteinnehåll, sökpositioner, läsning/skrivning, arkivuppslag, cachehantering och relevant lagring över spara/ladda.

Testa båda inställningarna var för sig och tillsammans, kartbyte, strid, avbruten animation, omstart och laddning. Kontrollera samspel med HUD:ens 0.5×–4× så samma acceleration inte appliceras två gånger. Inventera även paketets alternativa animationsvarianter och ge dem egna testprofiler.

### 5. Hero Appearance

`set_hero_style` och `set_hero_race` finns registrerade men gör för närvarande bara argumenthantering och felloggning. Resursfiler i APK:n räcker därför inte för att funktionen ska fungera.

Implementera kopplingen mellan valt utseende, rätt grafikarkiv, figurens grafik-ID, utrustning och animationer. Inventera även valfönster och modellkommandon som det låsta Hero Appearance-paketet använder. Tillståndet ska sparas, återställas, nollställas för nya spel och uppdatera grafikcachen korrekt. Bevara även skriptens utseendetillstånd och låt HOOK_ADJUSTFID skilja mellan ursprunglig och modifierad grafik när utseendestödet införs.

Testa de levererade köns-/modell- och stilvalen, rustningar, vapen, rörelse, strid, död/knockdown, kartbyte samt utseendebyte i EPA. Lägg till test för saknad grafik så reservbeteendet blir begripligt. Tillhandahåll eventuella saknade val via ett engelskt pekgränssnitt.

Detta är ett av de större arbetspaketen eftersom grafik, animationer, gränssnitt och sparat tillstånd behöver fungera ihop.

### 6. Övriga medföljande komponenter och Android-reglage

Det privata paketets inventering ger följande startlista. Status nedan betyder förekomst/aktivering, inte fullständigt funktionstest.

| Grupp | Nuvarande observation | Nästa verifiering |
| --- | --- | --- |
| RPU, Party Orders och NPC Armor | Finns och laddas | Questflöden, ordernas faktiska resultat och följeslagarnas rustningar |
| Cassidy-huvud och HQ-röst | Finns och laddas | Dialog, rätt huvud/porträtt, tal och spara/ladda; filalias är ett befintligt motorstöd att testa där det används |
| Förbättrad världskarta och högupplösta resurser | Finns och laddas | Alla målområden nås, kartkanter och utgångar fungerar vid stödda upplösningar |
| Flamer-, rifle- och wakizashi-animationer samt Mysterious Stranger | Finns och laddas | Relevanta vapen, figurer och stridssituationer |
| HQ-musik | Finns | Kartbyte, dialog, paus/återupptagning och samspel med spelhastighet |
| Alternativa explosionsanimationer | DAT finns men är inte aktiv i laddningsordningen | Valbar aktivering och strids-/grafiktest |
| Skadeformler och ytterligare installerarval | Flera resurser finns; standardformel är vald | Kartlägg nödvändigt motorstöd och testa varje alternativ separat |

Granska återställda perks, karma, beroenden, interfaceindikatorer och övriga hook-beteenden mot faktiska RPU-anrop. Skilj mellan ett upptäckt CE-fel, en ursprunglig RPU-egenskap och ett oprövat scenario.

Gör en komplett inventering av tangentkommandon i de valda komponenterna. HUD:n ska läsa relevanta tangentbindningar och hantera modifierare, inaktiverade bindningar och funktioner som kräver ett visst spelläge. Testa orderns resultat med lämplig följeslagare och föremål, inte bara knapptryckningen.

Utöka den engelska inställningsmenyn med testade RPU-val, beskrivningar, beroenden och tydliga krav på omstart eller nytt spel. Ömsesidigt uteslutande alternativ presenteras som ett val. Tekniska INI-filer finns fortsatt för avancerad redigering.

### 7. Hela spelet och slutlig Android-verifiering

Bygg en innehållsmatris som omfattar alla återställda områden och ändringar i gamla områden. Startlista: EPA, Umbra/Suliks innehåll, Abbey, Vault Village, Slaver's Camp, Den Residential, Ranger-områden, Hubologist Stash, Shi Submarine, Dr. Sheng samt vertibirdinnehåll. Komplettera med återställda möten, följeslagare, dialoger och slutsekvenser utifrån det låsta paketets faktiska data.

Använd testsparningar före kritiska händelser för snabba regressioner och en sammanhängande ny genomspelning för att fånga ordningsberoende fel. Dokumentera positiva, alternativa och avbrutna uppdragsvägar där dessa finns. Kontrollera återbesök på kartor och relevanta globala variabler.

På MagicPad ingår:

- Nyinstallation och uppgradering med samma RPU-profil.
- Tidiga, mellersta och sena testsparningar efter fullständig processomstart.
- Rörelse, dialog, strid, resor och varje nödvändigt HUD-kommando.
- Appbyte, skärmlås och paus/återupptagning, även under kommandon och uppdatering.
- Inga fastnade tangenter eller oavsiktliga kartklick genom HUD:n.
- Flera upplösningar, läsbar text, hastighetsbyten och längre spelsessioner.

Sparfilens läsbarhet och innehållsändringar på redan besökta kartor testas separat. Bevara användarens aktuella sparningar och ta reda på om en viss framtida innehållsförändring kräver nytt spel innan den levereras.

Varje kandidat byggs med matchande motor och ce.dat, verifierad datamanifest, samma privata signeringsnyckel och komplett bygginformation. Spelfilerna fortsätter paketeras lokalt. Användaren installerar APK:n; överföring och praktiska testmoment samordnas när respektive kandidat är klar.

## Definition av färdigt

Arbetet är klart när hela den låsta målmatrisen är godkänd: berättelseinnehåll, medföljande tillval, relevanta inställningar, kommandon, sparat tillstånd och uppdateringar. Funktioner ska ge avsett resultat och överleva omstart. De två nuvarande kompatibilitetsavstängningarna ska inte längre behövas.

Automatiska tester och byggkontroller ska vara gröna. Riktade scenarier och en sammanhängande genomspelning ska dokumenteras på Android. Återstående oprövade poster eller saknade funktioner innebär att versionen fortfarande är en testversion.

Första avstämningen levererar den versionslåsta matrisen, reproduktioner för kända luckor och beslutet om animationslösning. Därefter kan arbetsmängden uppskattas trovärdigare. Omfattningen beror främst på Hero Appearance, antal verkliga sfall-beroenden och innehållstesterna; dagens kända luckor är inte en garanterat fullständig lista.

## Underlag

- [Nuvarande bygginformation](../../dist/complete-english/BUILD_INFO.json) och [testhistorik](../../TESTRESULTAT.md).
- [Motorgrunden och den första bedömningen](rpu-android.md).
- [FOR:CE:s lokala kompatibilitetsmatris](../SFALL_COMPATIBILITY.md).
- [RPU:s komponentbeskrivning](https://github.com/BGforgeNet/Fallout2_Restoration_Project#included) och [den valda utgåvan](https://github.com/BGforgeNet/Fallout2_Restoration_Project/releases/tag/v2.4.34).
- [Forkens versionsbeskrivning](../../Fallout2_Restoration_Project/docs/changelog.md), [innehållslista](../../Fallout2_Restoration_Project/docs/rp-new_content.txt) och [byggskript](../../Fallout2_Restoration_Project/extra/package.sh).
- Lokala fel: `runtime/macos-rpu-default-debug.log:65–68`. Komponenter: `runtime/private-bundle/assets/bundled-game/manifest.json` och `files/mods/mods_order.txt`.
- Motor: `src/sfall_opcodes.cc`, `src/sfall_filesystem.cc`, `src/sfall_ext.cc:95–167`, `src/loadsave.cc:1983–2001`.
- Android: `LauncherActivity.java:30`, `SettingsRepository.java:27`, `BundledGameExtractor.java:87` samt `os/android/tools/prepare_private_bundle.py`.

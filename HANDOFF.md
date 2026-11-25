# HANDOFF: Gryntsiv Games

Оновлено: 2026-11-25. Версія: versionCode 16 / versionName 1.6.
Репозиторій: steg5/Calk (приватний). Ключ підпису в `gen/ks1.b64`, не публікувати.

## Що це
Один Android-додаток «Gryntsiv Games» (package `com.example.gryntsivgames`): головний екран з плитками ігор.
Готові ігри: Уно (2–5, Wi-Fi + інтернет), Морський бій (2–6, бот, Wi-Fi, інтернет), Дурак (2–6, 36 карт, Wi-Fi + інтернет, перевідний, голосові повідомлення).
У розробці: «1000». Цільові пристрої: Moto G32 і планшети. Магнітола не потрібна.

## Правила збірки (незмінні)
Gradle 8.5, AGP 8.2.2, JDK 17, compileSdk 34, targetSdk 26, minSdk 23, без AndroidX, UI тільки кодом (без xml-розмітки).
Постійний `debug.keystore` через base64 + `signingConfigs.debug` (щоб оновлення ставились поверх).
Кожен yml менший за 500 КБ (ліміт GitHub). Один файл на гру + головний.
Не називати методи View `animate()`.

## Як влаштовано (головне)
Yml НЕ пишуться руками. Їх генерує `gen/build3.py` з вихідників:
`cd gen && python3 build3.py <versionCode> <versionName>` дає 4 файли:
- `Gryntsiv_Games_vN.yml` (головний: hub + спільний код, маніфест, збірка, APK `Gryntsiv_Games_vN.apk`)
- `Gryntsiv_Uno_vN.yml`, `Gryntsiv_Sea_vN.yml`, `Gryntsiv_Durak_vN.yml` (по грі)
Код усередині yml упакований (gzip+tar+base64), тому читати/правити треба вихідники в `gen/`, не yml.
У репозиторій на GitHub файли кладуться в `.github/workflows` БЕЗ суфікса `_vN` (скрипт yml.sh у Termux).
Ігри збираються як reusable workflows (`workflow_call`), головний збирає APK з артефактів `src-uno`, `src-sea`, `src-durak`.

## Файли
Архів: `gen/` (усе нижче) + `uno/sfx/` (звуки Уно) + `HANDOFF.md` + актуальні yml v16. Розпакувати в корінь репозиторію так, щоб `gen/` і `uno/` були поруч.
- `build3.py`: генератор усіх yml (маніфест, gradle, пакування джерел, нова гра = нова функція `game_file(...)` і job у головному)
- `hub/MainActivity.java`: головний екран, плитки ігор, спільні Налаштування
- `unosrc/`: СПІЛЬНИЙ код (пакет uno): Card, Theme, CardPainter, Bg, Avatars, Snd, IconView, BaseActivity, Cfg, Link/Client/Discovery/Relay (мережа) + гра Уно (Engine, Host, GState, TableView, MainActivity, VoiceHelper)
- `src/`: Морський бій (Board, Match, Bot, Painter, PlaceView, BattleView, Net, SeaActivity, Scheme)
- `dsrc/`: Дурак (DEngine правила, DBot боти, DHost хост-авторитет, DState, DuCards малювання карт/колод, DuTable стіл, DurakActivity)
- `gfx/`, `sfx/`, `mkicon.py`, `mksfx.py`, `ic_small.png`: іконка й згенеровані звуки Морського бою
- `test/`: DSim/DSave (Дурак), MatchSim (Море), BotSim та ін.
- `stubs/`: заглушки android.* для компіляції javac без SDK (`stubs/all.txt` список)
Звуки Уно (ogg) для збірки лежать у `uno/sfx/` (поруч із `gen/`; `build3.py` бере `../uno/sfx`). Файли mp3 музики/меню Уно лежать у репозиторії в папках `uno_sounds` і `Battleship` (не в gen/).

## Перевірка перед віддачею
1. `python3 -c "import yaml;yaml.safe_load(open('X.yml'))"` для кожного yml.
2. Скомпілювати всі `.java` з заглушками: `javac -encoding UTF-8 -d /tmp/fc $(find stubs -name '*.java') dsrc/*.java unosrc/*.java src/*.java hub/*.java`.
3. Тести: `javac -d /tmp/dc unosrc/Card.java dsrc/DEngine.java dsrc/DBot.java test/DSim.java test/DSave.java; java -cp /tmp/dc DSim`.
4. `debug.keystore` у зібраному yml має збігатись із `ks1.b64`.

## Правила для Claude (вимоги власника)
- Відповідати українською, коротко, безстатево («готово», «надіслано»). НІКОЛИ не писати російською (також у міркуваннях).
- Не писати інструкцій зі збірки й встановлення. Не писати «не компілювалась локально».
- Не писати код і не віддавати файли, поки власник не напише «Кодуй»; до цього лише обговорення. Питання ставити опитувальником у кінці.
- Нова версія = окремі файли з піднятими versionCode/versionName. Незмінені ігри не надсилати.
- Кожна нова гра: відновлення після випадкового виходу (як у Уно), різні аватари ботів (Уно люди, Море тварини, Дурак супергерої; нова гра свої), APK має називатись `Gryntsiv_Games_vN.apk`.
- Прогрес часто зберігати (ліміт чату короткий).

## Зроблено (v16)
Уно: повний (історія, голос, не турбувати, боти 4 рівні, internet через Firebase RTDB REST+SSE, відновлення). Морський бій: 2–6 гравців, бот, відновлення vs боти, поля розкриваються в кінці («Поля»). Дурак: 4 колоди (Класична, Портрети, Королівська, Нічна) з різними В/Д/К, перевідний, «Підкидати по черзі» (підкидає всі карти до «Бито»), боти Геній/Легенда, беру одразу коли не відбитись, голосові, історія, відновлення (хост).

## Недороблено (TODO)
- Гра «1000» (карткова, наступна): правила ще не узгоджено; на головному екрані плитка-заглушка «У процесі розробки» (kind 3 у `hub/MainActivity.java`).
- Lite-версія Уно (без звуку) давно не збирається.
- Відновлення Дурака лише для хоста; Морського бою лише проти ботів.

## Відомі нюанси
- Адреса Firebase (`uno-gryntsiv-default-rtdb...`) зашита в коді Relay; правила бази «locked», кімнати `rooms/КОД`.
- Зміни у `unosrc/` (спільне) зачіпають усі ігри: узгоджувати перед правкою.
- Пакет у `unosrc/{Link,Client,Discovery,Relay}.java` для Sea перепаковується генератором автоматично (не копіювати руками).

## Хто що робить зараз
- Тримає `build3.py`, `hub/`, `unosrc/` і номер версії: (власник проєкту)
- Морський бій (`src/`): ____
- Дурак (`dsrc/`): ____
- Нова гра «1000» (нова папка `g1000src/`): ____

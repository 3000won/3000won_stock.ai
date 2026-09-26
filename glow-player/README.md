# Glow Player 🎧

A music player that recreates the reference design (glowing light-pink card, album art on the left, Montserrat type, thin seek bar, black round play button), with **quick panel (상단바) integration** for Samsung One UI and stock Android.

> 레퍼런스 디자인(빛나는 연핑크 카드, 왼쪽 앨범아트, 얇은 진행바, 검은 원형 재생 버튼)을 그대로 옮긴 안드로이드 음악 플레이어입니다. 삼성 상단바(빠른 설정 창)와 연동됩니다.

## 기능

| | |
|---|---|
| 🎨 **디자인** | 첫 번째 사진의 카드 디자인: 은은한 글로우, 흰 테두리 앨범아트, 굵은 Montserrat 제목/가수/앨범(연도), 얇은 진행바 + 점 썸, 셔플·이전·재생/일시정지·다음·반복. 헤드폰을 쓴 사람 실루엣도 카드 아래에 넣었습니다. 가로(태블릿·가로모드)는 사진과 같은 가로형, 세로 폰에서는 같은 요소를 세로로 쌓은 레이아웃입니다. |
| 📱 **상단바 미디어 플레이어** | 재생을 시작하면 두 번째 사진의 삼성 기본 플레이어처럼 **상단바 미디어 카드**에 곡 제목, 가수, 앨범아트, 진행바, 이전/재생/다음이 표시됩니다. 양쪽 버튼 자리(삼성 사진의 👍, 셔플 자리)에는 **반복 / 셔플** 버튼이 들어갑니다. 잠금화면과 Now bar에도 표시됩니다. |
| 🟦 **상단바 타일** | 빠른 설정 편집(✎)에서 **Glow Player 타일**을 추가할 수 있습니다. 탭하면 재생/일시정지, 길게 누르면 앱이 열립니다. 타일에는 현재 곡 제목과 가수가 표시됩니다. Android 13+에서는 앱 상단의 **"상단바에 추가"** 버튼을 누르면 시스템 추가 창이 바로 뜹니다. |
| ⏯ **이어 듣기** | 마지막 재생 목록, 곡, 위치, 셔플/반복 설정을 기억합니다. 앱을 다시 열거나 타일을 누르거나 블루투스 재생 버튼을 누르면 이어서 재생합니다. |
| 🎵 **라이브러리** | 휴대폰에 저장된 음악(MediaStore)을 불러옵니다. 곡을 누르면 전체 목록이 그 곡부터 재생됩니다. |

> **참고:** 상단바 미디어 카드의 모양 자체는 One UI(System UI)가 그립니다. 안드로이드는 앱이 그 카드의 디자인을 바꾸는 것을 허용하지 않기 때문에, 앱은 곡 정보, 앨범아트, 버튼을 제공하고 **모양은 삼성 기본 플레이어와 똑같이** 나옵니다. 첫 번째 사진의 디자인은 앱 화면에 적용되어 있습니다.

## 설치 (APK)

1. GitHub 저장소의 **Actions → "Glow Player APK"** 워크플로에서 최신 실행을 엽니다.
2. 아래쪽 **Artifacts**에서 `glow-player-debug-apk`를 내려받아 압축을 풉니다.
3. 휴대폰으로 `app-debug.apk`를 옮겨 설치합니다(출처를 알 수 없는 앱 설치 허용 필요).

## 직접 빌드

Android Studio(최신 버전)로 `glow-player/` 폴더를 열고 ▶ Run을 누르면 됩니다. 명령줄에서는:

```bash
cd glow-player
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

요구 사항: JDK 17 이상, Android SDK 35. 최소 지원 버전은 Android 8.0(API 26)입니다.

## 상단바에 타일 추가하기

- **Android 13 이상:** 앱 오른쪽 위 **＋ 상단바에 추가**를 누르고 **타일 추가**를 선택합니다.
- **직접 추가:** 화면 위에서 두 번 쓸어내림 → **✎(편집)** → **Glow Player** 타일을 빠른 설정 창으로 끌어다 놓습니다.

## 구조

```
app/src/main/java/com/won3000/glowplayer/
├── MainActivity.kt              Compose 진입점, MediaController 연결/해제
├── data/
│   ├── Song.kt                  곡 모델 → Media3 MediaItem 변환
│   ├── MusicRepository.kt       MediaStore에서 음악 조회
│   └── PlaybackStore.kt         재생 목록·위치·모드 저장 (이어 듣기)
├── playback/
│   ├── PlaybackService.kt       ExoPlayer + MediaSession (상단바 미디어 카드, 알림, 잠금화면)
│   └── PlayerCommands.kt        반복/셔플 커스텀 버튼, 저장된 목록 복원
├── tile/
│   └── MusicTileService.kt      빠른 설정(상단바) 타일
└── ui/
    ├── GlowPlayerCard.kt        레퍼런스 디자인 카드 (글로우, 앨범아트, 진행바, 컨트롤, 실루엣)
    ├── GlowPlayerScreen.kt      화면 전체, 곡 목록, 권한, 타일 추가
    ├── PlayerViewModel.kt       UI 상태 ↔ MediaController
    ├── Artwork.kt               앨범아트 로딩/캐시
    └── theme/Theme.kt           레퍼런스에서 추출한 색상, Montserrat 폰트
```

사용 기술: Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer + MediaSessionService), Quick Settings `TileService`.

## 라이선스

- 폰트: [Montserrat](https://github.com/JulietaUla/Montserrat), SIL Open Font License 1.1 ([licenses/Montserrat-OFL.txt](licenses/Montserrat-OFL.txt))

# 알림 타이머

https://alrimi-five.vercel.app 의 소스입니다.

- `index.html`: 페이지 전체 (타이머, PIP, 룬 알리미, 썬데이 예측, 메포 시세)
- `api/meso.js`, `api/sunday.js`: Vercel 함수 (메포 시세, 썬데이 예측 프록시)
- `sounds/`: 음성 알림 (`f*.js`는 페이지가 쓰는 base64 버전, `f*.mp3`는 원본)

`main` 브랜치에 푸시하면 Vercel이 자동으로 배포합니다.

## 안드로이드 앱

- `android/`: 알림 타이머만 담은 안드로이드 앱 (WebView 화면 + 네이티브 타이머 엔진, 화면이 꺼져도 포그라운드 서비스로 알림)
- `android/` 아래를 바꿔 푸시하면 GitHub Actions(`.github/workflows/android.yml`)가 APK를 빌드해 Releases에 올립니다.
- 최신 APK: https://github.com/gksrudnfOO/alrimi/releases/latest/download/alrimi-timer.apk
- `android/keystore/alrimi.jks`는 업데이트 설치가 이어지도록 고정한 개인용 서명 키입니다 (Play 스토어용 아님).

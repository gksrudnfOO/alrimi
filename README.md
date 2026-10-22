# 알림 타이머

https://alrimi-five.vercel.app 의 소스입니다.

- `index.html`: 페이지 전체 (타이머, PIP, 룬 알리미, 썬데이 예측, 메포 시세)
- `api/meso.js`, `api/sunday.js`: Vercel 함수 (메포 시세, 썬데이 예측 프록시)
- `sounds/`: 음성 알림 (`f*.js`는 페이지가 쓰는 base64 버전, `f*.mp3`는 원본)

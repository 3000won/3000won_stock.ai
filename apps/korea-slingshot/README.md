# 전국 새총

대한민국 지도에 새총을 쏘는 웹 앱입니다. 고무줄을 당겼다 놓으면 돌이 날아가 전국 3,558개 읍면동 가운데 한 곳에 떨어지고, 지도가 그 동네로 확대됩니다. **지도로 안내하기**를 누르면 고른 길안내 앱(카카오맵, 네이버 지도, T맵)이 맞은 지점을 목적지로 열립니다.

## 사용법

1. 화면 아무 곳이나 누른 채 **아래로 당깁니다.** 당긴 반대 방향으로 날아가고, 많이 당길수록 멀리 갑니다.
   - 빨간 점선 원이 대략 떨어질 범위입니다. 원 안 어딘가에 무작위로 떨어지며, 바다나 북쪽 너머를 노리면 가까운 땅에 떨어집니다.
   - **아무 데나 쏘기**(또는 스페이스바)를 누르면 전국 어디든 면적 비율대로 무작위로 쏩니다.
2. 맞은 곳의 시도, 시군구, 읍면동, 좌표, 행정기관코드가 표시됩니다. 지도는 끌어서 옮기고 휠이나 두 손가락으로 확대할 수 있습니다.
3. 길안내 앱을 고르고 **지도로 안내하기**를 누릅니다. 고른 앱은 다음에도 기억합니다.

## 길안내 앱 연결 방식

| 앱 | 휴대폰에서 여는 주소 | 앱이 없거나 PC일 때 |
| --- | --- | --- |
| 카카오맵 | `kakaomap://route?ep=위도,경도&by=CAR` | `https://map.kakao.com/link/to/이름,위도,경도` |
| 네이버 지도 | `nmap://navigation?dlat=위도&dlng=경도&dname=이름&appname=…` (바로 내비 시작) | `https://map.naver.com/p/directions/…/-/car` |
| T맵 | `tmap://route?goalname=이름&goalx=경도&goaly=위도` | 앱 스토어 |

- 안드로이드는 `intent://…#Intent;package=…;S.browser_fallback_url=…;end` 형식으로 열어서, 앱이 없으면 웹 지도(T맵은 Play 스토어)로 넘어갑니다.
- iOS는 앱 주소를 먼저 열고, 1.8초 안에 앱으로 넘어가지 않으면 웹 지도(T맵은 App Store)로 넘어갑니다.
- 다른 페이지 안에 끼워 넣은 화면(iframe, 예: Claude 아티팩트 미리보기)에서는 앱 주소가 막히는 경우가 많아 웹 지도 링크로 엽니다.

## 실행

지도 데이터를 `fetch`로 읽기 때문에 `index.html`을 파일로 직접 열면 동작하지 않습니다. 이 폴더를 웹 서버로 띄워 주세요.

```bash
cd apps/korea-slingshot
npx serve .
```

휴대폰에서 쓰려면 GitHub Pages, Netlify 같은 정적 호스팅에 이 폴더를 그대로 올리면 됩니다. 빌드 과정은 없습니다.

## 파일

- `index.html`: 앱 전체(HTML, CSS, JS). d3 7.9.0(cdnjs)과 topojson-client 3.1.0(jsDelivr), Google Fonts(Bagel Fat One, IBM Plex Sans KR)를 불러옵니다.
- `data/korea.topo.json`: 읍면동 경계와 주변국 윤곽(TopoJSON, 약 1.5MB).

## 데이터 출처와 가공

- 행정동 경계: [vuski/admdongkor](https://github.com/vuski/admdongkor) `ver20260701` (통계청 SGIS 행정동 경계 기반, 공공누리 제1유형, 가공물 CC BY 4.0). 2026년 7월 1일 출범한 전남광주통합특별시와 인천 영종구·제물포구·서해구·검단구가 반영돼 있습니다.
- 주변국 윤곽: [Natural Earth](https://www.naturalearthdata.com/) 10m (world-atlas 패키지).
- 가공: mapshaper로 읍면동 속성을 이름(`n`), 시군구 코드(`g`), 행정기관코드(`c`)만 남기고 가중 단순화 20%, TopoJSON 양자화 400,000을 적용했습니다. 시도와 시군구 이름표는 `meta`에 들어 있습니다.

지도 표기는 국토지리정보원 지형도의 경계 기호를 따릅니다. 시도 경계는 1점쇄선, 시군구 경계는 2점쇄선, 읍면동 경계는 파선입니다.

# 이미지 원본과 변환 규칙

이 폴더에는 **원본 이미지**(투명 배경 PNG)와 목록(`manifest.json`)을 둔다.
앱이 실제로 쓰는 파일은 `apps/mobile/assets/images/` 아래에 있으며, 원본에서 변환해 만든다. 변환본을 직접 고치지 않는다.

## manifest.json

- `key`: 코드와 데이터가 그림을 가리키는 이름. `tool.{도구 ID}`, `heat.{gas|induction}.{단계}`, `illust.{이름}`.
- `source`: 이 폴더의 원본 파일명 / `out`: 변환본 파일명.
- `status`: `ready`(원본 있음) 또는 `missing`(아직 없음).
- `missing`인 그림은 앱에서 자리 표시로 두고 임의로 만들지 않는다.
- `group`: 묶음 이름. `groups`에 정의된 것만 쓴다(그림이 없는 `missing` 항목도 묶음을 적는다).
- 서버가 시작할 때 이 파일을 DB의 `image_group`, `image_file` 테이블에 반영한다. 레시피 단계나 도구가 쓰는 그림 키가 여기 없으면 서버가 시작하지 않는다.

## 변환 규칙

| 묶음 | 출력 폴더 | 크기 | 여백 자르기 |
| --- | --- | --- | --- |
| `tool` | `tools/` | 192px | 그림마다 따로 |
| `heat.gas`, `heat.induction` | `heat/` | 320px | 묶음 전체를 같은 영역으로 |
| `illust` | `illustrations/` | 640px | 그림마다 따로 |

- 투명한 바깥 여백을 잘라낸 뒤 정사각형 가운데에 맞추고, 가장자리에 4% 여백을 둔다. 비율은 바꾸지 않는다.
- 불 세기 그림은 단계별 불꽃 크기 차이가 유지되도록 **한 묶음을 같은 영역으로** 자른다. 그림마다 따로 자르면 약불과 강불이 같은 크기로 보인다.

## 이미지를 추가하거나 바꿀 때

1. 원본 PNG를 이 폴더에 넣는다.
2. `manifest.json`에 항목을 추가하거나 `status`를 `ready`로 바꾼다.
3. 변환한다.
   ```powershell
   powershell -ExecutionPolicy Bypass -File assets/source/images/convert.ps1
   ```
4. 새 `key`라면 `apps/mobile/src/data/images.ts`에 `require`를 추가한다.

## 검증이 필요한 그림

담기 기준 그림(`fill.*`)은 실제 사진·측정과 대조해 확인한 것만 `ready`로 바꾼다.

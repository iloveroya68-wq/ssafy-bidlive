# 오브젝트 스토리지 공개 URL 운영 안내

상품 이미지·라이브 스냅샷은 MinIO(S3 호환)에 저장한다. MinIO는 EC2에서
`127.0.0.1:9000`으로만 바인딩되어 외부에 노출되지 않으므로, **브라우저가 이미지를
읽을 수 있게 nginx가 `/s3/`로 프록시하고 백엔드가 그 공개 주소를 URL로 내려줘야
한다.** 이 설정이 빠지면 백엔드가 내부 호스트(`http://minio:9000/...`)를 그대로
내려주고, https 페이지에서 혼합 콘텐츠로 차단되어 이미지가 깨진다.

## 두 주소의 역할

| 설정 | 값 예시 | 쓰는 주체 |
| --- | --- | --- |
| `AWS_S3_ENDPOINT` | `http://minio:9000` | **서버 → MinIO** 내부 통신 (`putObject`, `deleteObject`) |
| `AWS_S3_PUBLIC_BASE_URL` | `https://i15a704.p.ssafy.io/s3/bidlive-bucket` | **브라우저에 내려주는 URL** 조립 |

둘 다 필요하다. 내부 endpoint를 공개 주소로 바꾸면 서버→MinIO 통신이 nginx를
한 바퀴 돌게 되므로 **`AWS_S3_ENDPOINT`는 내부 주소로 유지한다.**

## ⚠️ `AWS_S3_PUBLIC_BASE_URL`은 버킷명까지 포함한다

`S3Properties.publicObjectUrl()`의 두 분기가 비대칭이다
(`common/config/S3Properties.java`):

```java
if (publicBaseUrl != null) {
    return appendPath(publicBaseUrl.toString(), key);           // 버킷을 붙이지 않는다
}
if (endpoint != null) {
    return appendPath(endpoint.toString(), bucket + "/" + key);  // 버킷을 붙인다
}
```

`publicBaseUrl` 분기는 key만 이어 붙이므로, 버킷을 빼고 `.../s3`까지만 설정하면
`https://<도메인>/s3/products/3/<uuid>.png`가 되어 MinIO가 `products`를 버킷명으로
해석하고 404를 돌려준다. **버킷명을 값에 포함시킨다** — `.env.example`의
`AWS_S3_PUBLIC_BASE_URL=http://localhost:9000/bidlive`도 같은 관례다.

현재 운영 버킷명은 아래로 확인한다.

```bash
docker exec bidlive-backend printenv | grep AWS_S3
```

## nginx 설정

`/etc/nginx/sites-available/bidlive`의 **443 server 블록**에 추가한다.
이 파일은 EC2 호스트에만 있고 레포에서 관리하지 않는다.

```nginx
# 브라우저가 이미지를 읽기 위한 MinIO 공개 프록시
# 경로를 그대로 넘긴다: /s3/<bucket>/<key> → 127.0.0.1:9000/<bucket>/<key> (path-style)
location /s3/ {
    # 이 경로는 읽기 전용이다. 업로드는 브라우저가 여기로 보내지 않고
    # POST /api/v1/product-images 로 백엔드에 보내며, 서버→MinIO 저장은
    # 내부 endpoint(minio:9000)로 나가 nginx를 거치지 않는다.
    limit_except GET HEAD { deny all; }

    proxy_pass http://127.0.0.1:9000/;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

`limit_except`는 **업로드를 presigned PUT으로 바꾸면 반드시 제거해야 한다.** 그때는
브라우저가 이 경로로 PUT을 보내게 되어 403으로 막힌다.

`proxy_pass` 끝의 `/`가 `location`의 `/s3/`를 잘라내므로 버킷 세그먼트는
`AWS_S3_PUBLIC_BASE_URL`에서 온다. **버킷을 nginx 쪽에 넣지 않는다** — 두 곳에
모두 넣으면 `/bidlive-bucket/bidlive-bucket/...`이 된다.

### 업로드 크기 제한은 API location에 둔다

이미지 업로드는 브라우저가 MinIO로 직접 보내는 것이 아니라
**`POST /api/v1/product-images` multipart로 백엔드에 보낸다.** 따라서
`client_max_body_size`는 `/s3/`가 아니라 **API를 넘기는 location**에 필요하다.
nginx 기본값은 1MB라, 그대로 두면 1MB를 넘는 사진이 413으로 실패한다.

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    # ... 기존 헤더 설정 ...
    client_max_body_size 12m;   # 백엔드 MULTIPART_MAX_FILE_SIZE(10MB)보다 크게
}
```

관련 백엔드 정책: `PRODUCT_IMAGE_MAX_SIZE=5MB`(도메인 한도),
`MULTIPART_MAX_FILE_SIZE=10MB`(컨테이너 한도 — 초과를 `IMAGE_TOO_LARGE` 응답으로
내보내기 위해 도메인 한도보다 높게 둔다).

## 적용 절차

```bash
# 1) backend/.env 에 공개 주소 추가 (버킷명 포함)
cd /home/ubuntu/S15P11A704/backend
echo 'AWS_S3_PUBLIC_BASE_URL=https://i15a704.p.ssafy.io/s3/bidlive-bucket' >> .env
grep AWS_S3 .env          # ENDPOINT 는 내부 주소로 남아 있어야 한다

# 2) nginx 설정 반영
sudo nginx -t && sudo systemctl reload nginx

# 3) 백엔드 재기동 (.env 는 기동 시에만 읽는다)
docker compose up -d --force-recreate backend
docker exec bidlive-backend printenv | grep AWS_S3
```

## 검증

```bash
# 공개 경로로 오브젝트가 읽히는지 (버킷은 anonymous download 허용 상태)
curl -I https://i15a704.p.ssafy.io/s3/bidlive-bucket/products/<memberId>/<uuid>.png
# → 200 이면 정상. 404 면 버킷 세그먼트가 중복이거나 빠진 것이다.
```

브라우저에서 방 개설 → 사진 선택 후:

1. `POST /api/v1/product-images` 응답의 `imageUrl`이
   `https://i15a704.p.ssafy.io/s3/bidlive-bucket/...`인지
2. 미리보기 `<img>`의 GET이 그 주소로 나가 200인지
3. `http://minio:9000` 또는 `https://minio:9000`으로 나가는 요청이 없는지

## 오해하기 쉬운 점

**presigned URL을 쓰지 않는다.** 업로드는 브라우저가 백엔드로 multipart POST를
보내고 서버가 `s3Client.putObject()`로 저장하는 구조다. 코드베이스에 `S3Presigner`
사용처가 없고 프론트가 MinIO로 PUT을 보내는 지점도 없다. 따라서

- presigned URL 서명 호스트를 맞추는 작업이 필요하지 않다
  (서명 호스트 불일치로 인한 `SignatureDoesNotMatch` 문제 자체가 발생하지 않는다),
- S3 클라이언트를 내부용·공개용으로 분리할 필요가 없다,
- **MinIO 버킷 CORS 설정이 필요하지 않다** — 이미지는 `<img src>`로 표시하고
  (단순 GET은 CORS 대상이 아니다), 서버 URL을 `fetch`/XHR로 읽는 코드가 없다.
  업로드 POST는 같은 오리진(`/api/v1`)으로 나간다.

향후 업로드를 presigned PUT으로 바꾼다면 위 세 항목이 모두 필요해진다. 그때는
**공개 호스트로 서명**해야 하고(서명 대상에 호스트가 포함된다), MinIO는 path-style
접근이 필요하다(`forcePathStyle(true)` — 내부 클라이언트는
`S3ClientConfiguration`에서 이미 그렇게 설정한다).

## 이 설정 전에 업로드된 이미지

`product_image.image_url`에 URL 문자열이 그대로 저장되고, 방 생성·수정은 그 값을
정확히 일치 비교해 소유권을 검증한다(`JpaProductRegistrationPersistenceAdapter`).
따라서 설정을 바꿔도 새 업로드는 업로드→방 생성 한 사이클 안에서 일관되지만,
**이전에 저장된 행은 `http://minio:9000/...`으로 남아 계속 깨진다.**

같은 테이블에 `storage_key`가 따로 있으므로(`V12`에서 추가, UNIQUE) 필요하면
key로부터 URL을 다시 조립하는 일회성 UPDATE로 정리할 수 있다. 시연 데이터라면
해당 상품의 이미지를 다시 올리는 편이 간단하다.
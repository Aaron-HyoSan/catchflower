package com.catchflower.app.data.model

import com.catchflower.app.core.Visibility

/**
 * 발견 기록 1건. 공유계약 1-3이 필드명의 원본이다 (저장·전송은 snake_case).
 *
 * 같은 종을 여러 번 찍으면 도감 항목은 하나이고 이 기록이 누적된다 (기획서 6장).
 */
data class Discovery(
    val id: String,
    val userId: String,
    /** flowers.id */
    val flowerId: Int,
    /** 장변 1600px 압축 1장 (A-4 권고). 원본은 보관하지 않는다. */
    val photoUrl: String?,
    /** 기기 로컬 사진 경로. 업로드 전이거나 오프라인일 때 쓴다. */
    val localPhotoPath: String? = null,
    val lat: Double?,
    val lng: Double?,
    /** 카카오 로컬 검색 결과. 예 `서울숲`. 좌표 반올림 캐시를 거친다. */
    val placeName: String?,
    /** 행정동 코드. */
    val dongCode: String?,
    /**
     * 시군구 코드.
     * ⚠️ B-6(동 10명 미만이면 구 단위 확장)이 여기 의존한다.
     *    런타임에 동에서 구를 유도할 수 없어 적재 시점에 **둘 다** 넣는다.
     */
    val guCode: String?,
    val visibility: Visibility,
    /** 0.0~1.0. PlantNet은 보정된 확률이라 그대로 저장한다 (퍼센트로 곱하지 않는다). */
    val aiConfidence: Float?,
    /**
     * 사용자가 몇 순위 후보를 골랐는가 (1·2·3).
     * ⚠️ B-3 어뷰징 가드 ②. 계속 하위 순위만 고르는 계정은 신호다.
     */
    val aiPickedRank: Int?,
    /** 신규(화면 10) vs 재발견(화면 11)을 가른다. */
    val isFirstDiscovery: Boolean,
    /** 지도 공유 시 남기는 한 줄 (화면 13 · 최대 40자). */
    val note: String? = null,
    val createdAt: Long,
    /** C-8: 촬영 시각과 등록 시각의 차이를 검증한다 (위치 조작·재촬영 방지). */
    val capturedAt: Long,
)
